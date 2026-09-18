package com.example.autowindowsize;

import net.minecraftforge.common.ForgeConfigSpec;

public class Config {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.IntValue WINDOW_WIDTH;
    public static final ForgeConfigSpec.IntValue WINDOW_HEIGHT;
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

    // 代码硬编码的最小分辨率（16:9）。配置文件的取值下限与运行时锁定下限都以这里为准，
    // 避免出现"配置能填低于硬写死分辨率的值"的脱节 bug。
    public static final int HARD_MIN_WIDTH = 856;
    public static final int HARD_MIN_HEIGHT = 482;

    // 允许的最大分辨率（超宽 / 8K 上限，仅作取值保护）
    private static final int MAX_WIDTH = 7680;
    private static final int MAX_HEIGHT = 4320;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.push("window");

        WINDOW_WIDTH = builder
                .comment(
                        "",
                        "Startup window width. Also the minimum width allowed when the size lock is on.",
                        "启动时的窗口宽度。开启最小尺寸锁定后，窗口不能缩小到该值以下。")
                .defineInRange("width", 1280, HARD_MIN_WIDTH, MAX_WIDTH);

        WINDOW_HEIGHT = builder
                .comment(
                        "",
                        "Startup window height. Also the minimum height allowed when the size lock is on.",
                        "启动时的窗口高度。开启最小尺寸锁定后，窗口不能缩小到该值以下。")
                .defineInRange("height", 720, HARD_MIN_HEIGHT, MAX_HEIGHT);

        builder.pop();

        builder.push("startup");

        APPLY_STARTUP_GUIDE = builder
                .comment(
                        "",
                        "-- Onboarding (modpack authors) --",
                        "One-time onboarding. When true, this launch follows startFullscreen / startMaximized,",
                        "syncs the in-game preference, then resets to false. If the player already has a",
                        "preference, the guide is skipped and all three one-time flags reset to false.",
                        "[整合包作者] 一次性首次引导。为 true 时本次启动按 startFullscreen / startMaximized",
                        "决定是否全屏或最大化，同步游戏内偏好后自动写回 false。玩家已有偏好时跳过本引导，",
                        "并把 applyStartupGuide / startFullscreen / startMaximized 全部写回 false。")
                .define("applyStartupGuide", false);

        START_FULLSCREEN = builder
                .comment(
                        "",
                        "Used only when applyStartupGuide is true: whether the first launch starts in fullscreen.",
                        "仅当 applyStartupGuide 为 true 且玩家无偏好时生效：本次首次启动是否自动全屏。与 startMaximized 互斥。")
                .define("startFullscreen", false);

        START_MAXIMIZED = builder
                .comment(
                        "",
                        "Used only when applyStartupGuide is true: whether the first launch starts maximized.",
                        "仅当 applyStartupGuide 为 true 且玩家无偏好时生效：本次首次启动是否自动最大化。与 startFullscreen 互斥。")
                .define("startMaximized", false);

        AUTO_FULLSCREEN = builder
                .comment(
                        "",
                        "-- Player preference --",
                        "Start in fullscreen on the NEXT launch (set in the in-game UI). Mutually exclusive with autoMaximized;",
                        "if both are set true by hand, the mod resets both to false on launch.",
                        "玩家偏好：下次启动是否自动全屏，在游戏内窗口设置里修改。与 autoMaximized 互斥；",
                        "若被手动改成两者都 true，模组启动时会自动都归零。")
                .define("autoFullscreen", false);

        AUTO_MAXIMIZED = builder
                .comment(
                        "",
                        "Start maximized on the NEXT launch (set in the in-game UI).",
                        "玩家偏好：下次启动是否自动最大化，在游戏内窗口设置里修改。与 autoFullscreen 互斥。")
                .define("autoMaximized", false);

        builder.pop();

        SPEC = builder.build();
    }
}
