"""
Draws the Dross trader's candidate skins and their previews.

Run from the project folder:

    python tools/skins/draw_skins.py

It writes:
  - the textures:  src/main/resources/assets/dross/textures/entity/trader/skin_NN_<id>.png  (64x64 RGBA)
  - the previews:  skin-previews/NN_<id>_front.png and NN_<id>_back.png
  - an overview:   skin-previews/all_skins.png (every skin, front and back, numbered and named)

Extra checks (not part of the project): python tools/skins/draw_skins.py --views <folder>
renders each skin from eight directions into that folder, to look at the sides and the seams.

Needs Python 3 with Pillow and numpy. The skins themselves are in designs.py; the 3D preview renderer
(the vanilla villager model's exact geometry) is in villager_render.py.
"""
import os
import sys

sys.dont_write_bytecode = True  # no __pycache__ folder in the project

from PIL import Image, ImageDraw, ImageFont  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

from designs import SKINS  # noqa: E402
from villager_render import render  # noqa: E402

ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
TEXTURE_DIR = os.path.join(ROOT, "src", "main", "resources", "assets", "dross", "textures", "entity", "trader")
PREVIEW_DIR = os.path.join(ROOT, "skin-previews")

PREVIEW_SCALE = 12      # screen pixels per texture pixel in the single previews
SHEET_SCALE = 7         # ... and in the overview sheet
BACKGROUND = (58, 63, 74)
MAX_SIGN_TEXT = 15      # a sign line fits about 15 characters


def texture_name(number, skin_id):
    return f"skin_{number:02d}_{skin_id}.png"


def font(size):
    for name in ("segoeuib.ttf", "arialbd.ttf", "DejaVuSans-Bold.ttf"):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            continue
    return ImageFont.load_default(size=size)


def check_entry(number, skin_id, name, mood):
    if len(name) > MAX_SIGN_TEXT or len(mood) > MAX_SIGN_TEXT:
        raise ValueError(f"skin {number}: name and mood must be {MAX_SIGN_TEXT} characters or fewer")
    if skin_id != skin_id.lower() or " " in skin_id:
        raise ValueError(f"skin {number}: the id must be lowercase with no spaces")


def overview(entries):
    """Every skin's front and back, numbered and named, five to a row."""
    title_font = font(30)
    label_font = font(22)
    mood_font = font(17)
    cells = []
    for number, skin_id, name, mood, _desc, img in entries:
        front = render(img, back=False, scale=SHEET_SCALE, background=BACKGROUND)
        back = render(img, back=True, scale=SHEET_SCALE, background=BACKGROUND)
        gap = 6
        label_h = 62
        cell = Image.new("RGB", (front.width * 2 + gap, front.height + label_h), (34, 37, 45))
        cell.paste(front, (0, label_h))
        cell.paste(back, (front.width + gap, label_h))
        d = ImageDraw.Draw(cell)
        d.text((8, 4), f"{number}. {name}", font=label_font, fill=(235, 240, 255))
        d.text((8, 34), mood, font=mood_font, fill=(127, 168, 255))
        d.text((front.width - 48, label_h + 4), "front", font=mood_font, fill=(150, 160, 185))
        d.text((front.width * 2 + gap - 46, label_h + 4), "back", font=mood_font, fill=(150, 160, 185))
        cells.append(cell)

    per_row = 5
    pad = 16
    header = 60
    cw, ch = cells[0].size
    rows = (len(cells) + per_row - 1) // per_row
    sheet = Image.new("RGB", (pad + per_row * (cw + pad), header + rows * (ch + pad) + pad), (22, 24, 30))
    d = ImageDraw.Draw(sheet)
    d.text((pad, 14), "Dross trader: candidate skins (front | back)", font=title_font, fill=(235, 240, 255))
    for i, cell in enumerate(cells):
        x = pad + (i % per_row) * (cw + pad)
        y = header + (i // per_row) * (ch + pad)
        sheet.paste(cell, (x, y))
    return sheet


def main(argv):
    views_dir = None
    if len(argv) >= 3 and argv[1] == "--views":
        views_dir = argv[2]
        os.makedirs(views_dir, exist_ok=True)

    os.makedirs(TEXTURE_DIR, exist_ok=True)
    os.makedirs(PREVIEW_DIR, exist_ok=True)
    entries = []
    for number, skin_id, name, mood, desc, make in SKINS:
        check_entry(number, skin_id, name, mood)
        img = make().image()
        img.save(os.path.join(TEXTURE_DIR, texture_name(number, skin_id)))
        stem = f"{number:02d}_{skin_id}"
        render(img, back=False, scale=PREVIEW_SCALE, background=BACKGROUND).save(
            os.path.join(PREVIEW_DIR, f"{stem}_front.png"))
        render(img, back=True, scale=PREVIEW_SCALE, background=BACKGROUND).save(
            os.path.join(PREVIEW_DIR, f"{stem}_back.png"))
        entries.append((number, skin_id, name, mood, desc, img))
        if views_dir:
            wide = (-12.5, -12.0, 12.5, 24.5)
            shots = [render(img, scale=6, yaw_deg=yaw, bounds=wide, elevation_deg=12)
                     for yaw in (0, 45, 90, 135, 180, 225, 270, 315)]
            sheet = Image.new("RGB", (shots[0].width * len(shots), shots[0].height), BACKGROUND)
            for i, shot in enumerate(shots):
                sheet.paste(shot, (i * shot.width, 0))
            sheet.save(os.path.join(views_dir, f"{stem}_views.png"))
            img.resize((512, 512), Image.NEAREST).save(os.path.join(views_dir, f"{stem}_texture.png"))
        print(f"{number:2d}. {name} ({mood})")
    overview(entries).save(os.path.join(PREVIEW_DIR, "all_skins.png"))
    print(f"Wrote {len(entries)} skins to {TEXTURE_DIR}")
    print(f"Previews in {PREVIEW_DIR}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
