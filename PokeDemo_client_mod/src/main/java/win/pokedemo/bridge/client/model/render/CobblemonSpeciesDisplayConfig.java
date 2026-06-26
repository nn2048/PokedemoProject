package win.pokedemo.bridge.client.model.render;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

final class CobblemonSpeciesDisplayConfig {
    final float baseScale;
    final float hitboxHeight;
    final float speciesHeight;
    final String rootBone;
    final float profileScale;
    final float[] profileTranslation;
    final boolean canFly;
    final boolean canSwim;
    final boolean canSleep;
    final boolean canWalk;

    private CobblemonSpeciesDisplayConfig(float baseScale, float hitboxHeight, float speciesHeight, String rootBone,
                                          float profileScale, float[] profileTranslation,
                                          boolean canFly, boolean canSwim, boolean canSleep, boolean canWalk) {
        this.baseScale = baseScale;
        this.hitboxHeight = hitboxHeight;
        this.speciesHeight = speciesHeight;
        this.rootBone = rootBone;
        this.profileScale = profileScale;
        this.profileTranslation = profileTranslation;
        this.canFly = canFly;
        this.canSwim = canSwim;
        this.canSleep = canSleep;
        this.canWalk = canWalk;
    }

    static CobblemonSpeciesDisplayConfig load(Identifier speciesJson, Identifier poserJson) throws IOException {
        float baseScale = 1.0f;
        float hitboxHeight = 1.0f;
        float speciesHeight = 10.0f;
        boolean canFly = false;
        boolean canSwim = true;
        boolean canSleep = false;
        boolean canWalk = true;
        try (InputStream in = open(speciesJson); InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (root.has("baseScale")) baseScale = root.get("baseScale").getAsFloat();
            if (root.has("height")) speciesHeight = root.get("height").getAsFloat();
            if (root.has("hitbox") && root.get("hitbox").isJsonObject()) {
                JsonObject hit = root.getAsJsonObject("hitbox");
                if (hit.has("height")) hitboxHeight = hit.get("height").getAsFloat();
            }
            if (root.has("behaviour") && root.get("behaviour").isJsonObject()) {
                JsonObject behaviour = root.getAsJsonObject("behaviour");
                JsonObject moving = behaviour.getAsJsonObject("moving");
                if (moving != null) {
                    JsonObject fly = moving.getAsJsonObject("fly");
                    if (fly != null && (!fly.has("canFly") || fly.get("canFly").getAsBoolean())) canFly = true;
                    JsonObject swim = moving.getAsJsonObject("swim");
                    if (swim != null) canSwim = true;
                    JsonObject walk = moving.getAsJsonObject("walk");
                    if (walk != null && walk.has("canWalk")) canWalk = walk.get("canWalk").getAsBoolean();
                }
                JsonObject resting = behaviour.getAsJsonObject("resting");
                if (resting != null && resting.has("canSleep")) canSleep = resting.get("canSleep").getAsBoolean();
            }
        }
        String rootBone = null;
        float profileScale = 1.0f;
        float[] profileTranslation = new float[]{0.0f, 0.0f, 0.0f};
        try (InputStream in = openOptional(poserJson)) {
            if (in != null) {
                try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                    if (root.has("rootBone")) rootBone = root.get("rootBone").getAsString();
                    if (root.has("profileScale")) profileScale = root.get("profileScale").getAsFloat();
                    if (root.has("profileTranslation") && root.get("profileTranslation").isJsonArray()) {
                        JsonArray arr = root.getAsJsonArray("profileTranslation");
                        for (int i = 0; i < Math.min(3, arr.size()); i++) profileTranslation[i] = arr.get(i).getAsFloat();
                    }
                }
            }
        }
        return new CobblemonSpeciesDisplayConfig(baseScale, hitboxHeight, speciesHeight, rootBone, profileScale, profileTranslation,
                canFly, canSwim, canSleep, canWalk);
    }

    private static InputStream openOptional(Identifier id) throws IOException {
        String cp = "assets/" + id.getNamespace() + "/" + id.getPath();
        InputStream in = CobblemonSpeciesDisplayConfig.class.getClassLoader().getResourceAsStream(cp);
        if (in == null) {
            cp = "data/" + id.getNamespace() + "/" + id.getPath();
            in = CobblemonSpeciesDisplayConfig.class.getClassLoader().getResourceAsStream(cp);
        }
        return in;
    }

    private static InputStream open(Identifier id) throws IOException {
        String cp = "assets/" + id.getNamespace() + "/" + id.getPath();
        InputStream in = CobblemonSpeciesDisplayConfig.class.getClassLoader().getResourceAsStream(cp);
        if (in == null) {
            cp = "data/" + id.getNamespace() + "/" + id.getPath();
            in = CobblemonSpeciesDisplayConfig.class.getClassLoader().getResourceAsStream(cp);
        }
        if (in == null) throw new IOException("Missing config resource: " + id);
        return in;
    }
}
