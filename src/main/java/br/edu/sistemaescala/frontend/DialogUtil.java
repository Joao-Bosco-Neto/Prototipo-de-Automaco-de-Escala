package br.edu.sistemaescala.frontend;

import java.net.URL;
import java.util.Optional;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;

/**
 * Dialogos padrao do sistema (erro, informacao e confirmacao).
 *
 * Existe por dois motivos: evitar que cada controller monte o proprio
 * Alert na mao e, principalmente, garantir que o app.css seja aplicado ao
 * DialogPane. Um Alert criado sem o tema renderiza com a aparencia cinza
 * padrao do JavaFX, destoando do resto do sistema.
 */
public final class DialogUtil {

    private static final String TITULO_JANELA = "Sistema de Escala";
    private static final String CAMINHO_TEMA = "/frontend/css/app.css";

    /** Mensagem orientativa de banco fora do ar: sem detalhe tecnico de SQL. */
    private static final String MENSAGEM_BANCO_INDISPONIVEL =
            "Não foi possível acessar o banco de dados. Verifique se você tem "
            + "permissão de escrita na pasta do sistema e tente novamente.\n\n"
            + "Se o problema continuar, procure o administrador do sistema.";

    private DialogUtil() {
        // classe utilitaria: nao deve ser instanciada
    }

    public static void mostrarErro(String titulo, String mensagem) {
        montar(Alert.AlertType.ERROR, titulo, mensagem).showAndWait();
    }

    public static void mostrarInformacao(String titulo, String mensagem) {
        montar(Alert.AlertType.INFORMATION, titulo, mensagem).showAndWait();
    }

    /** Exibe a confirmacao e devolve true somente se o usuario confirmar. */
    public static boolean mostrarConfirmacao(String titulo, String mensagem) {
        Optional<ButtonType> escolha =
                montar(Alert.AlertType.CONFIRMATION, titulo, mensagem).showAndWait();
        return escolha.isPresent() && escolha.get() == ButtonType.OK;
    }

    /**
     * Erro de acesso ao banco (RepositoryException e afins), com texto
     * orientando o usuario sobre o que fazer. O detalhe tecnico da falha
     * vai para o log, nunca para a tela.
     */
    public static void mostrarErroBancoIndisponivel() {
        mostrarErro("Banco de dados indisponível", MENSAGEM_BANCO_INDISPONIVEL);
    }

    /** Sobrecarga para quando o chamador sabe dar um titulo mais especifico. */
    public static void mostrarErroBancoIndisponivel(String titulo) {
        mostrarErro(titulo, MENSAGEM_BANCO_INDISPONIVEL);
    }

    /**
     * Aplica o tema a um DialogPane qualquer. Publico porque telas que
     * montam Dialog proprio (e nao um Alert) precisam da mesma aparencia.
     */
    public static void aplicarTema(DialogPane painel) {
        URL tema = DialogUtil.class.getResource(CAMINHO_TEMA);
        if (tema == null) {
            // Sem o tema o dialogo fica feio, mas ainda abre e informa o usuario.
            return;
        }
        String folha = tema.toExternalForm();
        if (!painel.getStylesheets().contains(folha)) {
            painel.getStylesheets().add(folha);
        }
    }

    private static Alert montar(Alert.AlertType tipo, String titulo, String mensagem) {
        Alert alerta = new Alert(tipo);
        alerta.setTitle(TITULO_JANELA);
        alerta.setHeaderText(titulo);
        alerta.setContentText(mensagem);
        alerta.getDialogPane().setMinHeight(DialogPane.USE_PREF_SIZE);
        aplicarTema(alerta.getDialogPane());
        return alerta;
    }
}
