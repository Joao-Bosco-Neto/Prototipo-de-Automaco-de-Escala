package br.edu.sistemaescala.backend.service;

import java.util.Optional;

import br.edu.sistemaescala.backend.model.Usuario;

public interface AutenticacaoService {

    Optional<Usuario> autenticar(String login, String senha);

    String gerarHash(String senhaPura);

    void alterarSenha(int usuarioId, String senhaAtual, String senhaNova);
}
