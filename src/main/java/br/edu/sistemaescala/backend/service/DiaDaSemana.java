package br.edu.sistemaescala.backend.service;

import java.time.LocalDate;
import java.util.List;

/**
 * Uma coluna da faixa da semana da tela "Visão geral" (issue #54).
 *
 * <p>A semana vai de domingo a sábado, como a grade do calendário de montagem
 * — a lista sempre tem sete itens, inclusive os dias sem nenhum turno, que
 * chegam com {@code turnos} vazio para a coluna aparecer vazia em vez de
 * sumir.</p>
 *
 * @param dia    data da coluna
 * @param hoje   se esta é a coluna do dia de referência, que a tela destaca
 * @param turnos turnos que começam neste dia (mais de um em 12x36)
 */
public record DiaDaSemana(LocalDate dia, boolean hoje, List<TurnoResumido> turnos) {

    public boolean semTurnos() {
        return turnos.isEmpty();
    }
}
