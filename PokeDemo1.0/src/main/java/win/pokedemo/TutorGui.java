package win.pokedemo;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class TutorGui {
    private TutorGui() {}

    public static void openMoveList(Player player, List<String> moves) {
        PokeDemoPlugin plugin = PokeDemoPlugin.INSTANCE;
        if (plugin == null) return;
        GuiHolder holder = new GuiHolder(GuiType.TUTOR_MOVE_LIST, player.getUniqueId());
        holder.tutorMoves = (moves == null) ? new ArrayList<>() : new ArrayList<>(moves);
        Inventory inv = Bukkit.createInventory(holder, 54, UtilGui.title("§b招式教学师"));
        for (int i = 0; i < 54; i++) inv.setItem(i, UtilGui.button(Material.GRAY_STAINED_GLASS_PANE, " ", List.of()));
        int[] slots = new int[]{10,11,12,13,14,15,16,28,29,30};
        Dex dex = plugin.getDex();
        LangManager lang = plugin.getLang();
        for (int i = 0; i < holder.tutorMoves.size() && i < slots.length; i++) {
            String mid = holder.tutorMoves.get(i);
            Move m = dex.getMoveOrPlaceholder(mid);
            List<String> lore = new ArrayList<>();
            lore.add("§7点击选择这招式，然后为精灵教学");
            lore.add("§8教学师招式");
            lore.add("§7属性：§f" + m.type());
            lore.add("§7分类：§f" + m.category());
            lore.add("§7威力：§f" + (m.power() <= 0 ? "—" : String.valueOf(m.power())));
            lore.add("§7命中：§f" + (m.accuracy() <= 0 ? "—" : String.valueOf(m.accuracy())));
            lore.add("§7PP：§f" + m.pp());
            inv.setItem(slots[i], UtilGui.button(Material.ENCHANTED_BOOK, "§e" + lang.move(mid, null), lore));
        }
        inv.setItem(49, UtilGui.button(Material.BARRIER, "§c关闭", List.of()));
        player.openInventory(inv);
    }

    public static void openPartySelect(Player player, Storage storage, String moveId, List<String> tutorMoves) {
        PokeDemoPlugin plugin = PokeDemoPlugin.INSTANCE;
        if (plugin == null) return;
        PlayerProfile prof = storage.getProfile(player.getUniqueId());
        GuiHolder holder = new GuiHolder(GuiType.TUTOR_PARTY_SELECT, player.getUniqueId());
        holder.tutorSelectedMove = moveId;
        holder.tutorMoves = (tutorMoves == null) ? new ArrayList<>() : new ArrayList<>(tutorMoves);
        Inventory inv = Bukkit.createInventory(holder, 27, UtilGui.title("§b选择要教学的精灵"));
        for (int i = 0; i < 27; i++) inv.setItem(i, UtilGui.button(Material.GRAY_STAINED_GLASS_PANE, " ", List.of()));
        for (int i = 0; i < 6; i++) {
            if (prof != null && i < prof.party.size()) {
                PokemonInstance p = prof.party.get(i);
                ItemStack icon = UtilGui.pokemonIcon(p);
                ItemMeta meta = icon.getItemMeta();
                meta.setDisplayName("§a" + p.displayName() + " §7Lv." + p.level);
                List<String> lore = new ArrayList<>();
                lore.add("§7点击让这只精灵学习：");
                lore.add("§e" + plugin.getLang().move(moveId, null));
                if (p.isEgg) lore.add("§c蛋不能学习招式");
                meta.setLore(lore);
                icon.setItemMeta(meta);
                inv.setItem(i, icon);
            }
        }
        inv.setItem(26, UtilGui.button(Material.BARRIER, "§c返回", List.of("§7返回教学招式列表")));
        player.openInventory(inv);
    }
}
