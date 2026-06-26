package win.pokedemo.bridge.client.battle;

import win.pokedemo.bridge.client.PokeDemoBridgeClient;
import win.pokedemo.bridge.common.CarrierRenderState;

import java.util.ArrayList;

public final class BridgeBattleStateStore {
    private static BridgeBattleViewState current;

    private BridgeBattleStateStore() {}

    public static BridgeBattleViewState current() {
        return current;
    }

    public static BridgeBattleViewState currentOrDemo() {
        if (current != null) return current;
        String foeSpecies = nearestSpecies();
        return BridgeBattleViewState.demo(PokeDemoBridgeClient.partyHudState().slots(), foeSpecies);
    }

    public static void setCurrent(BridgeBattleViewState state) {
        current = state;
    }

    public static void pushLocalLog(String line) {
        BridgeBattleViewState base = currentOrDemo();
        var log = new ArrayList<>(base.logLines());
        log.add(line);
        while (log.size() > 6) log.remove(0);
        current = new BridgeBattleViewState(base.self(), base.foe(), base.moves(), base.party(), log,
                base.canFight(), base.canSwitch(), base.canBag(), base.canRun(),
                base.canForfeit(), base.pvp(), base.awaitingForcedSwitch(), base.processingTurn(),
                base.statusLine(), base.requestType());
    }

    private static String nearestSpecies() {
        CarrierRenderState best = null;
        for (CarrierRenderState state : PokeDemoBridgeClient.entityRenderManager().snapshot().values()) {
            if (state == null) continue;
            if (best == null) {
                best = state;
                continue;
            }
        }
        return best != null ? best.species() : null;
    }
}
