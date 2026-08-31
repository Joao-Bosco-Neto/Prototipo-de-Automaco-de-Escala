package br.edu.sistemaescala.backend.service;

import java.sql.Connection;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.MockedStatic;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import br.edu.sistemaescala.backend.dao.FuncaoTransacional;
import br.edu.sistemaescala.backend.dao.TransacaoUtil;
import br.edu.sistemaescala.backend.model.AcaoSeguranca;
import br.edu.sistemaescala.backend.model.EscalaExcecao;
import br.edu.sistemaescala.backend.model.EscalaFuncionario;
import br.edu.sistemaescala.backend.model.EscalaTurno;
import br.edu.sistemaescala.backend.model.Funcionario;
import br.edu.sistemaescala.backend.model.RegraExcecao;
import br.edu.sistemaescala.backend.model.RoleUsuario;
import br.edu.sistemaescala.backend.model.Usuario;
import br.edu.sistemaescala.backend.repository.EscalaExcecaoRepository;
import br.edu.sistemaescala.backend.repository.EscalaFuncionarioRepository;

/**
 * Testes da autorização de exceção às regras da escala (issue #64).
 *
 * <p>{@link TransacaoUtil} é estático e abriria conexão real, então é
 * substituído por um dublê que executa o bloco recebido — mesmo desenho do
 * {@code LimpezaEscalaServiceImplTest}.</p>
 */
class EscalaExcecaoServiceImplTest {

    private static final LocalDateTime INICIO = LocalDateTime.of(2041, 5, 12, 8, 0);
    private static final int ALOCACAO_ID = 77;

    /** Trilha de exceções em memória, com a opção de falhar dentro da transação. */
    private static class RepositorioEmMemoria implements EscalaExcecaoRepository {
        private final List<EscalaExcecao> gravadas = new ArrayList<>();
        private boolean falhar;

        @Override
        public EscalaExcecao inserir(EscalaExcecao excecao) {
            return inserir(excecao, null);
        }

        @Override
        public EscalaExcecao inserir(EscalaExcecao excecao, Connection conexao) {
            if (falhar) {
                throw new IllegalStateException("falha simulada depois do INSERT da alocação");
            }
            gravadas.add(excecao);
            return excecao;
        }

        @Override
        public List<EscalaExcecao> listarPorFuncionario(int funcionarioId) {
            return gravadas.stream().filter(e -> e.getFuncionarioId() == funcionarioId).toList();
        }
    }

    private EscalaFuncionarioRepository escalaFuncionarioRepository;
    private RepositorioEmMemoria escalaExcecaoRepository;
    private LogSegurancaFake logSeguranca;
    private SessaoUsuario sessaoUsuario;
    private EscalaExcecaoService servico;

    private MockedStatic<TransacaoUtil> transacaoEstatica;

    @BeforeEach
    void preparar() {
        escalaFuncionarioRepository = mock(EscalaFuncionarioRepository.class);
        escalaExcecaoRepository = new RepositorioEmMemoria();
        logSeguranca = new LogSegurancaFake();
        sessaoUsuario = new SessaoUsuario();
        sessaoUsuario.iniciar(new Usuario(1, "Admin", "admin", "hash", RoleUsuario.ADMIN, true, null, null));

        servico = new EscalaExcecaoServiceImpl(escalaFuncionarioRepository, escalaExcecaoRepository,
                logSeguranca, sessaoUsuario);

        // A alocação nasce com id, como o repositório JDBC faz depois do INSERT.
        doAnswer(invocacao -> {
            EscalaFuncionario alocacao = invocacao.getArgument(0);
            alocacao.setId(ALOCACAO_ID);
            return alocacao;
        }).when(escalaFuncionarioRepository).inserir(any(EscalaFuncionario.class), any());

        transacaoEstatica = mockStatic(TransacaoUtil.class);
        transacaoEstatica.when(() -> TransacaoUtil.executar(any())).thenAnswer(invocacao -> {
            FuncaoTransacional<?> operacao = invocacao.getArgument(0);
            return operacao.aplicar(mock(Connection.class));
        });
    }

    @AfterEach
    void encerrarDubleEstatico() {
        transacaoEstatica.close();
    }

    private EscalaTurno turno() {
        EscalaTurno turno = new EscalaTurno();
        turno.setId(9);
        turno.setInicio(INICIO);
        turno.setFim(INICIO.plusHours(24));
        return turno;
    }

    private Funcionario funcionario() {
        Funcionario funcionario = new Funcionario();
        funcionario.setId(5);
        funcionario.setNome("Fulano de Tal");
        funcionario.setMatricula("MAT-123");
        return funcionario;
    }

    private ResultadoDescanso descansoViolado() {
        return new ResultadoDescanso(false, Duration.ofHours(72), Duration.ofHours(12),
                "Descanso insuficiente: são 12h até o plantão vizinho, mas o regime exige 72h. Faltam 60h.");
    }

    @Test
    void alocaEGravaAExcecaoComQuemAutorizou() {
        EscalaFuncionario alocacao = servico.alocarComExcecao(turno(), funcionario(), descansoViolado());

        assertNotNull(alocacao.getId());
        assertEquals(1, escalaExcecaoRepository.gravadas.size());

        EscalaExcecao excecao = escalaExcecaoRepository.gravadas.get(0);
        assertEquals(ALOCACAO_ID, excecao.getEscalaFuncionarioId());
        assertEquals(5, excecao.getFuncionarioId());
        assertEquals(INICIO.toLocalDate(), excecao.getDataPlantao());
        assertEquals(RegraExcecao.DESCANSO_MINIMO, excecao.getRegra());
        assertEquals("admin", excecao.getAutorizadoPor());
        assertTrue(excecao.getDescricao().contains("exige 72h"));
    }

    @Test
    void aExcecaoAutorizadaEntraNaTrilhaDeSeguranca() {
        servico.alocarComExcecao(turno(), funcionario(), descansoViolado());

        LogSegurancaFake.Evento evento = logSeguranca.ultimo();
        assertEquals(AcaoSeguranca.ESCALA_EXCECAO_AUTORIZADA, evento.acao());
        assertTrue(evento.detalhes().contains("descanso_minimo"), evento.detalhes());
        assertTrue(evento.detalhes().contains("MAT-123"), evento.detalhes());
    }

    @Test
    void aTrilhaIdentificaOAgentePelaMatriculaNaoPeloNomeCompleto() {
        servico.alocarComExcecao(turno(), funcionario(), descansoViolado());

        assertFalse(logSeguranca.textoCompleto().contains("Fulano de Tal"));
    }

    @Test
    void descansoRespeitadoNaoGeraExcecaoNemAlocacao() {
        ResultadoDescanso semViolacao = new ResultadoDescanso(true, Duration.ofHours(72),
                Duration.ofHours(80), "Descanso de 80h respeitado (o regime exige 72h).");

        assertThrows(RegraEscalaExcecaoException.class,
                () -> servico.alocarComExcecao(turno(), funcionario(), semViolacao));

        assertTrue(escalaExcecaoRepository.gravadas.isEmpty());
        assertTrue(logSeguranca.eventos().isEmpty());
    }

    @Test
    void falhaNaGravacaoDaExcecaoNaoDeixaEventoDeAuditoriaDoQueNaoAconteceu() {
        escalaExcecaoRepository.falhar = true;

        assertThrows(IllegalStateException.class,
                () -> servico.alocarComExcecao(turno(), funcionario(), descansoViolado()));

        // O rollback da transação é do TransacaoUtil real; o que este teste
        // garante é que o evento de segurança só sai depois do commit.
        assertTrue(logSeguranca.eventos().isEmpty());
    }
}
