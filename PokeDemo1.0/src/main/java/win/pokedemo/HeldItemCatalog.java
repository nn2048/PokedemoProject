package win.pokedemo;

import org.bukkit.Material;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Loads the held-item list from a bundled txt and registers missing items into {@link ItemRegistry}.
 *
 * File format (tab-separated):
 *   中文\t日文\t英文\t道具说明
 *
 * We derive item ids from the English name (snake_case). This matches most Cobblemon/Showdown ids.
 *
 * Notes:
 * - Berries and TMs/HMs are excluded (they have their own systems).
 * - If an item already exists in the registry, we never override it.
 */
public final class HeldItemCatalog {
    private HeldItemCatalog() {}

    private static final Map<String, String> CN_NAME_BY_ID = new HashMap<>();
    private static final Map<String, String> DESC_BY_ID = new HashMap<>();

    
    public static void registerBulkHeldItemsFromTxt(ItemRegistry registry, int startCmdInclusive) {
        Objects.requireNonNull(registry, "registry");

        // Prefer the canonical CMD mapping shipped with the resource pack.
        // This prevents "order changes" from breaking textures (items turning into phone/berries, etc.).
        Map<String, Integer> cmdMap = loadCmdMapOrEmpty();

        List<Row> rows = readRows();
        int cmdFallback = startCmdInclusive;

        for (Row r : rows) {
            String id = englishToId(r.en);
            if (id.isBlank()) continue;

            CN_NAME_BY_ID.putIfAbsent(id, r.cn);
            DESC_BY_ID.putIfAbsent(id, r.desc == null ? "" : r.desc);

            // Exclusions
            if (id.endsWith("_berry") || id.equals("berry") || id.equals("gold_berry")) continue;
            if (id.startsWith("tm") || id.startsWith("hm")) continue;

            if (registry.get(id) != null) continue; // don't override

            int cmd = -1;
            Integer mapped = cmdMap.get("paper:held_" + id);
            if (mapped != null) {
                cmd = mapped;
            } else {
                cmd = cmdFallback++;
            }

            // Held items are not directly usable (they're equipped through the held-item GUI).
            registry.register(new ItemDef(
                    id,
                    ItemType.HELD,
                    Material.PAPER,
                    cmd,
                    "item." + id,
                    false,
                    true,
                    0,
                    Map.of("desc", r.desc == null ? "" : r.desc)
            ));
        }
    }

    private static Map<String, Integer> loadCmdMapOrEmpty() {
        try (InputStream in = HeldItemCatalog.class.getClassLoader().getResourceAsStream("pokedemo_custom_model_data_map.json")) {
            if (in == null) return Map.of();
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);

            Map<String, Integer> out = new HashMap<>();
            com.google.gson.JsonObject obj = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
            for (Map.Entry<String, com.google.gson.JsonElement> e : obj.entrySet()) {
                if (e.getValue() != null && e.getValue().isJsonPrimitive()) {
                    try {
                        out.put(e.getKey(), e.getValue().getAsInt());
                    } catch (Exception ignored) {}
                }
            }
            return out;
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    public static String cnNameOrNull(String id) {
        if (id == null) return null;
        return CN_NAME_BY_ID.get(id.toLowerCase(Locale.ROOT));
    }

    public static String descOrEmpty(String id) {
        if (id == null) return "";
        return DESC_BY_ID.getOrDefault(id.toLowerCase(Locale.ROOT), "");
    }

    private static List<Row> readRows() {
        InputStream in = HeldItemCatalog.class.getClassLoader().getResourceAsStream("data/held_items_zh_ja_en.txt");
        if (in == null) return List.of();

        List<Row> out = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            boolean headerSkipped = false;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                if (!headerSkipped) {
                    // first line could be "携带物品" or header columns
                    if (line.startsWith("携带物品")) continue;
                    if (line.startsWith("中文") && line.contains("英文")) { headerSkipped = true; continue; }
                }
                String[] parts = line.split("\t");
                // Some versions of the file contain 5 columns: CN(short)\tCN(full)\tJA\tEN\tDESC
                if (parts.length < 4) continue;
                String cn = parts[0].trim();
                String ja;
                String en;
                String desc;
                if (parts.length >= 5) {
                    ja = parts[2].trim();
                    en = parts[3].trim();
                    desc = parts[4].trim();
                } else {
                    ja = parts[1].trim();
                    en = parts[2].trim();
                    desc = parts[3].trim();
                }
                // Some lines might be misaligned (rare). Guard.
                if (en.isBlank()) continue;
                out.add(new Row(cn, ja, en, desc));
            }
        } catch (Exception ignored) {
            return List.of();
        }
        return out;
    }

    static String englishToId(String en) {
        if (en == null) return "";
        String s = en.toLowerCase(Locale.ROOT).trim();
        // Common punctuation
        s = s.replace("'", "");
        s = s.replace("’", "");
        s = s.replace("-", "_");
        s = s.replace(" ", "_");
        s = s.replace(".", "");
        s = s.replace("/", "_");
        s = s.replace("(", "");
        s = s.replace(")", "");
        while (s.contains("__")) s = s.replace("__", "_");
        // a few well-known special cases in older gens
        if (s.equals("przcureberry")) return "prz_cure_berry";
        if (s.equals("psncureberry")) return "psn_cure_berry";
        if (s.equals("miracleberry")) return "miracle_berry";
        if (s.equals("mysteryberry")) return "mystery_berry";
        if (s.equals("polkadot_bow")) return "polkadot_bow";
        return s;
    }

    private record Row(String cn, String ja, String en, String desc) {}
}
