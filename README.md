# Auto Window Size

> Minecraft Forge 1.20.1 Client Mod | Auto window sizing, centering & minimum size lock

> **⚠ Platform note: This mod only supports the desktop edition of Minecraft (Windows / macOS / Linux) and is NOT compatible with mobile / Bedrock / phone editions.**

[中文版本](README_ZH-CN.md)

---

## Downloads

- **GitHub Releases**: [v1.0.4](https://github.com/3270728922/AutoWindowSize/releases)
- **CurseForge**: [auto-window-size](https://www.curseforge.com/minecraft/mc-mods/auto-window-size)
- **Modrinth**: [autowindowsize](https://modrinth.com/mod/autowindowsize)
- **MC百科 (MC百科)**: [Auto Window Size](https://www.mcmod.cn/class/30769.html)
- **BBSMC**: [autowindowsize](https://bbsmc.net/mod/autowindowsize)
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

### Core goal of this mod

The entire reason this mod exists is to turn the game window size from an **accident** into something **controlled, repeatable and constrained**:

- **Stable modpack experience**: the window size becomes something a pack author can plan for in advance, rather than being left to each player's habits.
- **Protect UI layouts**: the "minimum size lock" guarantees that any tuned interface never drifts out of place just because someone drags the window edge.
- **Lightweight and non-intrusive**: it only touches the window's own size and position. It does not modify any in-game mechanic or gameplay logic, so it should stay clear of conflicts with other mods.
- **Zero-config yet customizable**: it works out of the box (default 1280×720), and offers four ways to fine-tune it — config file, in-game button, hotkey, and commands.

### Design trade-offs

While building this mod I deliberately held a few lines:

- **Window only, nothing in-game**: it never changes GUI scale, never touches any in-game UI, and never hooks gameplay logic. The window is an OS-level concern, so it stays out of the game's internals and almost never conflicts with other mods.
- **Delayed, not instant**: it waits ~1.5s after launch before resizing, so the loading screen never stretches and low-end PCs get a little head start.
- **Low hard floor**: the hard minimum is fixed at 856×482 (16:9) on purpose — better to support older displays than to raise the bar.
- **Graceful fallback, not errors**: when the configured size exceeds the player's screen, it does not crash or pop errors; it just silently disables the lock and explains why in chat.
- **No key by default**: no hotkey is bound out of the box, so it never fights with the player's existing controls; those who want one can bind it in Controls.

### Who it is for

- Modpack authors who need UI layouts to stay consistent across resolutions.
- Players who like a small side window but are afraid of shrinking it by accident.
- Multi-monitor users who want the window to land centered on the right screen.

### Future plans

This is my (the author's) **first ever Minecraft mod**, so for now I am keeping things deliberate and small, getting one thing right before expanding:

- **Currently only Minecraft 1.20.1 / Forge is supported.** This is the only version actively maintained and tested.
- As a first-time mod developer, the priority right now is to make the existing features stable, well documented and polished — not to spread across versions prematurely.
- Once the current version has seen enough real use and is confirmed stable, I will consider porting to other Minecraft versions or loaders (e.g. NeoForge, Fabric, or other game versions).
- Until then, no release timeline for other versions is promised; progress will be posted on GitHub and the release pages.

---

## Features

- **Auto window size on launch**: Automatically resizes the game window to the configured resolution and centers it on the current monitor, with a 1.5-second delay to ensure smooth startup
- **Minimum size lock**: When locked, the window can only be enlarged, not shrunk below the configured size — protecting UI layouts
- **Native Video Settings entry**: A "Window Settings" button is natively inserted into `Options → Video Settings`, right below "Fullscreen Resolution", scrolling and scaling properly with the list
- **Window Settings screen**: A dedicated settings screen with lock toggle, a one-click center button, live screen/window resolution display (yellow and labeled "Fullscreen" in fullscreen), and real-time button state sync
- **Center Window button**: One-click centers the window on its current monitor while windowed; it is auto-disabled (with a reason) in fullscreen, maximized, minimized, or already-centered states
- **Client commands**: `/aws toggle`, `/aws lock`, `/aws unlock`, `/aws status`, `/aws gui`, `/aws center` — control the lock from chat
- **Fullscreen auto-disable**: Lock is temporarily disabled when entering fullscreen, and restored on exit based on pre-fullscreen state
- **Custom hotkey**: Bind a key in Controls settings to open the Window Settings screen (default: unbound)
- **Auto-disable on low resolution**: When the configured or hardcoded minimum resolution exceeds the player's screen resolution, lock is automatically disabled and the window is left unchanged
- **Status notification on join**: Each time you enter a world, the chat displays the current lock status
- **Live resolution display**: The Window Settings screen shows screen resolution and game window resolution, refreshing 0.5s after you stop dragging
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

> **Note**: When lock is enabled, the window's minimum size = config value. Since the config range is already bound to the hardcoded minimum (856×482), the config value can never drop below it, so the two can no longer conflict.

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
| `/aws center` | Center the window on its current monitor (warns when unavailable in fullscreen/maximized/minimized/already-centered) |

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
- While dragging the window edge, the resolution stays put and an orange hint appears: "Window size changing, auto-refresh 0.5s after release…"
- After stopping dragging for **0.5s**, the orange hint fades and the resolution updates to the current window size

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
# ==== Window Size ====
# Startup window width. This is also the minimum width the window
# is allowed to shrink to when the size lock is enabled.
# 启动时的窗口宽度。开启尺寸锁定后，窗口也不能缩小到该值以下。
#Range: 856 ~ 7680
width = 1280
# ==== Window Height ====
# Startup window height. This is also the minimum height the window
# is allowed to shrink to when the size lock is enabled.
# 启动时的窗口高度。开启尺寸锁定后，窗口也不能缩小到该值以下。
#Range: 482 ~ 4320
height = 720
```

> Comments are laid out as English above, Chinese below, with a single `====` heading. `Range` is generated automatically by Forge and not repeated.

### Notes

- Config values are read at game **launch**; modifying the config file during runtime will not take effect until restart
- The config range is bound to the hardcoded minimum (width 856, height 482): you **cannot** enter a value below that floor, so the config and the runtime lock can never drift out of sync
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
A: `/aws toggle` toggles lock, `/aws lock` enables it, `/aws unlock` disables it, `/aws status` shows the current state and screen resolution, `/aws gui` opens the settings screen, `/aws center` centers the window.

**Q: Does it support multi-monitor setups?**
A: Yes. The mod automatically detects which monitor the game window is on and centers it accordingly.

**Q: Why doesn't the resolution update instantly while I drag?**
A: The value stays put with an orange hint while dragging, so the numbers don't jump constantly; 0.5s after you release, the hint fades and the resolution updates to the current window size.

**Q: Lock is disabled, how do I recover it?**
A: Change the resolution in the config file to be less than or equal to the screen resolution, then restart the game.

**Q: Does this mod need to be installed on the server?**
A: No. This is a pure client-side mod that only affects the client's window size.

**Q: Does it conflict with other mods?**
A: This mod only modifies the game window's size and minimum size limit, and does not modify any in-game mechanics, so it generally does not conflict with other mods.

---

## Changelog

<details open>
<summary><strong>v1.0.4</strong> — Config UX, release-page links & earlier startup</summary>

### Added / Changed
- Config comments now use a clean EN-above / CN-below layout with a single `====` heading; `Range` is generated by Forge and not repeated
- Config range now starts from the hardcoded minimum (width 856, height 482) instead of 1
- The mods list (mods.toml) description is rewritten into a structured, detailed layout and now links to the CurseForge / Modrinth / MC百科 / BBSMC pages
- Better ModList support: clickable blue links and a "client" badge show in the mod details; links are clickable in the vanilla mod list too
- In the Window Settings screen, the resolution turns yellow and is labeled "(Fullscreen)" so windowed vs fullscreen is obvious at a glance
- Added a "Center Window" button: centers the window on its current monitor while windowed; auto-disabled (with a reason) in fullscreen, maximized, minimized, or already-centered states; added the `/aws center` command
- Docs now include a "not compatible with mobile" note, the mod's core goal, and future plans

### Changed
- The window now applies ~1.5 s (30 ticks) after launch instead of ~2 s

### Fixed
- Fixed the bug where the config could be set below the hardcoded resolution: the hardcoded minimum constants now live in Config, and both the config floor and the runtime lock point to the same constants, so they can no longer drift apart
- Fixed the "resizing…" hint staying on screen and freezing the UI when entering fullscreen right after changing the window size: in fullscreen the current size is aligned immediately without entering the drag hint; in windowed mode the value stays frozen while dragging and refreshes once 0.5s after release

</details>

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
