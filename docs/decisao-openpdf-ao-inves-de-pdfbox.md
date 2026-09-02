# Decisão: Por que usamos OpenPDF ao invés de Apache PDFBox (Issue #50)

| Campo | Valor |
|-------|-------|
| **Status** | Aceita |
| **Data** | 01/09/2026 |
| **Issue de origem** | [#50 — \[PDF\] Integrar PDFBox e gerar o documento base](https://github.com/Joao-Bosco-Neto/Prototipo-de-Automaco-de-Escala/issues/50) |
| **Backlog interno** | `scripts/BACKLOG_ISSUES.md` — Issue #41 |
| **Responsável** | Davi (@DaviRSuassuna) |

---

## Contexto

A Issue #50 do GitHub foi criada com o seguinte escopo original:

> Adicionar o Apache PDFBox 3.x e montar o serviço de geração: página A4 retrato, margens,
> fontes e utilitários de tabela e quebra de página. PDFBox não tem componente de tabela
> pronto — o utilitário de desenho de linhas e células é o trabalho principal aqui, e todo
> o resto do PDF depende dele.

O contrato técnico proposto era:

```java
public interface GeradorPdfService {
    Path gerarEscalaMensal(YearMonth mes, OpcoesPdf opcoes, Path destino);
}
```

Critérios de aceite originais:
- PDFBox no `pom.xml`
- Serviço gera PDF A4 com cabeçalho e rodapé
- Utilitário de tabela com quebra automática de página
- Acentuação correta (fonte com suporte a UTF-8)

---

## Problema

Entre a redação da issue e o início da implementação, a arquitetura do projeto evoluiu.
O **OpenPDF** (`com.github.librepdf:openpdf-core-modern:2.4.0`) foi adotado como biblioteca
padrão de geração de PDF, e a classe `ExportacaoRelatorioService` já estava em produção
gerando o relatório de Banco de Horas com ela.

Implementar a Issue #50 literalmente traria os seguintes problemas:

### 1. Redundância de bibliotecas

| Aspecto | Apache PDFBox 3.x | OpenPDF 2.4.0 (já no projeto) |
|---------|-------------------|-------------------------------|
| Licença | Apache 2.0 | LGPL/MPL |
| Tabela nativa | ❌ Não tem — exige utilitário manual | ✅ `PdfPTable` + `PdfPCell` nativos |
| Dependências transitivas | fontbox, pdfbox-io, commons-logging | Nenhuma (todas `optional` na 2.x) |
| Binários nativos | Não na 3.x base | Não |
| Já no `pom.xml` | ❌ | ✅ |

Adicionar o PDFBox significaria **duas bibliotecas de PDF no classpath** resolvendo o mesmo
problema, com licenciamentos diferentes e sem ganho funcional.

### 2. Esforço desnecessário

O PDFBox não possui componente de tabela. A Issue #50 reconhecia isso explicitamente:

> *"o utilitário de desenho de linhas e células é o trabalho principal"*

Isso significaria implementar manualmente:
- Cálculo de largura de colunas e altura de linhas
- Geometria de posicionamento de texto dentro de células
- Detecção de fim de página e quebra automática com repetição de cabeçalho
- Renderização de bordas e preenchimento de fundo

O OpenPDF resolve **todos** esses pontos nativamente com `PdfPTable.setHeaderRows(1)`,
`PdfPCell.setPadding()`, `setWidths()`, etc.

### 3. Quebra do padrão arquitetural

O `ExportacaoRelatorioService` já estabeleceu os padrões visuais do projeto:
- Fontes: `FontFactory.HELVETICA` / `HELVETICA_BOLD` (Type 1, built-in)
- Margens: `36, 36, 44, 36` pontos
- Estilo de cabeçalho de tabela: `grayFill(0.9f)`, `padding(5f)`, `ALIGN_CENTER`
- Tratamento de erro: `DocumentException` → `IOException`

Usar uma biblioteca diferente para o PDF da escala quebraria essa padronização e exigiria
que a equipe mantivesse dois estilos de código distintos para a mesma finalidade.

---

## Decisão

**Descartar o Apache PDFBox 3.x e implementar o serviço de geração de PDF da escala mensal
usando o OpenPDF (`openpdf-core-modern`), que já é a biblioteca padrão do projeto.**

### Adaptações ao contrato original

| Issue #50 (original) | Implementação (adaptada) | Justificativa |
|----------------------|--------------------------|---------------|
| `interface GeradorPdfService` | `class GeradorPdfService` (concreta) | `ExportacaoRelatorioService` é classe concreta, sem interface. Manter o padrão. |
| `OpcoesPdf opcoes` | `File destino` | `OpcoesPdf` não existe no projeto. O padrão consolidado é receber `File destino` direto do `FileChooser`. |
| `Path destino` (retorno) | `void` (sem retorno) | `ExportacaoRelatorioService.exportarPdf()` retorna `void`. O chamador já tem o `File`. |
| PDFBox no `pom.xml` | Nenhuma alteração no `pom.xml` | OpenPDF já está declarado. |
| Utilitário manual de tabela | `PdfPTable` nativo do OpenPDF | Desnecessário reinventar. |
| Fonte `.ttf` para UTF-8 | Helvetica built-in (ISO-8859-1) | Cobre toda a acentuação pt-BR. Mesmo mecanismo do `ExportacaoRelatorioService`. |

### Critérios de aceite da Issue #50 — como foram atendidos

| Critério original | Como foi atendido |
|-------------------|-------------------|
| ~~PDFBox no `pom.xml`~~ | OpenPDF já estava no `pom.xml`. Nenhuma dependência nova necessária. |
| Serviço gera PDF A4 com cabeçalho e rodapé | ✅ `GeradorPdfService.exportarPdf()` gera A4 retrato com cabeçalho "Escala de Serviço – {Mês/Ano}" e rodapé textual. |
| Utilitário de tabela com quebra automática de página | ✅ `PdfPTable` com `setHeaderRows(1)` — o OpenPDF repete o cabeçalho da tabela automaticamente em cada página. |
| Acentuação correta | ✅ Fontes Helvetica built-in (ISO-8859-1) cobrem a acentuação portuguesa. Validado nos testes. |

---

## Artefatos produzidos

| Arquivo | Descrição |
|---------|-----------|
| `src/main/java/br/edu/sistemaescala/backend/service/GeradorPdfService.java` | Serviço de geração do PDF da escala mensal |
| `src/test/java/br/edu/sistemaescala/backend/service/GeradorPdfServiceTest.java` | 4 testes unitários (PDF com turnos, PDF vazio, nome sugerido, título) |

Ambos seguem rigorosamente os padrões estabelecidos pelo `ExportacaoRelatorioService` e
seu teste `ExportacaoRelatorioServiceTest`.

---

## Consequências

### Positivas
- **Zero dependências novas** — o `pom.xml` não foi alterado.
- **Consistência visual** — os dois PDFs do sistema (Banco de Horas e Escala Mensal) usam
  a mesma biblioteca, as mesmas fontes, as mesmas margens e o mesmo estilo de tabela.
- **Manutenibilidade** — a equipe precisa conhecer apenas uma API de PDF.
- **Tempo economizado** — não foi necessário implementar um utilitário manual de tabela,
  paginação e geometria de células.

### Riscos aceitos
- **ISO-8859-1 vs. Unicode completo** — as fontes Helvetica built-in não suportam
  caracteres fora do ISO-8859-1 (ex.: emoji, caracteres CJK). Para nomes em português
  brasileiro isso é irrelevante. Se no futuro houver necessidade de Unicode completo,
  será necessário embutir uma fonte `.ttf` (ex.: Roboto) e usar `BaseFont.IDENTITY_H`.
  Isso pode ser feito sem alterar a API pública do serviço.
- **Issue #50 diverge do código** — alguém lendo a Issue #50 no GitHub esperará ver
  PDFBox. Esta documentação registra a divergência. Recomenda-se adicionar um comentário
  na Issue #50 referenciando este documento.

---

## Adendo (01/09/2026) — PDFBox volta, só para *renderizar*

A tela de exportação (Issue #43/#52) precisa de uma **pré-visualização ao vivo**
do PDF. O OpenPDF só escreve PDF; não tem parser nem engine de rasterização, e
nenhuma versão dele expõe algo equivalente ao `PDFRenderer`. Rasterizar PDF →
imagem em Java exige uma engine de renderização.

**Decisão:** adicionar `org.apache.pdfbox:pdfbox:3.0.3` ao `pom.xml`, usado
**exclusivamente** para ler o PDF que o OpenPDF gerou e rasterizar a primeira
página (`br.edu.sistemaescala.frontend.PdfPreviewRenderer`). A geração de PDF
continua 100% no OpenPDF — a decisão acima permanece válida para tudo o que
*grava* PDF.

Por que não quebra o empacotamento offline: a linha 3.x base do PDFBox não traz
binário nativo. As dependências transitivas (`fontbox`, `pdfbox-io`,
`commons-logging`) são Java puro. O módulo opcional que arrasta brotli/JAI não
entra.

---

## Referências

- `pom.xml` L50-67 — declaração do OpenPDF com justificativa de licença
- `ExportacaoRelatorioService.java` — serviço de referência (padrão visual e arquitetural)
- `scripts/BACKLOG_ISSUES.md` L1046-L1104 — Issues #41 e #42 do backlog interno
- [OpenPDF no GitHub](https://github.com/LibrePDF/OpenPDF) — documentação da biblioteca

