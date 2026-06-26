package win.pokedemo;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.security.CodeSource;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Extracts and incrementally syncs a bundled plugin data folder from inside the jar.
 *
 * Resources are stored under /bundled_plugin/ (same structure as plugin data folder).
 *
 * Behaviour:
 * - First run (marker missing): can overwrite existing files when requested, so a brand-new install
 *   gets the latest curated defaults instead of half-created Bukkit placeholders.
 * - Later runs (marker exists): still scans the bundled folder and copies only MISSING files.
 *   This lets old servers upgrade to newer jars and automatically receive newly-added data files
 *   such as species_raw/species/generation2~generation9 JSON without clobbering admin edits.
 *
 * Runtime-generated logs/debug/reports should not be extracted from the jar.
 */
public final class BundledDataExtractor {
    private BundledDataExtractor() {}

    /**
     * @param overwriteOnFirstExtract if true and the marker file is missing, bundled files will overwrite
     *                                any existing files in the data folder. This is useful because Bukkit
     *                                may create config/lang files before we get a chance to extract.
     */
    public static void ensureExtracted(JavaPlugin plugin, boolean overwriteOnFirstExtract) {
        try {
            Path dataDir = plugin.getDataFolder().toPath();
            Files.createDirectories(dataDir);

            // Marker for "we already performed the first full extract".
            Path marker = dataDir.resolve(".bundled_plugin_extracted");
            boolean firstExtract = !Files.exists(marker);
            boolean overwrite = firstExtract && overwriteOnFirstExtract;

            SyncStats stats = extractPrefix(plugin, "bundled_plugin/", dataDir, overwrite);

            // Always keep / refresh the marker so upgraded servers can continue receiving missing files.
            try {
                Files.writeString(marker,
                        "extracted=1\n" +
                        "firstExtract=" + firstExtract + "\n" +
                        "copied=" + stats.copiedFiles + "\n" +
                        "skipped=" + stats.skippedExistingFiles + "\n" +
                        "ignored=" + stats.ignoredFiles + "\n",
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            } catch (IOException ignored) {}

            if (firstExtract) {
                plugin.getLogger().info("[PokeDemo] Bundled plugin folder extracted to " + dataDir.toAbsolutePath()
                        + " (copied=" + stats.copiedFiles + ", ignored=" + stats.ignoredFiles + ")");
            } else if (stats.copiedFiles > 0) {
                plugin.getLogger().info("[PokeDemo] Bundled plugin folder synced missing files to " + dataDir.toAbsolutePath()
                        + " (copied=" + stats.copiedFiles + ", skippedExisting=" + stats.skippedExistingFiles
                        + ", ignored=" + stats.ignoredFiles + ")");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[PokeDemo] Failed to extract bundled plugin folder: " + e.getMessage());
        }
    }

    /** Backward compatible default: non-destructive extraction. */
    public static void ensureExtracted(JavaPlugin plugin) {
        ensureExtracted(plugin, false);
    }

    private static SyncStats extractPrefix(JavaPlugin plugin, String prefix, Path outDir, boolean overwriteExisting) throws IOException, URISyntaxException {
        File jarFile = getJarFile(plugin);
        if (jarFile == null || !jarFile.exists()) return new SyncStats();

        SyncStats stats = new SyncStats();
        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry e = entries.nextElement();
                String name = e.getName();
                if (!name.startsWith(prefix)) continue;
                String rel = name.substring(prefix.length());
                if (rel.isEmpty()) continue;
                rel = rel.replace('\\', '/');

                if (shouldIgnoreBundledPath(rel)) {
                    if (!e.isDirectory()) stats.ignoredFiles++;
                    continue;
                }

                Path target = outDir.resolve(rel);
                if (e.isDirectory()) {
                    Files.createDirectories(target);
                    continue;
                }
                // Non-destructive sync on normal upgrades: do not overwrite existing files.
                if (!overwriteExisting && Files.exists(target)) {
                    stats.skippedExistingFiles++;
                    continue;
                }
                Files.createDirectories(target.getParent());
                try (InputStream in = jar.getInputStream(e)) {
                    Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                    stats.copiedFiles++;
                }
            }
        }
        return stats;
    }

    private static boolean shouldIgnoreBundledPath(String rel) {
        String p = rel.toLowerCase(java.util.Locale.ROOT);
        return p.equals(".bundled_plugin_extracted")
                || p.startsWith("reports/")
                || p.startsWith("logs/")
                || p.startsWith("debug/");
    }

    private static final class SyncStats {
        int copiedFiles;
        int skippedExistingFiles;
        int ignoredFiles;
    }

    private static File getJarFile(JavaPlugin plugin) throws URISyntaxException {
        CodeSource src = plugin.getClass().getProtectionDomain().getCodeSource();
        if (src == null || src.getLocation() == null) return null;
        return new File(src.getLocation().toURI());
    }
}
