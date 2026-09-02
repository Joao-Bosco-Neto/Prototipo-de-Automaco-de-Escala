package br.edu.sistemaescala.backend.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Um turno do calendário resumido para a faixa da semana e para a tabela de
 * próximos plantões da tela "Visão geral" (issue #54).
 *
 * <p>Mostra o <b>tipo de turno</b>, e não a equipe: o rodízio por equipes
 * fixas foi substituído por {@code tipo_turno} e não existe campo de equipe no
 * modelo — a mesma correção já registrada em {@link PlantaoDoDiaItem}.</p>
 *
 * <p>O {@code efetivo} vem pronto de
 * {@link RegraEscalaService#verificarEfetivo(br.edu.sistemaescala.backend.model.EscalaTurno, List)},
 * a sobrecarga que trabalha sobre os agentes já carregados. É ele que decide o
 * selo Confirmado/Incompleto — a tela não recalcula nada, e por isso o selo
 * não tem como divergir do estado que o calendário pinta no mesmo dia.</p>
 *
 * @param escalaTurnoId id do turno
 * @param tipoTurno     nome do tipo de turno
 * @param inicio        início do turno
 * @param fim           fim do turno (pode cair no dia seguinte)
 * @param postos        postos ocupados, com a cobertura já casada
 * @param efetivo       resultado da conferência de efetivo deste turno
 */
public record TurnoResumido(
        int escalaTurnoId,
        String tipoTurno,
        LocalDateTime inicio,
        LocalDateTime fim,
        List<PostoDoTurno> postos,
        ResultadoEfetivo efetivo) {

    /** Dia em que o turno começa — é por ele que a faixa da semana agrupa. */
    public LocalDate dia() {
        return inicio.toLocalDate();
    }

    /** Algum posto do turno foi assumido por um substituto. */
    public boolean temCobertura() {
        return postos.stream().anyMatch(PostoDoTurno::coberto);
    }
}
