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

/** GUI for the Clone Machine. */
public class CloneGui {

    // Layout (27)
    // 10: selected Mew
    // 12: open PC to select Mew
    // 13: start
    // 14: chance display
    // 15/16/17/18: add chance by consuming blocks
    // 22: timer/status
    // 26: unlock/clear

    public static void open(Player player, PokeDemoPlugin plugin, Location machineLoc) {
        if (player == null || plugin == null || machineLoc == null) return;
        CloneManager cm = plugin.getCloneManager();
        if (cm == null) return;
        Storage storage = plugin.getStorage();
        LangManager lang = plugin.getLang();

        String key = cm.keyOf(machineLoc);
        cm.ensureOwner(key, player.getUniqueId());

        GuiHolder holder = new GuiHolder(GuiType.CLONE_MACHINE, player.getUniqueId());
        holder.cloneKey = key;
        Inventory inv = Bukkit.createInventory(holder, 27, UtilGui.title((lang != null) ? lang.ui("gui.clone.title", "§d宝可梦克隆仪") : "§d宝可梦克隆仪"));

        // fill glass
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, button(Material.GRAY_STAINED_GLASS_PANE, " ", List.of()));
        }

        UUID mewUuid = cm.getSelectedMew(key);
        PokemonInstance mew = null;
        PlayerProfile prof = storage.getProfile(player.getUniqueId());
        if (mewUuid != null && prof != null) mew = prof.findByUuid(mewUuid);

        inv.setItem(10, mew == null ? button(Material.BARRIER,
                (lang != null) ? lang.ui("gui.clone.no_mew", "§c未选择梦幻") : "§c未选择梦幻",
                List.of((lang != null) ? lang.ui("gui.clone.no_mew.lore", "§7点击右侧按钮选择") : "§7点击右侧按钮选择"))
                : pokemonButton(lang, mew));

        inv.setItem(12, button(Material.ENDER_PEARL,
                (lang != null) ? lang.ui("gui.clone.select_mew", "§b选择梦幻") : "§b选择梦幻",
                List.of(
                        (lang != null) ? lang.ui("gui.clone.select_mew.l1", lang.ui("machine.clone.select_lore1", "§7打开队伍选择一只可克隆的梦幻")) : lang.ui("machine.clone.select_lore1", "§7打开队伍选择一只可克隆的梦幻"),
                        (lang != null) ? lang.ui("gui.clone.select_mew.l2", lang.ui("machine.clone.select_lore2", "§7选中后将进入锁定状态")) : lang.ui("machine.clone.select_lore2", "§7选中后将进入锁定状态")
                )));

        int bonus = cm.getBonus(key);
        inv.setItem(14, button(Material.PAPER,
                (lang != null) ? lang.ui("gui.clone.chance", "§e克隆成功率") : "§e克隆成功率",
                List.of(
                        (lang != null) ? lang.uiFmt("gui.clone.chance.bonus", "§f当前加成：§a+{n}%", java.util.Map.of("n", String.valueOf(bonus))) : lang.ui("machine.clone.bonus_prefix", "§f当前加成：§a+") + bonus + "%",
                        (lang != null) ? lang.ui("gui.clone.chance.hint", "§7可通过消耗材料提高") : "§7可通过消耗材料提高",
                        (lang != null) ? lang.ui("gui.clone.chance.cap", "§7上限：§a+50%") : "§7上限：§a+50%"
                )));

        inv.setItem(15, addMat(lang, Material.IRON_BLOCK, lang.ui("machine.clone.mat_iron", "铁块"), 5));
        inv.setItem(16, addMat(lang, Material.GOLD_BLOCK, lang.ui("machine.clone.mat_gold", "金块"), 10));
        inv.setItem(17, addMat(lang, Material.DIAMOND_BLOCK, lang.ui("machine.clone.mat_diamond", "钻石块"), 15));
        inv.setItem(18, addMat(lang, Material.NETHERITE_BLOCK, lang.ui("machine.clone.mat_netherite", "下界合金块"), 25));

        boolean running = cm.isRunning(key);
        long endAt = cm.getEndAt(key);
        if (running && endAt > 0) {
            long rem = Math.max(0L, endAt - System.currentTimeMillis());
            long sec = rem / 1000L;
            inv.setItem(22, button(Material.CLOCK,
                    (lang != null) ? lang.ui("gui.clone.running", "§d正在克隆中...") : "§d正在克隆中...",
                    List.of(
                            (lang != null) ? lang.uiFmt("gui.clone.running.rem", "§7剩余：§f{sec}§7 秒", java.util.Map.of("sec", String.valueOf(sec))) : lang.ui("machine.clone.remaining_prefix", "§7剩余：§f") + sec + lang.ui("common.seconds_gray", "§7 秒"),
                            (lang != null) ? lang.ui("gui.clone.running.break", lang.ui("machine.clone.break_unlock_hint", "§8挖掉机器将自动解除锁定")) : lang.ui("machine.clone.break_unlock_hint", "§8挖掉机器将自动解除锁定")
                    )));
        } else {
            inv.setItem(22, button(Material.CLOCK,
                    (lang != null) ? lang.ui("gui.clone.idle", "§7待机") : "§7待机",
                    List.of((lang != null) ? lang.ui("gui.clone.idle.hint", lang.ui("machine.clone.hint1", "§7选择梦幻并设置概率后开始")) : lang.ui("machine.clone.hint1", "§7选择梦幻并设置概率后开始"))));
        }

        inv.setItem(13, button(Material.AMETHYST_SHARD,
                (lang != null) ? lang.ui("gui.clone.start", "§d开始克隆") : "§d开始克隆",
                List.of(
                        (lang != null) ? lang.ui("gui.clone.start.l1", lang.ui("machine.clone.hint2", "§7开始后将进入 60 秒倒计时")) : lang.ui("machine.clone.hint2", "§7开始后将进入 60 秒倒计时"),
                        (lang != null) ? lang.ui("gui.clone.start.l2", "§7结束后按成功率判定") : "§7结束后按成功率判定",
                        (lang != null) ? lang.ui("gui.clone.start.ok", lang.ui("machine.clone.outcome_success", "§a成功：§f生成超梦并摧毁克隆机")) : lang.ui("machine.clone.outcome_success", "§a成功：§f生成超梦并摧毁克隆机"),
                        (lang != null) ? lang.ui("gui.clone.start.fail", lang.ui("machine.clone.outcome_fail", "§c失败：§f生成低等级百变怪，机器保留")) : lang.ui("machine.clone.outcome_fail", "§c失败：§f生成低等级百变怪，机器保留")
                )));

        inv.setItem(26, button(Material.BARRIER,
                (lang != null) ? lang.ui("gui.clone.clear", "§c解除锁定/清空") : "§c解除锁定/清空",
                List.of(
                        (lang != null) ? lang.ui("gui.clone.clear.l1", "§7解除本机器对梦幻的锁定") : "§7解除本机器对梦幻的锁定",
                        (lang != null) ? lang.ui("gui.clone.clear.l2", "§7并清空概率加成") : "§7并清空概率加成",
                        (lang != null) ? lang.ui("gui.clone.clear.l3", lang.ui("machine.clone.outcome_note", "§8(不会重置梦幻的克隆次数)")) : lang.ui("machine.clone.outcome_note", "§8(不会重置梦幻的克隆次数)")
                )));

        player.openInventory(inv);
    }

    public static void handleClick(Player player, GuiHolder holder, int rawSlot) {
        if (player == null || holder == null) return;
        PokeDemoPlugin plugin = PokeDemoPlugin.INSTANCE;
        if (plugin == null) return;
        LangManager lang = plugin.getLang();
        CloneManager cm = plugin.getCloneManager();
        if (cm == null) return;

        String key = holder.cloneKey;
        if (key == null) return;

        // select
        if (rawSlot == 12) {
            player.sendMessage((lang != null)
                    ? lang.ui("clone.select_hint", lang.ui("machine.clone.choose_hint", "§b请在队伍中选择一只可克隆的梦幻。\n§7提示：被锁定的精灵不可选择。\n§7可用 /pokedemo unlockall 一键解除锁定。"))
                    : lang.ui("machine.clone.choose_hint", "§b请在队伍中选择一只可克隆的梦幻。\n§7提示：被锁定的精灵不可选择。\n§7可用 /pokedemo unlockall 一键解除锁定。")
            );
            openPartySelect(player, key);
            return;
        }

        // clear
        if (rawSlot == 26) {
            cm.clearSelection(player.getUniqueId(), key);
            player.sendMessage((lang != null)
                    ? lang.ui("clone.cleared", lang.ui("machine.clone.cleared", "§a已解除锁定并清空该克隆机设置。"))
                    : lang.ui("machine.clone.cleared", "§a已解除锁定并清空该克隆机设置。")
            );
            // reopen
            reopen(player, key);
            return;
        }

        // add bonus
        if (rawSlot == 15) { addBonus(player, key, Material.IRON_BLOCK, 5); return; }
        if (rawSlot == 16) { addBonus(player, key, Material.GOLD_BLOCK, 10); return; }
        if (rawSlot == 17) { addBonus(player, key, Material.DIAMOND_BLOCK, 15); return; }
        if (rawSlot == 18) { addBonus(player, key, Material.NETHERITE_BLOCK, 25); return; }

        // start
        if (rawSlot == 13) {
            Location loc = parseKeyToLocation(key);
            if (loc == null) {
            player.sendMessage((lang != null)
                        ? lang.ui("clone.invalid_location", lang.ui("machine.clone.bad_loc", "§c机器位置无效，请重新放置。"))
                        : lang.ui("machine.clone.bad_loc", "§c机器位置无效，请重新放置。")
                );
                return;
            }
            cm.startClone(player, loc, () -> {
                // if still online and viewing this GUI, refresh
                try {
                    if (player.isOnline()) {
                        if (player.getOpenInventory() != null && player.getOpenInventory().getTopInventory().getHolder() instanceof GuiHolder h2
                                && h2.type == GuiType.CLONE_MACHINE && key.equals(h2.cloneKey)) {
                            open(player, plugin, loc);
                        }
                    }
                } catch (Throwable ignored) {}
            });
            reopen(player, key);
        }
    }

    private static void addBonus(Player player, String key, Material mat, int inc) {
        PokeDemoPlugin plugin = PokeDemoPlugin.INSTANCE;
        if (plugin == null) return;
        CloneManager cm = plugin.getCloneManager();
        if (cm == null) return;
        LangManager lang = plugin.getLang();
        int cur = cm.getBonus(key);
        if (cur >= 50) {
            player.sendMessage((lang != null) ? lang.ui("machine.clone.bonus_cap", "§7成功率加成已达上限：§a+50%§7。") : "§7成功率加成已达上限：§a+50%§7。");
            return;
        }
        // has?
        if (!player.getInventory().containsAtLeast(new ItemStack(mat), 1)) {
            player.sendMessage((lang != null)
                    ? lang.uiFmt("clone.bonus.no_mat", "§c你没有足够的材料：{mat}", java.util.Map.of("mat", mat.name()))
                    : lang.ui("machine.clone.no_mats", "§c你没有足够的材料：") + mat.name());
            return;
        }
        // consume one
        removeOne(player, mat);
        int next = Math.min(50, cur + inc);
        cm.setBonus(key, next);
        player.sendMessage((lang != null)
                ? lang.uiFmt("clone.bonus.added", "§a已添加成功率：§f+{inc}% §7→ §a+{next}%", java.util.Map.of("inc", String.valueOf(inc), "next", String.valueOf(next)))
                : lang.ui("machine.clone.added_bonus_prefix", "§a已添加成功率：§f+") + inc + "% §7→ §a+" + next + "%");
        reopen(player, key);
    }

    private static void removeOne(Player player, Material mat) {
        var inv = player.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack it = inv.getItem(i);
            if (it == null || it.getType() != mat) continue;
            int a = it.getAmount();
            if (a <= 1) inv.setItem(i, null);
            else it.setAmount(a - 1);
            break;
        }
        player.updateInventory();
    }

    private static void reopen(Player player, String key) {
        PokeDemoPlugin plugin = PokeDemoPlugin.INSTANCE;
        if (plugin == null) return;
        LangManager lang = plugin.getLang();
        Location loc = parseKeyToLocation(key);
        if (loc == null) return;
        open(player, plugin, loc);
    }

    /** Party-only selector for clone machine. */
    public static void openPartySelect(Player player, String cloneKey) {
        if (player == null || cloneKey == null) return;
        PokeDemoPlugin plugin = PokeDemoPlugin.INSTANCE;
        if (plugin == null) return;
        LangManager lang = plugin.getLang();
        Storage storage = plugin.getStorage();
        PlayerProfile prof = storage.getProfile(player.getUniqueId());
        if (prof == null) return;

        GuiHolder holder = new GuiHolder(GuiType.PARTY_CLONE_SELECT, player.getUniqueId());
        holder.cloneKey = cloneKey;
        Inventory inv = Bukkit.createInventory(holder, 27, UtilGui.title(lang.ui("machine.clone.pick_title", "§d选择梦幻 §7(队伍)")));

        // Clone selection requires the player to have at least one OTHER non-egg pokemon in party.
        int nonEggCount = 0;
        for (int i = 0; i < Math.min(6, prof.party.size()); i++) {
            PokemonInstance p = prof.party.get(i);
            if (p != null && !p.isEgg) nonEggCount++;
        }

        for (int i = 0; i < 27; i++) {
            inv.setItem(i, button(Material.GRAY_STAINED_GLASS_PANE, " ", List.of()));
        }
        for (int i = 0; i < 6; i++) {
            if (i >= prof.party.size()) {
                inv.setItem(i, button(Material.BLACK_STAINED_GLASS_PANE, lang.ui("common.empty_slot", "§7空槽位"), List.of()));
                continue;
            }
            PokemonInstance p = prof.party.get(i);
            ItemStack icon = UtilGui.pokemonIcon(p);
            ItemMeta meta = icon.getItemMeta();
            meta.setDisplayName("§a" + p.displayName() + " §7Lv." + p.level);
            List<String> lore = new ArrayList<>();
            if (p.isEgg) {
                lore.add(lang.ui("machine.clone.no_egg", "§c蛋不能用于克隆"));
            } else if (p.uiLocked) {
                lore.add(lang.ui("common.locked_cannot_select", "§c已锁定，无法选择"));
                lore.add(lang.ui("common.unlockall_hint_short", "§7可用 /pokedemo unlockall 一键解除"));
            } else if (!"mew".equalsIgnoreCase(p.speciesId)) {
                lore.add(lang.ui("machine.clone.only_mew", "§7仅梦幻可用于克隆"));
            } else {
                int otherNonEgg = nonEggCount - 1; // this Mew itself is non-egg
                if (otherNonEgg <= 0) {
                    lore.add(lang.ui("machine.clone.party_invalid", "§c队伍不能只有梦幻/蛋"));
                    lore.add(lang.ui("machine.clone.party_need_other", "§7请至少携带 1 只其它可战斗精灵"));
                } else {
                    lore.add(lang.ui("machine.clone.click_pick", "§e点击选择这只梦幻"));
                }
            }
            meta.setLore(lore);
            icon.setItemMeta(meta);
            inv.setItem(i, icon);
        }

        inv.setItem(26, button(Material.BARRIER, lang.ui("common.back_red", "§c返回"), List.of(lang.ui("machine.clone.back_to_machine", "§7返回克隆机界面"))));
        player.openInventory(inv);
    }

    private static Location parseKeyToLocation(String key) {
        try {
            String[] a = key.split(":", 2);
            String[] xyz = a[1].split(",");
            var w = Bukkit.getWorld(a[0]);
            if (w == null || xyz.length != 3) return null;
            return new Location(w, Integer.parseInt(xyz[0]), Integer.parseInt(xyz[1]), Integer.parseInt(xyz[2]));
        } catch (Throwable t) {
            return null;
        }
    }

    private static ItemStack addMat(LangManager lang, Material mat, String cn, int inc) {
        return button(mat, "§e+" + inc + "% §f(" + cn + ")", List.of(
                lang.ui("machine.clone.consume_one_prefix", "§7点击消耗 1 个 ") + cn,
                lang.ui("machine.clone.raise_success", "§7提升克隆成功率"),
                lang.ui("machine.clone.raise_cap", "§7最高到 §a+50%")
        ));
    }

    private static ItemStack pokemonButton(LangManager lang, PokemonInstance p) {
        ItemStack it = UtilGui.pokemonIcon(p);
        ItemMeta meta = it.getItemMeta();
        List<String> lore = new ArrayList<>();
        lore.add("§7Lv." + p.level);
        lore.add(lang.ui("machine.clone.times_prefix","§7克隆次数：§f") + p.mewCloneAttempts + "§7/3");
        if (p.mewCloneDisabled || p.mewCloneAttempts >= 3) {
            lore.add(lang.ui("machine.clone.no_more","§c已无法再克隆"));
        } else {
            lore.add(lang.ui("machine.clone.can_clone","§a可克隆"));
        }
        meta.setDisplayName("§b" + p.displayName());
        meta.setLore(lore);
        it.setItemMeta(meta);
        return it;
    }

    private static ItemStack button(Material mat, String name, List<String> lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null) meta.setLore(lore);
            it.setItemMeta(meta);
        }
        return it;
    }
}