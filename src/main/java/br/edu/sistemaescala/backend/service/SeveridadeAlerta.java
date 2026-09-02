package br.edu.sistemaescala.backend.service;

/**
 * Quanto uma pendência do painel da "Visão geral" cobra do gestor (issue #55).
 *
 * <p>A ordem da enumeração é a ordem de exibição: o painel lista primeiro o que
 * é mais grave. A cor de cada nível fica com a tela — o backend não conhece
 * classe de CSS.</p>
 */
public enum SeveridadeAlerta {

    /** Inconsistência nos dados: a escala está gravada com algo que não deveria existir. */
    CRITICO,

    /** Escala publicável, mas com algo que o gestor precisa resolver ou confirmar. */
    ATENCAO,

    /** Aviso de planejamento: nada está errado, só há trabalho pela frente. */
    INFORMATIVO
}
