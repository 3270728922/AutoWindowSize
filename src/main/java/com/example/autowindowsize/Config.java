package com.example.autowindowsize;

import net.minecraftforge.common.ForgeConfigSpec;

public class Config {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.IntValue WINDOW_WIDTH;
    public static final ForgeConfigSpec.IntValue WINDOW_HEIGHT;
    // 记住窗口位置：开启后退出游戏时保存当前窗口位置与大小，下次启动恢复到该位置
    // （而不是强制居中）。与自动全屏/最大化兼容：启动时先全屏/最大化，退出后恢复记忆位置。
    public static final ForgeConfigSpec.BooleanValue REMEMBER_POSITION;
    // 值1：一次性"首次引导"开关（整合包作者用）。为 true 时，本次启动按 startFullscreen
    // 决定是否自动全屏，并把玩家偏好 autoFullscreen 同步成 startFullscreen；随后本值自动写回 false。
    // 默认 false：玩家单独安装时不被强制全屏。
    public static final ForgeConfigSpec.BooleanValue APPLY_STARTUP_GUIDE;
    // 值2：引导目标。当值1为 true 时，是否要在这次启动自动全屏。
    // 注意：startFullscreen 与 startMaximized 互斥，二者不能同时为 true。
    public static final ForgeConfigSpec.BooleanValue START_FULLSCREEN;
    // 值2b：引导目标。当值1为 true 时，是否要在这次启动自动最大化。
    // 与 startFullscreen 互斥；若二者同时为 true，本次启动两个都不生效，并自动都写回 false。
    public static final ForgeConfigSpec.BooleanValue START_MAXIMIZED;
    // 玩家在游戏内设置里的持久化偏好：下次启动是否自动全屏。
    // 值1为 false 时（绝大多数情况），启动只看这一项。与 autoMaximized 互斥。
    public static final ForgeConfigSpec.BooleanValue AUTO_FULLSCREEN;
    // 玩家在游戏内设置里的持久化偏好：下次启动是否自动最大化。与 autoFullscreen 互斥。
    public static final ForgeConfigSpec.BooleanValue AUTO_MAXIMIZED;
    // 启动延迟（秒）：启动后等待多久再应用窗口大小/位置，避免拉伸加载界面。
    // 范围 0.5 ~ 10.0，默认 1.5。
    public static final ForgeConfigSpec.DoubleValue STARTUP_DELAY;

    // 代码硬编码的最小分辨率（16:9）。配置文件的取值下限与运行时锁定下限都以这里为准，
    // 避免出现"配置能填低于硬写死分辨率的值"的脱节 bug。
    public static final int HARD_MIN_WIDTH = 856;
    public static final int HARD_MIN_HEIGHT = 482;

    // 允许的最大分辨率（超宽 / 8K 上限，仅作取值保护）
    public static final int MAX_WIDTH = 7680;
    public static final int MAX_HEIGHT = 4320;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.push("window");

        WINDOW_WIDTH = builder
                .comment(
                        "=== Window Size ===",
                        "Startup window width. Also the minimum width when the minimum-size lock is on.",
                        "Modpack authors can set this to the size that best fits the modpack UI layout.")
                .translation("config.autowindowsize.window.width")
                .defineInRange("width", 1280, HARD_MIN_WIDTH, MAX_WIDTH);

        WINDOW_HEIGHT = builder
                .comment(
                        "Startup window height. Also the minimum height when the minimum-size lock is on.",
                        "The lower bound is hard-coded (856x482).")
                .translation("config.autowindowsize.window.height")
                .defineInRange("height", 720, HARD_MIN_HEIGHT, MAX_HEIGHT);

        REMEMBER_POSITION = builder
                .comment(
                        "=== Position Memory ===",
                        "When enabled, the window position and size are saved on exit and restored on next launch",
                        "(instead of forcing the window to center). Compatible with auto-fullscreen / auto-maximize:",
                        "the game launches fullscreen / maximized first, then restores the remembered position on exit.")
                .translation("config.autowindowsize.window.rememberPosition")
                .define("rememberPosition", false);

        builder.pop();

        builder.push("startup");

        APPLY_STARTUP_GUIDE = builder
                .comment(
                        "=== One-time Onboarding (Modpack Authors) ===",
                        "When true, this launch follows startFullscreen / startMaximized to decide whether to auto-fullscreen",
                        "or auto-maximize, then syncs the in-game preference and resets itself to false (one-time only).",
                        "Skipped if the player already has an in-game preference.")
                .translation("config.autowindowsize.startup.applyStartupGuide")
                .define("applyStartupGuide", false);

        START_FULLSCREEN = builder
                .comment(
                        "Only used when applyStartupGuide is true: whether the first launch starts in fullscreen.",
                        "Mutually exclusive with startMaximized (if both are true, neither takes effect).")
                .translation("config.autowindowsize.startup.startFullscreen")
                .define("startFullscreen", false);

        START_MAXIMIZED = builder
                .comment(
                        "Only used when applyStartupGuide is true: whether the first launch starts maximized.",
                        "Mutually exclusive with startFullscreen (if both are true, neither takes effect).")
                .translation("config.autowindowsize.startup.startMaximized")
                .define("startMaximized", false);

        AUTO_FULLSCREEN = builder
                .comment(
                        "=== Player Preference ===",
                        "Whether to auto-fullscreen on the next launch (set in the in-game Window Settings screen).",
                        "Stays enabled until manually turned off. Mutually exclusive with autoMaximized;",
                        "if both are set true by hand, the mod resets both to false on launch.")
                .translation("config.autowindowsize.startup.autoFullscreen")
                .define("autoFullscreen", false);

        AUTO_MAXIMIZED = builder
                .comment(
                        "Whether to auto-maximize on the next launch (set in the in-game Window Settings screen).",
                        "Stays enabled until manually turned off. Mutually exclusive with autoFullscreen;",
                        "if both are set true by hand, the mod resets both to false on launch.")
                .translation("config.autowindowsize.startup.autoMaximized")
                .define("autoMaximized", false);

        STARTUP_DELAY = builder
                .comment(
                        "=== Startup Delay ===",
                        "Seconds to wait before applying the window size and position on launch.",
                        "A small delay avoids stretching the loading screen and gives lower-end PCs some buffer.")
                .translation("config.autowindowsize.startup.startupDelay")
                .defineInRange("startupDelay", 1.5, 0.5, 10.0);

        builder.pop();

        SPEC = builder.build();
    }
}
