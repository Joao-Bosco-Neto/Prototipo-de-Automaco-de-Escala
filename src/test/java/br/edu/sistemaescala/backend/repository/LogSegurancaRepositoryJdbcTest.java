package br.edu.sistemaescala.backend.repository;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import br.edu.sistemaescala.backend.dao.BancoInicializador;
import br.edu.sistemaescala.backend.dao.ConexaoBanco;
import br.edu.sistemaescala.backend.model.AcaoSeguranca;
import br.edu.sistemaescala.backend.model.LogSeguranca;
import br.edu.sistemaescala.backend.model.ResultadoSeguranca;
import br.edu.sistemaescala.backend.repository.jdbc.LogSegurancaRepositoryJdbc;
import br.edu.sistemaescala.backend.service.LogSegurancaService;
import br.edu.sistemaescala.backend.service.LogSegurancaServiceImpl;

/**
 * Confere que o schema.sql cria a tabela log_seguranca e que a gravação do
 * evento chega inteira ao banco (issue #64).
 */
class LogSegurancaRepositoryJdbcTest {

    private static final String MARCA = "TESTE-LOG-SEG-";
    private static final LogSegurancaRepository REPOSITORIO = new LogSegurancaRepositoryJdbc();

    @BeforeAll
    static void prepararBanco() {
        BancoInicializador.inicializar();
    }

    @AfterAll
    static void limparBanco() throws SQLException {
        try (Connection conexao = ConexaoBanco.getConnection();
             PreparedStatement stmt = conexao.prepareStatement(
                     "DELETE FROM log_seguranca WHERE identificacao LIKE ?")) {
            stmt.setString(1, MARCA + "%");
            stmt.executeUpdate();
        }
    }

    @Test
    void gravaERecuperaOEventoDeSeguranca() {
        LocalDateTime agora = LocalDateTime.now();
        LogSeguranca evento = new LogSeguranca(null, agora, MARCA + "fulano",
                AcaoSeguranca.LOGIN, ResultadoSeguranca.FALHA, "usuario inexistente");

        REPOSITORIO.inserir(evento);
        assertNotNull(evento.getId());

        List<LogSeguranca> gravados = REPOSITORIO.listarPorPeriodo(agora.minusMinutes(1), agora.plusMinutes(1));
        LogSeguranca lido = gravados.stream()
                .filter(item -> evento.getId().equals(item.getId()))
                .findFirst()
                .orElseThrow();

        assertEquals(MARCA + "fulano", lido.getIdentificacao());
        assertEquals(AcaoSeguranca.LOGIN, lido.getAcao());
        assertEquals(ResultadoSeguranca.FALHA, lido.getResultado());
        assertEquals("usuario inexistente", lido.getDetalhes());
    }

    @Test
    void aStringTentadaChegaAoBancoSemQuebraDeLinha() {
        LocalDateTime agora = LocalDateTime.now();
        LogSegurancaService servico = new LogSegurancaServiceImpl(REPOSITORIO, null);

        servico.registrar(AcaoSeguranca.LOGIN, MARCA + "fulano\nfalsa linha de log",
                ResultadoSeguranca.FALHA, "usuario inexistente");

        LogSeguranca lido = REPOSITORIO.listarPorPeriodo(agora.minusMinutes(1), LocalDateTime.now().plusMinutes(1))
                .stream()
                .filter(item -> item.getIdentificacao().startsWith(MARCA + "fulano"))
                .findFirst()
                .orElseThrow();

        assertFalse(lido.getIdentificacao().contains("\n"));
        assertEquals(MARCA + "fulano falsa linha de log", lido.getIdentificacao());
    }

    /**
     * A imutabilidade da trilha não depende de trigger: o repositório
     * simplesmente não expõe atualização nem exclusão.
     */
    @Test
    void oRepositorioNaoOfereceAtualizacaoNemExclusao() {
        List<String> metodos = List.of(LogSegurancaRepository.class.getMethods()).stream()
                .map(Method::getName)
                .toList();

        assertTrue(metodos.contains("inserir"));
        assertFalse(metodos.stream().anyMatch(nome ->
                nome.startsWith("atualizar") || nome.startsWith("remover") || nome.startsWith("excluir")));
    }
}
