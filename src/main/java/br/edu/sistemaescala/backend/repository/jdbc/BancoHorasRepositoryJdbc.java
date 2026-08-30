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
 * sobre {@code escala_funcionario} e o saldo é o somatório incondicional de
 * {@code lancamento_horas} — este último de propósito fora do recorte de mês,
 * porque o saldo é um valor contínuo.</p>
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
                ) AS saldo_minutos
            FROM funcionario f
            WHERE f.ativo = TRUE
            ORDER BY f.nome, f.id
            """;

    @Override
    public List<BancoHorasListagemItem> listarMensal(YearMonth mesReferencia) {
        YearMonth mes = mesReferencia != null ? mesReferencia : YearMonth.now();
        LocalDate inicioMes = mes.atDay(1);
        LocalDate inicioProximoMes = mes.plusMonths(1).atDay(1);

        try (Connection conexao = ConexaoBanco.getConnection();
             PreparedStatement stmt = conexao.prepareStatement(SQL_LISTAR_MENSAL)) {

            // Os três pares de parâmetros repetem o mesmo recorte de mês.
            for (int par = 0; par < 3; par++) {
                stmt.setObject(par * 2 + 1, inicioMes.atStartOfDay());
                stmt.setObject(par * 2 + 2, inicioProximoMes.atStartOfDay());
            }

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
            throw new RepositoryException("Falha ao listar o banco de horas do mês " + mes, e);
        }
    }
}
