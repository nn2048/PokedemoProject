package win.pokedemo;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class SpawnPositionResolver {

    public List<ResolvedSpawnPosition> resolve(SpawnZone zone, int sampleCount) {
        List<ResolvedSpawnPosition> out = new ArrayList<>();
        if (zone == null || zone.world == null) return out;
        World w = zone.world;
        int attempts = Math.max(16, sampleCount);
        Set<String> seen = new HashSet<>();

        for (int i = 0; i < attempts; i++) {
            int x = zone.minX + Util.RND.nextInt(Math.max(1, zone.maxX - zone.minX + 1));
            int z = zone.minZ + Util.RND.nextInt(Math.max(1, zone.maxZ - zone.minZ + 1));
            maybeAdd(out, seen, grounded(w, x, z, zone), "grounded");
            maybeAdd(out, seen, surface(w, x, z, zone), "surface");
            maybeAdd(out, seen, submerged(w, x, z, zone), "submerged");
            maybeAdd(out, seen, seafloor(w, x, z, zone), "seafloor");
            maybeAdd(out, seen, cave(w, x, z, zone), "cave");
            if (out.size() < Math.max(16, sampleCount)) {
                Location g = grounded(w, x, z, zone);
                if (g != null) {
                    try {
                        if (StructureUtil.isNearAnyStructure(w, g, java.util.List.of("VILLAGE"), 96, false)) maybeAdd(out, seen, g, "grounded");
                        if (StructureUtil.isNearAnyStructure(w, g, java.util.List.of("PILLAGER_OUTPOST", "WOODLAND_MANSION", "ANCIENT_CITY", "TRAIL_RUINS", "RUINED_PORTAL", "FORTRESS", "BASTION_REMNANT"), 128, false)) maybeAdd(out, seen, g, "grounded");
                    } catch (Throwable ignored) {}
                }
            }
            if (out.size() >= Math.max(16, sampleCount)) break;
        }
        return out;
    }

    private void maybeAdd(List<ResolvedSpawnPosition> out, Set<String> seen, Location loc, String type) {
        if (loc == null || loc.getWorld() == null) return;
        if (tooClose(out, loc, type)) return;
        String key = type + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
        if (!seen.add(key)) return;
        out.add(build(loc, type));
    }

    private ResolvedSpawnPosition build(Location loc, String type) {
        String biomeKey = biomeKey(loc);
        boolean canSeeSky;
        int skyLight;
        int blockLight;
        boolean nearWater;
        try {
            canSeeSky = loc.getWorld().getHighestBlockYAt(loc) <= loc.getBlockY();
        } catch (Throwable ignored) {
            canSeeSky = false;
        }
        try { skyLight = loc.getBlock().getLightFromSky(); } catch (Throwable ignored) { skyLight = 0; }
        try { blockLight = loc.getBlock().getLightFromBlocks(); } catch (Throwable ignored) { blockLight = 0; }
        try { nearWater = NearbyBlockPredicates.hasAnyNearby(loc, Set.of("minecraft:water"), 8); } catch (Throwable ignored) { nearWater = false; }
        Set<String> tags = BiomeTagService.collectTags(biomeKey, loc, type, canSeeSky, nearWater);
        Set<String> nearbyBlocks = collectNearbyBlocks(loc, 6);
        Set<String> archetypes = collectArchetypes(loc, nearbyBlocks, tags, nearWater);
        return new ResolvedSpawnPosition(loc, type, biomeKey, tags, nearbyBlocks, canSeeSky, skyLight, blockLight, nearWater,
                loc.getWorld().hasStorm(), loc.getWorld().isThundering(), archetypes);
    }


    private Set<String> collectArchetypes(Location loc, Set<String> nearbyBlocks, Set<String> tags, boolean nearWater) {
        Set<String> out = new HashSet<>();
        if (nearWater) out.add("water_near");
        if (nearbyBlocks != null) {
            if (nearbyBlocks.contains("pokedemo:pc_machine") || nearbyBlocks.contains("pc_machine") || nearbyBlocks.contains("pokedemo:healer_machine") || nearbyBlocks.contains("healer_machine")) out.add("machine_near");
            if (nearbyBlocks.contains("minecraft:lava") || nearbyBlocks.contains("minecraft:magma_block")) out.add("lava_near");
            if (nearbyBlocks.contains("minecraft:cobweb")) out.add("webs_near");
            if (nearbyBlocks.contains("POKEDEMO_REDSTONE")) out.add("redstone_near");
        }
        try {
            World w = loc.getWorld();
            if (StructureUtil.isNearAnyStructure(w, loc, java.util.List.of("VILLAGE"), 96, false)) out.add("urban_near");
            if (StructureUtil.isNearAnyStructure(w, loc, java.util.List.of("PILLAGER_OUTPOST", "WOODLAND_MANSION", "ANCIENT_CITY", "RUINED_PORTAL", "TRAIL_RUINS", "FORTRESS", "BASTION_REMNANT"), 128, false)) out.add("structure_near");
        } catch (Throwable ignored) {}
        if (tags != null) {
            if (tags.contains("urban")) out.add("urban_near");
            if (tags.contains("cave")) out.add("cave_like");
        }
        return out;
    }

    private String biomeKey(Location loc) {
        try {
            Biome b = loc.getBlock().getBiome();
            String k = b.getKey() != null ? b.getKey().toString().toLowerCase(Locale.ROOT) : null;
            return k != null ? k : b.name().toLowerCase(Locale.ROOT);
        } catch (Throwable ignored) {
            return "unknown";
        }
    }

    private Set<String> collectNearbyBlocks(Location loc, int radius) {
        Set<String> out = new HashSet<>();
        if (loc == null || loc.getWorld() == null) return out;
        World w = loc.getWorld();
        int cx = loc.getBlockX();
        int cy = loc.getBlockY();
        int cz = loc.getBlockZ();
        int step = Math.max(1, radius / 2);
        for (int dx = -radius; dx <= radius; dx += step) {
            for (int dy = -Math.min(2, radius); dy <= Math.min(2, radius); dy++) {
                for (int dz = -radius; dz <= radius; dz += step) {
                    Block b = w.getBlockAt(cx + dx, cy + dy, cz + dz);
                    Material m = b.getType();
                    if (m == null || m.isAir()) continue;
                    String id = m.getKey() != null ? m.getKey().toString().toLowerCase(Locale.ROOT) : m.name().toLowerCase(Locale.ROOT);
                    out.add(id);
                    if (m == Material.NOTE_BLOCK && PokeDemoPlugin.INSTANCE != null && PokeDemoPlugin.INSTANCE.getMachineRegistry() != null) {
                        try {
                            MachineType mt = PokeDemoPlugin.INSTANCE.getMachineRegistry().get(b.getLocation());
                            if (mt != null) {
                                String mid = switch (mt) {
                                    case PC -> "pokedemo:pc_machine";
                                    case HEALER -> "pokedemo:healer_machine";
                                    case PASTURE -> "pokedemo:pasture_machine";
                                    case FOSSIL -> "pokedemo:fossil_machine";
                                    case FOSSIL_ANALYZER -> "pokedemo:fossil_analyzer";
                                    case TRADE -> "pokedemo:trade_machine";
                                    case CLONE -> "pokedemo:clone_machine";
                                };
                                out.add(mid);
                                out.add(mid.substring(mid.indexOf(':') + 1));
                            }
                        } catch (Throwable ignored) {}
                    }
                    out.addAll(EnvironmentAliasService.expandObservedBlock(id));
                    if (out.size() >= 64) return out;
                }
            }
        }
        return out;
    }


    private static final int MIN_POINT_SPACING = 8;

    private boolean tooClose(List<ResolvedSpawnPosition> out, Location loc, String type) {
        if (loc == null) return true;
        for (ResolvedSpawnPosition rsp : out) {
            if (rsp == null || rsp.location == null) continue;
            Location o = rsp.location;
            if (o.getWorld() != loc.getWorld()) continue;
            if (Math.abs(o.getBlockY() - loc.getBlockY()) > 6) continue;
            double dx = o.getX() - loc.getX();
            double dz = o.getZ() - loc.getZ();
            if ((dx * dx + dz * dz) < (MIN_POINT_SPACING * MIN_POINT_SPACING)) return true;
        }
        return false;
    }

    private boolean isBadSpawnFloor(Material m) {
        if (m == null) return true;
        if (!m.isSolid()) return true;
        String name = m.name();
        if (name.endsWith("_LEAVES")) return true;
        return m == Material.NETHER_WART_BLOCK
                || m == Material.WARPED_WART_BLOCK
                || m == Material.SHROOMLIGHT
                || m == Material.MANGROVE_ROOTS
                || m == Material.MUDDY_MANGROVE_ROOTS
                || m == Material.HAY_BLOCK
                || m == Material.CACTUS
                || m == Material.BAMBOO
                || m == Material.BAMBOO_SAPLING;
    }

    private Location grounded(World w, int x, int z, SpawnZone zone) {
        int startY = Math.min(zone.maxY, w.getHighestBlockYAt(x, z) + 2);
        for (int y = startY; y >= zone.minY; y--) {
            Block floor = w.getBlockAt(x, y - 1, z);
            Block b0 = w.getBlockAt(x, y, z);
            Block b1 = w.getBlockAt(x, y + 1, z);
            if (isBadSpawnFloor(floor.getType())) continue;
            if (!b0.isPassable() || !b1.isPassable()) continue;
            if (floor.getType() == Material.LAVA || b0.getType() == Material.LAVA || b1.getType() == Material.LAVA) continue;
            return new Location(w, x + 0.5, y, z + 0.5);
        }
        return null;
    }

    private Location surface(World w, int x, int z, SpawnZone zone) {
        int top = Math.min(zone.maxY, w.getHighestBlockYAt(x, z) + 1);
        for (int y = top; y >= Math.max(zone.minY, top - 20); y--) {
            Block water = w.getBlockAt(x, y, z);
            Block above = w.getBlockAt(x, y + 1, z);
            if (water.getType() == Material.WATER && above.isPassable()) {
                return new Location(w, x + 0.5, y + 0.1, z + 0.5);
            }
        }
        return null;
    }

    private Location submerged(World w, int x, int z, SpawnZone zone) {
        int top = Math.min(zone.maxY, w.getHighestBlockYAt(x, z));
        for (int y = top; y >= Math.max(zone.minY, top - 24); y--) {
            Block b = w.getBlockAt(x, y, z);
            Block up = w.getBlockAt(x, y + 1, z);
            if (b.getType() == Material.WATER && up.getType() == Material.WATER) {
                return new Location(w, x + 0.5, y + 0.2, z + 0.5);
            }
        }
        return null;
    }

    private Location seafloor(World w, int x, int z, SpawnZone zone) {
        int top = Math.min(zone.maxY, w.getHighestBlockYAt(x, z));
        for (int y = top; y >= Math.max(zone.minY, top - 24); y--) {
            Block floor = w.getBlockAt(x, y - 1, z);
            Block b = w.getBlockAt(x, y, z);
            Block up = w.getBlockAt(x, y + 1, z);
            if (b.getType() == Material.WATER && up.getType() == Material.WATER && floor.getType().isSolid()) {
                return new Location(w, x + 0.5, y + 0.1, z + 0.5);
            }
        }
        return null;
    }

    private Location cave(World w, int x, int z, SpawnZone zone) {
        int startY = zone.maxY;
        for (int y = startY; y >= zone.minY; y--) {
            Block floor = w.getBlockAt(x, y - 1, z);
            Block b0 = w.getBlockAt(x, y, z);
            Block b1 = w.getBlockAt(x, y + 1, z);
            if (isBadSpawnFloor(floor.getType())) continue;
            if (!b0.isPassable() || !b1.isPassable()) continue;
            if (b0.getLightFromSky() > 1) continue;
            try {
                if (w.getHighestBlockYAt(x, z) <= y) continue;
            } catch (Throwable ignored) {}
            return new Location(w, x + 0.5, y, z + 0.5);
        }
        return null;
    }
}
