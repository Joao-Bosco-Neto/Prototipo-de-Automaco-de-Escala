package br.edu.sistemaescala.backend.service;

/**
 * Resultado da geração automática do rodízio de um mês (RF09 / Issue #43).
 *
 * <p>Segue o mesmo desenho de {@link ResultadoAlocacao},
 * {@link ResultadoEfetivo} e {@link ResultadoViabilidadeTurno}: o service
 * apenas informa, com a mensagem já pronta para exibição, e quem decide o que
 * fazer com o número é a camada de cima.</p>
 *
 * <p>{@code gerado = false} significa que nada foi gravado — mês já preenchido
 * sem autorização de sobrescrita, nenhum tipo de turno ativo ou nenhum
 * funcionário ativo. Um mês com turnos incompletos é gerado do mesmo jeito
 * ({@code gerado = true}) e reporta o problema em
 * {@code diasSemEfetivoSuficiente}: escala incompleta pode ser salva com
 * aviso (decisão do cliente registrada em docs/CONTEXTO_PARA_IA.md).</p>
 *
 * @param gerado                    false quando nada foi gravado no banco
 * @param turnosCriados             quantos escala_turno foram inseridos
 * @param alocacoesCriadas          quantas escala_funcionario foram inseridas
 * @param diasSemEfetivoSuficiente  dias distintos do mês com pelo menos um
 *                                  turno abaixo do mínimo de agentes
 */
public record ResultadoGeracao(
        boolean gerado,
        int turnosCriados,
        int alocacoesCriadas,
        int diasSemEfetivoSuficiente,
        String mensagem
) {
}
