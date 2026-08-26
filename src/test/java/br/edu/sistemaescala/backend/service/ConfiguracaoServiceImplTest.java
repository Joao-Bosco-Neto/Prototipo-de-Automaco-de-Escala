package br.edu.sistemaescala.backend.service;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.edu.sistemaescala.backend.model.Configuracao;
import br.edu.sistemaescala.backend.model.RoleUsuario;
import br.edu.sistemaescala.backend.model.Usuario;
import br.edu.sistemaescala.backend.repository.ConfiguracaoRepository;

class ConfiguracaoServiceImplTest {

    private ConfiguracaoRepository configuracaoRepository;
    private SessaoUsuario sessaoUsuario;
    private ConfiguracaoService configuracaoService;

    @BeforeEach
    void setup() {
        configuracaoRepository = mock(ConfiguracaoRepository.class);
        sessaoUsuario = new SessaoUsuario();
        Usuario admin = new Usuario(1, "Admin", "admin", "hash", RoleUsuario.ADMIN, true, null, null);
        sessaoUsuario.iniciar(admin);

        configuracaoService = new ConfiguracaoServiceImpl(configuracaoRepository, sessaoUsuario);
    }

    @Test
    void buscarRetornaConfiguracaoDoRepositorio() {
        Configuracao config = new Configuracao(1, "GOTE", "Polícia Civil", new BigDecimal("160"), "mensal", "/relatorios", null);
        when(configuracaoRepository.buscar()).thenReturn(Optional.of(config));

        Optional<Configuracao> resultado = configuracaoService.buscar();

        assertTrue(resultado.isPresent());
        assertEquals("GOTE", resultado.get().getNomeOrganizacao());
        assertEquals("Polícia Civil", resultado.get().getSubtitulo());
        verify(configuracaoRepository).buscar();
    }

    @Test
    void salvarAtualizaComSucessoQuandoDadosValidos() {
        Configuracao existente = new Configuracao(1, "Nome Antigo", "Sub Antigo", new BigDecimal("180"), "mensal", null, null);
        when(configuracaoRepository.buscar()).thenReturn(Optional.of(existente));
        when(configuracaoRepository.atualizar(any(Configuracao.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Configuracao atualizada = configuracaoService.salvar(
                "  Diretoria do GOTE  ",
                "  Polícia Civil do Tocantins  ",
                new BigDecimal("160.00"),
                "CONTINUO",
                "  C:\\Relatorios\\PDFs  "
        );

        assertNotNull(atualizada);
        assertEquals("Diretoria do GOTE", atualizada.getNomeOrganizacao());
        assertEquals("Polícia Civil do Tocantins", atualizada.getSubtitulo());
        assertEquals(new BigDecimal("160.00"), atualizada.getCargaHorariaMensal());
        assertEquals("continuo", atualizada.getApuracaoBancoHoras());
        assertEquals("C:\\Relatorios\\PDFs", atualizada.getCaminhoPdfPadrao());
        verify(configuracaoRepository).atualizar(existente);
    }

    @Test
    void salvarRejeitaAcessoQuandoNaoForAdministrador() {
        Usuario gestor = new Usuario(2, "Gestor Silva", "gestor", "hash", RoleUsuario.GESTOR, true, null, null);
        sessaoUsuario.iniciar(gestor);

        AcessoNegadoException erro = assertThrows(AcessoNegadoException.class, () ->
                configuracaoService.salvar("GOTE", null, null, "mensal", null));

        assertTrue(erro.getMessage().contains("Apenas administradores"));
    }

    @Test
    void salvarRejeitaNomeEmBranco() {
        RegraConfiguracaoException erroNull = assertThrows(RegraConfiguracaoException.class, () ->
                configuracaoService.salvar(null, "Sub", null, "mensal", null));
        assertEquals("O nome da organização é obrigatório.", erroNull.getMessage());

        RegraConfiguracaoException erroVazio = assertThrows(RegraConfiguracaoException.class, () ->
                configuracaoService.salvar("   ", "Sub", null, "mensal", null));
        assertEquals("O nome da organização é obrigatório.", erroVazio.getMessage());
    }

    @Test
    void salvarRejeitaApuracaoInvalida() {
        RegraConfiguracaoException erro = assertThrows(RegraConfiguracaoException.class, () ->
                configuracaoService.salvar("GOTE", null, null, "SEMANAL", null));
        assertEquals("O regime de apuração do banco de horas deve ser 'mensal' ou 'continuo'.", erro.getMessage());
    }

    @Test
    void salvarRejeitaCargaHorariaNegativa() {
        RegraConfiguracaoException erro = assertThrows(RegraConfiguracaoException.class, () ->
                configuracaoService.salvar("GOTE", null, new BigDecimal("-10"), "mensal", null));
        assertEquals("A carga horária mensal não pode ser negativa.", erro.getMessage());
    }

    @Test
    void salvarAceitaCargaHorariaNulaETrataCamposOpcionaisVaziosComoNulos() {
        Configuracao existente = new Configuracao(1, "GOTE", "Sub", new BigDecimal("160"), "mensal", "/pdf", null);
        when(configuracaoRepository.buscar()).thenReturn(Optional.of(existente));
        when(configuracaoRepository.atualizar(any(Configuracao.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Configuracao resultado = configuracaoService.salvar("GOTE", "   ", null, "mensal", "   ");

        assertEquals("GOTE", resultado.getNomeOrganizacao());
        assertNull(resultado.getSubtitulo());
        assertNull(resultado.getCargaHorariaMensal());
        assertNull(resultado.getCaminhoPdfPadrao());
        assertEquals("mensal", resultado.getApuracaoBancoHoras());
    }
}

