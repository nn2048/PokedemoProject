package win.pokedemo;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persistent state & selection locks for Clone Machines.
 *
 * Design goals:
 * - A selected Mew is UI-locked (persisted in PokemonInstance), so trading it away keeps it locked.
 * - Clone attempt counters are stored on the Pokémon itself, so trading it away also preserves usage.
 * - Machine block break automatically unlocks the selected Pokémon.
 */
public class CloneManager {

    private final JavaPlugin plugin;
    private final Storage storage;
    private final Dex dex;

    private final File file;
    private YamlConfiguration yaml;

    /** Pending selection flow: player chooses a Pokémon in PC to assign to a clone machine. */
    private final ConcurrentHashMap<UUID, PendingSelect> pending = new ConcurrentHashMap<>();

    /** Runtime task ids for running clone countdowns (not persisted). */
    private final ConcurrentHashMap<String, Integer> runningTaskIds = new ConcurrentHashMap<>();

    public static final class PendingSelect {
        public final String cloneKey;
        public final long expiresAt;
        public PendingSelect(String cloneKey, long expiresAt) {
            this.cloneKey = cloneKey;
            this.expiresAt = expiresAt;
        }
    }

    public CloneManager(JavaPlugin plugin, Storage storage, Dex dex) {
        this.plugin = plugin;
        this.storage = storage;
        this.dex = dex;
        this.file = new File(plugin.getDataFolder(), "clones.yml");
        reload();
    }

    public void reload() {
        this.yaml = YamlConfiguration.loadConfiguration(file);
        runningTaskIds.clear();
    }

    private static String key(Location loc) {
        return loc.getWorld().getName() + ":" + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }

    public String keyOf(Location loc) {
        return key(loc);
    }

    public UUID getOwner(String cloneKey) {
        try {
            String s = yaml.getString(cloneKey + ".owner");
            return (s == null || s.isBlank()) ? null : UUID.fromString(s);
        } catch (Exception ignored) {
            return null;
        }
    }

    public void ensureOwner(String cloneKey, UUID owner) {
        if (cloneKey == null || owner == null) return;
        if (yaml.getString(cloneKey + ".owner") != null) return;
        yaml.set(cloneKey + ".owner", owner.toString());
        save();
    }

    public UUID getSelectedMew(String cloneKey) {
        try {
            String s = yaml.getString(cloneKey + ".mew");
            return (s == null || s.isBlank()) ? null : UUID.fromString(s);
        } catch (Exception ignored) {
            return null;
        }
    }

    public int getBonus(String cloneKey) {
        return yaml.getInt(cloneKey + ".bonus", 0);
    }

    public void setBonus(String cloneKey, int bonus) {
        yaml.set(cloneKey + ".bonus", Math.max(0, Math.min(50, bonus)));
        save();
    }

    public boolean isRunning(String cloneKey) {
        return yaml.getBoolean(cloneKey + ".running", false);
    }

    public long getEndAt(String cloneKey) {
        return yaml.getLong(cloneKey + ".endAt", 0L);
    }

    public void beginSelect(UUID playerId, String cloneKey) {
        pending.put(playerId, new PendingSelect(cloneKey, Long.MAX_VALUE));
    }

    public PendingSelect getPending(UUID playerId) {
        return pending.get(playerId);
    }

    public void clearPending(UUID playerId) {
        pending.remove(playerId);
    }

    /** Assign a Mew to the clone machine and lock it. */
    public void assignMew(UUID owner, String cloneKey, UUID pokemonUuid) {
        if (owner == null || cloneKey == null) return;
        ensureOwner(cloneKey, owner);
        PlayerProfile prof = storage.getProfile(owner);
        if (prof == null) return;

        // unlock previous
        UUID prev = getSelectedMew(cloneKey);
        if (prev != null) {
            PokemonInstance old = prof.findByUuid(prev);
            if (old != null) {
                old.uiLocked = false;
                old.uiLockReason = "";
            }
        }

        // lock new
        if (pokemonUuid != null) {
            PokemonInstance p = prof.findByUuid(pokemonUuid);
            if (p != null) {
                p.uiLocked = true;
                p.uiLockReason = "克隆机";
            }
        }

        yaml.set(cloneKey + ".mew", pokemonUuid == null ? null : pokemonUuid.toString());
        save();
    }

    public void clearSelection(UUID owner, String cloneKey) {
        if (owner == null || cloneKey == null) return;
        PlayerProfile prof = storage.getProfile(owner);
        if (prof != null) {
            UUID sel = getSelectedMew(cloneKey);
            if (sel != null) {
                PokemonInstance p = prof.findByUuid(sel);
                if (p != null) {
                    p.uiLocked = false;
                    p.uiLockReason = "";
                }
            }
        }
        yaml.set(cloneKey + ".mew", null);
        yaml.set(cloneKey + ".bonus", 0);
        yaml.set(cloneKey + ".running", false);
        yaml.set(cloneKey + ".endAt", 0L);
        save();
        cancelRunningTask(cloneKey);
    }

    /** Called when the clone machine block is broken/removed. Must unlock any selected Mew. */
    public void onMachineBroken(Location loc) {
        if (loc == null) return;
        String k = key(loc);
        UUID owner = getOwner(k);
        if (owner != null) {
            clearSelection(owner, k);
        } else {
            // still clear persisted fields
            yaml.set(k, null);
            save();
            cancelRunningTask(k);
        }
    }

    public void startClone(Player player, Location machineLoc, Runnable after) {
        if (player == null || machineLoc == null) return;
        String k = key(machineLoc);
        UUID owner = player.getUniqueId();
        ensureOwner(k, owner);
        if (isRunning(k)) {
            player.sendMessage("§c克隆正在进行中，请耐心等待。");
            return;
        }

        UUID mewUuid = getSelectedMew(k);
        if (mewUuid == null) {
            player.sendMessage("§c请先选择一只可克隆的梦幻。");
            return;
        }
        PlayerProfile prof = storage.getProfile(owner);
        PokemonInstance mew = prof == null ? null : prof.findByUuid(mewUuid);
        if (mew == null) {
            player.sendMessage("§c找不到已选择的梦幻，请重新选择。");
            clearSelection(owner, k);
            return;
        }
        if (!"mew".equalsIgnoreCase(mew.speciesId)) {
            player.sendMessage("§c只有梦幻可以用于克隆。");
            return;
        }
        if (mew.mewCloneDisabled || mew.mewCloneAttempts >= 3) {
            player.sendMessage("§c这只梦幻已无法再进行克隆（次数已用尽或已克隆出超梦）。");
            return;
        }

        long now = System.currentTimeMillis();
        long end = now + 60_000L;
        yaml.set(k + ".running", true);
        yaml.set(k + ".endAt", end);
        save();

        int taskId = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try {
                resolveClone(machineLoc);
            } finally {
                if (after != null) after.run();
            }
        }, 20L * 60L).getTaskId();
        runningTaskIds.put(k, taskId);
        player.sendMessage("§d克隆开始... §7(1分钟后将得出结果)\n§7提示：挖掉/破坏克隆机将自动解除锁定。");
    }

    private void cancelRunningTask(String cloneKey) {
        Integer id = runningTaskIds.remove(cloneKey);
        if (id != null) {
            try { Bukkit.getScheduler().cancelTask(id); } catch (Throwable ignored) {}
        }
    }

    /**
     * Resolve clone result. Success -> spawn Mewtwo (legendary) and break the machine.
     * Fail -> spawn low level Ditto and keep machine/mew locked.
     */
    private void resolveClone(Location machineLoc) {
        if (machineLoc == null) return;
        String k = key(machineLoc);
        cancelRunningTask(k);

        UUID owner = getOwner(k);
        if (owner == null) {
            // no owner -> clear state
            yaml.set(k + ".running", false);
            yaml.set(k + ".endAt", 0L);
            save();
            return;
        }
        PlayerProfile prof = storage.getProfile(owner);
        UUID mewUuid = getSelectedMew(k);
        PokemonInstance mew = (prof == null || mewUuid == null) ? null : prof.findByUuid(mewUuid);
        if (mew == null) {
            yaml.set(k + ".running", false);
            yaml.set(k + ".endAt", 0L);
            save();
            return;
        }

        int bonus = getBonus(k);
        int roll = new java.util.Random().nextInt(100);
        boolean success = roll < bonus;

        // consume attempt
        mew.mewCloneAttempts = Math.max(0, mew.mewCloneAttempts) + 1;
        if (success) mew.mewCloneDisabled = true;
        if (mew.mewCloneAttempts >= 3) mew.mewCloneDisabled = true;

        yaml.set(k + ".running", false);
        yaml.set(k + ".endAt", 0L);
        save();

        // spawn results
        Location spawn = machineLoc.clone().add(0.5, 1.0, 0.5);

        if (success) {
            // broadcast + spawn legendary Mewtwo
            Bukkit.broadcastMessage("§5一只传说中的宝可梦§a【§6超梦§a】§5出现了！");
            if (plugin instanceof PokeDemoPlugin pd) {
                pd.spawnLegendaryAt(spawn, "mewtwo", 60, 3, "gen1_mythical");
            }

            // Break machine (will also unlock via onMachineBroken)
            try {
                if (plugin instanceof PokeDemoPlugin pd) {
                    MachineRegistry mr = pd.getMachineRegistry();
                    if (mr != null) mr.remove(machineLoc);
                }
                if (machineLoc.getBlock().getType() == org.bukkit.Material.NOTE_BLOCK) {
                    machineLoc.getBlock().setType(org.bukkit.Material.AIR, false);
                }
            } catch (Throwable ignored) {}

            // unlock mew & clear selection
            clearSelection(owner, k);

        } else {
            // spawn Ditto, keep selection locked
            if (plugin instanceof PokeDemoPlugin pd) {
                int lvl = 10;
                try {
                    lvl = pd.getConfig().getInt("clone.ditto-level", 10);
                } catch (Throwable ignored) {}
                pd.spawnWildAt(spawn, "ditto", lvl);
            }
            Bukkit.broadcastMessage("§d克隆失败... §7一只百变怪出现了。");
        }
    }

    /** Unlock all locked Pokémon for the owner and clear all their clone machine selections. */
    public int unlockAll(UUID owner) {
        if (owner == null) return 0;
        int unlocked = 0;
        PlayerProfile prof = storage.getProfile(owner);
        if (prof != null) {
            for (PokemonInstance p : prof.party) {
                if (p != null && p.uiLocked) { p.uiLocked = false; p.uiLockReason = ""; unlocked++; }
            }
            for (PokemonInstance p : prof.pc) {
                if (p != null && p.uiLocked) { p.uiLocked = false; p.uiLockReason = ""; unlocked++; }
            }
        }

        // Clear all clone machine selections owned by this player
        Set<String> keys = yaml.getKeys(false);
        for (String k : keys) {
            try {
                String s = yaml.getString(k + ".owner");
                if (s == null) continue;
                if (!owner.toString().equals(s)) continue;
                yaml.set(k, null);
                cancelRunningTask(k);
            } catch (Throwable ignored) {}
        }
        save();
        return unlocked;
    }

    private void save() {
        try {
            yaml.save(file);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to save clones.yml: " + e.getMessage());
        }
    }
}
