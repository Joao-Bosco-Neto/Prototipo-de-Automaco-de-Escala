package br.edu.sistemaescala.backend.service;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;

import br.edu.sistemaescala.backend.model.RoleUsuario;
import br.edu.sistemaescala.backend.model.Usuario;

/**
 * Serviço responsável por validar permissões de acesso na camada de negócio (backend/service).
 *
 * Conforme OWASP A01 (Broken Access Control), o controle de acesso nunca deve
 * depender unicamente de restrições de interface gráfica (ocultação de menus/botões).
 * Todas as operações privilegiadas devem ser validadas na camada de serviço,
 * lançando AcessoNegadoException e registrando log de segurança em caso de violação.
 */
public class AutorizacaoService {

    private static final Logger LOGGER = System.getLogger(AutorizacaoService.class.getName());

    public Usuario exigirAdministrador(SessaoUsuario sessao) {
        return exigirRole(sessao, RoleUsuario.ADMIN, "Apenas administradores podem acessar esta area");
    }

    public Usuario exigirAutenticado(SessaoUsuario sessao) {
        if (sessao == null) {
            LOGGER.log(Level.WARNING, "Tentativa de acesso restrito com sessão nula");
            throw new AcessoNegadoException("Nenhum usuario autenticado");
        }
        Usuario usuario = sessao.usuarioAtual().orElse(null);
        if (usuario == null) {
            LOGGER.log(Level.WARNING, "Tentativa de acesso restrito sem usuário autenticado");
            throw new AcessoNegadoException("Nenhum usuario autenticado");
        }
        return usuario;
    }

    public Usuario exigirRole(SessaoUsuario sessao, RoleUsuario roleEsperado, String mensagemErro) {
        Usuario usuario = exigirAutenticado(sessao);
        if (usuario.getRole() != roleEsperado) {
            LOGGER.log(Level.WARNING, "Acesso negado: usuário ''{0}'' (perfil {1}) tentou executar operação restrita ao perfil {2}",
                    usuario.getLogin(), usuario.getRole(), roleEsperado);
            throw new AcessoNegadoException(mensagemErro != null ? mensagemErro : "Acesso negado: perfil insuficiente");
        }
        return usuario;
    }

    public boolean possuiRole(SessaoUsuario sessao, RoleUsuario roleEsperado) {
        if (sessao == null) {
            return false;
        }
        return sessao.usuarioAtual()
                .map(usuario -> usuario.getRole() == roleEsperado)
                .orElse(false);
    }
}