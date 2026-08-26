package br.edu.sistemaescala.frontend.controller;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import br.edu.sistemaescala.backend.model.Funcionario;
import br.edu.sistemaescala.backend.service.FuncionarioListagemItem;
import br.edu.sistemaescala.backend.service.FuncionarioService;
import br.edu.sistemaescala.backend.service.RegraFuncionarioException;
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
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Controller da tela de Funcionários (Listagem + Formulário lateral de cadastro/edição).
 * Atende às Issues #20 e #21 (Backlog #21 e #22 - Tela 2).
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

    private static final String CLASSE_CAMPO_COM_ERRO = "campo-com-erro";

    private final FuncionarioService funcionarioService;
    private final ObservableList<FuncionarioListagemItem> listaExibicao = FXCollections.observableArrayList();

    // Componentes da Tabela e Filtros
    private final TableView<FuncionarioListagemItem> tabela = new TableView<>();
    private final TextField campoBusca = new TextField();
    private final ComboBox<FiltroStatus> comboStatus = new ComboBox<>();
    private final Label contadorRegistros = new Label();
    private final Button botaoNovoFuncionario = new Button("+ Novo Funcionário");

    // Componentes do Formulário Lateral (Card)
    private final Label tituloFormulario = new Label("Novo Funcionário");
    private final TextField campoNome = new TextField();
    private final Label erroNome = new Label();

    private final TextField campoMatricula = new TextField();
    private final Label erroMatricula = new Label();

    private final TextField campoTelefone = new TextField();
    private final Label erroTelefone = new Label();

    private final TextArea campoObservacoes = new TextArea();
    private final CheckBox checkAtivo = new CheckBox("Funcionário ativo");
    private final Label labelMensagemCard = new Label();

    private final Button botaoSalvar = new Button("Salvar");
    private final Button botaoLimpar = new Button("Limpar");

    // Estado de Edição
    private Integer idFuncionarioEmEdicao = null;
    private boolean formatandoTelefone = false;

    public FuncionarioController(FuncionarioService funcionarioService) {
        this.funcionarioService = funcionarioService;
    }

    public Parent criarTela() {
        VBox raiz = new VBox(18);
        raiz.setPadding(new Insets(24));
        raiz.getStyleClass().add("area-conteudo");

        VBox cabecalho = criarCabecalho();
        HBox conteudoPrincipal = criarConteudoPrincipal();

        raiz.getChildren().addAll(cabecalho, conteudoPrincipal);
        VBox.setVgrow(conteudoPrincipal, Priority.ALWAYS);

        configurarEventos();
        carregarDados();

        return raiz;
    }

    private VBox criarCabecalho() {
        Label titulo = new Label("Funcionários");
        titulo.getStyleClass().add("titulo-1");

        Label subtitulo = new Label("Gestão do efetivo, cadastro/edição e apuração de plantões no mês.");
        subtitulo.getStyleClass().add("texto-secundario");

        return new VBox(4, titulo, subtitulo);
    }

    private HBox criarConteudoPrincipal() {
        VBox painelTabela = criarPainelListagem();
        VBox painelFormulario = criarCardFormulario();

        HBox.setHgrow(painelTabela, Priority.ALWAYS);

        HBox layout = new HBox(20, painelTabela, painelFormulario);
        layout.setAlignment(Pos.TOP_LEFT);
        return layout;
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
        campoBusca.setPrefWidth(280);

        comboStatus.setItems(FXCollections.observableArrayList(FiltroStatus.values()));
        comboStatus.setValue(FiltroStatus.TODOS);
        comboStatus.setPrefWidth(160);

        Button botaoLimparBusca = new Button("Limpar");
        botaoLimparBusca.getStyleClass().add("button-secundario");
        botaoLimparBusca.setOnAction(e -> {
            campoBusca.clear();
            comboStatus.setValue(FiltroStatus.TODOS);
            carregarDados();
        });

        botaoNovoFuncionario.getStyleClass().add("button-primario");
        botaoNovoFuncionario.setOnAction(e -> prepararNovoCadastro());

        Region espacador = new Region();
        HBox.setHgrow(espacador, Priority.ALWAYS);

        HBox barra = new HBox(10, campoBusca, comboStatus, botaoLimparBusca, espacador, botaoNovoFuncionario);
        barra.setAlignment(Pos.CENTER_LEFT);
        return barra;
    }

    private void configurarColunasTabela() {
        TableColumn<FuncionarioListagemItem, String> colMatricula = new TableColumn<>("Matrícula");
        colMatricula.setCellValueFactory(dado -> new SimpleStringProperty(dado.getValue().getMatricula()));
        colMatricula.setPrefWidth(110);

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
        colNome.setPrefWidth(220);

        TableColumn<FuncionarioListagemItem, String> colTelefone = new TableColumn<>("Telefone");
        colTelefone.setCellValueFactory(dado -> {
            String tel = dado.getValue().getTelefone();
            return new SimpleStringProperty(tel != null && !tel.isBlank() ? tel : "-");
        });
        colTelefone.setPrefWidth(140);

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
        colPlantoes.setPrefWidth(100);

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
        colStatus.setPrefWidth(90);

        TableColumn<FuncionarioListagemItem, Void> colAcoes = new TableColumn<>("Ações");
        colAcoes.setCellFactory(col -> new TableCell<>() {
            private final Button botaoEditarLinha = new Button("Editar");
            private final Button botaoAlternarLinha = new Button();
            private final HBox containerAcoes = new HBox(6, botaoEditarLinha, botaoAlternarLinha);

            {
                botaoEditarLinha.getStyleClass().add("button-secundario");
                botaoAlternarLinha.getStyleClass().add("button-secundario");
                containerAcoes.setAlignment(Pos.CENTER);

                botaoEditarLinha.setOnAction(event -> {
                    FuncionarioListagemItem item = getTableView().getItems().get(getIndex());
                    if (item != null) {
                        carregarNoFormulario(item.getFuncionario());
                    }
                });

                botaoAlternarLinha.setOnAction(event -> {
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
                        botaoAlternarLinha.setText(linha.isAtivo() ? "Desativar" : "Reativar");
                        setGraphic(containerAcoes);
                    } else {
                        setGraphic(null);
                    }
                }
            }
        });
        colAcoes.setPrefWidth(160);

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

    private VBox criarCardFormulario() {
        VBox painelFormulario = new VBox(10);
        painelFormulario.getStyleClass().add("card");
        painelFormulario.setPrefWidth(360);
        painelFormulario.setMinWidth(320);

        tituloFormulario.getStyleClass().add("titulo-2");

        configurarEstiloErroLabel(erroNome);
        configurarEstiloErroLabel(erroMatricula);
        configurarEstiloErroLabel(erroTelefone);

        campoNome.setPromptText("Nome completo do policial/agente");
        campoMatricula.setPromptText("Ex: POL12345");
        campoTelefone.setPromptText("(63) 99999-9999");
        campoObservacoes.setPromptText("Observações ou restrições...");
        campoObservacoes.setPrefRowCount(3);
        campoObservacoes.setWrapText(true);

        checkAtivo.setSelected(true);

        labelMensagemCard.getStyleClass().add("selo");
        labelMensagemCard.setWrapText(true);
        labelMensagemCard.setVisible(false);
        labelMensagemCard.setManaged(false);

        botaoSalvar.getStyleClass().add("button-primario");
        botaoLimpar.getStyleClass().add("button-secundario");

        HBox barraBotoes = new HBox(10, botaoSalvar, botaoLimpar);
        barraBotoes.setAlignment(Pos.CENTER_RIGHT);

        painelFormulario.getChildren().addAll(
                tituloFormulario,
                new Label("Nome Completo *"),
                campoNome,
                erroNome,
                new Label("Matrícula *"),
                campoMatricula,
                erroMatricula,
                new Label("Telefone"),
                campoTelefone,
                erroTelefone,
                new Label("Observações"),
                campoObservacoes,
                checkAtivo,
                labelMensagemCard,
                barraBotoes
        );

        return painelFormulario;
    }

    private void configurarEstiloErroLabel(Label label) {
        label.getStyleClass().addAll("selo", "selo-perigo");
        label.setVisible(false);
        label.setManaged(false);
    }

    private void configurarEventos() {
        campoBusca.textProperty().addListener((obs, antigo, novo) -> carregarDados());
        comboStatus.valueProperty().addListener((obs, antigo, novo) -> carregarDados());

        tabela.getSelectionModel().selectedItemProperty().addListener((obs, antigo, selecionado) -> {
            if (selecionado != null) {
                carregarNoFormulario(selecionado.getFuncionario());
            }
        });

        // Limpeza de erros em tempo real ao digitar
        campoNome.textProperty().addListener((obs, antigo, novo) -> {
            campoNome.getStyleClass().remove(CLASSE_CAMPO_COM_ERRO);
            esconderErroLabel(erroNome);
        });

        campoMatricula.textProperty().addListener((obs, antigo, novo) -> {
            campoMatricula.getStyleClass().remove(CLASSE_CAMPO_COM_ERRO);
            esconderErroLabel(erroMatricula);
        });

        // Máscara de Telefone
        campoTelefone.textProperty().addListener((obs, antigo, novo) -> {
            if (formatandoTelefone || novo == null) {
                return;
            }
            formatandoTelefone = true;
            String apenasDigitos = novo.replaceAll("[^0-9]", "");
            if (apenasDigitos.length() > 11) {
                apenasDigitos = apenasDigitos.substring(0, 11);
            }
            String formatado = formatarTelefone(apenasDigitos);
            campoTelefone.setText(formatado);
            campoTelefone.positionCaret(formatado.length());
            formatandoTelefone = false;
        });

        botaoSalvar.setOnAction(e -> salvarFuncionario());
        botaoLimpar.setOnAction(e -> prepararNovoCadastro());
    }

    private String formatarTelefone(String digitos) {
        if (digitos.isEmpty()) {
            return "";
        }
        if (digitos.length() <= 2) {
            return "(" + digitos;
        }
        if (digitos.length() <= 6) {
            return "(" + digitos.substring(0, 2) + ") " + digitos.substring(2);
        }
        if (digitos.length() <= 10) {
            return "(" + digitos.substring(0, 2) + ") " + digitos.substring(2, 6) + "-" + digitos.substring(6);
        }
        return "(" + digitos.substring(0, 2) + ") " + digitos.substring(2, 7) + "-" + digitos.substring(7, 11);
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

    private void prepararNovoCadastro() {
        idFuncionarioEmEdicao = null;
        tituloFormulario.setText("Novo Funcionário");
        campoNome.clear();
        campoMatricula.clear();
        campoTelefone.clear();
        campoObservacoes.clear();
        checkAtivo.setSelected(true);
        botaoLimpar.setText("Limpar");
        limparErrosFormulario();
        tabela.getSelectionModel().clearSelection();
        campoNome.requestFocus();
    }

    private void carregarNoFormulario(Funcionario funcionario) {
        if (funcionario == null) {
            return;
        }
        idFuncionarioEmEdicao = funcionario.getId();
        tituloFormulario.setText("Editar: " + funcionario.getNome());
        campoNome.setText(funcionario.getNome());
        campoMatricula.setText(funcionario.getMatricula());
        campoTelefone.setText(funcionario.getTelefone() != null ? funcionario.getTelefone() : "");
        campoObservacoes.setText(funcionario.getObservacoes() != null ? funcionario.getObservacoes() : "");
        checkAtivo.setSelected(funcionario.isAtivo());
        botaoLimpar.setText("Cancelar");
        limparErrosFormulario();
    }

    private void salvarFuncionario() {
        limparErrosFormulario();

        String nome = campoNome.getText();
        String matricula = campoMatricula.getText();
        String telefone = campoTelefone.getText();
        String observacoes = campoObservacoes.getText();
        boolean ativo = checkAtivo.isSelected();

        if (idFuncionarioEmEdicao != null && !ativo) {
            int plantoesFuturos = funcionarioService.contarPlantoesFuturos(idFuncionarioEmEdicao, LocalDateTime.now());
            if (plantoesFuturos > 0) {
                Alert alerta = new Alert(Alert.AlertType.CONFIRMATION);
                alerta.setTitle("Confirmar Desativação");
                alerta.setHeaderText("Atenção: O funcionário possui " + plantoesFuturos + " plantão(ões) futuro(s) agendado(s)!");
                alerta.setContentText("Ao desativá-lo, as alocações existentes serão preservadas no banco, mas ele não poderá ser escalado para novos plantões.\n\nDeseja confirmar a desativação?");
                var resposta = alerta.showAndWait();
                if (resposta.isEmpty() || resposta.get() != ButtonType.OK) {
                    checkAtivo.setSelected(true);
                    return;
                }
            }
        }

        try {
            if (idFuncionarioEmEdicao == null) {
                funcionarioService.cadastrar(nome, matricula, telefone, observacoes, ativo);
                exibirSucessoCard("Funcionário cadastrado com sucesso!");
                prepararNovoCadastro();
            } else {
                funcionarioService.atualizar(idFuncionarioEmEdicao, nome, matricula, telefone, observacoes, ativo);
                exibirSucessoCard("Funcionário atualizado com sucesso!");
                prepararNovoCadastro();
            }
            carregarDados();
        } catch (RegraFuncionarioException excecao) {
            tratarErroValidacao(excecao.getMessage());
        } catch (Exception excecao) {
            exibirErroCard("Erro ao salvar funcionário: " + excecao.getMessage());
        }
    }

    private void tratarErroValidacao(String mensagem) {
        String msgLower = mensagem.toLowerCase();
        if (msgLower.contains("nome")) {
            marcarErroCampo(campoNome, erroNome, mensagem);
        } else if (msgLower.contains("matrícula") || msgLower.contains("matricula")) {
            marcarErroCampo(campoMatricula, erroMatricula, mensagem);
        } else {
            exibirErroCard(mensagem);
        }
    }

    private void marcarErroCampo(TextField campo, Label labelErro, String mensagem) {
        if (!campo.getStyleClass().contains(CLASSE_CAMPO_COM_ERRO)) {
            campo.getStyleClass().add(CLASSE_CAMPO_COM_ERRO);
        }
        labelErro.setText(mensagem);
        labelErro.setVisible(true);
        labelErro.setManaged(true);
        campo.requestFocus();
    }

    private void esconderErroLabel(Label labelErro) {
        labelErro.setText("");
        labelErro.setVisible(false);
        labelErro.setManaged(false);
    }

    private void limparErrosFormulario() {
        campoNome.getStyleClass().remove(CLASSE_CAMPO_COM_ERRO);
        campoMatricula.getStyleClass().remove(CLASSE_CAMPO_COM_ERRO);
        campoTelefone.getStyleClass().remove(CLASSE_CAMPO_COM_ERRO);
        esconderErroLabel(erroNome);
        esconderErroLabel(erroMatricula);
        esconderErroLabel(erroTelefone);
        labelMensagemCard.setText("");
        labelMensagemCard.setVisible(false);
        labelMensagemCard.setManaged(false);
        labelMensagemCard.getStyleClass().removeAll("selo-sucesso", "selo-perigo");
    }

    private void exibirSucessoCard(String mensagem) {
        labelMensagemCard.setText(mensagem);
        labelMensagemCard.getStyleClass().removeAll("selo-sucesso", "selo-perigo");
        labelMensagemCard.getStyleClass().add("selo-sucesso");
        labelMensagemCard.setVisible(true);
        labelMensagemCard.setManaged(true);
    }

    private void exibirErroCard(String mensagem) {
        labelMensagemCard.setText(mensagem);
        labelMensagemCard.getStyleClass().removeAll("selo-sucesso", "selo-perigo");
        labelMensagemCard.getStyleClass().add("selo-perigo");
        labelMensagemCard.setVisible(true);
        labelMensagemCard.setManaged(true);
    }

    private void confirmarEDesativar(FuncionarioListagemItem item) {
        int plantoesFuturos = funcionarioService.contarPlantoesFuturos(item.getId(), LocalDateTime.now());

        Alert alerta = new Alert(Alert.AlertType.CONFIRMATION);
        alerta.setTitle("Confirmar Desativação");

        if (plantoesFuturos > 0) {
            alerta.setHeaderText("Atenção: O funcionário " + item.getNome() + " possui " + plantoesFuturos + " plantão(ões) futuro(s) agendado(s)!");
            alerta.setContentText("Ao desativá-lo, o histórico passado e as alocações existentes serão preservados, mas ele não poderá ser atribuído a novos plantões ou coberturas.\n\nDeseja realmente desativar?");
        } else {
            alerta.setHeaderText("Deseja desativar o funcionário " + item.getNome() + "?");
            alerta.setContentText("O funcionário não será escalado para novos plantões, mas o histórico passado será preservado.");
        }

        alerta.showAndWait().ifPresent(resposta -> {
            if (resposta == ButtonType.OK) {
                try {
                    funcionarioService.desativar(item.getId());
                    carregarDados();
                    if (idFuncionarioEmEdicao != null && idFuncionarioEmEdicao.equals(item.getId())) {
                        prepararNovoCadastro();
                    }
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
            if (idFuncionarioEmEdicao != null && idFuncionarioEmEdicao.equals(item.getId())) {
                prepararNovoCadastro();
            }
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
