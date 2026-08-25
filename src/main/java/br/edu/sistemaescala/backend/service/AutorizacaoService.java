package br.edu.sistemaescala.backend.service;

import br.edu.sistemaescala.backend.model.RoleUsuario;
import br.edu.sistemaescala.backend.model.Usuario;

public class AutorizacaoService {

    public Usuario exigirAdministrador(SessaoUsuario sessao) {
        Usuario usuario = sessao.exigirUsuario();
        if (usuario.getRole() != RoleUsuario.ADMIN) {
            throw new AcessoNegadoException("Apenas administradores podem acessar esta area");
        }
        return usuario;
    }
}