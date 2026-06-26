package win.pokedemo;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * Handles using PokeDemo items (medicine/status cures/revive). Gen1-first minimal implementation.
 *
 * Design goals:
 *  - Identify items via PDC (item_id)
 *  - Apply effects to player's party (for now: first eligible party mon)
 *  - Keep it safe: no usage while in battle
 */
public class ItemUseListener implements Listener {
    private final PokeDemoPlugin plugin;
    private final Storage storage;
    private final Dex dex;
    private final BattleManager battles;
    private final ItemRegistry registry;
    private final ItemFactory items;
    private final org.bukkit.NamespacedKey KEY_REPEL_UNTIL;
    // Simple GUI sessions for item usage
    private final Map<UUID, ItemUseSession> sessions = new HashMap<>();

    private static class ItemUseSession {
        final ItemDef def;
        final int handSlot;
        int stage = 0; // 0 select pokemon, 1 select move slot, 2 select IV stat
        int partyIndex = -1;
        ItemUseSession(ItemDef def, int handSlot) { this.def = def; this.handSlot = handSlot; }
    }

    private static final String TITLE_SELECT_POKEMON = "§8使用道具 - 选择精灵";
    private static final String TITLE_SELECT_MOVE = "§8使用道具 - 选择技能";
    private static final String TITLE_SELECT_IVSTAT = "§8使用道具 - 选择要提升的个体值";


    public ItemUseListener(PokeDemoPlugin plugin, Storage storage, Dex dex, BattleManager battles, ItemRegistry registry, ItemFactory items) {
        this.plugin = plugin;
        this.storage = storage;
        this.dex = dex;
        this.battles = battles;
        this.registry = registry;
        this.items = items;
    
        this.KEY_REPEL_UNTIL = new org.bukkit.NamespacedKey(plugin, "repel_until");
}

    private void msg(Player p, String key, String def) {
        if (p == null) return;
        p.sendMessage(plugin.getLang().ui(key, def));
    }

    private void msgFmt(Player p, String key, String def, Map<String, String> vars) {
        if (p == null) return;
        p.sendMessage(plugin.getLang().uiFmt(key, def, vars));
    }

    // Do NOT ignore cancelled: some environments mark RIGHT_CLICK_AIR as cancelled in edge-cases,
    // but our custom items must still work when right-clicking the air.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() == null) return;
        // Only right-click use for now
        switch (e.getAction()) {
            case RIGHT_CLICK_AIR, RIGHT_CLICK_BLOCK -> {}
            default -> { return; }
        }

        
        // If clicking a Poké Ball loot chest, never open item GUIs (prevents instant close/drop dupes).

        // If clicking a Poké Ball loot chest, never open item GUIs (prevents instant close/drop dupes).
        if (e.getClickedBlock() != null && e.getClickedBlock().getType() == Material.TRAPPED_CHEST) {
            try {
                var st = e.getClickedBlock().getState();
                if (st instanceof org.bukkit.block.TileState tile) {
                    var pdc = tile.getPersistentDataContainer();
                    var keys = new NamespacedKeys(plugin);
                    Byte marker = pdc.get(keys.pokeChestMarkerKey(), org.bukkit.persistence.PersistentDataType.BYTE);
                    if (marker != null && marker == (byte) 1) return;
                }
            } catch (Throwable ignored) {}
        }

Player player = e.getPlayer();
        ItemStack it = player.getInventory().getItemInMainHand();
        if (it == null) return;
        // Control wand should not trigger
        if (items.isControlWand(it)) return;

        String id = items.getItemId(it);
        if (id == null) return;
        ItemDef def = registry.get(id);
        if (def == null) return;

        // Machine placement items are handled by MachineListener.
        // Do NOT cancel interact here, otherwise placement never happens.
        if ("pc_machine".equals(def.id) || "healer_machine".equals(def.id) || "pasture_machine".equals(def.id)
                || "fossil_machine".equals(def.id) || "fossil_analyzer".equals(def.id)
                || "trade_machine".equals(def.id) || "clone_machine".equals(def.id)
                || isPokemonRodId(def.id)) {
            return;
        }

        // Fossils are NOT "use items". They are inputs for the Fossil Machine.
        if ("helix_fossil".equals(def.id) || "dome_fossil".equals(def.id) || "old_amber".equals(def.id)) {
            return;
        }

        // Prevent item usage while in battle (keeps battle engine deterministic)
        if (battles != null && battles.isInBattle(player.getUniqueId())) {
            // In-battle: allow battle items (use from battle GUI); other items are blocked.
            if (def.type == ItemType.BATTLE) {
                player.sendMessage(plugin.getLang().ui("itemuse.battle_use_items_in_gui","§7请在战斗界面点击§b道具§7按钮使用战斗道具。"));
            } else if (def.type == ItemType.BALL) {
                player.sendMessage(plugin.getLang().ui("itemuse.battle_use_capture_in_gui","§7请在战斗界面点击§a捕捉§7按钮来丢球捕捉。"));
            } else {
                player.sendMessage(plugin.getLang().ui("itemuse.battle_item_not_supported","§c战斗中暂不支持使用该道具。"));
            }
            e.setCancelled(true);
            return;
        }

        // Poké Balls should not open the overworld "use item" GUI.
        // They are used from the battle UI (capture flow).
        if (def.type == ItemType.BALL) {
            player.sendMessage(plugin.getLang().ui("itemuse.ball_only_in_battle","§7精灵球只能在战斗中使用：请在战斗界面点击§a捕捉§7按钮来丢球捕捉。"));
            e.setCancelled(true);
            return;
        }

        e.setCancelled(true);

        // Phone / Pokédex
        if ("poke_phone".equals(def.id)) {
            UtilGui.openPhoneMenu(player);
            return;
        }
        if ("pokedex".equals(def.id)) {
            UtilGui.openPokedexList(player, storage, 0, false);
            return;
        }

        // Overworld utility items that don't need target selection
        if ("repel".equals(def.id) || "super_repel".equals(def.id) || "max_repel".equals(def.id)) {
            int seconds = 300;
            try {
                Object v = def.data.get("repel_seconds");
                if (v instanceof Number n) seconds = n.intValue();
            } catch (Exception ignored) {}
            long until = System.currentTimeMillis() + seconds * 1000L;
            player.getPersistentDataContainer().set(KEY_REPEL_UNTIL, org.bukkit.persistence.PersistentDataType.LONG, until);
            // consume one            // safer: consume main hand
            ItemStack handItem = player.getInventory().getItemInMainHand();
            if (handItem != null && items.getItemId(handItem) != null && items.getItemId(handItem).equals(def.id)) {
                handItem.setAmount(handItem.getAmount() - 1);
            } else {
                // fallback: consume by slot
                int slot = player.getInventory().getHeldItemSlot();
                ItemStack si = player.getInventory().getItem(slot);
                if (si != null && def.id.equals(items.getItemId(si))) si.setAmount(si.getAmount() - 1);
            }
            msgFmt(player, "itemuse.repel.used", "§a已使用驱虫喷雾，{min}分钟内不会遭遇野生精灵。",
                    Map.of("min", String.valueOf(Math.max(1, (seconds/60)))));
            return;
        }
        if ("escape_rope".equals(def.id)) {
            org.bukkit.Location spawn = player.getWorld().getSpawnLocation();
            player.teleport(spawn);
            ItemStack handItem = player.getInventory().getItemInMainHand();
            if (handItem != null && def.id.equals(items.getItemId(handItem))) {
                handItem.setAmount(handItem.getAmount() - 1);
            }
            msg(player, "itemuse.escape_rope.used", "§a你使用了逃脱绳，返回了出生点。");
            return;
        }

        // Party-wide items (e.g., Poké Flute)
        if (def.data != null && Boolean.TRUE.equals(parseBoolObj(def.data.get("party_all")))) {
            PlayerProfile prof = storage.getProfile(player.getUniqueId());
            if (prof.party == null || prof.party.isEmpty()) {
                player.sendMessage(plugin.getLang().ui("party.empty","§e你的队伍里没有精灵。"));
                return;
            }
            boolean ok = applyPartyAll(player, prof, def);
            if (ok && def.consumable) {
                ItemStack handItem = player.getInventory().getItemInMainHand();
                if (handItem != null && def.id.equals(items.getItemId(handItem))) {
                    handItem.setAmount(handItem.getAmount() - 1);
                }
                storage.markDirty(player.getUniqueId());
            }
            return;
        }

        PlayerProfile prof = storage.getProfile(player.getUniqueId());
        if (prof.party == null || prof.party.isEmpty()) {
            player.sendMessage(plugin.getLang().ui("party.empty","§e你的队伍里没有精灵。"));
            return;
        }

        // Open target selection GUI; consume only after successful application.
        openPokemonSelect(player, prof, def, player.getInventory().getHeldItemSlot());
        return;
    }

    private boolean applyToParty(Player player, PlayerProfile prof, ItemDef def) {
        return switch (def.type) {
            case MEDICINE -> useMedicine(player, prof, def);
            case STATUS_CURE -> useStatusCure(player, prof, def);
            case REVIVE -> useRevive(player, prof, def);
            case BALL -> {
                player.sendMessage(plugin.getLang().ui("itemuse.battle_use_capture_in_gui","§7请在战斗界面点击§a捕捉§7按钮来丢球捕捉。"));
                yield false;
            }
            default -> {
                msgFmt(player, "itemuse.not_implemented", "§e该道具暂未实现：{id}", Map.of("id", String.valueOf(def.id)));
                yield false;
            }
        };
    }

    /** Apply an item to the whole party without opening target GUI (e.g., Poké Flute). */
    private boolean applyPartyAll(Player player, PlayerProfile prof, ItemDef def) {
        if (def.type == ItemType.STATUS_CURE) {
            String cure = String.valueOf(def.data.getOrDefault("cure", ""));
            if (cure == null || cure.isBlank()) return false;
            int changed = 0;
            for (PokemonInstance p : prof.party) {
                if (p == null) continue;
                if (p.currentHp <= 0) continue;
                String st = p.status == null ? "none" : p.status;
                boolean ok = false;
                if ("all".equalsIgnoreCase(cure)) ok = !"none".equalsIgnoreCase(st);
                else if ("sleep".equalsIgnoreCase(cure)) ok = "sleep".equalsIgnoreCase(st);
                else if ("poison".equalsIgnoreCase(cure)) ok = "poison".equalsIgnoreCase(st);
                else if ("burn".equalsIgnoreCase(cure)) ok = "burn".equalsIgnoreCase(st);
                else if ("paralysis".equalsIgnoreCase(cure)) ok = ("paralyze".equalsIgnoreCase(st) || "paralysis".equalsIgnoreCase(st));
                else if ("freeze".equalsIgnoreCase(cure)) ok = ("freeze".equalsIgnoreCase(st) || "frozen".equalsIgnoreCase(st));
                if (!ok) continue;
                p.status = "none";
                changed++;
            }
            if (changed <= 0) {
                msg(player, "itemuse.no_eligible_party", "§7你的队伍里没有需要使用该道具的精灵。");
                return false;
            }
            msgFmt(player, "itemuse.party_all_cured", "§a使用了 {item}：§a已治愈 {n} 只精灵的异常状态。",
                    Map.of("item", itemName(def), "n", String.valueOf(changed)));
            return true;
        }
        msgFmt(player, "itemuse.party_all_not_supported", "§e该道具暂不支持对全队使用：{id}", Map.of("id", String.valueOf(def.id)));
        return false;
    }

    private boolean useMedicine(Player player, PlayerProfile prof, ItemDef def) {
        Boolean full = parseBoolObj(def.data.get("heal_full"));
        Integer heal = parseIntObj(def.data.get("heal"));
        if ((full == null || !full) && (heal == null || heal <= 0)) {
            player.sendMessage("§e该回复道具参数异常。");
            return false;
        }
        PokemonInstance p = firstAlive(prof);
        if (p == null) {
            player.sendMessage("§e你的队伍里没有可回复的精灵（都已倒下）。");
            return false;
        }
        Species s = dex.getSpecies(p.speciesId);
        if (s == null) {
            player.sendMessage("§e精灵数据异常：未知物种。" );
            return false;
        }
        int max = p.maxHp(s);
        if (p.currentHp >= max) {
            player.sendMessage("§7" + displayName(p, s) + " 已经是满血了。");
            return false;
        }
        int before = p.currentHp;
        if (full != null && full) {
            p.currentHp = max;
        } else {
            p.currentHp = Math.min(max, p.currentHp + heal);
        }
        player.sendMessage("§a使用了 " + itemName(def) + "：§f" + displayName(p, s) + " §a回复 " + (p.currentHp - before) + " HP（" + p.currentHp + "/" + max + "）");
        return true;
    }

    private boolean useStatusCure(Player player, PlayerProfile prof, ItemDef def) {
        String cure = String.valueOf(def.data.getOrDefault("cure", ""));
        if (cure.isBlank()) {
            player.sendMessage("§e该状态药参数异常。");
            return false;
        }
        PokemonInstance target = null;
        for (PokemonInstance p : prof.party) {
            if (p == null) continue;
            if (p.currentHp <= 0) continue;
            if (p.status == null) continue;
            boolean needs = false;
            if (cure.equalsIgnoreCase("all") || cure.equalsIgnoreCase("any")) {
                needs = !p.status.equalsIgnoreCase("none");
            } else if (cure.equalsIgnoreCase("paralysis")) {
                needs = p.status.equalsIgnoreCase("paralyze") || p.status.equalsIgnoreCase("paralysis");
            } else if (cure.equalsIgnoreCase("freeze")) {
                needs = p.status.equalsIgnoreCase("freeze") || p.status.equalsIgnoreCase("frozen");
            } else {
                needs = p.status.equalsIgnoreCase(cure);
            }
            if (needs) {
                target = p;
                break;
            }
        }
        if (target == null) {
            player.sendMessage("§7队伍中没有需要治疗该异常状态的精灵。");
            return false;
        }
        Species s = dex.getSpecies(target.speciesId);
        if (s == null) {
            player.sendMessage("§e精灵数据异常：未知物种。" );
            return false;
        }
        target.status = "none";
        player.sendMessage("§a使用了 " + itemName(def) + "：§f" + displayName(target, s) + " §a异常状态已治愈。");
        return true;
    }

    private boolean useRevive(Player player, PlayerProfile prof, ItemDef def) {
        Double ratio = parseDoubleObj(def.data.get("revive"));
        if (ratio == null || ratio <= 0) ratio = 0.5;
        PokemonInstance target = null;
        for (PokemonInstance p : prof.party) {
            if (p == null) continue;
            if (p.currentHp <= 0) { target = p; break; }
        }
        if (target == null) {
            player.sendMessage("§7队伍中没有倒下的精灵。");
            return false;
        }
        Species s = dex.getSpecies(target.speciesId);
        if (s == null) {
            player.sendMessage("§e精灵数据异常：未知物种。" );
            return false;
        }
        int max = target.maxHp(s);
        int hp = Math.max(1, (int)Math.floor(max * ratio));
        target.currentHp = hp;
        target.status = "none";
        player.sendMessage("§a使用了 " + itemName(def) + "：§f" + displayName(target, s) + " §a复活（" + hp + "/" + max + "）。");
        return true;
    }

    private PokemonInstance firstAlive(PlayerProfile prof) {
        for (PokemonInstance p : prof.party) {
            if (p == null) continue;
            if (p.currentHp > 0) return p;
        }
        return null;
    }

    private String displayName(PokemonInstance p, Species s) {
        if (p.nickname != null && !p.nickname.isBlank()) return p.nickname;
        // Prefer current lang translation, falling back to stored name.
        LangManager lang = plugin.getLang();
        if (lang != null) {
            return lang.species(p.speciesId, p.speciesName);
        }
        if (p.speciesName != null && !p.speciesName.isBlank()) return p.speciesName;
        return s.name();
    }

    private String itemName(ItemDef def) {
        LangManager lang = plugin.getLang();
        String name = (lang != null) ? lang.item(def.id, def.id) : def.id;
        return "§f" + name;
    }

    private Integer parseIntObj(Object o) {
        if (o == null) return null;
        if (o instanceof Integer i) return i;
        if (o instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(o)); } catch (Exception ignored) { return null; }
    }

    private Boolean parseBoolObj(Object o) {
        if (o == null) return null;
        if (o instanceof Boolean b) return b;
        String s = String.valueOf(o);
        if (s.equalsIgnoreCase("true") || s.equals("1")) return true;
        if (s.equalsIgnoreCase("false") || s.equals("0")) return false;
        return null;
    }

    private Double parseDoubleObj(Object o) {
        if (o == null) return null;
        if (o instanceof Double d) return d;
        if (o instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(String.valueOf(o)); } catch (Exception ignored) { return null; }
    }

    private void openPokemonSelect(Player player, PlayerProfile prof, ItemDef def, int handSlot) {
        ItemUseSession s = new ItemUseSession(def, handSlot);
        sessions.put(player.getUniqueId(), s);

        Inventory inv = Bukkit.createInventory(player, 9, UtilGui.title(TITLE_SELECT_POKEMON));
        for (int i = 0; i < Math.min(6, prof.party.size()); i++) {
            PokemonInstance p = prof.party.get(i);
            ItemStack icon = UtilGui.pokemonIcon(p);
            ItemMeta meta = icon.getItemMeta();
            if (meta != null) {
                Species sp = dex.getSpecies(p.speciesId);
                int max = sp == null ? 0 : p.maxHp(sp);
                List<String> lore = new ArrayList<>();
                lore.add("§7HP: §f" + p.currentHp + "/" + (max <= 0 ? "?" : String.valueOf(max)));
                lore.add("§7状态: §f" + (p.status == null ? "none" : p.status));
                lore.add("§e点击选择");
                if (p.speciesId != null && !p.speciesId.isBlank()) lore.add("§0species:" + p.speciesId.toLowerCase(java.util.Locale.ROOT));
                meta.setDisplayName("§a" + displayName(p, sp));
                meta.setLore(lore);
                icon.setItemMeta(meta);
            }
            inv.setItem(i, icon);
        }
        // filler + cancel
        inv.setItem(8, UtilGui.button(Material.BARRIER, "§c取消", List.of("§7关闭")));
        player.openInventory(inv);
    }

    private void openMoveSelect(Player player, PlayerProfile prof, ItemUseSession session) {
        PokemonInstance p = prof.party.get(session.partyIndex);
        Inventory inv = Bukkit.createInventory(player, 9, UtilGui.title(TITLE_SELECT_MOVE));
        for (int i = 0; i < 4; i++) {
            ItemStack it = new ItemStack(Material.PAPER);
            ItemMeta meta = it.getItemMeta();
            if (meta != null) {
                String name = "空";
                String ppTxt = "";
                if (p.moves != null && i < p.moves.size()) {
                    MoveSlot ms = p.moves.get(i);
                    if (ms != null && ms.moveId != null && !ms.moveId.isEmpty()) {
                        Move mv = dex.getMove(ms.moveId);
                        name = mv != null ? mv.name() : ms.moveId;
                        ppTxt = "§7PP: §f" + ms.pp + "/" + ms.maxPp;
                    }
                }
                meta.setDisplayName("§a" + name);
                meta.setLore(List.of(ppTxt, "§e点击选择"));
                it.setItemMeta(meta);
            }
            inv.setItem(i, it);
        }
        inv.setItem(8, UtilGui.button(Material.BARRIER, "§c取消", List.of("§7关闭")));
        player.openInventory(inv);
    }

    private void openIvStatSelect(Player player, PlayerProfile prof, ItemUseSession session) {
        // stage 2: choose which IV to max (silver bottle cap)
        session.stage = 2;
        Inventory inv = Bukkit.createInventory(player, 9, UtilGui.title(TITLE_SELECT_IVSTAT));
        String[] stats = new String[]{"hp","atk","def","spa","spd","spe"};
        String[] names = new String[]{"HP","攻击","防御","特攻","特防","速度"};
        for (int i = 0; i < stats.length; i++) {
            ItemStack it = new ItemStack(Material.IRON_NUGGET);
            ItemMeta meta = it.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§a" + names[i]);
                meta.setLore(List.of("§7点击把该项 IV 提升到 31"));
                it.setItemMeta(meta);
            }
            inv.setItem(i, it);
        }
        inv.setItem(8, UtilGui.button(Material.BARRIER, "§c取消", List.of("§7关闭")));
        player.openInventory(inv);
    }


    @EventHandler
    public void onInvClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        ItemUseSession session = sessions.get(player.getUniqueId());
        if (session == null) return;
        String title = e.getView().getTitle();
        if (!TITLE_SELECT_POKEMON.equals(title) && !TITLE_SELECT_MOVE.equals(title) && !TITLE_SELECT_IVSTAT.equals(title)) return;

        e.setCancelled(true);

        if (e.getRawSlot() == 8) { // cancel
            sessions.remove(player.getUniqueId());
            player.closeInventory();
            return;
        }

        PlayerProfile prof = storage.getProfile(player.getUniqueId());
        if (prof == null || prof.party == null || prof.party.isEmpty()) {
            sessions.remove(player.getUniqueId());
            player.closeInventory();
            return;
        }

        if (TITLE_SELECT_POKEMON.equals(title)) {
            int slot = e.getRawSlot();
            if (slot < 0 || slot >= Math.min(6, prof.party.size())) return;
            session.partyIndex = slot;

            // Silver bottle cap: need stat selection
            if (session.def.type == ItemType.MISC) {
                try {
                    Object act = session.def.data == null ? null : session.def.data.get("action");
                    if (act != null && String.valueOf(act).equals("iv_one31")) {
                        openIvStatSelect(player, prof, session);
                        return;
                    }
                } catch (Exception ignored) {}
            }

            // For elixir-type (all moves) we can apply immediately.
            if (session.def.type == ItemType.PP_RESTORE) {
                if (session.def.data.containsKey("pp_all") || session.def.data.containsKey("pp_all_full")) {
                    if (applyAndConsume(player, prof, session, -1)) {
                        player.closeInventory();
                        sessions.remove(player.getUniqueId());
                    }
                    return;
                }
                // otherwise need move selection
                openMoveSelect(player, prof, session);
                return;
            }

            if (applyAndConsume(player, prof, session, -1)) {
                player.closeInventory();
                sessions.remove(player.getUniqueId());
            }
            return;
        }

        
        if (TITLE_SELECT_IVSTAT.equals(title)) {
            int slot = e.getRawSlot();
            if (slot < 0 || slot > 5) return;
            // use moveIndex parameter as stat index
            if (applyAndConsume(player, prof, session, slot)) {
                player.closeInventory();
                sessions.remove(player.getUniqueId());
            }
            return;
        }

if (TITLE_SELECT_MOVE.equals(title)) {
            int slot = e.getRawSlot();
            if (slot < 0 || slot > 3) return;
            if (applyAndConsume(player, prof, session, slot)) {
                player.closeInventory();
                sessions.remove(player.getUniqueId());
            }
        }
    }

    @EventHandler
    public void onInvClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player player)) return;
        UUID uid = player.getUniqueId();
        ItemUseSession session = sessions.get(uid);
        if (session == null) return;
        String title = e.getView().getTitle();
        if (!TITLE_SELECT_POKEMON.equals(title) && !TITLE_SELECT_MOVE.equals(title) && !TITLE_SELECT_IVSTAT.equals(title)) return;

        // Delay cleanup to allow switching between selection inventories.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try {
                String now = player.getOpenInventory() != null ? player.getOpenInventory().getTitle() : "";
                if (!TITLE_SELECT_POKEMON.equals(now) && !TITLE_SELECT_MOVE.equals(now) && !TITLE_SELECT_IVSTAT.equals(now)) {
                    sessions.remove(uid);
                }
            } catch (Exception ignored) {
                sessions.remove(uid);
            }
        }, 2L);
    }

    private boolean applyAndConsume(Player player, PlayerProfile prof, ItemUseSession session, int moveIndex) {
        // Verify the item still in hand slot and still matches id
        ItemStack inHand = player.getInventory().getItem(session.handSlot);
        if (inHand == null || inHand.getAmount() <= 0) {
            player.sendMessage("§e你手上的道具不存在了。");
            return false;
        }
        String id = items.getItemId(inHand);
        if (id == null || !id.equals(session.def.id)) {
            player.sendMessage("§e你手上的道具已变化，已取消使用。");
            return false;
        }

        boolean ok = applyToSelected(player, prof, session.def, session.partyIndex, moveIndex);
        if (!ok) return false;

        // TM items are consumed only AFTER the move is actually learned (to allow ESC cancel without loss).
        // See PendingItemConsumeManager.
        if (session.def.consumable && session.def.type != ItemType.TM) {
            inHand.setAmount(inHand.getAmount() - 1);
        }
        storage.markDirty(player.getUniqueId());
        return true;
    }

    private boolean applyToSelected(Player player, PlayerProfile prof, ItemDef def, int partyIndex, int moveIndex) {
        if (partyIndex < 0 || partyIndex >= prof.party.size()) return false;
        PokemonInstance p = prof.party.get(partyIndex);
        Species s = dex.getSpecies(p.speciesId);

        return switch (def.type) {
            case MEDICINE -> applyMedicine(player, p, s, def);
            case STATUS_CURE -> applyStatusCure(player, p, s, def);
            case REVIVE -> applyRevive(player, p, s, def);
            case PP_RESTORE -> applyPpRestore(player, p, s, def, moveIndex);
            case VITAMIN -> applyVitamin(player, p, s, def);
            case MISC -> applyMisc(player, prof, p, s, def, moveIndex);
            case TM -> applyTm(player, p, s, def);
            default -> {
                msgFmt(player, "itemuse.not_implemented", "§e该道具暂未实现：{id}", Map.of("id", String.valueOf(def.id)));
                yield false;
            }
        };
    }

    private boolean applyVitamin(Player player, PokemonInstance p, Species s, ItemDef def) {
        if (s == null) { player.sendMessage("§e精灵数据异常：未知物种。"); return false; }
        if (p.currentHp <= 0) { player.sendMessage("§7" + displayName(p, s) + " 已经倒下了。"); return false; }

        String stat = def.data == null ? null : String.valueOf(def.data.getOrDefault("vit_stat", ""));
        int amt = 10;
        try {
            Object v = def.data == null ? null : def.data.get("vit_amount");
            if (v instanceof Number n) amt = n.intValue();
        } catch (Exception ignored) {}
        if (stat == null || stat.isBlank()) return false;

        // Current EVs
        int before = getEvByStat(p, stat);
        int totalBefore = p.evHp + p.evAtk + p.evDef + p.evSpa + p.evSpd + p.evSpe;

        // Caps: 255 per stat, 510 total (modernized EV system)
        int maxStat = 255;
        int maxTotal = 510;
        if (before >= maxStat || totalBefore >= maxTotal) {
            player.sendMessage("§7" + displayName(p, s) + " 的努力值已经无法再提升了。");
            return false;
        }
        int canAdd = Math.min(amt, maxStat - before);
        canAdd = Math.min(canAdd, maxTotal - totalBefore);
        if (canAdd <= 0) {
            player.sendMessage("§7" + displayName(p, s) + " 的努力值已经无法再提升了。");
            return false;
        }

        setEvByStat(p, stat, before + canAdd);

        // Recompute max HP if needed; keep HP ratio (same approach as rare candy)
        int oldMax = Math.max(1, p.maxHp(s));
        double ratio = p.currentHp / (double) oldMax;
        int newMax = Math.max(1, p.maxHp(s));
        p.currentHp = Math.max(1, (int)Math.round(newMax * ratio));
        if (p.currentHp > newMax) p.currentHp = newMax;

        player.sendMessage("§a使用了 " + itemName(def) + "：§f" + displayName(p, s) + " §a的努力值提升了！（" + prettyEvStat(stat) + " +" + canAdd + "）");
        return true;
    }

    private int getEvByStat(PokemonInstance p, String stat) {
        return switch (stat.toLowerCase()) {
            case "hp" -> p.evHp;
            case "atk" -> p.evAtk;
            case "def" -> p.evDef;
            case "spa" -> p.evSpa;
            case "spd" -> p.evSpd;
            case "spe" -> p.evSpe;
            default -> 0;
        };
    }
    private void setEvByStat(PokemonInstance p, String stat, int v) {
        v = Util.clamp(v, 0, 255);
        switch (stat.toLowerCase()) {
            case "hp" -> p.evHp = v;
            case "atk" -> p.evAtk = v;
            case "def" -> p.evDef = v;
            case "spa" -> p.evSpa = v;
            case "spd" -> p.evSpd = v;
            case "spe" -> p.evSpe = v;
        }
    }
    private String prettyEvStat(String stat) {
        return switch (stat.toLowerCase()) {
            case "hp" -> "HP";
            case "atk" -> "攻击";
            case "def" -> "防御";
            case "spa" -> "特攻";
            case "spd" -> "特防";
            case "spe" -> "速度";
            default -> stat;
        };
    }

    private boolean applyTm(Player player, PokemonInstance p, Species s, ItemDef def) {
        if (s == null) { player.sendMessage("§e精灵数据异常：未知物种。"); return false; }
        String moveId = plugin.getTmManager() == null ? null : plugin.getTmManager().moveForTm(def.id);
        if (moveId == null) {
            player.sendMessage("§e该学习机/秘传机未映射技能：" + def.id);
            return false;
        }

        // Debounce: if this player already has a pending item-learn for the same move, don't enqueue duplicates.
        try {
            PendingItemConsumeManager pending = plugin.getPendingItemConsumeManager();
            if (pending != null && pending.hasPending(player.getUniqueId())) {
                if (p.pendingMoveLearns != null && !p.pendingMoveLearns.isEmpty() && p.pendingMoveLearns.get(0).equalsIgnoreCase(moveId)) {
                    Bukkit.getScheduler().runTask(plugin, () -> UtilGui.openMoveLearn(player, storage, p.uuid, true));
                    return true;
                }
            }
        } catch (Throwable ignored) {}
        // Compatibility check using Showdown learnsets (Gen1 filter). If learnsets missing, try lazy-load.
        if (plugin.getTmManager() != null) {
            boolean loaded = plugin.getTmManager().ensureLoaded();
            if (!loaded) {
                player.sendMessage("§c学习机数据缺失：§7请把 §flearnsets.ts§7（Pokemon Showdown 源码）或 §ftm_compat_gen1.json§7 放到 §fplugins/PokeDemo/moves_raw/§7，然后重试。\n§7（你的服务器无法访问 raw.githubusercontent.com 时必须用离线文件）");
                return false;
            }
            if (!plugin.getTmManager().canLearnTm(s.id(), moveId)) {
                player.sendMessage("§7" + displayName(p, s) + " 无法学习 " + plugin.getLang().move(moveId, null) + "。" );
                return false;
            }
        }
        if (!dex.isMoveAllowed(moveId)) {
            player.sendMessage("§7当前数据集中不支持该技能：" + moveId);
            return false;
        }
        if (p.knowsMove(moveId)) {
            player.sendMessage("§7" + displayName(p, s) + " 已经学会了 " + plugin.getLang().move(moveId, null) + "。" );
            return false;
        }

        // Use LearnsetManager pipeline (supports pending learn + forget GUI)
        boolean learnedNow;
        try {
            learnedNow = plugin.getLearnsetManager().tryLearnMoveFromItem(player.getUniqueId(), p, moveId);
        } catch (Throwable t) {
            player.sendMessage("§c学习失败：" + t.getMessage());
            return false;
        }

        // Track deferred consumption for TMs: only consume after the move is actually learned.
        if (def.consumable && plugin.getPendingItemConsumeManager() != null) {
            plugin.getPendingItemConsumeManager().setPending(player.getUniqueId(), def.id, moveId, true);
        }

        // If queued, open learn GUI next tick
        if (!learnedNow && p.pendingMoveLearns != null && !p.pendingMoveLearns.isEmpty()) {
            Bukkit.getScheduler().runTask(plugin, () -> UtilGui.openMoveLearn(player, storage, p.uuid, true));
        }

        // If learned immediately, consume now (TMs only)
        if (learnedNow && def.consumable && plugin.getPendingItemConsumeManager() != null) {
            plugin.getPendingItemConsumeManager().onMoveLearnResolved(player.getUniqueId(), moveId, true);
        }

        player.sendMessage("§a使用了 " + itemName(def) + "：" + displayName(p, s) + " 尝试学习 " + plugin.getLang().move(moveId, null) + "。" );
        return true;
    }

    
    private boolean applyMisc(Player player, PlayerProfile prof, PokemonInstance p, Species s, ItemDef def, int extraIndex) {
        // Rare Candy: level +1 (up to 100), adjust total EXP to minimum of new level, trigger learnset/evolution hints
        if ("rare_candy".equals(def.id)) {
            if (s == null) { player.sendMessage("§e精灵数据异常：未知物种。"); return false; }
            int before = p.level;
            if (before >= 100) { player.sendMessage("§e已经达到最高等级。"); return false; }
            int after = before + 1;

            // Keep HP ratio when max HP changes
            int oldMax = Math.max(1, p.maxHp(s));
            double ratio = p.currentHp / (double) oldMax;

            p.level = after;
            p.addFriendship(2);
            p.totalExp = ExpCurve.totalExpAtLevel(s.expGroup(), after);

            // Recompute HP using new level stats
            int newMax = Math.max(1, p.maxHp(s));
            p.currentHp = Math.max(1, (int) Math.round(newMax * ratio));
            if (p.currentHp > newMax) p.currentHp = newMax;

            storage.markDirty(player.getUniqueId());

            player.sendMessage("§a" + displayName(p, s) + " §7升级到了 §eLv." + after + "§7！");

            // Learnset + evolution hint
            try {
                plugin.getLearnsetManager().onLevelUp(player.getUniqueId(), p, s, before, after);
            } catch (Throwable ignored) { }
            try {
                plugin.getEvolutionManager().notifyIfCanEvolve(player.getUniqueId(), p);
            } catch (Throwable ignored) { }
            return true;
        }

        if ("relic_coin".equals(def.id)) {
            String sid = p.speciesId == null ? "" : p.speciesId.toLowerCase(java.util.Locale.ROOT);
            if (!sid.contains("gimmighoul")) {
                player.sendMessage("§e古代硬币只能对索财灵使用。");
                return false;
            }
            plugin.getEvolutionManager().addGimmighoulCoins(player.getUniqueId(), p, 1);
            player.sendMessage("§a索财灵的硬币进度：§e" + Math.min(999, p.gimmighoulCoins) + "§7/999");
            try { plugin.getEvolutionManager().notifyIfCanEvolve(player.getUniqueId(), p); } catch (Throwable ignored) {}
            return true;
        }

        // Evolution stones: attempt item evolution for Gen1 (hardcoded mapping for now)
        if (def.data != null && def.data.containsKey("evo_item")) {
            Object v = def.data.get("evo_item");
            String itemId = v == null ? def.id : String.valueOf(v);
            boolean ok = plugin.getEvolutionManager().tryEvolveWithItem(player, p, itemId);
            if (!ok) {
                player.sendMessage("§e该进化石对这只精灵没有效果。");
            }
            return ok;
        }



        // Berries used on Pokémon (targeted): EV-reducing berries (Pomeg/Kelpsy/Qualot/Hondew/Grepa/Tamato)
        if (def.data != null && def.data.containsKey("berry")) {
            String berry = String.valueOf(def.data.get("berry")).toLowerCase(java.util.Locale.ROOT);
            String stat = switch (berry) {
                case "pomeg" -> "hp";
                case "kelpsy" -> "atk";
                case "qualot" -> "def";
                case "hondew" -> "spa";
                case "grepa" -> "spd";
                case "tamato" -> "spe";
                default -> null;
            };
            if (stat != null) {
                if (s == null) { player.sendMessage("§e精灵数据异常：未知物种。"); return false; }
                if (p.currentHp <= 0) { player.sendMessage("§7" + displayName(p, s) + " 已经倒下了。"); return false; }

                int before = getEvByStat(p, stat);
                if (before <= 0) {
                    player.sendMessage("§7" + displayName(p, s) + " 的" + prettyEvStat(stat) + "努力值已经是 0。");
                    return false;
                }
                int dec = 10;
                int after = Math.max(0, before - dec);
                setEvByStat(p, stat, after);

                // If HP EV reduced, max HP may shrink; clamp current HP but keep ratio roughly
                int oldMax = Math.max(1, p.maxHp(s));
                int newMax = Math.max(1, p.maxHp(s));
                if (p.currentHp > newMax) p.currentHp = newMax;

                storage.markDirty(player.getUniqueId());
                player.sendMessage("§a使用了 " + itemName(def) + "：§f" + displayName(p, s) + " §a的" + prettyEvStat(stat) + "努力值下降了！（" + before + " -> " + after + "）");
                return true;
            }
        }
        

        // Ability Capsule / Patch / Mints / Bottle Caps
        if (def.data != null && def.data.containsKey("action")) {
            String action = String.valueOf(def.data.get("action"));

            // Nature mint
            if ("set_nature".equals(action)) {
                if (s == null) { player.sendMessage("§e精灵数据异常：未知物种。"); return false; }
                if (p.currentHp <= 0) { player.sendMessage("§7" + displayName(p, s) + " 已经倒下了。"); return false; }
                String nat = String.valueOf(def.data.getOrDefault("nature", "HARDY"));
                Nature n = Nature.fromId(nat);
                String before = p.nature == null ? "HARDY" : p.nature;
                if (before.equalsIgnoreCase(n.name())) {
                    player.sendMessage("§7" + displayName(p, s) + " 已经是该性格了。");
                    return false;
                }
                int oldMax = Math.max(1, p.maxHp(s));
                double ratio = p.currentHp / (double) oldMax;
                p.nature = n.name();
                int newMax = Math.max(1, p.maxHp(s));
                p.currentHp = Math.max(1, (int)Math.round(newMax * ratio));
                if (p.currentHp > newMax) p.currentHp = newMax;
                player.sendMessage("§a使用了 " + itemName(def) + "：§f" + displayName(p, s) + " §a的性格变为 §e" + n.zhName + "§a！");
                return true;
            }

            // Ability Capsule: swap between normal abilities only (no hidden)
            if ("ability_capsule".equals(action)) {
                if (s == null) { player.sendMessage("§e精灵数据异常：未知物种。"); return false; }
                if (p.currentHp <= 0) { player.sendMessage("§7" + displayName(p, s) + " 已经倒下了。"); return false; }
                java.util.List<String> normals = dex.getNormalAbilityIds(p.speciesId);
                if (normals == null || normals.size() < 2) {
                    player.sendMessage("§7这只精灵没有可切换的两种特性。");
                    return false;
                }
                String hidden = dex.getHiddenAbilityId(p.speciesId);
                if (hidden != null && hidden.equalsIgnoreCase(p.abilityId)) {
                    player.sendMessage("§7梦特无法通过特性胶囊切换。");
                    return false;
                }
                String cur = p.abilityId == null ? "" : p.abilityId;
                String next = null;
                for (String ab : normals) {
                    if (!ab.equalsIgnoreCase(cur)) { next = ab; break; }
                }
                if (next == null) {
                    player.sendMessage("§7这只精灵没有可切换的两种特性。");
                    return false;
                }
                p.abilityId = next;
                player.sendMessage("§a使用了 " + itemName(def) + "：§f" + displayName(p, s) + " §a的特性变更为 §e" + AbilityEffects.displayName(next) + "§a！");
                return true;
            }

            // Ability Patch: set to hidden ability
            if ("ability_patch".equals(action)) {
                if (s == null) { player.sendMessage("§e精灵数据异常：未知物种。"); return false; }
                if (p.currentHp <= 0) { player.sendMessage("§7" + displayName(p, s) + " 已经倒下了。"); return false; }
                String hidden = dex.getHiddenAbilityId(p.speciesId);
                if (hidden == null || hidden.isBlank()) {
                    player.sendMessage("§7这只精灵没有梦特。");
                    return false;
                }
                if (hidden.equalsIgnoreCase(p.abilityId)) {
                    player.sendMessage("§7" + displayName(p, s) + " 已经是梦特了。");
                    return false;
                }
                p.abilityId = hidden;
                player.sendMessage("§a使用了 " + itemName(def) + "：§f" + displayName(p, s) + " §a的特性变更为 §e" + AbilityEffects.displayName(hidden) + "§a（梦特）！");
                return true;
            }

            // Bottle caps (IV to 31)
            if ("iv_all31".equals(action)) {
                if (s == null) { player.sendMessage("§e精灵数据异常：未知物种。"); return false; }
                if (p.currentHp <= 0) { player.sendMessage("§7" + displayName(p, s) + " 已经倒下了。"); return false; }
                int oldMax = Math.max(1, p.maxHp(s));
                double ratio = p.currentHp / (double) oldMax;
                p.ivHp = p.ivAtk = p.ivDef = p.ivSpa = p.ivSpd = p.ivSpe = 31;
                int newMax = Math.max(1, p.maxHp(s));
                p.currentHp = Math.max(1, (int)Math.round(newMax * ratio));
                if (p.currentHp > newMax) p.currentHp = newMax;
                player.sendMessage("§a使用了 " + itemName(def) + "：§f" + displayName(p, s) + " §a的个体值全部提升到 §e31§a！");
                return true;
            }
            if ("iv_one31".equals(action)) {
                if (s == null) { player.sendMessage("§e精灵数据异常：未知物种。"); return false; }
                if (p.currentHp <= 0) { player.sendMessage("§7" + displayName(p, s) + " 已经倒下了。"); return false; }
                int statIdx = extraIndex;
                if (statIdx < 0 || statIdx > 5) {
                    // Should have been selected via GUI
                    player.sendMessage("§e请选择要提升的个体值。");
                    return false;
                }
                int oldMax = Math.max(1, p.maxHp(s));
                double ratio = p.currentHp / (double) oldMax;
                String name;
                switch (statIdx) {
                    case 0 -> { p.ivHp = 31; name = "HP"; }
                    case 1 -> { p.ivAtk = 31; name = "攻击"; }
                    case 2 -> { p.ivDef = 31; name = "防御"; }
                    case 3 -> { p.ivSpa = 31; name = "特攻"; }
                    case 4 -> { p.ivSpd = 31; name = "特防"; }
                    default -> { p.ivSpe = 31; name = "速度"; }
                }
                int newMax = Math.max(1, p.maxHp(s));
                p.currentHp = Math.max(1, (int)Math.round(newMax * ratio));
                if (p.currentHp > newMax) p.currentHp = newMax;
                player.sendMessage("§a使用了 " + itemName(def) + "：§f" + displayName(p, s) + " §a的" + name + "个体值提升到 §e31§a！");
                return true;
            }
        }
// Existing misc items (repel/escape rope) are handled in onInteract (no pokemon target) - so here: no-op
        msgFmt(player, "itemuse.not_implemented", "§e该道具暂未实现：{id}", Map.of("id", String.valueOf(def.id)));
        return false;
    }

private boolean applyMedicine(Player player, PokemonInstance p, Species s, ItemDef def) {
        Boolean full = parseBoolObj(def.data.get("heal_full"));
        Boolean cureAll = parseBoolObj(def.data.get("cure_all"));
        Integer heal = parseIntObj(def.data.get("heal"));
        if (s == null) { player.sendMessage("§e精灵数据异常：未知物种。"); return false; }
        int max = p.maxHp(s);
        if (p.currentHp <= 0) { player.sendMessage("§7" + displayName(p, s) + " 已经倒下了，无法直接使用该回复道具。"); return false; }

        boolean changed = false;
        int beforeHp = p.currentHp;
        if (full != null && full) {
            if (p.currentHp < max) { p.currentHp = max; changed = true; }
        } else if (heal != null && heal > 0) {
            if (p.currentHp < max) { p.currentHp = Math.min(max, p.currentHp + heal); changed = true; }
        }
        if (cureAll != null && cureAll) {
            if (p.status != null && !"none".equalsIgnoreCase(p.status)) {
                p.status = "none";
                changed = true;
            }
        }
        if (!changed) {
            player.sendMessage("§7" + displayName(p, s) + " 已经是满血/无需治疗。");
            return false;
        }
        player.sendMessage("§a使用了 " + itemName(def) + "：§f" + displayName(p, s) + " §aHP " + beforeHp + " -> " + p.currentHp + "（" + p.currentHp + "/" + max + "）");
        return true;
    }

    private boolean applyStatusCure(Player player, PokemonInstance p, Species s, ItemDef def) {
        if (s == null) { player.sendMessage("§e精灵数据异常：未知物种。"); return false; }
        String cure = String.valueOf(def.data.getOrDefault("cure", ""));
        if (p.currentHp <= 0) { player.sendMessage("§7" + displayName(p, s) + " 已经倒下了。"); return false; }
        String st = p.status == null ? "none" : p.status;
        if ("none".equalsIgnoreCase(st)) {
            player.sendMessage("§7" + displayName(p, s) + " 没有异常状态。");
            return false;
        }
        boolean ok = false;
        if ("all".equalsIgnoreCase(cure)) ok = true;
        else if ("poison".equalsIgnoreCase(cure) && "poison".equalsIgnoreCase(st)) ok = true;
        else if ("burn".equalsIgnoreCase(cure) && "burn".equalsIgnoreCase(st)) ok = true;
        else if ("sleep".equalsIgnoreCase(cure) && "sleep".equalsIgnoreCase(st)) ok = true;
        else if ("paralysis".equalsIgnoreCase(cure) && ("paralyze".equalsIgnoreCase(st) || "paralysis".equalsIgnoreCase(st))) ok = true;
        else if ("freeze".equalsIgnoreCase(cure) && ("freeze".equalsIgnoreCase(st) || "frozen".equalsIgnoreCase(st))) ok = true;

        if (!ok) {
            player.sendMessage("§7该异常状态无法用 " + itemName(def) + " 治疗。");
            return false;
        }
        p.status = "none";
        player.sendMessage("§a使用了 " + itemName(def) + "：§f" + displayName(p, s) + " §a异常状态已治愈。");
        return true;
    }

    private boolean applyRevive(Player player, PokemonInstance p, Species s, ItemDef def) {
        if (s == null) { player.sendMessage("§e精灵数据异常：未知物种。"); return false; }
        if (p.currentHp > 0) {
            player.sendMessage("§7" + displayName(p, s) + " 还没有倒下。");
            return false;
        }
        Double ratio = parseDoubleObj(def.data.get("revive"));
        if (ratio == null || ratio <= 0) ratio = 0.5;
        int max = p.maxHp(s);
        p.currentHp = Math.max(1, (int)Math.round(max * ratio));
        p.status = "none";
        player.sendMessage("§a使用了 " + itemName(def) + "：§f" + displayName(p, s) + " §a已复活（" + p.currentHp + "/" + max + "）。");
        return true;
    }

    private boolean applyPpRestore(Player player, PokemonInstance p, Species s, ItemDef def, int moveIndex) {
        if (s == null) { player.sendMessage("§e精灵数据异常：未知物种。"); return false; }
        if (p.currentHp <= 0) { player.sendMessage("§7" + displayName(p, s) + " 已经倒下了。"); return false; }
        if (p.moves == null || p.moves.isEmpty()) { player.sendMessage("§e该精灵没有技能。"); return false; }

        if (def.data.containsKey("pp_all_full")) {
            boolean any = false;
            for (MoveSlot ms : p.moves) {
                if (ms == null) continue;
                if (ms.pp < ms.maxPp) { ms.pp = ms.maxPp; any = true; }
            }
            if (!any) { player.sendMessage("§7所有技能PP均已满。"); return false; }
            player.sendMessage("§a使用了 " + itemName(def) + "：§f" + displayName(p, s) + " §a所有技能PP已回满。");
            return true;
        }
        if (def.data.containsKey("pp_all")) {
            int add = parseIntObj(def.data.get("pp_all"));
            if (add <= 0) add = 10;
            boolean any = false;
            for (MoveSlot ms : p.moves) {
                if (ms == null) continue;
                int before = ms.pp;
                ms.pp = Math.min(ms.maxPp, ms.pp + add);
                if (ms.pp != before) any = true;
            }
            if (!any) { player.sendMessage("§7所有技能PP均已满。"); return false; }
            player.sendMessage("§a使用了 " + itemName(def) + "：§f" + displayName(p, s) + " §a所有技能PP已恢复。");
            return true;
        }

                // PP Up / PP Max: permanently increase this move's max PP (battle-safe persistent).
        if (def.data.containsKey("pp_up") || def.data.containsKey("pp_max")) {
            if (moveIndex < 0 || moveIndex >= 4) {
                player.sendMessage("§e请选择一个技能。");
                return false;
            }
            if (p.moves.size() <= moveIndex) { player.sendMessage("§e该槽位没有技能。"); return false; }
            MoveSlot ms2 = p.moves.get(moveIndex);
            if (ms2 == null || ms2.moveId == null || ms2.moveId.isEmpty()) { player.sendMessage("§e该槽位没有技能。"); return false; }
            int beforeMax = ms2.maxPp;
            int beforeUps = ms2.ppUpsUsed;
            if (def.data.containsKey("pp_max")) {
                if (ms2.ppUpsUsed >= 3) { player.sendMessage("§7该技能的PP已经提升到最大。"); return false; }
                ms2.ppUpsUsed = 3;
            } else {
                if (ms2.ppUpsUsed >= 3) { player.sendMessage("§7该技能的PP已经提升到最大。"); return false; }
                ms2.ppUpsUsed = Math.min(3, ms2.ppUpsUsed + 1);
            }
            ms2.recalcMaxPp();
            int delta = ms2.maxPp - beforeMax;
            if (delta > 0) ms2.pp = Math.min(ms2.maxPp, ms2.pp + delta);

            Move mv2 = dex.getMove(ms2.moveId);
            String mvName2 = mv2 != null ? mv2.name() : ms2.moveId;
            player.sendMessage("§a使用了 " + itemName(def) + "：§f" + displayName(p, s) + " §a的 §f" + mvName2 +
                    " §a最大PP提升（" + beforeMax + " -> " + ms2.maxPp + "，提升次数 " + beforeUps + " -> " + ms2.ppUpsUsed + "）");
            return true;
        }

if (moveIndex < 0 || moveIndex >= 4) {
            player.sendMessage("§e请选择一个技能。");
            return false;
        }
        if (p.moves.size() <= moveIndex) { player.sendMessage("§e该槽位没有技能。"); return false; }
        MoveSlot ms = p.moves.get(moveIndex);
        if (ms == null || ms.moveId == null || ms.moveId.isEmpty()) { player.sendMessage("§e该槽位没有技能。"); return false; }

        if (ms.pp >= ms.maxPp) { player.sendMessage("§7该技能PP已满。"); return false; }

        if (parseBoolObj(def.data.get("pp_full")) != null && parseBoolObj(def.data.get("pp_full"))) {
            ms.pp = ms.maxPp;
        } else {
            int add = parseIntObj(def.data.get("pp"));
            if (add <= 0) add = 10;
            ms.pp = Math.min(ms.maxPp, ms.pp + add);
        }

        Move mv = dex.getMove(ms.moveId);
        String mvName = mv != null ? mv.name() : ms.moveId;
        player.sendMessage("§a使用了 " + itemName(def) + "：§f" + displayName(p, s) + " §a的 §f" + mvName + " §aPP恢复至 " + ms.pp + "/" + ms.maxPp);
        return true;
    }
    private boolean isPokemonRodId(String id) {
        if (id == null) return false;
        return java.util.Set.of("poke_rod","great_rod","ultra_rod","master_rod","love_rod").contains(id.toLowerCase(java.util.Locale.ROOT));
    }

}
