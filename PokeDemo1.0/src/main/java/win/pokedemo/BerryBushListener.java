package win.pokedemo;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Berry bush rework:
 * - Right click mature sweet berry bush => gives Poké berry item (random if wild, fixed if planted by berry item)
 * - Planting: right click dirt/grass with a Poké berry item => places a sweet berry bush and binds its type
 */
public class BerryBushListener implements Listener {

    private final PokeDemoPlugin plugin;

    // worldName:x:y:z -> berryId
    private final Map<String, String> bushType = new HashMap<>();
    private final File saveFile;
    private volatile boolean dirty = false;

    public BerryBushListener(PokeDemoPlugin plugin) {
        this.plugin = plugin;
        this.saveFile = new File(plugin.getDataFolder(), "berry_bushes.yml");
        load();

        // periodic save (cheap + safe)
        new BukkitRunnable() {
            @Override public void run() {
                if (!dirty) return;
                save();
            }
        }.runTaskTimer(plugin, 20L * 10, 20L * 10);
    }

    private static String key(Location loc) {
        return loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }

    private void load() {
        bushType.clear();
        if (!saveFile.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(saveFile);
        for (String k : yml.getKeys(false)) {
            String v = yml.getString(k);
            if (v != null && !v.isEmpty()) bushType.put(k, v);
        }
        dirty = false;
    }

    private void save() {
        YamlConfiguration yml = new YamlConfiguration();
        for (Map.Entry<String, String> e : bushType.entrySet()) {
            yml.set(e.getKey(), e.getValue());
        }
        try {
            if (!saveFile.getParentFile().exists()) saveFile.getParentFile().mkdirs();
            yml.save(saveFile);
            dirty = false;
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to save berry_bushes.yml: " + ex.getMessage());
        }
    }

    private boolean isPokeBerryItem(ItemStack it) {
        if (it == null || it.getType() != Material.PAPER) return false;
        String id = plugin.getItems().getItemId(it);
        return id != null && id.endsWith("_berry");
    }

    private String getBerryId(ItemStack it) {
        return plugin.getItems().getItemId(it);
    }

    private ItemStack makeBerryItem(String berryId) {
        return plugin.getItems().make(berryId, plugin.getItemRegistry());
    }

    private String randomBerryId() {
        String[] all = BerryIndex.ALL_BERRIES;
        if (all == null || all.length == 0) return null;
        return all[ThreadLocalRandom.current().nextInt(all.length)] + "_berry";
    }

    private static Sound safeSound(String primary, String fallback) {
        try {
            return Sound.valueOf(primary);
        } catch (Throwable ignored) {
            try {
                return Sound.valueOf(fallback);
            } catch (Throwable ignored2) {
                return null;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onHarvest(PlayerInteractEvent e) {
        if (e.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
        if (e.getHand() != EquipmentSlot.HAND) return;
        Block b = e.getClickedBlock();
        if (b == null || b.getType() != Material.SWEET_BERRY_BUSH) return;

        // If not mature, don't interfere (vanilla thorn / growth etc)
        if (!(b.getBlockData() instanceof Ageable age)) return;
        int max = age.getMaximumAge();
        if (age.getAge() < max) return;

        // stop vanilla harvest
        e.setCancelled(true);
        try {
            e.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
            e.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        } catch (Throwable ignored) {}

        String k = key(b.getLocation());
        String berryId = bushType.get(k);
        if (berryId == null) berryId = randomBerryId();
        if (berryId == null) return;

        ItemStack drop = makeBerryItem(berryId);
        if (drop == null || drop.getType() == Material.AIR) return;

        // set bush back to regrow (like vanilla)
        age.setAge(Math.max(1, max - 2));
        b.setBlockData(age, false);

        Player p = e.getPlayer();
        HashMap<Integer, ItemStack> left = p.getInventory().addItem(drop);
        if (!left.isEmpty()) {
            b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.8, 0.5), drop);
        }
        Sound pick = safeSound("BLOCK_SWEET_BERRY_BUSH_PICK_BERRIES", "ITEM_SWEET_BERRIES_PICK_FROM_BUSH");
        if (pick != null) b.getWorld().playSound(b.getLocation(), pick, 1f, 1f);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlant(PlayerInteractEvent e) {
        if (e.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
        if (e.getHand() != EquipmentSlot.HAND) return;
        ItemStack inHand = e.getItem();
        if (!isPokeBerryItem(inHand)) return;

        Block clicked = e.getClickedBlock();
        if (clicked == null) return;
        Material base = clicked.getType();
        // allow planting on grass/dirt/farmland
        if (!(base == Material.GRASS_BLOCK || base == Material.DIRT || base == Material.FARMLAND || base == Material.PODZOL || base == Material.COARSE_DIRT || base == Material.ROOTED_DIRT)) {
            return;
        }

        Block place = clicked.getRelative(e.getBlockFace());
        if (place.getType() != Material.AIR && !place.isPassable()) return;

        // place bush
        e.setCancelled(true);
        try {
            e.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
            e.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        } catch (Throwable ignored) {}

        place.setType(Material.SWEET_BERRY_BUSH, false);
        if (place.getBlockData() instanceof Ageable age) {
            age.setAge(0);
            place.setBlockData(age, false);
        }

        String berryId = getBerryId(inHand);
        bushType.put(key(place.getLocation()), berryId);
        dirty = true;

        // consume 1
        Player p = e.getPlayer();
        if (p.getGameMode() != GameMode.CREATIVE) {
            inHand.setAmount(inHand.getAmount() - 1);
        }
        place.getWorld().playSound(place.getLocation(), Sound.ITEM_CROP_PLANT, 1f, 1f);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        if (b.getType() != Material.SWEET_BERRY_BUSH) return;
        String k = key(b.getLocation());
        String berryId = bushType.remove(k);
        if (berryId == null) return; // wild -> vanilla drops
        dirty = true;

        // replace vanilla drops with 1 seed berry item
        e.setDropItems(false);
        ItemStack drop = makeBerryItem(berryId);
        if (drop != null && drop.getType() != Material.AIR) {
            b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), drop);
        }
    }
}
