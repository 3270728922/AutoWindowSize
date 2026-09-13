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
    // 锁定功能是否被永久禁用（当配置分辨率 >= 系统分辨率时为 true）
    private static boolean lockDisabled = false;
    // 全屏时临时禁用锁定
    private static boolean fullscreenTempDisabled = false;
    // 记录进入全屏前的锁定状态
    private static boolean lockBeforeFullscreen = true;

    // 按键绑定：默认未设置，玩家自行在控制设置中绑定
    public static final KeyMapping TOGGLE_LOCK_KEY = new KeyMapping(
            "key.autowindowsize.toggle_lock",
            InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(),
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

        if (lockEnabled) {
            GLFW.glfwSetWindowSizeLimits(hwnd, targetWidth, targetHeight,
                    GLFW.GLFW_DONT_CARE, GLFW.GLFW_DONT_CARE);
        } else {
            GLFW.glfwSetWindowSizeLimits(hwnd, 0, 0,
                    GLFW.GLFW_DONT_CARE, GLFW.GLFW_DONT_CARE);
        }
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

    public static boolean toggleLock() {
        if (!canLock()) return false;
        lockEnabled = !lockEnabled;
        long hwnd = Minecraft.getInstance().getWindow().getWindow();
        if (lockEnabled) {
            int w = Config.WINDOW_WIDTH.get();
            int h = Config.WINDOW_HEIGHT.get();
            GLFW.glfwSetWindowSizeLimits(hwnd, w, h,
                    GLFW.GLFW_DONT_CARE, GLFW.GLFW_DONT_CARE);
        } else {
            GLFW.glfwSetWindowSizeLimits(hwnd, 0, 0,
                    GLFW.GLFW_DONT_CARE, GLFW.GLFW_DONT_CARE);
        }
        return lockEnabled;
    }

    public static boolean enableLock() {
        if (!canLock()) return false;
        if (lockEnabled) return true;
        lockEnabled = true;
        long hwnd = Minecraft.getInstance().getWindow().getWindow();
        int w = Config.WINDOW_WIDTH.get();
        int h = Config.WINDOW_HEIGHT.get();
        GLFW.glfwSetWindowSizeLimits(hwnd, w, h,
                GLFW.GLFW_DONT_CARE, GLFW.GLFW_DONT_CARE);
        return true;
    }

    public static boolean disableLock() {
        if (!canLock()) return false;
        if (!lockEnabled) return true;
        lockEnabled = false;
        long hwnd = Minecraft.getInstance().getWindow().getWindow();
        GLFW.glfwSetWindowSizeLimits(hwnd, 0, 0,
                GLFW.GLFW_DONT_CARE, GLFW.GLFW_DONT_CARE);
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
        private int buttonX = -1;
        private int buttonWidth = -1;

        public WindowSettingsEntry(Screen parent, OptionsList optionsList) {
            this.optionsList = optionsList;
            this.button = Button.builder(
                    Component.translatable("gui.autowindowsize.menu.button"),
                    btn -> Minecraft.getInstance().setScreen(new ConfigScreen(parent))
            ).bounds(0, 0, 200, 20).build();
        }

        @SuppressWarnings("unchecked")
        private void copyResolutionButtonBounds() {
            int rowWidth = optionsList.getRowWidth();
            List<?> children = optionsList.children();
            for (int i = 0; i < children.size(); i++) {
                Object entry = children.get(i);
                if (entry == this) continue;
                if (!(entry instanceof ContainerObjectSelectionList.Entry<?> listEntry)) continue;
                try {
                    List<? extends GuiEventListener> entryChildren = listEntry.children();
                    for (GuiEventListener child : entryChildren) {
                        if (child instanceof AbstractWidget widget) {
                            String text = widget.getMessage().getString();
                            int w = widget.getWidth();
                            int wx = widget.getX();
                            if ((text.contains("分辨率") || text.toLowerCase().contains("resolution"))
                                    && w > 0 && w < rowWidth) {
                                this.buttonX = wx;
                                this.buttonWidth = w;
                                return;
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }
            this.buttonX = -1;
            this.buttonWidth = rowWidth - 64;
        }

        @Override
        public void render(GuiGraphics guiGraphics, int index, int y, int x,
                           int entryWidth, int entryHeight, int mouseX, int mouseY,
                           boolean hovered, float partialTick) {
            if (buttonWidth == -1) copyResolutionButtonBounds();
            if (buttonX >= 0) {
                this.button.setX(buttonX);
            } else {
                this.button.setX(x + (entryWidth - buttonWidth) / 2);
            }
            this.button.setY(y);
            this.button.setWidth(buttonWidth);
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

            event.getDispatcher().register(aws);
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
                lockBeforeFullscreen = lockEnabled;
                fullscreenTempDisabled = true;
                lockEnabled = false;
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
                        int w = Config.WINDOW_WIDTH.get();
                        int h = Config.WINDOW_HEIGHT.get();
                        GLFW.glfwSetWindowSizeLimits(hwnd, w, h,
                                GLFW.GLFW_DONT_CARE, GLFW.GLFW_DONT_CARE);
                    } else {
                        lockEnabled = false;
                        GLFW.glfwSetWindowSizeLimits(hwnd, 0, 0,
                                GLFW.GLFW_DONT_CARE, GLFW.GLFW_DONT_CARE);
                    }
                }
            }

            wasFullscreen = isFullscreen;

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

        int[] monitorRes = getCurrentMonitorResolutionStatic(hwnd);
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
        } else {
            GLFW.glfwSetWindowSizeLimits(hwnd, 0, 0,
                    GLFW.GLFW_DONT_CARE, GLFW.GLFW_DONT_CARE);
        }
    }
}
