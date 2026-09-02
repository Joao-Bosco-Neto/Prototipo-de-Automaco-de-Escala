package br.edu.sistemaescala.backend.repository.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import br.edu.sistemaescala.backend.dao.ConexaoBanco;
import br.edu.sistemaescala.backend.model.EscalaFuncionario;
import br.edu.sistemaescala.backend.model.EscalaTurno;
import br.edu.sistemaescala.backend.model.Funcionario;
import br.edu.sistemaescala.backend.model.TipoTurno;
import br.edu.sistemaescala.backend.repository.EscalaTurnoRepository;
import br.edu.sistemaescala.backend.repository.RepositoryException;
import br.edu.sistemaescala.backend.service.PlantaoDoDiaItem;

/**
 * Implementacao JDBC de {@link EscalaTurnoRepository}.
 *
 * {@link #buscarPorPeriodo} e {@link #buscarPorId} usam um unico SELECT com
 * LEFT JOIN ate funcionario, montando o grafo turno -> agentes em memoria
 * (agrupando linhas por escala_turno_id num LinkedHashMap) em vez de repetir
 * uma consulta por turno — e o que evita o N+1 pedido na issue #11.
 */
public class EscalaTurnoRepositoryJdbc implements EscalaTurnoRepository {

    private static final String SQL_CAMPOS_COM_AGENTES = """
            SELECT et.id AS et_id, et.tipo_turno_id AS et_tipo_turno_id,
                   et.inicio AS et_inicio, et.fim AS et_fim,
                   et.min_agentes AS et_min_agentes, et.max_agentes AS et_max_agentes,
                   et.observacao AS et_observacao, et.ativo AS et_ativo, et.criado_em AS et_criado_em,
                   tt.nome AS tt_nome, tt.hora_inicio AS tt_hora_inicio,
                   tt.duracao_horas AS tt_duracao_horas,
                   tt.intervalo_descanso_horas AS tt_intervalo_descanso_horas,
                   tt.min_agentes AS tt_min_agentes, tt.max_agentes AS tt_max_agentes,
                   tt.conta_banco_horas AS tt_conta_banco_horas, tt.ativo AS tt_ativo,
                   tt.criado_em AS tt_criado_em,
                   ef.id AS ef_id, ef.funcionario_id AS ef_funcionario_id,
                   ef.inicio AS ef_inicio, ef.fim AS ef_fim, ef.cobertura_de AS ef_cobertura_de,
                   ef.motivo_cobertura_id AS ef_motivo_cobertura_id,
                   ef.observacao AS ef_observacao, ef.lancou_banco_horas AS ef_lancou_banco_horas,
                   ef.criado_em AS ef_criado_em,
                   f.nome AS f_nome, f.matricula AS f_matricula, f.telefone AS f_telefone,
                   f.observacoes AS f_observacoes, f.ativo AS f_ativo, f.criado_em AS f_criado_em
            FROM escala_turno et
            JOIN tipo_turno tt ON tt.id = et.tipo_turno_id
            LEFT JOIN escala_funcionario ef ON ef.escala_turno_id = et.id
            LEFT JOIN funcionario f ON f.id = ef.funcionario_id
            """;

    private static final String SQL_BUSCAR_POR_PERIODO = SQL_CAMPOS_COM_AGENTES + """
            WHERE et.inicio >= ? AND et.inicio < ?
            ORDER BY et.inicio, et.id, ef.id
            """;

    private static final String SQL_BUSCAR_POR_ID = SQL_CAMPOS_COM_AGENTES + """
            WHERE et.id = ?
            ORDER BY ef.id
            """;

    private static final String SQL_INSERIR = """
            INSERT INTO escala_turno (tipo_turno_id, inicio, fim, min_agentes, max_agentes, observacao, ativo)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String SQL_ATUALIZAR = """
            UPDATE escala_turno
            SET tipo_turno_id = ?, inicio = ?, fim = ?, min_agentes = ?, max_agentes = ?,
                observacao = ?, ativo = ?
            WHERE id = ?
            """;

    private static final String SQL_REMOVER_POR_MES = """
            DELETE FROM escala_turno WHERE inicio >= ? AND inicio < ?
            """;

    /**
     * Postos de fato ocupados num turno, na mesma regra do efetivo do
     * calendario: a alocacao do titular que alguem cobriu nao entra, senao um
     * turno de minimo 2 com uma cobertura apareceria como 3/2.
     *
     * Fragmento porque as duas consultas agregadas do dashboard precisam
     * exatamente da mesma contagem — deixar as duas copias divergirem faria o
     * card "Dias incompletos" discordar do "Plantao de hoje".
     */
    private static final String SQL_POSTOS_OCUPADOS = """
            (SELECT COUNT(*)
               FROM escala_funcionario ef
              WHERE ef.escala_turno_id = et.id
                AND NOT EXISTS (SELECT 1 FROM escala_funcionario cob
                                 WHERE cob.cobertura_de = ef.id))
            """;

    /** Um turno por linha, com o nome do tipo e a contagem de agentes ja agregada. */
    private static final String SQL_RESUMIR_PLANTOES_DO_DIA = """
            SELECT et.id AS et_id, tt.nome AS tt_nome,
                   et.inicio AS et_inicio, et.fim AS et_fim,
                   et.min_agentes AS et_min_agentes,
            """ + SQL_POSTOS_OCUPADOS + """
                   AS agentes
            FROM escala_turno et
            JOIN tipo_turno tt ON tt.id = et.tipo_turno_id
            WHERE et.inicio >= ? AND et.inicio < ?
            ORDER BY et.inicio, et.id
            """;

    /** Turnos que comecam no mes — so o numero, para saber se o mes tem escala. */
    private static final String SQL_CONTAR_TURNOS_NO_MES = """
            SELECT COUNT(*)
            FROM escala_turno et
            WHERE et.inicio >= ? AND et.inicio < ?
            """;

    /** Dias distintos do mes com pelo menos um turno abaixo do minimo. */
    private static final String SQL_CONTAR_DIAS_INCOMPLETOS = """
            SELECT COUNT(DISTINCT CAST(et.inicio AS DATE))
            FROM escala_turno et
            WHERE et.inicio >= ? AND et.inicio < ?
              AND
            """ + SQL_POSTOS_OCUPADOS + """
                  < et.min_agentes
            """;

    @Override
    public List<EscalaTurno> buscarPorPeriodo(LocalDateTime inicio, LocalDateTime fim) {
        try (Connection conexao = ConexaoBanco.getConnection();
             PreparedStatement stmt = conexao.prepareStatement(SQL_BUSCAR_POR_PERIODO)) {

            stmt.setObject(1, inicio);
            stmt.setObject(2, fim);
            try (ResultSet rs = stmt.executeQuery()) {
                return List.copyOf(mapearComAgentes(rs).values());
            }

        } catch (SQLException e) {
            throw new RepositoryException("Falha ao buscar turnos do periodo " + inicio + " a " + fim, e);
        }
    }

    @Override
    public Optional<EscalaTurno> buscarPorId(int id) {
        try (Connection conexao = ConexaoBanco.getConnection();
             PreparedStatement stmt = conexao.prepareStatement(SQL_BUSCAR_POR_ID)) {

            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                Map<Integer, EscalaTurno> turnos = mapearComAgentes(rs);
                return Optional.ofNullable(turnos.get(id));
            }

        } catch (SQLException e) {
            throw new RepositoryException("Falha ao buscar turno " + id, e);
        }
    }

    @Override
    public EscalaTurno salvar(EscalaTurno turno) {
        // Abre a conexao so para esta escrita e delega, para o SQL do
        // INSERT/UPDATE existir num lugar unico.
        try (Connection conexao = ConexaoBanco.getConnection()) {
            return salvar(turno, conexao);

        } catch (SQLException e) {
            throw new RepositoryException("Falha ao salvar turno", e);
        }
    }

    @Override
    public EscalaTurno salvar(EscalaTurno turno, Connection conexao) {
        // A conexao vem de fora (TransacaoUtil, por exemplo) e nao e fechada
        // aqui: quem abriu decide a hora do commit, do rollback e do close.
        return turno.getId() == null ? inserir(turno, conexao) : atualizar(turno, conexao);
    }

    private EscalaTurno inserir(EscalaTurno turno, Connection conexao) {
        try (PreparedStatement stmt = conexao.prepareStatement(SQL_INSERIR, Statement.RETURN_GENERATED_KEYS)) {

            preencherCampos(stmt, turno);
            stmt.executeUpdate();

            try (ResultSet chaves = stmt.getGeneratedKeys()) {
                if (!chaves.next()) {
                    throw new RepositoryException("Banco nao retornou o id do turno inserido", null);
                }
                turno.setId(chaves.getInt(1));
            }
            return turno;

        } catch (SQLException e) {
            throw new RepositoryException("Falha ao inserir turno", e);
        }
    }

    private EscalaTurno atualizar(EscalaTurno turno, Connection conexao) {
        try (PreparedStatement stmt = conexao.prepareStatement(SQL_ATUALIZAR)) {

            int indice = preencherCampos(stmt, turno);
            stmt.setInt(indice, turno.getId());
            if (stmt.executeUpdate() == 0) {
                throw new RepositoryException("Nenhum turno encontrado para atualizar", null);
            }
            return turno;

        } catch (SQLException e) {
            throw new RepositoryException("Falha ao atualizar turno", e);
        }
    }

    @Override
    public void removerPorMes(YearMonth mes) {
        try (Connection conexao = ConexaoBanco.getConnection()) {
            removerPorMes(mes, conexao);

        } catch (SQLException e) {
            throw new RepositoryException("Falha ao remover turnos do mes " + mes, e);
        }
    }

    @Override
    public void removerPorMes(YearMonth mes, Connection conexao) {
        try (PreparedStatement stmt = conexao.prepareStatement(SQL_REMOVER_POR_MES)) {

            stmt.setObject(1, mes.atDay(1).atStartOfDay());
            stmt.setObject(2, mes.plusMonths(1).atDay(1).atStartOfDay());
            stmt.executeUpdate();

        } catch (SQLException e) {
            throw new RepositoryException("Falha ao remover turnos do mes " + mes, e);
        }
    }

    @Override
    public List<PlantaoDoDiaItem> resumirPlantoesDoDia(LocalDate dia) {
        try (Connection conexao = ConexaoBanco.getConnection();
             PreparedStatement stmt = conexao.prepareStatement(SQL_RESUMIR_PLANTOES_DO_DIA)) {

            stmt.setObject(1, dia.atStartOfDay());
            stmt.setObject(2, dia.plusDays(1).atStartOfDay());

            List<PlantaoDoDiaItem> plantoes = new ArrayList<>();
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    plantoes.add(new PlantaoDoDiaItem(
                            rs.getInt("et_id"),
                            rs.getString("tt_nome"),
                            rs.getObject("et_inicio", LocalDateTime.class),
                            rs.getObject("et_fim", LocalDateTime.class),
                            rs.getInt("agentes"),
                            rs.getInt("et_min_agentes")));
                }
            }
            return plantoes;

        } catch (SQLException e) {
            throw new RepositoryException("Falha ao resumir os plantoes do dia " + dia, e);
        }
    }

    @Override
    public int contarDiasComEfetivoIncompleto(YearMonth mes) {
        try (Connection conexao = ConexaoBanco.getConnection();
             PreparedStatement stmt = conexao.prepareStatement(SQL_CONTAR_DIAS_INCOMPLETOS)) {

            stmt.setObject(1, mes.atDay(1).atStartOfDay());
            stmt.setObject(2, mes.plusMonths(1).atDay(1).atStartOfDay());
            try (ResultSet rs = stmt.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }

        } catch (SQLException e) {
            throw new RepositoryException(
                    "Falha ao contar dias com efetivo incompleto no mes " + mes, e);
        }
    }

    @Override
    public int contarTurnosNoMes(YearMonth mes) {
        try (Connection conexao = ConexaoBanco.getConnection();
             PreparedStatement stmt = conexao.prepareStatement(SQL_CONTAR_TURNOS_NO_MES)) {

            stmt.setObject(1, mes.atDay(1).atStartOfDay());
            stmt.setObject(2, mes.plusMonths(1).atDay(1).atStartOfDay());
            try (ResultSet rs = stmt.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }

        } catch (SQLException e) {
            throw new RepositoryException("Falha ao contar turnos do mes " + mes, e);
        }
    }

    /** Preenche tipo_turno_id..ativo na ordem do INSERT/UPDATE e devolve o proximo indice livre (o do WHERE id). */
    private int preencherCampos(PreparedStatement stmt, EscalaTurno turno) throws SQLException {
        stmt.setInt(1, turno.getTipoTurno().getId());
        stmt.setObject(2, turno.getInicio());
        stmt.setObject(3, turno.getFim());
        stmt.setInt(4, turno.getMinAgentes());
        if (turno.getMaxAgentes() != null) {
            stmt.setInt(5, turno.getMaxAgentes());
        } else {
            stmt.setNull(5, Types.INTEGER);
        }
        stmt.setString(6, turno.getObservacao());
        stmt.setBoolean(7, turno.isAtivo());
        return 8;
    }

    /**
     * Percorre o ResultSet do JOIN turno+tipoTurno+agente+funcionario uma unica
     * vez, agrupando as linhas por escala_turno_id (a repeticao de turno vem do
     * fan-out do LEFT JOIN com escala_funcionario).
     */
    private Map<Integer, EscalaTurno> mapearComAgentes(ResultSet rs) throws SQLException {
        Map<Integer, EscalaTurno> turnos = new LinkedHashMap<>();
        while (rs.next()) {
            int turnoId = rs.getInt("et_id");
            EscalaTurno turno = turnos.computeIfAbsent(turnoId, id -> {
                try {
                    return mapearTurno(rs);
                } catch (SQLException e) {
                    throw new RepositoryException("Falha ao montar turno " + id, e);
                }
            });

            int agenteId = rs.getInt("ef_id");
            if (!rs.wasNull()) {
                turno.getAgentes().add(mapearAgente(rs, turno));
            }
        }
        return turnos;
    }

    private EscalaTurno mapearTurno(ResultSet rs) throws SQLException {
        TipoTurno tipoTurno = new TipoTurno();
        tipoTurno.setId(rs.getInt("et_tipo_turno_id"));
        tipoTurno.setNome(rs.getString("tt_nome"));
        tipoTurno.setHoraInicio(rs.getObject("tt_hora_inicio", LocalTime.class));
        tipoTurno.setDuracaoHoras(rs.getBigDecimal("tt_duracao_horas"));
        tipoTurno.setIntervaloDescansoHoras(rs.getBigDecimal("tt_intervalo_descanso_horas"));
        tipoTurno.setMinAgentes(rs.getInt("tt_min_agentes"));
        int tipoMaxAgentes = rs.getInt("tt_max_agentes");
        tipoTurno.setMaxAgentes(rs.wasNull() ? null : tipoMaxAgentes);
        tipoTurno.setContaBancoHoras(rs.getBoolean("tt_conta_banco_horas"));
        tipoTurno.setAtivo(rs.getBoolean("tt_ativo"));
        tipoTurno.setCriadoEm(rs.getObject("tt_criado_em", LocalDateTime.class));

        EscalaTurno turno = new EscalaTurno();
        turno.setId(rs.getInt("et_id"));
        turno.setTipoTurno(tipoTurno);
        turno.setInicio(rs.getObject("et_inicio", LocalDateTime.class));
        turno.setFim(rs.getObject("et_fim", LocalDateTime.class));
        turno.setMinAgentes(rs.getInt("et_min_agentes"));
        int turnoMaxAgentes = rs.getInt("et_max_agentes");
        turno.setMaxAgentes(rs.wasNull() ? null : turnoMaxAgentes);
        turno.setObservacao(rs.getString("et_observacao"));
        turno.setAtivo(rs.getBoolean("et_ativo"));
        turno.setCriadoEm(rs.getObject("et_criado_em", LocalDateTime.class));
        return turno;
    }

    /** Funcionario hidratado; coberturaDe fica "raso" (so com o id) — quem precisa dos detalhes busca por ele. */
    private EscalaFuncionario mapearAgente(ResultSet rs, EscalaTurno turno) throws SQLException {
        Funcionario funcionario = new Funcionario();
        funcionario.setId(rs.getInt("ef_funcionario_id"));
        funcionario.setNome(rs.getString("f_nome"));
        funcionario.setMatricula(rs.getString("f_matricula"));
        funcionario.setTelefone(rs.getString("f_telefone"));
        funcionario.setObservacoes(rs.getString("f_observacoes"));
        funcionario.setAtivo(rs.getBoolean("f_ativo"));
        funcionario.setCriadoEm(rs.getObject("f_criado_em", LocalDateTime.class));

        EscalaFuncionario agente = new EscalaFuncionario();
        agente.setId(rs.getInt("ef_id"));
        agente.setEscalaTurno(turno);
        agente.setFuncionario(funcionario);
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
}
