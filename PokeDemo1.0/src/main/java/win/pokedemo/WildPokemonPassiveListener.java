package win.pokedemo;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.persistence.PersistentDataType;

/**
 * Makes wild placeholder wolves behave like passive wild Pokémon.
 * They never aggro vanilla mobs, but they are still allowed to move at night.
 * Sleep visuals are handled client-side when they are idle.
 */
public final class WildPokemonPassiveListener implements Listener {
    private final PokeDemoPlugin plugin;
    private int taskId = -1;

    public WildPokemonPassiveListener(PokeDemoPlugin plugin) {
        this.plugin = plugin;
        start();
    }

    private void start() {
        stop();
        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::enforceAllWildCarriers, 20L, 20L);
    }

    private void stop() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Wolf wolf)) return;
        if (!isWildPokemonCarrier(wolf)) return;
        event.setCancelled(true);
        calm(wolf);
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        for (Entity e : event.getChunk().getEntities()) {
            if (e instanceof Wolf wolf && isWildPokemonCarrier(wolf)) {
                applyWildState(wolf);
            }
        }
    }

    private void enforceAllWildCarriers() {
        for (var world : plugin.getServer().getWorlds()) {
            for (Wolf wolf : world.getEntitiesByClass(Wolf.class)) {
                if (!isWildPokemonCarrier(wolf)) continue;
                applyWildState(wolf);
            }
        }
    }

    private void applyWildState(Wolf wolf) {
        calm(wolf);
        try { wolf.setSilent(true); } catch (Throwable ignored) {}
        applyPassiveState(wolf);
    }

    private void calm(Wolf wolf) {
        try { wolf.setTarget(null); } catch (Throwable ignored) {}
        try { wolf.setAngry(false); } catch (Throwable ignored) {}
    }

    private void applyPassiveState(Wolf wolf) {
        try { wolf.setSitting(false); } catch (Throwable ignored) {}
        try { wolf.setAI(true); } catch (Throwable ignored) {}
        try { wolf.setAware(true); } catch (Throwable ignored) {}
    }

    private boolean isWildPokemonCarrier(Wolf wolf) {
        Byte wild = wolf.getPersistentDataContainer().get(plugin.KEY_WILD, PersistentDataType.BYTE);
        if (wild == null || wild != (byte) 1) return false;
        String species = wolf.getPersistentDataContainer().get(plugin.KEY_SPECIES, PersistentDataType.STRING);
        return species != null && !species.isBlank();
    }
}
