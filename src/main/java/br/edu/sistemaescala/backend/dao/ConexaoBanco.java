package br.edu.sistemaescala.backend.dao;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Base64;
import java.util.EnumSet;
import java.util.Set;

/**
 * Fornece conexoes com o banco H2 (modo de compatibilidade PostgreSQL).
 *
 * O arquivo do banco fica no diretorio privado do usuario e e criado na
 * primeira execucao. A criacao das tabelas NAO acontece aqui: quem faz
 * isso e o BancoInicializador, chamado uma vez na partida da aplicacao.
 */
public final class ConexaoBanco {

    private static final String USUARIO = "sa";
    private static final Path DIRETORIO_DADOS =
            Path.of(System.getProperty("user.home"), ".sistema-escala");
    private static final Path ARQUIVO_SENHAS = DIRETORIO_DADOS.resolve("banco.key");
    private static final Path ARQUIVO_BANCO = DIRETORIO_DADOS.resolve("sistema_escala");
    private static final SecureRandom ALEATORIO = new SecureRandom();
    private static String senhaBanco;

    private ConexaoBanco() {
        // classe utilitaria: nao deve ser instanciada
    }

    public static synchronized Connection getConnection() throws SQLException {
        String[] senhas = obterSenhas();
        String caminho = ARQUIVO_BANCO.toAbsolutePath().toString().replace('\\', '/');
        String url = "jdbc:h2:file:" + caminho
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;CIPHER=AES";
        return DriverManager.getConnection(url, USUARIO, senhas[0] + " " + senhas[1]);
    }

    private static String[] obterSenhas() throws SQLException {
        if (senhaBanco != null) {
            return senhaBanco.split(" ", 2);
        }

        try {
            Files.createDirectories(DIRETORIO_DADOS);
            restringirPermissoes(DIRETORIO_DADOS);

            if (Files.notExists(ARQUIVO_SENHAS)) {
                String valor = gerarSenha() + " " + gerarSenha();
                Files.writeString(ARQUIVO_SENHAS, valor, StandardCharsets.US_ASCII);
                restringirPermissoes(ARQUIVO_SENHAS);
            }

            String conteudo = Files.readString(ARQUIVO_SENHAS, StandardCharsets.US_ASCII).trim();
            String[] partes = conteudo.split("\\s+");
            if (partes.length != 2 || partes[0].isBlank() || partes[1].isBlank()) {
                throw new IOException("arquivo de senha do banco invalido");
            }
            senhaBanco = partes[0] + " " + partes[1];
            return partes;
        } catch (IOException | SecurityException excecao) {
            throw new SQLException("Nao foi possivel preparar a senha e o diretorio privado do banco", excecao);
        }
    }

    private static String gerarSenha() {
        byte[] bytes = new byte[32];
        ALEATORIO.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static void restringirPermissoes(Path caminho) throws IOException {
        try {
            Set<PosixFilePermission> permissoes = EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE);
            if (Files.isRegularFile(caminho)) {
                permissoes.remove(PosixFilePermission.OWNER_EXECUTE);
            }
            Files.setPosixFilePermissions(caminho, permissoes);
        } catch (UnsupportedOperationException ignorado) {
            // Windows usa ACL abaixo; outros sistemas sem POSIX seguem com a ACL nativa.
        }

        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            String usuario = System.getProperty("user.name");
            Process processo = new ProcessBuilder("icacls", caminho.toString(),
                    "/inheritance:r", "/grant:r", usuario + ":F")
                    .redirectErrorStream(true)
                    .start();
            try {
                if (processo.waitFor() != 0) {
                    throw new IOException("icacls nao conseguiu restringir " + caminho);
                }
            } catch (InterruptedException excecao) {
                Thread.currentThread().interrupt();
                throw new IOException("interrupcao ao restringir " + caminho, excecao);
            }
        }
    }
}
