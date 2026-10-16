"""
The Rat King's parts: the shared rat body (torso, head, arms, legs, tail, cape panels) and the crowns, staffs and
extras the six designs choose from.

A few parts hide until an animation needs them, so no code has to switch them on and off:
  - the eyelids, the cartoon X eyes and the tongue sit inside the head (the animations slide them out);
  - the dirt hole and mound sit under the ground (the burrow animations raise them).
`HIDDEN` records how far each one moves to show (Bedrock units, in its parent's frame); the animations use it.

Marker bones (no boxes) give the mod spots for particles: `zzz` (above his head), `ear_l_steam` / `ear_r_steam` and
their `_aim` partners (steam out of the ears; the aim bone is 3 units out along the puff), `dig` (the hole).

All positions are Bedrock units in the bind pose (see rig.py).
"""
import math

import numpy as np

from . import rig

HIDDEN = {}   # bone name -> (dx, dy, dz) that brings it out (set while building)


def add_hidden(name, offset):
    HIDDEN[name] = tuple(float(v) for v in offset)


# ---------------------------------------------------------------- the body


def torso(m, B, b, look):
    hw, hh, hd = b.hips_size
    B["hips"].add((-hw / 2.0, -3.0, -hd / 2.0), (hw, hh, hd), "fur", tag="hips")
    # Rump: a rounder back end where the tail starts.
    B["hips"].add((-hw / 2.0 + 1, -2.0, hd / 2.0 - 1), (hw - 2, hh - 3, 2), "fur", tag="hips")
    bw, bh, bd = b.belly_size
    B["belly"].add((-bw / 2.0, -0.5, b.belly_z - bd / 2.0), (bw, bh, bd), "fur", tag="belly")
    cw, ch, cd = b.chest_size
    B["chest"].add((-cw / 2.0, 0.0, -cd / 2.0), (cw, int(b.chest_h), cd), "fur", tag="chest")
    # Shoulders: a little wider at the top.
    B["chest"].add((-cw / 2.0 - 1, b.chest_h - 4, -cd / 2.0 + 1), (cw + 2, 3, cd - 2), "fur", tag="chest")
    if b.hump:
        # A hunched upper back: a rounded hump on the back of the chest, under the cape.
        uw, uh, ud = b.hump
        B["chest"].add((-uw / 2.0, b.chest_h - 1.0 - uh, cd / 2.0 - 1.0), (uw, uh, ud + 1), "fur", tag="chest")
        B["chest"].add((-uw / 2.0 + 1.0, b.chest_h - 2.0 - uh, cd / 2.0 - 1.0), (uw - 2, 1, ud), "fur", tag="chest")
    nw, nh, nd = b.neck_size
    B["neck"].add((-nw / 2.0, -1.5, -nd / 2.0), (nw, nh, nd), "fur", tag="neck")


def head(m, B, b, look):
    """Skull, cheeks, muzzle, nose, teeth, jaw (+ hidden tongue), eyes (+ hidden lids and X eyes), whiskers, ears."""
    h = B["head"]
    sw, sh, sd = b.skull
    h.add((-sw / 2.0, 0.0, -sd / 2.0), (sw, sh, sd), "fur", tag="skull")
    front = -sd / 2.0
    # The long rat snout: cheeks, muzzle, a pink nose on the tip; orange incisors under it.
    h.add((-3.5, 0.0, front - 3.0), (7, 5, 3), "fur", tag="cheek")
    h.add((-2.5, 0.0, front - 6.0), (5, 4, 3), "fur", tag="muzzle")
    h.add((-1.5, 1.0, front - 7.5), (3, 3, 2), "fur", tag="muzzle")
    h.add((-1.5, 2.5, front - 8.5), (3, 2, 1), "nose", tag="nose")
    for x in (-1.0, 0.0):
        h.add((x, -2.0, front - 7.0), (1, 2, 1), "tooth", tag="tooth")
    jaw = m.bone("jaw", (h.pivot[0], h.pivot[1], h.pivot[2] + front), parent=h)
    jaw.add((-2.0, -1.5, -6.0), (4, 2, 6), "fur", tag="jaw")
    B["jaw"] = jaw
    # Tongue: tucked inside the muzzle; slides out of the side of his mouth when he dies.
    tongue = m.bone("tongue", (jaw.pivot[0], jaw.pivot[1] + 0.5, jaw.pivot[2] - 1.0), parent=jaw)
    tongue.add((-1.0, 0.0, -3.0), (2, 1, 5), "tongue", tag="tongue")
    tongue.add((-1.5, -0.5, -4.5), (3, 1, 2), "tongue", tag="tongue")
    B["tongue"] = tongue
    add_hidden("tongue", (2.6, -2.0, -5.4))
    ew = look.get("eye_size", 2)
    eye_y = sh - 1.5 - ew
    for side, sx in rig.SIDES:
        if side in look.get("no_eye", ()):
            continue
        # A bead on the front corner of the skull: seen from the front and the side.
        ex0 = sw / 2.0 - ew + 0.5 if sx > 0 else -sw / 2.0 - 0.5
        h.add((ex0, eye_y, front - 0.5), (ew, ew, ew), "eye", tag=f"eye_{side}")
    # Eyelids and X eyes (one per side), hidden in the skull.
    for side, sx in rig.SIDES:
        cx = sx * (sw / 2.0 - ew / 2.0 + 0.5)
        cy = eye_y + ew / 2.0
        lid = m.bone(f"lid_{side}", (h.pivot[0] + cx, h.pivot[1] + cy, h.pivot[2] + front), parent=h)
        n = ew + 1
        hide = np.array([-sx * 3.0, 0.0, 2.4])
        lid.add((-n / 2.0 + hide[0], -n / 2.0, -0.75 + hide[2]), (n, n, n), "lid", tag="lid")
        add_hidden(f"lid_{side}", -hide)
        B[f"lid_{side}"] = lid
        xe = m.bone(f"xeye_{side}", (h.pivot[0] + cx, h.pivot[1] + cy, h.pivot[2] + front), parent=h)
        hide_x = np.array([-sx * 4.2, -0.5, 4.6])
        for ang in (45.0, -45.0):
            # A cross over the front of the eye only (the user's note: the X's go on the front of his face).
            xe.add_c((hide_x[0], hide_x[1], -1.3 + hide_x[2]), (1, 4, 1), "xmark", rotation=(0.0, 0.0, ang))
        add_hidden(f"xeye_{side}", -hide_x)
        B[f"xeye_{side}"] = xe
    # Whiskers: thin bars fanned out of the muzzle's sides.
    wl = look.get("whisker_len", 6)
    for side, sx in rig.SIDES:
        for k, (ay, az) in enumerate(((-16.0, 14.0), (4.0, 0.0), (22.0, -14.0))):
            base = (sx * 2.5, 2.0, front - 6.5 + 0.7 * k)
            org = (base[0] if sx > 0 else base[0] - wl, base[1] - 0.5, base[2] - 0.5)
            h.add(org, (wl, 1, 1), look.get("whisker_mat", "whisker"),
                  rotation=(0.0, sx * ay, sx * az), pivot=tuple(h.pivot[i] + base[i] for i in range(3)), tag="whisker")
    # Ears: big round discs on the top corners of the skull, leaning out.
    ew_, eh_ = b.ear
    for side, sx in rig.SIDES:
        piv = (h.pivot[0] + sx * (sw / 2.0 - 0.25), h.pivot[1] + sh - 2.0, h.pivot[2] + 0.5)
        tilt = look.get(f"ear_{side}_rot", (-6.0, -sx * 18.0, sx * b.ear_tilt))
        ear = m.bone(f"ear_{side}", piv, tilt, parent=h)
        x0 = -0.5 if sx > 0 else -(ew_ - 0.5)
        ear.add((x0, 0.0, -0.5), (ew_, eh_, 1), "ear", tag=f"ear_{side}")
        B[f"ear_{side}"] = ear
        # Steam comes out of the ear's base and puffs out and up along it.
        st = m.bone(f"ear_{side}_steam", (piv[0] + sx * 1.0, piv[1] + 1.0, piv[2] - 1.0), parent=ear)
        m.bone(f"ear_{side}_steam_aim", (piv[0] + sx * 2.6, piv[1] + 3.6, piv[2] - 1.6), parent=st)
    # Zzz rise from just above his nose.
    m.bone("zzz", (h.pivot[0], h.pivot[1] + sh + 2.0, h.pivot[2] + front - 4.0), parent=h)


def arms(m, B, b, look):
    aw = b.arm_w
    for side, sx in rig.SIDES:
        B[f"arm_{side}"].add((-aw / 2.0, -b.upper_len, -aw / 2.0), (aw, int(b.upper_len), aw), "fur", tag="arm")
        B[f"arm_{side}"].add((-(aw + 1) / 2.0, -3.0, -(aw + 1) / 2.0), (aw + 1, 4, aw + 1), "fur", tag="shoulder")
        fw = b.fore_w
        B[f"forearm_{side}"].add((-fw / 2.0, -b.fore_len, -fw / 2.0), (fw, int(b.fore_len), fw), "fur", tag="forearm")
        hand = B[f"hand_{side}"]
        if side == "r" or look.get("fist_l"):
            # A fist around the staff.
            hand.add((-1.5, -3.5, -1.5), (3, 4, 3), "skin", tag="hand")
            hand.add((-1.5 - sx * 0.5, -3.0, -2.5), (3, 3, 1), "skin", tag="fingers")
        else:
            hand.add((-1.5, -3.0, -1.0), (3, 3, 2), "skin", tag="hand")
            # The fingers hang from the knuckles on their own bone, so an animation can curl them into a fist.
            hp = hand.pivot
            fingers = m.bone(f"fingers_{side}", (hp[0], hp[1] - 3.0, hp[2] - 0.3), parent=hand)
            for k, x in enumerate((-1.2, 0.0, 1.2)):
                fingers.add((x - 0.5, -2.0 + (0.5 if k == 1 else 0.0), -0.5), (1, 2, 1), "skin", tag="finger")
            B[f"fingers_{side}"] = fingers
            hand.add((sx * 1.5 - 0.5, -3.0, -1.5), (1, 2, 1), "skin", tag="thumb")
        # Claw tips are painted on the fingers.


def legs(m, B, b, look):
    for side, sx in rig.SIDES:
        tw, td, sw_ = b.thigh_w, b.thigh_d, b.shin_w
        B[f"thigh_{side}"].add((-tw / 2.0, -b.thigh_len - 1.0, -td / 2.0), (tw, int(b.thigh_len) + 3, td), "fur",
                               tag="thigh")
        B[f"shin_{side}"].add((-sw_ / 2.0, -b.shin_len, -sw_ / 2.0), (sw_, int(b.shin_len), sw_), "fur", tag="shin")
        foot = B[f"foot_{side}"]
        fl, heel = b.foot_len, b.heel
        foot.add((-1.5, -1.0, -1.5), (3, 2, 3), "skin", tag="ankle")
        foot.add((-2.0, -b.ankle_h, -(fl - heel)), (4, 2, fl), "skin", tag="foot")
        for x in (-1.5, 0.0, 1.5):
            foot.add((x - 0.5, -b.ankle_h, -(fl - heel) - 1.5), (1, 1, 2), "claw", tag="toe")


def tail(m, B, b, look):
    for i, (length, t) in enumerate(b.tail):
        seg = B[f"tail_{i}"]
        last = i == len(b.tail) - 1
        seg.add((-t / 2.0, -length - (0.0 if last else 0.5), -t / 2.0), (t, length + (0 if last else 1), t),
                look.get("tail_mat", "tail"), tag="tail", inflate=0.06 * (i % 2))


def cape_panels(m, B, b, mat="cape", widths=None):
    for i, length in enumerate(b.cape):
        w = (widths[i] if widths else b.cape_w)
        last = i == len(b.cape) - 1
        B[f"cape_{i}"].add((-w / 2.0, -length - (0.0 if last else 0.5), -0.5),
                           (w, length + (0 if last else 1), 1), mat, tag=f"cape_{i}", inflate=0.05 * (i % 2))


def mantle(m, B, b, mat="cape", height=2, extra=1):
    """Cloth lying over the shoulders (the cape's top), tied at the throat."""
    cw, _ch, cd = b.chest_size
    B["chest"].add((-cw / 2.0 - extra - 0.5, b.chest_h - 1.0, -cd / 2.0 - 0.5), (cw + 2 * extra + 1, height, cd + 1), mat,
                   tag="mantle")


HOLE_FORWARD = 14.0     # units in front of his feet: the hole he digs, dives head first into and bursts out of
HOLE_MIN_RADIUS = 9.0   # units: wide enough for his head and snout to come up through, on the skinniest build


def hole_radius(b):
    """Half the hole's width (units): a little wider than his belly, so he fits down it (the user's note on round 1:
    the dirt mound shows where his head goes in)."""
    return max(HOLE_MIN_RADIUS, b.belly_size[0] / 2.0 + 2.0)


HOLE_HIDE_GAP = 1.0     # units the tallest clod's top stays under the ground while the hole is hidden


def hole(m, B, b, forward=HOLE_FORWARD):
    """The dirt hole he dives into: a dark hole with a ring of dirt clods round it. Hidden under the ground, deep
    enough that even the tallest clod's top stays HOLE_HIDE_GAP under it (the mod also hides the bone unless he's
    burrowing or popping up)."""
    h = B["hole"]
    z = -forward
    r = hole_radius(b)
    k = r / 6.0
    w1, d1 = int(round(2 * r)), int(round(1.34 * r))
    clods = [(-7.0, -1.0, 2), (-5.0, -6.0, 2), (0.0, -7.5, 3), (5.0, -6.0, 2), (7.0, -1.0, 2), (6.0, 4.0, 2),
             (0.0, 6.0, 2), (-6.0, 4.0, 2), (3.0, 7.0, 1), (-3.5, -7.0, 1)]
    sizes = [max(1, int(round(s * min(k, 1.3)))) for _x, _dz, s in clods]
    depth = float(max(sizes)) + HOLE_HIDE_GAP
    h.add((-w1 / 2.0, -depth, z - d1 / 2.0), (w1, 1, d1), "hole", tag="hole")
    h.add((-d1 / 2.0, -depth, z - w1 / 2.0), (d1, 1, w1), "hole", tag="hole")
    for (x, dz, _s), s in zip(clods, sizes):
        h.add((x * k - s / 2.0, -depth, z + dz * k - s / 2.0), (s, s, s), "dirt", tag="clod")
    add_hidden("hole", (0.0, depth, 0.0))
    m.bone("dig", (0.0, 0.5, z), parent=h)
    m.hole_radius = r + 4.0     # the clods' ring, and a little more (checks: things may go underground in it)


# ---------------------------------------------------------------- crowns (on the `crown` bone, pivot at the skull top)


def crown_bone(m, B, b, rotation=(0.0, 0.0, 0.0), shift=(0.0, 0.0, 0.0)):
    h = B["head"]
    piv = (h.pivot[0] + shift[0], h.pivot[1] + b.skull[1] + shift[1], h.pivot[2] + shift[2])
    c = m.bone("crown", piv, rotation, parent=h)
    B["crown"] = c
    return c


def ring(bone, w, h, d, mat, y=0.0, thick=1, tag="band"):
    """A square band, w x d outside, h tall, centred on the bone's pivot."""
    t = thick
    bone.add((-w / 2.0, y, -d / 2.0), (w, h, t), mat, tag=tag)
    bone.add((-w / 2.0, y, d / 2.0 - t), (w, h, t), mat, tag=tag)
    bone.add((-w / 2.0, y, -d / 2.0 + t), (t, h, d - 2 * t), mat, tag=tag)
    bone.add((w / 2.0 - t, y, -d / 2.0 + t), (t, h, d - 2 * t), mat, tag=tag)


def crown_three(m, B, b):
    """No. 1: a small three-point gold crown with a green gem, cocked to one side."""
    c = crown_bone(m, B, b, rotation=(-6.0, 0.0, 16.0), shift=(1.2, -0.6, 0.5))
    ring(c, 7, 2, 7, "gold")
    for x, z, hgt in ((-3.0, -3.0, 2), (3.0, -3.0, 2), (0.0, -3.0, 3), (-3.0, 3.0, 2), (3.0, 3.0, 2)):
        c.add((x - 0.5, 2.0, z - 0.5), (1, hgt, 1), "gold", tag="point")
    c.add((-1.0, 2.0, -3.5), (2, 1, 1), "gold", tag="point")
    c.add((-0.5, 0.5, -4.0), (1, 1, 1), "gem", tag="gem")


def crown_royal(m, B, b):
    """No. 2: a tall royal crown: eight points with pearls, a red velvet cap and a green orb on top."""
    c = crown_bone(m, B, b, rotation=(-4.0, 0.0, 0.0), shift=(0.0, -0.3, 0.3))
    ring(c, 8, 2, 8, "gold")
    for x in (-3.5, 0.0, 3.5):
        for z in (-3.5, 3.5):
            c.add((x - 0.5, 2.0, z - 0.5), (1, 3, 1), "gold", tag="point")
            c.add((x - 0.5, 5.0, z - 0.5), (1, 1, 1), "pearl", tag="pearl")
    for x in (-3.5, 3.5):
        c.add((x - 0.5, 2.0, -0.5), (1, 3, 1), "gold", tag="point")
        c.add((x - 0.5, 5.0, -0.5), (1, 1, 1), "pearl", tag="pearl")
    c.add((-3.0, 1.0, -3.0), (6, 4, 6), "velvet", tag="velvet")
    c.add((-1.0, 5.0, -1.0), (2, 2, 2), "gem", tag="orb")
    for x, z in ((-2.0, -4.5), (2.0, -4.5), (-4.5, 0.0), (4.5, 0.0)):
        c.add((x - 0.5, 0.5, z), (1, 1, 1), "ruby", tag="jewel")


def crown_cutlery(m, B, b):
    """No. 3: bent forks and spoons stuck in a tin band."""
    c = crown_bone(m, B, b, rotation=(-5.0, 0.0, -6.0), shift=(-0.4, -0.5, 0.4))
    ring(c, 7, 2, 7, "silver")
    # (x, z, height, lean z, lean x, kind)
    pieces = ((0.0, -3.0, 5, 0.0, -8.0, "fork"), (-2.6, -3.0, 4, 18.0, -6.0, "spoon"), (2.6, -3.0, 4, -16.0, -6.0, "fork"),
              (-3.0, 1.0, 3, 24.0, 4.0, "spoon"), (3.0, 1.5, 3, -24.0, 6.0, "fork"), (0.0, 3.0, 3, 0.0, 14.0, "spoon"))
    for i, (x, z, hgt, lz, lx, kind) in enumerate(pieces):
        piv = (x, 1.5, z)
        rot = (lx, 0.0, lz)
        c.add((x - 0.5, 1.0, z - 0.5), (1, hgt, 1), "silver", rotation=rot, pivot=_abs(c, piv), tag="handle")
        top = 1.0 + hgt
        if kind == "fork":
            c.add((x - 1.5, top, z - 0.5), (3, 1, 1), "silver", rotation=rot, pivot=_abs(c, piv), tag="fork")
            for dx in (-1.0, 1.0):
                c.add((x + dx - 0.5, top + 1.0, z - 0.5), (1, 2, 1), "silver", rotation=rot, pivot=_abs(c, piv), tag="tine")
        else:
            c.add((x - 1.0, top, z - 0.5), (2, 3, 1), "spoon", rotation=rot, pivot=_abs(c, piv), tag="spoon")


def crown_candles(m, B, b):
    """No. 4: a bone band with a ring of dripping candles burning with green plague flames."""
    c = crown_bone(m, B, b, rotation=(-3.0, 0.0, 0.0), shift=(0.0, -0.4, 0.2))
    ring(c, 7, 2, 7, "bone")
    spots = ((0.0, -3.0, 5, 2), (-3.0, -3.0, 3, 1), (3.0, -3.0, 4, 1), (-3.0, 3.0, 4, 1), (3.0, 3.0, 3, 1),
             (-3.0, 0.0, 2, 1), (3.0, 0.0, 2, 1), (0.0, 3.0, 2, 1))
    for i, (x, z, hgt, w) in enumerate(spots):
        c.add((x - w / 2.0, 2.0, z - w / 2.0), (w, hgt, w), "wax", tag="candle")
        fl = m.bone(f"flame_{i}", _abs(c, (x, 2.0 + hgt, z)), parent=c)
        fl.add((-0.5, 0.0, -0.5), (1, 2 if w > 1 else 1, 1), "flame", tag="flame")
    for x in (-1.0, 1.0):
        c.add((x - 0.5, 0.5, -4.0), (1, 1, 1), "tooth", tag="tooth")


def crown_heavy(m, B, b):
    """No. 5: an oversized heavy crown with a broken point, slipping down over his right ear."""
    c = crown_bone(m, B, b, rotation=(-4.0, 6.0, -22.0), shift=(-1.6, -1.6, 0.4))
    ring(c, 10, 3, 10, "gold", thick=1)
    heights = {(-4.5, -4.5): 3, (0.0, -4.5): 4, (4.5, -4.5): 3, (-4.5, 4.5): 3, (0.0, 4.5): 1, (4.5, 4.5): 3,
               (-4.5, 0.0): 3, (4.5, 0.0): 3}
    for (x, z), hgt in heights.items():
        c.add((x - 0.5, 3.0, z - 0.5), (1, hgt, 1), "gold", tag="point")
        if hgt >= 3:
            c.add((x - 0.5, 3.0 + hgt, z - 0.5), (1, 1, 1), "gold_dark", tag="tip")
    c.add((-1.0, 0.5, -5.5), (2, 2, 1), "ruby", tag="jewel")
    c.add((3.0, 1.0, -5.5), (1, 1, 1), "gem", tag="jewel")
    c.add((-4.0, 1.0, -5.5), (1, 1, 1), "gem", tag="jewel")


def crown_tincan(m, B, b):
    """No. 6: a tin can cut into jagged points, dented, tipped back."""
    c = crown_bone(m, B, b, rotation=(-14.0, 0.0, 5.0), shift=(0.2, -0.6, 0.8))
    ring(c, 7, 4, 7, "tin")
    # Jagged rim: points of different heights all round.
    k = 0
    for x in (-3.0, -1.0, 1.0, 3.0):
        for z in (-3.0, 3.0):
            hgt = (2, 1, 2, 1, 1, 2, 1, 2)[k]
            c.add((x - 0.5, 4.0, z - 0.5), (1, hgt, 1), "tin", tag="point")
            k += 1
    for z in (-1.0, 1.0):
        for x in (-3.0, 3.0):
            c.add((x - 0.5, 4.0, z - 0.5), (1, 1 + (1 if z > 0 else 0), 1), "tin", tag="point")
    c.add((-2.0, 3.0, -2.0), (4, 1, 4), "tin_dark", tag="lid")


def _abs(bone, rel):
    return tuple(bone.pivot[i] + rel[i] for i in range(3))


# ---------------------------------------------------------------- staffs (on the `staff` bone, shaft along +y)


def staff_bone(m, B, b, below=0.0):
    """The staff's bone (in the right fist). Made before the rest pose; the staff's boxes are added after it."""
    if "staff" in B:
        s = B["staff"]
    else:
        hand = B["hand_r"]
        grip = (hand.pivot[0], hand.pivot[1] - 2.0, hand.pivot[2])
        s = m.bone("staff", grip, parent=hand)
        B["staff"] = s
    s.below = int(round(below))      # how far the shaft runs below the grip (its foot)
    return s


def staff_reach(m, b, clearance=1.0):
    """How far the shaft must run below the grip for its foot to stop `clearance` above the ground (rest pose)."""
    mats = m.pose_matrices()
    s = m["staff"]
    grip = m.point(mats, "staff", s.pivot)
    up = rig.normalize(m.direction(mats, "staff", (0.0, 1.0, 0.0)))
    if up[1] < 0.2:
        return 24
    return max(6, int(round((grip[1] - clearance) / up[1])))


def staff_foot(s):
    return (s.pivot[0], s.pivot[1] - s.below, s.pivot[2])


def staff_claw_orb(m, B, b, below=26, above=22, claw_mat="bone"):
    """No. 1: a twisted dark-wood staff; a claw on top clutches a glowing green orb."""
    s = staff_bone(m, B, b, below)
    y = -below
    segs = [(0.0, 0.0, 9), (0.5, 0.0, 9), (0.0, 0.5, 10), (-0.5, 0.0, 10), (0.0, -0.5, below + above - 38)]
    for dx, dz, ln in segs:
        s.add((-1.0 + dx, y, -1.0 + dz), (2, ln, 2), "wood", tag="shaft")
        y += ln
    s.add((-1.5, y - 2.0, -1.5), (3, 2, 3), "wood", tag="knob")
    top = y
    for k, (cx, cz) in enumerate(((1.6, 1.6), (-1.6, 1.6), (1.6, -1.6), (-1.6, -1.6))):
        rx = 18.0 if cz > 0 else -18.0
        rz = -18.0 if cx > 0 else 18.0
        s.add((cx - 0.5, top - 0.5, cz - 0.5), (1, 5, 1), claw_mat, rotation=(rx, 0.0, rz),
              pivot=_abs(s, (cx, top, cz)), tag="claw")
    s.add((-1.5, top + 1.0, -1.5), (3, 3, 3), "orb", tag="orb")


def staff_scepter(m, B, b, below=14, above=26):
    """No. 2: a gold scepter; a crown-shaped setting on top holds a big green gem."""
    s = staff_bone(m, B, b, below)
    s.add((-1.0, -below, -1.0), (2, below + above, 2), "gold", tag="shaft")
    for y in (-below, -below + 6, 6, above - 6):
        s.add((-1.5, y, -1.5), (3, 1, 3), "gold_dark", tag="band")
    top = above
    s.add((-2.0, top, -2.0), (4, 1, 4), "gold", tag="setting")
    for x, z in ((-2.0, -2.0), (1.0, -2.0), (-2.0, 1.0), (1.0, 1.0)):
        s.add((x, top + 1.0, z), (1, 3, 1), "gold", tag="prong")
    s.add((-1.5, top + 1.0, -1.5), (3, 4, 3), "gem", tag="gem")
    s.add((-0.5, top + 5.0, -0.5), (1, 1, 1), "gold", tag="tip")


def staff_pipe(m, B, b, below=22, above=26):
    """No. 3: the Pied Piper's long wooden pipe, a brass mouthpiece on top, a flared bell below, ribbons."""
    s = staff_bone(m, B, b, below)
    s.add((-1.0, -below + 3, -1.0), (2, below + above - 3, 2), "pipe", tag="shaft")
    s.add((-1.5, -below + 1, -1.5), (3, 2, 3), "brass", tag="bell")
    s.add((-2.0, -below, -2.0), (4, 1, 4), "brass", tag="bell")
    s.add((-1.5, above, -1.5), (3, 1, 3), "brass", tag="ring")
    s.add((-1.0, above + 1.0, -1.0), (2, 3, 2), "brass", tag="mouthpiece")
    s.add((-0.5, above + 4.0, -0.5), (1, 1, 1), "brass", tag="mouthpiece")
    for i, (x, z, ln, mat) in enumerate(((1.0, 0.0, 9, "ribbon_red"), (-1.0, 0.5, 7, "ribbon_gold"))):
        r = m.bone(f"ribbon_{i}", _abs(s, (x, above - 2.0, z)), parent=s)
        r.add((-0.5 if x > 0 else -1.5, -ln, -0.5), (2, ln, 1), mat, tag="ribbon")
        B[f"ribbon_{i}"] = r


def staff_censer(m, B, b, below=26, above=30):
    """No. 4: stacked bones bound with cord; an iron censer glowing green swings from a hook on top."""
    s = staff_bone(m, B, b, below)
    y = -below
    while y < above - 1:
        ln = min(6, above - y)
        s.add((-1.0, y, -1.0), (2, int(ln), 2), "bone", tag="shaft")
        if y + ln < above - 1:
            s.add((-1.5, y + ln - 1.0, -1.5), (3, 2, 3), "bone_knob", tag="knuckle")
        y += ln
    top = above
    s.add((-1.5, top - 1.0, -1.5), (3, 3, 3), "bone_knob", tag="knob")
    s.add((-0.5, top + 2.0, -0.5), (1, 3, 1), "iron", tag="hook")
    s.add((-0.5, top + 4.0, -4.5), (1, 1, 5), "iron", tag="hook")
    s.add((-0.5, top + 2.0, -4.5), (1, 2, 1), "iron", tag="hook")
    hook = _abs(s, (0.0, top + 2.0, -4.0))
    chain = m.bone("censer_chain", hook, parent=s)
    chain.add((-0.5, -5.0, -0.5), (1, 5, 1), "chain", tag="chain")
    cen = m.bone("censer", (hook[0], hook[1] - 5.0, hook[2]), parent=chain)
    cen.add((-2.0, -5.0, -2.0), (4, 4, 4), "censer", tag="censer")
    cen.add((-1.5, -1.0, -1.5), (3, 1, 3), "iron", tag="censer_lid")
    cen.add((-0.5, 0.0, -0.5), (1, 1, 1), "iron", tag="censer_lid")
    cen.add((-2.5, -4.0, -0.5), (1, 2, 1), "censer_glow", tag="censer_glow")
    B["censer_chain"], B["censer"] = chain, cen


def staff_knot(m, B, b, below=33, above=5):
    """No. 5: a crooked cane topped with a 'rat king': a knot of tied rat tails."""
    s = staff_bone(m, B, b, below)
    y = -below
    for dx, dz, ln in ((0.0, 0.0, 10), (0.5, 0.5, 11), (0.0, 1.0, below + above - 21)):
        s.add((-1.0 + dx, y, -1.0 + dz), (2, ln, 2), "cane", tag="shaft")
        y += ln
    top = y
    s.add((-2.5, top - 1.0, -1.5), (5, 4, 4), "knot", tag="knot")
    s.add((-1.5, top - 0.5, -2.5), (3, 4, 5), "knot", tag="knot")
    tails = ((2.5, 1.0, 0.0, -30.0, 0.0, 5), (-2.5, 1.5, 0.5, 35.0, 10.0, 4), (0.5, 2.0, -2.5, 0.0, -35.0, 5),
             (-0.5, 0.5, 2.5, 0.0, 40.0, 4), (1.5, -0.5, -2.0, -25.0, -20.0, 6), (-1.5, -0.5, 1.5, 30.0, 25.0, 6))
    for x, yy, z, rz, rx, ln in tails:
        s.add((x - 0.5, top + yy - ln, z - 0.5), (1, ln, 1), "tail", rotation=(rx, 0.0, rz),
              pivot=_abs(s, (x, top + yy, z)), tag="knot_tail")


def staff_fork(m, B, b, below=30, above=22):
    """No. 6: a huge rusty three-tined fork."""
    s = staff_bone(m, B, b, below)
    s.add((-1.0, -below, -1.0), (2, below + above, 2), "rust", tag="shaft")
    top = above
    s.add((-1.5, top, -1.5), (3, 2, 3), "rust", tag="neck")
    s.add((-3.5, top + 2.0, -1.0), (7, 2, 2), "rust", tag="head")
    for x in (-3.0, 0.0, 3.0):
        s.add((x - 0.5, top + 4.0, -0.5), (1, 7, 1), "rust", tag="tine")
        s.add((x - 0.5, top + 11.0, -0.5), (1, 1, 1), "rust_tip", tag="tip")


# ---------------------------------------------------------------- extras


def ermine_collar(m, B, b):
    """No. 2: a tall stiff collar standing up behind his head."""
    cw, _ch, cd = b.chest_size
    top = b.chest_h
    ch = B["chest"]
    ch.add((-7.0, top - 1.0, cd / 2.0 - 1.0), (14, 7, 1), "ermine", rotation=(-14.0, 0.0, 0.0),
           pivot=_abs(ch, (0.0, top - 1.0, cd / 2.0 - 0.5)), tag="collar")
    for sx in (1, -1):
        ch.add((sx * 6.5 - 0.5, top - 1.0, -1.0), (1, 6, 5), "ermine", rotation=(0.0, 0.0, sx * 16.0),
               pivot=_abs(ch, (sx * 6.5, top - 1.0, 1.5)), tag="collar")


def monocle(m, B, b, side="r"):
    """No. 2: a gold monocle over one eye, with a short chain."""
    h = B["head"]
    sx = 1 if side == "l" else -1
    sw, sh, sd = b.skull
    cx = sx * (sw / 2.0 - 1.0)
    cy = sh - 3.0
    z = -sd / 2.0 - 1.5
    for (x0, y0, w, hh) in ((-1.5, 1.5, 3, 1), (-1.5, -2.5, 3, 1), (-2.5, -1.5, 1, 3), (1.5, -1.5, 1, 3)):
        h.add((cx + x0, cy + y0, z), (w, hh, 1), "gold", tag="monocle")
    h.add((cx + sx * 1.5 - 0.5, cy - 6.5, z + 0.5), (1, 4, 1), "chain_gold", tag="monocle_chain")


def teeth_necklace(m, B, b):
    """No. 4: a necklace of teeth on a cord round his neck."""
    ch = B["chest"]
    cw, _h, cd = b.chest_size
    top = b.chest_h
    for k, x in enumerate((-3.5, -2.0, -0.5, 1.0, 2.5)):
        dy = -abs(x + 0.5) * 0.35
        ch.add((x, top - 3.0 + dy, -cd / 2.0 - 1.0), (1, 2, 1), "claw", tag="necklace")


def fur_collar(m, B, b):
    """No. 5: a thick ragged fur collar on the cape."""
    cw, _h, cd = b.chest_size
    top = b.chest_h
    B["chest"].add((-cw / 2.0 - 2.0, top - 1.5, -cd / 2.0 - 1.0), (cw + 4, 4, cd + 2), "fur_collar", tag="fur_collar")


def brows_beard(m, B, b):
    """No. 5: bushy white brows and a wispy beard of long chin hairs."""
    h = B["head"]
    sw, sh, sd = b.skull
    for sx in (1, -1):
        h.add((sx * (sw / 2.0 - 1.5) - 1.5, sh - 1.0, -sd / 2.0 - 1.0), (3, 1, 2), "white_hair",
              rotation=(0.0, 0.0, -sx * 14.0), pivot=_abs(h, (sx * (sw / 2.0 - 1.5), sh - 0.5, -sd / 2.0)), tag="brow")
    jaw = B["jaw"]
    for k, (x, ln, rz) in enumerate(((-1.0, 5, 6.0), (0.0, 7, 0.0), (1.0, 5, -6.0))):
        jaw.add((x - 0.5, -1.5 - ln, -4.0), (1, ln, 1), "white_hair", rotation=(-8.0, 0.0, rz),
                pivot=_abs(jaw, (x, -1.5, -3.5)), tag="beard")


def eyepatch(m, B, b, side="l"):
    """No. 6: an eyepatch over one eye, with its strap round the head."""
    h = B["head"]
    sx = 1 if side == "l" else -1
    sw, sh, sd = b.skull
    ew = 2
    eye_y = sh - 1.0 - ew
    ex = sx * (sw / 2.0 - ew / 2.0 + 0.5)
    h.add((ex - 1.5, eye_y - 0.5, -sd / 2.0 - 1.0), (3, 3, 1), "patch", tag="patch")
    # Strap: round the skull just above the eyes, and a short piece up from the patch to it.
    band = eye_y + ew + 0.5
    h.add((-sw / 2.0 - 0.5, band, -sd / 2.0 - 0.5), (sw + 1, 1, sd + 1), "strap", tag="strap")
    h.add((ex - 0.5, eye_y + 2.5, -sd / 2.0 - 0.75), (1, 1, 1), "strap", tag="strap")


def earring(m, B, b, side="r"):
    """No. 6: a gold hoop through one ear."""
    ear = B[f"ear_{side}"]
    sx = 1 if side == "l" else -1
    x = sx * 3.0
    ear.add((x - 1.0, -2.0, -0.5), (2, 1, 1), "gold", tag="earring")
    ear.add((x - 1.5, -1.5, -0.5), (1, 2, 1), "gold", tag="earring")
    ear.add((x + 0.5, -1.5, -0.5), (1, 2, 1), "gold", tag="earring")


def kid_cape_knot(m, B, b):
    """No. 6: the cape's corners knotted under his chin."""
    ch = B["chest"]
    cw, _h, cd = b.chest_size
    top = b.chest_h
    ch.add((-1.5, top - 3.0, -cd / 2.0 - 2.0), (3, 3, 2), "cape", tag="knot")
    for sx in (1, -1):
        ch.add((sx * 1.0 - 0.5, top - 7.0, -cd / 2.0 - 1.5), (1, 4, 1), "cape", rotation=(0.0, 0.0, sx * 12.0),
               pivot=_abs(ch, (sx * 1.0, top - 3.0, -cd / 2.0 - 1.0)), tag="knot_end")


def hood(m, B, b):
    """No. 4: a hood thrown back, lying on his upper back."""
    ch = B["chest"]
    cw, _h, cd = b.chest_size
    top = b.chest_h
    ch.add((-4.5, top - 4.0, cd / 2.0), (9, 6, 4), "cape", rotation=(-16.0, 0.0, 0.0),
           pivot=_abs(ch, (0.0, top - 1.0, cd / 2.0)), tag="hood")


def tassels(m, B, b, segment, n=5, mat="gold"):
    """No. 3: gold tassels along the bottom hem of a cape panel."""
    seg = B[f"cape_{segment}"]
    length = b.cape[segment]
    w = b.cape_w
    for k in range(n):
        x = -w / 2.0 + 1.0 + k * (w - 2.0) / (n - 1)
        if abs(x) < 3.0:
            continue      # the split in the middle
        seg.add((x - 0.5, -length - 2.0, -0.5), (1, 2, 1), mat, tag="tassel")


def shaggy_tufts(m, B, b):
    """No. 5: shaggy tufts of old fur at the shoulders and elbows."""
    for side, sx in rig.SIDES:
        B[f"arm_{side}"].add((sx * 1.0 - 1.0, -5.0, 0.5), (2, 3, 2), "fur", tag="tuft")
        B[f"forearm_{side}"].add((-1.0, -1.0, 1.0), (2, 2, 2), "fur", tag="tuft")
