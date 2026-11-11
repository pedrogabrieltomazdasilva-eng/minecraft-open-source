plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Keep generated artifacts on an external development drive when C: is nearly full.
System.getenv("OPENMC_BUILD_DIR")?.takeIf { it.isNotBlank() }?.let { path ->
    layout.buildDirectory.set(file(path))
}

val compileBootstrap by tasks.registering(JavaCompile::class) {
    source = fileTree("bootstrap") { include("**/*.java") }
    classpath = files()
    destinationDirectory.set(layout.buildDirectory.dir("generated/bootstrap/classes"))
    options.release.set(17)
}

val packageBootstrap by tasks.registering(Jar::class) {
    dependsOn(compileBootstrap)
    from(compileBootstrap.map { it.destinationDirectory })
    archiveFileName.set("openmc-bootstrap.jar")
    destinationDirectory.set(layout.buildDirectory.dir("generated/bootstrap/assets"))
}

val compileArm64Launcher by tasks.registering(Exec::class) {
    val sourceFile = file("src/main/cpp/openmc_launcher.c")
    val outputFile = layout.buildDirectory.file("generated/native/arm64-v8a/libopenmc_launcher.so")
    inputs.file(sourceFile)
    outputs.file(outputFile)
    doFirst {
        val ndk = System.getenv("ANDROID_NDK_HOME")
            ?: error("ANDROID_NDK_HOME must point to Android NDK r30")
        val host = if (System.getProperty("os.name").startsWith("Windows")) "windows-x86_64" else "linux-x86_64"
        val clang = file("$ndk/toolchains/llvm/prebuilt/$host/bin/clang${if (host.startsWith("windows")) ".exe" else ""}")
        check(clang.isFile) { "Android NDK clang not found: $clang" }
        outputFile.get().asFile.parentFile.mkdirs()
        commandLine(
            clang.absolutePath, "--target=aarch64-linux-android26", "-O2", "-fPIE", "-pie",
            "-Wall", "-Wextra", "-Werror", "-Wl,-z,relro,-z,now",
            sourceFile.absolutePath, "-ldl", "-o", outputFile.get().asFile.absolutePath
        )
    }
}

android {
    namespace = "org.openmc.server"
    compileSdk = 36
    packaging { jniLibs { useLegacyPackaging = true } }
    sourceSets.getByName("main").assets.srcDir(layout.buildDirectory.dir("generated/bootstrap/assets"))
    sourceSets.getByName("main").jniLibs.srcDir(layout.buildDirectory.dir("generated/native"))
    defaultConfig {
        applicationId = "org.openmc.server"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-poc"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}

tasks.configureEach {
    if (name == "mergeDebugAssets") dependsOn(packageBootstrap)
    if (name == "mergeDebugJniLibFolders") dependsOn(compileArm64Launcher)
}
