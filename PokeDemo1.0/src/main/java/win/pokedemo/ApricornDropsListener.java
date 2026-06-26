package win.pokedemo;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Minimal apricorn acquisition implementation (phase 1):
 *  - Breaking leaf blocks has a small chance to drop apricorns.
 *
 * This is intentionally simple and server-friendly.
 * Later we can upgrade to "right-click harvestable apricorn trees" like Cobblemon.
 */
public class ApricornDropsListener implements Listener {
    private final PokeDemoPlugin plugin;

    public ApricornDropsListener(PokeDemoPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onLeafBreak(BlockBreakEvent e) {
        if (e.isCancelled()) return;
        Player p = e.getPlayer();
        Block b = e.getBlock();
        if (b == null) return;

        Material t = b.getType();
        if (!t.name().endsWith("_LEAVES")) return;
        // Configurable drop chance, default 2.5%
        double chance = plugin.getConfig().getDouble("apricorns.leaf-drop-chance", 0.025);
        if (chance <= 0) return;
        if (Util.RND.nextDouble() > chance) return;

        // Random apricorn color
        String[] colors = new String[]{"red", "blue", "yellow", "green", "pink", "black", "white"};
        String id = colors[Util.RND.nextInt(colors.length)] + "_apricorn";
        ItemDef def = plugin.getItemRegistry().get(id);
        if (def == null) return;
        ItemStack drop = plugin.getItems().createItem(def, plugin.getLang(), 1);
        b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), drop);
        if (plugin.getConfig().getBoolean("apricorns.notify-on-drop", false)) {
            p.sendMessage("§a你获得了球果：§f" + plugin.getLang().item(def.id, def.id));
        }
    }
}
