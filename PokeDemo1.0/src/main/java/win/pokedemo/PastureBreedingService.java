package win.pokedemo;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.DaylightDetector;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Breeding core for Pasture machines.
 *
 * Rules (foundation):
 * - Legal check: eggGroups intersect + opposite gender, OR Ditto with non-undiscovered.
 * - Timer runs at day. At night, it runs only if a non-inverted Daylight Detector exists nearby.
 * - Environment acceleration: scan a 9x9 area (radius 4) and height +/- 3.
 *   Count blocks matching the parents' type->block mapping (see egg_hatch_blocks.yml).
 *   Each 9 blocks = 1 group. Groups affect total duration stages.
 */
public final class PastureBreedingService implements Runnable {

    private static final java.util.Map<String, String> INCENSE_BABY_BY_PARENT = java.util.Map.ofEntries(
            java.util.Map.entry("snorlax", "munchlax"),
            java.util.Map.entry("wobbuffet", "wynaut"),
            java.util.Map.entry("chansey", "happiny"),
            java.util.Map.entry("blissey", "happiny"),
            java.util.Map.entry("marill", "azurill"),
            java.util.Map.entry("azumarill", "azurill"),
            java.util.Map.entry("roselia", "budew"),
            java.util.Map.entry("roserade", "budew"),
            java.util.Map.entry("chimecho", "chingling"),
            java.util.Map.entry("mr_mime", "mimejr"),
            java.util.Map.entry("mrmime", "mimejr"),
            java.util.Map.entry("sudowoodo", "bonsly"),
            java.util.Map.entry("mantine", "mantyke")
    );

    private static final java.util.Map<String, String> REQUIRED_INCENSE_BY_BABY = java.util.Map.ofEntries(
            java.util.Map.entry("munchlax", "full_incense"),
            java.util.Map.entry("wynaut", "lax_incense"),
            java.util.Map.entry("happiny", "luck_incense"),
            java.util.Map.entry("azurill", "sea_incense"),
            java.util.Map.entry("budew", "rose_incense"),
            java.util.Map.entry("chingling", "pure_incense"),
            java.util.Map.entry("mimejr", "odd_incense"),
            java.util.Map.entry("bonsly", "rock_incense"),
            java.util.Map.entry("mantyke", "wave_incense")
    );

    private final JavaPlugin plugin;
    private final Storage storage;
    private final Dex dex;
    private final MachineRegistry machines;
    private final PastureManager pastures;

    // Scan rules
    private static final int RADIUS = 4; // 9x9
    private static final int DY = 3;
    private static final long ACTIVE_SCAN_INTERVAL_MS = 60_000L;
    // When the pasture is idle (no parents selected / illegal / egg ready), scan far less often.
    private static final long IDLE_SCAN_INTERVAL_MS = 300_000L; // 5 min

    public PastureBreedingService(JavaPlugin plugin, Storage storage, Dex dex, MachineRegistry machines, PastureManager pastures) {
        this.plugin = plugin;
        this.storage = storage;
        this.dex = dex;
        this.machines = machines;
        this.pastures = pastures;
    }

    /** Expose key parsing for GUIs. */
    public Location parseKeyPublic(String key) {
        return parseKey(key);
    }

    /**
     * Claim a finished egg using the given ball id, creating an "egg Pokémon" instance.
     * Egg will go to Party first, then PC.
     */
    public boolean claimEggWithBall(UUID playerId, String pastureKey, String ballId) {
        try {
            if (playerId == null || pastureKey == null || ballId == null) return false;
            UUID owner = pastures.getOwner(pastureKey);
            if (owner == null || !owner.equals(playerId)) return false;

            PastureManager.BreedingState st = pastures.state(pastureKey);
            if (st == null) return false;
            if (st.running) return false;
            if (st.remainingSeconds() > 0) return false;
            if (st.status == null || !st.status.contains("可产蛋")) return false;

            PlayerProfile prof = storage.getProfile(owner);
            if (prof == null) return false;

            UUID aId = pastures.getSelected(pastureKey, PastureManager.Slot.A);
            UUID bId = pastures.getSelected(pastureKey, PastureManager.Slot.B);
            if (aId == null || bId == null) return false;
            PokemonInstance a = prof.findByUuid(aId);
            PokemonInstance b = prof.findByUuid(bId);
            if (a == null || b == null) return false;

            Species sa = dex.getSpecies(a.speciesId);
            Species sb = dex.getSpecies(b.speciesId);
            if (sa == null || sb == null) return false;

            // Re-check legality (cheap)
            String illegal = legalityReason(a, b, sa, sb);
            if (illegal != null) return false;

            // Determine child species from the non-Ditto parent, including incense-gated baby exceptions.
            String ida = a.speciesId == null ? "" : a.speciesId.toLowerCase();
            String idb = b.speciesId == null ? "" : b.speciesId.toLowerCase();
            PokemonInstance nonDittoParent = "ditto".equals(ida) ? b : a;
            String parentId = nonDittoParent == null ? ("ditto".equals(ida) ? b.speciesId : a.speciesId) : nonDittoParent.speciesId;
            String childId = resolveChildSpeciesId(parentId, a, b);
            Species childSpecies = dex.getSpeciesFlexible(childId);
            if (childSpecies == null) childSpecies = "ditto".equals(ida) ? sb : sa;

            // Create egg Pokémon at level 1, apply inheritance now but hide until hatch.
            PokemonInstance egg = PokemonInstance.createOwned(childSpecies, 1, dex);
            egg.isEgg = true;
            egg.eggStepsTotal = Math.max(1, plugin.getConfig().getInt("breeding.egg.hatch-steps", 1000));
            egg.eggStepsRemaining = egg.eggStepsTotal;
            egg.eggBallId = ballId;

            // Apply inheritance (Everstone/Destiny Knot/Power items). This sets IV/Nature/Gender etc.
            BreedingInheritance.applyInheritance(egg, a, b, dex);

            // Put into party/pc
            prof.depositToPartyOrPc(egg);
            storage.saveProfile(owner);

            // Reset breeding state to continue next cycle
            st.elapsedSeconds = 0;
            st.startedAtMillis = System.currentTimeMillis();
            st.status = "等待";
            st.pauseReason = "";
            st.running = false; // will be resumed on next scan if conditions allow
            pastures.saveState(pastureKey, st);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private String normalizeSpeciesId(String id) {
        return id == null ? "" : id.toLowerCase(java.util.Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    private boolean parentHasHeldItem(PokemonInstance p, String itemId) {
        return p != null && p.heldItemId != null && itemId != null && itemId.equalsIgnoreCase(p.heldItemId);
    }

    private String resolveIncenseBabySpeciesId(String parentId, PokemonInstance a, PokemonInstance b) {
        String pid = normalizeSpeciesId(parentId);
        String baby = INCENSE_BABY_BY_PARENT.get(pid);
        if (baby == null) return null;
        String incense = REQUIRED_INCENSE_BY_BABY.get(baby);
        if (incense == null) return baby;
        if (parentHasHeldItem(a, incense) || parentHasHeldItem(b, incense)) return baby;

        return switch (baby) {
            case "munchlax" -> "snorlax";
            case "wynaut" -> "wobbuffet";
            case "happiny" -> "chansey";
            case "azurill" -> "marill";
            case "budew" -> "roselia";
            case "chingling" -> "chimecho";
            case "mimejr" -> "mr_mime";
            case "bonsly" -> "sudowoodo";
            case "mantyke" -> "mantine";
            default -> baby;
        };
    }

    private String resolveChildSpeciesId(String parentId, PokemonInstance a, PokemonInstance b) {
        String incenseResolved = resolveIncenseBabySpeciesId(parentId, a, b);
        if (incenseResolved != null && !incenseResolved.isBlank()) return incenseResolved;
        return dex.getBabySpeciesId(parentId);
    }

    @Override
    public void run() {
        try {
            tick();
        } catch (Throwable t) {
            plugin.getLogger().warning("[PokeDemo] PastureBreedingService tick error: " + t.getMessage());
        }
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (String key : pastures.allPastureKeys()) {
            if (key == null || key.isBlank()) continue;
            Location loc = parseKey(key);
            if (loc == null) continue;

            // Ensure block is still a pasture machine.
            if (loc.getWorld() == null) continue;
            Block b = loc.getBlock();
            if (b.getType() != Material.NOTE_BLOCK || machines.get(loc) != MachineType.PASTURE) {
                // stale entry: clear it safely
                pastures.onPastureBroken(loc);
                continue;
            }

            PastureManager.BreedingState st = pastures.state(key);
            if (st == null) continue;

            // Throttle scans. Heavy scans (sensor + blocks) only make sense when parents are selected and legal.
            UUID aIdQuick = pastures.getSelected(key, PastureManager.Slot.A);
            UUID bIdQuick = pastures.getSelected(key, PastureManager.Slot.B);
            boolean parentsSelected = (aIdQuick != null && bIdQuick != null);
            boolean legal = parentsSelected && pastures.isLegal(key);
            boolean eggReady = (st.remainingSeconds() <= 0) && (st.status != null && st.status.contains("可产蛋"));

            long interval = (legal && !eggReady) ? ACTIVE_SCAN_INTERVAL_MS : IDLE_SCAN_INTERVAL_MS;

            // If we're actively running (or paused by night), keep active cadence.
            if (st.running) interval = ACTIVE_SCAN_INTERVAL_MS;

            boolean doScan = (now - st.lastScanMillis) >= interval;
            if (doScan) {
                st.lastScanMillis = now;
                scanAndUpdate(loc, key, st);
                pastures.saveState(key, st);
            }

            // Progress time if running and not paused.
            long dtMs = Math.max(0, now - st.lastTickMillis);
            st.lastTickMillis = now;
            if (st.running && (st.pauseReason == null || st.pauseReason.isBlank())) {
                // Avoid drift when this task runs more frequently than 1s by carrying milliseconds.
                long accum = st.carryMillis + dtMs;
                long add = accum / 1000L;
                st.carryMillis = accum % 1000L;
                if (add > 0) {
                    st.elapsedSeconds = Math.min(st.totalSeconds, st.elapsedSeconds + add);
                    if (st.elapsedSeconds >= st.totalSeconds) {
                        st.running = false;
                        st.status = "§a可产蛋";
                        st.carryMillis = 0;
                    }
                    pastures.saveState(key, st);
                }
            } else {
                // When paused/not running, don't accumulate leftover millis.
                st.carryMillis = 0;
            }
        }
    }

    private void scanAndUpdate(Location loc, String key, PastureManager.BreedingState st) {
        UUID owner = pastures.getOwner(key);
        if (owner == null) {
            st.running = false;
            st.status = "等待";
            st.pauseReason = "";
            return;
        }
        PlayerProfile prof = storage.getProfile(owner);
        if (prof == null) {
            st.running = false;
            st.status = "等待";
            st.pauseReason = "";
            return;
        }

        UUID aId = pastures.getSelected(key, PastureManager.Slot.A);
        UUID bId = pastures.getSelected(key, PastureManager.Slot.B);
        if (aId == null || bId == null) {
            st.running = false;
            st.status = "等待选择精灵";
            st.pauseReason = "";
            st.elapsedSeconds = 0;
            st.totalSeconds = 3600;
            return;
        }
        PokemonInstance a = prof.findByUuid(aId);
        PokemonInstance b = prof.findByUuid(bId);
        if (a == null || b == null) {
            st.running = false;
            st.status = "等待选择精灵";
            st.pauseReason = "";
            st.elapsedSeconds = 0;
            st.totalSeconds = 3600;
            return;
        }

        Species sa = dex.getSpecies(a.speciesId);
        Species sb = dex.getSpecies(b.speciesId);
        if (sa == null || sb == null) {
            st.running = false;
            st.status = "§c物种数据缺失";
            st.pauseReason = "";
            return;
        }

        // legality is refreshed only when A/B selection changes.
        if (!pastures.isLegal(key)) {
            String illegal = pastures.illegalReason(key);
            st.running = false;
            st.status = "§c不合法：" + (illegal == null ? "" : illegal);
            st.pauseReason = "";
            st.elapsedSeconds = 0;
            st.totalSeconds = 3600;
            return;
        }

        // time pause condition: day ok; night needs daylight detector.
        st.pauseReason = "";
        if (!isDay(loc.getWorld())) {
            boolean hasSensor = hasNonInvertedDaylightDetector(loc);
            if (!hasSensor) st.pauseReason = "§e夜晚无阳光探测器，暂停计时";
        }

        // compute groups based on environment + parents types.
        int groups = computeGroups(loc, sa, sb);
        st.groups = groups;

        // stage duration: base 60 min, cap reduction 30 min.
        long oldTotal = st.totalSeconds;
        long oldElapsed = st.elapsedSeconds;
        long newTotal = stageTotalSeconds(groups);
        // Keep elapsed real-time; recompute remaining = newTotal - elapsed.
        st.totalSeconds = newTotal;
        st.elapsedSeconds = Math.min(oldElapsed, newTotal);
        // If total changed and elapsed was beyond newTotal, clamp.

        // start running if not finished.
        if (st.elapsedSeconds >= st.totalSeconds) {
            st.running = false;
            st.status = "§a可产蛋";
        } else {
            if (st.startedAtMillis == 0) st.startedAtMillis = System.currentTimeMillis();
            st.running = true;
            st.status = "§d繁殖中";
        }

        // If total changed, adjust elapsed proportionally by real-time already elapsed since start.
        // (We intentionally keep elapsed as "real seconds passed"; do not allow repeated reductions exploits.)
        if (oldTotal != newTotal) {
            // no-op: elapsed already represents real time.
        }
    }

    private String legalityReason(PokemonInstance a, PokemonInstance b, Species sa, Species sb) {
        String ida = (a.speciesId == null) ? "" : a.speciesId.toLowerCase();
        String idb = (b.speciesId == null) ? "" : b.speciesId.toLowerCase();
        boolean dittoA = "ditto".equals(ida);
        boolean dittoB = "ditto".equals(idb);
        if (dittoA && dittoB) return "百变怪不能互相繁殖";

        List<String> ega = sa.eggGroups() == null ? List.of() : sa.eggGroups();
        List<String> egb = sb.eggGroups() == null ? List.of() : sb.eggGroups();
        if (ega.isEmpty() || egb.isEmpty()) return "未知蛋组";

        // Undiscovered / no-eggs group
        if (containsNoEggs(ega) || containsNoEggs(egb)) return "该精灵不能繁殖";

        if (dittoA || dittoB) {
            // Ditto with non-undiscovered is ok.
            return null;
        }

        // same egg group
        boolean share = false;
        for (String g : ega) {
            if (g == null) continue;
            for (String h : egb) {
                if (h == null) continue;
                if (g.equalsIgnoreCase(h)) { share = true; break; }
            }
            if (share) break;
        }
        if (!share) return "蛋组不匹配";

        // gender
        if ("N".equalsIgnoreCase(a.gender) || "N".equalsIgnoreCase(b.gender)) return "无性别需要百变怪";
        if (a.gender.equalsIgnoreCase(b.gender)) return "需要一公一母";
        return null;
    }

    private boolean containsNoEggs(List<String> eggGroups) {
        for (String g : eggGroups) {
            if (g == null) continue;
            String s = g.toLowerCase();
            if (s.contains("undiscovered") || s.contains("no-eggs") || s.contains("noeggs") || s.contains("unknown")) return true;
            if (s.contains("未发现") || s.contains("不可繁殖")) return true;
        }
        return false;
    }

    private boolean isDay(World w) {
        long t = w.getTime() % 24000L;
        return t < 12300L; // simple day/night split
    }

    private boolean hasNonInvertedDaylightDetector(Location center) {
        World w = center.getWorld();
        if (w == null) return false;
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                for (int dy = -DY; dy <= DY; dy++) {
                    Block b = w.getBlockAt(cx + dx, cy + dy, cz + dz);
                    if (b.getType() != Material.DAYLIGHT_DETECTOR) continue;
                    BlockData bd = b.getBlockData();
                    if (bd instanceof DaylightDetector det) {
                        if (!det.isInverted()) return true; // default bright (non-inverted)
                    } else {
                        // fallback: treat as valid
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private int computeGroups(Location center, Species sa, Species sb) {
        World w = center.getWorld();
        if (w == null) return 0;

        // Determine "breeding environment types": union of parents' types.
        java.util.LinkedHashSet<String> typeSet = new java.util.LinkedHashSet<>();
        if (sa.types() != null) for (String t : sa.types()) if (t != null && !t.isBlank()) typeSet.add(t);
        if (sb.types() != null) for (String t : sb.types()) if (t != null && !t.isBlank()) typeSet.add(t);
        if (typeSet.isEmpty()) return 0;

        java.util.List<String> types = new java.util.ArrayList<>(typeSet);
        String t1 = types.get(0);
        String t2 = types.size() >= 2 ? types.get(1) : null;
        boolean dual = (t2 != null);

        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();

        int count1 = 0;
        int count2 = 0;
        Set<Material> wanted1 = new HashSet<>();
        addTypeMaterials(wanted1, java.util.List.of(t1));
        Set<Material> wanted2 = new HashSet<>();
        if (dual) addTypeMaterials(wanted2, java.util.List.of(t2));

        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                for (int dy = -DY; dy <= DY; dy++) {
                    Block b = w.getBlockAt(cx + dx, cy + dy, cz + dz);
                    Material mt = b.getType();
                    if (wanted1.contains(mt)) count1++;
                    if (dual && wanted2.contains(mt)) count2++;
                }
            }
        }

        int groups1 = Math.max(0, count1 / 9);
        if (!dual) {
            return Math.min(4, groups1); // single-type can reach full speed
        }
        int groups2 = Math.max(0, count2 / 9);
        // dual-type: each type contributes at most 2 groups
        groups1 = Math.min(2, groups1);
        groups2 = Math.min(2, groups2);
        return Math.min(4, groups1 + groups2);
    }

    private void addTypeMaterials(Set<Material> out, List<String> types) {
        if (types == null) return;
        for (String t : types) {
            if (t == null) continue;
            switch (t.toLowerCase()) {
                case "normal", "一般" -> out.add(Material.WHITE_WOOL);
                case "flying", "飞行" -> out.add(Material.GLASS);
                case "fire", "火" -> out.add(Material.NETHERRACK);
                case "psychic", "超能力" -> out.add(Material.END_STONE);
                case "water", "水" -> out.add(Material.CLAY);
                case "bug", "虫" -> out.add(Material.SAND);
                case "electric", "电" -> out.add(Material.REDSTONE_BLOCK);
                case "rock", "岩石" -> out.add(Material.COBBLESTONE);
                case "grass", "草" -> { out.add(Material.GRASS_BLOCK); out.add(Material.HAY_BLOCK); }
                case "ghost", "幽灵" -> out.add(Material.SOUL_SAND);
                case "ice", "冰" -> { out.add(Material.ICE); out.add(Material.PACKED_ICE); out.add(Material.BLUE_ICE); }
                case "dragon", "龙" -> out.add(Material.MAGMA_BLOCK);
                case "fighting", "格斗" -> out.add(Material.BONE_BLOCK);
                case "dark", "恶" -> out.add(Material.COAL_BLOCK);
                case "poison", "毒" -> out.add(Material.SLIME_BLOCK);
                case "steel", "钢" -> { out.add(Material.IRON_BLOCK); out.add(Material.COPPER_BLOCK); }
                case "ground", "地面" -> out.add(Material.STONE);
                case "fairy", "妖精" -> out.add(Material.GLOWSTONE);
                default -> {}
            }
        }
    }

    /** Base 60min. Each group reduces 7m30s. 0->60m, 1->52m30s, 2->45m, 3->37m30s, 4+->30m. */
    private long stageTotalSeconds(int groups) {
        if (groups <= 0) return 3600L;
        if (groups == 1) return 3150L; // 52:30
        if (groups == 2) return 2700L; // 45:00
        if (groups == 3) return 2250L; // 37:30
        return 1800L; // 30:00
    }

    private Location parseKey(String key) {
        try {
            // world:x,y,z
            int idx = key.indexOf(':');
            if (idx <= 0) return null;
            String wName = key.substring(0, idx);
            String rest = key.substring(idx + 1);
            String[] parts = rest.split(",");
            if (parts.length != 3) return null;
            int x = Integer.parseInt(parts[0]);
            int y = Integer.parseInt(parts[1]);
            int z = Integer.parseInt(parts[2]);
            World w = Bukkit.getWorld(wName);
            if (w == null) return null;
            return new Location(w, x, y, z);
        } catch (Exception ignored) {
            return null;
        }
    }
}
