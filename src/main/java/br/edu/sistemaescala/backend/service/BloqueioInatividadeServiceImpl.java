package br.edu.sistemaescala.backend.service;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import br.edu.sistemaescala.backend.model.Usuario;

/**
 * Implementação do serviço de bloqueio automático por inatividade (Issue #61 / OWASP A07).
 *
 * Registra eventos de auditoria com sanitização de entradas para evitar log injection (CWE-117)
 * e sem registrar dados sensíveis como senhas ou hashes (CWE-532).
 */
public class BloqueioInatividadeServiceImpl implements BloqueioInatividadeService {

    private static final Logger LOGGER = System.getLogger(BloqueioInatividadeServiceImpl.class.getName());

    private final SessaoUsuario sessaoUsuario;
    private final AutenticacaoService autenticacaoService;
    private final Clock clock;

    private volatile Duration tempoInatividade;
    private final AtomicBoolean bloqueado = new AtomicBoolean(false);
    private final AtomicReference<Instant> ultimaAtividade;
    private final List<Consumer<Boolean>> ouvintes = new CopyOnWriteArrayList<>();

    public BloqueioInatividadeServiceImpl(SessaoUsuario sessaoUsuario, AutenticacaoService autenticacaoService) {
        this(sessaoUsuario, autenticacaoService, TEMPO_PADRAO_INATIVIDADE, Clock.systemDefaultZone());
    }

    public BloqueioInatividadeServiceImpl(SessaoUsuario sessaoUsuario,
                                          AutenticacaoService autenticacaoService,
                                          Duration tempoInatividade,
                                          Clock clock) {
        this.sessaoUsuario = Objects.requireNonNull(sessaoUsuario, "SessaoUsuario nao pode ser nula");
        this.autenticacaoService = Objects.requireNonNull(autenticacaoService, "AutenticacaoService nao pode ser nulo");
        this.clock = Objects.requireNonNull(clock, "Clock nao pode ser nulo");
        setTempoInatividade(tempoInatividade);
        this.ultimaAtividade = new AtomicReference<>(this.clock.instant());
    }

    @Override
    public void registrarAtividade() {
        if (!bloqueado.get()) {
            ultimaAtividade.set(clock.instant());
        }
    }

    @Override
    public boolean estaBloqueado() {
        return bloqueado.get();
    }

    @Override
    public void bloquear() {
        Optional<Usuario> usuarioOpt = sessaoUsuario.usuarioAtual();
        if (usuarioOpt.isEmpty()) {
            return;
        }

        if (bloqueado.compareAndSet(false, true)) {
            Usuario usuario = usuarioOpt.get();
            LOGGER.log(Level.INFO, "Sessão do usuário ''{0}'' bloqueada por inatividade", sanitizarLog(usuario.getLogin()));
            notificarOuvintes(true);
        }
    }

    @Override
    public boolean desbloquear(String senha) {
        Usuario usuario = sessaoUsuario.exigirUsuario();

        if (senha == null || senha.isBlank()) {
            LOGGER.log(Level.WARNING, "Tentativa de desbloqueio com senha em branco para o usuário ''{0}''",
                    sanitizarLog(usuario.getLogin()));
            return false;
        }

        Optional<Usuario> autenticado = autenticacaoService.autenticar(usuario.getLogin(), senha);
        if (autenticado.isPresent()) {
            bloqueado.set(false);
            ultimaAtividade.set(clock.instant());
            LOGGER.log(Level.INFO, "Sessão do usuário ''{0}'' desbloqueada com sucesso",
                    sanitizarLog(usuario.getLogin()));
            notificarOuvintes(false);
            return true;
        } else {
            LOGGER.log(Level.WARNING, "Tentativa inválida de desbloqueio de sessão para o usuário ''{0}''",
                    sanitizarLog(usuario.getLogin()));
            return false;
        }
    }

    @Override
    public void verificarInatividade() {
        verificarInatividade(clock.instant());
    }

    @Override
    public void verificarInatividade(Instant agora) {
        if (bloqueado.get() || sessaoUsuario.usuarioAtual().isEmpty()) {
            return;
        }

        Instant ultima = ultimaAtividade.get();
        if (ultima != null && Duration.between(ultima, agora).compareTo(tempoInatividade) >= 0) {
            bloquear();
        }
    }

    @Override
    public Duration getTempoInatividade() {
        return tempoInatividade;
    }

    @Override
    public void setTempoInatividade(Duration tempoInatividade) {
        if (tempoInatividade == null || tempoInatividade.isNegative() || tempoInatividade.isZero()) {
            throw new IllegalArgumentException("Tempo de inatividade deve ser positivo");
        }
        this.tempoInatividade = tempoInatividade;
    }

    @Override
    public Duration getTempoRestante() {
        return getTempoRestante(clock.instant());
    }

    @Override
    public Duration getTempoRestante(Instant agora) {
        if (bloqueado.get()) {
            return Duration.ZERO;
        }

        Instant ultima = ultimaAtividade.get();
        if (ultima == null) {
            return tempoInatividade;
        }

        Duration decorrido = Duration.between(ultima, agora);
        Duration restante = tempoInatividade.minus(decorrido);
        return restante.isNegative() ? Duration.ZERO : restante;
    }

    @Override
    public void adicionarOuvinteBloqueio(Consumer<Boolean> ouvinte) {
        if (ouvinte != null) {
            ouvintes.add(ouvinte);
        }
    }

    @Override
    public void removerOuvinteBloqueio(Consumer<Boolean> ouvinte) {
        ouvintes.remove(ouvinte);
    }

    private void notificarOuvintes(boolean estaBloqueado) {
        for (Consumer<Boolean> ouvinte : ouvintes) {
            try {
                ouvinte.accept(estaBloqueado);
            } catch (Exception excecao) {
                LOGGER.log(Level.WARNING, "Erro ao notificar ouvinte de bloqueio: {0}", excecao.getMessage());
            }
        }
    }

    private String sanitizarLog(String texto) {
        if (texto == null) {
            return "";
        }
        return texto.replace('\r', '_').replace('\n', '_');
    }
}

