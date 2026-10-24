"""Checks that everything a player can read is translatable.

Two questions, both cheap to answer and expensive to discover later:

1. Does any Java string reach the screen without going through a translation key? A hardcoded
   English word shows as English to every player in every language, which is the whole of what
   "international" rules out.
2. Do the two language files carry the same keys? A key present in one and missing in the other is a
   player seeing a raw key name, and it is invisible until somebody switches language.

Full marks are not expected: an item name, a command literal and a debug string are all legitimately
hardcoded. What the report is for is making sure the list is short and deliberate.
"""
import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
JAVA = ROOT / "src" / "main" / "java"
LANG = ROOT / "src" / "main" / "resources" / "assets" / "globalshop" / "lang"

# A string literal, allowing escaped quotes inside it.
LITERAL = re.compile(r'"((?:[^"\\]|\\.)*)"')
CJK = re.compile(r"[\u4e00-\u9fff\u3000-\u303f\uff00-\uffef]")
# The self-test harness prints to the server log, not to a screen. Reporting its strings for ever
# would teach the eye to skip this report, and then a real finding would be missed.
TEST_SUFFIX_MARK = "globalshop/test/SelfTest.java"
# Passed straight to the player by name, so worth seeing separately from the rest.
SHOWN = re.compile(
    r"(Component\.literal|sendSystemMessage\(\s*Component\.literal|displayClientMessage"
    r"\(\s*Component\.literal)")


def report_hardcoded():
    print("Hardcoded strings that could reach a screen")
    print("-" * 70)
    found = 0
    for path in sorted(JAVA.rglob("*.java")):
        if path.as_posix().endswith(TEST_SUFFIX_MARK):
            continue
        text = path.read_text(encoding="utf-8")
        relative = path.relative_to(ROOT)
        for line_number, line in enumerate(text.splitlines(), 1):
            stripped = line.strip()
            if stripped.startswith("*") or stripped.startswith("//"):
                continue
            for match in LITERAL.finditer(line):
                value = match.group(1)
                # Keys, resource paths, format specifiers and debug words are not prose.
                if not value or "globalshop." in value or value.startswith("%"):
                    continue
                if not re.search(r"[A-Za-z]{2}", value):
                    continue
                if SHOWN.search(line):
                    print(f"  {relative}:{line_number}: {value!r}")
                    found += 1
    print(f"  total: {found}")
    print()
    return found


def report_cjk_in_code():
    print("Chinese characters inside Java string literals")
    print("-" * 70)
    found = 0
    for path in sorted(JAVA.rglob("*.java")):
        if path.as_posix().endswith(TEST_SUFFIX_MARK):
            continue
        text = path.read_text(encoding="utf-8")
        relative = path.relative_to(ROOT)
        for line_number, line in enumerate(text.splitlines(), 1):
            stripped = line.strip()
            if stripped.startswith("*") or stripped.startswith("//"):
                continue
            for match in LITERAL.finditer(line):
                if CJK.search(match.group(1)):
                    print(f"  {relative}:{line_number}: {match.group(1)!r}")
                    found += 1
    print(f"  total: {found}  (should be 0: all player-facing Chinese lives in zh_cn.json)")
    print()
    return found


def report_lang_parity():
    print("Language files")
    print("-" * 70)
    data = {}
    for name in ("en_us", "zh_cn"):
        data[name] = json.loads((LANG / f"{name}.json").read_text(encoding="utf-8"))
        print(f"  {name}.json: {len(data[name])} keys")
    only_en = sorted(set(data["en_us"]) - set(data["zh_cn"]))
    only_zh = sorted(set(data["zh_cn"]) - set(data["en_us"]))
    for key in only_en:
        print(f"  missing from zh_cn: {key}")
    for key in only_zh:
        print(f"  missing from en_us: {key}")
    print(f"  differences: {len(only_en) + len(only_zh)}")
    print()
    return len(only_en) + len(only_zh)


hard = report_hardcoded()
cjk = report_cjk_in_code()
diff = report_lang_parity()
print("NOTE: the test harness is skipped here. Its output goes to the log, not to a screen -- but\n      it is compiled into the release jar, and whether it should be is a separate decision.")
print()
print("I18N CHECK:", "clean" if hard == 0 and cjk == 0 and diff == 0
      else f"{hard} hardcoded, {cjk} Chinese literals, {diff} key differences")
sys.exit(0)
