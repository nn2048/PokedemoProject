package win.pokedemo;

import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.persistence.PersistentDataType;

/**
 * Right-clicking a nurse NPC heals the player's party (same as healer machine).
 */
public class NurseListener implements Listener {
    private final PokeDemoPlugin plugin;
    private final NamespacedKey KEY_NURSE;

    public NurseListener(PokeDemoPlugin plugin) {
        this.plugin = plugin;
        this.KEY_NURSE = new NamespacedKey(plugin, "nurse_npc");
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent e) {
        Entity ent = e.getRightClicked();
        if (ent == null) return;
        Byte b = ent.getPersistentDataContainer().get(KEY_NURSE, PersistentDataType.BYTE);
        if (b == null || b == 0) return;

        e.setCancelled(true);
        Player p = e.getPlayer();

        // Prevent healing in battle
        try {
            BattleManager bm = plugin.battles();
            if (bm != null && bm.isInBattle(p.getUniqueId())) {
                p.sendMessage("§c战斗中不能使用该功能。");
                return;
            }
        } catch (Throwable ignored) {}


        int healed = plugin.healParty(p, true);
        if (healed > 0) {
            p.sendMessage("§a护士帮你治疗了队伍中的 " + healed + " 只精灵。\n§7（回满血、清除异常状态并恢复PP）");
            try { p.playSound(p.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.8f, 1.2f); } catch (Throwable ignored) {}
        } else {
            p.sendMessage("§7你的队伍看起来已经是健康状态了。");
            try { p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.3f); } catch (Throwable ignored) {}
        }
    }
}
