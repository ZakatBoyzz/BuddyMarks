package com.buddymarks.client.render;

import com.buddymarks.client.ClientConfig;
import com.buddymarks.client.ClientState;
import net.fabricmc.fabric.api.client.rendering.v1.HudLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.IdentifiedLayer;
import com.buddymarks.BuddyMarksMod;
import net.minecraft.util.Identifier;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.Text;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

/** Lightweight renderer for team markers and opt-in teammate hitbox outlines. */
public final class MarkerRenderer {
    private static final int CIRCLE_SEGMENTS = 32;
    private static final double MAX_TEAMMATE_VISUAL_DISTANCE_SQUARED = 256.0 * 256.0;
    private static final double MAX_MARKER_RENDER_DISTANCE_SQUARED = 4096.0 * 4096.0;

    private MarkerRenderer() {}

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(MarkerRenderer::renderWorld);
        HudLayerRegistrationCallback.EVENT.register(layers -> layers.attachLayerAfter(
                IdentifiedLayer.MISC_OVERLAYS,
                Identifier.of(BuddyMarksMod.MOD_ID, "marker_arrows"),
                MarkerRenderer::renderHud));
    }

    private static void renderWorld(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null || context.matrixStack() == null || context.consumers() == null) return;
        MatrixStack matrices = context.matrixStack();
        VertexConsumerProvider consumers = context.consumers();
        Camera camera = context.camera();
        Vec3d cameraPos = camera.getPos();
        VertexConsumer lines = consumers.getBuffer(RenderLayer.getLines());

        for (PlayerEntity target : client.world.getPlayers()) {
            if (target == client.player || !ClientState.isFriend(target.getUuid())) continue;
            ClientConfig.VisualSettings visual = ClientConfig.visualFor(target.getUuidAsString());
            if (target.isRemoved() || target.squaredDistanceTo(client.player) > MAX_TEAMMATE_VISUAL_DISTANCE_SQUARED) continue;
            if (visual.showHitbox) {
                drawBox(context, matrices, consumers, target, cameraPos, visual.hitboxColor, 0.96f, 0.002);
            }
            if (visual.showGlow) {
                // Three thin contours create a low-cost halo-like outline without a post-processing shader.
                drawBox(context, matrices, consumers, target, cameraPos, visual.glowColor, 0.18f, 0.065);
                drawBox(context, matrices, consumers, target, cameraPos, visual.glowColor, 0.38f, 0.035);
                drawBox(context, matrices, consumers, target, cameraPos, visual.glowColor, 0.95f, 0.012);
            }
            if (visual.showHat) {
                drawHat(matrices, consumers, target, cameraPos, visual, System.currentTimeMillis());
            }
        }

        long now = ClientState.serverNow();
        for (ClientState.MarkerInfo marker : ClientState.MARKERS.values()) {
            if (!marker.dimension().equals(client.world.getRegistryKey().getValue().toString())) continue;
            if (marker.temporary() && !ClientConfig.INSTANCE.showTemporaryMarkers) continue;
            if (!marker.temporary() && !ClientConfig.INSTANCE.showMainMarkers) continue;
            if (marker.expiresAt() >= 0 && marker.expiresAt() <= now) continue;
            double dx = marker.x() - cameraPos.x;
            double dy = marker.y() - cameraPos.y;
            double dz = marker.z() - cameraPos.z;
            if (dx * dx + dy * dy + dz * dz > MAX_MARKER_RENDER_DISTANCE_SQUARED) continue;
            int color = marker.color();
            int r = (color >> 16) & 0xFF;
            int g = (color >> 8) & 0xFF;
            int b = color & 0xFF;
            int a = Math.round(ClientConfig.INSTANCE.markerOpacity * 255.0f);
            double radius = 0.62 * ClientConfig.INSTANCE.markerSize / 100.0 * marker.size() / 100.0;
            drawGroundRing(matrices, lines, dx, dy + 0.025, dz, radius, r, g, b, a);
            drawDiamond(matrices, lines, dx, dy + 0.04, dz, radius * 0.22, r, g, b, a);
            if (!marker.temporary() && !marker.name().isBlank()) {
                drawMarkerLabel(client, matrices, consumers, camera, marker);
            }
        }
    }

    private static void drawMarkerLabel(MinecraftClient client, MatrixStack matrices, VertexConsumerProvider consumers,
                                        Camera camera, ClientState.MarkerInfo marker) {
        String label = marker.name();
        if (label.length() > 40) label = label.substring(0, 40);
        matrices.push();
        Vec3d cameraPos = camera.getPos();
        matrices.translate(marker.x() - cameraPos.x, marker.y() - cameraPos.y + 0.32, marker.z() - cameraPos.z);
        matrices.multiply(client.getEntityRenderDispatcher().getRotation());
        matrices.scale(-0.025f, -0.025f, 0.025f);
        float x = -client.textRenderer.getWidth(label) / 2.0f;
        client.textRenderer.draw(Text.literal(label), x, 0.0f, 0xFFFFFFFF, false,
                matrices.peek().getPositionMatrix(), consumers, TextRenderer.TextLayerType.SEE_THROUGH,
                0x70000000, 0xF000F0);
        matrices.pop();
    }

    private static void drawBox(WorldRenderContext context, MatrixStack matrices, VertexConsumerProvider consumers,
                                PlayerEntity target, Vec3d cameraPos, int color, float alpha, double expand) {
        float red = ((color >> 16) & 0xFF) / 255.0f;
        float green = ((color >> 8) & 0xFF) / 255.0f;
        float blue = (color & 0xFF) / 255.0f;
        Box box = target.getBoundingBox().offset(-cameraPos.x, -cameraPos.y, -cameraPos.z).expand(expand);
        VertexRendering.drawBox(matrices, consumers.getBuffer(RenderLayer.getLines()), box, red, green, blue, alpha);
    }

    /** Procedural, client-only cone hat. It follows the teammate's position and head yaw. */
    private static void drawHat(MatrixStack matrices, VertexConsumerProvider consumers, PlayerEntity target,
                                Vec3d cameraPos, ClientConfig.VisualSettings settings, long now) {
        double baseY = target.getY() + target.getHeight() - 0.16 - (target.isInSneakingPose() ? 0.18 : 0.0);
        double topY = baseY + 0.38;
        double cx = target.getX() - cameraPos.x;
        double cz = target.getZ() - cameraPos.z;
        // Slight pitch follows the look direction while keeping the cone stable.
        double yaw = Math.toRadians(target.getHeadYaw());
        double pitch = Math.toRadians(target.getPitch() * 0.35f);
        double axisX = -Math.sin(yaw) * Math.sin(pitch) * 0.16;
        double axisZ = Math.cos(yaw) * Math.sin(pitch) * 0.16;
        double apexX = cx + axisX;
        double apexZ = cz + axisZ;
        double radius = 0.52;
        VertexConsumer fan = consumers.getBuffer(RenderLayer.getDebugTriangleFan());
        MatrixStack.Entry entry = matrices.peek();
        int topColor = colorAt(settings, 0, now);
        fan.vertex(entry, (float) apexX, (float) topY, (float) apexZ)
                .color((topColor >> 16) & 0xFF, (topColor >> 8) & 0xFF, topColor & 0xFF, 245);
        for (int i = 0; i <= 24; i++) {
            double angle = Math.PI * 2.0 * i / 24.0;
            double x = cx + Math.cos(angle) * radius;
            double z = cz + Math.sin(angle) * radius;
            int color = colorAt(settings, i, now);
            fan.vertex(entry, (float) x, (float) baseY, (float) z)
                    .color((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, 238);
        }
        VertexConsumer lineConsumer = consumers.getBuffer(RenderLayer.getLines());
        int baseColor = colorAt(settings, 8, now);
        drawGroundRing(matrices, lineConsumer, cx, baseY, cz, radius, (baseColor >> 16) & 0xFF,
                (baseColor >> 8) & 0xFF, baseColor & 0xFF, 255);
    }

    private static int colorAt(ClientConfig.VisualSettings settings, int phase, long now) {
        int[] colors = settings.hatColors;
        int count = Math.max(1, Math.min(settings.hatColorCount, colors == null ? 1 : colors.length));
        if (!settings.rainbowHat || count <= 1) return settings.hatColor;
        double animation = now * 0.001 * Math.max(0.1, Math.min(4.0, settings.hatAnimationSpeed));
        double position = (animation + phase / 24.0 * count) % count;
        int first = colors[(int) Math.floor(position) % count];
        int second = colors[((int) Math.floor(position) + 1) % count];
        double t = position - Math.floor(position);
        int r = (int) (((first >> 16) & 255) * (1 - t) + ((second >> 16) & 255) * t);
        int g = (int) (((first >> 8) & 255) * (1 - t) + ((second >> 8) & 255) * t);
        int b = (int) ((first & 255) * (1 - t) + (second & 255) * t);
        return (r << 16) | (g << 8) | b;
    }

    private static void drawGroundRing(MatrixStack matrices, VertexConsumer consumer, double x, double y, double z,
                                       double radius, int r, int g, int b, int a) {
        MatrixStack.Entry entry = matrices.peek();
        for (int i = 0; i < CIRCLE_SEGMENTS; i++) {
            double first = (Math.PI * 2.0 * i) / CIRCLE_SEGMENTS;
            double second = (Math.PI * 2.0 * (i + 1)) / CIRCLE_SEGMENTS;
            float x1 = (float) (x + Math.cos(first) * radius);
            float z1 = (float) (z + Math.sin(first) * radius);
            float x2 = (float) (x + Math.cos(second) * radius);
            float z2 = (float) (z + Math.sin(second) * radius);
            line(consumer, entry, x1, (float) y, z1, x2, (float) y, z2, r, g, b, a);
        }
    }

    private static void drawDiamond(MatrixStack matrices, VertexConsumer consumer, double x, double y, double z,
                                    double radius, int r, int g, int b, int a) {
        MatrixStack.Entry entry = matrices.peek();
        line(consumer, entry, (float) x, (float) (y + radius), (float) z,
                (float) (x + radius), (float) y, (float) z, r, g, b, a);
        line(consumer, entry, (float) (x + radius), (float) y, (float) z,
                (float) x, (float) (y - radius), (float) z, r, g, b, a);
        line(consumer, entry, (float) x, (float) (y - radius), (float) z,
                (float) (x - radius), (float) y, (float) z, r, g, b, a);
        line(consumer, entry, (float) (x - radius), (float) y, (float) z,
                (float) x, (float) (y + radius), (float) z, r, g, b, a);
    }

    private static void line(VertexConsumer consumer, MatrixStack.Entry entry,
                             float x1, float y1, float z1, float x2, float y2, float z2,
                             int r, int g, int b, int a) {
        consumer.vertex(entry, x1, y1, z1).color(r, g, b, a).normal(entry, 0.0f, 1.0f, 0.0f);
        consumer.vertex(entry, x2, y2, z2).color(r, g, b, a).normal(entry, 0.0f, 1.0f, 0.0f);
    }

    private static void renderHud(DrawContext context, net.minecraft.client.render.RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || client.options.hudHidden) return;
        int screenWidth = client.getWindow().getScaledWidth();
        int screenHeight = client.getWindow().getScaledHeight();
        double centerX = screenWidth / 2.0;
        double centerY = screenHeight / 2.0;
        double radiusX = Math.min(screenWidth, screenHeight) * 0.39;
        double radiusY = radiusX;
        long now = ClientState.serverNow();
        java.util.Set<Integer> drawnArrowSectors = new java.util.HashSet<>();

        for (ClientState.MarkerInfo marker : ClientState.MARKERS.values()) {
            if (!marker.dimension().equals(client.world.getRegistryKey().getValue().toString())) continue;
            if (marker.temporary() && !ClientConfig.INSTANCE.showTemporaryMarkers) continue;
            if (!marker.temporary() && !ClientConfig.INSTANCE.showMainMarkers) continue;
            if (marker.expiresAt() >= 0 && marker.expiresAt() <= now) continue;
            double dx = marker.x() - client.player.getX();
            double dz = marker.z() - client.player.getZ();
            double distance = Math.sqrt(dx * dx + dz * dz);
            if (distance < 2.0) continue;
            double absoluteYaw = Math.toDegrees(Math.atan2(-dx, dz));
            double relativeYaw = wrapDegrees(absoluteYaw - client.player.getYaw());
            // Arrows are for strongly off-screen bearings; markers mostly in front stay uncluttered.
            if (Math.abs(relativeYaw) < 42.0) continue;
            int sector = (int) Math.round(relativeYaw / 18.0);
            if (!drawnArrowSectors.add(sector)) continue;
            double radians = Math.toRadians(relativeYaw);
            int x = (int) (centerX + Math.sin(radians) * radiusX);
            int y = (int) (centerY - Math.cos(radians) * radiusY);
            int color = marker.color() | 0xFF000000;
            var matrices = context.getMatrices();
            matrices.push();
            matrices.translate(x, y, 0.0f);
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) relativeYaw));
            context.drawCenteredTextWithShadow(client.textRenderer, "▲", 0, -4, color);
            matrices.pop();
        }
    }

    private static double wrapDegrees(double value) {
        value %= 360.0;
        if (value >= 180.0) value -= 360.0;
        if (value < -180.0) value += 360.0;
        return value;
    }
}
