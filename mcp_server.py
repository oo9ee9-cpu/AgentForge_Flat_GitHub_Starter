"""AgentForge MCP server. Run: python mcp_server.py"""
from pathlib import Path
import os, re
from mcp.server.fastmcp import FastMCP

ROOT = Path(os.getenv("WORKSPACE_DIR", Path(__file__).resolve().parent / "workspace")).resolve()
ROOT.mkdir(parents=True, exist_ok=True)
mcp = FastMCP("AgentForge Local Workspace")

def project_path(project: str, rel: str = "") -> Path:
    project = re.sub(r"[^a-zA-Z0-9_-]", "-", project).strip("-_")[:60]
    base = (ROOT / project).resolve()
    if ROOT not in base.parents: raise ValueError("invalid project")
    p = (base / rel).resolve()
    if p != base and base not in p.parents: raise ValueError("path outside project")
    return p

@mcp.tool()
def list_files(project: str) -> list[str]:
    """List files in a named project workspace."""
    base = project_path(project); base.mkdir(parents=True, exist_ok=True)
    return [str(p.relative_to(base)) for p in base.rglob("*") if p.is_file()]

@mcp.tool()
def read_file(project: str, path: str) -> str:
    """Read a UTF-8 text file from the named project."""
    p = project_path(project, path)
    if not p.is_file() or p.stat().st_size > 500000: raise ValueError("missing or too large")
    return p.read_text("utf-8")

@mcp.tool()
def write_file(project: str, path: str, content: str) -> str:
    """Write a UTF-8 text file inside the named project."""
    if len(content.encode()) > 500000: raise ValueError("file too large")
    p = project_path(project, path); p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(content, encoding="utf-8")
    return f"saved {path}"

if __name__ == "__main__":
    mcp.run()
