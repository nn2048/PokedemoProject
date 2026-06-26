package win.pokedemo;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores mapping from berry-bush block location -> berry item id (e.g. "oran_berry").
 * Persisted to plugins/PokeDemo/berrybushes.yml
 */
public class BerryBushManager {

    private final PokeDemoPlugin plugin;
    private final File file;
    private YamlConfiguration yaml;

    // key: worldName|x|y|z -> berryId
    private final Map<String, String> map = new ConcurrentHashMap<>();

    public BerryBushManager(PokeDemoPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "berrybushes.yml");
        load();
    }

    private static String key(Location loc) {
        return loc.getWorld().getName() + "|" + loc.getBlockX() + "|" + loc.getBlockY() + "|" + loc.getBlockZ();
    }

    public void set(Location loc, String berryId) {
        map.put(key(loc), berryId);
    }

    public String get(Location loc) {
        return map.get(key(loc));
    }

    public void remove(Location loc) {
        map.remove(key(loc));
    }

    public boolean has(Location loc) {
        return map.containsKey(key(loc));
    }

    public void load() {
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
        yaml = YamlConfiguration.loadConfiguration(file);
        map.clear();
        for (String k : yaml.getKeys(false)) {
            String v = yaml.getString(k);
            if (v != null && !v.isEmpty()) map.put(k, v);
        }
    }

    public void save() {
        if (yaml == null) yaml = new YamlConfiguration();
        yaml.getKeys(false).forEach(k -> yaml.set(k, null));
        for (Map.Entry<String, String> e : map.entrySet()) {
            yaml.set(e.getKey(), e.getValue());
        }
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save berrybushes.yml: " + e.getMessage());
        }
    }

    public void saveAsyncDebounced() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::save);
    }

    public Location parseKey(String k) {
        try {
            String[] parts = k.split("\\|");
            if (parts.length != 4) return null;
            World w = Bukkit.getWorld(parts[0]);
            if (w == null) return null;
            int x = Integer.parseInt(parts[1]);
            int y = Integer.parseInt(parts[2]);
            int z = Integer.parseInt(parts[3]);
            return new Location(w, x, y, z);
        } catch (Exception ex) {
            return null;
        }
    }

    public Collection<Map.Entry<String, String>> entries() {
        return Collections.unmodifiableCollection(map.entrySet());
    }
}
