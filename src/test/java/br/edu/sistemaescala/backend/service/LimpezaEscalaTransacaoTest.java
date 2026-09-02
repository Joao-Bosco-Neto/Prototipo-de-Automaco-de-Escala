package br.edu.sistemaescala.backend.service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.edu.sistemaescala.backend.dao.BancoInicializador;
import br.edu.sistemaescala.backend.dao.ConexaoBanco;
import br.edu.sistemaescala.backend.dao.TransacaoUtil;
import br.edu.sistemaescala.backend.model.EscalaTurno;
import br.edu.sistemaescala.backend.repository.EscalaTurnoRepository;
import br.edu.sistemaescala.backend.repository.jdbc.EscalaTurnoRepositoryJdbc;

/**
 * Testes de integração da limpeza de mês contra o H2 real (Issue #44).
 *
 * <p>Aqui o {@link TransacaoUtil} não é dublê: é ele mesmo, abrindo transação
 * de verdade. É o único jeito de provar o que a issue pede — que uma falha no
 * meio da remoção não deixe nada apagado —, porque o rollback acontece no
 * banco, não no código do service.</p>
 *
 * <p>A conferência final é feita numa Connection nova, depois que a transação
 * já foi desfeita e fechada.</p>
 */
class LimpezaEscalaTransacaoTest {

    private static final YearMonth AGOSTO = YearMonth.of(2031, 8);
    private static final LocalDateTime DIA_1 = LocalDateTime.of(2031, 8, 1, 8, 0);
    private static final LocalDateTime DIA_2 = LocalDateTime.of(2031, 8, 2, 8, 0);

    private static final EscalaTurnoRepository REPOSITORIO = new EscalaTurnoRepositoryJdbc();

    private int tipoTurnoId;
    private int funcionarioId;

    @BeforeAll
    static void prepararBanco() {
        BancoInicializador.inicializar();
    }

    @BeforeEach
    void semearMes() throws SQLException {
        limparFixtures();
        try (Connection conexao = ConexaoBanco.getConnection()) {
            tipoTurnoId = inserir(conexao,
                    "INSERT INTO tipo_turno (nome, hora_inicio, duracao_horas, intervalo_descanso_horas,"
                    + " min_agentes) VALUES ('TESTE-LIMPEZA-TIPO', TIME '08:00:00', 24, 72, 1)");
            funcionarioId = inserir(conexao,
                    "INSERT INTO funcionario (nome, matricula) VALUES ('TESTE-LIMPEZA', 'TESTE-LIMPEZA-1')");

            int turno1 = inserirTurno(conexao, DIA_1);
            int turno2 = inserirTurno(conexao, DIA_2);
            int alocacao1 = inserirAlocacao(conexao, turno1);
            inserirAlocacao(conexao, turno2);

            // Um lançamento preso à alocação, para provar que a cascata chega
            // até o banco de horas — e que o rollback também o traz de volta.
            executar(conexao,
                    "INSERT INTO lancamento_horas (funcionario_id, escala_funcionario_id, data_referencia,"
                    + " minutos, tipo) VALUES (?, ?, DATE '2031-08-01', 60, 'credito_cobertura')",
                    funcionarioId, alocacao1);
        }
    }

    @AfterEach
    void limparDepois() throws SQLException {
        limparFixtures();
    }

    @Test
    void limpezaRemoveTurnosAlocacoesELancamentosEmCascata() {
        LimpezaEscalaService servico = new LimpezaEscalaServiceImpl(REPOSITORIO, new LogSegurancaFake());

        ResultadoLimpeza resultado = servico.limparMes(AGOSTO);

        assertTrue(resultado.limpo());
        assertEquals(2, resultado.turnosRemovidos());
        assertEquals(2, resultado.alocacoesRemovidas());

        assertEquals(0, contar("escala_turno", "inicio >= ? AND inicio < ?"));
        assertEquals(0, contarAlocacoesDoFuncionario());
        assertEquals(0, contarLancamentosDoFuncionario());
    }

    @Test
    void falhaNoMeioDaRemocaoNaoDeixaNadaApagado() {
        // O DELETE roda e, logo depois, algo estoura dentro da mesma transação:
        // é o cenário de "falhou no meio" que a issue pede.
        EscalaTurnoRepository repositorioQueFalhaDepoisDeApagar = new EscalaTurnoRepository() {
            @Override
            public void removerPorMes(YearMonth mes, Connection conexao) {
                REPOSITORIO.removerPorMes(mes, conexao);
                throw new IllegalStateException("falha simulada depois do DELETE");
            }

            @Override
            public List<EscalaTurno> buscarPorPeriodo(LocalDateTime inicio, LocalDateTime fim) {
                return REPOSITORIO.buscarPorPeriodo(inicio, fim);
            }

            @Override
            public Optional<EscalaTurno> buscarPorId(int id) {
                return REPOSITORIO.buscarPorId(id);
            }

            @Override
            public EscalaTurno salvar(EscalaTurno turno) {
                return REPOSITORIO.salvar(turno);
            }

            @Override
            public EscalaTurno salvar(EscalaTurno turno, Connection conexao) {
                return REPOSITORIO.salvar(turno, conexao);
            }

            @Override
            public void removerPorMes(YearMonth mes) {
                REPOSITORIO.removerPorMes(mes);
            }

            @Override
            public List<PlantaoDoDiaItem> resumirPlantoesDoDia(LocalDate dia) {
                return REPOSITORIO.resumirPlantoesDoDia(dia);
            }

            @Override
            public int contarDiasComEfetivoIncompleto(YearMonth mes) {
                return REPOSITORIO.contarDiasComEfetivoIncompleto(mes);
            }
        };

        LimpezaEscalaService servico =
                new LimpezaEscalaServiceImpl(repositorioQueFalhaDepoisDeApagar, new LogSegurancaFake());

        assertThrows(IllegalStateException.class, () -> servico.limparMes(AGOSTO));

        // Conferido numa conexão nova: a transação já foi desfeita e fechada.
        assertEquals(2, contar("escala_turno", "inicio >= ? AND inicio < ?"),
                "O rollback deveria ter devolvido os turnos");
        assertEquals(2, contarAlocacoesDoFuncionario(),
                "O rollback deveria ter devolvido as alocações");
        assertEquals(1, contarLancamentosDoFuncionario(),
                "O rollback deveria ter devolvido o lançamento de banco de horas");
    }

    // -----------------------------------------------------------------
    // Fixtures
    // -----------------------------------------------------------------

    private void limparFixtures() throws SQLException {
        try (Connection conexao = ConexaoBanco.getConnection()) {
            executar(conexao, "DELETE FROM lancamento_horas WHERE funcionario_id IN"
                    + " (SELECT id FROM funcionario WHERE matricula = 'TESTE-LIMPEZA-1')");
            executar(conexao, "DELETE FROM escala_funcionario WHERE funcionario_id IN"
                    + " (SELECT id FROM funcionario WHERE matricula = 'TESTE-LIMPEZA-1')");
            executar(conexao, "DELETE FROM escala_turno WHERE tipo_turno_id IN"
                    + " (SELECT id FROM tipo_turno WHERE nome = 'TESTE-LIMPEZA-TIPO')");
            executar(conexao, "DELETE FROM tipo_turno WHERE nome = 'TESTE-LIMPEZA-TIPO'");
            executar(conexao, "DELETE FROM funcionario WHERE matricula = 'TESTE-LIMPEZA-1'");
        }
    }

    private int inserirTurno(Connection conexao, LocalDateTime inicio) throws SQLException {
        try (PreparedStatement stmt = conexao.prepareStatement(
                "INSERT INTO escala_turno (tipo_turno_id, inicio, fim, min_agentes) VALUES (?, ?, ?, 1)",
                Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, tipoTurnoId);
            stmt.setObject(2, inicio);
            stmt.setObject(3, inicio.plusHours(24));
            stmt.executeUpdate();
            return primeiraChave(stmt);
        }
    }

    private int inserirAlocacao(Connection conexao, int turnoId) throws SQLException {
        try (PreparedStatement stmt = conexao.prepareStatement(
                "INSERT INTO escala_funcionario (escala_turno_id, funcionario_id) VALUES (?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, turnoId);
            stmt.setInt(2, funcionarioId);
            stmt.executeUpdate();
            return primeiraChave(stmt);
        }
    }

    private static int inserir(Connection conexao, String sql) throws SQLException {
        try (PreparedStatement stmt = conexao.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.executeUpdate();
            return primeiraChave(stmt);
        }
    }

    private static int primeiraChave(PreparedStatement stmt) throws SQLException {
        try (ResultSet chaves = stmt.getGeneratedKeys()) {
            chaves.next();
            return chaves.getInt(1);
        }
    }

    private static void executar(Connection conexao, String sql, Object... parametros) throws SQLException {
        try (PreparedStatement stmt = conexao.prepareStatement(sql)) {
            for (int i = 0; i < parametros.length; i++) {
                stmt.setObject(i + 1, parametros[i]);
            }
            stmt.executeUpdate();
        }
    }

    private int contar(String tabela, String filtro) {
        try (Connection conexao = ConexaoBanco.getConnection();
             PreparedStatement stmt = conexao.prepareStatement(
                     "SELECT count(*) FROM " + tabela + " WHERE " + filtro)) {
            stmt.setObject(1, AGOSTO.atDay(1).atStartOfDay());
            stmt.setObject(2, AGOSTO.plusMonths(1).atDay(1).atStartOfDay());
            try (ResultSet rs = stmt.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao contar " + tabela, e);
        }
    }

    private int contarAlocacoesDoFuncionario() {
        return contarSimples("SELECT count(*) FROM escala_funcionario WHERE funcionario_id = " + funcionarioId);
    }

    private int contarLancamentosDoFuncionario() {
        return contarSimples("SELECT count(*) FROM lancamento_horas WHERE funcionario_id = " + funcionarioId);
    }

    private int contarSimples(String sql) {
        try (Connection conexao = ConexaoBanco.getConnection();
             Statement stmt = conexao.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao contar", e);
        }
    }
}
