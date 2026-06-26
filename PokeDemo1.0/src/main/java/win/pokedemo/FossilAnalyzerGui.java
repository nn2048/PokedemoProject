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

/**
 * Fossil Analyzer UI (3-button logic):
 * Left: select suspicious stone
 * Right: consume water bucket, return empty bucket
 * Middle: start / running / claim fossil
 */
public final class FossilAnalyzerGui {

    private static final int SLOT_LEFT = 11;
    private static final int SLOT_MID = 13;
    private static final int SLOT_RIGHT = 15;
    private static final int SLOT_CLOSE = 26;

    private FossilAnalyzerGui() {}

    public static void open(Player player, PokeDemoPlugin plugin, Location loc) {
        LangManager lang = plugin.getLang();
        FossilAnalyzerManager am = plugin.getFossilAnalyzerManager();
        if (am == null) return;
        String key = am.keyOf(loc);
        GuiHolder holder = new GuiHolder(GuiType.FOSSIL_ANALYZER, player.getUniqueId());
        holder.fossilAnalyzerKey = key;

        Inventory inv = Bukkit.createInventory(holder, 27, lang.ui("machine.analyzer.title", "§6化石解析仪"));
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, pane());

        refresh(inv, plugin, holder);
        inv.setItem(SLOT_CLOSE, UtilGui.button(Material.BARRIER, lang.ui("common.close", lang.ui("common.close", "§c关闭")), List.of()));
        player.openInventory(inv);
    }

    private static void refresh(Inventory inv, PokeDemoPlugin plugin, GuiHolder holder) {
        FossilAnalyzerManager am = plugin.getFossilAnalyzerManager();
        FossilAnalyzerManager.State st = am.state(holder.fossilAnalyzerKey);
        inv.setItem(SLOT_LEFT, leftIcon(plugin, st));
        inv.setItem(SLOT_RIGHT, rightIcon(st));
        inv.setItem(SLOT_MID, midIcon(plugin, st));
    }

    public static void handleClick(Player player, GuiHolder holder, int rawSlot, boolean left, boolean right) {
        PokeDemoPlugin plugin = PokeDemoPlugin.INSTANCE;
        if (plugin == null) return;
        LangManager lang = plugin.getLang();
        FossilAnalyzerManager am = plugin.getFossilAnalyzerManager();
        if (am == null) return;

        if (rawSlot == SLOT_CLOSE) { player.closeInventory(); return; }

        FossilAnalyzerManager.State st = am.state(holder.fossilAnalyzerKey);
        if (st.running && rawSlot != SLOT_MID) {
            player.sendMessage(lang.ui("machine.analyzer.running_wait", "§7解析中，等待倒计时结束…"));
            return;
        }

        if (rawSlot == SLOT_LEFT) {
            if (st.readyFossilId != null) {
                player.sendMessage(lang.ui("machine.analyzer.result_wait", "§7已有结果待领取。"));
                return;
            }
            // consume 1 suspicious stone
            if (!consumeOneId(player, "suspicious_stone")) {
                player.sendMessage(lang.ui("machine.analyzer.no_stone", "§7你背包里没有可疑的石头。"));
                return;
            }
            am.putStone(holder.fossilAnalyzerKey);
            reopen(player, plugin, holder);
            return;
        }

        if (rawSlot == SLOT_RIGHT) {
            if (st.readyFossilId != null) {
                player.sendMessage(lang.ui("machine.analyzer.result_wait", "§7已有结果待领取。"));
                return;
            }
            // consume 1 water bucket, return empty bucket
            if (!consumeOneMaterial(player, Material.WATER_BUCKET)) {
                player.sendMessage(lang.ui("machine.analyzer.no_bucket", "§7你背包里没有水桶。"));
                return;
            }
            ItemStack empty = new ItemStack(Material.BUCKET, 1);
            // give or drop if full
            var leftover = player.getInventory().addItem(empty);
            if (!leftover.isEmpty()) {
                player.getWorld().dropItemNaturally(player.getLocation(), empty);
            }
            am.putWater(holder.fossilAnalyzerKey);
            reopen(player, plugin, holder);
            return;
        }

        if (rawSlot == SLOT_MID) {
            if (st.readyFossilId != null) {
                String fossilId = am.claimResult(holder.fossilAnalyzerKey);
                if (fossilId != null) {
                    ItemDef def = plugin.getItemRegistry().get(fossilId);
                    if (def != null) {
                        ItemStack it = plugin.getItems().createItem(def, plugin.getLang(), 1);
                        var lefts = player.getInventory().addItem(it);
                        if (!lefts.isEmpty()) {
                            player.getWorld().dropItemNaturally(player.getLocation(), it);
                        }
                    }
                }
                reopen(player, plugin, holder);
                return;
            }
            if (!st.running) {
                if (!st.hasStone) { player.sendMessage(lang.ui("machine.analyzer.need_stone", "§7请先放入可疑的石头。")); return; }
                if (!st.hasWater) { player.sendMessage(lang.ui("machine.analyzer.need_water", "§7请先注入水桶。")); return; }
                if (am.start(holder.fossilAnalyzerKey)) {
                    player.sendMessage(lang.ui("machine.analyzer.starting", "§6开始解析（60秒）…"));
                    reopen(player, plugin, holder);
                }
            }
        }
    }

    private static void reopen(Player player, PokeDemoPlugin plugin, GuiHolder holder) {
        if (player.getOpenInventory() == null) return;
        if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof GuiHolder h)) return;
        if (h.type != GuiType.FOSSIL_ANALYZER) return;
        refresh(player.getOpenInventory().getTopInventory(), plugin, h);
        player.updateInventory();
    }

    private static ItemStack leftIcon(PokeDemoPlugin plugin, FossilAnalyzerManager.State st) {
        LangManager lang = plugin.getLang();
        boolean ok = st.hasStone;
        ItemStack it;
        ItemDef def = plugin.getItemRegistry().get("suspicious_stone");
        if (def != null) it = plugin.getItems().createItem(def, plugin.getLang(), 1);
        else it = new ItemStack(Material.PAPER);
        ItemMeta m = it.getItemMeta();
        if (m != null) {
            m.setDisplayName(ok ? lang.ui("machine.analyzer.stone_in", "§a已放入：可疑的石头") : lang.ui("machine.analyzer.put_stone_btn", "§e放入可疑的石头"));
            List<String> lore = new ArrayList<>();
            lore.add(lang.ui("machine.analyzer.put_stone_lore", "§7点击消耗 1 个可疑的石头"));
            if (ok) lore.add(lang.ui("machine.analyzer.put_stone_note", "§8（放入后不可取回）"));
            m.setLore(lore);
            it.setItemMeta(m);
        }
        return it;
    }

    private static ItemStack rightIcon(FossilAnalyzerManager.State st) {
        LangManager lang = PokeDemoPlugin.INSTANCE == null ? null : PokeDemoPlugin.INSTANCE.getLang();
        ItemStack it = new ItemStack(Material.WATER_BUCKET);
        ItemMeta m = it.getItemMeta();
        if (m != null) {
            m.setDisplayName(st.hasWater ? lang.ui("machine.analyzer.water_in", "§a已注入水") : lang.ui("machine.analyzer.inject_water_btn", "§e注入水桶"));
            List<String> lore = new ArrayList<>();
            lore.add(lang.ui("machine.analyzer.inject_water_lore1", "§7点击消耗 1 桶水"));
            lore.add(lang.ui("machine.analyzer.inject_water_lore2", "§7并返还 1 个空桶"));
            m.setLore(lore);
            it.setItemMeta(m);
        }
        return it;
    }

    private static ItemStack midIcon(PokeDemoPlugin plugin, FossilAnalyzerManager.State st) {
        LangManager lang = plugin.getLang();
        if (st.readyFossilId != null) {
            ItemDef def = plugin.getItemRegistry().get(st.readyFossilId);
            ItemStack it = def == null ? new ItemStack(Material.PAPER) : plugin.getItems().createItem(def, plugin.getLang(), 1);
            ItemMeta m = it.getItemMeta();
            if (m != null) {
                m.setDisplayName(lang.ui("machine.analyzer.click_claim", "§a点击获取化石"));
                m.setLore(List.of(lang.ui("machine.analyzer.claim_note", "§7背包满了会掉落到地面")));
                it.setItemMeta(m);
            }
            return it;
        }
        if (st.running) {
            ItemStack it = new ItemStack(Material.CLOCK);
            ItemMeta m = it.getItemMeta();
            if (m != null) {
                long rem = Math.max(0, (st.endAtMs - System.currentTimeMillis()) / 1000L);
                m.setDisplayName(lang.ui("machine.analyzer.running", "§e解析中…"));
                m.setLore(List.of(lang.ui("machine.analyzer.countdown_prefix", lang.ui("machine.analyzer.countdown_prefix", "§7倒计时：§e")) + rem + lang.ui("machine.analyzer.seconds_suffix", lang.ui("common.seconds_gray", "§7 秒"))));
                it.setItemMeta(m);
            }
            return it;
        }
        ItemStack it = new ItemStack(Material.GRAY_DYE);
        ItemMeta m = it.getItemMeta();
        if (m != null) {
            m.setDisplayName(lang.ui("machine.analyzer.start_btn", "§7开始解析"));
            m.setLore(List.of(lang.ui("machine.analyzer.start_lore", "§7点击开始（60秒）")));
            it.setItemMeta(m);
        }
        return it;
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

    private static boolean consumeOneMaterial(Player p, Material mat) {
        for (int i = 0; i < p.getInventory().getSize(); i++) {
            ItemStack it = p.getInventory().getItem(i);
            if (it == null || it.getType() != mat) continue;
            if (it.getAmount() <= 1) p.getInventory().setItem(i, null);
            else it.setAmount(it.getAmount() - 1);
            p.updateInventory();
            return true;
        }
        return false;
    }

    private static boolean consumeOneId(Player p, String id) {
        PokeDemoPlugin plugin = PokeDemoPlugin.INSTANCE;
        if (plugin == null) return false;
        for (int i = 0; i < p.getInventory().getSize(); i++) {
            ItemStack it = p.getInventory().getItem(i);
            if (it == null || it.getType() == Material.AIR) continue;
            String itemId = plugin.getItems().getItemId(it);
            if (!id.equals(itemId)) continue;
            if (it.getAmount() <= 1) p.getInventory().setItem(i, null);
            else it.setAmount(it.getAmount() - 1);
            p.updateInventory();
            return true;
        }
        return false;
    }
}