package br.edu.sistemaescala.frontend.controller;

import java.util.Objects;

import br.edu.sistemaescala.backend.service.BloqueioInatividadeService;
import br.edu.sistemaescala.backend.service.SessaoUsuario;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * Controller responsável pela exibição e interação da tela de bloqueio por inatividade (Issue #61 / OWASP A07).
 *
 * Apresentado como sobreposição modal no Shell, preservando integralmente o estado das telas por baixo.
 * Exige a senha do usuário logado para retomar a sessão ou permite o encerramento seguro.
 */
public class BloqueioController {

    private final BloqueioInatividadeService bloqueioService;
    private final SessaoUsuario sessaoUsuario;
    private final Runnable aoTrocarUsuario;
    private final Runnable aoDesbloquear;

    private StackPane painelOverlay;
    private Label rotuloUsuario;
    private PasswordField campoSenha;
    private Label mensagemErro;

    public BloqueioController(BloqueioInatividadeService bloqueioService,
                              SessaoUsuario sessaoUsuario,
                              Runnable aoTrocarUsuario,
                              Runnable aoDesbloquear) {
        this.bloqueioService = Objects.requireNonNull(bloqueioService, "BloqueioInatividadeService nao pode ser nulo");
        this.sessaoUsuario = Objects.requireNonNull(sessaoUsuario, "SessaoUsuario nao pode ser nula");
        this.aoTrocarUsuario = Objects.requireNonNull(aoTrocarUsuario, "Callback aoTrocarUsuario nao pode ser nulo");
        this.aoDesbloquear = Objects.requireNonNull(aoDesbloquear, "Callback aoDesbloquear nao pode ser nulo");
    }

    public StackPane criarPainelBloqueio() {
        painelOverlay = new StackPane();
        painelOverlay.getStyleClass().add("painel-bloqueio-overlay");
        painelOverlay.setVisible(false);
        painelOverlay.setManaged(false);

        VBox card = new VBox(16);
        card.getStyleClass().add("card-bloqueio");
        card.setMaxWidth(460);
        card.setAlignment(Pos.TOP_LEFT);

        // Ícone e cabeçalho
        Label icone = new Label("🔒");
        icone.getStyleClass().add("bloqueio-icone-texto");
        StackPane iconeContainer = new StackPane(icone);
        iconeContainer.getStyleClass().add("bloqueio-icone-container");

        Label titulo = new Label("Sessão Bloqueada");
        titulo.getStyleClass().add("titulo-1");

        Label subtitulo = new Label("Aplicação bloqueada por inatividade. Informe sua senha para retomar o trabalho.");
        subtitulo.getStyleClass().add("texto-secundario");
        subtitulo.setWrapText(true);

        HBox cabecalho = new HBox(16, iconeContainer, new VBox(4, titulo, subtitulo));
        cabecalho.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(cabecalho.getChildren().get(1), Priority.ALWAYS);

        // Card com dados do usuário logado
        Label rotuloIdentificacao = new Label("Usuário autenticado na estação:");
        rotuloIdentificacao.getStyleClass().add("texto-secundario");

        rotuloUsuario = new Label();
        rotuloUsuario.getStyleClass().add("titulo-2");

        VBox cardUsuario = new VBox(4, rotuloIdentificacao, rotuloUsuario);
        cardUsuario.getStyleClass().add("bloqueio-usuario-card");

        // Formulário de senha
        Label labelSenha = new Label("Senha de acesso *");
        campoSenha = new PasswordField();
        campoSenha.setPromptText("Digite sua senha para desbloquear");

        mensagemErro = new Label();
        mensagemErro.getStyleClass().add("login-mensagem-erro");
        mensagemErro.setWrapText(true);
        mensagemErro.setVisible(false);
        mensagemErro.setManaged(false);

        Button botaoDesbloquear = new Button("Desbloquear");
        botaoDesbloquear.getStyleClass().add("button-primario");
        botaoDesbloquear.setMaxWidth(Double.MAX_VALUE);
        botaoDesbloquear.setDefaultButton(true);

        Button botaoSair = new Button("Trocar de usuário / Sair");
        botaoSair.getStyleClass().add("button-secundario");
        botaoSair.setMaxWidth(Double.MAX_VALUE);

        // Ações
        Runnable tentarDesbloqueio = () -> {
            String senha = campoSenha.getText();
            if (senha == null || senha.isBlank()) {
                mostrarErro("Informe a senha para desbloquear a sessão.");
                return;
            }

            boolean sucesso = bloqueioService.desbloquear(senha);
            if (sucesso) {
                ocultarErro();
                campoSenha.clear();
                aoDesbloquear.run();
            } else {
                mostrarErro("Senha incorreta. Tente novamente.");
                campoSenha.clear();
                campoSenha.requestFocus();
            }
        };

        botaoDesbloquear.setOnAction(e -> tentarDesbloqueio.run());
        campoSenha.setOnAction(e -> tentarDesbloqueio.run());

        botaoSair.setOnAction(e -> {
            sessaoUsuario.encerrar();
            aoTrocarUsuario.run();
        });

        card.getChildren().addAll(
                cabecalho,
                cardUsuario,
                labelSenha,
                campoSenha,
                mensagemErro,
                botaoDesbloquear,
                botaoSair
        );

        painelOverlay.getChildren().add(card);
        StackPane.setMargin(card, new Insets(20));

        return painelOverlay;
    }

    public void prepararExibicao() {
        if (rotuloUsuario != null) {
            String identificacao = sessaoUsuario.usuarioAtual()
                    .map(u -> u.getNome() + " (" + u.getLogin() + " · " + u.getRole() + ")")
                    .orElse("Usuário não identificado");
            rotuloUsuario.setText(identificacao);
        }
        if (campoSenha != null) {
            campoSenha.clear();
            Platform.runLater(campoSenha::requestFocus);
        }
        ocultarErro();
    }

    private void mostrarErro(String mensagem) {
        if (mensagemErro != null) {
            mensagemErro.setText(mensagem);
            mensagemErro.setVisible(true);
            mensagemErro.setManaged(true);
        }
    }

    private void ocultarErro() {
        if (mensagemErro != null) {
            mensagemErro.setText("");
            mensagemErro.setVisible(false);
            mensagemErro.setManaged(false);
        }
    }
}

