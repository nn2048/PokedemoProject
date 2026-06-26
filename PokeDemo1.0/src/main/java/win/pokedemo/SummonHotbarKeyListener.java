package win.pokedemo;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemHeldEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side "hotbar number key" summon:
 * When sneaking, switching to hotbar slot 1-6 will summon/recall that party slot.
 *
 * NOTE: We cannot truly distinguish "pressed number key" vs "scrolled" on the server.
 * This triggers on ANY held-item change while sneaking.
 */
public class SummonHotbarKeyListener implements Listener {
    private final SummonManager summonManager;
    private final PokeDemoPlugin plugin;

    private final Map<UUID, Long> cooldownMs = new HashMap<>();

    public SummonHotbarKeyListener(PokeDemoPlugin plugin, SummonManager summonManager) {
        this.plugin = plugin;
        this.summonManager = summonManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHeldChange(PlayerItemHeldEvent event) {
        // Hotbar change is the 2nd part of the "Shift + F + hotbar" combo.
        if (!plugin.getConfig().getBoolean("summon.hotbar-keys.enabled", true)) return;

        Player player = event.getPlayer();
        if (plugin.getConfig().getBoolean("summon.hotbar-keys.require-sneak", true) && !player.isSneaking()) return;

        int slot = event.getNewSlot(); // 0-8
        if (slot < 0 || slot > 5) return; // only 1-6

        // Require the prior "swap hand" arming within window.
        long windowMs = (long) plugin.getConfig().getInt("summon.combo.window-ms", 800);
        if (!plugin.consumeSummonComboIfArmed(player.getUniqueId(), windowMs)) {
            return;
        }

        // simple cooldown to avoid accidental rapid scroll spam
        long now = System.currentTimeMillis();
        long cd = (long) plugin.getConfig().getInt("summon.hotbar-keys.cooldown-ms", 250);
        Long last = cooldownMs.get(player.getUniqueId());
        if (last != null && now - last < cd) return;
        cooldownMs.put(player.getUniqueId(), now);

        summonManager.toggleSendOutAt(player, slot);
    }
}
