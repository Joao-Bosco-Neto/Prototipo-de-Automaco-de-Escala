package br.edu.sistemaescala.backend.service;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.MockedStatic;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.edu.sistemaescala.backend.dao.FuncaoTransacional;
import br.edu.sistemaescala.backend.dao.TransacaoUtil;
import br.edu.sistemaescala.backend.model.AcaoSeguranca;
import br.edu.sistemaescala.backend.model.EscalaFuncionario;
import br.edu.sistemaescala.backend.model.EscalaTurno;
import br.edu.sistemaescala.backend.model.Funcionario;
import br.edu.sistemaescala.backend.repository.EscalaTurnoRepository;
import br.edu.sistemaescala.backend.repository.RepositoryException;

/**
 * Testes da limpeza da escala de um mês (Issue #44).
 *
 * <p>{@link TransacaoUtil} é estático e abriria conexão real, então é
 * substituído por um dublê que executa o bloco recebido. O rollback de
 * verdade, contra o H2, é exercitado em
 * {@code LimpezaEscalaTransacaoTest}.</p>
 */
class LimpezaEscalaServiceImplTest {

    private static final YearMonth AGOSTO = YearMonth.of(2026, 8);

    private EscalaTurnoRepository escalaTurnoRepository;
    private LimpezaEscalaService limpezaEscalaService;
    private LogSegurancaFake logSeguranca;

    private MockedStatic<TransacaoUtil> transacaoEstatica;
    private final List<YearMonth> mesesRemovidos = new ArrayList<>();

    @BeforeEach
    void prepararMocks() {
        escalaTurnoRepository = mock(EscalaTurnoRepository.class);
        logSeguranca = new LogSegurancaFake();
        limpezaEscalaService = new LimpezaEscalaServiceImpl(escalaTurnoRepository, logSeguranca);

        when(escalaTurnoRepository.buscarPorPeriodo(any(), any())).thenReturn(List.of());
        doAnswer(invocacao -> {
            mesesRemovidos.add(invocacao.getArgument(0));
            return null;
        }).when(escalaTurnoRepository).removerPorMes(any(YearMonth.class), any());

        Connection conexao = conexaoDeTeste();
        transacaoEstatica = mockStatic(TransacaoUtil.class);
        transacaoEstatica.when(() -> TransacaoUtil.executar(any())).thenAnswer(invocacao -> {
            FuncaoTransacional<?> operacao = invocacao.getArgument(0);
            return operacao.aplicar(conexao);
        });
    }

    @AfterEach
    void encerrarDubleEstatico() {
        transacaoEstatica.close();
    }

    @Test
    void limparMesComTurnosEAlocacoesRemoveTudo() {
        when(escalaTurnoRepository.buscarPorPeriodo(any(), any())).thenReturn(List.of(
                turno(1, LocalDateTime.of(2026, 8, 1, 8, 0), 2),
                turno(2, LocalDateTime.of(2026, 8, 2, 8, 0), 3)));

        ResultadoLimpeza resultado = limpezaEscalaService.limparMes(AGOSTO);

        assertTrue(resultado.limpo());
        assertEquals(2, resultado.turnosRemovidos());
        assertEquals(5, resultado.alocacoesRemovidas(), "As alocações dos dois turnos devem ser contadas");
        assertEquals(List.of(AGOSTO), mesesRemovidos);
        assertTrue(resultado.mensagem().contains("2 turno"), resultado.mensagem());
        assertTrue(resultado.mensagem().contains("5 aloca"), resultado.mensagem());
    }

    @Test
    void limparMesEntraNaTrilhaDeAuditoria() {
        when(escalaTurnoRepository.buscarPorPeriodo(any(), any()))
                .thenReturn(List.of(turno(1, LocalDateTime.of(2026, 8, 1, 8, 0), 2)));

        limpezaEscalaService.limparMes(AGOSTO);

        LogSegurancaFake.Evento evento = logSeguranca.ultimo();
        assertEquals(AcaoSeguranca.ESCALA_MES_LIMPA, evento.acao());
        assertTrue(evento.detalhes().contains("2026-08"), evento.detalhes());
    }

    @Test
    void mesVazioNaoGeraEventoDeSegurancaPorqueNadaFoiApagado() {
        limpezaEscalaService.limparMes(AGOSTO);

        assertTrue(logSeguranca.eventos().isEmpty());
    }

    @Test
    void falhaNaRemocaoNaoDeixaEventoDeAuditoriaDeExclusaoQueNaoAconteceu() {
        when(escalaTurnoRepository.buscarPorPeriodo(any(), any()))
                .thenReturn(List.of(turno(1, LocalDateTime.of(2026, 8, 1, 8, 0), 1)));
        doThrow(new RepositoryException("banco fora do ar", null))
                .when(escalaTurnoRepository).removerPorMes(any(YearMonth.class), any());

        assertThrows(RepositoryException.class, () -> limpezaEscalaService.limparMes(AGOSTO));

        assertTrue(logSeguranca.eventos().isEmpty());
    }

    @Test
    void limparMesVazioNaoQuebraENaoAbreTransacao() {
        ResultadoLimpeza resultado = limpezaEscalaService.limparMes(AGOSTO);

        assertFalse(resultado.limpo());
        assertEquals(0, resultado.turnosRemovidos());
        assertEquals(0, resultado.alocacoesRemovidas());
        assertTrue(mesesRemovidos.isEmpty());
        assertTrue(resultado.mensagem().toLowerCase().contains("não há o que limpar"),
                resultado.mensagem());

        // Nem transação nem DELETE: mês vazio não deveria custar uma escrita.
        transacaoEstatica.verifyNoInteractions();
        verify(escalaTurnoRepository, never()).removerPorMes(any(YearMonth.class));
    }

    @Test
    void aRemocaoUsaASobrecargaTransacionalNuncaAQueAbreConexaoPropria() {
        when(escalaTurnoRepository.buscarPorPeriodo(any(), any()))
                .thenReturn(List.of(turno(1, LocalDateTime.of(2026, 8, 1, 8, 0), 1)));

        limpezaEscalaService.limparMes(AGOSTO);

        verify(escalaTurnoRepository).removerPorMes(any(YearMonth.class), any(Connection.class));
        verify(escalaTurnoRepository, never()).removerPorMes(any(YearMonth.class));
    }

    @Test
    void falhaNaRemocaoSobeParaOChamadorEmVezDeRelatarSucesso() {
        // O TransacaoUtil real desfaz a transação e relança; o que o service
        // não pode fazer é engolir a falha e devolver um resultado de sucesso.
        when(escalaTurnoRepository.buscarPorPeriodo(any(), any()))
                .thenReturn(List.of(turno(1, LocalDateTime.of(2026, 8, 1, 8, 0), 1)));
        RepositoryException falha = new RepositoryException("banco fora do ar", null);
        doThrow(falha).when(escalaTurnoRepository).removerPorMes(any(YearMonth.class), any());

        RepositoryException lancada = assertThrows(RepositoryException.class,
                () -> limpezaEscalaService.limparMes(AGOSTO));

        assertSame(falha, lancada);
    }

    @Test
    void resumirContaSemApagarNada() {
        when(escalaTurnoRepository.buscarPorPeriodo(any(), any())).thenReturn(List.of(
                turno(1, LocalDateTime.of(2026, 8, 1, 8, 0), 2),
                turno(2, LocalDateTime.of(2026, 8, 2, 8, 0), 1)));

        ResumoEscalaMes resumo = limpezaEscalaService.resumir(AGOSTO);

        assertEquals(2, resumo.turnos());
        assertEquals(3, resumo.alocacoes());
        assertFalse(resumo.vazio());
        assertTrue(mesesRemovidos.isEmpty(), "resumir() não pode apagar nada");
        transacaoEstatica.verifyNoInteractions();
    }

    @Test
    void resumirDeMesVazioAvisaQueEstaVazio() {
        ResumoEscalaMes resumo = limpezaEscalaService.resumir(AGOSTO);

        assertTrue(resumo.vazio());
        assertEquals(0, resumo.turnos());
        assertEquals(0, resumo.alocacoes());
    }

    // -----------------------------------------------------------------
    // Apoio
    // -----------------------------------------------------------------

    private static EscalaTurno turno(int id, LocalDateTime inicio, int quantidadeDeAgentes) {
        EscalaTurno turno = new EscalaTurno();
        turno.setId(id);
        turno.setInicio(inicio);
        turno.setFim(inicio.plusHours(24));
        for (int i = 0; i < quantidadeDeAgentes; i++) {
            Funcionario funcionario = new Funcionario();
            funcionario.setId(id * 100 + i);
            EscalaFuncionario alocacao = new EscalaFuncionario();
            alocacao.setEscalaTurno(turno);
            alocacao.setFuncionario(funcionario);
            turno.getAgentes().add(alocacao);
        }
        return turno;
    }

    /**
     * Connection de mentira só para o bloco transacional ter o que repassar.
     * Um mock do Mockito não serve: java.sql.Connection é interface do JDK e o
     * inline mock maker não consegue instrumentá-la.
     */
    private static Connection conexaoDeTeste() {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[] { Connection.class },
                (proxy, metodo, argumentos) -> switch (metodo.getName()) {
                    case "toString" -> "conexao-de-teste";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == argumentos[0];
                    default -> null;
                });
    }
}
