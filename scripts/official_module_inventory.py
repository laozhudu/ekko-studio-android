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
    ("shell", "login", None, "登录/服务器地址", ["LoginScreen", "auth/login"], "login"),
    ("shell", "devices", None, "已保存设备", ["DevicesScreen", "app-connections"], "device"),
    ("shell", "bootstrap", None, "首次连接引导", ["Onboarding", "bootstrap"], "bootstrap"),
    ("shell", "about", None, "关于/条款/检查更新", ["About", "StudioUpdate"], "about"),
    ("shell", "network-mode", "network", "局域网/P2P/云端模式", ["NetworkMode", "p2p"], "network"),

    ("chat", "drawer", "drawer", "会话抽屉/账户菜单", ["DrawerContent", "ModalNavigationDrawer"], "drawer"),
    ("chat", "session", "session", "会话列表/分类/文件夹/搜索", ["SessionRow", "sessionCategor", "sessionSearch"], "session"),
    ("chat", "new-chat", "new", "新建会话/分类", ["startNewConversation", "new-chat"], "new chat"),
    ("chat", "composer", "composer", "输入区/附件/工具条", ["Composer(", "composer"], "composer"),
    ("chat", "input-context", "input", "上下文计量", ["ContextUsage", "input-context"], "context"),
    ("chat", "messages", "message", "消息气泡/元信息", ["MessageBubble"], "message"),
    ("chat", "markdown", "markdown", "Markdown/图片/链接卡", ["ChatMarkdown"], "markdown"),
    ("chat", "tool", "tool", "工具调用卡片", ["ToolStepRow", "tool-call"], "tool"),
    ("chat", "file-preview", "file-preview", "文件预览", ["ChatFileCard", "file-preview"], "file-preview"),
    ("chat", "voice", "voice", "语音录制/朗读", ["Recorder", "voice"], "voice"),
    ("chat", "usage", "usage", "用量/缓存/成本", ["UsageStats", "usage"], "usage"),
    ("chat", "session-share", None, "会话分享", ["share"], "share"),

    ("group", "group", "group", "群聊/群成员/头像", ["GroupsScreen", "group-agent"], "group"),
    ("group", "presets", None, "智能体预设", ["agentPreset", "AgentPreset"], "preset"),

    ("work", "workspace", "workspace", "工作区浏览器", ["WorkspaceScreen", "workspace"], "workspace"),
    ("work", "terminal", "terminal", "终端", ["TerminalClient", "TerminalScreen"], "terminal"),
    ("work", "workflow", "workflow", "工作流编排", ["WorkflowsScreen", "workflow"], "workflow"),
    ("work", "edge", "edge", "工作流连线/条件", ["edge"], "edge"),

    ("agent", "agent", "agent", "智能体徽标/运行态", ["AgentHub", "agent-badge"], "agent"),
    ("agent", "skills", "skills", "技能库/编辑", ["SkillsScreen", "skills"], "skill"),
    ("agent", "skill-detail", "skill-", "技能详情", ["SkillScreen"], "skill detail"),
    ("agent", "bundles", "bundle", "技能包", ["bundle"], "bundle"),
    ("agent", "mcp", "mcp", "MCP 服务", ["McpScreen", "mcp"], "mcp"),
    ("agent", "coding", "coding", "编码代理配置", ["codingAgent"], "coding agent"),
    ("agent", "aux", "aux", "辅助模型/委派", ["auxiliary"], "aux"),
    ("agent", "moa", "moa", "MoA 混合智能体", ["moa"], "moa"),
    ("agent", "pets", None, "宠物/吉祥物", ["Pets"], "pet"),
    ("agent", "journey", None, "技能旅程图", ["Journey"], "journey"),

    ("auto", "jobs", "job", "定时任务", ["CronJob"], "cron"),
    ("auto", "kanban", None, "看板", ["Kanban"], "kanban"),
    ("auto", "webhooks", "hook", "Webhook", ["Webhook"], "webhook"),
    ("auto", "channels", None, "消息渠道/微信", ["Channel"], "channel"),

    ("model", "model", "model", "模型面板/API 协议", ["ModelSettings", "model-"], "model"),
    ("model", "provider", "provider", "Provider 授权", ["provider"], "provider"),
    ("model", "api", "api-", "API 地址/Key", ["ApiKeys", "api-key"], "api key"),
    ("model", "hermes", "hermes", "Hermes 配置", ["hermes/config", "HermesConfig"], "hermes config"),

    ("obs", "performance", "performance", "性能面板", ["performance"], "performance"),
    ("obs", "logs", "log", "日志", ["LogsScreen", "log-"], "log"),
    ("obs", "health", "active-health", "健康检查", ["health"], "health"),
    ("obs", "insights", None, "洞察", ["Insights"], "insight"),

    ("look", "theme", "theme", "主题/字号/颜色", ["AppearanceScreen", "theme-"], "theme"),
    ("look", "background", "theme-background", "聊天背景图", ["background"], "background"),
    ("look", "inbox", "inbox", "通知收件箱", ["inbox"], "inbox"),
    ("look", "location", None, "位置发送", ["location"], "location"),
    ("look", "search", "search", "全局搜索", ["sessionSearch", "StudioSearchField"], "search"),
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
    """Class names, grouped by their full dash-separated prefix.

    Splitting on the first dash loses compound prefixes: `.file-preview-*`
    would be counted as `file`, and `.input-context-*` as `input`. Modules are
    declared with the full prefix, so the lookup is a startswith count.
    """
    names = re.findall(r"\.([a-zA-Z][a-zA-Z0-9_-]{1,60})", css)
    return names


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
    ap.add_argument("--resource-copy", default="android/app/src/test/resources/module-inventory.json",
                    help="also copy the inventory here for the CI ratchet test")
    args = ap.parse_args()

    _z, css, service, manifest, pages = load(args.apk)
    class_names = classes_by_prefix(css)
    src = sources(args.src)

    rows = []
    for area, module, prefix, label, keywords, official_kw in MODULES:
        css_classes = (
            sum(1 for name in class_names if name == prefix.rstrip("-") or name.startswith(prefix.rstrip("-") + "-"))
            if prefix else 0
        )
        hits = []
        for path, text in src.items():
            if any(k in text for k in keywords):
                hits.append(os.path.basename(path))
        # Presence in the official bundle: its own stylesheet prefix, its own
        # page, or its own vocabulary in the shared UI layer. A module with a
        # zero on all three is ours, not theirs — that distinction matters, since
        # claiming to "align with the official client" on a screen it does not
        # have would be meaningless.
        mentions = service.lower().count(official_kw.lower()) if official_kw else 0
        official = css_classes > 0 or module in pages or mentions >= 5
        rows.append(
            {
                "area": area,
                "module": module,
                "label": label,
                "css_prefix": prefix,
                "official_css_classes": css_classes,
                "official_mentions": mentions,
                "official": official,
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
    text = json.dumps(payload, ensure_ascii=False, indent=2)
    with open(os.path.join(args.out, "module-inventory.json"), "w", encoding="utf-8") as fh:
        fh.write(text)
    if args.resource_copy:
        os.makedirs(os.path.dirname(args.resource_copy), exist_ok=True)
        with open(args.resource_copy, "w", encoding="utf-8") as fh:
            fh.write(text)

    lines = [
        "# Official module inventory",
        "",
        f"Generated by `scripts/official_module_inventory.py` from `{os.path.basename(args.apk)}`.",
        "Counts are stylesheet class prefixes and source-file matches; no vendor asset is copied.",
        "",
        f"- pages: {', '.join(pages)}",
        f"- permissions: {', '.join(sorted(manifest.get('permissions', {}).keys()))}",
        "",
        "| area | module | official? | official classes | mentions | we have | files |",
        "| --- | --- | --- | --- | --- | --- | --- |",
    ]
    for r in rows:
        lines.append(
            "| {area} | {module} | {official} | {css} | {mentions} | {status} | {files} |".format(
                area=r["area"], module=r["module"],
                official="yes" if r["official"] else "**no**",
                css=r["official_css_classes"], mentions=r["official_mentions"],
                status=r["status"], files=", ".join(r["our_files"][:3]),
            )
        )
    missing = [r["module"] for r in rows if r["status"] == "missing"]
    missing_official = [r["module"] for r in rows if r["status"] == "missing" and r["official"]]
    ours_only = [r["module"] for r in rows if not r["official"]]
    lines += ["", f"no counterpart in our sources: {len(missing)} → {', '.join(missing)}"]
    lines += ["", f"of those, the official client has them: {len(missing_official)} → {', '.join(missing_official)}"]
    lines += ["", f"modules the official client does NOT have (ours): {', '.join(ours_only)}"]
    with open(os.path.join(args.out, "module-inventory.md"), "w", encoding="utf-8") as fh:
        fh.write("\n".join(lines) + "\n")

    print(f"pages={len(pages)} modules={len(rows)} missing={len(missing)} official-and-missing={len(missing_official)}")
    for m in missing_official:
        print("  official but absent:", m)
    print("  ours only:", ", ".join(ours_only))
    return 0


if __name__ == "__main__":
    sys.exit(main())
