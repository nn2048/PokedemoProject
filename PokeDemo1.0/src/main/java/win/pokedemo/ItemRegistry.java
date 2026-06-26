package win.pokedemo;

import org.bukkit.Material;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Central registry for PokeDemo items (Gen1 first).
 *
 * Roadmap:
 *  - Load items from Showdown items.js -> items_imported.json
 *  - Generate resourcepack CMD mapping + keep in sync
 *  - Implement category-based handlers (medicine/status/revive/balls/TMs)
 */
public class ItemRegistry {

    // Canonical CustomModelData mapping shared with the resource pack.
    // Keys look like "paper:held_<id>".
    private static final Map<String, Integer> CMD_MAP = loadCmdMap();

    private static Map<String, Integer> loadCmdMap() {
        try (InputStream in = ItemRegistry.class.getClassLoader().getResourceAsStream("pokedemo_custom_model_data_map.json")) {
            if (in == null) return Map.of();
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            Map<String, Integer> out = new HashMap<>();
            com.google.gson.JsonObject obj = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
            for (Map.Entry<String, com.google.gson.JsonElement> e : obj.entrySet()) {
                if (e.getValue() != null && e.getValue().isJsonPrimitive()) {
                    try { out.put(e.getKey(), e.getValue().getAsInt()); } catch (Exception ignored) {}
                }
            }
            return out;
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private static int cmdOr(int fallback, String key) {
        Integer v = CMD_MAP.get(key);
        return v != null ? v : fallback;
    }

    private final Map<String, ItemDef> defsById = new HashMap<>();

    public ItemRegistry() {
        // Minimal seed definitions (Gen1 starters for the upcoming item system).
        // CMD values are placeholders for now; we'll map them to the resourcepack later.
        register(new ItemDef("potion", ItemType.MEDICINE, Material.PAPER, 30001, "item.potion",
                true, true, 300, Map.of("heal", 20)));
        register(new ItemDef("super_potion", ItemType.MEDICINE, Material.PAPER, 30002, "item.super_potion",
                true, true, 700, Map.of("heal", 50)));
register(new ItemDef("hyper_potion", ItemType.MEDICINE, Material.PAPER, 30003, "item.hyper_potion",
                true, true, 1200, Map.of("heal", 200)));
        register(new ItemDef("max_potion", ItemType.MEDICINE, Material.PAPER, 30004, "item.max_potion",
                true, true, 2500, Map.of("heal_full", true)));
        register(new ItemDef("full_restore", ItemType.MEDICINE, Material.PAPER, 30005, "item.full_restore",
                true, true, 3000, Map.of("heal_full", true, "cure_all", true)));
        register(new ItemDef("full_heal", ItemType.STATUS_CURE, Material.PAPER, 30015, "item.full_heal",
                true, true, 600, Map.of("cure", "all")));
        register(new ItemDef("burn_heal", ItemType.STATUS_CURE, Material.PAPER, 30012, "item.burn_heal",
                true, true, 250, Map.of("cure", "burn")));
        register(new ItemDef("ice_heal", ItemType.STATUS_CURE, Material.PAPER, 30013, "item.ice_heal",
                true, true, 250, Map.of("cure", "freeze")));
        register(new ItemDef("awakening", ItemType.STATUS_CURE, Material.PAPER, 30011, "item.awakening",
                true, true, 250, Map.of("cure", "sleep")));
        register(new ItemDef("paralyze_heal", ItemType.STATUS_CURE, Material.PAPER, 30014, "item.paralyze_heal",
                true, true, 200, Map.of("cure", "paralysis")));
        register(new ItemDef("ether", ItemType.PP_RESTORE, Material.PAPER, 30030, "item.ether",
                true, true, 1200, Map.of("pp", 10)));
        register(new ItemDef("max_ether", ItemType.PP_RESTORE, Material.PAPER, 30031, "item.max_ether",
                true, true, 2000, Map.of("pp_full", true)));
        register(new ItemDef("elixir", ItemType.PP_RESTORE, Material.PAPER, 30032, "item.elixir",
                true, true, 3000, Map.of("pp_all", 10)));
        register(new ItemDef("max_elixir", ItemType.PP_RESTORE, Material.PAPER, 30033, "item.max_elixir",
                true, true, 4500, Map.of("pp_all_full", true)));
        // Vending machine drinks (Gen1)
        register(new ItemDef("fresh_water", ItemType.MEDICINE, Material.PAPER, 30006, "item.fresh_water",
                true, true, 200, Map.of("heal", 50)));
        register(new ItemDef("soda_pop", ItemType.MEDICINE, Material.PAPER, 30007, "item.soda_pop",
                true, true, 300, Map.of("heal", 60)));
        register(new ItemDef("lemonade", ItemType.MEDICINE, Material.PAPER, 30008, "item.lemonade",
                true, true, 350, Map.of("heal", 80)));

        // PP Up / PP Max (per-move max PP increase)
        register(new ItemDef("pp_up", ItemType.PP_RESTORE, Material.PAPER, 30034, "item.pp_up",
                true, true, 9800, Map.of("pp_up", true)));
        register(new ItemDef("pp_max", ItemType.PP_RESTORE, Material.PAPER, 30035, "item.pp_max",
                true, true, 0, Map.of("pp_max", true)));

        register(new ItemDef("antidote", ItemType.STATUS_CURE, Material.PAPER, 30010, "item.antidote",
                true, true, 100, Map.of("cure", "poison")));
        register(new ItemDef("revive", ItemType.REVIVE, Material.PAPER, 30020, "item.revive",
                true, true, 1500, Map.of("revive", 0.5)));
        register(new ItemDef("max_revive", ItemType.REVIVE, Material.PAPER, 30021, "item.max_revive",
                true, true, 4000, Map.of("revive", 1.0)));

        // Poke Balls (Gen1 basic)
        register(new ItemDef("poke_ball", ItemType.BALL, Material.SNOWBALL, 32001, "item.poke_ball",
                false, true, 200, Map.of("ballBonus", 1.0, "master", false)));
        register(new ItemDef("great_ball", ItemType.BALL, Material.SNOWBALL, 32002, "item.great_ball",
                false, true, 600, Map.of("ballBonus", 1.5, "master", false)));
        register(new ItemDef("ultra_ball", ItemType.BALL, Material.SNOWBALL, 32003, "item.ultra_ball",
                false, true, 1200, Map.of("ballBonus", 2.0, "master", false)));
        register(new ItemDef("master_ball", ItemType.BALL, Material.SNOWBALL, 32004, "item.master_ball",
                false, true, 0, Map.of("ballBonus", 255.0, "master", true)));

        // Battle items (Gen1 basic)
        register(new ItemDef("x_attack", ItemType.BATTLE, Material.PAPER, 30101, "item.x_attack",
                false, true, 500, Map.of("stage", "atk", "delta", 1)));
        register(new ItemDef("x_defend", ItemType.BATTLE, Material.PAPER, 30102, "item.x_defend",
                false, true, 550, Map.of("stage", "def", "delta", 1)));
        register(new ItemDef("x_speed", ItemType.BATTLE, Material.PAPER, 30103, "item.x_speed",
                false, true, 350, Map.of("stage", "spe", "delta", 1)));
        register(new ItemDef("x_accuracy", ItemType.BATTLE, Material.PAPER, 30104, "item.x_accuracy",
                false, true, 950, Map.of("stage", "accuracy", "delta", 1)));
        register(new ItemDef("x_special", ItemType.BATTLE, Material.PAPER, 30107, "item.x_special",
                false, true, 350, Map.of("stage", "special", "delta", 1)));
        register(new ItemDef("x_evasion", ItemType.BATTLE, Material.PAPER, 30108, "item.x_evasion",
                false, true, 350, Map.of("stage", "evasion", "delta", 1)));
        register(new ItemDef("poke_doll", ItemType.BATTLE, Material.PAPER, 30109, "item.poke_doll",
                false, true, 1000, Map.of("escape", true)));

        // ---------------- HELD ITEMS (携带物品) ----------------
        // NOTE: Many held item effects are implemented in HeldItemEffects (battle hooks).
        register(new ItemDef("leftovers", ItemType.HELD, Material.PAPER, cmdOr(31000, "paper:held_leftovers"), "item.leftovers", false, true, 1000, Map.of("held", true)));
        register(new ItemDef("black_sludge", ItemType.HELD, Material.PAPER, cmdOr(31001, "paper:held_black_sludge"), "item.black_sludge", false, true, 1000, Map.of("held", true)));
        register(new ItemDef("quick_claw", ItemType.HELD, Material.PAPER, cmdOr(31002, "paper:held_quick_claw"), "item.quick_claw", false, true, 1000, Map.of("held", true)));
        register(new ItemDef("lagging_tail", ItemType.HELD, Material.PAPER, cmdOr(31003, "paper:held_lagging_tail"), "item.lagging_tail", false, true, 1000, Map.of("held", true)));
        register(new ItemDef("full_incense", ItemType.HELD, Material.PAPER, cmdOr(31004, "paper:held_full_incense"), "item.full_incense", false, true, 1000, Map.of("held", true)));

        register(new ItemDef("kings_rock", ItemType.HELD, Material.PAPER, cmdOr(31005, "paper:held_kings_rock"), "item.kings_rock", false, true, 1000, Map.of("held", true)));
        register(new ItemDef("razor_fang", ItemType.HELD, Material.PAPER, cmdOr(31006, "paper:held_razor_fang"), "item.razor_fang", false, true, 1000, Map.of("held", true)));

        register(new ItemDef("bright_powder", ItemType.HELD, Material.PAPER, cmdOr(31007, "paper:held_bright_powder"), "item.bright_powder", false, true, 1000, Map.of("held", true)));
        register(new ItemDef("lax_incense", ItemType.HELD, Material.PAPER, cmdOr(31008, "paper:held_lax_incense"), "item.lax_incense", false, true, 1000, Map.of("held", true)));

        // Utility held items commonly referenced by later mechanics
        register(new ItemDef("light_clay", ItemType.HELD, Material.PAPER, cmdOr(31090, "paper:held_light_clay"), "item.light_clay", false, true, 1000, Map.of("held", true)));
        register(new ItemDef("mental_herb", ItemType.HELD, Material.PAPER, cmdOr(31091, "paper:held_mental_herb"), "item.mental_herb", false, true, 1000, Map.of("held", true)));
        register(new ItemDef("fairy_gem", ItemType.HELD, Material.PAPER, cmdOr(31092, "paper:held_fairy_gem"), "item.fairy_gem", false, true, 1000, Map.of("held", true)));
        register(new ItemDef("smoke_ball", ItemType.HELD, Material.PAPER, cmdOr(31093, "paper:held_smoke_ball"), "item.smoke_ball", false, true, 1000, Map.of("held", true)));
        register(new ItemDef("shed_shell", ItemType.HELD, Material.PAPER, cmdOr(31094, "paper:held_shed_shell"), "item.shed_shell", false, true, 1000, Map.of("held", true)));

        // Evolution items (we treat them as "use on Pokémon" items in the plugin version)
        register(new ItemDef("up_grade", ItemType.MISC, Material.PAPER, cmdOr(31200, "paper:held_up_grade"), "item.up_grade", true, true, 0, Map.of("evo_item", "up_grade")));
        register(new ItemDef("dubious_disc", ItemType.MISC, Material.PAPER, cmdOr(31201, "paper:held_dubious_disc"), "item.dubious_disc", true, true, 0, Map.of("evo_item", "dubious_disc")));
        register(new ItemDef("protector", ItemType.MISC, Material.PAPER, cmdOr(31202, "paper:held_protector"), "item.protector", true, true, 0, Map.of("evo_item", "protector")));
        register(new ItemDef("reaper_cloth", ItemType.MISC, Material.PAPER, cmdOr(31203, "paper:held_reaper_cloth"), "item.reaper_cloth", true, true, 0, Map.of("evo_item", "reaper_cloth")));
        register(new ItemDef("electirizer", ItemType.MISC, Material.PAPER, cmdOr(31204, "paper:held_electirizer"), "item.electirizer", true, true, 0, Map.of("evo_item", "electirizer")));
        register(new ItemDef("magmarizer", ItemType.MISC, Material.PAPER, cmdOr(31205, "paper:held_magmarizer"), "item.magmarizer", true, true, 0, Map.of("evo_item", "magmarizer")));
        register(new ItemDef("dragon_scale", ItemType.MISC, Material.PAPER, cmdOr(31206, "paper:held_dragon_scale"), "item.dragon_scale", true, true, 0, Map.of("evo_item", "dragon_scale")));
        register(new ItemDef("oval_stone", ItemType.MISC, Material.PAPER, cmdOr(31207, "paper:held_oval_stone"), "item.oval_stone", true, true, 0, Map.of("evo_item", "oval_stone")));
        register(new ItemDef("leaders_crest", ItemType.MISC, Material.PAPER, cmdOr(31208, "paper:held_leaders_crest"), "item.leaders_crest", true, true, 0, Map.of("evo_item", "leaders_crest")));

        register(new ItemDef("focus_band", ItemType.HELD, Material.PAPER, cmdOr(31009, "paper:held_focus_band"), "item.focus_band", false, true, 1000, Map.of("held", true)));
        register(new ItemDef("focus_sash", ItemType.HELD, Material.PAPER, cmdOr(31010, "paper:held_focus_sash"), "item.focus_sash", false, true, 1000, Map.of("held", true)));

        register(new ItemDef("scope_lens", ItemType.HELD, Material.PAPER, cmdOr(31011, "paper:held_scope_lens"), "item.scope_lens", false, true, 1000, Map.of("held", true)));
        register(new ItemDef("razor_claw", ItemType.HELD, Material.PAPER, cmdOr(31012, "paper:held_razor_claw"), "item.razor_claw", false, true, 1000, Map.of("held", true)));
        register(new ItemDef("lucky_punch", ItemType.HELD, Material.PAPER, cmdOr(31013, "paper:held_lucky_punch"), "item.lucky_punch", false, true, 1000, Map.of("held", true)));

        register(new ItemDef("choice_band", ItemType.HELD, Material.PAPER, cmdOr(31014, "paper:held_choice_band"), "item.choice_band", false, true, 1000, Map.of("held", true)));
        register(new ItemDef("choice_specs", ItemType.HELD, Material.PAPER, cmdOr(31015, "paper:held_choice_specs"), "item.choice_specs", false, true, 1000, Map.of("held", true)));

        register(new ItemDef("light_ball", ItemType.HELD, Material.PAPER, cmdOr(31016, "paper:held_light_ball"), "item.light_ball", false, true, 1000, Map.of("held", true)));
        register(new ItemDef("thick_club", ItemType.HELD, Material.PAPER, cmdOr(31017, "paper:held_thick_club"), "item.thick_club", false, true, 1000, Map.of("held", true)));

        // Type-boosting held items (1.1x in battle)
        register(new ItemDef("charcoal", ItemType.HELD, Material.PAPER, cmdOr(31100, "paper:held_charcoal"), "item.charcoal", false, true, 1000, Map.of("held", true)));
        register(new ItemDef("mystic_water", ItemType.HELD, Material.PAPER, cmdOr(31101, "paper:held_mystic_water"), "item.mystic_water", false, true, 1000, Map.of("held", true)));
        register(new ItemDef("magnet", ItemType.HELD, Material.PAPER, cmdOr(31102, "paper:held_magnet"), "item.magnet", false, true, 1000, Map.of("held", true)));
        register(new ItemDef("miracle_seed", ItemType.HELD, Material.PAPER, cmdOr(31103, "paper:held_miracle_seed"), "item.miracle_seed", false, true, 1000, Map.of("held", true)));
        register(new ItemDef("soft_sand", ItemType.HELD, Material.PAPER, cmdOr(31104, "paper:held_soft_sand"), "item.soft_sand", false, true, 1000, Map.of("held", true)));
        register(new ItemDef("hard_stone", ItemType.HELD, Material.PAPER, cmdOr(31105, "paper:held_hard_stone"), "item.hard_stone", false, true, 1000, Map.of("held", true)));
        register(new ItemDef("never_melt_ice", ItemType.HELD, Material.PAPER, cmdOr(31106, "paper:held_never_melt_ice"), "item.never_melt_ice", false, true, 1000, Map.of("held", true)));
        register(new ItemDef("black_belt", ItemType.HELD, Material.PAPER, cmdOr(31107, "paper:held_black_belt"), "item.black_belt", false, true, 1000, Map.of("held", true)));
        register(new ItemDef("poison_barb", ItemType.HELD, Material.PAPER, cmdOr(31108, "paper:held_poison_barb"), "item.poison_barb", false, true, 1000, Map.of("held", true)));
        register(new ItemDef("sharp_beak", ItemType.HELD, Material.PAPER, cmdOr(31109, "paper:held_sharp_beak"), "item.sharp_beak", false, true, 1000, Map.of("held", true)));
        register(new ItemDef("spell_tag", ItemType.HELD, Material.PAPER, cmdOr(31110, "paper:held_spell_tag"), "item.spell_tag", false, true, 1000, Map.of("held", true)));
        register(new ItemDef("twisted_spoon", ItemType.HELD, Material.PAPER, cmdOr(31111, "paper:held_twisted_spoon"), "item.twisted_spoon", false, true, 1000, Map.of("held", true)));
        register(new ItemDef("dragon_fang", ItemType.HELD, Material.PAPER, cmdOr(31112, "paper:held_dragon_fang"), "item.dragon_fang", false, true, 1000, Map.of("held", true)));
        register(new ItemDef("metal_coat", ItemType.HELD, Material.PAPER, cmdOr(31113, "paper:held_metal_coat"), "item.metal_coat", false, true, 1000, Map.of("held", true)));
        register(new ItemDef("black_glasses", ItemType.HELD, Material.PAPER, cmdOr(31114, "paper:held_black_glasses"), "item.black_glasses", false, true, 1000, Map.of("held", true)));
        register(new ItemDef("silk_scarf", ItemType.HELD, Material.PAPER, cmdOr(31115, "paper:held_silk_scarf"), "item.silk_scarf", false, true, 1000, Map.of("held", true)));

        // Contest scarves / cosmetic held items (no battle effect in PokeDemo yet)
        register(new ItemDef("red_scarf", ItemType.HELD, Material.PAPER, cmdOr(31116, "paper:held_red_scarf"), "item.red_scarf", false, true, 1000, Map.of("held", true)));
        register(new ItemDef("blue_scarf", ItemType.HELD, Material.PAPER, cmdOr(31117, "paper:held_blue_scarf"), "item.blue_scarf", false, true, 1000, Map.of("held", true)));
        register(new ItemDef("pink_scarf", ItemType.HELD, Material.PAPER, cmdOr(31118, "paper:held_pink_scarf"), "item.pink_scarf", false, true, 1000, Map.of("held", true)));
        register(new ItemDef("green_scarf", ItemType.HELD, Material.PAPER, cmdOr(31119, "paper:held_green_scarf"), "item.green_scarf", false, true, 1000, Map.of("held", true)));
        register(new ItemDef("yellow_scarf", ItemType.HELD, Material.PAPER, cmdOr(31120, "paper:held_yellow_scarf"), "item.yellow_scarf", false, true, 1000, Map.of("held", true)));

        // Economy / social systems are not implemented yet, but we register these items for completeness.
        register(new ItemDef("amulet_coin", ItemType.HELD, Material.PAPER, cmdOr(31121, "paper:held_amulet_coin"), "item.amulet_coin", false, true, 1000, Map.of("held", true)));
        register(new ItemDef("relic_coin", ItemType.MISC, Material.PAPER, cmdOr(31310, "paper:relic_coin"), "item.relic_coin", true, true, 0, Map.of()));
        register(new ItemDef("luck_incense", ItemType.HELD, Material.PAPER, cmdOr(31122, "paper:held_luck_incense"), "item.luck_incense", false, true, 1000, Map.of("held", true)));
        register(new ItemDef("soothe_bell", ItemType.HELD, Material.PAPER, cmdOr(31123, "paper:held_soothe_bell"), "item.soothe_bell", false, true, 1000, Map.of("held", true)));
        register(new ItemDef("destiny_knot", ItemType.HELD, Material.PAPER, cmdOr(31124, "paper:held_destiny_knot"), "item.destiny_knot", false, true, 1000, Map.of("held", true)));

        // Weather extender rocks (PokeDemo has no weather engine yet; registered for future use)
        register(new ItemDef("damp_rock", ItemType.HELD, Material.PAPER, cmdOr(31125, "paper:held_damp_rock"), "item.damp_rock", false, true, 1000, Map.of("held", true)));
        register(new ItemDef("heat_rock", ItemType.HELD, Material.PAPER, cmdOr(31126, "paper:held_heat_rock"), "item.heat_rock", false, true, 1000, Map.of("held", true)));
        register(new ItemDef("icy_rock", ItemType.HELD, Material.PAPER, cmdOr(31127, "paper:held_icy_rock"), "item.icy_rock", false, true, 1000, Map.of("held", true)));
        register(new ItemDef("smooth_rock", ItemType.HELD, Material.PAPER, cmdOr(31128, "paper:held_smooth_rock"), "item.smooth_rock", false, true, 1000, Map.of("held", true)));

        // Primal Orbs (require form system; registered for future)
        register(new ItemDef("red_orb", ItemType.HELD, Material.PAPER, cmdOr(31129, "paper:held_red_orb"), "item.red_orb", false, true, 1000, Map.of("held", true)));
        register(new ItemDef("blue_orb", ItemType.HELD, Material.PAPER, cmdOr(31130, "paper:held_blue_orb"), "item.blue_orb", false, true, 1000, Map.of("held", true)));

        // Alcremie sweets (form system not implemented; register only)
        register(new ItemDef("strawberry_sweet", ItemType.HELD, Material.PAPER, cmdOr(31131, "paper:held_strawberry_sweet"), "item.strawberry_sweet", false, true, 1000, Map.of("held", true)));
        register(new ItemDef("berry_sweet", ItemType.HELD, Material.PAPER, cmdOr(31132, "paper:held_berry_sweet"), "item.berry_sweet", false, true, 1000, Map.of("held", true)));
        register(new ItemDef("love_sweet", ItemType.HELD, Material.PAPER, cmdOr(31133, "paper:held_love_sweet"), "item.love_sweet", false, true, 1000, Map.of("held", true)));
        register(new ItemDef("clover_sweet", ItemType.HELD, Material.PAPER, cmdOr(31134, "paper:held_clover_sweet"), "item.clover_sweet", false, true, 1000, Map.of("held", true)));
        register(new ItemDef("flower_sweet", ItemType.HELD, Material.PAPER, cmdOr(31135, "paper:held_flower_sweet"), "item.flower_sweet", false, true, 1000, Map.of("held", true)));
        register(new ItemDef("star_sweet", ItemType.HELD, Material.PAPER, cmdOr(31136, "paper:held_star_sweet"), "item.star_sweet", false, true, 1000, Map.of("held", true)));
        register(new ItemDef("ribbon_sweet", ItemType.HELD, Material.PAPER, cmdOr(31137, "paper:held_ribbon_sweet"), "item.ribbon_sweet", false, true, 1000, Map.of("held", true)));

        // Drives (Techno Blast type override supported in battle)
        register(new ItemDef("burn_drive", ItemType.HELD, Material.PAPER, cmdOr(31138, "paper:held_burn_drive"), "item.burn_drive", false, true, 1000, Map.of("held", true)));
        register(new ItemDef("douse_drive", ItemType.HELD, Material.PAPER, cmdOr(31139, "paper:held_douse_drive"), "item.douse_drive", false, true, 1000, Map.of("held", true)));
        register(new ItemDef("chill_drive", ItemType.HELD, Material.PAPER, cmdOr(31140, "paper:held_chill_drive"), "item.chill_drive", false, true, 1000, Map.of("held", true)));
        register(new ItemDef("shock_drive", ItemType.HELD, Material.PAPER, cmdOr(31141, "paper:held_shock_drive"), "item.shock_drive", false, true, 1000, Map.of("held", true)));

        // Memories (Multi-Attack type override supported in battle)
        register(new ItemDef("bug_memory", ItemType.HELD, Material.PAPER, cmdOr(31142, "paper:held_bug_memory"), "item.bug_memory", false, true, 1000, Map.of("held", true)));
        register(new ItemDef("dark_memory", ItemType.HELD, Material.PAPER, cmdOr(31143, "paper:held_dark_memory"), "item.dark_memory", false, true, 1000, Map.of("held", true)));
        register(new ItemDef("dragon_memory", ItemType.HELD, Material.PAPER, cmdOr(31144, "paper:held_dragon_memory"), "item.dragon_memory", false, true, 1000, Map.of("held", true)));
        register(new ItemDef("electric_memory", ItemType.HELD, Material.PAPER, cmdOr(31145, "paper:held_electric_memory"), "item.electric_memory", false, true, 1000, Map.of("held", true)));
        register(new ItemDef("fairy_memory", ItemType.HELD, Material.PAPER, cmdOr(31146, "paper:held_fairy_memory"), "item.fairy_memory", false, true, 1000, Map.of("held", true)));
        register(new ItemDef("fighting_memory", ItemType.HELD, Material.PAPER, cmdOr(31147, "paper:held_fighting_memory"), "item.fighting_memory", false, true, 1000, Map.of("held", true)));
        register(new ItemDef("fire_memory", ItemType.HELD, Material.PAPER, cmdOr(31148, "paper:held_fire_memory"), "item.fire_memory", false, true, 1000, Map.of("held", true)));
        register(new ItemDef("flying_memory", ItemType.HELD, Material.PAPER, cmdOr(31149, "paper:held_flying_memory"), "item.flying_memory", false, true, 1000, Map.of("held", true)));
        register(new ItemDef("ghost_memory", ItemType.HELD, Material.PAPER, cmdOr(31150, "paper:held_ghost_memory"), "item.ghost_memory", false, true, 1000, Map.of("held", true)));
        register(new ItemDef("grass_memory", ItemType.HELD, Material.PAPER, cmdOr(31151, "paper:held_grass_memory"), "item.grass_memory", false, true, 1000, Map.of("held", true)));
        register(new ItemDef("ground_memory", ItemType.HELD, Material.PAPER, cmdOr(31152, "paper:held_ground_memory"), "item.ground_memory", false, true, 1000, Map.of("held", true)));
        register(new ItemDef("ice_memory", ItemType.HELD, Material.PAPER, cmdOr(31153, "paper:held_ice_memory"), "item.ice_memory", false, true, 1000, Map.of("held", true)));
        register(new ItemDef("poison_memory", ItemType.HELD, Material.PAPER, cmdOr(31154, "paper:held_poison_memory"), "item.poison_memory", false, true, 1000, Map.of("held", true)));
        register(new ItemDef("psychic_memory", ItemType.HELD, Material.PAPER, cmdOr(31155, "paper:held_psychic_memory"), "item.psychic_memory", false, true, 1000, Map.of("held", true)));
        register(new ItemDef("rock_memory", ItemType.HELD, Material.PAPER, cmdOr(31156, "paper:held_rock_memory"), "item.rock_memory", false, true, 1000, Map.of("held", true)));
        register(new ItemDef("steel_memory", ItemType.HELD, Material.PAPER, cmdOr(31157, "paper:held_steel_memory"), "item.steel_memory", false, true, 1000, Map.of("held", true)));
        register(new ItemDef("water_memory", ItemType.HELD, Material.PAPER, cmdOr(31158, "paper:held_water_memory"), "item.water_memory", false, true, 1000, Map.of("held", true)));

        register(new ItemDef("dire_hit", ItemType.BATTLE, Material.PAPER, 30105, "item.dire_hit",
                false, true, 650, Map.of("dire_hit", true)));
        register(new ItemDef("guard_spec", ItemType.BATTLE, Material.PAPER, 30106, "item.guard_spec",
                false, true, 700, Map.of("mist", 5)));

        // Overworld utility (Gen1 approximations)
        register(new ItemDef("repel", ItemType.MISC, Material.PAPER, 30201, "item.repel",
                true, true, 350, Map.of("repel_seconds", 300)));
        register(new ItemDef("super_repel", ItemType.MISC, Material.PAPER, 30202, "item.super_repel",
                true, true, 500, Map.of("repel_seconds", 600)));
        register(new ItemDef("max_repel", ItemType.MISC, Material.PAPER, 30203, "item.max_repel",
                true, true, 700, Map.of("repel_seconds", 900)));
        register(new ItemDef("escape_rope", ItemType.MISC, Material.PAPER, 30210, "item.escape_rope",
                true, true, 550, Map.of("escape_rope", true)));



        // Level-up / evolution items (Gen1)
        register(new ItemDef("rare_candy", ItemType.MISC, Material.PAPER, 30301, "item.rare_candy",
                true, true, 4800, Map.of("rare_candy", true)));
        register(new ItemDef("fire_stone", ItemType.MISC, Material.PAPER, 30311, "item.fire_stone",
                true, true, 2100, Map.of("evo_item", "fire_stone")));
        register(new ItemDef("water_stone", ItemType.MISC, Material.PAPER, 30312, "item.water_stone",
                true, true, 2100, Map.of("evo_item", "water_stone")));
        register(new ItemDef("thunder_stone", ItemType.MISC, Material.PAPER, 30313, "item.thunder_stone",
                true, true, 2100, Map.of("evo_item", "thunder_stone")));
        register(new ItemDef("leaf_stone", ItemType.MISC, Material.PAPER, 30314, "item.leaf_stone",
                true, true, 2100, Map.of("evo_item", "leaf_stone")));
        register(new ItemDef("moon_stone", ItemType.MISC, Material.PAPER, 30315, "item.moon_stone",
                true, true, 2100, Map.of("evo_item", "moon_stone")));
        register(new ItemDef("sun_stone", ItemType.MISC, Material.PAPER, cmdOr(30316, "paper:sun_stone"), "item.sun_stone",
                true, true, 2100, Map.of("evo_item", "sun_stone")));
        register(new ItemDef("shiny_stone", ItemType.MISC, Material.PAPER, cmdOr(30317, "paper:shiny_stone"), "item.shiny_stone",
                true, true, 2100, Map.of("evo_item", "shiny_stone")));
        register(new ItemDef("dusk_stone", ItemType.MISC, Material.PAPER, cmdOr(30318, "paper:dusk_stone"), "item.dusk_stone",
                true, true, 2100, Map.of("evo_item", "dusk_stone")));
        register(new ItemDef("dawn_stone", ItemType.MISC, Material.PAPER, cmdOr(30319, "paper:dawn_stone"), "item.dawn_stone",
                true, true, 2100, Map.of("evo_item", "dawn_stone")));
        register(new ItemDef("ice_stone", ItemType.MISC, Material.PAPER, cmdOr(30320, "paper:ice_stone"), "item.ice_stone",
                true, true, 2100, Map.of("evo_item", "ice_stone")));

        register(new ItemDef("tart_apple", ItemType.MISC, Material.PAPER, cmdOr(30340, "paper:tart_apple"), "item.tart_apple",
                true, true, 0, Map.of("evo_item", "tart_apple")));
        register(new ItemDef("sweet_apple", ItemType.MISC, Material.PAPER, cmdOr(30341, "paper:sweet_apple"), "item.sweet_apple",
                true, true, 0, Map.of("evo_item", "sweet_apple")));
        register(new ItemDef("syrupy_apple", ItemType.MISC, Material.PAPER, cmdOr(30342, "paper:syrupy_apple"), "item.syrupy_apple",
                true, true, 0, Map.of("evo_item", "syrupy_apple")));
        register(new ItemDef("auspicious_armor", ItemType.MISC, Material.PAPER, cmdOr(30343, "paper:auspicious_armor"), "item.auspicious_armor",
                true, true, 0, Map.of("evo_item", "auspicious_armor")));
        register(new ItemDef("malicious_armor", ItemType.MISC, Material.PAPER, cmdOr(30344, "paper:malicious_armor"), "item.malicious_armor",
                true, true, 0, Map.of("evo_item", "malicious_armor")));
        register(new ItemDef("metal_alloy", ItemType.MISC, Material.PAPER, cmdOr(30345, "paper:metal_alloy"), "item.metal_alloy",
                true, true, 0, Map.of("evo_item", "metal_alloy")));
        register(new ItemDef("scroll_of_darkness", ItemType.MISC, Material.PAPER, cmdOr(30346, "paper:scroll_of_darkness"), "item.scroll_of_darkness",
                true, true, 0, Map.of("evo_item", "scroll_of_darkness")));
        register(new ItemDef("scroll_of_waters", ItemType.MISC, Material.PAPER, cmdOr(30347, "paper:scroll_of_waters"), "item.scroll_of_waters",
                true, true, 0, Map.of("evo_item", "scroll_of_waters")));
        register(new ItemDef("black_augurite", ItemType.MISC, Material.PAPER, cmdOr(30348, "paper:black_augurite"), "item.black_augurite",
                true, true, 0, Map.of("evo_item", "black_augurite")));
        register(new ItemDef("peat_block", ItemType.MISC, Material.PAPER, cmdOr(30349, "paper:peat_block"), "item.peat_block",
                true, true, 0, Map.of("evo_item", "peat_block")));
        register(new ItemDef("galarica_cuff", ItemType.MISC, Material.PAPER, cmdOr(30350, "paper:galarica_cuff"), "item.galarica_cuff",
                true, true, 0, Map.of("evo_item", "galarica_cuff")));
        register(new ItemDef("galarica_wreath", ItemType.MISC, Material.PAPER, cmdOr(30351, "paper:galarica_wreath"), "item.galarica_wreath",
                true, true, 0, Map.of("evo_item", "galarica_wreath")));
        register(new ItemDef("cracked_pot", ItemType.MISC, Material.PAPER, cmdOr(30352, "paper:cracked_pot"), "item.cracked_pot",
                true, true, 0, Map.of("evo_item", "cracked_pot")));
        register(new ItemDef("chipped_pot", ItemType.MISC, Material.PAPER, cmdOr(30353, "paper:chipped_pot"), "item.chipped_pot",
                true, true, 0, Map.of("evo_item", "chipped_pot")));
        register(new ItemDef("unremarkable_teacup", ItemType.MISC, Material.PAPER, cmdOr(30354, "paper:unremarkable_teacup"), "item.unremarkable_teacup",
                true, true, 0, Map.of("evo_item", "unremarkable_teacup")));
        register(new ItemDef("masterpiece_teacup", ItemType.MISC, Material.PAPER, cmdOr(30355, "paper:masterpiece_teacup"), "item.masterpiece_teacup",
                true, true, 0, Map.of("evo_item", "masterpiece_teacup")));

        // Vitamins (EV training; modernized EV system: +10 EV, caps at 255 per stat and 510 total)
        register(new ItemDef("hp_up", ItemType.VITAMIN, Material.PAPER, 30321, "item.hp_up",
                true, true, 9800, Map.of("vit_stat", "hp", "vit_amount", 10)));
        register(new ItemDef("protein", ItemType.VITAMIN, Material.PAPER, 30322, "item.protein",
                true, true, 9800, Map.of("vit_stat", "atk", "vit_amount", 10)));
        register(new ItemDef("iron", ItemType.VITAMIN, Material.PAPER, 30323, "item.iron",
                true, true, 9800, Map.of("vit_stat", "def", "vit_amount", 10)));
        register(new ItemDef("calcium", ItemType.VITAMIN, Material.PAPER, 30324, "item.calcium",
                true, true, 9800, Map.of("vit_stat", "spa", "vit_amount", 10)));
        register(new ItemDef("carbos", ItemType.VITAMIN, Material.PAPER, 30325, "item.carbos",
                true, true, 9800, Map.of("vit_stat", "spe", "vit_amount", 10)));

        // Wings (Gen5+) - EV +1 (钓鱼稀有掉落)
        register(new ItemDef("health_wing", ItemType.VITAMIN, Material.PAPER, cmdOr(30590, "paper:health_wing"), "item.health_wing", true, true, 0, java.util.Map.of("vit_stat", "hp", "vit_amount", 1)));
        register(new ItemDef("muscle_wing", ItemType.VITAMIN, Material.PAPER, cmdOr(30591, "paper:muscle_wing"), "item.muscle_wing", true, true, 0, java.util.Map.of("vit_stat", "atk", "vit_amount", 1)));
        register(new ItemDef("resist_wing", ItemType.VITAMIN, Material.PAPER, cmdOr(30592, "paper:resist_wing"), "item.resist_wing", true, true, 0, java.util.Map.of("vit_stat", "def", "vit_amount", 1)));
        register(new ItemDef("genius_wing", ItemType.VITAMIN, Material.PAPER, cmdOr(30593, "paper:genius_wing"), "item.genius_wing", true, true, 0, java.util.Map.of("vit_stat", "spa", "vit_amount", 1)));
        register(new ItemDef("clever_wing", ItemType.VITAMIN, Material.PAPER, cmdOr(30594, "paper:clever_wing"), "item.clever_wing", true, true, 0, java.util.Map.of("vit_stat", "spd", "vit_amount", 1)));
        register(new ItemDef("swift_wing", ItemType.VITAMIN, Material.PAPER, cmdOr(30595, "paper:swift_wing"), "item.swift_wing", true, true, 0, java.util.Map.of("vit_stat", "spe", "vit_amount", 1)));

        // Ability changing items
        register(new ItemDef("ability_capsule", ItemType.MISC, Material.PAPER, cmdOr(30420, "paper:ability_capsule"), "item.ability_capsule", true, true, 0, java.util.Map.of("action", "ability_capsule")));
        register(new ItemDef("ability_patch", ItemType.MISC, Material.PAPER, cmdOr(30424, "paper:ability_patch"), "item.ability_patch", true, true, 0, java.util.Map.of("action", "ability_patch")));

        // Bottle caps (IV training)
        register(new ItemDef("silver_bottle_cap", ItemType.MISC, Material.PAPER, cmdOr(30481, "paper:silver_bottle_cap"), "item.silver_bottle_cap", true, true, 0, java.util.Map.of("action", "iv_one31")));
        register(new ItemDef("gold_bottle_cap", ItemType.MISC, Material.PAPER, cmdOr(30480, "paper:gold_bottle_cap"), "item.gold_bottle_cap", true, true, 0, java.util.Map.of("action", "iv_all31")));

        // Mints (Nature change)
        register(new ItemDef("lonely_mint", ItemType.MISC, Material.PAPER, cmdOr(30450, "paper:lonely_mint"), "item.lonely_mint", true, true, 0, java.util.Map.of("action", "set_nature", "nature", "LONELY")));
        register(new ItemDef("adamant_mint", ItemType.MISC, Material.PAPER, cmdOr(30451, "paper:adamant_mint"), "item.adamant_mint", true, true, 0, java.util.Map.of("action", "set_nature", "nature", "ADAMANT")));
        register(new ItemDef("naughty_mint", ItemType.MISC, Material.PAPER, cmdOr(30452, "paper:naughty_mint"), "item.naughty_mint", true, true, 0, java.util.Map.of("action", "set_nature", "nature", "NAUGHTY")));
        register(new ItemDef("brave_mint", ItemType.MISC, Material.PAPER, cmdOr(30453, "paper:brave_mint"), "item.brave_mint", true, true, 0, java.util.Map.of("action", "set_nature", "nature", "BRAVE")));
        register(new ItemDef("bold_mint", ItemType.MISC, Material.PAPER, cmdOr(30454, "paper:bold_mint"), "item.bold_mint", true, true, 0, java.util.Map.of("action", "set_nature", "nature", "BOLD")));
        register(new ItemDef("impish_mint", ItemType.MISC, Material.PAPER, cmdOr(30455, "paper:impish_mint"), "item.impish_mint", true, true, 0, java.util.Map.of("action", "set_nature", "nature", "IMPISH")));
        register(new ItemDef("lax_mint", ItemType.MISC, Material.PAPER, cmdOr(30456, "paper:lax_mint"), "item.lax_mint", true, true, 0, java.util.Map.of("action", "set_nature", "nature", "LAX")));
        register(new ItemDef("relaxed_mint", ItemType.MISC, Material.PAPER, cmdOr(30457, "paper:relaxed_mint"), "item.relaxed_mint", true, true, 0, java.util.Map.of("action", "set_nature", "nature", "RELAXED")));
        register(new ItemDef("modest_mint", ItemType.MISC, Material.PAPER, cmdOr(30458, "paper:modest_mint"), "item.modest_mint", true, true, 0, java.util.Map.of("action", "set_nature", "nature", "MODEST")));
        register(new ItemDef("mild_mint", ItemType.MISC, Material.PAPER, cmdOr(30459, "paper:mild_mint"), "item.mild_mint", true, true, 0, java.util.Map.of("action", "set_nature", "nature", "MILD")));
        register(new ItemDef("rash_mint", ItemType.MISC, Material.PAPER, cmdOr(30460, "paper:rash_mint"), "item.rash_mint", true, true, 0, java.util.Map.of("action", "set_nature", "nature", "RASH")));
        register(new ItemDef("quiet_mint", ItemType.MISC, Material.PAPER, cmdOr(30461, "paper:quiet_mint"), "item.quiet_mint", true, true, 0, java.util.Map.of("action", "set_nature", "nature", "QUIET")));
        register(new ItemDef("calm_mint", ItemType.MISC, Material.PAPER, cmdOr(30462, "paper:calm_mint"), "item.calm_mint", true, true, 0, java.util.Map.of("action", "set_nature", "nature", "CALM")));
        register(new ItemDef("gentle_mint", ItemType.MISC, Material.PAPER, cmdOr(30463, "paper:gentle_mint"), "item.gentle_mint", true, true, 0, java.util.Map.of("action", "set_nature", "nature", "GENTLE")));
        register(new ItemDef("careful_mint", ItemType.MISC, Material.PAPER, cmdOr(30464, "paper:careful_mint"), "item.careful_mint", true, true, 0, java.util.Map.of("action", "set_nature", "nature", "CAREFUL")));
        register(new ItemDef("sassy_mint", ItemType.MISC, Material.PAPER, cmdOr(30465, "paper:sassy_mint"), "item.sassy_mint", true, true, 0, java.util.Map.of("action", "set_nature", "nature", "SASSY")));
        register(new ItemDef("timid_mint", ItemType.MISC, Material.PAPER, cmdOr(30466, "paper:timid_mint"), "item.timid_mint", true, true, 0, java.util.Map.of("action", "set_nature", "nature", "TIMID")));
        register(new ItemDef("hasty_mint", ItemType.MISC, Material.PAPER, cmdOr(30467, "paper:hasty_mint"), "item.hasty_mint", true, true, 0, java.util.Map.of("action", "set_nature", "nature", "HASTY")));
        register(new ItemDef("jolly_mint", ItemType.MISC, Material.PAPER, cmdOr(30468, "paper:jolly_mint"), "item.jolly_mint", true, true, 0, java.util.Map.of("action", "set_nature", "nature", "JOLLY")));
        register(new ItemDef("naive_mint", ItemType.MISC, Material.PAPER, cmdOr(30469, "paper:naive_mint"), "item.naive_mint", true, true, 0, java.util.Map.of("action", "set_nature", "nature", "NAIVE")));
        register(new ItemDef("serious_mint", ItemType.MISC, Material.PAPER, cmdOr(30470, "paper:serious_mint"), "item.serious_mint", true, true, 0, java.util.Map.of("action", "set_nature", "nature", "SERIOUS")));

        // (berries / poke flute are registered later in the unified items section)


        // ========== Overworld machines (custom Note Blocks) ==========
        // Placeable machine items.
        // IMPORTANT: use PAPER (not PLAYER_HEAD) so right-click will not equip it on the head.
        // The resource pack maps PAPER CMD 36001/36002 to the Cobblemon PC / Healing Machine item icons.
        // (We keep this range well away from TM/HM CMDs to avoid accidental pack collisions.)
                // Cobblemon-style apricorn balls (craftable via apricorns + tier materials)
        // NOTE: master_ball crafting is intentionally NOT registered (server owner will decide acquisition).
        // CMD range for balls:
        //   - 32001..32004: base balls
        //   - 32005+: apricorn/other balls
        int ballCmd = 32005;
        String[] extraBalls = new String[]{
                // Modern/standard balls
                "premier_ball", "quick_ball", "timer_ball", "dusk_ball", "net_ball", "nest_ball", "repeat_ball",
                "luxury_ball", "heal_ball", "dive_ball", "dream_ball",
                // Apricorn balls
                "fast_ball", "friend_ball", "heavy_ball", "level_ball", "love_ball", "lure_ball", "moon_ball",
                // Special
                "safari_ball", "sport_ball", "park_ball", "beast_ball", "iron_ball",
                // Gem balls (Cobblemon)
                "azure_ball", "citrine_ball", "roseate_ball", "slate_ball", "verdant_ball", "ivory_ball",
                // Ancient balls (Cobblemon)
                "ancient_poke_ball", "ancient_great_ball", "ancient_ultra_ball",
                "ancient_feather_ball", "ancient_wing_ball", "ancient_jet_ball",
                "ancient_leaden_ball", "ancient_heavy_ball", "ancient_gigaton_ball",
                "ancient_azure_ball", "ancient_citrine_ball", "ancient_roseate_ball", "ancient_slate_ball",
                "ancient_verdant_ball", "ancient_ivory_ball"
        };
        for (String id : extraBalls) {
            register(new ItemDef(id, ItemType.BALL, Material.SNOWBALL, ballCmd++, "item." + id,
                    false, true, 0, Map.of("ballBonus", 1.0, "master", false)));
        }

        // Apricorns (for ball crafting) + Tumblestones (for ancient balls)
        // We keep them as PAPER carriers so they are always usable in crafting via ExactChoice.
        register(new ItemDef("red_apricorn", ItemType.MISC, Material.PAPER, 30401, "item.red_apricorn",
                true, true, 0, Map.of("apricorn", "red")));
        register(new ItemDef("blue_apricorn", ItemType.MISC, Material.PAPER, 30402, "item.blue_apricorn",
                true, true, 0, Map.of("apricorn", "blue")));
        register(new ItemDef("yellow_apricorn", ItemType.MISC, Material.PAPER, 30403, "item.yellow_apricorn",
                true, true, 0, Map.of("apricorn", "yellow")));
        register(new ItemDef("green_apricorn", ItemType.MISC, Material.PAPER, 30404, "item.green_apricorn",
                true, true, 0, Map.of("apricorn", "green")));
        register(new ItemDef("pink_apricorn", ItemType.MISC, Material.PAPER, 30405, "item.pink_apricorn",
                true, true, 0, Map.of("apricorn", "pink")));
        register(new ItemDef("black_apricorn", ItemType.MISC, Material.PAPER, 30406, "item.black_apricorn",
                true, true, 0, Map.of("apricorn", "black")));
        register(new ItemDef("white_apricorn", ItemType.MISC, Material.PAPER, 30407, "item.white_apricorn",
                true, true, 0, Map.of("apricorn", "white")));
        // Apricorn seeds (plantable)
        register(new ItemDef("seed_red_apricorn", ItemType.MISC, Material.PAPER, 30411, "item.seed_red_apricorn", true, true, 0, Map.of("seed","apricorn","color","red")));
        register(new ItemDef("seed_blue_apricorn", ItemType.MISC, Material.PAPER, 30412, "item.seed_blue_apricorn", true, true, 0, Map.of("seed","apricorn","color","blue")));
        register(new ItemDef("seed_yellow_apricorn", ItemType.MISC, Material.PAPER, 30413, "item.seed_yellow_apricorn", true, true, 0, Map.of("seed","apricorn","color","yellow")));
        register(new ItemDef("seed_green_apricorn", ItemType.MISC, Material.PAPER, 30414, "item.seed_green_apricorn", true, true, 0, Map.of("seed","apricorn","color","green")));
        register(new ItemDef("seed_pink_apricorn", ItemType.MISC, Material.PAPER, 30415, "item.seed_pink_apricorn", true, true, 0, Map.of("seed","apricorn","color","pink")));
        register(new ItemDef("seed_black_apricorn", ItemType.MISC, Material.PAPER, 30416, "item.seed_black_apricorn", true, true, 0, Map.of("seed","apricorn","color","black")));
        register(new ItemDef("seed_white_apricorn", ItemType.MISC, Material.PAPER, 30417, "item.seed_white_apricorn", true, true, 0, Map.of("seed","apricorn","color","white")));

        // Berries (plantable like Pixelmon: berry itself can be planted)
        int berryCmd = 30500;
        for (String b : BerryIndex.ALL_BERRIES) {
            String id = b + "_berry";
            String key = "item." + id;
            register(new ItemDef(id, ItemType.MISC, Material.PAPER, berryCmd++, key, true, true, 0, Map.of("berry", b)));
        }


        register(new ItemDef("tumblestone", ItemType.MISC, Material.PAPER, 30421, "item.tumblestone",
                true, true, 0, Map.of("tumblestone", "normal")));
        register(new ItemDef("black_tumblestone", ItemType.MISC, Material.PAPER, 30422, "item.black_tumblestone",
                true, true, 0, Map.of("tumblestone", "black")));
        register(new ItemDef("sky_tumblestone", ItemType.MISC, Material.PAPER, 30423, "item.sky_tumblestone",
                true, true, 0, Map.of("tumblestone", "sky")));

        // Battle items (Gen1 basic)
        register(new ItemDef("x_attack", ItemType.BATTLE, Material.PAPER, 30101, "item.x_attack",
                false, true, 500, Map.of("stage", "atk", "delta", 1)));
        register(new ItemDef("x_defend", ItemType.BATTLE, Material.PAPER, 30102, "item.x_defend",
                false, true, 550, Map.of("stage", "def", "delta", 1)));
        register(new ItemDef("x_speed", ItemType.BATTLE, Material.PAPER, 30103, "item.x_speed",
                false, true, 350, Map.of("stage", "spe", "delta", 1)));
        register(new ItemDef("x_accuracy", ItemType.BATTLE, Material.PAPER, 30104, "item.x_accuracy",
                false, true, 950, Map.of("stage", "accuracy", "delta", 1)));
        register(new ItemDef("x_special", ItemType.BATTLE, Material.PAPER, 30107, "item.x_special",
                false, true, 350, Map.of("stage", "special", "delta", 1)));
        register(new ItemDef("x_evasion", ItemType.BATTLE, Material.PAPER, 30108, "item.x_evasion",
                false, true, 350, Map.of("stage", "evasion", "delta", 1)));
        register(new ItemDef("poke_doll", ItemType.BATTLE, Material.PAPER, 30109, "item.poke_doll",
                false, true, 1000, Map.of("escape", true)));
        register(new ItemDef("dire_hit", ItemType.BATTLE, Material.PAPER, 30105, "item.dire_hit",
                false, true, 650, Map.of("dire_hit", true)));
        register(new ItemDef("guard_spec", ItemType.BATTLE, Material.PAPER, 30106, "item.guard_spec",
                false, true, 700, Map.of("mist", 5)));

        // Overworld utility (Gen1 approximations)
        register(new ItemDef("repel", ItemType.MISC, Material.PAPER, 30201, "item.repel",
                true, true, 350, Map.of("repel_seconds", 300)));
        register(new ItemDef("super_repel", ItemType.MISC, Material.PAPER, 30202, "item.super_repel",
                true, true, 500, Map.of("repel_seconds", 600)));
        register(new ItemDef("max_repel", ItemType.MISC, Material.PAPER, 30203, "item.max_repel",
                true, true, 700, Map.of("repel_seconds", 900)));
        register(new ItemDef("escape_rope", ItemType.MISC, Material.PAPER, 30210, "item.escape_rope",
                true, true, 550, Map.of("escape_rope", true)));



        // Level-up / evolution items (Gen1)
        register(new ItemDef("rare_candy", ItemType.MISC, Material.PAPER, 30301, "item.rare_candy",
                true, true, 4800, Map.of("rare_candy", true)));
        register(new ItemDef("fire_stone", ItemType.MISC, Material.PAPER, 30311, "item.fire_stone",
                true, true, 2100, Map.of("evo_item", "fire_stone")));
        register(new ItemDef("water_stone", ItemType.MISC, Material.PAPER, 30312, "item.water_stone",
                true, true, 2100, Map.of("evo_item", "water_stone")));
        register(new ItemDef("thunder_stone", ItemType.MISC, Material.PAPER, 30313, "item.thunder_stone",
                true, true, 2100, Map.of("evo_item", "thunder_stone")));
        register(new ItemDef("leaf_stone", ItemType.MISC, Material.PAPER, 30314, "item.leaf_stone",
                true, true, 2100, Map.of("evo_item", "leaf_stone")));
        register(new ItemDef("moon_stone", ItemType.MISC, Material.PAPER, 30315, "item.moon_stone",
                true, true, 2100, Map.of("evo_item", "moon_stone")));
        register(new ItemDef("sun_stone", ItemType.MISC, Material.PAPER, cmdOr(30316, "paper:sun_stone"), "item.sun_stone",
                true, true, 2100, Map.of("evo_item", "sun_stone")));
        register(new ItemDef("shiny_stone", ItemType.MISC, Material.PAPER, cmdOr(30317, "paper:shiny_stone"), "item.shiny_stone",
                true, true, 2100, Map.of("evo_item", "shiny_stone")));
        register(new ItemDef("dusk_stone", ItemType.MISC, Material.PAPER, cmdOr(30318, "paper:dusk_stone"), "item.dusk_stone",
                true, true, 2100, Map.of("evo_item", "dusk_stone")));
        register(new ItemDef("dawn_stone", ItemType.MISC, Material.PAPER, cmdOr(30319, "paper:dawn_stone"), "item.dawn_stone",
                true, true, 2100, Map.of("evo_item", "dawn_stone")));
        register(new ItemDef("ice_stone", ItemType.MISC, Material.PAPER, cmdOr(30320, "paper:ice_stone"), "item.ice_stone",
                true, true, 2100, Map.of("evo_item", "ice_stone")));

        register(new ItemDef("tart_apple", ItemType.MISC, Material.PAPER, cmdOr(30340, "paper:tart_apple"), "item.tart_apple",
                true, true, 0, Map.of("evo_item", "tart_apple")));
        register(new ItemDef("sweet_apple", ItemType.MISC, Material.PAPER, cmdOr(30341, "paper:sweet_apple"), "item.sweet_apple",
                true, true, 0, Map.of("evo_item", "sweet_apple")));
        register(new ItemDef("syrupy_apple", ItemType.MISC, Material.PAPER, cmdOr(30342, "paper:syrupy_apple"), "item.syrupy_apple",
                true, true, 0, Map.of("evo_item", "syrupy_apple")));
        register(new ItemDef("auspicious_armor", ItemType.MISC, Material.PAPER, cmdOr(30343, "paper:auspicious_armor"), "item.auspicious_armor",
                true, true, 0, Map.of("evo_item", "auspicious_armor")));
        register(new ItemDef("malicious_armor", ItemType.MISC, Material.PAPER, cmdOr(30344, "paper:malicious_armor"), "item.malicious_armor",
                true, true, 0, Map.of("evo_item", "malicious_armor")));
        register(new ItemDef("metal_alloy", ItemType.MISC, Material.PAPER, cmdOr(30345, "paper:metal_alloy"), "item.metal_alloy",
                true, true, 0, Map.of("evo_item", "metal_alloy")));
        register(new ItemDef("scroll_of_darkness", ItemType.MISC, Material.PAPER, cmdOr(30346, "paper:scroll_of_darkness"), "item.scroll_of_darkness",
                true, true, 0, Map.of("evo_item", "scroll_of_darkness")));
        register(new ItemDef("scroll_of_waters", ItemType.MISC, Material.PAPER, cmdOr(30347, "paper:scroll_of_waters"), "item.scroll_of_waters",
                true, true, 0, Map.of("evo_item", "scroll_of_waters")));
        register(new ItemDef("black_augurite", ItemType.MISC, Material.PAPER, cmdOr(30348, "paper:black_augurite"), "item.black_augurite",
                true, true, 0, Map.of("evo_item", "black_augurite")));
        register(new ItemDef("peat_block", ItemType.MISC, Material.PAPER, cmdOr(30349, "paper:peat_block"), "item.peat_block",
                true, true, 0, Map.of("evo_item", "peat_block")));
        register(new ItemDef("galarica_cuff", ItemType.MISC, Material.PAPER, cmdOr(30350, "paper:galarica_cuff"), "item.galarica_cuff",
                true, true, 0, Map.of("evo_item", "galarica_cuff")));
        register(new ItemDef("galarica_wreath", ItemType.MISC, Material.PAPER, cmdOr(30351, "paper:galarica_wreath"), "item.galarica_wreath",
                true, true, 0, Map.of("evo_item", "galarica_wreath")));
        register(new ItemDef("cracked_pot", ItemType.MISC, Material.PAPER, cmdOr(30352, "paper:cracked_pot"), "item.cracked_pot",
                true, true, 0, Map.of("evo_item", "cracked_pot")));
        register(new ItemDef("chipped_pot", ItemType.MISC, Material.PAPER, cmdOr(30353, "paper:chipped_pot"), "item.chipped_pot",
                true, true, 0, Map.of("evo_item", "chipped_pot")));
        register(new ItemDef("unremarkable_teacup", ItemType.MISC, Material.PAPER, cmdOr(30354, "paper:unremarkable_teacup"), "item.unremarkable_teacup",
                true, true, 0, Map.of("evo_item", "unremarkable_teacup")));
        register(new ItemDef("masterpiece_teacup", ItemType.MISC, Material.PAPER, cmdOr(30355, "paper:masterpiece_teacup"), "item.masterpiece_teacup",
                true, true, 0, Map.of("evo_item", "masterpiece_teacup")));

        // Vitamins (EV training; modernized EV system: +10 EV, caps at 255 per stat and 510 total)
        register(new ItemDef("hp_up", ItemType.VITAMIN, Material.PAPER, 30321, "item.hp_up",
                true, true, 9800, Map.of("vit_stat", "hp", "vit_amount", 10)));
        register(new ItemDef("protein", ItemType.VITAMIN, Material.PAPER, 30322, "item.protein",
                true, true, 9800, Map.of("vit_stat", "atk", "vit_amount", 10)));
        register(new ItemDef("iron", ItemType.VITAMIN, Material.PAPER, 30323, "item.iron",
                true, true, 9800, Map.of("vit_stat", "def", "vit_amount", 10)));
        register(new ItemDef("calcium", ItemType.VITAMIN, Material.PAPER, 30324, "item.calcium",
                true, true, 9800, Map.of("vit_stat", "spa", "vit_amount", 10)));
        register(new ItemDef("carbos", ItemType.VITAMIN, Material.PAPER, 30325, "item.carbos",
                true, true, 9800, Map.of("vit_stat", "spe", "vit_amount", 10)));

        // Poké Flute (wake up all sleeping party Pokémon). In Gen1 it affects both sides; here we apply to party.
        register(new ItemDef("poke_flute", ItemType.STATUS_CURE, Material.PAPER, 30331, "item.poke_flute",
                false, true, 0, Map.of("cure", "sleep", "party_all", true)));

        // ========== Overworld machines (custom Note Blocks) ==========
        // Placeable machine items.
        // IMPORTANT: use PAPER (not PLAYER_HEAD) so right-click will not equip it on the head.
        // The resource pack maps PAPER CMD 36001/36002 to the Cobblemon PC / Healing Machine item icons.
        // (We keep this range well away from TM/HM CMDs to avoid accidental pack collisions.)
register(new ItemDef("pc_machine", ItemType.KEY, Material.PAPER, 36001, "item.pc_machine",
                false, true, 0, Map.of("machine", "pc")));
        register(new ItemDef("healer_machine", ItemType.KEY, Material.PAPER, 36002, "item.healer_machine",
                false, true, 0, Map.of("machine", "healer")));

        // Pasture (breeding ranch). PAPER CMD 36000 to avoid collisions with held-items (36005+).
        register(new ItemDef("pasture_machine", ItemType.KEY, Material.PAPER, 36000, "item.pasture_machine",
                false, true, 0, Map.of("machine", "pasture")));

        // Fossil Reviver (化石复活机).
        // IMPORTANT: paper CMD must NOT collide with held-items range.
        // Our resource pack's paper range-dispatch currently ends at 36221 (held items).
        // So we allocate fossil machine + fossils after that range.
        register(new ItemDef("fossil_machine", ItemType.KEY, Material.PAPER, 36222, "item.fossil_machine",
                false, true, 0, Map.of("machine", "fossil")));

        // Fossil Analyzer (化石解析仪) - separate machine.
        register(new ItemDef("fossil_analyzer", ItemType.KEY, Material.PAPER, 36229, "item.fossil_analyzer",
                false, true, 0, Map.of("machine", "fossil_analyzer")));

        // Fossils (archaeology loot -> fossil machine -> egg)
        // Fossils (after held-items range)
        register(new ItemDef("helix_fossil", ItemType.MISC, Material.PAPER, 36223, "item.helix_fossil",
                true, true, 0, Map.of("fossil", "helix")));
        register(new ItemDef("dome_fossil", ItemType.MISC, Material.PAPER, 36224, "item.dome_fossil",
                true, true, 0, Map.of("fossil", "dome")));
        register(new ItemDef("old_amber", ItemType.MISC, Material.PAPER, 36225, "item.old_amber",
                true, true, 0, Map.of("fossil", "amber")));

        // Suspicious stone (archaeology-like wash result)
        register(new ItemDef("suspicious_stone", ItemType.MISC, Material.PAPER, 36228, "item.suspicious_stone",
                true, true, 0, Map.of("tag", "suspicious_stone")));

        // Extra machines (devices)
        register(new ItemDef("trade_machine", ItemType.KEY, Material.PAPER, 36226, "item.trade_machine",
                false, true, 0, Map.of("machine", "trade")));
        register(new ItemDef("clone_machine", ItemType.KEY, Material.PAPER, 36227, "item.clone_machine",
                false, true, 0, Map.of("machine", "clone")));

        // ========== Phone & Pokédex ==========
        register(new ItemDef("pokedex", ItemType.KEY, Material.PAPER, 36003, "item.pokedex",
                false, true, 0, Map.of("ui", "pokedex")));
        register(new ItemDef("poke_phone", ItemType.KEY, Material.PAPER, 36004, "item.poke_phone",
                false, true, 0, Map.of("ui", "phone")));
        register(new ItemDef("poke_rod", ItemType.KEY, Material.FISHING_ROD, 39001, "item.poke_rod",
                false, false, 0, Map.of("tool", "poke_rod", "rod_tier", "old")));
        register(new ItemDef("great_rod", ItemType.KEY, Material.FISHING_ROD, 39002, "item.great_rod",
                false, false, 0, Map.of("tool", "great_rod", "rod_tier", "good")));
        register(new ItemDef("ultra_rod", ItemType.KEY, Material.FISHING_ROD, 39003, "item.ultra_rod",
                false, false, 0, Map.of("tool", "ultra_rod", "rod_tier", "super")));
        register(new ItemDef("master_rod", ItemType.KEY, Material.FISHING_ROD, 39004, "item.master_rod",
                false, false, 0, Map.of("tool", "master_rod", "rod_tier", "master")));
        register(new ItemDef("love_rod", ItemType.KEY, Material.FISHING_ROD, 39005, "item.love_rod",
                false, false, 0, Map.of("tool", "love_rod", "rod_tier", "love")));

        // ========== Bulk Held Items (data-driven) ==========
        // We keep the canonical held-item list in a txt file (Chinese/Japanese/English/Desc columns)
        // so we can extend quickly in the future.
        // Berries and TMs/HMs are excluded here because they have dedicated systems.
        // This will register missing items into the registry with sequential CMD mapping.
        // The resource pack must include matching overrides in minecraft/models/item/paper.json.
        HeldItemCatalog.registerBulkHeldItemsFromTxt(this, 36005);

        // ========== TM/HM ==========
        // We intentionally use PAPER as the carrier to avoid vanilla music disc subtitle like "C418 - cat".
        // NOTE: Balls already use SNOWBALL CMD 32001-32004 in this project.
        // To avoid CMD collisions, we map TM/HM onto a separate range:
        //   - TM: 33001-33100  (TM01-TM100)
        //   - HM: 33101-33108  (HM01-HM08)
        // Resource pack must map PAPER CMD 33001+ to TM models, and 33101+ to HM models.
        // TMs are consumed on use; HMs are not consumed.
        for (int i = 1; i <= 100; i++) {
            String id = String.format(java.util.Locale.ROOT, "tm%02d", i);
            int cmd = 33000 + i;
            register(new ItemDef(id, ItemType.TM, Material.PAPER, cmd, "item." + id,
                    true, true, 0, Map.of("tm_no", i, "is_hm", false)));
        }
        for (int i = 1; i <= 8; i++) {
            String id = String.format(java.util.Locale.ROOT, "hm%02d", i);
            int cmd = 33100 + i;
            register(new ItemDef(id, ItemType.TM, Material.PAPER, cmd, "item." + id,
                    false, true, 0, Map.of("tm_no", i, "is_hm", true)));
        }

        // ========== Full machine/tutor aliases ==========
        // Examples:
        //   /pokedemo giveitem tm_roost
        //   /pokedemo giveitem tm_earthquake
        //   /pokedemo giveitem mt_thunderpunch
        //   /pokedemo giveitem tutor_heatwave
        registerMachineAliasesFromResource("default_data/moves_raw/machine_alias_moves.json", "tm_", 33201);
        registerMachineAliasesFromResource("default_data/moves_raw/tutor_alias_moves.json", "mt_", 33601);
        registerMachineAliasesFromResource("default_data/moves_raw/tutor_alias_moves.json", "tutor_", 34001);

    }

    private void registerMachineAliasesFromResource(String resourcePath, String prefix, int startCmd) {
        try (InputStream in = ItemRegistry.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) return;
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            com.google.gson.JsonObject obj = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
            if (!obj.has("moves") || !obj.get("moves").isJsonArray()) return;
            int idx = 0;
            for (com.google.gson.JsonElement el : obj.getAsJsonArray("moves")) {
                String moveId;
                try {
                    moveId = el.getAsString();
                } catch (Exception ignored) {
                    continue;
                }
                if (moveId == null || moveId.isBlank()) continue;
                moveId = moveId.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", "");
                if (moveId.isBlank()) continue;
                String id = prefix + moveId;
                int cmd = startCmd + idx++;
                register(new ItemDef(id, ItemType.TM, Material.PAPER, cmd, "item." + id,
                        !prefix.startsWith("hm"), true, 0, java.util.Map.of("machine_prefix", prefix.replace("_", "").toUpperCase(java.util.Locale.ROOT), "move_id", moveId)));
            }
        } catch (Exception ignored) {
        }
    }


    public void register(ItemDef def) {
        defsById.put(def.id, def);
    }

    public ItemDef get(String id) {
        return defsById.get(id);
    }

    /** Backward-compatible alias used by some registrars. */
    public ItemDef getById(String id) {
        return get(id);
    }

    public Map<String, ItemDef> all() {
        return Collections.unmodifiableMap(defsById);
    }
}