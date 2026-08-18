-- Ativa modo de compatibilidade PostgreSQL no H2
SET MODE PostgreSQL;

CREATE TYPE role_usuario AS ENUM ('admin', 'gestor');

CREATE TABLE configuracao (
    id INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nome_organizacao VARCHAR(255) NOT NULL
);

CREATE TABLE usuario (
    id INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nome VARCHAR(100) NOT NULL UNIQUE,
    senha_hash VARCHAR(255) NOT NULL,
    role role_usuario NOT NULL DEFAULT 'gestor',
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    criado_em TIMESTAMP DEFAULT NOW()
);

CREATE TABLE funcionario (
    id INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nome VARCHAR(150) NOT NULL,
    matricula VARCHAR(50),
    telefone VARCHAR(20),
    horas_banco INT NOT NULL DEFAULT 0,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    criado_em TIMESTAMP DEFAULT NOW()
);

CREATE TABLE tipo_turno (
    id INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nome VARCHAR(100) NOT NULL,
    duracao_horas INT NOT NULL,
    intervalo_descanso_horas INT NOT NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE escala_turno (
    id INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tipo_turno_id INT NOT NULL REFERENCES tipo_turno(id),
    inicio TIMESTAMP NOT NULL,
    fim TIMESTAMP NOT NULL,
    min_agentes INT NOT NULL DEFAULT 2,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    CHECK (fim > inicio)
);

CREATE TABLE escala_funcionario (
    id INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    escala_turno_id INT NOT NULL REFERENCES escala_turno(id),
    funcionario_id INT NOT NULL REFERENCES funcionario(id),
    cobertura_de INT REFERENCES escala_funcionario(id),
    UNIQUE (escala_turno_id, funcionario_id)
);

CREATE INDEX idx_escala_funcionario_funcionario ON escala_funcionario(funcionario_id);
CREATE INDEX idx_escala_turno_inicio ON escala_turno(inicio);
CREATE INDEX idx_escala_turno_fim ON escala_turno(fim);
