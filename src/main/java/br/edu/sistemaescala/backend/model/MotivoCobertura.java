package br.edu.sistemaescala.backend.model;

import java.util.Objects;

public class MotivoCobertura {
    private Integer id;
    private String nome;
    private boolean geraLancamento = true;
    private boolean ativo = true;

    public MotivoCobertura() {
    }

    public MotivoCobertura(Integer id, String nome, boolean geraLancamento, boolean ativo) {
        this.id = id;
        this.nome = nome;
        this.geraLancamento = geraLancamento;
        this.ativo = ativo;
    }

    public Integer getId() { return id; }
    public void setId(Integer valor) { id = valor; }
    public String getNome() { return nome; }
    public void setNome(String valor) { nome = valor; }
    public boolean isGeraLancamento() { return geraLancamento; }
    public void setGeraLancamento(boolean valor) { geraLancamento = valor; }
    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean valor) { ativo = valor; }

    @Override
    public boolean equals(Object objeto) {
        return this == objeto || objeto instanceof MotivoCobertura outro
                && id != null && Objects.equals(id, outro.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }

    @Override
    public String toString() {
        return "MotivoCobertura{id=" + id + ", nome='" + nome + "', ativo=" + ativo + "}";
    }
}