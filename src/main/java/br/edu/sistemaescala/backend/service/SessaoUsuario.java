package br.edu.sistemaescala.backend.service;

import java.util.Optional;

import br.edu.sistemaescala.backend.model.Usuario;

public class SessaoUsuario {
    private Usuario usuarioAtual;

    public void iniciar(Usuario usuario) {
        if (usuario == null) {
            throw new IllegalArgumentException("Usuario da sessao nao pode ser nulo");
        }
        usuarioAtual = usuario;
    }

    public void encerrar() {
        usuarioAtual = null;
    }

    public Optional<Usuario> usuarioAtual() {
        return Optional.ofNullable(usuarioAtual);
    }

    public Usuario exigirUsuario() {
        return usuarioAtual().orElseThrow(() -> new AcessoNegadoException("Nenhum usuario autenticado"));
    }
}