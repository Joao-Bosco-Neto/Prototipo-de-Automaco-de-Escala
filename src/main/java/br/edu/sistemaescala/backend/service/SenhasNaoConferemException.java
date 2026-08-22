package br.edu.sistemaescala.backend.service;

public class SenhasNaoConferemException extends RuntimeException {

    public SenhasNaoConferemException() {
        super("As senhas nao conferem");
    }
}