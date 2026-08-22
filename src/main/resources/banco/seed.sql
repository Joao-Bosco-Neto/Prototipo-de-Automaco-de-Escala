-- O seed de producao nao contem credenciais nem dados de exemplo.
-- Fixtures para testes ficam em src/test/resources/banco/seed-teste.sql.

-- Configuracao obrigatoria para o primeiro acesso. O nome e substituido pelo
-- gestor na tela inicial; nenhum usuario ou senha e criado aqui.
INSERT INTO configuracao (nome_organizacao, subtitulo)
SELECT 'Organizacao', 'Sistema de Escala'
WHERE NOT EXISTS (SELECT 1 FROM configuracao);
