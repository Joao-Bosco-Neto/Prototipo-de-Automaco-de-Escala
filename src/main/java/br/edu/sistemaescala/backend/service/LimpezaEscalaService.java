package br.edu.sistemaescala.backend.service;

import java.time.YearMonth;

/**
 * Limpeza da escala de um mês inteiro (RF10 / Issue #44).
 *
 * <p>É a contraparte do {@link GeradorRodizioService}: enquanto ele preenche o
 * mês, este esvazia. Existe separado porque hoje só dá para limpar um mês
 * sobrescrevendo-o pelo gerador, e nem sempre se quer uma escala nova no
 * lugar.</p>
 */
public interface LimpezaEscalaService {

    /**
     * Conta o que existe no mês, sem apagar nada.
     *
     * <p>Serve para a tela dizer ao usuário exatamente quantos turnos e
     * alocações a confirmação vai destruir, em vez de um aviso genérico.</p>
     */
    ResumoEscalaMes resumir(YearMonth mes);

    /**
     * Remove todos os turnos do mês e, em cascata, as alocações e os
     * lançamentos de banco de horas presos a eles.
     *
     * <p>Tudo numa transação só: uma falha no meio não deixa meio mês
     * apagado.</p>
     *
     * <p><b>A remoção é em cascata, não um estorno com registro de
     * reversão.</b> Os {@code lancamento_horas} vinculados às alocações são
     * apagados pela FK {@code escala_funcionario_id ON DELETE CASCADE} — o
     * saldo do banco de horas volta ao que era porque as linhas somem, e não
     * porque exista um lançamento de sinal contrário registrando a reversão.
     * Se a auditoria do banco de horas passar a exigir esse rastro, será outra
     * issue.</p>
     *
     * @return o resultado, com os números e a mensagem prontos; {@code limpo}
     *         vem false quando o mês já estava vazio
     */
    ResultadoLimpeza limparMes(YearMonth mes);
}
