package br.edu.sistemaescala.backend.repository;

import java.sql.Connection;
import java.util.List;

import br.edu.sistemaescala.backend.model.EscalaExcecao;

/**
 * Persistencia das excecoes autorizadas as regras da escala.
 *
 * <p>A tabela {@code escala_excecao} e uma trilha de auditoria, e e tratada
 * como tal: a linha entra e nunca muda. Nao ha {@code remover}, e
 * {@link #atualizar} existe apenas para fechar a porta de forma explicita —
 * qualquer chamada estoura {@link UnsupportedOperationException}.</p>
 *
 * <p>O bloqueio mora aqui, em Java, e nao numa trigger do banco: a decisao do
 * projeto e manter toda regra no backend.</p>
 */
public interface EscalaExcecaoRepository {

    /** Grava a excecao abrindo a propria conexao. */
    EscalaExcecao inserir(EscalaExcecao excecao);

    /**
     * Grava a excecao na conexao de uma transacao em curso — o caso normal,
     * porque a excecao e a alocacao que a motivou precisam entrar juntas.
     *
     * <p>A conexao vem de fora e nao e fechada aqui: quem abriu decide a hora
     * do commit, do rollback e do close.</p>
     */
    EscalaExcecao inserir(EscalaExcecao excecao, Connection conexao);

    /** Excecoes ja registradas para o funcionario, da mais recente para a mais antiga. */
    List<EscalaExcecao> listarPorFuncionario(int funcionarioId);

    /**
     * Nunca atualiza: sempre lanca {@link UnsupportedOperationException}.
     *
     * <p>O metodo existe de proposito, como {@code default} da interface, para
     * que nenhuma implementacao precise (nem consiga, sem sobrescrever de
     * forma explicita) abrir um caminho de UPDATE. Uma trilha de auditoria que
     * aceita edicao nao e trilha de auditoria.</p>
     *
     * @throws UnsupportedOperationException sempre
     */
    default EscalaExcecao atualizar(EscalaExcecao excecao) {
        throw new UnsupportedOperationException("A edição de exceções de escala é bloqueada");
    }
}
