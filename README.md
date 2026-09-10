# Sistema de Escala

![Build](https://github.com/Joao-Bosco-Neto/Prototipo-de-Automaco-de-Escala/actions/workflows/build.yml/badge.svg)

Projeto integrador — sistema de gestao de escalas de turno (Java + JavaFX + H2).

## O que o sistema faz

O Sistema de Escala monta, valida e publica a escala mensal de plantao de uma equipe
operacional. Ele substitui a montagem manual em planilha: o gestor abre o calendario do
mes, aloca os agentes em cada turno, e o proprio sistema recusa a alocacao que quebra uma
regra de servico.

O que esta implementado hoje:

- **Cadastro de pessoas e de tipos de turno.** O regime de trabalho e dado (`tipo_turno`),
  nao codigo: 24x72, 12x36 e 5x2 saem da mesma estrutura, sem nova versao do programa.
- **Montagem da escala no calendario mensal**, com atribuicao por painel lateral e gerador
  automatico de rodizio que mantem continuidade de um mes para o seguinte.
- **Validacao das regras de servico**: conflito de horario, duplicidade na mesma alocacao,
  efetivo minimo por turno e intervalo de descanso obrigatorio entre plantoes.
- **Excecao autorizada.** O gestor pode romper uma regra, mas o sistema exige
  justificativa e grava quem autorizou, quando e sobre qual plantao, em trilha imutavel.
- **Coberturas** de plantao, com lancamento automatico no banco de horas: credito para
  quem cobre, debito para o ausente, no valor da duracao do turno.
- **Banco de horas em extrato**, com saldo derivado por soma dos lancamentos e apuracao
  mensal ou continua conforme a configuracao da organizacao.
- **Exportacao da escala em PDF**, com opcoes, pre-visualizacao e impressao.
- **Dashboard** com indicadores, plantoes da semana e painel de pendencias.
- **Trilha de auditoria de seguranca**: login (inclusive o falho), gestao de contas,
  autorizacao de excecao, exclusao de cobertura e limpeza de mes.

### Para quem

O operador e uma pessoa so: o gestor de escalas da unidade, na maquina dele. O cliente
desta primeira entrega e a Diretoria do GOTE (Policia Civil do Tocantins), onde a escala
mensal e montada a mao hoje.

A aplicacao e **desktop, offline e monousuaria**. Nao ha servidor, nao ha rede e nao ha
acesso pelo navegador. Os agentes escalados nao tem conta no sistema — eles recebem a
escala pelo PDF exportado.

Existem dois perfis de acesso, e eles nao sao hierarquia funcional: `gestor` opera o
sistema, e `admin` e o suporte tecnico (a propria equipe de desenvolvimento).

## Requisitos de sistema

| Item | Producao (maquina do gestor) | Desenvolvimento |
| --- | --- | --- |
| Sistema operacional | Windows 10 ou superior | Windows ou Linux |
| Java | **Nao precisa instalar** — o pacote leva a JRE 21 embutida | JDK 21 |
| Maven | Nao se aplica | Maven 3.9 ou superior |
| Banco de dados | Nenhum a instalar — H2 embarcado no proprio processo | idem |
| Rede | Nenhuma; o sistema opera offline | Apenas para baixar dependencias |
| Privilegio de administrador | Apenas para rodar o instalador `.msi` | Nao exigido |

O sistema e compilado com `maven.compiler.release=21` e usa JavaFX 21.0.2. A equipe
desenvolve em Linux e Windows; o alvo de producao e Windows, e o empacotamento com
`jpackage` (secao **Empacotamento e Distribuicao**) so roda em Windows.

Todo o estado mutavel — banco, chave de criptografia e logs — fica em
`%USERPROFILE%\.sistema-escala`, nunca no diretorio de instalacao.

## Documentacao

| Documento | Conteudo |
| --- | --- |
| [documentacao/ARQUITETURA.md](documentacao/ARQUITETURA.md) | Camadas, MER das 10 tabelas, decisoes tecnicas e rastreabilidade RF/RNF |
| [documentacao/GUIA_DE_CONTRIBUICAO.md](documentacao/GUIA_DE_CONTRIBUICAO.md) | Fluxo de branches, commits e pull requests |
| [documentacao/seguranca/MODELAGEM_AMEACAS.md](documentacao/seguranca/MODELAGEM_AMEACAS.md) | Ameacas, mitigacoes e riscos residuais aceitos |
| [documentacao/seguranca/LGPD_E_DADOS_PESSOAIS.md](documentacao/seguranca/LGPD_E_DADOS_PESSOAIS.md) | Classificacao dos dados pessoais, retencao e orientacao ao cliente |
| [documentacao/seguranca/PROTECAO_BANCO.md](documentacao/seguranca/PROTECAO_BANCO.md) | Criptografia do arquivo do banco e permissoes do diretorio |
| [docs/CONTEXTO_PARA_IA.md](docs/CONTEXTO_PARA_IA.md) | Contexto do projeto para colar em conversa com assistente de IA |

### Manual do usuario

**O manual ainda nao existe.** Ele sera escrito na
[issue #59](https://github.com/Joao-Bosco-Neto/Prototipo-de-Automaco-de-Escala/issues/59)
e entregue como PDF com capturas de tela, em `documentacao/MANUAL_DO_USUARIO.pdf`,
acompanhando o instalador.

O manual cobrira os fluxos de ponta a ponta — primeiro acesso, cadastros, montagem da
escala, rodizio, coberturas, banco de horas e exportacao do PDF — mais uma secao de
problemas comuns. A orientacao de seguranca para a organizacao (secao 4 de
`LGPD_E_DADOS_PESSOAIS.md`) e o procedimento de conferencia do hash do instalador tambem
serao incorporados a ele.

Ate que o manual exista, este README e a referencia de instalacao, e
[`prototipo/index.html`](prototipo/index.html) e a referencia visual das telas.

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
│   │   │   │   ├── dao/                  # conexao, inicializacao e transacao
│   │   │   │   ├── repository/           # interfaces de acesso a dados
│   │   │   │   │   └── jdbc/             # implementacoes JDBC das interfaces
│   │   │   │   └── service/              # regras de negocio (descanso, efetivo, rodizio)
│   │   │   └── frontend/
│   │   │       ├── controller/           # telas em Java puro, sem FXML
│   │   │       └── VitrineComponentesApp.java  # vitrine visual do tema
│   │   │
│   │   └── resources/
│   │       ├── frontend/
│   │       │   ├── css/                  # tema visual (app.css)
│   │       │   ├── assets/               # icones e logo
│   │       │   └── fxml/                 # vazio: as telas sao construidas em Java
│   │       └── banco/
│   │           ├── schema.sql            # criacao das 10 tabelas (idempotente)
│   │           └── seed.sql              # dados iniciais (idempotente)
│   │
│   └── test/
│       └── java/br/edu/sistemaescala/backend/   # testes de service e de repositorio
│
├── prototipo/index.html               # protótipo visual das telas (referencia de UI)
├── data/                              # legado; o banco novo fica no perfil do usuario
├── docs/                              # notas de apoio ao desenvolvimento
└── documentacao/                      # documentacao final do projeto
```

As telas sao construidas em **Java puro**, sem FXML — a pasta `resources/frontend/fxml/`
sobrou da estrutura inicial e esta vazia. A separacao entre `repository/` (interface) e
`repository/jdbc/` (implementacao) e o que permite testar servico sem banco, e a regra que
mantem a logica fora do controller esta em
[documentacao/ARQUITETURA.md](documentacao/ARQUITETURA.md).

## Banco de dados

O banco usado e o **H2** em modo de compatibilidade PostgreSQL, embarcado no proprio
processo da aplicacao (nao precisa de Podman/Docker rodando à parte).

- Arquivo gerado em: `%USERPROFILE%/.sistema-escala/sistema_escala.mv.db`
- Senha e chave AES: `%USERPROFILE%/.sistema-escala/banco.key` (fora do repositorio)
- Script de criacao: `src/main/resources/banco/schema.sql`
- Conexao (`ConexaoBanco.java`): H2 em modo PostgreSQL com `CIPHER=AES`

Sao **10 tabelas**: `configuracao`, `usuario`, `funcionario`, `tipo_turno`, `escala_turno`,
`motivo_cobertura`, `escala_funcionario`, `lancamento_horas`, `escala_excecao` e
`log_seguranca`. O modelo esta descrito tabela a tabela, com os relacionamentos e as
decisoes que os motivaram, em
[documentacao/ARQUITETURA.md](documentacao/ARQUITETURA.md#2-mer-final) — o
`documentacao/diagrama_mer.png` esta desatualizado e o proprio documento explica em que.

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
`target/dist/Sistema de Escala-1.0.0.msi` (acompanhado do arquivo de checksum `Sistema de Escala-1.0.0.msi.sha256`).

### 3. Verificação de Integridade do Instalador (SHA-256 / OWASP A08)

Em conformidade com o **OWASP A08 (Software and Data Integrity Failures)**, para garantir que o pacote `.msi` ou executável não foi corrompido ou adulterado durante a transferência entre os desenvolvedores e a delegacia:

1. Abra o **PowerShell** no diretório onde o arquivo `.msi` foi baixado e execute:
   ```powershell
   Get-FileHash -Algorithm SHA256 ".\Sistema de Escala-1.0.0.msi"
   ```
   *(No Prompt de Comando clássico / CMD, use: `CertUtil -hashfile "Sistema de Escala-1.0.0.msi" SHA256`)*

2. Compare o hash retornado com o valor publicado no arquivo `Sistema de Escala-1.0.0.msi.sha256` ou nas notas de release da entrega.
3. Se os hashes forem idênticos, a integridade do pacote está confirmada e a instalação pode ser realizada com segurança.

Para detalhes sobre a decisão de mitigação por SHA-256 em vez de certificado Authenticode comercial e sobre a ausência de serialização Java (CWE-502), consulte [documentacao/seguranca/MODELAGEM_AMEACAS.md](documentacao/seguranca/MODELAGEM_AMEACAS.md).

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

