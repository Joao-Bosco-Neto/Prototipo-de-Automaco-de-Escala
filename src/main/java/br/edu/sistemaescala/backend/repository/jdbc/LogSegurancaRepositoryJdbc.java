package br.edu.sistemaescala.backend.repository.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import br.edu.sistemaescala.backend.dao.ConexaoBanco;
import br.edu.sistemaescala.backend.model.AcaoSeguranca;
import br.edu.sistemaescala.backend.model.LogSeguranca;
import br.edu.sistemaescala.backend.model.ResultadoSeguranca;
import br.edu.sistemaescala.backend.repository.LogSegurancaRepository;
import br.edu.sistemaescala.backend.repository.RepositoryException;

/**
 * Implementacao JDBC de {@link LogSegurancaRepository}, sempre com
 * PreparedStatement.
 *
 * <p>Nao ha SQL de UPDATE nem de DELETE nesta classe, e isso e proposital: a
 * imutabilidade da trilha e garantida por nao existir o comando, sem depender
 * de trigger no banco.</p>
 */
public class LogSegurancaRepositoryJdbc implements LogSegurancaRepository {

    private static final String SQL_INSERIR = """
            INSERT INTO log_seguranca (data_hora, identificacao, acao, resultado, detalhes)
            VALUES (?, ?, ?, ?, ?)
            """;

    private static final String SQL_LISTAR_POR_PERIODO = """
            SELECT id, data_hora, identificacao, acao, resultado, detalhes
            FROM log_seguranca
            WHERE data_hora BETWEEN ? AND ?
            ORDER BY data_hora DESC, id DESC
            """;

    @Override
    public LogSeguranca inserir(LogSeguranca log) {
        try (Connection conexao = ConexaoBanco.getConnection();
             PreparedStatement stmt = conexao.prepareStatement(SQL_INSERIR, Statement.RETURN_GENERATED_KEYS)) {

            LocalDateTime dataHora = log.getDataHora() != null ? log.getDataHora() : LocalDateTime.now();
            log.setDataHora(dataHora);

            stmt.setObject(1, dataHora);
            stmt.setString(2, log.getIdentificacao());
            stmt.setString(3, log.getAcao().valor());
            if (log.getResultado() == null) {
                stmt.setNull(4, Types.VARCHAR);
            } else {
                stmt.setString(4, log.getResultado().valor());
            }
            if (log.getDetalhes() == null) {
                stmt.setNull(5, Types.VARCHAR);
            } else {
                stmt.setString(5, log.getDetalhes());
            }
            stmt.executeUpdate();

            try (ResultSet chaves = stmt.getGeneratedKeys()) {
                if (chaves.next()) {
                    log.setId(chaves.getInt(1));
                }
            }
            return log;

        } catch (SQLException e) {
            throw new RepositoryException("Falha ao gravar evento de seguranca", e);
        }
    }

    @Override
    public List<LogSeguranca> listarPorPeriodo(LocalDateTime inicio, LocalDateTime fim) {
        try (Connection conexao = ConexaoBanco.getConnection();
             PreparedStatement stmt = conexao.prepareStatement(SQL_LISTAR_POR_PERIODO)) {

            stmt.setObject(1, inicio);
            stmt.setObject(2, fim);

            List<LogSeguranca> eventos = new ArrayList<>();
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    eventos.add(mapear(rs));
                }
            }
            return eventos;

        } catch (SQLException e) {
            throw new RepositoryException("Falha ao listar eventos de seguranca", e);
        }
    }

    private LogSeguranca mapear(ResultSet rs) throws SQLException {
        LogSeguranca log = new LogSeguranca();
        log.setId(rs.getInt("id"));
        log.setDataHora(rs.getObject("data_hora", LocalDateTime.class));
        log.setIdentificacao(rs.getString("identificacao"));
        log.setAcao(AcaoSeguranca.deValor(rs.getString("acao")));
        String resultado = rs.getString("resultado");
        log.setResultado(resultado == null ? null : ResultadoSeguranca.deValor(resultado));
        log.setDetalhes(rs.getString("detalhes"));
        return log;
    }
}
