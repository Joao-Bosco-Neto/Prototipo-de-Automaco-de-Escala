package br.edu.sistemaescala.backend.service;

/**
 * Resultado da análise de uma tentativa de alocar um funcionário num turno
 * (duplicidade e sobreposição de período).
 *
 * <p>Segue o mesmo desenho de {@link ResultadoViabilidadeTurno}: o service
 * apenas informa, com a mensagem já pronta para exibição, e quem decide o que
 * fazer é a camada de cima.</p>
 */
public record ResultadoAlocacao(
        boolean permitido,
        String mensagem
) {
}
