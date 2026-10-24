"""Checks that every Java file the documentation names actually exists.

The same failure this reorganisation kept producing, one level up: a document that points at
`menu/ShopLayout.java` after the file moved to `core/menu/` reads perfectly and sends the reader
nowhere. It is the tool-side problem — a checker reading a path that no longer holds anything — with
a human on the receiving end.

The rule is deliberately narrow: any token ending in `.java` must correspond to a file somewhere in
the repository. Prose, resource paths and shell commands are left alone, because a checker that cries
wolf gets switched off.
"""
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
SOURCES = sorted(p.name for p in ROOT.glob("src/main/java/**/*.java"))
TOKEN = re.compile(r"[\w./-]+\.java\b")

# Names that are illustrations rather than references: a recipe's placeholder for whatever the
# reader is writing, and files that belong to other mods or to the JDK.
EXEMPT = {"XScreen.java", "XService.java", "XSavedData.java", "XResult.java", "XServices.java",
          "XActionPacket.java", "XDataPacket.java"}

missing = []
# Markdown and plain text alike: the owner's copy of the design notes is a txt, and it names source
# files too, so a file it points at that has moved is exactly the failure this checks for.
documents = sorted(list(ROOT.glob("*.md")) + list(ROOT.glob("*.txt")))
for document in documents:
    for number, line in enumerate(document.read_text(encoding="utf-8").splitlines(), 1):
        for token in TOKEN.findall(line):
            name = token.rsplit("/", 1)[-1]
            if name in EXEMPT:
                continue
            if name not in SOURCES:
                missing.append(f"{document.name}:{number}: {token}")

print(f"  {len(documents)} documents, {len(SOURCES)} source files")
for problem in missing:
    print(f"  MISSING: {problem}")
if missing:
    print(f"DOC PATHS: {len(missing)} reference(s) to files that are not there")
    sys.exit(1)
print("DOC PATHS: clean")
