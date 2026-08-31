package br.edu.sistemaescala.backend.repository.jdbc;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import br.edu.sistemaescala.backend.dao.ConexaoBanco;
import br.edu.sistemaescala.backend.model.EscalaExcecao;
import br.edu.sistemaescala.backend.model.RegraExcecao;
import br.edu.sistemaescala.backend.repository.EscalaExcecaoRepository;
import br.edu.sistemaescala.backend.repository.RepositoryException;

/**
 * Implementacao JDBC de {@link EscalaExcecaoRepository}, sempre com
 * PreparedStatement.
 *
 * <p>Nao existe SQL de UPDATE nem de DELETE nesta classe, e isso e a propria
 * garantia de imutabilidade: {@code atualizar} fica com o {@code default} da
 * interface, que lanca {@link UnsupportedOperationException}.</p>
 */
public class EscalaExcecaoRepositoryJdbc implements EscalaExcecaoRepository {

    private static final String SQL_INSERIR = """
            INSERT INTO escala_excecao
                (escala_funcionario_id, funcionario_id, data_plantao, regra, descricao, autorizado_por, criado_em)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String SQL_LISTAR_POR_FUNCIONARIO = """
            SELECT id, escala_funcionario_id, funcionario_id, data_plantao,
                   regra, descricao, autorizado_por, criado_em
            FROM escala_excecao
            WHERE funcionario_id = ?
            ORDER BY criado_em DESC, id DESC
            """;

    @Override
    public EscalaExcecao inserir(EscalaExcecao excecao) {
        // Abre a conexao so para esta escrita e delega, para o SQL do INSERT
        // existir num lugar unico.
        try (Connection conexao = ConexaoBanco.getConnection()) {
            return inserir(excecao, conexao);
        } catch (SQLException e) {
            throw new RepositoryException("Falha ao gravar excecao de escala", e);
        }
    }

    @Override
    public EscalaExcecao inserir(EscalaExcecao excecao, Connection conexao) {
        try (PreparedStatement stmt = conexao.prepareStatement(SQL_INSERIR, Statement.RETURN_GENERATED_KEYS)) {

            LocalDateTime criadoEm = excecao.getCriadoEm() != null ? excecao.getCriadoEm() : LocalDateTime.now();
            excecao.setCriadoEm(criadoEm);

            if (excecao.getEscalaFuncionarioId() == null) {
                stmt.setNull(1, Types.INTEGER);
            } else {
                stmt.setInt(1, excecao.getEscalaFuncionarioId());
            }
            stmt.setInt(2, excecao.getFuncionarioId());
            stmt.setDate(3, Date.valueOf(excecao.getDataPlantao()));
            stmt.setString(4, excecao.getRegra().valor());
            stmt.setString(5, excecao.getDescricao());
            stmt.setString(6, excecao.getAutorizadoPor());
            stmt.setObject(7, criadoEm);
            stmt.executeUpdate();

            try (ResultSet chaves = stmt.getGeneratedKeys()) {
                if (chaves.next()) {
                    excecao.setId(chaves.getInt(1));
                }
            }
            return excecao;

        } catch (SQLException e) {
            throw new RepositoryException("Falha ao gravar excecao de escala", e);
        }
    }

    @Override
    public List<EscalaExcecao> listarPorFuncionario(int funcionarioId) {
        try (Connection conexao = ConexaoBanco.getConnection();
             PreparedStatement stmt = conexao.prepareStatement(SQL_LISTAR_POR_FUNCIONARIO)) {

            stmt.setInt(1, funcionarioId);

            List<EscalaExcecao> excecoes = new ArrayList<>();
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    excecoes.add(mapear(rs));
                }
            }
            return excecoes;

        } catch (SQLException e) {
            throw new RepositoryException("Falha ao listar excecoes de escala", e);
        }
    }

    private EscalaExcecao mapear(ResultSet rs) throws SQLException {
        EscalaExcecao excecao = new EscalaExcecao();
        excecao.setId(rs.getInt("id"));
        int alocacao = rs.getInt("escala_funcionario_id");
        excecao.setEscalaFuncionarioId(rs.wasNull() ? null : alocacao);
        excecao.setFuncionarioId(rs.getInt("funcionario_id"));
        excecao.setDataPlantao(rs.getObject("data_plantao", LocalDate.class));
        excecao.setRegra(RegraExcecao.deValor(rs.getString("regra")));
        excecao.setDescricao(rs.getString("descricao"));
        excecao.setAutorizadoPor(rs.getString("autorizado_por"));
        excecao.setCriadoEm(rs.getObject("criado_em", LocalDateTime.class));
        return excecao;
    }
}
