package br.edu.sistemaescala.backend.model;

/**
 * Acao critica registrada na trilha de auditoria (OWASP A09).
 *
 * <p>Os valores gravados em {@code log_seguranca.acao} sao os do campo
 * {@code valor}, nao o nome da constante: renomear a constante nao pode
 * mudar o que ja esta gravado no banco.</p>
 */
public enum AcaoSeguranca {

    /** Tentativa de autenticacao, bem-sucedida ou nao. */
    LOGIN("login"),

    /** Encerramento de sessao pelo proprio usuario. */
    LOGOUT("logout"),

    /** Cadastro de uma nova conta de acesso ao sistema. */
    USUARIO_CRIADO("usuario_criado"),

    /** Conta de acesso desativada (o sistema nao exclui usuario). */
    USUARIO_DESATIVADO("usuario_desativado"),

    /** Conta de acesso reativada. */
    USUARIO_REATIVADO("usuario_reativado"),

    /** Redefinicao de senha feita pelo administrador. */
    SENHA_REDEFINIDA("senha_redefinida"),

    /** Excecao a uma regra da escala autorizada por um gestor. */
    ESCALA_EXCECAO_AUTORIZADA("escala_excecao_autorizada"),

    /** Limpeza da escala de um mes inteiro (exclusao em massa de turnos). */
    ESCALA_MES_LIMPA("escala_mes_limpa"),

    /** Exclusao de uma cobertura de plantao ja registrada. */
    COBERTURA_EXCLUIDA("cobertura_excluida");

    private final String valor;

    AcaoSeguranca(String valor) {
        this.valor = valor;
    }

    public String valor() {
        return valor;
    }

    @Override
    public String toString() {
        return valor;
    }

    public static AcaoSeguranca deValor(String valor) {
        for (AcaoSeguranca acao : values()) {
            if (acao.valor.equals(valor)) {
                return acao;
            }
        }
        throw new IllegalArgumentException("Acao de seguranca invalida: " + valor);
    }
}
