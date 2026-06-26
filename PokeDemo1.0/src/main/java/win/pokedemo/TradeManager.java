package win.pokedemo;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Trade machine state manager.
 *
 * Goals:
 * - Trade swaps the exact {@link PokemonInstance} objects between two players (no re-creating),
 *   preserving IV/EV/nature/held item/current HP/status/etc.
 * - Selected Pokémon are uiLocked so players can't move them around mid-trade.
 * - Machine break unlocks and clears session.
 */
public class TradeManager {

    public enum Side { LEFT, RIGHT }

    public static final class PendingSelect {
        public final String tradeKey;
        public final Side side;
        public final long expiresAt;

        public PendingSelect(String tradeKey, Side side, long expiresAt) {
            this.tradeKey = tradeKey;
            this.side = side;
            this.expiresAt = expiresAt;
        }
    }

    private final JavaPlugin plugin;
    private final Storage storage;
    private final EvolutionManager evo;

    private final File file;
    private YamlConfiguration yaml;

    private final ConcurrentHashMap<UUID, PendingSelect> pending = new ConcurrentHashMap<>();

    public TradeManager(JavaPlugin plugin, Storage storage, EvolutionManager evo) {
        this.plugin = plugin;
        this.storage = storage;
        this.evo = evo;
        this.file = new File(plugin.getDataFolder(), "trades.yml");
        reload();
    }

    public void reload() {
        this.yaml = YamlConfiguration.loadConfiguration(file);
    }

    public String keyOf(Location loc) {
        return loc.getWorld().getName() + ":" + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }

    private void save() {
        try {
            yaml.save(file);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to save trades.yml: " + e.getMessage());
        }
    }

    // --- Pending selection ---
    public void beginSelect(UUID playerId, String tradeKey, Side side) {
        pending.put(playerId, new PendingSelect(tradeKey, side, System.currentTimeMillis() + 120_000L));
    }

    public PendingSelect getPending(UUID playerId) {
        PendingSelect p = pending.get(playerId);
        if (p == null) return null;
        if (p.expiresAt > 0 && System.currentTimeMillis() > p.expiresAt) {
            pending.remove(playerId);
            return null;
        }
        return p;
    }

    public void clearPending(UUID playerId) {
        pending.remove(playerId);
    }

    // --- Session state (persisted by machine key) ---
    public UUID getLeftPlayer(String tradeKey) {
        return readUuid(tradeKey + ".left.player");
    }

    public UUID getRightPlayer(String tradeKey) {
        return readUuid(tradeKey + ".right.player");
    }

    public void join(String tradeKey, UUID playerId) {
        if (tradeKey == null || playerId == null) return;
        UUID left = getLeftPlayer(tradeKey);
        UUID right = getRightPlayer(tradeKey);
        if (left == null) {
            yaml.set(tradeKey + ".left.player", playerId.toString());
            save();
            return;
        }
        if (left.equals(playerId)) return;
        if (right == null) {
            yaml.set(tradeKey + ".right.player", playerId.toString());
            save();
        }
    }

    /**
     * Leave a trade machine session. Clears that side's selection/confirm and unlocks the selected Pokémon.
     * This is used when a player exits the trade GUI.
     */
    public void leave(String tradeKey, UUID playerId) {
        if (tradeKey == null || playerId == null) return;
        UUID left = getLeftPlayer(tradeKey);
        UUID right = getRightPlayer(tradeKey);
        if (!playerId.equals(left) && !playerId.equals(right)) return;

        Side side = playerId.equals(left) ? Side.LEFT : Side.RIGHT;
        PlayerProfile prof = storage.getProfile(playerId);
        if (prof != null) {
            UUID sel = getSelected(tradeKey, side);
            if (sel != null) {
                PokemonInstance p = prof.findByUuid(sel);
                if (p != null) {
                    p.uiLocked = false;
                    p.uiLockReason = "";
                    storage.markDirty(playerId);
                }
            }
        }

        String base = tradeKey + (side == Side.LEFT ? ".left" : ".right");
        yaml.set(base + ".pokemon", null);
        yaml.set(base + ".partySlot", -1);
        yaml.set(base + ".confirm", false);

        // Remove occupying player id
        yaml.set(base + ".player", null);

        // Reset both confirmations
        yaml.set(tradeKey + ".left.confirm", false);
        yaml.set(tradeKey + ".right.confirm", false);

        save();
    }

    public boolean isInSession(String tradeKey, UUID playerId) {
        if (tradeKey == null || playerId == null) return false;
        UUID l = getLeftPlayer(tradeKey);
        UUID r = getRightPlayer(tradeKey);
        return playerId.equals(l) || playerId.equals(r);
    }

    public boolean isFull(String tradeKey) {
        return getLeftPlayer(tradeKey) != null && getRightPlayer(tradeKey) != null;
    }

    public UUID getSelected(String tradeKey, Side side) {
        return readUuid(tradeKey + (side == Side.LEFT ? ".left.pokemon" : ".right.pokemon"));
    }

    public int getSelectedPartySlot(String tradeKey, Side side) {
        return yaml.getInt(tradeKey + (side == Side.LEFT ? ".left.partySlot" : ".right.partySlot"), -1);
    }

    public boolean isConfirmed(String tradeKey, Side side) {
        return yaml.getBoolean(tradeKey + (side == Side.LEFT ? ".left.confirm" : ".right.confirm"), false);
    }

    public void setConfirmed(String tradeKey, Side side, boolean v) {
        yaml.set(tradeKey + (side == Side.LEFT ? ".left.confirm" : ".right.confirm"), v);
        save();
    }

    /** Assign selected Pokémon for a side, lock it, and reset confirmations. */
    public void assignFromParty(UUID owner, String tradeKey, Side side, UUID pokemonUuid, int partySlot) {
        if (owner == null || tradeKey == null || side == null) return;
        join(tradeKey, owner);

        PlayerProfile prof = storage.getProfile(owner);
        if (prof == null) return;

        // unlock previous
        UUID prev = getSelected(tradeKey, side);
        if (prev != null) {
            PokemonInstance old = prof.findByUuid(prev);
            if (old != null) {
                old.uiLocked = false;
                old.uiLockReason = "";
            }
        }

        // lock new
        if (pokemonUuid != null) {
            PokemonInstance p = prof.findByUuid(pokemonUuid);
            if (p != null) {
                p.uiLocked = true;
                p.uiLockReason = "交换机";
            }
        }

        String base = tradeKey + (side == Side.LEFT ? ".left" : ".right");
        yaml.set(base + ".pokemon", pokemonUuid == null ? null : pokemonUuid.toString());
        yaml.set(base + ".partySlot", partySlot);
        yaml.set(base + ".confirm", false);
        // also reset the other side confirm so both must reconfirm if any side changed
        yaml.set(tradeKey + ".left.confirm", false);
        yaml.set(tradeKey + ".right.confirm", false);

        save();
        storage.markDirty(owner);
    }

    /**
     * Execute trade if both confirmed and still valid.
     * This swaps the exact PokemonInstance objects between the two players.
     */
    public boolean tryExecuteTrade(Location machineLoc, Runnable after) {
        if (machineLoc == null) return false;
        String key = keyOf(machineLoc);
        UUID leftP = getLeftPlayer(key);
        UUID rightP = getRightPlayer(key);
        if (leftP == null || rightP == null) return false;

        if (!isConfirmed(key, Side.LEFT) || !isConfirmed(key, Side.RIGHT)) return false;

        PlayerProfile leftProf = storage.getProfile(leftP);
        PlayerProfile rightProf = storage.getProfile(rightP);
        if (leftProf == null || rightProf == null) return false;

        UUID leftUuid = getSelected(key, Side.LEFT);
        UUID rightUuid = getSelected(key, Side.RIGHT);
        int leftSlot = getSelectedPartySlot(key, Side.LEFT);
        int rightSlot = getSelectedPartySlot(key, Side.RIGHT);
        if (leftUuid == null || rightUuid == null) return false;

        PokemonInstance leftMon = leftProf.findByUuid(leftUuid);
        PokemonInstance rightMon = rightProf.findByUuid(rightUuid);
        if (leftMon == null || rightMon == null) return false;

        // Validate party slots still point to these mons (to avoid swapping wrong ones)
        if (leftSlot < 0 || leftSlot >= leftProf.party.size()) return false;
        if (rightSlot < 0 || rightSlot >= rightProf.party.size()) return false;
        if (leftProf.party.get(leftSlot) == null || !leftUuid.equals(leftProf.party.get(leftSlot).uuid)) return false;
        if (rightProf.party.get(rightSlot) == null || !rightUuid.equals(rightProf.party.get(rightSlot).uuid)) return false;

        // Swap references in PARTY
        leftProf.party.set(leftSlot, rightMon);
        rightProf.party.set(rightSlot, leftMon);

        // Unlock both
        leftMon.uiLocked = false;
        leftMon.uiLockReason = "";
        rightMon.uiLocked = false;
        rightMon.uiLockReason = "";

        // Mark dirty
        storage.markDirty(leftP);
        storage.markDirty(rightP);

        // Apply trade evolutions to the received mons
        Player leftPlayer = Bukkit.getPlayer(leftP);
        Player rightPlayer = Bukkit.getPlayer(rightP);

        // left received rightMon
        if (leftPlayer != null) handleTradeEvolution(leftPlayer, rightMon, leftMon);
        // right received leftMon
        if (rightPlayer != null) handleTradeEvolution(rightPlayer, leftMon, rightMon);

        // Clear session selections/confirms but keep join players so they can trade again without re-joining.
        yaml.set(key + ".left.pokemon", null);
        yaml.set(key + ".right.pokemon", null);
        yaml.set(key + ".left.partySlot", -1);
        yaml.set(key + ".right.partySlot", -1);
        yaml.set(key + ".left.confirm", false);
        yaml.set(key + ".right.confirm", false);
        save();

        if (after != null) {
            try { after.run(); } catch (Throwable ignored) {}
        }
        return true;
    }

    private boolean consumeHeldTradeItem(PokemonInstance mon, String expectedItemId) {
        if (mon == null || mon.heldItemId == null || expectedItemId == null) return false;
        if (!mon.heldItemId.equalsIgnoreCase(expectedItemId)) return false;
        mon.heldItemId = null;
        return true;
    }

    private void handleTradeEvolution(Player receiver, PokemonInstance receivedMon, PokemonInstance tradedForMon) {
        if (receiver == null || receivedMon == null || evo == null) return;
        if (receivedMon.heldItemId != null && receivedMon.heldItemId.equalsIgnoreCase("everstone")) return;
        String sid = receivedMon.speciesId == null ? "" : receivedMon.speciesId.toLowerCase(Locale.ROOT);
        String partner = tradedForMon == null || tradedForMon.speciesId == null ? "" : tradedForMon.speciesId.toLowerCase(Locale.ROOT);
        String target = null;
        switch (sid) {
            case "kadabra" -> target = "alakazam";
            case "machoke" -> target = "machamp";
            case "graveler" -> target = "golem";
            case "graveler_alolan", "graveler alolan" -> target = "golem_alolan";
            case "haunter" -> target = "gengar";
            case "boldore" -> target = "gigalith";
            case "gurdurr" -> target = "conkeldurr";
            case "phantump" -> target = "trevenant";
            case "pumpkaboo" -> target = "gourgeist";
            case "karrablast" -> { if ("shelmet".equals(partner)) target = "escavalier"; }
            case "shelmet" -> { if ("karrablast".equals(partner)) target = "accelgor"; }
            case "onix" -> { if (consumeHeldTradeItem(receivedMon, "metal_coat")) target = "steelix"; }
            case "scyther" -> { if (consumeHeldTradeItem(receivedMon, "metal_coat")) target = "scizor"; }
            case "seadra" -> { if (consumeHeldTradeItem(receivedMon, "dragon_scale")) target = "kingdra"; }
            case "porygon" -> { if (consumeHeldTradeItem(receivedMon, "up_grade")) target = "porygon2"; }
            case "porygon2" -> { if (consumeHeldTradeItem(receivedMon, "dubious_disc")) target = "porygon_z"; }
            case "rhydon" -> { if (consumeHeldTradeItem(receivedMon, "protector")) target = "rhyperior"; }
            case "electabuzz" -> { if (consumeHeldTradeItem(receivedMon, "electirizer")) target = "electivire"; }
            case "magmar" -> { if (consumeHeldTradeItem(receivedMon, "magmarizer")) target = "magmortar"; }
            case "dusclops" -> { if (consumeHeldTradeItem(receivedMon, "reaper_cloth")) target = "dusknoir"; }
            case "spritzee" -> { if (consumeHeldTradeItem(receivedMon, "sachet")) target = "aromatisse"; }
            case "swirlix" -> { if (consumeHeldTradeItem(receivedMon, "whipped_dream")) target = "slurpuff"; }
            case "feebas" -> { if (consumeHeldTradeItem(receivedMon, "prism_scale")) target = "milotic"; }
            case "poliwhirl" -> { if (consumeHeldTradeItem(receivedMon, "kings_rock")) target = "politoed"; }
            case "slowpoke" -> { if (consumeHeldTradeItem(receivedMon, "kings_rock")) target = "slowking"; }
            case "clamperl" -> {
                if (consumeHeldTradeItem(receivedMon, "deep_sea_tooth")) target = "huntail";
                else if (consumeHeldTradeItem(receivedMon, "deep_sea_scale")) target = "gorebyss";
            }
        }
        if (target != null) evo.evolveNow(receiver, receivedMon, target);
    }

    /** Called when the trade machine block is broken/removed. Must unlock any selected Pokémon and clear state. */
    public void onMachineBroken(Location loc) {
        if (loc == null) return;
        String key = keyOf(loc);
        UUID leftP = getLeftPlayer(key);
        UUID rightP = getRightPlayer(key);
        unlockSide(key, Side.LEFT, leftP);
        unlockSide(key, Side.RIGHT, rightP);
        yaml.set(key, null);
        save();
    }

    private void unlockSide(String key, Side side, UUID owner) {
        if (owner == null) return;
        PlayerProfile prof = storage.getProfile(owner);
        if (prof == null) return;
        UUID u = getSelected(key, side);
        if (u == null) return;
        PokemonInstance p = prof.findByUuid(u);
        if (p != null && p.uiLocked) {
            p.uiLocked = false;
            p.uiLockReason = "";
            storage.markDirty(owner);
        }
    }

    /** One-key unlock: clears all trade selections owned by the player and unlocks the selected Pokémon. */
    public int unlockAll(UUID owner) {
        if (owner == null) return 0;
        int unlocked = 0;
        for (String k : new ArrayList<>(yaml.getKeys(false))) {
            UUID l = getLeftPlayer(k);
            UUID r = getRightPlayer(k);
            if (!owner.equals(l) && !owner.equals(r)) continue;
            if (owner.equals(l)) {
                unlocked += unlockAndClear(k, Side.LEFT, owner);
            }
            if (owner.equals(r)) {
                unlocked += unlockAndClear(k, Side.RIGHT, owner);
            }
        }
        save();
        return unlocked;
    }

    private int unlockAndClear(String key, Side side, UUID owner) {
        int unlocked = 0;
        PlayerProfile prof = storage.getProfile(owner);
        if (prof != null) {
            UUID u = getSelected(key, side);
            if (u != null) {
                PokemonInstance p = prof.findByUuid(u);
                if (p != null && p.uiLocked) {
                    p.uiLocked = false;
                    p.uiLockReason = "";
                    unlocked++;
                    storage.markDirty(owner);
                }
            }
        }
        String base = key + (side == Side.LEFT ? ".left" : ".right");
        yaml.set(base + ".pokemon", null);
        yaml.set(base + ".partySlot", -1);
        yaml.set(base + ".confirm", false);
        return unlocked;
    }

    private UUID readUuid(String path) {
        try {
            String s = yaml.getString(path);
            return (s == null || s.isBlank()) ? null : UUID.fromString(s);
        } catch (Exception ignored) {
            return null;
        }
    }
}
