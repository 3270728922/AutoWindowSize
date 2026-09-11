package com.example.autowindowsize;

import net.minecraftforge.common.ForgeConfigSpec;

public class Config {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.IntValue WINDOW_WIDTH;
    public static final ForgeConfigSpec.IntValue WINDOW_HEIGHT;

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

        builder.pop();

        SPEC = builder.build();
    }
}
