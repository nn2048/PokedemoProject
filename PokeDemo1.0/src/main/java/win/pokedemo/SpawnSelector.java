package win.pokedemo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SpawnSelector {
    private final SpawnTable table;

    public SpawnSelector(SpawnTable table) {
        this.table = table;
    }

    public Selection select(SpawnZone zone, List<ResolvedSpawnPosition> positions, String bucket) {
        return selectInternal(zone, positions, bucket, false);
    }

    public Selection selectFishing(List<ResolvedSpawnPosition> positions, String bucket, FishingContext fishingContext) {
        return selectInternal(null, positions, bucket, true, fishingContext);
    }

    private Selection selectInternal(SpawnZone zone, List<ResolvedSpawnPosition> positions, String bucket, boolean includeFishingOnly) {
        return selectInternal(zone, positions, bucket, includeFishingOnly, null);
    }

    private Selection selectInternal(SpawnZone zone, List<ResolvedSpawnPosition> positions, String bucket, boolean includeFishingOnly, FishingContext fishingContext) {
        if (positions == null || positions.isEmpty()) return null;
        Map<SpawnTable.Entry, Integer> totals = new HashMap<>();
        Map<SpawnTable.Entry, List<WeightedPosition>> byEntry = new HashMap<>();

        for (ResolvedSpawnPosition pos : positions) {
            List<SpawnTable.Entry> candidates = table.getCandidates(pos, bucket, includeFishingOnly);
            if (candidates == null || candidates.isEmpty()) continue;
            for (SpawnTable.Entry entry : candidates) {
                if (entry == null || table.isNaturalSpawnBlacklisted(entry.species)) continue;
                if (includeFishingOnly && !entry.matchesFishingContext(fishingContext)) continue;
                int w = table.getEffectiveWeight(entry, pos);
                if (zone != null && zone.world != null && zone.player != null) {
                    w = applyEcologyPenalty(zone, pos, entry, w);
                }
                if (w <= 0) continue;
                totals.put(entry, totals.getOrDefault(entry, 0) + w);
                byEntry.computeIfAbsent(entry, k -> new ArrayList<>()).add(new WeightedPosition(pos, w));
            }
        }

        if (totals.isEmpty()) return null;
        int total = 0;
        for (int w : totals.values()) total += Math.max(0, w);
        if (total <= 0) return null;

        int roll = Util.RND.nextInt(total);
        SpawnTable.Entry chosen = null;
        for (Map.Entry<SpawnTable.Entry, Integer> en : totals.entrySet()) {
            roll -= en.getValue();
            if (roll < 0) {
                chosen = en.getKey();
                break;
            }
        }
        if (chosen == null) return null;

        List<WeightedPosition> weightedPositions = byEntry.get(chosen);
        if (weightedPositions == null || weightedPositions.isEmpty()) return null;
        int posTotal = 0;
        for (WeightedPosition wp : weightedPositions) posTotal += Math.max(0, wp.weight);
        if (posTotal <= 0) return null;
        int posRoll = Util.RND.nextInt(posTotal);
        for (WeightedPosition wp : weightedPositions) {
            posRoll -= wp.weight;
            if (posRoll < 0) return new Selection(chosen, wp.position);
        }
        return new Selection(chosen, weightedPositions.get(weightedPositions.size() - 1).position);
    }


    private int applyEcologyPenalty(SpawnZone zone, ResolvedSpawnPosition pos, SpawnTable.Entry entry, int baseWeight) {
        if (baseWeight <= 0 || zone == null || zone.world == null || zone.player == null || entry == null) return baseWeight;
        int nearbySame = 0;
        int nearbyBucket = 0;
        double radius = Math.max(10.0, zone.player.getLocation().distance(pos.location) * 0.75);
        try {
            for (org.bukkit.entity.Entity ent : zone.world.getNearbyEntities(pos.location, radius, radius, radius)) {
                if (!(ent instanceof org.bukkit.entity.Wolf wolf)) continue;
                String sid = wolf.getPersistentDataContainer().get(PokeDemoPlugin.INSTANCE.KEY_SPECIES, org.bukkit.persistence.PersistentDataType.STRING);
                String buck = wolf.getPersistentDataContainer().get(PokeDemoPlugin.INSTANCE.KEY_BUCKET, org.bukkit.persistence.PersistentDataType.STRING);
                if (sid != null && sid.equalsIgnoreCase(entry.species)) nearbySame++;
                if (buck != null && entry.bucket != null && buck.equalsIgnoreCase(entry.bucket)) nearbyBucket++;
            }
        } catch (Throwable ignored) {}
        double mul = 1.0;
        if (nearbySame >= 1) mul *= Math.pow(0.55D, nearbySame);
        if (nearbyBucket >= 3) mul *= 0.70D;
        return Math.max(0, (int) Math.floor(baseWeight * mul));
    }

    private static final class WeightedPosition {
        final ResolvedSpawnPosition position;
        final int weight;
        WeightedPosition(ResolvedSpawnPosition position, int weight) {
            this.position = position;
            this.weight = weight;
        }
    }

    public static final class Selection {
        public final SpawnTable.Entry entry;
        public final ResolvedSpawnPosition position;
        public Selection(SpawnTable.Entry entry, ResolvedSpawnPosition position) {
            this.entry = entry;
            this.position = position;
        }
    }
}
