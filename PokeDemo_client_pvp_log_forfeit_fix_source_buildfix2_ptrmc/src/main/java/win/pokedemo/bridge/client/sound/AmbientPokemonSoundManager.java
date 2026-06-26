
package win.pokedemo.bridge.client.sound;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.Entity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import win.pokedemo.bridge.client.PokeDemoBridgeClient;
import win.pokedemo.bridge.common.CarrierRenderState;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public final class AmbientPokemonSoundManager {
    private final Map<UUID, Integer> cryUntilTick = new HashMap<>();
    private final Map<UUID, Integer> nextCryTick = new HashMap<>();
    private final Set<String> soundableSpecies = new HashSet<>();

    public void clear() {
        cryUntilTick.clear();
        nextCryTick.clear();
    }

    public String overrideAnimation(CarrierRenderState state, String baseAnimationHint) {
        if (state == null) return baseAnimationHint;
        Integer until = cryUntilTick.get(state.entityUuid());
        if (until == null) return baseAnimationHint;
        MinecraftClient client = MinecraftClient.getInstance();
        int age = client.player != null ? client.player.age : 0;
        if (age >= until) {
            cryUntilTick.remove(state.entityUuid());
            return baseAnimationHint;
        }
        String base = baseAnimationHint == null ? "" : baseAnimationHint.trim();
        if (base.isEmpty()) return "cry";
        if (!base.toLowerCase(Locale.ROOT).contains("cry")) return base + "|cry";
        return baseAnimationHint;
    }

    public void tick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) {
            clear();
            return;
        }
        int age = client.player.age;
        cryUntilTick.entrySet().removeIf(e -> e.getValue() <= age);
        var snapshot = PokeDemoBridgeClient.entityRenderManager().snapshot();
        for (CarrierRenderState state : snapshot.values()) {
            if (state == null || state.species() == null || state.species().isBlank()) continue;
            Entity e = client.world.getEntityById(state.entityId());
            if (e == null) continue;
            if (client.player.squaredDistanceTo(e) > (26 * 26)) continue;
            UUID id = state.entityUuid();
            int next = nextCryTick.getOrDefault(id, age + ThreadLocalRandom.current().nextInt(180, 320));
            if (age < next) continue;
            nextCryTick.put(id, age + ThreadLocalRandom.current().nextInt(420, 900));
            triggerCry(state);
        }
    }

    public void triggerCry(CarrierRenderState state) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null || state == null || state.species() == null) return;
        String species = state.species().toLowerCase(Locale.ROOT).trim();
        UUID id = state.entityUuid();
        int age = client.player.age;
        int nextAllowed = nextCryTick.getOrDefault(id, -1);
        if (age < nextAllowed) return;
        Identifier soundId = resolveCryOrAmbient(species);
        if (soundId == null) return;
        client.getSoundManager().play(PositionedSoundInstance.ambient(SoundEvent.of(soundId), 0.9f, 1.0f));
        cryUntilTick.put(id, age + 28);
        nextCryTick.put(id, age + 90);
    }

    private Identifier resolveCryOrAmbient(String species) {
        if (species == null || species.isBlank()) return null;
        if (hasResource("sounds/pokemon/" + species + "/" + species + "_cry.ogg")) {
            soundableSpecies.add(species);
            return Identifier.of("cobblemon", "pokemon." + species + ".cry");
        }
        if (hasResource("sounds/pokemon/" + species + "/" + species + "_ambient.ogg")) {
            soundableSpecies.add(species);
            return Identifier.of("cobblemon", "pokemon." + species + ".ambient");
        }
        return null;
    }

    private boolean hasCry(String species) {
        if (soundableSpecies.contains(species)) return true;
        return resolveCryOrAmbient(species) != null;
    }

    private boolean hasResource(String path) {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.getResourceManager().getResource(Identifier.of("cobblemon", path)).isPresent();
    }
}
