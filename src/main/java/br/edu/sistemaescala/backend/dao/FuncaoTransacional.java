package br.edu.sistemaescala.backend.dao;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Bloco de trabalho executado dentro de uma transacao pelo
 * {@link TransacaoUtil}.
 *
 * Recebe a Connection da transacao e devolve o resultado da operacao. A
 * Connection e emprestada: quem implementa esta interface NAO deve fazer
 * commit, rollback nem close — isso e responsabilidade do TransacaoUtil.
 *
 * @param <T> tipo devolvido pela operacao (use Void e retorne null quando
 *            a operacao nao produzir resultado)
 */
@FunctionalInterface
public interface FuncaoTransacional<T> {

    /**
     * Executa o trabalho usando a conexao da transacao.
     *
     * Declara SQLException para permitir JDBC direto no bloco; os
     * repositorios continuam lancando RepositoryException, que tambem
     * dispara o rollback por ser RuntimeException.
     */
    T aplicar(Connection conexao) throws SQLException;
}
