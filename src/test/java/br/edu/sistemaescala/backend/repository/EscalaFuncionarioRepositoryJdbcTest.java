package br.edu.sistemaescala.backend.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import br.edu.sistemaescala.backend.dao.BancoInicializador;
import br.edu.sistemaescala.backend.dao.ConexaoBanco;
import br.edu.sistemaescala.backend.model.EscalaFuncionario;
import br.edu.sistemaescala.backend.model.EscalaTurno;
import br.edu.sistemaescala.backend.model.Funcionario;
import br.edu.sistemaescala.backend.repository.jdbc.EscalaFuncionarioRepositoryJdbc;

/**
 * Testes de integracao contra o H2 real. Fixtures (funcionario, tipo_turno,
 * escala_turno) sao criadas com SQL puro e limpas na ordem que respeita as
 * chaves estrangeiras.
 */
class EscalaFuncionarioRepositoryJdbcTest {

    private static final EscalaFuncionarioRepository REPOSITORIO = new EscalaFuncionarioRepositoryJdbc();

    private static final List<Integer> ESCALA_FUNCIONARIO_IDS = new ArrayList<>();
    private static final List<Integer> ESCALA_TURNO_IDS = new ArrayList<>();
    private static final List<Integer> TIPO_TURNO_IDS = new ArrayList<>();
    private static final List<Integer> FUNCIONARIO_IDS = new ArrayList<>();

    @BeforeAll
    static void prepararBanco() {
        BancoInicializador.inicializar();
    }

    @AfterAll
    static void limparBanco() throws SQLException {
        try (Connection conexao = ConexaoBanco.getConnection()) {
            // ordem reversa: uma cobertura referencia o titular via cobertura_de (FK auto-referenciada)
            for (int i = ESCALA_FUNCIONARIO_IDS.size() - 1; i >= 0; i--) {
                executar(conexao, "DELETE FROM escala_funcionario WHERE id = ?", ESCALA_FUNCIONARIO_IDS.get(i));
            }
            for (int id : ESCALA_TURNO_IDS) {
                executar(conexao, "DELETE FROM escala_turno WHERE id = ?", id);
            }
            for (int id : TIPO_TURNO_IDS) {
                executar(conexao, "DELETE FROM tipo_turno WHERE id = ?", id);
            }
            for (int id : FUNCIONARIO_IDS) {
                executar(conexao, "DELETE FROM funcionario WHERE id = ?", id);
            }
        }
    }

    @Test
    void inserirEListarPorTurnoTrazOAgenteComOFuncionarioHidratadoERemoverOTiraDaLista() {
        int tipoTurnoId = inserirTipoTurno("TESTE-EF-TIPO-A");
        int funcionarioId = inserirFuncionario("TESTE-EF-FUNC-A");
        int escalaTurnoId = inserirEscalaTurno(tipoTurnoId,
                LocalDateTime.of(2026, 3, 10, 8, 0), LocalDateTime.of(2026, 3, 11, 8, 0));
        TIPO_TURNO_IDS.add(tipoTurnoId);
        FUNCIONARIO_IDS.add(funcionarioId);
        ESCALA_TURNO_IDS.add(escalaTurnoId);

        EscalaTurno escalaTurno = new EscalaTurno();
        escalaTurno.setId(escalaTurnoId);
        Funcionario funcionario = new Funcionario();
        funcionario.setId(funcionarioId);

        EscalaFuncionario alocacao = new EscalaFuncionario();
        alocacao.setEscalaTurno(escalaTurno);
        alocacao.setFuncionario(funcionario);

        REPOSITORIO.inserir(alocacao);
        assertNotNull(alocacao.getId());
        ESCALA_FUNCIONARIO_IDS.add(alocacao.getId());

        List<EscalaFuncionario> agentesDoTurno = REPOSITORIO.listarPorTurno(escalaTurnoId);
        assertEquals(1, agentesDoTurno.size());
        assertEquals("TESTE-EF-FUNC-A", agentesDoTurno.get(0).getFuncionario().getMatricula());

        REPOSITORIO.remover(alocacao.getId());
        assertTrue(REPOSITORIO.listarPorTurno(escalaTurnoId).isEmpty(),
                "apos remover, o turno nao deve mais listar o agente");
    }

    @Test
    void listarPorFuncionarioTrazSoOsPlantoesCujoTurnoComecaNoIntervaloPedido() {
        int tipoTurnoId = inserirTipoTurno("TESTE-EF-TIPO-B");
        int funcionarioId = inserirFuncionario("TESTE-EF-FUNC-B");
        int turnoAbrilUmId = inserirEscalaTurno(tipoTurnoId,
                LocalDateTime.of(2026, 4, 5, 8, 0), LocalDateTime.of(2026, 4, 6, 8, 0));
        int turnoAbrilDoisId = inserirEscalaTurno(tipoTurnoId,
                LocalDateTime.of(2026, 4, 15, 8, 0), LocalDateTime.of(2026, 4, 16, 8, 0));
        int turnoMaioId = inserirEscalaTurno(tipoTurnoId,
                LocalDateTime.of(2026, 5, 5, 8, 0), LocalDateTime.of(2026, 5, 6, 8, 0));

        int efAbrilUmId = inserirEscalaFuncionario(turnoAbrilUmId, funcionarioId, null);
        int efAbrilDoisId = inserirEscalaFuncionario(turnoAbrilDoisId, funcionarioId, null);
        int efMaioId = inserirEscalaFuncionario(turnoMaioId, funcionarioId, null);

        TIPO_TURNO_IDS.add(tipoTurnoId);
        FUNCIONARIO_IDS.add(funcionarioId);
        ESCALA_TURNO_IDS.add(turnoAbrilUmId);
        ESCALA_TURNO_IDS.add(turnoAbrilDoisId);
        ESCALA_TURNO_IDS.add(turnoMaioId);
        ESCALA_FUNCIONARIO_IDS.add(efAbrilUmId);
        ESCALA_FUNCIONARIO_IDS.add(efAbrilDoisId);
        ESCALA_FUNCIONARIO_IDS.add(efMaioId);

        List<EscalaFuncionario> plantoesDeAbril = REPOSITORIO.listarPorFuncionario(funcionarioId,
                LocalDateTime.of(2026, 4, 1, 0, 0), LocalDateTime.of(2026, 5, 1, 0, 0));
        assertEquals(2, plantoesDeAbril.size());
        assertTrue(plantoesDeAbril.stream().allMatch(p ->
                p.getEscalaTurno().getInicio().getMonthValue() == 4));

        List<EscalaFuncionario> plantoesDeMaio = REPOSITORIO.listarPorFuncionario(funcionarioId,
                LocalDateTime.of(2026, 5, 1, 0, 0), LocalDateTime.of(2026, 6, 1, 0, 0));
        assertEquals(1, plantoesDeMaio.size());
        assertEquals(efMaioId, plantoesDeMaio.get(0).getId());
    }

    @Test
    void buscarCoberturasDoMesTrazSoAsAlocacoesQueSubstituemOutroAgente() {
        int tipoTurnoId = inserirTipoTurno("TESTE-EF-TIPO-C");
        int funcionarioTitularId = inserirFuncionario("TESTE-EF-FUNC-TITULAR-C");
        int funcionarioCoberturaId = inserirFuncionario("TESTE-EF-FUNC-COBERTURA-C");
        int escalaTurnoId = inserirEscalaTurno(tipoTurnoId,
                LocalDateTime.of(2026, 10, 10, 8, 0), LocalDateTime.of(2026, 10, 11, 8, 0));

        int titularId = inserirEscalaFuncionario(escalaTurnoId, funcionarioTitularId, null);
        int coberturaId = inserirEscalaFuncionario(escalaTurnoId, funcionarioCoberturaId, titularId);

        TIPO_TURNO_IDS.add(tipoTurnoId);
        FUNCIONARIO_IDS.add(funcionarioTitularId);
        FUNCIONARIO_IDS.add(funcionarioCoberturaId);
        ESCALA_TURNO_IDS.add(escalaTurnoId);
        ESCALA_FUNCIONARIO_IDS.add(titularId);
        ESCALA_FUNCIONARIO_IDS.add(coberturaId);

        List<EscalaFuncionario> coberturasDeOutubro = REPOSITORIO.buscarCoberturasDoMes(YearMonth.of(2026, 10));
        assertEquals(1, coberturasDeOutubro.size());
        EscalaFuncionario cobertura = coberturasDeOutubro.get(0);
        assertEquals(coberturaId, cobertura.getId());
        assertEquals("TESTE-EF-FUNC-COBERTURA-C", cobertura.getFuncionario().getMatricula());
        assertNotNull(cobertura.getCoberturaDe());
        assertEquals(titularId, cobertura.getCoberturaDe().getId());

        assertTrue(REPOSITORIO.buscarCoberturasDoMes(YearMonth.of(2026, 11)).isEmpty());
    }

    private int inserirTipoTurno(String nome) {
        try (Connection conexao = ConexaoBanco.getConnection()) {
            return inserirRetornandoId(conexao,
                    "INSERT INTO tipo_turno (nome, hora_inicio, duracao_horas, intervalo_descanso_horas, min_agentes) " +
                            "VALUES (?, ?, ?, ?, ?)",
                    nome, java.sql.Time.valueOf("08:00:00"), 24, 72, 1);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private int inserirFuncionario(String matricula) {
        try (Connection conexao = ConexaoBanco.getConnection()) {
            return inserirRetornandoId(conexao,
                    "INSERT INTO funcionario (nome, matricula, ativo) VALUES (?, ?, TRUE)",
                    matricula, matricula);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private int inserirEscalaTurno(int tipoTurnoId, LocalDateTime inicio, LocalDateTime fim) {
        try (Connection conexao = ConexaoBanco.getConnection()) {
            return inserirRetornandoId(conexao,
                    "INSERT INTO escala_turno (tipo_turno_id, inicio, fim, min_agentes) VALUES (?, ?, ?, ?)",
                    tipoTurnoId, java.sql.Timestamp.valueOf(inicio), java.sql.Timestamp.valueOf(fim), 1);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private int inserirEscalaFuncionario(int escalaTurnoId, int funcionarioId, Integer coberturaDe) {
        try (Connection conexao = ConexaoBanco.getConnection()) {
            return inserirRetornandoId(conexao,
                    "INSERT INTO escala_funcionario (escala_turno_id, funcionario_id, cobertura_de) VALUES (?, ?, ?)",
                    escalaTurnoId, funcionarioId, coberturaDe);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private static int inserirRetornandoId(Connection conexao, String sql, Object... parametros) throws SQLException {
        try (PreparedStatement stmt = conexao.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (int i = 0; i < parametros.length; i++) {
                stmt.setObject(i + 1, parametros[i]);
            }
            stmt.executeUpdate();
            try (ResultSet chaves = stmt.getGeneratedKeys()) {
                chaves.next();
                return chaves.getInt(1);
            }
        }
    }

    private static void executar(Connection conexao, String sql, Object parametro) throws SQLException {
        try (PreparedStatement stmt = conexao.prepareStatement(sql)) {
            stmt.setObject(1, parametro);
            stmt.executeUpdate();
        }
    }
}
