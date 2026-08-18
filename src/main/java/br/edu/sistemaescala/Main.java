package br.edu.sistemaescala;

import javafx.application.Application;
import javafx.stage.Stage;

/**
 * Ponto de entrada da aplicacao Sistema de Escala.
 *
 * TODO: carregar a tela inicial (login/gestor) via FXMLLoader,
 * apontando para src/main/resources/frontend/fxml/*.fxml
 */
public class Main extends Application {

    @Override
    public void start(Stage primaryStage) {
        // Exemplo de carregamento de tela FXML (ajustar quando as telas existirem):
        //
        // FXMLLoader loader = new FXMLLoader(getClass().getResource("/frontend/fxml/login.fxml"));
        // Scene scene = new Scene(loader.load(), 1366, 768);
        // primaryStage.setScene(scene);
        // primaryStage.setTitle("Sistema de Escala");
        // primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
