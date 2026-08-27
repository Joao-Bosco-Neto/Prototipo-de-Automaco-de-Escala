package br.edu.sistemaescala.backend.service;

import java.time.Duration;

/**
 * Resultado da conferência do intervalo de descanso obrigatório entre o turno
 * analisado e os plantões vizinhos do funcionário (RF11 / Issue #40).
 *
 * <p>Segue o mesmo desenho de {@link ResultadoAlocacao} e
 * {@link ResultadoViabilidadeTurno}: o service apenas informa, com a mensagem
 * já pronta para exibição, e quem decide o que fazer é a camada de cima.</p>
 *
 * @param respeitado        false quando algum plantão vizinho fica perto demais
 * @param descansoExigido   intervalo exigido pelo tipo de turno analisado
 * @param descansoEncontrado menor intervalo encontrado até um plantão vizinho,
 *                          ou {@code null} quando não há plantão vizinho algum
 */
public record ResultadoDescanso(
        boolean respeitado,
        Duration descansoExigido,
        Duration descansoEncontrado,
        String mensagem
) {
}
