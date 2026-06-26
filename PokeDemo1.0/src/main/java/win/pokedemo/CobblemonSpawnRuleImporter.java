package win.pokedemo;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Best-effort importer for Cobblemon-style spawn_pool_world json files.
 * This does not aim to support every datapack feature. Instead it converts the most important
 * fields into plugin-side SpawnTable rules and degrades unsupported features safely.
 */
public final class CobblemonSpawnRuleImporter {
    private final PokeDemoPlugin plugin;
    private final Dex dex;

    public CobblemonSpawnRuleImporter(PokeDemoPlugin plugin, Dex dex) {
        this.plugin = plugin;
        this.dex = dex;
    }

    public List<SpawnTable.Entry> loadImportedRules() {
        List<SpawnTable.Entry> out = new ArrayList<>();
        if (plugin == null || dex == null) return out;
        if (!plugin.getConfig().getBoolean("spawns.cobblemon-import.enabled", false)) {
            plugin.getLogger().info("[PokeDemo] Cobblemon spawn import disabled by config.");
            return out;
        }

        ImportStats stats = new ImportStats();
        boolean preferBundled = plugin.getConfig().getBoolean("spawns.cobblemon-import.prefer-bundled", true);
        boolean mergeFolderAfterBundled = plugin.getConfig().getBoolean("spawns.cobblemon-import.merge-folder-after-bundled", false);
        boolean allowExternalJar = plugin.getConfig().getBoolean("spawns.cobblemon-import.allow-external-jar", false);

        if (preferBundled) {
            loadFromBundledResource(out, stats);
        }

        String folderName = plugin.getConfig().getString("spawns.cobblemon-import-folder", "cobblemon_spawn_pool_world");
        File dir = new File(plugin.getDataFolder(), folderName);
        if (dir.exists() && dir.isDirectory() && (!preferBundled || out.isEmpty() || mergeFolderAfterBundled)) {
            loadFromFolder(dir, out, stats);
        }

        if (allowExternalJar && (!preferBundled || out.isEmpty())) {
            File jar = findCobblemonJar();
            if (jar != null && jar.isFile()) {
                loadFromJar(jar, out, stats);
            }
        }

        if (!out.isEmpty()) {
            plugin.getLogger().info("[PokeDemo] Imported " + out.size() + " spawn rules from Cobblemon data. files="
                    + stats.filesOk + ", skipped=" + stats.skippedEntries + ", failed=" + stats.failedFiles
                    + ", source=" + stats.sourceSummary() + ", speciesFallbacks=" + stats.mappedSpeciesFallbacks
                    + ", fishingFallbacks=" + stats.degradedFishingFallbacks + ", confidence=" + stats.confidenceSummary()
                    + ", skipTop=" + stats.skipSummary());
        } else {
            plugin.getLogger().info("[PokeDemo] Cobblemon spawn import found no usable rules. files="
                    + stats.filesOk + ", skipped=" + stats.skippedEntries + ", failed=" + stats.failedFiles
                    + ", speciesFallbacks=" + stats.mappedSpeciesFallbacks
                    + ", confidence=" + stats.confidenceSummary()
                    + ", skipTop=" + stats.skipSummary() + ". " + explainNoImportSource(preferBundled, allowExternalJar));
        }
        return out;
    }

    private void loadFromBundledResource(List<SpawnTable.Entry> out, ImportStats stats) {
        final String resourceName = "default_data/cobblemon_spawn_pool_world_bundle.zip";
        try (var in = plugin.getResource(resourceName)) {
            if (in == null) {
                stats.notes.add("bundled-resource-missing");
                return;
            }
            try (java.util.zip.ZipInputStream zin = new java.util.zip.ZipInputStream(in, StandardCharsets.UTF_8)) {
                java.util.zip.ZipEntry ze;
                while ((ze = zin.getNextEntry()) != null) {
                    String name = ze.getName();
                    if (ze.isDirectory()) continue;
                    if (!name.startsWith("data/cobblemon/spawn_pool_world/") || !name.endsWith(".json")) continue;
                    try {
                        byte[] bytes = zin.readAllBytes();
                        JsonElement root = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
                        int before = out.size();
                        parseRoot(root, out, stats, "bundled:" + name);
                        if (out.size() > before) stats.filesOk++;
                        stats.usedBundled = true;
                    } catch (Throwable t) {
                        stats.failedFiles++;
                        plugin.getLogger().warning("[PokeDemo] Failed to import bundled Cobblemon spawn json " + name + ": " + t.getMessage());
                    }
                }
            }
        } catch (Throwable t) {
            stats.notes.add("bundled-read-failed:" + t.getClass().getSimpleName());
            plugin.getLogger().warning("[PokeDemo] Failed to read bundled Cobblemon spawn resource: " + t.getMessage());
        }
    }

    private void loadFromFolder(File dir, List<SpawnTable.Entry> out, ImportStats stats) {
        List<File> files = new ArrayList<>();
        collectJsonFiles(dir, files);
        for (File f : files) {
            try (FileReader fr = new FileReader(f)) {
                JsonElement root = JsonParser.parseReader(fr);
                int before = out.size();
                parseRoot(root, out, stats, f.getName());
                if (out.size() > before) stats.filesOk++;
                stats.usedFolder = true;
            } catch (Throwable t) {
                stats.failedFiles++;
                plugin.getLogger().warning("[PokeDemo] Failed to import Cobblemon spawn json " + f.getName() + ": " + t.getMessage());
            }
        }
    }

    private void loadFromJar(File jarFile, List<SpawnTable.Entry> out, ImportStats stats) {
        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> en = jar.entries();
            while (en.hasMoreElements()) {
                JarEntry entry = en.nextElement();
                String name = entry.getName();
                if (entry.isDirectory()) continue;
                if (!name.startsWith("data/cobblemon/spawn_pool_world/") || !name.endsWith(".json")) continue;
                try (InputStreamReader reader = new InputStreamReader(jar.getInputStream(entry), StandardCharsets.UTF_8)) {
                    JsonElement root = JsonParser.parseReader(reader);
                    int before = out.size();
                    parseRoot(root, out, stats, name);
                    if (out.size() > before) stats.filesOk++;
                    stats.usedJar = true;
                } catch (Throwable t) {
                    stats.failedFiles++;
                    plugin.getLogger().warning("[PokeDemo] Failed to import Cobblemon spawn json from jar " + name + ": " + t.getMessage());
                }
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("[PokeDemo] Failed to read Cobblemon jar for spawn import: " + jarFile.getName() + " -> " + t.getMessage());
        }
    }

    private File findCobblemonJar() {
        String configured = plugin.getConfig().getString("spawns.cobblemon-import.jar", "");
        if (configured != null && !configured.isBlank()) {
            File f = new File(configured);
            if (!f.isAbsolute()) f = new File(plugin.getDataFolder(), configured);
            if (f.isFile()) return f;
        }
        List<File> searchRoots = new ArrayList<>();
        searchRoots.add(plugin.getDataFolder());
        File parent = plugin.getDataFolder().getParentFile();
        if (parent != null) searchRoots.add(parent);
        File cwd = new File(".").getAbsoluteFile();
        if (cwd != null) searchRoots.add(cwd);
        for (File root : searchRoots) {
            File found = findCobblemonJarRecursive(root, 2);
            if (found != null) return found;
        }
        return null;
    }

    private File findCobblemonJarRecursive(File root, int depth) {
        if (root == null || depth < 0 || !root.exists()) return null;
        File[] files = root.listFiles();
        if (files == null) return null;
        for (File f : files) {
            String n = f.getName().toLowerCase(Locale.ROOT);
            if (f.isFile() && n.endsWith(".jar") && n.contains("cobblemon")) return f;
        }
        if (depth == 0) return null;
        for (File f : files) {
            if (!f.isDirectory()) continue;
            String n = f.getName().toLowerCase(Locale.ROOT);
            if (n.equals("cache") || n.equals("logs") || n.equals("libraries") || n.equals("versions") || n.startsWith(".")) continue;
            File found = findCobblemonJarRecursive(f, depth - 1);
            if (found != null) return found;
        }
        return null;
    }

    private String explainNoImportSource(boolean preferBundled, boolean allowExternalJar) {
        String folderName = plugin.getConfig().getString("spawns.cobblemon-import-folder", "cobblemon_spawn_pool_world");
        File dir = new File(plugin.getDataFolder(), folderName);
        boolean bundledExists;
        try (var in = plugin.getResource("default_data/cobblemon_spawn_pool_world_bundle.zip")) {
            bundledExists = in != null;
        } catch (Throwable t) {
            bundledExists = false;
        }
        if (preferBundled && !bundledExists) return "Bundled Cobblemon spawn resource is missing from the plugin jar.";
        if (dir.exists()) return "Folder scanned=" + dir.getPath();
        if (allowExternalJar) {
            File jar = findCobblemonJar();
            if (jar != null) return "External jar scanned=" + jar.getPath();
            return "Bundled source=" + bundledExists + ", folder missing, external jar not found.";
        }
        return "Bundled source=" + bundledExists + ", folder missing, external jar disabled.";
    }

    private void collectJsonFiles(File dir, List<File> out) {
        File[] list = dir.listFiles();
        if (list == null) return;
        for (File f : list) {
            if (f.isDirectory()) collectJsonFiles(f, out);
            else if (f.getName().toLowerCase(Locale.ROOT).endsWith(".json")) out.add(f);
        }
    }

    private void parseRoot(JsonElement root, List<SpawnTable.Entry> out, ImportStats stats, String sourceName) {
        if (root == null || root.isJsonNull()) return;
        if (root.isJsonArray()) {
            for (JsonElement e : root.getAsJsonArray()) parseRoot(e, out, stats, sourceName);
            return;
        }
        if (!root.isJsonObject()) return;
        JsonObject obj = root.getAsJsonObject();

        for (String key : List.of("entries", "spawns", "spawnEntries", "pool", "pokemon")) {
            if (obj.has(key) && obj.get(key).isJsonArray()) {
                for (JsonElement e : obj.getAsJsonArray(key)) parseRoot(e, out, stats, sourceName);
                return;
            }
        }

        SpawnTable.Entry entry = parseEntry(obj, stats, sourceName);
        if (entry != null) out.add(entry);
    }

    private SpawnTable.Entry parseEntry(JsonObject obj, ImportStats stats, String sourceName) {
        String type = firstString(obj, "type");
        if (type != null && !type.isBlank()) {
            String nt = type.trim().toLowerCase(Locale.ROOT);
            if (!"pokemon".equals(nt) && !"pokemon-herd".equals(nt)) {
                stats.skip("type:" + nt);
                return null;
            }
        }

        String speciesRaw = firstString(obj, "species", "pokemon", "name", "id", "pokemon_id");
        boolean eFallbackSpecies = false;
        if (speciesRaw == null || speciesRaw.isBlank()) {
            stats.skip("missing-species");
            return null;
        }
        Species resolved = resolveImportedSpecies(speciesRaw, stats);
        if (resolved != null && !resolved.id().equalsIgnoreCase(speciesRaw)) {
            eFallbackSpecies = true;
        }
        if (resolved == null) {
            stats.skip("unknown-species");
            return null;
        }

        SpawnTable.Entry e = new SpawnTable.Entry();
        e.species = resolved.id();
        e.importSource = "cobblemon";
        e.importSourceId = sourceName;
        e.importConfidence = "HIGH";
        e.bucket = normalizeBucket(firstString(obj, "bucket", "rarity", "spawn_bucket"));
        e.weight = Math.max(1, firstInt(obj, 1, "weight", "spawnWeight"));
        e.position = normalizePosition(firstString(obj, "spawnablePositionType", "positionType", "position"));
        boolean isFishing = "fishing".equalsIgnoreCase(e.position);
        if (isFishing) e.fishingOnly = true;
        boolean includeFishing = plugin.getConfig().getBoolean("spawns.cobblemon-import.include-fishing", false);
        boolean degradeFishing = plugin.getConfig().getBoolean("spawns.cobblemon-import.degrade-fishing-to-water", true);
        if (isFishing && !includeFishing && !degradeFishing) {
            stats.skip("fishing");
            return null;
        }

        JsonObject condition = firstObject(obj, "condition", "spawnCondition", "requirements");
        JsonObject anti = firstObject(obj, "anticondition", "antiCondition", "anti_condition", "denied", "forbidden");
        collectOriginalMetadata(obj, condition, anti, e);
        if (eFallbackSpecies) e.fallbackReason = appendReason(e.fallbackReason, "species-fallback");
        String skipReason = findUnsupportedReason(condition, anti, obj, isFishing && degradeFishing);
        if (skipReason != null) {
            stats.skip(skipReason);
            return null;
        }

        parseLevel(obj, e);
        applyConditions(condition, e, false);
        applyConditions(anti, e, true);
        if (isFishing) parseFishingSpecifics(condition, e);
        if (isFishing && degradeFishing) {
            applyFishingFallback(e, stats);
            e.translatedConditions.add("fishing->surface-fallback");
            e.fallbackReason = appendReason(e.fallbackReason, "fishing-fallback");
        }
        parseWeightModifiers(obj, e);
        inferSpecialCases(obj, e);
        applyPresetWeightBalancing(obj, e);
        parseHerdFields(obj, e);
        assignImportConfidence(e);
        stats.countConfidence(e.importConfidence);
        return e;
    }

    private void parseLevel(JsonObject obj, SpawnTable.Entry e) {
        JsonElement levelEl = firstElement(obj, "level", "levels", "levelRange");
        if (levelEl != null && levelEl.isJsonObject()) {
            JsonObject level = levelEl.getAsJsonObject();
            e.minLevel = firstInt(level, e.minLevel, "min", "minimum", "low");
            e.maxLevel = firstInt(level, e.maxLevel, "max", "maximum", "high");
        } else if (levelEl != null && levelEl.isJsonPrimitive()) {
            String s = str(levelEl);
            if (s != null && !s.isBlank()) {
                String[] parts = s.trim().split("-");
                if (parts.length == 2) {
                    try { e.minLevel = Integer.parseInt(parts[0].trim()); } catch (Throwable ignored) {}
                    try { e.maxLevel = Integer.parseInt(parts[1].trim()); } catch (Throwable ignored) {}
                } else {
                    try {
                        int v = Integer.parseInt(s.trim());
                        e.minLevel = v;
                        e.maxLevel = v;
                    } catch (Throwable ignored) {}
                }
            }
        } else {
            e.minLevel = firstInt(obj, e.minLevel, "minLevel");
            e.maxLevel = firstInt(obj, e.maxLevel, "maxLevel");
        }
        if (e.maxLevel < e.minLevel) e.maxLevel = e.minLevel;
    }

    private void applyConditions(JsonObject cond, SpawnTable.Entry e, boolean anti) {
        if (cond == null) return;

        addAllStrings(cond, anti ? e.antiBiomeTags : e.biomeTags, "biomes", "biomeTags", "biome_tags");
        addAllStrings(cond, anti ? e.antiBiomeGroups : e.biomeGroups, "biomeGroup", "biomeGroups", "biome_group");
        if (firstArray(cond, "biomes", "biomeTags", "biome_tags") != null) e.translatedConditions.add((anti ? "anti:" : "") + "biome-tags");
        if (firstArray(cond, "biomeGroup", "biomeGroups", "biome_group") != null) e.translatedConditions.add((anti ? "anti:" : "") + "biome-groups");

        JsonArray dims = firstArray(cond, "dimensions", "dimension");
        if (!anti && dims != null) {
            e.translatedConditions.add("dimensions");
            for (JsonElement it : dims) {
                String s = str(it);
                if (s != null) e.dimensions.add(s.trim().toLowerCase(Locale.ROOT));
            }
        }

        if (!anti && cond.has("isSlimeChunk")) {
            try {
                e.requireSlimeChunk = cond.get("isSlimeChunk").getAsBoolean();
                e.translatedConditions.add("slime-chunk");
            } catch (Throwable ignored) {}
        }
        if (!anti && cond.has("moonPhase")) {
            JsonElement mp = cond.get("moonPhase");
            if (mp != null && !mp.isJsonNull()) {
                if (mp.isJsonArray()) {
                    for (JsonElement it : mp.getAsJsonArray()) {
                        String v = str(it);
                        if (v != null && !v.isBlank()) e.moonPhases.add(v.trim().toLowerCase(Locale.ROOT));
                    }
                } else {
                    String v = str(mp);
                    if (v != null && !v.isBlank()) e.moonPhases.add(v.trim().toLowerCase(Locale.ROOT));
                }
                if (!e.moonPhases.isEmpty()) e.translatedConditions.add("moon-phase");
            }
        }

        String tr = firstString(cond, "timeRange", "time", "times");
        if (!anti && tr != null && !tr.isBlank()) { e.time = normalizeTime(tr); e.translatedConditions.add("time"); }
        String weather = firstString(cond, "weather");
        if (!anti && weather != null && !weather.isBlank()) { e.weather = normalizeWeather(weather); e.translatedConditions.add("weather"); }
        if (!anti && cond.has("isThundering") && cond.get("isThundering").getAsBoolean()) {
            e.weather = "thunder";
        } else if (!anti && cond.has("isRaining") && cond.get("isRaining").getAsBoolean()) {
            if (e.weather == null || e.weather.isBlank() || "any".equalsIgnoreCase(e.weather)) e.weather = "rain";
        } else if (!anti && cond.has("isRaining") && cond.has("isThundering")
                && !cond.get("isRaining").getAsBoolean() && !cond.get("isThundering").getAsBoolean()) {
            e.weather = "clear";
        }

        if (!anti && cond.has("canSeeSky")) { e.canSeeSky = cond.get("canSeeSky").getAsBoolean(); e.translatedConditions.add("canSeeSky"); }
        if (!anti) {
            if (cond.has("minSkyLight") || cond.has("maxSkyLight") || cond.has("minLight") || cond.has("maxLight") || cond.has("minBlockLight") || cond.has("maxBlockLight")) e.translatedConditions.add("light");
            if (cond.has("minY") || cond.has("maxY") || cond.has("minHeight") || cond.has("maxHeight")) e.translatedConditions.add("height");
            e.minSkyLight = firstInt(cond, e.minSkyLight, "minSkyLight");
            e.maxSkyLight = firstInt(cond, e.maxSkyLight, "maxSkyLight");
            e.minBlockLight = firstInt(cond, e.minBlockLight, "minLight", "minBlockLight");
            e.maxBlockLight = firstInt(cond, e.maxBlockLight, "maxLight", "maxBlockLight");
            e.minY = firstInt(cond, e.minY, "minY", "minHeight");
            e.maxY = firstInt(cond, e.maxY, "maxY", "maxHeight");
        }

        JsonArray blocks = firstArray(cond, "neededNearbyBlocks", "nearBlocks", "nearbyBlocks", "neededBaseBlocks", "baseBlocks");
        if (!anti && blocks != null) {
            e.translatedConditions.add("nearby-blocks");
            for (JsonElement it : blocks) {
                String s = str(it);
                if (s != null) e.nearBlocks.add(BiomeTagService.normalize(s));
            }
        }

        JsonArray structures = firstArray(cond, "structures", "structure");
        if (structures != null && !structures.isEmpty()) {
            e.translatedConditions.add((anti ? "anti:" : "") + "structure");
            String s = str(structures.get(0));
            if (s != null && !s.isBlank()) {
                e.nearStructure = s;
                e.nearStructureNegate = anti;
                String rs = EnvironmentAliasService.resolveStructure(s);
                if (rs != null) {
                    String u = rs.trim().toUpperCase(java.util.Locale.ROOT);
                    if (u.contains("SHIPWRECK") || u.contains("OCEAN") || u.contains("MONUMENT")) {
                        e.nearStructureRadius = Math.max(e.nearStructureRadius, 160);
                    } else if (u.contains("VILLAGE")) {
                        e.nearStructureRadius = Math.max(e.nearStructureRadius, 96);
                    }
                }
            }
        }
    }


    private void parseFishingSpecifics(JsonObject condition, SpawnTable.Entry e) {
        if (condition == null || e == null) return;
        int minLure = firstInt(condition, 0, "minLureLevel");
        int maxLure = firstInt(condition, Integer.MAX_VALUE, "maxLureLevel");
        e.fishingMinLure = Math.max(0, minLure);
        e.fishingMaxLure = Math.max(e.fishingMinLure, maxLure);
        addAllStrings(condition, e.fishingRodTypes, "rodType", "rodTypes");
        addAllStrings(condition, e.fishingBaits, "bait", "baits");
        if (e.fishingMinLure > 0) e.translatedConditions.add("minLureLevel:" + e.fishingMinLure);
        if (e.fishingMaxLure < Integer.MAX_VALUE) e.translatedConditions.add("maxLureLevel:" + e.fishingMaxLure);
        if (!e.fishingRodTypes.isEmpty()) e.translatedConditions.add("rodType:" + String.join("|", e.fishingRodTypes));
        if (!e.fishingBaits.isEmpty()) e.translatedConditions.add("bait:" + String.join("|", e.fishingBaits));
    }

    private void parseWeightModifiers(JsonObject obj, SpawnTable.Entry e) {
        JsonElement wm = obj.get("weightMultiplier");
        if ((wm == null || wm.isJsonNull()) && obj.has("weightMultipliers")) wm = obj.get("weightMultipliers");
        if (wm == null || wm.isJsonNull()) return;
        if (wm.isJsonPrimitive()) {
            double mul = wm.getAsDouble();
            if (mul != 1.0) {
                SpawnTable.WeightModifier m = new SpawnTable.WeightModifier();
                m.multiply = mul;
                e.weightModifiers.add(m);
            }
            return;
        }
        if (wm.isJsonObject()) {
            SpawnTable.WeightModifier m = parseWeightModifierObject(wm.getAsJsonObject());
            if (m != null) e.weightModifiers.add(m);
            return;
        }
        if (wm.isJsonArray()) {
            for (JsonElement el : wm.getAsJsonArray()) {
                if (!el.isJsonObject()) continue;
                SpawnTable.WeightModifier m = parseWeightModifierObject(el.getAsJsonObject());
                if (m != null) e.weightModifiers.add(m);
            }
        }
    }

    private SpawnTable.WeightModifier parseWeightModifierObject(JsonObject obj) {
        if (obj == null) return null;
        SpawnTable.WeightModifier m = new SpawnTable.WeightModifier();
        m.multiply = firstDouble(obj, 1.0, "multiply", "multiplier", "value", "weight");
        JsonObject when = firstObject(obj, "when", "condition", "if");
        JsonObject src = when != null ? when : obj;
        String time = firstString(src, "timeRange", "time");
        if (time != null) m.time = normalizeTime(time);
        String weather = firstString(src, "weather");
        if (weather != null) m.weather = normalizeWeather(weather);
        if (src.has("isThundering")) m.thundering = src.get("isThundering").getAsBoolean();
        if (src.has("thundering")) m.thundering = src.get("thundering").getAsBoolean();
        if (src.has("isRaining")) m.raining = src.get("isRaining").getAsBoolean();
        if (src.has("raining")) m.raining = src.get("raining").getAsBoolean();
        if (src.has("canSeeSky")) m.canSeeSky = src.get("canSeeSky").getAsBoolean();
        String pos = firstString(src, "spawnablePositionType", "positionType", "position");
        if (pos != null) m.positionType = normalizePosition(pos);
        addAllStrings(src, m.biomeTags, "biomes", "biomeTags", "biome_tags");
        if (m.multiply == 1.0 && "any".equalsIgnoreCase(m.time) && "any".equalsIgnoreCase(m.weather)
                && m.thundering == null && m.raining == null && m.canSeeSky == null
                && (m.positionType == null || m.positionType.isBlank()) && m.biomeTags.isEmpty()) {
            return null;
        }
        return m;
    }


    private void applyFishingFallback(SpawnTable.Entry e, ImportStats stats) {
        e.position = "surface";
        e.nearWater = true;
        if (e.canSeeSky == null) e.canSeeSky = Boolean.TRUE;
        if (e.weight > 1) e.weight = Math.max(1, (int) Math.round(e.weight * 0.6));
        SpawnTable.WeightModifier wm = new SpawnTable.WeightModifier();
        wm.positionType = "surface";
        wm.multiply = 1.35;
        e.weightModifiers.add(wm);
        stats.degradedFishingFallbacks++;
    }

    private void inferSpecialCases(JsonObject obj, SpawnTable.Entry e) {
        JsonArray presets = firstArray(obj, "presets", "preset", "tags");
        if (presets != null) {
            for (JsonElement it : presets) {
                String s = str(it);
                if (s == null) continue;
                String n = s.trim().toLowerCase(Locale.ROOT);
                if (n.contains("water") || n.contains("river") || n.contains("shore")) e.nearWater = true;
                if (n.contains("urban") || n.contains("village") || n.contains("town") || n.contains("pokemon_center") || n.contains("pokecenter")) {
                    e.presetConstraints.add("urban");
                    if (e.nearStructure == null || e.nearStructure.isBlank()) e.nearStructure = "village";
                }
                if (n.contains("spooky")) e.presetConstraints.add("spooky");
                if (n.contains("redstone")) e.presetConstraints.add("redstone");
                if (n.contains("webs")) e.presetConstraints.add("webs");
                if (n.contains("lava")) e.presetConstraints.add("lava");
                if (n.contains("illager_structures")) e.presetConstraints.add("illager_structures");
                if (n.contains("nether_structures")) e.presetConstraints.add("nether_structures");
                if (n.contains("derelict")) e.presetConstraints.add("derelict");
                if (n.contains("ocean_ruins") || n.contains("ocean_ruin")) e.presetConstraints.add("ocean_ruins");
                if (n.contains("mansion") && (e.nearStructure == null || e.nearStructure.isBlank())) e.nearStructure = "woodland_mansion";
                if (n.contains("trail_ruins") && (e.nearStructure == null || e.nearStructure.isBlank())) e.nearStructure = "trail_ruins";
                if (n.contains("desert_pyramid") && (e.nearStructure == null || e.nearStructure.isBlank())) e.nearStructure = "desert_pyramid";
                if (n.contains("jungle_pyramid") && (e.nearStructure == null || e.nearStructure.isBlank())) e.nearStructure = "jungle_temple";
                if (n.contains("ancient_city") && (e.nearStructure == null || e.nearStructure.isBlank())) e.nearStructure = "ancient_city";
                if (n.contains("ruined_portal") && (e.nearStructure == null || e.nearStructure.isBlank())) e.nearStructure = "ruined_portal";
                if (n.contains("stronghold") && (e.nearStructure == null || e.nearStructure.isBlank())) e.nearStructure = "stronghold";
                if (n.contains("monument") && (e.nearStructure == null || e.nearStructure.isBlank())) e.nearStructure = "ocean_monument";
                if (n.contains("end_city") && (e.nearStructure == null || e.nearStructure.isBlank())) e.nearStructure = "end_city";
            }
        }
        if ("cave".equalsIgnoreCase(e.normalizedPosition())) {
            e.maxSkyLight = Math.min(e.maxSkyLight, 1);
            if (e.canSeeSky == null) e.canSeeSky = Boolean.FALSE;
        }
        if ("surface".equalsIgnoreCase(e.normalizedPosition()) || "submerged".equalsIgnoreCase(e.normalizedPosition()) || "seafloor".equalsIgnoreCase(e.normalizedPosition())) {
            e.nearWater = true;
        }
    }

    private String findUnsupportedReason(JsonObject condition, JsonObject anti, JsonObject obj, boolean degradingFishing) {
        if (!plugin.getConfig().getBoolean("spawns.cobblemon-import.skip-unsupported", true)) return null;
        Set<String> hardUnsupported = degradingFishing
                ? Set.of("appendages", "markers")
                : Set.of("minLureLevel", "maxLureLevel", "rodType", "bait", "appendages", "markers");
        if (containsAnyKey(condition, hardUnsupported) || containsAnyKey(anti, hardUnsupported)) return "unsupported-condition";
        JsonArray presets = firstArray(obj, "presets", "preset", "tags");
        if (presets != null) {
            for (JsonElement it : presets) {
                String s = str(it);
                if (s == null) continue;
                String n = s.trim().toLowerCase(Locale.ROOT);
                if (!plugin.getConfig().getBoolean("spawns.cobblemon-import.include-structure-presets", false)) {
                    if (isHardStructurePreset(n)) return "structure-preset:" + n;
                }
            }
        }
        return null;
    }

    private boolean isHardStructurePreset(String n) {
        return false;
    }

    private boolean containsAnyKey(JsonObject obj, Set<String> keys) {
        if (obj == null || obj.size() == 0) return false;
        for (String k : keys) if (obj.has(k)) return true;
        return false;
    }

    private JsonElement firstElement(JsonObject obj, String... keys) {
        if (obj == null) return null;
        for (String key : keys) {
            if (obj.has(key)) return obj.get(key);
        }
        return null;
    }

    private static final class ImportStats {
        int filesOk = 0;
        int failedFiles = 0;
        int skippedEntries = 0;
        int mappedSpeciesFallbacks = 0;
        int degradedFishingFallbacks = 0;
        int confidenceHigh = 0;
        int confidenceMedium = 0;
        int confidenceLow = 0;
        int confidenceUnsafe = 0;
        int confidenceUnknown = 0;
        boolean usedBundled = false;
        boolean usedJar = false;
        boolean usedFolder = false;
        List<String> notes = new ArrayList<>();
        Map<String, Integer> skipReasons = new LinkedHashMap<>();

        void skip(String reason) {
            skippedEntries++;
            String key = (reason == null || reason.isBlank()) ? "unknown" : reason;
            skipReasons.put(key, skipReasons.getOrDefault(key, 0) + 1);
        }

        String skipSummary() {
            if (skipReasons.isEmpty()) return "none";
            List<Map.Entry<String, Integer>> list = new ArrayList<>(skipReasons.entrySet());
            list.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
            StringBuilder sb = new StringBuilder();
            int lim = Math.min(6, list.size());
            for (int i = 0; i < lim; i++) {
                if (i > 0) sb.append(',');
                sb.append(list.get(i).getKey()).append('=').append(list.get(i).getValue());
            }
            return sb.toString();
        }


        void countConfidence(String confidence) {
            String c = confidence == null ? "UNKNOWN" : confidence.trim().toUpperCase(Locale.ROOT);
            switch (c) {
                case "HIGH" -> confidenceHigh++;
                case "MEDIUM" -> confidenceMedium++;
                case "LOW" -> confidenceLow++;
                case "UNSAFE" -> confidenceUnsafe++;
                default -> confidenceUnknown++;
            }
        }

        String confidenceSummary() {
            return "HIGH=" + confidenceHigh + ",MEDIUM=" + confidenceMedium + ",LOW=" + confidenceLow + ",UNSAFE=" + confidenceUnsafe + ",UNKNOWN=" + confidenceUnknown;
        }
        String sourceSummary() {
            List<String> src = new ArrayList<>();
            if (usedBundled) src.add("bundled");
            if (usedFolder) src.add("folder");
            if (usedJar) src.add("external-jar");
            if (src.isEmpty()) src.add("none");
            if (!notes.isEmpty()) src.add("notes=" + String.join(",", notes));
            return String.join("+", src);
        }
    }


    private Species resolveImportedSpecies(String speciesRaw, ImportStats stats) {
        Species resolved = dex.getSpeciesFlexible(speciesRaw);
        if (resolved != null) return resolved;

        String simplified = simplifyImportedSpeciesId(speciesRaw);
        if (!simplified.equalsIgnoreCase(speciesRaw)) {
            resolved = dex.getSpeciesFlexible(simplified);
            if (resolved != null) {
                stats.mappedSpeciesFallbacks++;
                return resolved;
            }
        }

        int sp = simplified.indexOf(' ');
        if (sp > 0) {
            String head = simplified.substring(0, sp).trim();
            resolved = dex.getSpeciesFlexible(head);
            if (resolved != null) {
                stats.mappedSpeciesFallbacks++;
                return resolved;
            }
        }
        return null;
    }

    private String simplifyImportedSpeciesId(String raw) {
        if (raw == null) return "";
        String s = raw.trim().toLowerCase(Locale.ROOT);
        if (s.contains(":")) s = s.substring(s.indexOf(':') + 1).trim();
        s = s.replace('-', '_').replace('.', '_');
        s = s.replaceAll("\s+", " ").trim();
        if (s.isEmpty()) return s;

        String[] parts = s.split(" ");
        if (parts.length <= 1) return s;
        java.util.List<String> kept = new java.util.ArrayList<>();
        kept.add(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            String part = parts[i].trim();
            if (part.isEmpty()) continue;
            if (part.contains("=")) continue;
            if (isVariantDescriptor(part)) continue;
            kept.add(part);
        }
        if (kept.isEmpty()) return s;
        return String.join(" ", kept).trim();
    }

    private boolean isVariantDescriptor(String token) {
        String t = token == null ? "" : token.trim().toLowerCase(Locale.ROOT);
        if (t.isEmpty()) return false;
        return t.equals("alolan") || t.equals("galarian") || t.equals("hisuian") || t.equals("paldean")
                || t.equals("region_bias=hisui") || t.equals("region_bias=galar") || t.equals("region_bias=paldea")
                || t.equals("region_bias=alola") || t.equals("striped") || t.equals("sea")
                || t.equals("character") || t.equals("curly") || t.equals("droopy") || t.equals("stretchy")
                || t.equals("family") || t.equals("male") || t.equals("female") || t.equals("ordinary")
                || t.equals("average") || t.equals("incarnate") || t.equals("therian");
    }



    private void parseHerdFields(JsonObject obj, SpawnTable.Entry e) {
        if (obj == null || e == null) return;
        String type = firstString(obj, "type");
        JsonObject herdObj = firstObject(obj, "herd", "group", "groupData");
        int maxHerd = firstInt(obj, 0, "maxHerdSize", "herdSize", "groupSize");
        int minDist = firstInt(obj, 0, "minDistanceBetweenSpawns", "minDistance");
        if (herdObj != null) {
            maxHerd = Math.max(maxHerd, firstInt(herdObj, maxHerd, "maxHerdSize", "groupSize", "size"));
            minDist = Math.max(minDist, firstInt(herdObj, minDist, "minDistanceBetweenSpawns", "minDistance"));
        }
        if ((type != null && type.equalsIgnoreCase("pokemon-herd")) || herdObj != null || maxHerd > 1) {
            e.herd = true;
            e.herdMin = Math.max(2, Math.min(4, maxHerd > 0 ? Math.max(2, maxHerd - 1) : 2));
            e.herdMax = Math.max(e.herdMin, Math.min(6, maxHerd > 0 ? maxHerd : 3));
            e.herdRadius = Math.max(4, Math.min(12, e.herdMax + 3));
            e.herdMinDistance = Math.max(1, Math.min(4, minDist > 0 ? minDist : 2));
            e.translatedConditions.add("herd");
        }
    }

    private void applyPresetWeightBalancing(JsonObject obj, SpawnTable.Entry e) {
        if (obj == null || e == null) return;
        if (e.weight <= 0) return;
        Set<String> presets = new LinkedHashSet<>();
        addAllStrings(obj, presets, "presets", "preset", "tags");
        if (presets.isEmpty()) return;

        boolean hasExactStructure = e.nearStructure != null && !e.nearStructure.isBlank() && !e.nearStructureNegate;
        boolean hasUrbanLike = presets.contains("urban") || presets.contains("village") || presets.contains("town")
                || presets.contains("pokemon_center") || presets.contains("pokecenter");
        boolean hasStructureLike = presets.contains("pillager_outpost") || presets.contains("mansion")
                || presets.contains("ocean_ruins") || presets.contains("ocean_ruin")
                || presets.contains("trail_ruins") || presets.contains("desert_pyramid")
                || presets.contains("jungle_pyramid") || presets.contains("ancient_city")
                || presets.contains("ruined_portal") || presets.contains("stronghold")
                || presets.contains("monument") || presets.contains("end_city");

        double mult = 1.0D;
        if (hasUrbanLike) {
            mult *= hasExactStructure ? 0.70D : 0.15D;
        }
        if (hasStructureLike) {
            mult *= hasExactStructure ? 0.75D : 0.08D;
        }
        if (e.weight >= 40) {
            mult *= hasExactStructure ? 0.55D : 0.35D;
        }
        if (presets.contains("ocean_ruins") || presets.contains("ocean_ruin")) {
            boolean onlyOceanApprox = !hasExactStructure && (e.biomeTags.contains("#cobblemon:is_ocean") || e.biomeTags.contains("ocean") || e.biomeGroups.contains("ocean"));
            if (onlyOceanApprox) mult *= 0.35D;
        }
        if (mult >= 0.999D) return;
        int adjusted = (int) Math.round(e.weight * mult);
        e.weight = Math.max(1, adjusted);
    }

    private void collectOriginalMetadata(JsonObject obj, JsonObject condition, JsonObject anti, SpawnTable.Entry e) {
        if (obj == null || e == null) return;
        JsonArray presets = firstArray(obj, "presets", "preset", "tags");
        if (presets != null) {
            for (JsonElement it : presets) {
                String v = str(it);
                if (v != null && !v.isBlank()) e.originalPresets.add(v.trim().toLowerCase(Locale.ROOT));
            }
        }
        captureOriginalTags(condition, e.originalBiomeTags, "biomes", "biomeTags", "biome_tags");
        captureOriginalTags(anti, e.originalAntiBiomeTags, "biomes", "biomeTags", "biome_tags");
        JsonArray dims = firstArray(condition, "dimensions", "dimension");
        if (dims != null) {
            for (JsonElement it : dims) {
                String v = str(it);
                if (v != null && !v.isBlank()) e.originalDimensions.add(v.trim().toLowerCase(Locale.ROOT));
            }
        }
        collectIgnoredConditionHints(condition, e, false);
        collectIgnoredConditionHints(anti, e, true);
    }

    private void captureOriginalTags(JsonObject obj, Set<String> out, String... keys) {
        JsonArray arr = firstArray(obj, keys);
        if (arr == null) return;
        for (JsonElement it : arr) {
            String v = str(it);
            if (v != null && !v.isBlank()) out.add(v.trim().toLowerCase(Locale.ROOT));
        }
    }

    private void collectIgnoredConditionHints(JsonObject obj, SpawnTable.Entry e, boolean anti) {
        if (obj == null || e == null) return;
        for (String k : List.of("minX", "maxX", "minZ", "maxZ", "appendages", "markers", "minLureLevel", "maxLureLevel", "rodType", "bait")) {
            if (obj.has(k)) e.ignoredConditions.add((anti ? "anti:" : "") + k);
        }
    }

    private String appendReason(String current, String extra) {
        if (extra == null || extra.isBlank()) return current;
        if (current == null || current.isBlank()) return extra;
        if (current.contains(extra)) return current;
        return current + "," + extra;
    }

    private void assignImportConfidence(SpawnTable.Entry e) {
        if (e == null) return;
        int ignored = e.ignoredConditions.size();
        boolean unhandledPreset = false;
        for (String p : e.originalPresets) {
            if (!isRecognizedPresetForConfidence(p)) {
                unhandledPreset = true;
                e.ignoredConditions.add("preset:" + p);
            }
        }
        boolean hasFallback = e.fallbackReason != null && !e.fallbackReason.isBlank();
        boolean highRiskWeight = e.weight >= 40;
        if ((ignored >= 3 && highRiskWeight) || (unhandledPreset && highRiskWeight) || containsHardIgnored(e.ignoredConditions) || (e.weight >= 60 && e.presetConstraints.isEmpty() && !e.originalPresets.isEmpty())) {
            e.importConfidence = "UNSAFE";
        } else if (ignored >= 2 || unhandledPreset || hasFallback) {
            e.importConfidence = "LOW";
        } else if (ignored == 1 || !e.translatedConditions.isEmpty() || !e.originalPresets.isEmpty()) {
            e.importConfidence = "MEDIUM";
        } else {
            e.importConfidence = "HIGH";
        }
    }

    private boolean containsHardIgnored(Set<String> ignored) {
        for (String s : ignored) {
            if (s.contains("isSlimeChunk") || s.contains("moonPhase") || s.contains("markers") || s.contains("appendages")) return true;
        }
        return false;
    }

    private boolean isRecognizedPresetForConfidence(String preset) {
        if (preset == null || preset.isBlank()) return false;
        String n = preset.trim().toLowerCase(Locale.ROOT);
        return n.contains("water") || n.contains("river") || n.contains("shore") || n.contains("urban")
                || n.contains("village") || n.contains("town") || n.contains("pokemon_center") || n.contains("pokecenter")
                || n.contains("spooky") || n.contains("mansion") || n.contains("trail_ruins")
                || n.contains("desert_pyramid") || n.contains("jungle_pyramid") || n.contains("ancient_city")
                || n.contains("ruined_portal") || n.contains("stronghold") || n.contains("monument")
                || n.contains("end_city") || n.contains("pillager_outpost") || n.contains("ocean_ruins")
                || n.contains("ocean_ruin") || n.contains("redstone") || n.contains("webs") || n.contains("lava")
                || n.contains("illager_structures") || n.contains("nether_structures") || n.contains("derelict");
    }

    private String normalizeBucket(String raw) {
        if (raw == null || raw.isBlank()) return "common";
        String s = raw.trim().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        if (s.equals("ultrarare")) return "ultra_rare";
        return s;
    }

    private String normalizePosition(String raw) {
        if (raw == null || raw.isBlank()) return "grounded";
        String s = raw.trim().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        if (s.equals("land")) return "grounded";
        if (s.equals("water")) return "submerged";
        if (s.equals("water_surface")) return "surface";
        return s;
    }

    private String normalizeTime(String raw) {
        String s = raw == null ? "any" : raw.trim().toLowerCase(Locale.ROOT);
        if (s.contains("day")) return "day";
        if (s.contains("night")) return "night";
        if (s.contains("dawn") || s.contains("sunrise")) return "dawn";
        if (s.contains("dusk") || s.contains("sunset")) return "dusk";
        return "any";
    }

    private String normalizeWeather(String raw) {
        String s = raw == null ? "any" : raw.trim().toLowerCase(Locale.ROOT);
        if (s.contains("thunder")) return "thunder";
        if (s.contains("rain")) return "rain";
        if (s.contains("clear") || s.contains("none")) return "clear";
        return "any";
    }

    private void addAllStrings(JsonObject src, Set<String> out, String... keys) {
        for (String key : keys) {
            JsonArray arr = firstArray(src, key);
            if (arr == null) continue;
            for (JsonElement it : arr) {
                String s = str(it);
                if (s == null || s.isBlank()) continue;
                String n = normalizeToken(s);
                if (!n.isBlank()) out.add(n);
            }
        }
    }

    private String normalizeToken(String s) {
        String n = s.trim().toLowerCase(Locale.ROOT);
        if (n.isBlank()) return n;
        if (n.startsWith("#")) return BiomeTagService.normalize(n);
        return n;
    }

    private JsonObject firstObject(JsonObject obj, String... keys) {
        if (obj == null) return null;
        for (String k : keys) {
            if (obj.has(k) && obj.get(k).isJsonObject()) return obj.getAsJsonObject(k);
        }
        return null;
    }

    private JsonArray firstArray(JsonObject obj, String... keys) {
        if (obj == null) return null;
        for (String k : keys) {
            if (obj.has(k) && obj.get(k).isJsonArray()) return obj.getAsJsonArray(k);
        }
        return null;
    }

    private String firstString(JsonObject obj, String... keys) {
        if (obj == null) return null;
        for (String k : keys) {
            if (obj.has(k)) {
                String s = str(obj.get(k));
                if (s != null && !s.isBlank()) return s;
            }
        }
        return null;
    }

    private int firstInt(JsonObject obj, int def, String... keys) {
        if (obj == null) return def;
        for (String k : keys) {
            if (obj.has(k)) {
                try { return obj.get(k).getAsInt(); } catch (Throwable ignored) {}
            }
        }
        return def;
    }

    private double firstDouble(JsonObject obj, double def, String... keys) {
        if (obj == null) return def;
        for (String k : keys) {
            if (obj.has(k)) {
                try { return obj.get(k).getAsDouble(); } catch (Throwable ignored) {}
            }
        }
        return def;
    }

    private String str(JsonElement e) {
        if (e == null || e.isJsonNull()) return null;
        try {
            if (e.isJsonPrimitive()) return e.getAsString();
            if (e.isJsonObject()) {
                JsonObject o = e.getAsJsonObject();
                for (String key : List.of("id", "name", "species", "tag", "biome")) {
                    if (o.has(key) && o.get(key).isJsonPrimitive()) return o.get(key).getAsString();
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }
}
