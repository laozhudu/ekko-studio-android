"""API coverage inventory.

Reads the vendored OpenAPI snapshot plus the client sources, and prints a
per-module table: how many operations the app actually calls, how many it does
not, and which of those are deliberately out of scope.

Usage:  python3 scripts/api_coverage.py [--json]
"""
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SPEC = os.path.join(ROOT, "docs/openapi/ekko-studio-0.7.38.json")
SRC_DIR = os.path.join(ROOT, "android/app/src/main/java/com/laozhudu/ekkostudio")

# Modules the app does not talk to, with the reason. Everything not listed here
# is expected to be reachable from the client; anything else is a gap.
OUT_OF_SCOPE = {
    "STT": "voice capture uses /studio/stt/{transcribe,settings,profile-status} only; local streaming models are desktop-only",
    "TTS": "speech output uses /studio/tts/synthesize; provider management stays on the web dashboard",
    "Social Messages": "Telegram/Feishu/WeChat routing is a server-side concern, not a phone chat client",
    "Model API": "OpenAI-compatible proxy for external tools; the app talks to Studio directly",
    "JEV": "browser automation service, no mobile surface",
    "Copilot Auth": "OAuth device flow for a coding agent; belongs to the desktop dashboard",
    "Anthropic Auth": "OAuth device flow for a coding agent",
    "Codex Auth": "OAuth device flow for a coding agent",
    "MiniMax Auth": "OAuth device flow for a coding agent",
    "Nous Auth": "OAuth device flow for a coding agent",
    "xAI Auth": "OAuth device flow for a coding agent",
    "MCU Devices": "ESP32-class hardware pairing",
    "MCU Firmware": "ESP32-class hardware flashing",
    "Data Migration": "one-time desktop migration of Windows appdata",
    "Health": "probe endpoints for orchestrators",
    "API Docs": "the OpenAPI document itself; consumed offline by tests",
    "Memory": "agent memory is managed through chat commands",
    "Petdex": "asset catalogue; only the manifest and adopt/active calls matter",
    "Announcements": "desktop release notes",
    "Performance": "server metrics; the app shows the runtime summary only",
}

# Operations that exist but are meaningless on a phone, per module.
OUT_OF_SCOPE_PATHS = {
    "/api/studio/mcu/firmware.bin",
    "/api/studio/mcu/firmware/manifest",
    "/api/studio/mcu/voice-turn",
    "/api/studio/mcu/audio/{file}",
    "/api/studio/stt/local-model",
    "/api/studio/stt/local-model/download",
    "/api/studio/stt/local-stream",
    "/api/studio/stt/local-stream/{sessionId}",
    "/api/studio/stt/local-stream/{sessionId}/chunk",
    "/api/studio/stt/local-stream/{sessionId}/finish",
    "/api/studio/stt/profile-status/missing-audio",
    "/api/studio/media/apikey-image-generate",
    "/api/studio/media/grok-image-to-video",
    "/api/studio/media/minimax-image-to-video",
    "/api/studio/petdex/asset",
    "/api/studio/jev/browser/match",
    "/api/studio/jev/browser/verify",
    "/api/studio/jev/evaluate",
    "/api/studio/jev/settings",
    "/api/studio/jev/test",
    "/api/studio/models/cloud-link",
    "/api/studio/models/api-keys",
    "/api/studio/models/api-keys/{id}",
    "/api/studio/usage/pricing",
    "/api/studio/mobile-calendar/request",
    "/api/studio/mobile-health/request",
    "/api/studio/mobile-location/request",
    "/api/studio/push/registration",
    "/api/studio/live-activities/registration",
    "/api/studio/voice/proxy/{profile}/v1/audio/speech",
    "/api/studio/voice/proxy/{profile}/v1/audio/transcriptions",
    "/api/studio/voice/proxy/{profile}/v1/tts",
    "/api/studio/app-uploads",
    "/api/studio/app-uploads/{id}",
    "/api/studio/app-uploads/{id}/chunks",
    "/api/studio/app-uploads/{id}/complete",
    "/api/studio/clarifications/request",
    "/api/studio/task-plans/update",
}


def normalize(path):
    path = path.split("?")[0]
    path = re.sub(r"\$\{[^}]*}", "{}", path)
    path = re.sub(r"\$[A-Za-z_][A-Za-z0-9_]*", "{}", path)
    path = re.sub(r"\{[^}]*}", "{}", path)
    return path.rstrip("/") or "/"


def client_calls():
    """Every /api literal in the network layer, normalized.

    Also records the static prefix of interpolated literals: the client builds
    `/api/hermes/mcp/reload${query}`, which resolves to the documented
    `/api/hermes/mcp/reload` but never normalizes to it.
    """
    calls = set()
    for name in os.listdir(SRC_DIR):
        if not name.endswith(".kt"):
            continue
        text = open(os.path.join(SRC_DIR, name)).read()
        for match in re.finditer(r'"(/api/[^"]*)"', text):
            literal = match.group(1)
            calls.add(normalize(literal))
            if "$" in literal:
                prefix = normalize(literal.split("$")[0]).rstrip("/")
                if len(prefix) > 12:
                    calls.add(prefix)
    return calls


def main():
    spec = json.load(open(SPEC))
    calls = client_calls()
    prefixes = {c.rstrip("/") for c in calls}

    def used(path):
        norm = normalize(path)
        if norm in calls:
            return True
        # dynamic tails on either side only line up by prefix
        return any(norm.startswith(p + "/") or p.startswith(norm + "/") for p in prefixes if len(p) > 12)

    rows = []
    for path, item in spec["paths"].items():
        for method, op in item.items():
            if method.lower() not in ("get", "post", "put", "patch", "delete"):
                continue
            tag = (op.get("tags") or ["(none)"])[0]
            status = "used" if used(path) else "gap"
            if tag in OUT_OF_SCOPE or path in OUT_OF_SCOPE_PATHS:
                status = "out-of-scope" if status != "used" else "used"
            rows.append({"tag": tag, "method": method.upper(), "path": path, "status": status})

    modules = {}
    for row in rows:
        m = modules.setdefault(row["tag"], {"total": 0, "used": 0, "gap": 0, "oos": 0})
        m["total"] += 1
        m["used" if row["status"] == "used" else "gap" if row["status"] == "gap" else "oos"] += 1

    if "--json" in sys.argv:
        print(json.dumps({"rows": rows, "modules": modules}, indent=2, ensure_ascii=False))
        return

    total = len(rows)
    used_count = sum(m["used"] for m in modules.values())
    gap = sum(m["gap"] for m in modules.values())
    oos = sum(m["oos"] for m in modules.values())
    print(f"operations: {total}  used: {used_count}  gap: {gap}  out-of-scope: {oos}")
    reachable = total - oos
    print(f"reachable coverage: {used_count}/{reachable} = {used_count / max(1, reachable):.1%}\n")
    print(f"{'module':<22}{'used':>5}{'gap':>5}{'oos':>5}{'total':>6}")
    for tag, m in sorted(modules.items(), key=lambda kv: -(kv[1]["gap"])):
        print(f"{tag:<22}{m['used']:>5}{m['gap']:>5}{m['oos']:>5}{m['total']:>6}")


if __name__ == "__main__":
    main()
