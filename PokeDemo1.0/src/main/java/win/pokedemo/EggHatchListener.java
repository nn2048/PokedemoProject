package win.pokedemo;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// project
import win.pokedemo.Species;

/**
 * Handles egg step countdown and hatching.
 * Eggs only hatch while in PARTY.
 */
public final class EggHatchListener implements Listener {

    private final PokeDemoPlugin plugin;
    private final Storage storage;
    private final Dex dex;
    private final Map<UUID, long[]> lastBlock = new ConcurrentHashMap<>();

    public EggHatchListener(PokeDemoPlugin plugin, Storage storage, Dex dex) {
        this.plugin = plugin;
        this.storage = storage;
        this.dex = dex;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (p == null) return;
        Location to = e.getTo();
        Location from = e.getFrom();
        if (to == null || from == null) return;
        // Count one step only when player enters a new block coordinate.
        if (to.getBlockX() == from.getBlockX() && to.getBlockY() == from.getBlockY() && to.getBlockZ() == from.getBlockZ()) return;

        UUID id = p.getUniqueId();
        long[] prev = lastBlock.get(id);
        long key = (((long) to.getBlockX()) & 0x3FFFFFF) << 38 | (((long) to.getBlockZ()) & 0x3FFFFFF) << 12 | ((long) to.getBlockY() & 0xFFF);
        if (prev != null && prev.length > 0 && prev[0] == key) return;
        lastBlock.put(id, new long[]{key});

        PlayerProfile prof = storage.getProfile(id);
        if (prof == null || prof.party == null) return;

        boolean changed = false;
        for (PokemonInstance pi : prof.party) {
            if (pi == null || !pi.isEgg) continue;
            if (pi.eggStepsRemaining <= 0) continue;
            pi.eggStepsRemaining = Math.max(0, pi.eggStepsRemaining - 1);
            changed = true;
            if (pi.eggStepsRemaining <= 0) {
                hatch(p, pi);
            }
        }
        if (changed) storage.saveProfile(id);
    }

    private void hatch(Player player, PokemonInstance egg) {
        try {
            egg.isEgg = false;
            egg.uiLocked = false;
            egg.uiLockReason = "";

            if (egg.eggBallId != null && !egg.eggBallId.isBlank()) {
                egg.ballId = egg.eggBallId;
            } else if (egg.ballId == null || egg.ballId.isBlank()) {
                egg.ballId = "poke_ball";
            }

            // Ensure HP/status sane
            Species s = dex.getSpecies(egg.speciesId);
            if (s != null) {
                int mhp = egg.maxHp(s);
                egg.currentHp = mhp;
                egg.status = "none";
            }

            // Announce
            player.sendMessage("§a你的蛋孵化出了 §f" + egg.displayName() + "§a！");
            // Optional: play a sound
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_CHICKEN_EGG, 1f, 1.2f);

        } catch (Throwable ignored) {}
        // If player currently has party GUI open, refresh next tick.
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                Object h = player.getOpenInventory().getTopInventory().getHolder();
                if (h instanceof GuiHolder gh && gh.type == GuiType.PARTY) {
                    UtilGui.openParty(player, storage, -1);
                }
            } catch (Throwable ignored) {}
        });
    }
}
