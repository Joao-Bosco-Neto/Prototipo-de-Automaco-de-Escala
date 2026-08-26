package br.edu.sistemaescala;

import br.edu.sistemaescala.backend.dao.BancoInicializador;
import br.edu.sistemaescala.backend.repository.ConfiguracaoRepository;
import br.edu.sistemaescala.backend.repository.FuncionarioRepository;
import br.edu.sistemaescala.backend.repository.TipoTurnoRepository;
import br.edu.sistemaescala.backend.repository.UsuarioRepository;
import br.edu.sistemaescala.backend.repository.jdbc.ConfiguracaoRepositoryJdbc;
import br.edu.sistemaescala.backend.repository.jdbc.FuncionarioRepositoryJdbc;
import br.edu.sistemaescala.backend.repository.jdbc.TipoTurnoRepositoryJdbc;
import br.edu.sistemaescala.backend.repository.jdbc.UsuarioRepositoryJdbc;
import br.edu.sistemaescala.backend.service.AutenticacaoService;
import br.edu.sistemaescala.backend.service.AutenticacaoServiceImpl;
import br.edu.sistemaescala.backend.service.BloqueioInatividadeService;
import br.edu.sistemaescala.backend.service.BloqueioInatividadeServiceImpl;
import br.edu.sistemaescala.backend.service.FuncionarioService;
import br.edu.sistemaescala.backend.service.FuncionarioServiceImpl;
import br.edu.sistemaescala.backend.service.GestaoUsuariosService;
import br.edu.sistemaescala.backend.service.GestaoUsuariosServiceImpl;
import br.edu.sistemaescala.backend.service.PrimeiroAcessoService;
import br.edu.sistemaescala.backend.service.PrimeiroAcessoServiceImpl;
import br.edu.sistemaescala.backend.service.SessaoUsuario;
import br.edu.sistemaescala.frontend.TratadorErroGlobal;
import br.edu.sistemaescala.frontend.controller.LoginController;
import br.edu.sistemaescala.frontend.controller.PrimeiroAcessoController;
import br.edu.sistemaescala.frontend.controller.ShellController;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Ponto de entrada da aplicação Sistema de Escala.
 *
 * No primeiro acesso abre a tela de configuração inicial; nas demais
 * execuções abre a tela de login e, após autenticado, o shell principal.
 */
public class Main extends Application {

    /**
     * Falha ocorrida no init(). Guardada em vez de propagada porque uma
     * excecao saindo do init() derruba a aplicacao antes de existir janela
     * para avisar o usuario — o start() abaixo transforma isso em tela e
     * dialogo de erro.
     */
    private Throwable falhaNaPartida;

    @Override
    public void init() {
        // Cria as tabelas e a carga inicial antes de abrir a janela.
        try {
            BancoInicializador.inicializar();
        } catch (RuntimeException excecao) {
            falhaNaPartida = excecao;
        }
    }

    @Override
    public void start(Stage palco) {
        // A JavaFX Application Thread e criada pelo framework e nao usa o
        // handler padrao registrado no main(); por isso o registro aqui.
        TratadorErroGlobal.instalarNaThreadAtual();

        if (falhaNaPartida != null) {
            abrirTelaFalhaPartida(palco, falhaNaPartida);
            return;
        }

        UsuarioRepository usuarioRepository = new UsuarioRepositoryJdbc();
        FuncionarioRepository funcionarioRepository = new FuncionarioRepositoryJdbc();
        ConfiguracaoRepository configuracaoRepository = new ConfiguracaoRepositoryJdbc();
        TipoTurnoRepository tipoTurnoRepository = new TipoTurnoRepositoryJdbc();
        AutenticacaoService autenticacaoService = new AutenticacaoServiceImpl(usuarioRepository);
        PrimeiroAcessoService primeiroAcessoService = new PrimeiroAcessoServiceImpl(
                usuarioRepository, configuracaoRepository, autenticacaoService);
        SessaoUsuario sessaoUsuario = new SessaoUsuario();

        Parent conteudo;
        if (primeiroAcessoService.primeiroAcesso()) {
            conteudo = new PrimeiroAcessoController(primeiroAcessoService).criarTela();
        } else {
            conteudo = criarTelaLogin(palco, configuracaoRepository, tipoTurnoRepository,
                    usuarioRepository, funcionarioRepository, autenticacaoService, sessaoUsuario);
        }

        Scene cena = new Scene(conteudo, 1366, 768);
        cena.getStylesheets().add(getClass().getResource("/frontend/css/app.css").toExternalForm());
        palco.setScene(cena);
        palco.setTitle("Sistema de Escala");
        palco.show();
    }

    private void abrirJanelaPrincipal(Stage palco,
                                      ConfiguracaoRepository configuracaoRepository,
                                      TipoTurnoRepository tipoTurnoRepository,
                                      UsuarioRepository usuarioRepository,
                                      FuncionarioRepository funcionarioRepository,
                                      SessaoUsuario sessaoUsuario,
                                      AutenticacaoService autenticacaoService) {
        GestaoUsuariosService gestaoUsuariosService = new GestaoUsuariosServiceImpl(
                usuarioRepository, autenticacaoService, sessaoUsuario);
        FuncionarioService funcionarioService = new FuncionarioServiceImpl(funcionarioRepository);
        BloqueioInatividadeService bloqueioInatividadeService =
                new BloqueioInatividadeServiceImpl(sessaoUsuario, autenticacaoService);

        palco.getScene().setRoot(new ShellController(
                configuracaoRepository,
                tipoTurnoRepository,
                gestaoUsuariosService,
                funcionarioService,
                sessaoUsuario,
                bloqueioInatividadeService,
                () -> palco.getScene().setRoot(
                        criarTelaLogin(palco, configuracaoRepository, tipoTurnoRepository,
                                usuarioRepository, funcionarioRepository, autenticacaoService, sessaoUsuario)))
                .criarTela());
    }

    private Parent criarTelaLogin(Stage palco,
                                  ConfiguracaoRepository configuracaoRepository,
                                  TipoTurnoRepository tipoTurnoRepository,
                                  UsuarioRepository usuarioRepository,
                                  FuncionarioRepository funcionarioRepository,
                                  AutenticacaoService autenticacaoService,
                                  SessaoUsuario sessaoUsuario) {
        String nomeOrganizacao = configuracaoRepository.buscar()
                .map(configuracao -> configuracao.getNomeOrganizacao())
                .orElse("Organização não configurada");

        return new LoginController(autenticacaoService, usuario -> {
            sessaoUsuario.iniciar(usuario);
            abrirJanelaPrincipal(palco, configuracaoRepository, tipoTurnoRepository,
                    usuarioRepository, funcionarioRepository, sessaoUsuario, autenticacaoService);
        }).criarTela(nomeOrganizacao);
    }

    /**
     * Tela minima exibida quando o banco nao subiu: informa o usuario, dá o
     * caminho do log e deixa a janela aberta ate ele mesmo fechar.
     */
    private void abrirTelaFalhaPartida(Stage palco, Throwable falha) {
        Label titulo = new Label("Não foi possível iniciar o sistema");
        titulo.getStyleClass().add("titulo-2");
        Label descricao = new Label(
                "O banco de dados local não pôde ser aberto. Verifique se você tem "
                + "permissão de escrita na pasta do sistema e tente novamente.");
        descricao.getStyleClass().add("texto-secundario");
        descricao.setWrapText(true);
        Label caminhoLog = new Label("Detalhes técnicos foram gravados em:\n"
                + LogAplicacao.arquivoLog());
        caminhoLog.getStyleClass().add("texto-secundario");
        caminhoLog.setWrapText(true);
        Button fechar = new Button("Fechar o sistema");
        fechar.getStyleClass().add("button-primario");
        fechar.setOnAction(evento -> Platform.exit());

        VBox cartao = new VBox(14, titulo, descricao, caminhoLog, fechar);
        cartao.getStyleClass().add("card");
        cartao.setPadding(new Insets(28));
        cartao.setMaxWidth(520);
        cartao.setMaxHeight(Region.USE_PREF_SIZE);
        cartao.setAlignment(Pos.CENTER_LEFT);

        StackPane raiz = new StackPane(cartao);
        raiz.setAlignment(Pos.CENTER);
        raiz.setPadding(new Insets(40));

        Scene cena = new Scene(raiz, 1366, 768);
        cena.getStylesheets().add(getClass().getResource("/frontend/css/app.css").toExternalForm());
        palco.setScene(cena);
        palco.setTitle("Sistema de Escala");
        palco.show();

        // Depois da janela aberta, para o dialogo aparecer sobre ela.
        Platform.runLater(() -> TratadorErroGlobal.tratar(Thread.currentThread(), falha));
    }

    public static void main(String[] args) {
        // Antes do launch(): cobre a thread do launcher (onde roda o init())
        // e qualquer thread criada depois pela aplicacao.
        TratadorErroGlobal.instalarGlobal();
        launch(args);
    }
}
