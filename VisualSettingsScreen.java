package com.buddymarks.client.screen;

import com.buddymarks.client.ClientConfig;
import com.buddymarks.client.ClientState;
import com.buddymarks.client.ClientState.FriendInfo;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Per-UUID visual controls. None of these options are sent to the server. */
public final class VisualSettingsScreen extends Screen {
    private final Screen parent;
    private final String uuid;
    private final String playerName;
    private boolean confirmRemove;
    private static final int[] COLORS = {0xFF5555, 0x55FF55, 0x55AAFF, 0xFFFF55, 0xFF55FF, 0x55FFFF, 0xFFFFFF, 0x222222, 0xFF8800, 0x8855FF};

    public VisualSettingsScreen(Screen parent, String uuid, String playerName) {
        super(Text.literal("BuddyMarks — " + playerName));
        this.parent = parent;
        this.uuid = uuid;
        this.playerName = playerName;
    }

    @Override
    protected void init() {
        ClientConfig.VisualSettings settings = ClientConfig.visualFor(uuid);
        ensureHatColors(settings);
        int gap = 8;
        int columnWidth = Math.max(100, Math.min(165, (width - 36 - gap) / 2));
        int totalWidth = columnWidth * 2 + gap;
        int left = (width - totalWidth) / 2;
        int right = left + columnWidth + gap;
        int y = 58;
        int rowHeight = 27;

        addDrawableChild(ButtonWidget.builder(Text.literal(hitboxLabel(settings)), button -> {
            settings.showHitbox = !settings.showHitbox;
            ClientConfig.save(); button.setMessage(Text.literal(hitboxLabel(settings)));
        }).dimensions(left, y, columnWidth, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Хитбокс: " + colorName(settings.hitboxColor)), button ->
                client.setScreen(new ColorPickerScreen(this, settings.hitboxColor, "Цвет хитбокса: " + playerName, picked -> {
                    settings.hitboxColor = picked;
                    ClientConfig.save();
                }))).dimensions(right, y, columnWidth, 20).build());
        y += rowHeight;
        addDrawableChild(ButtonWidget.builder(Text.literal(hatLabel(settings)), button -> {
            settings.showHat = !settings.showHat;
            ClientConfig.save(); button.setMessage(Text.literal(hatLabel(settings)));
        }).dimensions(left, y, columnWidth, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Шляпа: " + colorName(settings.hatColor)), button ->
                client.setScreen(new ColorPickerScreen(this, settings.hatColor, "Цвет шляпы: " + playerName, picked -> {
                    settings.hatColor = picked;
                    ensureHatColors(settings);
                    settings.hatColors[0] = picked;
                    ClientConfig.save();
                }))).dimensions(right, y, columnWidth, 20).build());
        y += rowHeight;
        addDrawableChild(ButtonWidget.builder(Text.literal(shimmerLabel(settings)), button -> {
            settings.rainbowHat = !settings.rainbowHat;
            if (settings.rainbowHat && settings.hatColorCount < 2) settings.hatColorCount = 2;
            ensureHatColors(settings);
            if (settings.hatColorCount > 1 && settings.hatColors[1] == settings.hatColors[0]) settings.hatColors[1] = nextColor(settings.hatColors[0]);
            ClientConfig.save(); button.setMessage(Text.literal(shimmerLabel(settings)));
        }).dimensions(left, y, columnWidth, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(hatColorsLabel(settings)), button -> {
            settings.hatColorCount = settings.hatColorCount % 5 + 1;
            ensureHatColors(settings);
            if (settings.hatColorCount > 1 && settings.hatColors[1] == settings.hatColors[0]) settings.hatColors[1] = nextColor(settings.hatColors[0]);
            ClientConfig.save(); button.setMessage(Text.literal(hatColorsLabel(settings)));
        }).dimensions(right, y, columnWidth, 20).build());
        y += rowHeight;
        addDrawableChild(ButtonWidget.builder(Text.literal(speedLabel(settings)), button -> {
            settings.hatAnimationSpeed = settings.hatAnimationSpeed >= 3.5f ? 0.5f : settings.hatAnimationSpeed + 0.5f;
            ClientConfig.save(); button.setMessage(Text.literal(speedLabel(settings)));
        }).dimensions(left, y, columnWidth, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(glowLabel(settings)), button -> {
            settings.showGlow = !settings.showGlow;
            ClientConfig.save(); button.setMessage(Text.literal(glowLabel(settings)));
        }).dimensions(right, y, columnWidth, 20).build());
        y += rowHeight;
        addDrawableChild(ButtonWidget.builder(Text.literal("Свечение: " + colorName(settings.glowColor)), button ->
                client.setScreen(new ColorPickerScreen(this, settings.glowColor, "Цвет свечения: " + playerName, picked -> {
                    settings.glowColor = picked;
                    ClientConfig.save();
                }))).dimensions(left, y, columnWidth, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(confirmRemove ? "ТОЧНО УДАЛИТЬ?" : "Удалить из друзей"), button -> {
            if (!confirmRemove) {
                confirmRemove = true;
                button.setMessage(Text.literal("Нажми ещё раз"));
            } else {
                JsonObject request = ClientState.message("friend_remove");
                request.addProperty("name", playerName);
                ClientState.send(request);
                client.setScreen(parent);
            }
        }).dimensions(right, y, columnWidth, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Настройки кожаной брони…"), button -> client.setScreen(new ArmorSettingsScreen(this, uuid, playerName)))
                .dimensions(width / 2 - Math.min(150, (width - 30) / 2), 193,
                        Math.min(300, width - 30), 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Назад"), button -> close())
                .dimensions(width / 2 - 45, height - 30, 90, 20).build());
    }

    private static void ensureHatColors(ClientConfig.VisualSettings settings) {
        int count = Math.max(1, Math.min(5, settings.hatColorCount));
        if (settings.hatColors == null || settings.hatColors.length != 5) {
            int[] old = settings.hatColors;
            int[] replacement = new int[]{settings.hatColor, nextColor(settings.hatColor), nextColor(nextColor(settings.hatColor)), 0x55FFFF, 0xFFFFFF};
            if (old != null) System.arraycopy(old, 0, replacement, 0, Math.min(old.length, replacement.length));
            settings.hatColors = replacement;
        }
        settings.hatColors[0] = settings.hatColor;
        settings.hatColorCount = count;
    }

    private static String hitboxLabel(ClientConfig.VisualSettings s) { return "Хитбокс: " + onOff(s.showHitbox); }
    private static String hatLabel(ClientConfig.VisualSettings s) { return "Коническая шляпа: " + onOff(s.showHat); }
    private static String shimmerLabel(ClientConfig.VisualSettings s) { return "Включить шейдеры / переливание: " + onOff(s.rainbowHat); }
    private static String hatColorsLabel(ClientConfig.VisualSettings s) { return "Цветов: " + Math.max(1, Math.min(5, s.hatColorCount)); }
    private static String speedLabel(ClientConfig.VisualSettings s) { return "Скорость: " + String.format(java.util.Locale.ROOT, "%.1fx", s.hatAnimationSpeed); }
    private static String glowLabel(ClientConfig.VisualSettings s) { return "Свечение: " + onOff(s.showGlow); }
    private static String onOff(boolean value) { return value ? "ВКЛ" : "ВЫКЛ"; }

    private static int nextColor(int current) {
        for (int i = 0; i < COLORS.length; i++) if (COLORS[i] == (current & 0xFFFFFF)) return COLORS[(i + 1) % COLORS.length];
        return COLORS[0];
    }

    private static String colorName(int rgb) {
        return switch (rgb & 0xFFFFFF) {
            case 0xFF5555 -> "красный";
            case 0x55FF55 -> "зелёный";
            case 0x55AAFF -> "голубой";
            case 0xFFFF55 -> "жёлтый";
            case 0xFF55FF -> "розовый";
            case 0x55FFFF -> "бирюзовый";
            case 0xFFFFFF -> "белый";
            case 0x222222 -> "тёмный";
            case 0xFF8800 -> "оранжевый";
            case 0x8855FF -> "фиолетовый";
            default -> "RGB";
        };
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        context.fill(12, 12, width - 12, height - 12, 0xE9101722);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 20, 0xFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("Настройки визуальны и индивидуальны для этого UUID").formatted(Formatting.GRAY), width / 2, 33, 0xFFFFFF);
        ClientConfig.VisualSettings settings = ClientConfig.visualFor(uuid);
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Ячейки цветов шляпы 1–5: нажми квадрат, чтобы сменить цвет").formatted(Formatting.GRAY),
                width / 2, height - 72, 0xFFFFFF);
        ensureHatColors(settings);
        int paletteStartX = width / 2 - 53;
        int paletteY = height - 55;
        for (int i = 0; i < 5; i++) {
            int cellX = paletteStartX + i * 22;
            int border = i < settings.hatColorCount ? 0xFFFFFFFF : 0xFF555555;
            context.fill(cellX - 2, paletteY - 2, cellX + 20, paletteY + 20, border);
            context.fill(cellX, paletteY, cellX + 18, paletteY + 18, 0xFF000000 | (settings.hatColors[i] & 0xFFFFFF));
            context.drawCenteredTextWithShadow(textRenderer, Integer.toString(i + 1), cellX + 9, paletteY + 5, 0xFFFFFFFF);
        }
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int paletteStartX = width / 2 - 53;
        int paletteY = height - 55;
        if (button == 0 && mouseY >= paletteY - 2 && mouseY <= paletteY + 20) {
            for (int i = 0; i < 5; i++) {
                int cellX = paletteStartX + i * 22;
                if (mouseX >= cellX - 2 && mouseX <= cellX + 20) {
                    ClientConfig.VisualSettings settings = ClientConfig.visualFor(uuid);
                    ensureHatColors(settings);
                    final int slot = i;
                    client.setScreen(new ColorPickerScreen(this, settings.hatColors[slot],
                            "Цвет ячейки шляпы " + (slot + 1) + ": " + playerName, picked -> {
                        settings.hatColors[slot] = picked;
                        settings.hatColorCount = Math.max(settings.hatColorCount, slot + 1);
                        if (slot == 0) settings.hatColor = picked;
                        ClientConfig.save();
                    }));
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override public void close() { client.setScreen(parent); }
    @Override public boolean shouldPause() { return false; }
}
