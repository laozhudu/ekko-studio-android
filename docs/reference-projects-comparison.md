# AI 智能体移动端客户端 —— 可参考项目全景对比（v0.10.0 版本为基线）

> **定位**：我们做的是「自托管 Ekko Studio / Hermes Agent 的原生 Android 客户端」
> **对标维度**：交互逻辑（草稿/回复/未读/编辑/搜索/多选）、智能体能力（思考/工具/审批/任务卡/引用/终端）、信息流组织（分组/日期/置顶/聚合）、多端同步、安全/隐私
> **数据来源**：GitHub 实测 README / Release / 截图 / 文档；商业应用官网 / 评测 / 官方文档

---

## 📊 一、开源项目（可直接阅读源码 / 借鉴实现）

| 项目 | 星标 | 许可 | 核心定位 | 对我们有价值的实现细节 | 明显短板 |
|------|------|------|----------|------------------------|----------|
| **KYRIE66nb/mobile-agent** | 142 ⭐ | Apache-2.0 | **Android 原生 AI + 本地工具 Agent**，无障碍+视觉双路感知，安全闸，后台虚拟屏，桌面桥，50+ 内置工具 | • 双路感知：无障碍节点树(语义) + 视觉模型(像素坐标)<br>• **DecisionGate 安全闸**：allow/confirm/block 三档，外部副作用必过闸<br>• Root 虚拟屏 + 无障碍服务双引擎，自动降级<br>• **桌面 USB 桥**：adb forward + loopback 鉴权，21 个 `aua_*` 工具给桌面 Agent 用<br>• 悬浮窗实时围观 Agent 操作<br>• 纯 Kotlin 6 模块，零框架依赖，minSdk 24<br>• **DecisionGate 裁决可外接独立小模型服务**（Laya/Jev），SHADOW/ENFORCE 双模式 | • 定位偏「手机自动化 Agent」，非通用聊天客户端<br>• 无草稿多端同步、无会话内搜索、无多选/批量操作<br>• 会话列表极简，无置顶/文件夹/归档 |
| **AI-Mobile-Coders/android-ai-agents** | 19 ⭐ | Apache-2.0 | **Google ADK 之上的 Kotlin SDK**，3 行 DSL 造 Agent，现成 Agent 目录，Compose UI，持久化会话 | • **Ready-made agents**：Chat/Summarizer/Translator/RAG/Vision/Voice/Tool/Router Agent<br>• **Compose-first UI**：`AgentChatScreen` 直接落地<br>• **Dev/Prod 双模式 API Key**：开发期明文，生产期 Firebase AI Logic 代理<br>• 多 Agent 编排：Router + 串/并行 Pipeline<br>• 持久化会话（Room/DataStore） | • 早期预发布，API 不稳定<br>• 绑定 Google ADK + Gemini 生态，非通用 OpenAI 兼容<br>• 无工具审批/安全闸、无终端/写码门、无任务卡 |
| **looper42/mobile_agent** | - | - | 与 KYRIE66nb 定位相似，本地模型 + 工具调用 | 参考同类实现 | 信息较少 |
| **X-PLUG/MobileAgent** | - | - | **GUI Agent / Computer Use**，轨迹感知工具合成 + 在线 RL | 纯学术/研究向，非产品级客户端 | 非通用客户端 |
| **huggingface/chat-ui** | 30k+ ⭐ | Apache-2.0 | **Web 优先**，MCP 支持，Omni 路由，agentic route，Tiny Agent | • **Omni 路由**：按输入类型自动选模型/路由（多模态/工具/普通）<br>• **MCP 客户端内置**：工具调用流式回传，`Agent` 类封装循环<br>• **Agentic route**：需工具时自动走 agentic 路由<br>• LLM Router 策略：policy file + heuristic + tools model<br>• 虚拟模型别名「Omni」对用户透明 | • Web 技术栈，非原生移动端<br>• 无原生草稿/未读/多选/搜索<br>• 部署运维复杂（需后端服务） |
| **assistant-ui** | - | MIT | **React AI Chat 组件库**，类 shadcn/ui | • 组件化程度极高：`Thread`/`Message`/`Composer`/`ToolCall` 等原子组件<br>• 流式/工具/推理/分支/附件/引用等模式开箱即用 | • React/Web only，需自行桥接原生 |
| **chatboxai/chatbox** | 42k ⭐ | GPLv3 | **跨平台桌面+移动**，本地优先，Prompt 库，团队共享 | • **全平台**：Win/Mac/Linux + iOS/Android + Web<br>• **Prompt Library**：保存/复用/分享 Prompt<br>• **本地优先 + 隐私优先**，数据不出设备<br>• 多模型：OpenAI/Claude/Gemini/Ollama/ChatGLM<br>• 图片生成、Markdown/LaTeX/代码高亮、键盘快捷键 | • Electron/React Native 技术栈，非原生 Kotlin/Compose<br>• 定位「通用聊天客户端」，无智能体工具/审批/任务卡/终端<br>• GPLv3 传染性强，商业嵌入需慎重 |

---

## 🏢 二、商业级移动 AI 应用（交互基准杆）

| App | 核心交互亮点 | 智能体/工具能力 | 信息流组织 | 多端同步 | 我们可直接借鉴的交互细节 |
|-----|--------------|-----------------|------------|----------|--------------------------|
| **ChatGPT (官方)** | • Voice 对话（Whisper）<br>• **Branched chats**：从任意消息分叉新对话<br>• 相机输入 + 文件上传<br>• Siri/Shortcuts 集成<br>• 键盘快捷键、分享扩展 | • GPT-4o/4o-mini/o1<br>• **Codex**（Plus 订阅）<br>• 图片生成、文件分析<br>• 暂无显式工具审批 UI | • 历史会话列表 + 搜索<br>• **Branched chats** 可视化分支树<br>• 无置顶/文件夹/归档 | ✅ 跨端全同步（草稿/历史/设置） | • **Branched chats** 交互模式：长按消息 →「Fork 到这里」<br>• Voice 模式：按住说话 + 实时波形<br>• 分享扩展：从其他 App 直接发内容给 GPT |
| **Perplexity** | • **默认助手**（Android 可替换 Google Assistant）<br>• Voice 模式（iOS 18+ 动作键触发）<br>• **Focus Modes**：学术/写作/数学/视频/Reddit 等垂直检索<br>• **Deep Research**：自动多轮检索+综合报告<br>• **Spaces/Labs**：协作空间 + 实验功能 | • Pro Search（多模型聚合）<br>• 文件上传、多媒体引用<br>• **每条答案强制引用来源**（可点击跳转） | • **Thread follow-ups**：追问保持上下文<br>• **Spaces**：团队协作空间<br>• 历史可搜、可置顶 | ✅ 跨端同步（历史/设置/Spaces） | • **默认助手**集成：长按 Home 键 / 声音触发<br>• **来源引用**交互：答案内联编号，点击展开原文<br>• Focus Modes 单选框：一键切换检索策略 |
| **Claude (官方)** | • 长上下文（200k）<br>• Artifacts：代码/文档/网页实时预览<br>• Projects：知识库 + 指令集 | • 文件分析、代码执行（内置沙箱）<br>• 无显式工具审批 | • Projects 分组<br>• 会话搜索、置顶 | ✅ 跨端同步 | • **Artifacts** 侧边栏交互：代码/Markdown 实时渲染、一键复制/下载<br>• Projects：上传文件建立知识库，系统提示词隔离 |
| **Poe (Quora)** | • 多模型聚合：GPT/Claude/Gemini/Llama 等<br>• **Bot 创建/分享/变现**<br>• 多轮对话分支 | • 自定义 Bot = Prompt + 知识库 + 工具<br>• 官方 Bot 商店 | • Bot 列表 + 最近对话<br>• 会话分组 | ✅ 跨端同步 | • **Bot 卡片化**：头像+描述+调用量，一键开聊<br>• 创建 Bot 引导式向导：Prompt/知识/工具/发布 |
| **Microsoft Copilot** | • 融入 Microsoft 365 生态<br>• Notebook：长文本协作编辑<br>• 插件商店 | • Graph/Office 数据访问<br>• 插件 = 工具 | • 会话历史 + Notebook | ✅ 跨端同步（Entra ID） | • **Notebook** 交互：左侧对话，右侧长文本协作编辑<br>• 插件卡片化管理 |
| **Chatbox** | • 跨平台（桌面+移动+Web）<br>• **Prompt Library**：保存/分类/导入导出<br>• 本地优先、隐私优先<br>• 多语言（9 种） | • 仅聊天，无 Agent 工具 | • 会话列表 + 搜索<br>• Prompt 标签分类 | ✅ 跨端同步（可选云同步） | • **Prompt Library** 交互：分类树+搜索+一键插入<br>• 本地优先架构：SQLite + 加密存储 |

---

## 🎯 三、对比我们 v0.10.0 —— 核心差距热力图

| 维度 | 我们现状 | 电报/飞书/商业基线 | 差距等级 | 可直接照抄的开源实现 |
|------|----------|-------------------|----------|---------------------|
| **草稿** | ✅ 会话级本地持久化 | ✅ 云端同步 + 多端合并 | 🔴 云端同步缺失 | chatbox (本地 SQLite) / Telegram (云端草稿协议) |
| **回复/引用** | ✅ 左滑回复 + 气泡内引用块 | ✅ 长按回复 + 引用跳转 | 🟡 缺**点击引用跳转定位** | Telegram (点击引用定位) / chatbox (引用块) |
| **菜单触发** | ✅ 长按菜单，单击无反应 | ✅ 电报同款 | ✅ 对齐 | — |
| **未读计数** | ✅ 底部跳转键显示数字 | ✅ 电报：未读计数 + @提醒 | 🟡 缺 @提醒/免打扰/多端已读同步 | Telegram (未读协议) |
| **编辑/撤回** | ❌ 完全没有 | ✅ 电报/飞书/ChatGPT 均有 | 🔴 **硬伤** | 需服务端接口 |
| **会话内搜索** | ❌ 完全没有 | ✅ 全部有（高亮+跳转） | 🔴 **高频刚需** | chatbox (本地 FTS5) / Telegram (服务端搜索) |
| **多选/批量操作** | ❌ 完全没有 | ✅ 电报/飞书/微信均有 | 🔴 **高频刚需** | Telegram (长按进入选择模式) |
| **引用跳转定位** | ❌ 点引用不跳转 | ✅ 电报/飞书/Perplexity 均有 | 🔴 **核心断链** | Telegram (messageId 定位) |
| **日期分组** | ✅ DaySeparator 胶囊 | ✅ 电报/飞书同款 | ✅ 对齐 | — |
| **同一发送者分组** | ✅ 合并间距/单次头像/时间 | ✅ 电报同款 | ✅ 对齐 | — |
| **置顶/文件夹/归档** | ❌ 会话列表无 | ✅ 电报 5 个置顶 + 文件夹 / 飞书无限置顶 | 🟡 缺管理入口 | Telegram (置顶/文件夹协议) |
| **媒体/文件/链接聚合页** | ❌ 无 | ✅ 电报三标签 / 飞书知识库 | 🟡 资料型会话刚需 | Telegram (媒体/文件/链接三标签) |
| **消息折叠/代码块折叠** | ❌ 无限高 | ✅ 电报自动折叠 / 飞书代码块折叠 | 🟡 长回答可读性 | chatbox (代码块折叠) |
| **思考/工具过程可视化** | ✅ ThinkingTimeline 折叠容器 | ❌ 商业应用无此能力 | ✅ **我们领先** | — |
| **工具调用状态卡片** | ✅ ToolStepRow 状态流 | ❌ 商业应用无 | ✅ **我们领先** | KYRIE66nb (工具卡片流式) |
| **上下文用量实时条** | ✅ 动态已用/上限·剩余 | ❌ 商业应用无 | ✅ **我们领先** | — |
| **任务卡(计划/执行)** | ✅ 推送+轮询双保险 | ❌ 商业应用无 | ✅ **核心优势** | KYRIE66nb (操作模板/触发器) |
| **审批/澄清内联键盘** | ✅ Inline + 全局兜底 Dialog | ✅ Telegram Bot inline keyboard | ✅ 对齐 Telegram 模式 | Telegram Bot API / KYRIE66nb (DecisionGate) |
| **引用块(非 > 拼接)** | ✅ replyToNote 独立字段 | ✅ 电报/飞书/ChatGPT 同款 | ✅ 对齐 | — |
| **代码执行/终端** | ✅ Terminal + Write Gate | ❌ 商业应用无 | ✅ **独有** | KYRIE66nb (device_shell/文件编辑) |
| **模型/推理/上下文切换** | ✅ Composer toolbar | ❌ 商业应用无 | ✅ **独有** | — |
| **会话分叉 /fork** | ✅ | ✅ ChatGPT Branched chats | ✅ 对齐 | ChatGPT (分叉交互) |
| **技能/插件/MCP 管理** | ✅ 完整管理页 | ❌ 商业应用无 | ✅ **独有** | huggingface/chat-ui (MCP 管理) |
| **多模型对比/聚合** | ✅ /moa | ❌ 商业应用无 | ✅ **独有** | Poe (多模型聚合) |
| **草稿云端同步** | ❌ 仅本地 | ✅ 电报/飞书/商业均有 | 🔴 云端缺失 | 需服务端 |
| **已读多端同步** | ❌ 仅本地 | ✅ 电报/飞书/商业均有 | 🔴 云端缺失 | 需服务端 |
| **正在输入指示器** | ❌ 无 | ✅ 电报/飞书/商业均有 | 🟡 群聊场景需求 | Telegram (typing 事件) |
| **送达/已读三态 ACK** | ❌ 无 | ✅ 电报单勾/双勾/蓝双勾 | 🟡 群聊场景需求 | Telegram (ACK 协议) |

---

## 💡 四、我们的「护城河」与「必补短板」

### ✅ 已经形成护城河（智能体客户端差异化）
1. **思考/工具过程可视化** —— ThinkingTimeline 折叠容器，工具卡片流式状态
2. **任务卡实时推送** —— plan.updated 推送 + 10s 轮询兜底，执行状态实时同步
3. **审批内联键盘** —— InlineActionKeyboard 贴在请求消息下，模态 Dialog 兜底
4. **引用块独立字段** —— replyToNote 非 > 拼接，气泡内渲染引用块
5. **代码执行/终端/Write Gate** —— 真终端 + 写入审批，非 WebView
6. **多模型/推理/上下文切换** —— Composer toolbar 一键切
7. **会话分叉 /fork** —— 从任意消息分叉新会话
8. **技能/插件/MCP 管理页** —— 完整 CRUD + 导入导出
9. **上下文用量实时条** —— 动态已用/上限·剩余，usage.updated 实时推送
10. **多模型聚合 /moa** —— 单次调用多模型聚合回答

### 🔴 P0 必须补齐（决定能不能当主力客户端）
| 项目 | 原因 | 实现路径 | 参考实现 |
|------|------|----------|----------|
| **会话内全文搜索** | 每天查报错/日志/技术细节，无搜索=不可用 | 1) 本地 SQLite FTS5 兜底（仅索引已加载消息）<br>2) 并行推服务端 `/messages/search?q=` | chatbox (本地 FTS5) / Telegram (服务端搜索) |
| **多选/批量操作** | 长会话管理刚需，转发/删除/标记已读 | 长按进入选择模式 → 顶部工具栏（转发/删除/标记已读/转发到...） | Telegram (长按进入选择模式) |
| **引用消息点击跳转** | 代码/报错追踪核心路径，点引用不跳=断链 | messageId 定位 → listState.animateScrollToItem | Telegram (messageId 定位) |

### 🔴 需服务端配合的接口（客户端做不到）
| 接口 | 用途 | 优先级 |
|------|------|--------|
| `PUT/DELETE /messages/{msgId}` | 编辑/撤回消息 | P0 |
| `POST /sessions/{id}/read` + `read_receipt` 广播 | 已读同步 | P0 |
| `GET/PUT /sessions/{id}/draft` | 草稿云端同步 | P0 |
| `typing` 事件广播 | 正在输入指示器 | P1 |
| `message.sent/delivered/read` 三态 ACK | 送达/已读回执 | P1 |
| `POST /sessions/{id}/pin` | 置顶消息/会话 | P1 |
| `GET /messages/{msgId}/link` | 永久链接/锚点跳转 | P1 |
| `GET /sessions/{id}/messages/search?q=` | 会话内全文搜索 | P0 |
| `GET /sessions/{id}/media?type=` | 媒体/文件/链接聚合 | P1 |
| `session.list.changed` WS 推送 | 多端会话列表同步 | P1 |

---

## 📋 五、建议下一步行动清单

### 1. 纯客户端可落地的 P0（本周内可出 PR）
- [ ] **多选模式**：长按进入选择模式 → 顶部工具栏（转发/删除/标记已读/复制/转发到...）
- [ ] **引用跳转定位**：`messageId` 定位 → `listState.animateScrollToItem` + 高亮闪烁
- [ ] **会话内搜索（本地兜底）**：SQLite FTS5 索引已加载消息，搜索框实时过滤 + 高亮

### 2. 并行推服务端接口评审（需后端配合）
- [ ] 梳理上表 10 个接口规范 → 给后端开 Issue
- [ ] 先上 `messages/search` + `messages/{id}` (编辑/删) + `read_receipt`
- [ ] 草稿/已读同步协议设计（冲突解决：LWW + 向量时钟）

### 3. 中期补齐（下个迭代）
- [ ] 编辑/撤回消息（需服务端）
- [ ] 引用跳转定位（需 messageId 索引）
- [ ] 置顶消息/会话置顶/免打扰
- [ ] 媒体/文件/链接聚合页（三标签）

### 4. 长期护城河建设
- [ ] 正在输入指示器 + 三态 ACK
- [ ] 多端会话列表实时同步
- [ ] Branched chats 可视化分支树（参考 ChatGPT）
- [ ] Artifacts 侧边栏（参考 Claude）
- [ ] Bot/技能商店（参考 Poe/huggingface）

---

## 📚 六、必读源码清单（按价值排序）

| 优先级 | 仓库 | 必读文件/模块 | 学习目标 |
|--------|------|---------------|----------|
| **P0** | `KYRIE66nb/mobile-agent` | `agent-core/DecisionGate.kt` `device/RootDeviceService.kt` `desktop/AndroidUseBridge.kt` `tools/*` | 安全闸设计、双引擎设备控制、桌面桥协议、工具契约 |
| **P0** | `chatboxai/chatbox` | `src/shared/database/` `src/renderer/components/Chat/` `src/shared/services/promptLibrary.ts` | 本地 SQLite/FTS5、Prompt Library、跨平台架构 |
| **P0** | `huggingface/chat-ui` | `src/routes/api/chat/` `src/lib/server/mcp/` `src/components/Message/` | MCP 客户端、Agentic 路由、工具流式回传 |
| **P1** | `AI-Mobile-Coders/android-ai-agents` | `agents-compose/src/.../AgentChatScreen.kt` `agents-ready/src/.../ChatAgent.kt` | Compose 现成聊天屏、Ready-made Agent DSL |
| **P1** | `assistant-ui` | `packages/assistant-ui/src/components/` | 原子组件设计：Thread/Message/Composer/ToolCall/Reasoning |
| **P2** | `Telegram-FOSS-Team/Telegram-FOSS` (Android) | `MessagesController.java` `ChatListAdapter.java` `DraftController.java` | 草稿/未读/多选/搜索/置顶/文件夹的原生实现 |

---

## 📝 结语

**我们的定位很清晰**：自托管 Ekko Studio / Hermes Agent 的**原生 Android 客户端**，主打「思考可视化、工具可审批、任务卡实时、终端可写码、引用可跳转、分叉可复现」。

**优势已经很明确**：思考/工具/任务卡/上下文用量/审批内联/引用块/终端/多模型切换 —— 这些是**智能体客户端**的护城河，已领先通用 IM 和通用 AI 聊天客户端。

**短板也很明确**：基础 IM 交互的「最后一公里」——搜索、多选、跳转、编辑/撤回、多端同步。这些是**日常高频刚需**，直接决定能不能当主力客户端用。

**建议策略**：
1. **先把 P0 三件套（搜索、多选、跳转）补上** —— 纯客户端可做，立竿见影
2. **并行推服务端 10 个接口** —— 编辑/撤回/已读/草稿/搜索/置顶/聚合/同步
3. **再考虑高阶协作**（Branched chats、Artifacts、Bot 商店）

文档已输出至：`/root/.hermes/workspace/ekko-studio-android/docs/chat-gap-analysis-v0.10.0.md`  
可直接拿去开会对齐、分工、排期。