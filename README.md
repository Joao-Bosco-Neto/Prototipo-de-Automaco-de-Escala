# Prototipo-de-Automacao-de-Escala

## Qual é o problema que o sistema resolve?

Desorganização na criação de escalas dos membros da delegacia. Em vista que, o escrivão muitas vezes têm problemas em gerir o tempo de cada funcionário.

## Quem utilizará o sistema?

O sistema possui dois perfis de usuário, sendo o administrador e o escrivão. O escrivão é o operador principal, responsável por cadastrar funcionários e gerenciar a escala mensal. O administrador possui as mesmas funcionalidades, com controle adicional sobre o gerenciamento de usuários do sistema. Os demais funcionários da delegacia não têm acesso ao sistema, eles recebem a escala já finalizada em formato PDF.

## Quais funcionalidades serão obrigatórias?

- Login/autenticação do escrivão;
- Cadastrar funcionários;
- Gerar escala mensal;
- Selecionar funcionários para a escala;
- Validar conflitos de horário, impedindo que um mesmo funcionário seja escalado duas vezes no mesmo período;
- Editar a escala mensal;
- Salvar escala no programa;
- Exportar escala em PDF;

## Quais funcionalidades seriam diferenciais (opcional)?

- Salvar banco de horas de cada funcionário;

## Quais tecnologias serão utilizadas?

- Java
- JavaFX
- PostgreSQL

## Requisitos Funcionais (RF)

**RF01** — O sistema deve permitir login com autenticação (usuário e senha), com diferenciação entre os perfis de administrador e escrivão.

**RF02** — O sistema deve permitir cadastrar, editar e remover funcionários.

**RF03** — O sistema deve permitir gerar uma escala mensal.

**RF04** — O sistema deve permitir selecionar funcionários para compor a escala.

**RF05** — O sistema deve validar conflitos de horário, impedindo que um mesmo funcionário seja escalado duas vezes no mesmo período.

**RF06** — O sistema deve permitir editar a escala mensal já criada.

**RF07** — O sistema deve permitir salvar a escala no banco de dados.

**RF08** — O sistema deve permitir exportar a escala em PDF.

**RF09** — O sistema deve permitir registrar e consultar banco de horas por funcionário.

**RF10** — O sistema deve exigir um mínimo de 2 agentes por plantão, impedindo a criação ou o salvamento de um plantão com menos funcionários que o mínimo definido.

**RF11** — O sistema deve validar o intervalo de descanso entre plantões, impedindo que um agente seja escalado novamente antes de completar 72 horas de descanso após um plantão de 24 horas (regime 24x72).

**RF12** — O sistema deve permitir que o administrador cadastre, edite e remova outros usuários do sistema.

## Requisitos Não Funcionais (RNF)

**RNF01** — O sistema deve ser desenvolvido em Java com interface gráfica em JavaFX.

**RNF02** — O sistema deve utilizar PostgreSQL como banco de dados.

**RNF03** — Segurança: a senha do escrivão deve ser armazenada com hash.

**RNF04** — O sistema deve rodar localmente no computador da delegacia.

**RNF05** — Usabilidade: a interface deve ser simples o bastante para uso por um único operador sem treinamento técnico extenso.

**RNF06** — Confiabilidade: o sistema não deve permitir salvar uma escala com conflito de horário não resolvido.

**RNF07** — O PDF exportado deve ser gerado em formato legível e pronto para impressão.

**RNF08** — Confiabilidade: o sistema não deve permitir salvar uma escala que viole o mínimo de agentes por plantão ou o intervalo de descanso de 72 horas do regime 24x72.

## Perguntas para o cliente

### Vocês possuem um servidor de domínio? Como funciona a questão das contas de cada desktop da delegacia?

**R:** “Sim, porém não temos controle sobre ele, os administradores do servidor ficam na Secretária de Segurança Pública. Sobre as contas, todos os computadores possuem uma conta de administrador e uma conta de usuário, temos acesso apenas a conta de usuário, mas sem restrições”.

### Cada funcionário no plantão tem uma função específica?

**R:** “Não, todos têm a mesma função de agente”.

### Como funcionaria a questão da permissão de baixar o app no desktop da delegacia? A TI deixaria?

**R:** “Sim, deixaria, não existe restrição do que podemos ou não podemos instalar nos computadores”.

### Um plantão precisa de quantos funcionários?

**R:** “Apenas dois agentes”.

### Qual é o regime do plantão?

**R:** “24 por 72 horas”.

<img width="856" height="668" alt="image" src="https://github.com/user-attachments/assets/4e1acbb7-1a88-4ab7-a1db-68d8ecc479b1" />
