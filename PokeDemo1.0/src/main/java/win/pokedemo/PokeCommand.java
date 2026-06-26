package win.pokedemo;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

import win.pokedemo.LangManager;

public class PokeCommand implements CommandExecutor, TabCompleter {
    private final PokeDemoPlugin plugin;
    private final ItemFactory items;

    public PokeCommand(PokeDemoPlugin plugin, ItemFactory items) {
        this.plugin = plugin;
        this.items = items;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("仅玩家可用。");
            return true;
        }

        if (args.length == 0) {
            LangManager lang = plugin.getLang();
            player.sendMessage(lang == null ? "§2Poke 指令：" : lang.ui("cmd.poke.header", "§2Poke 指令："));
            player.sendMessage(lang == null ? "§6/poke control §8获取“调试魔杖”（用于调整精灵模型大小/高度，§4仅 OP§8）" : lang.ui("cmd.poke.control", "§6/poke control §8获取“调试魔杖”（用于调整精灵模型大小/高度，§4仅 OP§8）"));
            player.sendMessage(lang == null ? "§6/poke guide §8获取教学之书（右键打开，左键切换）" : lang.ui("cmd.poke.guide", "§6/poke guide §8获取教学之书（右键打开，左键切换）"));
            player.sendMessage(lang == null ? "§6/poke rename <1-6> <昵称|clearname> §8给队伍中的精灵改名" : "§6/poke rename <1-6> <昵称|clearname> §8给队伍中的精灵改名");
            return true;
        }

        String sub = args[0].toLowerCase();
        if (sub.equals("control")) {
            // Only operators can obtain the control wand.
            // This wand can modify species scale/offset config and is considered admin tooling.
            if (!player.isOp()) {
                player.sendMessage("§c你没有权限使用该指令。§7（仅 OP 可用）");
                return true;
            }
            player.getInventory().addItem(items.createControlWand());
            player.sendMessage("§a已获得：§d精灵调试魔杖§7（对着精灵 左键缩小/右键放大，潜行+左键降低/潜行+右键抬高，会自动保存并热更新）");
            return true;
        }

        if (sub.equals("rename")) {
            if (args.length < 3) {
                player.sendMessage("§c用法：/poke rename <队伍槽位1-6> <昵称|clearname>");
                return true;
            }
            int slot;
            try { slot = Integer.parseInt(args[1]) - 1; } catch (Exception ex) { player.sendMessage("§c槽位请输入 1~6。"); return true; }
            if (slot < 0 || slot >= 6) { player.sendMessage("§c槽位请输入 1~6。"); return true; }
            Storage storage = plugin.getStorage();
            PlayerProfile prof = storage.getProfile(player.getUniqueId());
            if (slot >= prof.party.size() || prof.party.get(slot) == null) { player.sendMessage("§c该槽位没有精灵。"); return true; }
            PokemonInstance p = prof.party.get(slot);
            String value = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)).trim();
            if (value.equalsIgnoreCase("clearname") || value.equalsIgnoreCase("none")) {
                p.nickname = null;
                storage.markDirty(player.getUniqueId());
                player.sendMessage("§a已清除昵称。");
                return true;
            }
            value = value.replace('§', ' ').replace('&', ' ').trim();
            if (value.length() > 16) value = value.substring(0, 16);
            if (value.isBlank()) { player.sendMessage("§c昵称不能为空。"); return true; }
            p.nickname = value;
            storage.markDirty(player.getUniqueId());
            player.sendMessage("§a已改名为：§f" + value + "§a。");
            return true;
        }

        if (sub.equals("guide")) {
            if (plugin.getGuide() == null || !plugin.getGuide().isEnabled()) {
                LangManager lang = plugin.getLang();
                player.sendMessage(lang == null ? "§4教学之书未启用。" : lang.ui("guide.disabled", "§4教学之书未启用。"));
                return true;
            }
            player.getInventory().addItem(plugin.getGuide().createGuideBook("welcome"));
            LangManager lang = plugin.getLang();
            player.sendMessage(lang == null ? "§2已获得教学之书：§3右键打开§8，§3左键切换教程类型" : lang.ui("guide.received", "§2已获得教学之书：§3右键打开§8，§3左键切换教程类型"));
            return true;
        }

        LangManager lang = plugin.getLang();
        player.sendMessage(lang == null ? "§4未知子命令。用法：/poke control | /poke guide | /poke rename" : "§4未知子命令。用法：/poke control | /poke guide | /poke rename");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            if (sender.isOp()) out.add("control");
            out.add("guide");
            out.add("rename");
        }
        return out;
    }
}
