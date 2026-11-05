"""Downloads the newest Forge 1.20.1 file of each Modrinth project into a folder, checking its sha1.
Usage: fetch_mods.py <out dir> <slug>..."""
import hashlib
import json
import os
import sys
import urllib.request

UA = {"User-Agent": "dross-mod-compat-test"}
out = sys.argv[1]
os.makedirs(out, exist_ok=True)
for slug in sys.argv[2:]:
    url = ("https://api.modrinth.com/v2/project/" + slug + "/version?loaders=%5B%22forge%22%5D&game_versions=%5B%221.20.1%22%5D")
    versions = json.load(urllib.request.urlopen(urllib.request.Request(url, headers=UA)))
    v = versions[0]
    f = next((f for f in v["files"] if f.get("primary")), v["files"][0])
    data = urllib.request.urlopen(urllib.request.Request(f["url"], headers=UA)).read()
    sha1 = hashlib.sha1(data).hexdigest()
    if sha1 != f["hashes"]["sha1"]:
        raise SystemExit(slug + ": sha1 mismatch")
    name = os.path.basename(f["filename"])
    open(os.path.join(out, name), "wb").write(data)
    print(slug, v["version_number"], name, len(data), "bytes, sha1 ok")
