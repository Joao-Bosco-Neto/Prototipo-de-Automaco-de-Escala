# Arquitetura do Sistema de Escala

Este documento registra a organização em camadas, o modelo de dados final, as decisões
técnicas que moldaram o sistema e o rastreio de cada requisito às issues que o
implementaram.

Complementa `GUIA_DE_CONTRIBUICAO.md` (fluxo de trabalho), `MODELAGEM_AMEACAS.md`
(ameaças e riscos aceitos) e `LGPD_E_DADOS_PESSOAIS.md` (dados pessoais e retenção).

Ele descreve o estado do sistema em setembro de 2026 e foi escrito a partir do código,
do `schema.sql` e das issues — não a partir do backlog original.

---

## 1. Visão geral da arquitetura

### 1.1 As camadas

O código vive em `src/main/java/br/edu/sistemaescala/`, dividido em duas metades que só
se comunicam numa direção: o frontend chama o backend, nunca o contrário.

| Camada | Pacote | Responsabilidade | O que **não** pode ter |
| --- | --- | --- | --- |
| Controller de tela | `frontend/controller/` | Montar a interface JavaFX, ler entrada, exibir resultado | Regra de negócio, SQL, cálculo de descanso ou de efetivo |
| Serviço | `backend/service/` | Regras de negócio e orquestração; é a fronteira transacional | JavaFX, `Stage`, `Alert`, qualquer tipo de UI |
| Repositório | `backend/repository/` (interface) e `backend/repository/jdbc/` (implementação) | Ler e gravar no banco com `PreparedStatement` | Decisão de negócio |
| Infraestrutura de dados | `backend/dao/` | Conexão, criação do banco e transação (`ConexaoBanco`, `BancoInicializador`, `TransacaoUtil`) | Consulta de domínio |
| Modelo | `backend/model/` | Entidades e enums do domínio | Persistência, `Serializable` |

O ponto de entrada é `Main.java`, que dispara o `BancoInicializador` antes de abrir
qualquer janela. A janela principal é o `ShellController`, que hospeda menu, navegação
lateral, barra de status, bloqueio por inatividade e a área onde as telas são trocadas.

### 1.2 A regra que separa as camadas

**Nenhuma regra de negócio dentro de controller de tela.**

A razão é testabilidade, não pureza arquitetural. O que está em `frontend/controller/`
depende de uma `Stage` aberta e de uma thread da JavaFX para rodar — não dá para exercitar
num teste automatizado sem levantar a interface inteira. E justamente o que mais precisa de
teste neste sistema é regra:

- **Intervalo de descanso obrigatório** entre plantões, verificado nos dois sentidos:
  escalar alguém no dia 10 pode ser inválido por causa do dia 12, não só do dia 8.
- **Efetivo mínimo** por turno, com mínimo e máximo vindos do tipo de turno ou do turno
  concreto, nunca fixos no código.
- **Rodízio automático** com continuidade entre meses: o rodízio de setembro começa de
  onde agosto parou.
- **Sobreposição de horários** e duplicidade de alocação.

Tudo isso vive em `RegraEscalaServiceImpl` e `GeradorRodizioServiceImpl`, e é coberto por
testes que rodam em milissegundos sem abrir janela nenhuma. `mvn clean verify` executa
**294 testes** de serviço e de repositório.

A consequência prática no código é que o controller pergunta e obedece. `PainelAtribuicao`
chama `regraEscalaService.podeAlocar(...)` e `verificarDescanso(...)`, exibe o veredito e,
quando o gestor decide romper a regra, chama `EscalaExcecaoService` para registrar a
justificativa. Ele não decide se 72 horas bastam — não sabe.

### 1.3 Interface e implementação separadas

Cada repositório é uma interface em `backend/repository/` com uma implementação JDBC em
`backend/repository/jdbc/`. O mesmo par vale para os serviços que carregam regra: uma
interface `XService` com um `XServiceImpl`. A exceção são três classes concretas sem
interface — `AutorizacaoService`, `GeradorPdfService` e `ExportacaoRelatorioService` —, que
não são substituídas por dublê em nenhum teste e por isso não ganharam uma.

Isso não é cerimônia: é o que permite que `RegraEscalaServiceImplTest` e
`GeradorRodizioServiceImplTest` rodem com repositórios simulados por Mockito, sem banco.
Os testes que precisam do banco de verdade — os de repositório, mais
`CoberturaServiceImplTest` e `LimpezaEscalaTransacaoTest` — usam H2 e são explicitamente
testes de integração.

Não há framework de injeção de dependência. As dependências são passadas por construtor e
montadas à mão no controller ou no `Main`. Com o tamanho deste sistema, Spring custaria
mais do que resolve.

### 1.4 Transação

Operação que escreve em mais de uma tabela passa por `TransacaoUtil.executar(...)`, na
camada de serviço. A regra é falha fechada: qualquer exceção que escape do bloco provoca
`rollback` e sobe — nada de gravar metade. Ver a decisão 3.7.

### 1.5 Telas em Java puro

As telas são construídas em Java, sem FXML. A pasta `src/main/resources/frontend/fxml/`
sobrou da estrutura inicial e está vazia. Ver a decisão 3.3.

O tema visual está em `src/main/resources/frontend/css/app.css`, com a paleta extraída do
protótipo `prototipo/index.html`. A regra é usar `getStyleClass().add(...)`, nunca
`setStyle()` com cor em hexadecimal. `VitrineComponentesApp` exibe todos os componentes do
tema numa janela só.

---

## 2. MER final

### 2.1 Aviso sobre o diagrama

O arquivo `documentacao/diagrama_mer.png` **está desatualizado** e não deve ser usado como
referência até ser regerado.

Verificado em 10/09/2026 abrindo a imagem e comparando com `schema.sql`. O diagrama mostra
**6 tabelas** (`configuracao`, `usuario`, `funcionario`, `tipo_turno`, `escala_turno`,
`escala_funcionario`) e o schema tem **10**. Divergências encontradas:

| Divergência no `diagrama_mer.png` | Situação real no `schema.sql` |
| --- | --- |
| Faltam `motivo_cobertura`, `lancamento_horas`, `escala_excecao` e `log_seguranca` | As quatro existem |
| `funcionario.horas_banco` (INT) | Coluna não existe; o saldo é derivado por soma em `lancamento_horas` |
| `usuario.role` como tipo ENUM `role_usuario` | `VARCHAR(20)` com `CHECK (role IN ('admin','gestor'))` — ENUM é proibido por portabilidade |
| `tipo_turno` sem `hora_inicio`, `min_agentes`, `max_agentes`, `conta_banco_horas`, `ativo` | Todas presentes; sem `hora_inicio` o regime 12x36 não é representável |
| `escala_funcionario` sem `motivo_cobertura_id`, `inicio`/`fim`, `lancou_banco_horas` | Todas presentes |
| `configuracao` só com `nome_organizacao` | Tem também `subtitulo`, `carga_horaria_mensal`, `apuracao_banco_horas`, `caminho_pdf_padrao` |
| `funcionario` sem `matricula UNIQUE` visível como restrição, sem `ativo`, sem `observacoes` | `matricula NOT NULL UNIQUE`, `ativo` e `observacoes` presentes |

**A descrição em texto abaixo é a fonte autoritativa do modelo.** Ela existe justamente
porque texto desatualiza de forma visível — um `git diff` mostra quando o schema muda e a
descrição não —, enquanto uma imagem desatualiza em silêncio. A fonte última continua sendo
`src/main/resources/banco/schema.sql`.

Regenerar a imagem a partir do schema atual é trabalho pendente, e a issue #3 já listava
"Diagrama MER atualizado" como critério de aceite.

### 2.2 As 10 tabelas

| # | Tabela | O que guarda | Chave natural |
| --- | --- | --- | --- |
| 1 | `configuracao` | Identidade e preferências da organização: nome, subtítulo, carga horária mensal, regime de apuração do banco de horas, caminho padrão do PDF | Registro único |
| 2 | `usuario` | Contas de quem **opera** o programa: nome, login, hash BCrypt da senha, perfil, último login | `login` UNIQUE |
| 3 | `funcionario` | Pessoas **escaladas**: nome, matrícula, telefone, observações | `matricula` NOT NULL UNIQUE |
| 4 | `tipo_turno` | O regime de trabalho: hora de início, duração, intervalo de descanso, mínimo e máximo de agentes, se conta banco de horas | — |
| 5 | `escala_turno` | Turnos concretos no calendário: início, fim, mínimo e máximo do dia, observação | `(tipo_turno_id, inicio)` UNIQUE |
| 6 | `motivo_cobertura` | Catálogo de motivos de cobertura, cadastrável pela organização | `nome` UNIQUE |
| 7 | `escala_funcionario` | Alocação de uma pessoa num turno, incluindo cobertura e período parcial | `(escala_turno_id, funcionario_id)` UNIQUE |
| 8 | `lancamento_horas` | Extrato do banco de horas, em minutos, com tipo e data de referência | — |
| 9 | `escala_excecao` | Trilha imutável de regras rompidas com autorização do gestor | — |
| 10 | `log_seguranca` | Trilha de auditoria de eventos de segurança (OWASP A09) | — |

`usuario` e `funcionario` são tabelas separadas e **não há vínculo entre elas**: um agente
escalado não tem conta no sistema.

### 2.3 Relacionamentos

| Origem | Destino | Cardinalidade | Ao apagar o destino |
| --- | --- | --- | --- |
| `escala_turno.tipo_turno_id` | `tipo_turno.id` | N:1 | Sem cascata — tipo inativado não apaga turno gerado |
| `escala_funcionario.escala_turno_id` | `escala_turno.id` | N:1 | `ON DELETE CASCADE` |
| `escala_funcionario.funcionario_id` | `funcionario.id` | N:1 | Sem cascata — impede exclusão real de pessoa com histórico |
| `escala_funcionario.cobertura_de` | `escala_funcionario.id` (auto-relacionamento) | N:1, opcional | `ON DELETE CASCADE` (`fk_ef_cobertura`) |
| `escala_funcionario.motivo_cobertura_id` | `motivo_cobertura.id` | N:1, opcional | Sem cascata |
| `lancamento_horas.funcionario_id` | `funcionario.id` | N:1 | Sem cascata |
| `lancamento_horas.escala_funcionario_id` | `escala_funcionario.id` | N:1, opcional | `ON DELETE CASCADE` |
| `escala_excecao.escala_funcionario_id` | `escala_funcionario.id` | N:1, opcional | `ON DELETE SET NULL` |
| `escala_excecao.funcionario_id` | `funcionario.id` | N:1 | Sem cascata |

`configuracao`, `usuario` e `log_seguranca` não participam de nenhuma chave estrangeira.
No caso de `log_seguranca` isso é deliberado: a coluna `identificacao` guarda a **string de
login tentada**, não uma FK para `usuario`, porque num login falho o usuário pode nem
existir — e é justamente esse caso que o OWASP A09 manda registrar.

Em resumo, um turno pertence a um tipo de turno; várias alocações pertencem a um turno;
cada alocação aponta para uma pessoa e, opcionalmente, para a alocação que ela cobre e para
o motivo dessa cobertura; os lançamentos do banco de horas penduram na pessoa e,
opcionalmente, na alocação que os gerou; e as exceções autorizadas penduram na pessoa,
guardando a alocação só enquanto ela existir.

### 2.4 Decisões de modelagem que importam

#### `tipo_turno` é o que torna o produto genérico

Sem essa tabela o sistema serviria a um cliente só. Com ela, o regime de trabalho é dado, e
o mesmo binário atende organizações diferentes:

| Regime | Registros em `tipo_turno` |
| --- | --- |
| 24x72 | 1 registro: `08:00`, duração 24h, descanso 72h |
| 12x36 | 2 registros: `07:00`/12h/36h e `19:00`/12h/36h |
| 5x2 | 1 registro: `08:00`, duração 8h, descanso 16h |
| Sobreaviso | 1 registro: duração própria, descanso 0h, `conta_banco_horas = false` |

A coluna `hora_inicio` é o que permite mais de um turno por dia. Sem ela, 12x36 é
impossível de representar.

A consequência para o código é uma regra dura: **nunca fixar 72, 24 ou 2**. Esses valores
vêm sempre de `tipo_turno` ou de `configuracao`. O gerador de rodízio segue a mesma linha —
um tipo de turno ativo gera um turno por dia, dois tipos ativos geram dois, e não há caso
especial por regime dentro do laço.

#### `escala_funcionario.inicio` e `fim` opcionais permitem meio plantão

Nulos significam que a pessoa cumpre o turno inteiro, que é o caso normal. Preenchidos,
descrevem um turno parcial — meio plantão — sem exigir tabela nova nem coluna de tipo.

Por causa disso, a verificação de conflito é **sobreposição de intervalos**, que funciona
para qualquer regime, com ou sem período parcial:

```java
boolean haConflito = a.inicio().isBefore(b.fim()) && b.inicio().isBefore(a.fim());
```

Meio plantão está fora do escopo do protótipo (issue #63, aberta), mas o banco já o
suporta: quando a funcionalidade entrar, ela é tela, não migração.

#### `lancamento_horas` guarda minutos, não horas

Um turno de 8h30 quebra um campo inteiro de horas. Guardar minutos elimina a classe inteira
de bugs de arredondamento no saldo, ao custo de uma conversão na exibição.

Na mesma linha, `tipo_turno.duracao_horas` e `intervalo_descanso_horas` são `NUMERIC(5,2)`,
e o serviço converte para `Duration` **passando por segundos**, com `RoundingMode.HALF_UP`
— truncar para horas inteiras estaria errado num regime de 36,5h. Essa conversão é uma das
que foram validadas por sabotagem (seção 5).

O **saldo é derivado por soma do extrato, nunca guardado como coluna**. Uma coluna
`funcionario.horas_banco` — que existia no modelo original e foi removida — não sustenta a
tela "Ver extrato" nem o estorno automático quando uma cobertura é excluída.

#### Desativação lógica, nunca exclusão

`funcionario.ativo`, `usuario.ativo` e `tipo_turno.ativo` fazem desativação lógica. O
sistema não expõe exclusão de pessoa nem de conta em lugar nenhum:
`FuncionarioRepository` oferece `ativar(int)` e `desativar(int)`, e não há operação de
remoção.

A razão é o histórico. Apagar um funcionário destruiria o registro de quem estava de
plantão em cada data passada e o extrato que prova o saldo do banco de horas dele. Como as
FKs de `escala_funcionario`, `lancamento_horas` e `escala_excecao` para `funcionario` **não
têm cascata**, uma exclusão real seria recusada pelo banco — e se a cascata fosse
adicionada, levaria o histórico junto. Não existe meio-termo.

Inativar um tipo de turno segue a mesma lógica: ele some do gerador de rodízio e do
cadastro de novos turnos, mas os turnos já criados com ele continuam válidos.

O tensionamento disso com o princípio de minimização da LGPD está registrado com todas as
letras na seção 3.3 de `LGPD_E_DADOS_PESSOAIS.md`, incluindo o que **não** está
implementado.

#### A cadeia de `ON DELETE CASCADE`, e por que `cobertura_de` precisou dela

O botão "Limpar mês" (issue #44) apaga os turnos de um mês inteiro. Para isso funcionar sem
o banco recusar por violação de chave estrangeira, a cadeia de exclusão precisa estar
completa:

1. `escala_funcionario.escala_turno_id` → `CASCADE`: apagar o turno leva junto as alocações
   dele. Esse era o único cascade previsto originalmente.
2. `escala_funcionario.cobertura_de` → `CASCADE`: apagar uma alocação leva junto a alocação
   de cobertura que aponta para ela.
3. `lancamento_horas.escala_funcionario_id` → `CASCADE`: apagar a alocação leva junto o par
   de lançamentos crédito/débito que ela gerou. É o que faz o estorno automático da
   exclusão de cobertura.
4. `escala_excecao.escala_funcionario_id` → `SET NULL`, **não** cascade.

O passo 2 é o que não era óbvio, e foi corrigido durante a issue #44. O caso que o exige é
estreito: uma cobertura registrada num turno **fora do mês que está sendo limpo**. Se as
duas alocações caem no mesmo mês, elas já somem juntas pela cascata do turno. Mas se o
gestor limpa agosto e existe uma cobertura de setembro apontando para uma alocação de
agosto, sem o cascade a limpeza aborta inteira. A migração é aplicada consultando o
`INFORMATION_SCHEMA`, então bancos já instalados se corrigem sozinhos na próxima partida.

O efeito colateral fica registrado em vez de escondido: **limpar agosto também apaga uma
cobertura de setembro que aponte para agosto**. Se, no futuro, for preferível preservar o
plantão e só desfazer o vínculo, a troca é para `ON DELETE SET NULL`.

O passo 4 é o oposto, e por um motivo diferente: apagar a alocação **não pode** apagar o
registro de que a regra foi rompida com autorização. `escala_excecao` guarda
`funcionario_id` e `data_plantao` denormalizados de propósito, para o registro continuar
legível depois que a escala do mês some. É trilha de auditoria, não cadastro: o
repositório não tem `remover`, e o `atualizar` existe só para fechar a porta de forma
explícita — ele lança exceção em vez de gravar. Sem `trigger`, conforme a decisão de manter
as regras no backend.

A FK de cobertura é nomeada (`fk_ef_cobertura`) de propósito: uma chave estrangeira anônima
ganha nome gerado pelo H2 (`CONSTRAINT_E5`, `CONSTRAINT_E5C`) e não há como corrigi-la
depois por DDL fixo.

### 2.5 Regras de portabilidade do SQL

O schema precisa rodar em H2 (produção) e em PostgreSQL (desenvolvimento opcional), o que
proíbe um conjunto de construções:

| Não usar | Usar |
| --- | --- |
| `CREATE TYPE ... AS ENUM` | `VARCHAR` + `CHECK (col IN (...))` |
| `SERIAL` | `INT GENERATED ALWAYS AS IDENTITY` |
| `NOW()` | `CURRENT_TIMESTAMP` |
| `TEXT` | `VARCHAR(n)` |

`schema.sql` e `seed.sql` são idempotentes (`CREATE TABLE IF NOT EXISTS`, INSERT
condicional) e rodam a cada partida pelo `BancoInicializador`. Não há Flyway: com um script
idempotente e um único ambiente de produção, a ferramenta de migração custaria mais do que
entrega.

Toda consulta usa `PreparedStatement`; nenhuma concatena entrada do usuário. Ordenação
dinâmica usa lista fechada de colunas. Isso é verificado por
`SegurancaParametrizacaoSqlTest` (issue #35).

---

## 3. Decisões técnicas justificadas

### 3.1 H2 embarcado em vez de PostgreSQL — issue #1

**Problema.** O RNF02 exigia PostgreSQL. Mas o produto é offline e monousuário, instalado
na máquina do gestor. Um servidor PostgreSQL nessa máquina significaria instalador
separado, serviço no Windows, porta e senha de superusuário — o maior risco isolado de o
sistema não subir no dia da entrega.

**Decisão.** H2 embarcado no próprio processo, em modo de compatibilidade PostgreSQL
(`MODE=PostgreSQL` na URL de conexão). Uma dependência Maven, um arquivo, zero instalação.

**Por quê.** O modo de compatibilidade preserva a portabilidade: o mesmo `schema.sql` roda
nos dois bancos, e o acesso a dados fica atrás de interfaces de repositório. Se o cliente
um dia precisar de PostgreSQL de verdade — operação em rede, mais de um operador —, a troca
é de implementação de repositório e string de conexão, não de arquitetura.

**Divergência registrada.** Esta decisão diverge do RNF02 conscientemente. Ela foi
tomada na issue #1, que era uma issue de decisão bloqueante, com a condição explícita de
confirmar antes se PostgreSQL era exigência rígida de nota. Vale registrar que as telas do
protótipo já mostravam `escala.db` no rodapé.

**Consequência.** O plano original previa migrações no Flyway; com um `schema.sql`
idempotente e um único ambiente de produção, o Flyway não entrou (ver 2.5).

### 3.2 BCrypt via `at.favre.lib` em vez de `jbcrypt` — issue #66

**Problema.** O RNF03 exige senha armazenada com hash. O OWASP A04:2025 recomenda
Argon2id para sistemas novos e trata BCrypt como caso de sistema legado. Ao mesmo tempo, a
biblioteca BCrypt mais citada em tutoriais de Java, `org.mindrot:jbcrypt`, está **sem
manutenção desde 2010**.

**Decisão.** BCrypt com custo 12, pela biblioteca `at.favre.lib:bcrypt`. Registrada em
comentário na issue #66 e implementada no PR #84.

**Por quê.** São duas escolhas separadas, com razões separadas:

- **BCrypt em vez de Argon2id, contrariando a recomendação do OWASP.** BCrypt é hash
  adequado e amplamente usado, a equipe já o conhece, e o prazo não comporta introduzir
  biblioteca nova numa área crítica. A decisão está registrada na issue como **risco
  aceito conscientemente**, não como equivalência técnica — se o projeto tivesse mais
  folga, Argon2id seria a escolha.
- **`at.favre.lib` em vez de `jbcrypt`.** Mesmo algoritmo, biblioteca mantida ativamente e
  com API menos sujeita a erro de uso. Nenhum código usava a biblioteca ainda, então foi
  troca só de dependência. Dependência abandonada é dívida de segurança que a varredura do
  CI (issue #33) acaba cobrando.

**Nota de rastreio.** O commit `c74f042` referencia "#58" por engano; o número correto da
issue é #66. A correção está anotada no comentário da própria issue.

### 3.3 Java puro, sem FXML, nas telas

**Problema.** A equipe tem três pessoas aprendendo JavaFX com prazo curto. FXML acrescenta
um arquivo por tela, um ciclo de carregamento, anotações `@FXML` e um Scene Builder — e
divide a lógica de montagem entre XML e Java.

**Decisão.** Telas construídas inteiramente em Java, sem FXML. A pasta
`src/main/resources/frontend/fxml/` ficou da estrutura inicial e está vazia.
`PrimeiroAcessoController` é a referência de padrão.

**Por quê.** Com telas montadas em Java, a montagem é código comum: dá para extrair método,
reaproveitar por composição e revisar em `git diff` como qualquer outro código. O erro
aparece em tempo de compilação, não como exceção de carregamento em tempo de execução. E
não há um segundo lugar onde estilo possa ser definido por engano — o tema fica todo em
`app.css`, aplicado por `getStyleClass()`.

**Consequência aceita.** Não há Scene Builder e não há pré-visualização visual da tela sem
rodar a aplicação. O protótipo `prototipo/index.html` cobre esse papel na fase de desenho,
e `VitrineComponentesApp` mostra os componentes do tema.

### 3.4 Equipes de rodízio substituídas por `tipo_turno`

**Problema.** O modelo original previa uma tabela `equipe` com as equipes A/B/C/D, e
`funcionario.equipe_id` e `escala_turno.equipe_id` apontando para ela. Isso codifica no
banco uma prática específica de uma organização específica.

**Decisão.** A tabela `equipe` nunca foi criada, e a classe `Equipe` que existia sem tabela
correspondente foi removida (commit `35ab02b`). O rodízio passou a girar sobre a fila de
funcionários, com o regime descrito em `tipo_turno`.

**Por quê.** Equipe fixa e regime de turno são a mesma informação vista de ângulos
diferentes, e a equipe fixa é a versão menos flexível das duas. Com `tipo_turno`, o mesmo
código atende 24x72, 12x36 e 5x2; com `equipe`, cada regime novo exigiria interpretação
diferente da mesma coluna. Além disso, equipe fixa é exatamente o que produz **dupla fixa**
— o problema que o gerador de rodízio precisa evitar (seção 5).

**Consequência.** A sequência do rodízio é a lista de funcionários ordenada por matrícula.
Como `matricula` é `VARCHAR`, a ordenação é **alfabética, não numérica**: `PC-10432` vem
antes de `PC-9999`. É determinístico, que é o que o rodízio exige, mas não é a ordem que um
leitor humano esperaria — e por isso está registrado no javadoc do gerador.

### 3.5 CPF removido por minimização — issue #37

**Problema.** O backlog previa `funcionario.cpf`, com validação por dígito verificador, no
cadastro de funcionários.

**Decisão.** O campo foi removido do modelo. Não existe em `schema.sql`, em
`Funcionario.java`, no repositório JDBC nem em qualquer tela.

**Por quê.** A `matricula` já identifica o funcionário em todo o sistema — é
`NOT NULL UNIQUE`, aparece nas telas, nos relatórios e na trilha de auditoria. O CPF não
sustentava nenhuma funcionalidade: nenhum cálculo, nenhuma integração, nenhum relatório
dependia dele. Era retenção sem finalidade, o que o art. 6º, III da LGPD veda e o OWASP A04
desaconselha.

Há também um argumento de impacto: o CPF é o identificador que mais eleva o custo de um
vazamento, porque liga a pessoa a cadastros fora do sistema. Sua presença mudaria a
natureza de um incidente de "a escala vazou" para "a identidade civil de agentes vazou".
Dado que não é retido não pode ser roubado.

**Consequência aceita.** Se um dia houver integração com folha de pagamento ou com sistema
que use CPF como chave, o campo terá de voltar — e esta decisão precisa ser revista junto
com a classificação de dados, não desfeita em silêncio. Análise completa na seção 2 de
`LGPD_E_DADOS_PESSOAIS.md`.

### 3.6 Banco criptografado em `~/.sistema-escala` — issue #67

**Problema.** O banco começou em `./data/sistema_escala.mv.db`, dentro da pasta da
aplicação e em texto claro. Isso tem dois defeitos: uma cópia do arquivo entrega tudo o que
há dentro, e o diretório de instalação em `C:\Program Files` não é gravável pelo usuário
comum.

**Decisão.** O banco passou para `%USERPROFILE%\.sistema-escala\sistema_escala.mv.db`, com
`CIPHER=AES` na URL de conexão. A senha e a chave AES são geradas aleatoriamente no
primeiro uso e gravadas em `banco.key`, no mesmo diretório, fora do controle de versão. O
diretório é criado com ACL restrita ao usuário no Windows e permissões `700`/`600` em
ambientes POSIX.

**Por quê.** Separar código de dados resolve os dois problemas de uma vez: o instalador
pode gravar em diretório protegido do sistema, e todo o estado mutável — banco, chave e
logs — fica no perfil privado do usuário. A criptografia protege contra a cópia do arquivo
para outra máquina.

**Limite reconhecido.** A chave fica na mesma máquina que o banco, porque precisa estar lá
para o programa funcionar. Isso significa que a criptografia **não** protege contra quem
ligue esta máquina e entre na conta do gestor. O tratamento é organizacional — BitLocker e
bloqueio de estação —, e o risco está aceito em `MODELAGEM_AMEACAS.md`, seção 1. Detalhes
em `PROTECAO_BANCO.md`.

### 3.7 `TransacaoUtil` para operações multi-escrita — issue #31

**Problema.** Cada método de repositório abre a própria `Connection`, e o H2 nasce em
autocommit. Numa operação que escreve em duas tabelas — registrar uma cobertura e lançar o
par crédito/débito no banco de horas, por exemplo — a primeira escrita já estaria gravada
quando a segunda falhasse, deixando o banco pela metade.

**Decisão.** `TransacaoUtil.executar(...)` abre uma `Connection` única, desliga o
autocommit e a repassa ao bloco. Commit se o bloco terminar sem exceção; `rollback` e
relançamento se qualquer exceção escapar. Os repositórios ganharam sobrecargas que recebem
a `Connection` de fora e não fazem `commit`, `rollback` nem `close`.

**Por quê.** É falha fechada (OWASP A10): ou todas as escritas valem, ou nenhuma vale.
Nunca engolir a exceção, nunca tentar salvar parte do trabalho. `RuntimeException` sobe como
veio; `SQLException`, que é checked e não cabe na assinatura, sobe embrulhada em
`RepositoryException` com a original como causa — o mesmo padrão que os repositórios já
usavam.

**Onde é usada.** Registro, edição e exclusão de cobertura com os lançamentos vinculados;
salvamento da escala e limpeza do mês (issue #44); geração do rodízio.

### 3.8 OpenPDF em vez de PDFBox para gerar o PDF — issue #50

Decisão registrada em detalhe em [`docs/decisao-openpdf-ao-inves-de-pdfbox.md`](../docs/decisao-openpdf-ao-inves-de-pdfbox.md); o
resumo cabe aqui porque a issue original pedia PDFBox pelo nome.

**Problema.** PDFBox não tem componente de tabela pronto, e a escala mensal é uma tabela
grande com quebra de página. O utilitário de desenho de linhas e células seria o trabalho
principal da issue.

**Decisão.** OpenPDF (`com.github.librepdf:openpdf-core-modern`) para **gerar** o PDF, e
PDFBox mantido apenas para **rasterizar** o PDF pronto e alimentar a pré-visualização da
tela de exportação (issue #52). Os dois convivem com papéis opostos: um escreve, o outro
lê.

**Por quê.** OpenPDF traz tabela pronta, e a licença (LGPL/MPL) não tem a cláusula viral do
AGPL do iText moderno. É o artefato `openpdf-core-modern` e não `openpdf`, porque este
último é a linha legada `com.lowagie` com as classes todas marcadas `@Deprecated`. A linha
2.x declara as dependências como `optional`, então nenhum jar transitivo entra e nada exige
rede — o que importa porque o empacotamento precisa funcionar offline. A 3.x arrasta
`brotli4j`, com binário nativo por plataforma, e quebraria isso.

### 3.9 Distribuição com `jpackage` e JRE embutida — issue #58

**Problema.** Exigir que a delegacia instale um JDK 21 é um passo a mais que pode falhar, e
uma dependência que sai do nosso controle no dia seguinte à entrega.

**Decisão.** Empacotamento nativo autocontido com `jpackage`, em dois formatos: pasta
portátil (`-Pempacotar-windows`) e instalador `.msi` (`-Pinstalador-msi`, exige WiX
Toolset). Ambos levam uma runtime Java 21 customizada embutida.

**Por quê.** O cliente final não precisa ter Java instalado, e a versão da JVM que testamos
é a mesma que roda na delegacia.

**Complemento de segurança (issue #36).** Certificado Authenticode comercial está fora do
orçamento de um projeto acadêmico, então a integridade do artefato é garantida por
**checksum SHA-256** publicado junto da entrega, com o procedimento de conferência descrito
no README. Registrou-se também a proibição de serialização Java (CWE-502), verificada por
`SegurancaIntegridadeESerializacaoTest`, que varre os pacotes e falha o build se qualquer
classe declarar `Serializable`. Risco residual e limitação em `MODELAGEM_AMEACAS.md`,
seção 2.

---

## 4. Rastreabilidade RF/RNF → issue

### 4.1 Como ler esta tabela

Os requisitos originais estão em
[`documentacao/legado/README_original_delegacia.md`](legado/README_original_delegacia.md).
O `scripts/BACKLOG_ISSUES.md` cita os códigos (RF05, RF10, RF11, RF12, RNF02, RNF05, RNF06,
RNF07, RNF08) mas não os define; a definição está no documento legado.

**Atenção à numeração.** Houve uma queda do GitHub durante a criação do backlog, e os
números do arquivo `scripts/BACKLOG_ISSUES.md` **não batem** com os do GitHub. A ponte é a
linha `_Backlog #N_` no rodapé de cada issue. As duas colunas abaixo trazem os dois
números; **o número do GitHub é o que vale** em commits e referências.

### 4.2 Requisitos funcionais

| RF | Requisito | Issues no GitHub | Backlog | Situação |
| --- | --- | --- | --- | --- |
| RF01 | Login com autenticação e diferenciação de perfis | #13, #15, #17, #30, #66, #68, #69 | 13, 15, 18, 62, 58, 60, 61 | Implementado, com divergência de nomenclatura (4.4) |
| RF02 | Cadastrar, editar e remover funcionários | #10, #20, #21, #22 | 10, 21, 22, 23 | Implementado, com divergência em "remover" (4.4) |
| RF03 | Gerar escala mensal | #41, #43, #46 | 27, 29, 32 | Implementado |
| RF04 | Selecionar funcionários para compor a escala | #42 | 28 | Implementado |
| RF05 | Validar conflitos de horário e impedir dupla escala no mesmo período | #23 | 26 | Implementado |
| RF06 | Editar escala mensal já criada | #42, #44, #46, #47, #48 | 28, 30, 32, 34, 36 | Implementado |
| RF07 | Salvar a escala no banco de dados | #11, #44 | 11, 30 | Implementado |
| RF08 | Exportar a escala em PDF | #50, #51, #52 | 41, 42, 43 | Implementado |
| RF09 | Registrar e consultar banco de horas por funcionário | #12, #26, #27, #28, #49 | 12, 37, 38, 40, 39 | Implementado |
| RF10 | Exigir mínimo de agentes por plantão | #23 | 26 | Implementado, com ressalva (4.4) |
| RF11 | Validar intervalo de descanso entre plantões | #40 | 25 | Implementado |
| RF12 | Administrador cadastra, edita e remove usuários | #19 | 20 | Implementado, com divergência em "remove" (4.4) |

### 4.3 Requisitos não funcionais

| RNF | Requisito | Issues no GitHub | Backlog | Situação |
| --- | --- | --- | --- | --- |
| RNF01 | Java com interface gráfica em JavaFX | #4 | 4 | Atendido (Java 21, JavaFX 21.0.2) |
| RNF02 | PostgreSQL como banco de dados | #1 | 1 | **Divergência deliberada** (3.1) |
| RNF03 | Senha armazenada com hash | #13, #66 | 13, 58 | Atendido (BCrypt custo 12) |
| RNF04 | Rodar localmente no computador da delegacia | #1, #7, #58 | 1, 7, 49 | Atendido |
| RNF05 | Interface usável sem treinamento técnico extenso | #16, #18, #38, #45, #59 | 17, 19, 16, 31, 50 | **Parcial** — depende do manual (#59, aberta) |
| RNF06 | Não permitir salvar escala com conflito não resolvido | #23, #44 | 26, 30 | Atendido com ressalva (4.4) |
| RNF07 | PDF legível e pronto para impressão | #51, #52 | 42, 43 | Atendido |
| RNF08 | Não permitir salvar escala que viole mínimo de agentes ou descanso | #23, #40, #32 | 26, 25, 64 | Atendido com ressalva (4.4) |

### 4.4 Divergências e lacunas registradas

Nada aqui é omissão; é diferença entre o requisito escrito no início e o sistema entregue,
registrada em vez de disfarçada.

**RNF02 — PostgreSQL não foi usado.** Divergência consciente, decidida na issue #1, cuja
justificativa está no próprio enunciado da issue. Ver 3.1. É a divergência de maior peso
desta lista.

**RF01 — os perfis não são "administrador e escrivão".** O requisito original nomeia
"administrador" e "escrivão". O sistema tem `admin` e `gestor`, e o `admin` é o suporte
técnico (a própria equipe de desenvolvimento), não uma patente. A diferença é de
nomenclatura e de papel, não de funcionalidade: continuam sendo dois perfis com permissões
distintas, aplicadas na camada de serviço (issue #30).

**RF02 e RF12 — "remover" foi implementado como desativação lógica.** O sistema não exclui
funcionário nem usuário em nenhuma tela. A razão está em 2.4: exclusão real destrói o
histórico de plantões e o extrato do banco de horas. A operação equivalente é
`ativo = false` (issue #22 para funcionário, #19 para usuário). O tensionamento com a LGPD
está na seção 3.3 de `LGPD_E_DADOS_PESSOAIS.md`.

**RF10 — o mínimo de agentes não é fixo em 2.** O requisito original diz "mínimo de 2
agentes". No sistema, 2 é apenas o valor padrão (`min_agentes INT NOT NULL DEFAULT 2` em
`tipo_turno` e em `escala_turno`); o valor efetivo é configurável por tipo de turno e
pode ser sobrescrito num turno específico, para feriado ou operação. É superconjunto do
requisito, não redução dele.

**RNF06 e RNF08 — o bloqueio acontece na alocação, não no salvamento.** O sistema não tem
rascunho: cada alocação grava direto no banco, e é no momento de alocar que
`RegraEscalaService.podeAlocar` recusa conflito, duplicidade e descanso insuficiente. Não
existe, portanto, um "salvar escala inválida" a barrar. Duas consequências que precisam
ficar explícitas:

- **Um turno pode ficar abaixo do mínimo**, porque nunca ter alocado ninguém não é o mesmo
  que ter violado uma regra. O gerador de rodízio, aliás, deixa o turno vazio de propósito
  quando o efetivo não fecha (seção 5).
- **A exportação do PDF não verifica completude da escala** — verificado no código da tela
  de exportação e do gerador. Um mês com dias incompletos exporta normalmente.

**RNF08 — a regra pode ser rompida com autorização.** O gestor pode alocar contra a regra
de descanso, desde que registre justificativa; o sistema grava regra, agente, data, quem
autorizou e quando, em `escala_excecao`, numa trilha que o repositório não deixa alterar
nem apagar (issue #32). Isso é a regra funcionando, não uma brecha: o requisito original não
previa o caso, e a operação real exige a válvula.

**RNF05 — depende de entrega ainda não feita.** O manual do usuário (issue #59) é parte do
requisito e ainda não existe. Enquanto ele não for escrito, o RNF05 está parcialmente
atendido: a interface segue o protótipo aprovado e o tema é consistente, mas não há
material de apoio para o operador.

**Requisitos sem cobertura no escopo do protótipo.** Nenhum RF ou RNF ficou sem issue.
Ficaram fora do escopo, com issue aberta no milestone `Futuro`, funcionalidades que **não**
constavam dos requisitos originais: meio plantão (#63), escala de sobreaviso (#64) e acesso
de visualização para os agentes (#29). O banco já suporta as duas primeiras.

---

## 5. Verificação por sabotagem como prática

Teste que passa não prova nada sozinho: prova que passou. Um teste pode passar porque o
código está certo ou porque o teste não olha para o que diz olhar — e os dois casos são
verdes na mesma cor.

A prática adotada neste projeto para as regras críticas é **quebrar a implementação de
propósito e confirmar que os testes falham**. Se o teste continua verde com o código
sabotado, ele não estava testando aquilo. A sabotagem é desfeita em seguida, e o resultado
fica registrado na descrição do pull request.

Isso não substitui cobertura; substitui a confiança cega em cobertura.

### 5.1 O que foi sabotado, e o que cada rodada revelou

| Regra | Sabotagem aplicada | Resultado | Onde |
| --- | --- | --- | --- |
| Sobreposição de horários | `isBefore` trocado por `isAfter` | 3 testes falharam | PR #125 (issue #23) |
| Sobreposição de horários | Removida uma das duas condições da comparação | Falhou o teste de períodos que encostam sem cruzar (08h–14h vs 14h–20h) | PR #125 |
| Descanso obrigatório | Ignorar plantões **posteriores** ao turno | 2 testes falharam | PR #126 (issue #40) |
| Descanso obrigatório | Truncar o `BigDecimal` do descanso para inteiro | **Só um teste falhou** — ver 5.2 | PR #126 |
| Descanso obrigatório | Inverter a comparação no limite exato | Falharam os testes de folga exata | PR #126 |
| Descanso na virada de mês | Limitar a janela de busca ao mês do turno | **Nenhum teste falhou** — ver 5.3 | PR #155 (issue #56) |
| Transação | `rollback()` trocado por `commit()` no `TransacaoUtil` | 2 testes de rollback falharam (`expected 0, was 2`) | PR #119 (issue #31) |

### 5.2 O truncamento do `BigDecimal` que só um teste pegou

O caso mais instrutivo. `intervalo_descanso_horas` é `NUMERIC(5,2)`, e o serviço converte
para `Duration` passando por segundos com `RoundingMode.HALF_UP`.

Ao truncar o valor para horas inteiras — o bug que um programador escreveria sem pensar —
**apenas o teste do regime de 36,5h falhou**. Todos os regimes redondos (24h, 36h, 72h)
continuaram verdes, porque para eles truncar não muda nada.

O que isso demonstra: sem aquele único teste, um bug real de truncamento passaria
despercebido por toda a suíte, e apareceria só quando um cliente cadastrasse um regime
fracionário. O teste está preservado com o comentário que explica por que ele existe:

```java
// Regime de 36,5h: 36h de folga ainda e pouco. Se o valor fosse
// truncado para 36 inteiro, este caso passaria por engano.
```

### 5.3 A sabotagem que **não** quebrou nada — e o que isso revelou

Na cobertura de virada de mês e de ano, limitar a janela de busca ao mês do turno não fez
nenhum teste falhar. Isso não foi um teste ruim: foi uma descoberta sobre onde a regra
realmente mora.

O filtro efetivo acontece na comparação de períodos dentro do serviço, não na janela de
busca. Sabotando a comparação (`isBefore` → `isAfter` no descanso posterior), os testes
falharam como deviam.

A consequência ficou registrada em vez de escondida: aqueles testes provam que a comparação
de datas atravessa a virada corretamente, mas **não** provam que a consulta ao banco traz o
plantão do mês anterior — com Mockito, o dublê devolve o que foi mandado. Essa parte cabe
aos testes de integração (issue #57), e está anotada como tal.

### 5.4 A dupla fixa no rodízio: o que teste unitário nenhum pegou

O gerador de rodízio (issue #43) passava em todos os testes unitários da primeira versão. A
**verificação visual**, rodando a aplicação e olhando o mês gerado, revelou um bug real que
a suíte não via: com três agentes, "a" e "b" formavam **dupla fixa** e "c" trabalhava sempre
sozinho, em turno sempre incompleto.

A causa não era óbvia. Escalar o agente solitário num turno abaixo do mínimo **congela o
rodízio**: ele passa a descansar em fase com aquele dia e volta a ficar livre sempre na
mesma posição do ciclo, o que trava para sempre quem trabalha com quem.

A correção foi o critério de desempate e a regra de **tudo ou nada**: quando a fila inteira
é percorrida e o mínimo não fecha, a alocação parcial é desfeita e o turno fica vazio, com
o dia contabilizado como incompleto. Segurando esse agente, o ciclo de descanso dele se
desloca e as duplas voltam a girar — agosto/2026 passou a produzir `a+b, c+a, b+c, a+b,
c+a, b+c, a+b, c+a`.

A troca não custa cobertura de verdade: o que se perde são turnos que já estavam abaixo do
mínimo. A quantidade de turnos **completos** é a mesma.

Só depois de entender a causa foi possível escrever o teste que a captura,
`naoFormaDuplaFixaQuandoOEfetivoNaoFechaTodosOsDias`, que reproduz o cenário exato (três
agentes, dois por turno, 72h de descanso) e afirma três coisas que a versão original violava:
as três duplas possíveis aparecem, a carga fica equilibrada, e nenhum turno fica com agente
solitário. Hoje esse é um teste de regressão; ele não teria sido escrito sem a observação
visual que o motivou.

**A lição registrada:** teste unitário verifica a regra que você pensou em verificar.
Propriedade emergente de um algoritmo iterativo — quem acaba trabalhando com quem, ao longo
de um mês inteiro — não aparece num teste de caso isolado. Para essa classe de problema, a
verificação visual não é etapa opcional.

A continuidade entre meses foi confirmada da mesma forma: setembro segue o ciclo de agosto
em vez de reiniciar.

### 5.5 Limite honesto da prática

Sabotagem prova que o teste detecta **aquela** falha específica, e não que a implementação
está correta em geral. Não é prova formal e não substitui os testes de integração contra o
banco real (issue #57), que verificam o que os dublês de Mockito não podem verificar.

O que ela entrega é barato e concreto: a certeza de que um teste verde é verde por mérito
do código, não por acidente do teste.
