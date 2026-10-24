"""Checks that every package explains itself, in both languages.

Java's only place for a package comment is package-info.java, so this checks three things about each
package the mod ships:

* that the file exists at all — a package with no comment is a directory a newcomer has to guess at;
* that it carries both scripts, because the comment is written in English and Chinese in one file,
  and a file that lost one of them looks finished and answers half the readers;
* and that it is not empty, which is the failure mode every tool in this project has had.

A package is any directory under the mod that holds Java source, plus the mod's own root package. It
does not check that the Chinese is *right* — no tool can — only that it is there.
"""
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
BASE = ROOT / "src/main/java/com/example/globalshop"
CJK = re.compile(r"[\u4e00-\u9fff]")
LATIN = re.compile(r"[A-Za-z]{3}")

packages = []
if any(BASE.glob("*.java")):
    packages.append(BASE)
for directory in BASE.rglob("*"):
    if directory.is_dir() and any(directory.glob("*.java")):
        packages.append(directory)

problems = []
for directory in sorted(packages):
    relative = directory.relative_to(ROOT / "src/main/java").as_posix()
    info = directory / "package-info.java"
    if not info.is_file():
        problems.append(f"{relative}: no package-info.java")
        continue
    text = info.read_text(encoding="utf-8")
    comment = text.split("*/", 1)[0] if "*/" in text else ""
    if len(comment.strip()) < 60:
        problems.append(f"{relative}: the comment is essentially empty")
    if not CJK.search(comment):
        problems.append(f"{relative}: no Chinese in the comment")
    if not LATIN.search(comment):
        problems.append(f"{relative}: no English in the comment")

print(f"  {len(packages)} packages with source in them")
for problem in problems:
    print(f"  {problem}")
if problems:
    print(f"PACKAGE DOCS: {len(problems)} problem(s)")
    sys.exit(1)
print("PACKAGE DOCS: clean -- every package says what it is, in both languages")
