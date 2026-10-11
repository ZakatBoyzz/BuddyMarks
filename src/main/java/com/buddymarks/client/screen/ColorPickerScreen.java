package com.buddymarks.client.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.awt.Color;
import java.util.function.IntConsumer;

/** A visual HSB picker: hue/brightness grid plus saturation controls, no RGB/HEX typing. */
public final class ColorPickerScreen extends Screen {
    private final Screen parent;
    private final IntConsumer apply;
    private final String purpose;
    private float hue;
    private float saturation;
    private float brightness;
    private static final int COLUMNS = 24;
    private static final int ROWS = 9;

    public ColorPickerScreen(Screen parent, int initialRgb, String purpose, IntConsumer apply) {
        super(Text.literal("BuddyMarks — выбор цвета"));
        this.parent = parent;
        this.purpose = purpose;
        this.apply = apply;
        float[] hsb = Color.RGBtoHSB((initialRgb >> 16) & 255, (initialRgb >> 8) & 255, initialRgb & 255, null);
        this.hue = hsb[0];
        this.saturation = hsb[1];
        this.brightness = Math.max(0.20f, hsb[2]);
    }

    @Override
    protected void init() {
        int buttonWidth = Math.min(74, Math.max(56, (width - 48) / 4));
        int gap = 4;
        int total = buttonWidth * 4 + gap * 3;
        int x = (width - total) / 2;
        int controlsY = gridY() + gridHeight() + 24;
        addDrawableChild(ButtonWidget.builder(Text.literal("Насыщ. −"), b -> { saturation = Math.max(0.0f, saturation - 0.1f); }).dimensions(x, controlsY, buttonWidth, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Насыщ. +"), b -> { saturation = Math.min(1.0f, saturation + 0.1f); }).dimensions(x + buttonWidth + gap, controlsY, buttonWidth, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Яркость −"), b -> { brightness = Math.max(0.20f, brightness - 0.1f); }).dimensions(x + (buttonWidth + gap) * 2, controlsY, buttonWidth, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Яркость +"), b -> { brightness = Math.min(1.0f, brightness + 0.1f); }).dimensions(x + (buttonWidth + gap) * 3, controlsY, buttonWidth, 20).build());
        int footerY = Math.min(height - 30, controlsY + 27);
        addDrawableChild(ButtonWidget.builder(Text.literal("Применить"), b -> {
            apply.accept(currentColor());
            client.setScreen(parent);
        }).dimensions(width / 2 - 100, footerY, 96, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Отмена"), b -> close())
                .dimensions(width / 2 + 4, footerY, 96, 20).build());
    }

    private int gridX() { return (width - gridWidth()) / 2; }
    private int gridY() { return Math.max(55, Math.min(68, height / 5)); }
    private int gridWidth() { return Math.min(384, Math.max(240, width - 28)); }
    private int cellWidth() { return Math.max(1, gridWidth() / COLUMNS); }
    private int cellHeight() { return Math.max(10, Math.min(16, (height - 150) / ROWS)); }
    private int gridHeight() { return cellHeight() * ROWS; }

    private int currentColor() {
        return Color.HSBtoRGB(hue, saturation, brightness) & 0xFFFFFF;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        context.fill(10, 10, width - 10, height - 10, 0xEA101722);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 19, 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(purpose).formatted(Formatting.GRAY), width / 2, 34, 0xFFFFFFFF);
        int gx = gridX();
        int gy = gridY();
        int cw = cellWidth();
        int ch = cellHeight();
        for (int row = 0; row < ROWS; row++) {
            float rowBrightness = 1.0f - (row / (float) (ROWS - 1)) * 0.8f;
            for (int col = 0; col < COLUMNS; col++) {
                float cellHue = col / (float) COLUMNS;
                int rgb = Color.HSBtoRGB(cellHue, saturation, rowBrightness) & 0xFFFFFF;
                int left = gx + col * cw;
                int top = gy + row * ch;
                context.fill(left, top, left + cw - 1, top + ch - 1, 0xFF000000 | rgb);
                if (Math.abs(cellHue - hue) < (0.5f / COLUMNS) && Math.abs(rowBrightness - brightness) <= 0.10f) {
                    context.fill(left, top, left + cw - 1, top + 1, 0xFFFFFFFF);
                    context.fill(left, top + ch - 1, left + cw - 1, top + ch, 0xFFFFFFFF);
                    context.fill(left, top, left + 1, top + ch, 0xFFFFFFFF);
                    context.fill(left + cw - 2, top, left + cw - 1, top + ch, 0xFFFFFFFF);
                }
            }
        }
        int swatchY = gy + gridHeight() - 1;
        context.fill(gx, swatchY, gx + gridWidth(), swatchY + 5, 0xFF000000 | currentColor());
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Оттенок выбирается по горизонтали, яркость — по вертикали. Насыщенность: " + Math.round(saturation * 100) + "%"),
                width / 2, swatchY + 7, 0xFFD8E5F2);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int gx = gridX();
            int gy = gridY();
            if (mouseX >= gx && mouseX < gx + gridWidth() && mouseY >= gy && mouseY < gy + gridHeight()) {
                int column = Math.min(COLUMNS - 1, Math.max(0, (int) ((mouseX - gx) / cellWidth())));
                int row = Math.min(ROWS - 1, Math.max(0, (int) ((mouseY - gy) / cellHeight())));
                hue = column / (float) COLUMNS;
                brightness = 1.0f - (row / (float) (ROWS - 1)) * 0.8f;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override public void close() { client.setScreen(parent); }
    @Override public boolean shouldPause() { return false; }
}
