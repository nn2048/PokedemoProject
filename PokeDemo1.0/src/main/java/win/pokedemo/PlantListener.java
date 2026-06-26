package win.pokedemo;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class PlantListener implements Listener {
    private final PlantManager plants;
    private final ItemRegistry itemRegistry;
    private final ItemFactory items;

    public PlantListener(PlantManager plants, ItemRegistry itemRegistry, ItemFactory items) {
        this.plants = plants;
        this.itemRegistry = itemRegistry;
        this.items = items;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block clicked = e.getClickedBlock();
        if (clicked == null) return;

        Player p = e.getPlayer();
        ItemStack held = e.getItem();

        // Harvest first
        if (plants.isPlantBlock(clicked)) {
            e.setCancelled(true);
            plants.harvest(p, clicked);
            return;
        }

        if (held == null) return;
        ItemDef def = items.identify(held, itemRegistry);
        if (def == null) return;

        boolean isBerry = def.id.endsWith("_berry");
        if (!isBerry) return;

        // Planting soil rules (Pixelmon-ish): dirt/grass/farmland etc.
        Material soil = clicked.getType();
        if (!(soil.name().endsWith("_DIRT") || soil == Material.GRASS_BLOCK || soil == Material.FARMLAND || soil == Material.COARSE_DIRT || soil == Material.PODZOL)) {
            return;
        }

        e.setCancelled(true);
        boolean ok = plants.plant(p, clicked, def);
        if (!ok) return;

        // consume 1
        if (held.getAmount() <= 1) {
            p.getInventory().setItemInMainHand(null);
        } else {
            held.setAmount(held.getAmount() - 1);
            p.getInventory().setItemInMainHand(held);
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        if (plants.isPlantBlock(b)) {
            // prevent vanilla note block drops
            e.setDropItems(false);
            e.setExpToDrop(0);

            // custom drops + remove record
            plants.dropOnBreak(b);
            plants.remove(b);

            // remove block (do NOT drop note block)
            b.setType(Material.AIR, false);
        }
    }

    // cactus-like rule: placing blocks next to a plant should pop it
    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
        Block placed = e.getBlockPlaced();
        // check 4 neighbors around the placed block
        plants.popIfBlocked(placed.getRelative(1, 0, 0));
        plants.popIfBlocked(placed.getRelative(-1, 0, 0));
        plants.popIfBlocked(placed.getRelative(0, 0, 1));
        plants.popIfBlocked(placed.getRelative(0, 0, -1));
    }

    // also handle physics updates (pistons/liquids/etc) so plants can't be boxed in later
    @EventHandler
    public void onPhysics(BlockPhysicsEvent e) {
        Block b = e.getBlock();
        if (plants.isPlantBlock(b)) {
            plants.popIfBlocked(b);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onGrow(BlockGrowEvent e) {
        Block b = e.getBlock();
        if (b.getType() == Material.SWEET_BERRY_BUSH && plants.isPlantBlock(b)) {
            // keep growth controlled by PlantManager's scheduled tick
            e.setCancelled(true);
        }
    }
}
