package win.pokedemo;

import java.util.List;
import java.util.Map;

public record Species(
        String id,
        String name,
        List<String> types,
        Map<String, Integer> baseStats,
        /** EV yields (努力值掉落) per stat key: hp/atk/def/spa/spd/spe */
        Map<String, Integer> evYields,
        /** Evolutions (进化列表) */
        List<Evolution> evolutions,
        /** Learnset: level-up moves (技能树：升级学招) level -> move ids */
        Map<Integer, List<String>> levelUpMoves,
        int baseExpYield,
        String expGroup,
        int catchRate,
        int minLevel,
        int maxLevel,
        /** Weight in kilograms. 0 when unknown. */
        double weightKg,
        /** Egg groups (蛋组). Empty when unknown. */
        List<String> eggGroups,
        /** Male ratio (0.0~1.0). Use -1 for genderless/unknown. */
        double maleRatio,
        /** Base friendship / happiness when obtained. */
        int baseFriendship
) {
    public int stat(String key) {
        return baseStats.getOrDefault(key, 50);
    }

    public int ev(String key) {
        if (evYields == null) return 0;
        return evYields.getOrDefault(key, 0);
    }

    public List<Evolution> evolutionsSafe() {
        return evolutions == null ? List.of() : evolutions;
    }

    public Map<Integer, List<String>> levelUpMovesSafe() {
        return levelUpMoves == null ? Map.of() : levelUpMoves;
    }

    public List<String> movesLearnedAtLevel(int level) {
        return levelUpMovesSafe().getOrDefault(level, List.of());
    }
}
