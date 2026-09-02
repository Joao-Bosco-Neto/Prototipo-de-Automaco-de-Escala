package br.edu.sistemaescala.frontend.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

import br.edu.sistemaescala.LogAplicacao;
import br.edu.sistemaescala.backend.service.ContagemFuncionarios;
import br.edu.sistemaescala.backend.service.DashboardService;
import br.edu.sistemaescala.backend.service.DashboardServiceImpl;
import br.edu.sistemaescala.backend.service.IndicadoresDashboard;
import br.edu.sistemaescala.backend.service.PlantaoDoDiaItem;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Tela "Visão geral" (issue #53): os quatro cards de indicadores do mês
 * corrente — plantão de hoje, funcionários ativos, coberturas no mês e dias
 * com efetivo incompleto.
 *
 * <p>Sem regra de negócio aqui: o {@link DashboardService} devolve os quatro
 * números prontos num {@link IndicadoresDashboard} e esta classe só decide como
 * cada um vira texto na tela.</p>
 *
 * <p>A carga roda numa {@link Task} em thread daemon, como na tela de
 * exportação: são quatro consultas ao banco, e a JavaFX Application Thread não
 * pode ficar parada esperando por elas. O indicador de carregamento cobre o
 * intervalo e a falha vira mensagem na própria tela, não diálogo — o dashboard
 * é a primeira coisa que aparece depois do login, e um alerta modal no meio da
 * abertura do shell trancaria a navegação.</p>
 *
 * <p>Um detalhe do card "Plantão de hoje": ele mostra o <b>tipo de turno</b>. A
 * issue falava em "equipe", mas equipes de rodízio foram substituídas por
 * {@code tipo_turno} e não existe campo de equipe no modelo.</p>
 */
public class DashboardController {

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DIA_CURTO = DateTimeFormatter.ofPattern("dd/MM");

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

        raiz.getChildren().addAll(criarCabecalho(), mensagemErro, faixaCards);

        carregar();
        return raiz;
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

        Label horario = new Label(descreverHorario(plantao));
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
    private String descreverHorario(PlantaoDoDiaItem plantao) {
        LocalDateTime inicio = plantao.inicio();
        LocalDateTime fim = plantao.fim();
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
            desenharCards(tarefa.getValue());
        });
        tarefa.setOnFailed(evento -> {
            if (tarefa != tarefaAtual) {
                return;
            }
            mostrarCarregando(false);
            exibirErro("Não foi possível carregar os indicadores. Verifique se o banco "
                    + "de dados está acessível e use \"Atualizar\" para tentar de novo.");
            LogAplicacao.registrarErro(
                    "Falha ao carregar os indicadores da visão geral", tarefa.getException());
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
