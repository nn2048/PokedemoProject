package win.pokedemo;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Downloads Pokemon Showdown public data files into plugins/PokeDemo/moves_raw.
 *
 * Data index: https://play.pokemonshowdown.com/data/
 */
public final class ShowdownDownloader {
    private ShowdownDownloader() {}

    public static final String SHOWDOWN_BASE = "https://play.pokemonshowdown.com/data/";
    public static final String MOVES_JSON = SHOWDOWN_BASE + "moves.json";
    public static final String LEARNSETS_JSON = SHOWDOWN_BASE + "learnsets.json";
    public static final String POKEDEX_JSON = SHOWDOWN_BASE + "pokedex.json";

    // Full learnsets (includes TM/HM tags like "1M"). play.pokemonshowdown.com/data/learnsets.json may omit machine tags.
    public static final String LEARNSETS_TS = "https://raw.githubusercontent.com/smogon/pokemon-showdown/master/data/learnsets.ts";

    /** Ensure moves.json exists in moves_raw. Returns true if the file exists after this call. */
    public static boolean ensureMovesJson(JavaPlugin plugin) {
        return ensureFile(plugin, "moves_raw/moves.json", MOVES_JSON);
    }

    /** Ensure learnsets.json exists in moves_raw. Returns true if the file exists after this call. */
    public static boolean ensureLearnsetsJson(JavaPlugin plugin) {
        return ensureFile(plugin, "moves_raw/learnsets.json", LEARNSETS_JSON);
    }


    /** Ensure learnsets.ts (full learnsets with TM/HM tags) exists in moves_raw. */
    public static boolean ensureLearnsetsTs(JavaPlugin plugin) {
        return ensureFile(plugin, "moves_raw/learnsets.ts", LEARNSETS_TS);
    }

    /** Ensure pokedex.json exists in moves_raw. Returns true if the file exists after this call. */
    public static boolean ensurePokedexJson(JavaPlugin plugin) {
        return ensureFile(plugin, "moves_raw/pokedex.json", POKEDEX_JSON);
    }

    private static boolean ensureFile(JavaPlugin plugin, String relPath, String url) {
        try {
            Path out = plugin.getDataFolder().toPath().resolve(relPath);
            if (Files.exists(out) && Files.size(out) > 10_000) {
                return true;
            }
            Files.createDirectories(out.getParent());
            plugin.getLogger().info("[PokeDemo] Downloading: " + url);

            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty("User-Agent", "PokeDemoPlugin/1.0 (+Paper)");
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                plugin.getLogger().warning("[PokeDemo] Download failed (HTTP " + code + "): " + url);
                return Files.exists(out);
            }
            try (InputStream in = conn.getInputStream()) {
                Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
            }
            plugin.getLogger().info("[PokeDemo] Downloaded to " + out);
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("[PokeDemo] Download error: " + e.getMessage());
            return false;
        }
    }
}
