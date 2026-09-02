package br.edu.sistemaescala.backend.repository;

import java.sql.Connection;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import br.edu.sistemaescala.backend.model.EscalaFuncionario;
import br.edu.sistemaescala.backend.service.CoberturaListagemItem;

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

    /**
     * Atualiza uma alocacao ja existente numa Connection recebida de fora — e
     * assim que a edicao de uma cobertura (issue #33) entra na mesma transacao
     * que refaz os lancamentos de banco de horas.
     *
     * A conexao continua sendo de quem chamou: este metodo nao faz commit,
     * rollback nem close.
     */
    EscalaFuncionario atualizar(EscalaFuncionario escalaFuncionario, Connection conexao);

    void remover(int id);

    /**
     * Mesma remocao, porem numa Connection recebida de fora — a exclusao de uma
     * cobertura (issue #33) roda em transacao, e o par de lancamentos vinculado
     * sai junto pela cascata do schema.
     *
     * A conexao continua sendo de quem chamou: este metodo nao faz commit,
     * rollback nem close.
     */
    void remover(int id, Connection conexao);

    /** Coberturas (cobertura_de preenchido) de turnos com inicio no mes informado. */
    List<EscalaFuncionario> buscarCoberturasDoMes(YearMonth mes);

    /**
     * Mesmas coberturas do mes, porem prontas para a tabela da tela de
     * Coberturas: cada linha ja traz o nome do ausente, o nome de quem cobriu,
     * a descricao do motivo e a duracao do turno.
     *
     * Existe separada de {@link #buscarCoberturasDoMes} porque resolve esses
     * quatro dados com JOINs no mesmo SELECT — a alternativa seria uma consulta
     * por linha exibida (N+1) para descobrir quem era o titular e qual era o
     * motivo.
     */
    List<CoberturaListagemItem> listarCoberturasParaListagem(YearMonth mes);

    /**
     * Quantas coberturas ({@code cobertura_de} preenchido) existem em turnos
     * que comecam no mes informado — o card "Coberturas no mes" do dashboard
     * (issue #53).
     *
     * O recorte e o mesmo de {@link #buscarCoberturasDoMes}: o mes vem do
     * {@code inicio} do turno, nao da data em que a cobertura foi registrada.
     * A contagem sai de um COUNT no banco, sem trazer as linhas para a
     * memoria.
     */
    int contarCoberturasDoMes(YearMonth mes);
}
