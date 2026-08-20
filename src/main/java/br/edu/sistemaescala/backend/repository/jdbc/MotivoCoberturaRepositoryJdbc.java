package br.edu.sistemaescala.backend.repository.jdbc;

import br.edu.sistemaescala.backend.dao.ConexaoBanco;
import br.edu.sistemaescala.backend.model.MotivoCobertura;
import br.edu.sistemaescala.backend.repository.MotivoCoberturaRepository;
import br.edu.sistemaescala.backend.repository.RepositoryException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class MotivoCoberturaRepositoryJdbc implements MotivoCoberturaRepository {

    private static final String SQL_CAMPOS = """
            SELECT id, nome, gera_lancamento, ativo
            FROM motivo_cobertura
            """;
    private static final String SQL_BUSCAR_POR_ID = SQL_CAMPOS + " WHERE id = ?";
    private static final String SQL_INSERIR = """
            INSERT INTO motivo_cobertura (nome, gera_lancamento, ativo)
            VALUES (?, ?, ?)
            """;
    private static final String SQL_ATUALIZAR = """
            UPDATE motivo_cobertura
            SET nome = ?, gera_lancamento = ?, ativo = ?
            WHERE id = ?
            """;
    private static final String SQL_ATIVAR = "UPDATE motivo_cobertura SET ativo = TRUE WHERE id = ?";
    private static final String SQL_DESATIVAR = "UPDATE motivo_cobertura SET ativo = FALSE WHERE id = ?";

    @Override
    public List<MotivoCobertura> listar(Boolean ativo) {
        String sql = ativo == null
                ? SQL_CAMPOS + " ORDER BY nome, id"
                : SQL_CAMPOS + " WHERE ativo = ? ORDER BY nome, id";

        try (Connection conexao = ConexaoBanco.getConnection();
             PreparedStatement stmt = conexao.prepareStatement(sql)) {
            if (ativo != null) {
                stmt.setBoolean(1, ativo);
            }
            List<MotivoCobertura> motivos = new ArrayList<>();
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    motivos.add(mapear(rs));
                }
            }
            return motivos;
        } catch (SQLException e) {
            throw new RepositoryException("Falha ao listar motivos de cobertura", e);
        }
    }

    @Override
    public Optional<MotivoCobertura> buscarPorId(int id) {
        try (Connection conexao = ConexaoBanco.getConnection();
             PreparedStatement stmt = conexao.prepareStatement(SQL_BUSCAR_POR_ID)) {
            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? Optional.of(mapear(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RepositoryException("Falha ao buscar motivo de cobertura " + id, e);
        }
    }

    @Override
    public MotivoCobertura inserir(MotivoCobertura motivo) {
        try (Connection conexao = ConexaoBanco.getConnection();
             PreparedStatement stmt = conexao.prepareStatement(SQL_INSERIR, Statement.RETURN_GENERATED_KEYS)) {
            preencher(stmt, motivo);
            stmt.executeUpdate();
            try (ResultSet chaves = stmt.getGeneratedKeys()) {
                if (!chaves.next()) {
                    throw new RepositoryException("Banco nao retornou o id do motivo inserido", null);
                }
                motivo.setId(chaves.getInt(1));
            }
            return motivo;
        } catch (SQLException e) {
            throw new RepositoryException("Falha ao inserir motivo de cobertura", e);
        }
    }

    @Override
    public MotivoCobertura atualizar(MotivoCobertura motivo) {
        try (Connection conexao = ConexaoBanco.getConnection();
             PreparedStatement stmt = conexao.prepareStatement(SQL_ATUALIZAR)) {
            preencher(stmt, motivo);
            stmt.setInt(4, motivo.getId());
            if (stmt.executeUpdate() == 0) {
                throw new RepositoryException("Nenhum motivo de cobertura encontrado para atualizar", null);
            }
            return motivo;
        } catch (SQLException e) {
            throw new RepositoryException("Falha ao atualizar motivo de cobertura", e);
        }
    }

    @Override
    public void ativar(int id) {
        executarAtualizacao(SQL_ATIVAR, "Falha ao ativar motivo de cobertura", id);
    }

    @Override
    public void desativar(int id) {
        executarAtualizacao(SQL_DESATIVAR, "Falha ao desativar motivo de cobertura", id);
    }

    private void preencher(PreparedStatement stmt, MotivoCobertura motivo) throws SQLException {
        stmt.setString(1, motivo.getNome());
        stmt.setBoolean(2, motivo.isGeraLancamento());
        stmt.setBoolean(3, motivo.isAtivo());
    }

    private void executarAtualizacao(String sql, String mensagem, int id) {
        try (Connection conexao = ConexaoBanco.getConnection();
             PreparedStatement stmt = conexao.prepareStatement(sql)) {
            stmt.setInt(1, id);
            if (stmt.executeUpdate() == 0) {
                throw new RepositoryException("Nenhum motivo de cobertura encontrado para atualizar", null);
            }
        } catch (SQLException e) {
            throw new RepositoryException(mensagem, e);
        }
    }

    private MotivoCobertura mapear(ResultSet rs) throws SQLException {
        MotivoCobertura motivo = new MotivoCobertura();
        motivo.setId(rs.getInt("id"));
        motivo.setNome(rs.getString("nome"));
        motivo.setGeraLancamento(rs.getBoolean("gera_lancamento"));
        motivo.setAtivo(rs.getBoolean("ativo"));
        return motivo;
    }
}