
package win.pokedemo;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public class ItemFactory {
    public final NamespacedKey KEY_CONTROL_WAND;

    private final JavaPlugin plugin;
    public final NamespacedKey KEY_BALL_TYPE;
    public final NamespacedKey KEY_ITEM_ID;

    public ItemFactory(JavaPlugin plugin) {
        this.plugin = plugin;
        this.KEY_BALL_TYPE = new NamespacedKey(plugin, "ball_type");
        this.KEY_CONTROL_WAND = new NamespacedKey(plugin, "control_wand");
        this.KEY_ITEM_ID = new NamespacedKey(plugin, "item_id");
    }

    /** Create a PokeDemo item (medicine/status/revive/TM etc.) using a vanilla carrier item + CMD + PDC id. */
    public ItemStack createItem(ItemDef def, LangManager lang, int amount) {
        if (def == null) return null;
        ItemStack it = new ItemStack(def.carrier, Math.max(1, amount));
        ItemMeta meta = it.getItemMeta();
        String name;
        // Special display for TM/HM: show number + move name (and avoid weird vanilla disc subtitle by using PAPER carrier).
        if (def.type == ItemType.TM) {
            Integer no = null;
            boolean isHm = false;
            String forcedPrefix = null;
            if (def.data != null) {
                Object n = def.data.get("tm_no");
                if (n instanceof Number nn) no = nn.intValue();
                Object hm = def.data.get("is_hm");
                if (hm instanceof Boolean b) isHm = b;
                Object mp = def.data.get("machine_prefix");
                if (mp != null) forcedPrefix = String.valueOf(mp);
            }
            String prefix = forcedPrefix != null && !forcedPrefix.isBlank() ? forcedPrefix : (isHm ? "HM" : "TM");
            String moveId = null;
            String moveName = "";
            if (plugin instanceof PokeDemoPlugin pd) {
                TmManager tm = pd.getTmManager();
                moveId = tm == null ? null : tm.moveForTm(def.id);
                if (lang != null && moveId != null) moveName = lang.move(moveId, null);
            }
            if (no != null) {
                name = "§d" + prefix + String.format(java.util.Locale.ROOT, "%02d", no) + (moveName.isBlank() ? "" : " §f" + moveName);
            } else if (moveId != null && !moveId.isBlank()) {
                name = "§d" + prefix + " §f" + (moveName == null || moveName.isBlank() ? moveId : moveName);
            } else {
                name = "§d" + prefix + " §f" + def.id;
            }
        } else {
            name = (lang != null) ? lang.item(def.id, def.id) : def.id;
            name = "§f" + name;
        }

        meta.setDisplayName(name);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        if (def.customModelData > 0) meta.setCustomModelData(def.customModelData);
        meta.getPersistentDataContainer().set(KEY_ITEM_ID, PersistentDataType.STRING, def.id);
        // Simple lore for now; later we can show price/usage.
        meta.setLore(List.of("§8右键使用"));
        it.setItemMeta(meta);
        return it;
    }

    public String getItemId(ItemStack it) {
        if (it == null || it.getType() == Material.AIR) return null;
        if (!it.hasItemMeta()) return null;
        return it.getItemMeta().getPersistentDataContainer().get(KEY_ITEM_ID, PersistentDataType.STRING);
    }

    /**
     * Ensure an item uses the expected CustomModelData.
     * Useful for migrating older items whose PDC id is correct but CMD has changed,
     * which would otherwise show the wrong texture/model (e.g. TM icon).
     */
    public void ensureModelData(ItemStack it, int expectedCmd) {
        if (it == null || expectedCmd <= 0) return;
        if (!it.hasItemMeta()) return;
        ItemMeta meta = it.getItemMeta();
        Integer cur = meta.hasCustomModelData() ? meta.getCustomModelData() : null;
        if (cur != null && cur == expectedCmd) return;
        meta.setCustomModelData(expectedCmd);
        it.setItemMeta(meta);
    }

    public ItemStack createBall(String type, int amount) {
        Material mat = Material.matchMaterial(plugin.getConfig().getString("capture.ball-material","SNOWBALL"));
        if (mat == null) mat = Material.SNOWBALL;
        ItemStack it = new ItemStack(mat, Math.max(1, amount));
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName("§f" + pretty(type));
        meta.setLore(List.of("§7右键野生精灵", "§7尝试捕捉（演示）"));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        int cmd = plugin.getConfig().getInt("capture.ball-custom-model-data", 9001);
        meta.setCustomModelData(cmd);
        meta.getPersistentDataContainer().set(KEY_BALL_TYPE, PersistentDataType.STRING, type.toUpperCase());
        it.setItemMeta(meta);
        return it;
    }

    public String getBallType(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;
        return it.getItemMeta().getPersistentDataContainer().get(KEY_BALL_TYPE, PersistentDataType.STRING);
    }


public ItemStack createControlWand() {
    Material mat = Material.matchMaterial(plugin.getConfig().getString("control-wand.item.material", "STICK"));
    if (mat == null) mat = Material.STICK;

    ItemStack it = new ItemStack(mat, 1);
    ItemMeta meta = it.getItemMeta();
    meta.setDisplayName("§d精灵调试魔杖");
    meta.setLore(List.of(
            "§7对着精灵：§f左键缩小 §8/ §f右键放大",
            "§7潜行时：§f左键降低 §8/ §f右键抬高",
            "§7会自动写入配置并热更新（按精灵种类）"
    ));
    meta.addEnchant(Enchantment.UNBREAKING, 1, true);
    meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
    meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

    int cmd = plugin.getConfig().getInt("control-wand.item.custom-model-data", 0);
    if (cmd > 0) meta.setCustomModelData(cmd);

    meta.getPersistentDataContainer().set(KEY_CONTROL_WAND, PersistentDataType.INTEGER, 1);
    it.setItemMeta(meta);
    return it;
}

public boolean isControlWand(ItemStack it) {
    if (it == null || it.getType() == Material.AIR) return false;
    if (!it.hasItemMeta()) return false;
    Integer v = it.getItemMeta().getPersistentDataContainer().get(KEY_CONTROL_WAND, PersistentDataType.INTEGER);
    return v != null && v == 1;
}

    private String pretty(String type) {
        return switch (type.toUpperCase()) {
            case "POKE_BALL" -> "精灵球";
            case "GREAT_BALL" -> "超级球";
            case "ULTRA_BALL" -> "高级球";
            default -> type.replace("_", " ");
        };
    }

    /** Create any registered custom item by id (used by drops/plant interactions). */
    public ItemStack make(String id, ItemRegistry registry) {
        if (id == null || registry == null) return null;
        ItemDef def = registry.get(id);
        if (def == null) return null;
        LangManager lang = (plugin instanceof PokeDemoPlugin pd) ? pd.getLang() : null;
        return createItem(def, lang, 1);
    }

    /** Identify a custom item and return its ItemDef (null if not ours). */
    public ItemDef identify(ItemStack stack, ItemRegistry registry) {
        if (stack == null || registry == null) return null;
        String id = getItemId(stack);
        if (id == null) return null;
        return registry.get(id);
    }
}
