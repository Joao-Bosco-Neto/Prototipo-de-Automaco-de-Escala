package br.edu.sistemaescala.frontend.controller;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import br.edu.sistemaescala.backend.dao.ConexaoBanco;
import br.edu.sistemaescala.backend.model.Configuracao;
import br.edu.sistemaescala.backend.repository.ConfiguracaoRepository;
import br.edu.sistemaescala.backend.repository.RepositoryException;
import br.edu.sistemaescala.backend.repository.TipoTurnoRepository;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * Shell da aplicacao (issue #38): barra de titulo, menu superior, navegacao
 * lateral, area central de conteudo e barra de status.
 *
 * A tela e montada em Java puro, como o resto do frontend — o projeto nao usa
 * FXML em lugar nenhum. O tema (app.css) e aplicado na Scene criada pelo
 * Main, entao aqui basta usar as classes de estilo via getStyleClass().
 *
 * A troca de conteudo e um roteador simples: cada item da navegacao lateral
 * substitui o centro do BorderPane. Como as telas reais ainda nao existem,
 * todos os itens mostram um placeholder "em construcao"; cada uma sera
 * trocada pela issue especifica correspondente.
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

    private final BorderPane raiz = new BorderPane();
    private final List<Button> botoesNavegacao = new ArrayList<>();

    public ShellController(ConfiguracaoRepository configuracaoRepository,
                           TipoTurnoRepository tipoTurnoRepository) {
        this.configuracaoRepository = configuracaoRepository;
        this.tipoTurnoRepository = tipoTurnoRepository;
    }

    public Parent criarTela() {
        raiz.setTop(new VBox(criarBarraTitulo(), criarMenuSuperior()));
        raiz.setLeft(criarNavegacaoLateral());
        raiz.setBottom(criarBarraStatus());

        // "Visão geral" e o item selecionado ao abrir o shell.
        selecionar(botoesNavegacao.get(0), ITENS_NAVEGACAO.get(0));
        return raiz;
    }

    // -----------------------------------------------------------------
    // Barra de titulo
    // -----------------------------------------------------------------

    private HBox criarBarraTitulo() {
        Label organizacao = new Label(nomeOrganizacao());
        organizacao.getStyleClass().add("titulo-organizacao");

        Region espacador = new Region();
        HBox.setHgrow(espacador, Priority.ALWAYS);

        // Sem usuario logado por enquanto: login e sessao ainda nao existem
        // (issues #15 e #17), entao a barra mostra so a organizacao e o Sair.
        Button sair = new Button("Sair");
        sair.getStyleClass().add("button-secundario-claro");
        sair.setOnAction(evento -> {
            // TODO(#15,#17): substituir por logout real quando login e sessao existirem.
            Platform.exit();
        });

        HBox barra = new HBox(12, organizacao, espacador, sair);
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
