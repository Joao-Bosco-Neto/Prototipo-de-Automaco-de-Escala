package br.edu.sistemaescala.backend.dao;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Fornece conexoes com o banco H2 (modo de compatibilidade PostgreSQL).
 *
 * O arquivo do banco fica em ./data/sistema_escala.mv.db e e criado na
 * primeira execucao. A criacao das tabelas NAO acontece aqui: quem faz
 * isso e o BancoInicializador, chamado uma vez na partida da aplicacao.
 */
public final class ConexaoBanco {

    private static final String URL =
            "jdbc:h2:file:./data/sistema_escala;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE";
    private static final String USUARIO = "sa";
    private static final String SENHA = "";

    private ConexaoBanco() {
        // classe utilitaria: nao deve ser instanciada
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USUARIO, SENHA);
    }
}
