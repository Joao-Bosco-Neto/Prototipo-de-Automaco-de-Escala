## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

Rules:
- For codebase questions, first run `graphify query "<question>"` when graphify-out/graph.json exists. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).

## Protótipo de referência das telas

`prototipo/index.html` é o protótipo visual do sistema (HTML standalone). Ao trabalhar
em qualquer issue de UI, consulte este arquivo antes de implementar o layout — ele é a
referência visual oficial, citada nas issues como "conforme a tela X".

## Tema CSS — regra obrigatória

Antes de criar qualquer tela nova, leia `src/main/resources/frontend/css/app.css`.
Use as classes de lá (`button-primario`, `card`, `selo-*`, etc.) via
`getStyleClass().add(...)`. NUNCA use `setStyle()` com cor em hex — é
exatamente o problema que este arquivo existe para evitar.

Referência visual de todos os componentes:
`br.edu.sistemaescala.frontend.VitrineComponentesApp` (roda com
`mvn exec:java -Dexec.mainClass=...`).
