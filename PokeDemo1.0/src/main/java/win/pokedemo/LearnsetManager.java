package win.pokedemo;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Learnset (技能树/学招式) manager.
 *
 * Current scope (Gen1 first):
 * - Only supports Cobblemon-style level learn entries in species JSON "moves" array, e.g. "5:quickattack".
 * - On level up, learns moves for each reached level.
 * - If moveset is full (4 moves), queue pendingMoveLearns and open a GUI to let player choose a move to forget.
 */
public class LearnsetManager {
    private final PokeDemoPlugin plugin;
    private final Dex dex;
    private final Storage storage;

    public LearnsetManager(PokeDemoPlugin plugin, Dex dex, Storage storage) {
        this.plugin = plugin;
        this.dex = dex;
        this.storage = storage;
    }

    public void onLevelUp(java.util.UUID owner, PokemonInstance pokemon, Species species, int beforeLevel, int newLevel) {
        if (pokemon == null || species == null) return;
        if (newLevel <= beforeLevel) return;

        for (int lv = beforeLevel + 1; lv <= newLevel; lv++) {
            List<String> moves = species.movesLearnedAtLevel(lv);
            if (moves == null || moves.isEmpty()) continue;
            for (String moveId : moves) {
                tryLearnMove(owner, pokemon, moveId);
            }
        }

        // If player is online and there are pending learns, open GUI on the next tick.
        // This avoids conflicts with battle GUI closing/rendering.
        Player player = Bukkit.getPlayer(owner);
        if (player != null && player.isOnline() && pokemon.pendingMoveLearns != null && !pokemon.pendingMoveLearns.isEmpty()) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                UtilGui.openMoveLearn(player, storage, pokemon.uuid);
            });
        }
    }

    public void tryLearnMove(java.util.UUID owner, PokemonInstance pokemon, String moveId) {
        if (pokemon == null || moveId == null) return;
        String id = moveId.toLowerCase();

        // Never learn a move that isn't present/allowed in the current Dex.
        // This avoids Gen2+ learnsets injecting moves into a Gen1-only dataset,
        // which would otherwise degrade into placeholder "40 power" moves.
        if (!dex.isMoveAllowed(id)) {
            return;
        }
        if (pokemon.knowsMove(id)) return;

        // If there is an empty slot (or fewer than 4 moves), learn directly.
        if (pokemon.moves == null) pokemon.moves = new ArrayList<>();
        if (pokemon.moves.size() < 4) {
            pokemon.moves.add(MoveSlot.of(dex.getMoveOrPlaceholder(id)));
            storage.markDirty(owner);
            Player player = Bukkit.getPlayer(owner);
            if (player != null && player.isOnline()) {
                player.sendMessage("§a" + pokemon.displayName() + " 学会了 §e" + plugin.getLang().move(id, null) + "§a！");
            }
            return;
        }

        // Queue pending learn
        if (pokemon.pendingMoveLearns == null) pokemon.pendingMoveLearns = new ArrayList<>();
        if (!pokemon.pendingMoveLearns.contains(id)) pokemon.pendingMoveLearns.add(id);
        storage.markDirty(owner);

        Player player = Bukkit.getPlayer(owner);
        if (player != null && player.isOnline()) {
            player.sendMessage("§e" + pokemon.displayName() + " 想要学习 §b" + plugin.getLang().move(id, null) + "§e，但技能已满！");
            player.sendMessage("§7请在弹出的界面中选择要遗忘的技能。");
        }
    }

    /**
     * Like {@link #tryLearnMove(UUID, PokemonInstance, String)} but intended for player-triggered item learning
     * (TM/HM). If the moveset is full, the requested move is inserted to the FRONT of pendingMoveLearns so the
     * learn GUI always refers to the move just requested (avoids confusing mixes with level-up pending moves).
     *
     * @return true if learned immediately, false if queued or rejected.
     */
    public boolean tryLearnMoveFromItem(java.util.UUID owner, PokemonInstance pokemon, String moveId) {
        if (pokemon == null || moveId == null) return false;
        String id = moveId.toLowerCase();

        if (!dex.isMoveAllowed(id)) return false;
        if (pokemon.knowsMove(id)) return false;

        if (pokemon.moves == null) pokemon.moves = new ArrayList<>();
        if (pokemon.moves.size() < 4) {
            pokemon.moves.add(MoveSlot.of(dex.getMoveOrPlaceholder(id)));
            storage.markDirty(owner);
            Player player = Bukkit.getPlayer(owner);
            if (player != null && player.isOnline()) {
                player.sendMessage("§a" + pokemon.displayName() + " 学会了 §e" + plugin.getLang().move(id, null) + "§a！");
            }
            return true;
        }

        if (pokemon.pendingMoveLearns == null) pokemon.pendingMoveLearns = new ArrayList<>();
        // Remove existing occurrence then add to front
        pokemon.pendingMoveLearns.remove(id);
        pokemon.pendingMoveLearns.add(0, id);
        storage.markDirty(owner);

        Player player = Bukkit.getPlayer(owner);
        if (player != null && player.isOnline()) {
            player.sendMessage("§e" + pokemon.displayName() + " 想要学习 §b" + plugin.getLang().move(id, null) + "§e，但技能已满！");
            player.sendMessage("§7请在弹出的界面中选择要遗忘的技能。\n§8（按 ESC 关闭不会消耗技能机/秘传机）");
        }
        return false;
    }

    /** @return the move id that was learned (the pending one), or null if nothing happened. */
    public String forgetAndLearn(Player player, java.util.UUID owner, PokemonInstance pokemon, int forgetSlotIndex) {
        if (pokemon == null || pokemon.pendingMoveLearns == null || pokemon.pendingMoveLearns.isEmpty()) return null;
        if (forgetSlotIndex < 0 || forgetSlotIndex >= pokemon.moves.size()) return null;

        String newMoveId = pokemon.pendingMoveLearns.remove(0);
        MoveSlot slot = pokemon.moves.get(forgetSlotIndex);
        String oldMoveId = slot == null ? "unknown" : slot.moveId;

        pokemon.moves.set(forgetSlotIndex, MoveSlot.of(dex.getMoveOrPlaceholder(newMoveId)));
        storage.markDirty(owner);

        if (player != null) {
            player.sendMessage("§a" + pokemon.displayName() + " 遗忘了 §c" + plugin.getLang().move(oldMoveId, null) + "§a，学会了 §e" + plugin.getLang().move(newMoveId, null) + "§a！");
        }

        return newMoveId;
    }

    /** @return the move id that was cancelled, or null if nothing happened. */
    public String cancelLearn(Player player, java.util.UUID owner, PokemonInstance pokemon) {
        if (pokemon == null || pokemon.pendingMoveLearns == null || pokemon.pendingMoveLearns.isEmpty()) return null;
        String moveId = pokemon.pendingMoveLearns.remove(0);
        storage.markDirty(owner);
        if (player != null) {
            player.sendMessage("§7" + pokemon.displayName() + " 放弃学习 §f" + plugin.getLang().move(moveId, null) + "§7。");
        }

        return moveId;
    }
}
