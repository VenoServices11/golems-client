"""Copies the launcher's data files (version list, version art, mod bundles, bad-mod lists...) from
OneClient's data host into ./data, so Golem's Client serves its own copy from the `data` branch.

Starts from the files the launcher asks for by name and follows every "/oneclient/..." path found
inside them. Then makes the copy ours:
- only the Minecraft versions in KEEP_VERSIONS are listed (version list and bundles);
- Poly+ (Polyfrost's online mod) is taken out of the bundles, the bundles are renamed from
  "OneClient ..." to "Golem's Client ...", and their checksums are updated.
"""
import hashlib
import json
import os
import sys
import urllib.request
import zipfile
import io

SOURCE = "https://data-v2.polyfrost.org"
OUT = sys.argv[1] if len(sys.argv) > 1 else "data"
KEEP_VERSIONS = ["1.21.11"]
REMOVED_PROJECTS = {"Iw9mZi4a": "Poly+"}  # Modrinth project ids taken out of the bundles
SEEDS = [
    "/oneclient/versions/metadata.json",
    "/oneclient/versions/arts.json",
    "/oneclient/bundles/metadata.json",
    "/oneclient/bundles/disable-warnings.json",
    "/oneclient/bad-mods.json",
    "/oneclient/bad-modpacks.json",
    "/oneclient/improvements.json",
]


def fetch(path):
    req = urllib.request.Request(SOURCE + path, headers={"User-Agent": "golems-client-mirror"})
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            return r.read()
    except Exception as e:  # noqa: BLE001
        print(f"  skip {path}: {e}")
        return None


def paths_in(value):
    if isinstance(value, dict):
        for v in value.values():
            yield from paths_in(v)
    elif isinstance(value, list):
        for v in value:
            yield from paths_in(v)
    elif isinstance(value, str) and value.startswith("/oneclient/"):
        yield value.split("?")[0]


def wanted(path):
    if path.endswith(".mrpack"):
        return any(v in path for v in KEEP_VERSIONS)
    return ".." not in path


seen, queue, total = set(), list(SEEDS), 0
while queue:
    path = queue.pop(0)
    if path in seen or not wanted(path):
        continue
    seen.add(path)
    body = fetch(path)
    if body is None:
        continue
    dest = os.path.join(OUT, path.lstrip("/"))
    os.makedirs(os.path.dirname(dest), exist_ok=True)
    with open(dest, "wb") as f:
        f.write(body)
    total += len(body)
    print(f"  {path} ({len(body)} bytes, sha1 {hashlib.sha1(body).hexdigest()[:10]})")
    if path.endswith(".json"):
        try:
            queue.extend(p for p in paths_in(json.loads(body)) if p not in seen)
        except ValueError:
            pass

print(f"{len(seen)} paths, {total / 1e6:.1f} MB")


def local(path):
    return os.path.join(OUT, path.lstrip("/"))


def load(path):
    with open(local(path), "rb") as f:
        return json.load(f)


def save(path, value):
    with open(local(path), "w", encoding="utf-8") as f:
        json.dump(value, f, indent=2, ensure_ascii=False)
        f.write("\n")


def keep_version(mc_version):
    return mc_version in KEEP_VERSIONS


# --- version list: only the versions we ship
versions_path = "/oneclient/versions/metadata.json"
if os.path.exists(local(versions_path)):
    manifest = load(versions_path)
    clusters = []
    for cluster in manifest.get("clusters", []):
        entries = [e for e in cluster.get("entries", [])
                   if keep_version(f"1.{cluster['major_version']}.{e['minor_version']}")]
        if entries:
            cluster["entries"] = entries
            clusters.append(cluster)
    manifest["clusters"] = clusters
    manifest["migrations"] = [m for m in manifest.get("migrations", [])
                              if keep_version(m.get("to", {}).get("mc_version", ""))]
    save(versions_path, manifest)
    print("versions kept:", [f"1.{c['major_version']}.{e['minor_version']}" for c in clusters for e in c["entries"]])


def strip_projects(mrpack_path):
    """Takes REMOVED_PROJECTS out of a bundle archive and renames it. Returns its new sha1."""
    src = local(mrpack_path)
    with zipfile.ZipFile(src) as zin:
        items = [(info, zin.read(info.filename)) for info in zin.infolist()]
    out = io.BytesIO()
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as zout:
        for info, data in items:
            if info.filename == "modrinth.index.json":
                index = json.loads(data)
                before = len(index.get("files", []))
                index["files"] = [
                    f for f in index.get("files", [])
                    if not any(f"/data/{pid}/" in url for url in f.get("downloads", []) for pid in REMOVED_PROJECTS)
                ]
                removed = before - len(index["files"])
                if removed:
                    print(f"  {mrpack_path}: removed {removed} file(s) ({', '.join(REMOVED_PROJECTS.values())})")
                # "OneClient 1.21.11 Fabric [HUD]" -> "Golem's Client 1.21.11 Fabric [HUD]"
                if str(index.get("name", "")).startswith("OneClient"):
                    index["name"] = "Golem's Client" + index["name"][len("OneClient"):]
                data = json.dumps(index, indent=2, ensure_ascii=False).encode()
            zout.writestr(info, data)
    body = out.getvalue()
    with open(src, "wb") as f:
        f.write(body)
    return hashlib.sha1(body).hexdigest()


# --- bundles: only our versions, without Poly+
bundles_path = "/oneclient/bundles/metadata.json"
if os.path.exists(local(bundles_path)):
    catalog = load(bundles_path)
    catalog["versions"] = {v: loaders for v, loaders in catalog.get("versions", {}).items() if keep_version(v)}
    for loaders in catalog["versions"].values():
        for entries in loaders.values():
            for entry in entries:
                if os.path.exists(local(entry["path"])):
                    entry["sha1"] = strip_projects(entry["path"])
    save(bundles_path, catalog)
    print("bundles kept:", {v: {l: [e["path"].rsplit("/", 1)[-1] for e in es] for l, es in ls.items()}
                            for v, ls in catalog["versions"].items()})
