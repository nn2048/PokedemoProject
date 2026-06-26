package win.pokedemo;

import com.google.gson.*;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

public class Dex {
    private final JavaPlugin plugin;
    private final Gson gson = new GsonBuilder().create();
    private final Map<String, Species> speciesById = new HashMap<>();
    /** Alias lookup for species ids across different naming conventions (e.g. mrmime vs mr_mime). */
    private final Map<String, String> speciesAliasToId = new HashMap<>();
    private final Map<String, Move> movesById = new HashMap<>();

    /** childId -> parentId mapping built from evolutions lists (used to resolve baby species for breeding). */
    private final Map<String, String> prevoMap = new HashMap<>();

    /**
     * Showdown pokedex abilities map (speciesId -> list of ability ids).
     *
     * <p>Loaded from plugins/PokeDemo/moves_raw/pokedex.json if present.
     * We keep it lightweight: only the ability ids, and selection is random.
     */
    // Ability pools loaded from Pokemon Showdown pokedex.json (moves_raw).
    // Normal abilities use keys like "0", "1"; hidden ability uses key "H".
    private final java.util.Map<String, java.util.List<String>> normalAbilitiesBySpecies = new java.util.HashMap<>();
    private final java.util.Map<String, String> hiddenAbilityBySpecies = new java.util.HashMap<>();

    // Gen1 learnset import report (only created when auto-detected Gen1 import is used)
    private MoveLearnsetReport lastGen1LearnsetReport = null;
    // Moves raw import report (only created when moves_raw import is used)
    private MoveRawImportReport lastMovesRawImportReport = null;

    // Whether we detected Cobblemon Gen1-only species import (species_raw/.../generation1)
    // If true, we force Gen1 constraints for Showdown moves/learnsets unless explicitly overridden later.
    private boolean autoGen1SpeciesDetected = false;

    public Dex(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadAll() {
        try {
            ensureDefaultDataCopied("species");
            ensureDefaultDataCopied("moves");
            speciesById.clear();
            movesById.clear();
            autoGen1SpeciesDetected = false;
            loadSpeciesFolder(plugin.getDataFolder().toPath().resolve("species"));
            // Optional: import Cobblemon species JSON (client mod data) if provided
            loadCobblemonSpeciesFolder(plugin.getDataFolder().toPath().resolve("species_raw"));

            // Overlay breeding metadata shipped in egg_groups.json.
            // Our default species JSONs often omit maleRatio entirely, which would make
            // newly caught Pokémon fall back to genderless/unknown (N) and display as "-".
            // The bundled overlay contains both eggGroups and maleRatio for Gen1 species.
            applyBreedingOverlayFromResource();

            // EV Yield overlay: many Showdown/builtin species sources don't include EV drops.
            // Without this, EV gain will always stay 0 even if the EV system is enabled.
            // We ship a lightweight overlay (Gen1) generated from Cobblemon species JSON.
            applyEvYieldOverlayFromResource();
            // Optional: import moves data (e.g., Pokemon Showdown/Cobblemon moves.json) if provided
            loadMovesRawFolder(plugin.getDataFolder().toPath().resolve("moves_raw"));
            loadPokedexAbilities(plugin.getDataFolder().toPath().resolve("moves_raw").resolve("pokedex.json"));
            loadMovesFolder(plugin.getDataFolder().toPath().resolve("moves"));

            // Some Showdown exports or older raw sources may omit a few classic Gen1 moves.
            // To keep "Gen1 coverage" meaningful and prevent basic battles from breaking,
            // we inject minimal definitions for a tiny whitelist if they are missing.
            ensureMissingGen1MoveStubs();

            // Optional: apply Pokemon Showdown learnsets onto loaded species (in-memory)
            if (plugin.getConfig().getBoolean("showdown.apply-learnsets", false)) {
                applyShowdownLearnsets(plugin.getDataFolder().toPath().resolve("moves_raw"));
            }

            // Build pre-evolution mapping for breeding baby resolution.
            rebuildPrevoMap();

            // Build alias mapping for flexible id resolution (used by spawn tables imported from mods).
            rebuildSpeciesAliasMap();
            if (lastGen1LearnsetReport != null) {
                finalizeAndWriteLearnsetReport(lastGen1LearnsetReport);
                lastGen1LearnsetReport = null;
            }

            if (lastMovesRawImportReport != null) {
                writeMovesRawImportReport(lastMovesRawImportReport);
                lastMovesRawImportReport = null;
            }

        } catch (Exception e) {
            plugin.getLogger().severe("Dex load failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /** Resolve the lowest-stage (baby/base) species id by walking pre-evolution chain. */
    public String getBabySpeciesId(String id) {
        if (id == null) return null;
        String cur = normalizeId(id);
        int guard = 0;
        while (guard++ < 16) {
            String prev = prevoMap.get(cur);
            if (prev == null || prev.isBlank()) break;
            cur = normalizeId(prev);
        }
        return cur;
    }

    private void rebuildPrevoMap() {
        prevoMap.clear();
        for (Species s : speciesById.values()) {
            if (s == null) continue;
            for (Evolution ev : s.evolutionsSafe()) {
                if (ev == null) continue;
                String child = ev.result();
                if (child == null || child.isBlank()) continue;
                child = normalizeId(child);
                prevoMap.putIfAbsent(child, normalizeId(s.id()));
            }
        }
    }

    private void rebuildSpeciesAliasMap() {
        speciesAliasToId.clear();
        for (String id : speciesById.keySet()) {
            if (id == null) continue;
            String norm = normalizeId(id);
            speciesAliasToId.putIfAbsent(norm, norm);
            speciesAliasToId.putIfAbsent(norm.replace('-', '_'), norm);
            speciesAliasToId.putIfAbsent(norm.replace('_', '-'), norm);
            speciesAliasToId.putIfAbsent(stripToAlnum(norm), norm);
        }
        // A few well-known mod naming quirks
        speciesAliasToId.putIfAbsent("mrmime", "mr_mime");
        speciesAliasToId.putIfAbsent("mr.mime", "mr_mime");
        speciesAliasToId.putIfAbsent("farfetchd", "farfetchd");
    }

    private String stripToAlnum(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) sb.append(c);
        }
        return sb.toString();
    }

    private String normalizeId(String raw) {
        String s = raw.toLowerCase(Locale.ROOT);
        if (s.contains(":")) s = s.substring(s.indexOf(':') + 1);
        return s;
    }


    private static String jsonOptString(JsonObject o, String key, String def) {
        if (o == null || key == null || !o.has(key)) return def;
        try {
            JsonElement el = o.get(key);
            if (el == null || el.isJsonNull()) return def;
            if (el.isJsonPrimitive()) return el.getAsString();
        } catch (Exception ignored) {
        }
        return def;
    }

    private static int jsonOptInt(JsonObject o, String key, int def) {
        if (o == null || key == null || !o.has(key)) return def;
        try {
            JsonElement el = o.get(key);
            if (el == null || el.isJsonNull()) return def;
            if (el.isJsonPrimitive()) return el.getAsInt();
        } catch (Exception ignored) {
        }
        return def;
    }


    /**
     * Apply breeding metadata overlay onto loaded species.
     *
     * Why: the bundled/default species JSON is intentionally lightweight and often omits
     * egg groups and especially maleRatio. Without maleRatio, newly created/caught Pokémon
     * default to gender "N" and all UIs show them as genderless.
     */
    private void applyBreedingOverlayFromResource() {
        try (InputStream in = plugin.getResource("bundled_plugin/egg_groups.json")) {
            if (in == null) {
                plugin.getLogger().warning("[Dex] egg_groups.json not found in jar; gender ratios may remain unknown.");
                return;
            }
            JsonObject root = gson.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), JsonObject.class);
            if (root == null || !root.isJsonObject()) return;

            int applied = 0;
            for (Map.Entry<String, JsonElement> e : root.entrySet()) {
                String sid = e.getKey().toLowerCase(Locale.ROOT);
                if (!e.getValue().isJsonObject()) continue;
                Species sp = speciesById.get(sid);
                if (sp == null) continue;

                JsonObject o = e.getValue().getAsJsonObject();

                List<String> eggGroups = sp.eggGroups();
                if (o.has("eggGroups") && o.get("eggGroups").isJsonArray()) {
                    List<String> out = new ArrayList<>();
                    for (JsonElement el : o.getAsJsonArray("eggGroups")) {
                        if (!el.isJsonPrimitive()) continue;
                        String g = el.getAsString();
                        if (g == null) continue;
                        g = g.trim().toLowerCase(Locale.ROOT);
                        if (!g.isEmpty() && !out.contains(g)) out.add(g);
                    }
                    if (!out.isEmpty()) eggGroups = out;
                }

                double maleRatio = sp.maleRatio();
                if (o.has("maleRatio") && o.get("maleRatio").isJsonPrimitive()) {
                    try {
                        maleRatio = o.get("maleRatio").getAsDouble();
                    } catch (Exception ignored) {}
                }

                speciesById.put(sid, new Species(
                        sp.id(), sp.name(), sp.types(), sp.baseStats(), sp.evYields(),
                        sp.evolutions(), sp.levelUpMoves(), sp.baseExpYield(), sp.expGroup(),
                        sp.catchRate(), sp.minLevel(), sp.maxLevel(), sp.weightKg(),
                        eggGroups, maleRatio, sp.baseFriendship()
                ));
                applied++;
            }
            plugin.getLogger().info("[Dex] Breeding overlay applied to " + applied + " species (egg groups / maleRatio).");
        } catch (Exception ex) {
            plugin.getLogger().warning("[Dex] Failed to apply breeding overlay: " + ex.getMessage());
        }
    }

    /**
     * Apply EV-yield overlay onto loaded species.
     *
     * Why: our default/species_raw imports often omit EV yield data.
     * This causes EV gain after defeating Pokémon to always be 0.
     */
    private void applyEvYieldOverlayFromResource() {
        try (InputStream in = plugin.getResource("default_data/ev_yields_gen1.json")) {
            if (in == null) {
                plugin.getLogger().warning("[Dex] ev_yields_gen1.json not found in jar; EV drops may remain 0.");
                return;
            }
            JsonObject root = gson.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), JsonObject.class);
            if (root == null || !root.isJsonObject()) return;

            int applied = 0;
            for (Map.Entry<String, JsonElement> e : root.entrySet()) {
                String sid = e.getKey().toLowerCase(Locale.ROOT);
                if (!e.getValue().isJsonObject()) continue;
                Species sp = speciesById.get(sid);
                if (sp == null) continue;

                // Only overlay when missing or effectively empty (all zeros)
                Map<String, Integer> cur = sp.evYields();
                boolean missing = (cur == null || cur.isEmpty());
                boolean allZero = true;
                if (!missing) {
                    for (int v : cur.values()) {
                        if (v != 0) { allZero = false; break; }
                    }
                }
                if (!(missing || allZero)) continue;

                JsonObject yo = e.getValue().getAsJsonObject();
                Map<String, Integer> overlay = new HashMap<>();
                overlay.put("hp", yo.has("hp") ? yo.get("hp").getAsInt() : 0);
                overlay.put("atk", yo.has("atk") ? yo.get("atk").getAsInt() : 0);
                overlay.put("def", yo.has("def") ? yo.get("def").getAsInt() : 0);
                overlay.put("spa", yo.has("spa") ? yo.get("spa").getAsInt() : 0);
                overlay.put("spd", yo.has("spd") ? yo.get("spd").getAsInt() : 0);
                overlay.put("spe", yo.has("spe") ? yo.get("spe").getAsInt() : 0);

                speciesById.put(sid, new Species(
                        sp.id(), sp.name(), sp.types(), sp.baseStats(), overlay,
                        sp.evolutions(), sp.levelUpMoves(), sp.baseExpYield(), sp.expGroup(),
                        sp.catchRate(), sp.minLevel(), sp.maxLevel(), sp.weightKg(),
                        sp.eggGroups(), sp.maleRatio(), sp.baseFriendship()
                ));
                applied++;
            }
            plugin.getLogger().info("[Dex] EV yield overlay applied to " + applied + " species (Gen1). ");
        } catch (Exception ex) {
            plugin.getLogger().warning("[Dex] Failed to apply EV yield overlay: " + ex.getMessage());
        }
    }

    /**
     * Ensure a few essential Gen1 moves exist even if the raw moves source omitted them.
     * This is a safety-net only; when moves are present from Showdown, they take precedence.
     */
    private void ensureMissingGen1MoveStubs() {
        try {
            // Build quick "seen" set by move num.
            boolean[] seen = new boolean[166];
            for (Move m : movesById.values()) {
                if (m == null) continue;
                int n = m.numSafe();
                if (n >= 1 && n <= 165) seen[n] = true;
            }

            // #33 Tackle

// If present but missing/incorrect num, fix it. Otherwise inject stub.
if (!seen[33]) {
    Move existing = movesById.get("tackle");
    if (existing != null) {
        // In early project snapshots, some stubs used placeholder effect kinds like "damage".
        // Normalize Tackle to be a plain damaging move (no special effects).
        boolean hadLegacyDamageKind = false;
        if (existing.effectsSafe() != null) {
            for (java.util.Map<String, Object> ef : existing.effectsSafe()) {
                if (ef == null) continue;
                String kind = String.valueOf(ef.getOrDefault("kind", ef.getOrDefault("id", ""))).toLowerCase();
                if ("damage".equals(kind)) { hadLegacyDamageKind = true; break; }
            }
        }

        if (existing.numSafe() != 33 || hadLegacyDamageKind) {
            movesById.put("tackle", new Move(
                    existing.id(), existing.name(), existing.type(), existing.category(),
                    existing.power(), existing.accuracy(), existing.pp(), existing.priority(),
                    33,
                    // keep legacy single-effect map only if we didn't detect the legacy placeholder
                    hadLegacyDamageKind ? null : existing.effect(),
                    hadLegacyDamageKind ? java.util.List.of() : existing.effectsSafe()
            ));
            if (existing.numSafe() != 33) plugin.getLogger().info("[Dex] Fixed Gen1 move num for tackle -> #33");
            if (hadLegacyDamageKind) plugin.getLogger().warning("[Dex] Normalized legacy stub effect for tackle (removed placeholder kind 'damage')");
        }
    } else {
        movesById.put("tackle", new Move(
                "tackle", "Tackle", "normal", "physical",
                40, 100.0, 35, 0, 33,
                null, java.util.List.of()
        ));
        plugin.getLogger().warning("[Dex] Injected missing Gen1 move stub: tackle (#33)");
    }
}


            // #45 Growl (lower target attack by 1 stage)

// If present but missing/incorrect num, fix it. Otherwise inject stub.
if (!seen[45]) {
    Move existing = movesById.get("growl");
    if (existing != null) {
        // Normalize legacy "stat" placeholders to our supported stat_stage effect.
        boolean hadLegacyStatKind = false;
        if (existing.effectsSafe() != null) {
            for (java.util.Map<String, Object> ef : existing.effectsSafe()) {
                if (ef == null) continue;
                String kind = String.valueOf(ef.getOrDefault("kind", ef.getOrDefault("id", ""))).toLowerCase();
                if ("stat".equals(kind)) { hadLegacyStatKind = true; break; }
            }
        }

        java.util.List<java.util.Map<String, Object>> newEffects = existing.effectsSafe();
        java.util.Map<String, Object> newEffect = existing.effect();
        if (hadLegacyStatKind) {
            java.util.Map<String, Object> ef = new java.util.LinkedHashMap<>();
            ef.put("id", "stat_stage");
            ef.put("kind", "stat_stage");
            ef.put("stat", "attack");
            ef.put("stages", -1);
            ef.put("target", "target");
            newEffects = java.util.List.of(ef);
            newEffect = null;
        }

        if (existing.numSafe() != 45 || hadLegacyStatKind) {
            movesById.put("growl", new Move(
                    existing.id(), existing.name(), existing.type(), existing.category(),
                    existing.power(), existing.accuracy(), existing.pp(), existing.priority(),
                    45, newEffect, newEffects
            ));
            if (existing.numSafe() != 45) plugin.getLogger().info("[Dex] Fixed Gen1 move num for growl -> #45");
            if (hadLegacyStatKind) plugin.getLogger().warning("[Dex] Normalized legacy stub effect for growl (mapped 'stat' -> 'stat_stage')");
        }
    } else {
        java.util.Map<String, Object> ef = new java.util.LinkedHashMap<>();
        ef.put("id", "stat_stage");
        ef.put("kind", "stat_stage");
        ef.put("stat", "attack");
        ef.put("stages", -1);
        ef.put("target", "target");
        movesById.put("growl", new Move(
                "growl", "Growl", "normal", "status",
                0, 100.0, 40, 0, 45,
                null, java.util.List.of(ef)
        ));
        plugin.getLogger().warning("[Dex] Injected missing Gen1 move stub: growl (#45)");
    }
}


            // #105 Recover (heal 50%)

// If present but missing/incorrect num, fix it. Otherwise inject stub.
if (!seen[105]) {
    Move existing = movesById.get("recover");
    if (existing != null) {
        if (existing.numSafe() != 105) {
            movesById.put("recover", new Move(
                    existing.id(), existing.name(), existing.type(), existing.category(),
                    existing.power(), existing.accuracy(), existing.pp(), existing.priority(),
                    105, existing.effect(), existing.effectsSafe()
            ));
            plugin.getLogger().info("[Dex] Fixed Gen1 move num for recover -> #105");
        }
    } else {
        java.util.Map<String, Object> ef = new java.util.LinkedHashMap<>();
        ef.put("id", "heal");
        ef.put("kind", "heal");
        ef.put("percent", 0.5);
        movesById.put("recover", new Move(
                "recover", "Recover", "normal", "status",
                0, 100.0, 20, 0, 105,
                null, java.util.List.of(ef)
        ));
        plugin.getLogger().warning("[Dex] Injected missing Gen1 move stub: recover (#105)");
    }
}

        } catch (Exception ignored) {
        }
    }

    private void ensureDefaultDataCopied(String type) throws IOException {
        Path folder = plugin.getDataFolder().toPath().resolve(type);
        if (!Files.exists(folder)) Files.createDirectories(folder);

        // If empty, copy built-in samples from jar resources
        try (InputStream index = plugin.getResource(type + "_builtin/index.txt")) {
            if (index == null) {
                // create from known list in jar by scanning isn't possible, so ship index.txt
                return;
            }
            boolean empty = Files.list(folder).findAny().isEmpty();
            if (!empty) return;

            List<String> files = new BufferedReader(new InputStreamReader(index, StandardCharsets.UTF_8))
                    .lines().filter(l -> !l.isBlank()).collect(java.util.stream.Collectors.toList());
            for (String f : files) {
                try (InputStream in = plugin.getResource(type + "_builtin/" + f)) {
                    if (in == null) continue;
                    Files.copy(in, folder.resolve(f), StandardCopyOption.REPLACE_EXISTING);
                }
            }
            plugin.getLogger().info("Copied built-in " + type + " samples to " + folder);
        }
    }

    private void loadSpeciesFolder(Path folder) throws IOException {
        if (!Files.exists(folder)) return;
        List<Path> files = Files.walk(folder).filter(p -> p.toString().endsWith(".json")).collect(java.util.stream.Collectors.toList());
        for (Path p : files) {
            try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
                JsonObject o = gson.fromJson(r, JsonObject.class);
                String id = jsonOptString(o, "id", "").toLowerCase(Locale.ROOT);
                if (id.isBlank()) continue;
                String name = jsonOptString(o, "name", id);
                List<String> types = new ArrayList<>();
                if (o.has("types")) {
                    for (JsonElement e : o.getAsJsonArray("types")) types.add(e.getAsString().toLowerCase());
                }
                Map<String,Integer> baseStats = new HashMap<>();
                if (o.has("baseStats")) {
                    for (Map.Entry<String, JsonElement> en : o.getAsJsonObject("baseStats").entrySet()) {
                        baseStats.put(en.getKey().toLowerCase(), en.getValue().getAsInt());
                    }
                }

                Map<String,Integer> evYields = parseEvYields(o);

                int catchRate = jsonOptInt(o, "catchRate", 45);
                int baseExpYield = jsonOptInt(o, "baseExpYield", 64);
                String expGroup = jsonOptString(o, "expGroup", "MEDIUM_FAST").toUpperCase(Locale.ROOT);
                int minLv = 2, maxLv = 10;
                if (o.has("levelRange")) {
                    JsonArray a = o.getAsJsonArray("levelRange");
                    if (a.size() >= 2) {
                        minLv = a.get(0).getAsInt();
                        maxLv = a.get(1).getAsInt();
                    }
                } else {
                    if (o.has("minLevel")) minLv = o.get("minLevel").getAsInt();
                    if (o.has("maxLevel")) maxLv = o.get("maxLevel").getAsInt();
                }
                List<Evolution> evolutions = parseEvolutions(o);
                Map<Integer, List<String>> levelUpMoves = parseLearnsetLevel(o, null, id);
                double weightKg = 0.0;
                if (o.has("weight") && o.get("weight").isJsonPrimitive()) {
                    try { weightKg = o.get("weight").getAsDouble(); } catch (Exception ignored) {}
                }
                Species s = new Species(id, name, types, baseStats, evYields, evolutions, levelUpMoves, baseExpYield, expGroup, catchRate, minLv, maxLv,
                        weightKg, java.util.List.of(), -1.0, jsonOptInt(o, "baseFriendship", 70));
                speciesById.put(id, s);
            } catch (Exception ex) {
                plugin.getLogger().warning("Failed to parse species file: " + p.getFileName() + " => " + ex.getMessage());
            }
        }
        plugin.getLogger().info("Loaded species: " + speciesById.size() + " from " + folder);
    }

    private void loadMovesRawFolder(Path folder) throws IOException {
        if (!Files.exists(folder)) return;

        // Search for a "moves.json" (Pokemon Showdown style) anywhere under moves_raw
        List<Path> jsonFiles = Files.walk(folder).filter(p -> p.toString().endsWith(".json")).collect(Collectors.toList());
        if (jsonFiles.isEmpty()) return;

        MoveRawImportReport report = new MoveRawImportReport();
        report.importRoot = folder.toAbsolutePath().toString();
        boolean importedAnything = false;

        // Prefer a file named moves.json if present
        Optional<Path> showdown = jsonFiles.stream()
                .filter(p -> p.getFileName().toString().equalsIgnoreCase("moves.json"))
                .findFirst();

        if (showdown.isPresent()) {
            importedAnything |= importShowdownMovesFile(showdown.get(), report);
        }

        // Also allow individual move definition jsons (our internal format or simple format)
        for (Path p : jsonFiles) {
            if (showdown.isPresent() && p.equals(showdown.get())) continue;
            try {
                importedAnything |= importSingleMoveFile(p, report);
            } catch (Exception ex) {
                report.failedFiles++;
                report.skippedFiles.add(p.toString());
                plugin.getLogger().warning("moves_raw: failed to parse " + p.getFileName() + " => " + ex.getMessage());
            }
        }

        if (importedAnything) {
            lastMovesRawImportReport = report;
            plugin.getLogger().info("Imported moves from moves_raw. showdown=" + report.parsedShowdownMoves +
                    ", single=" + report.parsedInternalMoves + ", failed=" + report.failedFiles);
        }
    }

    /**
     * Apply Pokemon Showdown learnsets (learnsets.json) to already loaded species.
     * This does NOT write files; it only updates the in-memory Species records.
     *
     * Showdown learnset tags look like "9L15" (gen9, level-up at 15). We accept any gen and parse the level.
     */
    private void applyShowdownLearnsets(Path movesRawFolder) {
        try {
            Path f = movesRawFolder.resolve("learnsets.json");
            if (!java.nio.file.Files.exists(f)) {
                plugin.getLogger().warning("showdown.learnsets: file not found: " + f);
                return;
            }
            // Default to all generations when the key is missing.
            int genFilter = plugin.getConfig().getInt("showdown.learnsets-generation", 0); // 0 = accept all
            boolean gen1OnlyMoves = plugin.getConfig().getBoolean("showdown.gen1-only-moves", false) || autoGen1SpeciesDetected;
            if ((gen1OnlyMoves || autoGen1SpeciesDetected) && genFilter == 0) genFilter = 1;


            JsonObject root;
            try (Reader r = java.nio.file.Files.newBufferedReader(f, java.nio.charset.StandardCharsets.UTF_8)) {
                root = gson.fromJson(r, JsonObject.class);
            }
            if (root == null) return;

            int appliedSpecies = 0;
            for (var spEn : new java.util.ArrayList<>(speciesById.entrySet())) {
                String spId = spEn.getKey();
                Species sp = spEn.getValue();
                if (!root.has(spId)) continue;
                JsonElement el = root.get(spId);
                if (!el.isJsonObject()) continue;
                JsonObject spObj = el.getAsJsonObject();
                if (!spObj.has("learnset") || !spObj.get("learnset").isJsonObject()) continue;
                JsonObject learnset = spObj.getAsJsonObject("learnset");

                java.util.Map<Integer, java.util.List<String>> merged = new java.util.HashMap<>();
                // start from existing, but sanitize old data (drop moves not present in current dex)
                for (var en : sp.levelUpMovesSafe().entrySet()) {
                    int lvlKey = en.getKey();
                    java.util.List<String> sanitized = new java.util.ArrayList<>();
                    for (String mid : en.getValue()) {
                        if (mid == null) continue;
                        String id = mid.toLowerCase();
                        Move mvDef = movesById.get(id);
                        if (mvDef == null) continue;
                        if (gen1OnlyMoves && (mvDef.num() < 1 || mvDef.num() > 165)) continue;
                        if (!sanitized.contains(id)) sanitized.add(id);
                    }
                    if (!sanitized.isEmpty()) merged.put(lvlKey, sanitized);
                }

                for (var mvEn : learnset.entrySet()) {
                    String mvId = mvEn.getKey().toLowerCase();
                    JsonElement tagsEl = mvEn.getValue();
                    if (!tagsEl.isJsonArray()) continue;
                    JsonArray tags = tagsEl.getAsJsonArray();
                    for (JsonElement tEl : tags) {
                        if (!tEl.isJsonPrimitive()) continue;
                        String tag = tEl.getAsString();
                        // Format examples: "9L15", "1L1", "9L1"
                        int idx = tag.indexOf('L');
                        if (idx <= 0) continue;
                        int gen = safeParseInt(tag.substring(0, idx), -1);
                        if (genFilter > 0 && gen != genFilter) continue;
                        int lvl = safeParseInt(tag.substring(idx + 1), -1);
                        if (lvl < 1 || lvl > 100) continue;
                        Move mvDef = movesById.get(mvId);
                        if (mvDef == null) continue;
                        if (gen1OnlyMoves && (mvDef.num() < 1 || mvDef.num() > 165)) continue;
                        merged.computeIfAbsent(lvl, k -> new java.util.ArrayList<>());
                        java.util.List<String> list = merged.get(lvl);
                        if (!list.contains(mvId)) list.add(mvId);
                    }
                }

                // build new species record
                Species ns = new Species(
                        sp.id(), sp.name(), sp.types(), sp.baseStats(), sp.evYields(), sp.evolutions(), merged,
                        sp.baseExpYield(), sp.expGroup(), sp.catchRate(), sp.minLevel(), sp.maxLevel(), sp.weightKg(),
                        sp.eggGroups(), sp.maleRatio(), sp.baseFriendship()
                );
                speciesById.put(spId, ns);
                appliedSpecies++;
            }

            plugin.getLogger().info("showdown.learnsets applied: species=" + appliedSpecies + ", genFilter=" + genFilter);
        } catch (Exception e) {
            plugin.getLogger().warning("showdown.learnsets apply failed: " + e.getMessage());
        }
    }

    private int safeParseInt(String s, int def) {
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            return def;
        }
    }

    /** Parse egg groups from a species JSON object (best-effort). */
    private List<String> parseEggGroups(JsonObject o) {
        if (o == null) return java.util.List.of();
        try {
            // Cobblemon commonly uses "eggGroups": ["monster", "dragon"]
            if (o.has("eggGroups") && o.get("eggGroups").isJsonArray()) {
                java.util.List<String> out = new java.util.ArrayList<>();
                for (JsonElement el : o.getAsJsonArray("eggGroups")) {
                    if (!el.isJsonPrimitive()) continue;
                    String g = el.getAsString();
                    if (g == null) continue;
                    g = g.trim().toLowerCase(java.util.Locale.ROOT);
                    if (!g.isEmpty() && !out.contains(g)) out.add(g);
                }
                return out;
            }
            // Some datasets may use snake_case
            if (o.has("egg_groups") && o.get("egg_groups").isJsonArray()) {
                java.util.List<String> out = new java.util.ArrayList<>();
                for (JsonElement el : o.getAsJsonArray("egg_groups")) {
                    if (!el.isJsonPrimitive()) continue;
                    String g = el.getAsString();
                    if (g == null) continue;
                    g = g.trim().toLowerCase(java.util.Locale.ROOT);
                    if (!g.isEmpty() && !out.contains(g)) out.add(g);
                }
                return out;
            }
        } catch (Throwable ignored) {}
        return java.util.List.of();
    }

    /** Parse male ratio (0.0~1.0), or -1 for genderless/unknown. */
    private double parseMaleRatio(JsonObject o) {
        if (o == null) return -1.0;
        try {
            // Pokemon Showdown style: gender: "N" for genderless
            if (o.has("gender") && o.get("gender").isJsonPrimitive() && o.get("gender").getAsJsonPrimitive().isString()) {
                String g = o.get("gender").getAsString();
                if (g != null) {
                    g = g.trim();
                    // Showdown sometimes uses fixed gender as "M"/"F"/"N"
                    if (g.equalsIgnoreCase("N")) return -1.0;
                    if (g.equalsIgnoreCase("M")) return 1.0;
                    if (g.equalsIgnoreCase("F")) return 0.0;
                    // Cobblemon/other datasets may use words
                    if (g.equalsIgnoreCase("GENDERLESS") || g.equalsIgnoreCase("NONE")) return -1.0;
                    if (g.equalsIgnoreCase("MALE")) return 1.0;
                    if (g.equalsIgnoreCase("FEMALE")) return 0.0;
                }
            }

            if (o.has("maleRatio") && o.get("maleRatio").isJsonPrimitive()) {
                return o.get("maleRatio").getAsDouble();
            }
            if (o.has("male_ratio") && o.get("male_ratio").isJsonPrimitive()) {
                return o.get("male_ratio").getAsDouble();
            }

            // Some datasets store male_ratio as a string percentage
            if (o.has("male_ratio") && o.get("male_ratio").isJsonPrimitive() && o.get("male_ratio").getAsJsonPrimitive().isString()) {
                String s = o.get("male_ratio").getAsString();
                s = s.replace("%", "").trim();
                double v = Double.parseDouble(s);
                return (v > 1.0) ? (v / 100.0) : v;
            }

            // Pokemon Showdown style: genderRatio: {"M":0.875,"F":0.125}
            if (o.has("genderRatio") && o.get("genderRatio").isJsonObject()) {
                JsonObject gr = o.getAsJsonObject("genderRatio");
                if (gr != null) {
                    // Common keys: M/F or male/female
                    String[] maleKeys = new String[]{"M", "m", "male"};
                    for (String k : maleKeys) {
                        if (!gr.has(k)) continue;
                        JsonElement el = gr.get(k);
                        if (el == null || el.isJsonNull()) continue;
                        if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isNumber()) {
                            double v = el.getAsDouble();
                            return (v > 1.0) ? (v / 100.0) : v;
                        }
                        if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()) {
                            String s = el.getAsString();
                            if (s != null) {
                                s = s.replace("%", "").trim();
                                double v = Double.parseDouble(s);
                                return (v > 1.0) ? (v / 100.0) : v;
                            }
                        }
                    }
                }
            }

            // Some datasets may use snake_case gender_ratio
            if (o.has("gender_ratio") && o.get("gender_ratio").isJsonObject()) {
                JsonObject gr = o.getAsJsonObject("gender_ratio");
                if (gr != null) {
                    String[] maleKeys = new String[]{"M", "m", "male"};
                    for (String k : maleKeys) {
                        if (!gr.has(k)) continue;
                        JsonElement el = gr.get(k);
                        if (el == null || el.isJsonNull()) continue;
                        if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isNumber()) {
                            double v = el.getAsDouble();
                            return (v > 1.0) ? (v / 100.0) : v;
                        }
                        if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()) {
                            String s = el.getAsString();
                            if (s != null) {
                                s = s.replace("%", "").trim();
                                double v = Double.parseDouble(s);
                                return (v > 1.0) ? (v / 100.0) : v;
                            }
                        }
                    }
                }
            }

            // Sometimes represented as a string like "50" or "50%"
            if (o.has("maleRatio") && o.get("maleRatio").isJsonPrimitive() && o.get("maleRatio").getAsJsonPrimitive().isString()) {
                String s = o.get("maleRatio").getAsString();
                s = s.replace("%", "").trim();
                double v = Double.parseDouble(s);
                return (v > 1.0) ? (v / 100.0) : v;
            }
            // Genderless flag
            if (o.has("genderless") && o.get("genderless").isJsonPrimitive() && o.get("genderless").getAsBoolean()) {
                return -1.0;
            }
        } catch (Throwable ignored) {}
        return -1.0;
    }

    private boolean importShowdownMovesFile(Path file, MoveRawImportReport report) {
        try {
            // Pokemon Showdown data is usually pure JSON, but some mirrors/packagers ship it as a JS assignment
            // like: "var BattleMovedex = {...};". Parse defensively by extracting the first JSON object.
            String raw = Files.readString(file, StandardCharsets.UTF_8);
            if (raw == null || raw.isBlank()) return false;
            int first = raw.indexOf('{');
            int last = raw.lastIndexOf('}');
            if (first < 0 || last <= first) return false;
            String json = raw.substring(first, last + 1);

            JsonObject root = gson.fromJson(json, JsonObject.class);
            if (root == null || root.size() == 0) return false;

            // Default to all generations when the key is missing. Also, if we auto-detected a Gen1 species dataset,
            // force Gen1-only imports even if an old config had this disabled (prevents Gen2+ moves like synthesis).
            boolean gen1Only = plugin.getConfig().getBoolean("showdown.gen1-only-moves", false) || autoGen1SpeciesDetected;

            int count = 0;
            int missingNum = 0;
            for (Map.Entry<String, JsonElement> en : root.entrySet()) {
                String id = en.getKey().toLowerCase(Locale.ROOT);
                if (!en.getValue().isJsonObject()) continue;
                JsonObject o = en.getValue().getAsJsonObject();

                int num = o.has("num") ? safeInt(o.get("num"), -1) : -1;
                if (num < 0) missingNum++;
                if (gen1Only && (num < 1 || num > 165)) {
                    continue;
                }

                String name = jsonOptString(o, "name", id);
                String type = o.has("type") ? o.get("type").getAsString().toLowerCase(Locale.ROOT) : "normal";

                String categoryRaw = o.has("category") ? o.get("category").getAsString() : "Physical";
                String category = normalizeCategory(categoryRaw);

                // Power:
                // - PS uses basePower
                // - Fixed-damage / OHKO / selfdestruct moves often have basePower=0; do NOT default them to 40.
                boolean hasSpecialDamageRule = o.has("damage") || (o.has("ohko") && o.get("ohko").isJsonPrimitive() && o.get("ohko").getAsBoolean())
                        || (o.has("selfdestruct") && o.get("selfdestruct").isJsonPrimitive() && o.get("selfdestruct").getAsBoolean())
                        || isKnownFixedDamageMove(id);

                int power = o.has("basePower") ? safeInt(o.get("basePower"), 0) : (o.has("power") ? safeInt(o.get("power"), 0) : 0);
                if (!hasSpecialDamageRule && power <= 0 && !"status".equals(category)) power = 40;

                double acc = 1.0;
                if (o.has("accuracy")) {
                    JsonElement a = o.get("accuracy");
                    if (a.isJsonPrimitive()) {
                        JsonPrimitive ap = a.getAsJsonPrimitive();
                        if (ap.isBoolean()) {
                            acc = ap.getAsBoolean() ? 1.0 : 1.0;
                        } else if (ap.isNumber()) {
                            acc = ap.getAsDouble();
                            if (acc > 1.0) acc = acc / 100.0; // PS uses percent
                            if (acc <= 0) acc = 1.0;
                        } else {
                            acc = 1.0;
                        }
                    } else {
                        acc = 1.0;
                    }
                }

                int pp = o.has("pp") ? safeInt(o.get("pp"), 35) : 35;
                int priority = o.has("priority") ? safeInt(o.get("priority"), 0) : 0;

                // Convert a subset of Showdown effect fields into our MoveEngine 'effects' list.
                List<Map<String, Object>> effects = new ArrayList<>();

                // Fixed damage: Showdown can have "damage": 20/40/... or special fixed-damage moves.
                // It can also be "level" (Night Shade / Seismic Toss) or "half" (Super Fang) in some dumps.
                if (o.has("damage") && o.get("damage").isJsonPrimitive() && o.get("damage").getAsJsonPrimitive().isNumber()) {
                    int dmg = o.get("damage").getAsInt();
                    if (dmg > 0) effects.add(mapOf("id", "fixed_damage", "amount", dmg));
                } else if (o.has("damage") && o.get("damage").isJsonPrimitive() && o.get("damage").getAsJsonPrimitive().isString()) {
                    String dmgMode = o.get("damage").getAsString();
                    if (dmgMode != null) {
                        dmgMode = dmgMode.toLowerCase(Locale.ROOT).trim();
                        if ("level".equals(dmgMode)) {
                            effects.add(mapOf("id", "fixed_damage", "mode", "level"));
                        } else if ("half".equals(dmgMode)) {
                            effects.add(mapOf("id", "fixed_damage", "mode", "half"));
                        }
                    }
                } else {
                    Integer fixed = fixedDamageAmountById(id);
                    if (fixed != null) {
                        effects.add(mapOf("id", "fixed_damage", "amount", fixed));
                    } else {
                        Map<String, Object> sp = specialFixedDamageEffectById(id);
                        if (sp != null) effects.add(sp);
                    }
                }

                // OHKO
                if (o.has("ohko") && o.get("ohko").isJsonPrimitive() && o.get("ohko").getAsBoolean()) {
                    effects.add(mapOf("id", "ohko"));
                }

                // Selfdestruct
                if (o.has("selfdestruct") && o.get("selfdestruct").isJsonPrimitive() && o.get("selfdestruct").getAsBoolean()) {
                    effects.add(mapOf("id", "selfdestruct"));
                }

                // Primary status (e.g., Toxic)
                if (o.has("status")) {
                    String st = normalizeStatusCode(o.get("status").getAsString());
                    if (st != null) effects.add(mapOf("id", "set_status", "status", st, "chance", 1.0, "target", "target"));
                }

                // Volatile status (we support leechseed, confusion)
                if (o.has("volatileStatus")) {
                    String v = o.get("volatileStatus").getAsString();
                    if (v != null && v.equalsIgnoreCase("leechseed")) {
                        effects.add(mapOf("id", "leech_seed"));
                    } else if (v != null && v.equalsIgnoreCase("confusion")) {
                        effects.add(mapOf("id", "confusion", "chance", 1.0, "target", "target", "min", 1, "max", 4));
                    }
                }

                // Side conditions / side-wide utility (Reflect / Light Screen / Safeguard / Tailwind / Aurora Veil)
                if (o.has("sideCondition")) {
                    String sc = o.get("sideCondition").getAsString();
                    if (sc != null) {
                        if (sc.equalsIgnoreCase("reflect")) {
                            effects.add(mapOf("id", "screen", "which", "reflect", "turns", 5));
                        } else if (sc.equalsIgnoreCase("lightscreen") || sc.equalsIgnoreCase("light_screen")) {
                            effects.add(mapOf("id", "screen", "which", "light_screen", "turns", 5));
                        } else if (sc.equalsIgnoreCase("safeguard")) {
                            effects.add(mapOf("id", "side_condition", "which", "safeguard", "turns", 5));
                        } else if (sc.equalsIgnoreCase("tailwind")) {
                            effects.add(mapOf("id", "side_condition", "which", "tailwind", "turns", 4));
                        } else if (sc.equalsIgnoreCase("auroraveil") || sc.equalsIgnoreCase("aurora_veil")) {
                            effects.add(mapOf("id", "side_condition", "which", "aurora_veil", "turns", 5));
                        } else if (sc.equalsIgnoreCase("stealthrock") || sc.equalsIgnoreCase("stealth_rock")) {
                            effects.add(mapOf("id", "side_condition", "which", "stealth_rock"));
                        } else if (sc.equalsIgnoreCase("spikes")) {
                            effects.add(mapOf("id", "side_condition", "which", "spikes"));
                        } else if (sc.equalsIgnoreCase("toxicspikes") || sc.equalsIgnoreCase("toxic_spikes")) {
                            effects.add(mapOf("id", "side_condition", "which", "toxic_spikes"));
                        } else if (sc.equalsIgnoreCase("stickyweb") || sc.equalsIgnoreCase("sticky_web")) {
                            effects.add(mapOf("id", "side_condition", "which", "sticky_web"));
                        }
                    }
                }

                // Boosts: usually apply to the target, but some moves store self-boosts here (e.g. Growth in some gens).
                // Use the move's declared target to decide where the boosts go.
                String moveTarget = o.has("target") && o.get("target").isJsonPrimitive() ? o.get("target").getAsString() : "";
                String boostsTarget = "self".equalsIgnoreCase(moveTarget) ? "self" : "target";
                if (o.has("boosts") && o.get("boosts").isJsonObject()) {
                    JsonObject boosts = o.getAsJsonObject("boosts");
                    for (Map.Entry<String, JsonElement> be : boosts.entrySet()) {
                        String stat = normalizeBoostStat(be.getKey());
                        int stages = safeInt(be.getValue(), 0);
                        if (stat != null && stages != 0) {
                            effects.add(mapOf("id", "stat_stage", "stat", stat, "stages", stages, "target", boostsTarget));
                        }
                    }
                }

                // Self boosts
                if (o.has("self") && o.get("self").isJsonObject()) {
                    JsonObject self = o.get("self").getAsJsonObject();
                    if (self.has("boosts") && self.get("boosts").isJsonObject()) {
                        JsonObject boosts = self.getAsJsonObject("boosts");
                        for (Map.Entry<String, JsonElement> be : boosts.entrySet()) {
                            String stat = normalizeBoostStat(be.getKey());
                            int stages = safeInt(be.getValue(), 0);
                            if (stat != null && stages != 0) {
                                effects.add(mapOf("id", "stat_stage", "stat", stat, "stages", stages, "target", "self"));
                            }
                        }
                    }
                }


                // Self status (e.g., Rest sets sleep on self)
                if (o.has("self") && o.get("self").isJsonObject()) {
                    JsonObject self = o.getAsJsonObject("self");
                    if (self.has("status") && self.get("status").isJsonPrimitive()) {
                        String st = normalizeStatusCode(self.get("status").getAsString());
                        if (st != null) {
                            // Gen1 Rest: exactly 2 turns sleep; others default 2-4 handled by MoveEngine
                            int turns = "rest".equalsIgnoreCase(id) ? 2 : 0;
                            if (turns > 0) effects.add(mapOf("id", "set_status", "status", st, "chance", 1.0, "target", "self", "turns", turns));
                            else effects.add(mapOf("id", "set_status", "status", st, "chance", 1.0, "target", "self"));
                        }
                    }
                }

                // Drain: [numerator, denominator]
                if (o.has("drain") && o.get("drain").isJsonArray()) {
                    JsonArray a = o.getAsJsonArray("drain");
                    if (a.size() >= 2) {
                        double nume = a.get(0).getAsDouble();
                        double den = a.get(1).getAsDouble();
                        if (den != 0) {
                            double pct = nume / den;
                            if (pct > 0) effects.add(mapOf("id", "drain", "percent", pct));
                        }
                    }
                }

                // Heal: [numerator, denominator] of max HP
                if (o.has("heal") && o.get("heal").isJsonArray()) {
                    JsonArray a = o.getAsJsonArray("heal");
                    if (a.size() >= 2) {
                        double nume = a.get(0).getAsDouble();
                        double den = a.get(1).getAsDouble();
                        if (den != 0) {
                            double pct = nume / den;
                            if (pct > 0) effects.add(mapOf("id", "heal", "percent", pct));
                        }
                    }
                }

                // Recoil: [numerator, denominator]
                if (o.has("recoil") && o.get("recoil").isJsonArray()) {
                    JsonArray a = o.getAsJsonArray("recoil");
                    if (a.size() >= 2) {
                        double nume = a.get(0).getAsDouble();
                        double den = a.get(1).getAsDouble();
                        if (den != 0) {
                            double pct = Math.abs(nume / den);
                            if (pct > 0) effects.add(mapOf("id", "recoil", "percent", pct));
                        }
                    }
                }

                // Secondary effects (chance)
                if (o.has("secondary") && o.get("secondary").isJsonObject()) {
                    JsonObject sec = o.getAsJsonObject("secondary");
                    double chance = sec.has("chance") ? safeInt(sec.get("chance"), 100) / 100.0 : 1.0;
                    if (sec.has("status")) {
                        String st = normalizeStatusCode(sec.get("status").getAsString());
                        if (st != null) effects.add(mapOf("id", "set_status", "status", st, "chance", chance, "target", "target"));
                    }
                    if (sec.has("flinch") && sec.get("flinch").isJsonPrimitive() && sec.get("flinch").getAsBoolean()) {
                        effects.add(mapOf("id", "flinch", "chance", chance));
                    }
                    if (sec.has("volatileStatus") && sec.get("volatileStatus").isJsonPrimitive()) {
                        String vs = sec.get("volatileStatus").getAsString();
                        if ("confusion".equalsIgnoreCase(vs)) {
                            effects.add(mapOf("id", "confusion", "chance", chance, "target", "target", "min", 1, "max", 4));
                        }
                    }
                    if (sec.has("boosts") && sec.get("boosts").isJsonObject()) {
                        JsonObject boosts = sec.getAsJsonObject("boosts");
                        for (Map.Entry<String, JsonElement> be : boosts.entrySet()) {
                            String stat = normalizeBoostStat(be.getKey());
                            int stages = safeInt(be.getValue(), 0);
                            if (stat != null && stages != 0) {
                                effects.add(mapOf("id", "stat_stage", "stat", stat, "stages", stages, "chance", chance, "target", "target"));
                            }
                        }
                    }
                }

                // Secondary effects array (e.g. Fire Fang / Ice Fang / Thunder Fang / Triple Arrows)
                if (o.has("secondaries") && o.get("secondaries").isJsonArray()) {
                    JsonArray secs = o.getAsJsonArray("secondaries");
                    for (JsonElement secEl : secs) {
                        if (secEl == null || !secEl.isJsonObject()) continue;
                        JsonObject sec = secEl.getAsJsonObject();
                        double chance = sec.has("chance") ? safeInt(sec.get("chance"), 100) / 100.0 : 1.0;
                        if (sec.has("status")) {
                            String st = normalizeStatusCode(sec.get("status").getAsString());
                            if (st != null) effects.add(mapOf("id", "set_status", "status", st, "chance", chance, "target", "target"));
                        }
                        if (sec.has("volatileStatus") && sec.get("volatileStatus").isJsonPrimitive()) {
                            String vs = sec.get("volatileStatus").getAsString();
                            if ("confusion".equalsIgnoreCase(vs)) {
                                effects.add(mapOf("id", "confusion", "chance", chance, "target", "target", "min", 1, "max", 4));
                            } else if ("flinch".equalsIgnoreCase(vs)) {
                                effects.add(mapOf("id", "flinch", "chance", chance));
                            }
                        }
                        if (sec.has("flinch") && sec.get("flinch").isJsonPrimitive() && sec.get("flinch").getAsBoolean()) {
                            effects.add(mapOf("id", "flinch", "chance", chance));
                        }
                        if (sec.has("boosts") && sec.get("boosts").isJsonObject()) {
                            JsonObject boosts = sec.getAsJsonObject("boosts");
                            for (Map.Entry<String, JsonElement> be : boosts.entrySet()) {
                                String stat = normalizeBoostStat(be.getKey());
                                int stages = safeInt(be.getValue(), 0);
                                if (stat != null && stages != 0) {
                                    effects.add(mapOf("id", "stat_stage", "stat", stat, "stages", stages, "chance", chance, "target", "target"));
                                }
                            }
                        }
                    }
                }

                // Self-boosts after damage (e.g. Clanging Scales / Scale Shot)
                if (o.has("selfBoost") && o.get("selfBoost").isJsonObject()) {
                    JsonObject selfBoost = o.getAsJsonObject("selfBoost");
                    if (selfBoost.has("boosts") && selfBoost.get("boosts").isJsonObject()) {
                        JsonObject boosts = selfBoost.getAsJsonObject("boosts");
                        for (Map.Entry<String, JsonElement> be : boosts.entrySet()) {
                            String stat = normalizeBoostStat(be.getKey());
                            int stages = safeInt(be.getValue(), 0);
                            if (stat != null && stages != 0) {
                                effects.add(mapOf("id", "stat_stage", "stat", stat, "stages", stages, "target", "self"));
                            }
                        }
                    }
                }

                // Common later-gen utility flags that fit our current engine.
                if (o.has("stallingMove") && o.get("stallingMove").isJsonPrimitive() && o.get("stallingMove").getAsBoolean()) {
                    effects.add(mapOf("id", "protect"));
                }
                if (o.has("selfSwitch")) {
                    effects.add(mapOf("id", "self_switch"));
                }
                if (o.has("weather") && o.get("weather").isJsonPrimitive()) {
                    String weather = o.get("weather").getAsString();
                    if (weather != null && !weather.isBlank()) effects.add(mapOf("id", "weather", "weather", weather));
                }
                if (o.has("pseudoWeather") && o.get("pseudoWeather").isJsonPrimitive()) {
                    String pw = o.get("pseudoWeather").getAsString();
                    if (pw != null) {
                        if (pw.equalsIgnoreCase("trickroom") || pw.equalsIgnoreCase("trick_room")) {
                            effects.add(mapOf("id", "room", "which", "trick_room", "turns", 5));
                        } else if (pw.equalsIgnoreCase("magicroom") || pw.equalsIgnoreCase("magic_room")) {
                            effects.add(mapOf("id", "room", "which", "magic_room", "turns", 5));
                        } else if (pw.equalsIgnoreCase("wonderroom") || pw.equalsIgnoreCase("wonder_room")) {
                            effects.add(mapOf("id", "room", "which", "wonder_room", "turns", 5));
                        }
                    }
                }

                // Simple volatile statuses we can already express with current battle state.
                if (o.has("volatileStatus") && o.get("volatileStatus").isJsonPrimitive()) {
                    String v = o.get("volatileStatus").getAsString();
                    if ("attract".equalsIgnoreCase(v)) {
                        effects.add(mapOf("id", "attract", "target", "target"));
                    }
                }

                // Quality-of-life mappings for party cure moves in single battles.
                if ("healbell".equalsIgnoreCase(id) || "aromatherapy".equalsIgnoreCase(id)) {
                    effects.add(mapOf("id", "cure_status_party"));
                }
                if ("junglehealing".equalsIgnoreCase(id) || "lunarblessing".equalsIgnoreCase(id)) {
                    effects.add(mapOf("id", "cure_status_party"));
                    effects.add(mapOf("id", "heal", "percent", 0.25));
                }

                // Straightforward later-gen utility/status moves that our engine can model directly.
                if ("wish".equalsIgnoreCase(id)) effects.add(mapOf("id", "wish", "turns", 1));
                if ("ingrain".equalsIgnoreCase(id)) effects.add(mapOf("id", "ingrain"));
                if ("aquaring".equalsIgnoreCase(id) || "aqua_ring".equalsIgnoreCase(id)) effects.add(mapOf("id", "aqua_ring"));
                if ("healblock".equalsIgnoreCase(id) || "heal_block".equalsIgnoreCase(id)) effects.add(mapOf("id", "heal_block", "turns", 5));
                if ("nightmare".equalsIgnoreCase(id)) effects.add(mapOf("id", "nightmare", "turns", 3));
                if ("taunt".equalsIgnoreCase(id)) effects.add(mapOf("id", "taunt", "turns", 3));
                if ("torment".equalsIgnoreCase(id)) effects.add(mapOf("id", "torment", "turns", 3));
                if ("encore".equalsIgnoreCase(id)) effects.add(mapOf("id", "encore", "turns", 3));
                if ("perishsong".equalsIgnoreCase(id) || "perish_song".equalsIgnoreCase(id)) effects.add(mapOf("id", "perish_song", "turns", 3));
                if ("luckychant".equalsIgnoreCase(id) || "lucky_chant".equalsIgnoreCase(id)) effects.add(mapOf("id", "side_condition", "which", "lucky_chant", "turns", 5));
                if ("defog".equalsIgnoreCase(id)) effects.add(mapOf("id", "defog"));
                if ("gastroacid".equalsIgnoreCase(id) || "gastro_acid".equalsIgnoreCase(id)) effects.add(mapOf("id", "gastro_acid"));
                if ("trickroom".equalsIgnoreCase(id) || "trick_room".equalsIgnoreCase(id)) effects.add(mapOf("id", "room", "which", "trick_room", "turns", 5));
                if ("magicroom".equalsIgnoreCase(id) || "magic_room".equalsIgnoreCase(id)) effects.add(mapOf("id", "room", "which", "magic_room", "turns", 5));
                if ("wonderroom".equalsIgnoreCase(id) || "wonder_room".equalsIgnoreCase(id)) effects.add(mapOf("id", "room", "which", "wonder_room", "turns", 5));
                if ("smackdown".equalsIgnoreCase(id) || "smack_down".equalsIgnoreCase(id)) effects.add(mapOf("id", "smack_down"));
                if ("gravity".equalsIgnoreCase(id)) effects.add(mapOf("id", "gravity", "turns", 5));
                if ("foresight".equalsIgnoreCase(id)) effects.add(mapOf("id", "foresight"));
                if ("miracleeye".equalsIgnoreCase(id) || "miracle_eye".equalsIgnoreCase(id)) effects.add(mapOf("id", "miracle_eye"));
                if ("healpulse".equalsIgnoreCase(id) || "heal_pulse".equalsIgnoreCase(id)) effects.add(mapOf("id", "heal_target", "percent", 0.5));
                if ("charge".equalsIgnoreCase(id)) effects.add(mapOf("id", "charge"));
                if ("magnetrise".equalsIgnoreCase(id) || "magnet_rise".equalsIgnoreCase(id)) effects.add(mapOf("id", "magnet_rise", "turns", 5));
                if ("telekinesis".equalsIgnoreCase(id)) effects.add(mapOf("id", "telekinesis", "turns", 3));
                if ("saltcure".equalsIgnoreCase(id) || "salt_cure".equalsIgnoreCase(id)) effects.add(mapOf("id", "salt_cure", "turns", 4));
                if ("destinybond".equalsIgnoreCase(id) || "destiny_bond".equalsIgnoreCase(id)) effects.add(mapOf("id", "destiny_bond"));
                if ("shedtail".equalsIgnoreCase(id) || "shed_tail".equalsIgnoreCase(id)) effects.add(mapOf("id", "shed_tail"));

                if ("psychup".equalsIgnoreCase(id) || "psych_up".equalsIgnoreCase(id)) effects.add(mapOf("id", "psych_up"));
                if ("heartswap".equalsIgnoreCase(id) || "heart_swap".equalsIgnoreCase(id)) effects.add(mapOf("id", "heart_swap"));
                if ("powerswap".equalsIgnoreCase(id) || "power_swap".equalsIgnoreCase(id)) effects.add(mapOf("id", "power_swap"));
                if ("guardswap".equalsIgnoreCase(id) || "guard_swap".equalsIgnoreCase(id)) effects.add(mapOf("id", "guard_swap"));
                if ("topsyturvy".equalsIgnoreCase(id) || "topsy_turvy".equalsIgnoreCase(id)) effects.add(mapOf("id", "topsy_turvy"));
                if ("soak".equalsIgnoreCase(id)) effects.add(mapOf("id", "type_change", "target", "target", "type1", "water"));
                if ("magicpowder".equalsIgnoreCase(id) || "magic_powder".equalsIgnoreCase(id)) effects.add(mapOf("id", "type_change", "target", "target", "type1", "psychic"));
                if ("trickortreat".equalsIgnoreCase(id) || "trick_or_treat".equalsIgnoreCase(id)) effects.add(mapOf("id", "type_add", "target", "target", "type", "ghost"));
                if ("forestscurse".equalsIgnoreCase(id) || "forests_curse".equalsIgnoreCase(id)) effects.add(mapOf("id", "type_add", "target", "target", "type", "grass"));
                if ("reflecttype".equalsIgnoreCase(id) || "reflect_type".equalsIgnoreCase(id)) effects.add(mapOf("id", "reflect_type"));
                if ("conversion2".equalsIgnoreCase(id) || "conversion_2".equalsIgnoreCase(id)) effects.add(mapOf("id", "conversion2"));
                if ("stockpile".equalsIgnoreCase(id)) effects.add(mapOf("id", "stockpile"));
                if ("spitup".equalsIgnoreCase(id) || "spit_up".equalsIgnoreCase(id)) effects.add(mapOf("id", "spit_up"));
                if ("swallow".equalsIgnoreCase(id)) effects.add(mapOf("id", "swallow"));
                if ("imprison".equalsIgnoreCase(id)) effects.add(mapOf("id", "imprison", "turns", 999));
                if ("healingwish".equalsIgnoreCase(id) || "healing_wish".equalsIgnoreCase(id)) effects.add(mapOf("id", "healing_wish"));
                if ("revivalblessing".equalsIgnoreCase(id) || "revival_blessing".equalsIgnoreCase(id)) effects.add(mapOf("id", "revival_blessing"));
                if ("dragontail".equalsIgnoreCase(id) || "dragon_tail".equalsIgnoreCase(id)) effects.add(mapOf("id", "phaze"));
                if ("circlethrow".equalsIgnoreCase(id) || "circle_throw".equalsIgnoreCase(id)) effects.add(mapOf("id", "phaze"));
                if ("roar".equalsIgnoreCase(id) || "whirlwind".equalsIgnoreCase(id)) effects.add(mapOf("id", "phaze"));
                if ("embargo".equalsIgnoreCase(id)) effects.add(mapOf("id", "embargo", "turns", 5));
                if ("laserfocus".equalsIgnoreCase(id) || "laser_focus".equalsIgnoreCase(id)) effects.add(mapOf("id", "laser_focus", "turns", 1));
                if ("painsplit".equalsIgnoreCase(id) || "pain_split".equalsIgnoreCase(id)) effects.add(mapOf("id", "pain_split"));
                if ("guardsplit".equalsIgnoreCase(id) || "guard_split".equalsIgnoreCase(id)) effects.add(mapOf("id", "guard_split"));
                if ("powersplit".equalsIgnoreCase(id) || "power_split".equalsIgnoreCase(id)) effects.add(mapOf("id", "power_split"));
                if ("speedswap".equalsIgnoreCase(id) || "speed_swap".equalsIgnoreCase(id)) effects.add(mapOf("id", "speed_swap"));
                if ("powertrick".equalsIgnoreCase(id) || "power_trick".equalsIgnoreCase(id)) effects.add(mapOf("id", "power_trick"));
                if ("psychoshift".equalsIgnoreCase(id) || "psycho_shift".equalsIgnoreCase(id)) effects.add(mapOf("id", "psycho_shift"));
                if ("trick".equalsIgnoreCase(id) || "switcheroo".equalsIgnoreCase(id)) effects.add(mapOf("id", "swap_items"));
                if ("bestow".equalsIgnoreCase(id)) effects.add(mapOf("id", "bestow"));
                if ("recycle".equalsIgnoreCase(id)) effects.add(mapOf("id", "recycle"));
                if ("camouflage".equalsIgnoreCase(id)) effects.add(mapOf("id", "type_change", "target", "self", "type1", "normal"));
                if ("powder".equalsIgnoreCase(id)) effects.add(mapOf("id", "powder"));
                if ("electrify".equalsIgnoreCase(id)) effects.add(mapOf("id", "electrify"));
                if ("iondeluge".equalsIgnoreCase(id) || "ion_deluge".equalsIgnoreCase(id)) effects.add(mapOf("id", "ion_deluge"));
                if ("tarshot".equalsIgnoreCase(id) || "tar_shot".equalsIgnoreCase(id)) effects.add(mapOf("id", "tar_shot"));
                if ("octolock".equalsIgnoreCase(id) || "octo_lock".equalsIgnoreCase(id)) effects.add(mapOf("id", "octolock"));
                if ("noretreat".equalsIgnoreCase(id) || "no_retreat".equalsIgnoreCase(id)) effects.add(mapOf("id", "no_retreat"));
                if ("electricterrain".equalsIgnoreCase(id) || "electric_terrain".equalsIgnoreCase(id)) effects.add(mapOf("id", "terrain", "which", "electric", "turns", 5));
                if ("grassyterrain".equalsIgnoreCase(id) || "grassy_terrain".equalsIgnoreCase(id)) effects.add(mapOf("id", "terrain", "which", "grassy", "turns", 5));
                if ("mistyterrain".equalsIgnoreCase(id) || "misty_terrain".equalsIgnoreCase(id)) effects.add(mapOf("id", "terrain", "which", "misty", "turns", 5));
                if ("psychicterrain".equalsIgnoreCase(id) || "psychic_terrain".equalsIgnoreCase(id)) effects.add(mapOf("id", "terrain", "which", "psychic", "turns", 5));
                if ("fairylock".equalsIgnoreCase(id) || "fairy_lock".equalsIgnoreCase(id)) effects.add(mapOf("id", "fairy_lock", "turns", 2));
                if ("quickguard".equalsIgnoreCase(id) || "quick_guard".equalsIgnoreCase(id)) effects.add(mapOf("id", "protect"));
                if ("wideguard".equalsIgnoreCase(id) || "wide_guard".equalsIgnoreCase(id)) effects.add(mapOf("id", "protect"));
                if ("craftyshield".equalsIgnoreCase(id) || "crafty_shield".equalsIgnoreCase(id)) effects.add(mapOf("id", "protect"));
                if ("coreenforcer".equalsIgnoreCase(id) || "core_enforcer".equalsIgnoreCase(id)) effects.add(mapOf("id", "gastro_acid"));
                if ("corrosivegas".equalsIgnoreCase(id) || "corrosive_gas".equalsIgnoreCase(id)) effects.add(mapOf("id", "remove_item"));
                if ("jawlock".equalsIgnoreCase(id) || "jaw_lock".equalsIgnoreCase(id)) effects.add(mapOf("id", "jaw_lock"));
                if ("throatchop".equalsIgnoreCase(id) || "throat_chop".equalsIgnoreCase(id)) effects.add(mapOf("id", "throat_chop", "turns", 2));
                if ("burnup".equalsIgnoreCase(id) || "burn_up".equalsIgnoreCase(id)) effects.add(mapOf("id", "burn_up"));
                if ("doubleshock".equalsIgnoreCase(id) || "double_shock".equalsIgnoreCase(id)) effects.add(mapOf("id", "double_shock"));


                // Multi-hit: number or [min,max]
                if (o.has("multihit")) {
                    JsonElement mh = o.get("multihit");
                    if (mh.isJsonPrimitive() && mh.getAsJsonPrimitive().isNumber()) {
                        int hits = mh.getAsInt();
                        if (hits > 1) effects.add(mapOf("id", "multi_hit", "hits", hits, "min", hits, "max", hits));
                    } else if (mh.isJsonArray()) {
                        JsonArray a = mh.getAsJsonArray();
                        if (a.size() >= 2) {
                            int min = a.get(0).getAsInt();
                            int max = a.get(1).getAsInt();
                            if (min > 1 && max >= min) effects.add(mapOf("id", "multi_hit", "min", min, "max", max));
                        }
                    }
                }

                // Two-turn moves (minimal mapping by id)
                if (isTwoTurnMove(id)) {
                    effects.add(mapOf("id", "two_turn", "chargeTurns", 1, "chargeText", "§7{user} 蓄力中……"));
                }

                // Fixed damage / special damage kinds (Gen1)
                if (o.has("damage")) {
                    JsonElement dmgEl = o.get("damage");
                    if (dmgEl != null) {
                        if (dmgEl.isJsonPrimitive() && dmgEl.getAsJsonPrimitive().isNumber()) {
                            int fixed = safeInt(dmgEl, 0);
                            if (fixed > 0) effects.add(mapOf("id", "fixed_damage", "amount", fixed));
                        } else if (dmgEl.isJsonPrimitive() && dmgEl.getAsJsonPrimitive().isString()) {
                            String dv = dmgEl.getAsString();
                            if ("level".equalsIgnoreCase(dv)) {
                                // Night Shade / Seismic Toss etc.
                                effects.add(mapOf("id", "fixed_damage", "mode", "level"));
                            } else if ("half".equalsIgnoreCase(dv)) {
                                // Super Fang
                                effects.add(mapOf("id", "fixed_damage", "mode", "half"));
                            }
                        }
                    }
                }

                // OHKO moves (Gen1 simplified)
                if (o.has("ohko") && o.get("ohko").isJsonPrimitive() && o.get("ohko").getAsBoolean()) {
                    effects.add(mapOf("id", "ohko"));
                }

                // Self-destruct/explosion moves
                if (o.has("selfdestruct")) {
                    effects.add(mapOf("id", "selfdestruct"));
                }

                // Rest (Gen1): heal to full + sleep 2 turns
                if ("rest".equalsIgnoreCase(id)) {
                    effects.add(mapOf("id", "rest"));
                }

                // Gen1 special moves (Showdown/original behavior)
                if ("conversion".equalsIgnoreCase(id)) {
                    effects.add(mapOf("id", "conversion"));
                }
                if ("mirrormove".equalsIgnoreCase(id)) {
                    effects.add(mapOf("id", "mirror_move"));
                }

                // Step2 special moves (battle-only transient behaviors in MoveEngine)
                if ("transform".equalsIgnoreCase(id)) {
                    effects.add(mapOf("id", "transform"));
                }
                if ("mimic".equalsIgnoreCase(id)) {
                    effects.add(mapOf("id", "mimic"));
                }
                if ("metronome".equalsIgnoreCase(id)) {
                    effects.add(mapOf("id", "metronome"));
                }

                // Extra Gen1 special moves mapping (kept minimal and id-based)
                if ("substitute".equalsIgnoreCase(id)) {
                    effects.add(mapOf("id", "substitute"));
                }
                if ("hyperbeam".equalsIgnoreCase(id)) {
                    effects.add(mapOf("id", "recharge"));
                }
                if ("disable".equalsIgnoreCase(id)) {
                    effects.add(mapOf("id", "disable"));
                }
                if ("bide".equalsIgnoreCase(id)) {
                    effects.add(mapOf("id", "bide"));
                }
                if ("counter".equalsIgnoreCase(id)) {
                    effects.add(mapOf("id", "counter"));
                }
                if ("haze".equalsIgnoreCase(id)) {
                    effects.add(mapOf("id", "haze"));
                }
                if ("mist".equalsIgnoreCase(id)) {
                    effects.add(mapOf("id", "mist"));
                }
                if (id != null) {
                    String k = id.toLowerCase(java.util.Locale.ROOT);
                    if (k.equals("bind") || k.equals("wrap") || k.equals("clamp") || k.equals("firespin") || k.equals("magmastorm") || k.equals("whirlpool") || k.equals("sandtomb") || k.equals("infestation") || k.equals("snaptrap") || k.equals("thundercage")) {
                        effects.add(mapOf("id", "partial_trap"));
                    }
                }

                // Ensure core Gen1 special status moves always have effect ids even if Showdown
                // dumps omit convenient flags for them. Without this, the moves become "no_effects"
                // in our coverage report and do nothing in battle.
                effects = ensureCoreGen1Effects(id, effects);

                Move m = new Move(id, name, type, category, power, acc, pp, priority, num, Map.of(), effects);
                movesById.put(id, m);
                count++;
            }

            report.parsedShowdownMoves += count;
            report.notes.add("showdownMissingNum: " + missingNum);
            report.notes.add("showdownFile: " + file.toString());
            return count > 0;
        } catch (Exception ex) {
            report.failedFiles++;
            report.skippedFiles.add(file.toString());
            plugin.getLogger().warning("moves_raw: failed to parse showdown moves.json: " + ex.getMessage());
            return false;
        }
    }

    private boolean importSingleMoveFile(Path file, MoveRawImportReport report) throws IOException {
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject o = gson.fromJson(r, JsonObject.class);
            if (o == null) return false;

            // If this looks like Pokemon Showdown map (no id but many keys), ignore here
            if (!o.has("id") && o.entrySet().size() > 10 && o.entrySet().stream().allMatch(e -> e.getValue().isJsonObject())) {
                // probably a showdown map but named differently
                report.notes.add("skipped-showdown-map-file: " + file.toString());
                return false;
            }

            // Internal move definition format (id required)
            if (!o.has("id")) return false;

            String id = o.get("id").getAsString().toLowerCase();
            String name = jsonOptString(o, "name", id);
            String type = o.has("type") ? o.get("type").getAsString().toLowerCase() : "normal";
            String category = o.has("category") ? normalizeCategory(o.get("category").getAsString()) : "physical";
            int power = o.has("power") ? o.get("power").getAsInt() : 40;
            double acc = o.has("accuracy") ? o.get("accuracy").getAsDouble() : 1.0;
            int pp = o.has("pp") ? o.get("pp").getAsInt() : 35;
            int priority = o.has("priority") ? o.get("priority").getAsInt() : 0;

            Move m = new Move(id, name, type, category, power, acc, pp, priority, -1, Map.of(), java.util.List.of());
            movesById.put(id, m);

            report.parsedInternalMoves++;
            return true;
        }
    }

    private String normalizeCategory(String raw) {
        if (raw == null) return "physical";
        String r = raw.trim().toLowerCase();
        if (r.equals("physical") || r.equals("phys")) return "physical";
        if (r.equals("special") || r.equals("spec")) return "special";
        if (r.equals("status") || r.equals("other")) return "status";
        // Sometimes already lower
        if (r.equals("变化") || r.equals("辅助")) return "status";
        return r;
    }

    private boolean isTwoTurnMove(String id) {
        if (id == null) return false;
        String k = id.toLowerCase();
        // Minimal list: we can expand later.
        return switch (k) {
            case "dig", "fly", "solarbeam", "skyattack", "razorwind", "skullbash" -> true;
            default -> false;
        };
    }


    private boolean isKnownFixedDamageMove(String id) {
        return fixedDamageAmountById(id) != null || isSpecialFixedDamageById(id);
    }

    private Integer fixedDamageAmountById(String id) {
        if (id == null) return null;
        return switch (id.toLowerCase(Locale.ROOT)) {
            case "sonicboom" -> 20;
            case "dragonrage" -> 40;
            default -> null;
        };
    }

    private boolean isSpecialFixedDamageById(String id) {
        if (id == null) return false;
        return switch (id.toLowerCase(Locale.ROOT)) {
            case "nightshade", "seismictoss", "superfang" -> true;
            default -> false;
        };
    }

    private Map<String, Object> specialFixedDamageEffectById(String id) {
        if (id == null) return null;
        return switch (id.toLowerCase(Locale.ROOT)) {
            case "nightshade" -> mapOf("id", "fixed_damage", "mode", "level");
            case "seismictoss" -> mapOf("id", "fixed_damage", "mode", "level");
            case "superfang" -> mapOf("id", "fixed_damage", "mode", "half");
            default -> null;
        };
    }

    /**
     * Safe int parsing for Gson elements.
     * Pokemon Showdown data sometimes uses numbers, but we also accept numeric strings defensively.
     */
    private int safeInt(JsonElement e, int def) {
        try {
            if (e == null || !e.isJsonPrimitive()) return def;
            JsonPrimitive p = e.getAsJsonPrimitive();
            if (p.isNumber()) return p.getAsInt();
            if (p.isString()) {
                String s = p.getAsString();
                if (s == null) return def;
                s = s.trim();
                if (s.isEmpty()) return def;
                try {
                    return Integer.parseInt(s);
                } catch (NumberFormatException ignored) {
                    return def;
                }
            }
            return def;
        } catch (Exception ex) {
            return def;
        }
    }

    private void writeMovesRawImportReport(MoveRawImportReport report) {
        try {
            Path reports = plugin.getDataFolder().toPath().resolve("reports");
            if (!Files.exists(reports)) Files.createDirectories(reports);
            Path out = reports.resolve("moves_raw_import_report.yml");

            StringBuilder sb = new StringBuilder();
            sb.append("generatedAt: \"").append(java.time.OffsetDateTime.now()).append("\"\n");
            sb.append("importRoot: \"").append(escapeYaml(report.importRoot)).append("\"\n");
            sb.append("parsedShowdownMoves: " + report.parsedShowdownMoves + "\n");
            sb.append("parsedInternalMoves: " + report.parsedInternalMoves + "\n");
            sb.append("failedFiles: " + report.failedFiles + "\n");
            if (!report.notes.isEmpty()) {
                sb.append("notes:\n");
                for (String n : report.notes) sb.append("  - \"").append(escapeYaml(n)).append("\"\n");
            }
            if (!report.skippedFiles.isEmpty()) {
                sb.append("skippedFiles:\n");
                for (String f : report.skippedFiles) sb.append("  - \"").append(escapeYaml(f)).append("\"\n");
            }

            Files.writeString(out, sb.toString(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to write moves_raw report: " + e.getMessage());
        }
    }

    private void loadMovesFolder(Path folder) throws IOException {
        if (!Files.exists(folder)) return;
        List<Path> files = Files.walk(folder).filter(p -> p.toString().endsWith(".json")).toList();
        for (Path p : files) {
            try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
                JsonObject o = gson.fromJson(r, JsonObject.class);
                String id = jsonOptString(o, "id", "").toLowerCase(Locale.ROOT);
                if (id.isBlank()) continue;
                String name = jsonOptString(o, "name", id);
                String type = o.has("type") ? o.get("type").getAsString().toLowerCase() : "normal";
                String category = o.has("category") ? o.get("category").getAsString().toLowerCase() : "physical";
                int power = o.has("power") ? o.get("power").getAsInt() : 40;
                double acc = o.has("accuracy") ? o.get("accuracy").getAsDouble() : 1.0;
                int pp = o.has("pp") ? o.get("pp").getAsInt() : 35;
                int priority = o.has("priority") ? o.get("priority").getAsInt() : 0;
                int num = o.has("num") ? safeInt(o.get("num"), -1) : -1;

                Map<String,Object> effect = new HashMap<>();
                if (o.has("effect")) {
                    // store as generic map
                    effect = gson.fromJson(o.get("effect"), Map.class);
                }
                java.util.List<java.util.Map<String, Object>> effects = java.util.List.of();
                if (o.has("effects")) {
                    try {
                        effects = gson.fromJson(o.get("effects"), java.util.List.class);
                    } catch (Exception ignored) {
                        effects = java.util.List.of();
                    }
                }
                // Ensure core Gen1 special moves always have effect ids even if local move JSON lacks them
                effects = ensureCoreGen1Effects(id, effects);

                Move m = new Move(id, name, type, category, power, acc, pp, priority, num, effect, effects);
                movesById.put(id, m);
            } catch (Exception ex) {
                plugin.getLogger().warning("Failed to parse move file: " + p.getFileName() + " => " + ex.getMessage());
            }
        }
        plugin.getLogger().info("Loaded moves: " + movesById.size() + " from " + folder);
    }

    public Species getSpecies(String id) {
        if (id == null) return null;
        String key = id.trim().toLowerCase();
        return speciesById.get(key);
    }

    /**
     * Resolve species across different id conventions (namespace, hyphen/underscore, punctuation).
     */
    public Species getSpeciesFlexible(String rawId) {
        if (rawId == null) return null;
        String norm = normalizeId(rawId);
        Species s = speciesById.get(norm);
        if (s != null) return s;
        String alias = speciesAliasToId.get(norm);
        if (alias != null) return speciesById.get(alias);
        String a2 = speciesAliasToId.get(norm.replace('-', '_'));
        if (a2 != null) return speciesById.get(a2);
        String a3 = speciesAliasToId.get(stripToAlnum(norm));
        if (a3 != null) return speciesById.get(a3);
        // last resort: try common replacements
        String guess = norm.replace("-", "_").replace(".", "_");
        return speciesById.get(guess);
    }

    public Move getMove(String id) {
        if (id == null) return null;
        return movesById.get(id.trim().toLowerCase());
    }

    public Move getMoveOrPlaceholder(String id) {
        if (id == null) return null;
        String key = id.trim().toLowerCase();
        Move m = movesById.get(key);
        if (m != null) return m;
        // Create a placeholder move so learnsets can work even if full move data isn't imported yet.
        Move placeholder = new Move(key, key, "normal", "physical", 40, 1.0, 35, 0, -1, Map.of(), java.util.List.of());
        movesById.put(key, placeholder);
        return placeholder;
    }

        public Collection<Move> allMoves() {
        return movesById.values();
    }

public Collection<Species> allSpecies() {
        return speciesById.values();
    }

    public int getSpeciesCount() {
        return speciesById.size();
    }

    public int getMoveCount() {
        return movesById.size();
    }

    /** Whether current dataset should be treated as Gen1-only for moves/learnsets. */
    public boolean isGen1OnlyMode() {
        return plugin.getConfig().getBoolean("showdown.gen1-only-moves", false) || autoGen1SpeciesDetected;
    }

    /**
     * Whether a move is usable under the current dataset/mode.
     * <ul>
     *   <li>Must exist in dex</li>
     *   <li>If Gen1-only mode is enabled, must be within #1..#165</li>
     * </ul>
     */
    public boolean isMoveAllowed(String moveId) {
        if (moveId == null) return false;
        String id = moveId.toLowerCase(java.util.Locale.ROOT);
        Move mv = getMove(id);
        if (mv == null) return false;
        if (isGen1OnlyMode() && (mv.num() < 1 || mv.num() > 165)) return false;
        return true;
    }

    /** Manually apply Showdown learnsets (learnsets.json) once, without needing config toggle. */
    public void applyShowdownLearnsetsNow() {
        applyShowdownLearnsets(plugin.getDataFolder().toPath().resolve("moves_raw"));
    }

    public List<String> getSpeciesIdsSorted() {
        return speciesById.keySet().stream().sorted().collect(Collectors.toList());
    }


/**
 * Import Cobblemon species JSON files (as-is from Cobblemon repo) into our minimal Species dex.
 * Put Cobblemon species folder under plugins/PokeDemo/species_raw/ (can contain nested generation folders).
 * We only extract minimal fields for the demo: id, display name, types, baseStats, catchRate, and a default level range.
 */
public void loadCobblemonSpeciesFolder(Path folder) throws IOException {
    if (!Files.exists(folder)) {
        // Create the folder to guide server owners.
        Files.createDirectories(folder);
        return;
    }

    // Old behaviour always preferred generation1 whenever that folder existed anywhere under species_raw.
    // That was fine for a pure-Gen1 dataset, but it breaks full Cobblemon imports because the plugin folder
    // commonly contains species/generation1..generation9 all at once. In that layout we must import ALL gens,
    // otherwise commands like /pokedemo summon chikorita will fail with “unknown species”.
    //
    // New rule:
    // - if the dataset only contains generation1, keep the old Gen1-only mode;
    // - if multiple generation folders exist, import the whole tree.
    List<Path> generationDirs = Files.walk(folder)
            .filter(Files::isDirectory)
            .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).matches("generation\\d+[a-z]?"))
            .toList();

    boolean gen1OnlyDataset = !generationDirs.isEmpty()
            && generationDirs.stream().allMatch(p -> p.getFileName().toString().equalsIgnoreCase("generation1"));

    Optional<Path> gen1 = gen1OnlyDataset ? findBestGenerationFolder(folder, "generation1") : Optional.empty();
    Path importRoot = gen1.orElse(folder);

    // Remember whether we are running in a true Gen1-only dataset. This informs Showdown imports.
    autoGen1SpeciesDetected = gen1OnlyDataset && gen1.isPresent();

    List<Path> files = Files.walk(importRoot)
            .filter(p -> p.toString().endsWith(".json"))
            .toList();

    EvolutionImportReport report = new EvolutionImportReport();
    report.target = gen1.isPresent() ? "generation1" : "all";
    report.importRoot = importRoot.toString();

    MoveLearnsetReport reportMoveLearnset = null;
    if (gen1.isPresent()) {
        reportMoveLearnset = new MoveLearnsetReport();
        reportMoveLearnset.target = "generation1";
        reportMoveLearnset.importRoot = importRoot.toString();
        lastGen1LearnsetReport = reportMoveLearnset;
    }


    int imported = 0;
    for (Path p : files) {
        try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
            JsonObject o = gson.fromJson(r, JsonObject.class);
            if (o == null) continue;

            String id = p.getFileName().toString().replace(".json", "").toLowerCase(Locale.ROOT);
            // Some files may contain explicit identifier fields; try them first
            id = getStringAny(o, "id", "identifier", "speciesId", "name").orElse(id).toLowerCase(Locale.ROOT);
            // normalize like cobblemon:bulbasaur -> bulbasaur
            if (id.contains(":")) id = id.substring(id.indexOf(':') + 1);
            if (id.isBlank()) continue;

            String name = getStringAny(o, "displayName", "name", "pokemonName").orElse(titleCase(id));
            // Some cobblemon 'name' fields can be namespaced; keep pretty
            if (name.contains(":")) name = titleCase(name.substring(name.indexOf(':') + 1));

            List<String> types = new ArrayList<>();
            // types can be ["grass","poison"] or primaryType/secondaryType
            if (o.has("types") && o.get("types").isJsonArray()) {
                for (JsonElement e : o.getAsJsonArray("types")) {
                    String t = e.getAsString().toLowerCase(Locale.ROOT);
                    if (t.contains(":")) t = t.substring(t.indexOf(':') + 1);
                    if (!t.isBlank()) types.add(t);
                }
            } else {
                Optional<String> primaryType = getStringAny(o, "primaryType", "type1");
                if (primaryType.isPresent()) {
                    types.add(stripNamespace(primaryType.get()));
                }
                Optional<String> secondaryType = getStringAny(o, "secondaryType", "type2");
                if (secondaryType.isPresent()) {
                    String tt = stripNamespace(secondaryType.get());
                    if (!tt.isBlank() && !types.contains(tt)) types.add(tt);
                }
            }
            if (types.isEmpty()) types = List.of("normal");

            Map<String, Integer> stats = new HashMap<>();
            if (o.has("baseStats") && o.get("baseStats").isJsonObject()) {
                JsonObject bs = o.getAsJsonObject("baseStats");
                stats.put("hp", getIntAny(bs, "hp", "HP").orElse(50));
                stats.put("atk", getIntAny(bs, "attack", "atk").orElse(50));
                stats.put("def", getIntAny(bs, "defence", "defense", "def").orElse(50));
                stats.put("spa", getIntAny(bs, "specialAttack", "special_attack", "spAttack", "spa").orElse(50));
                stats.put("spd", getIntAny(bs, "specialDefence", "specialDefense", "special_defense", "spDefence", "spDefense", "spd").orElse(50));
                stats.put("spe", getIntAny(bs, "speed", "spe").orElse(50));
            } else {
                // fallback: try top-level keys
                stats.put("hp", getIntAny(o, "hp").orElse(50));
                stats.put("atk", getIntAny(o, "attack").orElse(50));
                stats.put("def", getIntAny(o, "defence", "defense").orElse(50));
                stats.put("spa", getIntAny(o, "specialAttack", "special_attack").orElse(50));
                stats.put("spd", getIntAny(o, "specialDefence", "specialDefense", "special_defense").orElse(50));
                stats.put("spe", getIntAny(o, "speed").orElse(50));
            }

            Map<String,Integer> evYields = parseEvYieldsCobblemon(o);

            int catchRate = getIntAny(o, "catchRate", "catch_rate", "baseCatchRate").orElse(45);

            // Experience fields (optional in Cobblemon JSON). Fallbacks match common defaults.
            int baseExpYield = getIntAny(o, "baseExperienceYield", "baseExpYield", "experienceYield", "expYield").orElse(64);
            String expGroup = getStringAny(o, "experienceGroup", "expGroup", "growthRate", "growth_rate")
                    .map(String::toUpperCase)
                    .orElse("MEDIUM_FAST");

            int minLv = 2;
            int maxLv = 30;
            if (o.has("levelRange") && o.get("levelRange").isJsonObject()) {
                JsonObject lr = o.getAsJsonObject("levelRange");
                minLv = getIntAny(lr, "min", "minimum").orElse(minLv);
                maxLv = getIntAny(lr, "max", "maximum").orElse(maxLv);
            }

            List<Evolution> evolutions = parseEvolutions(o);
            Map<Integer, List<String>> levelUpMoves = parseLearnsetLevel(o, reportMoveLearnset, id);

            // Breeding data (optional in different sources)
            List<String> eggGroups = parseEggGroups(o);
            double maleRatio = parseMaleRatio(o);

            // Record unsupported evolution variants when importing generation1
            if (gen1.isPresent() && evolutions != null && !evolutions.isEmpty()) {
                for (Evolution ev : evolutions) {
                    if (ev == null) continue;
                    String variant = ev.variant() == null ? "" : ev.variant().toLowerCase(Locale.ROOT);
                    boolean supported = "level_up".equalsIgnoreCase(variant) && ev.minLevel() > 0 && ev.result() != null && !ev.result().isBlank();
                    if (!supported) {
                        report.addSkipped(id, ev);
                    } else {
                        report.addSupported();
                    }
                }
            }

            double weightKg = 0.0;
            if (o.has("weight") && o.get("weight").isJsonPrimitive()) {
                try { weightKg = o.get("weight").getAsDouble(); } catch (Exception ignored) {}
            }
            Species s = new Species(id, name, types, stats, evYields, evolutions, levelUpMoves, baseExpYield, expGroup, catchRate, minLv, maxLv,
                    weightKg, eggGroups, maleRatio, getIntAny(o, "baseFriendship", "base_friendship", "friendship", "happiness").orElse(70));
            speciesById.put(id, s);
            imported++;
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to parse species file: " + p.getFileName() + " => " + ex.getMessage());
        }
    }

    plugin.getLogger().info("Loaded species: " + imported + " from " + importRoot + (gen1.isPresent() ? " (auto-detected Gen1)" : ""));

    if (gen1.isPresent()) {
        writeEvolutionReport(report);
    }
}

private Optional<String> getStringAny(JsonObject o, String... keys) {
    for (String k : keys) {
        if (o.has(k) && !o.get(k).isJsonNull()) {
            try { return Optional.of(o.get(k).getAsString()); } catch (Exception ignored) {}
        }
    }
    return Optional.empty();
}
private Optional<Integer> getIntAny(JsonObject o, String... keys) {
    for (String k : keys) {
        if (o.has(k) && !o.get(k).isJsonNull()) {
            try { return Optional.of(o.get(k).getAsInt()); } catch (Exception ignored) {}
        }
    }
    return Optional.empty();
}
private String stripNamespace(String s) {
    s = s.toLowerCase();
    if (s.contains(":")) s = s.substring(s.indexOf(':') + 1);
    return s;
}
private String titleCase(String id) {
    String[] parts = id.replace('_',' ').replace('-',' ').split("\\s+");
    StringBuilder sb = new StringBuilder();
    for (String p : parts) {
        if (p.isBlank()) continue;
        sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)).append(' ');
    }
    return sb.toString().trim();
}

    private Map<Integer, List<String>> parseLearnsetLevel(JsonObject o, MoveLearnsetReport report, String speciesId) {
        Map<Integer, List<String>> map = new TreeMap<>();
        if (o == null || !o.has("moves") || !o.get("moves").isJsonArray()) return map;
        for (JsonElement e : o.getAsJsonArray("moves")) {
            if (!e.isJsonPrimitive()) continue;
            String s = e.getAsString();
            if (s == null || s.isBlank()) continue;
            int colon = s.indexOf(':');
            if (colon <= 0 || colon >= s.length()-1) continue;
            String left = s.substring(0, colon).trim().toLowerCase();
            String right = stripNamespace(s.substring(colon+1).trim());
            if (right.isBlank()) continue;

            // Level learn: "5:quickattack"
            try {
                int level = Integer.parseInt(left);
                map.computeIfAbsent(level, k -> new ArrayList<>()).add(right);
                if (report != null) report.addLevelMove(speciesId, level, right);
            } catch (NumberFormatException nfe) {
                // other methods: egg/tm/tutor/etc.
                if (report != null) report.addSkipped(speciesId, left, right);
            }
        }
        return map;
    }

    private void finalizeAndWriteLearnsetReport(MoveLearnsetReport report) {
        if (report == null) return;
        // Identify missing move definitions (moves that appear in learnset but no JSON definition exists)
        for (String moveId : report.referencedMoves) {
            if (moveId == null) continue;
            if (movesById.containsKey(moveId.toLowerCase())) continue;
            report.missingMoveDefs.add(moveId.toLowerCase());
        }
        writeLearnsetReport(report);
    }

    private void writeLearnsetReport(MoveLearnsetReport report) {
        try {
            Path reports = plugin.getDataFolder().toPath().resolve("reports");
            if (!Files.exists(reports)) Files.createDirectories(reports);
            Path out = reports.resolve("gen1_learnset_unsupported.yml");

            StringBuilder sb = new StringBuilder();
            sb.append("generatedAt: \"").append(java.time.OffsetDateTime.now()).append("\"\n");
            sb.append("importRoot: \"").append(escapeYaml(report.importRoot)).append("\"\n");
            sb.append("target: \"").append(report.target).append("\"\n");
            sb.append("supportedMethods:\n");
            sb.append("  - level\n");
            sb.append("counts:\n");
            sb.append("  levelEntries: " + report.levelEntries + "\n");
            sb.append("  skippedEntries: " + report.skippedTotal() + "\n");
            sb.append("  missingMoveDefs: " + report.missingMoveDefs.size() + "\n");

            sb.append("missingMoveDefs:\n");
            if (report.missingMoveDefs.isEmpty()) {
                sb.append("  []\n");
            } else {
                for (String m : report.missingMoveDefs) sb.append("  - " + m + "\n");
            }

            sb.append("skipped:\n");
            if (report.skipped.isEmpty()) {
                sb.append("  {}\n");
            } else {
                for (Map.Entry<String, List<String>> en : report.skipped.entrySet()) {
                    sb.append("  " + en.getKey() + ":\n");
                    for (String line : en.getValue()) {
                        sb.append("    - " + line + "\n");
                    }
                }
            }

            Files.writeString(out, sb.toString(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            plugin.getLogger().info("[Gen1 Learnset] level entries: " + report.levelEntries
                    + ", skipped: " + report.skippedTotal()
                    + ", missing move defs: " + report.missingMoveDefs.size()
                    + ", report: " + out.toAbsolutePath());
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to write learnset report: " + e.getMessage());
        }
    }

private List<Evolution> parseEvolutions(JsonObject o) {
    if (o == null || !o.has("evolutions") || !o.get("evolutions").isJsonArray()) return List.of();
    List<Evolution> list = new ArrayList<>();
    for (JsonElement el : o.getAsJsonArray("evolutions")) {
        if (!el.isJsonObject()) continue;
        JsonObject eo = el.getAsJsonObject();
        String variant = getStringAny(eo, "variant", "type").orElse("level_up");
        String result = getStringAny(eo, "result", "to", "evolvesTo").orElse(null);
        int minLevel = getIntAny(eo, "minLevel", "level").orElse(0);

        // Cobblemon style: requirements can be an object or an array of requirement objects.
        if (minLevel <= 0 && eo.has("requirements")) {
            JsonElement reqEl = eo.get("requirements");
            if (reqEl.isJsonObject()) {
                JsonObject req = reqEl.getAsJsonObject();
                minLevel = getIntAny(req, "minLevel", "level", "minimumLevel", "min_level").orElse(0);
            } else if (reqEl.isJsonArray()) {
                for (JsonElement re : reqEl.getAsJsonArray()) {
                    if (!re.isJsonObject()) continue;
                    JsonObject ro = re.getAsJsonObject();
                    String rVar = getStringAny(ro, "variant", "type").orElse("").toLowerCase(Locale.ROOT);
                    // Cobblemon commonly uses: {"variant":"level","minLevel":16}
                    if (rVar.contains("level")) {
                        int ml = getIntAny(ro, "minLevel", "level", "minimumLevel", "min_level").orElse(0);
                        if (ml > 0) {
                            minLevel = ml;
                            break;
                        }
                    }
                }
            }
        }

        // normalize result like cobblemon:ivysaur -> ivysaur
        if (result != null) result = stripNamespace(result).toLowerCase(Locale.ROOT);

        List<String> learn = new ArrayList<>();
        if (eo.has("learnableMoves") && eo.get("learnableMoves").isJsonArray()) {
            for (JsonElement me : eo.getAsJsonArray("learnableMoves")) {
                String mid = me.getAsString();
                mid = stripNamespace(mid).toLowerCase(Locale.ROOT);
                if (!mid.isBlank()) learn.add(mid);
            }
        }
        if (result != null && !result.isBlank()) {
            list.add(new Evolution(variant, minLevel, result, learn.isEmpty() ? List.of() : learn));
        }
    }
    return list;
}

private List<Evolution> parseEvolutionsCobblemon(JsonObject o) {
    // Cobblemon species JSON also uses "evolutions": [...]
    return parseEvolutions(o);
}

private Map<String,Integer> parseEvYields(JsonObject o) {
    Map<String,Integer> ev = new HashMap<>();
    if (o == null) return ev;
    // Accept keys: evYields {hp:0, atk:1,...} or evYield / effortYield
    JsonObject src = null;
    if (o.has("evYields") && o.get("evYields").isJsonObject()) src = o.getAsJsonObject("evYields");
    else if (o.has("evYield") && o.get("evYield").isJsonObject()) src = o.getAsJsonObject("evYield");
    else if (o.has("effortYield") && o.get("effortYield").isJsonObject()) src = o.getAsJsonObject("effortYield");

    if (src != null) {
        ev.put("hp", getIntAny(src, "hp", "HP").orElse(0));
        ev.put("atk", getIntAny(src, "atk", "attack").orElse(0));
        ev.put("def", getIntAny(src, "def", "defense", "defence").orElse(0));
        ev.put("spa", getIntAny(src, "spa", "spA", "specialAttack", "special_attack").orElse(0));
        ev.put("spd", getIntAny(src, "spd", "spD", "specialDefense", "specialDefence", "special_defense").orElse(0));
        ev.put("spe", getIntAny(src, "spe", "speed").orElse(0));
    }
    // Ensure non-negative
    for (String k : List.of("hp","atk","def","spa","spd","spe")) {
        ev.put(k, Math.max(0, ev.getOrDefault(k, 0)));
    }
    return ev;
}

private Map<String,Integer> parseEvYieldsCobblemon(JsonObject o) {
    // Cobblemon files can use slightly different naming; reuse parseEvYields first.
    Map<String,Integer> ev = parseEvYields(o);
    if (ev.values().stream().anyMatch(v -> v != null && v > 0)) return ev;

    // Some Cobblemon versions use "evYield" at top-level with keys matching main-series names.
    if (o != null && o.has("evYield") && o.get("evYield").isJsonObject()) {
        JsonObject src = o.getAsJsonObject("evYield");
        ev.put("hp", getIntAny(src, "hp", "HP").orElse(0));
        ev.put("atk", getIntAny(src, "attack", "atk").orElse(0));
        ev.put("def", getIntAny(src, "defence", "defense", "def").orElse(0));
        ev.put("spa", getIntAny(src, "specialAttack", "special_attack", "spa").orElse(0));
        ev.put("spd", getIntAny(src, "specialDefence", "specialDefense", "special_defense", "spd").orElse(0));
        ev.put("spe", getIntAny(src, "speed", "spe").orElse(0));
    }
    for (String k : List.of("hp","atk","def","spa","spd","spe")) {
        ev.put(k, Math.max(0, ev.getOrDefault(k, 0)));
    }
    return ev;
}

    private Optional<Path> findBestGenerationFolder(Path root, String folderName) throws IOException {
        List<Path> candidates = Files.walk(root)
                .filter(Files::isDirectory)
                .filter(p -> p.getFileName().toString().equalsIgnoreCase(folderName))
                .toList();
        if (candidates.isEmpty()) return Optional.empty();

        Path best = null;
        int bestCount = -1;
        for (Path c : candidates) {
            int count;
            try {
                count = (int) Files.walk(c).filter(p -> p.toString().endsWith(".json")).count();
            } catch (Exception e) {
                continue;
            }
            if (count > bestCount) {
                bestCount = count;
                best = c;
            }
        }
        return Optional.ofNullable(best);
    }

    private void writeEvolutionReport(EvolutionImportReport report) {
        try {
            Path reports = plugin.getDataFolder().toPath().resolve("reports");
            if (!Files.exists(reports)) Files.createDirectories(reports);
            Path out = reports.resolve("gen1_evolution_unsupported.yml");

            StringBuilder sb = new StringBuilder();
            sb.append("generatedAt: \"").append(java.time.OffsetDateTime.now()).append("\"\n");
            sb.append("importRoot: \"").append(escapeYaml(report.importRoot)).append("\"\n");
            sb.append("target: \"").append(report.target).append("\"\n");
            sb.append("supportedVariants:\n");
            sb.append("  - level_up\n");
            sb.append("counts:\n");
            sb.append("  supportedLevelUp: " + report.supportedLevelUp + "\n");
            sb.append("  skipped: " + report.skippedTotal() + "\n");
            sb.append("skipped:\n");

            if (report.skipped.isEmpty()) {
                sb.append("  {}\n");
            } else {
                for (Map.Entry<String, List<String>> en : report.skipped.entrySet()) {
                    sb.append("  " + en.getKey() + ":\n");
                    for (String line : en.getValue()) {
                        sb.append("    - " + line + "\n");
                    }
                }
            }

            Files.writeString(out, sb.toString(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            // Console summary
            plugin.getLogger().info("[Gen1 Evolution] supported level_up: " + report.supportedLevelUp
                    + ", skipped: " + report.skippedTotal()
                    + ", report: " + out.toAbsolutePath());
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to write evolution report: " + e.getMessage());
        }
    }

    private static String escapeYaml(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static final class MoveLearnsetReport {
        String target;
        String importRoot;
        int levelEntries = 0;
        final Map<String, List<String>> skipped = new TreeMap<>();
        final Set<String> referencedMoves = new TreeSet<>();
        final Set<String> missingMoveDefs = new TreeSet<>();

        void addLevelMove(String speciesId, int level, String moveId) {
            levelEntries++;
            referencedMoves.add(moveId.toLowerCase());
        }

        void addSkipped(String speciesId, String method, String moveId) {
            if (speciesId == null) speciesId = "unknown";
            String line = "{method: \"" + escapeYaml(method) + "\", move: \"" + escapeYaml(moveId) + "\"}";
            skipped.computeIfAbsent(speciesId, k -> new ArrayList<>()).add(line);
            referencedMoves.add(moveId.toLowerCase());
        }

        int skippedTotal() {
            int total = 0;
            for (List<String> v : skipped.values()) total += v.size();
            return total;
        }
    }

    private static Map<String, Object> mapOf(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }


    private static List<Map<String, Object>> ensureCoreGen1Effects(String moveId, List<Map<String, Object>> effects) {
        List<Map<String, Object>> out = (effects == null) ? new ArrayList<>() : new ArrayList<>(effects);
        String id = (moveId == null) ? "" : moveId.toLowerCase(Locale.ROOT);

        // These are implemented in MoveEngine, but old/local move JSONs might not declare effects at all.
        if ("conversion".equals(id)) addEffectIfMissing(out, "conversion");
        if ("mirrormove".equals(id)) addEffectIfMissing(out, "mirror_move");
        if ("transform".equals(id)) addEffectIfMissing(out, "transform");
        if ("mimic".equals(id)) addEffectIfMissing(out, "mimic");
        if ("metronome".equals(id)) addEffectIfMissing(out, "metronome");
        if ("whirlwind".equals(id)) addEffectIfMissing(out, "phaze");
        if ("roar".equals(id)) addEffectIfMissing(out, "phaze");
        if ("dragontail".equals(id) || "dragon_tail".equals(id)) addEffectIfMissing(out, "phaze");
        if ("circlethrow".equals(id) || "circle_throw".equals(id)) addEffectIfMissing(out, "phaze");
        if ("teleport".equals(id)) addEffectIfMissing(out, "teleport");
        if ("focusenergy".equals(id)) addEffectIfMissing(out, "focus_energy");

        return out;
    }

    private static void addEffectIfMissing(List<Map<String, Object>> effects, String effectId) {
        if (effects == null) return;
        for (Map<String, Object> e : effects) {
            Object v = (e == null) ? null : e.get("id");
            if (v != null && effectId.equalsIgnoreCase(String.valueOf(v))) return;
        }
        effects.add(mapOf("id", effectId));
    }

    /** Map Pokemon Showdown status codes to our internal status ids. */
    private static String normalizeStatusCode(String code) {
        if (code == null) return null;
        return switch (code.toLowerCase()) {
            case "psn" -> "poison";
            case "tox" -> "toxic";
            case "par" -> "paralyze";
            case "brn" -> "burn";
            case "slp" -> "sleep";
            case "frz" -> "freeze";
            default -> null;
        };
    }

    private static String normalizeBoostStat(String key) {
        if (key == null) return null;
        return switch (key.toLowerCase()) {
            case "atk" -> "atk";
            case "def" -> "def";
            case "spa" -> "spa";
            case "spd" -> "spd";
            case "spe" -> "spe";
            case "accuracy", "acc" -> "accuracy";
            case "evasion", "eva" -> "evasion";
            default -> null;
        };
    }

    private static final class MoveRawImportReport {
        String importRoot;
        int parsedShowdownMoves = 0;
        int parsedInternalMoves = 0;
        int failedFiles = 0;
        final List<String> skippedFiles = new ArrayList<>();
        final List<String> notes = new ArrayList<>();
    }

    private static final class EvolutionImportReport {
        String target;
        String importRoot;
        int supportedLevelUp = 0;
        final Map<String, List<String>> skipped = new TreeMap<>();

        void addSupported() {
            supportedLevelUp++;
        }

        void addSkipped(String speciesId, Evolution ev) {
            if (speciesId == null) speciesId = "unknown";
            String variant = ev.variant() == null ? "unknown" : ev.variant();
            String result = ev.result() == null ? "unknown" : ev.result();
            int minLevel = ev.minLevel();
            String learn = (ev.learnableMoves() == null || ev.learnableMoves().isEmpty()) ? "" : (", learn=" + ev.learnableMoves());
            String line = "{variant: \"" + escapeYaml(variant) + "\", result: \"" + escapeYaml(result) + "\", minLevel: " + minLevel + learn + "}";
            skipped.computeIfAbsent(speciesId, k -> new ArrayList<>()).add(line);
        }

        int skippedTotal() {
            int total = 0;
            for (List<String> v : skipped.values()) total += v.size();
            return total;
        }
    }

        // ---------------- Gen1 coverage report ----------------

        public Path writeGen1CoverageReport() {
            try {
                Path reports = plugin.getDataFolder().toPath().resolve("reports");
                if (!java.nio.file.Files.exists(reports)) java.nio.file.Files.createDirectories(reports);
                Path out = reports.resolve("gen1_moves_coverage.yml");

                java.util.Set<String> supportedKinds = java.util.Set.of(
                        "heal","drain","set_status","screen","leech_seed","stat_stage",
                        "multi_hit","recoil","two_turn","confusion","flinch",
                        "fixed_damage","ohko","selfdestruct","rest","conversion","mirror_move",
                        "transform","mimic","metronome",
                        "substitute","recharge","disable","partial_trap","bide","counter","haze","mist","phaze","teleport","focus_energy"
                );

                java.util.Set<String> allowEmptyStatus = java.util.Set.of(
                        "splash"
                );

                int total = 0;
                int supported = 0;
                java.util.List<String> unsupportedMoves = new java.util.ArrayList<>();
                java.util.Map<String, Integer> missingKindCounts = new java.util.LinkedHashMap<>();

                java.util.List<Move> gen1 = movesById.values().stream()
                        .filter(m -> m != null && m.numSafe() >= 1 && m.numSafe() <= 165)
                        .sorted(java.util.Comparator.comparingInt(Move::numSafe))
                        .toList();

                // Find missing Gen1 move numbers (should be 1..165)
                boolean[] seen = new boolean[166];
                java.util.Map<Integer, String> numToId = new java.util.HashMap<>();
                for (Move m1 : gen1) { int n = m1.numSafe(); if (n>=1 && n<=165) { seen[n]=true; if(!numToId.containsKey(n)) numToId.put(n, m1.id()); } }
                java.util.List<Integer> missingNums = new java.util.ArrayList<>();
                for (int n=1;n<=165;n++) if(!seen[n]) missingNums.add(n);

                total = gen1.size();

                for (Move m : gen1) {
                    java.util.List<java.util.Map<String,Object>> efs = new java.util.ArrayList<>();
                    if (m.effectsSafe() != null) efs.addAll(m.effectsSafe());
                    if ((efs == null || efs.isEmpty()) && m.effect() != null && !m.effect().isEmpty()) efs.add(m.effect());

                    java.util.List<String> missingKinds = new java.util.ArrayList<>();
                    for (java.util.Map<String,Object> ef : efs) {
                        if (ef == null || ef.isEmpty()) continue;
                        String kind = String.valueOf(ef.getOrDefault("kind", ef.getOrDefault("id", ""))).toLowerCase();
                        if (kind.isBlank()) continue;
                        if (!supportedKinds.contains(kind)) {
                            missingKinds.add(kind);
                            missingKindCounts.put(kind, missingKindCounts.getOrDefault(kind, 0) + 1);
                        }
                    }

                    boolean ok = true;
                    if (!missingKinds.isEmpty()) ok = false;

                    if ("status".equalsIgnoreCase(m.category())) {
                        if ((efs == null || efs.isEmpty()) && !allowEmptyStatus.contains(m.id())) {
                            ok = false;
                            missingKinds.add("no_effects");
                            missingKindCounts.put("no_effects", missingKindCounts.getOrDefault("no_effects", 0) + 1);
                        }
                    }

                    if (ok) supported++;
                    else {
                        String reason = missingKinds.isEmpty() ? "unknown" : String.join(",", missingKinds);
                        unsupportedMoves.add("#" + m.numSafe() + " " + m.id() + " (" + m.name() + ") -> " + reason);
                    }
                }

                StringBuilder sb = new StringBuilder();
                sb.append("generatedAt: \"").append(java.time.OffsetDateTime.now()).append("\"\n");
                sb.append("gen1TotalMoves: 165\n");
                sb.append("gen1ImportedMoves: ").append(total).append("\n");
                sb.append("supportedMoves: ").append(supported).append("\n");
                sb.append("unsupportedMoves: ").append(Math.max(0, total - supported)).append("\n");

                if (!missingNums.isEmpty()) {
                    sb.append("missingGen1Nums: ").append(missingNums.size()).append("\n");
                    sb.append("missingGen1NumList: ").append(missingNums).append("\n");
                } else {
                    sb.append("missingGen1Nums: 0\n");
                }

                if (!missingKindCounts.isEmpty()) {
                    sb.append("missingKinds:\n");
                    for (var e : missingKindCounts.entrySet()) {
                        sb.append("  ").append(escapeYaml(e.getKey())).append(": ").append(e.getValue()).append("\n");
                    }
                }

                if (!unsupportedMoves.isEmpty()) {
                    sb.append("unsupportedMoveList:\n");
                    for (String s : unsupportedMoves) {
                        sb.append("  - \"").append(escapeYaml(s)).append("\"\n");
                    }
                }
                java.nio.file.Files.writeString(out, sb.toString(), java.nio.charset.StandardCharsets.UTF_8,
                        java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);

                return out;
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to write Gen1 coverage report: " + e.getMessage());
                return null;
            }
        }

    /** Load abilities from Pokemon Showdown pokedex.json (in moves_raw). */
    private void loadPokedexAbilities(java.nio.file.Path pokedexJson) {
        normalAbilitiesBySpecies.clear();
        hiddenAbilityBySpecies.clear();
        try {
            if (pokedexJson == null || !java.nio.file.Files.exists(pokedexJson)) return;
            String raw = java.nio.file.Files.readString(pokedexJson);
            com.google.gson.JsonObject obj = com.google.gson.JsonParser.parseString(raw).getAsJsonObject();
            for (String key : obj.keySet()) {
                com.google.gson.JsonObject sp = obj.getAsJsonObject(key);
                if (sp == null) continue;
                // key is showdown id
                String sid = key.toLowerCase(java.util.Locale.ROOT);
                com.google.gson.JsonObject abs = sp.has("abilities") ? sp.getAsJsonObject("abilities") : null;
                if (abs == null) continue;
                java.util.List<String> normals = new java.util.ArrayList<>();
                String hidden = null;
                for (String ak : abs.keySet()) {
                    try {
                        String v = abs.get(ak).getAsString();
                        if (v == null) continue;
                        String id = v.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", "");
                        if (id.isBlank()) continue;
                        if ("h".equalsIgnoreCase(ak)) {
                            hidden = id;
                        } else {
                            if (!normals.contains(id)) normals.add(id);
                        }
                    } catch (Throwable ignored) {}
                }
                if (!normals.isEmpty()) normalAbilitiesBySpecies.put(sid, normals);
                if (hidden != null && !hidden.isBlank()) hiddenAbilityBySpecies.put(sid, hidden);
            }
        } catch (Throwable ignored) {}
    }

    /**
     * Pick an ability id for a species, using moves_raw/pokedex.json.
     * If none found, returns null.
     */
    public String pickAbilityIdForSpecies(String speciesId, boolean allowHidden) {
        if (speciesId == null) return null;
        String sid = speciesId.toLowerCase(java.util.Locale.ROOT);
        String hid = hiddenAbilityBySpecies.get(sid);
        java.util.List<String> normals = normalAbilitiesBySpecies.get(sid);

        if (allowHidden && hid != null && !hid.isBlank()) {
            return hid;
        }
        if (normals == null || normals.isEmpty()) {
            // Fallback: if species only has hidden in data, return it.
            return (hid == null || hid.isBlank()) ? null : hid;
        }
        return normals.get(Util.RND.nextInt(normals.size()));
    }
    /** Get normal (non-hidden) ability ids for a species (from moves_raw/pokedex.json). */
    public java.util.List<String> getNormalAbilityIds(String speciesId) {
        if (speciesId == null) return java.util.List.of();
        java.util.List<String> v = normalAbilitiesBySpecies.get(speciesId.toLowerCase(java.util.Locale.ROOT));
        return v == null ? java.util.List.of() : java.util.List.copyOf(v);
    }

    /** Get hidden ability id for a species (from moves_raw/pokedex.json), or null. */
    public String getHiddenAbilityId(String speciesId) {
        if (speciesId == null) return null;
        return hiddenAbilityBySpecies.get(speciesId.toLowerCase(java.util.Locale.ROOT));
    }

    /** Whether an ability id is the hidden ability for this species. */
    public boolean isHiddenAbility(String speciesId, String abilityId) {
        if (speciesId == null || abilityId == null) return false;
        String h = getHiddenAbilityId(speciesId);
        return h != null && h.equalsIgnoreCase(abilityId);
    }


    /** Roll whether a wild Pokemon should get its hidden ability ("梦特"). */
    public boolean rollHiddenAbilityForWild() {
        try {
            if (plugin == null) return false;
            boolean enabled = plugin.getConfig().getBoolean("abilities.hidden-enabled", true);
            if (!enabled) return false;
            double chance = plugin.getConfig().getDouble("abilities.hidden-chance", 1.0 / 150.0);
            if (chance <= 0) return false;
            return Util.RND.nextDouble() < chance;
        } catch (Throwable ignored) {
            return false;
        }
    }

}
