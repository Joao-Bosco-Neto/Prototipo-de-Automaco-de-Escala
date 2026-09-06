package br.edu.sistemaescala.backend.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.edu.sistemaescala.backend.dao.BancoInicializador;
import br.edu.sistemaescala.backend.dao.ConexaoBanco;
import br.edu.sistemaescala.backend.model.Funcionario;
import br.edu.sistemaescala.backend.repository.jdbc.FuncionarioRepositoryJdbc;

/**
 * Testes de segurança contra SQL Injection (OWASP A05) e parametrização de consultas.
 *
 * <p>Verifica:</p>
 * <ul>
 *   <li>Busca textual por nome e matrícula tratando entradas maliciosas como texto literal puro;</li>
 *   <li>Proteção de ordenação dinâmica via lista fechada de colunas permitidas (whitelist);</li>
 *   <li>Rejeição imediata de payloads de SQL Injection no ORDER BY.</li>
 * </ul>
 */
class SegurancaParametrizacaoSqlTest {

    private final FuncionarioRepository funcionarioRepository = new FuncionarioRepositoryJdbc();
    private final List<Integer> funcionariosParaLimpar = new ArrayList<>();

    @BeforeAll
    static void prepararBanco() {
        BancoInicializador.inicializar();
    }

    @AfterEach
    void limparDados() throws SQLException {
        if (!funcionariosParaLimpar.isEmpty()) {
            try (Connection conexao = ConexaoBanco.getConnection()) {
                for (int id : funcionariosParaLimpar) {
                    try (PreparedStatement stmt = conexao.prepareStatement("DELETE FROM funcionario WHERE id = ?")) {
                        stmt.setInt(1, id);
                        stmt.executeUpdate();
                    }
                }
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "' OR '1'='1",
            "' OR 1=1 --",
            "admin' --",
            "'; DROP TABLE funcionario; --",
            "' UNION SELECT id, login, senha_hash, null, null, true, null FROM usuario --",
            "%' OR '1'='1' --",
            "'; UPDATE funcionario SET ativo = FALSE; --",
            "'; DELETE FROM funcionario; --",
            "\" OR \"\"=\"",
            "' OR ''='",
            "1' AND 1=(SELECT COUNT(*) FROM usuario) --"
    })
    @DisplayName("OWASP A05: Busca textual é imune a ataques de SQL Injection")
    void buscaTextualImuneASqlInjection(String payloadInjecao) throws SQLException {
        // Criar registros controlados
        Funcionario f1 = new Funcionario(null, "Lucas Silva Alvo", "MAT-SEC-01", "63999990001", null, true, null);
        Funcionario f2 = new Funcionario(null, "Mariana Costa", "MAT-SEC-02", "63999990002", null, true, null);
        funcionarioRepository.inserir(f1);
        funcionarioRepository.inserir(f2);
        funcionariosParaLimpar.add(f1.getId());
        funcionariosParaLimpar.add(f2.getId());

        // Executar a busca passando o payload de SQL injection
        List<Funcionario> resultado = funcionarioRepository.listar(true, payloadInjecao);

        // A query parametrizada trata o payload como valor literal: não deve retornar todos os registros indiscriminadamente
        assertTrue(resultado.isEmpty(), "Payload de injeção não deve casar com registros reais nem burlar a cláusula WHERE");

        // Validar que a tabela funcionario continua intacta (nenhum DROP/UPDATE/DELETE malicioso foi executado)
        try (Connection conexao = ConexaoBanco.getConnection();
             PreparedStatement stmt = conexao.prepareStatement("SELECT COUNT(*) FROM funcionario WHERE id IN (?, ?)")) {
            stmt.setInt(1, f1.getId());
            stmt.setInt(2, f2.getId());
            try (ResultSet rs = stmt.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(2, rs.getInt(1), "Todos os registros devem continuar existindo no banco");
            }
        }
    }

    @Test
    @DisplayName("Busca textual com caracteres literais especiais funciona corretamente")
    void buscaTextualComCaracteresEspeciaisFuncionaComoLiteral() {
        Funcionario fEspecial = new Funcionario(null, "Agente D'Ávila", "MAT-SPECIAL-01", "63999991111", null, true, null);
        funcionarioRepository.inserir(fEspecial);
        funcionariosParaLimpar.add(fEspecial.getId());

        List<Funcionario> encontrados = funcionarioRepository.listar(true, "D'Ávila");
        assertEquals(1, encontrados.size());
        assertEquals("Agente D'Ávila", encontrados.get(0).getNome());
    }

    @Test
    @DisplayName("Ordenação dinâmica segura com lista fechada de colunas válidas")
    void ordenacaoDinamicaComColunasValidas() {
        Funcionario f1 = new Funcionario(null, "Ana Beatriz", "MAT-ORD-02", null, null, true, null);
        Funcionario f2 = new Funcionario(null, "Carlos Eduardo", "MAT-ORD-01", null, null, true, null);
        funcionarioRepository.inserir(f1);
        funcionarioRepository.inserir(f2);
        funcionariosParaLimpar.add(f1.getId());
        funcionariosParaLimpar.add(f2.getId());

        // Ordenação por nome ASC
        List<Funcionario> porNomeAsc = funcionarioRepository.listar(true, "MAT-ORD", "nome", true);
        assertEquals(2, porNomeAsc.size());
        assertEquals("Ana Beatriz", porNomeAsc.get(0).getNome());
        assertEquals("Carlos Eduardo", porNomeAsc.get(1).getNome());

        // Ordenação por nome DESC
        List<Funcionario> porNomeDesc = funcionarioRepository.listar(true, "MAT-ORD", "nome", false);
        assertEquals(2, porNomeDesc.size());
        assertEquals("Carlos Eduardo", porNomeDesc.get(0).getNome());
        assertEquals("Ana Beatriz", porNomeDesc.get(1).getNome());

        // Ordenação por matricula ASC
        List<Funcionario> porMatriculaAsc = funcionarioRepository.listar(true, "MAT-ORD", "matricula", true);
        assertEquals(2, porMatriculaAsc.size());
        assertEquals("MAT-ORD-01", porMatriculaAsc.get(0).getMatricula());
        assertEquals("MAT-ORD-02", porMatriculaAsc.get(1).getMatricula());

        // Outras colunas da whitelist
        assertNotNull(funcionarioRepository.listar(true, null, "id", true));
        assertNotNull(funcionarioRepository.listar(true, null, "telefone", true));
        assertNotNull(funcionarioRepository.listar(true, null, "ativo", true));
        assertNotNull(funcionarioRepository.listar(true, null, "criado_em", false));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "nome; DROP TABLE usuario; --",
            "nome, (SELECT senha_hash FROM usuario)",
            "CASE WHEN (1=1) THEN nome ELSE matricula END",
            "senha_hash",
            "coluna_inexistente",
            "nome DESC; DELETE FROM funcionario; --",
            "1; SELECT 1"
    })
    @DisplayName("OWASP A05: Rejeição estrita de colunas inválidas ou tentativas de injeção no ORDER BY")
    void rejeitaColunaInvalidaOuInjecaoNoOrderBy(String colunaMaliciosa) {
        assertThrows(IllegalArgumentException.class, () ->
                funcionarioRepository.listar(true, null, colunaMaliciosa, true)
        );
    }
}
