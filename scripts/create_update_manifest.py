#!/usr/bin/env python3
"""Stage a signed release APK and create its GitHub Releases manifest."""

import hashlib
import json
import re
import shutil
import subprocess
import sys
from pathlib import Path


def main() -> int:
    if len(sys.argv) != 4:
        print("usage: create_update_manifest.py APK OUTPUT_DIR ANDROID_SDK", file=sys.stderr)
        return 2

    apk = Path(sys.argv[1])
    output_dir = Path(sys.argv[2])
    sdk = Path(sys.argv[3])
    build_tools = sdk / "build-tools"
    aapt_candidates = list(build_tools.glob("*/aapt"))
    if not aapt_candidates:
        raise SystemExit(f"aapt was not found under {build_tools}")
    aapt = max(aapt_candidates, key=lambda path: tuple(int(part) for part in path.parent.name.split(".") if part.isdigit()))
    details = subprocess.run(
        [str(aapt), "dump", "badging", str(apk)],
        check=True,
        capture_output=True,
        text=True,
    ).stdout
    package_line = next(line for line in details.splitlines() if line.startswith("package:"))
    sdk_line = next(line for line in details.splitlines() if line.startswith("sdkVersion:"))
    package = dict(re.findall(r"(?:^|\s)(versionName|versionCode|name)='([^']*)'", package_line))
    min_sdk = re.search(r"sdkVersion:'([^']+)'", sdk_line).group(1)
    if package["name"] != "com.adulthub.tv":
        raise SystemExit(f"Unexpected APK package: {package['name']}")
    if not package["versionCode"].isdigit() or not min_sdk.isdigit():
        raise SystemExit("APK versionCode and minSdk must be numeric")

    version_name = package["versionName"]
    if not re.fullmatch(r"[A-Za-z0-9._+-]+", version_name):
        raise SystemExit(f"Unsafe APK versionName: {version_name}")
    apk_name = f"AdultHubTV-v{version_name}.apk"
    release_dir = output_dir / "releases"
    release_dir.mkdir(parents=True, exist_ok=True)
    published_apk = release_dir / apk_name
    shutil.copy2(apk, published_apk)
    manifest = {
        "schemaVersion": 2,
        "applicationId": package["name"],
        "versionCode": int(package["versionCode"]),
        "versionName": version_name,
        "minSdk": int(min_sdk),
        "apkAssetName": apk_name,
        "sizeBytes": published_apk.stat().st_size,
        "sha256": sha256_file(published_apk),
    }
    output_dir.mkdir(parents=True, exist_ok=True)
    manifest_path = output_dir / "stable.json"
    temporary_path = output_dir / "stable.json.tmp"
    temporary_path.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    temporary_path.replace(manifest_path)
    print(f"Created {manifest_path} for version {version_name} (code {package['versionCode']})")
    return 0


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as apk_file:
        for block in iter(lambda: apk_file.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


if __name__ == "__main__":
    raise SystemExit(main())
