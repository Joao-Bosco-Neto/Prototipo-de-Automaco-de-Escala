package br.edu.sistemaescala;

import br.edu.sistemaescala.backend.dao.BancoInicializador;
import br.edu.sistemaescala.backend.repository.ConfiguracaoRepository;
import br.edu.sistemaescala.backend.repository.UsuarioRepository;
import br.edu.sistemaescala.backend.repository.jdbc.ConfiguracaoRepositoryJdbc;
import br.edu.sistemaescala.backend.repository.jdbc.UsuarioRepositoryJdbc;
import br.edu.sistemaescala.backend.service.AutenticacaoService;
import br.edu.sistemaescala.backend.service.AutenticacaoServiceImpl;
import br.edu.sistemaescala.backend.service.PrimeiroAcessoService;
import br.edu.sistemaescala.backend.service.PrimeiroAcessoServiceImpl;
import br.edu.sistemaescala.frontend.controller.PrimeiroAcessoController;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
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
        UsuarioRepository usuarioRepository = new UsuarioRepositoryJdbc();
        ConfiguracaoRepository configuracaoRepository = new ConfiguracaoRepositoryJdbc();
        AutenticacaoService autenticacaoService = new AutenticacaoServiceImpl(usuarioRepository);
        PrimeiroAcessoService primeiroAcessoService = new PrimeiroAcessoServiceImpl(
                usuarioRepository, configuracaoRepository, autenticacaoService);

        Parent conteudo;
        if (primeiroAcessoService.primeiroAcesso()) {
            conteudo = new PrimeiroAcessoController(primeiroAcessoService).criarTela();
        } else {
            Label titulo = new Label("Sistema de Escala");
            titulo.setStyle("-fx-font-size: 24px; -fx-font-weight: bold;");
            Label situacao = new Label("Sistema pronto para uso.");
            VBox raizPronta = new VBox(12, titulo, situacao);
            raizPronta.setAlignment(Pos.CENTER);
            raizPronta.setPadding(new Insets(40));
            conteudo = raizPronta;
        }

        palco.setScene(new Scene(conteudo, 1366, 768));
        palco.setTitle("Sistema de Escala");
        palco.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
