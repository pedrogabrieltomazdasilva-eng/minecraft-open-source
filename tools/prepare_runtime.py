"""Verify and package the published Amethyst OpenJDK 25 ARM64 archive.

Run with an archive downloaded from the project's official release. Requires
only Python's standard library. No executable is extracted to app data.
"""

from __future__ import annotations

import hashlib
import os
from pathlib import Path, PurePosixPath
import shutil
import sys
import tarfile
import zipfile


EXPECTED_SHA256 = "d3eb7afe2240c26728a1bb440502c5f18ac3883e932d202dd7f0c9bcbbce4c37"


def hash_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def safe_name(name: str) -> str:
    relative = name.removeprefix("./")
    parts = PurePosixPath(relative).parts
    if not parts or any(part in ("", ".", "..") for part in parts) or name.startswith("/"):
        raise ValueError(f"Unsafe archive path: {name}")
    return relative


def main() -> None:
    if len(sys.argv) != 2:
        raise SystemExit("Usage: python tools/prepare_runtime.py PATH_TO_JRE25_TAR_XZ")
    archive = Path(sys.argv[1]).resolve(strict=True)
    actual = hash_file(archive)
    if actual != EXPECTED_SHA256:
        raise ValueError(f"SHA-256 mismatch: {actual}")

    project = Path(__file__).resolve().parents[1]
    assets = project / "app/src/main/assets"
    native = project / "app/src/main/jniLibs/arm64-v8a"
    assets.mkdir(parents=True, exist_ok=True)
    native.mkdir(parents=True, exist_ok=True)
    temp_zip = assets / "runtime-data.zip.part"
    links: list[str] = []
    seen_libraries: dict[str, str] = {}
    with tarfile.open(archive, "r:xz") as source, zipfile.ZipFile(
        temp_zip, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=6
    ) as data:
        members = source.getmembers()
        for member in members:
            if member.name in (".", "./"):
                continue
            safe_name(member.name)
            if member.issym() or member.islnk():
                target = PurePosixPath(member.name).parent.joinpath(member.linkname)
                if ".." in target.parts and not member.name.startswith("./legal/"):
                    raise ValueError(f"Unexpected symlink: {member.name}")

        for member in members:
            if not (member.isfile() or member.issym() or member.islnk()):
                continue
            name = safe_name(member.name)
            if name.startswith("bin/"):
                continue
            stream = source.extractfile(member)
            if stream is None:
                raise ValueError(f"Unreadable archive entry: {name}")
            with stream:
                if name.endswith(".so"):
                    header = stream.read(20)
                    if header[:4] != b"\x7fELF" or header[4] != 2 or int.from_bytes(header[18:20], "little") != 183:
                        raise ValueError(f"Not an AArch64 ELF library: {name}")
                    basename = PurePosixPath(name).name
                    destination = native / basename
                    temporary = native / (basename + ".part")
                    with temporary.open("wb") as output:
                        output.write(header)
                        shutil.copyfileobj(stream, output)
                    digest = hash_file(temporary)
                    if basename in seen_libraries:
                        if seen_libraries[basename] != digest:
                            raise ValueError(f"Conflicting library name: {basename}")
                        temporary.unlink()
                    else:
                        seen_libraries[basename] = digest
                        os.replace(temporary, destination)
                    links.append(name)
                else:
                    with data.open(name, "w") as output:
                        shutil.copyfileobj(stream, output)

    required = {"libjvm.so", "libjli.so", "libjava.so"}
    if not required.issubset(seen_libraries):
        raise ValueError(f"Missing native libraries: {required - seen_libraries.keys()}")
    ndk_value = os.environ.get("ANDROID_NDK_HOME")
    if not ndk_value:
        raise ValueError("ANDROID_NDK_HOME must point to the verified Android NDK r30")
    ndk = Path(ndk_value).resolve(strict=True)
    hosts = ["windows-x86_64", "linux-x86_64", "darwin-x86_64"]
    host = next((name for name in hosts if (ndk / "toolchains/llvm/prebuilt" / name).is_dir()), None)
    if host is None:
        raise ValueError("NDK host toolchain not found")
    sysroot = ndk / "toolchains/llvm/prebuilt" / host / "sysroot"
    cpp = sysroot / "usr/lib/aarch64-linux-android/libc++_shared.so"
    with cpp.open("rb") as stream:
        header = stream.read(20)
    if header[:4] != b"\x7fELF" or header[4] != 2 or int.from_bytes(header[18:20], "little") != 183:
        raise ValueError("NDK libc++ is not an AArch64 ELF library")
    shutil.copy2(cpp, native / cpp.name)
    shutil.copy2(sysroot / "NOTICE", assets / "ndk-sysroot-NOTICE.txt")
    with zipfile.ZipFile(temp_zip) as verify:
        entries = set(verify.namelist())
        if not {"lib/modules", "release", "legal/java.base/LICENSE"}.issubset(entries):
            raise ValueError("Missing runtime data or license")
        bad_entry = verify.testzip()
        if bad_entry is not None:
            raise ValueError(f"Bad ZIP CRC: {bad_entry}")
    os.replace(temp_zip, assets / "runtime-data.zip")
    (assets / "runtime-native-links.txt").write_text("\n".join(sorted(set(links))) + "\n", encoding="utf-8")
    print(f"Packaged {len(seen_libraries)} libraries and {len(entries)} data files")
    print(f"Runtime data ZIP SHA-256: {hash_file(assets / 'runtime-data.zip')}")


if __name__ == "__main__":
    main()
