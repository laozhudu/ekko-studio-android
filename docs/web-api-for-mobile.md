# 网页端接口能否给手机端用？——实测结论

> 环境：Ekko Studio 0.7.38（webui 0.7.38 / hermes 0.21.7），Android 客户端 v0.12.0
> 方法：直接对运行中的服务端发请求 + 抓 `/chat-run` socket 的真实帧 + 读 `dist/client` 与 `dist/server` 产物

## 一句话结论

**能，而且不是“勉强能用”——网页端用的就是同一套 HTTP + Socket.IO 接口，同一枚 Bearer token，没有任何 web-only 的旁路。**

## 逐项验证

### 1. 认证：没有只给浏览器留的口子

| 检查 | 结果 |
|---|---|
| 带 token 访问 | 200 |
| 不带 token 访问 `/api/studio/sessions` | **401 Unauthorized** |
| 不带 token 访问 `/api/hermes/sessions` | **401 Unauthorized** |
| 不带 token 访问 `/api/openapi.json` | **200（公开）** |
| 客户端里是否有 CSRF / `X-Requested-With` / `credentials:"include"` | **一处都没有** |

所以认证模型是纯 Bearer token，手机端与浏览器完全对等。副作用：`/api/openapi.json` 公开可取，客户端**可以在运行时自检规范漂移**（后续可做）。

### 2. 流式与“思考折叠”：数据和网页端同源

思考内容有两条来源，网页端两条都用，我们之前只用了一条：

| 场景 | 来源 | 字段/事件 | 我们 v0.11.x | 本版 |
|---|---|---|---|---|
| 正在输出 | socket | `thinking.delta` / `reasoning.delta`（`text`） | ✅ 已用 | ✅ |
| **重载历史** | REST | 消息行上的 `reasoning` / **`reasoning_content`** | ❌ 丢弃 | ✅ 已接 |
| 思考就绪信号 | socket | `reasoning.available` | ❌ 未订阅 | 未订阅（内容已由 run.completed 带回，暂不需要） |
| 工具调用 | REST | `role:"tool"` + `tool_name` + `content` | ❌ 当成气泡渲染 | ✅ 折叠进过程块 |
| 工具调用 | socket | `tool.started` / `tool.completed` / `tool.failed` | ✅ 已用 | ✅ |
| 任务计划 | REST + socket | `taskPlans`（历史）/ `plan.updated`（实时） | ✅ | ✅ |

**关键点**：浏览器刷新页面后思考块还在，靠的就是历史行里的 `reasoning_content`。我们之前只渲染流式 thinking，所以**一重载就消失**——这不是“接口不给”，是客户端没读。本版已修。

### 3. 工具行为什么会让聊天显得乱

历史里工具行长这样：

```json
{"role":"tool","tool_name":"terminal","content":"{\"output\":\"PASS …\"}"}
```

旧实现把每一条都当成 agent 气泡渲染，因此聊天里会出现大段原始 JSON。本版按网页端的做法映射：

- `user` → 自己气泡
- `assistant` → 气泡 + 折叠思考 + 它用过的工具步骤
- `tool` → **折叠成上一条回复里的步骤**，不单独成为气泡
- `command` → 系统提示（如“Branched session …”），不进对话流

### 4. 网页端在用、我们尚未接的事件（已核对名称）

服务端对会话转发的是白名单事件；`subagent.*` 那一组是**新版 bridge 的名字，0.7.38 实际只发 `subagent.event`**（我一开始按网页 bundle 里的名字订阅，核对后改正）。

| 事件 | 语义 | 本版 |
|---|---|---|
| `session.title.updated` | agent 自动命名/改名 | ✅ 已接：标题即时跟随 |
| `compression.started/completed` | 上下文压缩 | ✅ 已接：显示“正在压缩上下文” |
| `subagent.event` | 后台子任务（单一事件，载荷带 type） | ✅ 已接：作为通知显示 |
| `run.reattach_failed` | 断线后恢复失败 | ✅ 已接：提示而不是一直转圈 |
| `reasoning.available` | 思考已落库 | 未接（内容随 run.completed 到达） |
| `moa.reference/aggregating` | 多模型聚合中间态 | 未接（仅 `/moa` 用） |
| `workspace.diff.completed` | 工作区改动 | 未接（历史里有 `workspaceRunChanges`） |
| `message.interim` | 语音/中间态文本 | 未接 |
| `peer.user.message` | 多端同步的他人消息 | 未接（单人使用） |

### 5. 明确做不到的（服务端没有，不是客户端偷懒）

以下在 529 条操作里搜过，确实不存在：

- 消息编辑 / 撤回（无 `PUT|DELETE /messages/{id}`）
- 已读回执（无 `read` / `receipt` 端点）
- 草稿云端同步（`draft` 命中 0）
- 正在输入（`typing` 命中 0）
- 消息级全文搜索（只有会话级 `search/sessions`，返回会话对象）

## 给后续开发的判断规则

1. 只要网页端能做，手机端就能做——**先去看 `dist/client` 用了哪个接口/事件，再去 `openapi.json` 核对**，两步都能验证。
2. 判断某能力是否可行，看**历史接口是否带这个字段**：网页端刷新后还能显示的东西，一定在历史响应里，不可能只存在于 socket。
3. `subagent.*` 这类名字不要照抄前端 bundle：前端可能比当前服务端新，以服务端实际转发的事件名为准。
