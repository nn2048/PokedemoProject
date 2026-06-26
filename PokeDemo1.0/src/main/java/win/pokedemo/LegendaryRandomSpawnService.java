package win.pokedemo;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/**
 * Periodic random legendary spawns (e.g., Mew in Jungles).
 *
 * Design (per user request):
 *  1) Each interval, roll once with probability 1/denominator.
 *  2) Only if roll passes, check whether any online player is in (or near) a matching biome.
 *  3) Pick a random eligible player and spawn the legendary near them.
 *
 * Extensible via config: legendary.random.entries (a list of maps).
 */
public class LegendaryRandomSpawnService {

    private final PokeDemoPlugin plugin;
    private final SpawnManager spawns;
    private final Dex dex;

    private int taskId = -1;

    public LegendaryRandomSpawnService(PokeDemoPlugin plugin, Dex dex, SpawnManager spawns) {
        this.plugin = plugin;
        this.dex = dex;
        this.spawns = spawns;
    }

    public void start() {
        stop();
        if (!plugin.getConfig().getBoolean("legendary.enabled", true)) return;
        if (!plugin.getConfig().getBoolean("legendary.random.enabled", true)) return;

        int minutes = Math.max(1, plugin.getConfig().getInt("legendary.random.interval-minutes", 60));
        int denom = Math.max(1, plugin.getConfig().getInt("legendary.random.roll-denominator", 12));
        java.util.List<LegendaryEntry> entries = loadEntries();
        long period = minutes * 60L * 20L;

        // Start after one period to avoid spawning immediately at boot.
        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::tick, period, period);
        try {
            plugin.getLogger().info("[PokeDemo] Legendary random spawns enabled: interval=" + minutes
                    + "m, roll=1/" + denom + ", entries=" + entries.size() + ", defaultMew="
                    + (entries.stream().anyMatch(e -> "mew".equalsIgnoreCase(e.speciesId)) ? "yes" : "no"));
        } catch (Throwable ignored) {}
    }

    public void stop() {
        if (taskId != -1) {
            try { Bukkit.getScheduler().cancelTask(taskId); } catch (Throwable ignored) {}
            taskId = -1;
        }
    }

    private void tick() {
        try {
            if (!plugin.isEnabled()) return;
            if (!plugin.getConfig().getBoolean("legendary.enabled", true)) return;
            if (!plugin.getConfig().getBoolean("legendary.random.enabled", true)) return;

            // Step 1: roll first (1/denominator).
            int denom = Math.max(1, plugin.getConfig().getInt("legendary.random.roll-denominator", 12));
            if (Util.RND.nextInt(denom) != 0) return;

            // Step 2: find eligible entries + players.
            List<LegendaryEntry> entries = loadEntries();
            if (entries.isEmpty()) return;

            List<Pick> picks = new ArrayList<>();
            for (LegendaryEntry e : entries) {
                List<Player> eligiblePlayers = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p == null || !p.isOnline()) continue;
                    if (p.isDead()) continue;
                    if (p.getWorld() == null) continue;
                    if (!isAllowedInWorld(p.getWorld(), e)) continue;
                    if (matchesBiomeOrNearby(p, e)) {
                        eligiblePlayers.add(p);
                    }
                }
                if (!eligiblePlayers.isEmpty()) {
                    picks.add(new Pick(e, eligiblePlayers));
                }
            }
            if (picks.isEmpty()) return;

            // Step 3: choose random entry then random player.
            Pick pick = picks.get(Util.RND.nextInt(picks.size()));
            Player target = pick.players.get(Util.RND.nextInt(pick.players.size()));

            spawnLegendaryNear(target, pick.entry);

        } catch (Throwable t) {
            try { plugin.getLogger().warning("[PokeDemo] LegendaryRandomSpawnService tick error: " + t.getMessage()); } catch (Throwable ignored) {}
        }
    }

    private boolean isAllowedInWorld(World w, LegendaryEntry e) {
        if (w == null) return false;
        if (e.dimensions != null && !e.dimensions.isEmpty()) {
            String env = w.getEnvironment().name();
            for (String d : e.dimensions) {
                if (d == null) continue;
                if (env.equalsIgnoreCase(d.trim())) return true;
            }
            return false;
        }
        // default: overworld only
        return w.getEnvironment() == World.Environment.NORMAL;
    }

    private boolean matchesBiomeOrNearby(Player p, LegendaryEntry e) {
        if (p == null || e == null) return false;
        if (e.biomes == null || e.biomes.isEmpty()) return false;

        Biome here = p.getLocation().getBlock().getBiome();
        if (biomeMatches(here, e.biomes)) return true;

        int radius = Math.max(0, plugin.getConfig().getInt("legendary.random.nearby-radius", 64));
        int samples = Math.max(0, plugin.getConfig().getInt("legendary.random.sample-points", 10));
        if (radius <= 0 || samples <= 0) return false;

        Location base = p.getLocation();
        World w = base.getWorld();
        if (w == null) return false;

        for (int i = 0; i < samples; i++) {
            double ang = Util.RND.nextDouble() * Math.PI * 2.0;
            double rr = Util.RND.nextDouble() * radius;
            int dx = (int) Math.round(Math.cos(ang) * rr);
            int dz = (int) Math.round(Math.sin(ang) * rr);

            int x = base.getBlockX() + dx;
            int z = base.getBlockZ() + dz;
            int y = w.getHighestBlockYAt(x, z);
            Location probe = new Location(w, x + 0.5, y + 1, z + 0.5);
            Biome b = probe.getBlock().getBiome();
            if (biomeMatches(b, e.biomes)) return true;
        }
        return false;
    }

    private boolean biomeMatches(Biome biome, List<String> tokens) {
        if (biome == null || tokens == null) return false;
        String name = biome.name().toUpperCase(Locale.ROOT);
        for (String t : tokens) {
            if (t == null) continue;
            String tok = t.trim().toUpperCase(Locale.ROOT);
            if (tok.isEmpty()) continue;
            // Allow exact or "contains" match (so "JUNGLE" covers multiple jungle biomes)
            if (name.equals(tok) || name.contains(tok)) return true;
        }
        return false;
    }

    private void spawnLegendaryNear(Player target, LegendaryEntry e) {
        if (target == null || e == null) return;
        if (dex.getSpeciesCount() == 0) return;

        Species s = dex.getSpecies(e.speciesId.toLowerCase(Locale.ROOT));
        if (s == null) return;

        int level = Util.clamp(e.level, 1, 100);
        int minPerfect = Math.max(0, e.minPerfectIvs);

        Location spawnLoc = findSafeLandLocationNear(target.getLocation(), 12, 16);
        if (spawnLoc == null) spawnLoc = target.getLocation().clone().add(0, 1, 0);

        UUID wolfId = spawns.spawnWildManual(spawnLoc, s.id(), level);
        if (wolfId == null) return;

        Entity ent = Bukkit.getEntity(wolfId);
        if (!(ent instanceof Wolf wolf)) return;

        // Mark as legendary
        wolf.getPersistentDataContainer().set(plugin.KEY_BUCKET, PersistentDataType.STRING, "legendary");
        wolf.getPersistentDataContainer().set(plugin.KEY_LEGENDARY, PersistentDataType.BYTE, (byte)1);
        wolf.getPersistentDataContainer().set(plugin.KEY_LEGENDARY_GROUP, PersistentDataType.STRING, e.group == null ? "legendary" : e.group);
        wolf.getPersistentDataContainer().set(plugin.KEY_MIN_PERFECT_IVS, PersistentDataType.INTEGER, minPerfect);

        // Override floating label for legendaries (gold)
        LangManager lang = plugin.getLang();
        String display = (lang != null) ? lang.species(s.id(), s.name()) : s.name();
        String label = "§6★ §6" + display + " §7Lv." + level;
        new VisualCarrierManager(plugin).attach(wolf, s.id(), label);

        // Auto-despawn (same as altar legendaries)
        int despawnMinutes = plugin.getConfig().getInt("legendary.despawn-minutes", 10);
        if (despawnMinutes > 0) {
            long delayTicks = Math.max(1L, despawnMinutes * 60L * 20L);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                try {
                    if (wolf == null || !wolf.isValid() || wolf.isDead()) return;
                    Byte isLeg = wolf.getPersistentDataContainer().get(plugin.KEY_LEGENDARY, PersistentDataType.BYTE);
                    if (isLeg == null || isLeg != (byte) 1) return;
                    new VisualCarrierManager(plugin).detach(wolf);
                    wolf.remove();
                } catch (Throwable ignored) {}
            }, delayTicks);
        }

        // Broadcast
        String msg = plugin.getConfig().getString("legendary.messages.spawn", "§5一只传说中的宝可梦§a【§6%name%§a】§5出现了！");
        msg = msg.replace("%name%", display);
        Bukkit.broadcastMessage(msg);
        try {
            wolf.getWorld().playSound(wolf.getLocation(), org.bukkit.Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1.6f);
        } catch (Throwable ignored) {}
    }

    private Location findSafeLandLocationNear(Location base, int attempts, int radius) {
        if (base == null || base.getWorld() == null) return null;
        World w = base.getWorld();

        for (int i = 0; i < attempts; i++) {
            int dx = Util.RND.nextInt(radius * 2 + 1) - radius;
            int dz = Util.RND.nextInt(radius * 2 + 1) - radius;

            int x = base.getBlockX() + dx;
            int z = base.getBlockZ() + dz;

            int y = w.getHighestBlockYAt(x, z);
            Location loc = new Location(w, x + 0.5, y + 1, z + 0.5);

            // Require 2 blocks of air at spawn
            if (!loc.getBlock().isEmpty()) continue;
            Location head = loc.clone().add(0, 1, 0);
            if (!head.getBlock().isEmpty()) continue;

            // Avoid spawning on water/lava
            Material below = loc.clone().add(0, -1, 0).getBlock().getType();
            if (below == org.bukkit.Material.WATER || below == org.bukkit.Material.LAVA) continue;

            return loc;
        }
        return null;
    }

    private List<LegendaryEntry> loadEntries() {
        List<LegendaryEntry> list = new ArrayList<>();
        try {
            List<Map<?, ?>> maps = plugin.getConfig().getMapList("legendary.random.entries");
            if (maps != null) {
                for (Map<?, ?> m : maps) {
                    if (m == null) continue;
                    String id = asString(m.get("id"));
                    if (id == null || id.isBlank()) id = asString(m.get("species"));
                    if (id == null || id.isBlank()) continue;

                    String group = asString(m.get("group"));
                    int level = asInt(m.get("level"), plugin.getConfig().getInt("legendary.defaults.level", 60));
                    int minPerf = asInt(m.get("min-perfect-ivs"), plugin.getConfig().getInt("legendary.defaults.min-perfect-ivs", 3));

                    List<String> biomes = toStringList(m.get("biomes"));
                    List<String> dims = toStringList(m.get("dimensions"));

                    list.add(new LegendaryEntry(id, group, level, minPerf, biomes, dims));
                }
            }
        } catch (Throwable ignored) {}

        // Default entry: Mew in Jungles
        if (list.isEmpty()) {
            list.add(new LegendaryEntry("mew", "gen1_mythical", plugin.getConfig().getInt("legendary.defaults.level", 60),
                    plugin.getConfig().getInt("legendary.defaults.min-perfect-ivs", 3),
                    Arrays.asList("JUNGLE"), Collections.emptyList()));
        }
        return list;
    }

    private static String asString(Object o) {
        if (o == null) return null;
        return String.valueOf(o);
    }

    private static int asInt(Object o, int def) {
        if (o == null) return def;
        try {
            if (o instanceof Number n) return n.intValue();
            return Integer.parseInt(String.valueOf(o).trim());
        } catch (Throwable ignored) { return def; }
    }

    @SuppressWarnings("unchecked")
    private static List<String> toStringList(Object o) {
        if (o == null) return Collections.emptyList();
        if (o instanceof List<?> l) {
            List<String> out = new ArrayList<>();
            for (Object x : l) {
                if (x == null) continue;
                String s = String.valueOf(x).trim();
                if (!s.isEmpty()) out.add(s);
            }
            return out;
        }
        // allow comma-separated
        String s = String.valueOf(o);
        if (s.contains(",")) {
            List<String> out = new ArrayList<>();
            for (String part : s.split(",")) {
                String p = part.trim();
                if (!p.isEmpty()) out.add(p);
            }
            return out;
        }
        String single = s.trim();
        return single.isEmpty() ? Collections.emptyList() : Collections.singletonList(single);
    }

    private record LegendaryEntry(String speciesId, String group, int level, int minPerfectIvs, List<String> biomes, List<String> dimensions) {}

    private record Pick(LegendaryEntry entry, List<Player> players) {}
}
