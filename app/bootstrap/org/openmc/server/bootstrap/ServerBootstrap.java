package org.openmc.server.bootstrap;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.jar.JarFile;

/** Runs the Main-Class declared by a user-supplied server JAR. */
public final class ServerBootstrap {
    private ServerBootstrap() {}

    public static void main(String[] args) throws Exception {
        if (args.length == 0) throw new IllegalArgumentException("Server JAR path is required");
        Path jar = Path.of(args[0]).toAbsolutePath();
        String mainClass;
        try (JarFile archive = new JarFile(jar.toFile())) {
            if (archive.getManifest() == null) throw new IllegalArgumentException("JAR has no manifest");
            mainClass = archive.getManifest().getMainAttributes().getValue("Main-Class");
        }
        if (mainClass == null || mainClass.isBlank()) {
            throw new IllegalArgumentException("JAR has no Main-Class");
        }
        URL[] urls = { jar.toUri().toURL() };
        try (URLClassLoader loader = new URLClassLoader(urls, ClassLoader.getPlatformClassLoader())) {
            Thread.currentThread().setContextClassLoader(loader);
            Class<?> entry = Class.forName(mainClass, true, loader);
            String[] serverArgs = Arrays.copyOfRange(args, 1, args.length);
            entry.getMethod("main", String[].class).invoke(null, (Object) serverArgs);
        }
    }
}
