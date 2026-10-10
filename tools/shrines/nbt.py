"""Minimal NBT reader/writer (Java edition, big-endian, gzip) for structure template files.

Values are plain Python objects tagged with small wrapper classes where the NBT type
would be ambiguous: Byte, Short, Int, Long, Float, Double, IntArray, List(elem_type, items).
Compounds are dicts, strings are str.
"""
import gzip
import io
import struct

END, BYTE, SHORT, INT, LONG, FLOAT, DOUBLE, BYTE_ARRAY, STRING, LIST, COMPOUND, INT_ARRAY, LONG_ARRAY = range(13)


class Byte(int):
    pass


class Short(int):
    pass


class Int(int):
    pass


class Long(int):
    pass


class Float(float):
    pass


class Double(float):
    pass


class IntArray(list):
    pass


class List(list):
    """An NBT list; elem_type is one of the tag ids above (END for an empty list)."""

    def __init__(self, elem_type, items=()):
        super().__init__(items)
        self.elem_type = elem_type


def _type_of(value):
    if isinstance(value, Byte):
        return BYTE
    if isinstance(value, Short):
        return SHORT
    if isinstance(value, Long):
        return LONG
    if isinstance(value, Int) or (isinstance(value, int) and not isinstance(value, bool)):
        return INT
    if isinstance(value, Float):
        return FLOAT
    if isinstance(value, Double) or isinstance(value, float):
        return DOUBLE
    if isinstance(value, str):
        return STRING
    if isinstance(value, IntArray):
        return INT_ARRAY
    if isinstance(value, List):
        return LIST
    if isinstance(value, dict):
        return COMPOUND
    raise TypeError(f"Can't store {type(value)} in NBT")


def _write_string(out, s):
    data = s.encode("utf-8")
    out.write(struct.pack(">H", len(data)))
    out.write(data)


def _write_payload(out, tag, value):
    if tag == BYTE:
        out.write(struct.pack(">b", value))
    elif tag == SHORT:
        out.write(struct.pack(">h", value))
    elif tag == INT:
        out.write(struct.pack(">i", value))
    elif tag == LONG:
        out.write(struct.pack(">q", value))
    elif tag == FLOAT:
        out.write(struct.pack(">f", value))
    elif tag == DOUBLE:
        out.write(struct.pack(">d", value))
    elif tag == STRING:
        _write_string(out, value)
    elif tag == INT_ARRAY:
        out.write(struct.pack(">i", len(value)))
        out.write(struct.pack(f">{len(value)}i", *value))
    elif tag == LIST:
        elem = value.elem_type if len(value) else END
        out.write(struct.pack(">bi", elem, len(value)))
        for item in value:
            _write_payload(out, elem, item)
    elif tag == COMPOUND:
        for key, item in value.items():
            t = _type_of(item)
            out.write(struct.pack(">b", t))
            _write_string(out, key)
            _write_payload(out, t, item)
        out.write(struct.pack(">b", END))
    else:
        raise ValueError(tag)


def to_bytes(root):
    """The gzipped file contents. mtime=0 keeps the output the same every time for the same data."""
    buf = io.BytesIO()
    buf.write(struct.pack(">b", COMPOUND))
    _write_string(buf, "")
    _write_payload(buf, COMPOUND, root)
    return gzip.compress(buf.getvalue(), mtime=0)


def write_file(path, root):
    """Writes the file only if its contents changed. Returns True if it wrote."""
    data = to_bytes(root)
    try:
        with open(path, "rb") as f:
            if f.read() == data:
                return False
    except OSError:
        pass
    with open(path, "wb") as f:
        f.write(data)
    return True


def _read_string(data, i):
    (n,) = struct.unpack_from(">H", data, i)
    i += 2
    return data[i:i + n].decode("utf-8", "replace"), i + n


def _read_payload(data, i, tag):
    if tag == BYTE:
        return struct.unpack_from(">b", data, i)[0], i + 1
    if tag == SHORT:
        return struct.unpack_from(">h", data, i)[0], i + 2
    if tag == INT:
        return struct.unpack_from(">i", data, i)[0], i + 4
    if tag == LONG:
        return struct.unpack_from(">q", data, i)[0], i + 8
    if tag == FLOAT:
        return struct.unpack_from(">f", data, i)[0], i + 4
    if tag == DOUBLE:
        return struct.unpack_from(">d", data, i)[0], i + 8
    if tag == BYTE_ARRAY:
        (n,) = struct.unpack_from(">i", data, i)
        return bytes(data[i + 4:i + 4 + n]), i + 4 + n
    if tag == STRING:
        return _read_string(data, i)
    if tag == LIST:
        elem, n = struct.unpack_from(">bi", data, i)
        i += 5
        items = List(elem)
        for _ in range(n):
            v, i = _read_payload(data, i, elem)
            items.append(v)
        return items, i
    if tag == COMPOUND:
        out = {}
        while True:
            (t,) = struct.unpack_from(">b", data, i)
            i += 1
            if t == END:
                return out, i
            key, i = _read_string(data, i)
            out[key], i = _read_payload(data, i, t)
    if tag == INT_ARRAY:
        (n,) = struct.unpack_from(">i", data, i)
        return list(struct.unpack_from(f">{n}i", data, i + 4)), i + 4 + 4 * n
    if tag == LONG_ARRAY:
        (n,) = struct.unpack_from(">i", data, i)
        return list(struct.unpack_from(f">{n}q", data, i + 4)), i + 4 + 8 * n
    raise ValueError(f"bad tag {tag} at {i}")


def read_bytes(raw):
    try:
        data = gzip.decompress(raw)
    except OSError:
        data = raw
    (t,) = struct.unpack_from(">b", data, 0)
    assert t == COMPOUND
    _, i = _read_string(data, 1)
    root, _ = _read_payload(data, i, COMPOUND)
    return root


def read_file(path):
    with open(path, "rb") as f:
        return read_bytes(f.read())
