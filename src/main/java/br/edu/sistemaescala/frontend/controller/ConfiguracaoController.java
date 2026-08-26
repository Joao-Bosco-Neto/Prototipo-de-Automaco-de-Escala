package br.edu.sistemaescala.frontend.controller;

import java.io.File;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import br.edu.sistemaescala.backend.model.Configuracao;
import br.edu.sistemaescala.backend.service.AcessoNegadoException;
import br.edu.sistemaescala.backend.service.ConfiguracaoService;
import br.edu.sistemaescala.backend.service.RegraConfiguracaoException;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;

/**
 * Controller responsável pela tela de Configurações da Organização (Issue #70 / Backlog #70).
 *
 * Permite ao administrador:
 * - Editar o nome institucional da organização (refletido no título da aplicação e no cabeçalho do PDF)
 * - Editar o subtítulo / diretoria
 * - Selecionar o regime de apuração do banco de horas ('mensal' ou 'continuo')
 * - Definir a carga horária mensal de referência (opcional)
 * - Escolher o diretório padrão de salvamento de PDFs
 */
public class ConfiguracaoController {

    private static final String CLASSE_CAMPO_COM_ERRO = "campo-com-erro";
    private static final DateTimeFormatter FORMATO_DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final ConfiguracaoService configuracaoService;
    private final Consumer<Configuracao> aoSalvarConfiguracao;

    private final TextField campoNomeOrganizacao = new TextField();
    private final Label erroNome = new Label();

    private final TextField campoSubtitulo = new TextField();

    private final ComboBox<OpcaoApuracao> comboApuracao = new ComboBox<>();

    private final TextField campoCargaHoraria = new TextField();
    private final Label erroCargaHoraria = new Label();

    private final TextField campoCaminhoPdf = new TextField();
    private final Button botaoSelecionarPasta = new Button("Procurar...");

    private final Label labelUltimaAtualizacao = new Label();
    private final Label labelMensagemCard = new Label();

    private final Button botaoSalvar = new Button("Salvar Configurações");
    private final Button botaoRecarregar = new Button("Recarregar");

    public record OpcaoApuracao(String codigo, String descricao) {
        @Override
        public String toString() {
            return descricao;
        }
    }

    public ConfiguracaoController(ConfiguracaoService configuracaoService) {
        this(configuracaoService, null);
    }

    public ConfiguracaoController(ConfiguracaoService configuracaoService, Consumer<Configuracao> aoSalvarConfiguracao) {
        this.configuracaoService = Objects.requireNonNull(configuracaoService, "ConfiguracaoService não pode ser nulo");
        this.aoSalvarConfiguracao = aoSalvarConfiguracao;
    }

    public Parent criarTela() {
        VBox raiz = new VBox(20);
        raiz.setPadding(new Insets(24));
        raiz.getStyleClass().add("area-conteudo");

        VBox cabecalho = criarCabecalho();
        VBox cardFormulario = criarCardFormulario();

        raiz.getChildren().addAll(cabecalho, cardFormulario);

        configurarEventos();
        carregarDados();

        ScrollPane scroll = new ScrollPane(raiz);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        return scroll;
    }

    private VBox criarCabecalho() {
        Label titulo = new Label("Configurações da Organização");
        titulo.getStyleClass().add("titulo-1");

        Label subtitulo = new Label("Parâmetros institucionais, regime de apuração do banco de horas e preferências de exportação.");
        subtitulo.getStyleClass().add("texto-secundario");

        return new VBox(4, titulo, subtitulo);
    }

    private VBox criarCardFormulario() {
        VBox painel = new VBox(16);
        painel.getStyleClass().add("card");
        painel.setMaxWidth(680);

        // Seção 1: Identificação Institucional
        Label tituloIdentificacao = new Label("1. Identificação Institucional");
        tituloIdentificacao.getStyleClass().add("titulo-2");

        campoNomeOrganizacao.setPromptText("Ex: Diretoria do GOTE - Polícia Civil");
        campoSubtitulo.setPromptText("Ex: Secretaria de Segurança Pública do Estado do Tocantins");

        configurarEstiloErroLabel(erroNome);
        configurarEstiloErroLabel(erroCargaHoraria);

        VBox boxNome = new VBox(4, new Label("Nome da Organização *"), campoNomeOrganizacao, erroNome);
        VBox boxSubtitulo = new VBox(4, new Label("Subtítulo / Descrição Complementar"), campoSubtitulo);

        // Seção 2: Banco de Horas
        Label tituloBancoHoras = new Label("2. Regime do Banco de Horas");
        tituloBancoHoras.getStyleClass().add("titulo-2");

        comboApuracao.setItems(FXCollections.observableArrayList(
                new OpcaoApuracao("mensal", "Mensal (Zera / Fecha o saldo a cada mês)"),
                new OpcaoApuracao("continuo", "Contínuo (Saldo acumula entre os meses)")
        ));
        comboApuracao.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(OpcaoApuracao item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.descricao());
            }
        });
        comboApuracao.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(OpcaoApuracao item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.descricao());
            }
        });
        comboApuracao.setMaxWidth(Double.MAX_VALUE);

        campoCargaHoraria.setPromptText("Ex: 160 (Opcional)");

        Label dicaCarga = new Label("Deixe em branco caso a organização não controle carga mensal fixa.");
        dicaCarga.getStyleClass().add("texto-secundario");

        VBox boxApuracao = new VBox(4, new Label("Regime de Apuração de Horas *"), comboApuracao);
        VBox boxCarga = new VBox(4, new Label("Carga Horária Mensal de Referência (horas)"), campoCargaHoraria, dicaCarga, erroCargaHoraria);

        // Seção 3: Exportação e Relatórios
        Label tituloExportacao = new Label("3. Exportação de Relatórios e Escala");
        tituloExportacao.getStyleClass().add("titulo-2");

        campoCaminhoPdf.setPromptText("Ex: C:\\Relatorios\\Escalas");
        botaoSelecionarPasta.getStyleClass().add("button-secundario");

        HBox linhaPasta = new HBox(8, campoCaminhoPdf, botaoSelecionarPasta);
        HBox.setHgrow(campoCaminhoPdf, Priority.ALWAYS);

        VBox boxPdf = new VBox(4, new Label("Pasta Padrão para Exportação de PDFs"), linhaPasta);

        // Feedback e Ações
        labelUltimaAtualizacao.getStyleClass().add("texto-secundario");

        labelMensagemCard.getStyleClass().add("selo");
        labelMensagemCard.setWrapText(true);
        labelMensagemCard.setVisible(false);
        labelMensagemCard.setManaged(false);

        botaoSalvar.getStyleClass().add("button-primario");
        botaoRecarregar.getStyleClass().add("button-secundario");

        HBox barraBotoes = new HBox(12, botaoSalvar, botaoRecarregar);
        barraBotoes.setAlignment(Pos.CENTER_RIGHT);

        painel.getChildren().addAll(
                tituloIdentificacao,
                boxNome,
                boxSubtitulo,
                tituloBancoHoras,
                boxApuracao,
                boxCarga,
                tituloExportacao,
                boxPdf,
                labelUltimaAtualizacao,
                labelMensagemCard,
                barraBotoes
        );

        return painel;
    }

    private void configurarEstiloErroLabel(Label label) {
        label.getStyleClass().addAll("selo", "selo-perigo");
        label.setVisible(false);
        label.setManaged(false);
    }

    private void configurarEventos() {
        campoNomeOrganizacao.textProperty().addListener((obs, antigo, novo) -> {
            campoNomeOrganizacao.getStyleClass().remove(CLASSE_CAMPO_COM_ERRO);
            esconderErroLabel(erroNome);
        });

        campoCargaHoraria.textProperty().addListener((obs, antigo, novo) -> {
            campoCargaHoraria.getStyleClass().remove(CLASSE_CAMPO_COM_ERRO);
            esconderErroLabel(erroCargaHoraria);
        });

        botaoSelecionarPasta.setOnAction(e -> selecionarDiretorioPdf());
        botaoSalvar.setOnAction(e -> salvarConfiguracoes());
        botaoRecarregar.setOnAction(e -> carregarDados());
    }

    private void selecionarDiretorioPdf() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Selecionar Pasta para Exportação de PDFs");
        String caminhoAtual = campoCaminhoPdf.getText();
        if (caminhoAtual != null && !caminhoAtual.isBlank()) {
            File pastaAtual = new File(caminhoAtual.trim());
            if (pastaAtual.exists() && pastaAtual.isDirectory()) {
                chooser.setInitialDirectory(pastaAtual);
            }
        }

        File pastaEscolhida = chooser.showDialog(campoCaminhoPdf.getScene().getWindow());
        if (pastaEscolhida != null) {
            campoCaminhoPdf.setText(pastaEscolhida.getAbsolutePath());
        }
    }

    private void carregarDados() {
        limparErrosFormulario();
        try {
            Optional<Configuracao> configOpt = configuracaoService.buscar();
            if (configOpt.isPresent()) {
                Configuracao config = configOpt.get();
                campoNomeOrganizacao.setText(config.getNomeOrganizacao());
                campoSubtitulo.setText(config.getSubtitulo() != null ? config.getSubtitulo() : "");

                String apuracao = config.getApuracaoBancoHoras() != null ? config.getApuracaoBancoHoras().toLowerCase() : "mensal";
                comboApuracao.getItems().stream()
                        .filter(op -> op.codigo().equalsIgnoreCase(apuracao))
                        .findFirst()
                        .ifPresent(comboApuracao::setValue);

                campoCargaHoraria.setText(config.getCargaHorariaMensal() != null ? config.getCargaHorariaMensal().toPlainString() : "");
                campoCaminhoPdf.setText(config.getCaminhoPdfPadrao() != null ? config.getCaminhoPdfPadrao() : "");

                if (config.getAtualizadoEm() != null) {
                    labelUltimaAtualizacao.setText("Última alteração registrada em: " + config.getAtualizadoEm().format(FORMATO_DATA_HORA));
                } else {
                    labelUltimaAtualizacao.setText("");
                }
            } else {
                campoNomeOrganizacao.setText("Sistema de Escala");
                comboApuracao.getSelectionModel().selectFirst();
                labelUltimaAtualizacao.setText("");
            }
        } catch (Exception e) {
            exibirErroCard("Erro ao carregar configurações: " + e.getMessage());
        }
    }

    private void salvarConfiguracoes() {
        limparErrosFormulario();

        String nome = campoNomeOrganizacao.getText();
        String subtitulo = campoSubtitulo.getText();

        OpcaoApuracao opcao = comboApuracao.getValue();
        String apuracao = opcao != null ? opcao.codigo() : "mensal";

        BigDecimal cargaHoraria = null;
        String cargaTexto = campoCargaHoraria.getText();
        if (cargaTexto != null && !cargaTexto.isBlank()) {
            try {
                cargaHoraria = new BigDecimal(cargaTexto.trim().replace(",", "."));
            } catch (NumberFormatException e) {
                marcarErroCampo(campoCargaHoraria, erroCargaHoraria, "Informe um número válido para a carga horária.");
                return;
            }
        }

        String caminhoPdf = campoCaminhoPdf.getText();

        try {
            Configuracao configAtualizada = configuracaoService.salvar(nome, subtitulo, cargaHoraria, apuracao, caminhoPdf);
            exibirSucessoCard("Configurações atualizadas com sucesso!");
            if (configAtualizada.getAtualizadoEm() != null) {
                labelUltimaAtualizacao.setText("Última alteração registrada em: " + configAtualizada.getAtualizadoEm().format(FORMATO_DATA_HORA));
            }
            if (aoSalvarConfiguracao != null) {
                aoSalvarConfiguracao.accept(configAtualizada);
            }
        } catch (RegraConfiguracaoException e) {
            tratarErroValidacao(e.getMessage());
        } catch (AcessoNegadoException e) {
            exibirErroCard("Acesso negado: " + e.getMessage());
        } catch (Exception e) {
            exibirErroCard("Erro ao salvar configurações: " + e.getMessage());
        }
    }

    private void tratarErroValidacao(String mensagem) {
        String msgLower = mensagem.toLowerCase();
        if (msgLower.contains("nome")) {
            marcarErroCampo(campoNomeOrganizacao, erroNome, mensagem);
        } else if (msgLower.contains("carga horária") || msgLower.contains("carga horaria")) {
            marcarErroCampo(campoCargaHoraria, erroCargaHoraria, mensagem);
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
        campoNomeOrganizacao.getStyleClass().remove(CLASSE_CAMPO_COM_ERRO);
        campoCargaHoraria.getStyleClass().remove(CLASSE_CAMPO_COM_ERRO);
        esconderErroLabel(erroNome);
        esconderErroLabel(erroCargaHoraria);
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
}

