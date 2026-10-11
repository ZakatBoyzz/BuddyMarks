package com.buddymarks.server;

import com.buddymarks.BuddyMarksMod;
import com.buddymarks.WirePayload;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.Vec3d;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Server-authoritative data. Changes are written only after mutations, never every tick. */
public final class ServerData {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int MAX_FRIENDS = 144;
    private static final int MAX_MARKERS = 128;
    private static final int MAX_MARKERS_PER_SYNC_PACKET = 32;
    private static final int MAX_NAME_LENGTH = 40;
    private static final long RATE_WINDOW_MS = 1_000L;
    private static final int MAX_ACTIONS_PER_WINDOW = 20;

    private static JsonObject db = new JsonObject();
    private static Path databasePath;
    private static long tickCounter;
    private static final Map<UUID, RateWindow> RATE_LIMITS = new HashMap<>();

    private ServerData() {}

    public static void load(MinecraftServer server) {
        databasePath = server.getSavePath(WorldSavePath.ROOT).resolve("data").resolve("buddymarks.json");
        RATE_LIMITS.clear();
        tickCounter = 0L;
        try {
            Files.createDirectories(databasePath.getParent());
            if (Files.exists(databasePath)) {
                JsonElement parsed = com.google.gson.JsonParser.parseString(Files.readString(databasePath));
                db = parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject();
            } else {
                db = new JsonObject();
            }
            ensureRoots();
            pruneExpired(System.currentTimeMillis());
            save(server);
            BuddyMarksMod.LOGGER.info("Loaded BuddyMarks data from {}", databasePath);
        } catch (Exception exception) {
            BuddyMarksMod.LOGGER.error("Could not load BuddyMarks data; preserving a backup and starting with empty data", exception);
            try {
                if (databasePath != null && Files.exists(databasePath)) {
                    Path backup = databasePath.resolveSibling("buddymarks.json.broken-" + System.currentTimeMillis());
                    Files.copy(databasePath, backup, StandardCopyOption.REPLACE_EXISTING);
                    BuddyMarksMod.LOGGER.warn("Preserved unreadable BuddyMarks data at {}", backup);
                }
            } catch (IOException backupException) {
                BuddyMarksMod.LOGGER.warn("Could not back up unreadable BuddyMarks data", backupException);
            }
            db = new JsonObject();
            ensureRoots();
        }
    }

    public static void save(MinecraftServer server) {
        if (databasePath == null) return;
        try {
            Files.createDirectories(databasePath.getParent());
            Path temp = databasePath.resolveSibling(databasePath.getFileName() + ".tmp");
            Files.writeString(temp, GSON.toJson(db));
            try {
                Files.move(temp, databasePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temp, databasePath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            BuddyMarksMod.LOGGER.error("Could not save BuddyMarks data", exception);
        }
    }

    public static void tick(MinecraftServer server) {
        tickCounter++;
        if (tickCounter % 20L != 0L) return;
        long now = System.currentTimeMillis();
        if (pruneExpired(now)) {
            save(server);
            syncAll(server);
        }
        RATE_LIMITS.entrySet().removeIf(entry -> now - entry.getValue().windowStart > 10_000L);
    }

    public static void handle(MinecraftServer server, ServerPlayerEntity player, JsonObject message) {
        if (!allowAction(player.getUuid())) {
            notice(player, "Слишком много запросов. Подожди немного.");
            return;
        }
        String type = string(message, "type", 32);
        switch (type) {
            case "hello", "pull" -> {
                send(player, obj("type", "hello_ok", "protocol", "1", "serverNow", Long.toString(System.currentTimeMillis())));
                if ("hello".equals(type)) {
                    sendPendingRequests(player);
                    // A new connection changes online states for every existing friend.
                    syncAll(server);
                } else {
                    syncPlayer(player);
                    notice(player, "Данные друзей и меток обновлены.");
                }
            }
            case "friend_list" -> {
                send(player, obj("type", "hello_ok", "protocol", "1", "serverNow", Long.toString(System.currentTimeMillis())));
                sendFriendList(server, player);
                syncPlayer(player);
            }
            case "friend_add" -> friendAdd(server, player, string(message, "name", 16));
            case "friend_accept" -> friendAnswer(server, player, string(message, "name", 16), true);
            case "friend_deny" -> friendAnswer(server, player, string(message, "name", 16), false);
            case "friend_remove" -> friendRemove(server, player, string(message, "name", 16));
            case "marker_temp" -> createMarker(server, player, message, true);
            case "marker_main" -> createMarker(server, player, message, false);
            case "marker_remove" -> removeMarker(server, player, string(message, "id", 64));
            case "marker_mode" -> changeMarkerMode(server, player, message);
            case "marker_style" -> changeMarkerStyle(server, player, message);
            default -> notice(player, "Неизвестная команда BuddyMarks.");
        }
    }

    private static boolean allowAction(UUID uuid) {
        long now = System.currentTimeMillis();
        RateWindow window = RATE_LIMITS.computeIfAbsent(uuid, ignored -> new RateWindow(now));
        if (now - window.windowStart >= RATE_WINDOW_MS) {
            window.windowStart = now;
            window.count = 0;
        }
        return ++window.count <= MAX_ACTIONS_PER_WINDOW;
    }

    private static void sendPendingRequests(ServerPlayerEntity player) {
        JsonObject pending = playerObject("requests", player.getUuid());
        for (Map.Entry<String, JsonElement> entry : pending.entrySet()) {
            String requesterId = entry.getKey();
            String requesterName;
            try { requesterName = entry.getValue().getAsString(); }
            catch (Exception ignored) { continue; }
            send(player, obj("type", "friend_request", "uuid", requesterId, "name", requesterName));
        }
    }


    private static void sendFriendList(MinecraftServer server, ServerPlayerEntity player) {
        Set<String> memberIds = teamMembers(player.getUuid());
        memberIds.remove(player.getUuid().toString());
        if (memberIds.isEmpty()) {
            notice(player, "Список команды пуст (0/144). Используй .friend add НикИгрока.");
            return;
        }
        List<String> rows = new ArrayList<>();
        for (String memberId : memberIds) {
            try {
                UUID memberUuid = UUID.fromString(memberId);
                ServerPlayerEntity onlinePlayer = server.getPlayerManager().getPlayer(memberUuid);
                String name = resolveName(server, memberUuid);
                rows.add((onlinePlayer == null ? "○ " : "● ") + name);
            } catch (IllegalArgumentException ignored) { }
        }
        rows.sort(String.CASE_INSENSITIVE_ORDER);
        notice(player, "Тиммейты (" + rows.size() + "/" + MAX_FRIENDS + "): " + String.join(", ", rows.subList(0, Math.min(12, rows.size()))));
        for (int start = 12; start < rows.size(); start += 12) {
            notice(player, String.join(", ", rows.subList(start, Math.min(start + 12, rows.size()))));
        }
    }

    private static void friendAdd(MinecraftServer server, ServerPlayerEntity sender, String targetName) {
        if (targetName.isBlank()) { notice(sender, "Использование: .friend add НикИгрока"); return; }
        if (sender.getName().getString().equalsIgnoreCase(targetName)) { notice(sender, "Нельзя добавить самого себя."); return; }
        ServerPlayerEntity target = server.getPlayerManager().getPlayer(targetName);
        if (target == null) { notice(sender, "Игрок должен быть в сети, чтобы отправить ему приглашение."); return; }
        UUID from = sender.getUuid();
        UUID to = target.getUuid();
        JsonObject ownFriends = playerObject("friends", from);
        JsonObject otherFriends = playerObject("friends", to);
        if (ownFriends.has(to.toString())) { notice(sender, "Этот игрок уже в списке друзей."); return; }
        if (teamMembers(from).contains(to.toString())) { notice(sender, "Этот игрок уже состоит с тобой в общей команде."); return; }
        if (ownFriends.size() >= MAX_FRIENDS || otherFriends.size() >= MAX_FRIENDS) {
            notice(sender, "Лимит друзей (144) достигнут у одного из игроков."); return;
        }
        JsonObject pending = playerObject("requests", to);
        if (pending.size() >= MAX_FRIENDS) { notice(sender, "У игрока слишком много ожидающих запросов."); return; }
        if (pending.has(from.toString())) { notice(sender, "Запрос этому игроку уже отправлен."); return; }
        if (playerObject("requests", from).has(to.toString())) {
            notice(sender, "У тебя уже есть запрос от этого игрока. Используй .friend accept " + targetName);
            return;
        }
        pending.addProperty(from.toString(), sender.getName().getString());
        save(server);
        send(target, obj("type", "friend_request", "uuid", from.toString(), "name", sender.getName().getString()));
        notice(sender, "Запрос в друзья отправлен игроку " + target.getName().getString() + ".");
    }

    private static void friendAnswer(MinecraftServer server, ServerPlayerEntity player, String requestName, boolean accept) {
        if (requestName.isBlank()) { notice(player, "Укажи ник отправителя запроса."); return; }
        UUID recipientId = player.getUuid();
        JsonObject pending = playerObject("requests", recipientId);
        String requesterId = null;
        String requesterName = null;
        for (Map.Entry<String, JsonElement> entry : pending.entrySet()) {
            if (entry.getValue().getAsString().equalsIgnoreCase(requestName) || entry.getKey().equalsIgnoreCase(requestName)) {
                requesterId = entry.getKey();
                requesterName = entry.getValue().getAsString();
                break;
            }
        }
        if (requesterId == null) { notice(player, "Ожидающий запрос от этого игрока не найден."); return; }
        UUID requesterUuid;
        try { requesterUuid = UUID.fromString(requesterId); }
        catch (IllegalArgumentException invalidId) { pending.remove(requesterId); save(server); notice(player, "Запрос повреждён и удалён."); return; }
        if (accept) {
            JsonObject ownFriends = playerObject("friends", recipientId);
            JsonObject requesterFriends = playerObject("friends", requesterUuid);
            if (teamMembers(recipientId).contains(requesterId)) {
                pending.remove(requesterId);
                save(server);
                syncAll(server);
                notice(player, "Этот игрок уже состоит с тобой в общей команде.");
                return;
            }
            Set<String> combinedTeam = teamMembers(recipientId);
            combinedTeam.addAll(teamMembers(requesterUuid));
            if (combinedTeam.size() > MAX_FRIENDS + 1) {
                notice(player, "Объединение превысило бы лимит команды в 144 игрока.");
                ServerPlayerEntity requester = server.getPlayerManager().getPlayer(requesterUuid);
                if (requester != null) notice(requester, "Запрос не принят: объединённая команда превысила бы лимит в 144 игрока.");
                return;
            }
            if (ownFriends.size() >= MAX_FRIENDS || requesterFriends.size() >= MAX_FRIENDS) {
                notice(player, "Лимит друзей (144) достигнут у одного из игроков."); return;
            }
            ownFriends.addProperty(requesterId, requesterName);
            requesterFriends.addProperty(recipientId.toString(), player.getName().getString());
            notice(player, "Теперь вы друзья с " + requesterName + ".");
            ServerPlayerEntity requester = server.getPlayerManager().getPlayer(requesterUuid);
            if (requester != null) notice(requester, player.getName().getString() + " принял(а) запрос в друзья.");
        } else {
            notice(player, "Запрос от " + requesterName + " отклонён.");
            ServerPlayerEntity requester = server.getPlayerManager().getPlayer(requesterUuid);
            if (requester != null) notice(requester, player.getName().getString() + " отклонил(а) запрос в друзья.");
        }
        pending.remove(requesterId);
        save(server);
        syncAll(server);
    }

    private static void friendRemove(MinecraftServer server, ServerPlayerEntity sender, String targetName) {
        if (targetName.isBlank()) { notice(sender, "Использование: .friend remove НикИгрока"); return; }
        UUID targetId = findTeamMemberByName(server, sender.getUuid(), targetName);
        if (targetId == null) { notice(sender, "Игрок не найден в твоей команде."); return; }
        Set<String> oldTeam = teamMembers(sender.getUuid());
        String resolvedName = resolveName(server, targetId);
        // Removing a teammate clears every relation linking that player to this shared component.
        for (String memberId : oldTeam) {
            try {
                UUID memberUuid = UUID.fromString(memberId);
                playerObject("friends", memberUuid).remove(targetId.toString());
                playerObject("friends", targetId).remove(memberId);
            } catch (IllegalArgumentException ignored) { }
        }
        save(server);
        notice(sender, "Игрок " + resolvedName + " удалён из твоей команды.");
        ServerPlayerEntity target = server.getPlayerManager().getPlayer(targetId);
        if (target != null) notice(target, sender.getName().getString() + " удалил(а) тебя из общей команды.");
        syncAll(server);
    }

    private static UUID findTeamMemberByName(MinecraftServer server, UUID owner, String name) {
        for (String candidate : teamMembers(owner)) {
            if (candidate.equals(owner.toString())) continue;
            try {
                UUID id = UUID.fromString(candidate);
                if (candidate.equalsIgnoreCase(name) || resolveName(server, id).equalsIgnoreCase(name)) return id;
            } catch (IllegalArgumentException ignored) { }
        }
        return null;
    }

    private static String resolveName(MinecraftServer server, UUID id) {
        ServerPlayerEntity online = server == null ? null : server.getPlayerManager().getPlayer(id);
        if (online != null) return online.getName().getString();

        // Mutual links retain the offline player's last-known name in each neighbor's entry.
        JsonObject ownLinks = playerObject("friends", id);
        JsonObject friendsRoot = rootObject("friends");
        for (String neighborId : ownLinks.keySet()) {
            JsonElement neighborElement = friendsRoot.get(neighborId);
            if (neighborElement == null || !neighborElement.isJsonObject()) continue;
            JsonElement data = neighborElement.getAsJsonObject().get(id.toString());
            if (data != null && data.isJsonPrimitive()) {
                try { return data.getAsString(); } catch (Exception ignored) { }
            }
        }
        return id.toString();
    }

    /** Connected friend relations form one shared team, so all linked members see each other's markers. */
    private static Set<String> teamMembers(UUID start) {
        Set<String> visited = new HashSet<>();
        List<String> queue = new ArrayList<>();
        String startId = start.toString();
        visited.add(startId);
        queue.add(startId);
        JsonObject friendsRoot = rootObject("friends");
        for (int cursor = 0; cursor < queue.size() && visited.size() <= MAX_FRIENDS + 1; cursor++) {
            JsonElement node = friendsRoot.get(queue.get(cursor));
            if (node == null || !node.isJsonObject()) continue;
            for (Map.Entry<String, JsonElement> entry : node.getAsJsonObject().entrySet()) {
                String friendId = entry.getKey();
                try { UUID.fromString(friendId); } catch (IllegalArgumentException ignored) { continue; }
                if (visited.add(friendId)) queue.add(friendId);
                if (visited.size() > MAX_FRIENDS + 1) return visited;
            }
        }
        return visited;
    }

    private static void createMarker(MinecraftServer server, ServerPlayerEntity player, JsonObject request, boolean temporary) {
        JsonArray markers = array("markers");
        if (markers.size() >= MAX_MARKERS) { notice(player, "Достигнут лимит общих меток на этом мире."); return; }
        double x = number(request, "x", player.getX());
        double y = number(request, "y", player.getY());
        double z = number(request, "z", player.getZ());
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
            || y < -64 || y > 384
                || new Vec3d(x, y, z).squaredDistanceTo(player.getPos()) > 256.0 * 256.0) {
            notice(player, "Недопустимая координата метки (максимум 256 блоков от тебя)."); return;
        }
        long now = System.currentTimeMillis();
        long duration = (long) number(request, "duration", temporary ? 10 : 3600);
        duration = Math.max(1, Math.min(duration, temporary ? 3600 : 604800));
        boolean manual = !temporary && bool(request, "manual", false);
        boolean unlimited = !temporary && bool(request, "unlimited", false);
        String name = temporary ? "" : string(request, "name", MAX_NAME_LENGTH).trim();
        if (!temporary && name.isBlank()) name = "Метка";
        long expiresAt = manual || unlimited ? -1L : now + duration * 1000L;
        JsonObject marker = new JsonObject();
        marker.addProperty("id", UUID.randomUUID().toString());
        marker.addProperty("owner", player.getUuid().toString());
        marker.addProperty("ownerName", player.getName().getString());
        marker.addProperty("dimension", player.getWorld().getRegistryKey().getValue().toString());
        marker.addProperty("x", x);
        marker.addProperty("y", y);
        marker.addProperty("z", z);
        marker.addProperty("name", name);
        marker.addProperty("temporary", temporary);
        marker.addProperty("expiresAt", expiresAt);
        marker.addProperty("manual", manual);
        marker.addProperty("unlimited", unlimited);
        marker.addProperty("durationSeconds", unlimited ? 0L : duration);
        int fallbackColor = temporary ? 0x39D7FF : 0xFFBE47;
        long suppliedColor = (long) number(request, "color", fallbackColor);
        marker.addProperty("color", (int) Math.max(0L, Math.min(0xFFFFFFL, suppliedColor)));
        int markerSize = (int) number(request, "size", 100);
        marker.addProperty("size", Math.max(50, Math.min(200, markerSize)));
        markers.add(marker);
        save(server);
        notice(player, temporary ? "Временная метка поставлена." : "Основная метка создана.");
        syncAll(server);
    }

    private static void removeMarker(MinecraftServer server, ServerPlayerEntity player, String markerId) {
        JsonArray markers = array("markers");
        boolean removed = false;
        for (int i = 0; i < markers.size(); i++) {
            JsonObject marker = markers.get(i).getAsJsonObject();
            if (!markerId.equals(marker.get("id").getAsString())) continue;
            String owner = marker.get("owner").getAsString();
            boolean ownerOrTeammate = teamMembers(player.getUuid()).contains(owner);
            if (!ownerOrTeammate) { notice(player, "У тебя нет прав удалить эту метку."); return; }
            markers.remove(i);
            removed = true;
            break;
        }
        if (removed) {
            save(server);
            notice(player, "Метка удалена.");
            syncAll(server);
        } else notice(player, "Метка не найдена или уже удалена.");
    }

    private static void changeMarkerMode(MinecraftServer server, ServerPlayerEntity player, JsonObject request) {
        String id = string(request, "id", 64);
        boolean manual = bool(request, "manual", false);
        JsonArray markers = array("markers");
        for (JsonElement element : markers) {
            JsonObject marker = element.getAsJsonObject();
            if (!id.equals(string(marker, "id", 64))) continue;
            if (bool(marker, "temporary", false)) { notice(player, "У временной метки нельзя менять режим срока."); return; }
            if (!canEditMarker(player, marker)) { notice(player, "У тебя нет прав изменять эту метку."); return; }
            boolean unlimited = bool(marker, "unlimited", false);
            long durationSeconds = (long) number(marker, "durationSeconds", 0L);
            marker.addProperty("manual", manual);
            long expiresAt = manual || unlimited || durationSeconds <= 0
                    ? -1L : System.currentTimeMillis() + Math.min(durationSeconds, 604800L) * 1000L;
            marker.addProperty("expiresAt", expiresAt);
            save(server);
            notice(player, manual ? "Метка теперь удаляется только вручную." : "Для метки снова включён срок существования.");
            syncAll(server);
            return;
        }
        notice(player, "Метка не найдена или уже удалена.");
    }

    private static void changeMarkerStyle(MinecraftServer server, ServerPlayerEntity player, JsonObject request) {
        String id = string(request, "id", 64);
        int color = (int) Math.max(0L, Math.min(0xFFFFFFL, (long) number(request, "color", 0xFFBE47)));
        int size = (int) Math.max(50L, Math.min(200L, (long) number(request, "size", 100)));
        for (JsonElement element : array("markers")) {
            JsonObject marker = element.getAsJsonObject();
            if (!id.equals(string(marker, "id", 64))) continue;
            if (bool(marker, "temporary", false)) { notice(player, "Стиль временной метки не редактируется."); return; }
            if (!canEditMarker(player, marker)) { notice(player, "У тебя нет прав изменять эту метку."); return; }
            marker.addProperty("color", color);
            marker.addProperty("size", size);
            save(server);
            notice(player, "Цвет и размер основной метки обновлены.");
            syncAll(server);
            return;
        }
        notice(player, "Метка не найдена или уже удалена.");
    }

    private static boolean canEditMarker(ServerPlayerEntity player, JsonObject marker) {
        String owner = string(marker, "owner", 64);
        return teamMembers(player.getUuid()).contains(owner);
    }

    public static void syncAll(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) syncPlayer(player);
    }

    public static void syncPlayer(ServerPlayerEntity player) {
        Set<String> teamIds = teamMembers(player.getUuid());
        JsonArray friends = new JsonArray();
        MinecraftServer server = player.getServer();
        for (String memberId : teamIds) {
            if (memberId.equals(player.getUuid().toString())) continue;
            JsonObject row = new JsonObject();
            row.addProperty("uuid", memberId);
            String displayName = memberId;
            boolean online = false;
            try {
                UUID memberUuid = UUID.fromString(memberId);
                displayName = resolveName(server, memberUuid);
                ServerPlayerEntity onlineMember = server == null ? null : server.getPlayerManager().getPlayer(memberUuid);
                online = onlineMember != null;
            } catch (IllegalArgumentException ignored) { }
            row.addProperty("name", displayName);
            row.addProperty("online", online);
            friends.add(row);
        }
        List<JsonObject> visibleMarkers = new ArrayList<>();
        JsonArray allMarkers = array("markers");
        for (JsonElement element : allMarkers) {
            JsonObject marker = element.getAsJsonObject();
            String ownerId = marker.get("owner").getAsString();
            String dimension = marker.has("dimension") ? marker.get("dimension").getAsString() : "minecraft:overworld";
            if (dimension.equals(player.getWorld().getRegistryKey().getValue().toString()) && teamIds.contains(ownerId)) {
                visibleMarkers.add(marker.deepCopy());
            }
        }
        if (visibleMarkers.isEmpty()) {
            JsonObject packet = new JsonObject();
            packet.addProperty("type", "sync");
            packet.addProperty("replaceMarkers", true);
            packet.add("friends", friends);
            packet.add("markers", new JsonArray());
            send(player, packet);
            return;
        }
        for (int start = 0; start < visibleMarkers.size(); start += MAX_MARKERS_PER_SYNC_PACKET) {
            JsonObject packet = new JsonObject();
            packet.addProperty("type", "sync");
            packet.addProperty("replaceMarkers", start == 0);
            if (start == 0) packet.add("friends", friends);
            JsonArray chunk = new JsonArray();
            for (int i = start; i < Math.min(start + MAX_MARKERS_PER_SYNC_PACKET, visibleMarkers.size()); i++) chunk.add(visibleMarkers.get(i));
            packet.add("markers", chunk);
            send(player, packet);
        }
    }

    public static void notice(ServerPlayerEntity player, String message) {
        send(player, obj("type", "notice", "message", message));
    }

    private static void send(ServerPlayerEntity player, JsonObject message) {
        if (ServerPlayNetworking.canSend(player, WirePayload.ID)) {
            ServerPlayNetworking.send(player, new WirePayload(message.toString()));
        }
    }

    private static boolean pruneExpired(long now) {
        JsonArray markers = array("markers");
        boolean changed = false;
        for (int i = markers.size() - 1; i >= 0; i--) {
            JsonObject marker = markers.get(i).getAsJsonObject();
            long expiresAt = marker.has("expiresAt") ? marker.get("expiresAt").getAsLong() : -1L;
            if (expiresAt >= 0 && expiresAt <= now) {
                markers.remove(i);
                changed = true;
            }
        }
        return changed;
    }

    private static void ensureRoots() {
        if (!db.has("friends") || !db.get("friends").isJsonObject()) db.add("friends", new JsonObject());
        if (!db.has("requests") || !db.get("requests").isJsonObject()) db.add("requests", new JsonObject());
        if (!db.has("markers") || !db.get("markers").isJsonArray()) db.add("markers", new JsonArray());
    }

    private static JsonObject rootObject(String key) {
        ensureRoots();
        return db.getAsJsonObject(key);
    }

    private static JsonObject playerObject(String section, UUID player) {
        JsonObject parent = rootObject(section);
        JsonElement existing = parent.get(player.toString());
        if (existing != null && existing.isJsonObject()) return existing.getAsJsonObject();
        JsonObject created = new JsonObject();
        parent.add(player.toString(), created);
        return created;
    }

    private static JsonArray array(String key) {
        ensureRoots();
        return db.getAsJsonArray(key);
    }

    private static String string(JsonObject object, String key, int maxLength) {
        if (!object.has(key) || object.get(key).isJsonNull()) return "";
        String value = object.get(key).getAsString();
        value = value.replaceAll("[\\p{Cntrl}]", "").trim();
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }

    private static double number(JsonObject object, String key, double fallback) {
        try { return object.has(key) ? object.get(key).getAsDouble() : fallback; }
        catch (Exception ignored) { return fallback; }
    }

    private static boolean bool(JsonObject object, String key, boolean fallback) {
        try { return object.has(key) ? object.get(key).getAsBoolean() : fallback; }
        catch (Exception ignored) { return fallback; }
    }

    private static JsonObject obj(String... pairs) {
        JsonObject result = new JsonObject();
        for (int i = 0; i + 1 < pairs.length; i += 2) result.addProperty(pairs[i], pairs[i + 1]);
        return result;
    }

    private static final class RateWindow {
        long windowStart;
        int count;
        RateWindow(long windowStart) { this.windowStart = windowStart; }
    }
}
