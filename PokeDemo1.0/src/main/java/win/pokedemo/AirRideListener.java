package win.pokedemo;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Minimal flying ride controller for summoned final-form flying Pokémon carriers.
 * View direction controls movement. Sneak dismounts via vanilla passenger logic.
 */
public final class AirRideListener implements Listener {
    private static final Set<String> AIR_RIDEABLE = Set.of(
            "charizard", "dragonite", "aerodactyl", "articuno", "zapdos", "moltres", "mewtwo"
    );

    private final PokeDemoPlugin plugin;
    private int taskId = -1;

    public AirRideListener(PokeDemoPlugin plugin) {
        this.plugin = plugin;
        start();
    }

    private void start() {
        stop();
        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::tickRiders, 1L, 1L);
    }

    private void stop() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onRightClickEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        Entity clicked = event.getRightClicked();
        if (!(clicked instanceof Wolf wolf)) return;
        if (!player.getInventory().getItemInMainHand().getType().isAir()) return;
        if (player.isInsideVehicle()) return;
        if (!isRideablePokemonCarrier(wolf, player.getUniqueId())) return;
        if (plugin.battles() != null && plugin.battles().isInBattle(player.getUniqueId())) return;
        event.setCancelled(true);
        try { wolf.addPassenger(player); } catch (Throwable ignored) { return; }
        try { wolf.setGravity(false); } catch (Throwable ignored) {}
        try { wolf.setAI(false); } catch (Throwable ignored) {}
        try { wolf.setAware(false); } catch (Throwable ignored) {}
        player.sendMessage("§b已骑乘飞行宝可梦。§7视角控制飞行，潜行下坐骑。");
    }

    private void tickRiders() {
        for (var world : plugin.getServer().getWorlds()) {
            for (Wolf wolf : world.getEntitiesByClass(Wolf.class)) {
                if (!isCarrier(wolf)) continue;
                Player rider = firstPlayerPassenger(wolf);
                if (rider == null) {
                    releaseIfNeeded(wolf);
                    continue;
                }
                driveMountedWolf(wolf, rider);
            }
        }
    }

    private void driveMountedWolf(Wolf wolf, Player rider) {
        if (rider.isSneaking()) {
            try { wolf.removePassenger(rider); } catch (Throwable ignored) {}
            releaseIfNeeded(wolf);
            return;
        }
        Vector dir = rider.getEyeLocation().getDirection();
        if (dir.lengthSquared() < 1.0e-6) dir = rider.getLocation().getDirection();
        dir = dir.normalize();
        double speed = 0.9;
        Vector step = dir.multiply(speed);
        try { wolf.setGravity(false); } catch (Throwable ignored) {}
        try { wolf.setAI(false); } catch (Throwable ignored) {}
        try { wolf.setAware(false); } catch (Throwable ignored) {}
        try { wolf.setSitting(false); } catch (Throwable ignored) {}
        try { wolf.setFallDistance(0f); } catch (Throwable ignored) {}
        try {
            Location next = wolf.getLocation().clone().add(step);
            next.setYaw(rider.getLocation().getYaw());
            next.setPitch(rider.getLocation().getPitch());
            wolf.teleport(next);
        } catch (Throwable ignored) {}
        try { wolf.setVelocity(step); } catch (Throwable ignored) {}
    }

    private void releaseIfNeeded(Wolf wolf) {
        try { wolf.setGravity(true); } catch (Throwable ignored) {}
        try { wolf.setAI(true); } catch (Throwable ignored) {}
        try { wolf.setAware(true); } catch (Throwable ignored) {}
    }

    private boolean isRideablePokemonCarrier(Wolf wolf, UUID playerId) {
        if (!isCarrier(wolf)) return false;
        String owner = wolf.getPersistentDataContainer().get(plugin.KEY_OWNER, PersistentDataType.STRING);
        if (owner == null || !owner.equals(playerId.toString())) return false;
        String species = wolf.getPersistentDataContainer().get(plugin.KEY_SPECIES, PersistentDataType.STRING);
        if (species == null) return false;
        return AIR_RIDEABLE.contains(species.toLowerCase(Locale.ROOT));
    }

    private boolean isCarrier(Wolf wolf) {
        return wolf != null
                && wolf.getPersistentDataContainer().has(plugin.KEY_OWNER, PersistentDataType.STRING)
                && wolf.getPersistentDataContainer().has(plugin.KEY_SPECIES, PersistentDataType.STRING);
    }

    private Player firstPlayerPassenger(Wolf wolf) {
        for (Entity passenger : wolf.getPassengers()) {
            if (passenger instanceof Player p) return p;
        }
        return null;
    }
}
