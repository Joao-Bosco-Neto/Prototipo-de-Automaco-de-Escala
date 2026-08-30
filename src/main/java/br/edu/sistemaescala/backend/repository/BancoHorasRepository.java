package br.edu.sistemaescala.backend.repository;

import java.time.YearMonth;
import java.util.List;

import br.edu.sistemaescala.backend.service.BancoHorasListagemItem;

/**
 * Consulta de apoio à tela de Banco de Horas (issue "Tela Banco de Horas").
 *
 * A listagem é feita numa única consulta: métricas e saldo recortados pelo
 * mesmo período. Quando {@code mesReferencia} é {@code null}, nada é recortado
 * e tudo passa a somar todo o histórico de {@code lancamento_horas}.
 */
public interface BancoHorasRepository {

    /**
     * Um item por funcionário, com as métricas operacionais e o saldo apurados
     * em {@code mesReferencia}. {@code null} soma todo o histórico.
     */
    List<BancoHorasListagemItem> listarMensal(YearMonth mesReferencia);
}
