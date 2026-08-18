# Backlog de Issues — Sistema de Automação de Escala

Cada bloco abaixo vira uma issue no GitHub. O script `criar_issues.py` lê este arquivo e
cria tudo automaticamente — mantenha o formato dos cabeçalhos (`### #N · título`) e das
linhas de metadados.

**Labels a criar:** `decisao`, `setup`, `banco`, `backend`, `ui`, `regra-de-negocio`,
`escala`, `cobertura`, `banco-de-horas`, `pdf`, `dashboard`, `auth`, `testes`, `docs`,
`empacotamento`, `futuro`, `bloqueante`

**Milestones:** `M0 — Fundação`, `M1 — Dados e autenticação`, `M2 — Shell e usuários`,
`M3 — Funcionários e equipes`, `M4 — Escala`, `M5 — Coberturas e banco de horas`,
`M6 — PDF e dashboard`, `M7 — Qualidade e entrega`, `Futuro`

---

## M0 — Fundação

### #1 · [Decisão] Decidir o SGBD de produção (PostgreSQL vs. H2 embarcado)
- **Milestone:** M0 — Fundação
- **Labels:** decisao, banco, bloqueante
- **Responsável:** Joao-Bosco-Neto
- **Depende de:** —

## 🎯 Objetivo e Contexto

O RNF02 exige PostgreSQL, mas o produto é offline e monousuário, instalado na máquina do
gestor. Um servidor PostgreSQL na máquina do cliente traz instalador separado, serviço no
Windows, porta e senha de superusuário — o maior risco de o sistema não subir no dia da
entrega. As telas do protótipo, aliás, mostram `escala.db` no rodapé.

## 💼 Regras e Considerações

Confirmar com o professor se PostgreSQL é exigência rígida de nota. Se for, mantemos e
reservamos tempo para o instalador. Se houver flexibilidade, H2 embarcado (dependência
Maven, arquivo único, sem instalação) é a escolha adequada. Em qualquer cenário o acesso
a dados fica atrás de interfaces de repositório e as migrações no Flyway.

## ✅ Critérios de Aceite

- [ ] Decisão registrada em comentário nesta issue com a justificativa
- [ ] `README.md` atualizado com o banco escolhido
- [ ] Se PostgreSQL: definido como será instalado na máquina do cliente

---

### #2 · [Decisão] Congelar o escopo do protótipo e registrar o backlog futuro
- **Milestone:** M0 — Fundação
- **Labels:** decisao, bloqueante
- **Responsável:** Joao-Bosco-Neto
- **Depende de:** —

## 🎯 Objetivo e Contexto

Com 28 dias e três desenvolvedores, escopo aberto é o risco número um. Fechar por escrito
o que entra e o que fica de fora, e levar ao cliente para validação.

## 💼 Regras e Considerações

Fora do protótipo: meio plantão, escala de sobreaviso, múltiplos tipos de turno na
interface, assinatura digital com certificado, acesso dos agentes, pedido de troca de dia,
operação em rede.

Pendências a confirmar com o cliente: mínimo de agentes por plantão é 2 ou 3 (o cliente
disse 2, as telas tratam menos de 3 como incompleto)? O banco de horas zera todo mês ou
acumula no ano (respostas conflitantes)?

## ✅ Critérios de Aceite

- [ ] Documento de escopo aprovado pelos três integrantes
- [ ] Dúvidas do mínimo de agentes e do período do banco de horas respondidas pelo cliente
- [ ] Itens fora de escopo criados como issues no milestone `Futuro`

---

### #3 · [Banco] Implementar o schema do banco de dados
- **Milestone:** M0 — Fundação
- **Labels:** banco, bloqueante
- **Responsável:** Krisbrn
- **Depende de:** #1

## 🎯 Objetivo e Contexto

As telas exigem três estruturas que o schema atual não tem: equipes de rodízio (A/B/C/D),
campos de motivo/observação na cobertura, e um extrato de banco de horas (a coluna
`funcionario.horas_banco` não sustenta o "Ver extrato" nem o estorno quando uma cobertura
é excluída).

## 💼 Regras e Considerações

O SQL completo está em `PLANO_DE_PROJETO.md`, seção 4. Principais mudanças: nova tabela
`equipe`; `funcionario` ganha `equipe_id`, `cpf`, `data_admissao`, `observacoes` e perde
`horas_banco`; `escala_turno` ganha `equipe_id`; `escala_funcionario` ganha
`motivo_cobertura`, `observacao_cobertura` e `lancou_banco_horas`; nova tabela
`lancamento_horas`.

## 🔧 Contrato Técnico

```sql
-- src/main/resources/db/migration/V1__schema.sql
-- 11 tabelas: configuracao, usuario, equipe, funcionario, tipo_turno,
-- escala_turno, motivo_cobertura, escala_funcionario, lancamento_horas,
-- funcionario_tipo_turno, escala_excecao
-- Regras de portabilidade: sem ENUM, sem SERIAL, sem NOW(), sem TEXT
```
Arquivo completo já validado em PostgreSQL 16 — ver `V1__schema.sql`.

## ✅ Critérios de Aceite

- [ ] `schema.sql` em `src/main/resources/banco/` como fonte única (remover `banco/import.sql`)
- [ ] `usuario.login` separado de `nome`
- [ ] `funcionario.matricula` NOT NULL UNIQUE
- [ ] `tipo_turno.hora_inicio` (sem ela, 12x36 é impossível de representar)
- [ ] `max_agentes` em `tipo_turno` e `escala_turno`
- [ ] Tabelas `motivo_cobertura` e `lancamento_horas`
- [ ] `ON DELETE CASCADE` em `escala_funcionario` (senão "Limpar mês" falha)
- [ ] Script idempotente, roda duas vezes sem erro
- [ ] Diagrama MER atualizado

---

### #4 · [Setup] Criar a estrutura do projeto Maven com Java 21 e JavaFX 21
- **Milestone:** M0 — Fundação
- **Labels:** setup, bloqueante
- **Responsável:** Joao-Bosco-Neto
- **Depende de:** —

## 🎯 Objetivo e Contexto

Projeto Maven com `javafx-maven-plugin`, dependências (driver do banco, Flyway, BCrypt,
PDFBox, JUnit 5) e a estrutura de pacotes definida no plano: `config`, `model`,
`repository`, `service`, `controller`, `view`, `util`, mais `resources/fxml`,
`resources/css` e `resources/db/migration`.

## 💼 Regras e Considerações

Entregar uma janela JavaFX vazia que abre com `mvn javafx:run` — é o "hello world" que
destrava todo mundo.

## ✅ Critérios de Aceite

- [ ] `mvn clean install` passa
- [ ] `mvn javafx:run` abre uma janela (o `start()` não pode ficar vazio)
- [ ] Estrutura de pacotes criada e commitada
- [ ] Instruções de execução no README

---

### #5 · [Docs] Padronizar o fluxo de Git e adicionar o guia de contribuição
- **Milestone:** M0 — Fundação
- **Labels:** docs, setup
- **Responsável:** Joao-Bosco-Neto
- **Depende de:** —

## 🎯 Objetivo e Contexto

Adicionar `GUIA_DE_CONTRIBUICAO.md` na raiz, criar a branch `develop` a partir da `main`,
proteger ambas (sem push direto, PR obrigatório com pelo menos uma aprovação) e adicionar
`.gitignore` para Java/Maven/IDEs.

## ✅ Critérios de Aceite

- [ ] `GUIA_DE_CONTRIBUICAO.md` na raiz do repositório
- [ ] Branch `develop` criada e definida como padrão
- [ ] Regras de proteção ativas em `main` e `develop`
- [ ] `.gitignore` cobrindo `target/`, `.idea/`, `*.iml`, `.vscode/`, arquivos locais de banco

---

### #6 · [Setup] Configurar build e testes automáticos no GitHub Actions
- **Milestone:** M0 — Fundação
- **Labels:** setup
- **Responsável:** DaviRSuassuna
- **Depende de:** #4

## 🎯 Objetivo e Contexto

Workflow que roda `mvn clean verify` em todo push e pull request para `develop` e `main`,
com JDK 21. Serve para ninguém mesclar código que não compila.

## ✅ Critérios de Aceite

- [ ] `.github/workflows/build.yml` criado
- [ ] Workflow verde em um PR de teste
- [ ] Badge do build no README

---

### #7 · [Banco] Configurar conexão H2 e inicialização automática do banco
- **Milestone:** M0 — Fundação
- **Labels:** banco, backend
- **Responsável:** Krisbrn
- **Depende de:** #3, #4

## 🎯 Objetivo e Contexto

Sem isto, **nenhuma issue de repositório (#9 a #12) consegue sequer ser testada**: o H2
cria um arquivo vazio e qualquer consulta falha com "table not found".

O Flyway foi descartado: a inicialização é feita por uma classe própria
(`BancoInicializador`) que executa `schema.sql` e `seed.sql` do classpath na partida da
aplicação. Os dois scripts são idempotentes, então rodar de novo não quebra nem duplica.

## 💼 Regras e Considerações

O seed é o que permite as outras frentes desenvolverem sem depender da tela de cadastro.

## ✅ Critérios de Aceite

- [ ] `BancoInicializador` executa schema e seed na partida
- [ ] Aplicação sobe com banco vazio e cria tudo sozinha
- [ ] Executar duas vezes não gera erro nem duplica dados
- [ ] Falha na inicialização exibe mensagem clara, não stack trace

---

### #8 · [Backend] Criar as classes de domínio
- **Milestone:** M1 — Dados e autenticação
- **Labels:** backend
- **Responsável:** Krisbrn
- **Depende de:** #3

## 🎯 Objetivo e Contexto

Classes de domínio espelhando o schema: `Usuario`, `Configuracao`, `Equipe`,
`Funcionario`, `TipoTurno`, `EscalaTurno`, `EscalaFuncionario`, `LancamentoHoras`, mais os
enums `RoleUsuario`, `MotivoCobertura`, `TipoLancamento`.

## 💼 Regras e Considerações

Usar `LocalDate` e `LocalDateTime` (nunca `java.util.Date`). Como as telas mostram dados
em `TableView`, avaliar propriedades JavaFX nos campos exibidos em tabela.

## ✅ Critérios de Aceite

- [ ] Todas as classes criadas em `model/`
- [ ] Enums alinhados com os tipos do banco
- [ ] `equals`, `hashCode` e `toString` implementados

---

### #9 · [Banco] Repositórios de Usuario e Configuracao
- **Milestone:** M1 — Dados e autenticação
- **Labels:** backend, banco
- **Responsável:** Krisbrn
- **Depende de:** #7, #8

## 🎯 Objetivo e Contexto

Interface + implementação JDBC. `UsuarioRepository`: buscar por nome, listar, inserir,
atualizar, desativar, atualizar senha, registrar último login. `ConfiguracaoRepository`:
ler e atualizar o registro único.

## 💼 Regras e Considerações

Sempre `PreparedStatement` — nada de concatenar SQL.

## ✅ Critérios de Aceite

- [ ] Interfaces em `repository/` e implementações em `repository/jdbc/`
- [ ] Operações de CRUD funcionando contra o banco real
- [ ] Zero concatenação de string em SQL

---

### #10 · [Banco] Repositório de Funcionario
- **Milestone:** M1 — Dados e autenticação
- **Labels:** backend, banco
- **Responsável:** Krisbrn
- **Depende de:** #7, #8

## 🎯 Objetivo e Contexto

`FuncionarioRepository`: listar com filtro de status e busca por nome ou matrícula,
buscar por id, inserir, atualizar, ativar/desativar, verificar matrícula duplicada, e uma
consulta agregada de plantões no mês (coluna "Plantões/mês" da tela).
Equipes de rodízio saíram do modelo: a organização passou a ser por tipo de turno.

## ✅ Critérios de Aceite

- [ ] Busca combinando texto livre e filtro de status
- [ ] Contagem de plantões no mês por funcionário
- [ ] Desativação preservando o histórico de plantões

---

### #11 · [Escala] Repositórios de escala
- **Milestone:** M1 — Dados e autenticação
- **Labels:** backend, banco, escala
- **Responsável:** Joao-Bosco-Neto
- **Depende de:** #7, #8

## 🎯 Objetivo e Contexto

`TipoTurnoRepository` (CRUD simples), `EscalaTurnoRepository` (buscar turnos de um
intervalo de datas, inserir, atualizar, remover turnos de um mês) e
`EscalaFuncionarioRepository` (listar por turno, listar por funcionário em intervalo,
inserir, remover, buscar coberturas do mês).

## 💼 Regras e Considerações

A consulta mais importante do sistema é "todos os turnos de um mês com seus agentes e
coberturas" — ela alimenta o calendário, o PDF e o dashboard. Vale otimizá-la desde já
para não fazer N+1 consultas.

## 🔧 Contrato Técnico

```java
public interface EscalaTurnoRepository {
    List<EscalaTurno> buscarPorPeriodo(LocalDateTime inicio, LocalDateTime fim);
    Optional<EscalaTurno> buscarPorId(int id);
    EscalaTurno salvar(EscalaTurno turno);
    void removerPorMes(YearMonth mes);
}
```
A consulta de um mês inteiro alimenta o calendário, o PDF e o dashboard — deve trazer
turnos, agentes e coberturas sem cair em N+1.

## ✅ Critérios de Aceite

- [ ] Consulta de um mês inteiro traz turnos, agentes e coberturas
- [ ] Remoção em lote dos turnos de um mês ("Limpar mês")
- [ ] Consulta de plantões de um funcionário em intervalo (base da regra de descanso)

---

### #12 · [Banco de Horas] Repositório de lançamentos do banco de horas
- **Milestone:** M1 — Dados e autenticação
- **Labels:** backend, banco, banco-de-horas
- **Responsável:** DaviRSuassuna
- **Depende de:** #7, #8

## 🎯 Objetivo e Contexto

`LancamentoHorasRepository`: inserir, remover por `escala_funcionario_id` (estorno quando
uma cobertura é excluída), listar por funcionário e período (extrato), e somar saldo por
funcionário em um período.

## ✅ Critérios de Aceite

- [ ] Soma de saldo por funcionário filtrada por período
- [ ] Extrato ordenado por data
- [ ] Estorno remove os lançamentos vinculados

---

### #13 · [Auth] Serviço de autenticação com BCrypt
- **Milestone:** M1 — Dados e autenticação
- **Labels:** auth, backend, regra-de-negocio
- **Responsável:** Krisbrn
- **Depende de:** #9

## 🎯 Objetivo e Contexto

Serviço com `autenticar(usuario, senha)`, geração de hash com `at.favre.lib:bcrypt` (custo
12) e alteração de senha com verificação da senha atual. Nunca armazenar ou logar a senha
em texto puro. Usuário inativo não autentica.

## 🔧 Contrato Técnico

```java
public interface AutenticacaoService {
    Optional<Usuario> autenticar(String nome, String senha);
    String gerarHash(String senhaPura);
    void alterarSenha(int usuarioId, String senhaAtual, String senhaNova);
}
```
A senha em texto puro nunca é gravada, retornada ou registrada em log.

## ✅ Critérios de Aceite

- [ ] Senha gravada apenas como hash BCrypt
- [ ] Mensagem de erro genérica no login (não revelar se o usuário existe)
- [ ] Usuário inativo bloqueado
- [ ] Testes unitários do serviço

---

### #14 · [Auth] Criar o administrador inicial no primeiro acesso
- **Milestone:** M1 — Dados e autenticação
- **Labels:** auth, backend
- **Responsável:** Krisbrn
- **Depende de:** #13

## 🎯 Objetivo e Contexto

Sendo offline e sem cadastro externo, o sistema precisa resolver o primeiro acesso. Se não
houver nenhum usuário no banco, exibir uma tela de configuração inicial pedindo nome da
organização, usuário e senha do administrador.

## 💼 Regras e Considerações

Nada de usuário `admin/admin` fixo no código.

## ✅ Critérios de Aceite

- [ ] Tela de primeiro acesso quando a tabela `usuario` está vazia
- [ ] Senha com mínimo de 8 caracteres e confirmação
- [ ] Nome da organização gravado em `configuracao`
- [ ] Fluxo não aparece nas inicializações seguintes

---

### #15 · [Auth] Tela de login
- **Milestone:** M2 — Shell e usuários
- **Labels:** ui, auth
- **Responsável:** Krisbrn
- **Depende de:** #13, #17

## 🎯 Objetivo e Contexto

Reproduzir o layout da tela 7: painel lateral escuro com identificação do sistema e painel
de credenciais à direita, com "Lembrar meu usuário nesta estação" e rodapé com versão e
estação.

## 💼 Regras e Considerações

O "Esqueci minha senha" da tela não funciona em sistema offline — substituir por um texto
orientando a procurar o administrador (o reset vem na #20). O "lembrar usuário" guarda
apenas o nome de usuário em preferências locais, jamais a senha.

## ✅ Critérios de Aceite

- [ ] Layout fiel ao protótipo
- [ ] Enter no campo de senha submete
- [ ] Erro exibido sem travar a tela
- [ ] "Lembrar usuário" persiste só o nome de usuário
- [ ] Login bem-sucedido abre a janela principal

---

### #16 · [UI] Shell da aplicação com menu, navegação lateral e barra de status
- **Milestone:** M2 — Shell e usuários
- **Labels:** ui, bloqueante
- **Responsável:** Joao-Bosco-Neto
- **Depende de:** #4

## 🎯 Objetivo e Contexto

Esqueleto que todas as telas vão usar: barra de título com organização, usuário logado,
data/hora e botão sair; menu superior (Arquivo, Cadastros, Escala, Relatórios, Ajuda);
navegação lateral com os seis itens; área central de conteúdo; rodapé com regime
configurado, status do banco e data da última exportação.

## 💼 Regras e Considerações

Inclui o roteador de telas — carregar FXML na área central mantendo a navegação. **Esta
issue destrava todas as telas; priorizar.**

## ✅ Critérios de Aceite

- [ ] Shell renderiza igual ao protótipo
- [ ] Navegação troca o conteúdo central e marca o item ativo
- [ ] Barra de status lê dados reais (organização, usuário, banco)
- [ ] Sair volta ao login limpando a sessão

---

### #17 · [UI] Tema CSS base e componentes reutilizáveis
- **Milestone:** M2 — Shell e usuários
- **Labels:** ui
- **Responsável:** Joao-Bosco-Neto
- **Depende de:** #4

## 🎯 Objetivo e Contexto

Folha CSS com a paleta do protótipo (azul institucional, cinzas, verde/amarelo/vermelho
de status), tipografia, e estilos de botão primário/secundário/perigo, tabela, campo de
formulário, card de indicador e selo de status.

## 💼 Regras e Considerações

Sem isso, cada dev inventa um estilo e as telas ficam visualmente inconsistentes.

## ✅ Critérios de Aceite

- [ ] `app.css` com variáveis de cor documentadas
- [ ] Classes para botões, tabelas, campos, cards e selos
- [ ] Tela de exemplo demonstrando os componentes

---

### #18 · [Auth] Sessão do usuário e controle de acesso por perfil
- **Milestone:** M2 — Shell e usuários
- **Labels:** auth, backend
- **Responsável:** Krisbrn
- **Depende de:** #15, #16

## 🎯 Objetivo e Contexto

Sessão em memória com o usuário logado, acessível pelas telas. Perfil `gestor` usa tudo
menos a gestão de usuários; `admin` usa tudo. Itens de menu indisponíveis ficam ocultos ou
desabilitados.

## ✅ Critérios de Aceite

- [ ] Sessão acessível a partir de qualquer controller
- [ ] Gestão de usuários visível apenas para `admin`
- [ ] Verificação também no serviço, não só na interface

---

### #19 · [UI] Tratamento global de erros e diálogos padronizados
- **Milestone:** M2 — Shell e usuários
- **Labels:** ui, backend
- **Responsável:** Joao-Bosco-Neto
- **Depende de:** #16

## 🎯 Objetivo e Contexto

Utilitário de diálogos (informação, confirmação, erro) e um handler global que captura
exceções não tratadas, grava em log e mostra mensagem amigável — em vez de a janela
congelar em frente ao cliente.

## 💼 Regras e Considerações

Erro de banco fora do ar precisa de mensagem específica orientando o usuário.

## ✅ Critérios de Aceite

- [ ] Classe utilitária de diálogos usada por todas as telas
- [ ] Handler global registrado com log em arquivo
- [ ] Falha de conexão com o banco exibe mensagem orientativa

---

### #20 · [Auth] Gestão de usuários do sistema (RF12)
- **Milestone:** M2 — Shell e usuários
- **Labels:** ui, auth
- **Responsável:** Krisbrn
- **Depende de:** #18

## 🎯 Objetivo e Contexto

Tela restrita ao administrador: listar usuários, cadastrar, editar, ativar/desativar,
definir perfil e redefinir senha (substitui o "esqueci minha senha" da tela de login).

## 💼 Regras e Considerações

O administrador não pode desativar a si mesmo nem remover o último admin ativo — senão o
sistema fica sem acesso e é preciso mexer no banco na mão.

## ✅ Critérios de Aceite

- [ ] CRUD completo de usuários
- [ ] Reset de senha pelo admin
- [ ] Bloqueio de autodesativação e da remoção do último admin
- [ ] Tela inacessível para perfil `gestor`

---

### #21 · [UI] Listagem de funcionários com busca e filtro
- **Milestone:** M3 — Funcionários e equipes
- **Labels:** ui
- **Responsável:** Krisbrn
- **Depende de:** #10, #16

## 🎯 Objetivo e Contexto

Tabela conforme a tela 2: matrícula, nome, telefone, admissão, plantões/mês, status e
ações. Campo de busca por nome ou matrícula, filtro por status e contador de registros.
Inativos aparecem com nome em cinza e botão "Reativar".

## ✅ Critérios de Aceite

- [ ] Colunas conforme o protótipo
- [ ] Busca filtrando por nome e matrícula
- [ ] Filtro de status funcionando
- [ ] Contador de registros atualizado

---

### #22 · [UI] Formulário de cadastro e edição de funcionário
- **Milestone:** M3 — Funcionários e equipes
- **Labels:** ui, regra-de-negocio
- **Responsável:** Krisbrn
- **Depende de:** #21

## 🎯 Objetivo e Contexto

Formulário lateral com nome, matrícula, CPF, telefone, data de admissão, equipe de rodízio
e observações. Validações: nome e matrícula obrigatórios, matrícula única (mensagem clara
no caso de duplicata), CPF válido quando preenchido, máscara de telefone, data de admissão
não futura. Botões salvar e limpar; ao editar, o formulário carrega os dados.

## ✅ Critérios de Aceite

- [ ] Cadastro e edição funcionando
- [ ] Matrícula duplicada rejeitada com mensagem específica
- [ ] CPF validado por dígito verificador quando informado
- [ ] Erros exibidos junto do campo, não em popup genérico

---

### #23 · [UI] Ativar e desativar funcionário preservando o histórico
- **Milestone:** M3 — Funcionários e equipes
- **Labels:** regra-de-negocio, ui
- **Responsável:** Krisbrn
- **Depende de:** #22

## 🎯 Objetivo e Contexto

Desativação lógica com confirmação. Funcionário inativo não aparece na atribuição de
plantões nem em coberturas, mas continua nos plantões passados. Se ele estiver escalado em
plantão futuro, avisar antes de desativar — é a origem do alerta "Equipe D opera com 2
agentes desde então" do dashboard.

## ✅ Critérios de Aceite

- [ ] Desativação pede confirmação
- [ ] Aviso quando há plantões futuros do funcionário
- [ ] Inativo sumido das listas de atribuição
- [ ] Histórico de plantões intacto

---

### #24 · [Escala] CRUD de tipos de turno
- **Milestone:** M3 — Funcionários e equipes
- **Labels:** ui, escala
- **Responsável:** Krisbrn
- **Depende de:** #10, #16

## 🎯 Objetivo e Contexto

CRUD de tipos de turno: nome, hora de início, duração, intervalo de descanso, mínimo e
máximo de agentes, e se conta banco de horas. É esta tela que define o regime de trabalho
da organização — 24x72, 12x36, 5x2 ou sobreaviso.

## 💼 Regras e Considerações

Inativar um tipo de turno não pode apagar os turnos já gerados com ele — o histórico da
escala precisa continuar legível.

Validar a coerência do regime: se `duracao_horas + intervalo_descanso_horas` for maior que
o ciclo possível com o número de funcionários ativos, o descanso fica impossível de
cumprir. Vale avisar na tela em vez de deixar o gerador falhar depois.

## ✅ Critérios de Aceite

- [ ] CRUD de tipos de turno funcionando
- [ ] Campos de duração, descanso, mínimo e máximo de agentes editáveis
- [ ] Inativar tipo não afeta turnos já criados
- [ ] Aviso quando o regime é inviável para o efetivo disponível

---

### #25 · [Escala] Validação do intervalo de descanso obrigatório (RF11)
- **Milestone:** M4 — Escala
- **Labels:** regra-de-negocio, escala, backend
- **Responsável:** Joao-Bosco-Neto
- **Depende de:** #11

## 🎯 Objetivo e Contexto

Serviço que responde se um funcionário pode ser escalado em um turno, checando o intervalo
de descanso configurado no `tipo_turno` (não fixar 72h no código — o cliente quer o produto
genérico).

## 💼 Regras e Considerações

Regra: quem sai às 08h do dia seguinte só volta 72h depois, ou seja, às 08h do quarto dia.
Verificar tanto o plantão anterior quanto o posterior — escalar alguém no dia 10 pode ser
inválido por causa do dia 12, não só do dia 8.

## 🔧 Contrato Técnico

```java
public interface RegraEscalaService {
    ResultadoValidacao podeSerEscalado(int funcionarioId, EscalaTurno turno);
}

public record ResultadoValidacao(boolean permitido, String motivo) {}
```
O descanso é lido de `tipo_turno.intervalo_descanso_horas` — nunca fixado em 72 no código.
Comparação por intervalo: `fim` do turno anterior até `inicio` do próximo.

## ✅ Critérios de Aceite

- [ ] Método retorna disponível/indisponível com o motivo
- [ ] Intervalo lido do `tipo_turno`, não fixo no código
- [ ] Verifica plantões anteriores e posteriores
- [ ] Testes cobrindo limite exato, dentro e fora do intervalo

---

### #26 · [Escala] Mínimo de agentes por plantão e bloqueio de duplicidade (RF05, RF10)
- **Milestone:** M4 — Escala
- **Labels:** regra-de-negocio, escala, backend
- **Responsável:** Joao-Bosco-Neto
- **Depende de:** #11

## 🎯 Objetivo e Contexto

Duas validações: um funcionário não pode ocupar duas vagas do mesmo turno (já garantido no
banco pela constraint única, mas precisa de mensagem amigável antes do erro de SQL), e o
turno não pode ser salvo com menos agentes que o mínimo definido.

## 💼 Regras e Considerações

Decidir o comportamento do turno incompleto: bloquear o salvamento (RNF06/RNF08) ou
permitir salvar como rascunho e bloquear apenas a exportação do PDF. As telas apontam para
a segunda opção — existem dias marcados como "efetivo incompleto" salvos, e o alerta do
dashboard diz "complete o plantão antes de exportar a escala".

## 🔧 Contrato Técnico

```java
// sobreposição de períodos — primitiva genérica de conflito
boolean haConflito = a.inicio().isBefore(b.fim()) && b.inicio().isBefore(a.fim());
```
Vale para 24x72, 12x36, turno parcial e sobreaviso, sem caso especial por regime.

## ✅ Critérios de Aceite

- [ ] Duplicidade bloqueada com mensagem clara
- [ ] Mínimo de agentes validado a partir de `escala_turno.min_agentes`
- [ ] Comportamento do turno incompleto decidido e documentado
- [ ] Testes unitários das duas regras

---

### #27 · [Escala] Calendário mensal de montagem da escala
- **Milestone:** M4 — Escala
- **Labels:** ui, escala
- **Responsável:** Joao-Bosco-Neto
- **Depende de:** #11, #16

## 🎯 Objetivo e Contexto

Grade de sete colunas (domingo a sábado) com as células do mês, cada uma mostrando o dia,
a equipe e os agentes escalados. Clique seleciona o dia e alimenta o painel lateral.
Navegação entre meses pelas setas.

## 💼 Regras e Considerações

É a tela mais complexa do sistema. Sugestão: `GridPane` com um componente próprio de
célula, em vez de tentar adaptar um `DatePicker`.

## ✅ Critérios de Aceite

- [ ] Grade renderiza o mês com alinhamento correto dos dias da semana
- [ ] Célula mostra dia, equipe e agentes
- [ ] Clique seleciona o dia e destaca visualmente
- [ ] Navegação entre meses recarrega os dados

---

### #28 · [Escala] Painel lateral de atribuição de agentes ao plantão
- **Milestone:** M4 — Escala
- **Labels:** ui, escala
- **Responsável:** Joao-Bosco-Neto
- **Depende de:** #25, #26, #27

## 🎯 Objetivo e Contexto

Painel do dia selecionado: cabeçalho com data e equipe, lista de escalados com contador
(3/3) e botão de remover, e lista de funcionários disponíveis com selo de estado
(`descanso 72h`, `escalado`) vindo das regras da #25 e #26.

## 💼 Regras e Considerações

Funcionário em descanso aparece marcado e não pode ser selecionado — o feedback precisa ser
imediato, ainda na lista, não só depois de tentar salvar.

## ✅ Critérios de Aceite

- [ ] Painel reflete o dia selecionado no calendário
- [ ] Contador de escalados sobre o mínimo do turno
- [ ] Selos de indisponibilidade calculados pelas regras
- [ ] Adicionar e remover atualiza calendário e painel na hora

---

### #29 · [Escala] Gerador automático de rodízio
- **Milestone:** M4 — Escala
- **Labels:** escala, regra-de-negocio, backend
- **Responsável:** Joao-Bosco-Neto
- **Depende de:** #24, #25, #27

## 🎯 Objetivo e Contexto

Botão "Gerar rodízio": percorre os dias do período e, para cada `tipo_turno` ativo, cria o
turno a partir de `hora_inicio` + `duracao_horas` e escala os funcionários disponíveis em
sequência circular, respeitando o `intervalo_descanso_horas` do tipo.

O mesmo laço produz 24x72 (um turno por dia), 12x36 (dois turnos por dia) e 5x2 — o regime
vem da configuração, não do código.

## 💼 Regras e Considerações

Dois detalhes que costumam passar batido: a **continuidade entre meses** (o rodízio de
setembro começa de onde agosto parou, senão a virada do mês quebra o descanso) e o que
fazer quando o mês já tem escala — perguntar se sobrescreve.

## ✅ Critérios de Aceite

- [ ] Gera o período inteiro a partir dos tipos de turno ativos
- [ ] Continuidade correta com o último dia do mês anterior
- [ ] Confirmação antes de sobrescrever mês existente
- [ ] Escala gerada não viola nenhuma regra de descanso
- [ ] Testes cobrindo virada de mês

---

### #30 · [Escala] Salvar escala em transação e limpar mês
- **Milestone:** M4 — Escala
- **Labels:** escala, backend
- **Responsável:** Joao-Bosco-Neto
- **Depende de:** #27, #28

## 🎯 Objetivo e Contexto

"Salvar escala" persiste todas as alterações do mês em uma única transação — se qualquer
turno falhar, nada é gravado. "Limpar mês" remove os turnos do mês com dupla confirmação.

## 💼 Regras e Considerações

Cuidado: limpar mês com coberturas registradas precisa estornar os lançamentos do banco de
horas junto.

## ✅ Critérios de Aceite

- [ ] Salvamento transacional com rollback em caso de erro
- [ ] Aviso de alterações não salvas ao sair da tela
- [ ] "Limpar mês" com dupla confirmação
- [ ] Limpeza estorna lançamentos de banco de horas vinculados

---

### #31 · [Escala] Estados visuais e legenda do calendário
- **Milestone:** M4 — Escala
- **Labels:** ui, escala
- **Responsável:** Joao-Bosco-Neto
- **Depende de:** #27

## 🎯 Objetivo e Contexto

Quatro estados do protótipo com cores distintas: plantão completo, cobertura registrada
(amarelo, com `Fulano → Beltrano`), efetivo incompleto (vermelho, com etiqueta) e dia
selecionado (borda azul). Legenda no rodapé da grade.

## 💼 Regras e Considerações

Não depender só de cor: manter a etiqueta textual "EFETIVO INCOMPLETO" para quem tem
dificuldade de distinguir cores.

## ✅ Critérios de Aceite

- [ ] Quatro estados renderizados via classes CSS
- [ ] Cobertura exibida no formato `ausente → substituto`
- [ ] Legenda presente
- [ ] Estado combinado (incompleto + cobertura) tratado

---

### #32 · [Escala] Navegação entre meses e carregamento de escala salva
- **Milestone:** M4 — Escala
- **Labels:** escala, ui
- **Responsável:** Joao-Bosco-Neto
- **Depende de:** #30

## 🎯 Objetivo e Contexto

Trocar de mês carrega a escala já salva; mês sem escala abre vazio, pronto para montar.
Alertar sobre alterações pendentes antes de trocar. Carregar mês com muitos registros não
pode travar a interface — usar `Task` em thread separada se necessário.

## ✅ Critérios de Aceite

- [ ] Navegação carrega dados persistidos
- [ ] Mês sem escala abre vazio sem erro
- [ ] Aviso de alterações não salvas ao navegar
- [ ] Interface não congela durante o carregamento

---

### #33 · [Cobertura] Formulário de registro de cobertura
- **Milestone:** M5 — Coberturas e banco de horas
- **Labels:** ui, cobertura
- **Responsável:** DaviRSuassuna
- **Depende de:** #11, #16

## 🎯 Objetivo e Contexto

Conforme a tela 4: data do plantão, funcionário ausente (só quem está escalado naquela
data, com indicação da equipe), funcionário que irá cobrir (com selo de disponibilidade),
motivo, observações e checkbox de lançamento no banco de horas.

## 💼 Regras e Considerações

Escolher a data deve recarregar a lista de ausentes — só faz sentido oferecer quem está
escalado ali.

## ✅ Critérios de Aceite

- [ ] Lista de ausentes filtrada pela data escolhida
- [ ] Substitutos com selo de disponibilidade
- [ ] Motivo e observações gravados
- [ ] Checkbox de lançamento marcado por padrão

---

### #34 · [Cobertura] Listagem de coberturas do mês com edição e exclusão
- **Milestone:** M5 — Coberturas e banco de horas
- **Labels:** ui, cobertura
- **Responsável:** DaviRSuassuna
- **Depende de:** #33

## 🎯 Objetivo e Contexto

Tabela com data, ausente, quem cobriu, motivo, selo de lançamento no banco de horas e
ações. Editar recarrega o formulário; excluir pede confirmação e estorna os lançamentos.

## ✅ Critérios de Aceite

- [ ] Listagem do mês corrente com contador
- [ ] Edição recarrega o formulário
- [ ] Exclusão confirmada estorna o banco de horas
- [ ] Selo distingue "+24h" de "sem lançamento"

---

### #35 · [Cobertura] Validar disponibilidade do substituto
- **Milestone:** M5 — Coberturas e banco de horas
- **Labels:** regra-de-negocio, cobertura
- **Responsável:** DaviRSuassuna
- **Depende de:** #25, #33

## 🎯 Objetivo e Contexto

Reaproveitar o serviço de regras da #25: o substituto não pode estar em descanso
obrigatório nem já escalado naquela data, e não pode ser o próprio ausente. A tela avisa
que "o sistema bloqueia a seleção de quem já está escalado nesta data".

## ✅ Critérios de Aceite

- [ ] Indisponíveis marcados na lista
- [ ] Seleção de indisponível bloqueada com motivo
- [ ] Ausente não aparece como opção de substituto
- [ ] Validação também no serviço, não só na tela

---

### #36 · [Cobertura] Refletir a cobertura na montagem da escala
- **Milestone:** M5 — Coberturas e banco de horas
- **Labels:** cobertura, escala
- **Responsável:** DaviRSuassuna
- **Depende de:** #31, #33

## 🎯 Objetivo e Contexto

Registrar cobertura marca o dia como "COBERTURA" no calendário e exibe `ausente →
substituto` na linha correspondente. É o que liga as telas 3 e 4.

## ✅ Critérios de Aceite

- [ ] Cobertura registrada aparece no calendário
- [ ] Formato `ausente → substituto` na célula
- [ ] Excluir cobertura reverte a exibição
- [ ] Contagem de agentes do dia continua correta

---

### #37 · [Banco de Horas] Lançamentos automáticos a partir das coberturas
- **Milestone:** M5 — Coberturas e banco de horas
- **Labels:** banco-de-horas, regra-de-negocio
- **Responsável:** DaviRSuassuna
- **Depende de:** #12, #33

## 🎯 Objetivo e Contexto

Com o checkbox marcado, gravar dois lançamentos: crédito para quem cobre e débito para o
ausente, no valor da duração do turno (não fixar 24h — ler do `tipo_turno`). Cobertura e
lançamentos na mesma transação; exclusão estorna ambos.

## ✅ Critérios de Aceite

- [ ] Dois lançamentos gerados com sinais opostos
- [ ] Valor lido da duração do tipo de turno
- [ ] Cobertura sem o checkbox não gera lançamento
- [ ] Exclusão estorna os dois lançamentos

---

### #38 · [Banco de Horas] Tela de saldos do banco de horas
- **Milestone:** M5 — Coberturas e banco de horas
- **Labels:** ui, banco-de-horas
- **Responsável:** DaviRSuassuna
- **Depende de:** #12, #16

## 🎯 Objetivo e Contexto

Tabela da tela 7: matrícula, funcionário, plantões cumpridos, coberturas feitas, plantões
cobertos e saldo, com verde para positivo, vermelho para negativo e neutro para zero.
Nota de rodapé explicando o significado do saldo.

## ✅ Critérios de Aceite

- [ ] Colunas conforme o protótipo
- [ ] Saldo derivado de `lancamento_horas`, não de coluna fixa
- [ ] Cores por sinal do saldo
- [ ] Apenas funcionários ativos por padrão

---

### #39 · [Banco de Horas] Extrato individual do banco de horas
- **Milestone:** M5 — Coberturas e banco de horas
- **Labels:** ui, banco-de-horas
- **Responsável:** DaviRSuassuna
- **Depende de:** #38

## 🎯 Objetivo e Contexto

Modal do botão "Ver extrato": lançamentos do funcionário no período, com data, tipo,
descrição, horas e saldo acumulado linha a linha.

## ✅ Critérios de Aceite

- [ ] Modal com lançamentos ordenados por data
- [ ] Saldo acumulado progressivo
- [ ] Estado vazio tratado
- [ ] Fecha sem afetar a tela anterior

---

### #40 · [Banco de Horas] Período de apuração e exportação do relatório
- **Milestone:** M5 — Coberturas e banco de horas
- **Labels:** banco-de-horas
- **Responsável:** DaviRSuassuna
- **Depende de:** #38

## 🎯 Objetivo e Contexto

Seletor de período (mês corrente, meses anteriores, acumulado do ano) e exportação em PDF
ou CSV. Resolve a divergência da issue #2: o cliente disse que o banco zera mensalmente,
mas a tela mostra "Acumulado de 2026" — com lançamentos datados, o filtro atende aos dois.

## ✅ Critérios de Aceite

- [ ] Seletor de período filtrando os saldos
- [ ] Exportação do relatório gerando arquivo
- [ ] Período selecionado impresso no cabeçalho do relatório

---

### #41 · [PDF] Integrar PDFBox e gerar o documento base
- **Milestone:** M6 — PDF e dashboard
- **Labels:** pdf, backend
- **Responsável:** DaviRSuassuna
- **Depende de:** #11

## 🎯 Objetivo e Contexto

Adicionar o Apache PDFBox 3.x e montar o serviço de geração: página A4 retrato, margens,
fontes e utilitários de tabela e quebra de página. PDFBox não tem componente de tabela
pronto — o utilitário de desenho de linhas e células é o trabalho principal aqui, e todo o
resto do PDF depende dele.

## 🔧 Contrato Técnico

```java
public interface GeradorPdfService {
    Path gerarEscalaMensal(YearMonth mes, OpcoesPdf opcoes, Path destino);
}
```
O PDFBox não traz componente de tabela: o utilitário de desenho de células e quebra de
página é o trabalho principal, e todo o resto do PDF depende dele.

## ✅ Critérios de Aceite

- [ ] PDFBox no `pom.xml`
- [ ] Serviço gera PDF A4 com cabeçalho e rodapé
- [ ] Utilitário de tabela com quebra automática de página
- [ ] Acentuação correta (fonte com suporte a UTF-8)

---

### #42 · [PDF] Layout da escala mensal (RF08, RNF07)
- **Milestone:** M6 — PDF e dashboard
- **Labels:** pdf
- **Responsável:** DaviRSuassuna
- **Depende de:** #41

## 🎯 Objetivo e Contexto

Documento conforme a pré-visualização: cabeçalho com organização e período, tabela por dia
(data, dia da semana, equipe, agentes), rodapé com coberturas do período e linhas de
assinatura. O cliente pediu layout limpo e sem brasão de órgão específico — o produto é
genérico.

## 💼 Regras e Considerações

Cuidar do que raramente se testa: mês de 31 dias que estoura para a segunda página precisa
repetir o cabeçalho da tabela.

## ✅ Critérios de Aceite

- [ ] Documento fiel à pré-visualização
- [ ] Cabeçalho repetido em todas as páginas
- [ ] Coberturas como nota de rodapé
- [ ] Linhas de assinatura no fim
- [ ] Legível ao imprimir em preto e branco

---

### #43 · [PDF] Tela de exportação com opções, pré-visualização e impressão
- **Milestone:** M6 — PDF e dashboard
- **Labels:** ui, pdf
- **Responsável:** DaviRSuassuna
- **Depende de:** #42

## 🎯 Objetivo e Contexto

Conforme a tela 5: período, formato, checkboxes de conteúdo (coberturas, telefones, saldo
do banco de horas, campo de assinatura), seletor de caminho, pré-visualização e botões
Gerar PDF / Imprimir.

## 💼 Regras e Considerações

A pré-visualização sai do próprio PDFBox: `PDFRenderer` gera a imagem da página e ela vai
para um `ImageView`. Sem biblioteca extra.

## ✅ Critérios de Aceite

- [ ] Opções afetam o documento gerado
- [ ] Pré-visualização atualiza ao trocar as opções
- [ ] Seletor de diretório com caminho padrão de `configuracao`
- [ ] Impressão direta funcionando
- [ ] Data da última exportação atualizada na barra de status

---

### #44 · [Dashboard] Cards de indicadores do dashboard
- **Milestone:** M6 — PDF e dashboard
- **Labels:** ui, dashboard
- **Responsável:** Joao-Bosco-Neto
- **Depende de:** #11, #16

## 🎯 Objetivo e Contexto

Quatro cards da tela 1: plantão de hoje (equipe, nº de agentes, horário), funcionários
ativos (com inativos), coberturas no mês e dias incompletos. Cada um com consulta agregada
própria.

## ✅ Critérios de Aceite

- [ ] Quatro cards com dados reais
- [ ] Estado tratado quando não há plantão hoje
- [ ] Consultas agregadas, sem carregar listas inteiras em memória

---

### #45 · [Dashboard] Semana atual e próximos plantões no dashboard
- **Milestone:** M6 — PDF e dashboard
- **Labels:** ui, dashboard
- **Responsável:** Joao-Bosco-Neto
- **Depende de:** #44

## 🎯 Objetivo e Contexto

Faixa da semana corrente com sete dias (equipe e agentes, dia de hoje destacado) e tabela
dos próximos plantões com selo Confirmado/Incompleto.

## ✅ Critérios de Aceite

- [ ] Semana renderizada de domingo a sábado com hoje destacado
- [ ] Próximos plantões a partir de hoje
- [ ] Selo de situação conforme o mínimo de agentes

---

### #46 · [Dashboard] Painel de pendências e alertas
- **Milestone:** M6 — PDF e dashboard
- **Labels:** dashboard, regra-de-negocio
- **Responsável:** Joao-Bosco-Neto
- **Depende de:** #44

## 🎯 Objetivo e Contexto

Alertas derivados do estado do sistema, como no protótipo: dia com efetivo incompleto,
funcionário desativado que estava escalado, escala do próximo mês não iniciada, cobertura
pendente. Regras em serviço próprio, nada de texto fixo na tela.

## ✅ Critérios de Aceite

- [ ] Pelo menos quatro tipos de alerta implementados
- [ ] Alertas calculados por serviço testável
- [ ] Severidade diferenciada por cor
- [ ] Estado vazio quando não há pendências

---

### #47 · [Testes] Testes unitários das regras de escala
- **Milestone:** M7 — Qualidade e entrega
- **Labels:** testes
- **Responsável:** Joao-Bosco-Neto
- **Depende de:** #25, #26, #29

## 🎯 Objetivo e Contexto

Cobrir descanso obrigatório (limite exato, dentro, fora, virada de mês), mínimo de agentes,
duplicidade e gerador de rodízio. São as regras que, se falharem na apresentação, derrubam
a demonstração — e as únicas que dá para testar sem abrir a janela.

## ✅ Critérios de Aceite

- [ ] Testes de descanso, mínimo, duplicidade e rodízio
- [ ] Casos de limite explícitos
- [ ] Todos verdes no CI

---

### #48 · [Banco] Testes de integração dos repositórios
- **Milestone:** M7 — Qualidade e entrega
- **Labels:** testes, banco
- **Responsável:** Krisbrn
- **Depende de:** #9, #10, #11, #12

## 🎯 Objetivo e Contexto

Testes contra banco real (H2 em memória ou instância descartável), cobrindo CRUD,
constraints de unicidade, cascata de exclusão e a consulta de escala mensal.

## ✅ Critérios de Aceite

- [ ] Base limpa e migrada a cada execução
- [ ] CRUD de todos os repositórios coberto
- [ ] Constraints de unicidade testadas
- [ ] Rodando no CI

---

### #49 · [Build] Empacotar a aplicação com jpackage para Windows
- **Milestone:** M7 — Qualidade e entrega
- **Labels:** empacotamento, bloqueante
- **Responsável:** Krisbrn
- **Depende de:** #4

## 🎯 Objetivo e Contexto

Instalador Windows com JRE embutida (o cliente não instala Java), ícone, atalho no menu
iniciar e diretório de dados para banco, configuração e logs.

## 💼 Regras e Considerações

**Fazer uma versão de teste já no M1**, mesmo com a aplicação vazia. jpackage costuma
revelar surpresas com módulos do JavaFX, e descobrir isso no dia 26 é problema sério.

## ✅ Critérios de Aceite

- [ ] Instalador `.msi` ou `.exe` gerado por comando Maven
- [ ] Instala e roda em máquina Windows sem Java instalado
- [ ] Banco e configuração em diretório gravável do usuário
- [ ] Processo documentado no README

---

### #50 · [Docs] Manual do usuário
- **Milestone:** M7 — Qualidade e entrega
- **Labels:** docs
- **Responsável:** DaviRSuassuna
- **Depende de:** #43

## 🎯 Objetivo e Contexto

Manual em PDF com capturas: primeiro acesso, cadastro de funcionários e equipes, montagem
da escala, geração do rodízio, registro de coberturas, consulta do banco de horas e
exportação do PDF. O RNF05 exige uso sem treinamento técnico — o manual é parte da
entrega, não enfeite.

## ✅ Critérios de Aceite

- [ ] Todos os fluxos principais documentados com capturas
- [ ] Seção de problemas comuns
- [ ] PDF entregue junto do instalador

---

### #51 · [Docs] README final e documentação de arquitetura
- **Milestone:** M7 — Qualidade e entrega
- **Labels:** docs
- **Responsável:** Joao-Bosco-Neto
- **Depende de:** #49

## 🎯 Objetivo e Contexto

README com descrição, stack, requisitos, instruções de build e execução, estrutura de
pastas e link para o manual. Documento de arquitetura com o MER final, decisões técnicas e
o rastreio de cada RF/RNF para as issues que o implementaram — isso costuma valer nota na
avaliação acadêmica.

## ✅ Critérios de Aceite

- [ ] README completo
- [ ] MER atualizado
- [ ] Tabela de rastreabilidade RF/RNF → issue
- [ ] Decisões técnicas justificadas

---

### #52 · [Testes] Teste de aceitação com o cliente
- **Milestone:** M7 — Qualidade e entrega
- **Labels:** testes
- **Responsável:** Joao-Bosco-Neto
- **Depende de:** #49, #50

## 🎯 Objetivo e Contexto

Instalar na máquina real e acompanhar o gestor montando uma escala de verdade do zero:
cadastrar funcionários, gerar o rodízio, registrar uma cobertura, exportar o PDF.

## 💼 Regras e Considerações

**Agendar até o dia 26 (05/09)**, deixando margem para corrigir o que aparecer antes da
apresentação. Registrar os problemas como issues, classificando o que é bloqueante.

## ✅ Critérios de Aceite

- [ ] Instalação concluída na máquina do cliente
- [ ] Fluxo completo executado pelo próprio gestor
- [ ] Problemas registrados como issues classificadas
- [ ] Aceite formal registrado

---

### #53 · [Docs] Roteiro e material da apresentação
- **Milestone:** M7 — Qualidade e entrega
- **Labels:** docs
- **Responsável:** Joao-Bosco-Neto
- **Depende de:** #52

## 🎯 Objetivo e Contexto

Slides e roteiro de demonstração cobrindo problema, solução, arquitetura, demo ao vivo e
resultados. Preparar base de dados de demonstração já populada e ensaiar o roteiro — demo
ao vivo com banco vazio é onde a apresentação costuma travar.

## ✅ Critérios de Aceite

- [ ] Slides prontos
- [ ] Roteiro de demonstração escrito e cronometrado
- [ ] Base de demonstração preparada
- [ ] Ensaio completo feito pela equipe

---

### #54 · [Escala] Suporte a meio plantão
- **Milestone:** Futuro
- **Labels:** futuro, escala
- **Responsável:** —
- **Depende de:** —

## 🎯 Objetivo e Contexto

O cliente confirmou que um agente pode cumprir metade do plantão, com outra pessoa cobrindo
as 12h restantes. Nenhuma tela do protótipo trata isso. Implementação prevista: colunas
`inicio_previsto` e `fim_previsto` em `escala_funcionario`, sem tabela nova.

---

### #55 · [Escala] Escala de sobreaviso
- **Milestone:** Futuro
- **Labels:** futuro, escala
- **Responsável:** —
- **Depende de:** —

## 🎯 Objetivo e Contexto

Grupo de agentes de prontidão, acionados quando a equipe de plantão precisa sair. O grupo
muda periodicamente. O `tipo_turno` genérico já suporta o conceito — falta a interface.

---

### #56 · [Futuro] Acesso de visualização para os agentes
- **Milestone:** Futuro
- **Labels:** futuro
- **Responsável:** —
- **Depende de:** —

## 🎯 Objetivo e Contexto

Evolução citada pelo cliente: agentes consultando a própria escala e solicitando troca de
dia pelo aplicativo. Exige repensar a arquitetura para multiusuário em rede — hoje o
sistema é monousuário e local.

## 💼 Regras e Considerações

## Segurança (OWASP Top 10:2025)

Issues derivadas da análise em `ANALISE_SEGURANCA_OWASP.md`. Label adicional a criar:
`seguranca` (cor `b60205`).

---

### #57 · [Segurança] Modelagem de ameaças do sistema
- **Milestone:** M0 — Fundação
- **Labels:** seguranca, docs, decisao
- **Responsável:** Joao-Bosco-Neto
- **Depende de:** —

## 🎯 Objetivo e Contexto

O OWASP A06 (Design Inseguro) trata falhas de arquitetura que nenhuma implementação
perfeita corrige, porque o controle nunca foi projetado. A prevenção recomendada é modelagem
de ameaças nas partes críticas: autenticação, controle de acesso e lógica de negócio.

## 💼 Regras e Considerações

Documentar: quais dados o sistema guarda e sua sensibilidade (nome, CPF, telefone e escala
de policiais), quem são os atores (gestor, administrador, alguém com acesso físico à
máquina), quais os fluxos críticos, e quais riscos foram aceitos conscientemente com
justificativa (ex.: ausência de MFA em sistema offline).

Além do ganho de segurança, é artefato que agrega em avaliação acadêmica.

## ✅ Critérios de Aceite

- [ ] Documento com atores, ativos e fluxos críticos
- [ ] Tabela de riscos com tratamento ou aceite justificado
- [ ] Riscos aceitos referenciados no README

---

### #58 · [Segurança] Decidir algoritmo de hash de senha (BCrypt vs Argon2id)
- **Milestone:** M0 — Fundação
- **Labels:** seguranca, decisao, auth
- **Responsável:** Krisbrn
- **Depende de:** —

## 🎯 Objetivo e Contexto

O OWASP A04:2025 recomenda Argon2, yescrypt, scrypt ou PBKDF2-HMAC-SHA-512 para
armazenamento de senha, tratando bcrypt como caso de sistema legado. Nossa decisão anterior
foi BCrypt.

## 💼 Regras e Considerações

**Deixou de ser teórica:** o `jbcrypt` da org.mindrot já está no `pom.xml`, e essa
biblioteca está sem manutenção desde 2010. Ainda não há nenhuma senha gravada, então a
troca custa uma classe agora — e fica cara depois da primeira.

BCrypt com custo 12 não é falha e segue amplamente usado, mas estamos começando um sistema
novo. Em Java, `password4j` ou Bouncy Castle entregam Argon2id com API simples. O custo da
troca é uma classe, feita agora, antes de existir qualquer senha gravada.

## ✅ Critérios de Aceite

- [ ] Decisão registrada com justificativa
- [ ] Parâmetros definidos (custo do BCrypt, ou memória/iterações/paralelismo do Argon2id)
- [ ] Documentos do projeto atualizados

---

### #59 · [Segurança] Proteger o arquivo do banco de dados
- **Milestone:** M1 — Dados e autenticação
- **Labels:** seguranca, banco, bloqueante
- **Responsável:** Krisbrn
- **Depende de:** #7

## 🎯 Objetivo e Contexto

**Maior risco do sistema.** Login, perfis e todas as regras podem ser contornados por quem
copiar o arquivo do banco e abrir com qualquer ferramenta. Mapeia CWE-922 e CWE-732.

## 💼 Regras e Considerações

Implementar: senha de acesso ao banco (não vazia, não fixa no código), arquivo gravado em
diretório do usuário com permissão restrita, e avaliar criptografia AES do H2.

Registrar honestamente a limitação: num desktop monousuário a chave fica na mesma máquina,
o que limita o ganho real. A proteção efetiva nesse cenário é criptografia de disco do
sistema operacional — recomendar BitLocker ao cliente e documentar como risco residual.

## ✅ Critérios de Aceite

- [ ] Banco protegido por senha, fora do repositório
- [ ] Arquivo em diretório com permissão restrita ao usuário
- [ ] Decisão sobre criptografia AES registrada
- [ ] Risco residual documentado no manual e na modelagem de ameaças

---

### #60 · [Segurança] Endurecer o fluxo de autenticação
- **Milestone:** M1 — Dados e autenticação
- **Labels:** seguranca, auth
- **Responsável:** Krisbrn
- **Depende de:** #13
- **Limite de tentativas** com atraso progressivo. Força bruta contra banco local é trivial.
- **Mensagem genérica** ("Usuário ou senha inválidos") para não permitir enumeração de contas.
- **Política de senha pelo NIST 800-63b**: mínimo de 8 caracteres priorizando comprimento
- **Sem credenciais padrão** em código ou seed.

## 🎯 Objetivo e Contexto

Conforme OWASP A07:

## 💼 Regras e Considerações

sobre complexidade, sem rotação forçada — a rotação obrigatória comprovadamente leva a
  senhas piores.

Cuidado ao implementar o bloqueio: travar a conta indefinidamente cria negação de serviço
contra o próprio gestor, que é o único operador do sistema. Preferir atraso progressivo.

## ✅ Critérios de Aceite

- [ ] Atraso progressivo após tentativas falhas
- [ ] Mensagem de erro idêntica para usuário inexistente e senha errada
- [ ] Validação de comprimento mínimo sem exigência de rotação
- [ ] Nenhuma credencial fixa no código ou no seed
- [ ] Testes cobrindo as regras

---

### #61 · [Segurança] Bloqueio automático por inatividade
- **Milestone:** M2 — Shell e usuários
- **Labels:** seguranca, auth, ui
- **Responsável:** Krisbrn
- **Depende de:** #18

## 🎯 Objetivo e Contexto

O cenário #3 do OWASP A07 descreve exatamente nosso ambiente: aplicação sensível deixada
aberta e um colega com acesso temporário ao computador desbloqueado. Numa delegacia com
máquina compartilhada, isso é rotina, não hipótese.

## 💼 Regras e Considerações

Após período configurável de inatividade, exibir tela de bloqueio exigindo a senha para
retomar, preservando o trabalho em andamento — bloquear perdendo uma escala em montagem faz
o usuário desativar o recurso.

## ✅ Critérios de Aceite

- [ ] Bloqueio após inatividade configurável (padrão sugerido: 15 min)
- [ ] Retomada exige senha do usuário logado
- [ ] Trabalho não salvo é preservado
- [ ] Evento registrado em log

---

### #62 · [Segurança] Aplicar controle de perfil na camada de serviço
- **Milestone:** M2 — Shell e usuários
- **Labels:** seguranca, auth, backend
- **Responsável:** Krisbrn
- **Depende de:** #18, #20

## 🎯 Objetivo e Contexto

O cenário #3 do OWASP A01 trata de aplicações que colocam todo o controle de acesso no
front-end. Em JavaFX, esconder um item de menu **não é controle de acesso** — é apenas
apresentação.

## 💼 Regras e Considerações

Toda operação restrita ao administrador precisa verificar o perfil também no serviço, e
falhar de forma segura quando a sessão não tiver permissão.

## ✅ Critérios de Aceite

- [ ] Verificação de perfil nos serviços, não apenas nos controllers
- [ ] Operação sem permissão lança exceção específica e registra log
- [ ] Testes chamando o serviço diretamente com perfil insuficiente

---

### #63 · [Segurança] Tratamento global de exceções com falha segura
- **Milestone:** M2 — Shell e usuários
- **Labels:** seguranca, backend
- **Responsável:** Joao-Bosco-Neto
- **Depende de:** #19
- **Nunca exibir stack trace ao usuário** (CWE-209). Mensagem compreensível na tela,
- **Em transação, rollback de tudo e recomeço** — falhar de forma fechada. Tentar recuperar

## 🎯 Objetivo e Contexto

Complementa a #19 com os requisitos do OWASP A10 (categoria nova em 2025):

## 💼 Regras e Considerações

- Nenhum `catch` vazio — é CWE-390, detecção de erro sem ação, e a causa provável do
  clássico "botão Salvar não faz nada".
  detalhe técnico no arquivo de log.
  transação pela metade é onde nascem erros irrecuperáveis.
- Manipulador global como rede de segurança para o que escapar.

## ✅ Critérios de Aceite

- [ ] Nenhum bloco catch vazio no projeto
- [ ] Stack trace apenas em arquivo de log
- [ ] Transações com rollback completo em caso de erro
- [ ] Manipulador global registrado

---

### #64 · [Segurança] Log de auditoria e eventos de segurança
- **Milestone:** M5 — Coberturas e banco de horas
- **Labels:** seguranca, backend
- **Responsável:** DaviRSuassuna
- **Depende de:** #13, #19

## 🎯 Objetivo e Contexto

Conforme OWASP A09, registrar: login bem-sucedido **e falho** (o OWASP cita explicitamente
o erro de registrar só os sucessos), redefinição de senha, criação e desativação de
usuário, exceções autorizadas a regras de escala, e exclusão de escala ou cobertura.

## 💼 Regras e Considerações

**Nunca registrar senha, hash ou dado pessoal completo** (CWE-532). Sanitizar quebras de
linha em dados do usuário antes de gravar, para evitar forja de entradas no log (CWE-117).

A tabela `escala_excecao` já é uma trilha de auditoria — tratá-la como tal, sem permitir
edição.

## ✅ Critérios de Aceite

- [ ] Eventos de segurança registrados com data, usuário e resultado
- [ ] Nenhuma senha ou hash em log
- [ ] Quebras de linha sanitizadas
- [ ] Exceções de regra não podem ser editadas após criadas

---

### #65 · [Segurança] Verificação de dependências vulneráveis no CI
- **Milestone:** M0 — Fundação
- **Labels:** seguranca, setup
- **Responsável:** DaviRSuassuna
- **Depende de:** #6

## 🎯 Objetivo e Contexto

O OWASP A03 foi a categoria mais votada pela comunidade como risco nº 1. Nosso `pom.xml`
puxa JavaFX, H2, Flyway, PDFBox e BCrypt, mais as dependências transitivas de cada um — o
Log4Shell foi exatamente isso: uma biblioteca de log que ninguém sabia que estava ali.

## 💼 Regras e Considerações

Configurar o Dependabot (um arquivo em `.github/`) e o plugin OWASP Dependency-Check no
Maven, rodando no CI. Depois de configurados, funcionam sozinhos.

Fixar versões explicitamente, sem intervalos abertos, e baixar apenas do Maven Central.

## ✅ Critérios de Aceite

- [ ] `.github/dependabot.yml` configurado
- [ ] Dependency-Check rodando no CI
- [ ] Todas as versões fixas no `pom.xml`
- [ ] Relatório de vulnerabilidades revisado ao menos uma vez antes da entrega

---

### #66 · [Segurança] Endurecer configuração da aplicação e do H2
- **Milestone:** M1 — Dados e autenticação
- **Labels:** seguranca, banco, setup
- **Responsável:** Krisbrn
- **Depende de:** #7
- **Desativar explicitamente o console web do H2.** Ele abre porta HTTP local e já foi vetor
- **Senha do banco fora do repositório** (CWE-260) — arquivo de configuração externo,
- **Sem dados de exemplo em produção**: o seed de teste não pode ir no instalador.
- **Sem modo debug ativo** na build de entrega.

## 🎯 Objetivo e Contexto

Conforme OWASP A02:

## 💼 Regras e Considerações

de execução remota de código. Não deve subir junto com a aplicação.
  incluído no `.gitignore`.

## ✅ Critérios de Aceite

- [ ] Console H2 comprovadamente desabilitado
- [ ] Nenhuma credencial versionada no repositório
- [ ] Seed de teste separado do seed de produção
- [ ] Build de entrega sem flags de depuração

---

### #67 · [Segurança] Revisar parametrização de todas as consultas SQL
- **Milestone:** M7 — Qualidade e entrega
- **Labels:** seguranca, banco, testes
- **Responsável:** Krisbrn
- **Depende de:** #48

## 🎯 Objetivo e Contexto

Revisão dirigida conforme OWASP A05. Dois pontos de atenção específicos:

## 💼 Regras e Considerações

- A **busca por nome ou matrícula** é entrada livre indo direto para uma cláusula `WHERE`.
- O OWASP alerta que **estruturas SQL, como nomes de tabela e coluna, não podem sofrer
  escape**. Se a ordenação da `TableView` montar `ORDER BY` a partir do nome da coluna
  clicada, isso é injeção — e parece código inofensivo. Usar lista fechada de colunas
  permitidas.

## ✅ Critérios de Aceite

- [ ] Nenhuma concatenação de entrada do usuário em SQL
- [ ] Ordenação dinâmica usando lista fechada de colunas
- [ ] Teste tentando injeção pelo campo de busca

---

### #68 · [Segurança] Integridade do instalador
- **Milestone:** M7 — Qualidade e entrega
- **Labels:** seguranca, empacotamento
- **Responsável:** Krisbrn
- **Depende de:** #49

## 🎯 Objetivo e Contexto

O cenário #2 do OWASP A08 trata de firmware distribuído sem assinatura. Nosso `.msi` tem o
mesmo problema: nada impede a substituição por versão adulterada entre nós e a delegacia.

## 💼 Regras e Considerações

Certificado de assinatura de código custa dinheiro e está fora do escopo de um projeto
acadêmico. A mitigação viável é **publicar o hash SHA-256** do instalador junto da entrega
e conferir antes de instalar.

Registrar também a regra: **não usar serialização Java** (CWE-502) em nenhum ponto do
sistema.

## ✅ Critérios de Aceite

- [ ] Hash SHA-256 publicado com o instalador
- [ ] Procedimento de verificação no manual
- [ ] Ausência de serialização Java confirmada
- [ ] Limitação registrada na modelagem de ameaças

---

### #69 · [Segurança] Conformidade com a LGPD e minimização de dados
- **Milestone:** M7 — Qualidade e entrega
- **Labels:** seguranca, docs
- **Responsável:** Joao-Bosco-Neto
- **Depende de:** #57

## 🎯 Objetivo e Contexto

O sistema guarda nome, CPF, telefone e a escala de plantão de policiais. O CPF por si só já
coloca o sistema sob a LGPD, e a escala é informação operacionalmente sensível — saber que
uma delegacia terá dois agentes numa data específica tem valor para quem não deveria saber.

## 💼 Regras e Considerações

O OWASP A04 orienta classificar os dados e não armazenar o que não for necessário: dado que
não é retido não pode ser roubado.

Reavaliar se o CPF é realmente necessário — a matrícula já identifica o funcionário em todo
o sistema. Se não for, remover o campo é a medida mais eficaz e a mais barata.

## ✅ Critérios de Aceite

- [ ] Dados classificados por sensibilidade
- [ ] Necessidade do CPF reavaliada e decisão registrada
- [ ] Política de retenção definida
- [ ] Orientação de segurança para o cliente no manual (disco criptografado, tela bloqueada)

---

---

### #70 · [UI] Tela de configurações da organização
- **Milestone:** M3 — Funcionários e equipes
- **Labels:** ui, setup
- **Responsável:** Krisbrn
- **Depende de:** #16

## 🎯 Objetivo e Contexto

O protótipo tem um item "Configurações" no menu que o backlog não previa. É a tela que
edita o registro único da tabela `configuracao`: nome da organização, subtítulo, regime de
apuração do banco de horas, carga horária mensal de referência e caminho padrão do PDF.

## 💼 Regras e Considerações

É esta tela que permite instalar o mesmo sistema em outro cliente sem tocar no código —
o nome da organização aparece na barra de título e no cabeçalho do PDF.

A carga horária mensal pode ficar vazia: nem toda organização controla isso.

## ✅ Critérios de Aceite

- [ ] Edição de todos os campos de `configuracao`
- [ ] Nome da organização refletido na barra de título e no PDF
- [ ] Alteração da apuração do banco de horas refletida na tela de saldos
- [ ] Acesso restrito conforme o perfil do usuário

---

### #71 · [Banco] Repositórios de TipoTurno, MotivoCobertura e Configuracao
- **Milestone:** M1 — Dados e autenticação
- **Labels:** banco, backend
- **Responsável:** Krisbrn
- **Depende de:** #7, #8

## 🎯 Objetivo e Contexto

As três tabelas de apoio que o novo modelo introduziu e que alimentam o CRUD de tipos de
turno (#24), a tela de configurações (#70) e o formulário de cobertura (#33).

## 💼 Regras e Considerações

`TipoTurnoRepository` precisa listar apenas os ativos para o gerador de rodízio, mas todos
para a tela de cadastro — inativar um tipo não pode apagar os turnos já gerados com ele.

`ConfiguracaoRepository` opera sobre registro único: leitura devolve sempre a mesma linha.

## ✅ Critérios de Aceite

- [ ] CRUD de `tipo_turno` com filtro de ativos
- [ ] CRUD de `motivo_cobertura`
- [ ] Leitura e atualização de `configuracao`
- [ ] Inativar tipo de turno não afeta turnos já criados
