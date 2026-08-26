package br.edu.sistemaescala.backend.repository.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import br.edu.sistemaescala.backend.dao.ConexaoBanco;
import br.edu.sistemaescala.backend.model.EscalaFuncionario;
import br.edu.sistemaescala.backend.model.EscalaTurno;
import br.edu.sistemaescala.backend.model.Funcionario;
import br.edu.sistemaescala.backend.repository.EscalaFuncionarioRepository;
import br.edu.sistemaescala.backend.repository.RepositoryException;

/** Implementacao JDBC de {@link EscalaFuncionarioRepository}. */
public class EscalaFuncionarioRepositoryJdbc implements EscalaFuncionarioRepository {

    private static final String SQL_LISTAR_POR_TURNO = """
            SELECT ef.id AS ef_id, ef.escala_turno_id AS ef_escala_turno_id,
                   ef.funcionario_id AS ef_funcionario_id, ef.inicio AS ef_inicio, ef.fim AS ef_fim,
                   ef.cobertura_de AS ef_cobertura_de, ef.motivo_cobertura_id AS ef_motivo_cobertura_id,
                   ef.observacao AS ef_observacao, ef.lancou_banco_horas AS ef_lancou_banco_horas,
                   ef.criado_em AS ef_criado_em,
                   f.nome AS f_nome, f.matricula AS f_matricula, f.telefone AS f_telefone,
                   f.observacoes AS f_observacoes, f.ativo AS f_ativo, f.criado_em AS f_criado_em
            FROM escala_funcionario ef
            JOIN funcionario f ON f.id = ef.funcionario_id
            WHERE ef.escala_turno_id = ?
            ORDER BY f.nome, ef.id
            """;

    private static final String SQL_LISTAR_POR_FUNCIONARIO = """
            SELECT ef.id AS ef_id, ef.escala_turno_id AS ef_escala_turno_id,
                   ef.funcionario_id AS ef_funcionario_id, ef.inicio AS ef_inicio, ef.fim AS ef_fim,
                   ef.cobertura_de AS ef_cobertura_de, ef.motivo_cobertura_id AS ef_motivo_cobertura_id,
                   ef.observacao AS ef_observacao, ef.lancou_banco_horas AS ef_lancou_banco_horas,
                   ef.criado_em AS ef_criado_em,
                   et.inicio AS et_inicio, et.fim AS et_fim
            FROM escala_funcionario ef
            JOIN escala_turno et ON et.id = ef.escala_turno_id
            WHERE ef.funcionario_id = ?
              AND et.inicio >= ? AND et.inicio < ?
            ORDER BY et.inicio, ef.id
            """;

    private static final String SQL_INSERIR = """
            INSERT INTO escala_funcionario
                (escala_turno_id, funcionario_id, inicio, fim, cobertura_de, motivo_cobertura_id,
                 observacao, lancou_banco_horas)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String SQL_REMOVER = "DELETE FROM escala_funcionario WHERE id = ?";

    private static final String SQL_BUSCAR_COBERTURAS_DO_MES = """
            SELECT ef.id AS ef_id, ef.escala_turno_id AS ef_escala_turno_id,
                   ef.funcionario_id AS ef_funcionario_id, ef.inicio AS ef_inicio, ef.fim AS ef_fim,
                   ef.cobertura_de AS ef_cobertura_de, ef.motivo_cobertura_id AS ef_motivo_cobertura_id,
                   ef.observacao AS ef_observacao, ef.lancou_banco_horas AS ef_lancou_banco_horas,
                   ef.criado_em AS ef_criado_em,
                   f.nome AS f_nome, f.matricula AS f_matricula, f.telefone AS f_telefone,
                   f.observacoes AS f_observacoes, f.ativo AS f_ativo, f.criado_em AS f_criado_em
            FROM escala_funcionario ef
            JOIN escala_turno et ON et.id = ef.escala_turno_id
            JOIN funcionario f ON f.id = ef.funcionario_id
            WHERE ef.cobertura_de IS NOT NULL
              AND et.inicio >= ? AND et.inicio < ?
            ORDER BY et.inicio, ef.id
            """;

    @Override
    public List<EscalaFuncionario> listarPorTurno(int escalaTurnoId) {
        try (Connection conexao = ConexaoBanco.getConnection();
             PreparedStatement stmt = conexao.prepareStatement(SQL_LISTAR_POR_TURNO)) {

            stmt.setInt(1, escalaTurnoId);
            List<EscalaFuncionario> agentes = new ArrayList<>();
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    agentes.add(mapearComFuncionario(rs));
                }
            }
            return agentes;

        } catch (SQLException e) {
            throw new RepositoryException("Falha ao listar agentes do turno " + escalaTurnoId, e);
        }
    }

    @Override
    public List<EscalaFuncionario> listarPorFuncionario(int funcionarioId, LocalDateTime inicio, LocalDateTime fim) {
        try (Connection conexao = ConexaoBanco.getConnection();
             PreparedStatement stmt = conexao.prepareStatement(SQL_LISTAR_POR_FUNCIONARIO)) {

            stmt.setInt(1, funcionarioId);
            stmt.setObject(2, inicio);
            stmt.setObject(3, fim);
            List<EscalaFuncionario> plantoes = new ArrayList<>();
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    plantoes.add(mapearComTurno(rs, funcionarioId));
                }
            }
            return plantoes;

        } catch (SQLException e) {
            throw new RepositoryException("Falha ao listar plantoes do funcionario " + funcionarioId, e);
        }
    }

    @Override
    public EscalaFuncionario inserir(EscalaFuncionario escalaFuncionario) {
        // Abre a conexao so para esta escrita e delega, para o SQL do INSERT
        // existir num lugar unico.
        try (Connection conexao = ConexaoBanco.getConnection()) {
            return inserir(escalaFuncionario, conexao);

        } catch (SQLException e) {
            throw new RepositoryException("Falha ao inserir alocacao de funcionario no turno", e);
        }
    }

    @Override
    public EscalaFuncionario inserir(EscalaFuncionario escalaFuncionario, Connection conexao) {
        // A conexao vem de fora (TransacaoUtil, por exemplo) e nao e fechada
        // aqui: quem abriu decide a hora do commit, do rollback e do close.
        try (PreparedStatement stmt = conexao.prepareStatement(SQL_INSERIR, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setInt(1, escalaFuncionario.getEscalaTurno().getId());
            stmt.setInt(2, escalaFuncionario.getFuncionario().getId());
            stmt.setObject(3, escalaFuncionario.getInicio());
            stmt.setObject(4, escalaFuncionario.getFim());
            setNullableInt(stmt, 5, escalaFuncionario.getCoberturaDe() != null
                    ? escalaFuncionario.getCoberturaDe().getId() : null);
            setNullableInt(stmt, 6, escalaFuncionario.getMotivoCoberturaId());
            stmt.setString(7, escalaFuncionario.getObservacao());
            stmt.setBoolean(8, escalaFuncionario.isLancouBancoHoras());
            stmt.executeUpdate();

            try (ResultSet chaves = stmt.getGeneratedKeys()) {
                if (!chaves.next()) {
                    throw new RepositoryException("Banco nao retornou o id da alocacao inserida", null);
                }
                escalaFuncionario.setId(chaves.getInt(1));
            }
            return escalaFuncionario;

        } catch (SQLException e) {
            throw new RepositoryException("Falha ao inserir alocacao de funcionario no turno", e);
        }
    }

    @Override
    public void remover(int id) {
        try (Connection conexao = ConexaoBanco.getConnection();
             PreparedStatement stmt = conexao.prepareStatement(SQL_REMOVER)) {

            stmt.setInt(1, id);
            if (stmt.executeUpdate() == 0) {
                throw new RepositoryException("Nenhuma alocacao encontrada para remover", null);
            }

        } catch (SQLException e) {
            throw new RepositoryException("Falha ao remover alocacao " + id, e);
        }
    }

    @Override
    public List<EscalaFuncionario> buscarCoberturasDoMes(YearMonth mes) {
        try (Connection conexao = ConexaoBanco.getConnection();
             PreparedStatement stmt = conexao.prepareStatement(SQL_BUSCAR_COBERTURAS_DO_MES)) {

            stmt.setObject(1, mes.atDay(1).atStartOfDay());
            stmt.setObject(2, mes.plusMonths(1).atDay(1).atStartOfDay());
            List<EscalaFuncionario> coberturas = new ArrayList<>();
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    coberturas.add(mapearComFuncionario(rs));
                }
            }
            return coberturas;

        } catch (SQLException e) {
            throw new RepositoryException("Falha ao buscar coberturas do mes " + mes, e);
        }
    }

    private void setNullableInt(PreparedStatement stmt, int indice, Integer valor) throws SQLException {
        if (valor != null) {
            stmt.setInt(indice, valor);
        } else {
            stmt.setNull(indice, Types.INTEGER);
        }
    }

    /** Monta o registro com funcionario hidratado; escalaTurno e coberturaDe ficam "rasos" (so com o id). */
    private EscalaFuncionario mapearComFuncionario(ResultSet rs) throws SQLException {
        EscalaFuncionario agente = mapearCamposBase(rs);

        EscalaTurno escalaTurno = new EscalaTurno();
        escalaTurno.setId(rs.getInt("ef_escala_turno_id"));
        agente.setEscalaTurno(escalaTurno);

        agente.setFuncionario(mapearFuncionario(rs));
        return agente;
    }

    /** Monta o registro com escalaTurno (inicio/fim) hidratado; funcionario fica "raso" (id ja conhecido pelo chamador). */
    private EscalaFuncionario mapearComTurno(ResultSet rs, int funcionarioId) throws SQLException {
        EscalaFuncionario plantao = mapearCamposBase(rs);

        EscalaTurno escalaTurno = new EscalaTurno();
        escalaTurno.setId(rs.getInt("ef_escala_turno_id"));
        escalaTurno.setInicio(rs.getObject("et_inicio", LocalDateTime.class));
        escalaTurno.setFim(rs.getObject("et_fim", LocalDateTime.class));
        plantao.setEscalaTurno(escalaTurno);

        Funcionario funcionario = new Funcionario();
        funcionario.setId(funcionarioId);
        plantao.setFuncionario(funcionario);
        return plantao;
    }

    private EscalaFuncionario mapearCamposBase(ResultSet rs) throws SQLException {
        EscalaFuncionario agente = new EscalaFuncionario();
        agente.setId(rs.getInt("ef_id"));
        agente.setInicio(rs.getObject("ef_inicio", LocalDateTime.class));
        agente.setFim(rs.getObject("ef_fim", LocalDateTime.class));

        int coberturaDeId = rs.getInt("ef_cobertura_de");
        if (!rs.wasNull()) {
            EscalaFuncionario coberturaDe = new EscalaFuncionario();
            coberturaDe.setId(coberturaDeId);
            agente.setCoberturaDe(coberturaDe);
        }

        int motivoCoberturaId = rs.getInt("ef_motivo_cobertura_id");
        agente.setMotivoCoberturaId(rs.wasNull() ? null : motivoCoberturaId);

        agente.setObservacao(rs.getString("ef_observacao"));
        agente.setLancouBancoHoras(rs.getBoolean("ef_lancou_banco_horas"));
        agente.setCriadoEm(rs.getObject("ef_criado_em", LocalDateTime.class));
        return agente;
    }

    private Funcionario mapearFuncionario(ResultSet rs) throws SQLException {
        Funcionario funcionario = new Funcionario();
        funcionario.setId(rs.getInt("ef_funcionario_id"));
        funcionario.setNome(rs.getString("f_nome"));
        funcionario.setMatricula(rs.getString("f_matricula"));
        funcionario.setTelefone(rs.getString("f_telefone"));
        funcionario.setObservacoes(rs.getString("f_observacoes"));
        funcionario.setAtivo(rs.getBoolean("f_ativo"));
        funcionario.setCriadoEm(rs.getObject("f_criado_em", LocalDateTime.class));
        return funcionario;
    }
}
