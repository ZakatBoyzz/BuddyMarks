package com.buddymarks.client.screen;

import com.buddymarks.client.ClientConfig;
import com.buddymarks.client.ClientState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

public final class BuddyScreen extends Screen {
    private final Screen parent;
    private TextFieldWidget searchField;
    private TextFieldWidget addField;
    private int scrollOffset;
    private int listRight;
    private int controlX;
    private int controlWidth;

    public BuddyScreen(Screen parent) {
        super(Text.literal("BuddyMarks — друзья"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int left = 24;
        listRight = Math.min(350, width / 2 - 8);
        listRight = Math.max(left + 110, listRight);
        controlWidth = Math.min(222, Math.max(110, width - listRight - 48));
        controlX = width - 24 - controlWidth;
        int listWidth = Math.max(100, listRight - left);
        searchField = addDrawableChild(new TextFieldWidget(textRenderer, left, 45, listWidth, 20, Text.literal("Поиск")));
        searchField.setPlaceholder(Text.literal("Найти друга"));
        addField = addDrawableChild(new TextFieldWidget(textRenderer, left, height - 51, listWidth, 20, Text.literal("Ник")));
        addField.setMaxLength(16);
        addField.setPlaceholder(Text.literal("Ник игрока"));
        addDrawableChild(ButtonWidget.builder(Text.literal("Добавить друга"), button -> {
            String name = addField.getText().trim();
            if (!name.isBlank()) {
                var json = ClientState.message("friend_add");
                json.addProperty("name", name);
                ClientState.send(json);
                addField.setText("");
            }
        }).dimensions(left, height - 27, Math.min(120, listWidth), 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Метки"), button -> client.setScreen(new MarkerScreen(this)))
                .dimensions(controlX, 44, controlWidth, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(attackToggleLabel()), button -> {
            ClientConfig.INSTANCE.blockTeammateHits = !ClientConfig.INSTANCE.blockTeammateHits;
            ClientConfig.save();
            button.setMessage(Text.literal(attackToggleLabel()));
        }).dimensions(controlX, 70, controlWidth, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Обновить с сервера"), button -> ClientState.send(ClientState.message("pull")))
                .dimensions(controlX, 96, controlWidth, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Общие настройки"), button -> client.setScreen(new SettingsScreen(this)))
                .dimensions(controlX, 122, controlWidth, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Закрыть"), button -> close())
                .dimensions(width - 85, height - 27, 65, 20).build());
    }

    private static String attackToggleLabel() {
        return "Отключить удар по тиммейту: " + (ClientConfig.INSTANCE.blockTeammateHits ? "ВКЛ" : "ВЫКЛ");
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        context.fill(15, 15, width - 15, height - 15, 0xE9101722);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 22, 0xFFFFFF);
        context.drawTextWithShadow(textRenderer, Text.literal("ДРУЗЬЯ  " + ClientState.FRIENDS.size() + "/144").formatted(Formatting.AQUA), 24, 32, 0xFFFFFF);
        context.fill(22, 69, listRight, height - 78, 0xAA071019);
        context.drawTextWithShadow(textRenderer, Text.literal("Нажми на друга для настроек"), 24, 59, 0xC8D7E8);

        List<ClientState.FriendInfo> friends = ClientState.sortedFriends(searchField == null ? "" : searchField.getText());
        int visibleRows = Math.max(0, (height - 160) / 15);
        if (scrollOffset > Math.max(0, friends.size() - visibleRows)) scrollOffset = Math.max(0, friends.size() - visibleRows);
        int y = 75;
        for (int i = scrollOffset; i < friends.size() && i < scrollOffset + visibleRows; i++) {
            ClientState.FriendInfo friend = friends.get(i);
            int rowBottom = y + 14;
            int color = mouseX >= 24 && mouseX <= listRight && mouseY >= y && mouseY < rowBottom ? 0xAA24617A : 0x00000000;
            if (color != 0) context.fill(24, y - 1, listRight, rowBottom, color);
            context.drawTextWithShadow(textRenderer, Text.literal(friend.online() ? "●" : "○"), 30, y + 2,
                    friend.online() ? 0x55FF55 : 0x777777);
            context.drawTextWithShadow(textRenderer, Text.literal(friend.name()), 42, y + 2, 0xE7F5FF);
            y += 15;
        }
        if (friends.isEmpty()) {
            context.drawTextWithShadow(textRenderer, Text.literal("Список пуст. Добавь друга по нику ниже."), 30, 82, 0xAAAAAA);
        }
        context.drawTextWithShadow(textRenderer, Text.literal("B — временная, N — основная, O — меню"), controlX, height - 66, 0xAFC4D7);
        if (!ClientState.serverHasMod) {
            context.drawTextWithShadow(textRenderer, Text.literal("Нет серверной части BuddyMarks"), controlX, height - 82, 0xFF7777);
        }
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseX >= 24 && mouseX <= listRight && mouseY >= 75 && mouseY < height - 78) {
            int row = (int) ((mouseY - 75) / 15.0) + scrollOffset;
            List<ClientState.FriendInfo> friends = ClientState.sortedFriends(searchField == null ? "" : searchField.getText());
            if (row >= 0 && row < friends.size()) {
                ClientState.FriendInfo friend = friends.get(row);
                client.setScreen(new VisualSettingsScreen(this, friend.uuid(), friend.name()));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        List<ClientState.FriendInfo> friends = ClientState.sortedFriends(searchField == null ? "" : searchField.getText());
        int visibleRows = Math.max(0, (height - 160) / 15);
        scrollOffset = Math.max(0, Math.min(Math.max(0, friends.size() - visibleRows), scrollOffset + (verticalAmount < 0 ? 3 : -3)));
        return true;
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() { return false; }
}
