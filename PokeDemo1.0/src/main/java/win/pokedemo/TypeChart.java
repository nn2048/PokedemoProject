package win.pokedemo;
import java.util.List;

public final class TypeChart {
    private TypeChart() {}

    public static double effectiveness(String attackType, List<String> defTypes) {
        if (attackType == null || defTypes == null || defTypes.isEmpty()) return 1.0;
        double mult = 1.0;
        for (String def : defTypes) {
            mult *= single(attackType, def);
            if (mult == 0.0) return 0.0;
        }
        return mult;
    }

    private static double single(String atk, String def) {
        if (atk == null || def == null) return 1.0;
        atk = atk.toLowerCase();
        def = def.toLowerCase();
        return switch (atk) {
            case "normal" -> switch (def) {
                case "ghost" -> 0.0;
                case "rock" -> 0.5;
                case "steel" -> 0.5;
                default -> 1.0;
            };
            case "fire" -> switch (def) {
                case "bug" -> 2.0;
                case "dragon" -> 0.5;
                case "fire" -> 0.5;
                case "grass" -> 2.0;
                case "ice" -> 2.0;
                case "rock" -> 0.5;
                case "steel" -> 2.0;
                case "water" -> 0.5;
                default -> 1.0;
            };
            case "water" -> switch (def) {
                case "dragon" -> 0.5;
                case "fire" -> 2.0;
                case "grass" -> 0.5;
                case "ground" -> 2.0;
                case "rock" -> 2.0;
                case "water" -> 0.5;
                default -> 1.0;
            };
            case "electric" -> switch (def) {
                case "dragon" -> 0.5;
                case "electric" -> 0.5;
                case "flying" -> 2.0;
                case "grass" -> 0.5;
                case "ground" -> 0.0;
                case "water" -> 2.0;
                default -> 1.0;
            };
            case "grass" -> switch (def) {
                case "bug" -> 0.5;
                case "dragon" -> 0.5;
                case "fire" -> 0.5;
                case "flying" -> 0.5;
                case "grass" -> 0.5;
                case "ground" -> 2.0;
                case "poison" -> 0.5;
                case "rock" -> 2.0;
                case "steel" -> 0.5;
                case "water" -> 2.0;
                default -> 1.0;
            };
            case "ice" -> switch (def) {
                case "dragon" -> 2.0;
                case "fire" -> 0.5;
                case "flying" -> 2.0;
                case "grass" -> 2.0;
                case "ground" -> 2.0;
                case "ice" -> 0.5;
                case "steel" -> 0.5;
                case "water" -> 0.5;
                default -> 1.0;
            };
            case "fighting" -> switch (def) {
                case "bug" -> 0.5;
                case "dark" -> 2.0;
                case "fairy" -> 0.5;
                case "flying" -> 0.5;
                case "ghost" -> 0.0;
                case "ice" -> 2.0;
                case "normal" -> 2.0;
                case "poison" -> 0.5;
                case "psychic" -> 0.5;
                case "rock" -> 2.0;
                case "steel" -> 2.0;
                default -> 1.0;
            };
            case "poison" -> switch (def) {
                case "fairy" -> 2.0;
                case "ghost" -> 0.5;
                case "grass" -> 2.0;
                case "ground" -> 0.5;
                case "poison" -> 0.5;
                case "rock" -> 0.5;
                case "steel" -> 0.0;
                default -> 1.0;
            };
            case "ground" -> switch (def) {
                case "bug" -> 0.5;
                case "electric" -> 2.0;
                case "fire" -> 2.0;
                case "flying" -> 0.0;
                case "grass" -> 0.5;
                case "poison" -> 2.0;
                case "rock" -> 2.0;
                case "steel" -> 2.0;
                default -> 1.0;
            };
            case "flying" -> switch (def) {
                case "bug" -> 2.0;
                case "electric" -> 0.5;
                case "fighting" -> 2.0;
                case "grass" -> 2.0;
                case "rock" -> 0.5;
                case "steel" -> 0.5;
                default -> 1.0;
            };
            case "psychic" -> switch (def) {
                case "dark" -> 0.0;
                case "fighting" -> 2.0;
                case "poison" -> 2.0;
                case "psychic" -> 0.5;
                case "steel" -> 0.5;
                default -> 1.0;
            };
            case "bug" -> switch (def) {
                case "dark" -> 2.0;
                case "fairy" -> 0.5;
                case "fighting" -> 0.5;
                case "fire" -> 0.5;
                case "flying" -> 0.5;
                case "ghost" -> 0.5;
                case "grass" -> 2.0;
                case "poison" -> 0.5;
                case "psychic" -> 2.0;
                case "steel" -> 0.5;
                default -> 1.0;
            };
            case "rock" -> switch (def) {
                case "bug" -> 2.0;
                case "fighting" -> 0.5;
                case "fire" -> 2.0;
                case "flying" -> 2.0;
                case "ground" -> 0.5;
                case "ice" -> 2.0;
                case "steel" -> 0.5;
                default -> 1.0;
            };
            case "ghost" -> switch (def) {
                case "dark" -> 0.5;
                case "ghost" -> 2.0;
                case "normal" -> 0.0;
                case "psychic" -> 2.0;
                default -> 1.0;
            };
            case "dragon" -> switch (def) {
                case "dragon" -> 2.0;
                case "fairy" -> 0.0;
                case "steel" -> 0.5;
                default -> 1.0;
            };
            case "dark" -> switch (def) {
                case "dark" -> 0.5;
                case "fairy" -> 0.5;
                case "fighting" -> 0.5;
                case "ghost" -> 2.0;
                case "psychic" -> 2.0;
                default -> 1.0;
            };
            case "steel" -> switch (def) {
                case "electric" -> 0.5;
                case "fairy" -> 2.0;
                case "fire" -> 0.5;
                case "ice" -> 2.0;
                case "rock" -> 2.0;
                case "steel" -> 0.5;
                case "water" -> 0.5;
                default -> 1.0;
            };
            case "fairy" -> switch (def) {
                case "dark" -> 2.0;
                case "dragon" -> 2.0;
                case "fighting" -> 2.0;
                case "fire" -> 0.5;
                case "poison" -> 0.5;
                case "steel" -> 0.5;
                default -> 1.0;
            };
            default -> 1.0;
        };
    }
}
