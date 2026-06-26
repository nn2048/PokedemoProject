
package win.pokedemo;

import java.util.*;

public class PokemonInstance {

    /**
     * Some old saves / corrupted data may reference a speciesId that is not present in the loaded Dex.
     * We must never crash the server thread because of that.
     */
    private static final java.util.Set<String> WARNED_UNKNOWN_SPECIES =
            java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
    public UUID uuid;
    public String speciesId;
    // Localized species display name copied at creation time (so GUI can be Chinese even if id is english)
    public String speciesName;
    /** Gender: "M", "F" or "N" (genderless/unknown). Persisted. */
    public String gender = "N";

    /** If true, this entry is an egg and should hide most info until hatched. Persisted. */
    public boolean isEgg = false;
    /** Egg hatch steps remaining (only decreases while in PARTY). Persisted. */
    public int eggStepsRemaining = 0;
    /** Egg hatch steps total (for progress display). Persisted. */
    public int eggStepsTotal = 0;
    /** Ball id chosen when claiming the egg (used for the hatched Pokémon). Persisted. */
    public String eggBallId = null;
    /** Ball id this Pokémon is currently stored in (sendout/recall visuals). Persisted. */
    public String ballId = "poke_ball";

    /** If true, this Pokémon is locked in PC/party GUIs (e.g., selected in a Pasture). Persisted. */
    public boolean uiLocked = false;
    /** Optional lock reason for UI. Persisted. */
    public String uiLockReason = "";
    // Optional nickname
    public String nickname;
    public int level;

    // Total experience (总经验). Level is derived from this, but we also store level for UI convenience.
    public long totalExp;

    // Friendship / happiness (0..255). Used by moves like Return / Frustration and future evolutions.
    // Older saves may not contain this field; treat 0 as the default caught value in helper methods.
    public int friendship = 70;

    // --- Stage 6 evolution progress counters (persisted) ---
    /** Primeape -> Annihilape: use Rage Fist 20 times, then level up. */
    public int rageFistUses = 0;
    /** Stantler -> Wyrdeer: plugin simplification, use Psyshield Bash 20 times, then level up. */
    public int psyshieldBashUses = 0;
    /** Hisuian Qwilfish -> Overqwil: plugin simplification, use Barb Barrage 20 times, then level up. */
    public int barbBarrageUses = 0;
    /** White-Striped Basculin -> Basculegion: cumulative recoil HP lost without fainting. */
    public int basculinRecoilHp = 0;
    /** Bisharp -> Kingambit: defeat 3 Bisharp that hold King's Rock, then level up. */
    public int bisharpLeaderDefeats = 0;
    /** Pawmo/Bramblin/Rellor: plugin simplification of Let's Go walking progress. */
    public int evolutionStepCounter = 0;
    /** Gimmighoul -> Gholdengo: collected coin progress. */
    public int gimmighoulCoins = 0;

    // Nature (性格)
    public String nature; // e.g. "HARDY". Stored as enum name for stability.

    public int ivHp, ivAtk, ivDef, ivSpa, ivSpd, ivSpe;
    public int evHp, evAtk, evDef, evSpa, evSpd, evSpe;

    public int currentHp;
    public String status; // none, poison, burn, paralyze, sleep

    // Held item id (e.g. "oran_berry"). Stored as PokeDemo item id; null = none.
    public String heldItemId;

    // Battle-only: last consumed berry id (for Harvest). Null = none.
    public transient String lastConsumedBerryId = null;

    // Ability id (Showdown-style, e.g. "intimidate").
    public String abilityId;

    // Legendary flag (persisted). Used for special announcements / IV rules.
    public boolean isLegendary = false;
    /** Optional legendary group/category (persisted). */
    public String legendaryGroup = null;

    // --- Clone module (Mew) ---
    /** How many times this Mew has attempted cloning (persisted). Max 3. */
    public int mewCloneAttempts = 0;
    /** If true, this Mew can never be cloned again (persisted). Set when cloning succeeds (Mewtwo) or attempts exhausted. */
    public boolean mewCloneDisabled = false;

    // Battle-only ability override support (e.g. Trace).
    public transient String traceOriginalAbilityId = null;
    public transient String tracedAbilityId = null;

    // Battle-only: last used move (for Mirror Move / Mimic / Disable etc.)
    public transient String lastMoveId = null;

    // Battle-only: Metronome held item consecutive move tracking.
    public transient String metronomeLastMoveId = null;
    public transient int metronomeCount = 0;

    // Battle-only: infatuation (Attract-like). When true, 50% chance to be immobilized.
    public transient boolean infatuated = false;

    // Battle-only: temporary type override (for Conversion). Null means use species default types.
    public transient String overrideType1 = null;
    public transient String overrideType2 = null;

    // Battle-only: temporary species override (for Transform). Null means use original speciesId.
    public transient String overrideSpeciesId = null;

    // Battle-only: temporary moveset override (for Mimic/Transform). When non-null, battle logic should use this list.
    public transient java.util.List<MoveSlot> overrideMoves = null;

    /**
     * Gen1 Transform does NOT change the user's max HP.
     * Because some parts of the code recompute max HP from the battle "effective species",
     * we lock the original max HP for the duration of Transform.
     */
    public transient Integer lockedMaxHp = null;

    // Gen1 Focus Energy bug flag (battle-only). When true, crit chance is quartered.
    public transient boolean focusEnergyActive = false;
    public transient boolean direHitActive = false; // battle-only crit boost from Dire Hit

    /** Effective species id in battle (uses overrideSpeciesId when set). */
    public String effectiveSpeciesId() {
        return (overrideSpeciesId != null && !overrideSpeciesId.isEmpty()) ? overrideSpeciesId : speciesId;
    }

    /** Effective moveset in battle (uses overrideMoves when set). */
    public java.util.List<MoveSlot> effectiveMoves() {
        return (overrideMoves != null) ? overrideMoves : moves;
    }

    public int friendshipValue() {
        int v = friendship;
        if (v <= 0) v = 70;
        if (v > 255) v = 255;
        return v;
    }

    public void setFriendship(int value) {
        friendship = Math.max(0, Math.min(255, value));
    }

    public void addFriendship(int delta) {
        if (delta == 0) return;
        int d = delta;
        if (d > 0 && "soothe_bell".equalsIgnoreCase(heldItemId)) {
            d = Math.max(1, (int) Math.round(d * 1.5));
        }
        setFriendship(friendshipValue() + d);
    }

    /** Ensure overrideMoves exists as a deep-copy of the persisted moves list, so battle-only modifications won't touch the save. */
    public void ensureBattleMovesFromBase() {
        if (overrideMoves != null) return;
        overrideMoves = new java.util.ArrayList<>();
        if (moves == null) return;
        for (MoveSlot ms : moves) {
            if (ms == null) { overrideMoves.add(null); continue; }
            MoveSlot c = new MoveSlot();
            c.moveId = ms.moveId;
            c.pp = ms.pp;
            c.basePp = ms.basePp;
            c.ppUpsUsed = ms.ppUpsUsed;
            c.maxPp = ms.maxPp;
            overrideMoves.add(c);
        }
    }


    /**
     * Deep-copy a MoveSlot list (including null slots). Useful for battle-only move overrides.
     */
    public static java.util.List<MoveSlot> deepCopyMoveSlots(java.util.List<MoveSlot> src) {
        java.util.ArrayList<MoveSlot> out = new java.util.ArrayList<>();
        if (src == null) return out;
        for (MoveSlot ms : src) {
            if (ms == null) { out.add(null); continue; }
            MoveSlot c = new MoveSlot();
            c.moveId = ms.moveId;
            c.pp = ms.pp;
            c.basePp = ms.basePp;
            c.ppUpsUsed = ms.ppUpsUsed;
            c.maxPp = ms.maxPp;
            out.add(c);
        }
        return out;
    }


// Battle-only volatile counters (not persisted)
public transient int sleepTurns = 0;      // remaining turns asleep
public transient int toxicCounter = 0;    // toxic stage (1,2,3...)
public transient boolean leechSeeded = false;
public transient boolean leechSeedByPlayer = false; // who applied seed

    // More Gen1 volatiles (battle-only)
    public transient int rechargeTurns = 0;          // e.g. Hyper Beam recharge
    public transient String disabledMoveId = null;   // Disable
    public transient int disabledTurns = 0;

    // Substitute (HP of substitute; 0 means none)
    public transient int substituteHp = 0;

    // Partial-trapping (Bind/Wrap/Clamp/Fire Spin)
    public transient String trappingMoveId = null;   // the move being repeated
    public transient int trappingTurnsRemaining = 0;
    public transient int trappedTurnsRemaining = 0;  // victim cannot act while >0
    public transient boolean trappingContinuing = false; // internal flag for accuracy skip

    // Bide
    public transient boolean bideActive = false;
    public transient int bideTurnsRemaining = 0;
    public transient int bideDamageTaken = 0;

    // Counter / Mirror Coat / Metal Burst helpers
    public transient int lastPhysicalDamageTaken = 0;
    public transient java.util.UUID lastPhysicalDamageSourceUuid = null;
    public transient int lastPhysicalDamageTurn = -1;

    // Mist
    public transient int mistTurnsRemaining = 0;

    // Choice item lock
    /** Choice items (band/specs/scarf) lock the user into the first selected move until it switches out. */
    public transient String choiceLockedMoveId = null;

    // Ability transient flags
    public transient boolean unburdenActive = false;
    public transient boolean actedLastThisTurn = false;
    public transient boolean flashFireBoost = false;

    // Volatile statuses
    public transient int confusionTurns = 0;      // remaining turns confused
    public transient boolean flinched = false;    // flinched this turn
    public transient int protectTurnsRemaining = 0; // protect-like effects for the current turn
    public transient int protectSuccessStreak = 0; // shared consecutive success counter for protect-like moves
    public transient int healBlockTurns = 0;
    public transient int nightmareTurns = 0;
    public transient boolean aquaRingActive = false;
    public transient boolean ingrainActive = false;
    public transient int tauntTurns = 0;
    public transient int tormentTurns = 0;
    public transient int encoreTurns = 0;
    public transient String encoreMoveId = null;
    public transient int perishSongTurns = 0;
    public transient boolean abilitySuppressed = false;
    public transient boolean groundedBySmackDown = false;
    public transient boolean identifiedTarget = false;
    public transient boolean miracleEyeTarget = false;
    public transient boolean roostSuppressFlying = false;
    public transient int magnetRiseTurns = 0;
    public transient int telekinesisTurns = 0;
    public transient boolean tookDamageThisTurn = false;
    public transient int saltCureTurns = 0;
    public transient boolean lastMoveFailed = false;
    public transient boolean chargeActive = false;
    public transient boolean typeChangeAbilityUsed = false;
    public transient int stockpileCount = 0;
    public transient boolean imprisonActive = false;
    public transient boolean justSwitchedIn = false;
    public transient boolean magicCoatActive = false;
    public transient boolean grudgeActive = false;
    public transient int embargoTurns = 0;
    public transient int laserFocusTurns = 0;
    public transient boolean noRetreatActive = false;
    public transient boolean tarShotActive = false;
    public transient int throatChopTurns = 0;
    public transient boolean powdered = false;
    public transient boolean electrifiedThisTurn = false;
    public transient boolean powerTrickActive = false;
    public transient int lastSpecialDamageTaken = 0;
    public transient java.util.UUID lastSpecialDamageSourceUuid = null;
    public transient int lastSpecialDamageTurn = -1;
    public transient int lastDamageTaken = 0;
    public transient java.util.UUID lastDamageSourceUuid = null;
    public transient int lastDamageTurn = -1;
    public transient int rageFistHits = 0;
    public transient boolean destinyBondActive = false;
    public transient boolean battleFaintedCounted = false;
    public transient int slowStartTurns = 0;
    public transient boolean truantLoafing = false;
    public transient boolean angerShellUsed = false;
    public transient boolean disguiseBroken = false;
    public transient boolean iceFaceBroken = false;
    public transient boolean battleBondUsed = false;
    public transient boolean powerConstructUsed = false;
    public transient String cudChewBerryId = null;
    public transient int cudChewTurns = 0;
    public transient boolean paradoxBoostedByItem = false;
    public transient boolean zeroToHeroPrimed = false;
    public transient boolean emergencyExitTriggered = false;
    public transient boolean illusionActive = false;

    // Two-turn moves (Fly/Dig/Solar Beam...)
    public transient String chargingMoveId = null;
    public transient int chargingTurnsRemaining = 0; // usually 1

    // Stat stages (战斗中的能力变化，范围 -6..+6)
    public transient int stageAtk = 0;
    public transient int stageDef = 0;
    public transient int stageSpa = 0;
    public transient int stageSpd = 0;
    public transient int stageSpe = 0;
    public transient int stageAccuracy = 0;
    public transient int stageEvasion = 0;

    public void resetBattleStages() {
        stageAtk = stageDef = stageSpa = stageSpd = stageSpe = stageAccuracy = stageEvasion = 0;
    }

    public void resetBattleVolatiles() {
        // Restore battle-only ability overrides (e.g. Trace).
        if (traceOriginalAbilityId != null) {
            abilityId = traceOriginalAbilityId;
        }
        traceOriginalAbilityId = null;
        tracedAbilityId = null;

        // Clear battle-only overlays
        overrideSpeciesId = null;
        overrideMoves = null;
        lockedMaxHp = null;
        focusEnergyActive = false;
        direHitActive = false;

        rechargeTurns = 0;
        disabledMoveId = null;
        disabledTurns = 0;

        substituteHp = 0;

        trappingMoveId = null;
        trappingTurnsRemaining = 0;
        trappedTurnsRemaining = 0;
        trappingContinuing = false;

        bideActive = false;
        bideTurnsRemaining = 0;
        bideDamageTaken = 0;

        lastPhysicalDamageTaken = 0;
        lastPhysicalDamageSourceUuid = null;
        lastPhysicalDamageTurn = -1;
        lastSpecialDamageTaken = 0;
        lastSpecialDamageSourceUuid = null;
        lastSpecialDamageTurn = -1;
        lastDamageTaken = 0;
        lastDamageSourceUuid = null;
        lastDamageTurn = -1;
        rageFistHits = 0;

        mistTurnsRemaining = 0;

        choiceLockedMoveId = null;
        abilitySuppressed = false;
        groundedBySmackDown = false;
        identifiedTarget = false;
        miracleEyeTarget = false;
        roostSuppressFlying = false;
        magnetRiseTurns = 0;
        telekinesisTurns = 0;
        tookDamageThisTurn = false;
        saltCureTurns = 0;
        lastMoveFailed = false;
        chargeActive = false;
        typeChangeAbilityUsed = false;
        stockpileCount = 0;
        imprisonActive = false;
        justSwitchedIn = false;
        magicCoatActive = false;
        grudgeActive = false;
        embargoTurns = 0;
        laserFocusTurns = 0;
        noRetreatActive = false;
        tarShotActive = false;
        throatChopTurns = 0;
        powdered = false;
        electrifiedThisTurn = false;
        powerTrickActive = false;

        sleepTurns = 0;
        toxicCounter = 0;
        leechSeeded = false;
        leechSeedByPlayer = false;
        confusionTurns = 0;
        flinched = false;
        protectTurnsRemaining = 0;
        protectSuccessStreak = 0;
        healBlockTurns = 0;
        nightmareTurns = 0;
        aquaRingActive = false;
        ingrainActive = false;
        tauntTurns = 0;
        tormentTurns = 0;
        encoreTurns = 0;
        encoreMoveId = null;
        perishSongTurns = 0;
        chargingMoveId = null;
        chargingTurnsRemaining = 0;
        lastMoveId = null;
        overrideType1 = null;
        overrideType2 = null;
    }


public void setBattleAbility(String newAbilityId) {
    if (traceOriginalAbilityId == null) traceOriginalAbilityId = abilityId;
    abilityId = newAbilityId;
}
    public void applyStage(String stat, int delta) {
        if (stat == null) return;
        // Ability-based prevention of stat drops (simplified).
        // Note: This does not distinguish source (self vs opponent). We only block negative deltas.
        if (delta < 0) {
            String a = abilityId == null ? "" : abilityId.toLowerCase(java.util.Locale.ROOT);
            String st = stat.toLowerCase(java.util.Locale.ROOT);
            // Clear Body / White Smoke / Full Metal Body: prevent all stat reductions.
            if ("clearbody".equals(a) || "whitesmoke".equals(a) || "fullmetalbody".equals(a)) {
                return;
            }
            // Hyper Cutter: prevents Attack reduction.
            if ("hypercutter".equals(a) && "atk".equals(st)) {
                return;
            }
            // Keen Eye: prevents Accuracy reduction.
            if ("keeneye".equals(a) && "accuracy".equals(st)) {
                return;
            }
            // Big Pecks: prevents Defense reduction.
            if ("bigpecks".equals(a) && "def".equals(st)) {
                return;
            }
        }
        switch (stat.toLowerCase()) {
            case "atk" -> stageAtk = Util.clamp(stageAtk + delta, -6, 6);
            case "def" -> stageDef = Util.clamp(stageDef + delta, -6, 6);
            case "spa" -> stageSpa = Util.clamp(stageSpa + delta, -6, 6);
            case "spd" -> stageSpd = Util.clamp(stageSpd + delta, -6, 6);
            case "spe" -> stageSpe = Util.clamp(stageSpe + delta, -6, 6);
            case "accuracy" -> stageAccuracy = Util.clamp(stageAccuracy + delta, -6, 6);
            case "evasion" -> stageEvasion = Util.clamp(stageEvasion + delta, -6, 6);
        }
    }

    public double stageMultiplier(String stat) {
        int st = 0;
        if (stat == null) return 1.0;
        switch (stat.toLowerCase()) {
            case "atk" -> st = stageAtk;
            case "def" -> st = stageDef;
            case "spa" -> st = stageSpa;
            case "spd" -> st = stageSpd;
            case "spe" -> st = stageSpe;
            case "accuracy" -> st = stageAccuracy;
            case "evasion" -> st = stageEvasion;
            default -> { return 1.0; }
        }
        if (st >= 0) return (2.0 + st) / 2.0;
        return 2.0 / (2.0 - st);
    }


    public List<MoveSlot> moves = new ArrayList<>();

    // Pending moves to learn (when moveset is full). Stored as move IDs.
    public List<String> pendingMoveLearns = new ArrayList<>();

    public long createdAt;
    public UUID originalTrainer;
    public String originalTrainerName;

    /**
     * Create a wild Pokémon instance.
     * Wild Pokémon can roll hidden ability / 梦特 according to config.
     */
    public static PokemonInstance createWild(Species species, int level, Dex dex) {
        return createWithAbilityRoll(species, level, dex, true);
    }

    /** Apply a minimum number of perfect IVs (31) across the 6 stats. */
    public void applyMinPerfectIvs(int minPerfect) {
        int need = Util.clamp(minPerfect, 0, 6);
        if (need <= 0) return;

        // Count existing perfect IVs first.
        int have = 0;
        int[] ivs = new int[]{ivHp, ivAtk, ivDef, ivSpa, ivSpd, ivSpe};
        for (int v : ivs) if (v >= 31) have++;
        if (have >= need) return;

        // Randomly choose distinct stats to set to 31.
        java.util.List<Integer> idx = new java.util.ArrayList<>();
        for (int i = 0; i < 6; i++) idx.add(i);
        java.util.Collections.shuffle(idx, Util.RND);
        int toSet = need - have;
        for (int i : idx) {
            if (toSet <= 0) break;
            switch (i) {
                case 0 -> { if (ivHp < 31) { ivHp = 31; toSet--; } }
                case 1 -> { if (ivAtk < 31) { ivAtk = 31; toSet--; } }
                case 2 -> { if (ivDef < 31) { ivDef = 31; toSet--; } }
                case 3 -> { if (ivSpa < 31) { ivSpa = 31; toSet--; } }
                case 4 -> { if (ivSpd < 31) { ivSpd = 31; toSet--; } }
                case 5 -> { if (ivSpe < 31) { ivSpe = 31; toSet--; } }
            }
        }
    }

    /**
     * Create an owned/granted Pokémon instance (starter/admin give, etc.).
     * By default we do NOT roll hidden ability, to match player expectation.
     */
    public static PokemonInstance createOwned(Species species, int level, Dex dex) {
        return createWithAbilityRoll(species, level, dex, false);
    }

    /**
     * Create an owned/granted Pokémon instance, but ALLOW rolling hidden ability (梦特)
     * using the same config/chance as wilds.
     */
    public static PokemonInstance createOwnedAllowHidden(Species species, int level, Dex dex) {
        return createWithAbilityRoll(species, level, dex, true);
    }

    private static PokemonInstance createWithAbilityRoll(Species species, int level, Dex dex, boolean allowHiddenRoll) {
        PokemonInstance p = new PokemonInstance();
        p.uuid = UUID.randomUUID();
        p.speciesId = species.id();
        p.speciesName = species.name();
        p.level = Util.clamp(level, 1, 100);
        // Initialize total exp to the minimum exp for this level.
        p.totalExp = ExpCurve.totalExpAtLevel(species.expGroup(), p.level);
        p.setFriendship(Math.max(0, species.baseFriendship()));

        // random nature
        p.nature = Nature.random(Util.RND).name();

        // Gender: use species maleRatio when available.
        // maleRatio < 0 => genderless/unknown (N)
        double mr = -1.0;
        try { mr = species.maleRatio(); } catch (Throwable ignored) {}
        if (mr < 0) {
            p.gender = "N";
        } else {
            mr = Math.max(0.0, Math.min(1.0, mr));
            p.gender = (Util.RND.nextDouble() < mr ? "M" : "F");
        }
        p.ivHp = Util.RND.nextInt(32);
        p.ivAtk = Util.RND.nextInt(32);
        p.ivDef = Util.RND.nextInt(32);
        p.ivSpa = Util.RND.nextInt(32);
        p.ivSpd = Util.RND.nextInt(32);
        p.ivSpe = Util.RND.nextInt(32);
        p.evHp = p.evAtk = p.evDef = p.evSpa = p.evSpd = p.evSpe = 0;
        p.status = "none";
        // assign ability
        try {
            boolean useHidden = allowHiddenRoll && (dex != null) && dex.rollHiddenAbilityForWild();
            p.abilityId = (dex == null) ? null : dex.pickAbilityIdForSpecies(species.id(), useHidden);
        } catch (Throwable ignored) {}
        p.createdAt = System.currentTimeMillis();
        p.ballId = "poke_ball";

        // starting moves from learnset (技能树初始招式)
        // IMPORTANT: only include moves that exist in current Dex and comply with the current generation mode.
        java.util.List<String> learned = new java.util.ArrayList<>();
        for (var en : species.levelUpMovesSafe().entrySet()) {
            int lvlKey = en.getKey();
            if (lvlKey <= p.level) {
                for (String mv : en.getValue()) {
                    if (mv == null) continue;
                    String id = mv.toLowerCase();
                    if (!dex.isMoveAllowed(id)) continue;
                    if (!learned.contains(id)) learned.add(id);
                }
            }
        }
        // keep last 4 learned moves
        int from = Math.max(0, learned.size() - 4);
        java.util.List<String> pick = learned.subList(from, learned.size());
        for (String mid : pick) {
            p.moves.add(MoveSlot.of(dex.getMoveOrPlaceholder(mid)));
        }
        // fallback if learnset is empty
        if (p.moves.isEmpty()) {
            p.moves.add(MoveSlot.of(dex.getMoveOrPlaceholder("tackle")));
            p.moves.add(MoveSlot.of(dex.getMoveOrPlaceholder("growl")));
        }
        while (p.moves.size() < 4) p.moves.add(MoveSlot.of(dex.getMoveOrPlaceholder("tackle")));

        int maxHp = p.calcStat(species, "hp", p.ivHp, p.evHp, true);
        p.currentHp = maxHp;
        if (isShedinjaSpecies(species)) p.currentHp = Math.min(p.currentHp, 1);
        return p;
    }

    public boolean knowsMove(String moveId) {
        if (moveId == null) return false;
        String key = moveId.toLowerCase();
        for (MoveSlot s : moves) {
            if (s != null && s.moveId != null && s.moveId.equalsIgnoreCase(key)) return true;
        }
        return false;
    }

    public String displayName() {
        if (isEgg) return "蛋";
        if (nickname != null && !nickname.isBlank()) return nickname;
        // Prefer translation file (Chinese/English). speciesName is kept for backward compatibility with old saves.
        LangManager lang = PokeDemoPlugin.INSTANCE != null ? PokeDemoPlugin.INSTANCE.getLang() : null;
        if (lang != null) return lang.species(speciesId, speciesName);
        if (speciesName != null && !speciesName.isBlank()) return speciesName;
        return speciesId;
    }

    public int maxHp(Species s) {
        if (lockedMaxHp != null) return lockedMaxHp;
        Species resolved = resolveSpeciesForStats(s);
        if (resolved == null) {
            warnUnknownSpeciesOnce();
            // Safe fallback: use a reasonable base stat so battles don't NPE.
            int base = 50;
            int hp = (int) Math.floor(((2 * base + ivHp + (evHp / 4.0)) * level) / 100.0) + level + 10;
            return hp;
        }
        int hp = calcStat(resolved, "hp", ivHp, evHp, true);
        if (isShedinjaSpecies(resolved)) {
            if (currentHp > 1) currentHp = 1;
            if (currentHp < 0) currentHp = 0;
            return 1;
        }
        return hp;
    }

    public int calcStat(Species s, String stat, int iv, int ev, boolean hp) {
        int base;
        String effectiveStat = stat;
        if (!hp && powerTrickActive) {
            if ("atk".equalsIgnoreCase(stat)) effectiveStat = "def";
            else if ("def".equalsIgnoreCase(stat)) effectiveStat = "atk";
        }
        Species resolved = resolveSpeciesForStats(s);
        if (resolved == null) {
            warnUnknownSpeciesOnce();
            base = 50; // safe fallback
        } else {
            base = resolved.stat(effectiveStat);
        }
        if (hp) {
            if (isShedinjaSpecies(resolved)) return 1;
            // HP is not affected by nature
            return (int)Math.floor(((2 * base + iv + (ev / 4.0)) * level) / 100.0) + level + 10;
        } else {
            double value = Math.floor(((2 * base + iv + (ev / 4.0)) * level) / 100.0) + 5;
            // Apply nature modifier (+10% / -10%)
            Nature n = Nature.fromId(this.nature);
            if (n.plus != null && n.plus.equalsIgnoreCase(effectiveStat)) value *= 1.1;
            if (n.minus != null && n.minus.equalsIgnoreCase(effectiveStat)) value *= 0.9;
            return (int)Math.floor(value);
        }
    }


    private static boolean isShedinjaSpecies(Species species) {
        if (species == null || species.id() == null) return false;
        return "shedinja".equalsIgnoreCase(species.id());
    }

    private Species resolveSpeciesForStats(Species provided) {
        if (provided != null) return provided;
        if (PokeDemoPlugin.INSTANCE == null || PokeDemoPlugin.INSTANCE.getDex() == null) return null;
        Dex dex = PokeDemoPlugin.INSTANCE.getDex();
        Species byEffective = dex.getSpeciesFlexible(effectiveSpeciesId());
        if (byEffective != null) return byEffective;
        return dex.getSpeciesFlexible(speciesId);
    }

    private void warnUnknownSpeciesOnce() {
        String id = (speciesId != null ? speciesId : "(null)");
        if (!WARNED_UNKNOWN_SPECIES.add(id)) return;
        if (PokeDemoPlugin.INSTANCE != null) {
            PokeDemoPlugin.INSTANCE.getLogger().warning(
                    "[PokeDemo] Unknown speciesId '" + id + "' referenced by a PokemonInstance. " +
                            "This usually means Dex data is missing or the save contains an invalid species. " +
                            "Falling back to default base stats to avoid crashing.");
        }
    }

    
    /** Create a deep copy containing only persisted fields (no battle-only transient overrides). */
    public PokemonInstance deepCopyPersisted() {
        PokemonInstance p = new PokemonInstance();
        p.uuid = java.util.UUID.randomUUID();
        p.speciesId = this.speciesId;
        p.speciesName = this.speciesName;
        p.nickname = this.nickname;
        p.level = this.level;
        p.totalExp = this.totalExp;
        p.friendship = this.friendship;
        p.nature = this.nature;

        p.ivHp = this.ivHp; p.ivAtk = this.ivAtk; p.ivDef = this.ivDef; p.ivSpa = this.ivSpa; p.ivSpd = this.ivSpd; p.ivSpe = this.ivSpe;
        p.evHp = this.evHp; p.evAtk = this.evAtk; p.evDef = this.evDef; p.evSpa = this.evSpa; p.evSpd = this.evSpd; p.evSpe = this.evSpe;

        p.currentHp = this.currentHp;
        p.status = this.status;
        p.heldItemId = this.heldItemId;
        p.gender = this.gender;
        p.abilityId = this.abilityId;
        p.isLegendary = this.isLegendary;

        p.moves = deepCopyMoveSlots(this.moves);

        p.createdAt = this.createdAt;
        p.originalTrainer = this.originalTrainer;
        p.originalTrainerName = this.originalTrainerName;
        p.isEgg = this.isEgg;
        p.eggStepsRemaining = this.eggStepsRemaining;
        p.eggStepsTotal = this.eggStepsTotal;
        p.eggBallId = this.eggBallId;
        p.ballId = (this.ballId == null || this.ballId.isBlank()) ? "poke_ball" : this.ballId;
        p.uiLocked = this.uiLocked;
        p.uiLockReason = this.uiLockReason;
        p.legendaryGroup = this.legendaryGroup;
        p.mewCloneAttempts = this.mewCloneAttempts;
        p.mewCloneDisabled = this.mewCloneDisabled;
        p.pendingMoveLearns = (this.pendingMoveLearns == null) ? new java.util.ArrayList<>() : new java.util.ArrayList<>(this.pendingMoveLearns);
        return p;
    }


}
