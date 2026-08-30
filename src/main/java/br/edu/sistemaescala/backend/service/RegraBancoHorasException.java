package br.edu.sistemaescala.backend.service;

/**
 * Exceção lançada quando uma regra de negócio do banco de horas é violada
 * (ex.: ajuste manual com horas inválidas ou sem descrição).
 */
public class RegraBancoHorasException extends RuntimeException {

    public RegraBancoHorasException(String mensagem) {
        super(mensagem);
    }
}
