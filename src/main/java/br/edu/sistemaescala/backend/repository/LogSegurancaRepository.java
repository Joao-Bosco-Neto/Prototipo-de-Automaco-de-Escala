package br.edu.sistemaescala.backend.repository;

import java.time.LocalDateTime;
import java.util.List;

import br.edu.sistemaescala.backend.model.LogSeguranca;

/**
 * Persistencia da trilha de auditoria de eventos de seguranca (OWASP A09).
 *
 * <p>A interface e deliberadamente so de escrita e leitura: nao existe
 * {@code atualizar} nem {@code remover}. Trilha de auditoria que pode ser
 * editada nao serve como trilha de auditoria — o registro so entra, nunca
 * muda.</p>
 */
public interface LogSegurancaRepository {

    /**
     * Grava um evento de seguranca. O {@code id} e a {@code dataHora}
     * efetivamente gravados voltam preenchidos no proprio objeto.
     */
    LogSeguranca inserir(LogSeguranca log);

    /**
     * Eventos gravados na janela informada, do mais recente para o mais
     * antigo. Existe para a consulta pelo administrador do banco e para os
     * testes; nao ha tela usando isto (fora do escopo da issue #64).
     */
    List<LogSeguranca> listarPorPeriodo(LocalDateTime inicio, LocalDateTime fim);
}
