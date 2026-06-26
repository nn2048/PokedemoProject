package win.pokedemo.bridge.common;

import java.util.UUID;

public record PartySlotState(
        int slot,
        UUID pokemonUuid,
        boolean occupied,
        String species,
        String displayName,
        int level,
        int hp,
        int maxHp,
        PokemonStatus status,
        boolean active,
        String gender,
        boolean shiny,
        String heldItemId,
        String ballId
) {}
