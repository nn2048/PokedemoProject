package win.pokedemo;

import org.bukkit.Material;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Minimal in-memory representation of a Cobblemon-style ball recipe.
 * Used by {@link BallCraftingListener} to make crafting table output work reliably.
 */
public final class BallRecipeDef {
    /** 3 rows, each length 3, may contain spaces. */
    public final String[] pattern;
    /** symbol -> accepted ingredient matcher */
    public final java.util.Map<Character, Ingredient> key;
    /** internal item id in our registry (e.g. poke_ball) */
    public final String resultId;
    public final int resultCount;

    public BallRecipeDef(String[] pattern, java.util.Map<Character, Ingredient> key, String resultId, int resultCount) {
        this.pattern = Objects.requireNonNull(pattern);
        this.key = Objects.requireNonNull(key);
        this.resultId = Objects.requireNonNull(resultId);
        this.resultCount = Math.max(1, resultCount);
    }

    /**
     * Ingredient matcher.
     * - vanilla: match by {@link Material}
     * - custom: match by PDC item_id
     */
    public static final class Ingredient {
        public final Set<Material> vanilla;
        public final Set<String> customIds;

        public Ingredient(Set<Material> vanilla, Set<String> customIds) {
            this.vanilla = vanilla == null ? Collections.emptySet() : Collections.unmodifiableSet(new HashSet<>(vanilla));
            this.customIds = customIds == null ? Collections.emptySet() : Collections.unmodifiableSet(new HashSet<>(customIds));
        }

        public static Ingredient ofVanilla(Material m) {
            return new Ingredient(Set.of(m), null);
        }

        public static Ingredient ofCustom(String id) {
            return new Ingredient(null, Set.of(id));
        }

        public static Ingredient of(Set<Material> vanilla, Set<String> customIds) {
            return new Ingredient(vanilla, customIds);
        }
    }
}
