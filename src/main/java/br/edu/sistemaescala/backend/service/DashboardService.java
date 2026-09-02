package br.edu.sistemaescala.backend.service;

import java.time.LocalDate;

/**
 * Fonte do conteúdo da tela "Visão geral": os indicadores da issue #53 e a
 * faixa da semana com os próximos plantões da issue #54.
 *
 * <p>Existe para manter a regra fora do controller: a tela pinta os cards, mas
 * não sabe de qual repositório sai cada número, nem qual é o recorte de mês, nem
 * como se conta um posto ocupado, nem em que dia a semana começa.</p>
 */
public interface DashboardService {

    /**
     * Carrega tudo de uma vez, tomando {@code diaReferencia} como "hoje", o mês
     * dele como recorte dos indicadores mensais e a semana de domingo a sábado
     * que o contém como recorte da faixa.
     *
     * <p>Recebe o dia em vez de chamar {@code LocalDate.now()} por dentro para
     * a apuração ser reproduzível — os quatro números saem sempre do mesmo dia,
     * mesmo que a consulta atravesse a meia-noite.</p>
     */
    IndicadoresDashboard carregar(LocalDate diaReferencia);
}
