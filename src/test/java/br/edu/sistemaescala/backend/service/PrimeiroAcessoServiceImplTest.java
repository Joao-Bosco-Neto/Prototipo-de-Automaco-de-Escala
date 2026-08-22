package br.edu.sistemaescala.backend.service;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.edu.sistemaescala.backend.model.Configuracao;
import br.edu.sistemaescala.backend.model.RoleUsuario;
import br.edu.sistemaescala.backend.model.Usuario;
import br.edu.sistemaescala.backend.repository.ConfiguracaoRepository;
import br.edu.sistemaescala.backend.repository.UsuarioRepository;

class PrimeiroAcessoServiceImplTest {

    private UsuarioRepository usuarioRepository;
    private ConfiguracaoRepository configuracaoRepository;
    private AutenticacaoService autenticacaoService;
    private PrimeiroAcessoService primeiroAcessoService;
    private Configuracao configuracao;

    @BeforeEach
    void prepararMocks() {
        usuarioRepository = mock(UsuarioRepository.class);
        configuracaoRepository = mock(ConfiguracaoRepository.class);
        autenticacaoService = mock(AutenticacaoService.class);
        primeiroAcessoService = new PrimeiroAcessoServiceImpl(
                usuarioRepository, configuracaoRepository, autenticacaoService);
        configuracao = new Configuracao();
        configuracao.setId(1);
        when(configuracaoRepository.buscar()).thenReturn(Optional.of(configuracao));
    }

    @Test
    void primeiroAcessoDependeDaTabelaDeUsuariosVazia() {
        when(usuarioRepository.listar()).thenReturn(List.of());
        assertTrue(primeiroAcessoService.primeiroAcesso());

        when(usuarioRepository.listar()).thenReturn(List.of(new Usuario()));
        assertFalse(primeiroAcessoService.primeiroAcesso());
    }

    @Test
    void configuraOrganizacaoEInsereAdministradorSemCredencialFixa() {
        when(usuarioRepository.listar()).thenReturn(List.of());
        when(autenticacaoService.gerarHash("senha-segura")).thenReturn("hash-gerado");

        primeiroAcessoService.configurar("  GOTE  ", " gestor ", "senha-segura", "senha-segura");

        assertEquals("GOTE", configuracao.getNomeOrganizacao());
        verify(configuracaoRepository).atualizar(configuracao);
        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).inserir(captor.capture());
        Usuario administrador = captor.getValue();
        assertEquals("gestor", administrador.getLogin());
        assertEquals("hash-gerado", administrador.getSenhaHash());
        assertEquals(RoleUsuario.ADMIN, administrador.getRole());
        assertTrue(administrador.isAtivo());
    }

    @Test
    void rejeitaSenhaCurtaOuDiferente() {
        when(usuarioRepository.listar()).thenReturn(List.of());

        assertThrows(SenhaFracaException.class,
                () -> primeiroAcessoService.configurar("GOTE", "gestor", "1234567", "1234567"));
        assertThrows(SenhasNaoConferemException.class,
                () -> primeiroAcessoService.configurar("GOTE", "gestor", "senha-segura", "outra-senha"));
        verify(usuarioRepository, never()).inserir(any());
    }

    @Test
    void naoPermiteRepetirConfiguracaoDepoisDoPrimeiroAcesso() {
        when(usuarioRepository.listar()).thenReturn(List.of(new Usuario()));

        assertThrows(IllegalStateException.class,
                () -> primeiroAcessoService.configurar("GOTE", "gestor", "senha-segura", "senha-segura"));
        verify(configuracaoRepository, never()).atualizar(any());
        verify(usuarioRepository, never()).inserir(any());
    }
}