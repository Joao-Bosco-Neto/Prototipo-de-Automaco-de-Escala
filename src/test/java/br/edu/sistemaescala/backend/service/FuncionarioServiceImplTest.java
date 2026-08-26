package br.edu.sistemaescala.backend.service;

import java.time.LocalDateTime;
import java.time.YearMonth;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.edu.sistemaescala.backend.model.EscalaFuncionario;
import br.edu.sistemaescala.backend.model.Funcionario;
import br.edu.sistemaescala.backend.repository.EscalaFuncionarioRepository;
import br.edu.sistemaescala.backend.repository.FuncionarioRepository;

class FuncionarioServiceImplTest {

    private FuncionarioRepository funcionarioRepository;
    private EscalaFuncionarioRepository escalaFuncionarioRepository;
    private FuncionarioService funcionarioService;

    @BeforeEach
    void setup() {
        funcionarioRepository = mock(FuncionarioRepository.class);
        escalaFuncionarioRepository = mock(EscalaFuncionarioRepository.class);
        funcionarioService = new FuncionarioServiceImpl(funcionarioRepository, escalaFuncionarioRepository);
    }

    @Test
    void cadastrarFuncionarioComSucesso() {
        when(funcionarioRepository.existeMatricula("POL001", null)).thenReturn(false);
        when(funcionarioRepository.inserir(any(Funcionario.class))).thenAnswer(invocation -> {
            Funcionario f = invocation.getArgument(0);
            f.setId(10);
            return f;
        });

        Funcionario criado = funcionarioService.cadastrar("  João Silva  ", "  POL001  ", "(63) 99999-1111", "Obs", true);

        assertNotNull(criado);
        assertEquals(10, criado.getId());
        assertEquals("João Silva", criado.getNome());
        assertEquals("POL001", criado.getMatricula());
        assertEquals("(63) 99999-1111", criado.getTelefone());
        assertEquals("Obs", criado.getObservacoes());
        assertTrue(criado.isAtivo());
        verify(funcionarioRepository).inserir(any(Funcionario.class));
    }

    @Test
    void cadastrarRejeitaNomeEmBranco() {
        RegraFuncionarioException erroNull = assertThrows(RegraFuncionarioException.class,
                () -> funcionarioService.cadastrar(null, "POL001", null, null, true));
        assertEquals("O nome do funcionário é obrigatório.", erroNull.getMessage());

        RegraFuncionarioException erroVazio = assertThrows(RegraFuncionarioException.class,
                () -> funcionarioService.cadastrar("   ", "POL001", null, null, true));
        assertEquals("O nome do funcionário é obrigatório.", erroVazio.getMessage());
    }

    @Test
    void cadastrarRejeitaMatriculaEmBranco() {
        RegraFuncionarioException erroNull = assertThrows(RegraFuncionarioException.class,
                () -> funcionarioService.cadastrar("João Silva", null, null, null, true));
        assertEquals("A matrícula do funcionário é obrigatória.", erroNull.getMessage());

        RegraFuncionarioException erroVazio = assertThrows(RegraFuncionarioException.class,
                () -> funcionarioService.cadastrar("João Silva", "   ", null, null, true));
        assertEquals("A matrícula do funcionário é obrigatória.", erroVazio.getMessage());
    }

    @Test
    void cadastrarRejeitaMatriculaDuplicada() {
        when(funcionarioRepository.existeMatricula("POL001", null)).thenReturn(true);

        RegraFuncionarioException erro = assertThrows(RegraFuncionarioException.class,
                () -> funcionarioService.cadastrar("João Silva", "POL001", null, null, true));
        assertEquals("Já existe um funcionário cadastrado com a matrícula 'POL001'.", erro.getMessage());
    }

    @Test
    void atualizarFuncionarioComSucesso() {
        Funcionario existente = new Funcionario(5, "Antigo Nome", "POL005", "111", null, true, null);
        when(funcionarioRepository.buscarPorId(5)).thenReturn(Optional.of(existente));
        when(funcionarioRepository.existeMatricula("POL005-NOVA", 5)).thenReturn(false);
        when(funcionarioRepository.atualizar(any(Funcionario.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Funcionario atualizado = funcionarioService.atualizar(5, "Novo Nome", "POL005-NOVA", "222", "Nova obs", false);

        assertNotNull(atualizado);
        assertEquals("Novo Nome", atualizado.getNome());
        assertEquals("POL005-NOVA", atualizado.getMatricula());
        assertEquals("222", atualizado.getTelefone());
        assertEquals("Nova obs", atualizado.getObservacoes());
        assertFalse(atualizado.isAtivo());
        verify(funcionarioRepository).atualizar(existente);
    }

    @Test
    void atualizarRejeitaFuncionarioNaoEncontrado() {
        when(funcionarioRepository.buscarPorId(99)).thenReturn(Optional.empty());

        RegraFuncionarioException erro = assertThrows(RegraFuncionarioException.class,
                () -> funcionarioService.atualizar(99, "Nome", "POL99", null, null, true));
        assertEquals("Funcionário não encontrado.", erro.getMessage());
    }

    @Test
    void atualizarRejeitaMatriculaDuplicadaDeOutroFuncionario() {
        Funcionario existente = new Funcionario(5, "Nome", "POL005", null, null, true, null);
        when(funcionarioRepository.buscarPorId(5)).thenReturn(Optional.of(existente));
        when(funcionarioRepository.existeMatricula("POL006", 5)).thenReturn(true);

        RegraFuncionarioException erro = assertThrows(RegraFuncionarioException.class,
                () -> funcionarioService.atualizar(5, "Nome", "POL006", null, null, true));
        assertEquals("Já existe um funcionário cadastrado com a matrícula 'POL006'.", erro.getMessage());
    }

    @Test
    void listarComPlantoesAgregaCorretamenteContagemDePlantoesNoMes() {
        Funcionario f1 = new Funcionario(1, "João Silva", "POL001", "63999991111", null, true, null);

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

    @Test
    void contarEBuscarPlantoesFuturosConsultamEscalaFuncionarioRepository() {
        LocalDateTime agora = LocalDateTime.of(2026, 8, 26, 14, 0);
        EscalaFuncionario ef1 = new EscalaFuncionario();
        ef1.setId(100);
        EscalaFuncionario ef2 = new EscalaFuncionario();
        ef2.setId(101);

        when(escalaFuncionarioRepository.listarPorFuncionario(eq(1), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(ef1, ef2));

        int totalFuturos = funcionarioService.contarPlantoesFuturos(1, agora);
        assertEquals(2, totalFuturos);

        List<EscalaFuncionario> listaFuturos = funcionarioService.buscarPlantoesFuturos(1, agora);
        assertEquals(2, listaFuturos.size());
        verify(escalaFuncionarioRepository, org.mockito.Mockito.times(2))
                .listarPorFuncionario(eq(1), any(LocalDateTime.class), any(LocalDateTime.class));
    }
}
