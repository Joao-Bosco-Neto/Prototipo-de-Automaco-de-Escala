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
├── data/                              # arquivo do banco H2 (gerado em runtime, nao versionar)
└── documentacao/                      # documentacao final do projeto
```

## Banco de dados

O banco usado e o **H2** em modo de compatibilidade PostgreSQL, embarcado no proprio
processo da aplicacao (nao precisa de Podman/Docker rodando à parte).

- Arquivo gerado em: `./data/sistema_escala.mv.db`
- Script de criacao: `src/main/resources/banco/schema.sql`
- Conexao (`ConexaoBanco.java`): `jdbc:h2:file:./data/sistema_escala;MODE=PostgreSQL`

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
