package win.pokedemo.bridge.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import win.pokedemo.bridge.client.fx.BridgeSendoutFxManager;
import win.pokedemo.bridge.client.hud.PartyHudRenderer;
import win.pokedemo.bridge.client.hud.PartyHudState;
import win.pokedemo.bridge.client.model.BridgeEntityRenderManager;
import win.pokedemo.bridge.client.model.render.BridgeCarrierWorldRenderer;
import win.pokedemo.bridge.client.sound.AmbientPokemonSoundManager;
import win.pokedemo.bridge.client.sound.BattleActionSoundManager;
import win.pokedemo.bridge.client.net.BridgePayloads;
import win.pokedemo.bridge.client.net.BridgeProtocol;
import win.pokedemo.bridge.client.starter.BridgeStarterScreen;
import win.pokedemo.bridge.client.starter.BridgeStarterStateStore;

public final class PokeDemoBridgeClient implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("PokeDemoBridge");
    private static final PartyHudState PARTY_HUD_STATE = new PartyHudState();
    private static final BridgeEntityRenderManager ENTITY_RENDER_MANAGER = new BridgeEntityRenderManager();
    private static KeyBinding TOGGLE_HUD_KEY;
    private static KeyBinding PARTY_PREV_KEY;
    private static KeyBinding PARTY_NEXT_KEY;
    private static KeyBinding SENDOUT_KEY;
    private static KeyBinding BATTLE_UI_KEY;
    private static final BridgeSendoutFxManager SENDOUT_FX = new BridgeSendoutFxManager();
    private static final AmbientPokemonSoundManager AMBIENT_SOUNDS = new AmbientPokemonSoundManager();
    private static final BattleActionSoundManager BATTLE_ACTION_SOUNDS = new BattleActionSoundManager();
    private static boolean notifiedJoin = false;
    private static Object lastWorldRef = null;
    private static boolean battleUiHiddenByUser = false;
    private static String battleUiSuppressedUntilSignatureChanges = null;
    private static long battleReopenHintUntilMs = 0L;

    @Override
    public void onInitializeClient() {
        LOGGER.info("[PokeDemoBridge] Client mod loaded successfully.");

        KeyBinding.Category category = KeyBinding.Category.create(Identifier.of("pokedemo_bridge", "main"));
        TOGGLE_HUD_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.pokedemo_bridge.toggle_hud",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_O,
                category
        ));
        PARTY_PREV_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.pokedemo_bridge.party_prev",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_UP,
                category
        ));
        PARTY_NEXT_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.pokedemo_bridge.party_next",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_DOWN,
                category
        ));
        SENDOUT_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.pokedemo_bridge.sendout",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_R,
                category
        ));
        BATTLE_UI_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.pokedemo_bridge.battle_ui",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_B,
                category
        ));

        PayloadTypeRegistry.playS2C().register(BridgePayloads.SYNC_PARTY_ID, BridgePayloads.RawBridgePayload.PARTY_CODEC);
        PayloadTypeRegistry.playS2C().register(BridgePayloads.SYNC_ENTITY_ID, BridgePayloads.RawBridgePayload.ENTITY_CODEC);
        PayloadTypeRegistry.playS2C().register(BridgePayloads.REMOVE_ENTITY_ID, BridgePayloads.RawBridgePayload.REMOVE_CODEC);
        PayloadTypeRegistry.playS2C().register(BridgePayloads.BATTLE_STATE_ID, BridgePayloads.RawBridgePayload.BATTLE_STATE_CODEC);
        PayloadTypeRegistry.playS2C().register(BridgePayloads.PC_STATE_ID, BridgePayloads.RawBridgePayload.PC_STATE_CODEC);
        PayloadTypeRegistry.playS2C().register(BridgePayloads.STARTER_STATE_ID, BridgePayloads.RawBridgePayload.STARTER_STATE_CODEC);
        PayloadTypeRegistry.playC2S().register(BridgePayloads.HELLO_ID, BridgePayloads.RawBridgePayload.HELLO_CODEC);
        PayloadTypeRegistry.playC2S().register(BridgePayloads.SENDOUT_ID, BridgePayloads.RawBridgePayload.SENDOUT_CODEC);
        PayloadTypeRegistry.playC2S().register(BridgePayloads.MODEL_INTERACT_ID, BridgePayloads.RawBridgePayload.MODEL_INTERACT_CODEC);
        PayloadTypeRegistry.playC2S().register(BridgePayloads.BATTLE_ACTION_ID, BridgePayloads.RawBridgePayload.BATTLE_ACTION_CODEC);
        PayloadTypeRegistry.playC2S().register(BridgePayloads.PC_ACTION_ID, BridgePayloads.RawBridgePayload.PC_ACTION_CODEC);
        PayloadTypeRegistry.playC2S().register(BridgePayloads.STARTER_ACTION_ID, BridgePayloads.RawBridgePayload.STARTER_ACTION_CODEC);

        ClientPlayNetworking.registerGlobalReceiver(BridgePayloads.SYNC_PARTY_ID, (payload, context) ->
                context.client().execute(() -> PARTY_HUD_STATE.setSlots(BridgeProtocol.decodeParty(payload.bytes()))));
        ClientPlayNetworking.registerGlobalReceiver(BridgePayloads.SYNC_ENTITY_ID, (payload, context) ->
                context.client().execute(() -> ENTITY_RENDER_MANAGER.upsert(BridgeProtocol.decodeEntity(payload.bytes()))));
        ClientPlayNetworking.registerGlobalReceiver(BridgePayloads.REMOVE_ENTITY_ID, (payload, context) ->
                context.client().execute(() -> ENTITY_RENDER_MANAGER.remove(BridgeProtocol.decodeRemove(payload.bytes()))));
        ClientPlayNetworking.registerGlobalReceiver(BridgePayloads.BATTLE_STATE_ID, (payload, context) ->
                context.client().execute(() -> win.pokedemo.bridge.client.battle.BridgeBattleStateStore.setCurrent(BridgeProtocol.decodeBattleState(payload.bytes()))));
        ClientPlayNetworking.registerGlobalReceiver(BridgePayloads.PC_STATE_ID, (payload, context) ->
                context.client().execute(() -> {
                    var state = BridgeProtocol.decodePcState(payload.bytes());
                    win.pokedemo.bridge.client.pc.BridgePcStateStore.setCurrent(state);
                    if (state != null && state.active() && !(context.client().currentScreen instanceof win.pokedemo.bridge.client.pc.BridgeCobblemonPcScreen)) {
                        try {
                            var mc = context.client();
                            if (mc.getSoundManager() != null) {
                                mc.getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.ambient(net.minecraft.sound.SoundEvent.of(net.minecraft.util.Identifier.of("cobblemon", "pc.on")), 1.0f, 1.0f));
                            }
                        } catch (Throwable ignored) {}
                        context.client().setScreen(new win.pokedemo.bridge.client.pc.BridgeCobblemonPcScreen());
                    }
                }));
        ClientPlayNetworking.registerGlobalReceiver(BridgePayloads.STARTER_STATE_ID, (payload, context) ->
                context.client().execute(() -> {
                    var state = BridgeProtocol.decodeStarterState(payload.bytes());
                    BridgeStarterStateStore.setCurrent(state);
                    if (state != null && state.active() && !(context.client().currentScreen instanceof win.pokedemo.bridge.client.starter.BridgeStarterScreen)) {
                        context.client().setScreen(new win.pokedemo.bridge.client.starter.BridgeStarterScreen(state));
                    }
                }));

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            notifiedJoin = false;
            lastWorldRef = client.world;
            ENTITY_RENDER_MANAGER.clear();
            SENDOUT_FX.clear();
            AMBIENT_SOUNDS.clear();
            BATTLE_ACTION_SOUNDS.clear();
            ClientPlayNetworking.send(BridgePayloads.RawBridgePayload.hello());
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            battleUiHiddenByUser = false;
            battleUiSuppressedUntilSignatureChanges = null;
            PARTY_HUD_STATE.setSlots(java.util.List.of());
            PARTY_HUD_STATE.setDebugMode(true);
            ENTITY_RENDER_MANAGER.clear();
            SENDOUT_FX.clear();
            AMBIENT_SOUNDS.clear();
            BATTLE_ACTION_SOUNDS.clear();
            win.pokedemo.bridge.client.pc.BridgePcStateStore.clear();
            BridgeStarterStateStore.clear();
            lastWorldRef = null;
            notifiedJoin = false;
        });

        HudRenderCallback.EVENT.register(new PartyHudRenderer(PARTY_HUD_STATE));
        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (battleReopenHintUntilMs <= 0L || client == null || client.textRenderer == null) return;
            long now = System.currentTimeMillis();
            if (now >= battleReopenHintUntilMs) {
                battleReopenHintUntilMs = 0L;
                return;
            }
            if (client.currentScreen != null) return;
            Text text = Text.translatable("message.pokedemo_bridge.battle_reopen_hint");
            int sw = client.getWindow().getScaledWidth();
            int sh = client.getWindow().getScaledHeight();
            float scale = 1.8F;
            int x = sw / 2;
            int y = sh / 2 - 18;
            int boxHalfW = Math.max(90, client.textRenderer.getWidth(text));
            int boxH = 18;
            drawContext.fill(x - boxHalfW / 2 - 8, y - 6, x + boxHalfW / 2 + 8, y + boxH, 0xA0000000);
            drawContext.getMatrices().pushMatrix();
            drawContext.getMatrices().translate((float) x, (float) y);
            drawContext.getMatrices().scale(scale, scale);
            drawContext.drawCenteredTextWithShadow(client.textRenderer, text, 0, 0, 0xFFFFE066);
            drawContext.getMatrices().popMatrix();
        });
        BridgeCarrierWorldRenderer.register(SENDOUT_FX);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world != lastWorldRef) {
                ENTITY_RENDER_MANAGER.clear();
                SENDOUT_FX.clear();
                AMBIENT_SOUNDS.clear();
                BATTLE_ACTION_SOUNDS.clear();
                lastWorldRef = client.world;
            }
            ENTITY_RENDER_MANAGER.tick();
            SENDOUT_FX.tick();
            AMBIENT_SOUNDS.tick();
            BATTLE_ACTION_SOUNDS.tick();
            if (!SENDOUT_FX.isBusy()) {
                while (PARTY_PREV_KEY.wasPressed()) {
                    PARTY_HUD_STATE.cycleSelectedSlot(-1);
                }
                while (PARTY_NEXT_KEY.wasPressed()) {
                    PARTY_HUD_STATE.cycleSelectedSlot(1);
                }
            }
            win.pokedemo.bridge.client.battle.BridgeBattleViewState battleState = win.pokedemo.bridge.client.battle.BridgeBattleStateStore.current();
            while (SENDOUT_KEY.wasPressed()) {
                if (battleState != null) {
                    continue;
                }
                if (!SENDOUT_FX.isBusy()) {
                    SENDOUT_FX.triggerSelected();
                }
            }
            boolean activeBattle = battleState != null;
            String battleSignature = activeBattle ? battleUiStateSignature(battleState) : null;
            if (!activeBattle) {
                battleUiHiddenByUser = false;
                battleUiSuppressedUntilSignatureChanges = null;
            } else if (battleUiSuppressedUntilSignatureChanges != null && !battleUiSuppressedUntilSignatureChanges.equals(battleSignature)) {
                battleUiSuppressedUntilSignatureChanges = null;
            }
            if (activeBattle && client.currentScreen == null && !battleUiHiddenByUser && battleUiSuppressedUntilSignatureChanges == null) {
                client.setScreen(new win.pokedemo.bridge.client.battle.BridgeCobblemonBattleScreen(battleState));
            } else if (!activeBattle && client.currentScreen instanceof win.pokedemo.bridge.client.battle.BridgeCobblemonBattleScreen) {
                client.setScreen(null);
            }
            var pcState = win.pokedemo.bridge.client.pc.BridgePcStateStore.current();
            if ((pcState == null || !pcState.active()) && client.currentScreen instanceof win.pokedemo.bridge.client.pc.BridgeCobblemonPcScreen) {
                client.setScreen(null);
            }
            win.pokedemo.bridge.client.starter.BridgeStarterViewState starterState = BridgeStarterStateStore.current();
            boolean activeStarter = starterState != null && starterState.active();
            if (activeStarter && !activeBattle && !(client.currentScreen instanceof BridgeStarterScreen)) {
                client.setScreen(new BridgeStarterScreen(starterState));
            } else if (!activeStarter && client.currentScreen instanceof BridgeStarterScreen) {
                client.setScreen(null);
            }

            while (BATTLE_UI_KEY.wasPressed()) {
                if (!activeBattle) {
                    continue;
                }
                if (client.currentScreen == null) {
                    battleUiHiddenByUser = false;
                    battleUiSuppressedUntilSignatureChanges = null;
                    client.setScreen(new win.pokedemo.bridge.client.battle.BridgeCobblemonBattleScreen(battleState));
                } else if (client.currentScreen instanceof win.pokedemo.bridge.client.battle.BridgeCobblemonBattleScreen battleScreen) {
                    battleScreen.requestCloseUi();
                }
            }
            while (client.options.useKey.wasPressed()) {
                if (client.currentScreen != null) break;
                if (client.crosshairTarget != null && client.crosshairTarget.getType() == net.minecraft.util.hit.HitResult.Type.ENTITY) break;
                java.util.UUID target = win.pokedemo.bridge.client.model.render.BridgeCarrierWorldRenderer.pickModelTarget(client, 6.0);
                if (target != null) {
                    ClientPlayNetworking.send(BridgePayloads.RawBridgePayload.modelInteract(target));
                }
            }
            while (client.options.attackKey.wasPressed()) {
                if (client.currentScreen != null) break;
                if (client.crosshairTarget != null && client.crosshairTarget.getType() == net.minecraft.util.hit.HitResult.Type.ENTITY) break;
                java.util.UUID target = win.pokedemo.bridge.client.model.render.BridgeCarrierWorldRenderer.pickModelTarget(client, 6.0);
                if (target != null) {
                    ClientPlayNetworking.send(BridgePayloads.RawBridgePayload.modelInteract(target));
                }
            }
            while (TOGGLE_HUD_KEY.wasPressed()) {
                PARTY_HUD_STATE.setEnabled(!PARTY_HUD_STATE.enabled());
                if (client.player != null) {
                    client.player.sendMessage(Text.translatable(
                            PARTY_HUD_STATE.enabled() ? "message.pokedemo_bridge.hud_on" : "message.pokedemo_bridge.hud_off"
                    ), true);
                }
            }
            if (!notifiedJoin && client.player != null && client.world != null) {
                client.player.sendMessage(localizedBridgeLoadedMessage(client), false);
                notifiedJoin = true;
            }
        });
    }

    public static PartyHudState partyHudState() {
        return PARTY_HUD_STATE;
    }

    public static BridgeEntityRenderManager entityRenderManager() {
        return ENTITY_RENDER_MANAGER;
    }


    public static BridgeSendoutFxManager sendoutFx() {
        return SENDOUT_FX;
    }

    public static AmbientPokemonSoundManager ambientSounds() {
        return AMBIENT_SOUNDS;
    }

    public static BattleActionSoundManager battleActionSounds() {
        return BATTLE_ACTION_SOUNDS;
    }

    public static void hideBattleUiByUser() {
        battleUiHiddenByUser = true;
        battleUiSuppressedUntilSignatureChanges = null;
    }

    public static void showBattleReopenHint() {
        battleReopenHintUntilMs = System.currentTimeMillis() + 3500L;
    }

    public static void suppressBattleUiUntilStateChanges(win.pokedemo.bridge.client.battle.BridgeBattleViewState state) {
        battleUiHiddenByUser = false;
        battleUiSuppressedUntilSignatureChanges = state == null ? null : battleUiStateSignature(state);
    }

    public static void allowBattleUiAutoOpen() {
        battleUiHiddenByUser = false;
        battleUiSuppressedUntilSignatureChanges = null;
    }

    private static String battleUiStateSignature(win.pokedemo.bridge.client.battle.BridgeBattleViewState state) {
        if (state == null) return "";
        String lastLog = state.logLines().isEmpty() ? "" : state.logLines().get(state.logLines().size() - 1);
        return String.join("|",
                safe(state.requestType()),
                safe(state.statusLine()),
                safe(lastLog),
                safe(state.self().name()),
                String.valueOf(state.self().hp()),
                String.valueOf(state.self().maxHp()),
                safe(state.foe().name()),
                String.valueOf(state.foe().hp()),
                String.valueOf(state.foe().maxHp()),
                String.valueOf(state.awaitingForcedSwitch()),
                String.valueOf(state.processingTurn()));
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }


    private static Text localizedBridgeLoadedMessage(MinecraftClient client) {
        String lang = "en_us";
        try {
            if (client != null && client.options != null && client.options.language != null) {
                lang = String.valueOf(client.options.language).toLowerCase(java.util.Locale.ROOT);
            }
        } catch (Throwable ignored) {}
        return switch (lang) {
            case "zh_cn" -> Text.literal("客户端模组已接入。按\"O\"可关闭左侧宝可梦栏显示。\n上下键选择栏位后按\"R\"召唤宝可梦。");
            case "ja_jp" -> Text.literal("クライアントモッドが接続されました。\"O\"キーで左側のポケモン欄を非表示にできます。\n上下キーでスロットを選び、\"R\"キーでポケモンを繰り出します。");
            case "ko_kr" -> Text.literal("클라이언트 모드가 연결되었습니다. \"O\" 키로 왼쪽 포켓몬 목록 표시를 끌 수 있습니다.\n위/아래 키로 슬롯을 선택한 뒤 \"R\" 키로 포켓몬을 꺼내세요.");
            default -> Text.literal("Bridge client connected. Press \"O\" to hide the party HUD.\nUse the Up/Down keys to select a slot, then press \"R\" to send out your Pokémon.");
        };
    }

    private static Text localizedHudToggleMessage(MinecraftClient client, boolean enabled) {
        String lang = "en_us";
        try {
            if (client != null && client.options != null && client.options.language != null) {
                lang = String.valueOf(client.options.language).toLowerCase(java.util.Locale.ROOT);
            }
        } catch (Throwable ignored) {}
        return switch (lang) {
            case "zh_cn" -> Text.literal(enabled ? "已开启左侧宝可梦栏显示" : "已关闭左侧宝可梦栏显示");
            case "ja_jp" -> Text.literal(enabled ? "左側のポケモン欄を表示しました" : "左側のポケモン欄を非表示にしました");
            case "ko_kr" -> Text.literal(enabled ? "왼쪽 포켓몬 목록 표시를 켰습니다" : "왼쪽 포켓몬 목록 표시를 껐습니다");
            default -> Text.literal(enabled ? "Party HUD enabled" : "Party HUD hidden");
        };
    }
}
