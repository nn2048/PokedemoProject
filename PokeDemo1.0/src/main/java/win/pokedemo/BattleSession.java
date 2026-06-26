
package win.pokedemo;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class BattleSession {
    public UUID playerId;
    public UUID wildEntityId;

    // --- PvP ---
    /** True when this is a PvP battle session (player vs player). */
    public boolean pvp = false;
    /** Opponent player UUID (PvP only). */
    public UUID pvpOpponentId;
    /** Shared PvP battle key (sorted UUIDs). */
    public transient String pvpKey;

    // Track which of the player's Pokémon have participated (switched in at least once)
    public transient java.util.Set<java.util.UUID> playerParticipants = new java.util.HashSet<>();
    public PokemonInstance playerMon;
    public PokemonInstance wildMon;
    public int turn = 0;
    public boolean finished = false;

    // UI state
    public boolean guiOpen = false;
    public long lastGuiCloseAt = 0L;
    /**
     * When we intentionally transition from the main battle GUI to another GUI (switch/bag/ball),
     * we suppress the auto-reopen that normally happens on close.
     */
    public long suppressReopenUntilMs = 0L;

    // Side field effects (turns remaining)
    public int playerReflectTurns = 0;
    public int playerLightScreenTurns = 0;
    public int wildReflectTurns = 0;
    public int wildLightScreenTurns = 0;
    public int playerSafeguardTurns = 0;
    public int wildSafeguardTurns = 0;
    public int playerTailwindTurns = 0;
    public int wildTailwindTurns = 0;
    public int playerAuroraVeilTurns = 0;
    public int wildAuroraVeilTurns = 0;
    public int playerLuckyChantTurns = 0;
    public int wildLuckyChantTurns = 0;

    public int playerWishTurns = 0;
    public int wildWishTurns = 0;
    public int playerWishHeal = 0;
    public int wildWishHeal = 0;

    // Simple single-battle hazards / room states
    public boolean playerStealthRock = false;
    public boolean wildStealthRock = false;
    public int playerSpikesLayers = 0;
    public int wildSpikesLayers = 0;
    public int playerToxicSpikesLayers = 0;
    public int wildToxicSpikesLayers = 0;
    public boolean playerStickyWeb = false;
    public boolean wildStickyWeb = false;
    public int trickRoomTurns = 0;
    public int magicRoomTurns = 0;
    public int wonderRoomTurns = 0;
    public int gravityTurns = 0;
    public boolean playerHealingWishPending = false;
    public boolean wildHealingWishPending = false;
public transient String playerPlannedMoveId = null;
public transient String wildPlannedMoveId = null;
public transient boolean playerSnatchActive = false;
public transient boolean wildSnatchActive = false;
public transient boolean ionDelugeActive = false;
public String terrain = null;
public int terrainTurns = 0;
public int fairyLockTurns = 0;
public int playerPartyFaintedCount = 0;
public int wildPartyFaintedCount = 0;

    // Turn processing state
    public volatile boolean processingTurn = false;
    public String statusLine = "§7请选择一个招式。";

    /** When the active Pokémon faints and the player must choose a replacement before continuing. */
    public transient boolean awaitingForcedSwitch = false;

    /** If a faint happened mid-turn, we should still apply end-of-turn residuals after the forced switch. */
    public transient boolean pendingEndOfTurnAfterForcedSwitch = false;

    /**
     * If the wild Pokémon acted first and fainted the player's active Pokémon,
     * the player is forced to switch and then still gets to perform their action
     * as the remaining action of the current turn.
     */
    public transient boolean resumeAfterForcedSwitch = false;

    // --- Weather (minimal core) ---
    /** Current battle weather. */
    public WeatherType weather = WeatherType.NONE;
    /** Remaining turns for current weather (0 means no weather / ended). */
    public int weatherTurns = 0;

    // --- Wild battle escape ---
    /** Number of consecutive escape attempts in this battle (resets on player switch). */
    public transient int escapeAttempts = 0;

    // Spectators (chat-only). They will receive battle log messages.
    public final Set<UUID> spectators = new HashSet<>();

    // --- Battle visuals (transient, server-only) ---
    /** Visual carrier entity for player's active mon during this battle (wolf). */
    public transient UUID playerBattleCarrierId;
    /** Visual carrier entity for wild/opponent active mon during this battle (wolf). */
    public transient UUID wildBattleCarrierId;
    /** One-time battle anchor / facing for player side. */
    public transient UUID playerAnchorWorldId;
    public transient double playerAnchorX, playerAnchorY, playerAnchorZ;
    public transient float playerAnchorYaw;
    public transient boolean playerAnchorWater;
    /** One-time battle anchor / facing for wild side. */
    public transient UUID wildAnchorWorldId;
    public transient double wildAnchorX, wildAnchorY, wildAnchorZ;
    public transient float wildAnchorYaw;
    public transient boolean wildAnchorWater;
    /** Simple intro stage marker for battle visual sequencing. */
    public transient int introStage = 0;
    /** Original wild entity location to restore on run/escape. */
    public transient UUID wildOriginalWorldId;
    public transient double wildOriginalX, wildOriginalY, wildOriginalZ;
    public transient float wildOriginalYaw, wildOriginalPitch;
    public transient boolean wildOriginalAi;
    public transient boolean visualsSpawned;

    // Recent battle log lines for bridge UI (transient, server-only)
    public transient java.util.Deque<String> recentLog = new java.util.ArrayDeque<>();

    public BattleSession(UUID playerId, UUID wildEntityId, PokemonInstance playerMon, PokemonInstance wildMon) {
        this.playerId = playerId;
        this.wildEntityId = wildEntityId;
        this.playerMon = playerMon;
        this.wildMon = wildMon;
    }
}
