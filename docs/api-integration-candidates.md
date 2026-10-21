# 还能用上的接口：实测清单

> 基线：官方 0.7.38（529 个操作），客户端 v0.13.0
> 当前覆盖：**204 已用 / 211 缺口 / 114 不适用**（可达 49.2%）
> 方法：从 `docs/openapi/` 快照取缺口，再对本机服务端逐个发真实请求确认「存在且返回可用数据」

---

## 一、实测可用、且对手机端有价值（建议接入）

下列每一条都实测过状态码与响应结构，不是「文档里有」。

| 接口 | 实测 | 用途 | 价值 | 成本 |
|---|---|---|---|---|
| `GET /api/studio/sessions/{id}/workspace-run-changes` | 200，返回 `changes[]`（含 change_id、message_id、文件列表） | **展示这一轮 agent 改了哪些文件**，点开看 diff | ★★★★★ | 中（需新 UI） |
| `GET /api/hermes/write-gate/pending/{subsystem}/{id}/diff` | 路由存在 | **审批前先看 diff**，而不是盲批 | ★★★★★ | 中 |
| `GET /api/studio/sessions/{id}/context` | 200，返回 messages + title + 计数 | 「当前上下文里有什么」检查器 | ★★★★ | 低 |
| `GET /api/studio/sessions/{id}/usage` | 200，input/output/cache/reasoning tokens | 会话级 token 用量（现在只有全局 Insights） | ★★★ | 低 |
| `GET /api/hermes/mcp/tools` | 200，按 server 分组的工具清单 | MCP 页展示「这个服务提供了哪些工具」 | ★★★ | 低 |
| `GET /api/studio/sessions/count` | 200，`{"count":33}` | 会话列表头部显示总数 | ★★ | 极低 |
| `GET /api/studio/sessions/hermes/groups` | 200，按 source 分组的会话 | 会话列表分组（cli / webui / agent 分开） | ★★★ | 中 |
| `GET /api/agents/availability` | 200，revision + 每个 agent 的安装状态 | Agent Hub 显示可用性 | ★★ | 低 |
| `GET /api/studio/files/stat` | 200，name/path/isDir/size/modTime | 工作区文件详情（大小、修改时间） | ★★ | 极低 |
| `GET /api/hermes/config/auxiliary-models` | 200，任务列表（vision / web_extract …） | 显示「辅助模型」配置 | ★★ | 低 |
| `POST /api/studio/sessions/{id}/messages/batch-delete` | 文档有，未试（破坏性） | 配合「多选」批量删消息 | ★★★★ | 中 |
| `GET/POST /api/studio/sessions/{id}/shares` 等 7 个 | 文档有 | 只读分享链接 | ★★★ | 中 |
| `GET /api/studio/sessions/search` | 200（此前实测） | 会话搜索（现在用的是 `/api/studio/search/sessions`） | ★★ | 极低 |

### 两个我最想推的

**1. `workspace-run-changes`（agent 改了哪些文件）**
这是智能体客户端与普通聊天最本质的差别之一。现在用户在手机上只能看到「agent 说它改了文件」，
看不到**改了哪几个、改了什么**。接上之后，一条消息下面可以挂「3 个文件已修改」，点开是 diff。

**2. `write-gate/pending/{subsystem}/{id}/diff`**
我们已经做了「审批内联键盘」，但用户是在**不知道内容**的情况下点「允许」的。
接上 diff，审批才有意义——这是安全性的实质提升，不只是体验。

---

## 二、实测可用、但优先级低

| 接口 | 为什么不急 |
|---|---|
| `GET /api/hermes/runtime-versions/jobs` + download/activate（9 个） | 装/切运行时是桌面运维动作；手机端已有版本信息展示，缺的是「真去下载安装」的耐心场景 |
| `GET/POST /api/theme/presets`（7 个） | 主题预设需要一整套设计器才有意义；手机端改配色收益小 |
| `GET /api/hermes/config/{moa,fallback-providers,delegation-model}`（8 个） | 属于控制台配置，改错了会直接影响所有会话 |
| `GET /api/auth/status`（1 个） | 只用于登录页提示，可有可无 |
| Coding Agents 的 28 个 | 只有「运行/取消运行」对手机有用，其余是桌面配置；且该模块在本机处于「Node 未检测到」不可用状态 |
| Group Chat 的 57 个 | 群聊协作在单人自托管场景用得少；其中 `rooms/{id}/workspace-file/*`（10 个）与邀请码（2 个）值得做，其余是 agent-link 握手协议 |
| `GET /api/studio/workspace/{directories,folders}`（7 个） | 工作区浏览已可用，目录收藏是锦上添花 |

---

## 三、不建议接（保持 out-of-scope）

- MCU 硬件、固件烧写、JEV 浏览器自动化、媒体生成、语音本地流式模型
- 各 OAuth 设备流（Copilot/Codex/Anthropic/xAI/MiniMax/Nous）
- 社交平台（Telegram/飞书/微信）凭据与路由
- 官方 App 专用：push 注册、live-activities、mobile-calendar/health/location

理由已写在 `scripts/api_coverage.py` 的 `OUT_OF_SCOPE` 里，每条一句。

---

## 四、建议的接入顺序

1. **write-gate diff**（安全，改动集中在 WriteGateScreen）
2. **workspace-run-changes**（消息下方「N 个文件已修改」）
3. **session context / usage**（两个只读接口，加进会话详情）
4. **mcp/tools**（MCP 页展开工具清单）
5. 多选 + batch-delete（与已规划的「多选」一起做）

前两项做完，覆盖率约 **+2 个操作**，但对「敢不敢用手机审批」这件事影响最大。
