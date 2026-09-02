package br.edu.sistemaescala.backend.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * Os quatro indicadores da tela "Visão geral" (issue #53), reunidos num
 * resultado só para a tela fazer uma chamada em vez de quatro.
 *
 * <p>Todos os campos saem de consultas agregadas — nenhuma listagem é trazida
 * para a memória só para ser medida.</p>
 *
 * @param diaReferencia      dia usado como "hoje"
 * @param mesReferencia      mês de {@code diaReferencia}, recorte dos dois
 *                           indicadores mensais
 * @param plantoesDeHoje     turnos que começam em {@code diaReferencia}; vazio
 *                           quando não há plantão no dia, e é essa lista vazia
 *                           que a tela traduz no estado explícito do card
 * @param funcionarios       quantos funcionários estão ativos e quantos estão
 *                           inativos
 * @param coberturasNoMes    coberturas registradas em turnos que começam no mês
 * @param diasIncompletos    dias do mês com pelo menos um turno abaixo do
 *                           mínimo de agentes
 */
public record IndicadoresDashboard(
        LocalDate diaReferencia,
        YearMonth mesReferencia,
        List<PlantaoDoDiaItem> plantoesDeHoje,
        ContagemFuncionarios funcionarios,
        int coberturasNoMes,
        int diasIncompletos) {

    /** Não há nenhum turno começando hoje — o card mostra o estado vazio. */
    public boolean semPlantaoHoje() {
        return plantoesDeHoje.isEmpty();
    }
}
