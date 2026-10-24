"""Checks the guide against the code it documents.

A guide is only worth having if it is true. Two things in it are checkable mechanically, and both
have been wrong at least once in this project:

  * a command the guide tells people to type that is not actually registered
      (this is how `/globalshop vip info` came to be documented a version before it existed)
  * a config default printed in the guide that disagrees with Config.java
      (a renamed key or a changed default leaves the table quietly lying)

Run it after touching the command tree, Config.java, or either guide.
"""

import pathlib
import re
import sys

BT = chr(96)
ROOT = pathlib.Path(__file__).resolve().parent.parent
COMMANDS = (ROOT / "src" / "main" / "java" / "com" / "example" / "globalshop"
            / "core" / "command")
# A checker that cannot find its input must fail. When the commands moved, this directory held
# nothing and the report went on saying every command was documented -- a green that means the
# check stopped happening rather than that it passed.
if not COMMANDS.is_dir():
    print(f"FAIL: the command package is not at {COMMANDS}")
    raise SystemExit(1)
CONFIG = ROOT / "src" / "main" / "java" / "com" / "example" / "globalshop" / "Config.java"

# Second-level words that legitimately never appear straight after "/globalshop", so they are not
# evidence of a gap on their own. Each is reached through its parent, e.g. "/globalshop vip info".
NESTED_OK = {
    "accounts", "add", "claimall", "delete", "globalshop", "grant", "help", "info", "list",
    "offer", "price", "resetDaily", "revoke", "selftest", "shop", "withdraw", "buy", "claim",
    "buy", "run", "make", "set",
    # The teleport branch: /globalshop tp <open|random|accept|deny|cancel>, and the bare
    # /globalshop tp. The guide documents all of them together, under one heading.
    "tp",
    "open",
    "accept",
    "deny",
    "cancel",
    "random",
}


def guide_text():
    """The Chinese guide."""
    preferred = ROOT / "GUIDE.zh_cn.md"
    if preferred.exists():
        return preferred.read_text(encoding="utf-8")
    candidates = [p for p in ROOT.glob("*.zh_cn.md") if not p.name.startswith("README")]
    if not candidates:
        print("no Chinese guide found at the project root")
        return None
    return candidates[0].read_text(encoding="utf-8")


def registered_literals():
    found = set()
    if not COMMANDS.is_dir():
        return found
    for path in COMMANDS.glob("*.java"):
        source = path.read_text(encoding="utf-8")
        found.update(re.findall(r'Commands\.literal\("([A-Za-z]+)"\)', source))
        # Some literals are built from a parameter — MoneyCommand.buildMoney("money") — so the name
        # never appears next to Commands.literal and a naive scan reports it as missing.
        found.update(re.findall(r'build(?:Money|Pay)\("([A-Za-z]+)"\)', source))
    return found


def config_defaults():
    source = CONFIG.read_text(encoding="utf-8")
    found = {}
    for key, raw in re.findall(r'\.(?:defineInRange|define)\("([A-Za-z]+)",\s*([^,\)]+)', source):
        value = raw.strip().strip('"').replace("_", "").replace(",", "")
        if value.endswith("L"):
            value = value[:-1]
        found[key] = value
    return found


def documented_defaults(guide):
    pattern = ("^\\|\\s*" + BT + "([A-Za-z][A-Za-z0-9]*)" + BT + "\\s*\\|\\s*"
               + BT + "([^" + BT + "]+)" + BT)
    found = {}
    for key, raw in re.findall(pattern, guide, re.M):
        # Quotes stripped on both sides: the code writes "" for an empty string, the guide writes
        # it as `""` so a reader can see that it is empty rather than missing.
        found[key] = raw.strip().strip('"').replace("_", "").replace(",", "")
    return found


def main():
    guide = guide_text()
    if guide is None:
        return 1
    problems = []

    # 1. Commands the guide tells people to type.
    documented = set(re.findall(r"/globalshop\s+([a-zA-Z]+)", guide))
    registered = registered_literals()
    undocumented = sorted(documented - registered)
    print("commands: %d named in the guide, %d literals registered"
          % (len(documented), len(registered)))
    for name in undocumented:
        problems.append("the guide uses /globalshop %s, which is not registered anywhere" % name)
    unused = sorted(registered - documented - NESTED_OK)
    for name in unused:
        problems.append("the command /globalshop %s exists but the guide never mentions it" % name)

    # 2. Config defaults printed in the tables.
    code = config_defaults()
    table = documented_defaults(guide)
    print("config:  %d keys in Config.java, %d printed in the guide" % (len(code), len(table)))
    for key in sorted(set(code) - set(table)):
        problems.append("config key %s is not documented" % key)
    for key in sorted(set(table) - set(code)):
        problems.append("the guide documents config key %s, which does not exist" % key)
    for key in sorted(set(code) & set(table)):
        if code[key] != table[key]:
            problems.append("config default for %s: code says %s, the guide says %s"
                            % (key, code[key], table[key]))

    if problems:
        print("\nGUIDE VS CODE: %d problem(s)" % len(problems))
        for problem in problems:
            print("  - %s" % problem)
        return 1
    print("\nGUIDE VS CODE: clean")
    return 0


if __name__ == "__main__":
    sys.exit(main())
