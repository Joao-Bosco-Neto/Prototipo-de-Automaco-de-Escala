package br.edu.sistemaescala.backend.service;

import java.time.LocalDate;

import br.edu.sistemaescala.backend.model.EscalaFuncionario;

/**
 * Linha da listagem mensal da tela de Coberturas.
 *
 * <p>Tudo o que a tabela mostra já vem resolvido na consulta — nome do ausente,
 * nome de quem cobriu, descrição do motivo e a duração do turno —, para o
 * controller não precisar de nenhuma ida extra ao banco por linha.</p>
 *
 * <p>{@code cobertura} carrega a entidade que a consulta hidratou: é ela que
 * {@link CoberturaService#editar} e {@link CoberturaService#excluir} recebem.
 * Vem com o substituto e o ausente (nome e matrícula) e o turno (início e fim)
 * preenchidos, porque a auditoria da exclusão descreve a cobertura pela
 * matrícula dos envolvidos e pela data do plantão — dados que não existem mais
 * depois do DELETE.</p>
 *
 * @param coberturaId      id da alocação de cobertura ({@code escala_funcionario.id})
 * @param dataPlantao      dia em que o turno coberto começa
 * @param nomeSubstituto   quem assumiu o plantão
 * @param nomeAusente      titular que foi coberto
 * @param motivoDescricao  nome do motivo de cobertura, ou {@code null} quando não houver
 * @param lancouBancoHoras se a cobertura gerou o par crédito/débito no extrato
 * @param minutosDoTurno   duração do turno coberto, base do valor lançado
 * @param cobertura        a alocação de cobertura hidratada, para editar e excluir
 */
public record CoberturaListagemItem(
        int coberturaId,
        LocalDate dataPlantao,
        String nomeSubstituto,
        String nomeAusente,
        String motivoDescricao,
        boolean lancouBancoHoras,
        int minutosDoTurno,
        EscalaFuncionario cobertura) {
}
