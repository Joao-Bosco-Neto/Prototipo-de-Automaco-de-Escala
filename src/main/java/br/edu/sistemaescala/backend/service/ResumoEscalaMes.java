package br.edu.sistemaescala.backend.service;

/**
 * Quanto existe montado num mês: usado pela tela para mostrar números reais
 * na confirmação de "Limpar mês" (Issue #44).
 *
 * @param turnos    quantidade de escala_turno no mês
 * @param alocacoes quantidade de agentes alocados nesses turnos
 */
public record ResumoEscalaMes(int turnos, int alocacoes) {

    public boolean vazio() {
        return turnos == 0;
    }
}
