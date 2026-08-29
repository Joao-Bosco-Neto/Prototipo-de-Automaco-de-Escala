package br.edu.sistemaescala.backend.service;

/**
 * Exceção lançada quando uma regra de negócio do registro de cobertura é
 * violada (ex.: substituto igual ao ausente, alocação ausente sem turno,
 * motivo inexistente).
 */
public class RegraCoberturaException extends RuntimeException {

    public RegraCoberturaException(String mensagem) {
        super(mensagem);
    }
}
