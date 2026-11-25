"""
What takes up the room in a built Dross jar (the user, November 25, 2026: "check what parts take up the most space in
the jar file"). Not part of the mod.

  python tools/release_check/jar_size.py build/libs/dross-<version>-1.20.1-beta.jar [older jar to compare with]

It prints:
  - the jar's size, in it and unpacked; its folders (two levels and three under assets/data), its file types;
  - the biggest folders of entity pictures, sounds, models, animations and structures, and the 25 biggest files;
  - what may never be used: sound files no sound event names (Minecraft never loads them), sound files only a
    showcase's numbered versions play (`<event>.vN`: alternatives kept for the user to listen to), and pictures no class
    or JSON names by path, name or stem (most of those are loaded by names built while the game runs, like
    `<look>_iceglow` or a trader skin's number: check by hand before removing anything);
  - with an older jar: what grew or shrank most, folder by folder.

Sizes are as stored in the jar (compressed), which is what a download costs.
"""
import collections
import json
import re
import sys
import zipfile


def files(path):
    z = zipfile.ZipFile(path)
    return z, [i for i in z.infolist() if not i.is_dir()]


def folder(name):
    p = name.split("/")
    if name.startswith(("assets/dross/", "data/dross/")):
        return "/".join(p[:4]) if len(p) > 4 else "/".join(p[:3])
    if name.startswith("com/reg21meme/dross/"):
        return "/".join(p[:4]) if len(p) > 4 else "/".join(p[:3])
    return "/".join(p[:2]) if len(p) > 2 else p[0]


def table(rows, total, top=None):
    for key, (c, u, n) in sorted(rows.items(), key=lambda kv: -kv[1][0])[:top]:
        print(f"  {c / 1e6:6.2f} MB {100 * c / total:5.1f}%   unpacked {u / 1e6:6.2f} MB  {n:5d} files  {key}")


def breakdown(z, infos):
    total = sum(i.compress_size for i in infos)
    print(f"{len(infos)} files: {total / 1e6:.2f} MB in the jar, {sum(i.file_size for i in infos) / 1e6:.2f} MB unpacked")
    for title, key in (("By folder", folder),
                       ("By file type", lambda n: "." + n.rsplit(".", 1)[-1] if "." in n.split("/")[-1] else "(none)")):
        rows = collections.defaultdict(lambda: [0, 0, 0])
        for i in infos:
            r = rows[key(i.filename)]
            r[0] += i.compress_size
            r[1] += i.file_size
            r[2] += 1
        print(f"\n{title}:")
        table(rows, total, top=30)

    rows = collections.defaultdict(lambda: [0, 0, 0])
    for i in infos:
        n = i.filename
        if n.startswith(("assets/dross/textures/entity/", "assets/dross/geo/entity/", "assets/dross/animations/entity/")):
            k = "/".join(n.split("/")[:5])
        elif n.startswith(("assets/dross/sounds/", "data/dross/structures/", "assets/dross/textures/block/")):
            k = "/".join(n.split("/")[:4 if n.count("/") < 5 else 5])
        else:
            continue
        r = rows[k]
        r[0] += i.compress_size
        r[1] += i.file_size
        r[2] += 1
    print("\nBiggest folders of pictures, sounds, models, animations and structures:")
    table(rows, total, top=30)

    print("\nThe 25 biggest files:")
    for i in sorted(infos, key=lambda i: -i.compress_size)[:25]:
        print(f"  {i.compress_size / 1e3:7.0f} KB  {i.filename}")
    return total


def unused(z, infos):
    by = {i.filename: i for i in infos}
    size = lambda n: by[n].compress_size
    blob = b"\n".join(z.read(n) for n in by if n.endswith((".class", ".json", ".mcmeta", ".toml")))

    sounds = json.loads(z.read("assets/dross/sounds.json"))
    events_of = collections.defaultdict(set)
    for event, e in sounds.items():
        for s in e.get("sounds", []):
            events_of[(s if isinstance(s, str) else s["name"]).split(":", 1)[-1]].add(event)
    oggs = [n for n in by if n.endswith(".ogg")]
    nobody, showcase = [], []
    for n in oggs:
        events = events_of.get(n[len("assets/dross/sounds/"):-4])
        if not events:
            nobody.append(n)
        elif all(re.search(r"\.v\d+$", e) for e in events):
            showcase.append(n)
    print(f"\nSounds: {len(oggs)} files, {sum(map(size, oggs)) / 1e6:.2f} MB")
    print(f"  named by no sound event (never loaded): {len(nobody)} files, {sum(map(size, nobody)) / 1e3:.0f} KB")
    for n in nobody[:20]:
        print(f"    {n}")
    print(f"  only a showcase's numbered versions play them: {len(showcase)} files, "
          f"{sum(map(size, showcase)) / 1e3:.0f} KB")
    per = collections.Counter()
    for n in showcase:
        per[n.split("/")[3]] += size(n)
    for k, v in per.most_common():
        print(f"    {v / 1e3:6.0f} KB  {k}")

    pngs = [n for n in by if n.endswith(".png") and n.startswith("assets/dross/textures/")]
    unnamed = []
    for n in pngs:
        rel = n[len("assets/dross/textures/"):-4]
        stem = rel.split("/")[-1]
        names = {rel, stem, re.sub(r"(_[a-z]*glow|_ice|_cracks|_statue|_flow_base|_flow|_fire\d+|_clear|_rage)+$", "", stem),
                 re.sub(r"(_fire\d+)?(_glow)?$", "", stem)}
        if not any(x.encode() in blob for x in names if x):
            unnamed.append(n)
    print(f"\nPictures: {len(pngs)} files, {sum(map(size, pngs)) / 1e6:.2f} MB; named nowhere by path, name or stem: "
          f"{len(unnamed)} files, {sum(map(size, unnamed)) / 1e3:.0f} KB (most are loaded by names built at run time)")
    for n in sorted(unnamed, key=lambda n: -size(n))[:20]:
        print(f"  {size(n) / 1e3:6.0f} KB  {n}")


def compare(infos, old_infos):
    grew = collections.Counter()
    for i in infos:
        grew[folder(i.filename)] += i.compress_size
    for i in old_infos:
        grew[folder(i.filename)] -= i.compress_size
    print("\nCompared with the older jar (in the jar, MB): grew most")
    for k, v in grew.most_common(15):
        if v > 0:
            print(f"  +{v / 1e6:5.2f}  {k}")
    print("shrank most")
    for k, v in sorted(grew.items(), key=lambda kv: kv[1])[:10]:
        if v < 0:
            print(f"  {v / 1e6:6.2f}  {k}")


def main(argv):
    if not argv:
        sys.exit(__doc__)
    z, infos = files(argv[0])
    total = breakdown(z, infos)
    unused(z, infos)
    if len(argv) > 1:
        _oz, old = files(argv[1])
        old_total = sum(i.compress_size for i in old)
        print(f"\nThe older jar: {old_total / 1e6:.2f} MB; this one {total / 1e6:.2f} MB "
              f"({(total - old_total) / 1e6:+.2f} MB)")
        compare(infos, old)


if __name__ == "__main__":
    main(sys.argv[1:])
