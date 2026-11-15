#!/usr/bin/env python3
"""
Generates the tool/weapon trim overlays, item models and the TrimPerks resource pack.

    python3 tools/gen_tool_trims.py <path to the 26.3 minecraft-client.jar>

The client jar is only read for tool shapes (which pixels are the head, which are the handle) and the vanilla item
definitions that the overlays wrap. No vanilla textures are copied into the output. Writes:
  src/main/resources/assets/...      (modded clients get the textures straight from the mod)
  build/TrimPerks-Resources.zip      (for players without the mod; set it as the server resource pack)
  build/tool-trim-preview.png        (every pattern on every tool, for checking)
No libraries needed: PNGs are read and written by hand.
"""
import json, math, os, struct, sys, zipfile, zlib

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src/main/resources")
PATTERNS = ["sentry", "dune", "coast", "wild", "ward", "eye", "vex", "tide", "snout", "rib", "spire", "wayfinder",
            "shaper", "silence", "raiser", "host", "flow", "bolt"]
# Trim material colors (constant tints over the gray overlay)
MATERIALS = {"iron": 0xD8D8D8, "copper": 0xC06A4A, "gold": 0xEFC23A, "redstone": 0xC41C0C, "lapis": 0x3A64B4,
             "amethyst": 0xA368D6, "emerald": 0x1BB348, "quartz": 0xF1E9DF, "diamond": 0x5FE8D6,
             "netherite": 0x4A4042, "resin": 0xF27A1A}
# class -> (texture that gives the shape, model to inherit display settings from, which part gets the overlay)
CLASSES = {
    "sword": ("iron_sword", "minecraft:item/iron_sword", "head"),
    "axe": ("iron_axe", "minecraft:item/iron_axe", "head"),
    "pickaxe": ("iron_pickaxe", "minecraft:item/iron_pickaxe", "head"),
    "shovel": ("iron_shovel", "minecraft:item/iron_shovel", "head"),
    "hoe": ("iron_hoe", "minecraft:item/iron_hoe", "head"),
    "mace": ("mace", "minecraft:item/mace", "head"),
    "fishing_rod": ("fishing_rod", "minecraft:item/fishing_rod", "wood"),
    "bow": ("bow", "minecraft:item/bow", "wood"),
    "crossbow": ("crossbow_standby", "minecraft:item/crossbow", "wood"),
}
TIERS = ["wooden", "stone", "copper", "iron", "golden", "diamond", "netherite"]
ITEMS = {f"{t}_{c}": c for t in TIERS for c in ["sword", "axe", "pickaxe", "shovel", "hoe"]}
ITEMS.update({"mace": "mace", "fishing_rod": "fishing_rod", "bow": "bow", "crossbow": "crossbow"})
# Trident is left out on purpose: in hand it's a 3D model, and a flat overlay would float next to it.


# ---------------------------------------------------------------- PNG

def read_png(data):
    assert data[:8] == b"\x89PNG\r\n\x1a\n"
    pos, idat, palette, trns = 8, b"", None, None
    while pos < len(data):
        length, kind = struct.unpack(">I4s", data[pos:pos + 8])
        chunk = data[pos + 8:pos + 8 + length]
        pos += 12 + length
        if kind == b"IHDR":
            w, h, depth, ctype, _, _, interlace = struct.unpack(">IIBBBBB", chunk)
            assert interlace == 0
        elif kind == b"PLTE":
            palette = [tuple(chunk[i:i + 3]) for i in range(0, len(chunk), 3)]
        elif kind == b"tRNS":
            trns = chunk
        elif kind == b"IDAT":
            idat += chunk
    raw = zlib.decompress(idat)
    channels = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[ctype]
    bpp = max(1, channels * depth // 8)
    stride = (w * channels * depth + 7) // 8
    rows, prev, i = [], bytearray(stride), 0
    for _ in range(h):
        f, line = raw[i], bytearray(raw[i + 1:i + 1 + stride])
        i += 1 + stride
        for x in range(stride):
            a = line[x - bpp] if x >= bpp else 0
            b, c = prev[x], prev[x - bpp] if x >= bpp else 0
            if f == 1: line[x] = (line[x] + a) & 255
            elif f == 2: line[x] = (line[x] + b) & 255
            elif f == 3: line[x] = (line[x] + (a + b) // 2) & 255
            elif f == 4:
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                line[x] = (line[x] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 255
        rows.append(line)
        prev = line
    px = []
    for line in rows:
        row = []
        for x in range(w):
            if ctype == 3:
                bit = x * depth
                idx = (line[bit // 8] >> (8 - depth - bit % 8)) & ((1 << depth) - 1)
                r, g, b = palette[idx]
                row.append((r, g, b, trns[idx] if trns and idx < len(trns) else 255))
            elif ctype == 6: row.append(tuple(line[x * 4:x * 4 + 4]))
            elif ctype == 2: row.append(tuple(line[x * 3:x * 3 + 3]) + (255,))
            elif ctype == 4: row.append((line[x * 2],) * 3 + (line[x * 2 + 1],))
            else: row.append((line[x],) * 3 + (255,))
        px.append(row)
    return px


def write_png(px):
    h, w = len(px), len(px[0])
    raw = b"".join(b"\x00" + bytes(v for p in row for v in p) for row in px)
    def chunk(kind, body):
        return struct.pack(">I", len(body)) + kind + body + struct.pack(">I", zlib.crc32(kind + body) & 0xFFFFFFFF)
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


# ---------------------------------------------------------------- shapes and patterns

def lum(p): return 0.3 * p[0] + 0.59 * p[1] + 0.11 * p[2]


def is_wood(p):
    r, g, b, _ = p
    return r > g > b and r - b > 25 and (max(r, g, b) - min(r, g, b)) / max(1, max(r, g, b)) > 0.22


def mask_of(px, part):
    m = set()
    for y, row in enumerate(px):
        for x, p in enumerate(row):
            if p[3] < 128 or lum(p) < 40: continue  # skip transparent and the dark outline
            if part == "head" and (x < 6 and y > 9 or p[2] > p[0] + 20): continue  # handle corner; the mace's blue rod
            if (part == "wood") == is_wood(p): m.add((x, y))
    return m


def pattern(name, m):
    edge = {(x, y) for x, y in m if any((x + dx, y + dy) not in m for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))}
    inner = m - edge
    cx = sum(x for x, _ in m) / len(m)
    cy = sum(y for _, y in m) / len(m)
    h = lambda x, y: (x * 73856093 ^ y * 19349663) % 7
    d = lambda x, y: abs(x - cx) + abs(y - cy)
    rules = {
        "sentry": lambda x, y: (x, y) in edge,
        "dune": lambda x, y: y % 3 == 0,
        "coast": lambda x, y: (y + round(math.sin(x * 0.9) * 1.2)) % 4 == 0,
        "wild": lambda x, y: h(x, y) < 2,
        "ward": lambda x, y: (x, y) in inner and (x + y) % 2 == 0,
        "eye": lambda x, y: d(x, y) <= 1.5 or 3 <= d(x, y) <= 3.5,
        "vex": lambda x, y: (x + y) % 4 == 0,
        "tide": lambda x, y: (x - y) % 4 == 0,
        "snout": lambda x, y: abs(y - cy) <= 1 and 1 <= abs(x - cx) <= 2.5,
        "rib": lambda x, y: x % 3 == 0,
        "spire": lambda x, y: abs((x - cx) - (cy - y)) <= 0.6,
        "wayfinder": lambda x, y: abs(x - cx) <= 0.6 or abs(y - cy) <= 0.6,
        "shaper": lambda x, y: (x, y) in edge and (x + y) % 2 == 0,
        "silence": lambda x, y: (x, y) in inner and y < cy,
        "raiser": lambda x, y: (y + abs(x - round(cx))) % 4 == 0,
        "host": lambda x, y: 1.5 <= d(x, y) <= 2.6,
        "flow": lambda x, y: (x * x + y * y) % 9 < 3,
        "bolt": lambda x, y: (x + (y // 2 % 2) * 2) % 5 == 0,
    }
    picked = {p for p in m if rules[name](*p)}
    return picked if len(picked) >= 3 else edge  # tiny heads: fall back to the outline


def overlay(px, picked):
    vals = [lum(px[y][x]) for x, y in picked] or [1]
    lo, hi = min(vals), max(vals)
    out = [[(0, 0, 0, 0)] * len(px[0]) for _ in px]
    for x, y in picked:
        t = (lum(px[y][x]) - lo) / (hi - lo) if hi > lo else 1
        v = int(175 + 80 * t)
        out[y][x] = (v, v, v, 255)
    return out


# ---------------------------------------------------------------- output

def signed(argb): return argb - (1 << 32) if argb >= 1 << 31 else argb


# The tool's own material: a trim in that same material gets a darker shade so it stays visible (vanilla armor does this)
TIER_MATERIAL = {"iron": "iron", "golden": "gold", "copper": "copper", "diamond": "diamond", "netherite": "netherite"}


def shade(c, matching):
    if not matching:
        return c
    r, g, b = (c >> 16) & 255, (c >> 8) & 255, c & 255
    if 0.3 * r + 0.59 * g + 0.11 * b < 100:  # dark trims (netherite) on their own tool: lighter instead of darker
        r, g, b = (int(v + (255 - v) * 0.45) for v in (r, g, b))
    else:
        r, g, b = (int(v * 0.55) for v in (r, g, b))
    return (r << 16) | (g << 8) | b


def item_definition(vanilla, cls, item):
    own = TIER_MATERIAL.get(item.split("_")[0])
    cases = []
    for p in PATTERNS:
        model = f"trimperks:item/trim/{cls}_{p}"
        cases.append({"when": f"trimperks:{p}", "model": {
            "type": "minecraft:select", "property": "minecraft:trim_material",
            "cases": [{"when": f"minecraft:{m}", "model": {"type": "minecraft:model", "model": model,
                       "tints": [{"type": "minecraft:constant", "value": signed(0xFF000000 | shade(c, m == own))}]}} for m, c in MATERIALS.items()],
            "fallback": {"type": "minecraft:model", "model": model}}})
    trim = {"type": "minecraft:select", "property": "minecraft:custom_model_data", "index": 0, "cases": cases,
            "fallback": {"type": "minecraft:empty"}}
    return {"model": {"type": "minecraft:composite", "models": [vanilla["model"], trim]}}


def main():
    jar = zipfile.ZipFile(sys.argv[1])
    tex = lambda n: read_png(jar.read(f"assets/minecraft/textures/item/{n}.png"))
    files = {}
    preview_rows = []
    for cls, (shape, parent, part) in CLASSES.items():
        base = tex(shape)
        m = mask_of(base, part)
        row = []
        for p in PATTERNS:
            ov = overlay(base, pattern(p, m))
            files[f"assets/trimperks/textures/item/trim/{cls}_{p}.png"] = write_png(ov)
            files[f"assets/trimperks/models/item/trim/{cls}_{p}.json"] = json.dumps(
                {"parent": parent, "textures": {"layer0": f"trimperks:item/trim/{cls}_{p}"}}, indent=2).encode()
            # preview: overlay tinted gold over the base sprite
            c = MATERIALS["gold"]
            tint = ((c >> 16) & 255, (c >> 8) & 255, c & 255)
            cell = [[(ov[y][x][0] * tint[0] // 255, ov[y][x][1] * tint[1] // 255, ov[y][x][2] * tint[2] // 255, 255)
                     if ov[y][x][3] else base[y][x] for x in range(16)] for y in range(16)]
            row.append(cell)
        preview_rows.append(row)
    for item, cls in ITEMS.items():
        vanilla = json.loads(jar.read(f"assets/minecraft/items/{item}.json"))
        files[f"assets/minecraft/items/{item}.json"] = json.dumps(item_definition(vanilla, cls, item), separators=(",", ":")).encode()

    for path, data in files.items():
        full = os.path.join(OUT, path)
        os.makedirs(os.path.dirname(full), exist_ok=True)
        open(full, "wb").write(data)

    os.makedirs(os.path.join(ROOT, "build"), exist_ok=True)
    mcmeta = {"pack": {"description": "TrimPerks: trims on tools and weapons", "min_format": [97, 0], "max_format": [97, 1]}}
    with zipfile.ZipFile(os.path.join(ROOT, "build/TrimPerks-Resources.zip"), "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("pack.mcmeta", json.dumps(mcmeta, indent=2))
        for path, data in sorted(files.items()):
            z.writestr(path, data)

    # preview sheet: 18 patterns across, one tool class per row, 6x scale, dark background
    s, pad = 6, 2
    W, H = len(PATTERNS) * (16 + pad) * s, len(CLASSES) * (16 + pad) * s
    sheet = [[(40, 40, 46, 255)] * W for _ in range(H)]
    for r, row in enumerate(preview_rows):
        for c, cell in enumerate(row):
            for y in range(16):
                for x in range(16):
                    p = cell[y][x]
                    if p[3] < 128: continue
                    for yy in range(s):
                        for xx in range(s):
                            sheet[(r * (16 + pad) + y) * s + yy][(c * (16 + pad) + x) * s + xx] = p
    open(os.path.join(ROOT, "build/tool-trim-preview.png"), "wb").write(write_png(sheet))

    # every material on a few tools (a darker shade where the trim matches the tool's own material)
    picks = [("iron_sword", "sword", "sentry"), ("diamond_sword", "sword", "rib"), ("netherite_pickaxe", "pickaxe", "vex"),
             ("golden_axe", "axe", "bolt"), ("copper_shovel", "shovel", "ward"), ("bow", "bow", "flow")]
    masks = {cls: mask_of(tex(CLASSES[cls][0]), CLASSES[cls][2]) for _, cls, _ in picks}
    rows = []
    for m, c in MATERIALS.items():
        row = []
        for item, cls, pat in picks:
            base = tex(item)
            ov = overlay(tex(CLASSES[cls][0]), pattern(pat, masks[cls]))
            own = TIER_MATERIAL.get(item.split("_")[0])
            cc = shade(c, m == own)
            t = ((cc >> 16) & 255, (cc >> 8) & 255, cc & 255)
            row.append([[(ov[y][x][0] * t[0] // 255, ov[y][x][1] * t[1] // 255, ov[y][x][2] * t[2] // 255, 255)
                         if ov[y][x][3] else base[y][x] for x in range(16)] for y in range(16)])
        rows.append(row)
    W, H = len(picks) * (16 + pad) * s, len(MATERIALS) * (16 + pad) * s
    sheet = [[(40, 40, 46, 255)] * W for _ in range(H)]
    for r, row in enumerate(rows):
        for c2, cell in enumerate(row):
            for y in range(16):
                for x in range(16):
                    if cell[y][x][3] < 128: continue
                    for yy in range(s):
                        for xx in range(s):
                            sheet[(r * (16 + pad) + y) * s + yy][(c2 * (16 + pad) + x) * s + xx] = cell[y][x]
    open(os.path.join(ROOT, "build/tool-trim-materials.png"), "wb").write(write_png(sheet))
    print(f"{len(files)} files, {len(ITEMS)} items, {len(CLASSES)} tool shapes x {len(PATTERNS)} patterns")


if __name__ == "__main__":
    main()
