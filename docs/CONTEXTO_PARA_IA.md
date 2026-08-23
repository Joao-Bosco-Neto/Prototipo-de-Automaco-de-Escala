# Contexto do Projeto — cole isto antes de pedir ajuda a uma IA

Copie tudo daqui para baixo e cole no início da conversa com qualquer assistente de IA
(Claude Code, ChatGPT, Copilot). Depois descreva a tarefa que você quer fazer.

---

## Sobre o projeto

Sistema desktop de gestão de escalas de plantão. Projeto integrador acadêmico do SENAC que
também será entregue e usado de verdade por um cliente real — a Diretoria do GOTE (Polícia
Civil do Tocantins), onde o gestor de escalas monta a escala mensal à mão hoje.

**Apresentação em 11/09/2026. Entrega interna em 04/09 (uma semana antes, para análise).**

Equipe de três pessoas: João Bosco (@Joao-Bosco-Neto), Samuel (@Krisbrn) e
Davi (@DaviRSuassuna).

Repositório: `https://github.com/Joao-Bosco-Neto/Prototipo-de-Automaco-de-Escala`

## Stack e restrições

- **Java 21** (`maven.compiler.release`), **JavaFX 21.0.2**, Maven
- **H2 embarcado** em modo de compatibilidade PostgreSQL, arquivo em `./data/sistema_escala.mv.db`
- **at.favre.lib:bcrypt** para hash de senha, custo 12
- **JUnit 5** para testes
- Aplicação **offline, monousuário**, instalada na máquina do gestor. Sem servidor, sem
  rede, sem navegador.
- Alvo de produção: **Windows 10 ou superior** (o cliente vai atualizar as máquinas).
  A equipe desenvolve em Linux (Zorin) e Windows.

**Não sugira:** Spring, Hibernate/JPA, Flyway, PostgreSQL, Docker em produção, APIs REST,
frontend web. Tudo isso foi avaliado e descartado por decisão registrada.

## Arquitetura

```
src/main/java/br/edu/sistemaescala/
├── Main.java                      # ponto de entrada, chama BancoInicializador
├── backend/
│   ├── dao/                       # ConexaoBanco, BancoInicializador
│   ├── model/                     # classes de domínio
│   ├── repository/                # interfaces + implementações JDBC
│   └── service/                   # regras de negócio
└── frontend/
    ├── controller/                # controllers JavaFX
    └── VitrineComponentesApp.java # referência visual dos componentes do tema

src/main/resources/
├── banco/schema.sql               # criação das tabelas (idempotente)
├── banco/seed.sql                 # dados iniciais (idempotente)
└── frontend/css/app.css           # tema visual — ver seção própria abaixo
```

**Regra de ouro:** nenhuma regra de negócio dentro de controller de tela. Se está em
`frontend/controller/`, não dá para testar sem abrir a janela — e justamente o que precisa
de teste aqui (descanso, mínimo de agentes, rodízio) tem que ficar em `backend/service/`.

As telas são construídas em **Java puro**, sem FXML — veja `PrimeiroAcessoController` como
referência de padrão.

## Tema visual (CSS)

`src/main/resources/frontend/css/app.css` tem a paleta oficial, extraída do
`prototipo/index.html` — não é arbitrária. **Nunca use `setStyle()` com hex direto**,
sempre `getStyleClass().add("nome-da-classe")`.

Classes disponíveis: `button-primario`, `button-secundario`, `button-perigo`, `titulo-1`,
`titulo-2`, `card`, `selo-sucesso`, `selo-atencao`, `selo-perigo`, `selo-neutro`. Estilo
automático em `TextField`, `PasswordField`, `TableView`.

Variáveis de cor via mecanismo próprio do JavaFX (`-cor-primaria`, etc.) — **não** é a
sintaxe `--variavel` de CSS de navegador, JavaFX usa um traço só.

Para ver todos os componentes de uma vez:
```
mvn exec:java -Dexec.mainClass="br.edu.sistemaescala.frontend.VitrineComponentesApp"
```

## Banco de dados

8 tabelas: `configuracao`, `usuario`, `funcionario`, `tipo_turno`, `escala_turno`,
`motivo_cobertura`, `escala_funcionario`, `lancamento_horas`.

O `BancoInicializador` executa `schema.sql` e `seed.sql` do classpath na partida. Os dois
são idempotentes (`CREATE TABLE IF NOT EXISTS`, INSERT condicional).

**Regras de SQL que não podem ser quebradas** — o schema precisa rodar em H2 e PostgreSQL:

| Não usar | Usar |
|----------|------|
| `CREATE TYPE ... AS ENUM` | `VARCHAR` + `CHECK (col IN (...))` |
| `SERIAL` | `INT GENERATED ALWAYS AS IDENTITY` |
| `NOW()` | `CURRENT_TIMESTAMP` |
| `TEXT` | `VARCHAR(n)` |

Sempre `PreparedStatement`. Nunca concatenar entrada do usuário em SQL.

### Conceitos centrais

**`tipo_turno`** é o que torna o produto genérico. Guarda `hora_inicio`, `duracao_horas`,
`intervalo_descanso_horas`, `min_agentes` e `max_agentes`. Múltiplos registros descrevem
múltiplos turnos por dia:

- 24x72 → um registro: `08:00`, 24h de duração, 72h de descanso
- 12x36 → dois registros: `07:00`/12h/36h e `19:00`/12h/36h
- 5x2 → um registro: `08:00`, 8h, 16h de descanso

**Nunca fixe 72, 24 ou 2 no código.** Esses valores vêm sempre do `tipo_turno` ou da
`configuracao`.

**`escala_funcionario.inicio` e `fim`** são opcionais. Nulos = a pessoa cumpre o turno
inteiro (caso normal). Preenchidos = turno parcial (meio plantão). Por causa disso, a
verificação de conflito é **sobreposição de intervalos**, que funciona para qualquer regime:

```java
boolean haConflito = a.inicio().isBefore(b.fim()) && b.inicio().isBefore(a.fim());
```

**`lancamento_horas`** é o extrato do banco de horas, guardado em **minutos** (turno de
8h30 quebraria um campo de horas). O saldo é derivado por soma, nunca guardado como coluna.

**Ninguém é excluído do banco.** `funcionario.ativo` e `usuario.ativo` fazem desativação
lógica, para preservar o histórico de plantões.

## Regras de negócio

- Um plantão exige **mínimo de 2 agentes** por padrão, com máximo configurável.
- Após um plantão, o agente cumpre o **intervalo de descanso** definido no tipo de turno
  (72h no regime 24x72). O descanso conta do `fim` de um plantão ao `inicio` do próximo, e
  precisa ser verificado nos dois sentidos: escalar alguém no dia 10 pode ser inválido por
  causa do dia 12, não só do dia 8.
- Uma escala **incompleta pode ser salva**, com aviso, mas **bloqueia a exportação do PDF**.
- O **gestor pode romper uma regra**, desde que registre justificativa. O perfil `admin` é
  suporte técnico (a própria equipe de desenvolvimento).
- **Banco de horas apura mensalmente**, com o acumulado disponível para consulta.
- **Cobertura** é quando alguém assume o plantão de outro. Gera crédito para quem cobre e
  débito para o ausente, no valor da duração do turno.
- O **gerador de rodízio** precisa manter continuidade entre meses: o rodízio de setembro
  começa de onde agosto parou, senão a virada do mês quebra o descanso.
- **Primeiro acesso:** a tabela `configuracao` NUNCA está vazia (o `seed.sql` já grava uma
  linha padrão). Quem detecta "primeiro acesso" é a tabela `usuario` vazia, não a
  `configuracao`. O fluxo é buscar a linha existente e atualizar, nunca inserir uma nova
  (`ConfiguracaoRepository` não tem método de inserção).

## Fora do escopo do protótipo

Meio plantão (banco já preparado), escala de sobreaviso, acesso dos agentes para
visualizar escala, pedido de troca de dia, operação em rede ou multiusuário, assinatura
digital com certificado.

## Fluxo de trabalho

- Branches saem de **`develop`**, no formato `<numero-da-issue>-<descricao>`
- Commits em **Conventional Commits**: `tipo(escopo): descricao`
  Tipos: `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`
- Título curto (até 50 caracteres), corpo explicando o **porquê**
- PR para `develop`, com revisão de outro integrante, merge por **Squash and Merge**
- **Abra o PR no mesmo dia do commit.** Branches esquecidas sem PR por dias já causaram
  conflito real neste projeto mais de uma vez — a `develop` anda rápido.
- O CI roda `mvn clean verify` em todo push e PR

**Atenção à numeração das issues:** houve uma queda do GitHub durante a criação do
backlog, então os números do GitHub **não batem** com os do arquivo
`scripts/BACKLOG_ISSUES.md`. Cada issue tem no rodapé a linha `_Backlog #N_` — é por ela
que se localiza a issue no arquivo. Ao referenciar uma issue em commit, use o número do
GitHub.

## Comandos

```bash
mvn clean verify          # compila e testa (rápido, ~3-8s)
mvn clean javafx:run      # roda a aplicação
mvn -P seguranca verify   # análise de vulnerabilidades (LENTO, exige NVD_API_KEY)
```

O último só roda no CI, semanalmente. Não rode localmente sem necessidade — sem a chave do
NVD ele fica horas baixando a base de vulnerabilidades.

## Estado atual

**Concluído:** estrutura Maven, banco criando-se sozinho na partida, CI verde, Dependabot,
análise de dependências (0 vulnerabilidades na última varredura), decisão do hash de senha
(BCrypt via `at.favre.lib`), autenticação completa (login + primeiro acesso), todos os
repositórios/DAOs (`Usuario`, `Configuracao`, `Funcionario`, `TipoTurno`, `EscalaTurno`,
`EscalaFuncionario`, `MotivoCobertura`, `LancamentoHoras`), tema CSS base com vitrine de
componentes.

**Em aberto e prioritário:**
- **Shell da aplicação** (janela com menu, navegação lateral e área de conteúdo) — ainda
  não existe nenhuma classe de navegação. Trava a implementação de toda tela real do
  sistema — sem ele, nada além da tela de primeiro acesso pode ser testado de verdade.
- **Regras de negócio de escala** (descanso obrigatório, mínimo de agentes, conflito de
  horário) — os repositórios já têm os métodos necessários (ex.:
  `EscalaFuncionarioRepository.listarPorFuncionario` foi desenhado pensando nessa regra),
  falta o `RegraEscalaService`.
- **Calendário de montagem da escala** — a parte mais difícil do projeto.

`Main.java` hoje decide entre a tela de primeiro acesso e uma tela provisória de "sistema
pronto", dependendo se já existe usuário cadastrado. Essa tela provisória ainda usa
`setStyle()` inline em vez das classes do tema — será substituída pelo shell, então não é
prioridade corrigir isso agora, mas não use esse trecho como referência de padrão de
código.

## Como responder

- Escreva comentários e nomes de variáveis **em português**
- Não invente nomes de tabela ou coluna: peça o `schema.sql` se precisar conferir
- Se a tarefa exigir mudar o schema, **avise antes** — schema é decisão de equipe
- Não abra pull request nem use `Closes #N` em commits sem pedir; use `Refs #N`
- Prefira a solução mais simples que atenda: o prazo é curto e a equipe está aprendendo
  JavaFX
- Antes de criar uma tela nova, leia `app.css` e use as classes existentes — não invente
  estilo com hex hardcoded
