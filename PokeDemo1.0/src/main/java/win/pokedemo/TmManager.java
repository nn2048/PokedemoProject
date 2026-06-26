package win.pokedemo;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * TM/HM support.
 *
 * - Maps TM/HM item ids (tm01..tm100, hm01..hm08) to move ids.
 * - Built-in default mapping keeps the original Gen1 list.
 * - Additional/all-gen numbering can be supplied by plugins/PokeDemo/moves_raw/tm_moves.json
 *   with a simple object like {"tm51":"roost","hm06":"rocksmash"}.
 * - Reads Pokemon Showdown learnsets files to determine which species can learn which machine moves.
 */
public class TmManager {
    private final PokeDemoPlugin plugin;
    private final Dex dex;

    // tmId -> moveId
    private final Map<String, String> tmMoveById = new HashMap<>();
    // speciesId -> set(moveId) allowed via TM/HM in selected gen
    private final Map<String, Set<String>> tmCompatBySpecies = new HashMap<>();
    // speciesId -> set(moveId) allowed via Move Tutor in selected gen
    private final Map<String, Set<String>> tutorCompatBySpecies = new HashMap<>();

    private boolean loadedOnce = false;
    private Path lastLearnsetsPath = null;
    private int lastGenFilter = 1;

    public TmManager(PokeDemoPlugin plugin, Dex dex) {
        this.plugin = plugin;
        this.dex = dex;
        initDefaultTmMap();
    }

    public String moveForTm(String tmId) {
        if (tmId == null) return null;
        String id = tmId.toLowerCase(Locale.ROOT).trim();
        if (id.startsWith("tm_")) return id.substring(3).replaceAll("[^a-z0-9]", "");
        if (id.startsWith("mt_")) return id.substring(3).replaceAll("[^a-z0-9]", "");
        if (id.startsWith("tutor_")) return id.substring(6).replaceAll("[^a-z0-9]", "");
        return tmMoveById.get(id);
    }

    /** Whether species can learn the given TM/HM move according to Showdown learnsets.json. */
    public boolean canLearnTm(String speciesId, String moveId) {
        if (speciesId == null || moveId == null) return false;
        String sp = speciesId.toLowerCase(Locale.ROOT);
        String mv = moveId.toLowerCase(Locale.ROOT);

        // Lazy load if needed
        if (tmCompatBySpecies.isEmpty() && !loadedOnce) {
            ensureLoaded();
        }

        Set<String> set = tmCompatBySpecies.get(sp);
        if (set == null && lastLearnsetsPath != null && Files.exists(lastLearnsetsPath) && lastLearnsetsPath.toString().endsWith(".json")) {
            // On-demand load for this single species (handles weird learnsets structures too)
            try {
                loadSingleSpeciesFromLearnsetsJson(lastLearnsetsPath, lastGenFilter, sp);
            } catch (Exception ignored) {}
            set = tmCompatBySpecies.get(sp);
        }
        return set != null && set.contains(mv);
    }


    /** Whether species can learn the given move via Move Tutor (Showdown tag *T). */
    public boolean canLearnTutor(String speciesId, String moveId) {
        if (speciesId == null || moveId == null) return false;
        String sp = speciesId.toLowerCase(Locale.ROOT);
        String mv = moveId.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        if ((tmCompatBySpecies.isEmpty() && tutorCompatBySpecies.isEmpty()) && !loadedOnce) ensureLoaded();
        Set<String> set = tutorCompatBySpecies.get(sp);
        if (set == null && lastLearnsetsPath != null && Files.exists(lastLearnsetsPath)) {
            try {
                if (lastLearnsetsPath.toString().endsWith(".json")) loadSingleSpeciesFromLearnsetsJson(lastLearnsetsPath, lastGenFilter, sp);
            } catch (Exception ignored) {}
            set = tutorCompatBySpecies.get(sp);
        }
        return set != null && set.contains(mv);
    }

    /** Load/override TM/HM number -> move mappings from moves_raw/tm_moves.json when present. */
    private void tryLoadExternalTmMoveMap(Path movesRaw) {
        if (movesRaw == null) return;
        Path f = movesRaw.resolve("tm_moves.json");
        if (!Files.exists(f)) return;
        try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
            int loaded = 0;
            for (var en : root.entrySet()) {
                String key = en.getKey() == null ? "" : en.getKey().toLowerCase(Locale.ROOT).replace("_", "").replace("-", "").trim();
                String val;
                try {
                    val = en.getValue().getAsString();
                } catch (Exception ignored) {
                    continue;
                }
                if (!key.matches("(tm|hm)\\d{1,3}")) continue;
                if (val == null || val.isBlank()) continue;
                tmMoveById.put(key, val.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", ""));
                loaded++;
            }
            if (loaded > 0) {
                plugin.getLogger().info("[PokeDemo] TM move map loaded: entries=" + loaded + ", file=" + f.getFileName());
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[PokeDemo] Failed to load tm_moves.json: " + e.getMessage());
        }
    }

    /**
     * Ensure TM compatibility data is loaded. If missing, try to download/ensure learnsets.json and load it.
     * @return true if data is available after this call.
     */
    public boolean ensureLoaded() {
        if (!tmCompatBySpecies.isEmpty()) return true;
        try {
            // OFFLINE MODE: do not attempt any network downloads here.
            // We only load TM/HM compatibility from local files under plugins/PokeDemo/moves_raw/.
            int gen = plugin.getConfig().getInt("showdown.learnsets-generation", 0);

            Path movesRaw = plugin.getDataFolder().toPath().resolve("moves_raw");
            Path fCompat = movesRaw.resolve("tm_compat_gen1.json");
            Path fTs = movesRaw.resolve("learnsets.ts");
            Path fJson = movesRaw.resolve("learnsets.json");

            // Allow all-gen / custom machine numbering overrides.
            tryLoadExternalTmMoveMap(movesRaw);

            lastGenFilter = gen;

            if (Files.exists(fTs)) {
                lastLearnsetsPath = fTs;
                loadFromLearnsetsTs(fTs, gen);
            } else if (Files.exists(fJson)) {
                // NOTE: play.pokemonshowdown.com/data/learnsets.json usually contains only level-up data (1L)
                // and may omit machine tags (1M/1T). We keep the fallback for users who have a full learnsets.json,
                // but if it yields 0 species, the user must provide learnsets.ts or a compat json.
                lastLearnsetsPath = fJson;
                loadFromLearnsetsJson(fJson, gen);
            } else if (Files.exists(fCompat)) {
                // Legacy Gen1-only offline fallback.
                lastLearnsetsPath = fCompat;
                loadFromCompatJson(fCompat);
            } else {
                loadedOnce = true;
                return false;
            }

            loadedOnce = true;

            if (tmCompatBySpecies.isEmpty()) {
                plugin.getLogger().warning("[PokeDemo] TM compatibility is empty. Provide moves_raw/learnsets.ts (Pokemon Showdown source) or moves_raw/tm_compat_gen1.json.");
            }

            return !tmCompatBySpecies.isEmpty();
        } catch (Exception e) {
            plugin.getLogger().warning("[PokeDemo] ensureLoaded TM compat failed: " + e.getMessage());
            loadedOnce = true;
            return false;
        }
    }

    /** Load TM compatibility from Showdown learnsets.json. */
    public void loadFromLearnsetsJson(Path learnsetsJson, int genFilter) throws Exception {
        if (learnsetsJson == null || !Files.exists(learnsetsJson)) {
            plugin.getLogger().warning("[PokeDemo] learnsets.json not found for TM compat: " + learnsetsJson);
            return;
        }

        int gen = Math.max(0, genFilter);
        tmCompatBySpecies.clear();
        tutorCompatBySpecies.clear();
        tutorCompatBySpecies.clear();

        try (Reader r = Files.newBufferedReader(learnsetsJson, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(r).getAsJsonObject();

            // Some builds may wrap the real data under a key like "learnsets" or "data".
            JsonObject dataRoot = pickDataRoot(root);

            for (var spEn : dataRoot.entrySet()) {
                String spId = spEn.getKey().toLowerCase(Locale.ROOT);
                JsonObject spObj;
                try {
                    spObj = spEn.getValue().getAsJsonObject();
                } catch (Exception ignored) {
                    continue;
                }
                if (!spObj.has("learnset") || !spObj.get("learnset").isJsonObject()) continue;
                JsonObject learnset = spObj.getAsJsonObject("learnset");

                Set<String> allowed = new HashSet<>();
                Set<String> tutorAllowed = new HashSet<>();
                for (var mvEn : learnset.entrySet()) {
                    String moveId = mvEn.getKey().toLowerCase(Locale.ROOT);
                    JsonElement arrEl = mvEn.getValue();
                    if (!arrEl.isJsonArray()) continue;
                    boolean ok = false;
                    for (JsonElement tagEl : arrEl.getAsJsonArray()) {
                        String tag = tagEl.getAsString();
                        if (tag == null) continue;
                        tag = tag.trim();
                        if (tag.length() < 2) continue;
                        char last = Character.toUpperCase(tag.charAt(tag.length() - 1));
                        if (last != 'M' && last != 'T') continue;

                        int tg;
                        try {
                            tg = Integer.parseInt(tag.substring(0, tag.length() - 1));
                        } catch (Exception ignored) {
                            continue;
                        }
                        if (gen == 0 || tg == gen) {
                            ok = true;
                            if (last == 'T') tutorAllowed.add(moveId);
                            break;
                        }
                    }
                    if (ok) {
                        // only allow moves that exist in Dex and are allowed under Gen1 constraints
                        allowed.add(moveId);
                    }
                }

                if (!allowed.isEmpty()) tmCompatBySpecies.put(spId, allowed);
                if (!tutorAllowed.isEmpty()) tutorCompatBySpecies.put(spId, tutorAllowed);
            }
        }

        plugin.getLogger().info("[PokeDemo] TM compatibility loaded: species=" + tmCompatBySpecies.size() + ", gen=" + gen + ", file=" + learnsetsJson.getFileName());
    }

    private void loadSingleSpeciesFromLearnsetsJson(Path learnsetsJson, int genFilter, String targetSpeciesId) throws Exception {
        if (learnsetsJson == null || !Files.exists(learnsetsJson) || targetSpeciesId == null) return;

        int gen = Math.max(0, genFilter);
        String spNeed = targetSpeciesId.toLowerCase(Locale.ROOT);

        try (Reader r = Files.newBufferedReader(learnsetsJson, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
            JsonObject dataRoot = pickDataRoot(root);
            if (!dataRoot.has(spNeed) || !dataRoot.get(spNeed).isJsonObject()) return;

            JsonObject spObj = dataRoot.getAsJsonObject(spNeed);
            if (!spObj.has("learnset") || !spObj.get("learnset").isJsonObject()) return;
            JsonObject learnset = spObj.getAsJsonObject("learnset");

            Set<String> allowed = new HashSet<>();
            Set<String> tutorAllowed = new HashSet<>();
            for (var mvEn : learnset.entrySet()) {
                String moveId = mvEn.getKey().toLowerCase(Locale.ROOT);
                JsonElement arrEl = mvEn.getValue();
                if (!arrEl.isJsonArray()) continue;
                boolean ok = false;
                for (JsonElement tagEl : arrEl.getAsJsonArray()) {
                    String tag = tagEl.getAsString();
                    if (tag == null) continue;
                    tag = tag.trim();
                    if (tag.length() < 2) continue;
                    char last = Character.toUpperCase(tag.charAt(tag.length() - 1));
                    if (last != 'M' && last != 'T') continue;
                    int tg;
                    try {
                        tg = Integer.parseInt(tag.substring(0, tag.length() - 1));
                    } catch (Exception ignored) {
                        continue;
                    }
                    if (gen == 0 || tg == gen) {
                        ok = true;
                        if (last == 'T') tutorAllowed.add(moveId);
                        break;
                    }
                }
                if (ok) allowed.add(moveId);
            }
            if (!allowed.isEmpty()) tmCompatBySpecies.put(spNeed, allowed);
            if (!tutorAllowed.isEmpty()) tutorCompatBySpecies.put(spNeed, tutorAllowed);
        }
    }

    
    /**
     * Load TM compatibility from Pokemon Showdown's source file learnsets.ts (raw from GitHub).
     * This contains machine tags like "1M" which may be missing in play.pokemonshowdown.com/data/learnsets.json.
     *
     * Parser strategy: lightweight brace matching for the exported Learnsets object, then regex within each species' learnset.
     */
    public void loadFromLearnsetsTs(Path learnsetsTs, int genFilter) throws Exception {
        if (learnsetsTs == null || !Files.exists(learnsetsTs)) {
            plugin.getLogger().warning("[PokeDemo] learnsets.ts not found for TM compat: " + learnsetsTs);
            return;
        }
        int gen = Math.max(0, genFilter);
        tmCompatBySpecies.clear();

        String raw = Files.readString(learnsetsTs, StandardCharsets.UTF_8);
        if (raw == null || raw.isBlank()) return;

        // Strip block and line comments (best-effort).
        raw = raw.replaceAll("(?s)/\\*.*?\\*/", "");
        raw = raw.replaceAll("(?m)//.*?$", "");

        int idx = raw.indexOf("Learnsets");
        if (idx < 0) idx = 0;
        int start = raw.indexOf('{', idx);
        if (start < 0) return;
        int end = matchBrace(raw, start);
        if (end <= start) return;

        String top = raw.substring(start + 1, end); // inside { ... }

        int i = 0;
        while (i < top.length()) {
            i = skipWs(top, i);
            if (i >= top.length()) break;

            // Read key (identifier or quoted)
            String key;
            char c = top.charAt(i);
            if (c == '\'' || c == '"') {
                int j = i + 1;
                while (j < top.length() && top.charAt(j) != c) j++;
                if (j >= top.length()) break;
                key = top.substring(i + 1, j);
                i = j + 1;
            } else {
                int j = i;
                while (j < top.length()) {
                    char ch = top.charAt(j);
                    if (!(Character.isLetterOrDigit(ch) || ch == '_' || ch == '-')) break;
                    j++;
                }
                if (j == i) { i++; continue; }
                key = top.substring(i, j);
                i = j;
            }
            key = key.toLowerCase(Locale.ROOT);

            i = skipWs(top, i);
            if (i >= top.length() || top.charAt(i) != ':') continue;
            i++;
            i = skipWs(top, i);

            if (i >= top.length() || top.charAt(i) != '{') continue;
            int objStart = i;
            int objEnd = matchBrace(top, objStart);
            if (objEnd <= objStart) break;

            String obj = top.substring(objStart + 1, objEnd); // inside species object
            i = objEnd + 1;

            // Find learnset: { ... }
            int lsIdx = obj.indexOf("learnset");
            if (lsIdx < 0) continue;
            int brace = obj.indexOf('{', lsIdx);
            if (brace < 0) continue;
            int braceEnd = matchBrace(obj, brace);
            if (braceEnd <= brace) continue;
            String learnset = obj.substring(brace + 1, braceEnd);

            Set<String> allowed = parseLearnsetBlockForMachines(learnset, gen, false);
            Set<String> tutorAllowed = parseLearnsetBlockForMachines(learnset, gen, true);
            if (!allowed.isEmpty()) tmCompatBySpecies.put(key, allowed);
            if (!tutorAllowed.isEmpty()) tutorCompatBySpecies.put(key, tutorAllowed);

            // Skip trailing commas
            while (i < top.length() && top.charAt(i) != '\n' && top.charAt(i) != ',') i++;
            if (i < top.length() && top.charAt(i) == ',') i++;
        }

        plugin.getLogger().info("[PokeDemo] TM compatibility loaded: species=" + tmCompatBySpecies.size() + ", gen=" + gen + ", file=" + learnsetsTs.getFileName());
    }

    private Set<String> parseLearnsetBlockForMachines(String learnset, int gen, boolean tutorsOnly) {
        Set<String> allowed = new HashSet<>();
        if (learnset == null || learnset.isBlank()) return allowed;

        // Match "moveid: [ ... ]" where moveid is identifier.
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("(?m)\\b([a-z0-9_]+)\\s*:\\s*\\[(.*?)\\]");
        java.util.regex.Matcher m = p.matcher(learnset);
        while (m.find()) {
            String moveId = m.group(1).toLowerCase(Locale.ROOT);
            String arr = m.group(2);
            if (arr == null) continue;

            java.util.regex.Matcher t = java.util.regex.Pattern.compile("[\"'](\\d+)([A-Za-z])[\"']").matcher(arr);
            boolean ok = false;
            while (t.find()) {
                int tg;
                try { tg = Integer.parseInt(t.group(1)); } catch (Exception e) { continue; }
                char suf = Character.toUpperCase(t.group(2).charAt(0));
                if (suf != 'M' && suf != 'T') continue;
                if (tutorsOnly && suf != 'T') continue;
                if (gen == 0 || tg == gen) { ok = true; break; }
            }
            if (ok) allowed.add(moveId);
        }
        return allowed;
    }

    private int matchBrace(String s, int openIdx) {
        int depth = 0;
        boolean inStr = false;
        char strCh = 0;
        for (int i = openIdx; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inStr) {
                if (c == '\\') { i++; continue; }
                if (c == strCh) inStr = false;
                continue;
            }
            if (c == '\'' || c == '"') { inStr = true; strCh = c; continue; }
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    private int skipWs(String s, int i) {
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == ' ' || c == '\t' || c == '\r' || c == '\n') { i++; continue; }
            break;
        }
        return i;
    }

private JsonObject pickDataRoot(JsonObject root) {
        if (root == null) return new JsonObject();
        if (root.has("learnsets") && root.get("learnsets").isJsonObject()) return root.getAsJsonObject("learnsets");
        if (root.has("data") && root.get("data").isJsonObject()) return root.getAsJsonObject("data");
        return root;
    }

    
    /** Load TM compatibility from a compact offline json produced by this plugin. */
    public void loadFromCompatJson(Path compatJson) throws Exception {
        tmCompatBySpecies.clear();
        try (Reader r = Files.newBufferedReader(compatJson, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
            // format: { "gen":1, "species": { "pikachu": ["thunderbolt","..."], ... } }
            JsonObject spObj = root.has("species") ? root.getAsJsonObject("species") : root;
            for (Map.Entry<String, JsonElement> e : spObj.entrySet()) {
                String sp = e.getKey().toLowerCase(Locale.ROOT);
                Set<String> set = new HashSet<>();
                if (e.getValue().isJsonArray()) {
                    for (JsonElement el : e.getValue().getAsJsonArray()) {
                        if (el.isJsonPrimitive()) set.add(el.getAsString().toLowerCase(Locale.ROOT));
                    }
                } else if (e.getValue().isJsonObject()) {
                    // also accept { moveId:true }
                    for (Map.Entry<String, JsonElement> me : e.getValue().getAsJsonObject().entrySet()) {
                        set.add(me.getKey().toLowerCase(Locale.ROOT));
                    }
                }
                if (!set.isEmpty()) { tmCompatBySpecies.put(sp, set); tutorCompatBySpecies.put(sp, new HashSet<>(set)); }
            }
        }
        plugin.getLogger().info("[PokeDemo] TM compatibility loaded (offline json): species=" + tmCompatBySpecies.size() + ", gen=" + lastGenFilter + ", file=" + compatJson.getFileName());
    }

private void initDefaultTmMap() {
        // Gen1 TMs 01-50
        // IMPORTANT: Use Pokemon Showdown move ids (no underscores).
        putTm(1, "megapunch");
        putTm(2, "razorwind");
        putTm(3, "swordsdance");
        putTm(4, "whirlwind");
        putTm(5, "megakick");
        putTm(6, "toxic");
        putTm(7, "horndrill");
        putTm(8, "bodyslam");
        putTm(9, "takedown");
        putTm(10, "doubleedge");
        putTm(11, "bubblebeam");
        putTm(12, "watergun");
        putTm(13, "icebeam");
        putTm(14, "blizzard");
        putTm(15, "hyperbeam");
        putTm(16, "payday");
        putTm(17, "submission");
        putTm(18, "counter");
        putTm(19, "seismictoss");
        putTm(20, "rage");
        putTm(21, "megadrain");
        putTm(22, "solarbeam");
        putTm(23, "dragonrage");
        putTm(24, "thunderbolt");
        putTm(25, "thunder");
        putTm(26, "earthquake");
        putTm(27, "fissure");
        putTm(28, "dig");
        putTm(29, "psychic");
        putTm(30, "teleport");
        putTm(31, "mimic");
        putTm(32, "doubleteam");
        putTm(33, "reflect");
        putTm(34, "bide");
        putTm(35, "metronome");
        putTm(36, "selfdestruct");
        putTm(37, "eggbomb");
        putTm(38, "fireblast");
        putTm(39, "swift");
        putTm(40, "skullbash");
        putTm(41, "softboiled");
        putTm(42, "dreameater");
        putTm(43, "skyattack");
        putTm(44, "rest");
        putTm(45, "thunderwave");
        putTm(46, "psywave");
        putTm(47, "explosion");
        putTm(48, "rockslide");
        putTm(49, "triattack");
        putTm(50, "substitute");

        // Gen1 HMs 01-05
        tmMoveById.put("hm01", "cut");
        tmMoveById.put("hm02", "fly");
        tmMoveById.put("hm03", "surf");
        tmMoveById.put("hm04", "strength");
        tmMoveById.put("hm05", "flash");
        // Sensible defaults for later HM-like slots; can be overridden by tm_moves.json.
        tmMoveById.putIfAbsent("hm06", "rocksmash");
        tmMoveById.putIfAbsent("hm07", "waterfall");
        tmMoveById.putIfAbsent("hm08", "rockclimb");
    }

    private void putTm(int no, String moveId) {
        String id = String.format(Locale.ROOT, "tm%02d", no);
        tmMoveById.put(id, moveId);
    }
}
