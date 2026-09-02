package br.edu.sistemaescala.backend.service;

import java.time.LocalDateTime;

/**
 * Um turno de um dia, resumido para o card "Plantão de hoje" do dashboard
 * (issue #53).
 *
 * <p>O card mostra o <b>tipo de turno</b>, e não a equipe: o rodízio por
 * equipes fixas foi substituído por {@code tipo_turno} e não existe campo de
 * equipe no modelo.</p>
 *
 * <p>Um dia pode render mais de um item — em 12x36 o diurno e o noturno
 * começam na mesma data.</p>
 *
 * @param escalaTurnoId  id do turno, para quem precisar navegar até ele
 * @param tipoTurno      nome do tipo de turno (ex.: "Turno Noturno 12h")
 * @param inicio         início do turno
 * @param fim            fim do turno (pode cair no dia seguinte)
 * @param agentes        postos de fato ocupados, contando a substituição como
 *                       1-para-1 — mesma regra do efetivo do calendário
 * @param minimoExigido  mínimo de agentes do turno, para o card sinalizar
 *                       quando o plantão de hoje está incompleto
 */
public record PlantaoDoDiaItem(
        int escalaTurnoId,
        String tipoTurno,
        LocalDateTime inicio,
        LocalDateTime fim,
        int agentes,
        int minimoExigido) {

    /** Espelha {@link ResultadoEfetivo#completo()} para este turno. */
    public boolean completo() {
        return agentes >= minimoExigido;
    }
}
