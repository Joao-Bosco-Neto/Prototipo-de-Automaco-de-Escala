# Graph Report - .  (2026-08-18)

## Corpus Check
- Corpus is ~27,176 words - fits in a single context window. You may not need a graph.

## Summary
- 464 nodes · 812 edges · 27 communities (20 shown, 7 thin omitted)
- Extraction: 91% EXTRACTED · 9% INFERRED · 0% AMBIGUOUS · INFERRED: 73 edges (avg confidence: 0.81)
- Token cost: 0 input · 62,617 output

## Community Hubs (Navigation)
- [[_COMMUNITY_Usuario Repository JDBC|Usuario Repository JDBC]]
- [[_COMMUNITY_LancamentoHoras Repository Impl|LancamentoHoras Repository Impl]]
- [[_COMMUNITY_Configuracao Repository|Configuracao Repository]]
- [[_COMMUNITY_EscalaFuncionario Model|EscalaFuncionario Model]]
- [[_COMMUNITY_TipoTurno Model|TipoTurno Model]]
- [[_COMMUNITY_EscalaTurno Model|EscalaTurno Model]]
- [[_COMMUNITY_DB Connection & Usuario CRUD|DB Connection & Usuario CRUD]]
- [[_COMMUNITY_Funcionario Model|Funcionario Model]]
- [[_COMMUNITY_Database Bootstrap & Legacy Migration|Database Bootstrap & Legacy Migration]]
- [[_COMMUNITY_LancamentoHoras Repository Tests|LancamentoHoras Repository Tests]]
- [[_COMMUNITY_App Startup & DB Init|App Startup & DB Init]]
- [[_COMMUNITY_Dependency-Check Vulnerability Scan|Dependency-Check Vulnerability Scan]]
- [[_COMMUNITY_Usuario Repository Interface|Usuario Repository Interface]]
- [[_COMMUNITY_GitHub Issue Sync Script|GitHub Issue Sync Script]]
- [[_COMMUNITY_Git Workflow Conventions|Git Workflow Conventions]]
- [[_COMMUNITY_MER Diagram Entities|MER Diagram Entities]]
- [[_COMMUNITY_TipoLancamento Enum|TipoLancamento Enum]]
- [[_COMMUNITY_Repository Exception Handling|Repository Exception Handling]]
- [[_COMMUNITY_Shift Conflict Requirements|Shift Conflict Requirements]]
- [[_COMMUNITY_Rest Interval Requirement|Rest Interval Requirement]]
- [[_COMMUNITY_Tabela escala_funcionario|Tabela escala_funcionario]]
- [[_COMMUNITY_Tabela escala_turno|Tabela escala_turno]]
- [[_COMMUNITY_Tabela funcionario|Tabela funcionario]]
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
8. `Clean Scan: 0 Vulnerabilities Found (13/13 dependencies)` - 14 edges
9. `UsuarioRepositoryJdbc` - 13 edges
10. `LancamentoHorasRepositoryJdbcTest` - 11 edges

## Surprising Connections (you probably didn't know these)
- `UsuarioRepositoryJdbc.atualizarSenha(int, String)` --semantically_similar_to--> `jbcrypt-0.4.jar`  [INFERRED] [semantically similar]
  src/main/java/br/edu/sistemaescala/backend/repository/jdbc/UsuarioRepositoryJdbc.java → documentacao/seguranca/dependency-check-report.html
- `Issue #59 — Proteger o Arquivo do Banco de Dados` --references--> `ConexaoBanco`  [AMBIGUOUS]
  /home/jb/Prototipo-de-Automaco-de-Escala/scripts/BACKLOG_ISSUES.md → src/main/java/br/edu/sistemaescala/backend/dao/ConexaoBanco.java
- `Legado README Overview` --conceptually_related_to--> `ConexaoBanco`  [INFERRED]
  /home/jb/Prototipo-de-Automaco-de-Escala/documentacao/legado/README.md → src/main/java/br/edu/sistemaescala/backend/dao/ConexaoBanco.java
- `Issue #1 — Decidir SGBD (PostgreSQL vs H2)` --rationale_for--> `ConexaoBanco`  [INFERRED]
  /home/jb/Prototipo-de-Automaco-de-Escala/scripts/BACKLOG_ISSUES.md → src/main/java/br/edu/sistemaescala/backend/dao/ConexaoBanco.java
- `permissions` --conceptually_related_to--> `Convenção de Nomenclatura de Branches`  [INFERRED]
  .claude/settings.local.json → /home/jb/Prototipo-de-Automaco-de-Escala/documentacao/GUIA_DE_CONTRIBUICAO.md

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **Inicialização Automática do Banco na Partida** — sistemaescala_main_init, dao_bancoinicializador_inicializar, banco_schema_schema, banco_seed_seed, scripts_backlog_issues_issue_7 [EXTRACTED 1.00]
- **Banco de Horas Domain Aggregate** — model_lancamentohoras, model_funcionario, model_escalafuncionario, model_tipolancamento, repository_lancamentohorasrepository, jdbc_lancamentohorasrepositoryjdbc [INFERRED 0.85]
- **String-backed Enum with deValor Factory** — model_roleusuario, model_tipolancamento, model_motivocobertura [INFERRED 0.85]
- **Repository Interfaces with JDBC Implementations** — repository_configuracaorepository, repository_lancamentohorasrepository, repository_usuariorepository, jdbc_configuracaorepositoryjdbc, jdbc_lancamentohorasrepositoryjdbc, jdbc_usuariorepositoryjdbc, repository_repositoryexception [EXTRACTED 1.00]
- **Pipeline de Segurança de Dependências no CI** — github_dependabot_config, workflows_seguranca_dependency_check, scripts_backlog_issues_issue_65 [EXTRACTED 1.00]
- **Migração de PostgreSQL Legado para H2 Embarcado** — legado_import_postgres_legado_schema, legado_docker_compose_postgres_legado_db, scripts_backlog_issues_issue_1, dao_conexaobanco_conexaobanco, banco_schema_schema [INFERRED 0.85]
- **Employee shift assignment domain (funcionario, tipo_turno, escala_turno, escala_funcionario)** — documentacao_diagrama_mer_funcionario, documentacao_diagrama_mer_tipo_turno, documentacao_diagrama_mer_escala_turno, documentacao_diagrama_mer_escala_funcionario [INFERRED 0.85]
- **UsuarioRepository Interface / JDBC Implementation / Integration Test Triad** — repository_usuariorepository, jdbc_usuariorepositoryjdbc, repository_usuarioconfiguracaorepositoryjdbctest [INFERRED 0.85]
- **JavaFX 21.0.2 Dependency Family** — seguranca_dependency_check_report_javafx_base_21_0_2_jar, seguranca_dependency_check_report_javafx_base_21_0_2_linux_jar, seguranca_dependency_check_report_javafx_controls_21_0_2_jar, seguranca_dependency_check_report_javafx_controls_21_0_2_linux_jar, seguranca_dependency_check_report_javafx_fxml_21_0_2_jar, seguranca_dependency_check_report_javafx_fxml_21_0_2_linux_jar, seguranca_dependency_check_report_javafx_graphics_21_0_2_jar, seguranca_dependency_check_report_javafx_graphics_21_0_2_linux_jar [INFERRED 0.80]

## Communities (27 total, 7 thin omitted)

### Community 0 - "Usuario Repository JDBC"
Cohesion: 0.07
Nodes (21): UsuarioRepositoryJdbc.preencherInsercao(PreparedStatement, Usuario), UsuarioRepositoryJdbc, Usuario, UsuarioConfiguracaoRepositoryJdbcTest, RoleUsuario, Integer, LocalDateTime, Object (+13 more)

### Community 1 - "LancamentoHoras Repository Impl"
Cohesion: 0.07
Nodes (22): EscalaFuncionario, LancamentoHorasRepositoryJdbc, LancamentoHoras, LancamentoHorasRepository, Funcionario, Integer, LocalDate, LocalDateTime (+14 more)

### Community 2 - "Configuracao Repository"
Cohesion: 0.08
Nodes (21): Tabela configuracao, ConfiguracaoRepositoryJdbc, Configuracao, ConfiguracaoRepository, UsuarioConfiguracaoRepositoryJdbcTest.limparBanco(), Issue #70 — Tela de Configurações da Organização, Issue #8 — Criar Classes de Domínio, BigDecimal (+13 more)

### Community 3 - "EscalaFuncionario Model"
Cohesion: 0.08
Nodes (14): EscalaTurno, EscalaFuncionario, deValor(), MotivoCobertura(), toString(), valor(), Funcionario, Integer (+6 more)

### Community 4 - "TipoTurno Model"
Cohesion: 0.10
Nodes (8): LocalTime, TipoTurno, BigDecimal, Integer, LocalDateTime, Object, Override, String

### Community 5 - "EscalaTurno Model"
Cohesion: 0.11
Nodes (7): EscalaTurno, Integer, LocalDateTime, Object, Override, String, TipoTurno

### Community 6 - "DB Connection & Usuario CRUD"
Cohesion: 0.12
Nodes (24): ConexaoBanco, UsuarioRepositoryJdbc.atualizar(Usuario), UsuarioRepositoryJdbc.atualizarSenha(int, String), UsuarioRepositoryJdbc.buscarPorLogin(String), UsuarioRepositoryJdbc.desativar(int), UsuarioRepositoryJdbc.executarAtualizacao(String, String, int), UsuarioRepositoryJdbc.inserir(Usuario), UsuarioRepositoryJdbc.listar() (+16 more)

### Community 7 - "Funcionario Model"
Cohesion: 0.13
Nodes (6): Funcionario, Integer, LocalDateTime, Object, Override, String

### Community 8 - "Database Bootstrap & Legacy Migration"
Cohesion: 0.10
Nodes (21): Tabela motivo_cobertura, schema.sql (script de criação do banco), Tabela tipo_turno, Tabela usuario, seed.sql (carga inicial), BancoInicializador.executarScript(), BancoInicializador.inicializar(), Dependabot Configuration (+13 more)

### Community 9 - "LancamentoHoras Repository Tests"
Cohesion: 0.21
Nodes (10): LancamentoHorasRepositoryJdbcTest, AfterAll, BeforeAll, Connection, LancamentoHoras, LocalDate, Object, String (+2 more)

### Community 10 - "App Startup & DB Init"
Cohesion: 0.15
Nodes (10): Application, BancoInicializador, UsuarioConfiguracaoRepositoryJdbcTest.prepararBanco(), Main, Connection, List, String, Override (+2 more)

### Community 11 - "Dependency-Check Vulnerability Scan"
Cohesion: 0.13
Nodes (15): OWASP Dependency-Check Report (sistema-escala), h2-2.2.224.jar, h2-2.2.224.jar: data.zip: table.js, h2-2.2.224.jar: data.zip: tree.js, javafx-base-21.0.2.jar, javafx-base-21.0.2-linux.jar, javafx-controls-21.0.2.jar, javafx-controls-21.0.2-linux.jar (+7 more)

### Community 12 - "Usuario Repository Interface"
Cohesion: 0.23
Nodes (6): UsuarioRepository, List, LocalDateTime, Optional, String, Usuario

### Community 13 - "GitHub Issue Sync Script"
Cohesion: 0.36
Nodes (8): api_saudavel(), executar(), main(), mapear_existentes(), montar_corpo(), parse_backlog(), Casa GitHub -> backlog pelo rodape; cai para o titulo se faltar rodape., rodar()

### Community 14 - "Git Workflow Conventions"
Cohesion: 0.29
Nodes (6): permissions, allow, Convenção de Nomenclatura de Branches, Conventional Commits, Fluxo de Pull Request e Code Review, Issue #5 — Padronizar Fluxo de Git e Guia de Contribuição

### Community 15 - "MER Diagram Entities"
Cohesion: 0.48
Nodes (7): configuracao table, Diagrama MER (Entity-Relationship Diagram), escala_funcionario table, escala_turno table, funcionario table, tipo_turno table, usuario table

### Community 16 - "TipoLancamento Enum"
Cohesion: 0.48
Nodes (6): deValor(), TipoLancamento(), toString(), valor(), Override, String

### Community 17 - "Repository Exception Handling"
Cohesion: 0.33
Nodes (4): RepositoryException, RuntimeException, String, Throwable

### Community 18 - "Shift Conflict Requirements"
Cohesion: 0.40
Nodes (5): RF05 — Validar Conflito de Horário, RF10 — Mínimo de 2 Agentes por Plantão, RNF06 — Bloquear Conflito de Horário Não Resolvido, RNF08 — Bloquear Violação de Mínimo de Agentes / Descanso, Issue #26 — Mínimo de Agentes e Bloqueio de Duplicidade (RF05, RF10)

## Ambiguous Edges - Review These
- `ConexaoBanco` → `Issue #59 — Proteger o Arquivo do Banco de Dados`  [AMBIGUOUS]
  /home/jb/Prototipo-de-Automaco-de-Escala/scripts/BACKLOG_ISSUES.md · relation: references
- `EscalaFuncionario.java` → `MotivoCobertura.java`  [AMBIGUOUS]
  /home/jb/Prototipo-de-Automaco-de-Escala/src/main/java/br/edu/sistemaescala/backend/model/EscalaFuncionario.java · relation: shares_data_with
- `seed.sql (carga inicial)` → `Issue #66 — Endurecer Configuração da Aplicação e do H2`  [AMBIGUOUS]
  /home/jb/Prototipo-de-Automaco-de-Escala/scripts/BACKLOG_ISSUES.md · relation: references

## Knowledge Gaps
- **54 isolated node(s):** `allow`, `String`, `Object`, `Object`, `Object` (+49 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **7 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **What is the exact relationship between `ConexaoBanco` and `Issue #59 — Proteger o Arquivo do Banco de Dados`?**
  _Edge tagged AMBIGUOUS (relation: references) - confidence is low._
- **What is the exact relationship between `EscalaFuncionario.java` and `MotivoCobertura.java`?**
  _Edge tagged AMBIGUOUS (relation: shares_data_with) - confidence is low._
- **What is the exact relationship between `seed.sql (carga inicial)` and `Issue #66 — Endurecer Configuração da Aplicação e do H2`?**
  _Edge tagged AMBIGUOUS (relation: references) - confidence is low._
- **What connects `allow`, `Casa GitHub -> backlog pelo rodape; cai para o titulo se faltar rodape.`, `String` to the rest of the system?**
  _57 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Usuario Repository JDBC` be split into smaller, more focused modules?**
  _Cohesion score 0.07377049180327869 - nodes in this community are weakly interconnected._
- **Should `LancamentoHoras Repository Impl` be split into smaller, more focused modules?**
  _Cohesion score 0.0707070707070707 - nodes in this community are weakly interconnected._
- **Should `Configuracao Repository` be split into smaller, more focused modules?**
  _Cohesion score 0.08 - nodes in this community are weakly interconnected._