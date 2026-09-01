package br.edu.sistemaescala.frontend.controller;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import br.edu.sistemaescala.LogAplicacao;
import br.edu.sistemaescala.backend.model.Configuracao;
import br.edu.sistemaescala.backend.model.EscalaTurno;
import br.edu.sistemaescala.backend.model.OpcoesExportacaoPdf;
import br.edu.sistemaescala.backend.repository.ConfiguracaoRepository;
import br.edu.sistemaescala.backend.repository.EscalaTurnoRepository;
import br.edu.sistemaescala.backend.repository.jdbc.ConfiguracaoRepositoryJdbc;
import br.edu.sistemaescala.backend.repository.jdbc.EscalaTurnoRepositoryJdbc;
import br.edu.sistemaescala.backend.service.BancoHorasListagemItem;
import br.edu.sistemaescala.backend.service.BancoHorasService;
import br.edu.sistemaescala.backend.service.BancoHorasServiceImpl;
import br.edu.sistemaescala.backend.service.CoberturaListagemItem;
import br.edu.sistemaescala.backend.service.CoberturaService;
import br.edu.sistemaescala.backend.service.CoberturaServiceImpl;
import br.edu.sistemaescala.backend.service.EscalaPdfDados;
import br.edu.sistemaescala.backend.service.GeradorPdfService;
import br.edu.sistemaescala.frontend.DialogUtil;
import br.edu.sistemaescala.frontend.PdfPreviewRenderer;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.print.PageLayout;
import javafx.print.PrinterJob;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.transform.Scale;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import javafx.util.Duration;

/**
 * Tela de exportacao em PDF da escala mensal (issue #43 / #52).
 *
 * <p>Duas colunas dentro de {@code area-conteudo}: a esquerda um card com o
 * seletor de periodo, os checkboxes de conteudo e os botoes "Gerar PDF" e
 * "Imprimir"; a direita um card com a pre-visualizacao da primeira pagina.</p>
 *
 * <p>Qualquer mudanca de opcao ou de mes dispara, apos um curto debounce, uma
 * {@link Task} numa thread daemon: consulta os dados, gera o PDF em memoria via
 * {@link GeradorPdfService#gerarEmMemoria} e rasteriza a pagina com
 * {@link PdfPreviewRenderer}. A JavaFX Application Thread nunca bloqueia — o
 * indicador de carregamento cobre o intervalo.</p>
 */
public class ExportacaoPdfController {

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    private static final DateTimeFormatter ULTIMA_EXPORTACAO =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    /** Espera curta depois da ultima interacao antes de regenerar a previa. */
    private static final Duration DEBOUNCE = Duration.millis(280);

    private final EscalaTurnoRepository escalaTurnoRepository;
    private final CoberturaService coberturaService;
    private final BancoHorasService bancoHorasService;
    private final ConfiguracaoRepository configuracaoRepository;
    private final GeradorPdfService geradorPdfService;
    /** Notifica o shell da data/hora de uma exportacao bem-sucedida. */
    private final Consumer<LocalDateTime> aoExportar;

    /**
     * Thread unica e daemon para gerar/rasterizar a previa. Uma so porque as
     * regeracoes sao naturalmente sequenciais (o usuario mexe numa opcao de
     * cada vez); daemon para nao segurar a JVM se a janela fechar no meio.
     */
    private final ExecutorService executor = Executors.newSingleThreadExecutor(corpo -> {
        Thread thread = new Thread(corpo, "exportacao-pdf-preview");
        thread.setDaemon(true);
        return thread;
    });

    private final javafx.animation.PauseTransition debounce = new javafx.animation.PauseTransition(DEBOUNCE);

    private VBox raiz;
    private YearMonth mesSelecionado = YearMonth.now();
    private final Label rotuloMesAno = new Label();
    private final CheckBox chkCoberturas = new CheckBox("Exibir coberturas registradas no período");
    private final CheckBox chkTelefones = new CheckBox("Exibir telefone dos funcionários");
    private final CheckBox chkSaldo = new CheckBox("Exibir saldo do banco de horas");
    private final CheckBox chkAssinaturas = new CheckBox("Exibir campos para assinatura (Gestor e Direção)");

    private final ImageView imagemPreview = new ImageView();
    private final ProgressIndicator indicador = new ProgressIndicator();
    private final Label bannerCarregando = new Label("Atualizando pré-visualização…");
    private final Label mensagemPreview = new Label();

    /** Task em andamento, para ignorar o resultado de uma regeracao ja superada. */
    private Task<Image> tarefaAtual;

    /** Construtor de conveniencia: monta os servicos a partir dos padroes JDBC. */
    public ExportacaoPdfController(Consumer<LocalDateTime> aoExportar) {
        this(new EscalaTurnoRepositoryJdbc(), new CoberturaServiceImpl(), new BancoHorasServiceImpl(),
                new ConfiguracaoRepositoryJdbc(), new GeradorPdfService(), aoExportar);
    }

    public ExportacaoPdfController(EscalaTurnoRepository escalaTurnoRepository,
                                   CoberturaService coberturaService,
                                   BancoHorasService bancoHorasService,
                                   ConfiguracaoRepository configuracaoRepository,
                                   GeradorPdfService geradorPdfService,
                                   Consumer<LocalDateTime> aoExportar) {
        this.escalaTurnoRepository = escalaTurnoRepository;
        this.coberturaService = coberturaService;
        this.bancoHorasService = bancoHorasService;
        this.configuracaoRepository = configuracaoRepository;
        this.geradorPdfService = geradorPdfService;
        this.aoExportar = aoExportar != null ? aoExportar : instante -> { };
    }

    public Parent criarTela() {
        raiz = new VBox(18);
        raiz.setPadding(new Insets(24));
        raiz.getStyleClass().add("area-conteudo");

        HBox colunas = new HBox(20, criarPainelOpcoes(), criarPainelPreview());
        colunas.setAlignment(Pos.TOP_LEFT);
        VBox.setVgrow(colunas, Priority.ALWAYS);

        raiz.getChildren().addAll(criarCabecalho(), colunas);

        // Estado inicial dos checkboxes: espelha OpcoesExportacaoPdf.padrao(...).
        chkCoberturas.setSelected(true);
        chkAssinaturas.setSelected(true);

        debounce.setOnFinished(evento -> dispararPreview());
        registrarEventos();
        atualizarRotuloMes();
        agendarPreview();

        return raiz;
    }

    // -----------------------------------------------------------------
    // Cabecalho
    // -----------------------------------------------------------------

    private VBox criarCabecalho() {
        Label titulo = new Label("Exportar escala em PDF");
        titulo.getStyleClass().add("titulo-1");

        Label subtitulo = new Label(
                "Escolha o período e o que aparece no documento. A pré-visualização "
                + "à direita acompanha as opções em tempo real.");
        subtitulo.getStyleClass().add("texto-secundario");
        subtitulo.setWrapText(true);

        return new VBox(4, titulo, subtitulo);
    }

    // -----------------------------------------------------------------
    // Coluna esquerda: opcoes
    // -----------------------------------------------------------------

    private VBox criarPainelOpcoes() {
        VBox painel = new VBox(18);
        painel.getStyleClass().add("card");
        painel.setPrefWidth(360);
        painel.setMinWidth(320);

        painel.getChildren().addAll(
                criarSeletorPeriodo(),
                criarBlocoConteudo(),
                criarBlocoAcoes());
        return painel;
    }

    private VBox criarSeletorPeriodo() {
        Label rotulo = new Label("Período");
        rotulo.getStyleClass().add("titulo-2");

        Button anterior = new Button("<");
        anterior.getStyleClass().add("button-secundario");
        anterior.setOnAction(evento -> trocarMes(-1));

        Button proximo = new Button(">");
        proximo.getStyleClass().add("button-secundario");
        proximo.setOnAction(evento -> trocarMes(1));

        rotuloMesAno.getStyleClass().add("calendario-mes-ano");
        rotuloMesAno.setMinWidth(Region.USE_PREF_SIZE);
        HBox.setHgrow(rotuloMesAno, Priority.ALWAYS);
        rotuloMesAno.setMaxWidth(Double.MAX_VALUE);
        rotuloMesAno.setAlignment(Pos.CENTER);

        HBox navegacao = new HBox(10, anterior, rotuloMesAno, proximo);
        navegacao.setAlignment(Pos.CENTER_LEFT);

        return new VBox(8, rotulo, navegacao);
    }

    private VBox criarBlocoConteudo() {
        Label rotulo = new Label("Conteúdo do documento");
        rotulo.getStyleClass().add("titulo-2");

        VBox opcoes = new VBox(10, chkCoberturas, chkTelefones, chkSaldo, chkAssinaturas);
        for (CheckBox caixa : List.of(chkCoberturas, chkTelefones, chkSaldo, chkAssinaturas)) {
            caixa.setWrapText(true);
        }

        return new VBox(10, rotulo, opcoes);
    }

    private VBox criarBlocoAcoes() {
        Button gerar = new Button("Gerar PDF");
        gerar.getStyleClass().add("button-primario");
        gerar.setMaxWidth(Double.MAX_VALUE);
        gerar.setOnAction(evento -> gerarPdf());

        Button imprimir = new Button("Imprimir");
        imprimir.getStyleClass().add("button-secundario");
        imprimir.setMaxWidth(Double.MAX_VALUE);
        imprimir.setOnAction(evento -> imprimir());

        Label nota = new Label(
                "A impressão usa a página exibida na pré-visualização. Para o documento "
                + "completo com todas as páginas, use \"Gerar PDF\".");
        nota.getStyleClass().add("texto-secundario");
        nota.setWrapText(true);

        return new VBox(10, gerar, imprimir, nota);
    }

    // -----------------------------------------------------------------
    // Coluna direita: pre-visualizacao
    // -----------------------------------------------------------------

    private VBox criarPainelPreview() {
        VBox painel = new VBox(12);
        painel.getStyleClass().add("card");
        HBox.setHgrow(painel, Priority.ALWAYS);

        Label titulo = new Label("Pré-visualização");
        titulo.getStyleClass().add("titulo-2");

        imagemPreview.setPreserveRatio(true);
        imagemPreview.setSmooth(true);
        imagemPreview.setFitWidth(620);

        StackPane palco = new StackPane(imagemPreview);
        palco.setPadding(new Insets(12));
        palco.setAlignment(Pos.CENTER);

        ScrollPane rolagem = new ScrollPane(palco);
        rolagem.setFitToWidth(true);
        rolagem.setPannable(true);
        rolagem.getStyleClass().add("painel-atribuicao-rolagem");
        VBox.setVgrow(rolagem, Priority.ALWAYS);

        // Faixa de carregamento: indicador + texto, sobreposta ao topo do preview.
        indicador.setPrefSize(22, 22);
        indicador.setMaxSize(22, 22);
        bannerCarregando.getStyleClass().add("texto-secundario");
        HBox faixa = new HBox(8, indicador, bannerCarregando);
        faixa.setAlignment(Pos.CENTER_LEFT);
        faixa.setPadding(new Insets(6, 0, 0, 0));

        mensagemPreview.getStyleClass().addAll("selo", "selo-perigo");
        mensagemPreview.setWrapText(true);
        mensagemPreview.setVisible(false);
        mensagemPreview.setManaged(false);

        painel.getChildren().addAll(titulo, faixa, mensagemPreview, rolagem);
        return painel;
    }

    // -----------------------------------------------------------------
    // Eventos e ciclo da previa
    // -----------------------------------------------------------------

    private void registrarEventos() {
        chkCoberturas.selectedProperty().addListener((obs, antigo, novo) -> agendarPreview());
        chkTelefones.selectedProperty().addListener((obs, antigo, novo) -> agendarPreview());
        chkSaldo.selectedProperty().addListener((obs, antigo, novo) -> agendarPreview());
        chkAssinaturas.selectedProperty().addListener((obs, antigo, novo) -> agendarPreview());
    }

    private void trocarMes(int passos) {
        mesSelecionado = mesSelecionado.plusMonths(passos);
        atualizarRotuloMes();
        agendarPreview();
    }

    private void atualizarRotuloMes() {
        String nome = mesSelecionado.getMonth().getDisplayName(TextStyle.FULL, PT_BR);
        rotuloMesAno.setText(nome.substring(0, 1).toUpperCase(PT_BR) + nome.substring(1)
                + " de " + mesSelecionado.getYear());
    }

    private OpcoesExportacaoPdf opcoesAtuais() {
        return new OpcoesExportacaoPdf(mesSelecionado,
                chkCoberturas.isSelected(), chkTelefones.isSelected(),
                chkSaldo.isSelected(), chkAssinaturas.isSelected());
    }

    private void agendarPreview() {
        mostrarCarregando(true);
        debounce.playFromStart();
    }

    private void dispararPreview() {
        if (tarefaAtual != null && tarefaAtual.isRunning()) {
            tarefaAtual.cancel();
        }
        OpcoesExportacaoPdf opcoes = opcoesAtuais();

        Task<Image> tarefa = new Task<>() {
            @Override
            protected Image call() throws Exception {
                EscalaPdfDados dados = montarDados(opcoes);
                if (isCancelled()) {
                    return null;
                }
                byte[] pdf = geradorPdfService.gerarEmMemoria(dados, opcoes);
                if (isCancelled()) {
                    return null;
                }
                return PdfPreviewRenderer.renderizarPrimeiraPagina(pdf);
            }
        };
        tarefa.setOnSucceeded(evento -> {
            if (tarefa != tarefaAtual) {
                return;
            }
            Image imagem = tarefa.getValue();
            if (imagem != null) {
                imagemPreview.setImage(imagem);
                esconderMensagemPreview();
            }
            mostrarCarregando(false);
        });
        tarefa.setOnFailed(evento -> {
            if (tarefa != tarefaAtual) {
                return;
            }
            mostrarCarregando(false);
            exibirMensagemPreview("Não foi possível gerar a pré-visualização. "
                    + "Verifique se o banco de dados está acessível e tente de novo.");
            LogAplicacao.registrarErro(
                    "Falha ao gerar a pré-visualização da escala em PDF", tarefa.getException());
        });

        tarefaAtual = tarefa;
        executor.execute(tarefa);
    }

    /** Consulta tudo o que o PDF precisa. Roda fora da thread da interface. */
    private EscalaPdfDados montarDados(OpcoesExportacaoPdf opcoes) {
        YearMonth mes = opcoes.mes();
        List<EscalaTurno> turnos = escalaTurnoRepository.buscarPorPeriodo(
                mes.atDay(1).atStartOfDay(),
                mes.plusMonths(1).atDay(1).atStartOfDay());

        String nomeOrganizacao = null;
        String subtitulo = null;
        Optional<Configuracao> configuracao = configuracaoRepository.buscar();
        if (configuracao.isPresent()) {
            nomeOrganizacao = configuracao.get().getNomeOrganizacao();
            subtitulo = configuracao.get().getSubtitulo();
        }

        List<CoberturaListagemItem> coberturas = opcoes.exibirCoberturas()
                ? coberturaService.listarCoberturasParaListagem(mes)
                : List.of();
        List<BancoHorasListagemItem> saldos = opcoes.exibirSaldoBancoHoras()
                ? bancoHorasService.listarMensal(mes)
                : List.of();

        return new EscalaPdfDados(turnos, nomeOrganizacao, subtitulo, coberturas, saldos);
    }

    private void mostrarCarregando(boolean carregando) {
        indicador.setVisible(carregando);
        indicador.setManaged(carregando);
        bannerCarregando.setVisible(carregando);
        bannerCarregando.setManaged(carregando);
    }

    private void exibirMensagemPreview(String texto) {
        mensagemPreview.setText(texto);
        mensagemPreview.setVisible(true);
        mensagemPreview.setManaged(true);
    }

    private void esconderMensagemPreview() {
        mensagemPreview.setVisible(false);
        mensagemPreview.setManaged(false);
    }

    // -----------------------------------------------------------------
    // Gerar PDF
    // -----------------------------------------------------------------

    private void gerarPdf() {
        OpcoesExportacaoPdf opcoes = opcoesAtuais();

        FileChooser seletor = new FileChooser();
        seletor.setTitle("Salvar escala em PDF");
        seletor.setInitialFileName(geradorPdfService.nomeArquivoSugerido(opcoes.mes()));
        seletor.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Documento PDF (*.pdf)", "*.pdf"));
        aplicarDiretorioPadrao(seletor);

        File escolhido = seletor.showSaveDialog(janela());
        if (escolhido == null) {
            return;
        }
        File destino = garantirExtensaoPdf(escolhido);

        try {
            byte[] pdf = geradorPdfService.gerarEmMemoria(montarDados(opcoes), opcoes);
            Files.write(destino.toPath(), pdf);
            LocalDateTime agora = LocalDateTime.now();
            aoExportar.accept(agora);
            DialogUtil.mostrarInformacao("Exportação concluída",
                    "Escala de " + rotuloMesAno.getText().toLowerCase(PT_BR) + " salva em:\n"
                    + destino.getAbsolutePath());
        } catch (IOException | RuntimeException e) {
            LogAplicacao.registrarErro("Falha ao exportar a escala em PDF para " + destino, e);
            DialogUtil.mostrarErro("Falha na exportação",
                    "Não foi possível gravar o arquivo. Verifique se você tem permissão de "
                    + "escrita na pasta escolhida e se o arquivo não está aberto em outro programa.");
        }
    }

    /**
     * Aponta o {@code FileChooser} para {@code configuracao.caminho_pdf_padrao}
     * quando a pasta existe de fato no disco.
     */
    private void aplicarDiretorioPadrao(FileChooser seletor) {
        try {
            configuracaoRepository.buscar()
                    .map(Configuracao::getCaminhoPdfPadrao)
                    .filter(caminho -> caminho != null && !caminho.isBlank())
                    .map(caminho -> Path.of(caminho))
                    .filter(Files::isDirectory)
                    .ifPresent(pasta -> seletor.setInitialDirectory(pasta.toFile()));
        } catch (RuntimeException e) {
            // Diretorio padrao e conveniencia: se a consulta falhar, o dialogo
            // abre na pasta do sistema e o usuario navega manualmente.
            LogAplicacao.registrarErro("Não foi possível ler o caminho padrão de PDF", e);
        }
    }

    private File garantirExtensaoPdf(File escolhido) {
        return escolhido.getName().toLowerCase(PT_BR).endsWith(".pdf")
                ? escolhido
                : new File(escolhido.getPath() + ".pdf");
    }

    // -----------------------------------------------------------------
    // Imprimir
    // -----------------------------------------------------------------

    private void imprimir() {
        Image imagem = imagemPreview.getImage();
        if (imagem == null) {
            DialogUtil.mostrarInformacao("Pré-visualização em andamento",
                    "Aguarde a pré-visualização carregar antes de imprimir.");
            return;
        }

        PrinterJob job = PrinterJob.createPrinterJob();
        if (job == null) {
            DialogUtil.mostrarErro("Impressão indisponível",
                    "Nenhuma impressora foi encontrada neste computador.");
            return;
        }
        if (!job.showPrintDialog(janela())) {
            return; // o usuario cancelou a caixa de impressao
        }

        ImageView paraImpressao = new ImageView(imagem);
        paraImpressao.setPreserveRatio(true);
        PageLayout layout = job.getJobSettings().getPageLayout();
        double escala = Math.min(
                layout.getPrintableWidth() / imagem.getWidth(),
                layout.getPrintableHeight() / imagem.getHeight());
        if (escala < 1.0) {
            paraImpressao.getTransforms().add(new Scale(escala, escala));
        }

        boolean impresso = job.printPage(paraImpressao);
        if (impresso) {
            job.endJob();
            DialogUtil.mostrarInformacao("Impressão enviada",
                    "A escala foi enviada para a impressora.");
        } else {
            DialogUtil.mostrarErro("Falha na impressão",
                    "Não foi possível enviar o documento para a impressora.");
        }
    }

    // -----------------------------------------------------------------
    // Apoio
    // -----------------------------------------------------------------

    private Window janela() {
        return raiz.getScene() != null ? raiz.getScene().getWindow() : null;
    }

    /** Texto para a barra de status do shell: {@code Última exportação: dd/MM/yyyy HH:mm}. */
    public static String rotuloUltimaExportacao(LocalDateTime instante) {
        return "Última exportação: " + instante.format(ULTIMA_EXPORTACAO);
    }
}
