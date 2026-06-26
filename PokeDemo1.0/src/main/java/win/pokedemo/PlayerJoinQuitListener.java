
package win.pokedemo;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

public class PlayerJoinQuitListener implements Listener {
    private final PokeDemoPlugin plugin;
    private final Storage storage;

    public PlayerJoinQuitListener(PokeDemoPlugin plugin, Storage storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        storage.getProfile(e.getPlayer().getUniqueId());

        // Migrate legacy machine items (correct PDC id, outdated CMD) so they render correctly.
        try {
            ItemFactory items = plugin.getItems();
            ItemRegistry reg = plugin.getItemRegistry();
            if (items != null && reg != null) {
                for (ItemStack it : e.getPlayer().getInventory().getContents()) {
                    String id = items.getItemId(it);
                    if (id == null) continue;
                    // Include pasture_machine as well: otherwise older pasture items may render as TM/HM in inventory.
                    if (!"pc_machine".equals(id) && !"healer_machine".equals(id) && !"pasture_machine".equals(id)) continue;
                    ItemDef def = reg.get(id);
                    if (def != null) items.ensureModelData(it, def.customModelData);
                }
            }
        } catch (Throwable ignored) {
        }

        try {
            if (plugin.getPartySidebarManager() != null) {
                plugin.getPartySidebarManager().refreshPlayer(e.getPlayer());
            }
        } catch (Throwable ignored) {
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        storage.saveProfile(e.getPlayer().getUniqueId());
        try {
            if (plugin.getPartySidebarManager() != null) {
                plugin.getPartySidebarManager().clearSidebar(e.getPlayer());
            }
        } catch (Throwable ignored) {
        }
    }
}
