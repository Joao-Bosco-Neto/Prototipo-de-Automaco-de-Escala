package br.edu.sistemaescala.frontend.controller;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import br.edu.sistemaescala.backend.model.EscalaFuncionario;
import br.edu.sistemaescala.backend.model.EscalaTurno;
import br.edu.sistemaescala.backend.model.MotivoCobertura;
import br.edu.sistemaescala.backend.repository.RepositoryException;
import br.edu.sistemaescala.backend.service.CoberturaService;
import br.edu.sistemaescala.backend.service.RegraCoberturaException;
import br.edu.sistemaescala.backend.service.SubstitutoDisponivel;
import br.edu.sistemaescala.frontend.DialogUtil;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

/**
 * Tela de registro de cobertura de plantão (issue #33, "conforme a tela 4").
 *
 * <p>UI montada em Java puro, como o resto do frontend. O controller só trata
 * evento visual: escolher a data recarrega o combo de ausentes, escolher o
 * ausente recarrega o de substitutos (cada um com o selo de disponibilidade), e
 * "Registrar cobertura" delega para o {@link CoberturaService}, onde ficam a
 * validação, a inserção em {@code escala_funcionario} e os lançamentos de banco
 * de horas.</p>
 */
public class CoberturaController {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final CoberturaService coberturaService;

    private final DatePicker campoData = new DatePicker();
    private final ComboBox<EscalaFuncionario> comboAusente = new ComboBox<>();
    private final Label ajudaAusente = new Label("Escolha a data para listar quem está escalado.");
    private final ComboBox<SubstitutoDisponivel> comboSubstituto = new ComboBox<>();
    private final ComboBox<MotivoCobertura> comboMotivo = new ComboBox<>();
    private final TextArea campoObservacoes = new TextArea();
    private final CheckBox checkBancoHoras = new CheckBox("Lançar no banco de horas");

    private final Label labelMensagem = new Label();
    private final Button botaoRegistrar = new Button("Registrar cobertura");
    private final Button botaoLimpar = new Button("Limpar");

    public CoberturaController(CoberturaService coberturaService) {
        this.coberturaService = coberturaService;
    }

    public Parent criarTela() {
        VBox raiz = new VBox(18);
        raiz.setPadding(new Insets(24));
        raiz.getStyleClass().add("area-conteudo");

        raiz.getChildren().addAll(criarCabecalho(), criarCardFormulario());

        configurarCampos();
        configurarEventos();
        carregarMotivos();
        campoData.setValue(LocalDate.now());
        recarregarAusentes();

        return raiz;
    }

    private VBox criarCabecalho() {
        Label titulo = new Label("Cobertura de plantão");
        titulo.getStyleClass().add("titulo-1");

        Label subtitulo = new Label(
                "Registre quando um funcionário assume o turno de outro. "
                + "A cobertura pode gerar crédito no banco de horas.");
        subtitulo.getStyleClass().add("texto-secundario");
        subtitulo.setWrapText(true);

        return new VBox(4, titulo, subtitulo);
    }

    private VBox criarCardFormulario() {
        VBox card = new VBox(12);
        card.getStyleClass().add("card");
        card.setMaxWidth(560);

        Label tituloForm = new Label("Nova cobertura");
        tituloForm.getStyleClass().add("titulo-2");

        ajudaAusente.getStyleClass().add("texto-secundario");

        Label ajudaSubstituto = new Label(
                "Qualquer funcionário pode cobrir. Quem está em descanso mínimo "
                + "ou com turno sobreposto aparece marcado.");
        ajudaSubstituto.getStyleClass().add("texto-secundario");
        ajudaSubstituto.setWrapText(true);

        Label ajudaBancoHoras = new Label(
                "Crédito para quem cobre e débito para o coberto, conforme a duração do turno.");
        ajudaBancoHoras.getStyleClass().add("texto-secundario");
        ajudaBancoHoras.setWrapText(true);

        labelMensagem.getStyleClass().add("selo");
        labelMensagem.setWrapText(true);
        labelMensagem.setVisible(false);
        labelMensagem.setManaged(false);

        botaoRegistrar.getStyleClass().add("button-primario");
        botaoLimpar.getStyleClass().add("button-secundario");
        HBox barraBotoes = new HBox(10, botaoRegistrar, botaoLimpar);
        barraBotoes.setAlignment(Pos.CENTER_RIGHT);

        card.getChildren().addAll(
                tituloForm,
                new Label("Data do plantão *"), campoData,
                new Label("Funcionário ausente *"), comboAusente, ajudaAusente,
                new Label("Funcionário que irá cobrir *"), comboSubstituto, ajudaSubstituto,
                new Label("Motivo"), comboMotivo,
                new Label("Observações"), campoObservacoes,
                checkBancoHoras, ajudaBancoHoras,
                labelMensagem,
                barraBotoes);

        return card;
    }

    private void configurarCampos() {
        campoData.setMaxWidth(Double.MAX_VALUE);

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
                    selo.setTooltip(new javafx.scene.control.Tooltip(item.restricao()));
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
        campoData.valueProperty().addListener((obs, antigo, novo) -> recarregarAusentes());
        comboAusente.valueProperty().addListener((obs, antigo, novo) -> recarregarSubstitutos());
        botaoRegistrar.setOnAction(evento -> registrar());
        botaoLimpar.setOnAction(evento -> limparFormulario());
    }

    // -----------------------------------------------------------------
    // Carregamento dinâmico dos combos
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
                    : "Escalados no dia " + data + ".");
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
    // Registro
    // -----------------------------------------------------------------

    private void registrar() {
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
            boolean confirmar = DialogUtil.mostrarConfirmacao("Substituto com restrição",
                    substituto.funcionario().getNome() + ": " + substituto.restricao()
                    + "\n\nRegistrar a cobertura mesmo assim?");
            if (!confirmar) {
                return;
            }
        }

        try {
            coberturaService.registrar(ausente, substituto.funcionario(), motivoSelecionadoId(),
                    campoObservacoes.getText(), checkBancoHoras.isSelected());
            exibirSucesso("Cobertura registrada para " + substituto.funcionario().getNome() + ".");
            limparFormulario();
            recarregarAusentes();
        } catch (RegraCoberturaException excecao) {
            exibirErro(excecao.getMessage());
        } catch (RepositoryException excecao) {
            DialogUtil.mostrarErroBancoIndisponivel("Não foi possível registrar a cobertura");
        }
    }

    private Integer motivoSelecionadoId() {
        MotivoCobertura motivo = comboMotivo.getValue();
        return motivo != null ? motivo.getId() : null;
    }

    private void limparFormulario() {
        comboAusente.getSelectionModel().clearSelection();
        comboSubstituto.getSelectionModel().clearSelection();
        comboSubstituto.getItems().clear();
        comboMotivo.getSelectionModel().clearSelection();
        campoObservacoes.clear();
        checkBancoHoras.setSelected(true);
    }

    // -----------------------------------------------------------------
    // Apoio
    // -----------------------------------------------------------------

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
