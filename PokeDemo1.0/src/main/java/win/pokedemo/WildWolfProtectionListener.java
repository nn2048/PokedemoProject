package win.pokedemo;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.persistence.PersistentDataType;

/**
 * Prevent placeholder wolves used as wild Pokémon from dying in water/lava etc.
 * This is important because some species are meant to spawn in water/lava later,
 * but we use Wolves as placeholders in the demo.
 */
public class WildWolfProtectionListener implements Listener {
    private final PokeDemoPlugin plugin;

    public WildWolfProtectionListener(PokeDemoPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        Entity e = event.getEntity();
        if (!(e instanceof Wolf)) return;

        // Protect ANY placeholder wolf that represents a Pokémon (wild or owned/summoned carriers).
        String sid = e.getPersistentDataContainer().get(plugin.KEY_SPECIES, PersistentDataType.STRING);
        if (sid == null || sid.isBlank()) return;

        EntityDamageEvent.DamageCause c = event.getCause();
        switch (c) {
            case DROWNING, FIRE, FIRE_TICK, LAVA, HOT_FLOOR, SUFFOCATION, FREEZE -> event.setCancelled(true);
            default -> {}
        }
    }
}
