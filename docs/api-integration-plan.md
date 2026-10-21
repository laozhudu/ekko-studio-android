# 客户端 × 官方接口全面对接规划

> 基线：Ekko Studio **0.7.38**，529 个操作 / 56 个模块（快照见 `docs/openapi/`）
> 目标：**每一个官方操作，在客户端要么被使用，要么被明确标记「不适用」并写明理由。没有第三种状态。**
> 原则：不新增技术债——每一期都必须 CI 绿、有测试、不留死代码、不改动既有行为。

---

## 一、当前基线（`python3 scripts/api_coverage.py`）

| 指标 | 数值 |
|---|---|
| 官方操作总数 | **529** |
| 客户端已使用 | **311** |
| 明确不适用（有理由） | **108** |
| **缺口** | **110** |
| 可达覆盖率 | **73.9%**（311 / 421） |

### 缺口分布（前 10）

| 模块 | 已用 | 缺口 | 说明 |
|---|---|---|---|
| Coding Agents | 2 | 28 | 只做了安装，未做运行/预设/插件清单 |
| Group Chat | 40 | 23 | 房间/邀请/成员之外的能力未接 |
| Kanban | 12 | 15 | bulk / assignees / diagnostics / links 等 |
| Models | 7 | 7 | 自定义模型、别名、可见性、provider-models |
| Sessions | 52 | 7 | workspace directories / folders 管理 |
| Theme | 5 | 7 | 主题预设 CRUD + 导入导出 + 激活 |
| Auth | 12 | 5 | status / setup / app-login / mcu-login / 改密 |
| Skill Bundles | 0 | 3 | 技能包完全未接 |
| Session Shares | 4 | 3 | access / check / claim |
| 其余 | — | 13 | MCP reload/tools、webhook 测试、app-relay route 等 |

---

## 二、判定标准：什么算「不适用」

只有满足下列之一才允许标记 `out-of-scope`，并必须写入 `scripts/api_coverage.py` 的
`OUT_OF_SCOPE` / `OUT_OF_SCOPE_PATHS`，附带一句理由：

1. **硬件专属**：MCU/ESP32 配对与固件烧写。
2. **桌面专属**：Windows appdata 迁移、OpenAPI 文档自取。
3. **服务端运维**：健康探针、JEV 浏览器自动化、媒体生成代理。
4. **第三方 OAuth 设备流**：Copilot/Codex/Anthropic/xAI/MiniMax/Nous —— 属于控制台职责。
5. **社交平台路由**：Telegram/飞书/微信的绑定与凭据。
6. **平台自有扩展**：push / live-activities / mobile-calendar 等由官方 App 使用，自托管客户端无对应基础设施。

凡是「手机能做、只是没做」的，一律算**缺口**，不得标记不适用。

---

## 三、开发规范（本项目管理约定）

### 3.1 分层

```
HermesApi / ChatSocket     ← 只做 HTTP/WS 与 JSON → 模型，不含业务判断
AppViewModel               ← 状态与编排；UI 不可直接调 HermesApi
Compose screen (Screen)    ← 只读 UiState + 调 ViewModel
```

新增接口调用的固定动作：

1. 路径必须出现在 `docs/openapi/` 快照中；
2. 加进 `HermesApi`，返回**数据类**而不是 `JSONObject`；
3. 错误走既有 `call()` 抛出 `HermesException`，由 `launchWork` 统一转成 `UiState.error`；
4. 路径由 `ApiPathAlignmentTest` 守卫（未收录必须写白名单 + 理由）。

### 3.2 命名

- 官方路径 `/api/studio/*` 优先；`/api/hermes/*` 仅在官方只提供该形式时使用。
- 客户端方法名 = 资源 + 动作（`bundles()`、`createBundle()`、`deleteBundle()`）。
- 禁止在 UI 里拼路径字符串。

### 3.3 测试要求（每个新增能力必须带）

| 类型 | 要求 |
|---|---|
| 契约测试 | MockWebServer 验证**方法 + 路径 + 关键请求体字段**、响应解析 |
| 纯函数测试 | 状态映射/解析逻辑抽成纯函数后直接单测（参考 `messagesToLines`） |
| 覆盖率守卫 | 新增调用不能降低 `scripts/api_coverage.py` 的 used 数 |

### 3.4 完成定义（DoD）

- [ ] CI 绿（`assembleDebug` + 全部单测）
- [ ] 有对应 UI 入口，或用 `out-of-scope` 明确归档
- [ ] `docs/api-integration-plan.md` 的进度表更新
- [ ] 无 TODO/FIXME 残留，无静默 `runCatching`
- [ ] Release 包内验证（版本号 + 新符号）

---

## 四、分期

### 第一期：客户端可独立完成、收益明确（本轮）

| 能力 | 操作 | 入口 |
|---|---|---|
| 技能包（bundles） | `GET/POST /api/hermes/bundles`、`DELETE /{commandName}` | 斜杠命令面板新增「技能包」段 |
| MCP 重载与工具清单 | `POST /api/hermes/mcp/reload`、`GET /api/hermes/mcp/tools` | MCP 页面新增「重载」按钮 |

理由：两者都已有现成页面，接口语义清晰，无需服务端改动，能立刻减少 5 个缺口。

### 第二期：会话与工作区管理

`Sessions` 的 7 个缺口：`workspace/directories`（CRUD）、`workspace/folders`（增删改）。
工作区浏览页已有，补上「保存的目录 / 分组」即可闭环。

### 第三期：模型管理

`Models` 7 个：`custom-model`（增删改）、`provider-models`（列表/新增/刷新缓存）、
`model-alias`、`model-visibility`。模型设置页已有，扩展成完整 CRUD。

### 第四期：分享与主题

`Session Shares` 3 个（access/check/claim）提供「只读分享链接」；
`Theme` 7 个（预设 CRUD / 导入导出 / 激活）落到外观页。

### 第五期：群聊与看板补齐

`Group Chat` 23 个、`Kanban` 15 个。按「用户可见功能」而非「接口数量」排序：
优先邀请码、成员管理、任务批量操作；`diagnostics` 类内部接口标记不适用。

### 第六期：Coding Agents

28 个缺口里，运行/取消运行/预设属于产品能力，插件清单属于诊断。
先做「运行与取消」，其余按需。

---

## 五、验收

每期结束时：

```bash
python3 scripts/api_coverage.py          # 覆盖率必须上升
cd android && ./gradlew testDebugUnitTest # CI 同款
```

`ApiCoverageTest` 会读取 `docs/api-coverage-baseline.json`，**不允许 used 数下降**，
也不允许出现既非 used 又非 out-of-scope 的新条目。
