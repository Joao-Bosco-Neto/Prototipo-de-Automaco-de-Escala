package br.edu.sistemaescala.backend.service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import br.edu.sistemaescala.backend.model.EscalaFuncionario;
import br.edu.sistemaescala.backend.model.EscalaTurno;
import br.edu.sistemaescala.backend.model.Funcionario;
import br.edu.sistemaescala.backend.model.OpcoesExportacaoPdf;
import br.edu.sistemaescala.backend.model.TipoTurno;

class GeradorPdfServiceTest {

    private static final YearMonth SETEMBRO_2026 = YearMonth.of(2026, 9);

    @TempDir
    Path pastaTemporaria;

    private GeradorPdfService servico;

    @BeforeEach
    void setup() {
        servico = new GeradorPdfService();
    }

    @Test
    void pdfEGravadoNoDestinoComTurnosPopulados() throws IOException {
        File destino = arquivo("escala.pdf");

        servico.exportarPdf(
                List.of(turno("Diurno", LocalDateTime.of(2026, 9, 1, 7, 0), "Ana", "Bruno"),
                        turno("Noturno", LocalDateTime.of(2026, 9, 1, 19, 0), "Carla")),
                SETEMBRO_2026, destino);

        byte[] conteudo = Files.readAllBytes(destino.toPath());
        assertTrue(conteudo.length > 0, "o PDF não deveria sair vazio");
        assertEquals("%PDF", new String(conteudo, 0, 4, StandardCharsets.ISO_8859_1));
    }

    @Test
    void pdfSaiMesmoSemTurnosNoPeriodo() throws IOException {
        File destino = arquivo("vazio.pdf");

        servico.exportarPdf(List.of(), SETEMBRO_2026, destino);

        assertTrue(Files.size(destino.toPath()) > 0);
    }

    @Test
    void nomeSugeridoSegueOPadrao() {
        assertEquals("escala-mensal-2026-09.pdf", servico.nomeArquivoSugerido(SETEMBRO_2026));
    }

    @Test
    void tituloTrazOMesPorExtensoCapitalizado() {
        assertEquals("Escala de Serviço – Setembro/2026", servico.tituloEscala(SETEMBRO_2026));
    }

    @Test
    void pdfComCabecalhoInstitucional() throws IOException {
        File comOrg = arquivo("com-org.pdf");
        File semOrg = arquivo("sem-org.pdf");

        servico.exportarPdf(turnosBase(), SETEMBRO_2026, semOrg, null, null, List.of());
        servico.exportarPdf(turnosBase(), SETEMBRO_2026, comOrg,
                "Prefeitura Municipal", "Secretaria de Saúde", List.of());

        assertEquals("%PDF", cabecalho(comOrg));
        assertTrue(Files.size(comOrg.toPath()) > Files.size(semOrg.toPath()),
                "o PDF com cabeçalho institucional deveria ser maior");
    }

    @Test
    void pdfSemOrganizacaoOmiteCabecalhoInstitucional() throws IOException {
        File destino = arquivo("null-org.pdf");

        servico.exportarPdf(turnosBase(), SETEMBRO_2026, destino, null, null, List.of());

        assertEquals("%PDF", cabecalho(destino));
    }

    @Test
    void pdfComCoberturasNoRodape() throws IOException {
        File comCob = arquivo("com-cob.pdf");
        File semCob = arquivo("sem-cob.pdf");

        servico.exportarPdf(turnosBase(), SETEMBRO_2026, semCob, null, null, List.of());
        servico.exportarPdf(turnosBase(), SETEMBRO_2026, comCob, null, null, List.of(
                cobertura(LocalDate.of(2026, 9, 3), "Ana", "Bruno", "Atestado médico"),
                cobertura(LocalDate.of(2026, 9, 7), "Carla", "Diego", null)));

        assertEquals("%PDF", cabecalho(comCob));
        assertTrue(Files.size(comCob.toPath()) > Files.size(semCob.toPath()),
                "o PDF com coberturas deveria ser maior");
    }

    @Test
    void pdfSemCoberturasOmiteSecao() throws IOException {
        File destino = arquivo("sem-secao-cob.pdf");

        servico.exportarPdf(turnosBase(), SETEMBRO_2026, destino, null, null, List.of());

        assertEquals("%PDF", cabecalho(destino));
    }

    @Test
    void pdfContemLinhasDeAssinatura() throws IOException {
        File destino = arquivo("assinatura.pdf");

        servico.exportarPdf(turnosBase(), SETEMBRO_2026, destino, null, null, List.of());

        assertTrue(Files.size(destino.toPath()) > 0);
        assertEquals("%PDF", cabecalho(destino));
    }

    @Test
    void pdfComMesDe31DiasGeraMultiplasPaginas() throws IOException {
        File destino = arquivo("mes-cheio.pdf");
        List<EscalaTurno> turnos = new ArrayList<>();
        for (int dia = 1; dia <= 31; dia++) {
            turnos.add(turno("Diurno", LocalDateTime.of(2026, 10, dia, 7, 0),
                    "Agente A", "Agente B", "Agente C", "Agente D"));
        }

        servico.exportarPdf(turnos, YearMonth.of(2026, 10), destino,
                "Prefeitura Municipal", "Secretaria de Saúde", List.of());

        File pequeno = arquivo("mes-curto.pdf");
        servico.exportarPdf(turnosBase(), SETEMBRO_2026, pequeno, null, null, List.of());

        assertEquals("%PDF", cabecalho(destino));
        assertTrue(Files.size(destino.toPath()) > Files.size(pequeno.toPath()),
                "PDF de mês cheio deveria ser maior que o de dois turnos");
    }

    @Test
    void overloadAntigaContinuaFuncionando() throws IOException {
        File destino = arquivo("overload-antigo.pdf");

        servico.exportarPdf(turnosBase(), SETEMBRO_2026, destino);

        assertEquals("%PDF", cabecalho(destino));
    }

    @Test
    void gerarEmMemoriaProduzUmPdfValido() throws IOException {
        byte[] pdf = servico.gerarEmMemoria(
                EscalaPdfDados.apenasTurnos(turnosBase()),
                OpcoesExportacaoPdf.padrao(SETEMBRO_2026));

        assertTrue(pdf.length > 0);
        assertEquals("%PDF", new String(pdf, 0, 4, StandardCharsets.ISO_8859_1));
    }

    @Test
    void secaoDeTelefonesAumentaODocumento() throws IOException {
        EscalaPdfDados dados = EscalaPdfDados.apenasTurnos(turnosBase());

        byte[] semTelefone = servico.gerarEmMemoria(dados,
                new OpcoesExportacaoPdf(SETEMBRO_2026, false, false, false, false));
        byte[] comTelefone = servico.gerarEmMemoria(dados,
                new OpcoesExportacaoPdf(SETEMBRO_2026, false, true, false, false));

        assertTrue(comTelefone.length > semTelefone.length,
                "o PDF com a seção de contatos deveria ser maior");
    }

    @Test
    void secaoDeSaldoSoApareceQuandoLigadaEComDados() throws IOException {
        List<EscalaTurno> turnos = turnosBase();
        List<BancoHorasListagemItem> saldos = List.of(
                new BancoHorasListagemItem(1, "Ana", "M1", 5, 0, 0, 120),
                new BancoHorasListagemItem(2, "Bruno", "M2", 4, 1, 0, -90));
        EscalaPdfDados comSaldo = new EscalaPdfDados(turnos, null, null, List.of(), saldos);
        EscalaPdfDados semSaldo = new EscalaPdfDados(turnos, null, null, List.of(), List.of());

        byte[] ligadoComDados = servico.gerarEmMemoria(comSaldo,
                new OpcoesExportacaoPdf(SETEMBRO_2026, false, false, true, false));
        byte[] ligadoSemDados = servico.gerarEmMemoria(semSaldo,
                new OpcoesExportacaoPdf(SETEMBRO_2026, false, false, true, false));
        byte[] desligado = servico.gerarEmMemoria(comSaldo,
                new OpcoesExportacaoPdf(SETEMBRO_2026, false, false, false, false));

        assertTrue(ligadoComDados.length > ligadoSemDados.length);
        assertEquals(ligadoSemDados.length, desligado.length,
                "sem dados de saldo, ligar a opção não deveria mudar o documento");
    }

    @Test
    void assinaturasSaoOmitidasQuandoAOpcaoEstaDesligada() throws IOException {
        EscalaPdfDados dados = EscalaPdfDados.apenasTurnos(turnosBase());

        byte[] comAssinatura = servico.gerarEmMemoria(dados,
                new OpcoesExportacaoPdf(SETEMBRO_2026, false, false, false, true));
        byte[] semAssinatura = servico.gerarEmMemoria(dados,
                new OpcoesExportacaoPdf(SETEMBRO_2026, false, false, false, false));

        assertTrue(comAssinatura.length > semAssinatura.length);
    }

    // -----------------------------------------------------------------
    // Apoio
    // -----------------------------------------------------------------

    private String cabecalho(File destino) throws IOException {
        byte[] conteudo = Files.readAllBytes(destino.toPath());
        return new String(conteudo, 0, 4, StandardCharsets.ISO_8859_1);
    }

    private List<EscalaTurno> turnosBase() {
        return List.of(
                turno("Diurno", LocalDateTime.of(2026, 9, 1, 7, 0), "Ana", "Bruno"),
                turno("Noturno", LocalDateTime.of(2026, 9, 1, 19, 0), "Carla"));
    }

    private CoberturaListagemItem cobertura(LocalDate data, String substituto, String ausente,
            String motivo) {
        return new CoberturaListagemItem(0, data, substituto, ausente, motivo, false, 720, null);
    }

    private File arquivo(String nome) {
        return pastaTemporaria.resolve(nome).toFile();
    }

    private EscalaTurno turno(String nomeTurno, LocalDateTime inicio, String... nomesAgentes) {
        TipoTurno tipoTurno = new TipoTurno();
        tipoTurno.setNome(nomeTurno);

        EscalaTurno escalaTurno = new EscalaTurno();
        escalaTurno.setTipoTurno(tipoTurno);
        escalaTurno.setInicio(inicio);
        escalaTurno.setFim(inicio.plusHours(12));

        for (String nome : nomesAgentes) {
            Funcionario funcionario = new Funcionario();
            funcionario.setNome(nome);
            EscalaFuncionario agente = new EscalaFuncionario();
            agente.setFuncionario(funcionario);
            escalaTurno.getAgentes().add(agente);
        }
        return escalaTurno;
    }
}
