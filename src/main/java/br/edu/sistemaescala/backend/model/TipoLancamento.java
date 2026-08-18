package br.edu.sistemaescala.backend.model;

public enum TipoLancamento {
    CREDITO_COBERTURA("credito_cobertura"),
    DEBITO_AUSENCIA("debito_ausencia"),
    CREDITO_EXTRA("credito_extra"),
    AJUSTE_MANUAL("ajuste_manual");

    private final String valor;

    TipoLancamento(String valor) {
        this.valor = valor;
    }

    public String valor() {
        return valor;
    }

    @Override
    public String toString() {
        return valor;
    }

    public static TipoLancamento deValor(String valor) {
        for (TipoLancamento tipo : values()) {
            if (tipo.valor.equals(valor)) {
                return tipo;
            }
        }
        throw new IllegalArgumentException("Tipo de lancamento invalido: " + valor);
    }
}