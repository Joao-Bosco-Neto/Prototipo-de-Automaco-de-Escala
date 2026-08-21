package br.edu.sistemaescala.backend.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import java.util.Optional;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import br.edu.sistemaescala.backend.dao.BancoInicializador;
import br.edu.sistemaescala.backend.dao.ConexaoBanco;
import br.edu.sistemaescala.backend.model.EscalaFuncionario;
import br.edu.sistemaescala.backend.model.EscalaTurno;
import br.edu.sistemaescala.backend.model.TipoTurno;
import br.edu.sistemaescala.backend.repository.jdbc.EscalaTurnoRepositoryJdbc;

/**
 * Testes de integracao contra o H2 real. Fixtures sao criadas com SQL puro
 * (funcionario, tipo_turno, escala_funcionario) e limpas na ordem que
 * respeita as chaves estrangeiras.
 */
class EscalaTurnoRepositoryJdbcTest {

    private static final EscalaTurnoRepository REPOSITORIO = new EscalaTurnoRepositoryJdbc();

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
    void salvarInsereQuandoIdNuloEAtualizaQuandoIdPreenchido() {
        int tipoTurnoId = inserirTipoTurno("TESTE-ET-TIPO-A");

        TipoTurno tipoTurno = new TipoTurno();
        tipoTurno.setId(tipoTurnoId);

        EscalaTurno turno = new EscalaTurno();
        turno.setTipoTurno(tipoTurno);
        turno.setInicio(LocalDateTime.of(2026, 5, 10, 8, 0));
        turno.setFim(LocalDateTime.of(2026, 5, 11, 8, 0));
        turno.setMinAgentes(1);
        turno.setObservacao("original");

        REPOSITORIO.salvar(turno);
        ESCALA_TURNO_IDS.add(turno.getId());
        assertNotNull(turno.getId());

        turno.setObservacao("editado");
        REPOSITORIO.salvar(turno);

        EscalaTurno buscado = REPOSITORIO.buscarPorId(turno.getId()).orElseThrow();
        assertEquals("editado", buscado.getObservacao());
        assertEquals(tipoTurnoId, buscado.getTipoTurno().getId());
    }

    @Test
    void buscarPorPeriodoTrazTurnosComAgentesECoberturasHidratados() {
        int tipoTurnoId = inserirTipoTurno("TESTE-ET-TIPO-B");
        int funcionarioTitularId = inserirFuncionario("TESTE-ET-FUNC-TITULAR");
        int funcionarioCoberturaId = inserirFuncionario("TESTE-ET-FUNC-COBERTURA");

        int turnoDentroId = inserirEscalaTurno(tipoTurnoId,
                LocalDateTime.of(2026, 6, 10, 8, 0), LocalDateTime.of(2026, 6, 11, 8, 0));
        int turnoForaId = inserirEscalaTurno(tipoTurnoId,
                LocalDateTime.of(2026, 7, 10, 8, 0), LocalDateTime.of(2026, 7, 11, 8, 0));

        int titularId = inserirEscalaFuncionario(turnoDentroId, funcionarioTitularId, null);
        int coberturaId = inserirEscalaFuncionario(turnoDentroId, funcionarioCoberturaId, titularId);
        int agenteForaId = inserirEscalaFuncionario(turnoForaId, funcionarioTitularId, null);

        ESCALA_FUNCIONARIO_IDS.add(titularId);
        ESCALA_FUNCIONARIO_IDS.add(coberturaId);
        ESCALA_FUNCIONARIO_IDS.add(agenteForaId);
        ESCALA_TURNO_IDS.add(turnoDentroId);
        ESCALA_TURNO_IDS.add(turnoForaId);
        TIPO_TURNO_IDS.add(tipoTurnoId);
        FUNCIONARIO_IDS.add(funcionarioTitularId);
        FUNCIONARIO_IDS.add(funcionarioCoberturaId);

        List<EscalaTurno> turnos = REPOSITORIO.buscarPorPeriodo(
                LocalDateTime.of(2026, 6, 1, 0, 0), LocalDateTime.of(2026, 7, 1, 0, 0));

        assertEquals(1, turnos.size(), "so o turno de junho deve entrar no periodo pedido");
        EscalaTurno turno = turnos.get(0);
        assertEquals(turnoDentroId, turno.getId());
        assertEquals(2, turno.getAgentes().size(), "o turno deve trazer os dois agentes ja hidratados");

        EscalaFuncionario titular = turno.getAgentes().stream()
                .filter(a -> a.getId() == titularId).findFirst().orElseThrow();
        assertEquals("TESTE-ET-FUNC-TITULAR", titular.getFuncionario().getMatricula());
        assertEquals(null, titular.getCoberturaDe());

        EscalaFuncionario cobertura = turno.getAgentes().stream()
                .filter(a -> a.getId() == coberturaId).findFirst().orElseThrow();
        assertEquals("TESTE-ET-FUNC-COBERTURA", cobertura.getFuncionario().getMatricula());
        assertNotNull(cobertura.getCoberturaDe());
        assertEquals(titularId, cobertura.getCoberturaDe().getId());
    }

    @Test
    void removerPorMesApagaTurnosDoMesEEmCascataSuasAlocacoes() {
        int tipoTurnoId = inserirTipoTurno("TESTE-ET-TIPO-C");
        int funcionarioId = inserirFuncionario("TESTE-ET-FUNC-LIMPAR");

        int turnoNoMesId = inserirEscalaTurno(tipoTurnoId,
                LocalDateTime.of(2026, 8, 5, 8, 0), LocalDateTime.of(2026, 8, 6, 8, 0));
        int turnoForaDoMesId = inserirEscalaTurno(tipoTurnoId,
                LocalDateTime.of(2026, 9, 5, 8, 0), LocalDateTime.of(2026, 9, 6, 8, 0));
        int escalaFuncionarioId = inserirEscalaFuncionario(turnoNoMesId, funcionarioId, null);

        ESCALA_TURNO_IDS.add(turnoForaDoMesId);
        TIPO_TURNO_IDS.add(tipoTurnoId);
        FUNCIONARIO_IDS.add(funcionarioId);

        REPOSITORIO.removerPorMes(YearMonth.of(2026, 8));

        assertTrue(REPOSITORIO.buscarPorId(turnoNoMesId).isEmpty(), "turno do mes limpo deve ter sido removido");
        assertFalse(escalaFuncionarioExiste(escalaFuncionarioId),
                "a alocacao do turno removido deve cair em cascata (ON DELETE CASCADE)");

        Optional<EscalaTurno> aindaExiste = REPOSITORIO.buscarPorId(turnoForaDoMesId);
        assertTrue(aindaExiste.isPresent(), "turno de outro mes nao pode ser afetado por 'Limpar mes'");
    }

    private boolean escalaFuncionarioExiste(int id) {
        try (Connection conexao = ConexaoBanco.getConnection();
             PreparedStatement stmt = conexao.prepareStatement("SELECT 1 FROM escala_funcionario WHERE id = ?")) {
            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
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
