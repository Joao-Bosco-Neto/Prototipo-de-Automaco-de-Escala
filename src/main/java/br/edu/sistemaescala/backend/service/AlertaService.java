package br.edu.sistemaescala.backend.service;

import java.time.LocalDate;
import java.util.List;

/**
 * Levanta as pendências do painel "Pendências e alertas" da tela "Visão geral"
 * (issue #55).
 *
 * <p>Cada alerta sai do estado do banco, não de texto fixo na tela: é o que a
 * consulta encontra que decide se a pendência existe, qual é o seu número e
 * quais nomes ela cita. Um mês sem problema nenhum devolve lista vazia, e é a
 * tela que traduz isso na mensagem de "nada pendente".</p>
 */
public interface AlertaService {

    /**
     * As pendências abertas tomando {@code diaReferencia} como "hoje": o mês
     * dele é o recorte das verificações mensais e o instante dele é o corte
     * entre plantão passado e futuro.
     *
     * <p>Vem ordenada da mais grave para a menos grave, na ordem de
     * {@link SeveridadeAlerta} — o painel lista de cima para baixo sem
     * reordenar nada.</p>
     */
    List<AlertaDashboard> levantar(LocalDate diaReferencia);
}
