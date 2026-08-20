package br.edu.sistemaescala.backend.service;

/**
 * Lancada quando a senha atual informada em alterarSenha nao confere com
 * o hash gravado. A mensagem nunca inclui a senha em texto puro.
 */
public class SenhaInvalidaException extends RuntimeException {

    public SenhaInvalidaException() {
        super("Senha atual invalida");
    }
}
