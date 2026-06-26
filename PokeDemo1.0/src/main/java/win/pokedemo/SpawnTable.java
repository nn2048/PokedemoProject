package win.pokedemo;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;

/**
 * Minimal spawn table (YAML) used by the wild spawn system.
 *
 * This is an intermediate step toward Cobblemon-style JSON pools.
 */
public class SpawnTable {

    /**
     * True if the loaded table is the new custom rules format (meta/buckets/rules).
     * When enabled, SpawnManager must NOT fallback to random species when no rule matches.
     */
    private boolean rulesMode = false;

    // Species disallowed from the natural spawn selector (special acquisition only).
    private final Set<String> naturalSpawnBlacklist = new HashSet<>();

    public boolean isRulesMode() {
        return rulesMode;
    }

    public static class Entry {
        public String species;
        public int weight = 1;
        public int minLevel = 1;
        public int maxLevel = 100;

        // Cobblemon-style bucket / rarity group (common/uncommon/rare/ultra_rare...)
        public String bucket = "common";

        // Filters
        public String time = "any";      // day/night/dusk/dawn/any
        public String weather = "any";   // clear/rain/thunder/any
        public int minY = Integer.MIN_VALUE;
        public int maxY = Integer.MAX_VALUE;
        public boolean waterOnly = false;

        // New custom rules: spawn position type (grounded/surface/submerged/seafloor/cave)
        public String position = "grounded";

        // Optional dimension filtering (minecraft:overworld / minecraft:the_nether / minecraft:the_end)
        public Set<String> dimensions = new HashSet<>();

        // Optional biome groups (plains/forest/jungle/swamp/arid/cold/mountain/cave/ocean/river_lake/nether/end/urban)
        public Set<String> biomeGroups = new HashSet<>();
        public Set<String> antiBiomeGroups = new HashSet<>();

        // Optional: spawn on land but must be near water (for psyduck etc.)
        public boolean nearWater = false;

        // Light filters (optional)
        public int minSkyLight = Integer.MIN_VALUE;
        public int maxSkyLight = Integer.MAX_VALUE;
        public int minBlockLight = Integer.MIN_VALUE;
        public int maxBlockLight = Integer.MAX_VALUE;

        // Optional sky visibility requirement
        public Boolean canSeeSky = null;

        // Biome tags (Cobblemon-style, e.g. #cobblemon:is_overworld)
        public Set<String> biomeTags = new HashSet<>();
        public Set<String> antiBiomeTags = new HashSet<>();

        // Nearby blocks requirement (Cobblemon-style neededNearbyBlocks)
        public Set<String> nearBlocks = new HashSet<>();
        public int nearBlocksRadius = 12;

        // Structure proximity (optional)
        public String nearStructure = null; // e.g. VILLAGE
        public int nearStructureRadius = 128;
        public boolean nearStructureNegate = false;

        // Biomes filter
        public Set<String> biomes = new HashSet<>(); // biome keys (minecraft:plains) OR Biome enum name

        // Optional Cobblemon-like dynamic weight modifiers.
        public List<WeightModifier> weightModifiers = new ArrayList<>();

        // Stage 1 import fidelity metadata.
        public String importSource = null;
        public String importSourceId = null;
        public String importConfidence = "UNKNOWN"; // HIGH / MEDIUM / LOW / UNSAFE / UNKNOWN
        public String fallbackReason = null;
        public Set<String> originalPresets = new LinkedHashSet<>();
        public Set<String> originalBiomeTags = new LinkedHashSet<>();
        public Set<String> originalAntiBiomeTags = new LinkedHashSet<>();
        public Set<String> originalDimensions = new LinkedHashSet<>();
        public Set<String> ignoredConditions = new LinkedHashSet<>();
        public Set<String> translatedConditions = new LinkedHashSet<>();

        // Stage 4/5: independent preset constraints and special conditions.
        public Set<String> presetConstraints = new LinkedHashSet<>();
        public Boolean requireSlimeChunk = null;
        public Set<String> moonPhases = new LinkedHashSet<>();
        public boolean fishingOnly = false;
        public int fishingMinLure = 0;
        public int fishingMaxLure = Integer.MAX_VALUE;
        public Set<String> fishingRodTypes = new LinkedHashSet<>();
        public Set<String> fishingBaits = new LinkedHashSet<>();
        public boolean herd = false;
        public int herdMin = 1;
        public int herdMax = 1;
        public int herdRadius = 6;
        public int herdMinDistance = 2;

        private boolean matchesMoonPhase(World world) {
            if (moonPhases == null || moonPhases.isEmpty() || world == null) return true;
            String cur = MoonPhaseUtil.currentPhase(world);
            if (cur == null || cur.isBlank()) return true;
            for (String mp : moonPhases) {
                if (mp == null || mp.isBlank()) continue;
                if (MoonPhaseUtil.matches(mp, cur)) return true;
            }
            return false;
        }

        private boolean matchesSlimeChunk(Location loc) {
            if (requireSlimeChunk == null || loc == null || loc.getWorld() == null) return true;
            boolean in = SlimeChunkUtil.isSlimeChunk(loc.getWorld(), loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
            return requireSlimeChunk.booleanValue() == in;
        }

        private boolean matchesPresetConstraints(ResolvedSpawnPosition pos) {
            if (presetConstraints == null || presetConstraints.isEmpty()) return true;
            for (String raw : presetConstraints) {
                if (raw == null || raw.isBlank()) continue;
                String p = raw.trim().toLowerCase(Locale.ROOT);
                boolean ok;
                switch (p) {
                    case "urban", "village", "town" -> ok = StructureUtil.isNearAnyStructure(pos.location.getWorld(), pos.location, java.util.List.of("VILLAGE"), 96, false);
                    case "pokemon_center", "pokecenter" -> ok = pos.archetypes.contains("machine_near") || NearbyBlockPredicates.hasAnyNearby(pos.location, java.util.Set.of("POKEDEMO_PC", "pokedemo:healer_machine", "healer_machine"), 12);
                    case "redstone" -> ok = pos.archetypes.contains("redstone_near") || NearbyBlockPredicates.hasAnyNearby(pos.location, java.util.Set.of("POKEDEMO_REDSTONE"), 10);
                    case "webs" -> ok = pos.archetypes.contains("webs_near") || NearbyBlockPredicates.hasAnyNearby(pos.location, java.util.Set.of("minecraft:cobweb"), 10);
                    case "lava" -> ok = pos.archetypes.contains("lava_near") || NearbyBlockPredicates.hasAnyNearby(pos.location, java.util.Set.of("minecraft:lava", "minecraft:magma_block"), 12);
                    case "illager_structures" -> ok = StructureUtil.isNearAnyStructure(pos.location.getWorld(), pos.location, java.util.List.of("PILLAGER_OUTPOST", "WOODLAND_MANSION"), 128, false);
                    case "nether_structures" -> ok = StructureUtil.isNearAnyStructure(pos.location.getWorld(), pos.location, java.util.List.of("FORTRESS", "BASTION_REMNANT", "RUINED_PORTAL_NETHER"), 128, false);
                    case "derelict" -> ok = StructureUtil.isNearAnyStructure(pos.location.getWorld(), pos.location, java.util.List.of("MINESHAFT", "TRAIL_RUINS", "RUINED_PORTAL", "ANCIENT_CITY"), 128, false) || NearbyBlockPredicates.hasAnyNearby(pos.location, java.util.Set.of("minecraft:cobweb"), 10);
                    case "ocean_ruins", "ocean_ruin" -> ok = StructureUtil.isNearAnyStructure(pos.location.getWorld(), pos.location, java.util.List.of("OCEAN_RUIN_COLD", "OCEAN_RUIN_WARM", "OCEAN_MONUMENT"), 128, false);
                    case "spooky" -> ok = pos.biomeTags.contains("spooky") || pos.biomeTags.contains("#cobblemon:is_spooky") || StructureUtil.isNearAnyStructure(pos.location.getWorld(), pos.location, java.util.List.of("WOODLAND_MANSION", "ANCIENT_CITY"), 128, false);
                    case "machine", "machine_near" -> ok = pos.archetypes.contains("machine_near");
                    case "urban_near", "structure_near" -> ok = pos.archetypes.contains(p);
                    default -> ok = pos.biomeTags.contains(p) || pos.biomeTags.contains("#" + p) || pos.archetypes.contains(p);
                }
                if (!ok) return false;
            }
            return true;
        }


        public boolean matchesFishingContext(FishingContext ctx) {
            if (!fishingOnly) return true;
            if (ctx == null) return false;
            if (ctx.lureLevel < fishingMinLure) return false;
            if (ctx.lureLevel > fishingMaxLure) return false;
            if (!fishingRodTypes.isEmpty()) {
                boolean ok = false;
                for (String rt : fishingRodTypes) {
                    if (ctx.matchesRodType(rt)) { ok = true; break; }
                }
                if (!ok) return false;
            }
            if (!fishingBaits.isEmpty()) {
                boolean ok = false;
                for (String b : fishingBaits) {
                    if (ctx.matchesBait(b)) { ok = true; break; }
                }
                if (!ok) return false;
            }
            return true;
        }

        public String normalizedPosition() {
            String pos = position == null ? "grounded" : position.trim().toLowerCase(Locale.ROOT);
            if (pos.equals("land")) return "grounded";
            if (pos.equals("water_surface")) return "surface";
            if (pos.equals("water")) return waterOnly ? "submerged" : "surface";
            return pos;
        }

        boolean matches(ResolvedSpawnPosition pos) {
            if (pos == null || pos.location == null || pos.location.getWorld() == null) return false;
            String want = normalizedPosition();
            if (want.equals("submerged") || want.equals("surface") || want.equals("seafloor") || want.equals("cave") || want.equals("grounded")) {
                if (!want.equalsIgnoreCase(pos.positionType)) return false;
            }
            if (!dimensions.isEmpty()) {
                try {
                    String wk = pos.location.getWorld().getKey() != null ? pos.location.getWorld().getKey().toString().toLowerCase(Locale.ROOT) : "";
                    if (!dimensions.contains(wk)) return false;
                } catch (Throwable ignored) { return false; }
            }
            if (pos.y < minY || pos.y > maxY) return false;
            if (!matchesTime(pos.location.getWorld().getTime())) return false;
            if (!matchesMoonPhase(pos.location.getWorld())) return false;
            if (!matchesWeather(pos.raining, pos.thundering)) return false;
            if (!matchesSlimeChunk(pos.location)) return false;
            if (canSeeSky != null && canSeeSky.booleanValue() != pos.canSeeSky) return false;
            if (!biomes.isEmpty()) {
                String key = pos.biomeKey == null ? "" : pos.biomeKey.toLowerCase(Locale.ROOT);
                boolean ok = false;
                for (String b : biomes) {
                    if (b == null) continue;
                    String bb = b.toLowerCase(Locale.ROOT);
                    if (bb.equals(key) || key.endsWith(bb)) { ok = true; break; }
                }
                if (!ok) return false;
            }
            if (!biomeGroups.isEmpty()) {
                boolean ok = false;
                for (String g : biomeGroups) {
                    if (BiomeGroupPredicates.matches(g, pos.location.getWorld(), pos.biomeKey, pos.location)) { ok = true; break; }
                }
                if (!ok) return false;
            }
            if (!antiBiomeGroups.isEmpty()) {
                for (String g : antiBiomeGroups) {
                    if (BiomeGroupPredicates.matches(g, pos.location.getWorld(), pos.biomeKey, pos.location)) return false;
                }
            }
            if (!antiBiomeTags.isEmpty()) {
                for (String tag : antiBiomeTags) {
                    if (BiomeTagService.matches(tag, pos.biomeKey, pos.biomeTags)) return false;
                }
            }
            if (!biomeTags.isEmpty()) {
                boolean ok = false;
                for (String tag : biomeTags) {
                    if (BiomeTagService.matches(tag, pos.biomeKey, pos.biomeTags)) { ok = true; break; }
                }
                if (!ok) return false;
            }
            if (minSkyLight != Integer.MIN_VALUE || maxSkyLight != Integer.MAX_VALUE) {
                if (pos.skyLight < minSkyLight || pos.skyLight > maxSkyLight) return false;
            }
            if (minBlockLight != Integer.MIN_VALUE || maxBlockLight != Integer.MAX_VALUE) {
                if (pos.blockLight < minBlockLight || pos.blockLight > maxBlockLight) return false;
            }
            if (nearStructure != null && !nearStructure.isBlank()) {
                String wantStructure = EnvironmentAliasService.resolveStructure(nearStructure);
                if (!StructureUtil.isNearStructure(pos.location.getWorld(), pos.location, wantStructure, nearStructureRadius, nearStructureNegate)) return false;
            }
            if (!nearBlocks.isEmpty()) {
                boolean ok = false;
                for (String need : nearBlocks) {
                    if (need == null || need.isBlank()) continue;
                    String nn = need.trim().toLowerCase(Locale.ROOT);
                    if (EnvironmentAliasService.matchesBlock(nn, pos.nearbyBlocks) || pos.nearbyBlocks.contains(nn) || pos.nearbyBlocks.contains(nn.replace("#", ""))) { ok = true; break; }
                }
                if (!ok && !NearbyBlockPredicates.hasAnyNearby(pos.location, nearBlocks, Math.max(1, nearBlocksRadius))) return false;
            }
            if (nearWater && !pos.nearWater) return false;
            if (!matchesPresetConstraints(pos)) return false;
            return true;
        }

        boolean matches(Location loc) {
            if (loc == null || loc.getWorld() == null) return false;
            String biomeKey = null;
            try {
                Biome b = loc.getBlock().getBiome();
                biomeKey = b.getKey() != null ? b.getKey().toString().toLowerCase(Locale.ROOT) : b.name().toLowerCase(Locale.ROOT);
            } catch (Throwable ignored) {
                biomeKey = "unknown";
            }
            boolean canSeeSky;
            int skyLight;
            int blockLight;
            boolean nearWaterLocal;
            try { canSeeSky = loc.getWorld().getHighestBlockYAt(loc) <= loc.getBlockY(); } catch (Throwable ignored) { canSeeSky = false; }
            try { skyLight = loc.getBlock().getLightFromSky(); } catch (Throwable ignored) { skyLight = 0; }
            try { blockLight = loc.getBlock().getLightFromBlocks(); } catch (Throwable ignored) { blockLight = 0; }
            try { nearWaterLocal = NearbyBlockPredicates.hasAnyNearby(loc, Set.of("minecraft:water"), 8); } catch (Throwable ignored) { nearWaterLocal = false; }
            ResolvedSpawnPosition pos = new ResolvedSpawnPosition(
                    loc,
                    normalizedPosition(),
                    biomeKey,
                    BiomeTagService.collectTags(biomeKey, loc, normalizedPosition(), canSeeSky, nearWaterLocal),
                    Collections.emptySet(),
                    canSeeSky,
                    skyLight,
                    blockLight,
                    nearWaterLocal,
                    loc.getWorld().hasStorm(),
                    loc.getWorld().isThundering(),
                    Collections.<String>emptySet()
            );
            return matches(pos);
        }

        private boolean matchesTime(long t) {
            boolean isDay = (t >= 0 && t < 12300);
            boolean isNight = !isDay;
            boolean isDawn = (t >= 22000 || t < 1000);
            boolean isDusk = (t >= 12000 && t < 14000);
            if ("day".equalsIgnoreCase(time)) return isDay;
            if ("night".equalsIgnoreCase(time)) return isNight;
            if ("dawn".equalsIgnoreCase(time)) return isDawn;
            if ("dusk".equalsIgnoreCase(time)) return isDusk;
            return true;
        }

        private boolean matchesWeather(boolean storm, boolean thunder) {
            if ("clear".equalsIgnoreCase(weather)) return !(storm || thunder);
            if ("rain".equalsIgnoreCase(weather)) return storm && !thunder;
            if ("thunder".equalsIgnoreCase(weather)) return thunder;
            return true;
        }
    }


    public static class BucketCfg {
        public final String id;
        public final int weight;
        public final int perPlayerCap;
        public final int cooldownTicks;

        public BucketCfg(String id, int weight, int perPlayerCap, int cooldownTicks) {
            this.id = id == null ? "common" : id.trim().toLowerCase(Locale.ROOT);
            this.weight = Math.max(0, weight);
            this.perPlayerCap = Math.max(0, perPlayerCap);
            this.cooldownTicks = Math.max(0, cooldownTicks);
        }
    }

    public static class WeightModifier {
        public String time = "any";
        public String weather = "any";
        public Boolean thundering = null;
        public Boolean raining = null;
        public Boolean canSeeSky = null;
        public String positionType = null;
        public Set<String> biomeTags = new HashSet<>();
        public double multiply = 1.0;

        public boolean matches(ResolvedSpawnPosition pos) {
            if (pos == null || pos.location == null || pos.location.getWorld() == null) return false;
            long t = pos.location.getWorld().getTime();
            boolean isDay = (t >= 0 && t < 12300);
            boolean isDawn = (t >= 22000 || t < 1000);
            boolean isDusk = (t >= 12000 && t < 14000);
            if ("day".equalsIgnoreCase(time) && !isDay) return false;
            if ("night".equalsIgnoreCase(time) && isDay) return false;
            if ("dawn".equalsIgnoreCase(time) && !isDawn) return false;
            if ("dusk".equalsIgnoreCase(time) && !isDusk) return false;
            if ("clear".equalsIgnoreCase(weather) && (pos.raining || pos.thundering)) return false;
            if ("rain".equalsIgnoreCase(weather) && !(pos.raining && !pos.thundering)) return false;
            if ("thunder".equalsIgnoreCase(weather) && !pos.thundering) return false;
            if (thundering != null && thundering.booleanValue() != pos.thundering) return false;
            if (raining != null && raining.booleanValue() != pos.raining) return false;
            if (canSeeSky != null && canSeeSky.booleanValue() != pos.canSeeSky) return false;
            if (positionType != null && !positionType.isBlank() && !positionType.equalsIgnoreCase(pos.positionType)) return false;
            if (!biomeTags.isEmpty()) {
                boolean ok = false;
                for (String tag : biomeTags) {
                    if (BiomeTagService.matches(tag, pos.biomeKey, pos.biomeTags)) { ok = true; break; }
                }
                if (!ok) return false;
            }
            return true;
        }
    }

    private final PokeDemoPlugin plugin;
    private final Dex dex;

    // biomeKey -> entries
    private final Map<String, List<Entry>> byBiome = new HashMap<>();
    private final List<Entry> any = new ArrayList<>();

    private final Map<String, BucketCfg> bucketsFromTable = new LinkedHashMap<>();
    private final SpawnRuleRegistry ruleRegistry = new SpawnRuleRegistry();

    public Map<String, BucketCfg> getBucketsFromTable() {
        return bucketsFromTable;
    }

    public SpawnTable(PokeDemoPlugin plugin, Dex dex) {
        this.plugin = plugin;
        this.dex = dex;
        BiomeTagService.initialize(plugin);
        EnvironmentAliasService.initialize(plugin);
    }

    public void reloadNaturalSpawnBlacklist() {
        naturalSpawnBlacklist.clear();
        try {
            for (String s : plugin.getConfig().getStringList("spawns.natural-spawn-blacklist")) {
                if (s == null) continue;
                String id = s.trim().toLowerCase(java.util.Locale.ROOT);
                if (!id.isBlank()) naturalSpawnBlacklist.add(id);
            }
        } catch (Throwable ignored) {}
    }

    public boolean isNaturalSpawnBlacklisted(String speciesId) {
        if (speciesId == null) return false;
        return naturalSpawnBlacklist.contains(speciesId.trim().toLowerCase(java.util.Locale.ROOT));
    }

    public void load() {
        reloadNaturalSpawnBlacklist();
        byBiome.clear();
        any.clear();
        bucketsFromTable.clear();
        rulesMode = false;

        String fileName = plugin.getConfig().getString("spawns.table-file", "spawns_gen1_custom.yml");
        File f = new File(plugin.getDataFolder(), fileName);
        if (!f.exists()) {
            // If the admin does not want to manage files, allow loading from bundled defaults.
            // We DO NOT write/overwrite any file here.
            try {
                YamlConfiguration y = new YamlConfiguration();
                try (var in = plugin.getResource("default_data/" + fileName)) {
                    if (in == null) {
                        plugin.getLogger().info("[PokeDemo] Spawn table not found (" + fileName + "); falling back to random Dex spawns.");
                        return;
                    }
                    y.loadFromString(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
                }
                loadFromYaml(fileName + "(bundled)", y);
                return;
            } catch (Throwable t) {
                plugin.getLogger().warning("[PokeDemo] Failed to load bundled spawn table (" + fileName + "): " + t.getMessage());
                return;
            }
        }

        YamlConfiguration y = new YamlConfiguration();
        try {
            y.load(f);
        } catch (Exception e) {
            plugin.getLogger().warning("[PokeDemo] Failed to load spawn table (" + fileName + "): " + e.getMessage());
            return;
        }

        loadFromYaml(fileName, y);
        try {
            for (Entry imported : new CobblemonSpawnRuleImporter(plugin, dex).loadImportedRules()) {
                if (imported != null) any.add(imported);
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("[PokeDemo] Failed to import Cobblemon spawn json rules: " + t.getMessage());
        }
        ruleRegistry.rebuild(getAllEntries());
        try {
            java.io.File audit = SpawnMechanicAudit.writeReport(plugin, this);
            plugin.getLogger().info("[PokeDemo] Spawn mechanic audit written: " + audit.getName());
        } catch (Throwable t) {
            plugin.getLogger().warning("[PokeDemo] Failed to write spawn mechanic audit: " + t.getMessage());
        }
    }

    private void loadFromYaml(String fileName, YamlConfiguration y) {
        // New custom rule format: meta/buckets/rules
        if (y.get("rules") instanceof List) {
            rulesMode = true;
            parseRuleFormat(y);
            plugin.getLogger().info("[PokeDemo] Loaded spawn rules: file=" + fileName + ", rules=" + any.size() + ", buckets=" + bucketsFromTable.size());
            ruleRegistry.rebuild(getAllEntries());
            try { writeBilingualReport("spawn_rules_gen1_custom.txt"); } catch (Throwable ignored) {}
            return;
        }

        ConfigurationSection root = y.getConfigurationSection("biomes");
        if (root == null) {
            // allow top-level list as any
            List<Map<?, ?>> list = y.getMapList("any");
            if (list != null && !list.isEmpty()) any.addAll(parseList(list));
            plugin.getLogger().info("[PokeDemo] Loaded spawn table: file=" + fileName + ", biomes=" + byBiome.size() + ", any=" + any.size());
            ruleRegistry.rebuild(getAllEntries());
            return;
        }

        for (String k : root.getKeys(false)) {
            List<Map<?, ?>> list = root.getMapList(k);
            List<Entry> entries = parseList(list);
            if (k.equalsIgnoreCase("any")) {
                any.addAll(entries);
            } else {
                byBiome.put(k, entries);
            }
        }

        plugin.getLogger().info("[PokeDemo] Loaded spawn table: file=" + fileName + ", biomes=" + byBiome.size() + ", any=" + any.size());
        ruleRegistry.rebuild(getAllEntries());
    }

    private void writeBilingualReport(String fileName) throws Exception {
        java.io.File dir = new java.io.File(plugin.getDataFolder(), "reports");
        dir.mkdirs();
        java.io.File out = new java.io.File(dir, fileName);

        // Group rules by species
        Map<String, List<Entry>> bySp = new TreeMap<>();
        for (Entry e : any) {
            if (e == null || e.species == null) continue;
            bySp.computeIfAbsent(e.species, k -> new ArrayList<>()).add(e);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("PokeDemo Gen1 刷新地点表（中英对照）\n");
        sb.append("来源：spawns_gen1_custom.yml（原创规则表）\n\n");
        sb.append("字段：维度 | 群系组 | 位置 | 桶 | 权重 | 等级 | 额外条件\n\n");

        LangManager lang = plugin.getLang();
        for (var en : bySp.entrySet()) {
            String sid = en.getKey();
            String keyName = "cobblemon.species." + sid + ".name";
            String cn = lang != null ? lang.rawPrimary(keyName) : null;
            String enName = lang != null ? lang.rawFallback(keyName) : null;
            if (cn == null || cn.isBlank()) cn = sid;
            if (enName == null || enName.isBlank()) enName = sid;

            sb.append("【").append(cn).append(" / ").append(enName).append("】(").append(sid).append(")\n");
            List<Entry> rules = en.getValue();
            rules.sort(Comparator.comparing(a -> a.bucket == null ? "" : a.bucket));
            for (Entry r : rules) {
                sb.append("  - ");
                sb.append(formatRuleLine(r)).append("\n");
            }
            sb.append("\n");
        }

        java.nio.file.Files.writeString(out.toPath(), sb.toString(), java.nio.charset.StandardCharsets.UTF_8);
    }

    private String formatRuleLine(Entry r) {
        String dims = r.dimensions == null || r.dimensions.isEmpty() ? "(任意)" : String.join(",", r.dimensions);
        String bg = r.biomeGroups == null || r.biomeGroups.isEmpty() ? "(任意)" : String.join(",", r.biomeGroups);
        String pos = r.position == null ? "land" : r.position;
        String buck = r.bucket == null ? "common" : r.bucket;
        String lv = r.minLevel + "~" + r.maxLevel;
        List<String> extra = new ArrayList<>();
        if (r.time != null && !r.time.equalsIgnoreCase("any")) extra.add("时间:" + r.time);
        if (r.weather != null && !r.weather.equalsIgnoreCase("any")) extra.add("天气:" + r.weather);
        if (r.minY != Integer.MIN_VALUE || r.maxY != Integer.MAX_VALUE) extra.add("高度:" + r.minY + "~" + r.maxY);
        if (r.nearWater) extra.add("靠近水边");
        if (r.canSeeSky != null) extra.add(r.canSeeSky ? "露天" : "地下/遮天");
        if (r.minSkyLight != Integer.MIN_VALUE || r.maxSkyLight != Integer.MAX_VALUE) extra.add("天光:" + r.minSkyLight + "~" + r.maxSkyLight);
        if (r.minBlockLight != Integer.MIN_VALUE || r.maxBlockLight != Integer.MAX_VALUE) extra.add("方块光:" + r.minBlockLight + "~" + r.maxBlockLight);
        if (r.nearStructure != null && !r.nearStructure.isBlank()) extra.add("附近结构:" + r.nearStructure + " r=" + r.nearStructureRadius);
        if (r.nearBlocks != null && !r.nearBlocks.isEmpty()) extra.add("附近方块:" + String.join(",", r.nearBlocks) + " r=" + r.nearBlocksRadius);
        if (r.weightModifiers != null && !r.weightModifiers.isEmpty()) extra.add("动态权重x" + r.weightModifiers.size());
        String ex = extra.isEmpty() ? "无" : String.join("；", extra);
        return dims + " | " + bg + " | " + pos + " | " + buck + " | w=" + r.weight + " | Lv" + lv + " | " + ex;
    }

    @SuppressWarnings("unchecked")
    private void parseRuleFormat(YamlConfiguration y) {
        // Buckets
        ConfigurationSection b = y.getConfigurationSection("buckets");
        if (b != null) {
            for (String id : b.getKeys(false)) {
                ConfigurationSection s = b.getConfigurationSection(id);
                if (s == null) continue;
                int w = s.getInt("weight", 0);
                int cap = s.getInt("perPlayerCap", 1);
                int cd = s.getInt("cooldownTicks", 0);
                String key = id.toLowerCase(Locale.ROOT);
                bucketsFromTable.put(key, new BucketCfg(key, Math.max(0, w), Math.max(0, cap), Math.max(0, cd)));
            }
        }

        List<Map<?, ?>> rules = (List<Map<?, ?>>) y.getList("rules");
        if (rules == null) return;
        for (Map<?, ?> m : rules) {
            try {
                Entry e = new Entry();
                e.species = asStr(m.get("species"), null);
                if (e.species == null) continue;

                Species resolved = dex.getSpeciesFlexible(e.species);
                if (resolved == null) continue;
                e.species = resolved.id();

                e.weight = asInt(m.get("weight"), 1);
                e.bucket = normalizeBucket(asStr(m.get("bucket"), "common"));
                e.position = asStr(m.get("positionType"), asStr(m.get("position"), "grounded"));

                // level
                Object lv = m.get("level");
                if (lv instanceof Map<?, ?> lvm) {
                    e.minLevel = asInt(lvm.get("min"), 1);
                    e.maxLevel = asInt(lvm.get("max"), 100);
                } else {
                    e.minLevel = asInt(m.get("minLevel"), 1);
                    e.maxLevel = asInt(m.get("maxLevel"), 100);
                }

                // dimensions
                Object dims = m.get("dimensions");
                if (dims instanceof List<?> dl) {
                    for (Object o : dl) {
                        String s = asStr(o, null);
                        if (s != null) e.dimensions.add(s.toLowerCase(Locale.ROOT));
                    }
                }

                // biomeGroup
                Object bg = m.get("biomeGroup");
                if (bg == null) bg = m.get("biomeGroups");
                if (bg instanceof List<?> bl) {
                    for (Object o : bl) {
                        String s = asStr(o, null);
                        if (s != null) e.biomeGroups.add(s.toLowerCase(Locale.ROOT));
                    }
                }
                Object abg = m.get("antiBiomeGroups");
                if (abg instanceof List<?> bl) {
                    for (Object o : bl) {
                        String s = asStr(o, null);
                        if (s != null) e.antiBiomeGroups.add(s.toLowerCase(Locale.ROOT));
                    }
                }

                // time/weather
                Object t = m.get("time");
                if (t instanceof List<?> tl && !tl.isEmpty()) e.time = asStr(tl.get(0), "any");
                else e.time = asStr(m.get("time"), "any");

                Object we = m.get("weather");
                if (we instanceof List<?> wl && !wl.isEmpty()) e.weather = asStr(wl.get(0), "any");
                else e.weather = asStr(m.get("weather"), "any");

                // y-range
                Object yr = m.get("y");
                if (yr instanceof Map<?, ?> ym) {
                    e.minY = asInt(ym.get("min"), Integer.MIN_VALUE);
                    e.maxY = asInt(ym.get("max"), Integer.MAX_VALUE);
                } else {
                    e.minY = asInt(m.get("minY"), Integer.MIN_VALUE);
                    e.maxY = asInt(m.get("maxY"), Integer.MAX_VALUE);
                }

                // near water
                Object near = m.get("near");
                if (near instanceof Map<?, ?> nm) {
                    e.nearWater = asBool(nm.get("water"), false);
                }
                e.canSeeSky = m.containsKey("canSeeSky") ? asBool(m.get("canSeeSky"), false) : null;
                e.minSkyLight = asInt(m.get("minSkyLight"), e.minSkyLight);
                e.maxSkyLight = asInt(m.get("maxSkyLight"), e.maxSkyLight);
                e.minBlockLight = asInt(m.get("minBlockLight"), e.minBlockLight);
                e.maxBlockLight = asInt(m.get("maxBlockLight"), e.maxBlockLight);

                // nearBlock / nearBlocks
                Object nb = m.get("nearBlock");
                if (nb instanceof Map<?, ?> nbb) {
                    String type = asStr(nbb.get("type"), null);
                    int rad = asInt(nbb.get("radius"), 12);
                    if (type != null) {
                        e.nearBlocks.add(type.toLowerCase(Locale.ROOT));
                        e.nearBlocksRadius = rad;
                    }
                }
                Object nbl = m.get("nearBlocks");
                if (nbl instanceof List<?> bl) {
                    for (Object o : bl) {
                        String s = asStr(o, null);
                        if (s != null) e.nearBlocks.add(s.toLowerCase(Locale.ROOT));
                    }
                }

                // nearStructure
                Object ns = m.get("nearStructure");
                if (ns instanceof Map<?, ?> nss) {
                    e.nearStructure = asStr(nss.get("type"), null);
                    e.nearStructureRadius = asInt(nss.get("radius"), 128);
                }

                Object bt = m.get("biomeTags");
                if (bt instanceof List<?> bl) {
                    for (Object o : bl) {
                        String s = asStr(o, null);
                        if (s != null) e.biomeTags.add(BiomeTagService.normalize(s));
                    }
                }
                Object abt = m.get("antiBiomeTags");
                if (abt instanceof List<?> bl) {
                    for (Object o : bl) {
                        String s = asStr(o, null);
                        if (s != null) e.antiBiomeTags.add(BiomeTagService.normalize(s));
                    }
                }
                Object multipliers = m.get("multipliers");
                if (multipliers instanceof List<?> ml) {
                    for (Object mo : ml) {
                        if (!(mo instanceof Map<?, ?> mm)) continue;
                        WeightModifier wm = new WeightModifier();
                        wm.multiply = mm.get("multiply") instanceof Number n ? n.doubleValue() : (double) asInt(mm.get("multiply"), 1);
                        Object when = mm.get("when");
                        if (when instanceof Map<?, ?> cond) {
                            wm.time = asStr(cond.get("time"), wm.time);
                            wm.weather = asStr(cond.get("weather"), wm.weather);
                            if (cond.containsKey("thundering")) wm.thundering = asBool(cond.get("thundering"), false);
                            if (cond.containsKey("raining")) wm.raining = asBool(cond.get("raining"), false);
                            if (cond.containsKey("canSeeSky")) wm.canSeeSky = asBool(cond.get("canSeeSky"), false);
                            wm.positionType = asStr(cond.get("positionType"), asStr(cond.get("position"), null));
                            Object wbt = cond.get("biomeTags");
                            if (wbt instanceof List<?> bl2) {
                                for (Object o : bl2) {
                                    String s = asStr(o, null);
                                    if (s != null) wm.biomeTags.add(BiomeTagService.normalize(s));
                                }
                            }
                        }
                        e.weightModifiers.add(wm);
                    }
                }

                // position -> waterOnly
                String pos = e.position == null ? "land" : e.position.toLowerCase(Locale.ROOT);
                e.waterOnly = pos.equals("water") || pos.equals("water_surface");

                // cave position implies low skylight
                if (pos.equals("cave")) {
                    e.maxSkyLight = Math.min(e.maxSkyLight, 1);
                }

                any.add(e);
            } catch (Throwable ignored) {}
        }
    }

    private List<Entry> parseList(List<Map<?, ?>> list) {
        List<Entry> out = new ArrayList<>();
        if (list == null) return out;

        // Species that should always spawn in water (covers Cobblemon position types like submerged/seafloor).
        final Set<String> forceWater = new HashSet<>(Arrays.asList(
                "magikarp","gyarados","goldeen","seaking","tentacool","tentacruel",
                "horsea","seadra","staryu","starmie","shellder","cloyster",
                "krabby","kingler","seel","dewgong","lapras",
                "dratini","dragonair","dragonite"
        ));

        for (Map<?, ?> m : list) {
            try {
                Entry e = new Entry();
                e.species = asStr(m.get("species"), null);
                if (e.species == null) continue;
                // Allow different naming conventions (Cobblemon/Pixelmon ids, namespaces, hyphen/underscore).
                Species resolved = dex.getSpeciesFlexible(e.species);
                if (resolved == null) continue;
                // Store canonical id for later spawning.
                e.species = resolved.id();
                e.weight = asInt(m.get("weight"), 1);
                e.bucket = normalizeBucket(asStr(m.get("bucket"), "common"));
                e.position = asStr(m.get("positionType"), asStr(m.get("position"), e.position));
                e.minLevel = asInt(m.get("minLevel"), 1);
                e.maxLevel = asInt(m.get("maxLevel"), 100);
                e.time = asStr(m.get("time"), "any");
                e.weather = asStr(m.get("weather"), "any");
                e.minY = asInt(m.get("minY"), Integer.MIN_VALUE);
                e.maxY = asInt(m.get("maxY"), Integer.MAX_VALUE);
                e.waterOnly = asBool(m.get("waterOnly"), false);
                if (m.containsKey("canSeeSky")) e.canSeeSky = asBool(m.get("canSeeSky"), false);
                e.minBlockLight = asInt(m.get("minBlockLight"), Integer.MIN_VALUE);
                e.maxBlockLight = asInt(m.get("maxBlockLight"), Integer.MAX_VALUE);
                e.minSkyLight = asInt(m.get("minSkyLight"), Integer.MIN_VALUE);
                e.maxSkyLight = asInt(m.get("maxSkyLight"), Integer.MAX_VALUE);
                e.nearStructure = asStr(m.get("nearStructure"), null);
                e.nearStructureRadius = asInt(m.get("nearStructureRadius"), 128);
                e.nearStructureNegate = asBool(m.get("nearStructureNegate"), false);
                e.nearBlocksRadius = asInt(m.get("nearBlocksRadius"), 12);

                Object bio = m.get("biomes");
                if (bio instanceof List<?> bl) {
                    for (Object o : bl) {
                        String s = asStr(o, null);
                        if (s != null) e.biomes.add(s);
                    }
                }

                Object bg = m.get("biomeGroups");
                if (bg instanceof List<?> bl) {
                    for (Object o : bl) {
                        String s = asStr(o, null);
                        if (s != null) e.biomeGroups.add(s.toLowerCase(Locale.ROOT));
                    }
                }
                Object abg = m.get("antiBiomeGroups");
                if (abg instanceof List<?> bl) {
                    for (Object o : bl) {
                        String s = asStr(o, null);
                        if (s != null) e.antiBiomeGroups.add(s.toLowerCase(Locale.ROOT));
                    }
                }

                Object bt = m.get("biomeTags");
                if (bt instanceof List<?> bl) {
                    for (Object o : bl) {
                        String s = asStr(o, null);
                        if (s != null) e.biomeTags.add(BiomeTagService.normalize(s));
                    }
                }
                Object abt = m.get("antiBiomeTags");
                if (abt instanceof List<?> bl) {
                    for (Object o : bl) {
                        String s = asStr(o, null);
                        if (s != null) e.antiBiomeTags.add(BiomeTagService.normalize(s));
                    }
                }

                e.nearWater = asBool(m.get("nearWater"), e.nearWater);

                Object nb = m.get("nearBlocks");
                if (nb instanceof List<?> bl) {
                    for (Object o : bl) {
                        String s = asStr(o, null);
                        if (s != null) e.nearBlocks.add(s.toLowerCase(Locale.ROOT));
                    }
                }

                // If the entry requires water nearby and is a water-centric species, keep it in water.
                if (forceWater.contains(e.species.toLowerCase(Locale.ROOT))) {
                    e.waterOnly = true;
                }
                out.add(e);
            } catch (Throwable ignored) {}
        }
        return out;
    }


    public List<Entry> getAllEntries() {
        List<Entry> out = new ArrayList<>(any);
        for (List<Entry> list : byBiome.values()) {
            if (list != null && !list.isEmpty()) out.addAll(list);
        }
        return out;
    }

    public Entry pick(Location loc) {
        return pick(loc, null);
    }

    public List<Entry> getCandidates(ResolvedSpawnPosition pos, String desiredBucket) {
        return getCandidates(pos, desiredBucket, false);
    }

    public List<Entry> getCandidates(ResolvedSpawnPosition pos, String desiredBucket, boolean includeFishingOnly) {
        if (pos == null || pos.location == null) return java.util.Collections.emptyList();
        Set<String> banned = getBannedSpecies();
        List<Entry> pool = ruleRegistry.getCandidates(pos, desiredBucket);
        if (pool == null || pool.isEmpty()) return java.util.Collections.emptyList();
        List<Entry> candidates = new ArrayList<>();
        for (Entry e : pool) {
            if (e == null || e.species == null) continue;
            if (e.fishingOnly && !includeFishingOnly) continue;
            if (!banned.isEmpty() && banned.contains(e.species.toLowerCase(Locale.ROOT))) continue;
            if (!e.matches(pos)) continue;
            if (getEffectiveWeight(e, pos) <= 0) continue;
            candidates.add(e);
        }
        return candidates;
    }

    public int getEffectiveWeight(Entry e) {
        return getEffectiveWeight(e, null);
    }

    public int getEffectiveWeight(Entry e, ResolvedSpawnPosition pos) {
        if (e == null) return 0;
        double mul = 1.0;
        Double m = getWeightMultipliers().get(e.species.toLowerCase(Locale.ROOT));
        if (m != null) mul *= m;

        String conf = e.importConfidence == null ? "UNKNOWN" : e.importConfidence.toUpperCase(Locale.ROOT);
        switch (conf) {
            case "HIGH" -> mul *= plugin.getConfig().getDouble("spawns.confidence-multipliers.high", 1.00D);
            case "MEDIUM" -> mul *= plugin.getConfig().getDouble("spawns.confidence-multipliers.medium", 0.70D);
            case "LOW" -> mul *= plugin.getConfig().getDouble("spawns.confidence-multipliers.low", 0.28D);
            case "UNSAFE" -> mul *= plugin.getConfig().getDouble("spawns.confidence-multipliers.unsafe", 0.10D);
            default -> mul *= plugin.getConfig().getDouble("spawns.confidence-multipliers.unknown", 0.60D);
        }
        if (e.weight >= 40 && e.presetConstraints != null && !e.presetConstraints.isEmpty()) mul *= plugin.getConfig().getDouble("spawns.high-risk.special-preset-multiplier", 0.55D);
        if (e.ignoredConditions != null && !e.ignoredConditions.isEmpty()) mul *= plugin.getConfig().getDouble("spawns.high-risk.ignored-condition-multiplier", 0.70D);
        if (e.originalPresets != null && !e.originalPresets.isEmpty() && (e.presetConstraints == null || e.presetConstraints.isEmpty())) mul *= plugin.getConfig().getDouble("spawns.high-risk.missing-preset-multiplier", 0.40D);
        if (pos != null && e.weightModifiers != null) {
            for (WeightModifier wm : e.weightModifiers) {
                if (wm != null && wm.matches(pos)) mul *= Math.max(0.0, wm.multiply);
            }
        }
        return (int) Math.floor(Math.max(0, e.weight) * mul);
    }

    private Set<String> getBannedSpecies() {
        Set<String> banned = new HashSet<>();
        try {
            for (String s : plugin.getConfig().getStringList("spawns.banned-species")) {
                if (s != null && !s.isBlank()) banned.add(s.trim().toLowerCase(Locale.ROOT));
            }
        } catch (Throwable ignored) {}
        return banned;
    }

    private Map<String, Double> getWeightMultipliers() {
        Map<String, Double> mult = new HashMap<>();
        try {
            ConfigurationSection m = plugin.getConfig().getConfigurationSection("spawns.weight-multipliers");
            if (m != null) {
                for (String k : m.getKeys(false)) {
                    double v = m.getDouble(k, 1.0);
                    if (k != null && !k.isBlank()) mult.put(k.trim().toLowerCase(Locale.ROOT), v);
                }
            }
        } catch (Throwable ignored) {}
        return mult;
    }

    public Entry pick(Location loc, String desiredBucket) {
        if (loc == null) return null;

        // User/server bans (e.g. ban Ditto natural spawns)
        Set<String> banned = new HashSet<>();
        try {
            for (String s : plugin.getConfig().getStringList("spawns.banned-species")) {
                if (s != null && !s.isBlank()) banned.add(s.trim().toLowerCase(Locale.ROOT));
            }
        } catch (Throwable ignored) {}

        // Weight multipliers for balancing (e.g. starters/psuedo-legendaries)
        Map<String, Double> mult = new HashMap<>();
        try {
            ConfigurationSection m = plugin.getConfig().getConfigurationSection("spawns.weight-multipliers");
            if (m != null) {
                for (String k : m.getKeys(false)) {
                    double v = m.getDouble(k, 1.0);
                    if (k != null && !k.isBlank()) mult.put(k.trim().toLowerCase(Locale.ROOT), v);
                }
            }
        } catch (Throwable ignored) {}

        String key = null;
        try {
            Biome b = loc.getBlock().getBiome();
            key = b.getKey() != null ? b.getKey().toString() : null;
            if (key == null) key = b.name();
        } catch (Throwable ignored) {}

        // Start with exact biome key pool, then union any matching biome-tag pools (keys starting with '#').
        List<Entry> pool = new ArrayList<>();
        if (key != null) {
            List<Entry> exact = byBiome.get(key);
            if (exact == null) exact = byBiome.get(key.toLowerCase(Locale.ROOT));
            if (exact == null) exact = byBiome.get(key.toUpperCase(Locale.ROOT));
            if (exact != null) pool.addAll(exact);

            String biomeKey = key.toLowerCase(Locale.ROOT);
            for (Map.Entry<String, List<Entry>> en : byBiome.entrySet()) {
                String bk = en.getKey();
                if (bk == null) continue;
                if (!bk.startsWith("#")) continue;
                if (BiomeTagService.matches(bk, biomeKey, null)) {
                    pool.addAll(en.getValue());
                }
            }
        }
        if (pool.isEmpty()) pool = any;
        if (pool == null || pool.isEmpty()) return null;

        // Filter by conditions first
        List<Entry> candidates = new ArrayList<>();
        int total = 0;
        for (Entry e : pool) {
            if (e == null || e.species == null) continue;
            if (desiredBucket != null && !desiredBucket.isBlank()) {
                if (e.bucket == null || !e.bucket.equalsIgnoreCase(desiredBucket)) continue;
            }
            if (!banned.isEmpty() && banned.contains(e.species.toLowerCase(Locale.ROOT))) continue;
            if (!e.matches(loc)) continue;

            double mul = 1.0;
            Double m = mult.get(e.species.toLowerCase(Locale.ROOT));
            if (m != null) mul *= m;
            int w = (int) Math.floor(Math.max(0, e.weight) * mul);
            if (w <= 0) continue;
            candidates.add(e);
            total += w;
        }
        if (candidates.isEmpty() || total <= 0) return null;

        int r = Util.RND.nextInt(total);
        for (Entry e : candidates) {
            double mul = 1.0;
            Double m = mult.get(e.species.toLowerCase(Locale.ROOT));
            if (m != null) mul *= m;
            r -= (int) Math.floor(Math.max(0, e.weight) * mul);
            if (r < 0) return e;
        }
        return candidates.get(candidates.size() - 1);
    }

    private static String normalizeBucket(String b) {
        if (b == null) return "common";
        String s = b.trim().toLowerCase(Locale.ROOT);
        if (s.isBlank()) return "common";
        // common aliases
        if (s.equals("ultrarare")) return "ultra_rare";
        if (s.equals("ultra-rare")) return "ultra_rare";
        if (s.equals("ultra rare")) return "ultra_rare";
        if (s.equals("super_rare") || s.equals("super-rare")) return "ultra_rare";
        return s;
    }

    private static String asStr(Object o, String def) {
        if (o == null) return def;
        String s = String.valueOf(o).trim();
        return s.isEmpty() ? def : s;
    }
    private static int asInt(Object o, int def) {
        if (o == null) return def;
        if (o instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(o)); } catch (Exception e) { return def; }
    }
    private static boolean asBool(Object o, boolean def) {
        if (o == null) return def;
        if (o instanceof Boolean b) return b;
        String s = String.valueOf(o).trim().toLowerCase(Locale.ROOT);
        if (s.equals("true") || s.equals("yes") || s.equals("1")) return true;
        if (s.equals("false") || s.equals("no") || s.equals("0")) return false;
        return def;
    }
}
