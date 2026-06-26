package win.pokedemo;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.Bukkit;

import win.pokedemo.LangManager;

/** Sends a (configurable) join hint telling players to use /poke guide. */
public class GuideJoinHintListener implements Listener {
    private final PokeDemoPlugin plugin;
    private final NamespacedKey KEY_HINT_SENT;

    public GuideJoinHintListener(PokeDemoPlugin plugin) {
        this.plugin = plugin;
        this.KEY_HINT_SENT = new NamespacedKey(plugin, "guide_hint_sent");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        if (plugin.getGuide() == null || !plugin.getGuide().isEnabled()) return;
        Player p = e.getPlayer();

        boolean once = plugin.getGuide().isJoinHintOnce();
        if (once) {
            Byte b = p.getPersistentDataContainer().get(KEY_HINT_SENT, PersistentDataType.BYTE);
            if (b != null && b == (byte) 1) return;
            p.getPersistentDataContainer().set(KEY_HINT_SENT, PersistentDataType.BYTE, (byte) 1);
        }

        // Send a tiny delay so it doesn't get lost in join spam.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            LangManager lang = plugin.getLang();
            if (plugin.getGuide().isJoinHintMultilang()) {
                p.sendMessage("§2提示§8：输入 §3/poke guide §8获取教学之书");
                p.sendMessage("§2提示§8：§3Shift+F §8+ §3物品栏/鼠标滚轮 §8召唤精灵");
                p.sendMessage("§2Tip§8: Use §3/poke guide §8to get the Guide Book");
                p.sendMessage("§2Tip§8: §3Shift+F §8+ §3Hotbar/Mouse Wheel §8to send out Pokémon");
                p.sendMessage("§2ヒント§8：§3/poke guide §8でガイド本を取得できます");
                p.sendMessage("§2ヒント§8：§3Shift+F§8＋§3ホットバー/マウスホイール§8で召喚");
                p.sendMessage("§2안내§8: §3/poke guide §8로 안내서를 받을 수 있어요");
                p.sendMessage("§2안내§8: §3Shift+F §8+ §3단축바/마우스휠 §8로 소환");
                return;
            }
            String msg = (lang == null)
                    ? "§2提示§8：输入 §3/poke guide §8获取教学之书"
                    : lang.ui("guide.join_hint", "§2提示§8：输入 §3/poke guide §8获取教学之书");
            p.sendMessage(msg);

            String msg2 = (lang == null)
                    ? "§2提示§8：§3Shift+F §8+ §3物品栏/鼠标滚轮 §8召唤精灵"
                    : lang.ui("guide.join_hint_summon", "§2提示§8：§3Shift+F §8+ §3物品栏/鼠标滚轮 §8召唤精灵");
            p.sendMessage(msg2);
        }, 20L);
    }
}
