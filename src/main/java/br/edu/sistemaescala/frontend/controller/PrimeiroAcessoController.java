package br.edu.sistemaescala.frontend.controller;

import br.edu.sistemaescala.backend.service.PrimeiroAcessoService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

public class PrimeiroAcessoController {

    private final PrimeiroAcessoService primeiroAcessoService;

    public PrimeiroAcessoController(PrimeiroAcessoService primeiroAcessoService) {
        this.primeiroAcessoService = primeiroAcessoService;
    }

    public Parent criarTela() {
        Label titulo = new Label("Configuração inicial");
        titulo.getStyleClass().add("titulo-1");

        Label descricao = new Label("Cadastre a organização e o administrador do sistema.");
        TextField nomeOrganizacao = new TextField();
        nomeOrganizacao.setPromptText("Nome da organização");
        TextField login = new TextField();
        login.setPromptText("Usuário administrador");
        PasswordField senha = new PasswordField();
        senha.setPromptText("Senha (mínimo de 8 caracteres)");
        PasswordField confirmacaoSenha = new PasswordField();
        confirmacaoSenha.setPromptText("Confirme a senha");
        Button salvar = new Button("Concluir configuração");
        salvar.getStyleClass().add("button-primario");
        Label mensagem = new Label();
        mensagem.setWrapText(true);

        GridPane campos = new GridPane();
        campos.setHgap(12);
        campos.setVgap(12);
        campos.add(new Label("Organização"), 0, 0);
        campos.add(nomeOrganizacao, 1, 0);
        campos.add(new Label("Usuário"), 0, 1);
        campos.add(login, 1, 1);
        campos.add(new Label("Senha"), 0, 2);
        campos.add(senha, 1, 2);
        campos.add(new Label("Confirmação"), 0, 3);
        campos.add(confirmacaoSenha, 1, 3);

        salvar.setOnAction(evento -> {
            try {
                primeiroAcessoService.configurar(nomeOrganizacao.getText(), login.getText(),
                        senha.getText(), confirmacaoSenha.getText());
                salvar.setDisable(true);
                mensagem.setText("Configuração concluída. O administrador já pode entrar no sistema.");
                // Cor buscada via variavel do tema (app.css), nao mais hex fixo.
                mensagem.setStyle("-fx-text-fill: -cor-sucesso;");
                limpar(senha, confirmacaoSenha);
            } catch (RuntimeException excecao) {
                mensagem.setText(excecao.getMessage());
                mensagem.setStyle("-fx-text-fill: -cor-perigo;");
            }
        });

        VBox raiz = new VBox(18, titulo, descricao, campos, salvar, mensagem);
        raiz.setAlignment(Pos.CENTER);
        raiz.setPadding(new Insets(40));
        raiz.setMaxWidth(620);
        return raiz;
    }

    private void limpar(PasswordField senha, PasswordField confirmacaoSenha) {
        senha.clear();
        confirmacaoSenha.clear();
    }
}