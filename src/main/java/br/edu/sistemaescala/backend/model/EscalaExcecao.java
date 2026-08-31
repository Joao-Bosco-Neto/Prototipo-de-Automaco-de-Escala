package br.edu.sistemaescala.backend.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Excecao autorizada a uma regra da escala ({@code escala_excecao}).
 *
 * <p>E trilha de auditoria, nao cadastro: registra que um gestor alocou
 * alguem sabendo que a regra nao fechava. Uma vez criada, nao se edita — o
 * {@code EscalaExcecaoRepository} bloqueia a atualizacao.</p>
 *
 * <p>{@code funcionarioId} e {@code dataPlantao} sao denormalizados de
 * proposito: quando a alocacao some (remocao do agente, "Limpar mes"), a
 * {@code escalaFuncionarioId} vira nula pela FK e sao esses dois campos que
 * mantem o registro legivel.</p>
 */
public class EscalaExcecao {

    private Integer id;
    private Integer escalaFuncionarioId;
    private Integer funcionarioId;
    private LocalDate dataPlantao;
    private RegraExcecao regra;
    private String descricao;
    private String autorizadoPor;
    private LocalDateTime criadoEm;

    public EscalaExcecao() {
    }

    public EscalaExcecao(Integer id, Integer escalaFuncionarioId, Integer funcionarioId,
                         LocalDate dataPlantao, RegraExcecao regra, String descricao,
                         String autorizadoPor, LocalDateTime criadoEm) {
        this.id = id;
        this.escalaFuncionarioId = escalaFuncionarioId;
        this.funcionarioId = funcionarioId;
        this.dataPlantao = dataPlantao;
        this.regra = regra;
        this.descricao = descricao;
        this.autorizadoPor = autorizadoPor;
        this.criadoEm = criadoEm;
    }

    public Integer getId() { return id; }
    public void setId(Integer valor) { id = valor; }
    public Integer getEscalaFuncionarioId() { return escalaFuncionarioId; }
    public void setEscalaFuncionarioId(Integer valor) { escalaFuncionarioId = valor; }
    public Integer getFuncionarioId() { return funcionarioId; }
    public void setFuncionarioId(Integer valor) { funcionarioId = valor; }
    public LocalDate getDataPlantao() { return dataPlantao; }
    public void setDataPlantao(LocalDate valor) { dataPlantao = valor; }
    public RegraExcecao getRegra() { return regra; }
    public void setRegra(RegraExcecao valor) { regra = valor; }
    public String getDescricao() { return descricao; }
    public void setDescricao(String valor) { descricao = valor; }
    public String getAutorizadoPor() { return autorizadoPor; }
    public void setAutorizadoPor(String valor) { autorizadoPor = valor; }
    public LocalDateTime getCriadoEm() { return criadoEm; }
    public void setCriadoEm(LocalDateTime valor) { criadoEm = valor; }

    @Override
    public boolean equals(Object objeto) {
        return this == objeto || objeto instanceof EscalaExcecao outro
                && id != null && Objects.equals(id, outro.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }

    @Override
    public String toString() {
        return "EscalaExcecao{id=" + id + ", funcionarioId=" + funcionarioId
                + ", dataPlantao=" + dataPlantao + ", regra=" + regra
                + ", autorizadoPor='" + autorizadoPor + "'}";
    }
}
