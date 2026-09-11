# Auto Window Size

> Minecraft Forge 1.20.1 Client Mod | Auto window sizing, centering & minimum size lock

[中文版本](README_ZH-CN.md)

---

## Downloads

- **GitHub Releases**: [v1.0.3](https://github.com/3270728922/AutoWindowSize/releases)
- **CurseForge**: [auto-window-size](https://www.curseforge.com/minecraft/mc-mods/auto-window-size)
- **Modrinth**: [autowindowsize](https://modrinth.com/mod/autowindowsize)
- **Issues / Feedback**: [GitHub Issues](https://github.com/3270728922/AutoWindowSize/issues)

---

## Table of Contents

- [Purpose](#purpose)
- [Features](#features)
- [Resolution Priority](#resolution-priority)
- [Lock Mechanism](#lock-mechanism)
- [Low Resolution Detection](#low-resolution-detection)
- [Fullscreen Behavior](#fullscreen-behavior)
- [Commands](#commands)
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

- **Auto window size on launch**: Automatically resizes the game window to the configured resolution and centers it on the current monitor, with a 2-second delay to ensure smooth startup
- **Minimum size lock**: When locked, the window can only be enlarged, not shrunk below the configured size — protecting UI layouts
- **Native Video Settings entry**: A "Window Settings" button is natively inserted into `Options → Video Settings`, right below "Fullscreen Resolution", scrolling and scaling properly with the list
- **Window Settings screen**: A dedicated settings screen with lock toggle, live screen/window resolution display, and real-time button state sync
- **Client commands**: `/aws toggle`, `/aws lock`, `/aws unlock`, `/aws status`, `/aws gui` — control the lock from chat
- **Fullscreen auto-disable**: Lock is temporarily disabled when entering fullscreen, and restored on exit based on pre-fullscreen state
- **Custom hotkey**: Bind a key in Controls settings to open the Window Settings screen (default: unbound)
- **Auto-disable on low resolution**: When the configured or hardcoded minimum resolution exceeds the player's screen resolution, lock is automatically disabled and the window is left unchanged
- **Status notification on join**: Each time you enter a world, the chat displays the current lock status
- **Live resolution display**: The Window Settings screen shows screen resolution and game window resolution in real time, auto-refreshing 1 second after window drag
- **Multi-monitor support**: Automatically detects which monitor the game window is on and centers it accordingly

---

## Resolution Priority

The window size follows this priority (highest to lowest):

```
Hardcoded minimum (856×482)
        ↓
Config file value (default 1280×720, configurable)
        ↓
Player manual drag (only when lock is disabled)
```

| Priority | Source | Default | Description |
|----------|--------|---------|-------------|
| 1 (highest) | Hardcoded in code | 856×482 | Hardcoded minimum resolution, 16:9 aspect ratio, never goes below this |
| 2 | Config file | 1280×720 | Modified in `config/autowindowsize-client.toml`, read on launch |
| 3 (lowest) | Player drag | — | Only effective when lock is turned off; player can freely drag window edges |

> **Note**: When lock is enabled, the window's minimum size = config value (but not below the hardcoded minimum). If the config value is below 856×482, 856×482 is used as the actual minimum size.

---

## Lock Mechanism

### When Lock is ON

- The window's **minimum size** is locked to the config value (not below 856×482)
- The window **can be enlarged** with no upper limit
- The window **cannot be shrunk** below the locked size
- Minimum size limit is re-applied on exit fullscreen if lock was on before entering fullscreen

### When Lock is OFF

- The window size is **completely free**, can be enlarged or shrunk arbitrarily
- No minimum size limit is set
- The player can re-enable lock via the UI button, commands, or key binding

### When Lock is DISABLED

The lock feature is **completely unavailable** in these cases:

- **Resolution too low**: Config or hardcoded minimum exceeds screen resolution — button is greyed out in red, window size is left unchanged
- **Fullscreen mode**: Temporarily disabled while in fullscreen — button is greyed out in red, automatically restored on exit

Pressing the key binding or using commands while disabled shows a red chat message explaining why.

---

## Low Resolution Detection

### Trigger Condition

Lock is automatically disabled when **any** of the following conditions is met:

```
Config width > Current screen width
Config height > Current screen height
Hardcoded min width (856) > Current screen width
Hardcoded min height (482) > Current screen height
```

### Behavior After Trigger

1. **Window size unchanged**: Keeps the window size the player had before launch
2. **No minimum size limit**: Does not call GLFW's window size limit
3. **Lock marked as disabled**: `lockDisabled = true`
4. **Chat notification on world enter**: Red text explaining the reason
5. **Greyed button in Window Settings**: Shows "Lock Disabled (Resolution Too Low)", unclickable
6. **Commands and key binding blocked**: Pressing them shows a red message

### How to Recover

Change the resolution in the config file to be less than or equal to the screen resolution, then **restart the game**.

> Note: Detection runs once at game launch. Changing the screen resolution during runtime will not re-trigger detection.

---

## Fullscreen Behavior

When entering fullscreen (F11):

- Lock is **temporarily disabled** regardless of its current state
- The pre-fullscreen lock state is recorded
- A red chat message shows: "Lock unavailable in fullscreen, restored on exit"
- The lock button in Window Settings is greyed out

When exiting fullscreen (F11):

- Window size and position are **restored by Minecraft** to the pre-fullscreen state (the mod does not force any size)
- If lock was **ON** before fullscreen, the minimum size limit is re-applied
- If lock was **OFF** before fullscreen, no minimum size limit is set
- If lock is permanently disabled (low resolution), window size is not changed

---

## Commands

All commands start with `/aws` (client-side, only works in-game):

| Command | Description |
|---------|-------------|
| `/aws toggle` | Toggle lock ON/OFF |
| `/aws lock` | Enable lock (no-op if already enabled) |
| `/aws unlock` | Disable lock (no-op if already disabled) |
| `/aws status` | Show current lock status and screen resolution |
| `/aws gui` | Open the Window Settings screen |

When lock is disabled (low resolution or fullscreen), all lock-related commands show a red message explaining why.

---

## In-Game Status Notification

Each time you enter a world, the chat shows the current lock status once:

| Status | Message | Color |
|--------|---------|-------|
| Lock ON | `[AutoWindowSize] ✔ Window lock enabled` | Green |
| Lock OFF | `[AutoWindowSize] ✔ Window lock disabled, resize freely` | Gray |
| Low Resolution | `[AutoWindowSize] ✘ Lock auto-disabled — config resolution exceeds screen` | Red |
| Fullscreen | `[AutoWindowSize] ✘ Lock unavailable in fullscreen, restored on exit` | Red |

- Shows only **once** per world entry
- Notifications appear in the chat, not as a title/subtitle

---

## Window Settings Screen

### How to Open

1. Main Menu: `Options...` → `Video Settings...` → Click the `Window Settings` button
2. In-game: `Esc` → `Options...` → `Video Settings...` → Click the `Window Settings` button
3. Press your bound key binding (default: unbound)
4. Run `/aws gui` in chat

### Screen Contents

| Element | Description |
|---------|-------------|
| Screen Resolution | Current monitor resolution (e.g. 1920 × 1080), white text |
| Window Resolution | Current game window size (e.g. 1280 × 720), light green text |
| Lock Button | Toggle lock ON/OFF; greyed out with red reason when disabled |
| Done Button | Return to Video Settings screen |

### Live Refresh Mechanism

- Button state syncs **in real time** — no need to reopen the screen
- While dragging the window edge, an orange hint appears: "Window size changing, auto-refresh 1s after release…"
- After stopping dragging for **1 second**, the window resolution value automatically updates

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
- If the config value is below the hardcoded minimum (856×482), the actual minimum lock size is still 856×482
- If the config value is higher than the screen resolution, lock is automatically disabled and the window size is not changed

---

## Key Binding

### Default State

**Unbound**. The mod does not occupy any keys; the player must bind one in Controls settings.

### How to Bind

1. `Options...` → `Controls...`
2. Search for "Auto Window Size"
3. Click "Open Window Settings"
4. Press the key you want to bind
5. Click "Done" to save

### Usage

- Press the bound key to open the Window Settings screen
- Works in both main menu and in-game

---

## Multi-Monitor Support

- On launch, the mod automatically detects which monitor the game window is **currently on** (based on the window center point)
- The window is centered on that monitor, not always on the primary monitor
- When dragging the window across screens (window center between two monitors), it defaults to the primary monitor

---

## Technical Info

| Item | Value |
|------|-------|
| Minecraft Version | 1.20.1 |
| Forge Version | 47.x |
| Mod ID | `autowindowsize` |
| Mod Type | Client-side |
| Hardcoded Min Resolution | 856×482 (16:9) |
| Default Config Resolution | 1280×720 |
| License | MIT |
| Language | Java |

---

## FAQ

**Q: Why didn't the window change after I edited the config resolution?**
A: If the configured resolution is higher than your screen resolution, the mod automatically disables the lock and leaves the window unchanged. Set the config value to be less than or equal to your screen resolution, then restart the game.

**Q: Can I still enlarge the window when locked?**
A: Yes. Locking only restricts the minimum size, not the maximum — you can freely enlarge the window.

**Q: What do the `/aws` commands do?**
A: `/aws toggle` toggles lock, `/aws lock` enables it, `/aws unlock` disables it, `/aws status` shows the current state and screen resolution, `/aws gui` opens the settings screen.

**Q: Does it support multi-monitor setups?**
A: Yes. The mod automatically detects which monitor the game window is on and centers it accordingly.

**Q: Why doesn't the window resolution update instantly when I drag the window?**
A: To avoid lag during dragging, the value updates 1 second after you release the window edge. An orange hint is shown while dragging.

**Q: Lock is disabled, how do I recover it?**
A: Change the resolution in the config file to be less than or equal to the screen resolution, then restart the game.

**Q: Does this mod need to be installed on the server?**
A: No. This is a pure client-side mod that only affects the client's window size.

**Q: Does it conflict with other mods?**
A: This mod only modifies the game window's size and minimum size limit, and does not modify any in-game mechanics, so it generally does not conflict with other mods.

---

## Changelog

<details>
<summary><strong>v1.0.3</strong> — Delayed init & improved fullscreen messages</summary>

### Added
- Window size is now set 2 seconds after game launch, avoiding stretch during loading screen and onboarding
- Fullscreen disable messages now show whether lock will be re-enabled or stay disabled on exit, based on pre-fullscreen state
- Bilingual (Chinese + English) config file comments

### Fixed
- First-launch onboarding screen (AccessibilityOnboarding) no longer skips window initialization

</details>

<details>
<summary><strong>v1.0.2</strong> — Commands, fullscreen support & UI improvements</summary>

### Added
- `/aws` command system: `toggle`, `lock`, `unlock`, `status`, `gui`
- Fullscreen auto-disable: lock is temporarily disabled in fullscreen, restored on exit based on pre-fullscreen state
- Real-time button state sync in Window Settings screen (no need to reopen)
- Red reason text when lock is disabled (low resolution or fullscreen)
- Screen resolution display in `/aws status` output
- GitHub repository and Issues links in Mods list

### Changed
- Key binding now opens the Window Settings screen (previously toggled lock)
- Hardcoded minimum resolution lowered from 1024×576 to 856×482 for older monitor support
- Exit fullscreen no longer forces window size — Minecraft restores pre-fullscreen state naturally
- Window resolution label renamed to "当前游戏窗口分辨率" to avoid confusion with MC's "Fullscreen Resolution"
- Improved chat message styling with ✔/✘/ℹ/● symbols for clarity
- Authors: JujuMLQwQ, ML

### Fixed
- ConfigScreen: minimized window restore no longer gets stuck on "refreshing" hint
- Fullscreen detection now uses GLFW native API for reliability
- Button width now matches native Video Settings buttons exactly

</details>

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

- **JujuMLQwQ** — Developer
- **ML** — Contributor

Thanks to all players and modpack creators who use this mod.
Feedback and suggestions are welcome via [Issues](https://github.com/3270728922/AutoWindowSize/issues).
