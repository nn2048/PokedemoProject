package win.pokedemo;

import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

/**
 * Persistent registry for placed overworld machines.
 * We only treat NOTE_BLOCKs as machines if their coordinates are registered here.
 */
public class MachineRegistry {

    private final JavaPlugin plugin;
    private final File file;
    private YamlConfiguration yaml;

    public MachineRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "machines.yml");
        reload();
    }

    public void reload() {
        this.yaml = YamlConfiguration.loadConfiguration(file);
    }

    private static String key(Location loc) {
        return loc.getWorld().getName() + ":" + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }

    public void put(Location loc, MachineType type) {
        if (loc == null || type == null) return;
        yaml.set(key(loc), type.name());
        save();
    }

    public MachineType get(Location loc) {
        if (loc == null) return null;
        String v = yaml.getString(key(loc));
        if (v == null) return null;
        try {
            return MachineType.valueOf(v);
        } catch (Exception ignored) {
            return null;
        }
    }

    public void remove(Location loc) {
        if (loc == null) return;
        yaml.set(key(loc), null);
        save();
    }

    private void save() {
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            yaml.save(file);
        } catch (Exception e) {
            plugin.getLogger().warning("[PokeDemo] Failed to save machines.yml: " + e.getMessage());
        }
    }
}
