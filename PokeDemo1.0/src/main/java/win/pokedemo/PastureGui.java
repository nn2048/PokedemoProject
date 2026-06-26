package win.pokedemo;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Breeding ranch UI (foundation version). */
public final class PastureGui {

    // Layout (user-defined):
    // - Pokémon A: 2nd row, 2nd slot (index 10)
    // - Pokémon B: 2nd row, 2nd-to-last slot (index 16)
    // - Egg: 3rd row (moved down one row), centered (index 22)
    private static final int SLOT_A = 10;   // row2 col2
    private static final int SLOT_B = 16;   // row2 col8
    private static final int SLOT_EGG = 22; // row3 col5
    private static final int SLOT_CLOSE = 26;

    private PastureGui() {}

    private static String ui(LangManager lang, String key, String fallback) {
        return (lang == null) ? fallback : lang.ui(key, fallback);
    }

    public static void open(Player player, PokeDemoPlugin plugin, Location pastureLoc) {
        if (player == null || plugin == null || pastureLoc == null) return;
        LangManager lang = plugin.getLang();
        PastureManager pm = plugin.getPastureManager();
        if (pm == null) return;

        String key = pm.keyOf(pastureLoc);
        pm.ensureOwner(key, player.getUniqueId());

        GuiHolder holder = new GuiHolder(GuiType.PASTURE, player.getUniqueId());
        holder.pastureKey = key;

        Inventory inv = Bukkit.createInventory(holder, 27, UtilGui.title(ui(lang, "machine.pasture.gui_title", "§d精灵牧场")));
        // fill background
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, pane());

        UUID a = pm.getSelected(key, PastureManager.Slot.A);
        UUID b = pm.getSelected(key, PastureManager.Slot.B);

        PastureManager.BreedingState st = pm.state(key);

        inv.setItem(SLOT_A, slotIcon(plugin, player.getUniqueId(), a, ui(lang, "machine.pasture.slot_a", "精灵A")));
        inv.setItem(SLOT_B, slotIcon(plugin, player.getUniqueId(), b, ui(lang, "machine.pasture.slot_b", "精灵B")));
        inv.setItem(SLOT_EGG, eggIcon(lang, st));

        inv.setItem(SLOT_CLOSE, UtilGui.button(Material.BARRIER, ui(lang, "common.close", "§c关闭"), List.of()));
        player.openInventory(inv);
    }

    public static void handleClick(Player player, GuiHolder holder, int rawSlot) {
        if (player == null || holder == null) return;
        PokeDemoPlugin plugin = PokeDemoPlugin.INSTANCE;
        if (plugin == null) { player.closeInventory(); return; }
        LangManager lang = plugin.getLang();
        if (rawSlot == SLOT_CLOSE) { player.closeInventory(); return; }

        // Claim egg (opens ball selector)
        if (rawSlot == SLOT_EGG) {
            PastureManager pm = plugin.getPastureManager();
            if (pm == null) return;
            PastureManager.BreedingState st = pm.state(holder.pastureKey);
            if (st == null || st.remainingSeconds() > 0 || st.running) {
                player.sendMessage(ui(lang, "machine.pasture.no_egg", "§7还没有蛋可以领取。"));
                return;
            }
            // Only when status indicates ready.
            if (st == null || st.running || st.remainingSeconds() > 0 || (st.pauseReason != null && !st.pauseReason.isBlank())) {
                player.sendMessage(ui(lang, "machine.pasture.no_egg", "§7还没有蛋可以领取。"));
                return;
            }
            if (!plugin.getConfig().getBoolean("breeding.pasture.enabled", true)) {
                player.sendMessage(ui(lang, "machine.pasture.disabled", "§c精灵牧场已被管理员禁用。"));
                return;
            }
            openBallSelectGui(player, holder.pastureKey);
            return;
        }

        if (rawSlot != SLOT_A && rawSlot != SLOT_B) return;
        if (!plugin.getConfig().getBoolean("breeding.pasture.enabled", true)) {
            player.sendMessage(ui(lang, "machine.pasture.disabled", "§c精灵牧场已被管理员禁用。"));
            player.closeInventory();
            return;
        }

        PastureManager pm = plugin.getPastureManager();
        if (pm == null) return;

        PastureManager.Slot slot = (rawSlot == SLOT_A) ? PastureManager.Slot.A : PastureManager.Slot.B;
        pm.beginSelect(player.getUniqueId(), holder.pastureKey, slot);
        UtilGui.openPc(player, plugin.getStorage(), 0);
    }

    /** Open ball select GUI (same style as battle capture) for claiming an egg. */
    private static void openBallSelectGui(Player player, String pastureKey) {
        PokeDemoPlugin plugin = PokeDemoPlugin.INSTANCE;
        if (plugin == null) return;
        LangManager lang = plugin.getLang();
        java.util.Map<String, Integer> counts = new java.util.LinkedHashMap<>();
        for (org.bukkit.inventory.ItemStack it : player.getInventory().getContents()) {
            if (it == null || it.getType() == org.bukkit.Material.AIR) continue;
            String id = plugin.getItems().getItemId(it);
            if (id == null) continue;
            ItemDef def = plugin.getItemRegistry().get(id);
            if (def == null || def.type != ItemType.BALL) continue;
            counts.put(def.id, counts.getOrDefault(def.id, 0) + it.getAmount());
        }
        if (counts.isEmpty()) {
            player.sendMessage(ui(lang, "machine.pasture.no_ball", "§7你背包里没有可用的精灵球。"));
            return;
        }

        GuiHolder holder = new GuiHolder(GuiType.PASTURE_BALL_SELECT, player.getUniqueId());
        holder.pastureKey = pastureKey;

        org.bukkit.inventory.Inventory inv = org.bukkit.Bukkit.createInventory(holder, 18,
                UtilGui.title(ui(lang, "machine.pasture.choose_ball_title", "选择精灵球")));

        int slot = 0;
        for (java.util.Map.Entry<String, Integer> e : counts.entrySet()) {
            if (slot >= 9) break;
            String ballId = e.getKey();
            int cnt = e.getValue();
            ItemDef def = plugin.getItemRegistry().get(ballId);
            org.bukkit.inventory.ItemStack button = plugin.getItems().createItem(def, plugin.getLang(), Math.min(cnt, 64));
            org.bukkit.inventory.meta.ItemMeta meta = button.getItemMeta();
            if (meta != null) {
                java.util.List<String> lore = meta.hasLore() ? new java.util.ArrayList<>(meta.getLore()) : new java.util.ArrayList<>();
                lore.add(ui(lang, "machine.pasture.ball_click_use_prefix", "§8点击使用（拥有 ") + cnt + "）");
                meta.setLore(lore);
                button.setItemMeta(meta);
            }
            inv.setItem(slot++, button);
        }

        inv.setItem(17, UtilGui.button(org.bukkit.Material.ARROW,
                ui(lang, "common.back", "§e返回"),
                java.util.List.of(ui(lang, "machine.pasture.back_to_pasture", "§7返回牧场界面"))));
        player.openInventory(inv);
    }

    /** Handle click in pasture ball select GUI. */
    public static void handleBallSelectClick(Player player, GuiHolder holder, int rawSlot, org.bukkit.inventory.ItemStack clicked) {
        if (player == null || holder == null) return;
        PokeDemoPlugin plugin = PokeDemoPlugin.INSTANCE;
        if (plugin == null) return;
        LangManager lang = plugin.getLang();
// Back
        if (rawSlot == 17) {
            player.closeInventory();
            // reopen pasture
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                Location loc = plugin.getPastureManager() == null ? null : plugin.getPastureBreedingService().parseKeyPublic(holder.pastureKey);
                if (loc != null) PastureGui.open(player, plugin, loc);
            });
            return;
        }

        if (rawSlot < 0 || rawSlot >= 9) return;
        if (clicked == null || clicked.getType() == org.bukkit.Material.AIR) return;

        String id = plugin.getItems().getItemId(clicked);
        if (id == null) return;
        ItemDef def = plugin.getItemRegistry().get(id);
        if (def == null || def.type != ItemType.BALL) return;

        // consume one
        if (!consumeOneBall(player, def.id)) {
            player.sendMessage(ui(lang, "machine.pasture.no_ball", "§7你背包里没有可用的精灵球。"));
            player.closeInventory();
            return;
        }

        // Claim egg as a Pokémon instance
        boolean ok = plugin.getPastureBreedingService().claimEggWithBall(player.getUniqueId(), holder.pastureKey, def.id);
        player.closeInventory();
        if (!ok) {
            player.sendMessage(ui(lang, "machine.pasture.claim_fail", "§c领取蛋失败。"));
            return;
        }
        // reopen pasture
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Location loc = plugin.getPastureBreedingService().parseKeyPublic(holder.pastureKey);
            if (loc != null) PastureGui.open(player, plugin, loc);
        });
    }

    private static boolean consumeOneBall(Player player, String ballId) {
        PokeDemoPlugin plugin = PokeDemoPlugin.INSTANCE;
        if (plugin == null) return false;
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            org.bukkit.inventory.ItemStack it = player.getInventory().getItem(i);
            if (it == null || it.getType() == org.bukkit.Material.AIR) continue;
            String id = plugin.getItems().getItemId(it);
            if (id == null) continue;
            ItemDef def = plugin.getItemRegistry().get(id);
            if (def == null || def.type != ItemType.BALL) continue;
            if (!def.id.equals(ballId)) continue;

            int amt = it.getAmount();
            if (amt <= 1) player.getInventory().setItem(i, null);
            else it.setAmount(amt - 1);
            player.updateInventory();
            return true;
        }
        return false;
    }

    private static ItemStack pane() {
        ItemStack it = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(" ");
        it.setItemMeta(m);
        return it;
    }

    private static ItemStack eggIcon(LangManager lang, PastureManager.BreedingState st) {
        boolean ready = st != null && !st.running && st.remainingSeconds() <= 0 && (st.pauseReason == null || st.pauseReason.isBlank());
        ItemStack it = new ItemStack(ready ? Material.EGG : Material.GRAY_DYE);
        ItemMeta m = it.getItemMeta();
        String status;
        if (st == null) {
            status = ui(lang, "machine.pasture.waiting", "等待");
        } else if (st.running) {
            status = ui(lang, "pasture.egg.status.running", "§dBreeding");
        } else if (st.remainingSeconds() <= 0 && (st.pauseReason == null || st.pauseReason.isBlank())) {
            status = ui(lang, "pasture.egg.status.ready", "§aEgg ready");
        } else if (st.pauseReason != null && !st.pauseReason.isBlank()) {
            status = ui(lang, "pasture.egg.status.paused", "§ePaused");
        } else {
            status = ui(lang, "machine.pasture.waiting", "等待");
        }
        long rem = (st == null) ? 0 : st.remainingSeconds();
        String time = (st == null) ? "??:??" : formatTime(rem);
        int groups = (st == null) ? 0 : st.groups;
        m.setDisplayName(ui(lang, "machine.pasture.egg_unknown", "§f蛋 §7(???)"));

        List<String> lore = new ArrayList<>();
        lore.add(ui(lang, "machine.pasture.status_label", "§7状态：") + status);
        if (st != null) {
            lore.add(ui(lang, "machine.pasture.remaining_prefix", "§7剩余：§f") + time);
            lore.add(ui(lang, "machine.pasture.speed_groups", "§7加速组：§f") + groups + ui(lang, "machine.pasture.speed_group_note", "§7 (每9方块=1组)"));
            if (st.pauseReason != null && !st.pauseReason.isBlank()) lore.add(st.pauseReason);
        }
        lore.addAll(List.of(
                ui(lang, "machine.pasture.gender_unknown", "§7性别：§f?"),
                ui(lang, "machine.pasture.nature_unknown", "§7性格：§f?"),
                ui(lang, "machine.pasture.iv_unknown", "§7个体：§f??????")
        ));
        if (ready) lore.add(ui(lang, "machine.pasture.click_pick_ball", "§e点击选择球并领取蛋"));
        else lore.add(ui(lang, "machine.pasture.egg_note", "§8(完成繁殖后可领取蛋)")
        );
        m.setLore(lore);
        it.setItemMeta(m);
        return it;
    }

    private static String formatTime(long seconds) {
        if (seconds < 0) seconds = 0;
        long m = seconds / 60;
        long s = seconds % 60;
        return String.format("%02d:%02d", m, s);
    }

    private static ItemStack slotIcon(PokeDemoPlugin plugin, UUID owner, UUID pokemonUuid, String label) {
        LangManager lang = (plugin == null) ? null : plugin.getLang();
        ItemStack it;
        List<String> lore = new ArrayList<>();
        if (pokemonUuid == null) {
            it = new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
            ItemMeta m = it.getItemMeta();
            m.setDisplayName("§f" + label + ui(lang, "common.click_select_paren", " §7(点击选择)"));
            m.setLore(List.of(ui(lang, "machine.pasture.open_pc_pick", "§7点击后打开PC选择一只精灵")));
            it.setItemMeta(m);
            return it;
        }

        PlayerProfile prof = plugin.getStorage().getProfile(owner);
        PokemonInstance p = (prof == null) ? null : prof.findByUuid(pokemonUuid);
        if (p == null) {
            it = new ItemStack(Material.BARRIER);
            ItemMeta m = it.getItemMeta();
            m.setDisplayName("§c" + label + ui(lang, "common.missing_paren", " (丢失)"));
            m.setLore(List.of(ui(lang, "machine.pasture.mon_missing", "§7所选精灵不存在或已被移除"), ui(lang, "machine.pasture.reselect", "§7点击重新选择")));
            it.setItemMeta(m);
            return it;
        }

        it = UtilGui.pokemonIcon(p);
        ItemMeta m = it.getItemMeta();
        String g = switch (p.gender) {
            case "M" -> "§b♂";
            case "F" -> "§d♀";
            default -> "§7-";
        };
        m.setDisplayName("§a" + label + "：§f" + p.displayName() + " " + g + " §7Lv." + p.level);

        Nature n = Nature.fromId(p.nature);
        String natureName = (p.nature == null) ? "?" : ((lang == null) ? n.zhName : lang.natureName(n));
        lore.add(ui(lang, "machine.pasture.nature", "§7性格：§f") + natureName);
        lore.add(ui(lang, "machine.pasture.iv", "§7个体：§f") + p.ivHp + "/" + p.ivAtk + "/" + p.ivDef + "/" + p.ivSpa + "/" + p.ivSpd + "/" + p.ivSpe);
        lore.add(ui(lang, "machine.pasture.reselect_note", "§e点击：重新选择（将解除原精灵锁定）"));
        m.setLore(lore);
        it.setItemMeta(m);
        return it;
    }
}