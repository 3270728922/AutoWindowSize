package com.example.autowindowsize;

import net.minecraftforge.common.ForgeConfigSpec;

public class Config {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.IntValue WINDOW_WIDTH;
    public static final ForgeConfigSpec.IntValue WINDOW_HEIGHT;

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

        // 说明：Range / Default 由 ForgeConfigSpec 自动追加到值上方，
        // 这里只写标题与释义，避免重复。两个配置项之间不加空注释行，
        // 以免在 GUI 配置界面里多出难看的空行。
        WINDOW_WIDTH = builder
                .comment(
                        "==== Window Size ====",
                        "Startup window width. This is also the minimum width the window",
                        "is allowed to shrink to when the size lock is enabled.",
                        "启动时的窗口宽度。开启尺寸锁定后，窗口也不能缩小到该值以下。")
                .defineInRange("width", 1280, HARD_MIN_WIDTH, MAX_WIDTH);

        WINDOW_HEIGHT = builder
                .comment(
                        "==== Window Height ====",
                        "Startup window height. This is also the minimum height the window",
                        "is allowed to shrink to when the size lock is enabled.",
                        "启动时的窗口高度。开启尺寸锁定后，窗口也不能缩小到该值以下。")
                .defineInRange("height", 720, HARD_MIN_HEIGHT, MAX_HEIGHT);

        builder.pop();

        SPEC = builder.build();
    }
}
