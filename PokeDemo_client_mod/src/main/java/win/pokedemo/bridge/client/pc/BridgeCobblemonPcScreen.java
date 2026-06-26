package win.pokedemo.bridge.client.pc;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import win.pokedemo.bridge.client.net.BridgePayloads;
import win.pokedemo.bridge.client.render.PortraitRenderUtil;
import win.pokedemo.bridge.common.PokemonStatus;

import java.util.ArrayList;
import java.util.List;

public final class BridgeCobblemonPcScreen extends Screen {
    private static final Identifier TEX_BASE = Identifier.of("cobblemon", "textures/gui/pc/pc_base.png");
    private static final Identifier TEX_GRID = Identifier.of("cobblemon", "textures/gui/pc/pc_screen_grid.png");
    private static final Identifier TEX_OVERLAY = Identifier.of("cobblemon", "textures/gui/pc/pc_screen_overlay.png");
    private static final Identifier TEX_ARROW_PREV = Identifier.of("cobblemon", "textures/gui/pc/pc_arrow_previous.png");
    private static final Identifier TEX_ARROW_NEXT = Identifier.of("cobblemon", "textures/gui/pc/pc_arrow_next.png");
    private static final Identifier TEX_RELEASE = Identifier.of("cobblemon", "textures/gui/pc/pc_release_button.png");
    private static final Identifier TEX_RELEASE_CONFIRM = Identifier.of("cobblemon", "textures/gui/pc/pc_release_button_confirm.png");
    private static final Identifier TEX_PORTRAIT_BG = Identifier.of("cobblemon", "textures/gui/pc/portrait_background.png");
    private static final Identifier TEX_GENDER_MALE = Identifier.of("cobblemon", "textures/gui/pc/gender_icon_male.png");
    private static final Identifier TEX_GENDER_FEMALE = Identifier.of("cobblemon", "textures/gui/pc/gender_icon_female.png");

    private static final int BASE_W = 349;
    private static final int BASE_H = 205;
    private static final int GRID_W = 160;
    private static final int GRID_H = 133;
    private static final int OVERLAY_W = 174;
    private static final int OVERLAY_H = 155;

    private final List<SlotRect> slotRects = new ArrayList<>();
    private Rect prevRect;
    private Rect nextRect;
    private Rect releaseRect;
    private BridgePcViewState state;

    public BridgeCobblemonPcScreen() {
        super(Text.literal("PokeDemo PC"));
    }

    @Override
    protected void init() {
        rebuildLayout();
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void tick() {
        this.state = BridgePcStateStore.current();
        if (this.state == null || !this.state.active()) {
            close();
        }
    }

    private int panelScale() {
        return Math.max(1, Math.min((this.width - 16) / BASE_W, (this.height - 16) / BASE_H));
    }

    private void rebuildLayout() {
        slotRects.clear();
        int scale = panelScale();
        int baseW = BASE_W * scale;
        int baseH = BASE_H * scale;
        int left = (this.width - baseW) / 2;
        int top = (this.height - baseH) / 2;
        int gridX = left + 114 * scale;
        int gridY = top + 24 * scale;
        int cell = 17 * scale;
        int gap = 1 * scale;
        for (int row = 0; row < 5; row++) {
            for (int col = 0; col < 9; col++) {
                int idx = row * 9 + col;
                int x = gridX + col * (cell + gap);
                int y = gridY + row * (cell + gap);
                slotRects.add(new SlotRect(idx, x, y, cell, cell));
            }
        }
        prevRect = new Rect(left + 8 * scale, top + 162 * scale, 26 * scale, 30 * scale);
        nextRect = new Rect(left + 313 * scale, top + 162 * scale, 26 * scale, 30 * scale);
        releaseRect = new Rect(left + 138 * scale, top + 160 * scale, 72 * scale, 36 * scale);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {}

    @Override
    public void render(DrawContext dc, int mouseX, int mouseY, float delta) {
        this.state = BridgePcStateStore.current();
        if (this.state == null) this.state = BridgePcViewState.inactive();
        rebuildLayout();
        int scale = panelScale();
        int baseW = BASE_W * scale;
        int baseH = BASE_H * scale;
        int left = (this.width - baseW) / 2;
        int top = (this.height - baseH) / 2;

        drawTex(dc, TEX_BASE, left, top, baseW, baseH, BASE_W, BASE_H);
        drawTex(dc, TEX_GRID, left + 114 * scale, top + 24 * scale, GRID_W * scale, GRID_H * scale, GRID_W, GRID_H);
        drawTex(dc, TEX_OVERLAY, left + 107 * scale, top + 17 * scale, OVERLAY_W * scale, OVERLAY_H * scale, OVERLAY_W, OVERLAY_H);
        drawTex(dc, TEX_ARROW_PREV, prevRect.x + (prevRect.w - 14 * scale) / 2, prevRect.y + (prevRect.h - 28 * scale) / 2, 14 * scale, 28 * scale, 14, 28);
        drawTex(dc, TEX_ARROW_NEXT, nextRect.x + (nextRect.w - 14 * scale) / 2, nextRect.y + (nextRect.h - 28 * scale) / 2, 14 * scale, 28 * scale, 14, 28);
        boolean confirm = state.pendingReleaseIndex() >= 0;
        int relW = (confirm ? 30 : 58) * scale;
        int relH = (confirm ? 26 : 32) * scale;
        drawTex(dc, confirm ? TEX_RELEASE_CONFIRM : TEX_RELEASE, releaseRect.x + (releaseRect.w - relW) / 2, releaseRect.y + (releaseRect.h - relH) / 2, relW, relH, confirm ? 30 : 58, confirm ? 26 : 32);

        String pageLabel = "BOX " + (state.page() + 1);
        dc.drawText(this.textRenderer, pageLabel, left + 19 * scale, top + 16 * scale, 0xFFEEF3F7, false);
        dc.drawText(this.textRenderer, tr("已存放: ", "Stored: ") + state.totalCount(), left + 23 * scale, top + 165 * scale, 0xFFEEF3F7, false);

        List<BridgePcSlotState> slots = state.slots();
        for (int i = 0; i < slotRects.size(); i++) {
            SlotRect rect = slotRects.get(i);
            BridgePcSlotState slot = i < slots.size() ? slots.get(i) : BridgePcSlotState.empty(i);
            drawPcSlot(dc, rect, slot, mouseX, mouseY);
        }

        super.render(dc, mouseX, mouseY, delta);

        for (int i = 0; i < slotRects.size(); i++) {
            SlotRect rect = slotRects.get(i);
            if (!rect.contains(mouseX, mouseY)) continue;
            BridgePcSlotState slot = i < slots.size() ? slots.get(i) : BridgePcSlotState.empty(i);
            if (slot.occupied()) renderTooltip(dc, slot, mouseX, mouseY);
            break;
        }
    }

    private void drawPcSlot(DrawContext dc, SlotRect rect, BridgePcSlotState slot, int mouseX, int mouseY) {
        dc.fill(rect.x, rect.y, rect.x + rect.w, rect.y + rect.h, rect.contains(mouseX, mouseY) ? 0xCC1A212E : 0xA0111620);
        if (!slot.occupied()) return;
        drawTex(dc, TEX_PORTRAIT_BG, rect.x, rect.y, rect.w, rect.h, 21, 21);
        int portraitPad = Math.max(1, rect.w / 16);
        int px = rect.x + portraitPad;
        int py = rect.y + portraitPad;
        int pw = rect.w - portraitPad * 2;
        int ph = rect.h - portraitPad * 2;
        if (!drawPortraitFit(dc, slot.species(), px, py, pw, ph)) {
            PortraitRenderUtil.drawFallbackBadge(dc, this.textRenderer, slot.species(), px, py, pw, ph);
        }
        if (slot.locked()) {
            dc.fill(rect.x, rect.y, rect.x + rect.w, rect.y + 4, 0xCCB33939);
        } else if (slot.egg()) {
            dc.fill(rect.x, rect.y, rect.x + rect.w, rect.y + 4, 0xCC66A5C7);
        }
        if (slot.status() != PokemonStatus.NONE) {
            int c = switch (slot.status()) {
                case BRN -> 0xFFD6712E;
                case PSN, TOX -> 0xFFC25AFF;
                case PAR -> 0xFFE7D44B;
                case SLP -> 0xFF6F8CB6;
                case FRZ -> 0xFF6FD0F1;
                default -> 0xFF7A7A7A;
            };
            dc.fill(rect.x + rect.w - 5, rect.y, rect.x + rect.w, rect.y + rect.h, c);
        }
    }

    private boolean drawPortraitFit(DrawContext dc, String species, int x, int y, int w, int h) {
        var portrait = win.pokedemo.bridge.client.hud.HudAssetResolver.findSpeciesPortraitSlice(species);
        if (portrait == null) return false;
        float srcW = portrait.width();
        float srcH = portrait.height();
        float scale = Math.min((float) w / srcW, (float) h / srcH);
        int drawW = Math.max(1, Math.round(srcW * scale));
        int drawH = Math.max(1, Math.round(srcH * scale));
        int drawX = x + (w - drawW) / 2;
        int drawY = y + (h - drawH) / 2;
        dc.drawTexture(RenderPipelines.GUI_TEXTURED, portrait.texture(), drawX, drawY,
                (float) portrait.u(), (float) portrait.v(), drawW, drawH,
                portrait.width(), portrait.height(), portrait.textureWidth(), portrait.textureHeight());
        return true;
    }

    private void renderTooltip(DrawContext dc, BridgePcSlotState slot, int mouseX, int mouseY) {
        List<Text> lines = new ArrayList<>();
        String gender = switch (slot.gender()) { case "M" -> " ♂"; case "F" -> " ♀"; default -> ""; };
        lines.add(Text.literal(slot.displayName() + gender + " Lv." + slot.level()).formatted(Formatting.AQUA));
        if (slot.egg()) lines.add(Text.literal("蛋 / Egg").formatted(Formatting.GRAY));
        else lines.add(Text.literal("HP " + slot.hp() + "/" + slot.maxHp()).formatted(Formatting.WHITE));
        if (slot.status() != PokemonStatus.NONE) lines.add(Text.literal("状态: " + slot.status().name()).formatted(Formatting.GOLD));
        if (slot.locked()) lines.add(Text.literal("已锁定" + (slot.lockReason().isBlank() ? "" : " (" + slot.lockReason() + ")")).formatted(Formatting.RED));
        lines.add(Text.literal("左键取出 / 右键放生").formatted(Formatting.YELLOW));
        lines.add(Text.literal("Shift+点击 查看详情").formatted(Formatting.YELLOW));
        dc.drawTooltip(this.textRenderer, lines, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubleClick) {
        int mouseX = (int) click.x();
        int mouseY = (int) click.y();
        int button = click.button();
        if (prevRect.contains(mouseX, mouseY) && state.hasPrev()) { sendAction("prev"); return true; }
        if (nextRect.contains(mouseX, mouseY) && state.hasNext()) { sendAction("next"); return true; }
        if (releaseRect.contains(mouseX, mouseY)) {
            if (state.pendingReleaseIndex() >= 0) sendAction("release_confirm");
            return true;
        }
        for (SlotRect rect : slotRects) {
            if (!rect.contains(mouseX, mouseY)) continue;
            BridgePcSlotState slot = rect.index < state.slots().size() ? state.slots().get(rect.index) : BridgePcSlotState.empty(rect.index);
            if (!slot.occupied()) return true;
            if (isShiftHeld()) {
                sendAction("summary:" + rect.index);
                BridgePcStateStore.clear();
                return true;
            }
            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                sendAction("release:" + rect.index);
                return true;
            }
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                sendAction("withdraw:" + rect.index);
                return true;
            }
            return true;
        }
        return super.mouseClicked(click, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int keyCode = input.key();
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            BridgePcStateStore.clear();
            close();
            return true;
        }
        return super.keyPressed(input);
    }

    private boolean isShiftHeld() {
        if (this.client == null) return false;
        long handle = this.client.getWindow().getHandle();
        return GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
    }

    private boolean isZh() {
        try {
            var mc = MinecraftClient.getInstance();
            String code = mc != null && mc.getLanguageManager() != null ? mc.getLanguageManager().getLanguage() : "en_us";
            return code != null && code.toLowerCase().startsWith("zh");
        } catch (Throwable ignored) {
            return true;
        }
    }

    private String tr(String zh, String en) {
        return isZh() ? zh : en;
    }

    private String localizedStatus(PokemonStatus status) {
        return switch (status) {
            case BRN -> tr("灼伤", "Burn");
            case PSN -> tr("中毒", "Poison");
            case TOX -> tr("剧毒", "Toxic");
            case PAR -> tr("麻痹", "Paralysis");
            case SLP -> tr("睡眠", "Sleep");
            case FRZ -> tr("冰冻", "Freeze");
            default -> tr("无", "None");
        };
    }

    private String localizedSpeciesName(BridgePcSlotState slot) {
        String name = slot.displayName();
        if (name != null && !name.isBlank() && !looksEnglishSpecies(name)) return name;
        try {
            String key = "cobblemon.species." + slot.species().toLowerCase() + ".name";
            return net.minecraft.client.resource.language.I18n.hasTranslation(key)
                    ? net.minecraft.client.resource.language.I18n.translate(key)
                    : (name == null || name.isBlank() ? slot.species() : name);
        } catch (Throwable ignored) {
            return name == null || name.isBlank() ? slot.species() : name;
        }
    }

    private boolean looksEnglishSpecies(String s) {
        if (s == null) return true;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 0x4E00 && c <= 0x9FFF) return false;
        }
        return true;
    }

    private void sendAction(String action) {
        try {
            var mc = MinecraftClient.getInstance();
            if (mc != null && mc.getSoundManager() != null) {
                mc.getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.ambient(net.minecraft.sound.SoundEvent.of(net.minecraft.util.Identifier.of("cobblemon", action != null && action.startsWith("release") ? "pc.release" : "pc.click")), 1.0f, 1.0f));
            }
        } catch (Throwable ignored) {}
        ClientPlayNetworking.send(BridgePayloads.RawBridgePayload.pcAction(action));
    }

    private void drawTex(DrawContext dc, Identifier tex, int x, int y, int w, int h, int tw, int th) {
        dc.drawTexture(RenderPipelines.GUI_TEXTURED, tex, x, y, 0f, 0f, w, h, tw, th);
    }

    private record Rect(int x, int y, int w, int h) { boolean contains(int mx, int my) { return mx >= x && my >= y && mx < x + w && my < y + h; } }
    private record SlotRect(int index, int x, int y, int w, int h) { boolean contains(int mx, int my) { return mx >= x && my >= y && mx < x + w && my < y + h; } }
}
