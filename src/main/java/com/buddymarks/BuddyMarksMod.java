package com.buddymarks;

import com.buddymarks.server.ServerData;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BuddyMarksMod implements ModInitializer {
    public static final String MOD_ID = "buddymarks";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.playC2S().register(WirePayload.ID, WirePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(WirePayload.ID, WirePayload.CODEC);

        ServerLifecycleEvents.SERVER_STARTED.register(ServerData::load);
        ServerLifecycleEvents.SERVER_STOPPING.register(ServerData::save);
        ServerTickEvents.END_SERVER_TICK.register(ServerData::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                server.execute(() -> ServerData.syncAll(server)));
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> ServerData.syncPlayer(player));

        ServerPlayNetworking.registerGlobalReceiver(WirePayload.ID, (payload, context) -> {
            MinecraftServer server = context.server();
            ServerPlayerEntity player = context.player();
            // Fabric runs this callback on the server thread for packet-object handlers.
            try {
                JsonObject message = JsonParser.parseString(payload.json()).getAsJsonObject();
                ServerData.handle(server, player, message);
            } catch (Exception exception) {
                LOGGER.warn("Ignored malformed BuddyMarks packet from {}", player.getName().getString());
                ServerData.notice(player, "Некорректный запрос BuddyMarks проигнорирован.");
            }
        });

        LOGGER.info("BuddyMarks common/server code initialized");
    }
}
