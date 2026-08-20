package br.edu.sistemaescala.backend.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import br.edu.sistemaescala.backend.dao.BancoInicializador;
import br.edu.sistemaescala.backend.dao.ConexaoBanco;
import br.edu.sistemaescala.backend.model.TipoTurno;
import br.edu.sistemaescala.backend.repository.jdbc.TipoTurnoRepositoryJdbc;

class TipoTurnoRepositoryJdbcTest {

    private static final TipoTurnoRepository REPOSITORIO = new TipoTurnoRepositoryJdbc();

    private static final List<Integer> TIPO_TURNO_IDS = new ArrayList<>();

    @BeforeAll
    static void prepararBanco() {
        BancoInicializador.inicializar();
    }

    @AfterAll
    static void limparBanco() throws java.sql.SQLException {
        try (java.sql.Connection conexao = ConexaoBanco.getConnection()) {
            for (int id : TIPO_TURNO_IDS) {
                try (java.sql.PreparedStatement stmt =
                             conexao.prepareStatement("DELETE FROM tipo_turno WHERE id = ?")) {
                    stmt.setInt(1, id);
                    stmt.executeUpdate();
                }
            }
        }
    }

    @Test
    void insercaoAtualizacaoEDesativacaoPreservamALinha() {
        TipoTurno tipoTurno = novoTipoTurno("TESTE-TT-24x72", LocalTime.of(8, 0), "24", "72", 1);
        REPOSITORIO.inserir(tipoTurno);
        TIPO_TURNO_IDS.add(tipoTurno.getId());
        assertNotNull(tipoTurno.getId());

        tipoTurno.setNome("TESTE-TT-24x72-renomeado");
        tipoTurno.setMinAgentes(2);
        REPOSITORIO.atualizar(tipoTurno);

        TipoTurno atualizado = REPOSITORIO.buscarPorId(tipoTurno.getId()).orElseThrow();
        assertEquals("TESTE-TT-24x72-renomeado", atualizado.getNome());
        assertEquals(2, atualizado.getMinAgentes());
        assertTrue(atualizado.isAtivo());

        REPOSITORIO.desativar(tipoTurno.getId());
        TipoTurno inativo = REPOSITORIO.buscarPorId(tipoTurno.getId()).orElseThrow();
        assertFalse(inativo.isAtivo());

        REPOSITORIO.ativar(tipoTurno.getId());
        assertTrue(REPOSITORIO.buscarPorId(tipoTurno.getId()).orElseThrow().isAtivo());
    }

    @Test
    void listarFiltraPorStatusAtivo() {
        TipoTurno ativo = novoTipoTurno("TESTE-TT-ATIVO", LocalTime.of(7, 0), "12", "36", 1);
        TipoTurno inativo = novoTipoTurno("TESTE-TT-INATIVO", LocalTime.of(19, 0), "12", "36", 1);
        REPOSITORIO.inserir(ativo);
        REPOSITORIO.inserir(inativo);
        REPOSITORIO.desativar(inativo.getId());
        TIPO_TURNO_IDS.add(ativo.getId());
        TIPO_TURNO_IDS.add(inativo.getId());

        List<TipoTurno> ativos = REPOSITORIO.listar(true);
        assertTrue(ativos.stream().anyMatch(t -> t.getId().equals(ativo.getId())));
        assertFalse(ativos.stream().anyMatch(t -> t.getId().equals(inativo.getId())));

        List<TipoTurno> inativos = REPOSITORIO.listar(false);
        assertTrue(inativos.stream().anyMatch(t -> t.getId().equals(inativo.getId())));

        List<TipoTurno> todos = REPOSITORIO.listar(null);
        assertTrue(todos.stream().anyMatch(t -> t.getId().equals(ativo.getId())));
        assertTrue(todos.stream().anyMatch(t -> t.getId().equals(inativo.getId())));
    }

    private TipoTurno novoTipoTurno(String nome, LocalTime horaInicio, String duracaoHoras,
                                     String intervaloDescansoHoras, int minAgentes) {
        TipoTurno tipoTurno = new TipoTurno();
        tipoTurno.setNome(nome);
        tipoTurno.setHoraInicio(horaInicio);
        tipoTurno.setDuracaoHoras(new BigDecimal(duracaoHoras));
        tipoTurno.setIntervaloDescansoHoras(new BigDecimal(intervaloDescansoHoras));
        tipoTurno.setMinAgentes(minAgentes);
        tipoTurno.setAtivo(true);
        return tipoTurno;
    }
}
