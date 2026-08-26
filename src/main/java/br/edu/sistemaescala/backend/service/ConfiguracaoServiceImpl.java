package br.edu.sistemaescala.backend.service;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

import br.edu.sistemaescala.backend.model.Configuracao;
import br.edu.sistemaescala.backend.repository.ConfiguracaoRepository;

/**
 * Implementação das regras de negócio para configurações da organização.
 */
public class ConfiguracaoServiceImpl implements ConfiguracaoService {

    private final ConfiguracaoRepository configuracaoRepository;
    private final SessaoUsuario sessaoUsuario;
    private final AutorizacaoService autorizacaoService;

    public ConfiguracaoServiceImpl(ConfiguracaoRepository configuracaoRepository,
                                   SessaoUsuario sessaoUsuario) {
        this(configuracaoRepository, sessaoUsuario, new AutorizacaoService());
    }

    public ConfiguracaoServiceImpl(ConfiguracaoRepository configuracaoRepository,
                                   SessaoUsuario sessaoUsuario,
                                   AutorizacaoService autorizacaoService) {
        this.configuracaoRepository = Objects.requireNonNull(configuracaoRepository, "configuracaoRepository não pode ser nulo");
        this.sessaoUsuario = Objects.requireNonNull(sessaoUsuario, "sessaoUsuario não pode ser nula");
        this.autorizacaoService = Objects.requireNonNull(autorizacaoService, "autorizacaoService não pode ser nulo");
    }

    @Override
    public Optional<Configuracao> buscar() {
        return configuracaoRepository.buscar();
    }

    @Override
    public Configuracao salvar(String nomeOrganizacao, String subtitulo, BigDecimal cargaHorariaMensal,
                               String apuracaoBancoHoras, String caminhoPdfPadrao) {
        autorizacaoService.exigirAdministrador(sessaoUsuario);

        if (nomeOrganizacao == null || nomeOrganizacao.isBlank()) {
            throw new RegraConfiguracaoException("O nome da organização é obrigatório.");
        }

        String apuracaoNormalizada = apuracaoBancoHoras != null ? apuracaoBancoHoras.trim().toLowerCase() : "mensal";
        if (!"mensal".equals(apuracaoNormalizada) && !"continuo".equals(apuracaoNormalizada)) {
            throw new RegraConfiguracaoException("O regime de apuração do banco de horas deve ser 'mensal' ou 'continuo'.");
        }

        if (cargaHorariaMensal != null && cargaHorariaMensal.compareTo(BigDecimal.ZERO) < 0) {
            throw new RegraConfiguracaoException("A carga horária mensal não pode ser negativa.");
        }

        Configuracao configuracao = configuracaoRepository.buscar()
                .orElseGet(() -> {
                    Configuracao nova = new Configuracao();
                    nova.setId(1);
                    return nova;
                });

        configuracao.setNomeOrganizacao(nomeOrganizacao.trim());
        configuracao.setSubtitulo(subtitulo != null && !subtitulo.isBlank() ? subtitulo.trim() : null);
        configuracao.setCargaHorariaMensal(cargaHorariaMensal);
        configuracao.setApuracaoBancoHoras(apuracaoNormalizada);
        configuracao.setCaminhoPdfPadrao(caminhoPdfPadrao != null && !caminhoPdfPadrao.isBlank() ? caminhoPdfPadrao.trim() : null);

        return configuracaoRepository.atualizar(configuracao);
    }
}

