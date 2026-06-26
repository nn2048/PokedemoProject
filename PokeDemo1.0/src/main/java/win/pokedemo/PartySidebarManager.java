package win.pokedemo;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Cobblemon-like left sidebar party list based on the Bukkit scoreboard sidebar.
 * This is text-only, but it stays bound to the real party order / hp / major status.
 */
public class PartySidebarManager {
    private static final String OBJECTIVE_ID = "pokedemo_party";
    private static final String[] ENTRIES = {
            ChatColor.BLACK.toString(),
            ChatColor.DARK_BLUE.toString(),
            ChatColor.DARK_GREEN.toString(),
            ChatColor.DARK_AQUA.toString(),
            ChatColor.DARK_RED.toString(),
            ChatColor.DARK_PURPLE.toString(),
            ChatColor.GOLD.toString(),
            ChatColor.GRAY.toString(),
            ChatColor.DARK_GRAY.toString(),
            ChatColor.BLUE.toString(),
            ChatColor.GREEN.toString(),
            ChatColor.AQUA.toString()
    };

    private final PokeDemoPlugin plugin;
    private final Map<UUID, Scoreboard> personalBoards = new HashMap<>();
    private int taskId = -1;

    public PartySidebarManager(PokeDemoPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        shutdown();
        long interval = Math.max(10L, plugin.getConfig().getLong("party-sidebar.update-ticks", 20L));
        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::refreshAllOnline, 1L, interval);
    }

    public void shutdown() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            try {
                clearSidebar(p);
            } catch (Exception ignored) {}
        }
    }

    public void refreshAllOnline() {
        if (!plugin.getConfig().getBoolean("party-sidebar.enabled", true)) {
            for (Player p : Bukkit.getOnlinePlayers()) clearSidebar(p);
            return;
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            refreshPlayer(p);
        }
    }

    public void refreshPlayer(Player player) {
        if (player == null || !player.isOnline()) return;
        if (plugin.getBridgeSyncManager() != null && plugin.getBridgeSyncManager().isBridgeClient(player.getUniqueId())) {
            clearSidebar(player);
            return;
        }
        if (!plugin.getConfig().getBoolean("party-sidebar.enabled", true)) {
            clearSidebar(player);
            return;
        }

        Scoreboard board = ensurePersonalBoard(player);
        Objective obj = board.getObjective(OBJECTIVE_ID);
        if (obj == null) {
            obj = board.registerNewObjective(OBJECTIVE_ID, "dummy", color(plugin.getConfig().getString("party-sidebar.title", "&6✦ 精灵队伍 ✦")));
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
            for (int i = 0; i < ENTRIES.length; i++) {
                Team team = board.getTeam(teamName(i));
                if (team == null) team = board.registerNewTeam(teamName(i));
                String entry = ENTRIES[i];
                if (!team.hasEntry(entry)) team.addEntry(entry);
                obj.getScore(entry).setScore(ENTRIES.length - i);
            }
        } else {
            obj.setDisplayName(color(plugin.getConfig().getString("party-sidebar.title", "&6✦ 精灵队伍 ✦")));
            if (obj.getDisplaySlot() != DisplaySlot.SIDEBAR) obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        }

        PlayerProfile prof = plugin.getStorage().getProfile(player.getUniqueId());
        List<PokemonInstance> party = prof.party;
        for (int slot = 0; slot < 6; slot++) {
            PokemonInstance mon = (party != null && slot < party.size()) ? party.get(slot) : null;
            updateLine(board, slot * 2, headerLine(player.getUniqueId(), slot, mon));
            updateLine(board, slot * 2 + 1, detailLine(mon));
        }
    }

    public void clearSidebar(Player player) {
        if (player == null) return;
        Scoreboard board = personalBoards.remove(player.getUniqueId());
        if (board == null) {
            board = player.getScoreboard();
        }
        Objective obj = board.getObjective(OBJECTIVE_ID);
        if (obj != null) obj.unregister();
        for (int i = 0; i < ENTRIES.length; i++) {
            Team team = board.getTeam(teamName(i));
            if (team != null) team.unregister();
        }
        try {
            if (player.isOnline() && player.getScoreboard() == board) {
                player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            }
        } catch (Throwable ignored) {}
    }

    private Scoreboard ensurePersonalBoard(Player player) {
        Scoreboard board = personalBoards.get(player.getUniqueId());
        if (board == null) {
            board = Bukkit.getScoreboardManager().getNewScoreboard();
            personalBoards.put(player.getUniqueId(), board);
        }
        try {
            if (player.getScoreboard() != board) {
                player.setScoreboard(board);
            }
        } catch (Throwable ignored) {}
        return board;
    }

    private void updateLine(Scoreboard board, int index, String fullText) {
        Team team = board.getTeam(teamName(index));
        if (team == null) {
            team = board.registerNewTeam(teamName(index));
            team.addEntry(ENTRIES[index]);
        }
        String[] parts = splitForTeam(fullText);
        team.setPrefix(parts[0]);
        team.setSuffix(parts[1]);
    }

    private String headerLine(UUID playerId, int slot, PokemonInstance mon) {
        int shownSlot = slot + 1;
        if (mon == null) {
            return color("&8" + shownSlot + ". ——");
        }
        boolean active = plugin.getSummonManager() != null && plugin.getSummonManager().isSlotActive(playerId, slot);
        String marker = active ? "&a▶ " : "&7• ";
        String name = trimVisible(mon.displayName(), 10);
        String lv = "&7Lv" + mon.level;
        if (mon.currentHp <= 0) {
            return color(marker + "&c" + shownSlot + ". " + name + " " + lv);
        }
        return color(marker + "&f" + shownSlot + ". " + name + " " + lv);
    }

    private String detailLine(PokemonInstance mon) {
        if (mon == null) return color("&8  （空槽）");
        Species s = plugin.getDex() != null ? plugin.getDex().getSpecies(mon.speciesId) : null;
        int maxHp = Math.max(1, mon.maxHp(s));
        int hp = Math.max(0, Math.min(mon.currentHp, maxHp));
        if (mon.isEgg) {
            return color("&e  蛋 &7步数:" + Math.max(0, mon.eggStepsRemaining));
        }
        String hpBar = hpBar(hp, maxHp, 8);
        String faint = hp <= 0 ? " &8[力竭]" : "";
        String status = compactStatus(mon.status);
        return color("&7  " + hpBar + " &f" + hp + "&7/&f" + maxHp + status + faint);
    }

    private String hpBar(int hp, int maxHp, int segments) {
        double ratio = maxHp <= 0 ? 0.0 : (hp / (double) maxHp);
        int filled = (int) Math.round(ratio * segments);
        if (filled < 0) filled = 0;
        if (filled > segments) filled = segments;
        String color;
        if (ratio <= 0.20) color = "&c";
        else if (ratio <= 0.50) color = "&e";
        else color = "&a";
        StringBuilder sb = new StringBuilder();
        sb.append(color);
        for (int i = 0; i < filled; i++) sb.append('▌');
        sb.append("&8");
        for (int i = filled; i < segments; i++) sb.append('▌');
        return sb.toString();
    }

    private String compactStatus(String status) {
        if (status == null || status.isBlank() || "none".equalsIgnoreCase(status)) return "";
        String s = status.toLowerCase(Locale.ROOT);
        return switch (s) {
            case "poison" -> " &d[毒]";
            case "toxic" -> " &5[剧毒]";
            case "burn" -> " &c[灼]";
            case "paralyze" -> " &e[麻]";
            case "sleep" -> " &b[眠]";
            case "freeze" -> " &f[冻]";
            default -> " &7[" + trimVisible(status, 4) + "]";
        };
    }

    private static String[] splitForTeam(String s) {
        if (s == null) return new String[]{"", ""};
        if (s.length() <= 64) return new String[]{s, ""};
        String prefix = s.substring(0, 64);
        String suffix = s.substring(64, Math.min(s.length(), 128));
        if (!ChatColor.getLastColors(prefix).isEmpty() && !suffix.isEmpty()) {
            suffix = ChatColor.getLastColors(prefix) + suffix;
            if (suffix.length() > 64) suffix = suffix.substring(0, 64);
        }
        return new String[]{prefix, suffix};
    }

    private static String trimVisible(String s, int maxChars) {
        if (s == null) return "";
        String stripped = ChatColor.stripColor(color(s));
        if (stripped == null) stripped = s;
        if (stripped.length() <= maxChars) return stripped;
        return stripped.substring(0, Math.max(1, maxChars - 1)) + "…";
    }

    private static String teamName(int index) {
        return "pd_party_" + index;
    }

    private static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s);
    }
}
