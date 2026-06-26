
package win.pokedemo;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

public class PcCommand implements CommandExecutor {
    private final Storage storage;

    public PcCommand(Storage storage) {
        this.storage = storage;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;
        if (!player.hasPermission("pokedemo.command.pc")) {
            player.sendMessage("§c你没有权限使用 /pc。§7请通过§aPC方块§7或其他方式访问电脑盒子。");
            return true;
        }
        UtilGui.openPc(player, storage, 0);
        return true;
    }
}
