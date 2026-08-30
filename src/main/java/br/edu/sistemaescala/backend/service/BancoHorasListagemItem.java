package br.edu.sistemaescala.backend.service;

/**
 * Linha da listagem da tela de Banco de Horas.
 *
 * As três primeiras métricas são apuradas dentro do mês selecionado na UI
 * (atuação dentro do ciclo da escala). O {@code saldoMinutos} é o somatório
 * histórico e incondicional de {@code lancamento_horas}, sem recorte de mês —
 * é um valor contínuo, não uma métrica do ciclo.
 *
 * @param funcionarioId      id do funcionário
 * @param nome               nome do funcionário
 * @param matricula          matrícula do funcionário
 * @param plantoesCumpridos  plantões próprios cumpridos no mês (exclui coberturas
 *                           feitas e exclui plantões dele cobertos por terceiros)
 * @param coberturasFeitas   plantões que ele cobriu de outros no mês
 * @param plantoesCobertos   plantões dele, no mês, que foram assumidos por terceiros
 * @param saldoMinutos       saldo total (crédito - débito) em minutos, todo o histórico
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
