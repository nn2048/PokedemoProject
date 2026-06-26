package win.pokedemo;

import org.bukkit.Bukkit;
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
 * PvP entry: click another player's summoned Pokémon (carrier wolf or its display) to open PvP ready GUI.
 */
public class PvpChallengeListener implements Listener {
    private final PokeDemoPlugin plugin;
    private final ItemFactory items;
    private final BattleManager battles;

    public PvpChallengeListener(PokeDemoPlugin plugin, ItemFactory items, BattleManager battles) {
        this.plugin = plugin;
        this.items = items;
        this.battles = battles;
    }

    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Player player = e.getPlayer();

        ItemStack inHand = player.getInventory().getItemInMainHand();
        if (items != null && items.isControlWand(inHand)) return;

        Wolf wolf = resolveCarrierWolf(e.getRightClicked());
        if (wolf == null) return;

        // Ignore wild
        Byte wild = wolf.getPersistentDataContainer().get(plugin.KEY_WILD, PersistentDataType.BYTE);
        if (wild != null && wild == (byte) 1) return;

        // Only summoned Pokémon entities
        if (!plugin.getSummonManager().isSummonedPokemonEntity(wolf)) return;

        UUID owner = plugin.getSummonManager().getOwnerUuidFromEntity(wolf);
        if (owner == null || owner.equals(player.getUniqueId())) return;

        Player other = Bukkit.getPlayer(owner);
        if (other == null || !other.isOnline()) {
            player.sendMessage("§e对方不在线。");
            return;
        }

        e.setCancelled(true);
        // If the target player is already in a battle, enter spectate instead of challenging.
        BattleSession s = battles.getSession(owner);
        if (s != null && !s.finished) {
            battles.enterSpectate(player, s);
            return;
        }
        battles.startPvpReady(player, other);
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player player)) return;
        ItemStack inHand = player.getInventory().getItemInMainHand();
        if (items != null && items.isControlWand(inHand)) return;

        Wolf wolf = resolveCarrierWolf(e.getEntity());
        if (wolf == null) return;
        Byte wild = wolf.getPersistentDataContainer().get(plugin.KEY_WILD, PersistentDataType.BYTE);
        if (wild != null && wild == (byte) 1) return;
        if (!plugin.getSummonManager().isSummonedPokemonEntity(wolf)) return;

        UUID owner = plugin.getSummonManager().getOwnerUuidFromEntity(wolf);
        if (owner == null || owner.equals(player.getUniqueId())) return;

        Player other = Bukkit.getPlayer(owner);
        if (other == null || !other.isOnline()) return;

        // Cancel direct combat and open PvP ready
        e.setCancelled(true);
        BattleSession s = battles.getSession(owner);
        if (s != null && !s.finished) {
            battles.enterSpectate(player, s);
            return;
        }
        battles.startPvpReady(player, other);
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
