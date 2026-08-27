package br.edu.sistemaescala.backend.service;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.edu.sistemaescala.backend.model.EscalaFuncionario;
import br.edu.sistemaescala.backend.model.EscalaTurno;
import br.edu.sistemaescala.backend.model.Funcionario;
import br.edu.sistemaescala.backend.repository.EscalaFuncionarioRepository;

class RegraEscalaServiceImplTest {

    private static final int FUNCIONARIO_ID = 7;
    private static final int TURNO_ID = 100;
    private static final int OUTRO_TURNO_ID = 200;

    /** Turno analisado em todos os testes: 25/08/2026, das 08h às 14h. */
    private static final LocalDateTime INICIO_TURNO = LocalDateTime.of(2026, 8, 25, 8, 0);
    private static final LocalDateTime FIM_TURNO = LocalDateTime.of(2026, 8, 25, 14, 0);

    private EscalaFuncionarioRepository escalaFuncionarioRepository;
    private RegraEscalaService regraEscalaService;
    private EscalaTurno turno;

    @BeforeEach
    void prepararMocks() {
        escalaFuncionarioRepository = mock(EscalaFuncionarioRepository.class);
        regraEscalaService = new RegraEscalaServiceImpl(escalaFuncionarioRepository);
        turno = turno(TURNO_ID, INICIO_TURNO, FIM_TURNO, 2);

        when(escalaFuncionarioRepository.listarPorTurno(anyInt())).thenReturn(List.of());
        when(escalaFuncionarioRepository.listarPorFuncionario(anyInt(), any(), any())).thenReturn(List.of());
    }

    @Test
    void funcionarioJaAlocadoNoMesmoTurnoNaoPodeSerAlocadoDeNovo() {
        when(escalaFuncionarioRepository.listarPorTurno(TURNO_ID))
                .thenReturn(List.of(alocacaoDeTurnoInteiro(turno)));

        ResultadoAlocacao resultado = regraEscalaService.podeAlocar(FUNCIONARIO_ID, turno);

        assertFalse(resultado.permitido());
        assertTrue(resultado.mensagem().toLowerCase().contains("duplicidade"),
                "A mensagem deveria mencionar duplicidade: " + resultado.mensagem());
    }

    @Test
    void funcionarioLivreSemOutrasAlocacoesPodeSerAlocado() {
        ResultadoAlocacao resultado = regraEscalaService.podeAlocar(FUNCIONARIO_ID, turno);

        assertTrue(resultado.permitido());
    }

    @Test
    void alocacaoDeTurnoInteiroComPeriodoNuloUsaOPeriodoDoTurnoEDetectaSobreposicao() {
        // Caso normal do sistema: inicio/fim da EscalaFuncionario nulos, o
        // periodo efetivo vem do EscalaTurno (10h-16h invade o turno 08h-14h).
        EscalaTurno outroTurno = turno(OUTRO_TURNO_ID,
                LocalDateTime.of(2026, 8, 25, 10, 0),
                LocalDateTime.of(2026, 8, 25, 16, 0), 2);
        when(escalaFuncionarioRepository.listarPorFuncionario(eq(FUNCIONARIO_ID), any(), any()))
                .thenReturn(List.of(alocacaoDeTurnoInteiro(outroTurno)));

        ResultadoAlocacao resultado = regraEscalaService.podeAlocar(FUNCIONARIO_ID, turno);

        assertFalse(resultado.permitido());
        assertTrue(resultado.mensagem().toLowerCase().contains("sobrepo"),
                "A mensagem deveria mencionar sobreposição: " + resultado.mensagem());
    }

    @Test
    void alocacaoParcialQueInvadeOPeriodoDoTurnoImpedeAAlocacao() {
        // Meio plantao das 12h as 18h dentro de um turno de 12h-20h: os campos
        // da EscalaFuncionario tem prioridade sobre o periodo do turno.
        EscalaTurno outroTurno = turno(OUTRO_TURNO_ID,
                LocalDateTime.of(2026, 8, 25, 12, 0),
                LocalDateTime.of(2026, 8, 25, 20, 0), 2);
        EscalaFuncionario parcial = alocacaoParcial(outroTurno,
                LocalDateTime.of(2026, 8, 25, 12, 0),
                LocalDateTime.of(2026, 8, 25, 18, 0));
        when(escalaFuncionarioRepository.listarPorFuncionario(eq(FUNCIONARIO_ID), any(), any()))
                .thenReturn(List.of(parcial));

        ResultadoAlocacao resultado = regraEscalaService.podeAlocar(FUNCIONARIO_ID, turno);

        assertFalse(resultado.permitido());
    }

    @Test
    void alocacaoParcialQueApenasEncostaNoFimDoTurnoNaoImpedeAAlocacao() {
        // 14h-20h logo apos o turno 08h-14h: fim igual ao inicio do outro nao
        // e sobreposicao.
        EscalaTurno outroTurno = turno(OUTRO_TURNO_ID,
                LocalDateTime.of(2026, 8, 25, 14, 0),
                LocalDateTime.of(2026, 8, 25, 22, 0), 2);
        EscalaFuncionario parcial = alocacaoParcial(outroTurno,
                LocalDateTime.of(2026, 8, 25, 14, 0),
                LocalDateTime.of(2026, 8, 25, 20, 0));
        when(escalaFuncionarioRepository.listarPorFuncionario(eq(FUNCIONARIO_ID), any(), any()))
                .thenReturn(List.of(parcial));

        ResultadoAlocacao resultado = regraEscalaService.podeAlocar(FUNCIONARIO_ID, turno);

        assertTrue(resultado.permitido(),
                "Turnos que apenas se encostam não deveriam ser tratados como sobreposição.");
    }

    @Test
    void alocacaoAnteriorQueJaTerminouNaoImpedeAAlocacao() {
        // Plantao da madrugada (00h-06h) que terminou antes do turno 08h-14h
        // comecar: entra na janela de busca, mas nao se sobrepoe.
        EscalaTurno outroTurno = turno(OUTRO_TURNO_ID,
                LocalDateTime.of(2026, 8, 25, 0, 0),
                LocalDateTime.of(2026, 8, 25, 6, 0), 2);
        when(escalaFuncionarioRepository.listarPorFuncionario(eq(FUNCIONARIO_ID), any(), any()))
                .thenReturn(List.of(alocacaoDeTurnoInteiro(outroTurno)));

        ResultadoAlocacao resultado = regraEscalaService.podeAlocar(FUNCIONARIO_ID, turno);

        assertTrue(resultado.permitido(),
                "Um plantão que já terminou não deveria ser tratado como sobreposição.");
    }

    @Test
    void turnoComMenosAgentesQueOMinimoFicaIncompleto() {
        when(escalaFuncionarioRepository.listarPorTurno(TURNO_ID))
                .thenReturn(List.of(alocacaoDeTurnoInteiro(turno)));

        ResultadoEfetivo resultado = regraEscalaService.verificarEfetivo(turno);

        assertFalse(resultado.completo());
        assertEquals(1, resultado.alocados());
        assertEquals(2, resultado.minimoExigido());
        assertTrue(resultado.mensagem().contains("1 de 2"),
                "A mensagem deveria trazer a contagem: " + resultado.mensagem());
    }

    @Test
    void turnoComExatamenteOMinimoDeAgentesFicaCompleto() {
        when(escalaFuncionarioRepository.listarPorTurno(TURNO_ID))
                .thenReturn(List.of(alocacaoDeTurnoInteiro(turno), alocacaoDeTurnoInteiro(turno)));

        ResultadoEfetivo resultado = regraEscalaService.verificarEfetivo(turno);

        assertTrue(resultado.completo());
        assertEquals(2, resultado.alocados());
        assertEquals(2, resultado.minimoExigido());
    }

    @Test
    void turnoAcimaDoMinimoDeAgentesFicaCompleto() {
        when(escalaFuncionarioRepository.listarPorTurno(TURNO_ID)).thenReturn(
                List.of(alocacaoDeTurnoInteiro(turno), alocacaoDeTurnoInteiro(turno), alocacaoDeTurnoInteiro(turno)));

        ResultadoEfetivo resultado = regraEscalaService.verificarEfetivo(turno);

        assertTrue(resultado.completo());
        assertEquals(3, resultado.alocados());
        assertEquals(2, resultado.minimoExigido());
    }

    private EscalaTurno turno(int id, LocalDateTime inicio, LocalDateTime fim, int minAgentes) {
        EscalaTurno escalaTurno = new EscalaTurno();
        escalaTurno.setId(id);
        escalaTurno.setInicio(inicio);
        escalaTurno.setFim(fim);
        escalaTurno.setMinAgentes(minAgentes);
        return escalaTurno;
    }

    /** Alocação de turno inteiro: inicio/fim nulos, como o banco grava no caso normal. */
    private EscalaFuncionario alocacaoDeTurnoInteiro(EscalaTurno escalaTurno) {
        EscalaFuncionario alocacao = new EscalaFuncionario();
        alocacao.setEscalaTurno(escalaTurno);
        alocacao.setFuncionario(funcionario());
        return alocacao;
    }

    /** Alocação parcial: inicio/fim preenchidos (meio plantão). */
    private EscalaFuncionario alocacaoParcial(EscalaTurno escalaTurno, LocalDateTime inicio, LocalDateTime fim) {
        EscalaFuncionario alocacao = alocacaoDeTurnoInteiro(escalaTurno);
        alocacao.setInicio(inicio);
        alocacao.setFim(fim);
        return alocacao;
    }

    private Funcionario funcionario() {
        Funcionario funcionario = new Funcionario();
        funcionario.setId(FUNCIONARIO_ID);
        return funcionario;
    }
}
