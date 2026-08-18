package br.edu.sistemaescala.backend.repository;

/**
 * Erro de acesso a dados, sem checked exception vazando para quem usa os
 * repositorios. As implementacoes JDBC capturam SQLException e relancam
 * como RepositoryException.
 */
public class RepositoryException extends RuntimeException {

    public RepositoryException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
