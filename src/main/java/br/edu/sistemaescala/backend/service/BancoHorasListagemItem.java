package br.edu.sistemaescala.backend.service;

/**
 * Linha da listagem da tela de Banco de Horas.
 *
 * Todos os campos são apurados dentro do mesmo recorte: o mês selecionado na
 * UI. A apuração é estanque — o {@code saldoMinutos} soma apenas os
 * {@code lancamento_horas} com {@code data_referencia} dentro do mês, sem
 * arrastar resíduo de meses anteriores. Quando a tela pede o histórico
 * completo (sem mês), o recorte cai e tudo vira acumulado.
 *
 * @param funcionarioId      id do funcionário
 * @param nome               nome do funcionário
 * @param matricula          matrícula do funcionário
 * @param plantoesCumpridos  plantões próprios cumpridos no mês (exclui coberturas
 *                           feitas e exclui plantões dele cobertos por terceiros)
 * @param coberturasFeitas   plantões que ele cobriu de outros no mês
 * @param plantoesCobertos   plantões dele, no mês, que foram assumidos por terceiros
 * @param saldoMinutos       saldo (crédito - débito) em minutos, apurado no mês
 */
public record BancoHorasListagemItem(
        int funcionarioId,
        String nome,
        String matricula,
        int plantoesCumpridos,
        int coberturasFeitas,
        int plantoesCobertos,
        long saldoMinutos) {
}
