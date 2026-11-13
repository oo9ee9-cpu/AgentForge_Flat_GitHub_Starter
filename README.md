# AgentForge — Full-Stack AI Agent Builder

واجهة عربية داكنة + Backend FastAPI + Gemini Function Calling + مساحة عمل محلية + MCP Server.

## الملفات (كلها في الجذر)
- `main.py`: API، حلقة Gemini، الأدوات، المهارات، Graph analyzer، Context7 fetcher، فحص أمني.
- `index.html`: الواجهة.
- `mcp_server.py`: خادم MCP مستقل لأدوات مساحة العمل.
- `requirements.txt`, `Procfile`, `runtime.txt`: التشغيل والنشر.

## تشغيل محلياً
يتطلب Python 3.11+.

```bash
python -m venv .venv
# Windows:
.venv\Scripts\activate
# macOS/Linux:
source .venv/bin/activate
pip install -r requirements.txt
uvicorn main:app --reload
```

افتح `http://127.0.0.1:8000`، أدخل Gemini API Key، سمِّ المشروع، ثم اكتب الطلب.
المخرجات تحفظ في `workspace/<project>/` ويمكن تنزيلها ZIP من الواجهة.

## Gemini
أنشئ مفتاحاً من Google AI Studio. المفتاح يُرسل من المتصفح إلى الخادم عند الطلب ويُحفظ في LocalStorage على جهاز المتصفح. لا تنشر المفتاح في GitHub ولا تشاركه. LocalStorage ليس خزنة آمنة ضد XSS؛ في النشر العام الأفضل نقل المفتاح إلى متغير بيئة سري على الخادم وإزالة حقل المفتاح من الواجهة.

متغيرات اختيارية:
- `GEMINI_MODEL` (الافتراضي `gemini-2.5-flash`)
- `WORKSPACE_DIR` (مسار حفظ المشاريع)
- `CONTEXT7_MCP_URL` (الافتراضي `https://mcp.context7.com/mcp`)
- `CONTEXT7_API_KEY` (اختياري حسب حدود الخدمة)

## MCP
شغّل الخادم المحلي المستقل:
```bash
python mcp_server.py
```
يستخدم MCP stdio عبر FastMCP. الأدوات المتاحة: `list_files`, `read_file`, `write_file`.
هذا الخادم منفصل عن حلقة Gemini في `main.py`؛ أدوات Gemini الحالية تستدعي نفس منطق مساحة العمل مباشرة. لربط عميل MCP خارجي استخدم إعداد stdio الخاص بالعميل.

## GitHub والنشر
GitHub مستودع لحفظ الكود وليس مضيف Python backend. GitHub Pages يستضيف ملفات ثابتة فقط؛ لذلك لا يمكن تشغيل FastAPI عليه. انشر هذا المستودع على خدمة تدعم Python مثل Render:
1. أنشئ مستودع GitHub وارفع جميع ملفات الجذر.
2. في Render اختر New → Web Service → اربط المستودع.
3. Build Command: `pip install -r requirements.txt`
4. Start Command: `uvicorn main:app --host 0.0.0.0 --port $PORT`
5. أضف `GEMINI_MODEL` أو `CONTEXT7_API_KEY` عند الحاجة.
6. لا تحفظ مفاتيح الإنتاج في ملفات المستودع.

ملاحظة: بعض منصات الاستضافة تمسح الملفات المحلية عند إعادة التشغيل. استخدم قرصاً دائماً أو تخزيناً خارجياً إذا أردت الاحتفاظ بمشاريع المستخدمين.

## حدود الأمان المهمة
- لا يوجد تنفيذ أوامر shell حرة. المتاح فقط `python_compile` و`list_files`.
- عمليات الكتابة مقيدة داخل مجلد المشروع وبحد 500KB للملف.
- فاحص الأمان فحص Regex/ساكن أولي، وليس اختبار اختراق ولا بديلاً عن مراجعة بشرية أو أدوات مثل Bandit وpip-audit وSemgrep.
- لا تفتح هذا السيرفر للإنترنت دون مصادقة مستخدم، تحديد معدل الطلبات، HTTPS، عزل لكل مستخدم، وحماية من إساءة استخدام Gemini.
- Gemini قد ينتج كوداً معيباً؛ اختبر الناتج قبل تشغيله أو نشره.
