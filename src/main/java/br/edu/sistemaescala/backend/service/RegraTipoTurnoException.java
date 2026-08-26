package br.edu.sistemaescala.backend.service;

/**
 * Exceção lançada quando uma regra de negócio de tipos de turno é violada.
 */
public class RegraTipoTurnoException extends RuntimeException {

    public RegraTipoTurnoException(String mensagem) {
        super(mensagem);
    }
}
