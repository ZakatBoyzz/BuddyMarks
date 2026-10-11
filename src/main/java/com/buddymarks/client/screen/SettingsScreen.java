package com.buddymarks.client.screen;

import com.buddymarks.client.ClientConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Local preferences shared by the marker and teammate screens. */
public final class SettingsScreen extends Screen {
    private final Screen parent;
    private TextFieldWidget customDurationField;
    private static final int[] DURATIONS = {5, 10, 15, 30, 60};
    private static final int[] SIZES = {75, 100, 125, 150};
    private static final float[] OPACITIES = {0.50f, 0.70f, 0.85f, 1.00f};
    private static final int[] COLORS = {0x39D7FF, 0xFFBE47, 0xFF5555, 0x55FF55, 0x55AAFF, 0xFFFF55, 0xFF55FF, 0x55FFFF, 0xFFFFFF, 0x8855FF};

    public SettingsScreen(Screen parent) {
        super(Text.literal("BuddyMarks — общие настройки"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int buttonWidth = Math.min(310, width - 36);
        int x = (width - buttonWidth) / 2;
        int y = 54;
        addDrawableChild(ButtonWidget.builder(Text.literal(tempDurationLabel()), button -> {
            int index = 0;
            for (int i = 0; i < DURATIONS.length; i++) if (DURATIONS[i] == ClientConfig.INSTANCE.temporaryMarkerSeconds) index = i;
            ClientConfig.INSTANCE.temporaryMarkerSeconds = DURATIONS[(index + 1) % DURATIONS.length];
            ClientConfig.save();
            button.setMessage(Text.literal(tempDurationLabel()));
            if (customDurationField != null) customDurationField.setText(Integer.toString(ClientConfig.INSTANCE.temporaryMarkerSeconds));
        }).dimensions(x, y, buttonWidth, 20).build());
        y += 26;
        addDrawableChild(ButtonWidget.builder(Text.literal(tempMarkersLabel()), button -> {
            ClientConfig.INSTANCE.showTemporaryMarkers = !ClientConfig.INSTANCE.showTemporaryMarkers;
            ClientConfig.save();
            button.setMessage(Text.literal(tempMarkersLabel()));
        }).dimensions(x, y, buttonWidth, 20).build());
        y += 26;
        addDrawableChild(ButtonWidget.builder(Text.literal(mainMarkersLabel()), button -> {
            ClientConfig.INSTANCE.showMainMarkers = !ClientConfig.INSTANCE.showMainMarkers;
            ClientConfig.save();
            button.setMessage(Text.literal(mainMarkersLabel()));
        }).dimensions(x, y, buttonWidth, 20).build());
        y += 26;
        addDrawableChild(ButtonWidget.builder(Text.literal(sizeLabel()), button -> {
            int index = 0;
            for (int i = 0; i < SIZES.length; i++) if (SIZES[i] == ClientConfig.INSTANCE.markerSize) index = i;
            ClientConfig.INSTANCE.markerSize = SIZES[(index + 1) % SIZES.length];
            ClientConfig.save();
            button.setMessage(Text.literal(sizeLabel()));
        }).dimensions(x, y, buttonWidth, 20).build());
        y += 26;
        addDrawableChild(ButtonWidget.builder(Text.literal(opacityLabel()), button -> {
            int index = 0;
            for (int i = 0; i < OPACITIES.length; i++) if (Math.abs(OPACITIES[i] - ClientConfig.INSTANCE.markerOpacity) < 0.02f) index = i;
            ClientConfig.INSTANCE.markerOpacity = OPACITIES[(index + 1) % OPACITIES.length];
            ClientConfig.save();
            button.setMessage(Text.literal(opacityLabel()));
        }).dimensions(x, y, buttonWidth, 20).build());
        y += 26;
        addDrawableChild(ButtonWidget.builder(Text.literal(tempColorLabel()), button ->
                client.setScreen(new ColorPickerScreen(this, ClientConfig.INSTANCE.temporaryMarkerColor,
                        "Цвет временной метки", picked -> {
                    ClientConfig.INSTANCE.temporaryMarkerColor = picked;
                    ClientConfig.save();
                }))).dimensions(x, y, buttonWidth, 20).build());
        y += 26;
        addDrawableChild(ButtonWidget.builder(Text.literal(mainColorLabel()), button ->
                client.setScreen(new ColorPickerScreen(this, ClientConfig.INSTANCE.mainMarkerColor,
                        "Цвет основной метки по умолчанию", picked -> {
                    ClientConfig.INSTANCE.mainMarkerColor = picked;
                    ClientConfig.save();
                }))).dimensions(x, y, buttonWidth, 20).build());
        y += 26;
        customDurationField = addDrawableChild(new TextFieldWidget(textRenderer, x, y, buttonWidth - 92, 20, Text.literal("Своя длительность")));
        customDurationField.setMaxLength(4);
        customDurationField.setPlaceholder(Text.literal("Секунды: 1–3600"));
        customDurationField.setText(Integer.toString(ClientConfig.INSTANCE.temporaryMarkerSeconds));
        addDrawableChild(ButtonWidget.builder(Text.literal("Применить"), button -> applyCustomDuration())
                .dimensions(x + buttonWidth - 86, y, 86, 20).build());
        y += 26;
        addDrawableChild(ButtonWidget.builder(Text.literal("Закрыть"), button -> close())
                .dimensions(width / 2 - 45, height - 35, 90, 20).build());
    }

    private void applyCustomDuration() {
        try {
            int seconds = Integer.parseInt(customDurationField.getText().trim());
            if (seconds < 1 || seconds > 3600) throw new NumberFormatException();
            ClientConfig.INSTANCE.temporaryMarkerSeconds = seconds;
            ClientConfig.save();
            customDurationField.setText(Integer.toString(seconds));
        } catch (NumberFormatException exception) {
            com.buddymarks.client.BuddyMarksClient.notifyText("Укажи длительность временной метки от 1 до 3600 секунд.");
        }
    }

    private static String tempDurationLabel() { return "Временная метка: " + ClientConfig.INSTANCE.temporaryMarkerSeconds + " сек."; }
    private static String tempMarkersLabel() { return "Показывать временные метки: " + onOff(ClientConfig.INSTANCE.showTemporaryMarkers); }
    private static String mainMarkersLabel() { return "Показывать основные метки: " + onOff(ClientConfig.INSTANCE.showMainMarkers); }
    private static String sizeLabel() { return "Размер меток: " + ClientConfig.INSTANCE.markerSize + "%"; }
    private static String opacityLabel() { return "Непрозрачность меток: " + Math.round(ClientConfig.INSTANCE.markerOpacity * 100) + "%"; }
    private static String tempColorLabel() { return "Цвет временной метки: " + colorName(ClientConfig.INSTANCE.temporaryMarkerColor); }
    private static String mainColorLabel() { return "Цвет основной метки: " + colorName(ClientConfig.INSTANCE.mainMarkerColor); }
    private static int nextColor(int current) {
        for (int i = 0; i < COLORS.length; i++) if (COLORS[i] == (current & 0xFFFFFF)) return COLORS[(i + 1) % COLORS.length];
        return COLORS[0];
    }
    private static String colorName(int rgb) {
        return switch (rgb & 0xFFFFFF) {
            case 0x39D7FF -> "голубой"; case 0xFFBE47 -> "оранжевый"; case 0xFF5555 -> "красный";
            case 0x55FF55 -> "зелёный"; case 0x55AAFF -> "синий"; case 0xFFFF55 -> "жёлтый";
            case 0xFF55FF -> "розовый"; case 0x55FFFF -> "бирюзовый"; case 0xFFFFFF -> "белый";
            case 0x8855FF -> "фиолетовый"; default -> "RGB";
        };
    }
    private static String onOff(boolean value) { return value ? "ВКЛ" : "ВЫКЛ"; }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        context.fill(12, 12, width - 12, height - 12, 0xE9101722);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 25, 0xFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("Настройки сохраняются локально и не меняют параметры других игроков.").formatted(Formatting.GRAY), width / 2, 39, 0xFFFFFF);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override public void close() { client.setScreen(parent); }
    @Override public boolean shouldPause() { return false; }
}
