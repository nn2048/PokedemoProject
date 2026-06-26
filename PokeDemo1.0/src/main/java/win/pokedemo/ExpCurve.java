package win.pokedemo;

/**
 * Pokemon-style growth rate curves.
 *
 * We store total experience (总经验) and derive level from it.
 *
 * Groups: ERRATIC, FAST, MEDIUM_FAST, MEDIUM_SLOW, SLOW, FLUCTUATING
 *
 * Formulas follow the standard main-series curves (Gen III+).
 *
 * Notes:
 * - Total EXP at level 1 is always 0.
 * - Some curves (e.g. MEDIUM_SLOW) can be negative at very low levels; clamp to 0.
 */
public final class ExpCurve {
    private ExpCurve() {}

    public static long totalExpAtLevel(String group, int level) {
        if (level <= 1) return 0L;
        int L = Util.clamp(level, 1, 100);
        String g = (group == null ? "MEDIUM_FAST" : group.toUpperCase());

        long exp = switch (g) {
            // NOTE: total exp curves are cubic (L^3).
            // A previous bug used L^2 here, which made leveling absurdly fast.
            case "FAST" -> (4L * L * L * L) / 5L; // 4/5 * L^3
            case "SLOW" -> (5L * L * L * L) / 4L; // 5/4 * L^3
            case "MEDIUM_SLOW" -> (6L * L * L) / 5L - 15L * L + 100L * L - 140L;
            case "ERRATIC" -> erratic(L);
            case "FLUCTUATING" -> fluctuating(L);
            case "MEDIUM_FAST" -> 1L * L * L * L; // L^3
            default -> 1L * L * L * L;
        };

        // Guard: some curves can compute negative at low levels; exp cannot be negative.
        return Math.max(0L, exp);
    }

    public static int levelForTotalExp(String group, long totalExp) {
        if (totalExp <= 0L) return 1;
        int lo = 1, hi = 100;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            long need = totalExpAtLevel(group, mid);
            if (totalExp >= need) lo = mid;
            else hi = mid - 1;
        }
        return Util.clamp(lo, 1, 100);
    }

    private static long erratic(int L) {
        // Gen III+ Erratic growth
        if (L <= 50) {
            return (long) Math.floor((Math.pow(L, 3) * (100 - L)) / 50.0);
        } else if (L <= 68) {
            return (long) Math.floor((Math.pow(L, 3) * (150 - L)) / 100.0);
        } else if (L <= 98) {
            return (long) Math.floor((Math.pow(L, 3) * ((1911 - 10.0 * L) / 3.0)) / 500.0);
        } else {
            return (long) Math.floor((Math.pow(L, 3) * (160 - L)) / 100.0);
        }
    }

    private static long fluctuating(int L) {
        // Gen III+ Fluctuating growth
        if (L <= 15) {
            return (long) Math.floor(Math.pow(L, 3) * ((L + 1) / 3.0 + 24) / 50.0);
        } else if (L <= 36) {
            return (long) Math.floor(Math.pow(L, 3) * (L + 14) / 50.0);
        } else {
            return (long) Math.floor(Math.pow(L, 3) * (L / 2.0 + 32) / 50.0);
        }
    }
}
