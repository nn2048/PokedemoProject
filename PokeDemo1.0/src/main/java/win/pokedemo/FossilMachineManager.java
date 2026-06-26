package win.pokedemo;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.*;

/**
 * Fossil machine runtime + persistence.
 *
 * Requirements (user spec):
 * - Separate machine (do NOT break pasture)
 * - Redstone fuel: 1 redstone = 1 second parsing time
 * - During countdown cannot operate other buttons
 * - Every minute AND at countdown end: roll success
 * - Success chance increases with elapsed parsing time, max 90% at 64 seconds
 * - On success: produce an egg that can be claimed with a ball into party/pc
 * - If machine is broken, stored fossil/fuel is NOT dropped
 */
public final class FossilMachineManager implements Runnable {

    public static final class State {
        public String fossilId = null;      // helix_fossil / dome_fossil / old_amber
        public int fuelSeconds = 0;         // total fuel provided (seconds)
        public int elapsedSeconds = 0;      // seconds spent so far
        public boolean running = false;
        public boolean eggReady = false;
        public String eggSpeciesId = null;  // omanyte/kabuto/aerodactyl
        public long lastMinuteCheckAtMs = 0;

        // "Suspicious Stone" mode (wash -> random fossil)
        public boolean waterReady = false;
        public boolean fossilReady = false;
        public String readyFossilId = null; // helix_fossil / dome_fossil / old_amber
    }

    private final PokeDemoPlugin plugin;
    private final Storage storage;
    private final Dex dex;
    private final MachineRegistry machines;

    private final Map<String, State> states = new HashMap<>();
    private final File file;

    public FossilMachineManager(PokeDemoPlugin plugin, Storage storage, Dex dex, MachineRegistry machines) {
        this.plugin = plugin;
        this.storage = storage;
        this.dex = dex;
        this.machines = machines;
        this.file = new File(plugin.getDataFolder(), "fossil_machines.yml");
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
            int x = Integer.parseInt(b[0]);
            int y = Integer.parseInt(b[1]);
            int z = Integer.parseInt(b[2]);
            return new Location(w, x, y, z);
        } catch (Throwable t) {
            return null;
        }
    }

    public State state(String key) {
        if (key == null) return null;
        return states.computeIfAbsent(key, k -> new State());
    }

    public void onMachineBroken(Location loc) {
        String key = keyOf(loc);
        if (key == null) return;
        states.remove(key);
        save();
    }

    public boolean addFuelSeconds(String key, int seconds) {
        if (key == null || seconds <= 0) return false;
        State st = state(key);
        if (st == null) return false;
        if (st.running) return false;
        // Suspicious-stone mode doesn't use redstone fuel.
        if ("suspicious_stone".equals(st.fossilId)) return false;
        // Hard cap at 64s (one stack). Prevents legacy machines from exceeding the design limit.
        st.fuelSeconds = Math.min(64, st.fuelSeconds + seconds);
        save();
        return true;
    }

    public boolean addWaterBucket(String key) {
        if (key == null) return false;
        State st = state(key);
        if (st == null) return false;
        if (st.running) return false;
        if (!"suspicious_stone".equals(st.fossilId)) return false;
        if (st.fossilReady) return false;
        if (st.waterReady) return false;
        st.waterReady = true;
        save();
        return true;
    }

    public boolean setFossil(String key, String fossilId) {
        if (key == null || fossilId == null || fossilId.isBlank()) return false;
        State st = state(key);
        if (st == null) return false;
        if (st.running) return false;
        if (st.eggReady) return false;
        // reset stone-mode flags when swapping input
        st.waterReady = false;
        st.fossilReady = false;
        st.readyFossilId = null;
        st.fossilId = fossilId;
        save();
        return true;
    }

    public boolean start(String key) {
        State st = state(key);
        if (st == null) return false;
        if (st.running) return false;
        if (st.eggReady || st.fossilReady) return false;
        if (st.fossilId == null) return false;

        // Suspicious-stone mode: requires water bucket, runs fixed 60 seconds and produces a random fossil.
        if ("suspicious_stone".equals(st.fossilId)) {
            if (!st.waterReady) return false;
            st.running = true;
            st.elapsedSeconds = 0;
            st.fuelSeconds = 60;
            st.lastMinuteCheckAtMs = System.currentTimeMillis();
            save();
            return true;
        }

        if (st.fuelSeconds <= 0) return false;
        st.running = true;
        st.elapsedSeconds = 0;
        st.lastMinuteCheckAtMs = System.currentTimeMillis();
        save();
        return true;
    }

    public boolean claimReadyFossil(UUID player, String key) {
        if (player == null || key == null) return false;
        State st = states.get(key);
        if (st == null || !st.fossilReady || st.readyFossilId == null) return false;
        var p = plugin.getServer().getPlayer(player);
        if (p == null) return false;
        ItemDef def = plugin.getItemRegistry().get(st.readyFossilId);
        if (def == null) return false;
        ItemStack it = plugin.getItems().createItem(def, plugin.getLang(), 1);

        // give or drop
        Map<Integer, ItemStack> rem = p.getInventory().addItem(it);
        if (!rem.isEmpty()) {
            for (ItemStack r : rem.values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), r);
            }
        }

        // reset machine
        st.running = false;
        st.elapsedSeconds = 0;
        st.fuelSeconds = 0;
        st.fossilId = null;
        st.waterReady = false;
        st.fossilReady = false;
        st.readyFossilId = null;
        save();
        return true;
    }

    private String rollRandomFossilId() {
        // Amber has the smallest chance.
        int r = Util.RND.nextInt(100);
        if (r < 45) return "helix_fossil";
        if (r < 90) return "dome_fossil";
        return "old_amber";
    }

    public int remainingSeconds(State st) {
        if (st == null) return 0;
        return Math.max(0, st.fuelSeconds - st.elapsedSeconds);
    }

    /** chance grows with elapsed seconds; max 0.90 at 64s. */
    public double currentChance(State st) {
        if (st == null) return 0;
        int full = plugin.getConfig().getInt("fossils.machine.fullstack-seconds", 64);
        double max = plugin.getConfig().getDouble("fossils.machine.max-success", 0.90);
        double base = plugin.getConfig().getDouble("fossils.machine.base-success", 0.02);
        double t = Math.max(0, Math.min(1.0, st.elapsedSeconds / (double) Math.max(1, full)));
        return Math.min(max, base + (max - base) * t);
    }

    private boolean rollSuccess(State st) {
        double chance = currentChance(st);
        return Util.RND.nextDouble() < chance;
    }

    private String eggSpeciesForFossil(String fossilId) {
        if (fossilId == null) return null;
        return switch (fossilId) {
            case "helix_fossil" -> "omanyte";
            case "dome_fossil" -> "kabuto";
            case "old_amber" -> "aerodactyl";
            default -> null;
        };
    }

    /** Claim egg with given ball id into party/pc. */
    public boolean claimEggWithBall(UUID player, String key, String ballId) {
        if (player == null || key == null || ballId == null) return false;
        State st = states.get(key);
        if (st == null || !st.eggReady || st.eggSpeciesId == null) return false;
        Species s = dex.getSpecies(st.eggSpeciesId);
        if (s == null) return false;
        PlayerProfile prof = storage.getProfile(player);
        if (prof == null) return false;

        PokemonInstance egg = PokemonInstance.createOwned(s, 1, dex);
        egg.isEgg = true;
        egg.eggStepsTotal = Math.max(1, plugin.getConfig().getInt("fossils.egg.hatch-steps", 1000));
        egg.eggStepsRemaining = egg.eggStepsTotal;
        egg.eggBallId = ballId;

        prof.depositToPartyOrPc(egg);
        storage.saveProfile(player);

        // reset machine
        st.eggReady = false;
        st.eggSpeciesId = null;
        st.running = false;
        st.elapsedSeconds = 0;
        st.fuelSeconds = 0;
        st.fossilId = null;
        save();
        return true;
    }

    @Override
    public void run() {
        try {
            tick();
        } catch (Throwable t) {
            plugin.getLogger().warning("[PokeDemo] FossilMachineManager tick error: " + t.getMessage());
        }
    }

    private void tick() {
        long now = System.currentTimeMillis();
        boolean dirty = false;

        for (Map.Entry<String, State> en : new ArrayList<>(states.entrySet())) {
            String key = en.getKey();
            State st = en.getValue();
            if (st == null) continue;

            // Validate block still exists and is a fossil machine.
            Location loc = parseKey(key);
            if (loc == null || loc.getWorld() == null) { states.remove(key); dirty = true; continue; }
            Block b = loc.getBlock();
            if (b.getType() != Material.NOTE_BLOCK || machines.get(loc) != MachineType.FOSSIL) {
                states.remove(key);
                dirty = true;
                continue;
            }

            if (!st.running) continue;
            if (st.eggReady) { st.running = false; dirty = true; continue; }
            if (st.fossilReady) { st.running = false; dirty = true; continue; }

            st.elapsedSeconds++;
            dirty = true;

            boolean minute = (now - st.lastMinuteCheckAtMs) >= 60_000L;
            boolean finished = st.elapsedSeconds >= st.fuelSeconds;

            // Suspicious-stone mode: fixed 60s, always produce a fossil.
            if ("suspicious_stone".equals(st.fossilId)) {
                if (finished) {
                    st.readyFossilId = rollRandomFossilId();
                    st.fossilReady = true;
                    st.running = false;
                }
                continue;
            }

            if (minute) {
                st.lastMinuteCheckAtMs = now;
                if (rollSuccess(st)) {
                    String eggSpecies = eggSpeciesForFossil(st.fossilId);
                    if (eggSpecies != null) {
                        st.eggReady = true;
                        st.eggSpeciesId = eggSpecies;
                        st.running = false;
                    }
                }
            }

            if (!st.eggReady && finished) {
                // End-of-countdown roll
                if (rollSuccess(st)) {
                    String eggSpecies = eggSpeciesForFossil(st.fossilId);
                    if (eggSpecies != null) {
                        st.eggReady = true;
                        st.eggSpeciesId = eggSpecies;
                    }
                }
                st.running = false;
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
                st.fossilId = y.getString(key + ".fossil", null);
                // Suspicious stone is NOT a valid input for the Fossil Reviver (it belongs to Fossil Analyzer).
                if ("suspicious_stone".equals(st.fossilId)) {
                    st.fossilId = null;
                }
                st.fuelSeconds = y.getInt(key + ".fuel", 0);
                if (st.fuelSeconds > 64) st.fuelSeconds = 64;
                st.elapsedSeconds = y.getInt(key + ".elapsed", 0);
                if (st.elapsedSeconds > st.fuelSeconds) st.elapsedSeconds = st.fuelSeconds;
                st.running = y.getBoolean(key + ".running", false);
                st.eggReady = y.getBoolean(key + ".eggReady", false);
                st.eggSpeciesId = y.getString(key + ".eggSpecies", null);
                st.waterReady = y.getBoolean(key + ".waterReady", false);
                st.fossilReady = y.getBoolean(key + ".fossilReady", false);
                st.readyFossilId = y.getString(key + ".readyFossil", null);
                st.lastMinuteCheckAtMs = y.getLong(key + ".lastMinute", System.currentTimeMillis());
                states.put(key, st);
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("[PokeDemo] fossil_machines.yml load failed: " + t.getMessage());
        }
    }

    private void save() {
        try {
            YamlConfiguration y = new YamlConfiguration();
            for (Map.Entry<String, State> en : states.entrySet()) {
                String key = en.getKey();
                State st = en.getValue();
                if (key == null || st == null) continue;
                y.set(key + ".fossil", st.fossilId);
                y.set(key + ".fuel", st.fuelSeconds);
                y.set(key + ".elapsed", st.elapsedSeconds);
                y.set(key + ".running", st.running);
                y.set(key + ".eggReady", st.eggReady);
                y.set(key + ".eggSpecies", st.eggSpeciesId);
                y.set(key + ".waterReady", st.waterReady);
                y.set(key + ".fossilReady", st.fossilReady);
                y.set(key + ".readyFossil", st.readyFossilId);
                y.set(key + ".lastMinute", st.lastMinuteCheckAtMs);
            }
            y.save(file);
        } catch (Throwable t) {
            plugin.getLogger().warning("[PokeDemo] fossil_machines.yml save failed: " + t.getMessage());
        }
    }
}
