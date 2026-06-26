package win.pokedemo;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public final class FishingContext {
    public final String rodType;
    public final Set<String> rodTypeAliases = new LinkedHashSet<>();
    public final String bait;
    public final Set<String> baitAliases = new LinkedHashSet<>();
    public final int lureLevel;

    public FishingContext(String rodType, Set<String> rodTypeAliases, String bait, Set<String> baitAliases, int lureLevel) {
        this.rodType = normalize(rodType);
        this.bait = normalize(bait);
        this.lureLevel = Math.max(0, lureLevel);
        if (rodTypeAliases != null) {
            for (String s : rodTypeAliases) {
                String n = normalize(s);
                if (n != null && !n.isBlank()) this.rodTypeAliases.add(n);
            }
        }
        if (baitAliases != null) {
            for (String s : baitAliases) {
                String n = normalize(s);
                if (n != null && !n.isBlank()) this.baitAliases.add(n);
            }
        }
        if (this.rodType != null && !this.rodType.isBlank()) {
            this.rodTypeAliases.add(this.rodType);
            this.rodTypeAliases.add(stripNamespace(this.rodType));
        }
        if (this.bait != null && !this.bait.isBlank()) {
            this.baitAliases.add(this.bait);
            this.baitAliases.add(stripNamespace(this.bait));
        }
        this.rodTypeAliases.add("any");
    }

    public boolean matchesRodType(String raw) {
        String n = normalize(raw);
        if (n == null || n.isBlank()) return true;
        return rodTypeAliases.contains(n) || rodTypeAliases.contains(stripNamespace(n));
    }

    public boolean matchesBait(String raw) {
        String n = normalize(raw);
        if (n == null || n.isBlank()) return true;
        if (baitAliases.isEmpty()) return false;
        return baitAliases.contains(n) || baitAliases.contains(stripNamespace(n));
    }

    private static String stripNamespace(String s) {
        String n = normalize(s);
        if (n == null) return null;
        int idx = n.indexOf(':');
        return idx >= 0 ? n.substring(idx + 1) : n;
    }

    private static String normalize(String s) {
        if (s == null) return null;
        return s.trim().toLowerCase(Locale.ROOT);
    }
}
