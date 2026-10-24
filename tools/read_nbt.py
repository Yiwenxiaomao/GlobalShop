"""Reads Minecraft's .dat files, which are gzipped NBT.

Written to answer a question rather than to be a general library: a SavedData file holds the bank's
timestamps, and reading them directly says whether the server thinks a day has passed — which is the
thing nobody can currently see. The bank now logs the same numbers, but a file on disk does not need
the server to be running and does not need anybody to read a log.

Prints the whole tree, because the shape is the answer as much as the values are.
"""
import gzip
import pathlib
import struct
import sys

TAG_NAMES = {0: "End", 1: "Byte", 2: "Short", 3: "Int", 4: "Long", 5: "Float", 6: "Double",
             7: "ByteArray", 8: "String", 9: "List", 10: "Compound", 11: "IntArray",
             12: "LongArray"}


class Reader:
    def __init__(self, data):
        self.data = data
        self.at = 0

    def take(self, count):
        chunk = self.data[self.at:self.at + count]
        self.at += count
        return chunk

    def number(self, fmt, size):
        return struct.unpack(">" + fmt, self.take(size))[0]

    def byte(self):
        return self.number("b", 1)

    def short(self):
        return self.number("h", 2)

    def int(self):
        return self.number("i", 4)

    def long(self):
        return self.number("q", 8)

    def float(self):
        return self.number("f", 4)

    def double(self):
        return self.number("d", 8)

    def string(self):
        return self.take(self.number("H", 2)).decode("utf-8", "replace")

    def payload(self, tag):
        if tag == 1:
            return self.byte()
        if tag == 2:
            return self.short()
        if tag == 3:
            return self.int()
        if tag == 4:
            return self.long()
        if tag == 5:
            return self.float()
        if tag == 6:
            return self.double()
        if tag == 7:
            return list(self.take(self.int()))
        if tag == 8:
            return self.string()
        if tag == 9:
            kind = self.byte()
            count = self.int()
            return [self.payload(kind) for _ in range(count)]
        if tag == 10:
            return self.compound()
        if tag == 11:
            return [self.int() for _ in range(self.int())]
        if tag == 12:
            return [self.long() for _ in range(self.int())]
        raise ValueError(f"unknown tag {tag} at {self.at}")

    def compound(self):
        result = {}
        while True:
            tag = self.byte()
            if tag == 0:
                return result
            name = self.string()
            result[name] = self.payload(tag)


def show(value, indent, label):
    pad = "  " * indent
    if isinstance(value, dict):
        print(f"{pad}{label}:")
        for key, item in value.items():
            show(item, indent + 1, key)
    elif isinstance(value, list) and value and not isinstance(value[0], (int, float)):
        print(f"{pad}{label}: ({len(value)} entries)")
        for index, item in enumerate(value[:12]):
            show(item, indent + 1, f"[{index}]")
        if len(value) > 12:
            print(f"{pad}  ... and {len(value) - 12} more")
    else:
        text = repr(value) if isinstance(value, (list, bytes)) else repr(value)
        if len(text) > 96:
            text = text[:93] + "..."
        print(f"{pad}{label} = {text}")


for argument in sys.argv[1:]:
    path = pathlib.Path(argument)
    print(f"=== {path} ===")
    try:
        raw = gzip.decompress(path.read_bytes())
    except OSError:
        raw = path.read_bytes()
    reader = Reader(raw)
    root_tag = reader.byte()
    root_name = reader.string()
    print(f"  root: {TAG_NAMES.get(root_tag, root_tag)} named {root_name!r}")
    for key, value in reader.compound().items():
        show(value, 1, key)
    print(f"  ({len(raw)} bytes)")
    print()
