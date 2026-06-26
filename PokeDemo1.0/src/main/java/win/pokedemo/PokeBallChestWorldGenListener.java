package win.pokedemo;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.TileState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Random;

/**
 * Robust world-gen placement of Poké Ball loot chests.
 *
 * Design:
 * - Tries on every chunk load (new or existing) but marks the chunk so it only attempts once.
 * - Uses Folia region scheduler when available.
 * - Places TRAPPED_CHEST on solid surface (overworld only).
 */
public class PokeBallChestWorldGenListener implements Listener {

    private final PokeDemoPlugin plugin;
    private final ItemFactory items;
    private final ItemRegistry itemRegistry;
    private final NamespacedKeys keys;
    private final Random random = new Random();

    public PokeBallChestWorldGenListener(PokeDemoPlugin plugin, ItemFactory items, ItemRegistry itemRegistry) {
        this.plugin = plugin;
        this.items = items;
        this.itemRegistry = itemRegistry;
        this.keys = new NamespacedKeys(plugin);
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!plugin.getConfig().getBoolean("loot-chests.enabled", true)) return;

        Chunk chunk = event.getChunk();
        World world = chunk.getWorld();

        // Overworld only
        if (world.getEnvironment() != World.Environment.NORMAL) return;

        // once-per-chunk attempt marker (prevents repeated generation on chunk reloads)
        try {
            PersistentDataContainer cPdc = chunk.getPersistentDataContainer();
            if (cPdc.has(keys.pokeChestChunkTriedKey(), PersistentDataType.BYTE)) return;
            cPdc.set(keys.pokeChestChunkTriedKey(), PersistentDataType.BYTE, (byte) 1);
        } catch (Throwable ignored) {
            // If chunk PDC isn't available, we still try. (Worst case: more chests.)
        }

        double chance = plugin.getConfig().getDouble("loot-chests.spawn-chance", 0.05) / 5.0; // nerfed to 1/5
        if (chance <= 0) return;
        if (random.nextDouble() > chance) return;

        // pick random xz in chunk
        int bx = (chunk.getX() << 4) + random.nextInt(16);
        int bz = (chunk.getZ() << 4) + random.nextInt(16);

        // Run placement on region/main thread
        FoliaCompat.runAt(plugin, world.getBlockAt(bx, world.getMinHeight(), bz).getLocation(), () -> {
            // Find a land surface block (ignore water surface / shoreline).
            int y = world.getHighestBlockYAt(bx, bz);
            Block ground = null;
            for (int yy = y; yy > world.getMinHeight() + 2; yy--) {
                Block b = world.getBlockAt(bx, yy, bz);
                Material mt = b.getType();
                if (mt == Material.WATER || mt == Material.LAVA) continue;
                if (!mt.isSolid()) continue;
                // Require air above (not water), so we don't place in water.
                Block up = b.getRelative(BlockFace.UP);
                if (up.getType() != Material.AIR) continue;
                ground = b;
                break;
            }
            if (ground == null) return;
            Block place = ground.getRelative(BlockFace.UP);

            // Avoid placing inside leaves
            if (ground.getType().name().endsWith("_LEAVES")) return;

            place.setType(Material.TRAPPED_CHEST, false);

            var st = place.getState();
            if (!(st instanceof TileState tile)) return;

            PersistentDataContainer pdc = tile.getPersistentDataContainer();
            // Only initialize loot if it's not already our chest
            if (pdc.has(keys.pokeChestLootKey(), PersistentDataType.BYTE_ARRAY)) return;

            var loot = PokeBallLootTables.generateLoot(plugin, items, itemRegistry);
            byte[] bytes = PokeChestStorage.serializeItemStacks(loot);
            pdc.set(keys.pokeChestLootKey(), PersistentDataType.BYTE_ARRAY, bytes);
            pdc.set(keys.pokeChestMarkerKey(), PersistentDataType.BYTE, (byte) 1);
            tile.update(true, false);

            if (plugin.getConfig().getBoolean("worldgen.debug", false)) {
                plugin.getLogger().info("[WorldGen] Placed Pokeball chest at " + world.getName() + " " + bx + "," + place.getY() + "," + bz);
            }
        });
    }
}
