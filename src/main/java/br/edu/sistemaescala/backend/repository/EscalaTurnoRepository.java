package br.edu.sistemaescala.backend.repository;

import java.sql.Connection;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import br.edu.sistemaescala.backend.model.EscalaTurno;
import br.edu.sistemaescala.backend.service.PlantaoDoDiaItem;

/**
 * Repositorio de turnos concretos no calendario (issue #11).
 *
 * {@link #buscarPorPeriodo} e a consulta mais importante do sistema: alimenta
 * o calendario, o PDF e o dashboard. Ela traz cada {@link EscalaTurno} com o
 * {@code tipoTurno} e os {@code agentes} (com suas coberturas) ja hidratados
 * a partir de um unico JOIN, para nao cair em N+1 consultas.
 */
public interface EscalaTurnoRepository {

    /** Turnos com inicio no intervalo [inicio, fim), com tipoTurno e agentes/coberturas ja carregados. */
    List<EscalaTurno> buscarPorPeriodo(LocalDateTime inicio, LocalDateTime fim);

    Optional<EscalaTurno> buscarPorId(int id);

    /** Insere (id nulo) ou atualiza (id preenchido) o turno e retorna a mesma instancia. */
    EscalaTurno salvar(EscalaTurno turno);

    /**
     * Mesmo salvamento, porem numa Connection recebida de fora — e assim que
     * o turno participa de uma transacao aberta pelo TransacaoUtil, junto com
     * as alocacoes que o gerador de rodizio cria para ele (issue #43).
     *
     * A conexao continua sendo de quem chamou: este metodo nao faz commit,
     * rollback nem close.
     */
    EscalaTurno salvar(EscalaTurno turno, Connection conexao);

    /** Remove todos os turnos com inicio no mes informado ("Limpar mes"), em cascata sobre as coberturas. */
    void removerPorMes(YearMonth mes);

    /**
     * Mesma remocao, porem numa Connection recebida de fora. Sobrescrever um
     * mes ja gerado precisa apagar o antigo e gravar o novo na mesma
     * transacao, senao uma falha no meio deixaria o mes vazio.
     */
    void removerPorMes(YearMonth mes, Connection conexao);

    /**
     * Turnos que comecam no dia informado, ja resumidos para o card "Plantao
     * de hoje" do dashboard (issue #53): tipo de turno, horario e quantos
     * postos estao ocupados.
     *
     * <p>Agrega no banco (GROUP BY sobre escala_funcionario) em vez de usar
     * {@link #buscarPorPeriodo} e contar em memoria: o card so precisa do
     * numero, nao dos agentes. Lista vazia quando nao ha plantao no dia — e a
     * partir dela que a tela monta o estado vazio.</p>
     *
     * <p>A contagem repete a regra do efetivo do calendario: uma cobertura
     * ocupa o posto do titular ausente, entao a alocacao coberta nao entra na
     * soma e o turno nao aparece com um agente a mais do que de fato tem.</p>
     *
     * <p>Um dia pode devolver mais de um item — em 12x36 o diurno e o noturno
     * comecam na mesma data. Ordenado por inicio.</p>
     */
    List<PlantaoDoDiaItem> resumirPlantoesDoDia(LocalDate dia);

    /**
     * Quantos dias do mes tem pelo menos um turno abaixo do minimo de agentes
     * — o card "Dias incompletos" do dashboard (issue #53).
     *
     * <p>Conta dias distintos, nao turnos: um dia com os dois turnos do 12x36
     * incompletos conta uma vez so, exatamente como o calendario o pinta de
     * ambar uma vez so.</p>
     *
     * <p>COUNT(DISTINCT) direto no banco, com a mesma regra de posto ocupado
     * de {@link #resumirPlantoesDoDia} — nada de carregar o mes inteiro para
     * contar em memoria.</p>
     */
    int contarDiasComEfetivoIncompleto(YearMonth mes);

    /**
     * Quantos turnos comecam no mes informado — o que responde "a escala deste
     * mes ja foi montada?" para o painel de pendencias (issue #55).
     *
     * <p>COUNT no banco em vez de {@code buscarPorPeriodo(...).size()}: a
     * pergunta e se existe algum turno, nao quais sao, e carregar o mes inteiro
     * com agentes hidratados para descobrir isso seria desperdicio.</p>
     */
    int contarTurnosNoMes(YearMonth mes);
}
