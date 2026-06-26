package win.pokedemo;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 纯插件无法监听客户端任意按键（例如单独的“R”键）。
 *
 * 本实现使用原版“交换副手物品(默认F)”作为热键载体：
 * - 只在玩家【潜行(Shift)】时触发召唤/回收，并取消本次交换，避免打断正常游玩。
 * - 玩家可以在客户端把“交换副手物品”改键为 R，即可获得“Shift+R 召唤/回收”的体验。
 *
 * 这样不会占用“丢弃物品(Q)”按键，也不会影响正常丢弃逻辑。
 */
public class SummonHotkeyListener implements Listener {
    private final SummonManager summons;
    private final PokeDemoPlugin plugin;
    private final Map<UUID, Long> cooldownMs = new HashMap<>();

    public SummonHotkeyListener(PokeDemoPlugin plugin, SummonManager summons) {
        this.plugin = plugin;
        this.summons = summons;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        Player p = e.getPlayer();

        // 仅在潜行时触发，避免影响玩家平时交换副手
        if (!p.isSneaking()) return;

        // 取消真正的交换副手行为
        e.setCancelled(true);

        // Starter selection gate: if the player hasn't chosen a starter yet,
        // open the starter GUI instead of arming the summon combo.
        try {
            Storage storage = plugin.getStorage();
            if (storage != null) {
                PlayerProfile prof = storage.getProfile(p.getUniqueId());
                if (prof != null && !prof.starterChosen) {
                    UtilGui.openStarterSelect(p);
                    return;
                }
            }
        } catch (Throwable ignored) {
            // Never block summon key due to GUI issues.
        }

        // 简单冷却，防止按键连点造成抖动
        long now = System.currentTimeMillis();
        long last = cooldownMs.getOrDefault(p.getUniqueId(), 0L);
        if (now - last < 250) return;
        cooldownMs.put(p.getUniqueId(), now);

        // Arm combo: require an additional hotbar slot change within a short window.
        long windowMs = (long) plugin.getConfig().getInt("summon.combo.window-ms", 800);
        plugin.armSummonCombo(p.getUniqueId());
        // Optional hint (quiet by default)
        if (plugin.getConfig().getBoolean("summon.combo.debug-hint", false)) {
            p.sendMessage("§7[召唤] 组合键已准备：§fShift+F §7后在 " + windowMs + "ms 内切换到 1~6 号快捷栏触发。");
        }
    }
}
