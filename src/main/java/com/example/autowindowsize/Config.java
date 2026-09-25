package com.example.autowindowsize;

import net.minecraftforge.common.ForgeConfigSpec;

public class Config {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.IntValue WINDOW_WIDTH;
    public static final ForgeConfigSpec.IntValue WINDOW_HEIGHT;
    // 记住窗口位置：开启后退出游戏时保存当前窗口位置与大小，下次启动恢复到该位置
    // （而不是强制居中）。与自动全屏/最大化兼容：启动时先全屏/最大化，退出后恢复记忆位置。
    public static final ForgeConfigSpec.BooleanValue REMEMBER_POSITION;
    // 窗口置顶模式：0=关闭，1=普通置顶（GLFW_FLOATING），2=强制置顶（失去焦点时自动重新聚焦）。默认0。
    public static final ForgeConfigSpec.IntValue ALWAYS_ON_TOP_MODE;
    // 调试模式：开启后在日志中输出预设禁用判断等详细信息，方便排查问题。默认 false。
    public static final ForgeConfigSpec.BooleanValue DEBUG;
    // 无边框模式：开启后窗口没有标题栏和边框。全屏时自动无边框（无需设置）。
    // 无边框窗口无法通过标题栏拖动，需配合记住位置功能或在设置中调整分辨率。默认 false。
    public static final ForgeConfigSpec.BooleanValue BORDERLESS;
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
    // 玩家在游戏内设置里的持久化偏好：下次启动是否自动开启无边框。
    public static final ForgeConfigSpec.BooleanValue AUTO_BORDERLESS;
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

        ALWAYS_ON_TOP_MODE = builder
                .comment(
                        "=== Always On Top Mode ===",
                        "0 = Off (no always on top)",
                        "1 = Normal (GLFW_FLOATING: stays above non-floating windows; multiple floating windows follow Z-order)",
                        "2 = Force (always stays on top: when focus is lost, automatically re-focuses the window)",
                        "Cycle in the in-game Window Settings screen or with /aws top. Default 0.")
                .translation("config.autowindowsize.window.alwaysOnTopMode")
                .defineInRange("alwaysOnTopMode", 0, 0, 2);

        DEBUG = builder
                .comment(
                        "=== Debug Mode ===",
                        "When enabled, outputs detailed preset availability checks and other diagnostic info to the log.",
                        "Useful for troubleshooting. Toggle in-game or with /aws debug. Default false.")
                .translation("config.autowindowsize.window.debug")
                .define("debug", false);

        BORDERLESS = builder
                .comment(
                        "=== Borderless Window ===",
                        "When enabled, the window has no title bar or border (GLFW_DECORATED = false).",
                        "Fullscreen windows are automatically borderless, so this only affects windowed mode.",
                        "Borderless windows cannot be dragged by the title bar; use remember position",
                        "or adjust resolution in the settings screen. Toggle with /aws borderless. Default false.")
                .translation("config.autowindowsize.window.borderless")
                .define("borderless", false);

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

        AUTO_BORDERLESS = builder
                .comment(
                        "Whether to auto-enable borderless window on the next launch (set in the in-game Window Settings screen).",
                        "Stays enabled until manually turned off. Works independently of autoFullscreen / autoMaximized;",
                        "if autoFullscreen is also enabled, the game enters true fullscreen (which is inherently borderless).")
                .translation("config.autowindowsize.startup.autoBorderless")
                .define("autoBorderless", false);

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
