-- Dados exclusivos dos testes. Este arquivo nunca e empacotado na aplicacao.
INSERT INTO configuracao (nome_organizacao, subtitulo)
VALUES ('Organizacao de teste', 'Sistema de testes');

INSERT INTO motivo_cobertura (nome)
VALUES ('Motivo de teste');

INSERT INTO tipo_turno (nome, hora_inicio, duracao_horas, intervalo_descanso_horas, min_agentes)
VALUES ('Turno de teste', TIME '08:00:00', 8, 16, 1);