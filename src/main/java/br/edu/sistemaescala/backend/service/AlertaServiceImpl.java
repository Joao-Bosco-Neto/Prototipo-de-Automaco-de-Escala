package br.edu.sistemaescala.backend.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

import br.edu.sistemaescala.backend.model.Funcionario;
import br.edu.sistemaescala.backend.repository.EscalaFuncionarioRepository;
import br.edu.sistemaescala.backend.repository.EscalaTurnoRepository;
import br.edu.sistemaescala.backend.repository.FuncionarioRepository;
import br.edu.sistemaescala.backend.repository.jdbc.EscalaFuncionarioRepositoryJdbc;
import br.edu.sistemaescala.backend.repository.jdbc.EscalaTurnoRepositoryJdbc;
import br.edu.sistemaescala.backend.repository.jdbc.FuncionarioRepositoryJdbc;

/**
 * Implementação de {@link AlertaService}: uma verificação por tipo de alerta,
 * cada uma com sua consulta, e só entra na lista o que de fato encontrou
 * problema.
 *
 * <p>As quatro verificações são independentes — nenhuma depende do resultado da
 * outra —, então uma consulta que não acha nada apenas não gera alerta, e o
 * painel some com a linha em vez de mostrar "0 pendências deste tipo".</p>
 *
 * <p>As contagens saem agregadas do banco, na mesma regra das issues #53 e #54.
 * A exceção é o alerta de funcionário inativo, que traz os registros: ele cita
 * os nomes, e a lista é curta por construção (só quem está desativado <em>e</em>
 * ainda tem plantão marcado).</p>
 */
public class AlertaServiceImpl implements AlertaService {

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    /** Quantos nomes o alerta de inativo cita antes de resumir o resto. */
    private static final int MAXIMO_NOMES_CITADOS = 3;

    private final EscalaTurnoRepository escalaTurnoRepository;
    private final EscalaFuncionarioRepository escalaFuncionarioRepository;
    private final FuncionarioRepository funcionarioRepository;

    /** Construtor de conveniência com os repositórios JDBC padrão. */
    public AlertaServiceImpl() {
        this(new EscalaTurnoRepositoryJdbc(), new EscalaFuncionarioRepositoryJdbc(),
                new FuncionarioRepositoryJdbc());
    }

    public AlertaServiceImpl(EscalaTurnoRepository escalaTurnoRepository,
                             EscalaFuncionarioRepository escalaFuncionarioRepository,
                             FuncionarioRepository funcionarioRepository) {
        this.escalaTurnoRepository = Objects.requireNonNull(escalaTurnoRepository);
        this.escalaFuncionarioRepository = Objects.requireNonNull(escalaFuncionarioRepository);
        this.funcionarioRepository = Objects.requireNonNull(funcionarioRepository);
    }

    @Override
    public List<AlertaDashboard> levantar(LocalDate diaReferencia) {
        Objects.requireNonNull(diaReferencia, "diaReferencia não pode ser nulo");
        YearMonth mes = YearMonth.from(diaReferencia);

        List<AlertaDashboard> alertas = new ArrayList<>();
        adicionarSePresente(alertas, verificarInativosEscalados(diaReferencia));
        adicionarSePresente(alertas, verificarEfetivoIncompleto(mes));
        adicionarSePresente(alertas, verificarCoberturasSemLancamento(mes));
        adicionarSePresente(alertas, verificarProximoMesSemEscala(mes));

        // Ordena pela gravidade, e não pela ordem em que as verificações rodam:
        // o painel lista de cima para baixo e o crítico tem que abrir a lista.
        alertas.sort((um, outro) -> um.severidade().compareTo(outro.severidade()));
        return List.copyOf(alertas);
    }

    private void adicionarSePresente(List<AlertaDashboard> alertas, AlertaDashboard alerta) {
        if (alerta != null) {
            alertas.add(alerta);
        }
    }

    // -----------------------------------------------------------------
    // As quatro verificacoes
    // -----------------------------------------------------------------

    /**
     * Funcionário desativado que continua escalado num turno que ainda vai
     * acontecer.
     *
     * <p>Crítico porque é inconsistência de dado, não pendência de
     * planejamento: a escala está gravada contando com alguém que já saiu, e
     * ninguém vai aparecer no plantão. O corte é o início do dia de referência,
     * então o plantão de hoje ainda conta — desativar alguém não desfaz o
     * plantão que ele já estava cumprindo.</p>
     */
    private AlertaDashboard verificarInativosEscalados(LocalDate diaReferencia) {
        List<Funcionario> inativos =
                funcionarioRepository.listarInativosEscaladosApos(diaReferencia.atStartOfDay());
        if (inativos.isEmpty()) {
            return null;
        }

        boolean umSo = inativos.size() == 1;
        String titulo = umSo
                ? "1 funcionário desativado continua escalado"
                : inativos.size() + " funcionários desativados continuam escalados";

        // Concorda de verdade em vez de "está(ão) desativado(s)": o texto vai
        // para a tela do gestor, nao para um log.
        String descricao = umSo
                ? citarNomes(inativos) + " está desativado mas ainda aparece em turnos a partir "
                  + "de hoje. Substitua na montagem da escala antes de publicar o mês."
                : citarNomes(inativos) + " estão desativados mas ainda aparecem em turnos a partir "
                  + "de hoje. Substitua-os na montagem da escala antes de publicar o mês.";

        return new AlertaDashboard(TipoAlerta.INATIVO_ESCALADO, SeveridadeAlerta.CRITICO,
                titulo, descricao);
    }

    /**
     * Dias do mês corrente com pelo menos um turno abaixo do mínimo de agentes.
     *
     * <p>Atenção, e não crítico, pela mesma razão registrada em
     * {@link ResultadoEfetivo}: escala incompleta pode ser salva com aviso, o
     * que ela bloqueia é a exportação do PDF. É também o mesmo âmbar que o
     * calendário pinta na célula e que o card de "Dias incompletos" usa — os
     * três mostram o mesmo fato e não podem discordar na cor.</p>
     */
    private AlertaDashboard verificarEfetivoIncompleto(YearMonth mes) {
        int dias = escalaTurnoRepository.contarDiasComEfetivoIncompleto(mes);
        if (dias == 0) {
            return null;
        }

        String titulo = dias == 1
                ? "1 dia de " + nomeDoMes(mes) + " com efetivo incompleto"
                : dias + " dias de " + nomeDoMes(mes) + " com efetivo incompleto";

        return new AlertaDashboard(TipoAlerta.EFETIVO_INCOMPLETO, SeveridadeAlerta.ATENCAO, titulo,
                "Esses dias têm turno abaixo do mínimo de agentes e aparecem em âmbar no "
                + "calendário. A escala pode ser salva assim, mas a exportação em PDF fica "
                + "bloqueada até completar o efetivo.");
    }

    /**
     * Cobertura registrada no mês sem o par crédito/débito no banco de horas.
     *
     * <p>Atenção, e não crítico, porque lançar ou não é escolha do gestor no
     * registro da cobertura — o alerta existe para a escolha ser consciente, já
     * que uma cobertura sem lançamento deixa quem cobriu sem o crédito das
     * horas.</p>
     */
    private AlertaDashboard verificarCoberturasSemLancamento(YearMonth mes) {
        int coberturas = escalaFuncionarioRepository.contarCoberturasSemLancamentoNoMes(mes);
        if (coberturas == 0) {
            return null;
        }

        String titulo = coberturas == 1
                ? "1 cobertura sem lançamento no banco de horas"
                : coberturas + " coberturas sem lançamento no banco de horas";

        return new AlertaDashboard(TipoAlerta.COBERTURA_SEM_LANCAMENTO, SeveridadeAlerta.ATENCAO,
                titulo,
                "Quem cobriu não recebeu o crédito das horas e o ausente não teve o débito. "
                + "Confirme na tela de Coberturas se isso foi intencional.");
    }

    /**
     * O mês seguinte ainda não tem nenhum turno montado.
     *
     * <p>Informativo: nada está errado no que já existe, só há trabalho pela
     * frente. Vira alerta a partir de zero turnos — um mês parcialmente montado
     * já foi iniciado, e o que falta nele é assunto do alerta de efetivo
     * incompleto quando aquele mês virar o corrente.</p>
     */
    private AlertaDashboard verificarProximoMesSemEscala(YearMonth mes) {
        YearMonth proximo = mes.plusMonths(1);
        if (escalaTurnoRepository.contarTurnosNoMes(proximo) > 0) {
            return null;
        }

        return new AlertaDashboard(TipoAlerta.PROXIMO_MES_SEM_ESCALA, SeveridadeAlerta.INFORMATIVO,
                "Escala de " + nomeDoMes(proximo) + " ainda não iniciada",
                "Nenhum turno foi montado para " + nomeDoMes(proximo) + " de " + proximo.getYear()
                + ". Gere o rodízio em \"Montagem da escala\" com antecedência para dar tempo "
                + "de ajustar o efetivo.");
    }

    // -----------------------------------------------------------------
    // Apoio
    // -----------------------------------------------------------------

    /**
     * Até {@link #MAXIMO_NOMES_CITADOS} nomes por extenso; o excedente vira
     * "e mais N". Sem o corte, uma limpeza de cadastro com dez desativados
     * geraria um parágrafo de nomes dentro do painel.
     */
    private String citarNomes(List<Funcionario> inativos) {
        List<String> nomes = inativos.stream()
                .map(funcionario -> funcionario.getNome() != null
                        ? funcionario.getNome()
                        : "(sem nome)")
                .toList();

        if (nomes.size() <= MAXIMO_NOMES_CITADOS) {
            return String.join(", ", nomes);
        }
        int restantes = nomes.size() - MAXIMO_NOMES_CITADOS;
        return nomes.stream().limit(MAXIMO_NOMES_CITADOS).collect(Collectors.joining(", "))
                + " e mais " + restantes;
    }

    private String nomeDoMes(YearMonth mes) {
        String nome = mes.getMonth().getDisplayName(TextStyle.FULL, PT_BR);
        return nome.substring(0, 1).toUpperCase(PT_BR) + nome.substring(1);
    }
}
