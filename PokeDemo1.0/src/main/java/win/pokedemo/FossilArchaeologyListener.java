package win.pokedemo;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * Adds fossils into vanilla archaeology loot tables (suspicious sand / gravel).
 * Uses LootGenerateEvent so it triggers reliably when brushing finishes.
 */
public class FossilArchaeologyListener implements Listener {

    private final PokeDemoPlugin plugin;
    private final ItemFactory items;
    private final ItemRegistry registry;

    public FossilArchaeologyListener(PokeDemoPlugin plugin, ItemFactory items, ItemRegistry registry) {
        this.plugin = plugin;
        this.items = items;
        this.registry = registry;
    }

    @EventHandler(ignoreCancelled = true)
    public void onLootGenerate(LootGenerateEvent e) {
        try {
            if (e.getLootTable() == null) return;
            String key = e.getLootTable().getKey().toString();
            // Only archaeology tables.
            if (!key.startsWith("minecraft:archaeology/")) return;

            if (!plugin.getConfig().getBoolean("fossils.archaeology.enabled", true)) return;

            double chance = plugin.getConfig().getDouble("fossils.archaeology.extra-roll-chance", 0.35);
            if (chance <= 0) return;
            if (Util.RND.nextDouble() > chance) return;

            String fossilId = rollFossilForTable(key);
            if (fossilId == null) return;
            ItemDef def = registry.get(fossilId);
            if (def == null) return;

            ItemStack drop = items.createItem(def, plugin.getLang(), 1);
            if (drop == null) return;
            e.getLoot().add(drop);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Follow vanilla regions (loot tables):
     * - desert_well / desert_pyramid: old_amber (primary)
     * - ocean_ruin_warm: helix
     * - ocean_ruin_cold: dome
     * - trail_ruins_common/rare: mixed
     */
    private String rollFossilForTable(String lootTableKey) {
        // Allow config override: fossils.archaeology.tables.<lootTableKey> -> list of {id, weight}
        try {
            var sec = plugin.getConfig().getConfigurationSection("fossils.archaeology.tables");
            if (sec != null && sec.isList(lootTableKey)) {
                List<Map<?, ?>> list = (List<Map<?, ?>>) sec.getList(lootTableKey);
                return rollWeighted(list);
            }
        } catch (Throwable ignored) {}

        String k = lootTableKey.toLowerCase(Locale.ROOT);
        if (k.endsWith("/desert_well") || k.endsWith("/desert_pyramid")) {
            return "old_amber";
        }
        if (k.endsWith("/ocean_ruin_warm")) {
            return "helix_fossil";
        }
        if (k.endsWith("/ocean_ruin_cold")) {
            return "dome_fossil";
        }
        if (k.endsWith("/trail_ruins_rare")) {
            return rollDefaultMixed(true);
        }
        if (k.endsWith("/trail_ruins_common")) {
            return rollDefaultMixed(false);
        }
        // other archaeology tables (future): return mixed
        return rollDefaultMixed(false);
    }

    private String rollDefaultMixed(boolean rare) {
        // common: helix 45, dome 45, amber 10
        // rare:   helix 30, dome 30, amber 40
        int r = Util.RND.nextInt(100);
        if (!rare) {
            if (r < 45) return "helix_fossil";
            if (r < 90) return "dome_fossil";
            return "old_amber";
        } else {
            if (r < 30) return "helix_fossil";
            if (r < 60) return "dome_fossil";
            return "old_amber";
        }
    }

    private String rollWeighted(List<Map<?, ?>> list) {
        if (list == null || list.isEmpty()) return null;
        int total = 0;
        List<String> ids = new ArrayList<>();
        List<Integer> w = new ArrayList<>();
        for (Map<?, ?> m : list) {
            if (m == null) continue;
            Object idObj = m.get("id");
            if (idObj == null) continue;
            String id = String.valueOf(idObj);
            int weight = 1;
            try {
                Object ww = m.get("weight");
                if (ww instanceof Number n) weight = n.intValue();
                else if (ww != null) weight = Integer.parseInt(String.valueOf(ww));
            } catch (Throwable ignored) {}
            weight = Math.max(1, weight);
            ids.add(id);
            w.add(weight);
            total += weight;
        }
        if (total <= 0 || ids.isEmpty()) return null;
        int r = Util.RND.nextInt(total);
        for (int i = 0; i < ids.size(); i++) {
            r -= w.get(i);
            if (r < 0) return ids.get(i);
        }
        return ids.get(0);
    }
}
