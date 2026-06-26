package win.pokedemo;

import java.util.Locale;

/**
 * Approximate Cobblemon biome-tag matcher for a pure plugin environment.
 * The goal is to cover the tags actually used by Cobblemon spawn pools, especially
 * Nether / End / river / thermal / badlands / temperate style groupings.
 */
public class BiomeTagPredicates {

    public static boolean supports(String tag) {
        if (tag == null || tag.isBlank()) return false;
        String t = normalize(tag);
        return t.endsWith(":is_overworld")
                || t.endsWith(":is_nether")
                || t.endsWith(":is_end")
                || t.endsWith(":is_ocean")
                || t.endsWith(":is_cold_ocean")
                || t.endsWith(":is_frozen_ocean")
                || t.endsWith(":is_warm_ocean")
                || t.endsWith(":is_freshwater")
                || t.endsWith(":is_river")
                || t.endsWith(":is_coast")
                || t.endsWith(":is_beach")
                || t.endsWith(":is_swamp")
                || t.endsWith(":is_forest")
                || t.endsWith(":is_jungle")
                || t.endsWith(":is_tropical_island")
                || t.endsWith(":is_grassland")
                || t.endsWith(":is_plains")
                || t.endsWith(":is_hills")
                || t.endsWith(":is_mountain")
                || t.endsWith(":is_arid")
                || t.endsWith(":is_desert")
                || t.endsWith(":is_savanna")
                || t.endsWith(":is_badlands")
                || t.endsWith(":is_freezing")
                || t.endsWith(":is_taiga")
                || t.endsWith(":is_tundra")
                || t.endsWith(":is_temperate")
                || t.endsWith(":is_lush")
                || t.endsWith(":is_bamboo")
                || t.endsWith(":is_deep_dark")
                || t.endsWith(":is_spooky")
                || t.endsWith(":is_magical")
                || t.endsWith(":is_mushroom")
                || t.endsWith(":is_floral")
                || t.endsWith(":is_dripstone")
                || t.endsWith(":is_volcanic")
                || t.endsWith(":is_thermal")
                || t.endsWith(":is_sky")
                || t.contains(":nether/is_");
    }

    public static boolean matches(String tag, String biomeKey) {
        if (tag == null || biomeKey == null) return false;
        String t = normalize(tag);
        String b = biomeKey.trim().toLowerCase(Locale.ROOT);

        if (t.endsWith(":is_overworld")) return !(b.contains("nether") || b.contains("end") || b.contains("the_end"));
        if (t.endsWith(":is_nether")) return b.contains("nether") || b.contains("crimson") || b.contains("warped") || b.contains("soul_sand") || b.contains("basalt");
        if (t.endsWith(":is_end")) return b.contains("the_end") || b.endsWith(":end") || b.contains("end_") || b.contains("end");

        if (t.endsWith(":is_ocean") || t.endsWith(":is_cold_ocean") || t.endsWith(":is_frozen_ocean") || t.endsWith(":is_warm_ocean")) return b.contains("ocean");
        if (t.endsWith(":is_freshwater")) return containsAny(b, "river", "lake", "swamp", "marsh", "water");
        if (t.endsWith(":is_river")) return b.contains("river");
        if (t.endsWith(":is_coast") || t.endsWith(":is_beach")) return containsAny(b, "beach", "coast", "shore", "stony_shore");
        if (t.endsWith(":is_swamp")) return containsAny(b, "swamp", "mangrove", "marsh");
        if (t.endsWith(":is_forest")) return containsAny(b, "forest", "grove", "woods", "birch");
        if (t.endsWith(":is_jungle") || t.endsWith(":is_tropical_island")) return containsAny(b, "jungle", "bamboo", "tropical");
        if (t.endsWith(":is_grassland") || t.endsWith(":is_plains")) return containsAny(b, "plains", "meadow", "grass");
        if (t.endsWith(":is_hills") || t.endsWith(":is_mountain")) return containsAny(b, "hill", "mountain", "peak", "slope", "cliff", "highland", "windswept", "grove", "meadow", "stony_peaks", "jagged_peaks", "frozen_peaks");
        if (t.endsWith(":is_arid") || t.endsWith(":is_desert") || t.endsWith(":is_savanna")) return containsAny(b, "desert", "savanna", "badlands", "mesa");
        if (t.endsWith(":is_badlands")) return containsAny(b, "badlands", "mesa");
        if (t.endsWith(":is_freezing") || t.endsWith(":is_taiga") || t.endsWith(":is_tundra")) return containsAny(b, "snow", "ice", "frozen", "taiga", "cold");
        if (t.endsWith(":is_temperate")) return containsAny(b, "plains", "forest", "grove", "meadow", "birch", "river", "beach", "stony_shore");
        if (t.endsWith(":is_lush")) return containsAny(b, "lush", "jungle", "moss", "fern", "meadow", "garden");
        if (t.endsWith(":is_bamboo")) return b.contains("bamboo");
        if (t.endsWith(":is_deep_dark")) return b.contains("deep_dark");
        if (t.endsWith(":is_spooky") || t.endsWith(":is_magical")) return containsAny(b, "dark_forest", "mushroom", "spooky", "magical", "soul_sand");
        if (t.endsWith(":is_mushroom")) return b.contains("mushroom");
        if (t.endsWith(":is_floral")) return containsAny(b, "flower", "sunflower", "blossom", "meadow", "garden");
        if (t.endsWith(":is_dripstone")) return b.contains("dripstone");
        if (t.endsWith(":is_volcanic") || t.endsWith(":is_thermal")) return containsAny(b, "basalt", "lava", "volcan", "magma");
        if (t.endsWith(":is_sky")) return false;

        if (t.contains(":nether/is_")) {
            if (t.endsWith("/is_forest") || t.endsWith("/is_fungus")) return containsAny(b, "crimson_forest", "warped_forest", "warped", "crimson");
            if (t.endsWith("/is_basalt")) return b.contains("basalt");
            if (t.endsWith("/is_desert")) return containsAny(b, "nether_wastes", "soul_sand_valley", "soul_sand");
            if (t.endsWith("/is_warped")) return b.contains("warped");
            if (t.endsWith("/is_crimson")) return b.contains("crimson");
        }

        return false;
    }

    private static String normalize(String tag) {
        String t = tag.trim().toLowerCase(Locale.ROOT);
        if (t.startsWith("#")) t = t.substring(1);
        return t;
    }

    private static boolean containsAny(String s, String... needles) {
        if (s == null) return false;
        for (String n : needles) if (n != null && s.contains(n)) return true;
        return false;
    }
}
