# GEMINI.md - Diretrizes do Projeto Sistema de Escala

Este arquivo contém as diretrizes de arquitetura, regras de negócio, stack tecnológica e padrões de código para o desenvolvimento do **Sistema de Escala**.

---

## 1. Sobre o Projeto

- **Objetivo**: Sistema desktop de gestão de escalas de plantão. Projeto integrador acadêmico do SENAC que será entregue e utilizado em produção por um cliente real — a **Diretoria do GOTE (Polícia Civil do Tocantins)**, onde o gestor de escalas monta a escala mensal manualmente.
- **Prazos**:
  - Entrega interna: **04/09/2026** (uma semana antes, para análise).
  - Apresentação final: **11/09/2026**.
- **Equipe**: João Bosco (@Joao-Bosco-Neto), Samuel (@Krisbrn) e Davi (@DaviRSuassuna).
- **Repositório**: `https://github.com/Joao-Bosco-Neto/Prototipo-de-Automaco-de-Escala`

---

## 2. Stack Tecnológica e Restrições

- **Linguagem & Plataforma**: Java 21 (`maven.compiler.release`), JavaFX 21.0.2, Maven.
- **Banco de Dados**: H2 embarcado em modo de compatibilidade PostgreSQL (`CIPHER=AES`), arquivo localizado no perfil do usuário (`%USERPROFILE%/.sistema-escala/sistema_escala.mv.db`).
- **Segurança / Criptografia**: `at.favre.lib:bcrypt` (custo 12) para hash de senhas.
- **Testes**: JUnit 5 para testes unitários.
- **Ambiente de Execução**: Aplicação **offline, monousuário**, instalada na máquina do gestor. Sem servidor, sem rede, sem navegador.
- **Alvo de Produção**: Windows 10 ou superior (desenvolvimento em Linux Zorin e Windows).

> [!CAUTION]
> **O que NÃO sugerir ou usar:**
> Spring, Hibernate/JPA, Flyway, PostgreSQL standalone/server, Docker em produção, APIs REST, frontend web.
> Todas essas opções foram avaliadas e descartadas por decisão de arquitetura registrada.

---

## 3. Arquitetura e Estrutura de Pastas

```
src/main/java/br/edu/sistemaescala/
├── Main.java                      # Ponto de entrada, inicializa banco e shell
├── backend/
│   ├── dao/                       # ConexaoBanco, BancoInicializador, repositórios/DAOs
│   ├── model/                     # Classes e registros de domínio (Funcionario, etc.)
│   └── service/                   # Regras de negócio e validações
└── frontend/
    └── controller/                # Controllers JavaFX (apenas apresentação)

src/main/resources/
├── banco/
│   ├── schema.sql                 # Criação das tabelas (idempotente)
│   └── seed.sql                   # Dados iniciais / fixtures de teste
└── frontend/
    ├── fxml/                      # Telas FXML
    ├── css/app.css                # Folha de estilos padronizada
    └── assets/                    # Ícones, logo e imagens
```

### Regras Arquiteturais Críticas
1. **Regra de Ouro**: Nenhuma regra de negócio deve residir em controllers de tela (`frontend/controller/`).
2. Toda a lógica de verificação (descanso, rodízio, mínimo de agentes, conflitos de horário, apuração de horas) deve obrigatoriamente estar em `backend/service/` para ser testável via JUnit sem inicializar a interface gráfica.
3. Não crie acoplamento indevido ou dependências circulares entre camadas (`Controller` → `Service` → `DAO` → `Model`).

---

## 4. Banco de Dados e Regras de SQL

O banco é composto por 8 tabelas centrais:
`configuracao`, `usuario`, `funcionario`, `tipo_turno`, `escala_turno`, `motivo_cobertura`, `escala_funcionario`, `lancamento_horas`.

O `BancoInicializador` executa `schema.sql` e `seed.sql` do classpath na inicialização. Ambos são idempotentes (`CREATE TABLE IF NOT EXISTS`, INSERT condicional).

### Compatibilidade H2 & PostgreSQL (Mandatório)
| Não usar | Usar |
|---|---|
| `CREATE TYPE ... AS ENUM` | `VARCHAR` + `CHECK (col IN (...))` |
| `SERIAL` | `INT GENERATED ALWAYS AS IDENTITY` |
| `NOW()` | `CURRENT_TIMESTAMP` |
| `TEXT` | `VARCHAR(n)` |

- **Segurança**: Utilize SEMPRE `PreparedStatement`. Nunca concatene entrada de usuário em queries SQL.
- **Alterações no Schema**: Se a tarefa exigir alteração de schema, **avise antes** — schema é decisão de equipe. Nomes de tabelas e colunas não devem ser inventados sem conferir `schema.sql`.

### Conceitos Centrais de Dados
- **`tipo_turno` (Parametrização Genérica)**: Armazena `hora_inicio`, `duracao_horas`, `intervalo_descanso_horas`, `min_agentes` e `max_agentes`.
  - Exemplo 24x72: 1 registro (`08:00`, 24h duração, 72h descanso).
  - Exemplo 12x36: 2 registros (`07:00`/12h/36h e `19:00`/12h/36h).
  - Exemplo 5x2: 1 registro (`08:00`, 8h duração, 16h descanso).
  - *Nunca fixe números mágicos (ex.: 72, 24, 2) no código Java. Obtenha-os de `tipo_turno` ou `configuracao`.*
- **Turnos Parciais (`escala_funcionario.inicio` e `fim`)**:
  - `null`: Turno integral (caso comum).
  - Preenchido: Turno parcial / meio plantão.
  - Verificação de conflito baseada em sobreposição de intervalos:
    ```java
    boolean haConflito = a.inicio().isBefore(b.fim()) && b.inicio().isBefore(a.fim());
    ```
- **Banco de Horas (`lancamento_horas`)**: Armazenado em **minutos** (para suportar frações como turnos de 8h30). O saldo atual é sempre derivado por soma/agregação, nunca salvo como coluna estática.
- **Exclusão Lógica**: Ninguém é deletado fisicamente do banco (`DELETE`). Utiliza-se `funcionario.ativo = false` e `usuario.ativo = false` para manter a integridade do histórico de escalas.

---

## 5. Regras de Negócio do Domínio

- **Mínimo de Efetivo**: Todo plantão exige no mínimo 2 agentes por padrão (configurável via `tipo_turno` / `configuracao`).
- **Intervalo de Descanso Obrigatório**: Após um plantão, o agente cumpre o intervalo de descanso do seu tipo de turno (ex.: 72h). O descanso conta de `fim` do plantão até `inicio` do próximo, e a validação é **bidirecional** (verificar antes e depois da data ao alocar).
- **Validação de Escala e PDF**: Uma escala incompleta ou com pendências pode ser salva como rascunho com aviso ao usuário, mas **bloqueia a exportação em PDF**.
- **Quebra de Regra com Justificativa**: O gestor tem autonomia para romper uma regra de negócio (ex.: descanso insuficiente por emergência operacional), desde que registre obrigatoriamente uma **justificativa**. Perfil `admin` é exclusivo para suporte técnico/equipe de desenvolvimento.
- **Apuração de Banco de Horas**: Feita mensalmente, mantendo histórico acumulado para consulta.
- **Cobertura de Plantão**: Ocorre quando um agente substitui outro. Gera crédito no banco de horas para quem cobre e débito para o ausente, com base na duração do turno.
- **Continuidade do Rodízio**: O gerador de rodízio automático deve manter continuidade entre meses consecutivos (o rodízio de setembro inicia a partir do ponto onde agosto encerrou, prevenindo violações de descanso na virada do mês).

---

## 6. Padrões de Interface (JavaFX & CSS)

- **Protótipo de Referência**: `prototipo/index.html` é o protótipo visual HTML standalone oficial. Consulte sempre antes de criar qualquer layout FXML/UI ("conforme a tela X").
- **Folha de Estilos Obrigatória**: Todas as telas devem usar as classes definidas em `src/main/resources/frontend/css/app.css` via `getStyleClass().add(...)`.
  - Classes disponíveis: `button-primario`, `button-secundario`, `button-perigo`, `titulo-1`, `titulo-2`, `card`, `selo-sucesso`, `selo-atencao`, `selo-perigo`, `selo-neutro`, etc.
  - **NUNCA** use `setStyle()` com cores hexadecimais inline hardcoded.
- **Vitrine de Componentes**: Para visualizar todos os componentes prontos e estilizados:
  ```bash
  mvn exec:java -Dexec.mainClass="br.edu.sistemaescala.frontend.VitrineComponentesApp"
  ```
- **Resolução Alvo**: Desktop nativo (1366x768).

---

## 7. Fora de Escopo do Protótipo

Os seguintes itens **NÃO** fazem parte do escopo deste protótipo:
- Meio plantão na interface (embora o banco já esteja preparado).
- Escala de sobreaviso.
- Portal/aplicativo web para agentes visualizarem escalas.
- Solicitação de troca de plantão pelo próprio agente via app.
- Operação em rede ou suporte multiusuário simultâneo.
- Assinatura digital via certificado ICP-Brasil.

---

## 8. Fluxo de Trabalho e Git

- **Branches**: Criadas a partir de `develop`, seguindo o padrão `<numero-da-issue>-<descricao>`.
- **Convenção de Commits**: Conventional Commits (`tipo(escopo): descricao`).
  - Tipos: `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`.
  - Título conciso (até 50 caracteres) e corpo explicando a motivação.
- **Pull Requests**: Direcionados para `develop`, exigindo revisão de pares e **Squash and Merge**. O CI roda `mvn clean verify`.
- **Numeração de Issues**:
  - Ao referenciar issues em commits ou PRs, utilize o número do GitHub.
  - Para mapear com o arquivo `scripts/BACKLOG_ISSUES.md`, utilize o identificador de rodapé `_Backlog #N_`.
  - Use `Refs #N` (evite `Closes #N` sem aprovação prévia).

---

## 9. Comandos Úteis

```bash
# Compilar e rodar testes unitários (~3s)
mvn clean verify

# Executar a aplicação JavaFX
mvn clean javafx:run

# Executar vitrine de componentes UI
mvn exec:java -Dexec.mainClass="br.edu.sistemaescala.frontend.VitrineComponentesApp"

# Análise de segurança/vulnerabilidades (executado no CI semanal; não rodar localmente sem NVD_API_KEY)
mvn -P seguranca verify
```

---

## 10. Diretrizes para Assistentes de IA (Gemini / Antigravity)

1. **Idioma**: Escreva comentários no código, documentação e nomes de variáveis em **português**.
2. **Respeito ao Schema**: Não invente nomes de tabelas, colunas ou tipos. Consulte sempre `src/main/resources/banco/schema.sql`.
3. **Simplicidade (KISS)**: Prefira soluções diretas, limpas e sem excesso de abstração desnecessária. O prazo é rigoroso e a equipe prioriza manutenibilidade e clareza.
4. **Sem frameworks proibidos**: Jamais proponha Spring, Hibernate, JPA ou soluções web.

