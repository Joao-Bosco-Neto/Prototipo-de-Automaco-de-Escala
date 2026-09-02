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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import br.edu.sistemaescala.backend.dao.BancoInicializador;
import br.edu.sistemaescala.backend.dao.ConexaoBanco;
import br.edu.sistemaescala.backend.model.EscalaFuncionario;
import br.edu.sistemaescala.backend.model.EscalaTurno;
import br.edu.sistemaescala.backend.model.TipoTurno;
import br.edu.sistemaescala.backend.repository.jdbc.EscalaTurnoRepositoryJdbc;
import br.edu.sistemaescala.backend.service.PlantaoDoDiaItem;

/**
 * Testes de integracao contra o H2 real. Fixtures sao criadas com SQL puro
 * (funcionario, tipo_turno, escala_funcionario) e limpas na ordem que
 * respeita as chaves estrangeiras.
 */
class EscalaTurnoRepositoryJdbcTest {

    private static final EscalaTurnoRepository REPOSITORIO = new EscalaTurnoRepositoryJdbc();

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
            // ordem reversa: uma cobertura referencia o titular via cobertura_de (FK auto-referenciada)
            for (int i = ESCALA_FUNCIONARIO_IDS.size() - 1; i >= 0; i--) {
                executar(conexao, "DELETE FROM escala_funcionario WHERE id = ?", ESCALA_FUNCIONARIO_IDS.get(i));
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
    void salvarInsereQuandoIdNuloEAtualizaQuandoIdPreenchido() {
        int tipoTurnoId = inserirTipoTurno("TESTE-ET-TIPO-A");

        TipoTurno tipoTurno = new TipoTurno();
        tipoTurno.setId(tipoTurnoId);

        EscalaTurno turno = new EscalaTurno();
        turno.setTipoTurno(tipoTurno);
        turno.setInicio(LocalDateTime.of(2026, 5, 10, 8, 0));
        turno.setFim(LocalDateTime.of(2026, 5, 11, 8, 0));
        turno.setMinAgentes(1);
        turno.setObservacao("original");

        REPOSITORIO.salvar(turno);
        ESCALA_TURNO_IDS.add(turno.getId());
        assertNotNull(turno.getId());

        turno.setObservacao("editado");
        REPOSITORIO.salvar(turno);

        EscalaTurno buscado = REPOSITORIO.buscarPorId(turno.getId()).orElseThrow();
        assertEquals("editado", buscado.getObservacao());
        assertEquals(tipoTurnoId, buscado.getTipoTurno().getId());
    }

    @Test
    void buscarPorPeriodoTrazTurnosComAgentesECoberturasHidratados() {
        int tipoTurnoId = inserirTipoTurno("TESTE-ET-TIPO-B");
        int funcionarioTitularId = inserirFuncionario("TESTE-ET-FUNC-TITULAR");
        int funcionarioCoberturaId = inserirFuncionario("TESTE-ET-FUNC-COBERTURA");

        int turnoDentroId = inserirEscalaTurno(tipoTurnoId,
                LocalDateTime.of(2026, 6, 10, 8, 0), LocalDateTime.of(2026, 6, 11, 8, 0));
        int turnoForaId = inserirEscalaTurno(tipoTurnoId,
                LocalDateTime.of(2026, 7, 10, 8, 0), LocalDateTime.of(2026, 7, 11, 8, 0));

        int titularId = inserirEscalaFuncionario(turnoDentroId, funcionarioTitularId, null);
        int coberturaId = inserirEscalaFuncionario(turnoDentroId, funcionarioCoberturaId, titularId);
        int agenteForaId = inserirEscalaFuncionario(turnoForaId, funcionarioTitularId, null);

        ESCALA_FUNCIONARIO_IDS.add(titularId);
        ESCALA_FUNCIONARIO_IDS.add(coberturaId);
        ESCALA_FUNCIONARIO_IDS.add(agenteForaId);
        ESCALA_TURNO_IDS.add(turnoDentroId);
        ESCALA_TURNO_IDS.add(turnoForaId);
        TIPO_TURNO_IDS.add(tipoTurnoId);
        FUNCIONARIO_IDS.add(funcionarioTitularId);
        FUNCIONARIO_IDS.add(funcionarioCoberturaId);

        List<EscalaTurno> turnos = REPOSITORIO.buscarPorPeriodo(
                LocalDateTime.of(2026, 6, 1, 0, 0), LocalDateTime.of(2026, 7, 1, 0, 0));

        assertEquals(1, turnos.size(), "so o turno de junho deve entrar no periodo pedido");
        EscalaTurno turno = turnos.get(0);
        assertEquals(turnoDentroId, turno.getId());
        assertEquals(2, turno.getAgentes().size(), "o turno deve trazer os dois agentes ja hidratados");

        EscalaFuncionario titular = turno.getAgentes().stream()
                .filter(a -> a.getId() == titularId).findFirst().orElseThrow();
        assertEquals("TESTE-ET-FUNC-TITULAR", titular.getFuncionario().getMatricula());
        assertEquals(null, titular.getCoberturaDe());

        EscalaFuncionario cobertura = turno.getAgentes().stream()
                .filter(a -> a.getId() == coberturaId).findFirst().orElseThrow();
        assertEquals("TESTE-ET-FUNC-COBERTURA", cobertura.getFuncionario().getMatricula());
        assertNotNull(cobertura.getCoberturaDe());
        assertEquals(titularId, cobertura.getCoberturaDe().getId());
    }

    @Test
    void removerPorMesApagaTurnosDoMesEEmCascataSuasAlocacoes() {
        int tipoTurnoId = inserirTipoTurno("TESTE-ET-TIPO-C");
        int funcionarioId = inserirFuncionario("TESTE-ET-FUNC-LIMPAR");

        int turnoNoMesId = inserirEscalaTurno(tipoTurnoId,
                LocalDateTime.of(2026, 8, 5, 8, 0), LocalDateTime.of(2026, 8, 6, 8, 0));
        int turnoForaDoMesId = inserirEscalaTurno(tipoTurnoId,
                LocalDateTime.of(2026, 9, 5, 8, 0), LocalDateTime.of(2026, 9, 6, 8, 0));
        int escalaFuncionarioId = inserirEscalaFuncionario(turnoNoMesId, funcionarioId, null);

        ESCALA_TURNO_IDS.add(turnoForaDoMesId);
        TIPO_TURNO_IDS.add(tipoTurnoId);
        FUNCIONARIO_IDS.add(funcionarioId);

        REPOSITORIO.removerPorMes(YearMonth.of(2026, 8));

        assertTrue(REPOSITORIO.buscarPorId(turnoNoMesId).isEmpty(), "turno do mes limpo deve ter sido removido");
        assertFalse(escalaFuncionarioExiste(escalaFuncionarioId),
                "a alocacao do turno removido deve cair em cascata (ON DELETE CASCADE)");

        Optional<EscalaTurno> aindaExiste = REPOSITORIO.buscarPorId(turnoForaDoMesId);
        assertTrue(aindaExiste.isPresent(), "turno de outro mes nao pode ser afetado por 'Limpar mes'");
    }

    /**
     * Um dia de 12x36 volta dois itens, e a contagem de agentes segue a regra
     * do efetivo: a alocacao do titular coberto nao soma junto com a de quem
     * cobriu, senao um turno de minimo 2 apareceria como 3.
     */
    @Test
    void resumirPlantoesDoDiaTrazUmItemPorTurnoContandoPostoOcupado() {
        int tipoTurnoId = inserirTipoTurno("TESTE-ET-TIPO-DASH-DIA");
        int titularFuncId = inserirFuncionario("TESTE-ET-DASH-TITULAR");
        int substitutoFuncId = inserirFuncionario("TESTE-ET-DASH-SUBSTITUTO");

        LocalDate dia = LocalDate.of(2033, 3, 10);
        int diurnoId = inserirEscalaTurno(tipoTurnoId,
                dia.atTime(7, 0), dia.atTime(19, 0), 2);
        int noturnoId = inserirEscalaTurno(tipoTurnoId,
                dia.atTime(19, 0), dia.plusDays(1).atTime(7, 0), 2);

        int titularId = inserirEscalaFuncionario(diurnoId, titularFuncId, null);
        int coberturaId = inserirEscalaFuncionario(diurnoId, substitutoFuncId, titularId);
        int noturnoAgenteId = inserirEscalaFuncionario(noturnoId, titularFuncId, null);

        ESCALA_FUNCIONARIO_IDS.add(titularId);
        ESCALA_FUNCIONARIO_IDS.add(coberturaId);
        ESCALA_FUNCIONARIO_IDS.add(noturnoAgenteId);
        ESCALA_TURNO_IDS.add(diurnoId);
        ESCALA_TURNO_IDS.add(noturnoId);
        TIPO_TURNO_IDS.add(tipoTurnoId);
        FUNCIONARIO_IDS.add(titularFuncId);
        FUNCIONARIO_IDS.add(substitutoFuncId);

        List<PlantaoDoDiaItem> plantoes = REPOSITORIO.resumirPlantoesDoDia(dia);

        assertEquals(2, plantoes.size(), "o dia de 12x36 tem diurno e noturno");

        PlantaoDoDiaItem diurno = plantoes.get(0);
        assertEquals(diurnoId, diurno.escalaTurnoId(), "o resumo vem ordenado pelo inicio do turno");
        assertEquals("TESTE-ET-TIPO-DASH-DIA", diurno.tipoTurno());
        assertEquals(dia.atTime(7, 0), diurno.inicio());
        assertEquals(dia.atTime(19, 0), diurno.fim());
        assertEquals(1, diurno.agentes(),
                "titular coberto e substituto ocupam um posto so");
        assertEquals(2, diurno.minimoExigido());
        assertFalse(diurno.completo(), "1 de 2 agentes e efetivo incompleto");

        PlantaoDoDiaItem noturno = plantoes.get(1);
        assertEquals(noturnoId, noturno.escalaTurnoId());
        assertEquals(dia.plusDays(1).atTime(7, 0), noturno.fim(), "o noturno termina no dia seguinte");
        assertEquals(1, noturno.agentes());
    }

    @Test
    void resumirPlantoesDoDiaVoltaVazioQuandoNaoHaTurnoNoDia() {
        assertTrue(REPOSITORIO.resumirPlantoesDoDia(LocalDate.of(2033, 7, 4)).isEmpty(),
                "dia sem turno nao pode inventar plantao — e o estado vazio do card");
    }

    /**
     * Conta dias, nao turnos: os dois turnos incompletos do mesmo dia valem
     * um dia so, como o calendario que pinta a celula de ambar uma vez so.
     */
    @Test
    void contarDiasComEfetivoIncompletoContaDiasDistintosDoMes() {
        YearMonth mes = YearMonth.of(2033, 4);
        int tipoTurnoId = inserirTipoTurno("TESTE-ET-TIPO-DASH-MES");
        int funcionarioId = inserirFuncionario("TESTE-ET-DASH-MES-AGENTE");

        // Dia 5: dois turnos, os dois sem ninguem alocado (min 2) -> 1 dia incompleto.
        int diaCincoDiurnoId = inserirEscalaTurno(tipoTurnoId,
                mes.atDay(5).atTime(7, 0), mes.atDay(5).atTime(19, 0), 2);
        int diaCincoNoturnoId = inserirEscalaTurno(tipoTurnoId,
                mes.atDay(5).atTime(19, 0), mes.atDay(6).atTime(7, 0), 2);
        // Dia 9: um turno de minimo 1 com um agente -> completo, nao conta.
        int diaNoveId = inserirEscalaTurno(tipoTurnoId,
                mes.atDay(9).atTime(7, 0), mes.atDay(9).atTime(19, 0), 1);
        int alocacaoDiaNoveId = inserirEscalaFuncionario(diaNoveId, funcionarioId, null);
        // Fora do mes: nao pode contaminar o recorte.
        int foraDoMesId = inserirEscalaTurno(tipoTurnoId,
                mes.plusMonths(1).atDay(3).atTime(7, 0), mes.plusMonths(1).atDay(3).atTime(19, 0), 2);

        ESCALA_FUNCIONARIO_IDS.add(alocacaoDiaNoveId);
        ESCALA_TURNO_IDS.add(diaCincoDiurnoId);
        ESCALA_TURNO_IDS.add(diaCincoNoturnoId);
        ESCALA_TURNO_IDS.add(diaNoveId);
        ESCALA_TURNO_IDS.add(foraDoMesId);
        TIPO_TURNO_IDS.add(tipoTurnoId);
        FUNCIONARIO_IDS.add(funcionarioId);

        assertEquals(1, REPOSITORIO.contarDiasComEfetivoIncompleto(mes),
                "os dois turnos incompletos do dia 5 contam como um dia so");
        assertEquals(1, REPOSITORIO.contarDiasComEfetivoIncompleto(mes.plusMonths(1)),
                "o turno vazio do mes seguinte conta no mes dele, nao neste");
    }

    /**
     * O alerta de "proximo mes nao iniciado" depende so de existir turno ou
     * nao, entao o recorte por mes precisa ser exato nas duas pontas.
     */
    @Test
    void contarTurnosNoMesRespondeSeOMesJaTemEscalaMontada() {
        YearMonth mes = YearMonth.of(2033, 8);
        int tipoTurnoId = inserirTipoTurno("TESTE-ET-TIPO-CONTAGEM-MES");

        assertEquals(0, REPOSITORIO.contarTurnosNoMes(mes), "mes sem turno nenhum volta zero");

        int primeiroDiaId = inserirEscalaTurno(tipoTurnoId,
                mes.atDay(1).atTime(0, 0), mes.atDay(1).atTime(12, 0), 1);
        int ultimoDiaId = inserirEscalaTurno(tipoTurnoId,
                mes.atEndOfMonth().atTime(23, 0), mes.plusMonths(1).atDay(1).atTime(11, 0), 1);
        int mesSeguinteId = inserirEscalaTurno(tipoTurnoId,
                mes.plusMonths(1).atDay(1).atTime(0, 0),
                mes.plusMonths(1).atDay(1).atTime(12, 0), 1);

        ESCALA_TURNO_IDS.add(primeiroDiaId);
        ESCALA_TURNO_IDS.add(ultimoDiaId);
        ESCALA_TURNO_IDS.add(mesSeguinteId);
        TIPO_TURNO_IDS.add(tipoTurnoId);

        assertEquals(2, REPOSITORIO.contarTurnosNoMes(mes),
                "o turno da meia-noite do dia 1 entra e o da meia-noite do mes seguinte nao");
        assertEquals(1, REPOSITORIO.contarTurnosNoMes(mes.plusMonths(1)),
                "o turno que comeca no mes seguinte conta la");
    }

    private boolean escalaFuncionarioExiste(int id) {
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
        return inserirEscalaTurno(tipoTurnoId, inicio, fim, 1);
    }

    private int inserirEscalaTurno(int tipoTurnoId, LocalDateTime inicio, LocalDateTime fim, int minAgentes) {
        try (Connection conexao = ConexaoBanco.getConnection()) {
            return inserirRetornandoId(conexao,
                    "INSERT INTO escala_turno (tipo_turno_id, inicio, fim, min_agentes) VALUES (?, ?, ?, ?)",
                    tipoTurnoId, java.sql.Timestamp.valueOf(inicio), java.sql.Timestamp.valueOf(fim), minAgentes);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private int inserirEscalaFuncionario(int escalaTurnoId, int funcionarioId, Integer coberturaDe) {
        try (Connection conexao = ConexaoBanco.getConnection()) {
            return inserirRetornandoId(conexao,
                    "INSERT INTO escala_funcionario (escala_turno_id, funcionario_id, cobertura_de) VALUES (?, ?, ?)",
                    escalaTurnoId, funcionarioId, coberturaDe);
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
