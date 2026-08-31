-- =====================================================================
-- Sistema de Escala — criacao do banco
--
-- Alvo: H2 em modo PostgreSQL (producao) e PostgreSQL 13+ (dev opcional).
-- O MODE=PostgreSQL vem da URL de conexao, por isso nao ha "SET MODE" aqui
-- (esse comando quebraria o script se rodado contra um PostgreSQL de verdade).
--
-- Todo o script e idempotente: pode ser executado mais de uma vez sem erro.
-- Regras de portabilidade: VARCHAR + CHECK no lugar de ENUM,
-- CURRENT_TIMESTAMP no lugar de NOW(), IDENTITY no lugar de SERIAL.
-- =====================================================================

-- ---------------------------------------------------------------------
-- Configuracao da organizacao (registro unico)
-- Permite instalar o mesmo sistema em outro cliente sem tocar no codigo
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS configuracao (
    id                   INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nome_organizacao     VARCHAR(255) NOT NULL,
    subtitulo            VARCHAR(255),
    carga_horaria_mensal NUMERIC(6,2),
    apuracao_banco_horas VARCHAR(20) NOT NULL DEFAULT 'mensal',
    caminho_pdf_padrao   VARCHAR(500),
    atualizado_em        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_config_apuracao CHECK (apuracao_banco_horas IN ('mensal','continuo'))
);

-- ---------------------------------------------------------------------
-- Usuarios do sistema (quem opera o programa)
-- login e separado de nome: nomes se repetem, login nao (ex.: "alexandre39")
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS usuario (
    id           INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nome         VARCHAR(150) NOT NULL,
    login        VARCHAR(100) NOT NULL UNIQUE,
    senha_hash   VARCHAR(255) NOT NULL,
    role         VARCHAR(20)  NOT NULL DEFAULT 'gestor',
    ativo        BOOLEAN      NOT NULL DEFAULT TRUE,
    ultimo_login TIMESTAMP,
    criado_em    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_usuario_role CHECK (role IN ('admin','gestor'))
);

-- ---------------------------------------------------------------------
-- Pessoas escaladas
-- matricula e o identificador usado em todas as telas: NOT NULL UNIQUE
-- ativo sustenta o filtro Ativos/Inativos (funcionario nunca e excluido,
-- para preservar o historico de plantoes)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS funcionario (
    id          INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nome        VARCHAR(150) NOT NULL,
    matricula   VARCHAR(50)  NOT NULL UNIQUE,
    telefone    VARCHAR(20),
    observacoes VARCHAR(1000),
    ativo       BOOLEAN      NOT NULL DEFAULT TRUE,
    criado_em   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ---------------------------------------------------------------------
-- Tipos de turno — e aqui que o produto deixa de ser especifico
--
-- 24x72 ..... 1 registro:  08:00, 24h de duracao, 72h de descanso
-- 12x36 ..... 2 registros: 07:00/12h/36h e 19:00/12h/36h
-- 5x2 ....... 1 registro:  08:00, 8h, 16h de descanso
-- Sobreaviso  1 registro:  14:00, 18h, 0h, sem banco de horas
--
-- hora_inicio e o que permite mais de um turno por dia. Sem ela, 12x36
-- e impossivel de representar.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS tipo_turno (
    id                       INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nome                     VARCHAR(100) NOT NULL,
    hora_inicio              TIME         NOT NULL,
    duracao_horas            NUMERIC(5,2) NOT NULL,
    intervalo_descanso_horas NUMERIC(5,2) NOT NULL DEFAULT 0,
    min_agentes              INT          NOT NULL DEFAULT 2,
    max_agentes              INT,
    conta_banco_horas        BOOLEAN      NOT NULL DEFAULT TRUE,
    ativo                    BOOLEAN      NOT NULL DEFAULT TRUE,
    criado_em                TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_tt_duracao  CHECK (duracao_horas > 0),
    CONSTRAINT ck_tt_descanso CHECK (intervalo_descanso_horas >= 0),
    CONSTRAINT ck_tt_min      CHECK (min_agentes >= 1),
    CONSTRAINT ck_tt_max      CHECK (max_agentes IS NULL OR max_agentes >= min_agentes)
);

-- ---------------------------------------------------------------------
-- Turnos concretos no calendario
-- min/max por turno permitem excecao num dia especifico (feriado, operacao)
-- sem alterar o tipo de turno inteiro
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS escala_turno (
    id            INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tipo_turno_id INT       NOT NULL REFERENCES tipo_turno(id),
    inicio        TIMESTAMP NOT NULL,
    fim           TIMESTAMP NOT NULL,
    min_agentes   INT       NOT NULL DEFAULT 2,
    max_agentes   INT,
    observacao    VARCHAR(255),
    ativo         BOOLEAN   NOT NULL DEFAULT TRUE,
    criado_em     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_et_periodo CHECK (fim > inicio),
    CONSTRAINT uq_et_tipo_inicio UNIQUE (tipo_turno_id, inicio)
);

-- ---------------------------------------------------------------------
-- Motivos de cobertura — tabela, nao lista fixa no codigo.
-- Cada cliente cadastra os seus sem precisar de nova versao do sistema.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS motivo_cobertura (
    id              INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nome            VARCHAR(100) NOT NULL UNIQUE,
    gera_lancamento BOOLEAN      NOT NULL DEFAULT TRUE,
    ativo           BOOLEAN      NOT NULL DEFAULT TRUE
);

-- ---------------------------------------------------------------------
-- Alocacao de pessoas nos turnos
-- inicio/fim nulos = cumpre o turno inteiro (caso normal).
-- Preenchidos = turno parcial (meio plantao), sem tabela nova.
--
-- Os dois ON DELETE CASCADE sustentam o "Limpar mes" (issue #44):
--   escala_turno_id -> apaga as alocacoes junto com o turno;
--   cobertura_de    -> apaga a alocacao de cobertura junto com a titular.
--
-- O segundo so aparece quando a cobertura esta num turno FORA do mes que
-- esta sendo limpo: se as duas alocacoes caem no mesmo mes, elas ja somem
-- juntas pela cascata do turno. Sem cascade nesse caso, a limpeza aborta
-- com violacao de chave estrangeira. Coberturas so existem a partir do M5,
-- entao hoje isso e prevencao. O efeito colateral fica registrado: limpar
-- agosto tambem apaga uma cobertura de setembro que aponte para agosto.
-- Se o M5 preferir preservar o plantao e so desfazer o vinculo, a troca e
-- por ON DELETE SET NULL.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS escala_funcionario (
    id                  INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    escala_turno_id     INT NOT NULL REFERENCES escala_turno(id) ON DELETE CASCADE,
    funcionario_id      INT NOT NULL REFERENCES funcionario(id),
    inicio              TIMESTAMP,
    fim                 TIMESTAMP,
    cobertura_de        INT,
    motivo_cobertura_id INT REFERENCES motivo_cobertura(id),
    observacao          VARCHAR(500),
    lancou_banco_horas  BOOLEAN   NOT NULL DEFAULT FALSE,
    criado_em           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ef_turno_func UNIQUE (escala_turno_id, funcionario_id),
    CONSTRAINT ck_ef_periodo CHECK (inicio IS NULL OR fim IS NULL OR fim > inicio),
    -- Nomeada de proposito: uma FK anonima ganha nome gerado (CONSTRAINT_E5,
    -- CONSTRAINT_E5C...) e nao da para corrigi-la depois por DDL fixo.
    CONSTRAINT fk_ef_cobertura FOREIGN KEY (cobertura_de)
        REFERENCES escala_funcionario(id) ON DELETE CASCADE
);

-- ---------------------------------------------------------------------
-- Extrato do banco de horas
-- Saldo e derivado por soma. Uma coluna unica em funcionario nao sustenta
-- o "Ver extrato" da tela nem o estorno quando uma cobertura e excluida.
-- Guardado em minutos: turno de 8h30 quebra um campo inteiro de horas.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS lancamento_horas (
    id                    INT         GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    funcionario_id        INT         NOT NULL REFERENCES funcionario(id),
    escala_funcionario_id INT         REFERENCES escala_funcionario(id) ON DELETE CASCADE,
    data_referencia       DATE        NOT NULL,
    minutos               INT         NOT NULL,
    tipo                  VARCHAR(30) NOT NULL,
    descricao             VARCHAR(255),
    criado_em             TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_lanc_tipo CHECK (tipo IN ('credito_cobertura','debito_ausencia',
                                            'credito_extra','ajuste_manual'))
);

-- ---------------------------------------------------------------------
-- Excecoes autorizadas as regras da escala
--
-- Trilha de auditoria, nao cadastro: o gestor alocou alguem sabendo que a
-- regra de descanso nao fecha, e o sistema guarda quem autorizou, quando e
-- por que a regra foi violada. Por isso a linha e imutavel — o repositorio
-- nao tem UPDATE nem DELETE, e a interface bloqueia atualizar() em Java
-- (sem trigger, conforme a decisao de manter as regras no backend).
--
-- escala_funcionario_id usa ON DELETE SET NULL, nao CASCADE:
-- apagar a alocacao (ou limpar o mes, issue #44) nao pode apagar o registro
-- de que a excecao aconteceu. Sem o SET NULL, o "Limpar mes" abortaria por
-- violacao de chave estrangeira assim que existisse uma excecao — o mesmo
-- problema que a FK de cobertura ja teve. As colunas funcionario_id e
-- data_plantao ficam denormalizadas de proposito: sao o que mantem o registro
-- legivel depois que a escala do mes some.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS escala_excecao (
    id                    INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    escala_funcionario_id INT          REFERENCES escala_funcionario(id) ON DELETE SET NULL,
    funcionario_id        INT NOT NULL REFERENCES funcionario(id),
    data_plantao          DATE         NOT NULL,
    regra                 VARCHAR(50)  NOT NULL,
    descricao             VARCHAR(500) NOT NULL,
    autorizado_por        VARCHAR(150) NOT NULL,
    criado_em             TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ---------------------------------------------------------------------
-- Log de eventos de seguranca (OWASP A09)
--
-- Trilha de auditoria de acoes criticas: login (bem-sucedido E falho),
-- logout, criacao/desativacao de usuario, redefinicao de senha e exclusao
-- em massa na escala.
--
-- identificacao guarda a string de login tentada, nao um FK para usuario:
-- num login falho o usuario pode nem existir, e e justamente esse caso que
-- o OWASP manda registrar. O texto chega sanitizado de quebras de linha pela
-- camada de servico (CWE-117), para ninguem forjar uma linha de log.
--
-- detalhes NUNCA recebe senha nem hash (CWE-532): so o que a acao mudou,
-- em texto curto.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS log_seguranca (
    id             INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    data_hora      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    identificacao  VARCHAR(150) NOT NULL,
    acao           VARCHAR(100) NOT NULL,
    resultado      VARCHAR(100),
    detalhes       VARCHAR(500)
);

-- ---------------------------------------------------------------------
-- Indices
-- ---------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_ef_funcionario   ON escala_funcionario(funcionario_id);
CREATE INDEX IF NOT EXISTS idx_ef_turno         ON escala_funcionario(escala_turno_id);
CREATE INDEX IF NOT EXISTS idx_ef_cobertura     ON escala_funcionario(cobertura_de);
CREATE INDEX IF NOT EXISTS idx_turno_inicio     ON escala_turno(inicio);
CREATE INDEX IF NOT EXISTS idx_turno_fim        ON escala_turno(fim);
CREATE INDEX IF NOT EXISTS idx_lanc_func_data   ON lancamento_horas(funcionario_id, data_referencia);
CREATE INDEX IF NOT EXISTS idx_log_seg_data_hora ON log_seguranca(data_hora);
CREATE INDEX IF NOT EXISTS idx_exc_funcionario  ON escala_excecao(funcionario_id);
