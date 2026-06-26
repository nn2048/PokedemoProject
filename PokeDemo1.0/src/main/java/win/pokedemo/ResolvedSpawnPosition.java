package win.pokedemo;

import org.bukkit.Location;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class ResolvedSpawnPosition {
    public final Location location;
    public final String positionType;
    public final String biomeKey;
    public final Set<String> biomeTags;
    public final Set<String> nearbyBlocks;
    public final boolean canSeeSky;
    public final int skyLight;
    public final int blockLight;
    public final int y;
    public final boolean nearWater;
    public final boolean raining;
    public final boolean thundering;
    public final Set<String> archetypes;

    public ResolvedSpawnPosition(Location location,
                                 String positionType,
                                 String biomeKey,
                                 Set<String> biomeTags,
                                 Set<String> nearbyBlocks,
                                 boolean canSeeSky,
                                 int skyLight,
                                 int blockLight,
                                 boolean nearWater,
                                 boolean raining,
                                 boolean thundering,
                                 Set<String> archetypes) {
        this.location = location;
        this.positionType = positionType;
        this.biomeKey = biomeKey;
        this.biomeTags = biomeTags == null ? Collections.emptySet() : Collections.unmodifiableSet(new HashSet<>(biomeTags));
        this.nearbyBlocks = nearbyBlocks == null ? Collections.emptySet() : Collections.unmodifiableSet(new HashSet<>(nearbyBlocks));
        this.canSeeSky = canSeeSky;
        this.skyLight = skyLight;
        this.blockLight = blockLight;
        this.y = location.getBlockY();
        this.nearWater = nearWater;
        this.raining = raining;
        this.thundering = thundering;
        this.archetypes = archetypes == null ? Collections.emptySet() : Collections.unmodifiableSet(new HashSet<>(archetypes));
    }
}
