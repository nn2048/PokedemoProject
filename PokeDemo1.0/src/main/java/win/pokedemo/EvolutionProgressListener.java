package win.pokedemo;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stage 6 evolution progress tracking.
 * We use a plugin-friendly simplification for Let's Go step evolutions:
 * steps are counted while the relevant Pokémon is actively summoned.
 */
public final class EvolutionProgressListener implements Listener {
    private final PokeDemoPlugin plugin;
    private final Storage storage;
    private final SummonManager summons;
    private final EvolutionManager evolutions;
    private final Map<UUID, Long> lastBlock = new ConcurrentHashMap<>();

    public EvolutionProgressListener(PokeDemoPlugin plugin, Storage storage, SummonManager summons, EvolutionManager evolutions) {
        this.plugin = plugin;
        this.storage = storage;
        this.summons = summons;
        this.evolutions = evolutions;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (p == null) return;
        Location to = e.getTo();
        Location from = e.getFrom();
        if (to == null || from == null) return;
        if (to.getBlockX() == from.getBlockX() && to.getBlockY() == from.getBlockY() && to.getBlockZ() == from.getBlockZ()) return;

        UUID id = p.getUniqueId();
        long key = ((((long) to.getBlockX()) & 0x3FFFFFFL) << 38) | ((((long) to.getBlockZ()) & 0x3FFFFFFL) << 12) | (((long) to.getBlockY()) & 0xFFFL);
        Long prev = lastBlock.put(id, key);
        if (prev != null && prev == key) return;

        PlayerProfile prof = storage.getProfile(id);
        if (prof == null || prof.party == null) return;
        SummonManager.State st = summons.getState(id);
        if (st == null || st.activePokemonBySlot.isEmpty()) return;
        boolean changed = false;
        for (UUID puid : st.activePokemonBySlot.values()) {
            if (puid == null) continue;
            PokemonInstance mon = prof.findByUuid(puid);
            if (mon == null || mon.isEgg || mon.currentHp <= 0) continue;
            String sid = mon.speciesId == null ? "" : mon.speciesId.toLowerCase(java.util.Locale.ROOT);
            if (!"pawmo".equals(sid) && !"bramblin".equals(sid) && !"rellor".equals(sid)) continue;
            evolutions.onEvolutionSteps(id, mon, 1);
            changed = true;
        }
        if (changed) storage.markDirty(id);
    }
}
