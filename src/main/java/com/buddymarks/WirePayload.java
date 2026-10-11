package com.buddymarks;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Small bounded JSON envelope shared by client and server. */
public record WirePayload(String json) implements CustomPayload {
    public static final Id<WirePayload> ID = new Id<>(Identifier.of(BuddyMarksMod.MOD_ID, "wire"));
    public static final PacketCodec<RegistryByteBuf, WirePayload> CODEC = PacketCodec.of(
            (payload, buf) -> buf.writeString(payload.json(), 32767),
            buf -> new WirePayload(buf.readString(32767))
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
