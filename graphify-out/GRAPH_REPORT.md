# Graph Report - .  (2026-08-20)

## Corpus Check
- 23 files · ~34,059 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 663 nodes · 1302 edges · 31 communities (24 shown, 7 thin omitted)
- Extraction: 89% EXTRACTED · 10% INFERRED · 0% AMBIGUOUS · INFERRED: 135 edges (avg confidence: 0.8)
- Token cost: 0 input · 0 output

## Community Hubs (Navigation)
- [[_COMMUNITY_EscalaFuncionario Repository|EscalaFuncionario Repository]]
- [[_COMMUNITY_Usuario Repository & Model|Usuario Repository & Model]]
- [[_COMMUNITY_LancamentoHoras Repository|LancamentoHoras Repository]]
- [[_COMMUNITY_TipoTurno Repository|TipoTurno Repository]]
- [[_COMMUNITY_EscalaTurno JDBC Repository|EscalaTurno JDBC Repository]]
- [[_COMMUNITY_Configuracao Repository|Configuracao Repository]]
- [[_COMMUNITY_MotivoCobertura JDBC Repository|MotivoCobertura JDBC Repository]]
- [[_COMMUNITY_EscalaTurno Model|EscalaTurno Model]]
- [[_COMMUNITY_App Bootstrap & DB Init|App Bootstrap & DB Init]]
- [[_COMMUNITY_Funcionario Mapping|Funcionario Mapping]]
- [[_COMMUNITY_Funcionario Repository Interface|Funcionario Repository Interface]]
- [[_COMMUNITY_FuncionarioRepositoryJdbc Impl|FuncionarioRepositoryJdbc Impl]]
- [[_COMMUNITY_FuncionarioRepositoryJdbc Tests|FuncionarioRepositoryJdbc Tests]]
- [[_COMMUNITY_MotivoCobertura Model|MotivoCobertura Model]]
- [[_COMMUNITY_Dependency Check Report|Dependency Check Report]]
- [[_COMMUNITY_DB Schema & Seed Scripts|DB Schema & Seed Scripts]]
- [[_COMMUNITY_UsuarioRepositoryJdbc Auth Flows|UsuarioRepositoryJdbc Auth Flows]]
- [[_COMMUNITY_DB Schema & Legacy Docs|DB Schema & Legacy Docs]]
- [[_COMMUNITY_Issue Sync Script|Issue Sync Script]]
- [[_COMMUNITY_Contribution Guide & Settings|Contribution Guide & Settings]]
- [[_COMMUNITY_TipoLancamento Model|TipoLancamento Model]]
- [[_COMMUNITY_CICD & Dependabot|CI/CD & Dependabot]]
- [[_COMMUNITY_Legado Delegacia Requirements|Legado Delegacia Requirements]]
- [[_COMMUNITY_Legado Delegacia RF11|Legado Delegacia RF11]]
- [[_COMMUNITY_Schema escala_funcionario Table|Schema: escala_funcionario Table]]
- [[_COMMUNITY_Schema escala_turno Table|Schema: escala_turno Table]]
- [[_COMMUNITY_Schema funcionario Table|Schema: funcionario Table]]
- [[_COMMUNITY_Schema lancamento_horas Table|Schema: lancamento_horas Table]]
- [[_COMMUNITY_Backlog Issue 58|Backlog Issue 58]]
- [[_COMMUNITY_Main Entry Point|Main Entry Point]]

## God Nodes (most connected - your core abstractions)
1. `EscalaFuncionario` - 32 edges
2. `TipoTurno` - 26 edges
3. `EscalaTurno` - 25 edges
4. `LancamentoHoras` - 24 edges
5. `Funcionario` - 23 edges
6. `Usuario` - 23 edges
7. `Configuracao` - 21 edges
8. `MotivoCobertura()` - 15 edges
9. `Clean Scan: 0 Vulnerabilities Found (13/13 dependencies)` - 13 edges
10. `FuncionarioRepositoryJdbc` - 13 edges

## Surprising Connections (you probably didn't know these)
- `UsuarioRepositoryJdbc.atualizarSenha(int, String)` --semantically_similar_to--> `jbcrypt-0.4.jar`  [INFERRED] [semantically similar]
  src/main/java/br/edu/sistemaescala/backend/repository/jdbc/UsuarioRepositoryJdbc.java → documentacao/seguranca/dependency-check-report.html
- `Issue #59 — Proteger o Arquivo do Banco de Dados` --references--> `ConexaoBanco`  [AMBIGUOUS]
  /home/jb/Prototipo-de-Automaco-de-Escala/scripts/BACKLOG_ISSUES.md → src/main/java/br/edu/sistemaescala/backend/dao/ConexaoBanco.java
- `Issue #1 — Decidir SGBD (PostgreSQL vs H2)` --rationale_for--> `ConexaoBanco`  [INFERRED]
  /home/jb/Prototipo-de-Automaco-de-Escala/scripts/BACKLOG_ISSUES.md → src/main/java/br/edu/sistemaescala/backend/dao/ConexaoBanco.java
- `permissions` --conceptually_related_to--> `Convenção de Nomenclatura de Branches`  [INFERRED]
  .claude/settings.local.json → /home/jb/Prototipo-de-Automaco-de-Escala/documentacao/GUIA_DE_CONTRIBUICAO.md
- `Issue #7 — Conexão H2 e Inicialização Automática do Banco` --references--> `BancoInicializador`  [EXTRACTED]
  /home/jb/Prototipo-de-Automaco-de-Escala/scripts/BACKLOG_ISSUES.md → src/main/java/br/edu/sistemaescala/backend/dao/BancoInicializador.java

## Import Cycles
- None detected.

## Communities (31 total, 7 thin omitted)

### Community 0 - "EscalaFuncionario Repository"
Cohesion: 0.06
Nodes (33): configuracao table, Diagrama MER (Entity-Relationship Diagram), funcionario table, tipo_turno table, usuario table, EscalaFuncionario, EscalaTurno, EscalaFuncionarioRepository (+25 more)

### Community 1 - "Usuario Repository & Model"
Cohesion: 0.06
Nodes (29): UsuarioRepositoryJdbc.mapear(ResultSet), UsuarioRepositoryJdbc.preencherInsercao(PreparedStatement, Usuario), deValor(), RoleUsuario(), toString(), valor(), Usuario, UsuarioRepository (+21 more)

### Community 2 - "LancamentoHoras Repository"
Cohesion: 0.06
Nodes (30): LocalDate, LancamentoHoras, LancamentoHorasRepository, Connection, Funcionario, Integer, LocalDateTime, Object (+22 more)

### Community 3 - "TipoTurno Repository"
Cohesion: 0.06
Nodes (27): LocalTime, TipoTurno, TipoTurnoRepository, BigDecimal, Integer, LocalDateTime, Object, Override (+19 more)

### Community 4 - "EscalaTurno JDBC Repository"
Cohesion: 0.08
Nodes (26): Map, EscalaTurnoRepository, EscalaTurno, List, LocalDateTime, Optional, YearMonth, EscalaFuncionario (+18 more)

### Community 5 - "Configuracao Repository"
Cohesion: 0.08
Nodes (23): Tabela configuracao, Configuracao, ConfiguracaoRepository, RepositoryException, RuntimeException, Issue #70 — Tela de Configurações da Organização, Issue #8 — Criar Classes de Domínio, BigDecimal (+15 more)

### Community 6 - "MotivoCobertura JDBC Repository"
Cohesion: 0.10
Nodes (16): MotivoCoberturaRepository, Boolean, List, MotivoCobertura, Optional, Override, PreparedStatement, ResultSet (+8 more)

### Community 7 - "EscalaTurno Model"
Cohesion: 0.12
Nodes (8): EscalaTurno, EscalaFuncionario, Integer, List, LocalDateTime, Object, Override, String

### Community 8 - "App Bootstrap & DB Init"
Cohesion: 0.12
Nodes (13): Application, BancoInicializador, UsuarioConfiguracaoRepositoryJdbcTest, Main, Connection, List, String, Override (+5 more)

### Community 9 - "Funcionario Mapping"
Cohesion: 0.14
Nodes (7): Funcionario, Integer, LocalDateTime, Object, Override, String, Funcionario

### Community 10 - "Funcionario Repository Interface"
Cohesion: 0.13
Nodes (13): FuncionarioRepository, Boolean, Funcionario, Integer, List, Optional, String, YearMonth (+5 more)

### Community 11 - "FuncionarioRepositoryJdbc Impl"
Cohesion: 0.21
Nodes (7): FuncionarioRepositoryJdbc, Boolean, Funcionario, Integer, List, Override, String

### Community 12 - "FuncionarioRepositoryJdbc Tests"
Cohesion: 0.21
Nodes (7): FuncionarioRepositoryJdbcTest, AfterAll, BeforeAll, Connection, Object, String, Test

### Community 13 - "MotivoCobertura Model"
Cohesion: 0.20
Nodes (8): deValor(), MotivoCobertura(), toString(), valor(), Integer, Object, Override, String

### Community 14 - "Dependency Check Report"
Cohesion: 0.14
Nodes (14): OWASP Dependency-Check Report (sistema-escala), h2-2.2.224.jar, h2-2.2.224.jar: data.zip: table.js, h2-2.2.224.jar: data.zip: tree.js, javafx-base-21.0.2.jar, javafx-base-21.0.2-linux.jar, javafx-controls-21.0.2.jar, javafx-controls-21.0.2-linux.jar (+6 more)

### Community 15 - "DB Schema & Seed Scripts"
Cohesion: 0.22
Nodes (9): schema.sql (script de criação do banco), Tabela usuario, seed.sql (carga inicial), BancoInicializador.executarScript(), BancoInicializador.inicializar(), Schema PostgreSQL Legado, Issue #3 — Implementar Schema do Banco de Dados, Issue #66 — Endurecer Configuração da Aplicação e do H2 (+1 more)

### Community 16 - "UsuarioRepositoryJdbc Auth Flows"
Cohesion: 0.35
Nodes (10): UsuarioRepositoryJdbc.atualizar(Usuario), UsuarioRepositoryJdbc.atualizarSenha(int, String), UsuarioRepositoryJdbc.buscarPorLogin(String), UsuarioRepositoryJdbc.executarAtualizacao(String, String, int), UsuarioRepositoryJdbc.inserir(Usuario), UsuarioRepositoryJdbc.registrarUltimoLogin(int, LocalDateTime), UsuarioRepositoryJdbc.verificarAtualizacao(int, String), UsuarioConfiguracaoRepositoryJdbcTest.buscaPorLoginRetornaUsuarioCorretoQuandoNomesSaoIguais() (+2 more)

### Community 17 - "DB Schema & Legacy Docs"
Cohesion: 0.20
Nodes (10): Tabela motivo_cobertura, Tabela tipo_turno, ConexaoBanco, Serviço Postgres do Docker Compose Legado, Documento Original de Requisitos da Delegacia, RNF02 — Exigência de PostgreSQL, Legado README Overview, Issue #1 — Decidir SGBD (PostgreSQL vs H2) (+2 more)

### Community 18 - "Issue Sync Script"
Cohesion: 0.36
Nodes (8): api_saudavel(), executar(), main(), mapear_existentes(), montar_corpo(), parse_backlog(), Casa GitHub -> backlog pelo rodape; cai para o titulo se faltar rodape., rodar()

### Community 19 - "Contribution Guide & Settings"
Cohesion: 0.29
Nodes (6): permissions, allow, Convenção de Nomenclatura de Branches, Conventional Commits, Fluxo de Pull Request e Code Review, Issue #5 — Padronizar Fluxo de Git e Guia de Contribuição

### Community 20 - "TipoLancamento Model"
Cohesion: 0.48
Nodes (6): deValor(), TipoLancamento(), toString(), valor(), Override, String

### Community 21 - "CI/CD & Dependabot"
Cohesion: 0.40
Nodes (6): Dependabot Configuration, README Principal do Projeto, Issue #6 — Configurar Build e Testes no GitHub Actions, Issue #65 — Verificação de Dependências Vulneráveis no CI, Build CI Workflow, Dependency-Check Security Workflow

### Community 22 - "Legado Delegacia Requirements"
Cohesion: 0.40
Nodes (5): RF05 — Validar Conflito de Horário, RF10 — Mínimo de 2 Agentes por Plantão, RNF06 — Bloquear Conflito de Horário Não Resolvido, RNF08 — Bloquear Violação de Mínimo de Agentes / Descanso, Issue #26 — Mínimo de Agentes e Bloqueio de Duplicidade (RF05, RF10)

## Ambiguous Edges - Review These
- `ConexaoBanco` → `Issue #59 — Proteger o Arquivo do Banco de Dados`  [AMBIGUOUS]
  /home/jb/Prototipo-de-Automaco-de-Escala/scripts/BACKLOG_ISSUES.md · relation: references
- `seed.sql (carga inicial)` → `Issue #66 — Endurecer Configuração da Aplicação e do H2`  [AMBIGUOUS]
  /home/jb/Prototipo-de-Automaco-de-Escala/scripts/BACKLOG_ISSUES.md · relation: references

## Knowledge Gaps
- **71 isolated node(s):** `allow`, `String`, `Object`, `Object`, `Object` (+66 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **7 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **What is the exact relationship between `ConexaoBanco` and `Issue #59 — Proteger o Arquivo do Banco de Dados`?**
  _Edge tagged AMBIGUOUS (relation: references) - confidence is low._
- **What is the exact relationship between `seed.sql (carga inicial)` and `Issue #66 — Endurecer Configuração da Aplicação e do H2`?**
  _Edge tagged AMBIGUOUS (relation: references) - confidence is low._
- **Why does `EscalaFuncionario` connect `EscalaFuncionario Repository` to `Funcionario Mapping`, `LancamentoHoras Repository`, `EscalaTurno JDBC Repository`?**
  _High betweenness centrality (0.380) - this node is a cross-community bridge._
- **Why does `LocalTime` connect `TipoTurno Repository` to `EscalaTurno JDBC Repository`?**
  _High betweenness centrality (0.137) - this node is a cross-community bridge._
- **Why does `LocalDate` connect `LancamentoHoras Repository` to `Funcionario Repository Interface`?**
  _High betweenness centrality (0.068) - this node is a cross-community bridge._
- **Are the 2 inferred relationships involving `EscalaFuncionario` (e.g. with `.mapear()` and `.salvar()`) actually correct?**
  _`EscalaFuncionario` has 2 INFERRED edges - model-reasoned connections that need verification._
- **What connects `allow`, `Casa GitHub -> backlog pelo rodape; cai para o titulo se faltar rodape.`, `String` to the rest of the system?**
  _74 weakly-connected nodes found - possible documentation gaps or missing edges._