package br.edu.sistemaescala.backend.service;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.edu.sistemaescala.backend.model.Funcionario;
import br.edu.sistemaescala.backend.model.TipoTurno;
import br.edu.sistemaescala.backend.repository.FuncionarioRepository;
import br.edu.sistemaescala.backend.repository.TipoTurnoRepository;

class TipoTurnoServiceImplTest {

    private TipoTurnoRepository tipoTurnoRepository;
    private FuncionarioRepository funcionarioRepository;
    private TipoTurnoService tipoTurnoService;

    @BeforeEach
    void setup() {
        tipoTurnoRepository = mock(TipoTurnoRepository.class);
        funcionarioRepository = mock(FuncionarioRepository.class);
        tipoTurnoService = new TipoTurnoServiceImpl(tipoTurnoRepository, funcionarioRepository);
    }

    @Test
    void cadastrarTipoTurnoComSucesso() {
        when(tipoTurnoRepository.inserir(any(TipoTurno.class))).thenAnswer(inv -> {
            TipoTurno tt = inv.getArgument(0);
            tt.setId(1);
            return tt;
        });

        TipoTurno criado = tipoTurnoService.cadastrar(
                "Plantão 24x72", LocalTime.of(8, 0), BigDecimal.valueOf(24),
                BigDecimal.valueOf(72), 2, 4, true, true);

        assertNotNull(criado);
        assertEquals(1, criado.getId());
        assertEquals("Plantão 24x72", criado.getNome());
        assertEquals(LocalTime.of(8, 0), criado.getHoraInicio());
        assertEquals(BigDecimal.valueOf(24), criado.getDuracaoHoras());
        assertEquals(BigDecimal.valueOf(72), criado.getIntervaloDescansoHoras());
        assertEquals(2, criado.getMinAgentes());
        assertEquals(4, criado.getMaxAgentes());
        assertTrue(criado.isContaBancoHoras());
        assertTrue(criado.isAtivo());
        verify(tipoTurnoRepository).inserir(any(TipoTurno.class));
    }

    @Test
    void cadastrarRejeitaNomeObrigatorio() {
        assertThrows(RegraTipoTurnoException.class, () -> tipoTurnoService.cadastrar(
                null, LocalTime.of(8, 0), BigDecimal.valueOf(24), BigDecimal.valueOf(72), 2, null, true, true));

        assertThrows(RegraTipoTurnoException.class, () -> tipoTurnoService.cadastrar(
                "   ", LocalTime.of(8, 0), BigDecimal.valueOf(24), BigDecimal.valueOf(72), 2, null, true, true));
    }

    @Test
    void cadastrarRejeitaHoraInicioNula() {
        assertThrows(RegraTipoTurnoException.class, () -> tipoTurnoService.cadastrar(
                "24x72", null, BigDecimal.valueOf(24), BigDecimal.valueOf(72), 2, null, true, true));
    }

    @Test
    void cadastrarRejeitaDuracaoZeroOuNegativa() {
        assertThrows(RegraTipoTurnoException.class, () -> tipoTurnoService.cadastrar(
                "24x72", LocalTime.of(8, 0), BigDecimal.ZERO, BigDecimal.valueOf(72), 2, null, true, true));

        assertThrows(RegraTipoTurnoException.class, () -> tipoTurnoService.cadastrar(
                "24x72", LocalTime.of(8, 0), BigDecimal.valueOf(-5), BigDecimal.valueOf(72), 2, null, true, true));
    }

    @Test
    void cadastrarRejeitaDescansoNegativo() {
        assertThrows(RegraTipoTurnoException.class, () -> tipoTurnoService.cadastrar(
                "24x72", LocalTime.of(8, 0), BigDecimal.valueOf(24), BigDecimal.valueOf(-1), 2, null, true, true));
    }

    @Test
    void cadastrarRejeitaMinAgentesMenorQueUm() {
        assertThrows(RegraTipoTurnoException.class, () -> tipoTurnoService.cadastrar(
                "24x72", LocalTime.of(8, 0), BigDecimal.valueOf(24), BigDecimal.valueOf(72), 0, null, true, true));
    }

    @Test
    void cadastrarRejeitaMaxAgentesMenorQueMinimo() {
        assertThrows(RegraTipoTurnoException.class, () -> tipoTurnoService.cadastrar(
                "24x72", LocalTime.of(8, 0), BigDecimal.valueOf(24), BigDecimal.valueOf(72), 3, 2, true, true));
    }

    @Test
    void atualizarTipoTurnoComSucesso() {
        TipoTurno existente = new TipoTurno();
        existente.setId(2);
        when(tipoTurnoRepository.buscarPorId(2)).thenReturn(Optional.of(existente));
        when(tipoTurnoRepository.atualizar(any(TipoTurno.class))).thenAnswer(inv -> inv.getArgument(0));

        TipoTurno atualizado = tipoTurnoService.atualizar(
                2, "12x36 Diurno", LocalTime.of(7, 0), BigDecimal.valueOf(12),
                BigDecimal.valueOf(36), 2, 3, false, false);

        assertNotNull(atualizado);
        assertEquals("12x36 Diurno", atualizado.getNome());
        assertFalse(atualizado.isAtivo());
        verify(tipoTurnoRepository).atualizar(existente);
    }

    @Test
    void atualizarRejeitaIdInexistente() {
        when(tipoTurnoRepository.buscarPorId(99)).thenReturn(Optional.empty());

        assertThrows(RegraTipoTurnoException.class, () -> tipoTurnoService.atualizar(
                99, "Nome", LocalTime.of(8, 0), BigDecimal.valueOf(8), BigDecimal.ZERO, 1, null, true, true));
    }

    @Test
    void ativarEDesativarDelegamParaRepositorio() {
        tipoTurnoService.ativar(1);
        verify(tipoTurnoRepository).ativar(1);

        tipoTurnoService.desativar(2);
        verify(tipoTurnoRepository).desativar(2);
    }

    @Test
    void listarEBuscarDelegamParaRepositorio() {
        TipoTurno tt = new TipoTurno();
        when(tipoTurnoRepository.listar(true)).thenReturn(List.of(tt));
        when(tipoTurnoRepository.buscarPorId(1)).thenReturn(Optional.of(tt));

        assertEquals(1, tipoTurnoService.listar(true).size());
        assertTrue(tipoTurnoService.buscarPorId(1).isPresent());
    }

    @Test
    void verificarViabilidadeCalculaCorretamentePara24x72() {
        // Para 24x72: ciclo = 96h -> 4 turnos por ciclo. Com min 2 agentes -> 8 agentes necessários.
        Funcionario f1 = new Funcionario();
        Funcionario f2 = new Funcionario();
        Funcionario f3 = new Funcionario();

        // Cenário 1: Apenas 3 funcionários ativos -> Inviável (8 necessários)
        when(funcionarioRepository.listar(true, null)).thenReturn(List.of(f1, f2, f3));
        ResultadoViabilidadeTurno resultadoInviavel = tipoTurnoService.verificarViabilidade(
                BigDecimal.valueOf(24), BigDecimal.valueOf(72), 2);

        assertFalse(resultadoInviavel.viavel());
        assertEquals(8, resultadoInviavel.efetivoMinimoNecessario());
        assertEquals(3, resultadoInviavel.efetivoAtivoDisponivel());
        assertTrue(resultadoInviavel.mensagem().contains("necessários no mínimo 8 funcionários ativos"));

        // Cenário 2: 8 funcionários ativos -> Viável
        when(funcionarioRepository.listar(true, null)).thenReturn(List.of(f1, f2, f3, new Funcionario(), new Funcionario(), new Funcionario(), new Funcionario(), new Funcionario()));
        ResultadoViabilidadeTurno resultadoViavel = tipoTurnoService.verificarViabilidade(
                BigDecimal.valueOf(24), BigDecimal.valueOf(72), 2);

        assertTrue(resultadoViavel.viavel());
        assertEquals(8, resultadoViavel.efetivoMinimoNecessario());
        assertEquals(8, resultadoViavel.efetivoAtivoDisponivel());
    }
}

