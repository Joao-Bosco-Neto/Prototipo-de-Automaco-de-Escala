package br.edu.sistemaescala.frontend;

import java.sql.SQLException;

import br.edu.sistemaescala.LogAplicacao;
import br.edu.sistemaescala.backend.repository.RepositoryException;
import javafx.application.Platform;

/**
 * Captura as excecoes que ninguem tratou e transforma em log + dialogo
 * amigavel, no lugar de deixar a janela travada e o stack trace cru no
 * console.
 *
 * Sao dois registros diferentes, porque um so nao cobre tudo:
 *
 *  - instalarGlobal(), chamado antes do Application.launch(), cobre a
 *    thread main, a thread do launcher (onde roda o init()) e qualquer
 *    thread de servico criada depois;
 *  - instalarNaThreadAtual(), chamado de dentro do start(), cobre a
 *    JavaFX Application Thread. Ela e criada internamente pelo framework
 *    e ja nasce com um handler proprio, entao o default nao vale para
 *    ela — e e justamente nela que rodam os handlers de botao.
 */
public final class TratadorErroGlobal {

    private static final String MENSAGEM_GENERICA =
            "Ocorreu um erro inesperado e a operação não pôde ser concluída.\n\n"
            + "A aplicação continua aberta. Se o problema se repetir, envie o "
            + "arquivo de log ao administrador do sistema:\n";

    /** Evita cascata: um erro ao exibir o dialogo nao pode chamar o handler de novo. */
    private static volatile boolean exibindoDialogo;

    private TratadorErroGlobal() {
        // classe utilitaria: nao deve ser instanciada
    }

    /** Registra o handler padrao. Chamar no main(), antes do launch(). */
    public static void instalarGlobal() {
        Thread.setDefaultUncaughtExceptionHandler(TratadorErroGlobal::tratar);
    }

    /**
     * Registra o handler na thread corrente. Chamar de dentro do start(),
     * para cobrir a JavaFX Application Thread.
     */
    public static void instalarNaThreadAtual() {
        Thread.currentThread().setUncaughtExceptionHandler(TratadorErroGlobal::tratar);
    }

    /** Trata uma excecao ja capturada, com o mesmo caminho do handler global. */
    public static void tratar(Thread thread, Throwable erro) {
        LogAplicacao.registrarErro(
                "Excecao nao tratada na thread \"" + thread.getName() + "\"", erro);
        exibir(erro);
    }

    /**
     * Diz se a falha veio do acesso a dados. Olha a cadeia inteira porque
     * o RepositoryException sempre encadeia a SQLException original, e as
     * camadas de cima costumam reembrulhar tudo mais uma vez.
     */
    public static boolean ehFalhaDeAcessoAoBanco(Throwable erro) {
        for (Throwable atual = erro; atual != null; atual = atual.getCause()) {
            if (atual instanceof RepositoryException || atual instanceof SQLException) {
                return true;
            }
            if (atual.getCause() == atual) {
                break; // causa circular: para de andar na cadeia
            }
        }
        return false;
    }

    private static void exibir(Throwable erro) {
        Runnable exibicao = () -> {
            if (exibindoDialogo) {
                return;
            }
            exibindoDialogo = true;
            try {
                if (ehFalhaDeAcessoAoBanco(erro)) {
                    DialogUtil.mostrarErroBancoIndisponivel();
                } else {
                    DialogUtil.mostrarErro("Erro inesperado",
                            MENSAGEM_GENERICA + LogAplicacao.arquivoLog());
                }
            } catch (RuntimeException falhaAoExibir) {
                LogAplicacao.registrarErro("Falha ao exibir o dialogo de erro", falhaAoExibir);
            } finally {
                exibindoDialogo = false;
            }
        };

        if (Platform.isFxApplicationThread()) {
            exibicao.run();
            return;
        }
        try {
            Platform.runLater(exibicao);
        } catch (IllegalStateException toolkitIndisponivel) {
            // Erro antes de a interface subir (ou depois de fechada): so o log resta.
            LogAplicacao.registrarErro(
                    "Interface indisponivel para exibir o erro", toolkitIndisponivel);
        }
    }
}
