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
                .comment("Startup window width, also the minimum width when locked")
                .defineInRange("width", 1280, 1, 7680);
        WINDOW_HEIGHT = builder
                .comment("Startup window height, also the minimum height when locked")
                .defineInRange("height", 720, 1, 4320);
        builder.pop();
        SPEC = builder.build();
    }
}
