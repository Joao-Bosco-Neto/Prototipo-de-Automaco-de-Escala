package br.edu.sistemaescala.backend.model;

public enum MotivoCobertura {
    LICENCA_MEDICA("Licenca medica"),
    CURSO_CAPACITACAO("Curso / capacitacao"),
    CONVOCACAO_JUDICIAL("Convocacao judicial"),
    MOTIVO_PARTICULAR("Motivo particular");

    private final String valor;

    MotivoCobertura(String valor) {
        this.valor = valor;
    }

    public String valor() {
        return valor;
    }

    @Override
    public String toString() {
        return valor;
    }

    public static MotivoCobertura deValor(String valor) {
        for (MotivoCobertura motivo : values()) {
            if (motivo.valor.equals(valor)) {
                return motivo;
            }
        }
        throw new IllegalArgumentException("Motivo de cobertura invalido: " + valor);
    }
}