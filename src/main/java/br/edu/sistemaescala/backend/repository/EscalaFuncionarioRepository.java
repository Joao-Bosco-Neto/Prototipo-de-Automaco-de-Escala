package br.edu.sistemaescala.backend.repository;

import java.sql.Connection;
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

    /**
     * Mesma insercao, porem numa Connection recebida de fora — e assim que
     * a alocacao participa de uma transacao aberta pelo TransacaoUtil, junto
     * com outras escritas.
     *
     * A conexao continua sendo de quem chamou: este metodo nao faz commit,
     * rollback nem close.
     */
    EscalaFuncionario inserir(EscalaFuncionario escalaFuncionario, Connection conexao);

    void remover(int id);

    /** Coberturas (cobertura_de preenchido) de turnos com inicio no mes informado. */
    List<EscalaFuncionario> buscarCoberturasDoMes(YearMonth mes);
}
