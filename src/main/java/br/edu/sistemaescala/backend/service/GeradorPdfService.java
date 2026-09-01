package br.edu.sistemaescala.backend.service;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

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

import br.edu.sistemaescala.backend.model.EscalaTurno;

/**
 * Geracao do PDF da escala mensal de servico.
 *
 * <p>Segue o mesmo padrao do {@link ExportacaoRelatorioService}: classe concreta,
 * sem interface, que recebe os dados ja prontos e o {@link File} de destino. Quem
 * chama (controller ou teste) e responsavel pela consulta ao repositorio; o
 * servico apenas formata o que recebe.</p>
 *
 * <p>Fontes built-in Helvetica (Type 1, ISO-8859-1) cobrem a acentuacao
 * portuguesa — mesma escolha do {@code ExportacaoRelatorioService}.</p>
 */
public class GeradorPdfService {

    /** Margens em pontos: esquerda, direita, topo, base. Identico ao ExportacaoRelatorioService. */
    private static final float MARGEM_ESQUERDA = 36f;
    private static final float MARGEM_DIREITA = 36f;
    private static final float MARGEM_TOPO = 44f;
    private static final float MARGEM_BASE = 36f;

    private static final DateTimeFormatter GERADO_EM =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter DATA_CURTA =
            DateTimeFormatter.ofPattern("dd/MM");
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    private static final String[] CABECALHO_COLUNAS = { "Data", "Dia", "Turno", "Equipe" };

    /** Indice da coluna Equipe: unica alinhada a esquerda, as demais centralizam. */
    private static final int COLUNA_EQUIPE = 3;

    /**
     * Gera o PDF da escala mensal (A4 retrato, uma tabela).
     *
     * @param turnos  turnos do periodo, ja hidratados com tipo de turno e agentes
     * @param mes     mes de referencia da escala
     * @param destino arquivo escolhido pelo usuario no {@code FileChooser}
     * @throws IOException se o arquivo nao puder ser escrito ou o PDF nao puder ser montado
     */
    public void exportarPdf(List<EscalaTurno> turnos, YearMonth mes, File destino) throws IOException {
        exportarPdf(turnos, mes, destino, null, null, List.of());
    }

    /**
     * Gera o PDF completo da escala mensal: cabecalho institucional, tabela de
     * turnos, secao de coberturas e linhas de assinatura.
     *
     * @param turnos           turnos do periodo, ja hidratados com tipo de turno e agentes
     * @param mes              mes de referencia da escala
     * @param destino          arquivo escolhido pelo usuario no {@code FileChooser}
     * @param nomeOrganizacao  nome da organizacao para o cabecalho institucional;
     *                         {@code null} ou vazio omite o bloco inteiro
     * @param subtitulo        subtitulo institucional; {@code null} ou vazio omite a linha
     * @param coberturas       coberturas registradas no periodo; lista vazia omite a secao
     * @throws IOException se o arquivo nao puder ser escrito ou o PDF nao puder ser montado
     */
    public void exportarPdf(
            List<EscalaTurno> turnos,
            YearMonth mes,
            File destino,
            String nomeOrganizacao,
            String subtitulo,
            List<CoberturaListagemItem> coberturas) throws IOException {
        Document documento = new Document(PageSize.A4,
                MARGEM_ESQUERDA, MARGEM_DIREITA, MARGEM_TOPO, MARGEM_BASE);
        try (OutputStream saida = Files.newOutputStream(destino.toPath())) {
            PdfWriter.getInstance(documento, saida);
            documento.open();

            adicionarCabecalhoInstitucional(documento, nomeOrganizacao, subtitulo);

            Paragraph titulo = new Paragraph(tituloEscala(mes),
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 15));
            titulo.setSpacingAfter(4f);
            documento.add(titulo);

            Paragraph geradoEm = new Paragraph(
                    "Gerado em " + LocalDateTime.now().format(GERADO_EM),
                    FontFactory.getFont(FontFactory.HELVETICA, 9));
            geradoEm.setSpacingAfter(14f);
            documento.add(geradoEm);

            documento.add(montarTabela(turnos));

            adicionarSecaoCoberturas(documento, coberturas);
            adicionarLinhasDeAssinatura(documento);

            Paragraph rodape = new Paragraph(
                    "Documento gerado pelo Sistema de Escala.",
                    FontFactory.getFont(FontFactory.HELVETICA, 8));
            rodape.setSpacingBefore(12f);
            documento.add(rodape);

            documento.close();
        } catch (DocumentException e) {
            // A falha de montagem do PDF chega ao chamador como falha de escrita:
            // para quem exporta, o arquivo simplesmente nao saiu.
            throw new IOException("Não foi possível montar o PDF da escala.", e);
        }
    }

    /**
     * Nome sugerido no dialogo de salvamento. O usuario continua livre para
     * trocar nome e pasta.
     */
    public String nomeArquivoSugerido(YearMonth mes) {
        return String.format("escala-mensal-%04d-%02d.pdf", mes.getYear(), mes.getMonthValue());
    }

    /** Titulo do cabecalho: {@code Escala de Serviço – Setembro/2026}. */
    public String tituloEscala(YearMonth mes) {
        String nomeMes = mes.getMonth().getDisplayName(TextStyle.FULL, PT_BR);
        String mesCapitalizado = nomeMes.substring(0, 1).toUpperCase(PT_BR) + nomeMes.substring(1);
        return "Escala de Serviço – " + mesCapitalizado + "/" + mes.getYear();
    }

    // -----------------------------------------------------------------
    // Tabela
    // -----------------------------------------------------------------

    private PdfPTable montarTabela(List<EscalaTurno> turnos) throws DocumentException {
        PdfPTable tabela = new PdfPTable(CABECALHO_COLUNAS.length);
        tabela.setWidthPercentage(100);
        tabela.setWidths(new float[] { 1.0f, 1.0f, 1.6f, 4.4f });
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
        if (turnos.isEmpty()) {
            PdfPCell vazio = new PdfPCell(new Paragraph("Nenhum turno no período.", fonteCorpo));
            vazio.setColspan(CABECALHO_COLUNAS.length);
            vazio.setPadding(8f);
            vazio.setHorizontalAlignment(Element.ALIGN_CENTER);
            tabela.addCell(vazio);
            return tabela;
        }

        turnos.stream()
                .sorted(Comparator.comparing(EscalaTurno::getInicio))
                .forEach(turno -> {
                    String[] valores = colunasDe(turno);
                    for (int coluna = 0; coluna < valores.length; coluna++) {
                        PdfPCell celula = new PdfPCell(new Paragraph(valores[coluna], fonteCorpo));
                        celula.setPadding(5f);
                        celula.setHorizontalAlignment(
                                coluna == COLUNA_EQUIPE ? Element.ALIGN_LEFT : Element.ALIGN_CENTER);
                        tabela.addCell(celula);
                    }
                });
        return tabela;
    }

    private String[] colunasDe(EscalaTurno turno) {
        LocalDateTime inicio = turno.getInicio();
        String dia = inicio.getDayOfWeek().getDisplayName(TextStyle.SHORT, PT_BR);
        String nomeTurno = turno.getTipoTurno() != null ? turno.getTipoTurno().getNome() : "";
        String equipe = turno.getAgentes().stream()
                .map(agente -> agente.getFuncionario() != null ? agente.getFuncionario().getNome() : "")
                .collect(Collectors.joining(", "));
        return new String[] { inicio.format(DATA_CURTA), dia, nomeTurno, equipe };
    }

    // -----------------------------------------------------------------
    // Cabecalho institucional
    // -----------------------------------------------------------------

    /**
     * Bloco institucional acima do titulo da escala. Omitido por inteiro quando
     * {@code nomeOrganizacao} e nulo ou vazio.
     */
    private void adicionarCabecalhoInstitucional(Document documento, String nomeOrganizacao,
            String subtitulo) throws DocumentException {
        if (nomeOrganizacao == null || nomeOrganizacao.isBlank()) {
            return;
        }
        Paragraph nome = new Paragraph(nomeOrganizacao.trim(),
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13));
        nome.setAlignment(Element.ALIGN_CENTER);
        documento.add(nome);

        if (subtitulo != null && !subtitulo.isBlank()) {
            Paragraph sub = new Paragraph(subtitulo.trim(),
                    FontFactory.getFont(FontFactory.HELVETICA, 10));
            sub.setAlignment(Element.ALIGN_CENTER);
            documento.add(sub);
        }

        Paragraph espacador = new Paragraph(" ", FontFactory.getFont(FontFactory.HELVETICA, 1));
        espacador.setSpacingAfter(10f);
        documento.add(espacador);
    }

    // -----------------------------------------------------------------
    // Coberturas
    // -----------------------------------------------------------------

    /**
     * Secao "Coberturas registradas no periodo" apos a tabela. Omitida quando a
     * lista esta vazia.
     */
    private void adicionarSecaoCoberturas(Document documento, List<CoberturaListagemItem> coberturas)
            throws DocumentException {
        if (coberturas == null || coberturas.isEmpty()) {
            return;
        }

        Paragraph titulo = new Paragraph("Coberturas registradas no período",
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10));
        titulo.setSpacingBefore(14f);
        titulo.setSpacingAfter(6f);
        documento.add(titulo);

        Font fonteLinha = FontFactory.getFont(FontFactory.HELVETICA, 8);
        for (CoberturaListagemItem cobertura : coberturas) {
            documento.add(new Paragraph(linhaCobertura(cobertura), fonteLinha));
        }
    }

    /** {@code dd/MM — Fulano substituiu Beltrano (Motivo: ...)} — motivo omitido se nulo. */
    private String linhaCobertura(CoberturaListagemItem cobertura) {
        StringBuilder linha = new StringBuilder();
        if (cobertura.dataPlantao() != null) {
            linha.append(cobertura.dataPlantao().format(DATA_CURTA)).append(" — ");
        }
        linha.append(cobertura.nomeSubstituto())
                .append(" substituiu ")
                .append(cobertura.nomeAusente());
        if (cobertura.motivoDescricao() != null && !cobertura.motivoDescricao().isBlank()) {
            linha.append(" (Motivo: ").append(cobertura.motivoDescricao()).append(")");
        }
        return linha.toString();
    }

    // -----------------------------------------------------------------
    // Assinaturas
    // -----------------------------------------------------------------

    /** Duas colunas com traco horizontal e rotulo, sem bordas visiveis, ao final do documento. */
    private void adicionarLinhasDeAssinatura(Document documento) throws DocumentException {
        PdfPTable tabela = new PdfPTable(2);
        tabela.setWidthPercentage(100);
        tabela.setSpacingBefore(30f);

        tabela.addCell(celulaAssinatura("Gestor de Escala"));
        tabela.addCell(celulaAssinatura("Direção"));

        documento.add(tabela);
    }

    private PdfPCell celulaAssinatura(String rotulo) {
        PdfPCell traco = new PdfPCell(new Paragraph(" ", FontFactory.getFont(FontFactory.HELVETICA, 9)));
        traco.setBorderWidth(0f);
        traco.setBorderWidthBottom(0.5f);
        traco.setPaddingTop(24f);

        PdfPCell legenda = new PdfPCell(new Paragraph(rotulo, FontFactory.getFont(FontFactory.HELVETICA, 9)));
        legenda.setBorderWidth(0f);
        legenda.setHorizontalAlignment(Element.ALIGN_CENTER);
        legenda.setPaddingTop(3f);

        // Celula externa sem borda, empilhando traco + legenda numa tabela aninhada.
        PdfPTable interna = new PdfPTable(1);
        interna.setWidthPercentage(80);
        interna.addCell(traco);
        interna.addCell(legenda);

        PdfPCell externa = new PdfPCell(interna);
        externa.setBorderWidth(0f);
        externa.setHorizontalAlignment(Element.ALIGN_CENTER);
        externa.setPaddingTop(6f);
        return externa;
    }
}
