"""One-time copy of the game and Fabric loader version files into the data branch (`meta/`), so the
launcher reads them from Golem's Client's own repository. Keeps Minecraft 1.21.11 only.

Usage: python3 mirror_meta.py <out dir> <source> <our base url>
"""
import hashlib
import json
import os
import sys
import urllib.request

OUT, SOURCE, DEST = sys.argv[1], sys.argv[2].rstrip("/"), sys.argv[3].rstrip("/")
KEEP_VERSIONS = {"1.21.11"}
LOADERS = ["minecraft", "fabric", "forge", "neo", "quilt", "ornithe"]
MIRRORED_LOADERS = {"minecraft", "fabric"}
MAX_BYTES = 60_000_000


def fetch(url):
    req = urllib.request.Request(url, headers={"User-Agent": "golems-client-mirror"})
    try:
        with urllib.request.urlopen(req, timeout=120) as r:
            return r.read()
    except Exception as e:  # noqa: BLE001
        print(f"  skip {url}: {e}")
        return None


def strings(value):
    if isinstance(value, dict):
        for v in value.values():
            yield from strings(v)
    elif isinstance(value, list):
        for v in value:
            yield from strings(v)
    elif isinstance(value, str):
        yield value


def is_placeholder(version_id):
    return "${" in version_id


originals = {}  # path -> bytes as copied (lists already cut down to KEEP_VERSIONS)
queue = []

for loader in LOADERS:
    for n in range(0, 10):
        path = f"/{loader}/v{n}/manifest.json"
        body = fetch(SOURCE + path)
        if body is None:
            continue
        manifest = json.loads(body)
        if loader not in MIRRORED_LOADERS:
            manifest = {"gameVersions": []}
        elif "versions" in manifest:  # Minecraft's own list
            manifest["versions"] = [v for v in manifest["versions"] if v.get("id") in KEEP_VERSIONS]
            if isinstance(manifest.get("latest"), dict):
                manifest["latest"] = {k: "1.21.11" for k in manifest["latest"]}
        elif "gameVersions" in manifest:  # a mod loader's list
            manifest["gameVersions"] = [
                v for v in manifest["gameVersions"]
                if is_placeholder(v.get("id", "")) or v.get("id") in KEEP_VERSIONS
            ]
        originals[path] = json.dumps(manifest).encode()
        print(f"{path}: copied")
        queue.extend(s for s in strings(manifest) if s.startswith(SOURCE))

while queue:
    url = queue.pop(0)
    path = url[len(SOURCE):].split("?")[0]
    if path in originals or ".." in path:
        continue
    body = fetch(url)
    if body is None or len(body) > MAX_BYTES:
        continue
    originals[path] = body
    if path.endswith(".json"):
        try:
            queue.extend(s for s in strings(json.loads(body)) if s.startswith(SOURCE))
        except ValueError:
            pass

print(f"{len(originals)} files, {sum(map(len, originals.values())) / 1e6:.1f} MB")
if not any(p.startswith("/minecraft/") for p in originals) or not any(p.startswith("/fabric/") for p in originals):
    sys.exit("the Minecraft or Fabric list is missing; not writing a half copy")

# point every link at our copy, and fix the checksums of files whose links changed
final = dict(originals)


def fix(node):
    if isinstance(node, dict):
        url = node.get("url")
        if isinstance(url, str) and url.startswith(DEST):
            target = final.get(url[len(DEST):].split("?")[0])
            if target is not None:
                if "sha1" in node:
                    node["sha1"] = hashlib.sha1(target).hexdigest()
                if "sha256" in node:
                    node["sha256"] = hashlib.sha256(target).hexdigest()
                if isinstance(node.get("size"), int):
                    node["size"] = len(target)
        for v in node.values():
            fix(v)
    elif isinstance(node, list):
        for v in node:
            fix(v)


for _ in range(6):
    changed = False
    for path, body in originals.items():
        if not path.endswith(".json"):
            continue
        try:
            value = json.loads(body.decode().replace(SOURCE, DEST))
        except ValueError:
            continue
        fix(value)
        new = json.dumps(value, separators=(",", ":")).encode()
        if final[path] != new:
            final[path] = new
            changed = True
    if not changed:
        break

for path, body in final.items():
    dest = os.path.join(OUT, path.lstrip("/"))
    os.makedirs(os.path.dirname(dest), exist_ok=True)
    with open(dest, "wb") as f:
        f.write(body)
print("written to", OUT)
