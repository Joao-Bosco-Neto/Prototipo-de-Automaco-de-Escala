package br.edu.sistemaescala.backend.repository.jdbc;

import br.edu.sistemaescala.backend.dao.ConexaoBanco;
import br.edu.sistemaescala.backend.model.LancamentoHoras;
import br.edu.sistemaescala.backend.model.TipoLancamento;
import br.edu.sistemaescala.backend.repository.LancamentoHorasRepository;
import br.edu.sistemaescala.backend.repository.RepositoryException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Implementacao JDBC de {@link LancamentoHorasRepository}, sempre com PreparedStatement. */
public class LancamentoHorasRepositoryJdbc implements LancamentoHorasRepository {

    private static final String SQL_INSERIR = """
            INSERT INTO lancamento_horas
                (funcionario_id, escala_funcionario_id, data_referencia, minutos, tipo, descricao)
            VALUES (?, ?, ?, ?, ?, ?)
            """;

    private static final String SQL_REMOVER_POR_ESCALA_FUNCIONARIO = """
            DELETE FROM lancamento_horas WHERE escala_funcionario_id = ?
            """;

    private static final String SQL_EXTRATO = """
            SELECT id, funcionario_id, escala_funcionario_id, data_referencia,
                   minutos, tipo, descricao, criado_em
            FROM lancamento_horas
            WHERE funcionario_id = ?
              AND data_referencia BETWEEN ? AND ?
            ORDER BY data_referencia ASC, id ASC
            """;

    private static final String SQL_SOMAR_SALDO = """
            SELECT COALESCE(SUM(minutos), 0) AS saldo
            FROM lancamento_horas
            WHERE funcionario_id = ?
              AND data_referencia BETWEEN ? AND ?
            """;

    @Override
    public LancamentoHoras salvar(LancamentoHoras lancamento) {
        try (Connection conexao = ConexaoBanco.getConnection();
             PreparedStatement stmt = conexao.prepareStatement(SQL_INSERIR, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setInt(1, lancamento.getFuncionarioId());
            setNullableInt(stmt, 2, lancamento.getEscalaFuncionarioId());
            stmt.setObject(3, lancamento.getDataReferencia());
            stmt.setInt(4, lancamento.getMinutos());
            stmt.setString(5, lancamento.getTipo().getValorBanco());
            stmt.setString(6, lancamento.getDescricao());

            stmt.executeUpdate();

            try (ResultSet chaves = stmt.getGeneratedKeys()) {
                if (chaves.next()) {
                    lancamento.setId(chaves.getInt(1));
                }
            }
            return lancamento;

        } catch (SQLException e) {
            throw new RepositoryException("Falha ao inserir lancamento de banco de horas", e);
        }
    }

    @Override
    public int removerPorEscalaFuncionarioId(int escalaFuncionarioId) {
        try (Connection conexao = ConexaoBanco.getConnection();
             PreparedStatement stmt = conexao.prepareStatement(SQL_REMOVER_POR_ESCALA_FUNCIONARIO)) {

            stmt.setInt(1, escalaFuncionarioId);
            return stmt.executeUpdate();

        } catch (SQLException e) {
            throw new RepositoryException(
                    "Falha ao estornar lancamentos da escala_funcionario " + escalaFuncionarioId, e);
        }
    }

    @Override
    public List<LancamentoHoras> buscarExtrato(int funcionarioId, LocalDate inicio, LocalDate fim) {
        try (Connection conexao = ConexaoBanco.getConnection();
             PreparedStatement stmt = conexao.prepareStatement(SQL_EXTRATO)) {

            stmt.setInt(1, funcionarioId);
            stmt.setObject(2, inicio);
            stmt.setObject(3, fim);

            List<LancamentoHoras> extrato = new ArrayList<>();
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    extrato.add(mapear(rs));
                }
            }
            return extrato;

        } catch (SQLException e) {
            throw new RepositoryException(
                    "Falha ao buscar extrato do funcionario " + funcionarioId, e);
        }
    }

    @Override
    public int somarSaldoMinutos(int funcionarioId, LocalDate inicio, LocalDate fim) {
        try (Connection conexao = ConexaoBanco.getConnection();
             PreparedStatement stmt = conexao.prepareStatement(SQL_SOMAR_SALDO)) {

            stmt.setInt(1, funcionarioId);
            stmt.setObject(2, inicio);
            stmt.setObject(3, fim);

            try (ResultSet rs = stmt.executeQuery()) {
                rs.next();
                return rs.getInt("saldo");
            }

        } catch (SQLException e) {
            throw new RepositoryException(
                    "Falha ao somar saldo do funcionario " + funcionarioId, e);
        }
    }

    private void setNullableInt(PreparedStatement stmt, int indice, Integer valor) throws SQLException {
        if (valor != null) {
            stmt.setInt(indice, valor);
        } else {
            stmt.setNull(indice, Types.INTEGER);
        }
    }

    private LancamentoHoras mapear(ResultSet rs) throws SQLException {
        LancamentoHoras lancamento = new LancamentoHoras();
        lancamento.setId(rs.getInt("id"));
        lancamento.setFuncionarioId(rs.getInt("funcionario_id"));

        int escalaFuncionarioId = rs.getInt("escala_funcionario_id");
        lancamento.setEscalaFuncionarioId(rs.wasNull() ? null : escalaFuncionarioId);

        lancamento.setDataReferencia(rs.getObject("data_referencia", LocalDate.class));
        lancamento.setMinutos(rs.getInt("minutos"));
        lancamento.setTipo(TipoLancamento.fromValorBanco(rs.getString("tipo")));
        lancamento.setDescricao(rs.getString("descricao"));
        lancamento.setCriadoEm(rs.getObject("criado_em", LocalDateTime.class));

        return lancamento;
    }
}
