package br.edu.sistemaescala.backend.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;

import br.edu.sistemaescala.backend.repository.EscalaFuncionarioRepository;
import br.edu.sistemaescala.backend.repository.EscalaTurnoRepository;
import br.edu.sistemaescala.backend.repository.FuncionarioRepository;
import br.edu.sistemaescala.backend.repository.jdbc.EscalaFuncionarioRepositoryJdbc;
import br.edu.sistemaescala.backend.repository.jdbc.EscalaTurnoRepositoryJdbc;
import br.edu.sistemaescala.backend.repository.jdbc.FuncionarioRepositoryJdbc;

/**
 * Implementação de {@link DashboardService}: quatro consultas agregadas, uma
 * por indicador, montadas num {@link IndicadoresDashboard}.
 *
 * <p>Fala direto com os repositórios em vez de reaproveitar os serviços de
 * cobertura e de escala de propósito: os métodos que eles expõem devolvem
 * listagens completas, e contar o tamanho delas é exatamente o que a issue #53
 * proíbe.</p>
 */
public class DashboardServiceImpl implements DashboardService {

    private final EscalaTurnoRepository escalaTurnoRepository;
    private final EscalaFuncionarioRepository escalaFuncionarioRepository;
    private final FuncionarioRepository funcionarioRepository;

    /** Construtor de conveniência com os repositórios JDBC padrão. */
    public DashboardServiceImpl() {
        this(new EscalaTurnoRepositoryJdbc(), new EscalaFuncionarioRepositoryJdbc(),
                new FuncionarioRepositoryJdbc());
    }

    public DashboardServiceImpl(EscalaTurnoRepository escalaTurnoRepository,
                                EscalaFuncionarioRepository escalaFuncionarioRepository,
                                FuncionarioRepository funcionarioRepository) {
        this.escalaTurnoRepository = Objects.requireNonNull(escalaTurnoRepository);
        this.escalaFuncionarioRepository = Objects.requireNonNull(escalaFuncionarioRepository);
        this.funcionarioRepository = Objects.requireNonNull(funcionarioRepository);
    }

    @Override
    public IndicadoresDashboard carregar(LocalDate diaReferencia) {
        Objects.requireNonNull(diaReferencia, "diaReferencia não pode ser nulo");
        YearMonth mes = YearMonth.from(diaReferencia);

        List<PlantaoDoDiaItem> plantoesDeHoje = escalaTurnoRepository.resumirPlantoesDoDia(diaReferencia);
        ContagemFuncionarios funcionarios = funcionarioRepository.contarPorStatus();
        int coberturas = escalaFuncionarioRepository.contarCoberturasDoMes(mes);
        int diasIncompletos = escalaTurnoRepository.contarDiasComEfetivoIncompleto(mes);

        return new IndicadoresDashboard(diaReferencia, mes, plantoesDeHoje, funcionarios,
                coberturas, diasIncompletos);
    }
}
