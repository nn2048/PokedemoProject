package win.pokedemo;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class CarrierMotionController implements Runnable {
    public static final String MODE_LAND_IDLE = "land_idle";
    public static final String MODE_LAND_WALK = "land_walk";
    public static final String MODE_AIR_IDLE = "air_idle";
    public static final String MODE_AIR_FOLLOW = "air_follow";
    public static final String MODE_SURFACE_IDLE = "surface_idle";
    public static final String MODE_SURFACE_SWIM = "surface_swim";
    public static final String MODE_UNDERWATER_IDLE = "underwater_idle";
    public static final String MODE_UNDERWATER_SWIM = "underwater_swim";
    public static final String MODE_BATTLE = "battle";

    private final PokeDemoPlugin plugin;
    private final SpeciesMovementRegistry registry;
    private final Map<UUID, MotionState> states = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> stillTicks = new ConcurrentHashMap<>();
    private final Map<UUID, Location> wanderTargets = new ConcurrentHashMap<>();
    private final Map<UUID, Long> wanderRetargetAt = new ConcurrentHashMap<>();
    private final Map<String, CachedGround> groundCache = new ConcurrentHashMap<>();
    private final Map<String, CachedSafeAir> endSafeAirCache = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> waterGraceTicks = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> underwaterLatchTicks = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> lastSleeping = new ConcurrentHashMap<>();
    private static final int SLEEP_CHECK_PERIOD_TICKS = 200;
    private static final double FIXED_SLEEP_CHANCE = 0.50D;
    private static final double FIXED_WAKE_CHANCE = 0.50D;

    private int taskId = -1;

    public CarrierMotionController(PokeDemoPlugin plugin, SpeciesMovementRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
    }

    public void start() {
        stop();
        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this, 1L, 1L);
    }

    public void stop() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
        states.clear();
        stillTicks.clear();
        wanderTargets.clear();
        wanderRetargetAt.clear();
        groundCache.clear();
        endSafeAirCache.clear();
        waterGraceTicks.clear();
        underwaterLatchTicks.clear();
        lastSleeping.clear();
    }

    public MotionState getState(UUID entityUuid) {
        return entityUuid == null ? null : states.get(entityUuid);
    }

    @Override
    public void run() {
        for (World world : plugin.getServer().getWorlds()) {
            for (Wolf wolf : world.getEntitiesByClass(Wolf.class)) {
                if (!isCarrier(wolf)) continue;
                tickCarrier(wolf);
            }
        }
    }

    private void tickCarrier(Wolf wolf) {
        if (wolf == null || !wolf.isValid() || wolf.isDead()) return;
        String speciesId = wolf.getPersistentDataContainer().get(plugin.KEY_SPECIES, PersistentDataType.STRING);
        SpeciesMovementProfile profile = registry.get(speciesId);
        Player owner = resolveOwner(wolf);
        boolean inBattle = owner != null && plugin.battles() != null && plugin.battles().isInBattle(owner.getUniqueId());
        boolean rawInWater = wolf.isInWater() || wolf.isSwimming();
        boolean nearbyWater = hasNearbyLiquid(wolf);
        WaterColumn localColumn = findWaterColumn(wolf.getLocation());
        boolean columnTouchingBody = localColumn != null && wolf.getLocation().getY() <= localColumn.surfaceY + 0.18D && wolf.getLocation().getY() >= localColumn.bottomY - 0.20D;
        int waterGrace = updateWaterGrace(wolf, rawInWater || columnTouchingBody, profile);
        boolean inWater = rawInWater || columnTouchingBody || waterGrace > 0;
        boolean bodyUnderwater = inWater && isBodyUnderwater(wolf, localColumn);
        boolean latchedUnderwater = updateUnderwaterLatch(wolf, bodyUnderwater, localColumn, profile) > 0;
        bodyUnderwater = bodyUnderwater || latchedUnderwater;
        int still = updateStillTicks(wolf, inBattle);
        boolean shouldSleep = updateSleepState(wolf, owner, profile, still, inBattle, inWater, bodyUnderwater);

        if (inBattle) {
            clearSleepTracking(wolf.getUniqueId());
            restoreLandState(wolf, profile);
            remember(wolf, MODE_BATTLE, 0.0F, !wolf.isOnGround(), bodyUnderwater, false);
            return;
        }

        if (shouldSleep) {
            if (inWater && profile.canSwimInWater()) {
                holdWaterIdle(wolf, owner, profile, bodyUnderwater, true);
                return;
            }
            if (shouldUseAirMode(wolf, owner, profile)) {
                holdAirIdle(wolf, owner, profile, true);
                return;
            }
            holdLandSleep(wolf, profile, bodyUnderwater);
            return;
        }

        if (inWater && profile.canSwimInWater()) {
            if (shouldExitWaterToLand(wolf, owner, profile, localColumn, inWater)) {
                driveLand(wolf, profile);
                return;
            }
            driveWater(wolf, owner, profile, bodyUnderwater);
            return;
        }
        if (shouldUseAirMode(wolf, owner, profile)) {
            driveAir(wolf, owner, profile);
            return;
        }
        driveLand(wolf, profile);
    }

    private void driveLand(Wolf wolf, SpeciesMovementProfile profile) {
        restoreLandState(wolf, profile);
        Vector vel = wolf.getVelocity();
        double planar = planarSpeedSq(vel);
        remember(wolf, planar > 0.0009D ? MODE_LAND_WALK : MODE_LAND_IDLE, (float) Math.sqrt(planar), false, false, false);
    }

    private void driveAir(Wolf wolf, Player owner, SpeciesMovementProfile profile) {
        setNoGravity(wolf, true);
        setAi(wolf, true);
        try { wolf.setSwimming(false); } catch (Throwable ignored) {}
        try { wolf.setSitting(false); } catch (Throwable ignored) {}

        Location here = wolf.getLocation();
        Location target = owner != null ? ownedAirTarget(wolf, owner, profile) : wildAirTarget(wolf, profile);
        boolean mustFly = !profile.canWalk() || profile.avoidsLand();
        boolean shouldLand = !mustFly && canLandNear(here, target) && distanceSq(here, target) < 3.0D * 3.0D;
        if (shouldLand) {
            setNoGravity(wolf, false);
            Vector v = wolf.getVelocity();
            wolf.setVelocity(new Vector(v.getX() * 0.65D, Math.max(-0.18D, v.getY() - 0.08D), v.getZ() * 0.65D));
            remember(wolf, wolf.isOnGround() ? MODE_LAND_IDLE : MODE_AIR_IDLE, (float)Math.sqrt(planarSpeedSq(wolf.getVelocity())), !wolf.isOnGround(), false, false);
            return;
        }

        double horizSpeed = profile.mappedFlyMotion();
        Vector vel = steerTowards(here, target, horizSpeed, 0.40D, 0.18D, 0.10D);
        double targetY = target.getY();
        double currentY = here.getY();
        double dy = targetY - currentY;
        if (wolf.isOnGround() && dy > 0.18D) {
            vel.setY(Math.max(0.16D, vel.getY()));
        }
        if (dy > 0.65D) vel.setY(Math.min(0.08D, vel.getY()));
        else if (dy < -0.65D) vel.setY(Math.max(-0.08D, vel.getY()));
        else vel.setY(vel.getY() * 0.45D);

        double xzDist = Math.sqrt(distanceSqXZ(here, target));
        if (xzDist < 1.6D) {
            vel.setX(vel.getX() * 0.55D);
            vel.setZ(vel.getZ() * 0.55D);
        }
        if (xzDist < 0.85D) {
            vel.setY(Math.min(0.02D, vel.getY()));
        }
        applyFacing(wolf, vel);
        wolf.setFallDistance(0.0F);
        wolf.setVelocity(vel);

        boolean airborne = isActuallyAirborne(wolf, vel);
        double planar = planarSpeedSq(vel);
        String mode = planar > 0.0010D ? MODE_AIR_FOLLOW : MODE_AIR_IDLE;
        if (!airborne && !mustFly) mode = planar > 0.0008D ? MODE_LAND_WALK : MODE_LAND_IDLE;
        remember(wolf, mode, (float) Math.sqrt(planar), airborne, false, false);
    }

    private void driveWater(Wolf wolf, Player owner, SpeciesMovementProfile profile, boolean underwater) {
        setNoGravity(wolf, true);
        setAi(wolf, true);
        try { wolf.setSwimming(false); } catch (Throwable ignored) {}
        try { wolf.setSitting(false); } catch (Throwable ignored) {}

        WaterColumn column = findWaterColumn(wolf.getLocation());
        boolean wantsUnderwater = underwater || (profile.canBreatheUnderwater() && column != null && column.depth() >= 1.6D) || profile.avoidsLand();
        Location target = owner != null ? ownedWaterTarget(wolf, owner, profile, wantsUnderwater) : wildWaterTarget(wolf, profile, wantsUnderwater);
        if (column != null) {
            if (wantsUnderwater && column.depth() >= 1.2D) {
                target.setY(targetWaterDepthY(wolf.getLocation(), Math.min(1.8D, Math.max(1.1D, column.depth() * 0.50D))));
            } else {
                target.setY(targetSurfaceY(wolf.getLocation()));
            }
        }

        double base = profile.mappedSwimMotion();
        Vector vel = steerTowards(wolf.getLocation(), target, base, 0.28D, wantsUnderwater ? 0.22D : 0.08D, wantsUnderwater ? 0.12D : 0.04D);
        double targetY = target.getY();
        double currentY = wolf.getLocation().getY();
        if (wantsUnderwater) {
            if (currentY > targetY + 0.18D) vel.setY(Math.min(vel.getY(), -0.10D));
            else if (currentY < targetY - 0.22D) vel.setY(Math.max(vel.getY(), 0.045D));
            else vel.setY(vel.getY() * 0.35D);
        } else {
            vel.setY(Math.max(-0.035D, Math.min(0.015D, vel.getY())));
        }
        if (distanceSq(wolf.getLocation(), target) < 1.2D * 1.2D) vel.multiply(0.60D);
        if (wantsUnderwater && column != null && wolf.getLocation().getY() > column.surfaceY - 0.60D) {
            vel.setY(Math.min(vel.getY(), -0.085D));
        }
        applyFacing(wolf, vel);
        wolf.setFallDistance(0.0F);
        wolf.setVelocity(vel);
        double planar = planarSpeedSq(vel);
        String mode = wantsUnderwater
                ? (planar > 0.0007D ? MODE_UNDERWATER_SWIM : MODE_UNDERWATER_IDLE)
                : (planar > 0.0007D ? MODE_SURFACE_SWIM : MODE_SURFACE_IDLE);
        remember(wolf, mode, (float) Math.sqrt(planar), false, wantsUnderwater, false);
    }

    private void holdAirIdle(Wolf wolf, Player owner, SpeciesMovementProfile profile, boolean sleeping) {
        setNoGravity(wolf, true);
        setAi(wolf, true);
        Location target = owner != null ? ownedAirTarget(wolf, owner, profile) : wildAirTarget(wolf, profile);
        Location current = wolf.getLocation();
        if (isUnsafeEndVoid(current) || isUnsafeEndVoid(target)) {
            target = nearestSafeEndAirTarget(current, Math.min(2.0D, Math.max(0.95D, profile.clampedHoverHeight())));
        }
        Vector vel = steerTowards(current, target, Math.max(0.04D, profile.mappedFlyMotion() * 0.55D), 0.26D, 0.10D, 0.06D).multiply(0.45D);
        if (wolf.isOnGround() && target.getY() - current.getY() > 0.2D) vel.setY(Math.max(vel.getY(), 0.15D));
        if (isUnsafeEndVoid(current)) {
            vel.setY(Math.max(0.08D, vel.getY()));
        }
        wolf.setFallDistance(0.0F);
        wolf.setVelocity(vel);
        remember(wolf, isActuallyAirborne(wolf, vel) ? MODE_AIR_IDLE : MODE_LAND_IDLE, (float) Math.sqrt(planarSpeedSq(vel)), isActuallyAirborne(wolf, vel), false, sleeping);
    }

    private void holdWaterIdle(Wolf wolf, Player owner, SpeciesMovementProfile profile, boolean underwater, boolean sleeping) {
        setNoGravity(wolf, true);
        setAi(wolf, true);
        try { wolf.setSwimming(false); } catch (Throwable ignored) {}
        WaterColumn column = findWaterColumn(wolf.getLocation());
        boolean wantsUnderwater = underwater && column != null && column.depth() >= 1.2D;
        Location target = owner != null ? ownedWaterTarget(wolf, owner, profile, wantsUnderwater) : wildWaterTarget(wolf, profile, wantsUnderwater);
        if (column != null) {
            target.setY(wantsUnderwater ? targetWaterDepthY(wolf.getLocation(), Math.min(1.6D, Math.max(1.0D, column.depth() * 0.45D))) : targetSurfaceY(wolf.getLocation()));
        }
        Vector vel = steerTowards(wolf.getLocation(), target, Math.max(0.04D, profile.mappedSwimMotion() * 0.50D), 0.16D, wantsUnderwater ? 0.12D : 0.04D, wantsUnderwater ? 0.08D : 0.03D).multiply(0.40D);
        if (wantsUnderwater) {
            double dy = target.getY() - wolf.getLocation().getY();
            if (dy < -0.15D) vel.setY(Math.min(vel.getY(), -0.07D));
            else if (dy > 0.18D) vel.setY(Math.max(vel.getY(), 0.035D));
            else vel.setY(vel.getY() * 0.25D);
        } else {
            vel.setY(Math.max(-0.03D, Math.min(0.015D, vel.getY())));
        }
        if (wantsUnderwater && column != null && wolf.getLocation().getY() > column.surfaceY - 0.60D) {
            vel.setY(Math.min(vel.getY(), -0.075D));
        }
        wolf.setFallDistance(0.0F);
        wolf.setVelocity(vel);
        remember(wolf, wantsUnderwater ? MODE_UNDERWATER_IDLE : MODE_SURFACE_IDLE, (float) Math.sqrt(planarSpeedSq(vel)), false, wantsUnderwater, sleeping);
    }

    private void holdLandSleep(Wolf wolf, SpeciesMovementProfile profile, boolean underwater) {
        restoreLandState(wolf, profile);
        try { wolf.setSitting(true); } catch (Throwable ignored) {}
        wolf.setVelocity(new Vector());
        remember(wolf, MODE_LAND_IDLE, 0.0F, false, underwater, true);
    }

    private Location ownedAirTarget(Wolf wolf, Player owner, SpeciesMovementProfile profile) {
        Location base = owner.getLocation().clone();
        Vector dir = owner.getLocation().getDirection().setY(0.0D);
        if (dir.lengthSquared() < 1.0E-4D) dir = new Vector(0.0D, 0.0D, 1.0D);
        dir.normalize();
        Vector side = new Vector(-dir.getZ(), 0.0D, dir.getX()).normalize().multiply(sideOffset(wolf));
        base.add(dir.multiply(-1.6D)).add(side);
        double lowHover = Math.min(2.0D, Math.max(0.85D, profile.clampedHoverHeight()));
        if (base.getWorld() != null && base.getWorld().getEnvironment() == World.Environment.THE_END) {
            return nearestSafeEndAirTarget(base, lowHover);
        }
        double groundY = cachedGroundY(base);
        base.setY(groundY + lowHover);
        return base;
    }

    private Location wildAirTarget(Wolf wolf, SpeciesMovementProfile profile) {
        double hover = Math.min(1.8D, Math.max(0.9D, profile.clampedHoverHeight()));
        Location next = wanderTarget(wolf, hover, 5.5D, true, false);
        if (next.getWorld() != null && next.getWorld().getEnvironment() == World.Environment.THE_END) {
            return nearestSafeEndAirTarget(next, hover);
        }
        double groundY = cachedGroundY(next);
        next.setY(groundY + hover);
        return next;
    }

    private Location ownedWaterTarget(Wolf wolf, Player owner, SpeciesMovementProfile profile, boolean underwater) {
        Location base = owner.getLocation().clone();
        Vector dir = owner.getLocation().getDirection().setY(0.0D);
        if (dir.lengthSquared() > 1.0E-4D) base.add(dir.normalize().multiply(-1.1D));
        double dx = owner.getLocation().getX() - wolf.getLocation().getX();
        double dz = owner.getLocation().getZ() - wolf.getLocation().getZ();
        if ((dx * dx + dz * dz) < 0.20D * 0.20D) {
            int h = wolf.getUniqueId().hashCode();
            double angle = (h & 1023) / 1023.0D * Math.PI * 2.0D;
            base.add(Math.cos(angle) * 0.65D, 0.0D, Math.sin(angle) * 0.65D);
        }
        Location reference = wolf.getLocation();
        if (underwater) {
            base.setY(targetWaterDepthY(reference, 1.2D));
        } else {
            base.setY(targetSurfaceY(reference));
        }
        return base;
    }

    private Location wildWaterTarget(Wolf wolf, SpeciesMovementProfile profile, boolean underwater) {
        Location next = wanderTarget(wolf, underwater ? -0.8D : 0.05D, 3.6D, false, true);
        if (underwater) next.setY(targetWaterDepthY(next, 1.4D));
        else next.setY(targetSurfaceY(next));
        return next;
    }

    private Location wanderTarget(Wolf wolf, double yOffset, double radius, boolean preferAir, boolean preferWater) {
        UUID id = wolf.getUniqueId();
        long now = System.currentTimeMillis();
        Location current = wolf.getLocation();
        Location existing = wanderTargets.get(id);
        Long retarget = wanderRetargetAt.get(id);
        boolean needNew = existing == null || retarget == null || now >= retarget || !existing.getWorld().equals(current.getWorld()) || distanceSq(current, existing) < 0.8D * 0.8D;
        if (needNew) {
            ThreadLocalRandom rnd = ThreadLocalRandom.current();
            Location next = current.clone();
            for (int i = 0; i < 10; i++) {
                double dx = rnd.nextDouble(-radius, radius);
                double dz = rnd.nextDouble(-radius, radius);
                double dy = preferAir ? rnd.nextDouble(-0.75D, 1.4D) : rnd.nextDouble(-0.4D, 0.6D);
                next = current.clone().add(dx, dy + yOffset, dz);
                if (preferWater) {
                    if (!next.getBlock().isLiquid() && !next.clone().subtract(0, 0.6D, 0).getBlock().isLiquid()) continue;
                }
                break;
            }
            wanderTargets.put(id, next);
            wanderRetargetAt.put(id, now + rnd.nextLong(2200L, 4600L));
            existing = next;
        }
        return existing == null ? current.clone().add(0.0D, yOffset, 0.0D) : existing.clone();
    }

    private boolean shouldExitWaterToLand(Wolf wolf, Player owner, SpeciesMovementProfile profile, WaterColumn column, boolean inWater) {
        if (!inWater || wolf == null || !profile.canWalk()) return false;
        if (column != null && column.depth() > 1.35D) return false;
        if (owner == null || !Objects.equals(owner.getWorld(), wolf.getWorld())) return false;
        Location ownerLoc = owner.getLocation();
        boolean ownerInLiquid = ownerLoc.getBlock().isLiquid() || ownerLoc.clone().subtract(0.0D, 0.6D, 0.0D).getBlock().isLiquid();
        if (ownerInLiquid) return false;
        double xz = Math.sqrt(distanceSqXZ(wolf.getLocation(), ownerLoc));
        return xz < 3.2D || (column != null && column.depth() <= 1.10D);
    }

    private Vector steerTowards(Location from, Location to, double horizontalSpeed, double horizontalBlend, double verticalGain, double verticalCap) {
        Vector delta = to.toVector().subtract(from.toVector());
        Vector desired = new Vector();
        Vector horizontal = new Vector(delta.getX(), 0.0D, delta.getZ());
        if (horizontal.lengthSquared() > 1.0E-4D) {
            horizontal.normalize().multiply(horizontalSpeed);
            desired.setX(horizontal.getX());
            desired.setZ(horizontal.getZ());
        }
        desired.setY(Math.max(-verticalCap, Math.min(verticalCap, delta.getY() * verticalGain)));
        return desired;
    }


    private boolean canLandNear(Location from, Location target) {
        if (from == null || target == null || from.getWorld() == null || target.getWorld() == null || !from.getWorld().equals(target.getWorld())) return false;
        double groundY = cachedGroundY(target);
        return Math.abs(target.getY() - (groundY + 1.0D)) < 2.2D || Math.abs(from.getY() - groundY) < 3.2D;
    }

    private double cachedGroundY(Location loc) {
        if (loc == null || loc.getWorld() == null) return loc == null ? 0.0D : loc.getY();
        int bx = loc.getBlockX() >> 1;
        int bz = loc.getBlockZ() >> 1;
        String key = loc.getWorld().getUID() + ":" + bx + ":" + bz;
        long now = System.currentTimeMillis();
        CachedGround cg = groundCache.get(key);
        if (cg != null && now - cg.timeMs < 800L) return cg.groundY;
        World world = loc.getWorld();
        double ground;
        if (world.getEnvironment() == World.Environment.NETHER) {
            int x = loc.getBlockX();
            int z = loc.getBlockZ();
            int min = world.getMinHeight() + 1;
            int max = world.getMaxHeight() - 2;
            int startY = Math.max(min, Math.min(max, loc.getBlockY()));
            Integer solidBelow = null;
            for (int y = startY; y >= Math.max(min, startY - 18); y--) {
                if (world.getBlockAt(x, y, z).getType().isSolid()) {
                    solidBelow = y;
                    break;
                }
            }
            if (solidBelow != null) {
                ground = solidBelow + 1.0D;
            } else {
                int y = world.getHighestBlockYAt(loc);
                ground = Math.min(startY + 1.5D, y + 1.0D);
            }
        } else {
            int y = world.getHighestBlockYAt(loc);
            ground = y + 1.0D;
        }
        groundCache.put(key, new CachedGround(ground, now));
        return ground;
    }

    private boolean isUnsafeEndVoid(Location loc) {
        if (loc == null || loc.getWorld() == null || loc.getWorld().getEnvironment() != World.Environment.THE_END) return false;
        return findSupportingSurfaceY(loc, 28) == null;
    }

    private Location nearestSafeEndAirTarget(Location around, double hoverHeight) {
        if (around == null || around.getWorld() == null || around.getWorld().getEnvironment() != World.Environment.THE_END) {
            return around == null ? null : around.clone();
        }
        World world = around.getWorld();
        int bx = around.getBlockX() >> 1;
        int bz = around.getBlockZ() >> 1;
        String key = world.getUID() + ":endair:" + bx + ":" + bz;
        long now = System.currentTimeMillis();
        CachedSafeAir cached = endSafeAirCache.get(key);
        if (cached != null && now - cached.timeMs < 1200L) {
            return cached.location.clone();
        }

        Location best = null;
        double bestDist = Double.MAX_VALUE;
        int[] offsets = new int[] {0, 2, -2, 4, -4, 6, -6, 8, -8};
        for (int dx : offsets) {
            for (int dz : offsets) {
                Location probe = around.clone().add(dx, 0.0D, dz);
                Integer surfaceY = findSupportingSurfaceY(probe, 40);
                if (surfaceY == null) continue;
                Location candidate = probe.clone();
                candidate.setY(surfaceY + Math.max(1.2D, hoverHeight));
                double dist = candidate.distanceSquared(around);
                if (dist < bestDist) {
                    bestDist = dist;
                    best = candidate;
                }
            }
        }
        if (best == null) {
            best = around.clone();
            best.setY(Math.max(around.getY(), world.getMinHeight() + 8.0D));
        }
        endSafeAirCache.put(key, new CachedSafeAir(best.clone(), now));
        return best;
    }

    private Integer findSupportingSurfaceY(Location loc, int depth) {
        if (loc == null || loc.getWorld() == null) return null;
        World world = loc.getWorld();
        int x = loc.getBlockX();
        int z = loc.getBlockZ();
        int min = world.getMinHeight() + 1;
        int startY = Math.max(min, Math.min(world.getMaxHeight() - 2, loc.getBlockY()));
        for (int y = startY; y >= Math.max(min, startY - Math.max(8, depth)); y--) {
            if (world.getBlockAt(x, y, z).getType().isSolid()) {
                return y + 1;
            }
        }
        return null;
    }

    private double targetSurfaceY(Location loc) {
        WaterColumn column = findWaterColumn(loc);
        if (column == null) return loc == null ? 0.0D : loc.getY();
        return column.surfaceY - 0.90D;
    }

    private double targetWaterDepthY(Location loc, double desiredDepth) {
        WaterColumn column = findWaterColumn(loc);
        if (column == null) return loc == null ? 0.0D : loc.getY();
        double minDepth = Math.max(0.95D, desiredDepth);
        double target = column.surfaceY - minDepth;
        double minY = column.bottomY + 0.80D;
        double maxY = column.surfaceY - 1.20D;
        if (maxY < minY) {
            return Math.max(column.bottomY + 0.55D, column.surfaceY - 0.90D);
        }
        return Math.max(minY, Math.min(maxY, target));
    }


    private WaterColumn findWaterColumn(Location loc) {
        if (loc == null || loc.getWorld() == null) return null;
        World world = loc.getWorld();
        int bx = loc.getBlockX();
        int bz = loc.getBlockZ();
        int startY = Math.max(world.getMinHeight() + 1, loc.getBlockY() + 3);
        int minY = Math.max(world.getMinHeight() + 1, loc.getBlockY() - 8);
        Integer liquidY = null;
        for (int y = startY; y >= minY; y--) {
            if (world.getBlockAt(bx, y, bz).isLiquid()) {
                liquidY = y;
                break;
            }
        }
        if (liquidY == null) return null;
        int top = liquidY;
        while (top + 1 < world.getMaxHeight() && world.getBlockAt(bx, top + 1, bz).isLiquid()) top++;
        int bottom = liquidY;
        while (bottom - 1 > world.getMinHeight() && world.getBlockAt(bx, bottom - 1, bz).isLiquid()) bottom--;
        return new WaterColumn(top + 1.0D, bottom);
    }

    private boolean hasNearbyLiquid(Wolf wolf) {
        if (wolf == null) return false;
        Location base = wolf.getLocation();
        Location[] probes = new Location[] {
                base.clone(),
                base.clone().add(0.0D, 0.4D, 0.0D),
                base.clone().subtract(0.0D, 0.55D, 0.0D),
                base.clone().subtract(0.0D, 1.05D, 0.0D)
        };
        for (Location probe : probes) {
            if (probe.getBlock().isLiquid()) return true;
        }
        return false;
    }

    private int updateWaterGrace(Wolf wolf, boolean touchingWater, SpeciesMovementProfile profile) {
        UUID id = wolf.getUniqueId();
        if (!profile.canSwimInWater()) {
            waterGraceTicks.remove(id);
            return 0;
        }
        int next = touchingWater ? 10 : Math.max(0, waterGraceTicks.getOrDefault(id, 0) - 1);
        waterGraceTicks.put(id, next);
        return next;
    }


    private int updateUnderwaterLatch(Wolf wolf, boolean bodyUnderwater, WaterColumn column, SpeciesMovementProfile profile) {
        UUID id = wolf.getUniqueId();
        if (!profile.canSwimInWater() || !profile.canBreatheUnderwater()) {
            underwaterLatchTicks.remove(id);
            return 0;
        }
        boolean deepEnough = column != null && column.depth() >= 1.2D;
        double y = wolf.getLocation().getY();
        double surface = column == null ? y : column.surfaceY;
        boolean shouldLatch = deepEnough && (bodyUnderwater || y < surface - 0.55D || underwaterLatchTicks.getOrDefault(id, 0) > 0 && y < surface - 0.20D);
        int next = shouldLatch ? 12 : Math.max(0, underwaterLatchTicks.getOrDefault(id, 0) - 1);
        underwaterLatchTicks.put(id, next);
        return next;
    }

    private void restoreLandState(Wolf wolf, SpeciesMovementProfile profile) {
        setNoGravity(wolf, false);
        setAi(wolf, true);
        try { wolf.setAware(true); } catch (Throwable ignored) {}
        try { wolf.setSwimming(false); } catch (Throwable ignored) {}
        try { wolf.setSitting(false); } catch (Throwable ignored) {}
        try {
            var attr = wolf.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
            if (attr != null) attr.setBaseValue(profile.mappedWalkAttribute());
        } catch (Throwable ignored) {}
    }

    private int updateStillTicks(Wolf wolf, boolean inBattle) {
        UUID id = wolf.getUniqueId();
        if (inBattle) {
            stillTicks.put(id, 0);
            return 0;
        }
        Vector vel = wolf.getVelocity();
        double horizontal = planarSpeedSq(vel);
        double vertical = vel == null ? 0.0D : Math.abs(vel.getY());
        boolean still = horizontal < 0.0012D && vertical < 0.04D;
        int next = still ? stillTicks.getOrDefault(id, 0) + 1 : 0;
        stillTicks.put(id, next);
        return next;
    }

    private boolean updateSleepState(Wolf wolf, Player owner, SpeciesMovementProfile profile, int still, boolean inBattle, boolean inWater, boolean bodyUnderwater) {
        UUID id = wolf.getUniqueId();
        boolean sleeping = lastSleeping.getOrDefault(id, Boolean.FALSE);
        if (inBattle || !profile.canSleep()) {
            clearSleepTracking(id);
            return false;
        }

        if (sleeping) {
            if (shouldForceWake(wolf, owner, profile, still, inWater, bodyUnderwater)) {
                clearSleepTracking(id);
                return false;
            }
            if (shouldEvaluateSleepTick(id, wolf) && Math.random() < FIXED_WAKE_CHANCE) {
                clearSleepTracking(id);
                return false;
            }
            return true;
        }

        if (!canAttemptSleep(wolf, owner, profile, still, inWater, bodyUnderwater)) {
            clearSleepTracking(id);
            return false;
        }

        if (shouldEvaluateSleepTick(id, wolf) && Math.random() < FIXED_SLEEP_CHANCE) {
            clearSleepTracking(id);
            return true;
        }
        return false;
    }

    private boolean canAttemptSleep(Wolf wolf, Player owner, SpeciesMovementProfile profile, int still, boolean inWater, boolean bodyUnderwater) {
        if (owner != null && wolf.getLocation().distanceSquared(owner.getLocation()) > 64.0D) return false;
        if (inWater && !profile.canBreatheUnderwater()) return false;
        if (bodyUnderwater && !profile.canBreatheUnderwater()) return false;
        if (shouldUseAirMode(wolf, owner, profile)) return false;
        Vector vel = wolf.getVelocity();
        if (vel != null && (planarSpeedSq(vel) > 0.040D || Math.abs(vel.getY()) > 0.22D)) return false;
        long time = wolf.getWorld() == null ? 0L : wolf.getWorld().getTime();
        boolean night = time >= 12300L && time < 23850L;
        if (!night) return false;
        return true;
    }

    private boolean shouldForceWake(Wolf wolf, Player owner, SpeciesMovementProfile profile, int still, boolean inWater, boolean bodyUnderwater) {
        if (owner != null && wolf.getLocation().distanceSquared(owner.getLocation()) > 81.0D) return true;
        Vector vel = wolf.getVelocity();
        if (vel != null && (planarSpeedSq(vel) > 0.018D || Math.abs(vel.getY()) > 0.16D)) return true;
        if (inWater && !profile.canBreatheUnderwater()) return true;
        if (bodyUnderwater && !profile.canBreatheUnderwater()) return true;
        if (shouldUseAirMode(wolf, owner, profile)) return true;
        return false;
    }

    private boolean shouldEvaluateSleepTick(UUID id, Wolf wolf) {
        int period = Math.max(1, SLEEP_CHECK_PERIOD_TICKS);
        int salt = Math.floorMod(id.hashCode(), period);
        int tick = wolf.getTicksLived();
        return Math.floorMod(tick + salt, period) == 0;
    }

    private void clearSleepTracking(UUID id) {
        // keep lastSleeping controlled by remember(); this only clears transient timers if reintroduced later
    }

    private boolean shouldUseAirMode(Wolf wolf, Player owner, SpeciesMovementProfile profile) {
        if (!profile.canFly()) return false;
        if (!profile.canWalk() || profile.avoidsLand()) return true;
        Location here = wolf.getLocation();
        if (owner == null) {
            if (!wolf.isOnGround()) return true;
            Location hover = wildAirTarget(wolf, profile);
            return distanceSqXZ(here, hover) > 5.0D * 5.0D;
        }
        if (!Objects.equals(owner.getWorld(), wolf.getWorld())) return false;
        double distSq = distanceSq(here, owner.getLocation());
        if (owner.isFlying() || owner.isGliding()) return true;
        if (owner.getLocation().getY() - here.getY() > 2.2D) return true;
        if (distSq > 11.0D * 11.0D) return true;
        if (!wolf.isOnGround() && distSq > 5.0D * 5.0D) return true;
        return false;
    }

    private void setAi(Wolf wolf, boolean value) {
        try { wolf.setAI(value); } catch (Throwable ignored) {}
        try { wolf.setAware(value); } catch (Throwable ignored) {}
    }

    private void setNoGravity(Wolf wolf, boolean noGravity) {
        try { wolf.setGravity(!noGravity); } catch (Throwable ignored) {}
    }

    private void applyFacing(Wolf wolf, Vector velocity) {
        if (velocity == null || wolf == null) return;
        double horizontalSq = velocity.getX() * velocity.getX() + velocity.getZ() * velocity.getZ();
        if (horizontalSq < 0.0016D) return;
        try {
            float desiredYaw = (float) Math.toDegrees(Math.atan2(-velocity.getX(), velocity.getZ()));
            float currentYaw = wolf.getLocation().getYaw();
            float delta = wrapDegrees(desiredYaw - currentYaw);
            float maxTurn = 8.0F;
            if (delta > maxTurn) delta = maxTurn;
            if (delta < -maxTurn) delta = -maxTurn;
            wolf.setRotation(currentYaw + delta, wolf.getLocation().getPitch());
        } catch (Throwable ignored) {
            // Best-effort only. Motion is more important than facing.
        }
    }

    private float wrapDegrees(float angle) {
        while (angle <= -180.0F) angle += 360.0F;
        while (angle > 180.0F) angle -= 360.0F;
        return angle;
    }

    private boolean isActuallyAirborne(Wolf wolf, Vector velocity) {
        if (wolf == null) return false;
        if (!wolf.isOnGround()) return true;
        return velocity != null && velocity.getY() > 0.12D;
    }

    private boolean isBodyUnderwater(Wolf wolf, WaterColumn column) {
        if (wolf == null) return false;
        Location body = wolf.getLocation().clone().add(0.0D, Math.max(0.45D, wolf.getHeight() * 0.55D), 0.0D);
        if (body.getBlock().isLiquid()) return true;
        if (column == null) return false;
        return body.getY() < column.surfaceY - 0.35D;
    }

    private Player resolveOwner(Wolf wolf) {
        try {
            String raw = wolf.getPersistentDataContainer().get(plugin.KEY_OWNER, PersistentDataType.STRING);
            if (raw == null || raw.isBlank()) return null;
            UUID uuid = UUID.fromString(raw);
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline() || !player.getWorld().equals(wolf.getWorld())) return null;
            return player;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private boolean isCarrier(Wolf wolf) {
        return wolf != null
                && wolf.getPersistentDataContainer().has(plugin.KEY_SPECIES, PersistentDataType.STRING)
                && (wolf.getPersistentDataContainer().has(plugin.KEY_OWNER, PersistentDataType.STRING)
                || wolf.getPersistentDataContainer().has(plugin.KEY_WILD, PersistentDataType.BYTE));
    }

    private double sideOffset(Wolf wolf) {
        Integer slot = wolf.getPersistentDataContainer().get(plugin.KEY_PARTY_SLOT, PersistentDataType.INTEGER);
        if (slot == null) return 0.0D;
        int idx = Math.max(0, Math.min(5, slot));
        return switch (idx) {
            case 0 -> -0.9D;
            case 1 -> 0.9D;
            case 2 -> -1.7D;
            case 3 -> 1.7D;
            case 4 -> -2.5D;
            default -> 2.5D;
        };
    }

    private double distanceSq(Location a, Location b) {
        if (a == null || b == null || a.getWorld() == null || b.getWorld() == null || !a.getWorld().equals(b.getWorld())) return Double.MAX_VALUE;
        return a.distanceSquared(b);
    }

    private double distanceSqXZ(Location a, Location b) {
        if (a == null || b == null || a.getWorld() == null || b.getWorld() == null || !a.getWorld().equals(b.getWorld())) return Double.MAX_VALUE;
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }

    private double planarSpeedSq(Vector vel) {
        if (vel == null) return 0.0D;
        return vel.getX() * vel.getX() + vel.getZ() * vel.getZ();
    }

    private void remember(Wolf wolf, String mode, float speed, boolean airborne, boolean submerged, boolean sleeping) {
        UUID id = wolf.getUniqueId();
        Boolean prevSleeping = lastSleeping.put(id, sleeping);
        if (prevSleeping == null) prevSleeping = Boolean.FALSE;
        if (!prevSleeping && sleeping) {
            try { if (plugin.getBridgeSyncManager() != null) plugin.getBridgeSyncManager().triggerCarrierAnimation(id, "sleep_enter", 16L); } catch (Throwable ignored) {}
        } else if (prevSleeping && !sleeping) {
            try { if (plugin.getBridgeSyncManager() != null) plugin.getBridgeSyncManager().triggerCarrierAnimation(id, "wake", 12L); } catch (Throwable ignored) {}
        }
        states.put(id, new MotionState(mode, speed, airborne, submerged, sleeping));
    }

    public record MotionState(String moveMode, float moveSpeed, boolean airborne, boolean submerged, boolean sleeping) {}

    private record WaterColumn(double surfaceY, double bottomY) {
        double depth() { return surfaceY - bottomY; }
    }

    private record CachedGround(double groundY, long timeMs) {}
    private record CachedSafeAir(Location location, long timeMs) {}
}
