package br.edu.sistemaescala.backend.repository.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Duration;
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
import br.edu.sistemaescala.backend.service.CoberturaListagemItem;

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

    private static final String SQL_ATUALIZAR = """
            UPDATE escala_funcionario
               SET escala_turno_id = ?, funcionario_id = ?, inicio = ?, fim = ?,
                   cobertura_de = ?, motivo_cobertura_id = ?, observacao = ?, lancou_banco_horas = ?
             WHERE id = ?
            """;

    private static final String SQL_REMOVER = "DELETE FROM escala_funcionario WHERE id = ?";

    /**
     * Mesmo recorte do SQL_BUSCAR_COBERTURAS_DO_MES, so que agregado: o
     * dashboard quer o numero, nao as linhas. Sem JOIN com funcionario, que
     * nada acrescenta a uma contagem.
     */
    private static final String SQL_CONTAR_COBERTURAS_DO_MES = """
            SELECT COUNT(*)
            FROM escala_funcionario ef
            JOIN escala_turno et ON et.id = ef.escala_turno_id
            WHERE ef.cobertura_de IS NOT NULL
              AND et.inicio >= ? AND et.inicio < ?
            """;

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

    /**
     * Consulta da tabela da tela de Coberturas.
     *
     * O LEFT JOIN volta a escala_funcionario para alcancar o titular coberto
     * (ef_aus) e dai chegar ao nome dele (f_aus); motivo_cobertura entra pelo
     * nome do motivo. Sao LEFT porque cobertura_de aponta para uma linha que
     * pode ter sido apagada em cascata e motivo_cobertura_id e opcional.
     */
    private static final String SQL_LISTAR_PARA_LISTAGEM = """
            SELECT ef.id AS ef_id, ef.escala_turno_id AS ef_escala_turno_id,
                   ef.funcionario_id AS ef_funcionario_id, ef.inicio AS ef_inicio, ef.fim AS ef_fim,
                   ef.cobertura_de AS ef_cobertura_de, ef.motivo_cobertura_id AS ef_motivo_cobertura_id,
                   ef.observacao AS ef_observacao, ef.lancou_banco_horas AS ef_lancou_banco_horas,
                   ef.criado_em AS ef_criado_em,
                   et.inicio AS et_inicio, et.fim AS et_fim,
                   f.nome AS f_nome, f.matricula AS f_matricula, f.telefone AS f_telefone,
                   f.observacoes AS f_observacoes, f.ativo AS f_ativo, f.criado_em AS f_criado_em,
                   ef_aus.funcionario_id AS aus_funcionario_id,
                   f_aus.nome AS aus_nome, f_aus.matricula AS aus_matricula,
                   mc.nome AS mc_nome
            FROM escala_funcionario ef
            JOIN escala_turno et ON et.id = ef.escala_turno_id
            JOIN funcionario f ON f.id = ef.funcionario_id
            LEFT JOIN escala_funcionario ef_aus ON ef_aus.id = ef.cobertura_de
            LEFT JOIN funcionario f_aus ON f_aus.id = ef_aus.funcionario_id
            LEFT JOIN motivo_cobertura mc ON mc.id = ef.motivo_cobertura_id
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
    public EscalaFuncionario atualizar(EscalaFuncionario escalaFuncionario, Connection conexao) {
        // A conexao vem de fora (TransacaoUtil, por exemplo) e nao e fechada
        // aqui: quem abriu decide a hora do commit, do rollback e do close.
        try (PreparedStatement stmt = conexao.prepareStatement(SQL_ATUALIZAR)) {

            stmt.setInt(1, escalaFuncionario.getEscalaTurno().getId());
            stmt.setInt(2, escalaFuncionario.getFuncionario().getId());
            stmt.setObject(3, escalaFuncionario.getInicio());
            stmt.setObject(4, escalaFuncionario.getFim());
            setNullableInt(stmt, 5, escalaFuncionario.getCoberturaDe() != null
                    ? escalaFuncionario.getCoberturaDe().getId() : null);
            setNullableInt(stmt, 6, escalaFuncionario.getMotivoCoberturaId());
            stmt.setString(7, escalaFuncionario.getObservacao());
            stmt.setBoolean(8, escalaFuncionario.isLancouBancoHoras());
            stmt.setInt(9, escalaFuncionario.getId());

            if (stmt.executeUpdate() == 0) {
                throw new RepositoryException(
                        "Nenhuma alocacao encontrada para atualizar (id " + escalaFuncionario.getId() + ")", null);
            }
            return escalaFuncionario;

        } catch (SQLException e) {
            throw new RepositoryException(
                    "Falha ao atualizar alocacao " + escalaFuncionario.getId(), e);
        }
    }

    @Override
    public void remover(int id) {
        // Abre a conexao so para esta escrita e delega, para o SQL do DELETE
        // existir num lugar unico.
        try (Connection conexao = ConexaoBanco.getConnection()) {
            remover(id, conexao);

        } catch (SQLException e) {
            throw new RepositoryException("Falha ao remover alocacao " + id, e);
        }
    }

    @Override
    public void remover(int id, Connection conexao) {
        // A conexao vem de fora (TransacaoUtil, por exemplo) e nao e fechada
        // aqui: quem abriu decide a hora do commit, do rollback e do close.
        try (PreparedStatement stmt = conexao.prepareStatement(SQL_REMOVER)) {

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

    @Override
    public int contarCoberturasDoMes(YearMonth mes) {
        try (Connection conexao = ConexaoBanco.getConnection();
             PreparedStatement stmt = conexao.prepareStatement(SQL_CONTAR_COBERTURAS_DO_MES)) {

            stmt.setObject(1, mes.atDay(1).atStartOfDay());
            stmt.setObject(2, mes.plusMonths(1).atDay(1).atStartOfDay());
            try (ResultSet rs = stmt.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }

        } catch (SQLException e) {
            throw new RepositoryException("Falha ao contar coberturas do mes " + mes, e);
        }
    }

    @Override
    public List<CoberturaListagemItem> listarCoberturasParaListagem(YearMonth mes) {
        try (Connection conexao = ConexaoBanco.getConnection();
             PreparedStatement stmt = conexao.prepareStatement(SQL_LISTAR_PARA_LISTAGEM)) {

            stmt.setObject(1, mes.atDay(1).atStartOfDay());
            stmt.setObject(2, mes.plusMonths(1).atDay(1).atStartOfDay());
            List<CoberturaListagemItem> itens = new ArrayList<>();
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    itens.add(mapearItemDeListagem(rs));
                }
            }
            return itens;

        } catch (SQLException e) {
            throw new RepositoryException("Falha ao listar coberturas do mes " + mes, e);
        }
    }

    /**
     * Monta a linha da tabela e, junto, a entidade hidratada que a edicao e a
     * exclusao vao usar: substituto e ausente com nome e matricula, e o turno
     * com inicio e fim.
     */
    private CoberturaListagemItem mapearItemDeListagem(ResultSet rs) throws SQLException {
        EscalaFuncionario cobertura = mapearCamposBase(rs);

        EscalaTurno turno = new EscalaTurno();
        turno.setId(rs.getInt("ef_escala_turno_id"));
        turno.setInicio(rs.getObject("et_inicio", LocalDateTime.class));
        turno.setFim(rs.getObject("et_fim", LocalDateTime.class));
        cobertura.setEscalaTurno(turno);

        cobertura.setFuncionario(mapearFuncionario(rs));

        // mapearCamposBase deixa o coberturaDe so com o id; aqui ele ganha o
        // funcionario ausente, que e o nome exibido na coluna "Ausente".
        String nomeAusente = rs.getString("aus_nome");
        int ausenteFuncionarioId = rs.getInt("aus_funcionario_id");
        if (!rs.wasNull() && cobertura.getCoberturaDe() != null) {
            Funcionario ausente = new Funcionario();
            ausente.setId(ausenteFuncionarioId);
            ausente.setNome(nomeAusente);
            ausente.setMatricula(rs.getString("aus_matricula"));
            cobertura.getCoberturaDe().setFuncionario(ausente);
        }

        int minutosDoTurno = turno.getInicio() != null && turno.getFim() != null
                ? (int) Duration.between(turno.getInicio(), turno.getFim()).toMinutes()
                : 0;

        return new CoberturaListagemItem(
                cobertura.getId(),
                turno.getInicio() != null ? turno.getInicio().toLocalDate() : null,
                cobertura.getFuncionario().getNome(),
                nomeAusente,
                rs.getString("mc_nome"),
                cobertura.isLancouBancoHoras(),
                minutosDoTurno,
                cobertura);
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
