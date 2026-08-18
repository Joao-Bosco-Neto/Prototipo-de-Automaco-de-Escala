package br.edu.sistemaescala.backend.model;


import java.util.Objects;

// * não há equipe no schema, não há necessidade de implementar

public class Equipe {
    private Integer id;
    private String nome;
    private boolean ativo = true;

    public Equipe() {
    }

    public Equipe(Integer id, String nome, boolean ativo) {
        this.id = id;
        this.nome = nome;
        this.ativo = ativo;
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }
    @Override public boolean equals(Object objeto) { return this == objeto || objeto instanceof Equipe outra && id != null && Objects.equals(id, outra.id); }
    @Override public int hashCode() { return Objects.hash(id); }
    @Override public String toString() { return "Equipe{id=" + id + ", nome='" + nome + "', ativo=" + ativo + "}"; }
}