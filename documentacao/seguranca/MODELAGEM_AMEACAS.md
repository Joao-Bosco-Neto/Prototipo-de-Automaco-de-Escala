# Modelagem de ameaças: banco local

## Ativo

O arquivo H2 contém usuários, perfis, funcionários, escalas e lançamentos de
horas. Uma cópia do arquivo pode permitir contornar o login e alterar o
histórico da aplicação.

## Ameaças

| Ameaça | Referência | Tratamento |
| --- | --- | --- |
| Leitura ou alteração direta do arquivo do banco | CWE-922 | H2 com `CIPHER=AES`, senha aleatória fora do repositório e diretório privado |
| Permissão excessiva no arquivo local | CWE-732 | ACL exclusiva do usuário no Windows; permissões `700/600` em POSIX |
| Cópia feita por alguém com controle da máquina | Residual | Recomendar BitLocker e conta do Windows protegida ao cliente |

## Decisão AES

O H2 foi configurado com `CIPHER=AES`. A senha do arquivo e a senha do usuário
do banco são geradas aleatoriamente na primeira execução e armazenadas em
`%USERPROFILE%\.sistema-escala\banco.key`, que não faz parte do repositório.

Essa decisão protege o arquivo contra leitura casual e contra ferramentas que
tentem abri-lo sem a senha. Ela não é uma fronteira de segurança contra o
próprio usuário do Windows: em uma aplicação desktop monousuário, a chave
precisa estar na mesma máquina para o programa funcionar.

## Risco residual aceito

Um invasor com acesso à conta do gestor pode ler o arquivo de chave e o banco.
Por isso, a proteção efetiva recomendada para o ambiente do cliente é a
criptografia de disco do Windows com BitLocker, além de não compartilhar a
conta nem as credenciais do gestor.