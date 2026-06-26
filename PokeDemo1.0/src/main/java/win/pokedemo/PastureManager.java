package win.pokedemo;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persistent state for Pasture (breeding ranch) machines.
 * For now we only store selected Pokémon A/B and apply a UI-lock to prevent interactions.
 */
public class PastureManager {

    public enum Slot { A, B }

    private final JavaPlugin plugin;
    private final Storage storage;
    private final Dex dex;

    private final File file;
    private YamlConfiguration yaml;

    /** Pending selection flow: player chooses a Pokémon in PC to assign to a pasture slot. */
    private final ConcurrentHashMap<UUID, PendingSelect> pending = new ConcurrentHashMap<>();

    /** Runtime breeding state per pasture (key -> state). Persisted fields are mirrored to YAML. */
    private final ConcurrentHashMap<String, BreedingState> breeding = new ConcurrentHashMap<>();

    public static final class BreedingState {
        public long startedAtMillis;
        public long lastTickMillis;
        public long lastScanMillis;
        /** carry-over milliseconds to avoid drift when ticking more frequently than 1s */
        public long carryMillis;
        public boolean running;
        public int groups; // acceleration groups (each 9 blocks = 1 group)
        public long totalSeconds; // total duration for the current stage
        public long elapsedSeconds;
        public String status = "等待";
        public String pauseReason = "";

        public long remainingSeconds() {
            long r = totalSeconds - elapsedSeconds;
            return Math.max(0, r);
        }
    }

    public static final class PendingSelect {
        public final String pastureKey;
        public final Slot slot;
        public final long expiresAt;
        public PendingSelect(String pastureKey, Slot slot, long expiresAt) {
            this.pastureKey = pastureKey;
            this.slot = slot;
            this.expiresAt = expiresAt;
        }
    }

    public PastureManager(JavaPlugin plugin, Storage storage, Dex dex) {
        this.plugin = plugin;
        this.storage = storage;
        this.dex = dex;
        this.file = new File(plugin.getDataFolder(), "pastures.yml");
        reload();
    }

    public void reload() {
        this.yaml = YamlConfiguration.loadConfiguration(file);
        breeding.clear();
    }

    private static String key(Location loc) {
        return loc.getWorld().getName() + ":" + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }

    public String keyOf(Location loc) {
        return key(loc);
    }

    public Set<String> allPastureKeys() {
        ConfigurationSection root = yaml;
        if (root == null) return Set.of();
        return root.getKeys(false);
    }

    public UUID getOwner(String pastureKey) {
        try {
            String s = yaml.getString(pastureKey + ".owner");
            return (s == null || s.isBlank()) ? null : UUID.fromString(s);
        } catch (Exception ignored) {
            return null;
        }
    }

    public void ensureOwner(String pastureKey, UUID owner) {
        if (pastureKey == null || owner == null) return;
        if (yaml.getString(pastureKey + ".owner") != null) return;
        yaml.set(pastureKey + ".owner", owner.toString());
        save();
    }

    public UUID getSelected(String pastureKey, Slot slot) {
        try {
            String s = yaml.getString(pastureKey + "." + (slot == Slot.A ? "a" : "b"));
            return (s == null || s.isBlank()) ? null : UUID.fromString(s);
        } catch (Exception ignored) {
            return null;
        }
    }

    /** Get (or create) runtime breeding state for a pasture key. */
    public BreedingState state(String pastureKey) {
        if (pastureKey == null) return null;
        return breeding.computeIfAbsent(pastureKey, k -> {
            BreedingState st = new BreedingState();
            st.startedAtMillis = yaml.getLong(k + ".breeding.startedAt", 0L);
            st.lastTickMillis = System.currentTimeMillis();
            st.lastScanMillis = yaml.getLong(k + ".breeding.lastScan", 0L);
            st.carryMillis = yaml.getLong(k + ".breeding.carryMillis", 0L);
            st.running = yaml.getBoolean(k + ".breeding.running", false);
            st.groups = yaml.getInt(k + ".breeding.groups", 0);
            st.totalSeconds = yaml.getLong(k + ".breeding.totalSeconds", 3600L);
            st.elapsedSeconds = yaml.getLong(k + ".breeding.elapsedSeconds", 0L);
            st.status = yaml.getString(k + ".breeding.status", "等待");
            st.pauseReason = yaml.getString(k + ".breeding.pauseReason", "");
            return st;
        });
    }

    public void saveState(String pastureKey, BreedingState st) {
        if (pastureKey == null || st == null) return;
        yaml.set(pastureKey + ".breeding.startedAt", st.startedAtMillis);
        yaml.set(pastureKey + ".breeding.lastScan", st.lastScanMillis);
        yaml.set(pastureKey + ".breeding.carryMillis", st.carryMillis);
        yaml.set(pastureKey + ".breeding.running", st.running);
        yaml.set(pastureKey + ".breeding.groups", st.groups);
        yaml.set(pastureKey + ".breeding.totalSeconds", st.totalSeconds);
        yaml.set(pastureKey + ".breeding.elapsedSeconds", st.elapsedSeconds);
        yaml.set(pastureKey + ".breeding.status", st.status);
        yaml.set(pastureKey + ".breeding.pauseReason", st.pauseReason);
        save();
    }

    public void beginSelect(UUID playerId, String pastureKey, Slot slot) {
        // Selection is cancelled when the player closes the PC GUI or finishes selection.
        // No short timeout is needed.
        pending.put(playerId, new PendingSelect(pastureKey, slot, Long.MAX_VALUE));
    }

    public PendingSelect getPending(UUID playerId) {
        PendingSelect ps = pending.get(playerId);
        if (ps == null) return null;
        return ps;
    }

    public void setLegality(String pastureKey, boolean legal, String reason) {
        if (pastureKey == null) return;
        yaml.set(pastureKey + ".breeding.legal", legal);
        yaml.set(pastureKey + ".breeding.illegalReason", reason == null ? "" : reason);
        save();
    }

    public boolean isLegal(String pastureKey) {
        return yaml.getBoolean(pastureKey + ".breeding.legal", true);
    }

    public String illegalReason(String pastureKey) {
        return yaml.getString(pastureKey + ".breeding.illegalReason", "");
    }

    public void clearPending(UUID playerId) {
        pending.remove(playerId);
    }

    /**
     * One-key unlock: clears all pasture selections owned by the player and unlocks the selected Pokémon.
     * This is a safety valve in case players forget machine locations.
     */
    public int unlockAll(UUID owner) {
        if (owner == null) return 0;
        int unlocked = 0;
        PlayerProfile prof = storage.getProfile(owner);
        if (prof == null) return 0;

        for (String k : allPastureKeys()) {
            try {
                UUID o = getOwner(k);
                if (o == null || !o.equals(owner)) continue;
                // unlock selected
                UUID a = getSelected(k, Slot.A);
                UUID b = getSelected(k, Slot.B);
                if (a != null) {
                    PokemonInstance p = prof.findByUuid(a);
                    if (p != null && p.uiLocked) { p.uiLocked = false; p.uiLockReason = ""; unlocked++; }
                }
                if (b != null) {
                    PokemonInstance p = prof.findByUuid(b);
                    if (p != null && p.uiLocked) { p.uiLocked = false; p.uiLockReason = ""; unlocked++; }
                }
                yaml.set(k + ".a", null);
                yaml.set(k + ".b", null);
                yaml.set(k + ".breeding.running", false);
                yaml.set(k + ".breeding.status", "等待");
                yaml.set(k + ".breeding.pauseReason", "");
            } catch (Throwable ignored) {}
        }
        save();
        return unlocked;
    }

    public void assign(UUID owner, String pastureKey, Slot slot, UUID pokemonUuid) {
        if (owner == null || pastureKey == null || slot == null) return;
        ensureOwner(pastureKey, owner);

        PlayerProfile prof = storage.getProfile(owner);
        if (prof == null) return;

        // unlock previous
        UUID prev = getSelected(pastureKey, slot);
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
                p.uiLockReason = "牧场繁育";
            }
        }

        yaml.set(pastureKey + "." + (slot == Slot.A ? "a" : "b"), pokemonUuid == null ? null : pokemonUuid.toString());

        // Recompute legality only when selection changes.
        try {
            UUID aSel = getSelected(pastureKey, Slot.A);
            UUID bSel = getSelected(pastureKey, Slot.B);
            if (aSel != null && bSel != null) {
                PokemonInstance pa = prof.findByUuid(aSel);
                PokemonInstance pb = prof.findByUuid(bSel);
                Species sa = pa == null ? null : dex.getSpecies(pa.speciesId);
                Species sb = pb == null ? null : dex.getSpecies(pb.speciesId);
                String reason = legalityReason(pa, pb, sa, sb);
                setLegality(pastureKey, reason == null, reason == null ? "" : reason);
            } else {
                setLegality(pastureKey, true, "");
            }
        } catch (Throwable ignored) {}
        // Reset breeding progress when changing selections.
        BreedingState st = state(pastureKey);
        if (st != null) {
            st.running = false;
            st.elapsedSeconds = 0;
            st.totalSeconds = 3600;
            st.groups = 0;
            st.status = "等待";
            st.pauseReason = "";
            st.carryMillis = 0;
            st.startedAtMillis = 0;
            st.lastScanMillis = 0;
            saveState(pastureKey, st);
        } else {
            save();
        }
        storage.saveProfile(owner);
    }

    /** Return illegal reason or null if legal. */
    private String legalityReason(PokemonInstance a, PokemonInstance b, Species sa, Species sb) {
        if (a == null || b == null || sa == null || sb == null) return "物种数据缺失";
        boolean allowDitto = plugin.getConfig().getBoolean("breeding.allow-ditto", true);
        String ida = a.speciesId == null ? "" : a.speciesId.toLowerCase();
        String idb = b.speciesId == null ? "" : b.speciesId.toLowerCase();
        boolean dittoA = "ditto".equals(ida);
        boolean dittoB = "ditto".equals(idb);
        if ((dittoA || dittoB) && !allowDitto) return "百变怪繁殖已被禁用";
        if (dittoA && dittoB) return "两只百变怪不能繁殖";

        // Egg-group basic legality (legendary / undiscovered / missing egg groups should never breed).
        // IMPORTANT: Ditto does NOT bypass the "undiscovered / no-eggs" rule.
        java.util.List<String> ega = sa.eggGroups() == null ? java.util.List.of() : sa.eggGroups();
        java.util.List<String> egb = sb.eggGroups() == null ? java.util.List.of() : sb.eggGroups();
        if (ega.isEmpty() || egb.isEmpty()) return "未知蛋组";
        if (containsNoEggs(ega) || containsNoEggs(egb)) return "该精灵不能繁殖";

        // Opposite gender unless ditto is involved.
        if (!dittoA && !dittoB) {
            if ("N".equalsIgnoreCase(a.gender) || "N".equalsIgnoreCase(b.gender)) return "无性别需要百变怪";
            if (a.gender == null || b.gender == null) return "性别未知";
            if (a.gender.equalsIgnoreCase(b.gender)) return "需要一公一母";
        } else {
            // Ditto + genderless is allowed; Ditto + Ditto already blocked.
        }

        // Egg group intersect (skip for Ditto)
        if (!dittoA && !dittoB) {
            java.util.Set<String> egA = new java.util.HashSet<>();
            for (Object og : sa.eggGroups() == null ? java.util.List.of() : sa.eggGroups()) {
                if (og == null) continue;
                String g = String.valueOf(og);
                if (!g.isBlank()) egA.add(g.toLowerCase());
            }
            boolean ok = false;
            for (Object og : sb.eggGroups() == null ? java.util.List.of() : sb.eggGroups()) {
                if (og == null) continue;
                String g = String.valueOf(og);
                if (!g.isBlank() && egA.contains(g.toLowerCase())) { ok = true; break; }
            }
            if (!ok) return "蛋组不匹配";
        }
        return null;
    }

    private boolean containsNoEggs(java.util.List<String> eggGroups) {
        for (String g : eggGroups) {
            if (g == null) continue;
            String s = g.toLowerCase(java.util.Locale.ROOT);
            if (s.contains("undiscovered") || s.contains("no-eggs") || s.contains("noeggs") || s.contains("unknown")) return true;
            if (s.contains("未发现") || s.contains("不可繁殖")) return true;
        }
        return false;
    }

    /** Called when a pasture block is broken: unlock any selected Pokémon and clear state. */
    public void onPastureBroken(Location loc) {
        if (loc == null) return;
        String k = key(loc);
        UUID owner = getOwner(k);
        if (owner != null) {
            PlayerProfile prof = storage.getProfile(owner);
            if (prof != null) {
                UUID a = getSelected(k, Slot.A);
                UUID b = getSelected(k, Slot.B);
                unlockIfPresent(prof, a);
                unlockIfPresent(prof, b);
                storage.saveProfile(owner);
            }
        }

        yaml.set(k, null);
        save();
        breeding.remove(k);
    }

    private void unlockIfPresent(PlayerProfile prof, UUID u) {
        if (prof == null || u == null) return;
        PokemonInstance p = prof.findByUuid(u);
        if (p != null) {
            p.uiLocked = false;
            p.uiLockReason = "";
        }
    }

    private void save() {
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            yaml.save(file);
        } catch (Exception e) {
            plugin.getLogger().warning("[PokeDemo] Failed to save pastures.yml: " + e.getMessage());
        }
    }
}
