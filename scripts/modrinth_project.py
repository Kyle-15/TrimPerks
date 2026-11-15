#!/usr/bin/env python3
"""One-time Modrinth page setup, run from .github/workflows/modrinth-setup.yml (it has the MODRINTH_TOKEN secret).

    scripts/modrinth_project.py create   # makes the "trimperks" project as a draft, with icon, summary and description
    scripts/modrinth_project.py update   # pushes listing/description.md, summary and links to the existing project
    scripts/modrinth_project.py submit   # sends the draft for Modrinth's review (needs at least one version uploaded)

The token needs Modrinth's "Create projects" and "Write projects" scopes.
"""

import json, os, sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from publish import MODRINTH_API, PublishError, multipart, request  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
LISTING = json.loads((ROOT / "listing/listing.json").read_text())


def main():
    action = sys.argv[1] if len(sys.argv) > 1 else ""
    token = os.environ.get("MODRINTH_TOKEN", "").strip()
    if not token:
        print("No MODRINTH_TOKEN secret on this repository.", file=sys.stderr)
        return 1
    auth = {"Authorization": token}
    body_md = (ROOT / "listing/description.md").read_text()
    try:
        if action == "create":
            data = {"slug": "trimperks", "title": LISTING["title"], "description": LISTING["summary"], "body": body_md,
                    "categories": LISTING["modrinth_categories"], "additional_categories": [],
                    "client_side": "optional", "server_side": "required", "license_id": "MIT", "project_type": "mod",
                    "source_url": LISTING["source"], "issues_url": LISTING["issues"], "initial_versions": [], "is_draft": True}
            payload, ctype = multipart({"data": json.dumps(data)}, [("icon", "icon.png", (ROOT / "listing/icon.png").read_bytes(), "image/png")])
            created = request(f"{MODRINTH_API}/project", auth, payload, ctype)
            print(f"Created draft project {created['slug']} ({created['id']}): https://modrinth.com/mod/{created['slug']}")
        elif action == "update":
            patch = {"title": LISTING["title"], "description": LISTING["summary"], "body": body_md,
                     "categories": LISTING["modrinth_categories"], "source_url": LISTING["source"], "issues_url": LISTING["issues"]}
            request(f"{MODRINTH_API}/project/trimperks", auth, json.dumps(patch).encode(), "application/json", "PATCH")
            print("Updated the Modrinth page")
        elif action == "submit":
            request(f"{MODRINTH_API}/project/trimperks", auth, json.dumps({"status": "processing"}).encode(), "application/json", "PATCH")
            print("Submitted for review; Modrinth's moderators approve it (usually within a few days)")
        else:
            print(__doc__)
            return 1
    except PublishError as error:
        print(f"Stopped: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
