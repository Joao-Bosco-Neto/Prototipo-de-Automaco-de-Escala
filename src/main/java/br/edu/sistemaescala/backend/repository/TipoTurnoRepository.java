package br.edu.sistemaescala.backend.repository;

import java.util.List;
import java.util.Optional;

import br.edu.sistemaescala.backend.model.TipoTurno;

/**
 * Repositorio de tipos de turno (issue #11).
 *
 * TipoTurno nunca e excluido do banco, so desativado (coluna ativo): turnos
 * ja gerados em escala_turno referenciam o tipo por id sem cascata, entao
 * apagar a linha quebraria o historico de turnos ja criados com ele.
 */
public interface TipoTurnoRepository {

    /**
     * Lista tipos de turno.
     *
     * @param ativo true = so ativos, false = so inativos, null = todos
     */
    List<TipoTurno> listar(Boolean ativo);

    Optional<TipoTurno> buscarPorId(int id);

    /** Insere um tipo de turno e retorna a mesma instancia com o id gerado preenchido. */
    TipoTurno inserir(TipoTurno tipoTurno);

    TipoTurno atualizar(TipoTurno tipoTurno);

    void ativar(int id);

    /** Preserva o historico: so marca ativo = false, nunca remove a linha. */
    void desativar(int id);
}
