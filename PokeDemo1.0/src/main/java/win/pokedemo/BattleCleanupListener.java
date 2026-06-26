package win.pokedemo;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerKickEvent;

import java.util.UUID;

/** Ensures battle sessions and battle-only overlays are cleaned up when players leave. */
public class BattleCleanupListener implements Listener {

    private final PokeDemoPlugin plugin;
    private final BattleManager battles;

    public BattleCleanupListener(PokeDemoPlugin plugin, BattleManager battles) {
        this.plugin = plugin;
        this.battles = battles;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        if (battles != null) {
            battles.removeSpectator(id);
            battles.endBattle(id, plugin != null ? plugin.getLang().ui("battle.end.offline", "玩家离线") : "玩家离线");
        }
    }

    @EventHandler
    public void onKick(PlayerKickEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        if (battles != null) {
            battles.removeSpectator(id);
            battles.endBattle(id, plugin != null ? plugin.getLang().ui("battle.end.kicked", "玩家被踢出") : "玩家被踢出");
        }
    }
}
