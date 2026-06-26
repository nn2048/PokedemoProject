package win.pokedemo;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** In-game crafting recipe browser UI. */
public final class RecipeGui {
    private RecipeGui() {}

    public static void openCategories(Player player, boolean fromPhone) {
        PokeDemoPlugin plugin = PokeDemoPlugin.INSTANCE;
        if (plugin == null) return;
        LangManager lang = plugin.getLang();
        GuiHolder holder = new GuiHolder(GuiType.RECIPE_CATS, player.getUniqueId());
        holder.recipeOpenedFromPhone = fromPhone;

        String title = (lang != null) ? lang.ui("gui.recipes.title", "§6合成配方") : "§6合成配方";
        Inventory inv = Bukkit.createInventory(holder, 27, UtilGui.title(title));

        inv.setItem(11, UtilGui.button(Material.SNOWBALL,
                (lang != null) ? lang.ui("gui.recipes.cat.balls", "§e精灵球") : "§e精灵球",
                List.of((lang != null) ? lang.ui("gui.recipes.cat.balls.lore", "§7查看所有可合成的球") : "§7查看所有可合成的球")));
        inv.setItem(13, UtilGui.button(Material.REDSTONE,
                (lang != null) ? lang.ui("gui.recipes.cat.devices", "§d设备") : "§d设备",
                List.of((lang != null) ? lang.ui("gui.recipes.cat.devices.lore", "§7PC / 治疗机 / 图鉴 / 手机 / 钓竿") : "§7PC / 治疗机 / 图鉴 / 手机 / 钓竿")));
        inv.setItem(15, UtilGui.button(Material.CHEST,
                (lang != null) ? lang.ui("gui.recipes.cat.items", "§a道具") : "§a道具",
                List.of((lang != null) ? lang.ui("gui.recipes.cat.items.lore", "§7查看常用道具列表（无配方）") : "§7查看常用道具列表（无配方）")));

        inv.setItem(26, UtilGui.button(Material.BARRIER, (lang != null) ? lang.ui("gui.common.back", "§c返回") : "§c返回", List.of()));
        player.openInventory(inv);
    }

    public static void openList(Player player, RecipeBook.Category cat, int page, boolean fromPhone) {
        PokeDemoPlugin plugin = PokeDemoPlugin.INSTANCE;
        if (plugin == null) return;
        RecipeBook book = plugin.getRecipeBook();
        if (book == null) return;
        LangManager lang = plugin.getLang();

        GuiHolder holder = new GuiHolder(GuiType.RECIPE_LIST, player.getUniqueId());
        holder.page = Math.max(0, page);
        holder.recipeCategory = cat.name();
        holder.recipeOpenedFromPhone = fromPhone;

        String catName = (lang != null) ? lang.ui("gui.recipes.cat." + cat.name().toLowerCase(), cat.displayName) : cat.displayName;
        String titlePrefix = (lang != null) ? lang.ui("gui.recipes.list.prefix", "§6配方 - ") : "§6配方 - ";
        String title = titlePrefix + catName;
        Inventory inv = Bukkit.createInventory(holder, 54, UtilGui.title(title));

        List<RecipeBook.RecipeDef> list = book.list(cat);
        int start = holder.page * 45;
        for (int i = 0; i < 45; i++) {
            int idx = start + i;
            if (idx >= list.size()) break;
            RecipeBook.RecipeDef def = list.get(idx);
            ItemStack icon = def.output.clone();
            var meta = icon.getItemMeta();
            String recipeName = (lang != null) ? lang.ui("recipe." + def.key + ".name", def.displayName) : def.displayName;
            meta.setDisplayName("§f" + recipeName);
            if (cat == RecipeBook.Category.ITEMS) {
                // Catalog view: show description only.
                String note = (lang != null) ? lang.ui("recipe." + def.key + ".note", def.note) : def.note;
                if (note == null || note.isBlank()) meta.setLore(List.of((lang != null) ? lang.ui("common.no_desc", "§7（暂无说明）") : "§7（暂无说明）"));
                else meta.setLore(List.of("§7" + note));
            } else {
                String noRecipe = (lang != null) ? lang.ui("gui.recipes.no_recipe", "§7暂无配方") : "§7暂无配方";
                String clickView = (lang != null) ? lang.ui("gui.recipes.click_view", "§7点击查看配方") : "§7点击查看配方";
                String note = (lang != null) ? lang.ui("recipe." + def.key + ".note", def.note) : def.note;
                meta.setLore(def.grid9 == null
                        ? (note == null ? List.of(noRecipe) : List.of(noRecipe, "§8" + note))
                        : (note == null ? List.of(clickView) : List.of(clickView, "§8" + note)));
            }
            icon.setItemMeta(meta);
            inv.setItem(i, icon);
        }

        inv.setItem(45, UtilGui.button(Material.ARROW, (lang != null) ? lang.ui("gui.common.prev", "§e上一页") : "§e上一页", List.of()));
        inv.setItem(53, UtilGui.button(Material.ARROW, (lang != null) ? lang.ui("gui.common.next", "§e下一页") : "§e下一页", List.of()));
        inv.setItem(49, UtilGui.button(Material.BARRIER, (lang != null) ? lang.ui("gui.common.back", "§c返回") : "§c返回", List.of()));

        player.openInventory(inv);
    }

    public static void openView(Player player, String recipeKey, RecipeBook.Category returnCat, int returnPage, boolean fromPhone) {
        PokeDemoPlugin plugin = PokeDemoPlugin.INSTANCE;
        if (plugin == null) return;
        RecipeBook book = plugin.getRecipeBook();
        if (book == null) return;
        LangManager lang = plugin.getLang();

        RecipeBook.RecipeDef def = book.get(recipeKey);
        if (def == null) {
            player.sendMessage((lang != null)
                    ? lang.uiFmt("gui.recipes.not_found", "§c找不到该配方：{key}", java.util.Map.of("key", recipeKey))
                    : "§c找不到该配方：" + recipeKey);
            openList(player, returnCat, returnPage, fromPhone);
            return;
        }

        GuiHolder holder = new GuiHolder(GuiType.RECIPE_VIEW, player.getUniqueId());
        holder.recipeKey = recipeKey;
        holder.recipeCategory = returnCat.name();
        holder.recipeReturnPage = returnPage;
        holder.recipeOpenedFromPhone = fromPhone;

        String recipeName = (lang != null) ? lang.ui("recipe." + def.key + ".name", def.displayName) : def.displayName;
        String titlePrefix = (lang != null) ? lang.ui("gui.recipes.list.prefix", "§6配方 - ") : "§6配方 - ";
        String title = titlePrefix + recipeName;
        Inventory inv = Bukkit.createInventory(holder, 54, UtilGui.title(title));

        // Crafting grid display area (3x3): slots 10,11,12 / 19,20,21 / 28,29,30
        int[] slots = {10,11,12, 19,20,21, 28,29,30};
        if (def.grid9 != null) {
            for (int i = 0; i < 9; i++) {
                ItemStack it = def.grid9[i];
                if (it != null) inv.setItem(slots[i], it);
            }
        } else {
            // No recipe configured yet.
            inv.setItem(20, UtilGui.button(Material.PAPER,
                    (lang != null) ? lang.ui("gui.recipes.no_recipe", "§7暂无配方") : "§7暂无配方",
                    List.of((lang != null) ? lang.ui("gui.recipes.no_recipe_hint", "§8该物品暂未配置合成表") : "§8该物品暂未配置合成表")));
        }

        // Output slot
        inv.setItem(24, def.output);

        // Decor + back
        inv.setItem(49, UtilGui.button(Material.BARRIER, (lang != null) ? lang.ui("gui.common.back", "§c返回") : "§c返回", List.of()));
        player.openInventory(inv);
    }
}