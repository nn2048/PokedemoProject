package win.pokedemo;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Lightweight Pokédex dataset used by the in-game Pokédex GUI.
 *
 * We intentionally keep this separate from {@link Dex}/{@link Species} to avoid breaking
 * existing save formats and code paths.
 */
public final class PokedexData {

    public record Entry(String speciesId, int nationalDexNumber, String descKey) {}

    private final Map<String, Entry> bySpeciesId = new HashMap<>();
    private final List<Entry> ordered = new ArrayList<>();

    public Entry get(String speciesId) {
        if (speciesId == null) return null;
        return bySpeciesId.get(speciesId.toLowerCase(Locale.ROOT));
    }

    public List<Entry> ordered() {
        return ordered;
    }

    public static PokedexData loadBuiltin(JavaPlugin plugin) {
        PokedexData out = new PokedexData();
        if (plugin == null) return out;
        try (InputStream in = plugin.getResource("pokedex_builtin.json")) {
            if (in == null) {
                plugin.getLogger().warning("[PokeDemo] Missing resource pokedex_builtin.json");
                return out;
            }
            Type type = new TypeToken<List<Entry>>(){}.getType();
            List<Entry> list = new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), type);
            if (list == null) list = List.of();
            for (Entry e : list) {
                if (e == null || e.speciesId() == null) continue;
                String id = e.speciesId().toLowerCase(Locale.ROOT);
                out.bySpeciesId.put(id, e);
            }
            // order by national dex number, then species id
            out.ordered.addAll(out.bySpeciesId.values());
            out.ordered.sort(Comparator
                    .comparingInt(Entry::nationalDexNumber)
                    .thenComparing(Entry::speciesId));
        } catch (Exception ex) {
            plugin.getLogger().warning("[PokeDemo] Failed to load builtin pokedex: " + ex.getMessage());
        }
        return out;
    }
}
