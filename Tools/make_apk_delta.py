#!/usr/bin/env python3
"""Create byte-exact LGDELTA1 patches and release metadata (no APK re-signing).

python3 Tools/make_apk_delta.py --base old.apk --target new.apk \
    --version 2.10.2 --version-code 202611279 --output-dir delta
Upload linkigram-delta.json and *.lgdelta alongside the named full APK.
"""
import argparse
from collections import defaultdict
import gzip
import hashlib
import json
from pathlib import Path
import struct
import zlib

BLOCK = 16384
MAX_BYTES = 512 * 1024 * 1024


def sha(data):
    return hashlib.sha256(data).hexdigest()


def make_patch(base, target, path):
    if not 0 < len(base) <= MAX_BYTES or not 0 < len(target) <= MAX_BYTES:
        raise ValueError("APK size outside supported range")
    index = defaultdict(list)
    for offset in range(0, len(base) - BLOCK + 1, BLOCK):
        index[zlib.adler32(base[offset:offset + BLOCK])].append(offset)
    with path.open("wb") as raw:
        raw.write(b"LGDELTA1" + struct.pack(">qq", len(base), len(target)))
        raw.write(bytes.fromhex(sha(base)) + bytes.fromhex(sha(target)))
        with gzip.GzipFile(fileobj=raw, mode="wb", mtime=0) as out:
            pos = literal = 0
            checksum = None
            while pos + BLOCK <= len(target):
                if checksum is None:
                    checksum = zlib.adler32(target[pos:pos + BLOCK])
                match = None
                for offset in index.get(checksum, ()):
                    if base[offset:offset + BLOCK] == target[pos:pos + BLOCK]:
                        match = offset
                        break
                if match is not None:
                    if pos > literal:
                        out.write(b"\x02" + struct.pack(">I", pos - literal) + target[literal:pos])
                    length = BLOCK
                    while (match + length + BLOCK <= len(base) and pos + length + BLOCK <= len(target)
                           and base[match + length:match + length + BLOCK] == target[pos + length:pos + length + BLOCK]):
                        length += BLOCK
                    out.write(b"\x01" + struct.pack(">qI", match, length))
                    pos += length
                    literal = pos
                    checksum = None
                else:
                    if pos + BLOCK == len(target):
                        break
                    # Adler-32 rolling window, including its initial +1 term.
                    a, b = checksum & 65535, checksum >> 16
                    a = (a - target[pos] + target[pos + BLOCK]) % 65521
                    b = (b - BLOCK * target[pos] + a - 1) % 65521
                    checksum = (b << 16) | a
                    pos += 1
            if literal < len(target):
                out.write(b"\x02" + struct.pack(">I", len(target) - literal) + target[literal:])
            out.write(b"\x00")


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--base", type=Path, action="append", required=True)
    p.add_argument("--target", type=Path, required=True)
    p.add_argument("--apk-name", help="Full APK asset name on GitHub; defaults to target filename")
    p.add_argument("--version", required=True)
    p.add_argument("--version-code", type=int, required=True)
    p.add_argument("--output-dir", type=Path, required=True)
    args = p.parse_args()
    args.output_dir.mkdir(parents=True, exist_ok=True)
    target = args.target.read_bytes()
    manifest = dict(format="linkigram-delta", version=1, target=dict(
        name=args.apk_name or args.target.name, versionName=args.version,
        versionCode=args.version_code, size=len(target), sha256=sha(target)), patches=[])
    seen = set()
    for file in args.base:
        base = file.read_bytes()
        base_hash = sha(base)
        if base_hash in seen:
            continue
        seen.add(base_hash)
        patch = args.output_dir / (base_hash + ".lgdelta")
        make_patch(base, target, patch)
        ratio = patch.stat().st_size / len(target)
        print(f"{file.name}: {patch.stat().st_size:,} / {len(target):,} bytes ({ratio:.1%})")
        if ratio >= .85:
            patch.unlink()
            print("Not advertised: less than 15% saving")
            continue
        manifest["patches"].append(dict(name=patch.name, fromSha256=base_hash,
            fromSize=len(base), size=patch.stat().st_size, sha256=sha(patch.read_bytes())))
    (args.output_dir / "linkigram-delta.json").write_text(json.dumps(manifest, indent=2) + "\n")


if __name__ == "__main__":
    main()
