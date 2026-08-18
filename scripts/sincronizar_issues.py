#!/usr/bin/env python3
"""
Sincroniza as issues do GitHub com o BACKLOG_ISSUES.md.

Diferença para o reparar_issues.py: este casa as issues pelo rodapé
`_Backlog #N_` do corpo, e não pelo título. Isso importa porque algumas
issues foram RENOMEADAS (a #24 deixou de ser "Cadastro de equipes de
rodízio" e virou "CRUD de tipos de turno") — casar por título criaria
duplicatas em vez de atualizar.

O que faz:
  1. Lê todas as issues do GitHub e monta o mapa backlog -> GitHub pelo rodapé
  2. Atualiza título, corpo, labels e milestone das que já existem
  3. Cria as que faltam
  4. Corrige as referências cruzadas com o mapa completo

Seguro rodar quantas vezes quiser: nada duplica, nada é apagado.

USO
    python3 sincronizar_issues.py --status    # testa a API
    python3 sincronizar_issues.py --dry-run   # mostra o que mudaria
    python3 sincronizar_issues.py             # aplica
    python3 sincronizar_issues.py --repetir 5 # insiste se o GitHub oscilar
"""

import argparse
import json
import re
import subprocess
import sys
import time
from pathlib import Path

REPO = "Joao-Bosco-Neto/Prototipo-de-Automaco-de-Escala"
ARQUIVO = Path(__file__).with_name("BACKLOG_ISSUES.md")

CABECALHO = re.compile(r"^### #(\d+) · (.+)$")
META = re.compile(r"^- \*\*(Milestone|Labels|Responsável|Depende de):\*\* (.*)$")
REF = re.compile(r"#(\d+)")
RODAPE = re.compile(r"_Backlog #(\d+)_")

MAX_TENTATIVAS = 5


def rodar(args, tentativas=MAX_TENTATIVAS, ignorar=()):
    espera = 5
    for tentativa in range(1, tentativas + 1):
        try:
            r = subprocess.run(args, capture_output=True, text=True)
        except FileNotFoundError:
            sys.exit("gh CLI não encontrado. Instale: https://cli.github.com")
        if r.returncode == 0:
            return r.stdout.strip()
        err = r.stderr.strip()
        if any(t in err for t in ignorar):
            return ""
        transitorio = any(c in err for c in
                          ("503", "502", "504", "Service Unavailable",
                           "o server is currently available"))
        if transitorio and tentativa < tentativas:
            print(f"      instabilidade do GitHub — nova tentativa em {espera}s")
            time.sleep(espera)
            espera *= 2
            continue
        print(f"      ERRO: {err[:180]}", file=sys.stderr)
        return None
    return None


def api_saudavel():
    try:
        r = subprocess.run(
            ["gh", "api", "graphql", "-f", "query={viewer{login}}", "--silent"],
            capture_output=True, text=True)
    except FileNotFoundError:
        sys.exit("gh CLI não encontrado. Instale: https://cli.github.com")
    return r.returncode == 0


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


def montar_corpo(issue, mapa=None):
    corpo = "\n".join(issue["corpo"]).strip()
    rodape = []
    if issue["depende"] and issue["depende"] not in ("—", "-"):
        dep = issue["depende"]
        if mapa:
            dep = REF.sub(lambda m: "#" + str(mapa.get(int(m.group(1)), m.group(1))), dep)
        rodape.append(f"**Depende de:** {dep}")
    rodape.append(f"_Backlog #{issue['numero']}_")
    return corpo + "\n\n---\n\n" + "\n\n".join(rodape)


def mapear_existentes(repo, issues):
    """Casa GitHub -> backlog pelo rodape; cai para o titulo se faltar rodape."""
    saida = rodar(["gh", "issue", "list", "--repo", repo, "--state", "all",
                   "--limit", "300", "--json", "number,title,body"])
    if saida is None:
        sys.exit("Não foi possível listar as issues.")
    remotas = json.loads(saida or "[]")
    por_titulo = {i["titulo"]: i["numero"] for i in issues}

    mapa = {}
    for r in remotas:
        m = RODAPE.search(r.get("body") or "")
        if m:
            mapa[int(m.group(1))] = r["number"]
        elif r["title"] in por_titulo:
            mapa[por_titulo[r["title"]]] = r["number"]
    return mapa


def executar(args):
    issues = parse_backlog(ARQUIVO.read_text(encoding="utf-8"))
    print(f"{len(issues)} issues no backlog\n")

    print("== Lendo o GitHub ==")
    mapa = mapear_existentes(args.repo, issues)
    faltantes = [i for i in issues if i["numero"] not in mapa]
    print(f"  já existem: {len(mapa)}\n  a criar:    {len(faltantes)}")
    if faltantes:
        print("  faltando:  " + ", ".join(f"#{i['numero']}" for i in faltantes))

    if args.dry_run:
        print("\n(dry-run: nada foi alterado)")
        return not faltantes

    if faltantes:
        print("\n== Criando as que faltam ==")
        for issue in faltantes:
            cmd = ["gh", "issue", "create", "--repo", args.repo,
                   "--title", issue["titulo"], "--body", montar_corpo(issue)]
            for lb in issue["labels"]:
                cmd += ["--label", lb]
            if issue["milestone"]:
                cmd += ["--milestone", issue["milestone"]]
            if issue["responsavel"]:
                cmd += ["--assignee", issue["responsavel"]]
            saida = rodar(cmd)
            if saida is None:
                print(f"  #{issue['numero']:>2} FALHOU")
            else:
                mapa[issue["numero"]] = int(saida.rstrip("/").split("/")[-1])
                print(f"  #{issue['numero']:>2} -> #{mapa[issue['numero']]}")
            time.sleep(args.delay)

    print("\n== Atualizando título, corpo, labels e milestone ==")
    for issue in issues:
        if issue["numero"] not in mapa:
            continue
        alvo = str(mapa[issue["numero"]])
        cmd = ["gh", "issue", "edit", alvo, "--repo", args.repo,
               "--title", issue["titulo"],
               "--body", montar_corpo(issue, mapa)]
        for lb in issue["labels"]:
            cmd += ["--add-label", lb]
        if issue["milestone"]:
            cmd += ["--milestone", issue["milestone"]]
        if issue["responsavel"]:
            cmd += ["--add-assignee", issue["responsavel"]]
        if rodar(cmd) is not None:
            print(f"  #{alvo} ok")
        time.sleep(args.delay)

    faltam = [i["numero"] for i in issues if i["numero"] not in mapa]
    print("\n" + "=" * 50)
    print(f"No GitHub: {len(mapa)}/{len(issues)}")
    if faltam:
        print(f"Ainda faltam: {faltam} — rode de novo, ele retoma sozinho.")
        return False
    print(f"Tudo sincronizado: https://github.com/{args.repo}/issues")
    return True


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--repo", default=REPO)
    ap.add_argument("--dry-run", action="store_true")
    ap.add_argument("--status", action="store_true")
    ap.add_argument("--delay", type=float, default=1.5)
    ap.add_argument("--repetir", type=int, default=1, metavar="N")
    ap.add_argument("--intervalo", type=int, default=300)
    args = ap.parse_args()

    if args.status:
        print("API saudável — pode sincronizar." if api_saudavel()
              else "API instável — aguarde.")
        return

    for volta in range(1, args.repetir + 1):
        if args.repetir > 1:
            print(f"\n{'=' * 50}\nTentativa {volta}/{args.repetir}\n{'=' * 50}")
        if args.repetir > 1 and not api_saudavel():
            print("API instável. Aguardando...")
        elif executar(args):
            return
        if volta < args.repetir:
            print(f"\nNova tentativa em {args.intervalo // 60} min. Ctrl+C encerra.")
            time.sleep(args.intervalo)


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\nInterrompido. Nada foi corrompido — pode rodar de novo.")
