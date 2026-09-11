package com.example.autowindowsize;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWVidMode;

public class ConfigScreen extends Screen {
    private final Screen parent;
    private static final int BUTTON_WIDTH = 200;
    private static final int BUTTON_HEIGHT = 20;
    private Button lockButton;
    private int screenWidth;
    private int screenHeight;
    private int gameWidth;
    private int gameHeight;
    private long lastSizeChangeTime = 0;
    private int lastCheckedWidth = -1;
    private int lastCheckedHeight = -1;

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
            this.gameWidth = mc.getWindow().getScreenWidth();
            this.gameHeight = mc.getWindow().getScreenHeight();
            this.lastCheckedWidth = this.gameWidth;
            this.lastCheckedHeight = this.gameHeight;
        }

        int lockY = this.height / 2 - 60;
        this.lockButton = Button.builder(
                getLockButtonText(),
                btn -> {
                    if (AutoWindowSize.canLock()) {
                        AutoWindowSize.toggleLock();
                        btn.setMessage(getLockButtonText());
                    }
                }
        ).bounds(centerX - BUTTON_WIDTH / 2, lockY, BUTTON_WIDTH, BUTTON_HEIGHT).build();
        this.addRenderableWidget(this.lockButton);

        int doneY = this.height / 2 + 30;
        this.addRenderableWidget(Button.builder(
                Component.translatable("gui.done"),
                btn -> this.minecraft.setScreen(this.parent)
        ).bounds(centerX - BUTTON_WIDTH / 2, doneY, BUTTON_WIDTH, BUTTON_HEIGHT).build());
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

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        long hwnd = AutoWindowSize.getWindowHandle();

        // 检测窗口是否被最小化
        int iconified = GLFW.glfwGetWindowAttrib(hwnd, GLFW.GLFW_ICONIFIED);
        boolean isIconified = (iconified != GLFW.GLFW_FALSE);

        // 最小化时不更新分辨率，避免恢复后提示常驻
        if (!isIconified && mc.getWindow() != null) {
            int currentW = mc.getWindow().getScreenWidth();
            int currentH = mc.getWindow().getScreenHeight();

            if (currentW != lastCheckedWidth || currentH != lastCheckedHeight) {
                lastCheckedWidth = currentW;
                lastCheckedHeight = currentH;
                lastSizeChangeTime = System.currentTimeMillis();
            }

            if (lastSizeChangeTime > 0
                    && System.currentTimeMillis() - lastSizeChangeTime >= 1000
                    && (currentW != gameWidth || currentH != gameHeight)) {
                gameWidth = currentW;
                gameHeight = currentH;
                lastSizeChangeTime = 0;
            }
        }

        // 每帧实时同步按钮状态
        this.lockButton.active = AutoWindowSize.canLock();
        this.lockButton.setMessage(getLockButtonText());

        this.renderBackground(guiGraphics);

        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);

        int centerX = this.width / 2;
        int baseY = this.height / 2 - 30;

        // 屏幕分辨率
        String screenResText = Component.translatable("gui.autowindowsize.screen_resolution",
                screenWidth, screenHeight).getString();
        guiGraphics.drawCenteredString(this.font, screenResText, centerX, baseY, 0xFFFFFF);

        // 当前游戏窗口分辨率
        String gameResText = Component.translatable("gui.autowindowsize.window_resolution",
                gameWidth, gameHeight).getString();
        guiGraphics.drawCenteredString(this.font, gameResText, centerX, baseY + 14, 0x55FF55);

        // 拖动中提示
        if (lastSizeChangeTime > 0 && !isIconified) {
            String updatingText = Component.translatable("gui.autowindowsize.refreshing").getString();
            guiGraphics.drawCenteredString(this.font, updatingText, centerX, baseY + 28, 0xFFAA00);
        }

        // 禁用原因
        if (AutoWindowSize.isLockDisabled()) {
            String disabledText = Component.translatable("gui.autowindowsize.disabled_reason").getString();
            guiGraphics.drawCenteredString(this.font, disabledText, centerX, this.height / 2 + 2, 0xFF5555);
        } else if (AutoWindowSize.isFullscreenTempDisabled()) {
            String fsText = Component.translatable("gui.autowindowsize.fullscreen_reason").getString();
            guiGraphics.drawCenteredString(this.font, fsText, centerX, this.height / 2 + 2, 0xFF5555);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
