
package win.pokedemo;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

public class GuiHolder implements InventoryHolder {
    public final GuiType type;
    public final UUID playerId;
    public int page = 0;
    public BattleSession battleSession;
    // PARTY GUI: used for click-to-swap ordering
    public int selectedPartySlot = -1;

    // PVP_READY GUI
    public java.util.UUID pvpOpponentId;
    public boolean pvpConfirmed = false;

    // MOVE_LEARN GUI
    public UUID moveLearnPokemonUuid;
    /** If true, this MOVE_LEARN GUI was opened due to TM/HM (item learning). */
    public boolean moveLearnFromItem = false;

    // SUMMARY GUI
    public int summaryTab = 0; // 0 info, 1 moves, 2 stats, 3 iv/ev
    public UUID summaryPokemonUuid;
    public boolean summaryFromParty = true;
    public int summaryPartySlot = -1;
    public int summaryPcIndex = -1;

    // POKEDEX
    public String pokedexSpeciesId;
    public int pokedexReturnPage = 0;
    public boolean pokedexOpenedFromPhone = false;


    // TUTOR
    public java.util.List<String> tutorMoves;
    public String tutorSelectedMove;

    // RECIPES
    public String recipeCategory;
    public String recipeKey;
    public int recipeReturnPage = 0;
    public boolean recipeOpenedFromPhone = false;

    // WILD_LOOT
    /** Where to drop remaining items when the loot GUI is closed. */
    public org.bukkit.Location lootDropLocation;
    /** Guard to avoid double-dropping on close. */
    public boolean lootClosed = false;

    // PASTURE
    /** Pasture block location key (world:x,y,z) */
    public String pastureKey;

    // FOSSIL MACHINE
    /** Fossil machine block location key (world:x,y,z) */
    public String fossilKey;
    /** Which fossil is being selected in the fossil select GUI. */
    public String fossilSelectReturnToKey;

    // FOSSIL ANALYZER
    public String fossilAnalyzerKey;

    // CLONE MACHINE
    /** Clone machine block location key (world:x,y,z) */
    public String cloneKey;

    // TRADE MACHINE
    /** Trade machine block location key (world:x,y,z) */
    public String tradeKey;

    /** If this holder is a party selection screen for trade, which side is selecting. */
    public TradeManager.Side tradeSelectSide;

    public GuiHolder(GuiType type, UUID playerId) {
        this.type = type;
        this.playerId = playerId;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
