
package win.pokedemo;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

public class SendOutCommand implements CommandExecutor {
    private final PokeDemoPlugin plugin;
    private final Dex dex;
    private final Storage storage;
    private final Map<UUID, UUID> active = new HashMap<>();

    public SendOutCommand(PokeDemoPlugin plugin, Dex dex, Storage storage) {
        this.plugin = plugin;
        this.dex = dex;
        this.storage = storage;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;
        int slot = args.length >= 1 ? Util.parseInt(args[0], 1) : 1;
        slot = Util.clamp(slot, 1, 6);

        PlayerProfile prof = storage.getProfile(player.getUniqueId());
        if (prof.party.size() < slot) {
            player.sendMessage("§cNo Pokémon in that slot.");
            return true;
        }

        // recall previous
        UUID prev = active.remove(player.getUniqueId());
        if (prev != null) {
            var ent = player.getWorld().getEntity(prev);
            if (ent != null) ent.remove();
        }

        PokemonInstance p = prof.party.get(slot - 1);
        Species s = dex.getSpecies(p.speciesId);
        if (s == null) {
            player.sendMessage("§cInvalid species.");
            return true;
        }

        Location loc = player.getLocation().add(player.getLocation().getDirection().normalize().multiply(1.5));
        Wolf wolf = player.getWorld().spawn(loc, Wolf.class, w -> {
            w.setAdult();
            w.setTamed(true);
            w.setOwner(player);
            w.setCustomNameVisible(true);
            w.setCustomName("§b" + s.name() + " §7Lv." + p.level);
            w.setCollarColor(org.bukkit.DyeColor.LIGHT_BLUE);
        });

        wolf.getPersistentDataContainer().set(plugin.KEY_OWNER, PersistentDataType.STRING, player.getUniqueId().toString());
        wolf.getPersistentDataContainer().set(plugin.KEY_SPECIES, PersistentDataType.STRING, s.id());
        wolf.getPersistentDataContainer().set(plugin.KEY_LEVEL, PersistentDataType.INTEGER, p.level);

        active.put(player.getUniqueId(), wolf.getUniqueId());
        player.sendMessage("§aSent out " + s.name() + "!");
        return true;
    }
}
