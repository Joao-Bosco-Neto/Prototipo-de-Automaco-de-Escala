# Graph Report - .  (2026-08-18)

## Corpus Check
- Corpus is ~21,552 words - fits in a single context window. You may not need a graph.

## Summary
- 446 nodes · 791 edges · 25 communities (19 shown, 6 thin omitted)
- Extraction: 91% EXTRACTED · 9% INFERRED · 1% AMBIGUOUS · INFERRED: 71 edges (avg confidence: 0.81)
- Token cost: 0 input · 227,410 output

## Community Hubs (Navigation)
- [[_COMMUNITY_Configuracao Repository|Configuracao Repository]]
- [[_COMMUNITY_LancamentoHoras Repository Impl|LancamentoHoras Repository Impl]]
- [[_COMMUNITY_LancamentoHoras Domain Logic|LancamentoHoras Domain Logic]]
- [[_COMMUNITY_Usuario & Role Model|Usuario & Role Model]]
- [[_COMMUNITY_Usuario Repository JDBC|Usuario Repository JDBC]]
- [[_COMMUNITY_TipoTurno Model|TipoTurno Model]]
- [[_COMMUNITY_EscalaFuncionario Model|EscalaFuncionario Model]]
- [[_COMMUNITY_Database Bootstrap & Legacy Migration|Database Bootstrap & Legacy Migration]]
- [[_COMMUNITY_EscalaTurno Model|EscalaTurno Model]]
- [[_COMMUNITY_Funcionario Model|Funcionario Model]]
- [[_COMMUNITY_App Startup & DB Init|App Startup & DB Init]]
- [[_COMMUNITY_Equipe Legacy Model|Equipe Legacy Model]]
- [[_COMMUNITY_GitHub Issue Sync Script|GitHub Issue Sync Script]]
- [[_COMMUNITY_Git Workflow Conventions|Git Workflow Conventions]]
- [[_COMMUNITY_MER Diagram Entities|MER Diagram Entities]]
- [[_COMMUNITY_MotivoCobertura Enum|MotivoCobertura Enum]]
- [[_COMMUNITY_Repository Exception Handling|Repository Exception Handling]]
- [[_COMMUNITY_Shift Conflict Requirements|Shift Conflict Requirements]]
- [[_COMMUNITY_Rest Interval Requirement|Rest Interval Requirement]]
- [[_COMMUNITY_Tabela escala_funcionario|Tabela escala_funcionario]]
- [[_COMMUNITY_Tabela escala_turno|Tabela escala_turno]]
- [[_COMMUNITY_Tabela lancamento_horas|Tabela lancamento_horas]]
- [[_COMMUNITY_Password Hash Decision|Password Hash Decision]]
- [[_COMMUNITY_Main JavaFX App|Main JavaFX App]]

## God Nodes (most connected - your core abstractions)
1. `EscalaFuncionario` - 25 edges
2. `TipoTurno` - 25 edges
3. `EscalaTurno` - 23 edges
4. `Configuracao` - 22 edges
5. `LancamentoHoras` - 21 edges
6. `Usuario` - 21 edges
7. `Funcionario` - 19 edges
8. `Equipe` - 13 edges
9. `UsuarioRepositoryJdbc` - 13 edges
10. `LancamentoHorasRepositoryJdbcTest` - 11 edges

## Surprising Connections (you probably didn't know these)
- `Issue #59 — Proteger o Arquivo do Banco de Dados` --references--> `ConexaoBanco`  [AMBIGUOUS]
  scripts/BACKLOG_ISSUES.md → src/main/java/br/edu/sistemaescala/backend/dao/ConexaoBanco.java
- `Issue #1 — Decidir SGBD (PostgreSQL vs H2)` --rationale_for--> `ConexaoBanco`  [INFERRED]
  scripts/BACKLOG_ISSUES.md → src/main/java/br/edu/sistemaescala/backend/dao/ConexaoBanco.java
- `Issue #8 — Criar Classes de Domínio` --references--> `Configuracao`  [EXTRACTED]
  scripts/BACKLOG_ISSUES.md → src/main/java/br/edu/sistemaescala/backend/model/Configuracao.java
- `Issue #66 — Endurecer Configuração da Aplicação e do H2` --references--> `seed.sql (carga inicial)`  [AMBIGUOUS]
  scripts/BACKLOG_ISSUES.md → src/main/resources/banco/seed.sql
- `permissions` --conceptually_related_to--> `Convenção de Nomenclatura de Branches`  [INFERRED]
  .claude/settings.local.json → documentacao/GUIA_DE_CONTRIBUICAO.md

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **Migração de PostgreSQL Legado para H2 Embarcado** — legado_import_postgres_legado_schema, legado_docker_compose_postgres_legado_db, scripts_backlog_issues_issue_1, dao_conexaobanco_conexaobanco, banco_schema_schema [INFERRED 0.85]
- **Inicialização Automática do Banco na Partida** — sistemaescala_main_init, dao_bancoinicializador_inicializar, banco_schema_schema, banco_seed_seed, scripts_backlog_issues_issue_7 [EXTRACTED 1.00]
- **Pipeline de Segurança de Dependências no CI** — github_dependabot_config, workflows_seguranca_dependency_check, scripts_backlog_issues_issue_65 [EXTRACTED 1.00]
- **String-backed Enum with deValor Factory** — model_roleusuario, model_tipolancamento, model_motivocobertura [INFERRED 0.85]
- **Repository Interfaces with JDBC Implementations** — repository_configuracaorepository, repository_lancamentohorasrepository, repository_usuariorepository, jdbc_configuracaorepositoryjdbc, jdbc_lancamentohorasrepositoryjdbc, jdbc_usuariorepositoryjdbc, repository_repositoryexception [EXTRACTED 1.00]
- **Banco de Horas Domain Aggregate** — model_lancamentohoras, model_funcionario, model_escalafuncionario, model_tipolancamento, repository_lancamentohorasrepository, jdbc_lancamentohorasrepositoryjdbc [INFERRED 0.85]
- **Employee shift assignment domain (funcionario, tipo_turno, escala_turno, escala_funcionario)** — documentacao_diagrama_mer_funcionario, documentacao_diagrama_mer_tipo_turno, documentacao_diagrama_mer_escala_turno, documentacao_diagrama_mer_escala_funcionario [INFERRED 0.85]

## Communities (25 total, 6 thin omitted)

### Community 0 - "Configuracao Repository"
Cohesion: 0.09
Nodes (19): Tabela configuracao, ConfiguracaoRepositoryJdbc, Configuracao, ConfiguracaoRepository, Issue #70 — Tela de Configurações da Organização, BigDecimal, Integer, LocalDateTime (+11 more)

### Community 1 - "LancamentoHoras Repository Impl"
Cohesion: 0.08
Nodes (18): EscalaFuncionario, LancamentoHorasRepositoryJdbc, LancamentoHoras, Funcionario, Integer, LocalDate, LocalDateTime, Object (+10 more)

### Community 2 - "LancamentoHoras Domain Logic"
Cohesion: 0.10
Nodes (20): deValor(), TipoLancamento(), toString(), valor(), LancamentoHorasRepository, LancamentoHorasRepositoryJdbcTest, Override, String (+12 more)

### Community 3 - "Usuario & Role Model"
Cohesion: 0.08
Nodes (17): deValor(), RoleUsuario(), toString(), valor(), Usuario, UsuarioConfiguracaoRepositoryJdbcTest, RoleUsuario, Override (+9 more)

### Community 4 - "Usuario Repository JDBC"
Cohesion: 0.12
Nodes (15): UsuarioRepositoryJdbc, UsuarioRepository, List, LocalDateTime, Optional, Override, PreparedStatement, ResultSet (+7 more)

### Community 5 - "TipoTurno Model"
Cohesion: 0.10
Nodes (8): LocalTime, TipoTurno, BigDecimal, Integer, LocalDateTime, Object, Override, String

### Community 6 - "EscalaFuncionario Model"
Cohesion: 0.10
Nodes (8): EscalaTurno, EscalaFuncionario, Funcionario, Integer, LocalDateTime, Object, Override, String

### Community 7 - "Database Bootstrap & Legacy Migration"
Cohesion: 0.08
Nodes (26): Tabela motivo_cobertura, schema.sql (script de criação do banco), Tabela tipo_turno, Tabela usuario, seed.sql (carga inicial), BancoInicializador.executarScript(), BancoInicializador.inicializar(), ConexaoBanco (+18 more)

### Community 8 - "EscalaTurno Model"
Cohesion: 0.11
Nodes (7): EscalaTurno, Integer, LocalDateTime, Object, Override, String, TipoTurno

### Community 9 - "Funcionario Model"
Cohesion: 0.14
Nodes (6): Funcionario, Integer, LocalDateTime, Object, Override, String

### Community 10 - "App Startup & DB Init"
Cohesion: 0.16
Nodes (9): Application, BancoInicializador, Main, Connection, List, String, Override, String (+1 more)

### Community 11 - "Equipe Legacy Model"
Cohesion: 0.16
Nodes (7): Tabela funcionario, Equipe, Issue #8 — Criar Classes de Domínio, Integer, Object, Override, String

### Community 12 - "GitHub Issue Sync Script"
Cohesion: 0.36
Nodes (8): api_saudavel(), executar(), main(), mapear_existentes(), montar_corpo(), parse_backlog(), Casa GitHub -> backlog pelo rodape; cai para o titulo se faltar rodape., rodar()

### Community 13 - "Git Workflow Conventions"
Cohesion: 0.29
Nodes (6): permissions, allow, Convenção de Nomenclatura de Branches, Conventional Commits, Fluxo de Pull Request e Code Review, Issue #5 — Padronizar Fluxo de Git e Guia de Contribuição

### Community 14 - "MER Diagram Entities"
Cohesion: 0.48
Nodes (7): configuracao table, Diagrama MER (Entity-Relationship Diagram), escala_funcionario table, escala_turno table, funcionario table, tipo_turno table, usuario table

### Community 15 - "MotivoCobertura Enum"
Cohesion: 0.48
Nodes (6): deValor(), MotivoCobertura(), toString(), valor(), Override, String

### Community 16 - "Repository Exception Handling"
Cohesion: 0.33
Nodes (4): RepositoryException, RuntimeException, String, Throwable

### Community 17 - "Shift Conflict Requirements"
Cohesion: 0.40
Nodes (5): RF05 — Validar Conflito de Horário, RF10 — Mínimo de 2 Agentes por Plantão, RNF06 — Bloquear Conflito de Horário Não Resolvido, RNF08 — Bloquear Violação de Mínimo de Agentes / Descanso, Issue #26 — Mínimo de Agentes e Bloqueio de Duplicidade (RF05, RF10)

## Ambiguous Edges - Review These
- `ConexaoBanco` → `Issue #59 — Proteger o Arquivo do Banco de Dados`  [AMBIGUOUS]
  scripts/BACKLOG_ISSUES.md · relation: references
- `Equipe` → `Tabela funcionario`  [AMBIGUOUS]
  src/main/java/br/edu/sistemaescala/backend/model/Equipe.java · relation: conceptually_related_to
- `EscalaFuncionario.java` → `MotivoCobertura.java`  [AMBIGUOUS]
  src/main/java/br/edu/sistemaescala/backend/model/EscalaFuncionario.java · relation: shares_data_with
- `Issue #66 — Endurecer Configuração da Aplicação e do H2` → `seed.sql (carga inicial)`  [AMBIGUOUS]
  scripts/BACKLOG_ISSUES.md · relation: references

## Knowledge Gaps
- **41 isolated node(s):** `allow`, `String`, `Object`, `Object`, `Object` (+36 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **6 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **What is the exact relationship between `ConexaoBanco` and `Issue #59 — Proteger o Arquivo do Banco de Dados`?**
  _Edge tagged AMBIGUOUS (relation: references) - confidence is low._
- **What is the exact relationship between `Equipe` and `Tabela funcionario`?**
  _Edge tagged AMBIGUOUS (relation: conceptually_related_to) - confidence is low._
- **What is the exact relationship between `EscalaFuncionario.java` and `MotivoCobertura.java`?**
  _Edge tagged AMBIGUOUS (relation: shares_data_with) - confidence is low._
- **What is the exact relationship between `Issue #66 — Endurecer Configuração da Aplicação e do H2` and `seed.sql (carga inicial)`?**
  _Edge tagged AMBIGUOUS (relation: references) - confidence is low._
- **Why does `BancoInicializador.inicializar()` connect `Database Bootstrap & Legacy Migration` to `LancamentoHoras Domain Logic`, `Usuario & Role Model`?**
  _High betweenness centrality (0.135) - this node is a cross-community bridge._
- **Why does `Configuracao` connect `Configuracao Repository` to `Equipe Legacy Model`, `Database Bootstrap & Legacy Migration`?**
  _High betweenness centrality (0.129) - this node is a cross-community bridge._
- **Why does `EscalaFuncionario` connect `EscalaFuncionario Model` to `LancamentoHoras Domain Logic`?**
  _High betweenness centrality (0.102) - this node is a cross-community bridge._