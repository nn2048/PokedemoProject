package win.pokedemo;

import com.google.gson.Gson;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.Messenger;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Drop-in server-side helper for the existing PokeDemo plugin.
 * This file is not wired automatically; it shows the exact payloads the generated Fabric client expects.
 */
public final class PokeDemoClientBridgeService {
    private static final Gson GSON = new Gson();
    private static final String CHANNEL_SYNC_PARTY = "pokedemo_bridge:sync_party";
    private static final String CHANNEL_SYNC_ENTITY = "pokedemo_bridge:sync_entity";
    private static final String CHANNEL_REMOVE_ENTITY = "pokedemo_bridge:remove_entity";
    private static final String CHANNEL_HELLO = "pokedemo_bridge:hello";

    private final JavaPlugin plugin;

    public PokeDemoClientBridgeService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        Messenger messenger = plugin.getServer().getMessenger();
        messenger.registerOutgoingPluginChannel(plugin, CHANNEL_SYNC_PARTY);
        messenger.registerOutgoingPluginChannel(plugin, CHANNEL_SYNC_ENTITY);
        messenger.registerOutgoingPluginChannel(plugin, CHANNEL_REMOVE_ENTITY);
        messenger.registerIncomingPluginChannel(plugin, CHANNEL_HELLO, (channel, player, message) -> {
            // optional handshake hook
        });
    }

    public void syncParty(Player player, PlayerProfile profile) {
        List<Map<String, Object>> payload = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            PokemonInstance p = i < profile.party.size() ? profile.party.get(i) : null;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("slot", i);
            row.put("occupied", p != null);
            if (p != null) {
                row.put("species", p.speciesId);
                row.put("displayName", p.nickname != null && !p.nickname.isBlank() ? p.nickname : p.speciesId);
                row.put("level", p.level);
                row.put("hp", p.currentHp);
                row.put("maxHp", p.maxHp());
                row.put("status", toWireStatus(p.status));
                row.put("active", profile.summoned != null && profile.summoned.equals(p.uuid));
                row.put("gender", p.gender);
                row.put("shiny", p.shiny);
            }
            payload.add(row);
        }
        player.sendPluginMessage(plugin, CHANNEL_SYNC_PARTY, GSON.toJson(payload).getBytes(StandardCharsets.UTF_8));
    }

    public void syncCarrier(Player player, org.bukkit.entity.Entity carrier, PokemonInstance p) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("entityUuid", carrier.getUniqueId().toString());
        payload.put("entityId", carrier.getEntityId());
        payload.put("species", p.speciesId);
        payload.put("form", "normal");
        payload.put("gender", p.gender);
        payload.put("shiny", p.shiny);
        payload.put("animation", "idle");
        payload.put("scale", 1.0f);
        player.sendPluginMessage(plugin, CHANNEL_SYNC_ENTITY, GSON.toJson(payload).getBytes(StandardCharsets.UTF_8));
    }

    public void removeCarrier(Player player, UUID entityUuid) {
        player.sendPluginMessage(plugin, CHANNEL_REMOVE_ENTITY, entityUuid.toString().getBytes(StandardCharsets.UTF_8));
    }

    private String toWireStatus(String status) {
        if (status == null || status.isBlank()) return "NONE";
        return switch (status.toUpperCase(Locale.ROOT)) {
            case "POISON", "PSN" -> "PSN";
            case "BADLY_POISONED", "TOX" -> "TOX";
            case "BURN", "BRN" -> "BRN";
            case "PARALYSIS", "PAR" -> "PAR";
            case "SLEEP", "SLP" -> "SLP";
            case "FREEZE", "FRZ" -> "FRZ";
            case "FAINTED", "FNT" -> "FNT";
            default -> "NONE";
        };
    }
}
