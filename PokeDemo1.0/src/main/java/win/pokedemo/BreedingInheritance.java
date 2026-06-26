package win.pokedemo;

import java.util.*;

/**
 * Simplified breeding inheritance rules inspired by Showdown/vanilla games.
 * - Everstone: nature is inherited from holder (if both, random).
 * - Destiny Knot: inherit 5 IVs (otherwise 3).
 * - Power items: lock one specific IV to be inherited.
 */
public final class BreedingInheritance {
    private BreedingInheritance() {}

    // Power items ("项圈") -> stat key
    private static final Map<String, String> POWER_LOCK = Map.of(
            "power_weight", "hp",
            "power_bracer", "atk",
            "power_belt", "def",
            "power_lens", "spa",
            "power_band", "spd",
            "power_anklet", "spe"
    );

    public static void applyInheritance(PokemonInstance child, PokemonInstance a, PokemonInstance b, Dex dex) {
        if (child == null || a == null || b == null) return;

        // Nature: Everstone
        String nat = inheritedNature(a, b);
        if (nat != null) child.nature = nat;

        // IVs
        boolean knot = hasItem(a, "destiny_knot") || hasItem(b, "destiny_knot");
        int inheritCount = knot ? 5 : 3;

        // Power items ("项圈"):
        // - If both parents lock the SAME stat, randomly choose one parent as the source for that stat.
        // - If they lock DIFFERENT stats, BOTH locks apply (each stat inherited from its holder).
        Set<String> chosen = new HashSet<>();
        String sa = powerLockStatOf(a);
        String sb = powerLockStatOf(b);
        if (sa != null && sb != null && sa.equals(sb)) {
            chosen.add(sa);
            PokemonInstance src = Util.RND.nextBoolean() ? a : b;
            copyIvStat(child, src, sa);
        } else {
            if (sa != null) {
                chosen.add(sa);
                copyIvStat(child, a, sa);
            }
            if (sb != null) {
                chosen.add(sb);
                copyIvStat(child, b, sb);
            }
        }

        List<String> all = new ArrayList<>(List.of("hp", "atk", "def", "spa", "spd", "spe"));
        Collections.shuffle(all, Util.RND);
        for (String st : all) {
            if (chosen.size() >= inheritCount) break;
            if (chosen.contains(st)) continue;
            chosen.add(st);
            PokemonInstance src = (Util.RND.nextBoolean() ? a : b);
            copyIvStat(child, src, st);
        }

        // Any remaining IVs already randomized by createOwned().
    }

    private static boolean hasItem(PokemonInstance p, String id) {
        return p != null && p.heldItemId != null && p.heldItemId.equalsIgnoreCase(id);
    }

    private static String inheritedNature(PokemonInstance a, PokemonInstance b) {
        boolean ea = hasItem(a, "everstone");
        boolean eb = hasItem(b, "everstone");
        if (!ea && !eb) return null;
        if (ea && eb) {
            return Util.RND.nextBoolean() ? a.nature : b.nature;
        }
        return ea ? a.nature : b.nature;
    }

    private static String powerLockStatOf(PokemonInstance p) {
        if (p == null || p.heldItemId == null) return null;
        return POWER_LOCK.get(p.heldItemId.toLowerCase());
    }

    private static void copyIvStat(PokemonInstance dst, PokemonInstance src, String st) {
        if (dst == null || src == null || st == null) return;
        switch (st) {
            case "hp" -> dst.ivHp = src.ivHp;
            case "atk" -> dst.ivAtk = src.ivAtk;
            case "def" -> dst.ivDef = src.ivDef;
            case "spa" -> dst.ivSpa = src.ivSpa;
            case "spd" -> dst.ivSpd = src.ivSpd;
            case "spe" -> dst.ivSpe = src.ivSpe;
        }
    }
}
