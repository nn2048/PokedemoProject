package win.pokedemo;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * In-game recipe browser data.
 * We keep this separate from Bukkit recipes so we can provide categories and a clean UI.
 */
public class RecipeBook {

    public enum Category {
        BALLS("§e精灵球"),
        DEVICES("§d设备"),
        ITEMS("§a道具"),
        OTHER("§7其它");

        public final String displayName;
        Category(String displayName) { this.displayName = displayName; }
    }

    public static class RecipeDef {
        public final String key; // stable id
        public final Category category;
        public final String displayName;
        public final ItemStack output;
        public final ItemStack[] grid9; // 0..8
        public final String note;

        public RecipeDef(String key, Category category, String displayName, ItemStack output, ItemStack[] grid9, String note) {
            this.key = key;
            this.category = category;
            this.displayName = displayName;
            this.output = output;
            this.grid9 = grid9;
            this.note = note;
        }
    }

    private final Map<Category, List<RecipeDef>> byCat = new EnumMap<>(Category.class);
    private final Map<String, RecipeDef> byKey = new HashMap<>();

    public RecipeBook() {
        for (Category c : Category.values()) byCat.put(c, new ArrayList<>());
    }

    public List<Category> categories() {
        return List.of(Category.BALLS, Category.DEVICES, Category.ITEMS, Category.OTHER);
    }

    public List<RecipeDef> list(Category c) {
        return Collections.unmodifiableList(byCat.getOrDefault(c, List.of()));
    }

    public RecipeDef get(String key) { return byKey.get(key); }

    private void add(RecipeDef def) {
        byCat.get(def.category).add(def);
        byKey.put(def.key, def);
    }

    /** Build recipe list from the plugin's current registered items (only craftables). */
    public static RecipeBook build(PokeDemoPlugin plugin) {
        RecipeBook book = new RecipeBook();
        if (plugin == null) return book;
        ItemRegistry reg = plugin.getItemRegistry();
        ItemFactory items = plugin.getItems();
        LangManager lang = plugin.getLang();
        if (reg == null || items == null) return book;

        // Machines
        ItemDef pc = reg.get("pc_machine");
        ItemDef healer = reg.get("healer_machine");
        ItemDef pasture = reg.get("pasture_machine");
        ItemDef fossil = reg.get("fossil_machine");
        ItemDef analyzer = reg.get("fossil_analyzer");
        ItemDef trade = reg.get("trade_machine");
        ItemDef clone = reg.get("clone_machine");
        ItemDef pokeRod = reg.get("poke_rod");
        ItemDef greatRod = reg.get("great_rod");
        ItemDef ultraRod = reg.get("ultra_rod");
        ItemDef masterRod = reg.get("master_rod");
        ItemDef loveRod = reg.get("love_rod");
        if (pc != null) {
            book.add(new RecipeDef(
                    "pc_machine",
                    Category.DEVICES,
                    (lang != null ? lang.item(pc.id, "PC") : "PC"),
                    items.createItem(pc, lang, 1),
                    grid(
                            null, Material.GOLD_INGOT, null,
                            null, Material.IRON_DOOR, null,
                            null, Material.CHEST, null
                    ),
                    "竖向摆放：金锭-铁门-箱子"
            ));
        }
        if (healer != null) {
            book.add(new RecipeDef(
                    "healer_machine",
                    Category.DEVICES,
                    (lang != null ? lang.item(healer.id, "治疗机") : "治疗机"),
                    items.createItem(healer, lang, 1),
                    grid(
                            null, Material.ENDER_EYE, null,
                            null, Material.ENCHANTING_TABLE, null,
                            null, Material.LODESTONE, null
                    ),
                    "竖向摆放：末影之眼-附魔台-磁石"
            ));
        }

        if (pasture != null) {
            book.add(new RecipeDef(
                    "pasture_machine",
                    Category.DEVICES,
                    (lang != null ? lang.item(pasture.id, "精灵牧场") : "精灵牧场"),
                    items.createItem(pasture, lang, 1),
                    grid(
                            null, Material.HAY_BLOCK, null,
                            null, Material.OAK_FENCE, null,
                            null, Material.CHEST, null
                    ),
                    "竖向摆放：干草捆-橡木栅栏-箱子"
            ));
        }

        if (fossil != null) {
            book.add(new RecipeDef(
                    "fossil_machine",
                    Category.DEVICES,
                    (lang != null ? lang.item(fossil.id, "化石复活机") : "化石复活机"),
                    items.createItem(fossil, lang, 1),
                    grid(
                            null, Material.REDSTONE_BLOCK, null,
                            null, Material.BLAST_FURNACE, null,
                            null, Material.CHEST, null
                    ),
                    "竖向摆放：红石块-高炉-箱子"
            ));
        }

        if (analyzer != null) {
            book.add(new RecipeDef(
                    "fossil_analyzer",
                    Category.DEVICES,
                    (lang != null ? lang.item(analyzer.id, "化石解析仪") : "化石解析仪"),
                    items.createItem(analyzer, lang, 1),
                    grid(
                            null, Material.GLASS, null,
                            null, Material.WATER_BUCKET, null,
                            null, Material.CHEST, null
                    ),
                    "竖向摆放：玻璃-水桶-箱子"
            ));
        }

        if (trade != null) {
            book.add(new RecipeDef(
                    "trade_machine",
                    Category.DEVICES,
                    (lang != null ? lang.item(trade.id, "宝可梦交换机") : "宝可梦交换机"),
                    items.createItem(trade, lang, 1),
                    grid(
                            null, Material.GLASS, null,
                            null, Material.ENDER_PEARL, null,
                            null, Material.CHEST, null
                    ),
                    "竖向摆放：玻璃-末影珍珠-箱子"
            ));
        }

        if (clone != null) {
            book.add(new RecipeDef(
                    "clone_machine",
                    Category.DEVICES,
                    (lang != null ? lang.item(clone.id, "宝可梦克隆仪") : "宝可梦克隆仪"),
                    items.createItem(clone, lang, 1),
                    grid(
                            null, Material.GLASS, null,
                            null, Material.AMETHYST_SHARD, null,
                            null, Material.CHEST, null
                    ),
                    "竖向摆放：玻璃-紫水晶碎片-箱子"
            ));
        }
        addRodRecipe(book, items, lang, reg.get("poke_ball"), pokeRod, "poke_rod", "宝可梦钓竿", "无序合成：原版钓竿 + 精灵球");
        addRodRecipe(book, items, lang, reg.get("great_ball"), greatRod, "great_rod", "高级钓竿", "无序合成：原版钓竿 + 超级球");
        addRodRecipe(book, items, lang, reg.get("ultra_ball"), ultraRod, "ultra_rod", "超级钓竿", "无序合成：原版钓竿 + 高级球");
        addRodRecipe(book, items, lang, reg.get("master_ball"), masterRod, "master_rod", "大师钓竿", "无序合成：原版钓竿 + 大师球");
        addRodRecipe(book, items, lang, reg.get("love_ball"), loveRod, "love_rod", "爱心钓竿", "无序合成：原版钓竿 + 爱心球");

        // Devices
        ItemDef dex = reg.get("pokedex");
        if (dex != null) {
            book.add(new RecipeDef(
                    "poke_dex",
                    Category.DEVICES,
                    (lang != null ? lang.item(dex.id, "精灵图鉴") : "精灵图鉴"),
                    items.createItem(dex, lang, 1),
                    grid(
                            null, Material.COMPASS, null,
                            null, Material.REDSTONE, null,
                            null, Material.BOOK, null
                    ),
                    "竖向摆放：指南针-红石-书"
            ));
        }
        ItemDef phone = reg.get("poke_phone");
        if (phone != null && pc != null && healer != null && dex != null) {
            book.add(new RecipeDef(
                    "poke_phone",
                    Category.DEVICES,
                    (lang != null ? lang.item(phone.id, "精灵手机") : "精灵手机"),
                    items.createItem(phone, lang, 1),
                    grid(
                            null, items.createItem(dex, lang, 1), null,
                            items.createItem(pc, lang, 1), Material.REPEATER, items.createItem(healer, lang, 1),
                            null, null, null
                    ),
                    (lang != null ? lang.ui("gui.recipes.devices_hint", "上=图鉴 左=PC 右=治疗机 中=红石中继器") : "上=图鉴 左=PC 右=治疗机 中=红石中继器")
            ));
        }


        // Balls (loaded from bundled Cobblemon-style recipe jsons)
        loadBallRecipesFromResources(plugin, book, reg, items, lang);

        // Ensure the BALLS category shows all ball items (even if some have no recipe yet).
        for (ItemDef def : reg.all().values()) {
            if (def == null || def.type != ItemType.BALL) continue;
            if (book.byKey.containsKey(def.id)) continue; // already has a recipe
            String name = (lang != null ? lang.item(def.id, def.id) : def.id);
            book.add(new RecipeDef(
                    def.id,
                    Category.BALLS,
                    name,
                    items.createItem(def, lang, 1),
                    null,
                    (lang != null ? lang.ui("common.no_recipe", "暂无合成配方") : "暂无合成配方")
            ));
        }

        // Items gallery (no crafting grid required)
        List<ItemDef> sortedDefs = new ArrayList<>(reg.all().values());
        sortedDefs.sort((a, b) -> {
            if (a == null && b == null) return 0;
            if (a == null) return 1;
            if (b == null) return -1;
            int ta = typeOrder(a.type);
            int tb = typeOrder(b.type);
            if (ta != tb) return Integer.compare(ta, tb);
            return String.valueOf(a.id).compareToIgnoreCase(String.valueOf(b.id));
        });

        for (ItemDef def : sortedDefs) {
            if (def == null) continue;
            if (def.type == ItemType.BALL) continue;
            if (def.type == ItemType.BERRY) continue;
            if (def.type == ItemType.TM) continue;
            // Devices are shown under DEVICES with recipes; TMs and other items go to ITEMS.
            if (def.id == null) continue;
            if (book.byKey.containsKey(def.id) && book.byKey.get(def.id).category != Category.ITEMS) continue;

            String name = (lang != null ? lang.item(def.id, def.id) : def.id);
            String desc = null;
            if (lang != null) {
                desc = lang.itemDesc(def.id, null);
            }
            if (desc == null || desc.isBlank()) {
                desc = defaultItemDesc(def);
            }
            book.add(new RecipeDef(
                    def.id,
                    Category.ITEMS,
                    name,
                    items.createItem(def, lang, 1),
                    null,
                    desc
            ));
        }

        return book;
    }


    private static void loadBallRecipesFromResources(PokeDemoPlugin plugin, RecipeBook book,
                                                    ItemRegistry reg, ItemFactory items, LangManager lang) {
        if (plugin == null) return;
        // index file lists all json filenames under /cobblemon/recipe/
        List<String> files = new ArrayList<>();
        try (var in = RecipeBook.class.getResourceAsStream("/cobblemon/recipe/index.txt")) {
            if (in != null) {
                try (var br = new java.io.BufferedReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        line = line.trim();
                        if (line.isEmpty() || line.startsWith("#")) continue;
                        if (!line.endsWith(".json")) continue;
                        if (line.equalsIgnoreCase("master_ball.json")) continue; // disabled
                        files.add(line);
                    }
                }
            }
        } catch (Throwable ignored) {}

        // Fallback: if index missing, just return (BALLS will still show items with (lang != null ? lang.ui("common.no_recipe", "暂无合成配方") : "暂无合成配方"))
        if (files.isEmpty()) return;

        for (String fn : files) {
            try (var in = RecipeBook.class.getResourceAsStream("/cobblemon/recipe/" + fn)) {
                if (in == null) continue;
                var json = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                if (!json.has("type")) continue;

                String type = json.get("type").getAsString();
                if (!type.contains("crafting_shaped") && !type.contains("stonecutting")) continue;

                // Result id/count
                String rid = null;
                int rcount = 1;
                if (json.has("result")) {
                    var r = json.getAsJsonObject("result");
                    if (r.has("id")) rid = r.get("id").getAsString();
                    if (r.has("count")) rcount = r.get("count").getAsInt();
                    if (rid == null && r.has("item")) rid = r.get("item").getAsString(); // some packs use item
                }
                if (rid == null) continue;
                String ridPath = stripNs(rid);

                // Only collect balls here
                ItemDef outDef = reg.get(ridPath);
                if (outDef == null || outDef.type != ItemType.BALL) continue;

                String title = (lang != null ? lang.item(outDef.id, ridPath) : ridPath);
                ItemStack outStack = items.createItem(outDef, lang, Math.max(1, rcount));

                if (type.contains("stonecutting")) {
                    // Show as "切石机" recipe (we don't draw a 3x3 grid)
                    book.add(new RecipeDef(
                            ridPath,
                            Category.BALLS,
                            title,
                            outStack,
                            null,
                            "切石机合成（来自 Cobblemon 配方）"
                    ));
                    continue;
                }

                // Shaped recipe -> 3x3 grid
                String[] pattern = new String[0];
                if (json.has("pattern")) {
                    var arr = json.getAsJsonArray("pattern");
                    pattern = new String[arr.size()];
                    for (int i = 0; i < arr.size(); i++) pattern[i] = arr.get(i).getAsString();
                }
                if (pattern.length != 3) continue;

                var keyObj = json.getAsJsonObject("key");
                Map<Character, ItemStack> key = new HashMap<>();
                for (String k : keyObj.keySet()) {
                    if (k == null || k.length() != 1) continue;
                    char ch = k.charAt(0);
                    var ing = keyObj.getAsJsonObject(k);
                    ItemStack is = ingredientToItemStack(ing, reg, items, lang);
                    key.put(ch, is);
                }

                ItemStack[] g = new ItemStack[9];
                for (int row = 0; row < 3; row++) {
                    String line = pattern[row];
                    while (line.length() < 3) line += " ";
                    for (int col = 0; col < 3; col++) {
                        char ch = line.charAt(col);
                        ItemStack is = key.get(ch);
                        g[row * 3 + col] = is;
                    }
                }

                book.add(new RecipeDef(
                        ridPath,
                        Category.BALLS,
                        title,
                        outStack,
                        g,
                        "产出×" + rcount
                ));
            } catch (Throwable ignored) {
                // ignore broken recipe
            }
        }
    }

    private static ItemStack ingredientToItemStack(com.google.gson.JsonObject ing, ItemRegistry reg, ItemFactory items, LangManager lang) {
        if (ing == null) return null;
        // item
        if (ing.has("item")) {
            String id = ing.get("item").getAsString();
            String ns = nsOf(id);
            String path = stripNs(id);
            if ("minecraft".equals(ns)) {
                Material m = materialById(path);
                return (m != null ? new ItemStack(m) : null);
            }
            ItemDef def = reg.get(path);
            return (def != null ? items.createItem(def, lang, 1) : null);
        }
        // tag (only handle tier materials here)
        if (ing.has("tag")) {
            String tag = ing.get("tag").getAsString();
            String path = stripNs(tag);
            return switch (path) {
                case "tier_1_poke_ball_materials" -> new ItemStack(Material.COPPER_INGOT);
                case "tier_2_poke_ball_materials" -> new ItemStack(Material.IRON_INGOT);
                case "tier_3_poke_ball_materials" -> new ItemStack(Material.GOLD_INGOT);
                case "tier_4_poke_ball_materials" -> new ItemStack(Material.DIAMOND);
                default -> null;
            };
        }
        return null;
    }

    private static String nsOf(String id) {
        int i = id.indexOf(':');
        return i >= 0 ? id.substring(0, i) : "minecraft";
    }

    private static String stripNs(String id) {
        int i = id.indexOf(':');
        return i >= 0 ? id.substring(i + 1) : id;
    }

    private static Material materialById(String id) {
        if (id == null) return null;
        try {
            return Material.valueOf(id.toUpperCase(Locale.ROOT));
        } catch (Throwable ignored) {
            return null;
        }
    }


    private static void addRodRecipe(RecipeBook book, ItemFactory items, LangManager lang, ItemDef ball, ItemDef rod, String key, String fallbackName, String desc) {
        if (ball == null || rod == null) return;
        book.add(new RecipeDef(
                key,
                Category.DEVICES,
                (lang != null ? lang.item(rod.id, fallbackName) : fallbackName),
                items.createItem(rod, lang, 1),
                grid(
                        null, null, null,
                        null, Material.FISHING_ROD, items.createItem(ball, lang, 1),
                        null, null, null
                ),
                desc
        ));
    }

    private static ItemStack[] grid(Object... nine) {
        if (nine.length != 9) throw new IllegalArgumentException("grid needs 9");
        ItemStack[] g = new ItemStack[9];
        for (int i = 0; i < 9; i++) {
            Object o = nine[i];
            if (o == null) {
                g[i] = null;
            } else if (o instanceof Material m) {
                g[i] = new ItemStack(m);
            } else if (o instanceof ItemStack is) {
                g[i] = is.clone();
            } else {
                g[i] = null;
            }
        }
        return g;
    }

    private static int typeOrder(ItemType t) {
        if (t == null) return 999;
        return switch (t) {
            case MEDICINE -> 0;
            case STATUS_CURE -> 1;
            case REVIVE -> 2;
            case PP_RESTORE -> 3;
            case BATTLE -> 4;
            case VITAMIN -> 5;
            case HELD -> 6;
            case KEY -> 7;
            case MISC -> 8;
            case BERRY -> 90;
            case TM -> 91;
            case BALL -> 100;
        };
    }

    /**
     * Fallback description for the items gallery when lang does not provide item.<id>.desc.
     * Keep it short (one line).
     */
    private static String defaultItemDesc(ItemDef def) {
        if (def == null) return "道具";
        return switch (def.type) {
            case MEDICINE -> "回复体力";
            case STATUS_CURE -> "治疗异常状态";
            case REVIVE -> "复活倒下的精灵";
            case PP_RESTORE -> "回复技能PP";
            case BATTLE -> "战斗中使用的强化道具";
            case VITAMIN -> "提升努力值";
            case HELD -> "携带道具：战斗中自动生效";
            case BERRY -> "树果：战斗中可能自动触发";
            case TM -> "技能机：让精灵学习招式";
            case KEY -> "关键道具";
            case BALL -> "用于捕捉精灵";
            case MISC -> "道具";
        };
    }
}