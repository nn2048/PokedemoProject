package win.pokedemo;

import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.RayTraceResult;

import java.util.UUID;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Control wand:
 * - Left click: decrease scale
 * - Right click: increase scale
 * - Sneak + Left: decrease height (model y-offset)
 * - Sneak + Right: increase height
 * Writes to config and re-attaches the model to apply immediately (hot update).
 */
public class ControlWandListener implements Listener {
    private final PokeDemoPlugin plugin;
    private final ItemFactory items;
    private final VisualCarrierManager visuals;
    private final Map<UUID, Long> swingDebounce = new ConcurrentHashMap<>();
    // Right-click (use) can sometimes also trigger a PlayerAnimationEvent on certain clients/servers.
    // If we process swing as "left click" right after a right-click, the action may appear to "swap".
    private final Map<UUID, Long> rightClickDebounce = new ConcurrentHashMap<>();

    public ControlWandListener(PokeDemoPlugin plugin, ItemFactory items) {
        this.plugin = plugin;
        this.items = items;
        this.visuals = new VisualCarrierManager(plugin);
    }

    private boolean isControlWand(ItemStack it) {
        return items.isControlWand(it);
    }

    private boolean isPokemonCarrier(Entity e) {
        if (!(e instanceof Wolf w)) return false;
        String species = w.getPersistentDataContainer().get(plugin.KEY_SPECIES, PersistentDataType.STRING);
        return species != null && !species.isBlank();
    }

    /**
     * Allow clicking either the wolf carrier OR the display/interact hitbox that belongs to it.
     */
    private Wolf resolveCarrier(Entity clicked) {
        if (clicked == null) return null;
        if (clicked instanceof Wolf w) return isPokemonCarrier(w) ? w : null;

        // Displays / Interaction store their carrier owner UUID in PDC.
        PersistentDataContainer pdc = clicked.getPersistentDataContainer();
        String owner = pdc.get(plugin.KEY_CARRIER_OWNER, PersistentDataType.STRING);
        if (owner == null || owner.isBlank()) return null;
        try {
            UUID uuid = UUID.fromString(owner);
            Entity e = org.bukkit.Bukkit.getEntity(uuid);
            if (e instanceof Wolf w && isPokemonCarrier(w)) return w;
        } catch (Exception ignored) {}
        return null;
    }

    private void applyAndSave(Wolf wolf, String speciesIdLower, double newScale, double newYOffset, Player actor) {
        // clamp
        double minScale = plugin.getConfig().getDouble("control-wand.min-scale", 0.1);
        double maxScale = plugin.getConfig().getDouble("control-wand.max-scale", 10.0);
        newScale = Math.max(minScale, Math.min(maxScale, newScale));

        String base = "pokemon-visuals." + speciesIdLower;
        plugin.getConfig().set(base + ".scale", newScale);
        plugin.getConfig().set(base + ".y-offset", newYOffset);
        plugin.saveConfig();

        // re-attach visuals to apply immediately
        int level = 1;
        Integer lvl = wolf.getPersistentDataContainer().get(plugin.KEY_LEVEL, PersistentDataType.INTEGER);
        if (lvl != null) level = lvl;

        Species s = plugin.getDex() != null ? plugin.getDex().getSpecies(speciesIdLower) : null;
        LangManager lang = PokeDemoPlugin.INSTANCE.getLang();
        String displayName = (lang == null ? (s != null ? s.name() : speciesIdLower) : lang.species(speciesIdLower, (s != null ? s.name() : null)));
        String label = "§f" + displayName + " §7Lv." + level;

        visuals.attach(wolf, speciesIdLower, label);

        actor.sendActionBar("§d" + displayName + " §7scale=" + String.format(Locale.ROOT, "%.2f", newScale)
                + "  y=" + String.format(Locale.ROOT, "%.2f", newYOffset));
    }

    private void handleAdjust(Player player, Entity target, boolean increase, boolean isHeight) {
        Wolf wolf = resolveCarrier(target);
        if (wolf == null) return;
        String species = wolf.getPersistentDataContainer().get(plugin.KEY_SPECIES, PersistentDataType.STRING);
        if (species == null || species.isBlank()) return;

        String id = species.toLowerCase(Locale.ROOT);

        double globalScale = plugin.getConfig().getDouble("visuals.scale", 2.0);
        double globalYOffset = plugin.getConfig().getDouble("visuals.model-offset-y", 0.50);

        double curScale = plugin.getConfig().getDouble("pokemon-visuals." + id + ".scale", globalScale);
        double curYOffset = plugin.getConfig().getDouble("pokemon-visuals." + id + ".y-offset", globalYOffset);

        double stepScale = plugin.getConfig().getDouble("control-wand.scale-step", 0.10);
        double stepY = plugin.getConfig().getDouble("control-wand.y-step", 0.05);

        if (isHeight) {
            double nextY = curYOffset + (increase ? stepY : -stepY);
            applyAndSave(wolf, id, curScale, nextY, player);
        } else {
            double nextS = curScale + (increase ? stepScale : -stepScale);
            applyAndSave(wolf, id, nextS, curYOffset, player);
        }
    }

    /**
     * Fallback: some hitbox/entity types (e.g. Interaction / display proxies) may not consistently fire
     * PlayerInteractEntityEvent on all server implementations. We therefore also handle normal
     * right-click (air/block) with a ray-trace to find the Pokemon carrier in front of the player.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onRightClickRaytrace(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Action a = event.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack it = player.getInventory().getItemInMainHand();
        if (!isControlWand(it)) return;

        try {
            RayTraceResult rr = player.getWorld().rayTraceEntities(
                    player.getEyeLocation(),
                    player.getEyeLocation().getDirection(),
                    6.0,
                    0.35,
                    e -> e != null && e != player && (resolveCarrier(e) != null)
            );
            if (rr == null) return;
            Entity hit = rr.getHitEntity();
            if (hit == null) return;

            event.setCancelled(true);

            // mark recent right-click to avoid swing fallback firing as "left click"
            rightClickDebounce.put(player.getUniqueId(), System.currentTimeMillis());

            boolean height = player.isSneaking();
            handleAdjust(player, hit, true, height);
        } catch (Throwable ignored) {}
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onRightClickEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        ItemStack it = player.getInventory().getItemInMainHand();
        if (!isControlWand(it)) return;

        Entity target = event.getRightClicked();
        if (resolveCarrier(target) == null) return;

        event.setCancelled(true);

        // mark recent right-click to avoid swing fallback firing as "left click"
        rightClickDebounce.put(player.getUniqueId(), System.currentTimeMillis());

        boolean height = player.isSneaking();
        handleAdjust(player, target, true, height);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLeftClickEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        ItemStack it = player.getInventory().getItemInMainHand();
        if (!isControlWand(it)) return;

        Entity target = event.getEntity();
        if (resolveCarrier(target) == null) return;

        // prevent damage
        event.setCancelled(true);

        boolean height = player.isSneaking();
        handleAdjust(player, target, false, height);
    }

    /**
     * Some entity types (notably Interaction) don't reliably fire EntityDamageByEntityEvent
     * when the player "attacks" them on certain server implementations.
     * To make the control wand feel consistent, we also listen to arm swing and ray-trace
     * for a carrier hitbox in front of the player.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSwing(PlayerAnimationEvent event) {
        Player player = event.getPlayer();
        ItemStack it = player.getInventory().getItemInMainHand();
        if (!isControlWand(it)) return;

        // If the player just right-clicked with the wand, ignore swing fallback.
        // This prevents right-click from being interpreted as a left-click adjustment.
        long now = System.currentTimeMillis();
        Long lastRc = rightClickDebounce.get(player.getUniqueId());
        if (lastRc != null && now - lastRc < 220) return;

        // Debounce to avoid double-processing with EntityDamageByEntityEvent when that one does fire.
        Long last = swingDebounce.get(player.getUniqueId());
        if (last != null && now - last < 120) return;
        swingDebounce.put(player.getUniqueId(), now);

        try {
            RayTraceResult rr = player.getWorld().rayTraceEntities(
                    player.getEyeLocation(),
                    player.getEyeLocation().getDirection(),
                    6.0,
                    0.35,
                    e -> e != null && e != player && (resolveCarrier(e) != null)
            );
            if (rr == null) return;
            Entity hit = rr.getHitEntity();
            if (hit == null) return;

            boolean height = player.isSneaking();
            handleAdjust(player, hit, false, height);
        } catch (Throwable ignored) {}
    }
}
