package br.edu.sistemaescala.backend.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import br.edu.sistemaescala.backend.model.Usuario;

public interface UsuarioRepository {

    Optional<Usuario> buscarPorNome(String nome);

    List<Usuario> listar();

    Usuario inserir(Usuario usuario);

    Usuario atualizar(Usuario usuario);

    void desativar(int id);

    void atualizarSenha(int id, String senhaHash);

    void registrarUltimoLogin(int id, LocalDateTime ultimoLogin);
}