package br.edu.sistemaescala.backend.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.edu.sistemaescala.backend.model.Funcionario;
import br.edu.sistemaescala.backend.repository.EscalaFuncionarioRepository;
import br.edu.sistemaescala.backend.repository.EscalaTurnoRepository;
import br.edu.sistemaescala.backend.repository.FuncionarioRepository;

/**
 * Testes do levantamento de pendências da tela "Visão geral" (issue #55).
 *
 * <p>Cada teste começa do cenário limpo montado no {@link #setup()} — nenhum
 * problema em lugar nenhum — e liga só a condição que quer verificar. É o que
 * garante que cada alerta aparece por causa da sua própria consulta, e não de
 * carona em outra.</p>
 */
class AlertaServiceImplTest {

    /** Quarta-feira de setembro; o mês seguinte é outubro. */
    private static final LocalDate HOJE = LocalDate.of(2026, 9, 2);
    private static final YearMonth SETEMBRO = YearMonth.of(2026, 9);
    private static final YearMonth OUTUBRO = YearMonth.of(2026, 10);

    private EscalaTurnoRepository escalaTurnoRepository;
    private EscalaFuncionarioRepository escalaFuncionarioRepository;
    private FuncionarioRepository funcionarioRepository;
    private AlertaService servico;

    @BeforeEach
    void setup() {
        escalaTurnoRepository = mock(EscalaTurnoRepository.class);
        escalaFuncionarioRepository = mock(EscalaFuncionarioRepository.class);
        funcionarioRepository = mock(FuncionarioRepository.class);

        // Cenário sem pendência nenhuma: efetivo completo, ninguém inativo
        // escalado, coberturas todas lançadas e o próximo mês já montado.
        when(escalaTurnoRepository.contarDiasComEfetivoIncompleto(SETEMBRO)).thenReturn(0);
        when(escalaTurnoRepository.contarTurnosNoMes(OUTUBRO)).thenReturn(30);
        when(escalaFuncionarioRepository.contarCoberturasSemLancamentoNoMes(SETEMBRO)).thenReturn(0);
        when(funcionarioRepository.listarInativosEscaladosApos(any())).thenReturn(List.of());

        servico = new AlertaServiceImpl(escalaTurnoRepository, escalaFuncionarioRepository,
                funcionarioRepository);
    }

    // -----------------------------------------------------------------
    // Estado vazio
    // -----------------------------------------------------------------

    @Test
    void mesSemProblemaNenhumNaoGeraPendencia() {
        assertTrue(servico.levantar(HOJE).isEmpty(),
                "sem problema no banco não pode haver pendência inventada na tela");
    }

    @Test
    void levantarExigeODiaDeReferencia() {
        org.junit.jupiter.api.Assertions.assertThrows(NullPointerException.class,
                () -> servico.levantar(null));
    }

    // -----------------------------------------------------------------
    // a) Dias com efetivo incompleto
    // -----------------------------------------------------------------

    @Test
    void diasComEfetivoIncompletoViramAlertaDeAtencaoComAContagemDoMes() {
        when(escalaTurnoRepository.contarDiasComEfetivoIncompleto(SETEMBRO)).thenReturn(3);

        AlertaDashboard alerta = unico(servico.levantar(HOJE), TipoAlerta.EFETIVO_INCOMPLETO);

        assertEquals(SeveridadeAlerta.ATENCAO, alerta.severidade(),
                "escala incompleta pode ser salva com aviso; ela bloqueia é o PDF");
        assertTrue(alerta.titulo().contains("3 dias"), "o título traz o número que veio do banco");
        assertTrue(alerta.titulo().contains("Setembro"), "e o mês a que ele se refere");
    }

    @Test
    void umDiaIncompletoUsaOSingular() {
        when(escalaTurnoRepository.contarDiasComEfetivoIncompleto(SETEMBRO)).thenReturn(1);

        AlertaDashboard alerta = unico(servico.levantar(HOJE), TipoAlerta.EFETIVO_INCOMPLETO);

        assertTrue(alerta.titulo().startsWith("1 dia de "), alerta.titulo());
    }

    // -----------------------------------------------------------------
    // b) Funcionario desativado ainda escalado
    // -----------------------------------------------------------------

    @Test
    void funcionarioDesativadoAindaEscaladoEhCriticoECitaONome() {
        when(funcionarioRepository.listarInativosEscaladosApos(any()))
                .thenReturn(List.of(funcionario(1, "Sandra Regina Melo")));

        AlertaDashboard alerta = unico(servico.levantar(HOJE), TipoAlerta.INATIVO_ESCALADO);

        assertEquals(SeveridadeAlerta.CRITICO, alerta.severidade(),
                "escala contando com quem já saiu é inconsistência, não pendência de planejamento");
        assertTrue(alerta.titulo().startsWith("1 funcionário desativado"), alerta.titulo());
        assertTrue(alerta.descricao().contains("Sandra Regina Melo"),
                "sem o nome o gestor sabe que há problema, mas não em quem");
        assertTrue(alerta.descricao().contains("está desativado mas ainda aparece"),
                "um só concorda no singular: " + alerta.descricao());
    }

    /** O corte é o início do dia: o plantão de hoje ainda conta como futuro. */
    @Test
    void inativosSaoProcuradosAPartirDoInicioDoDiaDeReferencia() {
        servico.levantar(HOJE);

        verify(funcionarioRepository).listarInativosEscaladosApos(LocalDateTime.of(2026, 9, 2, 0, 0));
    }

    @Test
    void alertaDeInativosCitaAteTresNomesEResumeOResto() {
        when(funcionarioRepository.listarInativosEscaladosApos(any())).thenReturn(List.of(
                funcionario(1, "Ana Paula Souza"),
                funcionario(2, "Bruno Carvalho Lima"),
                funcionario(3, "Carla Menezes Dias"),
                funcionario(4, "Diego Nunes Alves"),
                funcionario(5, "Elisa Prado Rocha")));

        AlertaDashboard alerta = unico(servico.levantar(HOJE), TipoAlerta.INATIVO_ESCALADO);

        assertTrue(alerta.titulo().startsWith("5 funcionários desativados"), alerta.titulo());
        assertTrue(alerta.descricao().contains("Ana Paula Souza"));
        assertTrue(alerta.descricao().contains("Carla Menezes Dias"));
        assertTrue(alerta.descricao().contains("e mais 2"),
                "os nomes além do terceiro viram contagem, senão o painel vira um parágrafo");
        assertTrue(alerta.descricao().contains("estão desativados mas ainda aparecem"),
                "mais de um concorda no plural: " + alerta.descricao());
        assertTrue(!alerta.descricao().contains("Elisa Prado Rocha"), alerta.descricao());
    }

    // -----------------------------------------------------------------
    // c) Escala do proximo mes nao iniciada
    // -----------------------------------------------------------------

    @Test
    void proximoMesSemNenhumTurnoViraAlertaInformativo() {
        when(escalaTurnoRepository.contarTurnosNoMes(OUTUBRO)).thenReturn(0);

        AlertaDashboard alerta = unico(servico.levantar(HOJE), TipoAlerta.PROXIMO_MES_SEM_ESCALA);

        assertEquals(SeveridadeAlerta.INFORMATIVO, alerta.severidade(),
                "nada está errado no que existe, só há trabalho pela frente");
        assertTrue(alerta.titulo().contains("Outubro"), alerta.titulo());
        assertTrue(alerta.descricao().contains("2026"), alerta.descricao());
    }

    @Test
    void proximoMesParcialmenteMontadoJaFoiIniciadoENaoGeraAlerta() {
        when(escalaTurnoRepository.contarTurnosNoMes(OUTUBRO)).thenReturn(1);

        assertTrue(semTipo(servico.levantar(HOJE), TipoAlerta.PROXIMO_MES_SEM_ESCALA),
                "um turno já basta para o mês contar como iniciado");
    }

    /** Dezembro tem que virar janeiro do ano seguinte, não janeiro do mesmo ano. */
    @Test
    void emDezembroOProximoMesEhJaneiroDoAnoSeguinte() {
        when(escalaTurnoRepository.contarDiasComEfetivoIncompleto(YearMonth.of(2026, 12))).thenReturn(0);
        when(escalaTurnoRepository.contarTurnosNoMes(YearMonth.of(2027, 1))).thenReturn(0);
        when(escalaFuncionarioRepository.contarCoberturasSemLancamentoNoMes(YearMonth.of(2026, 12)))
                .thenReturn(0);

        AlertaDashboard alerta = unico(servico.levantar(LocalDate.of(2026, 12, 10)),
                TipoAlerta.PROXIMO_MES_SEM_ESCALA);

        assertTrue(alerta.descricao().contains("2027"), alerta.descricao());
        verify(escalaTurnoRepository).contarTurnosNoMes(YearMonth.of(2027, 1));
    }

    // -----------------------------------------------------------------
    // d) Cobertura sem lancamento no banco de horas
    // -----------------------------------------------------------------

    @Test
    void coberturaSemLancamentoNoBancoDeHorasViraAlertaDeAtencao() {
        when(escalaFuncionarioRepository.contarCoberturasSemLancamentoNoMes(SETEMBRO)).thenReturn(2);

        AlertaDashboard alerta = unico(servico.levantar(HOJE), TipoAlerta.COBERTURA_SEM_LANCAMENTO);

        assertEquals(SeveridadeAlerta.ATENCAO, alerta.severidade(),
                "lançar ou não é escolha do gestor; o alerta só cobra que seja consciente");
        assertTrue(alerta.titulo().startsWith("2 coberturas"), alerta.titulo());
    }

    // -----------------------------------------------------------------
    // Conjunto: ordenacao e independencia
    // -----------------------------------------------------------------

    @Test
    void alertasSaemDoMaisGraveParaOMenosGrave() {
        when(escalaTurnoRepository.contarDiasComEfetivoIncompleto(SETEMBRO)).thenReturn(3);
        when(escalaTurnoRepository.contarTurnosNoMes(OUTUBRO)).thenReturn(0);
        when(escalaFuncionarioRepository.contarCoberturasSemLancamentoNoMes(SETEMBRO)).thenReturn(2);
        when(funcionarioRepository.listarInativosEscaladosApos(any()))
                .thenReturn(List.of(funcionario(1, "Sandra Regina Melo")));

        List<AlertaDashboard> alertas = servico.levantar(HOJE);

        assertEquals(4, alertas.size(), "as quatro verificações acharam problema");
        assertEquals(SeveridadeAlerta.CRITICO, alertas.get(0).severidade());
        assertEquals(TipoAlerta.INATIVO_ESCALADO, alertas.get(0).tipo());
        assertEquals(SeveridadeAlerta.ATENCAO, alertas.get(1).severidade());
        assertEquals(SeveridadeAlerta.ATENCAO, alertas.get(2).severidade());
        assertEquals(SeveridadeAlerta.INFORMATIVO, alertas.get(3).severidade());
        assertEquals(TipoAlerta.PROXIMO_MES_SEM_ESCALA, alertas.get(3).tipo());
    }

    /** O recorte mensal sai do dia informado, não do relógio. */
    @Test
    void verificacoesMensaisUsamOMesDoDiaDeReferencia() {
        servico.levantar(LocalDate.of(2026, 3, 17));

        verify(escalaTurnoRepository).contarDiasComEfetivoIncompleto(YearMonth.of(2026, 3));
        verify(escalaFuncionarioRepository).contarCoberturasSemLancamentoNoMes(YearMonth.of(2026, 3));
        verify(escalaTurnoRepository).contarTurnosNoMes(YearMonth.of(2026, 4));
    }

    // -----------------------------------------------------------------
    // Apoio
    // -----------------------------------------------------------------

    /** O alerta do tipo pedido, exigindo que ele seja o único levantado. */
    private AlertaDashboard unico(List<AlertaDashboard> alertas, TipoAlerta tipo) {
        assertEquals(1, alertas.size(),
                "só a condição ligada pelo teste podia gerar pendência, mas veio " + alertas);
        assertEquals(tipo, alertas.get(0).tipo());
        return alertas.get(0);
    }

    private boolean semTipo(List<AlertaDashboard> alertas, TipoAlerta tipo) {
        return alertas.stream().noneMatch(alerta -> alerta.tipo() == tipo);
    }

    private Funcionario funcionario(int id, String nome) {
        Funcionario funcionario = new Funcionario();
        funcionario.setId(id);
        funcionario.setNome(nome);
        funcionario.setAtivo(false);
        return funcionario;
    }
}
