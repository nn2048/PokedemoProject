package win.pokedemo;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import net.kyori.adventure.text.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Tutorial/guide book system.
 *
 * - Language selection:
 *   - If config.yml has guide.lang = auto (default), use plugins/PokeDemo/lang/lang.yml primary locale.
 *   - Otherwise use guide.lang as an override (e.g. zh_CN / en_US / ja_JP / ko_KR).
 * - /poke guide gives the book
 * - Right click opens book
 * - Left click opens GUI to switch book type (Shift+Left = cycle)
 * - Crafting: 3 sticks shapeless
 */
public class GuideBookManager {
    public static final String GUI_TITLE_KEY = "gui.guide.switch_title";

    private final PokeDemoPlugin plugin;
    private final NamespacedKey KEY_GUIDE_BOOK;
    private final NamespacedKey KEY_GUIDE_BOOK_ID;

    private boolean enabled;
    /** Guide file locale, e.g. zh_CN / en_US */
    private String guideLocale;
    private boolean joinHintMultilang;
    private boolean joinHintOnce;

    private YamlConfiguration guideYaml;

    // Order for cycling.
    private static final List<String> BOOK_ORDER = List.of(
            "welcome", "stones", "breeding", "devices", "legends", "community"
    );

    public GuideBookManager(PokeDemoPlugin plugin) {
        this.plugin = plugin;
        this.KEY_GUIDE_BOOK = new NamespacedKey(plugin, "guide_book");
        this.KEY_GUIDE_BOOK_ID = new NamespacedKey(plugin, "guide_book_id");
    }

    public boolean isEnabled() { return enabled; }
    public String getLang() { return guideLocale; }
    public boolean isJoinHintMultilang() { return joinHintMultilang; }
    public boolean isJoinHintOnce() { return joinHintOnce; }

    public NamespacedKey keyGuideBook() { return KEY_GUIDE_BOOK; }
    public NamespacedKey keyGuideBookId() { return KEY_GUIDE_BOOK_ID; }

    public void ensureDefaultFiles() {
        try {
            Path dir = plugin.getDataFolder().toPath().resolve("lang");
            Files.createDirectories(dir);
            ensureGuideFile("guide/guide_zh_CN.yml", dir.resolve("guide_zh_CN.yml"));
            ensureGuideFile("guide/guide_en_US.yml", dir.resolve("guide_en_US.yml"));
            ensureGuideFile("guide/guide_ja_JP.yml", dir.resolve("guide_ja_JP.yml"));
            ensureGuideFile("guide/guide_ko_KR.yml", dir.resolve("guide_ko_KR.yml"));
        } catch (Exception e) {
            plugin.getLogger().warning("[Guide] Failed to ensure default guide files: " + e.getMessage());
        }
    }

    private void ensureGuideFile(String resPath, Path target) {
        copyTextResourceIfAbsent(resPath, target);
        try (InputStream in = plugin.getResource(resPath)) {
            if (in == null || !Files.exists(target)) return;
            String bundled = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            YamlConfiguration bundledYaml = new YamlConfiguration();
            bundledYaml.loadFromString(bundled);
            YamlConfiguration diskYaml = YamlConfiguration.loadConfiguration(target.toFile());

            List<String> bundledWelcomePages = bundledYaml.getStringList("books.welcome.pages");
            List<String> diskWelcomePages = diskYaml.getStringList("books.welcome.pages");

            boolean changed = false;
            if (!bundledWelcomePages.isEmpty() && diskWelcomePages.size() < bundledWelcomePages.size()) {
                diskYaml.set("books.welcome.pages", bundledWelcomePages);
                changed = true;
            }

            if (changed) {
                diskYaml.save(target.toFile());
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[Guide] Failed to upgrade " + target.getFileName() + ": " + e.getMessage());
        }
    }

    private void copyTextResourceIfAbsent(String resPath, Path target) {
        try {
            if (Files.exists(target)) return;
            try (InputStream in = plugin.getResource(resPath)) {
                if (in == null) return;
                // Preserve UTF-8
                String s = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                Files.writeString(target, s, StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[Guide] Failed to copy " + resPath + ": " + e.getMessage());
        }
    }

    public void reloadFromConfig() {
        enabled = plugin.getConfig().getBoolean("guide.enabled", true);
        String cfg = plugin.getConfig().getString("guide.lang", "auto");
        if (cfg == null || cfg.isBlank() || cfg.equalsIgnoreCase("auto")) {
            String loc = plugin.getLang() == null ? "zh_cn" : plugin.getLang().getPrimaryLocale();
            guideLocale = toGuideLocale(loc);
        } else {
            guideLocale = cfg;
        }
        // Defaults: follow lang.yml (single language) and remind every login.
        // If server owners want the old behavior they can explicitly set these in config.yml.
        joinHintMultilang = plugin.getConfig().getBoolean("guide.join-hint-multilang", false);
        joinHintOnce = plugin.getConfig().getBoolean("guide.join-hint-once-per-player", false);

        loadYaml();
    }

    /** Current GUI title for the guide switch inventory (localized). */
    public Component getSwitchGuiTitle() {
        LangManager lm = plugin.getLang();
        String raw = (lm != null) ? lm.ui(GUI_TITLE_KEY, "§8教程切换") : "§8教程切换";
        return UtilGui.title(raw);
    }

    /** Convert lang.yml locale (zh_cn) to guide file locale (zh_CN). */
    private String toGuideLocale(String loc) {
        if (loc == null) return "zh_CN";
        String l = loc.trim();
        if (l.isEmpty()) return "zh_CN";
        l = l.replace('-', '_');
        if (l.equalsIgnoreCase("zh_cn")) return "zh_CN";
        if (l.equalsIgnoreCase("en_us")) return "en_US";
        if (l.equalsIgnoreCase("ja_jp")) return "ja_JP";
        if (l.equalsIgnoreCase("ko_kr")) return "ko_KR";
        String[] parts = l.split("_");
        if (parts.length == 2) {
            return parts[0].toLowerCase(Locale.ROOT) + "_" + parts[1].toUpperCase(Locale.ROOT);
        }
        return l;
    }

    private void loadYaml() {
        try {
            String fileName = "guide_" + guideLocale + ".yml";
            Path p = plugin.getDataFolder().toPath().resolve("lang").resolve(fileName);
            if (!Files.exists(p)) {
                // Fallback to Chinese
                p = plugin.getDataFolder().toPath().resolve("lang").resolve("guide_zh_CN.yml");
            }
            guideYaml = YamlConfiguration.loadConfiguration(p.toFile());
        } catch (Exception e) {
            plugin.getLogger().warning("[Guide] Failed to load guide YAML: " + e.getMessage());
            guideYaml = new YamlConfiguration();
        }
    }

    public boolean isGuideBook(ItemStack it) {
        if (it == null) return false;
        if (it.getType() != Material.WRITTEN_BOOK) return false;
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return false;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        Byte b = pdc.get(KEY_GUIDE_BOOK, PersistentDataType.BYTE);
        return b != null && b == (byte) 1;
    }

    public String getBookId(ItemStack it) {
        if (!isGuideBook(it)) return null;
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(KEY_GUIDE_BOOK_ID, PersistentDataType.STRING);
    }

    public ItemStack createGuideBook(String bookId) {
        if (guideYaml == null) loadYaml();
        if (bookId == null || bookId.isBlank()) bookId = "welcome";

        String base = "books." + bookId + ".";
        String title = guideYaml.getString(base + "title", "Guide");
        String author = guideYaml.getString(base + "author", "PokeDemo");
        List<String> pages = guideYaml.getStringList(base + "pages");

        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta bm = (BookMeta) book.getItemMeta();
        if (bm == null) return book;
        bm.setTitle(title);
        bm.setAuthor(author);
        if (pages != null && !pages.isEmpty()) {
            // Improve readability in books: Minecraft book background makes light colors hard to read.
            List<String> fixed = new ArrayList<>(pages.size());
            for (String pg : pages) {
                fixed.add(enhanceReadability(pg));
            }
            bm.setPages(fixed);
        } else {
            bm.setPages(List.of("§c该教程内容缺失。请检查 guide_" + guideLocale + ".yml"));
        }

        bm.getPersistentDataContainer().set(KEY_GUIDE_BOOK, PersistentDataType.BYTE, (byte) 1);
        bm.getPersistentDataContainer().set(KEY_GUIDE_BOOK_ID, PersistentDataType.STRING, bookId);

        // Small hint in lore-less way: not shown in book, but helps in inventory.
        bm.setDisplayName("§6§lPokeDemo 教学之书");
        book.setItemMeta(bm);
        return book;
    }

    /** Replace low-contrast colors commonly used in guides with darker alternatives. */
    private String enhanceReadability(String s) {
        if (s == null) return "";
        // §a (green) / §b (aqua) / §e (yellow) / §f (white) are often too bright on the book page.
        return s
                .replace("§a", "§2")
                .replace("§b", "§3")
                .replace("§e", "§9")
                .replace("§f", "§7");
    }

    public String nextBookId(String current) {
        if (current == null) return BOOK_ORDER.getFirst();
        int idx = BOOK_ORDER.indexOf(current);
        if (idx < 0) return BOOK_ORDER.getFirst();
        return BOOK_ORDER.get((idx + 1) % BOOK_ORDER.size());
    }

    public Inventory createSwitchGui(UUID playerId, String currentId) {
        GuiHolder holder = new GuiHolder(GuiType.GUIDE_SWITCH, playerId);
        Inventory inv = Bukkit.createInventory(holder, 9, getSwitchGuiTitle());
        LangManager lm = plugin.getLang();
        java.util.function.BiFunction<String, String, String> ui = (k, fb) -> lm == null ? fb : lm.ui(k, fb);
        // slots 0..5
        inv.setItem(0, guiItem(Material.BOOK, ui.apply("gui.guide.tab.welcome", "§e新手指南"), "welcome", currentId, ui));
        inv.setItem(1, guiItem(Material.NETHER_STAR, ui.apply("gui.guide.tab.stones", "§e世界刷新与资源"), "stones", currentId, ui));
        inv.setItem(2, guiItem(Material.EGG, ui.apply("gui.guide.tab.breeding", "§e孵化与环境偏好"), "breeding", currentId, ui));
        inv.setItem(3, guiItem(Material.REDSTONE, ui.apply("gui.guide.tab.devices", "§e设备研究"), "devices", currentId, ui));
        inv.setItem(4, guiItem(Material.ENDER_EYE, ui.apply("gui.guide.tab.legends", "§e传说之书"), "legends", currentId, ui));
        inv.setItem(5, guiItem(Material.WRITABLE_BOOK, ui.apply("gui.guide.tab.community", "§e社区与支持"), "community", currentId, ui));
        return inv;
    }

    private ItemStack guiItem(Material mat, String name, String bookId, String currentId,
                              java.util.function.BiFunction<String, String, String> ui) {
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        if (m != null) {
            boolean cur = bookId.equalsIgnoreCase(currentId);
            m.setDisplayName((cur ? "§a§l" : "§f") + name);
            List<String> lore = new ArrayList<>();
            lore.add(cur ? ui.apply("gui.guide.tab.selected", "§a当前已选择") : ui.apply("gui.guide.tab.click_to_switch", "§7点击切换"));
            m.setLore(lore);
            m.getPersistentDataContainer().set(KEY_GUIDE_BOOK_ID, PersistentDataType.STRING, bookId);
            it.setItemMeta(m);
        }
        return it;
    }

    public void registerGuideRecipe() {
        try {
            if (!enabled) return;
            NamespacedKey key = new NamespacedKey(plugin, "guide_book");
            ItemStack out = createGuideBook("welcome");
            ShapelessRecipe r = new ShapelessRecipe(key, out);
            r.addIngredient(new RecipeChoice.MaterialChoice(Material.STICK));
            r.addIngredient(new RecipeChoice.MaterialChoice(Material.STICK));
            r.addIngredient(new RecipeChoice.MaterialChoice(Material.STICK));
            Bukkit.addRecipe(r);
        } catch (Exception e) {
            plugin.getLogger().warning("[Guide] Failed to register recipe: " + e.getMessage());
        }
    }

    public void openBook(Player p, ItemStack book) {
        if (p == null || book == null) return;
        try {
            p.openBook(book);
        } catch (Throwable t) {
            // Some forks throw if book meta invalid.
            p.sendMessage("§c无法打开教程书。请联系管理员。");
        }
    }
}