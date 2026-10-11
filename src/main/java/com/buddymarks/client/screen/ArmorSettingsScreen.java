package com.buddymarks.client.screen;

import com.buddymarks.client.ClientConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Individual local leather-armor tint controls, keyed by teammate UUID. */
public final class ArmorSettingsScreen extends Screen {
    private final Screen parent;
    private final String uuid;
    private final String playerName;
    private ButtonWidget helmetButton;
    private ButtonWidget chestButton;
    private ButtonWidget legsButton;
    private ButtonWidget bootsButton;
    private static final int[] COLORS = {0xFF5555, 0x55FF55, 0x55AAFF, 0xFFFF55, 0xFF55FF, 0x55FFFF, 0xFFFFFF, 0x222222, 0xFF8800, 0x8855FF};

    public ArmorSettingsScreen(Screen parent, String uuid, String playerName) {
        super(Text.literal("BuddyMarks — броня: " + playerName));
        this.parent = parent;
        this.uuid = uuid;
        this.playerName = playerName;
    }

    @Override
    protected void init() {
        ClientConfig.VisualSettings s = ClientConfig.visualFor(uuid);
        int buttonWidth = Math.min(310, width - 32);
        int x = (width - buttonWidth) / 2;
        int y = 58;
        addDrawableChild(ButtonWidget.builder(Text.literal("Окраска кожаной брони: " + onOff(s.tintLeatherArmor)), b -> {
            s.tintLeatherArmor = !s.tintLeatherArmor;
            ClientConfig.save();
            b.setMessage(Text.literal("Окраска кожаной брони: " + onOff(s.tintLeatherArmor)));
        }).dimensions(x, y, buttonWidth, 20).build());
        y += 27;
        addDrawableChild(ButtonWidget.builder(Text.literal("Вся броня: " + colorName(s.armorColor)), b ->
                client.setScreen(new ColorPickerScreen(this, s.armorColor, "Общий цвет кожаной брони: " + playerName, picked -> {
                    s.armorColor = picked;
                    s.helmetColor = picked;
                    s.chestColor = picked;
                    s.legsColor = picked;
                    s.bootsColor = picked;
                    ClientConfig.save();
                    refreshPieceButtons();
                }))).dimensions(x, y, buttonWidth, 20).build());
        y += 31;
        int gap = 8;
        int colWidth = (buttonWidth - gap) / 2;
        helmetButton = addDrawableChild(ButtonWidget.builder(Text.literal(pieceLabel("Шлем", s.helmetColor)), b ->
                client.setScreen(new ColorPickerScreen(this, s.helmetColor, "Цвет шлема: " + playerName, picked -> {
                    s.helmetColor = picked; ClientConfig.save(); refreshPieceButtons();
                }))).dimensions(x, y, colWidth, 22).build());
        chestButton = addDrawableChild(ButtonWidget.builder(Text.literal(pieceLabel("Нагрудник", s.chestColor)), b ->
                client.setScreen(new ColorPickerScreen(this, s.chestColor, "Цвет нагрудника: " + playerName, picked -> {
                    s.chestColor = picked; ClientConfig.save(); refreshPieceButtons();
                }))).dimensions(x + colWidth + gap, y, colWidth, 22).build());
        y += 29;
        legsButton = addDrawableChild(ButtonWidget.builder(Text.literal(pieceLabel("Поножи", s.legsColor)), b ->
                client.setScreen(new ColorPickerScreen(this, s.legsColor, "Цвет поножей: " + playerName, picked -> {
                    s.legsColor = picked; ClientConfig.save(); refreshPieceButtons();
                }))).dimensions(x, y, colWidth, 22).build());
        bootsButton = addDrawableChild(ButtonWidget.builder(Text.literal(pieceLabel("Ботинки", s.bootsColor)), b ->
                client.setScreen(new ColorPickerScreen(this, s.bootsColor, "Цвет ботинок: " + playerName, picked -> {
                    s.bootsColor = picked; ClientConfig.save(); refreshPieceButtons();
                }))).dimensions(x + colWidth + gap, y, colWidth, 22).build());
        y += 35;
        addDrawableChild(ButtonWidget.builder(Text.literal("Назад"), b -> close())
                .dimensions(width / 2 - 45, Math.min(height - 30, y), 90, 20).build());
    }

    private void refreshPieceButtons() {
        ClientConfig.VisualSettings s = ClientConfig.visualFor(uuid);
        if (helmetButton != null) helmetButton.setMessage(Text.literal(pieceLabel("Шлем", s.helmetColor)));
        if (chestButton != null) chestButton.setMessage(Text.literal(pieceLabel("Нагрудник", s.chestColor)));
        if (legsButton != null) legsButton.setMessage(Text.literal(pieceLabel("Поножи", s.legsColor)));
        if (bootsButton != null) bootsButton.setMessage(Text.literal(pieceLabel("Ботинки", s.bootsColor)));
    }

    private static String pieceLabel(String part, int color) { return part + ": " + colorName(color); }
    private static String onOff(boolean value) { return value ? "ВКЛ" : "ВЫКЛ"; }
    private static int nextColor(int current) {
        for (int i = 0; i < COLORS.length; i++) if (COLORS[i] == (current & 0xFFFFFF)) return COLORS[(i + 1) % COLORS.length];
        return COLORS[0];
    }
    private static String colorName(int rgb) {
        return switch (rgb & 0xFFFFFF) {
            case 0xFF5555 -> "красный"; case 0x55FF55 -> "зелёный"; case 0x55AAFF -> "голубой";
            case 0xFFFF55 -> "жёлтый"; case 0xFF55FF -> "розовый"; case 0x55FFFF -> "бирюзовый";
            case 0xFFFFFF -> "белый"; case 0x222222 -> "тёмный"; case 0xFF8800 -> "оранжевый";
            case 0x8855FF -> "фиолетовый"; default -> "RGB";
        };
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        context.fill(12, 12, width - 12, height - 12, 0xE9101722);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 22, 0xFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Изменение цвета применяется локально к модели друга в мире.").formatted(Formatting.GRAY), width / 2, 39, 0xFFFFFF);
        ClientConfig.VisualSettings s = ClientConfig.visualFor(uuid);
        int x = width / 2 - 54;
        int y = height - 52;
        context.fill(x - 25, y, x - 7, y + 18, 0xFF000000 | (s.helmetColor & 0xFFFFFF));
        context.fill(x - 3, y, x + 15, y + 18, 0xFF000000 | (s.chestColor & 0xFFFFFF));
        context.fill(x + 19, y, x + 37, y + 18, 0xFF000000 | (s.legsColor & 0xFFFFFF));
        context.fill(x + 41, y, x + 59, y + 18, 0xFF000000 | (s.bootsColor & 0xFFFFFF));
        super.render(context, mouseX, mouseY, delta);
    }

    @Override public void close() { client.setScreen(parent); }
    @Override public boolean shouldPause() { return false; }
}
