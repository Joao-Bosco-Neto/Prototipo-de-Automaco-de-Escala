package br.edu.sistemaescala.backend.repository;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.edu.sistemaescala.backend.dao.BancoInicializador;
import br.edu.sistemaescala.backend.dao.ConexaoBanco;
import br.edu.sistemaescala.backend.model.EscalaExcecao;
import br.edu.sistemaescala.backend.model.RegraExcecao;
import br.edu.sistemaescala.backend.repository.jdbc.EscalaExcecaoRepositoryJdbc;
import br.edu.sistemaescala.backend.repository.jdbc.EscalaTurnoRepositoryJdbc;

/**
 * Testes de integração da trilha de exceções contra o H2 real (issue #64).
 *
 * <p>O que mais importa aqui não é o CRUD — é o que <em>não</em> existe: não
 * há caminho de UPDATE, e apagar a escala não pode apagar o registro de que a
 * exceção foi autorizada.</p>
 */
class EscalaExcecaoRepositoryJdbcTest {

    private static final String PREFIXO = "TESTE-EXCECAO";
    private static final String AUTOR = "TESTE-EXCECAO-ADMIN";
    private static final YearMonth MES = YearMonth.of(2041, 5);
    private static final LocalDate DIA = MES.atDay(12);
    private static final String DESCRICAO = "Descanso de 12h até o plantão vizinho; o regime exige 72h.";

    private static final EscalaExcecaoRepository REPOSITORIO = new EscalaExcecaoRepositoryJdbc();

    private int funcionarioId;
    private int alocacaoId;

    @BeforeAll
    static void prepararBanco() {
        BancoInicializador.inicializar();
    }

    @BeforeEach
    void semear() throws SQLException {
        limparFixtures();
        try (Connection conexao = ConexaoBanco.getConnection()) {
            int tipoTurnoId = inserir(conexao,
                    "INSERT INTO tipo_turno (nome, hora_inicio, duracao_horas, intervalo_descanso_horas, min_agentes)"
                    + " VALUES ('" + PREFIXO + "-TIPO', TIME '08:00:00', 24, 72, 1)");
            funcionarioId = inserir(conexao,
                    "INSERT INTO funcionario (nome, matricula) VALUES ('" + PREFIXO + "-NOME', '"
                    + PREFIXO + "-MAT')");
            int turnoId = inserir(conexao,
                    "INSERT INTO escala_turno (tipo_turno_id, inicio, fim, min_agentes) VALUES ("
                    + tipoTurnoId + ", TIMESTAMP '" + DIA + " 08:00:00', TIMESTAMP '"
                    + DIA.plusDays(1) + " 08:00:00', 1)");
            alocacaoId = inserir(conexao,
                    "INSERT INTO escala_funcionario (escala_turno_id, funcionario_id) VALUES ("
                    + turnoId + ", " + funcionarioId + ")");
        }
    }

    @AfterEach
    void limpar() throws SQLException {
        limparFixtures();
    }

    private EscalaExcecao novaExcecao() {
        EscalaExcecao excecao = new EscalaExcecao();
        excecao.setEscalaFuncionarioId(alocacaoId);
        excecao.setFuncionarioId(funcionarioId);
        excecao.setDataPlantao(DIA);
        excecao.setRegra(RegraExcecao.DESCANSO_MINIMO);
        excecao.setDescricao(DESCRICAO);
        excecao.setAutorizadoPor(AUTOR);
        excecao.setCriadoEm(LocalDateTime.now());
        return excecao;
    }

    @Test
    void gravaERecuperaAExcecaoAutorizada() {
        EscalaExcecao gravada = REPOSITORIO.inserir(novaExcecao());
        assertNotNull(gravada.getId());

        List<EscalaExcecao> doFuncionario = REPOSITORIO.listarPorFuncionario(funcionarioId);
        assertEquals(1, doFuncionario.size());

        EscalaExcecao lida = doFuncionario.get(0);
        assertEquals(alocacaoId, lida.getEscalaFuncionarioId());
        assertEquals(DIA, lida.getDataPlantao());
        assertEquals(RegraExcecao.DESCANSO_MINIMO, lida.getRegra());
        assertEquals(AUTOR, lida.getAutorizadoPor());
        assertEquals(DESCRICAO, lida.getDescricao());
        assertNotNull(lida.getCriadoEm());
    }

    @Test
    void atualizarEBloqueadoPeloJavaComExcecaoNativa() {
        EscalaExcecao gravada = REPOSITORIO.inserir(novaExcecao());
        gravada.setDescricao("descrição adulterada");

        UnsupportedOperationException erro = assertThrows(UnsupportedOperationException.class,
                () -> REPOSITORIO.atualizar(gravada));
        assertEquals("A edição de exceções de escala é bloqueada", erro.getMessage());

        // O bloqueio precisa travar a persistência, não só devolver mensagem.
        assertEquals(DESCRICAO, REPOSITORIO.listarPorFuncionario(funcionarioId).get(0).getDescricao());
    }

    @Test
    void aInterfaceNaoOfereceRemocaoNemNenhumOutroCaminhoDeEscrita() {
        List<String> metodos = List.of(EscalaExcecaoRepository.class.getMethods()).stream()
                .map(Method::getName)
                .toList();

        assertTrue(metodos.contains("inserir"));
        assertFalse(metodos.stream().anyMatch(nome ->
                nome.startsWith("remover") || nome.startsWith("excluir") || nome.startsWith("salvar")));
    }

    @Test
    void limparOMesNaoApagaATrilhaDeAuditoriaSoDesligaOVinculo() {
        REPOSITORIO.inserir(novaExcecao());

        // "Limpar mês" (issue #44) apaga turnos e alocações em cascata. A FK da
        // exceção é ON DELETE SET NULL justamente para não ir junto — e para o
        // DELETE não abortar por violação de chave estrangeira.
        new EscalaTurnoRepositoryJdbc().removerPorMes(MES);

        List<EscalaExcecao> restantes = REPOSITORIO.listarPorFuncionario(funcionarioId);
        assertEquals(1, restantes.size(), "a exceção autorizada não pode sumir com a escala");
        assertNull(restantes.get(0).getEscalaFuncionarioId(), "o vínculo com a alocação vira nulo");
        assertEquals(DIA, restantes.get(0).getDataPlantao(),
                "a data denormalizada é o que mantém o registro legível");
    }

    // -----------------------------------------------------------------
    // Apoio
    // -----------------------------------------------------------------

    private static int inserir(Connection conexao, String sql) throws SQLException {
        try (Statement stmt = conexao.createStatement()) {
            stmt.executeUpdate(sql, Statement.RETURN_GENERATED_KEYS);
            try (ResultSet chaves = stmt.getGeneratedKeys()) {
                chaves.next();
                return chaves.getInt(1);
            }
        }
    }

    private static void limparFixtures() throws SQLException {
        try (Connection conexao = ConexaoBanco.getConnection()) {
            executar(conexao, "DELETE FROM escala_excecao WHERE autorizado_por = ?", AUTOR);
            executar(conexao, "DELETE FROM escala_funcionario WHERE funcionario_id IN"
                    + " (SELECT id FROM funcionario WHERE matricula = ?)", PREFIXO + "-MAT");
            executar(conexao, "DELETE FROM escala_turno WHERE tipo_turno_id IN"
                    + " (SELECT id FROM tipo_turno WHERE nome = ?)", PREFIXO + "-TIPO");
            executar(conexao, "DELETE FROM funcionario WHERE matricula = ?", PREFIXO + "-MAT");
            executar(conexao, "DELETE FROM tipo_turno WHERE nome = ?", PREFIXO + "-TIPO");
        }
    }

    private static void executar(Connection conexao, String sql, String parametro) throws SQLException {
        try (PreparedStatement stmt = conexao.prepareStatement(sql)) {
            stmt.setString(1, parametro);
            stmt.executeUpdate();
        }
    }
}
