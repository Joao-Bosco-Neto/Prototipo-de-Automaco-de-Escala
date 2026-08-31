package br.edu.sistemaescala.frontend.controller;

import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

import br.edu.sistemaescala.backend.model.EscalaFuncionario;
import br.edu.sistemaescala.backend.model.EscalaTurno;
import br.edu.sistemaescala.backend.model.MotivoCobertura;
import br.edu.sistemaescala.backend.repository.RepositoryException;
import br.edu.sistemaescala.backend.service.CoberturaListagemItem;
import br.edu.sistemaescala.backend.service.CoberturaService;
import br.edu.sistemaescala.backend.service.RegraCoberturaException;
import br.edu.sistemaescala.backend.service.SubstitutoDisponivel;
import br.edu.sistemaescala.frontend.DialogUtil;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

/**
 * Tela de coberturas de plantão: listagem mensal do que já foi registrado mais
 * o formulário de registro/edição (issues #33 e #34, "conforme a tela 4").
 *
 * <p>Segue o mesmo desenho do Banco de Horas: um {@code HBox} com a
 * {@code TableView} expansível à esquerda e um card lateral à direita que
 * alterna entre o estado vazio, o formulário de <b>nova cobertura</b> e o de
 * <b>edição</b> da linha escolhida na tabela. A barra de filtros recorta a
 * tabela por mês/ano.</p>
 *
 * <p>O controller só trata evento visual. Quem valida, grava e estorna é o
 * {@link CoberturaService}: a exclusão apenas confirma com o usuário e chama
 * {@code excluir()} — o par crédito/débito no banco de horas sai junto pela
 * cascata do schema, sem nenhum delete de lançamento aqui.</p>
 */
public class CoberturaController {

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    private static final String SEM_VALOR = "—";

    private final CoberturaService coberturaService;
    private final ObservableList<CoberturaListagemItem> listaExibicao = FXCollections.observableArrayList();

    // Listagem
    private final TableView<CoberturaListagemItem> tabela = new TableView<>();
    private final ComboBox<Month> comboMes = new ComboBox<>();
    private final ComboBox<Integer> comboAno = new ComboBox<>();
    private final Label contadorRegistros = new Label();
    private final Button botaoNova = new Button("+ Nova cobertura");

    // Card lateral: um único card com o corpo trocado conforme o modo.
    private final VBox painelLateral = new VBox(12);
    private final Label tituloLateral = new Label();
    private final VBox corpoLateral = new VBox(10);

    // Formulário — os mesmos campos servem ao registro e à edição.
    private final DatePicker campoData = new DatePicker();
    private final ComboBox<EscalaFuncionario> comboAusente = new ComboBox<>();
    private final Label ajudaAusente = new Label("Escolha a data para listar quem está escalado.");
    private final ComboBox<SubstitutoDisponivel> comboSubstituto = new ComboBox<>();
    private final ComboBox<MotivoCobertura> comboMotivo = new ComboBox<>();
    private final TextArea campoObservacoes = new TextArea();
    private final CheckBox checkBancoHoras = new CheckBox();
    private final Label labelMensagem = new Label();
    private final Button botaoSalvar = new Button();
    private final Button botaoCancelar = new Button("Cancelar");

    /** Linha em edição; {@code null} quando o formulário está no modo de registro. */
    private CoberturaListagemItem emEdicao;

    /**
     * Trava os listeners dos combos enquanto o código preenche o formulário.
     *
     * <p>Sem ela, preencher a data na edição dispararia
     * {@code recarregarAusentes()} e limparia o substituto que acabou de ser
     * selecionado.</p>
     */
    private boolean preenchendoFormulario;

    public CoberturaController(CoberturaService coberturaService) {
        this.coberturaService = coberturaService;
    }

    public Parent criarTela() {
        VBox raiz = new VBox(18);
        raiz.setPadding(new Insets(24));
        raiz.getStyleClass().add("area-conteudo");

        HBox conteudoPrincipal = criarConteudoPrincipal();
        raiz.getChildren().addAll(criarCabecalho(), conteudoPrincipal);
        VBox.setVgrow(conteudoPrincipal, Priority.ALWAYS);

        configurarCampos();
        configurarEventos();
        carregarMotivos();
        prepararSeletorMesAno();
        carregarDados();
        mostrarLateralVazio(null);

        return raiz;
    }

    private VBox criarCabecalho() {
        Label titulo = new Label("Cobertura de plantão");
        titulo.getStyleClass().add("titulo-1");

        Label subtitulo = new Label(
                "Registro das vezes em que um funcionário assumiu o turno de outro. "
                + "Excluir uma cobertura estorna os lançamentos que ela gerou no banco de horas.");
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

    // -----------------------------------------------------------------
    // Painel da listagem
    // -----------------------------------------------------------------

    private VBox criarPainelListagem() {
        VBox painel = new VBox(14);
        painel.getStyleClass().add("card");

        configurarColunasTabela();
        tabela.setItems(listaExibicao);
        tabela.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        tabela.setPlaceholder(textoSecundario("Nenhuma cobertura registrada no mês selecionado."));
        VBox.setVgrow(tabela, Priority.ALWAYS);

        contadorRegistros.getStyleClass().add("texto-secundario");
        HBox rodape = new HBox(contadorRegistros);
        rodape.setAlignment(Pos.CENTER_LEFT);

        Label nota = textoSecundario(
                "Toda cobertura registrada é impressa como nota de rodapé na escala exportada "
                + "em PDF. Excluir uma cobertura desfaz o crédito de quem cobriu e o débito do "
                + "ausente, e a operação fica registrada no log de auditoria.");

        painel.getChildren().addAll(criarBarraFiltros(), tabela, rodape, nota);
        return painel;
    }

    private HBox criarBarraFiltros() {
        Label rotulo = new Label("Mês de referência");
        rotulo.getStyleClass().add("texto-secundario");
        // Sem o minWidth o HBox encolhe rótulo e combos abaixo do tamanho do
        // texto em janela estreita, e tudo vira reticências.
        rotulo.setMinWidth(Region.USE_PREF_SIZE);
        comboMes.setMinWidth(Region.USE_PREF_SIZE);
        comboAno.setMinWidth(Region.USE_PREF_SIZE);

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

        // Mesma barra das telas de Funcionários e Banco de Horas: filtros à
        // esquerda e a ação principal empurrada para a direita do card.
        botaoNova.getStyleClass().add("button-primario");
        botaoNova.setMinWidth(Region.USE_PREF_SIZE);

        Region espacador = new Region();
        HBox.setHgrow(espacador, Priority.ALWAYS);

        HBox barra = new HBox(10, rotulo, comboMes, comboAno, espacador, botaoNova);
        barra.setAlignment(Pos.CENTER_LEFT);
        return barra;
    }

    private void configurarColunasTabela() {
        TableColumn<CoberturaListagemItem, String> colData = new TableColumn<>("Data");
        colData.setCellValueFactory(linha -> new SimpleStringProperty(
                linha.getValue().dataPlantao() != null
                        ? linha.getValue().dataPlantao().format(DATA)
                        : SEM_VALOR));
        colData.setPrefWidth(110);

        TableColumn<CoberturaListagemItem, String> colAusente = new TableColumn<>("Ausente");
        colAusente.setCellValueFactory(linha -> new SimpleStringProperty(
                textoOuTraco(linha.getValue().nomeAusente())));
        colAusente.setPrefWidth(170);

        TableColumn<CoberturaListagemItem, String> colSubstituto = new TableColumn<>("Quem cobriu");
        colSubstituto.setCellValueFactory(linha -> new SimpleStringProperty(
                textoOuTraco(linha.getValue().nomeSubstituto())));
        colSubstituto.setPrefWidth(170);

        TableColumn<CoberturaListagemItem, String> colMotivo = new TableColumn<>("Motivo");
        colMotivo.setCellValueFactory(linha -> new SimpleStringProperty(
                textoOuTraco(linha.getValue().motivoDescricao())));
        colMotivo.setPrefWidth(140);

        TableColumn<CoberturaListagemItem, Boolean> colLancamento = new TableColumn<>("Lançamento");
        colLancamento.setCellValueFactory(linha ->
                new SimpleBooleanProperty(linha.getValue().lancouBancoHoras()));
        colLancamento.setCellFactory(coluna -> new TableCell<>() {
            @Override
            protected void updateItem(Boolean lancou, boolean vazio) {
                super.updateItem(lancou, vazio);
                if (vazio || lancou == null || getIndex() >= getTableView().getItems().size()) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                CoberturaListagemItem item = getTableView().getItems().get(getIndex());
                Label selo = new Label(lancou
                        ? "+" + formatarDuracao(item.minutosDoTurno())
                        : "sem lançamento");
                selo.getStyleClass().setAll("selo", lancou ? "selo-sucesso" : "selo-neutro");
                setText(null);
                setGraphic(selo);
                setAlignment(Pos.CENTER);
            }
        });
        // 160, e não os 130 do resto: abaixo disso o selo "sem lançamento"
        // sai cortado com reticências.
        colLancamento.setPrefWidth(160);

        TableColumn<CoberturaListagemItem, Void> colAcoes = new TableColumn<>("Ações");
        colAcoes.setCellFactory(coluna -> new TableCell<>() {
            private final Button botaoEditar = new Button("Editar");
            private final Button botaoExcluir = new Button("Excluir");
            private final HBox container = new HBox(6, botaoEditar, botaoExcluir);

            {
                botaoEditar.getStyleClass().add("button-secundario-compacto");
                botaoExcluir.getStyleClass().add("button-perigo-compacto");
                container.setAlignment(Pos.CENTER);
                botaoEditar.setOnAction(evento -> mostrarEdicao(getTableView().getItems().get(getIndex())));
                botaoExcluir.setOnAction(evento -> excluir(getTableView().getItems().get(getIndex())));
            }

            @Override
            protected void updateItem(Void item, boolean vazio) {
                super.updateItem(item, vazio);
                setGraphic(vazio || getIndex() >= getTableView().getItems().size() ? null : container);
            }
        });
        colAcoes.setPrefWidth(160);

        tabela.getColumns().add(colData);
        tabela.getColumns().add(colAusente);
        tabela.getColumns().add(colSubstituto);
        tabela.getColumns().add(colMotivo);
        tabela.getColumns().add(colLancamento);
        tabela.getColumns().add(colAcoes);
    }

    // -----------------------------------------------------------------
    // Card lateral
    // -----------------------------------------------------------------

    private VBox criarCardLateral() {
        painelLateral.getStyleClass().add("card");
        painelLateral.setPrefWidth(360);
        painelLateral.setMinWidth(320);

        tituloLateral.getStyleClass().add("titulo-2");
        tituloLateral.setWrapText(true);

        VBox.setVgrow(corpoLateral, Priority.ALWAYS);
        painelLateral.getChildren().addAll(tituloLateral, corpoLateral);
        return painelLateral;
    }

    /** Estado inicial do card: nada em edição, com o resultado da última ação quando houver. */
    private void mostrarLateralVazio(Label aviso) {
        emEdicao = null;
        tituloLateral.setText("Cobertura");

        Label instrucao = textoSecundario(
                "Clique em \"+ Nova cobertura\" para registrar, ou em \"Editar\" numa linha da tabela.");
        if (aviso == null) {
            corpoLateral.getChildren().setAll(instrucao);
        } else {
            corpoLateral.getChildren().setAll(aviso, instrucao);
        }
    }

    /** Formulário no modo de registro: data livre e combos recarregados do zero. */
    private void mostrarNovaCobertura() {
        emEdicao = null;
        tituloLateral.setText("Nova cobertura");
        botaoSalvar.setText("Registrar cobertura");
        montarCorpoDoFormulario();

        preenchendoFormulario = true;
        campoData.setDisable(false);
        comboAusente.setDisable(false);
        campoData.setValue(LocalDate.now());
        limparCamposDaCobertura();
        esconderMensagem();
        preenchendoFormulario = false;

        recarregarAusentes();
    }

    /**
     * Formulário no modo de edição, preenchido com a cobertura da linha.
     *
     * <p>A data e o ausente ficam travados: o plantão coberto é o que define o
     * turno, e trocá-lo seria registrar outra cobertura, não editar esta.</p>
     */
    private void mostrarEdicao(CoberturaListagemItem item) {
        if (item == null) {
            return;
        }
        EscalaFuncionario cobertura = item.cobertura();
        EscalaFuncionario alocacaoAusente = buscarAlocacaoDoAusente(item);
        if (alocacaoAusente == null) {
            return;
        }

        emEdicao = item;
        tituloLateral.setText("Editar cobertura");
        botaoSalvar.setText("Salvar alterações");
        montarCorpoDoFormulario();

        preenchendoFormulario = true;
        campoData.setValue(item.dataPlantao());
        campoData.setDisable(true);

        comboAusente.getItems().setAll(alocacaoAusente);
        comboAusente.setValue(alocacaoAusente);
        comboAusente.setDisable(true);
        ajudaAusente.setText("O plantão coberto não muda na edição.");

        comboMotivo.setValue(motivoPorId(cobertura.getMotivoCoberturaId()));
        campoObservacoes.setText(cobertura.getObservacao() != null ? cobertura.getObservacao() : "");
        checkBancoHoras.setSelected(cobertura.isLancouBancoHoras());
        esconderMensagem();
        preenchendoFormulario = false;

        // Lista de substitutos da edição: inclui quem cobre hoje, que a versão
        // de registro esconderia por já estar escalado no turno.
        try {
            comboSubstituto.getItems().setAll(
                    coberturaService.listarSubstitutos(alocacaoAusente, cobertura));
            comboSubstituto.setValue(substitutoAtual(cobertura));
        } catch (RepositoryException excecao) {
            DialogUtil.mostrarErroBancoIndisponivel("Não foi possível carregar os substitutos");
        }
    }

    /** Monta o corpo do card lateral com os campos do formulário (mesma ordem nos dois modos). */
    private void montarCorpoDoFormulario() {
        Label ajudaSubstituto = textoSecundario(
                "Qualquer funcionário pode cobrir. Quem está em descanso mínimo "
                + "ou com turno sobreposto aparece marcado.");
        Label ajudaBancoHoras = textoSecundario(
                "Crédito para quem cobre e débito para o coberto, conforme a duração do turno.");

        HBox barraBotoes = new HBox(10, botaoSalvar, botaoCancelar);
        barraBotoes.setAlignment(Pos.CENTER_RIGHT);

        VBox campos = new VBox(14,
                grupoCampo(new Label("Data do plantão *"), campoData),
                grupoCampo(new Label("Funcionário ausente *"), comboAusente, ajudaAusente),
                grupoCampo(new Label("Funcionário que irá cobrir *"), comboSubstituto, ajudaSubstituto),
                grupoCampo(new Label("Motivo"), comboMotivo),
                grupoCampo(new Label("Observações"), campoObservacoes),
                grupoCampo(linhaBancoHoras(), ajudaBancoHoras),
                labelMensagem);

        ScrollPane rolagem = new ScrollPane(campos);
        rolagem.setFitToWidth(true);
        rolagem.getStyleClass().add("painel-atribuicao-rolagem");
        VBox.setVgrow(rolagem, Priority.ALWAYS);

        corpoLateral.getChildren().setAll(rolagem, barraBotoes);
    }

    /**
     * Agrupa o rótulo e o controle de um campo com espaçamento curto entre si,
     * separando os campos entre si pelo espaçamento maior do card — mesmo padrão
     * da tela de Configurações da Organização.
     */
    private VBox grupoCampo(Node... nos) {
        for (Node no : nos) {
            // Um Label recém-criado já traz a classe "label"; o rótulo do campo é
            // todo Label que ainda não recebeu classe própria (os textos de ajuda
            // chegam aqui com "texto-secundario" e devem ficar como estão).
            if (no instanceof Label rotulo && !rotulo.getStyleClass().contains("texto-secundario")) {
                rotulo.getStyleClass().add("rotulo-campo");
            }
        }
        return new VBox(4, nos);
    }

    /** Deixa o texto "Lançar no banco de horas" ao lado da checkbox. */
    private HBox linhaBancoHoras() {
        Label rotulo = new Label("Lançar no banco de horas");
        rotulo.getStyleClass().add("rotulo-campo");
        HBox linha = new HBox(8, checkBancoHoras, rotulo);
        linha.setAlignment(Pos.CENTER_LEFT);
        return linha;
    }

    // -----------------------------------------------------------------
    // Configuração dos campos e eventos
    // -----------------------------------------------------------------

    private void configurarCampos() {
        campoData.setMaxWidth(Double.MAX_VALUE);

        ajudaAusente.getStyleClass().add("texto-secundario");
        ajudaAusente.setWrapText(true);

        comboAusente.setMaxWidth(Double.MAX_VALUE);
        comboAusente.setConverter(new StringConverter<>() {
            @Override
            public String toString(EscalaFuncionario alocacao) {
                if (alocacao == null) {
                    return "";
                }
                String nome = alocacao.getFuncionario() != null ? alocacao.getFuncionario().getNome() : "?";
                return nome + " — " + descreverTurno(alocacao.getEscalaTurno());
            }

            @Override
            public EscalaFuncionario fromString(String texto) {
                return null;
            }
        });

        comboSubstituto.setMaxWidth(Double.MAX_VALUE);
        comboSubstituto.setButtonCell(criarCelulaSubstituto());
        comboSubstituto.setCellFactory(lista -> criarCelulaSubstituto());

        comboMotivo.setMaxWidth(Double.MAX_VALUE);
        comboMotivo.setPromptText("Sem motivo específico");
        comboMotivo.setPlaceholder(new Label("Nenhum motivo cadastrado."));
        comboMotivo.setConverter(new StringConverter<>() {
            @Override
            public String toString(MotivoCobertura motivo) {
                return motivo == null ? "" : motivo.getNome();
            }

            @Override
            public MotivoCobertura fromString(String texto) {
                return null;
            }
        });

        campoObservacoes.setPromptText("Anotações livres sobre esta cobertura.");
        campoObservacoes.setWrapText(true);
        campoObservacoes.setPrefRowCount(3);

        // Critério de aceite: marcado por padrão desde a abertura da tela.
        checkBancoHoras.setSelected(true);

        labelMensagem.getStyleClass().add("selo");
        labelMensagem.setWrapText(true);
        labelMensagem.setVisible(false);
        labelMensagem.setManaged(false);

        botaoSalvar.getStyleClass().add("button-primario");
        botaoCancelar.getStyleClass().add("button-secundario");
    }

    /** Célula do combo de substitutos: nome à esquerda, selo de disponibilidade à direita. */
    private ListCell<SubstitutoDisponivel> criarCelulaSubstituto() {
        return new ListCell<>() {
            @Override
            protected void updateItem(SubstitutoDisponivel item, boolean vazio) {
                super.updateItem(item, vazio);
                if (vazio || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                Label selo = new Label(item.disponivel() ? "Disponível" : "Com restrição");
                selo.getStyleClass().setAll("selo", item.disponivel() ? "selo-sucesso" : "selo-atencao");
                if (!item.disponivel()) {
                    selo.setTooltip(new Tooltip(item.restricao()));
                }

                Region espacador = new Region();
                HBox.setHgrow(espacador, Priority.ALWAYS);

                HBox linha = new HBox(8, new Label(item.funcionario().getNome()), espacador, selo);
                linha.setAlignment(Pos.CENTER_LEFT);
                setText(null);
                setGraphic(linha);
            }
        };
    }

    private void configurarEventos() {
        comboMes.valueProperty().addListener((obs, antigo, novo) -> carregarDados());
        comboAno.valueProperty().addListener((obs, antigo, novo) -> carregarDados());

        campoData.valueProperty().addListener((obs, antigo, novo) -> {
            if (!preenchendoFormulario) {
                recarregarAusentes();
            }
        });
        comboAusente.valueProperty().addListener((obs, antigo, novo) -> {
            if (!preenchendoFormulario) {
                recarregarSubstitutos();
            }
        });

        botaoNova.setOnAction(evento -> mostrarNovaCobertura());
        botaoSalvar.setOnAction(evento -> salvar());
        botaoCancelar.setOnAction(evento -> mostrarLateralVazio(null));
    }

    // -----------------------------------------------------------------
    // Dados da listagem
    // -----------------------------------------------------------------

    private void prepararSeletorMesAno() {
        YearMonth agora = YearMonth.now();
        comboMes.setValue(agora.getMonth());
        comboAno.setValue(agora.getYear());
    }

    private YearMonth mesSelecionado() {
        Month mes = comboMes.getValue() != null ? comboMes.getValue() : YearMonth.now().getMonth();
        Integer ano = comboAno.getValue() != null ? comboAno.getValue() : YearMonth.now().getYear();
        return YearMonth.of(ano, mes);
    }

    private void carregarDados() {
        try {
            List<CoberturaListagemItem> itens = coberturaService.listarCoberturasParaListagem(mesSelecionado());
            tabela.getSelectionModel().clearSelection();
            listaExibicao.setAll(itens);
            // Sem o refresh o JavaFX reaproveita células cujo valor "não mudou"
            // entre recargas e a linha fica com o conteúdo do filtro anterior.
            tabela.refresh();
            contadorRegistros.setText(itens.isEmpty()
                    ? "Nenhuma cobertura no mês."
                    : itens.size() + (itens.size() == 1 ? " cobertura no mês." : " coberturas no mês."));
        } catch (RepositoryException excecao) {
            listaExibicao.clear();
            contadorRegistros.setText("Erro ao carregar as coberturas do mês.");
            DialogUtil.mostrarErroBancoIndisponivel("Não foi possível carregar as coberturas");
        }
    }

    // -----------------------------------------------------------------
    // Carregamento dinâmico dos combos do formulário
    // -----------------------------------------------------------------

    private void carregarMotivos() {
        try {
            comboMotivo.getItems().setAll(coberturaService.listarMotivos());
        } catch (RepositoryException excecao) {
            DialogUtil.mostrarErroBancoIndisponivel("Não foi possível carregar os motivos de cobertura");
        }
    }

    private void recarregarAusentes() {
        esconderMensagem();
        comboAusente.getItems().clear();
        comboSubstituto.getItems().clear();

        LocalDate data = campoData.getValue();
        if (data == null) {
            ajudaAusente.setText("Escolha a data para listar quem está escalado.");
            return;
        }
        try {
            List<EscalaFuncionario> escalados = coberturaService.listarEscaladosNaData(data);
            comboAusente.getItems().setAll(escalados);
            ajudaAusente.setText(escalados.isEmpty()
                    ? "Nenhum funcionário escalado nesta data."
                    : "Escalados no dia " + data.format(DATA) + ".");
        } catch (RepositoryException excecao) {
            DialogUtil.mostrarErroBancoIndisponivel("Não foi possível carregar os escalados da data");
        }
    }

    private void recarregarSubstitutos() {
        comboSubstituto.getItems().clear();

        EscalaFuncionario ausente = comboAusente.getValue();
        if (ausente == null) {
            return;
        }
        try {
            comboSubstituto.getItems().setAll(coberturaService.listarSubstitutos(ausente));
        } catch (RepositoryException excecao) {
            DialogUtil.mostrarErroBancoIndisponivel("Não foi possível carregar os substitutos");
        }
    }

    // -----------------------------------------------------------------
    // Registro e edição
    // -----------------------------------------------------------------

    private void salvar() {
        esconderMensagem();

        if (campoData.getValue() == null) {
            exibirErro("Escolha a data do plantão.");
            return;
        }
        EscalaFuncionario ausente = comboAusente.getValue();
        if (ausente == null) {
            exibirErro("Escolha o funcionário ausente.");
            return;
        }
        SubstitutoDisponivel substituto = comboSubstituto.getValue();
        if (substituto == null) {
            exibirErro("Escolha o funcionário que irá cobrir.");
            return;
        }
        if (!substituto.disponivel()) {
            exibirErro("O sistema bloqueia a seleção: " + substituto.restricao());
            return;
        }

        boolean editando = emEdicao != null;
        try {
            if (editando) {
                coberturaService.editar(emEdicao.cobertura(), ausente, substituto.funcionario(),
                        motivoSelecionadoId(), campoObservacoes.getText(), checkBancoHoras.isSelected());
                carregarDados();
                mostrarLateralVazio(selo("Cobertura atualizada.", "selo-sucesso"));
            } else {
                coberturaService.registrar(ausente, substituto.funcionario(), motivoSelecionadoId(),
                        campoObservacoes.getText(), checkBancoHoras.isSelected());
                carregarDados();
                // Registrar várias coberturas em sequência é o uso normal da
                // tela: o formulário fica aberto e limpo para a próxima.
                preenchendoFormulario = true;
                limparCamposDaCobertura();
                preenchendoFormulario = false;
                recarregarAusentes();
                exibirSucesso("Cobertura registrada para " + substituto.funcionario().getNome() + ".");
            }
        } catch (RegraCoberturaException excecao) {
            exibirErro(excecao.getMessage());
        } catch (RepositoryException excecao) {
            DialogUtil.mostrarErroBancoIndisponivel(editando
                    ? "Não foi possível salvar as alterações da cobertura"
                    : "Não foi possível registrar a cobertura");
        }
    }

    // -----------------------------------------------------------------
    // Exclusão
    // -----------------------------------------------------------------

    /**
     * Confirma e exclui. O estorno no banco de horas não é feito aqui: o
     * service apaga a alocação e o schema derruba o par crédito/débito pela
     * cascata de {@code lancamento_horas.escala_funcionario_id}.
     */
    private void excluir(CoberturaListagemItem item) {
        if (item == null) {
            return;
        }
        StringBuilder mensagem = new StringBuilder("Excluir a cobertura de ")
                .append(textoOuTraco(item.nomeSubstituto()));
        if (item.dataPlantao() != null) {
            mensagem.append(" no dia ").append(item.dataPlantao().format(DATA));
        }
        mensagem.append('?');
        if (item.lancouBancoHoras()) {
            String duracao = formatarDuracao(item.minutosDoTurno());
            mensagem.append("\n\nOs lançamentos de +").append(duracao)
                    .append(" (crédito) e -").append(duracao)
                    .append(" (débito) no banco de horas serão estornados.");
        }
        mensagem.append("\n\nA operação é irreversível e fica registrada no log de auditoria.");

        if (!DialogUtil.mostrarConfirmacao("Remover registro de cobertura", mensagem.toString())) {
            return;
        }

        try {
            coberturaService.excluir(item.cobertura());
            carregarDados();
            if (emEdicao != null && emEdicao.coberturaId() == item.coberturaId()) {
                // A linha que estava aberta no card lateral não existe mais.
                mostrarLateralVazio(selo("Cobertura excluída e lançamentos estornados.", "selo-sucesso"));
            }
        } catch (RegraCoberturaException excecao) {
            DialogUtil.mostrarErro("Não foi possível excluir a cobertura", excecao.getMessage());
        } catch (RepositoryException excecao) {
            DialogUtil.mostrarErroBancoIndisponivel("Não foi possível excluir a cobertura");
        }
    }

    // -----------------------------------------------------------------
    // Apoio
    // -----------------------------------------------------------------

    /**
     * Recupera do backend a alocação do titular coberto, que a edição precisa
     * inteira: é dela que saem o turno (para as regras e para o cálculo das
     * horas) e a lista de substitutos.
     */
    private EscalaFuncionario buscarAlocacaoDoAusente(CoberturaListagemItem item) {
        EscalaFuncionario coberturaDe = item.cobertura().getCoberturaDe();
        if (coberturaDe == null || coberturaDe.getId() == null || item.dataPlantao() == null) {
            DialogUtil.mostrarErro("Não foi possível editar a cobertura",
                    "Esta cobertura está sem o plantão de origem e não pode ser alterada.");
            return null;
        }
        try {
            EscalaFuncionario alocacao = coberturaService.listarEscaladosNaData(item.dataPlantao()).stream()
                    .filter(candidata -> coberturaDe.getId().equals(candidata.getId()))
                    .findFirst()
                    .orElse(null);
            if (alocacao == null) {
                DialogUtil.mostrarErro("Não foi possível editar a cobertura",
                        "O plantão coberto não está mais na escala do dia. "
                        + "Atualize a listagem e tente de novo.");
            }
            return alocacao;
        } catch (RepositoryException excecao) {
            DialogUtil.mostrarErroBancoIndisponivel("Não foi possível carregar o plantão coberto");
            return null;
        }
    }

    /** Item do combo que corresponde a quem cobre hoje, para a edição já abrir com ele selecionado. */
    private SubstitutoDisponivel substitutoAtual(EscalaFuncionario cobertura) {
        if (cobertura.getFuncionario() == null || cobertura.getFuncionario().getId() == null) {
            return null;
        }
        return comboSubstituto.getItems().stream()
                .filter(candidato -> cobertura.getFuncionario().getId().equals(candidato.funcionario().getId()))
                .findFirst()
                .orElse(null);
    }

    private MotivoCobertura motivoPorId(Integer motivoId) {
        if (motivoId == null) {
            return null;
        }
        return comboMotivo.getItems().stream()
                .filter(motivo -> motivoId.equals(motivo.getId()))
                .findFirst()
                .orElse(null);
    }

    private Integer motivoSelecionadoId() {
        MotivoCobertura motivo = comboMotivo.getValue();
        return motivo != null ? motivo.getId() : null;
    }

    /** Zera os campos que descrevem a cobertura, preservando a data escolhida. */
    private void limparCamposDaCobertura() {
        comboAusente.getSelectionModel().clearSelection();
        comboSubstituto.getSelectionModel().clearSelection();
        comboSubstituto.getItems().clear();
        comboMotivo.getSelectionModel().clearSelection();
        campoObservacoes.clear();
        checkBancoHoras.setSelected(true);
    }

    private String descreverTurno(EscalaTurno turno) {
        if (turno == null) {
            return "turno";
        }
        String tipo = turno.getTipoTurno() != null && turno.getTipoTurno().getNome() != null
                ? turno.getTipoTurno().getNome() : "turno";
        if (turno.getInicio() != null) {
            return tipo + " (" + turno.getInicio().format(HORA) + ")";
        }
        return tipo;
    }

    /** Duração em horas cheias, com os minutos só quando existem: "8h", "8h30". */
    private static String formatarDuracao(int minutos) {
        int absoluto = Math.abs(minutos);
        int horas = absoluto / 60;
        int resto = absoluto % 60;
        return horas + "h" + (resto > 0 ? String.format("%02d", resto) : "");
    }

    private static String textoOuTraco(String texto) {
        return texto == null || texto.isBlank() ? SEM_VALOR : texto;
    }

    private Label textoSecundario(String texto) {
        Label label = new Label(texto);
        label.getStyleClass().add("texto-secundario");
        label.setWrapText(true);
        return label;
    }

    private Label selo(String texto, String classeSelo) {
        Label label = new Label(texto);
        label.getStyleClass().addAll("selo", classeSelo);
        label.setWrapText(true);
        return label;
    }

    private void exibirErro(String mensagem) {
        mostrarMensagem(mensagem, "selo-perigo");
    }

    private void exibirSucesso(String mensagem) {
        mostrarMensagem(mensagem, "selo-sucesso");
    }

    private void mostrarMensagem(String mensagem, String classeSelo) {
        labelMensagem.setText(mensagem);
        labelMensagem.getStyleClass().removeAll("selo-sucesso", "selo-perigo", "selo-atencao");
        labelMensagem.getStyleClass().add(classeSelo);
        labelMensagem.setVisible(true);
        labelMensagem.setManaged(true);
    }

    private void esconderMensagem() {
        labelMensagem.setText("");
        labelMensagem.setVisible(false);
        labelMensagem.setManaged(false);
    }
}
