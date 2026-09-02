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

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import br.edu.sistemaescala.backend.dao.BancoInicializador;
import br.edu.sistemaescala.backend.dao.ConexaoBanco;
import br.edu.sistemaescala.backend.model.Funcionario;
import br.edu.sistemaescala.backend.repository.jdbc.FuncionarioRepositoryJdbc;
import br.edu.sistemaescala.backend.service.ContagemFuncionarios;

/**
 * Testes de integracao contra o H2 real (mesmo banco usado pela aplicacao).
 * Cada teste usa matriculas proprias e limpa os dados que criou, na ordem
 * que respeita as chaves estrangeiras.
 */
class FuncionarioRepositoryJdbcTest {

    private static final FuncionarioRepository REPOSITORIO = new FuncionarioRepositoryJdbc();

    private static final List<Integer> FUNCIONARIO_IDS = new ArrayList<>();
    private static final List<Integer> ESCALA_FUNCIONARIO_IDS = new ArrayList<>();
    private static final List<Integer> ESCALA_TURNO_IDS = new ArrayList<>();
    private static final List<Integer> TIPO_TURNO_IDS = new ArrayList<>();

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
    void insercaoAtualizacaoEDesativacaoPreservamHistoricoDePlantoes() {
        Funcionario funcionario = novoFuncionario("Carlos Andrade", "TESTE-FUNC-001");
        REPOSITORIO.inserir(funcionario);
        FUNCIONARIO_IDS.add(funcionario.getId());
        assertNotNull(funcionario.getId());

        int escalaFuncionarioId = registrarPlantao(funcionario.getId(), YearMonth.of(2026, 1));

        funcionario.setNome("Carlos Andrade Silva");
        funcionario.setTelefone("11999999999");
        REPOSITORIO.atualizar(funcionario);

        Funcionario atualizado = REPOSITORIO.buscarPorId(funcionario.getId()).orElseThrow();
        assertEquals("Carlos Andrade Silva", atualizado.getNome());
        assertEquals("11999999999", atualizado.getTelefone());
        assertTrue(atualizado.isAtivo());

        REPOSITORIO.desativar(funcionario.getId());
        Funcionario inativo = REPOSITORIO.buscarPorId(funcionario.getId()).orElseThrow();
        assertFalse(inativo.isAtivo());

        assertEquals(1, REPOSITORIO.contarPlantoesNoMes(funcionario.getId(), YearMonth.of(2026, 1)),
                "desativar o funcionario nao pode apagar o historico de plantoes ja registrado");
        assertTrue(buscarEscalaFuncionarioExiste(escalaFuncionarioId),
                "a linha de escala_funcionario deve continuar existindo apos a desativacao");

        REPOSITORIO.ativar(funcionario.getId());
        assertTrue(REPOSITORIO.buscarPorId(funcionario.getId()).orElseThrow().isAtivo());
    }

    @Test
    void listarCombinaBuscaPorTextoLivreEFiltroDeStatus() {
        Funcionario ativoUm = novoFuncionario("Mariana Ferreira Lopes", "TESTE-FUNC-100");
        Funcionario ativoDois = novoFuncionario("Bruno Costa", "TESTE-FUNC-101-LOPES");
        Funcionario inativo = novoFuncionario("Mariana Lopes Inativa", "TESTE-FUNC-102");
        REPOSITORIO.inserir(ativoUm);
        REPOSITORIO.inserir(ativoDois);
        REPOSITORIO.inserir(inativo);
        REPOSITORIO.desativar(inativo.getId());
        FUNCIONARIO_IDS.add(ativoUm.getId());
        FUNCIONARIO_IDS.add(ativoDois.getId());
        FUNCIONARIO_IDS.add(inativo.getId());

        List<Funcionario> porTexto = REPOSITORIO.listar(null, "lopes");
        assertEquals(3, porTexto.size(),
                "busca por texto livre deve casar tanto pelo nome quanto pela matricula, em qualquer status");

        List<Funcionario> soAtivos = REPOSITORIO.listar(true, "lopes");
        assertEquals(2, soAtivos.size());
        assertTrue(soAtivos.stream().allMatch(Funcionario::isAtivo));

        List<Funcionario> soInativos = REPOSITORIO.listar(false, "lopes");
        assertEquals(1, soInativos.size());
        assertEquals(inativo.getId(), soInativos.get(0).getId());

        List<Funcionario> semFiltro = REPOSITORIO.listar(null, null);
        assertTrue(semFiltro.stream().anyMatch(f -> f.getId().equals(ativoUm.getId())));
    }

    @Test
    void contarPlantoesNoMesContaApenasOMesPedido() {
        Funcionario funcionario = novoFuncionario("Joana Ribeiro", "TESTE-FUNC-200");
        REPOSITORIO.inserir(funcionario);
        FUNCIONARIO_IDS.add(funcionario.getId());

        registrarPlantao(funcionario.getId(), YearMonth.of(2026, 2));
        registrarPlantao(funcionario.getId(), YearMonth.of(2026, 2));
        registrarPlantao(funcionario.getId(), YearMonth.of(2026, 3));

        assertEquals(2, REPOSITORIO.contarPlantoesNoMes(funcionario.getId(), YearMonth.of(2026, 2)));
        assertEquals(1, REPOSITORIO.contarPlantoesNoMes(funcionario.getId(), YearMonth.of(2026, 3)));
        assertEquals(0, REPOSITORIO.contarPlantoesNoMes(funcionario.getId(), YearMonth.of(2026, 4)));
    }

    @Test
    void existeMatriculaDetectaDuplicidadeEIgnoraOProprioRegistroNaEdicao() {
        Funcionario funcionario = novoFuncionario("Renata Souza", "TESTE-FUNC-300");
        REPOSITORIO.inserir(funcionario);
        FUNCIONARIO_IDS.add(funcionario.getId());

        assertTrue(REPOSITORIO.existeMatricula("TESTE-FUNC-300", null));
        assertFalse(REPOSITORIO.existeMatricula("TESTE-FUNC-300", funcionario.getId()),
                "a checagem deve ignorar o proprio registro quando ele esta sendo editado");
        assertFalse(REPOSITORIO.existeMatricula("TESTE-FUNC-NAO-EXISTE", null));
    }

    /**
     * Assercao por diferenca, nao por valor absoluto: o teste roda contra o
     * banco real, que ja tem funcionarios de outros testes e do uso normal.
     * O que importa e que desativar um registro tire um dos ativos e some um
     * aos inativos, sem mexer no total.
     */
    @Test
    void contarPorStatusSeparaAtivosDeInativosNaMesmaConsulta() {
        ContagemFuncionarios antes = REPOSITORIO.contarPorStatus();

        Funcionario ativo = novoFuncionario("Marcos Dias", "TESTE-FUNC-400");
        REPOSITORIO.inserir(ativo);
        FUNCIONARIO_IDS.add(ativo.getId());

        Funcionario paraDesativar = novoFuncionario("Carla Nunes", "TESTE-FUNC-401");
        REPOSITORIO.inserir(paraDesativar);
        FUNCIONARIO_IDS.add(paraDesativar.getId());

        ContagemFuncionarios comDoisAtivos = REPOSITORIO.contarPorStatus();
        assertEquals(antes.ativos() + 2, comDoisAtivos.ativos());
        assertEquals(antes.inativos(), comDoisAtivos.inativos());
        assertEquals(antes.total() + 2, comDoisAtivos.total());

        REPOSITORIO.desativar(paraDesativar.getId());

        ContagemFuncionarios depois = REPOSITORIO.contarPorStatus();
        assertEquals(antes.ativos() + 1, depois.ativos(), "o desativado sai dos ativos");
        assertEquals(antes.inativos() + 1, depois.inativos(), "e entra nos inativos");
        assertEquals(comDoisAtivos.total(), depois.total(),
                "desativar preserva o registro: o total cadastrado nao muda");
    }

    /**
     * O alerta so quer quem esta desativado E ainda tem plantao pela frente:
     * inativo sem plantao futuro e ativo com plantao futuro ficam de fora, e
     * quem tem varios turnos aparece uma vez so.
     */
    @Test
    void listarInativosEscaladosAposTrazSoOsDesativadosComPlantaoFuturoSemRepetir() {
        Funcionario inativoComPlantao = novoFuncionario("Sandra Regina Melo", "TESTE-FUNC-500");
        REPOSITORIO.inserir(inativoComPlantao);
        FUNCIONARIO_IDS.add(inativoComPlantao.getId());

        Funcionario inativoSemPlantaoFuturo = novoFuncionario("Otavio Prado", "TESTE-FUNC-501");
        REPOSITORIO.inserir(inativoSemPlantaoFuturo);
        FUNCIONARIO_IDS.add(inativoSemPlantaoFuturo.getId());

        Funcionario ativoComPlantao = novoFuncionario("Helena Braga", "TESTE-FUNC-502");
        REPOSITORIO.inserir(ativoComPlantao);
        FUNCIONARIO_IDS.add(ativoComPlantao.getId());

        LocalDateTime corte = LocalDateTime.of(2033, 9, 1, 0, 0);
        // Dois plantoes futuros para o mesmo inativo: o DISTINCT tem que unir.
        registrarPlantao(inativoComPlantao.getId(), YearMonth.of(2033, 9));
        registrarPlantao(inativoComPlantao.getId(), YearMonth.of(2033, 10));
        registrarPlantao(inativoSemPlantaoFuturo.getId(), YearMonth.of(2033, 7));
        registrarPlantao(ativoComPlantao.getId(), YearMonth.of(2033, 9));

        REPOSITORIO.desativar(inativoComPlantao.getId());
        REPOSITORIO.desativar(inativoSemPlantaoFuturo.getId());

        List<Funcionario> encontrados = REPOSITORIO.listarInativosEscaladosApos(corte);
        List<String> matriculas = encontrados.stream().map(Funcionario::getMatricula).toList();

        assertTrue(matriculas.contains("TESTE-FUNC-500"), "inativo com plantao futuro tem que entrar");
        assertFalse(matriculas.contains("TESTE-FUNC-501"),
                "inativo cujo ultimo plantao ja passou nao e pendencia");
        assertFalse(matriculas.contains("TESTE-FUNC-502"), "quem esta ativo nao entra");
        assertEquals(1, matriculas.stream().filter("TESTE-FUNC-500"::equals).count(),
                "dois plantoes futuros da mesma pessoa geram uma linha so (DISTINCT)");
    }

    private Funcionario novoFuncionario(String nome, String matricula) {
        Funcionario funcionario = new Funcionario();
        funcionario.setNome(nome);
        funcionario.setMatricula(matricula);
        funcionario.setAtivo(true);
        return funcionario;
    }

    /** Cria tipo_turno + escala_turno + escala_funcionario no dia 10 do mes informado. */
    private int registrarPlantao(int funcionarioId, YearMonth mes) {
        try (Connection conexao = ConexaoBanco.getConnection()) {
            int tipoTurnoId = inserirRetornandoId(conexao,
                    "INSERT INTO tipo_turno (nome, hora_inicio, duracao_horas, intervalo_descanso_horas, min_agentes) " +
                            "VALUES (?, ?, ?, ?, ?)",
                    "Turno de teste " + mes, java.sql.Time.valueOf("08:00:00"), 24, 72, 1);
            TIPO_TURNO_IDS.add(tipoTurnoId);

            int escalaTurnoId = inserirRetornandoId(conexao,
                    "INSERT INTO escala_turno (tipo_turno_id, inicio, fim, min_agentes) VALUES (?, ?, ?, ?)",
                    tipoTurnoId,
                    java.sql.Timestamp.valueOf(mes.atDay(10).atTime(8, 0)),
                    java.sql.Timestamp.valueOf(mes.atDay(11).atTime(8, 0)), 1);
            ESCALA_TURNO_IDS.add(escalaTurnoId);

            int escalaFuncionarioId = inserirRetornandoId(conexao,
                    "INSERT INTO escala_funcionario (escala_turno_id, funcionario_id) VALUES (?, ?)",
                    escalaTurnoId, funcionarioId);
            ESCALA_FUNCIONARIO_IDS.add(escalaFuncionarioId);
            return escalaFuncionarioId;

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean buscarEscalaFuncionarioExiste(int id) {
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
