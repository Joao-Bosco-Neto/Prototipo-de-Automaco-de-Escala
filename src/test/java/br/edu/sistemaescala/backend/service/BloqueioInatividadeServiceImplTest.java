package br.edu.sistemaescala.backend.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.edu.sistemaescala.backend.model.RoleUsuario;
import br.edu.sistemaescala.backend.model.Usuario;

class BloqueioInatividadeServiceImplTest {

    private static final String LOGIN = "gestor.silva";
    private static final String SENHA_CORRETA = "senhaSegura123";

    private SessaoUsuario sessaoUsuario;
    private AutenticacaoService autenticacaoService;
    private Usuario usuarioLogado;
    private AtomicReference<Instant> tempoAtual;
    private Clock clockMutavel;
    private BloqueioInatividadeService bloqueioService;

    @BeforeEach
    void prepararCenario() {
        sessaoUsuario = new SessaoUsuario();
        usuarioLogado = new Usuario(1, "Silva", LOGIN, "hash123", RoleUsuario.GESTOR, true, null, null);
        sessaoUsuario.iniciar(usuarioLogado);

        autenticacaoService = mock(AutenticacaoService.class);

        tempoAtual = new AtomicReference<>(Instant.parse("2026-08-25T10:00:00Z"));
        clockMutavel = new Clock() {
            @Override
            public ZoneId getZone() {
                return ZoneId.of("UTC");
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return tempoAtual.get();
            }
        };

        bloqueioService = new BloqueioInatividadeServiceImpl(
                sessaoUsuario,
                autenticacaoService,
                Duration.ofMinutes(15),
                clockMutavel
        );
    }

    @Test
    void iniciaDesbloqueadoEComTempoPadraoDe15Minutos() {
        assertFalse(bloqueioService.estaBloqueado());
        assertEquals(Duration.ofMinutes(15), bloqueioService.getTempoInatividade());
        assertEquals(Duration.ofMinutes(15), bloqueioService.getTempoRestante());
    }

    @Test
    void bloqueiaAposTempoDeInatividadeConfigurado() {
        // Avança 14 minutos e 59 segundos -> não deve bloquear
        tempoAtual.set(tempoAtual.get().plus(Duration.ofMinutes(14)).plusSeconds(59));
        bloqueioService.verificarInatividade();
        assertFalse(bloqueioService.estaBloqueado());
        assertEquals(Duration.ofSeconds(1), bloqueioService.getTempoRestante());

        // Avança mais 2 segundos (total 15m01s) -> deve bloquear
        tempoAtual.set(tempoAtual.get().plusSeconds(2));
        bloqueioService.verificarInatividade();
        assertTrue(bloqueioService.estaBloqueado());
        assertEquals(Duration.ZERO, bloqueioService.getTempoRestante());
    }

    @Test
    void registrarAtividadeAntesDoLimiteEvitaBloqueio() {
        // Passam 10 minutos
        tempoAtual.set(tempoAtual.get().plus(Duration.ofMinutes(10)));
        bloqueioService.verificarInatividade();
        assertFalse(bloqueioService.estaBloqueado());

        // Usuário move mouse ou digita -> registra atividade
        bloqueioService.registrarAtividade();

        // Passam mais 10 minutos (20 minutos do início, mas 10 minutos desde a última atividade)
        tempoAtual.set(tempoAtual.get().plus(Duration.ofMinutes(10)));
        bloqueioService.verificarInatividade();
        assertFalse(bloqueioService.estaBloqueado());
        assertEquals(Duration.ofMinutes(5), bloqueioService.getTempoRestante());

        // Passam mais 6 minutos (16 minutos desde a última atividade) -> bloqueia
        tempoAtual.set(tempoAtual.get().plus(Duration.ofMinutes(6)));
        bloqueioService.verificarInatividade();
        assertTrue(bloqueioService.estaBloqueado());
    }

    @Test
    void permiteCustomizarTempoDeInatividade() {
        bloqueioService.setTempoInatividade(Duration.ofMinutes(5));
        assertEquals(Duration.ofMinutes(5), bloqueioService.getTempoInatividade());

        // 4 minutos -> não bloqueia
        tempoAtual.set(tempoAtual.get().plus(Duration.ofMinutes(4)));
        bloqueioService.verificarInatividade();
        assertFalse(bloqueioService.estaBloqueado());

        // 5 minutos e 1 segundo -> bloqueia
        tempoAtual.set(tempoAtual.get().plus(Duration.ofMinutes(1)).plusSeconds(1));
        bloqueioService.verificarInatividade();
        assertTrue(bloqueioService.estaBloqueado());
    }

    @Test
    void rejeitaTempoDeInatividadeNuloOuNaoPositivo() {
        assertThrows(IllegalArgumentException.class, () -> bloqueioService.setTempoInatividade(null));
        assertThrows(IllegalArgumentException.class, () -> bloqueioService.setTempoInatividade(Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> bloqueioService.setTempoInatividade(Duration.ofMinutes(-5)));
    }

    @Test
    void desbloqueiaComSenhaCorretaERenovaAtividade() {
        tempoAtual.set(tempoAtual.get().plus(Duration.ofMinutes(20)));
        bloqueioService.verificarInatividade();
        assertTrue(bloqueioService.estaBloqueado());

        when(autenticacaoService.autenticar(LOGIN, SENHA_CORRETA)).thenReturn(Optional.of(usuarioLogado));

        boolean desbloqueou = bloqueioService.desbloquear(SENHA_CORRETA);

        assertTrue(desbloqueou);
        assertFalse(bloqueioService.estaBloqueado());
        assertEquals(Duration.ofMinutes(15), bloqueioService.getTempoRestante());
        verify(autenticacaoService).autenticar(LOGIN, SENHA_CORRETA);
    }

    @Test
    void recusaDesbloqueioComSenhaErradaEMantemBloqueado() {
        tempoAtual.set(tempoAtual.get().plus(Duration.ofMinutes(20)));
        bloqueioService.verificarInatividade();
        assertTrue(bloqueioService.estaBloqueado());

        when(autenticacaoService.autenticar(LOGIN, "senhaErrada")).thenReturn(Optional.empty());

        boolean desbloqueou = bloqueioService.desbloquear("senhaErrada");

        assertFalse(desbloqueou);
        assertTrue(bloqueioService.estaBloqueado());
        assertEquals(Duration.ZERO, bloqueioService.getTempoRestante());
    }

    @Test
    void recusaDesbloqueioComSenhaVaziaSemChamarAutenticacao() {
        bloqueioService.bloquear();
        assertTrue(bloqueioService.estaBloqueado());

        assertFalse(bloqueioService.desbloquear(""));
        assertFalse(bloqueioService.desbloquear("   "));
        assertFalse(bloqueioService.desbloquear(null));

        verify(autenticacaoService, never()).autenticar(anyString(), anyString());
    }

    @Test
    void recusaDesbloqueioSemUsuarioAutenticado() {
        sessaoUsuario.encerrar();
        assertThrows(AcessoNegadoException.class, () -> bloqueioService.desbloquear(SENHA_CORRETA));
    }

    @Test
    void bloqueioManualAcionaBloqueioImediato() {
        assertFalse(bloqueioService.estaBloqueado());
        bloqueioService.bloquear();
        assertTrue(bloqueioService.estaBloqueado());
    }

    @Test
    void notificaOuvintesAoBloquearEDesbloquear() {
        List<Boolean> estadosNotificados = new ArrayList<>();
        bloqueioService.adicionarOuvinteBloqueio(estadosNotificados::add);

        bloqueioService.bloquear();
        assertEquals(List.of(true), estadosNotificados);

        when(autenticacaoService.autenticar(LOGIN, SENHA_CORRETA)).thenReturn(Optional.of(usuarioLogado));
        bloqueioService.desbloquear(SENHA_CORRETA);
        assertEquals(List.of(true, false), estadosNotificados);
    }

    @Test
    void naoBloqueiaQuandoNaoHaUsuarioAutenticado() {
        sessaoUsuario.encerrar();
        tempoAtual.set(tempoAtual.get().plus(Duration.ofMinutes(30)));
        bloqueioService.verificarInatividade();
        assertFalse(bloqueioService.estaBloqueado());
    }

    @Test
    void atividadeIgnoradaEnquantoBloqueado() {
        bloqueioService.bloquear();
        assertTrue(bloqueioService.estaBloqueado());

        tempoAtual.set(tempoAtual.get().plus(Duration.ofMinutes(10)));
        bloqueioService.registrarAtividade();

        // Continua bloqueado
        assertTrue(bloqueioService.estaBloqueado());
    }
}

