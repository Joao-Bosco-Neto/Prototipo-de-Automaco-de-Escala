package br.edu.sistemaescala.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.edu.sistemaescala.backend.dao.BancoInicializador;
import br.edu.sistemaescala.backend.dao.ConexaoBanco;
import br.edu.sistemaescala.backend.model.EscalaFuncionario;
import br.edu.sistemaescala.backend.model.LancamentoHoras;
import br.edu.sistemaescala.backend.repository.LancamentoHorasRepository;
import br.edu.sistemaescala.backend.repository.jdbc.EscalaFuncionarioRepositoryJdbc;
import br.edu.sistemaescala.backend.repository.jdbc.EscalaTurnoRepositoryJdbc;
import br.edu.sistemaescala.backend.repository.jdbc.FuncionarioRepositoryJdbc;
import br.edu.sistemaescala.backend.repository.jdbc.LancamentoHorasRepositoryJdbc;
import br.edu.sistemaescala.backend.repository.jdbc.MotivoCoberturaRepositoryJdbc;

/**
 * Testes de integração do registro de cobertura contra o H2 real (issue #33).
 *
 * <p>O turno de teste dura 24h (1440 min), então o crédito de quem cobre é
 * {@code +1440} e o débito do ausente {@code -1440}.</p>
 */
class CoberturaServiceImplTest {

    private static final java.time.format.DateTimeFormatter TS =
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final LocalDate DIA = LocalDate.of(2040, 3, 10);
    private static final LocalDateTime INICIO = DIA.atTime(8, 0);
    private static final LocalDateTime FIM = INICIO.plusHours(24);
    private static final int MINUTOS_DO_TURNO = 24 * 60;

    private static final String PREFIXO = "COB-TEST";

    private int tipoTurnoId;
    private int turnoId;
    private int funcionarioAusenteId;
    private int funcionarioSubstitutoId;
    private int funcionarioTerceiroId;
    private int alocacaoAusenteId;

    private CoberturaService servico;

    @BeforeAll
    static void prepararBanco() {
        BancoInicializador.inicializar();
    }

    @BeforeEach
    void semear() throws SQLException {
        limparFixtures();
        try (Connection conexao = ConexaoBanco.getConnection()) {
            tipoTurnoId = inserir(conexao,
                    "INSERT INTO tipo_turno (nome, hora_inicio, duracao_horas, intervalo_descanso_horas, min_agentes)"
                    + " VALUES ('" + PREFIXO + "-TIPO', TIME '08:00:00', 24, 72, 1)");
            funcionarioAusenteId = inserirFuncionario(conexao, "AUS");
            funcionarioSubstitutoId = inserirFuncionario(conexao, "SUB");
            funcionarioTerceiroId = inserirFuncionario(conexao, "TER");

            turnoId = inserir(conexao,
                    "INSERT INTO escala_turno (tipo_turno_id, inicio, fim, min_agentes) VALUES ("
                    + tipoTurnoId + ", TIMESTAMP '" + TS.format(INICIO) + "', TIMESTAMP '" + TS.format(FIM) + "', 1)");

            alocacaoAusenteId = inserirAlocacao(conexao, funcionarioAusenteId);
            inserirAlocacao(conexao, funcionarioTerceiroId);
        }
        servico = novoServico(new LancamentoHorasRepositoryJdbc());
    }

    @AfterEach
    void limpar() throws SQLException {
        limparFixtures();
    }

    private CoberturaService novoServico(LancamentoHorasRepository lancamentoHorasRepository) {
        return new CoberturaServiceImpl(new EscalaTurnoRepositoryJdbc(), new EscalaFuncionarioRepositoryJdbc(),
                lancamentoHorasRepository, new MotivoCoberturaRepositoryJdbc(), new FuncionarioRepositoryJdbc(),
                new RegraEscalaServiceImpl());
    }

    // -----------------------------------------------------------------
    // Leituras
    // -----------------------------------------------------------------

    @Test
    void listarEscaladosNaDataTrazSoOsTitularesDaData() {
        List<EscalaFuncionario> escalados = servico.listarEscaladosNaData(DIA);

        assertEquals(2, escalados.size(), "o ausente e o terceiro estão escalados na data");
        assertTrue(escalados.stream().allMatch(a -> a.getCoberturaDe() == null));
        assertTrue(escalados.stream().anyMatch(a -> a.getFuncionario().getId() == funcionarioAusenteId));

        assertTrue(servico.listarEscaladosNaData(DIA.minusDays(1)).isEmpty(),
                "nenhum turno no dia anterior");
    }

    @Test
    void listarSubstitutosMarcaDisponibilidadeEExcluiQuemJaEstaNoTurno() {
        EscalaFuncionario ausente = alocacaoAusente();

        List<SubstitutoDisponivel> substitutos = servico.listarSubstitutos(ausente);

        assertTrue(substitutos.stream().noneMatch(s -> s.funcionario().getId() == funcionarioAusenteId),
                "o ausente já está no turno e não pode se cobrir");
        assertTrue(substitutos.stream().noneMatch(s -> s.funcionario().getId() == funcionarioTerceiroId),
                "o terceiro já está no turno");

        SubstitutoDisponivel substituto = substitutos.stream()
                .filter(s -> s.funcionario().getId() == funcionarioSubstitutoId)
                .findFirst()
                .orElseThrow();
        assertTrue(substituto.disponivel(), "o substituto não tem outro plantão: está livre");
    }

    // -----------------------------------------------------------------
    // Registro
    // -----------------------------------------------------------------

    @Test
    void registrarComBancoDeHorasCriaACoberturaEOsDoisLancamentos() {
        EscalaFuncionario cobertura = servico.registrar(alocacaoAusente(), substituto(),
                null, "Trocou com o colega", true);

        assertNotNull(cobertura.getId());
        assertEquals(alocacaoAusenteId, coberturaDeDaAlocacao(cobertura.getId()));
        assertTrue(lancouBancoHoras(cobertura.getId()));

        assertEquals(MINUTOS_DO_TURNO, minutosDoLancamento(funcionarioSubstitutoId, cobertura.getId()),
                "crédito para quem cobre, no valor da duração do turno");
        assertEquals(-MINUTOS_DO_TURNO, minutosDoLancamento(funcionarioAusenteId, cobertura.getId()),
                "débito para o ausente");
        assertEquals("credito_cobertura", tipoDoLancamento(funcionarioSubstitutoId, cobertura.getId()));
        assertEquals("debito_ausencia", tipoDoLancamento(funcionarioAusenteId, cobertura.getId()));
    }

    @Test
    void registrarSemBancoDeHorasNaoCriaLancamento() {
        EscalaFuncionario cobertura = servico.registrar(alocacaoAusente(), substituto(),
                null, null, false);

        assertFalse(lancouBancoHoras(cobertura.getId()));
        assertEquals(0, contarLancamentosDaCobertura(cobertura.getId()));
    }

    @Test
    void registrarRecusaSubstitutoIgualAoAusente() {
        EscalaFuncionario ausente = alocacaoAusente();
        RegraCoberturaException erro = assertThrows(RegraCoberturaException.class,
                () -> servico.registrar(ausente, ausente.getFuncionario(), null, null, true));
        assertTrue(erro.getMessage().toLowerCase().contains("próprio")
                || erro.getMessage().toLowerCase().contains("proprio"));
    }

    @Test
    void falhaNoLancamentoDesfazACoberturaInteira() {
        CoberturaService servicoQueFalha = novoServico(new LancamentoHorasRepositoryJdbc() {
            @Override
            public LancamentoHoras salvar(LancamentoHoras lancamento, Connection conexao) {
                throw new IllegalStateException("falha simulada no banco de horas");
            }
        });

        assertThrows(IllegalStateException.class,
                () -> servicoQueFalha.registrar(alocacaoAusente(), substituto(), null, null, true));

        // Conferido numa conexão nova: a transação já foi desfeita e fechada.
        assertEquals(0, contarCoberturasDoTurno(),
                "o rollback deveria ter apagado a alocação de cobertura");
    }

    // -----------------------------------------------------------------
    // Apoio
    // -----------------------------------------------------------------

    private EscalaFuncionario alocacaoAusente() {
        return servico.listarEscaladosNaData(DIA).stream()
                .filter(a -> a.getFuncionario().getId() == funcionarioAusenteId)
                .findFirst()
                .orElseThrow();
    }

    private br.edu.sistemaescala.backend.model.Funcionario substituto() {
        return servico.listarSubstitutos(alocacaoAusente()).stream()
                .filter(s -> s.funcionario().getId() == funcionarioSubstitutoId)
                .findFirst()
                .orElseThrow()
                .funcionario();
    }

    private int inserirFuncionario(Connection conexao, String sufixo) throws SQLException {
        return inserir(conexao, "INSERT INTO funcionario (nome, matricula) VALUES ('"
                + PREFIXO + " " + sufixo + "', '" + PREFIXO + "-" + sufixo + "')");
    }

    private int inserirAlocacao(Connection conexao, int funcionarioId) throws SQLException {
        return inserir(conexao, "INSERT INTO escala_funcionario (escala_turno_id, funcionario_id) VALUES ("
                + turnoId + ", " + funcionarioId + ")");
    }

    private static int inserir(Connection conexao, String sql) throws SQLException {
        try (PreparedStatement stmt = conexao.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.executeUpdate();
            try (ResultSet chaves = stmt.getGeneratedKeys()) {
                chaves.next();
                return chaves.getInt(1);
            }
        }
    }

    private int coberturaDeDaAlocacao(int alocacaoId) {
        return consultarInt("SELECT cobertura_de FROM escala_funcionario WHERE id = " + alocacaoId);
    }

    private boolean lancouBancoHoras(int alocacaoId) {
        return consultarInt("SELECT CASE WHEN lancou_banco_horas THEN 1 ELSE 0 END"
                + " FROM escala_funcionario WHERE id = " + alocacaoId) == 1;
    }

    private int minutosDoLancamento(int funcionarioId, int coberturaId) {
        return consultarInt("SELECT minutos FROM lancamento_horas WHERE funcionario_id = " + funcionarioId
                + " AND escala_funcionario_id = " + coberturaId);
    }

    private String tipoDoLancamento(int funcionarioId, int coberturaId) {
        return consultarTexto("SELECT tipo FROM lancamento_horas WHERE funcionario_id = " + funcionarioId
                + " AND escala_funcionario_id = " + coberturaId);
    }

    private int contarLancamentosDaCobertura(int coberturaId) {
        return consultarInt("SELECT count(*) FROM lancamento_horas WHERE escala_funcionario_id = " + coberturaId);
    }

    private int contarCoberturasDoTurno() {
        return consultarInt("SELECT count(*) FROM escala_funcionario WHERE escala_turno_id = " + turnoId
                + " AND cobertura_de IS NOT NULL");
    }

    private int consultarInt(String sql) {
        try (Connection conexao = ConexaoBanco.getConnection();
             Statement stmt = conexao.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao consultar: " + sql, e);
        }
    }

    private String consultarTexto(String sql) {
        try (Connection conexao = ConexaoBanco.getConnection();
             Statement stmt = conexao.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            rs.next();
            return rs.getString(1);
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao consultar: " + sql, e);
        }
    }

    private void limparFixtures() throws SQLException {
        try (Connection conexao = ConexaoBanco.getConnection();
             Statement stmt = conexao.createStatement()) {
            stmt.executeUpdate("DELETE FROM lancamento_horas WHERE funcionario_id IN"
                    + " (SELECT id FROM funcionario WHERE matricula LIKE '" + PREFIXO + "-%')");
            stmt.executeUpdate("DELETE FROM escala_funcionario WHERE funcionario_id IN"
                    + " (SELECT id FROM funcionario WHERE matricula LIKE '" + PREFIXO + "-%')");
            stmt.executeUpdate("DELETE FROM escala_turno WHERE tipo_turno_id IN"
                    + " (SELECT id FROM tipo_turno WHERE nome = '" + PREFIXO + "-TIPO')");
            stmt.executeUpdate("DELETE FROM tipo_turno WHERE nome = '" + PREFIXO + "-TIPO'");
            stmt.executeUpdate("DELETE FROM funcionario WHERE matricula LIKE '" + PREFIXO + "-%'");
        }
    }
}
