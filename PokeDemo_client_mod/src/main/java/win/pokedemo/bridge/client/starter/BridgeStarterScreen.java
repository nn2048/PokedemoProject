package win.pokedemo.bridge.client.starter;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import win.pokedemo.bridge.client.net.BridgePayloads;
import win.pokedemo.bridge.client.render.PortraitRenderUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class BridgeStarterScreen extends Screen {
    private static final int STARTER_ROW_SHIFT_X = 16;
    private static final Identifier TEX_BASE = Identifier.of("cobblemon", "textures/gui/starterselection/starterselection_base.png");
    private static final Identifier TEX_UNDERLAY = Identifier.of("cobblemon", "textures/gui/starterselection/starterselection_base_underlay.png");
    private static final Identifier TEX_FRAME = Identifier.of("cobblemon", "textures/gui/starterselection/starterselection_base_frame.png");
    private static final Identifier TEX_SLOT = Identifier.of("cobblemon", "textures/gui/starterselection/starterselection_slot.png");
    private static final Identifier TEX_BUTTON = Identifier.of("cobblemon", "textures/gui/starterselection/starterselection_button.png");
    private static final Identifier TEX_EXIT = Identifier.of("cobblemon", "textures/gui/starterselection/starterselection_exit.png");
    private static final Identifier TEX_ARROW_LEFT = Identifier.of("cobblemon", "textures/gui/starterselection/starterselection_arrow_left.png");
    private static final Identifier TEX_ARROW_RIGHT = Identifier.of("cobblemon", "textures/gui/starterselection/starterselection_arrow_right.png");
    private static final Identifier TEX_TYPE_SINGLE = Identifier.of("cobblemon", "textures/gui/starterselection/starterselection_type_slot1.png");
    private static final Identifier TEX_TYPE_DOUBLE = Identifier.of("cobblemon", "textures/gui/starterselection/starterselection_type_slot2.png");
    private static final Identifier TEX_TYPES = Identifier.of("cobblemon", "textures/gui/types.png");

    private static final int LOGICAL_W = 200;
    private static final int LOGICAL_H = 175;

    private static final Map<String, Integer> TYPE_INDEX = Map.ofEntries(
            Map.entry("normal", 0), Map.entry("fire", 1), Map.entry("water", 2), Map.entry("electric", 4),
            Map.entry("grass", 3), Map.entry("ice", 5), Map.entry("fighting", 6), Map.entry("poison", 7),
            Map.entry("ground", 8), Map.entry("flying", 9), Map.entry("psychic", 10), Map.entry("bug", 11),
            Map.entry("rock", 12), Map.entry("ghost", 13), Map.entry("dragon", 14), Map.entry("dark", 15),
            Map.entry("steel", 16), Map.entry("fairy", 17)
    );

    private static final List<StarterCategory> CATEGORIES = List.of(
            new StarterCategory("GEN 1", List.of(spec("bulbasaur", "grass", "poison", "妙蛙种子"), spec("charmander", "fire", "", "小火龙"), spec("squirtle", "water", "", "杰尼龟"))),
            new StarterCategory("GEN 2", List.of(spec("chikorita", "grass", "", "菊草叶"), spec("cyndaquil", "fire", "", "火球鼠"), spec("totodile", "water", "", "小锯鳄"))),
            new StarterCategory("GEN 3", List.of(spec("treecko", "grass", "", "木守宫"), spec("torchic", "fire", "", "火稚鸡"), spec("mudkip", "water", "", "水跃鱼"))),
            new StarterCategory("GEN 4", List.of(spec("turtwig", "grass", "", "草苗龟"), spec("chimchar", "fire", "", "小火焰猴"), spec("piplup", "water", "", "波加曼"))),
            new StarterCategory("GEN 5", List.of(spec("snivy", "grass", "", "藤藤蛇"), spec("tepig", "fire", "", "暖暖猪"), spec("oshawott", "water", "", "水水獭"))),
            new StarterCategory("GEN 6", List.of(spec("chespin", "grass", "", "哈力栗"), spec("fennekin", "fire", "", "火狐狸"), spec("froakie", "water", "", "呱呱泡蛙"))),
            new StarterCategory("GEN 7", List.of(spec("rowlet", "grass", "flying", "木木枭"), spec("litten", "fire", "", "火斑喵"), spec("popplio", "water", "", "球球海狮"))),
            new StarterCategory("GEN 8", List.of(spec("grookey", "grass", "", "敲音猴"), spec("scorbunny", "fire", "", "炎兔儿"), spec("sobble", "water", "", "泪眼蜥"))),
            new StarterCategory("GEN 9", List.of(spec("sprigatito", "grass", "", "新叶喵"), spec("fuecoco", "fire", "", "呆火鳄"), spec("quaxly", "water", "", "润水鸭")))
    );

    private BridgeStarterViewState state;
    private final List<ButtonWidget> entryButtons = new ArrayList<>();
    private final List<ButtonWidget> categoryButtons = new ArrayList<>();
    private ButtonWidget chooseButton;
    private ButtonWidget closeButton;
    private ButtonWidget prevButton;
    private ButtonWidget nextButton;
    private int selectedIndex;
    private int selectedCategory;
    private boolean awaitingChoice;

    public BridgeStarterScreen(BridgeStarterViewState state) {
        super(Text.literal(state == null ? "选择初始伙伴" : state.title()));
        this.state = state;
        this.selectedCategory = 0;
        this.selectedIndex = 0;
        alignWithState();
    }

    @Override protected void init() { rebuildButtons(); }
    @Override public boolean shouldPause() { return false; }

    @Override
    public void tick() {
        BridgeStarterViewState latest = BridgeStarterStateStore.current();
        if (latest == null || !latest.active()) {
            closeSilently();
            return;
        }
        if (latest != state) {
            state = latest;
            alignWithState();
            rebuildButtons();
        }
    }

    private static StarterSpec spec(String species, String primary, String secondary, String fallbackCn) {
        return new StarterSpec(species, primary, secondary, fallbackCn);
    }

    private void alignWithState() {
        if (state == null) return;
        if (!state.entries().isEmpty()) {
            String species = state.entries().get(clampIndex(state.selectedIndex(), state.entries().size())).species();
            for (int c = 0; c < CATEGORIES.size(); c++) {
                List<StarterSpec> specs = CATEGORIES.get(c).starters();
                for (int i = 0; i < specs.size(); i++) {
                    if (specs.get(i).species().equalsIgnoreCase(species)) {
                        selectedCategory = c;
                        selectedIndex = i;
                        return;
                    }
                }
            }
        }
        selectedCategory = clampCategory(selectedCategory);
        selectedIndex = clampIndex(selectedIndex, currentCategory().starters().size());
    }

    private void rebuildButtons() {
        clearChildren();
        entryButtons.clear();
        categoryButtons.clear();
        if (state == null) return;
        int x = panelX();
        int y = panelY();
        int scale = panelScale();

        closeButton = addDrawableChild(ButtonWidget.builder(Text.empty(), b -> closeAndNotify())
                .dimensions(sx(x, 182, scale), sy(y, 5, scale), 16 * scale, 12 * scale).build());
        prevButton = addDrawableChild(ButtonWidget.builder(Text.empty(), b -> cycle(-1))
                .dimensions(sx(x, 86, scale), sy(y, 114, scale), 10 * scale, 14 * scale).build());
        nextButton = addDrawableChild(ButtonWidget.builder(Text.empty(), b -> cycle(1))
                .dimensions(sx(x, 170, scale), sy(y, 114, scale), 10 * scale, 14 * scale).build());
        chooseButton = addDrawableChild(ButtonWidget.builder(Text.empty(), b -> chooseCurrent())
                .dimensions(sx(x, 108, scale), sy(y, 151, scale), 52 * scale, 12 * scale).build());

        for (int c = 0; c < CATEGORIES.size(); c++) {
            final int idx = c;
            int logicalY = 6 + c * 18;
            ButtonWidget btn = ButtonWidget.builder(Text.empty(), b -> {
                selectedCategory = idx;
                selectedIndex = 0;
                clickSound();
                rebuildButtons();
            }).dimensions(sx(x, 3, scale), sy(y, logicalY, scale), 36 * scale, 14 * scale).build();
            categoryButtons.add(addDrawableChild(btn));
        }

        List<BridgeStarterViewState.Entry> entries = currentEntries();
        for (int i = 0; i < entries.size(); i++) {
            final int idx = i;
            int logicalX = 94 + i * 24;
            ButtonWidget btn = ButtonWidget.builder(Text.empty(), b -> {
                selectedIndex = idx;
                clickSound();
            }).dimensions(sx(x, logicalX, scale), sy(y, 152, scale), 22 * scale, 18 * scale).build();
            entryButtons.add(addDrawableChild(btn));
        }
    }

    private int panelScale() {
        int maxByWidth = Math.max(1, (this.width - 40) / LOGICAL_W);
        int maxByHeight = Math.max(1, (this.height - 24) / LOGICAL_H);
        return Math.max(1, Math.min(2, Math.min(maxByWidth, maxByHeight)));
    }
    private int panelPixelWidth() { return LOGICAL_W * panelScale(); }
    private int panelPixelHeight() { return LOGICAL_H * panelScale(); }
    private int panelX() { return (this.width - panelPixelWidth()) / 2; }
    private int panelY() { return Math.max(2, (this.height - panelPixelHeight()) / 2 - 10); }
    private int sx(int panelX, int logicalX, int scale) { return panelX + logicalX * scale; }
    private int sy(int panelY, int logicalY, int scale) { return panelY + logicalY * scale; }

    private void cycle(int delta) {
        List<BridgeStarterViewState.Entry> entries = currentEntries();
        if (entries.isEmpty()) return;
        selectedIndex = Math.floorMod(selectedIndex + delta, entries.size());
        clickSound();
    }

    private void chooseCurrent() {
        if (awaitingChoice) return;
        BridgeStarterViewState.Entry entry = currentEntry();
        if (entry == null) return;
        awaitingChoice = true;
        clickSound();
        ClientPlayNetworking.send(BridgePayloads.RawBridgePayload.starterAction("choose:" + entry.species().toLowerCase(Locale.ROOT)));
    }

    private void closeAndNotify() {
        try { ClientPlayNetworking.send(BridgePayloads.RawBridgePayload.starterAction("close")); } catch (Throwable ignored) {}
        closeSilently();
    }

    @Override public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {}

    @Override
    public void render(DrawContext dc, int mouseX, int mouseY, float delta) {
        if (state == null) return;
        super.render(dc, mouseX, mouseY, delta);
        int x = panelX();
        int y = panelY();
        int scale = panelScale();
        int w = panelPixelWidth();
        int h = panelPixelHeight();

        drawTexture(dc, TEX_BASE, x, y, w, h, 800, 700);
        drawTexture(dc, TEX_UNDERLAY, x, y, w, h, 800, 700);
        drawCategoryButtons(dc, x, y, scale);

        BridgeStarterViewState.Entry current = currentEntry();
        if (current != null) {
            drawMainPreview(dc, current, x, y, scale, delta);
            drawTexts(dc, current, x, y, scale);
            drawTypes(dc, current, x, y, scale);
        }
        drawStarterCarousel(dc, x, y, scale);
        drawButtons(dc, x, y, scale);
        drawTexture(dc, TEX_FRAME, x, y, w, h, 800, 700);

        if (awaitingChoice) {
            dc.fill(sx(x, 78, scale), sy(y, 68, scale), sx(x, 188, scale), sy(y, 108, scale), 0x99000000);
            dc.drawCenteredTextWithShadow(this.textRenderer, "正在确认你的选择...", sx(x, 133, scale), sy(y, 86, scale), 0xFFFFFFFF);
        }
    }

    private void drawCategoryButtons(DrawContext dc, int x, int y, int scale) {
        for (int i = 0; i < CATEGORIES.size(); i++) {
            int bx = sx(x, 3, scale);
            int by = sy(y, 6 + i * 18, scale);
            int bw = 36 * scale;
            int bh = 14 * scale;
            boolean active = i == selectedCategory;
            dc.fill(bx, by, bx + bw, by + bh, active ? 0xFF4A4A4E : 0xFF38383C);
            dc.fill(bx + scale, by + scale, bx + bw - scale, by + bh - scale, active ? 0xFF222225 : 0xFF1E1E20);
            dc.drawCenteredTextWithShadow(this.textRenderer, CATEGORIES.get(i).label(), bx + bw / 2, by + 3 * scale, active ? 0xFFFDFDFD : 0xFFD1D1D1);
        }
    }

    private void drawTexts(DrawContext dc, BridgeStarterViewState.Entry current, int x, int y, int scale) {
        dc.drawText(this.textRenderer, current.displayName(), sx(x, 80, scale), sy(y, 16, scale), 0xFFF4F4F4, false);
        int textX = sx(x, 116, scale);
        int textY = sy(y, 14, scale);
        int wrapWidth = 66 * scale;
        String raw = current.description() == null ? "" : current.description().trim();
        Text descText;
        if (!raw.isEmpty() && raw.startsWith("cobblemon.") && I18n.hasTranslation(raw)) {
            descText = Text.translatable(raw);
        } else {
            descText = Text.literal(raw);
        }
        var wrapped = this.textRenderer.wrapLines(descText, wrapWidth);
        for (int i = 0; i < Math.min(4, wrapped.size()); i++) {
            dc.drawText(this.textRenderer, wrapped.get(i), textX, textY + i * (this.textRenderer.fontHeight + 2), 0xFFEAEAEA, false);
        }
    }

    private void drawTypes(DrawContext dc, BridgeStarterViewState.Entry current, int x, int y, int scale) {
        boolean dual = current.secondaryType() != null && !current.secondaryType().isBlank();
        int bgX = sx(x, 120, scale);
        int bgY = sy(y, 50, scale);
        int bgW = dual ? 34 * scale : 19 * scale;
        int bgH = 19 * scale;
        drawTexture(dc, dual ? TEX_TYPE_DOUBLE : TEX_TYPE_SINGLE, bgX, bgY, bgW, bgH, dual ? 136 : 76, 76);
        drawTypeIcon(dc, current.primaryType(), bgX + 2 * scale, bgY + 2 * scale, 15 * scale, 15 * scale);
        if (dual) drawTypeIcon(dc, current.secondaryType(), bgX + 17 * scale, bgY + 2 * scale, 15 * scale, 15 * scale);
    }

    private void drawTypeIcon(DrawContext dc, String type, int x, int y, int w, int h) {
        if (type == null) return;
        Integer idx = TYPE_INDEX.get(type.trim().toLowerCase(Locale.ROOT));
        if (idx == null) return;
        int u = idx * 36;
        dc.drawTexture(RenderPipelines.GUI_TEXTURED, TEX_TYPES, x, y, (float) u, 0f, w, h, 36, 36, 648, 36);
    }

    private void drawMainPreview(DrawContext dc, BridgeStarterViewState.Entry current, int x, int y, int scale, float delta) {
        int boxX1 = sx(x, 77, scale);
        int boxY1 = sy(y, 58, scale);
        int boxX2 = sx(x, 190, scale);
        int boxY2 = sy(y, 144, scale);
        dc.fill(boxX1 + scale, boxY1 + scale, boxX2 - scale, boxY2 - scale, 0xFF000000);
        int portraitSize = 74 * scale;
        int px = sx(x, 96, scale);
        int py = sy(y, 66, scale);
        boolean portrait = PortraitRenderUtil.drawSpeciesPortrait(dc, current.species(), px, py, portraitSize, portraitSize);
        if (!portrait) {
            PortraitRenderUtil.drawFallbackBadge(dc, this.textRenderer, current.species(), px + 8 * scale, py + 8 * scale, portraitSize - 16 * scale, portraitSize - 16 * scale);
        }
    }

    private void drawStarterCarousel(DrawContext dc, int x, int y, int scale) {
        List<BridgeStarterViewState.Entry> entries = currentEntries();
        for (int i = 0; i < entries.size(); i++) {
            BridgeStarterViewState.Entry e = entries.get(i);
            int logicalX = 94 + i * 24;
            int slotX = sx(x, logicalX, scale);
            int slotY = sy(y, 156, scale);
            int slotW = 22 * scale;
            int slotH = 18 * scale;
            drawTextureRegion(dc, TEX_SLOT, slotX, slotY, slotW, slotH, 16, 8, 150, 44, 202, 60);
            if (i == selectedIndex) dc.fill(slotX + scale, slotY + scale, slotX + slotW - scale, slotY + slotH - scale, 0x333CE6D4);
            boolean portrait = PortraitRenderUtil.drawSpeciesPortrait(dc, e.species(), slotX + 2 * scale, slotY + 1 * scale, 16 * scale, 16 * scale);
            if (!portrait) PortraitRenderUtil.drawFallbackBadge(dc, this.textRenderer, e.species(), slotX + 2 * scale, slotY + 1 * scale, 16 * scale, 16 * scale);
        }
    }

    private void drawButtons(DrawContext dc, int x, int y, int scale) {
        if (chooseButton != null) {
            drawTextureRegion(dc, TEX_BUTTON, chooseButton.getX(), chooseButton.getY(), chooseButton.getWidth(), chooseButton.getHeight(), 0,
                    chooseButton.isHovered() && chooseButton.active ? 48 : 0, 224, 48, 224, 96);
            dc.drawCenteredTextWithShadow(this.textRenderer, "选择", chooseButton.getX() + chooseButton.getWidth() / 2, chooseButton.getY() + 1 * scale, chooseButton.active ? 0xFFFFFFFF : 0xFF888888);
        }
        if (closeButton != null) drawTextureRegion(dc, TEX_EXIT, closeButton.getX(), closeButton.getY(), closeButton.getWidth(), closeButton.getHeight(), 0, 0, 64, 48, 64, 48);
        if (prevButton != null) drawTexture(dc, TEX_ARROW_LEFT, prevButton.getX(), prevButton.getY(), prevButton.getWidth(), prevButton.getHeight(), 36, 56);
        if (nextButton != null) drawTexture(dc, TEX_ARROW_RIGHT, nextButton.getX(), nextButton.getY(), nextButton.getWidth(), nextButton.getHeight(), 36, 56);
    }

    private List<BridgeStarterViewState.Entry> currentEntries() {
        List<BridgeStarterViewState.Entry> out = new ArrayList<>();
        Map<String, BridgeStarterViewState.Entry> server = new LinkedHashMap<>();
        if (state != null) for (BridgeStarterViewState.Entry e : state.entries()) server.put(e.species().toLowerCase(Locale.ROOT), e);
        for (StarterSpec spec : currentCategory().starters()) {
            String species = spec.species();
            BridgeStarterViewState.Entry fromServer = server.get(species.toLowerCase(Locale.ROOT));
            String displayName = fromServer != null && fromServer.displayName() != null && !fromServer.displayName().isBlank() ? fromServer.displayName() : translatedSpeciesName(species, spec.fallbackCn());
            String description = fromServer != null && fromServer.description() != null && !fromServer.description().isBlank() ? fromServer.description() : translatedDescription(species);
            String primary = fromServer != null && fromServer.primaryType() != null && !fromServer.primaryType().isBlank() ? fromServer.primaryType() : spec.primaryType();
            String secondary = fromServer != null && fromServer.secondaryType() != null && !fromServer.secondaryType().isBlank() ? fromServer.secondaryType() : spec.secondaryType();
            out.add(new BridgeStarterViewState.Entry(species, displayName, description, primary, secondary, fromServer != null ? fromServer.gender() : "N", fromServer != null && fromServer.shiny(), fromServer != null ? fromServer.level() : 10));
        }
        return out;
    }

    private String translatedSpeciesName(String species, String fallbackCn) {
        String k1 = "cobblemon.species." + species.toLowerCase(Locale.ROOT) + ".name";
        if (I18n.hasTranslation(k1)) return I18n.translate(k1);
        String k2 = "pokedemo.species." + species.toLowerCase(Locale.ROOT);
        if (I18n.hasTranslation(k2)) return I18n.translate(k2);
        return fallbackCn == null || fallbackCn.isBlank() ? pretty(species) : fallbackCn;
    }

    private String translatedDescription(String species) {
        String key = "cobblemon.species." + species.toLowerCase(Locale.ROOT) + ".desc";
        if (I18n.hasTranslation(key)) return I18n.translate(key);
        return "";
    }

    private static String pretty(String species) {
        if (species == null || species.isBlank()) return "";
        String s = species.replace('_', ' ').replace('-', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private BridgeStarterViewState.Entry currentEntry() {
        List<BridgeStarterViewState.Entry> entries = currentEntries();
        if (entries.isEmpty()) return null;
        selectedIndex = clampIndex(selectedIndex, entries.size());
        return entries.get(selectedIndex);
    }
    private StarterCategory currentCategory() { return CATEGORIES.get(clampCategory(selectedCategory)); }
    private int clampCategory(int idx) { return Math.max(0, Math.min(CATEGORIES.size() - 1, idx)); }
    private int clampIndex(int idx, int size) { return size <= 0 ? 0 : Math.max(0, Math.min(size - 1, idx)); }
    private void closeSilently() { MinecraftClient mc = MinecraftClient.getInstance(); if (mc.currentScreen == this) mc.setScreen(null); }
    private void clickSound() { MinecraftClient mc = MinecraftClient.getInstance(); if (mc.player != null) mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 1.0f); }
    private void drawTexture(DrawContext dc, Identifier tex, int x, int y, int w, int h, int texW, int texH) { dc.drawTexture(RenderPipelines.GUI_TEXTURED, tex, x, y, 0f, 0f, w, h, texW, texH, texW, texH); }
    private void drawTextureRegion(DrawContext dc, Identifier tex, int x, int y, int w, int h, int u, int v, int rw, int rh, int texW, int texH) { dc.drawTexture(RenderPipelines.GUI_TEXTURED, tex, x, y, (float) u, (float) v, w, h, rw, rh, texW, texH); }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();
        if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_E) { closeAndNotify(); return true; }
        if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_A) { cycle(-1); return true; }
        if (key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_D) { cycle(1); return true; }
        if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_W) { selectedCategory = Math.floorMod(selectedCategory - 1, CATEGORIES.size()); selectedIndex = 0; rebuildButtons(); return true; }
        if (key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_S) { selectedCategory = Math.floorMod(selectedCategory + 1, CATEGORIES.size()); selectedIndex = 0; rebuildButtons(); return true; }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_SPACE) { chooseCurrent(); return true; }
        return super.keyPressed(input);
    }

    private record StarterCategory(String label, List<StarterSpec> starters) {}
    private record StarterSpec(String species, String primaryType, String secondaryType, String fallbackCn) {}

    private static String localizeDesc(String raw) {
        if (raw == null || raw.isBlank()) return "";
        String s = raw.trim();
        if ((s.startsWith("cobblemon.") || s.startsWith("pokemon.")) && I18n.hasTranslation(s)) {
            return I18n.translate(s);
        }
        return s;
    }

    private static java.util.List<String> wrapDesc(String raw, int maxChars) {
        String desc = localizeDesc(raw);
        java.util.List<String> out = new java.util.ArrayList<>();
        if (desc == null) return out;
        String remaining = desc.trim();
        while (!remaining.isEmpty()) {
            if (remaining.length() <= maxChars) {
                out.add(remaining);
                break;
            }
            int cut = Math.min(maxChars, remaining.length());
            int space = remaining.lastIndexOf(' ', cut);
            int split = (space >= (maxChars / 2)) ? space : cut;
            out.add(remaining.substring(0, split).trim());
            remaining = remaining.substring(split).trim();
        }
        return out;
    }

}
