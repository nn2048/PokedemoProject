package win.pokedemo;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

import java.util.UUID;

/** GUI for the Trade Machine (two-player Pokémon exchange). */
public class TradeGui {

    // Layout (27)
    // 10: left selected
    // 11: left select
    // 12: left confirm
    // 13: status
    // 16: right selected
    // 15: right select
    // 17: right confirm
    // 26: clear/unlock

    public static void open(Player player, PokeDemoPlugin plugin, Location machineLoc) {
        if (player == null || plugin == null || machineLoc == null) return;
        TradeManager tm = plugin.getTradeManager();
        if (tm == null) return;
        Storage storage = plugin.getStorage();
        LangManager lang = plugin.getLang();

        String key = tm.keyOf(machineLoc);
        tm.join(key, player.getUniqueId());

        // If machine already has two different players and this player is neither, deny.
        if (tm.isFull(key) && !tm.isInSession(key, player.getUniqueId())) {
            player.sendMessage(lang.ui("machine.trade.in_use", "§c该交换机正在被其他玩家使用。"));
            return;
        }

        GuiHolder holder = new GuiHolder(GuiType.TRADE_MACHINE, player.getUniqueId());
        holder.tradeKey = key;
        Inventory inv = Bukkit.createInventory(holder, 27, UtilGui.title(lang.ui("machine.trade.title", "§e宝可梦交换机")));

        for (int i = 0; i < 27; i++) {
            inv.setItem(i, button(Material.GRAY_STAINED_GLASS_PANE, " ", List.of()));
        }

        UUID leftP = tm.getLeftPlayer(key);
        UUID rightP = tm.getRightPlayer(key);
        boolean isLeft = player.getUniqueId().equals(leftP);
        boolean isRight = player.getUniqueId().equals(rightP);

        // Left side
        PokemonInstance leftMon = null;
        UUID leftMonUuid = tm.getSelected(key, TradeManager.Side.LEFT);
        if (leftP != null) {
            PlayerProfile prof = storage.getProfile(leftP);
            if (prof != null && leftMonUuid != null) leftMon = prof.findByUuid(leftMonUuid);
        }

        inv.setItem(10, leftMon == null ? button(Material.BARRIER, lang.ui("machine.trade.left_not_selected","§c左侧未选择"), List.of(lang.ui("machine.trade.left_not_selected_lore","§7点击右侧按钮选择"))) : pokemonButton(leftMon));
        inv.setItem(11, button(Material.ENDER_PEARL, isLeft ? lang.ui("machine.trade.select_you","§b选择精灵(你)") : lang.ui("machine.trade.select_other","§7选择精灵"), List.of(
                lang.ui("machine.trade.select_lore1","§7打开队伍选择一只用于交换的精灵"),
                lang.ui("machine.trade.select_lore2","§7选中后将进入锁定状态")
        )));
        inv.setItem(12, button(Material.LIME_DYE, lang.ui("machine.trade.confirm","§a确定"), List.of(
                lang.ui("machine.trade.confirm_lore1","§7确认你选择的精灵"),
                lang.ui("machine.trade.confirm_lore2","§7双方都确认后将自动交换")
        )));

        // Right side
        PokemonInstance rightMon = null;
        UUID rightMonUuid = tm.getSelected(key, TradeManager.Side.RIGHT);
        if (rightP != null) {
            PlayerProfile prof = storage.getProfile(rightP);
            if (prof != null && rightMonUuid != null) rightMon = prof.findByUuid(rightMonUuid);
        }

        inv.setItem(16, rightMon == null ? button(Material.BARRIER, lang.ui("machine.trade.right_not_selected","§c右侧未选择"), List.of(lang.ui("machine.trade.right_not_selected_lore","§7点击左侧按钮选择"))) : pokemonButton(rightMon));
        inv.setItem(15, button(Material.ENDER_PEARL, isRight ? lang.ui("machine.trade.select_you","§b选择精灵(你)") : lang.ui("machine.trade.select_other","§7选择精灵"), List.of(
                lang.ui("machine.trade.select_lore1","§7打开队伍选择一只用于交换的精灵"),
                lang.ui("machine.trade.select_lore2","§7选中后将进入锁定状态")
        )));
        inv.setItem(17, button(Material.LIME_DYE, lang.ui("machine.trade.confirm","§a确定"), List.of(
                lang.ui("machine.trade.confirm_lore1","§7确认你选择的精灵"),
                lang.ui("machine.trade.confirm_lore2","§7双方都确认后将自动交换")
        )));

        boolean lc = tm.isConfirmed(key, TradeManager.Side.LEFT);
        boolean rc = tm.isConfirmed(key, TradeManager.Side.RIGHT);
        String status = lang.ui("machine.trade.waiting","§7等待选择…");
        if (leftMon != null && rightMon != null) {
            status = (lc ? lang.ui("machine.trade.left_confirmed","§a左侧已确认") : lang.ui("machine.trade.left_unconfirmed","§e左侧未确认")) + " §7| " + (rc ? lang.ui("machine.trade.right_confirmed","§a右侧已确认") : lang.ui("machine.trade.right_unconfirmed","§e右侧未确认"));
        }
        inv.setItem(13, button(Material.CLOCK, lang.ui("machine.trade.status","§d状态"), List.of(status,
                lang.ui("machine.trade.status_hint","§8提示：锁定精灵可用 /pokedemo unlockall 一键解除")
        )));

        inv.setItem(26, button(Material.BARRIER, lang.ui("machine.trade.clear","§c解除锁定/清空"), List.of(
                lang.ui("machine.trade.clear_lore1","§7解除本交换机对精灵的锁定"),
                lang.ui("machine.trade.clear_lore2","§7并清空双方选择")
        )));

        player.openInventory(inv);
    }

    public static void handleClick(Player player, GuiHolder holder, int rawSlot) {
        if (player == null || holder == null) return;
        PokeDemoPlugin plugin = PokeDemoPlugin.INSTANCE;
        if (plugin == null) return;
        LangManager lang = plugin.getLang();
        TradeManager tm = plugin.getTradeManager();
        if (tm == null) return;

        String key = holder.tradeKey;
        if (key == null) return;

        UUID leftP = tm.getLeftPlayer(key);
        UUID rightP = tm.getRightPlayer(key);
        boolean isLeft = player.getUniqueId().equals(leftP);
        boolean isRight = player.getUniqueId().equals(rightP);
        if (!isLeft && !isRight) {
            player.sendMessage(lang.ui("machine.trade.not_participant", "§c你不是该交换机的参与者。"));
            player.closeInventory();
            return;
        }

        // Clear/unlock
        if (rawSlot == 26) {
            // only allow either participant to clear
            tm.setConfirmed(key, TradeManager.Side.LEFT, false);
            tm.setConfirmed(key, TradeManager.Side.RIGHT, false);
            // unlock selected mons and clear selection using unlockAll-like helper
            if (isLeft) tm.unlockAll(player.getUniqueId());
            if (isRight) tm.unlockAll(player.getUniqueId());
            player.sendMessage(lang.ui("machine.trade.cleared", "§a已清空并解除该交换机的锁定。\n§7如仍有锁定可用 /pokedemo unlockall"));
            reopen(player, key);
            return;
        }

        // Select buttons
        if (rawSlot == 11 && isLeft) {
            player.sendMessage(lang.ui("machine.trade.choose_hint", "§b请在队伍中选择一只用于交换的精灵。\n§7提示：被锁定的精灵不可选择。\n§7可用 /pokedemo unlockall 一键解除锁定。"));
            UtilGui.openPartyTradeSelect(player, plugin.getStorage(), key, TradeManager.Side.LEFT);
            return;
        }
        if (rawSlot == 15 && isRight) {
            player.sendMessage(lang.ui("machine.trade.choose_hint", "§b请在队伍中选择一只用于交换的精灵。\n§7提示：被锁定的精灵不可选择。\n§7可用 /pokedemo unlockall 一键解除锁定。"));
            UtilGui.openPartyTradeSelect(player, plugin.getStorage(), key, TradeManager.Side.RIGHT);
            return;
        }

        // Confirm buttons
        if (rawSlot == 12 && isLeft) {
            if (tm.getSelected(key, TradeManager.Side.LEFT) == null) {
                player.sendMessage(lang.ui("machine.trade.not_selected", "§c你还没有选择精灵。"));
                return;
            }
            tm.setConfirmed(key, TradeManager.Side.LEFT, true);
            player.sendMessage(lang.ui("machine.trade.confirm_wait", "§a你已确认。等待对方确认…"));
            tryFinalize(player, key);
            return;
        }
        if (rawSlot == 17 && isRight) {
            if (tm.getSelected(key, TradeManager.Side.RIGHT) == null) {
                player.sendMessage(lang.ui("machine.trade.not_selected", "§c你还没有选择精灵。"));
                return;
            }
            tm.setConfirmed(key, TradeManager.Side.RIGHT, true);
            player.sendMessage(lang.ui("machine.trade.confirm_wait", "§a你已确认。等待对方确认…"));
            tryFinalize(player, key);
            return;
        }

        // clicks on other player's buttons
        if ((rawSlot == 11 || rawSlot == 12) && !isLeft) {
            player.sendMessage(lang.ui("machine.trade.left_buttons", "§7这是左侧玩家的操作按钮。"));
        }
        if ((rawSlot == 15 || rawSlot == 17) && !isRight) {
            player.sendMessage(lang.ui("machine.trade.right_buttons", "§7这是右侧玩家的操作按钮。"));
        }
    }

    private static void tryFinalize(Player player, String key) {
        PokeDemoPlugin plugin = PokeDemoPlugin.INSTANCE;
        if (plugin == null) return;
        TradeManager tm = plugin.getTradeManager();
        if (tm == null) return;
        Location loc = parseKeyToLocation(key);
        if (loc == null) return;

        if (!tm.isConfirmed(key, TradeManager.Side.LEFT) || !tm.isConfirmed(key, TradeManager.Side.RIGHT)) {
            reopen(player, key);
            return;
        }

        // Delay a few ticks to avoid click-race
        LangManager lang = plugin.getLang();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            boolean ok = tm.tryExecuteTrade(loc, () -> {});
            Player left = Bukkit.getPlayer(tm.getLeftPlayer(key));
            Player right = Bukkit.getPlayer(tm.getRightPlayer(key));
            if (ok) {
                if (left != null) left.sendMessage(lang.ui("machine.trade.success", "§a交换成功！"));
                if (right != null) right.sendMessage(lang.ui("machine.trade.success", "§a交换成功！"));
            } else {
                if (left != null) left.sendMessage(lang.ui("machine.trade.fail_changed", "§c交换失败：精灵状态已变化或被移动，请重新选择。"));
                if (right != null) right.sendMessage(lang.ui("machine.trade.fail_changed", "§c交换失败：精灵状态已变化或被移动，请重新选择。"));
            }
            if (left != null && left.isOnline()) reopen(left, key);
            if (right != null && right.isOnline()) reopen(right, key);
        }, 5L);

        reopen(player, key);
    }

    private static void reopen(Player player, String key) {
        PokeDemoPlugin plugin = PokeDemoPlugin.INSTANCE;
        if (plugin == null) return;
        Location loc = parseKeyToLocation(key);
        if (loc == null) return;
        open(player, plugin, loc);
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

    private static ItemStack pokemonButton(PokemonInstance p) {
        PokeDemoPlugin plugin = PokeDemoPlugin.INSTANCE;
        LangManager lang = (plugin == null) ? null : plugin.getLang();
        ItemStack it = UtilGui.pokemonIcon(p);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setLore(List.of(
                    "§7Lv." + p.level + "  §f" + p.displayName(),
                    p.uiLocked ? lang.ui("common.locked_tag","§c(锁定中)") : ""
            ));
            it.setItemMeta(meta);
        }
        return it;
    }

    private static ItemStack button(Material mat, String name, List<String> lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            it.setItemMeta(meta);
        }
        return it;
    }
}