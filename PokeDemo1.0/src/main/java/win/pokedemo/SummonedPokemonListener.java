package win.pokedemo;

import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

public class SummonedPokemonListener implements Listener {
    private final PokeDemoPlugin plugin;
    private final Dex dex;
    private final Storage storage;
    private final SummonManager summons;

    public SummonedPokemonListener(PokeDemoPlugin plugin, Dex dex, Storage storage, SummonManager summons) {
        this.plugin = plugin;
        this.dex = dex;
        this.storage = storage;
        this.summons = summons;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        Entity ent = e.getEntity();
        if (!(ent instanceof Wolf wolf)) return;
        if (!summons.isSummonedPokemonEntity(wolf)) return;

        // Summoned Pokémon placeholders should not die to environment (water/lava/suffocation).
        EntityDamageEvent.DamageCause c = e.getCause();
        switch (c) {
            case DROWNING, LAVA, FIRE, FIRE_TICK, HOT_FLOOR, SUFFOCATION, FREEZE -> {
                e.setCancelled(true);
                return;
            }
            default -> {}
        }

        UUID owner = summons.getOwnerUuidFromEntity(wolf);
        UUID puuid = summons.getPokemonUuidFromEntity(wolf);
        if (owner == null || puuid == null) return;

        PlayerProfile prof = storage.getProfile(owner);
        PokemonInstance p = prof.findByUuid(puuid);
        if (p == null) return;

        Species s = dex.getSpecies(p.speciesId);
        if (s == null) return;

        double dmg = e.getFinalDamage();
        int newHp = (int)Math.round(wolf.getHealth() - dmg);

        if (newHp <= 0) {
            // faint: allow death but ensure no drops later; set pokemon HP=0
            p.currentHp = 0;
            storage.markDirty(owner);
            return;
        }

        // update after damage is applied
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (wolf.isDead() || !wolf.isValid()) return;
            // read actual hp from entity to avoid mismatch due to armor/resistance
            double hp = wolf.getHealth();
            p.currentHp = (int)Math.max(0, Math.round(hp));
            storage.markDirty(owner);
            // keep max health consistent
            int maxHp = Math.max(1, p.maxHp(s));
            var attr = wolf.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            if (attr != null && Math.abs(attr.getBaseValue() - maxHp) > 0.01) attr.setBaseValue(maxHp);
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onRegain(EntityRegainHealthEvent e) {
        if (!(e.getEntity() instanceof Wolf wolf)) return;
        if (!summons.isSummonedPokemonEntity(wolf)) return;
        // cancel natural regen to keep HP purely data-driven
        e.setCancelled(true);
    }

    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        if (!(e.getEntity() instanceof Wolf wolf)) return;
        if (!summons.isSummonedPokemonEntity(wolf)) return;

        // remove visual carrier (armorstand) if any
        summons.removeCarrierOnDeath(wolf);

        // prevent drops
        e.getDrops().clear();
        e.setDroppedExp(0);

        UUID owner = summons.getOwnerUuidFromEntity(wolf);
        UUID puuid = summons.getPokemonUuidFromEntity(wolf);

        if (owner != null && puuid != null) {
            PlayerProfile prof = storage.getProfile(owner);
            PokemonInstance p = prof.findByUuid(puuid);
            if (p != null) {
                p.currentHp = 0;
                storage.markDirty(owner);
            }
        }

        // clear active tracking for this entity (multi-summon)
        if (owner != null) {
            summons.clearActiveByEntity(owner, wolf.getUniqueId());
            Player player = Bukkit.getPlayer(owner);
            if (player != null && player.isOnline()) {
                Integer slot = summons.getPartySlotFromEntity(wolf);
                if (slot != null) {
                    player.sendMessage("§c你的精灵昏厥了！§7(队伍 " + (slot + 1) + ")");
                } else {
                    player.sendMessage("§c你的精灵昏厥了！");
                }
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        summons.recallAll(p, null);
        summons.onPlayerQuit(p.getUniqueId());
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent e) {
        // recall to avoid lost entities
        Player p = e.getPlayer();
        summons.recallAll(p, "§7已自动回收精灵（传送/跨维度）。");
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        summons.recallAll(p, "§7已自动回收精灵（跨世界）。");
    }
}
