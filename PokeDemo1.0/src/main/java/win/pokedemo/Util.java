
package win.pokedemo;

import java.util.Random;

public final class Util {
    public static final Random RND = new Random();

    private Util() {}

    public static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    public static int parseInt(String s, int def) {
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            return def;
        }
    }

    public static double clamp01(double d) {
        if (d < 0) return 0;
        if (d > 1) return 1;
        return d;
    }

public static String titleCase(String id) {
    if (id == null || id.isBlank()) return "";
    String s = id.replace('_', ' ').replace('-', ' ');
    String[] parts = s.split("\\s+");
    StringBuilder sb = new StringBuilder();
    for (String p : parts) {
        if (p.isBlank()) continue;
        sb.append(Character.toUpperCase(p.charAt(0)));
        if (p.length() > 1) sb.append(p.substring(1).toLowerCase());
        sb.append(' ');
    }
    return sb.toString().trim();
}

    /**
     * Simple lore wrapper that works reasonably for both English and CJK.
     * Counts characters (not pixels) and splits on spaces when possible.
     */
    public static java.util.List<String> wrapLore(String text, int maxCharsPerLine) {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (text == null) return out;
        String t = text.trim();
        if (t.isEmpty()) return out;

        // Prefer splitting by spaces first (English), fallback to hard wrap (CJK).
        if (t.contains(" ")) {
            StringBuilder line = new StringBuilder();
            for (String w : t.split("\\s+")) {
                if (w.isEmpty()) continue;
                if (line.length() == 0) {
                    line.append(w);
                } else if (line.length() + 1 + w.length() <= maxCharsPerLine) {
                    line.append(' ').append(w);
                } else {
                    out.add(line.toString());
                    line.setLength(0);
                    line.append(w);
                }
            }
            if (line.length() > 0) out.add(line.toString());
            return out;
        }

        // Hard wrap for CJK.
        for (int i = 0; i < t.length(); i += maxCharsPerLine) {
            int end = Math.min(t.length(), i + maxCharsPerLine);
            out.add(t.substring(i, end));
        }
        return out;
    }
}
