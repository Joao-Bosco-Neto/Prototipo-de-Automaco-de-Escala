package br.edu.sistemaescala.backend.repository;

import java.time.YearMonth;
import java.util.List;

import br.edu.sistemaescala.backend.service.BancoHorasListagemItem;

/**
 * Consulta de apoio à tela de Banco de Horas (issue "Tela Banco de Horas").
 *
 * A listagem mensal é feita numa única consulta: métricas do mês por subquery
 * correlacionada e o saldo do banco de horas somado sobre todo o histórico de
 * {@code lancamento_horas}, sem recorte de mês.
 */
public interface BancoHorasRepository {

    /**
     * Um item por funcionário, com as métricas operacionais apuradas em
     * {@code mesReferencia} e o saldo consolidado (todo o histórico).
     */
    List<BancoHorasListagemItem> listarMensal(YearMonth mesReferencia);
}
