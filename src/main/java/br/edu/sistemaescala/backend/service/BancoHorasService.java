package br.edu.sistemaescala.backend.service;

import java.time.YearMonth;
import java.util.List;

import br.edu.sistemaescala.backend.model.LancamentoHoras;

/**
 * Regras e consultas da tela de Banco de Horas.
 */
public interface BancoHorasService {

    /**
     * Listagem da tela: uma linha por funcionário, com as métricas do mês
     * informado ({@code null} assume o mês corrente) e o saldo consolidado.
     */
    List<BancoHorasListagemItem> listarMensal(YearMonth mesReferencia);

    /**
     * Extrato cronológico completo de {@code lancamento_horas} do funcionário.
     */
    List<LancamentoHoras> buscarExtrato(int funcionarioId);

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
