package br.edu.sistemaescala.backend.repository;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
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
    private static boolean configuracaoCriadaPeloTeste;
    private static Integer usuarioId;

    @BeforeAll
    static void prepararBanco() {
        BancoInicializador.inicializar();
        configuracaoOriginal = CONFIGURACAO.buscar().orElseGet(() -> {
            configuracaoCriadaPeloTeste = true;
            return inserirConfiguracaoDeTeste();
        });
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
        if (configuracaoCriadaPeloTeste) {
            try (Connection conexao = ConexaoBanco.getConnection();
                 PreparedStatement stmt = conexao.prepareStatement("DELETE FROM configuracao WHERE id = ?")) {
                stmt.setInt(1, configuracaoOriginal.getId());
                stmt.executeUpdate();
            }
        } else {
            CONFIGURACAO.atualizar(configuracaoOriginal);
        }
    }

    private static Configuracao inserirConfiguracaoDeTeste() {
        String sql = "INSERT INTO configuracao (nome_organizacao, subtitulo) VALUES (?, ?)";
        try (Connection conexao = ConexaoBanco.getConnection();
             PreparedStatement stmt = conexao.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, "Organizacao de teste");
            stmt.setString(2, "Sistema de testes");
            stmt.executeUpdate();
            try (ResultSet chaves = stmt.getGeneratedKeys()) {
                chaves.next();
                Configuracao configuracao = new Configuracao();
                configuracao.setId(chaves.getInt(1));
                configuracao.setNomeOrganizacao("Organizacao de teste");
                configuracao.setSubtitulo("Sistema de testes");
                return configuracao;
            }
        } catch (SQLException excecao) {
            throw new RuntimeException("Falha ao criar configuracao de teste", excecao);
        }
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
    void rejeitaInsercaoDeUsuarioComLoginDuplicado() {
        String loginDuplicado = "teste-login-unico";
        Usuario primeiro = new Usuario(null, "Primeiro", loginDuplicado, "hash1", RoleUsuario.GESTOR, true, null, null);
        Usuario segundo = new Usuario(null, "Segundo", loginDuplicado, "hash2", RoleUsuario.GESTOR, true, null, null);

        USUARIOS.inserir(primeiro);
        try {
            org.junit.jupiter.api.Assertions.assertThrows(RepositoryException.class, () -> USUARIOS.inserir(segundo));
        } finally {
            removerUsuario(primeiro.getId());
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