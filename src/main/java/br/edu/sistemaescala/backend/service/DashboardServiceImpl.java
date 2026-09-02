package br.edu.sistemaescala.backend.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import br.edu.sistemaescala.backend.model.EscalaFuncionario;
import br.edu.sistemaescala.backend.model.EscalaTurno;
import br.edu.sistemaescala.backend.model.Funcionario;
import br.edu.sistemaescala.backend.repository.EscalaFuncionarioRepository;
import br.edu.sistemaescala.backend.repository.EscalaTurnoRepository;
import br.edu.sistemaescala.backend.repository.FuncionarioRepository;
import br.edu.sistemaescala.backend.repository.jdbc.EscalaFuncionarioRepositoryJdbc;
import br.edu.sistemaescala.backend.repository.jdbc.EscalaTurnoRepositoryJdbc;
import br.edu.sistemaescala.backend.repository.jdbc.FuncionarioRepositoryJdbc;

/**
 * Implementação de {@link DashboardService}.
 *
 * <p>Os quatro indicadores da issue #53 são quatro consultas agregadas, uma por
 * card. A semana e os próximos plantões da issue #54 saem de <b>uma</b>
 * {@link EscalaTurnoRepository#buscarPorPeriodo} só, cobrindo do domingo da
 * semana até o fim do horizonte de próximos plantões: ela já traz cada turno
 * com o tipo e os agentes hidratados no mesmo JOIN, então não há consulta por
 * dia nem por turno.</p>
 *
 * <p>Fala direto com os repositórios em vez de reaproveitar os serviços de
 * cobertura e de escala de propósito: os métodos que eles expõem devolvem
 * listagens completas, e contar o tamanho delas é exatamente o que a issue #53
 * proíbe. A exceção é o {@link RegraEscalaService}, usado aqui na sobrecarga
 * que recebe os agentes já carregados — assim o selo Confirmado/Incompleto sai
 * da mesma regra que pinta a célula do calendário, sem uma consulta por
 * turno.</p>
 */
public class DashboardServiceImpl implements DashboardService {

    /** Quantos plantões futuros a tela lista. É um resumo, não a agenda inteira. */
    private static final int LIMITE_PROXIMOS_PLANTOES = 8;

    /**
     * Até onde a consulta olha para a frente atrás dos próximos plantões.
     *
     * <p>Um mês cobre com folga o {@link #LIMITE_PROXIMOS_PLANTOES} mesmo no
     * regime mais esparso, e mantém a janela fechada: sem horizonte, um banco
     * com anos de escala montada leria tudo para mostrar oito linhas.</p>
     */
    private static final int HORIZONTE_PROXIMOS_DIAS = 31;

    private static final int DIAS_NA_SEMANA = 7;

    private final EscalaTurnoRepository escalaTurnoRepository;
    private final EscalaFuncionarioRepository escalaFuncionarioRepository;
    private final FuncionarioRepository funcionarioRepository;
    private final RegraEscalaService regraEscalaService;

    /** Construtor de conveniência com os repositórios JDBC padrão. */
    public DashboardServiceImpl() {
        this(new EscalaTurnoRepositoryJdbc(), new EscalaFuncionarioRepositoryJdbc(),
                new FuncionarioRepositoryJdbc(), new RegraEscalaServiceImpl());
    }

    public DashboardServiceImpl(EscalaTurnoRepository escalaTurnoRepository,
                                EscalaFuncionarioRepository escalaFuncionarioRepository,
                                FuncionarioRepository funcionarioRepository) {
        this(escalaTurnoRepository, escalaFuncionarioRepository, funcionarioRepository,
                new RegraEscalaServiceImpl());
    }

    public DashboardServiceImpl(EscalaTurnoRepository escalaTurnoRepository,
                                EscalaFuncionarioRepository escalaFuncionarioRepository,
                                FuncionarioRepository funcionarioRepository,
                                RegraEscalaService regraEscalaService) {
        this.escalaTurnoRepository = Objects.requireNonNull(escalaTurnoRepository);
        this.escalaFuncionarioRepository = Objects.requireNonNull(escalaFuncionarioRepository);
        this.funcionarioRepository = Objects.requireNonNull(funcionarioRepository);
        this.regraEscalaService = Objects.requireNonNull(regraEscalaService);
    }

    @Override
    public IndicadoresDashboard carregar(LocalDate diaReferencia) {
        Objects.requireNonNull(diaReferencia, "diaReferencia não pode ser nulo");
        YearMonth mes = YearMonth.from(diaReferencia);

        List<PlantaoDoDiaItem> plantoesDeHoje = escalaTurnoRepository.resumirPlantoesDoDia(diaReferencia);
        ContagemFuncionarios funcionarios = funcionarioRepository.contarPorStatus();
        int coberturas = escalaFuncionarioRepository.contarCoberturasDoMes(mes);
        int diasIncompletos = escalaTurnoRepository.contarDiasComEfetivoIncompleto(mes);

        LocalDate domingo = domingoDaSemanaDe(diaReferencia);
        List<TurnoResumido> turnosDaJanela = resumirJanela(domingo, diaReferencia);

        return new IndicadoresDashboard(diaReferencia, mes, plantoesDeHoje, funcionarios,
                coberturas, diasIncompletos,
                montarSemana(domingo, diaReferencia, turnosDaJanela),
                selecionarProximos(diaReferencia, turnosDaJanela));
    }

    // -----------------------------------------------------------------
    // Janela consultada
    // -----------------------------------------------------------------

    /**
     * Domingo da semana que contém o dia informado.
     *
     * <p>Mesmo ajuste de {@code DayOfWeek} que a grade do calendário faz: em
     * {@code java.time} a semana começa na segunda ({@code MONDAY} é 1 e
     * {@code SUNDAY} é 7), mas a faixa começa no domingo. O resto por 7 leva o
     * domingo a 0 e mantém segunda..sábado em 1..6, que é justamente quantos
     * dias há entre o domingo e o dia informado.</p>
     */
    private LocalDate domingoDaSemanaDe(LocalDate dia) {
        DayOfWeek diaDaSemana = dia.getDayOfWeek();
        return dia.minusDays(diaDaSemana.getValue() % DIAS_NA_SEMANA);
    }

    /**
     * A consulta única: do domingo da semana até o fim do horizonte de próximos
     * plantões, o que for mais longe.
     *
     * <p>A janela começa no domingo mesmo quando ele já passou, porque a faixa
     * mostra a semana inteira — inclusive os dias anteriores a hoje.</p>
     */
    private List<TurnoResumido> resumirJanela(LocalDate domingo, LocalDate diaReferencia) {
        LocalDate sabado = domingo.plusDays(DIAS_NA_SEMANA - 1);
        LocalDate ultimoDia = diaReferencia.plusDays(HORIZONTE_PROXIMOS_DIAS);
        LocalDateTime fim = (sabado.isAfter(ultimoDia) ? sabado : ultimoDia).plusDays(1).atStartOfDay();

        return escalaTurnoRepository.buscarPorPeriodo(domingo.atStartOfDay(), fim).stream()
                .map(this::resumir)
                .toList();
    }

    /**
     * Traduz o turno hidratado no que as duas visões precisam.
     *
     * <p>O efetivo sai da sobrecarga de {@code verificarEfetivo} que recebe os
     * agentes em mãos. A versão de um argumento voltaria ao banco uma vez por
     * turno — a consulta por turno que a issue #45 tirou do calendário.</p>
     */
    private TurnoResumido resumir(EscalaTurno turno) {
        List<EscalaFuncionario> agentes = agentesDoTurno(turno);
        return new TurnoResumido(
                turno.getId() != null ? turno.getId() : 0,
                nomeDoTipoDeTurno(turno),
                turno.getInicio(),
                turno.getFim(),
                montarPostos(agentes),
                regraEscalaService.verificarEfetivo(turno, agentes));
    }

    /** Nunca null, para as regras e a contagem não precisarem tratar o caso. */
    private List<EscalaFuncionario> agentesDoTurno(EscalaTurno turno) {
        return turno.getAgentes() != null ? turno.getAgentes() : List.of();
    }

    private String nomeDoTipoDeTurno(EscalaTurno turno) {
        return turno.getTipoTurno() != null && turno.getTipoTurno().getNome() != null
                ? turno.getTipoTurno().getNome()
                : "Turno sem tipo";
    }

    /**
     * Casa cada substituto com o titular que ele cobre, na mesma regra que o
     * calendário usa para descrever os agentes da célula: as duas linhas de uma
     * cobertura ocupam um posto só, então a do titular substituído não vira uma
     * entrada separada — ela vira o {@code titular} do posto de quem cobriu.
     */
    private List<PostoDoTurno> montarPostos(List<EscalaFuncionario> agentes) {
        Map<Integer, EscalaFuncionario> substitutoPorTitular = new LinkedHashMap<>();
        for (EscalaFuncionario alocacao : agentes) {
            EscalaFuncionario coberta = alocacao.getCoberturaDe();
            if (coberta != null && coberta.getId() != null) {
                substitutoPorTitular.put(coberta.getId(), alocacao);
            }
        }
        Set<Integer> titularesPresentes = agentes.stream()
                .map(EscalaFuncionario::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        List<PostoDoTurno> postos = new ArrayList<>();
        for (EscalaFuncionario alocacao : agentes) {
            EscalaFuncionario coberta = alocacao.getCoberturaDe();
            if (coberta != null && coberta.getId() != null && titularesPresentes.contains(coberta.getId())) {
                // Ja vai aparecer como substituto no posto do titular.
                continue;
            }
            EscalaFuncionario substituto = coberta == null ? substitutoPorTitular.get(alocacao.getId()) : null;
            postos.add(new PostoDoTurno(nomeDe(alocacao),
                    substituto != null ? nomeDe(substituto) : null));
        }
        return postos;
    }

    private String nomeDe(EscalaFuncionario alocacao) {
        Funcionario funcionario = alocacao.getFuncionario();
        return funcionario != null && funcionario.getNome() != null
                ? funcionario.getNome()
                : "(sem nome)";
    }

    // -----------------------------------------------------------------
    // As duas visoes sobre a mesma janela
    // -----------------------------------------------------------------

    /**
     * Os sete dias, domingo a sábado, cada um com os turnos que começam nele.
     *
     * <p>Um dia sem turno entra na lista mesmo assim, com {@code turnos} vazio:
     * a faixa tem sempre sete colunas, senão os dias escalados escorregariam
     * para debaixo do cabeçalho errado.</p>
     */
    private List<DiaDaSemana> montarSemana(LocalDate domingo, LocalDate diaReferencia,
                                           List<TurnoResumido> turnosDaJanela) {
        Map<LocalDate, List<TurnoResumido>> porDia = turnosDaJanela.stream()
                .collect(Collectors.groupingBy(TurnoResumido::dia));

        List<DiaDaSemana> semana = new ArrayList<>(DIAS_NA_SEMANA);
        for (int passo = 0; passo < DIAS_NA_SEMANA; passo++) {
            LocalDate dia = domingo.plusDays(passo);
            semana.add(new DiaDaSemana(dia, dia.isEqual(diaReferencia),
                    porDia.getOrDefault(dia, List.of())));
        }
        return semana;
    }

    /**
     * Os próximos plantões a partir de hoje, já em ordem de início — a consulta
     * devolve ordenado por {@code inicio}, então basta recortar.
     *
     * <p>Começa em hoje, não em amanhã: o plantão que ainda vai entrar hoje é
     * justamente o próximo.</p>
     */
    private List<TurnoResumido> selecionarProximos(LocalDate diaReferencia,
                                                   List<TurnoResumido> turnosDaJanela) {
        LocalDateTime aPartirDe = diaReferencia.atStartOfDay();
        return turnosDaJanela.stream()
                .filter(turno -> !turno.inicio().isBefore(aPartirDe))
                .limit(LIMITE_PROXIMOS_PLANTOES)
                .toList();
    }
}
