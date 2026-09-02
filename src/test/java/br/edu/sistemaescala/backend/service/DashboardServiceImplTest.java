package br.edu.sistemaescala.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import br.edu.sistemaescala.backend.model.EscalaFuncionario;
import br.edu.sistemaescala.backend.model.EscalaTurno;
import br.edu.sistemaescala.backend.model.Funcionario;
import br.edu.sistemaescala.backend.model.TipoTurno;
import br.edu.sistemaescala.backend.repository.EscalaFuncionarioRepository;
import br.edu.sistemaescala.backend.repository.EscalaTurnoRepository;
import br.edu.sistemaescala.backend.repository.FuncionarioRepository;

/**
 * O serviço não calcula indicador nenhum: ele escolhe os recortes e junta o que
 * os repositórios agregam. É isso que estes testes cobrem — que o mês e a
 * semana saem do dia informado, que cada número chega ao card certo e que a
 * semana e os próximos plantões saem de uma consulta só, sem voltar ao banco
 * por turno.
 */
class DashboardServiceImplTest {

    /** Quarta-feira. A semana dela vai de domingo 30/08 a sábado 05/09. */
    private static final LocalDate QUARTA = LocalDate.of(2026, 9, 2);

    @Test
    void carregarReuneOsQuatroIndicadoresNoRecorteDoDiaInformado() {
        PlantaoDoDiaItem plantao = new PlantaoDoDiaItem(7, "Turno Diurno 12h",
                QUARTA.atTime(7, 0), QUARTA.atTime(19, 0), 2, 2);
        EscalaTurnoFake turnos = new EscalaTurnoFake(List.of(plantao), 4, List.of());
        EscalaFuncionarioFake alocacoes = new EscalaFuncionarioFake(3);
        FuncionarioFake funcionarios = new FuncionarioFake(new ContagemFuncionarios(12, 5));

        IndicadoresDashboard indicadores = servico(turnos, alocacoes, funcionarios).carregar(QUARTA);

        assertEquals(QUARTA, indicadores.diaReferencia());
        assertEquals(YearMonth.of(2026, 9), indicadores.mesReferencia(),
                "o mês dos indicadores mensais sai do dia informado");
        assertEquals(List.of(plantao), indicadores.plantoesDeHoje());
        assertEquals(12, indicadores.funcionarios().ativos());
        assertEquals(5, indicadores.funcionarios().inativos());
        assertEquals(3, indicadores.coberturasNoMes());
        assertEquals(4, indicadores.diasIncompletos());
        assertFalse(indicadores.semPlantaoHoje());

        assertEquals(QUARTA, turnos.diaConsultado, "o plantão de hoje é consultado pelo dia");
        assertEquals(YearMonth.of(2026, 9), turnos.mesConsultado);
        assertEquals(YearMonth.of(2026, 9), alocacoes.mesConsultado);
    }

    @Test
    void carregarSinalizaDiaSemPlantaoEmVezDeDevolverNulo() {
        IndicadoresDashboard indicadores = servico(vazio()).carregar(QUARTA);

        assertTrue(indicadores.plantoesDeHoje().isEmpty());
        assertTrue(indicadores.semPlantaoHoje(), "o card precisa saber que o dia está sem plantão");
    }

    @Test
    void carregarExigeODiaDeReferencia() {
        DashboardService servico = servico(vazio());

        assertThrows(NullPointerException.class, () -> servico.carregar(null));
    }

    // -----------------------------------------------------------------
    // Faixa da semana (issue #54)
    // -----------------------------------------------------------------

    /**
     * O off-by-one da #41: em {@code java.time} a semana começa na segunda
     * ({@code SUNDAY} é 7), mas a faixa começa no domingo. Uma quarta-feira tem
     * que cair na quarta coluna, não na terceira.
     */
    @Test
    void semanaCorrenteVaiDeDomingoASabadoComHojeNaColunaCerta() {
        IndicadoresDashboard indicadores = servico(vazio()).carregar(QUARTA);

        List<DiaDaSemana> semana = indicadores.semanaCorrente();
        assertEquals(7, semana.size(), "a faixa tem sempre sete colunas");
        assertEquals(LocalDate.of(2026, 8, 30), semana.get(0).dia(), "a semana começa no domingo");
        assertEquals(LocalDate.of(2026, 9, 5), semana.get(6).dia(), "e termina no sábado");

        assertEquals(3, indiceDeHoje(semana), "quarta-feira é a quarta coluna (índice 3)");
        assertEquals(QUARTA, semana.get(3).dia());
    }

    /** Domingo é o começo da própria semana, não o fim da anterior. */
    @Test
    void semanaDeUmDomingoComecaNoProprioDomingo() {
        LocalDate domingo = LocalDate.of(2026, 8, 30);

        List<DiaDaSemana> semana = servico(vazio()).carregar(domingo).semanaCorrente();

        assertEquals(domingo, semana.get(0).dia());
        assertEquals(LocalDate.of(2026, 9, 5), semana.get(6).dia());
        assertEquals(0, indiceDeHoje(semana), "domingo é a primeira coluna");
    }

    /** Sábado é o fim da própria semana, não o começo da seguinte. */
    @Test
    void semanaDeUmSabadoTerminaNoProprioSabado() {
        LocalDate sabado = LocalDate.of(2026, 9, 5);

        List<DiaDaSemana> semana = servico(vazio()).carregar(sabado).semanaCorrente();

        assertEquals(LocalDate.of(2026, 8, 30), semana.get(0).dia());
        assertEquals(sabado, semana.get(6).dia());
        assertEquals(6, indiceDeHoje(semana), "sábado é a última coluna");
    }

    @Test
    void semanaMantemODiaSemTurnoEAgrupaOsDoisTurnosDoMesmoDia() {
        // 12x36 na quarta: diurno e noturno começam na mesma data.
        EscalaTurno diurno = turno(1, "Diurno 12h", QUARTA.atTime(7, 0), QUARTA.atTime(19, 0), 1);
        alocar(diurno, 10, "Ana Paula Souza");
        EscalaTurno noturno = turno(2, "Noturno 12h", QUARTA.atTime(19, 0),
                QUARTA.plusDays(1).atTime(7, 0), 2);
        alocar(noturno, 11, "Bruno Carvalho Lima");

        IndicadoresDashboard indicadores =
                servico(new EscalaTurnoFake(List.of(), 0, List.of(diurno, noturno)),
                        new EscalaFuncionarioFake(0),
                        new FuncionarioFake(new ContagemFuncionarios(0, 0))).carregar(QUARTA);

        List<DiaDaSemana> semana = indicadores.semanaCorrente();
        DiaDaSemana quarta = semana.get(3);
        assertEquals(2, quarta.turnos().size(), "os dois turnos do 12x36 ficam na mesma coluna");
        assertTrue(quarta.hoje());
        assertFalse(quarta.semTurnos());

        assertTrue(semana.get(0).semTurnos(), "o domingo sem turno continua na faixa, vazio");
        assertFalse(indicadores.semanaSemEscala(), "a semana tem turno, não está vazia");
    }

    @Test
    void semanaSemEscalaSinalizaOEstadoVazioParaATela() {
        IndicadoresDashboard indicadores = servico(vazio()).carregar(QUARTA);

        assertEquals(7, indicadores.semanaCorrente().size(),
                "mês sem escala não some com as colunas, só as deixa vazias");
        assertTrue(indicadores.semanaCorrente().stream().allMatch(DiaDaSemana::semTurnos));
        assertTrue(indicadores.semanaSemEscala());
    }

    // -----------------------------------------------------------------
    // Proximos plantoes (issue #54)
    // -----------------------------------------------------------------

    @Test
    void proximosPlantoesComecamHojeIgnoramOPassadoESaemEmOrdem() {
        EscalaTurno anteontem = turno(1, "Plantão", QUARTA.minusDays(2).atTime(8, 0),
                QUARTA.minusDays(1).atTime(8, 0), 1);
        EscalaTurno hoje = turno(2, "Plantão", QUARTA.atTime(8, 0), QUARTA.plusDays(1).atTime(8, 0), 1);
        EscalaTurno depois = turno(3, "Plantão", QUARTA.plusDays(3).atTime(8, 0),
                QUARTA.plusDays(4).atTime(8, 0), 1);

        List<TurnoResumido> proximos = servico(
                new EscalaTurnoFake(List.of(), 0, List.of(anteontem, hoje, depois)),
                new EscalaFuncionarioFake(0),
                new FuncionarioFake(new ContagemFuncionarios(0, 0)))
                .carregar(QUARTA).proximosPlantoes();

        assertEquals(2, proximos.size(), "o turno que já passou não é 'próximo'");
        assertEquals(2, proximos.get(0).escalaTurnoId(), "o de hoje ainda é o próximo");
        assertEquals(3, proximos.get(1).escalaTurnoId());
    }

    @Test
    void proximosPlantoesSaoLimitadosAUmResumo() {
        List<EscalaTurno> muitos = new ArrayList<>();
        for (int dia = 0; dia < 20; dia++) {
            muitos.add(turno(dia + 1, "Plantão", QUARTA.plusDays(dia).atTime(8, 0),
                    QUARTA.plusDays(dia + 1).atTime(8, 0), 1));
        }

        List<TurnoResumido> proximos = servico(
                new EscalaTurnoFake(List.of(), 0, muitos),
                new EscalaFuncionarioFake(0),
                new FuncionarioFake(new ContagemFuncionarios(0, 0)))
                .carregar(QUARTA).proximosPlantoes();

        assertEquals(8, proximos.size(), "a tela mostra um resumo, não a agenda inteira");
        assertTrue(proximos.size() <= 10 && proximos.size() >= 5,
                "o limite tem que ficar na faixa de 5 a 10 linhas pedida pela issue");
    }

    @Test
    void semProximosPlantoesSinalizaOEstadoVazio() {
        IndicadoresDashboard indicadores = servico(vazio()).carregar(QUARTA);

        assertTrue(indicadores.proximosPlantoes().isEmpty());
        assertTrue(indicadores.semProximosPlantoes());
    }

    // -----------------------------------------------------------------
    // Efetivo e coberturas nos turnos resumidos
    // -----------------------------------------------------------------

    /**
     * O selo sai de {@code verificarEfetivo(turno, agentes)}, a sobrecarga que
     * usa os agentes já carregados. Se o serviço caísse na versão de um
     * argumento, o {@link EscalaFuncionarioFake} estouraria em
     * {@code listarPorTurno} — que é exatamente a consulta por turno que a
     * issue #45 tirou do calendário.
     */
    @Test
    void efetivoDoTurnoVemDaRegraSemVoltarAoBancoPorTurno() {
        EscalaTurno completo = turno(1, "Plantão", QUARTA.atTime(8, 0),
                QUARTA.plusDays(1).atTime(8, 0), 2);
        alocar(completo, 10, "Ana Paula Souza");
        alocar(completo, 11, "Bruno Carvalho Lima");

        EscalaTurno incompleto = turno(2, "Plantão", QUARTA.plusDays(1).atTime(8, 0),
                QUARTA.plusDays(2).atTime(8, 0), 2);
        alocar(incompleto, 10, "Ana Paula Souza");

        List<TurnoResumido> proximos = servico(
                new EscalaTurnoFake(List.of(), 0, List.of(completo, incompleto)),
                new EscalaFuncionarioFake(0),
                new FuncionarioFake(new ContagemFuncionarios(0, 0)))
                .carregar(QUARTA).proximosPlantoes();

        assertTrue(proximos.get(0).efetivo().completo(), "2 de 2 é Confirmado");
        assertEquals(2, proximos.get(0).efetivo().alocados());
        assertFalse(proximos.get(1).efetivo().completo(), "1 de 2 é Incompleto");
        assertEquals(1, proximos.get(1).efetivo().alocados());
    }

    /**
     * Cobertura vira um posto só: o titular ausente não aparece numa entrada
     * separada, vira o {@code titular} do posto de quem o substituiu — a mesma
     * leitura que o calendário faz da célula.
     */
    @Test
    void turnoComCoberturaJuntaTitularESubstitutoNoMesmoPosto() {
        EscalaTurno turno = turno(1, "Plantão", QUARTA.atTime(8, 0),
                QUARTA.plusDays(1).atTime(8, 0), 2);
        EscalaFuncionario titular = alocar(turno, 10, "Ana Paula Souza");
        EscalaFuncionario semCobertura = alocar(turno, 12, "Carla Menezes Dias");
        EscalaFuncionario substituto = alocar(turno, 11, "Bruno Carvalho Lima");
        substituto.setCoberturaDe(titular);

        TurnoResumido resumido = servico(
                new EscalaTurnoFake(List.of(), 0, List.of(turno)),
                new EscalaFuncionarioFake(0),
                new FuncionarioFake(new ContagemFuncionarios(0, 0)))
                .carregar(QUARTA).proximosPlantoes().get(0);

        assertEquals(2, resumido.postos().size(),
                "titular coberto e substituto ocupam um posto só");
        assertTrue(resumido.temCobertura());

        PostoDoTurno coberto = resumido.postos().get(0);
        assertEquals("Ana Paula Souza", coberto.titular());
        assertEquals("Bruno Carvalho Lima", coberto.substituto());
        assertTrue(coberto.coberto());

        PostoDoTurno proprio = resumido.postos().get(1);
        assertEquals("Carla Menezes Dias", proprio.titular());
        assertNull(proprio.substituto());
        assertFalse(proprio.coberto());
        assertEquals(semCobertura.getFuncionario().getNome(), proprio.titular());

        assertTrue(resumido.efetivo().completo(), "os dois postos ocupados batem o mínimo 2");
    }

    /**
     * Uma consulta só cobre a semana e os próximos plantões: ela começa no
     * domingo da semana (que pode ser anterior a hoje) e vai até o fim do
     * horizonte de futuro.
     */
    @Test
    void janelaConsultadaCobreDaSemanaAteOHorizonteDeProximosPlantoes() {
        EscalaTurnoFake turnos = new EscalaTurnoFake(List.of(), 0, List.of());

        servico(turnos, new EscalaFuncionarioFake(0),
                new FuncionarioFake(new ContagemFuncionarios(0, 0))).carregar(QUARTA);

        assertEquals(1, turnos.chamadasBuscarPorPeriodo,
                "semana e próximos plantões saem da mesma consulta");
        assertEquals(LocalDate.of(2026, 8, 30).atStartOfDay(), turnos.inicioConsultado,
                "a janela começa no domingo da semana, mesmo já tendo passado");
        assertTrue(turnos.fimConsultado.isAfter(QUARTA.plusDays(30).atStartOfDay()),
                "e vai longe o bastante para achar os próximos plantões");
    }

    // -----------------------------------------------------------------
    // Apoio
    // -----------------------------------------------------------------

    private int indiceDeHoje(List<DiaDaSemana> semana) {
        for (int indice = 0; indice < semana.size(); indice++) {
            if (semana.get(indice).hoje()) {
                return indice;
            }
        }
        return -1;
    }

    private EscalaTurnoFake vazio() {
        return new EscalaTurnoFake(List.of(), 0, List.of());
    }

    private DashboardService servico(EscalaTurnoFake turnos) {
        return servico(turnos, new EscalaFuncionarioFake(0),
                new FuncionarioFake(new ContagemFuncionarios(0, 0)));
    }

    /**
     * A regra recebe o mesmo dublê de alocações usado pelo serviço: como ele
     * estoura em {@code listarPorTurno}, qualquer volta ao banco por turno
     * quebra o teste em vez de passar despercebida.
     */
    private DashboardService servico(EscalaTurnoFake turnos, EscalaFuncionarioFake alocacoes,
                                     FuncionarioFake funcionarios) {
        return new DashboardServiceImpl(turnos, alocacoes, funcionarios,
                new RegraEscalaServiceImpl(alocacoes));
    }

    private EscalaTurno turno(int id, String tipo, LocalDateTime inicio, LocalDateTime fim,
                              int minAgentes) {
        TipoTurno tipoTurno = new TipoTurno();
        tipoTurno.setId(id);
        tipoTurno.setNome(tipo);

        EscalaTurno turno = new EscalaTurno();
        turno.setId(id);
        turno.setTipoTurno(tipoTurno);
        turno.setInicio(inicio);
        turno.setFim(fim);
        turno.setMinAgentes(minAgentes);
        return turno;
    }

    private EscalaFuncionario alocar(EscalaTurno turno, int id, String nome) {
        Funcionario funcionario = new Funcionario();
        funcionario.setId(id);
        funcionario.setNome(nome);

        EscalaFuncionario alocacao = new EscalaFuncionario();
        alocacao.setId(id);
        alocacao.setEscalaTurno(turno);
        alocacao.setFuncionario(funcionario);
        turno.getAgentes().add(alocacao);
        return alocacao;
    }

    // -----------------------------------------------------------------
    // Dublês: só os métodos que o dashboard usa respondem.
    // -----------------------------------------------------------------

    private static final class EscalaTurnoFake implements EscalaTurnoRepository {

        private final List<PlantaoDoDiaItem> plantoes;
        private final int diasIncompletos;
        private final List<EscalaTurno> turnosDaJanela;
        private LocalDate diaConsultado;
        private YearMonth mesConsultado;
        private LocalDateTime inicioConsultado;
        private LocalDateTime fimConsultado;
        private int chamadasBuscarPorPeriodo;

        private EscalaTurnoFake(List<PlantaoDoDiaItem> plantoes, int diasIncompletos,
                                List<EscalaTurno> turnosDaJanela) {
            this.plantoes = plantoes;
            this.diasIncompletos = diasIncompletos;
            this.turnosDaJanela = turnosDaJanela;
        }

        @Override
        public List<PlantaoDoDiaItem> resumirPlantoesDoDia(LocalDate dia) {
            diaConsultado = dia;
            return plantoes;
        }

        @Override
        public int contarDiasComEfetivoIncompleto(YearMonth mes) {
            mesConsultado = mes;
            return diasIncompletos;
        }

        /** Devolve só o que cai na janela pedida, como o SQL faria. */
        @Override
        public List<EscalaTurno> buscarPorPeriodo(LocalDateTime inicio, LocalDateTime fim) {
            chamadasBuscarPorPeriodo++;
            inicioConsultado = inicio;
            fimConsultado = fim;
            return turnosDaJanela.stream()
                    .filter(turno -> !turno.getInicio().isBefore(inicio) && turno.getInicio().isBefore(fim))
                    .sorted((um, outro) -> um.getInicio().compareTo(outro.getInicio()))
                    .toList();
        }

        @Override
        public Optional<EscalaTurno> buscarPorId(int id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public EscalaTurno salvar(EscalaTurno turno) {
            throw new UnsupportedOperationException();
        }

        @Override
        public EscalaTurno salvar(EscalaTurno turno, Connection conexao) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void removerPorMes(YearMonth mes) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void removerPorMes(YearMonth mes, Connection conexao) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class EscalaFuncionarioFake implements EscalaFuncionarioRepository {

        private final int coberturas;
        private YearMonth mesConsultado;

        private EscalaFuncionarioFake(int coberturas) {
            this.coberturas = coberturas;
        }

        @Override
        public int contarCoberturasDoMes(YearMonth mes) {
            mesConsultado = mes;
            return coberturas;
        }

        @Override
        public List<EscalaFuncionario> listarPorTurno(int escalaTurnoId) {
            throw new UnsupportedOperationException(
                    "o dashboard usa os agentes já carregados, sem consulta por turno");
        }

        @Override
        public List<EscalaFuncionario> listarPorFuncionario(int funcionarioId,
                                                            LocalDateTime inicio, LocalDateTime fim) {
            throw new UnsupportedOperationException();
        }

        @Override
        public EscalaFuncionario inserir(EscalaFuncionario escalaFuncionario) {
            throw new UnsupportedOperationException();
        }

        @Override
        public EscalaFuncionario inserir(EscalaFuncionario escalaFuncionario, Connection conexao) {
            throw new UnsupportedOperationException();
        }

        @Override
        public EscalaFuncionario atualizar(EscalaFuncionario escalaFuncionario, Connection conexao) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void remover(int id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void remover(int id, Connection conexao) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<EscalaFuncionario> buscarCoberturasDoMes(YearMonth mes) {
            throw new UnsupportedOperationException("o dashboard conta no banco, não em memória");
        }

        @Override
        public List<CoberturaListagemItem> listarCoberturasParaListagem(YearMonth mes) {
            throw new UnsupportedOperationException("o dashboard conta no banco, não em memória");
        }
    }

    private static final class FuncionarioFake implements FuncionarioRepository {

        private final ContagemFuncionarios contagem;

        private FuncionarioFake(ContagemFuncionarios contagem) {
            this.contagem = contagem;
        }

        @Override
        public ContagemFuncionarios contarPorStatus() {
            return contagem;
        }

        @Override
        public List<Funcionario> listar(Boolean ativo, String textoBusca) {
            throw new UnsupportedOperationException("o dashboard conta no banco, não em memória");
        }

        @Override
        public Optional<Funcionario> buscarPorId(int id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Funcionario inserir(Funcionario funcionario) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Funcionario atualizar(Funcionario funcionario) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void ativar(int id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void desativar(int id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean existeMatricula(String matricula, Integer idParaExcluir) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int contarPlantoesNoMes(int funcionarioId, YearMonth mes) {
            throw new UnsupportedOperationException();
        }
    }
}
