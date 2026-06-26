package win.pokedemo.bridge.client.model;

import com.google.gson.Gson;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

/**
 * Stage-1 resolver only. It resolves per-species JSON manifests and raw extracted Cobblemon asset files.
 * It does not yet parse/render Bedrock geo models. That is the next step.
 */
public final class BedrockAssetResolver {
    private static final Gson GSON = new Gson();
    private final Path configDir;

    public BedrockAssetResolver() {
        this.configDir = FabricLoader.getInstance().getConfigDir().resolve("pokedemo_bridge");
    }

    public Optional<SpeciesAssetSet> resolve(String species, String gender) {
        String key = species.toLowerCase(Locale.ROOT);
        Path manifest = FabricLoader.getInstance().getModContainer("pokedemo_bridge")
                .flatMap(container -> container.findPath("data/pokedemo_bridge/species/" + key + ".json"))
                .orElse(null);
        if (manifest == null || !Files.exists(manifest)) {
            return Optional.empty();
        }
        try (Reader reader = Files.newBufferedReader(manifest)) {
            SpeciesManifest parsed = GSON.fromJson(reader, SpeciesManifest.class);
            SpeciesManifest.Entry entry = "F".equalsIgnoreCase(gender) && parsed.female != null ? parsed.female : parsed.male;
            if (entry == null) {
                return Optional.empty();
            }
            return Optional.of(new SpeciesAssetSet(entry.model, entry.animation, entry.texture));
        } catch (IOException ex) {
            return Optional.empty();
        }
    }

    public record SpeciesAssetSet(String modelPath, String animationPath, String texturePath) {}

    private static final class SpeciesManifest {
        String species;
        Entry male;
        Entry female;
        static final class Entry {
            String model;
            String animation;
            String texture;
        }
    }
}
