# Auto Window Size

> Minecraft 1.20.1 / Forge 纯客户端模组 — 自动窗口大小、居中、最小尺寸锁定、固定窗口大小与启动自动全屏

> **⚠ 平台提示：本模组仅支持桌面版 Minecraft（Windows / macOS / Linux），不支持手机版（基岩版 / 手机端）。**

> **📮 关于源码：作者是 GitHub / Git 新手，早期仓库更新曾出现过操作失误导致文件混乱。如果你下载到的源码与实际发布的 jar 对不上，请发邮件至 3270728922@qq.com 获取正确源码。**

[English](README.md)

---

## 这是什么

在整合包里把各模组 UI 一点点调好后，玩家换个分辨率启动，窗口一变、布局全乱。Auto Window Size 把"游戏窗口大小"从偶然变成可控的选择：启动后自动按指定分辨率把窗口设好并居中，还能锁定最小尺寸、彻底固定窗口大小、一键居中、下次启动自动全屏，几乎不碰游戏内逻辑，不和别的模组冲突。

- **启动自动设窗口**：启动后延迟约 1.5 秒，把窗口设为配置分辨率（默认 1280×720）并居中，不拉伸加载界面。
- **分辨率预设**：按宽高比（16:9 / 16:10 / 4:3 / 5:4 / 21:9）分组的常用分辨率一键切换，也支持自定义分辨率输入；超过屏幕的预设自动禁用。
- **最小尺寸锁定**：窗口只能放大、不能缩小到配置值以下，保护调好的 UI。
- **固定窗口大小**：把当前窗口大小完全锁死，最大化按钮在系统层禁用，从根上杜绝"假最大化"。
- **居中窗口**：一键把窗口在它所在的显示器居中。
- **启动自动全屏 / 自动最大化**：玩家可在设置里持久勾选"下次启动自动全屏"或"自动最大化"（互斥二选一）；整合包作者也可在配置里设一次性引导，让玩家首次启动自动全屏或最大化一次。
- **视频设置原生入口**：在「选项 → 视频设置」里插一个「窗口设置」按钮。
- **纯客户端**，不需要装在服务端；默认 1280×720 即装即用。

![视频设置中的窗口设置按钮](docs/zh-video-settings.png)
![窗口设置界面 — 分辨率预设](docs/zh-settings-presets.png)

更多细节、指令表、配置项、常见问题见 **Wiki**。

---

## 下载

> **建议优先从 GitHub Releases 下载**，其他平台（CurseForge / Modrinth / MC 百科 / MCBBS / BBSMC / 苦力怕论坛 / HIMCBBS）可能因审核或同步延迟，版本会比 GitHub 慢几天。

- **GitHub Releases（推荐，始终最新）**：https://github.com/3270728922/AutoWindowSize/releases
- **CurseForge**：https://www.curseforge.com/minecraft/mc-mods/auto-window-size
- **Modrinth**：https://modrinth.com/mod/autowindowsize
- **MC 百科**：https://www.mcmod.cn/class/30769.html
- **MCBBS**：https://www.mcbbs.co/thread-6322-1-1.html
- **BBSMC**：https://bbsmc.net/mod/autowindowsize
- **苦力怕论坛（KLBBS）**：https://klpbbs.com/thread-173769-1-1.html
- **HIMCBBS**：https://www.himcbbs.com/resources/1499/

## 注意事项

- **升级本模组时请手动删除旧配置文件**：`.minecraft/config/autowindowsize-client.toml`。由于部分原因，新版对配置注释和项做了调整，不删旧文件的话 Forge 不会重写注释、也可能缺少新增项；删除后启动游戏会自动生成全新的配置文件。本模组为纯客户端模组，仅支持桌面版 Minecraft，不支持手机版。

## Wiki

- **中文 Wiki**：[WIKI_ZH-CN.md](WIKI_ZH-CN.md)
- **English Wiki**：[WIKI.md](WIKI.md)

## 反馈

- **问题反馈 / 提 Bug**：https://github.com/3270728922/AutoWindowSize/issues
- **作者邮箱**：3270728922@qq.com

---

## 最新版本：v1.0.8

- 新增「分辨率预设」：窗口设置界面内置常用分辨率按钮，按 16:9 / 16:10 / 4:3 / 5:4 / 21:9 五种比例分组，点一下立即切换并居中。
- 新增「自定义分辨率」：比例切到最后一项"自定义"时，可手动输入宽和高，点应用立即切换并居中；超过屏幕分辨率的选项自动禁用。
- 比例按钮实时显示当前比例和窗口分辨率，点击循环切换；拖动窗口后若分辨率不匹配当前比例，自动切到自定义。
- 设置界面改用原版滚动列表（AbstractSelectionList），自带滚动条和上下渐隐，和原版视频设置界面观感一致。
- 实时检测屏幕分辨率变化，玩家在系统里改了屏幕分辨率后设置界面自动跟上。
- 新增繁体中文语言支持。

完整更新日志：[CHANGELOG_ZH-CN.md](CHANGELOG_ZH-CN.md) · [CHANGELOG.md](CHANGELOG.md)
