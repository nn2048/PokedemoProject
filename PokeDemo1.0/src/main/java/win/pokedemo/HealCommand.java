package win.pokedemo;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class HealCommand implements CommandExecutor {
    private final PokeDemoPlugin plugin;
    private final Dex dex;
    private final Storage storage;
    private final BattleManager battles;

    public HealCommand(PokeDemoPlugin plugin, Dex dex, Storage storage, BattleManager battles) {
        this.plugin = plugin;
        this.dex = dex;
        this.storage = storage;
        this.battles = battles;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getLang().ui("cmd.player_only", "§c仅玩家可用。"));
            return true;
        }
        // /heal is an admin convenience command.
        // Normal players should heal via the overworld healer machine (or other in-world facilities).
        if (!player.hasPermission("pokedemo.admin.heal")) {
            player.sendMessage(plugin.getLang().ui("cmd.heal.no_perm", "§c你没有权限使用 /heal。§7普通玩家请使用§a治疗机§7。"));
            return true;
        }

        int healed = plugin.healParty(player, false);
        if (healed > 0) {
            player.sendMessage(plugin.getLang().uiFmt("cmd.heal.done", "§a已治疗队伍中的 {n} 只精灵：回满血、清除异常状态并恢复PP。", java.util.Map.of("n", String.valueOf(healed))));
        }
        return true;
    }
}
