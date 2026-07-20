#!/usr/bin/env python3
"""
Data-driven generator for the LethalBreed horror ZOMBIE MODELS (not "variants" — the mod already has
ZombieVariation for random stats; these are grotesque *render models* that a plain minecraft:zombie can wear
in place of the vanilla model, chosen per-instance via a synced attachment).

ONE model = one spec JSON in tools/models/<id>.json. From each spec's cube-list this emits, sharing the exact
packed UVs so geometry and texture always line up:
  - GeckoLib geometry            assets/lethalbreed/geckolib/models/entity/<id>.geo.json
  - a procedural gore texture    assets/lethalbreed/textures/entity/<id>.png
  - a spawn-egg icon             assets/lethalbreed/textures/item/<id>_spawn_egg.png  (+ the 2 item-model JSONs)
And ONE shared animation file for the whole roster (GeckoLib's replaced-entity renderer loads a single
.animation.json for all instances; clips are namespaced "<id>_idle/walk/attack/spasm/death"):
  - assets/lethalbreed/geckolib/animations/entity/horror.animation.json

Design rules enforced by validation (see check_spec):
  * CANONICAL SKELETON — every model uses the same animated bones so one gait library drives them all:
        root, body, head, jaw, arm_r, arm_l, leg_r, leg_l
    Extra decorative child bones (jaw_hang, arm_stub, entrail_a, ...) are allowed and are animated only via
    the spec's optional "anim_overrides".
  * ON THE GROUND — the lowest cube of every model sits at y >= 0 (no floating, no sinking).
  * VISIBLE GORE — each model must carry a minimum of exposed-gore cubes (bone/flesh/entrails/ember), because
    "looks like a plain zombie" is the failure mode.

Build aid only (needs Pillow, numpy). Run:  python3 tools/gen_horror_models.py
"""
import json
import math
import os
import glob

import numpy as np
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
MODELS_DIR = os.path.join(HERE, "models")
ASSETS = os.path.join(HERE, "..", "src", "main", "resources", "assets", "lethalbreed")
ATLAS_W = 128

# Canonical animated skeleton — a gait clip may address any of these and it will resolve on every model.
CANONICAL = ["root", "body", "head", "jaw", "arm_r", "arm_l", "leg_r", "leg_l"]
# Materials that count as "visible gore" for the minimum-gore check.
GORE_MATS = {"FLESH", "FLESHDK", "BONE", "ENTRAIL", "EMBER", "PUS", "GROWTH"}
MIN_GORE_CUBES = 3

# (base rgb, secondary rgb) per material — the painter mottles between them with per-pixel noise + edge AO.
MAT = {
    "SKIN":    ((90, 107, 72),  (60, 74, 44)),
    "SKIN2":   ((78, 92, 60),   (52, 44, 52)),
    "GREY":    ((120, 122, 116),(80, 82, 78)),
    "BLOAT":   ((126, 140, 86), (74, 98, 60)),
    "BLOATDK": ((58, 66, 40),   (40, 30, 38)),
    "FLESH":   ((168, 59, 49),  (122, 43, 36)),
    "FLESHDK": ((110, 34, 30),  (70, 22, 20)),
    "BONE":    ((216, 203, 175),(185, 168, 134)),
    "TEETH":   ((201, 178, 122),(224, 214, 186)),
    "EYE":     ((196, 60, 58),  (230, 150, 138)),
    "SOCKET":  ((24, 17, 13),   (8, 6, 5)),
    "GROWTH":  ((138, 122, 94), (92, 82, 60)),
    "ENTRAIL": ((120, 40, 30),  (176, 66, 54)),
    "PUS":     ((196, 188, 120),(150, 140, 80)),
    "CHAR":    ((44, 40, 38),   (24, 22, 20)),
    "EMBER":   ((196, 78, 26),  (140, 44, 12)),
    "PALE":    ((150, 150, 122),(110, 112, 90)),
    # vanilla-look zombie palette (for the model-0 stand-in that most zombies wear)
    "ZSKIN":   ((79, 111, 43),  (55, 78, 30)),
    "ZSHIRT":  ((52, 90, 108),  (40, 70, 86)),
    "ZPANTS":  ((72, 76, 128),  (52, 56, 100)),
}

# ---------------------------------------------------------------------------------------------------------
# UV PACKING + PAINTING (unchanged core — geometry and texture share these packed rects)
# ---------------------------------------------------------------------------------------------------------

def unwrap_size(size):
    sx, sy, sz = size
    return max(1, int(math.ceil(2 * sz + 2 * sx))), max(1, int(math.ceil(sz + sy)))


def pack_cubes(bones):
    flat = []
    for bone in bones:
        for cube in bone[4]:
            flat.append([bone[0], cube])
    x = y = shelf_h = 0
    for entry in flat:
        rw, rh = unwrap_size(entry[1][1])
        if x + rw > ATLAS_W:
            x, y, shelf_h = 0, y + shelf_h + 1, 0
        entry.append((x, y))
        entry.append((rw, rh))
        x += rw + 1
        shelf_h = max(shelf_h, rh)
    return flat, int(math.ceil((y + shelf_h) / 16.0) * 16)


def paint(arr, rng, uv, rect, material):
    (ux, uy), (rw, rh) = uv, rect
    base, sec = (np.array(c, dtype=np.float32) for c in MAT[material])
    noise = rng.normal(0.0, 14.0, size=(rh, rw, 1)).astype(np.float32)
    tile = np.clip(base.reshape(1, 1, 3) + noise, 0, 255)
    mott = rng.random((rh, rw, 1)) > 0.72
    tile = np.where(mott, np.clip(sec.reshape(1, 1, 3) + noise, 0, 255), tile)
    if rh > 2 and rw > 2:
        tile[0, :, :] *= 0.6
        tile[-1, :, :] *= 0.6
        tile[:, 0, :] *= 0.6
        tile[:, -1, :] *= 0.6
    arr[uy:uy + rh, ux:ux + rw, :] = tile.astype(np.uint8)


def build_geo(bones, flat, tex_h, ident):
    per_bone = {}
    for name, cube, uv, rect in flat:
        per_bone.setdefault(name, []).append((cube, uv))
    bones_json = []
    for name, parent, pivot, rot, cubes in bones:
        b = {"name": name, "pivot": list(pivot)}
        if parent is not None:
            b["parent"] = parent
        if rot is not None:
            b["rotation"] = list(rot)
        if cubes:
            cj = []
            for cube, uv in per_bone[name]:
                e = {"origin": list(cube[0]), "size": list(cube[1]), "uv": [uv[0], uv[1]]}
                if len(cube) > 3 and cube[3]:
                    e["inflate"] = cube[3]
                cj.append(e)
            b["cubes"] = cj
        bones_json.append(b)
    return {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": "geometry.lethalbreed." + ident, "texture_width": ATLAS_W,
                        "texture_height": tex_h, "visible_bounds_width": 8, "visible_bounds_height": 8,
                        "visible_bounds_offset": [0, 2, 0]},
        "bones": bones_json}]}


def egg_icon(rgb, path, seed):
    rng = np.random.default_rng(seed)
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    r, g, b = rgb
    dark = (int(r * 0.55), int(g * 0.55), int(b * 0.55), 255)
    d.ellipse([4, 2, 11, 13], fill=(r, g, b, 255), outline=dark)
    for _ in range(6):
        im.putpixel((5 + int(rng.integers(0, 5)), 4 + int(rng.integers(0, 8))), dark)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    im.save(path)


def kf(pairs, length):
    return {str(round(f * length, 3)): list(v) for f, v in pairs}


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(obj, f, indent=2)


# ---------------------------------------------------------------------------------------------------------
# ANIMATION LIBRARY — one parametrised gait per named style, plus a grounded crawler set. Every clip only
# addresses canonical bones; per-model extra bones are animated through the spec's "anim_overrides".
# ---------------------------------------------------------------------------------------------------------

STYLE_DEFAULTS = {  # walk length, idle length  (spec.gait may override)
    "normal":  (1.0, 3.0), "heavy": (1.5, 4.0), "stiff": (1.0, 3.0), "fast": (0.75, 1.8),
    "shamble": (1.3, 3.4), "stagger": (1.15, 3.2), "limp": (1.1, 3.0), "lurch": (1.0, 3.0),
}


def jump_biped():
    """A beastly leap: drop onto all fours (body pitches forward-down, hands plant, legs coil), then push off
    hard with arms AND legs and launch, holding the airborne forward-pitched pose. Canonical bones only."""
    return {"loop": "hold_on_last_frame", "animation_length": 0.75, "bones": {
        # deep forward crouch onto all fours (torso pitches toward horizontal and drops), then extend up.
        "body": {"rotation": {"0.0": [0, 0, 0], "0.22": [56, 0, 0], "0.4": [16, 0, 0], "0.75": [30, 0, 0]},
                 "position": {"0.0": [0, 0, 0], "0.22": [0, -4, 2], "0.4": [0, 3, 0], "0.75": [0, 1, 1]}},
        "head": {"rotation": {"0.0": [0, 0, 0], "0.22": [-42, 0, 0], "0.4": [-22, 0, 0], "0.75": [-15, 0, 0]}},
        # arms swing forward-DOWN to plant the hands on the ground, then sweep back hard to push off.
        "arm_r": {"rotation": {"0.0": [0, 0, 0], "0.2": [48, 0, -4], "0.42": [-50, 0, -8], "0.75": [-24, 0, -6]}},
        "arm_l": {"rotation": {"0.0": [0, 0, 0], "0.2": [48, 0, 4], "0.42": [-50, 0, 8], "0.75": [-24, 0, 6]}},
        # legs coil under, then drive out.
        "leg_r": {"rotation": {"0.0": [0, 0, 0], "0.22": [44, 0, 0], "0.4": [-26, 0, 0], "0.75": [-34, 0, 0]}},
        "leg_l": {"rotation": {"0.0": [0, 0, 0], "0.22": [40, 0, 0], "0.4": [-30, 0, 0], "0.75": [-38, 0, 0]}},
    }}


def jump_crawler():
    """A crawler is already on all fours: it rears the front up, reaches both arms forward, then heaves off them
    to lunge up-and-forward, holding the leap."""
    return {"loop": "hold_on_last_frame", "animation_length": 0.7, "bones": {
        "body": {"rotation": {"0.0": [0, 0, 0], "0.18": [-15, 0, 0], "0.38": [8, 0, 0], "0.7": [-8, 0, 0]},
                 "position": {"0.0": [0, 0, 0], "0.2": [0, 2, 2], "0.7": [0, 1, 1]}},
        "head": {"rotation": {"0.0": [0, 0, 0], "0.2": [-14, 0, 0], "0.7": [-6, 0, 0]}},
        "arm_r": {"rotation": {"0.0": [0, 0, 0], "0.15": [-46, 0, 0], "0.38": [16, 0, 0], "0.7": [-20, 0, 0]}},
        "arm_l": {"rotation": {"0.0": [0, 0, 0], "0.15": [-46, 0, 0], "0.38": [16, 0, 0], "0.7": [-20, 0, 0]}},
        "leg_r": {"rotation": {"0.0": [0, 0, 0], "0.3": [12, 0, 0], "0.7": [0, 0, 0]}},
        "leg_l": {"rotation": {"0.0": [0, 0, 0], "0.3": [12, 0, 0], "0.7": [0, 0, 0]}},
    }}


def build_biped_anims(style, wl, il):
    heavy = style == "heavy"
    stiff = style == "stiff"
    fast = style == "fast"
    legamp = 26 if heavy else 38
    armamp = 14 if heavy else 20

    # ---- legs / body per style ----------------------------------------------------------------------
    if stiff:
        legs_r = [(0, [-22, 0, 0]), (0.08, [-22, 0, 0]), (0.13, [20, 0, 0]), (0.55, [20, 0, 0]), (0.6, [-22, 0, 0]), (1, [-22, 0, 0])]
        legs_l = [(0, [20, 0, 0]), (0.08, [20, 0, 0]), (0.13, [-22, 0, 0]), (0.55, [-22, 0, 0]), (0.6, [20, 0, 0]), (1, [20, 0, 0])]
    elif style == "limp":  # right leg barely swings + drags, weight sinks onto it each step
        legs_r = [(0, [-10, 0, 0]), (0.5, [6, 0, 0]), (1, [-10, 0, 0])]
        legs_l = [(0, [42, 0, 0]), (0.5, [-30, 0, 0]), (1, [42, 0, 0])]
    elif style == "shamble":  # uneven, asymmetric amplitude, slow drag-through
        legs_r = [(0, [-30, 0, 0]), (0.55, [24, 0, 0]), (1, [-30, 0, 0])]
        legs_l = [(0, [22, 0, 0]), (0.45, [-34, 0, 0]), (1, [22, 0, 0])]
    else:
        legs_r = [(0, [-legamp, 0, 0]), (0.5, [legamp, 0, 0]), (1, [-legamp, 0, 0])]
        legs_l = [(0, [legamp, 0, 0]), (0.5, [-legamp, 0, 0]), (1, [legamp, 0, 0])]

    if style == "stagger":  # heavy side-to-side roll of the whole torso
        body_rot = [(0, [4, 0, 12]), (0.25, [6, 0, -4]), (0.5, [4, 0, 14]), (0.75, [7, 0, -6]), (1, [4, 0, 12])]
        body_pos = [(0, [0, 0, 0]), (0.25, [1.5, -1, 0]), (0.5, [-1.5, 0, 0]), (0.75, [1.5, -1, 0]), (1, [0, 0, 0])]
    elif style == "lurch":  # pitched forward, bobbing down hard on each step
        body_rot = [(0, [14, 0, 3]), (0.5, [18, 0, -3]), (1, [14, 0, 3])]
        body_pos = [(0, [0, 0, 0]), (0.25, [0, -2.5, 0]), (0.5, [0, 0, 0]), (0.75, [0, -2.5, 0]), (1, [0, 0, 0])]
    elif style == "limp":
        body_rot = [(0, [4, 0, 8]), (0.5, [4, 0, 2]), (1, [4, 0, 8])]
        body_pos = [(0, [0, 0, 0]), (0.25, [0, -2, 0]), (0.5, [0, 0, 0]), (0.75, [0, -0.5, 0]), (1, [0, 0, 0])]
    else:
        body_rot = [(0, [3, 0, 4]), (0.5, [3, 0, -4]), (1, [3, 0, 4])]
        body_pos = [(0, [0, 0, 0]), (0.25, [0, -1.5, 0]), (0.5, [0, 0, 0]), (0.75, [0, -1.5, 0]), (1, [0, 0, 0])]

    walk = {"loop": True, "animation_length": wl, "bones": {
        "leg_r": {"rotation": kf(legs_r, wl)},
        "leg_l": {"rotation": kf(legs_l, wl)},
        "body": {"rotation": kf(body_rot, wl), "position": kf(body_pos, wl)},
        "arm_r": {"rotation": kf([(0, [armamp, 0, 0]), (0.5, [-armamp, 0, 0]), (1, [armamp, 0, 0])], wl)},
        "arm_l": {"rotation": kf([(0, [-armamp, 0, 0]), (0.5, [armamp, 0, 0]), (1, [-armamp, 0, 0])], wl)},
        "head": {"rotation": kf([(0, [0, 0, 0]), (0.5, [6, 0, 3]), (1, [0, 0, 0])], wl)},
    }}

    # ---- idle — never static: breathing/sway, wandering head-loll, working jaw, arm tremor -----------
    tr = 4 if heavy else 6
    head_loll = ([(0, [0, 0, 0]), (0.1, [-9, 8, 0]), (0.22, [7, -7, 3]), (0.34, [-6, 5, -4]),
                  (0.48, [9, -6, 5]), (0.62, [-7, 7, -3]), (0.8, [5, -4, 4]), (1, [0, 0, 0])] if fast
                 else [(0, [0, 0, 0]), (0.15, [-6, -6, 5]), (0.33, [6, -3, -5]), (0.5, [-4, 7, 6]),
                       (0.68, [8, 4, -6]), (0.85, [-5, -2, 4]), (1, [0, 0, 0])])
    idle_bones = {
        "body": {"rotation": kf([(0, [0, 0, 1]), (0.25, [4 if heavy else 3, 0, -3]), (0.5, [1, 0, 3]),
                                 (0.75, [4 if heavy else 3, 0, -3]), (1, [0, 0, 1])], il)},
        "head": {"rotation": kf(head_loll, il)},
        "jaw": {"rotation": kf([(0, [2, 0, 0]), (0.2, [19, 0, 0]), (0.35, [6, 0, 0]), (0.5, [23, 0, 0]),
                                (0.65, [5, 0, 0]), (0.82, [16, 0, 0]), (1, [2, 0, 0])], il)},
        "arm_r": {"rotation": kf([(0, [0, 0, 0]), (0.12, [tr, 0, -3]), (0.26, [-tr, 0, 3]), (0.42, [tr + 2, 0, -1]),
                                  (0.62, [-tr, 0, 2]), (0.82, [tr - 1, 0, -2]), (1, [0, 0, 0])], il)},
        "arm_l": {"rotation": kf([(0, [0, 0, 0]), (0.16, [-tr, 0, 3]), (0.32, [tr, 0, -3]), (0.52, [-tr - 1, 0, 2]),
                                  (0.72, [tr - 2, 0, -2]), (1, [0, 0, 0])], il)},
        "leg_r": {"rotation": kf([(0, [0, 0, 0]), (0.5, [3, 0, 0]), (1, [0, 0, 0])], il)},
        "leg_l": {"rotation": kf([(0, [0, 0, 0]), (0.5, [-3, 0, 0]), (1, [0, 0, 0])], il)},
    }
    if heavy:
        idle_bones["body"]["position"] = kf([(0, [0, 0, 0]), (0.3, [1.5, -0.6, 0]), (0.6, [-1.5, 0, 0]), (1, [0, 0, 0])], il)
    if stiff:
        idle_bones["head"] = {"rotation": kf([(0, [0, 0, 0]), (0.3, [0, 0, 0]), (0.33, [0, -18, 0]), (0.6, [0, -18, 0]),
                                              (0.63, [10, 6, 0]), (0.9, [10, 6, 0]), (0.93, [0, 0, 0]), (1, [0, 0, 0])], il)}
        idle_bones["arm_r"] = {"rotation": kf([(0, [0, 0, 0]), (0.45, [0, 0, 0]), (0.48, [12, 0, -5]), (1, [12, 0, -5])], il)}
    idle = {"loop": True, "animation_length": il, "bones": idle_bones}

    attack = {"loop": False, "animation_length": 0.6, "bones": {
        "arm_r": {"rotation": {"0.0": [0, 0, 0], "0.2": [50, 0, -8], "0.4": [10, 0, 0], "0.6": [0, 0, 0]}},
        "arm_l": {"rotation": {"0.0": [0, 0, 0], "0.2": [50, 0, 8], "0.4": [10, 0, 0], "0.6": [0, 0, 0]}},
        "jaw": {"rotation": {"0.0": [0, 0, 0], "0.15": [50, 0, 0], "0.3": [0, 0, 0], "0.6": [0, 0, 0]}},
        "body": {"rotation": {"0.0": [0, 0, 0], "0.2": [18, 0, 0], "0.6": [0, 0, 0]}},
    }}
    spasm = {"loop": False, "animation_length": 0.5, "bones": {
        "body": {"rotation": {"0.0": [0, 0, 0], "0.1": [6, 0, 5], "0.2": [-5, 0, -5], "0.3": [5, 0, 4], "0.5": [0, 0, 0]}},
        "head": {"rotation": {"0.0": [0, 0, 0], "0.1": [-8, 6, 0], "0.2": [8, -6, 0], "0.3": [-6, 4, 0], "0.5": [0, 0, 0]}},
        "arm_r": {"rotation": {"0.0": [0, 0, 0], "0.1": [20, 0, -10], "0.2": [-16, 0, 8], "0.5": [0, 0, 0]}},
        "arm_l": {"rotation": {"0.0": [0, 0, 0], "0.12": [-18, 0, 10], "0.24": [14, 0, -8], "0.5": [0, 0, 0]}},
        "jaw": {"rotation": {"0.0": [0, 0, 0], "0.08": [26, 0, 0], "0.16": [5, 0, 0], "0.24": [28, 0, 0], "0.5": [0, 0, 0]}},
    }}
    death = {"loop": "hold_on_last_frame", "animation_length": 1.5, "bones": {
        "root": {"position": {"0.0": [0, 0, 0], "0.3": [0, 1, 0], "1.5": [0, -11, 0]},
                 "rotation": {"0.0": [0, 0, 0], "1.5": [-85, 8, 12]}},
        "body": {"rotation": {"0.0": [0, 0, 0], "1.5": [24, 0, 4]}},
        "head": {"rotation": {"0.0": [0, 0, 0], "0.25": [-18, 0, 0], "1.5": [36, 0, 10]}},
        "jaw": {"rotation": {"0.0": [0, 0, 0], "0.4": [46, 0, 0], "1.5": [30, 0, 0]}},
        "arm_r": {"rotation": {"0.0": [0, 0, 0], "0.2": [20, 0, 0], "0.4": [-16, 0, 0], "1.5": [0, 0, 0]}},
    }}
    hurt = {"loop": False, "animation_length": 0.4, "bones": {
        "body": {"rotation": {"0.0": [0, 0, 0], "0.08": [-15, 0, 5], "0.22": [7, 0, -3], "0.4": [0, 0, 0]}},
        "head": {"rotation": {"0.0": [0, 0, 0], "0.08": [-17, 9, 0], "0.22": [6, -4, 0], "0.4": [0, 0, 0]}},
        "arm_r": {"rotation": {"0.0": [0, 0, 0], "0.1": [-26, 0, -12], "0.4": [0, 0, 0]}},
        "arm_l": {"rotation": {"0.0": [0, 0, 0], "0.1": [-26, 0, 12], "0.4": [0, 0, 0]}},
        "jaw": {"rotation": {"0.0": [0, 0, 0], "0.08": [30, 0, 0], "0.4": [0, 0, 0]}},
    }}
    return {"idle": idle, "walk": walk, "attack": attack, "spasm": spasm, "death": death, "hurt": hurt,
            "jump": jump_biped()}


def build_crawler_anims(wl, il):
    """Grounded drag set — arms pull the body forward, torso lurches, legs trail. Body stays flat on the floor."""
    return {
        "idle": {"loop": True, "animation_length": il, "bones": {
            "head": {"rotation": kf([(0, [0, 0, 0]), (0.13, [-8, -6, 4]), (0.3, [6, 4, -5]), (0.5, [-5, 7, 3]),
                                     (0.7, [8, -4, -4]), (0.87, [-4, -3, 3]), (1, [0, 0, 0])], il)},
            "jaw": {"rotation": kf([(0, [2, 0, 0]), (0.17, [20, 0, 0]), (0.3, [6, 0, 0]), (0.5, [24, 0, 0]),
                                    (0.67, [5, 0, 0]), (0.83, [16, 0, 0]), (1, [2, 0, 0])], il)},
            "arm_r": {"rotation": kf([(0, [0, 0, 0]), (0.17, [-10, 0, -3]), (0.33, [4, 0, 2]), (0.57, [-7, 0, -2]),
                                      (0.8, [3, 0, 0]), (1, [0, 0, 0])], il)},
            "arm_l": {"rotation": kf([(0, [0, 0, 0]), (0.23, [8, 0, 3]), (0.47, [-6, 0, -2]), (0.7, [7, 0, 2]), (1, [0, 0, 0])], il)},
            "body": {"rotation": kf([(0, [0, 0, 0]), (0.25, [4, 0, 3]), (0.5, [1, 0, -2]), (0.75, [4, 0, 3]), (1, [0, 0, 0])], il)},
        }},
        "walk": {"loop": True, "animation_length": wl, "bones": {
            "arm_r": {"rotation": kf([(0, [-6, 0, 0]), (0.27, [-42, 0, 0]), (0.55, [2, 0, 0]), (1, [-6, 0, 0])], wl)},
            "arm_l": {"rotation": kf([(0, [2, 0, 0]), (0.5, [-42, 0, 0]), (0.77, [2, 0, 0]), (1, [2, 0, 0])], wl)},
            "body": {"rotation": kf([(0, [0, 0, 0]), (0.27, [-7, 0, 0]), (0.55, [4, 0, 0]), (0.82, [-7, 0, 0]), (1, [0, 0, 0])], wl),
                     "position": kf([(0, [0, 0, 0]), (0.27, [0, 0, 2]), (0.55, [0, 0.5, 0]), (0.82, [0, 0, 2]), (1, [0, 0, 0])], wl)},
            "leg_r": {"rotation": kf([(0, [0, 0, 0]), (0.5, [16, 0, 6]), (1, [0, 0, 0])], wl)},
            "leg_l": {"rotation": kf([(0, [16, 0, 0]), (0.5, [0, 0, -6]), (1, [16, 0, 0])], wl)},
            "head": {"rotation": kf([(0, [0, 0, 0]), (0.27, [8, 0, 0]), (0.55, [-4, 0, 3]), (1, [0, 0, 0])], wl)},
        }},
        "attack": {"loop": False, "animation_length": 0.6, "bones": {
            "body": {"rotation": {"0.0": [0, 0, 0], "0.2": [-16, 0, 0], "0.4": [-2, 0, 0], "0.6": [0, 0, 0]}},
            "jaw": {"rotation": {"0.0": [0, 0, 0], "0.15": [52, 0, 0], "0.3": [-4, 0, 0], "0.6": [0, 0, 0]}},
            "arm_r": {"rotation": {"0.0": [0, 0, 0], "0.2": [-40, 0, -12], "0.4": [-6, 0, 0], "0.6": [0, 0, 0]}},
        }},
        "spasm": {"loop": False, "animation_length": 0.5, "bones": {
            "body": {"rotation": {"0.0": [0, 0, 0], "0.1": [5, 0, 8], "0.2": [-4, 0, -8], "0.3": [4, 0, 6], "0.5": [0, 0, 0]}},
            "head": {"rotation": {"0.0": [0, 0, 0], "0.1": [-8, 6, 0], "0.2": [8, -6, 0], "0.3": [-6, 4, 0], "0.5": [0, 0, 0]}},
            "arm_r": {"rotation": {"0.0": [0, 0, 0], "0.1": [-16, 0, -10], "0.2": [12, 0, 8], "0.5": [0, 0, 0]}},
            "arm_l": {"rotation": {"0.0": [0, 0, 0], "0.12": [-14, 0, 10], "0.24": [12, 0, -8], "0.5": [0, 0, 0]}},
        }},
        "death": {"loop": "hold_on_last_frame", "animation_length": 1.5, "bones": {
            "body": {"rotation": {"0.0": [0, 0, 0], "0.4": [-6, 0, 0], "1.5": [-16, 0, 6]}},
            "head": {"rotation": {"0.0": [0, 0, 0], "0.3": [-14, 0, 0], "1.5": [20, 0, 10]}},
            "jaw": {"rotation": {"0.0": [0, 0, 0], "0.5": [44, 0, 0], "1.5": [28, 0, 0]}},
            "arm_r": {"rotation": {"0.0": [0, 0, 0], "0.2": [-10, 0, 0], "0.4": [10, 0, 0], "1.5": [0, 0, 0]}},
        }},
        "hurt": {"loop": False, "animation_length": 0.4, "bones": {
            "body": {"rotation": {"0.0": [0, 0, 0], "0.08": [-8, 0, 6], "0.22": [4, 0, -4], "0.4": [0, 0, 0]}},
            "head": {"rotation": {"0.0": [0, 0, 0], "0.08": [-12, 8, 0], "0.22": [5, -4, 0], "0.4": [0, 0, 0]}},
            "arm_r": {"rotation": {"0.0": [0, 0, 0], "0.1": [-20, 0, -8], "0.4": [0, 0, 0]}},
            "jaw": {"rotation": {"0.0": [0, 0, 0], "0.08": [28, 0, 0], "0.4": [0, 0, 0]}},
        }},
        "jump": jump_crawler(),
    }


def deep_merge_bones(clips, overrides):
    """Merge per-model anim_overrides (clip -> bone -> channel -> keyframes) onto the generated clips."""
    for clip_name, bones in (overrides or {}).items():
        clip = clips.setdefault(clip_name, {"loop": True, "animation_length": 3.0, "bones": {}})
        cb = clip.setdefault("bones", {})
        for bone, channels in bones.items():
            tgt = cb.setdefault(bone, {})
            for ch, kfs in channels.items():
                tgt[ch] = kfs


# ---------------------------------------------------------------------------------------------------------
# SPEC LOADING + VALIDATION
# ---------------------------------------------------------------------------------------------------------

def _tuplify_bones(bones_json):
    """JSON bones -> the internal tuple form used by pack_cubes/build_geo."""
    out = []
    for b in bones_json:
        name, parent, pivot, rot, cubes = b
        cube_tuples = []
        for c in cubes:
            origin, size, mat = c[0], c[1], c[2]
            inflate = c[3] if len(c) > 3 else None
            entry = (tuple(origin), tuple(size), mat) if inflate in (None, 0) else (tuple(origin), tuple(size), mat, inflate)
            cube_tuples.append(entry)
        out.append((name, parent, tuple(pivot), tuple(rot) if rot else None, cube_tuples))
    return out


def check_spec(spec, bones):
    ident = spec["id"]
    names = [b[0] for b in bones]
    if names.count("root") != 1 or bones[0][0] != "root":
        raise SystemExit(f"{ident}: first bone must be the single 'root'")
    for c in CANONICAL:
        if c not in names:
            raise SystemExit(f"{ident}: missing canonical bone '{c}' (needed so gaits resolve)")
    seen = set()
    lo = math.inf
    gore = 0
    for name, parent, pivot, rot, cubes in bones:
        if name in seen:
            raise SystemExit(f"{ident}: duplicate bone '{name}'")
        seen.add(name)
        if parent is not None and parent not in seen:
            raise SystemExit(f"{ident}: bone '{name}' parent '{parent}' not defined before it")
        for cube in cubes:
            (ox, oy, oz), (sx, sy, sz), mat = cube[0], cube[1], cube[2]
            if min(sx, sy, sz) < 1:
                raise SystemExit(f"{ident}: bone '{name}' has a cube size < 1: {cube[1]}")
            if mat not in MAT:
                raise SystemExit(f"{ident}: unknown material '{mat}' on bone '{name}'")
            lo = min(lo, oy)
            if mat in GORE_MATS:
                gore += 1
    if lo < 0:
        raise SystemExit(f"{ident}: lowest cube y={lo} < 0 (would sink into the floor); keep every cube y>=0")
    if not spec.get("vanilla_look") and gore < MIN_GORE_CUBES:
        raise SystemExit(f"{ident}: only {gore} gore cubes (<{MIN_GORE_CUBES}); horror models must show visible gore")
    return lo


# Every model needs these clips (the Java state machine plays them). 'spasm' is optional (registered but unused).
REQUIRED_CLIPS = ["idle", "walk", "attack", "hurt", "death"]


def build_clips(spec):
    """Resolve a model's animation set: a full custom 'animations' block if the spec provides one, else the
    parametrised gait library — then merge any 'anim_overrides' on top. Returns clip_name -> clip dict."""
    gait = spec.get("gait", {})
    style = gait.get("style", "normal")
    d_wl, d_il = STYLE_DEFAULTS.get(style, STYLE_DEFAULTS["normal"])
    wl, il = gait.get("walk", d_wl), gait.get("idle", d_il)
    custom = spec.get("animations")
    if custom:
        # A full hand-authored set is authoritative — ignore any legacy anim_overrides so they can't pollute it.
        clips = json.loads(json.dumps(custom))  # deep copy so nothing mutates the loaded spec
    else:
        clips = build_crawler_anims(wl, il) if spec.get("crawler") else build_biped_anims(style, wl, il)
        deep_merge_bones(clips, spec.get("anim_overrides"))
    # Ensure a jump clip exists on every model (the on-all-fours leap), even hand-authored sets that omit it.
    if "jump" not in clips:
        clips["jump"] = jump_crawler() if spec.get("crawler") else jump_biped()
    # attack/hurt are played on a SEPARATE "action" controller (upper body) so the LEGS keep walking underneath
    # — a zombie can walk and attack at once. Strip leg bones from them so they never freeze the walk cycle.
    for cn in ("attack", "hurt"):
        b = (clips.get(cn) or {}).get("bones")
        if b:
            b.pop("leg_r", None)
            b.pop("leg_l", None)
    return clips


def check_anims(spec, clips, bone_names):
    """Validate a resolved animation set: required clips present, every animated bone exists, and — the key
    anti-slide rule — 'walk' actually drives the locomotion bones (both legs for bipeds, the arms for a crawler
    that pulls itself along), so a moving model never just glides."""
    ident = spec["id"]
    for r in REQUIRED_CLIPS:
        if r not in clips:
            raise SystemExit(f"{ident}: animation set missing required clip '{r}'")
    for clip_name, clip in clips.items():
        for bone in (clip.get("bones") or {}):
            if bone not in bone_names:
                raise SystemExit(f"{ident}: clip '{clip_name}' animates unknown bone '{bone}' "
                                 f"(not in this model's bones)")
    walk_bones = set((clips["walk"].get("bones") or {}).keys())
    if spec.get("crawler"):
        if not (walk_bones & {"arm_r", "arm_l"}):
            raise SystemExit(f"{ident}: crawler 'walk' must animate arm_r and/or arm_l (it drags on its arms), "
                             f"else it slides")
    elif not {"leg_r", "leg_l"}.issubset(walk_bones):
        raise SystemExit(f"{ident}: 'walk' must animate BOTH leg_r and leg_l (stepping), else it slides")


def load_specs():
    specs = []
    for path in sorted(glob.glob(os.path.join(MODELS_DIR, "*.json"))):
        with open(path) as f:
            spec = json.load(f)
        spec.setdefault("id", os.path.splitext(os.path.basename(path))[0])
        # per-model extra materials
        for k, (a, b) in (spec.get("materials") or {}).items():
            MAT[k] = (tuple(a), tuple(b))
        specs.append(spec)
    return specs


# ---------------------------------------------------------------------------------------------------------
# MAIN
# ---------------------------------------------------------------------------------------------------------

def main():
    specs = load_specs()
    if not specs:
        raise SystemExit(f"no model specs found in {MODELS_DIR}")
    shared_anims = {}
    for i, spec in enumerate(specs):
        ident = spec["id"]
        bones = _tuplify_bones(spec["bones"])
        lo = check_spec(spec, bones)

        flat, tex_h = pack_cubes(bones)
        rng = np.random.default_rng(7 + i)
        arr = np.zeros((tex_h, ATLAS_W, 4), dtype=np.uint8)
        for name, cube, uv, rect in flat:
            paint(arr[:, :, :3], rng, uv, rect, cube[2])
            (ux, uy), (rw, rh) = uv, rect
            arr[uy:uy + rh, ux:ux + rw, 3] = 255

        write_json(os.path.join(ASSETS, "geckolib", "models", "entity", ident + ".geo.json"),
                   build_geo(bones, flat, tex_h, ident))
        Image.fromarray(arr, "RGBA").save(os.path.join(ASSETS, "textures", "entity", ident + ".png"))

        egg = ident + "_spawn_egg"
        egg_rgb = tuple(spec.get("egg_rgb", (110, 120, 80)))
        egg_icon(egg_rgb, os.path.join(ASSETS, "textures", "item", egg + ".png"), 100 + i)
        write_json(os.path.join(ASSETS, "items", egg + ".json"),
                   {"model": {"type": "minecraft:model", "model": "lethalbreed:item/" + egg}})
        write_json(os.path.join(ASSETS, "models", "item", egg + ".json"),
                   {"parent": "minecraft:item/generated", "textures": {"layer0": "lethalbreed:item/" + egg}})

        # animation clips → shared file, namespaced "<id>_<clip>"
        clips = build_clips(spec)
        check_anims(spec, clips, {b[0] for b in bones})
        for clip_name, clip in clips.items():
            shared_anims[f"{ident}_{clip_name}"] = clip

        kind = "custom" if spec.get("animations") else ("crawler" if spec.get("crawler") else spec.get("gait", {}).get("style", "normal"))
        print(f"{ident:16s} cubes={len(flat):2d} atlas=128x{tex_h:<3d} floor_ok(y>={int(lo)}) anims={len(clips)}({kind})")

    write_json(os.path.join(ASSETS, "geckolib", "animations", "entity", "horror.animation.json"),
               {"format_version": "1.8.0", "animations": shared_anims})
    print(f"\n{len(specs)} models -> shared horror.animation.json ({len(shared_anims)} clips)")


def check_one(path):
    """Validate a SINGLE spec file (no assets written) — used by design agents to self-check.
    Exit 0 + 'OK ...' on success, exit 1 + 'FAIL ...' with the reason on failure."""
    import sys
    try:
        with open(path) as f:
            spec = json.load(f)
        spec.setdefault("id", os.path.splitext(os.path.basename(path))[0])
        for k, (a, b) in (spec.get("materials") or {}).items():
            MAT[k] = (tuple(a), tuple(b))
        bones = _tuplify_bones(spec["bones"])
        lo = check_spec(spec, bones)
        flat, tex_h = pack_cubes(bones)        # exercise packing so structural errors surface here too
        build_geo(bones, flat, tex_h, spec["id"])
        clips = build_clips(spec)              # resolve + validate the animation set (anti-slide rule included)
        check_anims(spec, clips, {b[0] for b in bones})
        kind = "custom" if spec.get("animations") else ("crawler" if spec.get("crawler") else spec.get("gait", {}).get("style", "normal"))
        print(f"OK {spec['id']}: cubes={len(flat)} atlas=128x{tex_h} floor_ok(y>={int(lo)}) anims={len(clips)}({kind})")
        return 0
    except SystemExit as e:               # check_spec signals validation failures this way
        print(f"FAIL: {e}")
        return 1
    except Exception as e:
        print(f"FAIL: {type(e).__name__}: {e}")
        return 1


if __name__ == "__main__":
    import sys
    if len(sys.argv) >= 3 and sys.argv[1] == "--check":
        raise SystemExit(check_one(sys.argv[2]))
    main()
