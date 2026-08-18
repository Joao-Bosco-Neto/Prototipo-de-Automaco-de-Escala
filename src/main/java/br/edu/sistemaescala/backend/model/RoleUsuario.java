package br.edu.sistemaescala.backend.model;

public enum RoleUsuario {
    ADMIN("admin"),
    GESTOR("gestor");

    private final String valor;

    RoleUsuario(String valor) {
        this.valor = valor;
    }

    public String valor() {
        return valor;
    }

    @Override
    public String toString() {
        return valor;
    }

    public static RoleUsuario deValor(String valor) {
        for (RoleUsuario role : values()) {
            if (role.valor.equals(valor)) {
                return role;
            }
        }
        throw new IllegalArgumentException("Role de usuario invalida: " + valor);
    }
}