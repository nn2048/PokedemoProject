package win.pokedemo;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Reopen the current battle GUI if the player is still in a battle.
 */
public class BattleCommand implements CommandExecutor {
    private final PokeDemoPlugin plugin;
    private final BattleManager battles;

    public BattleCommand(PokeDemoPlugin plugin, BattleManager battles) {
        this.plugin = plugin;
        this.battles = battles;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;
        if (!battles.reopenBattleGui(player)) {
            player.sendMessage(plugin.getLang().ui("battle.none", "§7当前没有进行中的战斗。"));
        }
        return true;
    }
}
