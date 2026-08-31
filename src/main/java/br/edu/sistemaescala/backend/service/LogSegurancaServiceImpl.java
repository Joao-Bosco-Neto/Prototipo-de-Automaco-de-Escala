package br.edu.sistemaescala.backend.service;

import java.time.LocalDateTime;

import br.edu.sistemaescala.LogAplicacao;
import br.edu.sistemaescala.backend.model.AcaoSeguranca;
import br.edu.sistemaescala.backend.model.LogSeguranca;
import br.edu.sistemaescala.backend.model.ResultadoSeguranca;
import br.edu.sistemaescala.backend.model.Usuario;
import br.edu.sistemaescala.backend.repository.LogSegurancaRepository;
import br.edu.sistemaescala.backend.repository.RepositoryException;
import br.edu.sistemaescala.backend.repository.jdbc.LogSegurancaRepositoryJdbc;

/**
 * Implementacao do registro de eventos de seguranca (issue #64).
 *
 * <p>Tres responsabilidades, todas antes de tocar o banco:</p>
 * <ul>
 *   <li><b>Sanitizacao (CWE-117).</b> A identificacao de um login falho e
 *       entrada livre do usuario. Sem limpar {@code \r} e {@code \n} dela,
 *       quem tentasse logar como {@code "fulano\nlogin sucesso admin"}
 *       forjaria uma segunda linha na trilha.</li>
 *   <li><b>Truncamento.</b> As colunas tem limite fixo no schema; entrada
 *       maior que o limite abortaria o INSERT — e a auditoria nao pode
 *       depender do tamanho do que o atacante digitou.</li>
 *   <li><b>Falha fechada para a auditoria, aberta para o fluxo.</b> Se o
 *       banco recusar a gravacao, o erro vai para o log de aplicacao e a
 *       operacao auditada continua: ninguem fica sem conseguir sair do
 *       sistema porque a trilha falhou.</li>
 * </ul>
 *
 * <p>A data/hora vem de {@link LocalDateTime#now()}, o padrao de
 * {@code java.time} usado no resto do projeto, e nao do
 * {@code CURRENT_TIMESTAMP} do banco.</p>
 */
public class LogSegurancaServiceImpl implements LogSegurancaService {

    /** Espelham os limites de log_seguranca no schema.sql. */
    private static final int LIMITE_IDENTIFICACAO = 150;
    private static final int LIMITE_DETALHES = 500;

    /** Quebras de linha em todas as formas que o Java reconhece como tal. */
    private static final String QUEBRAS_DE_LINHA = "[\r\n\u0085\u2028\u2029]";

    private static final String IDENTIFICACAO_DESCONHECIDA = "(desconhecido)";

    private final LogSegurancaRepository logSegurancaRepository;
    private final SessaoUsuario sessaoUsuario;

    public LogSegurancaServiceImpl() {
        this(new LogSegurancaRepositoryJdbc(), null);
    }

    public LogSegurancaServiceImpl(SessaoUsuario sessaoUsuario) {
        this(new LogSegurancaRepositoryJdbc(), sessaoUsuario);
    }

    public LogSegurancaServiceImpl(LogSegurancaRepository logSegurancaRepository, SessaoUsuario sessaoUsuario) {
        this.logSegurancaRepository = logSegurancaRepository;
        this.sessaoUsuario = sessaoUsuario;
    }

    @Override
    public void registrar(AcaoSeguranca acao, String identificacao, ResultadoSeguranca resultado, String detalhes) {
        if (acao == null) {
            return;
        }
        LogSeguranca evento = new LogSeguranca();
        evento.setDataHora(LocalDateTime.now());
        evento.setIdentificacao(sanitizar(resolverIdentificacao(identificacao), LIMITE_IDENTIFICACAO));
        evento.setAcao(acao);
        evento.setResultado(resultado);
        evento.setDetalhes(sanitizar(detalhes, LIMITE_DETALHES));

        try {
            logSegurancaRepository.inserir(evento);
        } catch (RepositoryException excecao) {
            // Catch com acao, nao vazio (issue #63): a trilha perdeu o evento,
            // mas isso precisa aparecer em algum lugar. So a acao vai para a
            // mensagem — a identificacao e entrada do usuario.
            LogAplicacao.registrarErro(
                    "Nao foi possivel gravar o evento de seguranca '" + acao.valor() + "'", excecao);
        }
    }

    /**
     * Identificacao explicita quando ha uma (login e logout sabem de quem
     * estao falando); senao, o login do usuario da sessao.
     */
    private String resolverIdentificacao(String identificacao) {
        if (identificacao != null && !identificacao.isBlank()) {
            return identificacao;
        }
        if (sessaoUsuario == null) {
            return IDENTIFICACAO_DESCONHECIDA;
        }
        return sessaoUsuario.usuarioAtual()
                .map(Usuario::getLogin)
                .filter(login -> !login.isBlank())
                .orElse(IDENTIFICACAO_DESCONHECIDA);
    }

    /** Tira as quebras de linha (CWE-117) e corta no limite da coluna. */
    private String sanitizar(String texto, int limite) {
        if (texto == null) {
            return null;
        }
        String limpo = texto.replaceAll(QUEBRAS_DE_LINHA, " ").trim();
        if (limpo.isEmpty()) {
            return limpo;
        }
        return limpo.length() <= limite ? limpo : limpo.substring(0, limite);
    }
}
