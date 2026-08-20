package br.edu.sistemaescala.backend.repository;

import br.edu.sistemaescala.backend.dao.BancoInicializador;
import br.edu.sistemaescala.backend.dao.ConexaoBanco;
import br.edu.sistemaescala.backend.model.MotivoCobertura;
import br.edu.sistemaescala.backend.repository.jdbc.MotivoCoberturaRepositoryJdbc;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MotivoCoberturaRepositoryJdbcTest {

    private static final MotivoCoberturaRepository REPOSITORIO = new MotivoCoberturaRepositoryJdbc();
    private static Integer motivoId;

    @BeforeAll
    static void prepararBanco() {
        BancoInicializador.inicializar();
    }

    @AfterAll
    static void limparBanco() throws SQLException {
        if (motivoId != null) {
            try (Connection conexao = ConexaoBanco.getConnection();
                 PreparedStatement stmt = conexao.prepareStatement("DELETE FROM motivo_cobertura WHERE id = ?")) {
                stmt.setInt(1, motivoId);
                stmt.executeUpdate();
            }
        }
    }

    @Test
    void executaCrudEFiltraMotivosAtivos() {
        MotivoCobertura motivo = new MotivoCobertura(null, "TESTE-MOTIVO-COBERTURA", true, true);
        REPOSITORIO.inserir(motivo);
        motivoId = motivo.getId();

        assertNotNull(motivoId);
        assertEquals(motivoId, REPOSITORIO.buscarPorId(motivoId).orElseThrow().getId());
        assertTrue(REPOSITORIO.listar(true).stream().anyMatch(item -> item.getId().equals(motivoId)));

        motivo.setNome("TESTE-MOTIVO-COBERTURA-ATUALIZADO");
        motivo.setGeraLancamento(false);
        REPOSITORIO.atualizar(motivo);
        MotivoCobertura atualizado = REPOSITORIO.buscarPorId(motivoId).orElseThrow();
        assertEquals("TESTE-MOTIVO-COBERTURA-ATUALIZADO", atualizado.getNome());
        assertFalse(atualizado.isGeraLancamento());

        REPOSITORIO.desativar(motivoId);
        assertFalse(REPOSITORIO.listar(true).stream().anyMatch(item -> item.getId().equals(motivoId)));
        assertTrue(REPOSITORIO.listar(false).stream().anyMatch(item -> item.getId().equals(motivoId)));

        REPOSITORIO.ativar(motivoId);
        assertTrue(REPOSITORIO.buscarPorId(motivoId).orElseThrow().isAtivo());
    }
}