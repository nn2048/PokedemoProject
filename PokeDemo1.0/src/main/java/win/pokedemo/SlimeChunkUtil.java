package win.pokedemo;

import org.bukkit.World;

import java.util.Random;

public final class SlimeChunkUtil {
    private SlimeChunkUtil() {}

    public static boolean isSlimeChunk(World world, int chunkX, int chunkZ) {
        if (world == null) return false;
        long seed = world.getSeed();
        long rndSeed = seed
                + (long) (chunkX * chunkX) * 4987142L
                + (long) (chunkX * 5947611L)
                + (long) (chunkZ * chunkZ) * 4392871L
                + (long) (chunkZ * 389711L)
                ^ 987234911L;
        return new Random(rndSeed).nextInt(10) == 0;
    }
}
