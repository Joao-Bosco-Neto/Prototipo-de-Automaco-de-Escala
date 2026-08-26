package br.edu.sistemaescala.backend.service;

import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import br.edu.sistemaescala.backend.model.Funcionario;

/**
 * Interface de regras de negócio e consultas para gestão de funcionários.
 */
public interface FuncionarioService {

    /**
     * Lista funcionários com o total de plantões calculados para o mês informado.
     *
     * @param statusAtivo   true = apenas ativos, false = apenas inativos, null = todos
     * @param termoBusca    filtro por nome ou matrícula (case-insensitive)
     * @param mesReferencia mês para contagem de plantões (null assume YearMonth.now())
     */
    List<FuncionarioListagemItem> listarComPlantoes(Boolean statusAtivo, String termoBusca, YearMonth mesReferencia);

    List<Funcionario> listar(Boolean statusAtivo, String termoBusca);

    Optional<Funcionario> buscarPorId(int id);

    void ativar(int id);

    void desativar(int id);

    int contarPlantoesNoMes(int funcionarioId, YearMonth mesReferencia);
}

