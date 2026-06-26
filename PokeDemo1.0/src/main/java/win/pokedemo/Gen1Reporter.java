package win.pokedemo;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * Generates a report for Gen1 move support coverage.
 *
 * Gen1 moves are identified by Pokemon Showdown "num" field (1..165).
 */
public final class Gen1Reporter {
    private Gen1Reporter() {}

    private static final Set<String> SUPPORTED_EFFECT_KINDS = Set.of(
            "heal",
            "drain",
            "set_status",
            "screen",
            "leech_seed",
            "stat_stage",
            "multi_hit",
            "recoil",
            "flinch",
            "confusion",
            "two_turn",
            "fixed_damage",
            "level_damage",
            "half_hp_damage",
            "ohko",
            "selfdestruct"
    );

    public record Summary(int gen1Total, int supported, int unsupported) {}

    public static Summary generate(PokeDemoPlugin plugin) {
        Dex dex = plugin.getDex();
        Path reports = plugin.getDataFolder().toPath().resolve("reports");
        try {
            if (!Files.exists(reports)) Files.createDirectories(reports);
        } catch (Exception ignored) {}

        // Build lists
        List<Move> gen1Moves = new ArrayList<>();
        for (Move m : dex.allMoves()) {
            int num = m.numSafe();
            if (num >= 1 && num <= 165) {
                gen1Moves.add(m);
            }
        }
        gen1Moves.sort(Comparator.comparingInt(Move::numSafe));

        List<Move> unsupported = new ArrayList<>();
        List<Move> supported = new ArrayList<>();

        for (Move m : gen1Moves) {
            if (isMoveSupported(m)) supported.add(m);
            else unsupported.add(m);
        }

        // Write report
        Path out = reports.resolve("gen1_move_support_report.yml");
        StringBuilder sb = new StringBuilder();
        sb.append("generatedAt: \"").append(OffsetDateTime.now()).append("\"\n");
        sb.append("gen1Total: ").append(gen1Moves.size()).append("\n");
        sb.append("supported: ").append(supported.size()).append("\n");
        sb.append("unsupported: ").append(unsupported.size()).append("\n");
        sb.append("supportedKinds:\n");
        for (String k : new TreeSet<>(SUPPORTED_EFFECT_KINDS)) {
            sb.append("  - \"").append(escapeYaml(k)).append("\"\n");
        }

        sb.append("unsupportedMoves:\n");
        for (Move m : unsupported) {
            sb.append("  - id: \"").append(escapeYaml(m.id())).append("\"\n");
            sb.append("    num: ").append(m.numSafe()).append("\n");
            sb.append("    category: \"").append(escapeYaml(m.category())).append("\"\n");
            sb.append("    effects:\n");
            List<Map<String, Object>> effs = new ArrayList<>();
            if (m.effectsSafe() != null) effs.addAll(m.effectsSafe());
            if ((effs == null || effs.isEmpty()) && m.effect() != null && !m.effect().isEmpty()) {
                effs = List.of(m.effect());
            }
            if (effs == null || effs.isEmpty()) {
                sb.append("      - \"(none)\"\n");
            } else {
                for (Map<String, Object> ef : effs) {
                    String kind = null;
                    if (ef != null) {
                        Object k = ef.get("kind");
                        if (k == null) k = ef.get("id");
                        if (k != null) kind = String.valueOf(k).toLowerCase(Locale.ROOT);
                    }
                    sb.append("      - \"").append(escapeYaml(kind == null ? "(unknown)" : kind)).append("\"\n");
                }
            }
        }

        try {
            Files.writeString(out, sb.toString(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to write gen1 report: " + e.getMessage());
        }

        return new Summary(gen1Moves.size(), supported.size(), unsupported.size());
    }

    private static boolean isMoveSupported(Move m) {
        // Status moves without any mapped effects are NOT supported.
        List<Map<String, Object>> effs = new ArrayList<>();
        if (m.effectsSafe() != null) effs.addAll(m.effectsSafe());
        if (effs.isEmpty() && m.effect() != null && !m.effect().isEmpty()) effs.add(m.effect());

        if ("status".equalsIgnoreCase(m.category())) {
            if (effs.isEmpty()) return false;
        }

        // Damaging moves with no effects are OK.
        for (Map<String, Object> ef : effs) {
            if (ef == null || ef.isEmpty()) continue;
            Object k = ef.get("kind");
            if (k == null) k = ef.get("id");
            if (k == null) return false;
            String kind = String.valueOf(k).toLowerCase(Locale.ROOT);
            if (!SUPPORTED_EFFECT_KINDS.contains(kind)) return false;
        }
        return true;
    }

    private static String escapeYaml(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
