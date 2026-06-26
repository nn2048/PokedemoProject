package win.pokedemo;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;

/**
 * Handles all PokeDemo GUIs.
 */
public class GuiListener implements Listener {
    private final Storage storage;
    private final BattleManager battles;
    private final EvolutionManager evolutions;

    /**
     * Held-item selection flow for players who cannot move cursor items while GUI is open.
     * Step1: left-click a PokeDemo item in the player's inventory area to "select" it.
     * Step2: left-click the held-item slot (summary tab0 slot25) to equip (consumes 1).
     */
    private final java.util.Map<java.util.UUID, PendingHeldPick> pendingHeldPick = new java.util.concurrent.ConcurrentHashMap<>();


    private final java.util.Map<java.util.UUID, PendingNicknameRename> pendingNicknameRename = new java.util.concurrent.ConcurrentHashMap<>();

    private static final class PendingNicknameRename {
        final java.util.UUID pokemonUuid;
        final boolean fromParty;
        final int partySlot;
        final int pcIndex;
        final int tab;
        final long expireAt;
        PendingNicknameRename(java.util.UUID pokemonUuid, boolean fromParty, int partySlot, int pcIndex, int tab, long expireAt) {
            this.pokemonUuid = pokemonUuid;
            this.fromParty = fromParty;
            this.partySlot = partySlot;
            this.pcIndex = pcIndex;
            this.tab = tab;
            this.expireAt = expireAt;
        }
        boolean expired() { return System.currentTimeMillis() > expireAt; }
    }

    private static final class PendingHeldPick {
        final String itemId;
        final int invSlot; // slot index in player's inventory
        final long expireAt;
        PendingHeldPick(String itemId, int invSlot, long expireAt) {
            this.itemId = itemId;
            this.invSlot = invSlot;
            this.expireAt = expireAt;
        }
        boolean expired() { return System.currentTimeMillis() > expireAt; }
    }
    private final LearnsetManager learnsets;

    /**
     * "Left then right" combo detection for PARTY GUI:
     * left click a slot, then within a short time right click the SAME slot -> open SUMMARY.
     */
    private final java.util.Map<java.util.UUID, LastClick> lastPartyLeftClick = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * For PC GUI, we support PARTY-like "Left then Right" to open SUMMARY.
     * Because left-click in PC normally withdraws immediately, we delay the withdraw slightly and
     * cancel it if a right-click on the same slot happens within the window.
     */
    private final java.util.Map<java.util.UUID, PendingPcWithdraw> pendingPcWithdraw = new java.util.concurrent.ConcurrentHashMap<>();

    private static final class PendingPcWithdraw {
        final int rawSlot;
        final int page;
        final int index;
        final java.util.UUID pokemonUuid;
        final long atMs;
        final int taskId;
        PendingPcWithdraw(int rawSlot, int page, int index, java.util.UUID pokemonUuid, long atMs, int taskId) {
            this.rawSlot = rawSlot;
            this.page = page;
            this.index = index;
            this.pokemonUuid = pokemonUuid;
            this.atMs = atMs;
            this.taskId = taskId;
        }
    }



    @EventHandler(ignoreCancelled = true)
    public void onAsyncChatRename(AsyncPlayerChatEvent e) {
        PendingNicknameRename pending = pendingNicknameRename.get(e.getPlayer().getUniqueId());
        if (pending == null) return;
        e.setCancelled(true);
        if (pending.expired()) {
            pendingNicknameRename.remove(e.getPlayer().getUniqueId());
            e.getPlayer().sendMessage("§c改名请求已过期，请重新打开精灵详情再试一次。");
            return;
        }
        String msg = e.getMessage() == null ? "" : e.getMessage().trim();
        org.bukkit.Bukkit.getScheduler().runTask(PokeDemoPlugin.INSTANCE, () -> handlePendingNicknameChat(e.getPlayer(), pending, msg));
    }

    private void handlePendingNicknameChat(Player player, PendingNicknameRename pending, String msg) {
        pendingNicknameRename.remove(player.getUniqueId());
        PlayerProfile prof = storage.getProfile(player.getUniqueId());
        PokemonInstance p = prof.findByUuid(pending.pokemonUuid);
        if (p == null) {
            player.sendMessage("§c这只精灵不存在或已被移除。");
            return;
        }
        if (msg.equalsIgnoreCase("cancel") || msg.equalsIgnoreCase("取消") || msg.equalsIgnoreCase("/cancel")) {
            player.sendMessage("§7已取消改名。");
            UtilGui.openSummary(player, storage, p, pending.fromParty, pending.partySlot, pending.pcIndex, pending.tab);
            return;
        }
        if (msg.equalsIgnoreCase("clear") || msg.equalsIgnoreCase("none") || msg.equalsIgnoreCase("清除")) {
            p.nickname = null;
            storage.markDirty(player.getUniqueId());
            player.sendMessage("§a已清除昵称。");
            UtilGui.openSummary(player, storage, p, pending.fromParty, pending.partySlot, pending.pcIndex, pending.tab);
            return;
        }
        String clean = msg.replace('§', ' ').replace('&', ' ').trim();
        if (clean.length() > 16) clean = clean.substring(0, 16);
        if (clean.isBlank()) {
            player.sendMessage("§c昵称不能为空。输入 clear 可清除昵称。");
            UtilGui.openSummary(player, storage, p, pending.fromParty, pending.partySlot, pending.pcIndex, pending.tab);
            return;
        }
        p.nickname = clean;
        storage.markDirty(player.getUniqueId());
        player.sendMessage("§a已将 §f" + p.speciesId + " §a改名为：§f" + clean + "§a。");
        UtilGui.openSummary(player, storage, p, pending.fromParty, pending.partySlot, pending.pcIndex, pending.tab);
    }

    public GuiListener(Storage storage, BattleManager battles, EvolutionManager evolutions, LearnsetManager learnsets) {
        this.storage = storage;
        this.battles = battles;
        this.evolutions = evolutions;
        this.learnsets = learnsets;
    }

    private static class LastClick {
        final int slot;
        final long atMs;
        LastClick(int slot, long atMs) {
            this.slot = slot;
            this.atMs = atMs;
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        Inventory inv = e.getInventory();
        if (!(inv.getHolder() instanceof GuiHolder holder)) return;

        // Guide switch GUI is handled by GuideBookListener.
        // Do NOT cancel here, otherwise its click handler won't run.
        if (holder.type == GuiType.GUIDE_SWITCH) {
            return;
        }

        e.setCancelled(true);

        if (holder.type == GuiType.PARTY_TRADE_SELECT) {
            handlePartyTradeSelectClick(player, holder, e.getRawSlot());
            return;
        }

        if (holder.type == GuiType.PARTY_CLONE_SELECT) {
            handlePartyCloneSelectClick(player, holder, e.getRawSlot());
            return;
        }

        if (holder.type == GuiType.PARTY) {
            handlePartyClick(player, holder, e.getRawSlot(), e.isLeftClick(), e.isRightClick(), e.isShiftClick());
            return;
        }

        if (holder.type == GuiType.PC) {
            handlePcClick(player, holder, e.getRawSlot(), e.isLeftClick(), e.isRightClick(), e.isShiftClick());
            return;
        }

        if (holder.type == GuiType.PASTURE) {
            PastureGui.handleClick(player, holder, e.getRawSlot());
            return;
        }

        if (holder.type == GuiType.PASTURE_BALL_SELECT) {
            PastureGui.handleBallSelectClick(player, holder, e.getRawSlot(), e.getCurrentItem());
            return;
        }

        if (holder.type == GuiType.FOSSIL_MACHINE) {
            FossilGui.handleClick(player, holder, e.getRawSlot(), e.isLeftClick(), e.isRightClick());
            return;
        }

        if (holder.type == GuiType.FOSSIL_FOSSIL_SELECT) {
            FossilGui.handleFossilSelectClick(player, holder, e.getRawSlot(), e.getCurrentItem());
            return;
        }

        if (holder.type == GuiType.FOSSIL_BALL_SELECT) {
            FossilGui.handleBallSelectClick(player, holder, e.getRawSlot(), e.getCurrentItem());
            return;
        }

        if (holder.type == GuiType.FOSSIL_ANALYZER) {
            FossilAnalyzerGui.handleClick(player, holder, e.getRawSlot(), e.isLeftClick(), e.isRightClick());
            return;
        }

        if (holder.type == GuiType.CLONE_MACHINE) {
            CloneGui.handleClick(player, holder, e.getRawSlot());
            return;
        }

        if (holder.type == GuiType.TRADE_MACHINE) {
            TradeGui.handleClick(player, holder, e.getRawSlot());
            return;
        }

        if (holder.type == GuiType.SUMMARY) {
            handleSummaryClick(player, holder, e);
            return;
        }

        if (holder.type == GuiType.BATTLE) {
            battles.handleBattleClick(player, holder, e.getRawSlot(), e.getCurrentItem());
            return;
        }

        if (holder.type == GuiType.BATTLE_SWITCH) {
            battles.handleBattleSwitchClick(player, holder, e.getRawSlot(), e.getCurrentItem());
            return;
        }

        if (holder.type == GuiType.BATTLE_BALL_SELECT) {
            battles.handleBallSelectClick(player, holder, e.getRawSlot(), e.getCurrentItem());
            return;
        }

        if (holder.type == GuiType.BATTLE_ITEM_SELECT) {
            battles.handleBattleItemSelectClick(player, holder, e.getRawSlot(), e.getCurrentItem());
            return;
        }

        if (holder.type == GuiType.PVP_READY) {
            battles.handlePvpReadyClick(player, holder, e.getRawSlot());
            return;
        }

        if (holder.type == GuiType.WILD_LOOT) {
            handleWildLootClick(player, holder, e);
            return;
        }

        if (holder.type == GuiType.STARTER_SELECT) {
            handleStarterSelectClick(player, holder, e.getRawSlot());
            return;
        }

        if (holder.type == GuiType.PHONE_MENU) {
            handlePhoneMenuClick(player, holder, e.getRawSlot());
            return;
        }

        if (holder.type == GuiType.RECIPE_CATS) {
            handleRecipeCatsClick(player, holder, e.getRawSlot());
            return;
        }

        if (holder.type == GuiType.RECIPE_LIST) {
            handleRecipeListClick(player, holder, e);
            return;
        }

        if (holder.type == GuiType.RECIPE_VIEW) {
            handleRecipeViewClick(player, holder, e);
            return;
        }

        if (holder.type == GuiType.POKEDEX_LIST) {
            handlePokedexListClick(player, holder, e.getRawSlot());
            return;
        }

        if (holder.type == GuiType.POKEDEX_ENTRY) {
            handlePokedexEntryClick(player, holder, e.getRawSlot());
            return;
        }

        if (holder.type == GuiType.MOVE_LEARN) {
            handleMoveLearnClick(player, holder, e.getRawSlot());
            return;
        }

        if (holder.type == GuiType.TUTOR_MOVE_LIST) {
            if (PokeDemoPlugin.INSTANCE != null && PokeDemoPlugin.INSTANCE.getTutorNpcManager() != null) {
                PokeDemoPlugin.INSTANCE.getTutorNpcManager().handleMoveListClick(player, holder, e.getRawSlot());
            }
            return;
        }

        if (holder.type == GuiType.TUTOR_PARTY_SELECT) {
            if (PokeDemoPlugin.INSTANCE != null && PokeDemoPlugin.INSTANCE.getTutorNpcManager() != null) {
                PokeDemoPlugin.INSTANCE.getTutorNpcManager().handlePartySelectClick(player, holder, e.getRawSlot());
            }
            return;
        }
    }

    private void handlePhoneMenuClick(Player player, GuiHolder holder, int rawSlot) {
        PokeDemoPlugin plugin = PokeDemoPlugin.INSTANCE;
        if (plugin == null) { player.closeInventory(); return; }
        if (rawSlot == 26) { player.closeInventory(); return; }

        // Party
        if (rawSlot == 11) {
            UtilGui.openParty(player, storage, -1);
            return;
        }
        // PC
        if (rawSlot == 12) {
            UtilGui.openPc(player, storage, 0);
            return;
        }

        // Recipes
        if (rawSlot == 13) {
            RecipeGui.openCategories(player, true);
            return;
        }
        // Healer / Healer Machine
        if (rawSlot == 14) {
            int healed = plugin.healParty(player, true);
            if (healed > 0) player.sendMessage("§a已治疗队伍中的 " + healed + " 只精灵：回满血、清除异常并恢复PP。");
            player.closeInventory();
            return;
        }
        // Pokédex
        if (rawSlot == 15) {
            UtilGui.openPokedexList(player, storage, 0, true);
        }
    }

    private void handleRecipeCatsClick(Player player, GuiHolder holder, int rawSlot) {
        if (rawSlot == 26) {
            if (holder.recipeOpenedFromPhone) UtilGui.openPhoneMenu(player);
            else player.closeInventory();
            return;
        }

        if (rawSlot == 11) {
            RecipeGui.openList(player, RecipeBook.Category.BALLS, 0, holder.recipeOpenedFromPhone);
            return;
        }
        if (rawSlot == 13) {
            RecipeGui.openList(player, RecipeBook.Category.DEVICES, 0, holder.recipeOpenedFromPhone);
            return;
        }
        if (rawSlot == 15) {
            RecipeGui.openList(player, RecipeBook.Category.ITEMS, 0, holder.recipeOpenedFromPhone);
        }
    }

    private void handleRecipeListClick(Player player, GuiHolder holder, org.bukkit.event.inventory.InventoryClickEvent e) {
        int rawSlot = e.getRawSlot();
        RecipeBook.Category cat;
        try {
            cat = RecipeBook.Category.valueOf(holder.recipeCategory);
        } catch (Exception ex) {
            cat = RecipeBook.Category.OTHER;
        }

        // back
        if (rawSlot == 49) {
            RecipeGui.openCategories(player, holder.recipeOpenedFromPhone);
            return;
        }
        // prev/next
        if (rawSlot == 45) {
            RecipeGui.openList(player, cat, Math.max(0, holder.page - 1), holder.recipeOpenedFromPhone);
            return;
        }
        if (rawSlot == 53) {
            RecipeGui.openList(player, cat, holder.page + 1, holder.recipeOpenedFromPhone);
            return;
        }
        // entries 0..44
        if (rawSlot < 0 || rawSlot >= 45) return;
        PokeDemoPlugin plugin = PokeDemoPlugin.INSTANCE;
        if (plugin == null) return;
        RecipeBook book = plugin.getRecipeBook();
        if (book == null) return;
        var list = book.list(cat);
        int idx = holder.page * 45 + rawSlot;
        if (idx < 0 || idx >= list.size()) return;
        var def = list.get(idx);

        // OP right-click: give the item directly for easy testing.
        if ((player.isOp() || player.hasPermission("pokedemo.admin")) && e.isRightClick()) {
            org.bukkit.inventory.ItemStack give = def.output.clone();
            var leftover = player.getInventory().addItem(give);
            if (!leftover.isEmpty()) {
                for (var it : leftover.values()) player.getWorld().dropItemNaturally(player.getLocation(), it);
            }
            player.sendMessage("§a已获得：§f" + def.displayName + " §7(" + def.key + ")");
            player.updateInventory();
            return;
        }

                RecipeGui.openView(player, def.key, cat, holder.page, holder.recipeOpenedFromPhone);
    }

    private void handleRecipeViewClick(Player player, GuiHolder holder, org.bukkit.event.inventory.InventoryClickEvent e) {
        int rawSlot = e.getRawSlot();

        // OP right-click output slot: give item.
        if ((player.isOp() || player.hasPermission("pokedemo.admin")) && e.isRightClick() && rawSlot == 24) {
            PokeDemoPlugin plugin = PokeDemoPlugin.INSTANCE;
            if (plugin != null && plugin.getRecipeBook() != null) {
                RecipeBook.RecipeDef def = plugin.getRecipeBook().get(holder.recipeKey);
                if (def != null) {
                    org.bukkit.inventory.ItemStack give = def.output.clone();
                    var leftover = player.getInventory().addItem(give);
                    if (!leftover.isEmpty()) {
                        for (var it : leftover.values()) player.getWorld().dropItemNaturally(player.getLocation(), it);
                    }
                    player.sendMessage("§a已获得：§f" + def.displayName + " §7(" + def.key + ")");
                    player.updateInventory();
                    return;
                }
            }
        }
        // back
        if (rawSlot == 49) {
            RecipeBook.Category cat;
            try {
                cat = RecipeBook.Category.valueOf(holder.recipeCategory);
            } catch (Exception ex) {
                cat = RecipeBook.Category.OTHER;
            }
            RecipeGui.openList(player, cat, holder.recipeReturnPage, holder.recipeOpenedFromPhone);
        }
    }

    /** True if player is near a registered healer machine NOTE_BLOCK. */
    private static boolean isNearHealerMachine(Player player, MachineRegistry mr, int radius) {
        if (player == null || mr == null) return false;
        var base = player.getLocation();
        var w = base.getWorld();
        if (w == null) return false;
        int r = Math.max(1, radius);
        int bx = base.getBlockX();
        int by = base.getBlockY();
        int bz = base.getBlockZ();
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    var loc = new org.bukkit.Location(w, bx + dx, by + dy, bz + dz);
                    MachineType t = mr.get(loc);
                    if (t == MachineType.HEALER) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void handlePokedexListClick(Player player, GuiHolder holder, int rawSlot) {
        // back
        if (rawSlot == 52) {
            if (holder.pokedexOpenedFromPhone) UtilGui.openPhoneMenu(player);
            else player.closeInventory();
            return;
        }
        // prev/next
        if (rawSlot == 45) {
            UtilGui.openPokedexList(player, storage, Math.max(0, holder.page - 1), holder.pokedexOpenedFromPhone);
            return;
        }
        if (rawSlot == 53) {
            UtilGui.openPokedexList(player, storage, holder.page + 1, holder.pokedexOpenedFromPhone);
            return;
        }
        // entries 0-44
        if (rawSlot < 0 || rawSlot >= 45) return;
        PokedexData pd = PokeDemoPlugin.INSTANCE != null ? PokeDemoPlugin.INSTANCE.getPokedexData() : null;
        if (pd == null) return;
        int index = holder.page * 45 + rawSlot;
        if (index < 0 || index >= pd.ordered().size()) return;
        String sid = pd.ordered().get(index).speciesId();
        UtilGui.openPokedexEntry(player, storage, sid, holder.page, holder.pokedexOpenedFromPhone);
    }

    private void handlePokedexEntryClick(Player player, GuiHolder holder, int rawSlot) {
        if (rawSlot == 26) { player.closeInventory(); return; }
        if (rawSlot == 18) {
            UtilGui.openPokedexList(player, storage, holder.pokedexReturnPage, holder.pokedexOpenedFromPhone);
        }
    }

    private void handleStarterSelectClick(Player player, GuiHolder holder, int rawSlot) {
        if (rawSlot == 22) {
            player.closeInventory();
            return;
        }

        String pick = null;
        if (rawSlot == 11) pick = "bulbasaur";
        if (rawSlot == 13) pick = "charmander";
        if (rawSlot == 15) pick = "squirtle";
        if (pick == null) return;

        PokeDemoPlugin plugin = PokeDemoPlugin.INSTANCE;
        if (plugin == null) return;
        Dex dex = plugin.getDex();
        if (dex == null) return;

        PlayerProfile prof = storage.getProfile(player.getUniqueId());
        if (prof == null) return;
        if (prof.starterChosen) {
            player.closeInventory();
            return;
        }

        Species sp = dex.getSpecies(pick);
        if (sp == null) {
            player.sendMessage("§c找不到该精灵：" + pick);
            return;
        }

        // starter: allow rolling hidden ability (梦特)
        PokemonInstance p = PokemonInstance.createOwnedAllowHidden(sp, 10, dex);
        p.originalTrainer = player.getUniqueId();
        p.originalTrainerName = player.getName();

        prof.depositToPartyOrPc(p);
        prof.starterChosen = true;
        prof.starterSpeciesId = pick;
        if (prof.dexCaught != null) prof.dexCaught.add(pick.toLowerCase(java.util.Locale.ROOT));
        storage.saveProfile(player.getUniqueId());

        LangManager lang = plugin.getLang();
        String name = (lang != null) ? lang.species(pick, pick) : pick;
        player.sendMessage("§a已选择初始伙伴：§f" + name + " §7(Lv.10)");
        player.closeInventory();
    }

    private void handlePartyClick(Player player, GuiHolder holder, int rawSlot, boolean left, boolean right, boolean shift) {
if (rawSlot == 26) {
            player.closeInventory();
            return;
        }

        if (rawSlot < 0 || rawSlot > 5) return;

        PlayerProfile prof = storage.getProfile(player.getUniqueId());
        if (rawSlot >= prof.party.size()) return;

        PokemonInstance picked = prof.party.get(rawSlot);
        if (picked != null && picked.uiLocked) {
            String reason = (picked.uiLockReason == null || picked.uiLockReason.isBlank()) ? "" : ("§7(" + picked.uiLockReason + ")");
            player.sendMessage("§c该精灵已被锁定，无法操作。" + reason);
            return;
        }

        // Eggs are special: can be moved between Party/PC, but cannot be inspected or used in systems.
        if (picked != null && picked.isEgg) {
            if (shift) {
                player.sendMessage("§c蛋不能查看详情，也不能参与战斗/牧场/机器选择。\n§7(只能存入队伍或电脑盒子以孵化)");
                return;
            }
        }

        // Shift+Click (either left or right): open summary details
        if (shift) {
            UtilGui.openSummary(player, storage, picked, true, rawSlot, -1, 0);
            return;
        }

        // Left then Right combo: open summary (to mimic Cobblemon-style "details")
        if (right) {
            long now = System.currentTimeMillis();
            LastClick lc = lastPartyLeftClick.get(player.getUniqueId());
            long windowMs = 450;
            if (lc != null && lc.slot == rawSlot && now - lc.atMs <= windowMs) {
                PokemonInstance p = prof.party.get(rawSlot);
                if (p != null && p.isEgg) {
                    player.sendMessage("§c蛋不能查看详情。§7(只能存入队伍或电脑盒子以孵化)");
                    return;
                }
                UtilGui.openSummary(player, storage, p, true, rawSlot, -1, 0);
                // consume
                lastPartyLeftClick.remove(player.getUniqueId());
                return;
            }

            // Right click: deposit to PC
            int nonEgg = 0;
            for (PokemonInstance pi : prof.party) if (pi != null && !pi.isEgg) nonEgg++;
            PokemonInstance target = prof.party.get(rawSlot);
            // Must keep at least one NON-EGG Pokémon in party
            if (target != null && !target.isEgg && nonEgg <= 1) {
                player.sendMessage("§c不能把最后一只可战斗精灵存入电脑盒子（蛋不算可战斗精灵）。");
                return;
            }
            PokemonInstance p = prof.party.remove(rawSlot);
            prof.pc.add(p);
            storage.clearPendingPcRelease(player.getUniqueId());
            player.sendMessage("§b已存入电脑盒子：" + p.displayName());
            UtilGui.openParty(player, storage, -1);
            return;
        }

        // Left click: remember for L+R combo, and also click-to-swap party order
        if (!left) return;
        lastPartyLeftClick.put(player.getUniqueId(), new LastClick(rawSlot, System.currentTimeMillis()));

        if (holder.selectedPartySlot < 0) {
            holder.selectedPartySlot = rawSlot;
            UtilGui.openParty(player, storage, holder.selectedPartySlot);
            return;
        }
        if (holder.selectedPartySlot == rawSlot) {
            holder.selectedPartySlot = -1;
            UtilGui.openParty(player, storage, -1);
            return;
        }

        int a = holder.selectedPartySlot;
        int b = rawSlot;
        if (a < prof.party.size() && b < prof.party.size()) {
            PokemonInstance pa = prof.party.get(a);
            PokemonInstance pb = prof.party.get(b);
            prof.party.set(a, pb);
            prof.party.set(b, pa);
            player.sendMessage("§a已交换队伍顺序：" + (a + 1) + " ↔ " + (b + 1));
        }
        holder.selectedPartySlot = -1;
        UtilGui.openParty(player, storage, -1);
    }

    private void handlePcClick(Player player, GuiHolder holder, int rawSlot, boolean left, boolean right, boolean shift) {
        // Some actions need a plugin instance for scheduled tasks.
        PokeDemoPlugin plugin = PokeDemoPlugin.INSTANCE;
        if (plugin == null) return;

        // Bukkit uses special negative rawSlot values (e.g. -999) for clicks outside the window.
        // Ignore those to avoid index underflow.
        if (rawSlot < 0) return;
        if (rawSlot >= 45) {
            if (rawSlot == 45) UtilGui.openPc(player, storage, Math.max(0, holder.page - 1));
            if (rawSlot == 53) UtilGui.openPc(player, storage, holder.page + 1);
            return;
        }

        PlayerProfile prof = storage.getProfile(player.getUniqueId());
        int index = holder.page * 45 + rawSlot;
        if (index < 0) return;
        if (index >= prof.pc.size()) return;

        PokemonInstance picked = prof.pc.get(index);

        // Cancel any pending delayed-withdraw when doing other actions
        if (shift) {
            PendingPcWithdraw pw = pendingPcWithdraw.remove(player.getUniqueId());
            if (pw != null) Bukkit.getScheduler().cancelTask(pw.taskId);
        }

        // Pasture selection mode: clicking a PC Pokémon assigns it to pasture A/B instead of withdrawing.
        if (plugin != null && plugin.getPastureManager() != null) {
            PastureManager.PendingSelect ps = plugin.getPastureManager().getPending(player.getUniqueId());
            if (ps != null) {
                if (picked != null && picked.isEgg) {
                    player.sendMessage("§c蛋不能用于牧场。\n§7(蛋只能放在队伍/电脑盒子里等待孵化)");
                    return;
                }
                if (picked != null && picked.uiLocked) {
                    player.sendMessage("§c该精灵已被锁定，无法用于牧场。");
                    return;
                }
                plugin.getPastureManager().assign(player.getUniqueId(), ps.pastureKey, ps.slot, picked == null ? null : picked.uuid);
                plugin.getPastureManager().clearPending(player.getUniqueId());
                // back to pasture
                try {
                    String[] a = ps.pastureKey.split(":", 2);
                    String[] xyz = a[1].split(",");
                    org.bukkit.World w = Bukkit.getWorld(a[0]);
                    if (w != null && xyz.length == 3) {
                        org.bukkit.Location loc = new org.bukkit.Location(w, Integer.parseInt(xyz[0]), Integer.parseInt(xyz[1]), Integer.parseInt(xyz[2]));
                        PastureGui.open(player, plugin, loc);
                        return;
                    }
                } catch (Throwable ignored) {}
                // fallback
                player.closeInventory();
                return;
            }
        }

        // Clone selection mode: clicking a PC Pokémon assigns it to clone machine.
        if (plugin != null && plugin.getCloneManager() != null) {
            CloneManager.PendingSelect cs = plugin.getCloneManager().getPending(player.getUniqueId());
            if (cs != null) {
                // must be Mew and clonable
                if (picked == null) return;
                if (picked.uiLocked) {
                    player.sendMessage("§c该精灵已被锁定，无法用于克隆。§7可用 /pokedemo unlockall 一键解除锁定。");
                    return;
                }
                if (!"mew".equalsIgnoreCase(picked.speciesId)) {
                    player.sendMessage("§c只有梦幻可以放入克隆机。");
                    return;
                }
                if (picked.mewCloneDisabled || picked.mewCloneAttempts >= 3) {
                    player.sendMessage("§c这只梦幻已无法再进行克隆（次数已用尽或已克隆出超梦）。");
                    return;
                }
                plugin.getCloneManager().assignMew(player.getUniqueId(), cs.cloneKey, picked.uuid);
                plugin.getCloneManager().clearPending(player.getUniqueId());
                // back to clone GUI
                try {
                    String[] a = cs.cloneKey.split(":", 2);
                    String[] xyz = a[1].split(",");
                    org.bukkit.World w = Bukkit.getWorld(a[0]);
                    if (w != null && xyz.length == 3) {
                        org.bukkit.Location loc = new org.bukkit.Location(w, Integer.parseInt(xyz[0]), Integer.parseInt(xyz[1]), Integer.parseInt(xyz[2]));
                        CloneGui.open(player, plugin, loc);
                        return;
                    }
                } catch (Throwable ignored) {}
                player.closeInventory();
                return;
            }
        }

        if (picked != null && picked.uiLocked) {
            String reason = (picked.uiLockReason == null || picked.uiLockReason.isBlank()) ? "" : ("§7(" + picked.uiLockReason + ")");
            player.sendMessage("§c该精灵已被锁定，无法操作。" + reason);
            return;
        }

        // Eggs: cannot be inspected or released.
        if (picked != null && picked.isEgg) {
            if (shift) {
                player.sendMessage("§c蛋不能查看详情，也不能放生。\n§7(只能存入队伍或电脑盒子以孵化)");
                return;
            }
            if (right) {
                player.sendMessage("§c蛋不能放生。\n§7(只能存入队伍或电脑盒子以孵化)");
                return;
            }
        }

        // Shift+Click: open summary
        if (shift) {
            UtilGui.openSummary(player, storage, picked, false, -1, index, 0);
            return;
        }

        // If there is a pending delayed withdraw for this slot, a right-click within window opens summary.
        if (right) {
            PendingPcWithdraw pw = pendingPcWithdraw.get(player.getUniqueId());
            long now = System.currentTimeMillis();
            long windowMs = 450;
            if (pw != null && pw.rawSlot == rawSlot && pw.page == holder.page && now - pw.atMs <= windowMs) {
                pendingPcWithdraw.remove(player.getUniqueId());
                Bukkit.getScheduler().cancelTask(pw.taskId);
                UtilGui.openSummary(player, storage, picked, false, -1, index, 0);
                return;
            }
        }

        if (left) {
            // Delay withdraw slightly to allow PARTY-like "Left then Right" to open summary.
            // If the player doesn't right-click quickly, we proceed with the withdraw.
            PendingPcWithdraw old = pendingPcWithdraw.remove(player.getUniqueId());
            if (old != null) Bukkit.getScheduler().cancelTask(old.taskId);

            java.util.UUID pickedUuid = picked == null ? null : picked.uuid;
            long at = System.currentTimeMillis();

            final long tokenAt = at;
            int taskId = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                PendingPcWithdraw cur = pendingPcWithdraw.get(player.getUniqueId());
                if (cur == null || cur.atMs != tokenAt) return;
                // Only proceed if player still viewing PC and the same slot still refers to the same Pokémon.
                try {
                    if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof GuiHolder h2)) return;
                    if (h2.type != GuiType.PC || h2.page != cur.page) return;
                    PlayerProfile prof2 = storage.getProfile(player.getUniqueId());
                    if (cur.index < 0 || cur.index >= prof2.pc.size()) return;
                    PokemonInstance nowPick = prof2.pc.get(cur.index);
                    if (nowPick == null || nowPick.uuid == null || !nowPick.uuid.equals(cur.pokemonUuid)) return;
                    if (prof2.party.size() >= 6) {
                        player.sendMessage("§c你的队伍已满（6只）。");
                        return;
                    }
                    // Eggs cannot be the only thing in party: must keep at least one non-egg Pokémon.
                    if (nowPick.isEgg) {
                        int nonEgg = 0;
                        for (PokemonInstance pi : prof2.party) if (pi != null && !pi.isEgg) nonEgg++;
                        if (nonEgg <= 0) {
                            player.sendMessage("§c不能让队伍里只剩蛋。\n§7请至少保留/取出一只可战斗精灵。");
                            return;
                        }
                    }
                    PokemonInstance p = prof2.pc.remove(cur.index);
                    prof2.party.add(p);
                    player.sendMessage("§a已取出：" + p.displayName() + " §a→ 队伍");
                    UtilGui.openPc(player, storage, cur.page);
                } finally {
                    pendingPcWithdraw.remove(player.getUniqueId());
                }
            }, 6L).getTaskId();

            pendingPcWithdraw.put(player.getUniqueId(), new PendingPcWithdraw(rawSlot, holder.page, index, pickedUuid, at, taskId));
            return;
        }

        if (right) {
            Integer pending = storage.getPendingPcRelease(player.getUniqueId());
            if (pending != null && pending == index) {
                PokemonInstance p = prof.pc.remove(index);
                storage.clearPendingPcRelease(player.getUniqueId());
                player.sendMessage("§c已放生：" + p.displayName());
                UtilGui.openPc(player, storage, holder.page);
                return;
            }
            storage.setPendingPcRelease(player.getUniqueId(), index, 8000);
            player.sendMessage("§e再次右键同一只精灵以确认放生（8秒内有效）");
            UtilGui.openPc(player, storage, holder.page);
        }
    }

    /** Party selection screen for trade machine. Only allows selecting from PARTY. */
    private void handlePartyTradeSelectClick(Player player, GuiHolder holder, int rawSlot) {
        PokeDemoPlugin plugin = PokeDemoPlugin.INSTANCE;
        if (plugin == null || plugin.getTradeManager() == null) {
            player.closeInventory();
            return;
        }
        TradeManager tm = plugin.getTradeManager();
        String key = holder.tradeKey;
        TradeManager.Side side = holder.tradeSelectSide;
        if (key == null || side == null) {
            player.closeInventory();
            return;
        }

        // Back button
        if (rawSlot == 26) {
            // Return to trade GUI
            try {
                String[] a = key.split(":", 2);
                String[] xyz = a[1].split(",");
                org.bukkit.World w = Bukkit.getWorld(a[0]);
                if (w != null && xyz.length == 3) {
                    org.bukkit.Location loc = new org.bukkit.Location(w, Integer.parseInt(xyz[0]), Integer.parseInt(xyz[1]), Integer.parseInt(xyz[2]));
                    TradeGui.open(player, plugin, loc);
                    return;
                }
            } catch (Throwable ignored) {}
            player.closeInventory();
            return;
        }

        if (rawSlot < 0 || rawSlot >= 6) return;
        PlayerProfile prof = storage.getProfile(player.getUniqueId());
        if (prof == null || rawSlot >= prof.party.size()) return;
        PokemonInstance picked = prof.party.get(rawSlot);
        if (picked == null) return;
        if (picked.isEgg) {
            player.sendMessage("§c蛋不能用于交换。");
            return;
        }
        if (picked.uiLocked) {
            player.sendMessage("§c该精灵已被锁定，无法用于交换。§7可用 /pokedemo unlockall 一键解除锁定。");
            return;
        }

        tm.assignFromParty(player.getUniqueId(), key, side, picked.uuid, rawSlot);
        // Back to trade GUI
        try {
            String[] a = key.split(":", 2);
            String[] xyz = a[1].split(",");
            org.bukkit.World w = Bukkit.getWorld(a[0]);
            if (w != null && xyz.length == 3) {
                org.bukkit.Location loc = new org.bukkit.Location(w, Integer.parseInt(xyz[0]), Integer.parseInt(xyz[1]), Integer.parseInt(xyz[2]));
                TradeGui.open(player, plugin, loc);
                return;
            }
        } catch (Throwable ignored) {}
        player.closeInventory();
    }

    /** Party selection screen for clone machine. Only allows selecting Mew from PARTY. */
    private void handlePartyCloneSelectClick(Player player, GuiHolder holder, int rawSlot) {
        PokeDemoPlugin plugin = PokeDemoPlugin.INSTANCE;
        if (plugin == null || plugin.getCloneManager() == null) {
            player.closeInventory();
            return;
        }
        CloneManager cm = plugin.getCloneManager();
        String key = holder.cloneKey;
        if (key == null) {
            player.closeInventory();
            return;
        }

        // Back button
        if (rawSlot == 26) {
            try {
                String[] a = key.split(":", 2);
                String[] xyz = a[1].split(",");
                org.bukkit.World w = Bukkit.getWorld(a[0]);
                if (w != null && xyz.length == 3) {
                    org.bukkit.Location loc = new org.bukkit.Location(w, Integer.parseInt(xyz[0]), Integer.parseInt(xyz[1]), Integer.parseInt(xyz[2]));
                    CloneGui.open(player, plugin, loc);
                    return;
                }
            } catch (Throwable ignored) {}
            player.closeInventory();
            return;
        }

        if (rawSlot < 0 || rawSlot >= 6) return;
        PlayerProfile prof = storage.getProfile(player.getUniqueId());
        if (prof == null || rawSlot >= prof.party.size()) return;
        PokemonInstance picked = prof.party.get(rawSlot);
        if (picked == null) return;
        if (picked.isEgg) {
            player.sendMessage("§c蛋不能用于克隆。\n§7(只能放在队伍/电脑盒子里等待孵化)");
            return;
        }
        if (picked.uiLocked) {
            player.sendMessage("§c该精灵已被锁定，无法用于克隆。§7可用 /pokedemo unlockall 一键解除锁定。");
            return;
        }
        if (!"mew".equalsIgnoreCase(picked.speciesId)) {
            player.sendMessage("§c只有梦幻可以放入克隆机。");
            return;
        }
        if (picked.mewCloneDisabled || picked.mewCloneAttempts >= 3) {
            player.sendMessage("§c这只梦幻已无法再进行克隆（次数已用尽或已克隆出超梦）。");
            return;
        }

        // Safety: party cannot contain ONLY this mew and/or eggs.
        int otherNonEgg = 0;
        for (int i = 0; i < Math.min(6, prof.party.size()); i++) {
            PokemonInstance p = prof.party.get(i);
            if (p == null) continue;
            if (p.isEgg) continue;
            if (p.uuid.equals(picked.uuid)) continue;
            otherNonEgg++;
        }
        if (otherNonEgg <= 0) {
            player.sendMessage("§c队伍不能只有梦幻/蛋，无法开始克隆。\n§7请至少携带 1 只其它可战斗精灵。");
            return;
        }

        cm.assignMew(player.getUniqueId(), key, picked.uuid);
        // Back to clone GUI
        try {
            String[] a = key.split(":", 2);
            String[] xyz = a[1].split(",");
            org.bukkit.World w = Bukkit.getWorld(a[0]);
            if (w != null && xyz.length == 3) {
                org.bukkit.Location loc = new org.bukkit.Location(w, Integer.parseInt(xyz[0]), Integer.parseInt(xyz[1]), Integer.parseInt(xyz[2]));
                CloneGui.open(player, plugin, loc);
                return;
            }
        } catch (Throwable ignored) {}
        player.closeInventory();
    }

    private void handleSummaryClick(Player player, GuiHolder holder, org.bukkit.event.inventory.InventoryClickEvent e) {
        int rawSlot = e.getRawSlot();
        org.bukkit.inventory.ItemStack cursor = e.getCursor();
        int topSize = e.getView().getTopInventory().getSize();
        PlayerProfile prof = storage.getProfile(player.getUniqueId());
        PokemonInstance p = prof.findByUuid(holder.summaryPokemonUuid);
        if (p == null) {
            player.sendMessage("§c这只精灵不存在或已被移除。");
            if (holder.summaryFromParty) UtilGui.openParty(player, storage, -1);
            else UtilGui.openPc(player, storage, Math.max(0, holder.page));
            return;
        }

        if (p.uiLocked) {
            String reason = (p.uiLockReason == null || p.uiLockReason.isBlank()) ? "" : ("§7(" + p.uiLockReason + ")");
            player.sendMessage("§c该精灵已被锁定，无法查看详情。" + reason);
            if (holder.summaryFromParty) UtilGui.openParty(player, storage, -1);
            else UtilGui.openPc(player, storage, Math.max(0, holder.page));
            return;
        }

        // --- Held item selection (Step1) ---
        // Some clients / server configs make cursor item moving unreliable in custom GUIs.
        // Allow: left-click an item in the player's inventory area to select it for equipping.
        if (holder.summaryTab == 0 && rawSlot >= topSize) {
            // Only react to normal left-click on player's inventory items
            if (e.getClick() == org.bukkit.event.inventory.ClickType.LEFT && e.getCurrentItem() != null
                    && e.getCurrentItem().getType() != org.bukkit.Material.AIR) {
                PokeDemoPlugin plugin = PokeDemoPlugin.INSTANCE;
                if (plugin == null) return;
                ItemFactory items = plugin.getItems();
                ItemRegistry reg = plugin.getItemRegistry();
                LangManager lang = plugin.getLang();

                ItemDef def = (items != null) ? items.identify(e.getCurrentItem(), reg) : null;
                if (def == null) {
                    // Not a PokeDemo item, ignore.
                    return;
                }
                if (def.type == ItemType.BALL) {
                    player.sendMessage("§c精灵球不能作为携带物品。");
                    return;
                }
                if (def.type == ItemType.TM) {
                    player.sendMessage("§c学习机不能作为携带物品。");
                    return;
                }

                int invSlot = e.getSlot(); // slot within clicked inventory (player inv)
                // 8 seconds to finish the second click
                pendingHeldPick.put(player.getUniqueId(), new PendingHeldPick(def.id, invSlot, System.currentTimeMillis() + 8000L));
                player.sendMessage("§e已选择：§f" + (lang == null ? def.id : lang.item(def.id, def.id)) + "§e。现在点击‘携带物品’格来让精灵携带（8秒内有效）。");
            }
            return;
        }

        // Tabs
        if (rawSlot == 0) {
            UtilGui.openSummary(player, storage, p, holder.summaryFromParty, holder.summaryPartySlot, holder.summaryPcIndex, 0);
            return;
        }
        if (rawSlot == 1) {
            UtilGui.openSummary(player, storage, p, holder.summaryFromParty, holder.summaryPartySlot, holder.summaryPcIndex, 1);
            return;
        }
        if (rawSlot == 2) {
            UtilGui.openSummary(player, storage, p, holder.summaryFromParty, holder.summaryPartySlot, holder.summaryPcIndex, 2);
            return;
        }
        if (rawSlot == 3) {
            UtilGui.openSummary(player, storage, p, holder.summaryFromParty, holder.summaryPartySlot, holder.summaryPcIndex, 3);
            return;
        }



        // Nickname rename (tab 0, slot 21)
        if (holder.summaryTab == 0 && rawSlot == 21) {
            if (e.isShiftClick()) {
                p.nickname = null;
                storage.markDirty(player.getUniqueId());
                player.sendMessage("§a已清除昵称。");
                UtilGui.openSummary(player, storage, p, holder.summaryFromParty, holder.summaryPartySlot, holder.summaryPcIndex, holder.summaryTab);
                return;
            }
            pendingNicknameRename.put(player.getUniqueId(), new PendingNicknameRename(p.uuid, holder.summaryFromParty, holder.summaryPartySlot, holder.summaryPcIndex, holder.summaryTab, System.currentTimeMillis() + 60000L));
            player.closeInventory();
            player.sendMessage("§e请输入新的昵称（最长16字）。输入 §fclear§e 可清除昵称，输入 §fcancel§e 取消。\n§8你有 60 秒时间。");
            return;
        }

        // Held item (tab 0, slot 25):
        // Preferred flow:
        //  - Step1: left-click a PokeDemo item in your inventory area (select)
        //  - Step2: click this slot to equip (consumes 1 from your inventory)
        // Fallback flow (if cursor moving works):
        //  - If cursor holds a PokeDemo item: set as held item (consume 1), returning old held item to player.
        //  - If cursor empty: take off held item.
        if (holder.summaryTab == 0 && rawSlot == 25) {
            PokeDemoPlugin plugin = PokeDemoPlugin.INSTANCE;
            if (plugin == null) return;
            ItemFactory items = plugin.getItems();
            ItemRegistry reg = plugin.getItemRegistry();
            LangManager lang = plugin.getLang();

            // helper: give item back safely
            java.util.function.Consumer<String> giveBack = (itemId) -> {
                if (itemId == null || itemId.isBlank() || items == null || reg == null) return;
                ItemDef def = reg.get(itemId);
                if (def == null) return;
                org.bukkit.inventory.ItemStack it = items.createItem(def, lang, 1);
                if (it == null) return;
                java.util.Map<Integer, org.bukkit.inventory.ItemStack> left = player.getInventory().addItem(it);
                if (!left.isEmpty()) {
                    for (org.bukkit.inventory.ItemStack v : left.values()) {
                        player.getWorld().dropItemNaturally(player.getLocation(), v);
                    }
                }
            };

            // Step2: equip from pending selection (consumes 1 from player's inventory)
            PendingHeldPick pick = pendingHeldPick.get(player.getUniqueId());
            if (pick != null && !pick.expired()) {
                org.bukkit.inventory.ItemStack inSlot = player.getInventory().getItem(pick.invSlot);
                if (inSlot == null || inSlot.getType() == org.bukkit.Material.AIR) {
                    pendingHeldPick.remove(player.getUniqueId());
                    player.sendMessage("§c你选中的物品已经不在背包里啦（或数量为0）。请重新选择一次。");
                    return;
                }
                ItemDef def2 = (items != null) ? items.identify(inSlot, reg) : null;
                if (def2 == null || !pick.itemId.equals(def2.id)) {
                    pendingHeldPick.remove(player.getUniqueId());
                    player.sendMessage("§c你选中的物品已变化，请重新选择一次。");
                    return;
                }
                // consume 1 from that inventory slot
                int amt2 = inSlot.getAmount();
                if (amt2 <= 0) {
                    pendingHeldPick.remove(player.getUniqueId());
                    player.sendMessage("§c你选中的物品数量不足，请重新选择一次。");
                    return;
                }
                if (amt2 == 1) player.getInventory().setItem(pick.invSlot, null);
                else {
                    inSlot.setAmount(amt2 - 1);
                    player.getInventory().setItem(pick.invSlot, inSlot);
                }

                // return old held item
                if (p.heldItemId != null && !p.heldItemId.isBlank()) {
                    giveBack.accept(p.heldItemId);
                }
                p.heldItemId = def2.id;
                storage.markDirty(player.getUniqueId());
                pendingHeldPick.remove(player.getUniqueId());
                player.sendMessage("§a已让 §f" + p.displayName() + " §a携带：§f" + (lang == null ? def2.id : lang.item(def2.id, def2.id)) + "§a。" );
                UtilGui.openSummary(player, storage, p, holder.summaryFromParty, holder.summaryPartySlot, holder.summaryPcIndex, holder.summaryTab);
                return;
            } else if (pick != null && pick.expired()) {
                pendingHeldPick.remove(player.getUniqueId());
            }

            // If holding something on cursor -> try set
            if (cursor != null && cursor.getType() != org.bukkit.Material.AIR) {
                ItemDef def = (items != null) ? items.identify(cursor, reg) : null;
                if (def == null) {
                    player.sendMessage("§c只能让精灵携带本插件的道具（需要有物品ID）。");
                    return;
                }
                // Disallow balls (to avoid weird capture flows) and disallow control wand
                if (def.type == ItemType.BALL) {
                    player.sendMessage("§c精灵球不能作为携带物品。");
                    return;
                }
                if (def.type == ItemType.TM) {
                    player.sendMessage("§c学习机不能作为携带物品。");
                    return;
                }

                // consume 1 from cursor
                int amt = cursor.getAmount();
                if (amt <= 0) return;
                if (amt == 1) {
                    e.setCursor(null);
                } else {
                    cursor.setAmount(amt - 1);
                    e.setCursor(cursor);
                }

                // return old held item
                if (p.heldItemId != null && !p.heldItemId.isBlank()) {
                    giveBack.accept(p.heldItemId);
                }
                p.heldItemId = def.id;
                storage.markDirty(player.getUniqueId());
                player.sendMessage("§a已让 §f" + p.displayName() + " §a携带：§f" + (lang == null ? def.id : lang.item(def.id, def.id)) + "§a。" );
                UtilGui.openSummary(player, storage, p, holder.summaryFromParty, holder.summaryPartySlot, holder.summaryPcIndex, holder.summaryTab);
                return;
            }

            // Cursor empty -> take off
            if (p.heldItemId != null && !p.heldItemId.isBlank()) {
                String oldId = p.heldItemId;
                p.heldItemId = null;
                giveBack.accept(oldId);
                storage.markDirty(player.getUniqueId());
                player.sendMessage("§e已取下 §f" + p.displayName() + " §e的携带物品。" );
                UtilGui.openSummary(player, storage, p, holder.summaryFromParty, holder.summaryPartySlot, holder.summaryPcIndex, holder.summaryTab);
            } else {
                player.sendMessage("§7这只精灵没有携带物品。" );
            }
            return;
        }
        // Back
        if (rawSlot == 45) {
            if (holder.summaryFromParty) {
                UtilGui.openParty(player, storage, -1);
            } else {
                UtilGui.openPc(player, storage, holder.page);
            }
            return;
        }

        // Evolution button
if (rawSlot == 53) {
    EvolutionManager evo = this.evolutions;
    if (evo == null) {
        player.sendMessage("§c进化系统未初始化。");
        return;
    }
    var list = evo.getAvailableEvolutions(player.getUniqueId(), p);
    if (list.isEmpty()) {
        player.sendMessage("§7这只精灵当前无法进化。");
        return;
    }
	    Evolution evoChoice = list.get(0);
	    boolean ok = evo.evolveNow(player, p, evoChoice.result());
    if (!ok) {
        player.sendMessage("§c进化失败：目标形态缺失或数据不完整。");
        return;
    }
    // reopen summary to refresh UI
    UtilGui.openSummary(player, storage, p, holder.summaryFromParty, holder.summaryPartySlot, holder.summaryPcIndex, holder.summaryTab);
    return;
}
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        Inventory inv = e.getInventory();
        if (!(inv.getHolder() instanceof GuiHolder holder)) return;

        // Cancel pasture selection if player closes the PC without choosing.
        if (holder.type == GuiType.PC) {
            try {
                PokeDemoPlugin pl = PokeDemoPlugin.INSTANCE;
                if (pl != null && pl.getPastureManager() != null) {
                    pl.getPastureManager().clearPending(holder.playerId);
                }
                if (pl != null && pl.getCloneManager() != null) {
                    pl.getCloneManager().clearPending(holder.playerId);
                }
            } catch (Throwable ignored) {}
        }

        if (holder.type == GuiType.WILD_LOOT) {
            dropRemainingLoot(inv, holder);
            return;
        }

        // Trade machine: leaving the trade UI should cancel that side's occupancy and unlock selection.
        if (holder.type == GuiType.TRADE_MACHINE || holder.type == GuiType.PARTY_TRADE_SELECT) {
            try {
                PokeDemoPlugin pl = PokeDemoPlugin.INSTANCE;
                if (pl != null && pl.getTradeManager() != null && holder.tradeKey != null) {
                    // Delay 1 tick: if they are switching between trade GUI <-> party select, don't leave.
                    Bukkit.getScheduler().runTaskLater(pl, () -> {
                        try {
                            Player p = Bukkit.getPlayer(holder.playerId);
                            if (p == null || !p.isOnline()) {
                                pl.getTradeManager().leave(holder.tradeKey, holder.playerId);
                                return;
                            }
                            Inventory top = p.getOpenInventory().getTopInventory();
                            if (top != null && top.getHolder() instanceof GuiHolder h2) {
                                if (h2.type == GuiType.TRADE_MACHINE || h2.type == GuiType.PARTY_TRADE_SELECT) {
                                    return; // still in trade flow
                                }
                            }
                            pl.getTradeManager().leave(holder.tradeKey, holder.playerId);
                        } catch (Throwable ignored) {}
                    }, 1L);
                }
            } catch (Throwable ignored) {}
        }
        if (holder.type == GuiType.BATTLE) {
            // Prevent closing battle GUI while the battle is processing a turn.
            // Closing during "请稍等..." can desync and soft-lock the session.
            if (battles.isGuiLockActive(holder.playerId)) {
                Player p = Bukkit.getPlayer(holder.playerId);
                if (p != null && p.isOnline()) {
                    Bukkit.getScheduler().runTask(PokeDemoPlugin.INSTANCE, () -> {
                        if (battles.isGuiLockActive(holder.playerId)) {
                            battles.reopenBattleGui(p);
                        }
                    });
                }
                return;
            }
            battles.onBattleGuiClosed(holder.playerId);
        }

        if (holder.type == GuiType.PVP_READY) {
            // Delay 1 tick: if we're refreshing the PVP_READY GUI (close+open), don't cancel the pending.
            try {
                PokeDemoPlugin pl = PokeDemoPlugin.INSTANCE;
                if (pl != null) {
                    Bukkit.getScheduler().runTaskLater(pl, () -> {
                        try {
                            Player p = Bukkit.getPlayer(holder.playerId);
                            if (p == null || !p.isOnline()) {
                                battles.onPvpReadyClosed(holder.playerId);
                                return;
                            }
                            Inventory top = p.getOpenInventory().getTopInventory();
                            if (top != null && top.getHolder() instanceof GuiHolder h2) {
                                if (h2.type == GuiType.PVP_READY) {
                                    return; // still in PVP ready flow
                                }
                            }
                            battles.onPvpReadyClosed(holder.playerId);
                        } catch (Throwable ignored) {}
                    }, 1L);
                } else {
                    battles.onPvpReadyClosed(holder.playerId);
                }
            } catch (Throwable ignored) {
                battles.onPvpReadyClosed(holder.playerId);
            }
        }

        // If the move learn GUI was opened by an item (TM/HM), closing with ESC should cancel that pending learn
        // and MUST NOT force the player to step through unrelated pending level-up learns.
        if (holder.type == GuiType.MOVE_LEARN && holder.moveLearnFromItem) {
            try {
                PendingItemConsumeManager pending = PokeDemoPlugin.INSTANCE.getPendingItemConsumeManager();
                if (pending != null && pending.hasPending(holder.playerId)) {
                    Player player = Bukkit.getPlayer(holder.playerId);
                    if (player != null) {
                        PlayerProfile prof = storage.getProfile(holder.playerId);
                        PokemonInstance p = prof.findByUuid(holder.moveLearnPokemonUuid);
                        if (p != null && p.pendingMoveLearns != null && !p.pendingMoveLearns.isEmpty()) {
                            // Cancel only the front move (the item-triggered one).
                            String cancelled = learnsets.cancelLearn(player, holder.playerId, p);
                            if (cancelled != null) {
                                pending.onMoveLearnResolved(holder.playerId, cancelled, false);
                            }
                        } else {
                            pending.clear(holder.playerId);
                        }
                        storage.markDirty(holder.playerId);
                    }
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private void handleWildLootClick(Player player, GuiHolder holder, InventoryClickEvent e) {
        // Only allow clicks in the top inventory area.
        Inventory top = e.getView().getTopInventory();
        int raw = e.getRawSlot();
        if (raw < 0 || raw >= top.getSize()) return;

        org.bukkit.inventory.ItemStack cur = e.getCurrentItem();
        if (cur == null || cur.getType() == org.bukkit.Material.AIR) return;

        // Disallow any special move operations.
        try {
            switch (e.getClick()) {
                case SHIFT_LEFT, SHIFT_RIGHT, NUMBER_KEY, DOUBLE_CLICK, DROP, CONTROL_DROP, SWAP_OFFHAND, CREATIVE, UNKNOWN -> {
                    return;
                }
            }
        } catch (Throwable ignored) {}

        boolean right = e.isRightClick();
        int take = right ? 1 : cur.getAmount();
        take = Math.max(1, Math.min(take, cur.getAmount()));

        org.bukkit.inventory.ItemStack give = cur.clone();
        give.setAmount(take);

        java.util.Map<Integer, org.bukkit.inventory.ItemStack> leftover = player.getInventory().addItem(give);
        if (leftover != null && !leftover.isEmpty()) {
            // no space, rollback
            player.sendMessage("§c背包空间不足，无法拾取。");
            return;
        }

        int remain = cur.getAmount() - take;
        if (remain <= 0) {
            top.setItem(raw, null);
        } else {
            cur.setAmount(remain);
            top.setItem(raw, cur);
        }
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.2f);
    }

    private void dropRemainingLoot(Inventory inv, GuiHolder holder) {
        if (holder.lootClosed) return;
        holder.lootClosed = true;
        org.bukkit.Location loc = holder.lootDropLocation;
        if (loc == null) {
            Player p = Bukkit.getPlayer(holder.playerId);
            if (p != null) loc = p.getLocation();
        }
        if (loc == null || loc.getWorld() == null) return;

        for (org.bukkit.inventory.ItemStack it : inv.getContents()) {
            if (it == null || it.getType() == org.bukkit.Material.AIR) continue;
            try { loc.getWorld().dropItemNaturally(loc, it); } catch (Throwable ignored) {}
        }
    }


    private void handleMoveLearnClick(Player player, GuiHolder holder, int rawSlot) {
        if (learnsets == null) {
            player.closeInventory();
            return;
        }
        if (holder.moveLearnPokemonUuid == null) {
            player.closeInventory();
            return;
        }
        PlayerProfile prof = storage.getProfile(player.getUniqueId());
        PokemonInstance p = prof.findByUuid(holder.moveLearnPokemonUuid);
        if (p == null || p.pendingMoveLearns == null || p.pendingMoveLearns.isEmpty()) {
            player.closeInventory();
            return;
        }

        // cancel
        if (rawSlot == 22) {
            String cancelled = learnsets.cancelLearn(player, player.getUniqueId(), p);
            if (cancelled != null) {
                try {
                    PokeDemoPlugin.INSTANCE.getPendingItemConsumeManager().onMoveLearnResolved(player.getUniqueId(), cancelled, false);
                } catch (Throwable ignored) {}
            }
            storage.markDirty(player.getUniqueId());
            // If this was triggered by an item, do NOT force the player to process other pending learns.
            if (holder.moveLearnFromItem) {
                player.closeInventory();
            } else if (p.pendingMoveLearns != null && !p.pendingMoveLearns.isEmpty()) {
                Bukkit.getScheduler().runTask(PokeDemoPlugin.INSTANCE, () -> UtilGui.openMoveLearn(player, storage, p.uuid));
            } else {
                player.closeInventory();
            }
            return;
        }

        // forget slots
        int forgetIndex = -1;
        if (rawSlot == 10) forgetIndex = 0;
        else if (rawSlot == 11) forgetIndex = 1;
        else if (rawSlot == 15) forgetIndex = 2;
        else if (rawSlot == 16) forgetIndex = 3;

        if (forgetIndex >= 0) {
            String learned = learnsets.forgetAndLearn(player, player.getUniqueId(), p, forgetIndex);
            if (learned != null) {
                try {
                    PokeDemoPlugin.INSTANCE.getPendingItemConsumeManager().onMoveLearnResolved(player.getUniqueId(), learned, true);
                } catch (Throwable ignored) {}
            }
            storage.markDirty(player.getUniqueId());
            if (holder.moveLearnFromItem) {
                player.closeInventory();
            } else if (p.pendingMoveLearns != null && !p.pendingMoveLearns.isEmpty()) {
                Bukkit.getScheduler().runTask(PokeDemoPlugin.INSTANCE, () -> UtilGui.openMoveLearn(player, storage, p.uuid));
            } else {
                player.closeInventory();
            }
        }
    }
}
