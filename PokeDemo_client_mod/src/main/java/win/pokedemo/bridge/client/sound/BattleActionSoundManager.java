package win.pokedemo.bridge.client.sound;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.Entity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import win.pokedemo.bridge.client.PokeDemoBridgeClient;
import win.pokedemo.bridge.common.CarrierRenderState;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class BattleActionSoundManager {
    private final Map<UUID, String> lastAnimation = new HashMap<>();
    private final Map<String, Integer> cooldownUntilTick = new HashMap<>();

    public void clear() {
        lastAnimation.clear();
        cooldownUntilTick.clear();
    }

    public void tick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) {
            clear();
            return;
        }
        int age = client.player.age;
        cooldownUntilTick.entrySet().removeIf(e -> e.getValue() <= age);
        for (CarrierRenderState state : PokeDemoBridgeClient.entityRenderManager().snapshot().values()) {
            if (state == null) continue;
            Entity entity = client.world.getEntityById(state.entityId());
            if (entity == null) continue;
            if (client.player.squaredDistanceTo(entity) > (40 * 40)) continue;
            String anim = state.animation() == null ? "" : state.animation().toLowerCase(Locale.ROOT).trim();
            if (anim.isBlank()) continue;
            String prev = lastAnimation.put(state.entityUuid(), anim);
            if (anim.equals(prev)) continue;
            handleAnimationChange(client, age, entity, state, anim);
        }
    }

    private void handleAnimationChange(MinecraftClient client, int age, Entity entity, CarrierRenderState state, String anim) {
        String moveId = parseMoveId(anim);
        if (moveId != null) {
            playMoveActor(client, age, entity, state, moveId);
            spawnMoveActorParticles(client, entity, moveId, anim);
            return;
        }
        if (anim.startsWith("hit_")) {
            String type = anim.substring("hit_".length()).trim();
            if (type.isEmpty()) type = "normal";
            playImpact(client, age, entity, type);
            spawnImpactParticles(client, entity, type);
            return;
        }
        if (anim.contains("recoil")) {
            playSound(client, entity, Identifier.of("cobblemon", "impact.normal"), 0.85f, 1.0f, age, "recoil:" + state.entityUuid(), 8);
            spawnSimpleBurst(client, entity, ParticleTypes.POOF, 5, 0.18D);
        }
    }

    private String parseMoveId(String anim) {
        int idx = anim.indexOf("move:");
        if (idx < 0) return null;
        String tail = anim.substring(idx + 5);
        int cut = tail.indexOf('|');
        if (cut >= 0) tail = tail.substring(0, cut);
        tail = tail.trim().toLowerCase(Locale.ROOT);
        return tail.isEmpty() ? null : tail;
    }

    private void playMoveActor(MinecraftClient client, int age, Entity entity, CarrierRenderState state, String moveId) {
        Identifier soundId = switch (moveId) {
            case "quickattack" -> Identifier.of("cobblemon", "move.quickattack.actor");
            case "watergun" -> Identifier.of("cobblemon", "move.watergun.actor");
            case "ember" -> Identifier.of("cobblemon", "move.ember.actor");
            case "razorleaf" -> Identifier.of("cobblemon", ThreadLocalRandom.current().nextBoolean() ? "move.razorleaf.actor_1" : "move.razorleaf.actor_2");
            case "growl" -> Identifier.of("cobblemon", "pokemon." + state.species().toLowerCase(Locale.ROOT) + ".cry");
            default -> null;
        };
        if (soundId != null) {
            playSound(client, entity, soundId, 0.95f, 1.0f, age, "move:" + moveId + ":" + state.entityUuid(), 10);
        }
    }

    private void playImpact(MinecraftClient client, int age, Entity entity, String type) {
        playSound(client, entity, Identifier.of("cobblemon", "impact." + type), 0.95f, 1.0f, age, "impact:" + type + ":" + entity.getId(), 6);
    }

    private void playSound(MinecraftClient client, Entity entity, Identifier soundId, float volume, float pitch, int age, String key, int cooldownTicks) {
        Integer until = cooldownUntilTick.get(key);
        if (until != null && age < until) return;
        cooldownUntilTick.put(key, age + cooldownTicks);
        client.getSoundManager().play(PositionedSoundInstance.ambient(SoundEvent.of(soundId), volume, pitch));
    }

    private void spawnMoveActorParticles(MinecraftClient client, Entity entity, String moveId, String anim) {
        switch (moveId) {
            case "tackle", "quickattack" -> { spawnSimpleBurst(client, entity, ParticleTypes.CLOUD, 10, 0.28D); spawnSimpleBurst(client, entity, ParticleTypes.CRIT, 6, 0.22D); }
            case "tailwhip" -> spawnSimpleBurst(client, entity, ParticleTypes.SWEEP_ATTACK, 4, 0.14D);
            case "growl" -> spawnSimpleBurst(client, entity, ParticleTypes.NOTE, 12, 0.42D);
            case "watergun" -> { spawnSimpleBurst(client, entity, ParticleTypes.SPLASH, 28, 0.52D); spawnSimpleBurst(client, entity, ParticleTypes.BUBBLE, 16, 0.44D); }
            case "ember" -> {
                spawnSimpleBurst(client, entity, ParticleTypes.FLAME, 18, 0.36D);
                spawnSimpleBurst(client, entity, ParticleTypes.SMOKE, 12, 0.38D);
            }
            case "vinewhip", "razorleaf", "leafage", "magicalleaf" -> spawnSimpleBurst(client, entity, ParticleTypes.COMPOSTER, 14, 0.34D);
            case "thundershock", "thunderbolt", "thunderwave", "spark" -> spawnSimpleBurst(client, entity, ParticleTypes.ELECTRIC_SPARK, 16, 0.28D);
            case "bubble", "bubblebeam", "surf", "hydropump" -> { spawnSimpleBurst(client, entity, ParticleTypes.SPLASH, 22, 0.48D); spawnSimpleBurst(client, entity, ParticleTypes.BUBBLE, 18, 0.42D); }
            case "gust", "airslash", "wingattack" -> spawnSimpleBurst(client, entity, ParticleTypes.CLOUD, 14, 0.34D);
            case "poisonpowder", "stunspore", "sleeppowder", "spore" -> spawnSimpleBurst(client, entity, ParticleTypes.HAPPY_VILLAGER, 12, 0.40D);
            default -> {
                if (anim.contains("attack_special")) spawnSimpleBurst(client, entity, ParticleTypes.ENCHANT, 8, 0.24D);
                else if (anim.contains("attack_status")) spawnSimpleBurst(client, entity, ParticleTypes.NOTE, 6, 0.28D);
                else spawnSimpleBurst(client, entity, ParticleTypes.CRIT, 6, 0.20D);
            }
        }
    }

    private void spawnImpactParticles(MinecraftClient client, Entity entity, String type) {
        switch (type) {
            case "water" -> spawnSimpleBurst(client, entity, ParticleTypes.SPLASH, 7, 0.24D);
            case "fire" -> {
                spawnSimpleBurst(client, entity, ParticleTypes.FLAME, 5, 0.22D);
                spawnSimpleBurst(client, entity, ParticleTypes.SMOKE, 4, 0.26D);
            }
            case "grass" -> spawnSimpleBurst(client, entity, ParticleTypes.COMPOSTER, 8, 0.25D);
            case "electric" -> spawnSimpleBurst(client, entity, ParticleTypes.ELECTRIC_SPARK, 8, 0.2D);
            default -> spawnSimpleBurst(client, entity, ParticleTypes.CRIT, 6, 0.18D);
        }
    }

    private void spawnSimpleBurst(MinecraftClient client, Entity entity, net.minecraft.particle.ParticleEffect particle, int count, double spread) {
        if (client.world == null) return;
        Vec3d pos = new Vec3d(entity.getX(), entity.getY() + Math.max(0.45D, entity.getHeight() * 0.45D), entity.getZ());
        for (int i = 0; i < count; i++) {
            double ox = (ThreadLocalRandom.current().nextDouble() - 0.5D) * spread * 2.0D;
            double oy = (ThreadLocalRandom.current().nextDouble() - 0.35D) * spread;
            double oz = (ThreadLocalRandom.current().nextDouble() - 0.5D) * spread * 2.0D;
            double vx = ox * 0.08D;
            double vy = 0.02D + Math.abs(oy) * 0.04D;
            double vz = oz * 0.08D;
            client.particleManager.addParticle(particle, pos.x + ox, pos.y + oy, pos.z + oz, vx, vy, vz);
        }
    }
}
