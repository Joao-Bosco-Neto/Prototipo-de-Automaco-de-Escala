package br.edu.sistemaescala.backend.service;

/**
 * Resultado da limpeza da escala de um mês (Issue #44).
 *
 * <p>Segue o mesmo desenho de {@link ResultadoGeracao} e
 * {@link ResultadoAlocacao}: o service apenas informa, com a mensagem já
 * pronta para exibição, e quem decide o que fazer com o número é a camada de
 * cima.</p>
 *
 * <p>{@code limpo = false} significa que nada foi removido — hoje, só o caso
 * de o mês já estar vazio.</p>
 *
 * @param limpo               false quando não havia o que remover
 * @param turnosRemovidos     quantos escala_turno foram apagados
 * @param alocacoesRemovidas  quantas escala_funcionario saíram junto, em cascata
 */
public record ResultadoLimpeza(
        boolean limpo,
        int turnosRemovidos,
        int alocacoesRemovidas,
        String mensagem
) {
}
