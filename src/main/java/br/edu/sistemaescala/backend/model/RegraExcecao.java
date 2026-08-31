package br.edu.sistemaescala.backend.model;

/**
 * Regra da escala que uma excecao autorizada dispensou.
 *
 * <p>Hoje so o descanso minimo entra aqui, e nao por falta de tempo: e a
 * unica regra que o protótipo manda tratar como excecao. Duplicidade e
 * sobreposicao de horario continuam bloqueio duro — "Sobreposicao de horario
 * e bloqueada; descanso minimo abaixo do exigido e registrado como
 * excecao".</p>
 *
 * <p>Como em {@link TipoLancamento}, o texto gravado e o do campo
 * {@code valor}: renomear a constante nao pode mudar o que ja esta no
 * banco.</p>
 */
public enum RegraExcecao {

    /** Intervalo de descanso obrigatorio entre plantoes (issue #40). */
    DESCANSO_MINIMO("descanso_minimo");

    private final String valor;

    RegraExcecao(String valor) {
        this.valor = valor;
    }

    public String valor() {
        return valor;
    }

    @Override
    public String toString() {
        return valor;
    }

    public static RegraExcecao deValor(String valor) {
        for (RegraExcecao regra : values()) {
            if (regra.valor.equals(valor)) {
                return regra;
            }
        }
        throw new IllegalArgumentException("Regra de excecao invalida: " + valor);
    }
}
