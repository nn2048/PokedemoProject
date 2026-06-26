
package win.pokedemo;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.entity.Entity;

import java.util.UUID;

public class CaptureListener implements Listener {
    private final PokeDemoPlugin plugin;
    private final Dex dex;
    private final Storage storage;
    private final ItemFactory items;
    private final BattleManager battles;

    public CaptureListener(PokeDemoPlugin plugin, Dex dex, Storage storage, ItemFactory items, BattleManager battles) {
        this.plugin = plugin;
        this.dex = dex;
        this.storage = storage;
        this.items = items;
        this.battles = battles;
    }

    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Wolf wolf = resolveCarrierWolf(e.getRightClicked());
        if (wolf == null) return;
        if (!isWild(wolf)) return;

        Player player = e.getPlayer();
        ItemStack inHand = player.getInventory().getItemInMainHand();
        // Control wand should not start battle/capture
        if (items.isControlWand(inHand)) return;
        String ballType = items.getBallType(inHand);
        // Prevent throwing balls directly at entities to avoid bypassing battle rules.
        // Use battle GUI capture button instead.
        String itemId = items.getItemId(inHand);
        if (ballType != null || (itemId != null && (itemId.endsWith("_ball") || itemId.equals("poke_ball") || itemId.equals("great_ball") || itemId.equals("ultra_ball") || itemId.equals("master_ball")))) {
            e.setCancelled(true);
            player.sendMessage(plugin.getLang().ui("capture.throw_disabled", "§e捕捉功能正在重做中，暂时无法丢球捕捉。请先进入战斗。"));
            return;
        }

        // If not holding a ball, treat right-click as "initiate battle" (Cobblemon-like UX).
        if (ballType == null) {
            if (!plugin.getConfig().getBoolean("battle.enabled", true)) return;
            e.setCancelled(true);
            UUID pid = player.getUniqueId();
            if (battles.isInBattle(pid)) {
                player.sendMessage(plugin.getLang().ui("battle.already_in", "§c你正在战斗中，无法再次进入。§7(/battle leave)"));
                return;
            }
            battles.startWildBattle(player, wolf);
            return;
        }

        e.setCancelled(true);

        String speciesId = wolf.getPersistentDataContainer().get(plugin.KEY_SPECIES, PersistentDataType.STRING);
        Integer level = wolf.getPersistentDataContainer().get(plugin.KEY_LEVEL, PersistentDataType.INTEGER);
        Species s = dex.getSpecies(speciesId);
        if (s == null || level == null) {
            player.sendMessage(plugin.getLang().ui("capture.missing_species", "§c这个野生精灵数据异常（缺少物种）。"));
            return;
        }

        // Consume 1 ball
        inHand.setAmount(inHand.getAmount() - 1);

        // Build a PokemonInstance based on wild entity
        PokemonInstance wild = PokemonInstance.createWild(s, level, dex);
        wild.originalTrainer = player.getUniqueId();
        wild.originalTrainerName = player.getName();
        wild.ballId = (ballType == null || ballType.isBlank()) ? "poke_ball" : ballType;
        // Roughly simulate current HP ratio if it was damaged (for demo, assume full)
        wild.currentHp = wild.maxHp(s);

        double statusMul = 1.0; // demo; future: sleep/paralyze multipliers
        double ballMul = plugin.getConfig().getDouble("capture.balls." + ballType, 1.0);

        double hpFactor = (3.0 * wild.maxHp(s) - 2.0 * wild.currentHp) / (3.0 * wild.maxHp(s));
        hpFactor = Util.clamp01(hpFactor);

        double base = (s.catchRate() / 255.0);
        double chance = Util.clamp01(base * ballMul * statusMul * (0.2 + 0.8 * hpFactor));

        if (Util.RND.nextDouble() < chance) {
            wolf.remove();
            storage.getProfile(player.getUniqueId()).depositToPartyOrPc(wild);
            // Automatically sanitize newly obtained pokemon's moves so players don't need to run
            // /pokedemo cleanseMoves every time.
            storage.cleanseMoves(player.getUniqueId(), dex);
            storage.markDirty(player.getUniqueId());
            LangManager lang = PokeDemoPlugin.INSTANCE != null ? PokeDemoPlugin.INSTANCE.getLang() : null;
            String display = (lang != null) ? lang.species(s.id(), s.name()) : s.name();
            player.sendMessage(plugin.getLang().uiFmt("capture.success", "§a成功捕捉：§f{mon} §a(Lv.{lv})！", java.util.Map.of("mon", display, "lv", String.valueOf(wild.level))));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
            battles.endBattleIfTarget(player.getUniqueId(), wolf.getUniqueId());
        } else {
            player.sendMessage(plugin.getLang().ui("capture.break_free", "§e精灵挣脱了精灵球！"));
            player.playSound(player.getLocation(), Sound.ENTITY_WOLF_GROWL, 1f, 1.0f);
        }
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!plugin.getConfig().getBoolean("battle.intercept-combat", true)) return;
        if (!(e.getDamager() instanceof Player player)) return;
        Wolf wolf = resolveCarrierWolf(e.getEntity());
        if (wolf == null) return;
        if (items.isControlWand(player.getInventory().getItemInMainHand())) return;
        if (!isWild(wolf)) return;

        if (!plugin.getConfig().getBoolean("battle.enabled", true)) return;

        // Start battle GUI and cancel damage
        e.setCancelled(true);
        UUID pid = player.getUniqueId();
        if (battles.isInBattle(pid)) {
            player.sendMessage(plugin.getLang().ui("battle.already_in_short", "§c你正在战斗中，无法再次进入。"));
            return;
        }
        battles.startWildBattle(player, wolf);
    }

    private boolean isWild(Wolf wolf) {
        Byte b = wolf.getPersistentDataContainer().get(plugin.KEY_WILD, PersistentDataType.BYTE);
        return b != null && b == (byte)1;
    }

    /**
     * Allow clicking the visual ItemDisplay/TextDisplay by resolving back to the logical carrier wolf.
     */
    private Wolf resolveCarrierWolf(Entity clicked) {
        if (clicked == null) return null;
        if (clicked instanceof Wolf w) return w;

        // Visual entities store the carrier uuid in PDC.
        String owner = clicked.getPersistentDataContainer().get(plugin.KEY_CARRIER_OWNER, PersistentDataType.STRING);
        if (owner == null) return null;
        try {
            Entity e = clicked.getWorld().getEntity(UUID.fromString(owner));
            if (e instanceof Wolf w) return w;
        } catch (IllegalArgumentException ignored) {}
        return null;
    }
}
