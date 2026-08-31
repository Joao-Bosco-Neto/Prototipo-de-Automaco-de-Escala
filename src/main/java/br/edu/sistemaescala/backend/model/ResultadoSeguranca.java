package br.edu.sistemaescala.backend.model;

/**
 * Desfecho de uma {@link AcaoSeguranca}, gravado em
 * {@code log_seguranca.resultado}.
 *
 * <p>Registrar so o sucesso e o erro que o OWASP A09 cita textualmente: a
 * falha e justamente o evento que denuncia ataque em andamento.</p>
 */
public enum ResultadoSeguranca {

    SUCESSO("sucesso"),
    FALHA("falha");

    private final String valor;

    ResultadoSeguranca(String valor) {
        this.valor = valor;
    }

    public String valor() {
        return valor;
    }

    @Override
    public String toString() {
        return valor;
    }

    public static ResultadoSeguranca deValor(String valor) {
        for (ResultadoSeguranca resultado : values()) {
            if (resultado.valor.equals(valor)) {
                return resultado;
            }
        }
        throw new IllegalArgumentException("Resultado de seguranca invalido: " + valor);
    }
}
