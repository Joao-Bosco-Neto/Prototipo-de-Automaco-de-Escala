package br.edu.sistemaescala.backend.dao;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

import br.edu.sistemaescala.backend.repository.RepositoryException;

/**
 * Executa varias escritas na mesma transacao: ou todas valem, ou nenhuma
 * vale (OWASP A10 — falha fechada).
 *
 * Sem isto nao ha como duas chamadas de repositorio compartilharem uma
 * transacao, porque cada metodo abre a propria Connection e o H2 nasce em
 * autocommit: a primeira escrita ja estaria gravada quando a segunda
 * falhasse, deixando o banco pela metade. Aqui a Connection e aberta uma
 * unica vez e repassada ao bloco, que a usa nas sobrecargas de
 * repositorio que aceitam Connection externa.
 *
 * Regra ao dar errado: rollback e a excecao original sobe. Nada de
 * engolir a falha nem de tentar salvar parte do trabalho.
 */
public final class TransacaoUtil {

    private static final Logger LOG = Logger.getLogger(TransacaoUtil.class.getName());

    private TransacaoUtil() {
        // classe utilitaria: nao deve ser instanciada
    }

    /**
     * Roda a operacao numa transacao. Commit se ela terminar sem excecao;
     * rollback e relancamento se qualquer excecao escapar.
     *
     * RuntimeException (inclusive RepositoryException) sobe como veio.
     * SQLException e checked e nao cabe na assinatura, entao sobe embrulhada
     * em RepositoryException — a original fica como causa, no mesmo padrao
     * que os repositorios ja usam.
     */
    public static <T> T executar(FuncaoTransacional<T> operacao) {
        Connection conexao = null;
        try {
            conexao = ConexaoBanco.getConnection();
            conexao.setAutoCommit(false);

            T resultado = operacao.aplicar(conexao);

            conexao.commit();
            return resultado;

        } catch (SQLException excecao) {
            desfazer(conexao, excecao);
            throw new RepositoryException(
                    "Falha na transacao; nenhuma alteracao foi gravada", excecao);
        } catch (RuntimeException | Error excecao) {
            desfazer(conexao, excecao);
            throw excecao;
        } finally {
            // Roda mesmo se o rollback acima tiver falhado.
            restaurarAutoCommit(conexao);
            fechar(conexao);
        }
    }

    /**
     * Desfaz o que a transacao tinha escrito. Se o proprio rollback falhar,
     * a falha entra como supressed na excecao que causou tudo: o motivo
     * original continua sendo o que chega em quem chamou.
     */
    private static void desfazer(Connection conexao, Throwable causa) {
        if (conexao == null) {
            return; // falhou antes de abrir a conexao: nao ha o que desfazer
        }
        try {
            conexao.rollback();
        } catch (SQLException falhaNoRollback) {
            LOG.log(Level.SEVERE, "Falha ao desfazer a transacao", falhaNoRollback);
            causa.addSuppressed(falhaNoRollback);
        }
    }

    private static void restaurarAutoCommit(Connection conexao) {
        if (conexao == null) {
            return;
        }
        try {
            conexao.setAutoCommit(true);
        } catch (SQLException falha) {
            LOG.log(Level.WARNING, "Falha ao restaurar o autocommit da conexao", falha);
        }
    }

    private static void fechar(Connection conexao) {
        if (conexao == null) {
            return;
        }
        try {
            conexao.close();
        } catch (SQLException falha) {
            LOG.log(Level.WARNING, "Falha ao fechar a conexao da transacao", falha);
        }
    }
}
