package br.edu.sistemaescala.backend.repository;

import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import br.edu.sistemaescala.backend.model.Funcionario;

/**
 * Repositorio de funcionarios (pessoas escaladas; issue #10).
 *
 * Funcionario nunca e excluido do banco, so desativado (coluna ativo) — e o
 * que sustenta o filtro Ativos/Inativos da tela e preserva o historico de
 * plantoes: escala_funcionario referencia funcionario_id sem cascata, entao
 * apagar a linha quebraria plantoes ja registrados.
 */
public interface FuncionarioRepository {

    /**
     * Lista funcionarios combinando filtro de status e busca por texto livre.
     *
     * @param ativo      true = so ativos, false = so inativos, null = todos
     * @param textoBusca trecho do nome ou da matricula (case-insensitive); null ou vazio = sem filtro
     */
    List<Funcionario> listar(Boolean ativo, String textoBusca);

    Optional<Funcionario> buscarPorId(int id);

    /** Insere um funcionario e retorna a mesma instancia com o id gerado preenchido. */
    Funcionario inserir(Funcionario funcionario);

    Funcionario atualizar(Funcionario funcionario);

    void ativar(int id);

    /** Preserva o historico: so marca ativo = false, nunca remove a linha. */
    void desativar(int id);

    /**
     * Verifica se ja existe funcionario cadastrado com essa matricula.
     *
     * @param idParaExcluir id do proprio funcionario a ignorar na checagem (edicao); null na inclusao
     */
    boolean existeMatricula(String matricula, Integer idParaExcluir);

    /** Quantidade de plantoes (escala_funcionario) do funcionario no mes — coluna "Plantoes/mes" da tela. */
    int contarPlantoesNoMes(int funcionarioId, YearMonth mes);
}
