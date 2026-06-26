package win.pokedemo;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Loads Cobblemon-like species drops from plugins/PokeDemo/species_raw/...
 *
 * We only implement the subset we need for "victory loot":
 *  - drops.amount (int)
 *  - drops.entries[]: item (string), percentage (number, optional), quantityRange ("a-b", optional)
 */
public class DropTableManager {

    public record DropEntry(String itemKey, double percentage, int minQty, int maxQty) {
        public int rollQuantity(java.util.Random rnd) {
            int lo = Math.max(0, minQty);
            int hi = Math.max(lo, maxQty);
            if (hi == lo) return lo;
            return lo + rnd.nextInt(hi - lo + 1);
        }
    }

    public record DropTable(int amount, java.util.List<DropEntry> entries) {}

    private final PokeDemoPlugin plugin;
    private final java.util.Map<String, DropTable> bySpecies = new java.util.concurrent.ConcurrentHashMap<>();

    public DropTableManager(PokeDemoPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadAll() {
        bySpecies.clear();
        Path root = plugin.getDataFolder().toPath().resolve("species_raw");
        if (!Files.exists(root)) {
            plugin.getLogger().info("[Drops] species_raw not found; drops disabled.");
            return;
        }

        // Typical Cobblemon path: species_raw/data/cobblemon/species/**.json
        Path data = root.resolve("data");
        if (!Files.exists(data)) {
            // Some packs may already start at data/...
            data = root;
        }

        int loaded = 0;
        try {
            java.util.List<Path> files = new java.util.ArrayList<>();
            Files.walk(data)
                    .filter(p -> p.toString().endsWith(".json"))
                    .forEach(files::add);

            for (Path p : files) {
                // Only care about /species/ paths to avoid scanning huge unrelated json.
                String norm = p.toString().replace('\\', '/');
                if (!norm.contains("/species/") && !norm.contains("/species\\")) continue;

                String speciesId = p.getFileName().toString();
                if (speciesId.endsWith(".json")) speciesId = speciesId.substring(0, speciesId.length() - 5);
                speciesId = speciesId.toLowerCase(java.util.Locale.ROOT);

                DropTable t = parseDrops(p);
                if (t != null && t.entries != null && !t.entries.isEmpty()) {
                    bySpecies.put(speciesId, t);
                    loaded++;
                }
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("[Drops] Failed to load species drops: " + t.getMessage());
        }

        plugin.getLogger().info("[Drops] Loaded drops for " + loaded + " species.");
    }

    public DropTable get(String speciesId) {
        if (speciesId == null) return null;
        return bySpecies.get(speciesId.toLowerCase(java.util.Locale.ROOT));
    }

    private DropTable parseDrops(Path json) {
        try (BufferedReader br = Files.newBufferedReader(json, StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(br);
            if (!root.isJsonObject()) return null;
            JsonObject obj = root.getAsJsonObject();
            if (!obj.has("drops") || !obj.get("drops").isJsonObject()) return null;

            JsonObject drops = obj.getAsJsonObject("drops");
            int amount = drops.has("amount") ? drops.get("amount").getAsInt() : 0;
            if (amount <= 0) amount = 3; // Cobblemon commonly uses 3; safe default

            java.util.List<DropEntry> entries = new java.util.ArrayList<>();
            if (drops.has("entries") && drops.get("entries").isJsonArray()) {
                JsonArray arr = drops.getAsJsonArray("entries");
                for (JsonElement el : arr) {
                    if (!el.isJsonObject()) continue;
                    JsonObject e = el.getAsJsonObject();
                    if (!e.has("item")) continue;
                    String item = e.get("item").getAsString();
                    double pct = e.has("percentage") ? e.get("percentage").getAsDouble() : 100.0;
                    int min = 1, max = 1;
                    if (e.has("quantityRange")) {
                        String qr = e.get("quantityRange").getAsString();
                        int[] mm = parseRange(qr);
                        if (mm != null) {
                            min = mm[0];
                            max = mm[1];
                        }
                    } else if (e.has("min") || e.has("max")) {
                        if (e.has("min")) min = e.get("min").getAsInt();
                        if (e.has("max")) max = e.get("max").getAsInt();
                    } else if (e.has("quantity")) {
                        // some packs use a fixed quantity
                        try {
                            min = max = e.get("quantity").getAsInt();
                        } catch (Throwable ignored) {}
                    }
                    entries.add(new DropEntry(item, pct, min, max));
                }
            }
            if (entries.isEmpty()) return null;
            return new DropTable(amount, entries);
        } catch (IOException ex) {
            return null;
        }
    }

    private static int[] parseRange(String s) {
        if (s == null) return null;
        s = s.trim();
        if (s.isEmpty()) return null;
        // formats: "0-1" or "1" or "1..3" (we accept both)
        String[] parts;
        if (s.contains("-")) parts = s.split("-");
        else if (s.contains("..")) parts = s.split("\\.\\.");
        else parts = new String[]{s};
        try {
            int a = Integer.parseInt(parts[0].trim());
            int b = (parts.length >= 2) ? Integer.parseInt(parts[1].trim()) : a;
            if (b < a) { int t = a; a = b; b = t; }
            return new int[]{a, b};
        } catch (Throwable ignored) {
            return null;
        }
    }
}
