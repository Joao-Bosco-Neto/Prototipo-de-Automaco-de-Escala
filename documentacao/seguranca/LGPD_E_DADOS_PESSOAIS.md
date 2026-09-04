# LGPD e dados pessoais

Classificação dos dados pessoais que o sistema armazena, decisão sobre o CPF,
política de retenção e orientação de segurança para a organização que usa o
sistema.

Complementa `MODELAGEM_AMEACAS.md` (atores, ativos e riscos aceitos) e
`PROTECAO_BANCO.md` (proteção do arquivo do banco). Referência do OWASP:
A04 — Design Inseguro, na parte que orienta classificar os dados e não
armazenar o que não for necessário.

## Escopo

O sistema é uma aplicação desktop monousuário e offline. Todos os dados
pessoais ficam em um único arquivo H2 na máquina do gestor, cifrado com
`CIPHER=AES` (ver `PROTECAO_BANCO.md`). Não há servidor, não há transmissão
pela rede e não há integração com terceiros: nenhum dado pessoal sai da
máquina por iniciativa do programa.

Os dados saem da máquina apenas quando o gestor exporta um arquivo — PDF da
escala mensal ou PDF/CSV do banco de horas —, e o arquivo exportado não herda
a criptografia do banco. Isso é tratado em *Riscos aceitos*.

## 1. Classificação dos dados por sensibilidade

As classificações usadas:

- **Identificação direta** — aponta para uma pessoa específica sozinho.
- **Dado de contato** — permite alcançar a pessoa fora do trabalho.
- **Dado operacional sensível** — não identifica sozinho, mas revela onde uma
  pessoa estará, quando, e com que efetivo. É o dado de maior valor para quem
  não deveria tê-lo.
- **Credencial** — sustenta o acesso ao próprio sistema.
- **Metadado de auditoria** — registra quem fez o quê e quando.

### 1.1 Pessoas escaladas — `funcionario`

| Campo | Para que serve | Classificação |
| --- | --- | --- |
| `nome` | Identificar o agente nas telas e no PDF entregue à direção | Identificação direta |
| `matricula` | Identificador do funcionário em todo o sistema (`NOT NULL UNIQUE`); é o que aparece na trilha de auditoria no lugar do nome | Identificação direta |
| `telefone` | Acionar o agente para cobertura de plantão; opcional | Dado de contato |
| `observacoes` | Texto livre do gestor sobre o agente (até 1000 caracteres) | Indeterminado — ver *Riscos aceitos* |
| `ativo` | Sustenta o filtro Ativos/Inativos e a desativação lógica | Dado operacional |
| `criado_em` | Data de cadastro | Metadado |

### 1.2 Contas de acesso — `usuario`

| Campo | Para que serve | Classificação |
| --- | --- | --- |
| `nome` | Exibição do operador logado na interface | Identificação direta |
| `login` | Autenticação; é também a identificação gravada na trilha de auditoria | Identificação direta |
| `senha_hash` | Hash BCrypt da senha; a senha em texto claro nunca é armazenada | Credencial |
| `role` | Perfil (`admin` ou `gestor`), base do controle de acesso | Dado operacional |
| `ativo` | Conta desativada não autentica; o sistema não exclui usuário | Dado operacional |
| `ultimo_login` | Último acesso bem-sucedido | Metadado de auditoria |
| `criado_em` | Data de criação da conta | Metadado |

O `usuario` é o operador do programa (gestor/administrador); o `funcionario` é
a pessoa escalada. São tabelas separadas e não há vínculo entre elas: um agente
escalado não tem conta no sistema.

### 1.3 A escala — `escala_turno` e `escala_funcionario`

Este é o dado de maior sensibilidade do sistema, e ele não é óbvio olhando
campo a campo: nenhuma coluna aqui é "dado pessoal" isoladamente. O conjunto é.
Saber que uma unidade terá dois agentes nominados numa data e hora específicas
é informação operacionalmente sensível — tem valor para quem não deveria
tê-la, e o risco recai sobre a segurança física das pessoas escaladas, não
sobre a privacidade delas.

| Campo | Para que serve | Classificação |
| --- | --- | --- |
| `escala_turno.inicio` / `fim` | Data e hora exatas do plantão | Dado operacional sensível (em conjunto) |
| `escala_turno.min_agentes` / `max_agentes` | Efetivo previsto para o turno | Dado operacional sensível (em conjunto) |
| `escala_funcionario.funcionario_id` | Quem cumpre o plantão | Dado operacional sensível (em conjunto) |
| `escala_funcionario.inicio` / `fim` | Turno parcial (meio plantão), quando preenchidos | Dado operacional sensível |
| `escala_funcionario.cobertura_de` e `motivo_cobertura_id` | Quem substituiu quem, e por quê | Dado operacional sensível — o motivo pode revelar ausência, licença ou afastamento |
| `escala_funcionario.observacao` | Texto livre sobre a alocação (até 500 caracteres) | Indeterminado — ver *Riscos aceitos* |
| `escala_turno.observacao` | Texto livre sobre o turno (até 255 caracteres) | Indeterminado — ver *Riscos aceitos* |

`motivo_cobertura` é uma tabela, não uma lista fixa no código: o repositório
expõe `inserir` e `atualizar`, de modo que os nomes dos motivos são definidos
pela organização e não pelo desenvolvedor. A tabela não guarda dado pessoal por
si, mas os nomes cadastrados ali passam a qualificar ausências de pessoas
nomeadas — um motivo como "licença médica" transforma a linha de cobertura
correspondente em informação de saúde vinculada a um agente identificado. Isso
está registrado em *Riscos aceitos*.

### 1.4 Banco de horas — `lancamento_horas`

O extrato é o histórico financeiro-trabalhista do agente. Não expõe onde ele
estará, mas expõe o padrão de trabalho dele ao longo do tempo: quantas
coberturas fez, quantas ausências teve, quantos ajustes manuais o gestor
lançou.

| Campo | Para que serve | Classificação |
| --- | --- | --- |
| `funcionario_id` | Dono do lançamento | Identificação indireta |
| `data_referencia` | Dia a que o lançamento se refere | Dado operacional sensível |
| `minutos` | Crédito (positivo) ou débito, guardado em minutos | Dado operacional |
| `tipo` | `credito_cobertura`, `debito_ausencia`, `credito_extra` ou `ajuste_manual` — `debito_ausencia` indica que o agente faltou a um plantão | Dado operacional sensível |
| `descricao` | Texto curto explicando o lançamento | Indeterminado — ver *Riscos aceitos* |

### 1.5 Exceções autorizadas — `escala_excecao`

Trilha de auditoria de escala: registra que o gestor alocou alguém sabendo que
a regra de descanso mínimo não fechava. As colunas `funcionario_id` e
`data_plantao` são denormalizadas de propósito, para o registro continuar
legível depois que a escala do mês for apagada.

| Campo | Para que serve | Classificação |
| --- | --- | --- |
| `funcionario_id` e `data_plantao` | Sobre quem e sobre qual plantão foi a exceção | Dado operacional sensível |
| `regra` e `descricao` | Qual regra foi violada e o contexto | Dado operacional sensível — indica jornada acima do previsto para uma pessoa identificada |
| `autorizado_por` | Login do usuário que autorizou (não o nome) | Metadado de auditoria |

### 1.6 Trilha de auditoria de segurança — `log_seguranca`

Implementada na issue #32 (OWASP A09). Registra login bem-sucedido e falho,
logout, criação/desativação/reativação de usuário, redefinição de senha,
autorização de exceção de escala, exclusão de cobertura e limpeza de mês
inteiro da escala.

| Campo | O que guarda | Classificação |
| --- | --- | --- |
| `data_hora` | Momento do evento | Metadado de auditoria |
| `identificacao` | O **login** de quem agiu; num login falho, a string tentada — que pode nem corresponder a um usuário existente | Identificação direta |
| `acao` e `resultado` | Ação executada e desfecho (`sucesso`/`falha`) | Metadado de auditoria |
| `detalhes` | Texto curto do que a ação mudou | Ver abaixo |

O que `detalhes` efetivamente recebe hoje, verificado nos pontos de chamada:

- **Login falho:** o motivo (`usuario inativo` ou `senha incorreta`).
- **Usuário criado:** o `login` da conta criada, o perfil e o status.
- **Senha redefinida:** o `login` da conta alvo — nunca a senha nem o hash
  gerado (CWE-532).
- **Conta desativada ou reativada:** apenas o `login` da conta alvo.
- **Exceção de escala autorizada:** a regra, a **matrícula** do agente, a data
  do plantão e o id da alocação.
- **Cobertura excluída:** o id da cobertura e as **matrículas** do substituto e
  do ausente, mais a data do plantão.
- **Mês limpo:** o mês e a contagem de turnos e alocações removidos.

Duas decisões de projeto se aplicam a esta tabela e valem registrar aqui:

- **A trilha identifica pessoas por matrícula e login, nunca por nome
  completo.** É minimização aplicada ao próprio artefato de auditoria: a
  trilha precisa ser suficiente para reconstituir o que aconteceu, não para
  substituir o cadastro. Quem precisar do nome chega nele por consulta.
- **A trilha não guarda senha nem hash em nenhuma hipótese** (CWE-532), e o
  texto que vem de entrada livre do usuário é sanitizado de quebras de linha
  antes de gravar (CWE-117) e truncado no limite da coluna.

### 1.7 Log de aplicação — arquivo, fora do banco

Além do `log_seguranca`, existe um log técnico em
`~/.sistema-escala/logs/aplicacao.log`, limitado a 2 MB e reaberto do zero ao
atingir o limite. Ele não fica dentro do banco cifrado.

O que ele registra de pessoal é pouco e deliberado: quando a gravação da
trilha de segurança falha, vai para lá só o nome da ação, nunca a
identificação de quem agiu. Separadamente, o serviço de bloqueio por
inatividade registra o **login** da sessão bloqueada pelo `System.Logger` da
JDK, sanitizado de quebras de linha antes de ser escrito. Nome, telefone,
escala e senha não entram no log de aplicação em nenhum ponto.

### 1.8 O que o sistema **não** guarda

Registrado explicitamente porque é o resultado da aplicação do princípio de
minimização, e porque a ausência é tão relevante quanto a presença:

- CPF (ver seção 2), RG ou qualquer documento oficial.
- Endereço residencial e e-mail.
- Data de nascimento, filiação, estado civil.
- Dados bancários ou remuneração.
- Dados pessoais sensíveis no sentido do art. 5º, II da LGPD — saúde,
  biometria, origem racial, convicção religiosa, opinião política, filiação
  sindical — em campo próprio. A ressalva sobre campos de texto livre está em
  *Riscos aceitos*.
- Senha em texto claro: apenas o hash BCrypt.
- Qualquer dado do agente escalado além de nome, matrícula, telefone e
  observações.

## 2. Decisão: remoção do CPF

**Situação anterior.** O backlog previa `funcionario.cpf`, com validação por
dígito verificador, no cadastro de funcionários.

**Decisão.** O campo foi removido do modelo. Não existe em `schema.sql`, em
`Funcionario.java`, no repositório JDBC nem em qualquer tela — verificado.

**Justificativa.** A `matricula` já identifica o funcionário em todo o sistema:
é `NOT NULL UNIQUE`, é o que aparece nas telas, nos relatórios e na trilha de
auditoria. O CPF não sustentava nenhuma funcionalidade — nenhum cálculo,
nenhuma integração, nenhum relatório dependia dele. Era retenção sem
finalidade, exatamente o que o art. 6º, III da LGPD (necessidade) veda.

Além disso, o CPF é o identificador que mais eleva o impacto de um vazamento:
sozinho, ele liga a pessoa a cadastros fora do sistema, e é a chave que
viabiliza fraude de identidade. Sua presença mudaria a natureza do incidente em
caso de cópia do banco — de "a escala vazou" para "a identidade civil de
agentes vazou".

Isto segue a orientação do **OWASP A04**: classificar os dados e não armazenar
o que não for necessário. Dado que não é retido não pode ser roubado, não
precisa ser cifrado, não precisa entrar na política de retenção e não aparece
em relatório de incidente.

**Consequência aceita.** Se, no futuro, o cliente precisar de integração com
folha de pagamento ou com um sistema que use CPF como chave, o campo terá de
voltar. Nesse momento, esta decisão precisa ser revista junto com a
classificação acima, não desfeita em silêncio.

## 3. Política de retenção

### 3.1 Prazos por tipo de dado

| Dado | Retenção | Base |
| --- | --- | --- |
| Cadastro do funcionário (`funcionario`) | Enquanto houver histórico de plantão vinculado; na prática, permanente | Ver 3.3 |
| Escala (`escala_turno`, `escala_funcionario`) | Enquanto o mês for relevante para auditoria de escala e conferência do banco de horas | Necessidade operacional |
| Banco de horas (`lancamento_horas`) | Enquanto o saldo for exigível pelo agente ou pela organização | O saldo é derivado por soma do extrato: apagar lançamento antigo altera o saldo atual |
| Exceções autorizadas (`escala_excecao`) | Permanente | Trilha de auditoria imutável — o repositório não tem `UPDATE` nem `DELETE` |
| Trilha de segurança (`log_seguranca`) | Permanente | O repositório não expõe exclusão; ver 3.4 |
| Contas de acesso (`usuario`) | Permanente, desativadas quando não usadas | A trilha de auditoria referencia o `login` de quem agiu |
| Log de aplicação (arquivo) | Até 2 MB; o arquivo é reaberto do zero ao atingir o limite | Comportamento do `FileHandler`, não uma política |
| PDF/CSV exportados | Fora do controle do sistema | Ver 3.5 |

Os prazos das três primeiras linhas não são um número fixo neste documento de
propósito: eles dependem do prazo de guarda que a própria organização observa
para documentação de jornada, que é decisão do cliente e não do software. O que
o sistema garante é que **nada é apagado automaticamente** — a decisão de
descartar é sempre humana e explícita.

### 3.2 O que acontece quando um funcionário é desativado

O sistema **nunca exclui um funcionário**. A operação disponível é a
desativação lógica: `funcionario.ativo = false`. Verificado — o
`FuncionarioRepository` expõe `ativar(int)` e `desativar(int)`, e não há
nenhuma operação de exclusão.

Efeitos da desativação:

- O funcionário deixa de ser oferecido para novas alocações: o painel de
  atribuição monta a lista de candidatos apenas com os ativos.
- Se ele já estava escalado em plantões futuros, essas alocações **não são
  desfeitas** — o painel de alertas sinaliza a pendência, citando o nome, para
  o gestor resolver manualmente.
- Todo o histórico permanece intacto: plantões cumpridos, coberturas feitas,
  lançamentos do banco de horas e exceções autorizadas.
- O registro continua legível em relatórios de períodos anteriores.
- Nome, matrícula, telefone e observações **continuam armazenados**.

### 3.3 Tensionamento com o princípio de minimização — registrado

Esta é uma decisão consciente que **tensiona com o art. 15 e com o princípio
de necessidade (art. 6º, III) da LGPD**, e vale registrá-la honestamente em vez
de apresentar a desativação lógica como se fosse conformidade plena.

**O tensionamento.** A LGPD orienta eliminar o dado pessoal quando a finalidade
que justificou a coleta se encerra. Quando um agente deixa a organização, a
finalidade "escalá-lo para plantões" acaba. Se o critério fosse só esse, o
cadastro deveria ser eliminado.

**Por que o histórico é preservado mesmo assim.** Duas finalidades continuam
vivas depois da saída do agente, e as duas são finalidades legítimas e
distintas da original:

1. **Auditoria de escala.** A escala é o registro de quem estava de plantão em
   cada data. Apagar o funcionário destrói esse registro para todos os
   plantões passados — e a escala de um mês passado precisa continuar
   respondendo "quem estava de serviço neste dia" mesmo que a pessoa não
   trabalhe mais ali. É a mesma razão pela qual `escala_excecao` guarda
   `funcionario_id` e `data_plantao` denormalizados: o registro do que
   aconteceu não pode depender de quem ainda está na equipe.

2. **Banco de horas.** O saldo é derivado por soma dos lançamentos, não
   guardado em uma coluna. O extrato de um agente desligado é a prova do saldo
   que ele tinha ao sair — que pode ser objeto de acerto, de conferência ou de
   questionamento posterior, pelo próprio agente ou pela organização. Apagar os
   lançamentos apaga a prova em ambas as direções.

Há ainda uma razão técnica: `escala_funcionario`, `lancamento_horas` e
`escala_excecao` referenciam `funcionario(id)` sem `ON DELETE CASCADE`. Uma
exclusão real ou seria recusada pelo banco, ou — se a cascata fosse adicionada
— levaria junto todo o histórico das duas finalidades acima. Não existe meio
termo em que o funcionário some e o histórico permaneça íntegro.

**O que isso implica, dito sem eufemismo:**

- **Não existe hoje "direito ao esquecimento" implementado.** Um pedido de
  eliminação de dados (art. 18, VI da LGPD) não tem, no sistema, um botão que
  o atenda. Atendê-lo exigiria intervenção manual no banco, com o custo de
  quebrar o histórico descrito acima.
- **Dado de contato é retido além da sua finalidade.** O `telefone` serve para
  acionar o agente para cobertura. Depois do desligamento, essa finalidade
  acabou — mas o campo continua preenchido. É o item da retenção com a
  justificativa mais fraca, e o mais indicado para uma futura anonimização
  seletiva.
- **A base legal se desloca.** Enquanto o agente está ativo, a retenção se
  apoia na execução do contrato de trabalho e na gestão da jornada. Depois do
  desligamento, ela se apoia na obrigação de guarda de documentação
  trabalhista e no exercício regular de direitos — que são finalidades
  legítimas, mas **não são as mesmas**, e é a organização, não o software, que
  responde por elas.

**Encaminhamento sugerido, não implementado.** O tratamento proporcional a
esse tensionamento seria uma **anonimização seletiva** do funcionário
desativado após o prazo de guarda: substituir `nome` por um rótulo, limpar
`telefone` e `observacoes`, e manter `matricula`, `id` e todo o histórico
vinculado. Isso preservaria as duas finalidades acima e eliminaria o dado que
não tem mais finalidade nenhuma. **Isso não existe no sistema hoje** e está
registrado aqui como recomendação, não como funcionalidade.

### 3.4 Retenção da trilha de auditoria

`log_seguranca` e `escala_excecao` são propositalmente permanentes. Uma trilha
de auditoria com expurgo automático é uma trilha que um invasor pode esperar
expirar, e uma trilha que pode ser apagada de dentro do sistema não é trilha.
Por isso os repositórios não expõem exclusão.

O custo é conhecido e aceito: a trilha guarda `login` — dado pessoal — por
tempo indeterminado. Ele foi minimizado no conteúdo (matrícula e login em vez
de nome completo, seção 1.6), não no prazo.

### 3.5 Retenção do que foi exportado

O PDF da escala e o PDF/CSV do banco de horas são arquivos comuns, gravados
onde o gestor escolher. A partir do momento em que são gerados, o sistema não
tem mais controle sobre eles: não os rastreia, não os apaga e não sabe para
onde foram. Retenção e descarte desses arquivos são responsabilidade da
organização — ver seção 4 e *Riscos aceitos*.

## 4. Orientação de segurança para a organização

> **Nota:** Esta seção deve ser incorporada ao manual do usuário (issue #59)
> quando ele for escrito.

Esta seção é escrita para o gestor, não para quem desenvolve. O sistema protege
o que está sob o controle dele — o arquivo do banco é cifrado e o acesso exige
login. O que está fora do controle do programa depende da organização, e são
essas medidas que valem mais do que qualquer ajuste no software.

### 4.1 Criptografia de disco na máquina onde o sistema roda

**O que fazer:** habilitar a criptografia de disco do Windows (BitLocker) na
máquina onde o sistema está instalado.

**Por quê:** o sistema já guarda o banco de dados em um arquivo cifrado, mas a
chave que abre esse arquivo fica na mesma máquina — ela precisa estar lá para o
programa funcionar. Isso significa que a criptografia do banco protege contra
alguém que copie o arquivo e tente abri-lo em outro computador, mas **não**
protege contra alguém que ligue esta máquina e entre na conta do gestor. A
criptografia de disco é o que fecha essa porta: se o computador for levado, o
disco não abre sem a senha do Windows.

Esta é a medida de proteção mais eficaz que a organização pode adotar, e não
depende de nenhuma mudança no sistema.

### 4.2 Tela bloqueada quando o gestor se ausenta

**O que fazer:** bloquear a tela (tecla Windows + L) sempre que sair da frente
do computador, mesmo por pouco tempo.

**O que o sistema já faz:** o sistema bloqueia a sessão sozinho depois de
**15 minutos** sem uso, e exige a senha do gestor para voltar (issue #69). Esse
bloqueio é uma rede de proteção, não um substituto: entre o momento em que o
gestor sai da sala e o momento em que o bloqueio entra, a escala inteira está
visível na tela para quem passar por ali.

**Por quê:** a informação mais sensível deste sistema — quem estará de plantão,
onde e quando — é justamente a que fica aberta na tela durante o uso normal.
Ela não precisa ser copiada para vazar; basta ser vista, ou fotografada.

### 4.3 Cópia de segurança do arquivo do banco

**O que fazer:** copiar periodicamente o arquivo do banco para um local seguro,
e verificar de tempos em tempos que a cópia realmente abre.

**Onde ficam os arquivos:** na pasta `.sistema-escala`, dentro da pasta do
usuário do Windows (`%USERPROFILE%\.sistema-escala`). Ela contém o banco
(`sistema_escala.mv.db`), o arquivo de chave que o abre (`banco.key`) e a pasta
`logs` com o log técnico do programa.

**Dois pontos que costumam ser esquecidos:**

- **A cópia do banco não serve sem o arquivo de chave.** O banco é cifrado; sem
  a chave, a cópia é um arquivo ilegível. Guarde os dois — e entenda a
  contrapartida: guardá-los juntos significa que quem obtiver o backup obtém o
  conteúdo. O local do backup precisa ter o mesmo cuidado que a máquina
  original.
- **O sistema não faz backup sozinho.** Não há rotina automática de cópia. Se
  ninguém copiar, não há cópia.

### 4.4 Quem tem acesso físico à máquina

**O que fazer:** tratar a máquina onde o sistema roda como um armário de
documentos restritos.

Na prática:

- A conta do Windows do gestor deve ter senha, e essa senha não se compartilha.
- Cada pessoa que opera o sistema deve ter o **seu próprio login**. Login
  compartilhado destrói a trilha de auditoria: o sistema registra quem criou
  um usuário, quem redefiniu uma senha, quem autorizou uma exceção de escala e
  quem apagou o mês — e todos esses registros passam a apontar para "a conta
  que todo mundo usa".
- Quem sai da organização deve ter a conta **desativada** no sistema, no mesmo
  dia. O sistema não exclui contas, justamente para a trilha de auditoria
  continuar fazendo sentido, mas a conta desativada não entra mais.
- Visitantes e terceiros — inclusive suporte técnico — não devem ficar sozinhos
  com a máquina desbloqueada.

**Por quê:** quem tem acesso físico à máquina, com a sessão do Windows aberta,
tem acesso a tudo. É o limite reconhecido da proteção deste sistema, e está
registrado como risco aceito em `MODELAGEM_AMEACAS.md`.

### 4.5 Cuidado com os arquivos exportados

**O que fazer:** tratar o PDF da escala e a planilha do banco de horas com o
mesmo cuidado do sistema.

**Por quê:** o PDF da escala contém os nomes dos agentes e as datas dos
plantões — e, se as opções de exportação estiverem marcadas, também os
**telefones** e o **saldo do banco de horas** de cada um. Esse arquivo **não é
cifrado**: uma vez salvo, ele é um arquivo comum, que pode ser copiado para um
pen drive, anexado a um e-mail ou impresso e esquecido em cima de uma mesa.

Na prática: exporte só o que precisa (as caixas de telefone e de saldo vêm
desmarcadas por padrão — é uma escolha marcá-las), envie o arquivo só a quem
precisa recebê-lo, e apague as cópias que não são mais necessárias.

## 5. Riscos aceitos

Seguindo o formato de `MODELAGEM_AMEACAS.md`: risco identificado, por que é
aceito, e o que sustenta o aceite.

| Risco | Referência | Aceite e justificativa |
| --- | --- | --- |
| Retenção do cadastro do funcionário desativado por prazo indeterminado | LGPD art. 6º, III / art. 15 | Aceito. O histórico de plantões e o extrato do banco de horas dependem do cadastro para permanecerem íntegros e legíveis. Tensionamento e encaminhamento registrados na seção 3.3 |
| Retenção do `telefone` de funcionário desativado, sem finalidade ativa | LGPD art. 6º, III | Aceito por ora, com a ressalva de que é o item de justificativa mais fraca da retenção. A anonimização seletiva da seção 3.3 é o tratamento indicado quando for implementada |
| Ausência de mecanismo de eliminação de dados a pedido do titular | LGPD art. 18, VI | Aceito. Não há botão que atenda o pedido; atendê-lo hoje exige intervenção manual no banco, com quebra de histórico. A limitação está registrada em vez de disfarçada |
| Campos de texto livre (`funcionario.observacoes`, `escala_funcionario.observacao`, `escala_turno.observacao`, `lancamento_horas.descricao`) podem receber dado pessoal sensível | LGPD art. 5º, II | Aceito. O sistema não pode impedir que o gestor digite "afastado por problema de saúde" num campo de observação, e restringir o campo inviabilizaria seu uso legítimo. Tratamento: orientação ao cliente para não registrar informação de saúde, disciplinar ou de foro íntimo nesses campos |
| Nomes cadastrados em `motivo_cobertura` podem qualificar ausências como dado de saúde | LGPD art. 5º, II | Aceito. Os motivos são dados da tabela de propósito, para o sistema servir a organizações diferentes sem nova versão. Tratamento: orientar a organização a usar motivos genéricos ("ausência justificada") em vez de motivos que revelem a causa |
| PDF e CSV exportados saem sem criptografia e fora do controle do sistema | CWE-312 | Aceito. O PDF existe para ser entregue à direção e afixado — cifrá-lo anularia sua finalidade. Tratamento: opções de telefone e saldo desmarcadas por padrão na tela de exportação, e orientação ao cliente na seção 4.5 |
| Trilha de auditoria retém `login` por tempo indeterminado | LGPD art. 6º, III | Aceito. Trilha com expurgo automático é trilha que se pode esperar expirar. Minimizado no conteúdo (matrícula e login, nunca nome completo), não no prazo — seção 3.4 |
| `escala_funcionario` e `escala_turno` visíveis na tela durante todo o uso normal | Residual | Aceito. É a tela principal do produto. Tratamento: bloqueio automático por inatividade em 15 minutos (issue #69) e orientação de bloqueio manual na seção 4.2 |
| Quem controla a conta do Windows do gestor lê o banco e a chave | CWE-922, CWE-732 | Já aceito em `MODELAGEM_AMEACAS.md` e `PROTECAO_BANCO.md`; repetido aqui porque define o limite de tudo que este documento descreve. Tratamento recomendado: BitLocker |
| Ausência de MFA | Residual | Aceito. Aplicação desktop offline monousuário, sem canal para segundo fator. Compensado por bloqueio por inatividade, atraso progressivo nas tentativas de login e trilha de logins falhos |

## Escopo deste documento

Este documento descreve o estado do sistema em setembro de 2026 e foi escrito a
partir de `schema.sql`, dos models e dos pontos de chamada da trilha de
auditoria — não a partir do backlog. Se um campo for adicionado ao schema, a
seção 1 precisa ser revista junto.

Ele não é uma avaliação jurídica de conformidade com a LGPD e não substitui uma.
É o registro técnico de quais dados pessoais o sistema trata, com que
justificativa, por quanto tempo, e onde as decisões de projeto tensionam com a
lei.
