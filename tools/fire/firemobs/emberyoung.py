"""
The Emberdactyl's young reborn (E2A, 22.5 blocks across its wings) on its skeleton, and its animations
(animation.emberyoung.*): the adult's moves (firemobs/emberdactyl.py), each one quicker, so its fight never lets up
(the user: stage 2 must feel like "bam bam ... no rest from the boss").
"""
import importlib.util
import os

SPEED = 1.6                   # its moves this much quicker than the adult's

_spec = importlib.util.spec_from_file_location("firemobs._emberyoung_moves", os.path.join(os.path.dirname(__file__),
                                                                                           "emberdactyl.py"))
_m = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(_m)
_m.setup(True)
_m.SPEED = SPEED
_m.AIR = 45.0                 # (its swept-back wings hang lower: it flies higher off its rest pose)
_m.WRIST_UP = 20.0
_m.WRIST_OUT = 72.0               # (its walk was clear with the wrists here)
_m.FOLD = (70.0, 230.0, 140.0)
_m.SNAP_LUNGE = 0.8
_m.GUST_CLOSED = (54.0, 8.0, 0.0, 0.0, 0.0)   # (its swept wings meet a little higher up)
_m.DIVE_SWEEP = 20.0             # (its hands came round into its head at 32)
_m.DIVE_FOLD = (8.0, 10.0, 4.0)   # (its swept wings' tips crossed behind it with the adult's)
_m.DIVE_NECK = -24.0              # (its horns into its wings at -14)
_m.FLAIL = 24.0

ID = "emberyoung"
NAME = "Young Reborn"
PREVIEW_SET = "emberdactyl"
IDLE = _m.IDLE
VIEW = _m.VIEW
DESIGN = _m.DESIGN
K = _m.K
BONES = _m.BONES
MODELS = _m.MODELS
DENSITY = _m.DENSITY
CLIP_PAIRS = _m.CLIP_PAIRS
MEMBRANES = _m.MEMBRANES
clip_group = _m.clip_group
make_rig = _m.make_rig
animations = _m.animations
extra_textures = _m.extra_textures
