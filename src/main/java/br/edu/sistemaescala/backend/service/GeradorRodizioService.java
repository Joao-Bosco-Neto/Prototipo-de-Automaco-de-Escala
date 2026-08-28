package br.edu.sistemaescala.backend.service;

import java.time.YearMonth;

/**
 * Geração automática do rodízio mensal (RF09 / Issue #43).
 *
 * <p>Preenche o mês inteiro de uma vez: para cada dia, cria um
 * {@code escala_turno} por tipo de turno ativo e distribui os funcionários
 * ativos em sequência circular até o mínimo de agentes de cada turno.</p>
 *
 * <p>O regime vem da configuração, não do código: um tipo de turno ativo
 * produz um turno por dia (24x72) e dois tipos ativos produzem dois turnos por
 * dia (12x36), pelo mesmo laço.</p>
 *
 * <p>A geração não força nada: cada alocação passa por
 * {@link RegraEscalaService#podeAlocar} e
 * {@link RegraEscalaService#verificarDescanso} antes de entrar. Candidato
 * indisponível é pulado.</p>
 *
 * <p>Quando o mínimo de agentes de um turno não fecha, o turno fica vazio em
 * vez de ficar com meia equipe, e o dia é contabilizado no
 * {@link ResultadoGeracao} — sem interromper a geração. Escalar o agente
 * solitário travaria o rodízio em duplas fixas, porque o descanso dele
 * passaria a cair sempre na mesma fase do ciclo.</p>
 */
public interface GeradorRodizioService {

    /**
     * Gera o rodízio do mês informado.
     *
     * <p>Tudo ou nada: a gravação roda numa única transação, então uma falha
     * no meio não deixa meio mês gerado.</p>
     *
     * @param mes         mês a preencher
     * @param sobrescrever true para apagar os turnos já existentes do mês
     *                     antes de gerar; false para recusar a geração quando
     *                     o mês já tem escala
     * @return o resultado da geração, com os números e a mensagem prontos
     */
    ResultadoGeracao gerarMes(YearMonth mes, boolean sobrescrever);
}
