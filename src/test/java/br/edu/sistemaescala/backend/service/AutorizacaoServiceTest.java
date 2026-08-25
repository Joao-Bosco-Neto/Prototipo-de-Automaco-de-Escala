package br.edu.sistemaescala.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

import br.edu.sistemaescala.backend.model.RoleUsuario;
import br.edu.sistemaescala.backend.model.Usuario;

class AutorizacaoServiceTest {

    private final AutorizacaoService autorizacaoService = new AutorizacaoService();

    @Test
    void permiteGestaoDeUsuariosSomenteAoAdministrador() {
        SessaoUsuario sessao = new SessaoUsuario();
        Usuario gestor = new Usuario();
        gestor.setRole(RoleUsuario.GESTOR);
        sessao.iniciar(gestor);

        assertThrows(AcessoNegadoException.class,
                () -> autorizacaoService.exigirAdministrador(sessao));

        Usuario administrador = new Usuario();
        administrador.setRole(RoleUsuario.ADMIN);
        sessao.iniciar(administrador);

        assertEquals(administrador, autorizacaoService.exigirAdministrador(sessao));
    }

    @Test
    void recusaAcessoSemSessaoAutenticada() {
        assertThrows(AcessoNegadoException.class,
                () -> autorizacaoService.exigirAdministrador(new SessaoUsuario()));
    }
}