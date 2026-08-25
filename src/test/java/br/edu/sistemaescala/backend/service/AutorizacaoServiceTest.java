package br.edu.sistemaescala.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import br.edu.sistemaescala.backend.model.RoleUsuario;
import br.edu.sistemaescala.backend.model.Usuario;

class AutorizacaoServiceTest {

    private final AutorizacaoService autorizacaoService = new AutorizacaoService();

    @Test
    void permiteAcessoAoAdministradorAutenticado() {
        SessaoUsuario sessao = new SessaoUsuario();
        Usuario administrador = new Usuario();
        administrador.setLogin("admin");
        administrador.setRole(RoleUsuario.ADMIN);
        sessao.iniciar(administrador);

        Usuario retornado = autorizacaoService.exigirAdministrador(sessao);
        assertNotNull(retornado);
        assertEquals("admin", retornado.getLogin());
        assertEquals(RoleUsuario.ADMIN, retornado.getRole());
    }

    @Test
    void recusaAcessoDeAdministradorParaPerfilGestor() {
        SessaoUsuario sessao = new SessaoUsuario();
        Usuario gestor = new Usuario();
        gestor.setLogin("gestor");
        gestor.setRole(RoleUsuario.GESTOR);
        sessao.iniciar(gestor);

        AcessoNegadoException excecao = assertThrows(AcessoNegadoException.class,
                () -> autorizacaoService.exigirAdministrador(sessao));
        assertEquals("Apenas administradores podem acessar esta area", excecao.getMessage());
    }

    @Test
    void recusaAcessoDeAdministradorSemSessaoAutenticada() {
        SessaoUsuario sessaoVazia = new SessaoUsuario();
        assertThrows(AcessoNegadoException.class,
                () -> autorizacaoService.exigirAdministrador(sessaoVazia));

        assertThrows(AcessoNegadoException.class,
                () -> autorizacaoService.exigirAdministrador(null));
    }

    @Test
    void validaExigirAutenticadoComSucessoEErro() {
        SessaoUsuario sessao = new SessaoUsuario();
        Usuario usuario = new Usuario();
        usuario.setLogin("operador");
        usuario.setRole(RoleUsuario.GESTOR);
        sessao.iniciar(usuario);

        assertEquals(usuario, autorizacaoService.exigirAutenticado(sessao));

        assertThrows(AcessoNegadoException.class,
                () -> autorizacaoService.exigirAutenticado(new SessaoUsuario()));
        assertThrows(AcessoNegadoException.class,
                () -> autorizacaoService.exigirAutenticado(null));
    }

    @Test
    void validaExigirRoleEspecifica() {
        SessaoUsuario sessao = new SessaoUsuario();
        Usuario gestor = new Usuario();
        gestor.setLogin("gestor");
        gestor.setRole(RoleUsuario.GESTOR);
        sessao.iniciar(gestor);

        assertEquals(gestor, autorizacaoService.exigirRole(sessao, RoleUsuario.GESTOR, "Erro customizado"));

        AcessoNegadoException excecao = assertThrows(AcessoNegadoException.class,
                () -> autorizacaoService.exigirRole(sessao, RoleUsuario.ADMIN, "Acesso restrito ao Admin"));
        assertEquals("Acesso restrito ao Admin", excecao.getMessage());
    }

    @Test
    void verificaPossuiRole() {
        SessaoUsuario sessao = new SessaoUsuario();
        assertFalse(autorizacaoService.possuiRole(sessao, RoleUsuario.ADMIN));
        assertFalse(autorizacaoService.possuiRole(null, RoleUsuario.ADMIN));

        Usuario gestor = new Usuario();
        gestor.setRole(RoleUsuario.GESTOR);
        sessao.iniciar(gestor);

        assertTrue(autorizacaoService.possuiRole(sessao, RoleUsuario.GESTOR));
        assertFalse(autorizacaoService.possuiRole(sessao, RoleUsuario.ADMIN));
    }
}