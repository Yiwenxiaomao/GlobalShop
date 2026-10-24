"""Checks that the two guides still say the same things.

The pair is meant to be the same document in two languages. A translation drifts the moment only
one side is edited, and a guide that quietly tells English readers something different from Chinese
readers is worse than one that is simply out of date. This compares the parts that must match
exactly, regardless of language:

  * every backticked token — commands, config keys, file paths, enum-ish names
  * every number that appears in prose or tables
  * the number of headings, and their levels
  * the set of fenced code blocks, by first line

Prose is deliberately not compared: it is in different languages by design.
"""

import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
PAIR = [("Chinese", ROOT / "GUIDE.zh_cn.md"),
        ("English", ROOT / "GUIDE.md")]

BACKTICK = re.compile(r"`([^`\n]+)`")
# Only the digits. The unit around a number is prose ("12 小时" / "12 hours") and the word for it
# differs by language; the figure itself must not. Commas are formatting and are dropped, so
# "1,000" and "1000" compare equal.
NUMBER = re.compile(r"\b(\d[\d,]*(?:\.\d+)?)")
HEADING = re.compile(r"^(#{1,6})\s+(.*)$", re.MULTILINE)
FENCE = re.compile(r"^```(\w*)\n(.*?)^```", re.MULTILINE | re.DOTALL)


def read(path):
    if not path.exists():
        return None
    return path.read_text(encoding="utf-8")


def tokens(text):
    """Backticked spans, normalised: bracketed placeholders are language-specific words."""
    out = set()
    for raw in BACKTICK.findall(text):
        item = raw.strip()
        # `<物品>` / `<item>` and `[序号]` / `[index]` differ by language, so compare the bracket
        # *shape*, not the word inside it.
        item = re.sub(r"<[^>]*>", "<>", item)
        item = re.sub(r"\[[^\]]*\]", "[]", item)
        if item:
            out.add(item)
    return out


def numbers(text):
    return {match.replace(",", "") for match in NUMBER.findall(text)}


def headings(text):
    return [(len(hashes), title.strip()) for hashes, title in HEADING.findall(text)]


BOX_CHARS = set("┌┐└┘│─├┤┬┴┼▓")


def fences(text):
    """Code blocks reduced to a skeleton that does not depend on the language.

    Comments inside a code block are prose and get translated, so they are stripped: what has to
    match is the command or the config line. A diagram is different again — its labels are prose but
    its box drawing is not, and a box that shifts is the layout bug this project keeps having — so a
    block containing box characters is reduced to the box characters alone, per line.
    """
    out = []
    for _lang, body in FENCE.findall(text):
        raw_lines = body.splitlines()
        if any(ch in BOX_CHARS for line in raw_lines for ch in line):
            shape = ["".join(ch for ch in line if ch in BOX_CHARS) for line in raw_lines]
            out.append("diagram: " + " | ".join(shape))
            continue
        kept = []
        for line in raw_lines:
            code = line.split("#", 1)[0].rstrip()
            if code.strip():
                code = re.sub(r"<[^>]*>", "<>", code)
                code = re.sub(r"\[[^\]]*\]", "[]", code)
                kept.append(" ".join(code.split()))
        out.append(" | ".join(kept))
    return out


def swallowed_blocks(text):
    """(block number, line number in the file, the offending line) for each unbalanced fence.

    A fence that is never closed swallows everything after it until the next fence, so the symptom
    is a "code block" containing a `## heading` or a `---` rule. Reporting the file line number is
    what makes it fixable; a count mismatch only says a block is missing.
    """
    guilty = []
    for index, match in enumerate(FENCE.finditer(text), start=1):
        start_line = text.count("\n", 0, match.start(2)) + 1
        for offset, line in enumerate(match.group(2).splitlines()):
            stripped = line.strip()
            if stripped.startswith("## ") or stripped == "---":
                guilty.append((index, start_line + offset, stripped))
                break
    return guilty


def main():
    texts = {}
    for name, path in PAIR:
        text = read(path)
        if text is None:
            print("MISSING: %s (%s)" % (name, path.name))
            return 1
        texts[name] = text

    problems = []
    zh, en = texts["Chinese"], texts["English"]

    zh_tokens, en_tokens = tokens(zh), tokens(en)
    for missing in sorted(zh_tokens - en_tokens):
        problems.append("the English guide is missing the token `%s`" % missing)
    for extra in sorted(en_tokens - zh_tokens):
        problems.append("the English guide has an extra token `%s`" % extra)

    zh_nums, en_nums = numbers(zh), numbers(en)
    for missing in sorted(zh_nums - en_nums):
        problems.append("the English guide is missing the number %s" % missing)
    for extra in sorted(en_nums - zh_nums):
        problems.append("the English guide has an extra number %s" % extra)

    zh_heads, en_heads = headings(zh), headings(en)
    # An unclosed fence, which is the usual way one guide ends up structurally different.
    for name, text in (("Chinese", zh), ("English", en)):
        for index, line_no, swallowed in swallowed_blocks(text):
            problems.append("%s: code block #%d (line %d) has swallowed %r — a ``` is not closed"
                            % (name, index, line_no, swallowed))

    if len(zh_heads) != len(en_heads):
        problems.append("heading count differs: Chinese %d, English %d"
                        % (len(zh_heads), len(en_heads)))
    else:
        for (zh_level, zh_title), (en_level, en_title) in zip(zh_heads, en_heads):
            if zh_level != en_level:
                problems.append("heading level differs at '%s' / '%s': %d vs %d"
                                % (zh_title, en_title, zh_level, en_level))

    zh_fences, en_fences = fences(zh), fences(en)
    if len(zh_fences) != len(en_fences):
        problems.append("code block count differs: Chinese %d, English %d"
                        % (len(zh_fences), len(en_fences)))
    # Always diffed, not only when the counts disagree: "one is missing" without saying which is
    # not a useful thing to report, and a substitution keeps the counts equal while changing a
    # command block.
    import difflib
    for line in difflib.unified_diff(zh_fences, en_fences, "chinese", "english", lineterm="", n=0):
        if line.startswith(("---", "+++", "@@")):
            continue
        if line.startswith("-"):
            problems.append("the English guide is missing the code block starting '%s'"
                            % line[1:].strip())
        elif line.startswith("+"):
            problems.append("the English guide has an extra code block starting '%s'"
                            % line[1:].strip())

    print("Chinese: %d lines, %d headings, %d tokens, %d numbers"
          % (zh.count("\n") + 1, len(zh_heads), len(zh_tokens), len(zh_nums)))
    print("English: %d lines, %d headings, %d tokens, %d numbers"
          % (en.count("\n") + 1, len(en_heads), len(en_tokens), len(en_nums)))

    if problems:
        print("\nGUIDE PARITY: %d problem(s)" % len(problems))
        for problem in problems[:40]:
            print("  - %s" % problem)
        return 1
    print("\nGUIDE PARITY: clean")
    return 0


if __name__ == "__main__":
    sys.exit(main())
