package win.pokedemo;

import org.bukkit.World;

import java.util.Locale;

public final class MoonPhaseUtil {
    private static final String[] PHASES = {
            "full_moon", "waning_gibbous", "last_quarter", "waning_crescent",
            "new_moon", "waxing_crescent", "first_quarter", "waxing_gibbous"
    };

    private MoonPhaseUtil() {}

    public static String currentPhase(World world) {
        if (world == null) return "";
        long days = Math.floorDiv(world.getFullTime(), 24000L);
        int idx = (int) Math.floorMod(days, 8);
        return PHASES[idx];
    }

    public static boolean matches(String wanted, String current) {
        if (wanted == null || wanted.isBlank()) return true;
        String w = wanted.trim().toLowerCase(Locale.ROOT);
        String c = current == null ? "" : current.trim().toLowerCase(Locale.ROOT);
        if (w.equals(c)) return true;
        return switch (w) {
            case "full" -> c.equals("full_moon");
            case "new" -> c.equals("new_moon");
            case "waxing" -> c.startsWith("waxing_");
            case "waning" -> c.startsWith("waning_");
            case "crescent" -> c.endsWith("crescent");
            case "gibbous" -> c.endsWith("gibbous");
            case "quarter" -> c.endsWith("quarter");
            default -> false;
        };
    }
}
