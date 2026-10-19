# 原版 HStudio App（v1.0.3）界面分析

> 本文档只描述**原版 APK 中真实存在的运行代码**。所有定位均为「相对路径 + 字节偏移 / 组件选择器 / 路由标识」，
> 可用 Python 在基准目录中复现。任何**未在原版中找到**的界面一律标为「未定位」，不使用网页版或其它版本推断填充。
> 全文不记录任何凭据、令牌或连接串；示例值一律 `[REDACTED]`。

---

## 0. 基准与证据可信度

| 项 | 值 |
| --- | --- |
| APK | `/root/Hermes任务文件/HStudioDirect/latest/HStudio-v1.0.3.apk` |
| manifest versionName / versionCode | `1.0.3` / `120` |
| 解包根目录 | `/root/Hermes任务文件/HStudioDirect/latest/decoded-v103/` |
| Web 资源根 | `<解包根>/assets/apps/__UNI__1F41684/www/` |
| 主运行包 | `app-service.js`，2,699,326 字节，SHA256 `14ada30c623108ac955dd91029c7e247e69d53038f448c9e9defe582d6ecff04` |
| 主样式 | `pages/index/index.css`，522,488 字节，SHA256 `de897a52a551e613639ff5fdcb6ae3919ceda3188ba68952dcaa804b8ca136ba` |
| 设备页样式 | `pages/devices/index.css`，24,577 字节，194 条规则 |
| 全局令牌 | `app.css`，52,938 字节 |

两个 SHA256 与基准提供值**逐字节一致**，故本文引用的行内偏移可复现。

**证据等级定义**

- **A 级**：在 v1.0.3 解包资源中定位到字节偏移，且该文本/规则属于目标组件 span 内。
- **B 级**：定位到字节偏移，但 span 归属由 `__scopeId` 边界推断（组件名与内容可能不符，会显式标注）。
- **C 级**：仅为字符串出现次数统计 + 邻域上下文抽样，未做逐条人工确认。
- **未定位**：以「零命中」或「命中全部为无关语义」判定不存在；已列出反例上下文。

**方法学限制（必须先声明）**

1. 组件边界由 `app-service.js` 中的 `[["__scopeId","data-v-XXXXXXXX"]]` 标记切分得到。uni-app 会把多个 SFC 的编译产物顺序拼接，
   故 span 是**近似边界**，跨组件字符串可能粘连。凡本文引用 span 内标签，均已抽检上下文语义一致。
2. **类名存在 ≠ 界面已展示。** 本文所有「已展示」结论均要求同时满足：CSS 规则存在 + 该类的模板字符串存在于同一 span 内。
3. 本仓库 Kotlin 移植对应的原版是 **v1.0.3**，不是任务早期给出的 `ref-v0.7.13`。依据：`--ink-*` 令牌族在 v0.7.13 中**零命中**，
   而 v1.0.3 `app.css` 中完整存在且数值与 `Theme.kt` 完全一致（见 §6）。
4. 单位：全量样式表中 `rpx` 命中 **0**，`rem` 仅在 `index.css`（68 次）与 `login.css`（13 次）出现，`px` 为主体
   （devices 332 / index 7185 / app 261 / login 834）。uni-app App 端 WebView 以设备宽度为视口，**1 CSS px ≈ 1 dp**，
   但存在 devicePixelRatio 与 viewport 缩放，**不做盲目 1:1 换算**，本文所有迁移建议都标注了取整理由。

---

## 1. 原版总体架构

原版是 uni-app（Vue 3）单入口 App，**只有 6 个页面路由**：

| 路由 | 出现次数 | 角色 |
| --- | --- | --- |
| `pages/devices/index` | 3 处字符串 + 10 次 `reLaunch` 目标 | **启动页**（设备/本地连接） |
| `pages/bootstrap/index` | 3 | 启动引导/初始化 |
| `pages/login/index` | 1 + 4 次 `reLaunch` | 登录（回退目标） |
| `pages/index/index` | 1 | **主 SPA**（几乎所有业务界面都在这里） |
| `pages/location-picker/index` | 1 | 位置选择器（`navigateTo`） |
| `pages/about/index` | 1 | 关于页（`navigateTo`） |

`navigateTo` 目标全集 = `{/pages/about/index, /pages/index/index, /pages/location-picker/index}`。
**不存在 tabBar**：`switchTab` 调用 **0 次**。

这意味着：历史会话、聊天、群聊、Files、工作流、定时、Skills/MCP、Profiles、Models、Memory、日志/用量/性能、主题
**全部是 `/pages/index/index` 内部的组件**，共用一个 522 KB 样式表。原版用**全屏浮层（`position:fixed` + `z-index`）+ 页面级视图**切换，
而不是路由跳转。这一点直接决定了 Android 端应使用「单 Activity + Compose 视图栈」而非多路由。

### 1.1 作用域 → 域映射（数据推导，非猜测）

`index.css` 共 **3,947 个最内层样式块**、**88 个不同 `data-v-` 作用域**（`app-service.js` 中 `__scopeId` 标记共 **91** 个）。
> 计数口径说明：`re.findall(r'\{[^{}]*\}')` 得 3,947 块；改用 `[^{}@]+\{` 得 4,125 个选择器位，去重后 3,719 个。
> 两种口径的差别来自 `@media` 内嵌块是否计入。本节及 JSON 索引统一采用前者。作用域数在本轮复核中由 87 修正为 **88**。

作用域身份由各 scope 内**独有类名前缀**推导，再与 `app-service.js` 中该 scope span 内的界面文案交叉验证。

| 域 | scope | app-service.js span（字节） | span 大小 |
| --- | --- | --- | --- |
| 消息列表/气泡 | `c0c550c3` | 1683233–1716532 | 33,299 |
| 输入区（含位置/日程/附件） | `8aca294f` | 1798801–1947844 | 149,043 |
| 输入框本体 | `744a5cd2` | 1331635–1344110 | 12,475 |
| 历史会话列表 | `b5cd62b3` | 1240591–1308999 | 68,408 |
| 群聊主体 | `e0c3cb1f` | 1953786–2116171 | 162,385 |
| 群成员浮层 | `760e0344` | 1947844–1953786 | 5,942 |
| 工作流运行 | `45f94f2e` | 2597225–2659243 | 62,018 |
| 工作流节点 | `19d17ade` | 2581543–2597225 | 15,682 |
| 工作流表单 | `8eec0edf` | 2522484–2528035 | 5,551 |
| 工作流连线/条件 | `4b83134d` | 2528035–2542142 | 14,107 |
| 定时计划（工作流侧） | `1c43be07` | 2542142–2555763 | 13,621 |
| 定时任务（Hermes jobs） | `d3614ca5` | 2370012–2385007 | 14,995 |
| 任务列表/状态 | `87ee7e53` | 2388731–2397605 | 8,874 |
| 运行历史 | `218409ea` | 2385007–2388731 | 3,724 |
| Ekko 记忆 | `3f8c1721` | 2260614–2268382 | 7,768 |
| Hermes 记忆分段 | `f89e2bdf` | 2406382–2411957 | 5,575 |
| MCP 服务器列表 | `b62dab03` | 2334125–2346209 | 12,084 |
| MCP 工具可见性 | `961fba34` | 2326254–2334125 | 7,871 |
| MCP 表单 | `2f3f27cd` | 2279052–2326254 | 47,202 |
| Skills 列表 | `3c019f49` | 2355683–2367250 | 11,567 |
| Skill 详情 | `f9d684ea` | 2346209–2355683 | 9,474 |
| Skills 用量 | `6bb0fc77` | 2424556–2435320 | 10,764 |
| Agent / Runtime 管理 | `e60e128b` | 2229496–2260614 | 31,118 |
| Profiles 列表 | `181a58e6` | 2443350–2453010 | 9,660 |
| Profile 表单 | `00cf131c` | 2435320–2439821 | 4,501 |
| Profile 配置文件 | `d15684f0` | 2439821–2443350 | 3,529 |
| Models 总览 | `fb39f5d3` | 2213172–2229496 | 16,324 |
| Model Provider 编辑 | `303c0407` | 2135957–2152283 | 16,326 |
| Provider 授权 | `f9857c3f` | 2127658–2135957 | 8,299 |
| 辅助/备用模型 | `98a96c0d` | 2158294–2172890 | 14,596 |
| 组合模型 MoA | `9e1d5a9f` | 2172890–2186178 | 13,288 |
| 语音 STT/TTS | `8d1c9efc` | 2186178–2213172 | 26,994 |
| 模型选择器 | `72375a2b` | 1722472–1728926 | 6,454 |
| 用量 | `1f1f16f0` | 2464031–2478565 | 14,534 |
| 性能监控 | `130adebd` | 2478565–2489404 | 10,839 |
| 日志列表 | `237c0272` | 2456552–2464031 | 7,479 |
| 日志详情 | `175a997a` | 2453010–2456552 | 3,542 |
| 手机主题 | `3d4864a4` | 2503183–2511224 | 8,041 |
| 取色器 | `f09227f2` | 2498661–2503183 | 4,522 |
| 主题切换按钮 | `b6963587` | 1138640–1139696 | 1,056 |
| 设置首页 | `5963587a` | 2511224–2522484 | 11,260 |
| 全局设置 | `d28cb492` | 2489404–2498661 | 9,257 |
| 输入设置 | `5c37be17` | 1328868–1331635 | 2,767 |
| Hermes 配置 | `da401c3d` | 2397605–2406382 | 8,777 |
| Hermes 设置分区 | `c18cdc0b` | 2411957–2414914 | 2,957 |
| **Files / 工作区** | `07bc1664` | 1778078–1798801 | 20,723 |
| 文件预览 | `ba39d9e1` | 1768726–1775928 | 7,202 |
| 本轮文件变更 diff | `879b120a` | 1680185–1683233 | 3,048 |
| 工作区目录选择器 | `e263dfb1` | 1204103–1216673 | 12,570 |
| 全局搜索 | `2736050c` | 1235746–1240591 | 4,845 |
| 新建对话 | `50ff9297` | 1216673–1235746 | 19,073 |
| Markdown 渲染 | `856f8e4d` | 1589506–1680185 | 90,679 |
| 工具调用/思考 | `fbdcf75c` | 1352665–1357076 | 4,411 |
| 后台子任务 | `dbfa15a2` | 1762745–1768726 | 5,981 |
| 任务计划 + z-paging | `9cc0eaa2` | 1357076–1486252 | 129,176 |
| 交互确认浮层 | `482b38f0` | 1347191–1352665 | 5,474 |
| 命令选择器 | `df5f754c` | 1344110–1347191 | 3,081 |
| 斜杠命令 | `805a1a4c` | 1761547–1762745 | 1,198 |
| 应用导航壳 | `f633209f` | 1775928–1778078 | 2,150 |
| 应用工作区壳 | `7faeb036` | 2659243–2674399 | 15,156 |
| 通用选择器 | `66c185f7` | 212553–219802 | 7,249 |
| 通用确认弹窗 | `348e588d` | 25829–29020 | 3,191 |
| 本地主题注入 | `91441b28` | 31054–32100 | 1,046 |
| i18n 词条表 | `e29ff977` | 219802–1138640 | **918,838** |

> **注意 `e29ff977`**：918 KB 的 span 不是界面，是 **11 种语言的词条字典**。凡在 §5 做「未定位」判定时，
> 若命中落在此区间内，一律视为**纯文案命中，不构成界面存在证据**。

---

## 2. 逐域分析

### 2.1 设备 / 本地连接 —— 已定位（A 级），启动页

- **定位**：路由 `pages/devices/index`（`reLaunch` 目标 10 次）；样式 `pages/devices/index.css`（24,577 B，194 规则）；
  运行组件在 `app-service.js` 中独立于主 SPA。
- **作用域**：`61a2f361`（170 规则）、`348e588d`（21）、`ac60d9fd`（15）、`91441b28`（7）。
- **入口与布局**：两栏纵向结构 ——
  `.devices-header-content`（offset 6526，`padding: calc(18px + var(--app-safe-area-top)) 18px 18px`）
  + `.devices-content`（offset 6567，`padding: 0 18px calc(28px + env(safe-area-inset-bottom))`）。
  两者共享 `width:100%; max-width:680px; margin:0 auto`。
  内容为 `.device-grid`（offset 13020，`grid-template-columns:repeat(2,minmax(0,1fr)); gap:12px`）+
  末尾 `.add-device-card`（offset 13148）。
- **关键交互与 API**：
  - 标题「我的设备」+ 副标题「管理你的 Ekko Studio」+ 账号菜单（`.account-trigger`）
  - 卡片状态：`--pressed`（`transform:scale(.985)`）、`--connecting`（`opacity:.72; pointer-events:none`）
  - 配对方式选择浮层：「扫码添加」/「手动添加」；手动表单字段：设备名称、Studio 账号、Studio 密码
  - 已连接后：修改名称、切换 App 线路（`.device-route-editor`）
  - API：`/api/app/auth/account`、`/api/app/auth/config`、`/api/app/auth/entitlement`、`/api/app/auth/refresh`、
    `/api/app/auth/logout`、`/api/app/auth/apple`、`/api/app/auth/google`、`/api/app/connections/claim`、
    `/api/app/profiles`、`/api/auth/app-login`、`/api/website/app/qr/approve`、`/api/website/app/qr/inspect`
- **云功能排除点**：`/api/app/auth/*`、`/api/app/entitlement`（账号权益、有效至）、`/api/website/app/qr/*`（扫码审批）
  均为 Ekko 云端账号体系。本地直连复刻应保留 **手动添加 + 局域网地址 + 本地探测**，排除云端登录、权益查询与二维码审批。
- **Android 对应**：`android/.../DevicesScreen.kt`（1,847 行，40 个 composable），
  已在 284–362 行实现 `screenWidthDp <= 520` 与 `>= 900 && >= 600` 横屏分支，与原版三条媒体查询对应。
- **明显差距**：见 §3 深度分析。
- **可信度**：A（样式规则、标签、API 路径均在同域 span 内，且路由独立）。

### 2.2 聊天 —— 已定位（A 级）

- **定位**：`app-service.js` span `c0c550c3` @ 1683233–1716532（33,299 B）；样式同 scope 见 `index.css`。
  输入区 `8aca294f` @ 1798801–1947844，输入框 `744a5cd2` @ 1331635–1344110。
- **入口与布局**：主 SPA 内的会话视图。头像行 `.message-author`（CSS @ 108650）+ 气泡 `.message-bubble`（@ 109671）+
  正文 `.msg-body`（@ 108447）。
  - `.msg-body[data-v-c0c550c3]{width:fit-content;max-width:min(88%,920px)}`
  - `.message-bubble[data-v-c0c550c3]{padding:10px 14px;background:var(--ink-bg-message);border-radius:10px}`
  - `.message.user .message-bubble[data-v-c0c550c3]{max-width:100%}` —— **仅约束宽度，无配色覆盖**
  - `.message-author[data-v-c0c550c3]{min-height:22px;margin:0 0 4px 2px;gap:4px}`
  - `.msg-avatar[data-v-c0c550c3]{width:22px;height:22px;border-radius:50%;background:var(--ink-bg-card-hover)}`
  - 代理徽标 `.agent-badge[data-v-e60e128b]`（CSS @ 302250）：`min-height:18px;padding:1px 6px;font-size:8px;line-height:14px`，
    `@media (max-width:640px)` 覆盖为 `16px / 0 5px / 7px / 13px`
- **关键交互**：空态「发送消息，开始协作」；思考态「正在思考 / 思考过程」（`fbdcf75c`）；
  工具调用（`工具 / 参数 / 结果 / 错误 / 已中断`）；后台子任务（`dbfa15a2`：「运行中 / 已完成 / 失败 / 已取消 / 已中断」）；
  本轮文件变更 diff（`879b120a`：「本轮文件变更 / N 个文件 / 变更内容较大，部分 diff 已截断」）；
  模型选择器（`72375a2b`）、斜杠命令（`805a1a4c`）、命令选择器（`df5f754c`）。
- **API 事件**：聊天组件 span 内 `/api/` 命中为 **0**（请求由父级 store 统一发出）；输入区 span 内的 API 见下：
  `/api/hermes/bundles`、`/api/studio/sessions/context-length`、`/api/hermes/model-context/${...}`。
- **云功能排除点**：无直接云依赖；但附件上传在 `856f8e4d` 中走 `/api/hermes/download`、`/api/studio/files/download`，
  属**本地 Studio 服务**而非 Ekko 云。
- **Android 对应**：`MainActivity.kt` 中 `ConversationScreen`(1508)、`ConversationTopBar`(1809)、`MessageBubble`(1871)、
  `InkAgentBadge`(2164)；Markdown 渲染在 `ChatMarkdown.kt`(828)；实时通道 `ChatSocket.kt`(534)。
- **可信度**：A。

### 2.3 历史会话 —— 已定位（A 级）

- **定位**：`b5cd62b3` @ 1240591–1308999（68,408 B，110 规则）；全局搜索 `2736050c` @ 1235746–1240591；
  新建对话 `50ff9297` @ 1216673–1235746。
- **入口与布局**：分段列表 —— 「群聊 / 工作流 / 历史记录 / 最近」四个分组，含空态（「暂无群聊 / 暂无工作流 /
  暂无历史记录 / 暂无单聊记录」）与搜索结果态（「搜索结果 / 没有找到匹配内容」）。
- **关键交互**：置顶/取消置顶、归档/取消归档、重命名、删除会话；搜索入口文案「搜索标题与历史消息内容」「选择会话后直接打开」。
- **API 事件**：`/api/studio/sessions`、`/api/studio/sessions/${...}`、`/api/studio/sessions/hermes`、
  `/api/studio/sessions/hermes/groups`、`/api/studio/workflows`、`/api/studio/group-chat/rooms`、
  `/api/studio/group-chat-link/v1/connections/${...}`。
- **云功能排除点**：会话「分享码」相关（「分享码无效」）与 `/api/studio/group-chat-link/v1/*` 属跨设备中继，本地复刻可排除。
- **Android 对应**：会话列表在 `MainActivity.kt` 会话视图内；`AppViewModel.kt`(3634) 管理状态。
- **可信度**：A。

### 2.4 群聊 —— 已定位（A 级）

- **定位**：`e0c3cb1f` @ 1953786–2116171（**162,385 B，本 App 最大组件**）；成员浮层 `760e0344` @ 1947844–1953786。
- **入口与布局**：会话列表「新建群聊」入口；成员浮层含「房主 / 成员 / 在线 / 离线 / 正在输入... / 正在回复 / 空闲 / 添加 Agent」。
- **关键交互**：Agent 预设（`/api/studio/group-chat/agent-presets`）、邀请链接
  （`/api/studio/group-chat/invites/${...}`）、房间创建/加入（`rooms`、`rooms/join/${...}`）、
  房间状态（「群聊连接超时 / 群聊尚未连接 / 群主已拒绝 Agent 接入」）。
- **API 事件**：`/api/studio/group-chat/rooms`、`/api/studio/group-chat/rooms/${...}`、
  `/api/studio/group-chat/rooms/join/${...}`、`/api/studio/group-chat/agent-presets`、
  `/api/studio/group-chat/invites/${...}`、`/api/studio/group-chat-link/v1/connect`、`/api/auth/me`。
- **云功能排除点**：**整个群聊域依赖 Ekko 云中继**（`group-chat-link`、房间与邀请均由服务端协调）。
  本地直连复刻应整体排除，或降级为「同一 Studio 上的多 Agent 并行会话」。
- **Android 对应**：`Channels.kt`(199)、`AgentToolScreens.kt`(749) 可能含相关壳；未见等价的群聊房间实现，属**功能缺口**。
- **可信度**：A（span 与 API 路径同域）。

### 2.5 Files / 工作区 —— 已定位（A 级）

- **定位**：`07bc1664` @ 1778078–1798801（20,723 B）；配套 `ba39d9e1`（文件预览）@ 1768726–1775928、
  `879b120a`（本轮 diff）@ 1680185–1683233、`e263dfb1`（目录选择器）@ 1204103–1216673。
- **入口与布局**：全屏浮层 `.workspace-layer`（`position:fixed; z-index:720`）+ `.workspace-backdrop` + `.workspace-browser`。
  三段结构：header / location-bar / (tree | document)。
- **emits**：`["close","preview-file","switch-workspace"]` —— **组件自身不直接发 API 请求**，
  span 内 `/api/` 命中 **0**，读写由父级传入。
- **关键交互**：新建文件 / 新建目录 / 刷新目录 / 切换工作区 / 展开折叠目录 / 选中文件 / 打开文档（编辑或 diff）/ 保存。
- **状态表**：`workspace-file-row.git-status-{modified,added,untracked,deleted,renamed,conflicted}` 各对应
  `--ink-git-*` 色令牌（如 `--ink-git-modified:#895503`）。
- **Android 对应**：`StudioWorkspaceScreens.kt`（**90 行**）—— 与原版 20,723 B 组件相比是**显著缺口**。
- **可信度**：A。详见 §4 深度分析。

### 2.6 终端 —— **未定位**

- `终端` 命中 **0**；`Terminal` 命中 **0**；`terminal` 命中 **4**，全部是 fork/run 事件上的**布尔标志**：
  `!0===e.terminal&&(Xa(),...)`、`!0!==e.terminal&&!1!==e.ok`、`"fork"===String(e.command).toLowerCase()&&(!0===e.terminal||...)`。
- **结论**：v1.0.3 **没有终端模拟器界面**。该字段是「本次运行为 fork/终端型」的服务端标记，不含 UI。
- **Android 侧参考**：Hermes Studio MCP 的 `hermes-studio-devices` 暴露了交互式终端能力，但那是**桌面 Studio 的能力**，
  不是本 App 的界面。若 Android 端已有终端界面，属**超出原版的新增**，需在差距表中单列。

### 2.7 浏览器 —— **未定位**

- `浏览器` 命中 74，邻域关键词分布：`授权` 44、`登录` 27、`系统` 16、`打开` 15、`GitHub` 13。
- 抽样上下文全部为 **拉起系统外部浏览器做 OAuth**（如 `github_browser_open_failed`、「无法打开系统浏览器」）。
- `browser` 命中 22，其中 `app.css` 1 次为 `-webkit-browser` 类前缀，无独立浏览器界面作用域。
- **结论**：**没有应用内浏览器**。Markdown 链接统一「使用系统浏览器打开」。

### 2.8 工作流 —— 已定位（A 级）

- **定位**：运行视图 `45f94f2e` @ 2597225–2659243（62,018 B）；节点 `19d17ade` @ 2581543–2597225；
  表单 `8eec0edf` @ 2522484–2528035；连线与条件 `4b83134d` @ 2528035–2542142。
- **入口与布局**：会话列表「新建工作流」入口；表单含「名称 / 工作区 / 工作流创建后不能切换 Profile」。
- **节点属性**（`19d17ade`）：「节点名称 / 选择 Agent / 运行模式（全局 / 当前配置 / 默认 / 无）/ 选择模型 /
  汇合方式（等待全部上游 / 任一上游完成）/ 节点完成后需要审批 / 选择 Skills / 输入节点提示词」；
  思考档位：「最小 / 低 / 中 / 高 / 超高 / 最大」。
- **连线条件**（`4b83134d`）：上游结果判定（「上游正常返回 / 上游执行失败 / 任一结果 / 不检查回复内容」）、
  数据路径（`output`、`outputJson.*`、`error`）、运算符 12 种（等于/不等于/包含/不包含/存在/不存在/大于/大于等于/小于/小于等于/属于列表/不属于列表）。
- **API 事件**：`/api/studio/workflows`、`/api/studio/workflows/${...}`。
- **云功能排除点**：无（工作流执行落在本地 Studio 设备）。
- **Android 对应**：`MainActivity.kt` 工作流视图；未见与 12 种运算符、汇合方式等价的数据模型，属**功能缺口**。
- **可信度**：A。

### 2.9 定时 —— 已定位（A 级，两套并存）

原版存在**两套彼此独立的定时体系**，容易混淆：

| 体系 | scope | span | 面向 | API |
| --- | --- | --- | --- | --- |
| Hermes Jobs | `d3614ca5` @ 2370012–2385007（14,995 B）+ `87ee7e53` @ 2388731–2397605 + `218409ea` @ 2385007–2388731 | 任务 CRUD、周期选择、投递目标、运行历史 | `/api/hermes/jobs`、`/api/hermes/jobs/${...}`、`/api/hermes/jobs/delivery-targets`、`/api/cron-history${...}`、`/api/cron-history/${...}` |
| 工作流定时计划 | `1c43be07` @ 2542142–2555763（13,621 B） | 按计划在工作流上执行 | `/api/studio/workflows/${...}` |

- **周期枚举（两套共用文案）**：「每分钟 / 每 5 分钟 / 每 30 分钟 / 每小时 / 每天 / 每周 / 每月 / 自定义」；
  星期文案「星期日…星期六」；时区无关。
- **状态枚举**：「运行中 / 已暂停 / 已调度 / 已禁用」；来源「本地」。
- **关键交互**：暂停/恢复、立即触发、删除任务、查看运行历史（「N 次运行」「暂无运行历史」）。
- **云功能排除点**：投递目标 `/api/hermes/jobs/delivery-targets` 可能指向云端通道，需按实际响应裁剪。
- **Android 对应**：`CronJobs.kt`（845 行）—— 两套体系是否都覆盖需单独核对。
- **可信度**：A。

### 2.10 Kanban / 看板 —— **未定位**

- `Kanban` 命中 8 + `kanban` 命中 1 + `看板` 命中 12，共 21 次。逐条核对结果：
  - **8 次 `Kanban`**：全部在 i18n 词条表（`e29ff977` span 内，偏移 249574 / 376985 / 442801 / 523001 / 633195 / 742699 / 851226 / 955080），
    均为中文键「看板拆解」的其他语言译文（`"Kanban dismantling"`、`"Kanban 分解"`、`"Décomposition Kanban"` …）。
  - **1 次 `kanban`**（偏移 2160469，在 `98a96c0d` 辅助模型 span 内）：
    `title_generation:"标题生成", triage_specifier:"任务分诊", kanban_decomposer:"看板拆解", profile_describer:"配置描述", curator:"记忆整理", session_search:"会话搜索"` ——
    这是**辅助模型职责枚举**中的一个角色名。
  - **12 次 `看板`**：11 次是上表 i18n 键本身（含繁体 319636/319643），1 次是 `kanban_decomposer` 的中文标签。
- **结论**：v1.0.3 **没有看板界面**。「看板拆解」只是「让某个辅助模型负责把任务拆解成看板条目」的**角色标签**。
- **重要提示**：Android 仓库中存在 `KanbanScreens.kt`（648 行）。**该界面在原版 v1.0.3 中无对应物**，
  属超出原版的新增实现，复刻验收时应排除在「对齐原版」范围之外，否则会误判为已完成。

### 2.11 Agent / Skills / Plugins / MCP

- **Agent 与 Runtime 管理** —— 已定位：`e60e128b` @ 2229496–2260614（31,118 B）。
  含 CLI 版本、更新策略、下载/校验/解压/安装进度、Runtime 版本与重启（「立即重启 / 稍后」）。
  API：`/api/agents/status`、`/api/coding-agents`、`/api/coding-agents/${...}`、
  `/api/coding-agents/${...}/update-policy`、`/api/coding-agents/update-policies`、`/api/devices/link-info`、
  `/api/hermes/runtime-versions`、`/api/hermes/runtime-versions/active-runtime`、`/api/hermes/runtime-versions/jobs`、
  `/api/hermes/runtime-versions/restart-webui`、`/api/hermes/runtime-versions/runtime/${...}`、
  `/api/hermes/runtime-versions/runtime/download`。
  Android 对应：`AgentToolScreens.kt`(749)、`AgentTools.kt`(250)。
- **Skills** —— 已定位：列表 `3c019f49` @ 2355683–2367250；详情 `f9d684ea` @ 2346209–2355683；
  用量 `6bb0fc77` @ 2424556–2435320。
  来源枚举：`builtin:"内置"`、`hub:"Hub 安装"`、`external:"外部目录"`、`local:"本地安装"`。
  状态：「已归档 / 用户已修改 / 已置顶技能」；操作：置顶、归档、编辑、保存。
  API：`/api/hermes/skills`、`/api/hermes/skills/${...}`、`/api/hermes/skills/toggle`、`/api/hermes/skills/pin`、
  `/api/hermes/skills/usage/stats`、`/api/ekko/skills`、`/api/ekko/skills/${...}`。
- **MCP** —— 已定位：列表 `b62dab03` @ 2334125–2346209；工具可见性 `961fba34` @ 2326254–2334125；
  表单 `2f3f27cd` @ 2279052–2326254（**47,202 B**）。
  状态：「已连接 / 未连接 / 已禁用 / 总计 N 工具」；工具可见性三档「全部 / 仅包含 / 排除」。
  API：`/api/hermes/mcp/servers`、`/api/hermes/mcp/servers/${...}`、`/api/hermes/mcp/reload`、
  `/api/hermes/mcp/tools`、`/api/ekko/mcp/servers`、`/api/ekko/mcp/servers/${...}`、`/api/coding-agents/${...}`。
- **Plugins —— 未定位**：`插件` 命中 **0**；`Plugin` 命中 7、`plugin` 命中 2，全部位于打包的
  highlight.js 10.7.3 内部（语法高亮器注册表），与界面无关。
- **云功能排除点**：`/api/ekko/*` 前缀（`ekko/memory`、`ekko/mcp`、`ekko/skills`）属 Ekko 云侧配置存储；
  本地直连复刻应走 `/api/hermes/*` 同名端点。
- **可信度**：A（已定位域）；未定位项为 C 级「零命中」判定，但已逐条检查反例上下文。

### 2.12 Profiles —— 已定位（A 级）

- **定位**：列表 `181a58e6` @ 2443350–2453010；表单 `00cf131c` @ 2435320–2439821；配置文件 `d15684f0` @ 2439821–2443350。
- **布局**：卡片式列表（「当前使用 / 默认 / 模型 / 技能 / 已配置 / 未配置 / 详情暂不可用」）+ 展开收起详情 + 新建/删除。
- **表单约束**：「仅支持小写字母、数字、下划线和连字符」「例如 coding_agent」「从当前 Profile 克隆
  —— 复制当前配置；独占平台凭据会被安全清理并停用」。
- **API 事件**：`/api/hermes/profiles`、`/api/hermes/profiles/${...}`；
  配置文件编辑走 `/api/studio/files/read` 与 `/api/studio/files/write`（读取 `config.yaml`）。
- **云功能排除点**：无。
- **Android 对应**：`StudioSettings.kt`(793) 内的设置分支。
- **可信度**：A。

### 2.13 Models —— 已定位（A 级，7 个组件）

| 子域 | scope | span | 关键内容 |
| --- | --- | --- | --- |
| 模型总览 | `fb39f5d3` | 2213172–2229496 | 常规/辅助/组合/语音识别/语音合成 五类；模型缓存刷新与恢复 |
| Provider 编辑 | `303c0407` | 2135957–2152283 | 请求地址、超时、停滞超时、限流延迟、上下文长度、额外请求参数（须为 JSON 对象） |
| Provider 授权 | `f9857c3f` | 2127658–2135957 | 授权码粘贴、轮询、MiniMax 区域（中国）、Copilot token 检查 |
| 辅助/备用模型 | `98a96c0d` | 2158294–2172890 | 角色枚举：上下文压缩/图片理解/网页提取/技能中心/审批判断/标题生成/任务分诊/看板拆解/配置描述/记忆整理/会话搜索/记忆写入 |
| 组合模型 MoA | `9e1d5a9f` | 2172890–2186178 | 参考模型 → 聚合模型 |
| 语音 | `8d1c9efc` | 2186178–2213172 | 本地/浏览器/豆包/自定义 STT；豆包/自定义 TTS；音色枚举 |
| 模型选择器 | `72375a2b` | 1722472–1728926 | 普通/组合模型切换、搜索、预览 |

- **API 事件**：`/api/hermes/config/providers`、`/api/hermes/config/providers/${...}`、`/api/hermes/provider-models`、
  `/api/hermes/provider-models/cache/refresh`、`/api/hermes/config/fallback-providers`、`/api/hermes/config/moa`、
  `/api/hermes/auth/${...}/start`、`/api/hermes/auth/${...}/poll/${...}`、`/api/hermes/auth/anthropic/submit/${...}`、
  `/api/hermes/auth/copilot/check-token`、`/api/hermes/auth/copilot/enable`、
  `/api/voice/providers/probe`、`/api/studio/stt/local-model`、`/api/studio/stt/local-model/download`、
  `/api/v3/auc/bigmodel`、`/api/v3/tts/unidirectional`、`/api/studio/${...}/settings`。
- **凭据处理**：Provider 表单出现「清除 Provider 凭据 / 凭据已清除」。
  **本文档不记录任何凭据字段的实际值或形态，示例一律 `[REDACTED]`。**
- **云功能排除点**：`/api/v3/auc/bigmodel`、`/api/v3/tts/unidirectional` 为豆包云端语音；本地复刻应仅保留
  `本地语音识别` 与 `自定义 STT/TTS`。
- **可信度**：A。

### 2.14 Memory —— 已定位（A 级，两套）

| 体系 | scope | span | 内容 |
| --- | --- | --- | --- |
| Ekko 记忆 | `3f8c1721` | 2260614–2268382 | 状态枚举「有效 / 已取代 / 已过期 / 已删除 / 全部」；搜索、编辑、删除 |
| Hermes 记忆分段 | `f89e2bdf` | 2406382–2411957 | 三段：「我的笔记」「用户画像」「灵魂」 |

- **API 事件**：`/api/ekko/memory`、`/api/ekko/memory/${...}`、`/api/hermes/memory`。
- **云功能排除点**：`/api/ekko/memory*` 属 Ekko 云记忆；本地复刻保留 `/api/hermes/memory`。
- **未保存保护**：两套共用文案「放弃未保存修改？/ 当前…尚未保存。/ 放弃」。
- **可信度**：A。

### 2.15 Hub —— **未定位**（本轮修正早期结论）

`Hub` 命中 146 次，逐条核对：

- **120 次**是 `GitHub` 的子串（OAuth 登录相关）。
- **26 次**与此无关，其中：
  - 「Hub 安装」i18n 词条 **22 次**（11 语言 × 中/繁）；
  - 法/德/葡/西 将「技能中心」译为 `Hub de compétences` / `Skill-Hub` / `Hub de habilidades` **4 次**；
  - 代码中 `"hub"` 枚举 **3 处**：`"builtin"===t.skill.source?"内置":"hub"===t.skill.source?"Hub 安装":...`、
    `{value:"hub",label:"Hub 安装",kind:"hub"}`、`ng(t.$t(...))` 的同款三元表达式。

> **修正**：早期摘要曾记为「146 次全部落在 GitHub 内」。重新逐条核对后该结论**不成立**。
> 正确结论是：26 次为 **Skills 来源枚举 `hub`（「Hub 安装」）**，仍**不构成独立界面**。

- **结论**：v1.0.3 **没有独立的 Hub 页面**；`hub` 仅是 Skill 的四种来源之一。

### 2.16 日志 / 用量 / 性能 / Journey

- **日志** —— 已定位：列表 `237c0272` @ 2456552–2464031（7,479 B）；详情 `175a997a` @ 2453010–2456552。
  含「日志文件 / 级别 / 行数 / 搜索消息、Logger 或原始日志」；详情字段「级别 / 时间 / 消息 / 原始日志 + 复制」。
  API：`/api/studio/logs`、`/api/studio/logs/${...}`。Android 对应：`StudioSettings.kt` 分支。
- **用量** —— 已定位：`1f1f16f0` @ 2464031–2478565（14,534 B）。
  指标：「总 Token / 输入 / 输出 / 会话总数 / 平均每天 / 预估费用 / N 次 API 调用 / 缓存命中率 / 缓存读取 Token」+
  分模型用量表。API：`/api/studio/usage/stats`。
- **性能监控** —— 已定位：`130adebd` @ 2478565–2489404（10,839 B）。
  指标：「系统 CPU / 系统内存 / 活跃会话 / 总内存 / Worker 列表（进程 / 运行中 / 已停止 / 内存 / 上次更新）」+
  自动刷新开关。API：`/api/studio/performance/runtime`。
- **Journey / 旅程 —— 未定位**：`Journey` 命中 **0**，`旅程` 命中 **0**。
- **Skills 用量** —— 已定位：`6bb0fc77` @ 2424556–2435320（10,764 B）。
  指标：「总操作 / 使用技能数 / 占比 / 最近使用 / 每日趋势 / 最近 N 次操作」。API：`/api/hermes/skills/usage/stats`。
- **可信度**：已定位项 A；Journey 未定位为 C 级零命中。

### 2.17 主题 / 语言 / 显示 / 隐私 / 代理

- **手机主题** —— 已定位：`3d4864a4` @ 2503183–2511224（8,041 B）+ 取色器 `f09227f2` @ 2498661–2503183 +
  切换按钮 `b6963587` @ 1138640–1139696。
  文案：「手机主题 —— 仅保存在当前手机，不会覆盖服务端主题」「字体大小 / 文字颜色 / 强调色 / 手机背景图」；
  取色器含「色相 / 鲜艳度 / 明暗 / 跟随默认主题 / 应用颜色」；重置「恢复默认主题？将清除手机本地保存的字体、颜色和背景图」。
  背景图「无需裁图，自动适配折叠、展开与横竖屏」。
  **重要**：`app.css` 中 ink 令牌写作 `var(--app-custom-*, <默认值>)` 形式，说明主题层是**在令牌之上注入覆盖变量**，而非替换令牌。
  Android 侧的 `Theme.kt` 只实现了默认分支，**未建模 `--app-custom-*` 覆盖层**。
  **API**：无（纯本地存储）。**云功能排除点**：无。
- **语言** —— 词条表 `e29ff977` @ 219802–1138640，**918,838 B**；实测语言数：中(简/繁)、英、日、韩、法、西、德、葡、俄、阿 = **11 套**。
- **显示 / 代理 / 压缩 / 隐私** —— 已定位：全局设置 `d28cb492` @ 2489404–2498661（9,257 B）。
  分组：「显示 / 代理 / 上下文压缩 / 隐私」。
  显示项：「流式响应（实时显示 AI 回复）/ 紧凑模式（减少 Studio 消息间距）/ 显示推理过程 / 显示费用 / 内联差异 /
  完成提示音 / 审批提示音 / 横幅提醒 / 震动提醒 / 选择音效 / 聊天输入框高度」。
  代理项：「普通 HTTP 请求代理」「HTTPS 请求代理，通常优先配置这一项」。
  API：`/api/hermes/config`。
- **隐私 —— 未定位为独立界面**：`隐私` 命中 100，抽样为**法律文本与权限用途说明**
  （相机/相册/麦克风/日历的授权理由），非隐私设置页。权限文案示例见词条表：
  「用于拍摄图片或视频；仅在你发送消息后，所选内容才会作为聊天附件上传」等。
- **可信度**：主题/全局设置为 A；语言为 A（词条表可枚举）；隐私未定位为 C。

---

## 3. 深度分析（一）：设备页 `/pages/devices/index`

### 3.1 真实原版处理流程

1. **启动即进入本页**：`reLaunch("/pages/devices/index")` 是启动与「会话失效回退」的统一目标（10 次调用）。
2. **首次渲染**：读本地已保存设备列表 → 并发探测 → 期间显示「正在读取设备」。
3. **有设备**：渲染 `.device-grid`，每卡一个设备；连接中卡片加 `--connecting`（禁用指针 + `opacity:.72`）。
4. **无设备**：网格末尾始终渲染 `.add-device-card`（虚线边框），点击进入配对流程。
5. **配对**：`PairingMethodsPanel` 二选一 ——
   - 「扫码添加」（`ScanPanel`，文案「扫描 Studio 生成的局域网二维码」）
   - 「手动添加」（`ManualPairingPanel`，文案「输入局域网地址和 Studio 账号」
     「使用任意启用的 Studio 用户账号登录并保存设备」）
6. **配对成功后**：卡片出现操作行（`DeviceCardAction`）与来源标签（`.device-source-tag--local` / `--cloud`）。
7. **线路切换**：`RoutePanel` / `AppRouteSwitch`，文案「切换 App 线路」「App 云端线路」。

### 3.2 尺寸 / 色值表（原版实测，单位 CSS px）

| 元素 | 选择器（offset） | 属性 | 基础值 | 横屏矮屏 ≤599px | 窄屏 ≤520px |
| --- | --- | --- | --- | --- | --- |
| 头部容器 | `.devices-header-content`（6526） | padding | `calc(18px + safe-top) 18px 18px` | — | — |
| 内容容器 | `.devices-content`（6567） | padding | `0 18px calc(28px + safe-bottom)` | — | — |
| 两者 | 同左 | width / max-width | `100%` / `680px`，`margin:0 auto` | 横屏 900×600 时 `calc(100% - 32px)` / `1240px` | — |
| 网格 | `.device-grid`（13020） | columns / gap | `repeat(2,minmax(0,1fr))` / `12px` | `10px` | `1fr`（单列） |
| 卡片 | `.device-card`（13118） | min-height / padding / radius | `170px` / `17px` / `17px` | `0` / `12px` / `14px` | min-height `156px` |
| 卡片 | 同左 | background / border | `var(--ink-bg-card)` / `1px solid var(--ink-border-light)` | — | — |
| 卡片按下 | `.device-card--pressed` | 效果 | `background:var(--ink-bg-card-hover); transform:scale(.985)` | — | — |
| 卡片连接中 | `.device-card--connecting` | 效果 | `opacity:.72; pointer-events:none` | — | — |
| 新增卡 | `.add-device-card`（13148） | border | `1.5px dashed var(--ink-border)`，背景透明 | 同卡片 | min-height `156px` |
| 状态胶囊 | `.device-status`（14451） | padding / radius / font | `4px 7px` / `10px` / `10px`，行高 `12px`，gap `5px` | — | — |
| 状态在线 | `.device-status--online` | 浅色 / 深色 | `#397454` on `rgba(57,116,84,.1)` / `#8fc9a5` on `rgba(143,201,165,.1)` | — | — |
| 卡片操作 | `.device-card-action`（17236） | height / radius / font | `28px` / `8px` / `10px`，`font-weight:550`，`flex:1` | `26px` / `9px` | — |
| 区块标题 | `.section-title`（12608） | font / weight / 行高 / letter-spacing | `22px` / `650` / `29px` / `-.25px` | `19px` / `24px` | — |
| 区块说明 | `.section-description` | font / 行高 | （基础值） | `10px` / `14px` | — |
| 设备图标 | `.device-symbol`（13788） | size | `31×31px`，`position:relative` | — | — |
| 屏幕图形 | `.device-screen`（13861） | size / border / radius | `31×22px` / `1.5px solid var(--ink-text-primary)` / `5px` | — | — |
| 屏幕圆点 | `.device-screen-dot` | size / 定位 | `2×2px`，`right:3px; bottom:3px`，圆形 | — | — |
| 支架图形 | `.device-stand`（14166） | size / 定位 | `11×6px`，`bottom:2px; left:10px`，`border-bottom:1.5px` | — | — |
| 设备名 | `.device-name`（15033） | font / weight / 行高 | `14px` / `620` / `20px`，单行截断 | — | — |
| 系统行 | `.device-system` | font / 行高 | `10px` / `15px` | — | — |
| 版本标签 | `.device-version-row uni-text`（15454） | padding / radius / font | `3px 6px` / `6px` / `8px`，行高 `12px`，等宽字体 Menlo/Monaco/Consolas | — | — |
| 来源标签 | `.device-source-tag--local` / `--cloud` | 颜色 | `#397454` / `#7657a6` | — | — |
| 端点标签 | `.device-endpoint-tag--desktop` / `--web` | 颜色 | `#397454` / `#3f648f` | — | — |
| 账号触发 | `.account-trigger` | size | （基础值） | `30×30px` | — |

**媒体查询边界（实测偏移）**

- `@media (orientation: landscape) and (min-width: 900px) and (min-height: 600px)` @ 22863
- `@media (orientation: landscape) and (max-height: 599px)` @ 23146
- `@media (max-width: 520px)` @ 24413

### 3.3 状态表

| 状态 | 触发条件 | 视觉 | 可交互 |
| --- | --- | --- | --- |
| 读取中 | 首帧后未探测完 | 「正在读取设备」+ 加载态 | 否 |
| 正常 | 探测成功 | 卡片常规样式 | 是 |
| 连接中 | 正在建立连接 | `--connecting`：`opacity .72` | **否**（`pointer-events:none`） |
| 按下 | 触摸按下 | `--pressed`：`scale(.985)` + hover 背景 | 是 |
| 在线 | 设备在线 | 状态胶囊 `--online` 绿色 | 是 |
| 离线 | 设备离线 | 状态胶囊灰 `--ink-text-muted` | 是（可重连） |
| 错误 | 读取失败 | 错误横幅 + 「重新同步」 | 是（重试） |
| 空 | 无设备 | 仅新增卡（虚线） | 是 |

### 3.4 Compose 迁移方案

**单位处理**：原版 `px` 在 uni-app App 端 WebView 中以设备宽度为视口，**1 px ≈ 1 dp**，可直接映射，**不做比例换算**。
例外是 `calc(18px + var(--app-safe-area-top))` 与 `env(safe-area-inset-bottom)` —— 应映射为
`WindowInsets.statusBars` / `WindowInsets.navigationBars`，**不要硬编码 18dp / 28dp 后再叠加安全区**，否则会双重留白。

| 原版 | Compose |
| --- | --- |
| `max-width:680px; margin:0 auto` | `Modifier.widthIn(max = 680.dp)` + 父级 `Arrangement.Center` |
| `grid-template-columns:repeat(2,minmax(0,1fr))` | `LazyVerticalGrid(GridCells.Fixed(2))` |
| 横屏 `repeat(auto-fit,minmax(300px,1fr))` | `GridCells.Adaptive(minSize = 300.dp)`，仅当 `screenWidthDp >= 900 && screenHeightDp >= 600 && landscape` |
| ≤520px 单列 | `GridCells.Fixed(1)` |
| `gap:12px` | `Arrangement.spacedBy(12.dp)` |
| `transform:scale(.985)` | `Modifier.graphicsLayer { scaleX/Y = .985f }`（**不要用 `Modifier.scale`**，会连带缩放阴影） |
| `pointer-events:none` + `opacity:.72` | `enabled = false` + `Modifier.alpha(.72f)` |
| `1.5px dashed` | `Modifier.drawBehind` + `PathEffect.dashPathEffect`，**`border` 不支持下划线样式** |
| `-webkit-line-clamp:1` | `maxLines = 1; overflow = Ellipsis` |
| `font-weight:650 / 620 / 550` | 需映射到可变字重或最近档（`W600` / `W600` / `W500`）；**Android 静态字体通常不提供 650 档** |
| `Menlo,Monaco,Consolas` | `FontFamily.Monospace` |

**已存在实现**：`DevicesScreen.kt` 284–362 行已实现 520 / 900×600 分支，方向正确；§3.5 列出未覆盖项。

### 3.5 验收清单（设备页）

- [ ] 网格列数在 `screenWidthDp = 480 / 520 / 521 / 900` 四点分别为 `1 / 1 / 2 / auto-fit`
- [ ] 卡片 `min-height` 在 170 / 156 / 0（横屏矮屏）三态正确
- [ ] `设备网格 gap` 为 12 / 10（横屏矮屏）
- [ ] 卡片圆角 17 → 14（横屏矮屏）
- [ ] 标题字号 22 → 19（横屏矮屏），字重 650
- [ ] 连接中卡片**不可点击**（验证 `pointer-events:none` 语义，不只是变淡）
- [ ] 按下态为 `scale(.985)` 而非 `alpha` 变化
- [ ] 新增卡为 `1.5px` **虚线**边框，非实线
- [ ] 在线状态胶囊浅色 `#397454` / 深色 `#8fc9a5`，底色透明度 `.1`
- [ ] 设备图标由 `31×22` 屏幕 + `2×2` 圆点 + `11×6` 支架三件套组成，非单一矢量图标
- [ ] 版本标签使用等宽字体、`8px`、圆角 `6px`
- [ ] 来源/端点标签四色正确（local `#397454` / cloud `#7657a6` / desktop `#397454` / web `#3f648f`）
- [ ] 安全区只计算一次（不出现头部与内容各叠一次顶部留白）
- [ ] 云端项已排除：账号权益、有效至、扫码审批、App 云端线路

---

## 4. 深度分析（二）：Files / 工作区

### 4.1 真实原版处理流程

组件 `07bc1664`（span 1778078–1798801，20,723 B）**不发任何 API 请求**（span 内 `/api/` 命中 0），
只通过 3 个事件与父级通信：

```
emits: ["close", "preview-file", "switch-workspace"]
```

交互闭环：

1. 父级置 `visible=true` → 渲染 `.workspace-layer`（`z-index:720`）+ `.workspace-backdrop`（`rgba(0,0,0,.4)`，
   入场动画 `workspace-backdrop-enter-07bc1664 .18s ease-out`）。
2. `.workspace-browser` 以 `workspace-enter-07bc1664 .23s cubic-bezier(.22,.72,.24,1)` 从右侧滑入。
3. header 三段：`.workspace-title-row`（工作区名 + git 状态 + 分支）、`.workspace-root-path`、
   `.workspace-header-actions`（刷新 15px / 新建 17px / 切换 20px）。
4. 「新建」按钮打开 `.workspace-create-menu`（`top: calc(safe-top + 51px); right:12px; width:156px`），
   两项 42px 高，中间 `.workspace-create-menu-divider`。
5. 选中项 → `.workspace-create-layer`（遮罩 `rgba(0,0,0,.36)`）+ `.workspace-create-card`（`max-width:360px`）。
6. `.workspace-location-bar`（38px）显示当前路径 + 「根目录」回跳。
7. 目录树：`.workspace-file-list`（`padding:5px 6px 18px`），行 `.workspace-file-row`（`min-height:48px`），
   展开箭头 `.workspace-tree-chevron` 旋转 `0 → 90deg`（`transition .14s ease`）。
8. 点文件 → `preview-file` 事件交给父级 `ba39d9e1`（文件预览浮层，含 diff / 编辑 / 保存）。
9. 关闭时若文档有未保存内容 → 「放弃修改？/ 当前文档还有未保存的内容。/ 放弃」。

### 4.2 尺寸 / 色值表（原版实测）

| 元素 | 属性 | 值 |
| --- | --- | --- |
| `.workspace-layer` | z-index / 定位 | `720` / `fixed` 全屏 |
| `.workspace-backdrop` | 背景 / 动画 | `rgba(0,0,0,.4)` / `.18s ease-out both` |
| `.workspace-browser` | 背景 / 颜色 / 动画 | `var(--ink-bg-primary)` / `var(--ink-text-primary)` / `.23s cubic-bezier(.22,.72,.24,1) both` |
| `.workspace-header` | min-height / padding / gap / 下边框 | `54px` / `7px 12px` / `8px` / `1px solid var(--ink-border-light)` |
| `.workspace-title` | font / weight / 行高 | `15px` / `650` / `21px`，单行省略 |
| `.workspace-root-path` | font / 行高 / 色 | `9px` / `13px` / `var(--ink-text-muted)` |
| `.workspace-header-action` | size / radius / gap 容器 | `34×34px` / `10px` / `gap:2px` |
| 头部图标 | 尺寸 | 刷新 `15px`、新建 `17px`、切换 `20px`，均带 `filter:var(--ink-icon-filter)` |
| `.workspace-create-menu` | 定位 / size / radius / 阴影 | `top: calc(safe-top + 51px); right:12px` / `156px` / `11px` / `0 12px 34px rgba(0,0,0,.2)` |
| `.workspace-create-menu-item` | height / padding / gap / radius / font | `42px` / `0 11px` / `10px` / `7px` / `13px` |
| `.workspace-create-layer` | 遮罩 / padding | `rgba(0,0,0,.36)` / `24px` |
| `.workspace-create-card` | max-width / padding / gap / radius / 阴影 | `360px` / `18px` / `15px` / `15px` / `0 16px 50px rgba(0,0,0,.22)` |
| `.workspace-create-title` | font / weight / 行高 | `16px` / `650` / `23px` |
| `.workspace-create-subtitle` | font / 行高 / 色 | `11px` / `17px` / `var(--ink-text-muted)` |
| `.workspace-create-input` | height / padding / radius / font | `42px` / `0 12px` / `9px` / `13px` |
| `.workspace-create-button` | min-width / height / padding / radius / font | `72px` / `36px` / `0 14px` / `9px` / `12px` |
| `.workspace-create-button--primary` | 配色 | `color: var(--ink-on-accent); background: var(--ink-accent)` |
| `.workspace-location-bar` | min-height / padding / 下边框 | `38px` / `5px 13px` / `1px solid var(--ink-border-light)` |
| `.workspace-location-caption` | font / 色 | `10px` / `var(--ink-text-muted)` |
| `.workspace-location-path` | font / 色 | `11px` / `var(--ink-text-secondary)` |
| `.workspace-location-root` | height / radius / font | `26px` / `7px` / `10px` |
| `.workspace-file-list` | padding | `5px 6px 18px` |
| `.workspace-file-row` | min-height / padding / gap / radius | `48px` / `5px 7px 5px` / `7px` / `5px` |
| `.workspace-file-row--selected` | 背景 | `var(--ink-bg-secondary)` |
| `.workspace-tree-chevron` | size / font / 行高 / 过渡 | `12×18px` / `19px` / `18px` / `transform .14s ease` |
| `.workspace-file-icon` | size / 透明度 | `19×19px` / `.82` |
| `.workspace-file-name` | font / 行高 / 色 | `13px` / `19px` / `var(--ink-text-primary)` |
| `.workspace-file-meta` / `-time` | font / 行高 / 色 | `9px` / `14px` / `var(--ink-text-muted)` |
| `.workspace-state` | min-height / padding / font | `140px` / `24px` / `12px`，居中 |
| `.workspace-spinner` | size / 边框 / 动画 | `17×17px` / `1.5px solid var(--ink-border)`，顶边 `--ink-text-secondary` / `.75s linear infinite` |
| `.document-toolbar` | min-height / padding / gap | `44px` / `5px 12px` / `8px`，背景 `--ink-bg-card` |
| `.document-back` | height / padding / radius / font | `30px` / `0 7px 0 3px` / `7px` / `11px` |
| `.document-path` | font / 行高 / 对齐 | `10px` / `16px` / `text-align:center` |
| `.document-action` | height / min-width / padding / radius / font | `30px` / `48px` / `0 10px` / `8px` / `11px` |
| `.document-diff-add` / `-delete` | 颜色 | `#2da44e` / `#cf222e` |
| `.document-preview` | 背景 / 前景 / 字体 | `var(--ink-bg-code)` / `var(--ink-code-text)` / 等宽 `11px`，行高 `18px` |
| `.document-diff-line` | min-height / padding | `18px` / `0 14px`，`white-space:pre-wrap` |
| `.document-diff-line--added` | 色 / 背景 | `#166534` / `rgba(34,197,94,.14)` |
| `.document-diff-line--deleted` | 色 / 背景 | `#b91c1c` / `rgba(239,68,68,.12)` |
| `.document-diff-line--hunk` | 色 / 背景 | `#3b82f6` / `rgba(59,130,246,.12)` |
| `.document-editor` | padding / 字体 / 行高 | `14px 16px 24px` / 等宽 `12px` / `19px` |

### 4.3 状态表

| 状态 | 触发 | 文案 | 视觉 |
| --- | --- | --- | --- |
| 读取目录 | 展开目录 | 「正在读取目录」 | `.workspace-state` + `.workspace-spinner` |
| 读取文档 | 打开文件 | 「正在读取文档」 | 同上 |
| 空目录 | 目录无子项 | 「此目录为空」 | `.workspace-state` |
| 读取失败 | 请求失败 | 「读取工作区失败 / 读取目录失败 / 读取文档失败」 | `.workspace-state--error` + `.workspace-retry`（下划线） |
| 不可用 | 工作区丢失 | 「当前工作区不可用」 | 同上 |
| 行禁用 | 无权限/不可预览 | — | `.workspace-file-row--disabled`，图标与文案 `opacity:.52` |
| 行选中 | 当前路径 | — | `.workspace-file-row--selected` 背景 `--ink-bg-secondary` |
| 新建菜单展开 | 点「新建」 | 「新建文件 / 新建目录」 | `.workspace-create-menu` 浮层 |
| 新建校验失败 | 名称含 `/` | 「名称不能包含路径分隔符」 | toast（`uni.showToast`） |
| 未保存离开 | 关闭时有改动 | 「放弃修改？/ 当前文档还有未保存的内容。/ 放弃」 | 确认浮层 |
| 退出工作区 | 点关闭 | 「退出工作区？/ 退出」 | 确认浮层 |
| 二进制文件 | 非文本 | 「二进制文件无法显示 diff」 | `.document-notice` |
| diff 截断 | 内容过大 | 「diff 内容较大，当前结果已截断」 | `.document-notice` |

### 4.4 Compose 迁移方案

| 原版 | Compose |
| --- | --- |
| `.workspace-layer` `fixed; z-index:720` | 顶层 `Box` 覆盖，由状态 `workspaceVisible` 控制；**不用 Dialog**（原版是整页滑入而非模态居中） |
| `.workspace-browser` 滑入 + `cubic-bezier(.22,.72,.24,1)` | `AnimatedVisibility` + `slideInHorizontally`；贝塞尔可近似为 `tween(230, easing = CubicBezierEasing(.22f,.72f,.24f,1f))` |
| `.workspace-backdrop` `.18s ease-out` | `animateFloatAsState` 透明度，`tween(180)` |
| `--app-safe-area-top` / `env(safe-area-inset-bottom)` | `WindowInsets.statusBars` / `WindowInsets.navigationBars` 一次性 `windowInsetsPadding` |
| 目录树递归 | `LazyColumn` + 扁平化后的 `visibleNodes` 列表（**不要用嵌套 Column 递归**，大目录会掉帧） |
| 箭头 `rotate(0 → 90deg)`，`.14s` | `animateFloatAsState(targetValue = if (expanded) 90f else 0f, tween(140))` + `Modifier.rotate` |
| `git-status-*` 六色 | `--ink-git-{modified,added,untracked,deleted,renamed,conflicted}`，`Theme.kt` **尚未定义这 6 个令牌**（见 §6 差距） |
| `filter: var(--ink-icon-filter)` | 图标一律用 `LocalContentColor` 着色；**原版用 CSS filter 而非 SVG 着色**，迁移时应改用 tint 而不是复制滤镜值 |
| 等宽字体 Menlo/Monaco/Consolas | `FontFamily.Monospace` |
| `font-weight: 650` | 映射 `FontWeight.W600`（理由见 §3.4） |

**单位说明**：本组件全部为 `px`（会话内实测 `rpx` 0 次），可按 1 px ≈ 1 dp 处理；
`calc(safe-top + 51px)` 中的 `51px` 是**相对 header 的固定偏移**，应改为 `headerHeight + 偏移` 的约束式布局，
而不是把 51dp 写死，否则改 header 高度时会错位。

### 4.5 验收清单（Files）

- [ ] 浮层为**整页右滑入**，遮罩 `rgba(0,0,0,.4)`，动画时长 230ms（不是默认 300ms）
- [ ] header 高 54dp，标题 15sp / W600，根路径 9sp / muted
- [ ] 「新建」菜单宽 156dp、圆角 11dp、位于 header 下方 `right:12dp`
- [ ] 新建对话框 `max-width 360dp`、圆角 15dp、输入框高 42dp
- [ ] 目录行 `min-height 48dp`，展开箭头 12×18dp 且旋转 90°
- [ ] 箭头旋转过渡 140ms（不是瞬时）
- [ ] 选中行背景 `--ink-bg-secondary`，非 accent
- [ ] 禁用行图标与文字 `alpha .52`，且**不可点击**
- [ ] 加载态为 17dp 圆环、1.5dp 描边、750ms 线性旋转
- [ ] 空态/错误态 `min-height 140dp`，错误态重试为**下划线**文字
- [ ] 文档区字体为等宽 11sp / 行高 18sp，背景 `--ink-bg-code`
- [ ] diff 四种行色正确（新增 `#166534` / 删除 `#b91c1c` / 文件 `--ink-text-secondary` / hunk `#3b82f6`）
- [ ] 新增/删除计数色 `#2da44e` / `#cf222e`
- [ ] 未保存离开有「放弃」确认，文案与原版一致
- [ ] 二进制与截断提示走 `.document-notice` 条，而非 toast

---

## 5. 未定位清单（原版 v1.0.3 中不存在）

| 界面 | 命中统计 | 反例上下文 | 判定 |
| --- | --- | --- | --- |
| 终端 | `终端` 0 / `Terminal` 0 / `terminal` 4 | 4 次均为事件布尔 `e.terminal`（fork/run 标记） | **未定位** |
| 应用内浏览器 | `浏览器` 74 / `browser` 22 | 邻域：授权 44、登录 27、打开 15，全部为拉起系统浏览器做 OAuth | **未定位** |
| Kanban 看板 | `Kanban` 8 / `kanban` 1 / `看板` 12 | 8 次在 i18n 表、11 次「看板」为词条键、1 次 `kanban_decomposer` 辅助模型角色 | **未定位** |
| Hub | `Hub` 146 | 120 次为 `GitHub` 子串；26 次为 Skill 来源枚举 `hub`（「Hub 安装」）与「技能中心」的四语译名 | **未定位**（*本轮修正早期结论*） |
| Plugins | `插件` 0 / `Plugin` 7 / `plugin` 2 | 9 次全部位于打包的 highlight.js 10.7.3 内 | **未定位** |
| Journey | `Journey` 0 / `旅程` 0 | — | **未定位** |
| 独立隐私设置页 | `隐私` 100 | 法律条款正文 + 相机/相册/麦克风/日历权限用途说明 | **未定位**（隐私项并入「全局设置」分组） |
| 剪贴板 / 蓝牙 | `剪贴板` 0 / `蓝牙` 0 | — | **未定位** |

> 判定口径：零命中直接判未定位；有命中则逐条抽取上下文，**凡命中全部落在 i18n 词条表（`e29ff977` span，219802–1138640）、
> 打包第三方库或无关语义内，均不构成界面存在证据**。

---

## 6. 版本与组件冲突记录

### 6.1 版本冲突：v1.0.3 vs ref-v0.7.13

| 主题 | v1.0.3（**本仓库实际对齐目标**） | ref-v0.7.13 | 结论 |
| --- | --- | --- | --- |
| ink 令牌族 | `app.css` 中完整存在，数值与 `Theme.kt` **完全一致** | **零命中** | 采用 v1.0.3 |
| 消息气泡令牌 | `--ink-bg-message` | `--msg-user-bg` / `--msg-assistant-bg`（两者同值） | 采用 v1.0.3 |
| `.chat-header` / `.header-session-title` | **不存在** | 存在 | **不可用来改 Kotlin 顶栏** |
| `.agent-badge` 断点 | `@media (max-width:640px)` | 无对应覆盖 | 采用 v1.0.3 |
| 断点常量 | 520 / 640 / 900×600 / 599 | `$breakpoint-mobile: 768px` | 两套断点不可混用 |

> **警告**：v0.7.13 的 `ChatPanel` 在 `≤768px` 会**隐藏会话标题**。手机宽度恒小于 768，若按 v0.7.13 移植会得出
> 「顶栏不应显示标题」的错误结论。本仓库顶栏标题应依 v1.0.3 处理。

### 6.2 组件集冲突：两套 `msg-body` 宽度

`index.css` 中同时存在**两个不同 scope** 的 `msg-body` 宽度规则：

| scope | 规则 | 偏移 |
| --- | --- | --- |
| `c0c550c3`（消息列表） | `.msg-body { width:fit-content; max-width:min(88%,920px) }` | 108447 |
| `8aca294f`（输入区） | `.message.user .msg-body { max-width:75% }` | 187397 |
| `8aca294f` | `.message.assistant .msg-body { max-width:80% }` | — |

**冲突性质**：二者是**不同组件**的样式（一个管列表渲染，一个管输入区引用/预览）。它们互不覆盖，但若在
Compose 中把消息行统一成一个 `MessageBubble`，就会出现「该用 88%/920dp 还是 75%/80%」的歧义。
**处置建议**：主会话列表按 `c0c550c3`（88%，920dp 上限）；输入区的引用块才用 75%/80%。**不要合并成一套值**。

### 6.3 令牌覆盖层缺失

`app.css` 中 ink 令牌实际写作：

```css
--ink-bg-message: var(--app-custom-bg-message, #f1f1f1);
--ink-accent:     var(--app-custom-accent,     #333333);
--ink-on-accent:  var(--app-custom-on-accent,  #ffffff);
--ink-bg-card:    var(--app-custom-bg-card,    #ffffff);
--ink-text-primary: var(--app-custom-text-primary, #1a1a1a);
--ink-bg-secondary: var(--app-custom-bg-secondary, #f0f0f0);
```

即原版有「手机主题」本地覆盖层（对应 §2.17 的 `3d4864a4`）。`Theme.kt` 的 `ink*()` 函数**只返回默认分支**，
未建模 `--app-custom-*`。这是**已存在的结构性差距**，不是本轮引入的问题；若后续要实现手机主题，需把这些函数
改为接受覆盖表，而非新增一套常量。

---

## 7. 与 Android 复刻的差距汇总

| 域 | 原版证据 | Android 对应 | 差距 |
| --- | --- | --- | --- |
| 设备 | 194 规则 + 3 断点（A 级） | `DevicesScreen.kt` 1,847 行 | 断点已实现；逐项差异见 §3.5 |
| 聊天 | `c0c550c3` 33 KB（A 级） | `MainActivity.kt` 4 个 composable | 已实现；气泡配色与徽标断点本轮已对齐 |
| Files | `07bc1664` 20,723 B，约 90 条规则（A 级） | `StudioWorkspaceScreens.kt` **90 行** | **最大缺口**，§4.5 清单基本未覆盖 |
| 工作流 | 4 个 scope 共 97 KB，12 种运算符（A 级） | 未定位等价数据模型 | **缺口** |
| 群聊 | `e0c3cb1f` 162 KB（A 级） | 无等价房间实现 | **缺口**（且依赖云，可整体排除） |
| 终端 | 原版无（C 级零命中） | — | **超出原版**（若已实现） |
| Kanban | 原版无，仅辅助模型角色标签（C 级） | `KanbanScreens.kt` **648 行** | **超出原版**，不应计入对齐率 |
| Hub | 原版无，仅 Skill 来源枚举（C 级） | — | 无需实现 |
| 手机主题覆盖层 | `--app-custom-*`（A 级） | `Theme.kt` 无覆盖层 | 结构性缺口 |

**统计口径提示**：本仓库共 20,356 行 Kotlin。其中 `KanbanScreens.kt`（648 行）在 v1.0.3 中**无对应界面**，
因此「对齐原版完成度」应将这 648 行从分子与分母中同时剔除，否则会同时高估分子与低估分母。

---

## 8. 方法论与可复现命令

所有定位由以下只读脚本产出（不修改基准目录）：

```bash
cd /root/Hermes任务文件/HStudioDirect/latest/decoded-v103/assets/apps/__UNI__1F41684/www
python3 - <<'PY'
import re, json
js  = open('app-service.js', encoding='utf-8', errors='replace').read()
# 1) 组件边界：以 __scopeId 标记切分（关联方向见下方说明）
marks = [(m.start(), m.group(1)) for m in re.finditer(r'\[\["__scopeId","(data-v-[0-9a-f]+)"\]\]', js)]
spans = {marks[i][1]: (marks[i-1][0], marks[i][0]) for i in range(1, len(marks))}
# 2) 逐域取标签 / API / 类名
for sc in ('data-v-c0c550c3', 'data-v-07bc1664'):
    a, b = spans[sc]; c = js[a:b]
    print(sc, a, b, b - a)
    print(' labels:', [x for x in re.findall(r'"([一-鿿][^"]{0,40})"', c)][:10])
    print(' api   :', sorted(set(re.findall(r'/api/[\w/${}.\-]{2,80}', c))))
PY
```

### 8.1 `__scopeId` 与组件边界的关联方向（反向）

编译产物把 `[["__scopeId","data-v-X"]]` 追加在其**自身组件代码的尾部**，即标记**收尾**而非开头。因此关联是**反向**的：

```
span(marks[i] 的 scope) = (marks[i-1].offset, marks[i].offset)
```

正向关联（`marks[i]` → `marks[i+1]`）会把每个组件的区间整体错位到下一个组件。本文档 §1.1 及各域偏移均采用**反向**结果，并用「类名包含关系」实证：

| scope | 该 scope 的 CSS 类名 | 反向后 span | span 内是否含该类名 |
| --- | --- | --- | --- |
| `data-v-07bc1664` | `.workspace-file-row` | 1778078–1798801 | 是 |
| `data-v-61a2f361` | `.device-card-action` | 32100–164003 | 是 |
| `data-v-c0c550c3` | `.message-bubble` | 1683233–1716532 | 是 |
| `data-v-e60e128b` | `.agent-badge` | 2229496–2260614 | 是 |

**边界局限**：`app-service.js` 中仅 91 个 `__scopeId` 标记，但 `index.css` 有 88 个样式作用域，且区间内可含无 `__scopeId` 的子组件与共享工具函数。故上面的区段是**近似切分**，
用于「该功能确实在该范围内实现」的定位，不能当作精确的组件字节长度。语义抽检已通过（各域标签与类名自洽），但未逐字节断言。

**遗漏自检**：`61a2f361`（设备页）标记之后紧邻的是 `d62010f4` 密码找回组件（`/api/app/auth/password-code`、`/api/app/auth/reset-password`），
属**云功能**，不计入设备页范围；这正说明区间边界需按类名复核，不能只按标记相邻关系推断。

**本文档未做的事**

- 未运行构建、未提交、未推送、未安装任何 APK。
- 未读取、未输出、未保存任何凭据或连接串。
- 未用网页版代码替代 App 证据；`ref-v0.7.13` 仅在 §6.1 作**冲突对照**出现，不参与任何「原版是什么」的结论。
- 未做逐像素截图比对（无 Android 构建环境），所有视觉结论均来自样式表数值，属**静态代码证据**而非渲染验证。
