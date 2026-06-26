package win.pokedemo;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

public final class SpawnMechanicAudit {
    private SpawnMechanicAudit() {}

    public static File writeReport(PokeDemoPlugin plugin, SpawnTable table) throws Exception {
        File dir = new File(plugin.getDataFolder(), "reports");
        dir.mkdirs();
        File out = new File(dir, "spawn_mechanic_audit.txt");

        List<SpawnTable.Entry> all = table.getAllEntries();
        Map<String, List<SpawnTable.Entry>> bySpecies = new TreeMap<>();
        for (SpawnTable.Entry e : all) {
            if (e == null || e.species == null || e.species.isBlank()) continue;
            bySpecies.computeIfAbsent(e.species.toLowerCase(Locale.ROOT), k -> new ArrayList<>()).add(e);
        }

        List<String> riskySpecies = new ArrayList<>();
        List<String> riskyRules = new ArrayList<>();
        List<String> ecologyNotes = new ArrayList<>();
        Map<String, Integer> confidenceCounts = new LinkedHashMap<>();
        Map<String, Integer> ignoredCounts = new LinkedHashMap<>();

        for (SpawnTable.Entry e : all) {
            String conf = e.importConfidence == null ? "UNKNOWN" : e.importConfidence;
            confidenceCounts.put(conf, confidenceCounts.getOrDefault(conf, 0) + 1);
            for (String ig : e.ignoredConditions) {
                ignoredCounts.put(ig, ignoredCounts.getOrDefault(ig, 0) + 1);
            }
            if ("LOW".equals(conf) || "UNSAFE".equals(conf)) {
                riskyRules.add(ruleLine(e));
            }
            if (e.weight >= 40 && (e.presetConstraints == null || e.presetConstraints.isEmpty())
                    && (e.importConfidence == null || !"HIGH".equals(e.importConfidence))) {
                ecologyNotes.add("high_weight_broad | " + ruleLine(e));
            }
        }

        for (Map.Entry<String, List<SpawnTable.Entry>> en : bySpecies.entrySet()) {
            String species = en.getKey();
            List<SpawnTable.Entry> list = en.getValue();
            int totalWeight = 0;
            int low = 0;
            int unsafe = 0;
            int unsupportedTags = 0;
            int herdRules = 0;
            int fishingRules = 0;
            for (SpawnTable.Entry e : list) {
                totalWeight += Math.max(0, e.weight);
                if ("LOW".equals(e.importConfidence)) low++;
                if ("UNSAFE".equals(e.importConfidence)) unsafe++;
                if (e.herd || e.herdMax > 1) herdRules++;
                if (e.fishingOnly) fishingRules++;
                for (String ig : e.ignoredConditions) {
                    if (ig.startsWith("unsupported-biome-tag:")) unsupportedTags++;
                }
            }
            if (unsafe > 0 || low >= 3 || totalWeight >= 100 || unsupportedTags > 0) {
                riskySpecies.add(species + " | rules=" + list.size()
                        + " | totalWeight=" + totalWeight
                        + " | LOW=" + low
                        + " | UNSAFE=" + unsafe
                        + " | unsupportedTags=" + unsupportedTags
                        + " | herd=" + herdRules
                        + " | fishing=" + fishingRules);
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("PokeDemo Spawn Mechanic Audit\n\n");
        sb.append("totalRules=").append(all.size()).append("\n");
        sb.append("species=").append(bySpecies.size()).append("\n\n");

        sb.append("=== Confidence Summary ===\n");
        for (Map.Entry<String, Integer> en : confidenceCounts.entrySet()) {
            sb.append(en.getKey()).append('=').append(en.getValue()).append('\n');
        }

        sb.append("\n=== Ignored Condition Summary ===\n");
        ignoredCounts.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(60)
                .forEach(en -> sb.append(en.getKey()).append('=').append(en.getValue()).append('\n'));

        sb.append("\n=== High Risk Species ===\n");
        riskySpecies.stream().sorted().limit(250).forEach(x -> sb.append(x).append('\n'));

        sb.append("\n=== High Risk Rules ===\n");
        riskyRules.stream().sorted().limit(400).forEach(x -> sb.append(x).append('\n'));

        sb.append("\n=== Ecology Notes ===\n");
        ecologyNotes.stream().sorted().limit(300).forEach(x -> sb.append(x).append('\n'));

        Files.writeString(out.toPath(), sb.toString(), StandardCharsets.UTF_8);
        return out;
    }

    private static String ruleLine(SpawnTable.Entry e) {
        return (e.species == null ? "?" : e.species)
                + " | conf=" + (e.importConfidence == null ? "UNKNOWN" : e.importConfidence)
                + " | bucket=" + e.bucket
                + " | weight=" + e.weight
                + " | pos=" + e.position
                + " | presets=" + e.originalPresets
                + " | presetConstraints=" + e.presetConstraints
                + " | ignored=" + e.ignoredConditions
                + " | translated=" + e.translatedConditions
                + " | herd=" + e.herd + "/" + e.herdMin + "-" + e.herdMax
                + " | source=" + e.importSourceId;
    }
}
