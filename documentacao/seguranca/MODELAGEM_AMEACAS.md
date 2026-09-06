# Modelagem de Ameaças e Segurança da Aplicação

Este documento registra a modelagem de ameaças, as decisões arquiteturais de segurança e os riscos residuais aceitos para o **Sistema de Escala**, em conformidade com as diretrizes do **OWASP Top 10**.

---

## 1. Banco de Dados Local e Chaves Criptográficas (CWE-922, CWE-732)

### Ativo
O arquivo do banco de dados H2 (`sistema_escala.mv.db`) armazena credenciais (com hash BCrypt), perfis, funcionários, escalas de serviço, coberturas e registros de banco de horas. Uma cópia não autorizada do arquivo permitiria tentativas de extração de informações sensíveis ou adulteração do histórico operacional.

### Ameaças e Mitigações
| Ameaça | Referência | Tratamento |
|---|---|---|
| Leitura ou alteração direta do arquivo do banco | CWE-922 | H2 embarcado com `CIPHER=AES`, senhas criptográficas aleatórias geradas no primeiro uso |
| Chaves criptográficas expostas no código/repositório | CWE-798 | Chave AES gerada dinamicamente e salva exclusivamente em `%USERPROFILE%\.sistema-escala\banco.key` (fora do controle de versão) |
| Permissão excessiva no diretório de dados | CWE-732 | Criação de diretório com ACL restrita ao usuário no Windows (`FileSecurity`) e `700/600` em ambientes POSIX |
| Acesso físico/cópia por terceiro na máquina | Residual | Recomendar BitLocker (criptografia de volume) e bloqueio de estação ao cliente |

---

## 2. Integridade do Instalador e Distribuição de Software (OWASP A08 / CWE-494)

### Ativo
Os artefatos de entrega e instalação para ambiente Windows:
- Pacote instalador MSI: `Sistema de Escala-1.0.0.msi`
- Pacote portátil standalone: `Sistema de Escala/Sistema de Escala.exe` acompanhado do runtime JRE 21 embutido.

### Ameaça (Cenário #2 OWASP A08 / CWE-494)
Substituição ou adulteração do instalador `.msi` ou binário `.exe` por uma versão maliciosa durante o transporte e distribuição (por exemplo: ataque de Man-in-the-Middle ou substituição de arquivo em mídia removível/pen drive entre a equipe de desenvolvimento e o ambiente de produção na delegacia).

### Decisão Arquitetural e Limitação
A assinatura digital de código com certificado comercial (**Microsoft Authenticode Code Signing**) exige certificado corporativo emitido por Autoridade Certificadora pública (ex.: Sectigo, DigiCert), o qual possui custo financeiro recorrente e procedimentos de validação jurídica incompatíveis com o escopo de um projeto integrador acadêmico gratuito.

### Mitigação Adotada
1. **Publicação de Checksums Criptográficos SHA-256**:
   - Todo artefato gerado via Maven (`-Pinstalador-msi` ou `-Pempacotar-windows`) gera automaticamente seu respectivo arquivo de hash criptográfico (`.sha256`).
   - O hash SHA-256 é publicado e divulgado junto da entrega oficial em canal seguro e no repositório.
2. **Procedimento de Validação Obrigatória no Manual**:
   - O manual de instalação (`README.md`) instrui o gestor/administrador a validar o hash do arquivo baixado utilizando ferramentas nativas do Windows (`Get-FileHash` no PowerShell ou `CertUtil` no CMD) antes de realizar a execução.

### Risco Residual Aceito
A verificação de integridade via SHA-256 depende de ação manual do operador de TI/gestor da delegacia. Caso o operador execute o instalador sem conferir o checksum publicado, uma versão interceptada não seria barrada automaticamente pelo Windows SmartScreen. O risco é aceito devido à ausência de orçamento para certificado e mitigado pelo procedimento explícito de entrega assistida.

---

## 3. Ausência de Serialização Insegura (OWASP A08 / CWE-502)

### Ativo
Objetos de domínio em memória, fluxo de dados entre camadas e arquivos de persistência.

### Ameaça
Ataques de Desserialização Insegura (CWE-502), onde dados serializados adulterados podem instanciar classes arbitrárias na JVM através de *gadget chains*, resultando em Execução Remota de Código (RCE) ou corrupção de estado interno.

### Regra de Arquitetura e Decisão Mandatória
1. **Proibição Estrita de Serialização Java Nativa**:
   - Nenhuma classe de modelo, serviço, repositório, DAO ou controller implementa `java.io.Serializable`.
   - É proibido o uso de `ObjectInputStream`, `ObjectOutputStream`, `readObject()`, `writeObject()`, `readResolve()` ou `writeReplace()`.
2. **Persistência Relacional Pura**:
   - A camada de dados utiliza exclusivamente Java `record`s e classes imutáveis mapeadas campo a campo via JDBC puro com `PreparedStatement`.
3. **Garantia por Testes Automatizados**:
   - O teste automatizado de arquitetura `SegurancaIntegridadeESerializacaoTest` varre todos os pacotes da aplicação e falha a esteira de build caso qualquer classe de modelo ou serviço declare `Serializable` ou métodos de serialização Java.

---

## 4. Recomendações Operacionais ao Cliente

Para a operação segura em produção na delegacia, recomenda-se:
1. **Validação Pré-Instalação**: Executar `Get-FileHash -Algorithm SHA256` no instalador `.msi` e comparar com o valor fornecido pela equipe.
2. **Proteção do Endpoint**: Manter a estação do gestor com Windows 10/11 atualizado, proteção de tela com bloqueio por senha e criptografia de disco **BitLocker** ativada.
3. **Controle de Contas**: Não compartilhar a conta de usuário do Windows com terceiros, preservando a confidencialidade do arquivo `%USERPROFILE%\.sistema-escala\banco.key`.