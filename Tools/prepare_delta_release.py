"""Prepare release delta assets from the latest released ARM64 APK, without publishing anything."""
import argparse
from pathlib import Path
import subprocess
import sys


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--base-dir", type=Path, required=True)
    p.add_argument("--target", type=Path, required=True)
    p.add_argument("--output-dir", type=Path, required=True)
    args = p.parse_args()
    root = Path(__file__).resolve().parents[1]
    props = dict(line.split("=", 1) for line in (root / "gradle.properties").read_text().splitlines()
                 if "=" in line and not line.startswith("#"))
    version = props["APP_VERSION_NAME"]
    code = int(props["APP_VERSION_CODE"]) * 10 + 9  # standalone ARM64 build.yml
    bases = sorted(args.base_dir.glob("*.apk"))
    if not bases:
        raise SystemExit("No released base APK; full APK remains available")
    subprocess.run([sys.executable, str(root / "Tools/make_apk_delta.py"),
                    *[part for base in bases for part in ("--base", str(base))],
                    "--target", str(args.target), "--version", version, "--version-code", str(code),
                    "--apk-name", f"LinkiGram-{version}-arm64.apk", "--output-dir", str(args.output_dir)], check=True)
    # Exercise the production Java decoder against the real APK pair in CI, not just fixture data.
    subprocess.run([sys.executable, str(root / "Tools/test_apk_delta.py"),
                    "--base", str(bases[0]), "--target", str(args.target)], check=True)


if __name__ == "__main__":
    main()
