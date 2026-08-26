package br.edu.sistemaescala.frontend.controller;

import java.time.YearMonth;
import java.util.List;

import br.edu.sistemaescala.backend.service.FuncionarioListagemItem;
import br.edu.sistemaescala.backend.service.FuncionarioService;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Controller da listagem de funcionários com busca, filtro e contadores (Issue #20 / Backlog #21 - Tela 2).
 */
public class FuncionarioController {

    public enum FiltroStatus {
        TODOS("Todos os status", null),
        ATIVOS("Apenas ativos", Boolean.TRUE),
        INATIVOS("Apenas inativos", Boolean.FALSE);

        private final String descricao;
        private final Boolean valorAtivo;

        FiltroStatus(String descricao, Boolean valorAtivo) {
            this.descricao = descricao;
            this.valorAtivo = valorAtivo;
        }

        public Boolean getValorAtivo() {
            return valorAtivo;
        }

        @Override
        public String toString() {
            return descricao;
        }
    }

    private final FuncionarioService funcionarioService;
    private final ObservableList<FuncionarioListagemItem> listaExibicao = FXCollections.observableArrayList();

    private final TableView<FuncionarioListagemItem> tabela = new TableView<>();
    private final TextField campoBusca = new TextField();
    private final ComboBox<FiltroStatus> comboStatus = new ComboBox<>();
    private final Label contadorRegistros = new Label();
    private final Button botaoNovoFuncionario = new Button("+ Novo Funcionário");

    public FuncionarioController(FuncionarioService funcionarioService) {
        this.funcionarioService = funcionarioService;
    }

    public Parent criarTela() {
        VBox raiz = new VBox(18);
        raiz.setPadding(new Insets(24));
        raiz.getStyleClass().add("area-conteudo");

        VBox cabecalho = criarCabecalho();
        VBox painelTabela = criarPainelListagem();

        raiz.getChildren().addAll(cabecalho, painelTabela);
        VBox.setVgrow(painelTabela, Priority.ALWAYS);

        configurarEventos();
        carregarDados();

        return raiz;
    }

    private VBox criarCabecalho() {
        Label titulo = new Label("Funcionários");
        titulo.getStyleClass().add("titulo-1");

        Label subtitulo = new Label("Gestão do efetivo, consulta de plantões no mês e controle de disponibilidade.");
        subtitulo.getStyleClass().add("texto-secundario");

        return new VBox(4, titulo, subtitulo);
    }

    private VBox criarPainelListagem() {
        VBox painel = new VBox(14);
        painel.getStyleClass().add("card");

        HBox barraFiltros = criarBarraFiltros();
        configurarColunasTabela();

        tabela.setItems(listaExibicao);
        VBox.setVgrow(tabela, Priority.ALWAYS);

        HBox barraRodape = new HBox(contadorRegistros);
        barraRodape.setAlignment(Pos.CENTER_LEFT);
        contadorRegistros.getStyleClass().add("texto-secundario");

        painel.getChildren().addAll(barraFiltros, tabela, barraRodape);
        return painel;
    }

    private HBox criarBarraFiltros() {
        campoBusca.setPromptText("Buscar por nome ou matrícula...");
        campoBusca.setPrefWidth(320);

        comboStatus.setItems(FXCollections.observableArrayList(FiltroStatus.values()));
        comboStatus.setValue(FiltroStatus.TODOS);
        comboStatus.setPrefWidth(180);

        Button botaoLimparBusca = new Button("Limpar");
        botaoLimparBusca.getStyleClass().add("button-secundario");
        botaoLimparBusca.setOnAction(e -> {
            campoBusca.clear();
            comboStatus.setValue(FiltroStatus.TODOS);
            carregarDados();
        });

        botaoNovoFuncionario.getStyleClass().add("button-primario");
        botaoNovoFuncionario.setOnAction(e -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Sistema de Escala");
            alert.setHeaderText("Cadastro de Funcionário");
            alert.setContentText("O formulário de cadastro de novo funcionário será integrado na Issue #22.");
            alert.showAndWait();
        });

        Region espacador = new Region();
        HBox.setHgrow(espacador, Priority.ALWAYS);

        HBox barra = new HBox(12, campoBusca, comboStatus, botaoLimparBusca, espacador, botaoNovoFuncionario);
        barra.setAlignment(Pos.CENTER_LEFT);
        return barra;
    }

    private void configurarColunasTabela() {
        TableColumn<FuncionarioListagemItem, String> colMatricula = new TableColumn<>("Matrícula");
        colMatricula.setCellValueFactory(dado -> new SimpleStringProperty(dado.getValue().getMatricula()));
        colMatricula.setPrefWidth(120);

        TableColumn<FuncionarioListagemItem, String> colNome = new TableColumn<>("Nome");
        colNome.setCellValueFactory(dado -> new SimpleStringProperty(dado.getValue().getNome()));
        colNome.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    getStyleClass().remove("texto-secundario");
                } else {
                    setText(item);
                    FuncionarioListagemItem linha = getTableRow() != null ? getTableRow().getItem() : null;
                    if (linha != null && !linha.isAtivo()) {
                        if (!getStyleClass().contains("texto-secundario")) {
                            getStyleClass().add("texto-secundario");
                        }
                    } else {
                        getStyleClass().remove("texto-secundario");
                    }
                }
            }
        });
        colNome.setPrefWidth(260);

        TableColumn<FuncionarioListagemItem, String> colTelefone = new TableColumn<>("Telefone");
        colTelefone.setCellValueFactory(dado -> {
            String tel = dado.getValue().getTelefone();
            return new SimpleStringProperty(tel != null && !tel.isBlank() ? tel : "-");
        });
        colTelefone.setPrefWidth(150);

        TableColumn<FuncionarioListagemItem, Number> colPlantoes = new TableColumn<>("Plantões/mês");
        colPlantoes.setCellValueFactory(dado -> new SimpleIntegerProperty(dado.getValue().getPlantoesNoMes()));
        colPlantoes.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.toString());
                    setAlignment(Pos.CENTER);
                }
            }
        });
        colPlantoes.setPrefWidth(130);

        TableColumn<FuncionarioListagemItem, String> colStatus = new TableColumn<>("Status");
        colStatus.setCellValueFactory(dado -> new SimpleStringProperty(
                dado.getValue().isAtivo() ? "Ativo" : "Inativo"));
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Label selo = new Label(item);
                    selo.getStyleClass().add("selo");
                    if ("Ativo".equalsIgnoreCase(item)) {
                        selo.getStyleClass().add("selo-sucesso");
                    } else {
                        selo.getStyleClass().add("selo-neutro");
                    }
                    setGraphic(selo);
                    setText(null);
                    setAlignment(Pos.CENTER);
                }
            }
        });
        colStatus.setPrefWidth(110);

        TableColumn<FuncionarioListagemItem, Void> colAcoes = new TableColumn<>("Ações");
        colAcoes.setCellFactory(col -> new TableCell<>() {
            private final Button botaoAcao = new Button();

            {
                botaoAcao.getStyleClass().add("button-secundario");
                botaoAcao.setOnAction(event -> {
                    FuncionarioListagemItem item = getTableView().getItems().get(getIndex());
                    if (item != null) {
                        if (item.isAtivo()) {
                            confirmarEDesativar(item);
                        } else {
                            reativarFuncionario(item);
                        }
                    }
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getIndex() >= getTableView().getItems().size()) {
                    setGraphic(null);
                } else {
                    FuncionarioListagemItem linha = getTableView().getItems().get(getIndex());
                    if (linha != null) {
                        botaoAcao.setText(linha.isAtivo() ? "Desativar" : "Reativar");
                        setGraphic(botaoAcao);
                        setAlignment(Pos.CENTER);
                    } else {
                        setGraphic(null);
                    }
                }
            }
        });
        colAcoes.setPrefWidth(120);

        tabela.getColumns().addAll(List.of(colMatricula, colNome, colTelefone, colPlantoes, colStatus, colAcoes));
        tabela.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

        tabela.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(FuncionarioListagemItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setStyle("");
                } else if (!item.isAtivo()) {
                    if (!getStyleClass().contains("linha-inativa")) {
                        getStyleClass().add("linha-inativa");
                    }
                } else {
                    getStyleClass().remove("linha-inativa");
                }
            }
        });
    }

    private void configurarEventos() {
        campoBusca.textProperty().addListener((obs, antigo, novo) -> carregarDados());
        comboStatus.valueProperty().addListener((obs, antigo, novo) -> carregarDados());
    }

    private void carregarDados() {
        String termo = campoBusca.getText();
        FiltroStatus filtro = comboStatus.getValue();
        Boolean statusAtivo = filtro != null ? filtro.getValorAtivo() : null;

        try {
            List<FuncionarioListagemItem> itens = funcionarioService.listarComPlantoes(statusAtivo, termo, YearMonth.now());
            listaExibicao.setAll(itens);
            atualizarContador(itens.size());
        } catch (Exception e) {
            contadorRegistros.setText("Erro ao carregar lista de funcionários.");
        }
    }

    private void atualizarContador(int total) {
        if (total == 0) {
            contadorRegistros.setText("Nenhum funcionário encontrado.");
        } else if (total == 1) {
            contadorRegistros.setText("1 funcionário encontrado.");
        } else {
            contadorRegistros.setText(total + " funcionários encontrados.");
        }
    }

    private void confirmarEDesativar(FuncionarioListagemItem item) {
        Alert alerta = new Alert(Alert.AlertType.CONFIRMATION);
        alerta.setTitle("Confirmar Desativação");
        alerta.setHeaderText("Deseja desativar o funcionário " + item.getNome() + "?");
        alerta.setContentText("O funcionário não será escalado para novos plantões, mas o histórico passado será preservado.");

        alerta.showAndWait().ifPresent(resposta -> {
            if (resposta == ButtonType.OK) {
                try {
                    funcionarioService.desativar(item.getId());
                    carregarDados();
                } catch (Exception e) {
                    mostrarErroDialogo("Erro ao desativar", "Não foi possível desativar o funcionário: " + e.getMessage());
                }
            }
        });
    }

    private void reativarFuncionario(FuncionarioListagemItem item) {
        try {
            funcionarioService.ativar(item.getId());
            carregarDados();
        } catch (Exception e) {
            mostrarErroDialogo("Erro ao reativar", "Não foi possível reativar o funcionário: " + e.getMessage());
        }
    }

    private void mostrarErroDialogo(String titulo, String mensagem) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Sistema de Escala");
        alert.setHeaderText(titulo);
        alert.setContentText(mensagem);
        alert.showAndWait();
    }
}

