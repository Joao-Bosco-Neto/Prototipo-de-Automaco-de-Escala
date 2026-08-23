package br.edu.sistemaescala;

import br.edu.sistemaescala.backend.dao.BancoInicializador;
import br.edu.sistemaescala.backend.repository.ConfiguracaoRepository;
import br.edu.sistemaescala.backend.repository.TipoTurnoRepository;
import br.edu.sistemaescala.backend.repository.UsuarioRepository;
import br.edu.sistemaescala.backend.repository.jdbc.ConfiguracaoRepositoryJdbc;
import br.edu.sistemaescala.backend.repository.jdbc.TipoTurnoRepositoryJdbc;
import br.edu.sistemaescala.backend.repository.jdbc.UsuarioRepositoryJdbc;
import br.edu.sistemaescala.backend.service.AutenticacaoService;
import br.edu.sistemaescala.backend.service.AutenticacaoServiceImpl;
import br.edu.sistemaescala.backend.service.PrimeiroAcessoService;
import br.edu.sistemaescala.backend.service.PrimeiroAcessoServiceImpl;
import br.edu.sistemaescala.frontend.controller.PrimeiroAcessoController;
import br.edu.sistemaescala.frontend.controller.ShellController;
import javafx.application.Application;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Ponto de entrada da aplicacao Sistema de Escala.
 *
 * No primeiro acesso abre a tela de configuracao inicial; nas demais
 * execucoes abre o shell da aplicacao (menu, navegacao lateral e area de
 * conteudo). A tela de login entra aqui quando a issue #15 for feita.
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
        TipoTurnoRepository tipoTurnoRepository = new TipoTurnoRepositoryJdbc();
        AutenticacaoService autenticacaoService = new AutenticacaoServiceImpl(usuarioRepository);
        PrimeiroAcessoService primeiroAcessoService = new PrimeiroAcessoServiceImpl(
                usuarioRepository, configuracaoRepository, autenticacaoService);

        Parent conteudo;
        if (primeiroAcessoService.primeiroAcesso()) {
            conteudo = new PrimeiroAcessoController(primeiroAcessoService).criarTela();
        } else {
            conteudo = new ShellController(configuracaoRepository, tipoTurnoRepository).criarTela();
        }

        Scene cena = new Scene(conteudo, 1366, 768);
        cena.getStylesheets().add(getClass().getResource("/frontend/css/app.css").toExternalForm());
        palco.setScene(cena);
        palco.setTitle("Sistema de Escala");
        palco.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
