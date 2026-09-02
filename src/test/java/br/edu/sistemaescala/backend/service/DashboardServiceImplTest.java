package br.edu.sistemaescala.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import br.edu.sistemaescala.backend.model.EscalaFuncionario;
import br.edu.sistemaescala.backend.model.EscalaTurno;
import br.edu.sistemaescala.backend.model.Funcionario;
import br.edu.sistemaescala.backend.repository.EscalaFuncionarioRepository;
import br.edu.sistemaescala.backend.repository.EscalaTurnoRepository;
import br.edu.sistemaescala.backend.repository.FuncionarioRepository;

/**
 * O serviço não calcula nada: ele escolhe o recorte e junta o que os
 * repositórios agregam. É isso que estes testes cobrem — que o mês vem do dia
 * informado e que cada número chega ao card certo.
 */
class DashboardServiceImplTest {

    private static final LocalDate DIA = LocalDate.of(2026, 9, 2);

    @Test
    void carregarReuneOsQuatroIndicadoresNoRecorteDoDiaInformado() {
        PlantaoDoDiaItem plantao = new PlantaoDoDiaItem(7, "Turno Diurno 12h",
                DIA.atTime(7, 0), DIA.atTime(19, 0), 2, 2);
        EscalaTurnoFake turnos = new EscalaTurnoFake(List.of(plantao), 4);
        EscalaFuncionarioFake alocacoes = new EscalaFuncionarioFake(3);
        FuncionarioFake funcionarios = new FuncionarioFake(new ContagemFuncionarios(12, 5));

        IndicadoresDashboard indicadores =
                new DashboardServiceImpl(turnos, alocacoes, funcionarios).carregar(DIA);

        assertEquals(DIA, indicadores.diaReferencia());
        assertEquals(YearMonth.of(2026, 9), indicadores.mesReferencia(),
                "o mês dos indicadores mensais sai do dia informado");
        assertEquals(List.of(plantao), indicadores.plantoesDeHoje());
        assertEquals(12, indicadores.funcionarios().ativos());
        assertEquals(5, indicadores.funcionarios().inativos());
        assertEquals(3, indicadores.coberturasNoMes());
        assertEquals(4, indicadores.diasIncompletos());
        assertFalse(indicadores.semPlantaoHoje());

        assertEquals(DIA, turnos.diaConsultado, "o plantão de hoje é consultado pelo dia");
        assertEquals(YearMonth.of(2026, 9), turnos.mesConsultado);
        assertEquals(YearMonth.of(2026, 9), alocacoes.mesConsultado);
    }

    @Test
    void carregarSinalizaDiaSemPlantaoEmVezDeDevolverNulo() {
        IndicadoresDashboard indicadores = new DashboardServiceImpl(
                new EscalaTurnoFake(List.of(), 0),
                new EscalaFuncionarioFake(0),
                new FuncionarioFake(new ContagemFuncionarios(0, 0))).carregar(DIA);

        assertTrue(indicadores.plantoesDeHoje().isEmpty());
        assertTrue(indicadores.semPlantaoHoje(), "o card precisa saber que o dia está sem plantão");
    }

    @Test
    void carregarExigeODiaDeReferencia() {
        DashboardService servico = new DashboardServiceImpl(
                new EscalaTurnoFake(List.of(), 0),
                new EscalaFuncionarioFake(0),
                new FuncionarioFake(new ContagemFuncionarios(0, 0)));

        assertThrows(NullPointerException.class, () -> servico.carregar(null));
    }

    // -----------------------------------------------------------------
    // Dublês: só os métodos que o dashboard usa respondem.
    // -----------------------------------------------------------------

    private static final class EscalaTurnoFake implements EscalaTurnoRepository {

        private final List<PlantaoDoDiaItem> plantoes;
        private final int diasIncompletos;
        private LocalDate diaConsultado;
        private YearMonth mesConsultado;

        private EscalaTurnoFake(List<PlantaoDoDiaItem> plantoes, int diasIncompletos) {
            this.plantoes = plantoes;
            this.diasIncompletos = diasIncompletos;
        }

        @Override
        public List<PlantaoDoDiaItem> resumirPlantoesDoDia(LocalDate dia) {
            diaConsultado = dia;
            return plantoes;
        }

        @Override
        public int contarDiasComEfetivoIncompleto(YearMonth mes) {
            mesConsultado = mes;
            return diasIncompletos;
        }

        @Override
        public List<EscalaTurno> buscarPorPeriodo(LocalDateTime inicio, LocalDateTime fim) {
            throw new UnsupportedOperationException("o dashboard não carrega listas para contar");
        }

        @Override
        public Optional<EscalaTurno> buscarPorId(int id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public EscalaTurno salvar(EscalaTurno turno) {
            throw new UnsupportedOperationException();
        }

        @Override
        public EscalaTurno salvar(EscalaTurno turno, Connection conexao) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void removerPorMes(YearMonth mes) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void removerPorMes(YearMonth mes, Connection conexao) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class EscalaFuncionarioFake implements EscalaFuncionarioRepository {

        private final int coberturas;
        private YearMonth mesConsultado;

        private EscalaFuncionarioFake(int coberturas) {
            this.coberturas = coberturas;
        }

        @Override
        public int contarCoberturasDoMes(YearMonth mes) {
            mesConsultado = mes;
            return coberturas;
        }

        @Override
        public List<EscalaFuncionario> listarPorTurno(int escalaTurnoId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<EscalaFuncionario> listarPorFuncionario(int funcionarioId,
                                                            LocalDateTime inicio, LocalDateTime fim) {
            throw new UnsupportedOperationException();
        }

        @Override
        public EscalaFuncionario inserir(EscalaFuncionario escalaFuncionario) {
            throw new UnsupportedOperationException();
        }

        @Override
        public EscalaFuncionario inserir(EscalaFuncionario escalaFuncionario, Connection conexao) {
            throw new UnsupportedOperationException();
        }

        @Override
        public EscalaFuncionario atualizar(EscalaFuncionario escalaFuncionario, Connection conexao) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void remover(int id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void remover(int id, Connection conexao) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<EscalaFuncionario> buscarCoberturasDoMes(YearMonth mes) {
            throw new UnsupportedOperationException("o dashboard conta no banco, não em memória");
        }

        @Override
        public List<CoberturaListagemItem> listarCoberturasParaListagem(YearMonth mes) {
            throw new UnsupportedOperationException("o dashboard conta no banco, não em memória");
        }
    }

    private static final class FuncionarioFake implements FuncionarioRepository {

        private final ContagemFuncionarios contagem;

        private FuncionarioFake(ContagemFuncionarios contagem) {
            this.contagem = contagem;
        }

        @Override
        public ContagemFuncionarios contarPorStatus() {
            return contagem;
        }

        @Override
        public List<Funcionario> listar(Boolean ativo, String textoBusca) {
            throw new UnsupportedOperationException("o dashboard conta no banco, não em memória");
        }

        @Override
        public Optional<Funcionario> buscarPorId(int id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Funcionario inserir(Funcionario funcionario) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Funcionario atualizar(Funcionario funcionario) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void ativar(int id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void desativar(int id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean existeMatricula(String matricula, Integer idParaExcluir) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int contarPlantoesNoMes(int funcionarioId, YearMonth mes) {
            throw new UnsupportedOperationException();
        }
    }
}
