package win.pokedemo;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * Fossil machine GUI.
 * Slots:
 *  - 11: Fossil select
 *  - 15: Redstone fuel (L=+1, R=+stack)
 *  - 22: Status (idle / running countdown / egg)
 */
public final class FossilGui {
    private static final int SLOT_FOSSIL = 11;
    private static final int SLOT_FUEL = 15;
    private static final int SLOT_STATUS = 22;
    private static final int SLOT_CLOSE = 26;

    private FossilGui() {}

    public static void open(Player player, PokeDemoPlugin plugin, Location loc) {
        if (player == null || plugin == null || loc == null) return;
        LangManager lang = plugin.getLang();
        FossilMachineManager fm = plugin.getFossilMachineManager();
        if (fm == null) return;

        String key = fm.keyOf(loc);
        if (key == null) return;

        GuiHolder holder = new GuiHolder(GuiType.FOSSIL_MACHINE, player.getUniqueId());
        holder.fossilKey = key;

        Inventory inv = Bukkit.createInventory(holder, 27, UtilGui.title(lang.ui("machine.fossil.title", "§6化石复活机")));
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, pane());

        refresh(inv, plugin, holder);
        inv.setItem(SLOT_CLOSE, UtilGui.button(Material.BARRIER, lang.ui("common.close","§c关闭"), List.of()));
        player.openInventory(inv);
    }

    private static void refresh(Inventory inv, PokeDemoPlugin plugin, GuiHolder holder) {
        LangManager lang = plugin.getLang();
        FossilMachineManager fm = plugin.getFossilMachineManager();
        FossilMachineManager.State st = fm.state(holder.fossilKey);
        inv.setItem(SLOT_FOSSIL, fossilIcon(plugin, st));
        inv.setItem(SLOT_FUEL, fuelIcon(plugin, st));
        inv.setItem(SLOT_STATUS, statusIcon(plugin, st));
    }

    public static void handleClick(Player player, GuiHolder holder, int rawSlot, boolean left, boolean right) {
        PokeDemoPlugin plugin = PokeDemoPlugin.INSTANCE;
        if (plugin == null || player == null || holder == null) return;
        LangManager lang = plugin.getLang();
        FossilMachineManager fm = plugin.getFossilMachineManager();
        if (fm == null) return;

        if (rawSlot == SLOT_CLOSE) {
            player.closeInventory();
            return;
        }

        FossilMachineManager.State st = fm.state(holder.fossilKey);
        if (st == null) return;

        // During countdown: block other operations.
        if (st.running && rawSlot != SLOT_STATUS) {
            player.sendMessage(lang.ui("machine.fossil.status_running_wait", "§7解析中，等待倒计时结束…"));
            return;
        }

        // Fossil slot -> open selection GUI
        if (rawSlot == SLOT_FOSSIL) {
            if (st.eggReady) {
                player.sendMessage(lang.ui("machine.fossil.status_egg_wait", "§7已有蛋待领取。"));
                return;
            }
            openFossilSelect(player, holder.fossilKey);
            return;
        }

        // Fuel slot
        if (rawSlot == SLOT_FUEL) {
            if (st.eggReady) {
                player.sendMessage(lang.ui("machine.fossil.status_egg_wait", "§7已有蛋待领取。"));
                return;
            }

            int toAdd = left ? 1 : (right ? 64 : 0);
            if (toAdd <= 0) return;
            int removed = removeItems(player, Material.REDSTONE, toAdd);
            if (removed <= 0) {
                player.sendMessage(lang.ui("machine.fossil.no_redstone", "§7你背包里没有红石。"));
                return;
            }
            if (!fm.addFuelSeconds(holder.fossilKey, removed)) {
                // give back
                player.getInventory().addItem(new ItemStack(Material.REDSTONE, removed));
                player.sendMessage(lang.ui("machine.fossil.cannot_add_fuel", "§7当前无法添加燃料。"));
                return;
            }
            player.sendMessage(lang.ui("machine.fossil.fuel_prefix", "§6已供能 §e") + removed + lang.ui("machine.fossil.seconds_suffix_gold", "§6 秒"));
            reopen(player, holder);
            return;
        }

        // Status slot
        if (rawSlot == SLOT_STATUS) {
            if (st.eggReady) {
                openBallSelect(player, holder.fossilKey);
                return;
            }

            // If idle and ready -> start
            if (!st.running) {
                if (st.fossilId == null) {
                    player.sendMessage(lang.ui("machine.fossil.need_fossil", "§7请先放入化石。"));
                    return;
                }
                if (st.fuelSeconds <= 0) {
                    player.sendMessage(lang.ui("machine.fossil.need_fuel", "§7请先添加红石供能。"));
                    return;
                }
                if (fm.start(holder.fossilKey)) {
                    player.sendMessage(lang.ui("machine.fossil.start_analyze", "§6开始解析…"));
                    reopen(player, holder);
                }
            }
        }
    }

    // ========== Fossil select GUI ==========
    private static void openFossilSelect(Player player, String fossilKey) {
        PokeDemoPlugin plugin = PokeDemoPlugin.INSTANCE;
        if (plugin == null) return;
        GuiHolder holder = new GuiHolder(GuiType.FOSSIL_FOSSIL_SELECT, player.getUniqueId());
        holder.fossilSelectReturnToKey = fossilKey;
        LangManager lang = plugin.getLang();
        Inventory inv = Bukkit.createInventory(holder, 18, UtilGui.title((lang != null) ? lang.ui("machine.fossil.choose_fossil_title", "选择化石") : "选择化石"));

        setFossilButton(inv, 0, plugin, "helix_fossil", lang.ui("machine.fossil.fossil_helix", "§b螺旋化石"));
        setFossilButton(inv, 1, plugin, "dome_fossil", lang.ui("machine.fossil.fossil_dome", "§a甲壳化石"));
        setFossilButton(inv, 2, plugin, "old_amber", lang.ui("machine.fossil.fossil_amber", "§6秘密琥珀"));
        // NOTE: suspicious_stone is used by the Fossil Analyzer machine (separate block),
        // so the Fossil Reviver should NOT accept it.

        inv.setItem(17, UtilGui.button(Material.ARROW, lang.ui("common.back", "§e返回"), List.of(lang.ui("common.back_to_machine", "§7返回机器界面"))));
        player.openInventory(inv);
    }

    private static void setFossilButton(Inventory inv, int slot, PokeDemoPlugin plugin, String id, String fallbackName) {
        LangManager lang = (plugin != null) ? plugin.getLang() : null;
        ItemDef def = plugin.getItemRegistry().get(id);
        ItemStack it = (def != null) ? plugin.getItems().createItem(def, plugin.getLang(), 1)
                : UtilGui.button(Material.PAPER, fallbackName, List.of());
        ItemMeta m = it.getItemMeta();
        if (m != null) {
            List<String> lore = m.hasLore() ? new ArrayList<>(m.getLore()) : new ArrayList<>();
            lore.add((lang != null) ? lang.ui("machine.fossil.put_one", "§7点击放入（消耗 1 个）") : "§7点击放入（消耗 1 个）");
            m.setLore(lore);
            it.setItemMeta(m);
        }
        inv.setItem(slot, it);
    }

    public static void handleFossilSelectClick(Player player, GuiHolder holder, int rawSlot, ItemStack clicked) {
        PokeDemoPlugin plugin = PokeDemoPlugin.INSTANCE;
        if (plugin == null || player == null || holder == null) return;
        LangManager lang = plugin.getLang();
        FossilMachineManager fm = plugin.getFossilMachineManager();
        if (fm == null) return;

        if (rawSlot == 17) {
            reopenMachine(player, holder.fossilSelectReturnToKey);
            return;
        }
        if (rawSlot < 0 || rawSlot > 8) return;
        if (clicked == null || clicked.getType() == Material.AIR) return;

        String id = plugin.getItems().getItemId(clicked);
        if (id == null) return;
        if (!List.of("helix_fossil", "dome_fossil", "old_amber").contains(id)) return;

        // consume 1 fossil from player inv
        if (!consumeOneCustomItem(player, id)) {
            player.sendMessage(lang.ui("machine.fossil.no_this_fossil", "§7你背包里没有这个化石。"));
            reopenMachine(player, holder.fossilSelectReturnToKey);
            return;
        }

        if (!fm.setFossil(holder.fossilSelectReturnToKey, id)) {
            // give back
            ItemDef def = plugin.getItemRegistry().get(id);
            if (def != null) player.getInventory().addItem(plugin.getItems().createItem(def, plugin.getLang(), 1));
            player.sendMessage(lang.ui("machine.fossil.cannot_put_fossil", "§7当前无法放入化石。"));
        } else {
            player.sendMessage(lang.ui("machine.fossil.put_ok", "§6已放入化石"));
        }
        reopenMachine(player, holder.fossilSelectReturnToKey);
    }

    // ========== Ball select (reuse style) ==========
    private static void openBallSelect(Player player, String fossilKey) {
        PokeDemoPlugin plugin = PokeDemoPlugin.INSTANCE;
        if (plugin == null) return;
        LangManager lang = plugin.getLang();

        Map<String, Integer> counts = new LinkedHashMap<>();
        for (ItemStack it : player.getInventory().getContents()) {
            if (it == null || it.getType() == Material.AIR) continue;
            String id = plugin.getItems().getItemId(it);
            if (id == null) continue;
            ItemDef def = plugin.getItemRegistry().get(id);
            if (def == null || def.type != ItemType.BALL) continue;
            counts.put(def.id, counts.getOrDefault(def.id, 0) + it.getAmount());
        }
        if (counts.isEmpty()) {
            player.sendMessage(lang.ui("machine.fossil.no_ball_for_egg","§7你背包里没有可用的精灵球。\n§7请先准备精灵球再领取蛋。"));
            return;
        }

        GuiHolder holder = new GuiHolder(GuiType.FOSSIL_BALL_SELECT, player.getUniqueId());
        holder.fossilKey = fossilKey;

        String title = (lang != null) ? lang.tr("ui.select_ball", lang.ui("machine.fossil.choose_ball_title", "选择精灵球")) : "选择精灵球";
        Inventory inv = Bukkit.createInventory(holder, 18, UtilGui.title(title));

        int slot = 0;
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            if (slot >= 9) break;
            String ballId = e.getKey();
            int cnt = e.getValue();
            ItemDef def = plugin.getItemRegistry().get(ballId);
            ItemStack button = plugin.getItems().createItem(def, plugin.getLang(), Math.min(cnt, 64));
            ItemMeta meta = button.getItemMeta();
            if (meta != null) {
                List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                lore.add(lang.ui("machine.fossil.ball_click_use_prefix", "§8点击使用（拥有 ") + cnt + "）");
                meta.setLore(lore);
                button.setItemMeta(meta);
            }
            inv.setItem(slot++, button);
        }
        inv.setItem(17, UtilGui.button(Material.ARROW, lang.ui("common.back", "§e返回"), List.of(lang.ui("common.back_to_machine", "§7返回机器界面"))));
        player.openInventory(inv);
    }

    public static void handleBallSelectClick(Player player, GuiHolder holder, int rawSlot, ItemStack clicked) {
        PokeDemoPlugin plugin = PokeDemoPlugin.INSTANCE;
        if (plugin == null || player == null || holder == null) return;
        LangManager lang = plugin.getLang();
        FossilMachineManager fm = plugin.getFossilMachineManager();
        if (fm == null) return;

        if (rawSlot == 17) {
            reopenMachine(player, holder.fossilKey);
            return;
        }
        if (rawSlot < 0 || rawSlot >= 9) return;
        if (clicked == null || clicked.getType() == Material.AIR) return;

        String id = plugin.getItems().getItemId(clicked);
        if (id == null) return;
        ItemDef def = plugin.getItemRegistry().get(id);
        if (def == null || def.type != ItemType.BALL) return;

        if (!consumeOneBall(player, def.id)) {
            player.sendMessage(lang.ui("machine.fossil.no_ball", "§7你背包里没有可用的精灵球。"));
            reopenMachine(player, holder.fossilKey);
            return;
        }

        boolean ok = fm.claimEggWithBall(player.getUniqueId(), holder.fossilKey, def.id);
        if (!ok) {
            player.sendMessage(lang.ui("machine.fossil.claim_fail", "§c领取失败。"));
        } else {
            player.sendMessage(lang.ui("machine.fossil.claim_ok", "§a已领取化石蛋！"));
        }
        reopenMachine(player, holder.fossilKey);
    }

    // ========== Icons ==========
    private static ItemStack fossilIcon(PokeDemoPlugin plugin, FossilMachineManager.State st) {
        LangManager lang = plugin.getLang();
        String name = lang.ui("machine.fossil.put_fossil_btn", "§e放入化石");
        List<String> lore = new ArrayList<>();
        if (st.fossilId != null) {
            name = lang.ui("machine.fossil.fossil_label", "§e化石：") + fossilCn(lang, st.fossilId);
            lore.add(lang.ui("machine.fossil.fossil_change_hint", "§7点击更换（化石放入后不可收回）"));
        } else {
            lore.add(lang.ui("machine.fossil.left_click_choose", "§7左键点击选择化石"));
        }
        return UtilGui.button(Material.PAPER, name, lore);
    }

    private static ItemStack fuelIcon(PokeDemoPlugin plugin, FossilMachineManager.State st) {
        LangManager lang = plugin.getLang();
        List<String> lore = new ArrayList<>();
        if ("suspicious_stone".equals(st.fossilId)) {
            lore.add(lang.ui("machine.fossil.use_water1", "§7点击消耗 1 桶水"));
            lore.add(lang.ui("machine.fossil.use_water2", "§7并返还 1 个空桶"));
            lore.add(st.waterReady ? lang.ui("machine.fossil.water_done", "§a已注入水") : lang.ui("machine.fossil.water_not", "§7尚未注入水"));
            return UtilGui.button(Material.WATER_BUCKET, lang.ui("machine.fossil.inject_water_btn", "§b注入水"), lore);
        }
        lore.add(lang.ui("machine.fossil.add_fuel_left", "§7左键：添加 1 个红石（+1秒）"));
        lore.add(lang.ui("machine.fossil.add_fuel_right", "§7右键：添加 64 个红石（+64秒，按你背包实际扣除）"));
        lore.add(lang.ui("machine.fossil.fuel_now", "§8当前燃料：") + st.fuelSeconds + lang.ui("machine.fossil.seconds_suffix", " 秒"));
        return UtilGui.button(Material.REDSTONE, lang.ui("machine.fossil.fuel_btn", "§c供能"), lore);
    }

    private static ItemStack statusIcon(PokeDemoPlugin plugin, FossilMachineManager.State st) {
        LangManager lang = plugin.getLang();
        FossilMachineManager fm = plugin.getFossilMachineManager();
        List<String> lore = new ArrayList<>();

        if (st.fossilReady) {
            lore.add(lang.ui("machine.fossil.click_claim", "§7点击领取化石"));
            String cn = fossilCn(lang, st.readyFossilId);
            return UtilGui.button(Material.AMETHYST_SHARD, lang.ui("machine.fossil.claimable", "§a可领取：") + cn, lore);
        }

        if (st.eggReady) {
            lore.add(lang.ui("machine.fossil.click_claim_egg", "§7点击领取蛋（选择精灵球）"));
            return UtilGui.button(Material.EGG, lang.ui("machine.fossil.egg", "§a蛋"), lore);
        }

        if (st.running) {
            int rem = fm.remainingSeconds(st);
            lore.add(lang.ui("machine.fossil.running", "§7解析中…"));
            lore.add(lang.ui("machine.fossil.countdown_prefix", "§7倒计时：§e") + rem + lang.ui("machine.fossil.seconds_suffix_gray", "§7 秒"));
            return UtilGui.button(Material.CLOCK, lang.ui("machine.fossil.status_running", "§e解析中"), lore);
        }

        // idle
        lore.add(lang.ui("machine.fossil.status_not", "§7状态：未解析"));
        lore.add(lang.ui("machine.fossil.click_start", "§7点击开始解析"));
        return UtilGui.button(Material.GRAY_DYE, lang.ui("machine.fossil.not_started", "§7未解析"), lore);
    }

    private static ItemStack pane() {
        ItemStack it = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta m = it.getItemMeta();
        if (m != null) {
            m.setDisplayName(" ");
            it.setItemMeta(m);
        }
        return it;
    }

    private static String fossilCn(LangManager lang, String id) {
        if (lang == null) {
            return switch (id) {
                case "helix_fossil" -> "螺旋化石";
                case "dome_fossil" -> "甲壳化石";
                case "old_amber" -> "秘密琥珀";
                default -> id;
            };
        }
        return switch (id) {
            case "helix_fossil" -> lang.ui("machine.fossil.fossil_helix_plain", "螺旋化石");
            case "dome_fossil" -> lang.ui("machine.fossil.fossil_dome_plain", "甲壳化石");
            case "old_amber" -> lang.ui("machine.fossil.fossil_amber_plain", "秘密琥珀");
            default -> id;
        };
    }

    // ========== helpers ==========
    private static void reopen(Player player, GuiHolder holder) {
        // refresh current inv
        if (player.getOpenInventory() == null) return;
        if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof GuiHolder h)) return;
        if (h.type != GuiType.FOSSIL_MACHINE) return;
        PokeDemoPlugin plugin = PokeDemoPlugin.INSTANCE;
        if (plugin == null) return;
        refresh(player.getOpenInventory().getTopInventory(), plugin, h);
        player.updateInventory();
    }

    private static void reopenMachine(Player player, String fossilKey) {
        PokeDemoPlugin plugin = PokeDemoPlugin.INSTANCE;
        if (plugin == null) return;
        Location loc = plugin.getFossilMachineManager().parseKey(fossilKey);
        if (loc != null) {
            plugin.getServer().getScheduler().runTask(plugin, () -> open(player, plugin, loc));
        }
    }

    private static int removeItems(Player player, Material mat, int amount) {
        int left = amount;
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack it = player.getInventory().getItem(i);
            if (it == null || it.getType() != mat) continue;
            int take = Math.min(left, it.getAmount());
            it.setAmount(it.getAmount() - take);
            if (it.getAmount() <= 0) player.getInventory().setItem(i, null);
            left -= take;
            if (left <= 0) break;
        }
        player.updateInventory();
        return amount - left;
    }

    private static boolean consumeOneCustomItem(Player player, String itemId) {
        PokeDemoPlugin plugin = PokeDemoPlugin.INSTANCE;
        if (plugin == null) return false;
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack it = player.getInventory().getItem(i);
            if (it == null || it.getType() == Material.AIR) continue;
            String id = plugin.getItems().getItemId(it);
            if (itemId.equals(id)) {
                int amt = it.getAmount();
                if (amt <= 1) player.getInventory().setItem(i, null);
                else it.setAmount(amt - 1);
                player.updateInventory();
                return true;
            }
        }
        return false;
    }

    private static boolean consumeOneBall(Player player, String ballId) {
        PokeDemoPlugin plugin = PokeDemoPlugin.INSTANCE;
        if (plugin == null) return false;
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack it = player.getInventory().getItem(i);
            if (it == null || it.getType() == Material.AIR) continue;
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
}