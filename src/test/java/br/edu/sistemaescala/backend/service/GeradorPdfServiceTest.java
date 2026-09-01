package br.edu.sistemaescala.backend.service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import br.edu.sistemaescala.backend.model.EscalaFuncionario;
import br.edu.sistemaescala.backend.model.EscalaTurno;
import br.edu.sistemaescala.backend.model.Funcionario;
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

    // -----------------------------------------------------------------
    // Apoio
    // -----------------------------------------------------------------

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
