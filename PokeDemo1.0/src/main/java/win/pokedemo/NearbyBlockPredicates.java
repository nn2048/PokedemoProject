package win.pokedemo;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.plugin.Plugin;
import org.bukkit.Bukkit;

import java.util.Locale;
import java.util.Set;

/**
 * Approximation of Cobblemon's neededNearbyBlocks.
 *
 * Supported values:
 * - "minecraft:<id>" (e.g. minecraft:sugar_cane)
 * - "#cobblemon:concrete_blocks" (any colored concrete)
 * - "POKEDEMO_PC" (our plugin's PC block)
 * - "POKEDEMO_RIPE_BERRY_BUSH" (ripe berry bush, as decided for Snorlax)
 * - "POKEDEMO_REDSTONE" (any redstone-related block nearby)
 */
public class NearbyBlockPredicates {

    public static boolean hasAnyNearby(Location loc, Set<String> predicates, int radius) {
        if (loc == null || loc.getWorld() == null || predicates == null || predicates.isEmpty()) return false;
        World w = loc.getWorld();
        int cx = loc.getBlockX();
        int cy = loc.getBlockY();
        int cz = loc.getBlockZ();
        int r = Math.max(1, radius);
        int yMin = Math.max(w.getMinHeight(), cy - 4);
        int yMax = Math.min(w.getMaxHeight() - 1, cy + 4);

        for (String raw : predicates) {
            if (raw == null) continue;
            String p = raw.trim();
            if (p.isEmpty()) continue;
            if (p.equalsIgnoreCase("POKEDEMO_PC")) {
                if (scanForPc(w, cx, cy, cz, r, yMin, yMax)) return true;
                continue;
            }
            if (p.equalsIgnoreCase("POKEDEMO_RIPE_BERRY_BUSH")) {
                if (scanForRipeBerryBush(w, cx, cy, cz, r, yMin, yMax)) return true;
                continue;
            }
            if (p.equalsIgnoreCase("POKEDEMO_REDSTONE")) {
                if (scanForRedstone(w, cx, cy, cz, r, yMin, yMax)) return true;
                continue;
            }
            if (p.startsWith("#")) {
                if (scanForTag(w, cx, cy, cz, r, yMin, yMax, p)) return true;
                continue;
            }
            if (scanForMaterial(w, cx, cy, cz, r, yMin, yMax, p)) return true;
        }
        return false;
    }

    private static boolean scanForMaterial(World w, int cx, int cy, int cz, int r, int yMin, int yMax, String id) {
        String key = id.toLowerCase(Locale.ROOT);
        if (key.startsWith("minecraft:")) key = key.substring("minecraft:".length());
        Material m;
        try {
            m = Material.matchMaterial(key);
        } catch (Throwable t) {
            m = null;
        }
        if (m == null) return false;

        for (int x = cx - r; x <= cx + r; x++) {
            for (int z = cz - r; z <= cz + r; z++) {
                for (int y = yMin; y <= yMax; y++) {
                    if (w.getBlockAt(x, y, z).getType() == m) return true;
                }
            }
        }
        return false;
    }

    private static boolean scanForTag(World w, int cx, int cy, int cz, int r, int yMin, int yMax, String tag) {
        String t = tag.toLowerCase(Locale.ROOT);
        // Only one tag currently needed for Gen1 replication.
        if (t.endsWith("cobblemon:concrete_blocks") || t.endsWith("cobblemon:concrete") || t.endsWith("concrete_blocks")) {
            for (int x = cx - r; x <= cx + r; x++) {
                for (int z = cz - r; z <= cz + r; z++) {
                    for (int y = yMin; y <= yMax; y++) {
                        Material m = w.getBlockAt(x, y, z).getType();
                        String n = m.name();
                        if (n.endsWith("_CONCRETE")) return true;
                    }
                }
            }
            return false;
        }
        if (t.endsWith("cobblemon:saccharine_trees") || t.endsWith("saccharine_trees")) {
            for (int x = cx - r; x <= cx + r; x++) {
                for (int z = cz - r; z <= cz + r; z++) {
                    for (int y = yMin; y <= yMax; y++) {
                        Material m = w.getBlockAt(x, y, z).getType();
                        String n = m.name();
                        if (n.endsWith("_LEAVES") || n.endsWith("_LOG") || n.endsWith("_WOOD")) return true;
                    }
                }
            }
            return false;
        }
        if (t.endsWith("minecraft:beehives") || t.endsWith("beehives")) {
            return scanForMaterial(w, cx, cy, cz, r, yMin, yMax, "minecraft:bee_nest") || scanForMaterial(w, cx, cy, cz, r, yMin, yMax, "minecraft:beehive");
        }
        if (t.endsWith("cobblemon:flowers") || t.endsWith("flowers")) {
            for (int x = cx - r; x <= cx + r; x++) {
                for (int z = cz - r; z <= cz + r; z++) {
                    for (int y = yMin; y <= yMax; y++) {
                        Material m = w.getBlockAt(x, y, z).getType();
                        String n = m.name();
                        if (n.endsWith("_TULIP") || n.endsWith("_ORCHID") || n.endsWith("_DAISY") || n.endsWith("_POPPY") || n.endsWith("_ROSE") || n.endsWith("_LILAC") || n.endsWith("_PEONY") || n.endsWith("_BLOSSOM") || n.endsWith("_FLOWER") || m == Material.DANDELION || m == Material.CORNFLOWER || m == Material.ALLIUM || m == Material.AZURE_BLUET || m == Material.SUNFLOWER || m == Material.LILY_OF_THE_VALLEY || m == Material.OXEYE_DAISY) return true;
                    }
                }
            }
            return false;
        }
        // Unknown tag: fail closed.
        return false;
    }

    private static boolean scanForPc(World w, int cx, int cy, int cz, int r, int yMin, int yMax) {
        // Our plugin PC is implemented as a Machine block.
        // Machines are persisted in machines.yml; resolve via MachineRegistry.
        MachineRegistry reg = null;
        try {
            Plugin pl = Bukkit.getPluginManager().getPlugin("PokeDemo");
            if (pl instanceof PokeDemoPlugin pd) reg = pd.getMachineRegistry();
        } catch (Throwable ignored) {}
        for (int x = cx - r; x <= cx + r; x++) {
            for (int z = cz - r; z <= cz + r; z++) {
                for (int y = yMin; y <= yMax; y++) {
                    Block b = w.getBlockAt(x, y, z);
                    if (b.getType() != Material.NOTE_BLOCK) continue;
                    try {
                        if (reg != null && reg.get(b.getLocation()) == MachineType.PC) return true;
                    } catch (Throwable ignored) {}
                }
            }
        }
        return false;
    }

    private static boolean scanForRipeBerryBush(World w, int cx, int cy, int cz, int r, int yMin, int yMax) {
        for (int x = cx - r; x <= cx + r; x++) {
            for (int z = cz - r; z <= cz + r; z++) {
                for (int y = yMin; y <= yMax; y++) {
                    Block b = w.getBlockAt(x, y, z);
                    if (b.getType() != Material.SWEET_BERRY_BUSH) continue;
                    try {
                        if (b.getBlockData() instanceof Ageable age) {
                            if (age.getAge() >= age.getMaximumAge()) return true;
                        }
                    } catch (Throwable ignored) { }
                }
            }
        }
        return false;
    }

    private static boolean scanForRedstone(World w, int cx, int cy, int cz, int r, int yMin, int yMax) {
        for (int x = cx - r; x <= cx + r; x++) {
            for (int z = cz - r; z <= cz + r; z++) {
                for (int y = yMin; y <= yMax; y++) {
                    Material m = w.getBlockAt(x, y, z).getType();
                    String n = m.name();
                    if (n.contains("REDSTONE")) return true;
                    if (n.endsWith("_REPEATER") || n.endsWith("_COMPARATOR")) return true;
                    if (n.endsWith("_PISTON") || n.endsWith("_PISTON_HEAD") || n.endsWith("_PISTON_EXTENSION")) return true;
                    if (n.endsWith("_OBSERVER")) return true;
                    if (n.endsWith("_DISPENSER") || n.endsWith("_DROPPER")) return true;
                    if (n.endsWith("_HOPPER")) return true;
                    if (n.endsWith("_TARGET")) return true;
                    if (m == Material.REDSTONE_BLOCK || m == Material.REDSTONE_ORE || m == Material.DEEPSLATE_REDSTONE_ORE) return true;
                }
            }
        }
        return false;
    }
}
