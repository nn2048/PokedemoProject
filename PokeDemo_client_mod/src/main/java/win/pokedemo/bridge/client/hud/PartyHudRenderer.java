package win.pokedemo.bridge.client.hud;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2fStack;
import win.pokedemo.bridge.client.model.render.BridgePreviewRenderer;
import win.pokedemo.bridge.client.render.PortraitRenderUtil;
import win.pokedemo.bridge.common.PartySlotState;
import win.pokedemo.bridge.common.PokemonStatus;

import java.util.List;

public final class PartyHudRenderer implements HudRenderCallback {
    private static final Identifier SLOT = Identifier.of("pokedemo_bridge", "hud/party_slot");
    private static final Identifier SLOT_FAINTED = Identifier.of("pokedemo_bridge", "hud/party_slot_fainted");
    private static final Identifier POKE_BALL = Identifier.of("pokedemo_bridge", "hud/poke_ball");
    private static final Identifier SHINY = Identifier.of("pokedemo_bridge", "hud/icon_shiny");
    private static final Identifier STATUS_PSN = Identifier.of("pokedemo_bridge", "hud/status_psn");
    private static final Identifier STATUS_BRN = Identifier.of("pokedemo_bridge", "hud/status_brn");
    private static final Identifier STATUS_PAR = Identifier.of("pokedemo_bridge", "hud/status_par");
    private static final Identifier STATUS_SLP = Identifier.of("pokedemo_bridge", "hud/status_slp");
    private static final Identifier STATUS_FRZ = Identifier.of("pokedemo_bridge", "hud/status_frz");
    private static final Identifier STATUS_TOX = Identifier.of("pokedemo_bridge", "hud/status_tox");
    private static final Identifier STATUS_FNT = Identifier.of("pokedemo_bridge", "hud/status_fnt");

    private static final int BASE_X = 8;
    private static final int BASE_Y = 14;
    private static final int SLOT_W = 62;
    private static final int SLOT_H = 30;
    private static final int STEP_Y = 28;

    private final PartyHudState state;

    public PartyHudRenderer(PartyHudState state) {
        this.state = state;
    }

    @Override
    public void onHudRender(DrawContext dc, RenderTickCounter tickCounter) {
        if (!state.enabled()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer tr = client.textRenderer;
        List<PartySlotState> slots = state.shouldUseDebugSlots() ? state.debugSlots() : state.slots();
        if (slots.isEmpty()) return;

        int renderCount = lastVisibleSlot(slots);
        for (int i = 0; i < renderCount; i++) {
            PartySlotState slot = i < slots.size()
                    ? slots.get(i)
                    : new PartySlotState(i, null, false, "", "", 0, 0, 0, PokemonStatus.NONE, false, "N", false, "", "poke_ball");
            drawSlot(dc, tr, BASE_X, BASE_Y + STEP_Y * i, slot);
        }
    }

    private int lastVisibleSlot(List<PartySlotState> slots) {
        int last = -1;
        for (int i = 0; i < Math.min(6, slots.size()); i++) {
            if (slots.get(i).occupied()) last = i;
        }
        return Math.max(0, last + 1);
    }

    private void drawSlot(DrawContext dc, TextRenderer tr, int x, int y, PartySlotState slot) {
        if (slot.occupied()) {
            Identifier bg = (slot.status() == PokemonStatus.FNT || slot.hp() <= 0) ? SLOT_FAINTED : SLOT;
            dc.drawGuiTexture(RenderPipelines.GUI_TEXTURED, bg, x, y, SLOT_W, SLOT_H);
        } else {
            drawCollapsedSlot(dc, x, y);
        }

        if (slot.slot() == state.selectedSlot()) {
            dc.drawStrokedRectangle(x - 2, y - 2, SLOT_W + 4, SLOT_H + 4, 0xCCFFE36E);
        }
        if (slot.active()) {
            dc.drawStrokedRectangle(x - 1, y - 1, SLOT_W + 2, SLOT_H + 2, 0xAA77E8FF);
        }

        if (!slot.occupied()) {
            drawEmptyMarker(dc, x, y);
            return;
        }

        drawLevel(tr, dc, x + 4, y + 4, slot.level());
        drawPortrait(dc, tr, x + 18, y + 4, slot);
        drawHeldItem(dc, tr, x + 4, y + 18, slot);
        drawName(tr, dc, x + 4, y + 20, slot);
        drawGenderAndShiny(tr, dc, x + 35, y + 20, slot);
        drawStatus(dc, tr, x + 13, y + 22, effectiveStatus(slot));
        drawBars(dc, x + 56, y + 3, slot);
        dc.drawGuiTexture(RenderPipelines.GUI_TEXTURED, POKE_BALL, x + 46, y + 17, 11, 12);
    }

    private void drawCollapsedSlot(DrawContext dc, int x, int y) {
        dc.drawGuiTexture(RenderPipelines.GUI_TEXTURED, SLOT, x, y, SLOT_W, SLOT_H);
        dc.fill(x + 2, y + 2, x + SLOT_W - 2, y + SLOT_H - 2, 0xAA1F2328);
    }

    private void drawEmptyMarker(DrawContext dc, int x, int y) {
        int bx = x + 4;
        int by = y + 4;
        dc.fill(bx, by, bx + 8, by + 8, 0xFFC9C9C9);
        dc.fill(bx + 1, by + 1, bx + 7, by + 7, 0xFF666A70);
    }

    private void drawHeldItem(DrawContext dc, TextRenderer tr, int x, int y, PartySlotState slot) {
        int outer = slot.occupied() ? 0xFFC9C9C9 : 0xFF8B8B8B;
        int inner = slot.occupied() ? 0xFF666A70 : 0xFF575757;
        dc.fill(x, y, x + 8, y + 8, outer);
        dc.fill(x + 1, y + 1, x + 7, y + 7, inner);

        Identifier item = HudAssetResolver.findHeldItemTexture(slot.heldItemId());
        if (item != null) {
            int[] size = HudAssetResolver.imageSize(item);
            dc.drawTexture(RenderPipelines.GUI_TEXTURED, item, x + 1, y + 1, 0.0f, 0.0f, 6, 6, size[0], size[1], size[0], size[1]);
            return;
        }

        if (slot.heldItemId() != null && !slot.heldItemId().isBlank()) {
            Matrix3x2fStack matrices = dc.getMatrices();
            matrices.pushMatrix();
            matrices.translate(x + 2, y + 2);
            matrices.scale(0.5f, 0.5f);
            String symbol = slot.heldItemId().substring(0, 1).toUpperCase();
            dc.drawText(tr, Text.literal(symbol), 0, 0, 0xFFFFFFFF, false);
            matrices.popMatrix();
        }
    }

    private void drawLevel(TextRenderer tr, DrawContext dc, int x, int y, int level) {
        dc.drawText(tr, Text.literal("Lv.").formatted(Formatting.WHITE), x, y, 0xFFFFFFFF, false);
        dc.drawText(tr, Text.literal(String.valueOf(level)).formatted(Formatting.WHITE), x, y + 8, 0xFFFFFFFF, false);
    }

    private void drawPortrait(DrawContext dc, TextRenderer tr, int x, int y, PartySlotState slot) {
        dc.fill(x, y, x + 14, y + 14, 0xFF6C6F76);
        dc.fill(x + 1, y + 1, x + 13, y + 13, 0xFF20242A);

        // GUI 3D portrait path is temporarily disabled here because the current HUD render pass
        // can produce black silhouettes on some environments/mappings. Fall back to the stable
        // legacy icon path until the dedicated portrait renderer is fully aligned with the GUI pipeline.

        if (PortraitRenderUtil.drawSpeciesPortrait(dc, slot.species(), x + 1, y + 1, 12, 12)) {
            return;
        }

        dc.fill(x + 1, y + 1, x + 13, y + 13, speciesColor(slot.species()));
        String abbrev = speciesAbbrev(slot.displayName(), slot.species());
        int w = tr.getWidth(abbrev);
        dc.drawText(tr, Text.literal(abbrev).formatted(Formatting.WHITE), x + (14 - w) / 2, y + 3, 0xFFFFFFFF, false);
    }

    private void drawPortraitTexture(DrawContext dc, HudAssetResolver.PortraitSlice portrait, int x, int y, int w, int h) {
        dc.drawTexture(RenderPipelines.GUI_TEXTURED, portrait.texture(), x, y,
                (float) portrait.u(), (float) portrait.v(), w, h,
                portrait.width(), portrait.height(), portrait.textureWidth(), portrait.textureHeight());
    }

    private void drawName(TextRenderer tr, DrawContext dc, int x, int y, PartySlotState slot) {
        int nameColor = slot.hp() <= 0 ? 0xFFB0B0B0 : 0xFFFFFF;
        String name = trimName(slot.displayName());
        Matrix3x2fStack matrices = dc.getMatrices();
        matrices.pushMatrix();
        matrices.translate(x, y);
        matrices.scale(0.75f, 0.75f);
        dc.drawText(tr, Text.literal(name), 0, 0, nameColor, false);
        matrices.popMatrix();
    }

    private void drawGenderAndShiny(TextRenderer tr, DrawContext dc, int x, int y, PartySlotState slot) {
        String gender = genderMarker(slot.gender());
        if (!gender.isEmpty()) {
            Formatting genderFmt = "F".equalsIgnoreCase(slot.gender()) ? Formatting.LIGHT_PURPLE : Formatting.AQUA;
            dc.drawText(tr, Text.literal(gender).formatted(genderFmt), x, y, 0xFFFFFFFF, false);
        }
        if (slot.shiny()) {
            dc.drawGuiTexture(RenderPipelines.GUI_TEXTURED, SHINY, x + 8, y, 7, 7);
        }
    }

    private void drawStatus(DrawContext dc, TextRenderer tr, int x, int y, PokemonStatus status) {
        Identifier tex = statusTexture(status);
        String label = statusLabel(status);
        if (tex == null || label == null) {
            return;
        }
        dc.drawGuiTexture(RenderPipelines.GUI_TEXTURED, tex, x, y, 18, 6);

        Matrix3x2fStack matrices = dc.getMatrices();
        matrices.pushMatrix();
        matrices.translate(x + 1, y + 1);
        matrices.scale(0.5f, 0.5f);
        int textWidth = tr.getWidth(label);
        int drawX = Math.max(0, (32 - textWidth) / 2);
        dc.drawText(tr, Text.literal(label), drawX, -1, statusTextColor(status), false);
        matrices.popMatrix();
    }

    private void drawBars(DrawContext dc, int x, int y, PartySlotState slot) {
        drawVerticalBar(dc, x, y, 3, 24, 0xFF2D3137, hpColor(slot), ratio(slot.hp(), slot.maxHp()));
        float expRatio = pseudoExpRatio(slot.level());
        drawVerticalBar(dc, x - 3, y, 1, 24, 0xFF2D3137, 0xFF39A8FF, expRatio);
    }

    private void drawVerticalBar(DrawContext dc, int x, int y, int w, int h, int bg, int fg, float ratio) {
        dc.fill(x, y, x + w, y + h, bg);
        int innerW = Math.max(1, w - 1);
        int fill = Math.max(0, Math.round(Math.max(0f, Math.min(1f, ratio)) * (h - 2)));
        dc.fill(x + 1, y + (h - 1 - fill), x + innerW, y + h - 1, fg);
    }

    private float ratio(int value, int max) {
        if (max <= 0) return 0f;
        return Math.max(0f, Math.min(1f, value / (float) max));
    }

    private float pseudoExpRatio(int level) {
        int mod = Math.floorMod(level, 10);
        return 0.1f + (mod / 10.0f) * 0.85f;
    }

    private int hpColor(PartySlotState slot) {
        if (slot.status() == PokemonStatus.FNT || slot.hp() <= 0 || slot.maxHp() <= 0) return 0xFF6D6D6D;
        float ratio = slot.hp() / (float) slot.maxHp();
        if (ratio > 0.5f) return 0xFF49E06C;
        if (ratio > 0.2f) return 0xFFE8C552;
        return 0xFFE65C5C;
    }

    private Identifier statusTexture(PokemonStatus status) {
        return switch (status) {
            case PSN -> STATUS_PSN;
            case BRN -> STATUS_BRN;
            case PAR -> STATUS_PAR;
            case SLP -> STATUS_SLP;
            case FRZ -> STATUS_FRZ;
            case TOX -> STATUS_TOX;
            case FNT -> STATUS_FNT;
            default -> null;
        };
    }

    private PokemonStatus effectiveStatus(PartySlotState slot) {
        if (slot.status() == PokemonStatus.FNT || slot.hp() <= 0) {
            return PokemonStatus.FNT;
        }
        return slot.status();
    }

    private String statusLabel(PokemonStatus status) {
        return switch (status) {
            case PSN -> "PSN";
            case BRN -> "BRN";
            case PAR -> "PAR";
            case SLP -> "SLP";
            case FRZ -> "FRZ";
            case TOX -> "TOX";
            case FNT -> "FNT";
            default -> null;
        };
    }

    private int statusTextColor(PokemonStatus status) {
        return switch (status) {
            case PAR -> 0xFF2A2410;
            default -> 0xFFFFFFFF;
        };
    }

    private String genderMarker(String value) {
        if (value == null || value.isBlank()) return "";
        return switch (value.toUpperCase()) {
            case "M" -> "♂";
            case "F" -> "♀";
            default -> "";
        };
    }

    private String trimName(String value) {
        if (value == null || value.isBlank()) return "---";
        return value.length() > 7 ? value.substring(0, 7) : value;
    }

    private String speciesAbbrev(String displayName, String species) {
        String source = (displayName != null && !displayName.isBlank()) ? displayName : species;
        if (source == null || source.isBlank()) return "?";
        return source.length() <= 2 ? source : source.substring(0, 2);
    }

    private int speciesColor(String species) {
        if (species == null) return 0xFF596D7A;
        int h = Math.abs(species.hashCode());
        int r = 80 + (h & 0x5F);
        int g = 80 + ((h >> 7) & 0x5F);
        int b = 80 + ((h >> 14) & 0x5F);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
