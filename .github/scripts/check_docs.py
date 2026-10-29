"""Vérifie que les liens relatifs des fichiers Markdown pointent vers des fichiers existants.

Les liens externes (http, mailto) et les ancres seules sont ignorés.
Code de sortie non nul si un lien est cassé.
"""
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
LINK = re.compile(r"\[[^\]]*\]\(([^)\s]+)\)")
broken = []

for md in [*ROOT.glob("*.md"), *ROOT.glob("docs/**/*.md"), *ROOT.glob(".github/*.md")]:
    text = md.read_text(encoding="utf-8")
    # Les blocs de code ne contiennent pas de liens à vérifier.
    text = re.sub(r"```.*?```", "", text, flags=re.S)
    for target in LINK.findall(text):
        if target.startswith(("http://", "https://", "mailto:", "#")):
            continue
        path = target.split("#", 1)[0]
        if not path:
            continue
        if not (md.parent / path).resolve().exists():
            broken.append(f"{md.relative_to(ROOT)} → {target}")

if broken:
    print("Liens cassés :")
    print("\n".join(f"  - {item}" for item in broken))
    sys.exit(1)
print("Documentation : tous les liens relatifs sont valides.")
