"""Manual GitHub Linux runner only. Never run this script on the user's Windows host."""
from pathlib import Path
import hashlib
import json
import os
import platform
import shutil
import subprocess
import sys
import tarfile
import urllib.request
import zipfile

AMETHYST = "7a0266e745d9b4acf400afa189b58e672900f710"
JDK = "c4deee6ead2e27dbd926e3965b2e0311ddbd6b3f"
CLEANER = "f9273bcb37bf4c187123438566657fa5ac40cd51"
DOWNLOADS = {
    "ndk.zip": ("https://dl.google.com/android/repository/android-ndk-r28-linux.zip", "sha1", "894f469c5192a116d21f412de27966140a530ebc"),
    "boot.tar.gz": ("https://github.com/adoptium/temurin24-binaries/releases/download/jdk-24.0.2%2B12/OpenJDK24U-jdk_x64_linux_hotspot_24.0.2_12.tar.gz", "sha256", "aea1cc55e51cf651c85f2f00ad021603fe269c4bb6493fa97a321ad770c9b096"),
    "freetype.tar.gz": ("https://downloads.sourceforge.net/project/freetype/freetype2/2.10.0/freetype-2.10.0.tar.gz", "sha256", "955e17244e9b38adb0c98df66abb50467312e6bb70eac07e49ce6bd1a20e809a"),
    "cups.tar.gz": ("https://github.com/apple/cups/releases/download/v2.2.4/cups-2.2.4-source.tar.gz", "sha256", "596d4db72651c335469ae5f37b0da72ac9f97d73e30838d787065f559dea98cc"),
}


def run(*args, cwd, **kwargs):
    subprocess.run(args, cwd=cwd, check=True, **kwargs)


def clone(url, commit, target):
    target.mkdir()
    run("git", "init", cwd=target)
    run("git", "remote", "add", "origin", url, cwd=target)
    run("git", "fetch", "--depth=1", "origin", commit, cwd=target)
    run("git", "checkout", "--detach", "FETCH_HEAD", cwd=target)
    actual = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=target, text=True).strip()
    if actual != commit:
        raise RuntimeError("Unexpected source commit")


def replace_once(path, old, new):
    text = path.read_text()
    if text.count(old) != 1:
        raise RuntimeError(f"Upstream script changed: {path.name}")
    path.write_text(text.replace(old, new), newline="\n")


def sha256(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def adapt_android_patch(builder):
    """Rebase two obsolete upstream hunks; preserve newer headless logic."""
    patch = builder / "patches/jdk25u_android.diff"
    text = patch.read_text()
    for start, end in (
        ("@@ -42,8 +42,8 @@ m4_include([lib-tests.m4])", "@@ -52,8 +52,8 @@"),
        ("@@ -348,2 +350,4 @@", "@@ -535,6 +539,7 @@"),
    ):
        if text.count(start) != 1 or text.count(end) != 1:
            raise RuntimeError("Unexpected Android patch hunks")
        begin = text.index(start)
        finish = text.index(end, begin)
        text = text[:begin] + text[finish:]
    patch.write_text(text, newline="\n")
    replace_once(builder / "openjdk/make/autoconf/libraries.m4",
                 '     test "x$ENABLE_HEADLESS_ONLY" = xtrue; then',
                 '     test "x$OPENJDK_TARGET_OS" = xandroid ||\n     test "x$ENABLE_HEADLESS_ONLY" = xtrue; then')
    replace_once(builder / "openjdk/src/hotspot/os/linux/os_linux.cpp",
                 '    #define SYS_gettid 186\n  #else',
                 '    #define SYS_gettid 186\n  #elif defined(__arm64__) || defined(__aarch64__)\n    #define SYS_gettid 178\n  #else')


def extract_tar(path, root):
    # Python 3.12 data filter blocks traversal and escaping links.
    with tarfile.open(path) as archive:
        archive.extractall(root, filter="data")


def main():
    if os.environ.get("GITHUB_ACTIONS") != "true" or platform.system() != "Linux":
        raise SystemExit("Only authorized GitHub Actions Linux execution is supported")
    root = Path(sys.argv[1]).resolve()
    if root.exists():
        raise SystemExit("Use an empty build directory; no files will be deleted")
    root.mkdir(parents=True)
    dist = root / "dist"
    dist.mkdir()
    builder = root / "builder"
    clone("https://github.com/AngelAuraMC/angelauramc-openjdk-build.git", AMETHYST, builder)
    clone("https://github.com/openjdk/jdk25u.git", JDK, builder / "openjdk")
    clone("https://github.com/termux/termux-elf-cleaner.git", CLEANER, builder / "termux-elf-cleaner")
    provenance = {"amethyst": AMETHYST, "openjdk": JDK, "elf_cleaner": CLEANER, "downloads": {}}
    for name, (url, algorithm, expected) in DOWNLOADS.items():
        path = root / name
        with urllib.request.urlopen(url, timeout=120) as source, path.open("wb") as output:
            shutil.copyfileobj(source, output)
        with path.open("rb") as stream:
            actual = hashlib.file_digest(stream, algorithm).hexdigest()
        if actual != expected:
            raise RuntimeError(f"Checksum mismatch: {name}")
        provenance["downloads"][name] = {"url": url, "algorithm": algorithm, "expected": expected, "sha256": sha256(path)}
        if name == "ndk.zip":
            with zipfile.ZipFile(path) as archive:
                # NDK is a verified Google archive; unzip preserves executable modes.
                if any(".." in Path(n).parts or n.startswith("/") for n in archive.namelist()):
                    raise RuntimeError("Unsafe ZIP entry")
            run("unzip", "-q", str(path), "-d", str(builder), cwd=root)
        else:
            extract_tar(path, builder)
        # Keep small dependency sources for license/source review; large installers stay on runner only.
        if name in ("freetype.tar.gz", "cups.tar.gz"):
            shutil.copy2(path, dist / name)
    (builder / "jdk-24.0.2+12").rename(builder / "jdk-24.0.2")
    env = os.environ.copy()
    env.update(TARGET="aarch64-linux-android", TARGET_JDK="aarch64", NDK_PREBUILT_ARCH="aarch64", JDK_DEBUG_LEVEL="release", JVM_VARIANTS="server")
    # Hosted runner has an Android SDK already; do not let it override our locked NDK.
    env.pop("ANDROID_NDK_ROOT", None)
    for name in ("android-wrapped-clang", "android-wrapped-clang++"):
        (builder / name).chmod(0o755)
    patch = Path(__file__).with_name("openmc-runtime-home.patch").resolve()
    shutil.copy2(patch, builder / "openmc-runtime-home.patch")
    adapt_android_patch(builder)
    build = builder / "build_jdk.sh"
    old = 'git reset --hard\ngit apply --reject --whitespace=fix ../patches/jdk25u_android.diff || echo "git apply failed (Android patch set)"'
    new = ('git apply --check --unidiff-zero ../patches/jdk25u_android.diff\n'
           'git apply --unidiff-zero ../patches/jdk25u_android.diff\n'
           'git apply --check --unidiff-zero ../openmc-runtime-home.patch\n'
           'git apply --unidiff-zero ../openmc-runtime-home.patch')
    replace_once(build, old, new)
    # No hidden retry: retain original failure for diagnosis.
    text = build.read_text()
    start = text.index('make JOBS=$jobs images ||')
    build.write_text(text[:start] + 'make JOBS=$jobs images\n')
    tar_script = builder / "tar_jdk.sh"
    replace_once(tar_script, 'git clone --depth 1 https://github.com/termux/termux-elf-cleaner || true', '# ELF cleaner already checked out at the locked commit')
    shutil.copy2(build, dist / "build_jdk.sh")
    shutil.copy2(patch, dist / patch.name)
    shutil.copy2(Path(__file__), dist / "build_ci.py")
    shutil.copy2(builder / "patches/jdk25u_android.diff", dist / "android-rebased.diff")
    for script in ("build_libs.sh", "build_jdk.sh", "remove_jdk_debug_info.sh", "tar_jdk.sh"):
        if shutil.disk_usage(root).free < 2 * 1024**3:
            raise RuntimeError("Runner has less than 2 GiB remaining")
        with (root / (script + ".log")).open("w") as log:
            run("bash", script, cwd=builder, env=env, stdout=log, stderr=subprocess.STDOUT)
    runtime = builder / "jreout"
    for name in ("lib/server/libjvm.so", "lib/libjava.so", "lib/libjimage.so"):
        header = (runtime / name).read_bytes()[:20]
        if header[:6] != b"\x7fELF\x02\x01" or int.from_bytes(header[18:20], "little") != 183:
            raise RuntimeError(f"Invalid AArch64 ELF: {name}")
    if not (runtime / "lib/modules").is_file() or not (runtime / "legal/java.base/LICENSE").is_file():
        raise RuntimeError("Missing modules or legal notices")
    jvm = (runtime / "lib/server/libjvm.so").read_bytes()
    if b"OPENMC_RUNTIME_HOME" not in jvm or b"OPENMC_NATIVE_DIR" not in jvm:
        raise RuntimeError("Runtime home patch is missing")
    readelf = builder / "android-ndk-r28/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-readelf"
    with (dist / "native-dependencies.txt").open("w") as output:
        for library in sorted(runtime.rglob("*.so")):
            output.write(str(library.relative_to(runtime)) + "\n"); output.flush()
            run(str(readelf), "-h", "-d", str(library), cwd=builder, stdout=output)
    archives = list(builder.glob("jre25-arm64-*-release.tar.xz"))
    if len(archives) != 1:
        raise RuntimeError("Expected exactly one runtime archive")
    shutil.copy2(archives[0], dist / "openmc-jre25-android-arm64.tar.xz")
    # Corresponding modified OpenJDK sources, not only a link to an upstream branch.
    run("tar", "--exclude=.git", "--exclude=build", "-cJf", str(dist / "openjdk-patched-source.tar.xz"), "-C", str(builder / "openjdk"), ".", cwd=root)
    run("tar", "--exclude=.git", "--exclude=build", "-cJf", str(dist / "elf-cleaner-source.tar.xz"), "-C", str(builder / "termux-elf-cleaner"), ".", cwd=root)
    with (dist / "amethyst-source.tar").open("wb") as output:
        run("git", "archive", "HEAD", cwd=builder, stdout=output)
    shutil.copy2(builder / "android-ndk-r28/toolchains/llvm/prebuilt/linux-x86_64/sysroot/NOTICE", dist / "ndk-sysroot-NOTICE.txt")
    shutil.copy2(builder / "android-ndk-r28/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so", dist / "libc++_shared.so")
    provenance["patch_sha256"] = sha256(patch)
    provenance["runtime_modules"] = {"bytes": (runtime / "lib/modules").stat().st_size, "sha256": sha256(runtime / "lib/modules")}
    provenance["runtime_release"] = (runtime / "release").read_text()
    provenance["validation"] = "Static ELF and packaging only; Android execution pending"
    (dist / "provenance.json").write_text(json.dumps(provenance, indent=2) + "\n")
    if sum(p.stat().st_size for p in dist.iterdir()) > 400 * 1024**2:
        raise RuntimeError("Artifact exceeds conservative 400 MiB limit")
    (dist / "SHA256SUMS").write_text("".join(f"{sha256(p)}  {p.name}\n" for p in sorted(dist.iterdir())))


if __name__ == "__main__":
    main()
