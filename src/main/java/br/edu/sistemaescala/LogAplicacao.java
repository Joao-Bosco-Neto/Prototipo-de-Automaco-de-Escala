package br.edu.sistemaescala;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * Log em arquivo da aplicacao, usando java.util.logging (nao ha, nem
 * precisa haver, biblioteca de log no pom.xml).
 *
 * O arquivo fica em ~/.sistema-escala/logs/aplicacao.log — o mesmo
 * diretorio privado que o ConexaoBanco ja usa para o banco e a chave,
 * para nao espalhar arquivo do sistema pela pasta do usuario.
 *
 * O tamanho e limitado pelo proprio FileHandler: ao chegar em 2 MB ele
 * reabre o arquivo do zero. Usamos uma unica geracao de proposito —
 * com count maior que 1 o FileHandler acrescenta o numero da geracao ao
 * nome (aplicacao.log.0), e o caminho combinado e exatamente
 * ~/.sistema-escala/logs/aplicacao.log.
 */
public final class LogAplicacao {

    private static final String NOME_LOGGER = "br.edu.sistemaescala";
    private static final Path DIRETORIO_LOGS =
            Path.of(System.getProperty("user.home"), ".sistema-escala", "logs");
    private static final Path ARQUIVO_LOG = DIRETORIO_LOGS.resolve("aplicacao.log");
    private static final int TAMANHO_MAXIMO_BYTES = 2_000_000;
    private static final int GERACOES = 1;

    private static Logger logger;

    private LogAplicacao() {
        // classe utilitaria: nao deve ser instanciada
    }

    /** Logger da aplicacao, ja com o arquivo configurado na primeira chamada. */
    public static synchronized Logger logger() {
        if (logger == null) {
            logger = configurar();
        }
        return logger;
    }

    /** Registra uma falha com o stack trace completo. */
    public static void registrarErro(String mensagem, Throwable erro) {
        logger().log(Level.SEVERE, mensagem, erro);
    }

    /** Caminho do arquivo de log, util para orientar o usuario/suporte. */
    public static Path arquivoLog() {
        return ARQUIVO_LOG;
    }

    private static Logger configurar() {
        Logger novoLogger = Logger.getLogger(NOME_LOGGER);
        novoLogger.setLevel(Level.ALL);
        try {
            Files.createDirectories(DIRETORIO_LOGS);
            restringirPermissoes(DIRETORIO_LOGS);
            FileHandler manipulador = new FileHandler(
                    ARQUIVO_LOG.toAbsolutePath().toString(),
                    TAMANHO_MAXIMO_BYTES, GERACOES, true);
            manipulador.setFormatter(new SimpleFormatter());
            manipulador.setLevel(Level.ALL);
            novoLogger.addHandler(manipulador);
        } catch (IOException | SecurityException excecao) {
            // Sem arquivo de log a aplicacao continua: o console vira o
            // unico destino, mas ninguem fica sem poder abrir o sistema.
            System.err.println("Nao foi possivel abrir o arquivo de log em "
                    + ARQUIVO_LOG + ": " + excecao.getMessage());
        }
        return novoLogger;
    }

    /**
     * Deixa a pasta de logs acessivel so ao dono, como o ConexaoBanco ja faz
     * com o banco: o stack trace nao tem senha, mas expoe caminhos e a
     * estrutura interna do sistema.
     */
    private static void restringirPermissoes(Path caminho) {
        try {
            Files.setPosixFilePermissions(caminho, EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE));
        } catch (IOException | UnsupportedOperationException ignorado) {
            // Sistema sem POSIX (Windows) ou sem permissao: segue com a ACL nativa.
        }
    }
}
