package win.pokedemo.bridge.common;

public enum PokemonStatus {
    NONE,
    FNT,
    PSN,
    BRN,
    PAR,
    SLP,
    FRZ,
    TOX;

    public static PokemonStatus fromWire(String value) {
        if (value == null || value.isBlank()) {
            return NONE;
        }
        try {
            return PokemonStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return NONE;
        }
    }
}
