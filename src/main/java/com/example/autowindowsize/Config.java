package com.example.autowindowsize;

import net.minecraftforge.common.ForgeConfigSpec;

public class Config {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.IntValue WINDOW_WIDTH;
    public static final ForgeConfigSpec.IntValue WINDOW_HEIGHT;
    public static final ForgeConfigSpec.BooleanValue FORCE_MIN_ON_LOAD;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.push("window");

        WINDOW_WIDTH = builder
                .comment("Startup window width, also the minimum width when locked",
                        "启动时窗口宽度，也是锁定时的最小宽度",
                        "Range: 1 ~ 7680 / 范围：1 ~ 7680",
                        "Default: 1280 / 默认：1280")
                .defineInRange("width", 1280, 1, 7680);

        WINDOW_HEIGHT = builder
                .comment("Startup window height, also the minimum height when locked",
                        "启动时窗口高度，也是锁定时的最小高度",
                        "Range: 1 ~ 4320 / 范围：1 ~ 4320",
                        "Default: 720 / 默认：720")
                .defineInRange("height", 720, 1, 4320);

        FORCE_MIN_ON_LOAD = builder
                .comment("If true, when loading finishes and window is smaller than configured size, force resize to configured size",
                        "如果开启，加载完成后如果窗口任意一边小于配置大小，强制改为配置值",
                        "If false, keep whatever size the user set during loading",
                        "如果关闭，保持用户在加载界面调整后的大小",
                        "Note: fullscreen and maximized windows are never modified",
                        "注意：全屏和最大化窗口不会被修改",
                        "Default: false / 默认：false")
                .define("forceMinOnLoad", false);

        builder.pop();

        SPEC = builder.build();
    }
}
