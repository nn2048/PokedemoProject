package win.pokedemo;

import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class DebugCommand implements CommandExecutor {
    private final PlantManager plants;
    public DebugCommand(PlantManager plants) { this.plants = plants; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("player only");
            return true;
        }
        Block b = p.getTargetBlockExact(8);
        if (b == null) {
            p.sendMessage("§cNo target block within 8 blocks.");
            return true;
        }
        p.sendMessage("§eTarget: §f" + b.getType() + " §7" + b.getX()+","+b.getY()+","+b.getZ());
        p.sendMessage("§eBlockData: §f" + b.getBlockData().getAsString());
        var pi = plants.get(b);
        if (pi == null) {
            p.sendMessage("§cNot a registered plant instance in PlantManager.");
        } else {
            p.sendMessage("§aPlant: §f" + pi.kind + " " + pi.id + " stage=" + pi.stage);
        }
        return true;
    }
}
