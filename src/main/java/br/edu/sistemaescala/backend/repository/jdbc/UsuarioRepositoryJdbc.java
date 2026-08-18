package br.edu.sistemaescala.backend.repository.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import br.edu.sistemaescala.backend.dao.ConexaoBanco;
import br.edu.sistemaescala.backend.model.RoleUsuario;
import br.edu.sistemaescala.backend.model.Usuario;
import br.edu.sistemaescala.backend.repository.RepositoryException;
import br.edu.sistemaescala.backend.repository.UsuarioRepository;

public class UsuarioRepositoryJdbc implements UsuarioRepository {

    private static final String SQL_BUSCAR_POR_NOME = """
            SELECT id, nome, login, senha_hash, role, ativo, ultimo_login, criado_em
            FROM usuario
            WHERE nome = ?
            ORDER BY id
            LIMIT 1
            """;

    private static final String SQL_LISTAR = """
            SELECT id, nome, login, senha_hash, role, ativo, ultimo_login, criado_em
            FROM usuario
            ORDER BY nome, id
            """;

    private static final String SQL_INSERIR = """
            INSERT INTO usuario (nome, login, senha_hash, role, ativo, ultimo_login)
            VALUES (?, ?, ?, ?, ?, ?)
            """;

    private static final String SQL_ATUALIZAR = """
            UPDATE usuario
            SET nome = ?, login = ?, role = ?, ativo = ?
            WHERE id = ?
            """;

    private static final String SQL_DESATIVAR = "UPDATE usuario SET ativo = FALSE WHERE id = ?";

    private static final String SQL_ATUALIZAR_SENHA = "UPDATE usuario SET senha_hash = ? WHERE id = ?";

    private static final String SQL_REGISTRAR_LOGIN = "UPDATE usuario SET ultimo_login = ? WHERE id = ?";

    @Override
    public Optional<Usuario> buscarPorNome(String nome) {
        try (Connection conexao = ConexaoBanco.getConnection();
             PreparedStatement stmt = conexao.prepareStatement(SQL_BUSCAR_POR_NOME)) {
            stmt.setString(1, nome);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? Optional.of(mapear(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RepositoryException("Falha ao buscar usuario por nome", e);
        }
    }

    @Override
    public List<Usuario> listar() {
        try (Connection conexao = ConexaoBanco.getConnection();
             PreparedStatement stmt = conexao.prepareStatement(SQL_LISTAR);
             ResultSet rs = stmt.executeQuery()) {
            List<Usuario> usuarios = new ArrayList<>();
            while (rs.next()) {
                usuarios.add(mapear(rs));
            }
            return usuarios;
        } catch (SQLException e) {
            throw new RepositoryException("Falha ao listar usuarios", e);
        }
    }

    @Override
    public Usuario inserir(Usuario usuario) {
        try (Connection conexao = ConexaoBanco.getConnection();
             PreparedStatement stmt = conexao.prepareStatement(SQL_INSERIR, Statement.RETURN_GENERATED_KEYS)) {
            preencherInsercao(stmt, usuario);
            stmt.executeUpdate();
            try (ResultSet chaves = stmt.getGeneratedKeys()) {
                if (!chaves.next()) {
                    throw new RepositoryException("Banco nao retornou o id do usuario inserido", null);
                }
                usuario.setId(chaves.getInt(1));
            }
            return usuario;
        } catch (SQLException e) {
            throw new RepositoryException("Falha ao inserir usuario", e);
        }
    }

    @Override
    public Usuario atualizar(Usuario usuario) {
        try (Connection conexao = ConexaoBanco.getConnection();
             PreparedStatement stmt = conexao.prepareStatement(SQL_ATUALIZAR)) {
            stmt.setString(1, usuario.getNome());
            stmt.setString(2, usuario.getLogin());
            stmt.setString(3, usuario.getRole().valor());
            stmt.setBoolean(4, usuario.isAtivo());
            stmt.setInt(5, usuario.getId());
            verificarAtualizacao(stmt.executeUpdate(), "usuario");
            return usuario;
        } catch (SQLException e) {
            throw new RepositoryException("Falha ao atualizar usuario", e);
        }
    }

    @Override
    public void desativar(int id) {
        executarAtualizacao(SQL_DESATIVAR, "Falha ao desativar usuario", id);
    }

    @Override
    public void atualizarSenha(int id, String senhaHash) {
        try (Connection conexao = ConexaoBanco.getConnection();
             PreparedStatement stmt = conexao.prepareStatement(SQL_ATUALIZAR_SENHA)) {
            stmt.setString(1, senhaHash);
            stmt.setInt(2, id);
            verificarAtualizacao(stmt.executeUpdate(), "usuario");
        } catch (SQLException e) {
            throw new RepositoryException("Falha ao atualizar senha do usuario", e);
        }
    }

    @Override
    public void registrarUltimoLogin(int id, LocalDateTime ultimoLogin) {
        try (Connection conexao = ConexaoBanco.getConnection();
             PreparedStatement stmt = conexao.prepareStatement(SQL_REGISTRAR_LOGIN)) {
            if (ultimoLogin == null) {
                stmt.setNull(1, Types.TIMESTAMP);
            } else {
                stmt.setObject(1, ultimoLogin);
            }
            stmt.setInt(2, id);
            verificarAtualizacao(stmt.executeUpdate(), "usuario");
        } catch (SQLException e) {
            throw new RepositoryException("Falha ao registrar ultimo login do usuario", e);
        }
    }

    private void preencherInsercao(PreparedStatement stmt, Usuario usuario) throws SQLException {
        stmt.setString(1, usuario.getNome());
        stmt.setString(2, usuario.getLogin());
        stmt.setString(3, usuario.getSenhaHash());
        stmt.setString(4, usuario.getRole().valor());
        stmt.setBoolean(5, usuario.isAtivo());
        if (usuario.getUltimoLogin() == null) {
            stmt.setNull(6, Types.TIMESTAMP);
        } else {
            stmt.setObject(6, usuario.getUltimoLogin());
        }
    }

    private void executarAtualizacao(String sql, String mensagem, int id) {
        try (Connection conexao = ConexaoBanco.getConnection();
             PreparedStatement stmt = conexao.prepareStatement(sql)) {
            stmt.setInt(1, id);
            verificarAtualizacao(stmt.executeUpdate(), "usuario");
        } catch (SQLException e) {
            throw new RepositoryException(mensagem, e);
        }
    }

    private void verificarAtualizacao(int linhas, String entidade) {
        if (linhas == 0) {
            throw new RepositoryException("Nenhum " + entidade + " encontrado para atualizar", null);
        }
    }

    private Usuario mapear(ResultSet rs) throws SQLException {
        Usuario usuario = new Usuario();
        usuario.setId(rs.getInt("id"));
        usuario.setNome(rs.getString("nome"));
        usuario.setLogin(rs.getString("login"));
        usuario.setSenhaHash(rs.getString("senha_hash"));
        usuario.setRole(RoleUsuario.deValor(rs.getString("role")));
        usuario.setAtivo(rs.getBoolean("ativo"));
        usuario.setUltimoLogin(rs.getObject("ultimo_login", LocalDateTime.class));
        usuario.setCriadoEm(rs.getObject("criado_em", LocalDateTime.class));
        return usuario;
    }
}