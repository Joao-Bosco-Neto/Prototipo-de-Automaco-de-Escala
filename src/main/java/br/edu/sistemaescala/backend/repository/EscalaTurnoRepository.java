package br.edu.sistemaescala.backend.repository;

import java.sql.Connection;
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

    /**
     * Mesmo salvamento, porem numa Connection recebida de fora — e assim que
     * o turno participa de uma transacao aberta pelo TransacaoUtil, junto com
     * as alocacoes que o gerador de rodizio cria para ele (issue #43).
     *
     * A conexao continua sendo de quem chamou: este metodo nao faz commit,
     * rollback nem close.
     */
    EscalaTurno salvar(EscalaTurno turno, Connection conexao);

    /** Remove todos os turnos com inicio no mes informado ("Limpar mes"), em cascata sobre as coberturas. */
    void removerPorMes(YearMonth mes);

    /**
     * Mesma remocao, porem numa Connection recebida de fora. Sobrescrever um
     * mes ja gerado precisa apagar o antigo e gravar o novo na mesma
     * transacao, senao uma falha no meio deixaria o mes vazio.
     */
    void removerPorMes(YearMonth mes, Connection conexao);
}
