package br.edu.sistemaescala.backend.repository;

import br.edu.sistemaescala.backend.model.LancamentoHoras;

import java.time.LocalDate;
import java.util.List;

/**
 * Repositorio de lancamentos do banco de horas (issue #12).
 *
 * Saldo e extrato sao sempre calculados sobre esta tabela, nunca sobre um
 * campo unico em funcionario — e o que sustenta o "Ver extrato" e o estorno
 * quando uma cobertura e excluida ou um mes e limpo.
 */
public interface LancamentoHorasRepository {

    /** Insere um lancamento e retorna a mesma instancia com o id gerado preenchido. */
    LancamentoHoras salvar(LancamentoHoras lancamento);

    /**
     * Estorno: remove todos os lancamentos vinculados a uma escala_funcionario
     * especifica (ex.: cobertura excluida, "Limpar mes").
     *
     * @return quantidade de lancamentos removidos
     */
    int removerPorEscalaFuncionarioId(int escalaFuncionarioId);

    /** Extrato de um funcionario no periodo (inclusive), ordenado por data. */
    List<LancamentoHoras> buscarExtrato(int funcionarioId, LocalDate inicio, LocalDate fim);

    /** Saldo em minutos (credito - debito) de um funcionario no periodo. Zero se nao houver lancamentos. */
    int somarSaldoMinutos(int funcionarioId, LocalDate inicio, LocalDate fim);
}
