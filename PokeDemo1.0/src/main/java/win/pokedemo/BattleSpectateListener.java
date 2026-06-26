package win.pokedemo;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

/**
 * Click battling Pokémon (battle visual carriers or locked wild entities) to enter chat-only spectate.
 * This covers:
 * - PvP battles (both sides' battle carriers)
 * - Wild battles where the wild entity is already locked by someone
 */
public class BattleSpectateListener implements Listener {
    private final PokeDemoPlugin plugin;
    private final ItemFactory items;
    private final BattleManager battles;

    public BattleSpectateListener(PokeDemoPlugin plugin, ItemFactory items, BattleManager battles) {
        this.plugin = plugin;
        this.items = items;
        this.battles = battles;
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Player player = e.getPlayer();
        if (player == null) return;
        ItemStack inHand = player.getInventory().getItemInMainHand();
        if (items != null && items.isControlWand(inHand)) return;

        Wolf wolf = resolveCarrierWolf(e.getRightClicked());
        if (wolf == null) return;
        if (trySpectate(player, wolf)) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player player)) return;
        ItemStack inHand = player.getInventory().getItemInMainHand();
        if (items != null && items.isControlWand(inHand)) return;
        Wolf wolf = resolveCarrierWolf(e.getEntity());
        if (wolf == null) return;

        if (trySpectate(player, wolf)) {
            e.setCancelled(true);
        }
    }

    private boolean trySpectate(Player spectator, Wolf clickedWolf) {
        if (spectator == null || clickedWolf == null) return false;

        // 1) If this wolf is a battle visual carrier, spectate that session.
        BattleSession s = battles.findSessionByCarrier(clickedWolf.getUniqueId());
        if (s != null && !s.finished) {
            // Don't interfere with the two battling players.
            if (spectator.getUniqueId().equals(s.playerId) || (s.pvp && spectator.getUniqueId().equals(s.pvpOpponentId))) {
                return false;
            }
            return battles.enterSpectate(spectator, s);
        }

        // 2) If this is a locked wild Pokémon entity, enter spectate of the owner.
        Byte wild = clickedWolf.getPersistentDataContainer().get(plugin.KEY_WILD, PersistentDataType.BYTE);
        if (wild != null && wild == (byte) 1) {
            UUID lockOwner = battles.getWildLockOwner(clickedWolf.getUniqueId());
            if (lockOwner != null && !lockOwner.equals(spectator.getUniqueId())) {
                BattleSession watching = battles.getSession(lockOwner);
                if (watching != null && !watching.finished) {
                    return battles.enterSpectate(spectator, watching);
                }
            }
        }

        return false;
    }

    /** Resolve clicked ItemDisplay/TextDisplay back to carrier wolf. */
    private Wolf resolveCarrierWolf(Entity clicked) {
        if (clicked == null) return null;
        if (clicked instanceof Wolf w) return w;
        String owner = clicked.getPersistentDataContainer().get(plugin.KEY_CARRIER_OWNER, PersistentDataType.STRING);
        if (owner == null) return null;
        try {
            Entity e = clicked.getWorld().getEntity(UUID.fromString(owner));
            if (e instanceof Wolf w) return w;
        } catch (IllegalArgumentException ignored) {
        }
        return null;
    }
}
