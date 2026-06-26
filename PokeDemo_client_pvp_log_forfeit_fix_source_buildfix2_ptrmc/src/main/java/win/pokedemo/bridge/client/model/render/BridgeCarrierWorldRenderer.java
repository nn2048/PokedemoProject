package win.pokedemo.bridge.client.model.render;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.fluid.Fluids;
import net.minecraft.util.Identifier;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import win.pokedemo.bridge.client.PokeDemoBridgeClient;
import win.pokedemo.bridge.client.fx.BridgeSendoutFxManager;
import win.pokedemo.bridge.common.CarrierRenderState;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class BridgeCarrierWorldRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger("PokeDemoBridge/GeoOverride");
    private static final Map<String, SpeciesDef> SPECIES = new HashMap<>();
    private static final Map<UUID, Integer> STILL_TICKS = new HashMap<>();
    private static final Map<UUID, Vec3d> LAST_POSITIONS = new HashMap<>();
    private static final Map<UUID, Integer> MOVE_TICKS = new HashMap<>();
    private static final Map<UUID, Float> BATTLE_LOCKED_YAW = new HashMap<>();
    private static boolean triedLoad;

    public static void register(BridgeSendoutFxManager fxManager) {
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.world == null || client.player == null) return;
            ensureLoaded();
            if (SPECIES.isEmpty()) return;

            var cam = context.worldState().cameraRenderState;
            double camX = cam.pos.x;
            double camY = cam.pos.y;
            double camZ = cam.pos.z;
            float tickDelta = client.getRenderTickCounter().getTickProgress(false);

            for (CarrierRenderState state : PokeDemoBridgeClient.entityRenderManager().snapshot().values()) {
                if (fxManager != null && fxManager.shouldSuppress(state)) continue;
                SpeciesDef def = speciesDef(state);
                if (def == null) continue;
                Entity entity = client.world.getEntityById(state.entityId());
                if (!(entity instanceof WolfEntity wolf)) continue;
                renderSpecies(wolf, state, def, context.matrices(), context.consumers(), tickDelta, camX, camY, camZ);
            }
            if (fxManager != null) {
                fxManager.render(context.matrices(), context.consumers(), tickDelta, camX, camY, camZ);
            }
        });
    }

    private static SpeciesDef speciesDef(CarrierRenderState state) {
        if (state == null || state.species() == null) return null;
        String s = state.species().toLowerCase(Locale.ROOT).trim();
        SpeciesDef direct = SPECIES.get(s);
        if (direct != null) return direct;
        for (Map.Entry<String, SpeciesDef> e : SPECIES.entrySet()) {
            String key = e.getKey();
            if (s.equals(key) || s.contains(key)) return e.getValue();
        }
        s = normalizeChineseAlias(s);
        return s == null ? null : SPECIES.get(s);
    }

    private static String normalizeChineseAlias(String s) {
        if (s.contains("喷火龙")) return "charizard";
        if (s.contains("皮卡丘")) return "pikachu";
        if (s.contains("妙蛙种子")) return "bulbasaur";
        if (s.contains("妙蛙草")) return "ivysaur";
        if (s.contains("妙蛙花")) return "venusaur";
        if (s.contains("小火龙")) return "charmander";
        if (s.contains("火恐龙")) return "charmeleon";
        if (s.contains("杰尼龟")) return "squirtle";
        if (s.contains("卡咪龟")) return "wartortle";
        if (s.contains("水箭龟")) return "blastoise";
        if (s.contains("卡蒂狗")) return "growlithe";
        if (s.contains("六尾")) return "vulpix";
        if (s.contains("九尾")) return "ninetales";
        if (s.contains("火焰鸟")) return "moltres";
        if (s.contains("闪电鸟")) return "zapdos";
        if (s.contains("急冻鸟")) return "articuno";
        if (s.contains("超梦")) return "mewtwo";
        if (s.contains("鲤鱼王")) return "magikarp";
        if (s.contains("鬼斯")) return "gastly";
        return null;
    }


    public static UUID pickModelTarget(MinecraftClient client, double maxDistance) {
        if (client == null || client.world == null || client.player == null) return null;
        ensureLoaded();
        var from = client.player.getCameraPosVec(1.0f);
        var dir = client.player.getRotationVec(1.0f).normalize();
        UUID best = null;
        double bestT = maxDistance + 1.0;
        for (CarrierRenderState state : PokeDemoBridgeClient.entityRenderManager().snapshot().values()) {
            SpeciesDef def = speciesDef(state);
            if (def == null) continue;
            Entity entity = client.world.getEntityById(state.entityId());
            if (!(entity instanceof WolfEntity wolf)) continue;
            var center = new Vec3d(wolf.getX(), wolf.getY(), wolf.getZ()).add(0.0, Math.max(0.6, def.sizes.hitboxHeight * 0.5), 0.0);
            double radius = Math.max(0.9, def.sizes.hitboxHeight * 0.45);
            double height = Math.max(1.2, def.sizes.hitboxHeight * 1.15);
            var rel = center.subtract(from);
            double t = rel.dotProduct(dir);
            if (t < 0.0 || t > maxDistance) continue;
            var closest = from.add(dir.multiply(t));
            double dy = Math.abs(closest.y - center.y);
            if (dy > height * 0.5) continue;
            double dx = closest.x - center.x;
            double dz = closest.z - center.z;
            if ((dx * dx + dz * dz) > radius * radius) continue;
            if (t < bestT) {
                bestT = t;
                best = state.entityUuid();
            }
        }
        return best;
    }

    private static void renderSpecies(WolfEntity wolf, CarrierRenderState state, SpeciesDef def, MatrixStack matrices,
                                      VertexConsumerProvider consumers, float tickDelta,
                                      double camX, double camY, double camZ) {
        CobblemonResolver.Resolved resolved = def.resolver.resolve(state);
        if (resolved == null) return;
        BedrockGeoModel.LoadedModel model = def.models.get(normalizeModelKey(resolved.model()));
        if (model == null && !def.models.isEmpty()) model = def.models.values().iterator().next();
        if (model == null) return;

        var pos = wolf.getLerpedPos(tickDelta);
        float bodyYaw = wolf.lastBodyYaw + (wolf.bodyYaw - wolf.lastBodyYaw) * tickDelta;
        float scale = computeWorldScale(model, def.sizes);
        float animTime = (wolf.age + tickDelta) / 20.0f;
        double horizontalSpeedSq = wolf.getVelocity().horizontalLengthSquared();
        String animLower = state.animation() == null ? "" : state.animation().toLowerCase(java.util.Locale.ROOT);
        boolean battleVisual = state.battle() || animLower.contains("battle") || animLower.contains("move:") || animLower.contains("attack_") || animLower.contains("hit") || animLower.contains("recoil") || animLower.contains("sendout") || animLower.contains("withdraw") || animLower.contains("cry") || animLower.contains("faint");
        String moveMode = state.moveMode() == null ? "land" : state.moveMode().toLowerCase(java.util.Locale.ROOT);
        double frameMotionSq = updateFrameMotionSq(wolf, pos);
        boolean rawMoving = !battleVisual && isActuallyMoving(state, moveMode, horizontalSpeedSq, frameMotionSq, wolf);
        boolean moving = smoothMoving(wolf, rawMoving);
        boolean touchingWater = wolf.isTouchingWater() || moveMode.contains("swim") || moveMode.contains("water");
        boolean submergedSwim = state.submerged() || (touchingWater && def.sizes.canSwim && shouldAquaticDive(wolf, def.sizes, moving));
        boolean physicallyAirborne = !touchingWater && (!wolf.isOnGround() || Math.abs(wolf.getVelocity().y) > 0.08D);
        boolean airborne = physicallyAirborne || (state.airborne() && physicallyAirborne);
        int stillTicks = updateStillTicks(wolf, moving);
        boolean nearOwner = wolf.getOwner() == null || wolf.distanceTo(wolf.getOwner()) <= 4.0f;
        boolean wildCarrier = state.ownerUuid() == null;
        boolean night = wolf.getEntityWorld() != null && !wolf.getEntityWorld().isDay();
        boolean sleepy = (!battleVisual) && state.sleeping();
        String localAnimHint = PokeDemoBridgeClient.ambientSounds().overrideAnimation(state, state.animation());
        CarrierRenderState effectiveState = state.withAnimation(localAnimHint);
        PoseSelection pose = pickPose(effectiveState, def, moving, touchingWater, submergedSwim, airborne, sleepy, horizontalSpeedSq);

        model.resetPose();
        if (pose.namedClip() != null) {
            def.animations.apply(pose.namedClip(), model, animTime);
        } else if (pose.pose() != null && pose.pose().clip != null) {
            def.animations.apply(pose.pose().clip, model, animTime);
        }
        if (pose.pose() != null) {
            java.util.List<CobblemonPoser.ProcAnim> procedures = pose.pose().procedures;
            boolean assistProcedures = moving && (
                    pose.pose() == def.poser.standingPose
                            || pose.pose() == def.poser.floatPose
                            || pose.pose() == def.poser.surfaceFloatPose
                            || pose.pose() == def.poser.hoverPose
            );
            if (((procedures == null || procedures.isEmpty()) || assistProcedures) && moving) {
                java.util.List<CobblemonPoser.ProcAnim> inferred = inferProcedures(model, def, touchingWater, airborne);
                if (procedures == null || procedures.isEmpty()) {
                    procedures = inferred;
                } else if (inferred != null && !inferred.isEmpty()) {
                    java.util.ArrayList<CobblemonPoser.ProcAnim> merged = new java.util.ArrayList<>(procedures);
                    merged.addAll(inferred);
                    procedures = merged;
                }
            }
            applyProcedures(model, procedures, animTime);
        }

        if (battleVisual) {
            Float locked = BATTLE_LOCKED_YAW.get(state.entityUuid());
            float desiredYaw = !Float.isNaN(state.battleYaw()) ? state.battleYaw() : bodyYaw;
            if (locked == null || !Float.isNaN(state.battleYaw()) && Math.abs(locked - desiredYaw) > 0.5f) {
                locked = desiredYaw;
                BATTLE_LOCKED_YAW.put(state.entityUuid(), locked);
            }
            bodyYaw = locked;
        } else {
            BATTLE_LOCKED_YAW.remove(state.entityUuid());
        }

        matrices.push();
        float waterSwimOffset = touchingWater && submergedSwim ? submergeOffset(def.sizes) : 0.0f;
        matrices.translate(pos.x - camX, pos.y - camY + waterSwimOffset, pos.z - camZ);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0f - bodyYaw));
        matrices.scale(-scale, -scale, scale);

        int light = 0xF000F0;
        var root = model.renderRoot(def.poser.rootBone != null ? def.poser.rootBone : def.sizes.rootBone);
        root.render(matrices, consumers.getBuffer(RenderLayers.entityCutoutNoCull(resolveTexture(resolved.texture()))), light, OverlayTexture.DEFAULT_UV);
        boolean disableFieryOverlay = state.species() != null && (state.species().equalsIgnoreCase("ponyta") || state.species().equalsIgnoreCase("rapidash"));
        for (CobblemonResolver.Layer layer : resolved.layers()) {
            if (disableFieryOverlay && layer.emissive()) continue;
            Identifier tex = resolveLayerTexture(layer, animTime);
            if (tex == null) continue;
            int layerLight = layer.emissive() ? 0xF000F0 : light;
            root.render(matrices, consumers.getBuffer(RenderLayers.entityCutoutNoCull(tex)), layerLight, OverlayTexture.DEFAULT_UV);
        }
        matrices.pop();
        syncClientNameTag(wolf, state);
    }


    private static void syncClientNameTag(WolfEntity wolf, CarrierRenderState state) {
        if (wolf == null || state == null) return;
        String base = state.displayName() != null && !state.displayName().isBlank() ? state.displayName() : state.species();
        if (base == null || base.isBlank()) {
            wolf.setCustomName(null);
            wolf.setCustomNameVisible(false);
            return;
        }
        Text desired;
        if (state.level() > 0) {
            desired = Text.literal("Lv." + state.level() + " ").formatted(Formatting.GRAY)
                    .append(Text.literal(base).formatted(Formatting.DARK_GRAY));
        } else {
            desired = Text.literal(base).formatted(Formatting.DARK_GRAY);
        }
        if (!desired.equals(wolf.getCustomName())) {
            wolf.setCustomName(desired);
        }
        wolf.setCustomNameVisible(true);
    }


    private static void renderLabel(CarrierRenderState state, SpeciesDef def, MatrixStack matrices, VertexConsumerProvider consumers,
                                    Vec3d pos, double camX, double camY, double camZ) {
        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer tr = client.textRenderer;
        String base = state.displayName() != null && !state.displayName().isBlank() ? state.displayName() : state.species();
        if (base == null || base.isBlank()) return;
        String label = state.level() > 0 ? ("Lv." + state.level() + " " + base) : base;
        matrices.push();
        float y = Math.max(1.4f, def.sizes.hitboxHeight + 0.95f);
        matrices.translate(pos.x - camX, pos.y - camY + y, pos.z - camZ);
        matrices.multiply(client.gameRenderer.getCamera().getRotation());
        matrices.scale(-0.03f, -0.03f, 0.03f);
        Matrix4f mat = matrices.peek().getPositionMatrix();
        float x = -tr.getWidth(label) / 2.0f;
        tr.draw(label, x, 0, 0x20FFFFFF, false, mat, consumers, TextRenderer.TextLayerType.SEE_THROUGH, 0, 0xF000F0);
        tr.draw(label, x, 0, 0xFFFFFFFF, false, mat, consumers, TextRenderer.TextLayerType.NORMAL, 0, 0xF000F0);
        matrices.pop();
    }


    public static BridgeLabelInfo getBridgeLabelInfo(Entity entity) {
        if (!(entity instanceof WolfEntity wolf)) return null;
        CarrierRenderState state = PokeDemoBridgeClient.entityRenderManager().get(wolf.getUuid()).orElse(null);
        if (state == null) return null;
        SpeciesDef def = speciesDef(state);
        if (def == null) return null;
        String base = state.displayName() != null && !state.displayName().isBlank() ? state.displayName() : state.species();
        if (base == null || base.isBlank()) return null;
        String label = state.level() > 0 ? ("Lv." + state.level() + " " + base) : base;
        float offset = Math.max(1.6f, (def.sizes.hitboxHeight * Math.max(0.6f, state.scale())) + 0.65f);
        return new BridgeLabelInfo(label, offset);
    }

    public record BridgeLabelInfo(String label, float offsetY) {}


    private static java.util.List<CobblemonPoser.ProcAnim> inferProcedures(BedrockGeoModel.LoadedModel model, SpeciesDef def, boolean touchingWater, boolean airborne) {
        java.util.Set<String> bones = model.bonePaths().keySet();
        java.util.function.Predicate<String> has = n -> bones.contains(n);
        java.util.List<CobblemonPoser.ProcAnim> out = new java.util.ArrayList<>();
        if (touchingWater && def.sizes.canSwim) {
            if (has.test("arm_left") && has.test("arm_right")) {
                out.add(CobblemonPoser.ProcAnim.wing(1.2f, 0.8f, 0.0f, 'x', "arm_left", "arm_right"));
            } else if (has.test("flap_left") && has.test("flap_right")) {
                out.add(CobblemonPoser.ProcAnim.wing(1.1f, 0.7f, 0.0f, 'z', "flap_left", "flap_right"));
            } else if (has.test("tentacle_left") && has.test("tentacle_right")) {
                out.add(CobblemonPoser.ProcAnim.wing(1.0f, 0.5f, 0.0f, 'z', "tentacle_left", "tentacle_right"));
            }
            return out;
        }
        if (airborne && def.sizes.canFly) {
            String left = firstBone(bones, "wing_left", "left_wing", "wing_l", "arm_left", "flap_left");
            String right = firstBone(bones, "wing_right", "right_wing", "wing_r", "arm_right", "flap_right");
            if (left != null && right != null) {
                out.add(CobblemonPoser.ProcAnim.wing(1.1f, 1.0f, 0.0f, 'z', left, right));
                return out;
            }
        }
        String ll = firstBone(bones, "left_leg", "leg_left", "leg_l");
        String rl = firstBone(bones, "right_leg", "leg_right", "leg_r");
        String fl = firstBone(bones, "front_left_leg", "leg_front_left");
        String fr = firstBone(bones, "front_right_leg", "leg_front_right");
        String bl = firstBone(bones, "back_left_leg", "leg_back_left", ll);
        String br = firstBone(bones, "back_right_leg", "leg_back_right", rl);
        if (fl != null && fr != null && bl != null && br != null) {
            out.add(CobblemonPoser.ProcAnim.quadruped(1.0f, 1.0f, fl, fr, bl, br));
            return out;
        }
        if (ll != null && rl != null) {
            out.add(CobblemonPoser.ProcAnim.biped(1.0f, 1.0f, ll, rl));
            return out;
        }
        String al = firstBone(bones, "arm_left", "left_arm", "flipper_left");
        String ar = firstBone(bones, "arm_right", "right_arm", "flipper_right");
        if (al != null && ar != null) {
            out.add(CobblemonPoser.ProcAnim.biped(1.0f, 0.7f, al, ar));
        }
        return out;
    }

    private static String firstBone(java.util.Set<String> bones, String... names) {
        for (String n : names) {
            if (n != null && bones.contains(n)) return n;
        }
        for (String bone : bones) {
            String low = bone.toLowerCase(java.util.Locale.ROOT);
            for (String n : names) {
                if (n != null && low.equals(n.toLowerCase(java.util.Locale.ROOT))) return bone;
            }
        }
        return null;
    }
    private static void applyProcedures(BedrockGeoModel.LoadedModel model, List<CobblemonPoser.ProcAnim> procedures, float seconds) {
        if (procedures == null) return;
        for (CobblemonPoser.ProcAnim proc : procedures) {
            switch (proc.kind) {
                case BIPED -> {
                    float swing = (float) Math.sin(seconds * Math.PI * 2.0 * proc.speed) * (0.55f * proc.amplitude);
                    rotatePart(model.findPart(proc.bones[0]), swing, 0f, 0f);
                    rotatePart(model.findPart(proc.bones[1]), -swing, 0f, 0f);
                }
                case QUADRUPED -> {
                    float swing = (float) Math.sin(seconds * Math.PI * 2.0 * proc.speed) * (0.45f * proc.amplitude);
                    rotatePart(model.findPart(proc.bones[0]), swing, 0f, 0f);
                    rotatePart(model.findPart(proc.bones[1]), -swing, 0f, 0f);
                    rotatePart(model.findPart(proc.bones[2]), -swing, 0f, 0f);
                    rotatePart(model.findPart(proc.bones[3]), swing, 0f, 0f);
                }
                case WING -> {
                    float flap = (float) Math.sin(seconds * Math.PI * 2.0 * proc.speed + proc.phase) * (0.35f * proc.amplitude);
                    if (proc.axis == 'x') {
                        rotatePart(model.findPart(proc.bones[0]), flap, 0f, 0f);
                        rotatePart(model.findPart(proc.bones[1]), -flap, 0f, 0f);
                    } else if (proc.axis == 'y') {
                        rotatePart(model.findPart(proc.bones[0]), 0f, flap, 0f);
                        rotatePart(model.findPart(proc.bones[1]), 0f, -flap, 0f);
                    } else {
                        rotatePart(model.findPart(proc.bones[0]), 0f, 0f, flap);
                        rotatePart(model.findPart(proc.bones[1]), 0f, 0f, -flap);
                    }
                }
            }
        }
    }

    private static void rotatePart(ModelPart part, float pitch, float yaw, float roll) {
        if (part == null) return;
        part.pitch += pitch;
        part.yaw += yaw;
        part.roll += roll;
    }



    private static boolean shouldAquaticDive(WolfEntity wolf, CobblemonSpeciesDisplayConfig sizes, boolean moving) {
        var world = wolf.getEntityWorld();
        if (world == null || !moving) return false;
        if (!wolf.isTouchingWater()) return false;
        BlockPos feet = BlockPos.ofFloored(wolf.getX(), wolf.getY(), wolf.getZ());
        int waterBelow = 0;
        for (int i = 1; i <= 4; i++) {
            if (world.getFluidState(feet.down(i)).isOf(Fluids.WATER)) waterBelow++;
            else break;
        }
        BlockPos body = BlockPos.ofFloored(wolf.getX(), wolf.getY() + Math.max(0.35, wolf.getHeight() * 0.35), wolf.getZ());
        boolean waterAtBody = world.getFluidState(body).isOf(Fluids.WATER);
        return waterAtBody && waterBelow >= 2;
    }

    private static boolean isSubmergedSwim(WolfEntity wolf) {
        var world = wolf.getEntityWorld();
        if (world == null) return false;
        BlockPos body = BlockPos.ofFloored(wolf.getX(), wolf.getY() + Math.max(0.35, wolf.getHeight() * 0.35), wolf.getZ());
        BlockPos head = BlockPos.ofFloored(wolf.getX(), wolf.getY() + Math.max(0.8, wolf.getHeight() * 0.9), wolf.getZ());
        boolean waterAtBody = world.getFluidState(body).isOf(Fluids.WATER);
        boolean waterAtHead = world.getFluidState(head).isOf(Fluids.WATER);
        return waterAtBody && waterAtHead;
    }

    private static float submergeOffset(CobblemonSpeciesDisplayConfig sizes) {
        return -Math.min(0.75f, Math.max(0.18f, sizes.hitboxHeight * 0.22f));
    }

    private static float computeWorldScale(BedrockGeoModel.LoadedModel model, CobblemonSpeciesDisplayConfig sizes) {
        float targetHeightBlocks = sizes.hitboxHeight > 0.0f
                ? sizes.hitboxHeight
                : (sizes.speciesHeight > 0.0f ? (sizes.speciesHeight / 10.0f) : 1.0f);
        targetHeightBlocks = Math.max(0.35f, targetHeightBlocks);
        float modelHeightUnits = Math.max(0.1f, model.visibleH());
        final float BEDROCK_TO_WORLD = 2.5f;
        float base = (targetHeightBlocks / modelHeightUnits) * BEDROCK_TO_WORLD;
        float speciesScale = sizes.baseScale > 0.0f ? sizes.baseScale : 1.0f;
        float normalizedSpeciesScale = 0.85f + ((speciesScale - 1.0f) * 0.35f);
        return base * normalizedSpeciesScale;
    }

    private static PoseSelection pickPose(CarrierRenderState state, SpeciesDef def, boolean moving,
                                          boolean touchingWater, boolean submergedSwim, boolean airborne, boolean sleeping,
                                          double horizontalSpeedSq) {
        String animHint = state.animation() == null ? "" : state.animation().toLowerCase(Locale.ROOT).trim();
        String moveMode = state.moveMode() == null ? "land_idle" : state.moveMode().toLowerCase(Locale.ROOT).trim();
        CobblemonPoser poser = def.poser;

        if (animHint.contains("volttackle") && poser.voltTackleClip != null) return new PoseSelection(null, poser.voltTackleClip);
        if (animHint.contains("physical") && poser.physicalClip != null) return new PoseSelection(null, poser.physicalClip);
        if (animHint.contains("special") && poser.specialClip != null) return new PoseSelection(null, poser.specialClip);
        if (animHint.contains("status") && poser.statusClip != null) return new PoseSelection(null, poser.statusClip);
        if (animHint.contains("sendout") || animHint.contains("summon") || animHint.contains("emerge")) {
            if (poser.sendoutClip != null) return new PoseSelection(null, poser.sendoutClip);
            if (poser.cryClip != null) return new PoseSelection(null, poser.cryClip);
        }
        if (animHint.contains("withdraw") || animHint.contains("return") || animHint.contains("recall")) {
            if (poser.withdrawClip != null) return new PoseSelection(null, poser.withdrawClip);
        }
        if (animHint.contains("sleep_enter") || animHint.contains("sleep-start") || animHint.contains("fall_asleep")) {
            if (poser.sleepPose != null) return new PoseSelection(poser.sleepPose, null);
        }
        if (animHint.contains("faint") || animHint.contains("death") || animHint.contains("knockout")) {
            if (poser.faintClip != null) return new PoseSelection(null, poser.faintClip);
        }
        if (animHint.contains("wake") || animHint.contains("wakeup")) {
            if (poser.wakeClip != null) return new PoseSelection(null, poser.wakeClip);
        }
        if (animHint.contains("hit")) {
            if (poser.hitClip != null) return new PoseSelection(null, poser.hitClip);
        }
        if (animHint.contains("recoil")) {
            if (animHint.contains("battle") && poser.battleRecoilClip != null) return new PoseSelection(null, poser.battleRecoilClip);
            if (poser.recoilClip != null) return new PoseSelection(null, poser.recoilClip);
        }
        if (animHint.contains("cry")) {
            if (animHint.contains("battle") && poser.battleCryClip != null) return new PoseSelection(null, poser.battleCryClip);
            if (poser.cryClip != null) return new PoseSelection(null, poser.cryClip);
        }
        if (sleeping && poser.sleepPose != null) return new PoseSelection(poser.sleepPose, null);
        if (moveMode.startsWith("battle") || animHint.contains("battle")) {
            if (poser.battleStandingPose != null) return new PoseSelection(poser.battleStandingPose, null);
        }
        if (moveMode.startsWith("air")) {
            if (moveMode.contains("follow") || moving || state.moveSpeed() > 0.035f || horizontalSpeedSq > 0.0012D) {
                return new PoseSelection(firstPose(poser.flyPose, poser.hoverPose, poser.walkingPose, poser.standingPose), null);
            }
            return new PoseSelection(firstPose(poser.hoverPose, poser.flyPose, poser.standingPose), null);
        }
        if (moveMode.startsWith("underwater")) {
            if (moveMode.contains("swim") || moving || state.moveSpeed() > 0.03f) return new PoseSelection(firstPose(poser.swimPose, poser.surfaceSwimPose, poser.walkingPose, poser.standingPose), null);
            return new PoseSelection(firstPose(poser.floatPose, poser.surfaceFloatPose, poser.standingPose), null);
        }
        if (moveMode.startsWith("surface_swim") || moveMode.startsWith("water")) {
            if (submergedSwim) {
                if (moveMode.contains("swim") || moving || state.moveSpeed() > 0.03f) return new PoseSelection(firstPose(poser.swimPose, poser.surfaceSwimPose, poser.walkingPose, poser.standingPose), null);
                return new PoseSelection(firstPose(poser.floatPose, poser.surfaceFloatPose, poser.standingPose), null);
            }
            if (moveMode.contains("swim") || moving || state.moveSpeed() > 0.025f) return new PoseSelection(firstPose(poser.surfaceSwimPose, poser.swimPose, poser.walkingPose, poser.standingPose), null);
            return new PoseSelection(firstPose(poser.surfaceFloatPose, poser.floatPose, poser.standingPose), null);
        }
        if (touchingWater && def.sizes.canSwim) {
            if (submergedSwim) {
                if (moving) return new PoseSelection(firstPose(poser.swimPose, poser.surfaceSwimPose, poser.walkingPose, poser.standingPose), null);
                return new PoseSelection(firstPose(poser.floatPose, poser.surfaceFloatPose, poser.standingPose), null);
            }
            if (moving) return new PoseSelection(firstPose(poser.surfaceSwimPose, poser.swimPose, poser.walkingPose, poser.standingPose), null);
            return new PoseSelection(firstPose(poser.surfaceFloatPose, poser.floatPose, poser.standingPose), null);
        }
        if (airborne && def.sizes.canFly) {
            if (moving || horizontalSpeedSq > 0.005D) return new PoseSelection(firstPose(poser.flyPose, poser.hoverPose, poser.walkingPose, poser.standingPose), null);
            return new PoseSelection(firstPose(poser.hoverPose, poser.flyPose, poser.standingPose), null);
        }
        if (moveMode.contains("land_walk")) {
            if (poser.runningPose != null && state.moveSpeed() > 0.42f) return new PoseSelection(poser.runningPose, null);
            return new PoseSelection(firstPose(poser.walkingPose, poser.runningPose, poser.standingPose), null);
        }
        if (moving) {
            if ((state.moveSpeed() > 0.30f || horizontalSpeedSq > 0.018D) && poser.runningPose != null && state.moveSpeed() > 0.40f) {
                return new PoseSelection(poser.runningPose, null);
            }
            return new PoseSelection(firstPose(poser.walkingPose, poser.runningPose, poser.standingPose), null);
        }
        return new PoseSelection(firstPose(poser.standingPose, poser.walkingPose), null);
    }

    private static CobblemonPoser.PoseDef firstPose(CobblemonPoser.PoseDef... defs) {
        for (CobblemonPoser.PoseDef def : defs) {
            if (def != null) return def;
        }
        return CobblemonPoser.PoseDef.EMPTY;
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
        if (cobblemonTexture == null || cobblemonTexture.isBlank()) {
            return Identifier.of("pokedemo_bridge", "textures/entity/pikachu.png");
        }

        String raw = cobblemonTexture.trim();
        Identifier direct = null;
        try {
            direct = Identifier.of(raw);
        } catch (Exception ignored) {
        }
        if (direct != null) {
            String directCp = "assets/" + direct.getNamespace() + "/" + direct.getPath();
            if (BridgeCarrierWorldRenderer.class.getClassLoader().getResource(directCp) != null) {
                return direct;
            }
        }

        String path = raw;
        String namespace = "cobblemon";
        int colon = path.indexOf(':');
        if (colon >= 0) {
            namespace = path.substring(0, colon);
            path = path.substring(colon + 1);
        }

        Identifier namespaced = Identifier.of(namespace, path);
        String namespacedCp = "assets/" + namespaced.getNamespace() + "/" + namespaced.getPath();
        if (BridgeCarrierWorldRenderer.class.getClassLoader().getResource(namespacedCp) != null) {
            return namespaced;
        }

        String rel = path;
        int texturesIdx = rel.indexOf("textures/");
        if (texturesIdx >= 0) {
            rel = rel.substring(texturesIdx);
        } else {
            rel = "textures/" + rel;
        }

        Identifier sameNamespaceTexture = Identifier.of(namespace, rel);
        String sameNamespaceTextureCp = "assets/" + sameNamespaceTexture.getNamespace() + "/" + sameNamespaceTexture.getPath();
        if (BridgeCarrierWorldRenderer.class.getClassLoader().getResource(sameNamespaceTextureCp) != null) {
            return sameNamespaceTexture;
        }

        String relNoPrefix = rel.startsWith("textures/") ? rel.substring("textures/".length()) : rel;
        Identifier bridgeNested = Identifier.of("pokedemo_bridge", "textures/entity/" + relNoPrefix);
        String bridgeNestedCp = "assets/" + bridgeNested.getNamespace() + "/" + bridgeNested.getPath();
        if (BridgeCarrierWorldRenderer.class.getClassLoader().getResource(bridgeNestedCp) != null) {
            return bridgeNested;
        }

        String base = path.substring(path.lastIndexOf('/') + 1);
        Identifier bridgeBase = Identifier.of("pokedemo_bridge", "textures/entity/" + base);
        String bridgeBaseCp = "assets/" + bridgeBase.getNamespace() + "/" + bridgeBase.getPath();
        if (BridgeCarrierWorldRenderer.class.getClassLoader().getResource(bridgeBaseCp) != null) {
            return bridgeBase;
        }

        return Identifier.of("pokedemo_bridge", "textures/entity/pikachu.png");
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


    private static double updateFrameMotionSq(WolfEntity wolf, Vec3d pos) {
        if (wolf == null || pos == null) return 0.0D;
        UUID key = wolf.getUuid();
        Vec3d prev = LAST_POSITIONS.put(key, pos);
        if (prev == null) return 0.0D;
        double dx = pos.x - prev.x;
        double dz = pos.z - prev.z;
        double dy = pos.y - prev.y;
        return dx * dx + dz * dz + dy * dy;
    }

    private static boolean isActuallyMoving(CarrierRenderState state, String moveMode, double horizontalSpeedSq, double frameMotionSq, WolfEntity wolf) {
        double planarFrame = frameMotionSq;
        boolean airborne = wolf != null && !wolf.isOnGround();
        if (moveMode.contains("land_walk") || moveMode.contains("air_follow") || moveMode.contains("surface_swim") || moveMode.contains("underwater_swim")) {
            return true;
        }
        if (moveMode.contains("air") || moveMode.contains("hover")) {
            return state.moveSpeed() > 0.08f || horizontalSpeedSq > 0.0036D || planarFrame > 0.0008D;
        }
        if (moveMode.contains("swim") || moveMode.contains("water")) {
            return state.moveSpeed() > 0.06f || horizontalSpeedSq > 0.0016D || planarFrame > 0.0004D;
        }
        if (airborne) {
            return state.moveSpeed() > 0.07f || horizontalSpeedSq > 0.0025D || planarFrame > 0.0006D;
        }
        return state.moveSpeed() > 0.06f || horizontalSpeedSq > 0.0025D || planarFrame > 0.0005D;
    }

    private static boolean smoothMoving(WolfEntity wolf, boolean movingNow) {
        if (wolf == null) return movingNow;
        UUID key = wolf.getUuid();
        int ticks = movingNow ? Math.min(6, MOVE_TICKS.getOrDefault(key, 0) + 1) : Math.max(0, MOVE_TICKS.getOrDefault(key, 0) - 1);
        MOVE_TICKS.put(key, ticks);
        return ticks >= 2;
    }

    private static int updateStillTicks(WolfEntity wolf, boolean moving) {
        UUID key = wolf.getUuid();
        int ticks = moving ? 0 : (STILL_TICKS.getOrDefault(key, 0) + 1);
        STILL_TICKS.put(key, ticks);
        return ticks;
    }

    private static void ensureLoaded() {
        if (triedLoad) return;
        triedLoad = true;
        InputStream in = BridgeCarrierWorldRenderer.class.getClassLoader().getResourceAsStream("assets/pokedemo_bridge/all_species_index.json");
        String indexName = "all_species_index.json";
        if (in == null) {
            in = BridgeCarrierWorldRenderer.class.getClassLoader().getResourceAsStream("assets/pokedemo_bridge/gen1_species_index.json");
            indexName = "gen1_species_index.json";
        }
        try (InputStream autoClose = in;
             InputStreamReader reader = autoClose == null ? null : new InputStreamReader(autoClose, StandardCharsets.UTF_8)) {
            if (reader == null) {
                LOGGER.error("Missing species index: all_species_index.json / gen1_species_index.json");
                return;
            }
            com.google.gson.JsonArray arr = com.google.gson.JsonParser.parseReader(reader).getAsJsonArray();
            for (com.google.gson.JsonElement el : arr) {
                if (!el.isJsonObject()) continue;
                com.google.gson.JsonObject o = el.getAsJsonObject();
                String key = o.get("key").getAsString();
                Identifier speciesJson = Identifier.of("pokedemo_bridge", "species/" + key + ".json");
                Identifier poserJson = Identifier.of("pokedemo_bridge", "cobblemon/" + key + "/" + key + ".poser.json");
                Identifier resolverJson = Identifier.of("pokedemo_bridge", "cobblemon/" + key + "/" + key + ".resolver.json");
                Identifier animationJson = Identifier.of("pokedemo_bridge", "cobblemon/" + key + "/" + key + ".animation.json");
                Map<String, Identifier> models = new HashMap<>();
                com.google.gson.JsonArray ms = o.getAsJsonArray("models");
                for (com.google.gson.JsonElement me : ms) {
                    String model = me.getAsString();
                    models.put(model.replace(".json", ""), Identifier.of("pokedemo_bridge", "cobblemon/" + key + "/" + model));
                }
                loadSpecies(key, speciesJson, poserJson, resolverJson, animationJson, models);
            }
            LOGGER.info("Loaded {} species pipelines from {}.", SPECIES.size(), indexName);
        } catch (Exception ex) {
            LOGGER.error("Failed to load species index", ex);
        }
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
            SPECIES.put(key, new SpeciesDef(key, loadedModels, sizes, poser, resolver, animations));
            LOGGER.info("Loaded {} pipeline. standing={}, walking={}, fly={}, hover={}, swim={}, models={}", key,
                    poser.standingPose.clip, poser.walkingPose.clip, poser.flyPose.clip, poser.hoverPose.clip, poser.swimPose.clip, loadedModels.keySet());
        } catch (IOException | RuntimeException ex) {
            LOGGER.error("Failed to load {} pipeline", key, ex);
        }
    }

    private record SpeciesDef(String key,
                              Map<String, BedrockGeoModel.LoadedModel> models,
                              CobblemonSpeciesDisplayConfig sizes,
                              CobblemonPoser poser,
                              CobblemonResolver resolver,
                              BedrockAnimations animations) {}

    private record PoseSelection(CobblemonPoser.PoseDef pose, String namedClip) {}

    private BridgeCarrierWorldRenderer() {}
}
