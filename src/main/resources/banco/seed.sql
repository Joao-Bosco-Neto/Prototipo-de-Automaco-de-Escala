-- O seed de producao nao contem credenciais nem dados de exemplo.
-- Fixtures para testes ficam em src/test/resources/banco/seed-teste.sql.

-- Configuracao obrigatoria para o primeiro acesso. O nome e substituido pelo
-- gestor na tela inicial; nenhum usuario ou senha e criado aqui.
INSERT INTO configuracao (nome_organizacao, subtitulo)
SELECT 'Organizacao', 'Sistema de Escala'
WHERE NOT EXISTS (SELECT 1 FROM configuracao);

-- Motivos de cobertura mais comuns, para a tela ja abrir com opcoes no combo
-- em vez de "Nenhum motivo cadastrado.". A tabela continua editavel pelo
-- cliente (comentario da criacao da tabela); isto e so uma carga inicial,
-- cada INSERT condicional ao nome para nao duplicar em reinicializacoes.
INSERT INTO motivo_cobertura (nome)
SELECT 'Atestado médico'
WHERE NOT EXISTS (SELECT 1 FROM motivo_cobertura WHERE nome = 'Atestado médico');

INSERT INTO motivo_cobertura (nome)
SELECT 'Falta justificada'
WHERE NOT EXISTS (SELECT 1 FROM motivo_cobertura WHERE nome = 'Falta justificada');

INSERT INTO motivo_cobertura (nome)
SELECT 'Falta injustificada'
WHERE NOT EXISTS (SELECT 1 FROM motivo_cobertura WHERE nome = 'Falta injustificada');

INSERT INTO motivo_cobertura (nome)
SELECT 'Férias'
WHERE NOT EXISTS (SELECT 1 FROM motivo_cobertura WHERE nome = 'Férias');

INSERT INTO motivo_cobertura (nome)
SELECT 'Folga compensatória'
WHERE NOT EXISTS (SELECT 1 FROM motivo_cobertura WHERE nome = 'Folga compensatória');

INSERT INTO motivo_cobertura (nome)
SELECT 'Licença médica'
WHERE NOT EXISTS (SELECT 1 FROM motivo_cobertura WHERE nome = 'Licença médica');

INSERT INTO motivo_cobertura (nome)
SELECT 'Emergência familiar'
WHERE NOT EXISTS (SELECT 1 FROM motivo_cobertura WHERE nome = 'Emergência familiar');

INSERT INTO motivo_cobertura (nome)
SELECT 'Troca de plantão'
WHERE NOT EXISTS (SELECT 1 FROM motivo_cobertura WHERE nome = 'Troca de plantão');
