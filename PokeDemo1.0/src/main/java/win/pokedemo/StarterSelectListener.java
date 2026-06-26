package win.pokedemo;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;

/**
 * Shows the starter selection GUI the first time a player opens their own inventory.
 */
public class StarterSelectListener implements Listener {
    private final Storage storage;

    public StarterSelectListener(Storage storage) {
        this.storage = storage;
    }

    @EventHandler
    public void onOpen(InventoryOpenEvent e) {
        if (!(e.getPlayer() instanceof Player player)) return;
        if (e.getInventory() == null) return;

        // Trigger when opening their own inventory (E key).
        // Depending on server/proxy versions this can show up as CRAFTING or PLAYER.
        InventoryType t = e.getInventory().getType();
        if (t != InventoryType.CRAFTING && t != InventoryType.PLAYER) return;

        // Extra guard: only when the opened inventory belongs to themselves.
        if (e.getInventory().getHolder() != null && e.getInventory().getHolder() != player) return;

        PlayerProfile prof = storage.getProfile(player.getUniqueId());
        if (prof == null || prof.starterChosen) return;

        // Open our GUI on next tick to avoid fighting with the vanilla inventory opening.
        Bukkit.getScheduler().runTask(PokeDemoPlugin.INSTANCE, () -> {
            // Re-check in case they selected in the meantime.
            PlayerProfile prof2 = storage.getProfile(player.getUniqueId());
            if (prof2 != null && !prof2.starterChosen) {
                UtilGui.openStarterSelect(player);
            }
        });
    }
}
