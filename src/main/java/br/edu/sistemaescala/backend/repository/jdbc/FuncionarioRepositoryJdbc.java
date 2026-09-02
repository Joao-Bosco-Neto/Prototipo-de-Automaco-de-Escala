package br.edu.sistemaescala.backend.repository.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import br.edu.sistemaescala.backend.dao.ConexaoBanco;
import br.edu.sistemaescala.backend.model.Funcionario;
import br.edu.sistemaescala.backend.repository.FuncionarioRepository;
import br.edu.sistemaescala.backend.repository.RepositoryException;
import br.edu.sistemaescala.backend.service.ContagemFuncionarios;

public class FuncionarioRepositoryJdbc implements FuncionarioRepository {

    private static final String SQL_CAMPOS = """
            SELECT id, nome, matricula, telefone, observacoes, ativo, criado_em
            FROM funcionario
            """;

    private static final String SQL_BUSCAR_POR_ID = SQL_CAMPOS + " WHERE id = ?";

    private static final String SQL_INSERIR = """
            INSERT INTO funcionario (nome, matricula, telefone, observacoes, ativo)
            VALUES (?, ?, ?, ?, ?)
            """;

    private static final String SQL_ATUALIZAR = """
            UPDATE funcionario
            SET nome = ?, matricula = ?, telefone = ?, observacoes = ?, ativo = ?
            WHERE id = ?
            """;

    private static final String SQL_ATIVAR = "UPDATE funcionario SET ativo = TRUE WHERE id = ?";

    private static final String SQL_DESATIVAR = "UPDATE funcionario SET ativo = FALSE WHERE id = ?";

    private static final String SQL_EXISTE_MATRICULA = "SELECT COUNT(*) FROM funcionario WHERE matricula = ?";

    private static final String SQL_EXISTE_MATRICULA_EXCETO_ID = SQL_EXISTE_MATRICULA + " AND id <> ?";

    /**
     * Ativos e inativos numa varredura so da tabela. COUNT(CASE ...) em vez de
     * duas consultas: o card do dashboard mostra os dois numeros juntos.
     */
    private static final String SQL_CONTAR_POR_STATUS = """
            SELECT COUNT(CASE WHEN ativo THEN 1 END)     AS ativos,
                   COUNT(CASE WHEN NOT ativo THEN 1 END) AS inativos
            FROM funcionario
            """;

    /**
     * Inativos que ainda tem plantao pela frente. DISTINCT porque a mesma
     * pessoa pode estar em varios turnos futuros e o alerta cita cada nome
     * uma vez so.
     */
    private static final String SQL_INATIVOS_ESCALADOS_APOS = """
            SELECT DISTINCT f.id, f.nome, f.matricula, f.telefone, f.observacoes,
                            f.ativo, f.criado_em
            FROM funcionario f
            JOIN escala_funcionario ef ON ef.funcionario_id = f.id
            JOIN escala_turno et ON et.id = ef.escala_turno_id
            WHERE f.ativo = FALSE
              AND et.inicio >= ?
            ORDER BY f.nome, f.id
            """;

    private static final String SQL_CONTAR_PLANTOES_MES = """
            SELECT COUNT(*)
            FROM escala_funcionario ef
            JOIN escala_turno et ON et.id = ef.escala_turno_id
            WHERE ef.funcionario_id = ?
              AND et.inicio >= ?
              AND et.inicio <  ?
            """;

    @Override
    public List<Funcionario> listar(Boolean ativo, String textoBusca) {
        StringBuilder sql = new StringBuilder(SQL_CAMPOS);
        List<Object> parametros = new ArrayList<>();
        List<String> condicoes = new ArrayList<>();

        if (ativo != null) {
            condicoes.add("ativo = ?");
            parametros.add(ativo);
        }
        String termo = textoBusca == null ? null : textoBusca.trim();
        if (termo != null && !termo.isEmpty()) {
            condicoes.add("(LOWER(nome) LIKE ? OR LOWER(matricula) LIKE ?)");
            String curinga = "%" + termo.toLowerCase() + "%";
            parametros.add(curinga);
            parametros.add(curinga);
        }
        if (!condicoes.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", condicoes));
        }
        sql.append(" ORDER BY nome, id");

        try (Connection conexao = ConexaoBanco.getConnection();
             PreparedStatement stmt = conexao.prepareStatement(sql.toString())) {

            for (int i = 0; i < parametros.size(); i++) {
                stmt.setObject(i + 1, parametros.get(i));
            }

            List<Funcionario> funcionarios = new ArrayList<>();
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    funcionarios.add(mapear(rs));
                }
            }
            return funcionarios;

        } catch (SQLException e) {
            throw new RepositoryException("Falha ao listar funcionarios", e);
        }
    }

    @Override
    public Optional<Funcionario> buscarPorId(int id) {
        try (Connection conexao = ConexaoBanco.getConnection();
             PreparedStatement stmt = conexao.prepareStatement(SQL_BUSCAR_POR_ID)) {

            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? Optional.of(mapear(rs)) : Optional.empty();
            }

        } catch (SQLException e) {
            throw new RepositoryException("Falha ao buscar funcionario " + id, e);
        }
    }

    @Override
    public Funcionario inserir(Funcionario funcionario) {
        try (Connection conexao = ConexaoBanco.getConnection();
             PreparedStatement stmt = conexao.prepareStatement(SQL_INSERIR, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setString(1, funcionario.getNome());
            stmt.setString(2, funcionario.getMatricula());
            stmt.setString(3, funcionario.getTelefone());
            stmt.setString(4, funcionario.getObservacoes());
            stmt.setBoolean(5, funcionario.isAtivo());
            stmt.executeUpdate();

            try (ResultSet chaves = stmt.getGeneratedKeys()) {
                if (!chaves.next()) {
                    throw new RepositoryException("Banco nao retornou o id do funcionario inserido", null);
                }
                funcionario.setId(chaves.getInt(1));
            }
            return funcionario;

        } catch (SQLException e) {
            throw new RepositoryException("Falha ao inserir funcionario", e);
        }
    }

    @Override
    public Funcionario atualizar(Funcionario funcionario) {
        try (Connection conexao = ConexaoBanco.getConnection();
             PreparedStatement stmt = conexao.prepareStatement(SQL_ATUALIZAR)) {

            stmt.setString(1, funcionario.getNome());
            stmt.setString(2, funcionario.getMatricula());
            stmt.setString(3, funcionario.getTelefone());
            stmt.setString(4, funcionario.getObservacoes());
            stmt.setBoolean(5, funcionario.isAtivo());
            stmt.setInt(6, funcionario.getId());
            verificarAtualizacao(stmt.executeUpdate());
            return funcionario;

        } catch (SQLException e) {
            throw new RepositoryException("Falha ao atualizar funcionario", e);
        }
    }

    @Override
    public void ativar(int id) {
        executarAtualizacao(SQL_ATIVAR, "Falha ao ativar funcionario", id);
    }

    @Override
    public void desativar(int id) {
        executarAtualizacao(SQL_DESATIVAR, "Falha ao desativar funcionario", id);
    }

    @Override
    public boolean existeMatricula(String matricula, Integer idParaExcluir) {
        String sql = idParaExcluir == null ? SQL_EXISTE_MATRICULA : SQL_EXISTE_MATRICULA_EXCETO_ID;
        try (Connection conexao = ConexaoBanco.getConnection();
             PreparedStatement stmt = conexao.prepareStatement(sql)) {

            stmt.setString(1, matricula);
            if (idParaExcluir != null) {
                stmt.setInt(2, idParaExcluir);
            }
            try (ResultSet rs = stmt.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }

        } catch (SQLException e) {
            throw new RepositoryException("Falha ao verificar matricula duplicada", e);
        }
    }

    @Override
    public int contarPlantoesNoMes(int funcionarioId, YearMonth mes) {
        LocalDate inicioMes = mes.atDay(1);
        LocalDate inicioProximoMes = mes.plusMonths(1).atDay(1);

        try (Connection conexao = ConexaoBanco.getConnection();
             PreparedStatement stmt = conexao.prepareStatement(SQL_CONTAR_PLANTOES_MES)) {

            stmt.setInt(1, funcionarioId);
            stmt.setObject(2, inicioMes.atStartOfDay());
            stmt.setObject(3, inicioProximoMes.atStartOfDay());
            try (ResultSet rs = stmt.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }

        } catch (SQLException e) {
            throw new RepositoryException(
                    "Falha ao contar plantoes do mes do funcionario " + funcionarioId, e);
        }
    }

    @Override
    public ContagemFuncionarios contarPorStatus() {
        try (Connection conexao = ConexaoBanco.getConnection();
             PreparedStatement stmt = conexao.prepareStatement(SQL_CONTAR_POR_STATUS);
             ResultSet rs = stmt.executeQuery()) {

            rs.next();
            return new ContagemFuncionarios(rs.getInt("ativos"), rs.getInt("inativos"));

        } catch (SQLException e) {
            throw new RepositoryException("Falha ao contar funcionarios por status", e);
        }
    }

    @Override
    public List<Funcionario> listarInativosEscaladosApos(LocalDateTime instante) {
        try (Connection conexao = ConexaoBanco.getConnection();
             PreparedStatement stmt = conexao.prepareStatement(SQL_INATIVOS_ESCALADOS_APOS)) {

            stmt.setObject(1, instante);
            List<Funcionario> inativos = new ArrayList<>();
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    inativos.add(mapear(rs));
                }
            }
            return inativos;

        } catch (SQLException e) {
            throw new RepositoryException(
                    "Falha ao listar inativos ainda escalados a partir de " + instante, e);
        }
    }

    private void executarAtualizacao(String sql, String mensagem, int id) {
        try (Connection conexao = ConexaoBanco.getConnection();
             PreparedStatement stmt = conexao.prepareStatement(sql)) {
            stmt.setInt(1, id);
            verificarAtualizacao(stmt.executeUpdate());
        } catch (SQLException e) {
            throw new RepositoryException(mensagem, e);
        }
    }

    private void verificarAtualizacao(int linhas) {
        if (linhas == 0) {
            throw new RepositoryException("Nenhum funcionario encontrado para atualizar", null);
        }
    }

    private Funcionario mapear(ResultSet rs) throws SQLException {
        Funcionario funcionario = new Funcionario();
        funcionario.setId(rs.getInt("id"));
        funcionario.setNome(rs.getString("nome"));
        funcionario.setMatricula(rs.getString("matricula"));
        funcionario.setTelefone(rs.getString("telefone"));
        funcionario.setObservacoes(rs.getString("observacoes"));
        funcionario.setAtivo(rs.getBoolean("ativo"));
        funcionario.setCriadoEm(rs.getObject("criado_em", LocalDateTime.class));
        return funcionario;
    }
}
