package br.edu.sistemaescala.backend.repository;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import br.edu.sistemaescala.backend.model.EscalaTurno;

/**
 * Repositorio de turnos concretos no calendario (issue #11).
 *
 * {@link #buscarPorPeriodo} e a consulta mais importante do sistema: alimenta
 * o calendario, o PDF e o dashboard. Ela traz cada {@link EscalaTurno} com o
 * {@code tipoTurno} e os {@code agentes} (com suas coberturas) ja hidratados
 * a partir de um unico JOIN, para nao cair em N+1 consultas.
 */
public interface EscalaTurnoRepository {

    /** Turnos com inicio no intervalo [inicio, fim), com tipoTurno e agentes/coberturas ja carregados. */
    List<EscalaTurno> buscarPorPeriodo(LocalDateTime inicio, LocalDateTime fim);

    Optional<EscalaTurno> buscarPorId(int id);

    /** Insere (id nulo) ou atualiza (id preenchido) o turno e retorna a mesma instancia. */
    EscalaTurno salvar(EscalaTurno turno);

    /** Remove todos os turnos com inicio no mes informado ("Limpar mes"), em cascata sobre as coberturas. */
    void removerPorMes(YearMonth mes);
}
