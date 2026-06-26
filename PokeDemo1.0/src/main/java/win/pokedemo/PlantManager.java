
package win.pokedemo;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.NoteBlock;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pixelmon-like apricorn/berry planting system using NoteBlock custom blockstates.
 *
 * Design goals:
 *  - Mature, stable: state stored in plugin data (not relying on block NBT).
 *  - Visual stages via resourcepack note_block variants.
 *  - Right-click harvest + regrow.
 */
public class PlantManager {

    public enum PlantKind { APRICORN, BERRY }

    public static class PlantDef {
        public final PlantKind kind;
        public final String id; // e.g. "red" (apricorn) or "cheri" (berry)
        public final List<Integer> stageMinutes; // per transition, size = stageCount-1
        public final int minDrop;
        public final int maxDrop;
        public final double seedChance; // extra seed/berry chance on harvest
        public PlantDef(PlantKind kind, String id, List<Integer> stageMinutes, int minDrop, int maxDrop, double seedChance) {
            this.kind = kind;
            this.id = id;
            this.stageMinutes = stageMinutes;
            this.minDrop = minDrop;
            this.maxDrop = maxDrop;
            this.seedChance = seedChance;
        }
        public int stageCount() {
            return kind == PlantKind.APRICORN ? 4 : 3;
        }
        public boolean isMature(int stage) {
            return stage == stageCount() - 1;
        }
    }

    public static class PlantInstance {
        public final UUID world;
        public final int x,y,z;
        public final PlantKind kind;
        public final String id;
        public int stage;
        public long stageStartMs;
        public PlantInstance(UUID world, int x, int y, int z, PlantKind kind, String id, int stage, long stageStartMs) {
            this.world=world; this.x=x; this.y=y; this.z=z;
            this.kind=kind; this.id=id; this.stage=stage; this.stageStartMs=stageStartMs;
        }
        public String key() { return world+":"+x+":"+y+":"+z; }
    }

    private final JavaPlugin plugin;
    private final ItemRegistry itemRegistry;
    private final ItemFactory items;
    private final LangManager lang;
    private final Map<String, PlantDef> defs = new HashMap<>();
    private final Map<String, PlantInstance> instances = new ConcurrentHashMap<>();
    private final Random rng = new Random();
    private final File dataFile;

    public PlantManager(JavaPlugin plugin, ItemRegistry itemRegistry, ItemFactory items, LangManager lang) {
        this.plugin = plugin;
        this.itemRegistry = itemRegistry;
        this.items = items;
        this.lang = lang;
        this.dataFile = new File(plugin.getDataFolder(), "plants.yml");

        // Defaults close to Pixelmon pacing (can be tuned later)
        // Apricorns: slower
        List<Integer> apri = List.of(6, 6, 6); // stage0->1,1->2,2->3
        for (String c : List.of("red","blue","yellow","green","pink","black","white")) {
            defs.put("apricorn:"+c, new PlantDef(PlantKind.APRICORN, c, apri, 1, 3, 0.25));
        }
        // Berries: faster
        List<Integer> berry = List.of(4, 4); // sprout->young, young->mature
        for (String b : BerryIndex.ALL_BERRIES) {
            defs.put("berry:"+b, new PlantDef(PlantKind.BERRY, b, berry, 1, 2, 0.20));
        }
    }

    public void load() {
        instances.clear();
        if (!dataFile.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(dataFile);
        var sec = yml.getConfigurationSection("plants");
        if (sec == null) return;
        for (String k : sec.getKeys(false)) {
            var s = sec.getConfigurationSection(k);
            if (s == null) continue;
            try {
                UUID w = UUID.fromString(s.getString("world"));
                int x = s.getInt("x");
                int y = s.getInt("y");
                int z = s.getInt("z");
                PlantKind kind = PlantKind.valueOf(s.getString("kind"));
                String id = s.getString("id");
                int stage = s.getInt("stage");
                long start = s.getLong("stageStartMs");
                PlantInstance pi = new PlantInstance(w,x,y,z,kind,id,stage,start);
                instances.put(pi.key(), pi);
            } catch (Exception ignored) {}
        }
    }

    public void save() {
        YamlConfiguration yml = new YamlConfiguration();
        int i=0;
        for (PlantInstance pi : instances.values()) {
            String path = "plants.p"+(i++);
            yml.set(path+".world", pi.world.toString());
            yml.set(path+".x", pi.x);
            yml.set(path+".y", pi.y);
            yml.set(path+".z", pi.z);
            yml.set(path+".kind", pi.kind.name());
            yml.set(path+".id", pi.id);
            yml.set(path+".stage", pi.stage);
            yml.set(path+".stageStartMs", pi.stageStartMs);
        }
        try {
            yml.save(dataFile);
        } catch (IOException ignored) {}
    }

    public void startTicking() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L*30, 20L*30); // every 30s
        Bukkit.getScheduler().runTaskTimer(plugin, this::save, 20L*60*5, 20L*60*5); // every 5m
    }

    /**
     * Worldgen / admin placement helper. This bypasses soil checks and simply
     * places a plant instance at the location. Used for overworld natural spawns.
     */
    public boolean placeWildPlant(Location loc, PlantKind kind, String id, int stage) {
        if (loc == null || loc.getWorld() == null) return false;
        PlantDef def = defs.get((kind==PlantKind.APRICORN?"apricorn:":"berry:") + id);
        if (def == null) return false;

        Block b = loc.getBlock();
        if (b.getType() != Material.AIR && !b.isPassable()) return false;

        b.setType(Material.NOTE_BLOCK, false);
        PlantInstance pi = new PlantInstance(b.getWorld().getUID(), b.getX(), b.getY(), b.getZ(), kind, id,
                Math.max(0, Math.min(stage, def.stageCount()-1)), System.currentTimeMillis());
        instances.put(pi.key(), pi);
        setBlockVisual(pi);
        return true;
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (PlantInstance pi : new ArrayList<>(instances.values())) {
            PlantDef def = defs.get((pi.kind==PlantKind.APRICORN?"apricorn:":"berry:")+pi.id);
            if (def == null) continue;
            if (def.isMature(pi.stage)) continue;

            int idx = pi.stage; // transition index
            int minutes = def.stageMinutes.get(Math.min(idx, def.stageMinutes.size()-1));
            if (now - pi.stageStartMs < minutes * 60_000L) continue;

            // advance stage
            pi.stage++;
            pi.stageStartMs = now;
            setBlockVisual(pi);
        }
    }

    public boolean isPlantBlock(Block b) {
        if (b == null || b.getType() != Material.NOTE_BLOCK) return false;
        return instances.containsKey(locKey(b.getLocation()));
    }

    public PlantInstance get(Block b) {
        return instances.get(locKey(b.getLocation()));
    }

    public void remove(Block b) {
        instances.remove(locKey(b.getLocation()));
    }

    private String locKey(Location l) {
        return l.getWorld().getUID()+":"+l.getBlockX()+":"+l.getBlockY()+":"+l.getBlockZ();
    }


    /** Returns true if the 4 horizontal sides around the plant block are air/passable. */
    public boolean isAreaClear(Block plantBlock) {
        if (plantBlock == null) return false;
        Block n = plantBlock.getRelative(0, 0, -1);
        Block s = plantBlock.getRelative(0, 0, 1);
        Block w = plantBlock.getRelative(-1, 0, 0);
        Block e = plantBlock.getRelative(1, 0, 0);
        return (n.getType() == Material.AIR || n.isPassable())
                && (s.getType() == Material.AIR || s.isPassable())
                && (w.getType() == Material.AIR || w.isPassable())
                && (e.getType() == Material.AIR || e.isPassable());
    }

    /**
     * Break the plant like cactus: if blocked on any side, pop into drops and remove the block.
     * This is used when players place blocks next to a planted berry/apricorn.
     */
    public void popIfBlocked(Block plantBlock) {
        if (plantBlock == null) return;
        if (plantBlock.getType() != Material.NOTE_BLOCK && plantBlock.getType() != Material.SWEET_BERRY_BUSH) return;
        if (!isPlantBlock(plantBlock)) return;
        if (isAreaClear(plantBlock)) return;
        dropOnBreak(plantBlock);
        instances.remove(locKey(plantBlock.getLocation()));
        plantBlock.setType(Material.AIR, false);
    }

    /**
     * Drops items when the plant is broken (not harvested).
     * Rules:
     *  - Always return the seed (apricorn seed / berry itself).
     *  - If mature, also drop the produce amount.
     */
    public void dropOnBreak(Block b) {
        PlantInstance pi = get(b);
        if (pi == null) return;
        PlantDef def = defs.get((pi.kind==PlantKind.APRICORN?"apricorn:":"berry:")+pi.id);
        if (def == null) return;

        if (pi.kind == PlantKind.APRICORN) {
            ItemDef seed = itemRegistry.get("seed_" + pi.id + "_apricorn");
            if (seed != null) b.getWorld().dropItemNaturally(b.getLocation().add(0.5,0.7,0.5), items.createItem(seed, lang, 1));
            if (def.isMature(pi.stage)) {
                int amount = def.minDrop + rng.nextInt(def.maxDrop - def.minDrop + 1);
                ItemDef fruit = itemRegistry.get(pi.id + "_apricorn");
                if (fruit != null) {
                    for (int i=0;i<amount;i++) {
                        b.getWorld().dropItemNaturally(b.getLocation().add(0.5,0.7,0.5), items.createItem(fruit, lang, 1));
                    }
                }
            }
        } else {
            ItemDef berry = itemRegistry.get(pi.id + "_berry");
            if (berry != null) b.getWorld().dropItemNaturally(b.getLocation().add(0.5,0.7,0.5), items.createItem(berry, lang, 1));
            if (def.isMature(pi.stage)) {
                int amount = def.minDrop + rng.nextInt(def.maxDrop - def.minDrop + 1);
                if (berry != null) {
                    for (int i=0;i<amount;i++) {
                        b.getWorld().dropItemNaturally(b.getLocation().add(0.5,0.7,0.5), items.createItem(berry, lang, 1));
                    }
                }
            }
        }
    }

    public boolean plant(Player p, Block soil, ItemDef usedDef) {
        if (soil == null) return false;
        Location base = soil.getLocation().add(0,1,0);
        Block target = base.getBlock();
        if (target.getType() != Material.AIR && !target.isPassable()) return false;

        // Visual rule: the berry/apricorn note-block models are not full cubes.
        // If there are blocks adjacent on the 4 horizontal sides, Minecraft may render with odd face culling
        // (appearing like a "透视" non-solid block). Enforce an empty cross around the plant.
        Block n = target.getRelative(0, 0, -1);
        Block s = target.getRelative(0, 0, 1);
        Block w = target.getRelative(-1, 0, 0);
        Block e = target.getRelative(1, 0, 0);
        if (!(n.getType() == Material.AIR || n.isPassable())) return false;
        if (!(s.getType() == Material.AIR || s.isPassable())) return false;
        if (!(w.getType() == Material.AIR || w.isPassable())) return false;
        if (!(e.getType() == Material.AIR || e.isPassable())) return false;

        PlantKind kind;
        String id;
        if (usedDef.id.startsWith("seed_")) {
            kind = PlantKind.APRICORN;
            id = usedDef.id.substring("seed_".length()); // seed_red_apricorn -> red_apricorn? we use red/blue...
            if (id.endsWith("_apricorn")) id = id.replace("_apricorn","");
        } else if (usedDef.id.endsWith("_berry")) {
            kind = PlantKind.BERRY;
            id = usedDef.id.substring(0, usedDef.id.length()-"_berry".length());
        } else {
            return false;
        }

        PlantDef def = defs.get((kind==PlantKind.APRICORN?"apricorn:":"berry:")+id);
        if (def == null) return false;

        // place note block
        target.setType(Material.NOTE_BLOCK, false);

        PlantInstance pi = new PlantInstance(target.getWorld().getUID(), target.getX(), target.getY(), target.getZ(), kind, id, 0, System.currentTimeMillis());
        instances.put(pi.key(), pi);
        setBlockVisual(pi);
        return true;
    }

    public void setBlockVisual(PlantInstance pi) {
        World w = Bukkit.getWorld(pi.world);
        if (w == null) return;
        Block b = w.getBlockAt(pi.x, pi.y, pi.z);
        // BERRY now uses vanilla sweet berry bush (age 0-3) instead of NOTE_BLOCK variants.
        // This avoids the long-standing "grown stage becomes note block" visual mismatch.
        if (pi.kind == PlantKind.BERRY) {
            if (b.getType() != Material.SWEET_BERRY_BUSH) {
                b.setType(Material.SWEET_BERRY_BUSH, false);
            }
            if (b.getBlockData() instanceof org.bukkit.block.data.Ageable ageable) {
                int age = Math.max(0, Math.min(3, pi.stage));
                ageable.setAge(age);
                b.setBlockData(ageable, false);
            }
            return;
        }

        if (b.getType() != Material.NOTE_BLOCK) return;
        NoteBlock data = (NoteBlock) b.getBlockData();
        PlantState.Resolved ps = PlantState.forPlant(pi.kind, pi.id, pi.stage);
        if (ps == null || ps.instrument == null || ps.note == null) return;
        data.setInstrument(ps.instrument);
        data.setNote(ps.note);
        data.setPowered(false);
        // IMPORTANT: do NOT apply physics here.
        b.setBlockData(data, false);
    }

    public void harvest(Player player, Block b) {
        PlantInstance pi = get(b);
        if (pi == null) return;
        PlantDef def = defs.get((pi.kind==PlantKind.APRICORN?"apricorn:":"berry:")+pi.id);
        if (def == null) return;
        if (!def.isMature(pi.stage)) return;

        int amount = def.minDrop + rng.nextInt(def.maxDrop - def.minDrop + 1);
        if (pi.kind == PlantKind.APRICORN) {
            ItemDef fruit = itemRegistry.get(pi.id + "_apricorn");
            if (fruit != null) b.getWorld().dropItemNaturally(b.getLocation().add(0.5,0.7,0.5), items.createItem(fruit, lang, 1));
            // extra fruits
            for (int i=1;i<amount;i++) {
                if (fruit != null) b.getWorld().dropItemNaturally(b.getLocation().add(0.5,0.7,0.5), items.createItem(fruit, lang, 1));
            }
            // seed chance
            if (rng.nextDouble() < def.seedChance) {
                ItemDef seed = itemRegistry.get("seed_" + pi.id + "_apricorn");
                if (seed != null) b.getWorld().dropItemNaturally(b.getLocation().add(0.5,0.7,0.5), items.createItem(seed, lang, 1));
            }
            // reset to stage1 (like Pixelmon regrow)
            pi.stage = 1;
        } else {
            ItemDef berry = itemRegistry.get(pi.id + "_berry");
            if (berry != null) {
                for (int i=0;i<amount;i++) {
                    b.getWorld().dropItemNaturally(b.getLocation().add(0.5,0.7,0.5), items.createItem(berry, lang, 1));
                }
                if (rng.nextDouble() < def.seedChance) {
                    b.getWorld().dropItemNaturally(b.getLocation().add(0.5,0.7,0.5), items.createItem(berry, lang, 1));
                }
            }
            // like vanilla berry bush: harvest -> goes back to a small bush
            pi.stage = 1;
        }
        pi.stageStartMs = System.currentTimeMillis();
        setBlockVisual(pi);
    }
}
