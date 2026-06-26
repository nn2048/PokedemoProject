package win.pokedemo.bridge.client.model;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import win.pokedemo.bridge.client.PokeDemoBridgeClient;
import win.pokedemo.bridge.common.CarrierRenderState;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stage-1 render manager.
 * Stores per-entity desired species/model state and resolves local asset paths.
 * Actual model rendering is intentionally left as a contained TODO instead of pretending it is finished.
 */
public final class BridgeEntityRenderManager {
    private final Map<UUID, CarrierRenderState> states = new ConcurrentHashMap<>();
    private final BedrockAssetResolver resolver = new BedrockAssetResolver();

    public void upsert(CarrierRenderState state) {
        CarrierRenderState previous = states.put(state.entityUuid(), state);
        String prevAnim = previous == null || previous.animation() == null ? "" : previous.animation().toLowerCase(java.util.Locale.ROOT);
        String nextAnim = state.animation() == null ? "" : state.animation().toLowerCase(java.util.Locale.ROOT);
        if (!Objects.equals(prevAnim, nextAnim) && nextAnim.contains("cry")) {
            try { PokeDemoBridgeClient.ambientSounds().triggerCry(state); } catch (Throwable ignored) {}
        }
    }

    public void remove(UUID uuid) {
        states.remove(uuid);
    }

    public Optional<CarrierRenderState> get(UUID uuid) {
        return Optional.ofNullable(states.get(uuid));
    }

    public Optional<BedrockAssetResolver.SpeciesAssetSet> resolveAssets(Entity entity) {
        CarrierRenderState state = states.get(entity.getUuid());
        if (state == null) {
            return Optional.empty();
        }
        return resolver.resolve(state.species(), state.gender());
    }

    public Map<UUID, CarrierRenderState> snapshot() {
        return Collections.unmodifiableMap(states);
    }

    public void clear() {
        states.clear();
    }

    public void tick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            return;
        }
        states.entrySet().removeIf(entry -> client.world.getEntityById(entry.getValue().entityId()) == null);
    }
}
