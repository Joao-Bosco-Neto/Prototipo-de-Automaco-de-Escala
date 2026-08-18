package br.edu.sistemaescala;

import br.edu.sistemaescala.backend.dao.BancoInicializador;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Ponto de entrada da aplicacao Sistema de Escala.
 *
 * Hoje mostra apenas uma tela provisoria confirmando que a aplicacao sobe
 * e que o banco foi criado. Sera substituida pelo shell da aplicacao
 * (menu, navegacao lateral e area de conteudo) e pela tela de login.
 */
public class Main extends Application {

    @Override
    public void init() {
        // Cria as tabelas e a carga inicial antes de abrir a janela.
        BancoInicializador.inicializar();
    }

    @Override
    public void start(Stage palco) {
        Label titulo = new Label("Sistema de Escala");
        titulo.setStyle("-fx-font-size: 24px; -fx-font-weight: bold;");

        Label situacao = new Label("Banco de dados inicializado com sucesso.");
        Label proximo = new Label("Proximo passo: shell da aplicacao e tela de login.");
        proximo.setStyle("-fx-text-fill: #666;");

        VBox raiz = new VBox(12, titulo, situacao, proximo);
        raiz.setAlignment(Pos.CENTER);
        raiz.setPadding(new Insets(40));

        palco.setScene(new Scene(raiz, 1366, 768));
        palco.setTitle("Sistema de Escala");
        palco.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
