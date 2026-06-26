package win.pokedemo;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/**
 * Custom GUI for Poké Ball loot chests.
 *
 * We do NOT use the vanilla chest inventory to avoid shift-click / number-key / double-click dupes.
 * Loot is stored in TileState PDC and mirrored into a temporary GUI when opened.
 *
 * IMPORTANT: These loot chests are **one-time**.
 * - Opening the chest consumes the block immediately (so it can't be reopened / farmed).
 * - On GUI close, any unclaimed items are dropped to the ground.
 */
public class PokeBallChestListener implements Listener {

    private final PokeDemoPlugin plugin;
    private final NamespacedKeys keys;
    private final Map<UUID, ChestSession> open = new HashMap<>();

    public PokeBallChestListener(PokeDemoPlugin plugin) {
        this.plugin = plugin;
        this.keys = new NamespacedKeys(plugin);
    }

    private boolean isPokeChest(Block b) {
        if (b == null) return false;
        if (b.getType() != Material.TRAPPED_CHEST) return false;
        BlockState st = b.getState();
        if (!(st instanceof TileState tile)) return false;
        PersistentDataContainer pdc = tile.getPersistentDataContainer();
        Byte marker = pdc.get(keys.pokeChestMarkerKey(), PersistentDataType.BYTE);
        return marker != null && marker == (byte) 1;
    }

    private List<ItemStack> readLoot(Block b) {
        BlockState st = b.getState();
        if (!(st instanceof TileState tile)) return List.of();
        byte[] bytes = tile.getPersistentDataContainer().get(keys.pokeChestLootKey(), PersistentDataType.BYTE_ARRAY);
        return PokeChestStorage.deserializeItemStacks(bytes);
    }

    private void writeLoot(Block b, List<ItemStack> loot) {
        BlockState st = b.getState();
        if (!(st instanceof TileState tile)) return;
        PersistentDataContainer pdc = tile.getPersistentDataContainer();
        pdc.set(keys.pokeChestLootKey(), PersistentDataType.BYTE_ARRAY, PokeChestStorage.serializeItemStacks(loot));
        pdc.set(keys.pokeChestMarkerKey(), PersistentDataType.BYTE, (byte) 1);
        tile.update(true, false);
    }

    private void clearChest(Block b) {
        try {
            b.setType(Material.AIR, false);
        } catch (Throwable ignored) {}
    }

    // Run early so item right-click actions don't fire on loot chests (prevents dupe).
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block b = event.getClickedBlock();
        if (!isPokeChest(b)) return;

        event.setCancelled(true);

        // Also deny using the item in hand so other PokeDemo item listeners (e.g., Phone/Recipes GUI)
        // won't open and instantly close this inventory (which would drop loot early).
        try {
            // Paper API: setUseItemInHand / setUseInteractedBlock
            Class<?> res = Class.forName("org.bukkit.event.Event$Result");
            Object deny = java.lang.Enum.valueOf((Class<java.lang.Enum>) res, "DENY");
            event.getClass().getMethod("setUseItemInHand", res).invoke(event, deny);
            event.getClass().getMethod("setUseInteractedBlock", res).invoke(event, deny);
        } catch (Throwable ignored) {}

        Player p = event.getPlayer();

        // Build GUI
        LangManager lang = plugin.getLang();
        Inventory inv = Bukkit.createInventory(null, 27, UtilGui.title(lang.ui("gui.ball_chest.title", "§6精灵球宝箱")));
        List<ItemStack> loot = readLoot(b);

        // Scatter loot across random slots (not neatly packed).
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < inv.getSize(); i++) slots.add(i);
        Collections.shuffle(slots, new Random());

        int si = 0;
        for (ItemStack s : loot) {
            if (s == null || s.getType() == null) continue;
            if (si >= slots.size()) break;
            inv.setItem(slots.get(si++), s.clone());
        }

        Location chestLoc = b.getLocation();
        open.put(p.getUniqueId(), new ChestSession(chestLoc, inv));
        p.openInventory(inv);

        // Consume the chest block immediately to prevent reopening/farming.
        // We do it one tick later to avoid edge-case client desync during the interact event.
        Bukkit.getScheduler().runTask(plugin, () -> {
            Block bb = chestLoc.getBlock();
            if (bb.getType() == Material.TRAPPED_CHEST) {
                clearChest(bb);
            }
        });
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player p)) return;
        ChestSession s = open.get(p.getUniqueId());
        if (s == null) return;
        if (event.getInventory().equals(s.inv)) {
            event.setCancelled(true);

        // Also deny using the item in hand so other PokeDemo item listeners (e.g., Phone/Recipes GUI)
        // won't open and instantly close this inventory (which would drop loot early).
        try {
            // Paper API: setUseItemInHand / setUseInteractedBlock
            Class<?> res = Class.forName("org.bukkit.event.Event$Result");
            Object deny = java.lang.Enum.valueOf((Class<java.lang.Enum>) res, "DENY");
            event.getClass().getMethod("setUseItemInHand", res).invoke(event, deny);
            event.getClass().getMethod("setUseInteractedBlock", res).invoke(event, deny);
        } catch (Throwable ignored) {}

        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player p)) return;
        ChestSession s = open.get(p.getUniqueId());
        if (s == null) return;
        if (!event.getInventory().equals(s.inv)) return;

        // Hard-cancel everything first.
        event.setCancelled(true);

        // Also deny using the item in hand so other PokeDemo item listeners (e.g., Phone/Recipes GUI)
        // won't open and instantly close this inventory (which would drop loot early).
        try {
            // Paper API: setUseItemInHand / setUseInteractedBlock
            Class<?> res = Class.forName("org.bukkit.event.Event$Result");
            Object deny = java.lang.Enum.valueOf((Class<java.lang.Enum>) res, "DENY");
            event.getClass().getMethod("setUseItemInHand", res).invoke(event, deny);
            event.getClass().getMethod("setUseInteractedBlock", res).invoke(event, deny);
        } catch (Throwable ignored) {}


        // Only allow taking from top inventory.
        if (event.getClickedInventory() == null) return;
        if (!event.getClickedInventory().equals(s.inv)) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        boolean takeOne = event.isRightClick();
        int takeAmt = takeOne ? 1 : clicked.getAmount();

        ItemStack toGive = clicked.clone();
        toGive.setAmount(takeAmt);

        // Check if player can receive.
        Map<Integer, ItemStack> leftovers = p.getInventory().addItem(toGive);
        if (!leftovers.isEmpty()) {
            // Back out (inventory full) – do not modify GUI.
            p.sendMessage("§c背包空间不足！");
            return;
        }

        // Deduct from GUI stack.
        int newAmt = clicked.getAmount() - takeAmt;
        if (newAmt <= 0) {
            event.getInventory().setItem(event.getSlot(), null);
        } else {
            clicked.setAmount(newAmt);
            event.getInventory().setItem(event.getSlot(), clicked);
        }

        // No persistence: one-time chest. Remaining items will drop on close.
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player p)) return;
        ChestSession s = open.remove(p.getUniqueId());
        if (s == null) return;

        // Drop any remaining loot to ground on close (one-time chest).
        Location loc = s.chestLoc;
        if (loc == null || loc.getWorld() == null) return;
        Location drop = loc.clone().add(0.5, 0.5, 0.5);
        for (ItemStack it : s.inv.getContents()) {
            if (it == null || it.getType() == Material.AIR) continue;
            drop.getWorld().dropItemNaturally(drop, it.clone());
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        Block b = event.getBlock();
        if (!isPokeChest(b)) return;
        // Drop remaining loot to ground on break (so it isn't lost).
        List<ItemStack> loot = readLoot(b);
        if (!loot.isEmpty()) {
            Location drop = b.getLocation().add(0.5, 0.5, 0.5);
            for (ItemStack it : loot) {
                if (it == null || it.getType() == Material.AIR) continue;
                drop.getWorld().dropItemNaturally(drop, it);
            }
        }
        // Clear the PDC so it doesn't leave ghost data.
        clearChest(b);
    }

    private record ChestSession(Location chestLoc, Inventory inv) {}
}