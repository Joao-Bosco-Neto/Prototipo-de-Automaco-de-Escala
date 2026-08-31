package br.edu.sistemaescala.backend.service;

import java.util.List;
import java.util.Objects;

import br.edu.sistemaescala.backend.dao.TransacaoUtil;
import br.edu.sistemaescala.backend.model.AcaoSeguranca;
import br.edu.sistemaescala.backend.model.EscalaExcecao;
import br.edu.sistemaescala.backend.model.EscalaFuncionario;
import br.edu.sistemaescala.backend.model.EscalaTurno;
import br.edu.sistemaescala.backend.model.Funcionario;
import br.edu.sistemaescala.backend.model.RegraExcecao;
import br.edu.sistemaescala.backend.model.ResultadoSeguranca;
import br.edu.sistemaescala.backend.model.Usuario;
import br.edu.sistemaescala.backend.repository.EscalaExcecaoRepository;
import br.edu.sistemaescala.backend.repository.EscalaFuncionarioRepository;
import br.edu.sistemaescala.backend.repository.jdbc.EscalaExcecaoRepositoryJdbc;
import br.edu.sistemaescala.backend.repository.jdbc.EscalaFuncionarioRepositoryJdbc;

/** Implementacao da autorizacao de excecoes as regras da escala (issue #64). */
public class EscalaExcecaoServiceImpl implements EscalaExcecaoService {

    private static final String AUTOR_DESCONHECIDO = "(desconhecido)";

    private final EscalaFuncionarioRepository escalaFuncionarioRepository;
    private final EscalaExcecaoRepository escalaExcecaoRepository;
    private final LogSegurancaService logSegurancaService;
    private final SessaoUsuario sessaoUsuario;

    public EscalaExcecaoServiceImpl(SessaoUsuario sessaoUsuario) {
        this(new EscalaFuncionarioRepositoryJdbc(), new EscalaExcecaoRepositoryJdbc(),
                new LogSegurancaServiceImpl(sessaoUsuario), sessaoUsuario);
    }

    public EscalaExcecaoServiceImpl(EscalaFuncionarioRepository escalaFuncionarioRepository,
                                    EscalaExcecaoRepository escalaExcecaoRepository,
                                    LogSegurancaService logSegurancaService,
                                    SessaoUsuario sessaoUsuario) {
        this.escalaFuncionarioRepository = Objects.requireNonNull(escalaFuncionarioRepository,
                "escalaFuncionarioRepository não pode ser nulo");
        this.escalaExcecaoRepository = Objects.requireNonNull(escalaExcecaoRepository,
                "escalaExcecaoRepository não pode ser nulo");
        this.logSegurancaService = Objects.requireNonNull(logSegurancaService,
                "logSegurancaService não pode ser nulo");
        this.sessaoUsuario = sessaoUsuario;
    }

    @Override
    public EscalaFuncionario alocarComExcecao(EscalaTurno turno, Funcionario funcionario,
                                              ResultadoDescanso descanso) {
        Objects.requireNonNull(turno, "turno não pode ser nulo");
        Objects.requireNonNull(funcionario, "funcionario não pode ser nulo");
        Objects.requireNonNull(descanso, "descanso não pode ser nulo");

        if (funcionario.getId() == null) {
            throw new RegraEscalaExcecaoException("O funcionário a alocar ainda não foi salvo.");
        }
        if (turno.getId() == null || turno.getInicio() == null) {
            throw new RegraEscalaExcecaoException("O turno da exceção está sem período definido.");
        }
        if (descanso.respeitado()) {
            // Sem violacao nao ha excecao a autorizar: gravar uma linha aqui
            // sujaria a trilha com um evento que nao aconteceu.
            throw new RegraEscalaExcecaoException(
                    "O descanso deste plantão está respeitado: não há exceção a autorizar.");
        }

        String autorizadoPor = autorizadoPor();

        EscalaFuncionario alocacao = TransacaoUtil.executar(conexao -> {
            EscalaFuncionario nova = new EscalaFuncionario();
            nova.setEscalaTurno(turno);
            nova.setFuncionario(funcionario);
            // inicio/fim nulos: o agente cumpre o turno inteiro, como o resto
            // da montagem da escala entende a alocacao.
            escalaFuncionarioRepository.inserir(nova, conexao);

            EscalaExcecao excecao = new EscalaExcecao();
            excecao.setEscalaFuncionarioId(nova.getId());
            excecao.setFuncionarioId(funcionario.getId());
            excecao.setDataPlantao(turno.getInicio().toLocalDate());
            excecao.setRegra(RegraExcecao.DESCANSO_MINIMO);
            excecao.setDescricao(descanso.mensagem());
            excecao.setAutorizadoPor(autorizadoPor);
            escalaExcecaoRepository.inserir(excecao, conexao);

            return nova;
        });

        // Só depois do commit: exceção que não entrou não vira evento.
        // A matricula identifica o agente sem despejar o nome completo dele
        // na trilha (CWE-532, "nenhum dado pessoal completo").
        logSegurancaService.registrar(AcaoSeguranca.ESCALA_EXCECAO_AUTORIZADA, ResultadoSeguranca.SUCESSO,
                "regra=" + RegraExcecao.DESCANSO_MINIMO.valor()
                        + ", matrícula=" + funcionario.getMatricula()
                        + ", plantão=" + turno.getInicio().toLocalDate()
                        + ", alocação id=" + alocacao.getId());

        return alocacao;
    }

    @Override
    public List<EscalaExcecao> listarPorFuncionario(int funcionarioId) {
        return escalaExcecaoRepository.listarPorFuncionario(funcionarioId);
    }

    /** Login de quem autorizou; a coluna e NOT NULL, entao nunca volta nulo. */
    private String autorizadoPor() {
        if (sessaoUsuario == null) {
            return AUTOR_DESCONHECIDO;
        }
        return sessaoUsuario.usuarioAtual()
                .map(Usuario::getLogin)
                .filter(login -> !login.isBlank())
                .orElse(AUTOR_DESCONHECIDO);
    }
}
