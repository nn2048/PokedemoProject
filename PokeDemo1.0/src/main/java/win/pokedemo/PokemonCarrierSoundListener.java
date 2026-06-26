package win.pokedemo;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Keeps all pokemon carrier wolves permanently silent.
 * We intentionally do not emit replacement cries here anymore.
 */
public final class PokemonCarrierSoundListener implements Listener {
    private final PokeDemoPlugin plugin;

    public PokemonCarrierSoundListener(PokeDemoPlugin plugin) {
        this.plugin = plugin;
        startSilenceTask();
        new BukkitRunnable() {
            @Override public void run() { silenceAllLoadedCarrierWolves(); }
        }.runTask(plugin);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        silenceChunk(event.getChunk());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldLoad(WorldLoadEvent event) {
        for (Chunk chunk : event.getWorld().getLoadedChunks()) silenceChunk(chunk);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.getEntity() instanceof Wolf wolf) silenceCarrier(wolf);
    }

    private void startSilenceTask() {
        new BukkitRunnable() {
            @Override public void run() {
                try {
                    silenceAllLoadedCarrierWolves();
                } catch (Throwable ignored) {
                }
            }
        }.runTaskTimer(plugin, 20L, 10L);
    }

    private void silenceAllLoadedCarrierWolves() {
        for (World world : Bukkit.getWorlds()) {
            for (Wolf wolf : world.getEntitiesByClass(Wolf.class)) {
                silenceCarrier(wolf);
            }
        }
    }

    private void silenceChunk(Chunk chunk) {
        for (Entity entity : chunk.getEntities()) {
            if (entity instanceof Wolf wolf) silenceCarrier(wolf);
        }
    }

    private void silenceCarrier(Wolf wolf) {
        String species = wolf.getPersistentDataContainer().get(plugin.KEY_SPECIES, PersistentDataType.STRING);
        if (species == null || species.isBlank()) return;
        try {
            wolf.setSilent(true);
        } catch (Throwable ignored) {
        }
    }
}
