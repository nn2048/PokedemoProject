package win.pokedemo;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Simple language manager.
 *
 * - Uses Cobblemon-style translation keys:
 *   cobblemon.species.<id>
 *   cobblemon.move.<id>
 *
 * - Loads a primary locale and a fallback locale from: plugins/PokeDemo/lang/lang.yml
 *   (primary default zh_cn, fallback default en_us).
 *
 * - Cobblemon dictionaries are JSON maps placed in: plugins/PokeDemo/lang/<locale>.json
 *   (you can copy Cobblemon's lang files there).
 *
 * - Plugin UI/messages can be overridden via YML in: plugins/PokeDemo/lang/<locale>.yml
 */
public class LangManager {
    private final JavaPlugin plugin;
    private final Gson gson = new Gson();

    private String primaryLocale;
    private String fallbackLocale;
    private Map<String, String> primary = new HashMap<>();
    private Map<String, String> fallback = new HashMap<>();

    /**
     * Direct access for reporting/export tools.
     * Prefer using tr()/ui() for gameplay UI.
     */
    public String rawPrimary(String key) {
        if (key == null) return null;
        return primary.get(key);
    }

    public String rawFallback(String key) {
        if (key == null) return null;
        return fallback.get(key);
    }

    public LangManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void ensureDefaultFiles() {
        try {
            Path dir = plugin.getDataFolder().toPath().resolve("lang");
            Files.createDirectories(dir);

            // Language selector (do NOT put this in config.yml; keep it independent)
            copyResourceIfAbsent("lang/lang.yml", dir.resolve("lang.yml"));

            // Minimal bundled Cobblemon dictionaries (servers can replace with full Cobblemon lang files)
            copyResourceIfAbsent("lang/zh_cn.json", dir.resolve("zh_cn.json"));
            copyResourceIfAbsent("lang/en_us.json", dir.resolve("en_us.json"));
            copyResourceIfAbsent("lang/ja_jp.json", dir.resolve("ja_jp.json"));
            copyResourceIfAbsent("lang/ko_kr.json", dir.resolve("ko_kr.json"));

            // Plugin UI/message overrides
            copyResourceIfAbsent("lang/zh_cn.yml", dir.resolve("zh_cn.yml"));
            copyResourceIfAbsent("lang/en_us.yml", dir.resolve("en_us.yml"));
            copyResourceIfAbsent("lang/ja_jp.yml", dir.resolve("ja_jp.yml"));
            copyResourceIfAbsent("lang/ko_kr.yml", dir.resolve("ko_kr.yml"));

            // Supplemental move descriptions (safe to keep separate from main lang files)
            copyResourceIfAbsent("lang/move_desc_zh_cn.yml", dir.resolve("move_desc_zh_cn.yml"));
            copyResourceIfAbsent("lang/move_desc_en_us.yml", dir.resolve("move_desc_en_us.yml"));
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to ensure lang files: " + e.getMessage());
        }
    }

    private void copyResourceIfAbsent(String resPath, Path target) {
        try {
            if (Files.exists(target)) return;
            try (InputStream in = plugin.getResource(resPath)) {
                if (in == null) return;
                Files.write(target, in.readAllBytes());
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to copy resource " + resPath + ": " + e.getMessage());
        }
    }

    public void load() {
        // Read primary/fallback from plugins/PokeDemo/lang/lang.yml
        readLangSettings();

        primary = readLocale(primaryLocale);
        fallback = readLocale(fallbackLocale);

        plugin.getLogger().info("Lang loaded. primary=" + primaryLocale + " (" + primary.size() + " keys), fallback=" + fallbackLocale + " (" + fallback.size() + " keys)");
    }

    private void readLangSettings() {
        try {
            Path file = plugin.getDataFolder().toPath().resolve("lang").resolve("lang.yml");
            org.bukkit.configuration.file.YamlConfiguration yml = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file.toFile());
            this.primaryLocale = yml.getString("primary", "zh_cn");
            this.fallbackLocale = yml.getString("fallback", "en_us");
        } catch (Exception e) {
            this.primaryLocale = "zh_cn";
            this.fallbackLocale = "en_us";
        }
    }

    public boolean setPrimaryLocale(String locale) {
        if (locale == null || locale.isBlank()) return false;
        try {
            Path file = plugin.getDataFolder().toPath().resolve("lang").resolve("lang.yml");
            org.bukkit.configuration.file.YamlConfiguration yml = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file.toFile());
            yml.set("primary", locale.toLowerCase(Locale.ROOT));
            if (yml.getString("fallback", null) == null) yml.set("fallback", "en_us");
            yml.save(file.toFile());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Map<String, String> readLocale(String locale) {
        try {
            // Merge strategy:
            // 1) load bundled defaults from jar (if present)
            // 2) overlay server-side file (if present)
            // This way old external files missing new keys won't regress UI back to English.
            Type t = new TypeToken<Map<String, String>>() {}.getType();
            Map<String, String> merged = new HashMap<>();

            // 1) bundled
            try (InputStream in = plugin.getResource("lang/" + locale + ".json")) {
                if (in != null) {
                    String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                    Map<String, String> m = gson.fromJson(json, t);
                    if (m != null) merged.putAll(m);
                }
            } catch (Exception ignore) {
                // ignore bundled failures
            }

            // 1b) bundled Cobblemon supplemental species names / Pokédex descriptions
            mergeBundledCobblemonLang(locale, merged, t);

            // 2) external
            Path file = plugin.getDataFolder().toPath().resolve("lang").resolve(locale + ".json");
            if (Files.exists(file)) {
                String json = Files.readString(file, StandardCharsets.UTF_8);
                Map<String, String> m = gson.fromJson(json, t);
                if (m != null) {
                    // Avoid regressions: many servers have old external lang files where
                    // values are still raw ids like "fresh_water" or "pp_up".
                    // For zh_cn, if the external value looks like an untranslated id, keep the bundled one.
                    boolean zh = locale.toLowerCase(Locale.ROOT).startsWith("zh");
                    for (var en : m.entrySet()) {
                        String k = en.getKey();
                        String v = en.getValue();
                        if (v == null) continue;
                        String vv = v.trim();
                        if (vv.isEmpty()) continue;
                        if (zh && looksUntranslatedId(vv)) {
                            // keep bundled
                            continue;
                        }
                        if (zh) {
                            String old = merged.get(k);
                            if (old != null) {
                                int oldC = countCjk(old);
                                int newC = countCjk(vv);
                                // If external is only partially translated (e.g. "Rowap树果"),
                                // prefer the bundled value with more Chinese characters.
                                if (oldC > newC) continue;
                            }
                        }
                        merged.put(k, vv);
                    }
                }
            }

            // 3) bundled YML overlay (shipped with plugin for UI strings)
            try (InputStream yin = plugin.getResource("lang/" + locale + ".yml")) {
                if (yin != null) {
                    org.bukkit.configuration.file.YamlConfiguration yml = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                            new java.io.InputStreamReader(yin, StandardCharsets.UTF_8)
                    );
                    for (String k : yml.getKeys(true)) {
                        if (yml.isConfigurationSection(k)) continue;
                        String v = yml.getString(k, null);
                        if (v == null) continue;
                        String vv = v.trim();
                        if (vv.isEmpty()) continue;
                        merged.put(k, vv);
                    }
                }
            } catch (Exception ignore) {
                // ignore bundled yml overlay failures
            }

            // 4) external YML overlay (plugin UI + optional overrides)
            //    This is the recommended place for server owners to edit translations.
            try {
                Path ymlFile = plugin.getDataFolder().toPath().resolve("lang").resolve(locale + ".yml");
                if (Files.exists(ymlFile)) {
                    org.bukkit.configuration.file.YamlConfiguration yml = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(ymlFile.toFile());
                    for (String k : yml.getKeys(true)) {
                        if (yml.isConfigurationSection(k)) continue;
                        String v = yml.getString(k, null);
                        if (v == null) continue;
                        String vv = v.trim();
                        if (vv.isEmpty()) continue;
                        merged.put(k, vv);
                    }
                }
            } catch (Exception ignore) {
                // ignore yml overlay failures
            }

            // 5) bundled supplemental move descriptions
            mergeSupplementalMoveDescriptions(locale, merged);

            // 6) external supplemental move descriptions
            try {
                Path extra = plugin.getDataFolder().toPath().resolve("lang").resolve("move_desc_" + locale + ".yml");
                if (Files.exists(extra)) {
                    org.bukkit.configuration.file.YamlConfiguration yml = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(extra.toFile());
                    for (String k : yml.getKeys(true)) {
                        if (yml.isConfigurationSection(k)) continue;
                        String v = yml.getString(k, null);
                        if (v == null) continue;
                        String vv = v.trim();
                        if (vv.isEmpty()) continue;
                        merged.put(k, vv);
                    }
                }
            } catch (Exception ignore) {
                // ignore supplemental move description failures
            }

            return merged;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to read locale " + locale + ": " + e.getMessage());
            return new HashMap<>();
        }
    }



    private void mergeSupplementalMoveDescriptions(String locale, Map<String, String> merged) {
        try (InputStream in = plugin.getResource("lang/move_desc_" + locale + ".yml")) {
            if (in == null) return;
            org.bukkit.configuration.file.YamlConfiguration yml = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                    new java.io.InputStreamReader(in, StandardCharsets.UTF_8)
            );
            for (String k : yml.getKeys(true)) {
                if (yml.isConfigurationSection(k)) continue;
                String v = yml.getString(k, null);
                if (v == null) continue;
                String vv = v.trim();
                if (vv.isEmpty()) continue;
                merged.put(k, vv);
            }
        } catch (Exception ignore) {
            // ignore bundled supplemental move descriptions
        }
    }
    private void mergeBundledCobblemonLang(String locale, Map<String, String> merged, Type t) {
        if (locale == null || locale.isBlank() || merged == null) return;
        try (InputStream in = plugin.getResource("default_data/cobblemon_lang_bundle.zip")) {
            if (in == null) return;
            try (java.util.zip.ZipInputStream zin = new java.util.zip.ZipInputStream(in, StandardCharsets.UTF_8)) {
                java.util.zip.ZipEntry ze;
                while ((ze = zin.getNextEntry()) != null) {
                    String name = ze.getName();
                    if (name == null) continue;
                    String base = java.nio.file.Paths.get(name).getFileName().toString();
                    if (!base.equalsIgnoreCase(locale + ".json")) continue;
                    String json = new String(zin.readAllBytes(), StandardCharsets.UTF_8);
                    Map<String, String> m = gson.fromJson(json, t);
                    if (m != null) merged.putAll(m);
                    break;
                }
            }
        } catch (Exception ignore) {
            // ignore bundled supplemental failures
        }
    }
    private static boolean looksUntranslatedId(String v) {
        // common placeholders: all-lowercase ascii with underscores/dots (e.g. fresh_water, item.pp_up)
        // or all-uppercase identifiers. If it contains any CJK char, we treat it as translated.
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            if (c >= 0x4E00 && c <= 0x9FFF) return false;
        }
        return v.matches("[A-Za-z0-9_ .:-]+") && (v.contains("_") || v.equals(v.toLowerCase(Locale.ROOT)) || v.equals(v.toUpperCase(Locale.ROOT)));
    }

    /** Count CJK Unified Ideographs in a string (rough heuristic for "how Chinese" it looks). */
    private static int countCjk(String s) {
        if (s == null || s.isEmpty()) return 0;
        int c = 0;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            // CJK Unified Ideographs + Extension A (good enough for our translation quality heuristic)
            if ((ch >= 0x4E00 && ch <= 0x9FFF) || (ch >= 0x3400 && ch <= 0x4DBF)) c++;
        }
        return c;
    }

    public String tr(String key, String fallbackText) {
        if (key == null || key.isBlank()) return fallbackText == null ? "" : fallbackText;

        // 1) direct lookup
        String v = primary.get(key);
        if (v != null && !v.isBlank()) return v;
        v = fallback.get(key);
        if (v != null && !v.isBlank()) return v;

        // 2) compatibility: some packs use namespaced keys like item.pokedemo.<id>
        //    while older plugin code used item.<id>
        if (key.startsWith("item.")) {
            String id = key.substring("item.".length());

            // 2.5) our bundled held-item list: provide Chinese names even if the lang file
            //      doesn't contain entries yet.
            String cn = HeldItemCatalog.cnNameOrNull(id);
            if (cn != null && !cn.isBlank()) return cn;

            String[] altKeys = new String[] {
                    "item.pokedemo." + id,
                    "item.cobblemon." + id,
                    "item.minecraft." + id
            };
            for (String ak : altKeys) {
                v = primary.get(ak);
                if (v != null && !v.isBlank()) return v;
                v = fallback.get(ak);
                if (v != null && !v.isBlank()) return v;
            }
        }

        return (fallbackText != null && !fallbackText.isBlank()) ? fallbackText : key;
    }


    /**
     * Translations for this plugin's own UI/messages.
     * Keys are: pokedemo.<suffix>
     */
    public String ui(String suffix, String fallbackText) {
        if (suffix == null || suffix.isBlank()) return fallbackText == null ? "" : fallbackText;
        return tr("pokedemo." + suffix, fallbackText);
    }


    /** Format a translated string using {key} placeholders. */
    public String fmt(String text, java.util.Map<String, String> vars) {
        if (text == null) return "";
        if (vars == null || vars.isEmpty()) return text;
        String out = text;
        for (var e : vars.entrySet()) {
            String k = e.getKey();
            String v = e.getValue();
            if (k == null || v == null) continue;
            out = out.replace("{" + k + "}", v);
        }
        return out;
    }

    public String uiFmt(String suffix, String fallbackText, java.util.Map<String, String> vars) {
        return fmt(ui(suffix, fallbackText), vars);
    }

    public String trFmt(String key, String fallbackText, java.util.Map<String, String> vars) {
        return fmt(tr(key, fallbackText), vars);
    }
    public String typeName(String typeId) {
        if (typeId == null || typeId.isBlank()) return "";
        String id = typeId.toLowerCase(Locale.ROOT);
        // Prefer Cobblemon-style keys if present, otherwise use our own namespace.
        String cobKey = "cobblemon.type." + id;
        String v = tr(cobKey, null);
        if (v != null && !v.equals(cobKey)) return v;
        return tr("pokedemo.type." + id, prettifyId(typeId));
    }

    public String categoryName(String categoryId) {
        if (categoryId == null || categoryId.isBlank()) return "";
        String id = categoryId.toLowerCase(Locale.ROOT);
        String cobKey = "cobblemon.move_category." + id;
        String v = tr(cobKey, null);
        if (v != null && !v.equals(cobKey)) return v;
        return tr("pokedemo.move_category." + id, prettifyId(categoryId));
    }

    public String statName(String statId) {
        if (statId == null || statId.isBlank()) return "";
        String id = statId.toLowerCase(Locale.ROOT);
        String cobKey = "cobblemon.stat." + id;
        String v = tr(cobKey, null);
        if (v != null && !v.equals(cobKey)) return v;
        return tr("pokedemo.stat." + id, prettifyId(statId));
    }

    /** Major status name (poison/burn/sleep/paralysis/freeze/toxic, etc.) */
    public String statusName(String statusId, String zhFallback) {
        if (statusId == null || statusId.isBlank()) return zhFallback == null ? "" : zhFallback;
        String id = statusId.toLowerCase(Locale.ROOT).trim();
        // Prefer Cobblemon-style keys.
        String cobKeyName = "cobblemon.status." + id + ".name";
        String v = tr(cobKeyName, null);
        if (v != null && !v.equals(cobKeyName)) return v;
        String cobKey = "cobblemon.status." + id;
        v = tr(cobKey, null);
        if (v != null && !v.equals(cobKey)) return v;
        // Our namespace
        String ourKey = "pokedemo.status." + id;
        v = tr(ourKey, null);
        if (v != null && !v.equals(ourKey)) return v;
        if (zhFallback != null && !zhFallback.isBlank()) return zhFallback;
        return prettifyId(id);
    }

    public String statusName(String statusId) {
        return statusName(statusId, statusId);
    }

    public String abilityName(String abilityId, String zhFallback) {
        if (abilityId == null || abilityId.isBlank()) return (zhFallback != null ? zhFallback : "");
        String id = abilityId.toLowerCase(java.util.Locale.ROOT).trim();
        // Prefer Cobblemon-style keys if present.
        String cobKeyName = "cobblemon.ability." + id + ".name";
        String v = tr(cobKeyName, null);
        if (v != null && !v.equals(cobKeyName)) return v;
        String cobKey = "cobblemon.ability." + id;
        v = tr(cobKey, null);
        if (v != null && !v.equals(cobKey)) return v;

        // Our namespace (optional)
        String ourKey = "pokedemo.ability." + id;
        v = tr(ourKey, null);
        if (v != null && !v.equals(ourKey)) return v;

        if (zhFallback != null && !zhFallback.isBlank()) return zhFallback;
        return prettifyId(id);
    }

    public String natureName(Nature n) {
        if (n == null) return "";
        return tr("pokedemo.nature." + n.id().toLowerCase(Locale.ROOT), n.zhName);
    }

    public String species(String speciesId, String oldStoredName) {
        if (speciesId == null || speciesId.isBlank()) return oldStoredName == null ? "" : oldStoredName;
        String base = "cobblemon.species." + speciesId.toLowerCase(Locale.ROOT);
        // Cobblemon 1.7.x uses keys like: cobblemon.species.<id>.name
        String keyName = base + ".name";
        // Use old stored display name only if it differs from id and translation missing.
        String pretty = prettifyId(speciesId);
        String translated = tr(keyName, null);
        if (translated != null && !translated.equals(keyName)) return translated;
        translated = tr(base, null);
        if (translated != null && !translated.equals(base)) return translated;
        if (oldStoredName != null && !oldStoredName.isBlank() && !oldStoredName.equalsIgnoreCase(speciesId)) return oldStoredName;
        return pretty;
    }

    public String move(String moveId, String moveNameFallback) {
        if (moveId == null || moveId.isBlank()) return moveNameFallback == null ? "" : moveNameFallback;
        String base = "cobblemon.move." + moveId.toLowerCase(Locale.ROOT);
        String keyName = base + ".name";
        String translated = tr(keyName, null);
        if (translated != null && !translated.equals(keyName)) return translated;
        translated = tr(base, null);
        if (translated != null && !translated.equals(base)) return translated;
        if (moveNameFallback != null && !moveNameFallback.isBlank()) return moveNameFallback;
        return prettifyId(moveId);
    }

    // Compatibility aliases used throughout the codebase
    public String moveName(String moveId) {
        return move(moveId, moveId);
    }

    public String moveName(String moveId, String fallback) {
        return move(moveId, fallback);
    }

    public String prettifyId(String id) {
        if (id == null) return "";
        String s = id.trim().replace('_', ' ').replace('-', ' ');
        if (s.isEmpty()) return s;
        String[] parts = s.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1).toLowerCase(Locale.ROOT)).append(' ');
        }
        return sb.toString().trim();
    }

    public String item(String keyOrId, String fallback) {
        if (keyOrId == null || keyOrId.isBlank()) return fallback == null ? "" : fallback;
        String key = keyOrId.contains(".") ? keyOrId : ("item." + keyOrId);
        if (!keyOrId.contains(".")) {
            String keyName = key + ".name";
            String tName = tr(keyName, null);
            if (tName != null && !tName.equals(keyName)) return tName;
        }
        String translated = tr(key, null);
        if (translated != null && !translated.equals(key)) return translated;
        return fallback != null ? fallback : prettifyId(keyOrId);
    }

    public String itemDesc(String id, String fallback) {
        if (id == null || id.isBlank()) return fallback == null ? "" : fallback;
        String key = "item." + id + ".desc";
        String translated = tr(key, null);
        if (translated != null && !translated.equals(key)) return translated;
        return fallback == null ? "" : fallback;
    }


    public String getPrimaryLocale() {
        return primaryLocale;
    }

    public String getFallbackLocale() {
        return fallbackLocale;
    }
}
