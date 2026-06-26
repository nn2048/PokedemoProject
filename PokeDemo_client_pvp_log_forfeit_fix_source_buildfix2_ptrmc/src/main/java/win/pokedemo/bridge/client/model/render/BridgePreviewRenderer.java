package win.pokedemo.bridge.client.model.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import win.pokedemo.bridge.common.CarrierRenderState;

import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class BridgePreviewRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger("PokeDemoBridge/PreviewRenderer");
    private static final Map<String, SpeciesDef> SPECIES = new HashMap<>();
    private static boolean triedLoad;

    private BridgePreviewRenderer() {}

    public static boolean hasSpecies(String species) {
        ensureLoaded();
        return speciesDef(species) != null;
    }

    public static void renderWorldSpecies(String species, String gender, boolean shiny,
                                          MatrixStack matrices, VertexConsumerProvider consumers,
                                          int light, float animTime, float runtimeScale, boolean emissive) {
        ensureLoaded();
        SpeciesDef def = speciesDef(species);
        if (def == null) return;
        CarrierRenderState state = new CarrierRenderState(UUID.randomUUID(), -1, null, -1, null, species, "normal", gender, shiny, "idle", 1.0f, species, 0, "land", 0.0f, false, false, false, false, 0.0f);
        CobblemonResolver.Resolved resolved = def.resolver.resolve(state);
        if (resolved == null) return;
        BedrockGeoModel.LoadedModel model = def.models.get(normalizeModelKey(resolved.model()));
        if (model == null && !def.models.isEmpty()) model = def.models.values().iterator().next();
        if (model == null) return;

        String clip = def.poser.standingPose != null ? def.poser.standingPose.clip : (def.poser.walkingPose != null ? def.poser.walkingPose.clip : null);
        model.resetPose();
        if (clip != null) def.animations.apply(clip, model, animTime);

        float scale = computeWorldScale(model, def.sizes, 1.0f) * runtimeScale;
        matrices.scale(-scale, -scale, scale);
        var root = model.renderRoot(def.poser.rootBone != null ? def.poser.rootBone : def.sizes.rootBone);
        int actualLight = emissive ? 0xF000F0 : light;
        root.render(matrices, consumers.getBuffer(RenderLayers.entityCutoutNoCull(resolveTexture(resolved.texture()))), actualLight, net.minecraft.client.render.OverlayTexture.DEFAULT_UV);
        for (CobblemonResolver.Layer layer : resolved.layers()) {
            Identifier tex = resolveLayerTexture(layer, animTime);
            if (tex == null) continue;
            int layerLight = (emissive || layer.emissive()) ? 0xF000F0 : actualLight;
            root.render(matrices, consumers.getBuffer(RenderLayers.entityCutoutNoCull(tex)), layerLight, net.minecraft.client.render.OverlayTexture.DEFAULT_UV);
        }
    }

    public static void renderHudPortrait(String species, String gender, boolean shiny,
                                         DrawTarget target, float animTime) {
        ensureLoaded();
        SpeciesDef def = speciesDef(species);
        if (def == null) return;
        MinecraftClient client = MinecraftClient.getInstance();
        var consumers = client.getBufferBuilders().getEntityVertexConsumers();
        MatrixStack matrices = new MatrixStack();
        matrices.translate(target.centerX, target.baseY, 240.0f);
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(180.0f));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0f));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(12.0f));
        float scale = target.size * 0.095f * Math.max(0.75f, def.poser.profileScale);
        matrices.scale(scale, scale, scale);
        matrices.translate(def.poser.profileTranslation[0], def.poser.profileTranslation[1], def.poser.profileTranslation[2]);
        renderWorldSpecies(species, gender, shiny, matrices, consumers, 0xF000F0, animTime, 1.0f, true);
        consumers.draw();
    }

    public record DrawTarget(float centerX, float baseY, float size) {}

    private static SpeciesDef speciesDef(String species) {
        if (species == null) return null;
        String s = species.toLowerCase(Locale.ROOT);
        if (s.contains("pikachu")) return SPECIES.get("pikachu");
        if (s.contains("charizard") || s.contains("喷火龙")) return SPECIES.get("charizard");
        return null;
    }

    private static void ensureLoaded() {
        if (triedLoad) return;
        triedLoad = true;
        loadSpecies(
                "pikachu",
                Identifier.of("pokedemo_bridge", "species/pikachu.json"),
                Identifier.of("pokedemo_bridge", "cobblemon/pikachu/pikachu.poser.json"),
                Identifier.of("pokedemo_bridge", "cobblemon/pikachu/pikachu.resolver.json"),
                Identifier.of("pokedemo_bridge", "cobblemon/pikachu/pikachu.animation.json"),
                Map.of(
                        "pikachu_male.geo", Identifier.of("pokedemo_bridge", "cobblemon/pikachu/pikachu_male.geo.json"),
                        "pikachu_female.geo", Identifier.of("pokedemo_bridge", "cobblemon/pikachu/pikachu_female.geo.json")
                )
        );
        loadSpecies(
                "charizard",
                Identifier.of("pokedemo_bridge", "species/charizard.json"),
                Identifier.of("pokedemo_bridge", "cobblemon/charizard/charizard.poser.json"),
                Identifier.of("pokedemo_bridge", "cobblemon/charizard/charizard.resolver.json"),
                Identifier.of("pokedemo_bridge", "cobblemon/charizard/charizard.animation.json"),
                Map.of("charizard.geo", Identifier.of("pokedemo_bridge", "cobblemon/charizard/charizard.geo.json"))
        );
    }

    private static void loadSpecies(String key, Identifier speciesJson, Identifier poserJson, Identifier resolverJson,
                                    Identifier animationJson, Map<String, Identifier> models) {
        try {
            Map<String, BedrockGeoModel.LoadedModel> loadedModels = new HashMap<>();
            for (Map.Entry<String, Identifier> e : models.entrySet()) loadedModels.put(e.getKey(), BedrockGeoModel.load(e.getValue()));
            CobblemonSpeciesDisplayConfig sizes = CobblemonSpeciesDisplayConfig.load(speciesJson, poserJson);
            BedrockAnimations animations = BedrockAnimations.load(animationJson);
            CobblemonPoser poser = CobblemonPoser.load(poserJson, animations, sizes);
            CobblemonResolver resolver = CobblemonResolver.load(resolverJson);
            SPECIES.put(key, new SpeciesDef(loadedModels, sizes, poser, resolver, animations));
        } catch (IOException | RuntimeException ex) {
            LOGGER.error("Failed to load preview species {}", key, ex);
        }
    }

    private static float computeWorldScale(BedrockGeoModel.LoadedModel model, CobblemonSpeciesDisplayConfig sizes, float stateScale) {
        float targetHeightBlocks = sizes.hitboxHeight > 0.0f ? sizes.hitboxHeight : (sizes.speciesHeight > 0 ? (sizes.speciesHeight / 10.0f) : 1.0f);
        targetHeightBlocks = Math.max(0.5f, targetHeightBlocks);
        float modelHeightUnits = Math.max(0.1f, model.visibleH());
        float base = (targetHeightBlocks / modelHeightUnits) * 2.5f;
        float speciesScale = sizes.baseScale > 0.0f ? sizes.baseScale : 1.0f;
        float normalizedSpeciesScale = 0.85f + ((speciesScale - 1.0f) * 0.35f);
        float runtimeScale = stateScale > 0.0f ? stateScale : 1.0f;
        return base * normalizedSpeciesScale * runtimeScale;
    }

    private static Identifier resolveLayerTexture(CobblemonResolver.Layer layer, float animTime) {
        if (layer.frames() == null || layer.frames().isEmpty()) return null;
        String source = layer.frames().get(0);
        if (layer.frames().size() > 1 && layer.fps() > 0) {
            int idx = (int) Math.floor(animTime * layer.fps()) % layer.frames().size();
            source = layer.frames().get(Math.max(0, idx));
        }
        return resolveTexture(source);
    }

    private static Identifier resolveTexture(String cobblemonTexture) {
        if (cobblemonTexture == null || cobblemonTexture.isBlank()) return Identifier.of("pokedemo_bridge", "textures/entity/pikachu.png");
        String path = cobblemonTexture;
        int colon = path.indexOf(':');
        if (colon >= 0) path = path.substring(colon + 1);
        String base = path.substring(path.lastIndexOf('/') + 1);
        return Identifier.of("pokedemo_bridge", "textures/entity/" + base);
    }

    private static String normalizeModelKey(String model) {
        if (model == null) return "";
        String m = model;
        int colon = m.indexOf(':');
        if (colon >= 0) m = m.substring(colon + 1);
        int slash = m.lastIndexOf('/');
        if (slash >= 0) m = m.substring(slash + 1);
        return m.toLowerCase(Locale.ROOT);
    }

    private record SpeciesDef(Map<String, BedrockGeoModel.LoadedModel> models,
                              CobblemonSpeciesDisplayConfig sizes,
                              CobblemonPoser poser,
                              CobblemonResolver resolver,
                              BedrockAnimations animations) {}
}
