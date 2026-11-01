"""Keeps the search service layered like the Java backend:
controller -> service -> repository, with dto, model and util shared and ml holding the embedding/reranking models."""
import ast
import pathlib

APP = pathlib.Path(__file__).resolve().parents[1] / "app"
LAYERS = {"config", "controller", "service", "repository", "dto", "model", "ml", "util", "exception"}


def imports():
    """(layer of the importing file, file name, imported module) for every import in app/."""
    out = []
    for f in APP.rglob("*.py"):
        rel = f.relative_to(APP)
        layer = rel.parts[0] if len(rel.parts) > 1 else rel.stem          # "main" for app/main.py
        for node in ast.walk(ast.parse(f.read_text(encoding="utf-8"))):
            if isinstance(node, ast.Import):
                out += [(layer, rel.as_posix(), a.name) for a in node.names]
            elif isinstance(node, ast.ImportFrom) and node.module:
                out += [(layer, rel.as_posix(), node.module)]
    return out


def app_layer(module: str) -> str | None:
    parts = module.split(".")
    return parts[1] if parts[0] == "app" and len(parts) > 1 else None


def violations(who: set[str], forbidden) -> list[str]:
    return [f"{file} -> {mod}" for layer, file, mod in imports() if layer in who and forbidden(mod)]


def test_every_module_lives_in_a_layer():
    stray = [p.relative_to(APP).as_posix() for p in APP.rglob("*.py")
             if len(p.relative_to(APP).parts) > 1 and p.relative_to(APP).parts[0] not in LAYERS]
    top = [p.name for p in APP.glob("*.py") if p.name not in ("__init__.py", "main.py")]
    assert stray == [] and top == []


def test_controllers_only_use_services_dtos_and_models():
    assert violations({"controller"}, lambda m: app_layer(m) in {"repository", "ml", "util"}) == []


def test_services_never_depend_on_the_web_layer():
    assert violations({"service", "repository", "ml", "util", "model", "dto"},
                      lambda m: app_layer(m) == "controller" or m.split(".")[0] in {"fastapi", "starlette"}) == []


def test_only_the_repository_talks_to_postgres():
    assert violations(LAYERS - {"repository"}, lambda m: m.split(".")[0] in {"psycopg", "psycopg_pool", "pgvector"}) == []


def test_lower_layers_do_not_reach_up():
    assert violations({"repository"}, lambda m: app_layer(m) in {"service", "controller"}) == []
    assert violations({"model", "dto", "util"}, lambda m: app_layer(m) in {"service", "controller", "repository"}) == []
