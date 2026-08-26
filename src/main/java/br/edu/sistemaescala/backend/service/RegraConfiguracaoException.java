package br.edu.sistemaescala.backend.service;

/**
 * Exceção lançada quando regras de validação ou de negócio da configuração são violadas.
 */
public class RegraConfiguracaoException extends RuntimeException {

    public RegraConfiguracaoException(String mensagem) {
        super(mensagem);
    }
}

