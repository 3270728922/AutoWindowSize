package com.example.autowindowsize;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWVidMode;

/**
 * Auto Window Size 配置界面。
 * 入口：视频设置界面的"窗口设置"按钮（原生插入可滚动列表）。
 * 以后新增配置项都可以加在这里。
 */
public class ConfigScreen extends Screen {

    private final Screen parent;
    private static final int BUTTON_WIDTH = 200;
    private static final int BUTTON_HEIGHT = 20;

    private Button lockButton;
    private int screenWidth;
    private int screenHeight;
    private int gameWidth;
    private int gameHeight;

    // 分辨率实时更新：拖动停止 1 秒后刷新显示，避免拖动时频繁重绘卡顿
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

        // 获取当前主显示器分辨率
        long monitor = GLFW.glfwGetPrimaryMonitor();
        GLFWVidMode mode = GLFW.glfwGetVideoMode(monitor);
        if (mode != null) {
            this.screenWidth = mode.width();
            this.screenHeight = mode.height();
        }

        // 获取当前游戏窗口分辨率（framebuffer 像素尺寸）
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() != null) {
            this.gameWidth = mc.getWindow().getScreenWidth();
            this.gameHeight = mc.getWindow().getScreenHeight();
            this.lastCheckedWidth = this.gameWidth;
            this.lastCheckedHeight = this.gameHeight;
        }

        // 锁定切换按钮
        int lockY = this.height / 2 - 60;
        this.lockButton = Button.builder(
                getLockButtonText(),
                btn -> {
                    if (!AutoWindowSize.isLockDisabled()) {
                        AutoWindowSize.toggleLock();
                        btn.setMessage(getLockButtonText());
                    }
                }
        ).bounds(centerX - BUTTON_WIDTH / 2, lockY, BUTTON_WIDTH, BUTTON_HEIGHT).build();
        this.lockButton.active = !AutoWindowSize.isLockDisabled();
        this.addRenderableWidget(this.lockButton);

        // 完成按钮
        int doneY = this.height / 2 + 30;
        this.addRenderableWidget(Button.builder(
                Component.translatable("gui.done"),
                btn -> this.minecraft.setScreen(this.parent)
        ).bounds(centerX - BUTTON_WIDTH / 2, doneY, BUTTON_WIDTH, BUTTON_HEIGHT).build());
    }

    /**
     * 根据当前锁定状态返回按钮文字。
     */
    private Component getLockButtonText() {
        if (AutoWindowSize.isLockDisabled()) {
            return Component.translatable("gui.autowindowsize.lock.button.disabled");
        }
        if (AutoWindowSize.isLockEnabled()) {
            return Component.translatable("gui.autowindowsize.lock.button.on");
        }
        return Component.translatable("gui.autowindowsize.lock.button.off");
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // ===== 分辨率实时更新：检测窗口尺寸变化，拖动停止 1 秒后刷新 =====
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() != null) {
            int currentW = mc.getWindow().getScreenWidth();
            int currentH = mc.getWindow().getScreenHeight();

            // 尺寸发生变化：记录最后变化时间
            if (currentW != lastCheckedWidth || currentH != lastCheckedHeight) {
                lastCheckedWidth = currentW;
                lastCheckedHeight = currentH;
                lastSizeChangeTime = System.currentTimeMillis();
            }

            // 距离最后一次变化已满 1 秒，且尺寸确实与显示值不同 → 更新显示
            if (lastSizeChangeTime > 0
                    && System.currentTimeMillis() - lastSizeChangeTime >= 1000
                    && (currentW != gameWidth || currentH != gameHeight)) {
                gameWidth = currentW;
                gameHeight = currentH;
                lastSizeChangeTime = 0;
            }
        }

        this.renderBackground(guiGraphics);

        // 标题
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);

        int centerX = this.width / 2;
        int baseY = this.height / 2 - 30;

        // 屏幕分辨率（白色，更醒目）
        String screenResText = "屏幕分辨率：" + screenWidth + " × " + screenHeight;
        guiGraphics.drawCenteredString(this.font, screenResText, centerX, baseY, 0xFFFFFF);

        // 游戏分辨率（浅绿色，与屏幕分辨率区分）
        String gameResText = "游戏分辨率：" + gameWidth + " × " + gameHeight;
        guiGraphics.drawCenteredString(this.font, gameResText, centerX, baseY + 14, 0x55FF55);

        // 如果正在等待更新（拖动中），显示提示
        if (lastSizeChangeTime > 0) {
            String updatingText = "窗口尺寸变化中，松开后 1 秒自动刷新…";
            guiGraphics.drawCenteredString(this.font, updatingText, centerX, baseY + 28, 0xFFAA00);
        }

        // 锁定被禁用时的警告（红色）
        if (AutoWindowSize.isLockDisabled()) {
            String disabledText = "锁定已禁用：配置分辨率高于屏幕分辨率";
            guiGraphics.drawCenteredString(this.font, disabledText, centerX, this.height / 2 + 2, 0xFF5555);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
