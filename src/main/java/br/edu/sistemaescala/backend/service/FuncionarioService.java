package br.edu.sistemaescala.backend.service;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import br.edu.sistemaescala.backend.model.EscalaFuncionario;
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

    /**
     * Cadastra um novo funcionário no sistema após validar regras de negócio.
     */
    Funcionario cadastrar(String nome, String matricula, String telefone, String observacoes, boolean ativo);

    /**
     * Atualiza os dados de um funcionário existente no sistema.
     */
    Funcionario atualizar(int id, String nome, String matricula, String telefone, String observacoes, boolean ativo);

    /**
     * Ativa o funcionário pelo ID.
     */
    void ativar(int id);

    /**
     * Desativa logicamente o funcionário pelo ID, preservando seu histórico.
     */
    void desativar(int id);

    /**
     * Quantidade de plantões do funcionário no mês informado.
     */
    int contarPlantoesNoMes(int funcionarioId, YearMonth mesReferencia);

    /**
     * Conta a quantidade de plantões futuros agendados para o funcionário a partir da data/hora informada.
     */
    int contarPlantoesFuturos(int funcionarioId, LocalDateTime aPartirDe);

    /**
     * Lista os plantões futuros agendados para o funcionário a partir da data/hora informada.
     */
    List<EscalaFuncionario> buscarPlantoesFuturos(int funcionarioId, LocalDateTime aPartirDe);
}
