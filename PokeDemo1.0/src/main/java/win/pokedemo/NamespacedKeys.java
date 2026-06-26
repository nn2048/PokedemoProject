package win.pokedemo;

import org.bukkit.NamespacedKey;

/**
 * Centralized NamespacedKeys so we never typo PDC identifiers.
 */
public class NamespacedKeys {
    private final PokeDemoPlugin plugin;

    public NamespacedKeys(PokeDemoPlugin plugin) {
        this.plugin = plugin;
    }

    public NamespacedKey pokeChestChunkTriedKey() {
        return new NamespacedKey(plugin, "poke_chest_chunk_tried");
    }

    public NamespacedKey pokeChestMarkerKey() {
        return new NamespacedKey(plugin, "poke_chest");
    }

    public NamespacedKey pokeChestLootKey() {
        return new NamespacedKey(plugin, "poke_chest_loot");
    }

    public NamespacedKey evoOreKey() {
        return new NamespacedKey(plugin, "evo_ore");
    }

    /** Chunk-level flag: whether we've already attempted evo-ore generation for this chunk. */
    public NamespacedKey evoOreChunkTriedKey() {
        return new NamespacedKey(plugin, "evo_ore_chunk_tried");
    }
}
