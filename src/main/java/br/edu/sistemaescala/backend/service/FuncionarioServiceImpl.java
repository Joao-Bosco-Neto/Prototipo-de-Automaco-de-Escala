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
}

