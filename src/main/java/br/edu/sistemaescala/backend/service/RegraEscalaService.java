package br.edu.sistemaescala.backend.service;

import java.util.List;

import br.edu.sistemaescala.backend.model.EscalaFuncionario;
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
     * exigido por ele, consultando o banco para saber quem está alocado.
     */
    ResultadoEfetivo verificarEfetivo(EscalaTurno turno);

    /**
     * Mesma conferência, sobre uma lista de agentes que quem chama já tem em
     * mãos — sem ir ao banco de novo.
     *
     * <p>Existe para quem já carregou os agentes junto com o turno, caso do
     * {@code buscarPorPeriodo}, que traz o mês inteiro com os agentes
     * hidratados num JOIN só. Sem esta sobrecarga, pintar o estado de cada
     * célula do calendário custaria uma consulta por turno — 31 num mês
     * cheio, a cada redesenho da grade.</p>
     *
     * <p>A regra e a mensagem são exatamente as mesmas: a versão que consulta
     * o banco apenas busca a lista e delega para esta.</p>
     *
     * @param agentesDoTurno agentes já alocados no turno; lista vazia se não
     *                       houver nenhum
     */
    ResultadoEfetivo verificarEfetivo(EscalaTurno turno, List<EscalaFuncionario> agentesDoTurno);

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
