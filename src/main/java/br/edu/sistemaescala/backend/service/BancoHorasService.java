package br.edu.sistemaescala.backend.service;

import java.time.YearMonth;
import java.util.List;

import br.edu.sistemaescala.backend.model.LancamentoHoras;

/**
 * Regras e consultas da tela de Banco de Horas.
 */
public interface BancoHorasService {

    /**
     * Listagem da tela: uma linha por funcionário, com as métricas e o saldo do
     * mês informado. {@code null} soma todo o histórico (sem filtro de mês).
     */
    List<BancoHorasListagemItem> listarMensal(YearMonth mesReferencia);

    /**
     * Extrato cronológico de {@code lancamento_horas} do funcionário no mês
     * informado. {@code null} traz o extrato completo.
     */
    List<LancamentoHoras> buscarExtrato(int funcionarioId, YearMonth mesReferencia);

    /**
     * Extrato individual detalhado do funcionário no mês informado, com o saldo
     * acumulado progressivo já calculado linha a linha (issue #49). {@code null}
     * traz o histórico completo. A lista vem em ordem cronológica
     * ({@code data_referencia ASC, id ASC}); o {@code saldoAcumuladoMinutos} da
     * última linha coincide com o saldo total apurado no recorte.
     */
    List<ExtratoLancamentoItem> buscarExtratoDetalhado(int funcionarioId, YearMonth mesReferencia);

    /**
     * Lança um ajuste manual (tipo {@code ajuste_manual}) no banco de horas.
     *
     * @param funcionarioId funcionário alvo
     * @param horas         quantidade de horas, sempre positiva; o sinal vem de {@code credito}
     * @param credito       {@code true} credita (positivo), {@code false} debita (negativo)
     * @param descricao     justificativa do ajuste (obrigatória)
     * @return o lançamento persistido, com id preenchido
     */
    LancamentoHoras lancarAjusteManual(int funcionarioId, double horas, boolean credito, String descricao);
}
