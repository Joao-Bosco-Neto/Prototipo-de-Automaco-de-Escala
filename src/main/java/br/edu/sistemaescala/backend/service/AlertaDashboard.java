package br.edu.sistemaescala.backend.service;

/**
 * Uma pendência do painel "Pendências e alertas" da tela "Visão geral"
 * (issue #55).
 *
 * <p>O texto vem pronto do serviço, já com as quantidades e os nomes que a
 * consulta encontrou — nada de frase fixa montada na tela. A tela só escolhe a
 * classe de selo a partir da {@code severidade}.</p>
 *
 * @param tipo       qual verificação gerou a pendência
 * @param severidade quanto ela cobra do gestor, e por tabela a cor do selo
 * @param titulo     a pendência em uma linha (ex.: "3 dias com efetivo incompleto")
 * @param descricao  o que fazer a respeito, em uma ou duas frases
 */
public record AlertaDashboard(
        TipoAlerta tipo,
        SeveridadeAlerta severidade,
        String titulo,
        String descricao) {
}
