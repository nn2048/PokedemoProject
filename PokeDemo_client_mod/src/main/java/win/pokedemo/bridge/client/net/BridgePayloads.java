package win.pokedemo.bridge.client.net;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import win.pokedemo.bridge.common.BridgeConstants;

import java.nio.charset.StandardCharsets;

public final class BridgePayloads {
    public static final CustomPayload.Id<RawBridgePayload> SYNC_PARTY_ID = new CustomPayload.Id<>(Identifier.of(BridgeConstants.MOD_ID, BridgeConstants.S2C_SYNC_PARTY));
    public static final CustomPayload.Id<RawBridgePayload> SYNC_ENTITY_ID = new CustomPayload.Id<>(Identifier.of(BridgeConstants.MOD_ID, BridgeConstants.S2C_SYNC_ENTITY));
    public static final CustomPayload.Id<RawBridgePayload> REMOVE_ENTITY_ID = new CustomPayload.Id<>(Identifier.of(BridgeConstants.MOD_ID, BridgeConstants.S2C_REMOVE_ENTITY));
    public static final CustomPayload.Id<RawBridgePayload> BATTLE_STATE_ID = new CustomPayload.Id<>(Identifier.of(BridgeConstants.MOD_ID, BridgeConstants.S2C_BATTLE_STATE));
    public static final CustomPayload.Id<RawBridgePayload> PC_STATE_ID = new CustomPayload.Id<>(Identifier.of(BridgeConstants.MOD_ID, BridgeConstants.S2C_PC_STATE));
    public static final CustomPayload.Id<RawBridgePayload> STARTER_STATE_ID = new CustomPayload.Id<>(Identifier.of(BridgeConstants.MOD_ID, BridgeConstants.S2C_STARTER_STATE));
    public static final CustomPayload.Id<RawBridgePayload> HELLO_ID = new CustomPayload.Id<>(Identifier.of(BridgeConstants.MOD_ID, BridgeConstants.C2S_HELLO));
    public static final CustomPayload.Id<RawBridgePayload> SENDOUT_ID = new CustomPayload.Id<>(Identifier.of(BridgeConstants.MOD_ID, BridgeConstants.C2S_SENDOUT));
    public static final CustomPayload.Id<RawBridgePayload> MODEL_INTERACT_ID = new CustomPayload.Id<>(Identifier.of(BridgeConstants.MOD_ID, BridgeConstants.C2S_MODEL_INTERACT));
    public static final CustomPayload.Id<RawBridgePayload> BATTLE_ACTION_ID = new CustomPayload.Id<>(Identifier.of(BridgeConstants.MOD_ID, BridgeConstants.C2S_BATTLE_ACTION));
    public static final CustomPayload.Id<RawBridgePayload> PC_ACTION_ID = new CustomPayload.Id<>(Identifier.of(BridgeConstants.MOD_ID, BridgeConstants.C2S_PC_ACTION));
    public static final CustomPayload.Id<RawBridgePayload> STARTER_ACTION_ID = new CustomPayload.Id<>(Identifier.of(BridgeConstants.MOD_ID, BridgeConstants.C2S_STARTER_ACTION));

    public record RawBridgePayload(CustomPayload.Id<RawBridgePayload> id, byte[] bytes) implements CustomPayload {
        private static byte[] readRemaining(PacketByteBuf buf) {
            byte[] out = new byte[buf.readableBytes()];
            buf.readBytes(out);
            return out;
        }

        private static void writeRaw(RawBridgePayload value, PacketByteBuf buf) {
            buf.writeBytes(value.bytes());
        }

        public static final PacketCodec<PacketByteBuf, RawBridgePayload> PARTY_CODEC = PacketCodec.of(
                RawBridgePayload::writeRaw,
                buf -> new RawBridgePayload(SYNC_PARTY_ID, readRemaining(buf))
        );
        public static final PacketCodec<PacketByteBuf, RawBridgePayload> ENTITY_CODEC = PacketCodec.of(
                RawBridgePayload::writeRaw,
                buf -> new RawBridgePayload(SYNC_ENTITY_ID, readRemaining(buf))
        );
        public static final PacketCodec<PacketByteBuf, RawBridgePayload> REMOVE_CODEC = PacketCodec.of(
                RawBridgePayload::writeRaw,
                buf -> new RawBridgePayload(REMOVE_ENTITY_ID, readRemaining(buf))
        );
        public static final PacketCodec<PacketByteBuf, RawBridgePayload> BATTLE_STATE_CODEC = PacketCodec.of(
                RawBridgePayload::writeRaw,
                buf -> new RawBridgePayload(BATTLE_STATE_ID, readRemaining(buf))
        );
        public static final PacketCodec<PacketByteBuf, RawBridgePayload> PC_STATE_CODEC = PacketCodec.of(
                RawBridgePayload::writeRaw,
                buf -> new RawBridgePayload(PC_STATE_ID, readRemaining(buf))
        );
        public static final PacketCodec<PacketByteBuf, RawBridgePayload> STARTER_STATE_CODEC = PacketCodec.of(
                RawBridgePayload::writeRaw,
                buf -> new RawBridgePayload(STARTER_STATE_ID, readRemaining(buf))
        );
        public static final PacketCodec<PacketByteBuf, RawBridgePayload> HELLO_CODEC = PacketCodec.of(
                RawBridgePayload::writeRaw,
                buf -> new RawBridgePayload(HELLO_ID, readRemaining(buf))
        );
        public static final PacketCodec<PacketByteBuf, RawBridgePayload> SENDOUT_CODEC = PacketCodec.of(
                RawBridgePayload::writeRaw,
                buf -> new RawBridgePayload(SENDOUT_ID, readRemaining(buf))
        );
        public static final PacketCodec<PacketByteBuf, RawBridgePayload> MODEL_INTERACT_CODEC = PacketCodec.of(
                RawBridgePayload::writeRaw,
                buf -> new RawBridgePayload(MODEL_INTERACT_ID, readRemaining(buf))
        );
        public static final PacketCodec<PacketByteBuf, RawBridgePayload> BATTLE_ACTION_CODEC = PacketCodec.of(
                RawBridgePayload::writeRaw,
                buf -> new RawBridgePayload(BATTLE_ACTION_ID, readRemaining(buf))
        );
        public static final PacketCodec<PacketByteBuf, RawBridgePayload> PC_ACTION_CODEC = PacketCodec.of(
                RawBridgePayload::writeRaw,
                buf -> new RawBridgePayload(PC_ACTION_ID, readRemaining(buf))
        );
        public static final PacketCodec<PacketByteBuf, RawBridgePayload> STARTER_ACTION_CODEC = PacketCodec.of(
                RawBridgePayload::writeRaw,
                buf -> new RawBridgePayload(STARTER_ACTION_ID, readRemaining(buf))
        );

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return id;
        }

        public static RawBridgePayload hello() {
            return new RawBridgePayload(HELLO_ID, "hello".getBytes(StandardCharsets.UTF_8));
        }

        public static RawBridgePayload sendout(int slot, String ballId, double x, double y, double z, java.util.UUID targetEntityUuid) {
            String safeBall = (ballId == null || ballId.isBlank()) ? "poke_ball" : ballId;
            String target = targetEntityUuid == null ? "" : targetEntityUuid.toString();
            String body = slot + "|" + safeBall + "|" + x + "|" + y + "|" + z + "|" + target;
            return new RawBridgePayload(SENDOUT_ID, body.getBytes(StandardCharsets.UTF_8));
        }

        public static RawBridgePayload modelInteract(java.util.UUID entityUuid) {
            return new RawBridgePayload(MODEL_INTERACT_ID, entityUuid.toString().getBytes(StandardCharsets.UTF_8));
        }

        public static RawBridgePayload battleAction(String action) {
            return new RawBridgePayload(BATTLE_ACTION_ID, action.getBytes(StandardCharsets.UTF_8));
        }

        public static RawBridgePayload pcAction(String action) {
            return new RawBridgePayload(PC_ACTION_ID, action.getBytes(StandardCharsets.UTF_8));
        }

        public static RawBridgePayload starterAction(String action) {
            return new RawBridgePayload(STARTER_ACTION_ID, action.getBytes(StandardCharsets.UTF_8));
        }
    }

    private BridgePayloads() {}
}
