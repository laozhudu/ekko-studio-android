# 技术债与接口掌握度审计（v0.10.1，服务端 0.7.38）

审计方式：本地代码静态扫描 + 从运行中的服务端拉取 `/api/openapi.json`（438 路径 / 529 操作 / 56 模块）做真实比对，关键路径用带 token 的 curl 实测。所有结论都有出处，不靠印象。

---

## 一、接口掌握度：约 28%，且有三类结构性问题

| 指标 | 数值 |
|---|---|
| 规范里的操作总数 | **529**（438 路径，56 模块） |
| 客户端引用的不同路径 | 178（其中 `/api/hermes/*` 84 个、`/api/studio/*` 51 个、其它 43 个） |
| 能在规范里定位并实际使用的 | **约 150 路径 / 28%** |
| 服务端有、客户端完全没碰 | 294 路径 |

### 1.1 各模块覆盖（已用 / 总数）

| 模块 | 覆盖 | 判断 |
|---|---|---|
| Update | 7/7 | 完整 |
| Ekko MCP / Logs / Weixin / Journey / Performance / Terminal | 全中 | 完整 |
| Workflows 14/21、Studio Files 11/16、Profiles 10/15、Jobs 7/11、Ekko Skills 7/11、Skills 6/11 | 高 | 够用 |
| **Sessions 19/61** | 31% | **偏低，聊天主战场** |
| **Chat Run 0/6** | 0% | **兜底路径是死的** |
| **Group Chat 3/63** | 5% | 群聊只做了最基础 |
| **Coding Agents 2/30** | 7% | 只做了安装，没做运行/预设 |
| **Kanban 13/27** | 48% | 缺 bulk/assignees/diagnostics |
| Social Messages 0/14、Model API 0/9、Session Shares 0/7、JEV 0/7、Media 0/3 | 0% | 有意不做，但 Session Shares / Media 是产品缺口 |
| OAuth 五家（Copilot/Codex/Anthropic/xAI/MiniMax/Nous）0/17 | 0% | 全部未做，只能用已有 key |
| STT 3/17、TTS 1/13 | 低 | 只用了录音转写/朗读入口 |
| Theme 2/12 | 低 | 主题预设未做 |

### 1.2 问题一：客户端在调、服务端返回 404 的死路径

实测（带 token，方法正确）：

| 路径 | 结果 | 影响 |
|---|---|---|
| `GET /api/devices` | **404** | `studioDevices()` 抛错 → **「连接」页打不开/报错** |
| `POST /api/devices/{id}/{action}` | **404** | 设备操作按钮全废 |
| `GET /api/devices/pairing-link` | **404** | 配对链接永远空 |
| `POST /api/devices/manual-request` | **404** | 手动添加设备不可用 |
| `GET /api/devices/peer-connections` | **404** | 局域网对端列表永远空 |
| `POST /api/devices/peer-connections/{id}/disconnect` | **404** | 断开按钮无效 |
| `POST /api/chat-run/runs` | **404** | **WebSocket 失败时的 REST 兜底从来没生效过** |

`/api/devices*` 这组是**按旧版 API 写的**（上游 fork v1.9.7 时代的接口），0.7.38 里已经不存在；现存的是 `/api/mcu-devices`（MCU 硬件）和 `/api/devices/link-info`。

`/api/chat-run/runs` 的正确路径是 `/api/studio/chat-run/runs`（实测存在，缺 `input` 时返回 400 而非 404）。**已在本次提交修正。**

### 1.3 问题二：84 处调用依赖未文档化的别名

规范只列 `/api/studio/*`，但客户端 84 处用的是 `/api/hermes/*`。实测两者是同一套兼容层（例如 `/api/hermes/sessions/...` 与 `/api/studio/sessions/...` 都通），**目前可用**，但：

- 规范不覆盖 → 无法从文档验证字段，只能靠试探
- 上游哪天收窄别名，这些调用会成批失效，且没有 CI 能提前发现

**建议**：新代码一律写 `/api/studio/*`；老代码在下次触碰该模块时顺手迁移。

### 1.4 问题三：有更好的接口，我们用了差的

| 我们在用 | 应该用 | 差异 |
|---|---|---|
| `GET /sessions/conversations/{id}/messages`（全量） | `GET .../messages/paginated?limit=&offset=` | 实测：全量返回 **572 条**人类消息；分页返回 `limit/offset/total/hasMore`，**并且顺带返回 `taskPlans` 和 `workspaceRunChanges`** |
| `PlanSocket` 每 10 秒 resume 轮询任务卡 | 上面那个 `taskPlans` | 现在为任务卡额外开了一条 socket，纯浪费 |
| 自己拼 `/messages` 的 `humanOnly` | 分页接口自带 `total` | 无法做「加载更早」 |

这是**最值得马上改的一条**：长会话首屏慢、内存高、没有增量加载，而且白开了一条 socket。

### 1.5 契约误读一处（不是 bug，但要讲清）

`/messages` 的响应顶层只有 `session_id / messages / visible_count / thread_session_count`，**没有 `contextTokens`**。我们 `conversationHistory()` 里读 `contextTokens` 永远拿到 null，所以打开历史会话时上下文一律显示 `—`。

真实来源只有两个：
- 实时：socket `usage.updated.contextTokens`（已接）
- 单次增量：`GET /sessions/{id}/usage` 的 `input_tokens`（**不等于**上下文占用，不能拿来顶替）

结论：`—` 是诚实的显示，不该改成假数。

### 1.6 服务端确实没有的能力（不是我们没做）

已逐条在规范里搜过：

- 消息编辑 / 撤回：无 `PUT|DELETE /messages/{id}`
- 已读回执：无 `read` / `receipt` 类端点
- 草稿同步：`draft` 命中 **0**
- 正在输入：`typing` 命中 **0**
- 消息级全文搜索：只有 `/studio/search/sessions` 和 `/studio/sessions/search`，返回的是**会话对象**，不是消息命中

所以上一轮差距分析里那几项「需服务端配合」的判断成立。

---

## 二、技术债

### 2.1 规模与结构

| 项 | 数值 |
|---|---|
| Kotlin 总行数 | **24,188** |
| `MainActivity.kt` | 6,115（单文件，含 30+ 个 Composable） |
| `AppViewModel.kt` | 4,560（单个类，~150 个方法） |
| `HermesApi.kt` | 2,559 |
| `DevicesScreen.kt` | 1,847 |

### 2.2 超长函数（>200 行）

| 函数 | 行数 | 风险 |
|---|---|---|
| `StudioUpdateScreen` | 380 | 更新中心：进度、日志、确认、预览全塞一个函数 |
| `Composer` | 357 | 输入框 + 工具栏 + 3 个 sheet + 录音状态 |
| `ConversationScreen` | 309 | 列表 + 滚动 + 未读 + 内联键盘 + 手势 |
| `SettingsScreen` | 240 | — |
| `AgentHubScreen` | 236 | — |
| `AgentSettings` | 210 | — |
| `ChannelScreen` | 206 | — |

### 2.3 错误处理：81 处静默吞异常

`runCatching { }` 共 177 处，其中 **81 处既没有 `onFailure` 也没有 `onSuccess`** —— 失败后什么都不发生。这正是「连接页空白」「某些按钮没反应」的通用成因：错误被吃掉，UI 只能表现为无响应。

典型位置：`AgentToolScreens.kt:96/537`、`MainActivity.kt:266/374/700/4179/4185/5455`、`AppViewModel.kt:576/658`。

另有 47 处 `getOrNull()/getOrDefault()` 兜底、2 处 `!!`（后者风险最高，但数量可控）。

### 2.4 测试：数量够，但结构脆弱

- 21 个测试文件、**143 个测试**
- 其中 **16 个文件是「读源码文本做字符串断言」**（`File("src/main/java/...").readText().contains(...)`）
- **0 个 UI / 仪器化测试**，**0 个针对真实响应的契约测试**（`HermesApiContractTest` 也是文本断言）

后果已经在今天显现：今天连续 3 次 CI 红，全部是「实现改了、文本断言没跟上」，而**没有一次是真实行为回归**。这类测试既挡不住 bug，又拖慢每次改动。

### 2.5 死代码

- `DevicesScreen.kt` 依赖的 6 个 `/api/devices*` 方法全部指向 404
- `sendMessage()`（REST 兜底）指向 404（本次已修）
- `PlanSocket` 在分页接口已提供 `taskPlans` 之后属于重复机制

### 2.6 已经做对的地方（不用改）

- 没有 `TODO/FIXME/HACK` 残留（0 处）
- 跨文件重复函数名只有 3 个，且都是 `DesignSystem.kt` 的局部重载
- `!!` 只有 2 处
- 网络层统一走 `HermesApi.call()`，没有散落的 OkHttp 调用
- 三语文案齐备，`TranslationsTest` 有覆盖

---

## 三、修复优先级

### P0（本周，纯客户端，风险低）

1. **改用 `messages/paginated`**：首屏只取最近 50 条，上滑加载更早；同时接管 `taskPlans`，删掉 `PlanSocket` 轮询
2. **修 `/api/devices*`**：要么改用 0.7.38 真实端点，要么把「连接」页里这几块**明确隐藏**（现在是最糟的第三种：显示但永远空/报错）
3. **81 处静默 `runCatching` 逐批加 `onFailure`**：至少让失败可见（Toast/错误条），先从聊天、连接、更新三条主链路开始

### P1（需要服务端配合）

4. 消息编辑/撤回、已读回执、草稿同步、typing —— 规范里确认不存在，需服务端立项
5. 消息级搜索（现在只有会话级）

### P2（结构治理）

6. 拆 `MainActivity.kt`：把 `ConversationScreen`、`Composer`、`AgentHubScreen`、`SettingsScreen` 拆成独立文件
7. 把 16 个「文本断言」测试改写成行为测试（输入 → 期望输出），或至少收敛到 3~4 个文件
8. 老 `/api/hermes/*` 调用逐步迁到 `/api/studio/*`

---

## 四、一句话结论

**接口没有摸透**：529 个操作只用了约 28%，而且用的那部分里还混着 7 条 404 死路径、84 处未文档化别名、1 处响应字段误读、1 个更优接口没用。

**技术债是真实存在的**，但集中在三处可治理的地方：错误被静默吞掉（81 处）、测试只断言源码文本（16 个文件）、三个巨型函数（357/380/309 行）。没有 TODO 堆积，也没有架构性错误——是「能跑但不够结实」的那种债，按上面的顺序清，两周能明显改善。


---

## 附：v0.11.0 清理后复核（本文件部分结论已更新）

上面的审计有两处数字需要更正，均已用更严格的脚本重算：

1. **静默吞异常不是 81 处。** 用括号配对重新扫描（而不是看 `runCatching` 后 400 字符内有没有 `onFailure`），真正没有错误处理的只有 **11 处**，其中 9 处是刻意的（Logo 同步、MediaPlayer 释放、并行可选探测、房间错误已由 `RoomEvent.Failed` 上报）。剩下 1 处已改为 Toast 提示。
2. **`/api/devices*` 不是"少用了几个端点"，是整个界面都建在 404 上。** 该页不仅调用了 6 个不存在的路由，而且**从未加载过** `/api/app-connections`，所以只能打开成空白 + 报错。

已完成的清理（v0.11.0）：

| 项 | 处理 |
|---|---|
| 历史全量加载 | 改用 `messages/paginated`，首屏 50 条，`loadOlderMessages()` 回填 |
| 任务卡轮询 socket | `PlanSocket` 删除；分页响应与 `plan.updated` 已覆盖 |
| `/api/devices*` 6 条 404 | 全部删除，页面改建在 `app-connections` + `authorization-codes/{lan,cloud}` + `app-relay/status` |
| REST 兜底 404 | `/api/chat-run/runs` → `/api/studio/chat-run/runs` |
| contextTokens 误读 | 该字段在 `/messages` 中不存在，改为返回 null（UI 显示 `—`） |
| 连接页从未加载真实数据 | `refreshConnections()` 接上真实接口，补了 LAN/云配对二维码 |

仍未处理（需要服务端，或属于结构治理）：消息编辑/撤回、已读回执、草稿同步、typing、消息级搜索；`MainActivity.kt` 拆分；16 个源码文本断言测试转行为测试；剩余 `/api/hermes/*` 调用逐步迁 `/api/studio/*`（新代码已一律使用官方路径）。


---

## v0.11.1：路径对齐结果（并更正一处结论）

### 更正："84 处未文档化别名"这个说法是错的

导出的 `openapi.json` 里 `/api/hermes/*` 有 **127 条**、`/api/studio/*` 有 **213 条**、其它 98 条。也就是说绝大多数 `/api/hermes/*` 调用**本来就有文档**，不是"未文档化别名"。重新精确统计：

| 指标 | 数值 |
|---|---|
| 客户端不同调用路径 | 173 |
| 在规范中可定位 | **150** |
| 真正未文档化 | 23 |

那 23 条里有 16 条是"只以 `/api/studio` 形式存在"的路径，属于应当迁移的，已在本版处理。

### 已迁移（每条都先用真实请求验证过）

`GET` 返回 200、`POST` 返回 400（缺参数）即证明路由存在：

```
sessions/context-length      sessions/{id}/model        usage/stats
performance/runtime          petdex/manifest           pets/active
pets/adopt                   stt/transcribe            stt/settings
stt/profile-status           tts/synthesize            group-chat/rooms(+/{id}, +/agents)
files/download
```

`sessions/{id}/model` 与 `context-length` 保留 `/api/hermes` 作为第二次尝试，旧服务器仍可用。

### 新增的防漂移测试

`ApiPathAlignmentTest` 把 529 条官方路径固化到 `src/test/resources/openapi-paths.txt`，任何 `/api` 字面量若既不在其中、也不在白名单里（白名单每条都要写理由），CI 直接红。

- **静态路径**逐字符比对 —— 这正是能挡住 `/api/devices/*`、`/api/chat-run/runs` 的检查
- **动态路径**（`kanban$suffix`、`jobs/${id}/$action`）按模块级比对，因为字面量只有运行时才成型

同时新增 `noCommentLineOpensANestedBlockComment`：Kotlin 块注释可嵌套，注释里写 `/api/devices/*` 会吞掉整个文件。这个坑今天踩了三次，现在有测试兜底。

### 仍未做（明确记录，不假装完成）

- **`MainActivity.kt` 拆分**（6,115 行）：机械搬运 + 大量 private 辅助函数要改可见性，属于"高风险、零用户可见收益"，本版**刻意不做**。建议单独一轮、只做搬运、不夹带功能改动。
- 16 个"读源码字符串"的测试：本版新增了契约式与守卫式测试，但存量文本断言未批量改写。
- 服务端确无：消息编辑/撤回、已读回执、草稿同步、typing、消息级搜索。
