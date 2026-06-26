
package win.pokedemo;

import java.util.*;

public class PlayerProfile {
    public UUID playerId;
    public List<PokemonInstance> party = new ArrayList<>(); // max 6
    public List<PokemonInstance> pc = new ArrayList<>(); // unlimited for demo
    public int pcPageSize = 54;

    /** Whether the player has already chosen a starter Pokemon. */
    public boolean starterChosen = false;

    /** Starter species id (e.g. "bulbasaur"). */
    public String starterSpeciesId = null;

    /** Caught Pokédex entries (species ids). Used by the Pokédex GUI. */
    public java.util.Set<String> dexCaught = new java.util.HashSet<>();

    public PlayerProfile(UUID playerId) {
        this.playerId = playerId;
    }

    public void depositToPartyOrPc(PokemonInstance p) {
        if (party.size() < 6) party.add(p);
        else pc.add(p);
    }

    
    public PokemonInstance findByUuid(UUID uuid) {
        if (uuid == null) return null;
        for (PokemonInstance p : party) {
            if (p != null && uuid.equals(p.uuid)) return p;
        }
        for (PokemonInstance p : pc) {
            if (p != null && uuid.equals(p.uuid)) return p;
        }
        return null;
    }

    public boolean canDepositLastParty() {
        // Cobblemon has preventCompletePartyDeposit; keep party at least 1 if setting enabled later
        return true;
    }
}
