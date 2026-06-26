package win.pokedemo.bridge.client.pc;

import java.util.List;

public record BridgePcViewState(
        boolean active,
        int page,
        int totalCount,
        boolean hasPrev,
        boolean hasNext,
        int pendingReleaseIndex,
        List<BridgePcSlotState> slots
) {
    public static BridgePcViewState inactive() {
        return new BridgePcViewState(false, 0, 0, false, false, -1, java.util.List.of());
    }
}
