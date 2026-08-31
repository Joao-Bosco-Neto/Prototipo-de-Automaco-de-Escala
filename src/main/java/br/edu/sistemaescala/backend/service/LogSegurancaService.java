package br.edu.sistemaescala.backend.service;

import br.edu.sistemaescala.backend.model.AcaoSeguranca;
import br.edu.sistemaescala.backend.model.ResultadoSeguranca;

/**
 * Registro de eventos de seguranca na trilha de auditoria (OWASP A09,
 * issue #64).
 *
 * <p>Quem chama nao precisa se preocupar com sanitizacao nem com o tamanho
 * das colunas: a implementacao limpa quebras de linha (CWE-117) e trunca no
 * limite do schema antes de gravar.</p>
 *
 * <p><b>Nada de senha ou hash aqui.</b> {@code detalhes} descreve o que a
 * acao mudou, nunca o segredo envolvido (CWE-532).</p>
 *
 * <p>Registrar auditoria nunca derruba a operacao auditada: se a gravacao
 * falhar, a falha vai para o log de aplicacao e o fluxo segue.</p>
 */
public interface LogSegurancaService {

    /**
     * Grava o evento com a identificacao informada.
     *
     * @param acao          o que aconteceu
     * @param identificacao login de quem agiu; no login falho, a string
     *                      tentada — pode nem existir como usuario.
     *                      {@code null} resolve pelo usuario da sessao
     * @param resultado     desfecho, ou {@code null} quando nao se aplica
     * @param detalhes      texto curto do que mudou, ou {@code null}
     */
    void registrar(AcaoSeguranca acao, String identificacao, ResultadoSeguranca resultado, String detalhes);

    /**
     * Mesma gravacao, com a identificacao resolvida a partir do usuario
     * autenticado na sessao — para as acoes que so acontecem depois do login
     * (gestao de usuarios, exclusoes na escala).
     */
    default void registrar(AcaoSeguranca acao, ResultadoSeguranca resultado, String detalhes) {
        registrar(acao, null, resultado, detalhes);
    }
}
