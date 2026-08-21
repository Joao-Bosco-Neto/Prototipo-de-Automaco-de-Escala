# Protecao do banco local

O H2 usa `CIPHER=AES` e uma senha aleatoria para o arquivo do banco. As duas
partes da senha sao geradas na primeira inicializacao e ficam em
`%USERPROFILE%\.sistema-escala\banco.key`, fora do repositorio.

No Windows, o aplicativo remove a heranca de permissao e concede acesso ao
diretorio e ao arquivo somente ao usuario que executa o sistema. Em sistemas
POSIX, aplica permissoes equivalentes a `700` no diretorio e `600` no arquivo.

## Limite da protecao

A chave fica na mesma maquina que o banco. Portanto, alguem com controle local
da conta do gestor pode obter a chave e abrir o banco; a criptografia AES nao
impede esse cenario. A protecao efetiva recomendada ao cliente e habilitar
criptografia de disco do Windows com BitLocker, mantendo a conta do gestor
protegida e sem compartilhamento de credenciais.

Esse e o risco residual aceito para uma aplicacao desktop offline monousuario
e corresponde aos riscos CWE-922 e CWE-732.