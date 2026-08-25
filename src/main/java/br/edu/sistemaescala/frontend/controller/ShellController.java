package br.edu.sistemaescala.frontend.controller;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import br.edu.sistemaescala.backend.dao.ConexaoBanco;
import br.edu.sistemaescala.backend.model.Configuracao;
import br.edu.sistemaescala.backend.model.RoleUsuario;
import br.edu.sistemaescala.backend.model.Usuario;
import br.edu.sistemaescala.backend.repository.ConfiguracaoRepository;
import br.edu.sistemaescala.backend.repository.RepositoryException;
import br.edu.sistemaescala.backend.repository.TipoTurnoRepository;
import br.edu.sistemaescala.backend.repository.jdbc.UsuarioRepositoryJdbc;
import br.edu.sistemaescala.backend.service.AutenticacaoServiceImpl;
import br.edu.sistemaescala.backend.service.AutorizacaoService;
import br.edu.sistemaescala.backend.service.BloqueioInatividadeService;
import br.edu.sistemaescala.backend.service.BloqueioInatividadeServiceImpl;
import br.edu.sistemaescala.backend.service.GestaoUsuariosService;
import br.edu.sistemaescala.backend.service.GestaoUsuariosServiceImpl;
import br.edu.sistemaescala.backend.service.SessaoUsuario;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.input.InputEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * Shell da aplicacao (issue #38 / #61): barra de titulo, menu superior, navegacao
 * lateral, area central de conteudo, barra de status e bloqueio por inatividade (OWASP A07).
 *
 * A tela e montada em Java puro, como o resto do frontend — o projeto nao usa
 * FXML em lugar nenhum. O tema (app.css) e aplicado na Scene criada pelo
 * Main, entao aqui basta usar as classes de estilo via getStyleClass().
 *
 * O trabalho nao salvo e preservado através de um StackPane onde o overlay de
 * bloqueio e exibido por cima da arvore de componentes sem reiniciar o estado.
 */
public class ShellController {

    /** Itens da navegacao lateral, na ordem do prototipo visual. */
    private static final List<String> ITENS_NAVEGACAO = List.of(
            "Visão geral",
            "Funcionários",
            "Montagem da escala",
            "Coberturas",
            "Exportar PDF",
            "Banco de horas");

    /** Menus do topo, na ordem do prototipo visual. Sem submenus ainda. */
    private static final List<String> MENUS_SUPERIORES = List.of(
            "Arquivo", "Cadastros", "Escala", "Relatórios", "Ajuda");

    private static final String CLASSE_ITEM_ATIVO = "nav-item-ativo";

    private final ConfiguracaoRepository configuracaoRepository;
    private final TipoTurnoRepository tipoTurnoRepository;
    private final GestaoUsuariosService gestaoUsuariosService;
    private final SessaoUsuario sessaoUsuario;
    private final BloqueioInatividadeService bloqueioService;
    private final AutorizacaoService autorizacaoService;
    private final Runnable aoSair;

    private final BorderPane raiz = new BorderPane();
    private final List<Button> botoesNavegacao = new ArrayList<>();
    private Timeline temporizadorInatividade;

    public ShellController(ConfiguracaoRepository configuracaoRepository,
                           TipoTurnoRepository tipoTurnoRepository) {
        this(configuracaoRepository, tipoTurnoRepository, new SessaoUsuario(), Platform::exit);
    }

    public ShellController(ConfiguracaoRepository configuracaoRepository,
                           TipoTurnoRepository tipoTurnoRepository,
                           SessaoUsuario sessaoUsuario, Runnable aoSair) {
        this(configuracaoRepository, tipoTurnoRepository,
                new GestaoUsuariosServiceImpl(
                        new UsuarioRepositoryJdbc(),
                        new AutenticacaoServiceImpl(new UsuarioRepositoryJdbc()),
                        sessaoUsuario),
                sessaoUsuario, aoSair);
    }

    public ShellController(ConfiguracaoRepository configuracaoRepository,
                           TipoTurnoRepository tipoTurnoRepository,
                           GestaoUsuariosService gestaoUsuariosService,
                           SessaoUsuario sessaoUsuario, Runnable aoSair) {
        this(configuracaoRepository, tipoTurnoRepository, gestaoUsuariosService, sessaoUsuario,
                new BloqueioInatividadeServiceImpl(sessaoUsuario, new AutenticacaoServiceImpl(new UsuarioRepositoryJdbc())),
                aoSair);
    }

    public ShellController(ConfiguracaoRepository configuracaoRepository,
                           TipoTurnoRepository tipoTurnoRepository,
                           GestaoUsuariosService gestaoUsuariosService,
                           SessaoUsuario sessaoUsuario,
                           BloqueioInatividadeService bloqueioService,
                           Runnable aoSair) {
        this.configuracaoRepository = configuracaoRepository;
        this.tipoTurnoRepository = tipoTurnoRepository;
        this.gestaoUsuariosService = gestaoUsuariosService;
        this.sessaoUsuario = sessaoUsuario;
        this.bloqueioService = bloqueioService;
        this.autorizacaoService = new AutorizacaoService();
        this.aoSair = aoSair;
    }

    public Parent criarTela() {
        raiz.setTop(new VBox(criarBarraTitulo(), criarMenuSuperior()));
        raiz.setLeft(criarNavegacaoLateral());
        raiz.setBottom(criarBarraStatus());

        // "Visão geral" e o item selecionado ao abrir o shell.
        selecionar(botoesNavegacao.get(0), ITENS_NAVEGACAO.get(0));

        // Cria o painel de sobreposição de bloqueio por inatividade
        BloqueioController bloqueioController = new BloqueioController(
                bloqueioService,
                sessaoUsuario,
                this::executarSaida,
                this::ocultarBloqueio
        );
        StackPane painelBloqueio = bloqueioController.criarPainelBloqueio();

        StackPane containerPrincipal = new StackPane(raiz, painelBloqueio);

        // Notificação de estado do serviço de bloqueio
        bloqueioService.adicionarOuvinteBloqueio(bloqueado -> {
            Platform.runLater(() -> {
                if (bloqueado) {
                    painelBloqueio.setVisible(true);
                    painelBloqueio.setManaged(true);
                    bloqueioController.prepararExibicao();
                } else {
                    painelBloqueio.setVisible(false);
                    painelBloqueio.setManaged(false);
                }
            });
        });

        // Captura qualquer interação do usuário para renovar o marco de atividade
        containerPrincipal.addEventFilter(InputEvent.ANY, evento -> {
            if (!bloqueioService.estaBloqueado()) {
                bloqueioService.registrarAtividade();
            }
        });

        iniciarMonitorInatividade();

        return containerPrincipal;
    }

    private void iniciarMonitorInatividade() {
        pararMonitorInatividade();
        temporizadorInatividade = new Timeline(new KeyFrame(Duration.seconds(1), evento -> {
            bloqueioService.verificarInatividade();
        }));
        temporizadorInatividade.setCycleCount(Animation.INDEFINITE);
        temporizadorInatividade.play();
    }

    private void pararMonitorInatividade() {
        if (temporizadorInatividade != null) {
            temporizadorInatividade.stop();
            temporizadorInatividade = null;
        }
    }

    private void ocultarBloqueio() {
        // Ao desbloquear com sucesso, retoma o monitor e foca o trabalho
        iniciarMonitorInatividade();
    }

    private void executarSaida() {
        pararMonitorInatividade();
        sessaoUsuario.encerrar();
        aoSair.run();
    }

    // -----------------------------------------------------------------
    // Barra de titulo
    // -----------------------------------------------------------------

    private HBox criarBarraTitulo() {
        Label organizacao = new Label(nomeOrganizacao());
        organizacao.getStyleClass().add("titulo-organizacao");

        Region espacador = new Region();
        HBox.setHgrow(espacador, Priority.ALWAYS);

        Usuario usuario = sessaoUsuario.exigirUsuario();
        Label identidade = new Label(usuario.getNome() + " (" + usuario.getRole() + ")");
        identidade.getStyleClass().add("texto-secundario");

        Button botaoBloquear = new Button("Bloquear");
        botaoBloquear.getStyleClass().add("button-secundario-claro");
        botaoBloquear.setOnAction(evento -> bloqueioService.bloquear());

        Button sair = new Button("Sair");
        sair.getStyleClass().add("button-secundario-claro");
        sair.setOnAction(evento -> executarSaida());

        HBox barra = new HBox(12, organizacao, identidade, espacador, botaoBloquear, sair);
        barra.setAlignment(Pos.CENTER_LEFT);
        barra.getStyleClass().add("barra-titulo");
        return barra;
    }

    private String nomeOrganizacao() {
        try {
            return configuracaoRepository.buscar()
                    .map(Configuracao::getNomeOrganizacao)
                    .orElse("Organização não configurada");
        } catch (RepositoryException excecao) {
            return "Organização não configurada";
        }
    }

    // -----------------------------------------------------------------
    // Menu superior
    // -----------------------------------------------------------------

    private MenuBar criarMenuSuperior() {
        MenuBar menuBar = new MenuBar();
        // Menus vazios de proposito: os itens de submenu chegam junto com a
        // issue de cada tela, para nao criar acao que ainda nao existe.
        MENUS_SUPERIORES.forEach(nome -> menuBar.getMenus().add(new Menu(nome)));
        if (sessaoUsuario.usuarioAtual().map(usuario -> usuario.getRole() == RoleUsuario.ADMIN).orElse(false)) {
            Menu gestaoUsuarios = new Menu("Gestão de usuários");
            MenuItem abrirGestao = new MenuItem("Administrar usuários");
            abrirGestao.setOnAction(evento -> {
                botoesNavegacao.forEach(botao -> botao.getStyleClass().remove(CLASSE_ITEM_ATIVO));
                raiz.setCenter(criarConteudo("Gestão de usuários"));
            });
            gestaoUsuarios.getItems().add(abrirGestao);
            menuBar.getMenus().add(gestaoUsuarios);
        }
        return menuBar;
    }

    // -----------------------------------------------------------------
    // Navegacao lateral
    // -----------------------------------------------------------------

    private VBox criarNavegacaoLateral() {
        VBox navegacao = new VBox(8);
        navegacao.getStyleClass().add("navegacao-lateral");
        navegacao.setPrefWidth(220);

        for (String item : ITENS_NAVEGACAO) {
            Button botao = new Button(item);
            botao.getStyleClass().add("button-secundario");
            botao.setMaxWidth(Double.MAX_VALUE);
            botao.setAlignment(Pos.CENTER_LEFT);
            botao.setOnAction(evento -> selecionar(botao, item));
            botoesNavegacao.add(botao);
            navegacao.getChildren().add(botao);
        }
        return navegacao;
    }

    /** Marca o item clicado como ativo e troca o conteudo da area central. */
    private void selecionar(Button botaoSelecionado, String item) {
        botoesNavegacao.forEach(botao -> botao.getStyleClass().remove(CLASSE_ITEM_ATIVO));
        botaoSelecionado.getStyleClass().add(CLASSE_ITEM_ATIVO);
        raiz.setCenter(criarConteudo(item));
    }

    /**
     * Placeholder da area central. Cada item da navegacao ganha sua tela real
     * na issue especifica correspondente (funcionarios, escala, coberturas...).
     */
    private Parent criarConteudo(String item) {
        if ("Gestão de usuários".equals(item)) {
            autorizacaoService.exigirAdministrador(sessaoUsuario);
            return new GestaoUsuariosController(gestaoUsuariosService).criarTela();
        }
        Label placeholder = new Label("Tela de " + item + " — em construção");
        placeholder.getStyleClass().add("titulo-2");

        StackPane area = new StackPane(placeholder);
        area.setPadding(new Insets(40));
        area.getStyleClass().add("area-conteudo");
        return area;
    }

    // -----------------------------------------------------------------
    // Barra de status
    // -----------------------------------------------------------------

    private HBox criarBarraStatus() {
        // Exportacao de PDF ainda nao existe: texto fixo ate a issue dela.
        HBox barra = new HBox(18,
                rotuloStatus(descreverTiposDeTurno()),
                criarSeloBanco(),
                rotuloStatus("Nenhuma exportação registrada"));
        barra.setAlignment(Pos.CENTER_LEFT);
        barra.getStyleClass().add("barra-status");
        return barra;
    }

    private Label rotuloStatus(String texto) {
        Label rotulo = new Label(texto);
        rotulo.getStyleClass().add("texto-secundario");
        return rotulo;
    }

    /**
     * Conta os tipos de turno ativos em vez de mostrar "o regime configurado":
     * Configuracao nao tem campo de regime e o schema aceita varios tipo_turno
     * ativos ao mesmo tempo (24x72 e 12x36 coexistindo, por decisao de projeto).
     */
    private String descreverTiposDeTurno() {
        try {
            int ativos = tipoTurnoRepository.listar(true).size();
            return ativos == 1
                    ? "1 tipo de turno configurado"
                    : ativos + " tipos de turno configurados";
        } catch (RepositoryException excecao) {
            return "Tipos de turno indisponíveis";
        }
    }

    private Label criarSeloBanco() {
        Label selo = new Label();
        selo.getStyleClass().add("selo");
        if (bancoDisponivel()) {
            selo.setText("Banco conectado");
            selo.getStyleClass().add("selo-sucesso");
        } else {
            selo.setText("Banco indisponível");
            selo.getStyleClass().add("selo-perigo");
        }
        return selo;
    }

    /** Consulta trivial so para confirmar que o banco responde. */
    private boolean bancoDisponivel() {
        try (Connection conexao = ConexaoBanco.getConnection();
             Statement stmt = conexao.createStatement()) {
            stmt.executeQuery("SELECT 1").close();
            return true;
        } catch (SQLException excecao) {
            return false;
        }
    }
}
