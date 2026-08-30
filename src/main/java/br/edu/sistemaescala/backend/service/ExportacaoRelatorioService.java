package br.edu.sistemaescala.backend.service;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.FontFactory;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;

/**
 * Escrita dos relatorios do Banco de Horas em disco (CSV e PDF).
 *
 * <p>Existe para que o controller nao precise conhecer nem I/O de arquivo nem
 * layout de PDF: ele so escolhe o destino pelo {@code FileChooser} e entrega a
 * lista que ja esta na tela.</p>
 *
 * <p>A lista recebida e a mesma que a tela exibe — ou seja, ja vem recortada
 * pelo mes de apuracao na consulta do repositorio (apuracao estanque: o saldo
 * soma apenas os lancamentos com {@code data_referencia} dentro do mes). O
 * servico nao recalcula nada, so formata; assim o arquivo gerado nunca diverge
 * do que o usuario viu antes de exportar.</p>
 *
 * <p>O periodo apurado vai impresso na primeira linha de conteudo dos dois
 * formatos, para que o arquivo continue interpretavel depois de sair do
 * sistema.</p>
 */
public class ExportacaoRelatorioService {

    /** Separador do CSV: o Excel em pt-BR usa ponto e virgula como separador de lista. */
    private static final char SEPARADOR_CSV = ';';

    /**
     * BOM de UTF-8. Sem ele o Excel abre o arquivo em Cp1252 e os acentos
     * viram caractere quebrado; outros leitores ignoram o marcador.
     */
    private static final String BOM_UTF8 = "\uFEFF";

    private static final DateTimeFormatter GERADO_EM =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    private static final String[] CABECALHO_COLUNAS = {
            "Matrícula", "Funcionário", "Plantões cumpridos",
            "Coberturas feitas", "Plantões cobertos", "Saldo"
    };

    /**
     * Grava a listagem em CSV.
     *
     * @param itens   linhas exibidas na tela, ja recortadas pelo periodo
     * @param periodo mes apurado, ou {@code null} para o historico completo
     * @param destino arquivo escolhido pelo usuario no {@code FileChooser}
     * @throws IOException se o arquivo nao puder ser escrito
     */
    public void exportarCsv(List<BancoHorasListagemItem> itens, YearMonth periodo, File destino)
            throws IOException {
        try (Writer escritor = Files.newBufferedWriter(destino.toPath(), StandardCharsets.UTF_8)) {
            escritor.write(BOM_UTF8);
            escreverLinhaCsv(escritor, new String[] { tituloRelatorio(periodo) });
            escreverLinhaCsv(escritor, new String[] { "Gerado em " + LocalDateTime.now().format(GERADO_EM) });
            escritor.write(System.lineSeparator());

            escreverLinhaCsv(escritor, CABECALHO_COLUNAS);
            for (BancoHorasListagemItem item : itens) {
                escreverLinhaCsv(escritor, colunasDe(item));
            }
        }
    }

    /**
     * Gera a listagem em PDF (A4 retrato, uma tabela).
     *
     * @param itens   linhas exibidas na tela, ja recortadas pelo periodo
     * @param periodo mes apurado, ou {@code null} para o historico completo
     * @param destino arquivo escolhido pelo usuario no {@code FileChooser}
     * @throws IOException se o arquivo nao puder ser escrito ou se o PDF nao
     *                     puder ser montado
     */
    public void exportarPdf(List<BancoHorasListagemItem> itens, YearMonth periodo, File destino)
            throws IOException {
        Document documento = new Document(PageSize.A4, 36, 36, 44, 36);
        try (OutputStream saida = Files.newOutputStream(destino.toPath())) {
            PdfWriter.getInstance(documento, saida);
            documento.open();

            Paragraph titulo = new Paragraph(tituloRelatorio(periodo),
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 15));
            titulo.setSpacingAfter(4f);
            documento.add(titulo);

            Paragraph geradoEm = new Paragraph("Gerado em " + LocalDateTime.now().format(GERADO_EM),
                    FontFactory.getFont(FontFactory.HELVETICA, 9));
            geradoEm.setSpacingAfter(14f);
            documento.add(geradoEm);

            documento.add(montarTabela(itens));

            Paragraph nota = new Paragraph(
                    "Saldo positivo indica horas a compensar em favor do funcionário; "
                            + "negativo, horas devidas.",
                    FontFactory.getFont(FontFactory.HELVETICA, 8));
            nota.setSpacingBefore(12f);
            documento.add(nota);

            documento.close();
        } catch (DocumentException e) {
            // A falha de montagem do PDF chega ao chamador como falha de
            // escrita: para quem exporta, o arquivo simplesmente nao saiu.
            throw new IOException("Não foi possível montar o PDF do relatório.", e);
        }
    }

    /**
     * Titulo impresso na primeira linha de conteudo dos dois formatos, com o
     * periodo apurado — e o que permite distinguir um relatorio do outro
     * depois de salvos lado a lado.
     */
    public String tituloRelatorio(YearMonth periodo) {
        return "Relatório de Apuração - " + rotuloPeriodo(periodo);
    }

    /** {@code Março/2026}, ou {@code Histórico completo} quando nao ha mes. */
    public String rotuloPeriodo(YearMonth periodo) {
        if (periodo == null) {
            return "Histórico completo";
        }
        String mes = periodo.getMonth().getDisplayName(TextStyle.FULL, PT_BR);
        return mes.substring(0, 1).toUpperCase(PT_BR) + mes.substring(1) + "/" + periodo.getYear();
    }

    /**
     * Nome sugerido no dialogo de salvamento. O usuario continua livre para
     * trocar nome e pasta — aqui so evitamos o "sem titulo" do sistema.
     */
    public String nomeArquivoSugerido(YearMonth periodo, String extensao) {
        String sufixo = periodo != null
                ? String.format("%04d-%02d", periodo.getYear(), periodo.getMonthValue())
                : "historico-completo";
        return "banco-de-horas-" + sufixo + "." + extensao;
    }

    // -----------------------------------------------------------------
    // PDF
    // -----------------------------------------------------------------

    private PdfPTable montarTabela(List<BancoHorasListagemItem> itens) throws DocumentException {
        PdfPTable tabela = new PdfPTable(CABECALHO_COLUNAS.length);
        tabela.setWidthPercentage(100);
        tabela.setWidths(new float[] { 1.1f, 2.6f, 1.3f, 1.3f, 1.3f, 1.0f });
        tabela.setHeaderRows(1);

        Font fonteCabecalho = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
        for (String coluna : CABECALHO_COLUNAS) {
            PdfPCell celula = new PdfPCell(new Paragraph(coluna, fonteCabecalho));
            celula.setPadding(5f);
            celula.setHorizontalAlignment(Element.ALIGN_CENTER);
            celula.setGrayFill(0.9f);
            tabela.addCell(celula);
        }

        Font fonteCorpo = FontFactory.getFont(FontFactory.HELVETICA, 9);
        if (itens.isEmpty()) {
            PdfPCell vazio = new PdfPCell(new Paragraph(
                    "Nenhum funcionário no período apurado.", fonteCorpo));
            vazio.setColspan(CABECALHO_COLUNAS.length);
            vazio.setPadding(8f);
            vazio.setHorizontalAlignment(Element.ALIGN_CENTER);
            tabela.addCell(vazio);
            return tabela;
        }

        for (BancoHorasListagemItem item : itens) {
            String[] valores = colunasDe(item);
            for (int coluna = 0; coluna < valores.length; coluna++) {
                PdfPCell celula = new PdfPCell(new Paragraph(valores[coluna], fonteCorpo));
                celula.setPadding(5f);
                // So o nome fica a esquerda; matricula, contagens e saldo centralizam.
                celula.setHorizontalAlignment(coluna == 1 ? Element.ALIGN_LEFT : Element.ALIGN_CENTER);
                tabela.addCell(celula);
            }
        }
        return tabela;
    }

    // -----------------------------------------------------------------
    // Apoio
    // -----------------------------------------------------------------

    private String[] colunasDe(BancoHorasListagemItem item) {
        return new String[] {
                item.matricula() != null ? item.matricula() : "",
                item.nome() != null ? item.nome() : "",
                String.valueOf(item.plantoesCumpridos()),
                String.valueOf(item.coberturasFeitas()),
                String.valueOf(item.plantoesCobertos()),
                formatarSaldo(item.saldoMinutos())
        };
    }

    private void escreverLinhaCsv(Writer escritor, String[] valores) throws IOException {
        StringBuilder linha = new StringBuilder();
        for (int i = 0; i < valores.length; i++) {
            if (i > 0) {
                linha.append(SEPARADOR_CSV);
            }
            linha.append(escapar(valores[i]));
        }
        escritor.write(linha.toString());
        escritor.write(System.lineSeparator());
    }

    /**
     * Aspas duplas em volta do campo quando ele contem separador, aspas ou
     * quebra de linha — e aspas internas duplicadas, conforme a RFC 4180.
     */
    private String escapar(String valor) {
        if (valor == null) {
            return "";
        }
        boolean precisaAspas = valor.indexOf(SEPARADOR_CSV) >= 0
                || valor.indexOf('"') >= 0
                || valor.indexOf('\n') >= 0
                || valor.indexOf('\r') >= 0;
        if (!precisaAspas) {
            return valor;
        }
        return '"' + valor.replace("\"", "\"\"") + '"';
    }

    /** Mesmo formato do selo de saldo da tela: {@code +12h30}, {@code -3h}, {@code 0h}. */
    private String formatarSaldo(long minutos) {
        String sinal = minutos > 0 ? "+" : minutos < 0 ? "-" : "";
        long abs = Math.abs(minutos);
        long horas = abs / 60;
        long resto = abs % 60;
        return sinal + horas + "h" + (resto > 0 ? String.format("%02d", resto) : "");
    }
}
