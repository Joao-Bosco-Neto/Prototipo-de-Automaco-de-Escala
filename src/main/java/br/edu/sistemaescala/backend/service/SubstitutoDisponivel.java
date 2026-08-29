package br.edu.sistemaescala.backend.service;

import br.edu.sistemaescala.backend.model.Funcionario;

/**
 * Um candidato a substituto de um plantão, já com o selo de disponibilidade
 * resolvido para a tela de registro de cobertura (issue #33).
 *
 * <p>Segue o mesmo desenho de {@link ResultadoAlocacao} e
 * {@link ResultadoDescanso}: o service só informa, com a mensagem pronta para
 * exibição, e quem decide o que fazer é a camada de cima. Aqui nada bloqueia —
 * qualquer funcionário pode cobrir qualquer outro; quem está em descanso mínimo
 * ou com turno sobreposto apenas aparece marcado.</p>
 *
 * @param funcionario o candidato
 * @param disponivel  false quando ele tem sobreposição de horário ou descanso
 *                    insuficiente para assumir o turno
 * @param restricao   texto do selo: "Disponível" quando livre, ou o motivo da
 *                    restrição encontrada
 */
public record SubstitutoDisponivel(
        Funcionario funcionario,
        boolean disponivel,
        String restricao
) {
}
