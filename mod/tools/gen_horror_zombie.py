#!/usr/bin/env python3
"""
Single-source generator for the LethalBreed horror-zombie ROSTER — 10 grotesque variants. From one cube list
per creature it emits: GeckoLib geometry (geckolib/models/entity/<id>.geo.json), a matching procedural gore
texture (textures/entity/<id>.png), a spawn-egg icon (textures/item/<id>_spawn_egg.png) + the two item model
JSONs, and (for the shared-rig variants) an animation file. Geometry and texture share the exact packed UVs.

Design: each creature is derived from the zombie/humanoid base then deformed hard, and EVERY one wears heavy,
VISIBLE gore — dangling intestines, bone shards piercing through the skin, torn-open flesh — because "looking
like a plain zombie" is the failure mode. Size/shape are free (crawlers, giant torn arms, colossi...).
Everything must sit ON the ground (lowest cube at y=0) and animate clearly (pronounced walk).

Roster: profanateur, ecorche, boursoufle, rampant(crawler), empale(spiked), difforme(giant arm),
pendu(broken neck), colosse(huge), emacie(skeletal), brule(charred).
All but profanateur & rampant share one humanoid rig (root, body, head, jaw, arm_r, arm_l, leg_r, leg_l).

Build aid only. Run: python3 tools/gen_horror_zombie.py   (needs Pillow, numpy)
"""
import json
import math
import os

import numpy as np
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, "..", "src", "main", "resources", "assets", "lethalbreed")
ATLAS_W = 128

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
}

# Gore packs appended to the shared humanoid rig (body cube = -4..4 x, 12..24 y, -2..2 z).
GUTS = [((-2, 4, -4), (3, 8, 2), "ENTRAIL"), ((0, 5, -5), (1, 7, 1), "ENTRAIL"),
        ((-3, 6, -4), (1, 6, 1), "ENTRAIL"), ((-3, 10, -3), (4, 3, 1), "FLESH")]      # dangling intestines + torn belly
RIBS = [((-5, 19, -2), (1, 2, 3), "BONE"), ((-5, 21, -1), (1, 2, 2), "BONE"),
        ((4, 20, -2), (1, 2, 3), "BONE")]                                            # rib shards jutting from the sides
ARM_BONE = ((-9, 13, -2), (1, 5, 1), "BONE")                                         # bone through the right upper-arm
LEG_BONE = ((-5, 3, -2), (1, 4, 1), "BONE")                                          # bone through the right shin

# =======================================================================================================
# GEOMETRY.  bone = (name, parent, pivot, rotation|None, [ (origin, size, mat[, inflate]), ... ])
# =======================================================================================================

PROFANATEUR = [
    ("root", None, (0, 0, 0), None, []),
    ("hips", "root", (0, 12, 0), (5, 0, 0), [((-4, 10, -3), (8, 5, 5), "SKIN", 0.25)]),
    ("spine_lower", "hips", (0, 15, -1), (10, 4, 4), [((-4, 15, -2), (8, 5, 4), "SKIN")]),
    ("belly", "spine_lower", (0, 16, -2), None, [((-5, 13, -5), (10, 7, 5), "BLOAT", 1.0)]),
    ("spine_mid", "spine_lower", (0, 20, -1), (9, 4, 3), [((-4, 20, -2), (8, 5, 4), "SKIN", 0.25)]),
    ("ribs_left", "spine_mid", (-4, 23, -1), (0, 0, 10), [
        ((-5, 22, -3), (1, 1, 4), "BONE"), ((-5, 23, -3), (1, 1, 4), "BONE"),
        ((-5, 24, -3), (1, 1, 4), "BONE"), ((-5, 25, -3), (1, 1, 4), "BONE")]),
    ("spine_upper", "spine_mid", (0, 25, -1), (8, 5, 0), [
        ((-4, 25, -2), (8, 6, 5), "SKIN"), ((-2, 27, -3), (4, 3, 1), "FLESH"),
        ((3, 30, -1), (2, 2, 2), "GROWTH"), ((4, 31, 0), (1, 1, 1), "GROWTH")]),
    ("neck", "spine_upper", (0, 31, -1), (16, 0, -6), [((-2, 31, -2), (4, 3, 3), "SKIN")]),
    ("head", "neck", (0, 34, -1), (-8, -12, 10), [
        ((-4, 34, -4), (8, 8, 7), "SKIN"), ((-3, 37, -5), (2, 2, 1), "SOCKET"),
        ((2, 38, -5), (1, 1, 1), "EYE"), ((-3, 34, -4), (6, 1, 1), "TEETH")]),
    ("jaw", "head", (0, 34, -4), (14, 0, 3), [((-3, 30, -4), (6, 3, 5), "SKIN"), ((-3, 33, -4), (6, 1, 1), "TEETH")]),
    ("arm_right", "spine_upper", (-4, 30, 0), (10, 0, -12), [((-8, 24, -2), (4, 7, 4), "SKIN")]),
    ("forearm_right", "arm_right", (-6, 24, 0), (18, 0, 6), [((-8, 17, -2), (4, 7, 4), "SKIN"), ((-7, 18, -3), (1, 5, 1), "BONE")]),
    ("arm_left", "spine_upper", (4, 30, 0), (-38, 18, 22), [((4, 24, -2), (4, 7, 4), "SKIN2")]),
    ("forearm_left", "arm_left", (6, 24, 0), (28, 0, -55), [((4, 17, -2), (4, 7, 4), "SKIN2")]),
    ("leg_right", "hips", (-2, 11, 0), (-6, 0, 3), [((-4, 5, -2), (4, 6, 4), "SKIN"), ((-4, -1, -2), (4, 6, 4), "SKIN"), ((-3, 0, -3), (1, 4, 1), "BONE")]),
    ("leg_left", "hips", (2, 11, 0), (8, 0, -5), [((0, 5, -2), (4, 6, 4), "SKIN")]),
    ("shin_left", "leg_left", (2, 5, 0), (-95, 0, 8), [((0, -1, -2), (4, 6, 4), "SKIN2")]),
    ("entrails", "belly", (0, 13, -4), None, [((-1, 7, -5), (2, 6, 2), "ENTRAIL")]),
    ("entrail_b", "entrails", (1, 10, -4), (0, 0, -8), [((1, 6, -5), (1, 5, 1), "ENTRAIL")]),
    ("entrail_c", "entrails", (-2, 10, -4), (0, 0, 10), [((-3, 7, -5), (1, 5, 1), "ENTRAIL")]),
]

ECORCHE = [
    ("root", None, (0, 0, 0), None, []),
    ("body", "root", (0, 24, 0), (3, 0, 0), [
        ((-4, 12, -2), (8, 12, 4), "FLESH"),
        ((-4, 20, -3), (8, 1, 1), "BONE"), ((-4, 18, -3), (8, 1, 1), "BONE"), ((-4, 16, -3), (8, 1, 1), "BONE"),
        ((-2, 4, -4), (3, 8, 2), "ENTRAIL"), ((0, 5, -5), (1, 7, 1), "ENTRAIL"), ((-3, 6, -4), (1, 6, 1), "ENTRAIL")]),
    ("head", "body", (0, 24, -1), (-3, -4, 3), [
        ((-4, 24, -4), (8, 8, 7), "BONE"), ((-4, 24, -4), (8, 1, 1), "TEETH"),
        ((-3, 28, -5), (2, 2, 1), "SOCKET"), ((1, 28, -5), (2, 2, 1), "SOCKET")]),
    ("jaw", "head", (0, 25, -4), (9, 0, 0), [((-3, 22, -4), (6, 2, 4), "BONE"), ((-3, 23, -4), (6, 1, 1), "TEETH")]),
    ("arm_r", "body", (-4, 23, 0), (-72, 0, 6), [((-8, 11, -2), (4, 12, 4), "FLESH"), ((-9, 13, -2), (1, 6, 1), "BONE")]),
    ("arm_l", "body", (4, 23, 0), (-72, 0, -8), [((4, 11, -2), (4, 12, 4), "FLESH")]),
    ("leg_r", "root", (-2, 12, 0), (0, 0, 2), [((-4, 0, -2), (4, 12, 4), "FLESH"), ((-5, 3, -2), (1, 4, 1), "BONE")]),
    ("leg_l", "root", (2, 12, 0), (0, 0, -2), [((0, 0, -2), (4, 12, 4), "FLESH")]),
]

BOURSOUFLE = [
    ("root", None, (0, 0, 0), None, []),
    ("body", "root", (0, 24, 0), (2, 0, 0), [
        ((-5, 12, -3), (10, 12, 6), "BLOAT", 0.5),
        ((-5, 10, -6), (10, 7, 4), "BLOAT", 1.0),
        ((3, 16, -6), (2, 2, 1), "PUS"), ((-5, 14, -6), (2, 2, 1), "PUS"), ((-1, 20, -6), (2, 2, 1), "GROWTH"),
        ((-3, 11, -7), (6, 3, 1), "FLESH"),
        ((-2, 3, -7), (3, 9, 2), "ENTRAIL"), ((1, 4, -8), (1, 7, 1), "ENTRAIL"), ((-3, 5, -7), (1, 6, 1), "ENTRAIL"),
        ((5, 19, -1), (1, 2, 3), "BONE"), ((-6, 20, -1), (1, 2, 3), "BONE")]),
    ("head", "body", (0, 24, -1), (7, -3, 0), [
        ((-3, 24, -3), (6, 6, 6), "BLOAT"), ((-3, 24, -3), (6, 1, 1), "TEETH"),
        ((-2, 27, -4), (1, 1, 1), "SOCKET"), ((1, 27, -4), (1, 1, 1), "EYE")]),
    ("jaw", "head", (0, 25, -3), (6, 0, 0), [((-2, 22, -3), (4, 2, 4), "BLOAT")]),
    ("arm_r", "body", (-5, 23, 0), (-58, 0, 15), [((-10, 12, -2), (5, 11, 5), "BLOAT"), ((-11, 15, -2), (1, 5, 1), "BONE")]),
    ("arm_l", "body", (5, 23, 0), (-58, 0, -15), [((5, 12, -2), (5, 11, 5), "BLOAT")]),
    ("leg_r", "root", (-3, 12, 0), (0, 0, 5), [((-5, 0, -2), (5, 12, 5), "BLOAT")]),
    ("leg_l", "root", (3, 12, 0), (0, 0, -5), [((0, 0, -2), (5, 12, 5), "BLOAT")]),
]

# rampant — a crawler that LIES ON THE GROUND (every cube y >= 0): long horizontal torso, head lifted at the
# front, arms planted forward on the floor, legs trailing back, guts dragging underneath.
RAMPANT = [
    ("root", None, (0, 0, 0), None, []),
    ("body", "root", (0, 4, 0), None, [
        ((-4, 1, -4), (8, 6, 10), "SKIN"),
        ((-1, 6, 0), (2, 1, 4), "BONE"),
        ((-2, 0, -7), (3, 4, 3), "ENTRAIL"), ((0, 0, -8), (1, 3, 2), "ENTRAIL"),
        ((-3, 2, -5), (6, 2, 1), "FLESH"),
        ((-5, 3, 2), (1, 2, 3), "BONE"), ((4, 3, 3), (1, 2, 3), "BONE")]),
    ("head", "body", (0, 4, -4), (-22, 0, 4), [
        ((-4, 3, -8), (8, 7, 6), "SKIN"), ((-4, 3, -8), (8, 1, 1), "TEETH"),
        ((-3, 7, -9), (2, 2, 1), "SOCKET"), ((1, 7, -9), (1, 1, 1), "EYE")]),
    ("jaw", "head", (0, 4, -8), (16, 0, 0), [((-3, 0, -8), (6, 3, 5), "SKIN"), ((-3, 1, -8), (6, 1, 1), "TEETH")]),
    ("arm_r", "body", (-4, 4, -3), None, [((-7, 0, -12), (3, 3, 9), "SKIN"), ((-6, 0, -13), (1, 1, 4), "BONE")]),
    ("arm_l", "body", (4, 4, -3), None, [((4, 0, -12), (3, 3, 9), "SKIN2")]),
    ("leg_r", "root", (-3, 2, 5), None, [((-4, 0, 4), (3, 3, 5), "SKIN")]),
    ("leg_l", "root", (3, 2, 5), None, [((1, 0, 4), (3, 3, 5), "SKIN2")]),
]

EMPALE = [
    ("root", None, (0, 0, 0), None, []),
    ("body", "root", (0, 24, 0), (2, 0, 0), [
        ((-4, 12, -2), (8, 12, 4), "GREY"),
        ((-2, 20, 2), (1, 4, 1), "BONE"), ((1, 18, 2), (1, 4, 1), "BONE"), ((-3, 15, 2), (1, 3, 1), "BONE"),
        ((-5, 22, -1), (1, 3, 1), "BONE"), ((4, 22, -1), (1, 3, 1), "BONE"), ((2, 16, -3), (1, 4, 1), "BONE"),
        ((-2, 4, -4), (3, 8, 2), "ENTRAIL"), ((0, 5, -5), (1, 7, 1), "ENTRAIL"), ((-3, 10, -3), (4, 3, 1), "FLESH")]),
    ("head", "body", (0, 24, -1), (0, -3, 3), [
        ((-4, 24, -4), (8, 8, 7), "GREY"), ((-4, 24, -4), (8, 1, 1), "TEETH"),
        ((2, 31, -1), (1, 3, 1), "BONE"), ((-3, 31, -1), (1, 3, 1), "BONE"),
        ((-3, 28, -5), (2, 2, 1), "SOCKET"), ((1, 28, -5), (1, 1, 1), "EYE")]),
    ("jaw", "head", (0, 25, -4), (8, 0, 0), [((-3, 22, -4), (6, 2, 4), "GREY"), ((-3, 23, -4), (6, 1, 1), "TEETH")]),
    ("arm_r", "body", (-4, 23, 0), (-68, 0, 6), [((-8, 11, -2), (4, 12, 4), "GREY"), ((-9, 15, -2), (1, 5, 1), "BONE")]),
    ("arm_l", "body", (4, 23, 0), (-78, 10, -10), [((4, 11, -2), (4, 12, 4), "GREY"), ((3, 14, -2), (1, 4, 1), "BONE")]),
    ("leg_r", "root", (-2, 12, 0), (2, 0, 2), [((-4, 0, -2), (4, 12, 4), "GREY"), ((-5, 4, -2), (1, 4, 1), "BONE")]),
    ("leg_l", "root", (2, 12, 0), (-2, 0, -2), [((0, 0, -2), (4, 12, 4), "GREY")]),
]

DIFFORME = [
    ("root", None, (0, 0, 0), None, []),
    ("body", "root", (0, 24, 0), (3, 0, -7), [
        ((-4, 12, -2), (8, 12, 4), "GREY"),
        ((-4, 20, -3), (3, 3, 1), "FLESH"), ((-5, 22, -1), (1, 3, 2), "BONE"),
        ((-2, 4, -4), (3, 8, 2), "ENTRAIL"), ((0, 5, -5), (1, 7, 1), "ENTRAIL"), ((-3, 10, -3), (4, 3, 1), "FLESH"),
        ((4, 19, -2), (1, 2, 3), "BONE")]),
    ("head", "body", (0, 24, -1), (2, -6, 9), [
        ((-4, 24, -4), (8, 8, 7), "GREY"), ((-4, 24, -4), (8, 1, 1), "TEETH"),
        ((-3, 28, -5), (2, 2, 1), "SOCKET"), ((1, 28, -5), (1, 1, 1), "EYE")]),
    ("jaw", "head", (0, 25, -4), (10, 0, 0), [((-3, 22, -4), (6, 3, 4), "GREY"), ((-3, 23, -4), (6, 1, 1), "TEETH")]),
    ("arm_r", "body", (-4, 23, 0), (-18, 0, 12), [
        ((-13, 6, -5), (9, 15, 9), "GREY"), ((-12, 8, -6), (3, 5, 1), "FLESH"), ((-13, 4, -1), (2, 6, 2), "BONE")]),
    ("arm_l", "body", (4, 23, 0), (-60, 0, -12), [((4, 14, -1), (3, 10, 3), "GREY")]),
    ("leg_r", "root", (-2, 12, 0), (2, 0, 5), [((-4, 0, -2), (4, 12, 4), "GREY"), ((-5, 3, -2), (1, 4, 1), "BONE")]),
    ("leg_l", "root", (2, 12, 0), (-2, 0, -3), [((0, 0, -2), (4, 12, 4), "GREY")]),
]

PENDU = [
    ("root", None, (0, 0, 0), None, []),
    ("body", "root", (0, 24, 0), (10, 0, 0), [
        ((-4, 12, -2), (8, 12, 4), "SKIN"), ((-2, 23, -2), (4, 2, 3), "FLESH"),
        ((-2, 4, -4), (3, 8, 2), "ENTRAIL"), ((0, 5, -5), (1, 7, 1), "ENTRAIL"), ((-3, 6, -4), (1, 6, 1), "ENTRAIL"),
        ((-3, 10, -3), (4, 3, 1), "FLESH"), ((-5, 19, -2), (1, 2, 3), "BONE"), ((4, 20, -2), (1, 2, 3), "BONE")]),
    ("head", "body", (0, 24, -1), (58, 0, 26), [
        ((-4, 24, -4), (8, 8, 7), "SKIN"), ((-4, 24, -4), (8, 1, 1), "TEETH"),
        ((-3, 28, -5), (2, 2, 1), "SOCKET"), ((1, 28, -5), (1, 1, 1), "EYE")]),
    ("jaw", "head", (0, 25, -4), (14, 0, 0), [((-3, 22, -4), (6, 3, 4), "SKIN"), ((-3, 23, -4), (6, 1, 1), "TEETH")]),
    ("arm_r", "body", (-4, 23, 0), (-20, 0, 6), [((-8, 11, -2), (4, 12, 4), "SKIN"), ((-9, 13, -2), (1, 5, 1), "BONE")]),
    ("arm_l", "body", (4, 23, 0), (-16, 0, -6), [((4, 11, -2), (4, 12, 4), "SKIN2")]),
    ("leg_r", "root", (-2, 12, 0), (0, 0, 2), [((-4, 0, -2), (4, 12, 4), "SKIN"), ((-5, 3, -2), (1, 4, 1), "BONE")]),
    ("leg_l", "root", (2, 12, 0), (0, 0, -2), [((0, 0, -2), (4, 12, 4), "SKIN2")]),
]

COLOSSE = [
    ("root", None, (0, 0, 0), None, []),
    ("body", "root", (0, 30, 0), (8, 0, 0), [
        ((-6, 14, -3), (12, 16, 6), "SKIN", 0.5), ((-4, 20, -4), (8, 4, 1), "FLESH"),
        ((-3, 5, -5), (5, 10, 3), "ENTRAIL"), ((1, 6, -6), (2, 8, 2), "ENTRAIL"), ((-5, 7, -5), (2, 7, 2), "ENTRAIL"),
        ((-7, 24, -3), (1, 3, 4), "BONE"), ((6, 25, -3), (1, 3, 4), "BONE"), ((-8, 22, -2), (1, 3, 3), "BONE")]),
    ("head", "body", (0, 30, -1), (2, -4, 4), [
        ((-5, 30, -5), (10, 9, 9), "SKIN"), ((-5, 30, -5), (10, 1, 1), "TEETH"),
        ((-4, 34, -6), (3, 2, 1), "SOCKET"), ((1, 34, -6), (2, 2, 1), "EYE")]),
    ("jaw", "head", (0, 31, -5), (10, 0, 0), [((-4, 27, -5), (8, 3, 5), "SKIN"), ((-4, 28, -5), (8, 1, 1), "TEETH")]),
    ("arm_r", "body", (-6, 29, 0), (-62, 0, 8), [((-12, 13, -3), (6, 16, 6), "SKIN"), ((-13, 17, -3), (1, 6, 2), "BONE")]),
    ("arm_l", "body", (6, 29, 0), (-62, 0, -8), [((6, 13, -3), (6, 16, 6), "SKIN2")]),
    ("leg_r", "root", (-3, 14, 0), (0, 0, 3), [((-6, 0, -3), (6, 14, 6), "SKIN"), ((-7, 4, -3), (1, 5, 1), "BONE")]),
    ("leg_l", "root", (3, 14, 0), (0, 0, -3), [((0, 0, -3), (6, 14, 6), "SKIN2")]),
]

EMACIE = [
    ("root", None, (0, 0, 0), None, []),
    ("body", "root", (0, 24, 0), (4, 0, 0), [
        ((-3, 12, -1), (6, 12, 3), "PALE"),
        ((-3, 19, -2), (6, 1, 1), "BONE"), ((-3, 17, -2), (6, 1, 1), "BONE"), ((-3, 15, -2), (6, 1, 1), "BONE"),
        ((-1, 12, -2), (2, 5, 1), "BONE"),
        ((-2, 4, -3), (2, 8, 1), "ENTRAIL"), ((0, 5, -3), (1, 6, 1), "ENTRAIL"),
        ((-4, 18, -1), (1, 2, 2), "BONE")]),
    ("head", "body", (0, 24, -1), (-6, -4, 3), [
        ((-3, 24, -3), (6, 7, 6), "PALE"), ((-3, 24, -3), (6, 1, 1), "TEETH"),
        ((-2, 27, -4), (2, 2, 1), "SOCKET"), ((0, 27, -4), (2, 2, 1), "SOCKET")]),
    ("jaw", "head", (0, 25, -3), (10, 0, 0), [((-2, 22, -3), (4, 2, 4), "PALE"), ((-2, 23, -3), (4, 1, 1), "TEETH")]),
    ("arm_r", "body", (-3, 23, 0), (-70, 0, 8), [((-5, 11, -1), (2, 12, 2), "PALE"), ((-6, 13, -1), (1, 5, 1), "BONE")]),
    ("arm_l", "body", (3, 23, 0), (-70, 0, -8), [((3, 11, -1), (2, 12, 2), "PALE")]),
    ("leg_r", "root", (-2, 12, 0), (0, 0, 3), [((-3, 0, -1), (3, 12, 3), "PALE"), ((-4, 3, -1), (1, 4, 1), "BONE")]),
    ("leg_l", "root", (2, 12, 0), (0, 0, -3), [((0, 0, -1), (3, 12, 3), "PALE")]),
]

BRULE = [
    ("root", None, (0, 0, 0), None, []),
    ("body", "root", (0, 24, 0), (3, 0, 2), [
        ((-4, 12, -2), (8, 12, 4), "CHAR"), ((-2, 18, -3), (3, 4, 1), "EMBER"), ((1, 14, -3), (2, 3, 1), "EMBER"),
        ((-2, 4, -4), (3, 8, 2), "EMBER"), ((0, 5, -5), (1, 7, 1), "EMBER"), ((-3, 10, -3), (4, 3, 1), "EMBER"),
        ((-5, 19, -2), (1, 2, 3), "BONE"), ((4, 20, -2), (1, 2, 3), "BONE")]),
    ("head", "body", (0, 24, -1), (-4, -5, 5), [
        ((-4, 24, -4), (8, 8, 7), "CHAR"), ((-4, 24, -4), (8, 1, 1), "EMBER"),
        ((-3, 28, -5), (2, 2, 1), "EMBER"), ((1, 28, -5), (1, 1, 1), "EMBER")]),
    ("jaw", "head", (0, 25, -4), (10, 0, 0), [((-3, 22, -4), (6, 3, 4), "CHAR")]),
    ("arm_r", "body", (-4, 23, 0), (-72, 0, 6), [((-8, 11, -2), (4, 12, 4), "CHAR"), ((-9, 13, -2), (1, 5, 1), "BONE")]),
    ("arm_l", "body", (4, 23, 0), (-72, 0, -6), [((4, 11, -2), (4, 12, 4), "CHAR")]),
    ("leg_r", "root", (-2, 12, 0), (0, 0, 2), [((-4, 0, -2), (4, 12, 4), "CHAR"), ((-5, 3, -2), (1, 4, 1), "BONE")]),
    ("leg_l", "root", (2, 12, 0), (0, 0, -2), [((0, 0, -2), (4, 12, 4), "CHAR")]),
]

VARIANTS = {
    "horror_zombie": (PROFANATEUR, (94, 107, 66)),
    "ecorche":       (ECORCHE,     (150, 46, 40)),
    "boursoufle":    (BOURSOUFLE,  (120, 138, 78)),
    "rampant":       (RAMPANT,     (86, 66, 52)),
    "empale":        (EMPALE,      (128, 130, 122)),
    "difforme":      (DIFFORME,    (120, 96, 84)),
    "pendu":         (PENDU,       (100, 112, 80)),
    "colosse":       (COLOSSE,     (70, 92, 55)),
    "emacie":        (EMACIE,      (150, 150, 122)),
    "brule":         (BRULE,       (48, 44, 42)),
}

ANIM_PARAMS = {
    "ecorche":    dict(walk=0.7, idle=1.7, style="fast"),
    "boursoufle": dict(walk=1.4, idle=4.2, style="heavy"),
    "empale":     dict(walk=1.0, idle=3.0, style="stiff"),
    "difforme":   dict(walk=1.2, idle=3.5, style="heavy"),
    "pendu":      dict(walk=1.0, idle=3.2, style="normal"),
    "colosse":    dict(walk=1.6, idle=4.0, style="heavy"),
    "emacie":     dict(walk=0.75, idle=1.9, style="fast"),
    "brule":      dict(walk=1.1, idle=3.0, style="normal"),
}


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
                        "texture_height": tex_h, "visible_bounds_width": 6, "visible_bounds_height": 6,
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


def build_anims(p):
    """Zombie-shuffle set on the shared humanoid rig — pronounced leg/arm swings so movement always reads."""
    wl, il, style = p["walk"], p["idle"], p["style"]
    heavy, stiff, fast = style == "heavy", style == "stiff", style == "fast"
    legamp = 26 if heavy else 38
    armamp = 14 if heavy else 20
    if stiff:
        legs_r = [(0, [-22, 0, 0]), (0.08, [-22, 0, 0]), (0.13, [20, 0, 0]), (0.55, [20, 0, 0]), (0.6, [-22, 0, 0]), (1, [-22, 0, 0])]
        legs_l = [(0, [20, 0, 0]), (0.08, [20, 0, 0]), (0.13, [-22, 0, 0]), (0.55, [-22, 0, 0]), (0.6, [20, 0, 0]), (1, [20, 0, 0])]
    else:
        legs_r = [(0, [-legamp, 0, 0]), (0.5, [legamp, 0, 0]), (1, [-legamp, 0, 0])]
        legs_l = [(0, [legamp, 0, 0]), (0.5, [-legamp, 0, 0]), (1, [legamp, 0, 0])]

    # RICH idle — never static: breathing + sway, wandering head loll, working jaw, arm tremor, weight shift.
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
    if stiff:  # robotic: sharp snap-and-hold instead of smooth wander
        idle_bones["head"] = {"rotation": kf([(0, [0, 0, 0]), (0.3, [0, 0, 0]), (0.33, [0, -18, 0]), (0.6, [0, -18, 0]),
                                              (0.63, [10, 6, 0]), (0.9, [10, 6, 0]), (0.93, [0, 0, 0]), (1, [0, 0, 0])], il)}
        idle_bones["arm_r"] = {"rotation": kf([(0, [0, 0, 0]), (0.45, [0, 0, 0]), (0.48, [12, 0, -5]), (1, [12, 0, -5])], il)}
    idle = {"loop": True, "animation_length": il, "bones": idle_bones}

    walk = {"loop": True, "animation_length": wl, "bones": {
        "leg_r": {"rotation": kf(legs_r, wl)},
        "leg_l": {"rotation": kf(legs_l, wl)},
        "body": {"rotation": kf([(0, [3, 0, 4]), (0.5, [3, 0, -4]), (1, [3, 0, 4])], wl),
                 "position": kf([(0, [0, 0, 0]), (0.25, [0, -1.5, 0]), (0.5, [0, 0, 0]), (0.75, [0, -1.5, 0]), (1, [0, 0, 0])], wl)},
        "arm_r": {"rotation": kf([(0, [armamp, 0, 0]), (0.5, [-armamp, 0, 0]), (1, [armamp, 0, 0])], wl)},
        "arm_l": {"rotation": kf([(0, [-armamp, 0, 0]), (0.5, [armamp, 0, 0]), (1, [-armamp, 0, 0])], wl)},
        "head": {"rotation": kf([(0, [0, 0, 0]), (0.5, [6, 0, 3]), (1, [0, 0, 0])], wl)},
    }}

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
    return {"format_version": "1.8.0", "animations": {"idle": idle, "walk": walk, "attack": attack, "spasm": spasm, "death": death}}


def build_crawler_anims():
    """Drag animation for the grounded rampant crawler — arms pull, torso lurches forward, head bobs."""
    return {"format_version": "1.8.0", "animations": {
        "idle": {"loop": True, "animation_length": 3.0, "bones": {
            "head": {"rotation": {"0.0": [0, 0, 0], "0.4": [-8, -6, 4], "0.9": [6, 4, -5], "1.5": [-5, 7, 3], "2.1": [8, -4, -4], "2.6": [-4, -3, 3], "3.0": [0, 0, 0]}},
            "jaw": {"rotation": {"0.0": [2, 0, 0], "0.5": [20, 0, 0], "0.9": [6, 0, 0], "1.5": [24, 0, 0], "2.0": [5, 0, 0], "2.5": [16, 0, 0], "3.0": [2, 0, 0]}},
            "arm_r": {"rotation": {"0.0": [0, 0, 0], "0.5": [-10, 0, -3], "1.0": [4, 0, 2], "1.7": [-7, 0, -2], "2.4": [3, 0, 0], "3.0": [0, 0, 0]}},
            "arm_l": {"rotation": {"0.0": [0, 0, 0], "0.7": [8, 0, 3], "1.4": [-6, 0, -2], "2.1": [7, 0, 2], "3.0": [0, 0, 0]}},
            "body": {"rotation": {"0.0": [0, 0, 0], "0.75": [4, 0, 3], "1.5": [1, 0, -2], "2.25": [4, 0, 3], "3.0": [0, 0, 0]}},
        }},
        "walk": {"loop": True, "animation_length": 1.1, "bones": {
            "arm_r": {"rotation": {"0.0": [-6, 0, 0], "0.3": [-42, 0, 0], "0.6": [2, 0, 0], "1.1": [-6, 0, 0]}},
            "arm_l": {"rotation": {"0.0": [2, 0, 0], "0.55": [-42, 0, 0], "0.85": [2, 0, 0], "1.1": [2, 0, 0]}},
            "body": {"rotation": {"0.0": [0, 0, 0], "0.3": [-7, 0, 0], "0.6": [4, 0, 0], "0.9": [-7, 0, 0], "1.1": [0, 0, 0]},
                     "position": {"0.0": [0, 0, 0], "0.3": [0, 0, 2], "0.6": [0, 0.5, 0], "0.9": [0, 0, 2], "1.1": [0, 0, 0]}},
            "leg_r": {"rotation": {"0.0": [0, 0, 0], "0.55": [16, 0, 6], "1.1": [0, 0, 0]}},
            "leg_l": {"rotation": {"0.0": [16, 0, 0], "0.55": [0, 0, -6], "1.1": [16, 0, 0]}},
            "head": {"rotation": {"0.0": [0, 0, 0], "0.3": [8, 0, 0], "0.6": [-4, 0, 3], "1.1": [0, 0, 0]}},
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
    }}


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(obj, f, indent=2)


def main():
    for i, (ident, (bones, egg_rgb)) in enumerate(VARIANTS.items()):
        lo = min((c[0][1] for b in bones for c in b[4]), default=0)
        if lo < -2:
            raise SystemExit(f"{ident}: lowest cube y={lo} (would float/clip badly); keep it on the ground")
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
        egg_icon(egg_rgb, os.path.join(ASSETS, "textures", "item", egg + ".png"), 100 + i)
        write_json(os.path.join(ASSETS, "items", egg + ".json"),
                   {"model": {"type": "minecraft:model", "model": "lethalbreed:item/" + egg}})
        write_json(os.path.join(ASSETS, "models", "item", egg + ".json"),
                   {"parent": "minecraft:item/generated", "textures": {"layer0": "lethalbreed:item/" + egg}})
        anim_path = os.path.join(ASSETS, "geckolib", "animations", "entity", ident + ".animation.json")
        if ident == "rampant":
            write_json(anim_path, build_crawler_anims())
        elif ident in ANIM_PARAMS:
            write_json(anim_path, build_anims(ANIM_PARAMS[ident]))
        print(f"{ident:14s} cubes={len(flat):2d} atlas=128x{tex_h}  floor_ok(y>={lo})")


if __name__ == "__main__":
    main()
