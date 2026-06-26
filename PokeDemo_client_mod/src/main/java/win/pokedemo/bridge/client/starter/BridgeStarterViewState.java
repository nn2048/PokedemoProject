package win.pokedemo.bridge.client.starter;

import java.util.List;

public record BridgeStarterViewState(boolean active, String title, int selectedIndex, List<Entry> entries) {
    public record Entry(String species, String displayName, String description, String primaryType, String secondaryType, String gender, boolean shiny, int level) {}
}
