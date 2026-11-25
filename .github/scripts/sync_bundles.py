#!/usr/bin/env python3
"""
Keeps Golem's Client's mod bundles in step with OneClient's.

OneClient's launcher downloads its bundles (performance, HUD, quality of life, utility mods) from Polyfrost's public
data server. Once a week this script downloads the same bundles for the game versions Golem's Client offers, changes
them the way Golem's Client's are changed (no Poly+, Golem's Client's name), and saves them to the data branch, so
Golem's Client gets the same mod updates as OneClient. Mods themselves still download from Modrinth.

Usage: sync_bundles.py <checkout of the data branch>
Prints what changed (Markdown); writes nothing when nothing changed.

Copyright (C) 2026 veno15 - GNU General Public License v3.0.
"""
import hashlib
import io
import json
import os
import sys
import urllib.request
import zipfile

SOURCE = "https://data-v2.polyfrost.org"
# Golem's Client has its own in-game mod instead of Poly+
SKIP = ("polyplus",)


def get(url):
    req = urllib.request.Request(url, headers={"User-Agent": "GolemsClient-bundle-sync (github.com/VenoServices11/golems-client)"})
    with urllib.request.urlopen(req, timeout=120) as res:
        return res.read()


def mod_name(path):
    return os.path.basename(path)


def adapt(raw):
    """Their bundle, Golem's Client's way: (zip bytes, index)."""
    zin = zipfile.ZipFile(io.BytesIO(raw))
    index = json.loads(zin.read("modrinth.index.json"))
    index["files"] = [f for f in index.get("files", []) if not any(s in f["path"].lower() for s in SKIP)]
    for key in ("name", "summary"):
        if isinstance(index.get(key), str):
            index[key] = index[key].replace("OneClient", "Golem's Client")
    out = io.BytesIO()
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
        for item in zin.infolist():
            if item.filename == "modrinth.index.json":
                data = json.dumps(index, indent=2, ensure_ascii=False).encode("utf-8")
            else:
                data = zin.read(item.filename)
            info = zipfile.ZipInfo(item.filename, date_time=(1980, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = item.external_attr
            z.writestr(info, data)
    return out.getvalue(), index


def same(a, b):
    """The same mods, versions and settings (formatting aside)."""
    if a is None:
        return False
    pick = lambda i: (sorted((f["path"], f.get("hashes", {}).get("sha1")) for f in i.get("files", [])), i.get("dependencies"), i.get("name"))
    return pick(a) == pick(b)


def main():
    data = sys.argv[1]
    meta_path = os.path.join(data, "golems", "bundles", "metadata.json")
    with open(meta_path, encoding="utf-8") as f:
        ours = json.load(f)
    theirs = json.loads(get(SOURCE + "/oneclient/bundles/metadata.json"))
    lines = []
    changed = False
    for version, loaders in ours.get("versions", {}).items():
        for loader, entries in loaders.items():
            upstream = theirs.get("versions", {}).get(version, {}).get(loader)
            if not upstream:
                lines.append(f"- **{version} {loader}**: OneClient has no bundles for this any more, so Golem's Client's were left as they are.")
                continue
            fresh = []
            for entry in upstream:
                name = os.path.basename(entry["path"])
                target = os.path.join(data, "golems", "bundles", "generated", name)
                packed, index = adapt(get(SOURCE + entry["path"]))
                old_index = None
                if os.path.exists(target):
                    with zipfile.ZipFile(target) as z:
                        old_index = json.loads(z.read("modrinth.index.json"))
                if same(old_index, index):
                    with open(target, "rb") as f:
                        packed = f.read()
                else:
                    changed = True
                    with open(target, "wb") as f:
                        f.write(packed)
                    before = {mod_name(f["path"]) for f in (old_index or {}).get("files", [])}
                    after = {mod_name(f["path"]) for f in index.get("files", [])}
                    title = index.get("name", name)
                    lines.append(f"- **{title}**")
                    for gone in sorted(before - after):
                        lines.append(f"  - removed or replaced: `{gone}`")
                    for new in sorted(after - before):
                        lines.append(f"  - new: `{new}`")
                    if before == after:
                        lines.append("  - settings changed (same mods)")
                fresh.append({"path": f"/golems/bundles/generated/{name}", "sha1": hashlib.sha1(packed).hexdigest()})
            if [e["path"] for e in fresh] != [e["path"] for e in entries] or [e["sha1"] for e in fresh] != [e["sha1"] for e in entries]:
                changed = True
                loaders[loader] = fresh
    if changed:
        with open(meta_path, "w", encoding="utf-8") as f:
            json.dump(ours, f, indent=2, ensure_ascii=False)
            f.write("\n")
    print("\n".join(lines) if lines else "No bundle changes this week.")


if __name__ == "__main__":
    main()
