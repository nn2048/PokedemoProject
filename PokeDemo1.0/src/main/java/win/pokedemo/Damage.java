
package win.pokedemo;

public final class Damage {
    private Damage() {}

    // Last damage roll crit flag (main thread). Used for abilities like Anger Point.
    private static boolean LAST_CRIT = false;
    public static boolean lastWasCrit() { return LAST_CRIT; }

    private static String effectiveHeldItemId(PokemonInstance mon) {
        if (mon == null) return null;
        BattleSession ctx = AbilityEffects.contextSession();
        if (ctx != null && ctx.magicRoomTurns > 0) return null;
        if (mon.embargoTurns > 0) return null;
        if (AbilityEffects.isItemSuppressedByKlutz(mon)) return null;
        return mon.heldItemId;
    }

    public static int calcDamage(PokemonInstance atk, PokemonInstance def, Species atkS, Species defS, Move move) {
        int level = atk.level;

        boolean special = "special".equalsIgnoreCase(move.category());
        String moveIdNorm = move == null || move.id() == null ? "" : move.id().toLowerCase(java.util.Locale.ROOT).replace("_", "");
        int A;
        if ("bodypress".equals(moveIdNorm)) {
            A = atk.calcStat(atkS, "def", atk.ivDef, atk.evDef, false);
        } else if ("foulplay".equals(moveIdNorm) && def != null && defS != null) {
            boolean useSpA = false;
            A = def.calcStat(defS, useSpA ? "spa" : "atk", useSpA ? def.ivSpa : def.ivAtk, useSpA ? def.evSpa : def.evAtk, false);
        } else if (("photongeyser".equals(moveIdNorm) || "lightthatburnsthesky".equals(moveIdNorm)) && atk != null && atkS != null) {
            int atkAtk = atk.calcStat(atkS, "atk", atk.ivAtk, atk.evAtk, false);
            int atkSpa = atk.calcStat(atkS, "spa", atk.ivSpa, atk.evSpa, false);
            boolean useSpa = atkSpa > atkAtk;
            A = atk.calcStat(atkS, useSpa ? "spa" : "atk", useSpa ? atk.ivSpa : atk.ivAtk, useSpa ? atk.evSpa : atk.evAtk, false);
        } else {
            A = atk.calcStat(atkS, special ? "spa" : "atk", special ? atk.ivSpa : atk.ivAtk, special ? atk.evSpa : atk.evAtk, false);
        }

        // Stat stages (Unaware: defender ignores attacker's offensive stages)
        double aMul = 1.0;
        if (!(def != null && AbilityEffects.has(def, "unaware"))) {
            if ("bodypress".equals(moveIdNorm)) aMul = atk.stageMultiplier("def");
            else if ("foulplay".equals(moveIdNorm) && def != null) aMul = def.stageMultiplier("atk");
            else aMul = atk.stageMultiplier(special ? "spa" : "atk");
        }
        A = (int) Math.max(1, Math.floor(A * aMul));

        // Burn halves physical attack (modern rule, simplified)
        if (!special && "burn".equalsIgnoreCase(atk.status)) {
            A = Math.max(1, A / 2);
        }

        // Deep Sea Tooth: Clamperl's SpA is doubled.
        if (special && atk != null && atkS != null && "deep_sea_tooth".equalsIgnoreCase(atk.heldItemId)) {
            try {
                if ("clamperl".equalsIgnoreCase(atkS.id())) A = Math.max(1, A * 2);
            } catch (Throwable ignored) {}
        }
        A = (int) Math.max(1, Math.floor(A * HeldItemEffects.speciesAttackMultiplier(atk, atkS, move)));

        boolean targetPhysicalDef = !special || "psyshock".equals(moveIdNorm) || "psystrike".equals(moveIdNorm) || "secretsword".equals(moveIdNorm);
        int D = def.calcStat(defS,
                targetPhysicalDef ? "def" : "spd",
                targetPhysicalDef ? def.ivDef : def.ivSpd,
                targetPhysicalDef ? def.evDef : def.evSpd,
                false);

        // Ability: defensive stat modifiers (e.g., Marvel Scale / Fur Coat)
        D = (int) Math.max(1, Math.floor(D * AbilityEffects.defenseStatMultiplier(def, special)));


        // Metal Powder: Ditto gets 1.5x Def/SpD while not transformed.
        if (def != null && defS != null && "metal_powder".equalsIgnoreCase(effectiveHeldItemId(def))) {
            try {
                boolean isDitto = "ditto".equalsIgnoreCase(defS.id());
                boolean transformed = def.overrideSpeciesId != null && !def.overrideSpeciesId.isBlank();
                if (isDitto && !transformed) D = (int) Math.floor(D * 1.5);
            } catch (Throwable ignored) {}
        }

        // Deep Sea Scale: Clamperl's SpD is doubled.
        if (special && def != null && defS != null && "deep_sea_scale".equalsIgnoreCase(effectiveHeldItemId(def))) {
            try {
                if ("clamperl".equalsIgnoreCase(defS.id())) D = Math.max(1, D * 2);
            } catch (Throwable ignored) {}
        }

        // Eviolite: boost Def/SpD by 50% if the Pokémon is not fully evolved.
        if (def != null && defS != null && "eviolite".equalsIgnoreCase(effectiveHeldItemId(def))) {
            try {
                boolean canEvolve = !defS.evolutionsSafe().isEmpty();
                if (canEvolve) D = (int) Math.floor(D * 1.5);
            } catch (Throwable ignored) {}
        }

        // Assault Vest: boost SpD by 50% but prevents status moves (status restriction handled elsewhere).
        if (special && def != null && "assault_vest".equalsIgnoreCase(effectiveHeldItemId(def))) {
            D = (int) Math.floor(D * 1.5);
        }

        // Defensive stages (Unaware: attacker ignores defender's defensive stages)
        double dMul = 1.0;
        if (!(atk != null && AbilityEffects.has(atk, "unaware"))) {
            boolean ignoreDefBoosts = "sacredsword".equals(moveIdNorm) || "secret_sword".equals(moveIdNorm)
                    || "darkestlariat".equals(moveIdNorm) || "chipaway".equals(moveIdNorm)
                    || "psyshock".equals(moveIdNorm) || "psystrike".equals(moveIdNorm);
            if (ignoreDefBoosts) {
                int stage = targetPhysicalDef ? def.stageDef : def.stageSpd;
                dMul = def.stageMultiplier(targetPhysicalDef ? "def" : "spd");
                if (stage > 0) dMul = 1.0;
            } else {
                dMul = def.stageMultiplier(targetPhysicalDef ? "def" : "spd");
            }
        }
        D = (int) Math.max(1, Math.floor(D * dMul));

        int movePower = effectivePower(move, atk, atkS, def, defS);
        double base = (((2.0 * level) / 5.0 + 2) * movePower * A / Math.max(1.0, D)) / 50.0 + 2;

        // Active types: allow battle-only overrides (e.g., Conversion).
        java.util.List<String> atkTypes = (atk.overrideType1 != null && !atk.overrideType1.isBlank())
                ? java.util.Arrays.asList(atk.overrideType1.toLowerCase(), atk.overrideType2 == null ? "" : atk.overrideType2.toLowerCase())
                    .stream().filter(t -> t != null && !t.isBlank()).collect(java.util.stream.Collectors.toList())
                : atkS.types();

        java.util.List<String> defTypes = effectiveDefTypes(def, defS);

        // Effective move type (some held items can override signature move types)
        String moveType = HeldItemEffects.overrideMoveType(atk, atkS, move);
        if (atk != null && AbilityEffects.has(atk, "liquidvoice") && move != null && move.id() != null) {
            try {
                java.lang.reflect.Method m = AbilityEffects.class.getDeclaredMethod("norm", String.class);
            } catch (Throwable ignored) {}
        }
        if (atk != null && AbilityEffects.has(atk, "liquidvoice") && move != null) {
            String midLV = move.id() == null ? "" : move.id().toLowerCase(java.util.Locale.ROOT).replace("_", "").replace("-", "").replace(" ", "");
            java.util.Set<String> soundMoves = java.util.Set.of("boomburst","bugbuzz","chatter","clangingscales","clangoroussoul","confide","disarmingvoice","echoedvoice","eerieimpulse","growl","healbell","howl","hypervoice","metalsound","nobleroar","overdrive","partingshot","perishsong","relicsong","roar","round","screech","sing","snarl","snore","sparklingaria","supersonic","torchsong","uproar");
            if (soundMoves.contains(midLV)) moveType = "water";
        }
        BattleSession ctxMoveType = AbilityEffects.contextSession();
        if (atk != null && atk.electrifiedThisTurn) moveType = "electric";
        else if (ctxMoveType != null && ctxMoveType.ionDelugeActive && "normal".equalsIgnoreCase(moveType)) moveType = "electric";
        String midType = move == null || move.id() == null ? "" : move.id().toLowerCase(java.util.Locale.ROOT).replace("_", "");
        if ("weatherball".equals(midType)) {
            BattleSession ctx = AbilityEffects.contextSession();
            if (ctx != null) {
                WeatherType ew = WeatherSystem.effectiveWeather(ctx);
                if (ew == WeatherType.SUN) moveType = "fire";
                else if (ew == WeatherType.RAIN) moveType = "water";
                else if (ew == WeatherType.SAND) moveType = "rock";
                else if (ew == WeatherType.HAIL) moveType = "ice";
            }
        }

        // STAB
        double stab = atkTypes.contains(moveType) ? 1.5 : 1.0;
        // Adaptability: STAB becomes 2.0
        if (stab > 1.0 && AbilityEffects.has(atk, "adaptability")) stab = 2.0;

        // Type effectiveness
        double eff = TypeChart.effectiveness(moveType, defTypes);
        if (eff == 0.0 && def != null && def.identifiedTarget
                && ("normal".equalsIgnoreCase(moveType) || "fighting".equalsIgnoreCase(moveType))) {
            eff = 1.0;
        }
        if (eff == 0.0 && def != null && def.miracleEyeTarget && "psychic".equalsIgnoreCase(moveType)) {
            eff = 1.0;
        }

        // Scrappy / Mind's Eye: Normal/Fighting moves can hit Ghost types.
        if (eff == 0.0 && atk != null && (AbilityEffects.has(atk, "scrappy") || AbilityEffects.has(atk, "mindseye"))
                && defTypes.stream().anyMatch(t -> "ghost".equalsIgnoreCase(t))) {
            if ("normal".equalsIgnoreCase(moveType) || "fighting".equalsIgnoreCase(moveType)) {
                eff = 1.0;
            }
        }
        if (def != null && def.tarShotActive && "fire".equalsIgnoreCase(moveType)) eff *= 2.0;
        BattleSession terrainCtx = AbilityEffects.contextSession();

        // Air Balloon: grants Ground immunity until popped.
        if (def != null && "ground".equalsIgnoreCase(moveType) && "air_balloon".equalsIgnoreCase(effectiveHeldItemId(def))) {
            BattleSession ctx = AbilityEffects.contextSession();
            if ((ctx == null || ctx.gravityTurns <= 0) && !def.groundedBySmackDown) eff = 0.0;
        }
        // Ring Target: removes type-based immunities from the holder.
        // We model this as: if the move would deal 0x due to immunity, treat it as neutral instead.
        if (eff == 0.0 && def != null && "ring_target".equalsIgnoreCase(effectiveHeldItemId(def))) {
            eff = 1.0;
        }

        // Iron Ball: removes Ground-type immunity from the holder (e.g. Flying type immunity).
        if (eff == 0.0 && def != null && "ground".equalsIgnoreCase(moveType) && "iron_ball".equalsIgnoreCase(effectiveHeldItemId(def))) {
            eff = 1.0;
        }
        if (def != null && "ground".equalsIgnoreCase(moveType) && (def.magnetRiseTurns > 0 || def.telekinesisTurns > 0)) {
            BattleSession ctx2 = AbilityEffects.contextSession();
            if ((ctx2 == null || ctx2.gravityTurns <= 0) && !def.groundedBySmackDown) eff = 0.0;
        }

        // Held item damage modifiers
        double itemMul = 1.0;
        if (terrainCtx != null && terrainCtx.terrain != null && !terrainCtx.terrain.isBlank()) {
            String terr = terrainCtx.terrain.toLowerCase(java.util.Locale.ROOT);
            if ("electric".equals(terr) && "electric".equalsIgnoreCase(moveType) && terrainGrounded(atk, atkS, terrainCtx)) itemMul *= 1.3;
            if ("grassy".equals(terr) && "grass".equalsIgnoreCase(moveType) && terrainGrounded(atk, atkS, terrainCtx)) itemMul *= 1.3;
            if ("psychic".equals(terr) && "psychic".equalsIgnoreCase(moveType) && terrainGrounded(atk, atkS, terrainCtx)) itemMul *= 1.3;
            if ("misty".equals(terr) && "dragon".equalsIgnoreCase(moveType) && terrainGrounded(def, defS, terrainCtx)) itemMul *= 0.5;
        }
        boolean itemsActive = effectiveHeldItemId(atk) != null || effectiveHeldItemId(def) != null;
        if (itemsActive) {
            itemMul *= HeldItemEffects.typeBoostMultiplier(atk, moveType);
            itemMul *= HeldItemEffects.speciesTypeBoostMultiplier(atk, atkS, moveType);
            itemMul *= HeldItemEffects.choiceDamageMultiplier(atk, move);
            itemMul *= HeldItemEffects.genericDamageMultiplier(atk, move, eff);
            itemMul *= HeldItemEffects.punchingGloveMultiplier(atk, move);
        }


        // Ability damage modifiers
        itemMul *= AbilityEffects.attackerDamageMultiplier(atk, def, move, moveType, eff);
        // Sheer Force: boost damage by 1.3 if the move has removable secondary effects.
        if (AbilityEffects.hasSheerForce(atk) && AbilityEffects.moveHasSecondaryEffects(move)) {
            itemMul *= 1.3;
        }
        itemMul *= AbilityEffects.defenderDamageMultiplier(def, defS, moveType, eff, atk);
        if (def != null && move != null) {
            String da = def.abilityId == null ? "" : def.abilityId.toLowerCase(java.util.Locale.ROOT);
            boolean moveIsSpecial = "special".equalsIgnoreCase(move.category());
            boolean moveIsPhysical = "physical".equalsIgnoreCase(move.category());
            if ("icescales".equals(da) && moveIsSpecial && move.power() > 0) itemMul *= 0.5;
            if ("fluffy".equals(da) && moveIsPhysical && move.power() > 0) itemMul *= 0.5;
            if ("fluffy".equals(da) && "fire".equalsIgnoreCase(moveType)) itemMul *= 2.0;
            if ("tabletsofruin".equals(da) && moveIsPhysical && move.power() > 0) itemMul *= 0.75;
            if ("vesselofruin".equals(da) && moveIsSpecial && move.power() > 0) itemMul *= 0.75;
        }
        // Flash Fire boost
        if (atk != null && atk.flashFireBoost && "fire".equalsIgnoreCase(moveType)) itemMul *= 1.5;


        // Crit
        double critChance = 0.0625; // 1/16

        // Shell Armor / Battle Armor: cannot be critically hit.
        // Lucky Chant also blocks critical hits for the protected side.
        // Suppressed by Neutralizing Gas; ignored by Mold Breaker family.
        try {
            BattleSession ctx = AbilityEffects.contextSession();
            if (ctx != null && def != null) {
                if (def == ctx.playerMon && ctx.playerLuckyChantTurns > 0) critChance = 0.0;
                if (def == ctx.wildMon && ctx.wildLuckyChantTurns > 0) critChance = 0.0;
            }
            if (def != null && !AbilityEffects.ignoresDefenderAbility(atk)) {
                if (AbilityEffects.has(def, "shellarmor") || AbilityEffects.has(def, "battlearmor")) {
                    critChance = 0.0;
                }
            }
        } catch (Throwable ignored) {}

        critChance *= HeldItemEffects.critChanceMultiplier(atk);
        if (atk != null && AbilityEffects.has(atk, "superluck")) critChance *= 2.0;
        critChance *= HeldItemEffects.luckyPunchCrit(atk, atkS);
        critChance *= HeldItemEffects.stickCrit(atk, atkS);
        // Dire Hit increases crit chance (simplified Gen1-ish behavior).
        if (atk != null && atk.direHitActive) {
            critChance = critChance * 4.0; // 1/4
        }
        if (atk != null && atk.focusEnergyActive) {
            // Gen1 Focus Energy bug: instead of boosting crit rate, it quarters it.
            critChance = critChance / 4.0;
        }
        String critMoveId = move == null || move.id() == null ? "" : move.id().toLowerCase(java.util.Locale.ROOT).replace("_", "");
        if ("frostbreath".equals(critMoveId) || "stormthrow".equals(critMoveId)) {
            critChance = 1.0;
        }
        if (critChance > 1.0) critChance = 1.0;
        if (atk != null && atk.laserFocusTurns > 0) critChance = 1.0;
        if (atk != null && AbilityEffects.has(atk, "merciless") && def != null) {
            String st = def.status == null ? "" : def.status.toLowerCase(java.util.Locale.ROOT);
            if ("poison".equals(st) || "toxic".equals(st)) critChance = 1.0;
        }
        boolean isCrit = (Util.RND.nextDouble() < critChance);
        double crit = isCrit ? 1.5 : 1.0;
        LAST_CRIT = isCrit;

        // Random factor
        double rnd = 0.85 + Util.RND.nextDouble() * 0.15;

        int dmg = (int)Math.floor(base * stab * eff * crit * rnd * itemMul);
        if (eff == 0.0 || itemMul <= 0.0) return 0;
        return Math.max(1, dmg);
    }

    /**
     * Confusion self-hit damage. Approximates the classic mechanic:
     * a typeless 40-power physical attack against itself (no STAB, no type effectiveness).
     */
    public static int calcConfusionSelfDamage(PokemonInstance self, Species selfS) {
        int level = self.level;
        int A = self.calcStat(selfS, "atk", self.ivAtk, self.evAtk, false);
        int D = self.calcStat(selfS, "def", self.ivDef, self.evDef, false);

        // Apply stat stages
        A = (int) Math.max(1, Math.floor(A * self.stageMultiplier("atk")));
        D = (int) Math.max(1, Math.floor(D * self.stageMultiplier("def")));

        double base = (((2.0 * level) / 5.0 + 2) * 40.0 * A / Math.max(1.0, D)) / 50.0 + 2;
        double rnd = 0.85 + Util.RND.nextDouble() * 0.15;
        int dmg = (int) Math.floor(base * rnd);
        return Math.max(1, dmg);
    }


    private static java.util.List<String> effectiveDefTypes(PokemonInstance def, Species defS) {
        java.util.List<String> base;
        if (def != null && def.overrideType1 != null && !def.overrideType1.isBlank()) {
            base = java.util.Arrays.asList(def.overrideType1, def.overrideType2 == null ? "" : def.overrideType2);
        } else {
            base = (defS == null || defS.types() == null) ? java.util.List.of() : defS.types();
        }
        if (base.isEmpty()) return base;
        java.util.ArrayList<String> out = new java.util.ArrayList<>();
        for (String t : base) {
            if (t == null || t.isBlank()) continue;
            if (def != null && def.roostSuppressFlying && "flying".equalsIgnoreCase(t)) continue;
            out.add(t.toLowerCase(java.util.Locale.ROOT));
        }
        return out.isEmpty() ? java.util.List.of("normal") : out;
    }

    private static int positiveStageSum(PokemonInstance mon) {
        if (mon == null) return 0;
        int sum = 0;
        sum += Math.max(0, mon.stageAtk);
        sum += Math.max(0, mon.stageDef);
        sum += Math.max(0, mon.stageSpa);
        sum += Math.max(0, mon.stageSpd);
        sum += Math.max(0, mon.stageSpe);
        sum += Math.max(0, mon.stageAccuracy);
        sum += Math.max(0, mon.stageEvasion);
        return sum;
    }

    private static double safeWeightKg(Species sp) {
        return sp == null ? 0.0 : Math.max(0.0, sp.weightKg());
    }

    private static int lowKickGrassKnotPower(double targetWeightKg) {
        if (targetWeightKg >= 200.0) return 120;
        if (targetWeightKg >= 100.0) return 100;
        if (targetWeightKg >= 50.0) return 80;
        if (targetWeightKg >= 25.0) return 60;
        if (targetWeightKg >= 10.0) return 40;
        return 20;
    }

    private static int heavySlamHeatCrashPower(double attackerWeightKg, double defenderWeightKg) {
        if (attackerWeightKg <= 0.0 || defenderWeightKg <= 0.0) return 40;
        double ratio = attackerWeightKg / defenderWeightKg;
        if (ratio >= 5.0) return 120;
        if (ratio >= 4.0) return 100;
        if (ratio >= 3.0) return 80;
        if (ratio >= 2.0) return 60;
        return 40;
    }

    private static boolean terrainGrounded(PokemonInstance mon, Species sp, BattleSession ctx) {
        if (mon == null || sp == null) return false;
        if (ctx != null && ctx.gravityTurns > 0) return true;
        if (mon.groundedBySmackDown || mon.roostSuppressFlying) return true;
        if (mon.magnetRiseTurns > 0 || mon.telekinesisTurns > 0) return false;
        java.util.List<String> ts = effectiveDefTypes(mon, sp);
        if (ts.stream().anyMatch(t -> "flying".equalsIgnoreCase(t))) return false;
        if (mon.abilityId != null && mon.abilityId.equalsIgnoreCase("levitate") && !mon.abilitySuppressed) return false;
        if (effectiveHeldItemId(mon) != null && effectiveHeldItemId(mon).equalsIgnoreCase("air_balloon")) return false;
        return true;
    }

    private static int effectivePower(Move move, PokemonInstance atk, Species atkS, PokemonInstance def, Species defS) {
        if (move == null) return 1;
        String id = move.id() == null ? "" : move.id().toLowerCase(java.util.Locale.ROOT).replace("_", "");
        if ("counter".equals(id) || "mirrorcoat".equals(id) || "metalburst".equals(id) || "comeuppance".equals(id)) {
            return 0;
        }
        int power = Math.max(1, move.power());

        switch (id) {
            case "storedpower", "powertrip" -> {
                power = 20 + 20 * positiveStageSum(atk);
            }
            case "grassknot", "lowkick" -> {
                power = lowKickGrassKnotPower(safeWeightKg(defS));
            }
            case "heavyslam", "heatcrash" -> {
                power = heavySlamHeatCrashPower(safeWeightKg(atkS), safeWeightKg(defS));
            }
            case "flail", "reversal" -> {
                int maxHp = (atk != null && atkS != null) ? Math.max(1, atk.maxHp(atkS)) : 1;
                int curHp = atk == null ? maxHp : Math.max(1, atk.currentHp);
                double ratio = (double) curHp / (double) maxHp;
                if (ratio <= (1.0 / 48.0)) power = 200;
                else if (ratio <= (1.0 / 5.0)) power = 150;
                else if (ratio <= (7.0 / 20.0)) power = 100;
                else if (ratio <= (35.0 / 100.0)) power = 80;
                else if (ratio <= (7.0 / 10.0)) power = 40;
                else power = 20;
            }
            case "payback" -> {
                if (atk != null && atk.actedLastThisTurn) power *= 2;
            }
            case "assurance" -> {
                if (def != null && def.tookDamageThisTurn) power *= 2;
            }
            case "revenge", "avalanche" -> {
                if (atk != null && atk.tookDamageThisTurn) power *= 2;
            }
            case "return" -> {
                int f = atk == null ? 70 : atk.friendshipValue();
                power = Math.max(1, Math.min(102, (int) Math.floor(f / 2.5)));
            }
            case "frustration" -> {
                int f = atk == null ? 70 : atk.friendshipValue();
                power = Math.max(1, Math.min(102, (int) Math.floor((255 - f) / 2.5)));
            }
            case "hex", "infernalparade" -> {
                String st = def == null || def.status == null ? "" : def.status.toLowerCase(java.util.Locale.ROOT);
                if (!st.isBlank() && !"none".equals(st)) power *= 2;
            }
            case "stompingtantrum" -> {
                if (atk != null && atk.lastMoveFailed) power *= 2;
            }
            case "dragonenergy" -> {
                int maxHp = (atk != null && atkS != null) ? Math.max(1, atk.maxHp(atkS)) : 1;
                int curHp = atk == null ? maxHp : Math.max(0, atk.currentHp);
                power = Math.max(1, (150 * curHp) / maxHp);
            }
            case "facade" -> {
                String st = atk == null || atk.status == null ? "" : atk.status.toLowerCase(java.util.Locale.ROOT);
                if (!st.isBlank() && !"none".equals(st)) power *= 2;
            }
            case "brine" -> {
                int maxHp = (def != null && defS != null) ? Math.max(1, def.maxHp(defS)) : 1;
                int curHp = def == null ? maxHp : Math.max(0, def.currentHp);
                if (curHp * 2 <= maxHp) power *= 2;
            }
            case "venoshock" -> {
                String st = def == null || def.status == null ? "" : def.status.toLowerCase(java.util.Locale.ROOT);
                if ("poison".equals(st) || "toxic".equals(st)) power *= 2;
            }
            case "wakeupslap" -> {
                String st = def == null || def.status == null ? "" : def.status.toLowerCase(java.util.Locale.ROOT);
                if ("sleep".equals(st)) power *= 2;
            }
            case "smellingsalts" -> {
                String st = def == null || def.status == null ? "" : def.status.toLowerCase(java.util.Locale.ROOT);
                if ("paralyze".equals(st)) power *= 2;
            }
            case "acrobatics" -> {
                if (atk == null || atk.heldItemId == null || atk.heldItemId.isBlank()) power *= 2;
            }
            case "boltbeak", "fishiousrend" -> {
                if (atk != null && !atk.actedLastThisTurn) power *= 2;
            }
            case "weatherball" -> {
                BattleSession ctx = AbilityEffects.contextSession();
                if (ctx != null && WeatherSystem.effectiveWeather(ctx) != WeatherType.NONE) power = 100;
            }
            case "ragefist" -> {
                int hits = atk == null ? 0 : Math.max(0, atk.rageFistHits);
                power = Math.min(350, 50 + 50 * hits);
            }
            case "electroball" -> {
                if (atk != null && def != null && atkS != null && defS != null) {
                    int atkSpe = Math.max(1, (int) Math.floor(atk.calcStat(atkS, "spe", atk.ivSpe, atk.evSpe, false) * atk.stageMultiplier("spe")));
                    int defSpe = Math.max(1, (int) Math.floor(def.calcStat(defS, "spe", def.ivSpe, def.evSpe, false) * def.stageMultiplier("spe")));
                    double ratio = (double) atkSpe / (double) defSpe;
                    power = ratio >= 4.0 ? 150 : ratio >= 3.0 ? 120 : ratio >= 2.0 ? 80 : ratio > 1.0 ? 60 : 40;
                }
            }
            case "gyroball" -> {
                if (atk != null && def != null && atkS != null && defS != null) {
                    int atkSpe = Math.max(1, (int) Math.floor(atk.calcStat(atkS, "spe", atk.ivSpe, atk.evSpe, false) * atk.stageMultiplier("spe")));
                    int defSpe = Math.max(1, (int) Math.floor(def.calcStat(defS, "spe", def.ivSpe, def.evSpe, false) * def.stageMultiplier("spe")));
                    power = Math.max(1, Math.min(150, (25 * defSpe) / atkSpe));
                }
            }
            case "eruption", "waterspout" -> {
                int maxHp = (atk != null && atkS != null) ? Math.max(1, atk.maxHp(atkS)) : 1;
                int curHp = atk == null ? maxHp : Math.max(0, atk.currentHp);
                power = Math.max(1, (150 * curHp) / maxHp);
            }
            case "punishment" -> {
                power = Math.min(200, 60 + 20 * positiveStageSum(def));
            }
            case "crushgrip", "wringout" -> {
                int maxHp = (def != null && defS != null) ? Math.max(1, def.maxHp(defS)) : 1;
                int curHp = def == null ? maxHp : Math.max(0, def.currentHp);
                power = Math.max(1, (120 * curHp) / maxHp);
            }
            case "risingvoltage" -> {
                BattleSession ctx = AbilityEffects.contextSession();
                if (ctx != null && "electric".equalsIgnoreCase(ctx.terrain) && terrainGrounded(def, defS, ctx)) power *= 2;
            }
            case "lastrespects" -> {
                BattleSession ctx = AbilityEffects.contextSession();
                int fainted = 0;
                if (ctx != null && atk != null) {
                    if (atk == ctx.playerMon) fainted = Math.max(0, ctx.playerPartyFaintedCount);
                    else if (atk == ctx.wildMon) fainted = Math.max(0, ctx.wildPartyFaintedCount);
                }
                power = Math.min(300, 50 + 50 * fainted);
            }
            case "beatup" -> {
                BattleSession ctx = AbilityEffects.contextSession();
                int alive = 1;
                if (ctx != null && atk != null) {
                    if (atk == ctx.playerMon) alive = Math.max(1, 6 - Math.max(0, ctx.playerPartyFaintedCount));
                    else if (atk == ctx.wildMon) alive = 1;
                }
                power = Math.max(10, Math.min(60, 10 * alive));
            }
        }
        return Math.max(1, power);
    }
}
