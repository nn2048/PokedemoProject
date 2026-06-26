package win.pokedemo;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;

/** /nurse - spawn a static nurse NPC (villager with no AI). */
public class NurseCommand implements CommandExecutor {
    private final PokeDemoPlugin plugin;

    public NurseCommand(PokeDemoPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("§c该指令只能由玩家执行。");
            return true;
        }
        Location loc = p.getLocation();
        NamespacedKey key = new NamespacedKey(plugin, "nurse_npc");
        Villager v = loc.getWorld().spawn(loc, Villager.class, villager -> {
            villager.setAI(false);
            villager.setInvulnerable(true);
            villager.setSilent(true);
            villager.setCollidable(false);
            villager.setPersistent(true);
            villager.setRemoveWhenFarAway(false);
            try { villager.setGravity(true); } catch (Throwable ignored) {}
            villager.setCustomNameVisible(true);
            villager.setCustomName("§d护士");
            try { villager.setProfession(Villager.Profession.CLERIC); } catch (Throwable ignored) {}
            villager.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        });
        p.sendMessage("§a已召唤护士：§f" + v.getLocation().getBlockX() + " " + v.getLocation().getBlockY() + " " + v.getLocation().getBlockZ());
        return true;
    }
}
