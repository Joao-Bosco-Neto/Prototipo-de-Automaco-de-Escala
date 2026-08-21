package br.edu.sistemaescala.backend.repository;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.AfterAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import br.edu.sistemaescala.backend.dao.BancoInicializador;
import br.edu.sistemaescala.backend.dao.ConexaoBanco;
import br.edu.sistemaescala.backend.model.Configuracao;
import br.edu.sistemaescala.backend.model.RoleUsuario;
import br.edu.sistemaescala.backend.model.Usuario;
import br.edu.sistemaescala.backend.repository.jdbc.ConfiguracaoRepositoryJdbc;
import br.edu.sistemaescala.backend.repository.jdbc.UsuarioRepositoryJdbc;

class UsuarioConfiguracaoRepositoryJdbcTest {

    private static final UsuarioRepository USUARIOS = new UsuarioRepositoryJdbc();
    private static final ConfiguracaoRepository CONFIGURACAO = new ConfiguracaoRepositoryJdbc();
    private static final String LOGIN_TESTE = "teste-repositorio-usuario";
    private static Configuracao configuracaoOriginal;
    private static Integer usuarioId;

    @BeforeAll
    static void prepararBanco() {
        BancoInicializador.inicializar();
        configuracaoOriginal = CONFIGURACAO.buscar().orElseThrow();
    }

    @AfterAll
    static void limparBanco() throws SQLException {
        if (usuarioId != null) {
            try (Connection conexao = ConexaoBanco.getConnection();
                 PreparedStatement stmt = conexao.prepareStatement("DELETE FROM usuario WHERE id = ?")) {
                stmt.setInt(1, usuarioId);
                stmt.executeUpdate();
            }
        }
        CONFIGURACAO.atualizar(configuracaoOriginal);
    }

    @Test
    void executaOperacoesDeUsuario() {
        Usuario usuario = new Usuario(null, "Usuario de teste", LOGIN_TESTE, "hash-inicial",
                RoleUsuario.GESTOR, true, null, null);

        USUARIOS.inserir(usuario);
        usuarioId = usuario.getId();

        Optional<Usuario> encontrado = USUARIOS.buscarPorLogin(LOGIN_TESTE);
        assertTrue(encontrado.isPresent());
        assertEquals("Usuario de teste", encontrado.orElseThrow().getNome());
        assertTrue(USUARIOS.listar().stream().anyMatch(item -> item.getId().equals(usuarioId)));

        usuario.setNome("Usuario atualizado");
        usuario.setLogin("teste-repositorio-atualizado");
        usuario.setRole(RoleUsuario.ADMIN);
        USUARIOS.atualizar(usuario);
        assertEquals("Usuario atualizado", USUARIOS.buscarPorLogin("teste-repositorio-atualizado").orElseThrow().getNome());

        LocalDateTime ultimoLogin = LocalDateTime.of(2026, 8, 18, 12, 30);
        USUARIOS.atualizarSenha(usuarioId, "hash-atualizado");
        USUARIOS.registrarUltimoLogin(usuarioId, ultimoLogin);
        Usuario aposLogin = USUARIOS.buscarPorLogin("teste-repositorio-atualizado").orElseThrow();
        assertEquals("hash-atualizado", aposLogin.getSenhaHash());
        assertEquals(ultimoLogin, aposLogin.getUltimoLogin());

        USUARIOS.desativar(usuarioId);
        assertFalse(USUARIOS.buscarPorLogin("teste-repositorio-atualizado").orElseThrow().isAtivo());
    }

    @Test
    void buscaPorLoginRetornaUsuarioCorretoQuandoNomesSaoIguais() {
        String nomeComum = "Usuario Homonimo";
        Usuario primeiro = new Usuario(null, nomeComum, "login-homonimo-1", "hash-1",
                RoleUsuario.GESTOR, true, null, null);
        Usuario segundo = new Usuario(null, nomeComum, "login-homonimo-2", "hash-2",
                RoleUsuario.GESTOR, true, null, null);
        USUARIOS.inserir(primeiro);
        USUARIOS.inserir(segundo);

        try {
            Usuario encontrado = USUARIOS.buscarPorLogin("login-homonimo-2").orElseThrow();
            assertEquals(segundo.getId(), encontrado.getId());
            assertEquals("login-homonimo-2", encontrado.getLogin());
        } finally {
            removerUsuario(primeiro.getId());
            removerUsuario(segundo.getId());
        }
    }

    private static void removerUsuario(int id) {
        try (Connection conexao = ConexaoBanco.getConnection();
             PreparedStatement stmt = conexao.prepareStatement("DELETE FROM usuario WHERE id = ?")) {
            stmt.setInt(1, id);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void leEAtualizaConfiguracaoUnica() {
        Configuracao atualizada = new Configuracao(
                configuracaoOriginal.getId(), "Organizacao de teste", "Subtitulo de teste",
                new BigDecimal("160.00"), "continuo", "C:/teste.pdf", configuracaoOriginal.getAtualizadoEm());

        Configuracao resultado = CONFIGURACAO.atualizar(atualizada);

        assertNotNull(resultado.getAtualizadoEm());
        assertEquals("Organizacao de teste", CONFIGURACAO.buscar().orElseThrow().getNomeOrganizacao());
        assertEquals("continuo", resultado.getApuracaoBancoHoras());
        assertEquals(new BigDecimal("160.00"), resultado.getCargaHorariaMensal());
    }
}