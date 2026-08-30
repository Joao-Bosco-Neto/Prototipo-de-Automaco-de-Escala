package br.edu.sistemaescala.frontend.controller;

import java.time.Month;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

import br.edu.sistemaescala.backend.model.LancamentoHoras;
import br.edu.sistemaescala.backend.service.BancoHorasListagemItem;
import br.edu.sistemaescala.backend.service.BancoHorasService;
import br.edu.sistemaescala.backend.service.RegraBancoHorasException;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

/**
 * Controller da tela de Banco de Horas.
 *
 * <p>Espelha a estrutura da tela de Funcionários ({@link FuncionarioController}):
 * um {@code HBox} com a {@code TableView} expansível à esquerda e um card lateral
 * à direita que alterna entre o <b>Extrato</b> e o formulário de <b>Ajuste
 * manual</b>.</p>
 *
 * <p>A barra de filtros seleciona Mês/Ano e recorta tanto as colunas de métricas
 * (plantões e coberturas) quanto a coluna <b>Saldo</b> e o <b>Extrato</b> do card
 * lateral. Marcar <b>"Ver todo o histórico"</b> remove o recorte e mostra o
 * acumulado de {@code lancamento_horas}.</p>
 */
public class BancoHorasController {

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    private final BancoHorasService bancoHorasService;
    private final ObservableList<BancoHorasListagemItem> listaExibicao = FXCollections.observableArrayList();

    private final TableView<BancoHorasListagemItem> tabela = new TableView<>();
    private final ComboBox<Month> comboMes = new ComboBox<>();
    private final ComboBox<Integer> comboAno = new ComboBox<>();
    private final CheckBox chkVerTudo = new CheckBox("Ver todo o histórico");
    private final Label contadorRegistros = new Label();

    // Card lateral: um único card com o conteúdo trocado conforme o modo.
    private final VBox painelLateral = new VBox(12);
    private final Label tituloLateral = new Label("Extrato");
    private final VBox corpoLateral = new VBox(10);

    // Formulário de ajuste manual
    private final ComboBox<String> comboTipoAjuste = new ComboBox<>();
    private final TextField campoHoras = new TextField();
    private final TextArea campoJustificativa = new TextArea();
    private final Label mensagemAjuste = new Label();


    public BancoHorasController(BancoHorasService bancoHorasService) {
        this.bancoHorasService = bancoHorasService;
    }

    public Parent criarTela() {
        VBox raiz = new VBox(18);
        raiz.setPadding(new Insets(24));
        raiz.getStyleClass().add("area-conteudo");

        HBox conteudoPrincipal = criarConteudoPrincipal();
        raiz.getChildren().addAll(criarCabecalho(), conteudoPrincipal);
        VBox.setVgrow(conteudoPrincipal, Priority.ALWAYS);

        configurarEventos();
        prepararSeletorMesAno();
        carregarDados();
        mostrarLateralVazio();

        return raiz;
    }

    private VBox criarCabecalho() {
        Label titulo = new Label("Banco de horas");
        titulo.getStyleClass().add("titulo-1");

        Label subtitulo = new Label(
                "Métricas de plantões e coberturas e o saldo do banco de horas de cada "
                + "funcionário no mês selecionado — ou em todo o histórico.");
        subtitulo.getStyleClass().add("texto-secundario");
        subtitulo.setWrapText(true);

        return new VBox(4, titulo, subtitulo);
    }

    private HBox criarConteudoPrincipal() {
        VBox painelTabela = criarPainelListagem();
        VBox painelForm = criarCardLateral();

        HBox.setHgrow(painelTabela, Priority.ALWAYS);

        HBox layout = new HBox(20, painelTabela, painelForm);
        layout.setAlignment(Pos.TOP_LEFT);
        return layout;
    }

    private VBox criarPainelListagem() {
        VBox painel = new VBox(14);
        painel.getStyleClass().add("card");

        configurarColunasTabela();
        tabela.setItems(listaExibicao);
        tabela.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        VBox.setVgrow(tabela, Priority.ALWAYS);

        contadorRegistros.getStyleClass().add("texto-secundario");
        HBox rodape = new HBox(contadorRegistros);
        rodape.setAlignment(Pos.CENTER_LEFT);

        Label notaSaldo = new Label(
                "Saldo positivo indica horas a compensar em favor do funcionário; negativo, "
                + "horas devidas. Todas as colunas — inclusive o saldo — consideram o mês "
                + "selecionado; marque \"Ver todo o histórico\" para o acumulado de lançamentos.");
        notaSaldo.getStyleClass().add("texto-secundario");
        notaSaldo.setWrapText(true);

        painel.getChildren().addAll(criarBarraFiltros(), tabela, rodape, notaSaldo);
        return painel;
    }

    private HBox criarBarraFiltros() {
        Label rotulo = new Label("Mês de referência");
        rotulo.getStyleClass().add("texto-secundario");

        comboMes.setItems(FXCollections.observableArrayList(Month.values()));
        comboMes.setConverter(new StringConverter<>() {
            @Override
            public String toString(Month mes) {
                if (mes == null) {
                    return "";
                }
                String nome = mes.getDisplayName(TextStyle.FULL, PT_BR);
                return nome.substring(0, 1).toUpperCase(PT_BR) + nome.substring(1);
            }

            @Override
            public Month fromString(String texto) {
                return null;
            }
        });
        comboMes.setPrefWidth(140);

        int anoAtual = YearMonth.now().getYear();
        for (int ano = anoAtual - 3; ano <= anoAtual + 1; ano++) {
            comboAno.getItems().add(ano);
        }
        comboAno.setPrefWidth(100);

        Region espacador = new Region();
        HBox.setHgrow(espacador, Priority.ALWAYS);

        HBox barra = new HBox(10, rotulo, comboMes, comboAno, chkVerTudo, espacador);
        barra.setAlignment(Pos.CENTER_LEFT);
        return barra;
    }

    private void configurarColunasTabela() {
        TableColumn<BancoHorasListagemItem, String> colMatricula = new TableColumn<>("Matrícula");
        colMatricula.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().matricula()));
        colMatricula.setPrefWidth(100);

        TableColumn<BancoHorasListagemItem, String> colNome = new TableColumn<>("Funcionário");
        colNome.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().nome()));
        colNome.setPrefWidth(180);

        tabela.getColumns().add(colMatricula);
        tabela.getColumns().add(colNome);
        tabela.getColumns().add(colunaNumerica("Plantões cumpridos", BancoHorasListagemItem::plantoesCumpridos));
        tabela.getColumns().add(colunaNumerica("Coberturas feitas", BancoHorasListagemItem::coberturasFeitas));
        tabela.getColumns().add(colunaNumerica("Plantões cobertos", BancoHorasListagemItem::plantoesCobertos));

        TableColumn<BancoHorasListagemItem, Long> colSaldo = new TableColumn<>("Saldo");
        colSaldo.setCellValueFactory(d -> new SimpleLongProperty(d.getValue().saldoMinutos()).asObject());
        colSaldo.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Long minutos, boolean empty) {
                super.updateItem(minutos, empty);
                if (empty || minutos == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                Label selo = new Label(formatarSaldo(minutos));
                selo.getStyleClass().add("selo");
                if (minutos > 0) {
                    selo.getStyleClass().add("selo-sucesso");
                } else if (minutos < 0) {
                    selo.getStyleClass().add("selo-perigo");
                } else {
                    selo.getStyleClass().add("selo-neutro");
                }
                setText(null);
                setGraphic(selo);
                setAlignment(Pos.CENTER);
            }
        });
        colSaldo.setPrefWidth(110);
        tabela.getColumns().add(colSaldo);

        TableColumn<BancoHorasListagemItem, Void> colAcoes = new TableColumn<>("Ações");
        colAcoes.setCellFactory(col -> new TableCell<>() {
            private final javafx.scene.control.Button botaoExtrato = new javafx.scene.control.Button("Ver extrato");
            private final javafx.scene.control.Button botaoEditar = new javafx.scene.control.Button("Editar");
            private final HBox container = new HBox(6, botaoExtrato, botaoEditar);

            {
                botaoExtrato.getStyleClass().add("button-secundario");
                botaoEditar.getStyleClass().add("button-secundario");
                container.setAlignment(Pos.CENTER);
                botaoExtrato.setOnAction(e -> mostrarExtrato(getTableView().getItems().get(getIndex())));
                botaoEditar.setOnAction(e -> mostrarEdicao(getTableView().getItems().get(getIndex())));
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty || getIndex() >= getTableView().getItems().size() ? null : container);
            }
        });
        colAcoes.setPrefWidth(170);
        tabela.getColumns().add(colAcoes);
    }

    private TableColumn<BancoHorasListagemItem, Number> colunaNumerica(
            String titulo, java.util.function.ToIntFunction<BancoHorasListagemItem> valor) {
        TableColumn<BancoHorasListagemItem, Number> coluna = new TableColumn<>(titulo);
        coluna.setCellValueFactory(d -> new SimpleLongProperty(valor.applyAsInt(d.getValue())));
        coluna.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.toString());
                setAlignment(Pos.CENTER);
            }
        });
        coluna.setPrefWidth(120);
        return coluna;
    }

    // -----------------------------------------------------------------
    // Card lateral
    // -----------------------------------------------------------------

    private VBox criarCardLateral() {
        painelLateral.getStyleClass().add("card");
        painelLateral.setPrefWidth(360);
        painelLateral.setMinWidth(320);

        tituloLateral.getStyleClass().add("titulo-2");
        painelLateral.getChildren().addAll(tituloLateral, corpoLateral);

        // Componentes fixos do formulário de ajuste (montados uma vez).
        comboTipoAjuste.setItems(FXCollections.observableArrayList(
                "Crédito (adiciona horas)", "Débito (remove horas)"));
        comboTipoAjuste.getSelectionModel().selectFirst();
        comboTipoAjuste.setMaxWidth(Double.MAX_VALUE);

        campoHoras.setPromptText("Ex: 2 ou 2.5");

        campoJustificativa.setPromptText("Motivo do ajuste manual...");
        campoJustificativa.setPrefRowCount(3);
        campoJustificativa.setWrapText(true);

        mensagemAjuste.getStyleClass().add("selo");
        mensagemAjuste.setWrapText(true);
        mensagemAjuste.setVisible(false);
        mensagemAjuste.setManaged(false);

        return painelLateral;
    }

    private void mostrarLateralVazio() {
        tituloLateral.setText("Extrato");
        corpoLateral.getChildren().setAll(textoSecundario(
                "Selecione \"Ver extrato\" ou \"Editar\" numa linha da tabela."));
    }

    private void mostrarExtrato(BancoHorasListagemItem item) {
        if (item == null) {
            return;
        }
        YearMonth periodo = mesSelecionado();
        tituloLateral.setText("Extrato — " + item.nome()
                + (periodo != null ? " (" + rotuloMes(periodo) + ")" : ""));

        VBox lista = new VBox(8);
        try {
            List<LancamentoHoras> extrato = bancoHorasService.buscarExtrato(item.funcionarioId(), periodo);
            if (extrato.isEmpty()) {
                lista.getChildren().add(textoSecundario(periodo != null
                        ? "Nenhum lançamento neste mês."
                        : "Nenhum lançamento registrado."));
            } else {
                for (LancamentoHoras lancamento : extrato) {
                    lista.getChildren().add(linhaExtrato(lancamento));
                }
            }
        } catch (RuntimeException e) {
            lista.getChildren().add(textoSecundario("Não foi possível carregar o extrato."));
        }

        Label total = new Label((periodo != null ? "Saldo no mês: " : "Saldo consolidado: ")
                + formatarSaldo(item.saldoMinutos()));
        total.getStyleClass().add("texto-secundario");

        ScrollPane rolagem = new ScrollPane(lista);
        rolagem.setFitToWidth(true);
        rolagem.setPrefViewportHeight(360);
        rolagem.getStyleClass().add("painel-atribuicao-rolagem");

        javafx.scene.control.Button botaoAjuste = new javafx.scene.control.Button("Novo ajuste manual");
        botaoAjuste.getStyleClass().add("button-secundario");
        botaoAjuste.setOnAction(e -> mostrarEdicao(item));

        corpoLateral.getChildren().setAll(total, rolagem, botaoAjuste);
    }

    private VBox linhaExtrato(LancamentoHoras lancamento) {
        String data = lancamento.getDataReferencia() != null ? lancamento.getDataReferencia().format(DATA) : "-";
        Label cabecalho = new Label(data + "   " + formatarSaldo(lancamento.getMinutos()));
        cabecalho.getStyleClass().add("painel-turno-titulo");

        Label descricao = new Label(lancamento.getDescricao() != null && !lancamento.getDescricao().isBlank()
                ? lancamento.getDescricao()
                : descreverTipo(lancamento));
        descricao.getStyleClass().add("texto-secundario");
        descricao.setWrapText(true);

        VBox linha = new VBox(2, cabecalho, descricao);
        linha.getStyleClass().add("painel-linha-agente");
        return linha;
    }

    private String descreverTipo(LancamentoHoras lancamento) {
        if (lancamento.getTipo() == null) {
            return "Lançamento";
        }
        return switch (lancamento.getTipo()) {
            case CREDITO_COBERTURA -> "Crédito por cobertura";
            case DEBITO_AUSENCIA -> "Débito por ausência coberta";
            case CREDITO_EXTRA -> "Crédito extra";
            case AJUSTE_MANUAL -> "Ajuste manual";
        };
    }

    private void mostrarEdicao(BancoHorasListagemItem item) {
        if (item == null) {
            return;
        }
        tituloLateral.setText("Ajuste manual — " + item.nome());

        campoHoras.clear();
        campoJustificativa.clear();
        comboTipoAjuste.getSelectionModel().selectFirst();
        esconderMensagemAjuste();

        javafx.scene.control.Button salvar = new javafx.scene.control.Button("Salvar ajuste");
        salvar.getStyleClass().add("button-primario");
        salvar.setOnAction(e -> salvarAjuste(item));

        javafx.scene.control.Button cancelar = new javafx.scene.control.Button("Cancelar");
        cancelar.getStyleClass().add("button-secundario");
        cancelar.setOnAction(e -> mostrarExtrato(item));

        HBox botoes = new HBox(10, salvar, cancelar);
        botoes.setAlignment(Pos.CENTER_RIGHT);

        corpoLateral.getChildren().setAll(
                new Label("Tipo de ajuste"), comboTipoAjuste,
                new Label("Horas"), campoHoras,
                new Label("Justificativa *"), campoJustificativa,
                mensagemAjuste,
                botoes);
    }

    private void salvarAjuste(BancoHorasListagemItem item) {
        esconderMensagemAjuste();

        double horas;
        try {
            horas = Double.parseDouble(campoHoras.getText().trim().replace(',', '.'));
        } catch (NullPointerException | NumberFormatException e) {
            exibirMensagemAjuste("Informe as horas em número (ex: 2 ou 2.5).", "selo-perigo");
            return;
        }

        boolean credito = comboTipoAjuste.getSelectionModel().getSelectedIndex() == 0;
        try {
            bancoHorasService.lancarAjusteManual(item.funcionarioId(), horas, credito, campoJustificativa.getText());
            carregarDados();
            BancoHorasListagemItem atualizado = buscarNaLista(item.funcionarioId());
            mostrarExtrato(atualizado != null ? atualizado : item);
        } catch (RegraBancoHorasException e) {
            exibirMensagemAjuste(e.getMessage(), "selo-perigo");
        } catch (RuntimeException e) {
            exibirMensagemAjuste("Não foi possível salvar o ajuste.", "selo-perigo");
        }
    }

    // -----------------------------------------------------------------
    // Dados e eventos
    // -----------------------------------------------------------------

    private void configurarEventos() {
        comboMes.valueProperty().addListener((obs, antigo, novo) -> carregarDados());
        comboAno.valueProperty().addListener((obs, antigo, novo) -> carregarDados());
        chkVerTudo.selectedProperty().addListener((obs, antigo, novo) -> {
            comboMes.setDisable(novo);
            comboAno.setDisable(novo);
            carregarDados();
        });
    }

    private void prepararSeletorMesAno() {
        YearMonth agora = YearMonth.now();
        comboMes.setValue(agora.getMonth());
        comboAno.setValue(agora.getYear());
    }

    /** Mês/ano do filtro, ou {@code null} quando "Ver todo o histórico" está marcado. */
    private YearMonth mesSelecionado() {
        if (chkVerTudo.isSelected()) {
            return null;
        }
        Month mes = comboMes.getValue() != null ? comboMes.getValue() : YearMonth.now().getMonth();
        Integer ano = comboAno.getValue() != null ? comboAno.getValue() : YearMonth.now().getYear();
        return YearMonth.of(ano, mes);
    }

    private String rotuloMes(YearMonth mes) {
        String nome = mes.getMonth().getDisplayName(TextStyle.FULL, PT_BR);
        return nome.substring(0, 1).toUpperCase(PT_BR) + nome.substring(1) + "/" + mes.getYear();
    }

    private void carregarDados() {
        try {
            List<BancoHorasListagemItem> itens = bancoHorasService.listarMensal(mesSelecionado());
            tabela.getSelectionModel().clearSelection();
            listaExibicao.setAll(itens);
            // Sem o refresh o JavaFX reaproveita células cujo valor "não mudou"
            // entre recargas e a linha fica com o número do filtro anterior.
            tabela.refresh();
            contadorRegistros.setText(itens.isEmpty()
                    ? "Nenhum funcionário encontrado."
                    : itens.size() + (itens.size() == 1 ? " funcionário." : " funcionários."));
        } catch (RuntimeException e) {
            listaExibicao.clear();
            contadorRegistros.setText("Erro ao carregar o banco de horas.");
        }
    }

    private BancoHorasListagemItem buscarNaLista(int funcionarioId) {
        return listaExibicao.stream()
                .filter(i -> i.funcionarioId() == funcionarioId)
                .findFirst()
                .orElse(null);
    }

    // -----------------------------------------------------------------
    // Apoio
    // -----------------------------------------------------------------

    private static String formatarSaldo(long minutos) {
        String sinal = minutos > 0 ? "+" : minutos < 0 ? "-" : "";
        long abs = Math.abs(minutos);
        long horas = abs / 60;
        long resto = abs % 60;
        return sinal + horas + "h" + (resto > 0 ? String.format("%02d", resto) : "");
    }

    private Label textoSecundario(String texto) {
        Label label = new Label(texto);
        label.getStyleClass().add("texto-secundario");
        label.setWrapText(true);
        return label;
    }

    private void exibirMensagemAjuste(String texto, String classeSelo) {
        mensagemAjuste.setText(texto);
        mensagemAjuste.getStyleClass().removeAll("selo-sucesso", "selo-perigo", "selo-atencao");
        mensagemAjuste.getStyleClass().add(classeSelo);
        mensagemAjuste.setVisible(true);
        mensagemAjuste.setManaged(true);
    }

    private void esconderMensagemAjuste() {
        mensagemAjuste.setText("");
        mensagemAjuste.setVisible(false);
        mensagemAjuste.setManaged(false);
    }
}
