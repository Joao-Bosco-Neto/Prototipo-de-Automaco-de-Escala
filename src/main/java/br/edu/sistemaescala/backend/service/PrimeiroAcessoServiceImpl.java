package br.edu.sistemaescala.backend.service;

import br.edu.sistemaescala.backend.model.Configuracao;
import br.edu.sistemaescala.backend.model.RoleUsuario;
import br.edu.sistemaescala.backend.model.Usuario;
import br.edu.sistemaescala.backend.repository.ConfiguracaoRepository;
import br.edu.sistemaescala.backend.repository.UsuarioRepository;

public class PrimeiroAcessoServiceImpl implements PrimeiroAcessoService {

    private final UsuarioRepository usuarioRepository;
    private final ConfiguracaoRepository configuracaoRepository;
    private final AutenticacaoService autenticacaoService;

    public PrimeiroAcessoServiceImpl(UsuarioRepository usuarioRepository,
                                     ConfiguracaoRepository configuracaoRepository,
                                     AutenticacaoService autenticacaoService) {
        this.usuarioRepository = usuarioRepository;
        this.configuracaoRepository = configuracaoRepository;
        this.autenticacaoService = autenticacaoService;
    }

    @Override
    public boolean primeiroAcesso() {
        return usuarioRepository.listar().isEmpty();
    }

    @Override
    public void configurar(String nomeOrganizacao, String login, String senha, String confirmacaoSenha) {
        validarTexto(nomeOrganizacao, "Nome da organizacao nao pode ficar vazio");
        validarTexto(login, "Login nao pode ficar vazio");
        if (senha == null || senha.length() < 8) {
            throw new SenhaFracaException();
        }
        if (!senha.equals(confirmacaoSenha)) {
            throw new SenhasNaoConferemException();
        }
        if (!primeiroAcesso()) {
            throw new IllegalStateException("A configuracao inicial ja foi realizada");
        }

        Configuracao configuracao = configuracaoRepository.buscar()
                .orElseThrow(() -> new IllegalStateException("Configuracao do sistema nao encontrada"));
        configuracao.setNomeOrganizacao(nomeOrganizacao.trim());
        configuracaoRepository.atualizar(configuracao);

        Usuario administrador = new Usuario();
        administrador.setNome(login.trim());
        administrador.setLogin(login.trim());
        administrador.setSenhaHash(autenticacaoService.gerarHash(senha));
        administrador.setRole(RoleUsuario.ADMIN);
        administrador.setAtivo(true);
        usuarioRepository.inserir(administrador);
    }

    private void validarTexto(String valor, String mensagem) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(mensagem);
        }
    }
}