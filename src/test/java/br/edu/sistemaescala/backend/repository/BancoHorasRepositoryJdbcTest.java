package br.edu.sistemaescala.backend.repository;

import br.edu.sistemaescala.backend.dao.BancoInicializador;
import br.edu.sistemaescala.backend.dao.ConexaoBanco;
import br.edu.sistemaescala.backend.repository.jdbc.BancoHorasRepositoryJdbc;
import br.edu.sistemaescala.backend.service.BancoHorasListagemItem;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Testes de integração contra o H2 real. Cada teste monta e limpa os próprios
 * dados, no padrão de {@link LancamentoHorasRepositoryJdbcTest}.
 *
 * Cenário (mês de referência: junho/2026):
 *   - titular A: um plantão próprio (ef1), um plantão coberto por B (ef2)
 *   - substituto B: cobre o plantão de A (ef3, cobertura_de = ef2)
 *   - lançamentos: A +480 em junho, A -240 em maio, B +240 em junho
 */
class BancoHorasRepositoryJdbcTest {

    private static final BancoHorasRepository repositorio = new BancoHorasRepositoryJdbc();

    private static int idA;
    private static int idB;
    private static int idC;
    private static int tipoTurnoId;
    private static int turno1Id;
    private static int turno2Id;
    private static int ef1Id;
    private static int ef2Id;
    private static int ef3Id;

    @BeforeAll
    static void prepararBanco() throws SQLException {
        BancoInicializador.inicializar();

        try (Connection c = ConexaoBanco.getConnection()) {
            idA = id(c, "INSERT INTO funcionario (nome, matricula) VALUES (?, ?)",
                    "AAA Banco Horas Titular", "TESTE-BH-A");
            idB = id(c, "INSERT INTO funcionario (nome, matricula) VALUES (?, ?)",
                    "BBB Banco Horas Substituto", "TESTE-BH-B");
            idC = id(c, "INSERT INTO funcionario (nome, matricula, ativo) VALUES (?, ?, FALSE)",
                    "CCC Banco Horas Inativo", "TESTE-BH-C");

            tipoTurnoId = id(c, "INSERT INTO tipo_turno (nome, hora_inicio, duracao_horas, "
                            + "intervalo_descanso_horas, min_agentes) VALUES (?, ?, ?, ?, ?)",
                    "Turno BH teste", java.sql.Time.valueOf("08:00:00"), 24, 72, 1);

            turno1Id = id(c, "INSERT INTO escala_turno (tipo_turno_id, inicio, fim, min_agentes) VALUES (?, ?, ?, ?)",
                    tipoTurnoId, java.sql.Timestamp.valueOf("2026-06-05 08:00:00"),
                    java.sql.Timestamp.valueOf("2026-06-06 08:00:00"), 1);
            turno2Id = id(c, "INSERT INTO escala_turno (tipo_turno_id, inicio, fim, min_agentes) VALUES (?, ?, ?, ?)",
                    tipoTurnoId, java.sql.Timestamp.valueOf("2026-06-15 08:00:00"),
                    java.sql.Timestamp.valueOf("2026-06-16 08:00:00"), 1);

            ef1Id = id(c, "INSERT INTO escala_funcionario (escala_turno_id, funcionario_id) VALUES (?, ?)",
                    turno1Id, idA);
            ef2Id = id(c, "INSERT INTO escala_funcionario (escala_turno_id, funcionario_id) VALUES (?, ?)",
                    turno2Id, idA);
            ef3Id = id(c, "INSERT INTO escala_funcionario (escala_turno_id, funcionario_id, cobertura_de) "
                            + "VALUES (?, ?, ?)",
                    turno2Id, idB, ef2Id);

            exec(c, "INSERT INTO lancamento_horas (funcionario_id, data_referencia, minutos, tipo) "
                    + "VALUES (?, DATE '2026-06-10', 480, 'ajuste_manual')", idA);
            exec(c, "INSERT INTO lancamento_horas (funcionario_id, data_referencia, minutos, tipo) "
                    + "VALUES (?, DATE '2026-05-10', -240, 'ajuste_manual')", idA);
            exec(c, "INSERT INTO lancamento_horas (funcionario_id, data_referencia, minutos, tipo) "
                    + "VALUES (?, DATE '2026-06-15', 240, 'credito_cobertura')", idB);
        }
    }

    @AfterAll
    static void limparBanco() throws SQLException {
        try (Connection c = ConexaoBanco.getConnection()) {
            exec(c, "DELETE FROM lancamento_horas WHERE funcionario_id IN (?, ?)", idA, idB);
            exec(c, "DELETE FROM escala_funcionario WHERE id IN (?, ?, ?)", ef3Id, ef2Id, ef1Id);
            exec(c, "DELETE FROM escala_turno WHERE id IN (?, ?)", turno1Id, turno2Id);
            exec(c, "DELETE FROM tipo_turno WHERE id = ?", tipoTurnoId);
            exec(c, "DELETE FROM funcionario WHERE id IN (?, ?, ?)", idA, idB, idC);
        }
    }

    @Test
    void metricasESaldoRecortadosPeloMes() {
        BancoHorasListagemItem a = buscar(repositorio.listarMensal(YearMonth.of(2026, 6)), idA);
        assertEquals(1, a.plantoesCumpridos(), "ef1 conta; ef2 não conta por ter sido coberto");
        assertEquals(0, a.coberturasFeitas());
        assertEquals(1, a.plantoesCobertos(), "ef2 foi assumido por B");
        assertEquals(480, a.saldoMinutos(), "só o lançamento de junho; o -240 de maio fica de fora");

        BancoHorasListagemItem b = buscar(repositorio.listarMensal(YearMonth.of(2026, 6)), idB);
        assertEquals(0, b.plantoesCumpridos(), "ef3 é cobertura, não plantão próprio");
        assertEquals(1, b.coberturasFeitas());
        assertEquals(0, b.plantoesCobertos());
        assertEquals(240, b.saldoMinutos());
    }

    @Test
    void semMesSelecionadoSomaTodoOHistorico() {
        BancoHorasListagemItem a = buscar(repositorio.listarMensal(null), idA);
        assertEquals(1, a.plantoesCumpridos());
        assertEquals(1, a.plantoesCobertos());
        assertEquals(240, a.saldoMinutos(), "480 de junho - 240 de maio");
    }

    @Test
    void listagemTrazSoFuncionariosAtivos() {
        List<BancoHorasListagemItem> itens = repositorio.listarMensal(YearMonth.of(2026, 6));
        assertEquals(0, itens.stream().filter(i -> i.funcionarioId() == idC).count(),
                "funcionário inativo não deve aparecer na listagem");
    }

    @Test
    void mesSemMovimentoZeraMetricasESaldo() {
        BancoHorasListagemItem a = buscar(repositorio.listarMensal(YearMonth.of(2026, 9)), idA);
        assertEquals(0, a.plantoesCumpridos());
        assertEquals(0, a.coberturasFeitas());
        assertEquals(0, a.plantoesCobertos());
        assertEquals(0, a.saldoMinutos(), "nenhum lançamento em setembro");
    }

    private static BancoHorasListagemItem buscar(List<BancoHorasListagemItem> itens, int funcionarioId) {
        return itens.stream()
                .filter(i -> i.funcionarioId() == funcionarioId)
                .findFirst()
                .orElseThrow(() -> new AssertionError("funcionário " + funcionarioId + " ausente da listagem"));
    }

    private static int id(Connection c, String sql, Object... params) throws SQLException {
        try (PreparedStatement stmt = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (int i = 0; i < params.length; i++) {
                stmt.setObject(i + 1, params[i]);
            }
            stmt.executeUpdate();
            try (ResultSet chaves = stmt.getGeneratedKeys()) {
                chaves.next();
                return chaves.getInt(1);
            }
        }
    }

    private static void exec(Connection c, String sql, Object... params) throws SQLException {
        try (PreparedStatement stmt = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                stmt.setObject(i + 1, params[i]);
            }
            stmt.executeUpdate();
        }
    }
}
