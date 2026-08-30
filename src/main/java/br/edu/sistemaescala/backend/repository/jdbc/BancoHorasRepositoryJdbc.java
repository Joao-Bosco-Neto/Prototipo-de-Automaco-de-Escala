package br.edu.sistemaescala.backend.repository.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import br.edu.sistemaescala.backend.dao.ConexaoBanco;
import br.edu.sistemaescala.backend.repository.BancoHorasRepository;
import br.edu.sistemaescala.backend.repository.RepositoryException;
import br.edu.sistemaescala.backend.service.BancoHorasListagemItem;

/**
 * Implementação JDBC de {@link BancoHorasRepository}, sempre com PreparedStatement.
 *
 * <p>Uma consulta só: as três métricas do mês são subqueries correlacionadas
 * sobre {@code escala_funcionario} e o saldo é o somatório de
 * {@code lancamento_horas} no mesmo recorte. Quando {@code mesReferencia} é
 * {@code null}, o recorte vira uma janela ampla e tudo soma o histórico
 * completo.</p>
 *
 * <ul>
 *   <li><b>Plantões cumpridos</b>: alocações do mês que não são cobertura
 *       ({@code cobertura_de IS NULL}) e que não foram cobertas por terceiros
 *       ({@code NOT EXISTS} uma alocação apontando para ela).</li>
 *   <li><b>Coberturas feitas</b>: alocações do mês com {@code cobertura_de} preenchido.</li>
 *   <li><b>Plantões cobertos</b>: alocações do mês que têm outra alocação
 *       apontando para elas ({@code EXISTS}) — as ausências dele cobertas por outro.</li>
 * </ul>
 *
 * <p>Apenas funcionários ativos entram na listagem ({@code f.ativo = TRUE}).</p>
 */
public class BancoHorasRepositoryJdbc implements BancoHorasRepository {

    /** Janela ampla usada quando não há mês selecionado (saldo/métricas do histórico). */
    private static final LocalDate INICIO_DOS_TEMPOS = LocalDate.of(2000, 1, 1);
    private static final LocalDate FIM_DOS_TEMPOS = LocalDate.of(2100, 1, 1);

    private static final String SQL_LISTAR_MENSAL = """
            SELECT f.id, f.nome, f.matricula,
                (SELECT COUNT(ef.id)
                   FROM escala_funcionario ef
                   JOIN escala_turno et ON et.id = ef.escala_turno_id
                  WHERE ef.funcionario_id = f.id
                    AND et.inicio >= ? AND et.inicio < ?
                    AND ef.cobertura_de IS NULL
                    AND NOT EXISTS (SELECT 1 FROM escala_funcionario cov
                                     WHERE cov.cobertura_de = ef.id)
                ) AS plantoes_cumpridos,
                (SELECT COUNT(ef.id)
                   FROM escala_funcionario ef
                   JOIN escala_turno et ON et.id = ef.escala_turno_id
                  WHERE ef.funcionario_id = f.id
                    AND et.inicio >= ? AND et.inicio < ?
                    AND ef.cobertura_de IS NOT NULL
                ) AS coberturas_feitas,
                (SELECT COUNT(ef.id)
                   FROM escala_funcionario ef
                   JOIN escala_turno et ON et.id = ef.escala_turno_id
                  WHERE ef.funcionario_id = f.id
                    AND et.inicio >= ? AND et.inicio < ?
                    AND EXISTS (SELECT 1 FROM escala_funcionario cov
                                 WHERE cov.cobertura_de = ef.id)
                ) AS plantoes_cobertos,
                (SELECT COALESCE(SUM(lh.minutos), 0)
                   FROM lancamento_horas lh
                  WHERE lh.funcionario_id = f.id
                    AND lh.data_referencia >= ? AND lh.data_referencia < ?
                ) AS saldo_minutos
            FROM funcionario f
            WHERE f.ativo = TRUE
            ORDER BY f.nome, f.id
            """;

    @Override
    public List<BancoHorasListagemItem> listarMensal(YearMonth mesReferencia) {
        LocalDate inicio = mesReferencia != null ? mesReferencia.atDay(1) : INICIO_DOS_TEMPOS;
        LocalDate fimExclusivo = mesReferencia != null
                ? mesReferencia.plusMonths(1).atDay(1)
                : FIM_DOS_TEMPOS;

        try (Connection conexao = ConexaoBanco.getConnection();
             PreparedStatement stmt = conexao.prepareStatement(SQL_LISTAR_MENSAL)) {

            // Os três primeiros pares (métricas, sobre escala_turno.inicio) repetem
            // o mesmo recorte; o quarto par é o do saldo, sobre data_referencia.
            for (int par = 0; par < 3; par++) {
                stmt.setObject(par * 2 + 1, inicio.atStartOfDay());
                stmt.setObject(par * 2 + 2, fimExclusivo.atStartOfDay());
            }
            stmt.setObject(7, inicio);
            stmt.setObject(8, fimExclusivo);

            List<BancoHorasListagemItem> itens = new ArrayList<>();
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    itens.add(new BancoHorasListagemItem(
                            rs.getInt("id"),
                            rs.getString("nome"),
                            rs.getString("matricula"),
                            rs.getInt("plantoes_cumpridos"),
                            rs.getInt("coberturas_feitas"),
                            rs.getInt("plantoes_cobertos"),
                            rs.getLong("saldo_minutos")));
                }
            }
            return itens;

        } catch (SQLException e) {
            throw new RepositoryException("Falha ao listar o banco de horas "
                    + (mesReferencia != null ? "do mês " + mesReferencia : "(histórico completo)"), e);
        }
    }
}
