package win.pokedemo.bridge.client.pc;

public final class BridgePcStateStore {
    private static volatile BridgePcViewState current = BridgePcViewState.inactive();

    private BridgePcStateStore() {}

    public static BridgePcViewState current() {
        return current;
    }

    public static void setCurrent(BridgePcViewState state) {
        current = state == null ? BridgePcViewState.inactive() : state;
    }

    public static void clear() {
        current = BridgePcViewState.inactive();
    }
}
