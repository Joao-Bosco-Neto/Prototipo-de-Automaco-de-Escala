package br.edu.sistemaescala.backend.repository.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import br.edu.sistemaescala.backend.dao.ConexaoBanco;
import br.edu.sistemaescala.backend.model.TipoTurno;
import br.edu.sistemaescala.backend.repository.RepositoryException;
import br.edu.sistemaescala.backend.repository.TipoTurnoRepository;

public class TipoTurnoRepositoryJdbc implements TipoTurnoRepository {

    private static final String SQL_CAMPOS = """
            SELECT id, nome, hora_inicio, duracao_horas, intervalo_descanso_horas,
                   min_agentes, max_agentes, conta_banco_horas, ativo, criado_em
            FROM tipo_turno
            """;

    private static final String SQL_BUSCAR_POR_ID = SQL_CAMPOS + " WHERE id = ?";

    private static final String SQL_INSERIR = """
            INSERT INTO tipo_turno
                (nome, hora_inicio, duracao_horas, intervalo_descanso_horas,
                 min_agentes, max_agentes, conta_banco_horas, ativo)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String SQL_ATUALIZAR = """
            UPDATE tipo_turno
            SET nome = ?, hora_inicio = ?, duracao_horas = ?, intervalo_descanso_horas = ?,
                min_agentes = ?, max_agentes = ?, conta_banco_horas = ?, ativo = ?
            WHERE id = ?
            """;

    private static final String SQL_ATIVAR = "UPDATE tipo_turno SET ativo = TRUE WHERE id = ?";

    private static final String SQL_DESATIVAR = "UPDATE tipo_turno SET ativo = FALSE WHERE id = ?";

    @Override
    public List<TipoTurno> listar(Boolean ativo) {
        String sql = ativo == null ? SQL_CAMPOS + " ORDER BY nome, id" : SQL_CAMPOS + " WHERE ativo = ? ORDER BY nome, id";

        try (Connection conexao = ConexaoBanco.getConnection();
             PreparedStatement stmt = conexao.prepareStatement(sql)) {

            if (ativo != null) {
                stmt.setBoolean(1, ativo);
            }

            List<TipoTurno> tipos = new ArrayList<>();
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    tipos.add(mapear(rs));
                }
            }
            return tipos;

        } catch (SQLException e) {
            throw new RepositoryException("Falha ao listar tipos de turno", e);
        }
    }

    @Override
    public Optional<TipoTurno> buscarPorId(int id) {
        try (Connection conexao = ConexaoBanco.getConnection();
             PreparedStatement stmt = conexao.prepareStatement(SQL_BUSCAR_POR_ID)) {

            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? Optional.of(mapear(rs)) : Optional.empty();
            }

        } catch (SQLException e) {
            throw new RepositoryException("Falha ao buscar tipo de turno " + id, e);
        }
    }

    @Override
    public TipoTurno inserir(TipoTurno tipoTurno) {
        try (Connection conexao = ConexaoBanco.getConnection();
             PreparedStatement stmt = conexao.prepareStatement(SQL_INSERIR, Statement.RETURN_GENERATED_KEYS)) {

            preencherCampos(stmt, tipoTurno);
            stmt.executeUpdate();

            try (ResultSet chaves = stmt.getGeneratedKeys()) {
                if (!chaves.next()) {
                    throw new RepositoryException("Banco nao retornou o id do tipo de turno inserido", null);
                }
                tipoTurno.setId(chaves.getInt(1));
            }
            return tipoTurno;

        } catch (SQLException e) {
            throw new RepositoryException("Falha ao inserir tipo de turno", e);
        }
    }

    @Override
    public TipoTurno atualizar(TipoTurno tipoTurno) {
        try (Connection conexao = ConexaoBanco.getConnection();
             PreparedStatement stmt = conexao.prepareStatement(SQL_ATUALIZAR)) {

            int indice = preencherCampos(stmt, tipoTurno);
            stmt.setInt(indice, tipoTurno.getId());
            if (stmt.executeUpdate() == 0) {
                throw new RepositoryException("Nenhum tipo de turno encontrado para atualizar", null);
            }
            return tipoTurno;

        } catch (SQLException e) {
            throw new RepositoryException("Falha ao atualizar tipo de turno", e);
        }
    }

    @Override
    public void ativar(int id) {
        executarAtualizacao(SQL_ATIVAR, "Falha ao ativar tipo de turno", id);
    }

    @Override
    public void desativar(int id) {
        executarAtualizacao(SQL_DESATIVAR, "Falha ao desativar tipo de turno", id);
    }

    private void executarAtualizacao(String sql, String mensagem, int id) {
        try (Connection conexao = ConexaoBanco.getConnection();
             PreparedStatement stmt = conexao.prepareStatement(sql)) {
            stmt.setInt(1, id);
            if (stmt.executeUpdate() == 0) {
                throw new RepositoryException("Nenhum tipo de turno encontrado para atualizar", null);
            }
        } catch (SQLException e) {
            throw new RepositoryException(mensagem, e);
        }
    }

    /** Preenche nome..ativo na ordem do INSERT/UPDATE e devolve o proximo indice livre (o do WHERE id). */
    private int preencherCampos(PreparedStatement stmt, TipoTurno tipoTurno) throws SQLException {
        stmt.setString(1, tipoTurno.getNome());
        stmt.setObject(2, tipoTurno.getHoraInicio());
        stmt.setBigDecimal(3, tipoTurno.getDuracaoHoras());
        stmt.setBigDecimal(4, tipoTurno.getIntervaloDescansoHoras());
        stmt.setInt(5, tipoTurno.getMinAgentes());
        if (tipoTurno.getMaxAgentes() != null) {
            stmt.setInt(6, tipoTurno.getMaxAgentes());
        } else {
            stmt.setNull(6, Types.INTEGER);
        }
        stmt.setBoolean(7, tipoTurno.isContaBancoHoras());
        stmt.setBoolean(8, tipoTurno.isAtivo());
        return 9;
    }

    private TipoTurno mapear(ResultSet rs) throws SQLException {
        TipoTurno tipoTurno = new TipoTurno();
        tipoTurno.setId(rs.getInt("id"));
        tipoTurno.setNome(rs.getString("nome"));
        tipoTurno.setHoraInicio(rs.getObject("hora_inicio", LocalTime.class));
        tipoTurno.setDuracaoHoras(rs.getBigDecimal("duracao_horas"));
        tipoTurno.setIntervaloDescansoHoras(rs.getBigDecimal("intervalo_descanso_horas"));
        tipoTurno.setMinAgentes(rs.getInt("min_agentes"));
        int maxAgentes = rs.getInt("max_agentes");
        tipoTurno.setMaxAgentes(rs.wasNull() ? null : maxAgentes);
        tipoTurno.setContaBancoHoras(rs.getBoolean("conta_banco_horas"));
        tipoTurno.setAtivo(rs.getBoolean("ativo"));
        tipoTurno.setCriadoEm(rs.getObject("criado_em", LocalDateTime.class));
        return tipoTurno;
    }
}
