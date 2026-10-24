"""Moves classes to new packages and repairs every reference to them.

Written for GlobalShop's reorganisation and meant to be used more than once, so it is deliberately
unforgiving. The failure it exists to prevent is the one that is not a failure: a patch script that
runs, prints that it did something, and quietly matched nothing. Every step here either proves it
happened or stops.

What it does, in order:

1. Finds each class by name anywhere under `src/main/java`. A class it cannot find is an error, not a
   skip — a mapping that has gone stale is exactly how a move ends up half done.
2. Moves the file, creates the directory, and rewrites the `package` line. The old package is read
   from the file rather than supplied, because a supplied one can be wrong and a read one cannot.
3. Rewrites every fully-qualified reference to the moved classes. The name is matched with a regex
   that tolerates whitespace around the dots, because this codebase has at least one call site where
   the package name is wrapped onto its own line to fit the column — a literal replacement misses
   exactly that case and reports success.
4. Adds the imports the moved files now need for each other. Two classes that shared a package do not
   need an import; two that used to and no longer do, do. Getting this wrong is a compile error, so
   it is not checked for here.
5. Verifies: every file is where the mapping says, and no source file still names a moved class
   through its old package. Both are asserted, and the exit code says whether it worked.

Usage:
    python tools/refactor_move.py mapping.json

where the JSON is `{"ClassName": "new.package.without.the.mod.prefix", ...}`.
"""
import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
JAVA = ROOT / "src/main/java"
BASE = "com.example.globalshop"


def find_class(name):
    """The one file declaring `class Name`, or None."""
    pattern = re.compile(r"\b(class|interface|enum|record)\s+" + re.escape(name) + r"\b")
    found = []
    for path in JAVA.rglob(name + ".java"):
        if pattern.search(path.read_text(encoding="utf-8")):
            found.append(path)
    if len(found) > 1:
        print(f"FAIL: {name} is declared in {len(found)} files: {found}")
        sys.exit(1)
    return found[0] if found else None


def package_of(text):
    match = re.search(r"^package\s+([\w.]+);", text, re.MULTILINE)
    return match.group(1) if match else None


def main():
    if len(sys.argv) != 2:
        print(__doc__)
        return 2
    mapping = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))

    moves = {}
    for name, new_package in mapping.items():
        path = find_class(name)
        if path is None:
            print(f"FAIL: no source file declares {name}")
            return 1
        text = path.read_text(encoding="utf-8")
        old_package = package_of(text)
        if old_package is None or not old_package.startswith(BASE):
            print(f"FAIL: {name} has no package in the mod's namespace ({old_package})")
            return 1
        moves[name] = {
            "path": path,
            "old": old_package,
            "new": BASE + "." + new_package,
        }
    print(f"  {len(moves)} classes to move")

    # 2. The files themselves.
    for name, move in moves.items():
        destination = JAVA / move["new"].replace(".", "/") / (name + ".java")
        destination.parent.mkdir(parents=True, exist_ok=True)
        text = move["path"].read_text(encoding="utf-8")
        text = re.sub(r"^package\s+[\w.]+;", f"package {move['new']};", text,
                      count=1, flags=re.MULTILINE)
        destination.write_text(text, encoding="utf-8")
        if destination != move["path"]:
            move["path"].unlink()
        move["path"] = destination
    print("  files moved and their package lines rewritten")

    # 3. References, with whitespace tolerated around the dots.
    fqn_edits = 0
    for path in sorted(JAVA.rglob("*.java")):
        text = path.read_text(encoding="utf-8")
        updated = text
        for name, move in moves.items():
            pattern = re.compile(
                re.escape(move["old"]) + r"\s*\.\s*" + re.escape(name) + r"\b")
            updated, count = pattern.subn(move["new"] + "." + name, updated)
            fqn_edits += count
        if updated != text:
            path.write_text(updated, encoding="utf-8")
    print(f"  {fqn_edits} fully-qualified references rewritten")

    # 4. Imports every file now needs for a moved class it names by simple name.
    #
    # This runs over every file, not only the moved ones. A class that shared a package with the
    # screens referred to them plainly and needed no import; after the move it does. The first
    # version of this tool only looked at moved files and only rewrote fully-qualified names, so it
    # reported success while a dozen references had quietly lost their imports — which is precisely
    # the failure this tool exists to make impossible.
    added = 0
    for path in sorted(JAVA.rglob("*.java")):
        text = path.read_text(encoding="utf-8")
        own_package = package_of(text)
        needed = []
        for name, move in moves.items():
            if own_package == move["new"]:
                continue
            if not re.search(r"\b" + re.escape(name) + r"\b", text):
                continue
            if re.search(r"^import\s+" + re.escape(move["new"] + "." + name) + r";",
                         text, re.MULTILINE):
                continue
            needed.append(f"import {move['new']}.{name};")
        if not needed:
            continue
        lines = text.splitlines(keepends=True)
        insert_at = 0
        for i, line in enumerate(lines):
            if line.startswith("package ") or line.startswith("import "):
                insert_at = i + 1
        for line in sorted(needed):
            lines.insert(insert_at, line + "\n")
            insert_at += 1
            added += 1
        path.write_text("".join(lines), encoding="utf-8")
    print(f"  {added} imports added where a plain name would no longer resolve")

    # 4b. And the mirror case: a moved file may name a class that did NOT move, from the package it
    # left. The step above only knows about classes in the mapping, so without this a packet that
    # called BalanceUpdatePacket would silently lose it — which is exactly what happened.
    mirror = 0
    stayed = {}
    for move in moves.values():
        directory = JAVA / move["old"].replace(".", "/")
        if not directory.is_dir():
            continue
        for path in directory.glob("*.java"):
            if path.stem not in moves:
                stayed.setdefault(path.stem, move["old"])
    for name, move in moves.items():
        text = move["path"].read_text(encoding="utf-8")
        own_package = package_of(text)
        needed = []
        for other, other_package in stayed.items():
            if other_package == own_package:
                continue
            if not re.search(r"\b" + re.escape(other) + r"\b", text):
                continue
            if re.search(r"^import\s+" + re.escape(other_package + "." + other) + r";",
                         text, re.MULTILINE):
                continue
            needed.append(f"import {other_package}.{other};")
        if not needed:
            continue
        lines = text.splitlines(keepends=True)
        insert_at = 0
        for i, line in enumerate(lines):
            if line.startswith("package ") or line.startswith("import "):
                insert_at = i + 1
        for line in sorted(needed):
            lines.insert(insert_at, line + "\n")
            insert_at += 1
            mirror += 1
        move["path"].write_text("".join(lines), encoding="utf-8")
    print(f"  {mirror} imports added for classes that stayed behind")

    # 5. Prove it, rather than assume it.
    problems = []
    for name, move in moves.items():
        expected = JAVA / move["new"].replace(".", "/") / (name + ".java")
        if not expected.is_file():
            problems.append(f"{name} is not at {expected.relative_to(ROOT)}")
        elif f"package {move['new']};" not in expected.read_text(encoding="utf-8"):
            problems.append(f"{name} does not declare {move['new']}")
    stale = []
    for name, move in moves.items():
        if move["old"] == move["new"]:
            # Re-running the tool over an already-moved class: the package it reads from the file is
            # the new one, so looking for the old one would flag every correct import as a leftover.
            continue
        pattern = re.compile(re.escape(move["old"]) + r"\s*\.\s*" + re.escape(name) + r"\b")
        for path in JAVA.rglob("*.java"):
            if pattern.search(path.read_text(encoding="utf-8")):
                stale.append(f"{path.relative_to(ROOT)} still names {move['old']}.{name}")

    # The check that matters, and the one the first version was missing: a plain name resolves only
    # if the file is in the class's package or imports it. Checking fully-qualified names alone says
    # nothing about the dozen references that had quietly stopped resolving.
    unresolved = []
    for name, move in moves.items():
        for path in JAVA.rglob("*.java"):
            text = path.read_text(encoding="utf-8")
            if package_of(text) == move["new"]:
                continue
            if not re.search(r"\b" + re.escape(name) + r"\b", text):
                continue
            if re.search(r"^import\s+" + re.escape(move["new"] + "." + name) + r";",
                         text, re.MULTILINE):
                continue
            if re.search(re.escape(move["old"]) + r"\s*\.\s*" + re.escape(name) + r"\b", text):
                continue
            unresolved.append(f"{path.relative_to(ROOT)} names {name} without importing it")

    problems.extend(stale)
    problems.extend(unresolved)
    if problems:
        print("FAIL: the move did not finish")
        for problem in problems:
            print("  " + problem)
        return 1
    print(f"  verified: {len(moves)} classes moved, nothing still names the old packages")
    return 0


if __name__ == "__main__":
    sys.exit(main())
