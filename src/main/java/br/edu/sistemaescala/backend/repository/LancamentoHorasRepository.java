package br.edu.sistemaescala.backend.repository;

import br.edu.sistemaescala.backend.model.LancamentoHoras;

import java.sql.Connection;
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
     * Mesma insercao, porem numa Connection recebida de fora — e assim que o
     * lancamento participa de uma transacao aberta pelo TransacaoUtil, junto
     * com a alocacao de cobertura que o originou (issue #33): o credito de quem
     * cobre e o debito do ausente entram no banco de horas ou nenhum dos dois.
     *
     * A conexao continua sendo de quem chamou: este metodo nao faz commit,
     * rollback nem close.
     */
    LancamentoHoras salvar(LancamentoHoras lancamento, Connection conexao);

    /**
     * Estorno: remove todos os lancamentos vinculados a uma escala_funcionario
     * especifica (ex.: cobertura excluida, "Limpar mes").
     *
     * @return quantidade de lancamentos removidos
     */
    int removerPorEscalaFuncionarioId(int escalaFuncionarioId);

    /**
     * Mesmo estorno, porem numa Connection recebida de fora — e assim que a
     * exclusao do par credito/debito participa da mesma transacao que atualiza
     * a cobertura durante uma edicao (issue #33): ou os lancamentos antigos
     * somem e os novos entram, ou nada muda.
     *
     * A conexao continua sendo de quem chamou: este metodo nao faz commit,
     * rollback nem close.
     *
     * @return quantidade de lancamentos removidos
     */
    int removerPorEscalaFuncionarioId(int escalaFuncionarioId, Connection conexao);

    /** Extrato de um funcionario no periodo (inclusive), ordenado por data. */
    List<LancamentoHoras> buscarExtrato(int funcionarioId, LocalDate inicio, LocalDate fim);

    /** Saldo em minutos (credito - debito) de um funcionario no periodo. Zero se nao houver lancamentos. */
    int somarSaldoMinutos(int funcionarioId, LocalDate inicio, LocalDate fim);
}
