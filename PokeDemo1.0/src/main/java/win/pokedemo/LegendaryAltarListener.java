package win.pokedemo;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/**
 * Altar-summoned Legendary Pokémon (Gen1 birds first):
 *  - Moltres (fire_stone)   : Nether / Savanna-like biomes
 *  - Articuno (water_stone) : Ice Spikes biome
 *  - Zapdos  (thunder_stone): Mountain biomes
 *
 * Structure (centered on diamond block):
 *  Layer Y:
 *    corners (±1,±1) = netherite_block
 *    cardinals (N/S/E/W) = element block (magma / packed_ice / iron|copper)
 *    center = diamond_block
 *  Layer Y+1:
 *    corners (±1,±1) = glowstone
 */
public class LegendaryAltarListener implements Listener {

    private final PokeDemoPlugin plugin;
    private final Dex dex;
    private final SpawnManager spawns;
    private final ItemFactory items;

    public LegendaryAltarListener(PokeDemoPlugin plugin, Dex dex, SpawnManager spawns, ItemFactory items) {
        this.plugin = plugin;
        this.dex = dex;
        this.spawns = spawns;
        this.items = items;
    }

    private enum Bird {
        MOLTRES("moltres", "fire_stone"),
        ARTICUNO("articuno", "water_stone"),
        ZAPDOS("zapdos", "thunder_stone");

        final String speciesId;
        final String stoneId;
        Bird(String speciesId, String stoneId) {
            this.speciesId = speciesId;
            this.stoneId = stoneId;
        }

        static Bird fromStone(String stoneId) {
            if (stoneId == null) return null;
            for (Bird b : values()) {
                if (b.stoneId.equalsIgnoreCase(stoneId)) return b;
            }
            return null;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getClickedBlock() == null) return;

        Block center = e.getClickedBlock();
        if (center.getType() != Material.DIAMOND_BLOCK) return;

        Player player = e.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        String itemId = items.getItemId(hand);
        Bird bird = Bird.fromStone(itemId);
        if (bird == null) return;

        // Legendary system toggle
        if (!plugin.getConfig().getBoolean("legendary.enabled", true)) return;

        e.setCancelled(true);

        // Biome gating
        if (!isValidBiomeForBird(center.getWorld(), center.getLocation(), bird)) {
            player.sendMessage("§c这里的环境无法召唤该传说宝可梦。§7请在指定群系/维度尝试。");
            return;
        }

        // Structure check
        if (!isValidAltar(center, bird)) {
            player.sendMessage("§c祭坛结构不正确。§7请检查下界合金角、四向元素方块、上层荧石与中心钻石块。");
            return;
        }

        // Consume 1 stone
        if (hand == null || hand.getAmount() <= 0) return;
        hand.setAmount(hand.getAmount() - 1);
        if (hand.getAmount() <= 0) player.getInventory().setItemInMainHand(null);

        // Clear altar blocks + a small spawn volume to avoid being stuck.
        clearAltar(center);
        clearSpawnVolume(center.getLocation());

        // NOTE: these must be effectively-final because we capture them in a lambda below.
        final int level = Util.clamp(plugin.getConfig().getInt("legendary.defaults.level", 60), 1, 100);
        final int minPerfect = Util.clamp(plugin.getConfig().getInt("legendary.defaults.min-perfect-ivs", 3), 0, 6);

        Species s = dex.getSpecies(bird.speciesId);
        if (s == null) {
            player.sendMessage("§c服务器未加载该精灵：" + bird.speciesId);
            return;
        }

        // Spawn slightly above the altar center.
        Location spawnLoc = center.getLocation().clone().add(0.5, 1.2, 0.5);

        Wolf wolf = spawnLoc.getWorld().spawn(spawnLoc, Wolf.class, w -> {
            w.setAdult();
            w.setTamed(false);
            try { w.setAI(true); } catch (Throwable ignored) {}
            try { w.setAware(true); } catch (Throwable ignored) {}
            try { w.setCollidable(true); } catch (Throwable ignored) {}
            try { w.setInvulnerable(false); } catch (Throwable ignored) {}
            w.setCustomNameVisible(false);
            w.setCustomName(null);
            w.setAngry(false);
            w.setRemoveWhenFarAway(true);

            // Hide wolf model
            w.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, false, false));
            w.setSilent(false);
            w.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.WATER_BREATHING, Integer.MAX_VALUE, 0, true, false));
            w.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.FIRE_RESISTANCE, Integer.MAX_VALUE, 0, true, false));

            LangManager lang = plugin.getLang();
            String display = (lang != null) ? lang.species(s.id(), s.name()) : s.name();
            String label = "§6★ §e" + display + " §7Lv." + level;
            new VisualCarrierManager(plugin).attach(w, s.id(), label);
        });

        // Mark as wild + legendary
        wolf.getPersistentDataContainer().set(plugin.KEY_WILD, PersistentDataType.BYTE, (byte)1);
        wolf.getPersistentDataContainer().set(plugin.KEY_SPECIES, PersistentDataType.STRING, s.id());
        wolf.getPersistentDataContainer().set(plugin.KEY_LEVEL, PersistentDataType.INTEGER, level);
        wolf.getPersistentDataContainer().set(plugin.KEY_BUCKET, PersistentDataType.STRING, "legendary");
        wolf.getPersistentDataContainer().set(plugin.KEY_LEGENDARY, PersistentDataType.BYTE, (byte)1);
        wolf.getPersistentDataContainer().set(plugin.KEY_LEGENDARY_GROUP, PersistentDataType.STRING, "gen1_birds");
        wolf.getPersistentDataContainer().set(plugin.KEY_MIN_PERFECT_IVS, PersistentDataType.INTEGER, minPerfect);

        // Prevent stuck (reuse spawn manager helper via a tiny teleport nudge + its own check)
        try {
            // SpawnManager has internal ensureNotStuck; we mimic a safe nudge here.
            wolf.teleport(wolf.getLocation().add(0, 0.2, 0));
        } catch (Throwable ignored) {}

        // Auto-despawn: all legendary Pokémon vanish after N minutes (default 10).
        int despawnMinutes = plugin.getConfig().getInt("legendary.despawn-minutes", 10);
        if (despawnMinutes > 0) {
            long delayTicks = Math.max(1L, despawnMinutes * 60L * 20L);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                try {
                    if (wolf == null || !wolf.isValid() || wolf.isDead()) return;
                    Byte isLeg = wolf.getPersistentDataContainer().get(plugin.KEY_LEGENDARY, PersistentDataType.BYTE);
                    if (isLeg == null || isLeg != (byte) 1) return;
                    // Clean model + hitbox + floating text
                    new VisualCarrierManager(plugin).detach(wolf);
                    wolf.remove();
                } catch (Throwable ignored) {}
            }, delayTicks);
        }

        // Broadcast
        LangManager lang = plugin.getLang();
        String display = (lang != null) ? lang.species(s.id(), s.name()) : s.name();
        String msg = plugin.getConfig().getString("legendary.messages.spawn", "§5一只传说中的宝可梦§a【§6%name%§a】§5出现了！");
        msg = msg.replace("%name%", display);
        Bukkit.broadcastMessage(msg);
        try {
            wolf.getWorld().playSound(wolf.getLocation(), org.bukkit.Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1.6f);
        } catch (Throwable ignored) {}
    }

    private boolean isValidBiomeForBird(World w, Location loc, Bird bird) {
        if (w == null || loc == null) return false;

        // Allow overriding in config lists
        String path = "legendary.biomes." + bird.name().toLowerCase(Locale.ROOT);
        List<String> allow = plugin.getConfig().getStringList(path);
        if (allow != null && !allow.isEmpty()) {
            String biomeName = loc.getBlock().getBiome().name();
            for (String s : allow) {
                if (s != null && biomeName.equalsIgnoreCase(s.trim())) return true;
            }
            // Also allow per-bird explicit dimension gate (e.g. NETHER for Moltres)
            List<String> dims = plugin.getConfig().getStringList("legendary.dimensions." + bird.name().toLowerCase(Locale.ROOT));
            if (dims != null && !dims.isEmpty()) {
                String env = w.getEnvironment().name();
                for (String d : dims) {
                    if (d != null && env.equalsIgnoreCase(d.trim())) return true;
                }
            }
            return false;
        }

        // Defaults (sensible)
        var biome = loc.getBlock().getBiome();
        switch (bird) {
            case MOLTRES -> {
                if (w.getEnvironment() == World.Environment.NETHER) return true;
                return biome == org.bukkit.block.Biome.SAVANNA
                        || biome == org.bukkit.block.Biome.SAVANNA_PLATEAU
                        || biome == org.bukkit.block.Biome.WINDSWEPT_SAVANNA;
            }
            case ARTICUNO -> {
                return biome == org.bukkit.block.Biome.ICE_SPIKES;
            }
            case ZAPDOS -> {
                return biome == org.bukkit.block.Biome.WINDSWEPT_HILLS
                        || biome == org.bukkit.block.Biome.WINDSWEPT_GRAVELLY_HILLS
                        || biome == org.bukkit.block.Biome.WINDSWEPT_FOREST
                        || biome == org.bukkit.block.Biome.MEADOW
                        || biome == org.bukkit.block.Biome.GROVE
                        || biome == org.bukkit.block.Biome.JAGGED_PEAKS
                        || biome == org.bukkit.block.Biome.FROZEN_PEAKS
                        || biome == org.bukkit.block.Biome.STONY_PEAKS
                        || biome == org.bukkit.block.Biome.SNOWY_SLOPES;
            }
        }
        return false;
    }

    private boolean isValidAltar(Block center, Bird bird) {
        if (center == null) return false;
        if (center.getType() != Material.DIAMOND_BLOCK) return false;

        // corners: netherite
        if (!is(center.getRelative( 1, 0, 1), Material.NETHERITE_BLOCK)) return false;
        if (!is(center.getRelative( 1, 0,-1), Material.NETHERITE_BLOCK)) return false;
        if (!is(center.getRelative(-1, 0, 1), Material.NETHERITE_BLOCK)) return false;
        if (!is(center.getRelative(-1, 0,-1), Material.NETHERITE_BLOCK)) return false;

        // upper corners: glowstone
        if (!is(center.getRelative( 1, 1, 1), Material.GLOWSTONE)) return false;
        if (!is(center.getRelative( 1, 1,-1), Material.GLOWSTONE)) return false;
        if (!is(center.getRelative(-1, 1, 1), Material.GLOWSTONE)) return false;
        if (!is(center.getRelative(-1, 1,-1), Material.GLOWSTONE)) return false;

        // cardinals: element blocks
        Block n = center.getRelative(BlockFace.NORTH);
        Block s = center.getRelative(BlockFace.SOUTH);
        Block e = center.getRelative(BlockFace.EAST);
        Block w = center.getRelative(BlockFace.WEST);

        return switch (bird) {
            case MOLTRES -> is(n, Material.MAGMA_BLOCK) && is(s, Material.MAGMA_BLOCK) && is(e, Material.MAGMA_BLOCK) && is(w, Material.MAGMA_BLOCK);
            case ARTICUNO -> isIceAltarBlock(n) && isIceAltarBlock(s) && isIceAltarBlock(e) && isIceAltarBlock(w);
            case ZAPDOS -> isElectricAltarBlock(n) && isElectricAltarBlock(s) && isElectricAltarBlock(e) && isElectricAltarBlock(w);
        };
    }

    private static boolean is(Block b, Material m) {
        return b != null && b.getType() == m;
    }

    private static boolean isIceAltarBlock(Block b) {
        if (b == null) return false;
        return b.getType() == Material.PACKED_ICE || b.getType() == Material.BLUE_ICE;
    }

    private static boolean isElectricAltarBlock(Block b) {
        if (b == null) return false;
        Material t = b.getType();
        if (t == Material.IRON_BLOCK) return true;
        // accept any copper block variants
        String name = t.name();
        if (name.endsWith("_COPPER") || name.endsWith("_COPPER_BLOCK")) return true;
        return name.contains("COPPER") && name.endsWith("_BLOCK");
    }

    private void clearAltar(Block center) {
        // Clear 3x3 at Y and the 4 glowstones at Y+1
        int cx = center.getX();
        int cy = center.getY();
        int cz = center.getZ();
        World w = center.getWorld();
        if (w == null) return;

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Block b = w.getBlockAt(cx + dx, cy, cz + dz);
                b.setType(Material.AIR, false);
            }
        }
        // glowstones
        w.getBlockAt(cx + 1, cy + 1, cz + 1).setType(Material.AIR, false);
        w.getBlockAt(cx + 1, cy + 1, cz - 1).setType(Material.AIR, false);
        w.getBlockAt(cx - 1, cy + 1, cz + 1).setType(Material.AIR, false);
        w.getBlockAt(cx - 1, cy + 1, cz - 1).setType(Material.AIR, false);
    }

    private void clearSpawnVolume(Location center) {
        if (center == null || center.getWorld() == null) return;
        World w = center.getWorld();
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();
        int r = plugin.getConfig().getInt("legendary.altar.clear-radius", 2);
        int h = plugin.getConfig().getInt("legendary.altar.clear-height", 3);

        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dy = 0; dy <= h; dy++) {
                    Block b = w.getBlockAt(cx + dx, cy + dy, cz + dz);
                    Material t = b.getType();
                    if (t == Material.BEDROCK || t == Material.BARRIER) continue;
                    if (t.name().contains("COMMAND_BLOCK") || t == Material.STRUCTURE_BLOCK) continue;
                    // Keep liquids below the altar if players built it over a lake, but clear within spawn.
                    b.setType(Material.AIR, false);
                }
            }
        }
    }
}
