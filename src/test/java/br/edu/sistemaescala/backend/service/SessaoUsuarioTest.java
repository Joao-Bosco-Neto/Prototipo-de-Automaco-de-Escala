package br.edu.sistemaescala.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import br.edu.sistemaescala.backend.model.Usuario;

class SessaoUsuarioTest {

    @Test
    void disponibilizaUsuarioAtualEPermiteEncerrarSessao() {
        SessaoUsuario sessao = new SessaoUsuario();
        Usuario usuario = new Usuario();
        usuario.setLogin("gestor");

        sessao.iniciar(usuario);

        assertEquals(usuario, sessao.usuarioAtual().orElseThrow());
        assertEquals(usuario, sessao.exigirUsuario());

        sessao.encerrar();

        assertTrue(sessao.usuarioAtual().isEmpty());
    }
}