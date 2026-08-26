package br.edu.sistemaescala.backend.service;

import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.edu.sistemaescala.backend.model.Funcionario;
import br.edu.sistemaescala.backend.repository.FuncionarioRepository;

class FuncionarioServiceImplTest {

    private FuncionarioRepository funcionarioRepository;
    private FuncionarioService funcionarioService;

    @BeforeEach
    void setup() {
        funcionarioRepository = mock(FuncionarioRepository.class);
        funcionarioService = new FuncionarioServiceImpl(funcionarioRepository);
    }

    @Test
    void listarComPlantoesAgregaCorretamenteContagemDePlantoesNoMes() {
        Funcionario f1 = new Funcionario(1, "João Silva", "POL001", "63999991111", null, true, null);
        Funcionario f2 = new Funcionario(2, "Maria Souza", "POL002", "63999992222", null, false, null);

        YearMonth mes = YearMonth.of(2026, 9);
        when(funcionarioRepository.listar(null, "Silva")).thenReturn(List.of(f1));
        when(funcionarioRepository.contarPlantoesNoMes(1, mes)).thenReturn(5);

        List<FuncionarioListagemItem> resultado = funcionarioService.listarComPlantoes(null, "Silva", mes);

        assertEquals(1, resultado.size());
        FuncionarioListagemItem item = resultado.get(0);
        assertEquals("João Silva", item.getNome());
        assertEquals("POL001", item.getMatricula());
        assertEquals(5, item.getPlantoesNoMes());
        assertTrue(item.isAtivo());
        verify(funcionarioRepository).listar(null, "Silva");
        verify(funcionarioRepository).contarPlantoesNoMes(1, mes);
    }

    @Test
    void listarComPlantoesUtilizaMesAtualQuandoMesReferenciaForNulo() {
        Funcionario f1 = new Funcionario(1, "Carlos Dias", "POL003", "63999993333", null, true, null);
        when(funcionarioRepository.listar(true, null)).thenReturn(List.of(f1));
        when(funcionarioRepository.contarPlantoesNoMes(eq(1), any(YearMonth.class))).thenReturn(3);

        List<FuncionarioListagemItem> resultado = funcionarioService.listarComPlantoes(true, null, null);

        assertEquals(1, resultado.size());
        assertEquals(3, resultado.get(0).getPlantoesNoMes());
        verify(funcionarioRepository).contarPlantoesNoMes(eq(1), any(YearMonth.class));
    }

    @Test
    void listarSimplesDelegaParaRepositorio() {
        Funcionario f1 = new Funcionario(1, "Carlos Dias", "POL003", "63999993333", null, true, null);
        when(funcionarioRepository.listar(true, "Carlos")).thenReturn(List.of(f1));

        List<Funcionario> lista = funcionarioService.listar(true, "Carlos");
        assertEquals(1, lista.size());
        assertEquals("Carlos Dias", lista.get(0).getNome());
        verify(funcionarioRepository).listar(true, "Carlos");
    }

    @Test
    void buscarPorIdRetornaOptionalDoFuncionario() {
        Funcionario f1 = new Funcionario(1, "Carlos Dias", "POL003", "63999993333", null, true, null);
        when(funcionarioRepository.buscarPorId(1)).thenReturn(Optional.of(f1));
        when(funcionarioRepository.buscarPorId(99)).thenReturn(Optional.empty());

        Optional<Funcionario> encontrado = funcionarioService.buscarPorId(1);
        assertTrue(encontrado.isPresent());
        assertEquals("Carlos Dias", encontrado.get().getNome());

        Optional<Funcionario> naoEncontrado = funcionarioService.buscarPorId(99);
        assertFalse(naoEncontrado.isPresent());
    }

    @Test
    void ativarEDesativarDelegamCorretamenteParaRepositorio() {
        funcionarioService.ativar(1);
        verify(funcionarioRepository).ativar(1);

        funcionarioService.desativar(2);
        verify(funcionarioRepository).desativar(2);
    }

    @Test
    void contarPlantoesNoMesDelegaParaRepositorio() {
        YearMonth mes = YearMonth.of(2026, 8);
        when(funcionarioRepository.contarPlantoesNoMes(1, mes)).thenReturn(4);

        int total = funcionarioService.contarPlantoesNoMes(1, mes);
        assertEquals(4, total);
        verify(funcionarioRepository).contarPlantoesNoMes(1, mes);
    }
}

