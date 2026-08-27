package br.edu.sistemaescala.backend.service;

import br.edu.sistemaescala.backend.model.EscalaTurno;

/**
 * Regras de negócio de montagem da escala: duplicidade/sobreposição de
 * alocações e mínimo de agentes por turno (Issue #23) e intervalo de descanso
 * obrigatório entre plantões (Issue #40).
 *
 * <p>Nenhum método lança exceção para impedir o salvamento: eles retornam o
 * resultado da análise com a mensagem pronta, e a camada de cima decide entre
 * bloquear, avisar ou seguir.</p>
 */
public interface RegraEscalaService {

    /**
     * Verifica se o funcionário pode ser alocado no turno informado, checando
     * se ele já está no próprio turno (duplicidade) e se possui outra alocação
     * cujo período se sobrepõe ao deste turno.
     */
    ResultadoAlocacao podeAlocar(int funcionarioId, EscalaTurno turno);

    /**
     * Confere quantos agentes estão alocados no turno em relação ao mínimo
     * exigido por ele.
     */
    ResultadoEfetivo verificarEfetivo(EscalaTurno turno);

    /**
     * Confere se o funcionário respeita o intervalo de descanso exigido pelo
     * tipo do turno informado, contado do fim de um plantão até o início do
     * próximo.
     *
     * <p>A checagem vale nos dois sentidos: alocar o funcionário num dia pode
     * ser inválido tanto por um plantão anterior quanto por um posterior.</p>
     */
    ResultadoDescanso verificarDescanso(int funcionarioId, EscalaTurno turno);
}
