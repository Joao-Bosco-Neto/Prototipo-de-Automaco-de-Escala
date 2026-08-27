package br.edu.sistemaescala.backend.service;

/**
 * Resultado da conferência do efetivo de um turno em relação ao mínimo de
 * agentes exigido.
 *
 * <p>Um turno incompleto não impede o salvamento da escala — apenas gera
 * aviso, bloqueando somente a exportação do PDF (decisão do cliente registrada
 * em docs/CONTEXTO_PARA_IA.md).</p>
 */
public record ResultadoEfetivo(
        boolean completo,
        int alocados,
        int minimoExigido,
        String mensagem
) {
}
