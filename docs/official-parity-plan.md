# 与官方 APK 逐模块对齐计划

目标：**先整体复现官方客户端的行为与信息架构**，再在其上保留本仓库已有的增强（滑动回复、每会话草稿、审批横幅等）。

对象：官方 `EkkoStudio.apk` 1.0.8（`com.ekkostudio.ai`，versionCode 165，uni-app `__UNI__1F41684`）。
我方：`com.laozhudu.ekkostudio`（fork 自 MIT 上游 `mrz2333/hermes-studio-mobile` v1.9.7）。

---

## 一、方法（可复现，不靠印象）

1. **官方模块从哪来**：官方是 uni-app 包，`pages/index` 承载整个应用，其余 9 个是独立页。它的样式表就是一份目录——每个功能区都有类前缀（`.session-*`、`.workspace-*`、`.mcp-*`…）。前缀存在而我方源码里没有对应物，就是一个可核对的缺口。
2. **脚本**：`scripts/official_module_inventory.py --apk EkkoStudio.apk`
   产出 `docs/reference/apk-1.0.8/module-inventory.{json,md}`：50 个模块、官方类数量、我方命中文件。
3. **接口层**：`scripts/api_coverage.py` 按 OpenAPI 模块统计（当前 **217/415 = 52.3%**），与 UI 模块互为交叉验证——UI 缺的模块通常在接口覆盖率上也是洼地。
4. **验收单位是模块，不是版本**：每完成一个模块，必须①CI 绿 ②有测试 ③矩阵状态前移 ④真机截图/操作记录其一。
5. **不抄资产**：官方 CSS/JS/文案/图形一律不进仓库；数值（尺寸、色值、事件名、路径）是事实，可以用。

当前脚本给出的官方侧事实：

- 页面：`about, background-settings, bootstrap, change-password, devices, index, location-picker, login, login-devices, session-share`
- 权限：`Barcode, Camera, Geolocation, OAuth, Push, Record, Share, Zip`
- 50 个模块分两类：

  **官方有、我方完全没有**（5 个，CI 盯着这个集合只许变小）：`network-mode, drawer, presets, inbox, location`
  **我方独有、官方 apk 里根本没有**（4 个）：`journey, kanban, webhooks, insights` —— 这四项谈不上"对齐官方"，只能按自家标准维护。

- 注意：脚本的 have/missing 是「源码里有没有提到」的粗判，**本文件矩阵里的 have/partial/missing 才是权威判定**。

---

## 二、模块矩阵

状态：**have** 已对齐 · **partial** 有但差距 · **missing** 无 · **cannot** 服务端不支持 · **ours** 官方没有、我方独有

### A. 外壳与设备

| 模块 | 官方 | 我方 | 状态 | 动作 |
| --- | --- | --- | --- | --- |
| 登录 | 服务器地址、账号密码、扫码、OAuth、忘记密码 | 地址+账号密码 | partial | 补「记住地址」多地址切换；扫码单列到 P1；OAuth/忘记密码 → cannot |
| 已保存设备 | 列表、连通性、重命名、切换、删除 | **已完成 P1**：新增「当前设备」徽标（按 active URL 判定） | have | — |
| 首次引导 bootstrap | 连接中/失败重试/权限引导 | **已完成 P1**：4 页，末页为后台存活（通知/电池优化/自启动 + 打开系统设置） | have | — |
| 关于 | 80dp 标志 + 24sp 名称 + 13sp 版本 + 行卡片（官网/GitHub/评分/检查更新） | **已完成 P1**：同版式，内容为自身/Studio/Hermes 版本 + 源码 + 许可，并声明非官方 | have | — |
| 网络模式 | 局域网 / P2P / 云端 三态 | **已完成 P1**：线路选择器，直连可用、云端禁用并说明「自建发不出配对令牌」 | have | P2P 一并写进不可用说明 |

### B. 会话（应用主体）

| 模块 | 官方 | 我方 | 状态 | 动作 |
| --- | --- | --- | --- | --- |
| 抽屉 | 会话抽屉 + 账户菜单 + 模型切换 | 底部三 Tab | partial | 抽屉式账户菜单（模型/主题/退出集中） |
| 会话列表 | 列表、分类、文件夹、置顶、未读点、预览、搜索 | 列表已按官方外观对齐 | partial | 分类/文件夹 UI 完整化；未读点与预览缺字段（服务端不给）→ 说明性替代 |
| 新建会话 | 新建、分类选择、初始工作区 | 直接新建 | partial | 补分类与初始工作区选择 |
| 输入区 | 附件、模型、推理强度、工作区、语音、发送 | 已有 | have | 对齐官方工具条顺序与折叠行为 |
| 上下文计量 | 9sp 标签 + 34×3 环 | 已对齐 + 点开用量 | have | — |
| 消息 | 单底色气泡、气泡外时间、作者行 | 0.17.0 已对齐 | have | — |
| Markdown | 正文、图片、链接卡、代码、表格 | 正文/代码/表格 | partial | 图片点开全屏、链接预览卡 |
| 工具卡片 | 26dp 芯片、30dp 折叠头、9sp 耗时 | 已对齐 | have | — |
| 文件预览 | 全屏预览、下载、失败重试 | 卡片 + 工作区预览 | partial | 全屏预览组件 |
| 语音 | 录制、连续语音模式、STT provider、朗读 | 录制 + 朗读 | partial | 语音模式与 STT 选择 |
| 用量 | 环形缓存图、分账号、分 agent、成本 | 统计 + 单会话用量 | partial | 缓存环形 + 分账号 |
| 会话分享 | 单聊/群聊分享码 | — | cannot | `share_app_device_required`，自建发不出 app_access 令牌 |

### C. 群聊

| 模块 | 官方 | 我方 | 状态 | 动作 |
| --- | --- | --- | --- | --- |
| 群聊 | 房间、成员、群智能体、头像编辑、入群邀请 | 房间列表 + 消息 | partial | 成员管理、群智能体配置 |
| 智能体预设 | 预设卡片、按 profile 保存 | — | **missing** | `gc_agent_presets` 对应接口 |

### D. 工作区 / 终端 / 工作流

| 模块 | 官方 | 我方 | 状态 | 动作 |
| --- | --- | --- | --- | --- |
| 工作区 | 浏览器、创建、收藏、扩散目录 | 已对齐 | have | — |
| 终端 | 会话、cwd、断开重连 | 已对齐 | have | — |
| 工作流 | 列表、运行、运行历史 | 列表 + 运行 | partial | 运行历史与输入表单 |
| 编排画布 | 节点、连线、条件、位置 | — | **missing** | 编辑是重活，放最后；先只读视图 |

### E. 智能体与技能

| 模块 | 官方 | 我方 | 状态 | 动作 |
| --- | --- | --- | --- | --- |
| 智能体 | 状态徽标、运行态、重启 | 已有 | have | — |
| 技能库 | 分类、详情、编辑、删除、归档 | 已有 | have | 补编辑/归档按钮 |
| 技能包 bundles | 创建、成员勾选 | 已有 | have | — |
| MCP | 服务卡片、连接态、工具清单 | 已有（含工具清单） | have | — |
| 编码代理 | 配置卡、策略、安装、检查更新 | 策略与安装 | partial | 配置卡（模型/工作区/审批策略） |
| 辅助模型 | 委派模型、推理强度 | 只读 | partial | 可编辑 |
| MoA | 聚合器、成员、推理 | 仅接口提及 | **missing** | 判定是否值得（个人使用频率低）→ 放 P6 末 |
| 宠物 | 宠物、吉祥物 | 已有 | have | — |
| 旅程 | **官方无此界面** | 已有 | ours | 非对齐项，保持现状 |

### F. 自动化

| 模块 | 官方 | 我方 | 状态 | 动作 |
| --- | --- | --- | --- | --- |
| 定时任务 | 卡片、表单、历史 | 已有 | have | — |
| 看板 | **官方无此界面** | 已有（列表式） | ours | 非对齐项；按自用需求做列视图 |
| Webhook | **官方无此界面** | 已有 | ours | 非对齐项，保持现状 |
| 渠道 | 微信/其他渠道开关、二维码 | 已有 | have | — |

### G. 模型

| 模块 | 官方 | 我方 | 状态 | 动作 |
| --- | --- | --- | --- | --- |
| 模型面板 | 目录、默认模型、上下文长度、API 协议 | 已有 | have | — |
| Provider | 列表、OAuth 授权、模型刷新 | 列表 + 刷新 | partial | OAuth 流程按 provider 逐个做（anthropic/codex/copilot/nous/xai 各有 start/poll） |
| API 地址/Key | 地址选择、Key 管理 | 0.16.0 已有 | have | — |
| Hermes 配置 | 分组表单 | `SettingsGroup` 已有 | have | — |

### H. 可观测

| 模块 | 官方 | 我方 | 状态 | 动作 |
| --- | --- | --- | --- | --- |
| 性能 | 面板、仪表、自动刷新 | 已有 | have | — |
| 日志 | 列表、详情抽屉、复制 | 已有 | have | — |
| 健康 | 主动检查、按钮 | 部分 | partial | 补主动检查 |
| 洞察 | **官方无此界面** | 已有 | ours | 非对齐项，保持现状 |

### I. 外观与通知

| 模块 | 官方 | 我方 | 状态 | 动作 |
| --- | --- | --- | --- | --- |
| 主题 | 字号、颜色、深浅、跟随设备 | 已拆分本机/服务端 | have | — |
| 聊天背景 | 背景图（本机） | 服务端背景 | partial | 本机背景图 |
| 通知收件箱 | 应用内通知列表 | — | **missing** | `app.notification` 已能收到，只缺落地页 |
| 位置发送 | 位置选择器、附近、发送 | — | **missing** | `Geolocation` 权限 + 地图选点（第三方 SDK 体积大，需决策） |
| 全局搜索 | 会话/消息/技能统一搜索 | 仅会话内搜索 | partial | 统一搜索页 |

统计：**have 24 / partial 15 / missing 4（含 1 个待 P9 决策） / cannot 1 / ours 4**。P1 完成后：About、bootstrap、网络模式、已保存设备 四项转 have。

---

## 三、阶段划分

每个阶段 = 一个可发版版本，验收标准相同：CI 绿、测试覆盖新增路径、矩阵状态前移、真机验证。

### P0 基线工具（v0.17.1，进行中）

- 模块清单脚本（已完成）
- 矩阵文档（本文件）
- **CI 棘轮**：把 `module-inventory.json` 纳入测试，missing 数量只许减；新增 `/api` 调用必须落在 OpenAPI 里（已有 `ApiPathAlignmentTest`）
- 完成定义：一条命令能列出所有差距，CI 拦住回退

### P1 外壳补全（v0.18.0）✅

范围：About 页、设备页连通性文案、引导页权限说明、网络模式两态。
理由：这些是「打开就能看到」的表面，成本低，先把门面做对。

已完成：About 页（官方版式、我方内容）、引导页第 4 页（后台存活 + 系统设置入口）、设备卡「当前设备」徽标、线路选择器文案点明 P2P 与令牌原因。`ShellParityTest` 10 条守住版式与文案。

### P2 会话清单（v0.19.0）

范围：抽屉式账户菜单、会话分类/文件夹完整 UI、新建会话的分类与工作区、全局搜索。
理由：会话列表是最高频界面；官方把分类/文件夹放在抽屉里，我们目前只有扁平列表。

### P3 会话细节（v0.20.0）

范围：Markdown 图片全屏与链接卡、文件全屏预览、语音模式与 STT 选择、用量缓存环形与分账号、消息操作菜单（复制/引用/朗读/删除）。

### P4 群聊（v0.21.0）

范围：成员管理、群智能体、头像编辑、智能体预设。
依据：群聊是接口洼地（6/63 已用），差距最大。

### P5 工作区/终端/工作流（v0.22.0）

范围：工作流运行历史与输入表单、工作流只读画布、终端会话列表。
画布编辑（edge/条件）留到 P7 之后，属于新技术面。

### P6 智能体与技能（v0.23.0）

范围：编码代理配置卡、辅助模型编辑、技能编辑/归档、MCP 服务详情、MoA（末位，可砍）。

### P7 自动化（v0.24.0）

范围：定时任务表单校验、看板列视图与拖拽、Webhook 事件流、渠道二维码。

### P8 模型与可观测（v0.25.0）

范围：Provider OAuth 逐个（anthropic/codex/copilot/nous/xai）、健康主动检查、日志详情抽屉、洞察细分。

### P9 外观/通知/位置 + 终验（v0.26.0）

范围：本机背景图、通知收件箱、位置发送（取决于地图 SDK 决策）、逐模块对照签字。

---

## 四、明确不做（附证据）

| 项 | 证据 |
| --- | --- |
| 云端账号 / 邮箱 / 三方登录 | `/api/app/auth/me`、`/api/app/connections/claim` 在自建服务器 404 |
| 会话分享 | `POST /sessions/{id}/shares` → 401 `share_app_device_required`，自建发不出 `app_access` 设备令牌 |
| P2P / 云端中继 | `App Relay` 接口需要云端配对，自建无中枢 |
| MCU / 桌面运行时交接 | 桌面端特性 |
| 官方图形资产、CSS、文案 | 仓库公开 = 分发，属版权范围 |

---

## 五、规矩（沿用并新增）

1. 一次只动一个模块；跨模块的公共改动（主题、度量）单独提交。
2. **禁止用文本区间整块改写 Kotlin**。已因此失误 5 次（删掉 ink 调色板、丢 `@Composable`、丢相邻函数）。改用：读全文件 → 小步 `patch` → 落盘后跑括号平衡检查。
3. 每个新界面必须有一个非截图型测试（契约测试或度量断言），否则等于没测。
4. 官方值优先于个人偏好；偏好差异写进 `behaviour-parity.md` 的「保留」段，而不是悄悄改回去。
5. 每次发版后更新矩阵状态，矩阵与代码不一致时以代码为准并立刻修正矩阵。

---

## 六、下一步

P0 只剩 CI 棘轮。之后按 P1 → P9 顺序推进；若你想先看某个模块（比如群聊或工作流），直接说，我把顺序调过来——阶段之间没有硬依赖，只有 P0 是所有阶段的前提。

---

<!-- parity:modules generated from module-inventory.json; the test asserts this list equals the inventory, so a new official module cannot be ignored -->
<!--
login, devices, bootstrap, about, network-mode, drawer, session, new-chat, composer, input-context, messages, markdown, tool, file-preview, voice, usage, session-share, group, presets, workspace, terminal, workflow, edge, agent, skills, skill-detail, bundles, mcp, coding, aux, moa, pets, journey, jobs, kanban, webhooks, channels, model, provider, api, hermes, performance, logs, health, insights, theme, background, inbox, location, search
-->
