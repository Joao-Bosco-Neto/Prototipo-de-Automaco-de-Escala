package br.edu.sistemaescala.backend.service;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import br.edu.sistemaescala.backend.dao.TransacaoUtil;
import br.edu.sistemaescala.backend.model.EscalaTurno;
import br.edu.sistemaescala.backend.repository.EscalaTurnoRepository;
import br.edu.sistemaescala.backend.repository.jdbc.EscalaTurnoRepositoryJdbc;

/**
 * Implementação da limpeza da escala de um mês (Issue #44).
 *
 * <p>O trabalho pesado é do banco: um único DELETE em {@code escala_turno}
 * arrasta as alocações e os lançamentos de banco de horas pelas cascatas
 * declaradas no schema. Aqui ficam a contagem que alimenta a confirmação da
 * tela, a transação e a mensagem pronta.</p>
 *
 * <p>A remoção roda dentro de {@link TransacaoUtil}, pela sobrecarga
 * {@link EscalaTurnoRepository#removerPorMes(YearMonth, java.sql.Connection)}:
 * o DELETE é um comando só, mas a cascata pode tocar várias tabelas, e uma
 * falha no meio não pode deixar o mês pela metade.</p>
 */
public class LimpezaEscalaServiceImpl implements LimpezaEscalaService {

    private static final Locale LOCALE_BR = Locale.forLanguageTag("pt-BR");
    private static final DateTimeFormatter FORMATO_MES_ANO =
            DateTimeFormatter.ofPattern("MMMM 'de' yyyy", LOCALE_BR);

    private final EscalaTurnoRepository escalaTurnoRepository;

    public LimpezaEscalaServiceImpl() {
        this(new EscalaTurnoRepositoryJdbc());
    }

    public LimpezaEscalaServiceImpl(EscalaTurnoRepository escalaTurnoRepository) {
        this.escalaTurnoRepository = Objects.requireNonNull(escalaTurnoRepository,
                "escalaTurnoRepository não pode ser nulo");
    }

    @Override
    public ResumoEscalaMes resumir(YearMonth mes) {
        Objects.requireNonNull(mes, "mes não pode ser nulo");
        return resumir(turnosDoMes(mes));
    }

    @Override
    public ResultadoLimpeza limparMes(YearMonth mes) {
        Objects.requireNonNull(mes, "mes não pode ser nulo");

        // Conta antes de apagar: depois do DELETE não há mais o que contar, e
        // é este número que vai para a mensagem final.
        ResumoEscalaMes resumo = resumir(turnosDoMes(mes));
        if (resumo.vazio()) {
            return new ResultadoLimpeza(false, 0, 0,
                    descreverMes(mes) + " já está sem turnos montados: não há o que limpar.");
        }

        TransacaoUtil.executar(conexao -> {
            escalaTurnoRepository.removerPorMes(mes, conexao);
            return null;
        });

        String mensagem = String.format("Escala de %s limpa: %d turno(s) e %d alocação(ões) removidos.",
                descreverMes(mes), resumo.turnos(), resumo.alocacoes());
        return new ResultadoLimpeza(true, resumo.turnos(), resumo.alocacoes(), mensagem);
    }

    /** Uma única consulta traz o mês com os agentes já hidratados — nunca uma por dia. */
    private List<EscalaTurno> turnosDoMes(YearMonth mes) {
        return escalaTurnoRepository.buscarPorPeriodo(inicioDe(mes), inicioDe(mes.plusMonths(1)));
    }

    private ResumoEscalaMes resumir(List<EscalaTurno> turnos) {
        int alocacoes = 0;
        for (EscalaTurno turno : turnos) {
            if (turno.getAgentes() != null) {
                alocacoes += turno.getAgentes().size();
            }
        }
        return new ResumoEscalaMes(turnos.size(), alocacoes);
    }

    private LocalDateTime inicioDe(YearMonth mes) {
        return mes.atDay(1).atStartOfDay();
    }

    private String descreverMes(YearMonth mes) {
        String texto = mes.atDay(1).format(FORMATO_MES_ANO);
        return texto.substring(0, 1).toUpperCase(LOCALE_BR) + texto.substring(1);
    }
}
