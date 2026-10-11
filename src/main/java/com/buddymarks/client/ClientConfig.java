package com.buddymarks.client;

import com.buddymarks.BuddyMarksMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

/** User-side preferences. Visual choices are deliberately local to this client. */
public final class ClientConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("buddymarks-client.json");
    public static ClientConfig INSTANCE = new ClientConfig();

    public boolean blockTeammateHits = true;
    public boolean showTemporaryMarkers = true;
    public boolean showMainMarkers = true;
    public int temporaryMarkerSeconds = 10;
    public int markerSize = 100;
    public float markerOpacity = 0.95f;
    public int temporaryMarkerColor = 0x39D7FF;
    public int mainMarkerColor = 0xFFBE47;
    public int mainMarkerDefaultSize = 100;
    public int mainMarkerDurationIndex = 0;
    public int customMainMarkerHours = 0;
    public boolean mainMarkersManualRemoval = false;
    public Map<String, VisualSettings> visuals = new HashMap<>();

    public static void load() {
        try {
            if (Files.exists(FILE)) {
                ClientConfig loaded = GSON.fromJson(Files.readString(FILE), ClientConfig.class);
                if (loaded != null) INSTANCE = loaded;
            }
        } catch (IOException | JsonParseException exception) {
            BuddyMarksMod.LOGGER.error("Failed to load BuddyMarks client config", exception);
            try {
                if (Files.exists(FILE)) Files.copy(FILE, FILE.resolveSibling("buddymarks-client.json.broken"), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) { }
        }
        if (INSTANCE.visuals == null) INSTANCE.visuals = new HashMap<>();
        INSTANCE.temporaryMarkerSeconds = clamp(INSTANCE.temporaryMarkerSeconds, 1, 3600);
        INSTANCE.markerSize = clamp(INSTANCE.markerSize, 50, 200);
        INSTANCE.markerOpacity = Math.max(0.15f, Math.min(1.0f, INSTANCE.markerOpacity));
        INSTANCE.temporaryMarkerColor &= 0xFFFFFF;
        INSTANCE.mainMarkerColor &= 0xFFFFFF;
        INSTANCE.mainMarkerDefaultSize = clamp(INSTANCE.mainMarkerDefaultSize, 50, 200);
        INSTANCE.mainMarkerDurationIndex = clamp(INSTANCE.mainMarkerDurationIndex, 0, 3);
        INSTANCE.customMainMarkerHours = clamp(INSTANCE.customMainMarkerHours, 0, 168);
        for (VisualSettings settings : INSTANCE.visuals.values()) {
            if (settings == null) continue;
            if (settings.hatColors == null || settings.hatColors.length == 0) settings.hatColors = new int[]{settings.hatColor};
            if (settings.hatColors.length > 5) settings.hatColors = java.util.Arrays.copyOf(settings.hatColors, 5);
            settings.hatColorCount = Math.max(1, Math.min(5, settings.hatColorCount));
            settings.hatAnimationSpeed = Math.max(0.1f, Math.min(4.0f, settings.hatAnimationSpeed));
            settings.hitboxColor &= 0xFFFFFF;
            settings.hatColor &= 0xFFFFFF;
            settings.glowColor &= 0xFFFFFF;
            settings.armorColor &= 0xFFFFFF;
            settings.helmetColor &= 0xFFFFFF;
            settings.chestColor &= 0xFFFFFF;
            settings.legsColor &= 0xFFFFFF;
            settings.bootsColor &= 0xFFFFFF;
        }
    }

    public static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Path temp = FILE.resolveSibling(FILE.getFileName() + ".tmp");
            Files.writeString(temp, GSON.toJson(INSTANCE));
            try {
                Files.move(temp, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException unsupportedAtomicMove) {
                Files.move(temp, FILE, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            BuddyMarksMod.LOGGER.error("Failed to save BuddyMarks client config", exception);
        }
    }

    public static VisualSettings visualFor(String uuid) {
        return INSTANCE.visuals.computeIfAbsent(uuid, ignored -> new VisualSettings());
    }

    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }

    public static final class VisualSettings {
        public boolean showHitbox = false;
        public boolean showHat = false;
        public boolean rainbowHat = false;
        public boolean showGlow = false;
        public boolean tintLeatherArmor = false;
        public int hitboxColor = 0x35E6FF;
        public int hatColor = 0xE8E8E8;
        public int armorColor = 0xFF5555;
        public int helmetColor = 0xFF5555;
        public int chestColor = 0x55AAFF;
        public int legsColor = 0x55FF55;
        public int bootsColor = 0xFF55FF;
        public int glowColor = 0xAA55FF;
        public int[] hatColors = new int[]{0xE8E8E8};
        public int hatColorCount = 1;
        public float hatAnimationSpeed = 1.0f;
    }
}
