package br.edu.sistemaescala.backend.service;

import java.time.LocalDate;

/**
 * Fonte dos indicadores da tela "Visão geral" (issue #53).
 *
 * <p>Existe para manter a regra fora do controller: a tela pinta os cards, mas
 * não sabe de qual repositório sai cada número, nem qual é o recorte de mês, nem
 * como se conta um posto ocupado.</p>
 */
public interface DashboardService {

    /**
     * Carrega os quatro indicadores de uma vez, tomando {@code diaReferencia}
     * como "hoje" e o mês dele como recorte dos indicadores mensais.
     *
     * <p>Recebe o dia em vez de chamar {@code LocalDate.now()} por dentro para
     * a apuração ser reproduzível — os quatro números saem sempre do mesmo dia,
     * mesmo que a consulta atravesse a meia-noite.</p>
     */
    IndicadoresDashboard carregar(LocalDate diaReferencia);
}
