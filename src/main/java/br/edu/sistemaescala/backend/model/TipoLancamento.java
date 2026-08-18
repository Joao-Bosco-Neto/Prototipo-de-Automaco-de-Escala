package br.edu.sistemaescala.backend.model;

/**
 * Motivo de um lancamento no banco de horas.
 *
 * Os valores em banco (valor) precisam bater exatamente com o CHECK da
 * coluna lancamento_horas.tipo no schema.sql: 'credito_cobertura',
 * 'debito_ausencia', 'credito_extra', 'ajuste_manual'.
 */
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
