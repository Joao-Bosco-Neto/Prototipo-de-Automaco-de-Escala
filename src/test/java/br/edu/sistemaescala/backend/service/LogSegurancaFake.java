package br.edu.sistemaescala.backend.service;

import java.util.ArrayList;
import java.util.List;

import br.edu.sistemaescala.backend.model.AcaoSeguranca;
import br.edu.sistemaescala.backend.model.ResultadoSeguranca;

/**
 * Trilha de auditoria em memória para os testes de serviço.
 *
 * <p>Existe para os testes de {@link AutenticacaoServiceImpl} e
 * {@link GestaoUsuariosServiceImpl} continuarem sem tocar no banco: o
 * construtor de produção desses serviços monta um
 * {@link LogSegurancaServiceImpl} JDBC.</p>
 */
class LogSegurancaFake implements LogSegurancaService {

    /** Um evento registrado, como o serviço o entregou — sem sanitização. */
    record Evento(AcaoSeguranca acao, String identificacao, ResultadoSeguranca resultado, String detalhes) {
    }

    private final List<Evento> eventos = new ArrayList<>();

    @Override
    public void registrar(AcaoSeguranca acao, String identificacao, ResultadoSeguranca resultado, String detalhes) {
        eventos.add(new Evento(acao, identificacao, resultado, detalhes));
    }

    List<Evento> eventos() {
        return List.copyOf(eventos);
    }

    List<Evento> eventosDe(AcaoSeguranca acao) {
        return eventos.stream().filter(evento -> evento.acao() == acao).toList();
    }

    Evento ultimo() {
        if (eventos.isEmpty()) {
            throw new AssertionError("nenhum evento de segurança foi registrado");
        }
        return eventos.get(eventos.size() - 1);
    }

    /** Todo o texto que foi parar na trilha, para conferir que nenhum segredo passou. */
    String textoCompleto() {
        StringBuilder texto = new StringBuilder();
        for (Evento evento : eventos) {
            texto.append(evento.acao()).append('|').append(evento.identificacao()).append('|')
                    .append(evento.resultado()).append('|').append(evento.detalhes()).append('\n');
        }
        return texto.toString();
    }

    void limpar() {
        eventos.clear();
    }
}
