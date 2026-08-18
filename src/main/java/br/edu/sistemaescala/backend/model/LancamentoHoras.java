package br.edu.sistemaescala.backend.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

public class LancamentoHoras {
    private Integer id;
    private Funcionario funcionario;
    private EscalaFuncionario escalaFuncionario;
/**
 * Um lancamento no extrato do banco de horas de um funcionario.
 *
 * Guardado em minutos (nao em horas fracionadas) para nao perder precisao
 * em turnos como 8h30 — ver comentario da tabela em schema.sql.
 * minutos positivo = credito, minutos negativo = debito.
 *
 * escalaFuncionarioId e nulo quando o lancamento nao veio de uma cobertura
 * de plantao (ex.: ajuste manual).
 */
public class LancamentoHoras {

    private Integer id;
    private int funcionarioId;
    private Integer escalaFuncionarioId;
    private LocalDate dataReferencia;
    private int minutos;
    private TipoLancamento tipo;
    private String descricao;
    private LocalDateTime criadoEm;

    public LancamentoHoras() { }
    public LancamentoHoras(Integer id, Funcionario funcionario, EscalaFuncionario escalaFuncionario,
                           LocalDate dataReferencia, int minutos, TipoLancamento tipo, String descricao,
                           LocalDateTime criadoEm) {
        this.id = id; this.funcionario = funcionario; this.escalaFuncionario = escalaFuncionario;
        this.dataReferencia = dataReferencia; this.minutos = minutos; this.tipo = tipo;
        this.descricao = descricao; this.criadoEm = criadoEm;
    }
    public Integer getId() { return id; }
    public void setId(Integer valor) { id = valor; }
    public Funcionario getFuncionario() { return funcionario; }
    public void setFuncionario(Funcionario valor) { funcionario = valor; }
    public EscalaFuncionario getEscalaFuncionario() { return escalaFuncionario; }
    public void setEscalaFuncionario(EscalaFuncionario valor) { escalaFuncionario = valor; }
    public LocalDate getDataReferencia() { return dataReferencia; }
    public void setDataReferencia(LocalDate valor) { dataReferencia = valor; }
    public int getMinutos() { return minutos; }
    public void setMinutos(int valor) { minutos = valor; }
    public TipoLancamento getTipo() { return tipo; }
    public void setTipo(TipoLancamento valor) { tipo = valor; }
    public String getDescricao() { return descricao; }
    public void setDescricao(String valor) { descricao = valor; }
    public LocalDateTime getCriadoEm() { return criadoEm; }
    public void setCriadoEm(LocalDateTime valor) { criadoEm = valor; }
    @Override public boolean equals(Object objeto) { return this == objeto || objeto instanceof LancamentoHoras outra && id != null && Objects.equals(id, outra.id); }
    @Override public int hashCode() { return Objects.hash(id); }
    @Override public String toString() { return "LancamentoHoras{id=" + id + ", funcionario=" + funcionario + ", dataReferencia=" + dataReferencia + ", minutos=" + minutos + ", tipo=" + tipo + "}"; }
}
    public LancamentoHoras() {
    }

    public LancamentoHoras(int funcionarioId, Integer escalaFuncionarioId, LocalDate dataReferencia,
                            int minutos, TipoLancamento tipo, String descricao) {
        this.funcionarioId = funcionarioId;
        this.escalaFuncionarioId = escalaFuncionarioId;
        this.dataReferencia = dataReferencia;
        this.minutos = minutos;
        this.tipo = tipo;
        this.descricao = descricao;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public int getFuncionarioId() {
        return funcionarioId;
    }

    public void setFuncionarioId(int funcionarioId) {
        this.funcionarioId = funcionarioId;
    }

    public Integer getEscalaFuncionarioId() {
        return escalaFuncionarioId;
    }

    public void setEscalaFuncionarioId(Integer escalaFuncionarioId) {
        this.escalaFuncionarioId = escalaFuncionarioId;
    }

    public LocalDate getDataReferencia() {
        return dataReferencia;
    }

    public void setDataReferencia(LocalDate dataReferencia) {
        this.dataReferencia = dataReferencia;
    }

    public int getMinutos() {
        return minutos;
    }

    public void setMinutos(int minutos) {
        this.minutos = minutos;
    }

    public TipoLancamento getTipo() {
        return tipo;
    }

    public void setTipo(TipoLancamento tipo) {
        this.tipo = tipo;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public LocalDateTime getCriadoEm() {
        return criadoEm;
    }

    public void setCriadoEm(LocalDateTime criadoEm) {
        this.criadoEm = criadoEm;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LancamentoHoras)) return false;
        LancamentoHoras outro = (LancamentoHoras) o;
        return Objects.equals(id, outro.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "LancamentoHoras{" +
                "id=" + id +
                ", funcionarioId=" + funcionarioId +
                ", escalaFuncionarioId=" + escalaFuncionarioId +
                ", dataReferencia=" + dataReferencia +
                ", minutos=" + minutos +
                ", tipo=" + tipo +
                ", descricao='" + descricao + '\'' +
                '}';
    }
}
