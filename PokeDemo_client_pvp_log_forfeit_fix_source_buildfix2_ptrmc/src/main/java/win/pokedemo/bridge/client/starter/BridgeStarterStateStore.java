package win.pokedemo.bridge.client.starter;

public final class BridgeStarterStateStore {
    private static volatile BridgeStarterViewState current;

    private BridgeStarterStateStore() {}

    public static BridgeStarterViewState current() { return current; }
    public static void setCurrent(BridgeStarterViewState state) { current = state; }
    public static void clear() { current = null; }
}
