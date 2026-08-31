package br.edu.sistemaescala.backend.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.edu.sistemaescala.backend.model.AcaoSeguranca;
import br.edu.sistemaescala.backend.model.LogSeguranca;
import br.edu.sistemaescala.backend.model.ResultadoSeguranca;
import br.edu.sistemaescala.backend.model.RoleUsuario;
import br.edu.sistemaescala.backend.model.Usuario;
import br.edu.sistemaescala.backend.repository.LogSegurancaRepository;
import br.edu.sistemaescala.backend.repository.RepositoryException;

/**
 * Testes do serviço de auditoria (issue #64), todos em memória: o que
 * interessa aqui é o tratamento do texto antes de gravar, não o SQL.
 */
class LogSegurancaServiceImplTest {

    /** Repositório em memória, com a opção de falhar para testar o fecho seguro. */
    private static class RepositorioEmMemoria implements LogSegurancaRepository {
        private final List<LogSeguranca> gravados = new ArrayList<>();
        private boolean falhar;

        @Override
        public LogSeguranca inserir(LogSeguranca log) {
            if (falhar) {
                throw new RepositoryException("falha simulada de gravação", null);
            }
            gravados.add(log);
            return log;
        }

        @Override
        public List<LogSeguranca> listarPorPeriodo(LocalDateTime inicio, LocalDateTime fim) {
            return List.copyOf(gravados);
        }
    }

    private RepositorioEmMemoria repositorio;
    private SessaoUsuario sessaoUsuario;
    private LogSegurancaService logSegurancaService;

    @BeforeEach
    void preparar() {
        repositorio = new RepositorioEmMemoria();
        sessaoUsuario = new SessaoUsuario();
        logSegurancaService = new LogSegurancaServiceImpl(repositorio, sessaoUsuario);
    }

    private LogSeguranca unicoGravado() {
        assertEquals(1, repositorio.gravados.size());
        return repositorio.gravados.get(0);
    }

    @Test
    void gravaOEventoComDataHoraDoJavaTime() {
        LocalDateTime antes = LocalDateTime.now();

        logSegurancaService.registrar(AcaoSeguranca.LOGIN, "fulano", ResultadoSeguranca.SUCESSO, null);

        LogSeguranca evento = unicoGravado();
        assertNotNull(evento.getDataHora());
        assertFalse(evento.getDataHora().isBefore(antes));
        assertEquals(AcaoSeguranca.LOGIN, evento.getAcao());
        assertEquals(ResultadoSeguranca.SUCESSO, evento.getResultado());
        assertEquals("fulano", evento.getIdentificacao());
    }

    @Test
    void removeQuebrasDeLinhaDaIdentificacaoParaNaoForjarLinhaNaTrilha() {
        String forjado = "fulano\nlogin | admin | sucesso";

        logSegurancaService.registrar(AcaoSeguranca.LOGIN, forjado, ResultadoSeguranca.FALHA, "usuario inexistente");

        String identificacao = unicoGravado().getIdentificacao();
        assertFalse(identificacao.contains("\n"));
        assertFalse(identificacao.contains("\r"));
        assertEquals("fulano login | admin | sucesso", identificacao);
    }

    @Test
    void removeQuebrasDeLinhaTambemDosDetalhes() {
        logSegurancaService.registrar(AcaoSeguranca.LOGIN, "fulano", ResultadoSeguranca.FALHA,
                "motivo\r\ncom quebra\u2028de linha");

        String detalhes = unicoGravado().getDetalhes();
        assertFalse(detalhes.contains("\r"));
        assertFalse(detalhes.contains("\n"));
        assertFalse(detalhes.contains("\u2028"));
    }

    @Test
    void truncaNoLimiteDasColunasDoSchema() {
        String identificacaoEnorme = "a".repeat(400);
        String detalhesEnormes = "b".repeat(900);

        logSegurancaService.registrar(AcaoSeguranca.LOGIN, identificacaoEnorme,
                ResultadoSeguranca.FALHA, detalhesEnormes);

        LogSeguranca evento = unicoGravado();
        assertEquals(150, evento.getIdentificacao().length());
        assertEquals(500, evento.getDetalhes().length());
    }

    @Test
    void semIdentificacaoExplicitaUsaOLoginDaSessao() {
        Usuario admin = new Usuario(1, "Admin", "admin", "hash", RoleUsuario.ADMIN, true, null, null);
        sessaoUsuario.iniciar(admin);

        logSegurancaService.registrar(AcaoSeguranca.USUARIO_CRIADO, ResultadoSeguranca.SUCESSO, "conta criada");

        assertEquals("admin", unicoGravado().getIdentificacao());
    }

    @Test
    void semSessaoAbertaAIdentificacaoNaoFicaNulaPorqueAColunaEObrigatoria() {
        logSegurancaService.registrar(AcaoSeguranca.ESCALA_MES_LIMPA, ResultadoSeguranca.SUCESSO, "mês 2026-08");

        assertFalse(unicoGravado().getIdentificacao().isBlank());
    }

    @Test
    void falhaAoGravarNaoDerrubaAOperacaoAuditada() {
        repositorio.falhar = true;

        assertDoesNotThrow(() -> logSegurancaService.registrar(
                AcaoSeguranca.LOGOUT, "fulano", ResultadoSeguranca.SUCESSO, null));
    }
}
