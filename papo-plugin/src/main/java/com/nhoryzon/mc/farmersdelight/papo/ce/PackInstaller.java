package com.nhoryzon.mc.farmersdelight.papo.ce;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Extracts the bundled CraftEngine pack (ce-pack/ inside the plugin jar) into
 * {@code plugins/CraftEngine/resources/farmersdelight/} whenever the bundled
 * version differs from the installed one, then triggers a CraftEngine reload.
 */
public final class PackInstaller {

    public static final String PACK_DIR = "ce-pack";
    public static final String TARGET_NAME = "farmersdelight";
    private static final String VERSION_FILE = ".fd-installed-version";

    private PackInstaller() {
    }

    /**
     * @return true if files were (re)installed and a CraftEngine reload is required.
     */
    public static boolean install(JavaPlugin plugin, String bundledVersion) throws IOException {
        Path ceResources = plugin.getDataFolder().toPath().getParent()
                .resolve("CraftEngine").resolve("resources");
        Files.createDirectories(ceResources);
        Path target = ceResources.resolve(TARGET_NAME);

        Path versionFile = target.resolve(VERSION_FILE);
        String installed = readVersion(versionFile);
        if (bundledVersion.equals(installed) && Files.exists(target.resolve("pack.yml"))) {
            return false;
        }

        plugin.getLogger().info("Installing Farmer's Delight CraftEngine pack v" + bundledVersion + "...");
        if (Files.exists(target)) {
            try (Stream<Path> walk = Files.walk(target)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException ignored) {
                    }
                });
            }
        }
        Files.createDirectories(target);
        copyDirectory(plugin, PACK_DIR, target);
        Files.writeString(versionFile, bundledVersion);
        return true;
    }

    private static String readVersion(Path file) {
        try {
            return Files.exists(file) ? Files.readString(file).trim() : "";
        } catch (IOException e) {
            return "";
        }
    }

    private static void copyDirectory(JavaPlugin plugin, String resourcePath, Path target) throws IOException {
        try (Stream<Path> walk = Files.walk(target)) {
            // target already emptied
        }
        copyRecursive(plugin, resourcePath, target);
    }

    private static void copyRecursive(JavaPlugin plugin, String resourcePath, Path target) throws IOException {
        // list entries via classpath scanning of the jar
        var urls = plugin.getClass().getClassLoader().resources(resourcePath).toList();
        for (var url : urls) {
            if (url.getProtocol().equals("jar")) {
                try (var jar = new java.util.jar.JarFile(
                        java.net.URLDecoder.decode(url.getPath().substring(5, url.getPath().indexOf('!')),
                                java.nio.charset.StandardCharsets.UTF_8))) {
                    var entries = jar.entries();
                    while (entries.hasMoreElements()) {
                        var entry = entries.nextElement();
                        String name = entry.getName();
                        if (!name.startsWith(resourcePath + "/") || entry.isDirectory()) {
                            continue;
                        }
                        String rel = name.substring(resourcePath.length() + 1);
                        Path out = target.resolve(rel);
                        Files.createDirectories(out.getParent());
                        try (InputStream in = jar.getInputStream(entry)) {
                            Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                } catch (IOException e) {
                    throw new IOException("Failed to read plugin jar for ce-pack extraction", e);
                }
                return;
            } else if (url.getProtocol().equals("file")) {
                Path dir = Path.of(java.net.URLDecoder.decode(url.getPath(), java.nio.charset.StandardCharsets.UTF_8));
                try (Stream<Path> walk = Files.walk(dir)) {
                    for (Path src : (Iterable<Path>) walk::iterator) {
                        if (src.equals(dir)) continue;
                        Path out = target.resolve(dir.relativize(src).toString());
                        if (Files.isDirectory(src)) {
                            Files.createDirectories(out);
                        } else {
                            Files.createDirectories(out.getParent());
                            Files.copy(src, out, StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                }
                return;
            }
        }
        throw new IOException("ce-pack resources not found on classpath");
    }
}
