package br.edu.sistemaescala.backend.model;

import java.time.LocalDateTime;
import java.util.Objects;

public class Funcionario {
    private Integer id;
    private String nome;
    private String matricula;
    private String telefone;
    private String observacoes;
    private boolean ativo = true;
    private LocalDateTime criadoEm;

    public Funcionario() { }
    public Funcionario(Integer id, String nome, String matricula, String telefone, String observacoes,
                       boolean ativo, LocalDateTime criadoEm) {
        this.id = id; this.nome = nome; this.matricula = matricula; this.telefone = telefone;
        this.observacoes = observacoes; this.ativo = ativo; this.criadoEm = criadoEm;
    }
    public Integer getId() { return id; }
    public void setId(Integer valor) { id = valor; }
    public String getNome() { return nome; }
    public void setNome(String valor) { nome = valor; }
    public String getMatricula() { return matricula; }
    public void setMatricula(String valor) { matricula = valor; }
    public String getTelefone() { return telefone; }
    public void setTelefone(String valor) { telefone = valor; }
    public String getObservacoes() { return observacoes; }
    public void setObservacoes(String valor) { observacoes = valor; }
    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean valor) { ativo = valor; }
    public LocalDateTime getCriadoEm() { return criadoEm; }
    public void setCriadoEm(LocalDateTime valor) { criadoEm = valor; }
    @Override public boolean equals(Object objeto) { return this == objeto || objeto instanceof Funcionario outra && id != null && Objects.equals(id, outra.id); }
    @Override public int hashCode() { return Objects.hash(id); }
    @Override public String toString() { return "Funcionario{id=" + id + ", nome='" + nome + "', matricula='" + matricula + "', ativo=" + ativo + "}"; }
}