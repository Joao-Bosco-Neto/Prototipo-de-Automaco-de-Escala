package br.edu.sistemaescala.backend.service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongConsumer;

import at.favre.lib.crypto.bcrypt.BCrypt;
import br.edu.sistemaescala.backend.model.AcaoSeguranca;
import br.edu.sistemaescala.backend.model.ResultadoSeguranca;
import br.edu.sistemaescala.backend.model.Usuario;
import br.edu.sistemaescala.backend.repository.UsuarioRepository;

public class AutenticacaoServiceImpl implements AutenticacaoService {

    private static final int CUSTO_BCRYPT = 12;
    private static final int TAMANHO_MINIMO_SENHA = 8;
    private static final long ATRASO_INICIAL_MS = 250L;
    private static final long ATRASO_MAXIMO_MS = 8_000L;

    private final UsuarioRepository usuarioRepository;
    private final LogSegurancaService logSegurancaService;
    private final String hashDummyTiming;
    private final Map<String, Integer> falhasPorLogin = new ConcurrentHashMap<>();
    private final LongConsumer esperar;

    public AutenticacaoServiceImpl(UsuarioRepository usuarioRepository) {
        this(usuarioRepository, new LogSegurancaServiceImpl());
    }

    public AutenticacaoServiceImpl(UsuarioRepository usuarioRepository, LogSegurancaService logSegurancaService) {
        this(usuarioRepository, logSegurancaService, AutenticacaoServiceImpl::esperarComInterrupcao);
    }

    AutenticacaoServiceImpl(UsuarioRepository usuarioRepository, LongConsumer esperar) {
        this(usuarioRepository, new LogSegurancaServiceImpl(), esperar);
    }

    AutenticacaoServiceImpl(UsuarioRepository usuarioRepository, LogSegurancaService logSegurancaService,
                            LongConsumer esperar) {
        this.usuarioRepository = usuarioRepository;
        this.logSegurancaService = logSegurancaService;
        this.esperar = esperar;
        this.hashDummyTiming = gerarHashDummyTiming();
    }

    @Override
    public Optional<Usuario> autenticar(String login, String senha) {
        if (login == null || senha == null || login.isBlank() || senha.isBlank()) {
            return Optional.empty();
        }
        Optional<Usuario> usuarioEncontrado = usuarioRepository.buscarPorLogin(login);
        char[] senhaChars = senha.toCharArray();
        try {
            if (usuarioEncontrado.isEmpty()) {
                BCrypt.verifyer().verify(senhaChars, hashDummyTiming);
                // A string tentada vai para a trilha como o usuario digitou;
                // quem limpa as quebras de linha dela e o LogSegurancaService.
                registrarFalhaDeLogin(login, "usuario inexistente");
                aplicarAtraso(login);
                return Optional.empty();
            }

            Usuario usuario = usuarioEncontrado.get();
            boolean senhaCorreta = BCrypt.verifyer().verify(senhaChars, usuario.getSenhaHash()).verified;

            // Usuario inexistente, inativo ou senha errada retornam o mesmo
            // Optional.empty(), sem diferenca observavel de fora: nao da
            // para saber qual dos tres casos aconteceu.
            if (!usuario.isAtivo() || !senhaCorreta) {
                // A trilha distingue os casos porque so o administrador do
                // banco a le; o retorno para quem chamou continua sendo o
                // mesmo Optional.empty() dos tres casos.
                registrarFalhaDeLogin(login, !usuario.isAtivo() ? "usuario inativo" : "senha incorreta");
                aplicarAtraso(login);
                return Optional.empty();
            }

            falhasPorLogin.remove(chaveLogin(login));
            usuarioRepository.registrarUltimoLogin(usuario.getId(), LocalDateTime.now());
            logSegurancaService.registrar(AcaoSeguranca.LOGIN, usuario.getLogin(),
                    ResultadoSeguranca.SUCESSO, null);
            return Optional.of(usuario);
        } finally {
            Arrays.fill(senhaChars, '0');
        }
    }

    @Override
    public String gerarHash(String senhaPura) {
        validarSenha(senhaPura);
        char[] senhaChars = senhaPura.toCharArray();
        try {
            return BCrypt.withDefaults().hashToString(CUSTO_BCRYPT, senhaChars);
        } finally {
            Arrays.fill(senhaChars, '0');
        }
    }

    @Override
    public void alterarSenha(int usuarioId, String senhaAtual, String senhaNova) {
        validarSenha(senhaNova);
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

    /**
     * Grava a tentativa falha antes do atraso, na mesma posicao nos dois
     * caminhos de falha: gravar depois do {@link #aplicarAtraso} em so um
     * deles criaria diferenca de tempo entre "usuario inexistente" e "senha
     * errada" — exatamente o que o atraso existe para esconder.
     *
     * <p>Nem a senha nem o hash entram no registro (CWE-532): so o login
     * tentado e o motivo em texto.</p>
     */
    private void registrarFalhaDeLogin(String loginTentado, String motivo) {
        logSegurancaService.registrar(AcaoSeguranca.LOGIN, loginTentado, ResultadoSeguranca.FALHA, motivo);
    }

    private void aplicarAtraso(String login) {
        String chave = chaveLogin(login);
        int numeroFalha = falhasPorLogin.merge(chave, 1, Integer::sum);
        int deslocamento = Math.min(numeroFalha - 1, 5);
        long atraso = Math.min(ATRASO_INICIAL_MS << deslocamento, ATRASO_MAXIMO_MS);
        esperar.accept(atraso);
    }

    private String chaveLogin(String login) {
        return login == null ? "" : login.trim().toLowerCase();
    }

    private void validarSenha(String senha) {
        if (senha == null || senha.length() < TAMANHO_MINIMO_SENHA) {
            throw new SenhaFracaException();
        }
    }

    private static String gerarHashDummyTiming() {
        byte[] aleatorio = new byte[32];
        new SecureRandom().nextBytes(aleatorio);
        char[] segredo = new char[aleatorio.length * 2];
        for (int indice = 0; indice < aleatorio.length; indice++) {
            int valor = aleatorio[indice] & 0xff;
            segredo[indice * 2] = Character.forDigit(valor >>> 4, 16);
            segredo[indice * 2 + 1] = Character.forDigit(valor & 0x0f, 16);
        }
        try {
            return BCrypt.withDefaults().hashToString(CUSTO_BCRYPT, segredo);
        } finally {
            Arrays.fill(aleatorio, (byte) 0);
            Arrays.fill(segredo, '0');
        }
    }

    private static void esperarComInterrupcao(long atrasoMs) {
        try {
            Thread.sleep(atrasoMs);
        } catch (InterruptedException excecao) {
            Thread.currentThread().interrupt();
        }
    }
}
