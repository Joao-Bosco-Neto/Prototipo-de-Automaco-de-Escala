package br.edu.sistemaescala.backend.model;

import java.time.YearMonth;

/**
 * Opcoes de conteudo da exportacao em PDF da escala mensal (issue #43 / #52).
 *
 * <p>Cada flag liga ou desliga uma secao condicional do documento. O
 * {@code mes} e o unico dado obrigatorio; o restante tem um padrao sensato
 * em {@link #padrao(YearMonth)}, que espelha o estado inicial dos checkboxes
 * da tela: coberturas e assinaturas marcados, telefones e saldo desmarcados.</p>
 *
 * <p>A orientacao e fixa em A4 retrato — o unico formato que o
 * {@code GeradorPdfService} monta —, por isso nao ha campo para ela.</p>
 *
 * @param mes                   mes de referencia da escala
 * @param exibirCoberturas      inclui a secao "Coberturas registradas no periodo"
 * @param exibirTelefones       inclui a secao "Contatos da equipe"
 * @param exibirSaldoBancoHoras inclui a secao "Saldo do banco de horas"
 * @param exibirAssinaturas     inclui as linhas de assinatura (Gestor e Direcao)
 */
public record OpcoesExportacaoPdf(
        YearMonth mes,
        boolean exibirCoberturas,
        boolean exibirTelefones,
        boolean exibirSaldoBancoHoras,
        boolean exibirAssinaturas) {

    public OpcoesExportacaoPdf {
        if (mes == null) {
            throw new IllegalArgumentException("mes de referencia e obrigatorio");
        }
    }

    /** Padrao da tela: coberturas e assinaturas ligados, telefones e saldo desligados. */
    public static OpcoesExportacaoPdf padrao(YearMonth mes) {
        return new OpcoesExportacaoPdf(mes, true, false, false, true);
    }

    public OpcoesExportacaoPdf comMes(YearMonth novoMes) {
        return new OpcoesExportacaoPdf(novoMes, exibirCoberturas, exibirTelefones,
                exibirSaldoBancoHoras, exibirAssinaturas);
    }

    public OpcoesExportacaoPdf comCoberturas(boolean valor) {
        return new OpcoesExportacaoPdf(mes, valor, exibirTelefones, exibirSaldoBancoHoras, exibirAssinaturas);
    }

    public OpcoesExportacaoPdf comTelefones(boolean valor) {
        return new OpcoesExportacaoPdf(mes, exibirCoberturas, valor, exibirSaldoBancoHoras, exibirAssinaturas);
    }

    public OpcoesExportacaoPdf comSaldoBancoHoras(boolean valor) {
        return new OpcoesExportacaoPdf(mes, exibirCoberturas, exibirTelefones, valor, exibirAssinaturas);
    }

    public OpcoesExportacaoPdf comAssinaturas(boolean valor) {
        return new OpcoesExportacaoPdf(mes, exibirCoberturas, exibirTelefones, exibirSaldoBancoHoras, valor);
    }
}
