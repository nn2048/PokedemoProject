package win.pokedemo;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.event.Listener;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.UUID;

/** Keeps battle carriers and any active summoned carriers for battling players visually frozen. */
public final class BattleCarrierFreezeListener implements Listener {
    private final PokeDemoPlugin plugin;
    private int taskId = -1;

    public BattleCarrierFreezeListener(PokeDemoPlugin plugin) {
        this.plugin = plugin;
        start();
    }

    private void start() {
        stop();
        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::tick, 1L, 2L);
    }

    private void stop() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
    }

    private void tick() {
        if (plugin.battles() == null) return;
        for (var world : plugin.getServer().getWorlds()) {
            for (Wolf wolf : world.getEntitiesByClass(Wolf.class)) {
                if (!wolf.isValid() || wolf.isDead()) continue;
                if (!isBattleRelated(wolf)) continue;
                freeze(wolf);
            }
        }
    }

    private boolean isBattleRelated(Wolf wolf) {
        var pdc = wolf.getPersistentDataContainer();
        Byte battleCarrier = pdc.get(new org.bukkit.NamespacedKey(plugin, "battle_carrier"), PersistentDataType.BYTE);
        if (battleCarrier != null && battleCarrier == (byte)1) return true;
        UUID owner = plugin.getSummonManager() == null ? null : plugin.getSummonManager().getOwnerUuidFromEntity(wolf);
        return owner != null && plugin.battles().isInBattle(owner);
    }

    private void freeze(Wolf wolf) {
        try { wolf.setTarget(null); } catch (Throwable ignored) {}
        try { wolf.setAngry(false); } catch (Throwable ignored) {}
        try { wolf.setAI(false); } catch (Throwable ignored) {}
        try { wolf.setAware(false); } catch (Throwable ignored) {}
        try { wolf.setSitting(false); } catch (Throwable ignored) {}
        try { wolf.setGravity(false); } catch (Throwable ignored) {}
        try { wolf.setVelocity(new Vector(0,0,0)); } catch (Throwable ignored) {}
        try { for (Entity passenger : wolf.getPassengers()) wolf.removePassenger(passenger); } catch (Throwable ignored) {}
    }
}
