
package win.pokedemo;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.nio.file.Files;
import java.nio.file.Path;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;


public class PokeDemoPlugin extends JavaPlugin implements TabExecutor {

    private void registerExactRodRecipe(String key, ItemStack result, ItemStack ballIngredient) {
        if (result == null || ballIngredient == null) return;
        ShapelessRecipe recipe = new ShapelessRecipe(new NamespacedKey(this, key), result);
        recipe.addIngredient(Material.FISHING_ROD);
        recipe.addIngredient(new org.bukkit.inventory.RecipeChoice.ExactChoice(ballIngredient));
        Bukkit.addRecipe(recipe);
    }



    /** Register simple shaped recipes for custom machine items (PC / Healer / Pasture). */
    private void registerMachineRecipes() {
        try {
            ItemDef pc = itemRegistry.get("pc_machine");
            ItemDef healer = itemRegistry.get("healer_machine");
            ItemDef pasture = itemRegistry.get("pasture_machine");
            ItemDef fossil = itemRegistry.get("fossil_machine");
            ItemDef analyzer = itemRegistry.get("fossil_analyzer");
            ItemDef trade = itemRegistry.get("trade_machine");
            ItemDef clone = itemRegistry.get("clone_machine");
            ItemDef pokeRod = itemRegistry.get("poke_rod");
            ItemDef greatRod = itemRegistry.get("great_rod");
            ItemDef ultraRod = itemRegistry.get("ultra_rod");
            ItemDef masterRod = itemRegistry.get("master_rod");
            ItemDef loveRod = itemRegistry.get("love_rod");
            ItemDef pokeBall = itemRegistry.get("poke_ball");
            ItemDef greatBall = itemRegistry.get("great_ball");
            ItemDef ultraBall = itemRegistry.get("ultra_ball");
            ItemDef masterBall = itemRegistry.get("master_ball");
            ItemDef loveBall = itemRegistry.get("love_ball");
            if (pc == null || healer == null || pasture == null || fossil == null || analyzer == null || trade == null || clone == null
                    || pokeRod == null || greatRod == null || ultraRod == null || masterRod == null || loveRod == null
                    || pokeBall == null || greatBall == null || ultraBall == null || masterBall == null || loveBall == null) {
                getLogger().warning("[PokeDemo] Machine item defs missing; recipes not registered.");
                return;
            }

            // Healer recipe (vertical 1x3 in center): Ender Eye, Enchanting Table, Lodestone
            ItemStack healerOut = items.createItem(healer, lang, 1);
            ShapedRecipe healerRecipe = new ShapedRecipe(new NamespacedKey(this, "healer_machine"), healerOut);
            healerRecipe.shape(" E ", " A ", " L ");
            healerRecipe.setIngredient('E', Material.ENDER_EYE);
            healerRecipe.setIngredient('A', Material.ENCHANTING_TABLE);
            healerRecipe.setIngredient('L', Material.LODESTONE);
            Bukkit.addRecipe(healerRecipe);

            // PC recipe (vertical 1x3 in center): Gold Ingot, Iron Door, Chest
            ItemStack pcOut = items.createItem(pc, lang, 1);
            ShapedRecipe pcRecipe = new ShapedRecipe(new NamespacedKey(this, "pc_machine"), pcOut);
            pcRecipe.shape(" G ", " D ", " C ");
            pcRecipe.setIngredient('G', Material.GOLD_INGOT);
            pcRecipe.setIngredient('D', Material.IRON_DOOR);
            pcRecipe.setIngredient('C', Material.CHEST);
            Bukkit.addRecipe(pcRecipe);

            // Pasture recipe (vertical 1x3 in center): Hay Bale, Oak Fence, Chest
            ItemStack pastureOut = items.createItem(pasture, lang, 1);
            ShapedRecipe pastureRecipe = new ShapedRecipe(new NamespacedKey(this, "pasture_machine"), pastureOut);
            pastureRecipe.shape(" H ", " F ", " C ");
            pastureRecipe.setIngredient('H', Material.HAY_BLOCK);
            pastureRecipe.setIngredient('F', Material.OAK_FENCE);
            pastureRecipe.setIngredient('C', Material.CHEST);
            Bukkit.addRecipe(pastureRecipe);

            // Fossil machine recipe (vertical 1x3): Redstone Block, Blast Furnace, Chest
            ItemStack fossilOut = items.createItem(fossil, lang, 1);
            ShapedRecipe fossilRecipe = new ShapedRecipe(new NamespacedKey(this, "fossil_machine"), fossilOut);
            fossilRecipe.shape(" R ", " B ", " C ");
            fossilRecipe.setIngredient('R', Material.REDSTONE_BLOCK);
            fossilRecipe.setIngredient('B', Material.BLAST_FURNACE);
            fossilRecipe.setIngredient('C', Material.CHEST);
            Bukkit.addRecipe(fossilRecipe);

            // Fossil analyzer recipe (vertical 1x3): Glass, Water Bucket, Chest
            ItemStack analyzerOut = items.createItem(analyzer, lang, 1);
            ShapedRecipe analyzerRecipe = new ShapedRecipe(new NamespacedKey(this, "fossil_analyzer"), analyzerOut);
            analyzerRecipe.shape(" G ", " W ", " C ");
            analyzerRecipe.setIngredient('G', Material.GLASS);
            analyzerRecipe.setIngredient('W', Material.WATER_BUCKET);
            analyzerRecipe.setIngredient('C', Material.CHEST);
            Bukkit.addRecipe(analyzerRecipe);

            // Trade machine recipe (vertical 1x3): Glass, Ender Pearl, Chest
            ItemStack tradeOut = items.createItem(trade, lang, 1);
            ShapedRecipe tradeRecipe = new ShapedRecipe(new NamespacedKey(this, "trade_machine"), tradeOut);
            tradeRecipe.shape(" G ", " P ", " C ");
            tradeRecipe.setIngredient('G', Material.GLASS);
            tradeRecipe.setIngredient('P', Material.ENDER_PEARL);
            tradeRecipe.setIngredient('C', Material.CHEST);
            Bukkit.addRecipe(tradeRecipe);

            // Clone machine recipe (vertical 1x3): Glass, Amethyst Shard, Chest
            ItemStack cloneOut = items.createItem(clone, lang, 1);
            ShapedRecipe cloneRecipe = new ShapedRecipe(new NamespacedKey(this, "clone_machine"), cloneOut);
            cloneRecipe.shape(" G ", " A ", " C ");
            cloneRecipe.setIngredient('G', Material.GLASS);
            cloneRecipe.setIngredient('A', Material.AMETHYST_SHARD);
            cloneRecipe.setIngredient('C', Material.CHEST);
            Bukkit.addRecipe(cloneRecipe);

            // Poké Rod recipes (shapeless): vanilla fishing rod + corresponding Poké Ball
            registerExactRodRecipe("poke_rod", items.createItem(pokeRod, lang, 1), items.createItem(pokeBall, lang, 1));
            registerExactRodRecipe("great_rod", items.createItem(greatRod, lang, 1), items.createItem(greatBall, lang, 1));
            registerExactRodRecipe("ultra_rod", items.createItem(ultraRod, lang, 1), items.createItem(ultraBall, lang, 1));
            registerExactRodRecipe("master_rod", items.createItem(masterRod, lang, 1), items.createItem(masterBall, lang, 1));
            registerExactRodRecipe("love_rod", items.createItem(loveRod, lang, 1), items.createItem(loveBall, lang, 1));

            getLogger().info("[PokeDemo] Registered machine recipes: pc_machine, healer_machine, pasture_machine, fossil_machine, fossil_analyzer, trade_machine, clone_machine, poke_rod, great_rod, ultra_rod, master_rod, love_rod");
        } catch (Exception e) {
            getLogger().warning("[PokeDemo] Failed to register machine recipes: " + e.getMessage());
        }
    }

    private void safeRegisterCommand(String name, org.bukkit.command.CommandExecutor executor) {
        if (getCommand(name) == null) {
            getLogger().severe("[PokeDemo] Command '" + name + "' not found in plugin.yml; plugin features may be unavailable.");
            return;
        }
        getCommand(name).setExecutor(executor);
    }

    public static PokeDemoPlugin INSTANCE;
    public NamespacedKey KEY_SPECIES;
    public NamespacedKey KEY_LEVEL;
    public NamespacedKey KEY_OWNER;
    public NamespacedKey KEY_WILD;
    public NamespacedKey KEY_BUCKET;
    public NamespacedKey KEY_PUUID;
    public NamespacedKey KEY_CARRIER;
    public NamespacedKey KEY_CARRIER_TEXT;
    public NamespacedKey KEY_CARRIER_IDTEXT;
    public NamespacedKey KEY_CARRIER_INTERACT;
    /** PDC key stored on Display entities to point back to their logical Wolf carrier UUID. */
    public NamespacedKey KEY_CARRIER_OWNER;
    public NamespacedKey KEY_PARTY_SLOT;

    // Legendary (altar summoned / special encounters)
    public NamespacedKey KEY_LEGENDARY;
    public NamespacedKey KEY_LEGENDARY_GROUP;
    public NamespacedKey KEY_MIN_PERFECT_IVS;

    private Storage storage;
    private Dex dex;
    private SpawnManager spawns;
    private LegendaryRandomSpawnService legendaryRandom;
    private BattleManager battles;
    private ItemFactory items;
    private ItemRegistry itemRegistry;
    private BerryBushManager berryBushManager;

    // Overworld machines (PC/Healer) based on custom Note Blocks.
    private MachineRegistry machineRegistry;
    private PastureManager pastureManager;
    private CloneManager cloneManager;
    private TradeManager tradeManager;
    private FossilMachineManager fossilMachineManager;
    private FossilAnalyzerManager fossilAnalyzerManager;
    private PastureBreedingService pastureBreedingService;
    private PlantManager plantManager;

    private LangManager lang;
    private GuideBookManager guide;

    // ---------------- Public helpers (used by commands/listeners) ----------------
    public ItemFactory getItemFactory() { return items; }
    public ItemRegistry getItemRegistry() { return itemRegistry; }
    public LangManager getLang() { return lang; }
    public GuideBookManager getGuide() { return guide; }

    // In-game recipe browser
    private RecipeBook recipeBook;

    // Pokédex dataset for GUI
    private PokedexData pokedexData;
    private PokedexSpawnIndex pokedexSpawnIndex;

    private SummonManager summonManager;
    private EvolutionManager evolutions;
    private LearnsetManager learnsetManager;
    private TmManager tmManager;
    private PartySidebarManager partySidebarManager;
    private BridgeSyncManager bridgeSyncManager;
    private TutorNpcManager tutorNpcManager;
    private SpeciesMovementRegistry speciesMovementRegistry;
    private CarrierMotionController carrierMotionController;

    // Wild drops (from Cobblemon species_raw)
    private DropTableManager dropTables;

    /** Tracks deferred consumption for items like TMs (consume only after the move is actually learned). */
    private PendingItemConsumeManager pendingItemConsumeManager;


    private final Map<String, Integer> modelCmdMap = new HashMap<>();

    /** Whether legacy server-side ItemDisplay/TextDisplay carrier visuals should be attached for pokemon wolves. */
    private volatile boolean carrierDisplaysEnabled = true;

    public boolean isCarrierDisplaysEnabled() { return carrierDisplaysEnabled; }

    public void setCarrierDisplaysEnabled(boolean enabled) {
        this.carrierDisplaysEnabled = enabled;
        try {
            getConfig().set("visuals.display-layers-enabled", enabled);
            saveConfig();
        } catch (Throwable ignored) {}
    }

    /**
     * "Armed" timestamp for the "Shift + SwapHand(F) + Hotbar change" summon combo.
     * We store it in the plugin so multiple listeners can share the state safely.
     */
    private final Map<UUID, Long> summonComboArmedAtMs = new ConcurrentHashMap<>();

    public void armSummonCombo(UUID uuid) {
        if (uuid == null) return;
        summonComboArmedAtMs.put(uuid, System.currentTimeMillis());
    }

    /** Consume armed state if still valid within windowMs. */
    public boolean consumeSummonComboIfArmed(UUID uuid, long windowMs) {
        if (uuid == null) return false;
        Long at = summonComboArmedAtMs.get(uuid);
        if (at == null) return false;
        long now = System.currentTimeMillis();
        if (now - at > windowMs) {
            summonComboArmedAtMs.remove(uuid);
            return false;
        }
        summonComboArmedAtMs.remove(uuid);
        return true;
    }

    public Dex getDex() {
        return dex;
    }

    public Storage getStorage() {
        return storage;
    }

    public BattleManager battles() {
        return battles;
    }

    // NOTE: getLang() / getItemRegistry() already exist earlier in the file.
    public RecipeBook getRecipeBook() { return recipeBook; }
    public PokedexData getPokedexData() { return pokedexData; }
    public PokedexSpawnIndex getPokedexSpawnIndex() { return pokedexSpawnIndex; }
    public ItemFactory getItems() { return items; }
    public MachineRegistry getMachineRegistry() { return machineRegistry; }
    public PastureManager getPastureManager() { return pastureManager; }
    public CloneManager getCloneManager() { return cloneManager; }
    public TradeManager getTradeManager() { return tradeManager; }
    public PastureBreedingService getPastureBreedingService() { return pastureBreedingService; }
    public DropTableManager getDropTables() { return dropTables; }
    public SpawnManager getSpawnManager() { return spawns; }

    /**
     * Heal the player's party (HP + status + PP).
     *
     * @param bypassPermission if true, does not require pokedemo.admin.heal (used by healer machine).
     */
    public int healParty(org.bukkit.entity.Player player, boolean bypassPermission) {
        if (player == null) return 0;
        if (!bypassPermission && !player.hasPermission("pokedemo.admin.heal")) {
            player.sendMessage("§c你没有权限使用治疗功能。");
            return 0;
        }
        if (battles != null && battles.isInBattle(player.getUniqueId())) {
            player.sendMessage("§c战斗中不能使用该功能。");
            return 0;
        }
        if (storage == null || dex == null) return 0;
        PlayerProfile prof = storage.getProfile(player.getUniqueId());
        int healed = 0;
        for (PokemonInstance p : prof.party) {
            if (p == null) continue;
            Species s = dex.getSpecies(p.speciesId);
            if (s != null) {
                int maxHp = Math.max(1, p.maxHp(s));
                p.currentHp = maxHp;
            }
            p.status = "none";
            p.sleepTurns = 0;
            p.toxicCounter = 0;
            p.resetBattleVolatiles();

            // Restore PP
            if (p.moves != null) {
                for (MoveSlot ms : p.moves) {
                    if (ms == null) continue;
                    ms.recalcMaxPp();
                    ms.pp = ms.maxPp;
                }
            }
            healed++;
        }
        storage.saveProfile(player.getUniqueId());
        return healed;
    }

    /** Spawn a normal wild Pokémon at location using the SpawnManager's manual method. */
    public java.util.UUID spawnWildAt(org.bukkit.Location loc, String speciesId, int level) {
        if (spawns == null || loc == null || speciesId == null) return null;
        int lvl = Util.clamp(level, 1, 100);
        return spawns.spawnWildManual(loc, speciesId.toLowerCase(java.util.Locale.ROOT), lvl);
    }

    /** Spawn a legendary at location (mark PDC + attach gold label + auto-despawn). */
    public java.util.UUID spawnLegendaryAt(org.bukkit.Location loc, String speciesId, int level, int minPerfectIvs, String group) {
        if (spawns == null || loc == null || speciesId == null) return null;
        Species s = dex == null ? null : dex.getSpecies(speciesId.toLowerCase(java.util.Locale.ROOT));
        if (s == null) return null;

        int lvl = Util.clamp(level, 1, 100);
        int minPerf = Math.max(0, minPerfectIvs);

        java.util.UUID wolfId = spawns.spawnWildManual(loc, s.id(), lvl);
        if (wolfId == null) return null;
        org.bukkit.entity.Entity ent = Bukkit.getEntity(wolfId);
        if (!(ent instanceof org.bukkit.entity.Wolf wolf)) return wolfId;

        try {
            wolf.getPersistentDataContainer().set(KEY_BUCKET, org.bukkit.persistence.PersistentDataType.STRING, "legendary");
            wolf.getPersistentDataContainer().set(KEY_LEGENDARY, org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
            wolf.getPersistentDataContainer().set(KEY_LEGENDARY_GROUP, org.bukkit.persistence.PersistentDataType.STRING, (group == null || group.isBlank()) ? "legendary" : group);
            wolf.getPersistentDataContainer().set(KEY_MIN_PERFECT_IVS, org.bukkit.persistence.PersistentDataType.INTEGER, minPerf);

            LangManager l = getLang();
            String display = (l != null) ? l.species(s.id(), s.name()) : s.name();
            String label = "§6★ §6" + display + " §7Lv." + lvl;
            new VisualCarrierManager(this).attach(wolf, s.id(), label);

            int despawnMinutes = getConfig().getInt("legendary.despawn-minutes", 10);
            if (despawnMinutes > 0) {
                long delayTicks = Math.max(1L, despawnMinutes * 60L * 20L);
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    try {
                        if (!wolf.isValid() || wolf.isDead()) return;
                        Byte isLeg = wolf.getPersistentDataContainer().get(KEY_LEGENDARY, org.bukkit.persistence.PersistentDataType.BYTE);
                        if (isLeg == null || isLeg != (byte) 1) return;
                        new VisualCarrierManager(this).detach(wolf);
                        wolf.remove();
                    } catch (Throwable ignored) {}
                }, delayTicks);
            }
        } catch (Throwable ignored) {}

        return wolfId;
    }


    @Override
    public void onEnable() {
        // IMPORTANT: Extract the fully-bundled data folder BEFORE Bukkit creates default config/lang files.
        // We overwrite on first extract (marker missing) to ensure a real "first-run" gets the full folder.
        BundledDataExtractor.ensureExtracted(this, true);

        safeLoadConfig();
        INSTANCE = this;
        KEY_SPECIES = new NamespacedKey(this, "species");
        KEY_LEVEL = new NamespacedKey(this, "level");
        KEY_OWNER = new NamespacedKey(this, "owner");
        KEY_WILD = new NamespacedKey(this, "wild");
        KEY_BUCKET = new NamespacedKey(this, "bucket");
        KEY_PUUID = new NamespacedKey(this, "puuid");
        KEY_CARRIER = new NamespacedKey(this, "carrier");
        KEY_CARRIER_TEXT = new NamespacedKey(this, "carrier_text");
        KEY_CARRIER_IDTEXT = new NamespacedKey(this, "carrier_idtext");
        KEY_CARRIER_INTERACT = new NamespacedKey(this, "carrier_interact");
        KEY_CARRIER_OWNER = new NamespacedKey(this, "carrier_owner");
        KEY_PARTY_SLOT = new NamespacedKey(this, "party_slot");

        KEY_LEGENDARY = new NamespacedKey(this, "legendary");
        KEY_LEGENDARY_GROUP = new NamespacedKey(this, "legendary_group");
        KEY_MIN_PERFECT_IVS = new NamespacedKey(this, "min_perfect_ivs");

        saveDefaultConfig();
        migrateConfigIfNeeded();
        this.carrierDisplaysEnabled = getConfig().getBoolean("visuals.display-layers-enabled", true);

        // (moved to the top of onEnable)

        // Write bundled offline TM compatibility table if missing (for open-source / plug-and-play).
        ensureBundledDefaultData();

        // Optional: auto-import Pokemon Showdown data into moves_raw before Dex loads.
        // - showdown.auto-download-on-start: always try to ensure moves.json exists
        // - showdown.auto-import-on-start: run-once style, with a state file; re-runs if files missing
        ShowdownAutoImport.importIfNeeded(this);

        // Language (Chinese/English display names)
        this.lang = new LangManager(this);
        this.lang.ensureDefaultFiles();
        this.lang.load();

        // Builtin Pokédex dataset (Gen1 national dex numbers + description keys)
        this.pokedexData = PokedexData.loadBuiltin(this);

        loadModelCmdMap();

        this.dex = new Dex(this);
        this.dex.loadAll();

        // Load species drop tables from Cobblemon raw dataset (optional).
        this.dropTables = new DropTableManager(this);
        this.dropTables.loadAll();

        // Optional: auto-run admin import commands once on server start (works without console).
        // This will download moves/pokedex/learnsets (if missing) and then reload Dex + apply learnsets.
        if (getConfig().getBoolean("showdown.auto-run-import-commands-on-start", false)) {
            getLogger().info("[PokeDemo] Auto-run import commands on start: importshowdown(moves+pokedex) + importlearnsets");
            Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                boolean okMoves = ShowdownDownloader.ensureMovesJson(this);
                boolean okDex = ShowdownDownloader.ensurePokedexJson(this);
                boolean okLearn = ShowdownDownloader.ensureLearnsetsJson(this);
                Bukkit.getScheduler().runTask(this, () -> {
                    if (okMoves || okDex) {
                        try { dex.loadAll(); } catch (Exception e) { getLogger().warning("Dex reload failed: " + e.getMessage()); }
                    }
                    try { if (dropTables != null) dropTables.loadAll(); } catch (Exception e) { getLogger().warning("Drops reload failed: " + e.getMessage()); }
                    if (okLearn) {
                        try { dex.applyShowdownLearnsetsNow(); } catch (Exception e) { getLogger().warning("Apply learnsets failed: " + e.getMessage()); }
                    }
                    if (getConfig().getBoolean("showdown.auto-run-gen1report-on-start", false)) {
                        try {
                            var p = dex.writeGen1CoverageReport();
                            if (p != null) getLogger().info("[PokeDemo] Gen1 coverage report written: " + p);
                        } catch (Exception e) {
                            getLogger().warning("Gen1 report failed: " + e.getMessage());
                        }
                    }
                });
            });
        }

        getLogger().info("[PokeDemo] Enabled. species=" + dex.getSpeciesCount() + ", langPrimary=" + lang.getPrimaryLocale() + ", langFallback=" + lang.getFallbackLocale());

        this.storage = new Storage(this);
        this.storage.loadAll();

        this.items = new ItemFactory(this);
        this.itemRegistry = new ItemRegistry();

        // Guide book (tutorial) system
        this.guide = new GuideBookManager(this);
        this.guide.ensureDefaultFiles();
        this.guide.reloadFromConfig();

        // Registry for placed overworld machines.
        this.machineRegistry = new MachineRegistry(this);
        this.pastureManager = new PastureManager(this, storage, dex);
        this.cloneManager = new CloneManager(this, storage, dex);
        this.fossilMachineManager = new FossilMachineManager(this, storage, dex, machineRegistry);
        this.fossilAnalyzerManager = new FossilAnalyzerManager(this, machineRegistry);
        this.plantManager = new PlantManager(this, itemRegistry, items, lang);

        this.battles = new BattleManager(this, dex, storage, items);
        this.spawns = new SpawnManager(this, dex);

        Bukkit.getPluginManager().registerEvents(new PlayerJoinQuitListener(this, storage), this);
        // Join hint for guide book (optional, can be once-per-player)
        Bukkit.getPluginManager().registerEvents(new GuideJoinHintListener(this), this);
        // Starter selection is now triggered via Shift+SwapHand (see SummonHotkeyListener)
        // to avoid relying on the "first time opening inventory" heuristic.
        Bukkit.getPluginManager().registerEvents(new BattleCleanupListener(this, battles), this);
        Bukkit.getPluginManager().registerEvents(new CaptureListener(this, dex, storage, items, battles), this);
        Bukkit.getPluginManager().registerEvents(new PvpChallengeListener(this, items, battles), this);
        Bukkit.getPluginManager().registerEvents(new BattleSpectateListener(this, items, battles), this);
        Bukkit.getPluginManager().registerEvents(spawns, this);
        Bukkit.getPluginManager().registerEvents(new LegendaryAltarListener(this, dex, spawns, items), this);
        Bukkit.getPluginManager().registerEvents(new WildWolfProtectionListener(this), this);
        Bukkit.getPluginManager().registerEvents(new WildPokemonPassiveListener(this), this);
        Bukkit.getPluginManager().registerEvents(new PokemonCarrierSoundListener(this), this);
        Bukkit.getPluginManager().registerEvents(new BattleCarrierFreezeListener(this), this);
        
        this.summonManager = new SummonManager(this, dex, storage);
        // Remove any orphaned visual carrier displays left from previous crash/restart.
        this.summonManager.cleanupOrphansOnEnable();
        this.evolutions = new EvolutionManager(this, dex, storage, this.summonManager);
        this.tradeManager = new TradeManager(this, storage, this.evolutions);
        this.learnsetManager = new LearnsetManager(this, dex, storage);
        this.tmManager = new TmManager(this, dex);

        this.pendingItemConsumeManager = new PendingItemConsumeManager(this, items);
        this.tutorNpcManager = new TutorNpcManager(this, storage, dex, this.learnsetManager, this.tmManager);
        try {
            this.tmManager.ensureLoaded();
        } catch (Exception e) {
            getLogger().warning("[PokeDemo] TM compatibility load failed: " + e.getMessage());
        }
        Bukkit.getPluginManager().registerEvents(new GuiListener(storage, battles, this.evolutions, this.learnsetManager), this);
        // 纯插件无法直接监听客户端任意按键（例如单独的R）。
        // 这里使用“交换副手物品(默认F)”作为热键载体：仅在潜行(Shift)时触发，并取消交换。
        // 玩家可在客户端把“交换副手物品”改键为 R，即可获得“Shift+R 召唤/回收”的体验。
        Bukkit.getPluginManager().registerEvents(new SummonHotkeyListener(this, this.summonManager), this);
        Bukkit.getPluginManager().registerEvents(new SummonedPokemonListener(this, dex, storage, this.summonManager), this);
        Bukkit.getPluginManager().registerEvents(new ExperienceManager(this, dex, storage, this.summonManager, this.evolutions, this.learnsetManager), this);
        // NOTE: 不再使用“潜行 + 切换快捷栏”来触发召唤，否则会出现玩家只是切格子就丢精灵的误触。
        // 现在只保留 Shift+交换副手键(默认F，可改键R) 的热键逻辑。
        Bukkit.getPluginManager().registerEvents(new SummonHotbarKeyListener(this, this.summonManager), this);
        Bukkit.getPluginManager().registerEvents(new ControlWandListener(this, items), this);
        Bukkit.getPluginManager().registerEvents(new GuideBookListener(this), this);
        // Item system (Gen1 medicines/status/revive first)
        Bukkit.getPluginManager().registerEvents(new ItemUseListener(this, storage, dex, battles, itemRegistry, items), this);
        Bukkit.getPluginManager().registerEvents(new FishingLootListener(this, itemRegistry, items), this);
        Bukkit.getPluginManager().registerEvents(new FishingEncounterListener(this, spawns, itemRegistry, items), this);

        // Overworld machines: pc/healer as custom Note Blocks.
        Bukkit.getPluginManager().registerEvents(new MachineListener(this, items, itemRegistry, lang, machineRegistry), this);
        Bukkit.getPluginManager().registerEvents(new PlantListener(plantManager, itemRegistry, items), this);
        Bukkit.getPluginManager().registerEvents(new ApricornLeafDropListener(this, items, itemRegistry), this);
        Bukkit.getPluginManager().registerEvents(new TumblestoneDropListener(this, items, itemRegistry), this);
        Bukkit.getPluginManager().registerEvents(new PlantWorldGenListener(this, plantManager), this);

        // Berry bushes (sweet berry bush pick sound + custom berry harvesting)
        Bukkit.getPluginManager().registerEvents(new BerryBushListener(this), this);

        // Egg hatching: eggs only hatch while in party.
        Bukkit.getPluginManager().registerEvents(new EggHatchListener(this, storage, dex), this);
        // Stage 6: track Let's Go-like evolution steps for Pawmo / Bramblin / Rellor while actively summoned.
        Bukkit.getPluginManager().registerEvents(new EvolutionProgressListener(this, storage, this.summonManager, this.evolutions), this);

        // Pasture breeding core (legal check + day/night timer + environment acceleration).
        this.pastureBreedingService = new PastureBreedingService(this, storage, dex, machineRegistry, pastureManager);
        Bukkit.getScheduler().runTaskTimer(this, this.pastureBreedingService, 40L, 20L);

        // Fossil machine core (energy + minute checks)
        Bukkit.getScheduler().runTaskTimer(this, this.fossilMachineManager, 40L, 20L);

        // Fossil analyzer core (60s countdown)
        Bukkit.getScheduler().runTaskTimer(this, this.fossilAnalyzerManager, 40L, 20L);

        // Overworld Poké Ball loot chests + evolution stone ores (simple worldgen on new chunks)
        Bukkit.getPluginManager().registerEvents(new PokeBallChestWorldGenListener(this, items, itemRegistry), this);
        Bukkit.getPluginManager().registerEvents(new PokeBallChestListener(this), this);
        Bukkit.getPluginManager().registerEvents(new EvoStoneOreWorldGenListener(this), this);
        Bukkit.getPluginManager().registerEvents(new EvoStoneOreBreakListener(this, items, itemRegistry), this);

        // Archaeology fossils: add fossils into vanilla suspicious sand/gravel loot tables.
        Bukkit.getPluginManager().registerEvents(new FossilArchaeologyListener(this, items, itemRegistry), this);
        Bukkit.getPluginManager().registerEvents(new SuspiciousStoneDropListener(this), this);

        // Crafting recipes for machines (PC / Healer).
        registerMachineRecipes();

        // Crafting recipe for Guide Book (3 sticks shapeless)
        if (guide != null) {
            guide.registerGuideRecipe();
        }

        // Register Cobblemon ball recipes into Bukkit (so crafting table works).
        int ballRecipes = CobblemonBallRecipeRegistrar.registerAll(this);
        getLogger().info("Registered " + ballRecipes + " ball crafting recipes.");

        // Also enable a reliable crafting-table output handler for those recipes.
        // (Bukkit ExactChoice can be overly strict across versions / meta changes.)
        Bukkit.getPluginManager().registerEvents(
                new BallCraftingListener(this, CobblemonBallRecipeRegistrar.loadAll(this)),
                this
        );

        // Build the in-game recipe browser (independent from Bukkit recipe registry).
        // If this is not initialized, the recipe GUI category buttons will appear but do nothing.
        this.recipeBook = RecipeBook.build(this);

        // Cobblemon-like left sidebar party list (scoreboard based).
        this.partySidebarManager = new PartySidebarManager(this);
        this.partySidebarManager.start();

        this.speciesMovementRegistry = new SpeciesMovementRegistry(this);
        this.speciesMovementRegistry.reload();
        this.carrierMotionController = new CarrierMotionController(this, this.speciesMovementRegistry);
        this.carrierMotionController.start();

        // Custom bridge sync for the client HUD mod (real party -> left HUD).
        this.bridgeSyncManager = new BridgeSyncManager(this);
        Bukkit.getPluginManager().registerEvents(this.bridgeSyncManager, this);
        this.bridgeSyncManager.start();

        safeRegisterCommand("pokedemo", this);
        safeRegisterCommand("party", new PartyCommand(storage));
        safeRegisterCommand("pc", new PcCommand(storage));
        safeRegisterCommand("battle", new BattleCommand(this, battles));
        safeRegisterCommand("poke", new PokeCommand(this, items));
        safeRegisterCommand("heal", new HealCommand(this, dex, storage, battles));
        safeRegisterCommand("health", new HealCommand(this, dex, storage, battles));
        safeRegisterCommand("recipes", new RecipesCommand());        safeRegisterCommand("nurse", new NurseCommand(this));

        // Nurse NPC click-to-heal
        Bukkit.getPluginManager().registerEvents(new NurseListener(this), this);
        if (tutorNpcManager != null) { Bukkit.getPluginManager().registerEvents(tutorNpcManager, this); tutorNpcManager.start(); }

        int autosaveSec = getConfig().getInt("storage.autosave-seconds", 30);
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            try {
                storage.saveAll();
            } catch (Exception e) {
                getLogger().warning("Autosave failed: " + e.getMessage());
            }
        }, autosaveSec * 20L, autosaveSec * 20L);

        if (getConfig().getBoolean("spawns.enabled", true)) {
            spawns.start();
        }
        try {
            this.pokedexSpawnIndex = PokedexSpawnIndex.build(spawns != null ? spawns.getSpawnTable() : null);
        } catch (Throwable t) {
            getLogger().warning("[PokeDemo] Failed to build Pokédex spawn summaries: " + t.getMessage());
            this.pokedexSpawnIndex = new PokedexSpawnIndex();
        }

        // Random legendary spawns (e.g., Mew in jungles)
        legendaryRandom = new LegendaryRandomSpawnService(this, dex, spawns);
        legendaryRandom.start();

        // Cleanup any ghost pokemon wolves left by an unclean previous shutdown.
        Bukkit.getScheduler().runTaskLater(this, this::cleanupPokemonWolves, 1L);

        getLogger().info("PokeDemo enabled. Species loaded: " + dex.getSpeciesCount() + ", moves loaded: " + dex.getMoveCount());
    
        if (getCommand("pokedebugblock") != null) getCommand("pokedebugblock").setExecutor(new DebugCommand(plantManager));
    }

    
    public SummonManager getSummonManager() { return summonManager; }
    public SpeciesMovementRegistry getSpeciesMovementRegistry() { return speciesMovementRegistry; }
    public CarrierMotionController getCarrierMotionController() { return carrierMotionController; }
    public EvolutionManager getEvolutionManager() { return evolutions; }

    public FossilMachineManager getFossilMachineManager() { return fossilMachineManager; }
    public FossilAnalyzerManager getFossilAnalyzerManager() { return fossilAnalyzerManager; }
    public LearnsetManager getLearnsetManager() { return learnsetManager; }
    public TmManager getTmManager() { return tmManager; }
    public PendingItemConsumeManager getPendingItemConsumeManager() { return pendingItemConsumeManager; }
    public PartySidebarManager getPartySidebarManager() { return partySidebarManager; }
    public BridgeSyncManager getBridgeSyncManager() { return bridgeSyncManager; }
    public TutorNpcManager getTutorNpcManager() { return tutorNpcManager; }

    public Integer getModelCmd(String speciesIdLower) {
        if (speciesIdLower == null) return null;
        String key = speciesIdLower.toLowerCase();
        return modelCmdMap.get(key);
    }

    private void loadModelCmdMap() {
        modelCmdMap.clear();
        // Defaults (must match the client resource pack custom_model_data mapping)
        modelCmdMap.put("bulbasaur", 100001);
        modelCmdMap.put("charizard", 100002);
        modelCmdMap.put("pikachu", 100003);
        modelCmdMap.put("blastoise", 100004);
        modelCmdMap.put("butterfree", 100005);
        modelCmdMap.put("caterpie", 100006);
        modelCmdMap.put("charmander", 100007);
        modelCmdMap.put("charmeleon", 100008);
        modelCmdMap.put("ivysaur", 100009);
        modelCmdMap.put("kakuna", 100010);
        modelCmdMap.put("metapod", 100011);
        modelCmdMap.put("squirtle", 100012);
        modelCmdMap.put("venusaur", 100013);
        modelCmdMap.put("wartortle", 100014);
        modelCmdMap.put("weedle", 100015);
        modelCmdMap.put("arbok", 100016);
        modelCmdMap.put("beedrill", 100017);
        modelCmdMap.put("clefable", 100018);
        modelCmdMap.put("clefairy", 100019);
        modelCmdMap.put("ekans", 100020);
        modelCmdMap.put("fearow", 100021);
        modelCmdMap.put("gloom", 100022);
        modelCmdMap.put("golbat", 100023);
        modelCmdMap.put("jigglypuff", 100024);
        modelCmdMap.put("nidoking", 100025);
        modelCmdMap.put("nidoqueen", 100026);
        modelCmdMap.put("nidoranf", 100027);
        modelCmdMap.put("nidoranm", 100028);
        modelCmdMap.put("nidorina", 100029);
        modelCmdMap.put("nidorino", 100030);
        modelCmdMap.put("ninetales", 100031);
        modelCmdMap.put("oddish", 100032);
        modelCmdMap.put("pidgeot", 100033);
        modelCmdMap.put("pidgeotto", 100034);
        modelCmdMap.put("pidgey", 100035);
        modelCmdMap.put("raichu", 100036);
        modelCmdMap.put("raticate", 100037);
        modelCmdMap.put("rattata", 100038);
        modelCmdMap.put("sandshrew", 100039);
        modelCmdMap.put("sandslash", 100040);
        modelCmdMap.put("spearow", 100041);
        modelCmdMap.put("vulpix", 100042);
        modelCmdMap.put("wigglytuff", 100043);
        modelCmdMap.put("zubat", 100044);

        // Ensure a few commonly-missing Gen1 entries exist even if the external json is stale.
        // (Client resource pack uses these CMD values; the model file name can differ, e.g. ponyta_full.)
        modelCmdMap.put("farfetchd", 100069);
        // Mr. Mime has multiple id conventions across sources.
        modelCmdMap.put("mr_mime", 100110);
        modelCmdMap.put("mrmime", 100110);
        modelCmdMap.put("mr.mime", 100110);
        modelCmdMap.put("mr-mime", 100110);
        modelCmdMap.put("ponyta", 100122);
        // Vileplume (霸王花) in fix36 resource pack mapping
        modelCmdMap.put("vileplume", 100147);

        // Extra Gen1 entries reported missing by players
        // kabutops is present in the fix36 resource pack as CMD 100089
        modelCmdMap.put("kabutops", 100089);
        // diglett model in fix36 is currently provided as diglett_alolan (CMD 100055);
        // map diglett to it as a fallback so it doesn't become Bulbasaur.
        modelCmdMap.put("diglett", 100055);
        modelCmdMap.put("diglett_alolan", 100055);

        // Optional external override: plugins/PokeDemo/model_cmd_map.json
        // If the file does not exist, we write the defaults once so server owners can edit it.
        try {
            Path file = getDataFolder().toPath().resolve("model_cmd_map.json");
            if (!Files.exists(file)) {
                Files.createDirectories(getDataFolder().toPath());
                com.google.gson.Gson pretty = new com.google.gson.GsonBuilder().setPrettyPrinting().create();
                Files.writeString(file, pretty.toJson(modelCmdMap));
                getLogger().info("Wrote default model_cmd_map.json");
            }

            if (Files.exists(file)) {
                String json = Files.readString(file);
                Gson gson = new Gson();
                Type t = new TypeToken<Map<String, Integer>>() {}.getType();
                Map<String, Integer> m = gson.fromJson(json, t);
                if (m != null) {
                    for (Map.Entry<String, Integer> e : m.entrySet()) {
                        if (e.getKey() == null || e.getValue() == null) continue;
                        modelCmdMap.put(e.getKey().toLowerCase(), e.getValue());
                    }
                }
                getLogger().info("Loaded model_cmd_map.json entries: " + (m == null ? 0 : m.size()));
                // If the file existed but did not include newly added defaults, write back the merged map
                // (preserving any user overrides) so newly-added models in the resource pack work immediately.
                if (m == null || m.size() < modelCmdMap.size()) {
                    com.google.gson.Gson pretty2 = new com.google.gson.GsonBuilder().setPrettyPrinting().create();
                    Files.writeString(file, pretty2.toJson(modelCmdMap));
                    getLogger().info("Updated model_cmd_map.json with merged defaults: " + modelCmdMap.size());
                }

            }
        } catch (Exception e) {
            getLogger().warning("Failed to load model_cmd_map.json: " + e.getMessage());
        }
    }

    private void tryExtractResource(String resourcePath, java.io.File outFile) {
        try {
            if (resourcePath == null || outFile == null) return;
            java.io.File parent = outFile.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            try (java.io.InputStream in = getResource(resourcePath)) {
                if (in == null) return;
                java.nio.file.Files.copy(in, outFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception ignored) {
        }
    }




    @Override
    public void onDisable() {
        try { if (legendaryRandom != null) legendaryRandom.stop(); } catch (Throwable ignored) {}
        try {
            if (partySidebarManager != null) partySidebarManager.shutdown();
        } catch (Exception e) {
            getLogger().warning("Party sidebar cleanup on disable failed: " + e.getMessage());
        }
        // Despawn all summoned entities & orphan visuals to avoid "frozen" ghost models after restart.
        try {
            if (summonManager != null) summonManager.shutdown();
        } catch (Exception e) {
            getLogger().warning("Summon cleanup on disable failed: " + e.getMessage());
        }

        // Hard cleanup: remove any remaining Pokemon wolf entities to avoid invisible/ghost wolves after stop/restart.
        try {
            cleanupPokemonWolves();
        } catch (Exception e) {
            getLogger().warning("Wolf cleanup on disable failed: " + e.getMessage());
        }
        try {
            storage.saveAll();
        } catch (Exception e) {
            getLogger().warning("Save on disable failed: " + e.getMessage());
        }
        if (carrierMotionController != null) carrierMotionController.stop();
        if (spawns != null) spawns.stop();
        if (battles != null) battles.shutdown();
        try { if (tutorNpcManager != null) tutorNpcManager.shutdown(); } catch (Throwable ignored) {}
    }

    /** Remove any spawned Pokemon carrier wolves from all worlds (they should NOT persist across restarts). */
    private void cleanupPokemonWolves() {
        try {
            for (org.bukkit.World w : getServer().getWorlds()) {
                for (org.bukkit.entity.Wolf wolf : w.getEntitiesByClass(org.bukkit.entity.Wolf.class)) {
                    var pdc = wolf.getPersistentDataContainer();
                    boolean isPokemon = pdc.has(KEY_SPECIES, org.bukkit.persistence.PersistentDataType.STRING)
                            || pdc.has(KEY_OWNER, org.bukkit.persistence.PersistentDataType.STRING)
                            || pdc.has(KEY_WILD, org.bukkit.persistence.PersistentDataType.BYTE)
                            || pdc.has(KEY_CARRIER_OWNER, org.bukkit.persistence.PersistentDataType.STRING);
                    if (isPokemon) {
                        wolf.remove();
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }


    private int toggleCarrierDisplays(boolean show) {
        int changed = 0;
        VisualCarrierManager visuals = new VisualCarrierManager(this);
        for (org.bukkit.World world : getServer().getWorlds()) {
            for (org.bukkit.entity.Wolf wolf : world.getEntitiesByClass(org.bukkit.entity.Wolf.class)) {
                var pdc = wolf.getPersistentDataContainer();
                String species = pdc.get(KEY_SPECIES, org.bukkit.persistence.PersistentDataType.STRING);
                if (species == null || species.isBlank()) continue;
                if (show) {
                    String label = buildCarrierLabel(wolf, species);
                    visuals.attach(wolf, species, label);
                } else {
                    visuals.detach(wolf);
                }
                changed++;
            }
        }
        return changed;
    }

    private String buildCarrierLabel(org.bukkit.entity.Wolf wolf, String species) {
        try {
            if (summonManager != null) {
                java.util.UUID owner = summonManager.getOwnerUuidFromEntity(wolf);
                java.util.UUID puuid = summonManager.getPokemonUuidFromEntity(wolf);
                if (owner != null && puuid != null) {
                    PlayerProfile profile = storage.getProfile(owner);
                    if (profile != null) {
                        java.util.List<PokemonInstance> all = new java.util.ArrayList<>();
                        if (profile.party != null) all.addAll(profile.party);
                        if (profile.pc != null) all.addAll(profile.pc);
                        for (PokemonInstance p : all) {
                            if (p != null && puuid.equals(p.uuid)) {
                                return "§8Lv." + p.level + " §7" + p.displayName();
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
        return "§f" + species + "";
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        final Player player = (sender instanceof Player p) ? p : null;
        if (args.length == 0) {
            sender.sendMessage("§aPokeDemo 指令：");
            sender.sendMessage("§e/pokedemo giveball <poke|great|ultra> [数量] §7获取精灵球（管理员）");
            sender.sendMessage("§e/pokedemo giveitem <道具ID> [数量] §7获取道具（管理员/测试）");
            sender.sendMessage("§e/pokedemo give <精灵ID> [等级] §7发放精灵（管理员）");
            sender.sendMessage("§e/pokedemo summon <精灵ID> [等级] §7在身边召唤一只野生精灵（测试/管理员）");
            sender.sendMessage("§e/pokedemo importshowdown §7下载并导入 Pokemon Showdown 的 moves.json（管理员）");
            sender.sendMessage("§e/pokedemo importlearnsets §7下载并应用 Pokemon Showdown 的 learnsets.json（管理员）");
            sender.sendMessage("§e/pokedemo gen1report §7生成 Gen1 招式支持度报告（管理员）");
            sender.sendMessage("§e/pokedemo lang <zh_cn|en_us|ja_jp|ko_kr> §7切换语言（写入 plugins/PokeDemo/lang/lang.yml）");
            sender.sendMessage("§e/pokedemo cleanseMoves §7清理队伍/PC中不存在的招式并用可用招式补齐（管理员）");
            sender.sendMessage("§e/pokedemo starter §7手动打开初始御三家选择GUI（用于测试/补选）");
            sender.sendMessage("§e/pokedemo resetstarter §7重置自己的初始选择（用于测试）");
            sender.sendMessage("§e/pokedemo unlockall §7一键解除所有牧场/克隆机锁定（防卡死）");
            sender.sendMessage("§e/pokedemo spectateoff §7退出观战（仅聊天）");
            sender.sendMessage("§e/party §7打开队伍GUI");
            sender.sendMessage("§e/pc §7打开电脑盒子GUI");
            sender.sendMessage("§e/battle §7重新打开战斗界面（关闭GUI后可用）");
            sender.sendMessage("§e/poke control §7获取调试魔杖（调整模型大小/高度并热更新）");
            sender.sendMessage("§e/pokedemo displaysoff §7关闭 Display/TextDisplay 视觉层；之后新召唤的宝可梦也不会再显示");
            sender.sendMessage("§e/pokedemo tutor §7在身边召唤常驻招式教学师（仅管理员）");
            sender.sendMessage("§e/pokedemo tutorclear §7清除自己所在区块内的招式教学师（仅管理员）");
            sender.sendMessage("§e/pokedemo displayson §7重新开启 Display/TextDisplay 视觉层；当前已在场的宝可梦也会恢复显示");
            sender.sendMessage("§e提示：§7你可以在客户端把“交换副手物品(默认F)”改键为R，按下即可召唤/回收队伍第一只可用精灵。");
            return true;
        }
        String sub = args[0].toLowerCase();
        if (player == null) {
            if (!(sub.equals("importshowdown") || sub.equals("importlearnsets") || sub.equals("gen1report") || sub.equals("cleansemoves"))) {
                sender.sendMessage("仅玩家可用。");
                return true;
            }
        }
        switch (sub) {
            case "starter" -> {
                if (player == null) {
                    sender.sendMessage("仅玩家可用。");
                    return true;
                }
                UtilGui.openStarterSelect(player);
                return true;
            }
            case "resetstarter" -> {
                if (player == null) {
                    sender.sendMessage("仅玩家可用。");
                    return true;
                }
                PlayerProfile prof = storage.getProfile(player.getUniqueId());
                if (prof == null) {
                    sender.sendMessage("§c未找到玩家数据。");
                    return true;
                }
                prof.starterChosen = false;
                prof.starterSpeciesId = null;
                sender.sendMessage("§a已重置初始选择。下次打开背包会再次弹出，也可直接 /pokedemo starter 打开。");
                return true;
            }
            case "lang" -> {
                if (args.length < 2) {
                    sender.sendMessage("§e用法：/pokedemo lang <zh_cn|en_us|ja_jp|ko_kr>");
                    return true;
                }
                String locale = args[1].toLowerCase();
                if (!(locale.equals("zh_cn") || locale.equals("en_us") || locale.equals("ja_jp") || locale.equals("ko_kr"))) {
                    sender.sendMessage("§c未知语言：" + locale + " §7(可用: zh_cn/en_us/ja_jp/ko_kr)");
                    return true;
                }
                boolean ok = lang.setPrimaryLocale(locale);
                if (!ok) {
                    sender.sendMessage("§c写入 lang/lang.yml 失败。");
                    return true;
                }
                lang.load();
                sender.sendMessage("§a语言已切换为：" + locale + " §7(已重新加载)");
                return true;
            }

            case "unlockall" -> {
                if (player == null) {
                    sender.sendMessage("仅玩家可用。");
                    return true;
                }
                int unlocked = 0;
                // clear pokemon locks
                PlayerProfile prof = storage.getProfile(player.getUniqueId());
                if (prof != null) {
                    for (PokemonInstance p : prof.party) {
                        if (p != null && p.isEgg) continue; // eggs have special restrictions and are not affected by unlockall
                        if (p != null && p.uiLocked) { p.uiLocked = false; p.uiLockReason = ""; unlocked++; }
                    }
                    for (PokemonInstance p : prof.pc) {
                        if (p != null && p.isEgg) continue;
                        if (p != null && p.uiLocked) { p.uiLocked = false; p.uiLockReason = ""; unlocked++; }
                    }
                }

                // clear pasture selections owned by player
                try {
                    if (pastureManager != null) pastureManager.unlockAll(player.getUniqueId());
                } catch (Throwable ignored) {}

                // clear clone selections owned by player
                try {
                    if (cloneManager != null) unlocked += cloneManager.unlockAll(player.getUniqueId());
                } catch (Throwable ignored) {}

                // clear trade selections owned by player
                try {
                    if (tradeManager != null) unlocked += tradeManager.unlockAll(player.getUniqueId());
                } catch (Throwable ignored) {}

                sender.sendMessage("§a已解除锁定。解除数量：" + unlocked + "。\n§7牧场/克隆机/交换机需要重新选择精灵。");
                return true;
            }
            case "spectateoff" -> {
                if (player == null) {
                    sender.sendMessage("仅玩家可用。");
                    return true;
                }
                if (battles != null) {
                    battles.removeSpectator(player.getUniqueId());
                }
                player.sendMessage("§7已退出观战。");
                return true;
            }
            case "tutor" -> {
                if (player != null && !player.hasPermission("pokedemo.admin")) {
                    sender.sendMessage("§c你没有权限。");
                    return true;
                }
                if (player == null) {
                    sender.sendMessage("仅玩家可用。");
                    return true;
                }
                if (tutorNpcManager == null) {
                    sender.sendMessage("§c招式教学师系统未初始化。");
                    return true;
                }
                tutorNpcManager.spawnTutorNpc(player.getLocation(), false, sender);
                return true;
            }
            case "tutorclear" -> {
                if (player != null && !player.hasPermission("pokedemo.admin")) {
                    sender.sendMessage("§c你没有权限。");
                    return true;
                }
                if (tutorNpcManager == null) {
                    sender.sendMessage("§c招式教学师系统未初始化。");
                    return true;
                }
                if (player == null) {
                    sender.sendMessage("仅玩家可用。");
                    return true;
                }
                int removed = tutorNpcManager.cleanupTutorsInChunk(player.getLocation().getChunk());
                sender.sendMessage("§a已清除当前区块内的招式教学师：§f" + removed + "§a 个。");
                return true;
            }
            case "displaysoff" -> {
                if (player != null && !player.hasPermission("pokedemo.admin")) {
                    sender.sendMessage("§c你没有权限。");
                    return true;
                }
                setCarrierDisplaysEnabled(false);
                int hidden = toggleCarrierDisplays(false);
                sender.sendMessage("§a已关闭 Display/TextDisplay 视觉层：" + hidden + " 个承载实体已隐藏；之后新召唤的宝可梦也不会再显示。");
                return true;
            }
            case "displayson" -> {
                if (player != null && !player.hasPermission("pokedemo.admin")) {
                    sender.sendMessage("§c你没有权限。");
                    return true;
                }
                setCarrierDisplaysEnabled(true);
                int shown = toggleCarrierDisplays(true);
                sender.sendMessage("§a已开启 Display/TextDisplay 视觉层：" + shown + " 个承载实体已恢复；之后新召唤的宝可梦也会正常显示。");
                return true;
            }
            case "importshowdown" -> {
                if (player != null && !player.hasPermission("pokedemo.admin")) {
                    sender.sendMessage("§c你没有权限。");
                    return true;
                }
                sender.sendMessage("§6开始下载/导入 Showdown 数据（moves.json + pokedex.json）...（完成后会提示）");
                Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                    boolean okMoves = ShowdownDownloader.ensureMovesJson(this);
                    boolean okDex = ShowdownDownloader.ensurePokedexJson(this);
                    // Reload dex on main thread (it touches plugin resources/loggers)
                    Bukkit.getScheduler().runTask(this, () -> {
                        if (!okMoves && !okDex) {
                            sender.sendMessage("§c下载失败：请检查服务器网络/代理，或手动把 moves.json / pokedex.json 放到 plugins/PokeDemo/moves_raw/");
                            return;
                        }
                        dex.loadAll();
                        sender.sendMessage("§aShowdown 数据导入完成！当前 moves=" + dex.getMoveCount() + "，species=" + dex.getSpeciesCount());
                    });
                });
                return true;
            }
            case "importlearnsets" -> {
                if (player != null && !player.hasPermission("pokedemo.admin")) {
                    sender.sendMessage("§c你没有权限。");
                    return true;
                }
                sender.sendMessage("§6开始下载/应用 Showdown learnsets.json...（完成后会提示）");
                Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                    boolean ok = ShowdownDownloader.ensureLearnsetsJson(this);
                    Bukkit.getScheduler().runTask(this, () -> {
                        if (!ok) {
                            sender.sendMessage("§c下载失败：请检查服务器网络/代理，或手动把 learnsets.json 放到 plugins/PokeDemo/moves_raw/");
                            return;
                        }
                        dex.applyShowdownLearnsetsNow();
                        sender.sendMessage("§aShowdown learnsets 应用完成！现在已有技能树的精灵数量可能增加了。");
                    });
                });
                return true;
            }
case "gen1report" -> {
    if (player != null && !player.hasPermission("pokedemo.admin")) {
        sender.sendMessage("§c你没有权限。");
        return true;
    }
    var p = dex.writeGen1CoverageReport();
    if (p == null) {
        sender.sendMessage("§c生成报告失败，请查看控制台日志。");
    } else {
        sender.sendMessage("§a已生成 Gen1 招式支持度报告：§f" + p.toString());
    }
    return true;
}
            case "giveball" -> {
                if (player != null && !player.hasPermission("pokedemo.admin")) {
                    sender.sendMessage("§c你没有权限。");
                    return true;
                }
                String type = args.length >= 2 ? args[1].toUpperCase() : "POKE_BALL";
                int amt = args.length >= 3 ? Util.parseInt(args[2], 1) : 1;
                player.getInventory().addItem(items.createBall(type, amt));
                player.sendMessage("§a已获得：" + items.createBall(type, 1).getItemMeta().getDisplayName() + " §7x" + amt);
                return true;
            }
            case "giveitem" -> {
                if (player != null && !player.hasPermission("pokedemo.admin")) {
                    sender.sendMessage("§c你没有权限。");
                    return true;
                }
                if (player == null) {
                    sender.sendMessage("仅玩家可用。");
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage("§c用法: /pokedemo giveitem <道具ID> [数量]\n§7示例: /pokedemo giveitem potion 5");
                    return true;
                }
                String itemId = args[1].toLowerCase();
                int amt = args.length >= 3 ? Util.parseInt(args[2], 1) : 1;
                ItemDef def = (itemRegistry != null) ? itemRegistry.get(itemId) : null;
                if (def == null) {
                    player.sendMessage("§c未知道具ID: " + itemId);
                    if (itemRegistry != null) {
                        player.sendMessage("§7可用: " + String.join(", ", itemRegistry.all().keySet()));
                    }
                    return true;
                }
                player.getInventory().addItem(items.createItem(def, lang, amt));
                player.sendMessage("§a已获得：" + lang.item(def.id, def.id) + " §7x" + amt);
                return true;
            }
            case "cleansemoves" -> {
                if (player != null && !player.hasPermission("pokedemo.admin")) {
                    sender.sendMessage("§c你没有权限。");
                    return true;
                }
                int fixed = storage.cleanseMoves(player.getUniqueId(), dex);
                sender.sendMessage("§a已清理/修复招式：" + fixed + " 只精灵。§7（旧的跨世代技能会被移除）");
                return true;
            }
            case "give" -> {
                if (player != null && !player.hasPermission("pokedemo.admin")) {
                    sender.sendMessage("§c你没有权限。");
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage("§c用法: /pokedemo give <精灵ID> [等级]");
                    return true;
                }
                String speciesId = args[1].toLowerCase();
                int level = args.length >= 3 ? Util.parseInt(args[2], 5) : 5;
                Species s = dex.getSpecies(speciesId);
                if (s == null) {
                    player.sendMessage("§c未知精灵ID: " + speciesId);
                    return true;
                }
                // /pokedemo give: allow rolling hidden ability (梦特)
                PokemonInstance p = PokemonInstance.createOwnedAllowHidden(s, level, dex);
                p.originalTrainer = player.getUniqueId();
                p.originalTrainerName = player.getName();
                storage.getProfile(player.getUniqueId()).depositToPartyOrPc(p);
                // Automatically sanitize moves on newly granted pokemon (e.g. after switching dataset/generation).
                storage.cleanseMoves(player.getUniqueId(), dex);
                storage.markDirty(player.getUniqueId());
                player.sendMessage("§a已添加：" + s.name() + " Lv." + level + " → 队伍/电脑盒子");
                return true;
            }

            case "summon", "summonwild" -> {
                if (player != null && !player.hasPermission("pokedemo.admin")) {
                    sender.sendMessage("§c你没有权限。");
                    return true;
                }
                if (player == null) {
                    sender.sendMessage("仅玩家可用。");
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage("§c用法: /pokedemo summon <精灵ID> [等级]\n§7示例: /pokedemo summon pikachu 10");
                    return true;
                }
                String speciesId = args[1].toLowerCase();
                int level = args.length >= 3 ? Util.parseInt(args[2], 5) : 5;
                Species s = dex.getSpecies(speciesId);
                if (s == null) {
                    player.sendMessage("§c未知精灵ID: " + speciesId);
                    return true;
                }
                org.bukkit.Location loc = player.getLocation().clone().add(1.0, 0.0, 0.0);
                try {
                    loc.setY(loc.getWorld().getHighestBlockYAt(loc) + 1);
                } catch (Throwable ignored) {}
                java.util.UUID wid = (spawns != null) ? spawns.spawnWildManual(loc, speciesId, level) : null;
                if (wid == null) {
                    player.sendMessage("§c召唤失败。");
                } else {
                    player.sendMessage("§a已召唤野生精灵：§f" + s.name() + " §7Lv." + Util.clamp(level, 1, 100));
                }
                return true;
            }

            case "genchest" -> {
                if (player != null && !player.hasPermission("pokedemo.admin")) {
                    sender.sendMessage("§c你没有权限。");
                    return true;
                }
                if (player == null) {
                    sender.sendMessage("仅玩家可用。");
                    return true;
                }
                // Place a Poké Ball loot chest at targeted block (or at player's feet).
                org.bukkit.block.Block target = player.getTargetBlockExact(8);
                org.bukkit.Location loc;
                if (target != null) {
                    loc = target.getLocation().add(0, 1, 0);
                } else {
                    loc = player.getLocation().getBlock().getLocation().add(0, 1, 0);
                }
                org.bukkit.block.Block b = loc.getBlock();
                if (b.getType() != org.bukkit.Material.AIR && !b.isPassable()) {
                    player.sendMessage("§c这里放不下宝箱（需要空气方块）。");
                    return true;
                }
                b.setType(org.bukkit.Material.TRAPPED_CHEST, false);
                var st = b.getState();
                if (st instanceof org.bukkit.block.TileState tile) {
                    var keys = new NamespacedKeys(this);
                    var pdc = tile.getPersistentDataContainer();
                    var loot = PokeBallLootTables.generateLoot(this, items, itemRegistry);
                    pdc.set(keys.pokeChestLootKey(), org.bukkit.persistence.PersistentDataType.BYTE_ARRAY, PokeChestStorage.serializeItemStacks(loot));
                    pdc.set(keys.pokeChestMarkerKey(), org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
                    tile.update(true, false);
                    player.sendMessage("§a已生成精灵球宝箱（测试用）。");
                } else {
                    player.sendMessage("§c生成失败：不是可用的箱子方块。");
                }
                return true;
            }

            case "genore" -> {
                if (player != null && !player.hasPermission("pokedemo.admin")) {
                    sender.sendMessage("§c你没有权限。");
                    return true;
                }
                if (player == null) {
                    sender.sendMessage("仅玩家可用。");
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage("§c用法: /pokedemo genore <fire|water|thunder|leaf|moon>");
                    return true;
                }
                String t = args[1].toLowerCase();
                EvoOreType type = switch (t) {
                    case "fire" -> EvoOreType.FIRE;
                    case "water" -> EvoOreType.WATER;
                    case "thunder", "electric" -> EvoOreType.THUNDER;
                    case "leaf" -> EvoOreType.LEAF;
                    case "moon" -> EvoOreType.MOON;
                    default -> null;
                };
                if (type == null) {
                    player.sendMessage("§c未知类型：" + t);
                    return true;
                }

                org.bukkit.block.Block target = player.getTargetBlockExact(8);
                org.bukkit.Location loc;
                if (target != null) {
                    loc = target.getLocation();
                } else {
                    loc = player.getLocation().getBlock().getLocation();
                }
                org.bukkit.block.Block b = loc.getBlock();
                // If the targeted block is not replaceable, place on top.
                if (b.getType() != org.bukkit.Material.AIR && !b.isPassable()) {
                    b = b.getRelative(org.bukkit.block.BlockFace.UP);
                }
                if (b.getType() != org.bukkit.Material.AIR && !b.isPassable()) {
                    player.sendMessage("§c这里放不下矿物（需要空气方块）。");
                    return true;
                }
                b.setType(org.bukkit.Material.NOTE_BLOCK, false);
                var bd = b.getBlockData();
                if (bd instanceof org.bukkit.block.data.type.NoteBlock nb) {
                    nb.setInstrument(type.instrument);
                    nb.setNote(type.note);
                    nb.setPowered(false);
                    b.setBlockData(nb, false);
                }
                // Record ore location for restoration on neighbor updates.
                try {
                    var keys = new NamespacedKeys(PokeDemoPlugin.INSTANCE);
                    var pdc = b.getChunk().getPersistentDataContainer();
                    String existing = pdc.getOrDefault(keys.evoOreKey(), org.bukkit.persistence.PersistentDataType.STRING, "");
                    String entry = b.getX()+","+b.getY()+","+b.getZ()+","+type.name()+";";
                    if (!existing.contains(entry)) {
                        pdc.set(keys.evoOreKey(), org.bukkit.persistence.PersistentDataType.STRING, existing + entry);
                    }
                } catch (Throwable ignored) {}

                player.sendMessage("§a已生成进化石矿物：§f" + type.itemId);
                return true;
            }
            default -> {
                player.sendMessage("§c未知子命令。");
                return true;
            }
        }
        // (listeners are registered in onEnable)
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return List.of();
    }

    private void ensureBundledDefaultData() {
        try {
            // We keep runtime data under plugins/PokeDemo/.
            // If the server admin already provided their own files, we do not overwrite them.
            copyResourceIfMissing(
                    "default_data/moves_raw/tm_compat_gen1.json",
                    new java.io.File(getDataFolder(), "moves_raw/tm_compat_gen1.json")
            );
            tryExtractResource(
                    "default_data/moves_raw/tm_moves.json",
                    new java.io.File(getDataFolder(), "moves_raw/tm_moves.json")
            );
            tryExtractResource(
                    "default_data/moves_raw/machine_alias_moves.json",
                    new java.io.File(getDataFolder(), "moves_raw/machine_alias_moves.json")
            );
            tryExtractResource(
                    "default_data/moves_raw/tutor_alias_moves.json",
                    new java.io.File(getDataFolder(), "moves_raw/tutor_alias_moves.json")
            );

            // Custom Gen1 spawn rules preset (default, original ruleset)
            // We write it if missing. Additionally, if the file is an *old* bundled preset
            // (format=1 + style=custom_pixelmon_like), we upgrade it once to keep behavior
            // consistent with the latest preset. We do NOT overwrite user-custom presets.
            copyResourceAlwaysUpdateWithBackup(
                    "default_data/spawns_gen1_custom.yml",
                    new java.io.File(getDataFolder(), "spawns_gen1_custom.yml"),
                    "spawns_gen1_custom.bak"
            );
        } catch (Exception e) {
            getLogger().warning("[PokeDemo] ensureBundledDefaultData failed: " + e.getMessage());
        }
    }

    
/**
 * Always keep a bundled default data file in sync with the jar resource.
 * If the existing file differs, back it up (timestamped) then overwrite.
 */
private void copyResourceAlwaysUpdateWithBackup(String resourcePathInJar, java.io.File targetFile, String backupPrefix) throws java.io.IOException {
    java.io.File parent = targetFile.getParentFile();
    if (parent != null) parent.mkdirs();

    byte[] bundled;
    try (java.io.InputStream in = getResource(resourcePathInJar)) {
        if (in == null) {
            getLogger().warning("[PokeDemo] Missing bundled resource: " + resourcePathInJar);
            return;
        }
        bundled = in.readAllBytes();
    }

    boolean different = true;
    if (targetFile.exists()) {
        try {
            byte[] cur = java.nio.file.Files.readAllBytes(targetFile.toPath());
            different = !java.util.Arrays.equals(cur, bundled);
        } catch (Throwable t) {
            different = true;
        }
    }

    if (!different) return;

    if (targetFile.exists()) {
        String ts = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date());
        java.io.File backup = new java.io.File(targetFile.getParentFile(), backupPrefix + "_" + ts + ".yml");
        try {
            java.nio.file.Files.copy(targetFile.toPath(), backup.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            getLogger().info("[PokeDemo] Backed up spawn rules to: " + backup.getPath());
        } catch (Throwable ignored) {}
    }

    try {
        java.nio.file.Files.write(targetFile.toPath(), bundled);
        getLogger().info("[PokeDemo] Updated bundled default file: " + targetFile.getPath());
    } catch (Throwable t) {
        getLogger().warning("[PokeDemo] Failed to update default file: " + targetFile.getPath() + " : " + t.getMessage());
    }
}

private void copyResourceIfMissingOrLegacyCustomRules(String resourcePathInJar, java.io.File targetFile) throws java.io.IOException {
        boolean shouldWrite = !targetFile.exists();
        boolean shouldBackup = false;
        if (!shouldWrite) {
            try {
                org.bukkit.configuration.file.YamlConfiguration y = new org.bukkit.configuration.file.YamlConfiguration();
                y.load(targetFile);
                org.bukkit.configuration.ConfigurationSection meta = y.getConfigurationSection("meta");
                int fmt = meta != null ? meta.getInt("format", 0) : y.getInt("meta.format", 0);
                String style = meta != null ? meta.getString("style", "") : y.getString("meta.style", "");
                // Only upgrade the bundled preset we previously shipped.
                if (fmt == 1 && "custom_pixelmon_like".equalsIgnoreCase(style)) {
                    shouldWrite = true;
                    shouldBackup = true;
                }
            } catch (Throwable ignored) {
                // If it can't be parsed, do nothing (assume it's user-provided).
                shouldWrite = false;
            }
        }
        if (!shouldWrite) return;

        java.io.File parent = targetFile.getParentFile();
        if (parent != null) parent.mkdirs();
        if (shouldBackup) {
            java.io.File backup = new java.io.File(targetFile.getParentFile(), "spawns_gen1_custom.legacy.bak.yml");
            try {
                java.nio.file.Files.copy(targetFile.toPath(), backup.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                getLogger().info("[PokeDemo] Backed up legacy spawn rules to: " + backup.getPath());
            } catch (Throwable ignored) {}
        }
        try (java.io.InputStream in = getResource(resourcePathInJar)) {
            if (in == null) {
                getLogger().warning("[PokeDemo] Missing bundled resource: " + resourcePathInJar);
                return;
            }
            java.nio.file.Files.copy(in, targetFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            getLogger().info("[PokeDemo] Wrote default file: " + targetFile.getPath());
        }
    }

    /**
     * We used to ship a tiny demo spawn table that put everything into "any".
     * For Cobblemon-style Gen1 replication we now ship format=2 tables.
     * If a legacy spawn table exists, overwrite it once to avoid confusing behavior.
     */
    private void copyResourceIfMissingOrLegacySpawnTable(String resourcePathInJar, java.io.File targetFile) throws java.io.IOException {
        boolean shouldWrite = !targetFile.exists();
        if (!shouldWrite) {
            try {
                org.bukkit.configuration.file.YamlConfiguration y = new org.bukkit.configuration.file.YamlConfiguration();
                y.load(targetFile);
                int fmt = y.getInt("pokedemoSpawnFormat", 0);
                if (fmt < 2) shouldWrite = true;
            } catch (Throwable ignored) {
                // If it can't be parsed, overwrite with bundled default.
                shouldWrite = true;
            }
        }
        if (!shouldWrite) return;

        java.io.File parent = targetFile.getParentFile();
        if (parent != null) parent.mkdirs();
        try (java.io.InputStream in = getResource(resourcePathInJar)) {
            if (in == null) {
                getLogger().warning("[PokeDemo] Missing bundled resource: " + resourcePathInJar);
                return;
            }
            java.nio.file.Files.copy(in, targetFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            getLogger().info("[PokeDemo] Wrote default file: " + targetFile.getPath());
        }
    }

    private void copyResourceIfMissing(String resourcePathInJar, java.io.File targetFile) throws java.io.IOException {
        if (targetFile.exists()) return;
        java.io.File parent = targetFile.getParentFile();
        if (parent != null) parent.mkdirs();

        try (java.io.InputStream in = getResource(resourcePathInJar)) {
            if (in == null) {
                getLogger().warning("[PokeDemo] Missing bundled resource: " + resourcePathInJar);
                return;
            }
            java.nio.file.Files.copy(in, targetFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            getLogger().info("[PokeDemo] Wrote default file: " + targetFile.getPath());
        }
    }
    private void safeLoadConfig() {
        // Paper/Purpur's reloadConfig() logs YAML errors but may not throw.
        // We validate explicitly so bad YAML cannot silently fall back to defaults.
        try {
            java.io.File data = getDataFolder();
            if (!data.exists()) data.mkdirs();
            java.io.File cfg = new java.io.File(data, "config.yml");
            if (cfg.exists()) {
                org.bukkit.configuration.file.YamlConfiguration test = new org.bukkit.configuration.file.YamlConfiguration();
                test.load(cfg); // throws on invalid YAML
            }
        } catch (Throwable ex) {
            getLogger().severe("Config.yml is invalid, backing up and regenerating default config. " + ex.getMessage());
            try {
                java.io.File data = getDataFolder();
                if (!data.exists()) data.mkdirs();
                java.io.File cfg = new java.io.File(data, "config.yml");
                if (cfg.exists()) {
                    String ts = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date());
                    java.io.File bak = new java.io.File(data, "config.yml.bad_" + ts);
                    java.nio.file.Files.move(cfg.toPath(), bak.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (Throwable ignored) {}
            try {
                saveDefaultConfig();
            } catch (Throwable ignored) {}
        }

        // Now load (or reload) the config.
        try {
            reloadConfig();
            return;
        } catch (Throwable ex) {
            getLogger().severe("Failed to load config.yml via reloadConfig(): " + ex.getMessage());
        }
        try {
            java.io.File data = getDataFolder();
            if (!data.exists()) data.mkdirs();
            java.io.File cfg = new java.io.File(data, "config.yml");
            if (cfg.exists()) {
                String ts = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date());
                java.io.File bak = new java.io.File(data, "config.yml.bad_" + ts);
                java.nio.file.Files.move(cfg.toPath(), bak.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Throwable ignored) {}
        try {
            saveDefaultConfig();
            reloadConfig();
        } catch (Throwable ex2) {
            getLogger().severe("Failed to regenerate config.yml: " + ex2.getMessage());
        }
    }

    /**
     * Small config migration so users don't have to delete their config for balance tweaks.
     * Only adjusts values when the config was created before the latest defaults.
     */
    private void migrateConfigIfNeeded() {
        try {
            var cfg = getConfig();
            int ver = cfg.getInt("config-version", 0);
            final int CURRENT = 49;
            if (ver >= CURRENT) return;

            // Worldgen rates: make ores & loot chests rarer by default.
            // Ores now ~1/200 per chunk (was ~1/20); chests ~1/40 per chunk.
            if (cfg.getDouble("evo-ores.chance.leaf", 0.0) > 0.006) cfg.set("evo-ores.chance.leaf", 0.005);
            if (cfg.getDouble("evo-ores.chance.fire", 0.0) > 0.006) cfg.set("evo-ores.chance.fire", 0.005);
            if (cfg.getDouble("evo-ores.chance.water", 0.0) > 0.006) cfg.set("evo-ores.chance.water", 0.005);
            if (cfg.getDouble("evo-ores.chance.moon", 0.0) > 0.006) cfg.set("evo-ores.chance.moon", 0.005);
            if (cfg.getDouble("evo-ores.chance.thunder", 0.0) > 0.006) cfg.set("evo-ores.chance.thunder", 0.005);
            if (cfg.getDouble("loot-chests.spawn-chance", 0.0) > 0.051) cfg.set("loot-chests.spawn-chance", 0.05);

            if (!cfg.contains("loot-chests.master-ball-chance")) cfg.set("loot-chests.master-ball-chance", 0.01);
            if (!cfg.contains("spawns.cobblemon-import.enabled")) cfg.set("spawns.cobblemon-import.enabled", false);

            // Wild spawns: default to our original Gen1 ruleset.
            // If older configs point to legacy tables, switch automatically.
            String tf = cfg.getString("spawns.table-file", "");
            if (!cfg.contains("spawns.table-file")
                    || tf == null || tf.isBlank()
                    || "spawns.yml".equalsIgnoreCase(tf)
                    || "spawns_cobblemon_gen1.yml".equalsIgnoreCase(tf)
                    || "spawns_gen1_custom.yml".equalsIgnoreCase(tf)
                    || "spawns_gen1_exact_biomes.yml".equalsIgnoreCase(tf)
                    || "spawns_all_exact_biomes.yml".equalsIgnoreCase(tf)) {
                cfg.set("spawns.table-file", "spawns_all_exact_biomes.yml");
                cfg.set("spawns.cobblemon-import.enabled", false);
            }

            cfg.set("config-version", CURRENT);
            saveConfig();
        } catch (Throwable ex) {
            getLogger().warning("Config migration skipped: " + ex.getMessage());
        }
    }


}