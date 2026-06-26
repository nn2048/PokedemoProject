package win.pokedemo;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Registers Cobblemon-style ball recipes (json) into Bukkit so the crafting table works.
 *
 * We keep the parser intentionally small: only what we need for ball recipes.
 * Files live in: resources/cobblemon/recipe/*.json and are listed in index.txt.
 */
public final class CobblemonBallRecipeRegistrar {
    private static final Gson GSON = new Gson();

    private CobblemonBallRecipeRegistrar() {}

    public static int registerAll(PokeDemoPlugin plugin) {
        int count = 0;
        try {
            List<String> files = readIndex(plugin, "cobblemon/recipe/index.txt");
            for (String file : files) {
                if (file == null || file.isBlank()) continue;
                String path = "cobblemon/recipe/" + file.trim();
                if (!path.endsWith(".json")) path += ".json";
                try {
                    if (registerOne(plugin, path)) count++;
                } catch (Exception ex) {
                    plugin.getLogger().warning("Failed to register ball recipe " + path + ": " + ex.getMessage());
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Ball recipe registration failed: " + e.getMessage());
        }
        return count;
    }

    /**
     * Load all ball recipes into memory (for {@link BallCraftingListener}).
     * We still keep {@link #registerAll(PokeDemoPlugin)} for Bukkit recipe book support,
     * but actual crafting output is driven by our listener for reliability.
     */
    public static List<BallRecipeDef> loadAll(PokeDemoPlugin plugin) {
        List<BallRecipeDef> out = new ArrayList<>();
        try {
            List<String> files = readIndex(plugin, "cobblemon/recipe/index.txt");
            for (String file : files) {
                if (file == null || file.isBlank()) continue;
                String path = "cobblemon/recipe/" + file.trim();
                if (!path.endsWith(".json")) path += ".json";
                BallRecipeDef def = loadOne(plugin, path);
                if (def != null) out.add(def);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Ball recipe load failed: " + e.getMessage());
        }
        return out;
    }

    private static BallRecipeDef loadOne(PokeDemoPlugin plugin, String resourcePath) throws Exception {
        InputStream in = plugin.getResource(resourcePath);
        if (in == null) return null;
        JsonObject root;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            root = GSON.fromJson(br, JsonObject.class);
        }
        if (root == null) return null;
        if (!root.has("pattern") || !root.has("key") || !root.has("result")) return null;

        String[] pattern = new String[3];
        int i = 0;
        for (JsonElement el : root.getAsJsonArray("pattern")) {
            if (i >= 3) break;
            String line = el.getAsString();
            if (line.length() != 3) return null;
            pattern[i++] = line;
        }
        if (i != 3) return null;

        JsonObject result = root.getAsJsonObject("result");
        String resultId = result.has("id") ? result.get("id").getAsString() : null;
        if (resultId == null || !resultId.contains(":")) return null;
        String internalId = resultId.substring(resultId.indexOf(':') + 1);
        int resultCount = result.has("count") ? result.get("count").getAsInt() : 1;

        // Only keep recipes whose output exists in our registry.
        if (plugin.getItemRegistry().getById(internalId) == null) return null;

        JsonObject keys = root.getAsJsonObject("key");
        Map<Character, BallRecipeDef.Ingredient> key = new HashMap<>();
        for (Map.Entry<String, JsonElement> e : keys.entrySet()) {
            String k = e.getKey();
            if (k == null || k.length() != 1) continue;
            char symbol = k.charAt(0);
            BallRecipeDef.Ingredient ing = parseIngredient(plugin, e.getValue().getAsJsonObject());
            if (ing != null) key.put(symbol, ing);
        }
        return new BallRecipeDef(pattern, key, internalId, resultCount);
    }

    private static boolean registerOne(PokeDemoPlugin plugin, String resourcePath) throws Exception {
        InputStream in = plugin.getResource(resourcePath);
        if (in == null) return false;
        JsonObject root;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            root = GSON.fromJson(br, JsonObject.class);
        }
        if (root == null) return false;

        // Only shaped crafting is used by these recipes.
        if (!root.has("pattern") || !root.has("key") || !root.has("result")) return false;

        List<String> pattern = new ArrayList<>();
        for (JsonElement el : root.getAsJsonArray("pattern")) {
            pattern.add(el.getAsString());
        }
        if (pattern.isEmpty()) return false;

        JsonObject result = root.getAsJsonObject("result");
        String resultId = result.has("id") ? result.get("id").getAsString() : null;
        if (resultId == null || !resultId.contains(":")) return false;
        // cobblemon:poke_ball -> poke_ball
        String internalId = resultId.substring(resultId.indexOf(':') + 1);
        int resultCount = result.has("count") ? result.get("count").getAsInt() : 1;

        ItemDef def = plugin.getItemRegistry().getById(internalId);
        if (def == null) {
            // Not implemented in our registry yet.
            return false;
        }
        ItemStack out = plugin.getItems().createItem(def, plugin.getLang(), Math.max(1, resultCount));

        NamespacedKey key = new NamespacedKey(plugin, "ball_" + internalId);
        ShapedRecipe recipe = new ShapedRecipe(key, out);
        recipe.shape(pattern.toArray(new String[0]));

        JsonObject keys = root.getAsJsonObject("key");
        for (Map.Entry<String, JsonElement> e : keys.entrySet()) {
            String k = e.getKey();
            if (k == null || k.length() != 1) continue;
            char symbol = k.charAt(0);
            RecipeChoice choice = parseChoice(plugin, e.getValue().getAsJsonObject());
            if (choice != null) {
                recipe.setIngredient(symbol, choice);
            }
        }

        // Replace old recipe with the same key if exists.
        Bukkit.removeRecipe(key);
        Bukkit.addRecipe(recipe);
        return true;
    }

    private static RecipeChoice parseChoice(PokeDemoPlugin plugin, JsonObject obj) {
        if (obj == null) return null;
        if (obj.has("item")) {
            String id = obj.get("item").getAsString();
            if (id == null) return null;
            if (id.startsWith("minecraft:")) {
                Material m = Material.matchMaterial(id.substring("minecraft:".length()).toUpperCase(Locale.ROOT));
                return m == null ? null : new RecipeChoice.MaterialChoice(m);
            }
            if (id.startsWith("cobblemon:")) {
                String internal = id.substring("cobblemon:".length());
                ItemDef def = plugin.getItemRegistry().getById(internal);
                if (def == null) return null;
                ItemStack it = plugin.getItems().createItem(def, plugin.getLang(), 1);
                return new RecipeChoice.ExactChoice(it);
            }
            return null;
        }
        if (obj.has("tag")) {
            String tag = obj.get("tag").getAsString();
            if (tag == null) return null;
            if (tag.startsWith("cobblemon:")) tag = tag.substring("cobblemon:".length());
            return tagChoice(plugin, tag);
        }
        return null;
    }

    private static BallRecipeDef.Ingredient parseIngredient(PokeDemoPlugin plugin, JsonObject obj) {
        if (obj == null) return null;
        if (obj.has("item")) {
            String id = obj.get("item").getAsString();
            if (id == null) return null;
            if (id.startsWith("minecraft:")) {
                Material m = Material.matchMaterial(id.substring("minecraft:".length()).toUpperCase(Locale.ROOT));
                return m == null ? null : BallRecipeDef.Ingredient.ofVanilla(m);
            }
            if (id.startsWith("cobblemon:")) {
                String internal = id.substring("cobblemon:".length());
                // Only accept if we actually have that item.
                if (plugin.getItemRegistry().getById(internal) == null) return null;
                return BallRecipeDef.Ingredient.ofCustom(internal);
            }
            return null;
        }
        if (obj.has("tag")) {
            String tag = obj.get("tag").getAsString();
            if (tag == null) return null;
            if (tag.startsWith("cobblemon:")) tag = tag.substring("cobblemon:".length());
            return tagIngredient(plugin, tag);
        }
        return null;
    }

    private static BallRecipeDef.Ingredient tagIngredient(PokeDemoPlugin plugin, String tag) {
        // Custom ids first
        Set<String> custom = new HashSet<>();
        switch (tag) {
            case "tier_1_poke_ball_materials" -> custom.add("tumblestone");
            case "tier_2_poke_ball_materials" -> custom.add("black_tumblestone");
            case "tier_3_poke_ball_materials" -> custom.add("sky_tumblestone");
            case "tier_4_poke_ball_materials" -> custom.add("tumblestone");
            default -> { }
        }
        // Vanilla fallback (lets players test even if they don't have tumblestones yet)
        Set<Material> vanilla = new HashSet<>();
        switch (tag) {
            case "tier_1_poke_ball_materials" -> vanilla.add(Material.COPPER_INGOT);
            case "tier_2_poke_ball_materials" -> vanilla.add(Material.IRON_INGOT);
            case "tier_3_poke_ball_materials" -> vanilla.add(Material.GOLD_INGOT);
            case "tier_4_poke_ball_materials" -> vanilla.add(Material.DIAMOND);
            default -> { }
        }
        if (custom.isEmpty() && vanilla.isEmpty()) return null;
        return BallRecipeDef.Ingredient.of(vanilla, custom);
    }

    private static RecipeChoice tagChoice(PokeDemoPlugin plugin, String tag) {
        // Prefer our real Cobblemon crafting components if implemented (tumblestones),
        // so the crafting table matches the recipe book and server progression.
        // Fallback to vanilla ingots if the custom item isn't present in the registry yet.
        if (plugin != null) {
            String id = switch (tag) {
                case "tier_1_poke_ball_materials" -> "tumblestone";
                case "tier_2_poke_ball_materials" -> "black_tumblestone";
                case "tier_3_poke_ball_materials" -> "sky_tumblestone";
                case "tier_4_poke_ball_materials" -> "tumblestone"; // placeholder; adjust when tier4 component is defined
                default -> null;
            };
            if (id != null) {
                ItemDef def = plugin.getItemRegistry().getById(id);
                if (def != null) {
                    ItemStack it = plugin.getItems().createItem(def, plugin.getLang(), 1);
                    return new RecipeChoice.ExactChoice(it);
                }
            }
        }

        // Fallback mapping (keeps crafting usable even if components aren't implemented yet).
        return switch (tag) {
            case "tier_1_poke_ball_materials" -> new RecipeChoice.MaterialChoice(Material.COPPER_INGOT);
            case "tier_2_poke_ball_materials" -> new RecipeChoice.MaterialChoice(Material.IRON_INGOT);
            case "tier_3_poke_ball_materials" -> new RecipeChoice.MaterialChoice(Material.GOLD_INGOT);
            case "tier_4_poke_ball_materials" -> new RecipeChoice.MaterialChoice(Material.DIAMOND);
            default -> null;
        };
    }

    private static List<String> readIndex(PokeDemoPlugin plugin, String resourcePath) throws Exception {
        InputStream in = plugin.getResource(resourcePath);
        if (in == null) return Collections.emptyList();
        List<String> out = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                out.add(line);
            }
        }
        return out;
    }
}
