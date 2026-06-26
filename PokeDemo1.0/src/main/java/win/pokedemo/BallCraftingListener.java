package win.pokedemo;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Makes ball crafting work even when Bukkit's {@link org.bukkit.inventory.RecipeChoice.ExactChoice}
 * becomes too strict across versions / meta.
 *
 * We re-check the 3x3 matrix against the Cobblemon recipe json and set the result.
 */
public class BallCraftingListener implements Listener {
    private final PokeDemoPlugin plugin;
    private final List<BallRecipeDef> recipes;

    public BallCraftingListener(PokeDemoPlugin plugin, List<BallRecipeDef> recipes) {
        this.plugin = plugin;
        this.recipes = recipes;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPrepare(PrepareItemCraftEvent e) {
        if (!(e.getInventory() instanceof CraftingInventory inv)) return;
        ItemStack[] matrix = inv.getMatrix();
        if (matrix == null || matrix.length < 9) return;

        // Only handle when the matrix looks like a ball recipe could match;
        // otherwise don't override other recipes.
        BallRecipeDef matched = null;
        for (BallRecipeDef r : recipes) {
            if (matches(r, matrix)) {
                matched = r;
                break;
            }
        }
        if (matched == null) return;

        ItemDef outDef = plugin.getItemRegistry().getById(matched.resultId);
        if (outDef == null) return;
        inv.setResult(plugin.getItems().createItem(outDef, plugin.getLang(), matched.resultCount));
    }

    private boolean matches(BallRecipeDef r, ItemStack[] matrix) {
        if (r.pattern.length != 3) return false;
        for (int row = 0; row < 3; row++) {
            String line = r.pattern[row];
            if (line == null || line.length() != 3) return false;
            for (int col = 0; col < 3; col++) {
                char c = line.charAt(col);
                ItemStack in = matrix[row * 3 + col];

                if (c == ' ') {
                    if (in != null && in.getType() != Material.AIR) return false;
                    continue;
                }

                BallRecipeDef.Ingredient ing = r.key.get(c);
                if (ing == null) return false;
                if (in == null || in.getType() == Material.AIR) return false;

                // 1) vanilla
                if (!ing.vanilla.isEmpty() && ing.vanilla.contains(in.getType())) continue;

                // 2) custom by PDC id
                String id = plugin.getItems().getItemId(in);
                if (id != null && !ing.customIds.isEmpty() && ing.customIds.contains(id)) continue;

                return false;
            }
        }
        return true;
    }
}
