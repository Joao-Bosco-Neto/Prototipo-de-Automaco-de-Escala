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

## Como rodar em desenvolvimento

```bash
mvn clean javafx:run
```

Na primeira execucao, o aplicativo cria as tabelas automaticamente. O seed de producao
nao inclui dados de exemplo nem credenciais; fixtures ficam apenas nos recursos de teste.

## Empacotamento e Distribuição (Windows)

A aplicação é distribuída como um pacote nativo autocontido com **JRE 21 embutida** via `jpackage`. O cliente final **não precisa ter Java instalado**.

### 1. Pacote Executável Standalone (Pasta Portátil)

Gera a pasta da aplicação contendo o `Sistema de Escala.exe` e uma runtime customizada embutida:

```bash
mvn clean package -Pempacotar-windows -DskipTests
```

O executável portátil é gerado em:
`target/dist/Sistema de Escala/` (com `Sistema de Escala.exe` e a pasta `runtime/`).

### 2. Instalador Windows (.msi)

Gera o assistente de instalação `.msi` para Windows com atalhos no Menu Iniciar e na Área de Trabalho (requer [WiX Toolset v3.11+](https://wixtoolset.org/) no PATH):

```bash
mvn clean package -Pinstalador-msi -DskipTests
```

O instalador é gerado em:
`target/dist/Sistema de Escala-1.0.0.msi`.

### Diretório de Dados e Permissões

Mesmo instalado em diretórios protegidos do sistema (como `C:\Program Files\Sistema de Escala`), todo o estado mutável do sistema permanece no perfil privado do usuário:
- **Banco de dados (AES)**: `%USERPROFILE%\.sistema-escala\sistema_escala.mv.db`
- **Chave de criptografia**: `%USERPROFILE%\.sistema-escala\banco.key`
- **Logs da aplicação**: `%USERPROFILE%\.sistema-escala\logs\aplicacao.log`

## Protótipo das telas

O protótipo visual (HTML standalone, abre em qualquer navegador sem servidor) está em
[`prototipo/index.html`](prototipo/index.html). Reflete o layout de referência para as
issues de UI — várias citam "conforme a tela X" apontando pra ele.

## Tema visual (CSS)

Toda tela deve usar as classes de `src/main/resources/frontend/css/app.css`,
nunca `setStyle()` com hex direto. Classes disponíveis: `button-primario`,
`button-secundario`, `button-perigo`, `titulo-1`, `titulo-2`, `card`,
`selo-sucesso`, `selo-atencao`, `selo-perigo`, `selo-neutro`, além de estilos
automáticos para `TextField`, `PasswordField` e `TableView`.

Para ver todos os componentes de uma vez, rode:

    mvn exec:java -Dexec.mainClass="br.edu.sistemaescala.frontend.VitrineComponentesApp"

