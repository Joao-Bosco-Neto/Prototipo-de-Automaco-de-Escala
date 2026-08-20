package br.edu.sistemaescala.backend.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class EscalaTurno {

    private Integer id;
    private TipoTurno tipoTurno;
    private LocalDateTime inicio;
    private LocalDateTime fim;
    private int minAgentes = 2;
    private Integer maxAgentes;
    private String observacao;
    private boolean ativo = true;
    private LocalDateTime criadoEm;
    /** Agentes alocados no turno (com suas coberturas), hidratado por buscas que fazem JOIN para evitar N+1. */
    private List<EscalaFuncionario> agentes = new ArrayList<>();

    public EscalaTurno() {
    }

    public EscalaTurno(Integer id, TipoTurno tipoTurno, LocalDateTime inicio, LocalDateTime fim,
                        int minAgentes, Integer maxAgentes, String observacao, boolean ativo,
                        LocalDateTime criadoEm) {
        this.id = id;
        this.tipoTurno = tipoTurno;
        this.inicio = inicio;
        this.fim = fim;
        this.minAgentes = minAgentes;
        this.maxAgentes = maxAgentes;
        this.observacao = observacao;
        this.ativo = ativo;
        this.criadoEm = criadoEm;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer valor) {
        id = valor;
    }

    public TipoTurno getTipoTurno() {
        return tipoTurno;
    }

    public void setTipoTurno(TipoTurno valor) {
        tipoTurno = valor;
    }

    public LocalDateTime getInicio() {
        return inicio;
    }

    public void setInicio(LocalDateTime valor) {
        inicio = valor;
    }

    public LocalDateTime getFim() {
        return fim;
    }

    public void setFim(LocalDateTime valor) {
        fim = valor;
    }

    public int getMinAgentes() {
        return minAgentes;
    }

    public void setMinAgentes(int valor) {
        minAgentes = valor;
    }

    public Integer getMaxAgentes() {
        return maxAgentes;
    }

    public void setMaxAgentes(Integer valor) {
        maxAgentes = valor;
    }

    public String getObservacao() {
        return observacao;
    }

    public void setObservacao(String valor) {
        observacao = valor;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public void setAtivo(boolean valor) {
        ativo = valor;
    }

    public LocalDateTime getCriadoEm() {
        return criadoEm;
    }

    public void setCriadoEm(LocalDateTime valor) {
        criadoEm = valor;
    }

    public List<EscalaFuncionario> getAgentes() {
        return agentes;
    }

    public void setAgentes(List<EscalaFuncionario> valor) {
        agentes = valor;
    }

    @Override
    public boolean equals(Object objeto) {
        return this == objeto || objeto instanceof EscalaTurno outra && id != null && Objects.equals(id, outra.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "EscalaTurno{id=" + id + ", inicio=" + inicio + ", fim=" + fim + "}";
    }
}
