package win.pokedemo.bridge.client.model.render;

import com.google.gson.*;
import net.minecraft.util.Identifier;
import win.pokedemo.bridge.common.CarrierRenderState;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class CobblemonResolver {
    private final List<Variation> variations;

    private CobblemonResolver(List<Variation> variations) {
        this.variations = variations;
    }

    static CobblemonResolver load(Identifier id) throws IOException {
        try (InputStream in = open(id); InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonArray vars = root.getAsJsonArray("variations");
            List<Variation> out = new ArrayList<>();
            if (vars != null) {
                for (JsonElement e : vars) {
                    if (!e.isJsonObject()) continue;
                    JsonObject v = e.getAsJsonObject();
                    List<String> aspects = new ArrayList<>();
                    if (v.has("aspects") && v.get("aspects").isJsonArray()) {
                        for (JsonElement a : v.getAsJsonArray("aspects")) aspects.add(a.getAsString().toLowerCase(Locale.ROOT));
                    }
                    String poser = str(v, "poser");
                    String model = str(v, "model");
                    String texture = str(v, "texture");
                    List<Layer> layers = new ArrayList<>();
                    if (v.has("layers") && v.get("layers").isJsonArray()) {
                        for (JsonElement le : v.getAsJsonArray("layers")) {
                            if (!le.isJsonObject()) continue;
                            JsonObject lo = le.getAsJsonObject();
                            String name = str(lo, "name");
                            boolean emissive = lo.has("emissive") && lo.get("emissive").getAsBoolean();
                            boolean translucent = lo.has("translucent") && lo.get("translucent").getAsBoolean();
                            if (lo.has("texture") && lo.get("texture").isJsonPrimitive()) {
                                layers.add(new Layer(name, List.of(str(lo, "texture")), 0, emissive, translucent));
                            } else if (lo.has("texture") && lo.get("texture").isJsonObject()) {
                                JsonObject to = lo.getAsJsonObject("texture");
                                List<String> frames = new ArrayList<>();
                                if (to.has("frames") && to.get("frames").isJsonArray()) {
                                    for (JsonElement fe : to.getAsJsonArray("frames")) frames.add(fe.getAsString());
                                }
                                int fps = to.has("fps") ? to.get("fps").getAsInt() : 0;
                                layers.add(new Layer(name, frames, fps, emissive, translucent));
                            }
                        }
                    }
                    out.add(new Variation(aspects, poser, model, texture, layers));
                }
            }
            return new CobblemonResolver(out);
        }
    }

    Resolved resolve(CarrierRenderState state) {
        boolean female = state.gender() != null && state.gender().toLowerCase(Locale.ROOT).contains("female");
        if (state.gender() != null && state.gender().startsWith("♀")) female = true;
        boolean shiny = state.shiny();
        Variation best = null;
        int bestScore = -1;
        for (Variation v : variations) {
            if (matches(v.aspects, female, shiny)) {
                int score = v.aspects.size();
                if (score > bestScore) {
                    best = v;
                    bestScore = score;
                }
            }
        }
        if (best == null && !variations.isEmpty()) best = variations.get(0);
        return best == null ? null : new Resolved(best.poser, best.model, best.texture, best.layers);
    }

    private static boolean matches(List<String> aspects, boolean female, boolean shiny) {
        for (String a : aspects) {
            if (a.equals("female") && !female) return false;
            if (a.equals("shiny") && !shiny) return false;
            if (!a.equals("female") && !a.equals("shiny") && !a.startsWith("cosmetic_item-")) return false;
            if (a.startsWith("cosmetic_item-")) return false;
        }
        return true;
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) ? o.get(key).getAsString() : null;
    }

    private static InputStream open(Identifier id) throws IOException {
        String cp = "assets/" + id.getNamespace() + "/" + id.getPath();
        InputStream in = CobblemonResolver.class.getClassLoader().getResourceAsStream(cp);
        if (in == null) throw new IOException("Missing resolver resource: " + id);
        return in;
    }

    record Resolved(String poser, String model, String texture, List<Layer> layers) {}
    record Layer(String name, List<String> frames, int fps, boolean emissive, boolean translucent) {}
    private record Variation(List<String> aspects, String poser, String model, String texture, List<Layer> layers) {}
}
