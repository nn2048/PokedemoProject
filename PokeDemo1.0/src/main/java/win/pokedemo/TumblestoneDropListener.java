package win.pokedemo;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Tumblestone acquisition.
 *
 * User design (Cobblemon-like):
 * - Mine STONE / COBBLESTONE / DEEPSLATE -> chance to drop normal tumblestone.
 * - Mine COAL_ORE / DEEPSLATE_COAL_ORE -> 1/10 chance to drop black tumblestone.
 * - Mine LAPIS_ORE / DEEPSLATE_LAPIS_ORE -> 1/3 chance to drop sky tumblestone.
 */
public class TumblestoneDropListener implements Listener {

    private final PokeDemoPlugin plugin;
    private final ItemFactory items;
    private final ItemRegistry registry;

    public TumblestoneDropListener(PokeDemoPlugin plugin, ItemFactory items, ItemRegistry registry) {
        this.plugin = plugin;
        this.items = items;
        this.registry = registry;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        Material t = b.getType();
        boolean isStone = (t == Material.STONE || t == Material.COBBLESTONE || t == Material.DEEPSLATE);
        boolean isCoal = (t == Material.COAL_ORE || t == Material.DEEPSLATE_COAL_ORE);
        boolean isLapis = (t == Material.LAPIS_ORE || t == Material.DEEPSLATE_LAPIS_ORE);
        if (!isStone && !isCoal && !isLapis) return;

        ItemStack tool = e.getPlayer().getInventory().getItemInMainHand();
        if (tool != null && tool.containsEnchantment(Enchantment.SILK_TOUCH)) return;

        String dropId;
        double chance;
        if (isStone) {
            dropId = "tumblestone";
            // Default: 1/30 (was 1/20). User can override in config.
            chance = plugin.getConfig().getDouble("tumblestones.from-stone-chance", 1.0 / 30.0);
        } else if (isCoal) {
            dropId = "black_tumblestone";
            chance = plugin.getConfig().getDouble("tumblestones.black-from-coal-chance", 0.10);
        } else {
            dropId = "sky_tumblestone";
            chance = plugin.getConfig().getDouble("tumblestones.sky-from-lapis-chance", 0.333333);
        }
        if (Util.RND.nextDouble() > chance) return;

        ItemStack drop = items.make(dropId, registry);
        if (drop == null) return;
        b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), drop);
    }
}
