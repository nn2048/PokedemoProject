package win.pokedemo;

import java.util.List;

/**
 * Simple evolution definition (进化定义).
 *
 * Currently supported:
 * - variant = "level_up": evolve when pokemon level >= minLevel
 *
 * Future:
 * - item, friendship, time, biome, etc.
 */
public record Evolution(
        String variant,
        int minLevel,
        String result,
        List<String> learnableMoves
) {
    public boolean isLevelUp() {
        return variant != null && variant.equalsIgnoreCase("level_up");
    }
}
