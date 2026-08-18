-- Dados iniciais. Idempotente: so insere se a tabela estiver vazia,
-- para nao duplicar quando a aplicacao reiniciar.

INSERT INTO configuracao (nome_organizacao, subtitulo)
SELECT 'Organizacao Exemplo', 'Sistema de Escala'
WHERE NOT EXISTS (SELECT 1 FROM configuracao);

INSERT INTO motivo_cobertura (nome)
SELECT 'Licenca medica'      WHERE NOT EXISTS (SELECT 1 FROM motivo_cobertura);
INSERT INTO motivo_cobertura (nome)
SELECT 'Curso / capacitacao' WHERE NOT EXISTS (SELECT 1 FROM motivo_cobertura WHERE nome='Curso / capacitacao');
INSERT INTO motivo_cobertura (nome)
SELECT 'Convocacao judicial' WHERE NOT EXISTS (SELECT 1 FROM motivo_cobertura WHERE nome='Convocacao judicial');
INSERT INTO motivo_cobertura (nome)
SELECT 'Motivo particular'   WHERE NOT EXISTS (SELECT 1 FROM motivo_cobertura WHERE nome='Motivo particular');

-- Regime padrao. Outros podem ser cadastrados pela tela de tipos de turno.
INSERT INTO tipo_turno (nome, hora_inicio, duracao_horas, intervalo_descanso_horas, min_agentes)
SELECT 'Plantao 24x72', TIME '08:00:00', 24, 72, 2
WHERE NOT EXISTS (SELECT 1 FROM tipo_turno);
