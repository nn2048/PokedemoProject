package win.pokedemo;

import org.bukkit.Location;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Centralized plugin-side biome/environment tag derivation.
 * This is intentionally approximate: we keep Cobblemon-style semantic tags,
 * but derive them from Bukkit biome keys + local environment.
 */
public final class BiomeTagService {
    private static boolean initialized = false;
    private BiomeTagService() {}

    public static synchronized void initialize(PokeDemoPlugin plugin) {
        if (initialized) return;
        initialized = true;
        EnvironmentAliasService.initialize(plugin);
    }

    public static Set<String> collectTags(String biomeKey,
                                          Location loc,
                                          String positionType,
                                          boolean canSeeSky,
                                          boolean nearWater) {
        Set<String> tags = new HashSet<>();
        String key = biomeKey == null ? "unknown" : biomeKey.trim().toLowerCase(Locale.ROOT);
        String pos = positionType == null ? "grounded" : positionType.trim().toLowerCase(Locale.ROOT);

        add(tags, key);
        for (String alias : EnvironmentAliasService.getBiomeAliases(key)) add(tags, alias);
        // central Cobblemon-like tags
        for (String t : new String[]{
                "#cobblemon:is_overworld", "#cobblemon:is_nether", "#cobblemon:is_end",
                "#minecraft:is_overworld", "#minecraft:is_nether", "#minecraft:is_end",
                "#cobblemon:is_ocean", "#cobblemon:is_cold_ocean", "#cobblemon:is_frozen_ocean", "#cobblemon:is_warm_ocean",
                "#cobblemon:is_freshwater", "#cobblemon:is_river", "#cobblemon:is_coast",
                "#cobblemon:is_beach", "#cobblemon:is_swamp", "#cobblemon:is_forest",
                "#cobblemon:is_jungle", "#cobblemon:is_tropical_island", "#cobblemon:is_grassland", "#cobblemon:is_plains",
                "#cobblemon:is_hills", "#cobblemon:is_mountain", "#cobblemon:is_arid", "#cobblemon:is_badlands",
                "#cobblemon:is_desert", "#cobblemon:is_savanna", "#cobblemon:is_freezing", "#cobblemon:is_tundra",
                "#cobblemon:is_taiga", "#cobblemon:is_temperate", "#cobblemon:is_lush", "#cobblemon:is_bamboo",
                "#cobblemon:is_deep_dark", "#cobblemon:is_spooky", "#cobblemon:is_magical", "#cobblemon:is_mushroom",
                "#cobblemon:is_floral", "#cobblemon:is_dripstone", "#cobblemon:is_volcanic", "#cobblemon:is_thermal",
                "#cobblemon:is_sky", "#cobblemon:nether/is_forest", "#cobblemon:nether/is_basalt",
                "#cobblemon:nether/is_desert", "#cobblemon:nether/is_warped", "#cobblemon:nether/is_crimson", "#cobblemon:nether/is_fungus"
        }) {
            if (BiomeTagPredicates.matches(t, key)) add(tags, t);
        }

        // simpler aliases commonly used by plugin rules.
        if (key.contains("ocean") || key.contains("sea")) {
            add(tags, "ocean"); add(tags, "aquatic"); add(tags, "water");
            if (key.contains("warm")) add(tags, "warm_ocean");
            if (key.contains("cold")) add(tags, "cold_ocean");
            if (key.contains("frozen")) add(tags, "frozen_ocean");
        }
        if (key.contains("river") || key.contains("lake") || key.contains("swamp") || key.contains("marsh")) {
            add(tags, "freshwater"); add(tags, "water");
        }
        if (key.contains("forest") || key.contains("grove") || key.contains("woods") || key.contains("taiga") || key.contains("birch")) add(tags, "forest");
        if (key.contains("jungle") || key.contains("bamboo") || key.contains("tropical")) add(tags, "jungle");
        if (key.contains("plains") || key.contains("meadow") || key.contains("grass") || key.contains("forest") || key.contains("grove") || key.contains("birch") || key.contains("river") || key.contains("beach")) { add(tags, "plains"); add(tags, "grassland"); add(tags, "temperate"); add(tags, "grassy"); }
        if (key.contains("mountain") || key.contains("peak") || key.contains("hill") || key.contains("slope") || key.contains("cliff") || key.contains("highland")) add(tags, "mountain");
        if (key.contains("desert") || key.contains("savanna") || key.contains("mesa") || key.contains("badlands")) { add(tags, "arid"); if (key.contains("badlands") || key.contains("mesa")) add(tags, "badlands"); }
        if (key.contains("snow") || key.contains("ice") || key.contains("frozen") || key.contains("taiga") || key.contains("cold")) { add(tags, "freezing"); if (key.contains("snow") || key.contains("ice") || key.contains("frozen")) add(tags, "tundra"); }
        if (key.contains("mushroom")) add(tags, "mushroom");
        if (key.contains("flower") || key.contains("sunflower") || key.contains("blossom") || key.contains("meadow")) add(tags, "floral");
        if (key.contains("swamp") || key.contains("mangrove") || key.contains("marsh")) add(tags, "swamp");
        if (key.contains("river")) add(tags, "river");
        if (key.contains("bamboo")) add(tags, "bamboo");
        if (key.contains("beach") || key.contains("coast") || key.contains("shore")) add(tags, "shore");
        if (key.contains("deep_dark") || key.contains("dripstone") || key.contains("lush_caves")) add(tags, "cave");
        if (key.contains("dark_forest") || key.contains("mushroom")) add(tags, "spooky");
        if (!(key.contains("nether") || key.contains("the_end") || key.contains("end"))) add(tags, "overworld");
        if (key.contains("nether") || key.contains("crimson") || key.contains("warped") || key.contains("basalt") || key.contains("soul_sand")) { add(tags, "nether");
            if (key.contains("warped")) { add(tags, "warped"); add(tags, "nether_fungus"); add(tags, "nether_forest"); }
            if (key.contains("crimson")) { add(tags, "crimson"); add(tags, "nether_fungus"); add(tags, "nether_forest"); }
            if (key.contains("basalt")) add(tags, "nether_basalt");
            if (key.contains("soul_sand") || key.contains("nether_wastes")) add(tags, "nether_desert");
        }
        if (key.contains("the_end") || key.endsWith(":end") || key.contains("_end")) add(tags, "end");
        if (key.contains("lava") || key.contains("basalt") || key.contains("magma")) add(tags, "thermal");

        if (nearWater) add(tags, "near_water");
        if (canSeeSky) add(tags, "can_see_sky"); else add(tags, "underground");
        add(tags, "position:" + pos);
        if ("cave".equals(pos)) add(tags, "cave");
        if ("surface".equals(pos) || "submerged".equals(pos) || "seafloor".equals(pos)) add(tags, "water");
        EnvironmentAliasService.applyBiomeExtraTags(tags, key, loc, pos);
        return tags;
    }

    public static boolean matches(String tag, String biomeKey, Set<String> cachedTags) {
        if (tag == null) return false;
        String t = normalize(tag);
        if (cachedTags != null && cachedTags.contains(t)) return true;
        if (cachedTags != null && t.startsWith("#") && cachedTags.contains(t.substring(1))) return true;
        if (cachedTags != null && !t.startsWith("#") && cachedTags.contains("#" + t)) return true;
        String key = biomeKey == null ? "unknown" : biomeKey.trim().toLowerCase(Locale.ROOT);
        if (t.equals(key) || key.endsWith(t)) return true;
        return BiomeTagPredicates.matches(tag, key);
    }

    public static String normalize(String tag) {
        String t = tag == null ? "" : tag.trim().toLowerCase(Locale.ROOT);
        if (t.isBlank()) return t;
        if (t.startsWith("minecraft:")) return t;
        return t;
    }

    private static void add(Set<String> tags, String tag) {
        String n = normalize(tag);
        if (!n.isBlank()) tags.add(n);
        if (n.startsWith("#") && n.length() > 1) tags.add(n.substring(1));
    }
}
