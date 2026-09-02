package br.edu.sistemaescala.backend.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * O conteúdo da tela "Visão geral", reunido num resultado só para a tela fazer
 * uma chamada em vez de várias: os quatro indicadores da issue #53, a faixa da
 * semana e os próximos plantões da issue #54 e as pendências da issue #55.
 *
 * <p>Os quatro indicadores saem de consultas agregadas — nenhuma listagem é
 * trazida para a memória só para ser medida. A semana e os próximos plantões,
 * ao contrário, precisam dos agentes de cada turno para mostrar nome e efetivo:
 * os dois saem de uma única {@code buscarPorPeriodo} cobrindo o intervalo
 * inteiro, e não de uma consulta por dia.</p>
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
 * @param semanaCorrente     os sete dias da semana que contém
 *                           {@code diaReferencia}, de domingo a sábado, sempre
 *                           com sete itens mesmo que alguns não tenham turno
 * @param proximosPlantoes   turnos a partir de {@code diaReferencia}, em ordem
 *                           de início e limitados a um resumo — não é a agenda
 *                           inteira, que vive na montagem da escala
 * @param alertas            pendências levantadas do estado do banco, da mais
 *                           grave para a menos grave; vazia quando não há
 *                           nenhuma, e é essa lista vazia que a tela traduz na
 *                           mensagem de "nada pendente"
 */
public record IndicadoresDashboard(
        LocalDate diaReferencia,
        YearMonth mesReferencia,
        List<PlantaoDoDiaItem> plantoesDeHoje,
        ContagemFuncionarios funcionarios,
        int coberturasNoMes,
        int diasIncompletos,
        List<DiaDaSemana> semanaCorrente,
        List<TurnoResumido> proximosPlantoes,
        List<AlertaDashboard> alertas) {

    /** Não há nenhum turno começando hoje — o card mostra o estado vazio. */
    public boolean semPlantaoHoje() {
        return plantoesDeHoje.isEmpty();
    }

    /**
     * Nenhum dos sete dias tem turno — mês sem escala montada, e a faixa
     * mostra o estado vazio em vez de sete colunas em branco sem explicação.
     */
    public boolean semanaSemEscala() {
        return semanaCorrente.stream().allMatch(DiaDaSemana::semTurnos);
    }

    /** Não há plantão nenhum daqui para a frente dentro do horizonte consultado. */
    public boolean semProximosPlantoes() {
        return proximosPlantoes.isEmpty();
    }

    /** Nenhuma verificação encontrou problema — o painel mostra o estado vazio. */
    public boolean semPendencias() {
        return alertas.isEmpty();
    }
}
