package win.pokedemo;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.FluidCollisionMode;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;

import static org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;

public class SummonManager {
    private final PokeDemoPlugin plugin;
    private final Dex dex;
    private final Storage storage;

    // per-player summon state (supports multiple active pokemon: up to 6 party slots)
    private final Map<UUID, State> states = new HashMap<>();

    private final VisualCarrierManager visuals;
    private final Map<UUID, Long> sendoutCooldowns = new HashMap<>();
    private static final long SENDOUT_COOLDOWN_MS = 700L;

    public SummonManager(PokeDemoPlugin plugin, Dex dex, Storage storage) {
        this.plugin = plugin;
        this.dex = dex;
        this.storage = storage;
        this.visuals = new VisualCarrierManager(plugin);
    }

    /** Cleanup orphan carrier displays on server start (covers previous crash/restart). */
    public void cleanupOrphansOnEnable() {
        try {
            int removed = visuals.cleanupOrphanVisuals();
            if (removed > 0) {
                plugin.getLogger().info("startup cleanup: removed orphan carrier displays=" + removed);
            }

            int removedCarriers = cleanupLeftoverSummonedCarriers();
            if (removedCarriers > 0) {
                plugin.getLogger().warning("startup cleanup: removed leftover summoned carriers=" + removedCarriers);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("startup cleanup failed: " + e.getMessage());
        }
    }

    /**
     * Remove leftover "carrier" wolves that belong to players (KEY_OWNER present) but should never persist across restarts.
     * Wild spawned pokemon (KEY_WILD==1) are NOT removed.
     */
    public int cleanupLeftoverSummonedCarriers() {
        int removed = 0;
        try {
            for (org.bukkit.World w : plugin.getServer().getWorlds()) {
                for (Entity e : w.getEntities()) {
                    if (!(e instanceof Wolf wolf)) continue;
                    var pdc = wolf.getPersistentDataContainer();
                    if (!pdc.has(plugin.KEY_OWNER, PersistentDataType.STRING)) continue;
                    Byte wild = pdc.get(plugin.KEY_WILD, PersistentDataType.BYTE);
                    if (wild != null && wild == (byte) 1) continue;
                    try {
                        destroyCarrier(wolf);
                        removed++;
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}
        return removed;
    }

    /**
     * Cleanup all summoned entities & orphan visuals on plugin disable.
     *
     * We aggressively remove: all active summons tracked in-memory for online players,
     * and any leftover persistent ItemDisplay/TextDisplay created by VisualCarrierManager.
     */
    public void shutdown() {
        // Recall for online players (fast path)
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            try {
                recallAll(p, null);
            } catch (Exception ignored) {}
        }

        // Remove orphan visuals (covers crash/restart leftovers)
        try {
            int removed = visuals.cleanupOrphanVisuals();
            if (removed > 0) {
                plugin.getLogger().info("cleanup: removed orphan carrier displays=" + removed);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("cleanup: orphan carrier cleanup failed: " + e.getMessage());
        }

        // Remove any player-owned carriers that might persist (server stop saving entities)
        try {
            int removedCarriers = cleanupLeftoverSummonedCarriers();
            if (removedCarriers > 0) {
                plugin.getLogger().warning("cleanup: removed leftover summoned carriers=" + removedCarriers);
            }
        } catch (Throwable ignored) {}

        states.clear();
    }

    public static class State {
        public int cursorIndex = 0;        // next index to try summon (0-5)
        // Active summons per party slot (0-5)
        public final Map<Integer, UUID> activeEntityBySlot = new HashMap<>();
        public final Map<Integer, UUID> activePokemonBySlot = new HashMap<>();
    }

    public State getState(UUID playerId) {
        return states.computeIfAbsent(playerId, k -> new State());
    }

    public boolean hasAnyActive(Player player) {
        State st = getState(player.getUniqueId());
        return !st.activeEntityBySlot.isEmpty();
    }

    public void clearState(UUID playerId) {
        states.remove(playerId);
    }

    /** Recall a specific party slot (0-5) if active. */
    public void recallSlotIfActive(Player player, int partySlot, String reasonMessage) {
        partySlot = Util.clamp(partySlot, 0, 5);
        State st = getState(player.getUniqueId());
        UUID entId = st.activeEntityBySlot.get(partySlot);
        if (entId == null) return;

        var ent = player.getWorld().getEntity(entId);
        if (ent instanceof Wolf wolf) {
            visuals.detach(wolf);
            UUID puuid = st.activePokemonBySlot.get(partySlot);
            syncHpFromEntity(player.getUniqueId(), puuid, wolf);
            wolf.remove();
        } else if (ent != null) {
            ent.remove();
        }

        st.activeEntityBySlot.remove(partySlot);
        st.activePokemonBySlot.remove(partySlot);

        if (reasonMessage != null && !reasonMessage.isBlank()) {
            player.sendMessage(reasonMessage);
        }
    }

    /** Recall all active summons for a player. */
    public void recallAll(Player player, String reasonMessage) {
        State st = getState(player.getUniqueId());
        if (st.activeEntityBySlot.isEmpty()) return;

        // Copy keys to avoid concurrent modification.
        List<Integer> slots = new ArrayList<>(st.activeEntityBySlot.keySet());
        for (int slot : slots) {
            recallSlotIfActive(player, slot, null);
        }

        if (reasonMessage != null && !reasonMessage.isBlank()) {
            player.sendMessage(reasonMessage);
        }
    }

    public void onPlayerQuit(UUID playerId) {
        // try remove entity from any world safely by scanning loaded worlds is expensive; we rely on listener to remove on quit with player world reference.
        states.remove(playerId);
    }

    /**
     * Main action (legacy): if any active -> recall all; else summon next available (hp>0) from party in order starting at cursorIndex.
     */
    public void toggleSendOut(Player player) {
        State st = getState(player.getUniqueId());

        // recall all
        if (!st.activeEntityBySlot.isEmpty()) {
            recallAll(player, "§a已回收精灵。\n§7(已回收你当前召唤的全部精灵)");
            st.cursorIndex = (st.cursorIndex + 1) % 6;
            return;
        }

        PlayerProfile prof = storage.getProfile(player.getUniqueId());
        if (prof.party.isEmpty()) {
            player.sendMessage("§c你还没有精灵。");
            return;
        }

        // find next summonable pokemon
        int start = Util.clamp(st.cursorIndex, 0, 5);
        int chosen = -1;
        final PokemonInstance[] chosenP = {null};
        final Species[] chosenS = {null};

        for (int i = 0; i < 6; i++) {
            int idx = (start + i) % 6;
            if (prof.party.size() <= idx) continue;
            PokemonInstance p = prof.party.get(idx);
            Species s = dex.getSpecies(p.speciesId);
            if (s == null) continue;
            if (p.currentHp <= 0) continue; // fainted
            chosen = idx;
            chosenP[0] = p;
            chosenS[0] = s;
            break;
        }

        if (chosenP[0] == null) {
            player.sendMessage("§c队伍里没有可用精灵（都已昏厥）。");
            return;
        }

        // summon chosen slot
        sendOutSelected(player, chosen, chosenP[0], chosenS[0]);
    }

    
/**
 * 指定队伍位(0-5)召唤/回收。
 * 规则：
 * - 若当前已召唤且正是该队伍位的精灵 -> 回收
 * - 若当前已召唤但不是该位 -> 先回收当前，再召唤该位
 * - 若未召唤 -> 直接召唤该位
 */
public void toggleSendOutAt(Player player, int partySlot) {
    toggleSendOutAt(player, partySlot, null, null);
}

public void toggleSendOutAt(Player player, int partySlot, Location desiredTarget, UUID targetEntityUuid) {
        if (player == null) return;
        try {
            if (plugin.battles() != null && plugin.battles().isInBattle(player.getUniqueId())) {
                return;
            }
        } catch (Throwable ignored) {}
    partySlot = Util.clamp(partySlot, 0, 5);
    long now = System.currentTimeMillis();
    long last = sendoutCooldowns.getOrDefault(player.getUniqueId(), 0L);
    if (now - last < SENDOUT_COOLDOWN_MS) {
        return;
    }
    sendoutCooldowns.put(player.getUniqueId(), now);
    State st = getState(player.getUniqueId());
    PlayerProfile prof = storage.getProfile(player.getUniqueId());

    if (prof.party.isEmpty() || prof.party.size() <= partySlot) {
        player.sendMessage("§c该队伍位没有精灵。");
        return;
    }

    PokemonInstance target = prof.party.get(partySlot);
    Species targetSpecies = dex.getSpecies(target.speciesId);
    if (targetSpecies == null) {
        player.sendMessage("§c未知精灵种类：" + target.speciesId);
        return;
    }

    // 如果该队伍位已召唤：再次触发即回收（只影响这一只，不影响其他已召唤的）
    UUID activeP = st.activePokemonBySlot.get(partySlot);
    if (activeP != null && activeP.equals(target.uuid)) {
        recallSlotIfActive(player, partySlot, "§a已回收精灵。§7(队伍 " + (partySlot + 1) + ")");
        st.cursorIndex = (partySlot + 1) % 6;
        return;
    }

    // 该队伍位已有其它精灵召唤在外（例如队伍调整/替换导致） -> 先回收再召唤
    if (st.activeEntityBySlot.containsKey(partySlot)) {
        recallSlotIfActive(player, partySlot, "§7已先回收该队伍位的精灵，准备召唤新的。§7(队伍 " + (partySlot + 1) + ")");
    }

    if (target.currentHp <= 0) {
        player.sendMessage("§c该精灵已昏厥，无法召唤。");
        return;
    }

    sendOutSelected(player, partySlot, target, targetSpecies, desiredTarget, targetEntityUuid);
}

private void sendOutSelected(Player player, int chosen, PokemonInstance chosenP, Species chosenS) {
    sendOutSelected(player, chosen, chosenP, chosenS, null, null);
}

private void sendOutSelected(Player player, int chosen, PokemonInstance chosenP, Species chosenS, Location desiredTarget, UUID targetEntityUuid) {
    Location loc = findSafeSpawn(player, desiredTarget, targetEntityUuid);
    if (loc == null) {
        Location base = player.getLocation();
        Location fallback = fitSpawn(base.clone().add(base.getDirection().normalize().multiply(2.0)).add(0, 0.2, 0));
        if (fallback == null) {
            fallback = fitSpawn(base.clone().add(0, 1.0, 0));
        }
        if (fallback == null) {
            fallback = base.clone().add(0, 1.0, 0);
        }
        loc = fallback;
    }
    Wolf wolf = player.getWorld().spawn(loc, Wolf.class, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.CUSTOM, false, w -> {
        w.setAdult();
        w.setTamed(true);
        w.setOwner(player);
        // 名字与等级使用 TextDisplay 渲染，避免受实体模型/隐身影响
        w.setCustomNameVisible(false);
        w.setCustomName(null);
        w.setCollarColor(org.bukkit.DyeColor.LIME);

        // Hide the wolf model completely. The wolf is only a logical carrier (AI/collision).
        w.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 20 * 60 * 60, 1, false, false, false));

        // Reduce noise
        w.setSilent(true);

        // Prevent drops
        w.getEquipment().clear();

        // Set base attributes
        w.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(40.0);
        w.setHealth(40.0);

        // No breeding
        w.setAgeLock(true);

        // Avoid wolf shaking water particles etc.
        w.setCanPickupItems(false);
        w.setRemoveWhenFarAway(false);
    });

    // attach visual carrier
    visuals.attach(wolf, chosenS.id());

    // mark persistent data
    wolf.getPersistentDataContainer().set(plugin.KEY_OWNER, PersistentDataType.STRING, player.getUniqueId().toString());
    wolf.getPersistentDataContainer().set(plugin.KEY_SPECIES, PersistentDataType.STRING, chosenS.id());
    wolf.getPersistentDataContainer().set(plugin.KEY_LEVEL, PersistentDataType.INTEGER, chosenP.level);
    wolf.getPersistentDataContainer().set(plugin.KEY_PUUID, PersistentDataType.STRING, chosenP.uuid.toString());
    wolf.getPersistentDataContainer().set(plugin.KEY_PARTY_SLOT, PersistentDataType.INTEGER, Util.clamp(chosen, 0, 5));

    // bind health
    bindEntityHealth(wolf, chosenP, chosenS);

    State st = getState(player.getUniqueId());
    st.activeEntityBySlot.put(Util.clamp(chosen, 0, 5), wolf.getUniqueId());
    st.activePokemonBySlot.put(Util.clamp(chosen, 0, 5), chosenP.uuid);
    st.cursorIndex = chosen;
    player.sendMessage("§a已召唤：§f" + chosenP.displayName() + " §7Lv." + chosenP.level + " §7(队伍 " + (chosen + 1) + ")");
    try {
        if (plugin.getBridgeSyncManager() != null) {
            plugin.getBridgeSyncManager().triggerCarrierAnimation(wolf.getUniqueId(), "sendout", 10L);
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                try { plugin.getBridgeSyncManager().triggerCarrierAnimation(wolf.getUniqueId(), "cry", 24L); } catch (Throwable ignored) {}
            }, 7L);
        }
    } catch (Throwable ignored) {}
    try {
        maybeScheduleAutoBattle(player, wolf, targetEntityUuid);
    } catch (Throwable ignored) {}
}

/**
     * 找一个更安全的召唤位置：优先使用客户端发来的落点；若提供目标野怪，则尽量在其附近且朝向玩家一侧落点生成。
     */

// (已废弃) toggleSendOutSlot: 旧版单只精灵召唤接口已移除，统一使用 toggleSendOut / toggleSendOutAt。
private Location findSafeSpawn(Player player, Location desiredTarget, UUID targetEntityUuid) {
        Wolf aimedTarget = resolveAimedBattleTarget(player, targetEntityUuid, 8.0);
        Location resolved = resolveSafeSendoutLocation(player, desiredTarget, aimedTarget, 12.0, 5.0);
        if (resolved != null) return resolved;
        Location base = player.getLocation();
        Location fallback = fitSpawn(base.clone().add(base.getDirection().normalize().multiply(2.0)).add(0, 0.2, 0));
        if (fallback != null) return fallback;
        fallback = fitSpawn(base.clone().add(0, 1.0, 0));
        if (fallback != null) return fallback;
        return base.clone().add(0, 1.0, 0);
    }

    private Location resolveSafeSendoutLocation(Player player, Location desiredTarget, Wolf aimedTarget, double maxForward, double maxDrop) {
        if (player == null || player.getWorld() == null) return null;
        Location eye = player.getEyeLocation();
        org.bukkit.util.Vector look = eye.getDirection();
        if (look.lengthSquared() < 1.0E-5) look = player.getLocation().getDirection();
        if (look.lengthSquared() < 1.0E-5) look = new org.bukkit.util.Vector(0, 0, 1);
        look = look.normalize();

        if (aimedTarget != null && !aimedTarget.isDead()) {
            Location around = aimedTarget.getLocation().clone();
            org.bukkit.util.Vector away = around.toVector().subtract(player.getLocation().toVector());
            if (away.lengthSquared() < 1.0E-4) away = look.clone(); else away.normalize();
            Location side = around.clone().subtract(away.multiply(1.9)).add(0, 0.1, 0);
            Location fitted = fitSpawn(side);
            if (fitted != null) return fitted;
        }

        Location hinted = desiredTarget != null && desiredTarget.getWorld() == player.getWorld() ? desiredTarget.clone() : null;
        org.bukkit.Location hitSeed = null;
        try {
            var blockHit = player.getWorld().rayTraceBlocks(eye, look, maxForward, FluidCollisionMode.NEVER, true);
            if (blockHit != null && blockHit.getHitPosition() != null) {
                org.bukkit.util.Vector hp = blockHit.getHitPosition();
                org.bukkit.util.Vector normal = blockHit.getHitBlockFace() != null ? blockHit.getHitBlockFace().getDirection() : new org.bukkit.util.Vector(0, 1, 0);
                double offset = blockHit.getHitBlockFace() != null && blockHit.getHitBlockFace().getModY() == 0 ? 1.15 : 0.2;
                hitSeed = new Location(player.getWorld(), hp.getX(), hp.getY(), hp.getZ()).add(normal.multiply(offset));
            }
        } catch (Throwable ignored) {}
        if (hitSeed != null) {
            Location fitted = fitSpawn(hitSeed);
            if (fitted != null) return fitted;
        }

        java.util.List<Location> seeds = new ArrayList<>();
        if (hinted != null) seeds.add(hinted);
        for (double d = 3.0; d <= maxForward; d += 1.25) {
            seeds.add(eye.clone().add(look.clone().multiply(d)));
        }

        for (Location seed : seeds) {
            if (seed == null) continue;
            Location grounded = traceDownToGround(player, seed, maxDrop);
            if (grounded != null) {
                Location fitted = fitSpawn(grounded);
                if (fitted != null) return fitted;
            }
            Location fittedSeed = fitSpawn(seed);
            if (fittedSeed != null) return fittedSeed;
        }
        return null;
    }

    private Location traceDownToGround(Player player, Location seed, double maxDrop) {
        if (player == null || player.getWorld() == null || seed == null) return null;
        try {
            Location start = seed.clone().add(0, 2.0, 0);
            var downHit = player.getWorld().rayTraceBlocks(start, new org.bukkit.util.Vector(0, -1, 0), maxDrop + 2.0, FluidCollisionMode.NEVER, true);
            if (downHit != null && downHit.getHitPosition() != null) {
                org.bukkit.util.Vector hp = downHit.getHitPosition();
                return new Location(player.getWorld(), hp.getX(), hp.getY() + 0.1, hp.getZ());
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private Location fitSpawn(Location loc) {
        if (loc == null || loc.getWorld() == null) return null;
        for (int dy = -1; dy <= 3; dy++) {
            Location tryLoc = loc.clone().add(0, dy, 0);
            if (!isPassable(tryLoc) || !isPassable(tryLoc.clone().add(0, 1, 0))) continue;
            Location below = tryLoc.clone().add(0, -1, 0);
            Material floor = below.getBlock().getType();
            if (floor.isSolid() || floor == Material.WATER) {
                return tryLoc;
            }
        }
        return null;
    }

    private boolean isPassable(Location loc) {
        Material t = loc.getBlock().getType();
        return t.isAir() || t.isTransparent() || t == Material.WATER || t == Material.LAVA;
    }

    private void maybeScheduleAutoBattle(Player player, Wolf ownWolf, UUID targetEntityUuid) {
        if (player == null || ownWolf == null || plugin.battles() == null) return;
        Wolf targetWolf = resolveAimedBattleTarget(player, targetEntityUuid, 8.0);
        if (targetWolf == null) return;
        final int[] checks = {0};
        plugin.getServer().getScheduler().runTaskTimer(plugin, task -> {
            if (!player.isOnline() || ownWolf.isDead() || targetWolf.isDead()) { task.cancel(); return; }
            if (plugin.battles().isInBattle(player.getUniqueId())) { task.cancel(); return; }
            if (!ownWolf.getWorld().equals(targetWolf.getWorld())) { task.cancel(); return; }
            checks[0]++;
            double hitRadius = 3.1;
            double distSq = ownWolf.getLocation().distanceSquared(targetWolf.getLocation());
            if (distSq <= (hitRadius * hitRadius)) {
                task.cancel();
                plugin.battles().startWildBattle(player, targetWolf, ownWolf);
                return;
            }
            if (checks[0] > 10) { task.cancel(); return; }
            org.bukkit.util.Vector v = targetWolf.getLocation().toVector().subtract(ownWolf.getLocation().toVector());
            if (v.lengthSquared() > 0.0001) {
                v.normalize().multiply(0.18);
                v.setY(Math.max(v.getY(), 0.02));
                try { ownWolf.setVelocity(v); } catch (Throwable ignored) {}
            }
        }, 6L, 4L);
    }

    private Wolf resolveAimedBattleTarget(Player player, UUID hintedTargetEntityUuid, double maxDistance) {
        if (player == null || player.getWorld() == null) return null;
        if (hintedTargetEntityUuid != null) {
            Entity hintedEntity = null;
            try { hintedEntity = player.getWorld().getEntity(hintedTargetEntityUuid); } catch (Throwable ignored) {}
            Wolf hinted = resolveWildWolf(hintedEntity);
            if (hinted != null && isWithinAimWindow(player, hinted, maxDistance)) return hinted;
        }

        Location eye = player.getEyeLocation();
        org.bukkit.util.Vector look = eye.getDirection();
        if (look.lengthSquared() < 1.0E-5) return null;
        look = look.normalize();
        Wolf best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        double maxDistSq = maxDistance * maxDistance;

        for (Entity entity : player.getNearbyEntities(maxDistance + 1.5, maxDistance + 1.5, maxDistance + 1.5)) {
            Wolf wolf = resolveWildWolf(entity);
            if (wolf == null) continue;
            Location body = wolf.getLocation().clone().add(0, 0.8, 0);
            org.bukkit.util.Vector to = body.toVector().subtract(eye.toVector());
            double distSq = to.lengthSquared();
            if (distSq > maxDistSq || distSq < 1.0E-6) continue;
            double dist = Math.sqrt(distSq);
            org.bukkit.util.Vector dir = to.clone().multiply(1.0 / dist);
            double dot = look.dot(dir);
            if (dot < 0.83) continue;
            double anglePenalty = 1.0 - dot;
            double radius = targetAimRadius(wolf);
            double offAxis = to.clone().crossProduct(look).length() / Math.max(look.length(), 1.0E-6);
            if (offAxis > radius) continue;
            double score = anglePenalty * 18.0 + dist * 0.08 - radius * 0.12;
            if (score < bestScore) {
                bestScore = score;
                best = wolf;
            }
        }
        return best;
    }

    private boolean isWithinAimWindow(Player player, Wolf wolf, double maxDistance) {
        if (player == null || wolf == null || player.getWorld() != wolf.getWorld()) return false;
        Location eye = player.getEyeLocation();
        org.bukkit.util.Vector look = eye.getDirection();
        if (look.lengthSquared() < 1.0E-5) return false;
        look = look.normalize();
        org.bukkit.util.Vector to = wolf.getLocation().clone().add(0, 0.8, 0).toVector().subtract(eye.toVector());
        double distSq = to.lengthSquared();
        if (distSq < 1.0E-6 || distSq > maxDistance * maxDistance) return false;
        double dist = Math.sqrt(distSq);
        org.bukkit.util.Vector dir = to.clone().multiply(1.0 / dist);
        double dot = look.dot(dir);
        if (dot < 0.83) return false;
        double offAxis = to.clone().crossProduct(look).length() / Math.max(look.length(), 1.0E-6);
        return offAxis <= targetAimRadius(wolf);
    }

    private Wolf resolveWildWolf(Entity entity) {
        if (!(entity instanceof Wolf wolf) || wolf.isDead()) return null;
        Byte wild = wolf.getPersistentDataContainer().get(plugin.KEY_WILD, PersistentDataType.BYTE);
        return wild != null && wild == (byte) 1 ? wolf : null;
    }

    private double targetAimRadius(Wolf wolf) {
        double scale = 1.0;
        try {
            String speciesId = wolf.getPersistentDataContainer().get(plugin.KEY_SPECIES, PersistentDataType.STRING);
            if (speciesId != null && !speciesId.isBlank()) {
                double globalScale = plugin.getConfig().getDouble("visuals.scale", 2.0);
                scale = plugin.getConfig().getDouble("pokemon-visuals." + speciesId.toLowerCase(java.util.Locale.ROOT) + ".scale", globalScale);
            }
        } catch (Throwable ignored) {}
        double radius = 1.35 + Math.max(0.0, scale - 1.0) * 0.9;
        return Math.max(1.2, Math.min(radius, 2.6));
    }

    /** Used by listeners when the summoned wolf dies. */
    public void removeCarrierOnDeath(Wolf wolf) {
        visuals.detach(wolf);
    }

    /** Force-destroy a carrier and its attached visuals (used by battle visuals cleanup). */
    public void destroyCarrier(Wolf wolf) {
        if (wolf == null) return;
        try { visuals.detach(wolf); } catch (Throwable ignored) {}
        try { wolf.remove(); } catch (Throwable ignored) {}
    }

    /** Refresh floating label (name + level) if TextDisplay is attached. */
        /** Update the carrier label after an evolution/rename etc. */
    public void refreshCarrierLabel(Wolf wolf, PokemonInstance p) {
        try {
            // Main label (name + level)
            String textId = wolf.getPersistentDataContainer().get(plugin.KEY_CARRIER_TEXT, PersistentDataType.STRING);
            if (textId != null) {
                Entity ent = wolf.getWorld().getEntity(UUID.fromString(textId));
                if (ent instanceof org.bukkit.entity.TextDisplay td) {
                    td.setText("§8Lv." + p.level + " §7" + p.displayName());
                }
            }

            // Species id label (above model)
            String idTextId = wolf.getPersistentDataContainer().get(plugin.KEY_CARRIER_IDTEXT, PersistentDataType.STRING);
            if (idTextId != null) {
                Entity ent2 = wolf.getWorld().getEntity(UUID.fromString(idTextId));
                if (ent2 instanceof org.bukkit.entity.TextDisplay td2) {
                    td2.setText("§8" + p.speciesId.toLowerCase(java.util.Locale.ROOT));
                }
            }
        } catch (Exception ignored) {}
    }


    public void bindEntityHealth(Wolf wolf, PokemonInstance p, Species s) {
        int maxHp = Math.max(1, p.maxHp(s));
        double max = maxHp;
        var attr = wolf.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (attr != null) attr.setBaseValue(max);
        double cur = Math.max(1, Math.min(max, p.currentHp));
        wolf.setHealth(cur);
    }

    /** Called when recalled or on periodic sync to store entity health back into pokemon. */
    public void syncHpFromEntity(UUID owner, UUID pokemonUuid, Wolf wolf) {
        if (pokemonUuid == null) return;
        PlayerProfile prof = storage.getProfile(owner);
        PokemonInstance p = prof.findByUuid(pokemonUuid);
        if (p == null) return;

        double hp = wolf.getHealth();
        p.currentHp = (int)Math.max(0, Math.round(hp));
        storage.markDirty(owner);
    }

    public boolean isSlotActive(UUID playerId, int partySlot) {
        partySlot = Util.clamp(partySlot, 0, 5);
        return getState(playerId).activeEntityBySlot.containsKey(partySlot);
    }

    /** Remove active tracking for a slot without touching the entity (used when the entity is already dead/invalid). */
    public void clearActiveSlot(UUID playerId, int partySlot) {
        partySlot = Util.clamp(partySlot, 0, 5);
        State st = getState(playerId);
        st.activeEntityBySlot.remove(partySlot);
        st.activePokemonBySlot.remove(partySlot);
    }

    /** Remove active tracking by entity uuid (used on death). */
    public void clearActiveByEntity(UUID playerId, UUID entityUuid) {
        if (playerId == null || entityUuid == null) return;
        State st = getState(playerId);
        Integer foundSlot = null;
        for (Map.Entry<Integer, UUID> e : st.activeEntityBySlot.entrySet()) {
            if (entityUuid.equals(e.getValue())) { foundSlot = e.getKey(); break; }
        }
        if (foundSlot != null) {
            clearActiveSlot(playerId, foundSlot);
        }
    }

    public boolean isSummonedPokemonEntity(Wolf wolf) {
        return wolf.getPersistentDataContainer().has(plugin.KEY_OWNER, PersistentDataType.STRING)
                && wolf.getPersistentDataContainer().has(plugin.KEY_PUUID, PersistentDataType.STRING);
    }

    public UUID getPokemonUuidFromEntity(Wolf wolf) {
        String s = wolf.getPersistentDataContainer().get(plugin.KEY_PUUID, PersistentDataType.STRING);
        if (s == null) return null;
        try { return UUID.fromString(s); } catch (Exception e) { return null; }
    }

    public UUID getOwnerUuidFromEntity(Wolf wolf) {
        String s = wolf.getPersistentDataContainer().get(plugin.KEY_OWNER, PersistentDataType.STRING);
        if (s == null) return null;
        try { return UUID.fromString(s); } catch (Exception e) { return null; }
    }

    public Integer getPartySlotFromEntity(Wolf wolf) {
        if (wolf == null) return null;
        if (!wolf.getPersistentDataContainer().has(plugin.KEY_PARTY_SLOT, PersistentDataType.INTEGER)) return null;
        Integer i = wolf.getPersistentDataContainer().get(plugin.KEY_PARTY_SLOT, PersistentDataType.INTEGER);
        return i;
    }

/**
 * Refresh model (ItemDisplay), floating label and health binding for a summoned pokemon entity.
 * Used by evolution system.
 */
public void refreshSummonedAppearance(Wolf wolf, PokemonInstance p, Species s) {
    if (wolf == null || p == null || s == null) return;
    // Re-attach visuals to update model cmd / scale / offsets
    visuals.attach(wolf, p.speciesId, "§8Lv." + p.level + " §7" + p.displayName());
    // Update label if text display exists
    refreshCarrierLabel(wolf, p);
    // Bind max health to new species stats
    bindEntityHealth(wolf, p, s);
}

    /**
     * Spawn a visual-only carrier for battle.
     * This carrier is NOT tracked as an "active slot" summon and will be auto-removed by BattleManager.
     */
    public Wolf spawnBattleCarrier(Player owner, PokemonInstance p, Species s, Location loc) {
        if (owner == null || p == null || s == null || loc == null) return null;

        Wolf wolf = owner.getWorld().spawn(loc, Wolf.class, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.CUSTOM, false, w -> {
            w.setAdult();
            w.setTamed(true);
            w.setOwner(owner);
            w.setCustomNameVisible(false);
            w.setCustomName(null);
            w.setCollarColor(org.bukkit.DyeColor.LIME);

            // Hide the wolf model completely. This wolf is only a logical/visual carrier.
            w.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 20 * 60 * 60, 1, false, false, false));
            w.setSilent(true);
            w.getEquipment().clear();
            w.setAgeLock(true);
            w.setCanPickupItems(false);
            w.setRemoveWhenFarAway(false);
        });

        // attach visuals + label
        visuals.attach(wolf, p.speciesId, "§8Lv." + p.level + " §7" + p.displayName());

        // mark persistent data
        wolf.getPersistentDataContainer().set(plugin.KEY_OWNER, PersistentDataType.STRING, owner.getUniqueId().toString());
        wolf.getPersistentDataContainer().set(plugin.KEY_SPECIES, PersistentDataType.STRING, s.id());
        wolf.getPersistentDataContainer().set(plugin.KEY_LEVEL, PersistentDataType.INTEGER, p.level);
        wolf.getPersistentDataContainer().set(plugin.KEY_PUUID, PersistentDataType.STRING, p.uuid.toString());
        // battle-only marker
        wolf.getPersistentDataContainer().set(new org.bukkit.NamespacedKey(plugin, "battle_carrier"), PersistentDataType.BYTE, (byte) 1);

        // bind health to pokemon
        bindEntityHealth(wolf, p, s);
        try {
            if (plugin.getBridgeSyncManager() != null) {
                plugin.getBridgeSyncManager().triggerCarrierAnimation(wolf.getUniqueId(), "sendout", 10L);
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    try { plugin.getBridgeSyncManager().triggerCarrierAnimation(wolf.getUniqueId(), "cry", 22L); } catch (Throwable ignored) {}
                }, 6L);
            }
        } catch (Throwable ignored) {}
        return wolf;
    }

}
