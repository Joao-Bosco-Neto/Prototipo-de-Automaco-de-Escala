package br.edu.sistemaescala.backend.service;

/**
 * Excecao de regra de negocio ao autorizar uma excecao de escala — mesmo
 * desenho das demais {@code Regra*Exception} do projeto: mensagem pronta para
 * a tela, sem stack trace vazando para o usuario (issue #63).
 */
public class RegraEscalaExcecaoException extends RuntimeException {

    public RegraEscalaExcecaoException(String mensagem) {
        super(mensagem);
    }
}
