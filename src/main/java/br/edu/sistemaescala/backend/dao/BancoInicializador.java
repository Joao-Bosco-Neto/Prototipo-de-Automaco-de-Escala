package br.edu.sistemaescala.backend.dao;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Cria as tabelas e a carga inicial na primeira execucao da aplicacao.
 *
 * Antes desta classe existir, o schema.sql precisava ser rodado a mao
 * contra o banco — o que fazia cada desenvolvedor ter um banco em estado
 * diferente. Agora a aplicacao sobe com o banco vazio e monta tudo sozinha.
 *
 * Os dois scripts sao idempotentes (CREATE TABLE IF NOT EXISTS e INSERT
 * condicional), entao executar de novo nao quebra nem duplica dados.
 */
public final class BancoInicializador {

    private static final String SCHEMA = "/banco/schema.sql";
    private static final String SEED = "/banco/seed.sql";

    private BancoInicializador() {
    }

    /** Executa schema e carga inicial. Chamar uma vez, na partida. */
    public static void inicializar() {
        try (Connection conexao = ConexaoBanco.getConnection()) {
            executarScript(conexao, SCHEMA);
            executarScript(conexao, SEED);
        } catch (SQLException | IOException e) {
            throw new IllegalStateException(
                    "Falha ao inicializar o banco de dados: " + e.getMessage(), e);
        }
    }

    private static void executarScript(Connection conexao, String recurso)
            throws SQLException, IOException {
        for (String comando : lerComandos(recurso)) {
            try (Statement st = conexao.createStatement()) {
                st.execute(comando);
            }
        }
    }

    /**
     * Le o arquivo do classpath e separa os comandos pelo ponto e virgula,
     * descartando linhas de comentario.
     */
    private static List<String> lerComandos(String recurso) throws IOException {
        InputStream entrada = BancoInicializador.class.getResourceAsStream(recurso);
        if (entrada == null) {
            throw new IOException("Script nao encontrado no classpath: " + recurso);
        }

        List<String> comandos = new ArrayList<>();
        StringBuilder atual = new StringBuilder();

        try (BufferedReader leitor = new BufferedReader(
                new InputStreamReader(entrada, StandardCharsets.UTF_8))) {
            String linha;
            while ((linha = leitor.readLine()) != null) {
                String limpa = linha.trim();
                if (limpa.isEmpty() || limpa.startsWith("--")) {
                    continue;
                }
                atual.append(linha).append('\n');
                if (limpa.endsWith(";")) {
                    String comando = atual.toString().trim();
                    comandos.add(comando.substring(0, comando.length() - 1));
                    atual.setLength(0);
                }
            }
        }

        String resto = atual.toString().trim();
        if (!resto.isEmpty()) {
            comandos.add(resto);
        }
        return comandos;
    }
}
