package win.pokedemo;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.EnumSet;
import java.util.Set;

/**
 * Pixelmon-ish apricorn acquisition: break natural leaves -> chance to drop apricorns.
 * 
 * Design goals (per your server rules):
 *  - Each leaf type drops only 1~3 apricorn colors (not fully random).
 *  - Lower overall drop chance.
 *  - Compatible with older MC (use the classic 6 leaf types).
 *  - Anti-farm rules:
 *      - No Silk Touch
 *      - No Shears
 *      - Only natural leaves (persistent=false)
 */
public class ApricornLeafDropListener implements Listener {

    private final PokeDemoPlugin plugin;
    private final ItemFactory items;
    private final ItemRegistry registry;

    private static final Set<Material> SUPPORTED_LEAVES = EnumSet.of(
            Material.OAK_LEAVES,
            Material.BIRCH_LEAVES,
            Material.SPRUCE_LEAVES,
            Material.JUNGLE_LEAVES,
            Material.ACACIA_LEAVES,
            Material.DARK_OAK_LEAVES
    );

    public ApricornLeafDropListener(PokeDemoPlugin plugin, ItemFactory items, ItemRegistry registry) {
        this.plugin = plugin;
        this.items = items;
        this.registry = registry;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        if (!SUPPORTED_LEAVES.contains(b.getType())) return;

        BlockData bd = b.getBlockData();
        if (bd instanceof Leaves leaves) {
            if (leaves.isPersistent()) return; // player-placed leaves
        }

        ItemStack tool = e.getPlayer().getInventory().getItemInMainHand();
        if (tool != null) {
            if (tool.getType() == Material.SHEARS) return;
            if (tool.containsEnchantment(Enchantment.SILK_TOUCH)) return;
        }

        // Base chance per leaf block (server-friendly)
        double chance = plugin.getConfig().getDouble("apricorns.leaf-drop-chance", 0.02); // default 2%
        if (Util.RND.nextDouble() > chance) return;

        String color = rollColorForLeaves(b.getType());
        if (color == null) return;

        ItemStack drop = items.make(color + "_apricorn", registry);
        if (drop == null) return;

        b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), drop);
    }

    /**
     * Each leaf type only drops 1~3 apricorn colors.
     * We keep a small rare third option so it doesn't feel too deterministic.
     */
    private String rollColorForLeaves(Material leaves) {
        return switch (leaves) {
            case OAK_LEAVES -> pickWeighted("red", 6, "yellow", 4, "green", 1);
            case BIRCH_LEAVES -> pickWeighted("yellow", 6, "white", 3, "red", 1);
            case SPRUCE_LEAVES -> pickWeighted("blue", 6, "black", 3, "white", 1);
            case JUNGLE_LEAVES -> pickWeighted("green", 6, "pink", 3, "yellow", 1);
            case ACACIA_LEAVES -> pickWeighted("red", 5, "yellow", 5, "pink", 1);
            case DARK_OAK_LEAVES -> pickWeighted("black", 5, "white", 4, "blue", 1);
            default -> pickWeighted("red", 5, "yellow", 4, "blue", 3);
        };
    }

    private String pickWeighted(String a, int wa, String b, int wb, String c, int wc) {
        int total = Math.max(0, wa) + Math.max(0, wb) + Math.max(0, wc);
        if (total <= 0) return null;
        int r = Util.RND.nextInt(total);
        if (r < wa) return a;
        r -= wa;
        if (r < wb) return b;
        return c;
    }
}
