#!/usr/bin/env python3
"""Build the official client's module inventory from the shipped APK.

The official Android client is a uni-app bundle: one page (`index`) holds the
whole application and the rest are small standalone screens. Its stylesheet is
therefore a usable table of contents — every feature area has a class prefix
(`.session-*`, `.workspace-*`, `.mcp-*`, ...), and a prefix that exists in the
stylesheet but not in our source is a concrete, checkable gap.

This script never copies stylesheet text into the repository. It counts class
prefixes and greps for the module's own strings, then emits numbers.

Usage:
    python3 scripts/official_module_inventory.py --apk /tmp/EkkoStudio.apk

Writes docs/reference/apk-1.0.8/module-inventory.{json,md}
"""

import argparse
import collections
import json
import os
import re
import sys
import zipfile

ASSET_ROOT = "assets/apps/__UNI__1F41684/www"

# area -> [(module id, css prefix or None, human name, source glob keywords)]
# `keywords` are matched against our own Kotlin sources to decide "have/partial".
MODULES = [
    ("shell", "login", None, "登录/服务器地址", ["LoginScreen", "auth/login"]),
    ("shell", "devices", None, "已保存设备", ["DevicesScreen", "app-connections"]),
    ("shell", "bootstrap", None, "首次连接引导", ["Onboarding", "bootstrap"]),
    ("shell", "about", None, "关于/条款/检查更新", ["About", "StudioUpdate"]),
    ("shell", "network-mode", "network", "局域网/P2P/云端模式", ["NetworkMode", "p2p"]),

    ("chat", "drawer", "drawer", "会话抽屉/账户菜单", ["DrawerContent", "ModalNavigationDrawer"]),
    ("chat", "session", "session", "会话列表/分类/文件夹/搜索", ["SessionRow", "sessionCategor", "sessionSearch"]),
    ("chat", "new-chat", "new", "新建会话/分类", ["startNewConversation", "new-chat"]),
    ("chat", "composer", "composer", "输入区/附件/工具条", ["Composer(", "composer"]),
    ("chat", "input-context", "input", "上下文计量", ["ContextUsage", "input-context"]),
    ("chat", "messages", None, "消息气泡/元信息", ["MessageBubble"]),
    ("chat", "markdown", "markdown", "Markdown/图片/链接卡", ["ChatMarkdown"]),
    ("chat", "tool", "tool", "工具调用卡片", ["ToolStepRow", "tool-call"]),
    ("chat", "file-preview", "file-preview", "文件预览", ["ChatFileCard", "file-preview"]),
    ("chat", "voice", "voice", "语音录制/朗读", ["Recorder", "voice"]),
    ("chat", "usage", "usage", "用量/缓存/成本", ["UsageStats", "usage"]),
    ("chat", "session-share", None, "会话分享", ["share"]),

    ("group", "group", "group", "群聊/群成员/头像", ["GroupsScreen", "group-agent"]),
    ("group", "presets", None, "智能体预设", ["agentPreset", "AgentPreset"]),

    ("work", "workspace", "workspace", "工作区浏览器", ["WorkspaceScreen", "workspace"]),
    ("work", "terminal", "terminal", "终端", ["TerminalClient", "TerminalScreen"]),
    ("work", "workflow", "workflow", "工作流编排", ["WorkflowsScreen", "workflow"]),
    ("work", "edge", "edge", "工作流连线/条件", ["edge"]),

    ("agent", "agent", "agent", "智能体徽标/运行态", ["AgentHub", "agent-badge"]),
    ("agent", "skills", "skills", "技能库/编辑", ["SkillsScreen", "skills"]),
    ("agent", "skill-detail", "skill-", "技能详情", ["SkillScreen"]),
    ("agent", "bundles", "bundle", "技能包", ["bundle"]),
    ("agent", "mcp", "mcp", "MCP 服务", ["McpScreen", "mcp"]),
    ("agent", "coding", "coding", "编码代理配置", ["codingAgent"]),
    ("agent", "aux", "aux", "辅助模型/委派", ["auxiliary"]),
    ("agent", "moa", "moa", "MoA 混合智能体", ["moa"]),
    ("agent", "pets", None, "宠物/吉祥物", ["Pets"]),
    ("agent", "journey", None, "技能旅程图", ["Journey"]),

    ("auto", "jobs", "job", "定时任务", ["CronJob"]),
    ("auto", "kanban", None, "看板", ["Kanban"]),
    ("auto", "webhooks", "webhook", "Webhook", ["Webhook"]),
    ("auto", "channels", None, "消息渠道/微信", ["Channel"]),

    ("model", "model", "model", "模型面板/API 协议", ["ModelSettings", "model-"]),
    ("model", "provider", "provider", "Provider 授权", ["provider"]),
    ("model", "api", "api-", "API 地址/Key", ["ApiKeys", "api-key"]),
    ("model", "hermes", "hermes", "Hermes 配置", ["hermes/config", "HermesConfig"]),

    ("obs", "performance", "performance", "性能面板", ["performance"]),
    ("obs", "logs", "log", "日志", ["LogsScreen", "log-"]),
    ("obs", "health", "active-health", "健康检查", ["health"]),
    ("obs", "insights", None, "洞察", ["Insights"]),

    ("look", "theme", "theme", "主题/字号/颜色", ["AppearanceScreen", "theme-"]),
    ("look", "background", None, "聊天背景图", ["background"]),
    ("look", "inbox", "inbox", "通知收件箱", ["inbox"]),
    ("look", "location", None, "位置发送", ["location"]),
    ("look", "search", "search", "全局搜索", ["sessionSearch", "StudioSearchField"]),
]


def load(apk_path):
    z = zipfile.ZipFile(apk_path)
    css_parts = []
    for name in z.namelist():
        if name.startswith(ASSET_ROOT + "/pages/") and name.endswith(".css"):
            css_parts.append(z.read(name).decode("utf-8", "replace"))
    css = "\n".join(css_parts)
    service = z.read(ASSET_ROOT + "/app-service.js").decode("utf-8", "replace")
    manifest = json.loads(z.read(ASSET_ROOT + "/manifest.json"))
    pages = sorted({n.split("/pages/")[1].split("/")[0] for n in z.namelist() if "/pages/" in n})
    return z, css, service, manifest, pages


def classes_by_prefix(css):
    counter = collections.Counter()
    for match in re.finditer(r"\.([a-zA-Z][a-zA-Z0-9-]{2,60})", css):
        counter[match.group(1).split("-")[0]] += 1
    return counter


def sources(root):
    out = {}
    for base, _dirs, files in os.walk(root):
        for f in files:
            if f.endswith(".kt"):
                p = os.path.join(base, f)
                out[p] = open(p, encoding="utf-8", errors="replace").read()
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--apk", required=True)
    ap.add_argument("--src", default="android/app/src/main")
    ap.add_argument("--out", default="docs/reference/apk-1.0.8")
    args = ap.parse_args()

    _z, css, service, manifest, pages = load(args.apk)
    prefixes = classes_by_prefix(css)
    src = sources(args.src)

    rows = []
    for area, module, prefix, label, keywords in MODULES:
        css_classes = prefixes.get(prefix, 0) if prefix else 0
        hits = []
        for path, text in src.items():
            if any(k in text for k in keywords):
                hits.append(os.path.basename(path))
        rows.append(
            {
                "area": area,
                "module": module,
                "label": label,
                "css_prefix": prefix,
                "official_css_classes": css_classes,
                "official_present": css_classes > 0 or module in pages,
                "our_files": sorted(set(hits)),
                "status": "have" if hits else "missing",
            }
        )

    os.makedirs(args.out, exist_ok=True)
    payload = {
        "apk": os.path.basename(args.apk),
        "package": manifest.get("id"),
        "name": manifest.get("name"),
        "pages": pages,
        "permissions": sorted(manifest.get("permissions", {}).keys()),
        "modules": rows,
    }
    with open(os.path.join(args.out, "module-inventory.json"), "w", encoding="utf-8") as fh:
        json.dump(payload, fh, ensure_ascii=False, indent=2)

    lines = [
        "# Official module inventory",
        "",
        f"Generated by `scripts/official_module_inventory.py` from `{os.path.basename(args.apk)}`.",
        "Counts are stylesheet class prefixes and source-file matches; no vendor asset is copied.",
        "",
        f"- pages: {', '.join(pages)}",
        f"- permissions: {', '.join(sorted(manifest.get('permissions', {}).keys()))}",
        "",
        "| area | module | official classes | we have | files |",
        "| --- | --- | --- | --- | --- |",
    ]
    for r in rows:
        lines.append(
            "| {area} | {module} | {css} | {status} | {files} |".format(
                area=r["area"], module=r["module"], css=r["official_css_classes"],
                status=r["status"], files=", ".join(r["our_files"][:3]),
            )
        )
    missing = [r["module"] for r in rows if r["status"] == "missing"]
    lines += ["", f"unaccounted modules: {len(missing)} → {', '.join(missing)}"]
    with open(os.path.join(args.out, "module-inventory.md"), "w", encoding="utf-8") as fh:
        fh.write("\n".join(lines) + "\n")

    print(f"pages={len(pages)} modules={len(rows)} missing={len(missing)}")
    for m in missing:
        print("  missing:", m)
    return 0


if __name__ == "__main__":
    sys.exit(main())
