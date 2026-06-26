package win.pokedemo.bridge.common;

import java.util.UUID;

public record CarrierRenderState(
        UUID entityUuid,
        int entityId,
        UUID ownerUuid,
        int slot,
        UUID pokemonUuid,
        String species,
        String form,
        String gender,
        boolean shiny,
        String animation,
        float scale,
        String displayName,
        int level,
        String moveMode,
        float moveSpeed,
        boolean airborne,
        boolean submerged,
        boolean sleeping,
        boolean battle,
        float battleYaw
) {
    public CarrierRenderState withAnimation(String newAnimation) {
        return new CarrierRenderState(entityUuid, entityId, ownerUuid, slot, pokemonUuid, species, form, gender, shiny, newAnimation, scale, displayName, level, moveMode, moveSpeed, airborne, submerged, sleeping, battle, battleYaw);
    }
}
