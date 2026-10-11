package com.buddymarks.client;

import com.buddymarks.BuddyMarksMod;
import com.buddymarks.WirePayload;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ClientState {
    public static final Map<String, FriendInfo> FRIENDS = new LinkedHashMap<>();
    public static final Map<String, MarkerInfo> MARKERS = new LinkedHashMap<>();
    public static boolean serverHasMod = false;
    private static long serverTimeOffsetMillis = 0L;

    public static long serverNow() {
        return System.currentTimeMillis() + serverTimeOffsetMillis;
    }

    public static void resetServerTimeOffset() {
        serverTimeOffsetMillis = 0L;
    }

    private ClientState() {}

    public static boolean isFriend(UUID uuid) {
        return uuid != null && FRIENDS.containsKey(uuid.toString());
    }

    public static List<FriendInfo> sortedFriends(String filter) {
        String needle = filter == null ? "" : filter.toLowerCase(java.util.Locale.ROOT).trim();
        List<FriendInfo> result = new ArrayList<>();
        for (FriendInfo friend : FRIENDS.values()) {
            if (friend.name().toLowerCase(java.util.Locale.ROOT).contains(needle)) result.add(friend);
        }
        result.sort(Comparator.comparing(FriendInfo::name, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    public static void handle(String raw) {
        try {
            JsonObject packet = JsonParser.parseString(raw).getAsJsonObject();
            switch (packet.has("type") ? packet.get("type").getAsString() : "") {
                case "notice" -> notifyPlayer(packet.has("message") ? packet.get("message").getAsString() : "BuddyMarks");
                case "friend_request" -> showRequest(packet);
                case "sync" -> applySync(packet);
                case "hello_ok" -> {
                    String protocol = packet.has("protocol") ? packet.get("protocol").getAsString() : "";
                    if (!"1".equals(protocol)) {
                        serverHasMod = false;
                        FRIENDS.clear();
                        MARKERS.clear();
                        notifyPlayer("Несовместимая версия протокола BuddyMarks на сервере.");
                    } else {
                        serverHasMod = true;
                        if (packet.has("serverNow")) {
                            try { serverTimeOffsetMillis = packet.get("serverNow").getAsLong() - System.currentTimeMillis(); }
                            catch (Exception ignored) { serverTimeOffsetMillis = 0L; }
                        }
                    }
                }
                default -> BuddyMarksMod.LOGGER.debug("Unknown BuddyMarks server message");
            }
        } catch (Exception exception) {
            BuddyMarksMod.LOGGER.warn("Ignored malformed BuddyMarks server packet", exception);
        }
    }

    private static void applySync(JsonObject packet) {
        serverHasMod = true;
        if (packet.has("friends") && packet.get("friends").isJsonArray()) {
            FRIENDS.clear();
            JsonArray friends = packet.getAsJsonArray("friends");
            for (JsonElement element : friends) {
                try {
                    JsonObject row = element.getAsJsonObject();
                    String uuid = row.get("uuid").getAsString();
                    String name = row.get("name").getAsString();
                    UUID.fromString(uuid);
                    boolean online = row.has("online") && row.get("online").getAsBoolean();
                    FRIENDS.put(uuid, new FriendInfo(uuid, name, online));
                } catch (Exception ignored) { }
            }
        }
        if (packet.has("replaceMarkers") && packet.get("replaceMarkers").getAsBoolean()) MARKERS.clear();
        if (packet.has("markers") && packet.get("markers").isJsonArray()) {
            for (JsonElement element : packet.getAsJsonArray("markers")) {
                try {
                    JsonObject marker = element.getAsJsonObject();
                    MarkerInfo parsed = MarkerInfo.fromJson(marker);
                    if (parsed != null) MARKERS.put(parsed.id(), parsed);
                } catch (Exception ignored) { }
            }
        }
    }

    private static void showRequest(JsonObject packet) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        String name = packet.has("name") ? packet.get("name").getAsString() : "Неизвестный игрок";
        Text lead = Text.literal("Игрок «" + name + "» хочет добавить вас в друзья").formatted(Formatting.YELLOW);
        Text accept = Text.literal("«Принять»").setStyle(Style.EMPTY.withColor(Formatting.GREEN).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/friend accept " + name))
                .withHoverEvent(new net.minecraft.text.HoverEvent(net.minecraft.text.HoverEvent.Action.SHOW_TEXT, Text.literal("Принять запрос"))));
        Text separator = Text.literal("          |          ").formatted(Formatting.GRAY);
        Text deny = Text.literal("«Отклонить»").setStyle(Style.EMPTY.withColor(Formatting.RED).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/friend deny " + name))
                .withHoverEvent(new net.minecraft.text.HoverEvent(net.minecraft.text.HoverEvent.Action.SHOW_TEXT, Text.literal("Отклонить запрос"))));
        client.player.sendMessage(lead, false);
        client.player.sendMessage(accept.copy().append(separator).append(deny), false);
    }

    private static void notifyPlayer(String message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) client.player.sendMessage(Text.literal("[BuddyMarks] ").formatted(Formatting.AQUA).append(Text.literal(message)), false);
    }

    public static void send(JsonObject payload) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getNetworkHandler() == null || !ClientPlayNetworking.canSend(WirePayload.ID)) {
            notifyPlayer("Сервер не поддерживает BuddyMarks. Для общих друзей и меток мод должен быть установлен на сервере.");
            return;
        }
        ClientPlayNetworking.send(new WirePayload(payload.toString()));
    }

    public static JsonObject message(String type) {
        JsonObject json = new JsonObject();
        json.addProperty("type", type);
        return json;
    }

    public record FriendInfo(String uuid, String name, boolean online) {}

    public record MarkerInfo(String id, String owner, String ownerName, String dimension, double x, double y, double z,
                             String name, boolean temporary, long expiresAt, boolean manual, int color, int size) {
        static MarkerInfo fromJson(JsonObject json) {
            if (!json.has("id") || !json.has("owner")) return null;
            return new MarkerInfo(
                    json.get("id").getAsString(), json.get("owner").getAsString(),
                    json.has("ownerName") ? json.get("ownerName").getAsString() : "",
                    json.has("dimension") ? json.get("dimension").getAsString() : "minecraft:overworld",
                    json.get("x").getAsDouble(), json.get("y").getAsDouble(), json.get("z").getAsDouble(),
                    json.has("name") ? json.get("name").getAsString() : "",
                    json.has("temporary") && json.get("temporary").getAsBoolean(),
                    json.has("expiresAt") ? json.get("expiresAt").getAsLong() : -1L,
                    json.has("manual") && json.get("manual").getAsBoolean(),
                    json.has("color") ? json.get("color").getAsInt() : 0x39D7FF,
                    json.has("size") ? Math.max(50, Math.min(200, json.get("size").getAsInt())) : 100
            );
        }
    }
}
