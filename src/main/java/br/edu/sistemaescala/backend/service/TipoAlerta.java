package br.edu.sistemaescala.backend.service;

/**
 * Que verificação gerou a pendência do painel da "Visão geral" (issue #55).
 *
 * <p>Existe para o alerta ser identificável sem depender do texto exibido:
 * o texto muda com o conteúdo (quantidades, nomes) e não serve de chave, nem
 * para o teste nem para uma futura navegação a partir do alerta.</p>
 */
public enum TipoAlerta {

    /** Dias do mês corrente com pelo menos um turno abaixo do mínimo de agentes. */
    EFETIVO_INCOMPLETO,

    /** Funcionário desativado que continua escalado em turno que ainda vai acontecer. */
    INATIVO_ESCALADO,

    /** O mês seguinte ainda não tem nenhum turno montado. */
    PROXIMO_MES_SEM_ESCALA,

    /** Cobertura registrada sem o crédito/débito correspondente no banco de horas. */
    COBERTURA_SEM_LANCAMENTO
}
