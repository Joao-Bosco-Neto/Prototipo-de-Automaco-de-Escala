package br.edu.sistemaescala.backend.service;

import java.time.LocalDate;
import java.util.List;

import br.edu.sistemaescala.backend.model.EscalaFuncionario;
import br.edu.sistemaescala.backend.model.Funcionario;
import br.edu.sistemaescala.backend.model.MotivoCobertura;

/**
 * Regras de negócio do registro de cobertura de plantão (issue #33 / Backlog #33,
 * "conforme a tela 4").
 *
 * <p>Uma cobertura é quando um agente assume o turno de outro. No banco ela é
 * uma linha em {@code escala_funcionario} com {@code cobertura_de} apontando
 * para a alocação do ausente; se o gestor pedir, gera também um crédito no
 * banco de horas para quem cobre e um débito para o ausente, no valor da
 * duração do turno.</p>
 *
 * <p>O controller da tela só trata evento visual: montar a data, recarregar
 * combos e chamar {@link #registrar}. Toda a lógica — filtrar quem está
 * escalado na data, resolver o selo de disponibilidade do substituto e gravar a
 * cobertura com os lançamentos na mesma transação — vive aqui.</p>
 */
public interface CoberturaService {

    /**
     * Agentes escalados na data informada, para o combo "Funcionário ausente".
     *
     * <p>Traz só os titulares (alocações sem {@code cobertura_de}); uma cobertura
     * já registrada não pode ser coberta de novo. Cada item vem com o
     * {@code escalaTurno} (e o tipo de turno) e o {@code funcionario} já
     * hidratados, para a tela mostrar o nome com a indicação do turno.</p>
     */
    List<EscalaFuncionario> listarEscaladosNaData(LocalDate data);

    /** Motivos de cobertura ativos, para o combo "Motivo" (carregados da tabela {@code motivo_cobertura}). */
    List<MotivoCobertura> listarMotivos();

    /**
     * Candidatos a substituto do plantão da {@code alocacaoAusente} informada,
     * cada um com o selo de disponibilidade (sobreposição de horário / descanso
     * obrigatório) já resolvido pelas regras da escala.
     *
     * <p>Quem já está no próprio turno fica de fora da lista. Ninguém é
     * bloqueado: um candidato com restrição apenas volta com
     * {@code disponivel = false}.</p>
     */
    List<SubstitutoDisponivel> listarSubstitutos(EscalaFuncionario alocacaoAusente);

    /**
     * Registra a cobertura: insere a alocação do substituto (com
     * {@code cobertura_de}, {@code motivo_cobertura_id}, {@code observacao} e
     * {@code lancou_banco_horas}) e, quando {@code lancarBancoHoras} é true, o
     * crédito de quem cobre e o débito do ausente em {@code lancamento_horas} —
     * tudo dentro de uma única transação.
     *
     * @param alocacaoAusente alocação do titular ausente (item de
     *                        {@link #listarEscaladosNaData})
     * @param substituto      funcionário que vai cobrir (de
     *                        {@link #listarSubstitutos})
     * @param motivoCoberturaId id do motivo escolhido, ou {@code null}
     * @param observacao      texto livre, ou {@code null}
     * @param lancarBancoHoras se deve gerar o crédito e o débito
     * @return a alocação de cobertura criada, com o id preenchido
     * @throws RegraCoberturaException se os dados forem inconsistentes
     */
    EscalaFuncionario registrar(EscalaFuncionario alocacaoAusente,
                                Funcionario substituto,
                                Integer motivoCoberturaId,
                                String observacao,
                                boolean lancarBancoHoras);

    /**
     * Edita uma cobertura já registrada. Atualiza a alocação de cobertura
     * ({@code coberturaExistente}, identificada pelo id) com o turno, o
     * substituto, o motivo, a observação e o {@code lancou_banco_horas} novos e,
     * no extrato, apaga o par crédito/débito anterior recriando-o do zero
     * quando {@code lancarBancoHoras} continua true — sem cálculo de delta,
     * tudo dentro de uma única transação.
     *
     * <p>As regras de duplicidade/sobreposição/descanso só são reavaliadas
     * quando o substituto muda; refiná-las para troca de turno é assunto da
     * issue #35.</p>
     *
     * @param coberturaExistente alocação de cobertura a alterar (id preenchido)
     * @param alocacaoAusente    alocação do titular ausente, que define o turno
     * @param substituto         funcionário que vai cobrir
     * @param motivoCoberturaId  id do motivo escolhido, ou {@code null}
     * @param observacao         texto livre, ou {@code null}
     * @param lancarBancoHoras   se deve manter o crédito e o débito
     * @return a alocação de cobertura atualizada
     * @throws RegraCoberturaException se os dados forem inconsistentes
     */
    EscalaFuncionario editar(EscalaFuncionario coberturaExistente,
                             EscalaFuncionario alocacaoAusente,
                             Funcionario substituto,
                             Integer motivoCoberturaId,
                             String observacao,
                             boolean lancarBancoHoras);

    /**
     * Exclui uma cobertura registrada. Remove a alocação de cobertura; o par
     * crédito/débito vinculado sai junto pela cascata do schema
     * ({@code lancamento_horas.escala_funcionario_id ON DELETE CASCADE}),
     * dentro de uma única transação.
     *
     * @param cobertura alocação de cobertura a excluir (id preenchido)
     * @throws RegraCoberturaException se a cobertura não tiver id
     */
    void excluir(EscalaFuncionario cobertura);
}
