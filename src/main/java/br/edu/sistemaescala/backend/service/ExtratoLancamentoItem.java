package br.edu.sistemaescala.backend.service;

import java.time.LocalDate;

import br.edu.sistemaescala.backend.model.TipoLancamento;

/**
 * Uma linha do extrato individual do banco de horas, já com o saldo acumulado
 * progressivo calculado na camada de serviço (issue #49).
 *
 * <p>O controller nunca soma nada: recebe cada linha com sua {@code variacaoMinutos}
 * (o valor do próprio lançamento, positivo = crédito, negativo = débito) e com o
 * {@code saldoAcumuladoMinutos}, que é a soma de todos os lançamentos do recorte
 * até e inclusive este, na ordem cronológica ({@code data_referencia ASC, id ASC}).</p>
 *
 * @param id                    id do {@code lancamento_horas}
 * @param dataReferencia        data de referência do lançamento
 * @param tipo                  motivo do lançamento
 * @param descricao             justificativa/descrição livre (pode ser nula ou vazia)
 * @param variacaoMinutos       variação deste lançamento, em minutos
 * @param saldoAcumuladoMinutos saldo progressivo até este lançamento, em minutos
 */
public record ExtratoLancamentoItem(
        int id,
        LocalDate dataReferencia,
        TipoLancamento tipo,
        String descricao,
        int variacaoMinutos,
        long saldoAcumuladoMinutos
) {}
