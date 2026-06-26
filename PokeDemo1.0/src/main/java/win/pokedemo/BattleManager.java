
package win.pokedemo;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.util.Vector;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BattleManager {
    private final PokeDemoPlugin plugin;
    private final Dex dex;
    private final Storage storage;
    private final ItemFactory items;

    private final Map<UUID, BattleSession> sessions = new ConcurrentHashMap<>();
    // Prevent multiple players from battling the same wild entity at the same time
    private final Map<UUID, UUID> wildLocks = new ConcurrentHashMap<>(); // wildEntityId -> playerId

    // --- PvP matchmaking / sessions ---
    private static final class PvpPending {
        final UUID a;
        final UUID b;
        boolean confirmedA;
        boolean confirmedB;
        int selectedA = -1;
        int selectedB = -1;
        PvpPending(UUID a, UUID b) { this.a = a; this.b = b; }
    }

    private static final class PvpBattle {
        final String key;
        final UUID a;
        final UUID b;
        final BattleSession sa;
        final BattleSession sb;
        volatile Integer moveA;
        volatile Integer moveB;
        volatile boolean processing;
        volatile boolean waitingForcedSwitch;
        volatile boolean pendingEndOfTurn;
        // defeated mons tracking for EXP
        final java.util.List<PokemonInstance> defeatedByA = new java.util.ArrayList<>();
        final java.util.List<PokemonInstance> defeatedByB = new java.util.ArrayList<>();

        PvpBattle(String key, UUID a, UUID b, BattleSession sa, BattleSession sb) {
            this.key = key; this.a = a; this.b = b; this.sa = sa; this.sb = sb;
        }
    }

    private final Map<String, PvpPending> pvpPending = new ConcurrentHashMap<>();
    private final Map<String, PvpBattle> pvpBattles = new ConcurrentHashMap<>();
    private final Map<UUID, String> pvpKeyByPlayer = new ConcurrentHashMap<>();


    public BattleManager(PokeDemoPlugin plugin, Dex dex, Storage storage, ItemFactory items) {
        this.plugin = plugin;
        this.dex = dex;
        this.storage = storage;
        this.items = items;
        startBattleVisualAnchorTask();
    }


    private void startBattleVisualAnchorTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    for (BattleSession s : sessions.values()) {
                        if (s == null || s.finished || !s.visualsSpawned) continue;
                        applyBattleAnchor(findWolf(s.playerBattleCarrierId), battleAnchor(s.playerAnchorWorldId, s.playerAnchorX, s.playerAnchorY, s.playerAnchorZ, s.playerAnchorYaw));
                        applyBattleAnchor(findWolf(s.wildBattleCarrierId), battleAnchor(s.wildAnchorWorldId, s.wildAnchorX, s.wildAnchorY, s.wildAnchorZ, s.wildAnchorYaw));
                    }
                } catch (Throwable ignored) {}
            }
        }.runTaskTimer(plugin, 5L, 5L);
    }

    private Wolf findWolf(UUID id) {
        if (id == null) return null;
        for (World world : Bukkit.getWorlds()) {
            try {
                Entity ent = world.getEntity(id);
                if (ent instanceof Wolf w) return w;
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private Location battleAnchor(UUID worldId, double x, double y, double z, float yaw) {
        if (worldId == null) return null;
        for (World world : Bukkit.getWorlds()) {
            if (!world.getUID().equals(worldId)) continue;
            Location loc = new Location(world, x, y, z, yaw, 0.0f);
            return loc;
        }
        return null;
    }

    private void applyBattleAnchor(Wolf wolf, Location anchor) {
        if (wolf == null || anchor == null || wolf.isDead()) return;
        try { wolf.setTarget(null); } catch (Throwable ignored) {}
        try { wolf.setVelocity(new Vector(0, 0, 0)); } catch (Throwable ignored) {}
        Location cur = wolf.getLocation();
        if (!cur.getWorld().equals(anchor.getWorld())) {
            try { wolf.teleport(anchor); } catch (Throwable ignored) {}
            return;
        }
        double dx = cur.getX() - anchor.getX();
        double dy = cur.getY() - anchor.getY();
        double dz = cur.getZ() - anchor.getZ();
        double distSq = dx*dx + dy*dy + dz*dz;
        if (distSq > 0.90D) {
            try { wolf.teleport(anchor); } catch (Throwable ignored) {}
        }
    }

    public boolean isInBattle(UUID playerId) {
        return sessions.containsKey(playerId);
    }

    /** Get the battle session of a player if present. */
    public BattleSession getSession(UUID playerId) {
        if (playerId == null) return null;
        return sessions.get(playerId);
    }

    /** Get lock owner of a wild entity (wolf uuid) if that wild entity is engaged in a battle. */
    public UUID getWildLockOwner(UUID wildEntityId) {
        if (wildEntityId == null) return null;
        return wildLocks.get(wildEntityId);
    }

    private static String pvpKey(UUID a, UUID b) {
        if (a == null || b == null) return null;
        return (a.compareTo(b) <= 0) ? (a.toString() + ":" + b.toString()) : (b.toString() + ":" + a.toString());
    }

    
    public boolean isWildInBattle(UUID wildEntityId) {
        return wildEntityId != null && wildLocks.containsKey(wildEntityId);
    }

    private void registerSession(BattleSession s) {
        sessions.put(s.playerId, s);
        if (s.wildEntityId != null) {
            wildLocks.put(s.wildEntityId, s.playerId);
        }
    }

    private BattleSession removeSessionInternal(UUID playerId) {
        if (playerId == null) return null;
        return sessions.remove(playerId);
    }

    private void removeSession(UUID playerId) {
        BattleSession s = removeSessionInternal(playerId);
        // Clean battle-only volatile state to avoid leaking into persisted storage.
        if (s != null) {
            try {
                if (s.playerMon != null) {
                    s.playerMon.resetBattleStages();
                    s.playerMon.resetBattleVolatiles();
                }
            } catch (Throwable ignored) {}
        }
        if (s != null && s.wildEntityId != null) {
            UUID owner = wildLocks.get(s.wildEntityId);
            if (owner != null && owner.equals(playerId)) {
                wildLocks.remove(s.wildEntityId);
            }
        }

        // Cleanup battle visuals (restore wild if still alive)
        if (s != null) {
            try { cleanupBattleVisuals(s, true); } catch (Throwable ignored) {}
        }
    }

    private void freezeCarrier(Wolf w) {
        if (w == null) return;
        try { w.setAI(false); } catch (Throwable ignored) {}
        try { w.setCollidable(false); } catch (Throwable ignored) {}
        try { w.setInvulnerable(true); } catch (Throwable ignored) {}
        try { w.setGravity(false); } catch (Throwable ignored) {}
        try { w.setVelocity(new Vector(0, 0, 0)); } catch (Throwable ignored) {}
        try { w.setTarget(null); } catch (Throwable ignored) {}
    }

    private void unfreezeCarrier(Wolf w, boolean restoreAi) {
        if (w == null) return;
        try { w.setCollidable(true); } catch (Throwable ignored) {}
        try { w.setInvulnerable(false); } catch (Throwable ignored) {}
        try { w.setGravity(true); } catch (Throwable ignored) {}
        try { w.setVelocity(new Vector(0, 0, 0)); } catch (Throwable ignored) {}
        try { if (restoreAi) w.setAI(true); } catch (Throwable ignored) {}
    }

    private double speciesVisualScale(String speciesId) {
        if (speciesId == null || speciesId.isBlank()) return 1.0D;
        try {
            double s = plugin.getConfig().getDouble("pokemon-visuals." + speciesId + ".scale", Double.NaN);
            if (Double.isFinite(s) && s > 0.05D) return s;
        } catch (Throwable ignored) {}
        try {
            double s = plugin.getConfig().getDouble("visuals.scale", 1.0D);
            if (Double.isFinite(s) && s > 0.05D) return s;
        } catch (Throwable ignored) {}
        return 1.0D;
    }

    private double battleSpacing(PokemonInstance playerMon, PokemonInstance wildMon) {
        double a = playerMon == null ? 1.0D : speciesVisualScale(playerMon.speciesId);
        double b = wildMon == null ? 1.0D : speciesVisualScale(wildMon.speciesId);
        return Math.max(2.5D, Math.min(5.0D, 3.2D + ((a + b) - 2.0D) * 0.7D));
    }

    private boolean isBattlePassable(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        Material t = loc.getBlock().getType();
        return t.isAir() || t.isTransparent() || t == Material.WATER;
    }

    private boolean isWaterAt(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        return loc.getBlock().getType() == Material.WATER || loc.clone().add(0, -1, 0).getBlock().getType() == Material.WATER;
    }

    private Location fitBattleAnchor(Location loc) {
        if (loc == null || loc.getWorld() == null) return null;
        for (int dy = -2; dy <= 3; dy++) {
            Location tryLoc = loc.clone().add(0, dy, 0);
            if (!isBattlePassable(tryLoc) || !isBattlePassable(tryLoc.clone().add(0, 1, 0))) continue;
            Location below = tryLoc.clone().add(0, -1, 0);
            Material floor = below.getBlock().getType();
            if (floor == Material.WATER || tryLoc.getBlock().getType() == Material.WATER) {
                int by = Math.max(below.getBlockY(), tryLoc.getBlockY());
                return new Location(tryLoc.getWorld(), tryLoc.getX(), by + 1.02D, tryLoc.getZ(), tryLoc.getYaw(), 0.0f);
            }
            if (floor.isSolid()) {
                return tryLoc;
            }
        }
        return null;
    }

    private void storeAnchor(BattleSession s, boolean playerSide, Location loc, float yaw) {
        if (s == null || loc == null || loc.getWorld() == null) return;
        if (playerSide) {
            s.playerAnchorWorldId = loc.getWorld().getUID();
            s.playerAnchorX = loc.getX(); s.playerAnchorY = loc.getY(); s.playerAnchorZ = loc.getZ();
            s.playerAnchorYaw = yaw; s.playerAnchorWater = isWaterAt(loc);
        } else {
            s.wildAnchorWorldId = loc.getWorld().getUID();
            s.wildAnchorX = loc.getX(); s.wildAnchorY = loc.getY(); s.wildAnchorZ = loc.getZ();
            s.wildAnchorYaw = yaw; s.wildAnchorWater = isWaterAt(loc);
        }
    }

    private Location resolvePlayerBattleAnchor(Player player) {
        Location base = player.getLocation();
        Vector forward = base.getDirection().clone();
        forward.setY(0);
        if (forward.lengthSquared() < 0.0001D) forward = new Vector(0, 0, 1); else forward.normalize();
        Vector right = new Vector(-forward.getZ(), 0, forward.getX()).normalize();
        double[][] offsets = {
                {2.05D, 1.05D}, {2.05D, -1.05D}, {1.85D, 1.25D}, {1.85D, -1.25D},
                {2.35D, 0.75D}, {2.35D, -0.75D}, {1.55D, 0.95D}, {1.55D, -0.95D},
                {2.65D, 0.45D}, {2.65D, -0.45D}
        };
        for (double[] off : offsets) {
            Location cand = base.clone().add(forward.clone().multiply(off[0])).add(right.clone().multiply(off[1])).add(0, 0.15D, 0);
            Location fit = fitBattleAnchor(cand);
            if (fit != null) return fit;
        }
        return fitBattleAnchor(base.clone().add(forward.clone().multiply(1.8D)).add(0, 0.15D, 0));
    }

    private Location resolveWildBattleAnchor(Player player, Wolf wildWolf, Location playerAnchor, double spacing) {
        if (player == null || wildWolf == null || playerAnchor == null) return null;
        Location wildBase = wildWolf.getLocation();
        Vector toWild = wildBase.toVector().subtract(playerAnchor.toVector());
        toWild.setY(0);
        if (toWild.lengthSquared() < 0.0001D) {
            toWild = player.getLocation().getDirection().clone();
            toWild.setY(0);
        }
        if (toWild.lengthSquared() < 0.0001D) toWild = new Vector(0, 0, 1);
        toWild.normalize();
        Vector side = new Vector(-toWild.getZ(), 0, toWild.getX()).normalize();
        double[] lateral = {0.0D, 0.55D, -0.55D, 1.0D, -1.0D};
        double[] longitudinal = {spacing, spacing + 0.4D, Math.max(2.4D, spacing - 0.5D)};
        for (double d : longitudinal) {
            for (double l : lateral) {
                Location cand = playerAnchor.clone().add(toWild.clone().multiply(d)).add(side.clone().multiply(l));
                Location fit = fitBattleAnchor(cand);
                if (fit != null) return fit;
            }
        }
        return fitBattleAnchor(wildBase.clone());
    }

    private void startBattleIntro(BattleSession s, boolean includePlayerSendout) {
        if (s == null || plugin.getBridgeSyncManager() == null) return;
        s.introStage = 1;
        UUID playerId = s.playerBattleCarrierId;
        UUID wildId = s.wildBattleCarrierId;
        if (includePlayerSendout && playerId != null) {
            try { plugin.getBridgeSyncManager().triggerCarrierAnimation(playerId, "sendout", 12L); } catch (Throwable ignored) {}
        }
        new BukkitRunnable() {
            @Override public void run() {
                try {
                    if (playerId != null) plugin.getBridgeSyncManager().triggerCarrierAnimation(playerId, "cry", 20L);
                    if (wildId != null) plugin.getBridgeSyncManager().triggerCarrierAnimation(wildId, "cry", 20L);
                    s.introStage = 2;
                } catch (Throwable ignored) {}
            }
        }.runTaskLater(plugin, includePlayerSendout ? 10L : 2L);
        new BukkitRunnable() {
            @Override public void run() {
                try {
                    if (playerId != null) plugin.getBridgeSyncManager().triggerCarrierAnimation(playerId, "battle_idle", 36L);
                    if (wildId != null) plugin.getBridgeSyncManager().triggerCarrierAnimation(wildId, "battle_idle", 36L);
                    s.introStage = 3;
                } catch (Throwable ignored) {}
            }
        }.runTaskLater(plugin, includePlayerSendout ? 24L : 14L);
    }

    /** Spawn/refresh in-world battle visuals for a wild battle. */
    private void ensureBattleVisuals(Player player, BattleSession s, Wolf wildWolf, PokemonInstance playerMon, Species playerSpecies, Wolf existingPlayerCarrier) {
        if (player == null || s == null || wildWolf == null || playerMon == null || playerSpecies == null) return;
        if (s.visualsSpawned) return;

        try {
            s.wildOriginalWorldId = wildWolf.getWorld().getUID();
            Location ol = wildWolf.getLocation();
            s.wildOriginalX = ol.getX();
            s.wildOriginalY = ol.getY();
            s.wildOriginalZ = ol.getZ();
            s.wildOriginalYaw = ol.getYaw();
            s.wildOriginalPitch = ol.getPitch();
            s.wildOriginalAi = wildWolf.hasAI();
        } catch (Throwable ignored) {}

        Location playerAnchor = resolvePlayerBattleAnchor(player);
        if (playerAnchor == null) playerAnchor = player.getLocation().clone().add(0, 0.15D, 0);
        Location wildAnchor = resolveWildBattleAnchor(player, wildWolf, playerAnchor, battleSpacing(playerMon, s.wildMon));
        if (wildAnchor == null) wildAnchor = wildWolf.getLocation().clone();

        Vector faceDir = wildAnchor.toVector().subtract(playerAnchor.toVector());
        faceDir.setY(0);
        if (faceDir.lengthSquared() < 0.0001D) faceDir = player.getLocation().getDirection().clone().setY(0);
        if (faceDir.lengthSquared() < 0.0001D) faceDir = new Vector(0, 0, 1);
        faceDir.normalize();
        float playerYaw = playerAnchor.clone().setDirection(faceDir).getYaw();
        float wildYaw = wildAnchor.clone().setDirection(faceDir.clone().multiply(-1)).getYaw();
        playerAnchor.setYaw(playerYaw);
        wildAnchor.setYaw(wildYaw);
        storeAnchor(s, true, playerAnchor, playerYaw);
        storeAnchor(s, false, wildAnchor, wildYaw);

        Wolf myCarrier = null;
        boolean reusedExisting = false;
        if (existingPlayerCarrier != null && existingPlayerCarrier.isValid() && !existingPlayerCarrier.isDead()) {
            myCarrier = existingPlayerCarrier;
            reusedExisting = true;
            try {
                Integer slot = myCarrier.getPersistentDataContainer().get(plugin.KEY_PARTY_SLOT, org.bukkit.persistence.PersistentDataType.INTEGER);
                if (slot != null) plugin.getSummonManager().clearActiveSlot(player.getUniqueId(), slot);
            } catch (Throwable ignored) {}
            try { myCarrier.teleport(playerAnchor); } catch (Throwable ignored) {}
        } else {
            myCarrier = plugin.getSummonManager().spawnBattleCarrier(player, playerMon, playerSpecies, playerAnchor);
        }
        if (myCarrier != null) {
            freezeCarrier(myCarrier);
            try { myCarrier.setRotation(playerYaw, 0.0f); } catch (Throwable ignored) {}
            s.playerBattleCarrierId = myCarrier.getUniqueId();
        }

        try { wildWolf.teleport(wildAnchor); } catch (Throwable ignored) {}
        freezeCarrier(wildWolf);
        try { wildWolf.setRotation(wildYaw, 0.0f); } catch (Throwable ignored) {}
        s.wildBattleCarrierId = wildWolf.getUniqueId();

        s.visualsSpawned = true;
        startBattleIntro(s, true);
    }

    /** Replace player's battle carrier when switching/auto-switching. */
    private void refreshPlayerBattleCarrier(Player player, BattleSession s, PokemonInstance newMon, Species newS) {
        if (player == null || s == null || newMon == null || newS == null) return;

        if (s.playerBattleCarrierId != null) {
            Entity ent = player.getWorld().getEntity(s.playerBattleCarrierId);
            if (ent instanceof Wolf w) {
                try { if (plugin.getBridgeSyncManager() != null) plugin.getBridgeSyncManager().triggerCarrierAnimation(w.getUniqueId(), "withdraw", 10L); } catch (Throwable ignored) {}
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> plugin.getSummonManager().destroyCarrier(w), 10L);
            }
        }
        s.playerBattleCarrierId = null;

        Location myLoc = battleAnchor(s.playerAnchorWorldId, s.playerAnchorX, s.playerAnchorY, s.playerAnchorZ, s.playerAnchorYaw);
        if (myLoc == null) myLoc = resolvePlayerBattleAnchor(player);
        if (myLoc == null) myLoc = player.getLocation().clone().add(0, 0.2, 0);
        Wolf myCarrier = plugin.getSummonManager().spawnBattleCarrier(player, newMon, newS, myLoc);
        if (myCarrier != null) {
            freezeCarrier(myCarrier);
            try { myCarrier.setRotation(s.playerAnchorYaw, 0.0f); } catch (Throwable ignored) {}
            s.playerBattleCarrierId = myCarrier.getUniqueId();
            try {
                if (plugin.getBridgeSyncManager() != null) {
                    plugin.getBridgeSyncManager().triggerCarrierAnimation(myCarrier.getUniqueId(), "sendout", 12L);
                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                        try { plugin.getBridgeSyncManager().triggerCarrierAnimation(myCarrier.getUniqueId(), "cry", 20L); } catch (Throwable ignored) {}
                    }, 10L);
                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                        try { plugin.getBridgeSyncManager().triggerCarrierAnimation(myCarrier.getUniqueId(), "battle_idle", 36L); } catch (Throwable ignored) {}
                    }, 24L);
                }
            } catch (Throwable ignored) {}
        }
    }

    /** Cleanup battle visuals. If restoreWild is true, restore wild entity on escape. */
    private void cleanupBattleVisuals(BattleSession s, boolean restoreWild) {
        if (s == null) return;

        // Remove player's battle carrier
        if (s.playerBattleCarrierId != null) {
            Player p = Bukkit.getPlayer(s.playerId);
            if (p != null) {
                Entity ent = p.getWorld().getEntity(s.playerBattleCarrierId);
                if (ent instanceof Wolf w) {
                    plugin.getSummonManager().destroyCarrier(w);
                }
            }
            s.playerBattleCarrierId = null;
        }

        // Restore wild if battle ended without defeat/capture
        if (restoreWild && s.wildBattleCarrierId != null) {
            for (World world : Bukkit.getWorlds()) {
                Entity ent = world.getEntity(s.wildBattleCarrierId);
                if (ent instanceof Wolf wolf) {
                    try {
                        if (s.wildOriginalWorldId != null && world.getUID().equals(s.wildOriginalWorldId)) {
                            Location back = new Location(world, s.wildOriginalX, s.wildOriginalY, s.wildOriginalZ, s.wildOriginalYaw, s.wildOriginalPitch);
                            wolf.teleport(back);
                        }
                    } catch (Throwable ignored) {}
                    unfreezeCarrier(wolf, s.wildOriginalAi);
                    break;
                }
            }
        }

        s.wildBattleCarrierId = null;
        s.visualsSpawned = false;
    }

public void shutdown() {
        sessions.clear();
        wildLocks.clear();
        pvpPending.clear();
        pvpBattles.clear();
        pvpKeyByPlayer.clear();
    }

    /**
     * Remove the given player from any spectating session.
     * Spectators are chat-only and do not open the battle GUI.
     */
    public void removeSpectator(UUID spectatorId) {
        if (spectatorId == null) return;
        for (BattleSession s : sessions.values()) {
            if (s == null) continue;
            s.spectators.remove(spectatorId);
        }
    }

    /** Find an ongoing battle session by a battle visual carrier wolf UUID. */
    public BattleSession findSessionByCarrier(UUID carrierWolfId) {
        if (carrierWolfId == null) return null;
        for (BattleSession s : sessions.values()) {
            if (s == null || s.finished) continue;
            if (carrierWolfId.equals(s.playerBattleCarrierId) || carrierWolfId.equals(s.wildBattleCarrierId)) {
                return s;
            }
        }
        return null;
    }

    /**
     * Enter chat-only spectate for the given battle session.
     * For PvP battles, the spectator will be attached to BOTH sides so they receive all lines.
     */
    public boolean enterSpectate(Player spectator, BattleSession s) {
        if (spectator == null || s == null || s.finished) return false;
        UUID sid = spectator.getUniqueId();
        if (sid == null) return false;
        // Can't spectate while battling.
        if (isInBattle(sid)) {
            spectator.sendMessage(plugin.getLang().ui("spectate.cannot_while_battling", "§e你正在战斗中，无法观战。\n§7请先结束当前战斗。"));
            return true;
        }
        // Move spectator from other sessions.
        removeSpectator(sid);

        // Attach to this session.
        s.spectators.add(sid);

        // For PvP, also attach to opponent session.
        if (s.pvp && s.pvpOpponentId != null) {
            BattleSession other = sessions.get(s.pvpOpponentId);
            if (other != null && !other.finished) {
                other.spectators.add(sid);
            }
        }

        String title;
        if (s.pvp && s.pvpOpponentId != null) {
            Player a = Bukkit.getPlayer(s.playerId);
            Player b = Bukkit.getPlayer(s.pvpOpponentId);
            String an = (a != null ? a.getName() : plugin.getLang().ui("common.player_a","玩家A"));
            String bn = (b != null ? b.getName() : plugin.getLang().ui("common.player_b","玩家B"));
            title = plugin.getLang().uiFmt("spectate.enter_pvp", "§7你已进入§a观战§7：§f{a} §7vs §f{b}§7（仅聊天）", java.util.Map.of("a", an, "b", bn));
        } else {
            Player owner = Bukkit.getPlayer(s.playerId);
            String on = (owner != null ? owner.getName() : plugin.getLang().ui("common.player","玩家"));
            title = plugin.getLang().uiFmt("spectate.enter_wild", "§7你已进入§a观战§7：§f{player}§7 的战斗（仅聊天）", java.util.Map.of("player", on));
        }
        spectator.sendMessage(title);
        spectator.sendMessage(plugin.getLang().ui("spectate.hint_exit", "§7输入 §f/pokedemo spectateoff §7可退出观战。"));
        if (s.statusLine != null && !s.statusLine.isEmpty()) {
            spectator.sendMessage(s.statusLine);
        }
        return true;
    }


    private boolean hasVanillaBattleInventoryOpen(Player player, BattleSession s) {
        if (player == null || s == null) return false;
        try {
            org.bukkit.inventory.InventoryView view = player.getOpenInventory();
            if (view == null) return false;
            org.bukkit.inventory.Inventory top = view.getTopInventory();
            if (top == null) return false;
            if (!(top.getHolder() instanceof GuiHolder gh)) return false;
            if (gh.battleSession != s) return false;
            return gh.type == GuiType.BATTLE
                    || gh.type == GuiType.BATTLE_SWITCH
                    || gh.type == GuiType.BATTLE_ITEM_SELECT
                    || gh.type == GuiType.BATTLE_BALL_SELECT;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void renderOpenVanillaBattleInventory(Player player, BattleSession s) {
        if (!hasVanillaBattleInventoryOpen(player, s)) return;
        try {
            org.bukkit.inventory.Inventory top = player.getOpenInventory().getTopInventory();
            if (top == null) return;
            GuiHolder gh = (GuiHolder) top.getHolder();
            if (gh == null) return;
            switch (gh.type) {
                case BATTLE -> renderBattle(top, player, s);
                case BATTLE_SWITCH -> renderBattleSwitch(top, player, s);
                case BATTLE_ITEM_SELECT -> renderBattleItemSelect(top, player, s);
                case BATTLE_BALL_SELECT -> renderBattleBallSelect(top, player, s);
            }
        } catch (Throwable ignored) {}
    }
    private void renderBattleSwitch(Inventory inv, Player player, BattleSession s) {
        if (player == null || s == null || s.finished) return;
        try {
            openBattleSwitchGui(player, s);
        } catch (Throwable ignored) {}
    }

    private void renderBattleItemSelect(Inventory inv, Player player, BattleSession s) {
        if (player == null || s == null || s.finished) return;
        try {
            openBattleItemSelectGui(player, s);
        } catch (Throwable ignored) {}
    }

    private void renderBattleBallSelect(Inventory inv, Player player, BattleSession s) {
        if (player == null || s == null || s.finished) return;
        try {
            openBallSelectGui(player, s);
        } catch (Throwable ignored) {}
    }

    private void sendBattleLine(BattleSession s, String msg) {
        if (s == null || msg == null) return;
        try {
            String plain = msg.replaceAll("§.", "");
            if (s.recentLog == null) s.recentLog = new java.util.ArrayDeque<>();
            s.recentLog.addLast(plain);
            while (s.recentLog.size() > 6) s.recentLog.removeFirst();
        } catch (Throwable ignored) {}
        Player p = Bukkit.getPlayer(s.playerId);
        if (p != null) p.sendMessage(msg);
        if (!s.spectators.isEmpty()) {
            for (UUID sid : new ArrayList<>(s.spectators)) {
                Player sp = Bukkit.getPlayer(sid);
                if (sp != null) {
                    sp.sendMessage(msg);
                }
            }
        }
        try {
            if (p != null && plugin.getBridgeSyncManager() != null) plugin.getBridgeSyncManager().syncBattleState(p);
            if (s.pvp && s.pvpOpponentId != null) {
                Player opp = Bukkit.getPlayer(s.pvpOpponentId);
                if (opp != null && plugin.getBridgeSyncManager() != null) plugin.getBridgeSyncManager().syncBattleState(opp);
            }
        } catch (Throwable ignored) {}
    }


        /** Consume best available pokeball in player's inventory. Returns the itemId if consumed. */
    private String consumeBestBallId(Player player) {
        if (player == null) return null;
        String[] pref = new String[]{"master_ball", "ultra_ball", "great_ball", "poke_ball"};
        for (String id : pref) {
            for (int i = 0; i < player.getInventory().getSize(); i++) {
                ItemStack st = player.getInventory().getItem(i);
                String itemId = items.getItemId(st);
                if (itemId != null && itemId.equalsIgnoreCase(id)) {
                    if (st.getAmount() > 1) st.setAmount(st.getAmount() - 1);
                    else player.getInventory().setItem(i, null);
                    return id;
                }
            }
        }
        return null;
    }

    private String ballNameKey(String ballId) {
        if (ballId == null) return "item.poke_ball";
        return switch (ballId.toLowerCase()) {
            case "great_ball" -> "item.great_ball";
            case "ultra_ball" -> "item.ultra_ball";
            case "master_ball" -> "item.master_ball";
            default -> "item.poke_ball";
        };
    }

    private double ballBonus(String ballId) {
        if (ballId == null) return 1.0;
        return switch (ballId.toLowerCase()) {
            case "great_ball" -> 1.5;
            case "ultra_ball" -> 2.0;
            case "master_ball" -> 255.0;
            default -> 1.0;
        };
    }

    private boolean isMasterBall(String ballId) {
        return ballId != null && "master_ball".equalsIgnoreCase(ballId);
    }


    /** Attempt to capture the current wild opponent using a pokeball from inventory. */
    private void tryThrowBall(Player player, BattleSession s) {
        if (player == null || s == null || s.finished) return;
        if (s.pvp) {
            player.sendMessage(plugin.getLang().ui("battle.pvp.no_capture", "§cPVP战斗中严格禁止捕捉！"));
            return;
        }
        if (s.wildEntityId == null || s.wildMon == null) {
            player.sendMessage(plugin.getLang().ui("battle.no_capture", "§c这场战斗无法捕捉。"));
            return;
        }
        if (s.wildMon.currentHp <= 0) {
            sendBattleLine(s, plugin.getLang().ui("battle.capture.target_fainted", "§7对方已昏厥，无法捕捉。"));
            return;
        }
        openBallSelectGui(player, s);
    }

    /** Open a GUI letting player choose which ball to throw (from inventory). */
    private void openBallSelectGui(Player player, BattleSession s) {
        suppressBattleGuiReopen(s, 1500);
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
            sendBattleLine(s, "§7" + plugin.getLang().tr("msg.no_balls", "你背包里没有可用的精灵球。"));
            return;
        }

        GuiHolder holder = new GuiHolder(GuiType.BATTLE_BALL_SELECT, player.getUniqueId());
        holder.battleSession = s;

        // 18 slots: 0-8 balls, 16 item button, 17 back button.
        org.bukkit.inventory.Inventory inv = org.bukkit.Bukkit.createInventory(holder, 18,
                plugin.getLang().tr("ui.select_ball", "选择精灵球"));

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
                lore.add(plugin.getLang().uiFmt("gui.common.click_use_have", "§8点击使用（拥有 {count}）", java.util.Map.of("count", String.valueOf(cnt))));
                meta.setLore(lore);
                button.setItemMeta(meta);
            }
            inv.setItem(slot++, button);
        }

        inv.setItem(16, UtilGui.button(org.bukkit.Material.CHEST,
                plugin.getLang().ui("battle.gui.bag.title", "§b道具"),
                java.util.List.of(plugin.getLang().ui("battle.gui.bag.lore", "§7打开战斗道具选择界面"))));

        // Back button (return to battle GUI)
        inv.setItem(17, UtilGui.button(org.bukkit.Material.ARROW,
                plugin.getLang().ui("gui.common.back", "§e返回"),
                java.util.List.of(plugin.getLang().ui("gui.common.back_to_battle","§7返回战斗界面"))));
        player.openInventory(inv);
    }

    /** Handle click in the ball selection GUI. */
    public void handleBallSelectClick(Player player, GuiHolder holder, int rawSlot, org.bukkit.inventory.ItemStack clicked) {
        if (player == null || holder == null || holder.battleSession == null) return;
        BattleSession s = holder.battleSession;
        if (s.finished) {
            player.closeInventory();
            return;
        }
        if (s.pvp) {
            player.sendMessage(plugin.getLang().ui("battle.pvp.no_capture", "§cPVP战斗中严格禁止捕捉！"));
            player.closeInventory();
            return;
        }
        // Item selector from ball menu
        if (rawSlot == 16) {
            player.closeInventory();
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                BattleSession cur = sessions.get(player.getUniqueId());
                if (cur != null && !cur.finished) openBattleItemSelectGui(player, cur);
            });
            return;
        }

        // Back
        if (rawSlot == 17) {
            player.closeInventory();
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                BattleSession cur = sessions.get(player.getUniqueId());
                if (cur != null && !cur.finished) openBattleGui(player, cur);
            });
            return;
        }

        if (rawSlot < 0 || rawSlot >= 9) return;
        if (clicked == null || clicked.getType() == org.bukkit.Material.AIR) return;

                String id = plugin.getItems().getItemId(clicked);
        if (id == null) return;
        ItemDef def = plugin.getItemRegistry().get(id);
        if (def == null) return;
        if (def.type != ItemType.BALL) return;

        // Consume one ball of this type from player's inventory
        if (!consumeOneBall(player, def.id)) {
            sendBattleLine(s, "§7" + plugin.getLang().tr("msg.no_balls", "你背包里没有可用的精灵球。"));
            player.closeInventory();
            return;
        }

        // Close selector, then reopen battle GUI next tick so capture animation/messages render in the main GUI.
        player.closeInventory();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            BattleSession cur = sessions.get(player.getUniqueId());
            if (cur == null || cur.finished) return;
            try { openBattleGui(player, cur); } catch (Exception ignored) {}
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                BattleSession cur2 = sessions.get(player.getUniqueId());
                if (cur2 == null || cur2.finished) return;
                throwBallSelected(player, cur2, def.id);
                // reopen watchdog: some clients/plugins may close inventory during capture messages
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    BattleSession cur3 = sessions.get(player.getUniqueId());
                    if (cur3 == null || cur3.finished) return;
                    try {
                        Object holderObj = player.getOpenInventory().getTopInventory().getHolder();
                        if (!(holderObj instanceof GuiHolder gh) || gh.type != GuiType.BATTLE) openBattleGui(player, cur3);
                    } catch (Throwable ignored) {}
                }, 10L);
            }, 1L);
        });
    }

    private boolean consumeOneBall(Player player, String ballId) {
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

    
    /** Open a GUI for selecting battle items from inventory. */
    private void openBattleItemSelectGui(Player player, BattleSession s) {
        if (player == null || s == null || s.finished) return;
        suppressBattleGuiReopen(s, 1500);
        java.util.Map<String, Integer> counts = new java.util.LinkedHashMap<>();
        for (org.bukkit.inventory.ItemStack it : player.getInventory().getContents()) {
            if (it == null || it.getType() == org.bukkit.Material.AIR) continue;
            String id = plugin.getItems().getItemId(it);
            if (id == null) continue;
            ItemDef def = plugin.getItemRegistry().get(id);
            if (def == null) continue;
            if (!(def.type == ItemType.BATTLE || def.type == ItemType.MEDICINE || def.type == ItemType.STATUS_CURE || def.type == ItemType.REVIVE)) continue;
            counts.put(def.id, counts.getOrDefault(def.id, 0) + it.getAmount());
        }
        if (counts.isEmpty()) {
            sendBattleLine(s, "§7" + plugin.getLang().tr("msg.no_battle_items", "你背包里没有可用的道具。"));
            return;
        }

        GuiHolder holder = new GuiHolder(GuiType.BATTLE_ITEM_SELECT, player.getUniqueId());
        holder.battleSession = s;

        org.bukkit.inventory.Inventory inv = org.bukkit.Bukkit.createInventory(holder, 27,
                plugin.getLang().tr("ui.select_battle_item", "选择道具"));

        int slot = 0;
        for (java.util.Map.Entry<String, Integer> e : counts.entrySet()) {
            if (slot >= 27) break;
            String itemId = e.getKey();
            int cnt = e.getValue();
            ItemDef def = plugin.getItemRegistry().get(itemId);
            org.bukkit.inventory.ItemStack button = plugin.getItems().createItem(def, plugin.getLang(), Math.min(cnt, 64));
            org.bukkit.inventory.meta.ItemMeta meta = button.getItemMeta();
            if (meta != null) {
                java.util.List<String> lore = meta.hasLore() ? new java.util.ArrayList<>(meta.getLore()) : new java.util.ArrayList<>();
                lore.add(plugin.getLang().uiFmt("gui.common.click_use_have", "§8点击使用（拥有 {count}）", java.util.Map.of("count", String.valueOf(cnt))));
                meta.setLore(lore);
                button.setItemMeta(meta);
            }
            inv.setItem(slot++, button);
        }
        inv.setItem(26, UtilGui.button(org.bukkit.Material.ARROW, plugin.getLang().ui("gui.common.back", "§e返回"), java.util.List.of(plugin.getLang().ui("gui.battle.back_to_battle", "§7返回战斗界面"))));
        player.openInventory(inv);
    }

    /** Handle click in the battle item selection GUI. */
    public void handleBattleItemSelectClick(Player player, GuiHolder holder, int rawSlot, org.bukkit.inventory.ItemStack clicked) {
        if (player == null || holder == null || holder.battleSession == null) return;
        BattleSession s = holder.battleSession;
        if (s.finished) { player.closeInventory(); return; }

        if (rawSlot == 26) {
            player.closeInventory();
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                BattleSession cur = sessions.get(player.getUniqueId());
                if (cur != null && !cur.finished) openBattleGui(player, cur);
            });
            return;
        }
        if (rawSlot < 0 || rawSlot >= 27) return;
        if (clicked == null || clicked.getType() == org.bukkit.Material.AIR) return;

        String id = plugin.getItems().getItemId(clicked);
        if (id == null) return;
        ItemDef def = plugin.getItemRegistry().get(id);
        if (def == null) return;
        if (!(def.type == ItemType.BATTLE || def.type == ItemType.MEDICINE || def.type == ItemType.STATUS_CURE || def.type == ItemType.REVIVE)) return;

        if (def.id != null && def.id.equalsIgnoreCase("poke_doll")) {
            // Poke Doll: escape immediately from wild battles (no wild move afterwards).
            String err = canUseBattleItem(s, def);
            if (err != null) {
                sendBattleLine(s, err);
                player.closeInventory();
                openBattleGui(player, s);
                return;
            }
            if (!consumeOneAnyItem(player, def.id)) {
                sendBattleLine(s, "§7" + plugin.getLang().tr("msg.no_battle_items", "你背包里没有可用的道具。"));
                player.closeInventory();
                return;
            }
            player.closeInventory();
            applyBattleItem(player, s, def);
            return;
        }

        // Validate first; do not consume if it would have no effect.
        String err = (def.type == ItemType.BATTLE)
                ? canUseBattleItem(s, def)
                : canUseBattleMedicineOrCure(s, def);
        if (err != null) {
            sendBattleLine(s, err);
            player.closeInventory();
            openBattleGui(player, s);
            return;
        }

        if (!consumeOneAnyItem(player, def.id)) {
            sendBattleLine(s, "§7" + plugin.getLang().tr("msg.no_battle_items", "你背包里没有可用的道具。"));
            player.closeInventory();
            return;
        }

        player.closeInventory();

        // Using an item consumes your action this turn: player acts, then wild acts, then end-of-turn.
        startItemTurn(player, s, () -> {
            if (def.type == ItemType.BATTLE) {
                applyBattleItem(player, s, def);
            } else {
                applyBattleMedicineOrCure(player, s, def);
            }
        });

    }


    /** Returns an error message if the battle item cannot be used right now; null if usable. */
    private String canUseBattleItem(BattleSession s, ItemDef def) {
        if (s == null || def == null || s.finished) return plugin.getLang().ui("battle.item.cannot_use", "§7无法使用该道具。");
        LangManager lang = plugin.getLang();
        Species myS = (s.playerMon == null) ? null : dex.getSpecies(s.playerMon.effectiveSpeciesId());
        String myName = (lang == null || myS == null) ? (myS == null ? "我方精灵" : myS.name()) : lang.species(myS.id(), myS.name());
        String itemName = plugin.getLang().item(def.id, def.id);

        if (def.data != null && def.data.containsKey("stage")) {
            String stat = String.valueOf(def.data.get("stage"));
            int delta = 1;
            try { delta = Integer.parseInt(String.valueOf(def.data.getOrDefault("delta", 1))); } catch (Exception ignored) {}
            if (s.playerMon == null) return plugin.getLang().ui("battle.item.cannot_use", "§7无法使用该道具。");
            if ("special".equalsIgnoreCase(stat)) {
                boolean maxSpa = s.playerMon.stageSpa >= 6;
                boolean maxSpd = s.playerMon.stageSpd >= 6;
                if (maxSpa && maxSpd) return plugin.getLang().uiFmt("battle.item.stat_cannot_raise", "§7{mon} 的能力已经无法再提升了。", java.util.Map.of("mon", myName));
                return null;
            }
            int cur = switch (stat.toLowerCase()) {
                case "atk" -> s.playerMon.stageAtk;
                case "def" -> s.playerMon.stageDef;
                case "spa" -> s.playerMon.stageSpa;
                case "spd" -> s.playerMon.stageSpd;
                case "spe" -> s.playerMon.stageSpe;
                case "accuracy" -> s.playerMon.stageAccuracy;
                case "evasion" -> s.playerMon.stageEvasion;
                default -> 0;
            };
            if (delta > 0 && cur >= 6) return plugin.getLang().uiFmt("battle.item.stat_cannot_raise", "§7{mon} 的能力已经无法再提升了。", java.util.Map.of("mon", myName));
            if (delta < 0 && cur <= -6) return plugin.getLang().uiFmt("battle.item.stat_cannot_lower", "§7{mon} 的能力已经无法再降低了。", java.util.Map.of("mon", myName));
            return null;
        }

        if (def.data != null && Boolean.TRUE.equals(def.data.get("dire_hit"))) {
            if (s.playerMon == null) return plugin.getLang().ui("battle.item.cannot_use", "§7无法使用该道具。");
            if (s.playerMon.direHitActive) return plugin.getLang().uiFmt("battle.item.already_active", "§7{item} 已经生效了。", java.util.Map.of("item", itemName));
            return null;
        }

        if (def.data != null && def.data.containsKey("mist")) {
            if (s.playerMon == null) return plugin.getLang().ui("battle.item.cannot_use", "§7无法使用该道具。");
            if (s.playerMon.mistTurnsRemaining > 0) return plugin.getLang().uiFmt("battle.item.already_active", "§7{item} 已经生效了。", java.util.Map.of("item", itemName));
            return null;
        }

        if ("poke_doll".equalsIgnoreCase(def.id)) {
            if (s.wildEntityId == null) return plugin.getLang().uiFmt("battle.item.only_wild", "§7{item} 只能用于野外战斗。", java.util.Map.of("item", itemName));
            return null;
        }

        // Unknown battle item
        return plugin.getLang().uiFmt("battle.item.not_implemented", "§7该战斗道具暂未实现：{id}", java.util.Map.of("id", String.valueOf(def.id)));
    }

    /** Returns an error message if the medicine/status-cure/revive cannot be used right now; null if usable. */
    private String canUseBattleMedicineOrCure(BattleSession s, ItemDef def) {
        if (s == null || def == null || s.finished || s.playerMon == null) return plugin.getLang().ui("battle.item.cannot_use", "§7无法使用该道具。");

        LangManager lang = plugin.getLang();
        Species monS = dex.getSpecies(s.playerMon.effectiveSpeciesId());
        String monName = (lang == null || monS == null)
                ? (monS == null ? "我方精灵" : monS.name())
                : lang.species(monS.id(), monS.name());
        String itemName = plugin.getLang().item(def.id, def.id);

        if (def.type == ItemType.MEDICINE) {
            boolean healFull = false;
            int heal = 0;
            boolean cureAll = false;
            if (def.data != null) {
                try { healFull = Boolean.parseBoolean(String.valueOf(def.data.getOrDefault("heal_full", false))); } catch (Exception ignored) {}
                try { heal = Integer.parseInt(String.valueOf(def.data.getOrDefault("heal", 0))); } catch (Exception ignored) {}
                try { cureAll = Boolean.parseBoolean(String.valueOf(def.data.getOrDefault("cure_all", false))); } catch (Exception ignored) {}
            }
            if (s.playerMon.currentHp <= 0) return plugin.getLang().uiFmt("battle.item.fainted_no_heal", "§7{mon} 已经倒下，无法使用该回复道具（请使用复活类道具）。", java.util.Map.of("mon", monName));
            int max = (monS == null) ? Math.max(1, s.playerMon.currentHp) : s.playerMon.maxHp(monS);
            boolean hpFull = s.playerMon.currentHp >= max;
            boolean noStatus = (s.playerMon.status == null || s.playerMon.status.equalsIgnoreCase("none"));
            if (hpFull && (!cureAll || noStatus)) return plugin.getLang().uiFmt("battle.item.not_needed", "§7{mon} 现在不需要使用 {item}。", java.util.Map.of("mon", monName, "item", itemName));
            if (cureAll && noStatus && !healFull && heal <= 0) return plugin.getLang().uiFmt("battle.item.not_needed", "§7{mon} 现在不需要使用 {item}。", java.util.Map.of("mon", monName, "item", itemName));
            return null;
        }

        if (def.type == ItemType.STATUS_CURE) {
            if (s.playerMon.currentHp <= 0) return plugin.getLang().uiFmt("battle.item.fainted_no_status", "§7{mon} 已经倒下，无法使用状态药。", java.util.Map.of("mon", monName));
            String cure = (def.data == null) ? "" : String.valueOf(def.data.getOrDefault("cure", ""));
            if (cure == null || cure.isBlank()) return plugin.getLang().uiFmt("battle.item.bad_param_status", "§7该状态药参数异常，无法使用：{id}", java.util.Map.of("id", String.valueOf(def.id)));

            String st = (s.playerMon.status == null) ? "none" : s.playerMon.status;
            boolean needs;
            if (cure.equalsIgnoreCase("all") || cure.equalsIgnoreCase("any")) {
                needs = !st.equalsIgnoreCase("none");
            } else if (cure.equalsIgnoreCase("paralysis")) {
                needs = st.equalsIgnoreCase("paralyze") || st.equalsIgnoreCase("paralysis");
            } else if (cure.equalsIgnoreCase("freeze")) {
                needs = st.equalsIgnoreCase("freeze") || st.equalsIgnoreCase("frozen");
            } else {
                needs = st.equalsIgnoreCase(cure);
            }
            if (!needs) return plugin.getLang().uiFmt("battle.item.no_status_to_cure", "§7{mon} 当前没有需要治愈的异常状态。", java.util.Map.of("mon", monName));
            return null;
        }

        if (def.type == ItemType.REVIVE) {
            if (s.playerMon.currentHp > 0) return plugin.getLang().uiFmt("battle.item.not_fainted_revive", "§7{mon} 还没倒下，无法使用复活类道具。", java.util.Map.of("mon", monName));
            return null;
        }

        return plugin.getLang().uiFmt("battle.item.not_supported_in_battle", "§7该道具暂未支持战斗中使用：{id}", java.util.Map.of("id", String.valueOf(def.id)));
    }

    private boolean consumeOneAnyItem(Player player, String itemId) {
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            org.bukkit.inventory.ItemStack it = player.getInventory().getItem(i);
            if (it == null || it.getType() == org.bukkit.Material.AIR) continue;
            String id = plugin.getItems().getItemId(it);
            if (id == null) continue;
            if (!id.equals(itemId)) continue;
            ItemDef def = plugin.getItemRegistry().get(id);
            if (def == null) continue;
            if (!(def.type == ItemType.BATTLE || def.type == ItemType.MEDICINE || def.type == ItemType.STATUS_CURE || def.type == ItemType.REVIVE)) continue;

            int amt = it.getAmount();
            if (amt <= 1) player.getInventory().setItem(i, null);
            else it.setAmount(amt - 1);
            player.updateInventory();
            return true;
        }
        return false;
    }

    private void applyBattleItem(Player player, BattleSession s, ItemDef def) {
        if (def == null || s == null || s.finished) return;
        Species myS = dex.getSpecies(s.playerMon.effectiveSpeciesId());
        LangManager lang = plugin.getLang();
        String myName = (lang == null || myS == null) ? (myS == null ? "我方精灵" : myS.name()) : lang.species(myS.id(), myS.name());
        String itemName = plugin.getLang().item(def.id, def.id);

        // X items: stage boosts
        if (def.data != null && def.data.containsKey("stage")) {
            String stat = String.valueOf(def.data.get("stage"));
            int delta = 1;
            try { delta = Integer.parseInt(String.valueOf(def.data.getOrDefault("delta", 1))); } catch (Exception ignored) {}
            
            if ("special".equalsIgnoreCase(stat)) {
                s.playerMon.applyStage("spa", delta);
                s.playerMon.applyStage("spd", delta);
                sendBattleLine(s, plugin.getLang().uiFmt("battle.log.use_item.boost_sp", "§e{mon} 使用了 §f{item}§e！§d特殊提升了！", java.util.Map.of("mon", myName, "item", itemName)));
                return;
            }
            s.playerMon.applyStage(stat, delta);
            sendBattleLine(s, plugin.getLang().uiFmt("battle.log.use_item.boost_stat", "§e{mon} 使用了 §f{item}§e！§d{stat}提升了！", java.util.Map.of("mon", myName, "item", itemName, "stat", statToCn(stat))));
            return;
        }

        // Dire Hit: boost crit chance
        if (def.data != null && Boolean.TRUE.equals(def.data.get("dire_hit"))) {
            s.playerMon.direHitActive = true;
            sendBattleLine(s, plugin.getLang().uiFmt("battle.log.use_item.dire_hit", "§e{mon} 使用了 §f{item}§e！§d要害命中率提高了！", java.util.Map.of("mon", myName, "item", itemName)));
            return;
        }

        // Guard Spec: use mist-like protection
        if (def.data != null && def.data.containsKey("mist")) {
            int turns = 5;
            try { turns = Integer.parseInt(String.valueOf(def.data.getOrDefault("mist", 5))); } catch (Exception ignored) {}
            s.playerMon.mistTurnsRemaining = Math.max(s.playerMon.mistTurnsRemaining, turns);
            sendBattleLine(s, plugin.getLang().uiFmt("battle.log.use_item.guard_spec", "§e{mon} 使用了 §f{item}§e！§d能力下降被防止了！ §7({n}回合)", java.util.Map.of("mon", myName, "item", itemName, "n", String.valueOf(turns))));
            return;
        }

        
        // Poke Doll: escape from wild battle
        if ("poke_doll".equals(def.id)) {
            // Only works in wild battles
            if (s.wildEntityId != null) {
                sendBattleLine(s, plugin.getLang().uiFmt("battle.log.use_item.poke_doll", "§e{mon} 使用了 §f{item}§e！§d你成功逃走了！", java.util.Map.of("mon", myName, "item", itemName)));
                // End immediately; no wild action.
                Player p = Bukkit.getPlayer(s.playerId);
                if (p != null) {
                    try { p.closeInventory(); } catch (Throwable ignored) {}
                }
                endBattle(s.playerId, null);
                return;
            } else {
                sendBattleLine(s, plugin.getLang().uiFmt("battle.item.only_wild", "§7{item} 只能用于野外战斗。", java.util.Map.of("item", itemName)));
                return;
            }
        }

        // Should not reach here because we validate before consuming.
        sendBattleLine(s, plugin.getLang().uiFmt("battle.item.not_implemented", "§7该战斗道具暂未实现：{id}", java.util.Map.of("id", def.id)));
    }

    /**
     * Apply a medicine/status-cure/revive in battle to the player's currently active Pokémon.
     *
     * This is intentionally conservative: it only targets the active slot (playerMon).
     */
    private void applyBattleMedicineOrCure(Player player, BattleSession s, ItemDef def) {
        if (player == null || s == null || s.finished || def == null) return;
        if (s.playerMon == null) return;

        LangManager lang = plugin.getLang();
        Species monS = dex.getSpecies(s.playerMon.effectiveSpeciesId());
        String monName = (lang == null || monS == null)
                ? (monS == null ? "我方精灵" : monS.name())
                : lang.species(monS.id(), monS.name());
        String itemName = plugin.getLang().item(def.id, def.id);

        // MEDICINE: heal HP, and optionally cure all
        if (def.type == ItemType.MEDICINE) {
            boolean healFull = false;
            int heal = 0;
            boolean cureAll = false;
            if (def.data != null) {
                try { healFull = Boolean.parseBoolean(String.valueOf(def.data.getOrDefault("heal_full", false))); } catch (Exception ignored) {}
                try { heal = Integer.parseInt(String.valueOf(def.data.getOrDefault("heal", 0))); } catch (Exception ignored) {}
                try { cureAll = Boolean.parseBoolean(String.valueOf(def.data.getOrDefault("cure_all", false))); } catch (Exception ignored) {}
            }

            if (s.playerMon.currentHp <= 0) {
                sendBattleLine(s, plugin.getLang().uiFmt("battle.item.fainted_no_heal", "§7{mon} 已经倒下，无法使用该回复道具（请使用复活类道具）。", java.util.Map.of("mon", monName)));
                return;
            }

            int max = (monS == null) ? Math.max(1, s.playerMon.currentHp) : s.playerMon.maxHp(monS);
            if (!healFull && heal <= 0 && !cureAll) {
                sendBattleLine(s, plugin.getLang().uiFmt("battle.item.bad_param", "§7该道具参数异常，无法在战斗中使用：{id}", java.util.Map.of("id", String.valueOf(def.id))));
                return;
            }

            int before = s.playerMon.currentHp;
            if (healFull) s.playerMon.currentHp = max;
            else if (heal > 0) s.playerMon.currentHp = Math.min(max, s.playerMon.currentHp + heal);

            int healed = s.playerMon.currentHp - before;
            if (cureAll) s.playerMon.status = "none";

            if (healed <= 0 && !cureAll) {
                sendBattleLine(s, plugin.getLang().uiFmt("battle.item.hp_full", "§7{mon} 已经是满血了。", java.util.Map.of("mon", monName)));
            } else {
                String extra = cureAll ? plugin.getLang().ui("battle.item.heal.extra_cure", "，并治愈了异常状态") : "";
                String msg = plugin.getLang().uiFmt(
                        "battle.log.use_item.heal",
                        "§a使用了 {item}：§f{mon} §a回复 {heal}",
                        java.util.Map.of("item", itemName, "mon", monName, "heal", String.valueOf(Math.max(0, healed)))
                );
                msg = msg + " HP（" + s.playerMon.currentHp + "/" + max + ")" + extra + "。";
                sendBattleLine(s, msg);
            }
            return;
        }

        // STATUS_CURE: cure a specific status
        if (def.type == ItemType.STATUS_CURE) {
            String cure = "";
            if (def.data != null) cure = String.valueOf(def.data.getOrDefault("cure", ""));
            if (cure == null) cure = "";
            if (cure.isBlank()) {
                sendBattleLine(s, plugin.getLang().uiFmt("battle.item.bad_param_status", "§7该状态药参数异常，无法使用：{id}", java.util.Map.of("id", def.id)));
                return;
            }
            if (s.playerMon.currentHp <= 0) {
                sendBattleLine(s, plugin.getLang().uiFmt("battle.item.fainted_no_status", "§7{mon} 已经倒下，无法使用状态药。", java.util.Map.of("mon", monName)));
                return;
            }
            String st = (s.playerMon.status == null) ? "none" : s.playerMon.status;
            boolean needs;
            if (cure.equalsIgnoreCase("all") || cure.equalsIgnoreCase("any")) {
                needs = !st.equalsIgnoreCase("none");
            } else if (cure.equalsIgnoreCase("paralysis")) {
                needs = st.equalsIgnoreCase("paralyze") || st.equalsIgnoreCase("paralysis");
            } else if (cure.equalsIgnoreCase("freeze")) {
                needs = st.equalsIgnoreCase("freeze") || st.equalsIgnoreCase("frozen");
            } else {
                needs = st.equalsIgnoreCase(cure);
            }

            if (!needs) {
                sendBattleLine(s, plugin.getLang().uiFmt("battle.item.no_status_to_cure", "§7{mon} 当前没有需要治愈的异常状态。", java.util.Map.of("mon", monName)));
                return;
            }
            s.playerMon.status = "none";
            sendBattleLine(s, plugin.getLang().uiFmt("battle.log.use_item.cure_status", "§a使用了 {item}：§f{mon} §a异常状态已治愈。", java.util.Map.of("item", itemName, "mon", monName)));
            return;
        }

        // REVIVE: revive fainted mon (active slot)
        if (def.type == ItemType.REVIVE) {
            double ratio = 0.5;
            if (def.data != null) {
                try { ratio = Double.parseDouble(String.valueOf(def.data.getOrDefault("revive", 0.5))); } catch (Exception ignored) {}
            }
            if (s.playerMon.currentHp > 0) {
                sendBattleLine(s, plugin.getLang().uiFmt("battle.item.not_fainted_revive", "§7{mon} 还没倒下，无法使用复活类道具。", java.util.Map.of("mon", monName)));
                return;
            }

            int max = (monS == null) ? 1 : Math.max(1, s.playerMon.maxHp(monS));
            int hp = Math.max(1, (int) Math.floor(max * Math.max(0.0, Math.min(1.0, ratio))));
            s.playerMon.currentHp = hp;
            s.playerMon.status = "none";
            sendBattleLine(s, plugin.getLang().uiFmt("battle.log.use_item.revive", "§a使用了 {item}：§f{mon} §a复活（{hp}/{max}）。", java.util.Map.of("item", itemName, "mon", monName, "hp", String.valueOf(hp), "max", String.valueOf(max))));
            return;
        }

        sendBattleLine(s, plugin.getLang().uiFmt("battle.item.not_supported_in_battle", "§7该道具暂未支持战斗中使用：{id}", java.util.Map.of("id", def.id)));
    }

/** Perform capture attempt with a chosen ball id (ball already consumed). */
    private void throwBallSelected(Player player, BattleSession s, String ballId) {
        if (player == null || s == null || s.finished) return;
        if (s.processingTurn) {
            player.sendMessage(plugin.getLang().ui("battle.wait_turn", "§e请稍等，本回合正在结算..."));
            return;
        }

        String ballName = plugin.getLang().tr(ballNameKey(ballId), ballId);
        if (ballName == null || ballName.isEmpty()) ballName = ballId;
        sendBattleLine(s, plugin.getLang().uiFmt("battle.log.throw_ball", "§e你投出了 §f{ball}§e!", java.util.Map.of("ball", ballName)));

        // Show suspense first, then reveal success/fail.
        s.processingTurn = true;
        int waitTicks = 20 * (1 + Util.RND.nextInt(3)); // 1-3 seconds
        s.statusLine = plugin.getLang().ui("battle.status.catching", "§e请稍等...§7（捕捉中）");
        try {
            renderOpenVanillaBattleInventory(player, s);
        } catch (Throwable ignored) {}

        boolean master = isMasterBall(ballId);
        double ballBonus = ballBonus(ballId);

        final boolean masterFinal = master;
        final double ballBonusFinal = ballBonus;
        new BukkitRunnable() {
            @Override public void run() {
                if (s.finished || !sessions.containsKey(player.getUniqueId())) return;

                boolean success;
                int shakes = 0;

                if (masterFinal) {
                    success = true;
                    shakes = 4;
                } else {
                    Species sp = dex.getSpecies(s.wildMon.effectiveSpeciesId());
                    int maxHp = (sp != null) ? s.wildMon.maxHp(sp) : Math.max(1, s.wildMon.currentHp);
                    int curHp = Math.max(1, s.wildMon.currentHp);

                    int catchRate = (sp != null) ? sp.catchRate() : 45;
                    double statusBonus = statusCaptureBonus(s.wildMon.status);

                    // Gen1-style HP factor: (3M - 2H) / (3M)
                    double numerator = (3.0 * maxHp - 2.0 * curHp);
                    if (numerator < 1) numerator = 1;
                    double aD = (numerator * catchRate * ballBonusFinal * statusBonus) / (3.0 * maxHp);
                    int a = (int) Math.floor(aD);
                    if (a < 1) a = 1;
                    if (a > 255) a = 255;

                    if (a >= 255) {
                        success = true;
                        shakes = 4;
                    } else {
                        double b = 1048560.0 / Math.sqrt(Math.sqrt(16711680.0 / a));
                        for (int i = 0; i < 4; i++) {
                            if (Math.random() * 65536.0 < b) {
                                shakes++;
                            } else {
                                break;
                            }
                        }
                        success = (shakes >= 4);
                    }
                }

                if (success) {
                    sendBattleLine(s, plugin.getLang().ui("battle.log.capture_success", "§a捕捉成功！§7你抓住了对方的精灵。"));
                } else {
                    if (shakes <= 0) sendBattleLine(s, plugin.getLang().ui("battle.log.capture_fail", "§c捕捉失败！§7精灵挣脱了出来。"));
                    else sendBattleLine(s, plugin.getLang().uiFmt("battle.log.capture_fail_shakes", "§c捕捉失败！§7精灵球摇晃了 {n} 次后挣脱了出来。", java.util.Map.of("n", String.valueOf(shakes))));
                }

                try {
                    renderOpenVanillaBattleInventory(player, s);
                } catch (Throwable ignored) {}

                if (success) {
            // add to party or pc (persisted copy to avoid leaking battle-only transient state)
            try {
                PlayerProfile prof = storage.getProfile(player.getUniqueId());
                PokemonInstance caught = s.wildMon.deepCopyPersisted();
                caught.ballId = (ballId == null || ballId.isBlank()) ? "poke_ball" : ballId;
                caught.originalTrainer = player.getUniqueId();
                caught.originalTrainerName = player.getName();

                prof.depositToPartyOrPc(caught);
                try {
                    if (prof.dexCaught != null && caught.speciesId != null) {
                        prof.dexCaught.add(caught.speciesId.toLowerCase(java.util.Locale.ROOT));
                    }
                } catch (Exception ignored) {}
                if (prof.party.contains(caught)) sendBattleLine(s, plugin.getLang().ui("battle.log.caught_party", "§7已加入你的队伍。"));
                else sendBattleLine(s, plugin.getLang().ui("battle.log.caught_pc", "§7队伍已满，已存入电脑盒子。"));
                storage.saveProfile(player.getUniqueId());
            } catch (Exception ex) {
                player.sendMessage(plugin.getLang().uiFmt("battle.capture.save_fail", "§c捕捉写入存档失败: {msg}", java.util.Map.of("msg", String.valueOf(ex.getMessage()))));
            }

            // remove wild entity
            try {
                org.bukkit.entity.Entity ent = org.bukkit.Bukkit.getEntity(s.wildEntityId);
                if (ent instanceof org.bukkit.entity.Wolf w) w.remove();
                else if (ent != null) ent.remove();
            } catch (Exception ignored) {}

            endBattle(player.getUniqueId(), null);
            try { player.closeInventory(); } catch (Exception ignored) {}
                    return;
                }

                // Failure: wild acts after a short moment
                s.statusLine = plugin.getLang().ui("battle.status.opponent_acting", "§e请稍等...§7（对方行动中）");
                new BukkitRunnable() {
                    @Override public void run() {
                        if (s.finished || !sessions.containsKey(player.getUniqueId())) return;

                        // Wild chooses random move
                        int wildChoice = Util.RND.nextInt(4);
                        MoveSlot wildSlot = (s.wildMon.effectiveMoves() != null && wildChoice < s.wildMon.effectiveMoves().size())
                                ? s.wildMon.effectiveMoves().get(wildChoice) : null;
                        Move wildMove = (wildSlot == null) ? null : dex.getMoveOrPlaceholder(wildSlot.moveId);
                        if (wildSlot == null || wildMove == null) {
                            wildSlot = MoveSlot.of(dex.getMoveOrPlaceholder("tackle"));
                            s.wildMon.ensureBattleMovesFromBase();
                            while (s.wildMon.effectiveMoves().size() < 4) s.wildMon.effectiveMoves().add(MoveSlot.of(dex.getMoveOrPlaceholder("tackle")));
                            s.wildMon.effectiveMoves().set(wildChoice, wildSlot);
                            wildMove = dex.getMoveOrPlaceholder(wildSlot.moveId);
                        }
                        if (wildSlot.pp > 0) wildSlot.pp--;
        if (wildSlot.pp == 0 && "mystery_berry".equalsIgnoreCase(s.wildMon.heldItemId)) {
            int restore = Math.min(5, Math.max(0, wildSlot.maxPp - wildSlot.pp));
            if (restore > 0) {
                wildSlot.pp += restore;
                s.wildMon.heldItemId = null;
            }
        }

                        List<String> lines = applyMoveDescribed(player, s, false, wildMove);
                        sendBattleLines(s, lines);

                        if (s.wildMon.currentHp <= 0) {
                            win(player, s);
                            try { player.closeInventory(); } catch (Exception ignored) {}
                            return;
                        }
                        if (s.playerMon.currentHp <= 0) {
                            lose(player, s);
                            if (s.finished) {
                                try { player.closeInventory(); } catch (Exception ignored) {}
                                return;
                            }
                        }

                        // End turn
                        s.processingTurn = false;
                        s.statusLine = plugin.getLang().ui("battle.status.choose_move", "§7请选择一个招式。");
                        try {
                            renderOpenVanillaBattleInventory(player, s);
                        } catch (Throwable ignored) {}
                    }
                }.runTaskLater(plugin, 20L);
            }
        }.runTaskLater(plugin, waitTicks);
    }
    private void sendBattleLines(BattleSession s, List<String> lines) {
        if (s == null || lines == null || lines.isEmpty()) return;
        for (String ln : lines) {
            if (ln == null) continue;
            sendBattleLine(s, ln);
        }
    }

    public void startWildBattle(Player player, Wolf wolf) {
        startWildBattle(player, wolf, null);
    }

    public void startWildBattle(Player player, Wolf wolf, Wolf initiatingCarrier) {
        if (player == null || wolf == null) return;

        // Repel: prevent starting wild battles while active
        try {
            org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey(plugin, "repel_until");
            Long until = player.getPersistentDataContainer().get(key, org.bukkit.persistence.PersistentDataType.LONG);
            if (until != null && until > System.currentTimeMillis()) {
                player.sendMessage(plugin.getLang().ui("battle.repel_active", "§7驱虫喷雾效果中，无法遭遇野生精灵。"));
                return;
            }
        } catch (Exception ignored) {}
        // If player was spectating someone else, stop spectating when starting a new battle.
        removeSpectator(player.getUniqueId());
        // Locking: one player in one battle; one wild entity in one battle.
        if (isInBattle(player.getUniqueId())) {
            player.sendMessage(plugin.getLang().ui("battle.already_in_short", "§e你已经在战斗中，结束后才能开始新的战斗。"));
            return;
        }
        UUID lockOwner = wildLocks.get(wolf.getUniqueId());
        if (lockOwner != null && !lockOwner.equals(player.getUniqueId())) {
            // Chat-only spectate: just mirror battle log to this player.
            BattleSession watching = sessions.get(lockOwner);
            if (watching != null && !watching.finished) {
                watching.spectators.add(player.getUniqueId());
                Player owner = Bukkit.getPlayer(lockOwner);
                String ownerName = (owner != null ? owner.getName() : plugin.getLang().ui("common.player","玩家"));
                player.sendMessage(plugin.getLang().uiFmt("spectate.auto_enter", "§7该野生精灵正在与 §f{owner} §7战斗中，你已进入§a观战§7（仅聊天）。", java.util.Map.of("owner", ownerName)));
                player.sendMessage(plugin.getLang().ui("spectate.hint_exit", "§7输入 §f/pokedemo spectateoff §7可退出观战。"));
                if (watching.statusLine != null && !watching.statusLine.isEmpty()) {
                    player.sendMessage(watching.statusLine);
                }
            } else {
                player.sendMessage(plugin.getLang().ui("battle.wild_locked", "§e该野生精灵已经在战斗中了！"));
            }
            return;
        }
        String speciesId = wolf.getPersistentDataContainer().get(plugin.KEY_SPECIES, PersistentDataType.STRING);
        Integer level = wolf.getPersistentDataContainer().get(plugin.KEY_LEVEL, PersistentDataType.INTEGER);
        Species s = dex.getSpecies(speciesId);
        if (s == null || level == null) {
            player.sendMessage(plugin.getLang().ui("battle.invalid_wild","§c无效的野生精灵。"));
            return;
        }

        PlayerProfile prof = storage.getProfile(player.getUniqueId());
        if (prof.party.isEmpty()) {
            player.sendMessage(plugin.getLang().ui("battle.no_party","§c你的队伍里没有精灵。管理员可用 /pokedemo give <精灵ID> 来给你一只（演示用）。"));
            return;
        }

        // Prefer the exact Pokémon whose carrier initiated this contact battle.
        PokemonInstance playerMon = null;
        Species playerSpecies = null;
        if (initiatingCarrier != null && initiatingCarrier.isValid() && !initiatingCarrier.isDead()) {
            try {
                String puid = initiatingCarrier.getPersistentDataContainer().get(plugin.KEY_PUUID, PersistentDataType.STRING);
                if (puid != null && !puid.isBlank()) {
                    java.util.UUID want = java.util.UUID.fromString(puid);
                    for (PokemonInstance p : prof.party) {
                        if (p == null || p.isEgg || p.currentHp <= 0) continue;
                        if (!want.equals(p.uuid)) continue;
                        Species ps = dex.getSpecies(p.speciesId);
                        if (ps == null) continue;
                        playerMon = p;
                        playerSpecies = ps;
                        break;
                    }
                }
            } catch (Throwable ignored) {}
        }
        // Fallback: choose the first non-fainted, non-egg Pokémon in party.
        if (playerMon == null || playerSpecies == null) {
            for (PokemonInstance p : prof.party) {
                Species ps = dex.getSpecies(p.speciesId);
                if (ps == null) continue;
                if (p.isEgg) continue;
                if (p.currentHp > 0) {
                    playerMon = p;
                    playerSpecies = ps;
                    break;
                }
            }
        }
        if (playerMon == null || playerSpecies == null) {
            player.sendMessage(plugin.getLang().ui("battle.no_usable_party","§c你的队伍中没有可出战的精灵（蛋不能出战，且必须至少有一只未昏厥的精灵）。"));
            return;
        }

        PokemonInstance wildMon = PokemonInstance.createWild(s, level, dex);

        // Legendary: apply minimum perfect IVs and enable global capture announcement.
        try {
            Byte leg = wolf.getPersistentDataContainer().get(plugin.KEY_LEGENDARY, PersistentDataType.BYTE);
            if (leg != null && leg == (byte)1) {
                wildMon.isLegendary = true;
                String grp = wolf.getPersistentDataContainer().get(plugin.KEY_LEGENDARY_GROUP, PersistentDataType.STRING);
                if (grp != null && !grp.isBlank()) wildMon.legendaryGroup = grp;
                Integer mp = wolf.getPersistentDataContainer().get(plugin.KEY_MIN_PERFECT_IVS, PersistentDataType.INTEGER);
                int minPerfect = (mp != null) ? mp : plugin.getConfig().getInt("legendary.defaults.min-perfect-ivs", 3);
                wildMon.applyMinPerfectIvs(minPerfect);
            }
        } catch (Throwable ignored) {}

        // Reset battle-only volatile state
        playerMon.resetBattleStages();
        wildMon.resetBattleStages();
        playerMon.resetBattleVolatiles();
        wildMon.resetBattleVolatiles();
        playerMon.justSwitchedIn = true;
        playerMon.typeChangeAbilityUsed = false;
        wildMon.justSwitchedIn = true;
        wildMon.typeChangeAbilityUsed = false;

        BattleSession session = new BattleSession(player.getUniqueId(), wolf.getUniqueId(), playerMon, wildMon);
        registerSession(session);

        // Spawn/freeze in-world battle visuals (both sides)
        ensureBattleVisuals(player, session, wolf, playerMon, playerSpecies, initiatingCarrier);

        openBattleGui(player, session);

        // Entry held item triggers (e.g., Berserk Gene)
        {
            java.util.List<String> enter = new java.util.ArrayList<>();
            enter.addAll(HeldItemEffects.onEntry(playerMon, playerSpecies, plugin.getLang() == null ? playerSpecies.name() : plugin.getLang().species(playerSpecies.id(), playerSpecies.name())));
            enter.addAll(HeldItemEffects.onEntry(wildMon, s, plugin.getLang() == null ? s.name() : plugin.getLang().species(s.id(), s.name())));
            if (!enter.isEmpty()) sendBattleLines(session, enter);

        // Entry ability triggers
        try {
            LangManager lang = plugin.getLang();
            Species pS = playerSpecies;
            Species wS = s;
            String pName = (lang == null || pS == null) ? (pS == null ? "?" : pS.name()) : lang.species(pS.id(), pS.name());
            String wName = (lang == null || wS == null) ? (wS == null ? "?" : wS.name()) : lang.species(wS.id(), wS.name());
            sendBattleLines(session, AbilityEffects.onSwitchIn(plugin, player, session, playerMon, pS, wildMon, wS, pName, wName, true));
            sendBattleLines(session, AbilityEffects.onSwitchIn(plugin, player, session, wildMon, wS, playerMon, pS, wName, pName, false));
        } catch (Throwable ignored) {}

        }

        player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.4f, 1.6f);
    }

    /**
     * Start / open a PvP ready screen between two players.
     * Both players must confirm to begin the battle.
     */
    public void startPvpReady(Player aPlayer, Player bPlayer) {
        if (aPlayer == null || bPlayer == null) return;
        UUID a = aPlayer.getUniqueId();
        UUID b = bPlayer.getUniqueId();

        if (a.equals(b)) return;
	        if (isInBattle(a) || isInBattle(b)) {
	            aPlayer.sendMessage(plugin.getLang().ui("pvp.cannot_start_someone_battling","§e有一方正在战斗中，无法开始PVP。"));
	            return;
	        }

        // One active pending/battle per player.
        String ka = pvpKeyByPlayer.get(a);
        String kb = pvpKeyByPlayer.get(b);
	        if (ka != null && !ka.equals(pvpKey(a, b))) {
	            aPlayer.sendMessage(plugin.getLang().ui("pvp.already_preparing_self","§e你正在准备/进行另一场PVP。"));
	            return;
	        }
	        if (kb != null && !kb.equals(pvpKey(a, b))) {
	            aPlayer.sendMessage(plugin.getLang().ui("pvp.already_preparing_other","§e对方正在准备/进行另一场PVP。"));
	            return;
	        }

        String key = pvpKey(a, b);
        if (key == null) return;
        PvpPending pending = pvpPending.computeIfAbsent(key, k -> new PvpPending(a, b));
        pvpKeyByPlayer.put(a, key);
        pvpKeyByPlayer.put(b, key);

        // Open ready GUI for both.
        UtilGui.openPvpReady(aPlayer, storage, b, bPlayer.getName(), pending.selectedA, pending.confirmedA);
        UtilGui.openPvpReady(bPlayer, storage, a, aPlayer.getName(), pending.selectedB, pending.confirmedB);

	        aPlayer.sendMessage(plugin.getLang().ui("pvp.prep_enter","§6[PVP]§7 已进入准备界面，请调整队伍顺序并点击确认。"));
	        bPlayer.sendMessage(plugin.getLang().ui("pvp.prep_enter","§6[PVP]§7 已进入准备界面，请调整队伍顺序并点击确认。"));
    }

    /** Called when a PVP_READY GUI is closed: cancel the pending. */
    public void onPvpReadyClosed(UUID playerId) {
        if (playerId == null) return;
        String key = pvpKeyByPlayer.get(playerId);
        if (key == null) return;
        // If already in battle, ignore.
        if (pvpBattles.containsKey(key)) return;
        PvpPending pending = pvpPending.remove(key);
        if (pending != null) {
            pvpKeyByPlayer.remove(pending.a);
            pvpKeyByPlayer.remove(pending.b);
            Player pa = Bukkit.getPlayer(pending.a);
            Player pb = Bukkit.getPlayer(pending.b);
	            if (pa != null) pa.sendMessage(plugin.getLang().ui("pvp.prep_cancelled","§6[PVP]§7 准备已取消。"));
	            if (pb != null) pb.sendMessage(plugin.getLang().ui("pvp.prep_cancelled","§6[PVP]§7 准备已取消。"));
        }
    }

    /** Handle clicks inside the PVP ready GUI. */
    public void handlePvpReadyClick(Player player, GuiHolder holder, int rawSlot) {
        if (player == null || holder == null) return;
        UUID pid = player.getUniqueId();
        if (holder.pvpOpponentId == null) { player.closeInventory(); return; }
        String key = pvpKey(pid, holder.pvpOpponentId);
        if (key == null) { player.closeInventory(); return; }
        PvpPending pending = pvpPending.get(key);
        if (pending == null) {
	            player.sendMessage(plugin.getLang().ui("pvp.prep_expired","§e该PVP准备已失效。"));
            player.closeInventory();
            return;
        }
        boolean isA = pid.equals(pending.a);

        // Cancel
        if (rawSlot == 17) {
            player.closeInventory();
            onPvpReadyClosed(pid);
            return;
        }

        // Confirm
        if (rawSlot == 26) {
            if (isA) pending.confirmedA = true; else pending.confirmedB = true;
            // Re-open GUIs to update confirm status
            Player pa = Bukkit.getPlayer(pending.a);
            Player pb = Bukkit.getPlayer(pending.b);
            if (pa != null) UtilGui.openPvpReady(pa, storage, pending.b, (pb != null ? pb.getName() : "?"), pending.selectedA, pending.confirmedA);
            if (pb != null) UtilGui.openPvpReady(pb, storage, pending.a, (pa != null ? pa.getName() : "?"), pending.selectedB, pending.confirmedB);
            if (pending.confirmedA && pending.confirmedB) {
                // Both confirmed -> start battle
                try { beginPvpBattle(pending); } catch (Throwable t) {
                    if (pa != null) pa.sendMessage("§cPVP开始失败：" + t.getMessage());
                    if (pb != null) pb.sendMessage("§cPVP开始失败：" + t.getMessage());
                    pvpPending.remove(key);
                    pvpKeyByPlayer.remove(pending.a);
                    pvpKeyByPlayer.remove(pending.b);
                }
            }
            return;
        }

        // Party reorder (slots 0-5)
        if (rawSlot >= 0 && rawSlot < 6) {
            PlayerProfile prof = storage.getProfile(pid);
            if (prof == null) return;
            if (rawSlot >= prof.party.size()) return;
            PokemonInstance clicked = prof.party.get(rawSlot);
            if (clicked != null && (clicked.isEgg || clicked.currentHp <= 0)) {
                // Allow reordering even if egg/fainted, but warn.
            }

            int sel = isA ? pending.selectedA : pending.selectedB;
            if (sel < 0) {
                if (isA) pending.selectedA = rawSlot; else pending.selectedB = rawSlot;
            } else if (sel == rawSlot) {
                if (isA) pending.selectedA = -1; else pending.selectedB = -1;
            } else {
                // swap
                try {
                    java.util.Collections.swap(prof.party, sel, rawSlot);
                    storage.markDirty(pid);
                } catch (Throwable ignored) {}
                if (isA) pending.selectedA = -1; else pending.selectedB = -1;
            }
            Player other = Bukkit.getPlayer(holder.pvpOpponentId);
            UtilGui.openPvpReady(player, storage, holder.pvpOpponentId, (other != null ? other.getName() : "?"), isA ? pending.selectedA : pending.selectedB, isA ? pending.confirmedA : pending.confirmedB);
            return;
        }
    }

    private void beginPvpBattle(PvpPending pending) {
        if (pending == null) return;
        String key = pvpKey(pending.a, pending.b);
        if (key == null) return;
        Player pa = Bukkit.getPlayer(pending.a);
        Player pb = Bukkit.getPlayer(pending.b);
        if (pa == null || pb == null) {
	            if (pa != null) pa.sendMessage(plugin.getLang().ui("pvp.other_offline","§e对方不在线，无法开始PVP。"));
	            if (pb != null) pb.sendMessage(plugin.getLang().ui("pvp.other_offline","§e对方不在线，无法开始PVP。"));
            pvpPending.remove(key);
            pvpKeyByPlayer.remove(pending.a);
            pvpKeyByPlayer.remove(pending.b);
            return;
        }

        // Choose first non-fainted, NON-EGG Pokémon
        PokemonInstance monA = firstUsablePartyMon(pending.a);
        PokemonInstance monB = firstUsablePartyMon(pending.b);
        Species spA = monA == null ? null : dex.getSpecies(monA.effectiveSpeciesId());
        Species spB = monB == null ? null : dex.getSpecies(monB.effectiveSpeciesId());
        if (monA == null || monB == null || spA == null || spB == null) {
	            pa.sendMessage(plugin.getLang().ui("pvp.need_usable_party","§c无法开始PVP：双方都必须至少有一只未昏厥且非蛋的精灵。"));
	            pb.sendMessage(plugin.getLang().ui("pvp.need_usable_party","§c无法开始PVP：双方都必须至少有一只未昏厥且非蛋的精灵。"));
            pvpPending.remove(key);
            pvpKeyByPlayer.remove(pending.a);
            pvpKeyByPlayer.remove(pending.b);
            return;
        }

        // Reset battle-only state
        monA.resetBattleStages(); monA.resetBattleVolatiles();
        monB.resetBattleStages(); monB.resetBattleVolatiles();

        BattleSession sa = new BattleSession(pending.a, null, monA, monB);
        sa.pvp = true;
        sa.pvpOpponentId = pending.b;
        sa.pvpKey = key;
        BattleSession sb = new BattleSession(pending.b, null, monB, monA);
        sb.pvp = true;
        sb.pvpOpponentId = pending.a;
        sb.pvpKey = key;

        registerSession(sa);
        registerSession(sb);

        PvpBattle pbattle = new PvpBattle(key, pending.a, pending.b, sa, sb);
        pvpBattles.put(key, pbattle);

        // Spawn shared visuals
        try { ensurePvpVisuals(pa, pb, pbattle, spA, spB); } catch (Throwable ignored) {}

        // Open battle GUI for both
        openBattleGui(pa, sa);
        openBattleGui(pb, sb);

        pvpPending.remove(key);
	        pa.sendMessage(plugin.getLang().ui("pvp.started","§6[PVP]§a 战斗开始！"));
	        pb.sendMessage(plugin.getLang().ui("pvp.started","§6[PVP]§a 战斗开始！"));
    }

    private PokemonInstance firstUsablePartyMon(UUID playerId) {
        PlayerProfile prof = storage.getProfile(playerId);
        if (prof == null || prof.party == null) return null;
        for (PokemonInstance p : prof.party) {
            if (p == null) continue;
            if (p.isEgg) continue;
            if (p.currentHp > 0) return p;
        }
        return null;
    }

    private boolean hasAnyUsablePartyMon(UUID playerId) {
        PlayerProfile prof = storage.getProfile(playerId);
        if (prof == null || prof.party == null) return false;
        for (PokemonInstance p : prof.party) {
            if (p == null) continue;
            if (p.isEgg) continue;
            if (p.currentHp > 0) return true;
        }
        return false;
    }

    /** Spawn two battle carriers at the midpoint between two players for PvP. */
    private void ensurePvpVisuals(Player pa, Player pb, PvpBattle battle, Species spA, Species spB) {
        if (pa == null || pb == null || battle == null) return;
        if (battle.sa.visualsSpawned || battle.sb.visualsSpawned) return;
        Location la = pa.getLocation();
        Location lb = pb.getLocation();
        Location mid = la.clone().add(lb).multiply(0.5);
        Vector dir = lb.toVector().subtract(la.toVector());
        dir.setY(0);
        if (dir.lengthSquared() < 0.0001) dir = new Vector(1, 0, 0);
        dir.normalize();
        Location aLoc = mid.clone().add(dir.clone().multiply(-1.4)).add(0, 0.2, 0);
        Location bLoc = mid.clone().add(dir.clone().multiply(1.4)).add(0, 0.2, 0);
        aLoc.setYaw(mid.clone().setDirection(dir).getYaw());
        bLoc.setYaw(mid.clone().setDirection(dir.clone().multiply(-1)).getYaw());

        Wolf aCarrier = plugin.getSummonManager().spawnBattleCarrier(pa, battle.sa.playerMon, spA, aLoc);
        Wolf bCarrier = plugin.getSummonManager().spawnBattleCarrier(pb, battle.sb.playerMon, spB, bLoc);
        if (aCarrier != null) {
            freezeCarrier(aCarrier);
            battle.sa.playerBattleCarrierId = aCarrier.getUniqueId();
            battle.sb.wildBattleCarrierId = aCarrier.getUniqueId();
        }
        if (bCarrier != null) {
            freezeCarrier(bCarrier);
            battle.sa.wildBattleCarrierId = bCarrier.getUniqueId();
            battle.sb.playerBattleCarrierId = bCarrier.getUniqueId();
        }
        battle.sa.visualsSpawned = true;
        battle.sb.visualsSpawned = true;
    }

    private void openBattleGui(Player player, BattleSession session) {
        if (player == null || session == null) return;
        session.guiOpen = true;
        try {
            if (plugin.getBridgeSyncManager() != null && plugin.getBridgeSyncManager().isBridgeClient(player.getUniqueId())) {
                plugin.getBridgeSyncManager().syncBattleState(player);
                return;
            }
        } catch (Throwable ignored) {}
        GuiHolder holder = new GuiHolder(GuiType.BATTLE, player.getUniqueId());
        holder.battleSession = session;
        LangManager lang = plugin.getLang();
        Inventory inv = Bukkit.createInventory(holder, 27, UtilGui.title(lang.ui("gui.battle.title", "§4战斗")));

        renderBattle(inv, player, session);
        player.openInventory(inv);
    }

    private Species resolveBattleSpecies(String rawId) {
        if (dex == null || rawId == null || rawId.isBlank()) return null;
        Species s = dex.getSpecies(rawId);
        if (s != null) return s;
        return dex.getSpeciesFlexible(rawId);
    }

    private int safeBattleMaxHp(PokemonInstance mon, Species species) {
        if (mon == null) return 1;
        if (species != null) return Math.max(1, mon.maxHp(species));
        int cur = Math.max(0, mon.currentHp);
        Integer locked = mon.lockedMaxHp;
        if (locked != null && locked > 0) return Math.max(cur, locked);
        return Math.max(cur, 1);
    }

    private String battleDisplayName(PokemonInstance mon, Species species, LangManager lang) {
        if (species != null) return (lang == null ? species.name() : lang.species(species.id(), species.name()));
        if (mon == null) return "?";
        String raw = mon.displayName();
        if (raw != null && !raw.isBlank()) return raw;
        return mon.effectiveSpeciesId() == null ? "?" : mon.effectiveSpeciesId();
    }

    private void renderBattle(Inventory inv, Player player, BattleSession s) {
        inv.clear();
        Species wildS = resolveBattleSpecies(s.wildMon == null ? null : s.wildMon.effectiveSpeciesId());
        Species myS = resolveBattleSpecies(s.playerMon == null ? null : s.playerMon.effectiveSpeciesId());
        LangManager lang = plugin.getLang();
        String myName = battleDisplayName(s.playerMon, myS, lang);
        String wildName = battleDisplayName(s.wildMon, wildS, lang);

        inv.setItem(0, UtilGui.button(Material.WOLF_SPAWN_EGG, lang.uiFmt("battle.gui.ally_title", "§a我方：{name} Lv.{level}", java.util.Map.of("name", myName, "level", String.valueOf(s.playerMon.level))),
                List.of(
                        lang.uiFmt("battle.gui.hp", "§7生命值: {cur}/{max}", java.util.Map.of("cur", String.valueOf(s.playerMon.currentHp), "max", String.valueOf(safeBattleMaxHp(s.playerMon, myS)))),
                        lang.uiFmt("battle.gui.status", "§7状态: {status}", java.util.Map.of("status", String.valueOf(s.playerMon.status)))
                )));
        if (s.pvp && s.pvpOpponentId != null) {
            Player opp = Bukkit.getPlayer(s.pvpOpponentId);
            String on = (opp != null) ? opp.getName() : "对方";
            inv.setItem(8, UtilGui.button(Material.BONE, lang.uiFmt("battle.gui.enemy_title_pvp", "§c对方：{player} 的 {name} Lv.{level}", java.util.Map.of("player", on, "name", wildName, "level", String.valueOf(s.wildMon.level))),
                    List.of(lang.uiFmt("battle.gui.hp", "§7生命值: {cur}/{max}", java.util.Map.of("cur", String.valueOf(s.wildMon.currentHp), "max", String.valueOf(safeBattleMaxHp(s.wildMon, wildS)))),
                            lang.uiFmt("battle.gui.status", "§7状态: {status}", java.util.Map.of("status", String.valueOf(s.wildMon.status))))));
        } else {
            inv.setItem(8, UtilGui.button(Material.BONE, lang.uiFmt("battle.gui.enemy_title_wild", "§c野生：{name} Lv.{level}", java.util.Map.of("name", wildName, "level", String.valueOf(s.wildMon.level))),
                    List.of(lang.uiFmt("battle.gui.hp", "§7生命值: {cur}/{max}", java.util.Map.of("cur", String.valueOf(s.wildMon.currentHp), "max", String.valueOf(safeBattleMaxHp(s.wildMon, wildS)))),
                            lang.uiFmt("battle.gui.status", "§7状态: {status}", java.util.Map.of("status", String.valueOf(s.wildMon.status))))));
        }

        // Center status / turn hint
        String hint = (s.processingTurn ? lang.ui("battle.gui.hint_wait", "§e请稍等...") : lang.ui("battle.gui.hint_choose", "§7请选择一个招式"))
                + " " + lang.uiFmt("battle.gui.turn_suffix", "§8(回合 {turn})", java.util.Map.of("turn", String.valueOf(s.turn + 1)));
        String line = (s.statusLine == null || s.statusLine.isBlank()) ? hint : s.statusLine;
        inv.setItem(13, UtilGui.button(Material.CLOCK, lang.ui("battle.gui.turn_title", "§6回合结算"), List.of(line, lang.ui("battle.gui.turn_locked", "§8结算期间无法再次操作"))));

        // Moves (slots 18-21)
        for (int i = 0; i < 4; i++) {
            MoveSlot ms = (s.playerMon.effectiveMoves() != null && i < s.playerMon.effectiveMoves().size()) ? s.playerMon.effectiveMoves().get(i) : null;
            // Resolve move safely. Even if the move isn't imported yet, use a placeholder to avoid NPE.
            Move m = (ms == null) ? null : dex.getMoveOrPlaceholder(ms.moveId);
            inv.setItem(18 + i, moveButtonSafe(m, ms));
        }

        inv.setItem(22, UtilGui.button(Material.CHEST, lang.ui("battle.gui.bag.title", "§b道具"), java.util.List.of(lang.ui("battle.gui.bag.lore", "§7使用战斗道具（X能力/要害攻击/防守指令等）"))));

        if (s.pvp) {
            inv.setItem(23, UtilGui.button(Material.GRAY_DYE, lang.ui("battle.gui.catch.disabled_title", "§c捕捉(禁用)"), List.of(lang.ui("battle.gui.catch.disabled_lore_pvp", "§cPVP战斗中严格禁止捕捉"))));
            inv.setItem(24, UtilGui.button(Material.GRAY_DYE, lang.ui("battle.gui.run.disabled_title", "§c逃跑(禁用)"), List.of(lang.ui("battle.gui.run.disabled_lore_pvp", "§7PVP战斗中禁止逃跑"))));
            inv.setItem(25, UtilGui.button(Material.GRAY_DYE, lang.ui("battle.gui.switch.disabled_title", "§c换精灵(禁用)"), List.of(lang.ui("battle.gui.switch.disabled_lore_pvp", "§7仅在昏厥时强制切换"))));
        } else {
            inv.setItem(23, UtilGui.button(Material.SNOWBALL, lang.ui("battle.gui.catch.title", "§a捕捉"), List.of(
                        lang.ui("battle.gui.catch.l1", "§7消耗背包中的精灵球"),
                        lang.ui("battle.gui.catch.l2", "§7优先级: §f大师球>高级球>超级球>精灵球")
                )));
            inv.setItem(24, UtilGui.button(Material.ARROW, lang.ui("battle.gui.run.title", "§e逃跑"), List.of(lang.ui("battle.gui.run.l1", "§7尝试逃跑（可能失败）"))));
            inv.setItem(25, UtilGui.button(Material.SHIELD, lang.ui("battle.gui.switch.title", "§d换精灵"), List.of(
                        lang.ui("battle.gui.switch.l1", "§7打开队伍选择界面"),
                        lang.ui("battle.gui.switch.l2", "§7换人会消耗本回合行动")
                )));
        }
        inv.setItem(26, UtilGui.button(Material.BARRIER, lang.ui("battle.gui.close.title", "§c关闭"), List.of(
                        lang.ui("battle.gui.close.l1", "§7关闭界面不会立刻结束战斗"),
                        lang.ui("battle.gui.close.l2", "§7你可随时 /battle 继续")
                )));
    }

    private ItemStack moveButtonSafe(Move m, MoveSlot slot) {
        // Empty / broken slot
        if (slot == null || m == null) {
            return UtilGui.button(Material.PAPER, plugin.getLang().ui("battle.gui.move.empty_title", "§7(无招式)"), java.util.List.of(plugin.getLang().ui("battle.gui.move.empty_lore", "§8该槽位为空或招式数据缺失")));
        }

        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta meta = it.getItemMeta();
        LangManager lang = plugin.getLang();
        String mName = (lang == null) ? m.name() : lang.move(m.id(), m.name());
        meta.setDisplayName("§f" + mName);
        java.util.List<String> lore = new java.util.ArrayList<>();
        lore.add(lang.uiFmt("battle.gui.move.l1", "§7属性: {type}  §7分类: {cat}", java.util.Map.of("type", lang.typeName(m.type()), "cat", lang.categoryName(m.category()))));
        lore.add(lang.uiFmt("battle.gui.move.l2", "§7威力: {power}  §7命中: {acc}%", java.util.Map.of("power", prettyMovePower(m), "acc", String.valueOf((int)(m.accuracy()*100)))));
        lore.add(lang.uiFmt("battle.gui.move.l3", "§7PP: {cur}/{max}", java.util.Map.of("cur", String.valueOf(slot.pp), "max", String.valueOf(slot.maxPp))));
        String desc = MoveDescriptionCatalog.descriptionFor(lang, m.id());
        if (desc != null && !desc.isBlank()) {
            lore.add("");
            lore.add(lang.ui("label.move_effect", "§6招式效果"));
            for (String line : Util.wrapLore("§7" + desc, 28)) lore.add(line);
        }
        java.util.List<String> extra = extraMoveLore(m);
        if (!extra.isEmpty()) {
            if (desc == null || desc.isBlank()) lore.add("");
            lore.addAll(extra);
        }
        lore.add(slot.pp <= 0 ? lang.ui("battle.gui.move.no_pp", "§cPP 不足") : lang.ui("battle.gui.move.click_use", "§8点击使用"));
        meta.setLore(lore);
        it.setItemMeta(meta);
        return it;
    }

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

    public void handleBattleClick(Player player, GuiHolder holder, int rawSlot, ItemStack current) {
        BattleSession s = holder.battleSession;
        if (s == null || s.finished) return;

        // If the player must choose a replacement due to fainting, only allow switching.
        if (s.awaitingForcedSwitch) {
            if (rawSlot == 25) {
                openBattleSwitchGui(player, s);
            } else {
                player.sendMessage(plugin.getLang().ui("battle.forced_switch", "§e你的精灵已昏厥，请先选择要派出的精灵。"));
                openBattleSwitchGui(player, s);
            }
            return;
        }

        // While a turn is being processed, block all interactions except closing the GUI.
        if (s.processingTurn && rawSlot != 26) {
            player.sendMessage(plugin.getLang().ui("battle.wait_turn", "§e请稍等，本回合正在结算..."));
            return;
        }

        if (rawSlot == 24) {
            if (s.pvp) {
                player.sendMessage("§7PVP战斗中禁止逃跑。");
            } else {
                handleRunAttempt(player, s);
            }
            return;
        }

        if (rawSlot == 25) {
            if (s.pvp) {
                player.sendMessage("§7PVP战斗中不能主动切换（仅在昏厥时强制切换）。");
            } else {
                openBattleSwitchGui(player, s);
            }
            return;
        }

        if (rawSlot == 22) {
            openBattleItemSelectGui(player, s);
            return;
        }

        if (rawSlot == 23) {
            if (s.pvp) {
                player.sendMessage(plugin.getLang().ui("battle.pvp.no_capture", "§cPVP战斗中严格禁止捕捉！"));
            } else {
                tryThrowBall(player, s);
            }
            return;
        }

        if (rawSlot == 26) {
            player.closeInventory();
            // actual close handling is in onBattleGuiClosed
            return;
        }

        if (rawSlot >= 18 && rawSlot <= 21) {
            int moveIndex = rawSlot - 18;
            if (s.pvp) submitPvpMove(player, s, moveIndex);
            else startTurn(player, s, moveIndex);
        }
    }

    /**
     * Attempt to run from a wild battle using a main-series-like probability formula
     * (speed-based, with increasing chance per attempt).
     *
     * Success: ends the battle immediately.
     * Failure: consumes the player's action; wild gets a move, then end-of-turn applies.
     */
    private void handleRunAttempt(Player player, BattleSession s) {
        if (player == null || s == null || s.finished) return;

        // Only makes sense for wild battles; if no wild entity, just deny politely.
        if (s.wildEntityId == null) {
            player.sendMessage("§7这里无法逃跑。");
            return;
        }

        // Run Away ability: guaranteed escape.
        try {
            if (s.playerMon != null && AbilityEffects.has(s.playerMon, "runaway")) {
                player.sendMessage("§6【逃跑】§e你轻松地逃走了！");
                endBattle(player.getUniqueId(), "§e你逃跑了。", true);
                try { player.closeInventory(); } catch (Exception ignored) {}
                return;
            }
        } catch (Throwable ignored) {}

        // Consume an attempt.
        s.escapeAttempts = Math.max(0, s.escapeAttempts) + 1;

        Species myS = (s.playerMon == null) ? null : dex.getSpecies(s.playerMon.effectiveSpeciesId());
        Species wildS = (s.wildMon == null) ? null : dex.getSpecies(s.wildMon.effectiveSpeciesId());

        int mySpeed = calcEffectiveSpeed(s.playerMon, myS, s);
        int wildSpeed = calcEffectiveSpeed(s.wildMon, wildS, s);

        boolean success;
        if (mySpeed > wildSpeed) {
            success = true;
        } else {
            // Modern-style escape formula (close to main series and easy to reason about):
            // chance = floor( (mySpeed * 128) / wildSpeed ) + 30 * attempts, capped at 255.
            if (wildSpeed <= 0) wildSpeed = 1;
            int chance = (int) Math.floor((mySpeed * 128.0) / wildSpeed) + 30 * s.escapeAttempts;
            if (chance > 255) chance = 255;
            int roll = Util.RND.nextInt(256);
            success = roll < chance;
        }

        if (success) {
            endBattle(player.getUniqueId(), "§e你成功逃跑了。", true);
            try { player.closeInventory(); } catch (Exception ignored) {}
            return;
        }

        // Failure: consume action; wild acts.
        sendBattleLine(s, plugin.getLang().ui("battle.log.escape_fail", "§c逃跑失败！"));
        startItemTurn(player, s, () -> sendBattleLine(s, plugin.getLang().ui("battle.log.escape_try_fail", "§7你尝试逃跑，但没能成功...")));
    }

    /** Compute effective Speed for escape checks (base stat + stage + status + ability multipliers). */
    private int calcEffectiveSpeed(PokemonInstance p, Species sp, BattleSession s) {
        if (p == null) return 1;
        int base;
        try {
            base = p.calcStat(sp, "spe", p.ivSpe, p.evSpe, false);
        } catch (Throwable t) {
            base = 1;
        }

        double v = base * p.stageMultiplier("spe");

        // Paralysis speed drop (Gen1-like: quarter speed).
        String st = (p.status == null) ? "none" : p.status.toLowerCase(java.util.Locale.ROOT);
        if (st.equals("paralyze") || st.equals("paralysis")) {
            v *= 0.25;
        }

        // Ability-based speed modifiers (Chlorophyll, Swift Swim, Unburden, etc.).
        try {
            v *= AbilityEffects.speedMultiplier(p, s);
        } catch (Throwable ignored) {}
        if (s != null) {
            boolean playerSide = s.playerMon == p;
            if (playerSide ? s.playerTailwindTurns > 0 : s.wildTailwindTurns > 0) {
                v *= 2.0;
            }
        }

        return Math.max(1, (int) Math.floor(v));
    }

    private void openBattleSwitchGui(Player player, BattleSession s) {
        suppressBattleGuiReopen(s, 1500);
        try {
            if (player != null && plugin.getBridgeSyncManager() != null && plugin.getBridgeSyncManager().isBridgeClient(player.getUniqueId())) {
                s.guiOpen = true;
                plugin.getBridgeSyncManager().syncBattleState(player);
                return;
            }
        } catch (Throwable ignored) {}
        GuiHolder holder = new GuiHolder(GuiType.BATTLE_SWITCH, player.getUniqueId());
        holder.battleSession = s;
        LangManager lang = plugin.getLang();
        Inventory inv = Bukkit.createInventory(holder, 27, UtilGui.title(lang.ui("gui.battle.switch_title", "§5换精灵")));
        PlayerProfile prof = storage.getProfile(player.getUniqueId());

        for (int i = 0; i < 6; i++) {
            if (i >= prof.party.size()) {
                inv.setItem(i, UtilGui.button(Material.GRAY_STAINED_GLASS_PANE, "§7空位", List.of()));
                continue;
            }
            PokemonInstance p = prof.party.get(i);
            Species sp = dex.getSpecies(p.speciesId);
            String name = (lang == null || sp == null) ? (sp == null ? p.speciesId : sp.name()) : lang.species(sp.id(), sp.name());
            boolean fainted = p.currentHp <= 0;
            boolean egg = p.isEgg;
            Material mat = egg ? Material.EGG : (fainted ? Material.BLACK_STAINED_GLASS_PANE : Material.WOLF_SPAWN_EGG);
            inv.setItem(i, UtilGui.button(mat,
                    (egg ? "§7" : (fainted ? "§8" : "§a")) + (i + 1) + ". " + name + (egg ? "" : (" Lv." + p.level)),
                    List.of("§7HP: " + p.currentHp + "/" + (sp == null ? p.maxHp(null) : p.maxHp(sp)),
                            egg ? "§c蛋不能出战" : (fainted ? "§c已昏厥" : "§e点击切换"))));
        }

        if (s.awaitingForcedSwitch) {
            inv.setItem(26, UtilGui.button(Material.BARRIER, "§c必须选择一只可战斗的精灵",
                    List.of("§7你的精灵昏厥了。", "§7请选择要派出的精灵。", "§a强制换人不消耗行动")));
        } else {
            inv.setItem(26, UtilGui.button(Material.ARROW, "§e返回", List.of("§7返回战斗界面")));
        }
        player.openInventory(inv);
    }

    public void handleBattleSwitchClick(Player player, GuiHolder holder, int rawSlot, ItemStack current) {
        BattleSession s = holder.battleSession;
        if (s == null || s.finished) {
            player.closeInventory();
            return;
        }

        final boolean wasForced = s.awaitingForcedSwitch;
        if (rawSlot == 26) {
            // back to battle (unless forced)
            if (!s.awaitingForcedSwitch) {
                openBattleGui(player, s);
            } else {
                player.sendMessage("§e请先选择要派出的精灵。");
                openBattleSwitchGui(player, s);
            }
            return;
        }
        if (rawSlot < 0 || rawSlot > 5) return;

        PlayerProfile prof = storage.getProfile(player.getUniqueId());
        if (rawSlot >= prof.party.size()) return;
        PokemonInstance chosen = prof.party.get(rawSlot);
        if (chosen.isEgg) {
            player.sendMessage("§c蛋不能参与战斗。");
            return;
        }
        if (chosen.currentHp <= 0) {
            player.sendMessage("§c这只精灵已昏厥，无法切换。");
            return;
        }
        // Already active
        if (chosen.uuid.equals(s.playerMon.uuid)) {
            player.sendMessage("§e它已经在场上了。");
            return;
        }

        // Trapping abilities (Shadow Tag / Arena Trap / Magnet Pull)
        try {
            PokemonInstance self = s.playerMon;
            PokemonInstance foe = s.wildMon;
            Species selfS = self == null ? null : dex.getSpecies(self.effectiveSpeciesId());
            Species foeS = foe == null ? null : dex.getSpecies(foe.effectiveSpeciesId());
            String reason = AbilityEffects.trappedReason(self, selfS, foe, foeS);
            if (reason != null) {
                player.sendMessage(reason);
                // keep player in switch GUI
                return;
            }
        } catch (Throwable ignored) {}

        // Partial-trapping moves (Wrap/Bind/Clamp/Fire Spin): you can still act (modern behavior),
        // but you cannot switch out unless this is a forced replacement.
        if (!wasForced) {
            try {
                PokemonInstance self = s.playerMon;
                if (self != null && self.trappedTurnsRemaining > 0) {
                    player.sendMessage("§e你被束缚住了，无法切换！");
                    return;
                }
                if (self != null && self.noRetreatActive) {
                    player.sendMessage("§e你的精灵背水一战，无法切换！");
                    return;
                }
                if (s.fairyLockTurns > 0) {
                    player.sendMessage("§e仙境之锁封住了战场，无法切换！");
                    return;
                }
            } catch (Throwable ignored) {}
        }

        // Switch is the player's action for this turn.
        PokemonInstance oldMon = s.playerMon;
        Species oldS = oldMon == null ? null : dex.getSpecies(oldMon.effectiveSpeciesId());
        if (oldMon != null && oldS != null) {
            String oldName = (plugin.getLang() == null) ? (oldS.name()) : plugin.getLang().species(oldS.id(), oldS.name());
            sendBattleLines(s, AbilityEffects.onSwitchOut(plugin, oldMon, oldS, oldName));
        }

        s.playerMon = chosen;
        chosen.justSwitchedIn = true;
        chosen.typeChangeAbilityUsed = false;
        // Switching resets escape attempt counter (like main-series).
        s.escapeAttempts = 0;
        // Switching out removes Choice lock.
        chosen.choiceLockedMoveId = null;

        // Switch-in abilities
        Species chosenS2 = dex.getSpecies(chosen.effectiveSpeciesId());
        Species foeS2 = dex.getSpecies(s.wildMon.effectiveSpeciesId());
        sendBattleLines(s, applyEntryHazards(s, true, chosen, chosenS2));
        String chosenName2 = (plugin.getLang() == null || chosenS2 == null) ? (chosenS2 == null ? "?" : chosenS2.name()) : plugin.getLang().species(chosenS2.id(), chosenS2.name());
        String foeName2 = (plugin.getLang() == null || foeS2 == null) ? (foeS2 == null ? "?" : foeS2.name()) : plugin.getLang().species(foeS2.id(), foeS2.name());
        sendBattleLines(s, AbilityEffects.onSwitchIn(plugin, player, s, chosen, chosenS2, s.wildMon, foeS2, chosenName2, foeName2, true));

        // Refresh in-world battle visual
        try {
            Species chosenS = dex.getSpecies(chosen.speciesId);
            if (chosenS != null) refreshPlayerBattleCarrier(player, s, chosen, chosenS);
        } catch (Throwable ignored) {}

        // If this was a forced replacement due to fainting, do NOT count it as a normal action.
        if (wasForced) {
            s.awaitingForcedSwitch = false;
            s.processingTurn = false;
            // Forced replacement does NOT trigger an immediate opponent action and does not
            // resume the current turn. The next turn begins after the switch.
            s.resumeAfterForcedSwitch = false;
            s.statusLine = plugin.getLang().ui("battle.status.choose_move", "§7请选择一个招式。");

            // If we are NOT resuming the turn (e.g., faint happened after both actions or on residuals),
            // we may still need to apply end-of-turn residuals that were deferred.
            if (!s.resumeAfterForcedSwitch && s.pendingEndOfTurnAfterForcedSwitch) {
                s.pendingEndOfTurnAfterForcedSwitch = false;
                try {
                    List<String> endLines = applyEndOfTurn(player, s);
                    sendBattleLines(s, endLines);
                } catch (Throwable ignored) {}
            }
        } else {
            s.processingTurn = true;
            s.statusLine = "§d你切换了精灵！";
        }

        // Close the current switch selector first so the player cannot keep clicking old choices.
        try { player.closeInventory(); } catch (Throwable ignored) {}

        // Re-open battle GUI immediately with the new active Pokémon.
        openBattleGui(player, s);
        renderOpenVanillaBattleInventory(player, s);

        // PvP: voluntary switching is disabled, and forced switching must be synchronized.
        if (s.pvp && s.pvpKey != null) {
            try {
                PvpBattle pb = pvpBattles.get(s.pvpKey);
                if (pb != null && wasForced) {
                    // Update canonical references (sa expects playerMon=A, wildMon=B).
                    if (player.getUniqueId().equals(pb.a)) {
                        pb.sa.playerMon = s.playerMon;
                        pb.sb.wildMon = s.playerMon;
                    } else {
                        pb.sb.playerMon = s.playerMon;
                        pb.sa.wildMon = s.playerMon;
                    }

                    // If both sides have finished forced switching, continue.
                    if (!pb.sa.awaitingForcedSwitch && !pb.sb.awaitingForcedSwitch) {
                        pb.waitingForcedSwitch = false;
                        pb.processing = false;
                        pb.sa.processingTurn = false;
                        pb.sb.processingTurn = false;
                        pb.moveA = null;
                        pb.moveB = null;
                        pb.sa.statusLine = "§7请选择一个招式。";
                        pb.sb.statusLine = "§7请选择一个招式。";
                        refreshPvpBattleGuis(pb);
                    }
                }
            } catch (Throwable ignored) {}
            return;
        }

        // After a short delay, let the wild Pokémon act once ONLY for a voluntary switch.
        if (!wasForced) new BukkitRunnable() {
            @Override public void run() {
                if (s.finished || !sessions.containsKey(player.getUniqueId())) return;
                try {
                    // Wild chooses a random move
                    int wildChoice = Util.RND.nextInt(4);
                    MoveSlot wildSlot = (s.wildMon.effectiveMoves() != null && wildChoice < s.wildMon.effectiveMoves().size()) ? s.wildMon.effectiveMoves().get(wildChoice) : null;
                    Move wildMove = (wildSlot == null) ? null : dex.getMoveOrPlaceholder(wildSlot.moveId);
                    if (wildSlot == null || wildMove == null) {
                        wildSlot = MoveSlot.of(dex.getMoveOrPlaceholder("tackle"));
                        s.wildMon.ensureBattleMovesFromBase();
                        s.wildMon.ensureBattleMovesFromBase();
                        while (s.wildMon.effectiveMoves().size() < 4) s.wildMon.effectiveMoves().add(MoveSlot.of(dex.getMoveOrPlaceholder("tackle")));
                        s.wildMon.effectiveMoves().set(wildChoice, wildSlot);
                        wildMove = dex.getMoveOrPlaceholder(wildSlot.moveId);
                    }
                    if (wildSlot.pp > 0) wildSlot.pp--;

                    // Announce & apply wild move
                    List<String> lines = applyMoveDescribed(player, s, false, wildMove);
                    sendBattleLines(s, lines);
                    renderOpenVanillaBattleInventory(player, s);
                } catch (Throwable t) {
                    abortTurnSafely(player, s, "switchWildAction", t);
                    return;
                }

                // Faint checks
                if (s.playerMon.currentHp <= 0) {
                    lose(player, s);
                    if (s.finished) {
                        player.closeInventory();
                        return;
                    }
                    if (s.awaitingForcedSwitch) {
                        // Defer residuals and stop; replacement happens now.
                        s.pendingEndOfTurnAfterForcedSwitch = true;
                        s.resumeAfterForcedSwitch = false;
                        s.processingTurn = false;
                        try { openBattleSwitchGui(player, s); } catch (Throwable ignored) {}
                        return;
                    }
                }
                if (s.wildMon.currentHp <= 0) {
                    win(player, s);
                    player.closeInventory();
                    return;
                }

                // End-of-turn residual effects
                try {
                    List<String> endLines = applyEndOfTurn(player, s);
                    sendBattleLines(s, endLines);
                    renderOpenVanillaBattleInventory(player, s);
                } catch (Throwable t) {
                    abortTurnSafely(player, s, "switchEndOfTurn", t);
                    return;
                }

                // Faint checks after residual damage
                if (s.playerMon.currentHp <= 0) {
                    lose(player, s);
                    if (s.finished) {
                        player.closeInventory();
                        return;
                    }
                }
                if (s.wildMon.currentHp <= 0) {
                    win(player, s);
                    player.closeInventory();
                    return;
                }

                // Advance turn
                s.turn++;
                s.processingTurn = false;
                s.statusLine = plugin.getLang().ui("battle.status.choose_move", "§7请选择一个招式。");
                renderOpenVanillaBattleInventory(player, s);
            }
        }.runTaskLater(plugin, 100L);
    }


/**
 * Item turn: player uses an item (already consumed), then wild performs its move, then end-of-turn.
 * This keeps the same pacing/cleanup as normal turns, and increments turn counter.
 */
private void startItemTurn(Player player, BattleSession s, Runnable playerAction) {
    if (player == null || s == null || s.finished) return;
    if (s.processingTurn) {
        player.sendMessage(plugin.getLang().ui("battle.wait_turn", "§e请稍等，本回合正在结算..."));
        return;
    }

    s.processingTurn = true;
    s.statusLine = "§e请稍等...正在结算本回合（道具）";

    // Ensure battle GUI is visible while processing.
    try {
        boolean needOpen = true;
        if (hasVanillaBattleInventoryOpen(player, s)) {
            needOpen = false;
            renderOpenVanillaBattleInventory(player, s);
        }
        if (needOpen) {
            openBattleGui(player, s);
        }
    } catch (Throwable ignored) {}

    // Player action (item)
    try {
        if (playerAction != null) playerAction.run();
    } catch (Throwable t) {
        sendBattleLine(s, plugin.getLang().uiFmt("battle.item.error", "§c道具使用时发生错误: {err}", java.util.Map.of("err", String.valueOf(t.getMessage()))));
    }

    renderOpenVanillaBattleInventory(player, s);

    // Early finish checks (e.g., Poke Doll ends battle)
    if (s.finished || !sessions.containsKey(player.getUniqueId())) {
        return;
    }

    if (s.wildMon.currentHp <= 0) {
        win(player, s);
        try { player.closeInventory(); } catch (Exception ignored) {}
        return;
    }
    if (s.playerMon.currentHp <= 0) {
        lose(player, s);
        if (s.finished) {
            try { player.closeInventory(); } catch (Exception ignored) {}
            return;
        }
        // auto-switch may have happened; continue
    }

    // Wild chooses random move
    int wildChoice = Util.RND.nextInt(4);
    MoveSlot wildSlot = (s.wildMon.effectiveMoves() != null && wildChoice < s.wildMon.effectiveMoves().size())
            ? s.wildMon.effectiveMoves().get(wildChoice) : null;
    Move wildMove = (wildSlot == null) ? null : dex.getMoveOrPlaceholder(wildSlot.moveId);
    if (wildSlot == null || wildMove == null) {
        wildSlot = MoveSlot.of(dex.getMoveOrPlaceholder("tackle"));
        s.wildMon.ensureBattleMovesFromBase();
        while (s.wildMon.effectiveMoves().size() < 4) s.wildMon.effectiveMoves().add(MoveSlot.of(dex.getMoveOrPlaceholder("tackle")));
        s.wildMon.effectiveMoves().set(wildChoice, wildSlot);
        wildMove = dex.getMoveOrPlaceholder(wildSlot.moveId);
    }
    if (wildSlot.pp > 0) wildSlot.pp--;

    // Delay to keep turn-based feel similar to startTurn()
    final Move wildMoveFinal = wildMove;
    new BukkitRunnable() {
        @Override public void run() {
            if (s.finished || !sessions.containsKey(player.getUniqueId())) return;

            List<String> lines = applyMoveDescribed(player, s, false, wildMoveFinal);
            sendBattleLines(s, lines);
            renderOpenVanillaBattleInventory(player, s);

            if (s.wildMon.currentHp <= 0) {
                win(player, s);
                try { player.closeInventory(); } catch (Exception ignored) {}
                return;
            }
            if (s.playerMon.currentHp <= 0) {
                lose(player, s);
                if (s.finished) {
                    try { player.closeInventory(); } catch (Exception ignored) {}
                    return;
                }
            }

            // End-of-turn effects
            List<String> endLines = applyEndOfTurn(player, s);
            sendBattleLines(s, endLines);
            renderOpenVanillaBattleInventory(player, s);

            if (s.wildMon.currentHp <= 0) {
                win(player, s);
                try { player.closeInventory(); } catch (Exception ignored) {}
                return;
            }
            if (s.playerMon.currentHp <= 0) {
                lose(player, s);
                if (s.finished) {
                    try { player.closeInventory(); } catch (Exception ignored) {}
                    return;
                }
            }

            s.turn++;
            s.processingTurn = false;
            s.statusLine = plugin.getLang().ui("battle.status.choose_move", "§7请选择一个招式。");
            renderOpenVanillaBattleInventory(player, s);
        }
    }.runTaskLater(plugin, 100L);
}

    private void startTurn(Player player, BattleSession s, int moveIndex) {
        // If the player was forced to switch due to fainting mid-turn (before their action),
        // the forced replacement should NOT consume an action; the player still gets to act
        // as the remaining action of the current turn.
        if (s.resumeAfterForcedSwitch) {
            startTurnAsSecondActionAfterForcedSwitch(player, s, moveIndex);
            return;
        }
        if (s.processingTurn) {
            player.sendMessage(plugin.getLang().ui("battle.wait_turn", "§e请稍等，本回合正在结算..."));
            return;
        }
        s.processingTurn = true;
        s.statusLine = "§e请稍等...正在结算本回合";
        renderOpenVanillaBattleInventory(player, s);

        Species myS = dex.getSpecies(s.playerMon.effectiveSpeciesId());
        Species wildS = dex.getSpecies(s.wildMon.effectiveSpeciesId());

        // Resolve player's selected move safely.
        MoveSlot mySlot = (s.playerMon.effectiveMoves() != null && moveIndex < s.playerMon.effectiveMoves().size()) ? s.playerMon.effectiveMoves().get(moveIndex) : null;
        Move myMove = (mySlot == null) ? null : dex.getMoveOrPlaceholder(mySlot.moveId);
        if (mySlot == null || myMove == null) {
            player.sendMessage("§c该招式槽位无效，已自动使用 撞击。");
            mySlot = MoveSlot.of(dex.getMoveOrPlaceholder("tackle"));
            // Ensure list has enough slots
            s.playerMon.ensureBattleMovesFromBase();
            while (s.playerMon.effectiveMoves().size() < 4) s.playerMon.effectiveMoves().add(MoveSlot.of(dex.getMoveOrPlaceholder("tackle")));
            s.playerMon.effectiveMoves().set(moveIndex, mySlot);
            myMove = dex.getMoveOrPlaceholder(mySlot.moveId);
        }

        // Choice items: if already locked, force the locked move.
        if ((HeldItemEffects.isChoiceItem(s.playerMon.heldItemId) || AbilityEffects.has(s.playerMon, "gorillatactics"))
                && s.playerMon.choiceLockedMoveId != null
                && myMove != null && myMove.id() != null
                && !myMove.id().equalsIgnoreCase(s.playerMon.choiceLockedMoveId)) {
            String lockedId = s.playerMon.choiceLockedMoveId;
            Move locked = dex.getMoveOrPlaceholder(lockedId);
            // Try find the slot containing the locked move for proper PP use.
            int lockedIdx = -1;
            if (s.playerMon.effectiveMoves() != null) {
                for (int i = 0; i < s.playerMon.effectiveMoves().size(); i++) {
                    MoveSlot ms = s.playerMon.effectiveMoves().get(i);
                    if (ms != null && ms.moveId != null && ms.moveId.equalsIgnoreCase(lockedId)) { lockedIdx = i; break; }
                }
            }
            if (lockedIdx >= 0) {
                mySlot = s.playerMon.effectiveMoves().get(lockedIdx);
            }
            myMove = locked;
            player.sendMessage("§e讲究道具的效果让你只能使用 §f" + locked.name() + "§e！");
        }
        if (mySlot.pp <= 0) {
            player.sendMessage("§cPP 不足！");
            s.processingTurn = false;
            s.statusLine = plugin.getLang().ui("battle.status.choose_move", "§7请选择一个招式。");
            return;
        }
        mySlot.pp--;

        // Mystery Berry: if PP becomes 0, restore 5 PP to that move once.
        if (mySlot.pp == 0 && "mystery_berry".equalsIgnoreCase(s.playerMon.heldItemId)) {
            int restore = Math.min(5, Math.max(0, mySlot.maxPp - mySlot.pp));
            if (restore > 0) {
                mySlot.pp += restore;
                s.playerMon.heldItemId = null;
                sendBattleLine(s, plugin.getLang().uiFmt("battle.log.mystery_berry", "§a{mon} 的§f神秘果§a回复了招式PP！", java.util.Map.of("mon", myS.name())));
            }
        }

        // Apply choice lock after confirming PP and selecting the move.
        if ((HeldItemEffects.isChoiceItem(s.playerMon.heldItemId) || AbilityEffects.has(s.playerMon, "gorillatactics")) && s.playerMon.choiceLockedMoveId == null && myMove != null) {
            s.playerMon.choiceLockedMoveId = myMove.id();
        }

        // Wild chooses random move
        int wildChoice = Util.RND.nextInt(4);
        MoveSlot wildSlot = (s.wildMon.effectiveMoves() != null && wildChoice < s.wildMon.effectiveMoves().size()) ? s.wildMon.effectiveMoves().get(wildChoice) : null;
        Move wildMove = (wildSlot == null) ? null : dex.getMoveOrPlaceholder(wildSlot.moveId);
        if (wildSlot == null || wildMove == null) {
            wildSlot = MoveSlot.of(dex.getMoveOrPlaceholder("tackle"));
            s.wildMon.ensureBattleMovesFromBase();
                    s.wildMon.ensureBattleMovesFromBase();
            while (s.wildMon.effectiveMoves().size() < 4) s.wildMon.effectiveMoves().add(MoveSlot.of(dex.getMoveOrPlaceholder("tackle")));
            s.wildMon.effectiveMoves().set(wildChoice, wildSlot);
            wildMove = dex.getMoveOrPlaceholder(wildSlot.moveId);
        }
        if (wildSlot.pp > 0) wildSlot.pp--;

        if ((HeldItemEffects.isChoiceItem(s.wildMon.heldItemId) || AbilityEffects.has(s.wildMon, "gorillatactics")) && s.wildMon.choiceLockedMoveId == null && wildMove != null) {
            s.wildMon.choiceLockedMoveId = wildMove.id();
        }

        s.playerPlannedMoveId = (myMove == null ? null : myMove.id());
        s.wildPlannedMoveId = (wildMove == null ? null : wildMove.id());

        // Capture which Pokémon chose which move, so a faint/forced switch can't "carry" the move to the next mon.
        final java.util.UUID plannedPlayerUuid = (s.playerMon == null ? null : s.playerMon.uuid);
        final java.util.UUID plannedWildUuid = (s.wildMon == null ? null : s.wildMon.uuid);

        // Reset per-turn hit tracking for dynamic later-gen move power and retaliation memory.
        s.playerMon.tookDamageThisTurn = false;
        s.wildMon.tookDamageThisTurn = false;
        MoveEngine.resetRetaliationMemory(s.playerMon);
        MoveEngine.resetRetaliationMemory(s.wildMon);

        // Determine order by priority then speed
        int myPri = myMove.priority() + AbilityEffects.priorityBonus(s.playerMon, myMove);
        int wildPri = wildMove.priority() + AbilityEffects.priorityBonus(s.wildMon, wildMove);
        boolean myFirst;
        if (myPri != wildPri) myFirst = myPri > wildPri;
        else {
            int mySpe = s.playerMon.calcStat(myS, "spe", s.playerMon.ivSpe, s.playerMon.evSpe, false);
            int wSpe = s.wildMon.calcStat(wildS, "spe", s.wildMon.ivSpe, s.wildMon.evSpe, false);

            // Apply speed stages and held item speed modifiers
            mySpe = calcEffectiveSpeed(s.playerMon, myS, s);
            wSpe  = calcEffectiveSpeed(s.wildMon, wildS, s);

            // Lagging Tail / Full Incense: always move last on priority tie
            boolean myLast = HeldItemEffects.forcesLast(s.playerMon) || AbilityEffects.alwaysMovesLast(s.playerMon, myMove);
            boolean wLast  = HeldItemEffects.forcesLast(s.wildMon) || AbilityEffects.alwaysMovesLast(s.wildMon, wildMove);

            // Quick Claw / Quick Draw: chance to move first on priority tie
            boolean myQC = HeldItemEffects.quickClawTriggers(s.playerMon) || AbilityEffects.quickDrawTriggers(s.playerMon, myMove);
            boolean wQC  = HeldItemEffects.quickClawTriggers(s.wildMon) || AbilityEffects.quickDrawTriggers(s.wildMon, wildMove);

            if (myLast != wLast) {
                myFirst = !myLast;
            } else if (myQC != wQC) {
                myFirst = myQC;
                if (myQC) player.sendMessage("§e§l【Quick Claw】§e" + s.playerMon.displayName() + " 抢先行动！");
                else player.sendMessage("§e§l【Quick Claw】§e野生精灵抢先行动！");
            } else {
                myFirst = (s.trickRoomTurns > 0) ? (mySpe <= wSpe) : (mySpe >= wSpe);
            }
        }

        
        // Ability helper: mark who acts last this turn (for Analytic etc.)
        s.playerMon.actedLastThisTurn = !myFirst;
        s.wildMon.actedLastThisTurn = myFirst;
        final Move firstMove = myFirst ? myMove : wildMove;
        final boolean firstIsPlayer = myFirst;
        final java.util.UUID firstActorUuid = myFirst ? plannedPlayerUuid : plannedWildUuid;
        final Move secondMove = myFirst ? wildMove : myMove;
        final boolean secondIsPlayer = !myFirst;
        final java.util.UUID secondActorUuid = myFirst ? plannedWildUuid : plannedPlayerUuid;

        // Step 1: announce & apply first action
        new BukkitRunnable() {
            @Override public void run() {
                if (s.finished || !sessions.containsKey(player.getUniqueId())) return;
                try {
                    // If the intended actor has fainted or been replaced, skip this action.
                    PokemonInstance intended = firstIsPlayer ? s.playerMon : s.wildMon;
                    if (intended == null || intended.currentHp <= 0 || (firstActorUuid != null && !firstActorUuid.equals(intended.uuid))) {
                        sendBattleLine(s, plugin.getLang().ui("battle.log.cannot_move_anymore", "§7但是它已无法行动了！"));
                    } else {
                    List<String> lines = applyMoveDescribed(player, s, firstIsPlayer, firstMove);
                    sendBattleLines(s, lines);
                    renderOpenVanillaBattleInventory(player, s);
                    }
                } catch (Throwable t) {
                    abortTurnSafely(player, s, "firstAction", t);
                    return;
                }

                if (s.wildMon.currentHp <= 0) {
                    win(player, s);
                    player.closeInventory();
                    return;
                }
                if (s.playerMon.currentHp <= 0) {
                    lose(player, s);
                    if (s.finished) {
                        player.closeInventory();
                    }
                    // If the player must choose a replacement, stop the turn here.
                    if (s.awaitingForcedSwitch) {
                        // Gen1/main-series behavior: forced replacement due to fainting
                        // does NOT grant an extra action in the same turn.
                        // Defer end-of-turn residuals until after the replacement is chosen.
                        s.pendingEndOfTurnAfterForcedSwitch = true;
                        s.resumeAfterForcedSwitch = false;
                        s.processingTurn = false;
                        try { openBattleSwitchGui(player, s); } catch (Throwable ignored) {}
                        return;
                    }
                }

                // Step 2: announce & apply second action
                new BukkitRunnable() {
                    @Override public void run() {
                        if (s.finished || !sessions.containsKey(player.getUniqueId())) return;
                        try {
                            // If the intended actor has fainted or been replaced, skip this action.
                            PokemonInstance intended2 = secondIsPlayer ? s.playerMon : s.wildMon;
                            if (intended2 == null || intended2.currentHp <= 0 || (secondActorUuid != null && !secondActorUuid.equals(intended2.uuid))) {
                                sendBattleLine(s, plugin.getLang().ui("battle.log.cannot_move_anymore", "§7但是它已无法行动了！"));
                            } else {
                            List<String> lines2 = applyMoveDescribed(player, s, secondIsPlayer, secondMove);
                            sendBattleLines(s, lines2);
                            renderOpenVanillaBattleInventory(player, s);
                            }
                        } catch (Throwable t) {
                            abortTurnSafely(player, s, "secondAction", t);
                            return;
                        }

                        if (s.wildMon.currentHp <= 0) {
                            win(player, s);
                            player.closeInventory();
                            return;
                        }
                        if (s.playerMon.currentHp <= 0) {
                            lose(player, s);
                            if (s.finished) {
                                player.closeInventory();
                                return;
                            }
                        }

                        // End-of-turn effects (poison/burn/toxic/leech-seed/screens)
                        try {
                            List<String> endLines = applyEndOfTurn(player, s);
                            sendBattleLines(s, endLines);
                            renderOpenVanillaBattleInventory(player, s);
                        } catch (Throwable t) {
                            abortTurnSafely(player, s, "endOfTurn", t);
                            return;
                        }

                        // Faint checks after residual damage
                        if (s.wildMon.currentHp <= 0) {
                            win(player, s);
                            player.closeInventory();
                            return;
                        }
                        if (s.playerMon.currentHp <= 0) {
                            lose(player, s);
                            if (s.finished) {
                                player.closeInventory();
                                return;
                            }
                        }

                        // End turn
                        s.turn++;
                        s.processingTurn = false;
                        s.statusLine = plugin.getLang().ui("battle.status.choose_move", "§7请选择一个招式。");
                        renderOpenVanillaBattleInventory(player, s);
                    }
                }.runTaskLater(plugin, 40L);
            }
        // Delay to make turn-based feel stronger (default ~5 seconds)
        }.runTaskLater(plugin, 100L);
    }

    /**
     * Resume a turn after the wild Pokémon has already acted and fainted the player's active Pokémon.
     * The player has been forced to send a replacement and now chooses a move.
     *
     * This is treated as the SECOND action of the same turn (wild already moved),
     * and thus does not re-run speed ordering or a wild move.
     */
    private void startTurnAsSecondActionAfterForcedSwitch(Player player, BattleSession s, int moveIndex) {
        if (player == null || s == null || s.finished) return;
        if (s.processingTurn) {
            player.sendMessage(plugin.getLang().ui("battle.wait_turn", "§e请稍等，本回合正在结算..."));
            return;
        }

        // Clear the resume flag now to avoid re-entry.
        s.resumeAfterForcedSwitch = false;

        s.processingTurn = true;
        s.statusLine = "§e请稍等...正在结算本回合（补行动）";
        renderOpenVanillaBattleInventory(player, s);

        Species myS = dex.getSpecies(s.playerMon.effectiveSpeciesId());
        Species wildS = dex.getSpecies(s.wildMon.effectiveSpeciesId());

        // Resolve player's selected move safely.
        MoveSlot mySlot = (s.playerMon.effectiveMoves() != null && moveIndex < s.playerMon.effectiveMoves().size()) ? s.playerMon.effectiveMoves().get(moveIndex) : null;
        Move myMove = (mySlot == null) ? null : dex.getMoveOrPlaceholder(mySlot.moveId);
        if (mySlot == null || myMove == null) {
            player.sendMessage("§c该招式槽位无效，已自动使用 撞击。");
            mySlot = MoveSlot.of(dex.getMoveOrPlaceholder("tackle"));
            s.playerMon.ensureBattleMovesFromBase();
            while (s.playerMon.effectiveMoves().size() < 4) s.playerMon.effectiveMoves().add(MoveSlot.of(dex.getMoveOrPlaceholder("tackle")));
            s.playerMon.effectiveMoves().set(moveIndex, mySlot);
            myMove = dex.getMoveOrPlaceholder(mySlot.moveId);
        }

        // PP consumption: keep consistent with the project's existing PP system.
        // We only decrement here if still positive to avoid going negative.
        if (mySlot.pp > 0) mySlot.pp--;

        final Move myMoveFinal = myMove;
        new BukkitRunnable() {
            @Override public void run() {
                if (s.finished || !sessions.containsKey(player.getUniqueId())) return;

                try {
                    List<String> lines = applyMoveDescribed(player, s, true, myMoveFinal);
                    sendBattleLines(s, lines);
                    renderOpenVanillaBattleInventory(player, s);
                } catch (Throwable t) {
                    abortTurnSafely(player, s, "resumeSecondAction", t);
                    return;
                }

                if (s.wildMon.currentHp <= 0) {
                    win(player, s);
                    try { player.closeInventory(); } catch (Exception ignored) {}
                    return;
                }
                if (s.playerMon.currentHp <= 0) {
                    lose(player, s);
                    if (s.finished) {
                        try { player.closeInventory(); } catch (Exception ignored) {}
                        return;
                    }
                    if (s.awaitingForcedSwitch) {
                        // Defer end-of-turn residuals until after replacement is chosen.
                        s.pendingEndOfTurnAfterForcedSwitch = true;
                        s.resumeAfterForcedSwitch = false;
                        s.processingTurn = false;
                        try { openBattleSwitchGui(player, s); } catch (Throwable ignored) {}
                        return;
                    }
                }

                // End-of-turn residual effects
                try {
                    List<String> endLines = applyEndOfTurn(player, s);
                    sendBattleLines(s, endLines);
                    renderOpenVanillaBattleInventory(player, s);
                } catch (Throwable t) {
                    abortTurnSafely(player, s, "resumeSecondActionEndOfTurn", t);
                    return;
                }

                if (s.wildMon.currentHp <= 0) {
                    win(player, s);
                    try { player.closeInventory(); } catch (Exception ignored) {}
                    return;
                }
                if (s.playerMon.currentHp <= 0) {
                    lose(player, s);
                    if (s.finished) {
                        try { player.closeInventory(); } catch (Exception ignored) {}
                        return;
                    }
                }

                s.turn++;
                s.processingTurn = false;
                s.statusLine = plugin.getLang().ui("battle.status.choose_move", "§7请选择一个招式。");
                renderOpenVanillaBattleInventory(player, s);
            }
        }.runTaskLater(plugin, 40L);
    }

    // ---------------------------
    // PvP turn coordination
    // ---------------------------

    private void submitPvpMove(Player player, BattleSession s, int moveIndex) {
        if (player == null || s == null || !s.pvp || s.pvpKey == null) return;
        PvpBattle pb = pvpBattles.get(s.pvpKey);
        if (pb == null) {
            player.sendMessage("§e该PVP战斗已结束。");
            return;
        }
        if (pb.waitingForcedSwitch) {
            player.sendMessage("§e等待强制换人中...");
            return;
        }
        if (pb.processing) {
            player.sendMessage(plugin.getLang().ui("battle.wait_turn", "§e请稍等，本回合正在结算..."));
            return;
        }

        boolean isA = player.getUniqueId().equals(pb.a);
        if (isA) pb.moveA = moveIndex; else pb.moveB = moveIndex;

        // feedback
        player.sendMessage("§7已选择招式，等待对方...");

        if (pb.moveA != null && pb.moveB != null) {
            startPvpTurn(pb);
        }
    }

    private void startPvpTurn(PvpBattle pb) {
        if (pb == null || pb.processing) return;
        Player pa = Bukkit.getPlayer(pb.a);
        Player pbp = Bukkit.getPlayer(pb.b);
        if (pa == null || pbp == null) {
            endPvpBattle(pb, null, "§e对方离线，PVP结束。");
            return;
        }

        pb.processing = true;
        pb.sa.processingTurn = true;
        pb.sb.processingTurn = true;

        // Capture planned UUIDs
        final UUID plannedAUuid = pb.sa.playerMon == null ? null : pb.sa.playerMon.uuid;
        final UUID plannedBUuid = pb.sa.wildMon == null ? null : pb.sa.wildMon.uuid;

        // Resolve selected moves for both sides
        MoveSlot slotA = getMoveSlot(pb.sa.playerMon, pb.moveA);
        MoveSlot slotB = getMoveSlot(pb.sa.wildMon, pb.moveB);
        Move moveA = (slotA == null) ? null : dex.getMoveOrPlaceholder(slotA.moveId);
        Move moveB = (slotB == null) ? null : dex.getMoveOrPlaceholder(slotB.moveId);
        if (slotA == null || moveA == null) {
            slotA = MoveSlot.of(dex.getMoveOrPlaceholder("tackle"));
            pb.sa.playerMon.ensureBattleMovesFromBase();
            while (pb.sa.playerMon.effectiveMoves().size() < 4) pb.sa.playerMon.effectiveMoves().add(MoveSlot.of(dex.getMoveOrPlaceholder("tackle")));
            pb.sa.playerMon.effectiveMoves().set(pb.moveA, slotA);
            moveA = dex.getMoveOrPlaceholder(slotA.moveId);
        }
        if (slotB == null || moveB == null) {
            slotB = MoveSlot.of(dex.getMoveOrPlaceholder("tackle"));
            pb.sa.wildMon.ensureBattleMovesFromBase();
            while (pb.sa.wildMon.effectiveMoves().size() < 4) pb.sa.wildMon.effectiveMoves().add(MoveSlot.of(dex.getMoveOrPlaceholder("tackle")));
            pb.sa.wildMon.effectiveMoves().set(pb.moveB, slotB);
            moveB = dex.getMoveOrPlaceholder(slotB.moveId);
        }
        if (slotA.pp > 0) slotA.pp--;
        if (slotB.pp > 0) slotB.pp--;

        // Choice item locks
        if ((HeldItemEffects.isChoiceItem(pb.sa.playerMon.heldItemId) || AbilityEffects.has(pb.sa.playerMon, "gorillatactics")) && pb.sa.playerMon.choiceLockedMoveId == null && moveA != null) {
            pb.sa.playerMon.choiceLockedMoveId = moveA.id();
        }
        if ((HeldItemEffects.isChoiceItem(pb.sa.wildMon.heldItemId) || AbilityEffects.has(pb.sa.wildMon, "gorillatactics")) && pb.sa.wildMon.choiceLockedMoveId == null && moveB != null) {
            pb.sa.wildMon.choiceLockedMoveId = moveB.id();
        }

        pb.sa.playerPlannedMoveId = (moveA == null ? null : moveA.id());
        pb.sa.wildPlannedMoveId = (moveB == null ? null : moveB.id());

        // Reset per-turn hit tracking for dynamic later-gen move power and retaliation memory.
        pb.sa.playerMon.tookDamageThisTurn = false;
        pb.sa.wildMon.tookDamageThisTurn = false;
        MoveEngine.resetRetaliationMemory(pb.sa.playerMon);
        MoveEngine.resetRetaliationMemory(pb.sa.wildMon);

        // Determine order by priority then speed (use sessionA as canonical)
        Species spA = dex.getSpecies(pb.sa.playerMon.effectiveSpeciesId());
        Species spB = dex.getSpecies(pb.sa.wildMon.effectiveSpeciesId());
        int priA = moveA.priority() + AbilityEffects.priorityBonus(pb.sa.playerMon, moveA);
        int priB = moveB.priority() + AbilityEffects.priorityBonus(pb.sa.wildMon, moveB);
        boolean aFirst;
        if (priA != priB) aFirst = priA > priB;
        else {
            int speA = calcEffectiveSpeed(pb.sa.playerMon, spA, pb.sa);
            int speB = calcEffectiveSpeed(pb.sa.wildMon, spB, pb.sa);
            boolean aLast = HeldItemEffects.forcesLast(pb.sa.playerMon) || AbilityEffects.alwaysMovesLast(pb.sa.playerMon, moveA);
            boolean bLast = HeldItemEffects.forcesLast(pb.sa.wildMon) || AbilityEffects.alwaysMovesLast(pb.sa.wildMon, moveB);
            boolean aQC = HeldItemEffects.quickClawTriggers(pb.sa.playerMon) || AbilityEffects.quickDrawTriggers(pb.sa.playerMon, moveA);
            boolean bQC = HeldItemEffects.quickClawTriggers(pb.sa.wildMon) || AbilityEffects.quickDrawTriggers(pb.sa.wildMon, moveB);
            if (aLast != bLast) aFirst = !aLast;
            else if (aQC != bQC) aFirst = aQC;
            else aFirst = (pb.sa.trickRoomTurns > 0) ? (speA <= speB) : (speA >= speB);
        }
        pb.sa.playerMon.actedLastThisTurn = !aFirst;
        pb.sa.wildMon.actedLastThisTurn = aFirst;

        final Move firstMove = aFirst ? moveA : moveB;
        final boolean firstIsA = aFirst;
        final UUID firstActorUuid = aFirst ? plannedAUuid : plannedBUuid;
        final Move secondMove = aFirst ? moveB : moveA;
        final boolean secondIsA = !aFirst;
        final UUID secondActorUuid = aFirst ? plannedBUuid : plannedAUuid;

        // Step 1
        new BukkitRunnable() {
            @Override public void run() {
                if (pb.sa.finished || pb.sb.finished) return;
                try {
                    PokemonInstance intended = firstIsA ? pb.sa.playerMon : pb.sa.wildMon;
                    if (intended == null || intended.currentHp <= 0 || (firstActorUuid != null && !firstActorUuid.equals(intended.uuid))) {
                        sendPvpMessage(pb, "§7但是它已无法行动了！");
                    } else {
                        Player actor = firstIsA ? pa : pbp;
                        java.util.List<String> lines = applyMoveDescribedPvp(pb, actor, firstIsA, firstMove);
                        sendPvpLines(pb, lines);
                    }
                    refreshPvpBattleGuis(pb);
                } catch (Throwable t) {
                    sendPvpMessage(pb, "§cPVP回合结算异常，已中止。");
                    endPvpBattle(pb, null, "§cPVP异常结束。");
                    return;
                }

                // Faint checks after first action
                if (handlePvpFaintsAfterAction(pb)) return;

                // Step 2
                new BukkitRunnable() {
                    @Override public void run() {
                        if (pb.sa.finished || pb.sb.finished) return;
                        try {
                            PokemonInstance intended2 = secondIsA ? pb.sa.playerMon : pb.sa.wildMon;
                            if (intended2 == null || intended2.currentHp <= 0 || (secondActorUuid != null && !secondActorUuid.equals(intended2.uuid))) {
                                sendPvpMessage(pb, "§7但是它已无法行动了！");
                            } else {
                                Player actor2 = secondIsA ? pa : pbp;
                                java.util.List<String> lines2 = applyMoveDescribedPvp(pb, actor2, secondIsA, secondMove);
                                sendPvpLines(pb, lines2);
                            }
                            refreshPvpBattleGuis(pb);
                        } catch (Throwable t) {
                            sendPvpMessage(pb, "§cPVP回合结算异常，已中止。");
                            endPvpBattle(pb, null, "§cPVP异常结束。");
                            return;
                        }

                        if (handlePvpFaintsAfterAction(pb)) return;

                        // End-of-turn residuals
                        try {
                            java.util.List<String> endLines = applyEndOfTurn(pa, pb.sa);
                            sendPvpLines(pb, endLines);
                        } catch (Throwable ignored) {}

                        pb.sa.turn++;
                        pb.sb.turn = pb.sa.turn;
                        pb.processing = false;
                        pb.sa.processingTurn = false;
                        pb.sb.processingTurn = false;
                        pb.moveA = null;
                        pb.moveB = null;
                        pb.sa.statusLine = "§7请选择一个招式。";
                        pb.sb.statusLine = "§7请选择一个招式。";
                        refreshPvpBattleGuis(pb);
                    }
                }.runTaskLater(plugin, 20L);
            }
        }.runTaskLater(plugin, 0L);
    }

    private MoveSlot getMoveSlot(PokemonInstance mon, Integer idx) {
        if (mon == null || idx == null) return null;
        java.util.List<MoveSlot> moves = mon.effectiveMoves();
        if (moves == null) return null;
        int i = Math.max(0, Math.min(3, idx));
        if (i >= moves.size()) return null;
        return moves.get(i);
    }

    private void sendPvpMessage(PvpBattle pb, String msg) {
        if (pb == null || msg == null) return;
        Player pa = Bukkit.getPlayer(pb.a);
        Player pbp = Bukkit.getPlayer(pb.b);
        if (pa != null) pa.sendMessage(msg);
        if (pbp != null) pbp.sendMessage(msg);
    }

    private void sendPvpLines(PvpBattle pb, java.util.List<String> lines) {
        if (pb == null || lines == null) return;
        for (String ln : lines) {
            if (ln == null) continue;
            sendPvpMessage(pb, ln);
        }
    }

    private void refreshPvpBattleGuis(PvpBattle pb) {
        if (pb == null) return;
        try {
            Player pa = Bukkit.getPlayer(pb.a);
            Player pbp = Bukkit.getPlayer(pb.b);
            if (pa != null && pa.getOpenInventory() != null) {
                Inventory top = pa.getOpenInventory().getTopInventory();
                if (top != null && top.getHolder() instanceof GuiHolder gh && gh.type == GuiType.BATTLE) {
                    renderBattle(top, pa, pb.sa);
                }
            }
            if (pbp != null && pbp.getOpenInventory() != null) {
                Inventory top = pbp.getOpenInventory().getTopInventory();
                if (top != null && top.getHolder() instanceof GuiHolder gh && gh.type == GuiType.BATTLE) {
                    renderBattle(top, pbp, pb.sb);
                }
            }
        } catch (Throwable ignored) {}
    }

    /**
     * Handle faint outcomes. Returns true if the turn should stop (forced switch or battle end).
     */
    private boolean handlePvpFaintsAfterAction(PvpBattle pb) {
        if (pb == null) return true;
        // A (playerMon in sa) fainted?
        if (pb.sa.playerMon != null && pb.sa.playerMon.currentHp <= 0) {
            try { if (plugin.getEvolutionManager() != null) plugin.getEvolutionManager().onFaint(pb.a, pb.sa.playerMon); } catch (Throwable ignored) {}
            try { if (plugin.getBridgeSyncManager() != null && pb.sa.playerBattleCarrierId != null) plugin.getBridgeSyncManager().triggerCarrierAnimation(pb.sa.playerBattleCarrierId, "faint", 24L); } catch (Throwable ignored) {}
            pb.defeatedByB.add(pb.sa.playerMon);
            if (hasAnyUsablePartyMon(pb.a)) {
                pb.waitingForcedSwitch = true;
                pb.sa.awaitingForcedSwitch = true;
                pb.sa.processingTurn = false;
                pb.sb.processingTurn = false;
                Player pa = Bukkit.getPlayer(pb.a);
                if (pa != null) openBattleSwitchGui(pa, pb.sa);
                sendPvpMessage(pb, "§e" + (pa != null ? pa.getName() : "玩家") + " 的精灵昏厥了！请选择下一只精灵。");
                return true;
            } else {
                // B wins
                endPvpBattle(pb, pb.b, "§c你的队伍全部昏厥了！PVP结束。");
                return true;
            }
        }
        // B (wildMon in sa) fainted?
        if (pb.sa.wildMon != null && pb.sa.wildMon.currentHp <= 0) {
            try { if (plugin.getEvolutionManager() != null) plugin.getEvolutionManager().onFaint(pb.b, pb.sa.wildMon); } catch (Throwable ignored) {}
            try { if (plugin.getBridgeSyncManager() != null && pb.sa.wildBattleCarrierId != null) plugin.getBridgeSyncManager().triggerCarrierAnimation(pb.sa.wildBattleCarrierId, "faint", 24L); } catch (Throwable ignored) {}
            pb.defeatedByA.add(pb.sa.wildMon);
            if (hasAnyUsablePartyMon(pb.b)) {
                pb.waitingForcedSwitch = true;
                pb.sb.awaitingForcedSwitch = true;
                pb.sa.processingTurn = false;
                pb.sb.processingTurn = false;
                Player pbp = Bukkit.getPlayer(pb.b);
                if (pbp != null) openBattleSwitchGui(pbp, pb.sb);
                sendPvpMessage(pb, "§e" + (pbp != null ? pbp.getName() : "玩家") + " 的精灵昏厥了！请选择下一只精灵。");
                return true;
            } else {
                // A wins
                endPvpBattle(pb, pb.a, "§c对方队伍全部昏厥！PVP结束。");
                return true;
            }
        }
        return false;
    }

    private java.util.List<String> applyMoveDescribedPvp(PvpBattle pb, Player actor, boolean actorIsA, Move move) {
        java.util.List<String> out = new java.util.ArrayList<>();
        BattleSession s = pb.sa; // canonical
        PokemonInstance atk = actorIsA ? s.playerMon : s.wildMon;
        PokemonInstance def = actorIsA ? s.wildMon : s.playerMon;
        if (atk == null || def == null || move == null) {
            out.add("§7但是失败了！");
            return out;
        }
        Species atkS = dex.getSpecies(atk.effectiveSpeciesId());
        Species defS = dex.getSpecies(def.effectiveSpeciesId());
        LangManager lang = plugin.getLang();
        String atkName = (lang == null || atkS == null) ? (atkS == null ? "?" : atkS.name()) : lang.species(atkS.id(), atkS.name());
        String defName = (lang == null || defS == null) ? (defS == null ? "?" : defS.name()) : lang.species(defS.id(), defS.name());
        String mvName = (lang == null) ? move.name() : lang.move(move.id(), move.name());
        String actorName = (actor != null) ? actor.getName() : (actorIsA ? plugin.getLang().ui("common.player_a","玩家A") : plugin.getLang().ui("common.player_b","玩家B"));
        out.add("§f" + actorName + " 的 " + atkName + " 使用了 §b" + mvName + "§f！");

        // Reuse existing engine
        out.addAll(MoveEngine.execute(plugin, actor, s, actorIsA, move, atk, def, atkS, defS, atkName, defName));
        out.addAll(applyHeldItemTriggers(actor, s));
        return out;
    }

    private void endPvpBattle(PvpBattle pb, UUID winnerId, String endMsg) {
        if (pb == null) return;
        pb.sa.finished = true;
        pb.sb.finished = true;
        pb.processing = false;

        // Cleanup visuals
        try { cleanupBattleVisuals(pb.sa, false); } catch (Throwable ignored) {}
        try { cleanupBattleVisuals(pb.sb, false); } catch (Throwable ignored) {}

        // EXP settlement: winner only, no loot GUI, no pickup/EV.
        if (winnerId != null) {
            try { awardPvpExp(pb, winnerId); } catch (Throwable ignored) {}
        }

        // Close inventories and remove sessions
        try {
            Player pa = Bukkit.getPlayer(pb.a);
            Player pbp = Bukkit.getPlayer(pb.b);
            if (endMsg != null) {
                if (pa != null) pa.sendMessage(endMsg);
                if (pbp != null) pbp.sendMessage(endMsg);
            }
            if (pa != null) pa.closeInventory();
            if (pbp != null) pbp.closeInventory();
        } catch (Throwable ignored) {}

        removeSession(pb.a);
        removeSession(pb.b);

        pvpBattles.remove(pb.key);
        pvpKeyByPlayer.remove(pb.a);
        pvpKeyByPlayer.remove(pb.b);
    }

    private void awardPvpExp(PvpBattle pb, UUID winnerId) {
        if (pb == null || winnerId == null) return;
        UUID loserId = winnerId.equals(pb.a) ? pb.b : pb.a;
        BattleSession winnerSession = winnerId.equals(pb.a) ? pb.sa : pb.sb;

        java.util.List<PokemonInstance> defeated = winnerId.equals(pb.a) ? pb.defeatedByA : pb.defeatedByB;
        if (defeated == null) defeated = java.util.List.of();
        // Also include currently fainted active if battle ended directly
        if (winnerId.equals(pb.a) && pb.sa.wildMon != null && pb.sa.wildMon.currentHp <= 0 && !defeated.contains(pb.sa.wildMon)) defeated.add(pb.sa.wildMon);
        if (winnerId.equals(pb.b) && pb.sa.playerMon != null && pb.sa.playerMon.currentHp <= 0 && !defeated.contains(pb.sa.playerMon)) defeated.add(pb.sa.playerMon);

        PlayerProfile prof = storage.getProfile(winnerId);
        if (prof == null) return;

        // Participants: those who switched in at least once
        java.util.List<PokemonInstance> participants = new java.util.ArrayList<>();
        java.util.Set<UUID> partSet = winnerSession.playerParticipants;
        if (partSet != null) {
            for (PokemonInstance p : prof.party) {
                if (p != null && partSet.contains(p.uuid) && p.currentHp > 0) participants.add(p);
            }
        }
        if (participants.isEmpty() && winnerSession.playerMon != null && winnerSession.playerMon.currentHp > 0) participants.add(winnerSession.playerMon);
        int participantCount = Math.max(1, participants.size());

        long totalGained = 0;
        for (PokemonInstance d : defeated) {
            if (d == null) continue;
            Species defeatedS = dex.getSpecies(d.effectiveSpeciesId());
            if (defeatedS == null) continue;
            int baseYield = Math.max(1, defeatedS.baseExpYield());
            int lvl = Util.clamp(d.level, 1, 100);
            double raw = (baseYield * (double) lvl) / 7.0;
            double mult = plugin.getConfig().getDouble("exp.global-multiplier", 1.0);
            totalGained += Math.max(1, (long) Math.floor(raw * mult));
        }
        if (totalGained <= 0) totalGained = 1;

        LangManager lang = plugin.getLang();
        Player winner = Bukkit.getPlayer(winnerId);
        Player loser = Bukkit.getPlayer(loserId);
        String loserName = loser != null ? loser.getName() : "对手";
        if (winner != null) winner.sendMessage("§a你击败了 " + loserName + "，获得经验！");

        boolean anyDirty = false;
        for (PokemonInstance p : participants) {
            Species ps = dex.getSpecies(p.speciesId);
            if (ps == null) continue;
            int beforeLv = p.level;
            long per = Math.max(1, totalGained / participantCount);
            if ("lucky_egg".equalsIgnoreCase(p.heldItemId)) {
                per = Math.max(1, (long) Math.floor(per * 1.5));
            }
            p.totalExp = Math.max(p.totalExp, ExpCurve.totalExpAtLevel(ps.expGroup(), p.level));
            p.totalExp += per;
            int newLv = ExpCurve.levelForTotalExp(ps.expGroup(), p.totalExp);
            if (newLv > p.level) {
                p.level = newLv;
                p.addFriendship(2);
                p.currentHp = Math.min(p.maxHp(ps), p.currentHp);
                try {
                    plugin.getLearnsetManager().onLevelUp(winnerId, p, ps, beforeLv, newLv);
                } catch (Throwable ignored) {}
                try {
                    plugin.getEvolutionManager().notifyIfCanEvolve(winnerId, p);
                } catch (Throwable ignored) {}
            }
            anyDirty = true;
            String pName = (lang == null) ? ps.name() : lang.species(ps.id(), ps.name());
            if (winner != null) {
                if (p.level > beforeLv) winner.sendMessage("§a" + pName + " 升到了 §eLv." + p.level + "§a！");
                else winner.sendMessage("§a" + pName + " 获得了 §e" + per + "§a 经验。");
            }
        }
        if (anyDirty) storage.markDirty(winnerId);
    }

    private String normalizeBattleCategory(Move move) {
        if (move == null || move.category() == null) return "attack_status";
        String cat = move.category().toLowerCase(java.util.Locale.ROOT).trim();
        return switch (cat) {
            case "physical" -> "attack_physical";
            case "special" -> "attack_special";
            default -> "attack_status";
        };
    }

    private String normalizeMoveEffectId(Move move) {
        if (move == null || move.id() == null) return null;
        String id = move.id().toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_]", "");
        return id.isBlank() ? null : id;
    }

    private String actorAnimationTokenForMove(Move move) {
        String base = normalizeBattleCategory(move);
        String moveId = normalizeMoveEffectId(move);
        return moveId == null ? base : ("move:" + moveId + "|" + base);
    }

    private String defenderImpactTokenForMove(Move move) {
        String t = move == null || move.type() == null ? "normal" : move.type().toLowerCase(java.util.Locale.ROOT).trim();
        if (t.isBlank()) t = "normal";
        return "hit_" + t;
    }

    private List<String> applyMoveDescribed(Player player, BattleSession s, boolean playerActing, Move move) {
        List<String> out = new ArrayList<>();
        PokemonInstance atk = playerActing ? s.playerMon : s.wildMon;
        PokemonInstance def = playerActing ? s.wildMon : s.playerMon;
        if (atk == null || def == null || move == null) {
            out.add("§7但是失败了！");
            return out;
        }
        Species atkS = dex.getSpecies(atk.effectiveSpeciesId());
        Species defS = dex.getSpecies(def.effectiveSpeciesId());

        LangManager lang = plugin.getLang();
        String atkName = (lang == null || atkS == null) ? (atkS == null ? "?" : atkS.name()) : lang.species(atkS.id(), atkS.name());
        String defName = (lang == null || defS == null) ? (defS == null ? "?" : defS.name()) : lang.species(defS.id(), defS.name());
        String mvName = (lang == null) ? move.name() : lang.move(move.id(), move.name());

        s.statusLine = (playerActing ? "§a我方行动：" : "§c对方行动：") + atkName + " 使用了 §b" + mvName + "§f";
        out.add((playerActing ? "§f你的 " : "§c野生 ") + atkName + " §f使用了 §b" + mvName + "§f！");
        int attackerHpBefore = atk.currentHp;
        int defenderHpBefore = def.currentHp;
        try {
            if (plugin.getBridgeSyncManager() != null) {
                UUID actorId = playerActing ? s.playerBattleCarrierId : s.wildBattleCarrierId;
                if (actorId != null) plugin.getBridgeSyncManager().triggerCarrierAnimation(actorId, actorAnimationTokenForMove(move), 14L);
            }
        } catch (Throwable ignored) {}

        // Pressure: extra PP drain on the attacker
        if (def != null && AbilityEffects.hasPressure(def) && atk != null) {
            try {
                java.util.List<MoveSlot> slots = atk.effectiveMoves();
                if (slots != null) {
                    for (MoveSlot ms : slots) {
                        if (ms != null && ms.moveId != null && move != null && ms.moveId.equalsIgnoreCase(move.id())) {
                            if (ms.pp > 0) { ms.pp--; out.add(plugin.getLang().uiFmt("battle.log.ability.pressure_pp", "§6【Pressure】§e{def} exerts its pressure! PP was reduced by 1 more!", java.util.Map.of("def", defName))); }
                            break;
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }

        // Status moves (modern immunities)
        
        // Assault Vest: cannot use status moves
        if ("status".equalsIgnoreCase(move.category()) && atk != null && "assault_vest".equalsIgnoreCase(atk.heldItemId)) {
            out.add("§7" + atkName + " 因§f突击背心§7无法使出变化招式！");
            return out;
        }

        if ("status".equalsIgnoreCase(move.category()) && defS != null && isStatusImmuneModern(move.id(), defS)) {
            out.add("§7但是失败了！");
            return out;
        }

        out.addAll(MoveEngine.execute(plugin, player, s, playerActing, move, atk, def, atkS, defS, atkName, defName));
        try {
            if (plugin.getBridgeSyncManager() != null) {
                UUID attackerId = playerActing ? s.playerBattleCarrierId : s.wildBattleCarrierId;
                UUID defenderId = playerActing ? s.wildBattleCarrierId : s.playerBattleCarrierId;
                if (def.currentHp < defenderHpBefore && defenderId != null) {
                    plugin.getBridgeSyncManager().triggerCarrierAnimation(defenderId, defenderImpactTokenForMove(move), 10L);
                }
                if (atk.currentHp < attackerHpBefore && attackerId != null) {
                    plugin.getBridgeSyncManager().triggerCarrierAnimation(attackerId, "recoil", 10L);
                }
            }
        } catch (Throwable ignored) {}

        // Auto-trigger held items (berries) after this action
        out.addAll(applyHeldItemTriggers(player, s));
        return out;

    }


    /**
     * Auto-trigger simple held items (mainly berries) for both sides.
     * This is intentionally lightweight (Gen1-ish + common berries) and can be expanded later.
     */
    private java.util.List<String> applyHeldItemTriggers(Player player, BattleSession s) {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (s == null) return out;

        // Player side
        try {
            Species ps = dex.getSpecies(s.playerMon.effectiveSpeciesId());
            out.addAll(tryConsumeHeldBerry(player, s, s.playerMon, ps, true));
        } catch (Throwable ignored) {}
        // Wild side
        try {
            Species ws = dex.getSpecies(s.wildMon.effectiveSpeciesId());
            out.addAll(tryConsumeHeldBerry(player, s, s.wildMon, ws, false));
        } catch (Throwable ignored) {}
        return out;
    }

    /**
     * Safety net: if any exception happens during a turn (often from a new ability/move edge case),
     * we MUST reset processingTurn so the player doesn't get stuck on "请稍等" forever.
     */
    private void abortTurnSafely(Player player, BattleSession s, String stage, Throwable t) {
        try {
            plugin.getLogger().warning("Battle turn aborted at " + stage + ": " + (t == null ? "null" : t.toString()));
            if (t != null) t.printStackTrace();
        } catch (Throwable ignored) {}
        try {
            if (s != null) {
                s.processingTurn = false;
                s.statusLine = "§c回合结算发生错误，已重置。请重新选择招式。";
            }
            if (player != null) {
                player.sendMessage("§c[战斗] 回合结算发生错误（" + stage + "），已重置。请再选一次招式。§7(请把后台报错发给我)");
                if (s != null) {
                    renderOpenVanillaBattleInventory(player, s);
                }
            }
        } catch (Throwable ignored) {}
    }

    private java.util.List<String> tryConsumeHeldBerry(Player player, BattleSession s, PokemonInstance mon, Species monS, boolean isPlayer) {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (mon == null || monS == null) return out;
        if (mon.currentHp <= 0) return out;
        if (mon.heldItemId == null || mon.heldItemId.isBlank()) return out;

        String itemId = mon.heldItemId;
        String berry = null;
        if (itemId.endsWith("_berry")) berry = itemId.substring(0, itemId.length() - 5).toLowerCase(java.util.Locale.ROOT);
        if (berry == null) return out;

        LangManager lang = plugin.getLang();
        String monName = (lang == null) ? monS.name() : lang.species(monS.id(), monS.name());
        String berryName = (lang == null) ? berry : lang.item(itemId, berry);

        // 1) Status cure berries (including Lum)
        String st = mon.status == null ? "none" : mon.status.toLowerCase(java.util.Locale.ROOT);
        boolean hasStatus = !"none".equals(st);
        boolean hasConfusion = mon.confusionTurns > 0;

        boolean cured = false;
        if ("lum".equals(berry)) {
            if (hasStatus) {
                mon.status = "none";
                mon.toxicCounter = 0;
                cured = true;
            }
            if (hasConfusion) {
                mon.confusionTurns = 0;
                cured = true;
            }
        } else if ("cheri".equals(berry)) {
            if (st.equals("paralyze") || st.equals("paralysis")) { mon.status = "none"; cured = true; }
        } else if ("chesto".equals(berry)) {
            if (st.equals("sleep")) { mon.status = "none"; cured = true; }
        } else if ("pecha".equals(berry)) {
            if (st.equals("poison") || st.equals("toxic")) { mon.status = "none"; mon.toxicCounter = 0; cured = true; }
        } else if ("rawst".equals(berry)) {
            if (st.equals("burn")) { mon.status = "none"; cured = true; }
        } else if ("aspear".equals(berry)) {
            if (st.equals("freeze") || st.equals("frozen")) { mon.status = "none"; cured = true; }
        } else if ("persim".equals(berry)) {
            if (hasConfusion) { mon.confusionTurns = 0; cured = true; }
        }

        if (cured) {
            mon.heldItemId = null;
            out.add("§e" + monName + " 食用了 §f" + berryName + "§e！状态已恢复。");
            if (isPlayer && player != null) storage.markDirty(player.getUniqueId());
            return out;
        }

        // 2) HP healing berries (Oran / Sitrus)
        int max = Math.max(1, mon.maxHp(monS));
        if (mon.currentHp > 0 && mon.currentHp <= (max / 2)) {
            int heal = 0;
            if ("oran".equals(berry)) heal = 10;
            else if ("sitrus".equals(berry)) heal = Math.max(1, max / 4);

            if (heal > 0) {
                int before = mon.currentHp;
                mon.currentHp = Math.min(max, mon.currentHp + heal);
                mon.heldItemId = null;
                out.add("§a" + monName + " 食用了 §f" + berryName + "§a！回复了 §c" + (mon.currentHp - before) + "§a 点体力。");
                if (isPlayer && player != null) storage.markDirty(player.getUniqueId());
                return out;
            }
        }

        // 3) Leppa Berry: restore PP when a move hits 0 PP
        if ("leppa".equals(berry)) {
            if (mon.effectiveMoves() != null) {
                for (MoveSlot ms : mon.effectiveMoves()) {
                    if (ms == null) continue;
                    if (ms.pp <= 0) {
                        int before = ms.pp;
                        ms.pp = Math.min(ms.maxPp, ms.pp + 10);
                        mon.heldItemId = null;
                        String mv = (lang == null) ? ms.moveId : lang.move(ms.moveId, ms.moveId);
                        out.add("§d" + monName + " 食用了 §f" + berryName + "§d！§7(" + mv + " PP " + before + "->" + ms.pp + ")");
                        if (isPlayer && player != null) storage.markDirty(player.getUniqueId());
                        return out;
                    }
                }
            }
        }

        return out;
    }


private List<String> applyEndOfTurn(Player player, BattleSession s) {
    if (AbilityEffects.contextSession() != s) {
        return AbilityEffects.withContext(s, () -> applyEndOfTurn(player, s));
    }
    List<String> out = new ArrayList<>();
    Species myS = dex.getSpecies(s.playerMon.effectiveSpeciesId());
    Species wildS = dex.getSpecies(s.wildMon.effectiveSpeciesId());
    LangManager lang = plugin.getLang();
    String myName = (lang == null || myS == null) ? (myS == null ? "我方精灵" : myS.name()) : lang.species(myS.id(), myS.name());
    String wildName = (lang == null || wildS == null) ? (wildS == null ? "野生精灵" : wildS.name()) : lang.species(wildS.id(), wildS.name());

    // Decrement screens
    if (s.playerReflectTurns > 0) s.playerReflectTurns--;
    if (s.playerLightScreenTurns > 0) s.playerLightScreenTurns--;
    if (s.wildReflectTurns > 0) s.wildReflectTurns--;
    if (s.wildLightScreenTurns > 0) s.wildLightScreenTurns--;
    if (s.playerSafeguardTurns > 0) s.playerSafeguardTurns--;
    if (s.wildSafeguardTurns > 0) s.wildSafeguardTurns--;
    if (s.playerTailwindTurns > 0) s.playerTailwindTurns--;
    if (s.wildTailwindTurns > 0) s.wildTailwindTurns--;
    if (s.playerAuroraVeilTurns > 0) s.playerAuroraVeilTurns--;
    if (s.wildAuroraVeilTurns > 0) s.wildAuroraVeilTurns--;
    if (s.playerLuckyChantTurns > 0) s.playerLuckyChantTurns--;
    if (s.wildLuckyChantTurns > 0) s.wildLuckyChantTurns--;
    if (s.trickRoomTurns > 0) s.trickRoomTurns--;
    if (s.magicRoomTurns > 0) s.magicRoomTurns--;
    if (s.wonderRoomTurns > 0) s.wonderRoomTurns--;
    if (s.gravityTurns > 0) s.gravityTurns--;
    if (s.terrainTurns > 0) {
        s.terrainTurns--;
        if (s.terrainTurns <= 0) s.terrain = null;
    }
    if (s.fairyLockTurns > 0) s.fairyLockTurns--;
    s.ionDelugeActive = false;

    // Decrement misc volatiles (Disable/Mist)
    java.util.function.Consumer<PokemonInstance> decVol = (mon) -> {
        if (mon.disabledTurns > 0) {
            mon.disabledTurns--;
            if (mon.disabledTurns <= 0) mon.disabledMoveId = null;
        }
        if (mon.mistTurnsRemaining > 0) mon.mistTurnsRemaining--;
        if (mon.protectTurnsRemaining > 0) mon.protectTurnsRemaining--;
        if (mon.healBlockTurns > 0) mon.healBlockTurns--;
        if (mon.nightmareTurns > 0) mon.nightmareTurns--;
        if (mon.tauntTurns > 0) mon.tauntTurns--;
        if (mon.tormentTurns > 0) mon.tormentTurns--;
        if (mon.encoreTurns > 0) {
            mon.encoreTurns--;
            if (mon.encoreTurns <= 0) mon.encoreMoveId = null;
        }
        mon.roostSuppressFlying = false;
        if (mon.magnetRiseTurns > 0) mon.magnetRiseTurns--;
        if (mon.telekinesisTurns > 0) mon.telekinesisTurns--;
        if (mon.embargoTurns > 0) mon.embargoTurns--;
        if (mon.laserFocusTurns > 0) mon.laserFocusTurns--;
        if (mon.throatChopTurns > 0) mon.throatChopTurns--;
        mon.powdered = false;
        mon.electrifiedThisTurn = false;
    };
    decVol.accept(s.playerMon);
    decVol.accept(s.wildMon);

    // Status damage helper
    java.util.function.BiConsumer<PokemonInstance, Species> applyStatusDmg = (mon, monS) -> {
        if (monS == null) return;

        // Magic Guard: prevents indirect damage (poison/burn/toxic etc.)
        if (AbilityEffects.has(mon, "magicguard")) return;

        int max = mon.maxHp(monS);
        String status = mon.status == null ? "none" : mon.status.toLowerCase();
        if ("poison".equals(status)) {
            // Poison Heal: heal instead of taking poison damage.
            if (AbilityEffects.has(mon, "poisonheal")) {
                int heal = Math.max(1, max / 8);
                int before = mon.currentHp;
                mon.currentHp = Math.min(max, mon.currentHp + heal);
                if (mon.currentHp > before) out.add(plugin.getLang().uiFmt(
                        "battle.log.ability.poison_heal",
                        "§a【{ability}】{mon} 回复了 §c{amt}§a 点体力。",
                        java.util.Map.of(
                                "ability", plugin.getLang().abilityName("poisonheal", "毒疗"),
                                "mon", (mon == s.playerMon ? myName : wildName),
                                "amt", String.valueOf(mon.currentHp - before)
                        )
                ));
                return;
            }
            int dmg = Math.max(1, max / 8);
            mon.currentHp = Math.max(0, mon.currentHp - dmg);
            out.add(plugin.getLang().uiFmt(
                    "battle.log.status.damage.poison",
                    "§a{mon} 因{status}受到了 §c{dmg}§a 点伤害。",
                    java.util.Map.of(
                            "mon", (mon == s.playerMon ? myName : wildName),
                            "status", plugin.getLang().statusName("poison", "中毒"),
                            "dmg", String.valueOf(dmg)
                    )
            ));
        } else if ("burn".equals(status)) {
            int dmg = Math.max(1, max / 16);
            mon.currentHp = Math.max(0, mon.currentHp - dmg);
            out.add(plugin.getLang().uiFmt(
                    "battle.log.status.damage.burn",
                    "§c{mon} 因{status}受到了 §c{dmg}§c 点伤害。",
                    java.util.Map.of(
                            "mon", (mon == s.playerMon ? myName : wildName),
                            "status", plugin.getLang().statusName("burn", "灼伤"),
                            "dmg", String.valueOf(dmg)
                    )
            ));
        } else if ("toxic".equals(status)) {
            // Poison Heal also works for toxic in modern rules; we follow that.
            if (AbilityEffects.has(mon, "poisonheal")) {
                int heal = Math.max(1, max / 8);
                int before = mon.currentHp;
                mon.currentHp = Math.min(max, mon.currentHp + heal);
                if (mon.currentHp > before) out.add(plugin.getLang().uiFmt(
                        "battle.log.ability.poison_heal",
                        "§a【{ability}】{mon} 回复了 §c{amt}§a 点体力。",
                        java.util.Map.of(
                                "ability", plugin.getLang().abilityName("poisonheal", "毒疗"),
                                "mon", (mon == s.playerMon ? myName : wildName),
                                "amt", String.valueOf(mon.currentHp - before)
                        )
                ));
                mon.toxicCounter = 0;
                return;
            }
            if (mon.toxicCounter <= 0) mon.toxicCounter = 1;
            int dmg = Math.max(1, (max / 16) * mon.toxicCounter);
            mon.currentHp = Math.max(0, mon.currentHp - dmg);
            out.add(plugin.getLang().uiFmt(
                    "battle.log.status.damage.toxic",
                    "§2{mon} 因{status}受到了 §c{dmg}§2 点伤害。",
                    java.util.Map.of(
                            "mon", (mon == s.playerMon ? myName : wildName),
                            "status", plugin.getLang().statusName("toxic", "剧毒"),
                            "dmg", String.valueOf(dmg)
                    )
            ));
            mon.toxicCounter++;
        }
    };

    // Apply status damage
    applyStatusDmg.accept(s.playerMon, myS);
    applyStatusDmg.accept(s.wildMon, wildS);

    // Shed Skin: chance to cure major status at end of turn (simplified 1/3).
    java.util.function.BiConsumer<PokemonInstance, String> shedSkin = (mon, monName) -> {
        if (mon == null) return;
        String st = mon.status == null ? "none" : mon.status.toLowerCase(java.util.Locale.ROOT);
        if (AbilityEffects.has(mon, "shedskin") && !"none".equals(st)) {
            if (Util.RND.nextDouble() < (1.0 / 3.0)) {
                mon.status = "none";
                mon.toxicCounter = 0;
                out.add(plugin.getLang().uiFmt("battle.log.ability.shed_skin", "§a【Shed Skin】{mon} shed its status condition!", java.util.Map.of("mon", monName)));
            }
        }
    };
    shedSkin.accept(s.playerMon, myName);
    shedSkin.accept(s.wildMon, wildName);

    // Healer: chance to cure major status at end of turn (simplified 30%).
    java.util.function.BiConsumer<PokemonInstance, String> healer = (mon, monName) -> {
        if (mon == null) return;
        String st = mon.status == null ? "none" : mon.status.toLowerCase(java.util.Locale.ROOT);
        if (AbilityEffects.has(mon, "healer") && !"none".equals(st)) {
            if (Util.RND.nextDouble() < 0.30) {
                mon.status = "none";
                mon.toxicCounter = 0;
                out.add(plugin.getLang().uiFmt("battle.log.ability.healer", "§a【Healer】{mon}'s status condition was cured!", java.util.Map.of("mon", monName)));
            }
        }
    };
    healer.accept(s.playerMon, myName);
    healer.accept(s.wildMon, wildName);

    // Leech Seed
    // Magic Guard prevents Leech Seed damage.
    if (s.playerMon.currentHp > 0 && s.playerMon.leechSeeded && myS != null && !AbilityEffects.has(s.playerMon, "magicguard")) {
        int dmg = Math.max(1, s.playerMon.maxHp(myS) / 8);
        s.playerMon.currentHp = Math.max(0, s.playerMon.currentHp - dmg);
        PokemonInstance seedHealer = s.playerMon.leechSeedByPlayer ? s.playerMon : s.wildMon;
        Species seedHealerS = s.playerMon.leechSeedByPlayer ? myS : wildS;
        if (seedHealerS != null && seedHealer.currentHp > 0) {
            seedHealer.currentHp = Math.min(seedHealer.maxHp(seedHealerS), seedHealer.currentHp + dmg);
        }
        out.add("§a" + myName + " 被寄生种子吸取了 §c" + dmg + "§a 点体力！");
    }
    if (s.wildMon.currentHp > 0 && s.wildMon.leechSeeded && wildS != null && !AbilityEffects.has(s.wildMon, "magicguard")) {
        int dmg = Math.max(1, s.wildMon.maxHp(wildS) / 8);
        s.wildMon.currentHp = Math.max(0, s.wildMon.currentHp - dmg);
        PokemonInstance seedHealer = s.wildMon.leechSeedByPlayer ? s.playerMon : s.wildMon;
        Species seedHealerS = s.wildMon.leechSeedByPlayer ? myS : wildS;
        if (seedHealerS != null && seedHealer.currentHp > 0) {
            seedHealer.currentHp = Math.min(seedHealer.maxHp(seedHealerS), seedHealer.currentHp + dmg);
        }
        out.add("§a" + wildName + " 被寄生种子吸取了 §c" + dmg + "§a 点体力！");
    }

    // Aqua Ring / Ingrain / Nightmare / Wish
    java.util.function.BiConsumer<PokemonInstance, Species> applyRecoveryVolatiles = (mon, monS) -> {
        if (mon == null || monS == null || mon.currentHp <= 0) return;
        String monName = (mon == s.playerMon ? myName : wildName);
        int max = mon.maxHp(monS);
        if (mon.aquaRingActive && mon.healBlockTurns <= 0) {
            int heal = Math.max(1, max / 16);
            int before = mon.currentHp;
            mon.currentHp = Math.min(max, mon.currentHp + heal);
            if (mon.currentHp > before) out.add("§b" + monName + " 受到了水流环的回复！");
        }
        if (mon.ingrainActive && mon.healBlockTurns <= 0) {
            int heal = Math.max(1, max / 16);
            int before = mon.currentHp;
            mon.currentHp = Math.min(max, mon.currentHp + heal);
            if (mon.currentHp > before) out.add("§a" + monName + " 扎根吸收了体力！");
        }
        String st = mon.status == null ? "none" : mon.status.toLowerCase(java.util.Locale.ROOT);
        if (mon.nightmareTurns > 0 && "sleep".equals(st)) {
            int dmg = Math.max(1, max / 4);
            mon.currentHp = Math.max(0, mon.currentHp - dmg);
            out.add("§8" + monName + " 被恶梦折磨了！(-" + dmg + ")");
        }
        PokemonInstance foe = (mon == s.playerMon ? s.wildMon : s.playerMon);
        if (foe != null && foe.currentHp > 0 && AbilityEffects.has(foe, "baddreams") && "sleep".equals(st)) {
            int dmg = Math.max(1, max / 8);
            mon.currentHp = Math.max(0, mon.currentHp - dmg);
            out.add(plugin.getLang().uiFmt("battle.log.ability.bad_dreams", "§6【Bad Dreams】§8{mon} is tormented! (-{dmg})", java.util.Map.of("mon", monName, "dmg", String.valueOf(dmg))));
        }
    };
    applyRecoveryVolatiles.accept(s.playerMon, myS);
    applyRecoveryVolatiles.accept(s.wildMon, wildS);

    if (s.terrainTurns > 0 && "grassy".equalsIgnoreCase(s.terrain)) {
        java.util.function.BiConsumer<PokemonInstance, Species> grassyHeal = (mon, monS) -> {
            if (mon == null || monS == null || mon.currentHp <= 0) return;
            if (!isGroundedForHazards(s, mon, monS)) return;
            int max = mon.maxHp(monS);
            int heal = Math.max(1, max / 16);
            int before = mon.currentHp;
            mon.currentHp = Math.min(max, mon.currentHp + heal);
            if (mon.currentHp > before) out.add("§a" + (mon == s.playerMon ? myName : wildName) + " 受到青草场地的回复！");
        };
        grassyHeal.accept(s.playerMon, myS);
        grassyHeal.accept(s.wildMon, wildS);
    }

    java.util.function.BiConsumer<PokemonInstance, String> applyOctolock = (mon, monName) -> {
        if (mon == null || mon.currentHp <= 0) return;
        if (mon.trappedTurnsRemaining >= 90) {
            int bd = mon.stageDef; int bs = mon.stageSpd;
            mon.applyStage("def", -1);
            mon.applyStage("spd", -1);
            if (mon.stageDef != bd || mon.stageSpd != bs) out.add("§7" + monName + " 被章鱼桶的束缚削弱了防御！");
        }
    };
    applyOctolock.accept(s.playerMon, myName);
    applyOctolock.accept(s.wildMon, wildName);

    if (s.playerWishTurns > 0) {
        s.playerWishTurns--;
        if (s.playerWishTurns <= 0 && s.playerMon != null && myS != null && s.playerMon.currentHp > 0) {
            int before = s.playerMon.currentHp;
            s.playerMon.currentHp = Math.min(s.playerMon.maxHp(myS), s.playerMon.currentHp + Math.max(1, s.playerWishHeal));
            if (s.playerMon.currentHp > before) out.add("§d" + myName + " 的祈愿实现了！");
            s.playerWishHeal = 0;
        }
    }
    if (s.wildWishTurns > 0) {
        s.wildWishTurns--;
        if (s.wildWishTurns <= 0 && s.wildMon != null && wildS != null && s.wildMon.currentHp > 0) {
            int before = s.wildMon.currentHp;
            s.wildMon.currentHp = Math.min(s.wildMon.maxHp(wildS), s.wildMon.currentHp + Math.max(1, s.wildWishHeal));
            if (s.wildMon.currentHp > before) out.add("§d" + wildName + " 的祈愿实现了！");
            s.wildWishHeal = 0;
        }
    }

    java.util.function.BiConsumer<PokemonInstance, String> applyPerishSong = (mon, monName) -> {
        if (mon == null || mon.currentHp <= 0) return;
        if (mon.perishSongTurns > 0) {
            mon.perishSongTurns--;
            if (mon.perishSongTurns <= 0) {
                mon.currentHp = 0;
                out.add("§5" + monName + " 因灭亡之歌倒下了！");
            } else {
                out.add("§5" + monName + " 的灭亡倒计时变成了 " + mon.perishSongTurns + "！");
            }
        }
    };
    applyPerishSong.accept(s.playerMon, myName);
    applyPerishSong.accept(s.wildMon, wildName);

    java.util.function.BiConsumer<PokemonInstance, Species> applySaltCure = (mon, monS) -> {
        if (mon == null || monS == null || mon.currentHp <= 0 || mon.saltCureTurns <= 0) return;
        int max = mon.maxHp(monS);
        boolean boosted = false;
        try {
            java.util.List<String> tps = monS.types();
            boosted = tps != null && tps.stream().anyMatch(t -> "water".equalsIgnoreCase(t) || "steel".equalsIgnoreCase(t));
        } catch (Throwable ignored) {}
        int dmg = Math.max(1, max / (boosted ? 4 : 8));
        mon.currentHp = Math.max(0, mon.currentHp - dmg);
        mon.saltCureTurns--;
        String monName = (mon == s.playerMon ? myName : wildName);
        out.add("§6" + monName + " 受到了盐腌的持续伤害！(-" + dmg + ")");
    };
    applySaltCure.accept(s.playerMon, myS);
    applySaltCure.accept(s.wildMon, wildS);

    java.util.function.BiConsumer<PokemonInstance, String> speedBoost = (mon, monName) -> {
        if (mon == null || mon.currentHp <= 0) return;
        if (AbilityEffects.has(mon, "speedboost")) {
            int before = mon.stageSpe;
            mon.applyStage("spe", 1);
            if (mon.stageSpe != before) out.add(plugin.getLang().uiFmt("battle.log.ability.speed_boost", "§6【Speed Boost】§e{mon}'s Speed rose!", java.util.Map.of("mon", monName)));
        }
    };
    speedBoost.accept(s.playerMon, myName);
    speedBoost.accept(s.wildMon, wildName);

    java.util.function.BiConsumer<PokemonInstance, String> moody = (mon, monName) -> {
        if (mon == null || mon.currentHp <= 0 || !AbilityEffects.has(mon, "moody")) return;
        java.util.List<String> up = new java.util.ArrayList<>(java.util.List.of("atk","def","spa","spd","spe","accuracy","evasion"));
        String upStat = up.get(Util.RND.nextInt(up.size()));
        mon.applyStage(upStat, 2);
        up.remove(upStat);
        String downStat = up.get(Util.RND.nextInt(up.size()));
        mon.applyStage(downStat, -1);
        out.add(plugin.getLang().uiFmt("battle.log.ability.moody", "§6【Moody】§e{mon}'s stats were altered!", java.util.Map.of("mon", monName)));
    };
    moody.accept(s.playerMon, myName);
    moody.accept(s.wildMon, wildName);

    java.util.function.Consumer<PokemonInstance> slowStartTick = mon -> {
        if (mon == null || mon.currentHp <= 0) return;
        if (AbilityEffects.has(mon, "slowstart") && mon.slowStartTurns > 0) mon.slowStartTurns--;
    };
    slowStartTick.accept(s.playerMon);
    slowStartTick.accept(s.wildMon);

    java.util.function.BiConsumer<PokemonInstance, String> cudChew = (mon, monName) -> {
        if (mon == null || mon.currentHp <= 0) return;
        if (!AbilityEffects.has(mon, "cudchew")) return;
        if (mon.cudChewTurns > 0) mon.cudChewTurns--;
        if (mon.cudChewTurns <= 0 && mon.cudChewBerryId != null && (mon.heldItemId == null || mon.heldItemId.isBlank())) {
            mon.heldItemId = mon.cudChewBerryId;
            mon.cudChewBerryId = null;
            out.add(plugin.getLang().uiFmt("battle.log.ability.cud_chew", "§6【Cud Chew】§e{mon} chewed its Berry again!", java.util.Map.of("mon", monName)));
        }
    };
    cudChew.accept(s.playerMon, myName);
    cudChew.accept(s.wildMon, wildName);

    java.util.function.BiConsumer<PokemonInstance, String> hungerSwitch = (mon, monName) -> {
        if (mon == null || mon.currentHp <= 0 || !AbilityEffects.has(mon, "hungerswitch")) return;
        String eff = mon.effectiveSpeciesId() == null ? "" : mon.effectiveSpeciesId().toLowerCase(java.util.Locale.ROOT);
        mon.overrideSpeciesId = eff.equals("morpekohangry") ? null : "morpekohangry";
        out.add(plugin.getLang().uiFmt("battle.log.ability.hunger_switch", "§6【Hunger Switch】§e{mon} changed its form!", java.util.Map.of("mon", monName)));
    };
    hungerSwitch.accept(s.playerMon, myName);
    hungerSwitch.accept(s.wildMon, wildName);

    AbilityEffects.refreshBattleForm(s.playerMon, myS, myName, out);
    AbilityEffects.refreshBattleForm(s.wildMon, wildS, wildName, out);

    // Weather end-of-turn (sand/hail residuals, hydration etc.)
    WeatherSystem.applyEndOfTurn(plugin, s, myS, wildS, myName, wildName, out);

    // Held-item end-of-turn residuals (e.g. Leftovers/Black Sludge, Orbs, Sticky Barb)
    if (s.playerMon != null && myS != null) {
        boolean berriesBlocked = AbilityEffects.hasUnnerveLike(s.wildMon);
        out.addAll(HeldItemEffects.endTurnItemResidual(plugin, s.playerMon, myS, myName, berriesBlocked));
    }
    if (s.wildMon != null && wildS != null) {
        boolean berriesBlocked = AbilityEffects.hasUnnerveLike(s.playerMon);
        out.addAll(HeldItemEffects.endTurnItemResidual(plugin, s.wildMon, wildS, wildName, berriesBlocked));
    }

    // Also check held items after end-of-turn damage (poison/burn/leech seed)
    out.addAll(applyHeldItemTriggers(player, s));


    return out;
}

    private void win(Player player, BattleSession s) {
        try { if (plugin.getBridgeSyncManager() != null && s.playerBattleCarrierId != null) plugin.getBridgeSyncManager().triggerCarrierAnimation(s.playerBattleCarrierId, "faint", 24L); } catch (Throwable ignored) {}
        s.finished = true;

        // Award EXP/EV to all Pokémon that participated (switched in at least once)
        PlayerProfile prof = storage.getProfile(player.getUniqueId());
        Species defeatedS = dex.getSpecies(s.wildMon.effectiveSpeciesId());

        java.util.List<PokemonInstance> participants = new java.util.ArrayList<>();
        if (prof != null) {
            for (PokemonInstance p : prof.party) {
                if (p != null && s.playerParticipants.contains(p.uuid)) {
                    // Fainted Pokémon don't receive exp in the mainline games
                    if (p.currentHp > 0) participants.add(p);
                }
            }
        }
        if (participants.isEmpty() && s.playerMon != null) participants.add(s.playerMon);
        int participantCount = Math.max(1, participants.size());

        // Compute base exp once
        long totalGained = 0;
        int lvl = Util.clamp(s.wildMon.level, 1, 100);
        if (defeatedS != null) {
            int baseYield = Math.max(1, defeatedS.baseExpYield());
            double raw = (baseYield * (double) lvl) / 7.0;
            double mult = plugin.getConfig().getDouble("exp.global-multiplier", 1.0);
            totalGained = Math.max(1, (long) Math.floor(raw * mult));
        }

        LangManager lang = plugin.getLang();
        String wildName = (lang == null || defeatedS == null) ? (defeatedS == null ? "?" : defeatedS.name()) : lang.species(defeatedS.id(), defeatedS.name());
        sendBattleLine(s, plugin.getLang().uiFmt("battle.log.defeat_wild", "§a你击败了野生精灵：§f{wild}§a！", java.util.Map.of("wild", wildName)));

        boolean anyDirty = false;

        // --- Ability: Pickup (捡拾) ---
        // After a battle, non-fainted party Pokémon with Pickup and no held item may find an item.
        if (prof != null) {
            double chance = plugin.getConfig().getDouble("abilities.pickup-chance", 0.10);
            ItemRegistry reg = plugin.getItemRegistry();
            for (PokemonInstance pMon : prof.party) {
                if (pMon == null) continue;
                if (pMon.currentHp <= 0) continue;
                if (pMon.heldItemId != null && !pMon.heldItemId.isBlank()) continue;
                if (!AbilityEffects.has(pMon, "pickup")) continue;
                if (Util.RND.nextDouble() >= chance) continue;

                String foundId = rollPickupItemId();
                if (foundId == null) continue;
                ItemDef def = (reg == null) ? null : reg.get(foundId);
                if (def == null) continue;

                pMon.heldItemId = foundId;
                anyDirty = true;
                Species ps = dex.getSpecies(pMon.speciesId);
                String pName = (lang == null || ps == null) ? (ps == null ? "?" : ps.name()) : lang.species(ps.id(), ps.name());
                String itemName = (lang == null) ? foundId : lang.item(def.id, def.id);
                sendBattleLine(s, plugin.getLang().uiFmt("battle.log.pickup", "§6【捡拾】§e{mon} 捡到了 §f{item}§e！", java.util.Map.of("mon", pName, "item", itemName)));
            }
        }

        // --- Wild loot flow (only for wild battles) ---
        // Remove the defeated wild entity (and its visuals) so it cannot be re-farmed.
        // Then open a loot GUI: click-to-take items into inventory, leftovers drop on close.
        // Default drop location: player's current position (safe fallback).
        org.bukkit.Location dropLoc = player.getLocation().clone();
        try {
            org.bukkit.entity.Entity ent = (s.wildEntityId != null) ? org.bukkit.Bukkit.getEntity(s.wildEntityId) : null;
            if (ent != null) {
                dropLoc = ent.getLocation().clone();
                if (ent instanceof org.bukkit.entity.Wolf w) {
                    try { new VisualCarrierManager(plugin).detach(w); } catch (Throwable ignored) {}
                }
                try { ent.remove(); } catch (Throwable ignored) {}
            }
        } catch (Throwable t) {
            // keep fallback
        }

        // Roll drops from Cobblemon species_raw drops table (if present)
        java.util.List<org.bukkit.inventory.ItemStack> rolledDrops = java.util.List.of();
        try {
            if (plugin.getDropTables() != null && defeatedS != null) {
                rolledDrops = rollDrops(defeatedS.id());
            }
        } catch (Throwable ignored) {}

        // anyDirty already tracks party changes above (Pickup / EVs / experience etc.)
        for (PokemonInstance p : participants) {
            Species ps = dex.getSpecies(p.speciesId);
            if (ps == null || defeatedS == null) continue;
            String pName = (lang == null) ? ps.name() : lang.species(ps.id(), ps.name());

            // EV gain (split)
            int evHp0 = p.evHp, evAtk0 = p.evAtk, evDef0 = p.evDef, evSpa0 = p.evSpa, evSpd0 = p.evSpd, evSpe0 = p.evSpe;
            boolean evChanged = applyEvGainSplit(p, defeatedS, participantCount);
            if (evChanged) anyDirty = true;
            int dHp = p.evHp - evHp0;
            int dAtk = p.evAtk - evAtk0;
            int dDef = p.evDef - evDef0;
            int dSpa = p.evSpa - evSpa0;
            int dSpd = p.evSpd - evSpd0;
            int dSpe = p.evSpe - evSpe0;

            // EXP
            int beforeLv = p.level;
            long per = (totalGained <= 0) ? 0 : Math.max(1, totalGained / participantCount);
            if ("lucky_egg".equalsIgnoreCase(p.heldItemId)) {
                per = Math.max(1, (long) Math.floor(per * 1.5));
            }
            p.totalExp = Math.max(p.totalExp, ExpCurve.totalExpAtLevel(ps.expGroup(), p.level));
            p.totalExp += per;

            int newLv = ExpCurve.levelForTotalExp(ps.expGroup(), p.totalExp);
            if (newLv > p.level) {
                p.level = newLv;
                p.addFriendship(2);
                p.currentHp = Math.min(p.maxHp(ps), p.currentHp);
                anyDirty = true;
                try {
                    PokeDemoPlugin.INSTANCE.getLearnsetManager().onLevelUp(player.getUniqueId(), p, ps, beforeLv, newLv);
                } catch (Throwable ignored) {}
                try {
                    PokeDemoPlugin.INSTANCE.getEvolutionManager().notifyIfCanEvolve(player.getUniqueId(), p);
                } catch (Throwable ignored) {}
            }

            if (per > 0) {
                sendBattleLine(s, plugin.getLang().uiFmt("battle.log.gain_exp", "§f{mon} §a获得了 §e{exp}§a 经验值。", java.util.Map.of("mon", pName, "exp", String.valueOf(per))));
            }
            if (dHp + dAtk + dDef + dSpa + dSpd + dSpe > 0) {
                StringBuilder sb = new StringBuilder("§a努力值：");
                if (dHp > 0) sb.append(" §fHP+").append(dHp);
                if (dAtk > 0) sb.append(" §fAtk+").append(dAtk);
                if (dDef > 0) sb.append(" §fDef+").append(dDef);
                if (dSpa > 0) sb.append(" §fSpA+").append(dSpa);
                if (dSpd > 0) sb.append(" §fSpD+").append(dSpd);
                if (dSpe > 0) sb.append(" §fSpe+").append(dSpe);
                sendBattleLine(s, sb.toString());
            }
            if (p.level > beforeLv) {
                sendBattleLine(s, plugin.getLang().uiFmt("battle.log.level_up", "§b升级！§f{mon} §a到达 §eLv.{lv}§a！", java.util.Map.of("mon", pName, "lv", String.valueOf(p.level))));
            }
        }

        if (anyDirty) storage.markDirty(player.getUniqueId());

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.0f);
        removeSession(player.getUniqueId());

        // Open loot GUI AFTER battle GUI is closed by callers.
        final java.util.List<org.bukkit.inventory.ItemStack> finalDrops = rolledDrops;
        // Variables captured by lambda must be effectively final.
        final org.bukkit.Location finalDropLoc = (dropLoc != null) ? dropLoc.clone() : player.getLocation().clone();
        if (finalDrops != null && !finalDrops.isEmpty()) {
            org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player == null || !player.isOnline()) return;
                openWildLootGui(player, finalDropLoc, finalDrops);
            }, 1L);
        }
    }

    // Pickup item table (simple + safe). Extend later if needed.
    // We only return items that exist in ItemRegistry.
    private static final String[] PICKUP_POOL_COMMON = new String[] {
            // Medicines / PP restores
            "potion","super_potion","hyper_potion","full_heal",
            "ether","max_ether","elixir",
            // Balls
            "poke_ball","great_ball","ultra_ball"
    };

    private String rollPickupItemId() {
        // Keep it deterministic & safe: common pool only for now.
        // If you want stricter Gen rules later, we can add level-based tiers.
        if (PICKUP_POOL_COMMON.length == 0) return null;
        return PICKUP_POOL_COMMON[Util.RND.nextInt(PICKUP_POOL_COMMON.length)];
    }

    private java.util.List<org.bukkit.inventory.ItemStack> rollDrops(String speciesId) {
        DropTableManager.DropTable t = plugin.getDropTables().get(speciesId);
        if (t == null || t.entries() == null || t.entries().isEmpty()) return java.util.List.of();

        java.util.Random rnd = Util.RND;
        java.util.List<DropTableManager.DropEntry> hits = new java.util.ArrayList<>();
        for (DropTableManager.DropEntry e : t.entries()) {
            if (e == null) continue;
            double pct = e.percentage();
            if (pct <= 0) continue;
            if (rnd.nextDouble() * 100.0 <= pct) {
                int q = e.rollQuantity(rnd);
                if (q > 0) hits.add(new DropTableManager.DropEntry(e.itemKey(), e.percentage(), q, q));
            }
        }

        if (hits.isEmpty()) return java.util.List.of();
        int amount = Math.max(1, t.amount());
        if (hits.size() > amount) {
            java.util.Collections.shuffle(hits, rnd);
            hits = hits.subList(0, amount);
        }

        java.util.List<org.bukkit.inventory.ItemStack> out = new java.util.ArrayList<>();
        for (DropTableManager.DropEntry e : hits) {
            String key = e.itemKey();
            int qty = Math.max(1, e.minQty());
            org.bukkit.inventory.ItemStack stack = mapDropToItemStack(key, qty);
            if (stack == null) continue;
            // split large stacks
            while (stack.getAmount() > 64) {
                org.bukkit.inventory.ItemStack part = stack.clone();
                part.setAmount(64);
                out.add(part);
                stack.setAmount(stack.getAmount() - 64);
            }
            out.add(stack);
        }
        return out;
    }

    private org.bukkit.inventory.ItemStack mapDropToItemStack(String itemKey, int qty) {
        if (itemKey == null || itemKey.isBlank()) return null;
        String k = itemKey.trim();
        String id = k;
        if (k.contains(":")) {
            String[] parts = k.split(":", 2);
            String ns = parts[0];
            String path = parts.length > 1 ? parts[1] : "";
            if ("cobblemon".equalsIgnoreCase(ns)) {
                id = path;
            } else if ("minecraft".equalsIgnoreCase(ns)) {
                try {
                    org.bukkit.Material m = org.bukkit.Material.matchMaterial(path);
                    if (m != null && m != org.bukkit.Material.AIR) {
                        return new org.bukkit.inventory.ItemStack(m, qty);
                    }
                } catch (Throwable ignored) {}
                return null;
            } else {
                // Unknown namespace: try as plain id
                id = path;
            }
        }

        try {
            ItemDef def = plugin.getItemRegistry() != null ? plugin.getItemRegistry().get(id.toLowerCase(java.util.Locale.ROOT)) : null;
            if (def != null) {
                return plugin.getItems().createItem(def, plugin.getLang(), qty);
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private void openWildLootGui(org.bukkit.entity.Player player, org.bukkit.Location dropLoc, java.util.List<org.bukkit.inventory.ItemStack> drops) {
        if (player == null || drops == null || drops.isEmpty()) return;

        GuiHolder holder = new GuiHolder(GuiType.WILD_LOOT, player.getUniqueId());
        holder.lootDropLocation = (dropLoc != null) ? dropLoc.clone() : player.getLocation().clone();

        LangManager lang = plugin.getLang();
        Inventory inv = Bukkit.createInventory(holder, 27, UtilGui.title(lang.ui("gui.battle.loot_title", "战利品")));

        int slot = 0;
        for (org.bukkit.inventory.ItemStack it : drops) {
            if (it == null || it.getType() == org.bukkit.Material.AIR) continue;
            if (slot >= inv.getSize()) break;
            inv.setItem(slot++, it);
        }

        // If we overflowed 27 slots, drop the rest immediately.
        if (slot >= inv.getSize()) {
            for (int i = inv.getSize(); i < drops.size(); i++) {
                org.bukkit.inventory.ItemStack it = drops.get(i);
                if (it == null || it.getType() == org.bukkit.Material.AIR) continue;
                try { holder.lootDropLocation.getWorld().dropItemNaturally(holder.lootDropLocation, it); } catch (Throwable ignored) {}
            }
        }

        player.openInventory(inv);
        player.sendMessage("§6获得战利品：§7点击物品拾取，关闭界面后未拾取的会掉落到地上。");
    }

    // --- EV gain helpers (battle rewards) ---
    private boolean applyEvGain(PokemonInstance winner, Species defeatedSpecies) {
        boolean enabled = plugin.getConfig().getBoolean("ev.enabled", true);
        if (!enabled) return false;
        if (winner == null || defeatedSpecies == null) return false;

        double mult = plugin.getConfig().getDouble("ev.multiplier", 1.0);
        int perStatCap = plugin.getConfig().getInt("ev.per-stat-cap", 252);
        int totalCap = plugin.getConfig().getInt("ev.total-cap", 510);

        int yHp = Math.max(0, defeatedSpecies.ev("hp"));
        int yAtk = Math.max(0, defeatedSpecies.ev("atk"));
        int yDef = Math.max(0, defeatedSpecies.ev("def"));
        int ySpa = Math.max(0, defeatedSpecies.ev("spa"));
        int ySpd = Math.max(0, defeatedSpecies.ev("spd"));
        int ySpe = Math.max(0, defeatedSpecies.ev("spe"));

        yHp = (int) Math.floor(yHp * mult);
        yAtk = (int) Math.floor(yAtk * mult);
        yDef = (int) Math.floor(yDef * mult);
        ySpa = (int) Math.floor(ySpa * mult);
        ySpd = (int) Math.floor(ySpd * mult);
        ySpe = (int) Math.floor(ySpe * mult);

        // Held item EV modifiers (Macho Brace / Power items)
        int[] mod = HeldItemEffects.modifyEvYields(winner, yHp, yAtk, yDef, ySpa, ySpd, ySpe);
        yHp = mod[0]; yAtk = mod[1]; yDef = mod[2]; ySpa = mod[3]; ySpd = mod[4]; ySpe = mod[5];

        if (yHp + yAtk + yDef + ySpa + ySpd + ySpe <= 0) return false;

        int curTotal = winner.evHp + winner.evAtk + winner.evDef + winner.evSpa + winner.evSpd + winner.evSpe;
        if (curTotal >= totalCap) return false;

        winner.evHp = clampEvAdd(winner.evHp, yHp, perStatCap);
        winner.evAtk = clampEvAdd(winner.evAtk, yAtk, perStatCap);
        winner.evDef = clampEvAdd(winner.evDef, yDef, perStatCap);
        winner.evSpa = clampEvAdd(winner.evSpa, ySpa, perStatCap);
        winner.evSpd = clampEvAdd(winner.evSpd, ySpd, perStatCap);
        winner.evSpe = clampEvAdd(winner.evSpe, ySpe, perStatCap);

        int newTotal = winner.evHp + winner.evAtk + winner.evDef + winner.evSpa + winner.evSpd + winner.evSpe;
        if (newTotal > totalCap) {
            int over = newTotal - totalCap;
            trimEv(winner, over);
        }

        return true;
    }

    /** Like applyEvGain, but split the defeated mon's EV yields among N participants. */
    private boolean applyEvGainSplit(PokemonInstance winner, Species defeatedSpecies, int participantCount) {
        int n = Math.max(1, participantCount);
        boolean enabled = plugin.getConfig().getBoolean("ev.enabled", true);
        if (!enabled) return false;
        if (winner == null || defeatedSpecies == null) return false;

        double mult = plugin.getConfig().getDouble("ev.multiplier", 1.0);
        int perStatCap = plugin.getConfig().getInt("ev.per-stat-cap", 252);
        int totalCap = plugin.getConfig().getInt("ev.total-cap", 510);

        int yHp = Math.max(0, defeatedSpecies.ev("hp"));
        int yAtk = Math.max(0, defeatedSpecies.ev("atk"));
        int yDef = Math.max(0, defeatedSpecies.ev("def"));
        int ySpa = Math.max(0, defeatedSpecies.ev("spa"));
        int ySpd = Math.max(0, defeatedSpecies.ev("spd"));
        int ySpe = Math.max(0, defeatedSpecies.ev("spe"));

        // Split yields first (floor), then apply server multiplier.
        yHp = (int) Math.floor((yHp / (double) n) * mult);
        yAtk = (int) Math.floor((yAtk / (double) n) * mult);
        yDef = (int) Math.floor((yDef / (double) n) * mult);
        ySpa = (int) Math.floor((ySpa / (double) n) * mult);
        ySpd = (int) Math.floor((ySpd / (double) n) * mult);
        ySpe = (int) Math.floor((ySpe / (double) n) * mult);

        int[] mod = HeldItemEffects.modifyEvYields(winner, yHp, yAtk, yDef, ySpa, ySpd, ySpe);
        yHp = mod[0]; yAtk = mod[1]; yDef = mod[2]; ySpa = mod[3]; ySpd = mod[4]; ySpe = mod[5];

        int beforeTotal = winner.evHp + winner.evAtk + winner.evDef + winner.evSpa + winner.evSpd + winner.evSpe;
        int beforeHp = winner.evHp, beforeAtk = winner.evAtk, beforeDef = winner.evDef, beforeSpa = winner.evSpa, beforeSpd = winner.evSpd, beforeSpe = winner.evSpe;

        winner.evHp = Math.min(perStatCap, winner.evHp + yHp);
        winner.evAtk = Math.min(perStatCap, winner.evAtk + yAtk);
        winner.evDef = Math.min(perStatCap, winner.evDef + yDef);
        winner.evSpa = Math.min(perStatCap, winner.evSpa + ySpa);
        winner.evSpd = Math.min(perStatCap, winner.evSpd + ySpd);
        winner.evSpe = Math.min(perStatCap, winner.evSpe + ySpe);

        int afterTotal = winner.evHp + winner.evAtk + winner.evDef + winner.evSpa + winner.evSpd + winner.evSpe;
        int over = afterTotal - totalCap;
        if (over > 0) {
            // Reduce the last-added EVs first (spe->spd->spa->def->atk->hp)
            int[] vals = new int[]{winner.evHp, winner.evAtk, winner.evDef, winner.evSpa, winner.evSpd, winner.evSpe};
            int[] base = new int[]{beforeHp, beforeAtk, beforeDef, beforeSpa, beforeSpd, beforeSpe};
            for (int idx = 5; idx >= 0 && over > 0; idx--) {
                int gained = Math.max(0, vals[idx] - base[idx]);
                int cut = Math.min(gained, over);
                vals[idx] -= cut;
                over -= cut;
            }
            winner.evHp = vals[0]; winner.evAtk = vals[1]; winner.evDef = vals[2];
            winner.evSpa = vals[3]; winner.evSpd = vals[4]; winner.evSpe = vals[5];
        }

        int finalTotal = winner.evHp + winner.evAtk + winner.evDef + winner.evSpa + winner.evSpd + winner.evSpe;
        return beforeTotal != finalTotal;
    }

    private int clampEvAdd(int cur, int add, int cap) {
        if (add <= 0) return cur;
        return Math.min(cap, cur + add);
    }

    private void trimEv(PokemonInstance p, int over) {
        // Trim in order: spe, spa, atk, def, spd, hp
        int[] vals = new int[]{p.evSpe, p.evSpa, p.evAtk, p.evDef, p.evSpd, p.evHp};
        for (int i = 0; i < vals.length && over > 0; i++) {
            int take = Math.min(vals[i], over);
            vals[i] -= take;
            over -= take;
        }
        p.evSpe = vals[0];
        p.evSpa = vals[1];
        p.evAtk = vals[2];
        p.evDef = vals[3];
        p.evSpd = vals[4];
        p.evHp = vals[5];
    }

    private void lose(Player player, BattleSession s) {
        // If player has other non-fainted Pokémon, require the player to choose a replacement.
        PlayerProfile prof = storage.getProfile(player.getUniqueId());
        boolean hasAlive = false;
        for (PokemonInstance p : prof.party) {
            if (p != null && p.currentHp > 0) { hasAlive = true; break; }
        }
        if (hasAlive) {
            try { if (plugin.getEvolutionManager() != null) plugin.getEvolutionManager().onFaint(player.getUniqueId(), s.playerMon); } catch (Throwable ignored) {}
            try { if (plugin.getBridgeSyncManager() != null && s.playerBattleCarrierId != null) plugin.getBridgeSyncManager().triggerCarrierAnimation(s.playerBattleCarrierId, "faint", 24L); } catch (Throwable ignored) {}
            s.awaitingForcedSwitch = true;
            s.processingTurn = false;
            s.statusLine = "§e你的精灵昏厥了！请选择下一只精灵。";
            sendBattleLine(s, plugin.getLang().ui("battle.log.forced_switch", "§e你的精灵昏厥了！请在列表中选择要派出的精灵。"));
            try { openBattleSwitchGui(player, s); } catch (Throwable ignored) {}
            return;
        }
        s.finished = true;
        sendBattleLine(s, plugin.getLang().ui("battle.log.party_fainted_end", "§c你的队伍全部昏厥了！战斗结束。"));
        removeSession(player.getUniqueId());
    }

    public void endBattleIfTarget(UUID playerId, UUID targetEntityId) {
        BattleSession s = sessions.get(playerId);
        if (s != null && s.wildEntityId.equals(targetEntityId)) {
            removeSession(playerId);
        }
    }

    /**
     * End a battle session.
     *
     * Historically this project used multiple endBattle() signatures in different branches.
     * To keep older call sites compiling (and to make future merges painless), we keep both
     * overloads here.
     */
    public void endBattle(UUID playerId, String msg) {
        endBattle(playerId, msg, false);
    }

    /**
     * @param closeInventory whether to close the player's currently open inventory (best-effort)
     */
    public void endBattle(@org.jetbrains.annotations.NotNull UUID playerId, String msg, boolean closeInventory) {
        // If this is a PvP session, end the entire PvP battle (both sides).
        try {
            BattleSession s = sessions.get(playerId);
            if (s != null && s.pvp && s.pvpKey != null) {
                PvpBattle pb = pvpBattles.get(s.pvpKey);
                if (pb != null) {
                    endPvpBattle(pb, null, msg);
                    return;
                }
            }
        } catch (Throwable ignored) {}

        removeSession(playerId);
        Player p = Bukkit.getPlayer(playerId);
        if (p != null) {
            if (msg != null) p.sendMessage(msg);
            if (closeInventory) {
                try { p.closeInventory(); } catch (Exception ignored) {}
            }
        }
    }

    public void handleBridgeBattleAction(Player player, String action) {
        if (player == null || action == null || action.isBlank()) return;
        BattleSession s = sessions.get(player.getUniqueId());
        if (s == null || s.finished) return;
        String a = action.trim().toLowerCase(java.util.Locale.ROOT);
        if (a.startsWith("move:")) {
            try {
                int idx = Integer.parseInt(a.substring("move:".length()));
                GuiHolder holder = new GuiHolder(GuiType.BATTLE, player.getUniqueId());
                holder.battleSession = s;
                handleBattleClick(player, holder, 18 + idx, null);
            } catch (Throwable ignored) {}
        } else if (a.startsWith("switch:")) {
            try {
                int idx = Integer.parseInt(a.substring("switch:".length()));
                GuiHolder holder = new GuiHolder(GuiType.BATTLE_SWITCH, player.getUniqueId());
                holder.battleSession = s;
                handleBattleSwitchClick(player, holder, idx, null);
            } catch (Throwable ignored) {}
        } else if (a.equals("run")) {
            GuiHolder holder = new GuiHolder(GuiType.BATTLE, player.getUniqueId());
            holder.battleSession = s;
            handleBattleClick(player, holder, 24, null);
        } else if (a.equals("bag") || a.equals("catch")) {
            GuiHolder holder = new GuiHolder(GuiType.BATTLE, player.getUniqueId());
            holder.battleSession = s;
            handleBattleClick(player, holder, 23, null);
        }
        try {
            if (plugin.getBridgeSyncManager() != null) {
                plugin.getBridgeSyncManager().syncBattleState(player);
                Bukkit.getScheduler().runTaskLater(plugin, () -> plugin.getBridgeSyncManager().syncBattleState(player), 1L);
            }
        } catch (Throwable ignored) {}
    }

    public void onBattleGuiClosed(UUID playerId) {
        BattleSession s = sessions.get(playerId);
        if (s == null || s.finished) return;
        // If we are intentionally transitioning to another GUI (switch/bag/ball),
        // do not auto-reopen the main battle GUI.
        if (System.currentTimeMillis() < s.suppressReopenUntilMs) {
            s.guiOpen = false;
            s.lastGuiCloseAt = System.currentTimeMillis();
            return;
        }
        // Player closed the battle GUI (usually via ESC).
        // We do NOT force re-open (players may want to temporarily exit),
        // but we provide a quick clickable "return to battle" button.
        s.guiOpen = false;
        s.lastGuiCloseAt = System.currentTimeMillis();

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            BattleSession cur = sessions.get(playerId);
            if (cur == null || cur.finished) return;
            Player p = Bukkit.getPlayer(playerId);
            if (p == null) return;
            try {
                // Green clickable chat button: runs /battle to reopen GUI.
                p.sendMessage(Component.text("§a[点击返回战斗] §7(/battle)")
                        .clickEvent(ClickEvent.runCommand("/battle")));
            } catch (Exception ignored) {
            }
        });
    }

    private void suppressBattleGuiReopen(BattleSession s, long ms) {
        s.suppressReopenUntilMs = Math.max(s.suppressReopenUntilMs, System.currentTimeMillis() + ms);
    }

    public boolean reopenBattleGui(Player player) {
        BattleSession s = sessions.get(player.getUniqueId());
        if (s == null || s.finished) return false;
        openBattleGui(player, s);
        return true;
    }

    /**
     * When true, the battle GUI should be considered "locked" and must not be closed.
     * This prevents state desync when the battle is processing a turn/capture.
     */
    public boolean isGuiLockActive(UUID playerId) {
        BattleSession s = sessions.get(playerId);
        if (s == null || s.finished) return false;
        return s.processingTurn;
    }

    private void tryCatch(Player player, BattleSession s) {
        // Find and consume a ball from inventory
        ItemStack ball = null;
        int ballSlot = -1;
        String ballType = null;
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack it = player.getInventory().getItem(i);
            String bt = items.getBallType(it);
            if (bt != null && it != null && it.getAmount() > 0) {
                ball = it;
                ballSlot = i;
                ballType = bt;
                break;
            }
        }
        if (ball == null || ballType == null) {
            player.sendMessage("§c你没有精灵球！请先获取一个。\n§7管理员：/pokedemo giveball poke 16");
            return;
        }

        // Consume 1 ball
        ball.setAmount(ball.getAmount() - 1);
        if (ball.getAmount() <= 0) player.getInventory().setItem(ballSlot, null);

        Species wildS = dex.getSpecies(s.wildMon.effectiveSpeciesId());
        if (wildS == null) {
            player.sendMessage("§c无法捕捉：野生精灵数据缺失。");
            return;
        }

        double statusMul = statusCaptureBonus(s.wildMon.status);
        double ballMul = ballCaptureMul(ballType);

        double hpFactor = (3.0 * s.wildMon.maxHp(wildS) - 2.0 * s.wildMon.currentHp) / (3.0 * s.wildMon.maxHp(wildS));
        hpFactor = Util.clamp01(hpFactor);

        // Lightweight Gen1-style catch approximation (we can refine to exact RBY later)
        double base = (wildS.catchRate() / 255.0);
        double chance = Util.clamp01(base * ballMul * statusMul * (0.2 + 0.8 * hpFactor));

        if (Util.RND.nextDouble() < chance) {
            // remove entity
            var ent = Bukkit.getEntity(s.wildEntityId);
            if (ent != null) ent.remove();

            // Create a persisted copy to avoid leaking battle-only transient state
            PokemonInstance caught = s.wildMon.deepCopyPersisted();
            caught.ballId = (ballType == null || ballType.isBlank()) ? "poke_ball" : ballType;
            caught.originalTrainer = player.getUniqueId();
            caught.originalTrainerName = player.getName();
            PlayerProfile prof = storage.getProfile(player.getUniqueId());
            prof.depositToPartyOrPc(caught);
            try {
                if (prof.dexCaught != null && caught.speciesId != null) {
                    prof.dexCaught.add(caught.speciesId.toLowerCase(java.util.Locale.ROOT));
                }
            } catch (Exception ignored) {}
            // Automatically sanitize newly obtained pokemon's moves so players don't need to run
            // /pokedemo cleanseMoves every time.
            storage.cleanseMoves(player.getUniqueId(), dex);
            storage.markDirty(player.getUniqueId());
            LangManager lang = plugin.getLang();
            String display = (lang != null) ? lang.species(wildS.id(), wildS.name()) : wildS.name();
            sendBattleLine(s, "§a成功捕捉：§f" + display + " §a(Lv." + s.wildMon.level + ")！");

            // Global announcement for legendaries
            if (caught.isLegendary) {
                String tmpl = plugin.getConfig().getString("legendary.messages.caught", "§d%player% 捕捉了 §e【%name%】§d！");
                String msg = tmpl.replace("%player%", player.getName()).replace("%name%", display);
                Bukkit.broadcastMessage(msg);
                try {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
                    }
                } catch (Throwable ignored) {}
            }
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
            s.finished = true;
            removeSession(player.getUniqueId());
        } else {
            sendBattleLine(s, "§e精灵挣脱了精灵球！");
            player.playSound(player.getLocation(), Sound.ENTITY_WOLF_GROWL, 1f, 1.0f);
        }
    }

    private static boolean isPowderMove(String moveId) {
        return moveId != null && (moveId.equals("sleep_powder") || moveId.equals("spore") || moveId.equals("stun_spore") || moveId.equals("poison_powder"));
    }

    private static boolean isStatusImmuneModern(String moveId, Species targetSpecies) {
        if (moveId == null || targetSpecies == null) return false;
        java.util.List<String> t = targetSpecies.types();
        if (t == null || t.isEmpty()) return false;

        if (moveId.equals("leech_seed") && t.stream().anyMatch(x -> "grass".equalsIgnoreCase(x))) return true;

        if ((moveId.equals("toxic") || moveId.equals("poison_powder")) &&
                t.stream().anyMatch(x -> "poison".equalsIgnoreCase(x) || "steel".equalsIgnoreCase(x))) return true;

        if (moveId.equals("will_o_wisp") && t.stream().anyMatch(x -> "fire".equalsIgnoreCase(x))) return true;

        if (moveId.equals("thunder_wave") && (t.stream().anyMatch(x -> "ground".equalsIgnoreCase(x)) || t.stream().anyMatch(x -> "electric".equalsIgnoreCase(x)))) return true;

        if (isPowderMove(moveId) && t.stream().anyMatch(x -> "grass".equalsIgnoreCase(x))) return true;

        return false;
    }



    private boolean hasTypeNow(PokemonInstance mon, Species sp, String type) {
        if (type == null || sp == null || mon == null) return false;
        java.util.List<String> ts = (mon.overrideType1 != null && !mon.overrideType1.isBlank())
                ? java.util.List.of(mon.overrideType1, mon.overrideType2 == null ? "" : mon.overrideType2)
                : sp.types();
        if (mon.roostSuppressFlying || mon.groundedBySmackDown) {
            ts = new java.util.ArrayList<>(ts);
            ts.removeIf(t -> "flying".equalsIgnoreCase(t));
        }
        for (String t : ts) if (type.equalsIgnoreCase(t)) return true;
        return false;
    }

    private boolean isGroundedForHazards(BattleSession s, PokemonInstance mon, Species sp) {
        if (mon == null || sp == null) return true;
        if (s != null && s.gravityTurns > 0) return true;
        if (mon.groundedBySmackDown) return true;
        if (mon.magnetRiseTurns > 0 || mon.telekinesisTurns > 0) return false;
        if (hasTypeNow(mon, sp, "flying")) return false;
        if (mon.abilityId != null && mon.abilityId.equalsIgnoreCase("levitate") && !mon.abilitySuppressed) return false;
        if (mon.heldItemId != null && mon.heldItemId.equalsIgnoreCase("air_balloon")) return false;
        return true;
    }

    private List<String> applyEntryHazards(BattleSession s, boolean playerSide, PokemonInstance mon, Species sp) {
        List<String> out = new ArrayList<>();
        if (s == null || mon == null || sp == null || mon.currentHp <= 0) return out;
        boolean grounded = isGroundedForHazards(s, mon, sp);
        boolean stealthRock = playerSide ? s.playerStealthRock : s.wildStealthRock;
        int spikes = playerSide ? s.playerSpikesLayers : s.wildSpikesLayers;
        int tspikes = playerSide ? s.playerToxicSpikesLayers : s.wildToxicSpikesLayers;
        boolean stickyWeb = playerSide ? s.playerStickyWeb : s.wildStickyWeb;

        if (stealthRock) {
            double eff = TypeChart.effectiveness("rock", sp.types());
            int dmg = Math.max(1, (int) Math.floor(mon.maxHp(sp) * 0.125 * eff));
            mon.currentHp = Math.max(0, mon.currentHp - dmg);
            out.add("§7隐形岩扎进了 " + mon.displayName() + "！");
        }
        if (grounded && spikes > 0) {
            double pct = spikes >= 3 ? 0.25 : (spikes == 2 ? (1.0/6.0) : 0.125);
            int dmg = Math.max(1, (int) Math.floor(mon.maxHp(sp) * pct));
            mon.currentHp = Math.max(0, mon.currentHp - dmg);
            out.add("§7撒菱伤到了 " + mon.displayName() + "！");
        }
        if (grounded && tspikes > 0) {
            boolean isPoison = hasTypeNow(mon, sp, "poison");
            boolean isSteel = hasTypeNow(mon, sp, "steel");
            if (isPoison) {
                if (playerSide) s.playerToxicSpikesLayers = 0; else s.wildToxicSpikesLayers = 0;
                out.add("§7毒菱被吸收了！");
            } else if (!isSteel && (mon.status == null || mon.status.equalsIgnoreCase("none"))) {
                mon.status = (tspikes >= 2) ? "toxic" : "poison";
                if ("toxic".equals(mon.status)) mon.toxicCounter = 1;
                out.add("§7" + mon.displayName() + " 中毒了！");
            }
        }
        if (grounded && stickyWeb) {
            mon.stageSpe = Math.max(-6, mon.stageSpe - 1);
            out.add("§7黏黏网降低了 " + mon.displayName() + " 的速度！");
        }
        // Healing Wish on switch-in (simple single-battle version)
        boolean healingWish = playerSide ? s.playerHealingWishPending : s.wildHealingWishPending;
        if (healingWish && mon.currentHp > 0) {
            int before = mon.currentHp;
            mon.currentHp = mon.maxHp(sp);
            mon.status = "none";
            mon.sleepTurns = 0;
            mon.toxicCounter = 0;
            if (playerSide) s.playerHealingWishPending = false; else s.wildHealingWishPending = false;
            if (mon.currentHp > before || true) out.add("§d治愈之愿实现了，" + mon.displayName() + " 恢复了状态与体力！");
        }
        return out;
    }

    private double statusCaptureBonus(String status) {
        if (status == null) return 1.0;
        return switch (status.toLowerCase()) {
            case "slp", "sleep" -> 2.0;
            case "frz", "freeze", "frozen" -> 2.0;
            case "par", "paralysis" -> 1.5;
            case "brn", "burn" -> 1.5;
            case "psn", "poison" -> 1.5;
            default -> 1.0;
        };
    }
    private double ballCaptureMul(String itemId) {
        if (itemId == null) return 1.0;
        String id = itemId.toLowerCase();
        return switch (id) {
            case "master_ball", "masterball" -> 999.0;
            case "ultra_ball", "ultraball" -> 2.0;
            case "great_ball", "greatball" -> 1.5;
            case "poke_ball", "pokeball" -> 1.0;
            default -> 1.0;
        };
    }




    private String statToCn(String stat) {
        if (stat == null) return "能力";
        return switch (stat.toLowerCase()) {
            case "atk" -> "攻击";
            case "def" -> "防御";
            case "spa" -> "特攻";
            case "spd" -> "特防";
            case "spe" -> "速度";
            case "accuracy" -> "命中率";
            case "evasion" -> "闪避率";
            case "special" -> "特殊";
            default -> stat;
        };
    }

}