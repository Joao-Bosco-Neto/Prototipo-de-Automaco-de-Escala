package br.edu.sistemaescala.backend.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import at.favre.lib.crypto.bcrypt.BCrypt;

import br.edu.sistemaescala.backend.model.RoleUsuario;
import br.edu.sistemaescala.backend.model.Usuario;
import br.edu.sistemaescala.backend.repository.UsuarioRepository;

class AutenticacaoServiceImplTest {

    private static final String LOGIN = "usuario-teste";
    private static final String SENHA_CORRETA = "senha-correta-123";
    private static final int USUARIO_ID = 1;

    private UsuarioRepository usuarioRepository;
    private AutenticacaoService autenticacaoService;
    private Usuario usuarioAtivo;
    private List<Long> atrasos;

    @BeforeEach
    void prepararMocks() {
        usuarioRepository = mock(UsuarioRepository.class);
        atrasos = new ArrayList<>();
        autenticacaoService = new AutenticacaoServiceImpl(usuarioRepository, atrasos::add);

        String hash = BCrypt.withDefaults().hashToString(12, SENHA_CORRETA.toCharArray());
        usuarioAtivo = new Usuario(USUARIO_ID, "Usuario Teste", LOGIN, hash,
                RoleUsuario.GESTOR, true, null, null);
    }

    @Test
    void autenticarComLoginESenhaCorretosRetornaUsuarioERegistraUltimoLogin() {
        when(usuarioRepository.buscarPorLogin(LOGIN)).thenReturn(Optional.of(usuarioAtivo));

        Optional<Usuario> resultado = autenticacaoService.autenticar(LOGIN, SENHA_CORRETA);

        assertTrue(resultado.isPresent());
        assertEquals(USUARIO_ID, resultado.orElseThrow().getId());
        verify(usuarioRepository).registrarUltimoLogin(eq(USUARIO_ID), any(LocalDateTime.class));
    }

    @Test
    void autenticarComSenhaErradaRetornaOptionalVazio() {
        when(usuarioRepository.buscarPorLogin(LOGIN)).thenReturn(Optional.of(usuarioAtivo));

        Optional<Usuario> resultado = autenticacaoService.autenticar(LOGIN, "senha-errada");

        assertTrue(resultado.isEmpty());
        verify(usuarioRepository, never()).registrarUltimoLogin(anyInt(), any(LocalDateTime.class));
    }

    @Test
    void autenticarComLoginInexistenteRetornaOptionalVazio() {
        when(usuarioRepository.buscarPorLogin("nao-existe")).thenReturn(Optional.empty());

        Optional<Usuario> resultado = autenticacaoService.autenticar("nao-existe", SENHA_CORRETA);

        assertTrue(resultado.isEmpty());
        verify(usuarioRepository, never()).registrarUltimoLogin(anyInt(), any(LocalDateTime.class));
    }

    @Test
    void loginInexistenteESenhaErradaTemResultadoIdentico() {
        when(usuarioRepository.buscarPorLogin("nao-existe")).thenReturn(Optional.empty());
        when(usuarioRepository.buscarPorLogin(LOGIN)).thenReturn(Optional.of(usuarioAtivo));

        Optional<Usuario> resultadoLoginInexistente = autenticacaoService.autenticar("nao-existe", SENHA_CORRETA);
        Optional<Usuario> resultadoSenhaErrada = autenticacaoService.autenticar(LOGIN, "senha-errada");

        assertEquals(resultadoLoginInexistente, resultadoSenhaErrada);
        assertTrue(resultadoLoginInexistente.isEmpty());
    }

    @Test
    void falhasAplicamAtrasoProgressivoELoginCorretoReiniciaContador() {
        when(usuarioRepository.buscarPorLogin(LOGIN)).thenReturn(Optional.of(usuarioAtivo));

        autenticacaoService.autenticar(LOGIN, "senha-errada");
        autenticacaoService.autenticar(LOGIN, "senha-errada");
        assertEquals(List.of(250L, 500L), atrasos);

        autenticacaoService.autenticar(LOGIN, SENHA_CORRETA);
        autenticacaoService.autenticar(LOGIN, "senha-errada");
        assertEquals(List.of(250L, 500L, 250L), atrasos);
    }

    @Test
    void autenticarComUsuarioInativoRetornaOptionalVazioMesmoComSenhaCerta() {
        Usuario usuarioInativo = new Usuario(USUARIO_ID, "Usuario Teste", LOGIN, usuarioAtivo.getSenhaHash(),
                RoleUsuario.GESTOR, false, null, null);
        when(usuarioRepository.buscarPorLogin(LOGIN)).thenReturn(Optional.of(usuarioInativo));

        Optional<Usuario> resultado = autenticacaoService.autenticar(LOGIN, SENHA_CORRETA);

        assertTrue(resultado.isEmpty());
        verify(usuarioRepository, never()).registrarUltimoLogin(anyInt(), any(LocalDateTime.class));
    }

    @Test
    void gerarHashNuncaEIgualASenhaOriginalEVerifyerConfirmaQueBate() {
        String senha = "senha-para-hash-456";

        String hash = autenticacaoService.gerarHash(senha);

        assertNotEquals(senha, hash);
        assertTrue(BCrypt.verifyer().verify(senha.toCharArray(), hash).verified);
    }

    @Test
    void rejeitaSenhaComMenosDeOitoCaracteres() {
        assertThrows(SenhaFracaException.class, () -> autenticacaoService.gerarHash("1234567"));
    }

    @Test
    void alterarSenhaNaoExigeRotacaoMasExigeMinimoDeOitoCaracteres() {
        when(usuarioRepository.buscarPorId(USUARIO_ID)).thenReturn(Optional.of(usuarioAtivo));

        assertThrows(SenhaFracaException.class,
                () -> autenticacaoService.alterarSenha(USUARIO_ID, SENHA_CORRETA, "1234567"));
        verify(usuarioRepository, never()).atualizarSenha(anyInt(), anyString());
    }

    @Test
    void alterarSenhaComSenhaAtualCorretaChamaAtualizarSenhaComNovoHash() {
        when(usuarioRepository.buscarPorId(USUARIO_ID)).thenReturn(Optional.of(usuarioAtivo));
        String senhaNova = "senha-nova-789";

        autenticacaoService.alterarSenha(USUARIO_ID, SENHA_CORRETA, senhaNova);

        ArgumentCaptor<String> hashCapturado = ArgumentCaptor.forClass(String.class);
        verify(usuarioRepository).atualizarSenha(eq(USUARIO_ID), hashCapturado.capture());
        assertNotEquals(senhaNova, hashCapturado.getValue());
        assertTrue(BCrypt.verifyer().verify(senhaNova.toCharArray(), hashCapturado.getValue()).verified);
    }

    @Test
    void alterarSenhaComSenhaAtualErradaLancaSenhaInvalidaExceptionENuncaAtualiza() {
        when(usuarioRepository.buscarPorId(USUARIO_ID)).thenReturn(Optional.of(usuarioAtivo));

        assertThrows(SenhaInvalidaException.class,
                () -> autenticacaoService.alterarSenha(USUARIO_ID, "senha-atual-errada", "senha-nova-789"));

        verify(usuarioRepository, never()).atualizarSenha(anyInt(), anyString());
    }
}
