package win.pokedemo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Idempotent "run once" auto-import for Pokemon Showdown datasets.
 * This is intentionally conservative: it only records that downloads succeeded,
 * and will re-run if the files are missing.
 */
public final class ShowdownAutoImport {

    private ShowdownAutoImport() {}

    private static final String STATE_FILE = "showdown_auto_import.json";

    @SuppressWarnings("unchecked")
    private static Map<String, Object> readState(Path file) {
        try {
            if (!Files.exists(file)) return new HashMap<>();
            String json = Files.readString(file);
            Object obj = new Gson().fromJson(json, Object.class);
            if (obj instanceof Map<?, ?> m) {
                Map<String, Object> out = new HashMap<>();
                for (Map.Entry<?, ?> e : m.entrySet()) {
                    if (e.getKey() instanceof String k) out.put(k, e.getValue());
                }
                return out;
            }
        } catch (Exception ignored) {}
        return new HashMap<>();
    }

    private static void writeState(Path file, Map<String, Object> state) {
        try {
            Files.createDirectories(file.getParent());
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            Files.writeString(file, gson.toJson(state));
        } catch (Exception ignored) {}
    }

    public static void importIfNeeded(PokeDemoPlugin plugin) {
        Path folder = plugin.getDataFolder().toPath();
        Path stateFile = folder.resolve(STATE_FILE);
        Map<String, Object> state = readState(stateFile);

        boolean wantMoves = plugin.getConfig().getBoolean("showdown.auto-download-on-start", true)
                || plugin.getConfig().getBoolean("showdown.auto-import-on-start", false);

        boolean wantLearnsets = plugin.getConfig().getBoolean("showdown.download-learnsets", false)
                || plugin.getConfig().getBoolean("showdown.auto-import-on-start", false);

        boolean wantPokedex = plugin.getConfig().getBoolean("showdown.download-pokedex", false);

        Path movesJson = folder.resolve("moves_raw").resolve("moves.json");
        Path learnsetsJson = folder.resolve("moves_raw").resolve("learnsets.json");
        Path pokedexJson = folder.resolve("moves_raw").resolve("pokedex.json");

        boolean movesOk = Boolean.TRUE.equals(state.get("movesOk")) && Files.exists(movesJson);
        boolean learnsetsOk = Boolean.TRUE.equals(state.get("learnsetsOk")) && Files.exists(learnsetsJson);
        boolean pokedexOk = Boolean.TRUE.equals(state.get("pokedexOk")) && Files.exists(pokedexJson);

        if (wantMoves && !movesOk) {
            plugin.getLogger().info("[PokeDemo] Auto-import: downloading Showdown moves.json ...");
            boolean ok = ShowdownDownloader.ensureMovesJson(plugin);
            state.put("movesOk", ok);
            plugin.getLogger().info("[PokeDemo] Auto-import: moves.json " + (ok ? "OK" : "FAILED"));
        }

        if (wantLearnsets && !learnsetsOk) {
            plugin.getLogger().info("[PokeDemo] Auto-import: downloading Showdown learnsets.json ...");
            boolean ok = ShowdownDownloader.ensureLearnsetsJson(plugin);
            state.put("learnsetsOk", ok);
            plugin.getLogger().info("[PokeDemo] Auto-import: learnsets.json " + (ok ? "OK" : "FAILED"));
        }

        if (wantPokedex && !pokedexOk) {
            plugin.getLogger().info("[PokeDemo] Auto-import: downloading Showdown pokedex.json ...");
            boolean ok = ShowdownDownloader.ensurePokedexJson(plugin);
            state.put("pokedexOk", ok);
            plugin.getLogger().info("[PokeDemo] Auto-import: pokedex.json " + (ok ? "OK" : "FAILED"));
        }

        state.put("lastRunEpochSec", Instant.now().getEpochSecond());
        writeState(stateFile, state);
    }
}
