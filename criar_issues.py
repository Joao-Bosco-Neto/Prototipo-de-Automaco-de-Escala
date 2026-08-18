#!/usr/bin/env python3
"""
Cria labels, milestones e issues no GitHub a partir do BACKLOG_ISSUES.md.

PRÉ-REQUISITOS
--------------
1. GitHub CLI instalado:  https://cli.github.com
   Windows:  winget install --id GitHub.cli
   Linux:    sudo apt install gh
2. Autenticado:  gh auth login
3. Samuel e Davi já adicionados como colaboradores do repositório
   (Settings > Collaborators). Sem isso, a atribuição falha.

USO
---
    python criar_issues.py --check       # só verifica o ambiente
    python criar_issues.py --dry-run     # simula, não cria nada
    python criar_issues.py               # cria de verdade
    python criar_issues.py --start-at 20 # retoma a partir da issue 20

O script roda em duas passadas:
  1a  cria todas as issues e anota o número real que o GitHub deu a cada uma
  2a  corrige as referências "Depende de #N" para os números reais
Assim, o backlog não depende de o GitHub numerar exatamente como o arquivo.
"""

import argparse
import re
import subprocess
import sys
import time
from pathlib import Path

REPO = "Joao-Bosco-Neto/Prototipo-de-Automaco-de-Escala"
ARQUIVO = Path(__file__).with_name("BACKLOG_ISSUES.md")

LABELS = {
    "decisao": ("d93f0b", "Decisão de projeto que bloqueia o desenvolvimento"),
    "setup": ("0e8a16", "Configuração de ambiente e ferramentas"),
    "banco": ("1d76db", "Banco de dados e migrações"),
    "backend": ("0052cc", "Lógica de aplicação e acesso a dados"),
    "ui": ("5319e7", "Interface JavaFX"),
    "regra-de-negocio": ("b60205", "Regras e validações do domínio"),
    "escala": ("fbca04", "Montagem e geração de escala"),
    "cobertura": ("f9d0c4", "Substituições de plantão"),
    "banco-de-horas": ("c2e0c6", "Saldo e lançamentos de horas"),
    "pdf": ("bfd4f2", "Geração e exportação de documentos"),
    "dashboard": ("d4c5f9", "Visão geral e indicadores"),
    "auth": ("e99695", "Autenticação e controle de acesso"),
    "testes": ("0e8a16", "Testes automatizados"),
    "docs": ("cccccc", "Documentação"),
    "empacotamento": ("006b75", "Build e instalador"),
    "futuro": ("ededed", "Fora do escopo do protótipo"),
    "bloqueante": ("b60205", "Trava outras tarefas - prioridade maxima"),
    "seguranca": ("d93f0b", "Requisito de seguranca (OWASP Top 10)"),
}

MILESTONES = [
    "M0 — Fundação",
    "M1 — Dados e autenticação",
    "M2 — Shell e usuários",
    "M3 — Funcionários e equipes",
    "M4 — Escala",
    "M5 — Coberturas e banco de horas",
    "M6 — PDF e dashboard",
    "M7 — Qualidade e entrega",
    "Futuro",
]

CABECALHO = re.compile(r"^### #(\d+) · (.+)$")
META = re.compile(r"^- \*\*(Milestone|Labels|Responsável|Depende de):\*\* (.*)$")
REF = re.compile(r"#(\d+)")


def rodar(args, dry_run=False, ignorar=()):
    """Executa um comando. Devolve stdout, ou None se falhou."""
    if dry_run:
        return ""
    r = subprocess.run(args, capture_output=True, text=True)
    if r.returncode != 0:
        err = r.stderr.strip()
        if any(t in err for t in ignorar):
            return ""
        print(f"      ERRO: {err[:200]}", file=sys.stderr)
        return None
    return r.stdout.strip()


def verificar_ambiente(repo):
    """Checagens antes de criar qualquer coisa."""
    if subprocess.run(["gh", "--version"], capture_output=True).returncode != 0:
        print("  [x] gh CLI nao encontrado. Instale: https://cli.github.com")
        return False
    print("  [ok] gh CLI instalado")

    if subprocess.run(["gh", "auth", "status"], capture_output=True).returncode != 0:
        print("  [x] Nao autenticado. Rode: gh auth login")
        return False
    print("  [ok] autenticado")

    r = subprocess.run(["gh", "repo", "view", repo, "--json", "name"],
                       capture_output=True, text=True)
    if r.returncode != 0:
        print(f"  [x] Sem acesso ao repositorio {repo}")
        return False
    print(f"  [ok] acesso a {repo}")

    # Issues e PRs dividem a MESMA numeracao no GitHub.
    r = subprocess.run(
        ["gh", "issue", "list", "--repo", repo, "--state", "all",
         "--limit", "1", "--json", "number"],
        capture_output=True, text=True)
    if r.returncode == 0 and r.stdout.strip() not in ("", "[]"):
        print("  [!] O repositorio JA TEM issues ou PRs.")
        print("      Os numeros do GitHub nao vao bater com os do backlog.")
        print("      Sem problema: a 2a passada corrige as referencias.")
    else:
        print("  [ok] repositorio sem issues/PRs anteriores")

    return True


def parse_backlog(texto):
    issues, atual = [], None
    for linha in texto.splitlines():
        cab = CABECALHO.match(linha)
        if cab:
            if atual:
                issues.append(atual)
            atual = {"numero": int(cab.group(1)), "titulo": cab.group(2).strip(),
                     "milestone": "", "labels": [], "responsavel": "",
                     "depende": "", "corpo": []}
            continue
        if atual is None:
            continue
        m = META.match(linha)
        if m:
            chave, valor = m.group(1), m.group(2).strip()
            if chave == "Milestone":
                atual["milestone"] = valor
            elif chave == "Labels":
                atual["labels"] = [x.strip() for x in valor.split(",") if x.strip()]
            elif chave == "Responsável":
                atual["responsavel"] = "" if valor in ("—", "-") else valor
            else:
                atual["depende"] = valor
            continue
        if linha.strip() == "---":
            continue
        atual["corpo"].append(linha)
    if atual:
        issues.append(atual)
    return issues


def montar_corpo(issue):
    corpo = "\n".join(issue["corpo"]).strip()
    rodape = []
    if issue["depende"] and issue["depende"] not in ("—", "-"):
        rodape.append(f"**Depende de:** {issue['depende']}")
    rodape.append(f"_Backlog #{issue['numero']}_")
    return corpo + "\n\n---\n\n" + "\n\n".join(rodape)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--dry-run", action="store_true")
    ap.add_argument("--check", action="store_true", help="so verifica o ambiente")
    ap.add_argument("--repo", default=REPO)
    ap.add_argument("--delay", type=float, default=1.0,
                    help="pausa entre criacoes, em segundos")
    ap.add_argument("--start-at", type=int, default=1,
                    help="retoma a partir desta issue do backlog")
    args = ap.parse_args()

    print("== Verificando ambiente ==")
    if not args.dry_run:
        if not verificar_ambiente(args.repo):
            sys.exit("\nCorrija os itens acima e rode de novo.")
    else:
        print("  (dry-run: verificacao pulada)")
    if args.check:
        return

    if not ARQUIVO.exists():
        sys.exit(f"Arquivo nao encontrado: {ARQUIVO}")
    issues = parse_backlog(ARQUIVO.read_text(encoding="utf-8"))
    print(f"\n{len(issues)} issues lidas de {ARQUIVO.name}")

    print("\n== Labels ==")
    for nome, (cor, desc) in LABELS.items():
        rodar(["gh", "label", "create", nome, "--repo", args.repo,
               "--color", cor, "--description", desc, "--force"], args.dry_run)
    print(f"  {len(LABELS)} labels processadas")

    print("\n== Milestones ==")
    for titulo in MILESTONES:
        rodar(["gh", "api", f"repos/{args.repo}/milestones",
               "-f", f"title={titulo}", "--silent"],
              args.dry_run, ignorar=("already_exists", "already exists"))
    print(f"  {len(MILESTONES)} milestones processadas")

    print("\n== Criando issues (1a passada) ==")
    mapa, falhas = {}, []
    for issue in issues:
        if issue["numero"] < args.start_at:
            continue

        cmd = ["gh", "issue", "create", "--repo", args.repo,
               "--title", issue["titulo"], "--body", montar_corpo(issue)]
        for lb in issue["labels"]:
            cmd += ["--label", lb]
        if issue["milestone"]:
            cmd += ["--milestone", issue["milestone"]]
        if issue["responsavel"]:
            cmd += ["--assignee", issue["responsavel"]]

        saida = rodar(cmd, args.dry_run)
        if saida is None:
            falhas.append(issue["numero"])
            print(f"  #{issue['numero']:>2} FALHOU  {issue['titulo'][:55]}")
        else:
            real = saida.rstrip("/").split("/")[-1] if saida else str(issue["numero"])
            mapa[issue["numero"]] = real
            print(f"  #{issue['numero']:>2} -> #{real:<4} {issue['titulo'][:55]}")

        if not args.dry_run:
            time.sleep(args.delay)

    desalinhado = any(str(k) != v for k, v in mapa.items())
    if desalinhado and not args.dry_run:
        print("\n== Corrigindo referencias cruzadas (2a passada) ==")
        for issue in issues:
            if issue["numero"] not in mapa:
                continue
            if not issue["depende"] or issue["depende"] in ("—", "-"):
                continue
            corpo = montar_corpo(issue)
            novo = REF.sub(
                lambda m: "#" + str(mapa.get(int(m.group(1)), m.group(1))), corpo)
            if novo != corpo:
                rodar(["gh", "issue", "edit", mapa[issue["numero"]],
                       "--repo", args.repo, "--body", novo], False)
                print(f"  #{mapa[issue['numero']]} atualizada")
                time.sleep(args.delay)
    elif desalinhado:
        print("\n  (dry-run: 2a passada seria executada)")
    else:
        print("\n  Numeracao bateu com o backlog - 2a passada dispensada.")

    print("\n" + "=" * 50)
    print(f"Criadas: {len(mapa)}   Falhas: {len(falhas)}")
    if falhas:
        print(f"Numeros que falharam: {falhas}")
        print(f"Para retomar:  python criar_issues.py --start-at {min(falhas)}")
    elif not args.dry_run:
        print(f"Veja em: https://github.com/{args.repo}/issues")


if __name__ == "__main__":
    main()
