package br.edu.sistemaescala.backend.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.edu.sistemaescala.backend.model.Funcionario;
import br.edu.sistemaescala.backend.model.LancamentoHoras;
import br.edu.sistemaescala.backend.model.TipoLancamento;
import br.edu.sistemaescala.backend.repository.BancoHorasRepository;
import br.edu.sistemaescala.backend.repository.LancamentoHorasRepository;

/**
 * Testes da apuração do extrato individual detalhado (issue #49).
 *
 * <p>Foco no saldo acumulado progressivo: o controller não soma nada, então a
 * soma cumulativa linha a linha é responsabilidade — e é testada aqui — do
 * {@link BancoHorasServiceImpl}.</p>
 */
class BancoHorasServiceImplTest {

    private static final int FUNCIONARIO_ID = 7;

    private BancoHorasRepository bancoHorasRepository;
    private LancamentoHorasRepository lancamentoHorasRepository;
    private BancoHorasService servico;

    @BeforeEach
    void setup() {
        bancoHorasRepository = mock(BancoHorasRepository.class);
        lancamentoHorasRepository = mock(LancamentoHorasRepository.class);
        servico = new BancoHorasServiceImpl(bancoHorasRepository, lancamentoHorasRepository);
    }

    private LancamentoHoras lancamento(int id, LocalDate data, int minutos, TipoLancamento tipo, String descricao) {
        Funcionario funcionario = new Funcionario();
        funcionario.setId(FUNCIONARIO_ID);
        return new LancamentoHoras(id, funcionario, null, data, minutos, tipo, descricao,
                LocalDateTime.of(data, java.time.LocalTime.NOON));
    }

    @Test
    void saldoAcumuladoAndaLinhaALinhaComCreditosEDebitosMisturados() {
        YearMonth mes = YearMonth.of(2026, 3);
        when(lancamentoHorasRepository.buscarExtrato(eq(FUNCIONARIO_ID),
                eq(mes.atDay(1)), eq(mes.atEndOfMonth())))
                .thenReturn(List.of(
                        lancamento(1, LocalDate.of(2026, 3, 2), 120, TipoLancamento.CREDITO_COBERTURA, "Cobriu fulano"),
                        lancamento(2, LocalDate.of(2026, 3, 10), -720, TipoLancamento.DEBITO_AUSENCIA, "Faltou"),
                        lancamento(3, LocalDate.of(2026, 3, 15), 60, TipoLancamento.CREDITO_EXTRA, "Hora extra"),
                        lancamento(4, LocalDate.of(2026, 3, 20), 480, TipoLancamento.AJUSTE_MANUAL, "Ajuste")));

        List<ExtratoLancamentoItem> extrato = servico.buscarExtratoDetalhado(FUNCIONARIO_ID, mes);

        assertEquals(4, extrato.size());
        assertEquals(120, extrato.get(0).saldoAcumuladoMinutos());
        assertEquals(-600, extrato.get(1).saldoAcumuladoMinutos());
        assertEquals(-540, extrato.get(2).saldoAcumuladoMinutos());
        assertEquals(-60, extrato.get(3).saldoAcumuladoMinutos());

        assertEquals(1, extrato.get(0).id());
        assertEquals(-720, extrato.get(1).variacaoMinutos());
        assertEquals(TipoLancamento.CREDITO_EXTRA, extrato.get(2).tipo());
        assertEquals("Ajuste", extrato.get(3).descricao());
    }

    @Test
    void saldoFinalDaUltimaLinhaBateComASomaDasVariacoes() {
        YearMonth mes = YearMonth.of(2026, 5);
        List<LancamentoHoras> lancamentos = List.of(
                lancamento(1, LocalDate.of(2026, 5, 1), 200, TipoLancamento.CREDITO_COBERTURA, null),
                lancamento(2, LocalDate.of(2026, 5, 3), -50, TipoLancamento.DEBITO_AUSENCIA, null),
                lancamento(3, LocalDate.of(2026, 5, 9), 15, TipoLancamento.AJUSTE_MANUAL, null));
        when(lancamentoHorasRepository.buscarExtrato(eq(FUNCIONARIO_ID),
                eq(mes.atDay(1)), eq(mes.atEndOfMonth()))).thenReturn(lancamentos);

        List<ExtratoLancamentoItem> extrato = servico.buscarExtratoDetalhado(FUNCIONARIO_ID, mes);

        long somaVariacoes = extrato.stream().mapToLong(ExtratoLancamentoItem::variacaoMinutos).sum();
        assertEquals(somaVariacoes, extrato.get(extrato.size() - 1).saldoAcumuladoMinutos());
        assertEquals(165, extrato.get(extrato.size() - 1).saldoAcumuladoMinutos());
    }

    @Test
    void extratoDeMesSemLancamentosVemVazio() {
        YearMonth mes = YearMonth.of(2026, 1);
        when(lancamentoHorasRepository.buscarExtrato(eq(FUNCIONARIO_ID),
                eq(mes.atDay(1)), eq(mes.atEndOfMonth()))).thenReturn(List.of());

        assertTrue(servico.buscarExtratoDetalhado(FUNCIONARIO_ID, mes).isEmpty());
    }

    @Test
    void periodoNuloUsaJanelaAmplaEAcumulaTodoOHistorico() {
        when(lancamentoHorasRepository.buscarExtrato(eq(FUNCIONARIO_ID),
                eq(LocalDate.of(2000, 1, 1)), eq(LocalDate.of(2100, 12, 31))))
                .thenReturn(List.of(
                        lancamento(1, LocalDate.of(2024, 12, 31), 100, TipoLancamento.CREDITO_COBERTURA, null),
                        lancamento(2, LocalDate.of(2025, 2, 1), 100, TipoLancamento.CREDITO_EXTRA, null)));

        List<ExtratoLancamentoItem> extrato = servico.buscarExtratoDetalhado(FUNCIONARIO_ID, null);

        assertEquals(2, extrato.size());
        assertEquals(100, extrato.get(0).saldoAcumuladoMinutos());
        assertEquals(200, extrato.get(1).saldoAcumuladoMinutos());
    }
}
