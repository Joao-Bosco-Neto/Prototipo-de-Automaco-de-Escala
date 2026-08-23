# Graph Report - .  (2026-08-22)

## Corpus Check
- 24 files · ~48,669 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 774 nodes · 1539 edges · 33 communities (24 shown, 9 thin omitted)
- Extraction: 90% EXTRACTED · 10% INFERRED · 0% AMBIGUOUS · INFERRED: 148 edges (avg confidence: 0.81)
- Token cost: 95,289 input · 0 output

## Community Hubs (Navigation)
- [[_COMMUNITY_Modelo de Dados da Escala|Modelo de Dados da Escala]]
- [[_COMMUNITY_Repositorio de Lancamento de Horas|Repositorio de Lancamento de Horas]]
- [[_COMMUNITY_Repositorio de Tipo de Turno|Repositorio de Tipo de Turno]]
- [[_COMMUNITY_Repositorio de Usuario (JDBC)|Repositorio de Usuario (JDBC)]]
- [[_COMMUNITY_Bootstrap da Aplicacao e Auth|Bootstrap da Aplicacao e Auth]]
- [[_COMMUNITY_Repositorio de Escala de Turno|Repositorio de Escala de Turno]]
- [[_COMMUNITY_Servico de Autenticacao|Servico de Autenticacao]]
- [[_COMMUNITY_Configuracao e Seed de Dados|Configuracao e Seed de Dados]]
- [[_COMMUNITY_Conexao e Senha do Banco|Conexao e Senha do Banco]]
- [[_COMMUNITY_Repositorio de Motivo de Cobertura|Repositorio de Motivo de Cobertura]]
- [[_COMMUNITY_Entidade EscalaTurno|Entidade EscalaTurno]]
- [[_COMMUNITY_Inicializacao do Banco (Schema)|Inicializacao do Banco (Schema)]]
- [[_COMMUNITY_Entidade Funcionario|Entidade Funcionario]]
- [[_COMMUNITY_Entidade MotivoCobertura|Entidade MotivoCobertura]]
- [[_COMMUNITY_Interface FuncionarioRepository|Interface FuncionarioRepository]]
- [[_COMMUNITY_Testes de FuncionarioRepository|Testes de FuncionarioRepository]]
- [[_COMMUNITY_Impl JDBC de FuncionarioRepository|Impl JDBC de FuncionarioRepository]]
- [[_COMMUNITY_Metodos de FuncionarioRepositoryJdbc|Metodos de FuncionarioRepositoryJdbc]]
- [[_COMMUNITY_Interface UsuarioRepository|Interface UsuarioRepository]]
- [[_COMMUNITY_Script de Sincronizacao de Issues|Script de Sincronizacao de Issues]]
- [[_COMMUNITY_Permissoes e Convencoes de Git|Permissoes e Convencoes de Git]]
- [[_COMMUNITY_Enum RoleUsuario|Enum RoleUsuario]]
- [[_COMMUNITY_CI e Verificacao de Dependencias|CI e Verificacao de Dependencias]]
- [[_COMMUNITY_Requisitos de Conflito de Horario|Requisitos de Conflito de Horario]]
- [[_COMMUNITY_Hooks do Claude Code|Hooks do Claude Code]]
- [[_COMMUNITY_Regras Graphify do Projeto|Regras Graphify do Projeto]]
- [[_COMMUNITY_Requisito de Descanso 72h|Requisito de Descanso 72h]]
- [[_COMMUNITY_Tabela escala_funcionario|Tabela escala_funcionario]]
- [[_COMMUNITY_Tabela escala_turno|Tabela escala_turno]]
- [[_COMMUNITY_Tabela funcionario|Tabela funcionario]]
- [[_COMMUNITY_Tabela lancamento_horas|Tabela lancamento_horas]]
- [[_COMMUNITY_Decisao de Hash de Senha|Decisao de Hash de Senha]]
- [[_COMMUNITY_Endurecimento de Configuracao|Endurecimento de Configuracao]]

## God Nodes (most connected - your core abstractions)
1. `EscalaFuncionario` - 32 edges
2. `Configuracao` - 29 edges
3. `EscalaTurno` - 25 edges
4. `Usuario` - 25 edges
5. `TipoTurno` - 24 edges
6. `Funcionario` - 23 edges
7. `LancamentoHoras` - 23 edges
8. `AutenticacaoServiceImplTest` - 17 edges
9. `MotivoCobertura()` - 15 edges
10. `ConexaoBanco` - 14 edges

## Surprising Connections (you probably didn't know these)
- `UsuarioRepositoryJdbc.atualizarSenha(int, String)` --semantically_similar_to--> `jbcrypt-0.4.jar`  [INFERRED] [semantically similar]
  src/main/java/br/edu/sistemaescala/backend/repository/jdbc/UsuarioRepositoryJdbc.java → documentacao/seguranca/dependency-check-report.html
- `VitrineComponentesApp` --semantically_similar_to--> `Prototipo Visual das Telas (HTML standalone)`  [INFERRED] [semantically similar]
  src/main/java/br/edu/sistemaescala/frontend/VitrineComponentesApp.java → prototipo/index.html
- `Issue #59 — Proteger o Arquivo do Banco de Dados` --references--> `ConexaoBanco`  [AMBIGUOUS]
  /home/jb/Prototipo-de-Automaco-de-Escala/scripts/BACKLOG_ISSUES.md → src/main/java/br/edu/sistemaescala/backend/dao/ConexaoBanco.java
- `Legado README Overview` --conceptually_related_to--> `ConexaoBanco`  [INFERRED]
  /home/jb/Prototipo-de-Automaco-de-Escala/documentacao/legado/README.md → src/main/java/br/edu/sistemaescala/backend/dao/ConexaoBanco.java
- `Issue #1 — Decidir SGBD (PostgreSQL vs H2)` --rationale_for--> `ConexaoBanco`  [INFERRED]
  /home/jb/Prototipo-de-Automaco-de-Escala/scripts/BACKLOG_ISSUES.md → src/main/java/br/edu/sistemaescala/backend/dao/ConexaoBanco.java

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **Fluxo de Primeiro Acesso (configuracao inicial do sistema)** — sistemaescala_main_main, service_primeiroacessoservice_primeiroacessoservice, service_primeiroacessoserviceimpl_primeiroacessoserviceimpl, controller_primeiroacessocontroller_primeiroacessocontroller, service_autenticacaoservice_autenticacaoservice [INFERRED 0.85]
- **Padrao de Servico de Autenticacao (interface + impl + testes + repositorio)** — service_autenticacaoservice_autenticacaoservice, service_autenticacaoserviceimpl_autenticacaoserviceimpl, service_autenticacaoserviceimpltest_autenticacaoserviceimpltest, repository_usuariorepository_usuariorepository [INFERRED 0.85]
- **Documentacao de Seguranca do Banco Local + Implementacao** — seguranca_modelagemameacas_decisaoaes, seguranca_protecaobanco_protecaoh2, dao_conexaobanco_conexaobanco [INFERRED 0.85]

## Communities (33 total, 9 thin omitted)

### Community 0 - "Modelo de Dados da Escala"
Cohesion: 0.06
Nodes (34): configuracao table, Diagrama MER (Entity-Relationship Diagram), funcionario table, tipo_turno table, usuario table, EscalaFuncionario, EscalaTurno, EscalaFuncionarioRepository (+26 more)

### Community 1 - "Repositorio de Lancamento de Horas"
Cohesion: 0.05
Nodes (35): LocalDate, LancamentoHoras, deValor(), TipoLancamento(), toString(), valor(), LancamentoHorasRepository, Funcionario (+27 more)

### Community 2 - "Repositorio de Tipo de Turno"
Cohesion: 0.06
Nodes (27): LocalTime, TipoTurno, TipoTurnoRepository, BigDecimal, Integer, LocalDateTime, Object, Override (+19 more)

### Community 3 - "Repositorio de Usuario (JDBC)"
Cohesion: 0.08
Nodes (22): AfterAll, BeforeAll, Connection, UsuarioRepositoryJdbc.mapear(ResultSet), UsuarioRepositoryJdbc.preencherInsercao(PreparedStatement, Usuario), Usuario, PreparedStatement, UsuarioConfiguracaoRepositoryJdbcTest (+14 more)

### Community 4 - "Bootstrap da Aplicacao e Auth"
Cohesion: 0.06
Nodes (32): Application, AutenticacaoService, Prototipo de Referencia Rule, Tema CSS Obrigatorio Rule, ConfiguracaoRepository, PrimeiroAcessoController, LinhaExemplo, VitrineComponentesApp (+24 more)

### Community 5 - "Repositorio de Escala de Turno"
Cohesion: 0.08
Nodes (26): Map, EscalaTurnoRepository, EscalaTurno, List, LocalDateTime, Optional, YearMonth, EscalaFuncionario (+18 more)

### Community 6 - "Servico de Autenticacao"
Cohesion: 0.07
Nodes (21): LongConsumer, RepositoryException, RuntimeException, AutenticacaoService, AutenticacaoServiceImplTest, SenhaFracaException, SenhaInvalidaException, SenhasNaoConferemException (+13 more)

### Community 7 - "Configuracao e Seed de Dados"
Cohesion: 0.09
Nodes (22): Tabela configuracao, seed.sql (production seed), seed-teste.sql (test fixtures), Configuracao, ConfiguracaoRepository, Issue #70 — Tela de Configurações da Organização, Issue #8 — Criar Classes de Domínio, BigDecimal (+14 more)

### Community 8 - "Conexao e Senha do Banco"
Cohesion: 0.07
Nodes (35): ConexaoBanco, UsuarioRepositoryJdbc.atualizar(Usuario), UsuarioRepositoryJdbc.atualizarSenha(int, String), UsuarioRepositoryJdbc.buscarPorLogin(String), UsuarioRepositoryJdbc.executarAtualizacao(String, String, int), UsuarioRepositoryJdbc.inserir(Usuario), UsuarioRepositoryJdbc.registrarUltimoLogin(int, LocalDateTime), UsuarioRepositoryJdbc.verificarAtualizacao(int, String) (+27 more)

### Community 9 - "Repositorio de Motivo de Cobertura"
Cohesion: 0.10
Nodes (16): MotivoCoberturaRepository, Boolean, List, MotivoCobertura, Optional, Override, PreparedStatement, ResultSet (+8 more)

### Community 10 - "Entidade EscalaTurno"
Cohesion: 0.13
Nodes (8): EscalaTurno, EscalaFuncionario, Integer, List, LocalDateTime, Object, Override, String

### Community 11 - "Inicializacao do Banco (Schema)"
Cohesion: 0.11
Nodes (17): Tabela motivo_cobertura, schema.sql (script de criação do banco), Tabela tipo_turno, Tabela usuario, BancoInicializador, BancoInicializador.executarScript(), BancoInicializador.inicializar(), Serviço Postgres do Docker Compose Legado (+9 more)

### Community 12 - "Entidade Funcionario"
Cohesion: 0.16
Nodes (6): Funcionario, Integer, LocalDateTime, Object, Override, String

### Community 13 - "Entidade MotivoCobertura"
Cohesion: 0.20
Nodes (8): deValor(), MotivoCobertura(), toString(), valor(), Integer, Object, Override, String

### Community 14 - "Interface FuncionarioRepository"
Cohesion: 0.16
Nodes (8): FuncionarioRepository, Boolean, Funcionario, Integer, List, Optional, String, YearMonth

### Community 15 - "Testes de FuncionarioRepository"
Cohesion: 0.25
Nodes (6): FuncionarioRepositoryJdbcTest, AfterAll, Connection, Object, String, Test

### Community 16 - "Impl JDBC de FuncionarioRepository"
Cohesion: 0.20
Nodes (9): Boolean, Funcionario, List, Optional, ResultSet, YearMonth, BeforeAll, Funcionario (+1 more)

### Community 17 - "Metodos de FuncionarioRepositoryJdbc"
Cohesion: 0.27
Nodes (4): FuncionarioRepositoryJdbc, Integer, Override, String

### Community 18 - "Interface UsuarioRepository"
Cohesion: 0.21
Nodes (6): UsuarioRepository, List, LocalDateTime, Optional, String, Usuario

### Community 19 - "Script de Sincronizacao de Issues"
Cohesion: 0.36
Nodes (8): api_saudavel(), executar(), main(), mapear_existentes(), montar_corpo(), parse_backlog(), Casa GitHub -> backlog pelo rodape; cai para o titulo se faltar rodape., rodar()

### Community 20 - "Permissoes e Convencoes de Git"
Cohesion: 0.29
Nodes (6): permissions, allow, Convenção de Nomenclatura de Branches, Conventional Commits, Fluxo de Pull Request e Code Review, Issue #5 — Padronizar Fluxo de Git e Guia de Contribuição

### Community 21 - "Enum RoleUsuario"
Cohesion: 0.48
Nodes (6): deValor(), RoleUsuario(), toString(), valor(), Override, String

### Community 22 - "CI e Verificacao de Dependencias"
Cohesion: 0.40
Nodes (5): Dependabot Configuration, Issue #6 — Configurar Build e Testes no GitHub Actions, Issue #65 — Verificação de Dependências Vulneráveis no CI, Build CI Workflow, Dependency-Check Security Workflow

### Community 23 - "Requisitos de Conflito de Horario"
Cohesion: 0.40
Nodes (5): RF05 — Validar Conflito de Horário, RF10 — Mínimo de 2 Agentes por Plantão, RNF06 — Bloquear Conflito de Horário Não Resolvido, RNF08 — Bloquear Violação de Mínimo de Agentes / Descanso, Issue #26 — Mínimo de Agentes e Bloqueio de Duplicidade (RF05, RF10)

## Ambiguous Edges - Review These
- `ConexaoBanco` → `Issue #59 — Proteger o Arquivo do Banco de Dados`  [AMBIGUOUS]
  /home/jb/Prototipo-de-Automaco-de-Escala/scripts/BACKLOG_ISSUES.md · relation: references

## Knowledge Gaps
- **81 isolated node(s):** `allow`, `String`, `Object`, `Object`, `Object` (+76 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **9 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **What is the exact relationship between `ConexaoBanco` and `Issue #59 — Proteger o Arquivo do Banco de Dados`?**
  _Edge tagged AMBIGUOUS (relation: references) - confidence is low._
- **Why does `EscalaFuncionario` connect `Modelo de Dados da Escala` to `Repositorio de Lancamento de Horas`, `Entidade Funcionario`, `Repositorio de Escala de Turno`?**
  _High betweenness centrality (0.256) - this node is a cross-community bridge._
- **Why does `Map` connect `Repositorio de Escala de Turno` to `Servico de Autenticacao`?**
  _High betweenness centrality (0.164) - this node is a cross-community bridge._
- **Why does `LocalTime` connect `Repositorio de Tipo de Turno` to `Repositorio de Escala de Turno`?**
  _High betweenness centrality (0.117) - this node is a cross-community bridge._
- **Are the 2 inferred relationships involving `EscalaFuncionario` (e.g. with `.mapear()` and `.salvar()`) actually correct?**
  _`EscalaFuncionario` has 2 INFERRED edges - model-reasoned connections that need verification._
- **Are the 2 inferred relationships involving `Configuracao` (e.g. with `seed.sql (production seed)` and `seed-teste.sql (test fixtures)`) actually correct?**
  _`Configuracao` has 2 INFERRED edges - model-reasoned connections that need verification._
- **What connects `allow`, `Casa GitHub -> backlog pelo rodape; cai para o titulo se faltar rodape.`, `String` to the rest of the system?**
  _89 weakly-connected nodes found - possible documentation gaps or missing edges._