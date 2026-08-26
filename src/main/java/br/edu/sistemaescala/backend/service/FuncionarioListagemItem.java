package br.edu.sistemaescala.backend.service;

import java.util.Objects;

import br.edu.sistemaescala.backend.model.Funcionario;

/**
 * Item de exibição para a listagem de funcionários (Issue #20 / Backlog #21).
 * Agrupa os dados cadastrais do funcionário com a apuração de plantões no mês.
 */
public class FuncionarioListagemItem {

    private final Funcionario funcionario;
    private final int plantoesNoMes;

    public FuncionarioListagemItem(Funcionario funcionario, int plantoesNoMes) {
        this.funcionario = Objects.requireNonNull(funcionario, "Funcionario não pode ser nulo");
        this.plantoesNoMes = plantoesNoMes;
    }

    public Funcionario getFuncionario() {
        return funcionario;
    }

    public Integer getId() {
        return funcionario.getId();
    }

    public String getNome() {
        return funcionario.getNome();
    }

    public String getMatricula() {
        return funcionario.getMatricula();
    }

    public String getTelefone() {
        return funcionario.getTelefone();
    }

    public String getObservacoes() {
        return funcionario.getObservacoes();
    }

    public boolean isAtivo() {
        return funcionario.isAtivo();
    }

    public int getPlantoesNoMes() {
        return plantoesNoMes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FuncionarioListagemItem that = (FuncionarioListagemItem) o;
        return plantoesNoMes == that.plantoesNoMes && Objects.equals(funcionario, that.funcionario);
    }

    @Override
    public int hashCode() {
        return Objects.hash(funcionario, plantoesNoMes);
    }
}

