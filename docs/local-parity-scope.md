# HStudio Mobile Local Parity Scope

目标：复刻 HStudio App 的本地/直连 UI 与功能；不复刻官方云服务及其依赖功能。

## 保留并复刻

- 登录与本地 Studio 连接
- Devices：已保存 Studio、本地配对、手动添加、改名、连接状态
- Chats：会话列表、会话搜索、聊天、Markdown、思考过程、工具调用、队列、附件、语音
- History：历史会话与导出
- Group Chat：本地群聊和房间管理（仅保留实际可直连部分）
- Files：文件树、文件列表、编辑器、文本/HTML/PDF/Office 预览、差异预览
- Terminal / Browser：本地 Studio 提供能力时显示并调用
- Profiles / Models：本地配置文件、模型选择和提供商配置
- Skills / Plugins / MCP：本地管理、导入、编辑和启停
- Jobs / Workflows：定时任务、工作流编辑和运行
- Kanban：看板、任务、评论/运行状态（通过本地 API）
- Logs / Usage / Performance / Journey / Skills Usage
- Ekko Hub：本地 Memory、Skills、MCP 管理
- Settings：账号、本地服务器、Profiles、Models、Agent、Memory、Compression、Sessions、Privacy、Proxy、Display、Device、About
- Theme / Appearance：亮暗主题、字体、颜色、背景
- Languages：现有中文、英文、阿拉伯语资源

## 排除或隐藏

- 官方云账号登录与订阅
- entitlement / subscription / plan / cloud account
- 官方 cloud relay 与 Cloudflare relay
- 云授权码、云二维码、云设备发现
- 依赖官方云后台的社交同步与远程授权
- 不能通过当前本地 Studio API 完成的云端按钮

## 实现原则

1. 本地直连功能必须调用当前 Studio 的真实 API，不能只做静态假页面。
2. 云相关内容默认不显示，不把 disabled 云按钮当成已复刻功能。
3. 页面 UI 按官方 v0.7.13 的组件结构、文字层级、间距、圆角、颜色和交互组织复刻。
4. 每完成一个功能域，执行 Kotlin 编译/单元测试，并在平板上验证入口和核心交互。
5. 不改变已验证的登录、设备添加、聊天连接逻辑。
