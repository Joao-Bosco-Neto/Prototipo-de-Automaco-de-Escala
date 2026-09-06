package br.edu.sistemaescala.backend.repository;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import br.edu.sistemaescala.backend.model.Funcionario;
import br.edu.sistemaescala.backend.service.ContagemFuncionarios;

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

    /**
     * Lista funcionarios combinando filtro de status, busca por texto livre e ordenação segura
     * validada contra lista fechada de colunas permitidas (OWASP A05).
     *
     * @param ativo            true = so ativos, false = so inativos, null = todos
     * @param textoBusca       trecho do nome ou da matricula (case-insensitive); null ou vazio = sem filtro
     * @param colunaOrdenacao  coluna permitida: "nome", "matricula", "telefone", "ativo", "criado_em", "id"
     * @param ascendente       true = ASC, false = DESC
     */
    default List<Funcionario> listar(Boolean ativo, String textoBusca, String colunaOrdenacao, boolean ascendente) {
        return listar(ativo, textoBusca);
    }

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

    /**
     * Conta, num COUNT so, quantos funcionarios estao ativos e quantos estao
     * inativos — o card "Funcionarios ativos" do dashboard (issue #53).
     *
     * <p>Existe em vez de {@code listar(true, null).size()} porque o criterio
     * da issue e contar no banco, sem trazer a lista inteira para a memoria so
     * para medir o tamanho dela.</p>
     */
    ContagemFuncionarios contarPorStatus();

    /**
     * Funcionarios desativados que continuam escalados em algum turno que
     * comeca em {@code instante} ou depois — a pendencia da issue #55.
     *
     * <p>Devolve os funcionarios, e nao uma contagem, porque o alerta cita os
     * nomes: sem eles o gestor saberia que ha um problema, mas nao em quem.
     * A lista e naturalmente curta (so inativos ainda escalados) e sai
     * distinta, um registro por pessoa, mesmo que ela tenha varios plantoes
     * pela frente.</p>
     */
    List<Funcionario> listarInativosEscaladosApos(LocalDateTime instante);
}
