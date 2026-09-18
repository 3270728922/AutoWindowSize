package com.example.autowindowsize;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWVidMode;

public class ConfigScreen extends Screen {
    private final Screen parent;
    private static final int BUTTON_WIDTH = 200;
    private static final int BUTTON_HEIGHT = 20;
    private Button lockButton;
    private Button fixedButton;
    private Button autoFsButton;
    private Button autoMaxButton;
    private Button centerButton;
    private int screenWidth;
    private int screenHeight;
    private int gameWidth;
    private int gameHeight;
    /** 信息区（分辨率文字 + 提示）起始 Y，init 时按按钮列结束位置计算 */
    private int infoY;

    // 实时尺寸跟踪：window 模式下拖动窗口会连续上报 resize；
    // 拖动时数字冻结、黄色提示亮，停止 0.5 秒后刷新数字并提示灭；
    // 之后 0.2 秒内的 settle 尾巴静默跟新、不再亮提示。
    private boolean fullscreen;
    private boolean dragging;
    private long dragStartMs;
    private long settledAtMs;
    private int lastSeenWidth = -1;
    private int lastSeenHeight = -1;
    private static final long DRAG_DEBOUNCE_MS = 500L;
    private static final long SETTLE_GRACE_MS = 200L;

    public ConfigScreen(Screen parent) {
        super(Component.translatable("gui.autowindowsize.config.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;

        long hwnd = AutoWindowSize.getWindowHandle();
        long monitor = AutoWindowSize.getCurrentMonitorStatic(hwnd);
        GLFWVidMode mode = GLFW.glfwGetVideoMode(monitor);
        if (mode != null) {
            this.screenWidth = mode.width();
            this.screenHeight = mode.height();
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() != null) {
            int curW = mc.getWindow().getScreenWidth();
            int curH = mc.getWindow().getScreenHeight();
            if (this.gameWidth <= 0 || this.gameHeight <= 0) {
                // 首次打开：用当前窗口尺寸初始化显示值
                this.gameWidth = curW;
                this.gameHeight = curH;
            }
            // 窗口 resize 会触发 Minecraft 重新调用 init()，这里不能覆盖已显示的
            // 分辨率数字，否则拖动窗口时数字会跟着走、"冻结"就失效了。
            this.lastSeenWidth = curW;
            this.lastSeenHeight = curH;
        }
        this.dragging = false;
        this.fullscreen = false;

        // ---- 布局：按钮列垂直居中，用游标顺次往下排，以后加按钮只改这里 ----
        // 按钮列 5 个：5*20 + 4*4(间距) = 116；信息区预留 3 行 *12 = 36，共约 160
        final int rowStep = BUTTON_HEIGHT + 4;          // 24
        final int buttonsBlockH = rowStep * 4 + BUTTON_HEIGHT; // 116
        final int infoBlockH = 3 * 12;                  // 屏幕/窗口分辨率 + 第三行提示
        final int totalBlockH = buttonsBlockH + 8 + infoBlockH;
        int y = this.height / 2 - totalBlockH / 2;      // 按钮列起点

        this.lockButton = Button.builder(
                getLockButtonText(),
                btn -> {
                    if (AutoWindowSize.canLock()) {
                        AutoWindowSize.toggleLock();
                        btn.setMessage(getLockButtonText());
                    }
                }
        ).bounds(centerX - BUTTON_WIDTH / 2, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                .tooltip(Tooltip.create(Component.translatable("gui.autowindowsize.lock.tooltip"))).build();
        this.addRenderableWidget(this.lockButton);

        y += rowStep;
        this.fixedButton = Button.builder(
                getFixedButtonText(),
                btn -> {
                    if (AutoWindowSize.canFixed()) {
                        AutoWindowSize.toggleFixed();
                        btn.setMessage(getFixedButtonText());
                    }
                }
        ).bounds(centerX - BUTTON_WIDTH / 2, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                .tooltip(Tooltip.create(Component.translatable("gui.autowindowsize.fixed.tooltip"))).build();
        this.addRenderableWidget(this.fixedButton);

        y += rowStep;
        this.autoFsButton = Button.builder(
                getAutoFsButtonText(),
                btn -> {
                    if (AutoWindowSize.canAutoFullscreen()) {
                        AutoWindowSize.toggleAutoFullscreenPref();
                    }
                    btn.setMessage(getAutoFsButtonText());
                }
        ).bounds(centerX - BUTTON_WIDTH / 2, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                .tooltip(Tooltip.create(Component.translatable("gui.autowindowsize.autofs.tooltip"))).build();
        this.addRenderableWidget(this.autoFsButton);

        y += rowStep;
        this.autoMaxButton = Button.builder(
                getAutoMaxButtonText(),
                btn -> {
                    if (AutoWindowSize.canAutoMaximized()) {
                        AutoWindowSize.toggleAutoMaximizedPref();
                    }
                    btn.setMessage(getAutoMaxButtonText());
                }
        ).bounds(centerX - BUTTON_WIDTH / 2, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                .tooltip(Tooltip.create(Component.translatable("gui.autowindowsize.automax.tooltip"))).build();
        this.addRenderableWidget(this.autoMaxButton);

        y += rowStep;
        this.centerButton = Button.builder(
                Component.translatable("gui.autowindowsize.center.button"),
                btn -> {
                    if (AutoWindowSize.canCenter()) {
                        AutoWindowSize.centerWindow();
                    }
                }
        ).bounds(centerX - BUTTON_WIDTH / 2, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                .tooltip(Tooltip.create(Component.translatable("gui.autowindowsize.center.tooltip"))).build();
        this.addRenderableWidget(this.centerButton);

        // 信息区紧跟按钮列，不在 render 里写死坐标
        this.infoY = y + BUTTON_HEIGHT + 8;

        // 完成按钮钉在底部，和按钮列脱钩，永远不会重叠
        this.addRenderableWidget(Button.builder(
                Component.translatable("gui.done"),
                btn -> this.minecraft.setScreen(this.parent)
        ).bounds(centerX - BUTTON_WIDTH / 2, this.height - 30, BUTTON_WIDTH, BUTTON_HEIGHT).build());
    }

    private Component getLockButtonText() {
        if (AutoWindowSize.isLockDisabled()) {
            return Component.translatable("gui.autowindowsize.lock.button.disabled");
        }
        if (AutoWindowSize.isFullscreenTempDisabled()) {
            return Component.translatable("gui.autowindowsize.lock.button.fullscreen");
        }
        if (AutoWindowSize.isLockEnabled()) {
            return Component.translatable("gui.autowindowsize.lock.button.on");
        }
        return Component.translatable("gui.autowindowsize.lock.button.off");
    }

    private Component getFixedButtonText() {
        if (AutoWindowSize.isFullscreenTempDisabled()) {
            return Component.translatable("gui.autowindowsize.fixed.button.fullscreen");
        }
        long hwnd = AutoWindowSize.getWindowHandle();
        if (GLFW.glfwGetWindowAttrib(hwnd, GLFW.GLFW_MAXIMIZED) == GLFW.GLFW_TRUE) {
            return Component.translatable("gui.autowindowsize.fixed.button.maximized");
        }
        if (AutoWindowSize.isFixedEnabled()) {
            return Component.translatable("gui.autowindowsize.fixed.button.on");
        }
        return Component.translatable("gui.autowindowsize.fixed.button.off");
    }

    /** "下次启动自动全屏"按钮文案：与自动最大化互斥。 */
    private Component getAutoFsButtonText() {
        if (!AutoWindowSize.canAutoFullscreen()) {
            return Component.translatable("gui.autowindowsize.autofs.button.mutual");
        }
        if (AutoWindowSize.isAutoFullscreenPref()) {
            return Component.translatable("gui.autowindowsize.autofs.button.on");
        }
        return Component.translatable("gui.autowindowsize.autofs.button.off");
    }

    /** "下次启动自动最大化"按钮文案：与自动全屏互斥。 */
    private Component getAutoMaxButtonText() {
        if (!AutoWindowSize.canAutoMaximized()) {
            return Component.translatable("gui.autowindowsize.automax.button.mutual");
        }
        if (AutoWindowSize.isAutoMaximizedPref()) {
            return Component.translatable("gui.autowindowsize.automax.button.on");
        }
        return Component.translatable("gui.autowindowsize.automax.button.off");
    }

    /**
     * 每帧更新"当前游戏窗口分辨率"。三种状态：
     *  - 最小化：不动，避免恢复后尺寸抖动把提示卡住；
     *  - 全屏：分辨率由系统决定、恒定，直接对齐；
     *  - 窗口模式：拖动时数字冻结、黄色提示亮；停止 0.5 秒后刷新数字并提示灭，
     *    之后 0.2 秒内的 settle 尾巴静默跟新、不再亮提示。
     */
    private void updateLiveDimensions() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() == null) {
            return;
        }
        long hwnd = AutoWindowSize.getWindowHandle();

        boolean minimized = GLFW.glfwGetWindowAttrib(hwnd, GLFW.GLFW_ICONIFIED) != GLFW.GLFW_FALSE;
        this.fullscreen = GLFW.glfwGetWindowMonitor(hwnd) != 0;

        if (minimized) {
            return;
        }

        int currentW = mc.getWindow().getScreenWidth();
        int currentH = mc.getWindow().getScreenHeight();

        if (this.fullscreen) {
            this.gameWidth = currentW;
            this.gameHeight = currentH;
            this.lastSeenWidth = currentW;
            this.lastSeenHeight = currentH;
            this.dragging = false;
            return;
        }

        // 窗口模式：拖动时数字冻结、黄色提示亮；停止 0.5 秒后刷新数字并提示灭；
        // 之后 0.2 秒内的 settle 尾巴（如 1439→1440）静默跟新、不再亮提示。
        long now = System.currentTimeMillis();
        boolean sizeChanged = currentW != this.lastSeenWidth || currentH != this.lastSeenHeight;

        if (sizeChanged) {
            this.lastSeenWidth = currentW;
            this.lastSeenHeight = currentH;
            boolean justSettled = this.settledAtMs > 0 && (now - this.settledAtMs) < SETTLE_GRACE_MS;
            if (justSettled) {
                // 刚刷新后的 settle 尾巴：静默跟上数字，不重新亮提示
                this.gameWidth = currentW;
                this.gameHeight = currentH;
            } else {
                this.dragStartMs = now;
                this.dragging = true;
                this.settledAtMs = 0;
            }
        }

        // 拖动停止 0.5 秒：刷新数字、提示灭，进入 settle 宽限期
        if (this.dragging && now - this.dragStartMs >= DRAG_DEBOUNCE_MS) {
            this.gameWidth = currentW;
            this.gameHeight = currentH;
            this.dragging = false;
            this.settledAtMs = now;
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        updateLiveDimensions();

        // 每帧实时同步按钮状态
        this.lockButton.active = AutoWindowSize.canLock();
        this.lockButton.setMessage(getLockButtonText());
        this.fixedButton.active = AutoWindowSize.canFixed();
        this.fixedButton.setMessage(getFixedButtonText());
        this.autoFsButton.active = AutoWindowSize.canAutoFullscreen();
        this.autoFsButton.setMessage(getAutoFsButtonText());
        this.autoMaxButton.active = AutoWindowSize.canAutoMaximized();
        this.autoMaxButton.setMessage(getAutoMaxButtonText());
        this.centerButton.active = AutoWindowSize.canCenter();

        this.renderBackground(guiGraphics);

        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);

        int centerX = this.width / 2;
        int info = this.infoY;

        // 屏幕分辨率
        String screenResText = Component.translatable("gui.autowindowsize.screen_resolution",
                screenWidth, screenHeight).getString();
        guiGraphics.drawCenteredString(this.font, screenResText, centerX, info, 0xFFFFFF);

        // 当前游戏窗口分辨率：全屏时单独标注，一眼看出当前处于全屏
        String windowResKey = this.fullscreen
                ? "gui.autowindowsize.window_resolution_fullscreen"
                : "gui.autowindowsize.window_resolution";
        String gameResText = Component.translatable(windowResKey, gameWidth, gameHeight).getString();
        int resColor = this.fullscreen ? 0xFFFFAA : 0x55FF55;
        guiGraphics.drawCenteredString(this.font, gameResText, centerX, info + 12, resColor);

        // 第三行：拖动中提示优先；否则显示禁用原因
        int thirdLineY = info + 24;
        if (this.dragging) {
            String updatingText = Component.translatable("gui.autowindowsize.refreshing").getString();
            guiGraphics.drawCenteredString(this.font, updatingText, centerX, thirdLineY, 0xFFAA00);
        } else if (AutoWindowSize.isLockDisabled()) {
            String disabledText = Component.translatable("gui.autowindowsize.disabled_reason").getString();
            guiGraphics.drawCenteredString(this.font, disabledText, centerX, thirdLineY, 0xFF5555);
        } else if (AutoWindowSize.isFullscreenTempDisabled()) {
            String fsText = Component.translatable("gui.autowindowsize.fullscreen_reason").getString();
            guiGraphics.drawCenteredString(this.font, fsText, centerX, thirdLineY, 0xFF5555);
        } else if (AutoWindowSize.getCenterDisabledReason() != null) {
            guiGraphics.drawCenteredString(this.font, AutoWindowSize.getCenterDisabledReason(), centerX, thirdLineY, 0xFFAA00);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
