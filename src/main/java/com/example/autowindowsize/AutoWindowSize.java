package com.example.autowindowsize;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWVidMode;

@Mod("autowindowsize")
public class AutoWindowSize {

    // 写死的最小分辨率（16:9），配置文件不能低于这个
    private static final int HARD_MIN_WIDTH = 1024;
    private static final int HARD_MIN_HEIGHT = 576;

    // 锁定最小窗口的开关（默认开启）
    private static boolean lockEnabled = true;

    // 按键绑定：默认 O 键切换锁定
    public static final KeyMapping TOGGLE_LOCK_KEY = new KeyMapping(
            "key.autowindowsize.toggle_lock",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_O,
            "key.categories.autowindowsize"
    );

    public AutoWindowSize() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, Config.SPEC);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onClientSetup);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onRegisterKeyMappings);
        MinecraftForge.EVENT_BUS.register(new WindowHandler());
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(this::initWindow);
    }

    private void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE_LOCK_KEY);
    }

    private void initWindow() {
        Minecraft mc = Minecraft.getInstance();
        long hwnd = mc.getWindow().getWindow();

        // 获取当前窗口所在显示器的分辨率（主副屏判断）
        int[] monitorRes = getCurrentMonitorResolution(hwnd);
        int screenWidth = monitorRes[0];
        int screenHeight = monitorRes[1];

        // 系统分辨率低于写死的最小值，弹错误并退出
        if (screenWidth < HARD_MIN_WIDTH || screenHeight < HARD_MIN_HEIGHT) {
            GLFW.glfwSetWindowShouldClose(hwnd, true);
            return;
        }

        // 读取配置，钳位到 [写死最小值, 当前显示器分辨率]
        int targetWidth = clamp(Config.WINDOW_WIDTH.get(), HARD_MIN_WIDTH, screenWidth);
        int targetHeight = clamp(Config.WINDOW_HEIGHT.get(), HARD_MIN_HEIGHT, screenHeight);

        // 配置值等于当前显示器分辨率 → 自动全屏
        if (targetWidth >= screenWidth && targetHeight >= screenHeight) {
            if (!mc.getWindow().isFullscreen()) {
                mc.getWindow().toggleFullScreen();
            }
            return;
        }

        // 设置窗口大小并居中（基于当前所在显示器）
        GLFW.glfwSetWindowSize(hwnd, targetWidth, targetHeight);
        long monitor = getCurrentMonitor(hwnd);
        int[] monX = new int[1], monY = new int[1];
        GLFW.glfwGetMonitorPos(monitor, monX, monY);
        GLFWVidMode mode = GLFW.glfwGetVideoMode(monitor);
        int posX = monX[0] + (mode.width() - targetWidth) / 2;
        int posY = monY[0] + (mode.height() - targetHeight) / 2;
        GLFW.glfwSetWindowPos(hwnd, posX, posY);

        // 设置最小窗口尺寸（锁定功能开启时）
        if (lockEnabled) {
            GLFW.glfwSetWindowSizeLimits(hwnd, targetWidth, targetHeight,
                    GLFW.GLFW_DONT_CARE, GLFW.GLFW_DONT_CARE);
        } else {
            GLFW.glfwSetWindowSizeLimits(hwnd, 0, 0,
                    GLFW.GLFW_DONT_CARE, GLFW.GLFW_DONT_CARE);
        }
    }

    // 获取窗口当前所在的显示器（主副屏判断）
    private long getCurrentMonitor(long window) {
        int[] winX = new int[1], winY = new int[1];
        int[] winW = new int[1], winH = new int[1];
        GLFW.glfwGetWindowPos(window, winX, winY);
        GLFW.glfwGetWindowSize(window, winW, winH);
        int centerX = winX[0] + winW[0] / 2;
        int centerY = winY[0] + winH[0] / 2;

        PointerBuffer monitors = GLFW.glfwGetMonitors();
        long primary = GLFW.glfwGetPrimaryMonitor();
        for (int i = 0; i < monitors.limit(); i++) {
            long monitor = monitors.get(i);
            int[] monX = new int[1], monY = new int[1];
            GLFW.glfwGetMonitorPos(monitor, monX, monY);
            GLFWVidMode mode = GLFW.glfwGetVideoMode(monitor);
            if (centerX >= monX[0] && centerX < monX[0] + mode.width()
                    && centerY >= monY[0] && centerY < monY[0] + mode.height()) {
                return monitor;
            }
        }
        return primary; // 跨屏时用主显示器
    }

    // 获取当前显示器的分辨率
    private int[] getCurrentMonitorResolution(long window) {
        long monitor = getCurrentMonitor(window);
        GLFWVidMode mode = GLFW.glfwGetVideoMode(monitor);
        return new int[]{mode.width(), mode.height()};
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    public static class WindowHandler {
        private boolean wasFullscreen = false;

        @SubscribeEvent
        public void onTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;

            // 检测按键：切换锁定
            if (TOGGLE_LOCK_KEY.consumeClick()) {
                lockEnabled = !lockEnabled;
                long hwnd = mc.getWindow().getWindow();
                if (lockEnabled) {
                    int w = Config.WINDOW_WIDTH.get();
                    int h = Config.WINDOW_HEIGHT.get();
                    GLFW.glfwSetWindowSizeLimits(hwnd, w, h,
                            GLFW.GLFW_DONT_CARE, GLFW.GLFW_DONT_CARE);
                    mc.player.displayClientMessage(
                            Component.literal("§a[AutoWindowSize] 已锁定最小窗口"), true);
                } else {
                    GLFW.glfwSetWindowSizeLimits(hwnd, 0, 0,
                            GLFW.GLFW_DONT_CARE, GLFW.GLFW_DONT_CARE);
                    mc.player.displayClientMessage(
                            Component.literal("§c[AutoWindowSize] 已解锁窗口大小"), true);
                }
            }

            // 检测退出全屏 → 恢复配置窗口大小并居中
            boolean isFullscreen = mc.getWindow().isFullscreen();
            if (wasFullscreen && !isFullscreen) {
                long hwnd = mc.getWindow().getWindow();
                int targetWidth = Config.WINDOW_WIDTH.get();
                int targetHeight = Config.WINDOW_HEIGHT.get();
                GLFW.glfwSetWindowSize(hwnd, targetWidth, targetHeight);

                long monitor = getCurrentMonitorStatic(hwnd);
                int[] monX = new int[1], monY = new int[1];
                GLFW.glfwGetMonitorPos(monitor, monX, monY);
                GLFWVidMode mode = GLFW.glfwGetVideoMode(monitor);
                int posX = monX[0] + (mode.width() - targetWidth) / 2;
                int posY = monY[0] + (mode.height() - targetHeight) / 2;
                GLFW.glfwSetWindowPos(hwnd, posX, posY);

                if (lockEnabled) {
                    GLFW.glfwSetWindowSizeLimits(hwnd, targetWidth, targetHeight,
                            GLFW.GLFW_DONT_CARE, GLFW.GLFW_DONT_CARE);
                }
            }
            wasFullscreen = isFullscreen;
        }

        private long getCurrentMonitorStatic(long window) {
            int[] winX = new int[1], winY = new int[1];
            int[] winW = new int[1], winH = new int[1];
            GLFW.glfwGetWindowPos(window, winX, winY);
            GLFW.glfwGetWindowSize(window, winW, winH);
            int centerX = winX[0] + winW[0] / 2;
            int centerY = winY[0] + winH[0] / 2;
            PointerBuffer monitors = GLFW.glfwGetMonitors();
            long primary = GLFW.glfwGetPrimaryMonitor();
            for (int i = 0; i < monitors.limit(); i++) {
                long monitor = monitors.get(i);
                int[] monX = new int[1], monY = new int[1];
                GLFW.glfwGetMonitorPos(monitor, monX, monY);
                GLFWVidMode mode = GLFW.glfwGetVideoMode(monitor);
                if (centerX >= monX[0] && centerX < monX[0] + mode.width()
                        && centerY >= monY[0] && centerY < monY[0] + mode.height()) {
                    return monitor;
                }
            }
            return primary;
        }
    }
}