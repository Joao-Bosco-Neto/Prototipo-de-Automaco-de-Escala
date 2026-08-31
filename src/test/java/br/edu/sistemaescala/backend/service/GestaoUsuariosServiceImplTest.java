package br.edu.sistemaescala.backend.service;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.edu.sistemaescala.backend.model.AcaoSeguranca;
import br.edu.sistemaescala.backend.model.ResultadoSeguranca;
import br.edu.sistemaescala.backend.model.RoleUsuario;
import br.edu.sistemaescala.backend.model.Usuario;
import br.edu.sistemaescala.backend.repository.UsuarioRepository;

class GestaoUsuariosServiceImplTest {

    private UsuarioRepository usuarioRepository;
    private AutenticacaoService autenticacaoService;
    private SessaoUsuario sessaoUsuario;
    private GestaoUsuariosService gestaoUsuariosService;
    private Usuario adminLogado;
    private LogSegurancaFake logSeguranca;

    @BeforeEach
    void prepararMocks() {
        usuarioRepository = mock(UsuarioRepository.class);
        autenticacaoService = mock(AutenticacaoService.class);
        sessaoUsuario = new SessaoUsuario();

        adminLogado = new Usuario();
        adminLogado.setId(1);
        adminLogado.setNome("Admin Principal");
        adminLogado.setLogin("admin");
        adminLogado.setRole(RoleUsuario.ADMIN);
        adminLogado.setAtivo(true);
        sessaoUsuario.iniciar(adminLogado);

        logSeguranca = new LogSegurancaFake();
        gestaoUsuariosService = new GestaoUsuariosServiceImpl(
                usuarioRepository, autenticacaoService, sessaoUsuario,
                new AutorizacaoService(), logSeguranca);
    }

    @Test
    void recusaAcessoParaPerfilGestor() {
        Usuario gestor = new Usuario();
        gestor.setId(2);
        gestor.setRole(RoleUsuario.GESTOR);
        sessaoUsuario.iniciar(gestor);

        assertThrows(AcessoNegadoException.class, () -> gestaoUsuariosService.listar());
        assertThrows(AcessoNegadoException.class,
                () -> gestaoUsuariosService.cadastrar("Novo", "novo", "12345678", "12345678", RoleUsuario.GESTOR, true));
        assertThrows(AcessoNegadoException.class,
                () -> gestaoUsuariosService.atualizar(2, "Novo", "novo", RoleUsuario.GESTOR, true));
        assertThrows(AcessoNegadoException.class,
                () -> gestaoUsuariosService.alterarStatus(2, false));
        assertThrows(AcessoNegadoException.class,
                () -> gestaoUsuariosService.redefinirSenha(2, "novaSenha123", "novaSenha123"));
    }

    @Test
    void recusaAcessoSemUsuarioAutenticado() {
        sessaoUsuario.encerrar();

        assertThrows(AcessoNegadoException.class, () -> gestaoUsuariosService.listar());
        assertThrows(AcessoNegadoException.class,
                () -> gestaoUsuariosService.cadastrar("Novo", "novo", "12345678", "12345678", RoleUsuario.GESTOR, true));
        assertThrows(AcessoNegadoException.class,
                () -> gestaoUsuariosService.atualizar(2, "Novo", "novo", RoleUsuario.GESTOR, true));
        assertThrows(AcessoNegadoException.class,
                () -> gestaoUsuariosService.alterarStatus(2, false));
        assertThrows(AcessoNegadoException.class,
                () -> gestaoUsuariosService.redefinirSenha(2, "novaSenha123", "novaSenha123"));
    }

    @Test
    void listaEBuscaUsuariosComSucesso() {
        when(usuarioRepository.listar()).thenReturn(List.of(adminLogado));
        when(usuarioRepository.buscarPorId(1)).thenReturn(Optional.of(adminLogado));

        List<Usuario> lista = gestaoUsuariosService.listar();
        assertEquals(1, lista.size());
        assertEquals("admin", lista.get(0).getLogin());

        Optional<Usuario> buscado = gestaoUsuariosService.buscarPorId(1);
        assertTrue(buscado.isPresent());
        assertEquals("admin", buscado.get().getLogin());
    }

    @Test
    void cadastraNovoUsuarioComSucesso() {
        when(usuarioRepository.buscarPorLogin("gestor1")).thenReturn(Optional.empty());
        when(autenticacaoService.gerarHash("senhaForte123")).thenReturn("hash-gerado");
        when(usuarioRepository.inserir(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Usuario criado = gestaoUsuariosService.cadastrar(
                "Gestor Um", "gestor1", "senhaForte123", "senhaForte123", RoleUsuario.GESTOR, true);

        assertNotNull(criado);
        assertEquals("Gestor Um", criado.getNome());
        assertEquals("gestor1", criado.getLogin());
        assertEquals("hash-gerado", criado.getSenhaHash());
        assertEquals(RoleUsuario.GESTOR, criado.getRole());
        assertTrue(criado.isAtivo());

        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).inserir(captor.capture());
        assertEquals("gestor1", captor.getValue().getLogin());
    }

    @Test
    void rejeitaCadastroComLoginDuplicado() {
        when(usuarioRepository.buscarPorLogin("admin")).thenReturn(Optional.of(adminLogado));

        assertThrows(RegraUsuarioException.class,
                () -> gestaoUsuariosService.cadastrar("Outro Admin", "admin", "senhaForte123", "senhaForte123", RoleUsuario.ADMIN, true));
        verify(usuarioRepository, never()).inserir(any());
    }

    @Test
    void rejeitaCadastroComSenhaFracaOuIncompativel() {
        when(usuarioRepository.buscarPorLogin("novo")).thenReturn(Optional.empty());

        assertThrows(SenhaFracaException.class,
                () -> gestaoUsuariosService.cadastrar("Novo", "novo", "12345", "12345", RoleUsuario.GESTOR, true));
        assertThrows(SenhasNaoConferemException.class,
                () -> gestaoUsuariosService.cadastrar("Novo", "novo", "senhaForte123", "outraSenha123", RoleUsuario.GESTOR, true));
        verify(usuarioRepository, never()).inserir(any());
    }

    @Test
    void atualizaUsuarioComSucesso() {
        Usuario existente = new Usuario(2, "Gestor Antigo", "gestor", "hash", RoleUsuario.GESTOR, true, null, null);
        when(usuarioRepository.buscarPorId(2)).thenReturn(Optional.of(existente));
        when(usuarioRepository.buscarPorLogin("gestor_novo")).thenReturn(Optional.empty());
        when(usuarioRepository.atualizar(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Usuario atualizado = gestaoUsuariosService.atualizar(2, "Gestor Novo", "gestor_novo", RoleUsuario.ADMIN, true);

        assertEquals("Gestor Novo", atualizado.getNome());
        assertEquals("gestor_novo", atualizado.getLogin());
        assertEquals(RoleUsuario.ADMIN, atualizado.getRole());
        verify(usuarioRepository).atualizar(existente);
    }

    @Test
    void rejeitaAtualizacaoComLoginDuplicadoDeOutroUsuario() {
        Usuario existente = new Usuario(2, "Gestor", "gestor", "hash", RoleUsuario.GESTOR, true, null, null);
        when(usuarioRepository.buscarPorId(2)).thenReturn(Optional.of(existente));
        when(usuarioRepository.buscarPorLogin("admin")).thenReturn(Optional.of(adminLogado));

        assertThrows(RegraUsuarioException.class,
                () -> gestaoUsuariosService.atualizar(2, "Gestor", "admin", RoleUsuario.GESTOR, true));
        verify(usuarioRepository, never()).atualizar(any());
    }

    @Test
    void permiteAtualizacaoMantendoOMesmoLogin() {
        when(usuarioRepository.buscarPorId(1)).thenReturn(Optional.of(adminLogado));
        when(usuarioRepository.buscarPorLogin("admin")).thenReturn(Optional.of(adminLogado));
        when(usuarioRepository.atualizar(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Usuario atualizado = gestaoUsuariosService.atualizar(1, "Admin Alterado", "admin", RoleUsuario.ADMIN, true);
        assertEquals("Admin Alterado", atualizado.getNome());
        verify(usuarioRepository).atualizar(adminLogado);
    }

    @Test
    void impedeAutodesativacaoDoAdministradorLogado() {
        when(usuarioRepository.buscarPorId(1)).thenReturn(Optional.of(adminLogado));

        assertThrows(RegraUsuarioException.class,
                () -> gestaoUsuariosService.atualizar(1, "Admin", "admin", RoleUsuario.ADMIN, false));
        assertThrows(RegraUsuarioException.class,
                () -> gestaoUsuariosService.alterarStatus(1, false));

        verify(usuarioRepository, never()).atualizar(any());
        verify(usuarioRepository, never()).desativar(anyInt());
    }

    @Test
    void impedeDesativacaoOuRebaixamentoDoUltimoAdminAtivo() {
        Usuario segundoAdmin = new Usuario(2, "Admin 2", "admin2", "hash", RoleUsuario.ADMIN, true, null, null);
        when(usuarioRepository.buscarPorId(2)).thenReturn(Optional.of(segundoAdmin));
        // Apenas segundoAdmin esta ativo na lista de admins alem do alvo quando simulamos que adminLogado e inativo ou segundoAdmin e o unico
        when(usuarioRepository.listar()).thenReturn(List.of(segundoAdmin));

        // Tenta rebaixar para GESTOR
        assertThrows(RegraUsuarioException.class,
                () -> gestaoUsuariosService.atualizar(2, "Admin 2", "admin2", RoleUsuario.GESTOR, true));

        // Tenta desativar
        assertThrows(RegraUsuarioException.class,
                () -> gestaoUsuariosService.alterarStatus(2, false));

        verify(usuarioRepository, never()).atualizar(any());
    }

    @Test
    void permiteDesativarSegundoAdminQuandoHaOutroAdminAtivo() {
        Usuario segundoAdmin = new Usuario(2, "Admin 2", "admin2", "hash", RoleUsuario.ADMIN, true, null, null);
        when(usuarioRepository.buscarPorId(2)).thenReturn(Optional.of(segundoAdmin));
        when(usuarioRepository.listar()).thenReturn(List.of(adminLogado, segundoAdmin));
        when(usuarioRepository.atualizar(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));

        gestaoUsuariosService.alterarStatus(2, false);

        assertFalse(segundoAdmin.isAtivo());
        verify(usuarioRepository).atualizar(segundoAdmin);
    }

    @Test
    void reativaUsuarioInativoComSucesso() {
        Usuario inativo = new Usuario(3, "Inativo", "inativo", "hash", RoleUsuario.GESTOR, false, null, null);
        when(usuarioRepository.buscarPorId(3)).thenReturn(Optional.of(inativo));
        when(usuarioRepository.atualizar(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));

        gestaoUsuariosService.alterarStatus(3, true);

        assertTrue(inativo.isAtivo());
        verify(usuarioRepository).atualizar(inativo);
    }

    @Test
    void redefinirSenhaComSucesso() {
        Usuario usuario = new Usuario(2, "Gestor", "gestor", "hashAntigo", RoleUsuario.GESTOR, true, null, null);
        when(usuarioRepository.buscarPorId(2)).thenReturn(Optional.of(usuario));
        when(autenticacaoService.gerarHash("novaSenhaSegura123")).thenReturn("novoHash");

        gestaoUsuariosService.redefinirSenha(2, "novaSenhaSegura123", "novaSenhaSegura123");

        verify(usuarioRepository).atualizarSenha(2, "novoHash");
    }

    @Test
    void redefinirSenhaRejeitaSenhaFracaOuNaoConferente() {
        Usuario usuario = new Usuario(2, "Gestor", "gestor", "hashAntigo", RoleUsuario.GESTOR, true, null, null);
        when(usuarioRepository.buscarPorId(2)).thenReturn(Optional.of(usuario));

        assertThrows(SenhaFracaException.class,
                () -> gestaoUsuariosService.redefinirSenha(2, "curta", "curta"));
        assertThrows(SenhasNaoConferemException.class,
                () -> gestaoUsuariosService.redefinirSenha(2, "senhaSegura123", "outraSenha123"));

        verify(usuarioRepository, never()).atualizarSenha(anyInt(), any());
    }

    // -----------------------------------------------------------------
    // Trilha de auditoria (issue #64)
    // -----------------------------------------------------------------

    @Test
    void cadastroDeUsuarioEntraNaTrilhaSemSenhaNemHash() {
        when(usuarioRepository.buscarPorLogin("gestor1")).thenReturn(Optional.empty());
        when(autenticacaoService.gerarHash("senhaForte123")).thenReturn("hash-gerado");
        when(usuarioRepository.inserir(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));

        gestaoUsuariosService.cadastrar("Gestor Um", "gestor1", "senhaForte123", "senhaForte123",
                RoleUsuario.GESTOR, true);

        LogSegurancaFake.Evento evento = logSeguranca.ultimo();
        assertEquals(AcaoSeguranca.USUARIO_CRIADO, evento.acao());
        assertEquals(ResultadoSeguranca.SUCESSO, evento.resultado());
        // quem agiu fica a cargo da sessão, resolvida dentro do serviço de log
        assertNull(evento.identificacao());
        assertTrue(evento.detalhes().contains("gestor1"));

        String trilha = logSeguranca.textoCompleto();
        assertFalse(trilha.contains("senhaForte123"));
        assertFalse(trilha.contains("hash-gerado"));
    }

    @Test
    void desativacaoEReativacaoEntramNaTrilhaComAcoesDiferentes() {
        Usuario segundoAdmin = new Usuario(2, "Admin 2", "admin2", "hash", RoleUsuario.ADMIN, true, null, null);
        when(usuarioRepository.buscarPorId(2)).thenReturn(Optional.of(segundoAdmin));
        when(usuarioRepository.listar()).thenReturn(List.of(adminLogado, segundoAdmin));
        when(usuarioRepository.atualizar(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));

        gestaoUsuariosService.alterarStatus(2, false);
        gestaoUsuariosService.alterarStatus(2, true);

        assertEquals(1, logSeguranca.eventosDe(AcaoSeguranca.USUARIO_DESATIVADO).size());
        assertEquals(1, logSeguranca.eventosDe(AcaoSeguranca.USUARIO_REATIVADO).size());
    }

    @Test
    void edicaoQueNaoMexeNoStatusNaoGeraEventoDeSeguranca() {
        Usuario existente = new Usuario(2, "Gestor Antigo", "gestor", "hash", RoleUsuario.GESTOR, true, null, null);
        when(usuarioRepository.buscarPorId(2)).thenReturn(Optional.of(existente));
        when(usuarioRepository.buscarPorLogin("gestor_novo")).thenReturn(Optional.empty());
        when(usuarioRepository.atualizar(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));

        gestaoUsuariosService.atualizar(2, "Gestor Novo", "gestor_novo", RoleUsuario.GESTOR, true);

        assertTrue(logSeguranca.eventos().isEmpty());
    }

    @Test
    void redefinicaoDeSenhaEntraNaTrilhaSemASenhaNovaNemOHash() {
        Usuario usuario = new Usuario(2, "Gestor", "gestor", "hashAntigo", RoleUsuario.GESTOR, true, null, null);
        when(usuarioRepository.buscarPorId(2)).thenReturn(Optional.of(usuario));
        when(autenticacaoService.gerarHash("novaSenhaSegura123")).thenReturn("novoHash");

        gestaoUsuariosService.redefinirSenha(2, "novaSenhaSegura123", "novaSenhaSegura123");

        LogSegurancaFake.Evento evento = logSeguranca.ultimo();
        assertEquals(AcaoSeguranca.SENHA_REDEFINIDA, evento.acao());
        assertTrue(evento.detalhes().contains("gestor"));

        String trilha = logSeguranca.textoCompleto();
        assertFalse(trilha.contains("novaSenhaSegura123"));
        assertFalse(trilha.contains("novoHash"));
        assertFalse(trilha.contains("hashAntigo"));
    }
}

