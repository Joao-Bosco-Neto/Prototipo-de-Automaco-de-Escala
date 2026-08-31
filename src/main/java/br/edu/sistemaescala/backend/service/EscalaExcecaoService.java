package br.edu.sistemaescala.backend.service;

import java.util.List;

import br.edu.sistemaescala.backend.model.EscalaExcecao;
import br.edu.sistemaescala.backend.model.EscalaFuncionario;
import br.edu.sistemaescala.backend.model.EscalaTurno;
import br.edu.sistemaescala.backend.model.Funcionario;

/**
 * Excecoes autorizadas as regras da escala (issue #64 / OWASP A09).
 *
 * <p>O protótipo define quais regras admitem excecao: "Sobreposicao de horario
 * e bloqueada; descanso minimo abaixo do exigido e registrado como excecao".
 * Duplicidade e sobreposicao continuam impedimento duro em
 * {@link RegraEscalaService#podeAlocar}; so o descanso insuficiente chega
 * aqui.</p>
 *
 * <p>Autorizar uma excecao produz dois registros: a linha em
 * {@code escala_excecao}, que e a trilha do <em>fato</em> e nao pode ser
 * editada depois, e o evento de seguranca em {@code log_seguranca}, que e a
 * trilha do <em>ato</em> — quem autorizou e quando.</p>
 */
public interface EscalaExcecaoService {

    /**
     * Aloca o funcionario no turno registrando a excecao ao descanso minimo.
     *
     * <p>A alocacao e a excecao entram na mesma transacao: uma escala com o
     * descanso violado e sem o registro da autorizacao seria pior do que nao
     * ter alocado.</p>
     *
     * @param turno       turno em que o funcionario sera alocado
     * @param funcionario quem sera alocado
     * @param descanso    veredito da regra, que vira a descricao da excecao
     * @return a alocacao criada, com o id preenchido
     * @throws RegraEscalaExcecaoException se o descanso na verdade estava
     *                                     respeitado (nao ha excecao a autorizar)
     *                                     ou se faltar dado obrigatorio
     */
    EscalaFuncionario alocarComExcecao(EscalaTurno turno, Funcionario funcionario, ResultadoDescanso descanso);

    /** Excecoes ja registradas para o funcionario, da mais recente para a mais antiga. */
    List<EscalaExcecao> listarPorFuncionario(int funcionarioId);
}
