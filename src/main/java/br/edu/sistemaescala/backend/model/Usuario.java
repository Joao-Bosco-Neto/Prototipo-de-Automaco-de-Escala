package br.edu.sistemaescala.backend.model;

import java.time.LocalDateTime;
import java.util.Objects;

public class Usuario {

    private Integer id;
    private String nome;
    private String login;
    private String senhaHash;
    private RoleUsuario role = RoleUsuario.GESTOR;
    private boolean ativo = true;
    private LocalDateTime ultimoLogin;
    private LocalDateTime criadoEm;

    public Usuario() {
    }

    public Usuario(Integer id, String nome, String login, String senhaHash, RoleUsuario role, boolean ativo,
                    LocalDateTime ultimoLogin, LocalDateTime criadoEm) {
        this.id = id;
        this.nome = nome;
        this.login = login;
        this.senhaHash = senhaHash;
        this.role = role;
        this.ativo = ativo;
        this.ultimoLogin = ultimoLogin;
        this.criadoEm = criadoEm;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer valor) {
        id = valor;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String valor) {
        nome = valor;
    }

    public String getLogin() {
        return login;
    }

    public void setLogin(String valor) {
        login = valor;
    }

    public String getSenhaHash() {
        return senhaHash;
    }

    public void setSenhaHash(String valor) {
        senhaHash = valor;
    }

    public RoleUsuario getRole() {
        return role;
    }

    public void setRole(RoleUsuario valor) {
        role = valor;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public void setAtivo(boolean valor) {
        ativo = valor;
    }

    public LocalDateTime getUltimoLogin() {
        return ultimoLogin;
    }

    public void setUltimoLogin(LocalDateTime valor) {
        ultimoLogin = valor;
    }

    public LocalDateTime getCriadoEm() {
        return criadoEm;
    }

    public void setCriadoEm(LocalDateTime valor) {
        criadoEm = valor;
    }

    @Override
    public boolean equals(Object objeto) {
        return this == objeto || objeto instanceof Usuario outra && id != null && Objects.equals(id, outra.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Usuario{id=" + id + ", nome='" + nome + "', login='" + login + "', role=" + role + ", ativo=" + ativo + "}";
    }
}
