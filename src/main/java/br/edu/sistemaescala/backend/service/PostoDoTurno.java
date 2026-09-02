package br.edu.sistemaescala.backend.service;

/**
 * Um posto de trabalho ocupado num turno, já com a cobertura resolvida
 * (issue #54).
 *
 * <p>Uma cobertura gera duas linhas em {@code escala_funcionario} no mesmo
 * turno — a do titular ausente e a de quem assumiu —, mas as duas ocupam um
 * único posto. Este record é esse posto: {@code titular} é quem estava
 * escalado e {@code substituto} é quem foi no lugar dele, ou {@code null}
 * quando o próprio titular cumpriu o plantão.</p>
 *
 * <p>Os nomes vêm completos. Encurtar para caber na célula é decisão de
 * apresentação e fica com a tela, que mostra o nome curto na faixa estreita da
 * semana e o nome inteiro na tabela larga dos próximos plantões.</p>
 *
 * @param titular    nome do funcionário escalado no posto
 * @param substituto nome de quem cobriu, ou {@code null} se não houve cobertura
 */
public record PostoDoTurno(String titular, String substituto) {

    public boolean coberto() {
        return substituto != null;
    }
}
