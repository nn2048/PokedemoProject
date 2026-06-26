package win.pokedemo.bridge.client.battle;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.input.KeyInput;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import win.pokedemo.bridge.client.PokeDemoBridgeClient;
import win.pokedemo.bridge.client.net.BridgePayloads;
import win.pokedemo.bridge.client.render.PortraitRenderUtil;
import win.pokedemo.bridge.common.PartySlotState;
import win.pokedemo.bridge.common.PokemonStatus;

import java.util.ArrayList;
import java.util.List;

public final class BridgeCobblemonBattleScreen extends Screen {
    private static final Identifier TEX_INFO_SELF = Identifier.of("cobblemon", "textures/gui/battle/battle_info_base.png");
    private static final Identifier TEX_INFO_FOE = Identifier.of("cobblemon", "textures/gui/battle/battle_info_base_flipped.png");
    private static final Identifier TEX_LOG = Identifier.of("cobblemon", "textures/gui/battle/battle_log.png");
    private static final Identifier TEX_LOG_EXPANDED = Identifier.of("cobblemon", "textures/gui/battle/battle_log_expanded.png");
    private static final Identifier TEX_UNDERLAY = Identifier.of("cobblemon", "textures/gui/battle/selection_underlay.png");
    private static final Identifier TEX_FIGHT = Identifier.of("cobblemon", "textures/gui/battle/battle_menu_fight.png");
    private static final Identifier TEX_SWITCH = Identifier.of("cobblemon", "textures/gui/battle/battle_menu_switch.png");
    private static final Identifier TEX_BAG = Identifier.of("cobblemon", "textures/gui/battle/battle_menu_bag.png");
    private static final Identifier TEX_RUN = Identifier.of("cobblemon", "textures/gui/battle/battle_menu_run.png");
    private static final Identifier TEX_FORFEIT = Identifier.of("cobblemon", "textures/gui/battle/battle_menu_forfeit.png");
    private static final Identifier TEX_MOVE = Identifier.of("cobblemon", "textures/gui/battle/battle_move.png");
    private static final Identifier TEX_MOVE_OVERLAY = Identifier.of("cobblemon", "textures/gui/battle/battle_move_overlay.png");
    private static final Identifier TEX_PARTY = Identifier.of("cobblemon", "textures/gui/battle/party_select.png");
    private static final Identifier TEX_PARTY_DISABLED = Identifier.of("cobblemon", "textures/gui/battle/party_select_disabled.png");
    private static final Identifier TEX_BACK = Identifier.of("cobblemon", "textures/gui/battle/battle_back.png");

    private static final int INFO_TEX_W = 140;
    private static final int INFO_TEX_H = 40;
    private static final int LOG_TEX_W = 169;
    private static final int LOG_TEX_H = 55;
    private static final int LOG_EXPANDED_TEX_H = 89;
    private static final int MENU_TEX_W = 90;
    private static final int MENU_FRAME_H = 26;
    private static final int MOVE_TEX_W = 92;
    private static final int MOVE_FRAME_H = 24;
    private static final int PARTY_TEX_W = 94;
    private static final int PARTY_FRAME_H = 29;
    private static final int BACK_TEX_W = 58;
    private static final int BACK_FRAME_H = 34;
    private static final int UNDERLAY_TEX_W = 160;
    private static final int UNDERLAY_TEX_H = 76;

    private enum Page { ROOT, MOVES, PARTY }

    private Page page = Page.ROOT;
    private BridgeBattleViewState state;
    private final List<ButtonWidget> actionButtons = new ArrayList<>();
    private boolean expandedLog = false;
    private boolean awaitingServerAck = false;
    private String awaitingStateSignature = null;
    private String lastObservedStateSignature = null;

    public BridgeCobblemonBattleScreen(BridgeBattleViewState state) {
        super(Text.literal("PokeDemo Battle"));
        this.state = state;
    }

    @Override
    protected void init() {
        lastObservedStateSignature = stateSignature(state == null ? BridgeBattleStateStore.currentOrDemo() : state);
        rebuildButtons();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void tick() {
        this.state = BridgeBattleStateStore.currentOrDemo();
        String currentSignature = stateSignature(this.state);
        boolean stateChanged = lastObservedStateSignature == null || !lastObservedStateSignature.equals(currentSignature);
        if (awaitingServerAck && stateChanged) {
            awaitingServerAck = false;
            awaitingStateSignature = null;
        }
        if (stateChanged) {
            lastObservedStateSignature = currentSignature;
            if (state.isForcedSwitchRequest() && !awaitingServerAck) {
                page = Page.PARTY;
            } else if (!state.isForcedSwitchRequest() && page != Page.ROOT && !awaitingServerAck) {
                page = Page.ROOT;
            }
            rebuildButtons();
        }
        if (state.isForcedSwitchRequest() && page != Page.PARTY && !awaitingServerAck) {
            page = Page.PARTY;
            rebuildButtons();
        }
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Intentionally empty to match Cobblemon-like battle GUI behavior and avoid vanilla blur/dimming.
    }

    private void rebuildButtons() {
        clearChildren();
        actionButtons.clear();
        if (state == null) state = BridgeBattleStateStore.currentOrDemo();

        if (state.isForcedSwitchRequest() && !awaitingServerAck) {
            page = Page.PARTY;
        }

        if (page == Page.ROOT) {
            int rootBtnW = 90;
            int rootBtnH = 26;
            int rootGapX = 3;
            int rootGapY = 5;
            int rootMenuW = rootBtnW * 2 + rootGapX;
            int rootMenuH = rootBtnH * 2 + rootGapY;
            int rootX = this.width - 24 - rootMenuW;
            int rootY = this.height - 18 - rootMenuH;

            addActionButton(rootX, rootY, rootBtnW, rootBtnH, !awaitingServerAck && state.canFight(), () -> { page = Page.MOVES; clickSound(); rebuildButtons(); });
            addActionButton(rootX + rootBtnW + rootGapX, rootY, rootBtnW, rootBtnH, !awaitingServerAck && state.canSwitch(), () -> { page = Page.PARTY; clickSound(); rebuildButtons(); });
            addActionButton(rootX, rootY + rootBtnH + rootGapY, rootBtnW, rootBtnH, !awaitingServerAck && state.canBag(), () -> sendBattleAction(state.pvp() ? "bag_disabled" : "bag"));
            boolean lastEnabled = !awaitingServerAck && (state.pvp() ? state.canForfeit() : state.canRun());
            addActionButton(rootX + rootBtnW + rootGapX, rootY + rootBtnH + rootGapY, rootBtnW, rootBtnH, lastEnabled,
                    () -> sendBattleAction(state.pvp() ? "forfeit" : "run"));
            return;
        }

        if (page == Page.MOVES) {
            int w = 142;
            int h = 38;
            int gapX = 6;
            int gapY = 6;
            int gridW = w * 2 + gapX;
            int x = this.width - 18 - gridW;
            int y = this.height - 18 - (h * 2 + gapY + 26);
            for (int i = 0; i < 4; i++) {
                int col = i % 2;
                int row = i / 2;
                int bx = x + col * (w + gapX);
                int by = y + row * (h + gapY);
                final int idx = i;
                boolean enabled = !awaitingServerAck && i < state.moves().size() && !state.moves().get(i).disabled() && state.canFight();
                addActionButton(bx, by, w, h, enabled, () -> sendBattleAction("move:" + idx));
            }
            addActionButton(x, y + 2 * (h + gapY) + 2, 58, 22, true, () -> { page = Page.ROOT; clickSound(); rebuildButtons(); });
            return;
        }

        int w = 142;
        int h = 40;
        int gap = 6;
        int gridW = w * 2 + gap;
        int x = this.width - 18 - gridW;
        int y = this.height - 18 - (h * 3 + gap * 2 + 24 + 6);
        for (int i = 0; i < 6; i++) {
            int col = i % 2;
            int row = i / 2;
            int bx = x + col * (w + gap);
            int by = y + row * (h + gap);
            final int idx = i;
            PartySlotState slot = i < state.party().size() ? state.party().get(i) : null;
            boolean enabled = !awaitingServerAck && slot != null && slot.occupied() && slot.hp() > 0 && !slot.active() && state.canSwitch();
            addActionButton(bx, by, w, h, enabled, () -> sendBattleAction("switch:" + idx));
        }
        addActionButton(x, y + 3 * (h + gap) + 2, 58, 22, !state.isForcedSwitchRequest(), () -> { page = Page.ROOT; clickSound(); rebuildButtons(); });
    }

    private void addActionButton(int x, int y, int w, int h, boolean active, Runnable action) {
        ButtonWidget button = ButtonWidget.builder(Text.empty(), b -> {
            if (active) action.run();
            else clickSound();
        }).dimensions(x, y, w, h).build();
        button.active = active;
        actionButtons.add(this.addDrawableChild(button));
    }

    @Override
    public void render(DrawContext dc, int mouseX, int mouseY, float delta) {
        if (state == null) state = BridgeBattleStateStore.currentOrDemo();
        super.render(dc, mouseX, mouseY, delta);

        int infoW = 188;
        int infoH = 54;
        drawBattleInfo(dc, 16, 8, true, state.self(), infoW, infoH);
        drawBattleInfo(dc, this.width - 16 - infoW, 8, false, state.foe(), infoW, infoH);

        int logW = 246;
        int logH = expandedLog ? 124 : 84;
        drawLog(dc, 16, this.height - 16 - logH, logW, logH);
        drawStatusHint(dc, 16, this.height - 16 - logH - 18, logW);

        drawSelectionUnderlay(dc);

        if (page == Page.ROOT) drawRootButtons(dc);
        else if (page == Page.MOVES) drawMoveButtons(dc);
        else drawPartyButtons(dc);
    }

    private void drawSelectionUnderlay(DrawContext dc) {
        int underlayW = 320;
        int underlayH = 124;
        int x = this.width - 18 - underlayW;
        int y = this.height - 18 - underlayH;
        drawTexture(dc, TEX_UNDERLAY, x, y, underlayW, underlayH, UNDERLAY_TEX_W, UNDERLAY_TEX_H);
    }

    private void drawStatusHint(DrawContext dc, int x, int y, int w) {
        String msg = awaitingServerAck ? "请稍等...正在提交你的选择" : (state.statusLine() == null || state.statusLine().isBlank()
                ? (state.isForcedSwitchRequest() ? "请选择下一只宝可梦。" : (state.isWaiting() ? "正在结算回合..." : "请选择一个动作。"))
                : state.statusLine());
        dc.fill(x, y, x + w, y + 12, 0x66000000);
        dc.drawText(this.textRenderer, msg, x + 4, y + 2, 0xFFFFFFFF, false);
    }

    private void drawBattleInfo(DrawContext dc, int x, int y, boolean self, BridgeBattleViewState.Battler battler, int w, int h) {
        drawTexture(dc, self ? TEX_INFO_SELF : TEX_INFO_FOE, x, y, w, h, INFO_TEX_W, INFO_TEX_H);

        int portraitSize = 34;
        int portraitX = self ? x + 10 : x + w - portraitSize - 10;
        int portraitY = y + 7;
        dc.fill(portraitX - 1, portraitY - 1, portraitX + portraitSize + 1, portraitY + portraitSize + 1, 0xFF80858E);
        dc.fill(portraitX, portraitY, portraitX + portraitSize, portraitY + portraitSize, 0xFF1C2026);
        if (!drawPortraitFit(dc, battler.species(), portraitX, portraitY, portraitSize, portraitSize)) {
            PortraitRenderUtil.drawFallbackBadge(dc, this.textRenderer, battler.species(), portraitX, portraitY, portraitSize, portraitSize);
        }

        int textX = self ? x + 47 : x + 10;
        int textRight = self ? x + w - 10 : x + w - 47;
        int nameY = y + 8;
        String displayName = battler.name();
        dc.drawText(this.textRenderer, displayName, textX, nameY, 0xFFFFFFFF, false);
        drawGenderSymbol(dc, battler.gender(), textX + this.textRenderer.getWidth(displayName) + 3, nameY);
        String level = "Lv." + battler.level();
        dc.drawText(this.textRenderer, level, textRight - this.textRenderer.getWidth(level), nameY, 0xFFFFFFFF, false);

        int barX = self ? x + 47 : x + 10;
        int barEnd = self ? x + w - 12 : x + w - 47;
        int barY = y + 30;
        int barW = Math.max(24, barEnd - barX);
        dc.fill(barX, barY, barX + barW, barY + 8, 0xFF1B1B1B);
        int fill = Math.max(1, Math.round(barW * (Math.max(0, battler.hp()) / (float) Math.max(1, battler.maxHp()))));
        dc.fill(barX, barY, barX + fill, barY + 8, healthColor(battler.hp(), battler.maxHp()));

        String hp = battler.hp() + "/" + battler.maxHp();
        dc.drawText(this.textRenderer, hp, textRight - this.textRenderer.getWidth(hp), y + 40, 0xFFFFFFFF, false);
        if (battler.status() != null && battler.status() != PokemonStatus.NONE) {
            dc.drawText(this.textRenderer, battler.status().name(), barX, y + 40, 0xFFE6E6E6, false);
        }
    }

    private boolean drawPortraitFit(DrawContext dc, String species, int x, int y, int boxW, int boxH) {
        var portrait = win.pokedemo.bridge.client.hud.HudAssetResolver.findSpeciesPortraitSlice(species);
        if (portrait == null) return false;
        float srcW = portrait.width();
        float srcH = portrait.height();
        float scale = Math.min(boxW / srcW, boxH / srcH);
        int drawW = Math.max(1, Math.round(srcW * scale));
        int drawH = Math.max(1, Math.round(srcH * scale));
        int drawX = x + (boxW - drawW) / 2;
        int drawY = y + (boxH - drawH) / 2;
        dc.drawTexture(RenderPipelines.GUI_TEXTURED, portrait.texture(), drawX, drawY,
                (float) portrait.u(), (float) portrait.v(), drawW, drawH,
                portrait.width(), portrait.height(), portrait.textureWidth(), portrait.textureHeight());
        return true;
    }

    private void drawGenderSymbol(DrawContext dc, String gender, int x, int y) {
        if (gender == null) return;
        String g = gender.trim().toUpperCase(java.util.Locale.ROOT);
        if ("M".equals(g) || "MALE".equals(g) || "♂".equals(g)) {
            dc.drawText(this.textRenderer, "♂", x, y, 0xFF77B8FF, false);
        } else if ("F".equals(g) || "FEMALE".equals(g) || "♀".equals(g)) {
            dc.drawText(this.textRenderer, "♀", x, y, 0xFFFF8FBE, false);
        }
    }

    private int healthColor(int hp, int maxHp) {
        float ratio = maxHp <= 0 ? 0F : (hp / (float) maxHp);
        if (ratio <= 0.2F) return 0xFFE14B4B;
        if (ratio <= 0.5F) return 0xFFF3C74A;
        return 0xFF2FD45A;
    }

    private void drawLog(DrawContext dc, int x, int y, int w, int h) {
        drawTexture(dc, expandedLog ? TEX_LOG_EXPANDED : TEX_LOG, x, y, w, h, LOG_TEX_W, expandedLog ? LOG_EXPANDED_TEX_H : LOG_TEX_H);
        int ty = y + 14;
        List<String> lines = state.logLines();
        int start = Math.max(0, lines.size() - (expandedLog ? 8 : 5));
        for (int i = start; i < lines.size(); i++) {
            String line = lines.get(i);
            dc.drawText(this.textRenderer, line, x + 12, ty, line.startsWith("现在为") ? 0xFF4BE4FF : 0xFFFFFFFF, false);
            ty += 12;
            if (ty > y + h - 12) break;
        }
    }

    private void drawRootButtons(DrawContext dc) {
        drawMenuButton(dc, 0, TEX_FIGHT, Text.literal("战斗"), !awaitingServerAck && state.canFight());
        drawMenuButton(dc, 1, TEX_SWITCH, Text.literal(state.isForcedSwitchRequest() ? "换出" : "宝可梦"), !awaitingServerAck && state.canSwitch());
        drawMenuButton(dc, 2, TEX_BAG, Text.literal("捕捉"), !awaitingServerAck && state.canBag());
        drawMenuButton(dc, 3, state.pvp() ? TEX_FORFEIT : TEX_RUN, Text.literal(state.pvp() ? "认输" : "逃走"), !awaitingServerAck && (state.pvp() ? state.canForfeit() : state.canRun()));
    }

    private void drawMoveButtons(DrawContext dc) {
        for (int i = 0; i < 4; i++) {
            ButtonWidget btn = actionButtons.get(i);
            boolean enabled = btn.active;
            boolean hover = enabled && btn.isHovered();
            BridgeBattleViewState.Move move = i < state.moves().size() ? state.moves().get(i) : new BridgeBattleViewState.Move("—", "", "", "", "", 0, 100, 0, "", true, "");
            int typeTint = typeTint(move.type());
            drawTextureRegion(dc, TEX_MOVE, btn.getX(), btn.getY(), btn.getWidth(), btn.getHeight(), 0, hover ? MOVE_FRAME_H : 0, MOVE_TEX_W, MOVE_FRAME_H, MOVE_TEX_W, MOVE_FRAME_H * 2);
            dc.fill(btn.getX() + 2, btn.getY() + 2, btn.getX() + btn.getWidth() - 2, btn.getY() + btn.getHeight() - 2, (typeTint & 0x00FFFFFF) | 0x66000000);
            drawTexture(dc, TEX_MOVE_OVERLAY, btn.getX(), btn.getY(), btn.getWidth(), btn.getHeight(), MOVE_TEX_W, MOVE_FRAME_H);
            int titleColor = enabled ? 0xFFFFFFFF : 0xFF9A9A9A;
            int detailColor = enabled ? 0xFFDADADA : 0xFF7A7A7A;
            dc.drawText(this.textRenderer, move.name(), btn.getX() + 10, btn.getY() + 6, titleColor, false);
            String detail = move.disabled() && move.disabledReason() != null && !move.disabledReason().isBlank() ? move.disabledReason() : move.detail();
            dc.drawText(this.textRenderer, detail, btn.getX() + 10, btn.getY() + 18, detailColor, false);
            dc.drawText(this.textRenderer, move.pp(), btn.getX() + btn.getWidth() - 10 - this.textRenderer.getWidth(move.pp()), btn.getY() + 18, titleColor, false);
            if (!enabled) dc.fill(btn.getX(), btn.getY(), btn.getX() + btn.getWidth(), btn.getY() + btn.getHeight(), 0x66000000);
        }
        drawBackButton(dc, actionButtons.get(4), true);
        drawMoveTooltip(dc);
    }


    private void drawMoveTooltip(DrawContext dc) {
        for (int i = 0; i < Math.min(4, actionButtons.size()); i++) {
            ButtonWidget btn = actionButtons.get(i);
            if (!btn.isHovered() || i >= state.moves().size()) continue;
            BridgeBattleViewState.Move move = state.moves().get(i);
            int tooltipW = 212;
            int tooltipH = 96;
            int x = Math.max(10, btn.getX() - tooltipW - 10);
            int y = Math.max(12, btn.getY() - 10);
            int tint = typeTint(move.type());
            dc.fill(x, y, x + tooltipW, y + tooltipH, 0xE61A1A1A);
            dc.fill(x + 2, y + 2, x + tooltipW - 2, y + 24, (tint & 0x00FFFFFF) | 0xCC000000);
            dc.drawText(this.textRenderer, move.name(), x + 8, y + 8, 0xFFFFFFFF, false);
            String tag = move.detail().isBlank() ? (move.type() + " / " + move.category()) : move.detail();
            dc.drawText(this.textRenderer, tag, x + 8, y + 28, 0xFFE0E0E0, false);
            String pwr = move.power() <= 0 ? "—" : String.valueOf(move.power());
            String acc = move.accuracy() <= 0 ? "—" : (move.accuracy() + "%");
            String pri = move.priority() == 0 ? "0" : ((move.priority() > 0 ? "+" : "") + move.priority());
            dc.drawText(this.textRenderer, "威力 " + pwr + "   命中 " + acc + "   优先度 " + pri, x + 8, y + 42, 0xFFFFFFFF, false);
            dc.drawText(this.textRenderer, "PP " + move.pp(), x + 8, y + 56, 0xFFE0E0E0, false);
            String desc = move.description() == null || move.description().isBlank() ? "暂无说明。" : move.description();
            int descY = y + 72;
            for (var orderLine : this.textRenderer.wrapLines(Text.literal(desc), tooltipW - 16)) {
                if (descY > y + tooltipH - 12) break;
                dc.drawText(this.textRenderer, orderLine, x + 8, descY, 0xFFD8D8D8, false);
                descY += 10;
            }
            break;
        }
    }

    private int typeTint(String type) {
        if (type == null) return 0xFF909090;
        return switch (type.toLowerCase(java.util.Locale.ROOT)) {
            case "normal" -> 0xFFA8A77A;
            case "fire" -> 0xFFEE8130;
            case "water" -> 0xFF6390F0;
            case "electric" -> 0xFFF7D02C;
            case "grass" -> 0xFF7AC74C;
            case "ice" -> 0xFF96D9D6;
            case "fighting" -> 0xFFC22E28;
            case "poison" -> 0xFFA33EA1;
            case "ground" -> 0xFFE2BF65;
            case "flying" -> 0xFFA98FF3;
            case "psychic" -> 0xFFF95587;
            case "bug" -> 0xFFA6B91A;
            case "rock" -> 0xFFB6A136;
            case "ghost" -> 0xFF735797;
            case "dragon" -> 0xFF6F35FC;
            case "dark" -> 0xFF705746;
            case "steel" -> 0xFFB7B7CE;
            case "fairy" -> 0xFFD685AD;
            default -> 0xFF909090;
        };
    }

    private void drawPartyButtons(DrawContext dc) {
        for (int i = 0; i < 6; i++) {
            ButtonWidget btn = actionButtons.get(i);
            PartySlotState slot = i < state.party().size() ? state.party().get(i) : null;
            boolean occupied = slot != null && slot.occupied();
            boolean enabled = btn.active;
            boolean hover = enabled && btn.isHovered();
            drawTextureRegion(dc, occupied ? TEX_PARTY : TEX_PARTY_DISABLED, btn.getX(), btn.getY(), btn.getWidth(), btn.getHeight(), 0, hover ? PARTY_FRAME_H : 0, PARTY_TEX_W, PARTY_FRAME_H, PARTY_TEX_W, PARTY_FRAME_H * 2);
            if (occupied) {
                int color = enabled ? 0xFFFFFFFF : 0xFFB0B0B0;
                dc.drawText(this.textRenderer, slot.displayName(), btn.getX() + 10, btn.getY() + 7, color, false);
                String sub = slot.active() ? "已在场" : (slot.hp() <= 0 ? "已昏厥" : ("Lv." + slot.level() + "  " + slot.hp() + "/" + slot.maxHp()));
                dc.drawText(this.textRenderer, sub, btn.getX() + 10, btn.getY() + 21, enabled ? 0xFFE0E0E0 : 0xFF8A8A8A, false);
            }
            if (!enabled) dc.fill(btn.getX(), btn.getY(), btn.getX() + btn.getWidth(), btn.getY() + btn.getHeight(), 0x50000000);
        }
        drawBackButton(dc, actionButtons.get(6), !state.isForcedSwitchRequest() && !awaitingServerAck);
    }

    private void drawBackButton(DrawContext dc, ButtonWidget btn, boolean enabled) {
        drawTextureRegion(dc, TEX_BACK, btn.getX(), btn.getY(), btn.getWidth(), btn.getHeight(), 0, btn.isHovered() && enabled ? BACK_FRAME_H : 0, BACK_TEX_W, BACK_FRAME_H, BACK_TEX_W, BACK_FRAME_H * 2);
        dc.drawCenteredTextWithShadow(this.textRenderer, Text.literal(state.isForcedSwitchRequest() ? "锁定" : "返回"), btn.getX() + btn.getWidth() / 2, btn.getY() + 6, enabled ? 0xFFFFFFFF : 0xFF9A9A9A);
        if (!enabled) dc.fill(btn.getX(), btn.getY(), btn.getX() + btn.getWidth(), btn.getY() + btn.getHeight(), 0x50000000);
    }

    private void drawMenuButton(DrawContext dc, int idx, Identifier tex, Text label, boolean enabled) {
        ButtonWidget btn = actionButtons.get(idx);
        drawTextureRegion(dc, tex, btn.getX(), btn.getY(), btn.getWidth(), btn.getHeight(), 0, btn.isHovered() && enabled ? MENU_FRAME_H : 0, MENU_TEX_W, MENU_FRAME_H, MENU_TEX_W, MENU_FRAME_H * 2);
        dc.drawCenteredTextWithShadow(this.textRenderer, label, btn.getX() + btn.getWidth() / 2, btn.getY() + 9, enabled ? 0xFFFFFFFF : 0xFF9A9A9A);
        if (!enabled) dc.fill(btn.getX(), btn.getY(), btn.getX() + btn.getWidth(), btn.getY() + btn.getHeight(), 0x66000000);
    }

    private void sendBattleAction(String action) {
        if ("bag_disabled".equals(action)) {
            clickSound();
            BridgeBattleStateStore.pushLocalLog("当前战斗不能捕捉");
            return;
        }
        clickSound();
        BridgeBattleStateStore.pushLocalLog(localActionText(action));
        awaitingServerAck = true;
        awaitingStateSignature = stateSignature(state);
        page = Page.ROOT;
        rebuildButtons();
        ClientPlayNetworking.send(BridgePayloads.RawBridgePayload.battleAction(action));
    }

    private String localActionText(String action) {
        if (action.startsWith("move:")) {
            try {
                int idx = Integer.parseInt(action.substring("move:".length()));
                if (idx >= 0 && idx < state.moves().size()) return "已选择技能：" + state.moves().get(idx).name();
            } catch (Exception ignored) {}
            return "已选择技能";
        }
        if (action.startsWith("switch:")) {
            try {
                int idx = Integer.parseInt(action.substring("switch:".length()));
                if (idx >= 0 && idx < state.party().size()) return "已选择换上：" + state.party().get(idx).displayName();
            } catch (Exception ignored) {}
            return "已选择换宝可梦";
        }
        if (action.equals("bag")) return "已选择捕捉";
        if (action.equals("run") || action.equals("forfeit") || action.equals("surrender")) return state.pvp() ? "已选择认输" : "已选择逃走";
        return action;
    }


    private String stateSignature(BridgeBattleViewState s) {
        if (s == null) return "null";
        String lastLog = s.logLines().isEmpty() ? "" : s.logLines().get(s.logLines().size() - 1);
        return s.requestType() + "|" + s.statusLine() + "|" + s.self().hp() + "/" + s.self().maxHp() + "|"
                + s.foe().hp() + "/" + s.foe().maxHp() + "|" + lastLog;
    }

    private void clickSound() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 1.0f);
        }
    }

    public boolean canCloseUi() {
        return !state.isForcedSwitchRequest() && !state.isWaiting();
    }

    public void requestCloseUi() {
        if (canCloseUi()) {
            clickSound();
            PokeDemoBridgeClient.hideBattleUiByUser();
            PokeDemoBridgeClient.showBattleReopenHint();
            MinecraftClient.getInstance().setScreen(null);
        }
    }

    private void drawTexture(DrawContext dc, Identifier tex, int x, int y, int w, int h, int texW, int texH) {
        dc.drawTexture(RenderPipelines.GUI_TEXTURED, tex, x, y, 0f, 0f, w, h, texW, texH, texW, texH);
    }

    private void drawTextureRegion(DrawContext dc, Identifier tex, int x, int y, int w, int h, int u, int v, int rw, int rh, int texW, int texH) {
        dc.drawTexture(RenderPipelines.GUI_TEXTURED, tex, x, y, (float) u, (float) v, w, h, rw, rh, texW, texH);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (page != Page.ROOT && !state.isForcedSwitchRequest()) {
                page = Page.ROOT;
                rebuildButtons();
                clickSound();
            } else if (canCloseUi()) {
                requestCloseUi();
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_TAB) {
            expandedLog = !expandedLog;
            clickSound();
            return true;
        }
        if (page == Page.MOVES && key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_4) {
            int idx = key - GLFW.GLFW_KEY_1;
            if (!awaitingServerAck && idx < state.moves().size() && !state.moves().get(idx).disabled()) {
                sendBattleAction("move:" + idx);
            }
            return true;
        }
        return super.keyPressed(input);
    }
}
