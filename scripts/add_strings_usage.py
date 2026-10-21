import os
import io, re, sys

NEW = {
    "pricing_title": ("Usage pricing", "模型计价", "تسعير الاستخدام"),
    "pricing_note": ("USD per million tokens, exact provider/model match. Only affects calls that report no cost.", "按百万 token 的美元单价，provider/model 精确匹配。只影响没有上报成本的调用。", "دولار لكل مليون رمز، بمطابقة دقيقة للمزوّد والطراز."),
    "pricing_add": ("Add rate", "添加价格", "إضافة سعر"),
    "pricing_empty": ("No rates yet — every call is counted as unknown cost.", "还没有价格——所有调用都会记为成本未知。", "لا توجد أسعار بعد."),
    "pricing_provider": ("Provider", "服务商", "المزوّد"),
    "pricing_model": ("Model", "模型", "الطراز"),
    "pricing_input": ("Input", "输入", "الإدخال"),
    "pricing_output": ("Output", "输出", "الإخراج"),
    "pricing_cache_read": ("Cache read", "缓存读", "قراءة الذاكرة"),
    "pricing_cache_write": ("Cache write", "缓存写", "كتابة الذاكرة"),
    "pricing_per_million": ("per 1M tokens", "每百万 token", "لكل مليون رمز"),
    "pricing_unsaved": ("Unsaved changes", "有未保存的修改", "تغييرات غير محفوظة"),
    "pricing_delete_confirm": ("Remove this rate?", "删除这条价格？", "إزالة هذا السعر؟"),
    "apikeys_title": ("Model API keys", "模型 API Key", "مفاتيح API للطرازات"),
    "apikeys_note": ("Account-wide keys for the OpenAI-compatible endpoint. The secret is shown once.", "账号级密钥，用于 OpenAI 兼容端点。明文只显示一次。", "مفاتيح على مستوى الحساب. يُعرض السر مرة واحدة فقط."),
    "apikeys_create": ("New key", "新建 Key", "مفتاح جديد"),
    "apikeys_budget": ("Budget (USD)", "预算（美元）", "الميزانية (دولار)"),
    "apikeys_budget_hint": ("Empty = unlimited, 0 = blocked", "留空 = 不限，0 = 停用", "فارغ = بلا حد، 0 = موقوف"),
    "apikeys_models": ("Allowed models", "可用模型", "الطرازات المسموحة"),
    "apikeys_search_model": ("Search models", "搜索模型", "بحث في الطرازات"),
    "apikeys_selected_count": ("selected", "已选", "محدد"),
    "apikeys_only_once": ("Copy it now — it is never shown again.", "现在复制——之后不会再显示。", "انسخه الآن، لن يُعرض مرة أخرى."),
    "apikeys_revoke_confirm": ("Revoke this key? Requests using it stop immediately.", "吊销这个 Key？使用它的请求会立即停止。", "إلغاء هذا المفتاح؟"),
    "apikeys_empty": ("No keys yet.", "还没有 Key。", "لا توجد مفاتيح بعد."),
    "apikeys_spent": ("Spent", "已用", "المُنفق"),
    "apikeys_last_used": ("Last used", "最后使用", "آخر استخدام"),
    "apikeys_never_used": ("Never used", "从未使用", "لم يُستخدم"),
    "apikeys_unlimited": ("Unlimited", "不限", "بلا حد"),
    "apikeys_pick_models": ("Select at least one model", "至少选一个模型", "اختر طرازًا واحدًا على الأقل"),
    "runtime_source": ("Install source", "安装来源", "مصدر التثبيت"),
    "runtime_download_warning": ("Downloading switches Hermes to the managed runtime. Your current CLI install stays on disk but stops being used.", "下载后 Hermes 会切换到托管运行时。当前 CLI 安装仍在磁盘上，但不再被使用。", "التنزيل يحوّل Hermes إلى وقت التشغيل المُدار."),
    "runtime_jobs": ("Download jobs", "下载任务", "مهام التنزيل"),
    "runtime_no_jobs": ("No download jobs in this session.", "本次会话没有下载任务。", "لا توجد مهام تنزيل."),
    "runtime_available": ("Available upstream", "上游可用版本", "متاح في المصدر"),
    "runtime_cli_installs": ("CLI installs", "CLI 安装", "تثبيتات CLI"),
    "runtime_confirm_delete": ("Delete this downloaded version?", "删除这个已下载版本？", "حذف هذه النسخة؟"),
    "action_copy": ("Copy", "复制", "نسخ"),
}

for path, idx in (("values/strings.xml", 0), ("values-en/strings.xml", 0), ("values-zh-rCN/strings.xml", 1), ("values-ar/strings.xml", 2)):
    if not os.path.exists(path):
        print("skip (absent)", path); continue
    s = io.open(path, encoding="utf-8").read()
    added, skipped = 0, 0
    lines = []
    for k, vals in NEW.items():
        if 'name="%s"' % k in s:
            skipped += 1; continue
        v = vals[idx].replace("'", "\\'").replace("&", "&amp;")
        lines.append('    <string name="%s">%s</string>' % (k, v))
        added += 1
    if not lines:
        print(path, "nothing to add"); continue
    s = s.replace("</resources>", "\n".join(lines) + "\n</resources>")
    io.open(path, "w", encoding="utf-8").write(s)
    print(path, "added", added, "skipped", skipped)
