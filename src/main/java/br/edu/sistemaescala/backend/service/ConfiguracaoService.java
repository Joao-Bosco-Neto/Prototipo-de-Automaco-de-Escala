package br.edu.sistemaescala.backend.service;

import java.math.BigDecimal;
import java.util.Optional;

import br.edu.sistemaescala.backend.model.Configuracao;

/**
 * Interface de regras de negócio para gestão e edição das configurações da organização (Issue #70 / Backlog #70).
 */
public interface ConfiguracaoService {

    /**
     * Busca a configuração atual da organização.
     */
    Optional<Configuracao> buscar();

    /**
     * Salva as configurações da organização. Exige permissão de administrador.
     *
     * @param nomeOrganizacao       Nome institucional da organização (obrigatório)
     * @param subtitulo             Subtítulo / diretoria (opcional)
     * @param cargaHorariaMensal    Carga horária mensal padrão em horas (opcional)
     * @param apuracaoBancoHoras    Regime de apuração do banco de horas ('mensal' ou 'continuo')
     * @param caminhoPdfPadrao      Diretório padrão para exportação de PDFs (opcional)
     * @return A configuração atualizada
     */
    Configuracao salvar(String nomeOrganizacao, String subtitulo, BigDecimal cargaHorariaMensal,
                        String apuracaoBancoHoras, String caminhoPdfPadrao);
}

