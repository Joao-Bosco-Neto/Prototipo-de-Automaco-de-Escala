package br.edu.sistemaescala.backend.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import br.edu.sistemaescala.backend.dao.TransacaoUtil;
import br.edu.sistemaescala.backend.model.EscalaFuncionario;
import br.edu.sistemaescala.backend.model.EscalaTurno;
import br.edu.sistemaescala.backend.model.Funcionario;
import br.edu.sistemaescala.backend.model.LancamentoHoras;
import br.edu.sistemaescala.backend.model.MotivoCobertura;
import br.edu.sistemaescala.backend.model.TipoLancamento;
import br.edu.sistemaescala.backend.repository.EscalaFuncionarioRepository;
import br.edu.sistemaescala.backend.repository.EscalaTurnoRepository;
import br.edu.sistemaescala.backend.repository.FuncionarioRepository;
import br.edu.sistemaescala.backend.repository.LancamentoHorasRepository;
import br.edu.sistemaescala.backend.repository.MotivoCoberturaRepository;
import br.edu.sistemaescala.backend.repository.jdbc.EscalaFuncionarioRepositoryJdbc;
import br.edu.sistemaescala.backend.repository.jdbc.EscalaTurnoRepositoryJdbc;
import br.edu.sistemaescala.backend.repository.jdbc.FuncionarioRepositoryJdbc;
import br.edu.sistemaescala.backend.repository.jdbc.LancamentoHorasRepositoryJdbc;
import br.edu.sistemaescala.backend.repository.jdbc.MotivoCoberturaRepositoryJdbc;

/**
 * Implementação do registro de cobertura de plantão (issue #33).
 *
 * <p>As leituras da tela (quem está escalado na data, motivos, selo do
 * substituto) usam {@link EscalaTurnoRepository#buscarPorPeriodo}, que já traz
 * o dia com tipo de turno e agentes hidratados num JOIN só — nunca uma consulta
 * por turno.</p>
 *
 * <p>A gravação roda dentro de {@link TransacaoUtil}: a alocação de cobertura e
 * os dois lançamentos de banco de horas (crédito de quem cobre, débito do
 * ausente) precisam entrar juntos ou não entrar. Ambos os lançamentos apontam
 * para a <em>alocação de cobertura</em> em {@code escala_funcionario_id}, para
 * a exclusão da cobertura (issue #34) estornar os dois pela cascata do schema.</p>
 *
 * <p><b>O que ainda não é consultado.</b> O lançamento é feito só com base no
 * checkbox da tela, como a issue pede. Refinar por
 * {@code motivo_cobertura.gera_lancamento} ou por
 * {@code tipo_turno.conta_banco_horas} é assunto da issue #35.</p>
 */
public class CoberturaServiceImpl implements CoberturaService {

    private static final DateTimeFormatter FORMATO_DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final EscalaTurnoRepository escalaTurnoRepository;
    private final EscalaFuncionarioRepository escalaFuncionarioRepository;
    private final LancamentoHorasRepository lancamentoHorasRepository;
    private final MotivoCoberturaRepository motivoCoberturaRepository;
    private final FuncionarioRepository funcionarioRepository;
    private final RegraEscalaService regraEscalaService;

    public CoberturaServiceImpl() {
        this(new EscalaTurnoRepositoryJdbc(), new EscalaFuncionarioRepositoryJdbc(),
                new LancamentoHorasRepositoryJdbc(), new MotivoCoberturaRepositoryJdbc(),
                new FuncionarioRepositoryJdbc(), new RegraEscalaServiceImpl());
    }

    public CoberturaServiceImpl(EscalaTurnoRepository escalaTurnoRepository,
                                EscalaFuncionarioRepository escalaFuncionarioRepository,
                                LancamentoHorasRepository lancamentoHorasRepository,
                                MotivoCoberturaRepository motivoCoberturaRepository,
                                FuncionarioRepository funcionarioRepository,
                                RegraEscalaService regraEscalaService) {
        this.escalaTurnoRepository = Objects.requireNonNull(escalaTurnoRepository,
                "escalaTurnoRepository não pode ser nulo");
        this.escalaFuncionarioRepository = Objects.requireNonNull(escalaFuncionarioRepository,
                "escalaFuncionarioRepository não pode ser nulo");
        this.lancamentoHorasRepository = Objects.requireNonNull(lancamentoHorasRepository,
                "lancamentoHorasRepository não pode ser nulo");
        this.motivoCoberturaRepository = Objects.requireNonNull(motivoCoberturaRepository,
                "motivoCoberturaRepository não pode ser nulo");
        this.funcionarioRepository = Objects.requireNonNull(funcionarioRepository,
                "funcionarioRepository não pode ser nulo");
        this.regraEscalaService = Objects.requireNonNull(regraEscalaService,
                "regraEscalaService não pode ser nulo");
    }

    // -----------------------------------------------------------------
    // Leituras da tela
    // -----------------------------------------------------------------

    @Override
    public List<EscalaFuncionario> listarEscaladosNaData(LocalDate data) {
        Objects.requireNonNull(data, "data não pode ser nula");

        LocalDateTime inicioDoDia = data.atStartOfDay();
        return escalaTurnoRepository.buscarPorPeriodo(inicioDoDia, inicioDoDia.plusDays(1)).stream()
                .flatMap(turno -> turno.getAgentes().stream())
                // Uma cobertura já registrada não entra: não se cobre uma cobertura.
                .filter(alocacao -> alocacao.getCoberturaDe() == null)
                .sorted(Comparator.comparing(this::nomeDoFuncionario, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(alocacao -> alocacao.getId()))
                .toList();
    }

    @Override
    public List<MotivoCobertura> listarMotivos() {
        return motivoCoberturaRepository.listar(true);
    }

    @Override
    public List<SubstitutoDisponivel> listarSubstitutos(EscalaFuncionario alocacaoAusente) {
        EscalaTurno turno = turnoDe(alocacaoAusente);

        // Quem já está escalado neste turno não é candidato a substituto dele.
        Set<Integer> jaNoTurno = turno.getAgentes().stream()
                .map(alocacao -> alocacao.getFuncionario().getId())
                .collect(Collectors.toSet());

        return funcionarioRepository.listar(true, null).stream()
                .filter(funcionario -> funcionario.getId() != null)
                .filter(funcionario -> !jaNoTurno.contains(funcionario.getId()))
                .map(funcionario -> avaliarSubstituto(funcionario, turno))
                .sorted(Comparator
                        // Disponíveis primeiro, depois em ordem de nome.
                        .comparing(SubstitutoDisponivel::disponivel).reversed()
                        .thenComparing(substituto -> substituto.funcionario().getNome(),
                                String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /** Roda as mesmas regras da montagem da escala: duplicidade/sobreposição (#23) e descanso (#40). */
    private SubstitutoDisponivel avaliarSubstituto(Funcionario funcionario, EscalaTurno turno) {
        ResultadoAlocacao alocacao = regraEscalaService.podeAlocar(funcionario.getId(), turno);
        if (!alocacao.permitido()) {
            return new SubstitutoDisponivel(funcionario, false, alocacao.mensagem());
        }
        ResultadoDescanso descanso = regraEscalaService.verificarDescanso(funcionario.getId(), turno);
        if (!descanso.respeitado()) {
            return new SubstitutoDisponivel(funcionario, false, descanso.mensagem());
        }
        return new SubstitutoDisponivel(funcionario, true, "Disponível");
    }

    // -----------------------------------------------------------------
    // Gravação
    // -----------------------------------------------------------------

    @Override
    public EscalaFuncionario registrar(EscalaFuncionario alocacaoAusente, Funcionario substituto,
                                       Integer motivoCoberturaId, String observacao,
                                       boolean lancarBancoHoras) {
        EscalaTurno turno = turnoDe(alocacaoAusente);
        Funcionario ausente = alocacaoAusente.getFuncionario();

        if (alocacaoAusente.getId() == null) {
            throw new RegraCoberturaException("A alocação do funcionário ausente não foi salva.");
        }
        if (ausente == null || ausente.getId() == null) {
            throw new RegraCoberturaException("A alocação do funcionário ausente está sem funcionário.");
        }
        Objects.requireNonNull(substituto, "substituto não pode ser nulo");
        if (substituto.getId() == null) {
            throw new RegraCoberturaException("Escolha o funcionário que irá cobrir.");
        }
        if (substituto.getId().equals(ausente.getId())) {
            throw new RegraCoberturaException("O substituto não pode ser o próprio funcionário ausente.");
        }
        if (turno.getInicio() == null || turno.getFim() == null) {
            throw new RegraCoberturaException("O turno da cobertura está sem período definido.");
        }
        if (motivoCoberturaId != null && motivoCoberturaRepository.buscarPorId(motivoCoberturaId).isEmpty()) {
            throw new RegraCoberturaException("O motivo de cobertura escolhido não existe mais.");
        }

        ResultadoAlocacao alocacao = regraEscalaService.podeAlocar(substituto.getId(), turno);
        if (!alocacao.permitido()) {
            throw new RegraCoberturaException(alocacao.mensagem());
        }
        ResultadoDescanso descanso = regraEscalaService.verificarDescanso(substituto.getId(), turno);
        if (!descanso.respeitado()) {
            throw new RegraCoberturaException(descanso.mensagem());
        }

        int minutosDoTurno = (int) Duration.between(turno.getInicio(), turno.getFim()).toMinutes();
        LocalDate dataDoPlantao = turno.getInicio().toLocalDate();
        String observacaoLimpa = observacao == null || observacao.isBlank() ? null : observacao.trim();

        return TransacaoUtil.executar(conexao -> {
            EscalaFuncionario cobertura = new EscalaFuncionario();
            cobertura.setEscalaTurno(turno);
            cobertura.setFuncionario(substituto);
            cobertura.setCoberturaDe(alocacaoAusente);
            cobertura.setMotivoCoberturaId(motivoCoberturaId);
            cobertura.setObservacao(observacaoLimpa);
            cobertura.setLancouBancoHoras(lancarBancoHoras);
            escalaFuncionarioRepository.inserir(cobertura, conexao);

            if (lancarBancoHoras) {
                lancamentoHorasRepository.salvar(lancamento(substituto, cobertura, dataDoPlantao,
                        minutosDoTurno, TipoLancamento.CREDITO_COBERTURA,
                        "Cobertura do plantão de " + ausente.getNome() + " em " + dataDoPlantao.format(FORMATO_DATA)),
                        conexao);
                lancamentoHorasRepository.salvar(lancamento(ausente, cobertura, dataDoPlantao,
                        -minutosDoTurno, TipoLancamento.DEBITO_AUSENCIA,
                        "Ausência coberta por " + substituto.getNome() + " em " + dataDoPlantao.format(FORMATO_DATA)),
                        conexao);
            }
            return cobertura;
        });
    }

    private LancamentoHoras lancamento(Funcionario funcionario, EscalaFuncionario cobertura, LocalDate dataReferencia,
                                       int minutos, TipoLancamento tipo, String descricao) {
        LancamentoHoras lancamento = new LancamentoHoras();
        lancamento.setFuncionario(funcionario);
        lancamento.setEscalaFuncionario(cobertura);
        lancamento.setDataReferencia(dataReferencia);
        lancamento.setMinutos(minutos);
        lancamento.setTipo(tipo);
        lancamento.setDescricao(descricao);
        return lancamento;
    }

    // -----------------------------------------------------------------
    // Apoio
    // -----------------------------------------------------------------

    private EscalaTurno turnoDe(EscalaFuncionario alocacao) {
        Objects.requireNonNull(alocacao, "alocacaoAusente não pode ser nula");
        EscalaTurno turno = alocacao.getEscalaTurno();
        if (turno == null || turno.getId() == null) {
            throw new RegraCoberturaException("A alocação do funcionário ausente está sem turno.");
        }
        return turno;
    }

    private String nomeDoFuncionario(EscalaFuncionario alocacao) {
        Funcionario funcionario = alocacao.getFuncionario();
        return funcionario != null && funcionario.getNome() != null ? funcionario.getNome() : "";
    }
}
