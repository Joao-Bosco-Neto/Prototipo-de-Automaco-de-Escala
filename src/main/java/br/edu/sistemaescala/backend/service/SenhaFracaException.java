package br.edu.sistemaescala.backend.service;

public class SenhaFracaException extends RuntimeException {

    public SenhaFracaException() {
        super("A senha deve ter no minimo 8 caracteres");
    }
}