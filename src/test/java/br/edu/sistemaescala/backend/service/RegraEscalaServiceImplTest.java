package br.edu.sistemaescala.backend.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.edu.sistemaescala.backend.model.EscalaFuncionario;
import br.edu.sistemaescala.backend.model.EscalaTurno;
import br.edu.sistemaescala.backend.model.Funcionario;
import br.edu.sistemaescala.backend.model.TipoTurno;
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

    // -----------------------------------------------------------------
    // verificarEfetivo sobre agentes ja carregados (regressao de desempenho
    // do calendario: uma consulta por turno a cada redesenho da grade)
    // -----------------------------------------------------------------

    @Test
    void verificarEfetivoComAgentesJaCarregadosNaoConsultaOBanco() {
        EscalaTurno turnoDeDoisAgentes = turno(TURNO_ID, INICIO_TURNO, FIM_TURNO, 2);

        ResultadoEfetivo resultado = regraEscalaService.verificarEfetivo(turnoDeDoisAgentes,
                List.of(alocacaoDeTurnoInteiro(turnoDeDoisAgentes),
                        alocacaoDeTurnoInteiro(turnoDeDoisAgentes)));

        assertTrue(resultado.completo());
        assertEquals(2, resultado.alocados());
        verifyNoInteractions(escalaFuncionarioRepository);
    }

    @Test
    void verificarEfetivoComAgentesJaCarregadosDaOMesmoResultadoDaVersaoQueConsulta() {
        EscalaTurno turnoDeDoisAgentes = turno(TURNO_ID, INICIO_TURNO, FIM_TURNO, 2);
        List<EscalaFuncionario> agentes = List.of(alocacaoDeTurnoInteiro(turnoDeDoisAgentes));
        when(escalaFuncionarioRepository.listarPorTurno(TURNO_ID)).thenReturn(agentes);

        ResultadoEfetivo consultando = regraEscalaService.verificarEfetivo(turnoDeDoisAgentes);
        ResultadoEfetivo comLista = regraEscalaService.verificarEfetivo(turnoDeDoisAgentes, agentes);

        // A regra e a mensagem sao as mesmas: muda so de onde vem a lista.
        assertEquals(consultando, comLista);
        assertFalse(comLista.completo());
        assertTrue(comLista.mensagem().contains("Faltam 1 agente"), comLista.mensagem());
    }

    @Test
    void verificarEfetivoComListaVaziaAcusaTurnoSemNinguem() {
        EscalaTurno turnoDeDoisAgentes = turno(TURNO_ID, INICIO_TURNO, FIM_TURNO, 2);

        ResultadoEfetivo resultado = regraEscalaService.verificarEfetivo(turnoDeDoisAgentes, List.of());

        assertFalse(resultado.completo());
        assertEquals(0, resultado.alocados());
        assertEquals(2, resultado.minimoExigido());
    }

    @Test
    void coberturaContaComoPostoUnicoEOEfetivoFicaCompleto() {
        // Turno de minimo 2: 1 titular comum + 1 titular ausente coberto por
        // 1 substituto = 3 linhas em escala_funcionario, mas so 2 postos.
        EscalaTurno turnoDeDoisAgentes = turno(TURNO_ID, INICIO_TURNO, FIM_TURNO, 2);
        EscalaFuncionario titularComum = alocacaoComId(1);
        EscalaFuncionario titularAusente = alocacaoComId(2);
        EscalaFuncionario substituto = alocacaoComId(3);
        substituto.setCoberturaDe(titularAusente);

        ResultadoEfetivo resultado = regraEscalaService.verificarEfetivo(turnoDeDoisAgentes,
                List.of(titularComum, titularAusente, substituto));

        assertTrue(resultado.completo(), "Titular + substituto deveriam contar como 1 unico posto.");
        assertEquals(2, resultado.alocados());
        assertEquals(2, resultado.minimoExigido());
    }

    @Test
    void substitutoSemTitularComumNaoCompletaOMinimo() {
        // Unico posto real (titular ausente coberto): 1/2, ainda incompleto.
        EscalaTurno turnoDeDoisAgentes = turno(TURNO_ID, INICIO_TURNO, FIM_TURNO, 2);
        EscalaFuncionario titularAusente = alocacaoComId(2);
        EscalaFuncionario substituto = alocacaoComId(3);
        substituto.setCoberturaDe(titularAusente);

        ResultadoEfetivo resultado = regraEscalaService.verificarEfetivo(turnoDeDoisAgentes,
                List.of(titularAusente, substituto));

        assertFalse(resultado.completo());
        assertEquals(1, resultado.alocados());
    }

    private EscalaFuncionario alocacaoComId(int id) {
        EscalaFuncionario alocacao = new EscalaFuncionario();
        alocacao.setId(id);
        alocacao.setFuncionario(funcionario());
        return alocacao;
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

    // ------------------------------------------------------------------
    // Intervalo de descanso obrigatorio (issue #40 / RF11)
    // ------------------------------------------------------------------

    @Test
    void semNenhumaAlocacaoAnteriorODescansoEstaRespeitado() {
        EscalaTurno turnoComDescanso = turnoComDescanso(TURNO_ID, INICIO_TURNO, FIM_TURNO, "24");

        ResultadoDescanso resultado = regraEscalaService.verificarDescanso(FUNCIONARIO_ID, turnoComDescanso);

        assertTrue(resultado.respeitado());
        assertNull(resultado.descansoEncontrado(), "Sem plantão vizinho não há descanso medido.");
    }

    @Test
    void plantaoAnteriorComDescansoSuficienteEPermitido() {
        // Plantao terminou 24/08 as 02h; o turno comeca 25/08 as 08h -> 30h de folga.
        alocacaoVizinhaDeTurnoInteiro(
                LocalDateTime.of(2026, 8, 23, 20, 0),
                LocalDateTime.of(2026, 8, 24, 2, 0));

        ResultadoDescanso resultado = regraEscalaService.verificarDescanso(
                FUNCIONARIO_ID, turnoComDescanso(TURNO_ID, INICIO_TURNO, FIM_TURNO, "24"));

        assertTrue(resultado.respeitado());
        assertEquals(Duration.ofHours(30), resultado.descansoEncontrado());
    }

    @Test
    void plantaoAnteriorComDescansoInsuficienteERecusadoEInformaQuantoFalta() {
        // Plantao terminou 25/08 as 02h; o turno comeca as 08h -> so 6h das 24h exigidas.
        alocacaoVizinhaDeTurnoInteiro(
                LocalDateTime.of(2026, 8, 24, 20, 0),
                LocalDateTime.of(2026, 8, 25, 2, 0));

        ResultadoDescanso resultado = regraEscalaService.verificarDescanso(
                FUNCIONARIO_ID, turnoComDescanso(TURNO_ID, INICIO_TURNO, FIM_TURNO, "24"));

        assertFalse(resultado.respeitado());
        assertEquals(Duration.ofHours(6), resultado.descansoEncontrado());
        assertTrue(resultado.mensagem().contains("Faltam 18h"),
                "A mensagem deveria dizer quantas horas faltam: " + resultado.mensagem());
    }

    @Test
    void plantaoPosteriorComDescansoInsuficienteTambemERecusado() {
        // O caso que uma implementacao ingenua esquece: o conflito esta DEPOIS
        // do turno analisado (termina 14h, o proximo plantao comeca as 20h).
        alocacaoVizinhaDeTurnoInteiro(
                LocalDateTime.of(2026, 8, 25, 20, 0),
                LocalDateTime.of(2026, 8, 26, 2, 0));

        ResultadoDescanso resultado = regraEscalaService.verificarDescanso(
                FUNCIONARIO_ID, turnoComDescanso(TURNO_ID, INICIO_TURNO, FIM_TURNO, "24"));

        assertFalse(resultado.respeitado());
        assertEquals(Duration.ofHours(6), resultado.descansoEncontrado());
    }

    @Test
    void descansoExatamenteIgualAoExigidoERespeitado() {
        // 24/08 08h + 24h = 25/08 08h, exatamente o inicio do turno: o limite vale.
        alocacaoVizinhaDeTurnoInteiro(
                LocalDateTime.of(2026, 8, 23, 20, 0),
                LocalDateTime.of(2026, 8, 24, 8, 0));

        ResultadoDescanso resultado = regraEscalaService.verificarDescanso(
                FUNCIONARIO_ID, turnoComDescanso(TURNO_ID, INICIO_TURNO, FIM_TURNO, "24"));

        assertTrue(resultado.respeitado(), "O limite exato não deveria ser tratado como violação.");
        assertEquals(Duration.ofHours(24), resultado.descansoEncontrado());
    }

    @Test
    void tipoDeTurnoSemIntervaloDeDescansoNaoExigeFolga() {
        // Sobreaviso: descanso zero, entao nem o plantao colado invalida.
        alocacaoVizinhaDeTurnoInteiro(
                LocalDateTime.of(2026, 8, 25, 2, 0),
                LocalDateTime.of(2026, 8, 25, 8, 0));

        ResultadoDescanso resultado = regraEscalaService.verificarDescanso(
                FUNCIONARIO_ID, turnoComDescanso(TURNO_ID, INICIO_TURNO, FIM_TURNO, "0"));

        assertTrue(resultado.respeitado());
    }

    @Test
    void descansoFracionarioRecusaFolgaLogoAbaixoDoLimite() {
        // Regime de 36,5h: 36h de folga ainda e pouco. Se o valor fosse
        // truncado para 36 inteiro, este caso passaria por engano.
        alocacaoVizinhaDeTurnoInteiro(
                LocalDateTime.of(2026, 8, 24, 2, 0),
                LocalDateTime.of(2026, 8, 24, 8, 0));

        ResultadoDescanso resultado = regraEscalaService.verificarDescanso(FUNCIONARIO_ID,
                turnoComDescanso(TURNO_ID,
                        LocalDateTime.of(2026, 8, 25, 20, 0),
                        LocalDateTime.of(2026, 8, 26, 2, 0), "36.5"));

        assertFalse(resultado.respeitado(), "36h de folga não cumprem um regime de 36,5h.");
        assertEquals(Duration.ofHours(36), resultado.descansoEncontrado());
        assertEquals(Duration.ofMinutes(2190), resultado.descansoExigido());
    }

    @Test
    void descansoFracionarioAceitaFolgaLogoAcimaDoLimite() {
        // Mesmo regime de 36,5h, agora com 37h de folga.
        alocacaoVizinhaDeTurnoInteiro(
                LocalDateTime.of(2026, 8, 24, 1, 0),
                LocalDateTime.of(2026, 8, 24, 7, 0));

        ResultadoDescanso resultado = regraEscalaService.verificarDescanso(FUNCIONARIO_ID,
                turnoComDescanso(TURNO_ID,
                        LocalDateTime.of(2026, 8, 25, 20, 0),
                        LocalDateTime.of(2026, 8, 26, 2, 0), "36.5"));

        assertTrue(resultado.respeitado());
        assertEquals(Duration.ofHours(37), resultado.descansoEncontrado());
    }

    @Test
    void alocacaoParcialContaODescansoAPartirDoFimRealDaAlocacao() {
        // O funcionario saiu as 14h de um turno que ia ate as 20h. Contando do
        // fim real (14h) sobram 12h ate o proximo turno -- exatamente o exigido.
        // Se contasse do fim do turno inteiro (20h), seriam so 6h e recusaria.
        EscalaTurno turnoDoVizinho = turno(OUTRO_TURNO_ID,
                LocalDateTime.of(2026, 8, 25, 8, 0),
                LocalDateTime.of(2026, 8, 25, 20, 0), 2);
        EscalaFuncionario parcial = alocacaoParcial(turnoDoVizinho,
                LocalDateTime.of(2026, 8, 25, 8, 0),
                LocalDateTime.of(2026, 8, 25, 14, 0));
        when(escalaFuncionarioRepository.listarPorFuncionario(eq(FUNCIONARIO_ID), any(), any()))
                .thenReturn(List.of(parcial));

        ResultadoDescanso resultado = regraEscalaService.verificarDescanso(FUNCIONARIO_ID,
                turnoComDescanso(TURNO_ID,
                        LocalDateTime.of(2026, 8, 26, 2, 0),
                        LocalDateTime.of(2026, 8, 26, 8, 0), "12"));

        assertTrue(resultado.respeitado(), "O descanso deveria contar do fim real da alocação parcial.");
        assertEquals(Duration.ofHours(12), resultado.descansoEncontrado());
    }

    @Test
    void regime24x72LiberaOProximoTurnoSoAs08hDoQuartoDia() {
        // Cenario da issue: plantao de 24h terminando 10/09 as 08h, descanso de
        // 72h -> o proximo turno so pode comecar 13/09 as 08h.
        alocacaoVizinhaDeTurnoInteiro(
                LocalDateTime.of(2026, 9, 9, 8, 0),
                LocalDateTime.of(2026, 9, 10, 8, 0));

        ResultadoDescanso noLimite = regraEscalaService.verificarDescanso(FUNCIONARIO_ID,
                turnoComDescanso(TURNO_ID,
                        LocalDateTime.of(2026, 9, 13, 8, 0),
                        LocalDateTime.of(2026, 9, 14, 8, 0), "72"));

        assertTrue(noLimite.respeitado(), "13/09 às 08h completa as 72h exigidas.");
        assertEquals(Duration.ofHours(72), noLimite.descansoEncontrado());
    }

    @Test
    void regime24x72RecusaOProximoTurnoUmMinutoAntesDas08hDoQuartoDia() {
        alocacaoVizinhaDeTurnoInteiro(
                LocalDateTime.of(2026, 9, 9, 8, 0),
                LocalDateTime.of(2026, 9, 10, 8, 0));

        ResultadoDescanso umMinutoAntes = regraEscalaService.verificarDescanso(FUNCIONARIO_ID,
                turnoComDescanso(TURNO_ID,
                        LocalDateTime.of(2026, 9, 13, 7, 59),
                        LocalDateTime.of(2026, 9, 14, 7, 59), "72"));

        assertFalse(umMinutoAntes.respeitado(), "Às 07h59 do quarto dia ainda falta 1 minuto de descanso.");
        assertEquals(Duration.ofHours(72).minusMinutes(1), umMinutoAntes.descansoEncontrado());
        assertTrue(umMinutoAntes.mensagem().contains("Faltam 0h01min"),
                "A mensagem deveria dizer que falta 1 minuto: " + umMinutoAntes.mensagem());
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

    /** Turno com tipo de turno configurado, que e de onde vem o intervalo de descanso exigido. */
    private EscalaTurno turnoComDescanso(int id, LocalDateTime inicio, LocalDateTime fim, String descansoHoras) {
        EscalaTurno escalaTurno = turno(id, inicio, fim, 2);
        TipoTurno tipoTurno = new TipoTurno();
        tipoTurno.setIntervaloDescansoHoras(new BigDecimal(descansoHoras));
        escalaTurno.setTipoTurno(tipoTurno);
        return escalaTurno;
    }

    /** Registra no mock um unico plantao vizinho de turno inteiro no periodo informado. */
    private void alocacaoVizinhaDeTurnoInteiro(LocalDateTime inicio, LocalDateTime fim) {
        EscalaTurno turnoDoVizinho = turno(OUTRO_TURNO_ID, inicio, fim, 2);
        when(escalaFuncionarioRepository.listarPorFuncionario(eq(FUNCIONARIO_ID), any(), any()))
                .thenReturn(List.of(alocacaoDeTurnoInteiro(turnoDoVizinho)));
    }

    private Funcionario funcionario() {
        Funcionario funcionario = new Funcionario();
        funcionario.setId(FUNCIONARIO_ID);
        return funcionario;
    }
}
