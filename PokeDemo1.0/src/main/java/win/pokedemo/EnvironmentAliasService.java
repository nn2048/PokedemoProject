package win.pokedemo;

import org.bukkit.Location;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/**
 * Plugin-side alias layer used to bridge Cobblemon datapack ids and this plugin's environment.
 */
public final class EnvironmentAliasService {
    private static final Map<String, Set<String>> BIOME_ALIASES = new HashMap<>();
    private static final Map<String, Set<String>> BIOME_EXTRA_TAGS = new HashMap<>();
    private static final Map<String, Set<String>> BLOCK_ALIASES = new HashMap<>();
    private static final Map<String, String> STRUCTURE_ALIASES = new HashMap<>();
    private static boolean initialized = false;

    private EnvironmentAliasService() {}

    public static synchronized void initialize(PokeDemoPlugin plugin) {
        if (initialized || plugin == null) return;
        initialized = true;
        BIOME_ALIASES.clear();
        BIOME_EXTRA_TAGS.clear();
        BLOCK_ALIASES.clear();
        STRUCTURE_ALIASES.clear();

        String fileName = plugin.getConfig().getString("spawns.environment-alias-file", "spawn_environment_aliases.yml");
        try {
            String text = null;
            File f = new File(plugin.getDataFolder(), fileName);
            if (f.exists()) {
                text = Files.readString(f.toPath(), StandardCharsets.UTF_8);
            } else {
                try (InputStream in = plugin.getResource("default_data/" + fileName)) {
                    if (in != null) text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
            if (text != null && !text.isBlank()) {
                parseSimpleAliasYaml(text);
                plugin.getLogger().info("[PokeDemo] Environment aliases loaded: biomeAliases=" + BIOME_ALIASES.size()
                        + ", biomeExtraTags=" + BIOME_EXTRA_TAGS.size()
                        + ", blockAliases=" + BLOCK_ALIASES.size()
                        + ", structureAliases=" + STRUCTURE_ALIASES.size());
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("[PokeDemo] Failed to load environment alias file (" + fileName + "): " + t.getMessage());
        }
    }

    private static void parseSimpleAliasYaml(String text) {
        String section = "";
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        for (String rawLine : normalized.split("\n", -1)) {
            String noComment = stripInlineComment(rawLine);
            if (noComment.isBlank()) continue;
            int indent = countLeadingSpaces(noComment);
            String line = noComment.trim();
            if (indent == 0 && line.endsWith(":")) {
                section = line.substring(0, line.length() - 1).trim();
                continue;
            }
            if (indent < 2) continue;
            int colon = findKeyValueDelimiter(line);
            if (colon < 0) continue;
            String key = line.substring(0, colon).trim();
            String valuePart = line.substring(colon + 1).trim();
            if (key.isEmpty()) continue;
            switch (section) {
                case "biomeAliases" -> addAll(BIOME_ALIASES, key, parseListValue(valuePart));
                case "biomeExtraTags" -> addAll(BIOME_EXTRA_TAGS, key, parseListValue(valuePart));
                case "blockAliases" -> addAll(BLOCK_ALIASES, key, parseListValue(valuePart));
                case "structureAliases" -> {
                    String v = unquote(valuePart);
                    if (!v.isBlank()) STRUCTURE_ALIASES.put(norm(key), v.trim());
                }
                default -> {
                }
            }
        }
    }

    private static List<String> parseListValue(String valuePart) {
        List<String> out = new ArrayList<>();
        if (valuePart == null) return out;
        String v = valuePart.trim();
        if (v.isEmpty()) return out;
        if (v.startsWith("[") && v.endsWith("]")) {
            v = v.substring(1, v.length() - 1).trim();
            if (v.isEmpty()) return out;
            for (String piece : v.split(",")) {
                String s = unquote(piece.trim());
                if (!s.isBlank()) out.add(s);
            }
            return out;
        }
        String single = unquote(v);
        if (!single.isBlank()) out.add(single);
        return out;
    }

    private static String stripInlineComment(String line) {
        StringBuilder out = new StringBuilder(line.length());
        boolean inSingle = false;
        boolean inDouble = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\'' && !inDouble) {
                inSingle = !inSingle;
            } else if (c == '"' && !inSingle) {
                inDouble = !inDouble;
            }
            if (c == '#' && !inSingle && !inDouble) break;
            out.append(c);
        }
        return out.toString().stripTrailing();
    }

    private static int countLeadingSpaces(String s) {
        int i = 0;
        while (i < s.length() && s.charAt(i) == ' ') i++;
        return i;
    }

    private static int findKeyValueDelimiter(String line) {
        if (line == null || line.isBlank()) return -1;
        boolean inSingle = false;
        boolean inDouble = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\'' && !inDouble) {
                inSingle = !inSingle;
                continue;
            }
            if (c == '"' && !inSingle) {
                inDouble = !inDouble;
                continue;
            }
            if (c != ':' || inSingle || inDouble) continue;
            char next = (i + 1) < line.length() ? line.charAt(i + 1) : '\0';
            if (next == ' ' || next == '[' || next == '\0' || next == '	') return i;
        }
        return -1;
    }

    private static String unquote(String s) {
        if (s == null) return "";
        String v = s.trim();
        if (v.length() >= 2) {
            if ((v.startsWith("\"") && v.endsWith("\"")) || (v.startsWith("'") && v.endsWith("'"))) {
                return v.substring(1, v.length() - 1);
            }
        }
        return v;
    }

    private static void addAll(Map<String, Set<String>> map, String key, List<String> values) {
        if (key == null || key.isBlank() || values == null || values.isEmpty()) return;
        Set<String> out = map.computeIfAbsent(norm(key), kk -> new LinkedHashSet<>());
        for (String s : values) {
            if (s == null || s.isBlank()) continue;
            out.add(norm(s));
        }
    }

    public static Set<String> getBiomeAliases(String biomeKey) {
        Set<String> out = new LinkedHashSet<>();
        String key = norm(biomeKey);
        if (key.isBlank()) return out;
        out.add(key);
        Set<String> direct = BIOME_ALIASES.get(key);
        if (direct != null) out.addAll(direct);
        for (Map.Entry<String, Set<String>> en : BIOME_ALIASES.entrySet()) {
            if (key.endsWith(en.getKey())) out.addAll(en.getValue());
        }
        return out;
    }

    public static void applyBiomeExtraTags(Set<String> tags, String biomeKey, Location loc, String positionType) {
        if (tags == null) return;
        Set<String> aliases = getBiomeAliases(biomeKey);
        if (aliases.isEmpty()) aliases = Set.of(norm(biomeKey));
        for (String a : aliases) {
            Set<String> extra = BIOME_EXTRA_TAGS.get(a);
            if (extra != null) {
                for (String t : extra) {
                    String n = BiomeTagService.normalize(t);
                    if (!n.isBlank()) tags.add(n);
                    if (n.startsWith("#") && n.length() > 1) tags.add(n.substring(1));
                }
            }
        }
        String pos = positionType == null ? "grounded" : positionType.trim().toLowerCase(Locale.ROOT);
        if (aliases.contains("cobblemon:ultra_space") || aliases.contains("ultra_space")) tags.add("ultra_space");
        if (aliases.contains("village") || aliases.contains("urban")) tags.add("urban");
        if (aliases.contains("pokemon_center") || aliases.contains("pc_room")) tags.add("urban");
        if (loc != null && loc.getWorld() != null) {
            try {
                if (loc.getWorld().getEnvironment() == org.bukkit.World.Environment.NETHER) tags.add("nether");
                if (loc.getWorld().getEnvironment() == org.bukkit.World.Environment.THE_END) tags.add("end");
            } catch (Throwable ignored) {
            }
        }
        if ("surface".equals(pos) || "submerged".equals(pos) || "seafloor".equals(pos)) tags.add("aquatic");
    }

    public static boolean matchesBlock(String need, Set<String> observedBlocks) {
        String n = norm(need);
        if (n.isBlank()) return false;
        if (observedBlocks == null || observedBlocks.isEmpty()) return false;
        if (observedBlocks.contains(n)) return true;
        if (n.startsWith("#") && observedBlocks.contains(n.substring(1))) return true;
        if (!n.startsWith("#") && observedBlocks.contains("#" + n)) return true;
        Set<String> aliases = BLOCK_ALIASES.get(n);
        if (aliases != null) {
            for (String a : aliases) {
                if (observedBlocks.contains(a)) return true;
            }
        }
        for (String observed : observedBlocks) {
            Set<String> rev = BLOCK_ALIASES.get(observed);
            if (rev != null && rev.contains(n)) return true;
        }
        return false;
    }

    public static Set<String> expandObservedBlock(String observed) {
        Set<String> out = new LinkedHashSet<>();
        String o = norm(observed);
        if (o.isBlank()) return out;
        out.add(o);
        Set<String> aliases = BLOCK_ALIASES.get(o);
        if (aliases != null) out.addAll(aliases);
        for (Map.Entry<String, Set<String>> en : BLOCK_ALIASES.entrySet()) {
            if (en.getValue().contains(o)) out.add(en.getKey());
        }
        return out;
    }

    public static String resolveStructure(String raw) {
        String n = norm(raw);
        if (n.isBlank()) return raw;
        String mapped = STRUCTURE_ALIASES.get(n);
        if (mapped != null && !mapped.isBlank()) return mapped;
        if (n.startsWith("#")) {
            mapped = STRUCTURE_ALIASES.get(n.substring(1));
            if (mapped != null && !mapped.isBlank()) return mapped;
        }
        if (n.contains("shipwreck")) return "SHIPWRECK";
        if (n.contains("monument")) return "OCEAN_MONUMENT";
        if (n.contains("village")) return "VILLAGE";
        if (n.contains("ocean_ruin")) return "OCEAN_RUIN";
        return raw;
    }

    private static String norm(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }
}
