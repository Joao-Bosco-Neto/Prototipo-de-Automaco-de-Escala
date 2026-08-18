package br.edu.sistemaescala.backend.repository;

import java.util.Optional;

import br.edu.sistemaescala.backend.model.Configuracao;

public interface ConfiguracaoRepository {

    Optional<Configuracao> buscar();

    Configuracao atualizar(Configuracao configuracao);
}