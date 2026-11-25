#!/usr/bin/env python3
"""
Keeps Golem's Client's mod bundles up to date, every day.

1. OneClient's bundles: OneClient's launcher downloads its bundles (performance, HUD, quality of life, utility mods)
   from Polyfrost's public data server. This script downloads the same bundles for the game versions Golem's Client
   offers and changes them the way Golem's Client's are changed (no Poly+, Golem's Client's name), so Golem's Client
   has the same mods as OneClient.
2. Newer versions on Modrinth: each mod then takes its newest Modrinth version for that game version and Fabric, when
   it's newer than the one OneClient picked: releases only (a mod already on a beta or alpha may take a newer beta or
   alpha), and only when everything it needs is in the bundle.

The result is saved to the data branch, where the launcher picks it up. Mods still download from Modrinth.

Usage: sync_bundles.py <checkout of the data branch>
Prints what changed (Markdown); writes nothing when nothing changed.

Copyright (C) 2026 veno15 - GNU General Public License v3.0.
"""
import hashlib
import io
import json
import os
import sys
import urllib.parse
import urllib.request
import zipfile

SOURCE = "https://data-v2.polyfrost.org"
MODRINTH = "https://api.modrinth.com/v2"
CHANNEL = {"release": 0, "beta": 1, "alpha": 2}
# Golem's Client has its own in-game mod instead of Poly+
SKIP = ("polyplus",)


def get(url):
    req = urllib.request.Request(url, headers={"User-Agent": "GolemsClient-bundle-sync (github.com/VenoServices11/golems-client)"})
    with urllib.request.urlopen(req, timeout=120) as res:
        return res.read()


def mod_name(path):
    return os.path.basename(path)


_versions = {}


def modrinth_versions(project, game, loader):
    """The project's versions for this game version and loader, newest first (cached)."""
    key = (project, game, loader)
    if key not in _versions:
        q = urllib.parse.urlencode({"loaders": json.dumps([loader]), "game_versions": json.dumps([game])})
        try:
            _versions[key] = json.loads(get(f"{MODRINTH}/project/{project}/version?{q}"))
        except Exception as e:  # one mod that can't be checked doesn't stop the rest
            print(f"(couldn't check {project} on Modrinth: {e})", file=sys.stderr)
            _versions[key] = []
    return _versions[key]


def upgrade(index, game, loader, notes):
    """Each Modrinth mod takes its newest suitable version (see the top of this file)."""
    files = index.get("files", [])
    projects = {f.get("id") for f in files if f.get("id")}
    for f in files:
        project = f.get("id")
        url = (f.get("downloads") or [""])[0]
        if not project or "cdn.modrinth.com" not in url:
            continue
        versions = modrinth_versions(project, game, loader)
        sha1 = f.get("hashes", {}).get("sha1")
        current = next((v for v in versions if any(x.get("hashes", {}).get("sha1") == sha1 for x in v.get("files", []))), None)
        if current is None:
            continue  # not a version Modrinth lists for this game version: leave it
        allowed = CHANNEL.get(current.get("version_type"), 0)
        for v in versions:  # newest first
            if v["id"] == current["id"] or v.get("date_published", "") <= current.get("date_published", ""):
                break
            if CHANNEL.get(v.get("version_type"), 2) > allowed:
                continue
            missing = [d.get("project_id") for d in v.get("dependencies", [])
                       if d.get("dependency_type") == "required" and d.get("project_id") and d.get("project_id") not in projects]
            if missing:
                notes.append(f"  - skipped `{v.get('name') or v['version_number']}` for {mod_name(f['path'])}: it needs mods that aren't in the bundle")
                continue
            primary = next((x for x in v.get("files", []) if x.get("primary")), (v.get("files") or [None])[0])
            if not primary:
                continue
            f["path"] = "mods/" + primary["filename"]
            f["hashes"] = {"sha1": primary["hashes"]["sha1"], "sha512": primary["hashes"]["sha512"]}
            f["downloads"] = [primary["url"]]
            f["fileSize"] = primary["size"]
            break
    return index


def adapt(raw, game, loader, notes):
    """Their bundle, Golem's Client's way, with Modrinth's newer versions: (zip bytes, index)."""
    zin = zipfile.ZipFile(io.BytesIO(raw))
    index = json.loads(zin.read("modrinth.index.json"))
    index["files"] = [f for f in index.get("files", []) if not any(s in f["path"].lower() for s in SKIP)]
    for key in ("name", "summary"):
        if isinstance(index.get(key), str):
            index[key] = index[key].replace("OneClient", "Golem's Client")
    upgrade(index, game, loader, notes)
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
                notes = []
                packed, index = adapt(get(SOURCE + entry["path"]), version, loader, notes)
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
                    lines.extend(notes)
                fresh.append({"path": f"/golems/bundles/generated/{name}", "sha1": hashlib.sha1(packed).hexdigest()})
            if [e["path"] for e in fresh] != [e["path"] for e in entries] or [e["sha1"] for e in fresh] != [e["sha1"] for e in entries]:
                changed = True
                loaders[loader] = fresh
    if changed:
        with open(meta_path, "w", encoding="utf-8") as f:
            json.dump(ours, f, indent=2, ensure_ascii=False)
            f.write("\n")
    print("\n".join(lines) if lines else "No bundle changes today.")


if __name__ == "__main__":
    main()
