package br.edu.sistemaescala.backend.service;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import br.edu.sistemaescala.backend.model.TipoTurno;
import br.edu.sistemaescala.backend.repository.FuncionarioRepository;
import br.edu.sistemaescala.backend.repository.TipoTurnoRepository;
import br.edu.sistemaescala.backend.repository.jdbc.FuncionarioRepositoryJdbc;
import br.edu.sistemaescala.backend.repository.jdbc.TipoTurnoRepositoryJdbc;

public class TipoTurnoServiceImpl implements TipoTurnoService {

    private final TipoTurnoRepository tipoTurnoRepository;
    private final FuncionarioRepository funcionarioRepository;

    public TipoTurnoServiceImpl() {
        this(new TipoTurnoRepositoryJdbc(), new FuncionarioRepositoryJdbc());
    }

    public TipoTurnoServiceImpl(TipoTurnoRepository tipoTurnoRepository, FuncionarioRepository funcionarioRepository) {
        this.tipoTurnoRepository = Objects.requireNonNull(tipoTurnoRepository, "tipoTurnoRepository não pode ser nulo");
        this.funcionarioRepository = Objects.requireNonNull(funcionarioRepository, "funcionarioRepository não pode ser nulo");
    }

    @Override
    public List<TipoTurno> listar(Boolean ativo) {
        return tipoTurnoRepository.listar(ativo);
    }

    @Override
    public Optional<TipoTurno> buscarPorId(int id) {
        return tipoTurnoRepository.buscarPorId(id);
    }

    @Override
    public TipoTurno cadastrar(String nome, LocalTime horaInicio, BigDecimal duracaoHoras,
                               BigDecimal intervaloDescansoHoras, int minAgentes, Integer maxAgentes,
                               boolean contaBancoHoras, boolean ativo) {
        validarCampos(nome, horaInicio, duracaoHoras, intervaloDescansoHoras, minAgentes, maxAgentes);

        TipoTurno novo = new TipoTurno();
        novo.setNome(nome.trim());
        novo.setHoraInicio(horaInicio);
        novo.setDuracaoHoras(duracaoHoras);
        novo.setIntervaloDescansoHoras(intervaloDescansoHoras != null ? intervaloDescansoHoras : BigDecimal.ZERO);
        novo.setMinAgentes(minAgentes);
        novo.setMaxAgentes(maxAgentes);
        novo.setContaBancoHoras(contaBancoHoras);
        novo.setAtivo(ativo);

        return tipoTurnoRepository.inserir(novo);
    }

    @Override
    public TipoTurno atualizar(int id, String nome, LocalTime horaInicio, BigDecimal duracaoHoras,
                               BigDecimal intervaloDescansoHoras, int minAgentes, Integer maxAgentes,
                               boolean contaBancoHoras, boolean ativo) {
        TipoTurno existente = tipoTurnoRepository.buscarPorId(id)
                .orElseThrow(() -> new RegraTipoTurnoException("Tipo de turno não encontrado."));

        validarCampos(nome, horaInicio, duracaoHoras, intervaloDescansoHoras, minAgentes, maxAgentes);

        existente.setNome(nome.trim());
        existente.setHoraInicio(horaInicio);
        existente.setDuracaoHoras(duracaoHoras);
        existente.setIntervaloDescansoHoras(intervaloDescansoHoras != null ? intervaloDescansoHoras : BigDecimal.ZERO);
        existente.setMinAgentes(minAgentes);
        existente.setMaxAgentes(maxAgentes);
        existente.setContaBancoHoras(contaBancoHoras);
        existente.setAtivo(ativo);

        return tipoTurnoRepository.atualizar(existente);
    }

    @Override
    public void ativar(int id) {
        tipoTurnoRepository.ativar(id);
    }

    @Override
    public void desativar(int id) {
        tipoTurnoRepository.desativar(id);
    }

    @Override
    public ResultadoViabilidadeTurno verificarViabilidade(BigDecimal duracaoHoras, BigDecimal intervaloDescansoHoras, int minAgentes) {
        int efetivoAtivo = funcionarioRepository.listar(true, null).size();

        if (duracaoHoras == null || duracaoHoras.compareTo(BigDecimal.ZERO) <= 0 || minAgentes < 1) {
            return new ResultadoViabilidadeTurno(true, 0, efetivoAtivo, "Preencha a duração e o mínimo de agentes para análise.");
        }

        BigDecimal descanso = intervaloDescansoHoras != null ? intervaloDescansoHoras : BigDecimal.ZERO;
        BigDecimal cicloTotal = duracaoHoras.add(descanso);
        double turnosPorCiclo = cicloTotal.doubleValue() / duracaoHoras.doubleValue();
        int equipesNecessarias = (int) Math.ceil(turnosPorCiclo);
        int efetivoNecessario = equipesNecessarias * minAgentes;

        if (efetivoAtivo < efetivoNecessario) {
            String mensagem = String.format(
                    "Atenção: Para cumprir este regime (ciclo de %.0fh com %d agente(s)/turno), são necessários no mínimo %d funcionários ativos. Atualmente existem %d ativos.",
                    cicloTotal.doubleValue(), minAgentes, efetivoNecessario, efetivoAtivo);
            return new ResultadoViabilidadeTurno(false, efetivoNecessario, efetivoAtivo, mensagem);
        } else {
            String mensagem = String.format(
                    "Regime viável para o efetivo disponível (%d ativos para %d necessários).",
                    efetivoAtivo, efetivoNecessario);
            return new ResultadoViabilidadeTurno(true, efetivoNecessario, efetivoAtivo, mensagem);
        }
    }

    private void validarCampos(String nome, LocalTime horaInicio, BigDecimal duracaoHoras,
                               BigDecimal intervaloDescansoHoras, int minAgentes, Integer maxAgentes) {
        if (nome == null || nome.isBlank()) {
            throw new RegraTipoTurnoException("O nome do tipo de turno é obrigatório.");
        }
        if (horaInicio == null) {
            throw new RegraTipoTurnoException("A hora de início é obrigatória.");
        }
        if (duracaoHoras == null || duracaoHoras.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RegraTipoTurnoException("A duração em horas deve ser maior que zero.");
        }
        if (intervaloDescansoHoras == null || intervaloDescansoHoras.compareTo(BigDecimal.ZERO) < 0) {
            throw new RegraTipoTurnoException("O intervalo de descanso não pode ser negativo.");
        }
        if (minAgentes < 1) {
            throw new RegraTipoTurnoException("O número mínimo de agentes deve ser de pelo menos 1.");
        }
        if (maxAgentes != null && maxAgentes < minAgentes) {
            throw new RegraTipoTurnoException("O número máximo de agentes não pode ser menor que o mínimo.");
        }
    }
}

