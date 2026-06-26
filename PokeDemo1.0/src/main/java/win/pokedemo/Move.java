package win.pokedemo;

import java.util.List;
import java.util.Map;

/**
 * A move definition.
 *
 * <p>"num" is the National Move Dex number used by Pokemon Showdown (Gen1 is 1..165).
 * For locally-defined/built-in moves it may be -1.
 */
public record Move(
        String id,
        String name,
        String type,
        String category, // physical / special / status
        int power,
        double accuracy,
        int pp,
        int priority,
        int num,
        /** Legacy single-effect map (backward compatible). */
        Map<String, Object> effect,
        /** New multi-effect list. Each entry is a generic map with at least an "id". */
        List<Map<String, Object>> effects
) {
    public List<Map<String, Object>> effectsSafe() {
        return effects == null ? List.of() : effects;
    }

    public int numSafe() {
        return num;
    }
}
