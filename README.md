# Auto Window Size

> Minecraft Forge 1.20.1 Client Mod | Auto window sizing, centering & minimum size lock

[中文版本](README_ZH-CN.md) | [Bilingual Version](README_BILINGUAL.md)

---

## Table of Contents

- [Purpose](#purpose)
- [Features](#features)
- [Resolution Priority](#resolution-priority)
- [Lock Mechanism](#lock-mechanism)
- [Low Resolution Detection](#low-resolution-detection)
- [Exit Fullscreen Behavior](#exit-fullscreen-behavior)
- [In-Game Status Notification](#in-game-status-notification)
- [Window Settings Screen](#window-settings-screen)
- [Installation](#installation)
- [Configuration](#configuration)
- [Key Binding](#key-binding)
- [Multi-Monitor Support](#multi-monitor-support)
- [Technical Info](#technical-info)
- [FAQ](#faq)
- [Changelog](#changelog)
- [Credits](#credits)

---

## Purpose

When creating modpacks, I often ran into this problem: after carefully tuning the UI layout of various mods, players would launch the game at a different resolution, the window size would change, and all the UI layouts would break.

To solve this, I created this mod: it automatically sets the window to a specified size and centers it on launch, while locking the minimum window size — ensuring that modpack UI layouts stay consistent across different players' computers.

---

## Features

- **Auto window size on launch**: Automatically resizes the game window to the configured resolution and centers it on the current monitor
- **Minimum size lock**: When locked, the window can only be enlarged, not shrunk below the configured size — protecting UI layouts
- **Native Video Settings entry**: A "Window Settings" button is natively inserted into `Options → Video Settings`, right below "Fullscreen Resolution", scrolling and scaling properly with the list
- **Window Settings screen**: A dedicated settings screen with manual lock toggle and live display of screen resolution and game window resolution
- **Custom hotkey toggle**: Bind a key in Controls settings to quickly toggle lock state (default: unbound)
- **Auto-disable on low resolution**: When the configured or hardcoded minimum resolution exceeds the player's screen resolution, lock is automatically disabled and the window is left unchanged to avoid display bugs
- **Restore on exit fullscreen**: When switching from fullscreen back to windowed mode, the configured window size and position are automatically restored
- **Status notification on join**: Each time you enter a world, the chat displays the current lock status (Locked / Unlocked / Disabled)
- **Live resolution display**: The Window Settings screen shows the current screen resolution and game window resolution in real time, auto-refreshing 1 second after you stop dragging the window
- **Multi-monitor support**: Automatically detects which monitor the game window is on and centers it accordingly

---

## Resolution Priority

The window size follows this priority (highest to lowest):

```
Hardcoded minimum (1024×576)
        ↓
Config file value (default 1280×720, configurable)
        ↓
Player manual drag (only when lock is disabled)
```

| Priority | Source | Default | Description |
|----------|--------|---------|-------------|
| 1 (highest) | Hardcoded in code | 1024×576 | Hardcoded minimum resolution, 16:9 aspect ratio, never goes below this |
| 2 | Config file | 1280×720 | Modified in `config/autowindowsize-client.toml`, read on launch |
| 3 (lowest) | Player drag | — | Only effective when lock is turned off; player can freely drag window edges |

> **Note**: When lock is enabled, the window's minimum size = config value (but not below the hardcoded minimum). If the config value is below 1024×576, 1024×576 is used as the actual minimum size.

---

## Lock Mechanism

### When Lock is ON

- The window's **minimum size** is locked to the config value (not below 1024×576)
- The window **can be enlarged** with no upper limit
- The window **cannot be shrunk** below the locked size
- On exit fullscreen, the window is restored to the config size and centered, and the minimum size limit is re-applied

### When Lock is OFF

- The window size is **completely free**, can be enlarged or shrunk arbitrarily
- On exit fullscreen, the window is restored to the config size and centered, but **no** minimum size limit is set
- The player can re-enable lock via the UI button or custom hotkey

### When Lock is DISABLED

- The lock feature is **completely unavailable**, the button is greyed out and unclickable
- The window size is **not changed**, keeping the player's original size
- On exit fullscreen, the window size is **not changed**
- Pressing the custom hotkey shows a chat message: "Resolution too low, lock feature is disabled"
- On entering a world, the chat shows the reason for disabling

---

## Low Resolution Detection

### Trigger Condition

Lock is automatically disabled when **any** of the following conditions is met:

```
Config width > Current screen width
Config height > Current screen height
Hardcoded min width (1024) > Current screen width
Hardcoded min height (576) > Current screen height
```

### Behavior After Trigger

1. **Window size unchanged**: Keeps the window size the player had before launch, does not force it to the config value
2. **No minimum size limit**: Does not call GLFW's window size limit
3. **Lock auto-turned-off**: `lockEnabled = false`
4. **Marked as disabled**: `lockDisabled = true`
5. **Chat notification on world enter**: Red text explaining the reason for disabling
6. **Greyed button in Window Settings**: Shows "Lock Disabled (Resolution Too Low)", unclickable
7. **Re-notification on hotkey press**: When pressing the toggle lock key, the chat again shows that the resolution is too low

### How to Recover

Change the resolution in the config file to be less than or equal to the screen resolution, then **restart the game**.

> Note: Detection runs once at game launch. Changing the screen resolution during runtime will not re-trigger detection.

---

## Exit Fullscreen Behavior

When switching from fullscreen back to windowed mode (F11), the mod's behavior depends on the current lock state:

| Lock State | Behavior |
|------------|----------|
| Lock ON | Restore config window size and center, re-apply minimum size limit |
| Lock OFF | Restore config window size and center, **do not set** minimum size limit |
| Lock DISABLED | **Do not change** window size, keep the size after exiting fullscreen |

---

## In-Game Status Notification

Each time you enter a world from the main menu or loading screen, the chat shows the current lock status once:

| Status | Message | Color |
|--------|---------|-------|
| Lock ON | `[AutoWindowSize] Minimum window locked` | Green |
| Lock OFF | `[AutoWindowSize] Window size unlocked, resize freely` | Gray |
| Lock DISABLED | `[AutoWindowSize] Config or minimum resolution exceeds screen resolution, window lock auto-disabled, window size unchanged` | Red |

- Shows only **once** per world entry
- After returning to the main menu and re-entering a world, it shows **again**
- Notifications appear in the chat, not as a title/subtitle

---

## Window Settings Screen

### How to Open

1. Main Menu: `Options...` → `Video Settings...` → Click the `Window Settings` button
2. In-game: `Esc` → `Options...` → `Video Settings...` → Click the `Window Settings` button

> The "Window Settings" button is natively inserted into the Video Settings list right below the "Fullscreen Resolution" row, scrolling and scaling properly with the list and GUI scale.

### Screen Contents

| Element | Description |
|---------|-------------|
| Screen Resolution | Current primary monitor resolution (e.g. 1920 × 1080), white text |
| Game Resolution | Current game window framebuffer pixel size (e.g. 1280 × 720), light green text |
| Window Lock Button | Click to toggle lock ON/OFF; greyed out and unclickable when lock is disabled |
| Done Button | Return to Video Settings screen |

### Live Refresh Mechanism

- On opening the screen, the current window resolution is read once
- While dragging the window edge to resize, the screen shows an orange hint: "Window size changing, auto-refresh 1s after release…"
- After stopping dragging for **1 second**, the "Game Resolution" value automatically updates
- The delayed refresh avoids frequent text redraws during dragging that could cause lag

---

## Installation

1. Ensure Minecraft 1.20.1 with Forge 47.x is installed
2. Place the mod jar file into your `.minecraft/mods` folder
3. Launch the game

> This is a **client-side mod** and does not need to be installed on the server.

---

## Configuration

### File Path

```
.minecraft/config/autowindowsize-client.toml
```

### Config Options

```toml
[window]
    # Startup window width, also the minimum width when locked
    # Range: 1 ~ 7680
    # Default: 1280
    width = 1280

    # Startup window height, also the minimum height when locked
    # Range: 1 ~ 4320
    # Default: 720
    height = 720
```

### Notes

- Config values are read at game **launch**; modifying the config file during runtime will not take effect until restart
- If the config value is below the hardcoded minimum (1024×576), the actual minimum lock size is still 1024×576
- If the config value is higher than the screen resolution, lock is automatically disabled and the window size is not changed

---

## Key Binding

### Default State

**Unbound**. The mod does not occupy any keys; the player must bind one in Controls settings.

### How to Bind

1. `Options...` → `Controls...`
2. Search for "Auto Window Size" or scroll to find the "Auto Window Size" category
3. Click "Toggle Window Size Lock"
4. Press the key you want to bind (can be a keyboard key or mouse button)
5. Click "Done" to save

### Usage

- In-game (inside a world), press the bound key to toggle lock ON/OFF
- The chat shows the state after toggling
- When lock is disabled, pressing the key does not toggle the state; instead, it shows that the resolution is too low

---

## Multi-Monitor Support

- On launch, the mod automatically detects which monitor the game window is **currently on** (based on the window center point)
- The window is centered on that monitor, not always on the primary monitor
- When dragging the window across screens (window center between two monitors), it defaults to the primary monitor
- When getting the current monitor resolution, it also uses the resolution of the monitor the window is on, not the primary monitor

---

## Technical Info

| Item | Value |
|------|-------|
| Minecraft Version | 1.20.1 |
| Forge Version | 47.x |
| Mod ID | `autowindowsize` |
| Mod Type | Client-side |
| Hardcoded Min Resolution | 1024×576 (16:9) |
| Default Config Resolution | 1280×720 |
| License | MIT |
| Language | Java |

---

## FAQ

**Q: Why didn't the window change after I edited the config resolution?**
A: If the configured resolution is higher than your screen resolution, the mod automatically disables the lock and leaves the window unchanged. Set the config value to be less than or equal to your screen resolution, then restart the game.

**Q: Can I still enlarge the window when locked?**
A: Yes. Locking only restricts the minimum size, not the maximum — you can freely enlarge the window.

**Q: How do I bind a custom hotkey to toggle lock?**
A: `Options...` → `Controls...` → Search for "Auto Window Size" → Click "Toggle Window Size Lock" → Press the key you want. Default is unbound.

**Q: Does it support multi-monitor setups?**
A: Yes. The mod automatically detects which monitor the game window is on and centers it accordingly.

**Q: Why doesn't the game resolution in the Window Settings screen update in real time?**
A: To avoid frequent text redraws during window dragging that could cause lag, the mod updates the value 1 second after you stop dragging. An orange hint is shown while dragging.

**Q: Lock is disabled, how do I recover it?**
A: Change the resolution in the config file to be less than or equal to the screen resolution, then restart the game. Lock disabling is only detected at launch and does not auto-recover during runtime.

**Q: Does this mod need to be installed on the server?**
A: No. This is a pure client-side mod that only affects the client's window size; the server does not need it installed.

**Q: Does it conflict with other mods?**
A: This mod only modifies the game window's size and minimum size limit, and does not modify any in-game mechanics, so it generally does not conflict with other mods. If used simultaneously with other mods that modify window size, there may be conflicts.

---

## Changelog

<details>
<summary><strong>v1.0.1</strong> — Video Settings native entry & Window Settings screen</summary>

### Added
- Native "Window Settings" button entry in Video Settings (below Fullscreen Resolution)
- New Window Settings screen with manual lock toggle
- Live display of screen resolution and game window resolution
- Chat notification of lock status when entering a world
- Auto-refresh resolution display 1s after window drag (with dragging hint)

### Changed
- Key binding default changed to unbound (player sets it in Controls)
- Default resolution changed from 1440×810 to 1280×720 for better compatibility with older monitors
- Improved chat notification text styling with more professional colors and formatting

### Fixed
- Rewrote fullscreen logic: when config resolution exceeds screen resolution, lock is auto-disabled and window is left unchanged to avoid display bugs
- Fixed button layout issues at different GUI scales — now natively inserted into the list, scrolling and scaling properly
- Removed "exit game on low resolution" design, replaced with chat notification
- When lock is disabled, exiting fullscreen no longer changes window size

</details>

<details>
<summary><strong>v1.0.0</strong> — Initial release</summary>

- Initial release
- Auto window sizing and centering on launch
- Minimum window size locking
- Custom hotkey toggle
- Multi-monitor support

</details>

---

## Credits

Thanks to all players and modpack creators who use this mod.
Feedback and suggestions are welcome via Issues.
#（注：内容由AI生成）
