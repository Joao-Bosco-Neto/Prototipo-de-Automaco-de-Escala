CREATE TYPE role_usuario AS ENUM ('admin', 'escrivao');

CREATE TABLE funcionario (
    id           SERIAL PRIMARY KEY,
    nome         VARCHAR(200) NOT NULL,
    cpf          VARCHAR(11) NOT NULL UNIQUE,
    horas_banco  INTEGER NOT NULL DEFAULT 0,
    ativo        BOOLEAN NOT NULL
);

CREATE TABLE usuario (
    id     SERIAL PRIMARY KEY,
    nome   VARCHAR(200) NOT NULL UNIQUE,
    senha  VARCHAR(200) NOT NULL,
    role   role_usuario NOT NULL DEFAULT 'escrivao',
    ativo  BOOLEAN NOT NULL
);

CREATE TABLE escala_dia (
    id          SERIAL PRIMARY KEY,
    data_escala DATE NOT NULL UNIQUE
);

CREATE TABLE escala_funcionario (
    id             SERIAL PRIMARY KEY,
    escala_dia_id  INTEGER NOT NULL REFERENCES escala_dia(id),
    funcionario_id INTEGER NOT NULL REFERENCES funcionario(id),
    cobertura_de   INTEGER REFERENCES escala_funcionario(id),
    ativo          BOOLEAN NOT NULL,

    CONSTRAINT uq_escala_funcionario_dia_func UNIQUE (escala_dia_id, funcionario_id)
);