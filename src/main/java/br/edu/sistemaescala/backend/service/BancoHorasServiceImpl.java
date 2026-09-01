package br.edu.sistemaescala.backend.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import br.edu.sistemaescala.backend.model.Funcionario;
import br.edu.sistemaescala.backend.model.LancamentoHoras;
import br.edu.sistemaescala.backend.model.TipoLancamento;
import br.edu.sistemaescala.backend.repository.BancoHorasRepository;
import br.edu.sistemaescala.backend.repository.LancamentoHorasRepository;
import br.edu.sistemaescala.backend.repository.jdbc.BancoHorasRepositoryJdbc;
import br.edu.sistemaescala.backend.repository.jdbc.LancamentoHorasRepositoryJdbc;

public class BancoHorasServiceImpl implements BancoHorasService {

    /** Janela ampla para o "extrato completo": buscarExtrato exige um período. */
    private static final LocalDate INICIO_DOS_TEMPOS = LocalDate.of(2000, 1, 1);
    private static final LocalDate FIM_DOS_TEMPOS = LocalDate.of(2100, 12, 31);

    private final BancoHorasRepository bancoHorasRepository;
    private final LancamentoHorasRepository lancamentoHorasRepository;

    public BancoHorasServiceImpl() {
        this(new BancoHorasRepositoryJdbc(), new LancamentoHorasRepositoryJdbc());
    }

    public BancoHorasServiceImpl(BancoHorasRepository bancoHorasRepository,
                                 LancamentoHorasRepository lancamentoHorasRepository) {
        this.bancoHorasRepository = Objects.requireNonNull(bancoHorasRepository,
                "bancoHorasRepository não pode ser nulo");
        this.lancamentoHorasRepository = Objects.requireNonNull(lancamentoHorasRepository,
                "lancamentoHorasRepository não pode ser nulo");
    }

    @Override
    public List<BancoHorasListagemItem> listarMensal(YearMonth mesReferencia) {
        return bancoHorasRepository.listarMensal(mesReferencia);
    }

    @Override
    public List<LancamentoHoras> buscarExtrato(int funcionarioId, YearMonth mesReferencia) {
        LocalDate inicio = mesReferencia != null ? mesReferencia.atDay(1) : INICIO_DOS_TEMPOS;
        LocalDate fim = mesReferencia != null ? mesReferencia.atEndOfMonth() : FIM_DOS_TEMPOS;
        return lancamentoHorasRepository.buscarExtrato(funcionarioId, inicio, fim);
    }

    @Override
    public List<ExtratoLancamentoItem> buscarExtratoDetalhado(int funcionarioId, YearMonth mesReferencia) {
        // Regra de Ouro: a soma cumulativa mora aqui, nunca no controller.
        // O repositório já devolve os lançamentos em ordem cronológica
        // (data_referencia ASC, id ASC), então basta um acumulador que anda
        // junto com a iteração.
        List<LancamentoHoras> lancamentos = buscarExtrato(funcionarioId, mesReferencia);

        List<ExtratoLancamentoItem> extrato = new ArrayList<>(lancamentos.size());
        long saldoAcumulado = 0L;
        for (LancamentoHoras lancamento : lancamentos) {
            saldoAcumulado += lancamento.getMinutos();
            extrato.add(new ExtratoLancamentoItem(
                    lancamento.getId() != null ? lancamento.getId() : 0,
                    lancamento.getDataReferencia(),
                    lancamento.getTipo(),
                    lancamento.getDescricao(),
                    lancamento.getMinutos(),
                    saldoAcumulado));
        }
        return extrato;
    }

    @Override
    public LancamentoHoras lancarAjusteManual(int funcionarioId, double horas, boolean credito, String descricao) {
        if (Double.isNaN(horas) || Double.isInfinite(horas) || horas <= 0) {
            throw new RegraBancoHorasException("Informe uma quantidade de horas maior que zero.");
        }
        if (descricao == null || descricao.isBlank()) {
            throw new RegraBancoHorasException("Informe a justificativa do ajuste manual.");
        }

        int minutos = (int) Math.round(horas * 60);
        if (minutos == 0) {
            throw new RegraBancoHorasException("A quantidade informada é pequena demais (menos de 1 minuto).");
        }
        if (!credito) {
            minutos = -minutos;
        }

        Funcionario funcionario = new Funcionario();
        funcionario.setId(funcionarioId);

        LancamentoHoras lancamento = new LancamentoHoras(
                null, funcionario, null, LocalDate.now(), minutos,
                TipoLancamento.AJUSTE_MANUAL, descricao.trim(), null);

        return lancamentoHorasRepository.salvar(lancamento);
    }
}
