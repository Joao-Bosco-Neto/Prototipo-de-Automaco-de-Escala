package br.edu.sistemaescala.backend.service;

import java.util.List;

import br.edu.sistemaescala.backend.model.EscalaTurno;

/**
 * Pacote de dados ja consultados que alimenta o {@link GeradorPdfService}.
 *
 * <p>Segue o mesmo principio do resto do servico: quem chama (controller ou
 * teste) faz as consultas ao banco e entrega tudo pronto; o gerador so
 * formata. As listas condicionais podem vir vazias — o gerador decide o que
 * imprimir cruzando isto com as flags de {@link
 * br.edu.sistemaescala.backend.model.OpcoesExportacaoPdf}.</p>
 *
 * @param turnos          turnos do periodo, hidratados com tipo de turno e agentes
 * @param nomeOrganizacao nome para o cabecalho institucional; {@code null}/vazio omite o bloco
 * @param subtitulo       subtitulo institucional; {@code null}/vazio omite a linha
 * @param coberturas      coberturas registradas no periodo; vazia omite a secao
 * @param saldosBancoHoras linha por funcionario com o saldo do mes; vazia omite a secao
 */
public record EscalaPdfDados(
        List<EscalaTurno> turnos,
        String nomeOrganizacao,
        String subtitulo,
        List<CoberturaListagemItem> coberturas,
        List<BancoHorasListagemItem> saldosBancoHoras) {

    public EscalaPdfDados {
        turnos = turnos != null ? List.copyOf(turnos) : List.of();
        coberturas = coberturas != null ? List.copyOf(coberturas) : List.of();
        saldosBancoHoras = saldosBancoHoras != null ? List.copyOf(saldosBancoHoras) : List.of();
    }

    /** Apenas os turnos, sem cabecalho institucional nem secoes extras. */
    public static EscalaPdfDados apenasTurnos(List<EscalaTurno> turnos) {
        return new EscalaPdfDados(turnos, null, null, List.of(), List.of());
    }
}
