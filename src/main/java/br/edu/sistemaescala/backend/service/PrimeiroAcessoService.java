package br.edu.sistemaescala.backend.service;

public interface PrimeiroAcessoService {

    boolean primeiroAcesso();

    void configurar(String nomeOrganizacao, String login, String senha, String confirmacaoSenha);
}