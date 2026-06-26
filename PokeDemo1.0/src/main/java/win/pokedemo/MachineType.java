package win.pokedemo;

/** Types of simple overworld machines implemented as custom Note Blocks. */
public enum MachineType {
    PC,
    HEALER,
    PASTURE,
    FOSSIL,
    FOSSIL_ANALYZER,
    TRADE,
    CLONE;

    public static MachineType fromId(String id) {
        if (id == null) return null;
        return switch (id) {
            case "pc_machine" -> PC;
            case "healer_machine" -> HEALER;
            case "pasture_machine" -> PASTURE;
            case "fossil_machine" -> FOSSIL;
            case "fossil_analyzer" -> FOSSIL_ANALYZER;
            case "trade_machine" -> TRADE;
            case "clone_machine" -> CLONE;
            default -> null;
        };
    }
}
