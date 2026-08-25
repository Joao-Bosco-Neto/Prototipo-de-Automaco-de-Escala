package br.edu.sistemaescala.backend.service;

import java.util.List;
import java.util.Optional;

import br.edu.sistemaescala.backend.model.RoleUsuario;
import br.edu.sistemaescala.backend.model.Usuario;

public interface GestaoUsuariosService {

    List<Usuario> listar();

    Optional<Usuario> buscarPorId(int id);

    Usuario cadastrar(String nome, String login, String senha, String confirmacaoSenha, RoleUsuario role, boolean ativo);

    Usuario atualizar(int id, String nome, String login, RoleUsuario role, boolean ativo);

    void alterarStatus(int id, boolean ativo);

    void redefinirSenha(int id, String novaSenha, String confirmacaoSenha);
}

