package com.buddymarks.client.screen;

import com.buddymarks.client.BuddyMarksClient;
import com.buddymarks.client.ClientConfig;
import com.buddymarks.client.ClientState;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class MarkerScreen extends Screen {
    private final Screen parent;
    private TextFieldWidget nameField;
    private TextFieldWidget durationHoursField;
    private ButtonWidget durationButton;
    private ButtonWidget styleColorButton;
    private ButtonWidget styleSizeButton;
    private int durationIndex;
    private int customDurationHours;
    private boolean manualRemoval;
    private String selectedMarkerId;
    private int markerScroll;
    private static final long[] DURATIONS = {3600, 21600, 86400, 0};
    private static final String[] DURATION_LABELS = {"1 час", "6 часов", "24 часа", "Без ограничения"};

    public MarkerScreen(Screen parent) {
        super(Text.literal("BuddyMarks — метки"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        durationIndex = ClientConfig.INSTANCE.mainMarkerDurationIndex;
        customDurationHours = ClientConfig.INSTANCE.customMainMarkerHours;
        manualRemoval = ClientConfig.INSTANCE.mainMarkersManualRemoval;
        int x = width / 2 - 155;
        nameField = addDrawableChild(new TextFieldWidget(textRenderer, x, 54, 180, 20, Text.literal("Название")));
        nameField.setMaxLength(40);
        nameField.setPlaceholder(Text.literal("База, портал, тайник..."));
        addDrawableChild(ButtonWidget.builder(Text.literal("Создать основную метку"), button -> createMainMarker())
                .dimensions(x + 186, 54, 124, 20).build());
        durationButton = addDrawableChild(ButtonWidget.builder(Text.literal(durationLabel()), button -> {
            durationIndex = (durationIndex + 1) % DURATIONS.length;
            customDurationHours = 0;
            ClientConfig.INSTANCE.mainMarkerDurationIndex = durationIndex;
            ClientConfig.INSTANCE.customMainMarkerHours = 0;
            ClientConfig.save();
            if (durationHoursField != null) durationHoursField.setText("");
            button.setMessage(Text.literal(durationLabel()));
        }).dimensions(x, 82, 154, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Убирать метку вручную: " + (manualRemoval ? "ВКЛ" : "ВЫКЛ")), button -> {
            manualRemoval = !manualRemoval;
            ClientConfig.INSTANCE.mainMarkersManualRemoval = manualRemoval;
            ClientConfig.save();
            button.setMessage(Text.literal("Убирать метку вручную: " + (manualRemoval ? "ВКЛ" : "ВЫКЛ")));
            ClientState.MarkerInfo selected = selectedMainMarker();
            if (selected != null) {
                JsonObject request = ClientState.message("marker_mode");
                request.addProperty("id", selected.id());
                request.addProperty("manual", manualRemoval);
                ClientState.send(request);
            }
        }).dimensions(x + 160, 82, 150, 20).build());
        durationHoursField = addDrawableChild(new TextFieldWidget(textRenderer, x, 108, 90, 20, Text.literal("Часы")));
        durationHoursField.setMaxLength(3);
        durationHoursField.setPlaceholder(Text.literal("1–168 ч"));
        if (customDurationHours > 0) durationHoursField.setText(Integer.toString(customDurationHours));
        addDrawableChild(ButtonWidget.builder(Text.literal("Применить срок в часах"), button -> applyCustomDuration())
                .dimensions(x + 96, 108, 214, 20).build());
        styleColorButton = addDrawableChild(ButtonWidget.builder(Text.literal(styleColorLabel()), button -> {
            ClientState.MarkerInfo selected = selectedMainMarker();
            int current = selected == null ? ClientConfig.INSTANCE.mainMarkerColor : selected.color();
            client.setScreen(new ColorPickerScreen(this, current, "Цвет основной метки", this::setMarkerColor));
        }).dimensions(x, 134, 154, 20).build());
        styleSizeButton = addDrawableChild(ButtonWidget.builder(Text.literal(styleSizeLabel()), button -> cycleMarkerSize())
                .dimensions(x + 160, 134, 150, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Удалить выбранную"), button -> {
            if (selectedMarkerId == null) {
                BuddyMarksClient.notifyText("Сначала выбери метку в списке.");
                return;
            }
            JsonObject request = ClientState.message("marker_remove");
            request.addProperty("id", selectedMarkerId);
            ClientState.send(request);
            selectedMarkerId = null;
            updateStyleLabels();
        }).dimensions(x, height - 38, 145, 22).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Назад"), button -> close())
                .dimensions(width - 95, height - 38, 75, 22).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Обновить"), button -> ClientState.send(ClientState.message("pull")))
                .dimensions(width - 178, height - 38, 75, 22).build());
    }

    private ClientState.MarkerInfo selectedMainMarker() {
        if (selectedMarkerId == null) return null;
        ClientState.MarkerInfo marker = ClientState.MARKERS.get(selectedMarkerId);
        return marker != null && !marker.temporary() ? marker : null;
    }

    private String styleColorLabel() {
        ClientState.MarkerInfo marker = selectedMainMarker();
        int color = marker == null ? ClientConfig.INSTANCE.mainMarkerColor : marker.color();
        return "Цвет: " + colorName(color);
    }

    private String styleSizeLabel() {
        ClientState.MarkerInfo marker = selectedMainMarker();
        int size = marker == null ? ClientConfig.INSTANCE.mainMarkerDefaultSize : marker.size();
        return "Размер: " + size + "%";
    }

    private void updateStyleLabels() {
        if (styleColorButton != null) styleColorButton.setMessage(Text.literal(styleColorLabel()));
        if (styleSizeButton != null) styleSizeButton.setMessage(Text.literal(styleSizeLabel()));
    }

    private void setMarkerColor(int picked) {
        ClientState.MarkerInfo marker = selectedMainMarker();
        if (marker == null) {
            ClientConfig.INSTANCE.mainMarkerColor = picked;
            ClientConfig.save();
        } else {
            ClientState.MARKERS.put(marker.id(), new ClientState.MarkerInfo(marker.id(), marker.owner(), marker.ownerName(),
                    marker.dimension(), marker.x(), marker.y(), marker.z(), marker.name(), marker.temporary(),
                    marker.expiresAt(), marker.manual(), picked, marker.size()));
            JsonObject request = ClientState.message("marker_style");
            request.addProperty("id", marker.id());
            request.addProperty("color", picked);
            request.addProperty("size", marker.size());
            ClientState.send(request);
        }
        updateStyleLabels();
    }

    private void cycleMarkerSize() {
        int[] sizes = {50, 75, 100, 125, 150, 200};
        ClientState.MarkerInfo marker = selectedMainMarker();
        int current = marker == null ? ClientConfig.INSTANCE.mainMarkerDefaultSize : marker.size();
        int next = 100;
        for (int i = 0; i < sizes.length; i++) if (sizes[i] == current) { next = sizes[(i + 1) % sizes.length]; break; }
        if (marker == null) {
            ClientConfig.INSTANCE.mainMarkerDefaultSize = next;
            ClientConfig.save();
        } else {
            ClientState.MARKERS.put(marker.id(), new ClientState.MarkerInfo(marker.id(), marker.owner(), marker.ownerName(),
                    marker.dimension(), marker.x(), marker.y(), marker.z(), marker.name(), marker.temporary(),
                    marker.expiresAt(), marker.manual(), marker.color(), next));
            JsonObject request = ClientState.message("marker_style");
            request.addProperty("id", marker.id());
            request.addProperty("color", marker.color());
            request.addProperty("size", next);
            ClientState.send(request);
        }
        updateStyleLabels();
    }

    private static int nextColor(int current) {
        int[] colors = {0xFFBE47, 0x39D7FF, 0xFF5555, 0x55FF55, 0x55AAFF, 0xFFFF55, 0xFF55FF, 0x55FFFF, 0xFFFFFF, 0x8855FF};
        for (int i = 0; i < colors.length; i++) if (colors[i] == (current & 0xFFFFFF)) return colors[(i + 1) % colors.length];
        return colors[0];
    }

    private static String colorName(int rgb) {
        return switch (rgb & 0xFFFFFF) {
            case 0xFFBE47 -> "оранжевый"; case 0x39D7FF -> "голубой"; case 0xFF5555 -> "красный";
            case 0x55FF55 -> "зелёный"; case 0x55AAFF -> "синий"; case 0xFFFF55 -> "жёлтый";
            case 0xFF55FF -> "розовый"; case 0x55FFFF -> "бирюзовый"; case 0xFFFFFF -> "белый";
            case 0x8855FF -> "фиолетовый"; default -> "RGB";
        };
    }

    private String durationLabel() {
        return customDurationHours > 0 ? "Срок: свой — " + customDurationHours + " ч" : "Срок: " + DURATION_LABELS[durationIndex];
    }

    private void applyCustomDuration() {
        String value = durationHoursField.getText().trim();
        try {
            int hours = Integer.parseInt(value);
            if (hours < 1 || hours > 168) throw new NumberFormatException();
            customDurationHours = hours;
            ClientConfig.INSTANCE.customMainMarkerHours = hours;
            ClientConfig.save();
            durationIndex = Math.min(durationIndex, DURATIONS.length - 2);
            ClientConfig.INSTANCE.mainMarkerDurationIndex = durationIndex;
            ClientConfig.save();
            BuddyMarksClient.notifyText("Срок основной метки установлен: " + hours + " ч.");
            if (durationButton != null) durationButton.setMessage(Text.literal(durationLabel()));
        } catch (NumberFormatException exception) {
            BuddyMarksClient.notifyText("Укажи целое число часов от 1 до 168.");
        }
    }

    private void createMainMarker() {
        String name = nameField.getText().trim();
        if (name.isBlank()) name = "Метка";
        var position = BuddyMarksClient.markerPositionFromLook(client);
        JsonObject request = ClientState.message("marker_main");
        request.addProperty("name", name);
        request.addProperty("x", position.x);
        request.addProperty("y", position.y);
        request.addProperty("z", position.z);
        long seconds = customDurationHours > 0 ? (long) customDurationHours * 3600L
                : (DURATIONS[durationIndex] == 0 ? 3600 : DURATIONS[durationIndex]);
        request.addProperty("duration", seconds);
        request.addProperty("unlimited", customDurationHours == 0 && DURATIONS[durationIndex] == 0);
        request.addProperty("manual", manualRemoval);
        request.addProperty("color", ClientConfig.INSTANCE.mainMarkerColor);
        request.addProperty("size", ClientConfig.INSTANCE.mainMarkerDefaultSize);
        ClientState.send(request);
        nameField.setText("");
    }

    private List<ClientState.MarkerInfo> visibleMarkers() {
        List<ClientState.MarkerInfo> rows = new ArrayList<>();
        for (ClientState.MarkerInfo marker : ClientState.MARKERS.values()) {
            if (!ClientConfig.INSTANCE.showTemporaryMarkers && marker.temporary()) continue;
            if (!ClientConfig.INSTANCE.showMainMarkers && !marker.temporary()) continue;
            rows.add(marker);
        }
        rows.sort(Comparator.comparing(ClientState.MarkerInfo::temporary).thenComparing(ClientState.MarkerInfo::name, String.CASE_INSENSITIVE_ORDER));
        return rows;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        context.fill(16, 16, width - 16, height - 16, 0xE9101722);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 25, 0xFFFFFF);
        context.drawTextWithShadow(textRenderer, Text.literal("Постановка временной метки: клавиша B (по умолчанию), длительность " + ClientConfig.INSTANCE.temporaryMarkerSeconds + " сек."), 24, 36, 0xAACCE8);
        context.fill(22, 174, width - 22, height - 54, 0xAA071019);
        context.drawTextWithShadow(textRenderer, Text.literal("Метки команды — нажми на строку, чтобы выбрать её для удаления:"), 26, 160, 0xFFFFFF);
        List<ClientState.MarkerInfo> markers = visibleMarkers();
        int y = 178;
        int visible = Math.max(0, (height - 240) / 16);
        if (markerScroll > Math.max(0, markers.size() - visible)) markerScroll = Math.max(0, markers.size() - visible);
        for (int i = markerScroll; i < markers.size() && i < markerScroll + visible; i++) {
            ClientState.MarkerInfo marker = markers.get(i);
            int rowBottom = y + 15;
            boolean selected = marker.id().equals(selectedMarkerId);
            boolean hover = mouseX >= 24 && mouseX <= width - 24 && mouseY >= y && mouseY < rowBottom;
            if (selected || hover) context.fill(24, y - 1, width - 24, rowBottom, selected ? 0xAA26617A : 0x66416A7A);
            String markerName = marker.temporary() ? "Временная" : marker.name();
            long remaining = marker.expiresAt() < 0 ? -1 : Math.max(0, (marker.expiresAt() - ClientState.serverNow()) / 1000);
            String life = remaining < 0 ? "∞" : remaining + "с";
            context.drawTextWithShadow(textRenderer, Text.literal((marker.temporary() ? "● " : "◆ ") + markerName + " — " + marker.ownerName() + " — " + String.format(java.util.Locale.ROOT, "%.0f %.0f %.0f", marker.x(), marker.y(), marker.z()) + " — " + life), 30, y + 3, marker.color() | 0xFF000000);
            y += 16;
        }
        if (markers.isEmpty()) context.drawTextWithShadow(textRenderer, Text.literal("Пока нет меток."), 30, 182, 0x999999);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseX >= 24 && mouseX <= width - 24 && mouseY >= 178 && mouseY < height - 54) {
            int row = (int) ((mouseY - 178) / 16.0) + markerScroll;
            List<ClientState.MarkerInfo> markers = visibleMarkers();
            if (row >= 0 && row < markers.size()) {
                selectedMarkerId = markers.get(row).id();
                updateStyleLabels();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int visible = Math.max(0, (height - 240) / 16);
        markerScroll = Math.max(0, Math.min(Math.max(0, visibleMarkers().size() - visible), markerScroll + (verticalAmount < 0 ? 3 : -3)));
        return true;
    }

    @Override
    public void close() { client.setScreen(parent); }
    @Override
    public boolean shouldPause() { return false; }
}
