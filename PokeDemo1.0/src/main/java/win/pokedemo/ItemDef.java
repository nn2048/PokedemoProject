package win.pokedemo;

import org.bukkit.Material;

import java.util.Map;

/**
 * Battle/overworld item definition for PokeDemo.
 * This is a lightweight definition used by ItemRegistry and future item handlers.
 * Note: This is NOT a Minecraft mod item. We represent items via a vanilla carrier Material + CustomModelData + PDC tag.
 */
public class ItemDef {
    public final String id;              // e.g. "potion", "tm_01"
    public final ItemType type;
    public final Material carrier;       // vanilla item used as the base stack
    public final int customModelData;    // resourcepack model selector
    public final String nameKey;         // lang key (e.g. "item.potion")
    public final boolean consumable;
    public final boolean stackable;
    public final int price;
    public final Map<String, Object> data; // effect parameters (heal, cureStatus, etc.)

    public ItemDef(String id, ItemType type, Material carrier, int customModelData, String nameKey,
                   boolean consumable, boolean stackable, int price, Map<String, Object> data) {
        this.id = id;
        this.type = type;
        this.carrier = carrier;
        this.customModelData = customModelData;
        this.nameKey = nameKey;
        this.consumable = consumable;
        this.stackable = stackable;
        this.price = price;
        this.data = data;
    }
}
