package win.pokedemo.bridge.client.fx;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import win.pokedemo.bridge.client.PokeDemoBridgeClient;
import win.pokedemo.bridge.client.model.render.BridgePokeBallRenderer;
import win.pokedemo.bridge.client.model.render.BridgePreviewRenderer;
import win.pokedemo.bridge.client.net.BridgePayloads;
import win.pokedemo.bridge.common.CarrierRenderState;
import win.pokedemo.bridge.common.PartySlotState;

import java.util.Comparator;
import java.util.Map;
import java.util.UUID;

public final class BridgeSendoutFxManager {
    private static final Identifier SEND_FLASH = Identifier.of("cobblemon", "textures/particle/balls/pokeball/casual/sendcasual.png");
    private static final Identifier SEND_SPARKS = Identifier.of("cobblemon", "textures/particle/balls/pokeball/ballsparks.png");
    private static final Identifier SEND_SPARKLE = Identifier.of("cobblemon", "textures/particle/balls/pokeball/ballsendsparkle.png");
    private static final Identifier SND_THROW = Identifier.of("cobblemon", "poke_ball.throw");
    private static final Identifier SND_OPEN = Identifier.of("cobblemon", "poke_ball.open");
    private static final Identifier SND_OPEN_ANCIENT = Identifier.of("cobblemon", "poke_ball.open.ancient");
    private static final Identifier SND_SEND_OUT = Identifier.of("cobblemon", "poke_ball.send_out");
    private static final Identifier SND_SHINY_SEND_OUT = Identifier.of("cobblemon", "poke_ball.shiny_send_out");
    private static final Identifier SND_RECALL = Identifier.of("cobblemon", "poke_ball.recall");

    private Transition active;

    public void clear() {
        active = null;
    }

    public boolean isBusy() {
        return active != null;
    }

    public boolean shouldSuppress(CarrierRenderState state) {
        if (active == null || state == null) return false;
        UUID playerId = active.ownerUuid;
        if (playerId == null || !playerId.equals(state.ownerUuid())) return false;
        if (active.pokemonUuid != null && state.pokemonUuid() != null) {
            return active.pokemonUuid.equals(state.pokemonUuid());
        }
        return state.slot() == active.slot;
    }

    public void triggerSelected() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (active != null || client.player == null || client.world == null) return;
        if (win.pokedemo.bridge.client.battle.BridgeBattleStateStore.current() != null) return;
        PartySlotState slot = PokeDemoBridgeClient.partyHudState().selectedSlotState();
        if (slot == null || !slot.occupied()) return;

        UUID ownerUuid = client.player.getUuid();
        int selectedSlot = PokeDemoBridgeClient.partyHudState().selectedSlot();
        Vec3d hand = handPosition(client);
        SendoutTargetInfo targetInfo = sendoutTarget(client);
        Vec3d aim = targetInfo.position();

        if (slot.active()) {
            CarrierRenderState state = findOwnedCarrier(slot.pokemonUuid(), selectedSlot, ownerUuid);
            Vec3d source = state != null ? carrierPosition(state) : aim;
            if (source == null) source = aim;
            active = Transition.recall(ownerUuid, selectedSlot, slot.pokemonUuid(), slot.species(), slot.gender(), slot.shiny(), slot.ballId(), source, hand, client.player.age);
        } else {
            active = Transition.sendout(ownerUuid, selectedSlot, slot.pokemonUuid(), slot.species(), slot.gender(), slot.shiny(), slot.ballId(), hand, aim, targetInfo.targetEntityUuid(), client.player.age);
        }
    }

    public void tick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) {
            active = null;
            return;
        }
        if (active == null) return;

        int age = client.player.age - active.startTick;
        if (!active.throwSoundPlayed && age >= active.throwSoundTick) {
            active.throwSoundPlayed = true;
            playLocal(active.mode == Mode.SENDOUT ? SND_THROW : SND_RECALL, 0.9f, active.mode == Mode.SENDOUT ? 1.0f : 1.05f);
        }
        if (!active.openSoundPlayed && age >= active.openSoundTick) {
            active.openSoundPlayed = true;
            if (active.mode == Mode.SENDOUT) {
                playLocal(isAncientBall(active.ballId) ? SND_OPEN_ANCIENT : SND_OPEN, 0.9f, 1.0f);
                playLocal(active.shiny ? SND_SHINY_SEND_OUT : SND_SEND_OUT, 0.95f, 1.0f);
            }
        }
        if (!active.requested && age >= active.packetTick) {
            active.requested = true;
            ClientPlayNetworking.send(BridgePayloads.RawBridgePayload.sendout(active.slot, active.ballId, active.target.x, active.target.y, active.target.z, active.targetEntityUuid));
        }
        if (age > active.duration) {
            active = null;
        }
    }

    public void render(MatrixStack worldMatrices, VertexConsumerProvider consumers, float tickDelta, double camX, double camY, double camZ) {
        if (active == null) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        float age = (client.player.age - active.startTick) + tickDelta;
        if (active.mode == Mode.SENDOUT) {
            renderSendout(active, worldMatrices, consumers, age, camX, camY, camZ, client.player.bodyYaw);
        } else {
            renderRecall(active, worldMatrices, consumers, age, camX, camY, camZ, client.player.bodyYaw);
        }
    }

    private void renderSendout(Transition fx, MatrixStack matrices, VertexConsumerProvider consumers, float age, double camX, double camY, double camZ, float bodyYaw) {
        float throwT = clamp01(age / 12.0f);
        Vec3d ballPos = fx.start.lerp(fx.target, easeOut(throwT));
        renderBeam(matrices, consumers, camX, camY, camZ, ballPos, fx.target.add(0.0, 0.45, 0.0), 0.18f, 0.12f + age * 0.03f, 0xF000F0);

        matrices.push();
        matrices.translate(ballPos.x - camX, ballPos.y - camY + 0.06, ballPos.z - camZ);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0f - bodyYaw + age * 16.0f));
        BridgePokeBallRenderer.render(matrices, consumers, 0xF000F0, 0.055f, age / 20.0f, age >= 10.0f, fx.ballId);
        matrices.pop();

        if (age >= 8.0f) {
            renderBurst(matrices, consumers, fx.target, camX, camY, camZ, age - 8.0f, 0xF000F0);
        }
        if (age >= 11.0f) {
            float appear = clamp01((age - 11.0f) / 12.0f);
            matrices.push();
            matrices.translate(fx.target.x - camX, fx.target.y - camY, fx.target.z - camZ);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0f - bodyYaw));
            float growth = 0.06f + 0.94f * easeOut(appear);
            BridgePreviewRenderer.renderWorldSpecies(fx.species, fx.gender, fx.shiny, matrices, consumers, 0xF000F0,
                    age / 20.0f, growth, true);
            matrices.pop();
        }
    }

    private void renderRecall(Transition fx, MatrixStack matrices, VertexConsumerProvider consumers, float age, double camX, double camY, double camZ, float bodyYaw) {
        float shrinkT = clamp01(age / 10.0f);
        float travelT = clamp01((age - 8.0f) / 12.0f);
        Vec3d ballStart = fx.start.add(0.0, 0.45, 0.0);
        Vec3d ballPos = ballStart.lerp(fx.target, easeOut(travelT));

        float remain = 1.0f - easeOut(shrinkT);
        if (remain > 0.0f) {
            matrices.push();
            matrices.translate(fx.start.x - camX, fx.start.y - camY, fx.start.z - camZ);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0f - bodyYaw));
            BridgePreviewRenderer.renderWorldSpecies(fx.species, fx.gender, fx.shiny, matrices, consumers, 0xF000F0,
                    age / 20.0f, Math.max(0.04f, remain), true);
            matrices.pop();
        }

        renderBeam(matrices, consumers, camX, camY, camZ, ballStart, fx.target, 0.16f, 0.1f + age * 0.03f, 0xF000F0);

        matrices.push();
        matrices.translate(ballPos.x - camX, ballPos.y - camY + 0.04, ballPos.z - camZ);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0f - bodyYaw - age * 14.0f));
        BridgePokeBallRenderer.render(matrices, consumers, 0xF000F0, 0.055f, Math.max(0.0f, (age - 6.0f) / 20.0f), false, fx.ballId);
        matrices.pop();

        if (age <= 14.0f) {
            renderBurst(matrices, consumers, fx.start.add(0.0, 0.2, 0.0), camX, camY, camZ, age, 0xF000F0);
        }
    }

    private void renderBurst(MatrixStack matrices, VertexConsumerProvider consumers, Vec3d center, double camX, double camY, double camZ, float age, int light) {
        renderBillboardFlipbook(matrices, consumers, camX, camY, camZ, center.add(0.0, 0.42, 0.0),
                1.05f, SEND_FLASH, 384, 32, 32, 32, 24, 12, age / 20.0f, light);
        for (int i = 0; i < 6; i++) {
            double angle = Math.toRadians((i * 60.0) + age * 42.0);
            double radius = 0.18 + 0.01 * i + age * 0.012;
            Vec3d p = center.add(Math.cos(angle) * radius, 0.18 + Math.sin(angle * 1.7) * 0.08, Math.sin(angle) * radius);
            renderBillboardFlipbook(matrices, consumers, camX, camY, camZ, p,
                    0.18f, SEND_SPARKLE, 99, 9, 9, 9, 18, 11, age / 20.0f, light);
        }
        for (int i = 0; i < 5; i++) {
            double angle = Math.toRadians((i * 72.0) - age * 55.0);
            double radius = 0.1 + age * 0.018;
            Vec3d p = center.add(Math.cos(angle) * radius, 0.08 + 0.03 * i, Math.sin(angle) * radius);
            renderBillboardFlipbook(matrices, consumers, camX, camY, camZ, p,
                    0.14f, SEND_SPARKS, 80, 8, 8, 8, 18, 10, age / 20.0f, light);
        }
    }

    private void renderBillboardFlipbook(MatrixStack matrices, VertexConsumerProvider consumers,
                                         double camX, double camY, double camZ, Vec3d worldPos, float size,
                                         Identifier texture, int texW, int texH, int frameW, int frameH,
                                         int fps, int frameCount, float animSeconds, int light) {
        matrices.push();
        matrices.translate(worldPos.x - camX, worldPos.y - camY, worldPos.z - camZ);
        Camera camera = MinecraftClient.getInstance().gameRenderer.getCamera();
        matrices.multiply(camera.getRotation());
        matrices.scale(size, size, size);
        int frame = Math.max(0, Math.min(frameCount - 1, (int) Math.floor(animSeconds * fps)));
        float u0 = (frame * frameW) / (float) texW;
        float u1 = ((frame + 1) * frameW) / (float) texW;
        float v0 = 0.0f;
        float v1 = frameH / (float) texH;
        MatrixStack.Entry entry = matrices.peek();
        Matrix4f pos = entry.getPositionMatrix();
        VertexConsumer vc = consumers.getBuffer(RenderLayers.entityTranslucentEmissive(texture));
        quad(vc, entry, pos, -1.0f, -1.0f, 1.0f, 1.0f, u0, v0, u1, v1, light);
        matrices.pop();
    }

    private void renderBeam(MatrixStack matrices, VertexConsumerProvider consumers,
                            double camX, double camY, double camZ,
                            Vec3d from, Vec3d to, float width, float animSeconds, int light) {
        Vec3d d = to.subtract(from);
        float length = (float) d.length();
        if (length <= 0.001f) return;

        matrices.push();
        matrices.translate(from.x - camX, from.y - camY, from.z - camZ);
        float yaw = (float) Math.toDegrees(Math.atan2(d.x, d.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitch));

        int texW = 384, texH = 32, frameW = 32, frameH = 32, fps = 24, frameCount = 12;
        int frame = Math.max(0, Math.min(frameCount - 1, (int) Math.floor(animSeconds * fps)));
        float u0 = (frame * frameW) / (float) texW;
        float u1 = ((frame + 1) * frameW) / (float) texW;
        float v0 = 0.0f;
        float v1 = frameH / (float) texH;
        MatrixStack.Entry entry = matrices.peek();
        Matrix4f pos = entry.getPositionMatrix();
        VertexConsumer vc = consumers.getBuffer(RenderLayers.entityTranslucentEmissive(SEND_FLASH));

        beamQuad(vc, entry, pos, width, length, u0, v0, u1, v1, light);
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(90.0f));
        entry = matrices.peek();
        pos = entry.getPositionMatrix();
        beamQuad(vc, entry, pos, width * 0.72f, length, u0, v0, u1, v1, light);
        matrices.pop();
    }

    private static void quad(VertexConsumer vc, MatrixStack.Entry entry, Matrix4f pos,
                             float minX, float minY, float maxX, float maxY,
                             float u0, float v0, float u1, float v1, int light) {
        vc.vertex(pos, minX, minY, 0.0f).color(255, 255, 255, 255).texture(u0, v1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, 0.0f, 1.0f, 0.0f);
        vc.vertex(pos, maxX, minY, 0.0f).color(255, 255, 255, 255).texture(u1, v1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, 0.0f, 1.0f, 0.0f);
        vc.vertex(pos, maxX, maxY, 0.0f).color(255, 255, 255, 255).texture(u1, v0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, 0.0f, 1.0f, 0.0f);
        vc.vertex(pos, minX, maxY, 0.0f).color(255, 255, 255, 255).texture(u0, v0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, 0.0f, 1.0f, 0.0f);
    }

    private static void beamQuad(VertexConsumer vc, MatrixStack.Entry entry, Matrix4f pos,
                                 float width, float length, float u0, float v0, float u1, float v1, int light) {
        vc.vertex(pos, -width, -width, 0.0f).color(255, 255, 255, 255).texture(u0, v1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, 0.0f, 1.0f, 0.0f);
        vc.vertex(pos,  width, -width, 0.0f).color(255, 255, 255, 255).texture(u1, v1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, 0.0f, 1.0f, 0.0f);
        vc.vertex(pos,  width,  width, length).color(255, 255, 255, 255).texture(u1, v0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, 0.0f, 1.0f, 0.0f);
        vc.vertex(pos, -width,  width, length).color(255, 255, 255, 255).texture(u0, v0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, 0.0f, 1.0f, 0.0f);
    }

    private static float clamp01(float v) {
        return MathHelper.clamp(v, 0.0f, 1.0f);
    }

    private static float easeOut(float t) {
        float inv = 1.0f - t;
        return 1.0f - inv * inv * inv;
    }

    private static void playLocal(Identifier id, float volume, float pitch) {
        MinecraftClient client = MinecraftClient.getInstance();
        client.getSoundManager().play(PositionedSoundInstance.ambient(SoundEvent.of(id), volume, pitch));
    }

    private static Vec3d handPosition(MinecraftClient client) {
        Vec3d eye = client.player.getCameraPosVec(1.0f);
        Vec3d dir = client.player.getRotationVec(1.0f).normalize();
        return eye.add(dir.multiply(0.45)).add(0.0, -0.18, 0.0);
    }

    private static SendoutTargetInfo sendoutTarget(MinecraftClient client) {
        Vec3d eye = client.player.getCameraPosVec(1.0f);
        Vec3d dir = client.player.getRotationVec(1.0f).normalize();
        Vec3d fallback = eye.add(dir.multiply(7.5));
        Vec3d pos = fallback;
        java.util.UUID targetEntityUuid = null;
        HitResult hit = client.crosshairTarget;
        if (hit instanceof EntityHitResult ehr && ehr.getEntity() != null) {
            Entity e = ehr.getEntity();
            pos = new Vec3d(e.getX(), e.getY() + Math.max(0.2D, e.getHeight() * 0.35D), e.getZ());
            targetEntityUuid = e.getUuid();
        } else if (hit instanceof BlockHitResult bhr && hit.getType() != HitResult.Type.MISS) {
            Vec3d hitPos = bhr.getPos();
            pos = new Vec3d(hitPos.x, hitPos.y + 0.1, hitPos.z);
        } else {
            pos = fallback;
        }
        double dy = pos.y - client.player.getY();
        if (dy > 3.5) pos = new Vec3d(pos.x, client.player.getY() + 3.5, pos.z);
        if (dy < -6.0) pos = new Vec3d(pos.x, client.player.getY() - 6.0, pos.z);
        return new SendoutTargetInfo(pos, targetEntityUuid);
    }

    private static CarrierRenderState findOwnedCarrier(UUID pokemonUuid, int slot, UUID ownerUuid) {
        return PokeDemoBridgeClient.entityRenderManager().snapshot().values().stream()
                .filter(s -> ownerUuid.equals(s.ownerUuid()))
                .filter(s -> pokemonUuid != null && s.pokemonUuid() != null ? pokemonUuid.equals(s.pokemonUuid()) : s.slot() == slot)
                .min(Comparator.comparingInt(CarrierRenderState::entityId))
                .orElse(null);
    }

    private static Vec3d carrierPosition(CarrierRenderState state) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (state == null || client.world == null) return null;
        Entity e = client.world.getEntityById(state.entityId());
        return e == null ? null : new Vec3d(e.getX(), e.getY(), e.getZ());
    }

    private static boolean isAncientBall(String ballId) {
        return ballId != null && ballId.toLowerCase(java.util.Locale.ROOT).startsWith("ancient_");
    }

    private record SendoutTargetInfo(Vec3d position, UUID targetEntityUuid) {}

    private enum Mode { SENDOUT, RECALL }

    private static final class Transition {
        private final Mode mode;
        private final UUID ownerUuid;
        private final int slot;
        private final UUID pokemonUuid;
        private final String species;
        private final String ballId;
        private final String gender;
        private final boolean shiny;
        private final Vec3d start;
        private final Vec3d target;
        private final UUID targetEntityUuid;
        private final int startTick;
        private final int packetTick;
        private final int throwSoundTick;
        private final int openSoundTick;
        private final int duration;
        private boolean requested;
        private boolean throwSoundPlayed;
        private boolean openSoundPlayed;

        private Transition(Mode mode, UUID ownerUuid, int slot, UUID pokemonUuid, String species, String gender, boolean shiny, String ballId,
                           Vec3d start, Vec3d target, UUID targetEntityUuid, int startTick, int packetTick, int throwSoundTick, int openSoundTick, int duration) {
            this.mode = mode;
            this.ownerUuid = ownerUuid;
            this.slot = slot;
            this.pokemonUuid = pokemonUuid;
            this.species = species;
            this.ballId = (ballId == null || ballId.isBlank()) ? "poke_ball" : ballId;
            this.gender = gender;
            this.shiny = shiny;
            this.start = start;
            this.target = target;
            this.targetEntityUuid = targetEntityUuid;
            this.startTick = startTick;
            this.packetTick = packetTick;
            this.throwSoundTick = throwSoundTick;
            this.openSoundTick = openSoundTick;
            this.duration = duration;
        }

        private static Transition sendout(UUID ownerUuid, int slot, UUID pokemonUuid, String species, String gender, boolean shiny, String ballId,
                                          Vec3d hand, Vec3d target, UUID targetEntityUuid, int startTick) {
            return new Transition(Mode.SENDOUT, ownerUuid, slot, pokemonUuid, species, gender, shiny, ballId, hand, target, targetEntityUuid, startTick, 18, 0, 10, 34);
        }

        private static Transition recall(UUID ownerUuid, int slot, UUID pokemonUuid, String species, String gender, boolean shiny, String ballId,
                                         Vec3d source, Vec3d hand, int startTick) {
            return new Transition(Mode.RECALL, ownerUuid, slot, pokemonUuid, species, gender, shiny, ballId, source, hand, null, startTick, 16, 8, 1000, 28);
        }
    }
}
