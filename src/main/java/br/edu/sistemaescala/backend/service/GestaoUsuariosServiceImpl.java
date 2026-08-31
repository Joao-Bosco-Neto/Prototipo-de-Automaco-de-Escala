package br.edu.sistemaescala.backend.service;

import java.util.List;
import java.util.Optional;

import br.edu.sistemaescala.backend.model.AcaoSeguranca;
import br.edu.sistemaescala.backend.model.ResultadoSeguranca;
import br.edu.sistemaescala.backend.model.RoleUsuario;
import br.edu.sistemaescala.backend.model.Usuario;
import br.edu.sistemaescala.backend.repository.UsuarioRepository;

public class GestaoUsuariosServiceImpl implements GestaoUsuariosService {

    private final UsuarioRepository usuarioRepository;
    private final AutenticacaoService autenticacaoService;
    private final SessaoUsuario sessaoUsuario;
    private final AutorizacaoService autorizacaoService;
    private final LogSegurancaService logSegurancaService;

    public GestaoUsuariosServiceImpl(UsuarioRepository usuarioRepository,
                                     AutenticacaoService autenticacaoService,
                                     SessaoUsuario sessaoUsuario) {
        this(usuarioRepository, autenticacaoService, sessaoUsuario, new AutorizacaoService());
    }

    public GestaoUsuariosServiceImpl(UsuarioRepository usuarioRepository,
                                     AutenticacaoService autenticacaoService,
                                     SessaoUsuario sessaoUsuario,
                                     AutorizacaoService autorizacaoService) {
        this(usuarioRepository, autenticacaoService, sessaoUsuario, autorizacaoService,
                new LogSegurancaServiceImpl(sessaoUsuario));
    }

    public GestaoUsuariosServiceImpl(UsuarioRepository usuarioRepository,
                                     AutenticacaoService autenticacaoService,
                                     SessaoUsuario sessaoUsuario,
                                     AutorizacaoService autorizacaoService,
                                     LogSegurancaService logSegurancaService) {
        this.usuarioRepository = usuarioRepository;
        this.autenticacaoService = autenticacaoService;
        this.sessaoUsuario = sessaoUsuario;
        this.autorizacaoService = autorizacaoService;
        this.logSegurancaService = logSegurancaService;
    }

    @Override
    public List<Usuario> listar() {
        autorizacaoService.exigirAdministrador(sessaoUsuario);
        return usuarioRepository.listar();
    }

    @Override
    public Optional<Usuario> buscarPorId(int id) {
        autorizacaoService.exigirAdministrador(sessaoUsuario);
        return usuarioRepository.buscarPorId(id);
    }

    @Override
    public Usuario cadastrar(String nome, String login, String senha, String confirmacaoSenha, RoleUsuario role, boolean ativo) {
        autorizacaoService.exigirAdministrador(sessaoUsuario);
        validarTexto(nome, "Nome do usuário não pode ficar vazio");
        validarTexto(login, "Login não pode ficar vazio");

        if (usuarioRepository.buscarPorLogin(login.trim()).isPresent()) {
            throw new RegraUsuarioException("Já existe um usuário cadastrado com este login");
        }

        if (senha == null || senha.length() < 8) {
            throw new SenhaFracaException();
        }
        if (!senha.equals(confirmacaoSenha)) {
            throw new SenhasNaoConferemException();
        }

        RoleUsuario roleFinal = role != null ? role : RoleUsuario.GESTOR;
        Usuario usuario = new Usuario();
        usuario.setNome(nome.trim());
        usuario.setLogin(login.trim());
        usuario.setSenhaHash(autenticacaoService.gerarHash(senha));
        usuario.setRole(roleFinal);
        usuario.setAtivo(ativo);

        Usuario criado = usuarioRepository.inserir(usuario);

        // So identidade e perfil na trilha. A senha e o hash ficam de fora
        // do log, sempre (CWE-532).
        logSegurancaService.registrar(AcaoSeguranca.USUARIO_CRIADO, ResultadoSeguranca.SUCESSO,
                "conta criada: login=" + criado.getLogin() + ", perfil=" + roleFinal
                        + ", ativo=" + criado.isAtivo());
        return criado;
    }

    @Override
    public Usuario atualizar(int id, String nome, String login, RoleUsuario role, boolean ativo) {
        Usuario usuarioLogado = autorizacaoService.exigirAdministrador(sessaoUsuario);
        validarTexto(nome, "Nome do usuário não pode ficar vazio");
        validarTexto(login, "Login não pode ficar vazio");

        Usuario usuario = usuarioRepository.buscarPorId(id)
                .orElseThrow(() -> new RegraUsuarioException("Usuário não encontrado"));

        Optional<Usuario> existenteComLogin = usuarioRepository.buscarPorLogin(login.trim());
        if (existenteComLogin.isPresent() && !existenteComLogin.get().getId().equals(id)) {
            throw new RegraUsuarioException("Já existe um usuário cadastrado com este login");
        }

        RoleUsuario roleFinal = role != null ? role : RoleUsuario.GESTOR;

        // Bloqueio de autodesativação
        if (usuarioLogado.getId() != null && usuarioLogado.getId().equals(id) && !ativo) {
            throw new RegraUsuarioException("O administrador não pode desativar o próprio usuário");
        }

        // Bloqueio de remoção ou rebaixamento do último admin ativo
        boolean eraAdminAtivo = usuario.getRole() == RoleUsuario.ADMIN && usuario.isAtivo();
        boolean deixaraDeSerAdminAtivo = roleFinal != RoleUsuario.ADMIN || !ativo;
        if (eraAdminAtivo && deixaraDeSerAdminAtivo) {
            long outrosAdminsAtivos = usuarioRepository.listar().stream()
                    .filter(u -> u.isAtivo() && u.getRole() == RoleUsuario.ADMIN && !u.getId().equals(id))
                    .count();
            if (outrosAdminsAtivos == 0) {
                throw new RegraUsuarioException("Não é permitido desativar ou alterar o perfil do último administrador ativo");
            }
        }

        boolean estavaAtivo = usuario.isAtivo();

        usuario.setNome(nome.trim());
        usuario.setLogin(login.trim());
        usuario.setRole(roleFinal);
        usuario.setAtivo(ativo);

        Usuario atualizado = usuarioRepository.atualizar(usuario);
        registrarMudancaDeStatus(atualizado.getLogin(), estavaAtivo, ativo);
        return atualizado;
    }

    @Override
    public void alterarStatus(int id, boolean ativo) {
        Usuario usuarioLogado = autorizacaoService.exigirAdministrador(sessaoUsuario);
        Usuario usuario = usuarioRepository.buscarPorId(id)
                .orElseThrow(() -> new RegraUsuarioException("Usuário não encontrado"));

        if (!ativo) {
            // Bloqueio de autodesativação
            if (usuarioLogado.getId() != null && usuarioLogado.getId().equals(id)) {
                throw new RegraUsuarioException("O administrador não pode desativar o próprio usuário");
            }

            // Bloqueio de desativação do último admin ativo
            if (usuario.getRole() == RoleUsuario.ADMIN && usuario.isAtivo()) {
                long outrosAdminsAtivos = usuarioRepository.listar().stream()
                        .filter(u -> u.isAtivo() && u.getRole() == RoleUsuario.ADMIN && !u.getId().equals(id))
                        .count();
                if (outrosAdminsAtivos == 0) {
                    throw new RegraUsuarioException("Não é permitido desativar o último administrador ativo");
                }
            }
        }

        boolean estavaAtivo = usuario.isAtivo();
        usuario.setAtivo(ativo);
        usuarioRepository.atualizar(usuario);
        registrarMudancaDeStatus(usuario.getLogin(), estavaAtivo, ativo);
    }

    @Override
    public void redefinirSenha(int id, String novaSenha, String confirmacaoSenha) {
        autorizacaoService.exigirAdministrador(sessaoUsuario);
        Usuario usuario = usuarioRepository.buscarPorId(id)
                .orElseThrow(() -> new RegraUsuarioException("Usuário não encontrado"));

        if (novaSenha == null || novaSenha.length() < 8) {
            throw new SenhaFracaException();
        }
        if (confirmacaoSenha == null || !novaSenha.equals(confirmacaoSenha)) {
            throw new SenhasNaoConferemException();
        }

        String novoHash = autenticacaoService.gerarHash(novaSenha);
        usuarioRepository.atualizarSenha(id, novoHash);

        // A trilha registra que a senha mudou e de quem — nunca a senha nem
        // o hash gerado (CWE-532).
        logSegurancaService.registrar(AcaoSeguranca.SENHA_REDEFINIDA, ResultadoSeguranca.SUCESSO,
                "senha redefinida para o login=" + usuario.getLogin());
    }

    /**
     * Grava a desativacao ou a reativacao da conta, e so isso: uma edicao que
     * nao mexeu no status nao gera evento de seguranca.
     */
    private void registrarMudancaDeStatus(String loginAlvo, boolean estavaAtivo, boolean ficouAtivo) {
        if (estavaAtivo == ficouAtivo) {
            return;
        }
        AcaoSeguranca acao = ficouAtivo ? AcaoSeguranca.USUARIO_REATIVADO : AcaoSeguranca.USUARIO_DESATIVADO;
        logSegurancaService.registrar(acao, ResultadoSeguranca.SUCESSO, "conta alvo: login=" + loginAlvo);
    }

    private void validarTexto(String valor, String mensagem) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(mensagem);
        }
    }
}

