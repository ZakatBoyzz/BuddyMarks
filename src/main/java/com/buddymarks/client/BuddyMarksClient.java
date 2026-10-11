package com.buddymarks.client;

import com.buddymarks.BuddyMarksMod;
import com.buddymarks.WirePayload;
import com.buddymarks.client.render.MarkerRenderer;
import com.buddymarks.client.screen.BuddyScreen;
import com.buddymarks.client.screen.MarkerScreen;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public final class BuddyMarksClient implements ClientModInitializer {
    private static KeyBinding openMenuKey;
    private static KeyBinding temporaryMarkerKey;
    private static KeyBinding mainMarkerKey;

    @Override
    public void onInitializeClient() {
        ClientConfig.load();

        openMenuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.buddymarks.open_menu",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_O,
                "category.buddymarks"
        ));

        temporaryMarkerKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.buddymarks.temporary_marker",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_B,
                "category.buddymarks"
        ));

        mainMarkerKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.buddymarks.main_marker",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_N,
                "category.buddymarks"
        ));

        ClientPlayNetworking.registerGlobalReceiver(WirePayload.ID, (payload, context) ->
                context.client().execute(() -> ClientState.handle(payload.json()))
        );

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            ClientState.serverHasMod = false;
            ClientState.resetServerTimeOffset();
            ClientState.FRIENDS.clear();
            ClientState.MARKERS.clear();

            if (ClientPlayNetworking.canSend(WirePayload.ID)) {
                ClientState.send(ClientState.message("hello"));
            } else if (client.player != null) {
                client.player.sendMessage(
                        Text.literal("[BuddyMarks] На сервере нет BuddyMarks: общие друзья и метки работать не будут."),
                        false
                );
            }
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ClientState.serverHasMod = false;
            ClientState.resetServerTimeOffset();
            ClientState.FRIENDS.clear();
            ClientState.MARKERS.clear();
        });

        ClientSendMessageEvents.ALLOW_CHAT.register(message -> {
            String trimmed = message.trim();

            if (!(trimmed.equalsIgnoreCase(".friend")
                    || trimmed.regionMatches(true, 0, ".friend ", 0, 8))) {
                return true;
            }

            handleFriendCommand(trimmed);
            return false;
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(
                        ClientCommandManager.literal("friend")
                                .then(ClientCommandManager.literal("add")
                                        .then(ClientCommandManager.argument("name", StringArgumentType.word())
                                                .executes(context -> {
                                                    sendFriendAction(
                                                            "friend_add",
                                                            StringArgumentType.getString(context, "name")
                                                    );
                                                    return 1;
                                                })))
                                .then(ClientCommandManager.literal("remove")
                                        .then(ClientCommandManager.argument("name", StringArgumentType.word())
                                                .executes(context -> {
                                                    sendFriendAction(
                                                            "friend_remove",
                                                            StringArgumentType.getString(context, "name")
                                                    );
                                                    return 1;
                                                })))
                                .then(ClientCommandManager.literal("accept")
                                        .then(ClientCommandManager.argument("name", StringArgumentType.word())
                                                .executes(context -> {
                                                    sendFriendAction(
                                                            "friend_accept",
                                                            StringArgumentType.getString(context, "name")
                                                    );
                                                    return 1;
                                                })))
                                .then(ClientCommandManager.literal("deny")
                                        .then(ClientCommandManager.argument("name", StringArgumentType.word())
                                                .executes(context -> {
                                                    sendFriendAction(
                                                            "friend_deny",
                                                            StringArgumentType.getString(context, "name")
                                                    );
                                                    return 1;
                                                })))
                                .then(ClientCommandManager.literal("list")
                                        .executes(context -> {
                                            ClientState.send(ClientState.message("friend_list"));
                                            return 1;
                                        }))
                                .then(ClientCommandManager.literal("help")
                                        .executes(context -> {
                                            notifyText("Команды: .friend add Ник | .friend list | .friend remove Ник | .friend accept Ник | .friend deny Ник");
                                            return 1;
                                        }))
                                .executes(context -> {
                                    notifyText("Используй /friend add/list/remove/accept/deny/help.");
                                    return 1;
                                })
                )
        );

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!world.isClient || !ClientConfig.INSTANCE.blockTeammateHits) {
                return ActionResult.PASS;
            }

            if (entity instanceof PlayerEntity target
                    && ClientState.isFriend(target.getUuid())) {
                return ActionResult.FAIL;
            }

            return ActionResult.PASS;
        });

        MarkerRenderer.register();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            boolean openPressed = false;
            boolean tempPressed = false;
            boolean mainPressed = false;

            while (openMenuKey.wasPressed()) {
                openPressed = true;
            }

            while (temporaryMarkerKey.wasPressed()) {
                tempPressed = true;
            }

            while (mainMarkerKey.wasPressed()) {
                mainPressed = true;
            }

            if (client.player == null || client.currentScreen != null) {
                return;
            }

            if (openPressed) {
                client.setScreen(new BuddyScreen(null));
            } else if (tempPressed) {
                createTemporaryMarker(client);
            } else if (mainPressed) {
                client.setScreen(new MarkerScreen(null));
            }
        });

        BuddyMarksMod.LOGGER.info("BuddyMarks client initialized");
    }

    private static void handleFriendCommand(String input) {
        String[] args = input.trim().split("\\s+");

        if (args.length == 1
                || (args.length > 1 && args[1].equalsIgnoreCase("help"))) {
            notifyText("Команды: .friend add Ник | .friend list | .friend remove Ник | .friend accept Ник | .friend deny Ник");
            return;
        }

        if (args.length == 2 && args[1].equalsIgnoreCase("list")) {
            ClientState.send(ClientState.message("friend_list"));
            return;
        }

        if (args.length < 3) {
            notifyText("Формат: .friend add/remove/accept/deny Ник");
            return;
        }

        String action = args[1].toLowerCase(java.util.Locale.ROOT);
        String name = args[2];

        switch (action) {
            case "add" -> sendFriendAction("friend_add", name);
            case "remove" -> sendFriendAction("friend_remove", name);
            case "accept" -> sendFriendAction("friend_accept", name);
            case "deny" -> sendFriendAction("friend_deny", name);
            default -> notifyText("Неизвестное действие. Используй .friend help");
        }
    }

    private static void sendFriendAction(String type, String name) {
        JsonObject request = ClientState.message(type);
        request.addProperty("name", name);
        ClientState.send(request);
    }

    public static void notifyText(String text) {
        MinecraftClient client = MinecraftClient.getInstance();

        if (client.player != null) {
            client.player.sendMessage(
                    Text.literal("[BuddyMarks] " + text),
                    false
            );
        }
    }
}
