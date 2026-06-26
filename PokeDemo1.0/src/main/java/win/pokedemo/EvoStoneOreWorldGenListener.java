package win.pokedemo;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.NoteBlock;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.Location;
import org.bukkit.Bukkit;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.ItemStack;

import java.util.Random;

/**
 * Evolution stone "ores" using NOTE_BLOCK states.
 *
 * User rules (debug-friendly / high spawn):
 * - Fire Stone: Nether surface, high chance
 * - Water Stone: Ocean biome seafloor surface, high chance
 * - Leaf Stone: On leaves surface, high chance
 * - Moon Stone: End surface, high chance
 *
 * Drops:
 * - Fortune increases quantity
 * - Silk Touch does NOT give ore block, but STILL gives the stone item
 */
public class EvoStoneOreWorldGenListener implements Listener {

    private final PokeDemoPlugin plugin;
    private final NamespacedKeys keys;
    private final Random random = new Random();

    public EvoStoneOreWorldGenListener(PokeDemoPlugin plugin) {
        this.plugin = plugin;
        this.keys = new NamespacedKeys(plugin);
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!plugin.getConfig().getBoolean("evo-ores.enabled", true)) return;

        Chunk chunk = event.getChunk();
        World w = chunk.getWorld();

        // Critical: ChunkLoadEvent fires for BOTH newly generated chunks and already-existing chunks being loaded.
        // We must never re-run ore placement on an existing chunk, otherwise ores will infinitely accumulate.
        // Use a tiny byte flag in Chunk PDC so even old chunks are "sealed" after the first load post-update.
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        try {
            if (pdc.has(keys.evoOreChunkTriedKey(), PersistentDataType.BYTE)) {
                return;
            }
            pdc.set(keys.evoOreChunkTriedKey(), PersistentDataType.BYTE, (byte) 1);
        } catch (Throwable ignored) {
            // If anything goes wrong with PDC, fall back to Paper's new-chunk indicator.
        }

        // Prefer hard guarantee: only generate on truly new chunks.
        try {
            if (!event.isNewChunk()) {
                return;
            }
        } catch (Throwable ignored) {
            // Older API may not have isNewChunk; the PDC flag above still prevents repeats.
        }

        // v7.40+: legacy cleanup.
        // Older builds recorded ore locations as a growing STRING inside Chunk PersistentDataContainer.
        // That can exceed NBT's UTF limit and cause "Failed to write NBT String ... too long" plus TPS collapse.
        // We no longer store any per-chunk ore list; remove the legacy key eagerly so existing worlds recover.
        try {
            chunk.getPersistentDataContainer().remove(keys.evoOreKey());
        } catch (Throwable ignored) {
        }

        double pFire = plugin.getConfig().getDouble("evo-ores.chance.fire", 0.10);
        double pWater = plugin.getConfig().getDouble("evo-ores.chance.water", 0.10);
        double pLeaf = plugin.getConfig().getDouble("evo-ores.chance.leaf", 0.10);
        double pMoon = plugin.getConfig().getDouble("evo-ores.chance.moon", 0.10);
        double pThunder = plugin.getConfig().getDouble("evo-ores.chance.thunder", 0.10);

        // We do placement on region/main thread.
        int bx0 = (chunk.getX() << 4);
        int bz0 = (chunk.getZ() << 4);
        FoliaCompat.runAt(plugin, w.getBlockAt(bx0, w.getMinHeight(), bz0).getLocation(), () -> {
            switch (w.getEnvironment()) {
                case NETHER -> {
                    if (random.nextDouble() < pFire) tryGenFireNetherSurface(chunk);
                }
                case THE_END -> {
                    if (random.nextDouble() < pMoon) tryGenMoonEndSurface(chunk);
                }
                case NORMAL -> {
                    if (random.nextDouble() < pWater) tryGenWaterOceanFloor(chunk);
                    if (random.nextDouble() < pLeaf) tryGenLeafOnLeaves(chunk);
                    if (random.nextDouble() < pThunder) tryGenThunderPixelmonStyle(chunk);
                }
                default -> {}
            }
        });
    }

	    private void placeOre(Block target, EvoOreType type) {
        if (target == null) return;

        // Replace target block itself
        target.setType(Material.NOTE_BLOCK, false);
        NoteBlock nb = (NoteBlock) target.getBlockData();
        nb.setInstrument(type.instrument);
        nb.setNote(type.note);
        nb.setPowered(false);
        target.setBlockData(nb, false);
        // v7.40+: do NOT record to chunk PDC (could create huge NBT strings and break chunk saving).

        // Re-apply once to reduce chance of state drift
        NoteBlock nb2 = (NoteBlock) target.getBlockData();
        nb2.setInstrument(type.instrument);
        nb2.setNote(type.note);
        nb2.setPowered(false);
        target.setBlockData(nb2, false);
    }

    // v7.40+: removed per-chunk ore recording (was causing huge NBT strings and TPS collapse).

    // Nether fire stone: pick a random xz and replace top solid surface block (netherrack/basalt/blackstone)
    private void tryGenFireNetherSurface(Chunk chunk) {
        World w = chunk.getWorld();
        for (int tries = 0; tries < 12; tries++) {
            int x = random.nextInt(16);
            int z = random.nextInt(16);
            int bx = (chunk.getX() << 4) + x;
            int bz = (chunk.getZ() << 4) + z;
            // Nether highestBlockYAt often hits the bedrock roof; scan down to find exposed surface.
            Block top = null;
            for (int yy = 120; yy > 20; yy--) {
                Block b = w.getBlockAt(bx, yy, bz);
                Material m = b.getType();
                if (m == Material.BEDROCK) continue;
                if (!(m == Material.NETHERRACK || m == Material.BASALT || m == Material.BLACKSTONE)) continue;
                if (!b.getRelative(0,1,0).getType().isAir()) continue;
                top = b;
                break;
            }
            if (top == null) continue;
            placeOre(top, EvoOreType.FIRE);
            if (plugin.getConfig().getBoolean("worldgen.debug", false)) {
                plugin.getLogger().info("[WorldGen] Placed FIRE ore at " + w.getName() + " " + bx + "," + top.getY() + "," + bz);
            }
            return;
        }
    }

    // Ocean water stone: choose biome ocean and replace seafloor top solid with water above.
    private void tryGenWaterOceanFloor(Chunk chunk) {
        World w = chunk.getWorld();
        int sea = w.getSeaLevel();
        for (int tries = 0; tries < 12; tries++) {
            int x = random.nextInt(16);
            int z = random.nextInt(16);
            int bx = (chunk.getX() << 4) + x;
            int bz = (chunk.getZ() << 4) + z;
            var biome = w.getBiome(bx, sea, bz);
            String bn = biome.getKey() != null ? biome.getKey().getKey() : biome.name().toLowerCase();
            if (!bn.contains("ocean")) continue;

            // find floor
            Block floor = null;
            for (int yy = sea; yy > sea - 50 && yy > w.getMinHeight() + 2; yy--) {
                Block b = w.getBlockAt(bx, yy, bz);
                if (b.getType().isSolid()) {
                    Block above = b.getRelative(0, 1, 0);
                    if (above.getType() == Material.WATER) floor = b;
                    break;
                }
            }
            if (floor == null) continue;
            placeOre(floor, EvoOreType.WATER);
            if (plugin.getConfig().getBoolean("worldgen.debug", false)) {
                plugin.getLogger().info("[WorldGen] Placed WATER ore at " + w.getName() + " " + bx + "," + floor.getY() + "," + bz);
            }
            return;
        }
    }

    // Leaf stone: replace a leaves block that has air above (surface leaves)
    private void tryGenLeafOnLeaves(Chunk chunk) {
        World w = chunk.getWorld();
        for (int tries = 0; tries < 20; tries++) {
            int x = random.nextInt(16);
            int z = random.nextInt(16);
            int bx = (chunk.getX() << 4) + x;
            int bz = (chunk.getZ() << 4) + z;
            int y = w.getHighestBlockYAt(bx, bz);
            // scan down a bit to find leaves
            for (int yy = y; yy > y - 20 && yy > w.getMinHeight() + 2; yy--) {
                Block b = w.getBlockAt(bx, yy, bz);
                if (b.getType().name().endsWith("_LEAVES")) {
                    if (!b.getRelative(0,1,0).getType().isAir()) continue;
                    placeOre(b, EvoOreType.LEAF);
                    if (plugin.getConfig().getBoolean("worldgen.debug", false)) {
                        plugin.getLogger().info("[WorldGen] Placed LEAF ore at " + w.getName() + " " + bx + "," + yy + "," + bz);
                    }
                    return;
                }
            }
        }
    }

    private void tryGenThunderPixelmonStyle(Chunk chunk) {
        // v7.40+: follow server rule: THUNDER ore spawns on LAND surface above Y>=110.
        World w = chunk.getWorld();

        int cx = (chunk.getX() << 4) + 8;
        int cz = (chunk.getZ() << 4) + 8;
        int cy = Math.max(w.getMinHeight() + 1, Math.min(w.getMaxHeight() - 1, w.getHighestBlockYAt(cx, cz)));
        Biome biome = w.getBiome(cx, cy, cz);

        if (plugin.getConfig().getBoolean("evo-ores.thunder.require-mountain", false) && !isMountainousBiome(biome)) {
            return;
        }

        int minSurfaceY = plugin.getConfig().getInt("evo-ores.thunder.min-surface-y", 110);
        int tries = plugin.getConfig().getInt("evo-ores.thunder.surface-tries", 24);

        int baseX = (chunk.getX() << 4);
        int baseZ = (chunk.getZ() << 4);

        for (int i = 0; i < tries; i++) {
            int bx = baseX + random.nextInt(16);
            int bz = baseZ + random.nextInt(16);
            int yTop = w.getHighestBlockYAt(bx, bz);
            if (yTop < minSurfaceY) continue;

            Block top = w.getBlockAt(bx, yTop, bz);
            Material m = top.getType();
            if (!m.isSolid()) continue;
            if (m == Material.WATER || m == Material.LAVA) continue;
            if (m.name().contains("LEAVES")) continue;

            String bn = biome.name();
            if (bn.contains("OCEAN") || bn.contains("RIVER") || bn.contains("BEACH")) continue;

            placeOre(top, EvoOreType.THUNDER);
            if (plugin.getConfig().getBoolean("worldgen.debug", false)) {
                plugin.getLogger().info("[WorldGen] Placed THUNDER ore at " + w.getName() + " " + bx + "," + yTop + "," + bz + " biome=" + biome);
            }
            return;
        }
    }

    private boolean isMountainousBiome(Biome biome) {
        String n = biome.name();
        // Approximate Pixelmon's #pixelmon:spawning/mountainous tag
        return n.contains("PEAK") || n.contains("WINDSWEPT") || n.contains("MOUNTAIN")
                || n.contains("SLOP") || n.contains("MEADOW") || n.contains("GROVE")
                || n.contains("HILL") || n.contains("HIGHLAND");
    }

    private boolean isThunderOreReplaceable(Material m) {
        // Pixelmon targets: stone + deepslate_ore_replaceables.
        if (m == Material.STONE || m == Material.DEEPSLATE) return true;
        if (m == Material.TUFF || m == Material.ANDESITE || m == Material.DIORITE || m == Material.GRANITE) return true;
        try {
            if (Tag.BASE_STONE_OVERWORLD.isTagged(m)) return true;
        } catch (Throwable ignored) {}
        return false;
    }


    // End moon stone: replace end stone on surface
    private void tryGenMoonEndSurface(Chunk chunk) {
        World w = chunk.getWorld();
        for (int tries = 0; tries < 18; tries++) {
            int x = random.nextInt(16);
            int z = random.nextInt(16);
            int bx = (chunk.getX() << 4) + x;
            int bz = (chunk.getZ() << 4) + z;

            Block top = null;
            // End has many void columns; scan downward to find surface end stone with air above.
            for (int yy = 90; yy > w.getMinHeight() + 2; yy--) {
                Block b = w.getBlockAt(bx, yy, bz);
                if (b.getType() != Material.END_STONE) continue;
                if (!b.getRelative(0, 1, 0).getType().isAir()) continue;
                top = b;
                break;
            }
            if (top == null) continue;

            placeOre(top, EvoOreType.MOON);
            if (plugin.getConfig().getBoolean("worldgen.debug", false)) {
                plugin.getLogger().info("[WorldGen] Placed MOON ore at " + w.getName() + " " + bx + "," + top.getY() + "," + bz);
            }
            return;
        }
    }

    
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block b = event.getClickedBlock();
        if (b == null || b.getType() != Material.NOTE_BLOCK) return;
        BlockData bd = b.getBlockData();
        if (!(bd instanceof NoteBlock nb)) return;
        EvoOreType t = EvoOreType.fromState(nb.getInstrument(), nb.getNote());
        if (t == null) return;
        // Prevent note block tuning from changing ore state.
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPhysics(BlockPhysicsEvent event) {
        Block b = event.getBlock();
        if (b.getType() != Material.NOTE_BLOCK) return;

        // v7.40+: We no longer record ore positions in chunk PDC.
        // If this NOTE_BLOCK is one of our evo ores (encoded by its state), cancel physics
        // so it won't revert when the supporting block changes.
        BlockData bd = b.getBlockData();
        if (bd instanceof NoteBlock nb && EvoOreType.fromState(nb) != null) {
            event.setCancelled(true);
        }
    }




    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBelowPlace(BlockPlaceEvent event) {
        Block placed = event.getBlockPlaced();
        Block above = placed.getRelative(0, 1, 0);
        if (above.getType() != Material.NOTE_BLOCK) return;

        // Building under a note-block ore causes vanilla to recompute the instrument, which can
        // break the ore's resource-pack variant (turning it into a plain note block visually).
        // Re-apply the ore block data one tick later.
        Bukkit.getScheduler().runTask(plugin, () -> repairOreBlock(above));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBelowBreak(BlockBreakEvent event) {
        Block broken = event.getBlock();
        Block above = broken.getRelative(0, 1, 0);
        if (above.getType() != Material.NOTE_BLOCK) return;
        Bukkit.getScheduler().runTask(plugin, () -> repairOreBlock(above));
    }

    private void repairOreBlock(Block ore) {
        if (ore == null || ore.getType() != Material.NOTE_BLOCK) return;
        BlockData bd = ore.getBlockData();
        if (!(bd instanceof NoteBlock nb)) return;
        EvoOreType t = EvoOreType.fromState(nb.getInstrument(), nb.getNote());
        if (t == null) return;
        nb.setInstrument(t.instrument);
        nb.setNote(t.note);
        nb.setPowered(false);
        ore.setBlockData(nb, false);
    }

@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block b = event.getBlock();
        if (b.getType() != Material.NOTE_BLOCK) return;
        BlockData bd = b.getBlockData();
        if (!(bd instanceof NoteBlock nb)) return;

        EvoOreType t = EvoOreType.fromState(nb.getInstrument(), nb.getNote());
        if (t == null) return;

        event.setDropItems(false);

        int fortune = 0;
        try {
            fortune = event.getPlayer().getInventory().getItemInMainHand()
                    .getEnchantmentLevel(org.bukkit.enchantments.Enchantment.FORTUNE);
        } catch (Throwable ignored) {}

        int qty = 1 + (fortune > 0 ? random.nextInt(fortune + 1) : 0);

        ItemStack drop = PokeDemoPlugin.INSTANCE.getItems().make(t.itemId, PokeDemoPlugin.INSTANCE.getItemRegistry());
        if (drop != null) {
            drop.setAmount(Math.max(1, Math.min(64, qty)));
            b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), drop);
        }
        // Do not give ore block itself (even with silk touch); simply remove.
        b.setType(Material.AIR, false);
        // v7.40+: no per-chunk recording.
    }

    public enum EvoOreType {
        FIRE(org.bukkit.Instrument.BANJO, new org.bukkit.Note(0), "fire_stone"),
        WATER(org.bukkit.Instrument.BIT, new org.bukkit.Note(2), "water_stone"),
        THUNDER(org.bukkit.Instrument.BELL, new org.bukkit.Note(4), "thunder_stone"),
        LEAF(org.bukkit.Instrument.FLUTE, new org.bukkit.Note(5), "leaf_stone"),
        MOON(org.bukkit.Instrument.CHIME, new org.bukkit.Note(7), "moon_stone");

        public final org.bukkit.Instrument instrument;
        public final org.bukkit.Note note;
        public final String itemId;

        EvoOreType(org.bukkit.Instrument instrument, org.bukkit.Note note, String itemId) {
            this.instrument = instrument;
            this.note = note;
            this.itemId = itemId;
        }

        public static EvoOreType fromState(org.bukkit.Instrument i, org.bukkit.Note n) {
            // Instrument can change when blocks below are placed/broken; identify by NOTE primarily.
            for (EvoOreType t : values()) {
                if (t.note.equals(n)) return t;
            }
            return null;
        }
    
        /** Convenience overload for NoteBlock blockdata. */
        public static EvoOreType fromState(org.bukkit.block.data.type.NoteBlock nb) {
            if (nb == null) return null;
            return fromState(nb.getInstrument(), nb.getNote());
        }

}
}
