# Guia de Contribuição

Padronização de Workflow de Desenvolvimento (Git & GitHub)
**Projeto:** Prototipo-de-Automaco-de-Escala
**Versão:** 1.0

---

## 1. Introdução

Este documento orienta a integração técnica de todos os colaboradores do projeto. O
objetivo é manter a qualidade e a consistência do código por meio de um processo
claro, organizado e rastreável, baseado em **GitHub Issues**, **Conventional Commits** e
**Pull Requests com revisão de código**.

---

## 2. O Ciclo de Desenvolvimento com GitHub Issues

Todas as tarefas — desenvolvimento, correção de bugs, documentação e tarefas
administrativas — são centralizadas nas **Issues** do repositório.

### 2.1 Selecionando uma Issue

1. Acesse o repositório no GitHub e clique na aba **Issues**.
2. Escolha a issue correspondente à tarefa que você vai realizar (verifique labels
   como `backend`, `banco`, `ui`, `pdf`, `docs`).
3. Atribua a issue a si mesmo (**Assignee**) na barra lateral direita.
4. Se estivermos usando um quadro de Projeto (Project board), mova o card para
   **In Progress**.

---

## 3. Gerenciamento de Branches

### 3.1 Branches principais

- **`main`** — branch de produção. Contém a versão estável e entregável do
  protótipo. Ninguém commita diretamente aqui.
- **`develop`** — branch de integração. Recebe as features já concluídas e testadas.
  É a base para novas branches de trabalho.

Toda branch de trabalho nasce a partir de `develop` e é reintegrada nela via Pull
Request após aprovação.

### 3.2 Padrão de nomenclatura

```
<numero-da-issue>-<slug-da-issue>
```

Exemplo: issue #7 — "[Backend] Validação de conflito de horário" → branch
`7-backend-validacao-de-conflito-de-horario`.

### 3.3 Criando a branch

Preferencialmente pela própria issue no GitHub: abra a issue → seção
**Development** → **Create a branch** → mantenha o nome sugerido e confirme que a
origem (*Source*) é `develop`.

### 3.4 Sincronizando localmente

```bash
# Atualiza os metadados do repositório remoto
git fetch origin

# Alterna para a branch criada a partir da issue
git checkout 7-backend-validacao-de-conflito-de-horario
```

---

## 4. Padronização de Commits

Seguimos **Conventional Commits**.

### 4.1 Estrutura

```
<tipo>[escopo opcional]: <descrição curta>
```

### 4.2 Tipos suportados

| Tipo       | Finalidade                                       | Exemplo                                                  |
|------------|---------------------------------------------------|-----------------------------------------------------------|
| `feat`     | Nova funcionalidade                                | `feat(escala): valida conflito de horario do agente`      |
| `fix`      | Correção de bug técnico ou funcional               | `fix(banco): corrige unique de cpf duplicado`              |
| `docs`     | Alteração exclusiva de documentação                | `docs: atualiza guia de contribuicao`                      |
| `style`    | Ajuste estético/formatação (sem mudar lógica)      | `style: formata indentacao do EscalaService`                |
| `refactor` | Reestruturação de código existente                 | `refactor(pdf): extrai geracao de cabecalho`                |
| `test`     | Inclusão/ajuste de testes automatizados            | `test(escala): adiciona teste de descanso 72h`              |
| `chore`    | Build, dependências, CI/CD, configs gerais         | `chore: atualiza driver jdbc postgresql`                    |

### 4.3 Diretrizes práticas

- **Mensagem curta:** a primeira linha (título) deve ter no máximo 50 caracteres.
- **Seja específico:** mensagens genéricas como `fix: corrigindo bug` ou
  `feat: alterações gerais` são proibidas.
- **Referencie a issue** no rodapé do commit que encerra o escopo dela, ex:
  `Closes #7`.

---

## 5. Pull Requests e Code Review

Nenhuma alteração entra diretamente em `main` ou `develop`. Toda entrega passa por
revisão formal.

### 5.1 Criando o PR

1. Após concluir e enviar (`git push`) sua branch, clique em **Compare & pull
   request** no GitHub.
2. Confirme que a branch de destino (**Base**) está correta — normalmente
   `develop`.
3. Dê um título claro ao PR, ex: `feat(escala): implementa validacao de conflito de horario`.

### 5.2 Vinculando issues

Referencie a issue no corpo do PR usando `Closes #7` (ou `Fixes`/`Resolves`). Isso
fecha a issue automaticamente ao mesclar.

### 5.3 Revisão

- Pelo menos **um outro colaborador** deve revisar o PR antes do merge.
- Se houver **Request Changes**, ajuste na mesma branch, faça commit e `git push`
  — o PR atualiza automaticamente.

### 5.4 Estratégia de merge

Usamos **Squash and Merge** para manter o histórico de `main`/`develop` limpo e
linear.

---

## 6. Cheat Sheet

```bash
# Sincronizar com o remoto
git checkout develop
git pull origin develop

# Criar e alternar para a branch de trabalho
git checkout -b 7-backend-validacao-de-conflito-de-horario

# Ver status das alterações
git status

# Adicionar arquivos para commit
git add .

# Commit no padrão semântico
git commit -m "feat(escala): valida conflito de horario do agente"

# Enviar para o GitHub
git push origin 7-backend-validacao-de-conflito-de-horario
```

Qualquer dúvida, perguntem no grupo da equipe. Bom trabalho!
