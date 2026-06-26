package win.pokedemo;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Loads Pokemon Showdown move short descriptions from plugins/PokeDemo/moves_raw/moves.json.
 *
 * Localized override keys supported via lang JSON/YML:
 * - move_desc.<move_id>
 * - move.<move_id>.desc
 *
 * If no localized override exists, falls back to Showdown's English shortDesc/desc.
 */
public final class MoveDescriptionCatalog {
    private static final Gson GSON = new Gson();
    private static volatile Map<String, String> EN_DESC = Map.of();
    private static volatile long LAST_LOADED_MTIME = Long.MIN_VALUE;

    private MoveDescriptionCatalog() {}

    private static String norm(String id) {
        if (id == null) return "";
        return id.trim().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
    }

    private static Path resolveMovesFile(JavaPlugin plugin) {
        if (plugin == null) return null;
        return plugin.getDataFolder().toPath().resolve("moves_raw").resolve("moves.json");
    }

    private static void ensureLoaded(JavaPlugin plugin) {
        try {
            Path file = resolveMovesFile(plugin);
            long mtime = (file != null && Files.exists(file)) ? Files.getLastModifiedTime(file).toMillis() : -1L;
            if (mtime == LAST_LOADED_MTIME && !EN_DESC.isEmpty()) return;
            Map<String, String> map = new HashMap<>();
            if (file != null && Files.exists(file)) {
                String json = Files.readString(file, StandardCharsets.UTF_8);
                JsonObject obj = GSON.fromJson(json, JsonObject.class);
                if (obj != null) parseMovesObject(obj, map);
            } else {
                try (InputStream in = plugin == null ? null : plugin.getResource("default_data/showdown_move_desc_en_us.json")) {
                    if (in != null) {
                        String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                        JsonObject obj = GSON.fromJson(json, JsonObject.class);
                        if (obj != null) {
                            for (var en : obj.entrySet()) {
                                if (en.getValue() != null && en.getValue().isJsonPrimitive()) {
                                    String v = en.getValue().getAsString();
                                    if (v != null && !v.isBlank()) map.put(norm(en.getKey()), sanitize(v));
                                }
                            }
                        }
                    }
                }
            }
            EN_DESC = Map.copyOf(map);
            LAST_LOADED_MTIME = mtime;
        } catch (Exception e) {
            if (plugin != null) plugin.getLogger().warning("Failed to load move descriptions: " + e.getMessage());
            EN_DESC = Map.of();
            LAST_LOADED_MTIME = Long.MIN_VALUE;
        }
    }

    private static void parseMovesObject(JsonObject obj, Map<String, String> out) {
        for (var en : obj.entrySet()) {
            String id = norm(en.getKey());
            JsonElement value = en.getValue();
            if (value == null || !value.isJsonObject()) continue;
            JsonObject mv = value.getAsJsonObject();
            String desc = "";
            if (mv.has("shortDesc") && mv.get("shortDesc").isJsonPrimitive()) desc = mv.get("shortDesc").getAsString();
            if ((desc == null || desc.isBlank()) && mv.has("desc") && mv.get("desc").isJsonPrimitive()) desc = mv.get("desc").getAsString();
            desc = sanitize(desc);
            if (!desc.isBlank()) out.put(id, desc);
        }
    }

    private static String sanitize(String s) {
        if (s == null) return "";
        return s.replace("\r", " ").replace("\n", " ").replaceAll("\\s+", " ").trim();
    }

    public static String descriptionFor(LangManager lang, String moveId) {
        String id = norm(moveId);
        if (id.isBlank()) return "";
        if (lang != null) {
            String a = lang.tr("move_desc." + id, null);
            if (a != null && !a.equals("move_desc." + id) && !a.isBlank()) return a.trim();
            String b = lang.tr("move." + id + ".desc", null);
            if (b != null && !b.equals("move." + id + ".desc") && !b.isBlank()) return b.trim();
            String c = lang.tr("cobblemon.move." + id + ".desc", null);
            if (c != null && !c.equals("cobblemon.move." + id + ".desc") && !c.isBlank()) return sanitize(c);
        }
        PokeDemoPlugin plugin = PokeDemoPlugin.INSTANCE;
        ensureLoaded(plugin);
        return EN_DESC.getOrDefault(id, "");
    }
}
