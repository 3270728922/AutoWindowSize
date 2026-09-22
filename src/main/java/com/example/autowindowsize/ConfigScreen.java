package com.example.autowindowsize;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWVidMode;

import java.util.ArrayList;
import java.util.List;

public class ConfigScreen extends Screen {
    private final Screen parent;
    private static final int BUTTON_WIDTH = 310;
    private static final int BUTTON_HEIGHT = 20;
    private static final int ROW_H = 25;

    // 主功能按钮
    private Button lockButton;
    private Button fixedButton;
    private Button autoFsButton;
    private Button autoMaxButton;
    private Button rememberButton;
    private Button centerButton;
    // 比例 / 分辨率预设
    private Button cycleAspectButton;
    private Button[] presetButtons = new Button[12];
    private int[][] presetRes = new int[12][2];
    private int currentGroup = 0;
    private int currentPreset = -1;
    private EditBox customWidthField;
    private EditBox customHeightField;
    private Button customApplyButton;

    // 实时分辨率
    private int screenWidth, screenHeight;
    private int gameWidth, gameHeight;
    private int lastSeenWidth, lastSeenHeight;
    private boolean dragging = false;
    private boolean fullscreen = false;
    private boolean wasMaximized = false;
    private boolean wasFullscreen = false;
    // 标记当前这一轮尺寸变化是由"最大化/全屏/从中恢复"引起的，
    // 整个过程（含恢复后尺寸稳定的那次判定）都不应自动把比例切到自定义。
    private boolean windowStateTransition = false;
    private long lastDragTime = 0;

    private OptionList list;
    private boolean initialized = false;

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
                this.gameWidth = curW;
                this.gameHeight = curH;
            }
            this.lastSeenWidth = curW;
            this.lastSeenHeight = curH;
        }
        this.dragging = false;
        this.fullscreen = false;

        // ---- 创建主功能按钮（不在此 addWidget，由 Entry 管理渲染/点击）----
        this.lockButton = Button.builder(getLockButtonText(), btn -> {
            if (AutoWindowSize.canLock()) { AutoWindowSize.toggleLock(); }
            if (list != null) list.setupEntries();
        }).bounds(0, 0, BUTTON_WIDTH, BUTTON_HEIGHT).tooltip(Tooltip.create(Component.translatable("gui.autowindowsize.lock.tooltip"))).build();

        this.fixedButton = Button.builder(getFixedButtonText(), btn -> {
            if (AutoWindowSize.canFixed()) { AutoWindowSize.toggleFixed(); }
            syncButtonStates();
            if (list != null) list.setupEntries();
        }).bounds(0, 0, BUTTON_WIDTH, BUTTON_HEIGHT).tooltip(Tooltip.create(Component.translatable("gui.autowindowsize.fixed.tooltip"))).build();

        this.autoFsButton = Button.builder(getAutoFsButtonText(), btn -> {
            if (AutoWindowSize.canAutoFullscreen()) { AutoWindowSize.toggleAutoFullscreenPref(); }
            if (list != null) list.setupEntries();
        }).bounds(0, 0, BUTTON_WIDTH, BUTTON_HEIGHT).tooltip(Tooltip.create(Component.translatable("gui.autowindowsize.autofs.tooltip"))).build();

        this.autoMaxButton = Button.builder(getAutoMaxButtonText(), btn -> {
            if (AutoWindowSize.canAutoMaximized()) { AutoWindowSize.toggleAutoMaximizedPref(); }
            if (list != null) list.setupEntries();
        }).bounds(0, 0, BUTTON_WIDTH, BUTTON_HEIGHT).tooltip(Tooltip.create(Component.translatable("gui.autowindowsize.automax.tooltip"))).build();

        this.rememberButton = Button.builder(getRememberButtonText(), btn -> {
            AutoWindowSize.toggleRememberPosition();
            if (list != null) list.setupEntries();
        }).bounds(0, 0, BUTTON_WIDTH, BUTTON_HEIGHT).tooltip(Tooltip.create(Component.translatable("gui.autowindowsize.remember.tooltip"))).build();

        this.centerButton = Button.builder(Component.translatable("gui.autowindowsize.center.button"), btn -> {
            if (AutoWindowSize.canCenter()) { AutoWindowSize.centerWindow(); }
            if (list != null) list.setupEntries();
        }).bounds(0, 0, BUTTON_WIDTH, BUTTON_HEIGHT).tooltip(Tooltip.create(Component.translatable("gui.autowindowsize.center.tooltip"))).build();

        // 比例按钮：只在第一次打开时自动匹配，之后保留用户选择
        if (!initialized) {
            int[] matched = AutoWindowSize.findPreset(this.gameWidth, this.gameHeight);
            this.currentGroup = matched[0] < 0 ? 0 : matched[0];
            this.currentPreset = matched[1];
            this.initialized = true;
        }

        // 比例循环按钮：和主按钮同宽，显示"当前 → 下一个"
        this.cycleAspectButton = Button.builder(cycleAspectText(), btn -> {
            this.currentGroup = (this.currentGroup + 1) % AutoWindowSize.ASPECT_NAMES.length;
            if (this.currentGroup >= AutoWindowSize.PRESETS.length && customWidthField != null) {
                customWidthField.setValue(String.valueOf(gameWidth));
                customHeightField.setValue(String.valueOf(gameHeight));
            }
            btn.setMessage(cycleAspectText());
            rebuildPresetButtons();
            if (list != null) list.setupEntries();
        }).bounds(0, 0, BUTTON_WIDTH, BUTTON_HEIGHT).build();

        // 分辨率预设按钮
        for (int i = 0; i < this.presetButtons.length; i++) {
            final int idx = i;
            Button b = Button.builder(Component.empty(), btn -> {
                if (AutoWindowSize.canApplyPreset()) {
                    int[] p = AutoWindowSize.PRESETS[this.currentGroup][idx];
                    AutoWindowSize.applyResolutionPreset(p[0], p[1]);
                    this.gameWidth = p[0];
                    this.gameHeight = p[1];
                    this.currentPreset = idx;
                    rebuildPresetButtons();
                    if (list != null) list.setupEntries();
                }
            }).bounds(0, 0, 68, BUTTON_HEIGHT).tooltip(Tooltip.create(Component.translatable("gui.autowindowsize.preset.tooltip"))).build();
            this.presetButtons[i] = b;
        }
        rebuildPresetButtons();
        markActiveButtons();

        // 自定义分辨率控件（加到 Screen 而非列表，让 MC 管理焦点）
        customWidthField = new EditBox(font, centerX - 155, 0, 80, BUTTON_HEIGHT,
                Component.translatable("gui.autowindowsize.custom.width"));
        customWidthField.setMaxLength(5);
        customWidthField.setValue(String.valueOf(gameWidth));
        customHeightField = new EditBox(font, centerX - 65, 0, 80, BUTTON_HEIGHT,
                Component.translatable("gui.autowindowsize.custom.height"));
        customHeightField.setMaxLength(5);
        customHeightField.setValue(String.valueOf(gameHeight));
        customApplyButton = Button.builder(
                Component.translatable("gui.autowindowsize.custom.apply"),
                btn -> applyCustomResolution()
        ).bounds(centerX + 25, 0, 130, BUTTON_HEIGHT).build();
        // 输入框内容变化时实时校验，非法则禁用应用按钮并给出悬浮提示
        customWidthField.setResponder(s -> refreshCustomApplyState());
        customHeightField.setResponder(s -> refreshCustomApplyState());
        customWidthField.visible = false;
        customHeightField.visible = false;
        customApplyButton.visible = false;
        this.addWidget(customWidthField);
        this.addWidget(customHeightField);
        this.addWidget(customApplyButton);

        // ---- 创建列表 ----
        this.list = new OptionList(mc, this.width, this.height, 32, this.height - 32, ROW_H);
        this.addRenderableWidget(this.list);

        // 完成按钮（原版位置）
        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"),
                btn -> this.minecraft.setScreen(this.parent))
                .bounds(centerX - 100, this.height - 27, 200, 20).build());
    }

    // ============ 内部类：原版滚动列表 ============
    private class OptionList extends AbstractSelectionList<OptionList.Entry> {
        public OptionList(Minecraft mc, int width, int height, int y0, int y1, int itemHeight) {
            super(mc, width, height, y0, y1, itemHeight);
            setupEntries();
        }

        void setupEntries() {
            this.children().clear();
            addEntry(new ButtonEntry(lockButton));
            addEntry(new ButtonEntry(fixedButton));
            addEntry(new TwoButtonEntry(autoFsButton, autoMaxButton));
            addEntry(new ButtonEntry(rememberButton));
            addEntry(new ButtonEntry(centerButton));
            addEntry(new InfoEntry());
            addEntry(new SpacerEntry()); // InfoEntry 现为3行文字，需要额外条目高度容纳溢出
            addEntry(new ButtonEntry(cycleAspectButton));
            // 自定义模式：显示输入框；否则显示预设按钮
            if (currentGroup == AutoWindowSize.ASPECT_NAMES.length - 1) {
                addEntry(new CustomEntry());
                // 占位符补齐到3行高度
                for (int r = 1; r < 3; r++) addEntry(new SpacerEntry());
            } else {
                int resCount = AutoWindowSize.PRESETS[currentGroup].length;
                int rows = (resCount + 3) / 4;
                for (int r = 0; r < rows; r++) {
                    addEntry(new RowEntry(presetButtons, r * 4, false));
                }
                // 占位符补齐到3行高度，确保切换比例时列表内容高度不变、滚动位置不跳
                for (int r = rows; r < 3; r++) {
                    addEntry(new SpacerEntry());
                }
                customWidthField.visible = false;
                customHeightField.visible = false;
                customApplyButton.visible = false;
            }
        }

        @Override
        public int getRowWidth() {
            return 310;
        }

        @Override
        public int getRowLeft() {
            return this.width / 2 - 155;
        }

        @Override
        protected int getScrollbarPosition() {
            return this.getRowLeft() + this.getRowWidth() + 1;
        }

        @Override
        public void updateNarration(NarrationElementOutput out) {
        }

        // ---- Entry 基类 ----
        abstract class Entry extends AbstractSelectionList.Entry<Entry> {
            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                return false;
            }
            @Override
            public boolean mouseReleased(double mouseX, double mouseY, int button) {
                return false;
            }
        }

        // 空白占位条目：不渲染任何内容，用于固定列表内容高度，避免切换比例时滚动条超界
        class SpacerEntry extends Entry {
            @Override
            public void render(GuiGraphics g, int index, int top, int left, int width, int height,
                               int mouseX, int mouseY, boolean hovered, float partialTick) {
                // 纯占位，不渲染
            }
        }

        // 单个按钮条目
        class ButtonEntry extends Entry {
            private final Button btn;
            ButtonEntry(Button b) { this.btn = b; }
            @Override
            public void render(GuiGraphics g, int index, int top, int left, int width, int height,
                               int mouseX, int mouseY, boolean hovered, float partialTick) {
                btn.setX(left);
                btn.setY(top);
                btn.setWidth(BUTTON_WIDTH);
                btn.setHeight(BUTTON_HEIGHT);
                btn.setFocused(false);
                syncButtonStates();
                btn.render(g, mouseX, mouseY, partialTick);
            }
            @Override
            public boolean mouseClicked(double mx, double my, int button) {
                if (btn.active && btn.visible && btn.isMouseOver(mx, my)) {
                    btn.mouseClicked(mx, my, button);
                    return true;
                }
                return false;
            }
            @Override
            public boolean mouseReleased(double mx, double my, int button) {
                if (btn.visible) { btn.mouseReleased(mx, my, button); }
                return false;
            }
        }

        // 两个并排按钮（自动全屏 / 自动最大化），总宽 220 居中
        class TwoButtonEntry extends Entry {
            private final Button left, right;
            TwoButtonEntry(Button l, Button r) { this.left = l; this.right = r; }
            @Override
            public void render(GuiGraphics g, int index, int top, int leftX, int width, int height,
                               int mouseX, int mouseY, boolean hovered, float partialTick) {
                int gap = 10;
                int half = (width - gap) / 2;
                left.setX(leftX);
                left.setY(top);
                left.setWidth(half);
                left.setHeight(BUTTON_HEIGHT);
                left.setFocused(false);
                right.setX(leftX + half + gap);
                right.setY(top);
                right.setWidth(half);
                right.setHeight(BUTTON_HEIGHT);
                right.setFocused(false);
                syncButtonStates();
                left.render(g, mouseX, mouseY, partialTick);
                right.render(g, mouseX, mouseY, partialTick);
            }
            @Override
            public boolean mouseClicked(double mx, double my, int button) {
                boolean a = left.active && left.visible && left.isMouseOver(mx, my) && left.mouseClicked(mx, my, button);
                boolean b = right.active && right.visible && right.isMouseOver(mx, my) && right.mouseClicked(mx, my, button);
                return a || b;
            }
            @Override
            public boolean mouseReleased(double mx, double my, int button) {
                left.mouseReleased(mx, my, button);
                right.mouseReleased(mx, my, button);
                return false;
            }
        }

        // 一行最多 4 个按钮（分辨率预设行）
        class RowEntry extends Entry {
            private final Button[] btns;
            private final int startIdx;
            private final boolean isAspect;
            RowEntry(Button[] all, int startIdx, boolean isAspect) {
                this.btns = all;
                this.startIdx = startIdx;
                this.isAspect = isAspect;
            }
            @Override
            public void render(GuiGraphics g, int index, int top, int left, int width, int height,
                               int mouseX, int mouseY, boolean hovered, float partialTick) {
                if (isAspect) {
                    int aw = 80, ag = 10;
                    int cnt = btns.length;
                    int startX = ConfigScreen.this.width / 2 - (aw * cnt + ag * (cnt - 1)) / 2;
                    for (int i = 0; i < cnt; i++) {
                        btns[i].setX(startX + i * (aw + ag));
                        btns[i].setY(top);
                        btns[i].setWidth(aw);
                        btns[i].setHeight(BUTTON_HEIGHT);
                        btns[i].setFocused(false);
                        btns[i].visible = true;
                        btns[i].render(g, mouseX, mouseY, partialTick);
                    }
                } else {
                    int resCount = AutoWindowSize.PRESETS[currentGroup].length;
                    int end = Math.min(startIdx + 4, resCount);
                    int count = end - startIdx;
                    int rg = 10;
                    int rw = (width - rg * 3) / 4;
                    for (int i = startIdx; i < end; i++) {
                        int[] p = AutoWindowSize.PRESETS[currentGroup][i];
                        btns[i].setX(left + (i - startIdx) * (rw + rg));
                        btns[i].setY(top);
                        btns[i].setWidth(rw);
                        btns[i].setHeight(BUTTON_HEIGHT);
                        btns[i].setFocused(false);
                        btns[i].visible = true;
                        // 超过屏幕分辨率或已是当前分辨率时自动禁用（仍可看见但点不动）
                        boolean tooBig = p[0] > screenWidth || p[1] > screenHeight;
                        boolean alreadyMatch = p[0] == gameWidth && p[1] == gameHeight;
                        btns[i].active = AutoWindowSize.canApplyPreset() && !AutoWindowSize.isFixedEnabled() && !tooBig && !alreadyMatch;
                        btns[i].render(g, mouseX, mouseY, partialTick);
                    }
                    for (int i = end; i < btns.length; i++) {
                        btns[i].visible = false;
                    }
                }
            }
            @Override
            public boolean mouseClicked(double mx, double my, int button) {
                int end;
                if (isAspect) {
                    end = btns.length;
                } else {
                    int resCount = AutoWindowSize.PRESETS[currentGroup].length;
                    end = Math.min(startIdx + 4, resCount);
                }
                for (int i = startIdx; i < end; i++) {
                    Button b = btns[i];
                    if (b.visible && b.active && b.isMouseOver(mx, my)) {
                        b.mouseClicked(mx, my, button);
                        return true;
                    }
                }
                return false;
            }
        }

        // 自定义分辨率输入行（控件已加到 Screen，这里只定位）
        class CustomEntry extends Entry {
            @Override
            public void render(GuiGraphics g, int index, int top, int left, int width, int height,
                               int mouseX, int mouseY, boolean hovered, float partialTick) {
                customWidthField.setX(left + 2);
                customWidthField.setY(top);
                customHeightField.setX(left + 92);
                customHeightField.setY(top);
                customApplyButton.setX(left + 180);
                customApplyButton.setY(top);
                customWidthField.visible = true;
                customHeightField.visible = true;
                customApplyButton.visible = true;
                customWidthField.render(g, mouseX, mouseY, partialTick);
                customHeightField.render(g, mouseX, mouseY, partialTick);
                customApplyButton.render(g, mouseX, mouseY, partialTick);
                // 输入框下方常驻两行提示：第一行边界值，第二行输入要求
                // 输入不合法时整体变为黄色警告色
                boolean invalid = combineErrors(getCustomInvalidReasons()) != null;
                int hintColor = invalid ? 0xFFAA00 : 0xAAAAAA;
                g.drawCenteredString(font,
                        Component.translatable("gui.autowindowsize.custom_range",
                                Config.HARD_MIN_WIDTH, screenWidth,
                                Config.HARD_MIN_HEIGHT, screenHeight),
                        left + width / 2, top + 24, hintColor);
                g.drawCenteredString(font,
                        Component.translatable("gui.autowindowsize.custom_input_hint"),
                        left + width / 2, top + 38, hintColor);
            }
        }

        // 信息区（三行：屏幕分辨率+居中标记 / 窗口状态+配置文件状态 / 详细说明）
        class InfoEntry extends Entry {
            @Override
            public void render(GuiGraphics g, int index, int top, int left, int width, int height,
                               int mouseX, int mouseY, boolean hovered, float partialTick) {
                int cx = left + width / 2;
                // 第一行：屏幕分辨率（常驻）+ 窗口已居中（居中时才显示）
                Component line1 = Component.translatable("gui.autowindowsize.screen_resolution", screenWidth, screenHeight);
                if (AutoWindowSize.isWindowCentered()) {
                    line1 = line1.copy().append("  ").append(Component.translatable("gui.autowindowsize.centered_mark"));
                }
                g.drawCenteredString(font, line1, cx, top - 2, 0xFFFFFF);

                // 第二行：窗口状态 + 配置文件状态（常驻）
                String stateKey;
                int stateColor;
                if (dragging) {
                    stateKey = "gui.autowindowsize.ws_dragging";
                    stateColor = 0xFFAA00;
                } else if (AutoWindowSize.isFullscreenTempDisabled()) {
                    stateKey = "gui.autowindowsize.ws_fullscreen";
                    stateColor = 0xFFAA00;
                } else if (AutoWindowSize.isMaximized()) {
                    stateKey = "gui.autowindowsize.ws_maximized";
                    stateColor = 0xFFAA00;
                } else if (AutoWindowSize.isFixedEnabled()) {
                    stateKey = "gui.autowindowsize.ws_fixed";
                    stateColor = 0xFFAA00;
                } else {
                    stateKey = "gui.autowindowsize.ws_normal";
                    stateColor = 0xAAAAAA;
                }
                boolean configError = AutoWindowSize.isLockDisabled();
                Component line2 = Component.translatable("gui.autowindowsize.line2_window_label")
                        .copy().append(Component.translatable(stateKey).withStyle(style -> style.withColor(stateColor)))
                        .append("  ")
                        .append(Component.translatable("gui.autowindowsize.line2_config_label"))
                        .append(Component.translatable(configError ? "gui.autowindowsize.cfg_error" : "gui.autowindowsize.cfg_correct")
                                .withStyle(style -> style.withColor(configError ? 0xFF5555 : 0x55FF55)));
                g.drawCenteredString(font, line2, cx, top + 12, 0xFFFFFF);

                // 第三行：对当前状态的详细说明（配置错误优先级最高，其次按窗口状态分别显示）
                String detailKey;
                int detailColor;
                if (configError) {
                    detailKey = "gui.autowindowsize.detail_error";
                    detailColor = 0xFF5555;
                } else if (dragging) {
                    detailKey = "gui.autowindowsize.detail_dragging";
                    detailColor = 0xFFAA00;
                } else if (AutoWindowSize.isFullscreenTempDisabled()) {
                    detailKey = "gui.autowindowsize.detail_fullscreen";
                    detailColor = 0xFFAA00;
                } else if (AutoWindowSize.isMaximized()) {
                    detailKey = "gui.autowindowsize.detail_maximized";
                    detailColor = 0xFFAA00;
                } else if (AutoWindowSize.isFixedEnabled()) {
                    detailKey = "gui.autowindowsize.detail_fixed";
                    detailColor = 0xFFAA00;
                } else {
                    detailKey = "gui.autowindowsize.detail_normal";
                    detailColor = 0x55FF55;
                }
                g.drawCenteredString(font, Component.translatable(detailKey), cx, top + 26, detailColor);
            }
        }
    }

    private void syncButtonStates() {
        this.lockButton.active = AutoWindowSize.canLock();
        this.lockButton.setMessage(getLockButtonText());
        this.fixedButton.active = AutoWindowSize.canFixed();
        this.fixedButton.setMessage(getFixedButtonText());
        this.autoFsButton.active = AutoWindowSize.canAutoFullscreen();
        this.autoFsButton.setMessage(getAutoFsButtonText());
        this.autoMaxButton.active = AutoWindowSize.canAutoMaximized();
        this.autoMaxButton.setMessage(getAutoMaxButtonText());
        this.rememberButton.setMessage(getRememberButtonText());
        this.centerButton.active = AutoWindowSize.canCenter();
        boolean canPreset = AutoWindowSize.canApplyPreset() && !AutoWindowSize.isFixedEnabled();
        for (Button b : this.presetButtons) b.active = canPreset;
        this.cycleAspectButton.active = canPreset;
        if (customApplyButton != null) customApplyButton.active = canPreset;
        if (customWidthField != null) customWidthField.active = canPreset;
        if (customHeightField != null) customHeightField.active = canPreset;
        refreshCustomApplyState();
    }

    // ============ 按钮文案 ============
    private Component getLockButtonText() {
        if (AutoWindowSize.isLockDisabled()) return Component.translatable("gui.autowindowsize.lock.button.disabled");
        if (AutoWindowSize.isFullscreenTempDisabled()) return Component.translatable("gui.autowindowsize.lock.button.fullscreen");
        if (AutoWindowSize.isLockEnabled()) return Component.translatable("gui.autowindowsize.lock.button.on");
        return Component.translatable("gui.autowindowsize.lock.button.off");
    }

    private Component getFixedButtonText() {
        if (AutoWindowSize.isFullscreenTempDisabled()) return Component.translatable("gui.autowindowsize.fixed.button.fullscreen");
        long hwnd = AutoWindowSize.getWindowHandle();
        if (GLFW.glfwGetWindowAttrib(hwnd, GLFW.GLFW_MAXIMIZED) == GLFW.GLFW_TRUE)
            return Component.translatable("gui.autowindowsize.fixed.button.maximized");
        if (AutoWindowSize.isFixedEnabled()) return Component.translatable("gui.autowindowsize.fixed.button.on");
        return Component.translatable("gui.autowindowsize.fixed.button.off");
    }

    private Component getAutoFsButtonText() {
        if (!AutoWindowSize.canAutoFullscreen()) return Component.translatable("gui.autowindowsize.autofs.button.mutual");
        if (AutoWindowSize.isAutoFullscreenPref()) return Component.translatable("gui.autowindowsize.autofs.button.on");
        return Component.translatable("gui.autowindowsize.autofs.button.off");
    }

    private Component getAutoMaxButtonText() {
        if (!AutoWindowSize.canAutoMaximized()) return Component.translatable("gui.autowindowsize.automax.button.mutual");
        if (AutoWindowSize.isAutoMaximizedPref()) return Component.translatable("gui.autowindowsize.automax.button.on");
        return Component.translatable("gui.autowindowsize.automax.button.off");
    }

    private Component getRememberButtonText() {
        if (AutoWindowSize.isRememberPosition()) return Component.translatable("gui.autowindowsize.remember.button.on");
        return Component.translatable("gui.autowindowsize.remember.button.off");
    }

    private void rebuildPresetButtons() {
        if (this.currentGroup >= AutoWindowSize.PRESETS.length) {
            for (Button b : this.presetButtons) b.visible = false;
            this.currentPreset = -1;
            markActiveButtons();
            return;
        }
        int resCount = AutoWindowSize.PRESETS[this.currentGroup].length;
        for (int i = 0; i < this.presetButtons.length; i++) {
            Button b = this.presetButtons[i];
            if (i < resCount) {
                int[] p = AutoWindowSize.PRESETS[this.currentGroup][i];
                this.presetRes[i][0] = p[0];
                this.presetRes[i][1] = p[1];
                b.setMessage(Component.translatable("gui.autowindowsize.preset.button", p[0], p[1]));
                b.visible = true;
            } else {
                b.visible = false;
            }
        }
        this.currentPreset = -1;
        boolean canPreset = AutoWindowSize.canApplyPreset() && !AutoWindowSize.isFixedEnabled();
        for (int i = 0; i < this.presetButtons.length; i++) {
            if (i < resCount) this.presetButtons[i].active = canPreset;
        }
        markActiveButtons();
    }

    private Component cycleAspectText() {
        String cur = AutoWindowSize.ASPECT_NAMES[this.currentGroup];
        Component result = Component.translatable("gui.autowindowsize.aspect.current", cur);
        if (this.currentGroup < AutoWindowSize.PRESETS.length) {
            result = result.copy()
                    .append(Component.literal("   ["))
                    .append(Component.translatable("gui.autowindowsize.aspect.window_resolution"))
                    .append(Component.literal(gameWidth + "×" + gameHeight + "]"));
        }
        return result;
    }

    private void markActiveButtons() {
        this.cycleAspectButton.setMessage(cycleAspectText());
        if (this.currentGroup >= AutoWindowSize.PRESETS.length) return;
        int resCount = AutoWindowSize.PRESETS[this.currentGroup].length;
        for (int i = 0; i < resCount; i++) {
            int[] p = AutoWindowSize.PRESETS[this.currentGroup][i];
            Component msg = (i == this.currentPreset)
                    ? Component.literal("▶ " + p[0] + "×" + p[1])
                    : Component.translatable("gui.autowindowsize.preset.button", p[0], p[1]);
            this.presetButtons[i].setMessage(msg);
        }
    }

    // 收集自定义输入框的所有非法原因（空值/非整数为阻断型，遇到即返回；低于下限/高于上限可并存）
    private List<Component> getCustomInvalidReasons() {
        List<Component> errors = new ArrayList<>();
        if (customWidthField == null || customHeightField == null) return errors;
        // 只在自定义组做校验
        if (this.currentGroup >= AutoWindowSize.PRESETS.length) {
            String ws = customWidthField.getValue().trim();
            String hs = customHeightField.getValue().trim();
            if (ws.isEmpty() || hs.isEmpty()) {
                errors.add(Component.translatable("gui.autowindowsize.custom.invalid.empty"));
                return errors;
            }
            int w, h;
            try {
                w = Integer.parseInt(ws);
                h = Integer.parseInt(hs);
            } catch (NumberFormatException e) {
                errors.add(Component.translatable("gui.autowindowsize.custom.invalid.number"));
                return errors;
            }
            if (w < 856 || h < 482) {
                errors.add(Component.translatable("gui.autowindowsize.custom.invalid.min", 856, 482));
            }
            if (w > screenWidth || h > screenHeight) {
                errors.add(Component.translatable("gui.autowindowsize.custom.invalid.max", screenWidth, screenHeight));
            }
        }
        return errors;
    }

    // 把多条非法原因合并成一条（默认用各语言分隔符连接，适合上方单行提示）
    private Component combineErrors(List<Component> errors) {
        return combineErrors(errors, Component.translatable("gui.autowindowsize.custom.invalid.separator").getString());
    }

    // 用指定分隔符合并多条原因（传 "\n" 可用于按钮悬浮提示的多行显示）
    private Component combineErrors(List<Component> errors, String sep) {
        if (errors.isEmpty()) return null;
        Component result = errors.get(0);
        for (int i = 1; i < errors.size(); i++) {
            result = result.copy().append(Component.literal(sep)).append(errors.get(i));
        }
        return result;
    }

    // 根据输入合法性与全局可用状态，更新应用按钮的启用/禁用与悬浮提示
    private void refreshCustomApplyState() {
        if (customApplyButton == null || customWidthField == null || customHeightField == null) return;
        boolean canPreset = AutoWindowSize.canApplyPreset() && !AutoWindowSize.isFixedEnabled();
        List<Component> errors = getCustomInvalidReasons();
        Component multiLine = combineErrors(errors, "\n");
        customApplyButton.active = canPreset && errors.isEmpty();
        // Tooltip 内部用 Font.split 排版，会按 \n 自动断行，实现多条原因逐行显示
        customApplyButton.setTooltip(multiLine == null ? null : Tooltip.create(multiLine));
    }

    private void applyCustomResolution() {
        if (customWidthField == null || customHeightField == null) return;
        try {
            int w = Integer.parseInt(customWidthField.getValue().trim());
            int h = Integer.parseInt(customHeightField.getValue().trim());
            w = Math.max(856, Math.min(screenWidth, w));
            h = Math.max(482, Math.min(screenHeight, h));
            if (AutoWindowSize.canApplyPreset()) {
                AutoWindowSize.applyResolutionPreset(w, h);
                gameWidth = w;
                gameHeight = h;
                customWidthField.setValue(String.valueOf(w));
                customHeightField.setValue(String.valueOf(h));
                markActiveButtons();
            }
        } catch (NumberFormatException ignored) {}
    }

    private void updateLiveDimensions() {
        if (this.minecraft.getWindow() == null) return;
        long hwnd = AutoWindowSize.getWindowHandle();
        boolean minimized = GLFW.glfwGetWindowAttrib(hwnd, GLFW.GLFW_ICONIFIED) != GLFW.GLFW_FALSE;
        boolean maximized = GLFW.glfwGetWindowAttrib(hwnd, GLFW.GLFW_MAXIMIZED) != GLFW.GLFW_FALSE;
        this.fullscreen = GLFW.glfwGetWindowMonitor(hwnd) != 0;
        boolean justRestored = wasMaximized && !maximized && !this.fullscreen;
        boolean justExitedFullscreen = wasFullscreen && !this.fullscreen;
        wasMaximized = maximized && !this.fullscreen;
        wasFullscreen = this.fullscreen;

        // 只要当前处于最大化/全屏，或本帧刚从中恢复，就把这一轮尺寸变化标记为
        // "窗口状态变化引起"。标志会一直保留到尺寸稳定、完成那次判定后才清除，
        // 因此能覆盖"恢复后尺寸才慢慢变回"的整个过程（justRestored 只在一帧内为 true）。
        if (maximized || this.fullscreen || justRestored || justExitedFullscreen) {
            this.windowStateTransition = true;
        }

        // 实时检测屏幕分辨率（防止玩家在系统设置里改了屏幕分辨率）
        long monitor = AutoWindowSize.getCurrentMonitorStatic(hwnd);
        GLFWVidMode mode = GLFW.glfwGetVideoMode(monitor);
        if (mode != null) {
            this.screenWidth = mode.width();
            this.screenHeight = mode.height();
        }

        if (minimized) return;

        int curW = this.minecraft.getWindow().getScreenWidth();
        int curH = this.minecraft.getWindow().getScreenHeight();
        if (curW != this.lastSeenWidth || curH != this.lastSeenHeight) {
            this.dragging = true;
            this.lastDragTime = System.currentTimeMillis();
        } else if (this.dragging && System.currentTimeMillis() - this.lastDragTime > 200) {
            this.dragging = false;
            this.gameWidth = curW;
            this.gameHeight = curH;
            // 自定义组内拖动窗口后，同步更新输入框为当前窗口大小；
            // 输入框正在被编辑（获得焦点）时不覆盖，避免打断用户输入。
            if (this.currentGroup >= AutoWindowSize.PRESETS.length && customWidthField != null) {
                if (!customWidthField.isFocused() && !customHeightField.isFocused()) {
                    customWidthField.setValue(String.valueOf(curW));
                    customHeightField.setValue(String.valueOf(curH));
                }
            }
            // 尺寸稳定后：只有"玩家自由拖动/非窗口状态变化"才据当前比例组判定是否切自定义；
            // 最大化、全屏及从中恢复引起的尺寸变化整轮都保持用户选中的比例分类不变。
            if (this.currentGroup < AutoWindowSize.PRESETS.length && !this.windowStateTransition) {
                boolean matched = false;
                for (int[] p : AutoWindowSize.PRESETS[this.currentGroup]) {
                    if (p[0] == curW && p[1] == curH) { matched = true; break; }
                }
                if (!matched) {
                    this.currentGroup = AutoWindowSize.ASPECT_NAMES.length - 1;
                    if (customWidthField != null) customWidthField.setValue(String.valueOf(curW));
                    if (customHeightField != null) customHeightField.setValue(String.valueOf(curH));
                    rebuildPresetButtons();
                    if (list != null) list.setupEntries();
                } else {
                    markActiveButtons();
                }
            }
            // 本轮窗口状态变化已处理完，解除抑制
            this.windowStateTransition = false;
        }
        this.lastSeenWidth = curW;
        this.lastSeenHeight = curH;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        updateLiveDimensions();
        this.renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
