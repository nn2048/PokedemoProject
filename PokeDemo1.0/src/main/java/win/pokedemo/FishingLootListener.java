package win.pokedemo;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * Fishing loot: extremely low chance to fish special PokeDemo items.
 * Config: fishing-loot.enabled, fishing-loot.table.<itemId>.enabled/chance
 */
public class FishingLootListener implements Listener {

    private final PokeDemoPlugin plugin;
    private final ItemRegistry registry;
    private final ItemFactory items;

    public FishingLootListener(PokeDemoPlugin plugin, ItemRegistry registry, ItemFactory items) {
        this.plugin = plugin;
        this.registry = registry;
        this.items = items;
    }

    private record Entry(String itemId, boolean enabled, double chance) {}

    private List<Entry> readTable() {
        try {
            if (!plugin.getConfig().getBoolean("fishing-loot.enabled", true)) return List.of();
            ConfigurationSection sec = plugin.getConfig().getConfigurationSection("fishing-loot.table");
            if (sec == null) return List.of();
            List<Entry> out = new ArrayList<>();
            for (String id : sec.getKeys(false)) {
                ConfigurationSection s = sec.getConfigurationSection(id);
                if (s == null) continue;
                boolean en = s.getBoolean("enabled", true);
                double ch = s.getDouble("chance", 0.0);
                if (ch <= 0) continue;
                out.add(new Entry(id, en, ch));
            }
            // deterministic order for fairness
            out.sort(Comparator.comparing(e -> e.itemId));
            return out;
        } catch (Exception e) {
            return List.of();
        }
    }


    private boolean isPokemonRodId(String id) {
        if (id == null) return false;
        return java.util.Set.of("poke_rod","great_rod","ultra_rod","master_rod","love_rod").contains(id.toLowerCase(java.util.Locale.ROOT));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFish(PlayerFishEvent e) {
        if (e.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        if (!(e.getPlayer() instanceof Player player)) return;
        try {
            if (plugin.getConfig().getBoolean("fishing-pokemon.enabled", true)) {
                ItemDef held = items.identify(player.getInventory().getItemInMainHand(), registry);
                if (held != null && isPokemonRodId(held.id)) return;
            }
        } catch (Throwable ignored) {}

        // Only when an item entity is actually caught.
        if (!(e.getCaught() instanceof Item caught)) return;

        List<Entry> table = readTable();
        if (table.isEmpty()) return;

        Random rnd = Util.RND;
        for (Entry en : table) {
            if (!en.enabled) continue;
            if (rnd.nextDouble() >= en.chance) continue;

            ItemDef def = registry.get(en.itemId);
            if (def == null) continue;

            ItemStack give = items.createItem(def, plugin.getLang(), 1);
            if (give == null) continue;

            // Replace the caught item with our custom item.
            caught.setItemStack(give);
            player.sendMessage("§b钓鱼获得稀有道具：§f" + give.getItemMeta().getDisplayName());
            return;
        }
        // otherwise keep vanilla fish
    }
}
