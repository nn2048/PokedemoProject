package win.pokedemo.bridge.client.hud;

import win.pokedemo.bridge.common.PartySlotState;
import win.pokedemo.bridge.common.PokemonStatus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PartyHudState {
    private volatile List<PartySlotState> slots = Collections.emptyList();
    private volatile boolean enabled = true;
    private volatile boolean debugMode = true;
    private volatile long lastServerSyncMillis = 0L;
    private volatile int selectedSlot = 0;

    public List<PartySlotState> slots() {
        return slots;
    }

    public void setSlots(List<PartySlotState> newSlots) {
        this.slots = Collections.unmodifiableList(new ArrayList<>(newSlots));
        this.lastServerSyncMillis = System.currentTimeMillis();
        this.debugMode = false;
        this.selectedSlot = normalizeSelectedSlot(this.selectedSlot, this.slots);
    }

    public boolean enabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean debugMode() {
        return debugMode;
    }

    public void setDebugMode(boolean debugMode) {
        this.debugMode = debugMode;
    }

    public boolean shouldUseDebugSlots() {
        return debugMode && slots.isEmpty();
    }

    public long lastServerSyncMillis() {
        return lastServerSyncMillis;
    }

    public List<PartySlotState> debugSlots() {
        return List.of(
                new PartySlotState(0, java.util.UUID.randomUUID(), true, "PIKACHU", "皮卡丘", 16, 39, 47, PokemonStatus.NONE, true, "M", false, "oran_berry", "poke_ball"),
                new PartySlotState(1, java.util.UUID.randomUUID(), true, "BUTTERFREE", "巴大蝶", 14, 21, 39, PokemonStatus.PSN, false, "F", false, "cheri_berry", "great_ball"),
                new PartySlotState(2, java.util.UUID.randomUUID(), true, "GEODUDE", "小拳石", 12, 0, 34, PokemonStatus.FNT, false, "M", false, "", "poke_ball"),
                new PartySlotState(3, null, false, "", "", 0, 0, 0, PokemonStatus.NONE, false, "N", false, "", "poke_ball"),
                new PartySlotState(4, null, false, "", "", 0, 0, 0, PokemonStatus.NONE, false, "N", false, "", "poke_ball"),
                new PartySlotState(5, null, false, "", "", 0, 0, 0, PokemonStatus.NONE, false, "N", false, "", "poke_ball")
        );
    }

    public int selectedSlot() {
        return selectedSlot;
    }

    public void setSelectedSlot(int selectedSlot) {
        this.selectedSlot = normalizeSelectedSlot(selectedSlot, shouldUseDebugSlots() ? debugSlots() : slots);
    }

    public void cycleSelectedSlot(int delta) {
        List<PartySlotState> current = shouldUseDebugSlots() ? debugSlots() : slots;
        this.selectedSlot = normalizeSelectedSlot(this.selectedSlot + delta, current);
    }

    public PartySlotState selectedSlotState() {
        List<PartySlotState> current = shouldUseDebugSlots() ? debugSlots() : slots;
        if (current.isEmpty()) return null;
        int index = normalizeSelectedSlot(selectedSlot, current);
        return index >= 0 && index < current.size() ? current.get(index) : null;
    }

    public PartySlotState firstActiveSlotState() {
        List<PartySlotState> current = shouldUseDebugSlots() ? debugSlots() : slots;
        for (PartySlotState slot : current) {
            if (slot != null && slot.occupied() && slot.active()) return slot;
        }
        return null;
    }

    public int firstActiveSlotIndex() {
        PartySlotState slot = firstActiveSlotState();
        return slot == null ? -1 : slot.slot();
    }

    private int normalizeSelectedSlot(int candidate, List<PartySlotState> current) {
        if (current == null || current.isEmpty()) return 0;
        int max = Math.min(6, current.size()) - 1;
        int normalized = Math.max(0, Math.min(max, candidate));
        for (int i = 0; i <= max; i++) {
            int idx = Math.floorMod(normalized + i, max + 1);
            PartySlotState slot = current.get(idx);
            if (slot != null && slot.occupied()) return idx;
        }
        return normalized;
    }

}
