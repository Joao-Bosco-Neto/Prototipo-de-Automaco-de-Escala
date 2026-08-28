package br.edu.sistemaescala.frontend.controller;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import br.edu.sistemaescala.backend.model.EscalaFuncionario;
import br.edu.sistemaescala.backend.model.EscalaTurno;
import br.edu.sistemaescala.backend.model.Funcionario;
import br.edu.sistemaescala.backend.repository.EscalaFuncionarioRepository;
import br.edu.sistemaescala.backend.repository.EscalaTurnoRepository;
import br.edu.sistemaescala.backend.repository.RepositoryException;
import br.edu.sistemaescala.backend.repository.TipoTurnoRepository;
import br.edu.sistemaescala.backend.repository.jdbc.EscalaFuncionarioRepositoryJdbc;
import br.edu.sistemaescala.backend.repository.jdbc.FuncionarioRepositoryJdbc;
import br.edu.sistemaescala.backend.repository.jdbc.TipoTurnoRepositoryJdbc;
import br.edu.sistemaescala.backend.service.FuncionarioService;
import br.edu.sistemaescala.backend.service.FuncionarioServiceImpl;
import br.edu.sistemaescala.backend.service.GeradorRodizioService;
import br.edu.sistemaescala.backend.service.GeradorRodizioServiceImpl;
import br.edu.sistemaescala.backend.service.RegraEscalaService;
import br.edu.sistemaescala.backend.service.RegraEscalaServiceImpl;
import br.edu.sistemaescala.backend.service.ResultadoGeracao;
import br.edu.sistemaescala.frontend.DialogUtil;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.VBox;

/**
 * Calendario mensal de montagem da escala (issue #41).
 *
 * Monta uma grade de 7 colunas (domingo a sabado) com uma celula por dia do
 * mes. Cada celula mostra o numero do dia e, para cada turno daquele dia, o
 * nome do tipo de turno e os agentes alocados — um mesmo dia pode ter mais de
 * um turno (o 12x36 tem diurno e noturno na mesma data), entao a celula
 * sempre trata uma lista.
 *
 * A issue original falava em mostrar "a equipe" na celula; equipes de rodizio
 * foram substituidas por tipo_turno e nao existem mais no modelo, por isso a
 * celula mostra o tipo de turno.
 *
 * Os dados de um mes inteiro vem de uma unica chamada a
 * {@link EscalaTurnoRepository#buscarPorPeriodo} — nunca uma consulta por dia.
 *
 * A tela e de duas colunas: o cartao do calendario no centro e o painel de
 * atribuicao da issue #42 a direita. O clique num dia dispara o
 * {@code aoSelecionarDia} recebido no construtor e tambem alimenta esse
 * painel; o painel, por sua vez, chama {@link #recarregar()} depois de cada
 * alteracao, para a celula do dia refletir a mudanca na hora.
 *
 * O botao "Gerar rodizio" chama o {@link GeradorRodizioService} (issue #43),
 * que preenche o mes exibido de uma vez; sobrescrever um mes que ja tem
 * escala passa por confirmacao antes.
 *
 * Os quatro estados visuais e a legenda sao escopo da issue #45: aqui existe
 * apenas o destaque do dia selecionado.
 */
public class MontagemEscalaController {

    /** Cabecalho da grade, na ordem em que as colunas aparecem (domingo primeiro). */
    private static final List<String> NOMES_DIAS_SEMANA =
            List.of("Domingo", "Segunda", "Terça", "Quarta", "Quinta", "Sexta", "Sábado");

    private static final int COLUNAS = 7;

    private static final Locale LOCALE_BR = Locale.forLanguageTag("pt-BR");
    private static final DateTimeFormatter FORMATO_MES_ANO =
            DateTimeFormatter.ofPattern("MMMM 'de' yyyy", LOCALE_BR);

    private static final String CLASSE_DIA_SELECIONADO = "calendario-dia-selecionado";

    private final EscalaTurnoRepository escalaTurnoRepository;
    private final GeradorRodizioService geradorRodizioService;
    private final Consumer<LocalDate> aoSelecionarDia;
    private final PainelAtribuicaoController painelAtribuicao;

    private final Label rotuloMesAno = new Label();
    private final Label rotuloMensagem = new Label();
    private final GridPane grade = new GridPane();
    private final ScrollPane rolagem = new ScrollPane(grade);

    private YearMonth mesExibido = YearMonth.now();
    private LocalDate diaSelecionado;
    /** Se o mes exibido ja tem turnos, para o botao saber quando pedir confirmacao. */
    private boolean mesExibidoTemEscala;
    /** Celula com o destaque de selecao no momento, para tirar o destaque dela ao trocar. */
    private Region celulaSelecionada;

    /** Monta as dependencias do painel a partir dos repositorios JDBC padrao. */
    public MontagemEscalaController(EscalaTurnoRepository escalaTurnoRepository) {
        this(escalaTurnoRepository, new EscalaFuncionarioRepositoryJdbc(), new TipoTurnoRepositoryJdbc(),
                new FuncionarioServiceImpl(new FuncionarioRepositoryJdbc()), new RegraEscalaServiceImpl(),
                dia -> { });
    }

    public MontagemEscalaController(EscalaTurnoRepository escalaTurnoRepository,
                                    EscalaFuncionarioRepository escalaFuncionarioRepository,
                                    TipoTurnoRepository tipoTurnoRepository,
                                    FuncionarioService funcionarioService,
                                    RegraEscalaService regraEscalaService) {
        this(escalaTurnoRepository, escalaFuncionarioRepository, tipoTurnoRepository,
                funcionarioService, regraEscalaService, dia -> { });
    }

    public MontagemEscalaController(EscalaTurnoRepository escalaTurnoRepository,
                                    EscalaFuncionarioRepository escalaFuncionarioRepository,
                                    TipoTurnoRepository tipoTurnoRepository,
                                    FuncionarioService funcionarioService,
                                    RegraEscalaService regraEscalaService,
                                    Consumer<LocalDate> aoSelecionarDia) {
        this(escalaTurnoRepository, escalaFuncionarioRepository, tipoTurnoRepository,
                funcionarioService, regraEscalaService, new GeradorRodizioServiceImpl(), aoSelecionarDia);
    }

    public MontagemEscalaController(EscalaTurnoRepository escalaTurnoRepository,
                                    EscalaFuncionarioRepository escalaFuncionarioRepository,
                                    TipoTurnoRepository tipoTurnoRepository,
                                    FuncionarioService funcionarioService,
                                    RegraEscalaService regraEscalaService,
                                    GeradorRodizioService geradorRodizioService,
                                    Consumer<LocalDate> aoSelecionarDia) {
        this.escalaTurnoRepository = escalaTurnoRepository;
        this.geradorRodizioService = geradorRodizioService;
        this.aoSelecionarDia = aoSelecionarDia != null ? aoSelecionarDia : dia -> { };
        // O painel recarrega a grade por this::recarregar depois de criar
        // turno, alocar ou remover agente.
        this.painelAtribuicao = new PainelAtribuicaoController(
                escalaTurnoRepository, escalaFuncionarioRepository, tipoTurnoRepository,
                funcionarioService, regraEscalaService, this::recarregar);
    }

    public Parent criarTela() {
        VBox raiz = new VBox(18);
        raiz.setPadding(new Insets(24));
        raiz.getStyleClass().add("area-conteudo");

        VBox cartaoCalendario = new VBox(14);
        cartaoCalendario.getStyleClass().add("card");

        configurarGrade();

        // O calendario rola quando as celulas de um mes cheio nao cabem na
        // altura da janela; a largura acompanha a area de conteudo.
        rolagem.setFitToWidth(true);
        rolagem.setFitToHeight(true);
        rolagem.getStyleClass().add("calendario-rolagem");
        VBox.setVgrow(rolagem, Priority.ALWAYS);

        cartaoCalendario.getChildren().addAll(criarBarraNavegacao(), rotuloMensagem, rolagem);

        // Duas colunas: calendario ocupando o espaco livre e o painel de
        // atribuicao (#42) fixo a direita.
        HBox colunas = new HBox(20, cartaoCalendario, painelAtribuicao.criarPainel());
        HBox.setHgrow(cartaoCalendario, Priority.ALWAYS);

        raiz.getChildren().addAll(criarCabecalho(), colunas);
        VBox.setVgrow(colunas, Priority.ALWAYS);

        esconderMensagem();
        renderizarMes();

        return raiz;
    }

    private VBox criarCabecalho() {
        Label titulo = new Label("Montagem da escala");
        titulo.getStyleClass().add("titulo-1");

        Label subtitulo = new Label(
                "Calendário mensal com os turnos já montados. Clique em um dia para selecioná-lo.");
        subtitulo.getStyleClass().add("texto-secundario");

        return new VBox(4, titulo, subtitulo);
    }

    private HBox criarBarraNavegacao() {
        Button anterior = new Button("<");
        anterior.getStyleClass().add("button-secundario");
        anterior.setOnAction(evento -> trocarMes(-1));

        Button proximo = new Button(">");
        proximo.getStyleClass().add("button-secundario");
        proximo.setOnAction(evento -> trocarMes(1));

        rotuloMesAno.getStyleClass().add("calendario-mes-ano");

        Region espacador = new Region();
        HBox.setHgrow(espacador, Priority.ALWAYS);

        Button gerarRodizio = new Button("Gerar rodízio");
        gerarRodizio.getStyleClass().add("button-primario");
        gerarRodizio.setOnAction(evento -> gerarRodizioDoMesExibido());

        HBox barra = new HBox(12, anterior, rotuloMesAno, proximo, espacador, gerarRodizio);
        barra.setAlignment(Pos.CENTER_LEFT);
        return barra;
    }

    // -----------------------------------------------------------------
    // Geracao automatica do rodizio (issue #43)
    // -----------------------------------------------------------------

    /**
     * Gera o rodizio do mes exibido. Um mes que ja tem escala so e gerado de
     * novo depois de confirmacao explicita, porque a escala atual e
     * substituida inteira.
     */
    private void gerarRodizioDoMesExibido() {
        boolean sobrescrever = mesExibidoTemEscala;
        if (sobrescrever && !confirmarSubstituicao()) {
            return;
        }

        try {
            ResultadoGeracao resultado = geradorRodizioService.gerarMes(mesExibido, sobrescrever);

            // O calendario e recarregado mesmo quando nada foi gerado: e o que
            // mantem a tela fiel ao banco depois da tentativa.
            limparSelecao();
            painelAtribuicao.mostrarDia(null);
            renderizarMes();

            mostrarResultado(resultado);

        } catch (RepositoryException excecao) {
            DialogUtil.mostrarErroBancoIndisponivel("Não foi possível gerar o rodízio");
        }
    }

    private boolean confirmarSubstituicao() {
        return DialogUtil.mostrarConfirmacao("Substituir a escala de " + descreverMes(mesExibido) + "?",
                "Este mês já tem escala montada. Gerar o rodízio agora apaga todos os turnos e "
                + "alocações existentes do mês e monta a escala do zero.\n\n"
                + "Esta ação não pode ser desfeita.");
    }

    /** Os numeros da geracao vao para o dialogo; o aviso de efetivo so aparece quando existe. */
    private void mostrarResultado(ResultadoGeracao resultado) {
        if (!resultado.gerado()) {
            DialogUtil.mostrarInformacao("Rodízio não gerado", resultado.mensagem());
            return;
        }

        StringBuilder detalhe = new StringBuilder()
                .append("Turnos criados: ").append(resultado.turnosCriados()).append('\n')
                .append("Alocações feitas: ").append(resultado.alocacoesCriadas()).append('\n')
                .append("Dias sem efetivo suficiente: ").append(resultado.diasSemEfetivoSuficiente());

        if (resultado.diasSemEfetivoSuficiente() > 0) {
            detalhe.append("\n\nOs dias incompletos ficaram abaixo do mínimo de agentes e "
                    + "precisam de ajuste manual no painel de atribuição.");
        }

        DialogUtil.mostrarInformacao("Rodízio de " + descreverMes(mesExibido) + " gerado",
                detalhe.toString());
    }

    /**
     * Recarrega os turnos do mes exibido e redesenha a grade, preservando o
     * dia selecionado. E o que o painel de atribuicao chama depois de criar
     * turno, alocar ou remover um agente, para a celula mudar na hora.
     */
    public void recarregar() {
        renderizarMes();
    }

    /** Troca o mes exibido, limpa a selecao e recarrega os dados do novo intervalo. */
    private void trocarMes(int meses) {
        mesExibido = mesExibido.plusMonths(meses);
        limparSelecao();
        painelAtribuicao.mostrarDia(null);
        renderizarMes();
    }

    private void configurarGrade() {
        grade.getStyleClass().add("calendario-grade");
        grade.setHgap(6);
        grade.setVgap(6);

        // As 7 colunas dividem a largura em partes iguais para a grade
        // acompanhar o redimensionamento da janela.
        for (int coluna = 0; coluna < COLUNAS; coluna++) {
            ColumnConstraints restricao = new ColumnConstraints();
            restricao.setPercentWidth(100.0 / COLUNAS);
            restricao.setHgrow(Priority.ALWAYS);
            grade.getColumnConstraints().add(restricao);
        }
    }

    // -----------------------------------------------------------------
    // Montagem da grade
    // -----------------------------------------------------------------

    private void renderizarMes() {
        rotuloMesAno.setText(descreverMes(mesExibido));

        grade.getChildren().clear();
        grade.getRowConstraints().clear();

        Map<LocalDate, List<EscalaTurno>> turnosPorDia = carregarTurnosDoMes();

        for (int coluna = 0; coluna < COLUNAS; coluna++) {
            grade.add(criarCabecalhoDiaSemana(NOMES_DIAS_SEMANA.get(coluna)), coluna, 0);
        }

        LocalDate primeiroDia = mesExibido.atDay(1);
        int deslocamento = colunaDoDiaDaSemana(primeiroDia.getDayOfWeek());
        int diasNoMes = mesExibido.lengthOfMonth();
        // Numero de semanas ocupadas pelo mes: varia entre 4 e 6, nunca fixo em 5.
        int linhasDeDias = (int) Math.ceil((deslocamento + diasNoMes) / (double) COLUNAS);

        // Linha 0 e o cabecalho dos dias da semana; as demais crescem juntas.
        RowConstraints restricaoCabecalho = new RowConstraints();
        restricaoCabecalho.setVgrow(Priority.NEVER);
        grade.getRowConstraints().add(restricaoCabecalho);

        for (int linha = 0; linha < linhasDeDias; linha++) {
            RowConstraints restricao = new RowConstraints();
            restricao.setVgrow(Priority.ALWAYS);
            restricao.setMinHeight(84);
            grade.getRowConstraints().add(restricao);
        }

        int totalCelulas = linhasDeDias * COLUNAS;
        for (int celula = 0; celula < totalCelulas; celula++) {
            int coluna = celula % COLUNAS;
            int linha = celula / COLUNAS + 1;
            int numeroDoDia = celula - deslocamento + 1;

            Region conteudo;
            if (numeroDoDia < 1 || numeroDoDia > diasNoMes) {
                conteudo = criarCelulaForaDoMes();
            } else {
                LocalDate dia = mesExibido.atDay(numeroDoDia);
                conteudo = criarCelulaDoDia(dia, turnosPorDia.getOrDefault(dia, List.of()));
            }
            grade.add(conteudo, coluna, linha);
        }

        // Volta ao topo ao trocar de mes: sem isso a grade nova herda a
        // rolagem da anterior e o cabecalho dos dias da semana fica fora
        // da area visivel. runLater porque a altura so e conhecida depois
        // do proximo layout.
        Platform.runLater(() -> rolagem.setVvalue(0));
    }

    /**
     * Converte o dia da semana do java.time para a coluna da grade. Em
     * java.time MONDAY vale 1 e SUNDAY vale 7, mas a grade comeca no domingo:
     * o resto da divisao por 7 leva SUNDAY para a coluna 0 e mantem
     * MONDAY..SATURDAY em 1..6.
     */
    private int colunaDoDiaDaSemana(DayOfWeek diaDaSemana) {
        return diaDaSemana.getValue() % COLUNAS;
    }

    private Label criarCabecalhoDiaSemana(String nome) {
        Label rotulo = new Label(nome);
        rotulo.getStyleClass().add("calendario-cabecalho-dia");
        rotulo.setMaxWidth(Double.MAX_VALUE);
        rotulo.setAlignment(Pos.CENTER);
        return rotulo;
    }

    /** Preenchimento do inicio e do fim da grade: apagado e sem tratador de clique. */
    private Region criarCelulaForaDoMes() {
        VBox celula = new VBox();
        celula.getStyleClass().addAll("calendario-celula-dia", "calendario-celula-fora-mes");
        celula.setMaxWidth(Double.MAX_VALUE);
        celula.setMaxHeight(Double.MAX_VALUE);
        return celula;
    }

    private Region criarCelulaDoDia(LocalDate dia, List<EscalaTurno> turnosDoDia) {
        VBox celula = new VBox(4);
        celula.getStyleClass().add("calendario-celula-dia");
        celula.setMaxWidth(Double.MAX_VALUE);
        celula.setMaxHeight(Double.MAX_VALUE);

        Label numero = new Label(String.valueOf(dia.getDayOfMonth()));
        numero.getStyleClass().add("calendario-numero-dia");
        celula.getChildren().add(numero);

        for (EscalaTurno turno : turnosDoDia) {
            celula.getChildren().add(criarBlocoTurno(turno));
        }

        celula.setOnMouseClicked(evento -> selecionar(dia, celula));

        if (dia.equals(diaSelecionado)) {
            aplicarDestaque(celula);
        }
        return celula;
    }

    /** Um bloco por turno: nome do tipo de turno e os agentes alocados nele. */
    private VBox criarBlocoTurno(EscalaTurno turno) {
        Label nomeTipoTurno = new Label(descreverTipoTurno(turno));
        nomeTipoTurno.getStyleClass().add("calendario-tipo-turno");
        nomeTipoTurno.setWrapText(true);

        Label agentes = new Label(descreverAgentes(turno));
        agentes.getStyleClass().add("calendario-agentes");
        agentes.setWrapText(true);

        VBox bloco = new VBox(1, nomeTipoTurno, agentes);
        bloco.getStyleClass().add("calendario-bloco-turno");
        bloco.setMaxWidth(Double.MAX_VALUE);
        return bloco;
    }

    private String descreverTipoTurno(EscalaTurno turno) {
        return turno.getTipoTurno() != null && turno.getTipoTurno().getNome() != null
                ? turno.getTipoTurno().getNome()
                : "Turno sem tipo";
    }

    private String descreverAgentes(EscalaTurno turno) {
        List<EscalaFuncionario> alocacoes = turno.getAgentes();
        if (alocacoes == null || alocacoes.isEmpty()) {
            return "Sem agentes";
        }
        return alocacoes.stream()
                .map(EscalaFuncionario::getFuncionario)
                .filter(funcionario -> funcionario != null)
                .map(Funcionario::getNome)
                .map(this::nomeCurto)
                .collect(Collectors.joining(", "));
    }

    /**
     * Nome curto para caber na celula: primeiro nome mais a inicial do ultimo
     * sobrenome, que ainda distingue dois agentes de mesmo primeiro nome.
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
    // Selecao
    // -----------------------------------------------------------------

    /** Marca o dia clicado como selecionado (apenas um por vez) e avisa quem escuta. */
    private void selecionar(LocalDate dia, Region celula) {
        if (celulaSelecionada != null) {
            celulaSelecionada.getStyleClass().remove(CLASSE_DIA_SELECIONADO);
        }
        diaSelecionado = dia;
        aplicarDestaque(celula);
        painelAtribuicao.mostrarDia(dia);
        aoSelecionarDia.accept(dia);
    }

    private void aplicarDestaque(Region celula) {
        if (!celula.getStyleClass().contains(CLASSE_DIA_SELECIONADO)) {
            celula.getStyleClass().add(CLASSE_DIA_SELECIONADO);
        }
        celulaSelecionada = celula;
    }

    private void limparSelecao() {
        if (celulaSelecionada != null) {
            celulaSelecionada.getStyleClass().remove(CLASSE_DIA_SELECIONADO);
        }
        celulaSelecionada = null;
        diaSelecionado = null;
    }

    // -----------------------------------------------------------------
    // Dados
    // -----------------------------------------------------------------

    /**
     * Uma unica consulta traz o mes inteiro com tipoTurno e agentes ja
     * hidratados; aqui os turnos so sao agrupados por data de inicio.
     */
    private Map<LocalDate, List<EscalaTurno>> carregarTurnosDoMes() {
        Map<LocalDate, List<EscalaTurno>> porDia = new LinkedHashMap<>();
        try {
            List<EscalaTurno> turnos = escalaTurnoRepository.buscarPorPeriodo(
                    mesExibido.atDay(1).atStartOfDay(),
                    mesExibido.plusMonths(1).atDay(1).atStartOfDay());
            for (EscalaTurno turno : turnos) {
                if (turno.getInicio() == null) {
                    continue;
                }
                porDia.computeIfAbsent(turno.getInicio().toLocalDate(), data -> new ArrayList<>())
                        .add(turno);
            }
            mesExibidoTemEscala = !turnos.isEmpty();
            esconderMensagem();
        } catch (RepositoryException excecao) {
            // A grade continua sendo desenhada vazia: navegar entre meses
            // precisa funcionar mesmo com o banco fora do ar.
            mesExibidoTemEscala = false;
            exibirMensagem("Não foi possível carregar os turnos deste mês.");
        }
        return porDia;
    }

    private String descreverMes(YearMonth mes) {
        String texto = mes.atDay(1).format(FORMATO_MES_ANO);
        return texto.substring(0, 1).toUpperCase(LOCALE_BR) + texto.substring(1);
    }

    private void exibirMensagem(String mensagem) {
        rotuloMensagem.setText(mensagem);
        rotuloMensagem.getStyleClass().setAll("selo", "selo-perigo");
        rotuloMensagem.setVisible(true);
        rotuloMensagem.setManaged(true);
    }

    private void esconderMensagem() {
        rotuloMensagem.setText("");
        rotuloMensagem.setVisible(false);
        rotuloMensagem.setManaged(false);
    }
}
