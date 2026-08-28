package br.edu.sistemaescala.backend.service;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IntSummaryStatistics;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import org.mockito.InOrder;
import org.mockito.MockedStatic;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.edu.sistemaescala.backend.dao.FuncaoTransacional;
import br.edu.sistemaescala.backend.dao.TransacaoUtil;
import br.edu.sistemaescala.backend.model.EscalaFuncionario;
import br.edu.sistemaescala.backend.model.EscalaTurno;
import br.edu.sistemaescala.backend.model.Funcionario;
import br.edu.sistemaescala.backend.model.TipoTurno;
import br.edu.sistemaescala.backend.repository.EscalaFuncionarioRepository;
import br.edu.sistemaescala.backend.repository.EscalaTurnoRepository;
import br.edu.sistemaescala.backend.repository.FuncionarioRepository;
import br.edu.sistemaescala.backend.repository.TipoTurnoRepository;

/**
 * Testes do gerador automático de rodízio (Issue #43).
 *
 * <p>Os repositórios são dublês, mas as regras de #23 e #40 rodam de verdade:
 * o gerador monta a {@code RegraEscalaServiceImpl} internamente, sobre o
 * retrato do mês em construção. É o que permite afirmar, no fim, que a escala
 * gerada não viola descanso — a checagem é a mesma que a tela usa.</p>
 *
 * <p>{@link TransacaoUtil} é estático e abriria conexão real, então é
 * substituído por um dublê que executa o bloco recebido com uma Connection
 * falsa. As gravações são capturadas em {@link #turnosGravados} e
 * {@link #alocacoesGravadas}.</p>
 */
class GeradorRodizioServiceImplTest {

    private static final YearMonth AGOSTO = YearMonth.of(2026, 8);
    private static final YearMonth JULHO = YearMonth.of(2026, 7);

    /** Matrículas fora da ordem dos nomes de propósito: o rodízio segue a matrícula. */
    private static final Funcionario ANA = funcionario(1, "Ana Souza", "PC-4");
    private static final Funcionario BRUNO = funcionario(2, "Bruno Lima", "PC-1");
    private static final Funcionario CARLA = funcionario(3, "Carla Dias", "PC-3");
    private static final Funcionario DIEGO = funcionario(4, "Diego Melo", "PC-2");

    /** Ordem que o rodízio deve seguir: PC-1, PC-2, PC-3, PC-4. */
    private static final List<Funcionario> ORDEM_POR_MATRICULA = List.of(BRUNO, DIEGO, CARLA, ANA);

    private EscalaTurnoRepository escalaTurnoRepository;
    private EscalaFuncionarioRepository escalaFuncionarioRepository;
    private FuncionarioRepository funcionarioRepository;
    private TipoTurnoRepository tipoTurnoRepository;
    private GeradorRodizioService gerador;

    private MockedStatic<TransacaoUtil> transacaoEstatica;

    private final List<EscalaTurno> turnosGravados = new ArrayList<>();
    private final List<EscalaFuncionario> alocacoesGravadas = new ArrayList<>();
    private final List<YearMonth> mesesRemovidos = new ArrayList<>();
    private int proximoIdDoBanco = 1;

    @BeforeEach
    void prepararMocks() {
        escalaTurnoRepository = mock(EscalaTurnoRepository.class);
        escalaFuncionarioRepository = mock(EscalaFuncionarioRepository.class);
        funcionarioRepository = mock(FuncionarioRepository.class);
        tipoTurnoRepository = mock(TipoTurnoRepository.class);
        gerador = new GeradorRodizioServiceImpl(escalaTurnoRepository, escalaFuncionarioRepository,
                funcionarioRepository, tipoTurnoRepository);

        // Nenhuma escala pré-existente, salvo quando o teste disser o contrário.
        when(escalaTurnoRepository.buscarPorPeriodo(any(), any())).thenReturn(List.of());
        // O repositório ordena por nome; quem reordena por matrícula é o service.
        when(funcionarioRepository.listar(any(), any()))
                .thenReturn(List.of(ANA, BRUNO, CARLA, DIEGO));

        capturarGravacoes();
        substituirTransacao();
    }

    @AfterEach
    void encerrarDubleEstatico() {
        transacaoEstatica.close();
    }

    // -----------------------------------------------------------------
    // Geração básica e ordem da fila
    // -----------------------------------------------------------------

    @Test
    void geraUmTurnoPorDiaSeguindoAOrdemDeMatricula() {
        // 24x72 simplificado: um único tipo ativo, um agente por turno.
        usarTipos(tipoTurno(10, "Plantão 24h", LocalTime.MIDNIGHT, 24, 0, 1));

        ResultadoGeracao resultado = gerador.gerarMes(AGOSTO, false);

        assertTrue(resultado.gerado());
        assertEquals(AGOSTO.lengthOfMonth(), resultado.turnosCriados(),
                "Um tipo de turno ativo deve produzir exatamente um turno por dia");
        assertEquals(AGOSTO.lengthOfMonth(), resultado.alocacoesCriadas());
        assertEquals(0, resultado.diasSemEfetivoSuficiente());

        // A sequência circular percorre PC-1, PC-2, PC-3, PC-4 e recomeça.
        List<Funcionario> esperados = new ArrayList<>();
        for (int dia = 0; dia < AGOSTO.lengthOfMonth(); dia++) {
            esperados.add(ORDEM_POR_MATRICULA.get(dia % ORDEM_POR_MATRICULA.size()));
        }
        assertEquals(matriculas(esperados), matriculasAlocadas(),
                "O rodízio deve seguir a ordem alfabética de matrícula, não a de nome");
    }

    @Test
    void doisTiposAtivosProduzemDoisTurnosPorDia() {
        // 12x36: diurno e noturno na mesma data, pelo mesmo laço.
        usarTipos(
                tipoTurno(20, "Diurno 12h", LocalTime.of(7, 0), 12, 0, 1),
                tipoTurno(21, "Noturno 12h", LocalTime.of(19, 0), 12, 0, 1));

        ResultadoGeracao resultado = gerador.gerarMes(AGOSTO, false);

        assertTrue(resultado.gerado());
        assertEquals(AGOSTO.lengthOfMonth() * 2, resultado.turnosCriados());

        Map<LocalDate, Integer> turnosPorDia = new LinkedHashMap<>();
        for (EscalaTurno turno : turnosGravados) {
            turnosPorDia.merge(turno.getInicio().toLocalDate(), 1, Integer::sum);
        }
        assertEquals(AGOSTO.lengthOfMonth(), turnosPorDia.size());
        assertTrue(turnosPorDia.values().stream().allMatch(quantidade -> quantidade == 2),
                "Todo dia do mês deveria ter dois turnos: " + turnosPorDia);
    }

    // -----------------------------------------------------------------
    // Continuidade entre meses (o caso que costuma passar batido)
    // -----------------------------------------------------------------

    @Test
    void filaContinuaDoProximoDepoisDoUltimoAlocadoNoMesAnterior() {
        TipoTurno tipo = tipoTurno(10, "Plantão 24h", LocalTime.MIDNIGHT, 24, 0, 1);
        usarTipos(tipo);

        // Julho fechou com DIEGO (PC-2) no último turno do mês.
        EscalaTurno ultimoDeJulho = turnoExistente(900, tipo,
                JULHO.atEndOfMonth().atStartOfDay(), DIEGO);
        when(escalaTurnoRepository.buscarPorPeriodo(any(), any())).thenReturn(List.of(
                turnoExistente(899, tipo, JULHO.atDay(30).atStartOfDay(), CARLA),
                ultimoDeJulho));

        ResultadoGeracao resultado = gerador.gerarMes(AGOSTO, false);

        assertTrue(resultado.gerado());
        // Depois de PC-2 vem PC-3: agosto começa em Carla, não em Bruno.
        assertEquals(CARLA.getMatricula(), matriculasAlocadas().get(0),
                "O rodízio de agosto deve continuar de onde julho parou");
        assertNotEquals(BRUNO.getMatricula(), matriculasAlocadas().get(0),
                "A fila não pode recomeçar do primeiro da lista a cada mês");
        assertEquals(List.of(CARLA.getMatricula(), ANA.getMatricula(), BRUNO.getMatricula()),
                matriculasAlocadas().subList(0, 3));
    }

    @Test
    void semEscalaNoMesAnteriorAFilaComecaDoPrimeiroPorMatricula() {
        usarTipos(tipoTurno(10, "Plantão 24h", LocalTime.MIDNIGHT, 24, 0, 1));

        gerador.gerarMes(AGOSTO, false);

        assertEquals(BRUNO.getMatricula(), matriculasAlocadas().get(0));
    }

    // -----------------------------------------------------------------
    // Efetivo insuficiente
    // -----------------------------------------------------------------

    @Test
    void efetivoInsuficienteNaoInterrompeAGeracaoApenasContabilizaOsDias() {
        // Dois agentes, dois por turno e 72h de descanso: só dá para escalar
        // um dia a cada quatro.
        when(funcionarioRepository.listar(any(), any())).thenReturn(List.of(ANA, BRUNO));
        usarTipos(tipoTurno(10, "Plantão 24h", LocalTime.MIDNIGHT, 24, 72, 2));

        ResultadoGeracao resultado = gerador.gerarMes(AGOSTO, false);

        assertTrue(resultado.gerado(), "Escala incompleta pode ser salva com aviso, não pode falhar");
        assertEquals(AGOSTO.lengthOfMonth(), resultado.turnosCriados(),
                "Todos os turnos do mês continuam sendo criados, mesmo sem quem alocar");
        assertTrue(resultado.diasSemEfetivoSuficiente() > 0,
                "Os dias sem efetivo deveriam ser contabilizados");
        assertTrue(resultado.diasSemEfetivoSuficiente() < AGOSTO.lengthOfMonth(),
                "Os dias em que dá para escalar não deveriam entrar na conta");
        assertTrue(resultado.mensagem().contains("efetivo"),
                "A mensagem deveria avisar sobre o efetivo: " + resultado.mensagem());
    }

    // -----------------------------------------------------------------
    // Sobrescrita
    // -----------------------------------------------------------------

    @Test
    void mesJaPreenchidoSemSobrescreverNaoAlteraNada() {
        TipoTurno tipo = tipoTurno(10, "Plantão 24h", LocalTime.MIDNIGHT, 24, 0, 1);
        usarTipos(tipo);
        when(escalaTurnoRepository.buscarPorPeriodo(any(), any())).thenReturn(List.of(
                turnoExistente(500, tipo, AGOSTO.atDay(1).atStartOfDay(), ANA)));

        ResultadoGeracao resultado = gerador.gerarMes(AGOSTO, false);

        assertFalse(resultado.gerado());
        assertEquals(0, resultado.turnosCriados());
        assertEquals(0, resultado.alocacoesCriadas());
        assertTrue(turnosGravados.isEmpty());
        assertTrue(mesesRemovidos.isEmpty());
        verify(escalaTurnoRepository, never()).removerPorMes(any(YearMonth.class));
        transacaoEstatica.verifyNoInteractions();
    }

    @Test
    void sobrescreverRemoveOsTurnosAntigosAntesDeGravarOsNovos() {
        TipoTurno tipo = tipoTurno(10, "Plantão 24h", LocalTime.MIDNIGHT, 24, 0, 1);
        usarTipos(tipo);
        when(escalaTurnoRepository.buscarPorPeriodo(any(), any())).thenReturn(List.of(
                turnoExistente(500, tipo, AGOSTO.atDay(1).atStartOfDay(), ANA)));

        ResultadoGeracao resultado = gerador.gerarMes(AGOSTO, true);

        assertTrue(resultado.gerado());
        assertEquals(List.of(AGOSTO), mesesRemovidos);
        assertEquals(AGOSTO.lengthOfMonth(), turnosGravados.size());

        // A remoção precisa vir antes das inserções, e tudo na mesma transação.
        InOrder ordem = inOrder(escalaTurnoRepository);
        ordem.verify(escalaTurnoRepository).removerPorMes(any(YearMonth.class), any(Connection.class));
        ordem.verify(escalaTurnoRepository, atLeastOnce()).salvar(any(EscalaTurno.class), any(Connection.class));
    }

    // -----------------------------------------------------------------
    // A escala gerada respeita as regras
    // -----------------------------------------------------------------

    @Test
    void escalaGeradaNaoColocaNinguemEmDoisTurnosDentroDoDescansoExigido() {
        // 12x36 com descanso maior do que o giro natural da fila: diurno e
        // noturno todo dia, três agentes e 30h de descanso. Com essa fila o
        // rodízio puro devolveria cada um ao serviço 24h depois do plantão
        // anterior — só a consulta a verificarDescanso segura isso, que é
        // exatamente o que este teste prova.
        int descansoHoras = 30;
        when(funcionarioRepository.listar(any(), any())).thenReturn(List.of(ANA, BRUNO, CARLA));
        usarTipos(
                tipoTurno(20, "Diurno 12h", LocalTime.of(7, 0), 12, descansoHoras, 1),
                tipoTurno(21, "Noturno 12h", LocalTime.of(19, 0), 12, descansoHoras, 1));

        ResultadoGeracao resultado = gerador.gerarMes(AGOSTO, false);

        assertTrue(resultado.gerado());
        assertTrue(resultado.alocacoesCriadas() > 0, "A geração precisa ter escalado alguém");

        Map<Integer, List<EscalaTurno>> turnosPorFuncionario = agruparTurnosPorFuncionario();
        assertFalse(turnosPorFuncionario.isEmpty());

        Duration descansoExigido = Duration.ofHours(descansoHoras);
        for (Map.Entry<Integer, List<EscalaTurno>> entrada : turnosPorFuncionario.entrySet()) {
            List<EscalaTurno> turnos = entrada.getValue();
            for (int i = 1; i < turnos.size(); i++) {
                Duration intervalo = Duration.between(turnos.get(i - 1).getFim(), turnos.get(i).getInicio());
                assertTrue(intervalo.compareTo(descansoExigido) >= 0,
                        "Funcionário " + entrada.getKey() + " descansou apenas " + intervalo
                                + " entre " + turnos.get(i - 1).getFim() + " e " + turnos.get(i).getInicio());
            }
        }
    }

    // -----------------------------------------------------------------
    // Distribuicao justa quando o efetivo nao fecha todos os dias
    // -----------------------------------------------------------------

    @Test
    void naoFormaDuplaFixaQuandoOEfetivoNaoFechaTodosOsDias() {
        // Cenário exato do relato: três agentes, dois por turno e 72h de
        // descanso. Cada um só pode voltar a cada quatro dias, então nem todo
        // dia tem como ser preenchido. O que não pode acontecer é dois deles
        // virarem dupla fixa e o terceiro trabalhar sempre sozinho.
        when(funcionarioRepository.listar(any(), any())).thenReturn(List.of(ANA, BRUNO, CARLA));
        usarTipos(tipoTurno(10, "Plantão 24h", LocalTime.MIDNIGHT, 24, 72, 2));

        ResultadoGeracao resultado = gerador.gerarMes(AGOSTO, false);

        assertTrue(resultado.gerado());

        Map<String, Integer> duplas = contarDuplas();
        assertEquals(3, duplas.size(),
                "Com três agentes o rodízio deveria formar as três duplas possíveis, e não uma só: " + duplas);

        // Nenhum agente pode ficar de fora das duplas nem preso a um só parceiro.
        Map<String, Integer> plantoes = contarPlantoes();
        assertEquals(3, plantoes.size(), "Todos os três deveriam trabalhar: " + plantoes);
        assertTrue(amplitude(plantoes) <= 2,
                "A carga deveria ficar equilibrada entre os três: " + plantoes);

        // E ninguém escalado sozinho: turno abaixo do mínimo fica vazio.
        for (EscalaTurno turno : turnosGravados) {
            int agentes = agentesDoTurno(turno).size();
            assertTrue(agentes == 0 || agentes >= turno.getMinAgentes(),
                    "Turno de " + turno.getInicio() + " ficou com " + agentes
                            + " agente(s), abaixo do mínimo de " + turno.getMinAgentes());
        }
    }

    @Test
    void distribuiPlantoesEDuplasDeFormaEquilibradaAoLongoDoMes() {
        // Sete agentes, dois por turno e 72h de descanso: a capacidade é de
        // 1,75 agente por dia, então nem todos cabem por dia e o rodízio
        // precisa girar de verdade em vez de repetir sempre as mesmas duplas.
        List<Funcionario> equipe = List.of(
                funcionario(1, "Ana Souza", "PC-1"), funcionario(2, "Bruno Lima", "PC-2"),
                funcionario(3, "Carla Dias", "PC-3"), funcionario(4, "Diego Melo", "PC-4"),
                funcionario(5, "Elisa Rocha", "PC-5"), funcionario(6, "Fabio Nunes", "PC-6"),
                funcionario(7, "Gina Alves", "PC-7"));
        when(funcionarioRepository.listar(any(), any())).thenReturn(equipe);
        usarTipos(tipoTurno(10, "Plantão 24h", LocalTime.MIDNIGHT, 24, 72, 2));

        ResultadoGeracao resultado = gerador.gerarMes(AGOSTO, false);

        assertTrue(resultado.gerado());

        // 1) Carga parecida: ninguém escalado muito mais (ou menos) que os outros.
        Map<String, Integer> plantoes = contarPlantoes();
        assertEquals(equipe.size(), plantoes.size(), "Todos deveriam ter trabalhado: " + plantoes);
        assertTrue(amplitude(plantoes) <= 2,
                "Entre o mais e o menos escalado deveria haver no máximo 2 plantões: " + plantoes);

        // 2) Duplas variadas: um rodízio congelado produziria só três duplas
        // fixas (uma por par de agentes que descansam em fase).
        Map<String, Integer> duplas = contarDuplas();
        assertTrue(duplas.size() >= equipe.size(),
                "As duplas deveriam variar ao longo do mês, mas só apareceram " + duplas.size() + ": " + duplas);

        // 3) Nenhuma dupla sistemática: a mais frequente não pode responder por
        // mais de um quarto dos turnos escalados.
        int turnosComAgentes = (int) turnosGravados.stream()
                .filter(turno -> !agentesDoTurno(turno).isEmpty())
                .count();
        int maisRepetida = duplas.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        assertTrue(maisRepetida * 4 <= turnosComAgentes,
                "A dupla mais frequente se repetiu " + maisRepetida + " vezes em " + turnosComAgentes
                        + " turnos, o que indica dupla fixa: " + duplas);
    }

    @Test
    void semTipoDeTurnoAtivoNaoGeraNada() {
        usarTipos();

        ResultadoGeracao resultado = gerador.gerarMes(AGOSTO, false);

        assertFalse(resultado.gerado());
        assertTrue(turnosGravados.isEmpty());
        transacaoEstatica.verifyNoInteractions();
    }

    @Test
    void semFuncionarioAtivoNaoGeraNada() {
        usarTipos(tipoTurno(10, "Plantão 24h", LocalTime.MIDNIGHT, 24, 0, 1));
        when(funcionarioRepository.listar(any(), any())).thenReturn(List.of());

        ResultadoGeracao resultado = gerador.gerarMes(AGOSTO, false);

        assertFalse(resultado.gerado());
        assertTrue(turnosGravados.isEmpty());
        transacaoEstatica.verifyNoInteractions();
    }

    // -----------------------------------------------------------------
    // Apoio
    // -----------------------------------------------------------------

    /**
     * Faz o TransacaoUtil rodar o bloco recebido na hora, com uma Connection
     * falsa: o teste exercita o gerador, não o commit do H2 (que já tem teste
     * de integração próprio).
     */
    private void substituirTransacao() {
        Connection conexao = conexaoDeTeste();
        transacaoEstatica = mockStatic(TransacaoUtil.class);
        transacaoEstatica.when(() -> TransacaoUtil.executar(any())).thenAnswer(invocacao -> {
            FuncaoTransacional<?> operacao = invocacao.getArgument(0);
            return operacao.aplicar(conexao);
        });
    }

    /**
     * Connection de mentira só para o bloco transacional ter o que repassar
     * adiante. Um mock do Mockito não serve: java.sql.Connection é interface do
     * JDK e o inline mock maker não consegue instrumentá-la. Como os
     * repositórios aqui são dublês, nenhum método dela chega a ser chamado.
     */
    private static Connection conexaoDeTeste() {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[] { Connection.class },
                (proxy, metodo, argumentos) -> switch (metodo.getName()) {
                    case "toString" -> "conexao-de-teste";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == argumentos[0];
                    default -> null;
                });
    }

    /** Os repositórios dublês guardam o que receberam e devolvem ids como o banco faria. */
    private void capturarGravacoes() {
        when(escalaTurnoRepository.salvar(any(EscalaTurno.class), any())).thenAnswer(invocacao -> {
            EscalaTurno turno = invocacao.getArgument(0);
            turno.setId(proximoIdDoBanco++);
            turnosGravados.add(turno);
            return turno;
        });
        when(escalaFuncionarioRepository.inserir(any(EscalaFuncionario.class), any())).thenAnswer(invocacao -> {
            EscalaFuncionario alocacao = invocacao.getArgument(0);
            alocacao.setId(proximoIdDoBanco++);
            alocacoesGravadas.add(alocacao);
            return alocacao;
        });
        doAnswer(invocacao -> {
            mesesRemovidos.add(invocacao.getArgument(0));
            return null;
        }).when(escalaTurnoRepository).removerPorMes(any(YearMonth.class), any());
    }

    private void usarTipos(TipoTurno... tipos) {
        when(tipoTurnoRepository.listar(any())).thenReturn(List.of(tipos));
    }

    /** Matrículas na ordem em que as alocações foram gravadas. */
    private List<String> matriculasAlocadas() {
        return alocacoesGravadas.stream()
                .map(alocacao -> alocacao.getFuncionario().getMatricula())
                .toList();
    }

    private List<String> matriculas(List<Funcionario> funcionarios) {
        return funcionarios.stream().map(Funcionario::getMatricula).toList();
    }

    /** Turnos de cada funcionário na escala gravada, em ordem cronológica. */
    private Map<Integer, List<EscalaTurno>> agruparTurnosPorFuncionario() {
        Map<Integer, List<EscalaTurno>> porFuncionario = new LinkedHashMap<>();
        for (EscalaFuncionario alocacao : alocacoesGravadas) {
            porFuncionario
                    .computeIfAbsent(alocacao.getFuncionario().getId(), id -> new ArrayList<>())
                    .add(alocacao.getEscalaTurno());
        }
        porFuncionario.values().forEach(turnos -> turnos.sort(Comparator.comparing(EscalaTurno::getInicio)));
        return porFuncionario;
    }

    /** Quantos plantões cada matrícula recebeu na escala gravada. */
    private Map<String, Integer> contarPlantoes() {
        Map<String, Integer> plantoes = new TreeMap<>();
        for (EscalaFuncionario alocacao : alocacoesGravadas) {
            plantoes.merge(alocacao.getFuncionario().getMatricula(), 1, Integer::sum);
        }
        return plantoes;
    }

    /** Quantas vezes cada par de matrículas dividiu o mesmo turno. */
    private Map<String, Integer> contarDuplas() {
        Map<String, Integer> duplas = new TreeMap<>();
        for (EscalaTurno turno : turnosGravados) {
            List<String> agentes = new ArrayList<>(agentesDoTurno(turno));
            agentes.sort(Comparator.naturalOrder());
            for (int i = 0; i < agentes.size(); i++) {
                for (int j = i + 1; j < agentes.size(); j++) {
                    duplas.merge(agentes.get(i) + "+" + agentes.get(j), 1, Integer::sum);
                }
            }
        }
        return duplas;
    }

    private List<String> agentesDoTurno(EscalaTurno turno) {
        return alocacoesGravadas.stream()
                .filter(alocacao -> alocacao.getEscalaTurno() == turno)
                .map(alocacao -> alocacao.getFuncionario().getMatricula())
                .toList();
    }

    /** Diferença entre o mais e o menos escalado. */
    private int amplitude(Map<String, Integer> plantoes) {
        IntSummaryStatistics estatisticas =
                plantoes.values().stream().mapToInt(Integer::intValue).summaryStatistics();
        return estatisticas.getMax() - estatisticas.getMin();
    }

    private static Funcionario funcionario(int id, String nome, String matricula) {
        Funcionario funcionario = new Funcionario();
        funcionario.setId(id);
        funcionario.setNome(nome);
        funcionario.setMatricula(matricula);
        funcionario.setAtivo(true);
        return funcionario;
    }

    private static TipoTurno tipoTurno(int id, String nome, LocalTime horaInicio,
                                       int duracaoHoras, int descansoHoras, int minAgentes) {
        TipoTurno tipo = new TipoTurno();
        tipo.setId(id);
        tipo.setNome(nome);
        tipo.setHoraInicio(horaInicio);
        tipo.setDuracaoHoras(BigDecimal.valueOf(duracaoHoras));
        tipo.setIntervaloDescansoHoras(BigDecimal.valueOf(descansoHoras));
        tipo.setMinAgentes(minAgentes);
        tipo.setAtivo(true);
        return tipo;
    }

    /** Turno já gravado no banco, com os agentes na ordem em que foram escalados. */
    private static EscalaTurno turnoExistente(int id, TipoTurno tipo, LocalDateTime inicio,
                                              Funcionario... agentes) {
        EscalaTurno turno = new EscalaTurno();
        turno.setId(id);
        turno.setTipoTurno(tipo);
        turno.setInicio(inicio);
        turno.setFim(inicio.plusHours(tipo.getDuracaoHoras().longValue()));
        turno.setMinAgentes(tipo.getMinAgentes());
        for (Funcionario agente : agentes) {
            EscalaFuncionario alocacao = new EscalaFuncionario();
            alocacao.setEscalaTurno(turno);
            alocacao.setFuncionario(agente);
            turno.getAgentes().add(alocacao);
        }
        return turno;
    }
}
