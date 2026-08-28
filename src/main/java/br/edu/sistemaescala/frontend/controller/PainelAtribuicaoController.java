package br.edu.sistemaescala.frontend.controller;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import br.edu.sistemaescala.backend.model.EscalaFuncionario;
import br.edu.sistemaescala.backend.model.EscalaTurno;
import br.edu.sistemaescala.backend.model.Funcionario;
import br.edu.sistemaescala.backend.model.TipoTurno;
import br.edu.sistemaescala.backend.repository.EscalaFuncionarioRepository;
import br.edu.sistemaescala.backend.repository.EscalaTurnoRepository;
import br.edu.sistemaescala.backend.repository.RepositoryException;
import br.edu.sistemaescala.backend.repository.TipoTurnoRepository;
import br.edu.sistemaescala.backend.service.FuncionarioService;
import br.edu.sistemaescala.backend.service.RegraEscalaService;
import br.edu.sistemaescala.backend.service.ResultadoAlocacao;
import br.edu.sistemaescala.backend.service.ResultadoDescanso;
import br.edu.sistemaescala.backend.service.ResultadoEfetivo;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Painel lateral de atribuicao de agentes ao dia selecionado no calendario
 * (issue #42).
 *
 * O ponto central da issue e o feedback imediato: cada funcionario da lista de
 * disponiveis ja chega com o selo de indisponibilidade calculado por
 * {@link RegraEscalaService#podeAlocar} (issue #23) e
 * {@link RegraEscalaService#verificarDescanso} (issue #40), com o botao de
 * adicionar desabilitado — o usuario nao clica para so entao descobrir que
 * nao pode.
 *
 * A issue original falava em "data e equipe" no cabecalho; equipes de rodizio
 * foram substituidas por tipo_turno e nao existem mais no modelo, por isso o
 * cabecalho mostra a data e os tipos de turno do dia.
 *
 * O painel tambem cria o escala_turno do dia, escolhendo o tipo: sem isso ele
 * seria inexercitavel, porque nada no sistema criava turno ainda. Gerar o mes
 * inteiro em rodizio continua sendo escopo da issue #43.
 */
public class PainelAtribuicaoController {

    private static final Locale LOCALE_BR = Locale.forLanguageTag("pt-BR");
    private static final DateTimeFormatter FORMATO_DATA_EXTENSO =
            DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM 'de' yyyy", LOCALE_BR);
    private static final DateTimeFormatter FORMATO_HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter FORMATO_DIA_HORA =
            DateTimeFormatter.ofPattern("dd/MM HH:mm");

    private static final int SEGUNDOS_POR_HORA = 3600;

    private final EscalaTurnoRepository escalaTurnoRepository;
    private final EscalaFuncionarioRepository escalaFuncionarioRepository;
    private final TipoTurnoRepository tipoTurnoRepository;
    private final FuncionarioService funcionarioService;
    private final RegraEscalaService regraEscalaService;
    /** Avisa o calendario que a escala mudou, para a grade refletir na hora. */
    private final Runnable aoAlterarEscala;

    private final VBox raiz = new VBox(14);
    private final VBox corpo = new VBox(14);
    private final Label rotuloMensagem = new Label();

    private LocalDate diaSelecionado;

    public PainelAtribuicaoController(EscalaTurnoRepository escalaTurnoRepository,
                                      EscalaFuncionarioRepository escalaFuncionarioRepository,
                                      TipoTurnoRepository tipoTurnoRepository,
                                      FuncionarioService funcionarioService,
                                      RegraEscalaService regraEscalaService,
                                      Runnable aoAlterarEscala) {
        this.escalaTurnoRepository = escalaTurnoRepository;
        this.escalaFuncionarioRepository = escalaFuncionarioRepository;
        this.tipoTurnoRepository = tipoTurnoRepository;
        this.funcionarioService = funcionarioService;
        this.regraEscalaService = regraEscalaService;
        this.aoAlterarEscala = aoAlterarEscala != null ? aoAlterarEscala : () -> { };
    }

    public Region criarPainel() {
        raiz.getStyleClass().addAll("card", "painel-atribuicao");
        // Largura travada: sem o maxWidth o painel estica conforme o texto do
        // selo mais longo e o calendario ao lado muda de tamanho a cada clique.
        raiz.setPrefWidth(380);
        raiz.setMinWidth(380);
        raiz.setMaxWidth(380);

        rotuloMensagem.setWrapText(true);
        esconderMensagem();

        ScrollPane rolagem = new ScrollPane(corpo);
        rolagem.setFitToWidth(true);
        rolagem.getStyleClass().add("painel-atribuicao-rolagem");
        VBox.setVgrow(rolagem, Priority.ALWAYS);

        raiz.getChildren().addAll(rotuloMensagem, rolagem);

        renderizar();
        return raiz;
    }

    /** Chamado pelo calendario quando um dia e selecionado. */
    public void mostrarDia(LocalDate dia) {
        diaSelecionado = dia;
        esconderMensagem();
        renderizar();
    }

    // -----------------------------------------------------------------
    // Montagem do painel
    // -----------------------------------------------------------------

    private void renderizar() {
        corpo.getChildren().clear();

        if (diaSelecionado == null) {
            corpo.getChildren().add(criarOrientacaoSemDia());
            return;
        }

        List<EscalaTurno> turnosDoDia;
        try {
            turnosDoDia = carregarTurnosDoDia(diaSelecionado);
        } catch (RepositoryException excecao) {
            exibirErro("Não foi possível carregar os turnos deste dia.");
            return;
        }

        corpo.getChildren().add(criarCabecalhoDoDia(turnosDoDia));

        if (turnosDoDia.isEmpty()) {
            Label vazio = new Label("Nenhum turno neste dia.");
            vazio.getStyleClass().addAll("selo", "selo-neutro");
            Label explicacao = new Label(
                    "Escolha um tipo de turno abaixo para criar o plantão deste dia "
                    + "e então alocar os agentes.");
            explicacao.getStyleClass().add("texto-secundario");
            explicacao.setWrapText(true);
            corpo.getChildren().addAll(vazio, explicacao);
        } else {
            for (EscalaTurno turno : turnosDoDia) {
                corpo.getChildren().add(criarBlocoDoTurno(turno));
            }
        }

        corpo.getChildren().addAll(new Separator(), criarCriacaoDeTurno(turnosDoDia.isEmpty()));
    }

    private VBox criarOrientacaoSemDia() {
        Label titulo = new Label("Nenhum dia selecionado");
        titulo.getStyleClass().add("titulo-2");

        Label orientacao = new Label(
                "Clique em um dia do calendário para ver os turnos daquela data "
                + "e atribuir os agentes.");
        orientacao.getStyleClass().add("texto-secundario");
        orientacao.setWrapText(true);

        return new VBox(6, titulo, orientacao);
    }

    /** Cabecalho: data por extenso e os tipos de turno daquele dia. */
    private VBox criarCabecalhoDoDia(List<EscalaTurno> turnosDoDia) {
        Label data = new Label(descreverData(diaSelecionado));
        data.getStyleClass().add("titulo-2");
        data.setWrapText(true);

        String tipos = turnosDoDia.isEmpty()
                ? "Sem turno definido"
                : turnosDoDia.stream()
                        .map(this::nomeDoTipoTurno)
                        .collect(Collectors.joining(" · "));
        Label rotuloTipos = new Label(tipos);
        rotuloTipos.getStyleClass().add("texto-secundario");
        rotuloTipos.setWrapText(true);

        return new VBox(2, data, rotuloTipos);
    }

    /** Um bloco por turno do dia: efetivo, escalados e disponiveis. */
    private VBox criarBlocoDoTurno(EscalaTurno turno) {
        VBox bloco = new VBox(10);
        bloco.getStyleClass().add("painel-turno");

        bloco.getChildren().add(criarTituloDoTurno(turno));
        bloco.getChildren().add(criarSecaoEscalados(turno));
        bloco.getChildren().add(criarSecaoDisponiveis(turno));
        return bloco;
    }

    private HBox criarTituloDoTurno(EscalaTurno turno) {
        Label nome = new Label(nomeDoTipoTurno(turno) + "  " + descreverHorario(turno));
        nome.getStyleClass().add("painel-turno-titulo");
        nome.setWrapText(true);

        Region espacador = new Region();
        HBox.setHgrow(espacador, Priority.ALWAYS);

        HBox linha = new HBox(8, nome, espacador, criarSeloEfetivo(turno));
        linha.setAlignment(Pos.CENTER_LEFT);
        return linha;
    }

    /**
     * Contador de efetivo no formato "3/2", vindo de verificarEfetivo. Abaixo
     * do minimo o selo vira de atencao; a mensagem completa da regra fica no
     * tooltip do proprio selo.
     */
    private Label criarSeloEfetivo(EscalaTurno turno) {
        ResultadoEfetivo efetivo = regraEscalaService.verificarEfetivo(turno);
        Label selo = new Label(efetivo.alocados() + "/" + efetivo.minimoExigido() + " agentes");
        selo.getStyleClass().addAll("selo", efetivo.completo() ? "selo-sucesso" : "selo-atencao");
        selo.setMinWidth(Region.USE_PREF_SIZE);
        selo.setTooltip(new Tooltip(efetivo.mensagem()));
        return selo;
    }

    private VBox criarSecaoEscalados(EscalaTurno turno) {
        VBox secao = new VBox(6);
        secao.getChildren().add(criarRotuloSecao("Escalados"));

        List<EscalaFuncionario> alocacoes = turno.getAgentes();
        if (alocacoes == null || alocacoes.isEmpty()) {
            Label vazio = new Label("Nenhum agente alocado neste turno.");
            vazio.getStyleClass().add("texto-secundario");
            vazio.setWrapText(true);
            secao.getChildren().add(vazio);
            return secao;
        }

        for (EscalaFuncionario alocacao : alocacoes) {
            secao.getChildren().add(criarLinhaEscalado(alocacao));
        }
        return secao;
    }

    private HBox criarLinhaEscalado(EscalaFuncionario alocacao) {
        Funcionario funcionario = alocacao.getFuncionario();
        Label nome = new Label(funcionario != null ? funcionario.getNome() : "(sem nome)");
        nome.setWrapText(true);
        HBox.setHgrow(nome, Priority.ALWAYS);
        nome.setMaxWidth(Double.MAX_VALUE);

        Button remover = new Button("Remover");
        remover.getStyleClass().add("button-secundario-compacto");
        remover.setOnAction(evento -> removerAlocacao(alocacao));

        HBox linha = new HBox(8, nome, remover);
        linha.setAlignment(Pos.CENTER_LEFT);
        linha.getStyleClass().add("painel-linha-agente");
        return linha;
    }

    /**
     * Funcionarios ativos que ainda nao estao no turno, cada um ja com o
     * veredito das regras. As consultas de podeAlocar/verificarDescanso rodam
     * uma vez por funcionario aqui, na montagem da lista, justamente para o
     * usuario ver o impedimento antes de clicar.
     */
    private VBox criarSecaoDisponiveis(EscalaTurno turno) {
        VBox secao = new VBox(6);
        secao.getChildren().add(criarRotuloSecao("Disponíveis para alocar"));

        Set<Integer> jaNoTurno = idsDosAlocados(turno);
        List<Funcionario> candidatos = funcionarioService.listar(true, null).stream()
                .filter(funcionario -> funcionario.getId() != null)
                .filter(funcionario -> !jaNoTurno.contains(funcionario.getId()))
                .toList();

        if (candidatos.isEmpty()) {
            Label vazio = new Label("Todos os funcionários ativos já estão neste turno.");
            vazio.getStyleClass().add("texto-secundario");
            vazio.setWrapText(true);
            secao.getChildren().add(vazio);
            return secao;
        }

        for (Funcionario funcionario : candidatos) {
            secao.getChildren().add(criarLinhaDisponivel(turno, funcionario));
        }
        return secao;
    }

    private VBox criarLinhaDisponivel(EscalaTurno turno, Funcionario funcionario) {
        Label nome = new Label(funcionario.getNome());
        nome.setWrapText(true);
        nome.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(nome, Priority.ALWAYS);

        Button adicionar = new Button("Adicionar");
        adicionar.getStyleClass().add("button-secundario-compacto");
        adicionar.setOnAction(evento -> adicionarAgente(turno, funcionario));

        HBox linha = new HBox(8, nome, adicionar);
        linha.setAlignment(Pos.CENTER_LEFT);

        VBox item = new VBox(3, linha);
        item.getStyleClass().add("painel-linha-agente");

        // O impedimento e calculado ANTES de exibir: o botao ja nasce
        // desabilitado e o motivo aparece no selo.
        Label selo = avaliarImpedimento(turno, funcionario);
        if (selo != null) {
            adicionar.setDisable(true);
            item.getChildren().add(selo);
        }
        return item;
    }

    /**
     * Roda as duas regras na ordem em que elas bloqueiam: duplicidade e
     * sobreposicao primeiro (#23), descanso depois (#40).
     *
     * @return selo com o motivo, ou {@code null} quando o funcionario esta livre
     */
    private Label avaliarImpedimento(EscalaTurno turno, Funcionario funcionario) {
        ResultadoAlocacao alocacao = regraEscalaService.podeAlocar(funcionario.getId(), turno);
        if (!alocacao.permitido()) {
            return criarSeloImpedimento(resumirAlocacao(alocacao), alocacao.mensagem(), "selo-perigo");
        }

        ResultadoDescanso descanso = regraEscalaService.verificarDescanso(funcionario.getId(), turno);
        if (!descanso.respeitado()) {
            return criarSeloImpedimento(resumirDescanso(descanso), descanso.mensagem(), "selo-atencao");
        }
        return null;
    }

    private Label criarSeloImpedimento(String texto, String detalhe, String classeSelo) {
        Label selo = new Label(texto);
        selo.getStyleClass().addAll("selo", classeSelo);
        selo.setWrapText(true);
        // A mensagem completa da regra e longa demais para a largura do
        // painel; fica no tooltip.
        selo.setTooltip(new Tooltip(detalhe));
        return selo;
    }

    /** Texto curto do bloqueio de alocacao; a mensagem inteira vai no tooltip. */
    private String resumirAlocacao(ResultadoAlocacao resultado) {
        return resultado.mensagem().startsWith("Duplicidade")
                ? "Já escalado neste turno"
                : "Conflito de horário";
    }

    /** Ex.: "descanso 72h" — o regime exigido, que e o que o gestor precisa ver. */
    private String resumirDescanso(ResultadoDescanso resultado) {
        return "Descanso insuficiente (exige " + formatarDuracao(resultado.descansoExigido()) + ")";
    }

    private String formatarDuracao(Duration duracao) {
        long horas = duracao.toHours();
        int minutos = duracao.toMinutesPart();
        return minutos == 0 ? horas + "h" : String.format("%dh%02dmin", horas, minutos);
    }

    // -----------------------------------------------------------------
    // Criacao de turno
    // -----------------------------------------------------------------

    /**
     * Escolha do tipo de turno e criacao do escala_turno do dia. Fica visivel
     * mesmo quando o dia ja tem turno, porque um regime 12x36 precisa de dois
     * turnos na mesma data (diurno e noturno).
     */
    private VBox criarCriacaoDeTurno(boolean diaVazio) {
        VBox secao = new VBox(8);
        secao.getChildren().add(criarRotuloSecao(diaVazio ? "Criar turno neste dia" : "Adicionar outro turno"));

        ComboBox<TipoTurno> combo = new ComboBox<>();
        combo.setMaxWidth(Double.MAX_VALUE);
        combo.setPromptText("Escolha o tipo de turno");
        combo.setButtonCell(criarCelulaTipoTurno());
        combo.setCellFactory(lista -> criarCelulaTipoTurno());

        List<TipoTurno> tiposAtivos;
        try {
            tiposAtivos = tipoTurnoRepository.listar(true);
        } catch (RepositoryException excecao) {
            exibirErro("Não foi possível carregar os tipos de turno.");
            return secao;
        }

        if (tiposAtivos.isEmpty()) {
            Label aviso = new Label("Nenhum tipo de turno ativo. Cadastre um em Cadastros › Tipos de Turno.");
            aviso.getStyleClass().addAll("selo", "selo-atencao");
            aviso.setWrapText(true);
            secao.getChildren().add(aviso);
            return secao;
        }

        combo.getItems().setAll(tiposAtivos);

        Button criar = new Button("Criar turno");
        criar.getStyleClass().add("button-primario");
        criar.setDisable(true);
        criar.setOnAction(evento -> criarTurno(combo.getValue()));

        combo.valueProperty().addListener((obs, antigo, novo) -> criar.setDisable(novo == null));

        HBox barra = new HBox(8, criar);
        barra.setAlignment(Pos.CENTER_RIGHT);

        secao.getChildren().addAll(combo, barra);
        return secao;
    }

    private ListCell<TipoTurno> criarCelulaTipoTurno() {
        return new ListCell<>() {
            @Override
            protected void updateItem(TipoTurno item, boolean vazio) {
                super.updateItem(item, vazio);
                setText(vazio || item == null ? null : descreverTipoTurno(item));
            }
        };
    }

    private String descreverTipoTurno(TipoTurno tipo) {
        if (tipo.getHoraInicio() == null || tipo.getDuracaoHoras() == null) {
            return tipo.getNome() + " (sem horário definido)";
        }
        return tipo.getNome() + " — início " + tipo.getHoraInicio().format(FORMATO_HORA)
                + ", " + tipo.getDuracaoHoras().toPlainString() + "h";
    }

    private void criarTurno(TipoTurno tipo) {
        if (tipo == null || diaSelecionado == null) {
            return;
        }
        if (tipo.getHoraInicio() == null || tipo.getDuracaoHoras() == null) {
            exibirErro("O tipo de turno '" + tipo.getNome()
                    + "' não tem hora de início ou duração definidas.");
            return;
        }

        LocalDateTime inicio = diaSelecionado.atTime(tipo.getHoraInicio());
        LocalDateTime fim = inicio.plusSeconds(emSegundos(tipo.getDuracaoHoras()));

        EscalaTurno turno = new EscalaTurno();
        turno.setTipoTurno(tipo);
        turno.setInicio(inicio);
        turno.setFim(fim);
        // Minimo e maximo nascem herdados do regime, como o tipo de turno define.
        turno.setMinAgentes(tipo.getMinAgentes());
        turno.setMaxAgentes(tipo.getMaxAgentes());
        turno.setAtivo(true);

        try {
            escalaTurnoRepository.salvar(turno);
            esconderMensagem();
        } catch (RepositoryException excecao) {
            exibirErro("Não foi possível criar o turno: " + excecao.getMessage());
            return;
        }
        atualizarTelas();
    }

    /**
     * Converte a duracao do regime passando por segundos: o tipo pode ter
     * duracao fracionaria (8,5h), entao truncar para horas inteiras erraria o
     * fim do turno.
     */
    private long emSegundos(BigDecimal horas) {
        return horas.multiply(BigDecimal.valueOf(SEGUNDOS_POR_HORA))
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();
    }

    // -----------------------------------------------------------------
    // Alocar e remover
    // -----------------------------------------------------------------

    private void adicionarAgente(EscalaTurno turno, Funcionario funcionario) {
        EscalaFuncionario alocacao = new EscalaFuncionario();
        alocacao.setEscalaTurno(turno);
        alocacao.setFuncionario(funcionario);
        // inicio/fim nulos: o agente cumpre o turno inteiro. E assim que
        // RegraEscalaServiceImpl entende o periodo efetivo da alocacao.

        try {
            escalaFuncionarioRepository.inserir(alocacao);
            esconderMensagem();
        } catch (RepositoryException excecao) {
            exibirErro("Não foi possível alocar " + funcionario.getNome() + ".");
            return;
        }
        atualizarTelas();
    }

    private void removerAlocacao(EscalaFuncionario alocacao) {
        if (alocacao.getId() == null) {
            return;
        }
        try {
            escalaFuncionarioRepository.remover(alocacao.getId());
            esconderMensagem();
        } catch (RepositoryException excecao) {
            exibirErro("Não foi possível remover a alocação.");
            return;
        }
        atualizarTelas();
    }

    /** Recarrega calendario e painel: a celula do dia precisa mudar na hora. */
    private void atualizarTelas() {
        aoAlterarEscala.run();
        renderizar();
    }

    // -----------------------------------------------------------------
    // Dados e formatacao
    // -----------------------------------------------------------------

    /** Turnos com inicio no dia, com tipoTurno e agentes ja hidratados. */
    private List<EscalaTurno> carregarTurnosDoDia(LocalDate dia) {
        return escalaTurnoRepository.buscarPorPeriodo(
                dia.atStartOfDay(), dia.plusDays(1).atStartOfDay());
    }

    private Set<Integer> idsDosAlocados(EscalaTurno turno) {
        List<EscalaFuncionario> alocacoes = turno.getAgentes();
        if (alocacoes == null) {
            return Set.of();
        }
        return alocacoes.stream()
                .map(EscalaFuncionario::getFuncionario)
                .filter(funcionario -> funcionario != null && funcionario.getId() != null)
                .map(Funcionario::getId)
                .collect(Collectors.toSet());
    }

    private String descreverData(LocalDate dia) {
        String texto = dia.format(FORMATO_DATA_EXTENSO);
        return texto.substring(0, 1).toUpperCase(LOCALE_BR) + texto.substring(1);
    }

    private String nomeDoTipoTurno(EscalaTurno turno) {
        return turno.getTipoTurno() != null && turno.getTipoTurno().getNome() != null
                ? turno.getTipoTurno().getNome()
                : "Turno sem tipo";
    }

    /**
     * Horario do turno. Quando ele vira o dia (noturno das 19h as 07h), o fim
     * sai com a data para nao parecer que o turno acaba antes de comecar.
     */
    private String descreverHorario(EscalaTurno turno) {
        if (turno.getInicio() == null || turno.getFim() == null) {
            return "";
        }
        String inicio = turno.getInicio().format(FORMATO_HORA);
        boolean viraODia = !turno.getFim().toLocalDate().equals(turno.getInicio().toLocalDate());
        String fim = viraODia
                ? turno.getFim().format(FORMATO_DIA_HORA)
                : turno.getFim().format(FORMATO_HORA);
        return inicio + "–" + fim;
    }

    private Label criarRotuloSecao(String texto) {
        Label rotulo = new Label(texto);
        rotulo.getStyleClass().add("painel-secao-titulo");
        return rotulo;
    }

    private void exibirErro(String mensagem) {
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
