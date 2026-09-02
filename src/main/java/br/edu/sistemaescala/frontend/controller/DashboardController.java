package br.edu.sistemaescala.frontend.controller;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import br.edu.sistemaescala.LogAplicacao;
import br.edu.sistemaescala.backend.service.AlertaDashboard;
import br.edu.sistemaescala.backend.service.ContagemFuncionarios;
import br.edu.sistemaescala.backend.service.DashboardService;
import br.edu.sistemaescala.backend.service.DashboardServiceImpl;
import br.edu.sistemaescala.backend.service.DiaDaSemana;
import br.edu.sistemaescala.backend.service.IndicadoresDashboard;
import br.edu.sistemaescala.backend.service.PlantaoDoDiaItem;
import br.edu.sistemaescala.backend.service.PostoDoTurno;
import br.edu.sistemaescala.backend.service.SeveridadeAlerta;
import br.edu.sistemaescala.backend.service.TurnoResumido;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Tela "Visão geral", conforme a tela 1 do protótipo. De cima para baixo:
 *
 * <ol>
 *   <li>os quatro cards de indicadores do mês corrente — plantão de hoje,
 *       funcionários ativos, coberturas no mês e dias com efetivo incompleto
 *       (issue #53);</li>
 *   <li>a faixa da semana corrente, de domingo a sábado, com o dia de hoje
 *       destacado (issue #54);</li>
 *   <li>a tabela dos próximos plantões a partir de hoje, cada linha com o selo
 *       Confirmado ou Incompleto (issue #54);</li>
 *   <li>o painel de pendências e alertas, cada linha com o selo da sua
 *       severidade (issue #55).</li>
 * </ol>
 *
 * <p>Sem regra de negócio aqui: o {@link DashboardService} devolve tudo pronto
 * num {@link IndicadoresDashboard} e esta classe só decide como cada dado vira
 * texto na tela. O selo das linhas, em particular, apenas lê o
 * {@code ResultadoEfetivo} que veio do serviço — é a mesma regra que pinta a
 * célula do calendário, então as duas telas não têm como discordar sobre o
 * mesmo dia.</p>
 *
 * <p>A carga roda numa {@link Task} em thread daemon, como na tela de
 * exportação: são várias consultas ao banco, e a JavaFX Application Thread não
 * pode ficar parada esperando por elas. O indicador de carregamento cobre o
 * intervalo e a falha vira mensagem na própria tela, não diálogo — o dashboard
 * é a primeira coisa que aparece depois do login, e um alerta modal no meio da
 * abertura do shell trancaria a navegação.</p>
 *
 * <p>Um detalhe do card "Plantão de hoje" e da faixa da semana: os dois mostram
 * o <b>tipo de turno</b>. As issues falavam em "equipe", mas equipes de rodízio
 * foram substituídas por {@code tipo_turno} e não existe campo de equipe no
 * modelo.</p>
 */
public class DashboardController {

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DIA_CURTO = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter DIA_E_HORA = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    /** Colunas da faixa, na ordem em que aparecem — domingo primeiro. */
    private static final List<String> NOMES_DIAS_SEMANA =
            List.of("Domingo", "Segunda", "Terça", "Quarta", "Quinta", "Sexta", "Sábado");

    private static final int DIAS_NA_SEMANA = 7;

    /** Variantes de cor dos cards, na ordem em que aparecem na faixa. */
    private static final String VARIANTE_PRIMARIA = "primaria";
    private static final String VARIANTE_SUCESSO = "sucesso";
    private static final String VARIANTE_NEUTRO = "neutro";
    private static final String VARIANTE_ATENCAO = "atencao";

    private final DashboardService dashboardService;
    /** Injetável para o "hoje" do card de plantão não depender do relógio em teste. */
    private final Supplier<LocalDate> hoje;

    private final VBox raiz = new VBox(18);
    private final GridPane faixaCards = new GridPane();
    private final GridPane faixaSemana = new GridPane();
    private final Label rotuloSemana = new Label();
    private final Label semanaVazia = new Label();
    private final TableView<TurnoResumido> tabelaProximos = new TableView<>();
    private final ObservableList<TurnoResumido> proximos = FXCollections.observableArrayList();
    private final Label contadorAlertas = new Label();
    private final VBox listaAlertas = new VBox();
    private final Label semAlertas = new Label();
    private final ProgressIndicator indicador = new ProgressIndicator();
    private final Label rotuloSubtitulo = new Label();
    private final Label mensagemErro = new Label();

    /**
     * Thread única e daemon: as recargas são sequenciais (uma por clique em
     * "Atualizar") e nenhuma delas pode segurar a JVM se a janela fechar no
     * meio da consulta.
     */
    private final ExecutorService executor = Executors.newSingleThreadExecutor(corpo -> {
        Thread thread = new Thread(corpo, "dashboard-indicadores");
        thread.setDaemon(true);
        return thread;
    });

    private Task<IndicadoresDashboard> tarefaAtual;

    /** Construtor de conveniência: monta o serviço sobre os repositórios JDBC padrão. */
    public DashboardController() {
        this(new DashboardServiceImpl(), LocalDate::now);
    }

    public DashboardController(DashboardService dashboardService) {
        this(dashboardService, LocalDate::now);
    }

    public DashboardController(DashboardService dashboardService, Supplier<LocalDate> hoje) {
        this.dashboardService = dashboardService;
        this.hoje = hoje;
    }

    public Parent criarTela() {
        raiz.setPadding(new Insets(24));
        raiz.getStyleClass().add("area-conteudo");

        configurarFaixaCards();

        mensagemErro.getStyleClass().addAll("selo", "selo-perigo");
        mensagemErro.setWrapText(true);
        esconderErro();

        raiz.getChildren().addAll(criarCabecalho(), mensagemErro, faixaCards,
                criarCardSemana(), criarCardProximosPlantoes(), criarCardAlertas());

        carregar();

        // Cards, faixa da semana e tabela empilhados passam da altura da janela.
        // Sem rolagem o VBox espreme as linhas de cima para caber tudo, e a
        // primeira coisa a sumir é justamente o texto de apoio dos cards.
        ScrollPane rolagem = new ScrollPane(raiz);
        rolagem.setFitToWidth(true);
        rolagem.getStyleClass().addAll("area-conteudo", "dashboard-rolagem");
        return rolagem;
    }

    // -----------------------------------------------------------------
    // Cabecalho
    // -----------------------------------------------------------------

    private VBox criarCabecalho() {
        Label titulo = new Label("Visão geral");
        titulo.getStyleClass().add("titulo-1");

        rotuloSubtitulo.getStyleClass().add("texto-secundario");
        rotuloSubtitulo.setWrapText(true);
        rotuloSubtitulo.setText(descreverDia(hoje.get()));

        indicador.getStyleClass().add("indicador-carregando");

        Region espacador = new Region();
        HBox.setHgrow(espacador, Priority.ALWAYS);

        Button atualizar = new Button("Atualizar");
        atualizar.getStyleClass().add("button-secundario");
        atualizar.setOnAction(evento -> carregar());

        HBox linha = new HBox(10, titulo, indicador, espacador, atualizar);
        linha.setAlignment(Pos.CENTER_LEFT);

        return new VBox(4, linha, rotuloSubtitulo);
    }

    /** "Terça-feira, 02/09/2026 — indicadores de setembro de 2026." */
    private String descreverDia(LocalDate dia) {
        String diaSemana = dia.getDayOfWeek().getDisplayName(TextStyle.FULL, PT_BR);
        String mes = dia.getMonth().getDisplayName(TextStyle.FULL, PT_BR);
        return maiusculaInicial(diaSemana) + ", "
                + dia.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                + " — indicadores de " + mes + " de " + dia.getYear() + ".";
    }

    // -----------------------------------------------------------------
    // Faixa dos quatro cards
    // -----------------------------------------------------------------

    /**
     * Os quatro cards dividem a largura em colunas de porcentagem fixa: o de
     * plantão fica mais largo porque lista um bloco por turno do dia (em 12x36
     * são dois), enquanto os outros três só mostram um número.
     */
    private void configurarFaixaCards() {
        faixaCards.setHgap(16);
        faixaCards.setVgap(16);

        double[] larguras = {31, 23, 23, 23};
        for (double largura : larguras) {
            ColumnConstraints coluna = new ColumnConstraints();
            coluna.setPercentWidth(largura);
            coluna.setFillWidth(true);
            faixaCards.getColumnConstraints().add(coluna);
        }
    }

    private void desenharCards(IndicadoresDashboard indicadores) {
        faixaCards.getChildren().clear();
        faixaCards.addRow(0,
                criarCardPlantaoDeHoje(indicadores.plantoesDeHoje()),
                criarCardFuncionarios(indicadores.funcionarios()),
                criarCardCoberturas(indicadores.coberturasNoMes()),
                criarCardDiasIncompletos(indicadores.diasIncompletos()));
    }

    private VBox criarCardPlantaoDeHoje(List<PlantaoDoDiaItem> plantoes) {
        VBox card = criarCardBase("Plantão de hoje", VARIANTE_PRIMARIA);

        if (plantoes.isEmpty()) {
            // Critério de aceite da issue: o dia sem plantão tem estado próprio,
            // e ele diz o que fazer, não só que está vazio.
            Label vazio = new Label("Nenhum turno começa hoje. Monte a escala do mês "
                    + "para o plantão aparecer aqui.");
            vazio.getStyleClass().add("indicador-vazio");
            vazio.setMaxWidth(Double.MAX_VALUE);
            card.getChildren().add(vazio);
            return card;
        }

        for (PlantaoDoDiaItem plantao : plantoes) {
            card.getChildren().add(criarBlocoTurno(plantao));
        }
        return card;
    }

    /** Um turno do dia: tipo, horário e o contador de agentes com o selo de efetivo. */
    private VBox criarBlocoTurno(PlantaoDoDiaItem plantao) {
        Label tipo = new Label(plantao.tipoTurno());
        tipo.getStyleClass().add("indicador-turno-tipo");
        tipo.setWrapText(true);

        Label horario = new Label(descreverHorario(plantao.inicio(), plantao.fim()));
        horario.getStyleClass().add("indicador-turno-horario");

        Label agentes = new Label(plantao.agentes() + " de " + plantao.minimoExigido() + " agentes");
        agentes.getStyleClass().addAll("selo",
                plantao.completo() ? "selo-sucesso" : "selo-atencao");

        HBox rodape = new HBox(8, horario, agentes);
        rodape.setAlignment(Pos.CENTER_LEFT);

        VBox bloco = new VBox(5, tipo, rodape);
        bloco.getStyleClass().add("indicador-turno");
        return bloco;
    }

    /**
     * "07:00 – 19:00", ou "19:00 – 07:00 (03/09)" quando o turno vira o dia —
     * sem a data, um 12x36 noturno pareceria terminar antes de começar.
     */
    private String descreverHorario(LocalDateTime inicio, LocalDateTime fim) {
        String base = inicio.format(HORA) + " – " + fim.format(HORA);
        return fim.toLocalDate().isEqual(inicio.toLocalDate())
                ? base
                : base + " (" + fim.format(DIA_CURTO) + ")";
    }

    private VBox criarCardFuncionarios(ContagemFuncionarios contagem) {
        VBox card = criarCardBase("Funcionários ativos", VARIANTE_SUCESSO);
        card.getChildren().addAll(
                criarValor(contagem.ativos(), VARIANTE_SUCESSO),
                criarDetalhe(contagem.inativos() == 1
                        ? "1 inativo · " + contagem.total() + " cadastrados"
                        : contagem.inativos() + " inativos · " + contagem.total() + " cadastrados"));
        return card;
    }

    private VBox criarCardCoberturas(int coberturas) {
        VBox card = criarCardBase("Coberturas no mês", VARIANTE_NEUTRO);
        card.getChildren().addAll(
                criarValor(coberturas, VARIANTE_NEUTRO),
                criarDetalhe(coberturas == 1
                        ? "cobertura registrada no mês corrente"
                        : "coberturas registradas no mês corrente"));
        return card;
    }

    private VBox criarCardDiasIncompletos(int dias) {
        // Zero dia incompleto é uma boa notícia, então o card fica verde; a
        // partir de um, vira o mesmo âmbar que o calendário usa na célula.
        String variante = dias == 0 ? VARIANTE_SUCESSO : VARIANTE_ATENCAO;
        VBox card = criarCardBase("Dias incompletos", variante);
        card.getChildren().addAll(
                criarValor(dias, variante),
                criarDetalhe(dias == 0
                        ? "nenhum dia do mês está abaixo do mínimo"
                        : "dias do mês com turno abaixo do mínimo de agentes"));
        return card;
    }

    // -----------------------------------------------------------------
    // Faixa da semana (issue #54)
    // -----------------------------------------------------------------

    /**
     * Card da semana corrente: cabeçalho com o intervalo e, abaixo, a grade de
     * sete colunas que {@link #desenharSemana} preenche a cada carga.
     */
    private VBox criarCardSemana() {
        Label titulo = new Label("Semana atual");
        titulo.getStyleClass().add("titulo-2");

        rotuloSemana.getStyleClass().add("texto-secundario");

        semanaVazia.getStyleClass().add("indicador-vazio");
        semanaVazia.setWrapText(true);
        semanaVazia.setText("Nenhum turno nesta semana. Monte a escala do mês "
                + "em \"Montagem da escala\" para a semana aparecer aqui.");
        semanaVazia.setVisible(false);
        semanaVazia.setManaged(false);

        // Sete colunas de largura igual: a faixa é um calendário de uma linha
        // só, e uma coluna mais larga que a outra desalinharia os dias.
        for (int coluna = 0; coluna < DIAS_NA_SEMANA; coluna++) {
            ColumnConstraints restricao = new ColumnConstraints();
            restricao.setPercentWidth(100.0 / DIAS_NA_SEMANA);
            restricao.setFillWidth(true);
            faixaSemana.getColumnConstraints().add(restricao);
        }

        VBox card = new VBox(12, titulo, rotuloSemana, semanaVazia, faixaSemana);
        card.getStyleClass().add("card");
        return card;
    }

    private void desenharSemana(IndicadoresDashboard indicadores) {
        List<DiaDaSemana> semana = indicadores.semanaCorrente();
        rotuloSemana.setText(descreverIntervaloDaSemana(semana));

        boolean vazia = indicadores.semanaSemEscala();
        semanaVazia.setVisible(vazia);
        semanaVazia.setManaged(vazia);

        faixaSemana.getChildren().clear();
        for (int coluna = 0; coluna < semana.size(); coluna++) {
            DiaDaSemana dia = semana.get(coluna);
            faixaSemana.add(criarCabecalhoDaColuna(dia), coluna, 0);
            faixaSemana.add(criarCelulaDaSemana(dia), coluna, 1);
        }
    }

    /**
     * Nome do dia da semana no topo da coluna.
     *
     * <p>O índice vem da posição na lista, não de {@code DayOfWeek}: o serviço
     * já entrega os sete dias começando no domingo. Ainda assim a asserção
     * abaixo vale — em {@code java.time} {@code MONDAY} é 1 e {@code SUNDAY} é
     * 7, então o resto por 7 é o que traduz o dia da semana na coluna da faixa,
     * mesmo ajuste da grade do calendário (#41).</p>
     */
    private Label criarCabecalhoDaColuna(DiaDaSemana dia) {
        DayOfWeek diaDaSemana = dia.dia().getDayOfWeek();
        Label rotulo = new Label(NOMES_DIAS_SEMANA.get(diaDaSemana.getValue() % DIAS_NA_SEMANA));
        rotulo.getStyleClass().add("calendario-cabecalho-dia");
        rotulo.setMaxWidth(Double.MAX_VALUE);
        rotulo.setAlignment(Pos.CENTER);
        return rotulo;
    }

    /** Uma coluna da faixa: número do dia e, abaixo, um bloco por turno. */
    private VBox criarCelulaDaSemana(DiaDaSemana dia) {
        Label numero = new Label(String.valueOf(dia.dia().getDayOfMonth()));
        numero.getStyleClass().add("semana-numero-dia");

        VBox celula = new VBox(6, numero);
        celula.getStyleClass().add("semana-celula");
        if (dia.hoje()) {
            celula.getStyleClass().add("semana-celula-hoje");
        }

        if (dia.semTurnos()) {
            Label vazio = new Label("sem plantão");
            vazio.getStyleClass().add("semana-sem-turno");
            celula.getChildren().add(vazio);
            return celula;
        }

        for (TurnoResumido turno : dia.turnos()) {
            celula.getChildren().add(criarBlocoDaSemana(turno));
        }
        return celula;
    }

    /** Tipo de turno, horário e os agentes do turno, um por linha. */
    private VBox criarBlocoDaSemana(TurnoResumido turno) {
        Label tipo = new Label(turno.tipoTurno());
        tipo.getStyleClass().add("semana-tipo-turno");
        tipo.setWrapText(true);

        Label horario = new Label(descreverHorario(turno.inicio(), turno.fim()));
        horario.getStyleClass().add("semana-horario");

        VBox bloco = new VBox(2, tipo, horario);
        if (turno.postos().isEmpty()) {
            Label semAgentes = new Label("Sem agentes");
            semAgentes.getStyleClass().add("semana-sem-turno");
            bloco.getChildren().add(semAgentes);
        } else {
            // Nome curto na faixa: a coluna tem um sétimo da largura da tela e
            // o nome inteiro sairia cortado. A tabela abaixo mostra o completo.
            for (PostoDoTurno posto : turno.postos()) {
                Label agente = new Label(descreverPosto(posto, true));
                agente.getStyleClass().add("semana-agente");
                agente.setTooltip(new Tooltip(descreverPosto(posto, false)));
                bloco.getChildren().add(agente);
            }
        }
        return bloco;
    }

    /** "Semana de 30/08 a 05/09." */
    private String descreverIntervaloDaSemana(List<DiaDaSemana> semana) {
        if (semana.isEmpty()) {
            return "";
        }
        LocalDate domingo = semana.get(0).dia();
        LocalDate sabado = semana.get(semana.size() - 1).dia();
        return "Semana de " + domingo.format(DIA_CURTO) + " a " + sabado.format(DIA_CURTO) + ".";
    }

    // -----------------------------------------------------------------
    // Proximos plantoes (issue #54)
    // -----------------------------------------------------------------

    private VBox criarCardProximosPlantoes() {
        Label titulo = new Label("Próximos plantões");
        titulo.getStyleClass().add("titulo-2");

        Label subtitulo = new Label("Os turnos a partir de hoje, em ordem. "
                + "A agenda completa fica em \"Montagem da escala\".");
        subtitulo.getStyleClass().add("texto-secundario");
        subtitulo.setWrapText(true);

        configurarColunasProximos();
        tabelaProximos.setItems(proximos);
        tabelaProximos.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        tabelaProximos.setPlaceholder(new Label(
                "Nenhum plantão montado daqui para a frente."));
        // Altura fixa para caber o resumo inteiro sem rolagem própria: quem
        // rola é a página. Uma tabela rolável dentro de uma página rolável
        // captura o scroll do mouse e prende o usuário no meio da tela.
        // Ajustada para o cabeçalho mais as LIMITE linhas do resumo: mais que
        // isso deixa linhas vazias sobrando no rodapé do card.
        tabelaProximos.setPrefHeight(250);
        tabelaProximos.setMinHeight(250);

        VBox card = new VBox(12, titulo, subtitulo, tabelaProximos);
        card.getStyleClass().add("card");
        return card;
    }

    private void configurarColunasProximos() {
        TableColumn<TurnoResumido, String> colTipo = new TableColumn<>("Tipo de turno");
        colTipo.setCellValueFactory(linha -> new SimpleStringProperty(linha.getValue().tipoTurno()));
        colTipo.setPrefWidth(170);

        TableColumn<TurnoResumido, String> colInicio = new TableColumn<>("Início");
        colInicio.setCellValueFactory(linha ->
                new SimpleStringProperty(linha.getValue().inicio().format(DIA_E_HORA)));
        colInicio.setPrefWidth(110);

        TableColumn<TurnoResumido, String> colFim = new TableColumn<>("Fim");
        colFim.setCellValueFactory(linha ->
                new SimpleStringProperty(linha.getValue().fim().format(DIA_E_HORA)));
        colFim.setPrefWidth(110);

        TableColumn<TurnoResumido, String> colAgentes = new TableColumn<>("Funcionários escalados");
        colAgentes.setCellValueFactory(linha ->
                new SimpleStringProperty(descreverAgentes(linha.getValue())));
        colAgentes.setPrefWidth(280);

        TableColumn<TurnoResumido, Void> colSituacao = new TableColumn<>("Situação");
        colSituacao.setCellFactory(coluna -> new TableCell<>() {
            @Override
            protected void updateItem(Void item, boolean vazio) {
                super.updateItem(item, vazio);
                if (vazio || getIndex() >= getTableView().getItems().size()) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                TurnoResumido turno = getTableView().getItems().get(getIndex());
                boolean completo = turno.efetivo().completo();

                Label selo = new Label(completo ? "Confirmado" : "Incompleto");
                // As mesmas duas cores do calendário: verde para o turno que
                // bateu o mínimo, âmbar para o que cobra ação do gestor.
                selo.getStyleClass().setAll("selo", completo ? "selo-sucesso" : "selo-atencao");
                selo.setTooltip(new Tooltip(turno.efetivo().mensagem()));

                setText(null);
                setGraphic(selo);
                setAlignment(Pos.CENTER);
            }
        });
        colSituacao.setPrefWidth(130);

        tabelaProximos.getColumns().setAll(List.of(
                colTipo, colInicio, colFim, colAgentes, colSituacao));
    }

    /** Nomes completos, separados por vírgula — aqui a coluna é larga. */
    private String descreverAgentes(TurnoResumido turno) {
        if (turno.postos().isEmpty()) {
            return "— sem escala —";
        }
        return turno.postos().stream()
                .map(posto -> descreverPosto(posto, false))
                .collect(Collectors.joining(", "));
    }

    /**
     * Um posto ocupado. Quando houve cobertura sai "Ausente → Substituto", o
     * mesmo formato do calendário — quem lê as duas telas reconhece a marca.
     */
    private String descreverPosto(PostoDoTurno posto, boolean curto) {
        String titular = curto ? nomeCurto(posto.titular()) : posto.titular();
        if (!posto.coberto()) {
            return titular;
        }
        return titular + " → " + (curto ? nomeCurto(posto.substituto()) : posto.substituto());
    }

    /**
     * Primeiro nome mais a inicial do último sobrenome, como no calendário:
     * ainda distingue dois agentes de mesmo primeiro nome e cabe na coluna.
     */
    private String nomeCurto(String nome) {
        if (nome == null || nome.isBlank()) {
            return "(sem nome)";
        }
        String[] partes = nome.trim().split("\\s+");
        if (partes.length == 1) {
            return partes[0];
        }
        return partes[0] + " " + partes[partes.length - 1].charAt(0) + ".";
    }

    // -----------------------------------------------------------------
    // Pendencias e alertas (issue #55)
    // -----------------------------------------------------------------

    /**
     * Card das pendências, no rodapé da tela: cabeçalho com a contagem e, abaixo,
     * uma linha por alerta que {@link #desenharAlertas} preenche a cada carga.
     */
    private VBox criarCardAlertas() {
        Label titulo = new Label("Pendências e alertas");
        titulo.getStyleClass().add("titulo-2");

        Label subtitulo = new Label("Itens que exigem ação antes de publicar a escala do mês.");
        subtitulo.getStyleClass().add("texto-secundario");
        subtitulo.setWrapText(true);

        contadorAlertas.getStyleClass().add("texto-secundario");

        semAlertas.getStyleClass().add("indicador-vazio");
        semAlertas.setWrapText(true);
        semAlertas.setText("Nenhuma pendência: o efetivo do mês está completo, "
                + "não há funcionário desativado escalado e o próximo mês já foi iniciado.");
        semAlertas.setVisible(false);
        semAlertas.setManaged(false);

        VBox card = new VBox(12, titulo, subtitulo, contadorAlertas, semAlertas, listaAlertas);
        card.getStyleClass().add("card");
        return card;
    }

    private void desenharAlertas(IndicadoresDashboard indicadores) {
        List<AlertaDashboard> alertas = indicadores.alertas();

        boolean vazio = indicadores.semPendencias();
        semAlertas.setVisible(vazio);
        semAlertas.setManaged(vazio);
        contadorAlertas.setText(alertas.size() == 1
                ? "1 pendência registrada"
                : alertas.size() + " pendências registradas");

        listaAlertas.getChildren().clear();
        for (AlertaDashboard alerta : alertas) {
            listaAlertas.getChildren().add(criarLinhaDeAlerta(alerta));
        }
    }

    /**
     * Uma pendência: selo da severidade, título e o que fazer a respeito.
     *
     * <p>A cor é a única coisa que a severidade decide aqui, e ela nunca vem
     * sozinha — o selo é escrito ("Crítico", "Atenção", "Informativo") e o
     * título já diz o tamanho do problema. É a mesma regra da issue #45: quem
     * não distingue as cores continua lendo a linha inteira.</p>
     */
    private VBox criarLinhaDeAlerta(AlertaDashboard alerta) {
        Label selo = new Label(rotuloDaSeveridade(alerta.severidade()));
        selo.getStyleClass().addAll("selo", classeDoSelo(alerta.severidade()));
        selo.setMinWidth(Region.USE_PREF_SIZE);

        Label titulo = new Label(alerta.titulo());
        titulo.getStyleClass().add("alerta-titulo");
        titulo.setWrapText(true);
        HBox.setHgrow(titulo, Priority.ALWAYS);

        HBox cabecalho = new HBox(8, selo, titulo);
        cabecalho.setAlignment(Pos.CENTER_LEFT);

        Label descricao = new Label(alerta.descricao());
        descricao.getStyleClass().add("alerta-descricao");
        descricao.setWrapText(true);

        VBox linha = new VBox(3, cabecalho, descricao);
        linha.getStyleClass().addAll("alerta-linha", "alerta-linha-" + classeDaSeveridade(alerta.severidade()));
        linha.setMaxWidth(Double.MAX_VALUE);
        return linha;
    }

    /**
     * Severidade para classe de selo. O mapa vive aqui, e não na enumeração:
     * {@code SeveridadeAlerta} é do backend e não conhece CSS.
     */
    private String classeDoSelo(SeveridadeAlerta severidade) {
        return switch (severidade) {
            case CRITICO -> "selo-perigo";
            case ATENCAO -> "selo-atencao";
            case INFORMATIVO -> "selo-neutro";
        };
    }

    /** Sufixo da classe da faixa lateral da linha, na mesma família de cor do selo. */
    private String classeDaSeveridade(SeveridadeAlerta severidade) {
        return switch (severidade) {
            case CRITICO -> "critico";
            case ATENCAO -> "atencao";
            case INFORMATIVO -> "informativo";
        };
    }

    private String rotuloDaSeveridade(SeveridadeAlerta severidade) {
        return switch (severidade) {
            case CRITICO -> "Crítico";
            case ATENCAO -> "Atenção";
            case INFORMATIVO -> "Informativo";
        };
    }

    // -----------------------------------------------------------------
    // Pecas comuns dos cards
    // -----------------------------------------------------------------

    private VBox criarCardBase(String rotulo, String variante) {
        Label titulo = new Label(rotulo);
        titulo.getStyleClass().add("indicador-rotulo");

        VBox card = new VBox(8, titulo);
        card.getStyleClass().addAll("card", "card-indicador", "card-indicador-" + variante);
        card.setMaxWidth(Double.MAX_VALUE);
        GridPane.setHgrow(card, Priority.ALWAYS);
        return card;
    }

    private Label criarValor(int valor, String variante) {
        Label rotulo = new Label(String.valueOf(valor));
        rotulo.getStyleClass().addAll("indicador-valor", "indicador-valor-" + variante);
        return rotulo;
    }

    private Label criarDetalhe(String texto) {
        Label rotulo = new Label(texto);
        rotulo.getStyleClass().add("indicador-detalhe");
        rotulo.setWrapText(true);
        return rotulo;
    }

    // -----------------------------------------------------------------
    // Carga dos indicadores
    // -----------------------------------------------------------------

    private void carregar() {
        if (tarefaAtual != null && tarefaAtual.isRunning()) {
            tarefaAtual.cancel();
        }
        LocalDate dia = hoje.get();
        rotuloSubtitulo.setText(descreverDia(dia));
        mostrarCarregando(true);

        Task<IndicadoresDashboard> tarefa = new Task<>() {
            @Override
            protected IndicadoresDashboard call() {
                return dashboardService.carregar(dia);
            }
        };
        tarefa.setOnSucceeded(evento -> {
            if (tarefa != tarefaAtual) {
                return;
            }
            mostrarCarregando(false);
            esconderErro();
            IndicadoresDashboard indicadores = tarefa.getValue();
            desenharCards(indicadores);
            desenharSemana(indicadores);
            proximos.setAll(indicadores.proximosPlantoes());
            desenharAlertas(indicadores);
        });
        tarefa.setOnFailed(evento -> {
            if (tarefa != tarefaAtual) {
                return;
            }
            mostrarCarregando(false);
            exibirErro("Não foi possível carregar a visão geral. Verifique se o banco "
                    + "de dados está acessível e use \"Atualizar\" para tentar de novo.");
            LogAplicacao.registrarErro(
                    "Falha ao carregar a visão geral", tarefa.getException());
        });

        tarefaAtual = tarefa;
        executor.execute(tarefa);
    }

    private void mostrarCarregando(boolean carregando) {
        indicador.setVisible(carregando);
        indicador.setManaged(carregando);
    }

    private void exibirErro(String texto) {
        mensagemErro.setText(texto);
        mensagemErro.setVisible(true);
        mensagemErro.setManaged(true);
    }

    private void esconderErro() {
        mensagemErro.setVisible(false);
        mensagemErro.setManaged(false);
    }

    private String maiusculaInicial(String texto) {
        return texto.isEmpty() ? texto
                : texto.substring(0, 1).toUpperCase(PT_BR) + texto.substring(1);
    }
}
