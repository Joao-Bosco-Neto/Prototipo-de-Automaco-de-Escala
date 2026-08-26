package br.edu.sistemaescala.frontend.controller;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import br.edu.sistemaescala.backend.model.RoleUsuario;
import br.edu.sistemaescala.backend.model.Usuario;
import br.edu.sistemaescala.backend.service.AcessoNegadoException;
import br.edu.sistemaescala.backend.service.GestaoUsuariosService;
import br.edu.sistemaescala.backend.service.RegraUsuarioException;
import br.edu.sistemaescala.backend.service.SenhaFracaException;
import br.edu.sistemaescala.backend.service.SenhasNaoConferemException;
import br.edu.sistemaescala.frontend.DialogUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Controller da tela de Gestão de Usuários (Issue #19 / RF12).
 *
 * Permite ao administrador:
 * - Listar todos os usuários do sistema
 * - Cadastrar novo usuário com perfil e senha
 * - Editar dados cadastrais (nome, login, perfil, status)
 * - Ativar / Desativar usuários (com bloqueio de autodesativação e do último admin ativo)
 * - Redefinir senha de qualquer usuário
 */
public class GestaoUsuariosController {

    private static final DateTimeFormatter FORMATO_DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final GestaoUsuariosService gestaoUsuariosService;
    private final ObservableList<Usuario> listaUsuarios = FXCollections.observableArrayList();

    private final TableView<Usuario> tabela = new TableView<>();
    private final Button botaoNovo = new Button("+ Novo Usuário");
    private final Button botaoEditar = new Button("Editar");
    private final Button botaoAlternarStatus = new Button("Desativar");
    private final Button botaoRedefinirSenha = new Button("Redefinir Senha");

    // Painel lateral do formulário
    private final VBox painelFormulario = new VBox(12);
    private final Label tituloFormulario = new Label("Novo Usuário");
    private final TextField campoNome = new TextField();
    private final TextField campoLogin = new TextField();
    private final ComboBox<RoleUsuario> comboRole = new ComboBox<>();
    private final VBox grupoSenha = new VBox(8);
    private final PasswordField campoSenha = new PasswordField();
    private final PasswordField campoConfirmacaoSenha = new PasswordField();
    private final CheckBox checkAtivo = new CheckBox("Usuário ativo");
    private final Label mensagemFeedback = new Label();
    private final Button botaoSalvar = new Button("Salvar");
    private final Button botaoCancelar = new Button("Cancelar");

    private Usuario usuarioEmEdicao = null;

    public GestaoUsuariosController(GestaoUsuariosService gestaoUsuariosService) {
        this.gestaoUsuariosService = gestaoUsuariosService;
    }

    public Parent criarTela() {
        VBox raiz = new VBox(20);
        raiz.setPadding(new Insets(24));
        raiz.getStyleClass().add("area-conteudo");

        VBox cabecalho = criarCabecalho();
        HBox conteudoPrincipal = criarConteudoPrincipal();

        raiz.getChildren().addAll(cabecalho, conteudoPrincipal);
        VBox.setVgrow(conteudoPrincipal, Priority.ALWAYS);

        configurarEventos();
        carregarUsuarios();
        exibirModoPadraoFormulario();

        return raiz;
    }

    private VBox criarCabecalho() {
        Label titulo = new Label("Gestão de Usuários");
        titulo.getStyleClass().add("titulo-1");

        Label subtitulo = new Label("Administração de contas, perfis de acesso e credenciais do sistema.");
        subtitulo.getStyleClass().add("texto-secundario");

        return new VBox(4, titulo, subtitulo);
    }

    private HBox criarConteudoPrincipal() {
        VBox painelEsquerdo = criarPainelTabela();
        VBox painelDireito = criarCardFormulario();

        HBox.setHgrow(painelEsquerdo, Priority.ALWAYS);

        HBox layout = new HBox(20, painelEsquerdo, painelDireito);
        layout.setAlignment(Pos.TOP_LEFT);
        return layout;
    }

    private VBox criarPainelTabela() {
        VBox painel = new VBox(12);

        // Barra de Ações
        botaoNovo.getStyleClass().add("button-primario");
        botaoEditar.getStyleClass().add("button-secundario");
        botaoAlternarStatus.getStyleClass().add("button-secundario");
        botaoRedefinirSenha.getStyleClass().add("button-secundario");

        botaoEditar.setDisable(true);
        botaoAlternarStatus.setDisable(true);
        botaoRedefinirSenha.setDisable(true);

        HBox barraAcoes = new HBox(10, botaoNovo, botaoEditar, botaoAlternarStatus, botaoRedefinirSenha);
        barraAcoes.setAlignment(Pos.CENTER_LEFT);

        configurarColunasTabela();
        tabela.setItems(listaUsuarios);
        VBox.setVgrow(tabela, Priority.ALWAYS);

        painel.getChildren().addAll(barraAcoes, tabela);
        return painel;
    }

    private void configurarColunasTabela() {
        TableColumn<Usuario, String> colNome = new TableColumn<>("Nome");
        colNome.setCellValueFactory(dado -> new SimpleStringProperty(dado.getValue().getNome()));
        colNome.setPrefWidth(200);

        TableColumn<Usuario, String> colLogin = new TableColumn<>("Usuário / Login");
        colLogin.setCellValueFactory(dado -> new SimpleStringProperty(dado.getValue().getLogin()));
        colLogin.setPrefWidth(140);

        TableColumn<Usuario, String> colPerfil = new TableColumn<>("Perfil");
        colPerfil.setCellValueFactory(dado -> new SimpleStringProperty(
                dado.getValue().getRole() == RoleUsuario.ADMIN ? "Administrador" : "Gestor"));
        colPerfil.setPrefWidth(130);

        TableColumn<Usuario, String> colStatus = new TableColumn<>("Status");
        colStatus.setCellValueFactory(dado -> new SimpleStringProperty(
                dado.getValue().isAtivo() ? "Ativo" : "Inativo"));
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Label selo = new Label(item);
                    selo.getStyleClass().add("selo");
                    if ("Ativo".equalsIgnoreCase(item)) {
                        selo.getStyleClass().add("selo-sucesso");
                    } else {
                        selo.getStyleClass().add("selo-neutro");
                    }
                    setGraphic(selo);
                    setText(null);
                    setAlignment(Pos.CENTER);
                }
            }
        });
        colStatus.setPrefWidth(100);

        TableColumn<Usuario, String> colUltimoLogin = new TableColumn<>("Último Acesso");
        colUltimoLogin.setCellValueFactory(dado -> {
            LocalDateTime data = dado.getValue().getUltimoLogin();
            return new SimpleStringProperty(data != null ? data.format(FORMATO_DATA_HORA) : "Nunca acessou");
        });
        colUltimoLogin.setPrefWidth(160);

        tabela.getColumns().addAll(List.of(colNome, colLogin, colPerfil, colStatus, colUltimoLogin));
        tabela.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
    }

    private VBox criarCardFormulario() {
        painelFormulario.getStyleClass().add("card");
        painelFormulario.setPrefWidth(380);
        painelFormulario.setMinWidth(350);

        tituloFormulario.getStyleClass().add("titulo-2");

        campoNome.setPromptText("Nome completo");
        campoLogin.setPromptText("Nome de usuário para login");

        comboRole.setItems(FXCollections.observableArrayList(RoleUsuario.GESTOR, RoleUsuario.ADMIN));
        comboRole.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(RoleUsuario item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : formatarRole(item));
            }
        });
        comboRole.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(RoleUsuario item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : formatarRole(item));
            }
        });
        comboRole.setValue(RoleUsuario.GESTOR);
        comboRole.setMaxWidth(Double.MAX_VALUE);

        campoSenha.setPromptText("Mínimo 8 caracteres");
        campoConfirmacaoSenha.setPromptText("Repita a senha");

        grupoSenha.getChildren().addAll(
                new Label("Senha"),
                campoSenha,
                new Label("Confirmação de Senha"),
                campoConfirmacaoSenha
        );

        checkAtivo.setSelected(true);

        mensagemFeedback.setWrapText(true);
        mensagemFeedback.setVisible(false);
        mensagemFeedback.setManaged(false);

        botaoSalvar.getStyleClass().add("button-primario");
        botaoCancelar.getStyleClass().add("button-secundario");

        HBox barraBotoesForm = new HBox(10, botaoSalvar, botaoCancelar);
        barraBotoesForm.setAlignment(Pos.CENTER_RIGHT);

        painelFormulario.getChildren().addAll(
                tituloFormulario,
                new Label("Nome Completo *"),
                campoNome,
                new Label("Login *"),
                campoLogin,
                new Label("Perfil de Acesso *"),
                comboRole,
                grupoSenha,
                checkAtivo,
                mensagemFeedback,
                barraBotoesForm
        );

        return painelFormulario;
    }

    private String formatarRole(RoleUsuario role) {
        return role == RoleUsuario.ADMIN ? "Administrador (Acesso total)" : "Gestor (Escalas e plantões)";
    }

    private void configurarEventos() {
        tabela.getSelectionModel().selectedItemProperty().addListener((obs, antigo, selecionado) -> {
            boolean temSelecao = selecionado != null;
            botaoEditar.setDisable(!temSelecao);
            botaoAlternarStatus.setDisable(!temSelecao);
            botaoRedefinirSenha.setDisable(!temSelecao);

            if (temSelecao) {
                botaoAlternarStatus.setText(selecionado.isAtivo() ? "Desativar" : "Reativar");
            }
        });

        botaoNovo.setOnAction(e -> iniciarNovoCadastro());
        botaoEditar.setOnAction(e -> iniciarEdicao());
        botaoAlternarStatus.setOnAction(e -> alternarStatusUsuarioSelecionado());
        botaoRedefinirSenha.setOnAction(e -> abrirDialogoRedefinirSenha());

        botaoSalvar.setOnAction(e -> salvarUsuario());
        botaoCancelar.setOnAction(e -> exibirModoPadraoFormulario());
    }

    private void carregarUsuarios() {
        try {
            listaUsuarios.setAll(gestaoUsuariosService.listar());
        } catch (AcessoNegadoException excecao) {
            DialogUtil.mostrarErro("Acesso Negado", excecao.getMessage());
        } catch (Exception excecao) {
            DialogUtil.mostrarErro("Erro ao carregar usuários", "Não foi possível carregar a lista de usuários: " + excecao.getMessage());
        }
    }

    private void iniciarNovoCadastro() {
        usuarioEmEdicao = null;
        tituloFormulario.setText("Novo Usuário");
        campoNome.clear();
        campoLogin.clear();
        campoLogin.setDisable(false);
        comboRole.setValue(RoleUsuario.GESTOR);
        campoSenha.clear();
        campoConfirmacaoSenha.clear();
        grupoSenha.setVisible(true);
        grupoSenha.setManaged(true);
        checkAtivo.setSelected(true);
        checkAtivo.setDisable(false);
        ocultarFeedback();
        campoNome.requestFocus();
    }

    private void iniciarEdicao() {
        Usuario selecionado = tabela.getSelectionModel().getSelectedItem();
        if (selecionado == null) return;

        usuarioEmEdicao = selecionado;
        tituloFormulario.setText("Editar Usuário");
        campoNome.setText(selecionado.getNome());
        campoLogin.setText(selecionado.getLogin());
        campoLogin.setDisable(false);
        comboRole.setValue(selecionado.getRole());
        grupoSenha.setVisible(false);
        grupoSenha.setManaged(false);
        checkAtivo.setSelected(selecionado.isAtivo());
        checkAtivo.setDisable(false);
        ocultarFeedback();
        campoNome.requestFocus();
    }

    private void exibirModoPadraoFormulario() {
        iniciarNovoCadastro();
    }

    private void salvarUsuario() {
        ocultarFeedback();
        String nome = campoNome.getText() != null ? campoNome.getText().trim() : "";
        String login = campoLogin.getText() != null ? campoLogin.getText().trim() : "";
        RoleUsuario role = comboRole.getValue();
        boolean ativo = checkAtivo.isSelected();

        try {
            if (usuarioEmEdicao == null) {
                String senha = campoSenha.getText();
                String confirmacao = campoConfirmacaoSenha.getText();
                gestaoUsuariosService.cadastrar(nome, login, senha, confirmacao, role, ativo);
                mostrarFeedbackSucesso("Usuário cadastrado com sucesso!");
                iniciarNovoCadastro();
            } else {
                gestaoUsuariosService.atualizar(usuarioEmEdicao.getId(), nome, login, role, ativo);
                mostrarFeedbackSucesso("Usuário atualizado com sucesso!");
            }
            carregarUsuarios();
        } catch (RegraUsuarioException | SenhaFracaException | SenhasNaoConferemException | IllegalArgumentException excecao) {
            mostrarFeedbackErro(excecao.getMessage());
        } catch (Exception excecao) {
            mostrarFeedbackErro("Erro inesperado ao salvar: " + excecao.getMessage());
        }
    }

    private void alternarStatusUsuarioSelecionado() {
        Usuario selecionado = tabela.getSelectionModel().getSelectedItem();
        if (selecionado == null) return;

        boolean novoStatus = !selecionado.isAtivo();
        try {
            gestaoUsuariosService.alterarStatus(selecionado.getId(), novoStatus);
            carregarUsuarios();
            tabela.getSelectionModel().select(selecionado);
            mostrarFeedbackSucesso(novoStatus ? "Usuário reativado com sucesso!" : "Usuário desativado com sucesso!");
        } catch (RegraUsuarioException | AcessoNegadoException excecao) {
            DialogUtil.mostrarErro("Não foi possível alterar o status", excecao.getMessage());
        } catch (Exception excecao) {
            DialogUtil.mostrarErro("Erro ao alterar status", "Ocorreu um erro ao alterar o status do usuário: " + excecao.getMessage());
        }
    }

    private void abrirDialogoRedefinirSenha() {
        Usuario selecionado = tabela.getSelectionModel().getSelectedItem();
        if (selecionado == null) return;

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Redefinir Senha");
        dialog.setHeaderText("Redefinir senha de " + selecionado.getNome() + " (" + selecionado.getLogin() + ")");

        PasswordField campoNovaSenha = new PasswordField();
        campoNovaSenha.setPromptText("Mínimo 8 caracteres");
        PasswordField campoConfirma = new PasswordField();
        campoConfirma.setPromptText("Repita a nova senha");
        Label erroDialogo = new Label();
        erroDialogo.getStyleClass().add("campo-com-erro");
        erroDialogo.setVisible(false);
        erroDialogo.setManaged(false);

        VBox corpoDialogo = new VBox(10,
                new Label("Nova Senha *"), campoNovaSenha,
                new Label("Confirme a Nova Senha *"), campoConfirma,
                erroDialogo);
        corpoDialogo.setPadding(new Insets(20));

        DialogUtil.aplicarTema(dialog.getDialogPane());
        dialog.getDialogPane().setContent(corpoDialogo);
        ButtonType botaoConfirmar = new ButtonType("Redefinir Senha", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(botaoConfirmar, ButtonType.CANCEL);

        Button botaoOk = (Button) dialog.getDialogPane().lookupButton(botaoConfirmar);
        botaoOk.getStyleClass().add("button-primario");
        botaoOk.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            String novaSenha = campoNovaSenha.getText();
            String confirmacao = campoConfirma.getText();
            try {
                gestaoUsuariosService.redefinirSenha(selecionado.getId(), novaSenha, confirmacao);
                mostrarFeedbackSucesso("Senha do usuário redefinida com sucesso!");
            } catch (SenhaFracaException | SenhasNaoConferemException | RegraUsuarioException excecao) {
                event.consume(); // Não fecha o diálogo
                erroDialogo.setText(excecao.getMessage());
                erroDialogo.setVisible(true);
                erroDialogo.setManaged(true);
            } catch (Exception excecao) {
                event.consume();
                erroDialogo.setText("Erro ao redefinir senha: " + excecao.getMessage());
                erroDialogo.setVisible(true);
                erroDialogo.setManaged(true);
            }
        });

        dialog.showAndWait();
    }

    private void mostrarFeedbackSucesso(String mensagem) {
        mensagemFeedback.setText(mensagem);
        mensagemFeedback.getStyleClass().removeAll("campo-com-erro", "selo-perigo", "selo-sucesso");
        mensagemFeedback.getStyleClass().addAll("selo", "selo-sucesso");
        mensagemFeedback.setVisible(true);
        mensagemFeedback.setManaged(true);
    }

    private void mostrarFeedbackErro(String mensagem) {
        mensagemFeedback.setText(mensagem);
        mensagemFeedback.getStyleClass().removeAll("campo-com-erro", "selo-perigo", "selo-sucesso");
        mensagemFeedback.getStyleClass().addAll("selo", "selo-perigo");
        mensagemFeedback.setVisible(true);
        mensagemFeedback.setManaged(true);
    }

    private void ocultarFeedback() {
        mensagemFeedback.setVisible(false);
        mensagemFeedback.setManaged(false);
        mensagemFeedback.setText("");
    }
}
