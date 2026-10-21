# 官方 APK 能拿来做什么参考

> 对象：Ekko Studio 官方安卓 App **1.0.8**（`com.ekkostudio.ai`，versionCode 165）
> 来源：`https://github.com/EKKOLearnAI/ekko-studio/releases/download/v1.0.8/EkkoStudio.apk`
> SHA-256：`4d7fc3217de2b86262874ba45102378475d1cbe4654afb84b2606234eeb4138e`

---

## 一、先说边界：能参考什么，不能抄什么

这个 App 是**闭源、厂商签名**的发布物。法律上它不是「可用素材库」，但**为互操作而研究**是常见做法。
本项目的自我约束：

| 做法 | 判断 | 理由 |
|---|---|---|
| 读它的 CSS/JS，弄清官方手机端**长什么样、调什么接口** | ✅ 可以做 | 事实与行为，属于互操作研究 |
| 把令牌**数值**（颜色/字号）抄进我们的调色板 | ✅ 可以做 | 单个颜色值不构成可版权表达；测试里也是逐值比对的 |
| 把接口**路径清单**整理成参考文件 | ✅ 可以做 | 只是一组 URL |
| 把它的 CSS 文件、JS 代码、页面模板**拷进本仓库** | ❌ 不做 | 本仓库是公开的，等于分发他人代码 |
| 把它的文案/图标/`thinking.gif` 等素材打包进 App | ❌ 不做 | 素材有明确版权 |
| 反编译后改名当作自己的实现 | ❌ 不做 | 与「参考」不是一回事 |

⚠️ **已有的一处风险，需要你知道**：当前启动图标是早期从 `dist/client/logo.png`（官方前端产物里的品牌图）
生成的。严格说那是官方商标素材。作为个人自用 App 没问题；但这个仓库是 public，如果要长期公开，
建议换成自己画的图标（我可以做一版风格接近但原创的）。**这一条由你决定，我不擅自改。**

---

## 二、APK 里到底有什么（实测）

```
assets/apps/__UNI__1F41684/www/
├── app.css              53 KB   ← 50 个 --ink-* 令牌（light/dark）
├── app-service.js      2.0 MB   ← 页面逻辑 + 全部接口调用
├── app-renderjs.js     430 KB   ← 渲染层
├── app-confusion.js    2.3 MB   ← 混淆层（uni-app 运行时）
├── pages/                        10 个页面
│   ├── index/          index.css 603 KB  ← 聊天主界面样式
│   ├── login/  login-devices/  devices/
│   ├── bootstrap/  about/  background-settings/
│   ├── change-password/  location-picker/  session-share/
└── static/                       图标、启动图、thinking.gif …
```

两个关键事实：

1. **官方手机端不是「外壳 + WebView」**，它自己实现了聊天 UI（`app-service.js` 里出现
   `message-bubble`、`task-plan-card`、`thinking-status-label`，`index.css` 603 KB）。
   所以它是「真正的移动端客户端」，和我们的定位一致。
2. **它用的是同一套设计语言**：`--ink-*` 50 个令牌、类名与网页 `/chat` 页一致。
   这印证了我们此前把 shimmer、任务卡样式对齐的方向是对的。

---

## 三、最有价值的产出：官方手机端的接口清单

`app-service.js` 里抽到 **92 个 `/api/...` 字面量**（去重后 68 个干净路径，清单见
`docs/reference/apk-1.0.8/api-endpoints.txt`）。按归属分成三组：

| 分组 | 数量 | 说明 |
|---|---|---|
| 自托管 Studio（`/api/studio/*`、`/api/hermes/*`） | 58 | **和我们相关**，是手机端该调的接口 |
| 厂商云端（`/api/app/*`、`/api/website/app/qr/*`） | 8 | 自托管实例上实测 **404**，直连客户端用不到 |
| 第三方语音（`/api/v3/*`、`/api/voice/*`） | 2 | 腾讯 TTS/ASR |

### 一个重要的认知修正

官方 App 的登录走 `/api/app/auth/*` + `/api/app/connections/claim`——那是**厂商云端**的账号与配对流程。
我们在自托管上用 `/api/auth/login` 是对的，**不需要**去模仿它的云流程。
`/api/app/*` 全组可以直接判定为「不适用」，这比笼统标 out-of-scope 更有依据。

### 官方手机端在用、我们没用的自托管接口（按价值排序）

| 接口 | 官方用它做什么 | 我们要不要接 |
|---|---|---|
| `GET/POST /api/studio/session-shares/{access,claim}` | 分享会话给他人只读查看 | ★★★ 值得 |
| `GET /api/studio/workspace/directories` | 工作区目录列表 | ★★★ 值得（工作区浏览已具备） |
| `GET /api/studio/usage/pricing` | 费用/单价展示 | ★★ 可选 |
| `GET /api/studio/models/api-keys`（+`/{id}`） | 模型 API Key 管理 | ★★ 可选 |
| `GET /api/studio/stt/local-model`（+download） | 下载**离线语音模型** | ★★ 有兴趣再做（体积大） |
| `POST /api/studio/push/registration` | FCM 推送注册 | ✕ 自建无 FCM |
| `POST /api/studio/live-activities/registration` | 灵动岛/锁屏活动 | ✕ iOS 专属 |
| `GET /api/hermes/runtime-versions/jobs` 等 4 个 | 运行时下载/切换 | ★★ 运维向 |
| `GET /api/hermes/mcp/tools` | MCP 工具清单 | ★★★ 值得（页面已有） |
| `GET /api/hermes/memory`、`/api/ekko/memory` | 记忆浏览 | ★★ 可选 |
| `GET /api/studio/jev/{settings,test}` | 浏览器自动化配置 | ✕ 手机无意义 |
| `POST /api/studio/group-chat/rooms/join/{code}`、`agent-presets`、`remote-resources` | 群聊加入/预设/远程资源 | ★★ 看是否用群聊 |

---

## 四、结论

**能参考，而且这次参考带来了两个实质收获**：

1. 确认了「官方手机端是原生客户端、与网页共用设计语言」，我们的对齐方向没跑偏；
2. 拿到官方自己的接口调用清单，把「哪些接口手机端该用」从猜测变成证据——尤其把
   `/api/app/*` 整组判为云端专属、自托管不需要。

**不能拿的**：CSS、JS、文案、图标、gif。参考行为可以，搬运素材不行。

---

## 五、复现方式

```bash
# 1. 取最新版地址（厂商目录）
curl -s https://api.ekkostudio.xyz/api/studio/versions \
  | python3 -c 'import json,sys;print(json.load(sys.stdin)["mobile"]["channels"]["androidApk"]["githubUrl"])'

# 2. 下载后解包
unzip -q EkkoStudio.apk -d apk && cd apk/assets/apps/__UNI__*/www

# 3. 令牌（已有脚本）
python3 scripts/extract_apk_tokens.py EkkoStudio.apk docs/reference/apk-<ver>

# 4. 接口清单
grep -o '/api/[A-Za-z0-9/_.${}-]*' app-service.js | sort -u
```
