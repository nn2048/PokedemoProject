
package win.pokedemo;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.ArrayList;
import java.util.List;

public final class UtilGui {
    private UtilGui() {}

    public static Component title(String legacy) {
        if (legacy == null) legacy = "";
        return LegacyComponentSerializer.legacySection().deserialize(legacy);
    }

    public static ItemStack button(Material mat, String name, List<String> lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        it.setItemMeta(meta);
        return it;
    }


private static ItemStack buildEvolveButton(Player viewer, PokemonInstance p) {
    EvolutionManager evo = PokeDemoPlugin.INSTANCE.getEvolutionManager();
    if (evo == null || p == null) {
        return button(Material.ANVIL, "§6进化", List.of("§7不可用"));
    }
    var list = evo.getAvailableEvolutions(viewer != null ? viewer.getUniqueId() : null, p);
    if (list.isEmpty()) {
        return button(Material.GRAY_DYE, "§8进化", List.of("§7暂无可进化形态", "§8需要满足进化条件"));
    }
    Evolution e = list.get(0);
    String toName = evo.prettyResultName(e.result());
    List<String> lore = new ArrayList<>();
    lore.add("§a可进化：§f" + toName);
    lore.add(e.minLevel() > 1 ? "§7条件：§f等级 ≥ " + e.minLevel() : "§7条件：§f已满足特殊进化条件");
    lore.add("§e点击进化");
    return button(Material.LIME_DYE, "§6进化", lore);
}



    public static void openPc(Player player, Storage storage, int page) {
        PokeDemoPlugin plugin = PokeDemoPlugin.INSTANCE;
        if (plugin != null && plugin.getBridgeSyncManager() != null && plugin.getBridgeSyncManager().canUseBridgePc(player)) {
            plugin.getBridgeSyncManager().openPcScreen(player, page);
            return;
        }
        PlayerProfile prof = storage.getProfile(player.getUniqueId());
        Integer pending = storage.getPendingPcRelease(player.getUniqueId());
        GuiHolder holder = new GuiHolder(GuiType.PC, player.getUniqueId());
        holder.page = Math.max(0, page);
        LangManager lang = PokeDemoPlugin.INSTANCE.getLang();
        String pcTitle = (lang == null) ? "§3电脑盒子" : lang.ui("gui.pc.title", "§3电脑盒子");
        String pcPageFmt = (lang == null) ? " §7(第{page}页)" : lang.ui("gui.pc.page", " §7(第{page}页)");
        String title = pcTitle + pcPageFmt.replace("{page}", String.valueOf(holder.page+1));
        Inventory inv = Bukkit.createInventory(holder, 54, UtilGui.title(title));
        int start = holder.page * 45;
        int end = Math.min(prof.pc.size(), start + 45);
        for (int i = start; i < end; i++) {
            PokemonInstance p = prof.pc.get(i);
            ItemStack icon = pokemonIcon(p);
            ItemMeta meta = icon.getItemMeta();
            String g = p.isEgg ? "" : switch (p.gender) {
                case "M" -> "§b♂";
                case "F" -> "§d♀";
                default -> "§7-";
            };
            String lockTag = (p.uiLocked ? (" §c[" + ((lang==null)? "锁定" : lang.ui("label.locked_tag", "锁定")) + "]") : "");
            meta.setDisplayName("§b" + p.displayName() + (g.isEmpty() ? "" : (" " + g)) + " §7Lv." + p.level + lockTag);
            List<String> lore = new ArrayList<>();
            Dex dex = plugin != null ? plugin.getDex() : null;
            Species sp = (dex != null) ? dex.getSpecies(p.speciesId) : null;
            int maxHp = (sp != null) ? p.maxHp(sp) : p.currentHp;
            if (p.isEgg) {
                lore.add("§7性别：§f?");
                lore.add("§7性格：§f?");
                lore.add("§7个体：§f??????");
                lore.add("§8(放入队伍后才能孵化)");
            } else {
                lore.add("§7生命值: " + p.currentHp + "/" + maxHp);
            }
            if (p.uiLocked) {
                String reason = (p.uiLockReason == null || p.uiLockReason.isBlank()) ? "" : ("§7(" + p.uiLockReason + ")");
                lore.add("§c已锁定，无法操作" + reason);
                lore.add(((lang != null) ? lang.ui("gui.common.unlockall_hint", "§7可用 §f/pokedemo unlockall §7一键解除所有锁定") : "§7可用 §f/pokedemo unlockall §7一键解除所有锁定"));
            }
            lore.add("§8索引 " + i);
            lore.add(((lang != null) ? lang.ui("gui.pc.left_click_take", "§e左键：取出到队伍") : "§e左键：取出到队伍"));
            if (p.isEgg) {
                lore.add("§c蛋：不可查看详情/不可放生");
                lore.add("§7(只能存入队伍或电脑盒子以孵化)");
            } else {
                lore.add(((lang != null) ? lang.ui("gui.common.shift_click_details", "§eShift+点击：查看详情") : "§eShift+点击：查看详情"));
                if (pending != null && pending == i) {
                    lore.add(((lang != null) ? lang.ui("gui.pc.right_click_release_confirm", "§c再次右键：确认放生") : "§c再次右键：确认放生"));
                    lore.add("§8(确认将于数秒后失效)");
                } else {
                    lore.add(((lang != null) ? lang.ui("gui.pc.right_click_release", "§c右键：放生（需确认）") : "§c右键：放生（需确认）"));
                }
            }
            appendSpeciesHintLore(lore, p);
            meta.setLore(lore);
            icon.setItemMeta(meta);
            inv.setItem(i - start, icon);
        }
        // nav row
        inv.setItem(45, button(Material.ARROW, "§e上一页", List.of()));
        inv.setItem(49, button(Material.PAPER, "§f第" + (holder.page+1) + "页", List.of("§7已存放: " + prof.pc.size(), "§7提示：右键放生需要二次确认")));
        inv.setItem(53, button(Material.ARROW, "§e下一页", List.of()));
        player.openInventory(inv);
    }

    public static void openParty(Player player, Storage storage, int selectedSlot) {
        PlayerProfile prof = storage.getProfile(player.getUniqueId());
        GuiHolder holder = new GuiHolder(GuiType.PARTY, player.getUniqueId());
        holder.selectedPartySlot = selectedSlot;
        LangManager lang = PokeDemoPlugin.INSTANCE.getLang();
        String title = (lang == null) ? "§2队伍 §7(6)" : lang.ui("gui.party.title_compact", "§2队伍 §7(6)");
        Inventory inv = Bukkit.createInventory(holder, 27, UtilGui.title(title));

        for (int i = 0; i < 6; i++) {
            if (i < prof.party.size()) {
                PokemonInstance p = prof.party.get(i);
                ItemStack icon = pokemonIcon(p);
                ItemMeta meta = icon.getItemMeta();
                String g = p.isEgg ? "" : switch (p.gender) {
                    case "M" -> "§b♂";
                    case "F" -> "§d♀";
                    default -> "§7-";
                };
                String lockTag = (p.uiLocked ? (" §c[" + ((lang==null)? "锁定" : lang.ui("label.locked_tag", "锁定")) + "]") : "");
                meta.setDisplayName("§a" + p.displayName() + (g.isEmpty() ? "" : (" " + g)) + " §7Lv." + p.level + lockTag);
                List<String> lore = new ArrayList<>();
                PokeDemoPlugin plugin = PokeDemoPlugin.INSTANCE;
            Dex dex = plugin != null ? plugin.getDex() : null;
            Species sp = (dex != null) ? dex.getSpecies(p.speciesId) : null;
            int maxHp = (sp != null) ? p.maxHp(sp) : p.currentHp;
            if (p.isEgg) {
                lore.add("§7性别：§f?");
                lore.add("§7性格：§f?");
                lore.add("§7个体：§f??????");
            } else {
                lore.add("§7生命值: " + p.currentHp + "/" + maxHp);
            }
                if (p.uiLocked) {
                    String reason = (p.uiLockReason == null || p.uiLockReason.isBlank()) ? "" : ("§7(" + p.uiLockReason + ")");
                    lore.add("§c已锁定，无法操作" + reason);
                }
                lore.add("§8槽位 " + (i + 1));
                if (p.isEgg) {
                    lore.add("§c蛋：不可查看详情/不可战斗");
                    lore.add("§7(只能存入队伍或电脑盒子以孵化)");
                }
                if (p.currentHp <= 0) {
                    lore.add("§c已昏厥");
                }
                if (selectedSlot == i) {
                    lore.add("§e已选择：点击另一个位置可交换顺序");
                } else {
                    lore.add((lang == null) ? "§7左键：选择/交换顺序" : lang.ui("gui.party.left_click_select_swap_gray", "§7左键：选择/交换顺序"));
                    lore.add((lang == null) ? "§b右键：存入电脑盒子" : lang.ui("gui.party.right_click_store_pc", "§b右键：存入电脑盒子"));
                    lore.add((lang == null) ? "§eShift+点击：查看精灵详情" : lang.ui("gui.common.shift_click_details_pokemon", "§eShift+点击：查看精灵详情"));
                }
                appendSpeciesHintLore(lore, p);
                meta.setLore(lore);
                icon.setItemMeta(meta);
                inv.setItem(i, icon);
            } else {
                inv.setItem(i, new ItemStack(Material.GRAY_STAINED_GLASS_PANE));
            }
        }
        // PC button removed: players should use placed PC block to access storage.
inv.setItem(26, button(Material.BARRIER, "§c关闭", List.of()));
        player.openInventory(inv);
    }

    /**
     * PvP ready screen: allows reordering party, then confirm to start battle.
     * This GUI intentionally disallows sending Pokémon to PC.
     */
    public static void openPvpReady(Player player, Storage storage, java.util.UUID opponentId, String opponentName, int selectedSlot, boolean confirmed) {
        PlayerProfile prof = storage.getProfile(player.getUniqueId());
        GuiHolder holder = new GuiHolder(GuiType.PVP_READY, player.getUniqueId());
        holder.pvpOpponentId = opponentId;
        holder.selectedPartySlot = selectedSlot;
        holder.pvpConfirmed = confirmed;

        PokeDemoPlugin plugin = PokeDemoPlugin.INSTANCE;
        LangManager lang = (plugin != null) ? plugin.getLang() : null;
        String title = (lang == null)
                ? ("§6PVP准备 §7vs §f" + (opponentName == null ? "?" : opponentName))
                : lang.uiFmt("gui.pvp_ready.title", "§6PVP准备 §7vs §f{opponent}", java.util.Map.of("opponent", (opponentName == null ? "?" : opponentName)));
        Inventory inv = Bukkit.createInventory(holder, 27, UtilGui.title(title));

        // Fill background
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, button(Material.GRAY_STAINED_GLASS_PANE, " ", List.of()));
        }

        // Party slots 0-5
        for (int i = 0; i < 6; i++) {
            if (i < prof.party.size()) {
                PokemonInstance p = prof.party.get(i);
                ItemStack icon = pokemonIcon(p);
                ItemMeta meta = icon.getItemMeta();

                String g = p.isEgg ? "" : switch (p.gender) {
                    case "M" -> "§b♂";
                    case "F" -> "§d♀";
                    default -> "§7-";
                };
                meta.setDisplayName("§a" + p.displayName() + (g.isEmpty() ? "" : (" " + g)) + " §7Lv." + p.level);

                List<String> lore = new ArrayList<>();
                Dex dex = plugin != null ? plugin.getDex() : null;
                Species sp = (dex != null) ? dex.getSpecies(p.speciesId) : null;
                int maxHp = (sp != null) ? p.maxHp(sp) : p.currentHp;
                if (p.isEgg) lore.add("§c蛋：不可战斗");
                else lore.add("§7生命值: " + p.currentHp + "/" + maxHp);
                if (!p.isEgg && p.currentHp <= 0) lore.add("§c已昏厥");

                lore.add("§8槽位 " + (i + 1));
                if (selectedSlot == i) lore.add("§e已选择：点击另一个位置可交换顺序");
                else lore.add((lang == null) ? "§7左键：选择/交换顺序" : lang.ui("gui.party.left_click_select_swap_gray", "§7左键：选择/交换顺序"));
                appendSpeciesHintLore(lore, p);
                meta.setLore(lore);
                icon.setItemMeta(meta);
                inv.setItem(i, icon);
            } else {
                inv.setItem(i, new ItemStack(Material.BLACK_STAINED_GLASS_PANE));
            }
        }

        // Close
        inv.setItem(17, button(Material.BARRIER,
                (lang==null)? "§c取消" : lang.ui("gui.common.cancel", "§c取消"),
                List.of((lang==null)? "§7关闭将取消本次PVP准备" : lang.ui("gui.pvp.cancel_lore", "§7关闭将取消本次PVP准备"))));

        // Confirm
        if (confirmed) {
            inv.setItem(26, button(Material.LIME_DYE,
                    (lang==null)? "§a已确认" : lang.ui("gui.pvp.confirmed", "§a已确认"),
                    List.of((lang==null)? "§7等待对方确认..." : lang.ui("gui.pvp.wait_other", "§7等待对方确认..."))));
        } else {
            inv.setItem(26, button(Material.EMERALD,
                    (lang==null)? "§a确认出战" : lang.ui("gui.pvp.confirm", "§a确认出战"),
                    List.of(
                            (lang==null)? "§7两人都确认后开始战斗" : lang.ui("gui.pvp.both_confirm", "§7两人都确认后开始战斗"),
                            (lang==null)? "§7默认先派出队伍第一只可战斗精灵" : lang.ui("gui.pvp.default_first", "§7默认先派出队伍第一只可战斗精灵")
                    )));
        }

        player.openInventory(inv);
    }

    /** Open a party-only selector UI for Trade Machine. Forces selection from PARTY (not PC). */
    public static void openPartyTradeSelect(Player player, Storage storage, String tradeKey, TradeManager.Side side) {
        LangManager lang = (PokeDemoPlugin.INSTANCE == null) ? null : PokeDemoPlugin.INSTANCE.getLang();
        PlayerProfile prof = storage.getProfile(player.getUniqueId());
        GuiHolder holder = new GuiHolder(GuiType.PARTY_TRADE_SELECT, player.getUniqueId());
        holder.tradeKey = tradeKey;
        holder.tradeSelectSide = side;
        Inventory inv = Bukkit.createInventory(holder, 27,
                UtilGui.title((lang==null)? "§d选择交换精灵" : lang.ui("gui.trade.pick_party_title", "§d选择交换精灵")));

        for (int i = 0; i < 27; i++) {
            inv.setItem(i, button(Material.GRAY_STAINED_GLASS_PANE, " ", List.of()));
        }

        for (int i = 0; i < 6; i++) {
            if (prof != null && i < prof.party.size()) {
                PokemonInstance p = prof.party.get(i);
                ItemStack icon = pokemonIcon(p);
                ItemMeta meta = icon.getItemMeta();
                String lockTag = (p.uiLocked ? (" §c[" + ((lang==null)? "锁定" : lang.ui("label.locked_tag", "锁定")) + "]") : "");
                meta.setDisplayName("§a" + p.displayName() + " §7Lv." + p.level + lockTag);
                java.util.ArrayList<String> lore = new java.util.ArrayList<>();
                if (p.uiLocked) {
                    String reason = (p.uiLockReason == null || p.uiLockReason.isBlank()) ? "" : ("§7(" + p.uiLockReason + ")");
                    lore.add("§c已锁定，无法选择" + reason);
                    lore.add("§8可用 /pokedemo unlockall 一键解除");
                } else {
                    lore.add((lang==null) ? "§7左键：选择该精灵用于交换" : lang.ui("gui.party.left_click_pick_for_swap", "§7左键：选择该精灵用于交换"));
                }
                lore.add("§8队伍槽位 " + (i + 1));
                appendSpeciesHintLore(lore, p);
                meta.setLore(lore);
                icon.setItemMeta(meta);
                inv.setItem(i, icon);
            } else {
                inv.setItem(i, new ItemStack(Material.GRAY_STAINED_GLASS_PANE));
            }
        }

        inv.setItem(26, button(Material.BARRIER, "§c返回/取消", List.of(
                "§7返回交换机界面",
                "§7退出交换机将自动取消占用"
        )));

        player.openInventory(inv);
    }

    /**
     * Starter selection GUI (shown when a new player first opens their inventory).
     */
    public static void openStarterSelect(Player player) {
        PokeDemoPlugin plugin = PokeDemoPlugin.INSTANCE;
        if (plugin == null) return;
        Storage storage = plugin.getStorage();
        Dex dex = plugin.getDex();
        if (storage == null || dex == null) return;

        PlayerProfile prof = storage.getProfile(player.getUniqueId());
        if (prof != null && prof.starterChosen) return;

        if (plugin.getBridgeSyncManager() != null) {
            if (plugin.getBridgeSyncManager().isBridgeClient(player.getUniqueId())) {
                plugin.getBridgeSyncManager().syncStarterState(player);
                return;
            }
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                try {
                    if (!player.isOnline()) return;
                    PlayerProfile profLater = storage.getProfile(player.getUniqueId());
                    if (profLater != null && profLater.starterChosen) return;
                    if (plugin.getBridgeSyncManager() != null && plugin.getBridgeSyncManager().isBridgeClient(player.getUniqueId())) {
                        plugin.getBridgeSyncManager().syncStarterState(player);
                        return;
                    }

                    GuiHolder holder = new GuiHolder(GuiType.STARTER_SELECT, player.getUniqueId());
                    Inventory inv = Bukkit.createInventory(holder, 27, UtilGui.title("§a选择初始伙伴"));

                    java.util.function.BiFunction<String, String, ItemStack> mk = (id, loreLine) -> {
                        Species sp = dex.getSpecies(id);
                        Material material = Material.SLIME_BALL;
                        String name = (plugin.getLang() != null) ? plugin.getLang().species(id, id) : id;
                        return button(material, "§e" + name, java.util.List.of("§7" + loreLine, "§a点击选择"));
                    };

                    inv.setItem(11, mk.apply("bulbasaur", "背上的种子会随着成长而发芽。"));
                    inv.setItem(13, mk.apply("charmander", "尾巴上的火焰代表着它的生命力。"));
                    inv.setItem(15, mk.apply("squirtle", "缩进龟壳后会喷射水流保护自己。"));
                    inv.setItem(22, button(Material.BARRIER, "§c暂不选择", java.util.List.of("§7你也可以稍后再打开背包选择")));
                    player.openInventory(inv);
                } catch (Throwable t) {
                    plugin.getLogger().warning("[PokeDemoBridge] Failed to open delayed starter GUI: " + t.getMessage());
                }
            }, 5L);
            return;
        }

        GuiHolder holder = new GuiHolder(GuiType.STARTER_SELECT, player.getUniqueId());
        Inventory inv = Bukkit.createInventory(holder, 27, UtilGui.title("§a选择初始伙伴"));

        java.util.function.BiFunction<String, String, ItemStack> mk = (id, loreLine) -> {
            Species sp = dex.getSpecies(id);
            Material material = Material.SLIME_BALL;
            String name = (plugin.getLang() != null) ? plugin.getLang().species(id, id) : id;
            return button(material, "§e" + name, java.util.List.of("§7" + loreLine, "§a点击选择"));
        };

        inv.setItem(11, mk.apply("bulbasaur", "背上的种子会随着成长而发芽。"));
        inv.setItem(13, mk.apply("charmander", "尾巴上的火焰代表着它的生命力。"));
        inv.setItem(15, mk.apply("squirtle", "缩进龟壳后会喷射水流保护自己。"));
        inv.setItem(22, button(Material.BARRIER, "§c暂不选择", java.util.List.of("§7你也可以稍后再打开背包选择")));
        player.openInventory(inv);
    }

    public static boolean chooseStarter(Player player, String pick) {
        if (player == null || pick == null || pick.isBlank()) return false;
        PokeDemoPlugin plugin = PokeDemoPlugin.INSTANCE;
        if (plugin == null) return false;
        Storage storage = plugin.getStorage();
        Dex dex = plugin.getDex();
        if (storage == null || dex == null) return false;

        PlayerProfile prof = storage.getProfile(player.getUniqueId());
        if (prof == null || prof.starterChosen) return false;

        Species sp = dex.getSpecies(pick);
        if (sp == null) {
            player.sendMessage("§c找不到该精灵：" + pick);
            return false;
        }

        PokemonInstance mon = PokemonInstance.createOwnedAllowHidden(sp, 10, dex);
        mon.originalTrainer = player.getUniqueId();
        mon.originalTrainerName = player.getName();

        prof.depositToPartyOrPc(mon);
        prof.starterChosen = true;
        prof.starterSpeciesId = pick;
        if (prof.dexCaught != null) prof.dexCaught.add(pick.toLowerCase(java.util.Locale.ROOT));
        storage.saveProfile(player.getUniqueId());

        LangManager lang = plugin.getLang();
        String display = (lang != null) ? lang.species(pick, pick) : pick;
        player.sendMessage("§a已选择初始伙伴：§f" + display + " §7(Lv.10)");
        player.closeInventory();
        return true;
    }

    /**
     * Open the Pokémon Phone main menu.
     * Options: Party / PC / Healer (permission-gated) / Pokédex.
     */
    public static void openPhoneMenu(Player player) {
        PokeDemoPlugin plugin = PokeDemoPlugin.INSTANCE;
        if (plugin == null) return;
        LangManager lang = plugin.getLang();
        GuiHolder holder = new GuiHolder(GuiType.PHONE_MENU, player.getUniqueId());
        String title = (lang != null) ? lang.ui("gui.phone.title", "§5精灵手机") : "§5精灵手机";
        Inventory inv = Bukkit.createInventory(holder, 27, UtilGui.title(title));
        inv.setItem(13, button(Material.BOOK,
                (lang != null) ? lang.ui("gui.phone.recipes", "§a合成配方") : "§a合成配方",
                List.of((lang != null) ? lang.ui("gui.phone.recipes.lore", "§7查看所有可合成物品的配方") : "§7查看所有可合成物品的配方")));
inv.setItem(11, button(Material.SLIME_BALL, (lang != null) ? lang.ui("gui.phone.party", "§a队伍") : "§a队伍",
                List.of((lang != null) ? lang.ui("gui.phone.party.lore", "§7查看与管理队伍") : "§7查看与管理队伍")));
        inv.setItem(12, button(Material.CHEST, (lang != null) ? lang.ui("gui.phone.pc", "§bPC") : "§bPC",
                List.of((lang != null) ? lang.ui("gui.phone.pc.lore", "§7打开电脑盒子") : "§7打开电脑盒子")));

        boolean canHeal = player.hasPermission("pokedemo.admin.heal");
        // Slot 14 is "Healer" entry. Normal players do NOT have /heal permission,
        // but should still be able to heal by being near a placed healer machine.
        // Admins additionally get a remote heal convenience.
        inv.setItem(14, button(canHeal ? Material.BEACON : Material.ANVIL,
                canHeal
                        ? ((lang != null) ? lang.ui("gui.phone.healer", "§d治疗") : "§d治疗(管理员)")
                        : ((lang != null) ? lang.ui("gui.phone.healer.machine", "§d治疗") : "§d治疗机"),
                canHeal
                        ? List.of((lang != null) ? lang.ui("gui.phone.healer.lore", "§7远程恢复队伍血量与PP") : "§7远程恢复队伍血量与PP")
                        : List.of((lang != null) ? lang.ui("gui.phone.healer.machine.lore", "§7随地治疗队伍") : "§7靠近治疗机后点击使用")
        ));

        // Pokédex icon: use our custom item (pokedex) so it shows the proper texture.
        ItemStack dexIcon;
        try {
            ItemRegistry reg = plugin.getItemRegistry();
            ItemFactory fac = plugin.getItems();
            ItemDef def = (reg == null) ? null : reg.get("pokedex");
            dexIcon = (def != null && fac != null)
                    ? fac.createItem(def, lang, 1)
                    : new ItemStack(Material.BOOK);
        } catch (Throwable t) {
            dexIcon = new ItemStack(Material.BOOK);
        }
        {
            ItemMeta meta = dexIcon.getItemMeta();
            if (meta != null) {
                meta.setDisplayName((lang != null) ? lang.ui("gui.phone.dex", "§c图鉴") : "§c图鉴");
                meta.setLore(List.of((lang != null) ? lang.ui("gui.phone.dex.lore", "§7查看已捕捉精灵信息") : "§7查看已捕捉精灵信息"));
                dexIcon.setItemMeta(meta);
            }
        }
        inv.setItem(15, dexIcon);

        inv.setItem(26, button(Material.BARRIER, (lang != null) ? lang.ui("gui.common.close", "§c关闭") : "§c关闭", List.of()));
        player.openInventory(inv);
    }

    /**
     * Open Pokédex list.
     * Uncaught entries show ???.
     */
    public static void openPokedexList(Player player, Storage storage, int page, boolean fromPhone) {
        PokeDemoPlugin plugin = PokeDemoPlugin.INSTANCE;
        if (plugin == null || storage == null) return;
        LangManager lang = plugin.getLang();
        PokedexData pd = plugin.getPokedexData();
        if (pd == null) pd = new PokedexData();

        PlayerProfile prof = storage.getProfile(player.getUniqueId());
        java.util.Set<String> caught = new java.util.HashSet<>();
        if (prof != null) {
            if (prof.dexCaught != null) caught.addAll(prof.dexCaught);
            // Backfill: if player owns mons, treat as caught
            for (PokemonInstance p : prof.party) if (p != null && p.speciesId != null) caught.add(p.speciesId.toLowerCase(java.util.Locale.ROOT));
            for (PokemonInstance p : prof.pc) if (p != null && p.speciesId != null) caught.add(p.speciesId.toLowerCase(java.util.Locale.ROOT));
        }

        GuiHolder holder = new GuiHolder(GuiType.POKEDEX_LIST, player.getUniqueId());
        holder.page = Math.max(0, page);
        holder.pokedexOpenedFromPhone = fromPhone;
        String title = (lang != null) ? lang.ui("gui.dex.title", "§c精灵图鉴") : "§c精灵图鉴";
        Inventory inv = Bukkit.createInventory(holder, 54, UtilGui.title(title + " §7(第" + (holder.page + 1) + "页)"));

        java.util.List<PokedexData.Entry> list = pd.ordered();
        int start = holder.page * 45;
        int end = Math.min(list.size(), start + 45);
        for (int i = start; i < end; i++) {
            PokedexData.Entry e = list.get(i);
            String sid = e.speciesId();
            int num = e.nationalDexNumber();
            boolean ok = caught.contains(sid.toLowerCase(java.util.Locale.ROOT));
            String numStr = String.format("#%03d", num);
            String name = ok ? ((lang != null) ? lang.species(sid, sid) : sid) : "???";
            String disp = "§f" + numStr + " §a" + name;
            String desc;
            if (ok) {
                String key = e.descKey();
                desc = (lang != null) ? lang.tr(key, "") : "";
                if (desc == null || desc.isBlank()) desc = "§7(暂无描述)";
            } else {
                desc = "???";
            }
            java.util.List<String> lore = new java.util.ArrayList<>(Util.wrapLore("§7" + desc, 26));
            if (ok) {
                PokedexSpawnIndex psi = plugin.getPokedexSpawnIndex();
                PokedexSpawnIndex.Summary ss = psi != null ? psi.get(sid) : null;
                java.util.List<String> brief = ss != null ? ss.briefLines(lang) : java.util.List.of();
                if (!brief.isEmpty()) {
                    lore.add(" ");
                    lore.add("§f" + ((lang != null) ? lang.ui("gui.dex.spawn.brief", "刷新概览") : "刷新概览"));
                    lore.addAll(brief);
                }
            }
            ItemStack icon = button(Material.PAPER, disp, lore);
            inv.setItem(i - start, icon);
        }

        inv.setItem(45, button(Material.ARROW, (lang != null) ? lang.ui("gui.common.prev", "§e上一页") : "§e上一页", List.of()));
        inv.setItem(49, button(Material.PAPER, "§f" + (holder.page + 1) + "/" + Math.max(1, (int)Math.ceil(list.size()/45.0)),
                List.of("§7已捕捉: " + caught.size(), "§7点击条目查看")));
        inv.setItem(53, button(Material.ARROW, (lang != null) ? lang.ui("gui.common.next", "§e下一页") : "§e下一页", List.of()));
        inv.setItem(52, button(Material.BARRIER, (lang != null) ? lang.ui("gui.common.back", "§e返回") : "§e返回", List.of(fromPhone ? "§7返回手机" : "§7关闭")));

        player.openInventory(inv);
    }

    public static void openPokedexEntry(Player player, Storage storage, String speciesId, int returnPage, boolean fromPhone) {
        if (player == null) return;
        PokeDemoPlugin plugin = PokeDemoPlugin.INSTANCE;
        if (plugin == null || storage == null) return;
        LangManager lang = plugin.getLang();
        PokedexData pd = plugin.getPokedexData();
        PokedexData.Entry e = pd != null ? pd.get(speciesId) : null;
        if (e == null) {
            player.sendMessage("§c图鉴条目缺失：" + speciesId);
            return;
        }
        PlayerProfile prof = storage.getProfile(player.getUniqueId());
        boolean caught = prof != null && prof.dexCaught != null && prof.dexCaught.contains(speciesId.toLowerCase(java.util.Locale.ROOT));
        // Backfill: own mons
        if (!caught && prof != null) {
            for (PokemonInstance p : prof.party) if (p != null && speciesId.equalsIgnoreCase(p.speciesId)) caught = true;
            for (PokemonInstance p : prof.pc) if (p != null && speciesId.equalsIgnoreCase(p.speciesId)) caught = true;
        }

        GuiHolder holder = new GuiHolder(GuiType.POKEDEX_ENTRY, player.getUniqueId());
        holder.pokedexSpeciesId = speciesId;
        holder.pokedexReturnPage = returnPage;
        holder.pokedexOpenedFromPhone = fromPhone;

        String numStr = String.format("#%03d", e.nationalDexNumber());
        String name = caught ? ((lang != null) ? lang.species(speciesId, speciesId) : speciesId) : "???";
        String title = (lang != null) ? lang.ui("gui.dex.entry.title", "§c图鉴") : "§c图鉴";
        Inventory inv = Bukkit.createInventory(holder, 27, UtilGui.title(title + " §7" + numStr));

        String desc = caught ? ((lang != null) ? lang.tr(e.descKey(), "") : "") : "???";
        if (desc == null || desc.isBlank()) desc = caught ? "(暂无描述)" : "???";

        java.util.List<String> lore = new java.util.ArrayList<>(Util.wrapLore("§7" + desc, 26));
        if (caught) {
            PokedexSpawnIndex psi = plugin.getPokedexSpawnIndex();
            PokedexSpawnIndex.Summary ss = psi != null ? psi.get(speciesId) : null;
            java.util.List<String> details = ss != null ? ss.detailLines(lang) : java.util.List.of();
            if (!details.isEmpty()) {
                lore.add(" ");
                lore.add("§f" + ((lang != null) ? lang.ui("gui.dex.spawn.detail", "刷新详情") : "刷新详情"));
                lore.addAll(details);
            }
        }

        inv.setItem(13, button(Material.BOOK, "§f" + numStr + " §a" + name, lore));
        inv.setItem(18, button(Material.ARROW, (lang != null) ? lang.ui("gui.common.back", "§e返回") : "§e返回", List.of(fromPhone ? "§7返回图鉴列表/手机" : "§7返回图鉴列表")));
        inv.setItem(26, button(Material.BARRIER, (lang != null) ? lang.ui("gui.common.close", "§c关闭") : "§c关闭", List.of()));

        player.openInventory(inv);
    }

    /**
     * Create an inventory icon that uses the same custom_model_data mapping as the in-world model.
     */

    private static void appendSpeciesHintLore(java.util.List<String> lore, PokemonInstance p) {
        if (lore == null || p == null) return;
        String id = p.speciesId == null ? "" : p.speciesId.trim();
        if (!id.isBlank()) lore.add("§0species:" + id.toLowerCase(java.util.Locale.ROOT));
        String display = p.displayName();
        if (display != null && !display.isBlank()) lore.add("§0name:" + display);
    }

    public static ItemStack pokemonIcon(PokemonInstance p) {
        if (p == null) return new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        if (p.isEgg) {
            // Progress icon stages: Egg -> Turtle Egg -> Chicken Spawn Egg
            int total = Math.max(1, p.eggStepsTotal);
            int rem = Math.max(0, p.eggStepsRemaining);
            double done = 1.0 - (rem / (double) total);
            Material mat;
            if (done < (1.0 / 3.0)) mat = Material.EGG;
            else if (done < (2.0 / 3.0)) mat = Material.TURTLE_EGG;
            else mat = Material.CHICKEN_SPAWN_EGG;
            return new ItemStack(mat);
        }
        PokeDemoPlugin plugin = PokeDemoPlugin.INSTANCE;
        Integer cmd = plugin != null ? plugin.getModelCmd(p.speciesId) : null;
        ItemStack it = new ItemStack(Material.CARVED_PUMPKIN);
        ItemMeta meta = it.getItemMeta();
        if (meta != null && cmd != null) {
            meta.setCustomModelData(cmd);
            it.setItemMeta(meta);
        }
        return it;
    }

    /** Convert type id to Chinese label for UI. */
    private static String typeToCn(String id) {
        if (id == null) return "?";
        return switch (id.toLowerCase()) {
            case "normal" -> "一般";
            case "fire" -> "火";
            case "water" -> "水";
            case "electric" -> "电";
            case "grass" -> "草";
            case "ice" -> "冰";
            case "fighting" -> "格斗";
            case "poison" -> "毒";
            case "ground" -> "地面";
            case "flying" -> "飞行";
            case "psychic" -> "超能";
            case "bug" -> "虫";
            case "rock" -> "岩石";
            case "ghost" -> "幽灵";
            case "dragon" -> "龙";
            case "dark" -> "恶";
            case "steel" -> "钢";
            case "fairy" -> "妖精";
            default -> id;
        };
    }

    /**
     * Open a Cobblemon-like summary GUI for a Pokemon.
     *
     * @param fromParty whether the pokemon is referenced from party (slot) or pc (index)
     */
    public static void openSummary(Player player, Storage storage, PokemonInstance p, boolean fromParty, int partySlot, int pcIndex, int tab) {
        if (p == null) return;
        GuiHolder holder = new GuiHolder(GuiType.SUMMARY, player.getUniqueId());
        holder.summaryPokemonUuid = p.uuid;
        holder.summaryFromParty = fromParty;
        holder.summaryPartySlot = partySlot;
        holder.summaryPcIndex = pcIndex;
        holder.summaryTab = Util.clamp(tab, 0, 3);
        if (!fromParty && pcIndex >= 0) {
            holder.page = pcIndex / 45;
        }

        LangManager lang = PokeDemoPlugin.INSTANCE.getLang();
        String title = (lang == null)
                ? ("§8精灵详情 §7- §a" + p.displayName())
                : (lang.ui("gui.summary.title", "§8精灵详情") + " §7- §a" + p.displayName());
        Inventory inv = Bukkit.createInventory(holder, 54, UtilGui.title(title));

        // top tabs
        String tabOverview = lang == null ? "概览" : lang.ui("gui.summary.tab.overview", "概览");
        String tabMoves = lang == null ? "技能" : lang.ui("gui.summary.tab.moves", "技能");
        String tabStats = lang == null ? "能力" : lang.ui("gui.summary.tab.stats", "能力");
        String tabIvEv = lang == null ? "个体/努力" : lang.ui("gui.summary.tab.iv_ev", "个体/努力");
        inv.setItem(0, button(Material.BOOK, holder.summaryTab == 0 ? "§a" + tabOverview : "§f" + tabOverview, List.of(lang == null ? "§7查看基本信息" : lang.ui("gui.summary.tab.overview.lore", "§7查看基本信息"))));
        inv.setItem(1, button(Material.WRITABLE_BOOK, holder.summaryTab == 1 ? "§a" + tabMoves : "§f" + tabMoves, List.of(lang == null ? "§7查看技能与PP" : lang.ui("gui.summary.tab.moves.lore", "§7查看技能与PP"))));
        inv.setItem(2, button(Material.IRON_SWORD, holder.summaryTab == 2 ? "§a" + tabStats : "§f" + tabStats, List.of(lang == null ? "§7查看能力值" : lang.ui("gui.summary.tab.stats.lore", "§7查看能力值"))));
        inv.setItem(3, button(Material.NETHER_STAR, holder.summaryTab == 3 ? "§a" + tabIvEv : "§f" + tabIvEv, List.of(lang == null ? "§7查看IV/EV" : lang.ui("gui.summary.tab.iv_ev.lore", "§7查看IV/EV"))));

        // back / actions
        inv.setItem(45, button(Material.ARROW,
                (lang == null ? "§e返回" : lang.ui("gui.common.back", "§e返回")),
                List.of(holder.summaryFromParty
                        ? (lang == null ? "§7返回队伍" : lang.ui("gui.summary.back.party", "§7返回队伍"))
                        : (lang == null ? "§7返回电脑盒子" : lang.ui("gui.summary.back.pc", "§7返回电脑盒子"))
                )));
        inv.setItem(53, buildEvolveButton(player, p));

        renderSummaryTab(inv, storage, p, holder.summaryTab);
        player.openInventory(inv);
    }

    private static void renderSummaryTab(Inventory inv, Storage storage, PokemonInstance p, int tab) {
        LangManager lang = PokeDemoPlugin.INSTANCE.getLang();
        // Clear content area (slots 9-44)
        for (int i = 9; i <= 44; i++) inv.setItem(i, null);

        // Common header item
        ItemStack head = p.isEgg ? new ItemStack(Material.EGG) : new ItemStack(Material.WOLF_SPAWN_EGG);
        ItemMeta hm = head.getItemMeta();
        String g = p.isEgg ? "" : switch (p.gender) {
            case "M" -> "§b♂";
            case "F" -> "§d♀";
            default -> "§7-";
        };
        String lockTag = (p.uiLocked ? (" §c[" + ((lang==null)? "锁定" : lang.ui("label.locked_tag", "锁定")) + "]") : "");
        hm.setDisplayName("§b" + p.displayName() + (g.isEmpty() ? "" : (" " + g)) + " §7Lv." + p.level + lockTag);
        List<String> hl = new ArrayList<>();
        String labelUuid = lang == null ? "UUID" : lang.ui("label.uuid", "UUID");
        hl.add("§7" + labelUuid + ": §8" + p.uuid.toString().substring(0, 8));
        if (p.isEgg) {
            hl.add("§7" + ((lang==null)? "性别" : lang.ui("label.gender", "性别")) + "：§f?");
            hl.add("§7" + ((lang==null)? "性格" : lang.ui("label.nature", "性格")) + "：§f?");
            hl.add("§7" + ((lang==null)? "个体" : lang.ui("label.iv", "个体")) + "：§f??????");
            hl.add((lang==null) ? "§8(蛋只有放在队伍中才会孵化)" : lang.ui("summary.egg.hint", "§8(蛋只有放在队伍中才会孵化)"));
        } else {
            String labelSpecies = lang == null ? "物种" : lang.ui("label.species", "物种");
            String labelNature = lang == null ? "性格" : lang.ui("label.nature", "性格");
            hl.add("§7" + labelSpecies + ": §f" + (lang == null ? (p.speciesName != null ? p.speciesName : p.speciesId) : lang.species(p.speciesId, p.speciesName)));

            // Types line (属性)
            try {
                Species ss = PokeDemoPlugin.INSTANCE.getDex().getSpecies(p.speciesId);
                if (ss != null && ss.types() != null && !ss.types().isEmpty()) {
                    String labelTypes = lang == null ? "属性" : lang.ui("label.types", "属性");
                    java.util.List<String> tcn = new java.util.ArrayList<>();
                    for (String tid : ss.types()) {
                        if (lang != null) tcn.add(lang.typeName(tid));
                        else tcn.add(typeToCn(tid));
                    }
                    hl.add("§7" + labelTypes + ": §f" + String.join("/", tcn));
                }
            } catch (Throwable ignored) {}

            Nature n = Nature.fromId(p.nature);
            String natureName = (lang == null) ? n.zhName : lang.natureName(n);
            hl.add("§7" + labelNature + ": §f" + natureName + " §8(" + n.id() + ")");
        }
        if (p.uiLocked) {
            String reason = (p.uiLockReason == null || p.uiLockReason.isBlank()) ? "" : ("§7(" + p.uiLockReason + ")");
            hl.add((lang == null) ? ("§c已锁定，无法操作" + reason) : (lang.ui("gui.common.locked", "§c已锁定，无法操作") + reason));
        }
        hm.setLore(hl);
        head.setItemMeta(hm);
        inv.setItem(4, head);

        if (tab == 0) {
            Species s = PokeDemoPlugin.INSTANCE.getDex().getSpecies(p.speciesId);
            // Basic info cards
            String labelStatus = lang == null ? "状态" : lang.ui("label.status", "状态");
            String statusName = lang == null ? p.status : lang.statusName(p.status);
            inv.setItem(19, button(Material.RED_DYE,
                    (lang == null ? "§cHP" : lang.ui("label.hp", "§cHP")),
                    List.of(
                            "§f" + p.currentHp + "§7/§f" + p.maxHp(s),
                            "§7" + labelStatus + ": §f" + statusName
                    )));
            inv.setItem(21, button(Material.NAME_TAG,
                    (lang == null ? "§f昵称" : lang.ui("label.nickname", "§f昵称")),
                    List.of(
                            "§7" + (p.nickname == null ? (lang == null ? "(无)" : lang.ui("common.none", "(无)")) : p.nickname),
                            (lang == null ? "§8左键改名，Shift+左键清除昵称" : lang.ui("summary.nickname.hint", "§8左键改名，Shift+左键清除昵称"))
                    )));
            inv.setItem(23, button(Material.PAPER,
                    (lang == null ? "§f初训家" : lang.ui("label.ot", "§f初训家")),
                    java.util.List.of(
                            "§7" + ((p.originalTrainerName == null || p.originalTrainerName.isBlank()) ? (lang == null ? "(未记录)" : lang.ui("common.none", "(无)")) : p.originalTrainerName),
                            "§8UUID: " + (p.originalTrainer == null ? "-" : p.originalTrainer.toString().substring(0, 8))
                    )));
            inv.setItem(41, button(Material.HONEY_BOTTLE,
                    (lang == null ? "§e亲密度" : lang.ui("label.friendship", "§e亲密度")),
                    java.util.List.of(
                            "§7当前: §f" + p.friendshipValue() + "§7/§f255",
                            "§8高亲密度可影响 Return/Frustration 与部分进化"
                    )));
            
            // Held item card (click to set/remove)
            {
                PokeDemoPlugin pl = PokeDemoPlugin.INSTANCE;
                ItemStack icon = null;
                String title = (lang == null ? "§f携带物品" : lang.ui("label.held_item", "§f携带物品"));
                java.util.List<String> lore2 = new java.util.ArrayList<>();

                if (p.heldItemId != null && !p.heldItemId.isBlank() && pl != null) {
                    ItemRegistry reg = pl.getItemRegistry();
                    ItemFactory fac = pl.getItems();
                    ItemDef hd = reg == null ? null : reg.get(p.heldItemId);
                    if (hd != null && fac != null) {
                        icon = fac.createItem(hd, lang, 1);
                        title = "§f" + (lang == null ? hd.id : lang.item(hd.id, hd.id));
                        lore2.add(lang == null ? "§7已携带" : lang.ui("summary.held_item.has", "§7已携带"));
                        lore2.add(lang == null ? "§8左键取下" : lang.ui("summary.held_item.remove", "§8左键取下"));
                        lore2.add(lang == null ? "§8先左键背包里的道具，再点此格可替换" : lang.ui("summary.held_item.replace", "§8先左键背包里的道具，再点此格可替换"));
                    }
                }

                if (icon == null) {
                    icon = button(Material.CHEST, title, java.util.List.of(
                            (lang == null ? "§7(空)" : lang.ui("common.empty", "§7(空)")),
                            (lang == null ? "§8先左键背包里的道具，再点此格即可携带" : lang.ui("summary.held_item.set", "§8先左键背包里的道具，再点此格即可携带")),
                            (lang == null ? "§8仅支持本插件道具" : lang.ui("summary.held_item.only_pokedemo", "§8仅支持本插件道具"))
                    ));
                } else {
                    // override generic lore (like "右键使用") with held-item instructions
                    org.bukkit.inventory.meta.ItemMeta im = icon.getItemMeta();
                    im.setDisplayName(title);
                    im.setLore(lore2);
                    icon.setItemMeta(im);
                }

                inv.setItem(25, icon);
            }


            if (s != null) {
                long curMin = ExpCurve.totalExpAtLevel(s.expGroup(), p.level);
                long nextMin = (p.level >= 100) ? curMin : ExpCurve.totalExpAtLevel(s.expGroup(), p.level + 1);
                long curTotal = Math.max(p.totalExp, curMin);
                long toNext = (p.level >= 100) ? 0 : Math.max(0, nextMin - curTotal);
                inv.setItem(31, button(Material.EXPERIENCE_BOTTLE, "§a经验", List.of(
                        "§7经验组: §f" + s.expGroup(),
                        "§7总经验: §f" + curTotal,
                        p.level >= 100 ? "§e已达到最高等级" : ("§7距离下一级: §f" + toNext)
                )));
            } else {
                inv.setItem(31, button(Material.EXPERIENCE_BOTTLE, "§a经验", List.of("§7(未知物种)", "§8无法计算经验")));
            }
            // Ability (特性)
            String abId = (p == null) ? null : p.abilityId;
            String abName = AbilityEffects.displayName(abId);
            String abDesc = AbilityEffects.shortDescription(abId);
            java.util.List<String> lore = new java.util.ArrayList<>();
            if (abId == null || abId.isBlank()) {
                lore.add("§7无");
                lore.add("§8(该精灵尚未分配特性)");
            } else {
                lore.add("§f" + abName + " §8(" + abId + ")");
                if (abDesc != null && !abDesc.isBlank()) lore.add("§7" + abDesc);
                lore.add("§8(梦特会按概率刷出)");
            }
            inv.setItem(32, button(Material.ENDER_EYE, "§d特性", lore));
        } else if (tab == 1) {
            // Moves
            Dex dex = PokeDemoPlugin.INSTANCE.getDex();
            for (int i = 0; i < 4; i++) {
                int slot = 19 + i * 2;
                MoveSlot ms = (p.moves != null && p.moves.size() > i) ? p.moves.get(i) : null;
                if (ms == null || ms.moveId == null) {
                    inv.setItem(slot, button(Material.BARRIER, "§7(空技能)", List.of("§8以后可学习技能")));
                    continue;
                }
                Move m = dex.getMove(ms.moveId);
                if (m == null) {
                    inv.setItem(slot, button(Material.BARRIER, "§7(未知技能)", List.of("§8技能数据缺失")));
                    continue;
                }
                List<String> lore = buildMoveLore(lang, m, ms.pp);
                String moveName = (lang == null ? m.name() : lang.move(m.id(), m.name()));
                inv.setItem(slot, button(Material.GREEN_DYE, "§a" + moveName, lore));
            }
        } else if (tab == 2) {
            Species s = PokeDemoPlugin.INSTANCE.getDex().getSpecies(p.speciesId);
            int hp = p.maxHp(s);
            int atk = p.calcStat(s, "atk", p.ivAtk, p.evAtk, false);
            int def = p.calcStat(s, "def", p.ivDef, p.evDef, false);
            int spa = p.calcStat(s, "spa", p.ivSpa, p.evSpa, false);
            int spd = p.calcStat(s, "spd", p.ivSpd, p.evSpd, false);
            int spe = p.calcStat(s, "spe", p.ivSpe, p.evSpe, false);
            inv.setItem(19, button(Material.REDSTONE, "§cHP", List.of("§f" + hp)));
            inv.setItem(21, button(Material.IRON_SWORD, "§6攻击", List.of("§f" + atk)));
            inv.setItem(23, button(Material.SHIELD, "§b防御", List.of("§f" + def)));
            inv.setItem(25, button(Material.BLAZE_POWDER, "§d特攻", List.of("§f" + spa)));
            inv.setItem(30, button(Material.PRISMARINE_SHARD, "§9特防", List.of("§f" + spd)));
            inv.setItem(32, button(Material.SUGAR, "§a速度", List.of("§f" + spe)));
                        inv.setItem(40, button(Material.PAPER, "§7说明", List.of("§8能力值已计入 性格/IV/EV", "§8EV 上限: 252/单项, 510/总计")));
        } else if (tab == 3) {
            // IV/EV
            inv.setItem(19, button(Material.NETHERITE_SCRAP, "§fIV(个体)", List.of(
                    "§7HP: §f" + p.ivHp + "§7/31",
                    "§7Atk: §f" + p.ivAtk + "§7/31",
                    "§7Def: §f" + p.ivDef + "§7/31",
                    "§7SpA: §f" + p.ivSpa + "§7/31",
                    "§7SpD: §f" + p.ivSpd + "§7/31",
                    "§7Spe: §f" + p.ivSpe + "§7/31",
                    "§8(未来可加入瓶盖/极限特训)")));

            inv.setItem(25, button(Material.FEATHER, "§fEV(努力)", List.of(
                    "§7HP: §f" + p.evHp,
                    "§7Atk: §f" + p.evAtk,
                    "§7Def: §f" + p.evDef,
                    "§7SpA: §f" + p.evSpa,
                    "§7SpD: §f" + p.evSpd,
                    "§7Spe: §f" + p.evSpe)));

            inv.setItem(31, button(Material.GOLDEN_CARROT, "§e重置努力值", List.of("§7(接口预留)", "§8以后会做成道具/按钮")));
        }

    }

    private static void appendMoveDescriptionLore(java.util.List<String> lore, LangManager lang, Move m) {
        if (lore == null || m == null) return;
        String desc = MoveDescriptionCatalog.descriptionFor(lang, m.id());
        if (desc == null || desc.isBlank()) {
            java.util.List<String> extra = extraMoveLore(m);
            if (!extra.isEmpty()) {
                lore.add("");
                lore.addAll(extra);
            }
            return;
        }
        lore.add("");
        String header = (lang == null) ? "§6招式效果" : lang.ui("label.move_effect", "§6招式效果");
        lore.add(header);
        for (String line : Util.wrapLore("§7" + desc, 28)) lore.add(line);
        java.util.List<String> extra = extraMoveLore(m);
        if (!extra.isEmpty()) lore.addAll(extra);
    }

    private static java.util.List<String> buildMoveLore(LangManager lang, Move m, Integer currentPp) {
        java.util.List<String> lore = new java.util.ArrayList<>();
        if (m == null) return lore;
        String labelType = lang == null ? "属性" : lang.ui("label.type", "属性");
        String labelCategory = lang == null ? "分类" : lang.ui("label.category", "分类");
        String labelPower = lang == null ? "威力" : lang.ui("label.power", "威力");
        String labelAccuracy = lang == null ? "命中" : lang.ui("label.accuracy", "命中");
        String typeName = lang == null ? m.type() : lang.typeName(m.type());
        String catName = lang == null ? m.category() : lang.categoryName(m.category());
        lore.add("§7" + labelType + ": §f" + typeName);
        lore.add("§7" + labelCategory + ": §f" + catName);
        lore.add("§7" + labelPower + ": §f" + prettyMovePower(m));
        double accVal = m.accuracy();
        String accText;
        if (accVal <= 0) {
            accText = "-";
        } else {
            double pct = accVal <= 1.0 ? accVal * 100.0 : accVal;
            if (Math.abs(pct - Math.rint(pct)) < 1.0e-9) {
                accText = ((int) Math.rint(pct)) + "%";
            } else {
                accText = String.format(java.util.Locale.US, "%.1f%%", pct);
            }
        }
        lore.add("§7" + labelAccuracy + ": §f" + accText);
        lore.add("§7PP: §f" + (currentPp == null ? m.pp() : currentPp) + "§7/§f" + m.pp());
        appendMoveDescriptionLore(lore, lang, m);
        return lore;
    }

    /**
     * Some moves have base power 0 in data because they deal fixed damage (e.g. Dragon Rage).
     * For UI readability, show their well-known fixed power value.
     */
    private static String prettyMovePower(Move m) {
        if (m == null) return "-";
        int p = m.power();
        if (p > 0 && !isVariablePowerMove(m)) return String.valueOf(p);
        String id = m.id() == null ? "" : m.id().toLowerCase();
        if (id.equals("dragonrage") || id.equals("dragon_rage")) return "固定40";
        if (id.equals("sonicboom") || id.equals("sonic_boom")) return "固定20";
        if (id.equals("nightshade") || id.equals("night_shade") || id.equals("seismictoss") || id.equals("seismic_toss")) return "固定(等级)";
        if (isVariablePowerMove(m)) return "变化";
        return "-";
    }

    private static boolean isVariablePowerMove(Move m) {
        if (m == null) return false;
        String id = m.id() == null ? "" : m.id().toLowerCase();
        return java.util.Set.of(
                "counter", "mirrorcoat", "mirror_coat", "metalburst", "metal_burst", "comeuppance",
                "bide", "endeavor", "eruption", "waterspout", "water_spout", "flail", "reversal",
                "electroball", "electro_ball", "gyroball", "gyro_ball", "grassknot", "grass_knot",
                "lowkick", "low_kick", "heatcrash", "heat_crash", "heavyslam", "heavy_slam",
                "return", "frustration", "wringout", "wring_out", "crushgrip", "crush_grip",
                "storedpower", "stored_power", "powertrip", "power_trip", "foulplay", "foul_play",
                "payback", "assurance", "avalanche", "revenge", "retaliate", "facade",
                "hex", "venoshock", "wakeupslap", "wake_up_slap", "smellingsalts", "smelling_salts",
                "acrobatics", "fling", "present", "magnitude", "beatup", "beat_up"
        ).contains(id);
    }

    private static java.util.List<String> extraMoveLore(Move m) {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (m == null) return out;
        String id = m.id() == null ? "" : m.id().toLowerCase();
        if (id.equals("counter")) out.add("§8将本回合最后一次受到的物理伤害×2返还给对手");
        else if (id.equals("mirrorcoat") || id.equals("mirror_coat")) out.add("§8将本回合最后一次受到的特殊伤害×2返还给对手");
        else if (id.equals("metalburst") || id.equals("metal_burst")) out.add("§8将本回合最后一次受到的直接伤害以更高倍率返还给对手");
        else if (id.equals("comeuppance")) out.add("§8将本回合最后一次受到的直接伤害返还给对手");
        for (java.util.Map<String, Object> fx : m.effectsSafe()) {
            String fxId = String.valueOf(fx.getOrDefault("id", "")).toLowerCase();
            if ("throat_chop".equals(fxId)) {
                Object turns = fx.get("turns");
                out.add("§8命中后，目标" + (turns == null ? "若干" : String.valueOf(turns)) + "回合内不能使用声音招式");
            } else if ("fixed_damage".equals(fxId)) {
                if (fx.containsKey("value")) out.add("§8造成固定 " + fx.get("value") + " 点伤害");
                else if (fx.containsKey("level")) out.add("§8造成与使用者等级相同的固定伤害");
            } else if ("ohko".equals(fxId)) {
                out.add("§8命中时一击必杀");
            } else if ("selfdestruct".equals(fxId)) {
                out.add("§8使用后自己会倒下");
            }
        }
        return out;
    }

    /**
     * Open move learn GUI for the first pending move of this pokemon.
     */
    public static void openMoveLearn(Player player, Storage storage, java.util.UUID pokemonUuid) {
        openMoveLearn(player, storage, pokemonUuid, false);
    }

    public static void openMoveLearn(Player player, Storage storage, java.util.UUID pokemonUuid, boolean fromItem) {
        if (player == null || storage == null || pokemonUuid == null) return;
        PlayerProfile prof = storage.getProfile(player.getUniqueId());
        PokemonInstance p = prof.findByUuid(pokemonUuid);
        if (p == null || p.pendingMoveLearns == null || p.pendingMoveLearns.isEmpty()) {
            PokeDemoPlugin plugin = PokeDemoPlugin.INSTANCE;
            LangManager lang = (plugin == null) ? null : plugin.getLang();
            player.sendMessage((lang==null)? "§7没有需要学习的新技能。" : lang.ui("gui.move_learn.none", "§7没有需要学习的新技能。"));
            return;
        }

        String newMoveId = p.pendingMoveLearns.get(0);
        PokeDemoPlugin plugin = PokeDemoPlugin.INSTANCE;
        Dex dex = plugin.getDex();
        Move newMove = dex.getMoveOrPlaceholder(newMoveId);

        GuiHolder holder = new GuiHolder(GuiType.MOVE_LEARN, player.getUniqueId());
        holder.moveLearnPokemonUuid = pokemonUuid;
        holder.moveLearnFromItem = fromItem;

        Inventory inv = Bukkit.createInventory(holder, 27, UtilGui.title(plugin.getLang().ui("gui.move_learn.title", "§6学习新技能")));

        // center: new move info
        java.util.List<String> newMoveLore = new java.util.ArrayList<>(buildMoveLore(plugin.getLang(), newMove, null));
        newMoveLore.add(0, "");
        newMoveLore.add(0, "§a点击下方选择遗忘的技能");
        newMoveLore.add(0, "§7" + p.displayName() + " 想要学习这招式");
        newMoveLore.add("§c或点击放弃学习");
        inv.setItem(13, button(Material.ENCHANTED_BOOK,
                "§e" + plugin.getLang().move(newMove.id(), null),
                newMoveLore));

        // 4 current moves to forget
        int[] slots = new int[]{10, 11, 15, 16};
        for (int i = 0; i < 4; i++) {
            String mid = (p.moves.size() > i && p.moves.get(i) != null) ? p.moves.get(i).moveId : "tackle";
            Move m = dex.getMoveOrPlaceholder(mid);
            java.util.List<String> forgetLore = new java.util.ArrayList<>();
            forgetLore.add("§7将遗忘该技能并学习：");
            forgetLore.add("§e" + plugin.getLang().move(newMove.id(), null));
            forgetLore.add("§e点击选择");
            forgetLore.add("");
            forgetLore.addAll(buildMoveLore(plugin.getLang(), m, (p.moves.size() > i && p.moves.get(i) != null) ? p.moves.get(i).pp : null));
            inv.setItem(slots[i], button(Material.PAPER,
                    "§f遗忘：§c" + plugin.getLang().move(m.id(), null),
                    forgetLore));
        }

        // cancel
        inv.setItem(22, button(Material.BARRIER, "§7放弃学习", List.of("§c不学习新技能")));

        player.openInventory(inv);
    }
}
