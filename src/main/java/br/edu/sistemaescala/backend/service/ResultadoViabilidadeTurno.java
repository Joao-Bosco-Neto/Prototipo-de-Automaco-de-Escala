package br.edu.sistemaescala.backend.service;

/**
 * Resultado da análise de viabilidade do regime de trabalho do tipo de turno
 * em relação ao efetivo de funcionários ativos disponíveis.
 */
public record ResultadoViabilidadeTurno(
        boolean viavel,
        int efetivoMinimoNecessario,
        int efetivoAtivoDisponivel,
        String mensagem
) {
}

