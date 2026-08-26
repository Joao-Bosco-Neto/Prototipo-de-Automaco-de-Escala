package br.edu.sistemaescala.backend.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import br.edu.sistemaescala.backend.model.EscalaFuncionario;
import br.edu.sistemaescala.backend.model.EscalaTurno;
import br.edu.sistemaescala.backend.model.Funcionario;
import br.edu.sistemaescala.backend.repository.EscalaFuncionarioRepository;
import br.edu.sistemaescala.backend.repository.RepositoryException;
import br.edu.sistemaescala.backend.repository.jdbc.EscalaFuncionarioRepositoryJdbc;

/**
 * Testes de integracao do TransacaoUtil contra o H2 real.
 *
 * Nenhuma operacao de negocio do sistema faz duas escritas hoje, entao a
 * transacao e exercitada com duas alocacoes de funcionario em turno — o
 * cenario e artificial de proposito, mas o rollback testado e real: a
 * conferencia final e feita numa Connection nova, depois que a transacao
 * ja foi desfeita e fechada.
 *
 * Fixtures (funcionario, tipo_turno, escala_turno) sao criadas com SQL puro
 * FORA da transacao, para existirem quando as chaves estrangeiras forem
 * verificadas, e limpas na ordem que respeita essas chaves.
 */
class TransacaoUtilTest {

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
            for (int id : ESCALA_FUNCIONARIO_IDS) {
                executar(conexao, "DELETE FROM escala_funcionario WHERE id = ?", id);
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
    void commitGravaAsDuasInsercoesFeitasNaMesmaTransacao() {
        int tipoTurnoId = inserirTipoTurno("TESTE-TX-TIPO-OK");
        int funcionarioUmId = inserirFuncionario("TESTE-TX-FUNC-OK-1");
        int funcionarioDoisId = inserirFuncionario("TESTE-TX-FUNC-OK-2");
        int escalaTurnoId = inserirEscalaTurno(tipoTurnoId,
                LocalDateTime.of(2026, 6, 10, 8, 0), LocalDateTime.of(2026, 6, 11, 8, 0));
        TIPO_TURNO_IDS.add(tipoTurnoId);
        FUNCIONARIO_IDS.add(funcionarioUmId);
        FUNCIONARIO_IDS.add(funcionarioDoisId);
        ESCALA_TURNO_IDS.add(escalaTurnoId);

        EscalaFuncionario alocacaoUm = novaAlocacao(escalaTurnoId, funcionarioUmId);
        EscalaFuncionario alocacaoDois = novaAlocacao(escalaTurnoId, funcionarioDoisId);

        List<Integer> idsGerados = TransacaoUtil.executar(conexao -> {
            REPOSITORIO.inserir(alocacaoUm, conexao);
            REPOSITORIO.inserir(alocacaoDois, conexao);
            return List.of(alocacaoUm.getId(), alocacaoDois.getId());
        });

        assertEquals(2, idsGerados.size());
        assertNotNull(alocacaoUm.getId());
        assertNotNull(alocacaoDois.getId());
        ESCALA_FUNCIONARIO_IDS.add(alocacaoUm.getId());
        ESCALA_FUNCIONARIO_IDS.add(alocacaoDois.getId());

        // Conexao nova: le o que foi realmente commitado, nao o cache da transacao.
        assertEquals(2, contarAlocacoesDoTurno(escalaTurnoId),
                "apos o commit, as duas alocacoes devem estar gravadas");
    }

    @Test
    void runtimeExceptionDepoisDeDoisInsertsValidosDesfazTudoESobeAExcecaoOriginal() {
        int tipoTurnoId = inserirTipoTurno("TESTE-TX-TIPO-RB");
        int funcionarioUmId = inserirFuncionario("TESTE-TX-FUNC-RB-1");
        int funcionarioDoisId = inserirFuncionario("TESTE-TX-FUNC-RB-2");
        int escalaTurnoId = inserirEscalaTurno(tipoTurnoId,
                LocalDateTime.of(2026, 7, 10, 8, 0), LocalDateTime.of(2026, 7, 11, 8, 0));
        TIPO_TURNO_IDS.add(tipoTurnoId);
        FUNCIONARIO_IDS.add(funcionarioUmId);
        FUNCIONARIO_IDS.add(funcionarioDoisId);
        ESCALA_TURNO_IDS.add(escalaTurnoId);

        EscalaFuncionario alocacaoUm = novaAlocacao(escalaTurnoId, funcionarioUmId);
        EscalaFuncionario alocacaoDois = novaAlocacao(escalaTurnoId, funcionarioDoisId);
        IllegalStateException falhaProposital =
                new IllegalStateException("falha proposital depois dos dois inserts validos");

        IllegalStateException capturada = assertThrows(IllegalStateException.class, () ->
                TransacaoUtil.executar(conexao -> {
                    REPOSITORIO.inserir(alocacaoUm, conexao);
                    REPOSITORIO.inserir(alocacaoDois, conexao);
                    throw falhaProposital;
                }));

        assertSame(falhaProposital, capturada,
                "a excecao original deve subir intacta, sem embrulho nem troca");
        assertEquals(0, contarAlocacoesDoTurno(escalaTurnoId),
                "nenhuma das duas insercoes pode ter sobrevivido ao rollback");
    }

    @Test
    void erroDeSqlNoTerceiroInsertDesfazOsDoisAnterioresEViraRepositoryException() {
        int tipoTurnoId = inserirTipoTurno("TESTE-TX-TIPO-FK");
        int funcionarioUmId = inserirFuncionario("TESTE-TX-FUNC-FK-1");
        int funcionarioDoisId = inserirFuncionario("TESTE-TX-FUNC-FK-2");
        int escalaTurnoId = inserirEscalaTurno(tipoTurnoId,
                LocalDateTime.of(2026, 8, 10, 8, 0), LocalDateTime.of(2026, 8, 11, 8, 0));
        TIPO_TURNO_IDS.add(tipoTurnoId);
        FUNCIONARIO_IDS.add(funcionarioUmId);
        FUNCIONARIO_IDS.add(funcionarioDoisId);
        ESCALA_TURNO_IDS.add(escalaTurnoId);

        EscalaFuncionario alocacaoUm = novaAlocacao(escalaTurnoId, funcionarioUmId);
        EscalaFuncionario alocacaoDois = novaAlocacao(escalaTurnoId, funcionarioDoisId);
        // Funcionario inexistente: viola a chave estrangeira funcionario_id.
        EscalaFuncionario alocacaoInvalida = novaAlocacao(escalaTurnoId, -1);

        assertThrows(RepositoryException.class, () ->
                TransacaoUtil.executar(conexao -> {
                    REPOSITORIO.inserir(alocacaoUm, conexao);
                    REPOSITORIO.inserir(alocacaoDois, conexao);
                    return REPOSITORIO.inserir(alocacaoInvalida, conexao);
                }));

        assertEquals(0, contarAlocacoesDoTurno(escalaTurnoId),
                "erro no terceiro insert nao pode deixar os dois primeiros gravados");
    }

    private static EscalaFuncionario novaAlocacao(int escalaTurnoId, int funcionarioId) {
        EscalaTurno escalaTurno = new EscalaTurno();
        escalaTurno.setId(escalaTurnoId);
        Funcionario funcionario = new Funcionario();
        funcionario.setId(funcionarioId);

        EscalaFuncionario alocacao = new EscalaFuncionario();
        alocacao.setEscalaTurno(escalaTurno);
        alocacao.setFuncionario(funcionario);
        return alocacao;
    }

    /** Consulta em Connection nova e separada: so enxerga o que foi commitado. */
    private static int contarAlocacoesDoTurno(int escalaTurnoId) {
        try (Connection conexao = ConexaoBanco.getConnection();
             PreparedStatement stmt = conexao.prepareStatement(
                     "SELECT COUNT(*) FROM escala_funcionario WHERE escala_turno_id = ?")) {
            stmt.setInt(1, escalaTurnoId);
            try (ResultSet rs = stmt.executeQuery()) {
                rs.next();
                return rs.getInt(1);
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
