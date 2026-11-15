#!/usr/bin/env python3
"""Uploads a TrimPerks release (mod jar + resource pack) to Modrinth and CurseForge.

One jar per Minecraft version family; which game versions each jar is listed under comes from publish_versions in
versions/<mc>.properties. The resource pack goes with every Modrinth version and once on CurseForge (newest jar).
Run by .github/workflows/publish.yml when a GitHub release is published. Needs the MODRINTH_TOKEN and
CURSEFORGE_TOKEN secrets and the CURSEFORGE_PROJECT repository variable (the CurseForge project id). A site whose
token (or project id) isn't set is skipped.

    scripts/publish.py --tag v0.2.0 --files dl/ --changelog-file notes.md [--sites modrinth] [--dry-run]

Based on TweakSwitch's publish script. Needs only Python 3.
"""

import argparse, json, os, sys, urllib.error, urllib.request, uuid
from pathlib import Path

USER_AGENT = "Kyle-15/TrimPerks-publish (https://github.com/Kyle-15/TrimPerks)"
ROOT = Path(__file__).resolve().parent.parent
MODRINTH_API = "https://api.modrinth.com/v2"
MODRINTH_PROJECT = "trimperks"
MODRINTH_DEPENDENCIES = [{"project_id": "P7dR8mSH", "dependency_type": "required"}]  # Fabric API
CURSEFORGE_API = "https://minecraft.curseforge.com/api"
CURSEFORGE_RELATIONS = [{"slug": "fabric-api", "type": "requiredDependency"}]
# The release notes' GitHub-only tail (server.properties lines etc.) starts here; the changelog is what comes before
CHANGELOG_ENDS_AT = "### Resource pack"


class PublishError(Exception):
    """Something a person has to fix: a missing file, a refused token, an unknown game version."""


def request(url, headers=None, data=None, content_type=None, method=None):
    all_headers = {"User-Agent": USER_AGENT, "Accept": "application/json", **(headers or {})}
    if content_type:
        all_headers["Content-Type"] = content_type
    try:
        with urllib.request.urlopen(urllib.request.Request(url, data=data, headers=all_headers, method=method), timeout=180) as reply:
            body = reply.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as error:
        raise PublishError(f"{url} answered HTTP {error.code}: {error.read().decode('utf-8', 'replace')[:500]}") from None
    except urllib.error.URLError as error:
        raise PublishError(f"Couldn't reach {url}: {error.reason}") from None
    return json.loads(body) if body.strip() else None


def multipart(fields, files):
    """fields: {name: text}; files: [(field, file name, bytes, content type)]."""
    boundary = "----trimperks" + uuid.uuid4().hex
    parts = [f'--{boundary}\r\nContent-Disposition: form-data; name="{k}"\r\n\r\n'.encode() + v.encode() + b"\r\n" for k, v in fields.items()]
    for field, name, data, ctype in files:
        parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="{field}"; filename="{name}"\r\n'
                     f"Content-Type: {ctype}\r\n\r\n".encode() + data + b"\r\n")
    parts.append(f"--{boundary}--\r\n".encode())
    return b"".join(parts), f"multipart/form-data; boundary={boundary}"


def families():
    """{"1.21.10": {"game_versions": [...], "java": "21"}, ...} for every versions/*.properties with publish_versions, oldest first."""
    found = {}
    for path in (ROOT / "versions").glob("*.properties"):
        props = dict(line.split("=", 1) for line in path.read_text().splitlines() if "=" in line and not line.startswith("#"))
        if props.get("publish_versions", "").strip():
            found[path.stem] = {"game_versions": [v.strip() for v in props["publish_versions"].split(",") if v.strip()],
                                "java": props.get("java_version", "").strip()}
    key = lambda v: [int(x) if x.isdigit() else 0 for x in v.split(".")]
    return dict(sorted(found.items(), key=lambda item: key(item[0])))


def publish_modrinth(token, version, jobs, pack, changelog, dry_run):
    print("\n== Modrinth")
    auth = {"Authorization": token}
    project = request(f"{MODRINTH_API}/project/{MODRINTH_PROJECT}", auth)
    print(f"Project {project['title']} ({project['id']}), status: {project.get('status')}")
    existing = {v["version_number"] for v in request(f"{MODRINTH_API}/project/{project['id']}/version", auth)}
    for mc, family, jar in jobs:
        publish_modrinth_one(auth, project, version, mc, family, jar, pack, changelog, dry_run, existing)


def publish_modrinth_one(auth, project, version, mc, family, jar, pack, changelog, dry_run, existing):
    number = f"{version}+{mc}"
    if number in existing:
        print(f"- {number} is already there: skipped")
        return
    data = {"name": f"TrimPerks {version} ({mc.replace('1.21.10', '1.21.9-1.21.10').replace('26.1', '26.1.x')})",
            "version_number": number, "changelog": changelog,
            "dependencies": MODRINTH_DEPENDENCIES, "game_versions": family["game_versions"], "version_type": "release",
            "loaders": ["fabric"], "featured": mc == list(families())[-1], "status": "listed", "project_id": project["id"],
            "file_parts": ["file", "pack"], "primary_file": "file"}
    if dry_run:
        print(f"- would upload {jar.name} + {pack.name} as {number}")
        return
    body, ctype = multipart({"data": json.dumps(data)}, [("file", jar.name, jar.read_bytes(), "application/java-archive"),
                                                         ("pack", pack.name, pack.read_bytes(), "application/zip")])
    created = request(f"{MODRINTH_API}/version", auth, body, ctype)
    print(f"- uploaded {number} for {', '.join(family['game_versions'])} (version id {created['id']})")
    # Modrinth keeps the environment per version (v3 API) and won't list a version without it
    request(f"{MODRINTH_API.replace('/v2', '/v3')}/version/{created['id']}", auth,
            json.dumps({"environment": "server_only_client_optional"}).encode(), "application/json", "PATCH")
    print("- set environment: server-side, optional on the client")


def curseforge_ids(token, family):
    auth = {"X-Api-Token": token}
    types = {t["id"]: t["slug"] for t in request(f"{CURSEFORGE_API}/game/version-types", auth)}
    versions = request(f"{CURSEFORGE_API}/game/versions", auth)

    def find(name, ok):
        return next((v["id"] for v in versions if v["name"] == name and ok(types.get(v["gameVersionTypeID"], ""))), None)

    ids = []
    for gv in family["game_versions"]:
        found = find(gv, lambda slug: slug.startswith("minecraft"))
        if found is None:
            raise PublishError(f"CurseForge doesn't know game version {gv} yet")
        ids.append(found)
    fabric = find("Fabric", lambda slug: slug == "modloader")
    if fabric is None:
        raise PublishError("CurseForge's version list has no Fabric entry")
    ids.append(fabric)
    for name, slug in (("Server", "environment"), (f"Java {family['java']}", "java")):
        found = find(name, lambda s, want=slug: s == want)
        if found is not None:
            ids.append(found)
    return ids


def publish_curseforge(token, project_id, version, jobs, pack, changelog, dry_run):
    print("\n== CurseForge")
    auth = {"X-Api-Token": token}
    created = None
    for mc, family, jar in jobs:
        ids = curseforge_ids(token, family)
        name = f"TrimPerks {version}+{mc}"
        if dry_run:
            print(f"- would upload {jar.name} for {', '.join(family['game_versions'])} to project {project_id}, version ids {ids}")
            continue
        meta = {"changelog": changelog, "changelogType": "markdown", "displayName": name, "gameVersions": ids,
                "releaseType": "release", "relations": {"projects": CURSEFORGE_RELATIONS}}
        body, ctype = multipart({"metadata": json.dumps(meta)}, [("file", jar.name, jar.read_bytes(), "application/java-archive")])
        created = request(f"{CURSEFORGE_API}/projects/{project_id}/upload-file", auth, body, ctype)
        print(f"- uploaded {jar.name} (file id {created['id']})")
    if dry_run:
        print(f"- would add {pack.name} as an additional file of the newest jar")
        return
    extra = {"changelog": "Resource pack: trim textures on tools and weapons for players without the mod.",
             "changelogType": "markdown", "displayName": f"TrimPerks {version} resource pack", "parentFileID": created["id"],
             "releaseType": "release"}
    body, ctype = multipart({"metadata": json.dumps(extra)}, [("file", pack.name, pack.read_bytes(), "application/zip")])
    request(f"{CURSEFORGE_API}/projects/{project_id}/upload-file", auth, body, ctype)
    print(f"- uploaded {pack.name} as an additional file")


def main(argv=None):
    parser = argparse.ArgumentParser(description="Upload a TrimPerks release to Modrinth and CurseForge.")
    parser.add_argument("--tag", required=True)
    parser.add_argument("--files", required=True, type=Path, help="folder with the release's jar and TrimPerks-Resources.zip")
    parser.add_argument("--changelog-file", type=Path)
    parser.add_argument("--sites", default="modrinth,curseforge")
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args(argv)
    version = args.tag.removeprefix("v")
    try:
        pack = args.files / "TrimPerks-Resources.zip"
        jobs = [(mc, fam, args.files / f"trimperks-{version}+{mc}.jar") for mc, fam in families().items()]
        for f in [pack] + [job[2] for job in jobs]:
            if not f.is_file():
                raise PublishError(f"{f} is missing from the release")
        notes = args.changelog_file.read_text() if args.changelog_file else ""
        cut = notes.find(CHANGELOG_ENDS_AT)
        changelog = (notes if cut < 0 else notes[:cut]).strip()
        if not changelog and not args.dry_run:
            raise PublishError("The release has no notes to use as the changelog")
        tokens = {"modrinth": os.environ.get("MODRINTH_TOKEN", "").strip(), "curseforge": os.environ.get("CURSEFORGE_TOKEN", "").strip()}
        cf_project = os.environ.get("CURSEFORGE_PROJECT", "").strip()
        print(f"TrimPerks {version}{' (dry run)' if args.dry_run else ''}")
        for site in [s.strip() for s in args.sites.split(",") if s.strip()]:
            if not tokens.get(site):
                print(f"\n== {site.capitalize()}\nNo {site.upper()}_TOKEN secret on this repository: skipped.")
            elif site == "modrinth":
                publish_modrinth(tokens[site], version, jobs, pack, changelog, args.dry_run)
            elif not cf_project:
                print("\n== CurseForge\nNo CURSEFORGE_PROJECT repository variable (the project id): skipped.")
            else:
                publish_curseforge(tokens[site], cf_project, version, jobs, pack, changelog, args.dry_run)
    except PublishError as error:
        print(f"\nStopped: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
