package br.edu.sistemaescala.backend.service;

/**
 * Exceção lançada quando uma regra de negócio relacionada a funcionários é violada
 * (ex.: campos obrigatórios ausentes, duplicidade de matrícula, etc.).
 */
public class RegraFuncionarioException extends RuntimeException {

    public RegraFuncionarioException(String mensagem) {
        super(mensagem);
    }
}
