"""
Paints a candidate's four textures from its materials (rimekit/mats.py):

  <id>.png           the solid parts (Minecraft's cut-out entity rendering): see-through texels are holes here
  <id>_ice.png       only the see-through ice (alpha between 26 and 249), drawn after the solid parts with blending
  <id>_glow.png      what glows on the solid parts, drawn full-bright BEFORE the ice, so a glowing heart or eye shows
                     through the ice in front of it
  <id>_iceglow.png   what glows on the ice itself (the aurora's curtains, glowing cracks), drawn full-bright AFTER it

The mod draws them in that order (boss/rimefall/client/RimefallCandidateRenderer). Painting works on the box-UV
texture texel by texel (kit/paint.py TexelMap), each texel knowing where it sits on the model.
"""
import numpy as np

from kit import paint
from kit.paint import rgba

SOLID = 250      # alpha from here up is solid
HOLE = 26        # alpha below this is a hole (Minecraft's entity shaders drop texels under 0.1)


def bake(model, materials):
    tm = paint.TexelMap(model)
    Pw, Nw = paint.rest_world(model, tm)
    H, W = tm.H, tm.W
    base = np.zeros((H, W, 4))
    ice = np.zeros((H, W, 4))
    glow = np.zeros((H, W, 4))
    iceglow = np.zeros((H, W, 4))
    mats = np.array([c.mat for c in tm.cubes], dtype=object)
    for name in sorted(set(mats.tolist())):
        if name not in materials:
            raise KeyError(f"{model.identifier}: no painter for material '{name}'")
        ids = np.array([i for i, m in enumerate(mats) if m == name])
        sel = np.nonzero(np.isin(tm.cube, ids))
        tx = paint.Texels(tm, sel)
        if tx.n == 0:
            continue
        tx.Pw = Pw[sel]
        tx.Nw = Nw[sel]
        out = materials[name](tx)
        col, a, g = out["color"], out["alpha"], out["glow"]
        solid = a >= SOLID
        see = (a >= HOLE) & ~solid
        b = np.zeros((tx.n, 4))
        b[solid] = rgba(col[solid], 255.0)
        base[sel] = b
        i = np.zeros((tx.n, 4))
        i[see] = rgba(col[see], a[see])
        ice[sel] = i
        gs = np.zeros((tx.n, 4))
        gi = np.zeros((tx.n, 4))
        gs[solid] = g[solid]
        gi[see] = g[see]
        glow[sel] = gs
        iceglow[sel] = gi
    to8 = lambda x: np.clip(np.round(x), 0, 255).astype(np.uint8)
    return dict(base=to8(base), ice=to8(ice), glow=to8(glow), iceglow=to8(iceglow))


def has(tex, key):
    return bool((tex[key][..., 3] >= HOLE).any())
