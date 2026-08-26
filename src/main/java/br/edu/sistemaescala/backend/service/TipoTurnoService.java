package br.edu.sistemaescala.backend.service;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import br.edu.sistemaescala.backend.model.TipoTurno;

/**
 * Interface de regras de negócio para gestão e parametrização de tipos de turno (Issue #39 / Backlog #24).
 */
public interface TipoTurnoService {

    /**
     * Lista tipos de turno.
     *
     * @param ativo true = apenas ativos, false = apenas inativos, null = todos
     */
    List<TipoTurno> listar(Boolean ativo);

    Optional<TipoTurno> buscarPorId(int id);

    TipoTurno cadastrar(String nome, LocalTime horaInicio, BigDecimal duracaoHoras,
                         BigDecimal intervaloDescansoHoras, int minAgentes, Integer maxAgentes,
                         boolean contaBancoHoras, boolean ativo);

    TipoTurno atualizar(int id, String nome, LocalTime horaInicio, BigDecimal duracaoHoras,
                         BigDecimal intervaloDescansoHoras, int minAgentes, Integer maxAgentes,
                         boolean contaBancoHoras, boolean ativo);

    void ativar(int id);

    void desativar(int id);

    /**
     * Valida a coerência do regime e calcula se a escala é viável para o número de funcionários ativos disponíveis.
     */
    ResultadoViabilidadeTurno verificarViabilidade(BigDecimal duracaoHoras, BigDecimal intervaloDescansoHoras, int minAgentes);
}

