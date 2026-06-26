package win.pokedemo;

import java.util.*;

/**
 * Lightweight rule registry/index for large spawn tables.
 * It narrows candidate rules before the expensive per-position matching runs.
 */
public final class SpawnRuleRegistry {
    private final List<SpawnTable.Entry> all = new ArrayList<>();
    private final Map<String, List<SpawnTable.Entry>> byBucket = new HashMap<>();
    private final Map<String, List<SpawnTable.Entry>> byPosition = new HashMap<>();
    private final Map<String, List<SpawnTable.Entry>> byDimension = new HashMap<>();
    private final Map<String, List<SpawnTable.Entry>> byBiomeHint = new HashMap<>();
    private final Map<String, List<SpawnTable.Entry>> byExactBiome = new HashMap<>();
    private final Map<String, List<SpawnTable.Entry>> byTime = new HashMap<>();
    private final Map<String, List<SpawnTable.Entry>> byWeather = new HashMap<>();

    public void rebuild(Collection<SpawnTable.Entry> entries) {
        all.clear(); byBucket.clear(); byPosition.clear(); byDimension.clear(); byBiomeHint.clear();
        byExactBiome.clear(); byTime.clear(); byWeather.clear();
        if (entries == null) return;
        for (SpawnTable.Entry e : entries) {
            if (e == null || e.species == null || e.species.isBlank()) continue;
            all.add(e);
            index(byBucket, norm(e.bucket), e);
            index(byPosition, norm(e.normalizedPosition()), e);
            if (e.dimensions != null && !e.dimensions.isEmpty()) for (String d : e.dimensions) index(byDimension, norm(d), e); else index(byDimension, "*", e);
            if (e.biomes != null && !e.biomes.isEmpty()) for (String b : e.biomes) index(byExactBiome, norm(b), e); else index(byExactBiome, "*", e);
            String tm = norm(e.time); if (tm.isBlank() || tm.equals("any")) tm = "*"; index(byTime, tm, e);
            String ww = norm(e.weather); if (ww.isBlank() || ww.equals("any")) ww = "*"; index(byWeather, ww, e);
            boolean hinted = false;
            if (e.biomeTags != null) for (String tag : e.biomeTags) { index(byBiomeHint, norm(tag), e); hinted = true; }
            if (e.biomeGroups != null) for (String g : e.biomeGroups) { index(byBiomeHint, "group:" + norm(g), e); hinted = true; }
            if (!hinted) index(byBiomeHint, "*", e);
        }
    }

    public List<SpawnTable.Entry> getCandidates(ResolvedSpawnPosition pos, String bucket) {
        if (pos == null || pos.location == null || pos.location.getWorld() == null) return Collections.emptyList();
        String wantBucket = norm(bucket);
        String posType = norm(pos.positionType);
        String dim = "";
        try { dim = pos.location.getWorld().getKey() != null ? norm(pos.location.getWorld().getKey().toString()) : ""; } catch (Throwable ignored) {}
        String biome = norm(pos.biomeKey);
        String timeKey = inferTimeKey(pos.location.getWorld().getTime());
        String weatherKey = inferWeatherKey(pos.raining, pos.thundering);

        List<SpawnTable.Entry> bucketPool = wantBucket.isBlank() ? all : byBucket.getOrDefault(wantBucket, Collections.emptyList());
        if (bucketPool.isEmpty()) return Collections.emptyList();
        Set<SpawnTable.Entry> posPool = union(byPosition.get(posType), byPosition.get("*"), bucketPool);
        Set<SpawnTable.Entry> dimPool = union(byDimension.get(dim), byDimension.get("*"), bucketPool);
        Set<SpawnTable.Entry> biomePool = union(byExactBiome.get(biome), byExactBiome.get("*"), bucketPool);
        Set<SpawnTable.Entry> timePool = union(byTime.get(timeKey), byTime.get("*"), bucketPool);
        Set<SpawnTable.Entry> weatherPool = union(byWeather.get(weatherKey), byWeather.get("*"), bucketPool);

        Set<String> hints = new LinkedHashSet<>();
        if (pos.biomeTags != null) hints.addAll(pos.biomeTags);
        String simple = simplifyBiome(pos.biomeKey);
        if (!simple.isBlank()) { hints.add(simple); hints.add("group:" + simple); }
        hints.add("*");
        Set<SpawnTable.Entry> hintPool = new LinkedHashSet<>();
        for (String h : hints) hintPool.addAll(byBiomeHint.getOrDefault(norm(h), Collections.emptyList()));
        if (hintPool.isEmpty()) hintPool.addAll(bucketPool);

        List<SpawnTable.Entry> out = new ArrayList<>();
        for (SpawnTable.Entry e : bucketPool) {
            if (!posPool.contains(e)) continue;
            if (!dimPool.contains(e)) continue;
            if (!biomePool.contains(e)) continue;
            if (!timePool.contains(e)) continue;
            if (!weatherPool.contains(e)) continue;
            if (!hintPool.contains(e)) continue;
            out.add(e);
        }
        return out;
    }

    private Set<SpawnTable.Entry> union(List<SpawnTable.Entry> a, List<SpawnTable.Entry> b, List<SpawnTable.Entry> fallback) {
        LinkedHashSet<SpawnTable.Entry> s = new LinkedHashSet<>();
        if (a != null) s.addAll(a); if (b != null) s.addAll(b); if (s.isEmpty() && fallback != null) s.addAll(fallback);
        return s;
    }

    private void index(Map<String, List<SpawnTable.Entry>> map, String key, SpawnTable.Entry e) { if (key.isBlank()) key = "*"; map.computeIfAbsent(key, k -> new ArrayList<>()).add(e); }
    private String simplifyBiome(String biomeKey) { String s = norm(biomeKey); if (s.startsWith("minecraft:")) s = s.substring("minecraft:".length()); return s; }
    private String inferTimeKey(long ticks) { long t = ticks % 24000L; if (t >= 0 && t < 12300) return "day"; if (t >= 12300 && t < 13000) return "dusk"; if (t >= 13000 && t < 23000) return "night"; return "dawn"; }
    private String inferWeatherKey(boolean rain, boolean thunder) { if (thunder) return "thunder"; if (rain) return "rain"; return "clear"; }
    private String norm(String s) { return s == null ? "" : s.trim().toLowerCase(Locale.ROOT); }
}
