#!/usr/bin/env python3
"""
Lists what changed in OneClient's code since Golem's Client last took it in, as Markdown for the
"OneClient updates" issue. The commits Golem's Client is based on are in upstream.json; after bringing changes over,
move that repository's commit forward.

Copyright (C) 2026 veno15 - GNU General Public License v3.0.
"""
import json
import os
import urllib.request

API = "https://api.github.com"


def get(path):
    req = urllib.request.Request(API + path, headers={"Accept": "application/vnd.github+json",
                                                      "Authorization": "Bearer " + os.environ.get("GH_TOKEN", ""),
                                                      "User-Agent": "GolemsClient-upstream-check"})
    with urllib.request.urlopen(req, timeout=60) as res:
        return json.load(res)


def main():
    here = os.path.dirname(os.path.abspath(__file__))
    with open(os.path.join(here, "upstream.json"), encoding="utf-8") as f:
        upstream = json.load(f)
    out = ["Updated every Monday by the \"Keep up with OneClient\" workflow. Bundles (the mods) update by themselves; "
           "these are code changes in OneClient's parts to look at and bring over by hand.", ""]
    for repo, info in upstream.items():
        branch = get(f"/repos/{repo}")["default_branch"]
        compare = get(f"/repos/{repo}/compare/{info['commit']}...{branch}")
        commits = compare.get("commits", [])
        out.append(f"## {info['what']} ({repo})")
        out.append(f"{compare.get('ahead_by', len(commits))} new commits since `{info['commit'][:7]}`.")
        out.append("")
        for c in reversed(commits[-60:]):
            title = c["commit"]["message"].splitlines()[0][:120]
            date = c["commit"]["committer"]["date"][:10]
            out.append(f"- [ ] {date} [{title}]({c['html_url']})")
        if len(commits) > 60:
            out.append(f"- ...and {len(commits) - 60} older ones")
        out.append("")
    print("\n".join(out))


if __name__ == "__main__":
    main()
