package br.edu.sistemaescala.backend.dao;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Cria as tabelas e a carga inicial na primeira execucao da aplicacao.
 *
 * Antes desta classe existir, o schema.sql precisava ser rodado a mao
 * contra o banco — o que fazia cada desenvolvedor ter um banco em estado
 * diferente. Agora a aplicacao sobe com o banco vazio e monta tudo sozinha.
 *
 * Os dois scripts sao idempotentes (CREATE TABLE IF NOT EXISTS e INSERT
 * condicional), entao executar de novo nao quebra nem duplica dados.
 *
 * Justamente por serem idempotentes, mudar uma definicao no schema.sql nao
 * alcanca bancos ja criados: o CREATE TABLE IF NOT EXISTS simplesmente nao
 * roda. E por isso que existe o passo de migracao depois dos scripts.
 */
public final class BancoInicializador {

    private static final Logger LOG = Logger.getLogger(BancoInicializador.class.getName());

    private static final String SCHEMA = "/banco/schema.sql";
    private static final String SEED = "/banco/seed.sql";

    /**
     * Chaves estrangeiras de escala_funcionario.cobertura_de que ainda nao
     * apagam em cascata.
     *
     * Bancos criados antes da issue #44 tem essa FK sem ON DELETE CASCADE e
     * com nome gerado pelo proprio banco (CONSTRAINT_E5, CONSTRAINT_E5C...),
     * impossivel de acertar por DDL fixo — dai a busca pelo nome real.
     *
     * O filtro por delete_rule torna a consulta autolimitante: depois da
     * correcao ela nao devolve mais nada, entao o passo pode rodar a cada
     * partida sem efeito. As views usadas sao as do padrao SQL, presentes
     * tanto no H2 quanto no PostgreSQL.
     */
    private static final String SQL_FK_COBERTURA_SEM_CASCATA = """
            SELECT rc.constraint_name
            FROM information_schema.referential_constraints rc
            JOIN information_schema.key_column_usage kcu
              ON kcu.constraint_name = rc.constraint_name
             AND kcu.constraint_schema = rc.constraint_schema
            WHERE LOWER(kcu.table_name) = 'escala_funcionario'
              AND LOWER(kcu.column_name) = 'cobertura_de'
              AND rc.delete_rule <> 'CASCADE'
            """;

    private static final String SQL_RECRIAR_FK_COBERTURA = """
            ALTER TABLE escala_funcionario ADD CONSTRAINT fk_ef_cobertura
                FOREIGN KEY (cobertura_de) REFERENCES escala_funcionario(id) ON DELETE CASCADE
            """;

    private BancoInicializador() {
    }

    /** Executa schema, carga inicial e as migracoes de bancos ja existentes. Chamar uma vez, na partida. */
    public static void inicializar() {
        try (Connection conexao = ConexaoBanco.getConnection()) {
            executarScript(conexao, SCHEMA);
            executarScript(conexao, SEED);
            migrarCascataDeCobertura(conexao);
        } catch (SQLException | IOException e) {
            throw new IllegalStateException(
                    "Falha ao inicializar o banco de dados: " + e.getMessage(), e);
        }
    }

    /**
     * Poe ON DELETE CASCADE em escala_funcionario.cobertura_de nos bancos que
     * nasceram sem ele.
     *
     * Sem a cascata, limpar um mes (issue #44) aborta por violacao de chave
     * estrangeira assim que existir uma cobertura, num turno de outro mes,
     * apontando para uma alocacao do mes que esta sendo apagado. Coberturas so
     * chegam no M5, entao a troca aqui nao mexe em dado nenhum hoje: e so
     * deixar o banco antigo com a mesma definicao do schema atual.
     */
    private static void migrarCascataDeCobertura(Connection conexao) throws SQLException {
        List<String> semCascata = new ArrayList<>();
        try (Statement st = conexao.createStatement();
             ResultSet rs = st.executeQuery(SQL_FK_COBERTURA_SEM_CASCATA)) {
            while (rs.next()) {
                semCascata.add(rs.getString(1));
            }
        }

        for (String nome : semCascata) {
            try (Statement st = conexao.createStatement()) {
                // O nome vem do proprio catalogo do banco, nao de entrada de
                // usuario; as aspas preservam a caixa que o banco registrou.
                st.execute("ALTER TABLE escala_funcionario DROP CONSTRAINT \"" + nome + "\"");
                st.execute(SQL_RECRIAR_FK_COBERTURA);
            }
            LOG.info("Migracao: FK " + nome + " (cobertura_de) recriada com ON DELETE CASCADE");
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
