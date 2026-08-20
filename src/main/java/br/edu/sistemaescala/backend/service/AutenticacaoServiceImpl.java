package br.edu.sistemaescala.backend.service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Optional;

import at.favre.lib.crypto.bcrypt.BCrypt;

import br.edu.sistemaescala.backend.model.Usuario;
import br.edu.sistemaescala.backend.repository.UsuarioRepository;

public class AutenticacaoServiceImpl implements AutenticacaoService {

    private static final int CUSTO_BCRYPT = 12;

    // Hash bcrypt valido e fixo, sem correspondencia com senha real de
    // ninguem. Usado so para gastar o mesmo tempo de um verify() de verdade
    // quando o login nao existe, para o tempo de resposta nao denunciar se
    // um login existe ou nao no banco.
    private static final String HASH_DUMMY_TIMING = "$2b$12$Q.4/UwMCG1eiyEQ0dIySUeg1razw9C7c4gjGYa22BK1r/H8CPyAWe";

    private final UsuarioRepository usuarioRepository;

    public AutenticacaoServiceImpl(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    public Optional<Usuario> autenticar(String login, String senha) {
        Optional<Usuario> usuarioEncontrado = usuarioRepository.buscarPorLogin(login);
        char[] senhaChars = senha.toCharArray();
        try {
            if (usuarioEncontrado.isEmpty()) {
                // Login nao existe: ainda assim roda o verify contra o hash
                // dummy acima, ver comentario da constante.
                BCrypt.verifyer().verify(senhaChars, HASH_DUMMY_TIMING);
                return Optional.empty();
            }

            Usuario usuario = usuarioEncontrado.get();
            boolean senhaCorreta = BCrypt.verifyer().verify(senhaChars, usuario.getSenhaHash()).verified;

            // Usuario inexistente, inativo ou senha errada retornam o mesmo
            // Optional.empty(), sem diferenca observavel de fora: nao da
            // para saber qual dos tres casos aconteceu.
            if (!usuario.isAtivo() || !senhaCorreta) {
                return Optional.empty();
            }

            usuarioRepository.registrarUltimoLogin(usuario.getId(), LocalDateTime.now());
            return Optional.of(usuario);
        } finally {
            Arrays.fill(senhaChars, '0');
        }
    }

    @Override
    public String gerarHash(String senhaPura) {
        char[] senhaChars = senhaPura.toCharArray();
        try {
            return BCrypt.withDefaults().hashToString(CUSTO_BCRYPT, senhaChars);
        } finally {
            Arrays.fill(senhaChars, '0');
        }
    }

    @Override
    public void alterarSenha(int usuarioId, String senhaAtual, String senhaNova) {
        Usuario usuario = usuarioRepository.buscarPorId(usuarioId)
                .orElseThrow(SenhaInvalidaException::new);

        char[] senhaAtualChars = senhaAtual.toCharArray();
        boolean senhaAtualCorreta;
        try {
            senhaAtualCorreta = BCrypt.verifyer().verify(senhaAtualChars, usuario.getSenhaHash()).verified;
        } finally {
            Arrays.fill(senhaAtualChars, '0');
        }

        if (!senhaAtualCorreta) {
            throw new SenhaInvalidaException();
        }

        String novoHash = gerarHash(senhaNova);
        usuarioRepository.atualizarSenha(usuarioId, novoHash);
    }
}
