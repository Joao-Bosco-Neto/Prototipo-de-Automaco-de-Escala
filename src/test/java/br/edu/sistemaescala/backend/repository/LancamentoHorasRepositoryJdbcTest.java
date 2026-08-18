package br.edu.sistemaescala.backend.repository;

import br.edu.sistemaescala.backend.dao.BancoInicializador;
import br.edu.sistemaescala.backend.dao.ConexaoBanco;
import br.edu.sistemaescala.backend.model.LancamentoHoras;
import br.edu.sistemaescala.backend.model.TipoLancamento;
import br.edu.sistemaescala.backend.repository.jdbc.LancamentoHorasRepositoryJdbc;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testes de integracao contra o H2 real (mesmo banco usado pela aplicacao).
 * Cada teste cria e limpa os proprios dados para nao interferir no restante
 * da suíte nem no banco de desenvolvimento.
 */
class LancamentoHorasRepositoryJdbcTest {

    private static final LancamentoHorasRepository repositorio = new LancamentoHorasRepositoryJdbc();

    private static int funcionarioId;
    private static int tipoTurnoId;
    private static int escalaTurnoId;
    private static int escalaFuncionarioId;

    @BeforeAll
    static void prepararBanco() throws SQLException {
        BancoInicializador.inicializar();

        try (Connection conexao = ConexaoBanco.getConnection()) {
            funcionarioId = inserirRetornandoId(conexao,
                    "INSERT INTO funcionario (nome, matricula) VALUES (?, ?)",
                    "Teste Lancamento Horas", "TESTE-LANC-HORAS-001");

            tipoTurnoId = inserirRetornandoId(conexao,
                    "INSERT INTO tipo_turno (nome, hora_inicio, duracao_horas, intervalo_descanso_horas, min_agentes) " +
                            "VALUES (?, ?, ?, ?, ?)",
                    "Turno de teste", java.sql.Time.valueOf("08:00:00"), 24, 72, 1);

            escalaTurnoId = inserirRetornandoId(conexao,
                    "INSERT INTO escala_turno (tipo_turno_id, inicio, fim, min_agentes) VALUES (?, ?, ?, ?)",
                    tipoTurnoId, java.sql.Timestamp.valueOf("2026-01-01 08:00:00"),
                    java.sql.Timestamp.valueOf("2026-01-02 08:00:00"), 1);

            escalaFuncionarioId = inserirRetornandoId(conexao,
                    "INSERT INTO escala_funcionario (escala_turno_id, funcionario_id) VALUES (?, ?)",
                    escalaTurnoId, funcionarioId);
        }
    }

    @AfterAll
    static void limparBanco() throws SQLException {
        try (Connection conexao = ConexaoBanco.getConnection()) {
            executar(conexao, "DELETE FROM lancamento_horas WHERE funcionario_id = ?", funcionarioId);
            executar(conexao, "DELETE FROM escala_funcionario WHERE id = ?", escalaFuncionarioId);
            executar(conexao, "DELETE FROM escala_turno WHERE id = ?", escalaTurnoId);
            executar(conexao, "DELETE FROM tipo_turno WHERE id = ?", tipoTurnoId);
            executar(conexao, "DELETE FROM funcionario WHERE id = ?", funcionarioId);
        }
    }

    @Test
    void salvarPreencheIdGerado() {
        LancamentoHoras lancamento = new LancamentoHoras(
                funcionarioId, null, LocalDate.of(2026, 1, 5), 480,
                TipoLancamento.AJUSTE_MANUAL, "Ajuste de teste");

        repositorio.salvar(lancamento);

        assertNotNull(lancamento.getId());
    }

    @Test
    void extratoVemOrdenadoPorDataESaldoSomaSoOPeriodoPedido() {
        salvar(LocalDate.of(2026, 2, 10), 480, TipoLancamento.CREDITO_EXTRA, "dentro do periodo, mais recente");
        salvar(LocalDate.of(2026, 2, 1), 240, TipoLancamento.CREDITO_COBERTURA, "dentro do periodo, mais antigo");
        salvar(LocalDate.of(2026, 3, 1), 1000, TipoLancamento.CREDITO_EXTRA, "fora do periodo, nao deve entrar");

        List<LancamentoHoras> extrato = repositorio.buscarExtrato(
                funcionarioId, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28));

        assertEquals(2, extrato.size());
        assertTrue(extrato.get(0).getDataReferencia().isBefore(extrato.get(1).getDataReferencia()),
                "extrato deveria vir ordenado por data, mais antigo primeiro");

        int saldo = repositorio.somarSaldoMinutos(
                funcionarioId, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28));
        assertEquals(720, saldo, "saldo do periodo deve somar so os lancamentos de fevereiro");
    }

    @Test
    void somarSaldoRetornaZeroQuandoNaoHaLancamentoNoPeriodo() {
        int saldo = repositorio.somarSaldoMinutos(
                funcionarioId, LocalDate.of(2099, 1, 1), LocalDate.of(2099, 1, 31));
        assertEquals(0, saldo);
    }

    @Test
    void removerPorEscalaFuncionarioIdEstornaSoOsLancamentosVinculados() {
        LancamentoHoras vinculado = new LancamentoHoras(
                funcionarioId, escalaFuncionarioId, LocalDate.of(2026, 4, 1), 1440,
                TipoLancamento.CREDITO_COBERTURA, "cobertura a ser estornada");
        repositorio.salvar(vinculado);

        LancamentoHoras avulso = new LancamentoHoras(
                funcionarioId, null, LocalDate.of(2026, 4, 2), 60,
                TipoLancamento.AJUSTE_MANUAL, "lancamento avulso, nao deve ser afetado");
        repositorio.salvar(avulso);

        int removidos = repositorio.removerPorEscalaFuncionarioId(escalaFuncionarioId);
        assertEquals(1, removidos);

        List<LancamentoHoras> extrato = repositorio.buscarExtrato(
                funcionarioId, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30));
        assertEquals(1, extrato.size());
        assertEquals(avulso.getId(), extrato.get(0).getId());
    }

    private void salvar(LocalDate data, int minutos, TipoLancamento tipo, String descricao) {
        repositorio.salvar(new LancamentoHoras(funcionarioId, null, data, minutos, tipo, descricao));
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
