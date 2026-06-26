package win.pokedemo;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public class GuideBookListener implements Listener {
    private final PokeDemoPlugin plugin;
    private final java.util.Map<java.util.UUID, Long> swingCooldown = new java.util.concurrent.ConcurrentHashMap<>();

    public GuideBookListener(PokeDemoPlugin plugin) {
        this.plugin = plugin;
    }

    // Do NOT ignore cancelled: some servers/plugins cancel RIGHT_CLICK events, but we still
    // want the guide book to open reliably.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent e) {
        if (plugin.getGuide() == null || !plugin.getGuide().isEnabled()) return;

        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK && a != Action.LEFT_CLICK_AIR && a != Action.LEFT_CLICK_BLOCK) return;

        Player p = e.getPlayer();
        ItemStack it = e.getItem();
        if (!plugin.getGuide().isGuideBook(it)) return;

        // Prevent odd side-effects (e.g. placing blocks, punching)
        e.setCancelled(true);

        if (a == Action.RIGHT_CLICK_AIR || a == Action.RIGHT_CLICK_BLOCK) {
            plugin.getGuide().openBook(p, it);
            return;
        }

        // Left click
        String current = plugin.getGuide().getBookId(it);
        if (p.isSneaking()) {
            String next = plugin.getGuide().nextBookId(current);
            ItemStack nb = plugin.getGuide().createGuideBook(next);
            p.getInventory().setItemInMainHand(nb);
            // small feedback
            p.sendMessage("§a已切换教程：§e" + nb.getItemMeta().getDisplayName());
            return;
        }

        Inventory gui = plugin.getGuide().createSwitchGui(p.getUniqueId(), current);
        p.openInventory(gui);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onClick(InventoryClickEvent e) {
        if (plugin.getGuide() == null || !plugin.getGuide().isEnabled()) return;
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getView() == null) return;
        var holder = e.getView().getTopInventory().getHolder();
        if (!(holder instanceof GuiHolder gh) || gh.type != GuiType.GUIDE_SWITCH) return;

        e.setCancelled(true);
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;
        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return;
        String id = meta.getPersistentDataContainer().get(plugin.getGuide().keyGuideBookId(), PersistentDataType.STRING);
        if (id == null || id.isBlank()) return;

        ItemStack newBook = plugin.getGuide().createGuideBook(id);
        p.getInventory().setItemInMainHand(newBook);
        Bukkit.getScheduler().runTask(plugin, () -> {
            p.closeInventory();
            plugin.getGuide().openBook(p, newBook);
        });
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        // no-op (placeholder for future)
    }

    /**
     * Paper/Purpur does not always fire PlayerInteractEvent for LEFT_CLICK_AIR in every client context.
     * Listen to arm swing to reliably support "left-click air" switching for the guide book.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onSwing(org.bukkit.event.player.PlayerAnimationEvent e) {
        if (plugin.getGuide() == null || !plugin.getGuide().isEnabled()) return;
        Player p = e.getPlayer();

        ItemStack it = p.getInventory().getItemInMainHand();
        if (!plugin.getGuide().isGuideBook(it)) return;

        // If the player is aiming at a block in reach, treat it as normal block interaction.
        org.bukkit.block.Block target = p.getTargetBlockExact(5);
        if (target != null && target.getType() != Material.AIR) return;

        // Simple anti-spam cooldown (300ms)
        long now = System.currentTimeMillis();
        Long last = swingCooldown.get(p.getUniqueId());
        if (last != null && now - last < 300) return;
        swingCooldown.put(p.getUniqueId(), now);

        String current = plugin.getGuide().getBookId(it);
        if (p.isSneaking()) {
            String next = plugin.getGuide().nextBookId(current);
            ItemStack nb = plugin.getGuide().createGuideBook(next);
            p.getInventory().setItemInMainHand(nb);
            p.sendMessage("§a已切换教程：§7" + nb.getItemMeta().getDisplayName());
        } else {
            Inventory gui = plugin.getGuide().createSwitchGui(p.getUniqueId(), current);
            p.openInventory(gui);
        }
    }
}
