package win.pokedemo;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;

/**
 * Fossil Analyzer (化石解析仪):
 * - input: suspicious_stone + 1 water bucket
 * - click start -> 60s countdown
 * - finish -> random fossil (amber lowest)
 */
public final class FossilAnalyzerManager implements Runnable {

    public static final class State {
        public boolean hasStone;
        public boolean hasWater;
        public boolean running;
        public long endAtMs;
        public String readyFossilId;
    }

    private final PokeDemoPlugin plugin;
    private final MachineRegistry machines;
    private final File file;
    private final Map<String, State> states = new HashMap<>();

    public FossilAnalyzerManager(PokeDemoPlugin plugin, MachineRegistry machines) {
        this.plugin = plugin;
        this.machines = machines;
        this.file = new File(plugin.getDataFolder(), "fossil_analyzers.yml");
        load();
    }

    public String keyOf(Location loc) {
        if (loc == null || loc.getWorld() == null) return null;
        return loc.getWorld().getName() + ":" + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }

    public Location parseKey(String key) {
        try {
            if (key == null) return null;
            String[] a = key.split(":", 2);
            if (a.length != 2) return null;
            World w = plugin.getServer().getWorld(a[0]);
            if (w == null) return null;
            String[] b = a[1].split(",");
            if (b.length != 3) return null;
            return new Location(w, Integer.parseInt(b[0]), Integer.parseInt(b[1]), Integer.parseInt(b[2]));
        } catch (Throwable t) {
            return null;
        }
    }

    public State state(String key) {
        return states.computeIfAbsent(key, k -> new State());
    }

    public void onBroken(Location loc) {
        String k = keyOf(loc);
        if (k == null) return;
        states.remove(k);
        save();
    }

    public boolean putStone(String key) {
        State st = state(key);
        if (st.running) return false;
        if (st.readyFossilId != null) return false;
        st.hasStone = true;
        save();
        return true;
    }

    public boolean putWater(String key) {
        State st = state(key);
        if (st.running) return false;
        if (st.readyFossilId != null) return false;
        st.hasWater = true;
        save();
        return true;
    }

    public boolean start(String key) {
        State st = state(key);
        if (st.running) return false;
        if (st.readyFossilId != null) return false;
        if (!st.hasStone || !st.hasWater) return false;
        st.running = true;
        st.endAtMs = System.currentTimeMillis() + 60_000L;
        save();
        return true;
    }

    public String finishRoll(State st) {
        // weights: helix 45, dome 45, amber 10
        int r = Util.RND.nextInt(100);
        if (r < 45) return "helix_fossil";
        if (r < 90) return "dome_fossil";
        return "old_amber";
    }

    public String claimResult(String key) {
        State st = states.get(key);
        if (st == null) return null;
        String out = st.readyFossilId;
        if (out == null) return null;
        st.readyFossilId = null;
        st.hasStone = false;
        st.hasWater = false;
        st.running = false;
        st.endAtMs = 0;
        save();
        return out;
    }

    @Override
    public void run() {
        try {
            tick();
        } catch (Throwable t) {
            plugin.getLogger().warning("[PokeDemo] FossilAnalyzer tick error: " + t.getMessage());
        }
    }

    private void tick() {
        long now = System.currentTimeMillis();
        boolean dirty = false;
        for (var en : new ArrayList<>(states.entrySet())) {
            String key = en.getKey();
            State st = en.getValue();
            Location loc = parseKey(key);
            if (loc == null) { states.remove(key); dirty = true; continue; }
            Block b = loc.getBlock();
            if (b.getType() != Material.NOTE_BLOCK || machines.get(loc) != MachineType.FOSSIL_ANALYZER) {
                states.remove(key);
                dirty = true;
                continue;
            }
            if (st.running && now >= st.endAtMs) {
                st.running = false;
                st.readyFossilId = finishRoll(st);
                dirty = true;
            }
        }
        if (dirty) save();
    }

    private void load() {
        try {
            if (!file.exists()) return;
            YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
            for (String key : y.getKeys(false)) {
                State st = new State();
                st.hasStone = y.getBoolean(key + ".hasStone", false);
                st.hasWater = y.getBoolean(key + ".hasWater", false);
                st.running = y.getBoolean(key + ".running", false);
                st.endAtMs = y.getLong(key + ".endAt", 0L);
                st.readyFossilId = y.getString(key + ".ready", null);
                states.put(key, st);
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("[PokeDemo] fossil_analyzers.yml load failed: " + t.getMessage());
        }
    }

    private void save() {
        try {
            YamlConfiguration y = new YamlConfiguration();
            for (var en : states.entrySet()) {
                String key = en.getKey();
                State st = en.getValue();
                y.set(key + ".hasStone", st.hasStone);
                y.set(key + ".hasWater", st.hasWater);
                y.set(key + ".running", st.running);
                y.set(key + ".endAt", st.endAtMs);
                y.set(key + ".ready", st.readyFossilId);
            }
            y.save(file);
        } catch (Throwable t) {
            plugin.getLogger().warning("[PokeDemo] fossil_analyzers.yml save failed: " + t.getMessage());
        }
    }
}
