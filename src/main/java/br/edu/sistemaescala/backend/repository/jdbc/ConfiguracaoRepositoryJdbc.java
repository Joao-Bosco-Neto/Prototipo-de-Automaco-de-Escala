package br.edu.sistemaescala.backend.repository.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.Optional;

import br.edu.sistemaescala.backend.dao.ConexaoBanco;
import br.edu.sistemaescala.backend.model.Configuracao;
import br.edu.sistemaescala.backend.repository.ConfiguracaoRepository;
import br.edu.sistemaescala.backend.repository.RepositoryException;

public class ConfiguracaoRepositoryJdbc implements ConfiguracaoRepository {

    private static final String SQL_BUSCAR = """
            SELECT id, nome_organizacao, subtitulo, carga_horaria_mensal,
                   apuracao_banco_horas, caminho_pdf_padrao, atualizado_em
            FROM configuracao
            ORDER BY id
            LIMIT 1
            """;

    private static final String SQL_ATUALIZAR = """
            UPDATE configuracao
            SET nome_organizacao = ?, subtitulo = ?, carga_horaria_mensal = ?,
                apuracao_banco_horas = ?, caminho_pdf_padrao = ?, atualizado_em = CURRENT_TIMESTAMP
            WHERE id = ?
            """;

    @Override
    public Optional<Configuracao> buscar() {
        try (Connection conexao = ConexaoBanco.getConnection();
             PreparedStatement stmt = conexao.prepareStatement(SQL_BUSCAR);
             ResultSet rs = stmt.executeQuery()) {
            return rs.next() ? Optional.of(mapear(rs)) : Optional.empty();
        } catch (SQLException e) {
            throw new RepositoryException("Falha ao buscar configuracao", e);
        }
    }

    @Override
    public Configuracao atualizar(Configuracao configuracao) {
        try (Connection conexao = ConexaoBanco.getConnection();
             PreparedStatement stmt = conexao.prepareStatement(SQL_ATUALIZAR)) {
            stmt.setString(1, configuracao.getNomeOrganizacao());
            setNullable(stmt, 2, configuracao.getSubtitulo(), Types.VARCHAR);
            setNullable(stmt, 3, configuracao.getCargaHorariaMensal(), Types.NUMERIC);
            stmt.setString(4, configuracao.getApuracaoBancoHoras());
            setNullable(stmt, 5, configuracao.getCaminhoPdfPadrao(), Types.VARCHAR);
            stmt.setInt(6, configuracao.getId());
            if (stmt.executeUpdate() == 0) {
                throw new RepositoryException("Nenhuma configuracao encontrada para atualizar", null);
            }
            return buscar().orElseThrow(() -> new RepositoryException("Configuracao nao encontrada apos atualizar", null));
        } catch (SQLException e) {
            throw new RepositoryException("Falha ao atualizar configuracao", e);
        }
    }

    private void setNullable(PreparedStatement stmt, int indice, Object valor, int tipo) throws SQLException {
        if (valor == null) {
            stmt.setNull(indice, tipo);
        } else {
            stmt.setObject(indice, valor);
        }
    }

    private Configuracao mapear(ResultSet rs) throws SQLException {
        Configuracao configuracao = new Configuracao();
        configuracao.setId(rs.getInt("id"));
        configuracao.setNomeOrganizacao(rs.getString("nome_organizacao"));
        configuracao.setSubtitulo(rs.getString("subtitulo"));
        configuracao.setCargaHorariaMensal(rs.getBigDecimal("carga_horaria_mensal"));
        configuracao.setApuracaoBancoHoras(rs.getString("apuracao_banco_horas"));
        configuracao.setCaminhoPdfPadrao(rs.getString("caminho_pdf_padrao"));
        configuracao.setAtualizadoEm(rs.getObject("atualizado_em", LocalDateTime.class));
        return configuracao;
    }
}