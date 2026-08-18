package br.edu.sistemaescala.backend.dao;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Classe utilitaria para obter conexao com o banco H2 (modo PostgreSQL).
 *
 * O arquivo do banco fica em ./data/sistema_escala.mv.db, gerado automaticamente
 * na primeira execucao. O script de criacao das tabelas fica em
 * src/main/resources/banco/schema.sql
 */
public class ConexaoBanco {

    private static final String URL = "jdbc:h2:file:./data/sistema_escala;MODE=PostgreSQL";
    private static final String USER = "sa";
    private static final String PASSWORD = "";

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }
}
