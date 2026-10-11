# BuddyMarks — Minecraft Java 1.21.4 / Fabric

BuddyMarks is a combined client + server Fabric mod project. It adds teammate/friend management, synchronized team markers, and client-only visual preferences per teammate UUID.

## Important multiplayer requirement

**Install the same BuddyMarks JAR on the dedicated Fabric server and on every client that should share the system.** In single-player, the mod's common/server code runs in the integrated server. On an unmodified vanilla server or a server without BuddyMarks installed, synchronized friend and marker features cannot work; the mod reports this instead of pretending that the data was synchronized.

Accepted friendships form a connected team. Members of the connected team receive each other's markers and appear in the teammate list. A team can have up to 144 teammates in addition to the local player. Removing a teammate disconnects them from the shared team.

## Keybinds

The default keys are configurable in Minecraft's Controls menu:

- `O` — open BuddyMarks menu.
- `B` — place a temporary marker.
- `N` — create a named main marker.

The mod drains key presses while any screen is open, so a key pressed while typing in chat or browsing an inventory should not trigger a marker after the screen closes.

## Commands

Commands with a leading dot are intercepted locally and not sent as chat to the game server:

- `.friend add <nickname>` — send a friend request to a player who is online on the same server.
- `.friend list` — print the shared-team list and refresh local data.
- `.friend remove <nickname>` — remove a teammate from the shared team.
- `.friend accept <nickname>` — accept a pending request.
- `.friend deny <nickname>` — reject a pending request.
- `.friend help` — show command syntax.

The clickable chat request includes `«Принять»` and `«Отклонить»` actions.

## What the project includes

- Persistent server-side friendships/requests and shared marker storage.
- Temporary markers with configurable duration and main markers with names, expiry or manual-only removal.
- Marker color/size preferences, client-side rings/diamonds, named main-marker labels, and directional HUD indicators.
- Per-teammate UUID settings for hitbox outlines, decorative conical hats, animated color interpolation, glow-like outline layers, and local leather-armor tinting.
- Local teammate-hit blocking that only cancels attacks when the target is in the server-synchronized teammate list.
- Client settings stored in `config/buddymarks-client.json`; server data stored in the world's `data/buddymarks.json`.
- Request rate limits, input bounds, capped marker payload batches, and atomic JSON saves.

### Accuracy notes

- The hat's color cycling currently uses smoothly interpolated vertex colors, not a custom GLSL post-processing shader.
- The “glow” is rendered as several expanded bounding-box outlines; it is not a full-screen entity-outline post-process effect.
- The leather-armor mixins and rendering behavior need runtime verification against a real 1.21.4 client before being treated as release-tested.
- Visual settings are local to each client and are not broadcast to other users.

## Build the JAR

The project targets Java 21, Minecraft 1.21.4, Fabric Loader 0.16.10, Fabric API `0.119.2+1.21.4`, Fabric Loom 1.10.5, and Gradle 8.12.1.

### Build with GitHub Actions

The repository includes `.github/workflows/build.yml`:

1. Upload the contents of this project folder to a GitHub repository.
2. Open the repository's **Actions** tab and select **Build BuddyMarks (Minecraft 1.21.4)**.
3. Run the workflow manually, or let it run after a push.
4. Open the completed run and download the artifact named `BuddyMarks-Minecraft-1.21.4`.
5. Inside the artifact, use `buddymarks-0.1.0.jar` (the `-sources.jar`, if present, is not the runtime mod).

### Build locally

With Java 21 and Gradle 8.12.1 installed, run from the project folder:

```text
gradle --no-daemon clean build
```

The runtime mod should be in `build/libs/buddymarks-0.1.0.jar`.

## Testing status

The project files and JSON metadata have been checked for structural consistency. **A complete Gradle build, Minecraft launch, dedicated-server session, and multiplayer test have not been run in the generation environment** because Gradle dependencies could not be downloaded there. Therefore the project is not advertised as bug-free or release-tested. The included GitHub Actions workflow performs the actual Gradle build and will expose compile/build failures; the resulting mod still needs in-game and multiplayer testing.
