# Horror model spec — authoring guide

One JSON file here = **one grotesque render model** a plain `minecraft:zombie` can wear instead of the vanilla
model. `gen_horror_models.py` turns each spec into a GeckoLib `.geo.json`, a matching procedural gore texture,
a spawn-egg icon, and one shared animation clip-set. **You only write the spec JSON — never the .geo.json.**

## Coordinate system (Minecraft model units)
- 16 units = 1 block. **Y is up. The ground is `y = 0`.** Z **negative = front** (the direction the mob faces).
- A standing humanoid is ~32 units tall (legs 0–12, torso 12–24, head 24–32). Vanilla box sizes: head 8×8×8,
  torso 8×12×4, arm 4×12×4, leg 4×12×4. Deform hard from there.
- **Every cube must have `origin.y >= 0`.** Nothing floats, nothing sinks. The validator rejects `y < 0`.

## Schema
```json
{
  "id": "<id>",                       // matches the filename, snake_case
  "concept": "<one sentence>",        // what it is, for humans
  "egg_rgb": [r, g, b],               // spawn-egg tint 0..255
  "gait": { "style": "shamble", "walk": 1.2, "idle": 3.4 },   // walk/idle lengths optional
  "crawler": false,                   // true => grounded drag animation instead of a biped gait
  "materials": { "NEWMAT": [[r,g,b],[r2,g2,b2]] },            // OPTIONAL extra palette entries (base, secondary)
  "bones": [ [name, parent|null, [px,py,pz], [rx,ry,rz]|null, [ cubes ] ], ... ],
  "anim_overrides": { "walk": { "bone": { "rotation": { "0.0":[..], "0.5":[..], "1.0":[..] } } } }  // OPTIONAL
}
```
- **cube** = `[[ox,oy,oz], [sx,sy,sz], "MAT"]` or `[[ox,oy,oz],[sx,sy,sz],"MAT", inflate]`. Sizes are integers ≥ 1.
  `inflate` (float) puffs a cube outward evenly — great for bloat/swelling. `origin` is the min corner.
- **bone** = `[name, parent, pivot, rotation, cubes]`. `pivot` is the point the bone rotates about (put it at the
  joint). `rotation` is the bone's **rest pose** in degrees (animation is applied on top of it). A parent must be
  listed **before** its children.

## Canonical skeleton (REQUIRED — do not rename)
Every model MUST contain these 8 bones so the shared gait library animates it:
`root, body, head, jaw, arm_r, arm_l, leg_r, leg_l`.
- `root` is first, pivot `[0,0,0]`, usually no cubes.
- `arm_r`/`arm_l` typically get a **forward rest rotation ~`-70`** on X so the arms reach out (classic zombie).
- You MAY add **extra decorative bones** as children of the canonical ones (e.g. `jaw_hang`, `arm_stub`,
  `entrail_a`, `gut_l`, `spike`, `eye_dangle`). Extra bones stay still unless you animate them in `anim_overrides`.
- A limb can be "missing": give it a tiny stump cube (size ≥ 1) so the bone exists but the flesh is gone.

## Materials (pick from these; add more via "materials")
SKIN, SKIN2, GREY, PALE (skin tones) · BLOAT, BLOATDK (swollen) · FLESH, FLESHDK (raw muscle) · BONE, TEETH
(bone/teeth) · ENTRAIL (guts) · PUS, GROWTH (blisters/tumours) · CHAR, EMBER (burnt/glowing) · SOCKET (eye holes)
· EYE (red eye). The painter mottles each cube with noise + edge shading, so texture variety comes mostly from
**which materials you place where** + your `egg_rgb`/`materials` palette. Two models should never share a palette.

## Gait styles (biped) — `gait.style`
`normal` (shuffle) · `heavy` (slow, big weight-shift) · `stiff` (robotic) · `fast` (twitchy) · `shamble`
(uneven, asymmetric legs) · `stagger` (side-to-side torso roll) · `limp` (right leg drags) · `lurch` (pitched
forward, bobbing). For a **crawler** set `"crawler": true` (ignore style): body stays flat, arms pull it along,
legs trail — make sure the whole body sits at low Y with `head`/`jaw` lifted at the front.

## Rules the validator enforces
1. All 8 canonical bones present; `root` first; every parent defined before its child; no duplicate names.
2. Every cube `origin.y >= 0`; every cube size ≥ 1; every material known.
3. At least 3 "gore" cubes (BONE/FLESH/FLESHDK/ENTRAIL/EMBER/PUS/GROWTH) — horror models must show visible gore.

## Make it grotesque (the whole point)
Give each model a **silhouette you could name in the dark**: broken/pendant jaw, an arm stripped to bone, a
split or sheared skull, an open ribcage with hanging guts, missing/twisted/disproportionate limbs, a bloated or
emaciated or hunched torso. Pile on **visible gore**. Use rest rotations to twist the pose (cocked head, dropped
shoulder, buckled knee). For dangling/broken parts, add an extra bone at the break and swing it in `anim_overrides`
so it moves believably during walk/idle.

## Worked examples
- `examples/ecorche.json` — a biped (flayed, exposed ribs, bone-through-limbs).
- `examples/rampant.json` — a **crawler** flat on the floor (study its low Y values and lifted head).

## Self-check before you finish
```
python3 mod/tools/gen_horror_models.py --check mod/tools/models/<id>.json
```
Iterate until it prints `OK <id>: ...`. Then you're done.
