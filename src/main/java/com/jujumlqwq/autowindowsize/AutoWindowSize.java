package com.jujumlqwq.autowindowsize;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.AccessibilityOptionsScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.event.GameShuttingDownEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.Commands;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWVidMode;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.LinkedHashMap;
import java.util.Map;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

@Mod("autowindowsize")
public class AutoWindowSize {

    private static final org.apache.logging.log4j.Logger LOGGER =
            org.apache.logging.log4j.LogManager.getLogger("AutoWindowSize");

    // 写死的最小分辨率（16:9），统一定义在 Config 中；配置文件的取值下限也指向同一常量，
    // 这样配置层和运行时层不会脱节，杜绝"配置能填到比硬写死分辨率更小"的 bug。
    private static final int HARD_MIN_WIDTH = Config.HARD_MIN_WIDTH;
    private static final int HARD_MIN_HEIGHT = Config.HARD_MIN_HEIGHT;

    // 配置迁移标记：启动时若检测到旧版本配置，自动迁移并在进入世界时提示玩家
    private static boolean configMigrated = false;
    private static boolean configFileExistedBefore = false;
    private static boolean configMigrationNoticeShown = false;

    // 锁定最小窗口的开关（默认开启，运行时状态，不写入配置文件）
    private static boolean lockEnabled = true;
    // 固定分辨率开关（默认关闭）：开启后把"当前窗口大小"完全锁死（min=max=当前值），
    // 不跳变到配置分辨率、也不强制居中，只是不能再改大小
    private static boolean fixedEnabled = false;
    // 开启固定分辨率时记录的窗口尺寸（用于 min=max 锁定）
    private static int fixedWidth = 0;
    private static int fixedHeight = 0;
    // 锁定功能是否被永久禁用（当配置分辨率 >= 系统分辨率时为 true）
    private static boolean lockDisabled = false;
    // 全屏时临时禁用锁定
    private static boolean fullscreenTempDisabled = false;
    // 记录进入全屏前的锁定状态
    private static boolean lockBeforeFullscreen = true;
    // 记录进入全屏前的固定分辨率状态
    private static boolean fixedBeforeFullscreen = false;
    // 加载期间用户已全屏/最大化，导致初始化被跳过：等他退出该状态后补设配置大小并居中
    private static boolean deferredInitPending = false;
    // 强制置顶模式的焦点回调是否已注册（避免重复注册）
    private static boolean forceTopFocusCallbackRegistered = false;

    /** 最后一次正常（非全屏非最大化非最小化）窗口状态 {x,y,w,h}，退出游戏保存时用 */
    private static int[] lastNormalState = null;
    // 本次是靠"启动自动全屏"直接进的全屏：退出全屏后需要补一次配置尺寸+居中
    // （因为自动全屏分支跳过了 applyConfigWindow，窗口从未被居中过）。
    private static boolean pendingCenterAfterAutoFullscreen = false;
    // 进入全屏前窗口是否处于最大化（用于退出全屏后决定是恢复最大化还是补设配置值）
    private static boolean wasMaximizedAtFullscreen = false;
    // 加载期间"最大化→全屏→退出全屏恢复最大化"后，等用户再取消最大化时补设配置大小并居中
    private static boolean pendingCenterAfterUnmaximize = false;
    // 当前是否处于"无边框伪全屏"状态（窗口化模式铺满屏幕，非真正独占全屏）
    private static boolean borderlessFullscreenActive = false;
    // 循环切换窗口状态时，从全屏退出后需要强制还原为窗口化（避免恢复到最大化）
    private static boolean pendingRestoreToWindowed = false;

    // 按键绑定：默认未设置，玩家自行在控制设置中绑定
    public static final KeyMapping OPEN_SETTINGS_KEY = new KeyMapping(
            "key.autowindowsize.open_settings",
            InputConstants.Type.KEYSYM,
            -1,
            "key.categories.autowindowsize"
    );
    public static final KeyMapping CENTER_KEY = new KeyMapping(
            "key.autowindowsize.center",
            InputConstants.Type.KEYSYM,
            -1,
            "key.categories.autowindowsize"
    );
    public static final KeyMapping BORDERLESS_KEY = new KeyMapping(
            "key.autowindowsize.borderless",
            InputConstants.Type.KEYSYM,
            -1,
            "key.categories.autowindowsize"
    );
    public static final KeyMapping CYCLE_STATE_KEY = new KeyMapping(
            "key.autowindowsize.cycle_state",
            InputConstants.Type.KEYSYM,
            -1,
            "key.categories.autowindowsize"
    );
    public static final KeyMapping TOP_KEY = new KeyMapping(
            "key.autowindowsize.top",
            InputConstants.Type.KEYSYM,
            -1,
            "key.categories.autowindowsize"
    );

    public AutoWindowSize() {
        // 在Forge创建配置文件之前，检查配置文件是否已存在（区分全新安装 vs 旧版本升级）
        java.io.File cfgFile = new java.io.File("config/AutoWindowSize/config.toml");
        configFileExistedBefore = cfgFile.exists();
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, Config.SPEC, "AutoWindowSize/config.toml");
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onClientSetup);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onRegisterKeyMappings);
        MinecraftForge.EVENT_BUS.register(new WindowHandler());
        MinecraftForge.EVENT_BUS.register(new ScreenEventHandler());
        MinecraftForge.EVENT_BUS.register(new CommandHandler());
        MinecraftForge.EVENT_BUS.register(new ShutdownHandler());
    }

    /** 游戏关闭时保存窗口位置（仅当记住位置功能开启时） */
    public static class ShutdownHandler {
        @SubscribeEvent
        public void onGameShutdown(GameShuttingDownEvent event) {
            if (Config.REMEMBER_POSITION.get()) {
                saveWindowState();
            }
        }
    }

    /**
     * 配置自动迁移：检测旧版本配置，自动把缺失字段写回配置文件。
     * 原理：Forge 加载配置时，缺失字段会用默认值但不写回文件；
     * 只要修改任意一个字段（这里改 configVersion），Forge 就会在
     * 游戏关闭时把整份配置（含所有新增字段）写回文件。
     */
    private static void migrateConfig() {
        try {
            int version = Config.CONFIG_VERSION.get();

            // 全新安装：配置文件是本次新创建的，直接把版本号设为最新，不弹迁移提示
            if (!configFileExistedBefore) {
                if (version < Config.CURRENT_CONFIG_VERSION) {
                    Config.CONFIG_VERSION.set(Config.CURRENT_CONFIG_VERSION);
                }
                return;
            }

            // 旧版本升级：配置文件在本次运行前就存在，按版本号判断是否需要迁移
            if (version >= Config.CURRENT_CONFIG_VERSION) {
                return; // 已经是最新版本，无需迁移
            }

            LOGGER.info("[AutoWindowSize] Config migration: detected v{}, migrating to v{}...",
                    version, Config.CURRENT_CONFIG_VERSION);

            // 检测 1.0.9 之前的旧配置文件
            File oldConfig = new File("config/autowindowsize-client.toml");
            if (oldConfig.exists()) {
                LOGGER.warn("[AutoWindowSize] Found legacy config file: config/autowindowsize-client.toml. " +
                        "The mod now uses config/AutoWindowSize/config.toml. " +
                        "You may delete the old file after verifying your settings.");
            }

            // 修改版本号 → 触发 Forge 的 dirty 标记 → 关闭时整份保存（含所有新增字段）
            Config.CONFIG_VERSION.set(Config.CURRENT_CONFIG_VERSION);
            configMigrated = true;

            LOGGER.info("[AutoWindowSize] Config migration complete. Missing fields will be written to " +
                    "config/AutoWindowSize/config.toml on game shutdown. No manual config deletion needed.");
        } catch (Exception e) {
            LOGGER.error("[AutoWindowSize] Config migration failed: {}", e.getMessage());
        }
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        // 配置自动迁移（在窗口初始化之前执行，确保迁移后的配置立即生效）
        migrateConfig();
        // 延迟初始化：等游戏窗口完全创建后再设置大小
        // 不检测特定界面，避免第一次启动引导界面时漏掉
        MinecraftForge.EVENT_BUS.register(new DelayedInitHandler());
    }

    private void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_SETTINGS_KEY);
        event.register(CENTER_KEY);
        event.register(BORDERLESS_KEY);
        event.register(CYCLE_STATE_KEY);
        event.register(TOP_KEY);
    }

    private void initWindow() {
        Minecraft mc = Minecraft.getInstance();
        long hwnd = mc.getWindow().getWindow();

        int[] monitorRes = getCurrentMonitorResolution(hwnd);
        int screenWidth = monitorRes[0];
        int screenHeight = monitorRes[1];
        int configWidth = Config.WINDOW_WIDTH.get();
        int configHeight = Config.WINDOW_HEIGHT.get();

        if (configWidth > screenWidth || configHeight > screenHeight
                || HARD_MIN_WIDTH > screenWidth || HARD_MIN_HEIGHT > screenHeight) {
            lockDisabled = true;
            lockEnabled = false;
            return;
        }

        lockDisabled = false;
        int targetWidth = configWidth;
        int targetHeight = configHeight;

        // 先应用无边框，再设置大小和位置，确保居中计算基于正确的窗口状态
        applyBorderless();

        GLFW.glfwSetWindowSize(hwnd, targetWidth, targetHeight);
        long monitor = getCurrentMonitor(hwnd);
        int[] monX = new int[1], monY = new int[1];
        GLFW.glfwGetMonitorPos(monitor, monX, monY);
        GLFWVidMode mode = GLFW.glfwGetVideoMode(monitor);
        int posX = monX[0] + (mode.width() - targetWidth) / 2;
        int posY = monY[0] + (mode.height() - targetHeight) / 2;
        GLFW.glfwSetWindowPos(hwnd, posX, posY);

        applyWindowLimits();
        applyAlwaysOnTop();

        // 强制置顶模式：注册焦点变化回调，失去焦点时立即重新聚焦（快速响应）
        if (!forceTopFocusCallbackRegistered) {
            GLFW.glfwSetWindowFocusCallback(hwnd, (window, focused) -> {
                if (!focused && Config.ALWAYS_ON_TOP_MODE.get() == 2) {
                    GLFW.glfwFocusWindow(window);
                }
            });
            forceTopFocusCallbackRegistered = true;
        }
    }

    /** 应用窗口置顶设置：根据模式设置 GLFW_FLOATING。0=关闭，1=普通，2=强制。 */
    public static void applyAlwaysOnTop() {
        long hwnd = Minecraft.getInstance().getWindow().getWindow();
        int mode = Config.ALWAYS_ON_TOP_MODE.get();
        boolean floating = (mode == 1 || mode == 2);
        GLFW.glfwSetWindowAttrib(hwnd, GLFW.GLFW_FLOATING,
                floating ? GLFW.GLFW_TRUE : GLFW.GLFW_FALSE);
    }

    /** 切换窗口置顶模式：0→1→2→0 循环。返回新模式。 */
    public static int toggleAlwaysOnTop() {
        int newMode = (Config.ALWAYS_ON_TOP_MODE.get() + 1) % 3;
        Config.ALWAYS_ON_TOP_MODE.set(newMode);
        saveConfig();
        applyAlwaysOnTop();
        return newMode;
    }

    /** 当前置顶模式：0=关闭，1=普通，2=强制。 */
    public static int getAlwaysOnTopMode() {
        return Config.ALWAYS_ON_TOP_MODE.get();
    }

    /** 切换调试模式：开→关→开。返回新状态。 */
    public static boolean toggleDebug() {
        boolean newState = !Config.DEBUG.get();
        Config.DEBUG.set(newState);
        saveConfig();
        return newState;
    }

    /** 当前调试模式是否开启。 */
    public static boolean isDebugEnabled() {
        return Config.DEBUG.get();
    }

    /** 应用无边框设置：根据配置设置 GLFW_DECORATED 属性。全屏时 GLFW 自动无边框。 */
    public static void applyBorderless() {
        long hwnd = getWindowHandle();
        if (hwnd == 0) return;
        boolean fullscreen = GLFW.glfwGetWindowMonitor(hwnd) != 0;
        // 全屏时 GLFW 自动无边框，不设置 GLFW_DECORATED（避免干扰全屏逻辑）
        if (!fullscreen) {
            boolean borderless = Config.BORDERLESS.get();
            GLFW.glfwSetWindowAttrib(hwnd, GLFW.GLFW_DECORATED, borderless ? GLFW.GLFW_FALSE : GLFW.GLFW_TRUE);
        }
    }

    /** 切换无边框模式：开→关→开。
     *  全屏状态下：真正全屏 ↔ 无边框伪全屏（窗口化铺满屏幕，失去焦点不最小化）。
     *  最大化状态下：还原→切换→重新最大化（用户确认目前无bug，保持现状）。
     *  窗口化状态下：保持窗口总大小（外框尺寸）不变，边框空间并入/划出客户区，窗口位置不动。 */
    public static boolean toggleBorderless() {
        long hwnd = getWindowHandle();
        boolean newState = !Config.BORDERLESS.get();
        Config.BORDERLESS.set(newState);
        saveConfig();

        boolean isTrueFullscreen = GLFW.glfwGetWindowMonitor(hwnd) != 0;
        boolean maximized = GLFW.glfwGetWindowAttrib(hwnd, GLFW.GLFW_MAXIMIZED) == GLFW.GLFW_TRUE;
        int[] dbgW = new int[1], dbgH = new int[1];
        GLFW.glfwGetWindowSize(hwnd, dbgW, dbgH);
        if (Config.DEBUG.get()) {
            LOGGER.info("[AWS BORDERLESS] toggle called: newState={} isTrueFullscreen={} maximized={} pseudoActive={} size={}x{}",
                    newState, isTrueFullscreen, maximized, borderlessFullscreenActive, dbgW[0], dbgH[0]);
        }

        // ===== 真正全屏状态：优先拦截，避免误走最大化分支导致窗口变成"窗口化但全屏大小" =====
        if (isTrueFullscreen) {
            if (newState) {
                // 真正全屏 → 无边框伪全屏：退出独占全屏，用窗口化模式铺满屏幕
                long monitor = getCurrentMonitorStatic(hwnd);
                int[] monX = new int[1], monY = new int[1];
                GLFW.glfwGetMonitorPos(monitor, monX, monY);
                GLFWVidMode mode = GLFW.glfwGetVideoMode(monitor);
                borderlessFullscreenActive = true;
                GLFW.glfwSetWindowAttrib(hwnd, GLFW.GLFW_DECORATED, GLFW.GLFW_FALSE);
                GLFW.glfwSetWindowAttrib(hwnd, GLFW.GLFW_AUTO_ICONIFY, GLFW.GLFW_FALSE);
                GLFW.glfwSetWindowMonitor(hwnd, 0, monX[0], monY[0], mode.width(), mode.height(), GLFW.GLFW_DONT_CARE);
                if (Config.DEBUG.get()) LOGGER.info("[AWS BORDERLESS] true fullscreen -> pseudo fullscreen at {}x{}", mode.width(), mode.height());
            } else {
                // 真正全屏下关闭无边框：全屏时 GLFW 自动无边框，只需保存配置，不改变窗口状态
                borderlessFullscreenActive = false;
                if (Config.DEBUG.get()) LOGGER.info("[AWS BORDERLESS] true fullscreen, closing borderless: no window change needed");
            }
            return newState;
        }
        // 伪全屏（窗口化铺满屏幕）下关闭无边框 → 恢复真正全屏
        if (borderlessFullscreenActive && !newState) {
            long monitor = getCurrentMonitorStatic(hwnd);
            GLFWVidMode mode = GLFW.glfwGetVideoMode(monitor);
            borderlessFullscreenActive = false;
            GLFW.glfwSetWindowAttrib(hwnd, GLFW.GLFW_DECORATED, GLFW.GLFW_TRUE);
            GLFW.glfwSetWindowAttrib(hwnd, GLFW.GLFW_AUTO_ICONIFY, GLFW.GLFW_TRUE);
            GLFW.glfwSetWindowMonitor(hwnd, monitor, 0, 0, mode.width(), mode.height(), mode.refreshRate());
            if (Config.DEBUG.get()) LOGGER.info("[AWS BORDERLESS] pseudo fullscreen -> true fullscreen");
            return newState;
        }

        // ===== 最大化状态 =====
        if (maximized) {
            GLFW.glfwRestoreWindow(hwnd);
            GLFW.glfwSetWindowAttrib(hwnd, GLFW.GLFW_DECORATED, newState ? GLFW.GLFW_FALSE : GLFW.GLFW_TRUE);
            GLFW.glfwMaximizeWindow(hwnd);
            if (Config.DEBUG.get()) LOGGER.info("[AWS BORDERLESS] maximized branch: restore->toggle->remaximize, newState={}", newState);
            return newState;
        }

        // ===== 普通窗口化：暂时只切换边框属性（简单实现，位置/大小补偿后续再研究） =====
        GLFW.glfwSetWindowAttrib(hwnd, GLFW.GLFW_DECORATED, newState ? GLFW.GLFW_FALSE : GLFW.GLFW_TRUE);
        return newState;
    }

    /** 当前无边框模式是否开启。 */
    public static boolean isBorderlessEnabled() {
        return Config.BORDERLESS.get();
    }

    /** 当前是否处于无边框伪全屏状态（窗口化模式铺满屏幕，非真正独占全屏）。 */
    public static boolean isBorderlessFullscreenActive() {
        return borderlessFullscreenActive;
    }

    /** 统一的"全屏类状态"检测：真正独占全屏 + 无边框伪全屏都算。
     *  用于按钮禁用、状态提示等需要把伪全屏也当作全屏处理的场景。 */
    public static boolean isFullscreenLike() {
        long hwnd = getWindowHandle();
        if (hwnd == 0) return false;
        return GLFW.glfwGetWindowMonitor(hwnd) != 0 || borderlessFullscreenActive;
    }

    // ========== 供外部调用的静态方法 ==========

    public static boolean isLockEnabled() {
        return lockEnabled;
    }

    public static boolean isLockDisabled() {
        return lockDisabled;
    }

    public static boolean isFullscreenTempDisabled() {
        return fullscreenTempDisabled;
    }

    public static boolean canLock() {
        return !lockDisabled && !fullscreenTempDisabled;
    }

    /** 把当前窗口在其所在显示器上居中。仅在窗口化、未最大化、未最小化时有意义。 */
    public static void centerWindow() {
        long hwnd = getWindowHandle();
        long monitor = getCurrentMonitorStatic(hwnd);
        int[] monX = new int[1], monY = new int[1];
        GLFW.glfwGetMonitorPos(monitor, monX, monY);
        GLFWVidMode mode = GLFW.glfwGetVideoMode(monitor);
        int[] winW = new int[1], winH = new int[1];
        GLFW.glfwGetWindowSize(hwnd, winW, winH);
        int posX = monX[0] + (mode.width() - winW[0]) / 2;
        int posY = monY[0] + (mode.height() - winH[0]) / 2;
        GLFW.glfwSetWindowPos(hwnd, posX, posY);
    }

    /** 窗口是否已经在其所在显示器居中（允许 2px 误差，避免 DPI/边框取整导致的抖动）。 */
    public static boolean isWindowCentered() {
        long hwnd = getWindowHandle();
        long monitor = getCurrentMonitorStatic(hwnd);
        int[] monX = new int[1], monY = new int[1];
        GLFW.glfwGetMonitorPos(monitor, monX, monY);
        GLFWVidMode mode = GLFW.glfwGetVideoMode(monitor);
        int[] winX = new int[1], winY = new int[1], winW = new int[1], winH = new int[1];
        GLFW.glfwGetWindowPos(hwnd, winX, winY);
        GLFW.glfwGetWindowSize(hwnd, winW, winH);
        int targetX = monX[0] + (mode.width() - winW[0]) / 2;
        int targetY = monY[0] + (mode.height() - winH[0]) / 2;
        return Math.abs(winX[0] - targetX) <= 2 && Math.abs(winY[0] - targetY) <= 2;
    }

    /** 居中按钮不可用的原因；null 表示可用。按优先级返回全屏/最大化/最小化/已居中。 */
    public static Component getCenterDisabledReason() {
        long hwnd = getWindowHandle();
        if (isFullscreenLike()) {
            return Component.translatable("gui.autowindowsize.center.disabled_fullscreen");
        }
        if (GLFW.glfwGetWindowAttrib(hwnd, GLFW.GLFW_MAXIMIZED) == GLFW.GLFW_TRUE) {
            return Component.translatable("gui.autowindowsize.center.disabled_maximized");
        }
        if (GLFW.glfwGetWindowAttrib(hwnd, GLFW.GLFW_ICONIFIED) != GLFW.GLFW_FALSE) {
            return Component.translatable("gui.autowindowsize.center.disabled_minimized");
        }
        if (isWindowCentered()) {
            return Component.translatable("gui.autowindowsize.center.already_centered");
        }
        return null;
    }

    /** 居中按钮何时可用：非全屏、非最大化、非最小化、且尚未居中。 */
    public static boolean canCenter() {
        return getCenterDisabledReason() == null;
    }

    public static long getWindowHandle() {
        return Minecraft.getInstance().getWindow().getWindow();
    }

    /** 常用分辨率预设：按宽高比分组。比例按钮选组，分辨率按钮在组内选具体值。 */
    public static final String[] ASPECT_NAMES = {"16:9", "16:10", "4:3", "5:4", "21:9", "3:2", "2:1", "9:16", "自定义"};
    public static final int[][][] PRESETS = {
            // 16:9
            {{856, 482}, {960, 540}, {1024, 576}, {1152, 648}, {1280, 720}, {1366, 768}, {1440, 810}, {1600, 900}, {1760, 990}, {1920, 1080}, {2048, 1152}, {2240, 1260}, {2560, 1440}, {3200, 1800}, {3840, 2160}, {4096, 2304}},
            // 16:10
            {{1024, 640}, {1152, 720}, {1200, 750}, {1280, 800}, {1344, 840}, {1440, 900}, {1600, 1000}, {1680, 1050}, {1920, 1200}, {2048, 1280}, {2240, 1400}, {2560, 1600}, {2880, 1800}, {3200, 2000}, {3840, 2400}, {4096, 2560}},
            // 4:3
            {{640, 480}, {720, 540}, {800, 600}, {832, 624}, {960, 720}, {1024, 768}, {1152, 864}, {1280, 960}, {1344, 1008}, {1400, 1050}, {1440, 1080}, {1600, 1200}, {1680, 1260}, {1920, 1440}, {2048, 1536}, {2560, 1920}},
            // 5:4
            {{800, 640}, {900, 720}, {1000, 800}, {1100, 880}, {1200, 960}, {1280, 1024}, {1400, 1120}, {1440, 1152}, {1500, 1200}, {1600, 1280}, {1680, 1344}, {1800, 1440}, {1920, 1536}, {2000, 1600}, {2560, 2048}, {2880, 2304}},
            // 21:9（超宽屏）
            {{1920, 800}, {2048, 858}, {2280, 960}, {2400, 1000}, {2520, 1080}, {2560, 1080}, {2880, 1200}, {2940, 1260}, {3000, 1260}, {3200, 1350}, {3440, 1440}, {3780, 1620}, {3840, 1600}, {4320, 1800}, {4620, 1980}, {5120, 2160}},
            // 3:2（Surface 等笔记本常用）
            {{960, 640}, {1080, 720}, {1200, 800}, {1350, 900}, {1440, 960}, {1500, 1000}, {1620, 1080}, {1800, 1200}, {1920, 1280}, {2160, 1440}, {2400, 1600}, {2640, 1760}, {2880, 1920}, {3000, 2000}, {3240, 2160}, {3600, 2400}},
            // 2:1（平板/部分超宽屏）
            {{1024, 512}, {1152, 576}, {1200, 600}, {1280, 640}, {1440, 720}, {1536, 768}, {1600, 800}, {1680, 840}, {1920, 960}, {2048, 1024}, {2160, 1080}, {2400, 1200}, {2560, 1280}, {2880, 1440}, {3200, 1600}, {3840, 1920}},
            // 9:16（竖屏/手机直播/竖屏显示器）
            {{540, 960}, {630, 1120}, {720, 1280}, {810, 1440}, {900, 1600}, {990, 1760}, {1080, 1920}, {1170, 2080}, {1260, 2240}, {1350, 2400}, {1440, 2560}, {1620, 2880}, {1800, 3200}, {2160, 3840}, {2430, 4320}, {2700, 4800}},
    };

    /** 所有预设的扁平化列表（去重），用于指令补全时建议常用宽度/高度 */
    public static final int[][] ALL_PRESETS_FLAT = flattenPresets();

    private static int[][] flattenPresets() {
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        java.util.List<int[]> list = new java.util.ArrayList<>();
        for (int[][] group : PRESETS) {
            for (int[] p : group) {
                String key = p[0] + "x" + p[1];
                if (seen.add(key)) list.add(p);
            }
        }
        return list.toArray(new int[0][]);
    }

    /** 预设按钮是否可用：全屏或最大化时不可用。 */
    public static boolean canApplyPreset() {
        long hwnd = getWindowHandle();
        if (GLFW.glfwGetWindowMonitor(hwnd) != 0) return false;
        if (GLFW.glfwGetWindowAttrib(hwnd, GLFW.GLFW_MAXIMIZED) == GLFW.GLFW_TRUE) return false;
        return true;
    }

    /** 窗口当前是否处于最大化状态（独占全屏不算最大化）。 */
    public static boolean isMaximized() {
        long hwnd = getWindowHandle();
        if (GLFW.glfwGetWindowMonitor(hwnd) != 0) return false;
        return GLFW.glfwGetWindowAttrib(hwnd, GLFW.GLFW_MAXIMIZED) == GLFW.GLFW_TRUE;
    }

    /** 在所有分组里查找 (w,h)，返回 {组, 索引}；找不到返回 {-1,-1}。 */
    public static int[] findPreset(int w, int h) {
        for (int g = 0; g < PRESETS.length; g++) {
            for (int i = 0; i < PRESETS[g].length; i++) {
                if (PRESETS[g][i][0] == w && PRESETS[g][i][1] == h) return new int[]{g, i};
            }
        }
        return new int[]{-1, -1};
    }

    /** 应用指定分辨率预设：临时取消最小尺寸限制→设窗口大小并居中→恢复限制。不修改配置值。 */
    public static void applyResolutionPreset(int w, int h) {
        long hwnd = getWindowHandle();
        // 临时取消最小尺寸限制，否则锁定开着时 glfwSetWindowSize 会被拦截、窗口纹丝不动
        GLFW.glfwSetWindowSizeLimits(hwnd, 0, 0, GLFW.GLFW_DONT_CARE, GLFW.GLFW_DONT_CARE);

        GLFW.glfwSetWindowSize(hwnd, w, h);
        long monitor = getCurrentMonitorStatic(hwnd);
        int[] monX = new int[1], monY = new int[1];
        GLFW.glfwGetMonitorPos(monitor, monX, monY);
        GLFWVidMode mode = GLFW.glfwGetVideoMode(monitor);
        int posX = monX[0] + (mode.width() - w) / 2;
        int posY = monY[0] + (mode.height() - h) / 2;
        GLFW.glfwSetWindowPos(hwnd, posX, posY);

        // 分辨率超过当前屏幕：不阻止切换，只给一条提示。
        if (w > mode.width() || h > mode.height()) {
            Minecraft.getInstance().gui.getChat().addMessage(
                Component.translatable("message.autowindowsize.preset_oversize", w, h, mode.width(), mode.height()));
        }

        // 恢复原来的最小尺寸限制
        applyWindowLimits();
    }

    public static long getCurrentMonitorStatic(long window) {
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

    public static int[] getCurrentMonitorResolutionStatic(long window) {
        long monitor = getCurrentMonitorStatic(window);
        GLFWVidMode mode = GLFW.glfwGetVideoMode(monitor);
        return new int[]{mode.width(), mode.height()};
    }

    private static long getCurrentMonitor(long window) {
        return getCurrentMonitorStatic(window);
    }

    private static int[] getCurrentMonitorResolution(long window) {
        return getCurrentMonitorResolutionStatic(window);
    }

    public static boolean isFixedEnabled() {
        return fixedEnabled;
    }

    /** 玩家持久化偏好：下次启动是否自动全屏（不影响当前这次启动）。 */
    public static boolean isAutoFullscreenPref() {
        return Config.AUTO_FULLSCREEN.get();
    }

    /** 玩家持久化偏好：下次启动是否自动最大化（不影响当前这次启动）。 */
    public static boolean isAutoMaximizedPref() {
        return Config.AUTO_MAXIMIZED.get();
    }

    /**
     * "下次启动自动全屏"按钮是否可点。与自动最大化互斥：
     * 玩家偏好选了最大化时，全屏按钮禁用并提示。
     */
    public static boolean canAutoFullscreen() {
        return !Config.AUTO_MAXIMIZED.get();
    }

    /**
     * "下次启动自动最大化"按钮是否可点。与自动全屏互斥：
     * 玩家偏好选了全屏时，最大化按钮禁用并提示。
     */
    public static boolean canAutoMaximized() {
        return !Config.AUTO_FULLSCREEN.get();
    }

    /**
     * 切换"下次启动自动全屏"偏好。开启时自动关掉自动最大化（互斥单选）；
     * 关闭时不动另一个。只改持久化设置，不影响当前窗口。
     */
    public static boolean toggleAutoFullscreenPref() {
        boolean now = !Config.AUTO_FULLSCREEN.get();
        Config.AUTO_FULLSCREEN.set(now);
        if (now) {
            Config.AUTO_MAXIMIZED.set(false);
        }
        saveConfig();
        return now;
    }

    /**
     * 切换"下次启动自动最大化"偏好。开启时自动关掉自动全屏（互斥单选）；
     * 关闭时不动另一个。只改持久化设置，不影响当前窗口。
     */
    public static boolean toggleAutoMaximizedPref() {
        boolean now = !Config.AUTO_MAXIMIZED.get();
        Config.AUTO_MAXIMIZED.set(now);
        if (now) {
            Config.AUTO_FULLSCREEN.set(false);
        }
        saveConfig();
        return now;
    }

    /**
     * 切换"下次启动自动无边框"偏好。不与自动全屏/最大化互斥（全屏本身无边框）。
     * 只改持久化设置，不影响当前窗口。
     */
    public static boolean toggleAutoBorderlessPref() {
        boolean now = !Config.AUTO_BORDERLESS.get();
        Config.AUTO_BORDERLESS.set(now);
        saveConfig();
        return now;
    }

    /** "记住窗口位置"是否开启 */
    public static boolean isRememberPosition() {
        return Config.REMEMBER_POSITION.get();
    }

    /** 切换"记住窗口位置"开关。开启后下次启动恢复上次退出时的窗口位置与大小。 */
    public static boolean toggleRememberPosition() {
        boolean now = !Config.REMEMBER_POSITION.get();
        Config.REMEMBER_POSITION.set(now);
        saveConfig();
        return now;
    }

    /**
     * 循环切换窗口状态：窗口化 → 最大化 → 全屏 → 窗口化。
     * 无边框状态下也能正常切换（无边框最大化/全屏都支持）。
     * 伪全屏（无边框全屏）切换到窗口化时，先恢复真正全屏再退出，避免状态混乱。
     * @return 切换后的状态描述（0=窗口化, 1=最大化, 2=全屏）
     */
    public static int cycleWindowState() {
        long hwnd = getWindowHandle();
        boolean isTrueFullscreen = GLFW.glfwGetWindowMonitor(hwnd) != 0;
        boolean isPseudo = borderlessFullscreenActive;
        boolean maximized = GLFW.glfwGetWindowAttrib(hwnd, GLFW.GLFW_MAXIMIZED) == GLFW.GLFW_TRUE;

        if (isTrueFullscreen || isPseudo) {
            // ===== 全屏（含伪全屏）→ 窗口化 =====
            if (isPseudo) {
                // 伪全屏：保持无边框状态，直接把窗口大小改回配置值并居中，不恢复有边框
                borderlessFullscreenActive = false;
                GLFW.glfwSetWindowAttrib(hwnd, GLFW.GLFW_AUTO_ICONIFY, GLFW.GLFW_TRUE);
                // 用配置值设窗口大小并居中
                int targetW = Config.WINDOW_WIDTH.get();
                int targetH = Config.WINDOW_HEIGHT.get();
                long monitor = getCurrentMonitorStatic(hwnd);
                int[] monX = new int[1], monY = new int[1];
                GLFW.glfwGetMonitorPos(monitor, monX, monY);
                GLFWVidMode mode = GLFW.glfwGetVideoMode(monitor);
                int posX = monX[0] + (mode.width() - targetW) / 2;
                int posY = monY[0] + (mode.height() - targetH) / 2;
                GLFW.glfwSetWindowSize(hwnd, targetW, targetH);
                GLFW.glfwSetWindowPos(hwnd, posX, posY);
                if (Config.DEBUG.get()) LOGGER.info("[AWS CYCLE] pseudo -> windowed (borderless kept), size={}x{}", targetW, targetH);
                return 0; // 窗口化
            } else {
                // 真正全屏：退出全屏，但 Minecraft 会恢复到进入全屏前的状态（可能是最大化），
                // 所以设置标记，等窗口退出全屏后强制还原为窗口化
                pendingRestoreToWindowed = true;
                Minecraft.getInstance().getWindow().toggleFullScreen();
                if (Config.DEBUG.get()) LOGGER.info("[AWS CYCLE] true fullscreen -> windowed (pending restore)");
                return 0; // 窗口化
            }
        } else if (maximized) {
            // ===== 最大化 → 全屏 =====
            Minecraft.getInstance().getWindow().toggleFullScreen();
            if (Config.DEBUG.get()) LOGGER.info("[AWS CYCLE] maximized -> fullscreen");
            return 2; // 全屏
        } else {
            // ===== 窗口化 → 最大化 =====
            GLFW.glfwMaximizeWindow(hwnd);
            if (Config.DEBUG.get()) LOGGER.info("[AWS CYCLE] windowed -> maximized");
            return 1; // 最大化
        }
    }

    /**
     * 配置已通过 BooleanValue.set() 更新到内存；Forge 会在游戏正常退出时把
     * CLIENT 配置写回磁盘，这里无需手动保存（本版 registerConfig 返回 void）。
     */
    private static void saveConfig() {
        // no-op: rely on Forge's automatic config save on shutdown
    }

    /**
     * 固定分辨率是否可用：全屏时临时禁用；窗口最大化时禁用（最大化尺寸铺满屏幕，
     * 锁它没有意义，请先取消最大化）；分辨率过低不影响固定（它锁当前窗口大小，与配置值无关）。
     */
    public static boolean canFixed() {
        if (fullscreenTempDisabled) return false;
        long hwnd = Minecraft.getInstance().getWindow().getWindow();
        return GLFW.glfwGetWindowAttrib(hwnd, GLFW.GLFW_MAXIMIZED) != GLFW.GLFW_TRUE;
    }

    /**
     * 统一应用窗口尺寸限制：
     *  - 固定分辨率开启：min = max = 开启固定时的窗口尺寸，大小完全不能变；
     *    同时把 GLFW_RESIZABLE 置为 false —— 在 Windows 上这会一并移除标题栏的
     *    最大化按钮（WS_MAXIMIZEBOX）并禁止拖边，从根上避免"假最大化"。
     *  - 锁定最小开启：恢复 GLFW_RESIZABLE=true，min = 配置值，max 不限（只能放大不能缩小）
     *  - 都未开启：恢复 GLFW_RESIZABLE=true，无尺寸限制
     */
    private static void applyWindowLimits() {
        long hwnd = Minecraft.getInstance().getWindow().getWindow();
        if (fixedEnabled && fixedWidth > 0 && fixedHeight > 0) {
            GLFW.glfwSetWindowSizeLimits(hwnd, fixedWidth, fixedHeight,
                    fixedWidth, fixedHeight);
            GLFW.glfwSetWindowAttrib(hwnd, GLFW.GLFW_RESIZABLE, GLFW.GLFW_FALSE);
        } else if (lockEnabled) {
            GLFW.glfwSetWindowAttrib(hwnd, GLFW.GLFW_RESIZABLE, GLFW.GLFW_TRUE);
            int w = Config.WINDOW_WIDTH.get();
            int h = Config.WINDOW_HEIGHT.get();
            GLFW.glfwSetWindowSizeLimits(hwnd, w, h,
                    GLFW.GLFW_DONT_CARE, GLFW.GLFW_DONT_CARE);
        } else {
            GLFW.glfwSetWindowAttrib(hwnd, GLFW.GLFW_RESIZABLE, GLFW.GLFW_TRUE);
            GLFW.glfwSetWindowSizeLimits(hwnd, 0, 0,
                    GLFW.GLFW_DONT_CARE, GLFW.GLFW_DONT_CARE);
        }
    }

    /**
     * 切换固定分辨率。开启时只把"当前窗口尺寸"锁死（min=max=当前值），
     * 不跳变到配置分辨率、也不强制居中；关闭时按现有锁定状态恢复尺寸限制。
     * 最大化状态由 canFixed() 拦截，不会走到这里。
     */
    public static boolean toggleFixed() {
        if (!canFixed()) return false;
        fixedEnabled = !fixedEnabled;
        if (fixedEnabled) {
            lockToCurrentSize(Minecraft.getInstance().getWindow().getWindow());
        } else {
            applyWindowLimits();
        }
        return fixedEnabled;
    }

    /** 读取当前窗口尺寸并以 min=max 锁死（固定分辨率），同时禁用调整大小与最大化按钮。 */
    private static void lockToCurrentSize(long hwnd) {
        int[] w = new int[1], h = new int[1];
        GLFW.glfwGetWindowSize(hwnd, w, h);
        fixedWidth = w[0];
        fixedHeight = h[0];
        applyWindowLimits();
    }

    public static boolean toggleLock() {
        if (!canLock()) return false;
        lockEnabled = !lockEnabled;
        applyWindowLimits();
        return lockEnabled;
    }

    public static boolean enableLock() {
        if (!canLock()) return false;
        if (lockEnabled) return true;
        lockEnabled = true;
        applyWindowLimits();
        return true;
    }

    public static boolean disableLock() {
        if (!canLock()) return false;
        if (!lockEnabled) return true;
        lockEnabled = false;
        applyWindowLimits();
        return true;
    }

    // ========== 屏幕事件：在"辅助功能设置"列表第一排插入"窗口设置"全宽按钮 ==========
    // 沿用原"视频设置"按钮的列表插入方式（同一个 WindowSettingsEntry），
    // 只是把目标界面换成辅助功能设置、位置固定在第一排。这样不占底部按钮位、
    // 不与其他 mod 在选项主界面抢位置，也不受 Embeddium 替换视频设置界面影响。

    public static class ScreenEventHandler {
        @SubscribeEvent
        @SuppressWarnings({"unchecked", "rawtypes"})
        public void onScreenInit(ScreenEvent.Init.Post event) {
            Screen screen = event.getScreen();
            if (!(screen instanceof AccessibilityOptionsScreen)) return;

            try {
                OptionsList optionsList = findOptionsList(screen);
                if (optionsList == null) throw new RuntimeException("OptionsList not found");
                List children = optionsList.children();
                // 固定插在列表最顶部（第一排）
                children.add(0, new WindowSettingsEntry(screen, optionsList));
            } catch (Exception ignored) {}
        }

        private static OptionsList findOptionsList(Screen screen) {
            Class<?> clazz = screen.getClass();
            while (clazz != null) {
                for (Field field : clazz.getDeclaredFields()) {
                    if (OptionsList.class.isAssignableFrom(field.getType())) {
                        field.setAccessible(true);
                        try { return (OptionsList) field.get(screen); }
                        catch (IllegalAccessException e) { return null; }
                    }
                }
                clazz = clazz.getSuperclass();
            }
            return null;
        }
    }

    public static class WindowSettingsEntry extends ContainerObjectSelectionList.Entry<WindowSettingsEntry> {
        private final Button button;
        private final OptionsList optionsList;
        private int refX = -1;
        private int refW = -1;

        public WindowSettingsEntry(Screen parent, OptionsList optionsList) {
            this.optionsList = optionsList;
            this.button = Button.builder(
                    Component.translatable("gui.autowindowsize.menu.button"),
                    btn -> Minecraft.getInstance().setScreen(new ConfigScreen(parent))
            ).bounds(0, 0, 310, 20).build();
        }

        @SuppressWarnings("unchecked")
        private void findRef() {
            // 扫描列表里所有现成控件，取最左边缘到最右边缘作为全宽。
            // 这样无论该界面是"单列全宽选项"（如视频设置）还是
            // "一行两个半宽按钮"（如辅助功能设置），都能得到与内容区严格等宽的按钮。
            int minX = Integer.MAX_VALUE;
            int maxRight = Integer.MIN_VALUE;
            for (Object obj : optionsList.children()) {
                if (obj == this) continue;
                if (!(obj instanceof ContainerObjectSelectionList.Entry<?> e)) continue;
                try {
                    for (var c : e.children()) {
                        if (c instanceof AbstractWidget w && w.getWidth() > 50 && w.visible) {
                            minX = Math.min(minX, w.getX());
                            maxRight = Math.max(maxRight, w.getX() + w.getWidth());
                        }
                    }
                } catch (Exception ignored) {}
            }
            if (maxRight > minX) {
                refX = minX;
                refW = maxRight - minX;
            }
        }

        @Override
        public void render(GuiGraphics guiGraphics, int index, int y, int x,
                           int entryWidth, int entryHeight, int mouseX, int mouseY,
                           boolean hovered, float partialTick) {
            if (refW <= 0) findRef();
            this.button.setX(refX >= 0 ? refX : x);
            this.button.setY(y);
            this.button.setWidth(refW > 0 ? refW : 310);
            this.button.render(guiGraphics, mouseX, mouseY, partialTick);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of(this.button);
        }

        @Override
        public List<? extends net.minecraft.client.gui.narration.NarratableEntry> narratables() {
            return List.of(this.button);
        }
    }

    // ========== 指令系统 ==========

    public static class CommandHandler {

        /** 自定义参数类型：匹配从当前位置到下一个空格（或末尾）的任意字符，含冒号等特殊字符。
         *  用于 /aws resolution 的比例参数（如 16:9），因为 StringArgumentType.word() 不匹配冒号。 */
        public static class SingleTokenArgument implements ArgumentType<String> {
            public static SingleTokenArgument token() { return new SingleTokenArgument(); }

            @Override
            public String parse(StringReader reader) throws CommandSyntaxException {
                int start = reader.getCursor();
                while (reader.canRead() && reader.peek() != ' ') {
                    reader.skip();
                }
                return reader.getString().substring(start, reader.getCursor());
            }
        }

        @SubscribeEvent
        public void onRegisterCommands(RegisterClientCommandsEvent event) {
            LiteralArgumentBuilder<CommandSourceStack> aws = Commands.literal("aws");

            // 顺序即游戏内 /aws 的补全/帮助顺序：help、gui 置顶，其余按功能近似度与添加时间排列
            aws.then(Commands.literal("help")
                    .executes(CommandHandler::cmdHelp)
                    .then(Commands.argument("page", IntegerArgumentType.integer(1,
                            (HELP_LINES.length - 1 + HELP_COMMANDS_PER_PAGE - 1) / HELP_COMMANDS_PER_PAGE))
                            .executes(CommandHandler::cmdHelpPage)));
            aws.then(Commands.literal("gui").executes(CommandHandler::cmdGui));
            aws.then(Commands.literal("about").executes(CommandHandler::cmdAbout));
            aws.then(Commands.literal("info").executes(CommandHandler::cmdInfo));
            aws.then(Commands.literal("version").executes(CommandHandler::cmdVersion));
            aws.then(Commands.literal("config").executes(CommandHandler::cmdConfig));
            aws.then(Commands.literal("status").executes(CommandHandler::cmdStatus));
            aws.then(Commands.literal("toggle")
                    .executes(CommandHandler::cmdToggle)
                    .then(Commands.argument("value", BoolArgumentType.bool())
                            .executes(CommandHandler::cmdToggle)));
            aws.then(Commands.literal("fixed")
                    .executes(CommandHandler::cmdFixed)
                    .then(Commands.argument("value", BoolArgumentType.bool())
                            .executes(CommandHandler::cmdFixed)));
            aws.then(Commands.literal("center").executes(CommandHandler::cmdCenter));
            aws.then(Commands.literal("fullscreen")
                    .executes(CommandHandler::cmdFullscreen)
                    .then(Commands.argument("value", BoolArgumentType.bool())
                            .executes(CommandHandler::cmdFullscreen)));
            aws.then(Commands.literal("maximize")
                    .executes(CommandHandler::cmdMaximize)
                    .then(Commands.argument("value", BoolArgumentType.bool())
                            .executes(CommandHandler::cmdMaximize)));
            aws.then(Commands.literal("remember")
                    .executes(CommandHandler::cmdRemember)
                    .then(Commands.argument("value", BoolArgumentType.bool())
                            .executes(CommandHandler::cmdRemember)));
            aws.then(Commands.literal("top")
                    .executes(CommandHandler::cmdTop)
                    .then(Commands.literal("off").executes(ctx -> cmdTopWithMode(ctx, 0)))
                    .then(Commands.literal("normal").executes(ctx -> cmdTopWithMode(ctx, 1)))
                    .then(Commands.literal("force").executes(ctx -> cmdTopWithMode(ctx, 2))));
            aws.then(Commands.literal("debug")
                    .executes(CommandHandler::cmdDebug)
                    .then(Commands.argument("value", BoolArgumentType.bool())
                            .executes(CommandHandler::cmdDebug)));
            aws.then(Commands.literal("borderless")
                    .executes(CommandHandler::cmdBorderless)
                    .then(Commands.argument("value", BoolArgumentType.bool())
                            .executes(CommandHandler::cmdBorderless))
                    .then(Commands.literal("auto")
                            .executes(CommandHandler::cmdAutoBorderless)
                            .then(Commands.argument("value", BoolArgumentType.bool())
                                    .executes(CommandHandler::cmdAutoBorderless))));
            // /aws resolution 支持两种格式：
            //   比例模式：/aws resolution <比例> <预设>，如 /aws resolution 16:9 1920x1080
            //   自定义模式：/aws resolution <宽> <高>，如 /aws resolution 1920 1080
            // 第一个参数是比例名时走比例模式，否则走自定义模式。
            aws.then(Commands.literal("resolution")
                    .then(Commands.argument("a", SingleTokenArgument.token())
                            .suggests(CommandHandler::suggestResolutionFirst)
                            .executes(CommandHandler::cmdResolutionIncomplete)
                            .then(Commands.argument("b", SingleTokenArgument.token())
                                    .suggests(CommandHandler::suggestResolutionSecond)
                                    .executes(CommandHandler::cmdResolutionTwo))));

            event.getDispatcher().register(aws);
        }

        private static final String[] HELP_LINES = {
                "message.autowindowsize.help.header",
                "message.autowindowsize.help.gui",
                "message.autowindowsize.help.about",
                "message.autowindowsize.help.info",
                "message.autowindowsize.help.version",
                "message.autowindowsize.help.config",
                "message.autowindowsize.help.status",
                "message.autowindowsize.help.toggle",
                "message.autowindowsize.help.fixed",
                "message.autowindowsize.help.center",
                "message.autowindowsize.help.fullscreen",
                "message.autowindowsize.help.maximize",
                "message.autowindowsize.help.remember",
                "message.autowindowsize.help.top",
                "message.autowindowsize.help.debug",
                "message.autowindowsize.help.borderless",
                "message.autowindowsize.help.autoborderless",
                "message.autowindowsize.help.resolution"
        };
        // HELP_LINES[0]是标题，HELP_LINES[1..18]是18个命令
        private static final int HELP_COMMANDS_PER_PAGE = 8;
        private static final int HELP_TOTAL_PAGES = (HELP_LINES.length - 1 + HELP_COMMANDS_PER_PAGE - 1) / HELP_COMMANDS_PER_PAGE;

        /** 判断指令是否带有 true/false 参数 */
        private static boolean hasBoolArg(CommandContext<CommandSourceStack> context) {
            try {
                BoolArgumentType.getBool(context, "value");
                return true;
            } catch (IllegalArgumentException e) {
                return false;
            }
        }
        private static int cmdHelp(CommandContext<CommandSourceStack> context) {
            return showHelpPage(context, 1);
        }

        private static int cmdHelpPage(CommandContext<CommandSourceStack> context) {
            int page = IntegerArgumentType.getInteger(context, "page");
            return showHelpPage(context, page);
        }

        private static int showHelpPage(CommandContext<CommandSourceStack> context, int page) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null) return 0;
            if (page < 1) page = 1;
            if (page > HELP_TOTAL_PAGES) page = HELP_TOTAL_PAGES;
            // 第1行：标题（每页都显示）
            player.displayClientMessage(Component.translatable(HELP_LINES[0]), false);
            // 第2-9行：8个命令
            int cmdStart = 1 + (page - 1) * HELP_COMMANDS_PER_PAGE;
            int cmdEnd = Math.min(cmdStart + HELP_COMMANDS_PER_PAGE, HELP_LINES.length);
            for (int i = cmdStart; i < cmdEnd; i++) {
                player.displayClientMessage(Component.translatable(HELP_LINES[i]), false);
            }
            // 第10行：页码提示（每页都显示，全蓝色）
            player.displayClientMessage(Component.translatable(
                    "message.autowindowsize.help.page", page, HELP_TOTAL_PAGES).withStyle(ChatFormatting.AQUA), false);
            return 1;
        }

        private static int cmdFullscreen(CommandContext<CommandSourceStack> context) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null) return 0;
            if (!canAutoFullscreen()) {
                player.displayClientMessage(Component.translatable("message.autowindowsize.autofs_unavailable"), false);
                return 0;
            }
            if (hasBoolArg(context)) {
                boolean target = BoolArgumentType.getBool(context, "value");
                if (target == Config.AUTO_FULLSCREEN.get()) {
                    player.displayClientMessage(Component.translatable(target ? "message.autowindowsize.autofs_already_on" : "message.autowindowsize.autofs_already_off"), false);
                    return 1;
                }
            }
            boolean now = toggleAutoFullscreenPref();
            player.displayClientMessage(Component.translatable(
                    now ? "message.autowindowsize.autofs_on" : "message.autowindowsize.autofs_off"), false);
            return 1;
        }

        private static int cmdMaximize(CommandContext<CommandSourceStack> context) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null) return 0;
            if (!canAutoMaximized()) {
                player.displayClientMessage(Component.translatable("message.autowindowsize.automax_unavailable"), false);
                return 0;
            }
            if (hasBoolArg(context)) {
                boolean target = BoolArgumentType.getBool(context, "value");
                if (target == Config.AUTO_MAXIMIZED.get()) {
                    player.displayClientMessage(Component.translatable(target ? "message.autowindowsize.automax_already_on" : "message.autowindowsize.automax_already_off"), false);
                    return 1;
                }
            }
            boolean now = toggleAutoMaximizedPref();
            player.displayClientMessage(Component.translatable(
                    now ? "message.autowindowsize.automax_on" : "message.autowindowsize.automax_off"), false);
            return 1;
        }

        private static int cmdRemember(CommandContext<CommandSourceStack> context) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null) return 0;
            if (hasBoolArg(context)) {
                boolean target = BoolArgumentType.getBool(context, "value");
                if (target == Config.REMEMBER_POSITION.get()) {
                    player.displayClientMessage(Component.translatable(target ? "message.autowindowsize.remember_already_on" : "message.autowindowsize.remember_already_off"), false);
                    return 1;
                }
            }
            boolean now = toggleRememberPosition();
            player.displayClientMessage(Component.translatable(
                    now ? "message.autowindowsize.remember_on" : "message.autowindowsize.remember_off"), false);
            return 1;
        }

        /** /aws top：循环切换窗口置顶 */
        private static int cmdTop(CommandContext<CommandSourceStack> context) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null) return 0;
            int mode = toggleAlwaysOnTop();
            String key = switch (mode) {
                case 1 -> "message.autowindowsize.top_normal";
                case 2 -> "message.autowindowsize.top_force";
                default -> "message.autowindowsize.top_off";
            };
            player.displayClientMessage(Component.translatable(key), false);
            return 1;
        }

        /** /aws top <off|normal|force>：直接设置窗口置顶模式 */
        private static int cmdTopWithMode(CommandContext<CommandSourceStack> context, int targetMode) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null) return 0;
            int current = Config.ALWAYS_ON_TOP_MODE.get();
            if (current == targetMode) {
                String key = switch (targetMode) {
                    case 1 -> "message.autowindowsize.top_normal";
                    case 2 -> "message.autowindowsize.top_force";
                    default -> "message.autowindowsize.top_off";
                };
                player.displayClientMessage(Component.translatable(key), false);
                return 1;
            }
            // 循环切换直到达到目标模式
            int mode = current;
            while (mode != targetMode) {
                mode = toggleAlwaysOnTop();
            }
            String key = switch (mode) {
                case 1 -> "message.autowindowsize.top_normal";
                case 2 -> "message.autowindowsize.top_force";
                default -> "message.autowindowsize.top_off";
            };
            player.displayClientMessage(Component.translatable(key), false);
            return 1;
        }

        /** /aws debug：切换调试模式 */
        private static int cmdDebug(CommandContext<CommandSourceStack> context) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null) return 0;
            if (hasBoolArg(context)) {
                boolean target = BoolArgumentType.getBool(context, "value");
                if (target == Config.DEBUG.get()) {
                    player.displayClientMessage(Component.translatable(target ? "message.autowindowsize.debug_already_on" : "message.autowindowsize.debug_already_off"), false);
                    return 1;
                }
            }
            boolean enabled = toggleDebug();
            String key = enabled ? "message.autowindowsize.debug_on" : "message.autowindowsize.debug_off";
            player.displayClientMessage(Component.translatable(key), false);
            return 1;
        }

        private static int cmdBorderless(CommandContext<CommandSourceStack> context) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null) return 0;
            if (hasBoolArg(context)) {
                boolean target = BoolArgumentType.getBool(context, "value");
                if (target == Config.BORDERLESS.get()) {
                    player.displayClientMessage(Component.translatable(target ? "message.autowindowsize.borderless_already_on" : "message.autowindowsize.borderless_already_off"), false);
                    return 1;
                }
            }
            boolean enabled = toggleBorderless();
            String key = enabled ? "message.autowindowsize.borderless_on" : "message.autowindowsize.borderless_off";
            player.displayClientMessage(Component.translatable(key), false);
            return 1;
        }

        private static int cmdAutoBorderless(CommandContext<CommandSourceStack> context) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null) return 0;
            if (hasBoolArg(context)) {
                boolean target = BoolArgumentType.getBool(context, "value");
                if (target == Config.AUTO_BORDERLESS.get()) {
                    player.displayClientMessage(Component.translatable(target ? "message.autowindowsize.autoborderless_already_on" : "message.autowindowsize.autoborderless_already_off"), false);
                    return 1;
                }
            }
            boolean enabled = toggleAutoBorderlessPref();
            String key = enabled ? "message.autowindowsize.autoborderless_on" : "message.autowindowsize.autoborderless_off";
            player.displayClientMessage(Component.translatable(key), false);
            return 1;
        }

        // ===== resolution 指令：兼容 /aws resolution <宽> <高> 和 /aws resolution <比例> <宽> <高> =====

        /** 只输入了 /aws resolution <一个值>：提示用法 */
        private static int cmdResolutionIncomplete(CommandContext<CommandSourceStack> context) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null) {
                player.displayClientMessage(Component.translatable("message.autowindowsize.resolution_usage"), false);
            }
            return 0;
        }

        /** 两参数格式：
         *  - 比例模式：/aws resolution <比例> <预设>，如 /aws resolution 16:9 1920x1080
         *  - 自定义模式：/aws resolution <宽> <高>，如 /aws resolution 1920 1080
         * 第一个参数是预设比例名时走比例模式，否则走自定义模式。 */
        private static int cmdResolutionTwo(CommandContext<CommandSourceStack> context) {
            String a = context.getArgument("a", String.class);
            String b = context.getArgument("b", String.class);
            LocalPlayer player = Minecraft.getInstance().player;

            // "自定义"不是命令行可用的比例，提示用两参数数字格式
            if (a.equals("自定义") || a.equals("Custom")) {
                if (player != null) {
                    player.displayClientMessage(Component.translatable("message.autowindowsize.resolution_custom_hint"), false);
                }
                return 0;
            }

            if (isPresetAspect(a)) {
                // ===== 比例模式：b 是预设，格式 "宽x高"（兼容 x/X/*/: ：分隔符） =====
                String[] parts = b.split("[xX*:：]");
                if (parts.length != 2) {
                    if (player != null) {
                        player.displayClientMessage(Component.translatable("message.autowindowsize.resolution_invalid_preset"), false);
                    }
                    return 0;
                }
                int w, h;
                try {
                    w = Integer.parseInt(parts[0].trim());
                    h = Integer.parseInt(parts[1].trim());
                } catch (NumberFormatException e) {
                    if (player != null) {
                        player.displayClientMessage(Component.translatable("message.autowindowsize.resolution_invalid_number"), false);
                    }
                    return 0;
                }
                // 验证是否是该比例的预设值
                int idx = aspectIndex(a);
                boolean valid = false;
                if (idx >= 0 && idx < PRESETS.length) {
                    for (int[] p : PRESETS[idx]) {
                        if (p[0] == w && p[1] == h) { valid = true; break; }
                    }
                }
                if (!valid) {
                    if (player != null) {
                        player.displayClientMessage(Component.translatable("message.autowindowsize.resolution_not_preset", a), false);
                    }
                    return 0;
                }
                return applyResolutionCommand(context, w, h);
            }

            // ===== 自定义模式：a=宽, b=高 =====
            int w, h;
            try {
                w = Integer.parseInt(a);
                h = Integer.parseInt(b);
            } catch (NumberFormatException e) {
                if (player != null) {
                    player.displayClientMessage(Component.translatable("message.autowindowsize.resolution_invalid_number"), false);
                }
                return 0;
            }
            return applyResolutionCommand(context, w, h);
        }

        /** 实际应用分辨率：范围校验（上限为当前屏幕分辨率）+ 状态检查 + 应用 */
        private static int applyResolutionCommand(CommandContext<CommandSourceStack> context, int w, int h) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null) return 0;
            // 范围校验：下限为硬编码最小值，上限为当前屏幕分辨率
            long hwnd = Minecraft.getInstance().getWindow().getWindow();
            int[] screenRes = getCurrentMonitorResolutionStatic(hwnd);
            if (w < HARD_MIN_WIDTH || w > screenRes[0] || h < HARD_MIN_HEIGHT || h > screenRes[1]) {
                player.displayClientMessage(Component.translatable("message.autowindowsize.resolution_out_of_range",
                        HARD_MIN_WIDTH, screenRes[0], HARD_MIN_HEIGHT, screenRes[1]), false);
                return 0;
            }
            if (fullscreenTempDisabled) {
                player.displayClientMessage(Component.translatable("message.autowindowsize.change_fullscreen"), false);
                return 0;
            }
            if (isMaximized()) {
                player.displayClientMessage(Component.translatable("message.autowindowsize.change_maximized"), false);
                return 0;
            }
            if (isFixedEnabled()) {
                player.displayClientMessage(Component.translatable("message.autowindowsize.change_fixed"), false);
                return 0;
            }
            applyResolutionPreset(w, h);
            player.displayClientMessage(Component.translatable("message.autowindowsize.resolution_applied", w, h), false);
            return 1;
        }

        // ===== resolution 指令补全 =====

        /** 第一个参数补全：只建议有预设的比例名（不包含"自定义"和预设分辨率） */
        private static CompletableFuture<Suggestions> suggestResolutionFirst(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
            String remaining = builder.getRemainingLowerCase();
            for (int i = 0; i < PRESETS.length; i++) {
                String name = ASPECT_NAMES[i];
                if (name.toLowerCase().contains(remaining)) builder.suggest(name);
            }
            return builder.buildFuture();
        }

        /** 第二个参数补全：若第一个是比例名→该比例的预设（格式"宽x高"，宽高均不超屏才显示，与设置界面一致）；否则（自定义模式）不补全 */
        private static CompletableFuture<Suggestions> suggestResolutionSecond(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
            String a = ctx.getArgument("a", String.class);
            int idx = aspectIndex(a);
            if (idx >= 0 && idx < PRESETS.length) {
                int[] screenRes = getCurrentMonitorResolutionStatic(Minecraft.getInstance().getWindow().getWindow());
                for (int[] p : PRESETS[idx]) {
                    if (p[0] <= screenRes[0] && p[1] <= screenRes[1]) {
                        builder.suggest(p[0] + "x" + p[1]);
                    }
                }
            }
            return builder.buildFuture();
        }

        /** 判断字符串是否是已知比例名（含"自定义"） */
        private static boolean isAspectName(String s) {
            for (String name : ASPECT_NAMES) if (name.equals(s)) return true;
            return false;
        }

        /** 判断字符串是否是有预设的比例名（排除"自定义"），用于命令行比例模式 */
        private static boolean isPresetAspect(String s) {
            for (int i = 0; i < PRESETS.length; i++) {
                if (ASPECT_NAMES[i].equals(s)) return true;
            }
            return false;
        }

        /** 比例名→组索引，找不到返回 -1 */
        private static int aspectIndex(String s) {
            for (int i = 0; i < ASPECT_NAMES.length; i++) {
                if (ASPECT_NAMES[i].equals(s)) return i;
            }
            return -1;
        }

        private static int cmdFixed(CommandContext<CommandSourceStack> context) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null) return 0;
            // 固定分辨率不受"配置分辨率过低"影响（它锁当前窗口大小，与配置值无关）
            if (fullscreenTempDisabled) {
                player.displayClientMessage(Component.translatable("message.autowindowsize.fixed_fullscreen_disabled"), false);
                return 0;
            }
            long hwnd = Minecraft.getInstance().getWindow().getWindow();
            if (GLFW.glfwGetWindowAttrib(hwnd, GLFW.GLFW_MAXIMIZED) == GLFW.GLFW_TRUE) {
                player.displayClientMessage(Component.translatable("message.autowindowsize.fixed_maximized"), false);
                return 0;
            }
            if (hasBoolArg(context)) {
                boolean target = BoolArgumentType.getBool(context, "value");
                if (target == fixedEnabled) {
                    player.displayClientMessage(Component.translatable(target ? "message.autowindowsize.fixed_already_on" : "message.autowindowsize.fixed_already_off"), false);
                    return 1;
                }
            }
            boolean nowFixed = toggleFixed();
            if (nowFixed) {
                player.displayClientMessage(Component.translatable("message.autowindowsize.fixed_on"), false);
            } else {
                player.displayClientMessage(Component.translatable("message.autowindowsize.fixed_off"), false);
            }
            return 1;
        }

        private static int cmdToggle(CommandContext<CommandSourceStack> context) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null) return 0;
            if (lockDisabled) {
                player.displayClientMessage(Component.translatable("message.autowindowsize.lock_disabled"), false);
                return 0;
            }
            if (fullscreenTempDisabled) {
                player.displayClientMessage(Component.translatable("message.autowindowsize.lock_fullscreen_disabled"), false);
                return 0;
            }
            if (hasBoolArg(context)) {
                boolean target = BoolArgumentType.getBool(context, "value");
                if (target == lockEnabled) {
                    player.displayClientMessage(Component.translatable(target ? "message.autowindowsize.lock_already_on" : "message.autowindowsize.lock_already_off"), false);
                    return 1;
                }
            }
            boolean nowLocked = toggleLock();
            if (nowLocked) {
                player.displayClientMessage(Component.translatable("message.autowindowsize.lock_on"), false);
            } else {
                player.displayClientMessage(Component.translatable("message.autowindowsize.lock_off"), false);
            }
            return 1;
        }
        private static int cmdStatus(CommandContext<CommandSourceStack> context) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null) return 0;

            if (lockDisabled) {
                player.displayClientMessage(Component.translatable("message.autowindowsize.status_disabled"), false);
            } else if (fullscreenTempDisabled) {
                player.displayClientMessage(Component.translatable("message.autowindowsize.status_fullscreen"), false);
            } else if (lockEnabled) {
                player.displayClientMessage(Component.translatable("message.autowindowsize.status_on"), false);
            } else {
                player.displayClientMessage(Component.translatable("message.autowindowsize.status_off"), false);
            }
            return 1;
        }

        private static int cmdGui(CommandContext<CommandSourceStack> context) {
            Minecraft mc = Minecraft.getInstance();
            mc.setScreen(new ConfigScreen(mc.screen));
            return 1;
        }

        private static int cmdAbout(CommandContext<CommandSourceStack> context) {
            Minecraft mc = Minecraft.getInstance();
            mc.setScreen(new ConfigScreen.AboutScreen(mc.screen));
            return 1;
        }

        private static int cmdCenter(CommandContext<CommandSourceStack> context) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null) return 0;
            if (!canCenter()) {
                Component reason = getCenterDisabledReason();
                player.displayClientMessage(Component.translatable("message.autowindowsize.center_unavailable",
                        reason != null ? reason : Component.empty()), false);
                return 0;
            }
            centerWindow();
            player.displayClientMessage(Component.translatable("message.autowindowsize.center_done"), false);
            return 1;
        }

        private static int cmdInfo(CommandContext<CommandSourceStack> context) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null) return 0;
            long hwnd = getWindowHandle();
            int[] screenRes = getCurrentMonitorResolutionStatic(hwnd);
            int winW = Minecraft.getInstance().getWindow().getGuiScaledWidth();
            int winH = Minecraft.getInstance().getWindow().getGuiScaledHeight();
            // 用实际窗口大小（不是GUI缩放后的）
            winW = Minecraft.getInstance().getWindow().getWidth();
            winH = Minecraft.getInstance().getWindow().getHeight();

            boolean isFs = GLFW.glfwGetWindowMonitor(hwnd) != 0;
            String windowStateKey = isFs ? "message.autowindowsize.state_fullscreen" : (isMaximized() ? "message.autowindowsize.state_maximized" : "message.autowindowsize.state_windowed");
            String lockStateKey = lockDisabled ? "message.autowindowsize.state_disabled_low_res" : (fullscreenTempDisabled ? "message.autowindowsize.state_disabled_fullscreen" : (lockEnabled ? "message.autowindowsize.state_enabled" : "message.autowindowsize.state_disabled"));
            int topMode = Config.ALWAYS_ON_TOP_MODE.get();
            String topStateKey = topMode == 0 ? "message.autowindowsize.state_off" : (topMode == 1 ? "message.autowindowsize.state_normal" : "message.autowindowsize.state_forced");
            String borderlessStateKey = Config.BORDERLESS.get() ? "message.autowindowsize.state_enabled" : "message.autowindowsize.state_disabled";
            String fixedStateKey = fixedEnabled ? "message.autowindowsize.state_enabled" : "message.autowindowsize.state_disabled";
            String rememberStateKey = Config.REMEMBER_POSITION.get() ? "message.autowindowsize.state_enabled" : "message.autowindowsize.state_disabled";
            String debugStateKey = Config.DEBUG.get() ? "message.autowindowsize.state_enabled" : "message.autowindowsize.state_disabled";

            player.displayClientMessage(Component.translatable("message.autowindowsize.info_title"), false);
            player.displayClientMessage(Component.translatable("message.autowindowsize.info_resolution", winW, winH, screenRes[0], screenRes[1]), false);
            player.displayClientMessage(Component.translatable("message.autowindowsize.info_window_state", Component.translatable(windowStateKey), Component.translatable(borderlessStateKey)), false);
            player.displayClientMessage(Component.translatable("message.autowindowsize.info_lock", Component.translatable(lockStateKey), Component.translatable(fixedStateKey)), false);
            player.displayClientMessage(Component.translatable("message.autowindowsize.info_top", Component.translatable(topStateKey), Component.translatable(rememberStateKey), Component.translatable(debugStateKey)), false);
            player.displayClientMessage(Component.translatable("message.autowindowsize.info_footer"), false);
            return 1;
        }

        private static int cmdVersion(CommandContext<CommandSourceStack> context) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null) return 0;
            player.displayClientMessage(Component.translatable("message.autowindowsize.version_title"), false);
            player.displayClientMessage(Component.translatable("message.autowindowsize.version_name"), false);
            player.displayClientMessage(Component.translatable("message.autowindowsize.version_number", "1.1.2"), false);
            player.displayClientMessage(Component.translatable("message.autowindowsize.version_author"), false);
            player.displayClientMessage(Component.translatable("message.autowindowsize.version_license"), false);
            player.displayClientMessage(Component.translatable("message.autowindowsize.version_supported"), false);
            player.displayClientMessage(Component.translatable("message.autowindowsize.version_email"), false);
            player.displayClientMessage(Component.translatable("message.autowindowsize.version_footer"), false);
            return 1;
        }

        private static int cmdConfig(CommandContext<CommandSourceStack> context) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null) return 0;
            try {
                java.io.File configDir = new java.io.File(Minecraft.getInstance().gameDirectory, "config/AutoWindowSize");
                if (!configDir.exists()) {
                    configDir.mkdirs();
                }
                net.minecraft.Util.getPlatform().openFile(configDir);
                player.displayClientMessage(Component.translatable("message.autowindowsize.config_opened", configDir.getAbsolutePath()), false);
            } catch (Exception e) {
                player.displayClientMessage(Component.translatable("message.autowindowsize.config_open_failed", e.getMessage()), false);
            }
            return 1;
        }
    }

    // ========== 内部事件处理器（按键 + 全屏检测） ==========

    public static class WindowHandler {
        private boolean wasFullscreen = false;
        private boolean wasMaximized = false;
        private boolean enteredGameMessageShown = false;
        private boolean windowStateFileEnsured = false;

        @SubscribeEvent
        public void onTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getInstance();

            // 配置迁移提示：进入世界后提示玩家配置已自动更新，无需手动删除配置文件
            if (configMigrated && !configMigrationNoticeShown && mc.player != null) {
                mc.player.displayClientMessage(Component.translatable("message.autowindowsize.config_migrated"), false);

                configMigrationNoticeShown = true;
                LOGGER.info("[AutoWindowSize] Config migration notice shown to player.");
            }

            long hwnd = mc.getWindow().getWindow();

            // 确保 window.json 在首次启动时就生成（不依赖延迟初始化路径），
            // 只尝试一次；全屏/最大化/小尺寸时跳过，等状态正常后下一帧再试。
            if (!windowStateFileEnsured) {
                ensureWindowStateFileExists();
                File f = new File(mc.gameDirectory, WINDOW_STATE_DIR + "/" + WINDOW_STATE_FILE);
                if (f.exists()) windowStateFileEnsured = true;
            }

            // 强制置顶模式：每帧都确保窗口在最顶端（点击标题栏、被其他置顶窗口盖住都能拉回来）
            if (Config.ALWAYS_ON_TOP_MODE.get() == 2) {
                GLFW.glfwFocusWindow(hwnd);
            }

            // 全屏检测：用 GLFW 原生 API
            boolean isFullscreen = GLFW.glfwGetWindowMonitor(hwnd) != 0;

            // 进入全屏
            if (isFullscreen && !wasFullscreen) {
                // 记录进入全屏前是否最大化：若加载期间先最大化再全屏，退出全屏后应恢复最大化
                wasMaximizedAtFullscreen = GLFW.glfwGetWindowAttrib(hwnd, GLFW.GLFW_MAXIMIZED) == GLFW.GLFW_TRUE;
                lockBeforeFullscreen = lockEnabled;
                fixedBeforeFullscreen = fixedEnabled;
                fullscreenTempDisabled = true;
                lockEnabled = false;
                fixedEnabled = false;
                // 全屏下窗口尺寸交给显示器模式：先恢复可调整属性，退出全屏时再按状态统一应用
                GLFW.glfwSetWindowAttrib(hwnd, GLFW.GLFW_RESIZABLE, GLFW.GLFW_TRUE);
                // 注意：不在聊天框重复提示"最小尺寸锁定已禁用"，状态切换消息已说明进入全屏，
                // 设置界面中的按钮会直观显示禁用状态。
            }

            // 退出全屏
            if (!isFullscreen && wasFullscreen) {
                fullscreenTempDisabled = false;
                // 循环切换窗口状态时，从全屏退出后需要强制还原为窗口化（避免 Minecraft 恢复到最大化）
                if (pendingRestoreToWindowed) {
                    pendingRestoreToWindowed = false;
                    GLFW.glfwRestoreWindow(hwnd);
                    if (Config.DEBUG.get()) LOGGER.info("[AWS CYCLE] restored to windowed after fullscreen exit");
                }
                if (!lockDisabled) {
                    if (lockBeforeFullscreen) {
                        lockEnabled = true;
                    } else {
                        lockEnabled = false;
                    }
                    if (fixedBeforeFullscreen) {
                        fixedEnabled = true;
                    }
                    applyWindowLimits();
                }
                // 若这次全屏发生在加载期间、初始化被跳过：
                //  - 进全屏前是最大化的（先最大化再全屏），退出后恢复最大化，
                //    不能再 setSize，否则会变成"假最大化"（还原按钮还在、实际尺寸却是配置值）；
                //  - 否则按配置值设大小并居中。
                if (deferredInitPending) {
                    deferredInitPending = false;
                    pendingCenterAfterAutoFullscreen = false;
                    // 先设置边框状态，再恢复窗口，确保最大化/居中布局基于正确的边框
                    applyBorderless();
                    if (wasMaximizedAtFullscreen) {
                        GLFW.glfwMaximizeWindow(hwnd);
                        // 恢复了最大化；等用户之后取消最大化时，再补一次配置大小与居中
                        pendingCenterAfterUnmaximize = true;
                    } else {
                        applyDeferredWindow();
                    }
                } else if (pendingCenterAfterAutoFullscreen) {
                    // 本次是"启动自动全屏"直接进的全屏：退出后恢复记忆位置或按配置值居中
                    pendingCenterAfterAutoFullscreen = false;
                    applyBorderless();
                    applyDeferredWindow();
                }
            }

            // 取消最大化（且非全屏）：若最大化发生在加载期间，取消后补上配置大小与居中
            boolean isMaximized = GLFW.glfwGetWindowAttrib(hwnd, GLFW.GLFW_MAXIMIZED) == GLFW.GLFW_TRUE;
            if (deferredInitPending && wasMaximized && !isMaximized && !isFullscreen) {
                deferredInitPending = false;
                applyDeferredWindow();
            }
            // 仅在"加载期间最大化→全屏→退出全屏恢复最大化→再取消最大化"这条链上补居中；
            // 游戏中正常的取消最大化不受影响，仍恢复最大化前的位置。
            if (pendingCenterAfterUnmaximize && wasMaximized && !isMaximized && !isFullscreen) {
                pendingCenterAfterUnmaximize = false;
                applyDeferredWindow();
            }

            // 从无边框伪全屏切换到最大化：伪全屏不是最大化状态，glfwRestoreWindow 不生效，
            // 直接 glfwMaximizeWindow 会因 GLFW_DECORATED=false 导致顶部空白。
            // 注意：全屏状态下 GLFW_MAXIMIZED 可能仍为 true，切换到伪全屏后该标志不会自动清除，
            // 因此必须用窗口大小区分：伪全屏大小=屏幕大小，真正最大化大小=工作区大小<屏幕大小。
            if (borderlessFullscreenActive && isMaximized) {
                int[] curW = new int[1], curH = new int[1];
                GLFW.glfwGetWindowSize(hwnd, curW, curH);
                long monitor = getCurrentMonitorStatic(hwnd);
                GLFWVidMode mode = GLFW.glfwGetVideoMode(monitor);
                // 只有窗口明显小于屏幕时才认为是真正的最大化（伪全屏大小=屏幕大小，不触发）
                if (mode != null && (curW[0] < mode.width() || curH[0] < mode.height())) {
                    borderlessFullscreenActive = false;
                    if (Config.DEBUG.get()) LOGGER.info("[AWS BORDERLESS] pseudo->maximized fix triggered: size={}x{} (screen={}x{}), two-step remaximize",
                            curW[0], curH[0], mode.width(), mode.height());
                    // 第一步：有边框最大化
                    GLFW.glfwSetWindowAttrib(hwnd, GLFW.GLFW_DECORATED, GLFW.GLFW_TRUE);
                    GLFW.glfwRestoreWindow(hwnd);
                    GLFW.glfwMaximizeWindow(hwnd);
                    // 第二步：切换到无边框最大化（与 toggleBorderless 最大化分支一致）
                    GLFW.glfwRestoreWindow(hwnd);
                    GLFW.glfwSetWindowAttrib(hwnd, GLFW.GLFW_DECORATED, GLFW.GLFW_FALSE);
                    GLFW.glfwMaximizeWindow(hwnd);
                }
            }
            // 从伪全屏退出到普通窗口化（窗口大小不再等于屏幕大小）：重置标记，
            // 避免标记残留导致后续操作误触发修复逻辑
            else if (borderlessFullscreenActive) {
                int[] curW = new int[1], curH = new int[1];
                GLFW.glfwGetWindowSize(hwnd, curW, curH);
                long monitor = getCurrentMonitorStatic(hwnd);
                GLFWVidMode mode = GLFW.glfwGetVideoMode(monitor);
                if (mode != null && (curW[0] != mode.width() || curH[0] != mode.height())) {
                    borderlessFullscreenActive = false;
                    if (Config.DEBUG.get()) LOGGER.info("[AWS BORDERLESS] pseudo exited to windowed: size={}x{} (screen={}x{}), reset flag",
                            curW[0], curH[0], mode.width(), mode.height());
                }
            }

            wasFullscreen = isFullscreen;
            wasMaximized = isMaximized;

            // 记录最后一次正常窗口状态（非全屏、非最大化、非最小化），供退出游戏时保存
            boolean isIconified = GLFW.glfwGetWindowAttrib(hwnd, GLFW.GLFW_ICONIFIED) == GLFW.GLFW_TRUE;
            if (!isFullscreen && !isMaximized && !isIconified) {
                int[] px = new int[1], py = new int[1];
                GLFW.glfwGetWindowPos(hwnd, px, py);
                lastNormalState = new int[]{px[0], py[0],
                        mc.getWindow().getScreenWidth(), mc.getWindow().getScreenHeight()};
            }

            if (mc.player == null) {
                enteredGameMessageShown = false;
                return;
            }

            if (!enteredGameMessageShown) {
                if (lockDisabled) {
                    mc.player.displayClientMessage(
                            Component.translatable("message.autowindowsize.lock_disabled_reason"), false);
                } else if (fullscreenTempDisabled) {
                    mc.player.displayClientMessage(
                            Component.translatable("message.autowindowsize.lock_fullscreen_disabled"), false);
                } else if (lockEnabled) {
                    mc.player.displayClientMessage(
                            Component.translatable("message.autowindowsize.lock_on"), false);
                } else {
                    mc.player.displayClientMessage(
                            Component.translatable("message.autowindowsize.lock_off"), false);
                }
                enteredGameMessageShown = true;
            }

            // 按键：打开窗口设置界面
            if (OPEN_SETTINGS_KEY.consumeClick()) {
                if (lockDisabled) {
                    mc.player.displayClientMessage(
                            Component.translatable("message.autowindowsize.lock_disabled"), false);
                } else {
                    mc.setScreen(new ConfigScreen(mc.screen));
                }
            }

            // 按键：居中窗口
            if (CENTER_KEY.consumeClick()) {
                if (!canCenter()) {
                    Component reason = getCenterDisabledReason();
                    mc.player.displayClientMessage(Component.translatable("message.autowindowsize.center_unavailable",
                            reason != null ? reason : Component.empty()), false);
                } else if (isWindowCentered()) {
                    mc.player.displayClientMessage(Component.translatable("message.autowindowsize.center_already"), false);
                } else {
                    centerWindow();
                    mc.player.displayClientMessage(Component.translatable("message.autowindowsize.center_done"), false);
                }
            }

            // 按键：切换无边框
            if (BORDERLESS_KEY.consumeClick()) {
                boolean now = toggleBorderless();
                mc.player.displayClientMessage(Component.translatable(
                        now ? "message.autowindowsize.borderless_on" : "message.autowindowsize.borderless_off"), false);
            }

            // 按键：循环切换窗口状态
            if (CYCLE_STATE_KEY.consumeClick()) {
                int newState = cycleWindowState();
                String stateKey = switch (newState) {
                    case 0 -> "message.autowindowsize.state_windowed";
                    case 1 -> "message.autowindowsize.state_maximized";
                    case 2 -> "message.autowindowsize.state_fullscreen";
                    default -> "message.autowindowsize.state_windowed";
                };
                mc.player.displayClientMessage(Component.translatable("message.autowindowsize.state_changed",
                        Component.translatable(stateKey)), false);
            }

            // 按键：切换窗口置顶模式
            if (TOP_KEY.consumeClick()) {
                int nextMode = toggleAlwaysOnTop();
                String modeKey = switch (nextMode) {
                    case 0 -> "message.autowindowsize.top_off";
                    case 1 -> "message.autowindowsize.top_normal";
                    case 2 -> "message.autowindowsize.top_force";
                    default -> "message.autowindowsize.top_off";
                };
                mc.player.displayClientMessage(Component.translatable("message.autowindowsize.top_changed",
                        Component.translatable(modeKey)), false);
            }
        }
    }

    // 延迟初始化处理器：主菜单加载后延迟几帧再设置窗口大小
    public static class DelayedInitHandler {
        private int ticks = 0;
        // 启动延迟帧数 = 配置秒数 × 20 TPS，范围 0.5~10.0 秒 → 10~200 帧
        private final int targetTicks = Math.max(1, (int) Math.round(Config.STARTUP_DELAY.get() * 20));

        @SubscribeEvent
        public void onTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            ticks++;
            if (ticks >= targetTicks) {
                // 延迟由配置 startupDelay 决定（默认 1.5 秒），确保引导界面/主菜单完全加载
                MinecraftForge.EVENT_BUS.unregister(this);
                initWindowStatic();
            }
        }
    }

    private static void initWindowStatic() {
        Minecraft mc = Minecraft.getInstance();
        long hwnd = mc.getWindow().getWindow();

        // ===== 1. 决定本次是否自动全屏 / 自动最大化（一次性引导 优先于 玩家持久化偏好）=====
        // 全屏与最大化互斥：两者同时为 true 时本次都不生效。
        boolean wantFullscreen;
        boolean wantMaximized;
        if (Config.APPLY_STARTUP_GUIDE.get()) {
            boolean guideFs = Config.START_FULLSCREEN.get();
            boolean guideMax = Config.START_MAXIMIZED.get();
            // 玩家已有持久化偏好（任一为 true）：说明他已经被引导过/自己设置过，
            // 一次性引导不再插手，把一次性相关开关全部归零，改走玩家自己的偏好。
            boolean playerHasPref = Config.AUTO_FULLSCREEN.get() || Config.AUTO_MAXIMIZED.get();
            if (playerHasPref) {
                Config.APPLY_STARTUP_GUIDE.set(false);
                Config.START_FULLSCREEN.set(false);
                Config.START_MAXIMIZED.set(false);
                saveConfig();
                wantFullscreen = Config.AUTO_FULLSCREEN.get();
                wantMaximized = Config.AUTO_MAXIMIZED.get();
                if (wantFullscreen && wantMaximized) {
                    wantFullscreen = false;
                    wantMaximized = false;
                    Config.AUTO_FULLSCREEN.set(false);
                    Config.AUTO_MAXIMIZED.set(false);
                    saveConfig();
                }
            } else {
                // 全新玩家：本次按引导目标决定；同步玩家偏好；值1消费后写回 false。
                if (guideFs && guideMax) {
                    // 互斥冲突：本次都不生效，并把两个引导目标自动写回 false。
                    guideFs = false;
                    guideMax = false;
                    Config.START_FULLSCREEN.set(false);
                    Config.START_MAXIMIZED.set(false);
                }
                Config.AUTO_FULLSCREEN.set(guideFs);
                Config.AUTO_MAXIMIZED.set(guideMax);
                Config.APPLY_STARTUP_GUIDE.set(false);
                saveConfig();
                wantFullscreen = guideFs;
                wantMaximized = guideMax;
            }
        } else {
            // 值1已消费（常规情况）：只看玩家在游戏内设置里的持久化偏好。
            wantFullscreen = Config.AUTO_FULLSCREEN.get();
            wantMaximized = Config.AUTO_MAXIMIZED.get();
            if (wantFullscreen && wantMaximized) {
                // 保险：理论上界面层已保证单选，手动改配置仍可能都 true。
                // 本次都不生效，并把两个都写回 false，否则下次启动还是冲突、局内按钮互相禁用。
                wantFullscreen = false;
                wantMaximized = false;
                Config.AUTO_FULLSCREEN.set(false);
                Config.AUTO_MAXIMIZED.set(false);
                saveConfig();
            }
        }

        // ===== 2. 分辨率过低检测：只影响"最小尺寸锁定"，不影响自动全屏/最大化 =====
        int[] monitorRes = getCurrentMonitorResolutionStatic(hwnd);
        int screenWidth = monitorRes[0];
        int screenHeight = monitorRes[1];
        int configWidth = Config.WINDOW_WIDTH.get();
        int configHeight = Config.WINDOW_HEIGHT.get();

        if (configWidth > screenWidth || configHeight > screenHeight
                || HARD_MIN_WIDTH > screenWidth || HARD_MIN_HEIGHT > screenHeight) {
            lockDisabled = true;
            lockEnabled = false;
        } else {
            lockDisabled = false;
        }

        // ===== 3. 本次要自动全屏：直接进入全屏，不设窗口尺寸、不居中 =====
        if (wantFullscreen) {
            // 玩家若在加载界面已经手动全屏了，就不重复切换；否则切到全屏。
            // 进/退全屏后的锁定状态由 WindowHandler 统一处理。
            boolean alreadyFullscreen = GLFW.glfwGetWindowMonitor(hwnd) != 0;
            if (!alreadyFullscreen) {
                mc.getWindow().toggleFullScreen();
            }
            // 记标志：退出全屏后补一次配置尺寸+居中（本次从未被 applyConfigWindow 过）。
            pendingCenterAfterAutoFullscreen = true;
            return;
        }

        // ===== 4. 本次要自动最大化：直接最大化，复用"加载期最大化"的后续恢复链 =====
        if (wantMaximized) {
            boolean alreadyMaximized = GLFW.glfwGetWindowAttrib(hwnd, GLFW.GLFW_MAXIMIZED) == GLFW.GLFW_TRUE;
            if (!alreadyMaximized) {
                GLFW.glfwMaximizeWindow(hwnd);
            }
            // 记 deferredInitPending：等用户之后取消最大化时，由 WindowHandler 自动
            // 补设配置尺寸+居中（与加载期用户手动最大化的处理链完全一致）。
            deferredInitPending = true;
            return;
        }

        // ===== 5. 既不全屏也不最大化：分辨率过低则到此为止；否则走"设配置尺寸+居中"或"记住位置"逻辑 =====
        if (lockDisabled) return;

        // 如果玩家在加载界面期间已经手动全屏或最大化了窗口，就先尊重其操作，
        // 不立刻强制改窗口大小/位置；记一个标记，等他退出全屏或取消最大化后
        // 再补上"按配置值设大小 + 居中"。否则取消最大化后窗口会停在系统默认大小，
        // 退出全屏后窗口也不会居中。
        boolean userFullscreen = GLFW.glfwGetWindowMonitor(hwnd) != 0;
        boolean userMaximized = GLFW.glfwGetWindowAttrib(hwnd, GLFW.GLFW_MAXIMIZED) == GLFW.GLFW_TRUE;
        if (userFullscreen || userMaximized) {
            deferredInitPending = true;
            return;
        }

        // 记住窗口位置功能：开启且保存文件有效时，恢复到上次退出时的位置与大小；
        // 保存文件不存在或无效（如显示器配置变化）时，回退到默认居中。
        if (Config.REMEMBER_POSITION.get()) {
            int[] saved = loadWindowState();
            if (saved != null) {
                restoreWindowState(saved);
            } else {
                applyConfigWindow();
            }
        } else {
            applyConfigWindow();
        }

        // 确保 window.json 存在（首次启动时创建默认文件，方便用户调试）；已存在则不覆盖
        ensureWindowStateFileExists();

        // ===== 6. 启动自动无边框：在窗口尺寸/位置确定后应用（全屏本身无边框，无需处理） =====
        if (Config.AUTO_BORDERLESS.get() && !Config.BORDERLESS.get()) {
            boolean isFs = GLFW.glfwGetWindowMonitor(hwnd) != 0;
            if (!isFs) {
                // 窗口化或最大化状态：直接切换无边框（toggleBorderless 内部会按状态走对应分支）
                toggleBorderless();
            } else {
                // 全屏状态：全屏本身就是无边框的，只需把配置标记设为 true
                Config.BORDERLESS.set(true);
                saveConfig();
            }
        }
    }

    /** 把窗口设为配置分辨率、在所在显示器居中，并按当前锁定状态设置尺寸限制。 */
    private static void applyConfigWindow() {
        long hwnd = Minecraft.getInstance().getWindow().getWindow();
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

        applyWindowLimits();
    }

    // ===== 窗口位置记忆 =====
    /** 窗口状态保存目录：config/AutoWindowSize/（所有本模组自建文件统一放这里） */
    private static final String WINDOW_STATE_DIR = "config/AutoWindowSize";
    private static final String WINDOW_STATE_FILE = "window.json";

    /** 如果窗口状态文件不存在，创建一个包含当前窗口状态的默认文件，方便用户调试。已存在则不覆盖。 */
    private static void ensureWindowStateFileExists() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.getWindow() == null) return;
            File dir = new File(mc.gameDirectory, WINDOW_STATE_DIR);
            dir.mkdirs();
            File file = new File(dir, WINDOW_STATE_FILE);
            if (file.exists()) return;
            long hwnd = mc.getWindow().getWindow();
            if (GLFW.glfwGetWindowMonitor(hwnd) != 0) return;
            if (GLFW.glfwGetWindowAttrib(hwnd, GLFW.GLFW_MAXIMIZED) == GLFW.GLFW_TRUE) return;
            int[] x = new int[1], y = new int[1];
            GLFW.glfwGetWindowPos(hwnd, x, y);
            int w = mc.getWindow().getScreenWidth();
            int h = mc.getWindow().getScreenHeight();
            if (w < HARD_MIN_WIDTH || h < HARD_MIN_HEIGHT) return;
            Map<String, Integer> data = new LinkedHashMap<>();
            data.put("x", x[0]);
            data.put("y", y[0]);
            data.put("width", w);
            data.put("height", h);
            try (FileWriter writer = new FileWriter(file)) {
                new GsonBuilder().setPrettyPrinting().create().toJson(data, writer);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to create default window state file", e);
        }
    }

    /** 退出游戏时保存窗口位置与大小。
     *  当前是全屏/最大化时，保存最后一次正常状态（全屏/最大化前的状态）；
     *  正常状态时直接保存当前状态。异常小尺寸不保存。 */
    private static void saveWindowState() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.getWindow() == null) return;
            long hwnd = mc.getWindow().getWindow();
            boolean isFullscreen = GLFW.glfwGetWindowMonitor(hwnd) != 0;
            boolean isMaximized = GLFW.glfwGetWindowAttrib(hwnd, GLFW.GLFW_MAXIMIZED) == GLFW.GLFW_TRUE;

            int[] state;
            if (isFullscreen || isMaximized) {
                // 全屏/最大化中：保存最后一次正常状态（即进入全屏/最大化前的窗口状态）
                if (lastNormalState == null) return;
                state = lastNormalState;
            } else {
                int[] x = new int[1], y = new int[1];
                GLFW.glfwGetWindowPos(hwnd, x, y);
                int w = mc.getWindow().getScreenWidth();
                int h = mc.getWindow().getScreenHeight();
                if (w < HARD_MIN_WIDTH || h < HARD_MIN_HEIGHT) return;
                state = new int[]{x[0], y[0], w, h};
            }

            Map<String, Integer> data = new LinkedHashMap<>();
            data.put("x", state[0]);
            data.put("y", state[1]);
            data.put("width", state[2]);
            data.put("height", state[3]);
            File dir = new File(mc.gameDirectory, WINDOW_STATE_DIR);
            dir.mkdirs();
            File file = new File(dir, WINDOW_STATE_FILE);
            try (FileWriter writer = new FileWriter(file)) {
                new GsonBuilder().setPrettyPrinting().create().toJson(data, writer);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to save window state", e);
        }
    }

    /** 从文件读取窗口位置与大小，验证有效后返回 int[]{x,y,w,h}，无效返回 null。
     *  验证项：尺寸 >= 硬编码最小值；窗口中心落在某台显示器范围内。 */
    private static int[] loadWindowState() {
        try {
            Minecraft mc = Minecraft.getInstance();
            File file = new File(mc.gameDirectory, WINDOW_STATE_DIR + "/" + WINDOW_STATE_FILE);
            if (!file.exists()) return null;
            JsonObject obj;
            try (FileReader reader = new FileReader(file)) {
                obj = JsonParser.parseReader(reader).getAsJsonObject();
            }
            int x = obj.get("x").getAsInt();
            int y = obj.get("y").getAsInt();
            int w = obj.get("width").getAsInt();
            int h = obj.get("height").getAsInt();
            if (w < HARD_MIN_WIDTH || h < HARD_MIN_HEIGHT) return null;
            // 验证窗口中心在某台显示器范围内（防止显示器配置变化后窗口跑到屏幕外）
            int cx = x + w / 2, cy = y + h / 2;
            boolean onMonitor = false;
            PointerBuffer monitors = GLFW.glfwGetMonitors();
            if (monitors != null) {
                for (int i = 0; i < monitors.limit(); i++) {
                    long mon = monitors.get(i);
                    int[] mx = new int[1], my = new int[1];
                    GLFW.glfwGetMonitorPos(mon, mx, my);
                    GLFWVidMode mode = GLFW.glfwGetVideoMode(mon);
                    if (cx >= mx[0] && cx < mx[0] + mode.width()
                            && cy >= my[0] && cy < my[0] + mode.height()) {
                        onMonitor = true;
                        break;
                    }
                }
            }
            if (!onMonitor) return null;
            return new int[]{x, y, w, h};
        } catch (Exception e) {
            LOGGER.warn("Failed to load window state", e);
            return null;
        }
    }

    /** 把窗口恢复到保存的位置与大小，并应用尺寸限制。 */
    private static void restoreWindowState(int[] state) {
        long hwnd = Minecraft.getInstance().getWindow().getWindow();
        GLFW.glfwSetWindowSize(hwnd, state[2], state[3]);
        GLFW.glfwSetWindowPos(hwnd, state[0], state[1]);
        applyWindowLimits();
    }

    /** 延迟初始化（退出全屏/最大化后）应用窗口：开启记忆位置且有保存状态时恢复，否则按配置值居中。
     *  记忆位置不依赖配置值，分辨率过低（lockDisabled）时仍可恢复；回退到配置值时才受 lockDisabled 限制。 */
    private static void applyDeferredWindow() {
        if (Config.REMEMBER_POSITION.get()) {
            int[] saved = loadWindowState();
            if (saved != null) {
                restoreWindowState(saved);
                return;
            }
        }
        if (!lockDisabled) {
            applyConfigWindow();
        }
    }
}
