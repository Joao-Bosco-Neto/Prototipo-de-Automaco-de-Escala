package br.edu.sistemaescala.frontend.controller;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

import br.edu.sistemaescala.backend.model.TipoTurno;
import br.edu.sistemaescala.backend.service.RegraTipoTurnoException;
import br.edu.sistemaescala.backend.service.ResultadoViabilidadeTurno;
import br.edu.sistemaescala.backend.service.TipoTurnoService;
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
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Controller da tela de Tipos de Turno (Listagem e Parametrização de Regimes de Trabalho - Issue #39).
 */
public class TipoTurnoController {

    public enum FiltroStatusTurno {
        TODOS("Todos os status", null),
        ATIVOS("Apenas ativos", Boolean.TRUE),
        INATIVOS("Apenas inativos", Boolean.FALSE);

        private final String descricao;
        private final Boolean valorAtivo;

        FiltroStatusTurno(String descricao, Boolean valorAtivo) {
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
    private static final DateTimeFormatter HORA_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final TipoTurnoService tipoTurnoService;
    private final ObservableList<TipoTurno> listaTiposTurno = FXCollections.observableArrayList();

    // Componentes da Tabela
    private final TableView<TipoTurno> tabela = new TableView<>();
    private final ComboBox<FiltroStatusTurno> comboFiltroStatus = new ComboBox<>();
    private final Label contadorRegistros = new Label();
    private final Button botaoNovoTipoTurno = new Button("+ Novo Tipo de Turno");

    // Componentes do Formulário Lateral
    private final Label tituloFormulario = new Label("Novo Tipo de Turno");
    private final TextField campoNome = new TextField();
    private final Label erroNome = new Label();

    private final TextField campoHoraInicio = new TextField();
    private final Label erroHoraInicio = new Label();

    private final TextField campoDuracaoHoras = new TextField();
    private final Label erroDuracao = new Label();

    private final TextField campoDescansoHoras = new TextField();
    private final Label erroDescanso = new Label();

    private final TextField campoMinAgentes = new TextField();
    private final Label erroMinAgentes = new Label();

    private final TextField campoMaxAgentes = new TextField();
    private final Label erroMaxAgentes = new Label();

    private final CheckBox checkContaBancoHoras = new CheckBox("Computar no banco de horas");
    private final CheckBox checkAtivo = new CheckBox("Tipo de turno ativo");

    private final Label labelDiagnosticoViabilidade = new Label();
    private final Label labelMensagemCard = new Label();

    private final Button botaoSalvar = new Button("Salvar");
    private final Button botaoLimpar = new Button("Limpar");

    private Integer idTipoTurnoEmEdicao = null;

    public TipoTurnoController(TipoTurnoService tipoTurnoService) {
        this.tipoTurnoService = tipoTurnoService;
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
        atualizarDiagnosticoViabilidade();

        return raiz;
    }

    private VBox criarCabecalho() {
        Label titulo = new Label("Tipos de Turno e Regimes de Trabalho");
        titulo.getStyleClass().add("titulo-1");

        Label subtitulo = new Label("Parametrização de escalas (24x72, 12x36, 5x2, sobreaviso) e intervalos de descanso.");
        subtitulo.getStyleClass().add("texto-secundario");

        return new VBox(4, titulo, subtitulo);
    }

    private HBox criarConteudoPrincipal() {
        VBox painelTabela = criarPainelTabela();
        VBox painelFormulario = criarCardFormulario();

        HBox.setHgrow(painelTabela, Priority.ALWAYS);

        HBox layout = new HBox(20, painelTabela, painelFormulario);
        layout.setAlignment(Pos.TOP_LEFT);
        return layout;
    }

    private VBox criarPainelTabela() {
        VBox painel = new VBox(14);
        painel.getStyleClass().add("card");

        comboFiltroStatus.setItems(FXCollections.observableArrayList(FiltroStatusTurno.values()));
        comboFiltroStatus.setValue(FiltroStatusTurno.TODOS);
        comboFiltroStatus.setPrefWidth(160);

        botaoNovoTipoTurno.getStyleClass().add("button-primario");
        botaoNovoTipoTurno.setOnAction(e -> prepararNovoCadastro());

        Region espacador = new Region();
        HBox.setHgrow(espacador, Priority.ALWAYS);

        HBox barraFiltros = new HBox(10, new Label("Filtro:"), comboFiltroStatus, espacador, botaoNovoTipoTurno);
        barraFiltros.setAlignment(Pos.CENTER_LEFT);

        configurarColunasTabela();
        tabela.setItems(listaTiposTurno);
        VBox.setVgrow(tabela, Priority.ALWAYS);

        HBox barraRodape = new HBox(contadorRegistros);
        barraRodape.setAlignment(Pos.CENTER_LEFT);
        contadorRegistros.getStyleClass().add("texto-secundario");

        painel.getChildren().addAll(barraFiltros, tabela, barraRodape);
        return painel;
    }

    private void configurarColunasTabela() {
        TableColumn<TipoTurno, String> colNome = new TableColumn<>("Nome do Regime");
        colNome.setCellValueFactory(dado -> new SimpleStringProperty(dado.getValue().getNome()));
        colNome.setPrefWidth(180);

        TableColumn<TipoTurno, String> colInicio = new TableColumn<>("Início");
        colInicio.setCellValueFactory(dado -> new SimpleStringProperty(
                dado.getValue().getHoraInicio() != null ? dado.getValue().getHoraInicio().format(HORA_FORMATTER) : "-"));
        colInicio.setPrefWidth(80);

        TableColumn<TipoTurno, String> colDuracao = new TableColumn<>("Duração");
        colDuracao.setCellValueFactory(dado -> new SimpleStringProperty(
                dado.getValue().getDuracaoHoras() != null ? dado.getValue().getDuracaoHoras().toPlainString() + "h" : "-"));
        colDuracao.setPrefWidth(85);

        TableColumn<TipoTurno, String> colDescanso = new TableColumn<>("Descanso");
        colDescanso.setCellValueFactory(dado -> new SimpleStringProperty(
                dado.getValue().getIntervaloDescansoHoras() != null ? dado.getValue().getIntervaloDescansoHoras().toPlainString() + "h" : "0h"));
        colDescanso.setPrefWidth(90);

        TableColumn<TipoTurno, String> colAgentes = new TableColumn<>("Agentes (Mín/Máx)");
        colAgentes.setCellValueFactory(dado -> {
            TipoTurno tt = dado.getValue();
            String max = tt.getMaxAgentes() != null ? String.valueOf(tt.getMaxAgentes()) : "Sem limite";
            return new SimpleStringProperty(tt.getMinAgentes() + " / " + max);
        });
        colAgentes.setPrefWidth(130);

        TableColumn<TipoTurno, String> colBanco = new TableColumn<>("Banco de Horas");
        colBanco.setCellValueFactory(dado -> new SimpleStringProperty(dado.getValue().isContaBancoHoras() ? "Sim" : "Não"));
        colBanco.setPrefWidth(110);

        TableColumn<TipoTurno, String> colStatus = new TableColumn<>("Status");
        colStatus.setCellValueFactory(dado -> new SimpleStringProperty(dado.getValue().isAtivo() ? "Ativo" : "Inativo"));
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

        TableColumn<TipoTurno, Void> colAcoes = new TableColumn<>("Ações");
        colAcoes.setCellFactory(col -> new TableCell<>() {
            private final Button botaoEditar = new Button("Editar");
            private final Button botaoAlternar = new Button();
            private final HBox container = new HBox(6, botaoEditar, botaoAlternar);

            {
                // Variante compacta: com o padding do secundário cheio os
                // rótulos saem cortados ("E...", "Desat...") na largura da célula.
                botaoEditar.getStyleClass().add("button-secundario-compacto");
                botaoAlternar.getStyleClass().add("button-secundario-compacto");
                container.setAlignment(Pos.CENTER);

                botaoEditar.setOnAction(event -> {
                    TipoTurno item = getTableView().getItems().get(getIndex());
                    if (item != null) {
                        carregarNoFormulario(item);
                    }
                });

                botaoAlternar.setOnAction(event -> {
                    TipoTurno item = getTableView().getItems().get(getIndex());
                    if (item != null) {
                        if (item.isAtivo()) {
                            confirmarEDesativar(item);
                        } else {
                            reativarTipoTurno(item);
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
                    TipoTurno linha = getTableView().getItems().get(getIndex());
                    if (linha != null) {
                        botaoAlternar.setText(linha.isAtivo() ? "Desativar" : "Reativar");
                        setGraphic(container);
                    } else {
                        setGraphic(null);
                    }
                }
            }
        });
        colAcoes.setPrefWidth(160);

        tabela.getColumns().addAll(List.of(colNome, colInicio, colDuracao, colDescanso, colAgentes, colBanco, colStatus, colAcoes));
        tabela.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

        tabela.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(TipoTurno item, boolean empty) {
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
        painelFormulario.setPrefWidth(380);
        painelFormulario.setMinWidth(340);

        tituloFormulario.getStyleClass().add("titulo-2");

        configurarEstiloErroLabel(erroNome);
        configurarEstiloErroLabel(erroHoraInicio);
        configurarEstiloErroLabel(erroDuracao);
        configurarEstiloErroLabel(erroDescanso);
        configurarEstiloErroLabel(erroMinAgentes);
        configurarEstiloErroLabel(erroMaxAgentes);

        campoNome.setPromptText("Ex: Plantão 24x72, Diurno 12x36");
        campoHoraInicio.setPromptText("08:00");
        campoDuracaoHoras.setPromptText("24");
        campoDescansoHoras.setPromptText("72");
        campoMinAgentes.setPromptText("2");
        campoMaxAgentes.setPromptText("Opcional (ex: 4)");

        checkContaBancoHoras.setSelected(true);
        checkAtivo.setSelected(true);

        labelDiagnosticoViabilidade.setWrapText(true);
        labelDiagnosticoViabilidade.getStyleClass().add("selo");

        labelMensagemCard.getStyleClass().add("selo");
        labelMensagemCard.setWrapText(true);
        labelMensagemCard.setVisible(false);
        labelMensagemCard.setManaged(false);

        botaoSalvar.getStyleClass().add("button-primario");
        botaoLimpar.getStyleClass().add("button-secundario");

        HBox barraBotoes = new HBox(10, botaoSalvar, botaoLimpar);
        barraBotoes.setAlignment(Pos.CENTER_RIGHT);

        HBox linhaHorarios = new HBox(10,
                new VBox(4, new Label("Hora Início *"), campoHoraInicio, erroHoraInicio),
                new VBox(4, new Label("Duração (h) *"), campoDuracaoHoras, erroDuracao)
        );
        HBox.setHgrow(linhaHorarios.getChildren().get(0), Priority.ALWAYS);
        HBox.setHgrow(linhaHorarios.getChildren().get(1), Priority.ALWAYS);

        HBox linhaAgentes = new HBox(10,
                new VBox(4, new Label("Mín. Agentes *"), campoMinAgentes, erroMinAgentes),
                new VBox(4, new Label("Máx. Agentes"), campoMaxAgentes, erroMaxAgentes)
        );
        HBox.setHgrow(linhaAgentes.getChildren().get(0), Priority.ALWAYS);
        HBox.setHgrow(linhaAgentes.getChildren().get(1), Priority.ALWAYS);

        painelFormulario.getChildren().addAll(
                tituloFormulario,
                new Label("Nome do Tipo de Turno *"),
                campoNome,
                erroNome,
                linhaHorarios,
                new Label("Intervalo de Descanso (h) *"),
                campoDescansoHoras,
                erroDescanso,
                linhaAgentes,
                checkContaBancoHoras,
                checkAtivo,
                new Label("Diagnóstico de Viabilidade do Efetivo:"),
                labelDiagnosticoViabilidade,
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
        comboFiltroStatus.valueProperty().addListener((obs, antigo, novo) -> carregarDados());

        tabela.getSelectionModel().selectedItemProperty().addListener((obs, antigo, selecionado) -> {
            if (selecionado != null) {
                carregarNoFormulario(selecionado);
            }
        });

        // Limpeza de erros e recálculo dinâmico de viabilidade
        campoNome.textProperty().addListener((obs, antigo, novo) -> {
            campoNome.getStyleClass().remove(CLASSE_CAMPO_COM_ERRO);
            esconderErroLabel(erroNome);
        });

        campoHoraInicio.textProperty().addListener((obs, antigo, novo) -> {
            campoHoraInicio.getStyleClass().remove(CLASSE_CAMPO_COM_ERRO);
            esconderErroLabel(erroHoraInicio);
        });

        campoDuracaoHoras.textProperty().addListener((obs, antigo, novo) -> {
            campoDuracaoHoras.getStyleClass().remove(CLASSE_CAMPO_COM_ERRO);
            esconderErroLabel(erroDuracao);
            atualizarDiagnosticoViabilidade();
        });

        campoDescansoHoras.textProperty().addListener((obs, antigo, novo) -> {
            campoDescansoHoras.getStyleClass().remove(CLASSE_CAMPO_COM_ERRO);
            esconderErroLabel(erroDescanso);
            atualizarDiagnosticoViabilidade();
        });

        campoMinAgentes.textProperty().addListener((obs, antigo, novo) -> {
            campoMinAgentes.getStyleClass().remove(CLASSE_CAMPO_COM_ERRO);
            esconderErroLabel(erroMinAgentes);
            atualizarDiagnosticoViabilidade();
        });

        campoMaxAgentes.textProperty().addListener((obs, antigo, novo) -> {
            campoMaxAgentes.getStyleClass().remove(CLASSE_CAMPO_COM_ERRO);
            esconderErroLabel(erroMaxAgentes);
        });

        botaoSalvar.setOnAction(e -> salvarTipoTurno());
        botaoLimpar.setOnAction(e -> prepararNovoCadastro());
    }

    private void carregarDados() {
        FiltroStatusTurno filtro = comboFiltroStatus.getValue();
        Boolean statusAtivo = filtro != null ? filtro.getValorAtivo() : null;

        try {
            List<TipoTurno> lista = tipoTurnoService.listar(statusAtivo);
            listaTiposTurno.setAll(lista);
            atualizarContador(lista.size());
        } catch (Exception e) {
            contadorRegistros.setText("Erro ao carregar tipos de turno.");
        }
    }

    private void atualizarContador(int total) {
        if (total == 0) {
            contadorRegistros.setText("Nenhum tipo de turno encontrado.");
        } else if (total == 1) {
            contadorRegistros.setText("1 tipo de turno cadastrado.");
        } else {
            contadorRegistros.setText(total + " tipos de turno cadastrados.");
        }
    }

    private void atualizarDiagnosticoViabilidade() {
        try {
            BigDecimal duracao = parseBigDecimal(campoDuracaoHoras.getText());
            BigDecimal descanso = parseBigDecimal(campoDescansoHoras.getText());
            int minAgentes = parseInt(campoMinAgentes.getText(), 1);

            ResultadoViabilidadeTurno resultado = tipoTurnoService.verificarViabilidade(duracao, descanso, minAgentes);
            labelDiagnosticoViabilidade.setText(resultado.mensagem());
            labelDiagnosticoViabilidade.getStyleClass().removeAll("selo-sucesso", "selo-atencao", "selo-perigo");
            if (resultado.viavel()) {
                labelDiagnosticoViabilidade.getStyleClass().add("selo-sucesso");
            } else {
                labelDiagnosticoViabilidade.getStyleClass().add("selo-atencao");
            }
        } catch (Exception e) {
            labelDiagnosticoViabilidade.setText("Informe os valores de duração e mínimo de agentes para análise de viabilidade.");
            labelDiagnosticoViabilidade.getStyleClass().removeAll("selo-sucesso", "selo-atencao", "selo-perigo");
            labelDiagnosticoViabilidade.getStyleClass().add("selo-neutro");
        }
    }

    private void prepararNovoCadastro() {
        idTipoTurnoEmEdicao = null;
        tituloFormulario.setText("Novo Tipo de Turno");
        campoNome.clear();
        campoHoraInicio.setText("08:00");
        campoDuracaoHoras.setText("24");
        campoDescansoHoras.setText("72");
        campoMinAgentes.setText("2");
        campoMaxAgentes.clear();
        checkContaBancoHoras.setSelected(true);
        checkAtivo.setSelected(true);
        botaoLimpar.setText("Limpar");
        limparErrosFormulario();
        tabela.getSelectionModel().clearSelection();
        atualizarDiagnosticoViabilidade();
        campoNome.requestFocus();
    }

    private void carregarNoFormulario(TipoTurno tipoTurno) {
        if (tipoTurno == null) {
            return;
        }
        idTipoTurnoEmEdicao = tipoTurno.getId();
        tituloFormulario.setText("Editar: " + tipoTurno.getNome());
        campoNome.setText(tipoTurno.getNome());
        campoHoraInicio.setText(tipoTurno.getHoraInicio() != null ? tipoTurno.getHoraInicio().format(HORA_FORMATTER) : "");
        campoDuracaoHoras.setText(tipoTurno.getDuracaoHoras() != null ? tipoTurno.getDuracaoHoras().toPlainString() : "");
        campoDescansoHoras.setText(tipoTurno.getIntervaloDescansoHoras() != null ? tipoTurno.getIntervaloDescansoHoras().toPlainString() : "");
        campoMinAgentes.setText(String.valueOf(tipoTurno.getMinAgentes()));
        campoMaxAgentes.setText(tipoTurno.getMaxAgentes() != null ? String.valueOf(tipoTurno.getMaxAgentes()) : "");
        checkContaBancoHoras.setSelected(tipoTurno.isContaBancoHoras());
        checkAtivo.setSelected(tipoTurno.isAtivo());
        botaoLimpar.setText("Cancelar");
        limparErrosFormulario();
        atualizarDiagnosticoViabilidade();
    }

    private void salvarTipoTurno() {
        limparErrosFormulario();

        String nome = campoNome.getText();
        String horaTexto = campoHoraInicio.getText();
        String duracaoTexto = campoDuracaoHoras.getText();
        String descansoTexto = campoDescansoHoras.getText();
        String minTexto = campoMinAgentes.getText();
        String maxTexto = campoMaxAgentes.getText();
        boolean contaBancoHoras = checkContaBancoHoras.isSelected();
        boolean ativo = checkAtivo.isSelected();

        LocalTime horaInicio = null;
        if (horaTexto != null && !horaTexto.isBlank()) {
            try {
                horaInicio = LocalTime.parse(horaTexto.trim(), HORA_FORMATTER);
            } catch (DateTimeParseException e) {
                marcarErroCampo(campoHoraInicio, erroHoraInicio, "Formato de hora inválido (utilize HH:mm, ex: 08:00).");
                return;
            }
        }

        BigDecimal duracao = parseBigDecimal(duracaoTexto);
        BigDecimal descanso = parseBigDecimal(descansoTexto);
        int minAgentes = parseInt(minTexto, -1);
        Integer maxAgentes = (maxTexto != null && !maxTexto.isBlank()) ? parseInt(maxTexto, -1) : null;

        try {
            if (idTipoTurnoEmEdicao == null) {
                tipoTurnoService.cadastrar(nome, horaInicio, duracao, descanso, minAgentes, maxAgentes, contaBancoHoras, ativo);
                exibirSucessoCard("Tipo de turno cadastrado com sucesso!");
                prepararNovoCadastro();
            } else {
                tipoTurnoService.atualizar(idTipoTurnoEmEdicao, nome, horaInicio, duracao, descanso, minAgentes, maxAgentes, contaBancoHoras, ativo);
                exibirSucessoCard("Tipo de turno atualizado com sucesso!");
                prepararNovoCadastro();
            }
            carregarDados();
        } catch (RegraTipoTurnoException excecao) {
            tratarErroValidacao(excecao.getMessage());
        } catch (Exception excecao) {
            exibirErroCard("Erro ao salvar tipo de turno: " + excecao.getMessage());
        }
    }

    private void tratarErroValidacao(String mensagem) {
        String msgLower = mensagem.toLowerCase();
        if (msgLower.contains("nome")) {
            marcarErroCampo(campoNome, erroNome, mensagem);
        } else if (msgLower.contains("início") || msgLower.contains("inicio") || msgLower.contains("hora")) {
            marcarErroCampo(campoHoraInicio, erroHoraInicio, mensagem);
        } else if (msgLower.contains("duração") || msgLower.contains("duracao")) {
            marcarErroCampo(campoDuracaoHoras, erroDuracao, mensagem);
        } else if (msgLower.contains("descanso")) {
            marcarErroCampo(campoDescansoHoras, erroDescanso, mensagem);
        } else if (msgLower.contains("mínimo") || msgLower.contains("minimo")) {
            marcarErroCampo(campoMinAgentes, erroMinAgentes, mensagem);
        } else if (msgLower.contains("máximo") || msgLower.contains("maximo")) {
            marcarErroCampo(campoMaxAgentes, erroMaxAgentes, mensagem);
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
        campoHoraInicio.getStyleClass().remove(CLASSE_CAMPO_COM_ERRO);
        campoDuracaoHoras.getStyleClass().remove(CLASSE_CAMPO_COM_ERRO);
        campoDescansoHoras.getStyleClass().remove(CLASSE_CAMPO_COM_ERRO);
        campoMinAgentes.getStyleClass().remove(CLASSE_CAMPO_COM_ERRO);
        campoMaxAgentes.getStyleClass().remove(CLASSE_CAMPO_COM_ERRO);

        esconderErroLabel(erroNome);
        esconderErroLabel(erroHoraInicio);
        esconderErroLabel(erroDuracao);
        esconderErroLabel(erroDescanso);
        esconderErroLabel(erroMinAgentes);
        esconderErroLabel(erroMaxAgentes);

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

    private void confirmarEDesativar(TipoTurno item) {
        Alert alerta = new Alert(Alert.AlertType.CONFIRMATION);
        alerta.setTitle("Confirmar Inativação");
        alerta.setHeaderText("Deseja inativar o tipo de turno '" + item.getNome() + "'?");
        alerta.setContentText("A inativação não afetará os turnos já gerados na escala, mas impedirá novas alocações deste regime.");

        alerta.showAndWait().ifPresent(resposta -> {
            if (resposta == ButtonType.OK) {
                try {
                    tipoTurnoService.desativar(item.getId());
                    carregarDados();
                    if (idTipoTurnoEmEdicao != null && idTipoTurnoEmEdicao.equals(item.getId())) {
                        prepararNovoCadastro();
                    }
                } catch (Exception e) {
                    mostrarErroDialogo("Erro ao inativar", "Não foi possível inativar o tipo de turno: " + e.getMessage());
                }
            }
        });
    }

    private void reativarTipoTurno(TipoTurno item) {
        try {
            tipoTurnoService.ativar(item.getId());
            carregarDados();
            if (idTipoTurnoEmEdicao != null && idTipoTurnoEmEdicao.equals(item.getId())) {
                prepararNovoCadastro();
            }
        } catch (Exception e) {
            mostrarErroDialogo("Erro ao reativar", "Não foi possível reativar o tipo de turno: " + e.getMessage());
        }
    }

    private void mostrarErroDialogo(String titulo, String mensagem) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Sistema de Escala");
        alert.setHeaderText(titulo);
        alert.setContentText(mensagem);
        alert.showAndWait();
    }

    private BigDecimal parseBigDecimal(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(texto.trim().replace(',', '.'));
        } catch (Exception e) {
            return null;
        }
    }

    private int parseInt(String texto, int padrao) {
        if (texto == null || texto.isBlank()) {
            return padrao;
        }
        try {
            return Integer.parseInt(texto.trim());
        } catch (Exception e) {
            return padrao;
        }
    }
}

