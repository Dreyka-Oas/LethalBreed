// Infester generator — small infection creature, <= 1 block tall (16px).
// Squat lumpy body, low drooping head, four short crawling legs, fungal
// spore growths on the back. Legs are simple bones so they can animate.
// Run: node gen_infester.mjs
import { randomUUID } from "node:crypto";
import { writeFileSync } from "node:fs";

const elements = [];

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
// BODY — squat bulbous host, sits low. Total height kept under 16px.
// =========================================================================
const bodyKids = [];
bodyKids.push(cube("torso",    [-5, 4, -5], [5, 11, 6],  { color: 6 }));   // main lump
bodyKids.push(cube("torsoLo",  [-4, 2, -4], [4, 5, 5],   { color: 7 }));   // belly sag
bodyKids.push(cube("hump",     [-4, 9, -3], [4, 13, 4],  { color: 6 }));   // raised back
// fungal spore nodes on the back (infection tells)
bodyKids.push(cube("spore1",   [-3, 12, -1],[-0.5, 15, 2],{ color: 4 }));
bodyKids.push(cube("spore2",   [1, 11, 0],  [3, 14, 3],  { color: 4 }));
bodyKids.push(cube("spore3",   [-1, 12, 2], [1, 14.5, 4],{ color: 5 }));
const body = bone("body", [0, 6, 0]);

// =========================================================================
// HEAD — drooping forward off the front of the torso, low to the ground
// =========================================================================
const headKids = [];
headKids.push(cube("skull",  [-3.5, 4, 6],  [3.5, 9, 11], { color: 7 }));
headKids.push(cube("jaw",    [-3, 2.5, 7],  [3, 5, 11.5], { color: 0 }));
headKids.push(cube("eyeL",   [-3, 6, 10.5], [-1, 8, 11.5],{ color: 1 }));   // glowing infected eyes
headKids.push(cube("eyeR",   [1, 6, 10.5],  [3, 8, 11.5], { color: 1 }));
headKids.push(cube("growth", [-1, 8.5, 7],  [1, 11, 9],   { color: 4 }));   // spore tuft on skull
const head = bone("head", [0, 7, 6], { rotation: [18, 0, 0], children: headKids });
body.children = [...bodyKids, head];

// =========================================================================
// LEGS — four short stubby crawling legs. Simple 1-bone each, splayed out.
// =========================================================================
function leg(idx, x, z, yaw) {
  const c = cube(`leg${idx}`, [x - 1.5, 0, z - 1.5], [x + 1.5, 4, z + 1.5], { color: 7, origin: [x, 4, z] });
  const foot = cube(`foot${idx}`, [x - 2, 0, z - 2], [x + 2, 1.5, z + 2], { color: 0, origin: [x, 4, z] });
  return bone(`legRoot${idx}`, [x, 4, z], { rotation: [0, yaw, 0], children: [c, foot] });
}
const legs = [
  leg(0, -4, 4,  20),   // front-left
  leg(1,  4, 4, -20),   // front-right
  leg(2, -4, -3, 20),   // back-left
  leg(3,  4, -3,-20),   // back-right
];

const project = {
  meta: { format_version: "4.5", model_format: "modded_entity", box_uv: false },
  name: "infester", model_identifier: "infester",
  visible_box: [1, 1, 1], variable_placeholders: "", variable_placeholder_buttons: [],
  resolution: { width: 64, height: 64 },
  elements, outliner: [body, ...legs], textures: [],
};
writeFileSync(new URL("./infester.bbmodel", import.meta.url), JSON.stringify(project, null, 1));
console.log(`infester.bbmodel: ${elements.length} cubes, height <= 15px`);
