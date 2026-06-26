package win.pokedemo;

import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * Loot tables for overworld Poké Ball chests.
 *
 * Goal: "not too much" but feels rewarding.
 * - Common: berries, apricorns, tumblestones
 * - Uncommon: medicines, status cures, vitamins, battle items
 * - Rare: held items (excluding evolution stones; those come from mining)
 */
public final class PokeBallLootTables {
    private PokeBallLootTables() {}

    /**
     * Bias toward 2~3 items, small chance of 1 or max.
     * For max=4 -> weights 1:1,2:4,3:4,4:1 (10%/40%/40%/10%).
     * For max=3 -> weights 1:1,2:4,3:1 (≈16.7%/66.7%/16.7%).
     * For max=2 -> weights 1:1,2:4 (20%/80%).
     */
    private static int biasedRollCount(Random r, int max) {
        max = Math.max(1, Math.min(4, max));
        if (max == 1) return 1;
        int[] w = new int[max + 1];
        for (int i = 1; i <= max; i++) {
            if (i == 1 || i == max) w[i] = 1;
            else w[i] = 4;
        }
        int total = 0;
        for (int i = 1; i <= max; i++) total += w[i];
        int pick = r.nextInt(total);
        int acc = 0;
        for (int i = 1; i <= max; i++) {
            acc += w[i];
            if (pick < acc) return i;
        }
        return max;
    }

    public static List<ItemStack> generateLoot(PokeDemoPlugin plugin, ItemFactory items, ItemRegistry registry) {
        Random r = new Random();

        // Hard-ban list (configurable) to prevent new items from automatically entering chest loot.
        Set<String> banned = new HashSet<>();
        try {
            List<String> cfg = plugin.getConfig().getStringList("loot-chests.banned-ids");
            if (cfg != null) for (String s : cfg) if (s != null && !s.isBlank()) banned.add(s.toLowerCase(Locale.ROOT));
        } catch (Throwable ignored) {}
        // Default bans (safety)
        banned.addAll(List.of(
                "pc_machine", "healer_machine", "pasture_machine", "fossil_machine",
                "trade_machine", "clone_machine",
                "pokedex", "poke_phone",
                "helix_fossil", "dome_fossil", "old_amber"
        ));

        // How many items (not stacks) to roll in a chest.
        // Bias toward 2~3 items, with small chance of 1 or 4.
        int maxRolls = plugin.getConfig().getInt("loot-chests.rolls", 4);
        maxRolls = Util.clamp(maxRolls, 1, 10);
        int rolls = biasedRollCount(r, Math.min(4, maxRolls));

        // Build pools from the registry dynamically so it automatically grows with your item list.
        List<ItemDef> medicines = new ArrayList<>();
        // tumblestones / misc utility
        List<ItemDef> miscCommon = new ArrayList<>();
        // vitamins + X-items (keep very rare)
        List<ItemDef> battleVitamins = new ArrayList<>();
        List<ItemDef> held = new ArrayList<>();
        List<ItemDef> machines = new ArrayList<>();

        for (ItemDef def : registry.all().values()) {
            if (def == null) continue;
            String id = def.id;
            if (id == null) continue;

            if (banned.contains(id.toLowerCase(Locale.ROOT))) continue;

            // Evolution stones are handled by ore mining, so we exclude them here.
            if (id.endsWith("_stone") || id.equals("moon_stone")) {
                continue;
            }

            // Do NOT include berries/apricorns in loot chests.
            if (id.endsWith("_berry") || (id.endsWith("_apricorn") && !id.startsWith("seed_"))) {
                continue;
            }

            // Ban advanced/breeding items from wild loot chests (they come from fishing / events).
            // MT / Tutor 招式不再通过野外宝箱发放，而是由教学师 NPC 提供。
            // Keep medicines & normal machines (TM/HM/TR) in chests.
            String lowerId = id.toLowerCase(Locale.ROOT);
            if (lowerId.startsWith("mt_") || lowerId.startsWith("tutor_")) {
                continue;
            }
            if (lowerId.endsWith("_mint")
                    || lowerId.endsWith("_wing")
                    || lowerId.equals("ability_capsule")
                    || lowerId.equals("ability_patch")
                    || lowerId.equals("silver_bottle_cap")
                    || lowerId.equals("gold_bottle_cap")) {
                continue;
            }

            if (def.type == ItemType.MEDICINE || def.type == ItemType.STATUS_CURE || def.type == ItemType.PP_RESTORE || def.type == ItemType.REVIVE) {
                medicines.add(def);
            } else if (def.type == ItemType.VITAMIN || def.type == ItemType.BATTLE) {
                // Vitamins & X-items can appear, but keep them very rare.
                battleVitamins.add(def);
            } else if (id.contains("tumblestone")) {
                miscCommon.add(def);
            } else if (def.type == ItemType.HELD) {
                held.add(def);
            } else {
                // This codebase's ItemType may not include a MACHINE enum; classify by id prefix.
                // Accept both "tm_01" and "tm01" naming styles.
                String lower = id.toLowerCase();
                boolean isMachine = lower.startsWith("tm_") || lower.startsWith("hm_") || lower.startsWith("tr_")
                        || lower.matches("tm\\d{1,3}") || lower.matches("hm\\d{1,3}") || lower.matches("tr\\d{1,3}");
                if (isMachine) machines.add(def);
                else miscCommon.add(def);
            }
        }

        // Fallback safety.
        if (medicines.isEmpty() && miscCommon.isEmpty() && held.isEmpty() && machines.isEmpty()) {
            return List.of();
        }

        List<ItemStack> out = new ArrayList<>();
        int machineCount = 0;

        for (int i = 0; i < rolls; i++) {
            double p = r.nextDouble();
            ItemDef chosen;

            // Target distribution (rough):
            // 60% held items, 10% machine (max 1 per chest), 22% misc utility, 5% vitamins/X, 3% medicines.
            if (p < 0.60 && !held.isEmpty()) {
                chosen = held.get(r.nextInt(held.size()));
                out.add(items.createItem(chosen, plugin.getLang(), 1));
                continue;
            }

            // Machines: rare and at most ONE per chest.
            if (p < 0.70 && machineCount == 0 && !machines.isEmpty()) {
                chosen = machines.get(r.nextInt(machines.size()));
                out.add(items.createItem(chosen, plugin.getLang(), 1));
                machineCount++;
                continue;
            }

            if (p < 0.92 && !miscCommon.isEmpty()) {
                chosen = miscCommon.get(r.nextInt(miscCommon.size()));
                int amt = 1 + r.nextInt(2);
                out.add(items.createItem(chosen, plugin.getLang(), amt));
                continue;
            }

            // Vitamins / X-items (very low frequency)
            if (p < 0.97 && !battleVitamins.isEmpty()) {
                chosen = battleVitamins.get(r.nextInt(battleVitamins.size()));
                out.add(items.createItem(chosen, plugin.getLang(), 1));
                continue;
            }

            if (!medicines.isEmpty()) {
                chosen = medicines.get(r.nextInt(medicines.size()));
                int max = plugin.getConfig().getInt("loot-chests.stack-medicine-max", 3);
                int amt = 1 + r.nextInt(Math.max(1, max));
                out.add(items.createItem(chosen, plugin.getLang(), amt));
            }
        }

        // Master Ball: independent per-chest chance (default 1/100).
        try {
            double mbChance = plugin.getConfig().getDouble("loot-chests.master-ball-chance", 0.01);
            if (mbChance > 0 && r.nextDouble() < mbChance) {
                ItemDef mb = registry.get("master_ball");
                if (mb != null) out.add(items.createItem(mb, plugin.getLang(), 1));
            }
        } catch (Throwable ignored) {}

        // De-dup/merge stacks when possible.
        Map<String, ItemStack> merged = new LinkedHashMap<>();
        for (ItemStack s : out) {
            if (s == null || s.getType() == null) continue;
            String key = s.getType() + ":" + (s.hasItemMeta() && s.getItemMeta().hasCustomModelData() ? s.getItemMeta().getCustomModelData() : 0)
                    + ":" + (s.hasItemMeta() && s.getItemMeta().hasDisplayName() ? s.getItemMeta().getDisplayName() : "");
            ItemStack existing = merged.get(key);
            if (existing == null) {
                merged.put(key, s);
            } else {
                existing.setAmount(Math.min(existing.getMaxStackSize(), existing.getAmount() + s.getAmount()));
            }
        }
        return new ArrayList<>(merged.values());
    }
}
