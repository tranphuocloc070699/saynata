"""Checks that doc links still point at real files and lines.

Feature docs link code as [file.swift:12-20](path#L12-L20). When code moves, these go stale
silently. This script finds links to missing files and line numbers past the end of a file.
It can't tell if a line still holds the right code, only that it exists.

    python3 scripts/check_docs.py      # exit 1 if anything is broken
"""

import re
import sys
import urllib.parse
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DOCS = [ROOT / "README.md", ROOT / "CLAUDE.md", *sorted((ROOT / "backlog" / "docs").rglob("*.md"))]
LINK = re.compile(r"\]\(([^)#\s]+)(?:#L(\d+)(?:-L(\d+))?)?\)")


def main() -> int:
    problems = []
    for doc in DOCS:
        for number, line in enumerate(doc.read_text().splitlines(), start=1):
            for target, start, end in LINK.findall(line):
                if target.startswith(("http://", "https://", "mailto:")):
                    continue
                path = (doc.parent / urllib.parse.unquote(target)).resolve()
                where = f"{doc.relative_to(ROOT)}:{number}"
                if not path.exists():
                    problems.append(f"{where}: missing file {target}")
                elif start and path.is_file():
                    last = int(end or start)
                    length = len(path.read_text().splitlines())
                    if last > length:
                        problems.append(f"{where}: {path.name} has {length} lines, link asks for line {last}")
    for problem in problems:
        print(problem)
    print(f"{len(DOCS)} docs checked, {len(problems)} problems")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
