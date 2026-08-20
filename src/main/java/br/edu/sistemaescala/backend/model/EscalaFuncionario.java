package br.edu.sistemaescala.backend.model;

import java.time.LocalDateTime;
import java.util.Objects;

public class EscalaFuncionario {

    private Integer id;
    private EscalaTurno escalaTurno;
    private Funcionario funcionario;
    private LocalDateTime inicio;
    private LocalDateTime fim;
    private EscalaFuncionario coberturaDe;
    private Integer motivoCoberturaId;
    private String observacao;
    private boolean lancouBancoHoras;
    private LocalDateTime criadoEm;

    public EscalaFuncionario() {
    }

    public EscalaFuncionario(Integer id, EscalaTurno escalaTurno, Funcionario funcionario, LocalDateTime inicio,
                              LocalDateTime fim, EscalaFuncionario coberturaDe, Integer motivoCoberturaId,
                              String observacao, boolean lancouBancoHoras, LocalDateTime criadoEm) {
        this.id = id;
        this.escalaTurno = escalaTurno;
        this.funcionario = funcionario;
        this.inicio = inicio;
        this.fim = fim;
        this.coberturaDe = coberturaDe;
        this.motivoCoberturaId = motivoCoberturaId;
        this.observacao = observacao;
        this.lancouBancoHoras = lancouBancoHoras;
        this.criadoEm = criadoEm;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer valor) {
        id = valor;
    }

    public EscalaTurno getEscalaTurno() {
        return escalaTurno;
    }

    public void setEscalaTurno(EscalaTurno valor) {
        escalaTurno = valor;
    }

    public Funcionario getFuncionario() {
        return funcionario;
    }

    public void setFuncionario(Funcionario valor) {
        funcionario = valor;
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

    public EscalaFuncionario getCoberturaDe() {
        return coberturaDe;
    }

    public void setCoberturaDe(EscalaFuncionario valor) {
        coberturaDe = valor;
    }

    public Integer getMotivoCoberturaId() {
        return motivoCoberturaId;
    }

    public void setMotivoCoberturaId(Integer valor) {
        motivoCoberturaId = valor;
    }

    public String getObservacao() {
        return observacao;
    }

    public void setObservacao(String valor) {
        observacao = valor;
    }

    public boolean isLancouBancoHoras() {
        return lancouBancoHoras;
    }

    public void setLancouBancoHoras(boolean valor) {
        lancouBancoHoras = valor;
    }

    public LocalDateTime getCriadoEm() {
        return criadoEm;
    }

    public void setCriadoEm(LocalDateTime valor) {
        criadoEm = valor;
    }

    @Override
    public boolean equals(Object objeto) {
        return this == objeto || objeto instanceof EscalaFuncionario outra && id != null && Objects.equals(id, outra.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "EscalaFuncionario{id=" + id + ", funcionario=" + funcionario + ", escalaTurno=" + escalaTurno + "}";
    }
}
