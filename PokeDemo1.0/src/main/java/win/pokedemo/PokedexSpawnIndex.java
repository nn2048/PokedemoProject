package win.pokedemo;

import java.util.*;

/**
 * Derived Pokédex spawn summaries for player-facing UI.
 * Summaries are built from the live SpawnTable so they stay aligned with actual server logic.
 */
public final class PokedexSpawnIndex {

    public static final class Summary {
        public final String speciesId;
        public final LinkedHashSet<String> locations = new LinkedHashSet<>();
        public final LinkedHashSet<String> times = new LinkedHashSet<>();
        public final LinkedHashSet<String> methods = new LinkedHashSet<>();
        public final LinkedHashSet<String> specials = new LinkedHashSet<>();
        public final LinkedHashSet<String> structures = new LinkedHashSet<>();
        public String rarity = null;

        Summary(String speciesId) {
            this.speciesId = speciesId;
        }

        public List<String> briefLines(LangManager lang) {
            List<String> out = new ArrayList<>();
            String line1 = joinLabel(lang,
                    ui(lang, "gui.dex.spawn.where", "常见地点"),
                    locations, 2, "gui.dex.spawn.loc.");
            if (line1 != null) out.add("§6" + line1);
            String line2 = joinLabel(lang,
                    ui(lang, "gui.dex.spawn.when", "常见时间"),
                    times, 2, "gui.dex.spawn.time.");
            if (line2 != null) out.add("§b" + line2);
            String line3 = joinLabel(lang,
                    ui(lang, "gui.dex.spawn.method", "遭遇方式"),
                    methods, 3, "gui.dex.spawn.method.");
            if (line3 != null) out.add("§d" + line3);
            if (rarity != null && !rarity.isBlank()) {
                out.add("§e" + ui(lang, "gui.dex.spawn.rarity", "稀有度") + "：§f" + rarityLabel(lang, rarity));
            }
            return out;
        }

        public List<String> detailLines(LangManager lang) {
            List<String> out = new ArrayList<>();
            addSection(out, lang, "gui.dex.spawn.where", "常见地点", locations, 5, "§6", "gui.dex.spawn.loc.");
            addSection(out, lang, "gui.dex.spawn.when", "常见时间", times, 5, "§b", "gui.dex.spawn.time.");
            addSection(out, lang, "gui.dex.spawn.method", "遭遇方式", methods, 5, "§d", "gui.dex.spawn.method.");
            addSection(out, lang, "gui.dex.spawn.structure", "特殊地点", structures, 5, "§5", "gui.dex.spawn.structure.");
            addSection(out, lang, "gui.dex.spawn.special", "额外条件", specials, 6, "§7", "gui.dex.spawn.special.");
            if (rarity != null && !rarity.isBlank()) {
                out.add("§e" + ui(lang, "gui.dex.spawn.rarity", "稀有度") + "：§f" + rarityLabel(lang, rarity));
            }
            return out;
        }

        private static void addSection(List<String> out, LangManager lang, String key, String fallback, Collection<String> values, int max, String color, String valuePrefix) {
            String joined = joinLabel(lang, ui(lang, key, fallback), values, max, valuePrefix);
            if (joined != null) out.add(color + joined);
        }

        private static String joinLabel(LangManager lang, String label, Collection<String> values, int max, String valuePrefix) {
            if (values == null || values.isEmpty()) return null;
            StringBuilder sb = new StringBuilder();
            int i = 0;
            for (String v : values) {
                if (v == null || v.isBlank()) continue;
                if (i > 0) sb.append("、");
                sb.append(localizeValue(lang, valuePrefix, v));
                i++;
                if (i >= max) break;
            }
            if (i <= 0) return null;
            return label + "：" + sb;
        }

        private static String localizeValue(LangManager lang, String prefix, String token) {
            if (token == null || token.isBlank()) return "";
            String key = prefix + token.toLowerCase(Locale.ROOT);
            String fallback = switch (prefix) {
                case "gui.dex.spawn.loc." -> switch (token) {
                    case "overworld" -> "主世界";
                    case "nether" -> "下界";
                    case "end" -> "末地";
                    case "mountain" -> "山地";
                    case "arid" -> "干旱地带";
                    case "forest" -> "森林";
                    case "jungle" -> "丛林";
                    case "swamp" -> "沼泽";
                    case "cold" -> "寒冷地区";
                    case "ocean" -> "海洋";
                    case "river_lake" -> "河流/湖泊";
                    case "cave" -> "洞穴";
                    case "urban" -> "人类聚落";
                    case "badlands" -> "恶地";
                    case "river" -> "河流";
                    case "bamboo" -> "竹林";
                    case "warm_ocean" -> "温暖海洋";
                    case "thermal" -> "地热区";
                    case "temperate" -> "温带地区";
                    case "tundra" -> "冻原";
                    case "submerged" -> "水下";
                    case "surface" -> "水面";
                    case "seafloor" -> "海底";
                    case "grounded" -> "地表";
                    case "near_water" -> "靠近水域";
                    default -> token;
                };
                case "gui.dex.spawn.time." -> switch (token) {
                    case "any" -> "全天";
                    case "day" -> "白天";
                    case "night" -> "夜晚";
                    case "dawn" -> "黎明";
                    case "dusk" -> "黄昏";
                    default -> token;
                };
                case "gui.dex.spawn.method." -> switch (token) {
                    case "natural" -> "自然刷新";
                    case "fishing" -> "钓鱼";
                    case "herd" -> "群居";
                    case "special_spot" -> "特殊地点";
                    default -> token;
                };
                case "gui.dex.spawn.structure." -> switch (token) {
                    case "village" -> "村庄附近";
                    case "mansion" -> "林地府邸附近";
                    case "outpost" -> "掠夺者前哨站附近";
                    case "monument" -> "海底神殿附近";
                    case "shipwreck" -> "沉船附近";
                    case "ocean_ruin" -> "海底遗迹附近";
                    case "ancient_city" -> "远古城市附近";
                    case "fortress" -> "下界要塞附近";
                    default -> token;
                };
                case "gui.dex.spawn.special." -> switch (token) {
                    case "slime_chunk" -> "史莱姆区块";
                    case "moon_phase" -> "特定月相";
                    case "redstone" -> "靠近红石环境";
                    case "settlement" -> "靠近人类聚落";
                    case "pc_center" -> "靠近PC/治疗机";
                    case "webs" -> "靠近蜘蛛网";
                    case "lava" -> "靠近熔岩";
                    case "illager_structures" -> "掠夺者建筑附近";
                    case "nether_structures" -> "下界结构附近";
                    case "ocean_ruins" -> "海底遗迹钓点";
                    case "derelict" -> "废弃遗迹环境";
                    case "special_rod" -> "需要特定钓竿";
                    case "special_bait" -> "需要特殊诱饵";
                    default -> token.startsWith("herd_") ? token.substring(5) : token;
                };
                default -> token;
            };
            return lang != null ? lang.ui(key, fallback) : fallback;
        }

        private static String ui(LangManager lang, String key, String fallback) {
            return lang != null ? lang.ui(key, fallback) : fallback;
        }
    }

    private final Map<String, Summary> bySpecies = new HashMap<>();

    public Summary get(String speciesId) {
        if (speciesId == null) return null;
        return bySpecies.get(speciesId.toLowerCase(Locale.ROOT));
    }

    public static PokedexSpawnIndex build(SpawnTable table) {
        PokedexSpawnIndex idx = new PokedexSpawnIndex();
        if (table == null) return idx;
        Map<String, Summary> map = idx.bySpecies;
        for (SpawnTable.Entry e : table.getAllEntries()) {
            if (e == null || e.species == null || e.species.isBlank()) continue;
            String sid = e.species.toLowerCase(Locale.ROOT);
            Summary s = map.computeIfAbsent(sid, Summary::new);
            ingest(s, e);
        }
        return idx;
    }

    private static void ingest(Summary s, SpawnTable.Entry e) {
        String bucket = e.bucket == null ? "common" : e.bucket.toLowerCase(Locale.ROOT);
        if (s.rarity == null || rarityRank(bucket) > rarityRank(s.rarity)) {
            s.rarity = bucket;
        }

        if (e.fishingOnly) s.methods.add("fishing");
        else s.methods.add("natural");
        if (e.herd || e.herdMax > 1) s.methods.add("herd");
        if (e.nearStructure != null && !e.nearStructure.isBlank()) s.methods.add("special_spot");

        addTime(s.times, e.time);
        addLocations(s.locations, e);
        addStructures(s.structures, e);
        addSpecials(s.specials, e);
    }

    private static int rarityRank(String bucket) {
        return switch (bucket == null ? "" : bucket.toLowerCase(Locale.ROOT)) {
            case "ultra_rare", "special" -> 4;
            case "rare" -> 3;
            case "uncommon" -> 2;
            default -> 1;
        };
    }

    private static void addTime(Set<String> out, String time) {
        if (time == null || time.isBlank() || "any".equalsIgnoreCase(time)) {
            out.add("any");
            return;
        }
        switch (time.toLowerCase(Locale.ROOT)) {
            case "day" -> out.add("day");
            case "night" -> out.add("night");
            case "dawn" -> out.add("dawn");
            case "dusk" -> out.add("dusk");
            default -> out.add(time);
        }
    }

    private static void addLocations(Set<String> out, SpawnTable.Entry e) {
        for (String d : e.dimensions) {
            String x = d.toLowerCase(Locale.ROOT);
            if (x.contains("nether")) out.add("nether");
            else if (x.contains("end")) out.add("end");
            else if (x.contains("overworld")) out.add("overworld");
        }
        for (String g : e.biomeGroups) {
            switch (g.toLowerCase(Locale.ROOT)) {
                case "mountain" -> out.add("mountain");
                case "arid" -> out.add("arid");
                case "forest" -> out.add("forest");
                case "jungle" -> out.add("jungle");
                case "swamp" -> out.add("swamp");
                case "cold" -> out.add("cold");
                case "ocean" -> out.add("ocean");
                case "river_lake" -> out.add("river_lake");
                case "cave" -> out.add("cave");
                case "urban" -> out.add("urban");
                case "nether" -> out.add("nether");
                case "end" -> out.add("end");
                default -> {}
            }
        }
        for (String tag : e.biomeTags) {
            String t = tag.toLowerCase(Locale.ROOT);
            if (t.contains("badlands")) out.add("badlands");
            else if (t.contains("river")) out.add("river");
            else if (t.contains("bamboo")) out.add("bamboo");
            else if (t.contains("warm_ocean")) out.add("warm_ocean");
            else if (t.contains("thermal")) out.add("thermal");
            else if (t.contains("temperate")) out.add("temperate");
            else if (t.contains("tundra")) out.add("tundra");
        }
        switch (e.normalizedPosition()) {
            case "submerged" -> out.add("submerged");
            case "surface" -> out.add("surface");
            case "seafloor" -> out.add("seafloor");
            case "cave" -> out.add("cave");
            default -> out.add("grounded");
        }
        if (e.nearWater) out.add("near_water");
    }

    private static void addStructures(Set<String> out, SpawnTable.Entry e) {
        if (e.nearStructure != null && !e.nearStructure.isBlank()) {
            String s = e.nearStructure.toUpperCase(Locale.ROOT);
            if (s.contains("VILLAGE")) out.add("village");
            else if (s.contains("MANSION")) out.add("mansion");
            else if (s.contains("OUTPOST")) out.add("outpost");
            else if (s.contains("MONUMENT")) out.add("monument");
            else if (s.contains("SHIPWRECK")) out.add("shipwreck");
            else if (s.contains("OCEAN_RUIN")) out.add("ocean_ruin");
            else if (s.contains("ANCIENT_CITY")) out.add("ancient_city");
            else if (s.contains("FORTRESS")) out.add("fortress");
            else out.add(s);
        }
    }

    private static void addSpecials(Set<String> out, SpawnTable.Entry e) {
        if (e.requireSlimeChunk != null && e.requireSlimeChunk) out.add("slime_chunk");
        if (e.moonPhases != null && !e.moonPhases.isEmpty()) out.add("moon_phase");
        if (!e.presetConstraints.isEmpty()) {
            for (String p : e.presetConstraints) {
                switch (p.toLowerCase(Locale.ROOT)) {
                    case "redstone" -> out.add("redstone");
                    case "urban", "village", "town" -> out.add("settlement");
                    case "pokemon_center", "pokecenter" -> out.add("pc_center");
                    case "webs" -> out.add("webs");
                    case "lava" -> out.add("lava");
                    case "illager_structures" -> out.add("illager_structures");
                    case "nether_structures" -> out.add("nether_structures");
                    case "ocean_ruins", "ocean_ruin" -> out.add("ocean_ruins");
                    case "derelict" -> out.add("derelict");
                    default -> { }
                }
            }
        }
        if (!e.fishingRodTypes.isEmpty()) out.add("special_rod");
        if (!e.fishingBaits.isEmpty()) out.add("special_bait");
        if (e.herd || e.herdMax > 1) {
            out.add("herd_" + e.herdMin + "~" + e.herdMax);
        }
    }

    public static String rarityLabel(LangManager lang, String bucket) {
        if (bucket == null || bucket.isBlank()) return "常见";
        return switch (bucket.toLowerCase(Locale.ROOT)) {
            case "uncommon" -> lang != null ? lang.ui("gui.dex.spawn.rarity.uncommon", "少见") : "少见";
            case "rare" -> lang != null ? lang.ui("gui.dex.spawn.rarity.rare", "稀有") : "稀有";
            case "ultra_rare", "special" -> lang != null ? lang.ui("gui.dex.spawn.rarity.ultra", "极稀有") : "极稀有";
            default -> lang != null ? lang.ui("gui.dex.spawn.rarity.common", "常见") : "常见";
        };
    }
}
