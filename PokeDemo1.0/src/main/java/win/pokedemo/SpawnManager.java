
package win.pokedemo;

import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class SpawnManager implements Listener {
    private final PokeDemoPlugin plugin;
    private final Dex dex;
    private final VisualCarrierManager visuals;
    private final SpawnTable spawnTable;
    private final SpawnPositionResolver positionResolver;
    private final SpawnSelector spawnSelector;
    private BukkitTask task;

    private static class BucketRule {
        String id;
        int weight;          // relative chance to be selected
        int perPlayerCap;    // max wilds of this bucket near a player
        int perWorldCap;     // max wilds of this bucket in the world (<=0 means ignore)
        int cooldownTicks;   // minimum ticks between successful spawns for this bucket per player
        BucketRule(String id, int weight, int perPlayerCap, int perWorldCap) {
            this(id, weight, perPlayerCap, perWorldCap, 0);
        }
        BucketRule(String id, int weight, int perPlayerCap, int perWorldCap, int cooldownTicks) {
            this.id = id;
            this.weight = weight;
            this.perPlayerCap = perPlayerCap;
            this.perWorldCap = perWorldCap;
            this.cooldownTicks = cooldownTicks;
        }
    }

    private final List<BucketRule> bucketRules = new ArrayList<>();

    // per player bucket cooldown (playerUUID|bucket -> lastSpawnTick)
    private final Map<String, Long> bucketCooldown = new HashMap<>();

    public SpawnManager(PokeDemoPlugin plugin, Dex dex) {
        this.plugin = plugin;
        this.dex = dex;
        this.visuals = new VisualCarrierManager(plugin);
        this.spawnTable = new SpawnTable(plugin, dex);
        this.positionResolver = new SpawnPositionResolver();
        this.spawnSelector = new SpawnSelector(this.spawnTable);
    }

    public void start() {
        stop();
        // Load spawn table (optional)
        try { spawnTable.load(); } catch (Throwable ignored) {}
        reloadBucketRules();
        long interval = plugin.getConfig().getLong("spawns.check-interval-ticks", 80);
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, interval, interval);
    }

    private void reloadBucketRules() {
        bucketRules.clear();
        // Defaults approximate Cobblemon bucket cadence (common dominates, ultra rare is tiny)
        // Users can override via config.yml -> spawns.buckets.<id>.
        Map<String, BucketRule> defaults = new LinkedHashMap<>();
        defaults.put("common", new BucketRule("common", 70, 10, 0, 0));
        defaults.put("uncommon", new BucketRule("uncommon", 20, 5, 0, 0));
        defaults.put("rare", new BucketRule("rare", 8, 2, 0, 200));
        defaults.put("ultra_rare", new BucketRule("ultra_rare", 2, 1, 0, 1200));
        defaults.put("special", new BucketRule("special", 0, 1, 0, 200));

        // If the spawn table provides bucket settings (custom rule format), use it as the base.
        try {
            Map<String, SpawnTable.BucketCfg> fromTable = spawnTable.getBucketsFromTable();
            if (fromTable != null && !fromTable.isEmpty()) {
                defaults.clear();
                for (var bc : fromTable.values()) {
                    defaults.put(bc.id, new BucketRule(bc.id, bc.weight, bc.perPlayerCap, 0, bc.cooldownTicks));
                }
                // Ensure common exists.
                defaults.putIfAbsent("common", new BucketRule("common", 70, 10, 0, 0));
            }
        } catch (Throwable ignored) {}
        bucketRules.addAll(defaults.values());

        try {
            org.bukkit.configuration.ConfigurationSection sec = plugin.getConfig().getConfigurationSection("spawns.buckets");
            if (sec != null) {
                // Merge user config onto defaults rather than replacing completely.
                Map<String, BucketRule> merged = new LinkedHashMap<>(defaults);
                for (String k : sec.getKeys(false)) {
                    if (k == null || k.isBlank()) continue;
                    org.bukkit.configuration.ConfigurationSection b = sec.getConfigurationSection(k);
                    if (b == null) continue;
                    int w = b.getInt("weight", 1);
                    int pp = b.getInt("per-player-cap", 1);
                    int pw = b.getInt("per-world-cap", 0);
                    int cd = b.getInt("cooldown-ticks", 0);
                    String id = k.trim().toLowerCase(Locale.ROOT);
                    BucketRule base = merged.getOrDefault(id, new BucketRule(id, 0, 1, 0, 0));
                    merged.put(id, new BucketRule(id, Math.max(0, w), Math.max(0, pp), pw, Math.max(0, cd)));
                }
                bucketRules.clear();
                bucketRules.addAll(merged.values());
            }
        } catch (Throwable ignored) {}

        if (bucketRules.isEmpty()) bucketRules.add(new BucketRule("common", 1, 999, 0));
        plugin.getLogger().info("[PokeDemo] Buckets enabled: " + bucketRules.size() + " (" + bucketRules.get(0).id + "...)");
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        String mode = plugin.getConfig().getString("spawns.mode", "ENTITY").toUpperCase();
        if (!mode.equals("ENTITY")) return;

        int cap = plugin.getConfig().getInt("spawns.per-player-cap", 8);
        int legacyRadius = plugin.getConfig().getInt("spawns.spawn-radius", 24);
        int minSpawnDistance = plugin.getConfig().getInt("spawns.min-spawn-distance", Math.max(12, legacyRadius / 2));
        int maxSpawnDistance = plugin.getConfig().getInt("spawns.max-spawn-distance", Math.max(legacyRadius, 32));
        int zoneDiameter = plugin.getConfig().getInt("spawns.zone-diameter", 8);
        int zoneHeight = plugin.getConfig().getInt("spawns.zone-height", 16);
        int densityRadius = plugin.getConfig().getInt("spawns.nearby-density-radius", maxSpawnDistance);
        int sampleCount = plugin.getConfig().getInt("spawns.position-sample-count", 28);
        int despawnDist = plugin.getConfig().getInt("spawns.despawn-distance", 96);
        int maxSpawnsPerPass = plugin.getConfig().getInt("spawns.max-spawns-per-pass", 2);

        List<String> enabledDimsRaw = plugin.getConfig().getStringList("spawns.enabled-dimensions");
        Set<String> enabledDims = new HashSet<>();
        for (String s : enabledDimsRaw) if (s != null && !s.isBlank()) enabledDims.add(s.trim().toLowerCase(Locale.ROOT));
        Set<String> enabledWorlds = new HashSet<>(plugin.getConfig().getStringList("spawns.enabled-worlds"));
        Set<UUID> processedWilds = new HashSet<>();

        for (Player player : Bukkit.getOnlinePlayers()) {
            World w = player.getWorld();

            if (!enabledDims.isEmpty()) {
                try {
                    String key = w.getKey() != null ? w.getKey().toString().toLowerCase(Locale.ROOT) : "";
                    if (!enabledDims.contains(key)) continue;
                } catch (Throwable ignored) {
                    continue;
                }
            } else if (!enabledWorlds.isEmpty() && !enabledWorlds.contains(w.getName())) {
                continue;
            }

            try {
                Storage st = plugin.getStorage();
                if (st != null) {
                    PlayerProfile prof = st.getProfile(player.getUniqueId());
                    if (prof != null && prof.party != null && !prof.party.isEmpty()) {
                        PokemonInstance lead = prof.party.get(0);
                        String hid = lead == null || lead.heldItemId == null ? "" : lead.heldItemId.toLowerCase(Locale.ROOT);
                        if (hid.equals("cleanse_tag") || hid.equals("pure_incense")) {
                            if (Util.RND.nextDouble() < 0.50) continue;
                        }
                    }
                }
            } catch (Throwable ignored) {}

            for (Entity ent : w.getNearbyEntities(player.getLocation(), despawnDist, despawnDist, despawnDist)) {
                if (!(ent instanceof Wolf wolf) || !isWild(wolf)) continue;
                if (!processedWilds.add(wolf.getUniqueId())) continue;
                if (!hasNearbyPlayer(wolf.getLocation(), despawnDist)) {
                    visuals.detach(wolf);
                    wolf.remove();
                }
            }

            int nearby = 0;
            Map<String, Integer> bucketNear = new HashMap<>();
            for (Entity ent : w.getNearbyEntities(player.getLocation(), densityRadius, densityRadius, densityRadius)) {
                if (ent instanceof Wolf wolf && isWild(wolf)) {
                    nearby++;
                    String b = getBucket(wolf);
                    if (b == null) b = "common";
                    bucketNear.put(b, bucketNear.getOrDefault(b, 0) + 1);
                }
            }
            if (nearby >= cap) continue;

            int spawnCount = Math.min(maxSpawnsPerPass, cap - nearby);
            for (int i = 0; i < spawnCount; i++) {
                SpawnZone zone = buildZone(player, minSpawnDistance, maxSpawnDistance, zoneDiameter, zoneHeight);
                if (zone == null) continue;
                List<ResolvedSpawnPosition> positions = positionResolver.resolve(zone, sampleCount);
                if (positions.isEmpty()) continue;

                SpawnSelector.Selection selection = null;
                if (Util.RND.nextDouble() < 0.12) {
                    selection = spawnSelector.select(zone, positions, "special");
                }

                String chosenBucket = chooseBucket(w, player.getLocation(), bucketNear);
                if (selection == null && chosenBucket != null) selection = spawnSelector.select(zone, positions, chosenBucket);
                if (selection == null && chosenBucket != null && !"common".equalsIgnoreCase(chosenBucket)) {
                    selection = spawnSelector.select(zone, positions, "common");
                }
                if (selection == null) continue;

                SpawnTable.Entry pick = selection.entry;
                String b = (pick.bucket == null ? "common" : pick.bucket.toLowerCase(Locale.ROOT));
                int cd = getCooldownTicksForBucket(b);
                if (cd > 0) {
                    long now = w.getFullTime();
                    String ck = player.getUniqueId().toString() + "|" + b;
                    long last = bucketCooldown.getOrDefault(ck, -999999L);
                    if (now - last < cd) continue;
                }

                spawnWildWithExtras(selection.position.location, pick);

                if (cd > 0) bucketCooldown.put(player.getUniqueId().toString() + "|" + b, w.getFullTime());
                bucketNear.put(b, bucketNear.getOrDefault(b, 0) + 1);
            }
        }
    }


    private SpawnZone buildZone(Player player, int minDistance, int maxDistance, int diameter, int height) {
        if (player == null || player.getWorld() == null) return null;
        Location base = player.getLocation();
        double yaw = Math.toRadians(base.getYaw());
        double forwardX = -Math.sin(yaw);
        double forwardZ = Math.cos(yaw);
        double dist = minDistance + (Util.RND.nextDouble() * Math.max(1.0, (double) (maxDistance - minDistance)));
        double side = (Util.RND.nextDouble() * 2.0 - 1.0) * Math.max(4.0, diameter);
        double dx = forwardX * dist + forwardZ * side;
        double dz = forwardZ * dist - forwardX * side;
        Location center = base.clone().add(dx, 0, dz);
        int minY = Math.max(player.getWorld().getMinHeight() + 2, base.getBlockY() - height);
        int maxY = Math.min(player.getWorld().getMaxHeight() - 2, base.getBlockY() + height);
        center.setY(Util.clamp(base.getBlockY(), minY, maxY));
        return new SpawnZone(player, center, diameter, height);
    }

    private boolean hasNearbyPlayer(Location loc, int radius) {
        if (loc == null || loc.getWorld() == null) return false;
        double max = (double) radius * (double) radius;
        for (Player p : loc.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(loc) <= max) return true;
        }
        return false;
    }

    private String chooseBucket(World w, Location center, Map<String, Integer> bucketNear) {
        if (bucketRules.isEmpty()) return null;

        // Build eligible bucket list.
        List<BucketRule> eligible = new ArrayList<>();
        int totalW = 0;
        for (BucketRule r : bucketRules) {
            if (r == null || r.weight <= 0) continue;
            if (r.id != null && r.id.equalsIgnoreCase("special")) continue; // special rules are triggered by conditions, not random
            int near = bucketNear.getOrDefault(r.id, 0);
            if (r.perPlayerCap > 0 && near >= r.perPlayerCap) continue;
            if (r.perWorldCap > 0) {
                int worldCount = countBucketInWorld(w, r.id);
                if (worldCount >= r.perWorldCap) continue;
            }
            eligible.add(r);
            totalW += r.weight;
        }
        if (eligible.isEmpty() || totalW <= 0) return null;

        int roll = Util.RND.nextInt(totalW);
        for (BucketRule r : eligible) {
            roll -= r.weight;
            if (roll < 0) return r.id;
        }
        return eligible.get(eligible.size() - 1).id;
    }

    private int getCooldownTicksForBucket(String bucket) {
        if (bucket == null) return 0;
        for (BucketRule r : bucketRules) {
            if (r != null && r.id != null && r.id.equalsIgnoreCase(bucket)) return Math.max(0, r.cooldownTicks);
        }
        return 0;
    }

    private Location findCaveSpawnLocationNear(Location origin, int radius) {
        if (origin == null || origin.getWorld() == null) return null;
        World w = origin.getWorld();
        for (int i = 0; i < 24; i++) {
            int dx = Util.RND.nextInt(radius * 2 + 1) - radius;
            int dz = Util.RND.nextInt(radius * 2 + 1) - radius;
            int y = Math.max(w.getMinHeight() + 5, origin.getBlockY() - Util.RND.nextInt(40) - 5);
            Location l = new Location(w, origin.getX() + dx, y, origin.getZ() + dz);
            // Find a nearby air pocket.
            for (int j = 0; j < 16; j++) {
                if (l.getBlock().isPassable() && l.clone().add(0, -1, 0).getBlock().getType().isSolid()) {
                    try {
                        if (l.getBlock().getLightFromSky() <= 1) return l;
                    } catch (Throwable ignored) {
                        return l;
                    }
                }
                l.add(0, 1, 0);
            }
        }
        return null;
    }

    private int countBucketInWorld(World w, String bucket) {
        if (w == null || bucket == null) return 0;
        int c = 0;
        for (Entity e : w.getEntities()) {
            if (e instanceof Wolf wolf && isWild(wolf)) {
                String b = getBucket(wolf);
                if (b == null) b = "common";
                if (b.equalsIgnoreCase(bucket)) c++;
            }
        }
        return c;
    }

    private String getBucket(Wolf wolf) {
        try {
            return wolf.getPersistentDataContainer().get(plugin.KEY_BUCKET, PersistentDataType.STRING);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Picks a "probe" location used for selecting spawn table entries.
     * It is not required to be a valid ground spawn point.
     */
    private Location pickProbeLocation(Location origin, int radius) {
    World w = origin.getWorld();
    if (w == null) return null;

    // Nether has a bedrock ceiling; using highestBlockYAt() would probe on the roof and break rule matching.
    // So for NETHER we probe near the player's Y instead.
    boolean isNether = false;
    try { isNether = (w.getEnvironment() == World.Environment.NETHER); } catch (Throwable ignored) {}

    // If the player is near water, occasionally probe from a water column.
    // This helps river/lake spawns select water-only entries instead of land entries.
    if (!isNether && Util.RND.nextDouble() < 0.50) {
        Location waterProbe = findWaterSpawnLocationNear(origin, radius);
        if (waterProbe != null) return waterProbe;
    }

    for (int i = 0; i < 12; i++) {
        double dx = (Util.RND.nextDouble() * 2 - 1) * radius;
        double dz = (Util.RND.nextDouble() * 2 - 1) * radius;
        Location l = origin.clone().add(dx, 0, dz);

        if (isNether) {
            // Probe around player height (slightly above to avoid floors)
            l.setY(Math.min(w.getMaxHeight() - 2, Math.max(w.getMinHeight() + 2, origin.getY() + 2)));
            return l;
        } else {
            int top = w.getHighestBlockYAt(l);
            l.setY(Math.max(w.getMinHeight() + 2, top + 1));
            return l;
        }
    }
    return null;
}

    private Location findGroundSpawnLocationNear(Location origin, int radius) {
    World w = origin.getWorld();
    if (w == null) return null;

    boolean isNether = false;
    try { isNether = (w.getEnvironment() == World.Environment.NETHER); } catch (Throwable ignored) {}

    // Overworld: simple "top surface" search.
    if (!isNether) {
        for (int i = 0; i < 18; i++) {
            double dx = (Util.RND.nextDouble() * 2 - 1) * radius;
            double dz = (Util.RND.nextDouble() * 2 - 1) * radius;
            Location l = origin.clone().add(dx, 0, dz);
            l.setY(w.getHighestBlockYAt(l) + 1);
            if (l.getY() <= w.getMinHeight() + 2) continue;
            if (!l.getBlock().getType().isAir()) continue;
            if (!l.clone().add(0, 1, 0).getBlock().getType().isAir()) continue;
            if (!l.clone().subtract(0, 1, 0).getBlock().getType().isSolid()) continue;
            return l;
        }
        return null;
    }

    // Nether: DO NOT use highestBlockYAt() (bedrock roof).
    // Instead, scan downward from around the player's Y to find a solid floor with 2 passable blocks above.
    int minY = w.getMinHeight() + 2;
    int maxY = w.getMaxHeight() - 3;
    int baseY = Util.clamp(origin.getBlockY(), minY + 4, maxY - 4);

    for (int i = 0; i < 30; i++) {
        double dx = (Util.RND.nextDouble() * 2 - 1) * radius;
        double dz = (Util.RND.nextDouble() * 2 - 1) * radius;
        Location l = origin.clone().add(dx, 0, dz);

        // Start a bit above the player's Y to "catch" ledges/bridges.
        int startY = Math.min(maxY, baseY + 18);
        int endY = Math.max(minY, baseY - 48);

        for (int y = startY; y >= endY; y--) {
            l.setY(y);

            // Need a floor at y-1 and space at y and y+1
            var floor = l.clone().subtract(0, 1, 0).getBlock();
            var b0 = l.getBlock();
            var b1 = l.clone().add(0, 1, 0).getBlock();

            if (!floor.getType().isSolid()) continue;

            // Avoid lava floors
            if (floor.getType() == Material.LAVA) continue;

            // Require passable space (air-like) for the carrier/wolf
            if (!b0.isPassable()) continue;
            if (!b1.isPassable()) continue;

            // Avoid spawning inside lava blocks
            if (b0.getType() == Material.LAVA || b1.getType() == Material.LAVA) continue;

            // Avoid the nether roof area
            if (y >= 122) continue;

            return l.clone().add(0.5, 0.0, 0.5);
        }
    }

    return null;
}

    /** Ensure the wolf is not spawned inside blocks; nudge upward or sideways to a safe spot. */
    private void ensureNotStuck(Wolf wolf) {
        if (wolf == null || wolf.getWorld() == null) return;
        Location base = wolf.getLocation();
        World w = base.getWorld();
        // Try a few upward nudges first
        Location l = base.clone();
        for (int dy = 0; dy <= 6; dy++) {
            l.setY(base.getY() + dy);
            if (l.getBlock().isPassable() && l.clone().add(0, 1, 0).getBlock().isPassable()) {
                if (dy > 0) wolf.teleport(l);
                return;
            }
        }
        // Try small horizontal offsets
        int[][] d = {{1,0},{-1,0},{0,1},{0,-1},{1,1},{1,-1},{-1,1},{-1,-1}};
        for (int[] off : d) {
            Location t = base.clone().add(off[0], 0, off[1]);
            int top = w.getHighestBlockYAt(t);
            t.setY(Math.max(w.getMinHeight() + 2, top + 1));
            if (t.getBlock().isPassable() && t.clone().add(0, 1, 0).getBlock().isPassable()) {
                wolf.teleport(t);
                return;
            }
        }
    }

    private Location findWaterSpawnLocationNear(Location origin, int radius) {
        World w = origin.getWorld();
        if (w == null) return null;
        for (int i = 0; i < 24; i++) {
            double dx = (Util.RND.nextDouble() * 2 - 1) * radius;
            double dz = (Util.RND.nextDouble() * 2 - 1) * radius;
            Location l = origin.clone().add(dx, 0, dz);
            int top = w.getHighestBlockYAt(l);
            // scan downward a bit to find water surface
            for (int y = top; y > Math.max(w.getMinHeight() + 2, top - 24); y--) {
                l.setY(y);
                if (l.getBlock().getType() == Material.WATER) {
                    // Spawn slightly inside water so carrier is clickable but "in water"
                    return l.clone().add(0.5, 0.2, 0.5);
                }
            }
        }
        return null;
    }

    private void spawnWildWithExtras(Location loc, SpawnTable.Entry picked) {
        Wolf leader = spawnWildSingle(loc, picked);
        if (leader == null || picked == null) return;
        try { spawnHerdExtras(leader.getLocation(), picked); } catch (Throwable ignored) {}
    }

    private void spawnHerdExtras(Location center, SpawnTable.Entry picked) {
        if (center == null || center.getWorld() == null || picked == null) return;
        if (!(picked.herd || picked.herdMax > 1)) return;
        int min = Math.max(2, picked.herdMin);
        int max = Math.max(min, picked.herdMax);
        int total = min + Util.RND.nextInt(max - min + 1);
        int extras = Math.max(0, total - 1);
        int radius = Math.max(3, picked.herdRadius);
        int speciesNearby = countSpeciesNearby(center, picked.species, radius + 8);
        if (speciesNearby >= max) return;
        extras = Math.min(extras, Math.max(0, max - 1 - speciesNearby));
        for (int i = 0; i < extras; i++) {
            Location extra = findGroundSpawnLocationNear(center, radius);
            if (extra == null) continue;
            if (extra.distanceSquared(center) < (double) picked.herdMinDistance * picked.herdMinDistance) continue;
            spawnWildSingle(extra, picked);
        }
    }

    private int countSpeciesNearby(Location loc, String speciesId, int radius) {
        if (loc == null || loc.getWorld() == null || speciesId == null) return 0;
        int c = 0;
        try {
            for (Entity ent : loc.getWorld().getNearbyEntities(loc, radius, radius, radius)) {
                if (!(ent instanceof Wolf wolf) || !isWild(wolf)) continue;
                String sid = wolf.getPersistentDataContainer().get(plugin.KEY_SPECIES, PersistentDataType.STRING);
                if (sid != null && sid.equalsIgnoreCase(speciesId)) c++;
            }
        } catch (Throwable ignored) {}
        return c;
    }

    private void spawnWild(Location loc, SpawnTable.Entry picked) {
        spawnWildWithExtras(loc, picked);
    }

    private Wolf spawnWildSingle(Location loc, SpawnTable.Entry picked) {
        if (dex.getSpeciesCount() == 0) return null;

        Species s;
        int lv;
        if (picked != null && picked.species != null) {
            s = dex.getSpecies(picked.species.toLowerCase(Locale.ROOT));
            if (s == null) return null;
            int minL = Math.max(1, picked.minLevel);
            int maxL = Math.max(minL, picked.maxLevel);
            lv = minL + Util.RND.nextInt(maxL - minL + 1);
        } else {
            // IMPORTANT: When spawn rules are enabled, DO NOT fallback to random species.
            // Otherwise, dimensions/biomes with no matching rules (e.g., The End) would spawn
            // arbitrary Pokémon and appear as "random spawns".
            if (spawnTable.isRulesMode()) return null;

            // Fallback: random species (demo / legacy spawn table)
            List<Species> list = new ArrayList<>(dex.allSpecies());
            if (list.isEmpty()) return null;
            s = list.get(Util.RND.nextInt(list.size()));
            lv = Util.clamp(s.minLevel() + Util.RND.nextInt(Math.max(1, s.maxLevel() - s.minLevel() + 1)), 1, 100);
        }

        Wolf wolf = loc.getWorld().spawn(loc, Wolf.class, w -> {
            w.setAdult();
            w.setTamed(false);
            // Ensure vanilla AI is enabled so wild Pokémon can wander.
            try { w.setAI(true); } catch (Throwable ignored) {}
            try { w.setAware(true); } catch (Throwable ignored) {}
            try { w.setCollidable(true); } catch (Throwable ignored) {}
            try { w.setInvulnerable(false); } catch (Throwable ignored) {}
            // 名字/等级使用 TextDisplay 渲染
            w.setCustomNameVisible(false);
            w.setCustomName(null);
            w.setAngry(false);
            w.setRemoveWhenFarAway(true);

            // Hide the wolf model for wilds too (hitbox still exists)
            w.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, false, false));
            // Wild Pokémon carriers are kept quiet/passive; the client handles visuals.
            w.setSilent(true);
            try { w.setSitting(true); } catch (Throwable ignored) {}
            try { w.setAI(false); } catch (Throwable ignored) {}
            try { w.setAware(false); } catch (Throwable ignored) {}

            // placeholder immunity (water/lava) for water/lava species later
            w.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, Integer.MAX_VALUE, 0, true, false));
            w.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, Integer.MAX_VALUE, 0, true, false));

            // Visual model + floating label
            LangManager lang = PokeDemoPlugin.INSTANCE != null ? PokeDemoPlugin.INSTANCE.getLang() : null;
            String display = (lang != null) ? lang.species(s.id(), s.name()) : s.name();
            // Make the name tag less eye-catching than bright green.
            String label = "§f" + display + " §7Lv." + lv;
            visuals.attach(w, s.id(), label);
        });

        // Prevent being stuck in blocks (under ice, inside terrain, etc.)
        try { ensureNotStuck(wolf); } catch (Throwable ignored) {}

        wolf.getPersistentDataContainer().set(plugin.KEY_WILD, PersistentDataType.BYTE, (byte)1);
        wolf.getPersistentDataContainer().set(plugin.KEY_SPECIES, PersistentDataType.STRING, s.id());
        wolf.getPersistentDataContainer().set(plugin.KEY_LEVEL, PersistentDataType.INTEGER, lv);
        if (picked != null && picked.bucket != null) {
            wolf.getPersistentDataContainer().set(plugin.KEY_BUCKET, PersistentDataType.STRING, picked.bucket.toLowerCase(Locale.ROOT));
        } else {
            wolf.getPersistentDataContainer().set(plugin.KEY_BUCKET, PersistentDataType.STRING, "common");
        }
        return wolf;
    }

    /**
     * Spawn a specific wild Pokémon for testing.
     * Returns the spawned carrier wolf UUID, or null if failed.
     */
    public java.util.UUID spawnWildManual(org.bukkit.Location loc, String speciesId, int level) {
        if (loc == null || loc.getWorld() == null) return null;
        if (speciesId == null || speciesId.isBlank()) return null;
        Species s = dex.getSpecies(speciesId.toLowerCase(java.util.Locale.ROOT));
        if (s == null) return null;
        int lv = Util.clamp(level, 1, 100);

        Wolf wolf = loc.getWorld().spawn(loc, Wolf.class, w -> {
            w.setAdult();
            w.setTamed(false);
            try { w.setAI(true); } catch (Throwable ignored) {}
            try { w.setAware(true); } catch (Throwable ignored) {}
            try { w.setCollidable(true); } catch (Throwable ignored) {}
            try { w.setInvulnerable(false); } catch (Throwable ignored) {}
            w.setCustomNameVisible(false);
            w.setCustomName(null);
            w.setAngry(false);
            w.setRemoveWhenFarAway(true);

            w.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, false, false));
            w.setSilent(true);
            try { w.setSitting(true); } catch (Throwable ignored) {}
            try { w.setAI(false); } catch (Throwable ignored) {}
            try { w.setAware(false); } catch (Throwable ignored) {}
            w.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, Integer.MAX_VALUE, 0, true, false));
            w.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, Integer.MAX_VALUE, 0, true, false));

            LangManager lang = PokeDemoPlugin.INSTANCE != null ? PokeDemoPlugin.INSTANCE.getLang() : null;
            String display = (lang != null) ? lang.species(s.id(), s.name()) : s.name();
            String label = "§a" + display + " §7Lv." + lv;
            visuals.attach(w, s.id(), label);
        });

        try { ensureNotStuck(wolf); } catch (Throwable ignored) {}

        wolf.getPersistentDataContainer().set(plugin.KEY_WILD, PersistentDataType.BYTE, (byte)1);
        wolf.getPersistentDataContainer().set(plugin.KEY_SPECIES, PersistentDataType.STRING, s.id());
        wolf.getPersistentDataContainer().set(plugin.KEY_LEVEL, PersistentDataType.INTEGER, lv);
        return wolf.getUniqueId();
    }

    public SpawnTable getSpawnTable() { return spawnTable; }

    public SpawnSelector.Selection selectFishingEncounter(Location hookLocation, FishingContext context) {
        if (hookLocation == null || hookLocation.getWorld() == null) return null;
        List<ResolvedSpawnPosition> positions = new ArrayList<>();
        ResolvedSpawnPosition surface = buildFishingPosition(hookLocation.clone().add(0, 0.2, 0), "surface");
        ResolvedSpawnPosition submerged = buildFishingPosition(hookLocation.clone().add(0, -1.0, 0), "submerged");
        ResolvedSpawnPosition seafloor = buildFishingSeafloorPosition(hookLocation.clone());
        if (surface != null) positions.add(surface);
        if (submerged != null) positions.add(submerged);
        if (seafloor != null) positions.add(seafloor);
        if (positions.isEmpty()) return null;

        String weightedBucket = rollFishingBucket();
        SpawnSelector.Selection sel = spawnSelector.selectFishing(positions, weightedBucket, context);
        if (sel != null) return sel;
        for (String fallback : java.util.List.of("common", "uncommon", "rare", "ultra_rare", null)) {
            if (java.util.Objects.equals(weightedBucket, fallback)) continue;
            sel = spawnSelector.selectFishing(positions, fallback, context);
            if (sel != null) return sel;
        }
        return null;
    }

    private String rollFishingBucket() {
        java.util.LinkedHashMap<String, Integer> weights = new java.util.LinkedHashMap<>();
        weights.put("common", plugin.getConfig().getInt("fishing-pokemon.bucket-weights.common", 55));
        weights.put("uncommon", plugin.getConfig().getInt("fishing-pokemon.bucket-weights.uncommon", 28));
        weights.put("rare", plugin.getConfig().getInt("fishing-pokemon.bucket-weights.rare", 12));
        weights.put("ultra_rare", plugin.getConfig().getInt("fishing-pokemon.bucket-weights.ultra_rare", 5));
        int total = 0;
        for (int w : weights.values()) total += Math.max(0, w);
        if (total <= 0) return null;
        int roll = Util.RND.nextInt(total);
        for (var en : weights.entrySet()) {
            roll -= Math.max(0, en.getValue());
            if (roll < 0) return en.getKey();
        }
        return null;
    }

    private ResolvedSpawnPosition buildFishingPosition(Location loc, String positionType) {
        if (loc == null || loc.getWorld() == null) return null;
        String biomeKey;
        try {
            biomeKey = loc.getBlock().getBiome().getKey().toString().toLowerCase(Locale.ROOT);
        } catch (Throwable t) {
            biomeKey = "unknown";
        }
        boolean canSeeSky;
        int skyLight;
        int blockLight;
        try { canSeeSky = loc.getWorld().getHighestBlockYAt(loc) <= loc.getBlockY(); } catch (Throwable ignored) { canSeeSky = true; }
        try { skyLight = loc.getBlock().getLightFromSky(); } catch (Throwable ignored) { skyLight = 15; }
        try { blockLight = loc.getBlock().getLightFromBlocks(); } catch (Throwable ignored) { blockLight = 0; }
        java.util.Set<String> nearbyBlocks = new java.util.HashSet<>();
        nearbyBlocks.add("minecraft:water");
        java.util.Set<String> tags = BiomeTagService.collectTags(biomeKey, loc, positionType, canSeeSky, true);
        java.util.Set<String> archetypes = new java.util.HashSet<>();
        archetypes.add("water_near");
        if ("seafloor".equalsIgnoreCase(positionType)) archetypes.add("seafloor");
        if ("submerged".equalsIgnoreCase(positionType)) archetypes.add("submerged");
        if ("surface".equalsIgnoreCase(positionType)) archetypes.add("surface_water");
        if (tags.contains("river") || tags.contains("#cobblemon:is_river")) archetypes.add("river_spot");
        if (tags.contains("freshwater") || tags.contains("#cobblemon:is_freshwater")) archetypes.add("freshwater_spot");
        if (tags.contains("ocean") || tags.contains("#cobblemon:is_ocean")) archetypes.add("ocean_spot");
        if (tags.contains("shore") || tags.contains("coast") || tags.contains("#cobblemon:is_coast") || tags.contains("#cobblemon:is_beach")) archetypes.add("coast_spot");
        if (tags.contains("#cobblemon:is_warm_ocean")) archetypes.add("warm_ocean_spot");
        if (tags.contains("#cobblemon:is_cold_ocean")) archetypes.add("cold_ocean_spot");
        if (tags.contains("#cobblemon:is_frozen_ocean")) archetypes.add("frozen_ocean_spot");
        if (StructureUtil.isNearAnyStructure(loc.getWorld(), loc, java.util.List.of("SHIPWRECK", "OCEAN_RUIN", "OCEAN_MONUMENT"), 160, false)) archetypes.add("special_fishing_spot");
        return new ResolvedSpawnPosition(loc, positionType, biomeKey,
                tags,
                nearbyBlocks, canSeeSky, skyLight, blockLight, true,
                loc.getWorld().hasStorm(), loc.getWorld().isThundering(), archetypes);
    }


    private ResolvedSpawnPosition buildFishingSeafloorPosition(Location hookLocation) {
        if (hookLocation == null || hookLocation.getWorld() == null) return null;
        Location cur = hookLocation.clone();
        for (int i = 0; i < 16; i++) {
            cur.subtract(0, 1, 0);
            org.bukkit.block.Block b = cur.getBlock();
            if (b.getType() == org.bukkit.Material.WATER || b.isPassable()) continue;
            Location spawn = b.getLocation().add(0.5, 1.0, 0.5);
            if (spawn.getBlock().getType() != org.bukkit.Material.WATER && !spawn.getBlock().isPassable()) return null;
            return buildFishingPosition(spawn, "seafloor");
        }
        return null;
    }

    public boolean isWild(Wolf wolf) {
        Byte b = wolf.getPersistentDataContainer().get(plugin.KEY_WILD, PersistentDataType.BYTE);
        return b != null && b == (byte)1;
    }

    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        if (e.getEntity() instanceof Wolf wolf && isWild(wolf)) {
            visuals.detach(wolf);
            // prevent normal drops
            e.getDrops().clear();
        }
    }

    @EventHandler
    public void onWorldUnload(WorldUnloadEvent e) {
        // no-op
    }
}
