package win.pokedemo.bridge.client.net;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import win.pokedemo.bridge.client.battle.BridgeBattleViewState;
import win.pokedemo.bridge.client.pc.BridgePcSlotState;
import win.pokedemo.bridge.client.pc.BridgePcViewState;
import win.pokedemo.bridge.client.starter.BridgeStarterViewState;
import win.pokedemo.bridge.common.CarrierRenderState;
import win.pokedemo.bridge.common.PartySlotState;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

public final class BridgeProtocol {
    private static final Gson GSON = new Gson();
    public static List<PartySlotState> decodeParty(byte[] bytes) {
        JsonObject[] dummy = null;
        var arr = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonArray();
        java.util.List<PartySlotState> out = new java.util.ArrayList<>();
        for (var el : arr) {
            JsonObject obj = el.getAsJsonObject();
            UUID pokemonUuid = null;
            if (obj.has("pokemonUuid") && !obj.get("pokemonUuid").isJsonNull()) {
                String raw = obj.get("pokemonUuid").getAsString();
                if (raw != null && !raw.isBlank()) pokemonUuid = UUID.fromString(raw);
            }
            out.add(new PartySlotState(
                    obj.has("slot") ? obj.get("slot").getAsInt() : 0,
                    pokemonUuid,
                    obj.has("occupied") && obj.get("occupied").getAsBoolean(),
                    obj.has("species") ? obj.get("species").getAsString() : "",
                    obj.has("displayName") ? obj.get("displayName").getAsString() : "",
                    obj.has("level") ? obj.get("level").getAsInt() : 0,
                    obj.has("hp") ? obj.get("hp").getAsInt() : 0,
                    obj.has("maxHp") ? obj.get("maxHp").getAsInt() : 0,
                    obj.has("status") ? win.pokedemo.bridge.common.PokemonStatus.fromWire(obj.get("status").getAsString()) : win.pokedemo.bridge.common.PokemonStatus.NONE,
                    obj.has("active") && obj.get("active").getAsBoolean(),
                    obj.has("gender") ? obj.get("gender").getAsString() : "N",
                    obj.has("shiny") && obj.get("shiny").getAsBoolean(),
                    obj.has("heldItemId") ? obj.get("heldItemId").getAsString() : "",
                    obj.has("ballId") ? obj.get("ballId").getAsString() : "poke_ball"
            ));
        }
        return out;
    }

    public static CarrierRenderState decodeEntity(byte[] bytes) {
        JsonObject obj = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
        UUID ownerUuid = null;
        if (obj.has("ownerUuid") && !obj.get("ownerUuid").isJsonNull()) {
            String rawOwner = obj.get("ownerUuid").getAsString();
            if (rawOwner != null && !rawOwner.isBlank()) {
                ownerUuid = UUID.fromString(rawOwner);
            }
        }
        UUID pokemonUuid = null;
        if (obj.has("pokemonUuid") && !obj.get("pokemonUuid").isJsonNull()) {
            String rawPoke = obj.get("pokemonUuid").getAsString();
            if (rawPoke != null && !rawPoke.isBlank()) {
                pokemonUuid = UUID.fromString(rawPoke);
            }
        }
        return new CarrierRenderState(
                UUID.fromString(obj.get("entityUuid").getAsString()),
                obj.get("entityId").getAsInt(),
                ownerUuid,
                obj.has("slot") ? obj.get("slot").getAsInt() : -1,
                pokemonUuid,
                obj.get("species").getAsString(),
                obj.has("form") ? obj.get("form").getAsString() : "normal",
                obj.has("gender") ? obj.get("gender").getAsString() : "N",
                obj.has("shiny") && obj.get("shiny").getAsBoolean(),
                obj.has("animation") ? obj.get("animation").getAsString() : "idle",
                obj.has("scale") ? obj.get("scale").getAsFloat() : 1.0f,
                obj.has("displayName") ? obj.get("displayName").getAsString() : "",
                obj.has("level") ? obj.get("level").getAsInt() : 0,
                obj.has("moveMode") ? obj.get("moveMode").getAsString() : "land_idle",
                obj.has("moveSpeed") ? obj.get("moveSpeed").getAsFloat() : 0.0f,
                obj.has("airborne") && obj.get("airborne").getAsBoolean(),
                obj.has("submerged") && obj.get("submerged").getAsBoolean(),
                obj.has("sleeping") && obj.get("sleeping").getAsBoolean(),
                obj.has("battle") && obj.get("battle").getAsBoolean(),
                obj.has("battleYaw") ? obj.get("battleYaw").getAsFloat() : Float.NaN
        );
    }

    public static UUID decodeRemove(byte[] bytes) {
        return UUID.fromString(new String(bytes, StandardCharsets.UTF_8));
    }


    public static BridgePcViewState decodePcState(byte[] bytes) {
        JsonObject obj = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
        if (obj.has("active") && !obj.get("active").getAsBoolean()) return BridgePcViewState.inactive();
        java.util.List<BridgePcSlotState> slots = new java.util.ArrayList<>();
        if (obj.has("slots") && obj.get("slots").isJsonArray()) {
            for (var el : obj.getAsJsonArray("slots")) {
                JsonObject s = el.getAsJsonObject();
                java.util.UUID pokemonUuid = null;
                if (s.has("pokemonUuid") && !s.get("pokemonUuid").isJsonNull()) {
                    String raw = s.get("pokemonUuid").getAsString();
                    if (raw != null && !raw.isBlank()) pokemonUuid = java.util.UUID.fromString(raw);
                }
                slots.add(new BridgePcSlotState(
                        s.has("pageSlot") ? s.get("pageSlot").getAsInt() : 0,
                        s.has("absoluteIndex") ? s.get("absoluteIndex").getAsInt() : -1,
                        pokemonUuid,
                        s.has("occupied") && s.get("occupied").getAsBoolean(),
                        s.has("species") ? s.get("species").getAsString() : "",
                        s.has("displayName") ? s.get("displayName").getAsString() : "",
                        s.has("level") ? s.get("level").getAsInt() : 0,
                        s.has("hp") ? s.get("hp").getAsInt() : 0,
                        s.has("maxHp") ? s.get("maxHp").getAsInt() : 0,
                        s.has("status") ? win.pokedemo.bridge.common.PokemonStatus.fromWire(s.get("status").getAsString()) : win.pokedemo.bridge.common.PokemonStatus.NONE,
                        s.has("gender") ? s.get("gender").getAsString() : "N",
                        s.has("shiny") && s.get("shiny").getAsBoolean(),
                        s.has("heldItemId") ? s.get("heldItemId").getAsString() : "",
                        s.has("egg") && s.get("egg").getAsBoolean(),
                        s.has("locked") && s.get("locked").getAsBoolean(),
                        s.has("lockReason") ? s.get("lockReason").getAsString() : ""
                ));
            }
        }
        return new BridgePcViewState(
                !obj.has("active") || obj.get("active").getAsBoolean(),
                obj.has("page") ? obj.get("page").getAsInt() : 0,
                obj.has("totalCount") ? obj.get("totalCount").getAsInt() : 0,
                obj.has("hasPrev") && obj.get("hasPrev").getAsBoolean(),
                obj.has("hasNext") && obj.get("hasNext").getAsBoolean(),
                obj.has("pendingReleaseIndex") ? obj.get("pendingReleaseIndex").getAsInt() : -1,
                slots
        );
    }

    public static BridgeBattleViewState decodeBattleState(byte[] bytes) {
        JsonObject obj = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
        if (obj.has("active") && !obj.get("active").getAsBoolean()) return null;
        JsonObject selfObj = obj.has("self") && obj.get("self").isJsonObject() ? obj.getAsJsonObject("self") : new JsonObject();
        JsonObject foeObj = obj.has("foe") && obj.get("foe").isJsonObject() ? obj.getAsJsonObject("foe") : new JsonObject();
        BridgeBattleViewState.Battler self = new BridgeBattleViewState.Battler(
                selfObj.has("species") ? selfObj.get("species").getAsString() : "pikachu",
                selfObj.has("name") ? selfObj.get("name").getAsString() : "皮卡丘",
                selfObj.has("level") ? selfObj.get("level").getAsInt() : 25,
                selfObj.has("hp") ? selfObj.get("hp").getAsInt() : 70,
                selfObj.has("maxHp") ? selfObj.get("maxHp").getAsInt() : 70,
                selfObj.has("status") ? win.pokedemo.bridge.common.PokemonStatus.fromWire(selfObj.get("status").getAsString()) : win.pokedemo.bridge.common.PokemonStatus.NONE,
                selfObj.has("gender") ? selfObj.get("gender").getAsString() : "N"
        );
        BridgeBattleViewState.Battler foe = new BridgeBattleViewState.Battler(
                foeObj.has("species") ? foeObj.get("species").getAsString() : "eevee",
                foeObj.has("name") ? foeObj.get("name").getAsString() : "Eevee",
                foeObj.has("level") ? foeObj.get("level").getAsInt() : 23,
                foeObj.has("hp") ? foeObj.get("hp").getAsInt() : 51,
                foeObj.has("maxHp") ? foeObj.get("maxHp").getAsInt() : 51,
                foeObj.has("status") ? win.pokedemo.bridge.common.PokemonStatus.fromWire(foeObj.get("status").getAsString()) : win.pokedemo.bridge.common.PokemonStatus.NONE,
                foeObj.has("gender") ? foeObj.get("gender").getAsString() : "N"
        );
        java.util.List<BridgeBattleViewState.Move> moves = new java.util.ArrayList<>();
        if (obj.has("moves") && obj.get("moves").isJsonArray()) {
            for (var el : obj.getAsJsonArray("moves")) {
                JsonObject m = el.getAsJsonObject();
                moves.add(new BridgeBattleViewState.Move(
                        m.has("name") ? m.get("name").getAsString() : "—",
                        m.has("detail") ? m.get("detail").getAsString() : "",
                        m.has("pp") ? m.get("pp").getAsString() : "",
                        m.has("type") ? m.get("type").getAsString() : "",
                        m.has("category") ? m.get("category").getAsString() : "",
                        m.has("power") ? m.get("power").getAsInt() : 0,
                        m.has("accuracy") ? m.get("accuracy").getAsInt() : 100,
                        m.has("priority") ? m.get("priority").getAsInt() : 0,
                        m.has("description") ? m.get("description").getAsString() : "",
                        m.has("disabled") && m.get("disabled").getAsBoolean(),
                        m.has("disabledReason") ? m.get("disabledReason").getAsString() : ""
                ));
            }
        }
        java.util.List<win.pokedemo.bridge.common.PartySlotState> party = new java.util.ArrayList<>();
        if (obj.has("party") && obj.get("party").isJsonArray()) {
            for (var el : obj.getAsJsonArray("party")) {
                JsonObject po = el.getAsJsonObject();
                java.util.UUID pokemonUuid = null;
                if (po.has("pokemonUuid") && !po.get("pokemonUuid").isJsonNull()) {
                    String raw = po.get("pokemonUuid").getAsString();
                    if (raw != null && !raw.isBlank()) pokemonUuid = java.util.UUID.fromString(raw);
                }
                party.add(new win.pokedemo.bridge.common.PartySlotState(
                        po.has("slot") ? po.get("slot").getAsInt() : 0,
                        pokemonUuid,
                        po.has("occupied") && po.get("occupied").getAsBoolean(),
                        po.has("species") ? po.get("species").getAsString() : "",
                        po.has("displayName") ? po.get("displayName").getAsString() : "",
                        po.has("level") ? po.get("level").getAsInt() : 0,
                        po.has("hp") ? po.get("hp").getAsInt() : 0,
                        po.has("maxHp") ? po.get("maxHp").getAsInt() : 0,
                        po.has("status") ? win.pokedemo.bridge.common.PokemonStatus.fromWire(po.get("status").getAsString()) : win.pokedemo.bridge.common.PokemonStatus.NONE,
                        po.has("active") && po.get("active").getAsBoolean(),
                        po.has("gender") ? po.get("gender").getAsString() : "N",
                        po.has("shiny") && po.get("shiny").getAsBoolean(),
                        po.has("heldItemId") ? po.get("heldItemId").getAsString() : "",
                        po.has("ballId") ? po.get("ballId").getAsString() : "poke_ball"
                ));
            }
        }
        java.util.List<String> log = new java.util.ArrayList<>();
        if (obj.has("log") && obj.get("log").isJsonArray()) {
            for (var el : obj.getAsJsonArray("log")) log.add(el.getAsString());
        }
        return new BridgeBattleViewState(self, foe, moves, party, log,
                !obj.has("canFight") || obj.get("canFight").getAsBoolean(),
                !obj.has("canSwitch") || obj.get("canSwitch").getAsBoolean(),
                !obj.has("canBag") || obj.get("canBag").getAsBoolean(),
                !obj.has("canRun") || obj.get("canRun").getAsBoolean(),
                obj.has("canForfeit") && obj.get("canForfeit").getAsBoolean(),
                obj.has("pvp") && obj.get("pvp").getAsBoolean(),
                obj.has("awaitingForcedSwitch") && obj.get("awaitingForcedSwitch").getAsBoolean(),
                obj.has("processingTurn") && obj.get("processingTurn").getAsBoolean(),
                obj.has("statusLine") ? obj.get("statusLine").getAsString() : "",
                obj.has("requestType") ? obj.get("requestType").getAsString() : "ACTION");
    }


    public static BridgeStarterViewState decodeStarterState(byte[] bytes) {
        JsonObject obj = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
        if (obj.has("active") && !obj.get("active").getAsBoolean()) return null;
        java.util.List<BridgeStarterViewState.Entry> entries = new java.util.ArrayList<>();
        if (obj.has("entries") && obj.get("entries").isJsonArray()) {
            for (var el : obj.getAsJsonArray("entries")) {
                JsonObject e = el.getAsJsonObject();
                entries.add(new BridgeStarterViewState.Entry(
                        e.has("species") ? e.get("species").getAsString() : "",
                        e.has("displayName") ? e.get("displayName").getAsString() : "",
                        e.has("description") ? e.get("description").getAsString() : "",
                        e.has("primaryType") ? e.get("primaryType").getAsString() : "",
                        e.has("secondaryType") ? e.get("secondaryType").getAsString() : "",
                        e.has("gender") ? e.get("gender").getAsString() : "N",
                        e.has("shiny") && e.get("shiny").getAsBoolean(),
                        e.has("level") ? e.get("level").getAsInt() : 10
                ));
            }
        }
        return new BridgeStarterViewState(
                !obj.has("active") || obj.get("active").getAsBoolean(),
                obj.has("title") ? obj.get("title").getAsString() : "选择初始伙伴",
                obj.has("selectedIndex") ? obj.get("selectedIndex").getAsInt() : 0,
                entries
        );
    }

    private BridgeProtocol() {}
}
