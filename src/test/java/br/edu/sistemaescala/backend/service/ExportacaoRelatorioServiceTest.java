package br.edu.sistemaescala.backend.service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExportacaoRelatorioServiceTest {

    private static final YearMonth MARCO_2026 = YearMonth.of(2026, 3);

    @TempDir
    Path pastaTemporaria;

    private ExportacaoRelatorioService servico;

    @BeforeEach
    void setup() {
        servico = new ExportacaoRelatorioService();
    }

    @Test
    void csvComecaComOPeriodoApurado() throws IOException {
        File destino = arquivo("relatorio.csv");

        servico.exportarCsv(List.of(item("Ana", 90)), MARCO_2026, destino);

        assertEquals("Relatório de Apuração - Março/2026", linhas(destino).get(0));
    }

    @Test
    void csvSemMesIdentificaOHistoricoCompleto() throws IOException {
        File destino = arquivo("historico.csv");

        servico.exportarCsv(List.of(item("Ana", 90)), null, destino);

        assertEquals("Relatório de Apuração - Histórico completo", linhas(destino).get(0));
    }

    @Test
    void csvTrazCabecalhoEUmaLinhaPorFuncionario() throws IOException {
        File destino = arquivo("relatorio.csv");

        servico.exportarCsv(List.of(item("Ana", 150), item("Bruno", -90)), MARCO_2026, destino);

        List<String> linhas = linhas(destino);
        // 0: titulo, 1: gerado em, 2: linha em branco, 3: cabecalho, 4+: dados
        assertEquals("Matrícula;Funcionário;Plantões cumpridos;Coberturas feitas;"
                + "Plantões cobertos;Saldo", linhas.get(3));
        assertEquals(6, linhas.size());
        assertTrue(linhas.get(4).endsWith(";+2h30"), linhas.get(4));
        assertTrue(linhas.get(5).endsWith(";-1h30"), linhas.get(5));
    }

    @Test
    void csvEscapaOSeparadorDentroDoNome() throws IOException {
        File destino = arquivo("relatorio.csv");

        servico.exportarCsv(List.of(item("Silva; Ana", 0)), MARCO_2026, destino);

        assertTrue(linhas(destino).get(4).contains("\"Silva; Ana\""), linhas(destino).get(4));
    }

    @Test
    void pdfEGravadoNoDestinoEscolhido() throws IOException {
        File destino = arquivo("relatorio.pdf");

        servico.exportarPdf(List.of(item("Ana", 90), item("Bruno", -30)), MARCO_2026, destino);

        byte[] conteudo = Files.readAllBytes(destino.toPath());
        assertTrue(conteudo.length > 0, "o PDF não deveria sair vazio");
        assertEquals("%PDF", new String(conteudo, 0, 4, StandardCharsets.ISO_8859_1));
    }

    @Test
    void pdfSaiMesmoSemFuncionarioNoPeriodo() throws IOException {
        File destino = arquivo("vazio.pdf");

        servico.exportarPdf(List.of(), MARCO_2026, destino);

        assertTrue(Files.size(destino.toPath()) > 0);
    }

    @Test
    void nomeSugeridoUsaOMesApurado() {
        assertEquals("banco-de-horas-2026-03.pdf", servico.nomeArquivoSugerido(MARCO_2026, "pdf"));
        assertEquals("banco-de-horas-historico-completo.csv", servico.nomeArquivoSugerido(null, "csv"));
    }

    // -----------------------------------------------------------------
    // Apoio
    // -----------------------------------------------------------------

    private File arquivo(String nome) {
        return pastaTemporaria.resolve(nome).toFile();
    }

    /** Le o CSV descartando o BOM, que atrapalharia a comparacao da 1a linha. */
    private List<String> linhas(File destino) throws IOException {
        List<String> linhas = Files.readAllLines(destino.toPath(), StandardCharsets.UTF_8);
        if (!linhas.isEmpty() && linhas.get(0).startsWith("\uFEFF")) {
            linhas.set(0, linhas.get(0).substring(1));
        }
        return linhas;
    }

    private BancoHorasListagemItem item(String nome, long saldoMinutos) {
        return new BancoHorasListagemItem(1, nome, "M001", 2, 1, 0, saldoMinutos);
    }
}
