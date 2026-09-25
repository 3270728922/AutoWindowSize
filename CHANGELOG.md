# Changelog

[中文](CHANGELOG_ZH-CN.md)

Full release history for this mod. The latest version is expanded by default; older versions can be expanded on click.

---

<details open>
<summary><strong>v1.1.0</strong> — Borderless mode, always-on-top, keybinds, about page & massive UI overhaul</summary>

### Added
- **Borderless mode**: removes the window title bar and borders (GLFW_DECORATED = false). Supports three states: windowed borderless (simple implementation), maximized borderless, and fullscreen borderless (pseudo-fullscreen — borderless window at screen resolution). New `borderless` config option and `/aws borderless` command.
- **Auto-borderless on launch**: new `autoBorderless` config option (default false). When enabled, the game starts in borderless mode on the next launch. Toggle via `/aws borderless auto`.
- **Always-on-top (3 modes)**: new `alwaysOnTopMode` config option (0=off, 1=normal, 2=force). Normal mode uses GLFW_FLOATING. Force mode re-claims window focus every frame via `glfwFocusWindow` plus a focus-loss callback, so it can override other always-on-top windows (e.g. classroom monitoring software). Toggle via `/aws top`.
- **Cycle window state button**: one-click cycling through windowed → maximized → fullscreen in the settings screen. Also available as a keybind.
- **5 keybinds** (all unbound by default, configurable in Options → Controls → Auto Window Size):
  - Open Window Settings
  - Center Window
  - Toggle Borderless
  - Cycle Window State
  - Toggle Always-on-Top
  - All keybinds give chat feedback when pressed.
- **About page**: in-game info screen (accessed via the "About" button in settings) with 6 sections — Purpose, About, Usage, Downloads, Feedback, Author. Supports 20 languages via per-language `.txt` files.
- **Debug mode**: new `debug` config option. When enabled, outputs detailed diagnostic info (preset availability checks, borderless state transitions, window state changes, etc.) to the game log. Toggle via `/aws debug`.
- **New commands**: `/aws gui` (open settings), `/aws status` (show current state), `/aws toggle` (toggle lock), `/aws top` (cycle always-on-top), `/aws debug` (toggle debug), `/aws borderless` (toggle borderless, with `auto` subcommand). Total: 15 commands.
- **Help pagination**: `/aws help [page]` now supports 2 pages (8 commands per page), with a persistent title line and page indicator.
- **Custom resolution input live validation**: when typing in the custom width/height fields, the Apply button is disabled if the input is invalid (non-numeric or out of range). Hovering the disabled Apply button shows a tooltip explaining the valid range and which bound was exceeded.
- **Preset auto-disable expanded**: presets are now disabled if they are (1) larger than the screen resolution, (2) smaller than the configured minimum size, (3) smaller than the hard-coded minimum (856×482), or (4) matching the current window size.

### Changed
- **Settings entry confirmed in Accessibility Settings**: the "Window Settings" button lives in Options → Accessibility Settings (moved from Video Settings in v1.0.9). This is the permanent location, chosen for compatibility with Embeddium and other mods that completely replace the video settings screen.
- **Settings screen layout reworked**: fixed window size is now a large button; "Remember position" and "Center" are side-by-side; "Debug" and "About" are side-by-side at the bottom. The aspect ratio button cycles through ratios and shows the current resolution.
- **Info area finalized**: 4 lines — (1) screen resolution + centered indicator, (2) window state + config file status (always visible), (3) detailed status explanation, (4) custom input boundary hint (below input fields).
- **Scrolling system rebuilt**: the settings screen now extends `AbstractSelectionList` for a native Minecraft scrolling list, with the title and Done button having independent opaque backgrounds (matching vanilla options screens exactly). Scrollbar position matches vanilla.
- **Config comments reworked**: simplified per-option comments, removed duplicate Range lines (Forge auto-generates them), switched to English-only comments.
- **Language count**: 20 languages fully supported (en_us, zh_cn, zh_tw, ja_jp, ko_kr, ru_ru, fr_fr, de_de, es_es, pt_br, it_it, tr_tr, nl_nl, pl_pl, vi_vn, ar_sa, th_th, sv_se, cs_cz, id_id).

### Fixed
- Fixed the settings screen button layout overlapping the title and Done button (rebuilt with AbstractSelectionList).
- Fixed the scrollbar position overlapping buttons (now matches vanilla exactly).
- Fixed button highlight/selection border overflowing beyond the button bounds (rewrote `isSelectedItem()`).
- Fixed the aspect ratio button incorrectly showing "Custom" after toggling maximize / fullscreen.
- Fixed the aspect ratio button reverting to the previous ratio after clicking a preset (state sync issue).
- Fixed custom resolution input fields not updating in real-time when the window is resized by dragging.
- Fixed custom resolution input fields showing the previous value after switching to Custom from a preset.
- Fixed fixed window size not disabling resolution preset buttons (only disabled the ratio button).
- Fixed the window state detection in borderless mode (pseudo-fullscreen was incorrectly detected as maximized).
- Fixed borderless mode causing a gap at the top when switching from fullscreen to maximized.
- Fixed borderless mode causing the window to become windowed at fullscreen resolution after rapid state switching.
- Fixed force always-on-top mode losing focus when clicking the title bar of another window (added focus-loss callback).
- Fixed scroll position resetting to the top after clicking certain buttons (preserved scroll position via `lastScroll`).
- Fixed scroll position going out of bounds when switching to an aspect ratio with fewer presets.
- Fixed `window.json` not being generated on first launch (moved the ensure-check to `onTick` with a one-time flag).
- Fixed duplicate chat messages when entering fullscreen (removed the redundant "lock disabled" message; only the state-change message is shown).
- Fixed the Debug config option missing translation keys in the config screen (added `config.autowindowsize.window.debug` and `.tooltip` to all 20 languages).
- Fixed mouse wheel not scrolling when hovering over input fields or buttons.
- Fixed various minor UI alignment and spacing issues.

</details>

<details>
<summary><strong>v1.0.9</strong> — Resolution presets expansion, window position memory, config rework & UI polish</summary>

### Added
- **Resolution presets expanded**: 5 aspect ratios (16:9 / 16:10 / 4:3 / 5:4 / 21:9) with 12 presets each (3 rows). Click a preset to apply it instantly and center the window.
- **Custom resolution input**: when the aspect ratio is set to "Custom", two input fields (width + height) and an Apply button appear. Live boundary validation; values outside the valid range are clamped to the nearest valid value with a warning.
- **Configurable startup delay**: new `startupDelay` option in the config (0.5–10.0 seconds, default 1.5). Controls how long the mod waits after launch before applying the window size and position.
- **Window position memory**: new `rememberPosition` option (default false). When enabled, the window position and size are saved on exit and restored on the next launch instead of forcing the window to center. Compatible with auto-fullscreen / auto-maximize: the game launches fullscreen / maximized first, then restores the remembered position when you exit it.
- **Config files unified directory**: all config files moved to `config/AutoWindowSize/` (main config `config.toml` + saved window state `window.json`), keeping the config folder clean.
- **Config screen translations**: Forge config values now use `.translation()` keys, so option names and descriptions follow the game language when opened via a config-screen mod. 20 languages fully translated.
- **New commands**: `/aws help` (command list, pinned first), `/aws fullscreen` (toggle fullscreen), `/aws maximize` (toggle maximize), `/aws remember` (toggle position memory), `/aws resolution` (set resolution — supports `ratio preset` format and custom `width height` format, with tab-completion).
- **Button hover tooltips**: every button in the Window Settings screen now has a mouse-hover tooltip explaining what it does; tooltips support line wrapping.

### Changed
- **Settings entry moved** from Options → Video Settings to **Options → Accessibility Settings** (purpose: compatible with Embeddium and other mods that completely replace the video settings screen, which previously caused the "Window Settings" button to disappear).
- **Settings screen info area reworked** from two lines to four lines: (1) screen resolution + centered indicator, (2) window state + config file status (always visible), (3) detailed status explanation for the current state, (4) custom input boundary value + input requirement (always visible below the input fields).
- **Config comments reworked**: simplified per-option comments, removed duplicate Range lines (Forge auto-generates them), switched to English-only comments, added section separator lines.
- **Command order rearranged**: help → gui → status → toggle → lock → unlock → fixed → center → fullscreen → maximize → remember → resolution.
- **Language count expanded from 15 to 20**: added Arabic (ar_sa), Thai (th_th), Swedish (sv_se), Czech (cs_cz), Indonesian (id_id).

### Fixed
- Fixed the aspect ratio button incorrectly showing "Custom" after toggling maximize / fullscreen.
- Fixed the aspect ratio display lagging / bouncing back after clicking a resolution preset.
- Fixed the custom resolution input fields not updating live when the window is dragged.
- Fixed the fixed-window-size toggle not disabling the aspect ratio button and preset buttons when enabled.
- Fixed the Apply button and input field showing an oversized highlight background after clicking (spilling beyond the button bounds).
- Fixed the "Window Settings" button in Accessibility Settings shifting position / growing too wide when the game language has no corresponding lang file.
- Fixed the settings screen scrollbar overlapping the buttons; repositioned to match the vanilla scrollbar placement.
- Fixed auto-fullscreen on launch not centering the window after exiting fullscreen.
- Fixed various minor UI layout and spacing issues in the Window Settings screen.

### Notes
- **Config migration**: when upgrading from pre-1.0.9, manually delete the old `config/autowindowsize-client.toml` and the new `config/AutoWindowSize/` folder (if it exists), then launch the game to generate fresh config files.
- The auto-fullscreen-on-launch and window-position-memory features are independent implementations inspired by [Window Control](https://www.curseforge.com/minecraft/mc-mods/window-control); no code was copied.

</details>

<details>
<summary><strong>v1.0.8</strong> — Resolution presets + settings screen rework</summary>

### Added
- Resolution presets in the window settings screen, grouped by aspect ratio (16:9 / 16:10 / 4:3 / 5:4 / 21:9). Click a preset to apply it instantly, center the window, and save it to the config so it persists across launches.
- The aspect ratio cycles on click; the button shows "current → next".
- Presets larger than the current screen are auto-disabled (visible but not clickable).
- The screen resolution is re-checked at runtime, so changing the system resolution updates the UI live.

### Changed
- The settings screen was rebuilt on top of the vanilla scroll list (AbstractSelectionList), with scrollbar, top/bottom fade and mouse-wheel scrolling, matching the vanilla video settings look.
- Preset buttons wrap automatically, 4 per row, aligned with the single-column buttons above.
- Title moved to the vanilla-standard position.

### Fixed
- Fixed a startup crash on 1.20.1 caused by `InputConstants.UNKNOWN` (replaced with `-1` for unbound keys).

</details>

<details>
<summary><strong>v1.0.7</strong> — New: auto-fullscreen / auto-maximize on launch</summary>

### Added
- New "Auto-fullscreen on next launch" preference: a persistent toggle in the Window Settings screen. When on, the **next** launch starts in fullscreen. It does not change the current window; the choice is written back to the config file.
- New "Auto-maximized on next launch" preference: the matching persistent toggle to start maximized. The two are **mutually exclusive** — enabling one in the UI disables and greys out the other; in the config, if both guide targets are true, neither applies and both reset to false.
- New one-time onboarding for modpack authors: the `[startup]` config section now has `applyStartupGuide` (default `false`), `startFullscreen` and `startMaximized`. When an author sets `applyStartupGuide = true` and ships the pack, the player's first launch follows the guide target to start fullscreen or maximized once, syncs the matching in-game preference, and then `applyStartupGuide` is automatically written back to `false`. Afterwards only the player's own in-game setting is used.
- When auto-fullscreen triggers, the "set config resolution + center" step is skipped and the game goes straight to fullscreen. When auto-maximize triggers, the window is maximized and reuses the loading-phase-maximize recovery chain, so restoring later resizes to the config resolution and centers it. Entering/exiting fullscreen still goes through the existing lock/fixed state logic.

### Changed / Fixed
- Corrected the KLBBS download link to https://klpbbs.com/thread-173769-1-1.html.
- Documentation restructure: the README is now a one-screen intro; the detailed content moved to a Wiki (new English/Chinese Wiki files).
- Fixed overlapping of buttons, resolution text and disabled-reason messages in the Window Settings screen; reworked the layout with a uniform button column, resolution info that follows the button column automatically, and a bottom-pinned Done button, so future options won't crowd the screen.
- Simplified button labels to "feature: ON/OFF" (dropped the redundant "(click to enable/disable)") and added a hover tooltip explaining each feature.
- Rewrote the config comments: removed the `====` banners, kept a clean bilingual (English above, Chinese below) note per option, and added a `##` separator line between options.
- Fixed "no centering after exiting auto-fullscreen on launch": when the game was started by auto-fullscreen, exiting fullscreen now returns to the config resolution and centers; manual F11 toggles mid-game still restore the pre-fullscreen position.
- Fixed `autoFullscreen` and `autoMaximized` staying true when set both true by hand: on launch both are now reset to false, so the two buttons no longer stay mutually blocked.
- The one-time guide now yields to an existing player preference: if the player already enabled either startup preference, the pack's onboarding no longer overrides it and automatically resets `applyStartupGuide`, `startFullscreen` and `startMaximized` to false.

### Notes
- Safe by default: `applyStartupGuide` defaults to `false`, so a player installing the mod alone is never forced into fullscreen (same behaviour as before).
- Author workflow: after testing on your dev machine `applyStartupGuide` becomes `false`; set it back to `true` before packaging the modpack.
- The auto-fullscreen-on-launch idea draws on launching into a chosen window mode in [Window Control](https://www.curseforge.com/minecraft/mc-mods/window-control) (independent implementation, no copied code).

</details>

<details>
<summary><strong>v1.0.6</strong> — New: Fixed Window Size</summary>

### Added
- New "Fixed Window Size" toggle (button in the Window Settings screen + `/aws fixed` command): when on, the window is frozen at its **current** size (min = max = current size). It does **not** jump to the configured resolution and does **not** force a recenter — it simply cannot be resized anymore, and the title-bar maximize button is disabled at the OS level (GLFW_RESIZABLE=false removes WS_MAXIMIZEBOX on Windows), so a "fake maximized" state is impossible
- The fixed-window-size toggle is itself disabled while the window is maximized or fullscreen (with an explanatory message), instead of trying to lock the full-screen-sized maximized dimensions
- Fullscreen still works (GLFW fullscreen is not bound by window size limits) and returns to the fixed size on exit
- Fixed window size is only blocked by fullscreen/maximized state (temporarily disabled, restored on exit). It is **not** affected by the "config resolution too low" state, which only disables the minimum size lock — fixed window size and centering keep working because they do not depend on the config value

### Notes
- Fixed window size is a runtime toggle (same as the min size lock), off by default, reset on game restart; when fixed is on, the lock button has no additional effect (fixed takes priority)
- Inspiration: the fixed-window-size idea is inspired by the mod [Locked Window Size](https://modrinth.com/mod/locked-window-size) (LGPL-3.0). It is an independent implementation using only the public GLFW API; no source code was copied, so this mod remains MIT-licensed.

</details>

<details>
<summary><strong>v1.0.5</strong> — Fix: loading-phase fullscreen / maximized window handling</summary>

### Fixed
- Fixed the bug where, if the player manually went fullscreen or maximized the window while the game was still on the loading screen, the mod would still force the window back to the configured resolution once the main menu loaded. The startup init now checks the window state first.
- Further fixes: after maximizing on the loading screen and then un-maximizing, the window would land at the OS default size instead of the configured one; and after entering fullscreen on the loading screen and then exiting, the window was not centered. Now, when you exit fullscreen or un-maximize after that loading-phase state, the window is automatically resized back to the configured resolution and centered.
- Also fixed: maximizing first on the loading screen and then going fullscreen used to leave a "fake maximized" state after exiting fullscreen (the restore button still showed, but the actual size was the configured one). It now truly restores the maximized state in that case.
- And another fix in the same flow: after that restored maximized state, un-maximizing left the window at the right size but off-center. The full chain now ends with a recenter; normal in-game un-maximize is unaffected and still restores the previous position.

</details>

<details>
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
