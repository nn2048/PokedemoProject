
package win.pokedemo;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Storage {
    private final JavaPlugin plugin;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Map<UUID, PlayerProfile> profiles = new ConcurrentHashMap<>();

    // GUI helper state (not persisted)
    private final Map<UUID, PendingRelease> pendingPcRelease = new ConcurrentHashMap<>();

    public static final class PendingRelease {
        public final int index;
        public final long expiresAtMillis;

        public PendingRelease(int index, long expiresAtMillis) {
            this.index = index;
            this.expiresAtMillis = expiresAtMillis;
        }
    }

    /**
     * Set a pending release confirmation for a PC slot index.
     * A second right-click within the window will confirm the release.
     */
    public void setPendingPcRelease(UUID playerId, int index, long windowMillis) {
        pendingPcRelease.put(playerId, new PendingRelease(index, System.currentTimeMillis() + windowMillis));
    }

    public Integer getPendingPcRelease(UUID playerId) {
        PendingRelease pr = pendingPcRelease.get(playerId);
        if (pr == null) return null;
        if (System.currentTimeMillis() > pr.expiresAtMillis) {
            pendingPcRelease.remove(playerId);
            return null;
        }
        return pr.index;
    }

    public void clearPendingPcRelease(UUID playerId) {
        pendingPcRelease.remove(playerId);
    }

    private final Path dataFolder;
    private final Path profilesFolder;

    public Storage(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dataFolder = plugin.getDataFolder().toPath();
        this.profilesFolder = dataFolder.resolve("profiles");
        try {
            if (!Files.exists(profilesFolder)) Files.createDirectories(profilesFolder);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public PlayerProfile getProfile(UUID uuid) {
        return profiles.computeIfAbsent(uuid, PlayerProfile::new);
    }

    /**
     * Demo: 标记资料已变更。当前实现为 no-op，因为自动保存会保存全部玩家资料。
     */
    public void markDirty(UUID uuid) {
        // no-op
    }

    public void loadAll() {
        if (!Files.exists(profilesFolder)) return;
        try {
            Files.list(profilesFolder).filter(p -> p.toString().endsWith(".json")).forEach(p -> {
                try {
                    UUID id = UUID.fromString(p.getFileName().toString().replace(".json",""));
                    PlayerProfile prof = loadProfile(id);
                    if (prof != null) profiles.put(id, prof);
                } catch (Exception ignored) {}
            });
        } catch (IOException e) {
            plugin.getLogger().warning("Storage loadAll failed: " + e.getMessage());
        }
    }

    private PlayerProfile loadProfile(UUID id) {
        Path file = profilesFolder.resolve(id.toString() + ".json");
        if (!Files.exists(file)) return new PlayerProfile(id);
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Type type = new TypeToken<PlayerProfile>(){}.getType();
            PlayerProfile prof = gson.fromJson(r, type);
            if (prof == null) prof = new PlayerProfile(id);
            if (prof.playerId == null) prof.playerId = id;
            if (prof.party == null) prof.party = new ArrayList<>();
            if (prof.pc == null) prof.pc = new ArrayList<>();
            if (prof.dexCaught == null) prof.dexCaught = new java.util.HashSet<>();

            // Normalize old saves:
            // - remove null slots
            // - ensure totalExp matches level floor (prevents negative "current exp")
            // - resolve move runtime objects if present
            normalizeProfile(prof);
            return prof;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load profile " + id + ": " + e.getMessage());
            return new PlayerProfile(id);
        }
    }

    private void normalizeProfile(PlayerProfile prof) {
        if (prof == null) return;
        // Remove null entries introduced by older versions or manual edits.
        if (prof.party != null) prof.party.removeIf(Objects::isNull);
        if (prof.pc != null) prof.pc.removeIf(Objects::isNull);
        if (prof.dexCaught == null) prof.dexCaught = new java.util.HashSet<>();

        Dex dex = null;
        if (plugin instanceof PokeDemoPlugin pd) {
            dex = pd.getDex();
        } else if (PokeDemoPlugin.INSTANCE != null) {
            dex = PokeDemoPlugin.INSTANCE.getDex();
        }

        for (PokemonInstance p : concat(prof.party, prof.pc)) {
            if (p == null) continue;
            if (p.uuid == null) p.uuid = UUID.randomUUID();

            // Nature migration:
            // Older saves may not have nature recorded (null/blank), which shows as "?" in GUIs (e.g., Pasture).
            // Assign a deterministic nature so it stays stable across restarts.
            if (p.nature == null || p.nature.isBlank()) {
                try {
                    long bits = p.uuid.getLeastSignificantBits() ^ p.uuid.getMostSignificantBits();
                    int idx = (int) Math.floorMod(bits, Nature.values().length);
                    p.nature = Nature.values()[idx].name();
                } catch (Throwable ignored) {
                    p.nature = Nature.HARDY.name();
                }
            }

            Species s = (dex == null) ? null : dex.getSpecies(p.speciesId);
            if (s != null) {
                // Floor total exp to minimum exp for current level
                long minExp = ExpCurve.totalExpAtLevel(s.expGroup(), p.level);
                if (p.totalExp < minExp) p.totalExp = minExp;
                // Clamp to max exp at level 100
                long maxExp = ExpCurve.totalExpAtLevel(s.expGroup(), 100);
                if (p.totalExp > maxExp) p.totalExp = maxExp;

                // Ensure stored level is consistent with totalExp (fixes cases where level was edited)
                int derived = ExpCurve.levelForTotalExp(s.expGroup(), p.totalExp);
                if (derived != p.level) p.level = derived;

                // Gender migration:
                // Older saves often had gender missing or defaulted to "N".
                // If the species is not genderless, assign a deterministic gender so it doesn't flip across restarts.
                try {
                    String g = (p.gender == null) ? "" : p.gender.trim();
                    double mr = s.maleRatio();
                    if ((g.isEmpty() || g.equalsIgnoreCase("N") || g.equals("-")) && mr >= 0.0) {
                        mr = Math.max(0.0, Math.min(1.0, mr));
                        // Deterministic roll based on UUID bits (stable) but respecting species maleRatio.
                        long bits = p.uuid.getLeastSignificantBits() ^ p.uuid.getMostSignificantBits();
                        // Map to [0,1) using 53 bits (double mantissa).
                        double r = ((bits >>> 11) & ((1L << 53) - 1)) / (double) (1L << 53);
                        p.gender = (r < mr) ? "M" : "F";
                    }
                    if (mr < 0.0) {
                        // Genderless species always "N"
                        p.gender = "N";
                    }
                } catch (Throwable ignored) {}
            }


            // Ability migration: assign an ability if missing
            if (p.abilityId == null || p.abilityId.isBlank()) {
                try {
                    String ab = (dex == null) ? null : dex.pickAbilityIdForSpecies(p.speciesId, false);
                    p.abilityId = ab;
                } catch (Throwable ignored) {}
            }

            // Ensure moves list exists and has 4 entries
            if (p.moves == null) p.moves = new ArrayList<>();
            while (p.moves.size() < 4) {
                MoveSlot s2 = new MoveSlot();
                s2.moveId = "tackle";
                s2.basePp = 35;
                s2.ppUpsUsed = 0;
                s2.recalcMaxPp();
                s2.pp = s2.maxPp;
                p.moves.add(s2);
            }

            // Fix PP defaults for older saves
            if (dex != null) {
                for (MoveSlot ms : p.moves) {
                    if (ms == null) continue;
                    Move mm = dex.getMove(ms.moveId);
                    if (mm != null) {
                        if (ms.basePp <= 0) ms.basePp = mm.pp();
                        if (ms.ppUpsUsed < 0) ms.ppUpsUsed = 0;
                        ms.recalcMaxPp();
                        if (ms.pp <= 0) ms.pp = ms.maxPp;
                    } else {
                        if (ms.basePp <= 0) ms.basePp = 35;
                        if (ms.ppUpsUsed < 0) ms.ppUpsUsed = 0;
                        ms.recalcMaxPp();
                        if (ms.pp <= 0) ms.pp = ms.maxPp;
                    }
                }
            }
        }
    }

    private List<PokemonInstance> concat(List<PokemonInstance> a, List<PokemonInstance> b) {
        List<PokemonInstance> out = new ArrayList<>();
        if (a != null) out.addAll(a);
        if (b != null) out.addAll(b);
        return out;
    }

    public void saveProfile(UUID id) {
        PlayerProfile prof = getProfile(id);
        Path file = profilesFolder.resolve(id.toString() + ".json");
        try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            gson.toJson(prof, w);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to save profile " + id + ": " + e.getMessage());
        }
    }

    public void saveAll() {
        for (UUID id : profiles.keySet()) {
            saveProfile(id);
        }
    }

    /**
     * Remove illegal/unknown moves from a player's party/pc and refill with available moves from learnset.
     * This is mainly used when switching to Gen1-only datasets so old Gen2+ moves (e.g. Synthesis, Sweet Scent)
     * don't silently degrade into placeholder damage.
     *
     * @return number of pokemon modified
     */
    public int cleanseMoves(UUID playerId, Dex dex) {
        if (playerId == null || dex == null) return 0;
        PlayerProfile prof = getProfile(playerId);
        if (prof == null) return 0;

        boolean gen1Only = dex.isGen1OnlyMode();

        int changed = 0;
        for (PokemonInstance p : concat(prof.party, prof.pc)) {
            if (p == null) continue;
            Species sp = dex.getSpecies(p.speciesId);
            boolean touched = false;

            if (p.moves == null) p.moves = new ArrayList<>();

            // keep valid existing moves
            List<String> keep = new ArrayList<>();
            for (MoveSlot ms : p.moves) {
                if (ms == null) continue;
                String mid = (ms.moveId == null ? "" : ms.moveId.toLowerCase(Locale.ROOT));
                if (mid.isBlank()) continue;
                Move mv = dex.getMove(mid);
                if (mv == null) {
                    touched = true;
                    continue;
                }
                if (gen1Only && (mv.num() < 1 || mv.num() > 165)) {
                    touched = true;
                    continue;
                }
                if (!keep.contains(mid)) keep.add(mid);
            }

            // refill from learnset up to current level (take most recent 4)
            if (sp != null) {
                List<String> learned = new ArrayList<>();
                for (var en : sp.levelUpMovesSafe().entrySet()) {
                    if (en.getKey() > p.level) continue;
                    for (String mid : en.getValue()) {
                        if (mid == null) continue;
                        String id = mid.toLowerCase(Locale.ROOT);
                        Move mv = dex.getMove(id);
                        if (mv == null) continue;
                        if (gen1Only && (mv.num() < 1 || mv.num() > 165)) continue;
                        if (!learned.contains(id)) learned.add(id);
                    }
                }
                // ensure learned moves are considered after kept moves
                for (String id : learned) {
                    if (!keep.contains(id)) keep.add(id);
                }
            }

            // final pick = last 4
            if (keep.isEmpty()) {
                keep.add("tackle");
                keep.add("growl");
                touched = true;
            }
            int from = Math.max(0, keep.size() - 4);
            List<String> finalIds = new ArrayList<>(keep.subList(from, keep.size()));
            while (finalIds.size() < 4) finalIds.add(finalIds.get(0));

            // rebuild MoveSlots
            List<MoveSlot> rebuilt = new ArrayList<>();
            for (String id : finalIds) {
                Move mv = dex.getMoveOrPlaceholder(id);
                MoveSlot ms = new MoveSlot();
                ms.moveId = mv.id();
                ms.basePp = mv.pp();
                ms.ppUpsUsed = 0;
                ms.recalcMaxPp();
                ms.pp = ms.maxPp;
                rebuilt.add(ms);
            }

            // compare
            if (!sameMoves(p.moves, rebuilt)) {
                p.moves = rebuilt;
                touched = true;
            }

            if (touched) changed++;
        }

        if (changed > 0) {
            saveProfile(playerId);
        }
        return changed;
    }

    private boolean sameMoves(List<MoveSlot> a, List<MoveSlot> b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            MoveSlot x = a.get(i);
            MoveSlot y = b.get(i);
            String xi = (x == null || x.moveId == null) ? "" : x.moveId.toLowerCase(Locale.ROOT);
            String yi = (y == null || y.moveId == null) ? "" : y.moveId.toLowerCase(Locale.ROOT);
            if (!xi.equals(yi)) return false;
        }
        return true;
    }
}
