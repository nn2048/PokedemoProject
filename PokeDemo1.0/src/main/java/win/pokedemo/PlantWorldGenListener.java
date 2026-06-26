package win.pokedemo;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkPopulateEvent;

import java.util.List;
import java.util.Random;

/**
 * Pixelmon-like natural spawns for apricorn trees and berry bushes.
 * Runs ONLY on newly-generated chunks.
 */
public class PlantWorldGenListener implements Listener {

    private final PokeDemoPlugin plugin;
    private final PlantManager plants;
    private final Random rng = new Random();

    // Overworld only (user chose A)
    private static final String TARGET_WORLD = "world";

    // Basic tuning (safe defaults)
    private static final double APRICORN_TREE_CHANCE_PER_CHUNK = 0.03; // ~3% chunks
    private static final int BERRY_BUSH_ATTEMPTS_PER_CHUNK = 3;
    private static final double BERRY_BUSH_CHANCE_PER_ATTEMPT = 0.35; // up to ~1 bush avg

    // Berry pool: common + utility (Pixelmon-ish early game)
    private static final List<String> COMMON_BERRIES = List.of(
            "cheri","chesto","pecha","rawst","aspear","persim",
            "oran","sitrus","leppa"
    );

    public PlantWorldGenListener(PokeDemoPlugin plugin, PlantManager plants) {
        this.plugin = plugin;
        this.plants = plants;
    }

    @EventHandler
    public void onPopulate(ChunkPopulateEvent e) {
        World w = e.getWorld();
        if (!TARGET_WORLD.equalsIgnoreCase(w.getName())) return;

        Chunk c = e.getChunk();

        // 1) Apricorn tree (brown-ish trunk + leaves + hanging fruits)
        if (rng.nextDouble() < APRICORN_TREE_CHANCE_PER_CHUNK) {
            trySpawnApricornTree(w, c);
        }

        // 2) Berry bushes on grass
        for (int i=0;i<BERRY_BUSH_ATTEMPTS_PER_CHUNK;i++) {
            if (rng.nextDouble() > BERRY_BUSH_CHANCE_PER_ATTEMPT) continue;
            trySpawnBerryBush(w, c);
        }
    }

    private void trySpawnBerryBush(World w, Chunk c) {
        int x = (c.getX() << 4) + rng.nextInt(16);
        int z = (c.getZ() << 4) + rng.nextInt(16);
        int y = w.getHighestBlockYAt(x, z);
        if (y <= w.getMinHeight()) return;

        Block ground = w.getBlockAt(x, y - 1, z);
        if (ground.getType() != Material.GRASS_BLOCK && ground.getType() != Material.DIRT && ground.getType() != Material.COARSE_DIRT) return;
        Block air = w.getBlockAt(x, y, z);
        if (air.getType() != Material.AIR) return;

        String berry = COMMON_BERRIES.get(rng.nextInt(COMMON_BERRIES.size()));
        // stage 0 = sprout
        plants.placeWildPlant(air.getLocation(), PlantManager.PlantKind.BERRY, berry, 0);
    }

    private void trySpawnApricornTree(World w, Chunk c) {
        int x = (c.getX() << 4) + rng.nextInt(16);
        int z = (c.getZ() << 4) + rng.nextInt(16);
        int yTop = w.getHighestBlockYAt(x, z);
        int y = yTop - 1;
        if (y <= w.getMinHeight()+2) return;

        Block ground = w.getBlockAt(x, y, z);
        if (ground.getType() != Material.GRASS_BLOCK && !ground.getType().name().endsWith("_DIRT")) return;

        // ensure space
        int trunkH = 5 + rng.nextInt(2);
        for (int dy=1;dy<=trunkH+2;dy++) {
            Block b = w.getBlockAt(x, y+dy, z);
            if (b.getType() != Material.AIR) return;
        }

        // trunk: dark oak looks "brown" enough without adding new wood types
        for (int dy=1;dy<=trunkH;dy++) {
            w.getBlockAt(x, y+dy, z).setType(Material.DARK_OAK_LOG, false);
        }

        // leaves blob
        int leafY0 = y + trunkH - 1;
        for (int dy=0;dy<=3;dy++) {
            int radius = (dy==0?1:(dy==1?2:(dy==2?2:1)));
            int yy = leafY0 + dy;
            for (int dx=-radius;dx<=radius;dx++) {
                for (int dz=-radius;dz<=radius;dz++) {
                    if (Math.abs(dx)+Math.abs(dz) > radius+1) continue;
                    Block b = w.getBlockAt(x+dx, yy, z+dz);
                    if (b.getType() == Material.AIR) b.setType(Material.OAK_LEAVES, false);
                }
            }
        }

        // hanging fruits around leaves (use mature stage = 3)
        int fruits = 3 + rng.nextInt(5); // 3~7
        for (int i=0;i<fruits;i++) {
            int fx = x + rng.nextInt(5) - 2;
            int fz = z + rng.nextInt(5) - 2;
            int fy = leafY0 + 1 + rng.nextInt(3);
            Block leaf = w.getBlockAt(fx, fy, fz);
            if (leaf.getType() != Material.OAK_LEAVES) continue;
            Block spot = w.getBlockAt(fx, fy-1, fz);
            if (spot.getType() != Material.AIR) continue;
            String color = weightedApricornColor();
            plants.placeWildPlant(spot.getLocation(), PlantManager.PlantKind.APRICORN, color, 3);
        }
    }

    private String weightedApricornColor() {
        // weights: red/blue/yellow (3), green/pink (2), black/white (1)
        int roll = rng.nextInt(3+3+3 + 2+2 + 1+1); // 15
        if (roll < 3) return "red";
        roll -= 3;
        if (roll < 3) return "blue";
        roll -= 3;
        if (roll < 3) return "yellow";
        roll -= 3;
        if (roll < 2) return "green";
        roll -= 2;
        if (roll < 2) return "pink";
        roll -= 2;
        if (roll < 1) return "black";
        return "white";
    }
}
