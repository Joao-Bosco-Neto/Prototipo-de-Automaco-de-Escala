package br.edu.sistemaescala.backend.repository;

import br.edu.sistemaescala.backend.model.MotivoCobertura;

import java.util.List;
import java.util.Optional;

public interface MotivoCoberturaRepository {

    List<MotivoCobertura> listar(Boolean ativo);

    Optional<MotivoCobertura> buscarPorId(int id);

    MotivoCobertura inserir(MotivoCobertura motivo);

    MotivoCobertura atualizar(MotivoCobertura motivo);

    void ativar(int id);

    void desativar(int id);
}