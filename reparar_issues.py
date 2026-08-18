#!/usr/bin/env python3
"""
Repara uma execução parcial do criar_issues.py.

O que faz:
  1. Lê as issues que JÁ existem no GitHub e casa cada uma com o backlog pelo título
  2. Cria apenas as que faltam, com nova tentativa automática em caso de erro 5xx
  3. Reaplica labels e milestone em tudo (caso alguma tenha sido criada sem)
  4. Reescreve TODOS os corpos com o mapa completo, corrigindo as referências cruzadas

É seguro rodar várias vezes: nada é duplicado nem apagado.

USO
    python3 reparar_issues.py --status     # só verifica se a API do GitHub respondeu
    python3 reparar_issues.py --dry-run    # mostra o plano sem alterar nada
    python3 reparar_issues.py              # executa o reparo
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

MAX_TENTATIVAS = 5


def rodar(args, tentativas=MAX_TENTATIVAS, ignorar=()):
    """Executa comando gh com nova tentativa e espera crescente em erro 5xx."""
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
                           "no server is currently available",
                           "No server is currently available"))
        if transitorio and tentativa < tentativas:
            print(f"      instabilidade do GitHub — nova tentativa em {espera}s "
                  f"({tentativa}/{tentativas - 1})")
            time.sleep(espera)
            espera *= 2
            continue
        print(f"      ERRO: {err[:180]}", file=sys.stderr)
        return None
    return None


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


def listar_existentes(repo):
    saida = rodar(["gh", "issue", "list", "--repo", repo, "--state", "all",
                   "--limit", "300", "--json", "number,title"])
    if saida is None:
        sys.exit("Não foi possível listar as issues. O GitHub pode ainda estar instável.")
    return {i["title"]: i["number"] for i in json.loads(saida or "[]")}


def api_saudavel():
    """Testa o GraphQL, que e o caminho usado para criar issues.
    O endpoint rate_limit e REST e continua respondendo mesmo com escrita fora do ar."""
    try:
        r = subprocess.run(
            ["gh", "api", "graphql", "-f", "query={viewer{login}}", "--silent"],
            capture_output=True, text=True)
    except FileNotFoundError:
        sys.exit("gh CLI não encontrado. Instale: https://cli.github.com")
    return r.returncode == 0


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--repo", default=REPO)
    ap.add_argument("--dry-run", action="store_true")
    ap.add_argument("--status", action="store_true",
                    help="apenas testa se a API do GitHub está respondendo")
    ap.add_argument("--delay", type=float, default=1.5)
    ap.add_argument("--repetir", type=int, default=1, metavar="N",
                    help="tenta ate N vezes, esperando entre as tentativas. "
                         "Use --repetir 20 para deixar rodando durante a instabilidade")
    ap.add_argument("--intervalo", type=int, default=300,
                    help="segundos de espera entre repeticoes (padrao: 5 min)")
    args = ap.parse_args()

    if args.status:
        print("Testando a API do GitHub (caminho de escrita)...")
        print("  API saudável — pode rodar o reparo." if api_saudavel()
              else "  API instável — aguarde. Não adianta insistir agora.")
        return

    if args.repetir > 1:
        laco_repeticao(args)
    else:
        executar(args)


def laco_repeticao(args):
    """Executa o reparo repetidamente ate completar ou esgotar as tentativas.
    Como o script reconcilia pelo titulo, repetir e sempre seguro."""
    for volta in range(1, args.repetir + 1):
        print(f"\n{'=' * 50}\nTentativa {volta}/{args.repetir}\n{'=' * 50}")
        if not api_saudavel():
            print("API instável. Aguardando sem tentar escrever...")
        else:
            try:
                if executar(args):
                    print("\nBacklog completo. Encerrando.")
                    return
            except KeyboardInterrupt:
                print("\nInterrompido. Nada foi corrompido — pode rodar de novo depois.")
                return
        if volta < args.repetir:
            print(f"\nNova tentativa em {args.intervalo // 60} min. "
                  f"Ctrl+C encerra com segurança.")
            try:
                time.sleep(args.intervalo)
            except KeyboardInterrupt:
                print("\nEncerrado pelo usuário.")
                return
    print("\nTentativas esgotadas. Rode de novo quando o GitHub normalizar.")


def executar(args):
    """Faz uma passada de reparo. Devolve True se o backlog ficou completo."""
    issues = parse_backlog(ARQUIVO.read_text(encoding="utf-8"))
    print(f"{len(issues)} issues no backlog\n")

    print("== Lendo o que já existe no GitHub ==")
    existentes = listar_existentes(args.repo)
    print(f"  {len(existentes)} issues encontradas\n")

    mapa, faltantes = {}, []
    for issue in issues:
        if issue["titulo"] in existentes:
            mapa[issue["numero"]] = existentes[issue["titulo"]]
        else:
            faltantes.append(issue)

    print(f"== Plano ==\n  já existem: {len(mapa)}\n  a criar:    {len(faltantes)}")
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
                print(f"  #{issue['numero']:>2} FALHOU — rode o script de novo depois")
            else:
                real = int(saida.rstrip("/").split("/")[-1])
                mapa[issue["numero"]] = real
                print(f"  #{issue['numero']:>2} -> #{real}")
            time.sleep(args.delay)

    print("\n== Corrigindo corpos, labels e milestones ==")
    corrigidas = 0
    for issue in issues:
        if issue["numero"] not in mapa:
            continue
        alvo = str(mapa[issue["numero"]])
        cmd = ["gh", "issue", "edit", alvo, "--repo", args.repo,
               "--body", montar_corpo(issue, mapa)]
        for lb in issue["labels"]:
            cmd += ["--add-label", lb]
        if issue["milestone"]:
            cmd += ["--milestone", issue["milestone"]]
        if issue["responsavel"]:
            cmd += ["--add-assignee", issue["responsavel"]]
        if rodar(cmd) is not None:
            corrigidas += 1
            print(f"  #{alvo} ok")
        time.sleep(args.delay)

    print("\n" + "=" * 50)
    print(f"No GitHub: {len(mapa)}/{len(issues)}   Corpos corrigidos: {corrigidas}")
    faltam = [i["numero"] for i in issues if i["numero"] not in mapa]
    if faltam:
        print(f"Ainda faltam: {faltam}\nRode o script de novo — ele retoma sozinho.")
        return False
    print(f"Backlog completo: https://github.com/{args.repo}/issues")
    return True


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\nInterrompido. Nada foi corrompido — o script reconcilia pelo título, "
              "então pode rodar de novo a qualquer momento.")
