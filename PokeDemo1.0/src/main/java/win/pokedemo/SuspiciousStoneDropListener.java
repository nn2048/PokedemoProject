package win.pokedemo;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Gravel extra drops.
 *
 * - Extra FLINT drop chance: 10% (always additional, never replaces vanilla)
 * - Extra Suspicious Stone drop chance: 1/30 (always additional)
 *
 * We use BlockDropItemEvent (NOT BlockBreakEvent) to avoid interfering with vanilla gravel/flint drops.
 */
public class SuspiciousStoneDropListener implements Listener {

    private final PokeDemoPlugin plugin;

    public SuspiciousStoneDropListener(PokeDemoPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(BlockDropItemEvent e) {
        if (e.getBlockState() == null || e.getBlockState().getType() != Material.GRAVEL) return;

        // 10% extra flint drop (does not affect vanilla drop logic)
        if (Util.RND.nextInt(10) == 0) {
            ItemStack flint = new ItemStack(Material.FLINT, 1);
            e.getItems().add(e.getBlock().getWorld().dropItemNaturally(e.getBlock().getLocation().add(0.5, 0.6, 0.5), flint));
        }

        // 1/30 extra suspicious stone
        if (Util.RND.nextInt(30) != 0) return;

        ItemDef def = plugin.getItemRegistry().get("suspicious_stone");
        if (def == null) return;
        ItemStack drop = plugin.getItems().createItem(def, plugin.getLang(), 1);
        if (drop == null) return;

        // Add as an extra drop without removing vanilla drops.
        e.getItems().add(e.getBlock().getWorld().dropItemNaturally(e.getBlock().getLocation().add(0.5, 0.6, 0.5), drop));
    }
}
