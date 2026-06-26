package win.pokedemo;

import java.util.*;

public final class HeldItemEffects {
    private HeldItemEffects() {}

    /**
     * Some held items modify the type of specific signature moves.
     * We implement the most common and safe-to-support cases:
     *  - Genesect's Techno Blast changes type based on Drives.
     *  - Silvally's Multi-Attack changes type based on Memories.
     *
     * This is battle-only and does not mutate stored move data.
     */
    public static String overrideMoveType(PokemonInstance atk, Species atkS, Move move) {
        if (move == null) return "";
        String base = move.type() == null ? "" : move.type().toLowerCase();
        if (atk == null || atkS == null) return base;

        String id = move.id() == null ? "" : move.id().toLowerCase();
        String held = atk.heldItemId == null ? "" : atk.heldItemId.toLowerCase();
        if (AbilityEffects.isItemSuppressedByKlutz(atk)) held = "";
        if (AbilityEffects.has(atk, "normalize")) return "normal";
        if (AbilityEffects.has(atk, "liquidvoice")) {
            String mid = move.id() == null ? "" : move.id().toLowerCase().replace("_", "").replace("-", "").replace(" ", "");
            if (java.util.Set.of("boomburst","bugbuzz","chatter","clangingscales","clangoroussoul","confide","disarmingvoice","echoedvoice","eerieimpulse","growl","healbell","howl","hypervoice","metalsound","nobleroar","overdrive","partingshot","perishsong","relicsong","roar","round","screech","sing","snarl","snore","sparklingaria","supersonic","torchsong","uproar").contains(mid)) {
                return "water";
            }
        }

        // Genesect: Techno Blast
        if ("techno_blast".equals(id) && "genesect".equalsIgnoreCase(atkS.id())) {
            return switch (held) {
                case "burn_drive" -> "fire";
                case "douse_drive" -> "water";
                case "chill_drive" -> "ice";
                case "shock_drive" -> "electric";
                default -> base;
            };
        }

        // Silvally: Multi-Attack
        if ("multi_attack".equals(id) && "silvally".equalsIgnoreCase(atkS.id())) {
            return switch (held) {
                case "bug_memory" -> "bug";
                case "dark_memory" -> "dark";
                case "dragon_memory" -> "dragon";
                case "electric_memory" -> "electric";
                case "fairy_memory" -> "fairy";
                case "fighting_memory" -> "fighting";
                case "fire_memory" -> "fire";
                case "flying_memory" -> "flying";
                case "ghost_memory" -> "ghost";
                case "grass_memory" -> "grass";
                case "ground_memory" -> "ground";
                case "ice_memory" -> "ice";
                case "poison_memory" -> "poison";
                case "psychic_memory" -> "psychic";
                case "rock_memory" -> "rock";
                case "steel_memory" -> "steel";
                case "water_memory" -> "water";
                default -> base;
            };
        }

        // Ability-based Normal->type conversions (simplified).
        if ("normal".equals(base) && move.power() > 0) {
            String ab = AbilityEffects.norm(atk.abilityId);
            if ("aerilate".equals(ab)) return "flying";
            if ("pixilate".equals(ab)) return "fairy";
            if ("refrigerate".equals(ab)) return "ice";
            if ("galvanize".equals(ab)) return "electric";
        }

        return base;
    }

    
    /** Powder moves blocked by Safety Goggles. (Extend as needed.) */
    private static final Set<String> POWDER_MOVES = Set.of(
            "sleep_powder","stun_spore","poison_powder","spore","rage_powder","cotton_spore"
    );

    /** Punching moves boosted by Punching Glove. (Not exhaustive; extend as needed.) */
    private static final Set<String> PUNCH_MOVES = Set.of(
            "comet_punch","dizzy_punch",
            "fire_punch","ice_punch","thunder_punch",
            "mach_punch","bullet_punch","drain_punch","focus_punch",
            "shadow_punch","dynamic_punch","mega_punch",
            "power_up_punch","sky_uppercut"
    );

    // ===== Gen2-style berries / one-time hold items =====
    private static final Map<String, Integer> HP_BERRIES = Map.of(
            "berry", 10,
            "gold_berry", 30
    );

    // status curing berries (Gen2 names)
    private static final Set<String> PRZ_CURE = Set.of("przcureberry");
    private static final Set<String> PSN_CURE = Set.of("psncureberry");
    private static final Set<String> BRN_CURE = Set.of("burnt_berry");
    private static final Set<String> FRZ_CURE = Set.of("ice_berry");
    private static final Set<String> SLF_CURE = Set.of("mint_berry");
    private static final Set<String> ANY_CURE = Set.of("miracleberry");

    /** Called after a major status is applied by a move. May cure and consume berries. */
    public static List<String> onStatusApplied(PokemonInstance mon, Species monS, String monName) {
        List<String> out = new ArrayList<>();
        if (mon == null || monS == null || mon.currentHp <= 0) return out;
        String id = safe(mon.heldItemId);
        if (id.isEmpty()) return out;
        String st = mon.status == null ? "none" : mon.status.toLowerCase(Locale.ROOT);
        if ("none".equals(st)) return out;

        boolean cured = false;
        if ((st.equals("paralyze") || st.equals("paralysis")) && PRZ_CURE.contains(id)) cured = true;
        else if ((st.equals("poison") || st.equals("toxic")) && PSN_CURE.contains(id)) cured = true;
        else if (st.equals("burn") && BRN_CURE.contains(id)) cured = true;
        else if ((st.equals("freeze") || st.equals("frozen")) && FRZ_CURE.contains(id)) cured = true;
        else if (st.equals("sleep") && SLF_CURE.contains(id)) cured = true;
        else if (ANY_CURE.contains(id)) cured = true;

        if (!cured) return out;

        // cure
        mon.status = "none";
        mon.toxicCounter = 0;
        mon.sleepTurns = 0;
        consumeBerry(mon, id);

        PokeDemoPlugin plugin = PokeDemoPlugin.INSTANCE;
        LangManager lang = plugin != null ? plugin.getLang() : null;
        String itemName = (lang != null) ? lang.item(id, id) : id;
        out.add((lang != null)
                ? lang.uiFmt("battle.log.berry_cure_status", "§a{mon} 的§f{item}§a治愈了异常状态！", Map.of("mon", monName, "item", itemName))
                : "§a" + monName + " 的§f" + itemName + "§a治愈了异常状态！");
        return out;
    }

    /** Called right after confusion is applied. Bitter Berry / MiracleBerry can cure it. */
    public static List<String> onConfusionApplied(PokemonInstance mon, Species monS, String monName) {
        List<String> out = new ArrayList<>();
        if (mon == null || monS == null || mon.currentHp <= 0) return out;
        if (mon.confusionTurns <= 0) return out;
        String id = safe(mon.heldItemId);
        if (id.equals("bitter_berry") || id.equals("miracleberry")) {
            mon.confusionTurns = 0;
            consumeBerry(mon, id);
            PokeDemoPlugin plugin = PokeDemoPlugin.INSTANCE;
            LangManager lang = plugin != null ? plugin.getLang() : null;
            String itemName = (lang != null) ? lang.item(id, id) : id;
            out.add((lang != null)
                    ? lang.uiFmt("battle.log.berry_cure_confusion", "§a{mon} 的§f{item}§a治愈了混乱！", Map.of("mon", monName, "item", itemName))
                    : "§a" + monName + " 的§f" + itemName + "§a治愈了混乱！");
        }
        return out;
    }

    /**
     * Force-eat a berry immediately (used by Bug Bite / Pluck).
     * This applies the berry's effect right away (healing/cure/confusion cure) and consumes it.
     * Unnerve does NOT block this (attacker is eating the berry, not the holder).
     */
    public static List<String> forceEatBerryNow(PokeDemoPlugin plugin, PokemonInstance eater, Species eaterS, String eaterName, String berryId) {
        List<String> out = new ArrayList<>();
        if (eater == null || eaterS == null || eater.currentHp <= 0) return out;
        String id = safe(berryId);
        if (id.isEmpty()) return out;

        int max = Math.max(1, eater.maxHp(eaterS));

        // Healing berries: heal flat immediately when forced-eaten.
        Integer healFlat = HP_BERRIES.get(id);
        if (healFlat != null) {
            int before = eater.currentHp;
            int ripenMul = AbilityEffects.hasRipen(eater) ? 2 : 1;
            eater.currentHp = Math.min(max, eater.currentHp + healFlat * ripenMul);
            consumeBerry(eater, id);
            int healed = eater.currentHp - before;
            int cheek = applyCheekPouch(eater, eaterS);
            healed += cheek;
            if (healed > 0) {
                LangManager lang = (plugin != null) ? plugin.getLang() : null;
                String itemName = (lang != null) ? lang.item(id, id) : id;
                out.add((lang != null)
                        ? lang.uiFmt("battle.log.eat_berry_heal", "§a{mon} 吃下了§f{item}§a，回复了 §f{n}§a 点HP！", Map.of("mon", eaterName, "item", itemName, "n", String.valueOf(healed)))
                        : "§a" + eaterName + " 吃下了§f" + itemName + "§a，回复了 §f" + healed + "§a 点HP！");
            } else {
                LangManager lang = (plugin != null) ? plugin.getLang() : null;
                String itemName = (lang != null) ? lang.item(id, id) : id;
                out.add((lang != null)
                        ? lang.uiFmt("battle.log.eat_berry", "§a{mon} 吃下了§f{item}§a！", Map.of("mon", eaterName, "item", itemName))
                        : "§a" + eaterName + " 吃下了§f" + itemName + "§a！");
            }
            return out;
        }

        // Status cure berries (Gen2 names)
        String st = eater.status == null ? "none" : eater.status.toLowerCase(Locale.ROOT);
        boolean cured = false;
        if (!"none".equals(st)) {
            if (PRZ_CURE.contains(id) && "paralyze".equals(st)) cured = true;
            else if (PSN_CURE.contains(id) && ("poison".equals(st) || "toxic".equals(st))) cured = true;
            else if (BRN_CURE.contains(id) && "burn".equals(st)) cured = true;
            else if (FRZ_CURE.contains(id) && "freeze".equals(st)) cured = true;
            else if (SLF_CURE.contains(id) && "sleep".equals(st)) cured = true;
            else if (ANY_CURE.contains(id)) cured = true;

            if (cured) {
                eater.status = "none";
                eater.sleepTurns = 0;
                eater.toxicCounter = 0;
                consumeBerry(eater, id);
                LangManager lang = (plugin != null) ? plugin.getLang() : null;
                String itemName = (lang != null) ? lang.item(id, id) : id;
                out.add((lang != null)
                        ? lang.uiFmt("battle.log.eat_berry_cure", "§a{mon} 吃下了§f{item}§a，治愈了异常状态！", Map.of("mon", eaterName, "item", itemName))
                        : "§a" + eaterName + " 吃下了§f" + itemName + "§a，治愈了异常状态！");
                return out;
            }
        }

        // Confusion cure berries
        if (eater.confusionTurns > 0 && (id.equals("bitter_berry") || id.equals("miracleberry"))) {
            eater.confusionTurns = 0;
            consumeBerry(eater, id);
            LangManager lang = (plugin != null) ? plugin.getLang() : null;
            String itemName = (lang != null) ? lang.item(id, id) : id;
            out.add((lang != null)
                    ? lang.uiFmt("battle.log.eat_berry_cure_confusion", "§a{mon} 吃下了§f{item}§a，治愈了混乱！", Map.of("mon", eaterName, "item", itemName))
                    : "§a" + eaterName + " 吃下了§f" + itemName + "§a，治愈了混乱！");
            return out;
        }

        // MiracleBerry: if no major status but confused, also cured above; otherwise just consume for consistency.
        // If the berry has no modeled effect, simply consume it.
        consumeBerry(eater, id);
        {
            LangManager lang = (plugin != null) ? plugin.getLang() : null;
            String itemName = (lang != null) ? lang.item(id, id) : id;
            out.add((lang != null)
                    ? lang.uiFmt("battle.log.eat_berry", "§a{mon} 吃下了§f{item}§a！", Map.of("mon", eaterName, "item", itemName))
                    : "§a" + eaterName + " 吃下了§f" + itemName + "§a！");
        }
        return out;
    }


    /** Healing berries: at end of turn, if HP is low, heal and consume.
     *  We model modern-ish behavior: normally triggers at <= 1/4; with Gluttony triggers at <= 1/2.
     */
    private static List<String> tryEndTurnBerryHeal(PokemonInstance mon, Species monS, String monName) {
        List<String> out = new ArrayList<>();
        PokeDemoPlugin plugin = PokeDemoPlugin.INSTANCE;
        LangManager lang = plugin != null ? plugin.getLang() : null;
        if (mon == null || monS == null || mon.currentHp <= 0) return out;
        String id = safe(mon.heldItemId);
        Integer healFlat = HP_BERRIES.get(id);
        if (healFlat == null) return out;
        int max = Math.max(1, mon.maxHp(monS));
        int threshold = Math.max(1, max / 4);
        if (AbilityEffects.has(mon, "gluttony")) threshold = Math.max(1, max / 2);
        if (mon.currentHp > threshold) return out;
        int before = mon.currentHp;
        int ripenMul = AbilityEffects.hasRipen(mon) ? 2 : 1;
        mon.currentHp = Math.min(max, mon.currentHp + healFlat * ripenMul);
        if (mon.currentHp > before) {
            consumeBerry(mon, id);
            int cheek = applyCheekPouch(mon, monS);
            int healed = mon.currentHp - before;
            String itemName = (lang != null) ? lang.item(id, id) : id;
            out.add((lang != null)
                    ? lang.uiFmt("battle.log.item_heal_hp", "§a{mon} 的§f{item}§a回复了 {n} 点体力！", java.util.Map.of("mon", monName, "item", itemName, "n", String.valueOf(healed)))
                    : ("§a" + monName + " 的§f" + itemName + "§a回复了 " + healed + " 点体力！"));
            if (cheek > 0) out.add("§6【颊囊】§e" + monName + " 额外回复了体力！");
        }
        return out;
    }

    private static void consumeBerry(PokemonInstance mon, String berryId) {
        if (mon == null) return;
        if (berryId != null && !berryId.isBlank()) {
            mon.lastConsumedBerryId = berryId;
            if (AbilityEffects.has(mon, "cudchew")) {
                mon.cudChewBerryId = berryId;
                mon.cudChewTurns = 2;
            }
        }
        mon.heldItemId = null;
    }

    private static int applyCheekPouch(PokemonInstance mon, Species monS) {
        if (mon == null || monS == null || !AbilityEffects.has(mon, "cheekpouch")) return 0;
        int max = Math.max(1, mon.maxHp(monS));
        int heal = Math.max(1, max / 3);
        int before = mon.currentHp;
        mon.currentHp = Math.min(max, mon.currentHp + heal);
        return Math.max(0, mon.currentHp - before);
    }

    public static boolean blocksPowderMoves(PokemonInstance defender, Move move) {
        if (defender == null || move == null) return false;
        if (!"safety_goggles".equals(safe(defender.heldItemId))) return false;
        return POWDER_MOVES.contains(safe(move.id()));
    }

    /** Zoom Lens: if the user moves after the target, accuracy is boosted (we use 1.2x). */
    public static double zoomLensAccuracyMultiplier(PokemonInstance attacker, boolean movedAfterTarget) {
        if (attacker == null) return 1.0;
        if (!movedAfterTarget) return 1.0;
        return "zoom_lens".equals(safe(attacker.heldItemId)) ? 1.2 : 1.0;
    }

/** Items that always act after priority/speed: Lagging Tail, Full Incense. */
    public static boolean forcesLast(PokemonInstance mon) {
        if (mon == null) return false;
        String id = safe(mon.heldItemId);
        return id.equals("lagging_tail") || id.equals("full_incense");
    }

    /** Quick Claw: 20% chance to move first in Gen3+; we use 20% here (only when priority ties). */
    public static boolean quickClawTriggers(PokemonInstance mon) {
        if (mon == null) return false;
        String id = safe(mon.heldItemId);
        if (!id.equals("quick_claw")) return false;
        return Util.RND.nextDouble() < 0.20;
    }

    /** BrightPowder / Lax Incense: reduce opponent accuracy (we apply as multiplier to final hit chance). */
    public static double evasionItemAccuracyMultiplier(PokemonInstance defender) {
        if (defender == null) return 1.0;
        String id = safe(defender.heldItemId);
        if (id.equals("bright_powder") || id.equals("lax_incense")) return 0.9;
        return 1.0;
    }

    /** Attacker-side accuracy boosts (Wide Lens). */
    public static double attackerAccuracyMultiplier(PokemonInstance attacker) {
        if (attacker == null) return 1.0;
        String id = safe(attacker.heldItemId);
        if (id.equals("wide_lens")) return 1.1;
        return 1.0;
    }

    /** Final accuracy multiplier (attacker boost * defender evasion items). */
    public static double accuracyMultiplier(PokemonInstance attacker, PokemonInstance defender) {
        return attackerAccuracyMultiplier(attacker) * evasionItemAccuracyMultiplier(defender);
    }

    /** Crit modifiers from held items. */
    public static double critChanceMultiplier(PokemonInstance attacker) {
        if (attacker == null) return 1.0;
        String id = safe(attacker.heldItemId);
        // very simplified
        if (id.equals("scope_lens") || id.equals("razor_claw")) return 2.0;
        return 1.0;
    }

    /** Lucky Punch: Chansey crit boost (simplified). */
    public static double luckyPunchCrit(PokemonInstance attacker, Species atkS) {
        if (attacker == null || atkS == null) return 1.0;
        if (!"lucky_punch".equals(safe(attacker.heldItemId))) return 1.0;
        return "chansey".equalsIgnoreCase(atkS.id()) ? 2.0 : 1.0;
    }

    /** Damage multiplier from type-boosting held items (Charcoal, Mystic Water, etc). */
    public static double typeBoostMultiplier(PokemonInstance attacker, String moveType) {
        if (attacker == null || moveType == null) return 1.0;
        String id = safe(attacker.heldItemId);

        // plates & type-boost items: we treat as 1.1x
        String t = TYPE_BOOST.get(id);
        if (t != null && t.equalsIgnoreCase(moveType)) return 1.1;

        // Gems are not implemented here (would be one-time).
        return 1.0;
    }

    /**
     * Species-locked type boosts (Soul Dew / Orbs / etc.).
     * Modeled as a move power multiplier (Showdown-style).
     */
    public static double speciesTypeBoostMultiplier(PokemonInstance attacker, Species atkS, String moveType) {
        if (attacker == null || atkS == null || moveType == null) return 1.0;
        String id = safe(attacker.heldItemId);
        String sid = atkS.id() == null ? "" : atkS.id().toLowerCase(Locale.ROOT);
        String t = moveType.toLowerCase(Locale.ROOT);

        // Soul Dew (modern): Latios/Latias boosts Psychic & Dragon moves.
        if (id.equals("soul_dew") && (sid.equals("latios") || sid.equals("latias"))) {
            if (t.equals("psychic") || t.equals("dragon")) return 1.2;
        }

        // Orbs: Dialga / Palkia / Giratina
        if (id.equals("adamant_orb") && sid.equals("dialga")) {
            if (t.equals("dragon") || t.equals("steel")) return 1.2;
        }
        if (id.equals("lustrous_orb") && sid.equals("palkia")) {
            if (t.equals("dragon") || t.equals("water")) return 1.2;
        }
        if (id.equals("griseous_orb") && sid.equals("giratina")) {
            if (t.equals("dragon") || t.equals("ghost")) return 1.2;
        }

        return 1.0;
    }

    /** Generic damage multipliers (Life Orb, Muscle Band, Wise Glasses, Expert Belt, Metronome item, Gems). */
    public static double genericDamageMultiplier(PokemonInstance attacker, Move move, double effectiveness) {
        if (attacker == null || move == null) return 1.0;
        String id = safe(attacker.heldItemId);
        double mul = 1.0;
        if (id.equals("life_orb")) mul *= 1.3;
        if (id.equals("muscle_band") && "physical".equalsIgnoreCase(move.category())) mul *= 1.1;
        if (id.equals("wise_glasses") && "special".equalsIgnoreCase(move.category())) mul *= 1.1;
        if (id.equals("expert_belt") && effectiveness > 1.0) mul *= 1.2;

        // Metronome (held item): consecutive uses of the same move increase power.
        if (id.equals("metronome") && attacker.metronomeCount > 0) {
            // 1.0, 1.2, 1.4, 1.6, 1.8, 2.0 (cap)
            int n = Math.min(5, attacker.metronomeCount);
            mul *= 1.0 + 0.2 * n;
        }

        // One-time gems are not tracked yet (needs consumption). We still support a simple boost if present.
        String gemType = GEM_BOOST.get(id);
        if (gemType != null && gemType.equalsIgnoreCase(move.type())) {
            mul *= 1.3;
        }

        return mul;
    }

    /** Punching Glove: boosts punching moves (Gen9: 1.1x) and prevents contact (contact part handled elsewhere). */
    public static double punchingGloveMultiplier(PokemonInstance attacker, Move move) {
        if (attacker == null || move == null) return 1.0;
        if (!"punching_glove".equals(safe(attacker.heldItemId))) return 1.0;
        if ("status".equalsIgnoreCase(move.category())) return 1.0;
        return PUNCH_MOVES.contains(safe(move.id())) ? 1.1 : 1.0;
    }

    /** EV gain modifiers from held items (Macho Brace / Power items). */
    public static int[] modifyEvYields(PokemonInstance winner, int yHp, int yAtk, int yDef, int ySpa, int ySpd, int ySpe) {
        if (winner == null) return new int[]{yHp, yAtk, yDef, ySpa, ySpd, ySpe};
        String id = safe(winner.heldItemId);

        // Macho Brace: doubles all EVs
        if (id.equals("macho_brace")) {
            yHp *= 2; yAtk *= 2; yDef *= 2; ySpa *= 2; ySpd *= 2; ySpe *= 2;
        }

        // Power items: add +8 EV to one stat
        int bonus = 8;
        switch (id) {
            case "power_weight" -> yHp += bonus;
            case "power_bracer" -> yAtk += bonus;
            case "power_belt" -> yDef += bonus;
            case "power_lens" -> ySpa += bonus;
            case "power_band" -> ySpd += bonus;
            case "power_anklet" -> ySpe += bonus;
        }

        return new int[]{yHp, yAtk, yDef, ySpa, ySpd, ySpe};
    }

    /** Choice items: 1.5 damage boost but lock move. We only apply damage boost for band/specs. */
    public static double choiceDamageMultiplier(PokemonInstance attacker, Move move) {
        if (attacker == null || move == null) return 1.0;
        String id = safe(attacker.heldItemId);
        if (id.equals("choice_band") && "physical".equalsIgnoreCase(move.category())) return 1.5;
        if (id.equals("choice_specs") && "special".equalsIgnoreCase(move.category())) return 1.5;
        return 1.0;
    }

    public static boolean isChoiceItem(String itemId) {
        String id = safe(itemId);
        return id.equals("choice_band") || id.equals("choice_specs") || id.equals("choice_scarf");
    }

    /** Consume a type Gem after it successfully boosts a damaging move. */
    public static List<String> tryConsumeGem(PokemonInstance attacker, String atkName, Move move, int damageDone) {
        List<String> out = new ArrayList<>();
        PokeDemoPlugin plugin = PokeDemoPlugin.INSTANCE;
        LangManager lang = plugin != null ? plugin.getLang() : null;
        if (attacker == null || move == null) return out;
        if (damageDone <= 0) return out;
        String id = safe(attacker.heldItemId);
        if (!id.endsWith("_gem")) return out;
        String want = id.substring(0, id.length() - 4); // remove _gem
        if (want.equalsIgnoreCase(move.type())) {
            attacker.heldItemId = null;
            out.add(lang.uiFmt("battle.log.gem_broken", "§e{mon} 的§f宝石§e碎裂了！", java.util.Map.of("mon", atkName)));
        }
        return out;
    }

    /** Life Orb: recoil after dealing damage. */
    public static List<String> lifeOrbRecoil(PokemonInstance attacker, Species atkS, String atkName, int damageDone) {
        List<String> out = new ArrayList<>();
        if (attacker == null || atkS == null) return out;
        if (damageDone <= 0) return out;
        if (!safe(attacker.heldItemId).equals("life_orb")) return out;
        int max = Math.max(1, attacker.maxHp(atkS));
        int recoil = Math.max(1, max / 10);
        int before = attacker.currentHp;
        attacker.currentHp = Math.max(0, attacker.currentHp - recoil);
        out.add("§c" + atkName + " 因§f生命宝珠§c受到了 §c" + (before - attacker.currentHp) + "§c 点伤害。" );
        return out;
    }

    /** Species-specific power items. */
    public static double speciesAttackMultiplier(PokemonInstance attacker, Species atkS, Move move) {
        if (attacker == null || atkS == null || move == null) return 1.0;
        String id = safe(attacker.heldItemId);
        String sid = atkS.id().toLowerCase(Locale.ROOT);
        if (id.equals("light_ball") && sid.equals("pikachu")) return 2.0;
        if (id.equals("thick_club") && (sid.equals("cubone") || sid.equals("marowak"))) {
            if ("physical".equalsIgnoreCase(move.category())) return 2.0;
        }
        return 1.0;
    }

    /** End-of-turn healing/damage items (Leftovers/Black Sludge). */
    public static List<String> endTurnItemResidual(PokeDemoPlugin plugin, PokemonInstance mon, Species monS, String monName, boolean berriesBlocked) {
        List<String> out = new ArrayList<>();
        if (mon == null || monS == null || mon.currentHp <= 0) return out;
        String id = safe(mon.heldItemId);
        int max = Math.max(1, mon.maxHp(monS));

        // Gen2 berries (healing / curing) trigger at end of turn.
        // Unnerve: prevents opponents from eating berries.
        if (!berriesBlocked) {
            out.addAll(tryEndTurnBerryHeal(mon, monS, monName));
            // If healed berry consumed, refresh id.
            id = safe(mon.heldItemId);
            // Some cure berries may still be held if status came from residuals.
            out.addAll(onStatusApplied(mon, monS, monName));
            id = safe(mon.heldItemId);
            // Confusion cure berries
            out.addAll(onConfusionApplied(mon, monS, monName));
            id = safe(mon.heldItemId);
        }

        if (id.equals("leftovers")) {
            int heal = Math.max(1, max / 16);
            int before = mon.currentHp;
            mon.currentHp = Math.min(max, mon.currentHp + heal);
            if (mon.currentHp > before) out.add(plugin.getLang().uiFmt(
                    "battle.log.item.leftovers.heal",
                    "§a{mon} 的§f{item}§a回复了 §c{amt}§a 点体力。",
                    java.util.Map.of(
                            "mon", monName,
                            "item", plugin.getLang().item("leftovers", "剩饭"),
                            "amt", String.valueOf(mon.currentHp - before)
                    )
            ));
        } else if (id.equals("black_sludge")) {
            // If Poison type: heal, else damage
            boolean poisonType = monS.types().stream().anyMatch(t -> "poison".equalsIgnoreCase(t));
            int amt = Math.max(1, max / 16);
            int before = mon.currentHp;
            if (poisonType) {
                mon.currentHp = Math.min(max, mon.currentHp + amt);
                if (mon.currentHp > before) out.add(plugin.getLang().uiFmt(
                        "battle.log.item.black_sludge.heal",
                        "§a{mon} 的§f{item}§a回复了 §c{amt}§a 点体力。",
                        java.util.Map.of(
                                "mon", monName,
                                "item", plugin.getLang().item("black_sludge", "黑色淤泥"),
                                "amt", String.valueOf(mon.currentHp - before)
                        )
                ));
            } else {
                mon.currentHp = Math.max(0, mon.currentHp - amt);
                out.add(plugin.getLang().uiFmt(
                        "battle.log.item.black_sludge.damage",
                        "§c{mon} 因§f{item}§c受到了 §c{amt}§c 点伤害。",
                        java.util.Map.of(
                                "mon", monName,
                                "item", plugin.getLang().item("black_sludge", "黑色淤泥"),
                                "amt", String.valueOf(before - mon.currentHp)
                        )
                ));
            }
        }

        // Status Orbs: apply if not already statused.
        if (mon.currentHp > 0 && ("none".equalsIgnoreCase(mon.status) || mon.status == null || mon.status.isBlank())) {
            if (id.equals("toxic_orb")) {
                mon.status = "poison"; // simplified: treat as poison
                out.add(plugin.getLang().uiFmt(
                        "battle.log.item.status_orb.toxic_orb",
                        "§d{mon} 被§f{item}§d的力量影响，{status}了！",
                        java.util.Map.of(
                                "mon", monName,
                                "item", plugin.getLang().item("toxic_orb", "剧毒珠"),
                                "status", plugin.getLang().statusName("poison", "中毒")
                        )
                ));
            } else if (id.equals("flame_orb")) {
                mon.status = "burn";
                out.add(plugin.getLang().uiFmt(
                        "battle.log.item.status_orb.flame_orb",
                        "§6{mon} 被§f{item}§6的力量影响，{status}了！",
                        java.util.Map.of(
                                "mon", monName,
                                "item", plugin.getLang().item("flame_orb", "火焰珠"),
                                "status", plugin.getLang().statusName("burn", "灼伤")
                        )
                ));
            }
        }

        // Sticky Barb: damage each turn (transfer on contact not implemented yet)
        if (id.equals("sticky_barb")) {
            int dmg = Math.max(1, max / 8);
            int before = mon.currentHp;
            mon.currentHp = Math.max(0, mon.currentHp - dmg);
            out.add(plugin.getLang().uiFmt(
                    "battle.log.item.sticky_barb.damage",
                    "§c{mon} 因§f{item}§c受到了 §c{amt}§c 点伤害。",
                    java.util.Map.of(
                            "mon", monName,
                            "item", plugin.getLang().item("sticky_barb", "黏黏钩"),
                            "amt", String.valueOf(before - mon.currentHp)
                    )
            ));
        }
        return out;
    }

    /** King's Rock / Razor Fang: 10% flinch on damaging moves (simplified, ignores multi-hit nuances). */
    public static boolean canItemCauseFlinch(PokemonInstance attacker) {
        if (attacker == null) return false;
        String id = safe(attacker.heldItemId);
        return id.equals("kings_rock") || id.equals("razor_fang");
    }

    public static boolean rollFlinchFromItem() {
        return Util.RND.nextDouble() < 0.10;
    }

    /** Speed multipliers from held items (Choice Scarf, Macho Brace, Iron Ball, Quick Powder). */
    public static double speedMultiplier(PokemonInstance mon, Species monS) {
        if (mon == null) return 1.0;
        String id = safe(mon.heldItemId);
        double mul = 1.0;
        if (id.equals("choice_scarf")) mul *= 1.5;
        if (id.equals("macho_brace")) mul *= 0.5;
        if (id.equals("iron_ball")) mul *= 0.5;
        if (id.equals("quick_powder") && monS != null && "ditto".equalsIgnoreCase(monS.id())) mul *= 2.0;
        return mul;
    }

    /** Berserk Gene: on entry, sharply raises Attack and causes confusion; then consumed. */
    public static List<String> onEntry(PokemonInstance mon, Species monS, String monName) {
        List<String> out = new ArrayList<>();
        if (mon == null || monS == null || mon.currentHp <= 0) return out;
        String id = safe(mon.heldItemId);
        if (id.equals("berserk_gene")) {
            mon.applyStage("atk", 2);
            if (mon.confusionTurns <= 0) mon.confusionTurns = 2 + Util.RND.nextInt(4); // 2-5
            mon.heldItemId = null;
            out.add("§d" + monName + " 的§f破坏基因§d发动了！攻击大幅提升，但陷入混乱！");
        }
        return out;
    }

    /** White Herb: when any stat is lowered, restore all lowered stats once; then consumed. */
    public static List<String> onStatLowered(PokemonInstance mon, String monName) {
        List<String> out = new ArrayList<>();
        if (mon == null || mon.currentHp <= 0) return out;
        if (!safe(mon.heldItemId).equals("white_herb")) return out;

        boolean anyLowered = mon.stageAtk < 0 || mon.stageDef < 0 || mon.stageSpa < 0 || mon.stageSpd < 0
                || mon.stageSpe < 0 || mon.stageAccuracy < 0 || mon.stageEvasion < 0;

        if (!anyLowered) return out;

        if (mon.stageAtk < 0) mon.stageAtk = 0;
        if (mon.stageDef < 0) mon.stageDef = 0;
        if (mon.stageSpa < 0) mon.stageSpa = 0;
        if (mon.stageSpd < 0) mon.stageSpd = 0;
        if (mon.stageSpe < 0) mon.stageSpe = 0;
        if (mon.stageAccuracy < 0) mon.stageAccuracy = 0;
        if (mon.stageEvasion < 0) mon.stageEvasion = 0;

        mon.heldItemId = null;
        out.add("§e" + monName + " 的§f白色香草§e恢复了被降低的能力！");
        return out;
    }

    /** On being hit by a damaging move: trigger one-time reactive items (Absorb Bulb, Cell Battery, Luminous Moss, Snowball). */
    public static List<String> onDamagedByMove(PokemonInstance defender, Species defS, String defName, Move move, int damageDealt, double effectiveness) {
        List<String> out = new ArrayList<>();
        if (defender == null || defS == null || move == null) return out;
        if (damageDealt <= 0 || defender.currentHp <= 0) return out;
        String id = safe(defender.heldItemId);
        // Weakness Policy: when hit super-effectively, raise Attack & SpA by 2 stages, then consume.
        if (id.equals("weakness_policy") && effectiveness > 1.0 && damageDealt > 0) {
            defender.applyStage("atk", 2);
            defender.applyStage("spa", 2);
            defender.heldItemId = null;
            out.add("§d" + defName + " 的§f弱点保险§d发动了！攻击和特攻大幅提升！");
            return out;
        }

        // Air Balloon pops when hit by a damaging move.
        if (id.equals("air_balloon")) {
            defender.heldItemId = null;
            out.add("§e" + defName + " 的§f气球§e破了！");
            return out;
        }

        // Eject Button: when hit by a damaging move, force a switch out (handled by MoveEngine).
        // We only mark consumption + message here; the actual switching needs access to party storage.
        if (id.equals("eject_button")) {
            defender.heldItemId = null;
            out.add("§e" + defName + " 的§f逃脱按键§e发动了！");
            return out;
        }


        String mtype = move.type() == null ? "" : move.type().toLowerCase(Locale.ROOT);

        if ("status".equalsIgnoreCase(move.category())) return out;

        if (id.equals("absorb_bulb") && mtype.equals("water")) {
            defender.applyStage("spa", 1);
            defender.heldItemId = null;
            out.add("§a" + defName + " 的§f吸收球§a发动了！特攻提升了！");
        } else if (id.equals("cell_battery") && mtype.equals("electric")) {
            defender.applyStage("atk", 1);
            defender.heldItemId = null;
            out.add("§a" + defName + " 的§f充电电池§a发动了！攻击提升了！");
        } else if (id.equals("luminous_moss") && mtype.equals("water")) {
            defender.applyStage("spd", 1);
            defender.heldItemId = null;
            out.add("§a" + defName + " 的§f光苔§a发动了！特防提升了！");
        } else if (id.equals("snowball") && mtype.equals("ice")) {
            defender.applyStage("atk", 1);
            defender.heldItemId = null;
            out.add("§a" + defName + " 的§f雪球§a发动了！攻击提升了！");
        }

        return out;
    }

    /** Stick: Farfetch'd crit boost (simplified). */
    public static double stickCrit(PokemonInstance attacker, Species atkS) {
        if (attacker == null || atkS == null) return 1.0;
        if (!"stick".equals(safe(attacker.heldItemId))) return 1.0;
        return "farfetchd".equalsIgnoreCase(atkS.id()) ? 2.0 : 1.0;
    }

    private static String safe(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).trim();
    }

    /** Conservative check for items that should not be removed/knocked off/exchanged. */
    public static boolean isUnremovableItemId(String itemId) {
        if (itemId == null || itemId.isBlank()) return false;
        String iid = itemId.toLowerCase(java.util.Locale.ROOT);
        // Drives / Memories / Plates are species-locked items and should not be removed.
        if (iid.endsWith("_drive") || iid.endsWith("_memory") || iid.endsWith("_plate")) return true;
        // Common unremovable/key items in modern gens (safe patterns).
        if (iid.contains("mega") && iid.endsWith("_stone")) return true;
        if (iid.endsWith("_z") || iid.contains("z_crystal") || iid.endsWith("_crystal")) return true;
        if ("griseous_orb".equals(iid) || "rusted_sword".equals(iid) || "rusted_shield".equals(iid)) return true;
        return false;
    }

    /** Rough fling power table (covers common items; unknown defaults to 30). */
    public static int flingPower(String itemId) {
        if (itemId == null || itemId.isBlank()) return 0;
        String id = itemId.toLowerCase(java.util.Locale.ROOT);

        // Berries (Gen2 and modern-like): very low.
        if (id.contains("berry")) return 10;

        // High-power heavy items
        if (id.equals("iron_ball")) return 130;

        // 100-power stones / plates-like (we keep conservative)
        if (id.endsWith("_stone") || id.endsWith("_plate")) return 80;

        // Common held items
        if (id.equals("poison_barb")) return 70;

        // Orbs and many consumables are 30
        if (id.endsWith("_orb") || id.contains("orb")) return 30;
        if (id.equals("black_sludge") || id.equals("life_orb")) return 30;
        if (id.equals("light_ball")) return 30;
        if (id.equals("kings_rock") || id.equals("razor_fang")) return 30;

        // Leftovers / Choice items and many "utility" items are low
        if (id.equals("leftovers")) return 10;
        if (id.contains("choice_") || id.endsWith("_band") || id.endsWith("_specs") || id.endsWith("_scarf")) return 10;
        if (id.equals("focus_sash") || id.equals("focus_band")) return 10;

        return 30;
    }



    private static final Map<String, String> TYPE_BOOST = new HashMap<>();
    static {
        // Common type-boost items (not exhaustive; extend freely)
        TYPE_BOOST.put("black_belt", "fighting");
        TYPE_BOOST.put("black_glasses", "dark");
        TYPE_BOOST.put("charcoal", "fire");
        TYPE_BOOST.put("dragon_fang", "dragon");
        TYPE_BOOST.put("hard_stone", "rock");
        TYPE_BOOST.put("magnet", "electric");
        TYPE_BOOST.put("metal_coat", "steel");
        TYPE_BOOST.put("miracle_seed", "grass");
        TYPE_BOOST.put("mystic_water", "water");
        TYPE_BOOST.put("never_melt_ice", "ice");
        TYPE_BOOST.put("poison_barb", "poison");
        TYPE_BOOST.put("sharp_beak", "flying");
        TYPE_BOOST.put("silk_scarf", "normal");
        TYPE_BOOST.put("silver_powder", "bug");
        TYPE_BOOST.put("soft_sand", "ground");
        TYPE_BOOST.put("spell_tag", "ghost");
        TYPE_BOOST.put("twisted_spoon", "psychic");
        TYPE_BOOST.put("pink_bow", "normal");
        TYPE_BOOST.put("polkadot_bow", "normal");
        TYPE_BOOST.put("fairy_feather", "fairy");
        // Incenses (subset)
        TYPE_BOOST.put("odd_incense", "psychic");
        TYPE_BOOST.put("rock_incense", "rock");
        TYPE_BOOST.put("sea_incense", "water");
        TYPE_BOOST.put("wave_incense", "water");
        TYPE_BOOST.put("rose_incense", "grass");

        // Plates (Arceus plates)
        TYPE_BOOST.put("flame_plate", "fire");
        TYPE_BOOST.put("splash_plate", "water");
        TYPE_BOOST.put("zap_plate", "electric");
        TYPE_BOOST.put("meadow_plate", "grass");
        TYPE_BOOST.put("icicle_plate", "ice");
        TYPE_BOOST.put("fist_plate", "fighting");
        TYPE_BOOST.put("toxic_plate", "poison");
        TYPE_BOOST.put("earth_plate", "ground");
        TYPE_BOOST.put("sky_plate", "flying");
        TYPE_BOOST.put("mind_plate", "psychic");
        TYPE_BOOST.put("insect_plate", "bug");
        TYPE_BOOST.put("stone_plate", "rock");
        TYPE_BOOST.put("spooky_plate", "ghost");
        TYPE_BOOST.put("draco_plate", "dragon");
        TYPE_BOOST.put("dread_plate", "dark");
        TYPE_BOOST.put("iron_plate", "steel");
        TYPE_BOOST.put("pixie_plate", "fairy");
    }

    private static final Map<String, String> GEM_BOOST = new HashMap<>();
    static {
        // Representative set; extend freely. (Boost is applied if a gem is held; consumption not tracked yet.)
        GEM_BOOST.put("fire_gem", "fire");
        GEM_BOOST.put("water_gem", "water");
        GEM_BOOST.put("electric_gem", "electric");
        GEM_BOOST.put("grass_gem", "grass");
        GEM_BOOST.put("ice_gem", "ice");
        GEM_BOOST.put("fighting_gem", "fighting");
        GEM_BOOST.put("poison_gem", "poison");
        GEM_BOOST.put("ground_gem", "ground");
        GEM_BOOST.put("flying_gem", "flying");
        GEM_BOOST.put("psychic_gem", "psychic");
        GEM_BOOST.put("bug_gem", "bug");
        GEM_BOOST.put("rock_gem", "rock");
        GEM_BOOST.put("ghost_gem", "ghost");
        GEM_BOOST.put("dragon_gem", "dragon");
        GEM_BOOST.put("dark_gem", "dark");
        GEM_BOOST.put("steel_gem", "steel");
        GEM_BOOST.put("normal_gem", "normal");
        GEM_BOOST.put("fairy_gem", "fairy");
    }
}
