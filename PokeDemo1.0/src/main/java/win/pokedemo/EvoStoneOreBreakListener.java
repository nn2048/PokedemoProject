package win.pokedemo;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.NoteBlock;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.enchantments.Enchantment;

/**
 * Drops evolution stones from custom NoteBlock "ores".
 */
public class EvoStoneOreBreakListener implements Listener {

    private final java.util.Random random = new java.util.Random();

    private final PokeDemoPlugin plugin;
    private final ItemFactory items;
    private final ItemRegistry registry;

    public EvoStoneOreBreakListener(PokeDemoPlugin plugin, ItemFactory items, ItemRegistry registry) {
        this.plugin = plugin;
        this.items = items;
        this.registry = registry;
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        if (!plugin.getConfig().getBoolean("evo-ores.enabled", true)) return;

        Block b = event.getBlock();
        if (b.getType() != Material.NOTE_BLOCK) return;
        BlockData bd = b.getBlockData();
        if (!(bd instanceof NoteBlock nb)) return;

        EvoOreType t = EvoOreType.fromState(nb.getInstrument(), nb.getNote());
        if (t == null) return;

        // This is our ore.
        event.setDropItems(false);

        Player p = event.getPlayer();
        ItemStack tool = p.getInventory().getItemInMainHand();
        // Silk touch should NOT give the ore (as requested).
        if (tool != null && tool.containsEnchantment(Enchantment.SILK_TOUCH)) {
            // break with no drops
            try { b.setType(Material.AIR, false); } catch (Throwable ignored) {}
            try {
                Location loc = b.getLocation().add(0.5, 0.5, 0.5);
                p.playSound(loc, org.bukkit.Sound.BLOCK_DEEPSLATE_BREAK, 0.7f, 0.9f);
            } catch (Throwable ignored) {}
            return;
        }

        int fortune = 0;
        try { fortune = (tool != null) ? tool.getEnchantmentLevel(Enchantment.FORTUNE) : 0; } catch (Throwable ignored) {}
        int amount = 1;
        if (fortune > 0) {
            // Vanilla-like: 1 + max(0, rand(0..fortune+1)-1)
            int extra = random.nextInt(fortune + 2) - 1;
            if (extra < 0) extra = 0;
            amount += extra;
        }

        ItemDef def = registry.get(t.itemId);
        ItemStack drop = (def != null) ? items.createItem(def, plugin.getLang(), amount) : null;
        if (drop == null) return;

        Location loc = b.getLocation().add(0.5, 0.5, 0.5);
        loc.getWorld().dropItemNaturally(loc, drop);

        // Break naturally (sets AIR).
        try { b.setType(Material.AIR, false); } catch (Throwable ignored) {}

        // Small feedback.
        try {
            p.playSound(loc, org.bukkit.Sound.BLOCK_AMETHYST_BLOCK_BREAK, 0.7f, 1.2f);
        } catch (Throwable ignored) {}
    }
}
