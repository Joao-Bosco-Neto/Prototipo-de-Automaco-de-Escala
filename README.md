# Sistema de Escala

![Build](https://github.com/Joao-Bosco-Neto/Prototipo-de-Automaco-de-Escala/actions/workflows/build.yml/badge.svg)

Projeto integrador — sistema de gestao de escalas de turno (Java + JavaFX + H2).

## Estrutura de pastas

```
sistema-escala/
├── pom.xml                          # dependencias (JavaFX, H2, BCrypt) e build
│
├── src/
│   ├── main/
│   │   ├── java/br/edu/sistemaescala/
│   │   │   ├── Main.java                 # ponto de entrada da aplicacao
│   │   │   ├── backend/
│   │   │   │   ├── model/                # entidades (Funcionario, EscalaTurno...)
│   │   │   │   ├── dao/                  # acesso a dados / conexao / CRUD
│   │   │   │   └── service/              # regras de negocio (validacao de conflito)
│   │   │   └── frontend/
│   │   │       └── controller/           # controllers das telas (FXML)
│   │   │
│   │   └── resources/
│   │       ├── frontend/
│   │       │   ├── fxml/                 # telas (.fxml)
│   │       │   ├── css/                  # estilos
│   │       │   └── assets/               # icones, logo, imagens
│   │       └── banco/
│   │           └── schema.sql            # script de criacao do banco (H2)
│   │
│   └── test/
│       └── java/br/edu/sistemaescala/backend/   # testes unitarios
│
├── data/                              # legado; o banco novo fica no perfil do usuario
└── documentacao/                      # documentacao final do projeto
```

## Banco de dados

O banco usado e o **H2** em modo de compatibilidade PostgreSQL, embarcado no proprio
processo da aplicacao (nao precisa de Podman/Docker rodando à parte).

- Arquivo gerado em: `%USERPROFILE%/.sistema-escala/sistema_escala.mv.db`
- Senha e chave AES: `%USERPROFILE%/.sistema-escala/banco.key` (fora do repositorio)
- Script de criacao: `src/main/resources/banco/schema.sql`
- Conexao (`ConexaoBanco.java`): H2 em modo PostgreSQL com `CIPHER=AES`

Consulte [documentacao/seguranca/PROTECAO_BANCO.md](documentacao/seguranca/PROTECAO_BANCO.md)
e [documentacao/seguranca/MODELAGEM_AMEACAS.md](documentacao/seguranca/MODELAGEM_AMEACAS.md)
para a decisao de criptografia, as permissoes do arquivo e o risco residual.

## Como rodar

```bash
mvn clean javafx:run
```

Na primeira execucao, rode o script `schema.sql` contra o banco para criar as tabelas
(ou implemente a leitura automatica do script na inicializacao da aplicacao).

## Proximos passos

- Implementar entidades em `backend/model`
- Implementar DAOs em `backend/dao`
- Implementar validacao de conflito de horarios em `backend/service`
- Criar telas FXML em `frontend/fxml` seguindo a direcao visual (desktop nativo, 1366x768)

## Protótipo das telas

O protótipo visual (HTML standalone, abre em qualquer navegador sem servidor) está em
[`prototipo/index.html`](prototipo/index.html). Reflete o layout de referência para as
issues de UI — várias citam "conforme a tela X" apontando pra ele.
