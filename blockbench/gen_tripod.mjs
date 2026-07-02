// Tripod generator v2 — War-of-the-Worlds (2005 / Extermination mod) style:
// curved cobra-hood hull with a glowing eye, three long thin arched legs in a
// wide spider stance, heat-ray arm, hanging tentacles.
// Legs are a kinematic stack (segments hang straight at rest, joint rotations
// pose the arch) so they animate cleanly. Run: node gen_tripod.mjs
import { randomUUID } from "node:crypto";
import { writeFileSync } from "node:fs";

const elements = [];

// ---- tunable pose (degrees) --------------------------------------------
const HIP_KICK = -58;   // femur swings up & out from the body
const KNEE_BEND = 122;  // sharp elbow, tibia drops back toward ground
const ANKLE = 28;       // foot angle
const TOE = 26;
const HULL_TILT = 12;   // hood leans forward

// ---- helpers ------------------------------------------------------------
function uvFaces([fx, fy, fz], [tx, ty, tz]) {
  const sx = Math.abs(tx - fx), sy = Math.abs(ty - fy), sz = Math.abs(tz - fz);
  const f = (w, h) => ({ uv: [0, 0, w, h], texture: 0 });
  return { north: f(sx, sy), south: f(sx, sy), east: f(sz, sy), west: f(sz, sy), up: f(sx, sz), down: f(sx, sz) };
}
function cube(name, from, to, { origin = from, color = 0 } = {}) {
  const uuid = randomUUID();
  elements.push({ name, box_uv: false, rescale: false, locked: false, from, to, autouv: 1, color: color % 8, origin, faces: uvFaces(from, to), uuid });
  return uuid;
}
function bone(name, origin, { rotation = [0, 0, 0], children = [] } = {}) {
  return { name, origin, rotation, uuid: randomUUID(), export: true, isOpen: false, visibility: true, children };
}

// =========================================================================
// HULL — curved cobra hood, glowing eye, tilted forward
// =========================================================================
const hullKids = [];
// neck / spine top
hullKids.push(cube("neck",      [-6, 156, -6],  [6, 167, 6],   { color: 7 }));
// widest mid hood
hullKids.push(cube("hoodMid",   [-12, 148, -10],[12, 160, 10], { color: 6 }));
hullKids.push(cube("hoodMid2",  [-13, 150, -4], [13, 158, 8],  { color: 6 }));
// swept back taper
hullKids.push(cube("hoodBack",  [-9, 150, -14], [9, 159, -10], { color: 6 }));
hullKids.push(cube("hoodBack2", [-6, 151, -17], [6, 157, -14], { color: 7 }));
// front brow overhang + down-curved lip (the cowl over the eye)
hullKids.push(cube("brow",      [-13, 150, 8],  [13, 160, 17], { color: 6 }));
hullKids.push(cube("browLip",   [-11, 145, 15], [11, 153, 21], { color: 6 }));
hullKids.push(cube("browLip2",  [-8, 142, 19],  [8, 148, 23],  { color: 7 }));
// glowing eye recessed under the lip
hullKids.push(cube("eye",       [-6, 147, 17],  [6, 154, 19.5],{ color: 4 }));
hullKids.push(cube("eyeCore",   [-2.5, 149, 18],[2.5, 152, 20],{ color: 1 }));
// underbelly + chin where legs/tentacles/ray mount
hullKids.push(cube("belly",     [-8, 140, -8],  [8, 148, 8],   { color: 7 }));
hullKids.push(cube("chin",      [-5, 136, 4],   [5, 142, 13],  { color: 7 }));
hullKids.push(cube("collar",    [-10, 146, -9], [10, 150, 9],  { color: 0 }));
const hull = bone("hull", [0, 150, 0], { rotation: [HULL_TILT, 0, 0] });

// heat-ray arm (mounted under the chin, extends down & forward)
const rayKids = [];
rayKids.push(cube("rayBase",  [-3, 132, 6],  [3, 138, 14],  { color: 7 }));
rayKids.push(cube("rayArm",   [-2, 120, 12], [2, 134, 18],  { color: 7 }));
rayKids.push(cube("rayHead",  [-3.5, 116, 16],[3.5, 124, 23],{ color: 6 }));
rayKids.push(cube("rayLens",  [-2, 118, 23], [2, 122, 25],  { color: 4 }));
const ray = bone("heatRay", [0, 135, 10], { rotation: [28, 0, 0], children: rayKids });

// tentacles — 3 thin segmented, hanging under the belly
function tentacle(idx, ang) {
  const a = (ang * Math.PI) / 180, r = 4;
  const bx = +(r * Math.sin(a)).toFixed(2), bz = +(r * Math.cos(a)).toFixed(2);
  const lens = [8, 7, 6, 5], ws = [1.6, 1.3, 1.0, 0.7];
  let y = 140, child = null;
  const nodes = [];
  for (let s = 0; s < lens.length; s++) {
    const w = ws[s], yT = y, yB = y - lens[s];
    nodes.push({ cu: cube(`tent${idx}_s${s}`, [bx - w, yB, bz - w], [bx + w, yT, bz + w], { color: 7, origin: [bx, yT, bz] }), pv: [bx, yT, bz] });
    y = yB;
  }
  for (let s = nodes.length - 1; s >= 0; s--) {
    const kids = [nodes[s].cu]; if (child) kids.push(child);
    child = bone(`tent${idx}_b${s}`, nodes[s].pv, { rotation: [22, 0, 0], children: kids });
  }
  return child;
}
hull.children = [...hullKids, ray, tentacle(0, 0), tentacle(1, 120), tentacle(2, 240)];

// =========================================================================
// LEGS — long thin arched legs, kinematic stack, wide stance via root yaw
// authored straight-down at rest from hip (0,150,6); joint rotations arch it
// =========================================================================
function buildLeg(idx, yaw) {
  // segment 4: claw / toe
  const claw = bone(`L${idx}_toe`, [0, 70, 6], { rotation: [TOE, 0, 0], children: [
    cube(`L${idx}_claw`,  [-2, 58, 3],   [2, 71, 11],   { color: 0, origin: [0, 70, 6] }),
    cube(`L${idx}_clawTip`,[-1, 50, 6],  [1, 60, 9],    { color: 0, origin: [0, 70, 6] }),
  ]});
  // segment 3: tarsus (lower shin)
  const tarsus = bone(`L${idx}_ankle`, [0, 100, 6], { rotation: [ANKLE, 0, 0], children: [
    cube(`L${idx}_tarsus`, [-2, 70, 4],  [2, 100, 8],   { color: 7, origin: [0, 100, 6] }),
    cube(`L${idx}_tarsusV`,[-1, 74, 8],  [1, 96, 9.5],  { color: 4, origin: [0, 100, 6] }),
    claw,
  ]});
  // segment 2: tibia
  const tibia = bone(`L${idx}_knee`, [0, 124, 6], { rotation: [KNEE_BEND, 0, 0], children: [
    cube(`L${idx}_tibia`,  [-2.5, 100, 3.5],[2.5, 124, 8.5],{ color: 6, origin: [0, 124, 6] }),
    cube(`L${idx}_tibiaP`, [-3, 104, 8],  [3, 120, 9],    { color: 0, origin: [0, 124, 6] }),
    tarsus,
  ]});
  // segment 1: femur + knee joint sphere
  const femur = bone(`L${idx}_hip`, [0, 150, 6], { rotation: [HIP_KICK, 0, 0], children: [
    cube(`L${idx}_femur`,  [-3, 124, 3],  [3, 150, 9],    { color: 6, origin: [0, 150, 6] }),
    cube(`L${idx}_kneeJt`, [-3.5, 121, 2.5],[3.5, 128, 9.5],{ color: 4, origin: [0, 150, 6] }),
    cube(`L${idx}_femurP`, [-3.5, 130, 8.5],[3.5, 148, 9.5],{ color: 0, origin: [0, 150, 6] }),
    tibia,
  ]});
  // hip housing where the leg meets the hull
  const housing = cube(`L${idx}_housing`, [-5, 146, 4], [5, 156, 12], { color: 7, origin: [0, 150, 6] });
  return bone(`legRoot${idx}`, [0, 150, 0], { rotation: [0, yaw, 0], children: [housing, femur] });
}
const legs = [buildLeg(0, 0), buildLeg(1, 120), buildLeg(2, 240)];

// =========================================================================
// lower the whole rig so the feet sit at y=0 (entity-correct + frames in view)
const SHIFT = 50;
for (const e of elements) {
  e.from = [e.from[0], e.from[1] - SHIFT, e.from[2]];
  e.to = [e.to[0], e.to[1] - SHIFT, e.to[2]];
  e.origin = [e.origin[0], e.origin[1] - SHIFT, e.origin[2]];
}
(function shiftBones(nodes) {
  for (const n of nodes) { n.origin = [n.origin[0], n.origin[1] - SHIFT, n.origin[2]]; if (n.children) shiftBones(n.children.filter((c) => typeof c === "object")); }
})([hull, ...legs]);

const project = {
  meta: { format_version: "4.5", model_format: "modded_entity", box_uv: false },
  name: "tripod", model_identifier: "tripod",
  visible_box: [12, 14, 2], variable_placeholders: "", variable_placeholder_buttons: [],
  resolution: { width: 128, height: 128 },
  elements, outliner: [hull, ...legs], textures: [],
};
writeFileSync(new URL("./tripod.bbmodel", import.meta.url), JSON.stringify(project, null, 1));
console.log(`tripod.bbmodel: ${elements.length} cubes`);
