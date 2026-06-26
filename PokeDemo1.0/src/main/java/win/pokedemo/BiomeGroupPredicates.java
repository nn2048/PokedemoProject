package win.pokedemo;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.Locale;

/**
 * Simple, server-core-friendly biome grouping.
 *
 * We intentionally do NOT rely on Cobblemon's biome tag registry here.
 * Instead we match by vanilla biome keys / names (string contains checks)
 * and world environments.
 */
public class BiomeGroupPredicates {

    public static boolean matches(String groupId, World world, String biomeKeyLower, Location loc) {
        if (groupId == null) return false;
        String g = groupId.toLowerCase(Locale.ROOT);

        // Dimension groups
        if (g.equals("nether")) return world != null && world.getEnvironment() == World.Environment.NETHER;
        if (g.equals("end")) return world != null && world.getEnvironment() == World.Environment.THE_END;

        // Cave: low skylight OR deep Y.
        if (g.equals("cave")) {
            try {
                if (loc != null) {
                    int sl = loc.getBlock().getLightFromSky();
                    if (sl <= 1) return true;
                    if (loc.getBlockY() <= 40) return true;
                }
            } catch (Throwable ignored) {}
            return false;
        }

        // Urban: villages are handled via nearStructure, but keep a loose group for convenience.
        if (g.equals("urban")) {
            // Use common biome keywords where villages appear more often.
            return containsAny(biomeKeyLower, "plains", "savanna", "desert", "taiga");
        }

        // Water grouping
        if (g.equals("ocean")) return containsAny(biomeKeyLower, "ocean");
        if (g.equals("river_lake")) return containsAny(biomeKeyLower, "river", "lake");
        if (g.equals("swamp")) return containsAny(biomeKeyLower, "swamp", "mangrove");
        if (g.equals("jungle")) return containsAny(biomeKeyLower, "jungle", "bamboo");
        if (g.equals("forest")) return containsAny(biomeKeyLower, "forest", "birch", "dark_forest", "taiga");
        if (g.equals("plains")) return containsAny(biomeKeyLower, "plains", "meadow");
        if (g.equals("mountain")) return containsAny(biomeKeyLower, "mountain", "windswept", "stony_peaks", "jagged_peaks", "frozen_peaks", "grove", "meadow");
        if (g.equals("arid")) return containsAny(biomeKeyLower, "desert", "badlands", "savanna");
        if (g.equals("cold")) return containsAny(biomeKeyLower, "snow", "frozen", "ice", "cold");

        // Unknown group
        return false;
    }

    private static boolean containsAny(String s, String... needles) {
        if (s == null) return false;
        for (String n : needles) {
            if (n == null) continue;
            if (s.contains(n)) return true;
        }
        return false;
    }
}
