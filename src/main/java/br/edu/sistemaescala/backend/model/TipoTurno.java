package br.edu.sistemaescala.backend.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Objects;

public class TipoTurno {

    private Integer id;
    private String nome;
    private LocalTime horaInicio;
    private BigDecimal duracaoHoras;
    private BigDecimal intervaloDescansoHoras = BigDecimal.ZERO;
    private int minAgentes = 2;
    private Integer maxAgentes;
    private boolean contaBancoHoras = true;
    private boolean ativo = true;
    private LocalDateTime criadoEm;

    public TipoTurno() {
    }

    public TipoTurno(Integer id, String nome, LocalTime horaInicio, BigDecimal duracaoHoras,
                      BigDecimal intervaloDescansoHoras, int minAgentes, Integer maxAgentes,
                      boolean contaBancoHoras, boolean ativo, LocalDateTime criadoEm) {
        this.id = id;
        this.nome = nome;
        this.horaInicio = horaInicio;
        this.duracaoHoras = duracaoHoras;
        this.intervaloDescansoHoras = intervaloDescansoHoras;
        this.minAgentes = minAgentes;
        this.maxAgentes = maxAgentes;
        this.contaBancoHoras = contaBancoHoras;
        this.ativo = ativo;
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

    public LocalTime getHoraInicio() {
        return horaInicio;
    }

    public void setHoraInicio(LocalTime valor) {
        horaInicio = valor;
    }

    public BigDecimal getDuracaoHoras() {
        return duracaoHoras;
    }

    public void setDuracaoHoras(BigDecimal valor) {
        duracaoHoras = valor;
    }

    public BigDecimal getIntervaloDescansoHoras() {
        return intervaloDescansoHoras;
    }

    public void setIntervaloDescansoHoras(BigDecimal valor) {
        intervaloDescansoHoras = valor;
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

    public boolean isContaBancoHoras() {
        return contaBancoHoras;
    }

    public void setContaBancoHoras(boolean valor) {
        contaBancoHoras = valor;
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

    @Override
    public boolean equals(Object objeto) {
        return this == objeto || objeto instanceof TipoTurno outra && id != null && Objects.equals(id, outra.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "TipoTurno{id=" + id + ", nome='" + nome + "', horaInicio=" + horaInicio + "}";
    }
}
