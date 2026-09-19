package com.example.autowindowsize;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.VideoSettingsScreen;
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
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import com.mojang.brigadier.context.CommandContext;
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

@Mod("autowindowsize")
public class AutoWindowSize {

    private static final org.apache.logging.log4j.Logger LOGGER =
            org.apache.logging.log4j.LogManager.getLogger("AutoWindowSize");

    // 写死的最小分辨率（16:9），统一定义在 Config 中；配置文件的取值下限也指向同一常量，
    // 这样配置层和运行时层不会脱节，杜绝"配置能填到比硬写死分辨率更小"的 bug。
    private static final int HARD_MIN_WIDTH = Config.HARD_MIN_WIDTH;
    private static final int HARD_MIN_HEIGHT = Config.HARD_MIN_HEIGHT;

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
    // 本次是靠"启动自动全屏"直接进的全屏：退出全屏后需要补一次配置尺寸+居中
    // （因为自动全屏分支跳过了 applyConfigWindow，窗口从未被居中过）。
    private static boolean pendingCenterAfterAutoFullscreen = false;
    // 进入全屏前窗口是否处于最大化（用于退出全屏后决定是恢复最大化还是补设配置值）
    private static boolean wasMaximizedAtFullscreen = false;
    // 加载期间"最大化→全屏→退出全屏恢复最大化"后，等用户再取消最大化时补设配置大小并居中
    private static boolean pendingCenterAfterUnmaximize = false;

    // 按键绑定：默认未设置，玩家自行在控制设置中绑定
    public static final KeyMapping TOGGLE_LOCK_KEY = new KeyMapping(
            "key.autowindowsize.toggle_lock",
            InputConstants.Type.KEYSYM,
            -1,
            "key.categories.autowindowsize"
    );

    public AutoWindowSize() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, Config.SPEC);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onClientSetup);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onRegisterKeyMappings);
        MinecraftForge.EVENT_BUS.register(new WindowHandler());
        MinecraftForge.EVENT_BUS.register(new ScreenEventHandler());
        MinecraftForge.EVENT_BUS.register(new CommandHandler());
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        // 延迟初始化：等游戏窗口完全创建后再设置大小
        // 不检测特定界面，避免第一次启动引导界面时漏掉
        MinecraftForge.EVENT_BUS.register(new DelayedInitHandler());
    }

    private void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE_LOCK_KEY);
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

        GLFW.glfwSetWindowSize(hwnd, targetWidth, targetHeight);
        long monitor = getCurrentMonitor(hwnd);
        int[] monX = new int[1], monY = new int[1];
        GLFW.glfwGetMonitorPos(monitor, monX, monY);
        GLFWVidMode mode = GLFW.glfwGetVideoMode(monitor);
        int posX = monX[0] + (mode.width() - targetWidth) / 2;
        int posY = monY[0] + (mode.height() - targetHeight) / 2;
        GLFW.glfwSetWindowPos(hwnd, posX, posY);

        applyWindowLimits();
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
        if (GLFW.glfwGetWindowMonitor(hwnd) != 0) {
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
    public static final String[] ASPECT_NAMES = {"16:9", "16:10", "4:3", "5:4", "21:9", "自定义"};
    public static final int[][][] PRESETS = {
            {{856, 482}, {1024, 576}, {1152, 648}, {1280, 720}, {1366, 768}, {1600, 900}, {1920, 1080}, {2560, 1440}, {3840, 2160}},
            {{1024, 640}, {1280, 800}, {1440, 900}, {1680, 1050}, {1920, 1200}, {2560, 1600}},
            {{640, 480}, {800, 600}, {1024, 768}, {1152, 864}, {1280, 960}, {1400, 1050}, {1600, 1200}, {1920, 1440}},
            {{1280, 1024}, {1600, 1280}, {1920, 1536}},
            {{2560, 1080}, {3440, 1440}, {3840, 1600}},
    };

    /** 预设按钮是否可用：全屏或最大化时不可用。 */
    public static boolean canApplyPreset() {
        long hwnd = getWindowHandle();
        if (GLFW.glfwGetWindowMonitor(hwnd) != 0) return false;
        if (GLFW.glfwGetWindowAttrib(hwnd, GLFW.GLFW_MAXIMIZED) == GLFW.GLFW_TRUE) return false;
        return true;
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

    /** 应用指定分辨率预设：先写回配置并更新尺寸限制，再设窗口大小、居中。 */
    public static void applyResolutionPreset(int w, int h) {
        long hwnd = getWindowHandle();
        // 先写回配置并更新 GLFW 最小尺寸限制，否则锁定开着时窗口不能变小，
        // glfwSetWindowSize 会被旧的最小尺寸拦截、窗口纹丝不动。
        Config.WINDOW_WIDTH.set(w);
        Config.WINDOW_HEIGHT.set(h);
        saveConfig();
        applyWindowLimits();

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

    // ========== 屏幕事件：在视频设置界面原生插入"窗口设置"按钮 ==========

    public static class ScreenEventHandler {
        @SubscribeEvent
        @SuppressWarnings({"unchecked", "rawtypes"})
        public void onScreenInit(ScreenEvent.Init.Post event) {
            Screen screen = event.getScreen();
            if (!(screen instanceof VideoSettingsScreen)) return;

            try {
                OptionsList optionsList = findOptionsList(screen);
                if (optionsList == null) throw new RuntimeException("OptionsList not found");
                List children = optionsList.children();
                int insertIndex = findResolutionRowIndex(children);
                WindowSettingsEntry entry = new WindowSettingsEntry(screen, optionsList);
                if (insertIndex >= 0) {
                    children.add(insertIndex + 1, entry);
                } else {
                    children.add(0, entry);
                }
                return;
            } catch (Exception ignored) {}

            int buttonWidth = 150;
            int x = screen.width / 2 - 100 - buttonWidth - 5;
            int y = screen.height - 28;
            Button menuButton = Button.builder(
                    Component.translatable("gui.autowindowsize.menu.button"),
                    btn -> Minecraft.getInstance().setScreen(new ConfigScreen(screen))
            ).bounds(x, y, buttonWidth, 20).build();
            event.addListener(menuButton);
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

        @SuppressWarnings("unchecked")
        private static int findResolutionRowIndex(List children) {
            for (int i = 0; i < children.size(); i++) {
                Object entry = children.get(i);
                try {
                    java.lang.reflect.Method childrenMethod = entry.getClass().getMethod("children");
                    List<? extends GuiEventListener> entryChildren =
                            (List<? extends GuiEventListener>) childrenMethod.invoke(entry);
                    for (GuiEventListener child : entryChildren) {
                        if (child instanceof AbstractWidget widget) {
                            String text = widget.getMessage().getString().toLowerCase();
                            if (text.contains("分辨率") || text.contains("resolution")) return i;
                        }
                    }
                } catch (Exception ignored) {}
                try {
                    Class<?> clazz = entry.getClass();
                    while (clazz != null) {
                        for (java.lang.reflect.Field field : clazz.getDeclaredFields()) {
                            if (AbstractWidget.class.isAssignableFrom(field.getType())) {
                                field.setAccessible(true);
                                AbstractWidget widget = (AbstractWidget) field.get(entry);
                                if (widget != null) {
                                    String text = widget.getMessage().getString().toLowerCase();
                                    if (text.contains("分辨率") || text.contains("resolution")) return i;
                                }
                            }
                        }
                        clazz = clazz.getSuperclass();
                    }
                } catch (Exception ignored) {}
            }
            return -1;
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
            for (Object obj : optionsList.children()) {
                if (obj == this) continue;
                if (!(obj instanceof ContainerObjectSelectionList.Entry<?> e)) continue;
                try {
                    for (var c : e.children()) {
                        if (c instanceof AbstractWidget w && w.getWidth() > 100) {
                            refX = w.getX();
                            refW = w.getWidth();
                            return;
                        }
                    }
                } catch (Exception ignored) {}
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
        @SubscribeEvent
        public void onRegisterCommands(RegisterClientCommandsEvent event) {
            LiteralArgumentBuilder<CommandSourceStack> aws = Commands.literal("aws");

            aws.then(Commands.literal("toggle").executes(ctx -> cmdToggle(ctx)));
            aws.then(Commands.literal("lock").executes(ctx -> cmdLock(ctx)));
            aws.then(Commands.literal("unlock").executes(ctx -> cmdUnlock(ctx)));
            aws.then(Commands.literal("status").executes(ctx -> cmdStatus(ctx)));
            aws.then(Commands.literal("gui").executes(ctx -> cmdGui(ctx)));
            aws.then(Commands.literal("center").executes(ctx -> cmdCenter(ctx)));
            aws.then(Commands.literal("fixed").executes(ctx -> cmdFixed(ctx)));

            event.getDispatcher().register(aws);
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
            boolean nowLocked = toggleLock();
            if (nowLocked) {
                player.displayClientMessage(Component.translatable("message.autowindowsize.lock_on"), false);
            } else {
                player.displayClientMessage(Component.translatable("message.autowindowsize.lock_off"), false);
            }
            return 1;
        }

        private static int cmdLock(CommandContext<CommandSourceStack> context) {
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
            if (lockEnabled) {
                player.displayClientMessage(Component.translatable("message.autowindowsize.lock_already_on"), false);
                return 1;
            }
            enableLock();
            player.displayClientMessage(Component.translatable("message.autowindowsize.lock_on"), false);
            return 1;
        }

        private static int cmdUnlock(CommandContext<CommandSourceStack> context) {
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
            if (!lockEnabled) {
                player.displayClientMessage(Component.translatable("message.autowindowsize.lock_already_off"), false);
                return 1;
            }
            disableLock();
            player.displayClientMessage(Component.translatable("message.autowindowsize.lock_off"), false);
            return 1;
        }

        private static int cmdStatus(CommandContext<CommandSourceStack> context) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null) return 0;
            long hwnd = Minecraft.getInstance().getWindow().getWindow();
            int[] res = getCurrentMonitorResolutionStatic(hwnd);
            String screenRes = res[0] + "×" + res[1];

            if (lockDisabled) {
                player.displayClientMessage(Component.translatable("message.autowindowsize.status_disabled", screenRes), false);
            } else if (fullscreenTempDisabled) {
                player.displayClientMessage(Component.translatable("message.autowindowsize.status_fullscreen", screenRes), false);
            } else if (lockEnabled) {
                player.displayClientMessage(Component.translatable("message.autowindowsize.status_on", screenRes), false);
            } else {
                player.displayClientMessage(Component.translatable("message.autowindowsize.status_off", screenRes), false);
            }
            return 1;
        }

        private static int cmdGui(CommandContext<CommandSourceStack> context) {
            Minecraft mc = Minecraft.getInstance();
            mc.setScreen(new ConfigScreen(mc.screen));
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
    }

    // ========== 内部事件处理器（按键 + 全屏检测） ==========

    public static class WindowHandler {
        private boolean wasFullscreen = false;
        private boolean wasMaximized = false;
        private boolean enteredGameMessageShown = false;

        @SubscribeEvent
        public void onTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getInstance();
            long hwnd = mc.getWindow().getWindow();

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
                if (mc.player != null) {
                    if (lockBeforeFullscreen) {
                        mc.player.displayClientMessage(
                                Component.translatable("message.autowindowsize.lock_fullscreen_disabled_on"), false);
                    } else {
                        mc.player.displayClientMessage(
                                Component.translatable("message.autowindowsize.lock_fullscreen_disabled_off"), false);
                    }
                }
            }

            // 退出全屏
            if (!isFullscreen && wasFullscreen) {
                fullscreenTempDisabled = false;
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
                    if (wasMaximizedAtFullscreen) {
                        GLFW.glfwMaximizeWindow(hwnd);
                        // 恢复了最大化；等用户之后取消最大化时，再补一次配置大小与居中
                        pendingCenterAfterUnmaximize = true;
                    } else {
                        applyConfigWindow();
                    }
                } else if (pendingCenterAfterAutoFullscreen) {
                    // 本次是"启动自动全屏"直接进的全屏：退出后回到配置尺寸并居中
                    pendingCenterAfterAutoFullscreen = false;
                    if (!lockDisabled) {
                        applyConfigWindow();
                    }
                }
            }

            // 取消最大化（且非全屏）：若最大化发生在加载期间，取消后补上配置大小与居中
            boolean isMaximized = GLFW.glfwGetWindowAttrib(hwnd, GLFW.GLFW_MAXIMIZED) == GLFW.GLFW_TRUE;
            if (deferredInitPending && wasMaximized && !isMaximized && !isFullscreen) {
                deferredInitPending = false;
                applyConfigWindow();
            }
            // 仅在"加载期间最大化→全屏→退出全屏恢复最大化→再取消最大化"这条链上补居中；
            // 游戏中正常的取消最大化不受影响，仍恢复最大化前的位置。
            if (pendingCenterAfterUnmaximize && wasMaximized && !isMaximized && !isFullscreen) {
                pendingCenterAfterUnmaximize = false;
                applyConfigWindow();
            }

            wasFullscreen = isFullscreen;
            wasMaximized = isMaximized;

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
            if (TOGGLE_LOCK_KEY.consumeClick()) {
                if (lockDisabled) {
                    mc.player.displayClientMessage(
                            Component.translatable("message.autowindowsize.lock_disabled"), false);
                } else {
                    mc.setScreen(new ConfigScreen(mc.screen));
                }
            }
        }
    }

    // 延迟初始化处理器：主菜单加载后延迟几帧再设置窗口大小
    public static class DelayedInitHandler {
        private int ticks = 0;

        @SubscribeEvent
        public void onTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            ticks++;
            if (ticks >= 30) {
                // 延迟30帧（约1.5秒，20 TPS），确保引导界面/主菜单完全加载，低配电脑也够用
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

        // ===== 5. 既不全屏也不最大化：分辨率过低则到此为止；否则走原"设配置尺寸+居中"逻辑 =====
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

        applyConfigWindow();
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
}
