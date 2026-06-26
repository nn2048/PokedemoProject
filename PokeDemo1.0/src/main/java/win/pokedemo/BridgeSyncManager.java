
package win.pokedemo;

import com.google.gson.Gson;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Syncs the real PokeDemo party state to the custom Fabric client bridge mod via plugin messages.
 * The bridge client sends a hello on join; once detected, we keep the player's left HUD synced.
 */
public final class BridgeSyncManager implements Listener, PluginMessageListener {
    public static final String CHANNEL_SYNC_PARTY = "pokedemo_bridge:sync_party";
    public static final String CHANNEL_SYNC_ENTITY = "pokedemo_bridge:sync_entity";
    public static final String CHANNEL_REMOVE_ENTITY = "pokedemo_bridge:remove_entity";
    public static final String CHANNEL_HELLO = "pokedemo_bridge:hello";
    public static final String CHANNEL_SENDOUT = "pokedemo_bridge:sendout";
    public static final String CHANNEL_MODEL_INTERACT = "pokedemo_bridge:model_interact";
    public static final String CHANNEL_BATTLE_STATE = "pokedemo_bridge:battle_state";
    public static final String CHANNEL_BATTLE_ACTION = "pokedemo_bridge:battle_action";
    public static final String CHANNEL_PC_STATE = "pokedemo_bridge:pc_state";
    public static final String CHANNEL_PC_ACTION = "pokedemo_bridge:pc_action";
    public static final String CHANNEL_STARTER_STATE = "pokedemo_bridge:starter_state";
    public static final String CHANNEL_STARTER_ACTION = "pokedemo_bridge:starter_action";

    private final PokeDemoPlugin plugin;
    private final Gson gson = new Gson();
    private final Set<UUID> bridgeClients = ConcurrentHashMap.newKeySet();
    private int taskId = -1;
    private final Map<UUID, Set<UUID>> lastSyncedEntities = new ConcurrentHashMap<>();
    private final Map<UUID, TimedAnimation> animationOverrides = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> currentPcPages = new ConcurrentHashMap<>();

    public BridgeSyncManager(PokeDemoPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        shutdown();
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, CHANNEL_HELLO, this);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, CHANNEL_SENDOUT, this);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, CHANNEL_MODEL_INTERACT, this);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, CHANNEL_BATTLE_ACTION, this);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, CHANNEL_PC_ACTION, this);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, CHANNEL_STARTER_ACTION, this);
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL_SYNC_PARTY);
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL_SYNC_ENTITY);
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL_REMOVE_ENTITY);
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL_BATTLE_STATE);
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL_PC_STATE);
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL_STARTER_STATE);
        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::syncAllKnownClients, 40L, 20L);
    }

    public void shutdown() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
        bridgeClients.clear();
        currentPcPages.clear();
        lastSyncedEntities.clear();
        animationOverrides.clear();
        try { plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin, CHANNEL_HELLO, this); } catch (Throwable ignored) {}
        try { plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin, CHANNEL_SENDOUT, this); } catch (Throwable ignored) {}
        try { plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin, CHANNEL_MODEL_INTERACT, this); } catch (Throwable ignored) {}
        try { plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin, CHANNEL_BATTLE_ACTION, this); } catch (Throwable ignored) {}
        try { plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin, CHANNEL_PC_ACTION, this); } catch (Throwable ignored) {}
        try { plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin, CHANNEL_STARTER_ACTION, this); } catch (Throwable ignored) {}
        try { plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin, CHANNEL_SYNC_PARTY); } catch (Throwable ignored) {}
        try { plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin, CHANNEL_SYNC_ENTITY); } catch (Throwable ignored) {}
        try { plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin, CHANNEL_REMOVE_ENTITY); } catch (Throwable ignored) {}
        try { plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin, CHANNEL_BATTLE_STATE); } catch (Throwable ignored) {}
        try { plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin, CHANNEL_PC_STATE); } catch (Throwable ignored) {}
        try { plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin, CHANNEL_STARTER_STATE); } catch (Throwable ignored) {}
    }

    public boolean isBridgeClient(UUID playerId) {
        return playerId != null && bridgeClients.contains(playerId);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // wait for client hello; nothing to do here
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        bridgeClients.remove(event.getPlayer().getUniqueId());
        lastSyncedEntities.remove(event.getPlayer().getUniqueId());
        currentPcPages.remove(event.getPlayer().getUniqueId());
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (player == null) return;
        if (CHANNEL_HELLO.equals(channel)) {
            UUID id = player.getUniqueId();
            if (bridgeClients.add(id)) {
                plugin.getLogger().info("[PokeDemoBridge] Detected bridge client for " + player.getName());
            }
            tryCloseVanillaBattleInventory(player);
            syncParty(player);
            syncEntities(player);
            syncBattleState(player);
            try {
                if (plugin.getPartySidebarManager() != null) {
                    plugin.getPartySidebarManager().clearSidebar(player);
                }
            } catch (Throwable ignored) {}
            return;
        }
        if (CHANNEL_SENDOUT.equals(channel)) {
            SendoutRequest req = decodeSendoutRequest(player, message);
            if (req == null || req.slot() < 0 || req.slot() > 5) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    if (plugin.getSummonManager() != null) {
                        plugin.getSummonManager().toggleSendOutAt(player, req.slot(), req.target(), req.targetEntityUuid());
                    }
                } finally {
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        syncParty(player);
                        syncEntities(player);
                        syncBattleState(player);
                    }, 2L);
                }
            });
        }
        if (CHANNEL_MODEL_INTERACT.equals(channel)) {
            UUID entityId = decodeUuid(message);
            if (entityId == null) return;
            Bukkit.getScheduler().runTask(plugin, () -> handleModelInteract(player, entityId));
            return;
        }
        if (CHANNEL_BATTLE_ACTION.equals(channel)) {
            String action = decodeString(message);
            if (action == null || action.isBlank()) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    if (plugin.battles() != null) plugin.battles().handleBridgeBattleAction(player, action);
                } finally {
                    Bukkit.getScheduler().runTaskLater(plugin, () -> syncBattleState(player), 1L);
                }
            });
        }

        if (CHANNEL_PC_ACTION.equals(channel)) {
            String action = decodeString(message);
            if (action == null || action.isBlank()) return;
            Bukkit.getScheduler().runTask(plugin, () -> handleBridgePcAction(player, action));
            return;
        }
        if (CHANNEL_STARTER_ACTION.equals(channel)) {
            String action = decodeString(message);
            if (action == null || action.isBlank()) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    handleStarterAction(player, action);
                } finally {
                    Bukkit.getScheduler().runTaskLater(plugin, () -> syncStarterState(player), 1L);
                }
            });
        }
    }

    public boolean canUseBridgePc(Player player) {
        if (player == null) return false;
        if (!isBridgeClient(player.getUniqueId())) return false;
        try {
            if (plugin.getPastureManager() != null && plugin.getPastureManager().getPending(player.getUniqueId()) != null) return false;
        } catch (Throwable ignored) {}
        try {
            if (plugin.getCloneManager() != null && plugin.getCloneManager().getPending(player.getUniqueId()) != null) return false;
        } catch (Throwable ignored) {}
        try {
            org.bukkit.inventory.InventoryView view = player.getOpenInventory();
            if (view != null && view.getTopInventory() != null && view.getTopInventory().getHolder() instanceof GuiHolder gh) {
                if (gh.type == GuiType.PARTY_TRADE_SELECT) return false;
            }
        } catch (Throwable ignored) {}
        return true;
    }

    public void openPcScreen(Player player, int page) {
        if (player == null || !player.isOnline()) return;
        currentPcPages.put(player.getUniqueId(), Math.max(0, page));
        syncPcState(player, Math.max(0, page));
    }

    public void syncPcState(Player player, int page) {
        if (player == null || !player.isOnline()) return;
        try {
            PlayerProfile prof = plugin.getStorage().getProfile(player.getUniqueId());
            int safePage = Math.max(0, page);
            currentPcPages.put(player.getUniqueId(), safePage);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("active", true);
            payload.put("page", safePage);
            payload.put("totalCount", prof.pc == null ? 0 : prof.pc.size());
            payload.put("hasPrev", safePage > 0);
            payload.put("hasNext", prof.pc != null && (safePage + 1) * 45 < prof.pc.size());
            Integer pending = plugin.getStorage().getPendingPcRelease(player.getUniqueId());
            payload.put("pendingReleaseIndex", pending == null ? -1 : pending);
            List<Map<String, Object>> slots = new ArrayList<>();
            int start = safePage * 45;
            int end = Math.min(prof.pc.size(), start + 45);
            for (int slot = 0; slot < 45; slot++) {
                int abs = start + slot;
                if (abs >= end) {
                    Map<String, Object> empty = new LinkedHashMap<>();
                    empty.put("pageSlot", slot);
                    empty.put("absoluteIndex", -1);
                    empty.put("occupied", false);
                    slots.add(empty);
                    continue;
                }
                PokemonInstance p = prof.pc.get(abs);
                Species sp = plugin.getDex() == null ? null : plugin.getDex().getSpecies(p.speciesId);
                int maxHp = (sp != null) ? p.maxHp(sp) : p.currentHp;
                Map<String, Object> s = new LinkedHashMap<>();
                s.put("pageSlot", slot);
                s.put("absoluteIndex", abs);
                s.put("pokemonUuid", p.uuid == null ? null : p.uuid.toString());
                s.put("occupied", true);
                s.put("species", p.speciesId == null ? "unknown" : p.speciesId);
                s.put("displayName", stripLegacy(p.displayName()));
                s.put("level", p.level);
                s.put("hp", p.currentHp);
                s.put("maxHp", maxHp);
                s.put("status", p.status == null ? "NONE" : p.status);
                s.put("gender", p.gender == null ? "N" : p.gender);
                s.put("shiny", false);
                s.put("heldItemId", p.heldItemId == null ? "" : p.heldItemId);
                s.put("egg", p.isEgg);
                s.put("locked", p.uiLocked);
                s.put("lockReason", p.uiLockReason == null ? "" : stripLegacy(p.uiLockReason));
                slots.add(s);
            }
            payload.put("slots", slots);
            player.sendPluginMessage(plugin, CHANNEL_PC_STATE, gson.toJson(payload).getBytes(StandardCharsets.UTF_8));
        } catch (Throwable t) {
            plugin.getLogger().warning("[PokeDemoBridge] Failed to sync PC state to " + player.getName() + ": " + t.getMessage());
        }
    }

    private void handleBridgePcAction(Player player, String action) {
        if (player == null || !player.isOnline()) return;
        int page = Math.max(0, currentPcPages.getOrDefault(player.getUniqueId(), 0));
        PlayerProfile prof = plugin.getStorage().getProfile(player.getUniqueId());
        if (action.equals("prev")) { syncPcState(player, Math.max(0, page - 1)); return; }
        if (action.equals("next")) { syncPcState(player, page + 1); return; }
        if (action.equals("release_confirm")) {
            Integer pending = plugin.getStorage().getPendingPcRelease(player.getUniqueId());
            if (pending != null && pending >= 0 && pending < prof.pc.size()) {
                PokemonInstance p = prof.pc.remove((int) pending);
                plugin.getStorage().clearPendingPcRelease(player.getUniqueId());
                player.sendMessage("§c已放生：" + p.displayName());
                int lastPage = Math.max(0, (prof.pc.size() - 1) / 45);
                syncPcState(player, Math.min(page, lastPage));
            } else {
                syncPcState(player, page);
            }
            return;
        }
        if (action.startsWith("summary:")) {
            int pageSlot = parseTrailingInt(action);
            int index = page * 45 + pageSlot;
            if (index >= 0 && index < prof.pc.size()) {
                UtilGui.openSummary(player, plugin.getStorage(), prof.pc.get(index), false, -1, index, 0);
            }
            return;
        }
        if (action.startsWith("withdraw:")) {
            int pageSlot = parseTrailingInt(action);
            int index = page * 45 + pageSlot;
            if (index < 0 || index >= prof.pc.size()) return;
            PokemonInstance picked = prof.pc.get(index);
            if (picked == null || picked.uiLocked) { syncPcState(player, page); return; }
            if (prof.party.size() >= 6) { player.sendMessage("§c你的队伍已满（6只）。"); syncPcState(player, page); return; }
            if (picked.isEgg) {
                int nonEgg = 0;
                for (PokemonInstance pi : prof.party) if (pi != null && !pi.isEgg) nonEgg++;
                if (nonEgg <= 0) {
                    player.sendMessage("§c不能让队伍里只剩蛋。 §7请至少保留/取出一只可战斗精灵。");
                    syncPcState(player, page);
                    return;
                }
            }
            PokemonInstance p = prof.pc.remove(index);
            prof.party.add(p);
            player.sendMessage("§a已取出：" + p.displayName() + " §a→ 队伍");
            syncParty(player);
            syncPcState(player, page);
            return;
        }
        if (action.startsWith("release:")) {
            int pageSlot = parseTrailingInt(action);
            int index = page * 45 + pageSlot;
            if (index < 0 || index >= prof.pc.size()) return;
            PokemonInstance picked = prof.pc.get(index);
            if (picked == null || picked.isEgg || picked.uiLocked) { syncPcState(player, page); return; }
            Integer pending = plugin.getStorage().getPendingPcRelease(player.getUniqueId());
            if (pending != null && pending == index) {
                PokemonInstance p = prof.pc.remove(index);
                plugin.getStorage().clearPendingPcRelease(player.getUniqueId());
                player.sendMessage("§c已放生：" + p.displayName());
            } else {
                plugin.getStorage().setPendingPcRelease(player.getUniqueId(), index, 8000);
                player.sendMessage("§e再次右键同一只精灵以确认放生（8秒内有效）");
            }
            syncPcState(player, page);
        }
    }

    private int parseTrailingInt(String action) {
        int idx = action.lastIndexOf(':');
        if (idx < 0 || idx >= action.length() - 1) return -1;
        try { return Integer.parseInt(action.substring(idx + 1).trim()); } catch (Throwable ignored) { return -1; }
    }


    public void syncStarterState(Player player) {
        if (player == null || !player.isOnline()) return;
        if (!isBridgeClient(player.getUniqueId())) return;
        try {
            Storage storage = plugin.getStorage();
            LangManager lang = plugin.getLang();
            PlayerProfile prof = storage == null ? null : storage.getProfile(player.getUniqueId());
            java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
            boolean active = prof != null && !prof.starterChosen;
            payload.put("active", active);
            payload.put("title", lang != null ? lang.ui("gui.starter.title", "选择初始伙伴") : "选择初始伙伴");
            payload.put("selectedIndex", 0);
            java.util.List<java.util.Map<String, Object>> entries = new java.util.ArrayList<>();
            entries.add(starterEntry(lang, "bulbasaur"));
            entries.add(starterEntry(lang, "charmander"));
            entries.add(starterEntry(lang, "squirtle"));
            payload.put("entries", entries);
            byte[] bytes = gson.toJson(payload).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            player.sendPluginMessage(plugin, CHANNEL_STARTER_STATE, bytes);
        } catch (Throwable t) {
            plugin.getLogger().warning("[PokeDemoBridge] Failed to sync starter state: " + t.getMessage());
        }
    }

    private java.util.Map<String, Object> starterEntry(LangManager lang, String speciesId) {
        java.util.Map<String, Object> entry = new java.util.LinkedHashMap<>();
        entry.put("species", speciesId);
        entry.put("displayName", lang != null ? lang.species(speciesId, speciesId) : speciesId);
        entry.put("description", "cobblemon.species." + speciesId.toLowerCase(java.util.Locale.ROOT) + ".desc");
        Species sp = null;
        try { sp = plugin.getDex() == null ? null : plugin.getDex().getSpecies(speciesId); } catch (Throwable ignored) {}
        entry.put("primaryType", sp != null && sp.types() != null && !sp.types().isEmpty() ? sp.types().get(0) : "");
        entry.put("secondaryType", sp != null && sp.types() != null && sp.types().size() > 1 ? sp.types().get(1) : "");
        entry.put("gender", "N");
        entry.put("shiny", false);
        entry.put("level", 10);
        return entry;
    }

    private void handleStarterAction(Player player, String action) {
        if (player == null) return;
        if (action.equalsIgnoreCase("close")) {
            try { player.closeInventory(); } catch (Throwable ignored) {}
            return;
        }
        String lower = action.toLowerCase(java.util.Locale.ROOT);
        if (!lower.startsWith("choose:")) return;
        String speciesId = lower.substring("choose:".length()).trim();
        if (speciesId.isBlank()) return;
        boolean chosen = UtilGui.chooseStarter(player, speciesId);
        if (chosen) {
            try { player.closeInventory(); } catch (Throwable ignored) {}
        }
    }

    private int decodeSlot(byte[] message) {
        try {
            String raw = new String(message, StandardCharsets.UTF_8).trim();
            int pipe = raw.indexOf('|');
            if (pipe >= 0) raw = raw.substring(0, pipe).trim();
            return Integer.parseInt(raw);
        } catch (Throwable ignored) {
            return -1;
        }
    }


    private SendoutRequest decodeSendoutRequest(Player player, byte[] message) {
        try {
            String raw = new String(message, StandardCharsets.UTF_8).trim();
            if (raw.isEmpty()) return null;
            String[] parts = raw.split("\\|");
            int slot = Integer.parseInt(parts[0].trim());
            org.bukkit.Location target = null;
            java.util.UUID targetEntityUuid = null;
            if (parts.length >= 5 && player != null && player.getWorld() != null) {
                double x = Double.parseDouble(parts[2].trim());
                double y = Double.parseDouble(parts[3].trim());
                double z = Double.parseDouble(parts[4].trim());
                target = new org.bukkit.Location(player.getWorld(), x, y, z);
            }
            if (parts.length >= 6 && !parts[5].isBlank()) {
                targetEntityUuid = java.util.UUID.fromString(parts[5].trim());
            }
            return new SendoutRequest(slot, target, targetEntityUuid);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private record SendoutRequest(int slot, org.bukkit.Location target, java.util.UUID targetEntityUuid) {}

    private String decodeString(byte[] message) {
        try {
            return new String(message, StandardCharsets.UTF_8).trim();
        } catch (Throwable ignored) {
            return null;
        }
    }

    public void triggerCarrierAnimation(UUID entityUuid, String animation, long durationTicks) {
        if (entityUuid == null || animation == null || animation.isBlank()) return;
        long until = System.currentTimeMillis() + Math.max(100L, durationTicks * 50L);
        animationOverrides.put(entityUuid, new TimedAnimation(animation, until));
        syncAllKnownClients();
        Bukkit.getScheduler().runTaskLater(plugin, this::syncAllKnownClients, Math.max(1L, durationTicks + 1L));
    }

    public void triggerPokemonAnimation(UUID pokemonUuid, String animation, long durationTicks) {
        if (pokemonUuid == null) return;
        UUID entityUuid = findCarrierByPokemonUuid(pokemonUuid);
        if (entityUuid != null) triggerCarrierAnimation(entityUuid, animation, durationTicks);
    }

    private UUID findCarrierByPokemonUuid(UUID pokemonUuid) {
        String raw = pokemonUuid.toString();
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (Wolf wolf : world.getEntitiesByClass(Wolf.class)) {
                String pu = wolf.getPersistentDataContainer().get(plugin.KEY_PUUID, PersistentDataType.STRING);
                if (raw.equalsIgnoreCase(pu)) return wolf.getUniqueId();
            }
        }
        return null;
    }

    public void syncAllKnownClients() {
        for (UUID uuid : new ArrayList<>(bridgeClients)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                bridgeClients.remove(uuid);
                continue;
            }
            syncParty(player);
            syncEntities(player);
            syncBattleState(player);
        }
    }

    public void syncParty(Player player) {
        if (player == null || !player.isOnline()) return;
        try {
            PlayerProfile profile = plugin.getStorage().getProfile(player.getUniqueId());
            List<Map<String, Object>> payload = new ArrayList<>();
            List<PokemonInstance> party = profile.party == null ? List.of() : profile.party;
            for (int slot = 0; slot < 6; slot++) {
                PokemonInstance mon = slot < party.size() ? party.get(slot) : null;
                payload.add(toSlotPayload(player.getUniqueId(), slot, mon));
            }
            byte[] bytes = gson.toJson(payload).getBytes(StandardCharsets.UTF_8);
            player.sendPluginMessage(plugin, CHANNEL_SYNC_PARTY, bytes);
        } catch (Throwable t) {
            plugin.getLogger().warning("[PokeDemoBridge] Failed to sync party to " + player.getName() + ": " + t.getMessage());
        }
    }


    public void syncBattleState(Player player) {
        if (player == null || !player.isOnline()) return;
        try {
            BattleManager battles = plugin.battles();
            BattleSession s = battles == null ? null : battles.getSession(player.getUniqueId());
            Map<String, Object> payload = new LinkedHashMap<>();
            if (s == null || s.finished) {
                payload.put("active", false);
                payload.put("log", List.of());
                player.sendPluginMessage(plugin, CHANNEL_BATTLE_STATE, gson.toJson(payload).getBytes(StandardCharsets.UTF_8));
                return;
            }
            payload.put("active", true);
            payload.put("self", battlerPayload(s.playerMon));
            payload.put("foe", battlerPayload(s.wildMon));
            payload.put("moves", movePayloads(s.playerMon));
            payload.put("party", partyPayload(player, s));
            payload.put("log", battleLogPayload(s));
            payload.put("pvp", s.pvp);
            payload.put("awaitingForcedSwitch", s.awaitingForcedSwitch);
            payload.put("processingTurn", s.processingTurn);
            payload.put("statusLine", stripLegacy(s.statusLine == null ? "" : s.statusLine));
            payload.put("requestType", s.awaitingForcedSwitch ? "FORCED_SWITCH" : (s.processingTurn ? "WAIT" : "ACTION"));
            payload.put("canFight", !s.processingTurn && !s.awaitingForcedSwitch);
            payload.put("canSwitch", !s.processingTurn);
            payload.put("canBag", !s.processingTurn && !s.pvp);
            payload.put("canRun", !s.processingTurn && !s.pvp);
            payload.put("canForfeit", !s.processingTurn && s.pvp);
            player.sendPluginMessage(plugin, CHANNEL_BATTLE_STATE, gson.toJson(payload).getBytes(StandardCharsets.UTF_8));
        } catch (Throwable t) {
            plugin.getLogger().warning("[PokeDemoBridge] Failed to sync battle state to " + player.getName() + ": " + t.getMessage());
        }
    }

    private Map<String, Object> battlerPayload(PokemonInstance mon) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (mon == null) return out;
        Species sp = resolveSpecies(mon.effectiveSpeciesId());
        LangManager lang = plugin.getLang();
        String speciesId = mon.effectiveSpeciesId();
        String name = (lang == null || sp == null) ? (sp == null ? speciesId : sp.name()) : lang.species(sp.id(), sp.name());
        out.put("species", speciesId == null ? "unknown" : speciesId);
        out.put("name", stripLegacy(name));
        out.put("level", mon.level);
        out.put("hp", mon.currentHp);
        out.put("maxHp", safeMaxHp(mon, sp));
        out.put("status", normalizeStatus(mon, mon.currentHp));
        out.put("gender", normalizeGender(mon.gender));
        return out;
    }

    private List<Map<String, Object>> movePayloads(PokemonInstance mon) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (mon == null || mon.effectiveMoves() == null) return out;
        LangManager lang = plugin.getLang();
        for (MoveSlot ms : mon.effectiveMoves()) {
            Map<String, Object> row = new LinkedHashMap<>();
            Move m = ms == null ? null : plugin.getDex().getMoveOrPlaceholder(ms.moveId);
            if (m == null) {
                row.put("name", "—"); row.put("detail", ""); row.put("pp", ""); row.put("type", ""); row.put("category", "");
                row.put("power", 0); row.put("accuracy", 100); row.put("priority", 0); row.put("description", "");
                row.put("disabled", true); row.put("disabledReason", "无招式数据");
            } else {
                String name = (lang == null) ? m.name() : lang.move(m.id(), m.name());
                row.put("name", stripLegacy(name));
                row.put("detail", stripLegacy((lang == null ? String.valueOf(m.type()) : lang.typeName(m.type())) + " / " + (lang == null ? String.valueOf(m.category()) : lang.categoryName(m.category()))));
                row.put("pp", (ms == null ? 0 : ms.pp) + "/" + (ms == null ? 0 : ms.maxPp));
                row.put("type", m.type() == null ? "" : m.type());
                row.put("category", m.category() == null ? "" : m.category().toLowerCase(Locale.ROOT));
                row.put("power", Math.max(0, m.power()));
                row.put("accuracy", m.accuracy() <= 0 ? 100 : (int)Math.round(m.accuracy() * 100.0));
                row.put("priority", m.priority());
                String desc = MoveDescriptionCatalog.descriptionFor(lang, m.id());
                if (desc == null || desc.isBlank()) desc = summarizeMoveForBridge(m);
                row.put("description", stripLegacy(desc));
                boolean disabled = ms == null || ms.pp <= 0;
                row.put("disabled", disabled);
                row.put("disabledReason", disabled ? "PP不足" : "");
            }
            out.add(row);
        }
        return out;
    }


    private String summarizeMoveForBridge(Move move) {
        if (move == null) return "";
        String category = move.category() == null ? "status" : move.category().toLowerCase(Locale.ROOT);
        java.util.List<String> parts = new java.util.ArrayList<>();
        if (!"status".equals(category) && move.power() > 0) {
            parts.add("对目标造成" + (move.type() == null ? "" : move.type()) + "属性" + ("physical".equals(category) ? "物理" : "特殊") + "伤害");
        }
        try {
            for (Map<String, Object> fx : move.effectsSafe()) {
                if (fx == null) continue;
                String id = String.valueOf(fx.getOrDefault("id", "")).toLowerCase(Locale.ROOT);
                if (id.isBlank()) continue;
                if (id.contains("burn")) parts.add("有概率使目标灼伤");
                else if (id.contains("poison")) parts.add("有概率使目标中毒");
                else if (id.contains("paraly")) parts.add("有概率使目标麻痹");
                else if (id.contains("sleep")) parts.add("有概率使目标睡眠");
                else if (id.contains("freeze")) parts.add("有概率使目标冰冻");
                else if (id.contains("flinch")) parts.add("有概率使目标畏缩");
                else if (id.contains("heal")) parts.add("恢复自身状态或体力");
                else if (id.contains("protect")) parts.add("本回合保护自己免受攻击");
                else if (id.contains("switch")) parts.add("会导致场上宝可梦发生替换");
                else if (id.contains("confus")) parts.add("有概率使目标混乱");
                else if (id.contains("trap")) parts.add("束缚目标，阻止其切换");
                else if (id.contains("recoil")) parts.add("会令使用者承受反作用伤害");
                else if (id.contains("weather")) parts.add("改变天气");
                else if (id.contains("screen")) parts.add("张开防御屏障");
                else if (id.contains("terrain")) parts.add("改变场地");
                else if (id.contains("raise")) parts.add("提升能力值");
                else if (id.contains("lower")) parts.add("降低目标能力值");
            }
        } catch (Throwable ignored) {}
        if (parts.isEmpty()) {
            if ("status".equals(category)) parts.add("变化招式，会对战场或状态产生效果");
            else parts.add("对目标造成伤害");
        }
        java.util.LinkedHashSet<String> uniq = new java.util.LinkedHashSet<>(parts);
        return String.join("；", uniq);
    }

    private List<Map<String, Object>> partyPayload(Player player, BattleSession s) {
        List<Map<String, Object>> payload = new ArrayList<>();
        try {
            PlayerProfile profile = plugin.getStorage().getProfile(player.getUniqueId());
            List<PokemonInstance> party = profile.party == null ? List.of() : profile.party;
            for (int slot = 0; slot < 6; slot++) {
                PokemonInstance mon = slot < party.size() ? party.get(slot) : null;
                Map<String, Object> row = toSlotPayload(player.getUniqueId(), slot, mon);
                row.put("active", mon != null && s.playerMon != null && Objects.equals(mon.uuid, s.playerMon.uuid));
                payload.add(row);
            }
        } catch (Throwable ignored) {}
        return payload;
    }

    private List<String> battleLogPayload(BattleSession s) {
        List<String> out = new ArrayList<>();
        try {
            if (s.recentLog != null && !s.recentLog.isEmpty()) {
                for (String line : s.recentLog) out.add(stripLegacy(line));
            }
            if (out.isEmpty() && s.statusLine != null && !s.statusLine.isBlank()) out.add(stripLegacy(s.statusLine));
        } catch (Throwable ignored) {}
        if (out.isEmpty()) out.add("现在为第" + (s.turn + 1) + "回合。");
        return out;
    }

    private void tryCloseVanillaBattleInventory(Player player) {
        if (player == null) return;
        try {
            org.bukkit.inventory.InventoryView view = player.getOpenInventory();
            if (view == null) return;
            org.bukkit.inventory.Inventory top = view.getTopInventory();
            if (top == null) return;
            if (!(top.getHolder() instanceof GuiHolder holder)) return;
            if (holder.type == GuiType.BATTLE || holder.type == GuiType.BATTLE_SWITCH || holder.type == GuiType.BATTLE_ITEM_SELECT || holder.type == GuiType.BATTLE_BALL_SELECT) {
                player.closeInventory();
            }
        } catch (Throwable ignored) {}
    }

    private String stripLegacy(String s) {
        if (s == null) return "";
        return s.replaceAll("§.", "");
    }


    public void syncEntities(Player player) {
        if (player == null || !player.isOnline() || player.getWorld() == null) return;
        try {
            Set<UUID> seen = new HashSet<>();
            for (Wolf wolf : player.getWorld().getEntitiesByClass(Wolf.class)) {
                if (!isRelevantCarrier(player, wolf)) continue;
                CarrierSnapshot snap = snapshotForWolf(player, wolf);
                if (snap == null || snap.species == null || snap.species.isBlank()) continue;
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("entityUuid", wolf.getUniqueId().toString());
                payload.put("entityId", wolf.getEntityId());
                payload.put("ownerUuid", snap.ownerUuid == null ? "" : snap.ownerUuid.toString());
                payload.put("slot", snap.slot);
                payload.put("pokemonUuid", snap.pokemonUuid == null ? "" : snap.pokemonUuid.toString());
                payload.put("species", snap.species.toUpperCase(Locale.ROOT));
                payload.put("form", "normal");
                payload.put("gender", snap.gender);
                payload.put("shiny", snap.shiny);
                payload.put("displayName", snap.displayName == null ? "" : snap.displayName);
                payload.put("level", snap.level);
                CarrierMotionController.MotionState motion = plugin.getCarrierMotionController() != null ? plugin.getCarrierMotionController().getState(wolf.getUniqueId()) : null;
                payload.put("animation", snap.animation == null ? "idle" : snap.animation);
                payload.put("scale", 1.0f);
                payload.put("moveMode", motion == null ? CarrierMotionController.MODE_LAND_IDLE : motion.moveMode());
                payload.put("moveSpeed", motion == null ? 0.0f : motion.moveSpeed());
                boolean inBattleNow = false;
                float battleYaw = Float.NaN;
                try {
                    if (plugin.battles() != null) {
                        var carrierSession = plugin.battles().findSessionByCarrier(wolf.getUniqueId());
                        inBattleNow = (snap.ownerUuid != null && plugin.battles().isInBattle(snap.ownerUuid))
                                || carrierSession != null;
                        if (carrierSession != null) {
                            if (wolf.getUniqueId().equals(carrierSession.playerBattleCarrierId)) battleYaw = carrierSession.playerAnchorYaw;
                            else if (wolf.getUniqueId().equals(carrierSession.wildBattleCarrierId)) battleYaw = carrierSession.wildAnchorYaw;
                        }
                    }
                } catch (Throwable ignored) {}
                payload.put("airborne", motion != null && motion.airborne());
                payload.put("submerged", motion != null && motion.submerged());
                payload.put("sleeping", !inBattleNow && motion != null && motion.sleeping());
                payload.put("battle", inBattleNow);
                if (!Float.isNaN(battleYaw)) payload.put("battleYaw", battleYaw);
                byte[] bytes = gson.toJson(payload).getBytes(StandardCharsets.UTF_8);
                player.sendPluginMessage(plugin, CHANNEL_SYNC_ENTITY, bytes);
                seen.add(wolf.getUniqueId());
            }
            Set<UUID> old = lastSyncedEntities.getOrDefault(player.getUniqueId(), Set.of());
            for (UUID stale : old) {
                if (!seen.contains(stale)) {
                    player.sendPluginMessage(plugin, CHANNEL_REMOVE_ENTITY, stale.toString().getBytes(StandardCharsets.UTF_8));
                }
            }
            lastSyncedEntities.put(player.getUniqueId(), seen);
        } catch (Throwable t) {
            plugin.getLogger().warning("[PokeDemoBridge] Failed to sync entities to " + player.getName() + ": " + t.getMessage());
        }
    }

    private boolean isRelevantCarrier(Player viewer, Wolf wolf) {
        if (wolf == null || !wolf.isValid() || wolf.isDead()) return false;
        if (viewer.getLocation().distanceSquared(wolf.getLocation()) > (96.0 * 96.0)) return false;
        var pdc = wolf.getPersistentDataContainer();
        return pdc.has(plugin.KEY_SPECIES, PersistentDataType.STRING)
                || pdc.has(plugin.KEY_PUUID, PersistentDataType.STRING)
                || pdc.has(plugin.KEY_WILD, PersistentDataType.BYTE);
    }

    private CarrierSnapshot snapshotForWolf(Player viewer, Wolf wolf) {
        String species = wolf.getPersistentDataContainer().get(plugin.KEY_SPECIES, PersistentDataType.STRING);
        String gender = "N";
        boolean shiny = false;
        UUID owner = plugin.getSummonManager() != null ? plugin.getSummonManager().getOwnerUuidFromEntity(wolf) : null;
        UUID puuid = plugin.getSummonManager() != null ? plugin.getSummonManager().getPokemonUuidFromEntity(wolf) : null;
        int slot = -1;
        String displayName = localizeSpecies(species);
        int level = Optional.ofNullable(wolf.getPersistentDataContainer().get(plugin.KEY_LEVEL, PersistentDataType.INTEGER)).orElse(0);
        String animation = "idle";
        CarrierMotionController.MotionState motionState = plugin.getCarrierMotionController() != null ? plugin.getCarrierMotionController().getState(wolf.getUniqueId()) : null;
        TimedAnimation override = animationOverrides.get(wolf.getUniqueId());
        if (override != null && System.currentTimeMillis() >= override.untilMillis()) {
            animationOverrides.remove(wolf.getUniqueId());
            override = null;
        }
        PokemonInstance mon = findPokemon(owner, puuid);
        if (mon != null) {
            if (mon.speciesId != null && !mon.speciesId.isBlank()) species = mon.speciesId;
            gender = normalizeGender(mon.gender);
            shiny = isShiny(mon);
            slot = findPartySlot(owner, puuid);
            String localized = localizeSpecies(mon.speciesId != null && !mon.speciesId.isBlank() ? mon.speciesId : species);
            String rawDisplay = mon.displayName();
            displayName = (rawDisplay == null || rawDisplay.isBlank() || (mon.speciesId != null && rawDisplay.equalsIgnoreCase(mon.speciesId))) ? localized : rawDisplay;
            level = mon.level;
        }
        displayName = maskDisplayNameForViewer(viewer, species, displayName);
        if (species == null || species.isBlank()) return null;
        boolean inBattle = false;
        try {
            if (plugin.battles() != null) {
                inBattle = (owner != null && plugin.battles().isInBattle(owner)) || plugin.battles().findSessionByCarrier(wolf.getUniqueId()) != null;
            }
        } catch (Throwable ignored) {}
        if (override != null) animation = override.animation();
        else if (inBattle) animation = "battle";
        else if (motionState != null && motionState.sleeping()) animation = "sleep";
        else if (motionState != null && motionState.moveMode() != null && !motionState.moveMode().isBlank() && !CarrierMotionController.MODE_LAND_IDLE.equalsIgnoreCase(motionState.moveMode())) animation = motionState.moveMode();
        return new CarrierSnapshot(species.toLowerCase(Locale.ROOT), gender, shiny, owner, slot, puuid, displayName, level, animation);
    }




    private String maskDisplayNameForViewer(Player viewer, String speciesId, String resolvedDisplayName) {
        if (viewer == null || speciesId == null || speciesId.isBlank()) return safeLabel(resolvedDisplayName);
        try {
            PlayerProfile prof = plugin.getStorage().getProfile(viewer.getUniqueId());
            if (prof != null && prof.dexCaught != null && prof.dexCaught.contains(speciesId.toLowerCase(Locale.ROOT))) {
                return safeLabel(resolvedDisplayName);
            }
        } catch (Throwable ignored) {}
        return "???";
    }

    private String safeLabel(String s) {
        if (s == null || s.isBlank()) return "???";
        return s;
    }

    private String localizeSpecies(String species) {
        if (species == null || species.isBlank()) return "";
        try {
            LangManager lang = plugin.getLang();
            if (lang != null) return lang.species(species, species);
        } catch (Throwable ignored) {}
        return species;
    }

    private int findPartySlot(UUID ownerId, UUID puuid) {
        if (ownerId == null || puuid == null) return -1;
        try {
            PlayerProfile profile = plugin.getStorage().getProfile(ownerId);
            if (profile == null || profile.party == null) return -1;
            for (int i = 0; i < profile.party.size(); i++) {
                PokemonInstance p = profile.party.get(i);
                if (p != null && puuid.equals(p.uuid)) return i;
            }
        } catch (Throwable ignored) {}
        return -1;
    }

    private PokemonInstance findPokemon(UUID ownerId, UUID puuid) {
        if (ownerId == null || puuid == null) return null;
        try {
            PlayerProfile profile = plugin.getStorage().getProfile(ownerId);
            if (profile == null) return null;
            if (profile.party != null) {
                for (PokemonInstance p : profile.party) {
                    if (p != null && puuid.equals(p.uuid)) return p;
                }
            }
            if (profile.pc != null) {
                for (PokemonInstance p : profile.pc) {
                    if (p != null && puuid.equals(p.uuid)) return p;
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }


    private UUID decodeUuid(byte[] message) {
        try {
            return UUID.fromString(new String(message, StandardCharsets.UTF_8).trim());
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void handleModelInteract(Player player, UUID entityUuid) {
        if (player == null || !player.isOnline() || player.getWorld() == null || entityUuid == null) return;
        Entity raw = player.getWorld().getEntity(entityUuid);
        if (!(raw instanceof Wolf wolf)) return;
        if (!isRelevantCarrier(player, wolf)) return;

        Byte wild = wolf.getPersistentDataContainer().get(plugin.KEY_WILD, PersistentDataType.BYTE);
        boolean isWild = wild != null && wild == (byte) 1;
        UUID owner = plugin.getSummonManager() != null ? plugin.getSummonManager().getOwnerUuidFromEntity(wolf) : null;

        if (isWild) {
            if (plugin.battles() != null && !plugin.battles().isInBattle(player.getUniqueId())) {
                plugin.battles().startWildBattle(player, wolf);
            }
            return;
        }

        if (owner != null && owner.equals(player.getUniqueId())) {
            return;
        }

        if (owner != null && plugin.battles() != null) {
            BattleSession s = plugin.battles().getSession(owner);
            if (s != null && !s.finished) {
                plugin.battles().enterSpectate(player, s);
                return;
            }
            Player other = Bukkit.getPlayer(owner);
            if (other != null && other.isOnline()) {
                plugin.battles().startPvpReady(player, other);
            }
        }
    }
    private record TimedAnimation(String animation, long untilMillis) {}

    private static final class CarrierSnapshot {
        final String species;
        final String gender;
        final boolean shiny;
        final UUID ownerUuid;
        final int slot;
        final UUID pokemonUuid;
        final String displayName;
        final int level;
        final String animation;
        CarrierSnapshot(String species, String gender, boolean shiny, UUID ownerUuid, int slot, UUID pokemonUuid, String displayName, int level, String animation) {
            this.species = species;
            this.gender = gender;
            this.shiny = shiny;
            this.ownerUuid = ownerUuid;
            this.slot = slot;
            this.pokemonUuid = pokemonUuid;
            this.displayName = displayName;
            this.level = level;
            this.animation = animation;
        }
    }

    private Map<String, Object> toSlotPayload(UUID playerId, int slot, PokemonInstance mon) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("slot", slot);
        if (mon == null) {
            out.put("occupied", false);
            out.put("species", "");
            out.put("displayName", "");
            out.put("level", 0);
            out.put("hp", 0);
            out.put("maxHp", 0);
            out.put("status", "NONE");
            out.put("active", false);
            out.put("gender", "N");
            out.put("shiny", false);
            return out;
        }
        Species species = resolveSpecies(mon.speciesId);
        int maxHp = safeMaxHp(mon, species);
        int hp = Math.max(0, Math.min(mon.currentHp, maxHp));
        out.put("occupied", true);
        out.put("species", mon.speciesId == null ? "" : mon.speciesId.toUpperCase(Locale.ROOT));
        out.put("displayName", mon.displayName());
        out.put("level", mon.level);
        out.put("hp", hp);
        out.put("maxHp", maxHp);
        out.put("status", normalizeStatus(mon, hp));
        out.put("active", plugin.getSummonManager() != null && plugin.getSummonManager().isSlotActive(playerId, slot));
        out.put("gender", normalizeGender(mon.gender));
        out.put("shiny", isShiny(mon));
        out.put("heldItemId", mon.heldItemId == null ? "" : mon.heldItemId);
        out.put("ballId", mon.ballId == null ? "poke_ball" : mon.ballId);
        return out;
    }

    private Species resolveSpecies(String rawId) {
        if (plugin.getDex() == null || rawId == null || rawId.isBlank()) return null;
        Species s = plugin.getDex().getSpecies(rawId);
        if (s != null) return s;
        return plugin.getDex().getSpeciesFlexible(rawId);
    }

    private int safeMaxHp(PokemonInstance mon, Species species) {
        if (mon == null) return 1;
        if (species != null) return Math.max(1, mon.maxHp(species));
        int cur = Math.max(0, mon.currentHp);
        Integer locked = mon.lockedMaxHp;
        if (locked != null && locked > 0) return Math.max(cur, locked);
        return Math.max(cur, 1);
    }

    private String normalizeStatus(PokemonInstance mon, int hp) {
        if (mon == null) return "NONE";
        if (hp <= 0) return "FNT";
        String raw = mon.status == null ? "" : mon.status.trim().toLowerCase(Locale.ROOT);
        return switch (raw) {
            case "poison" -> "PSN";
            case "toxic" -> "TOX";
            case "burn" -> "BRN";
            case "paralyze" -> "PAR";
            case "sleep" -> "SLP";
            case "freeze" -> "FRZ";
            case "fnt", "fainted" -> "FNT";
            default -> "NONE";
        };
    }

    private String normalizeGender(String raw) {
        if (raw == null || raw.isBlank()) return "N";
        String g = raw.trim().toUpperCase(Locale.ROOT);
        if (g.equals("MALE")) return "M";
        if (g.equals("FEMALE")) return "F";
        return (g.equals("M") || g.equals("F")) ? g : "N";
    }

    private boolean isShiny(PokemonInstance mon) {
        try {
            var f = mon.getClass().getDeclaredField("shiny");
            f.setAccessible(true);
            Object v = f.get(mon);
            return (v instanceof Boolean b) && b;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
