package br.edu.sistemaescala.frontend;

import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Janela standalone para conferencia visual dos componentes do tema
 * (app.css), sem precisar navegar o fluxo real do sistema.
 */
public class VitrineComponentesApp extends Application {

    @Override
    public void start(Stage palco) {
        VBox raiz = new VBox(28,
                secaoCabecalho(),
                secaoBotoes(),
                secaoCampos(),
                secaoCard(),
                secaoTabela(),
                secaoSelos());
        raiz.setPadding(new Insets(32));

        ScrollPane scroll = new ScrollPane(raiz);
        scroll.setFitToWidth(true);

        Scene cena = new Scene(scroll, 900, 800);
        cena.getStylesheets().add(getClass().getResource("/frontend/css/app.css").toExternalForm());

        palco.setScene(cena);
        palco.setTitle("Vitrine de Componentes — Sistema de Escala");
        palco.show();
    }

    private VBox secaoCabecalho() {
        Label titulo = new Label("Sistema de Escala");
        titulo.getStyleClass().add("titulo-1");
        Label subtitulo = new Label("Vitrine de componentes visuais");
        subtitulo.getStyleClass().add("titulo-2");
        return new VBox(8, titulo, subtitulo);
    }

    private VBox secaoBotoes() {
        Label rotulo = new Label("Botões");
        rotulo.getStyleClass().add("titulo-2");

        Button primario = new Button("Primário");
        primario.getStyleClass().add("button-primario");

        Button secundario = new Button("Secundário");
        secundario.getStyleClass().add("button-secundario");

        Button perigo = new Button("Perigo");
        perigo.getStyleClass().add("button-perigo");

        Button desabilitado = new Button("Desabilitado");
        desabilitado.getStyleClass().add("button-primario");
        desabilitado.setDisable(true);

        HBox linha = new HBox(12, primario, secundario, perigo, desabilitado);
        linha.setAlignment(Pos.CENTER_LEFT);
        return new VBox(12, rotulo, linha);
    }

    private VBox secaoCampos() {
        Label rotulo = new Label("Campos de formulário");
        rotulo.getStyleClass().add("titulo-2");

        TextField campoTexto = new TextField();
        campoTexto.setPromptText("Campo de texto");

        PasswordField campoSenha = new PasswordField();
        campoSenha.setPromptText("Senha");

        TextField campoComErro = new TextField();
        campoComErro.setPromptText("Campo com erro");
        campoComErro.getStyleClass().add("campo-com-erro");

        HBox linha = new HBox(12, campoTexto, campoSenha, campoComErro);
        linha.setAlignment(Pos.CENTER_LEFT);
        return new VBox(12, rotulo, linha);
    }

    private VBox secaoCard() {
        Label rotulo = new Label("Card");
        rotulo.getStyleClass().add("titulo-2");

        Label tituloCard = new Label("Título do card");
        tituloCard.getStyleClass().add("titulo-2");
        Label textoCard = new Label("Conteúdo de exemplo dentro de um card, com sombra e borda arredondada.");
        textoCard.getStyleClass().add("texto-secundario");
        textoCard.setWrapText(true);

        VBox card = new VBox(8, tituloCard, textoCard);
        card.getStyleClass().add("card");
        card.setMaxWidth(400);

        return new VBox(12, rotulo, card);
    }

    private VBox secaoTabela() {
        Label rotulo = new Label("Tabela");
        rotulo.getStyleClass().add("titulo-2");

        TableView<LinhaExemplo> tabela = new TableView<>();
        TableColumn<LinhaExemplo, String> colunaNome = new TableColumn<>("Nome");
        colunaNome.setCellValueFactory(dado -> dado.getValue().nomeProperty());
        TableColumn<LinhaExemplo, String> colunaFuncao = new TableColumn<>("Função");
        colunaFuncao.setCellValueFactory(dado -> dado.getValue().funcaoProperty());
        TableColumn<LinhaExemplo, String> colunaStatus = new TableColumn<>("Status");
        colunaStatus.setCellValueFactory(dado -> dado.getValue().statusProperty());

        tabela.getColumns().add(colunaNome);
        tabela.getColumns().add(colunaFuncao);
        tabela.getColumns().add(colunaStatus);
        tabela.setItems(FXCollections.observableArrayList(
                new LinhaExemplo("Ana Souza", "Enfermeira", "Ativo"),
                new LinhaExemplo("Bruno Lima", "Técnico", "Ativo"),
                new LinhaExemplo("Carla Dias", "Enfermeira", "Inativo")));
        tabela.setPrefHeight(140);

        return new VBox(12, rotulo, tabela);
    }

    private VBox secaoSelos() {
        Label rotulo = new Label("Selos de status");
        rotulo.getStyleClass().add("titulo-2");

        Label sucesso = new Label("Sucesso");
        sucesso.getStyleClass().addAll("selo", "selo-sucesso");
        Label atencao = new Label("Atenção");
        atencao.getStyleClass().addAll("selo", "selo-atencao");
        Label perigo = new Label("Perigo");
        perigo.getStyleClass().addAll("selo", "selo-perigo");
        Label neutro = new Label("Neutro");
        neutro.getStyleClass().addAll("selo", "selo-neutro");

        HBox linha = new HBox(12, sucesso, atencao, perigo, neutro);
        linha.setAlignment(Pos.CENTER_LEFT);
        return new VBox(12, rotulo, linha);
    }

    public static void main(String[] args) {
        launch(args);
    }

    /** Linha fake apenas para popular a tabela de demonstração. */
    private static final class LinhaExemplo {
        private final javafx.beans.property.SimpleStringProperty nome;
        private final javafx.beans.property.SimpleStringProperty funcao;
        private final javafx.beans.property.SimpleStringProperty status;

        LinhaExemplo(String nome, String funcao, String status) {
            this.nome = new javafx.beans.property.SimpleStringProperty(nome);
            this.funcao = new javafx.beans.property.SimpleStringProperty(funcao);
            this.status = new javafx.beans.property.SimpleStringProperty(status);
        }

        javafx.beans.property.SimpleStringProperty nomeProperty() {
            return nome;
        }

        javafx.beans.property.SimpleStringProperty funcaoProperty() {
            return funcao;
        }

        javafx.beans.property.SimpleStringProperty statusProperty() {
            return status;
        }
    }
}
