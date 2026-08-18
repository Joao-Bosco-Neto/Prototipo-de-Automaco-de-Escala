package br.edu.sistemaescala.backend.model;

/**
 * Motivo de um lancamento no banco de horas.
 *
 * Os valores em banco (valorBanco) precisam bater exatamente com o
 * CHECK da coluna lancamento_horas.tipo no schema.sql:
 * 'credito_cobertura', 'debito_ausencia', 'credito_extra', 'ajuste_manual'.
 */
public enum TipoLancamento {

    CREDITO_COBERTURA("credito_cobertura"),
    DEBITO_AUSENCIA("debito_ausencia"),
    CREDITO_EXTRA("credito_extra"),
    AJUSTE_MANUAL("ajuste_manual");

    private final String valorBanco;

    TipoLancamento(String valorBanco) {
        this.valorBanco = valorBanco;
    }

    public String getValorBanco() {
        return valorBanco;
    }

    public static TipoLancamento fromValorBanco(String valorBanco) {
        for (TipoLancamento tipo : values()) {
            if (tipo.valorBanco.equals(valorBanco)) {
                return tipo;
            }
        }
        throw new IllegalArgumentException("Tipo de lancamento desconhecido: " + valorBanco);
    }
}
