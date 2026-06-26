package win.pokedemo;

import org.bukkit.Location;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;

public class FishingEncounterListener implements Listener {
    private final PokeDemoPlugin plugin;
    private final SpawnManager spawns;
    private final ItemRegistry registry;
    private final ItemFactory items;

    public FishingEncounterListener(PokeDemoPlugin plugin, SpawnManager spawns, ItemRegistry registry, ItemFactory items) {
        this.plugin = plugin;
        this.spawns = spawns;
        this.registry = registry;
        this.items = items;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFish(PlayerFishEvent e) {
        if (e.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        Player player = e.getPlayer();
        if (player == null) return;
        if (!plugin.getConfig().getBoolean("fishing-pokemon.enabled", true)) return;
        if (!hasUsablePartyPokemon(player)) return;
        if (!isValidRod(player.getInventory().getItemInMainHand())) return;

        FishingContext context = buildFishingContext(player.getInventory().getItemInMainHand(), player.getInventory().getItemInOffHand());
        double chance = plugin.getConfig().getDouble("fishing-pokemon.encounter-chance", 0.35D)
                + plugin.getConfig().getDouble("fishing-pokemon.lure-bonus-per-level", 0.10D) * Math.max(0, context.lureLevel - 1);

        Location loc = null;
        try { if (e.getHook() != null) loc = e.getHook().getLocation(); } catch (Throwable ignored) {}
        if (loc == null && e.getCaught() != null) loc = e.getCaught().getLocation();
        if (loc == null) loc = player.getLocation();

        if (isSpecialFishingSpot(loc)) chance += plugin.getConfig().getDouble("fishing-pokemon.special-spot-bonus", 0.10D);
        if (isNight(loc)) chance += plugin.getConfig().getDouble("fishing-pokemon.night-bonus", 0.05D);
        if (isRaining(loc)) chance += plugin.getConfig().getDouble("fishing-pokemon.rain-bonus", 0.05D);
        chance = Math.max(0.0D, Math.min(1.0D, chance));
        if (Util.RND.nextDouble() >= chance) return;

        SpawnSelector.Selection sel = spawns.selectFishingEncounter(loc, context);
        if (sel == null || sel.entry == null || sel.position == null) return;

        java.util.UUID id = spawns.spawnWildManual(sel.position.location, sel.entry.species, randomLevel(sel.entry));
        if (id == null) return;

        if (e.getCaught() instanceof Item item) item.remove();
        player.sendMessage("§b水面泛起波纹……有宝可梦上钩了！");
        try {
            org.bukkit.entity.Entity ent = org.bukkit.Bukkit.getEntity(id);
            if (ent instanceof Wolf wolf) plugin.battles().startWildBattle(player, wolf);
        } catch (Throwable ignored) {}
    }


    private FishingContext buildFishingContext(ItemStack mainHand, ItemStack offHand) {
        String rodType = null;
        java.util.LinkedHashSet<String> rodAliases = new java.util.LinkedHashSet<>();
        int lureLevel = 0;
        if (mainHand != null) {
            ItemDef def = items.identify(mainHand, registry);
            if (def != null && def.id != null) rodType = def.id;
            if (rodType == null && mainHand.getType() == org.bukkit.Material.FISHING_ROD) rodType = "minecraft:fishing_rod";
            try {
                lureLevel = mainHand.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.LURE);
            } catch (Throwable ignored) {}
            if (isPokemonRodId(rodType)) lureLevel = Math.max(1, lureLevel);
            addRodAliases(rodAliases, rodType, lureLevel);
        }
        String bait = null;
        java.util.LinkedHashSet<String> baitAliases = new java.util.LinkedHashSet<>();
        if (offHand != null) {
            ItemDef baitDef = items.identify(offHand, registry);
            if (baitDef != null && baitDef.id != null) bait = baitDef.id;
            addBaitAliases(baitAliases, bait, offHand.getType());
        }
        if ((baitAliases.contains("love_sweet") || baitAliases.contains("cobblemon:love_sweet")) && isPokemonRodId(rodType)) {
            rodAliases.add("love_rod");
            rodAliases.add("cobblemon:love_rod");
        }
        return new FishingContext(rodType, rodAliases, bait, baitAliases, lureLevel);
    }

    private void addRodAliases(java.util.Set<String> out, String rodType, int lureLevel) {
        if (out == null) return;
        String rt = rodType == null ? "" : rodType.trim().toLowerCase(java.util.Locale.ROOT);
        if (rt.isBlank()) return;
        out.add(rt);
        if (rt.contains(":")) out.add(rt.substring(rt.indexOf(':') + 1));
        if (rt.equals("minecraft:fishing_rod")) {
            out.add("basic");
            out.add("old_rod");
            out.add("cobblemon:old_rod");
        }
        if (rt.equals("poke_rod")) {
            out.add("basic");
            out.add("old_rod");
            out.add("cobblemon:old_rod");
        }
        if (rt.equals("great_rod")) {
            out.add("good_rod");
            out.add("cobblemon:good_rod");
        }
        if (rt.equals("ultra_rod")) {
            out.add("super_rod");
            out.add("cobblemon:super_rod");
        }
        if (rt.equals("master_rod")) {
            out.add("master_rod");
            out.add("cobblemon:master_rod");
        }
        if (rt.equals("love_rod")) {
            out.add("love_rod");
            out.add("cobblemon:love_rod");
            out.add("good_rod");
            out.add("cobblemon:good_rod");
        }
        if ((rt.equals("poke_rod") || rt.equals("great_rod") || rt.equals("ultra_rod") || rt.equals("master_rod") || rt.equals("love_rod")) && lureLevel >= 2) {
            out.add("super_rod");
            out.add("cobblemon:super_rod");
        }
        if ((rt.equals("great_rod") || rt.equals("ultra_rod") || rt.equals("master_rod") || rt.equals("love_rod")) && lureLevel >= 3) {
            out.add("master_rod");
            out.add("cobblemon:master_rod");
        }
    }

    private void addBaitAliases(java.util.Set<String> out, String bait, org.bukkit.Material mat) {
        if (out == null) return;
        String b = bait == null ? "" : bait.trim().toLowerCase(java.util.Locale.ROOT);
        if (!b.isBlank()) {
            out.add(b);
            if (b.contains(":")) out.add(b.substring(b.indexOf(':') + 1));
            if (b.endsWith("_berry")) out.add("berry");
            if (b.endsWith("_sweet")) {
                out.add("sweet");
                out.add("cobblemon:sweet");
            }
            if (b.equals("love_sweet") || b.equals("cobblemon:love_sweet")) {
                out.add("love_sweet");
                out.add("cobblemon:love_sweet");
            }
            if (b.equals("strawberry_sweet") || b.equals("cobblemon:strawberry_sweet")) {
                out.add("strawberry_sweet");
                out.add("cobblemon:strawberry_sweet");
            }
            if (b.equals("berry_sweet") || b.equals("cobblemon:berry_sweet")) {
                out.add("berry_sweet");
                out.add("cobblemon:berry_sweet");
            }
            if (b.equals("clover_sweet") || b.equals("cobblemon:clover_sweet")) {
                out.add("clover_sweet");
                out.add("cobblemon:clover_sweet");
            }
            if (b.equals("flower_sweet") || b.equals("cobblemon:flower_sweet")) {
                out.add("flower_sweet");
                out.add("cobblemon:flower_sweet");
            }
            if (b.equals("star_sweet") || b.equals("cobblemon:star_sweet")) {
                out.add("star_sweet");
                out.add("cobblemon:star_sweet");
            }
            if (b.equals("ribbon_sweet") || b.equals("cobblemon:ribbon_sweet")) {
                out.add("ribbon_sweet");
                out.add("cobblemon:ribbon_sweet");
            }
        }
        if (mat == null) return;
        switch (mat) {
            case SUGAR -> {
                out.add("love_sweet");
                out.add("cobblemon:love_sweet");
                out.add("sweet");
                out.add("cobblemon:sweet");
            }
            case SWEET_BERRIES, GLOW_BERRIES -> out.add("berry");
            default -> {
            }
        }
    }

    private boolean isSpecialFishingSpot(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        return StructureUtil.isNearAnyStructure(loc.getWorld(), loc, java.util.List.of("SHIPWRECK", "OCEAN_RUIN", "OCEAN_MONUMENT"), 160, false);
    }

    private boolean isNight(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        long t = loc.getWorld().getTime();
        return !(t >= 0 && t < 12300);
    }

    private boolean isRaining(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        try { return loc.getWorld().hasStorm(); } catch (Throwable ignored) { return false; }
    }

    private boolean isValidRod(ItemStack stack) {
        if (stack == null) return false;
        ItemDef def = items.identify(stack, registry);
        if (def != null && isPokemonRodId(def.id)) return true;
        return plugin.getConfig().getBoolean("fishing-pokemon.allow-vanilla-rod", false) && stack.getType() == org.bukkit.Material.FISHING_ROD;
    }

    private boolean hasUsablePartyPokemon(Player player) {
        try {
            PlayerProfile prof = plugin.getStorage().getProfile(player.getUniqueId());
            if (prof == null || prof.party == null) return false;
            for (PokemonInstance p : prof.party) {
                if (p == null || p.isEgg) continue;
                if (p.currentHp > 0) return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }


    private boolean isPokemonRodId(String id) {
        if (id == null) return false;
        return java.util.Set.of("poke_rod","great_rod","ultra_rod","master_rod","love_rod").contains(id.toLowerCase(java.util.Locale.ROOT));
    }

    private int randomLevel(SpawnTable.Entry e) {
        int min = Math.max(1, e.minLevel);
        int max = Math.max(min, e.maxLevel);
        return min + Util.RND.nextInt(max - min + 1);
    }
}
