package br.edu.sistemaescala.backend.service;

import java.time.YearMonth;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import br.edu.sistemaescala.backend.model.Funcionario;
import br.edu.sistemaescala.backend.repository.FuncionarioRepository;

public class FuncionarioServiceImpl implements FuncionarioService {

    private final FuncionarioRepository funcionarioRepository;

    public FuncionarioServiceImpl(FuncionarioRepository funcionarioRepository) {
        this.funcionarioRepository = Objects.requireNonNull(funcionarioRepository, "funcionarioRepository não pode ser nulo");
    }

    @Override
    public List<FuncionarioListagemItem> listarComPlantoes(Boolean statusAtivo, String termoBusca, YearMonth mesReferencia) {
        YearMonth mes = mesReferencia != null ? mesReferencia : YearMonth.now();
        List<Funcionario> funcionarios = funcionarioRepository.listar(statusAtivo, termoBusca);
        return funcionarios.stream()
                .map(funcionario -> {
                    int plantoes = funcionario.getId() != null
                            ? funcionarioRepository.contarPlantoesNoMes(funcionario.getId(), mes)
                            : 0;
                    return new FuncionarioListagemItem(funcionario, plantoes);
                })
                .toList();
    }

    @Override
    public List<Funcionario> listar(Boolean statusAtivo, String termoBusca) {
        return funcionarioRepository.listar(statusAtivo, termoBusca);
    }

    @Override
    public Optional<Funcionario> buscarPorId(int id) {
        return funcionarioRepository.buscarPorId(id);
    }

    @Override
    public Funcionario cadastrar(String nome, String matricula, String telefone, String observacoes, boolean ativo) {
        validarCamposObrigatorios(nome, matricula);

        String nomeNormalizado = nome.trim();
        String matriculaNormalizada = matricula.trim();
        String telefoneNormalizado = (telefone != null && !telefone.isBlank()) ? telefone.trim() : null;
        String observacoesNormalizadas = (observacoes != null && !observacoes.isBlank()) ? observacoes.trim() : null;

        validarUnicidadeMatricula(matriculaNormalizada, null);

        Funcionario novo = new Funcionario();
        novo.setNome(nomeNormalizado);
        novo.setMatricula(matriculaNormalizada);
        novo.setTelefone(telefoneNormalizado);
        novo.setObservacoes(observacoesNormalizadas);
        novo.setAtivo(ativo);

        return funcionarioRepository.inserir(novo);
    }

    @Override
    public Funcionario atualizar(int id, String nome, String matricula, String telefone, String observacoes, boolean ativo) {
        Funcionario existente = funcionarioRepository.buscarPorId(id)
                .orElseThrow(() -> new RegraFuncionarioException("Funcionário não encontrado."));

        validarCamposObrigatorios(nome, matricula);

        String nomeNormalizado = nome.trim();
        String matriculaNormalizada = matricula.trim();
        String telefoneNormalizado = (telefone != null && !telefone.isBlank()) ? telefone.trim() : null;
        String observacoesNormalizadas = (observacoes != null && !observacoes.isBlank()) ? observacoes.trim() : null;

        validarUnicidadeMatricula(matriculaNormalizada, id);

        existente.setNome(nomeNormalizado);
        existente.setMatricula(matriculaNormalizada);
        existente.setTelefone(telefoneNormalizado);
        existente.setObservacoes(observacoesNormalizadas);
        existente.setAtivo(ativo);

        return funcionarioRepository.atualizar(existente);
    }

    @Override
    public void ativar(int id) {
        funcionarioRepository.ativar(id);
    }

    @Override
    public void desativar(int id) {
        funcionarioRepository.desativar(id);
    }

    @Override
    public int contarPlantoesNoMes(int funcionarioId, YearMonth mesReferencia) {
        YearMonth mes = mesReferencia != null ? mesReferencia : YearMonth.now();
        return funcionarioRepository.contarPlantoesNoMes(funcionarioId, mes);
    }

    private void validarCamposObrigatorios(String nome, String matricula) {
        if (nome == null || nome.isBlank()) {
            throw new RegraFuncionarioException("O nome do funcionário é obrigatório.");
        }
        if (matricula == null || matricula.isBlank()) {
            throw new RegraFuncionarioException("A matrícula do funcionário é obrigatória.");
        }
    }

    private void validarUnicidadeMatricula(String matricula, Integer idParaExcluir) {
        if (funcionarioRepository.existeMatricula(matricula, idParaExcluir)) {
            throw new RegraFuncionarioException("Já existe um funcionário cadastrado com a matrícula '" + matricula + "'.");
        }
    }
}
