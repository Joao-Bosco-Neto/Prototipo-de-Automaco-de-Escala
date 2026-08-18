package br.edu.sistemaescala.backend.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

public class Configuracao {
    private Integer id;
    private String nomeOrganizacao;
    private String subtitulo;
    private BigDecimal cargaHorariaMensal;
    private String apuracaoBancoHoras;
    private String caminhoPdfPadrao;
    private LocalDateTime atualizadoEm;

    public Configuracao() {
    }

    public Configuracao(Integer id, String nomeOrganizacao, String subtitulo, BigDecimal cargaHorariaMensal,
                        String apuracaoBancoHoras, String caminhoPdfPadrao, LocalDateTime atualizadoEm) {
        this.id = id;
        this.nomeOrganizacao = nomeOrganizacao;
        this.subtitulo = subtitulo;
        this.cargaHorariaMensal = cargaHorariaMensal;
        this.apuracaoBancoHoras = apuracaoBancoHoras;
        this.caminhoPdfPadrao = caminhoPdfPadrao;
        this.atualizadoEm = atualizadoEm;
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getNomeOrganizacao() { return nomeOrganizacao; }
    public void setNomeOrganizacao(String valor) { nomeOrganizacao = valor; }
    public String getSubtitulo() { return subtitulo; }
    public void setSubtitulo(String valor) { subtitulo = valor; }
    public BigDecimal getCargaHorariaMensal() { return cargaHorariaMensal; }
    public void setCargaHorariaMensal(BigDecimal valor) { cargaHorariaMensal = valor; }
    public String getApuracaoBancoHoras() { return apuracaoBancoHoras; }
    public void setApuracaoBancoHoras(String valor) { apuracaoBancoHoras = valor; }
    public String getCaminhoPdfPadrao() { return caminhoPdfPadrao; }
    public void setCaminhoPdfPadrao(String valor) { caminhoPdfPadrao = valor; }
    public LocalDateTime getAtualizadoEm() { return atualizadoEm; }
    public void setAtualizadoEm(LocalDateTime valor) { atualizadoEm = valor; }

    @Override public boolean equals(Object objeto) {
        if (this == objeto) return true;
        if (!(objeto instanceof Configuracao outra)) return false;
        return id != null && Objects.equals(id, outra.id);
    }
    @Override public int hashCode() { return Objects.hash(id); }
    @Override public String toString() { return "Configuracao{id=" + id + ", nomeOrganizacao='" + nomeOrganizacao + "'}"; }
}