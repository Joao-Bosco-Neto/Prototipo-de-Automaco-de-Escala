package br.edu.sistemaescala.backend.service;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Consumer;

/**
 * Serviço responsável pelo controle de inatividade e bloqueio da sessão do usuário (Issue #61 / OWASP A07).
 *
 * Conforme OWASP A07 (Cenário #3), estações de trabalho compartilhadas exigem
 * bloqueio automático por inatividade para impedir acesso indevido quando a
 * aplicação for deixada aberta, garantindo a preservação dos dados não salvos.
 */
public interface BloqueioInatividadeService {

    /**
     * Duração padrão de inatividade para bloqueio automático (15 minutos / 900 segundos).
     */
    Duration TEMPO_PADRAO_INATIVIDADE = Duration.ofMinutes(15);

    /**
     * Registra interação do usuário, renovando o marco de atividade se não estiver bloqueado.
     */
    void registrarAtividade();

    /**
     * Retorna se a sessão está atualmente bloqueada.
     */
    boolean estaBloqueado();

    /**
     * Aciona o bloqueio da sessão (manual ou automático) e registra evento em log.
     */
    void bloquear();

    /**
     * Tenta desbloquear a sessão com a senha do usuário atualmente autenticado.
     *
     * @param senha Senha informada pelo usuário
     * @return {@code true} se a senha estiver correta e a sessão for desbloqueada; {@code false} caso contrário.
     */
    boolean desbloquear(String senha);

    /**
     * Verifica se o tempo de inatividade limite foi atingido e bloqueia a sessão se necessário.
     */
    void verificarInatividade();

    /**
     * Verifica se o tempo de inatividade limite foi atingido em relação ao instante fornecido.
     *
     * @param agora Instante atual de referência
     */
    void verificarInatividade(Instant agora);

    /**
     * Retorna o tempo limite de inatividade configurado.
     */
    Duration getTempoInatividade();

    /**
     * Configura o tempo limite de inatividade.
     *
     * @param tempoInatividade Duração limite (deve ser positiva)
     */
    void setTempoInatividade(Duration tempoInatividade);

    /**
     * Retorna a duração restante até o próximo bloqueio por inatividade.
     */
    Duration getTempoRestante();

    /**
     * Retorna a duração restante até o próximo bloqueio por inatividade em relação ao instante fornecido.
     *
     * @param agora Instante atual de referência
     */
    Duration getTempoRestante(Instant agora);

    /**
     * Adiciona um ouvinte de notificação para alterações do estado de bloqueio.
     *
     * @param ouvinte Consumidor recebendo {@code true} quando bloqueia e {@code false} quando desbloqueia
     */
    void adicionarOuvinteBloqueio(Consumer<Boolean> ouvinte);

    /**
     * Remove um ouvinte de notificação.
     *
     * @param ouvinte Ouvinte a ser removido
     */
    void removerOuvinteBloqueio(Consumer<Boolean> ouvinte);
}

