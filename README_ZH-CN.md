# Auto Window Size

> Minecraft 1.20.1 / Forge 纯客户端模组 — 自动窗口大小、居中、最小尺寸锁定、固定窗口大小与启动自动全屏

> **⚠ 平台提示：本模组仅支持桌面版 Minecraft（Windows / macOS / Linux），不支持手机版（基岩版 / 手机端）。**

> **📮 关于源码：作者是 GitHub / Git 新手，早期仓库更新曾出现过操作失误导致文件混乱。如果你下载到的源码与实际发布的 jar 对不上，请发邮件至 3270728922@qq.com 获取正确源码。**

[English](README.md)

---

## 这是什么

在整合包里把各模组 UI 一点点调好后，玩家换个分辨率启动，窗口一变、布局全乱。Auto Window Size 把"游戏窗口大小"从偶然变成可控的选择：启动后自动按指定分辨率把窗口设好并居中，还能锁定最小尺寸、彻底固定窗口大小、一键居中、下次启动自动全屏，几乎不碰游戏内逻辑，不和别的模组冲突。

- **启动自动设窗口**：启动后延迟可配置（默认1.5秒），把窗口设为配置分辨率（默认 1280×720）并居中，不拉伸加载界面。
- **分辨率预设**：按宽高比（16:9 / 16:10 / 4:3 / 5:4 / 21:9）分组，每种12个常用分辨率一键切换，也支持自定义分辨率输入；超过屏幕或匹配当前大小的预设自动禁用。
- **最小尺寸锁定**：窗口只能放大、不能缩小到配置值以下，保护调好的 UI。
- **固定窗口大小**：把当前窗口大小完全锁死，最大化按钮在系统层禁用，从根上杜绝"假最大化"。
- **居中窗口**：一键把窗口在它所在的显示器居中。
- **启动自动全屏 / 自动最大化**：玩家可在设置里持久勾选"下次启动自动全屏"或"自动最大化"（互斥二选一）；整合包作者也可在配置里设一次性引导，让玩家首次启动自动全屏或最大化一次。
- **窗口位置记忆**：可选择退出时保存窗口位置与大小，下次启动恢复（而非强制居中）；与自动全屏/最大化兼容。
- **辅助功能设置原生入口**：在「选项 → 辅助功能设置」里插一个「窗口设置」按钮（从视频设置迁移，兼容 Embeddium 等替换视频设置界面的模组）。
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

- **升级本模组时请手动删除旧配置文件夹**：`.minecraft/config/AutoWindowSize/`（如果是从 1.0.9 之前版本升级，还需删除旧的 `.minecraft/config/autowindowsize-client.toml`）。由于部分原因，新版对配置项和文件布局做了调整，不删旧文件的话 Forge 不会重写、也可能缺少新增项；删除后启动游戏会自动生成全新的配置文件。本模组为纯客户端模组，仅支持桌面版 Minecraft，不支持手机版。

## Wiki

- **中文 Wiki**：[WIKI_ZH-CN.md](WIKI_ZH-CN.md)
- **English Wiki**：[WIKI.md](WIKI.md)

## 反馈

- **问题反馈 / 提 Bug**：https://github.com/3270728922/AutoWindowSize/issues
- **作者邮箱**：3270728922@qq.com

---

## 最新版本：v1.0.9

- **分辨率预设扩充**：5种比例（16:9 / 16:10 / 4:3 / 5:4 / 21:9）每种12个预设，新增自定义分辨率输入（带实时校验）；超过屏幕或匹配当前大小的预设自动禁用。
- **启动延迟可配置**：配置文件新增 `startupDelay`（0.5~10.0秒，默认1.5秒）。
- **窗口位置记忆**：配置文件新增 `rememberPosition`，开启后退出时保存窗口位置与大小，下次启动恢复（而非强制居中）；与自动全屏/最大化兼容。
- **配置文件统一目录**：所有配置文件迁移到 `config/AutoWindowSize/` 目录下（config.toml + window.json）。
- **设置入口迁移**：从视频设置迁移到**辅助功能设置**（兼容 Embeddium 等完全替换视频设置界面的模组，避免按钮消失）。
- **设置界面重构**：信息区扩展为四行（屏幕分辨率 / 窗口状态+配置文件状态 / 状态详细说明 / 自定义输入边界提示），新增按钮悬停提示，修复滚动条和布局问题。
- **新增5个指令**：`/aws help`（帮助列表，置顶）、`/aws fullscreen`、`/aws maximize`、`/aws remember`、`/aws resolution`（支持比例+预设和自定义宽高两种格式）。
- **配置界面多语言**：通过配置界面模组打开时，配置名称和描述随游戏语言切换。
- **语言扩展到20种**（新增阿拉伯语、泰语、瑞典语、捷克语、印尼语）。
- 修复大量 bug：比例按钮在最大化/全屏切换后显示错误、预设点击后比例同步、自定义输入框实时更新、固定窗口大小未禁用预设、按钮高亮溢出、滚动条重叠等。

完整更新日志：[CHANGELOG_ZH-CN.md](CHANGELOG_ZH-CN.md) · [CHANGELOG.md](CHANGELOG.md)
