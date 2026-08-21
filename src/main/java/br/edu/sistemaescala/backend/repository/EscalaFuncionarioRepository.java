package br.edu.sistemaescala.backend.repository;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import br.edu.sistemaescala.backend.model.EscalaFuncionario;

/**
 * Repositorio de alocacoes de funcionarios em turnos (issue #11).
 */
public interface EscalaFuncionarioRepository {

    /** Agentes alocados num turno especifico, com o funcionario ja hidratado. */
    List<EscalaFuncionario> listarPorTurno(int escalaTurnoId);

    /**
     * Plantoes de um funcionario cujo turno comeca no intervalo [inicio, fim) —
     * base da regra de descanso entre turnos.
     */
    List<EscalaFuncionario> listarPorFuncionario(int funcionarioId, LocalDateTime inicio, LocalDateTime fim);

    /** Insere uma alocacao e retorna a mesma instancia com o id gerado preenchido. */
    EscalaFuncionario inserir(EscalaFuncionario escalaFuncionario);

    void remover(int id);

    /** Coberturas (cobertura_de preenchido) de turnos com inicio no mes informado. */
    List<EscalaFuncionario> buscarCoberturasDoMes(YearMonth mes);
}
