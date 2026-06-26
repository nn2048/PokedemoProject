package win.pokedemo;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

public final class SpawnZone {
    public final Player player;
    public final World world;
    public final Location center;
    public final int minX;
    public final int maxX;
    public final int minY;
    public final int maxY;
    public final int minZ;
    public final int maxZ;
    public final String dimensionKey;
    public final long worldTime;
    public final boolean raining;
    public final boolean thundering;

    public SpawnZone(Player player, Location center, int diameter, int height) {
        this.player = player;
        this.world = center.getWorld();
        this.center = center.clone();
        int halfD = Math.max(2, diameter) / 2;
        int halfH = Math.max(4, height) / 2;
        int cy = center.getBlockY();
        this.minX = center.getBlockX() - halfD;
        this.maxX = center.getBlockX() + halfD;
        this.minY = Math.max(world.getMinHeight() + 1, cy - halfH);
        this.maxY = Math.min(world.getMaxHeight() - 2, cy + halfH);
        this.minZ = center.getBlockZ() - halfD;
        this.maxZ = center.getBlockZ() + halfD;
        String dk;
        try {
            dk = world.getKey() != null ? world.getKey().toString().toLowerCase(java.util.Locale.ROOT) : world.getName().toLowerCase(java.util.Locale.ROOT);
        } catch (Throwable ignored) {
            dk = world.getName().toLowerCase(java.util.Locale.ROOT);
        }
        this.dimensionKey = dk;
        this.worldTime = world.getTime();
        this.raining = world.hasStorm();
        this.thundering = world.isThundering();
    }
}
