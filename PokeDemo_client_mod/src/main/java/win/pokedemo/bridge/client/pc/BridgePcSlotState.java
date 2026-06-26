package win.pokedemo.bridge.client.pc;

import win.pokedemo.bridge.common.PokemonStatus;

import java.util.UUID;

public record BridgePcSlotState(
        int pageSlot,
        int absoluteIndex,
        UUID pokemonUuid,
        boolean occupied,
        String species,
        String displayName,
        int level,
        int hp,
        int maxHp,
        PokemonStatus status,
        String gender,
        boolean shiny,
        String heldItemId,
        boolean egg,
        boolean locked,
        String lockReason
) {
    public static BridgePcSlotState empty(int slot) {
        return new BridgePcSlotState(slot, -1, null, false, "", "", 0, 0, 0, PokemonStatus.NONE, "N", false, "", false, false, "");
    }
}
