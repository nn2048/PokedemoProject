package win.pokedemo.bridge.client.model.render;

import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public final class BridgePokeBallRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger("PokeDemoBridge/PokeBallFx");
    private static final Identifier MODERN_GEO = Identifier.of("cobblemon", "bedrock/poke_balls/models/poke_ball.geo.json");
    private static final Identifier MODERN_ANIM = Identifier.of("cobblemon", "bedrock/poke_balls/animations/poke_ball.animation.json");
    private static final Identifier ANCIENT_GEO = Identifier.of("cobblemon", "bedrock/poke_balls/models/ancient_poke_ball.geo.json");
    private static final Identifier ANCIENT_ANIM = Identifier.of("cobblemon", "bedrock/poke_balls/animations/ancient_poke_ball.animation.json");
    private static BedrockGeoModel.LoadedModel modernModel;
    private static BedrockAnimations modernAnimations;
    private static BedrockGeoModel.LoadedModel ancientModel;
    private static BedrockAnimations ancientAnimations;
    private static boolean triedLoad;

    public static void render(MatrixStack matrices, VertexConsumerProvider consumers, int light, float scale, float animSeconds, boolean opening, String ballId) {
        ensureLoaded();
        boolean ancient = isAncient(ballId);
        BedrockGeoModel.LoadedModel model = ancient ? ancientModel : modernModel;
        BedrockAnimations animations = ancient ? ancientAnimations : modernAnimations;
        Identifier texture = textureFor(ballId);
        if (model == null) return;
        model.resetPose();
        if (animations != null) {
            animations.apply(opening ? "animation.poke_ball.open" : "animation.poke_ball.shut", model, Math.min(animSeconds, 0.5f));
            if (opening && animSeconds > 0.5f) {
                animations.apply("animation.poke_ball.open_idle", model, animSeconds - 0.5f);
            }
        }
        matrices.push();
        matrices.scale(-scale, -scale, scale);
        model.root().render(matrices, consumers.getBuffer(RenderLayers.entityCutoutNoCull(texture)), light, OverlayTexture.DEFAULT_UV);
        matrices.pop();
    }

    private static void ensureLoaded() {
        if (triedLoad) return;
        triedLoad = true;
        try {
            modernModel = BedrockGeoModel.load(MODERN_GEO);
            modernAnimations = BedrockAnimations.load(MODERN_ANIM);
        } catch (IOException | RuntimeException ex) {
            LOGGER.error("Failed to load modern poke ball resources", ex);
        }
        try {
            ancientModel = BedrockGeoModel.load(ANCIENT_GEO);
            ancientAnimations = BedrockAnimations.load(ANCIENT_ANIM);
        } catch (IOException | RuntimeException ex) {
            LOGGER.error("Failed to load ancient poke ball resources", ex);
        }
    }

    private static boolean isAncient(String ballId) {
        return ballId != null && ballId.toLowerCase(java.util.Locale.ROOT).startsWith("ancient_");
    }

    private static Identifier textureFor(String ballId) {
        String safe = (ballId == null || ballId.isBlank()) ? "poke_ball" : ballId.toLowerCase(java.util.Locale.ROOT);
        return Identifier.of("cobblemon", "textures/poke_balls/" + safe + ".png");
    }

    private BridgePokeBallRenderer() {}
}
