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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import br.edu.sistemaescala.LogAplicacao;
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
import br.edu.sistemaescala.backend.service.LimpezaEscalaService;
import br.edu.sistemaescala.backend.service.LimpezaEscalaServiceImpl;
import br.edu.sistemaescala.backend.service.RegraEscalaService;
import br.edu.sistemaescala.backend.service.RegraEscalaServiceImpl;
import br.edu.sistemaescala.backend.service.ResultadoGeracao;
import br.edu.sistemaescala.backend.service.ResultadoEfetivo;
import br.edu.sistemaescala.backend.service.ResultadoLimpeza;
import br.edu.sistemaescala.backend.service.ResumoEscalaMes;
import br.edu.sistemaescala.frontend.DialogUtil;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
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
 * escala passa por confirmacao antes. O botao "Limpar mes" chama o
 * {@link LimpezaEscalaService} (issue #44) e exige dupla confirmacao, porque
 * apaga a escala inteira sem colocar nada no lugar.
 *
 * Navegacao entre meses (issue #46): trocar de mes consulta o banco numa
 * {@link Task}, fora da thread da interface, e so desenha a grade quando o
 * resultado chega. O {@link #recarregar()} usado pelo painel de atribuicao,
 * pelo gerador e pela limpeza continua sincrono de proposito — ver o javadoc
 * dele.
 *
 * Estados visuais da celula (issue #45): a celula ganha uma classe de fundo
 * conforme o dia esteja com plantao completo, com efetivo incompleto ou com
 * cobertura registrada — ver {@link #estadoDoDia}. O destaque de selecao
 * continua sendo borda, entao convive com qualquer um dos tres. A legenda no
 * rodape do cartao usa as mesmas classes das celulas.
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

    /** Classes de estado da celula (issue #45), na ordem de precedencia de {@link #estadoDoDia}. */
    private static final String CLASSE_DIA_INCOMPLETO = "calendario-dia-incompleto";
    private static final String CLASSE_DIA_COBERTURA = "calendario-dia-cobertura";
    private static final String CLASSE_DIA_COMPLETO = "calendario-dia-completo";

    private final EscalaTurnoRepository escalaTurnoRepository;
    private final GeradorRodizioService geradorRodizioService;
    private final LimpezaEscalaService limpezaEscalaService;
    /** Fonte do estado da celula: quem decide se um turno bateu o minimo e a regra da #23. */
    private final RegraEscalaService regraEscalaService;
    private final Consumer<LocalDate> aoSelecionarDia;
    private final PainelAtribuicaoController painelAtribuicao;

    private final Label rotuloMesAno = new Label();
    private final Label rotuloMensagem = new Label();
    private final GridPane grade = new GridPane();
    private final ScrollPane rolagem = new ScrollPane(grade);
    private final ProgressIndicator indicadorCarregamento = new ProgressIndicator();

    /**
     * Thread unica para as consultas de navegacao entre meses.
     *
     * Daemon de proposito: se a janela fechar com uma consulta em andamento, a
     * JVM nao pode ficar presa esperando por ela. Uma so thread porque as
     * consultas sao naturalmente sequenciais — o usuario navega para um mes de
     * cada vez — e assim nao ha varias consultas concorrendo pelo banco.
     */
    private final ExecutorService executorNavegacao = Executors.newSingleThreadExecutor(corpo -> {
        Thread thread = new Thread(corpo, "calendario-navegacao");
        thread.setDaemon(true);
        return thread;
    });

    /** Consulta de mes ainda em andamento, para ser cancelada quando outra comeca. */
    private Task<Map<LocalDate, List<EscalaTurno>>> carregamentoEmAndamento;

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
                funcionarioService, regraEscalaService, new GeradorRodizioServiceImpl(),
                new LimpezaEscalaServiceImpl(), aoSelecionarDia);
    }

    public MontagemEscalaController(EscalaTurnoRepository escalaTurnoRepository,
                                    EscalaFuncionarioRepository escalaFuncionarioRepository,
                                    TipoTurnoRepository tipoTurnoRepository,
                                    FuncionarioService funcionarioService,
                                    RegraEscalaService regraEscalaService,
                                    GeradorRodizioService geradorRodizioService,
                                    LimpezaEscalaService limpezaEscalaService,
                                    Consumer<LocalDate> aoSelecionarDia) {
        this.escalaTurnoRepository = escalaTurnoRepository;
        this.geradorRodizioService = geradorRodizioService;
        this.limpezaEscalaService = limpezaEscalaService;
        this.regraEscalaService = regraEscalaService;
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

        cartaoCalendario.getChildren().addAll(criarBarraNavegacao(), rotuloMensagem, rolagem, criarLegenda());

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

        indicadorCarregamento.getStyleClass().add("calendario-carregando");
        exibirIndicadorDeCarregamento(false);

        Region espacador = new Region();
        HBox.setHgrow(espacador, Priority.ALWAYS);

        Button gerarRodizio = new Button("Gerar rodízio");
        gerarRodizio.getStyleClass().add("button-primario");
        gerarRodizio.setOnAction(evento -> gerarRodizioDoMesExibido());

        Button limparMes = new Button("Limpar mês");
        limparMes.getStyleClass().add("button-secundario");
        limparMes.setOnAction(evento -> limparMesExibido());

        // As setas continuam habilitadas durante o carregamento: desabilita-las
        // esconderia a condicao de corrida em vez de resolve-la, e quem resolve
        // e a conferencia de mes no setOnSucceeded.
        HBox barra = new HBox(12, anterior, rotuloMesAno, indicadorCarregamento, proximo,
                espacador, limparMes, gerarRodizio);
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

    // -----------------------------------------------------------------
    // Limpar mes (issue #44)
    // -----------------------------------------------------------------

    /**
     * Apaga a escala do mes exibido, com dupla confirmacao.
     *
     * A primeira confirmacao mostra os numeros reais do mes (consultados no
     * banco na hora, nao um texto generico) e a segunda deixa claro que a
     * acao nao tem volta. Basta cancelar uma das duas para nada acontecer.
     *
     * Mes vazio nem chega a perguntar: so avisa.
     */
    private void limparMesExibido() {
        try {
            ResumoEscalaMes resumo = limpezaEscalaService.resumir(mesExibido);
            if (resumo.vazio()) {
                DialogUtil.mostrarInformacao("Nada a limpar",
                        descreverMes(mesExibido) + " já está sem turnos montados.");
                return;
            }

            if (!confirmarLimpeza(resumo) || !confirmarLimpezaDefinitiva()) {
                return;
            }

            ResultadoLimpeza resultado = limpezaEscalaService.limparMes(mesExibido);

            limparSelecao();
            painelAtribuicao.mostrarDia(null);
            renderizarMes();

            DialogUtil.mostrarInformacao("Mês limpo", resultado.mensagem());

        } catch (RepositoryException excecao) {
            DialogUtil.mostrarErroBancoIndisponivel("Não foi possível limpar o mês");
        }
    }

    /** Primeira confirmacao: o que exatamente vai embora. */
    private boolean confirmarLimpeza(ResumoEscalaMes resumo) {
        return DialogUtil.mostrarConfirmacao("Limpar a escala de " + descreverMes(mesExibido) + "?",
                String.format("Serão removidos %d turno(s) e %d alocação(ões) de agentes.",
                        resumo.turnos(), resumo.alocacoes()));
    }

    /** Segunda confirmacao: o aviso de que nao da para desfazer. */
    private boolean confirmarLimpezaDefinitiva() {
        return DialogUtil.mostrarConfirmacao("Confirmar a limpeza definitiva?",
                "Esta ação não pode ser desfeita: a escala do mês será apagada por completo, "
                + "junto com os lançamentos de banco de horas vinculados a ela.\n\n"
                + "Deseja mesmo continuar?");
    }

    /**
     * Recarrega os turnos do mes exibido e redesenha a grade, preservando o
     * dia selecionado. E o que o painel de atribuicao chama depois de criar
     * turno, alocar ou remover um agente, para a celula mudar na hora.
     */
    public void recarregar() {
        renderizarMes();
    }

    /**
     * Troca o mes exibido e dispara a consulta do novo intervalo em segundo
     * plano. Diferente do {@link #recarregar()}, que segue sincrono: trocar de
     * mes e a unica navegacao que pode encarar um banco lento, e e nela que
     * travar a interface apareceria.
     */
    private void trocarMes(int meses) {
        mesExibido = mesExibido.plusMonths(meses);
        limparSelecao();
        painelAtribuicao.mostrarDia(null);
        carregarMesEmSegundoPlano(mesExibido);
    }

    /**
     * Consulta o mes numa {@link Task} e desenha a grade quando o resultado
     * chega.
     *
     * <p><b>Divisao de trabalho entre as threads:</b> o {@code call()} apenas
     * busca dados — nao pode tocar em Node nenhum, porque roda fora da thread
     * da interface e o JavaFX so aceita alteracao de tela vinda dela. Toda a
     * construcao da grade fica no {@code setOnSucceeded}, que o proprio JavaFX
     * executa de volta na thread da interface.</p>
     *
     * <p><b>Corrida entre navegacoes:</b> clicar depressa nas setas dispara
     * varias consultas, e nada garante que elas terminem na ordem em que
     * comecaram — uma consulta antiga terminando depois sobrescreveria a grade
     * com o mes errado. Quem resolve isso e a conferencia
     * {@code mes.equals(mesExibido)} nos dois tratadores: resultado de um mes
     * que nao esta mais na tela e simplesmente descartado. O cancelamento da
     * consulta anterior e so uma economia — nao serve como garantia, porque
     * {@code Task.cancel} nao interrompe uma consulta JDBC ja em andamento e
     * nao alcanca uma que ja terminou.</p>
     */
    private void carregarMesEmSegundoPlano(YearMonth mes) {
        rotuloMesAno.setText(descreverMes(mes));
        esconderMensagem();
        // A grade fica vazia enquanto carrega em vez de manter o mes anterior:
        // titulo de um mes com os dias de outro pareceria dado errado.
        limparGrade();
        exibirIndicadorDeCarregamento(true);

        if (carregamentoEmAndamento != null) {
            // Se a anterior ainda nem comecou, o executor a descarta.
            carregamentoEmAndamento.cancel(false);
        }

        Task<Map<LocalDate, List<EscalaTurno>>> tarefa = new Task<>() {
            @Override
            protected Map<LocalDate, List<EscalaTurno>> call() {
                // Fora da thread da interface: aqui so pode haver consulta.
                return buscarTurnosPorDia(mes);
            }
        };

        tarefa.setOnSucceeded(evento -> {
            if (!ehOMesExibido(mes)) {
                return; // o usuario ja navegou para outro mes
            }
            exibirIndicadorDeCarregamento(false);
            aplicarTurnos(tarefa.getValue());
        });

        tarefa.setOnFailed(evento -> {
            if (!ehOMesExibido(mes)) {
                return;
            }
            exibirIndicadorDeCarregamento(false);
            aplicarFalhaDeCarregamento(mes, tarefa.getException());
        });

        // Cancelada = outra navegacao ja assumiu a tela, inclusive o indicador.
        tarefa.setOnCancelled(evento -> { });

        carregamentoEmAndamento = tarefa;
        executorNavegacao.execute(tarefa);
    }

    private boolean ehOMesExibido(YearMonth mes) {
        return mes.equals(mesExibido);
    }

    private void exibirIndicadorDeCarregamento(boolean visivel) {
        indicadorCarregamento.setVisible(visivel);
        indicadorCarregamento.setManaged(visivel);
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

    /**
     * Caminho sincrono: consulta e desenha na hora, na thread da interface.
     *
     * <p>E o que o {@link #recarregar()} e a montagem inicial da tela usam. A
     * consulta e de um mes so e o banco e local, entao segurar a interface por
     * ela e o preco de a celula mudar no mesmo instante em que o agente e
     * alocado. Quem pode encarar espera e a troca de mes, e essa vai por
     * {@link #carregarMesEmSegundoPlano}.</p>
     */
    private void renderizarMes() {
        rotuloMesAno.setText(descreverMes(mesExibido));
        try {
            aplicarTurnos(buscarTurnosPorDia(mesExibido));
        } catch (RepositoryException excecao) {
            aplicarFalhaDeCarregamento(mesExibido, excecao);
        }
    }

    /** Estado de tela do carregamento bem-sucedido. Sempre na thread da interface. */
    private void aplicarTurnos(Map<LocalDate, List<EscalaTurno>> turnosPorDia) {
        mesExibidoTemEscala = !turnosPorDia.isEmpty();
        esconderMensagem();
        desenharGrade(turnosPorDia);
    }

    /**
     * Estado de tela do carregamento que falhou. A grade continua sendo
     * desenhada vazia: navegar entre meses precisa funcionar mesmo com o banco
     * fora do ar.
     *
     * <p>A falha vai para o log com o stack trace. Sem isso, uma consulta que
     * estoura dentro da Task nao deixaria rastro nenhum — o
     * {@code setOnFailed} engole a excecao por natureza.</p>
     */
    private void aplicarFalhaDeCarregamento(YearMonth mes, Throwable erro) {
        LogAplicacao.registrarErro("Falha ao carregar os turnos de " + mes, erro);
        mesExibidoTemEscala = false;
        exibirMensagem("Não foi possível carregar os turnos deste mês.");
        desenharGrade(Map.of());
    }

    private void limparGrade() {
        grade.getChildren().clear();
        grade.getRowConstraints().clear();
    }

    /** Monta a grade do mes exibido a partir de dados ja carregados. So UI, nada de banco. */
    private void desenharGrade(Map<LocalDate, List<EscalaTurno>> turnosPorDia) {
        limparGrade();

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

        // O efetivo sai dos agentes que buscarPorPeriodo ja trouxe hidratados:
        // a sobrecarga de verificarEfetivo que recebe a lista evita uma
        // consulta por turno (31 num mes cheio, a cada redesenho da grade).
        boolean algumTurnoIncompleto = false;
        boolean algumaCoberturaRegistrada = false;
        for (EscalaTurno turno : turnosDoDia) {
            List<EscalaFuncionario> agentes = agentesDoTurno(turno);
            ResultadoEfetivo efetivo = regraEscalaService.verificarEfetivo(turno, agentes);
            boolean temCobertura = temCoberturaRegistrada(agentes);

            algumTurnoIncompleto |= !efetivo.completo();
            algumaCoberturaRegistrada |= temCobertura;

            celula.getChildren().add(criarBlocoTurno(turno, efetivo, temCobertura));
        }

        String estado = estadoDoDia(turnosDoDia, algumTurnoIncompleto, algumaCoberturaRegistrada);
        if (estado != null) {
            celula.getStyleClass().add(estado);
        }

        celula.setOnMouseClicked(evento -> selecionar(dia, celula));

        if (dia.equals(diaSelecionado)) {
            aplicarDestaque(celula);
        }
        return celula;
    }

    /**
     * Classe de estado da celula, ou null para um dia sem turno nenhum (que
     * fica com a celula neutra, como antes da issue #45).
     *
     * <p>Um mesmo dia pode ter turnos em estados diferentes — o 12x36 tem
     * diurno e noturno na mesma data —, entao a precedencia importa:</p>
     *
     * <ol>
     *   <li><b>incompleto</b>, se qualquer turno do dia estiver abaixo do
     *       minimo. Vem primeiro porque e o unico estado que cobra acao do
     *       gestor: esconder isso atras de um dia "completo" seria mostrar o
     *       problema justamente para quem precisa resolve-lo;</li>
     *   <li><b>cobertura</b>, se algum turno tiver alocacao de cobertura. E
     *       informativo, nao pendencia;</li>
     *   <li><b>completo</b>, o caso em que nao ha nada a fazer.</li>
     * </ol>
     *
     * <p>So uma classe entra por celula. O detalhe turno a turno fica no selo
     * de cada bloco e no painel lateral, que mostram o dia inteiro.</p>
     */
    private String estadoDoDia(List<EscalaTurno> turnosDoDia,
                               boolean algumTurnoIncompleto, boolean algumaCoberturaRegistrada) {
        if (turnosDoDia.isEmpty()) {
            return null;
        }
        if (algumTurnoIncompleto) {
            return CLASSE_DIA_INCOMPLETO;
        }
        if (algumaCoberturaRegistrada) {
            return CLASSE_DIA_COBERTURA;
        }
        return CLASSE_DIA_COMPLETO;
    }

    /**
     * Agentes que {@link EscalaTurnoRepository#buscarPorPeriodo} ja trouxe
     * junto com o turno, no mesmo JOIN. Nunca null, para as regras e a
     * contagem nao precisarem tratar o caso.
     */
    private List<EscalaFuncionario> agentesDoTurno(EscalaTurno turno) {
        return turno.getAgentes() != null ? turno.getAgentes() : List.of();
    }

    /**
     * Um turno tem cobertura quando alguma de suas alocacoes aponta para a
     * alocacao que ela esta cobrindo ({@code coberturaDe} preenchido).
     *
     * <p><b>Este estado nao pode ser conferido na tela hoje.</b> Coberturas
     * sao do milestone M5 e ainda nao existem: nenhum ponto do sistema grava
     * {@code cobertura_de}, entao a classe de fundo e o selo existem mas nunca
     * aparecem. E a mesma situacao da celula vazia na #41 — o codigo esta
     * pronto para quando o M5 chegar, e nada foi inventado no banco so para
     * poder ver a cor.</p>
     */
    private boolean temCoberturaRegistrada(List<EscalaFuncionario> agentes) {
        return agentes.stream().anyMatch(alocacao -> alocacao.getCoberturaDe() != null);
    }

    /**
     * Um bloco por turno: nome do tipo de turno, contador de efetivo, agentes
     * alocados e, quando houver, a marca de cobertura.
     *
     * <p>O contador "alocados/minimo" repete em texto o que a cor do fundo da
     * celula diz. E o que mantem os estados legiveis para quem tem
     * dificuldade de distinguir cores, e usa o mesmo formato do selo do painel
     * lateral (#42), para as duas telas nao discordarem.</p>
     */
    private VBox criarBlocoTurno(EscalaTurno turno, ResultadoEfetivo efetivo, boolean temCobertura) {
        Label nomeTipoTurno = new Label(descreverTipoTurno(turno));
        nomeTipoTurno.getStyleClass().add("calendario-tipo-turno");
        nomeTipoTurno.setWrapText(true);
        HBox.setHgrow(nomeTipoTurno, Priority.ALWAYS);

        Label contador = new Label(efetivo.alocados() + "/" + efetivo.minimoExigido());
        contador.getStyleClass().addAll("calendario-selo-efetivo", efetivo.completo()
                ? "calendario-selo-efetivo-completo"
                : "calendario-selo-efetivo-incompleto");
        contador.setMinWidth(Region.USE_PREF_SIZE);
        contador.setTooltip(new Tooltip(efetivo.mensagem()));

        HBox cabecalho = new HBox(4, nomeTipoTurno, contador);
        cabecalho.setAlignment(Pos.CENTER_LEFT);

        Label agentes = new Label(descreverAgentes(turno));
        agentes.getStyleClass().add("calendario-agentes");
        agentes.setWrapText(true);

        VBox bloco = new VBox(1, cabecalho, agentes);
        if (temCobertura) {
            Label selo = new Label("cobertura");
            selo.getStyleClass().add("calendario-selo-cobertura");
            selo.setMinWidth(Region.USE_PREF_SIZE);
            bloco.getChildren().add(selo);
        }
        bloco.getStyleClass().add("calendario-bloco-turno");
        bloco.setMaxWidth(Double.MAX_VALUE);
        return bloco;
    }

    // -----------------------------------------------------------------
    // Legenda (issue #45)
    // -----------------------------------------------------------------

    /**
     * Legenda dos estados no rodape do cartao.
     *
     * <p>Cada amostra e uma celula de verdade: recebe
     * {@code calendario-celula-dia} mais a classe do estado, igual ao que a
     * grade monta. Dai a legenda nao ter como divergir da cor real da celula
     * quando o CSS mudar — o unico estilo proprio dela e o tamanho.</p>
     *
     * <p>FlowPane em vez de HBox para a legenda quebrar em duas linhas em
     * janela estreita, em vez de cortar o ultimo item.</p>
     */
    private FlowPane criarLegenda() {
        FlowPane legenda = new FlowPane(16, 6,
                criarItemDaLegenda("Plantão completo", CLASSE_DIA_COMPLETO),
                criarItemDaLegenda("Efetivo incompleto", CLASSE_DIA_INCOMPLETO),
                criarItemDaLegenda("Cobertura registrada", CLASSE_DIA_COBERTURA),
                criarItemDaLegenda("Dia selecionado", CLASSE_DIA_SELECIONADO));
        legenda.getStyleClass().add("calendario-legenda");
        return legenda;
    }

    private HBox criarItemDaLegenda(String texto, String classeDeEstado) {
        Region amostra = new Region();
        amostra.getStyleClass().addAll("calendario-celula-dia", classeDeEstado, "calendario-legenda-amostra");

        Label rotulo = new Label(texto);
        rotulo.getStyleClass().add("calendario-legenda-texto");

        HBox item = new HBox(6, amostra, rotulo);
        item.setAlignment(Pos.CENTER_LEFT);
        return item;
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
     *
     * <p><b>Nao toca em nenhum componente de tela</b>, de proposito: e este o
     * metodo que roda dentro do {@code call()} da Task, fora da thread da
     * interface. Mensagem de erro e estado da tela ficam por conta de quem
     * chama, ja de volta na thread certa.</p>
     *
     * @throws RepositoryException se o banco nao responder; quem chama decide
     *         entre tratar na hora (caminho sincrono) ou pelo setOnFailed
     */
    private Map<LocalDate, List<EscalaTurno>> buscarTurnosPorDia(YearMonth mes) {
        List<EscalaTurno> turnos = escalaTurnoRepository.buscarPorPeriodo(
                mes.atDay(1).atStartOfDay(),
                mes.plusMonths(1).atDay(1).atStartOfDay());

        Map<LocalDate, List<EscalaTurno>> porDia = new LinkedHashMap<>();
        for (EscalaTurno turno : turnos) {
            if (turno.getInicio() == null) {
                continue;
            }
            porDia.computeIfAbsent(turno.getInicio().toLocalDate(), data -> new ArrayList<>())
                    .add(turno);
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
