"""Puts every shrine's 3D preview on one sheet: shrine-previews/all_shrines.png (run after build.py)."""
import glob
import os

from PIL import Image, ImageDraw

import render

HERE = os.path.dirname(os.path.abspath(__file__))
PREVIEWS = os.path.normpath(os.path.join(HERE, "..", "..", "shrine-previews"))
CELL_W, CELL_H = 760, 620
COLS = 2


def main():
    files = sorted(glob.glob(os.path.join(PREVIEWS, "[0-9][0-9]_*_3d.png")))
    rows = (len(files) + COLS - 1) // COLS
    title_h = 70
    sheet = Image.new("RGBA", (COLS * CELL_W, title_h + rows * CELL_H), render.BG)
    d = ImageDraw.Draw(sheet)
    d.text((16, 12), "Dross portal shrine candidates: 10 designs", font=render.font(30, bold=True), fill=(235, 238, 245, 255))
    d.text((16, 46), "3D views from the front-right. Each file NN_*_top.png / _side.png has the top-down and side views.",
           font=render.font(16), fill=(170, 178, 195, 255))
    for i, f in enumerate(files):
        im = Image.open(f).convert("RGBA")
        scale = min((CELL_W - 16) / im.width, (CELL_H - 16) / im.height)
        im = im.resize((int(im.width * scale), int(im.height * scale)), Image.LANCZOS)
        cx = (i % COLS) * CELL_W + (CELL_W - im.width) // 2
        cy = title_h + (i // COLS) * CELL_H + (CELL_H - im.height) // 2
        sheet.alpha_composite(im, (cx, cy))
    out = os.path.join(PREVIEWS, "all_shrines.png")
    sheet.convert("RGB").save(out)
    print("wrote", out, sheet.size)


if __name__ == "__main__":
    main()
