package br.edu.sistemaescala.backend.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import br.edu.sistemaescala.backend.dao.TransacaoUtil;
import br.edu.sistemaescala.backend.model.EscalaFuncionario;
import br.edu.sistemaescala.backend.model.EscalaTurno;
import br.edu.sistemaescala.backend.model.Funcionario;
import br.edu.sistemaescala.backend.model.TipoTurno;
import br.edu.sistemaescala.backend.repository.EscalaFuncionarioRepository;
import br.edu.sistemaescala.backend.repository.EscalaTurnoRepository;
import br.edu.sistemaescala.backend.repository.FuncionarioRepository;
import br.edu.sistemaescala.backend.repository.TipoTurnoRepository;
import br.edu.sistemaescala.backend.repository.jdbc.EscalaFuncionarioRepositoryJdbc;
import br.edu.sistemaescala.backend.repository.jdbc.EscalaTurnoRepositoryJdbc;
import br.edu.sistemaescala.backend.repository.jdbc.FuncionarioRepositoryJdbc;
import br.edu.sistemaescala.backend.repository.jdbc.TipoTurnoRepositoryJdbc;

/**
 * Implementação do gerador automático de rodízio mensal (Issue #43).
 *
 * <p>O trabalho acontece em duas etapas bem separadas:</p>
 *
 * <ol>
 *   <li><b>Planejamento, em memória.</b> O mês inteiro é montado numa
 *       {@link PlanoDoMes} sem tocar no banco para escrita. Cada candidato
 *       passa por {@link RegraEscalaService#podeAlocar} e
 *       {@link RegraEscalaService#verificarDescanso} antes de entrar no
 *       plano.</li>
 *   <li><b>Gravação, numa transação só.</b> Todos os turnos e alocações vão
 *       para o banco dentro de {@link TransacaoUtil#executar}: commit único ou
 *       nada, para uma falha no meio não deixar meio mês gerado.</li>
 * </ol>
 *
 * <p><b>Por que o planejamento não consulta o banco a cada candidato.</b> As
 * regras precisam enxergar o que o próprio gerador acabou de decidir — senão o
 * dia 2 seria montado ignorando quem foi escalado no dia 1 e o descanso seria
 * violado dentro do mesmo mês. Como cada método de repositório abre a própria
 * Connection, nada gravado numa transação aberta ficaria visível para eles
 * antes do commit. Por isso as regras rodam sobre a {@link VisaoDeAlocacoes}:
 * um retrato em memória com os plantões já gravados dos meses vizinhos <i>mais
 * </i> as alocações deste plano. É também o que faz a sobrescrita funcionar —
 * os turnos do mês que será apagado ficam de fora do retrato, em vez de
 * aparecerem como sobreposição contra a escala nova.</p>
 *
 * <p><b>Ordem do rodízio.</b> A sequência circular é de funcionários
 * individuais ordenados por matrícula. Como {@code matricula} é VARCHAR, a
 * ordenação é <b>alfabética, não numérica</b>: "PC-10432" vem antes de
 * "PC-9999". É determinístico, que é o que o rodízio exige. A lista vem de
 * {@link FuncionarioRepository#listar}, que ordena por nome (a ordem certa para
 * as telas de listagem), e é reordenada aqui.</p>
 *
 * <p><b>Turno incompleto.</b> Um turno que não alcança o mínimo de agentes é
 * descartado inteiro, em vez de ficar com meia equipe: ver
 * {@link #preencherTurno}, onde está a razão — é o que impede o rodízio de
 * congelar em duplas fixas.</p>
 *
 * <p><b>Regime.</b> Não há caso especial por regime: um tipo de turno ativo
 * gera um turno por dia (24x72), dois tipos ativos geram dois turnos por dia
 * (12x36). O que muda é a configuração, não este laço.</p>
 */
public class GeradorRodizioServiceImpl implements GeradorRodizioService {

    private static final int SEGUNDOS_POR_HORA = 3600;

    /**
     * Meses vizinhos que entram no retrato usado pelas regras.
     *
     * <p>Um mês para trás sustenta a continuidade do rodízio e o descanso na
     * virada; um mês para frente cobre o caso de já existir escala no mês
     * seguinte, porque o descanso também olha para a frente.</p>
     */
    private static final int MESES_DE_CONTEXTO = 1;

    private static final Locale LOCALE_BR = Locale.forLanguageTag("pt-BR");
    private static final DateTimeFormatter FORMATO_MES_ANO =
            DateTimeFormatter.ofPattern("MMMM 'de' yyyy", LOCALE_BR);

    private final EscalaTurnoRepository escalaTurnoRepository;
    private final EscalaFuncionarioRepository escalaFuncionarioRepository;
    private final FuncionarioRepository funcionarioRepository;
    private final TipoTurnoRepository tipoTurnoRepository;

    public GeradorRodizioServiceImpl() {
        this(new EscalaTurnoRepositoryJdbc(), new EscalaFuncionarioRepositoryJdbc(),
                new FuncionarioRepositoryJdbc(), new TipoTurnoRepositoryJdbc());
    }

    public GeradorRodizioServiceImpl(EscalaTurnoRepository escalaTurnoRepository,
                                     EscalaFuncionarioRepository escalaFuncionarioRepository,
                                     FuncionarioRepository funcionarioRepository,
                                     TipoTurnoRepository tipoTurnoRepository) {
        this.escalaTurnoRepository = Objects.requireNonNull(escalaTurnoRepository,
                "escalaTurnoRepository não pode ser nulo");
        this.escalaFuncionarioRepository = Objects.requireNonNull(escalaFuncionarioRepository,
                "escalaFuncionarioRepository não pode ser nulo");
        this.funcionarioRepository = Objects.requireNonNull(funcionarioRepository,
                "funcionarioRepository não pode ser nulo");
        this.tipoTurnoRepository = Objects.requireNonNull(tipoTurnoRepository,
                "tipoTurnoRepository não pode ser nulo");
    }

    @Override
    public ResultadoGeracao gerarMes(YearMonth mes, boolean sobrescrever) {
        Objects.requireNonNull(mes, "mes não pode ser nulo");

        List<TipoTurno> tiposAtivos = tiposAtivosGeraveis();
        if (tiposAtivos.isEmpty()) {
            return recusa("Nenhum tipo de turno ativo com hora de início e duração definidas. "
                    + "Cadastre o regime de trabalho antes de gerar o rodízio.");
        }

        List<Funcionario> fila = funcionariosAtivosPorMatricula();
        if (fila.isEmpty()) {
            return recusa("Nenhum funcionário ativo cadastrado: não há quem escalar.");
        }

        // Uma única consulta traz o mês e os vizinhos, com tipoTurno e agentes
        // já hidratados: serve à checagem de mês preenchido, ao retrato das
        // regras e à continuidade do rodízio.
        List<EscalaTurno> contexto = escalaTurnoRepository.buscarPorPeriodo(
                inicioDe(mes.minusMonths(MESES_DE_CONTEXTO)),
                inicioDe(mes.plusMonths(MESES_DE_CONTEXTO + 1)));

        List<EscalaTurno> turnosDoMes = turnosDoMes(contexto, mes);
        if (!turnosDoMes.isEmpty() && !sobrescrever) {
            return recusa(String.format(
                    "%s já tem %d turno(s) montado(s). Confirme a substituição para gerar o rodízio de novo.",
                    descreverMes(mes), turnosDoMes.size()));
        }

        PlanoDoMes plano = planejar(mes, tiposAtivos, fila, contexto);
        gravar(mes, plano, !turnosDoMes.isEmpty());

        return new ResultadoGeracao(true, plano.turnos().size(), plano.alocacoes().size(),
                plano.diasIncompletos().size(), descreverGeracao(mes, plano));
    }

    // -----------------------------------------------------------------
    // Planejamento
    // -----------------------------------------------------------------

    /**
     * Monta o mês inteiro em memória: para cada dia, um turno por tipo ativo, e
     * para cada turno, agentes tirados da fila circular até o mínimo.
     */
    private PlanoDoMes planejar(YearMonth mes, List<TipoTurno> tiposAtivos,
                                List<Funcionario> fila, List<EscalaTurno> contexto) {
        PlanoDoMes plano = new PlanoDoMes();
        // As regras enxergam os meses vizinhos já gravados mais o que este
        // plano vai acumulando; o mês gerado é ignorado porque será
        // substituído.
        RegraEscalaService regras =
                new RegraEscalaServiceImpl(new VisaoDeAlocacoes(alocacoesVizinhas(contexto, mes), plano));

        int posicao = posicaoInicial(fila, contexto, mes);

        for (int diaDoMes = 1; diaDoMes <= mes.lengthOfMonth(); diaDoMes++) {
            LocalDate dia = mes.atDay(diaDoMes);
            for (TipoTurno tipoTurno : tiposAtivos) {
                EscalaTurno turno = criarTurno(dia, tipoTurno, plano.proximoIdProvisorio());
                plano.adicionarTurno(turno);
                posicao = preencherTurno(turno, fila, posicao, regras, plano);
            }
        }
        return plano;
    }

    /**
     * Puxa agentes da fila circular até o turno bater o mínimo — ou não escala
     * ninguém, se o mínimo não for alcançável.
     *
     * <p>Candidato que as regras recusam é pulado e a fila avança para o
     * seguinte; nunca se força uma alocação.</p>
     *
     * <p><b>Tudo ou nada.</b> Quando a fila inteira é percorrida e o mínimo não
     * fecha, a alocação parcial é desfeita e o turno fica vazio, com o dia
     * contabilizado como incompleto. Não é só porque um turno abaixo do mínimo
     * não é operacional: escalar o agente solitário é o que <b>congela o
     * rodízio</b>. Ele passa a descansar em fase com aquele dia e volta a ficar
     * livre sempre na mesma posição do ciclo, o que trava para sempre quem
     * trabalha com quem — foi assim que "a e b" viraram dupla fixa e "c" passou
     * o mês inteiro sozinho. Segurando esse agente, o ciclo de descanso dele
     * desloca e as duplas voltam a girar (a+b, depois c+a, depois b+c).</p>
     *
     * <p>A troca não custa cobertura de verdade: o que se perde são turnos que
     * já estavam abaixo do mínimo. A quantidade de turnos <i>completos</i> é a
     * mesma, e onde o efetivo dá conta de todo dia nada muda.</p>
     *
     * @return a posição da fila em que o próximo turno deve começar; a fila não
     *         anda quando o turno é descartado, porque ninguém foi consumido
     */
    private int preencherTurno(EscalaTurno turno, List<Funcionario> fila, int posicao,
                               RegraEscalaService regras, PlanoDoMes plano) {
        int posicaoAntesDoTurno = posicao;
        int alocados = 0;
        int recusadosSeguidos = 0;

        while (alocados < turno.getMinAgentes() && recusadosSeguidos < fila.size()) {
            Funcionario candidato = fila.get(posicao);
            posicao = (posicao + 1) % fila.size();

            if (disponivel(candidato, turno, regras)) {
                plano.alocar(turno, candidato);
                alocados++;
                // Cada vaga do turno tem direito a uma volta inteira na fila.
                recusadosSeguidos = 0;
            } else {
                recusadosSeguidos++;
            }
        }

        if (alocados < turno.getMinAgentes()) {
            plano.desfazerAlocacoes(turno);
            plano.marcarDiaIncompleto(turno.getInicio().toLocalDate());
            return posicaoAntesDoTurno;
        }
        return posicao;
    }

    /** As duas regras já construídas, nesta ordem: duplicidade/sobreposição (#23) e descanso (#40). */
    private boolean disponivel(Funcionario candidato, EscalaTurno turno, RegraEscalaService regras) {
        if (candidato.getId() == null) {
            return false;
        }
        if (!regras.podeAlocar(candidato.getId(), turno).permitido()) {
            return false;
        }
        return regras.verificarDescanso(candidato.getId(), turno).respeitado();
    }

    private EscalaTurno criarTurno(LocalDate dia, TipoTurno tipoTurno, int idProvisorio) {
        LocalDateTime inicio = dia.atTime(tipoTurno.getHoraInicio());

        EscalaTurno turno = new EscalaTurno();
        // Id provisório: as regras precisam distinguir um turno do outro antes
        // de existir linha no banco. O id real vem do INSERT, na gravação.
        turno.setId(idProvisorio);
        turno.setTipoTurno(tipoTurno);
        turno.setInicio(inicio);
        turno.setFim(inicio.plus(duracao(tipoTurno)));
        // Mínimo e máximo são herdados do tipo, que é onde o regime está configurado.
        turno.setMinAgentes(tipoTurno.getMinAgentes());
        turno.setMaxAgentes(tipoTurno.getMaxAgentes());
        turno.setAtivo(true);
        return turno;
    }

    // -----------------------------------------------------------------
    // Continuidade entre meses
    // -----------------------------------------------------------------

    /**
     * Posição em que a fila circular começa: a do <b>próximo</b> depois do
     * último funcionário alocado no mês anterior.
     *
     * <p>É o que faz o rodízio de setembro continuar de onde agosto parou, em
     * vez de recomeçar do primeiro da lista todo mês. Sem mês anterior com
     * escala — ou se quem fechou o mês não está mais ativo — a fila começa do
     * início.</p>
     *
     * <p>A posição sozinha não garante o descanso na virada: quem garante é o
     * {@link RegraEscalaService#verificarDescanso}, que enxerga os plantões do
     * mês anterior pelo retrato.</p>
     */
    private int posicaoInicial(List<Funcionario> fila, List<EscalaTurno> contexto, YearMonth mes) {
        Funcionario ultimo = ultimoAlocadoEm(contexto, mes.minusMonths(1));
        if (ultimo == null || ultimo.getId() == null) {
            return 0;
        }
        for (int posicao = 0; posicao < fila.size(); posicao++) {
            if (Objects.equals(fila.get(posicao).getId(), ultimo.getId())) {
                return (posicao + 1) % fila.size();
            }
        }
        return 0;
    }

    /**
     * Último funcionário alocado no mês: o último agente do turno que começa
     * mais tarde. Dentro de um turno os agentes vêm ordenados pelo id da
     * alocação, que é a própria ordem em que foram escalados.
     */
    private Funcionario ultimoAlocadoEm(List<EscalaTurno> contexto, YearMonth mes) {
        EscalaTurno ultimoTurno = null;
        for (EscalaTurno turno : contexto) {
            if (turno.getInicio() == null || !YearMonth.from(turno.getInicio()).equals(mes)) {
                continue;
            }
            if (turno.getAgentes() == null || turno.getAgentes().isEmpty()) {
                continue;
            }
            // Empate no horário fica com o último da consulta, que traz os
            // turnos ordenados por inicio e id.
            if (ultimoTurno == null || !turno.getInicio().isBefore(ultimoTurno.getInicio())) {
                ultimoTurno = turno;
            }
        }
        if (ultimoTurno == null) {
            return null;
        }
        List<EscalaFuncionario> agentes = ultimoTurno.getAgentes();
        return agentes.get(agentes.size() - 1).getFuncionario();
    }

    // -----------------------------------------------------------------
    // Gravação
    // -----------------------------------------------------------------

    /**
     * Grava o plano inteiro numa transação só. Quando o mês está sendo
     * sobrescrito, a remoção do antigo entra na mesma transação: se a gravação
     * do novo falhar, o mês volta como estava em vez de ficar vazio.
     */
    private void gravar(YearMonth mes, PlanoDoMes plano, boolean removerMesAtual) {
        TransacaoUtil.executar(conexao -> {
            if (removerMesAtual) {
                escalaTurnoRepository.removerPorMes(mes, conexao);
            }
            for (EscalaTurno turno : plano.turnos()) {
                // Os agentes são buscados pelo id provisório, antes de ele dar
                // lugar ao id real que o banco vai gerar.
                List<Funcionario> agentes = plano.agentesDe(turno);
                turno.setId(null);
                escalaTurnoRepository.salvar(turno, conexao);

                for (Funcionario funcionario : agentes) {
                    EscalaFuncionario alocacao = new EscalaFuncionario();
                    alocacao.setEscalaTurno(turno);
                    alocacao.setFuncionario(funcionario);
                    escalaFuncionarioRepository.inserir(alocacao, conexao);
                }
            }
            return null;
        });
    }

    // -----------------------------------------------------------------
    // Dados de entrada
    // -----------------------------------------------------------------

    /**
     * Tipos de turno ativos que dão para gerar. Sem hora de início ou sem
     * duração não há como calcular o período do turno, então o tipo fica de
     * fora em vez de gerar um turno quebrado.
     */
    private List<TipoTurno> tiposAtivosGeraveis() {
        return tipoTurnoRepository.listar(true).stream()
                .filter(tipo -> tipo.getHoraInicio() != null)
                .filter(tipo -> tipo.getDuracaoHoras() != null)
                .toList();
    }

    /**
     * Funcionários ativos na ordem do rodízio. A ordenação é alfabética por
     * matrícula (VARCHAR), não numérica, e o id desempata matrículas iguais
     * para a sequência nunca depender da ordem que o banco devolveu.
     */
    private List<Funcionario> funcionariosAtivosPorMatricula() {
        return funcionarioRepository.listar(true, null).stream()
                .sorted(Comparator
                        .comparing(Funcionario::getMatricula,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Funcionario::getId,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private List<EscalaTurno> turnosDoMes(List<EscalaTurno> contexto, YearMonth mes) {
        return contexto.stream()
                .filter(turno -> turno.getInicio() != null)
                .filter(turno -> YearMonth.from(turno.getInicio()).equals(mes))
                .toList();
    }

    /**
     * Alocações já gravadas nos meses vizinhos. O mês gerado fica de fora: o
     * que havia nele ou não existe, ou está prestes a ser apagado pela
     * sobrescrita, e contá-lo faria toda alocação nova parecer sobreposição.
     */
    private List<EscalaFuncionario> alocacoesVizinhas(List<EscalaTurno> contexto, YearMonth mes) {
        List<EscalaFuncionario> alocacoes = new ArrayList<>();
        for (EscalaTurno turno : contexto) {
            if (turno.getInicio() == null || YearMonth.from(turno.getInicio()).equals(mes)) {
                continue;
            }
            if (turno.getAgentes() != null) {
                alocacoes.addAll(turno.getAgentes());
            }
        }
        return alocacoes;
    }

    /**
     * Duração do turno convertida de BigDecimal para Duration passando por
     * segundos: o regime pode ter valor fracionário (7,5h, por exemplo), então
     * truncar para horas inteiras estaria errado.
     */
    private Duration duracao(TipoTurno tipoTurno) {
        BigDecimal segundos = tipoTurno.getDuracaoHoras()
                .multiply(BigDecimal.valueOf(SEGUNDOS_POR_HORA))
                .setScale(0, RoundingMode.HALF_UP);
        return Duration.ofSeconds(segundos.longValue());
    }

    private LocalDateTime inicioDe(YearMonth mes) {
        return mes.atDay(1).atStartOfDay();
    }

    // -----------------------------------------------------------------
    // Mensagens
    // -----------------------------------------------------------------

    private ResultadoGeracao recusa(String mensagem) {
        return new ResultadoGeracao(false, 0, 0, 0, mensagem);
    }

    private String descreverGeracao(YearMonth mes, PlanoDoMes plano) {
        String base = String.format("Rodízio de %s gerado: %d turno(s) e %d alocação(ões).",
                descreverMes(mes), plano.turnos().size(), plano.alocacoes().size());

        int incompletos = plano.diasIncompletos().size();
        if (incompletos == 0) {
            return base;
        }
        return base + String.format(" Atenção: %d dia(s) ficaram sem efetivo suficiente e precisam de ajuste manual.",
                incompletos);
    }

    private String descreverMes(YearMonth mes) {
        String texto = mes.atDay(1).format(FORMATO_MES_ANO);
        return texto.substring(0, 1).toUpperCase(LOCALE_BR) + texto.substring(1);
    }

    // -----------------------------------------------------------------
    // Estruturas internas do planejamento
    // -----------------------------------------------------------------

    /**
     * Mês montado em memória, antes de virar linha no banco.
     *
     * <p>Os turnos carregam ids provisórios negativos, que só existem para as
     * regras conseguirem distinguir um turno do outro durante o planejamento —
     * o id verdadeiro é o que o INSERT devolve.</p>
     */
    private static final class PlanoDoMes {

        private final List<EscalaTurno> turnos = new ArrayList<>();
        private final List<EscalaFuncionario> alocacoes = new ArrayList<>();
        private final Set<LocalDate> diasIncompletos = new LinkedHashSet<>();

        private int ultimoIdProvisorio;

        /** Negativos para nunca colidirem com um id real de escala_turno. */
        int proximoIdProvisorio() {
            return --ultimoIdProvisorio;
        }

        void adicionarTurno(EscalaTurno turno) {
            turnos.add(turno);
        }

        void alocar(EscalaTurno turno, Funcionario funcionario) {
            EscalaFuncionario alocacao = new EscalaFuncionario();
            alocacao.setEscalaTurno(turno);
            alocacao.setFuncionario(funcionario);
            alocacoes.add(alocacao);
        }

        /**
         * Tira do plano o que já tinha sido escalado para o turno. Usado quando
         * o mínimo de agentes não fecha: o turno é descartado inteiro e os
         * agentes voltam a ficar livres, inclusive aos olhos das regras, que
         * leem justamente esta lista.
         */
        void desfazerAlocacoes(EscalaTurno turno) {
            alocacoes.removeIf(alocacao -> alocacao.getEscalaTurno() == turno);
        }

        void marcarDiaIncompleto(LocalDate dia) {
            diasIncompletos.add(dia);
        }

        List<EscalaTurno> turnos() {
            return turnos;
        }

        List<EscalaFuncionario> alocacoes() {
            return alocacoes;
        }

        Set<LocalDate> diasIncompletos() {
            return diasIncompletos;
        }

        /** Agentes escalados para o turno, na ordem em que foram sorteados da fila. */
        List<Funcionario> agentesDe(EscalaTurno turno) {
            return alocacoes.stream()
                    .filter(alocacao -> alocacao.getEscalaTurno() == turno)
                    .map(EscalaFuncionario::getFuncionario)
                    .toList();
        }
    }

    /**
     * Retrato somente-leitura das alocações que as regras enxergam durante o
     * planejamento: os plantões já gravados dos meses vizinhos mais o que o
     * plano acumulou até agora.
     *
     * <p>Implementa {@link EscalaFuncionarioRepository} porque é essa a porta
     * pela qual {@link RegraEscalaServiceImpl} lê alocações — assim as regras
     * de #23 e #40 rodam exatamente como rodam na tela, só que sobre o mês em
     * construção. Os métodos de escrita nunca são chamados por elas.</p>
     */
    private record VisaoDeAlocacoes(List<EscalaFuncionario> jaGravadas, PlanoDoMes plano)
            implements EscalaFuncionarioRepository {

        @Override
        public List<EscalaFuncionario> listarPorTurno(int escalaTurnoId) {
            return todas()
                    .filter(alocacao -> alocacao.getEscalaTurno() != null
                            && Objects.equals(alocacao.getEscalaTurno().getId(), escalaTurnoId))
                    .toList();
        }

        @Override
        public List<EscalaFuncionario> listarPorFuncionario(int funcionarioId,
                                                            LocalDateTime inicio, LocalDateTime fim) {
            // Mesmo filtro do SQL equivalente: pelo inicio do turno, em [inicio, fim).
            return todas()
                    .filter(alocacao -> ehDoFuncionario(alocacao, funcionarioId))
                    .filter(alocacao -> comecaNaJanela(alocacao, inicio, fim))
                    .toList();
        }

        private Stream<EscalaFuncionario> todas() {
            return Stream.concat(jaGravadas.stream(), plano.alocacoes().stream());
        }

        private boolean ehDoFuncionario(EscalaFuncionario alocacao, int funcionarioId) {
            return alocacao.getFuncionario() != null
                    && alocacao.getFuncionario().getId() != null
                    && alocacao.getFuncionario().getId() == funcionarioId;
        }

        private boolean comecaNaJanela(EscalaFuncionario alocacao, LocalDateTime inicio, LocalDateTime fim) {
            if (alocacao.getEscalaTurno() == null || alocacao.getEscalaTurno().getInicio() == null) {
                return false;
            }
            LocalDateTime inicioDoTurno = alocacao.getEscalaTurno().getInicio();
            return !inicioDoTurno.isBefore(inicio) && inicioDoTurno.isBefore(fim);
        }

        @Override
        public EscalaFuncionario inserir(EscalaFuncionario escalaFuncionario) {
            throw new UnsupportedOperationException("Retrato de planejamento é somente leitura");
        }

        @Override
        public EscalaFuncionario inserir(EscalaFuncionario escalaFuncionario, Connection conexao) {
            throw new UnsupportedOperationException("Retrato de planejamento é somente leitura");
        }

        @Override
        public void remover(int id) {
            throw new UnsupportedOperationException("Retrato de planejamento é somente leitura");
        }

        @Override
        public List<EscalaFuncionario> buscarCoberturasDoMes(YearMonth mes) {
            throw new UnsupportedOperationException("Retrato de planejamento não conhece coberturas");
        }
    }
}
