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
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWVidMode;

import java.lang.reflect.Field;
import java.util.List;

@Mod("autowindowsize")
public class AutoWindowSize {

    // 写死的最小分辨率（16:9），配置文件不能低于这个
    // 优先级：代码硬编码最小值 > 配置文件值 > 玩家手动拖窗口大小
    private static final int HARD_MIN_WIDTH = 1024;
    private static final int HARD_MIN_HEIGHT = 576;

    // 锁定最小窗口的开关（默认开启，运行时状态，不写入配置文件）
    private static boolean lockEnabled = true;

    // 锁定功能是否被禁用（当配置分辨率 >= 系统分辨率时为 true）
    private static boolean lockDisabled = false;

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
        // 注册窗口大小事件处理器（按键切换 + 退出全屏恢复）
        MinecraftForge.EVENT_BUS.register(new WindowHandler());
        // 注册屏幕事件处理器（在选项界面原生插入"窗口设置"按钮）
        MinecraftForge.EVENT_BUS.register(new ScreenEventHandler());
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

        int configWidth = Config.WINDOW_WIDTH.get();
        int configHeight = Config.WINDOW_HEIGHT.get();

        // 如果配置分辨率 或 硬编码最小分辨率 高于系统分辨率：
        // 禁用锁定功能，不改动窗口大小，保持玩家原来的窗口
        if (configWidth > screenWidth || configHeight > screenHeight
                || HARD_MIN_WIDTH > screenWidth || HARD_MIN_HEIGHT > screenHeight) {
            lockDisabled = true;
            lockEnabled = false;
            // 不改动窗口分辨率，不设置最小窗口限制
            return;
        }

        // 正常情况：配置分辨率 <= 系统分辨率
        lockDisabled = false;

        int targetWidth = configWidth;
        int targetHeight = configHeight;

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

    // ========== 供外部调用的静态方法 ==========

    public static boolean isLockEnabled() {
        return lockEnabled;
    }

    public static boolean isLockDisabled() {
        return lockDisabled;
    }

    /**
     * 切换锁定状态。如果锁定被禁用则不做任何事。
     * @return 切换后是否处于锁定状态；被禁用时返回 false
     */
    public static boolean toggleLock() {
        if (lockDisabled) {
            return false;
        }
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

    // ========== 屏幕事件：在视频设置界面原生插入"窗口设置"按钮 ==========

    public static class ScreenEventHandler {

        @SubscribeEvent
        @SuppressWarnings({"unchecked", "rawtypes"})
        public void onScreenInit(ScreenEvent.Init.Post event) {
            Screen screen = event.getScreen();
            // 只在视频设置界面添加入口按钮
            // 主菜单：选项... → 视频设置...；游戏内：Esc → 选项 → 视频设置...
            if (!(screen instanceof VideoSettingsScreen)) {
                return;
            }

            // 尝试原生插入到 OptionsList（可滚动列表）
            try {
                // 遍历所有字段（含父类），按类型找 OptionsList，不依赖字段名
                OptionsList optionsList = findOptionsList(screen);
                if (optionsList == null) {
                    throw new RuntimeException("OptionsList not found");
                }

                List children = optionsList.children();

                // 找到"全屏分辨率"那一行，在它正下方插入
                int insertIndex = findResolutionRowIndex(children);
                WindowSettingsEntry entry = new WindowSettingsEntry(screen, optionsList);
                if (insertIndex >= 0) {
                    children.add(insertIndex + 1, entry);
                } else {
                    // 找不到分辨率行就加在列表最前面
                    children.add(0, entry);
                }
                return;
            } catch (Exception ignored) {
                // 反射失败，兜底：固定位置按钮
            }

            // 兜底：按钮放在"完成"按钮左边，同一行
            int buttonWidth = 150;
            int x = screen.width / 2 - 100 - buttonWidth - 5;
            int y = screen.height - 28;

            Button menuButton = Button.builder(
                    Component.translatable("gui.autowindowsize.menu.button"),
                    btn -> Minecraft.getInstance().setScreen(new ConfigScreen(screen))
            ).bounds(x, y, buttonWidth, 20).build();

            event.addListener(menuButton);
        }

        /**
         * 遍历 Screen 及其所有父类的字段，按类型找到 OptionsList。
         * 不依赖字段名，兼容不同 Forge/Minecraft 版本。
         */
        private static OptionsList findOptionsList(Screen screen) {
            Class<?> clazz = screen.getClass();
            while (clazz != null) {
                for (Field field : clazz.getDeclaredFields()) {
                    if (OptionsList.class.isAssignableFrom(field.getType())) {
                        field.setAccessible(true);
                        try {
                            return (OptionsList) field.get(screen);
                        } catch (IllegalAccessException ignored) {
                            return null;
                        }
                    }
                }
                clazz = clazz.getSuperclass();
            }
            return null;
        }

        /**
         * 遍历列表行，找到包含"分辨率"文本的那一行的索引。
         * 兼容中英文（"分辨率" / "resolution"）。
         */
        @SuppressWarnings("unchecked")
        private static int findResolutionRowIndex(List children) {
            for (int i = 0; i < children.size(); i++) {
                Object entry = children.get(i);

                // 方式1：通过 children() 方法获取该行的 widget
                try {
                    java.lang.reflect.Method childrenMethod = entry.getClass().getMethod("children");
                    List<? extends GuiEventListener> entryChildren =
                            (List<? extends GuiEventListener>) childrenMethod.invoke(entry);
                    for (GuiEventListener child : entryChildren) {
                        if (child instanceof AbstractWidget widget) {
                            String text = widget.getMessage().getString().toLowerCase();
                            if (text.contains("分辨率") || text.contains("resolution")) {
                                return i;
                            }
                        }
                    }
                } catch (Exception ignored) {}

                // 方式2：反射遍历 entry 所有字段（含父类），找 AbstractWidget 类型的字段
                try {
                    Class<?> clazz = entry.getClass();
                    while (clazz != null) {
                        for (java.lang.reflect.Field field : clazz.getDeclaredFields()) {
                            if (AbstractWidget.class.isAssignableFrom(field.getType())) {
                                field.setAccessible(true);
                                AbstractWidget widget = (AbstractWidget) field.get(entry);
                                if (widget != null) {
                                    String text = widget.getMessage().getString().toLowerCase();
                                    if (text.contains("分辨率") || text.contains("resolution")) {
                                        return i;
                                    }
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

    /**
     * 自定义列表行，里面放一个"窗口设置"按钮。
     * 原生插入视频设置界面的可滚动列表，随 GUI 缩放和滚动正常布局。
     * 因为 OptionsList.Entry 构造函数是 private 的，所以继承它的父类 ContainerObjectSelectionList.Entry。
     * 按钮位置和宽度在第一次 render 时从"全屏分辨率"按钮复制，保证完全一致。
     */
    public static class WindowSettingsEntry extends ContainerObjectSelectionList.Entry<WindowSettingsEntry> {
        private static final org.apache.logging.log4j.Logger LOGGER =
                org.apache.logging.log4j.LogManager.getLogger("AutoWindowSize");

        private final Button button;
        private final OptionsList optionsList;
        // 缓存"全屏分辨率"按钮的 x 和宽度，-1 表示还没获取
        private int buttonX = -1;
        private int buttonWidth = -1;

        public WindowSettingsEntry(Screen parent, OptionsList optionsList) {
            this.optionsList = optionsList;
            this.button = Button.builder(
                    Component.translatable("gui.autowindowsize.menu.button"),
                    btn -> Minecraft.getInstance().setScreen(new ConfigScreen(parent))
            ).bounds(0, 0, 200, 20).build();
        }

        /**
         * 遍历列表行，找到"全屏分辨率"那行的按钮，复制它的 x 和宽度。
         * 必须在 render 阶段调用，此时按钮已渲染，getX()/getWidth() 是实际值。
         * 同时输出调试日志，确认找对了按钮。
         */
        @SuppressWarnings("unchecked")
        private void copyResolutionButtonBounds() {
            int rowWidth = optionsList.getRowWidth();
            List<?> children = optionsList.children();
            LOGGER.info("[AutoWindowSize] 开始查找全屏分辨率按钮，rowWidth={}, 共{}行", rowWidth, children.size());

            for (int i = 0; i < children.size(); i++) {
                Object entry = children.get(i);
                if (entry == this) continue; // 跳过自己

                // 直接强制转换为 ContainerObjectSelectionList.Entry，调用 public 的 children()
                // 不用反射，避免 getMethod() 找不到方法的问题
                if (!(entry instanceof ContainerObjectSelectionList.Entry<?> listEntry)) {
                    LOGGER.warn("[AutoWindowSize] 行{}不是 ContainerObjectSelectionList.Entry，跳过", i);
                    continue;
                }

                try {
                    List<? extends GuiEventListener> entryChildren = listEntry.children();
                    for (GuiEventListener child : entryChildren) {
                        if (child instanceof AbstractWidget widget) {
                            String text = widget.getMessage().getString();
                            int w = widget.getWidth();
                            int wx = widget.getX();
                            LOGGER.info("[AutoWindowSize] 行{}: text='{}', x={}, width={}", i, text, wx, w);

                            // 找到"全屏分辨率"那行的按钮
                            if (text.contains("分辨率") || text.toLowerCase().contains("resolution")) {
                                if (w > 0 && w < rowWidth) {
                                    this.buttonX = wx;
                                    this.buttonWidth = w;
                                    LOGGER.info("[AutoWindowSize] 找到全屏分辨率按钮！x={}, width={}", wx, w);
                                    return;
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    LOGGER.warn("[AutoWindowSize] 行{}遍历失败: {}", i, e.getMessage());
                }
            }

            // 找不到就用兜底值：左对齐，宽度 = 整行宽 - 64
            this.buttonX = -1; // 用 render 里的 x 参数
            this.buttonWidth = rowWidth - 64;
            LOGGER.info("[AutoWindowSize] 未找到全屏分辨率按钮，使用兜底宽度={}", this.buttonWidth);
        }

        @Override
        public void render(GuiGraphics guiGraphics, int index, int y, int x,
                           int entryWidth, int entryHeight, int mouseX, int mouseY,
                           boolean hovered, float partialTick) {
            // 第一次 render 时复制"全屏分辨率"按钮的 x 和宽度
            if (buttonWidth == -1) {
                copyResolutionButtonBounds();
            }
            // 设置按钮位置和宽度
            if (buttonX >= 0) {
                this.button.setX(buttonX); // 用复制的 x（屏幕绝对坐标）
            } else {
                this.button.setX(x + (entryWidth - buttonWidth) / 2); // 兜底：居中
            }
            this.button.setY(y);
            this.button.setWidth(buttonWidth);
            this.button.render(guiGraphics, mouseX, mouseY, partialTick);
        }

        @Override
        public java.util.List<? extends net.minecraft.client.gui.components.events.GuiEventListener> children() {
            return java.util.Collections.singletonList(this.button);
        }

        @Override
        public java.util.List<? extends net.minecraft.client.gui.narration.NarratableEntry> narratables() {
            return java.util.Collections.singletonList(this.button);
        }
    }

    // ========== 内部事件处理器（按键 + 退出全屏） ==========

    public static class WindowHandler {
        private boolean wasFullscreen = false;
        // 进入游戏后是否已提示过当前锁定状态（每次进游戏提示一次）
        private boolean enteredGameMessageShown = false;

        @SubscribeEvent
        public void onTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getInstance();

            // 玩家不在游戏里（主菜单/加载界面）时重置标志位，下次进游戏再提示
            if (mc.player == null) {
                enteredGameMessageShown = false;
                return;
            }

            // 每次进入游戏，聊天栏提示一次当前锁定状态
            if (!enteredGameMessageShown) {
                if (lockDisabled) {
                    // 锁定被禁用：提示原因
                    mc.player.displayClientMessage(
                            Component.translatable("message.autowindowsize.lock_disabled_reason"), false);
                } else if (lockEnabled) {
                    mc.player.displayClientMessage(
                            Component.translatable("message.autowindowsize.lock_on"), false);
                } else {
                    mc.player.displayClientMessage(
                            Component.translatable("message.autowindowsize.lock_off"), false);
                }
                enteredGameMessageShown = true;
            }

            // 检测按键：切换锁定
            if (TOGGLE_LOCK_KEY.consumeClick()) {
                if (lockDisabled) {
                    // 锁定被禁用时再次在聊天栏提示分辨率过低
                    mc.player.displayClientMessage(
                            Component.translatable("message.autowindowsize.lock_disabled"), false);
                } else {
                    boolean nowLocked = toggleLock();
                    if (nowLocked) {
                        mc.player.displayClientMessage(
                                Component.translatable("message.autowindowsize.lock_on"), false);
                    } else {
                        mc.player.displayClientMessage(
                                Component.translatable("message.autowindowsize.lock_off"), false);
                    }
                }
            }

            // 检测退出全屏 → 恢复配置窗口大小并居中
            boolean isFullscreen = mc.getWindow().isFullscreen();
            if (wasFullscreen && !isFullscreen) {
                // 锁定被禁用时，退出全屏不改动窗口大小
                if (lockDisabled) {
                    wasFullscreen = isFullscreen;
                    return;
                }

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

                // 只有在锁定开启时才设置最小窗口限制
                if (lockEnabled) {
                    GLFW.glfwSetWindowSizeLimits(hwnd, targetWidth, targetHeight,
                            GLFW.GLFW_DONT_CARE, GLFW.GLFW_DONT_CARE);
                } else {
                    GLFW.glfwSetWindowSizeLimits(hwnd, 0, 0,
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
