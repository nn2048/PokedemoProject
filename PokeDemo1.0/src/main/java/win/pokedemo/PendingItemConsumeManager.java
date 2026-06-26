package win.pokedemo;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks "consume after success" item usage.
 *
 * Currently used for Gen1 TMs: do NOT consume on right-click, only consume when the move is actually learned.
 */
public class PendingItemConsumeManager {
    private final PokeDemoPlugin plugin;
    private final ItemFactory items;

    private static class Pending {
        final String itemId;
        final String expectedMoveId;
        final boolean consumable;
        Pending(String itemId, String expectedMoveId, boolean consumable) {
            this.itemId = itemId;
            this.expectedMoveId = expectedMoveId;
            this.consumable = consumable;
        }
    }

    /** playerId -> pending */
    private final Map<UUID, Pending> pendings = new ConcurrentHashMap<>();

    public PendingItemConsumeManager(PokeDemoPlugin plugin, ItemFactory items) {
        this.plugin = plugin;
        this.items = items;
    }

    public void setPending(UUID playerId, String itemId, String expectedMoveId, boolean consumable) {
        if (playerId == null || itemId == null || expectedMoveId == null) return;
        pendings.put(playerId, new Pending(itemId.toLowerCase(Locale.ROOT), expectedMoveId.toLowerCase(Locale.ROOT), consumable));
    }

    public void clear(UUID playerId) {
        if (playerId == null) return;
        pendings.remove(playerId);
    }

    public boolean hasPending(UUID playerId) {
        if (playerId == null) return false;
        return pendings.containsKey(playerId);
    }

    /**
     * Called when a move learn decision resolves.
     * @param learned true if the move was learned (forget+learn or direct learn), false if cancelled.
     */
    public void onMoveLearnResolved(UUID playerId, String moveId, boolean learned) {
        if (playerId == null || moveId == null) return;
        Pending p = pendings.get(playerId);
        if (p == null) return;
        String mv = moveId.toLowerCase(Locale.ROOT);
        if (!mv.equals(p.expectedMoveId)) {
            // Not the move we are tracking; ignore.
            return;
        }

        if (learned && p.consumable) {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player != null && player.isOnline()) {
                boolean ok = consumeOne(player, p.itemId);
                if (!ok) {
                    player.sendMessage("§c未能消耗学习机道具（可能被移出背包）：" + p.itemId);
                }
            }
        }
        pendings.remove(playerId);
    }

    /** Consume one item anywhere in inventory that matches item_id. */
    private boolean consumeOne(Player player, String itemId) {
        try {
            for (int i = 0; i < player.getInventory().getSize(); i++) {
                ItemStack it = player.getInventory().getItem(i);
                if (it == null || it.getAmount() <= 0) continue;
                String id = items.getItemId(it);
                if (id == null) continue;
                if (!id.equalsIgnoreCase(itemId)) continue;
                it.setAmount(it.getAmount() - 1);
                return true;
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("[PokeDemo] consumeOne failed: " + t.getMessage());
        }
        return false;
    }
}
