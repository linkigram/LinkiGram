"""Compile the production delta decoder and test Python-generated patches without Android."""
import argparse
import gzip
import hashlib
import os
from pathlib import Path
import random
import shutil
import struct
import subprocess
import tempfile

from make_apk_delta import make_patch


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", type=Path)
    parser.add_argument("--target", type=Path)
    args = parser.parse_args()
    home = os.environ.get("JAVA_HOME")
    javac = str(Path(home) / "bin/javac") if home else shutil.which("javac")
    java = str(Path(home) / "bin/java") if home else shutil.which("java")
    root = Path(__file__).resolve().parents[1]
    source = root / "TMessagesProj/src/main/java/app/nimarkogram/messenger/updater/ApkDelta.java"
    with tempfile.TemporaryDirectory(prefix="apk-delta-") as temp:
        folder = Path(temp)
        runner = folder / "Check.java"
        runner.write_text('''
import app.nimarkogram.messenger.updater.ApkDelta;
import java.io.*;
public class Check {
 public static void main(String[] args) throws Exception {
  final boolean cancel = args.length > 3;
  ApkDelta.apply(new File(args[0]), new File(args[1]), new File(args[2]), new ApkDelta.Control() {
   int checks;
   public void check() throws IOException { if(cancel && ++checks > 2) throw new InterruptedIOException(); }
   public void progress(long written,long total) { if(written>total) throw new AssertionError(); }
  });
 }
}''')
        subprocess.run([javac, "-d", temp, str(source), str(runner)], check=True)
        basefile, patchfile, output = [folder / name for name in ("base.apk", "patch", "out.apk")]
        checks = 0

        def run(ok=True, cancel=False):
            nonlocal checks
            result = subprocess.run([java, "-cp", temp, "Check", str(basefile), str(patchfile), str(output),
                                     *(["cancel"] if cancel else [])], capture_output=True)
            assert (result.returncode == 0) == ok, result.stderr.decode()
            if not ok:
                assert not output.exists(), "Failed reconstruction left an installable candidate"
            checks += 1

        rng = random.Random(741)
        base = rng.randbytes(2 * 1024 * 1024)
        for target in (base, b"insert" + base, base[:60000] + b"changed" * 100 + base[70000:],
                       base[500000:] + base[:500000], b"small", rng.randbytes(50000)):
            basefile.write_bytes(base)
            make_patch(base, target, patchfile)
            run()
            assert output.read_bytes() == target
            output.unlink()
        target = base[:100000] + b"insertion" + base[100000:]
        make_patch(base, target, patchfile)
        assert patchfile.stat().st_size < len(target) // 10
        valid = patchfile.read_bytes()
        for broken in (b"bad", valid[:50], valid[:-8], valid[:56] + bytes(32) + valid[88:]):
            patchfile.write_bytes(broken)
            run(False)
        patchfile.write_bytes(valid)
        basefile.write_bytes(b"x" + base[1:])
        run(False)
        basefile.write_bytes(base)
        run(False, True)
        for command in (b"\x03", b"\x01" + struct.pack(">qI", -1, 10),
                        b"\x01" + struct.pack(">qI", len(base), 10),
                        b"\x02" + struct.pack(">I", len(target) + 1),
                        b"\x02" + struct.pack(">I", 0), b"\x00", b"\x00garbage"):
            patchfile.write_bytes(valid[:88] + gzip.compress(command))
            run(False)
        # Header size overflow and exact terminal/trailing-data checks.
        for header in (valid[:8] + struct.pack(">qq", -1, len(target)) + valid[24:88],
                       valid[:16] + struct.pack(">q", 2**62) + valid[24:88]):
            patchfile.write_bytes(header + valid[88:])
            run(False)
        for command in (b"\x02" + struct.pack(">I", len(target)) + target + b"\x00tail",
                        b"\x02" + struct.pack(">I", len(target)) + b"X" * len(target) + b"\x00"):
            patchfile.write_bytes(valid[:88] + gzip.compress(command))
            run(False)
        if args.base and args.target:
            base = args.base.read_bytes()
            target = args.target.read_bytes()
            basefile.write_bytes(base)
            make_patch(base, target, patchfile)
            run()
            assert hashlib.sha256(output.read_bytes()).digest() == hashlib.sha256(target).digest()
            print(f"APK patch: {patchfile.stat().st_size:,}/{len(target):,} bytes ({patchfile.stat().st_size/len(target):.1%})")
        print(f"PASS: {checks} production decoder checks (roundtrip, shifted data, corruption, bounds, cancellation)")


if __name__ == "__main__":
    main()
