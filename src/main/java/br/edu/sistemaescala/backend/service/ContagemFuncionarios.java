package br.edu.sistemaescala.backend.service;

/**
 * Quantos funcionários existem em cada status, para o card "Funcionários
 * ativos" do dashboard (issue #53).
 *
 * <p>Os dois números saem de uma consulta agregada só; funcionário nunca é
 * excluído do banco, apenas desativado, então {@code ativos + inativos} é o
 * total cadastrado.</p>
 *
 * @param ativos   funcionários com {@code ativo = TRUE}
 * @param inativos funcionários desativados (histórico preservado)
 */
public record ContagemFuncionarios(int ativos, int inativos) {

    public int total() {
        return ativos + inativos;
    }
}
