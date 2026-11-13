from __future__ import annotations
import ast, json, os, re, shutil, sqlite3, zipfile
from pathlib import Path
from typing import Any
from fastapi import FastAPI, HTTPException
from fastapi.responses import HTMLResponse, FileResponse
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field
from google import genai
from google.genai import types

APP_DIR = Path(__file__).resolve().parent
WORKSPACE = Path(os.getenv("WORKSPACE_DIR", APP_DIR / "workspace")).resolve()
WORKSPACE.mkdir(parents=True, exist_ok=True)
MAX_FILE = 500_000
MAX_STEPS = 12
MODEL = os.getenv("GEMINI_MODEL", "gemini-2.5-flash")

app = FastAPI(title="AgentForge", version="1.0.0")
app.add_middleware(CORSMiddleware, allow_origins=["*"], allow_methods=["*"], allow_headers=["*"])

class RunRequest(BaseModel):
    prompt: str = Field(min_length=2, max_length=6000)
    api_key: str = Field(min_length=10, max_length=300)
    project: str = Field(default="my-project", min_length=1, max_length=60)

def safe_project(name: str) -> str:
    name = re.sub(r"[^a-zA-Z0-9_-]", "-", name).strip("-_")
    if not name: raise ValueError("اسم المشروع غير صالح")
    return name[:60]

def project_dir(name: str) -> Path:
    p = (WORKSPACE / safe_project(name)).resolve()
    if WORKSPACE not in p.parents: raise ValueError("مسار غير مسموح")
    p.mkdir(parents=True, exist_ok=True)
    return p

def safe_path(project: Path, rel: str) -> Path:
    rel = rel.replace("\\", "/").strip()
    p = (project / rel).resolve()
    if p != project and project not in p.parents: raise ValueError("المسار خارج مساحة المشروع")
    if rel.startswith("/") or re.match(r"^[A-Za-z]:", rel): raise ValueError("المسار المطلق ممنوع")
    return p

def list_files(project: Path) -> list[str]:
    return [str(p.relative_to(project)) for p in sorted(project.rglob("*"))
            if p.is_file() and not any(x in {".git", "__pycache__", "node_modules"} for x in p.parts)]

def read_file(project: Path, path: str) -> str:
    p = safe_path(project, path)
    if not p.is_file(): raise ValueError("الملف غير موجود")
    if p.stat().st_size > MAX_FILE: raise ValueError("الملف أكبر من الحد")
    return p.read_text("utf-8", errors="replace")

def write_file(project: Path, path: str, content: str) -> dict:
    if len(content.encode("utf-8")) > MAX_FILE: raise ValueError("الملف أكبر من 500KB")
    p = safe_path(project, path)
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(content, encoding="utf-8")
    return {"written": str(p.relative_to(project)), "bytes": p.stat().st_size}

def skill_catalog() -> dict:
    return {
      "web_app": "واجهة HTML/CSS/JS أو React، مع تصميم متجاوب وإمكانية الوصول.",
      "api_backend": "خدمات API باستخدام FastAPI، تحقق مدخلات ومعالجة أخطاء.",
      "database": "تصميم SQLite، جداول وعلاقات وفهارس وترحيلات.",
      "testing": "اختبارات وحدات وتشغيل فحوصات آمنة.",
      "security": "مراجعة الأسرار والمدخلات والصلاحيات والاعتماديات."
    }

def graph_analyze(project: Path) -> dict:
    nodes, edges = [], []
    for f in project.rglob("*.py"):
        if any(x in {".git", "__pycache__", "node_modules"} for x in f.parts): continue
        rel = str(f.relative_to(project)); nodes.append(rel)
        try:
            tree = ast.parse(f.read_text("utf-8"))
            for n in ast.walk(tree):
                if isinstance(n, ast.Import):
                    for a in n.names: edges.append({"from": rel, "to": a.name, "kind": "import"})
                elif isinstance(n, ast.ImportFrom):
                    edges.append({"from": rel, "to": n.module or "", "kind": "from"})
        except Exception: pass
    dbs = [str(f.relative_to(project)) for f in project.rglob("*.db")]
    return {"python_files": nodes, "imports": edges, "sqlite_files": dbs}

def security_scan(project: Path) -> dict:
    findings = []
    patterns = [
      ("possible_secret", re.compile(r"(?i)(api[_-]?key|secret|password)\s*[:=]\s*['\"][^'\"]{8,}['\"]"), "احتمال وجود سر ثابت داخل الكود"),
      ("sql_fstring", re.compile(r"(?i)execute\s*\(\s*f['\"]"), "استعلام SQL مبني عبر f-string؛ استخدم parameters"),
      ("dangerous_eval", re.compile(r"\b(eval|exec)\s*\("), "استخدام eval/exec يحتاج مراجعة"),
      ("debug_mode", re.compile(r"(?i)debug\s*=\s*true"), "وضع debug مفعّل")
    ]
    for f in project.rglob("*"):
        if not f.is_file() or f.stat().st_size > MAX_FILE: continue
        if any(x in {".git", "__pycache__", "node_modules"} for x in f.parts): continue
        try: data = f.read_text("utf-8", errors="ignore")
        except Exception: continue
        for code, pat, msg in patterns:
            for m in pat.finditer(data):
                findings.append({"file": str(f.relative_to(project)), "rule": code, "message": msg})
    return {"findings": findings, "count": len(findings),
            "note": "فحص ساكن أولي وليس اختبار اختراق أو ضماناً أمنياً."}

def run_safe_check(project: Path, command: str) -> dict:
    # Deliberately no arbitrary shell execution.
    allowed = {
      "python_compile": ["python", "-m", "compileall", "-q", "."],
      "list_files": None
    }
    if command not in allowed: raise ValueError("الأمر غير مسموح. المتاح: python_compile, list_files")
    if command == "list_files": return {"files": list_files(project)}
    import subprocess
    proc = subprocess.run(allowed[command], cwd=project, capture_output=True, text=True, timeout=20)
    return {"exit_code": proc.returncode, "stdout": proc.stdout[-5000:], "stderr": proc.stderr[-5000:]}

def docs_fetch(library: str, question: str) -> dict:
    # Context7 is exposed as a safe, optional remote-doc lookup endpoint.
    # It requires a reachable Context7 MCP server and its own credentials for higher limits.
    import urllib.request
    endpoint = os.getenv("CONTEXT7_MCP_URL", "https://mcp.context7.com/mcp")
    token = os.getenv("CONTEXT7_API_KEY", "")
    headers = {"Content-Type": "application/json", "Accept": "application/json, text/event-stream"}
    if token: headers["Authorization"] = f"Bearer {token}"
    def call(payload):
        req = urllib.request.Request(endpoint, data=json.dumps(payload).encode(), headers=headers, method="POST")
        with urllib.request.urlopen(req, timeout=20) as r: return r.read().decode()
    try:
        # Resolve the library first; response is returned raw because Context7 may stream SSE.
        resolved = call({"jsonrpc":"2.0","id":1,"method":"tools/call",
                         "params":{"name":"resolve-library-id","arguments":{"libraryName":library,"query":question}}})
        return {"library": library, "result": resolved[:12000],
                "hint": "إذا لم يُرجع الخادم نتيجة، أضف CONTEXT7_API_KEY أو تحقق من إعداد MCP."}
    except Exception as e:
        return {"library": library, "error": str(e), "hint": "استمر دون التوثيق أو اضبط Context7."}

TOOLS = [
  {"name":"list_project_files","description":"اعرض ملفات المشروع الحالية قبل التعديل.","parameters":{"type":"OBJECT","properties":{} }},
  {"name":"read_project_file","description":"اقرأ ملفاً موجوداً داخل المشروع.","parameters":{"type":"OBJECT","properties":{"path":{"type":"STRING"}},"required":["path"]}},
  {"name":"write_project_file","description":"أنشئ أو حدّث ملفاً داخل المشروع. لا تستخدم مسارات مطلقة.","parameters":{"type":"OBJECT","properties":{"path":{"type":"STRING"},"content":{"type":"STRING"}},"required":["path","content"]}},
  {"name":"select_skill","description":"اختر مهارة محلية مناسبة لجزء العمل.","parameters":{"type":"OBJECT","properties":{"skill":{"type":"STRING","enum":["web_app","api_backend","database","testing","security"]}},"required":["skill"]}},
  {"name":"analyze_project_graph","description":"حلل علاقات استيراد ملفات Python وقواعد SQLite.","parameters":{"type":"OBJECT","properties":{}}},
  {"name":"fetch_docs","description":"اطلب توثيقاً حديثاً لمكتبة عبر Context7 إن كان مضبوطاً.","parameters":{"type":"OBJECT","properties":{"library":{"type":"STRING"},"question":{"type":"STRING"}},"required":["library","question"]}},
  {"name":"security_scan","description":"نفّذ فحصاً ساكناً أولياً لملفات المشروع.","parameters":{"type":"OBJECT","properties":{}}},
  {"name":"run_safe_check","description":"نفذ فقط فحص compileall أو عرض الملفات؛ لا يوجد shell حر.","parameters":{"type":"OBJECT","properties":{"command":{"type":"STRING","enum":["python_compile","list_files"]}},"required":["command"]}}
]

def dispatch(name: str, args: dict, project: Path) -> Any:
    if name == "list_project_files": return {"files": list_files(project)}
    if name == "read_project_file": return {"path": args["path"], "content": read_file(project, args["path"])}
    if name == "write_project_file": return write_file(project, args["path"], args["content"])
    if name == "select_skill":
        skill = args["skill"]
        return {"skill": skill, "guidance": skill_catalog()[skill]}
    if name == "analyze_project_graph": return graph_analyze(project)
    if name == "fetch_docs": return docs_fetch(args["library"], args["question"])
    if name == "security_scan": return security_scan(project)
    if name == "run_safe_check": return run_safe_check(project, args["command"])
    raise ValueError("أداة غير معروفة")

@app.get("/", response_class=HTMLResponse)
def home():
    return (APP_DIR / "index.html").read_text("utf-8")

@app.get("/api/health")
def health(): return {"ok": True, "model": MODEL, "workspace": "ready"}

@app.post("/api/run")
def run_agent(req: RunRequest):
    try:
        project = project_dir(req.project)
        client = genai.Client(api_key=req.api_key)
        history = [types.Content(role="user", parts=[types.Part(text=(
          "أنت AgentForge، وكيل هندسة برمجيات. نفّذ طلب المستخدم باستدعاء الأدوات فقط للقراءة والكتابة والتحليل. "
          "ابدأ بعرض الملفات وتحليل المشروع عند وجود ملفات. اختر المهارة. اكتب ملفات فعلية صغيرة ومترابطة. "
          "لا تدّعِ نجاح شيء لم تنفذه. لا تكتب أسراراً. لا تستخدم مسارات خارج المشروع. "
          "لا تنفذ أوامر نظام حرة. بعد الإنجاز شغّل فحص الأمان وفحص Python إن كان مناسباً. "
          "أعد ملخصاً عربياً نهائياً واضحاً.\n\nطلب المستخدم: " + req.prompt
        ))])]
        events = []
        for step in range(MAX_STEPS):
            response = client.models.generate_content(
                model=MODEL, contents=history,
                config=types.GenerateContentConfig(
                    tools=[types.Tool(function_declarations=[
                      types.FunctionDeclaration(name=t["name"], description=t["description"], parameters=t["parameters"])
                      for t in TOOLS
                    ])],
                    temperature=0.2
                )
            )
            candidate = response.candidates[0]
            history.append(candidate.content)
            calls = [p.function_call for p in candidate.content.parts if p.function_call]
            if not calls:
                final = response.text or "انتهى التنفيذ دون نص ختامي."
                events.append({"type":"final","text":final})
                break
            for fc in calls:
                args = dict(fc.args or {})
                events.append({"type":"tool_start","tool":fc.name,"args":args})
                try:
                    result = dispatch(fc.name, args, project)
                    events.append({"type":"tool_result","tool":fc.name,"result":result})
                except Exception as e:
                    result = {"error":str(e)}
                    events.append({"type":"tool_error","tool":fc.name,"error":str(e)})
                history.append(types.Content(role="user", parts=[
                    types.Part.from_function_response(name=fc.name, response={"result":result})
                ]))
        else:
            final = "توقف الوكيل بعد بلوغ حد الخطوات الآمن. أرسل طلب متابعة."
            events.append({"type":"final","text":final})
        return {"project":safe_project(req.project), "events":events, "files":list_files(project)}
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))

@app.get("/api/projects/{name}/download")
def download_project(name: str):
    try: project = project_dir(name)
    except Exception as e: raise HTTPException(400, str(e))
    archive = WORKSPACE / f"{safe_project(name)}.zip"
    with zipfile.ZipFile(archive, "w", zipfile.ZIP_DEFLATED) as z:
        for f in project.rglob("*"):
            if f.is_file(): z.write(f, f.relative_to(project))
    return FileResponse(archive, filename=f"{safe_project(name)}.zip", media_type="application/zip")

@app.get("/api/projects/{name}/tree")
def tree(name: str):
    try: return {"files":list_files(project_dir(name))}
    except Exception as e: raise HTTPException(400, str(e))
