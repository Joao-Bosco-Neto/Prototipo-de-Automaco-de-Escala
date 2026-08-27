package br.edu.sistemaescala.backend.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

import br.edu.sistemaescala.backend.model.EscalaFuncionario;
import br.edu.sistemaescala.backend.model.EscalaTurno;
import br.edu.sistemaescala.backend.repository.EscalaFuncionarioRepository;
import br.edu.sistemaescala.backend.repository.jdbc.EscalaFuncionarioRepositoryJdbc;

/** Implementação das regras de duplicidade, sobreposição, efetivo mínimo e descanso obrigatório da escala. */
public class RegraEscalaServiceImpl implements RegraEscalaService {

    /**
     * Margem, em dias, para trás na busca de outras alocações do funcionário.
     *
     * <p>{@link EscalaFuncionarioRepository#listarPorFuncionario} filtra pelo
     * <em>início</em> do turno, não pelo período todo. Sem essa margem, um
     * plantão que começou antes da janela e ainda invade o turno analisado
     * (por exemplo, um noturno das 22h às 06h) ficaria de fora da consulta.</p>
     */
    private static final int MARGEM_JANELA_DIAS = 1;

    private static final int SEGUNDOS_POR_HORA = 3600;

    private static final DateTimeFormatter FORMATO_PERIODO = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final EscalaFuncionarioRepository escalaFuncionarioRepository;

    public RegraEscalaServiceImpl() {
        this(new EscalaFuncionarioRepositoryJdbc());
    }

    public RegraEscalaServiceImpl(EscalaFuncionarioRepository escalaFuncionarioRepository) {
        this.escalaFuncionarioRepository = Objects.requireNonNull(escalaFuncionarioRepository,
                "escalaFuncionarioRepository não pode ser nulo");
    }

    @Override
    public ResultadoAlocacao podeAlocar(int funcionarioId, EscalaTurno turno) {
        Objects.requireNonNull(turno, "turno não pode ser nulo");

        ResultadoAlocacao duplicidade = verificarDuplicidade(funcionarioId, turno);
        if (!duplicidade.permitido()) {
            return duplicidade;
        }
        return verificarSobreposicao(funcionarioId, turno);
    }

    @Override
    public ResultadoEfetivo verificarEfetivo(EscalaTurno turno) {
        Objects.requireNonNull(turno, "turno não pode ser nulo");

        int minimoExigido = turno.getMinAgentes();
        int alocados = escalaFuncionarioRepository.listarPorTurno(turno.getId()).size();

        if (alocados < minimoExigido) {
            String mensagem = String.format("Faltam %d agente(s): %d de %d alocados.",
                    minimoExigido - alocados, alocados, minimoExigido);
            return new ResultadoEfetivo(false, alocados, minimoExigido, mensagem);
        }

        String mensagem = String.format("Efetivo completo: %d de %d alocados.", alocados, minimoExigido);
        return new ResultadoEfetivo(true, alocados, minimoExigido, mensagem);
    }

    @Override
    public ResultadoDescanso verificarDescanso(int funcionarioId, EscalaTurno turno) {
        Objects.requireNonNull(turno, "turno não pode ser nulo");

        Duration descansoExigido = descansoExigido(turno);
        if (descansoExigido.isZero() || descansoExigido.isNegative()) {
            // Regimes como o sobreaviso não exigem intervalo entre plantões.
            return new ResultadoDescanso(true, Duration.ZERO, null,
                    "Este tipo de turno não exige intervalo de descanso.");
        }

        Periodo periodoDoTurno = periodoDoTurno(turno);
        if (periodoDoTurno == null) {
            return new ResultadoDescanso(true, descansoExigido, null,
                    "Turno sem período definido: não há descanso a conferir.");
        }

        Periodo vizinhoMaisProximo = null;
        Duration menorDescanso = null;
        for (EscalaFuncionario alocacao : plantoesVizinhos(funcionarioId, turno, periodoDoTurno, descansoExigido)) {
            Periodo periodoVizinho = periodoEfetivo(alocacao);
            if (periodoVizinho == null) {
                continue;
            }
            Duration descanso = descansoEntre(periodoVizinho, periodoDoTurno);
            if (descanso == null) {
                continue; // plantões sobrepostos são assunto de podeAlocar, não desta regra
            }
            if (menorDescanso == null || descanso.compareTo(menorDescanso) < 0) {
                menorDescanso = descanso;
                vizinhoMaisProximo = periodoVizinho;
            }
        }

        if (menorDescanso == null) {
            return new ResultadoDescanso(true, descansoExigido, null,
                    "Nenhum plantão próximo: o descanso de " + formatarDuracao(descansoExigido) + " está respeitado.");
        }

        if (menorDescanso.compareTo(descansoExigido) >= 0) {
            // O limite exato vale como respeitado: descansar o mínimo já cumpre a regra.
            String mensagem = String.format("Descanso de %s respeitado (o regime exige %s).",
                    formatarDuracao(menorDescanso), formatarDuracao(descansoExigido));
            return new ResultadoDescanso(true, descansoExigido, menorDescanso, mensagem);
        }

        Duration faltam = descansoExigido.minus(menorDescanso);
        String mensagem = String.format(
                "Descanso insuficiente: são %s até o plantão de %s às %s, mas o regime exige %s. Faltam %s.",
                formatarDuracao(menorDescanso),
                FORMATO_PERIODO.format(vizinhoMaisProximo.inicio()),
                FORMATO_PERIODO.format(vizinhoMaisProximo.fim()),
                formatarDuracao(descansoExigido),
                formatarDuracao(faltam));
        return new ResultadoDescanso(false, descansoExigido, menorDescanso, mensagem);
    }

    /**
     * Plantões do funcionário perto o bastante do turno para influenciar o
     * descanso, dos dois lados: os que terminam pouco antes dele começar e os
     * que começam pouco depois dele terminar.
     *
     * <p>A janela abre {@code descanso} antes do início do turno (mais a
     * margem, porque o repositório filtra pelo início do turno vizinho e não
     * pelo período todo) e fecha {@code descanso} depois do fim dele.</p>
     */
    private List<EscalaFuncionario> plantoesVizinhos(int funcionarioId, EscalaTurno turno,
                                                     Periodo periodoDoTurno, Duration descansoExigido) {
        LocalDateTime janelaInicio = periodoDoTurno.inicio().minus(descansoExigido).minusDays(MARGEM_JANELA_DIAS);
        LocalDateTime janelaFim = periodoDoTurno.fim().plus(descansoExigido);

        return escalaFuncionarioRepository.listarPorFuncionario(funcionarioId, janelaInicio, janelaFim).stream()
                .filter(alocacao -> !ehDoMesmoTurno(alocacao, turno))
                .toList();
    }

    /**
     * Intervalo livre entre um plantão vizinho e o turno analisado, contado do
     * fim de um até o início do outro — não importa qual vem primeiro.
     *
     * @return {@code null} quando os dois períodos se sobrepõem, caso em que
     *         não existe descanso a medir
     */
    private Duration descansoEntre(Periodo vizinho, Periodo turno) {
        if (!vizinho.fim().isAfter(turno.inicio())) {
            return Duration.between(vizinho.fim(), turno.inicio()); // vizinho antes do turno
        }
        if (!turno.fim().isAfter(vizinho.inicio())) {
            return Duration.between(turno.fim(), vizinho.inicio()); // vizinho depois do turno
        }
        return null;
    }

    /**
     * Descanso exigido pelo tipo do turno, convertido de BigDecimal para
     * Duration passando por segundos: o regime pode ter valor fracionário
     * (36,5h, por exemplo), então truncar para horas inteiras estaria errado.
     */
    private Duration descansoExigido(EscalaTurno turno) {
        if (turno.getTipoTurno() == null || turno.getTipoTurno().getIntervaloDescansoHoras() == null) {
            return Duration.ZERO;
        }
        BigDecimal segundos = turno.getTipoTurno().getIntervaloDescansoHoras()
                .multiply(BigDecimal.valueOf(SEGUNDOS_POR_HORA))
                .setScale(0, RoundingMode.HALF_UP);
        return Duration.ofSeconds(segundos.longValue());
    }

    private String formatarDuracao(Duration duracao) {
        long horas = duracao.toHours();
        int minutos = duracao.toMinutesPart();
        return minutos == 0 ? horas + "h" : String.format("%dh%02dmin", horas, minutos);
    }

    /**
     * A constraint uq_ef_turno_func já impede a duplicidade no banco, mas com
     * uma SQLException crua: esta checagem existe para dar mensagem amigável
     * antes de tentar gravar.
     */
    private ResultadoAlocacao verificarDuplicidade(int funcionarioId, EscalaTurno turno) {
        boolean jaAlocado = escalaFuncionarioRepository.listarPorTurno(turno.getId()).stream()
                .anyMatch(alocacao -> ehDoFuncionario(alocacao, funcionarioId));

        if (jaAlocado) {
            return new ResultadoAlocacao(false, "Duplicidade: este funcionário já está alocado neste turno.");
        }
        return new ResultadoAlocacao(true, "");
    }

    /** Procura outra alocação do funcionário cujo período efetivo invada o período deste turno. */
    private ResultadoAlocacao verificarSobreposicao(int funcionarioId, EscalaTurno turno) {
        Periodo periodoDoTurno = periodoDoTurno(turno);
        if (periodoDoTurno == null) {
            return new ResultadoAlocacao(true, "");
        }

        LocalDateTime janelaInicio = periodoDoTurno.inicio().minusDays(MARGEM_JANELA_DIAS);
        List<EscalaFuncionario> outrasAlocacoes =
                escalaFuncionarioRepository.listarPorFuncionario(funcionarioId, janelaInicio, periodoDoTurno.fim());

        for (EscalaFuncionario alocacao : outrasAlocacoes) {
            if (ehDoMesmoTurno(alocacao, turno)) {
                continue; // o próprio turno já foi coberto pela checagem de duplicidade
            }
            Periodo periodoEfetivo = periodoEfetivo(alocacao);
            if (periodoEfetivo != null && periodoEfetivo.sobrepoe(periodoDoTurno)) {
                String mensagem = String.format(
                        "Sobreposição: o funcionário já está alocado em outro turno das %s às %s.",
                        FORMATO_PERIODO.format(periodoEfetivo.inicio()),
                        FORMATO_PERIODO.format(periodoEfetivo.fim()));
                return new ResultadoAlocacao(false, mensagem);
            }
        }
        return new ResultadoAlocacao(true, "");
    }

    /**
     * Período que a alocação realmente ocupa: os campos inicio/fim da própria
     * {@link EscalaFuncionario} quando preenchidos (turno parcial, meio
     * plantão) ou, quando nulos, o período do {@link EscalaTurno}
     * correspondente — o caso normal, de quem cumpre o turno inteiro.
     */
    private Periodo periodoEfetivo(EscalaFuncionario alocacao) {
        if (alocacao.getInicio() != null && alocacao.getFim() != null) {
            return new Periodo(alocacao.getInicio(), alocacao.getFim());
        }
        return periodoDoTurno(alocacao.getEscalaTurno());
    }

    private Periodo periodoDoTurno(EscalaTurno turno) {
        if (turno == null || turno.getInicio() == null || turno.getFim() == null) {
            return null;
        }
        return new Periodo(turno.getInicio(), turno.getFim());
    }

    private boolean ehDoFuncionario(EscalaFuncionario alocacao, int funcionarioId) {
        return alocacao.getFuncionario() != null
                && alocacao.getFuncionario().getId() != null
                && alocacao.getFuncionario().getId() == funcionarioId;
    }

    private boolean ehDoMesmoTurno(EscalaFuncionario alocacao, EscalaTurno turno) {
        return alocacao.getEscalaTurno() != null
                && Objects.equals(alocacao.getEscalaTurno().getId(), turno.getId());
    }

    /** Intervalo [inicio, fim) ocupado por uma alocação ou por um turno. */
    private record Periodo(LocalDateTime inicio, LocalDateTime fim) {

        /** Encostar não é sobrepor: fim igual ao início do outro está liberado. */
        boolean sobrepoe(Periodo outro) {
            return inicio.isBefore(outro.fim()) && outro.inicio().isBefore(fim);
        }
    }
}
