package win.pokedemo;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Wolf;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Locale;
import java.util.UUID;

/**
 * Manages the visual model (optional ItemDisplay) + floating name/level (TextDisplay)
 * that are attached to a logical carrier entity (Wolf).
 */
public class VisualCarrierManager {
    private final PokeDemoPlugin plugin;
    private final org.bukkit.NamespacedKey KEY_CARRIER_TASK;

    public VisualCarrierManager(PokeDemoPlugin plugin) {
        this.plugin = plugin;
        this.KEY_CARRIER_TASK = new org.bukkit.NamespacedKey(plugin, "carrier_task");
    }

    /**
     * Attach a 3D model display and an optional floating text above the wolf.
     */
    public void attach(Wolf wolf, String speciesId, String floatingText) {
        if (wolf == null || speciesId == null) return;

        detach(wolf);
        if (!plugin.isCarrierDisplaysEnabled()) return;

        String id = speciesId.toLowerCase(Locale.ROOT);
        Integer cmd = plugin.getModelCmd(id);
        if (cmd == null) {
            String fallbackSpecies = plugin.getConfig().getString("visuals.fallback-model-species", "bulbasaur");
            if (fallbackSpecies == null || fallbackSpecies.isBlank()) fallbackSpecies = "bulbasaur";
            Integer fb = plugin.getModelCmd(fallbackSpecies.toLowerCase(Locale.ROOT));
            cmd = (fb != null) ? fb : 100001;
        }

        double globalModelYOffset = plugin.getConfig().getDouble("visuals.model-offset-y", 0.50);
        double globalNameYOffset = plugin.getConfig().getDouble("visuals.name-offset-y", 1.35);
        double raiseBy = plugin.getConfig().getDouble("visuals.name-raise-by", 0.0);
        double globalScale = plugin.getConfig().getDouble("visuals.scale", 2.0);
        boolean hideModelDisplay = plugin.getConfig().getBoolean("visuals.hide-model-display", true);
        boolean autoNameHeight = plugin.getConfig().getBoolean("visuals.name-auto-height", true);
        double topExtra = plugin.getConfig().getDouble("visuals.name-top-extra-y", 0.18);
        double minAboveModel = plugin.getConfig().getDouble("visuals.name-min-above-model", 0.55);
        double scaleFactor = plugin.getConfig().getDouble("visuals.name-scale-factor", 0.42);
        double largeBonusStart = plugin.getConfig().getDouble("visuals.name-large-bonus-start", 1.0);
        double largeBonusFactor = plugin.getConfig().getDouble("visuals.name-large-bonus-factor", 0.60);
        double hugeBonusStart = plugin.getConfig().getDouble("visuals.name-huge-bonus-start", 2.0);
        double hugeBonusFactor = plugin.getConfig().getDouble("visuals.name-huge-bonus-factor", 0.55);

        final double modelYOffset = plugin.getConfig().getDouble("pokemon-visuals." + id + ".y-offset", globalModelYOffset);
        float scale = (float) plugin.getConfig().getDouble("pokemon-visuals." + id + ".scale", globalScale);
        final double configuredNameYOffset = plugin.getConfig().getDouble("pokemon-visuals." + id + ".name-offset-y", -9999.0);
        final double fallbackNameYOffset = plugin.getConfig().getDouble("visuals.name-offset-y", globalNameYOffset) + raiseBy;
        final double autoNameLift = Math.max(minAboveModel, scale * scaleFactor)
                + Math.max(0.0, scale - largeBonusStart) * largeBonusFactor
                + Math.max(0.0, scale - hugeBonusStart) * hugeBonusFactor
                + topExtra
                + raiseBy;
        final double computedNameYOffset = (configuredNameYOffset > -9000.0)
                ? configuredNameYOffset
                : (autoNameHeight
                    ? Math.max(fallbackNameYOffset, modelYOffset + autoNameLift)
                    : fallbackNameYOffset);

        final ItemDisplay[] display = {null};
        if (!hideModelDisplay) {
            ItemStack item = new ItemStack(Material.CARVED_PUMPKIN, 1);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setCustomModelData(cmd);
                item.setItemMeta(meta);
            }
            display[0] = wolf.getWorld().spawn(wolf.getLocation(), ItemDisplay.class, d -> {
                d.setItemStack(item);
                d.setGravity(false);
                d.setInvulnerable(true);
                d.setSilent(true);
                d.setPersistent(true);
                d.getPersistentDataContainer().set(plugin.KEY_CARRIER_OWNER, PersistentDataType.STRING, wolf.getUniqueId().toString());
                d.setBillboard(Display.Billboard.FIXED);
                d.setInterpolationDelay(0);
                d.setInterpolationDuration(0);
                d.setTeleportDuration(0);
                Transformation t = new Transformation(
                        new Vector3f(0f, 0f, 0f),
                        new Quaternionf(),
                        new Vector3f(scale, scale, scale),
                        new Quaternionf()
                );
                d.setTransformation(t);
            });
            wolf.getPersistentDataContainer().set(plugin.KEY_CARRIER, PersistentDataType.STRING, display[0].getUniqueId().toString());
        } else {
            wolf.getPersistentDataContainer().remove(plugin.KEY_CARRIER);
        }

        final Interaction[] interact = {wolf.getWorld().spawn(wolf.getLocation(), Interaction.class, it -> {
            it.setGravity(false);
            it.setInvulnerable(true);
            it.setSilent(true);
            it.setPersistent(true);
            it.getPersistentDataContainer().set(plugin.KEY_CARRIER_OWNER, PersistentDataType.STRING, wolf.getUniqueId().toString());
            float w = Math.max(0.6f, Math.min(3.5f, scale * 0.9f));
            float h = Math.max(0.8f, Math.min(4.5f, scale * 1.2f));
            it.setInteractionWidth(w);
            it.setInteractionHeight(h);
            it.setResponsive(true);
        })};
        wolf.getPersistentDataContainer().set(plugin.KEY_CARRIER_INTERACT, PersistentDataType.STRING, interact[0].getUniqueId().toString());

        final String finalFloatingText = floatingText;
        final TextDisplay[] text = {null};
        if (finalFloatingText != null && !finalFloatingText.isBlank()) {
            text[0] = wolf.getWorld().spawn(wolf.getLocation(), TextDisplay.class, td -> {
                td.setText(finalFloatingText);
                td.setBillboard(Display.Billboard.CENTER);
                td.setSeeThrough(false);
                td.setShadowed(false);
                td.setDefaultBackground(false);
                try { td.setTextOpacity((byte)170); } catch (Throwable ignored) {}
                td.setBackgroundColor(org.bukkit.Color.fromARGB(0, 0, 0, 0));
                try {
                    float vr = (float) plugin.getConfig().getDouble("visuals.name-view-range", 16.0);
                    td.setViewRange(Math.max(2f, Math.min(64f, vr)));
                } catch (Throwable ignored) {}
                td.setPersistent(true);
                td.setInvulnerable(true);
                td.setGravity(false);
                td.getPersistentDataContainer().set(plugin.KEY_CARRIER_OWNER, PersistentDataType.STRING, wolf.getUniqueId().toString());
            });
            wolf.getPersistentDataContainer().set(plugin.KEY_CARRIER_TEXT, PersistentDataType.STRING, text[0].getUniqueId().toString());
        } else {
            wolf.getPersistentDataContainer().remove(plugin.KEY_CARRIER_TEXT);
        }

        wolf.setCustomNameVisible(false);

        long visibleCheckInterval = Math.max(1L, plugin.getConfig().getLong("visuals.name-visible-check-interval-ticks", 20L));
        final long[] visibilityCounter = {0L};
        final boolean[] nameVisible = {true};
        int taskId = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!wolf.isValid() || wolf.isDead()) {
                if (display[0] != null && display[0].isValid()) display[0].remove();
                if (text[0] != null && text[0].isValid()) text[0].remove();
                if (interact[0].isValid()) interact[0].remove();
                return;
            }
            Location base = wolf.getLocation();
            Location modelLoc = base.clone().add(0, modelYOffset, 0);
            if (display[0] != null && display[0].isValid()) {
                display[0].teleport(modelLoc);
                display[0].setRotation(base.getYaw(), 0f);
            }
            if (interact[0].isValid()) interact[0].teleport(modelLoc);
            if (text[0] != null && text[0].isValid()) {
                Location textLoc = base.clone().add(0, computedNameYOffset, 0);
                text[0].teleport(textLoc);
                text[0].setRotation(base.getYaw(), 0f);

                visibilityCounter[0]++;
                if (visibilityCounter[0] >= visibleCheckInterval) {
                    visibilityCounter[0] = 0L;
                    double visibleRange = plugin.getConfig().getDouble("visuals.name-visible-range", 5.0);
                    boolean anyNearbyPlayer = !base.getWorld().getNearbyEntities(base, visibleRange, visibleRange, visibleRange,
                            ent -> ent.getType() == EntityType.PLAYER && ent.isValid()).isEmpty();
                    if (anyNearbyPlayer != nameVisible[0]) {
                        nameVisible[0] = anyNearbyPlayer;
                        if (nameVisible[0]) {
                            text[0].setText(finalFloatingText);
                        } else {
                            text[0].setText("");
                        }
                    }
                }
            }
        }, 1L, 1L).getTaskId();
        wolf.getPersistentDataContainer().set(KEY_CARRIER_TASK, PersistentDataType.INTEGER, taskId);
    }

    public void attach(Wolf wolf, String speciesId) {
        attach(wolf, speciesId, null);
    }

    public void detach(Wolf wolf) {
        if (wolf == null) return;
        Integer taskId = wolf.getPersistentDataContainer().get(KEY_CARRIER_TASK, PersistentDataType.INTEGER);
        if (taskId != null) {
            plugin.getServer().getScheduler().cancelTask(taskId);
            wolf.getPersistentDataContainer().remove(KEY_CARRIER_TASK);
        }
        String carrier = wolf.getPersistentDataContainer().get(plugin.KEY_CARRIER, PersistentDataType.STRING);
        if (carrier != null) {
            try {
                Entity e = wolf.getWorld().getEntity(UUID.fromString(carrier));
                if (e != null) e.remove();
            } catch (IllegalArgumentException ignored) {}
            wolf.getPersistentDataContainer().remove(plugin.KEY_CARRIER);
        }
        String interactId = wolf.getPersistentDataContainer().get(plugin.KEY_CARRIER_INTERACT, PersistentDataType.STRING);
        if (interactId != null) {
            try {
                Entity e = wolf.getWorld().getEntity(UUID.fromString(interactId));
                if (e != null) e.remove();
            } catch (IllegalArgumentException ignored) {}
            wolf.getPersistentDataContainer().remove(plugin.KEY_CARRIER_INTERACT);
        }
        String textId = wolf.getPersistentDataContainer().get(plugin.KEY_CARRIER_TEXT, PersistentDataType.STRING);
        if (textId != null) {
            try {
                Entity e = wolf.getWorld().getEntity(UUID.fromString(textId));
                if (e != null) e.remove();
            } catch (IllegalArgumentException ignored) {}
            wolf.getPersistentDataContainer().remove(plugin.KEY_CARRIER_TEXT);
        }
    }

    public int cleanupOrphanVisuals() {
        int removed = 0;
        for (var world : plugin.getServer().getWorlds()) {
            for (Entity e : world.getEntities()) {
                if (e instanceof ItemDisplay || e instanceof TextDisplay || e.getType() == EntityType.INTERACTION) {
                    String owner = e.getPersistentDataContainer().get(plugin.KEY_CARRIER_OWNER, PersistentDataType.STRING);
                    if (owner != null) {
                        e.remove();
                        removed++;
                    }
                }
            }
            for (Wolf wolf : world.getEntitiesByClass(Wolf.class)) {
                if (wolf.getPersistentDataContainer().has(plugin.KEY_CARRIER, PersistentDataType.STRING)
                        || wolf.getPersistentDataContainer().has(plugin.KEY_CARRIER_TEXT, PersistentDataType.STRING)
                        || wolf.getPersistentDataContainer().has(plugin.KEY_CARRIER_INTERACT, PersistentDataType.STRING)) {
                    detach(wolf);
                }
            }
        }
        return removed;
    }
}
