package br.edu.sistemaescala.frontend.controller;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.prefs.Preferences;

import br.edu.sistemaescala.backend.model.Usuario;
import br.edu.sistemaescala.backend.service.AutenticacaoService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;

public class LoginController {

    private static final String CHAVE_USUARIO_LEMBRADO = "usuario";
    private static final String TEXTO_ERRO = "Usuário ou senha inválidos.";

    private final AutenticacaoService autenticacaoService;
    private final Consumer<Usuario> aoAutenticar;
    private final Preferences preferencias = Preferences.userNodeForPackage(LoginController.class);

    public LoginController(AutenticacaoService autenticacaoService, Consumer<Usuario> aoAutenticar) {
        this.autenticacaoService = autenticacaoService;
        this.aoAutenticar = aoAutenticar;
    }

    public Parent criarTela(String nomeOrganizacao) {
        BorderPane raiz = new BorderPane();
        raiz.getStyleClass().add("tela-login");
        raiz.setLeft(criarPainelIdentificacao(nomeOrganizacao));
        raiz.setCenter(criarPainelCredenciais());
        return raiz;
    }

    private VBox criarPainelIdentificacao(String nomeOrganizacao) {
        Label identificacao = new Label(nomeOrganizacao);
        identificacao.getStyleClass().add("login-organizacao");
        Label titulo = new Label("Sistema de Escala de Plantão");
        titulo.getStyleClass().add("login-titulo");
        titulo.setWrapText(true);
        Label descricao = new Label("Sistema institucional de uso interno, instalado localmente na organização. Não possui acesso pela internet.");
        descricao.getStyleClass().add("login-descricao");
        descricao.setWrapText(true);
        Label seguranca = new Label("CREDENCIAIS PROTEGIDAS\nSenhas armazenadas somente como hash BCrypt.\n\nBANCO LOCAL PROTEGIDO\nArquivo criptografado e restrito ao usuário da estação.");
        seguranca.getStyleClass().add("login-seguranca");
        seguranca.setWrapText(true);
        Label versao = new Label("v1.0 · Java 21 / JavaFX 21");
        versao.getStyleClass().add("login-versao");
        VBox painel = new VBox(18, identificacao, titulo, descricao, seguranca, versao);
        painel.setPadding(new Insets(34, 30, 26, 30));
        painel.setAlignment(Pos.TOP_LEFT);
        painel.setPrefWidth(344);
        painel.getStyleClass().add("login-painel-lateral");
        return painel;
    }

    private VBox criarPainelCredenciais() {
        Label titulo = new Label("Acesso controlado");
        titulo.getStyleClass().add("login-titulo-formulario");
        Label descricao = new Label("Identifique-se com suas credenciais para acessar o sistema.");
        descricao.getStyleClass().add("texto-secundario");
        descricao.setWrapText(true);
        TextField campoUsuario = new TextField(preferencias.get(CHAVE_USUARIO_LEMBRADO, ""));
        campoUsuario.setPromptText("Usuário");
        PasswordField campoSenha = new PasswordField();
        campoSenha.setPromptText("Senha");
        CheckBox lembrar = new CheckBox("Lembrar meu usuário nesta estação");
        Label mensagem = new Label();
        mensagem.setWrapText(true);
        mensagem.getStyleClass().add("login-mensagem-erro");
        mensagem.setManaged(false);
        Button entrar = new Button("Entrar no sistema");
        entrar.getStyleClass().add("button-primario");
        entrar.setDefaultButton(true);
        entrar.setMaxWidth(Double.MAX_VALUE);
        Label ajuda = new Label("Esqueceu sua senha? Procure o administrador do sistema.");
        ajuda.getStyleClass().add("texto-secundario");
        ajuda.setWrapText(true);

        Runnable autenticar = () -> {
            String login = campoUsuario.getText().trim();
            String senha = campoSenha.getText();
            Optional<Usuario> usuario = login.isBlank() || senha.isBlank()
                    ? Optional.empty() : autenticacaoService.autenticar(login, senha);
            if (usuario.isPresent()) {
                if (lembrar.isSelected()) preferencias.put(CHAVE_USUARIO_LEMBRADO, login);
                else preferencias.remove(CHAVE_USUARIO_LEMBRADO);
                campoSenha.clear();
                aoAutenticar.accept(usuario.orElseThrow());
            } else {
                mensagem.setText(TEXTO_ERRO);
                mensagem.setManaged(true);
                campoSenha.clear();
                campoSenha.requestFocus();
            }
        };
        entrar.setOnAction(evento -> autenticar.run());
        campoSenha.setOnAction(evento -> autenticar.run());

        VBox painel = new VBox(12, titulo, descricao,
                new Label("Usuário"), campoUsuario, new Label("Senha"), campoSenha,
                lembrar, mensagem, entrar, ajuda);
        painel.setPadding(new Insets(46, 54, 40, 54));
        painel.setAlignment(Pos.CENTER_LEFT);
        painel.setMaxWidth(560);
        painel.getStyleClass().add("login-painel-formulario");
        return painel;
    }
}