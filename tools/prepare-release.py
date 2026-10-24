#!/usr/bin/env python3
"""Validate a production APK and prepare self-contained GitHub release assets."""
import argparse
import datetime
import hashlib
import json
import os
import pathlib
import re
import shutil
import subprocess
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
SOURCE_PATHS = [
    "README.md", "LICENSE", "NOTICE", "AGENTS.md", ".gitignore",
    "build.gradle.kts", "settings.gradle.kts", "gradle.properties",
    "gradlew", "gradlew.bat", "gradle",
    "app/build.gradle.kts", "app/proguard-rules.pro", "app/src",
    "third_party", "tools",
    "docs/THIRD_PARTY_NOTICES.md", "docs/OPEN_SOURCE_LICENSES.md",
    "docs/RELEASING.md", "docs/RELEASE-NOTES.md",
]
PRIVATE_NAMES = {".signing", ".gradle", ".gradle-user-home", ".kotlin",
                 ".git", "__pycache__", "build", "local.properties", "keystore.properties",
                 "signing.properties", ".env"}
PRIVATE_SUFFIXES = {".jks", ".keystore", ".p12", ".pfx", ".key", ".pyc"}


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def sdk_tool(name):
    installed = shutil.which(name)
    if installed:
        return installed
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    local = ROOT / "local.properties"
    if not sdk and local.is_file():
        match = re.search(r"^sdk.dir=(.+)$", local.read_text(), re.M)
        if match:
            sdk = match[1].replace("\\:", ":").replace("\\\\", "\\")
    if sdk:
        candidates = list((pathlib.Path(sdk) / "build-tools").glob("*/" + name))
        if candidates:
            return str(max(candidates, key=lambda p: tuple(int(n) for n in re.findall(r"\d+", p.parent.name))))
    raise RuntimeError("Android SDK tool not found: " + name)


def command(*args):
    result = subprocess.run(args, capture_output=True, text=True)
    if result.returncode:
        raise RuntimeError(pathlib.Path(args[0]).name + " failed: " + result.stderr[:1000])
    return result.stdout


def signing_identity(certificates):
    if "android debug" in certificates.lower():
        raise RuntimeError("A debug signing certificate cannot be published")
    fingerprint = re.search(r"certificate SHA-256 digest:\s*([0-9a-fA-F]{64})", certificates)
    if not fingerprint or not re.search(r"Verified using v[23][^\n]*true", certificates):
        raise RuntimeError("APK must have a verified v2/v3 signing certificate")
    return fingerprint[1].lower()


def source_files():
    found = set()
    for relative in SOURCE_PATHS:
        path = ROOT / relative
        if not path.exists():
            raise RuntimeError("Required source path is missing: " + relative)
        candidates = path.rglob("*") if path.is_dir() else [path]
        for item in candidates:
            rel = item.relative_to(ROOT)
            if any(part in PRIVATE_NAMES or part.startswith(".env.") for part in rel.parts) or item.suffix.lower() in PRIVATE_SUFFIXES:
                continue
            if item.is_symlink():
                raise RuntimeError("Source archives cannot contain symlinks: " + str(rel))
            if item.is_file():
                found.add(rel)
    return sorted(found)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", type=pathlib.Path, required=True)
    parser.add_argument("--out", type=pathlib.Path)
    args = parser.parse_args()
    gradle = (ROOT / "app/build.gradle.kts").read_text()
    version = re.search(r'versionName\s*=\s*"([^"]+)"', gradle)[1]
    code = int(re.search(r"versionCode\s*=\s*(\d+)", gradle)[1])
    aapt, signer = sdk_tool("aapt"), sdk_tool("apksigner")
    badging = command(aapt, "dump", "badging", str(args.apk))
    package = re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", badging)
    if not package or package.groups() != ("com.lopleec.slopeweather", str(code), version):
        raise RuntimeError("APK identity/version does not match the project")
    if "application-debuggable" in badging:
        raise RuntimeError("A debuggable APK cannot be published")
    manifest = command(aapt, "dump", "xmltree", str(args.apk), "AndroidManifest.xml")
    for name in ("debuggable", "testOnly", "usesCleartextTraffic", "allowBackup"):
        if re.search(r"android:" + name + r"\([^\n]+\)=\(type 0x12\)0xffffffff", manifest):
            raise RuntimeError("Unexpected release manifest flag: " + name)
    if "android.permission.ACCESS_BACKGROUND_LOCATION" in badging:
        raise RuntimeError("Unexpected background location permission")
    certificates = command(signer, "verify", "--verbose", "--print-certs", str(args.apk))
    fingerprint = signing_identity(certificates)
    with zipfile.ZipFile(args.apk) as archive:
        names = set(archive.namelist())
        for required in ("assets/third-party-components.json", "assets/licenses/Runtime-dependencies.txt",
                         "assets/licenses/Slope-Weather-Apache-2.0.txt"):
            if required not in names:
                raise RuntimeError("Missing bundled notice: " + required)
        for name in names:
            if name.endswith(".dex"):
                dex = archive.read(name)
                if any(marker in dex for marker in (b"WeatherRenderChecks", b"Rendering isolation", b"Rendering test")):
                    raise RuntimeError("Diagnostic entry point is present in the production APK")
    paths = source_files()
    out = args.out or ROOT / "dist" / version
    out.mkdir(parents=True, exist_ok=True)
    apk = out / ("Slope-Weather-" + version + ".apk")
    source = out / ("Slope-Weather-" + version + "-source.zip")
    epoch = max(315532800, int(os.environ.get("SOURCE_DATE_EPOCH", "315532800")))
    stamp = datetime.datetime.fromtimestamp(epoch, datetime.timezone.utc).timetuple()[:6]
    with zipfile.ZipFile(source, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for relative in paths:
            item = zipfile.ZipInfo("Slope-Weather-" + version + "/" + relative.as_posix(), stamp)
            item.compress_type = zipfile.ZIP_DEFLATED
            item.external_attr = (0o100755 if relative.as_posix() == "gradlew" else 0o100644) << 16
            archive.writestr(item, (ROOT / relative).read_bytes())
    shutil.copy2(args.apk, apk)
    notes = out / "RELEASE-NOTES.md"
    shutil.copy2(ROOT / "docs/RELEASE-NOTES.md", notes)
    info = {
        "package": package[1], "version": version, "versionCode": code,
        "minSdk": int(re.search(r"sdkVersion:'(\d+)'", badging)[1]),
        "targetSdk": int(re.search(r"targetSdkVersion:'(\d+)'", badging)[1]),
        "certificate_sha256": fingerprint,
        "debuggable": False, "source_files": len(paths),
        "apk_sha256": digest(apk), "source_sha256": digest(source),
    }
    metadata = out / "BUILD-INFO.json"
    metadata.write_text(json.dumps(info, indent=2) + "\n")
    (out / "SHA256SUMS").write_text("".join(digest(p) + "  " + p.name + "\n" for p in (apk, source, notes, metadata)))
    print(json.dumps(info, indent=2))
    print("Prepared artifacts: " + str(out))


if __name__ == "__main__":
    main()
