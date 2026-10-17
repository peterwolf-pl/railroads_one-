#!/usr/bin/env python3
"""Generate vanilla-styled top-down rail textures and matching flat models."""

from __future__ import annotations

import json
import math
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
BLOCK_TEX = ROOT / "src/main/resources/assets/peterwolfs_railroads_one/textures/block"
ITEM_TEX = ROOT / "src/main/resources/assets/peterwolfs_railroads_one/textures/item"
BLOCK_MODEL = ROOT / "src/main/resources/assets/peterwolfs_railroads_one/models/block"
VANILLA_RAIL = Path("/tmp/mc-rail-tex/assets/minecraft/textures/block/rail.png")

IRON_LIGHT = (156, 156, 156, 255)
IRON_MID = (171, 172, 171, 255)
IRON_DARK = (104, 104, 104, 255)
IRON_MOSS = (114, 121, 110, 255)
WOOD_HI = (166, 130, 77, 255)
WOOD_MID = (150, 116, 65, 255)
WOOD_DARK = (126, 98, 55, 255)
WOOD_SHADOW = (103, 80, 44, 255)
WOOD_EDGE = (81, 61, 36, 255)
GOLD_HI = (243, 193, 42, 255)
GOLD_MID = (220, 174, 41, 255)
GOLD_DARK = (201, 136, 29, 255)
CLEAR = (0, 0, 0, 0)

# Vanilla NS rail lives in columns 2-3 and 12-13 of a 16px tile.
RAIL_HALF = 5.0  # centerline to each rail's inner edge
RAIL_WIDTH = 2.0
TIE_HALF = 7.0
TIE_WIDTH = 2.0
TIE_SPACING = 4.0


def new_canvas(w: int, h: int) -> Image.Image:
	return Image.new("RGBA", (w, h), CLEAR)


def set_px(px, w, h, x, y, color):
	ix, iy = int(round(x)), int(round(y))
	if 0 <= ix < w and 0 <= iy < h:
		px[ix, iy] = color


def stamp_disk(px, w, h, x, y, radius, color):
	r = int(math.ceil(radius))
	cx, cy = int(round(x)), int(round(y))
	rr = radius * radius
	for dy in range(-r, r + 1):
		for dx in range(-r, r + 1):
			if dx * dx + dy * dy <= rr:
				set_px(px, w, h, cx + dx, cy + dy, color)


def sample_path(fn, steps: int):
	pts = []
	for i in range(steps + 1):
		t = i / steps
		x, y, tx, ty = fn(t)
		length = math.hypot(tx, ty) or 1.0
		pts.append((x, y, tx / length, ty / length))
	return pts


def draw_ties(px, w, h, pts, gold: bool):
	hi, mid, dark = (GOLD_HI, GOLD_MID, GOLD_DARK) if gold else (WOOD_HI, WOOD_MID, WOOD_EDGE)
	accum = 0.0
	next_tie = 1.0
	for i in range(1, len(pts)):
		x0, y0, _, _ = pts[i - 1]
		x1, y1, tx, ty = pts[i]
		accum += math.hypot(x1 - x0, y1 - y0)
		if accum < next_tie:
			continue
		next_tie += TIE_SPACING
		nx, ny = -ty, tx
		# 2-pixel-wide tie across the gauge
		for s in range(-int(TIE_HALF), int(TIE_HALF) + 1):
			for woff in (-0.5, 0.5):
				color = hi if s % 3 == 0 else (mid if s % 3 == 1 else dark)
				stamp_disk(px, w, h, x1 + nx * s + tx * woff, y1 + ny * s + ty * woff, 0.7, color)


def draw_rails(px, w, h, pts, gold: bool):
	outer, inner = ((GOLD_HI, GOLD_DARK) if gold else (IRON_MID, IRON_DARK))
	for x, y, tx, ty in pts:
		nx, ny = -ty, tx
		for sign, colors in ((-1, (outer, inner)), (1, (inner, outer))):
			for i, color in enumerate(colors):
				ox = (RAIL_HALF + i) * sign
				stamp_disk(px, w, h, x + nx * ox, y + ny * ox, 0.85, color)


def draw_track(img: Image.Image, fn, gold: bool = False, steps: int = 400):
	px = img.load()
	w, h = img.size
	pts = sample_path(fn, steps)
	draw_ties(px, w, h, pts, gold)
	draw_rails(px, w, h, pts, gold)


def blit_vanilla_ns(dest: Image.Image, tile_x: int, tile_y: int, vanilla: Image.Image):
	dest.paste(vanilla, (tile_x * 16, tile_y * 16), vanilla)


def blit_vanilla_ew(dest: Image.Image, tile_x: int, tile_y: int, vanilla: Image.Image):
	rot = vanilla.rotate(-90, expand=False)
	dest.paste(rot, (tile_x * 16, tile_y * 16), rot)


def quarter_circle(size_px: int, radius: float, cx: float, cy: float, a0: float, a1: float):
	def fn(t: float):
		a = a0 + (a1 - a0) * t
		x = cx + math.cos(a) * radius
		y = cy + math.sin(a) * radius
		# tangent of increasing angle
		tx = -math.sin(a) * math.copysign(1.0, a1 - a0)
		ty = math.cos(a) * math.copysign(1.0, a1 - a0)
		return x, y, tx, ty

	return fn


def save(img: Image.Image, path: Path):
	path.parent.mkdir(parents=True, exist_ok=True)
	img.save(path)


def split_2x2(img: Image.Image, prefix: str):
	names = {
		"nw": (0, 0),
		"ne": (16, 0),
		"sw": (0, 16),
		"se": (16, 16),
	}
	for suffix, (x, y) in names.items():
		save(img.crop((x, y, x + 16, y + 16)), BLOCK_TEX / f"{prefix}_{suffix}.png")


def write_json(path: Path, data):
	path.write_text(json.dumps(data, indent=2) + "\n")


def template_rail_flat():
	return {
		"ambientocclusion": False,
		"textures": {"particle": "#rail"},
		"elements": [
			{
				"from": [0, 1, 0],
				"to": [16, 1, 16],
				"faces": {
					"down": {"uv": [0, 16, 16, 0], "texture": "#rail"},
					"up": {"uv": [0, 0, 16, 16], "texture": "#rail"},
				},
			}
		],
	}


def child_rail(texture: str):
	return {
		"parent": "peterwolfs_railroads_one:block/template_rail_flat",
		"textures": {"rail": f"peterwolfs_railroads_one:block/{texture}"},
	}


def uv_rail(texture: str, u0: float, v0: float, u1: float, v1: float):
	return {
		"ambientocclusion": False,
		"textures": {
			"particle": f"peterwolfs_railroads_one:block/{texture}",
			"rail": f"peterwolfs_railroads_one:block/{texture}",
		},
		"elements": [
			{
				"from": [0, 1, 0],
				"to": [16, 1, 16],
				"faces": {
					"down": {"uv": [u0, v1, u1, v0], "texture": "#rail"},
					"up": {"uv": [u0, v0, u1, v1], "texture": "#rail"},
				},
			}
		],
	}


def make_crossing(vanilla: Image.Image) -> Image.Image:
	img = vanilla.copy()
	ew = vanilla.rotate(-90, expand=False)
	img.alpha_composite(ew)
	return img


def make_left_curve(blocks: int) -> Image.Image:
	px = blocks * 16
	img = new_canvas(px, px)
	# SW tile center in image space (y grows south / down).
	cx, cy = 8.0, px - 8.0
	radius = (blocks - 1) * 16.0
	start_x, start_y = cx + radius, cy
	end_x, end_y = cx, cy - radius

	def fn(t: float):
		a = (-math.pi / 2.0) * t
		x = cx + math.cos(a) * radius
		y = cy + math.sin(a) * radius
		tx = math.sin(a)
		ty = -math.cos(a)
		return x, y, tx, ty

	draw_track(img, fn)

	def entry(t: float):
		x = start_x
		y = (px - 0.5) - t * (px - 0.5 - start_y)
		return x, y, 0.0, -1.0

	def exit_path(t: float):
		x = end_x - t * (end_x + 0.5)
		y = end_y
		return x, y, -1.0, 0.0

	draw_track(img, entry, steps=80)
	draw_track(img, exit_path, steps=80)
	return img


def make_switch_left(gold_siding: bool) -> Image.Image:
	img = new_canvas(32, 32)
	# Main: east column, full NS. Canvas y down = south.
	def main(t: float):
		x = 24.0
		y = 31.0 - t * 31.0
		return x, y, 0.0, -1.0

	draw_track(img, main, gold=False)

	def siding(t: float):
		# smoothstep west while running north from the south edge of the east column
		s = t * t * (3.0 - 2.0 * t)
		x = 24.0 - 16.0 * s
		y = 31.0 - 23.0 * t
		ds = 6.0 * t * (1.0 - t)
		dx = -16.0 * ds
		dy = -23.0
		return x, y, dx, dy

	draw_track(img, siding, gold=gold_siding)

	def siding_exit(t: float):
		x = 8.0
		y = 8.0 - t * 8.0
		return x, y, 0.0, -1.0

	draw_track(img, siding_exit, gold=gold_siding, steps=80)
	return img


def make_item_from(img: Image.Image, size: int = 16) -> Image.Image:
	return img.resize((size, size), Image.Resampling.NEAREST)


def write_models():
	write_json(BLOCK_MODEL / "template_rail_flat.json", template_rail_flat())

	simple = {
		"crossing_rail": "crossing_rail",
		"rail_curve_start": "rail_curve_start",
		"rail_curve_end": "rail_curve_end",
		"rail_curve_left": "rail_curve_left",
		"rail_curve_right": "rail_curve_right",
		"rail_curve_empty": None,
	}
	for name, tex in simple.items():
		if tex is None:
			write_json(
				BLOCK_MODEL / f"{name}.json",
				{"textures": {"particle": "peterwolfs_railroads_one:block/rail_curve_start"}, "elements": []},
			)
		else:
			write_json(BLOCK_MODEL / f"{name}.json", child_rail(tex))

	for turn in ("left", "right"):
		for corner in ("nw", "ne", "sw", "se"):
			write_json(
				BLOCK_MODEL / f"rail_curve_{turn}_2x2_{corner}.json",
				child_rail(f"rail_curve_{turn}_2x2_{corner}"),
			)
			for powered in ("", "_powered"):
				write_json(
					BLOCK_MODEL / f"parallel_siding_switch_{turn}_{corner}{powered}.json",
					child_rail(f"parallel_siding_switch_{turn}_{corner}{powered}"),
				)

	third = 16.0 / 3.0
	uvs = {
		"nw": (0.0, 0.0, third, third),
		"n": (third, 0.0, 2 * third, third),
		"ne": (2 * third, 0.0, 16.0, third),
		"w": (0.0, third, third, 2 * third),
		"center": (third, third, 2 * third, 2 * third),
		"e": (2 * third, third, 16.0, 2 * third),
		"sw": (0.0, 2 * third, third, 16.0),
		"s": (third, 2 * third, 2 * third, 16.0),
		"se": (2 * third, 2 * third, 16.0, 16.0),
	}
	for turn in ("left", "right"):
		tex = f"rail_curve_{turn}_3x3"
		for name, box in uvs.items():
			write_json(BLOCK_MODEL / f"rail_curve_{turn}_3x3_{name}.json", uv_rail(tex, *box))


def main():
	vanilla = Image.open(VANILLA_RAIL).convert("RGBA")

	# Straight leftovers and crossing.
	save(vanilla.copy(), BLOCK_TEX / "rail_curve_start.png")
	save(vanilla.copy(), BLOCK_TEX / "rail_curve_end.png")
	save(vanilla.copy(), BLOCK_TEX / "rail_curve_marker.png")
	save(make_crossing(vanilla), BLOCK_TEX / "crossing_rail.png")
	save(vanilla.rotate(-90, expand=False), BLOCK_TEX / "rail_curve_left.png")
	save(vanilla.rotate(90, expand=False), BLOCK_TEX / "rail_curve_right.png")

	left_2 = make_left_curve(2)
	right_2 = left_2.transpose(Image.Transpose.FLIP_LEFT_RIGHT)
	split_2x2(left_2, "rail_curve_left_2x2")
	split_2x2(right_2, "rail_curve_right_2x2")

	left_3 = make_left_curve(3)
	right_3 = left_3.transpose(Image.Transpose.FLIP_LEFT_RIGHT)
	save(left_3, BLOCK_TEX / "rail_curve_left_3x3.png")
	save(right_3, BLOCK_TEX / "rail_curve_right_3x3.png")

	switch_off = make_switch_left(False)
	switch_on = make_switch_left(True)
	split_2x2(switch_off, "parallel_siding_switch_left")
	split_2x2(switch_off.transpose(Image.Transpose.FLIP_LEFT_RIGHT), "parallel_siding_switch_right")
	# powered variants
	names = ("nw", "ne", "sw", "se")
	coords = ((0, 0), (16, 0), (0, 16), (16, 16))
	right_on = switch_on.transpose(Image.Transpose.FLIP_LEFT_RIGHT)
	for name, (x, y) in zip(names, coords):
		save(switch_on.crop((x, y, x + 16, y + 16)), BLOCK_TEX / f"parallel_siding_switch_left_{name}_powered.png")
		save(right_on.crop((x, y, x + 16, y + 16)), BLOCK_TEX / f"parallel_siding_switch_right_{name}_powered.png")

	save(make_item_from(left_3), ITEM_TEX / "gentle_rail_curve_left.png")
	save(make_item_from(right_3), ITEM_TEX / "gentle_rail_curve_right.png")
	save(make_item_from(left_2), ITEM_TEX / "gentle_rail_curve_left_2x2.png")
	save(make_item_from(right_2), ITEM_TEX / "gentle_rail_curve_right_2x2.png")
	save(make_item_from(switch_off), ITEM_TEX / "parallel_siding_switch_left.png")
	save(
		make_item_from(switch_off.transpose(Image.Transpose.FLIP_LEFT_RIGHT)),
		ITEM_TEX / "parallel_siding_switch_right.png",
	)

	write_models()
	print("generated vanilla-style rail textures and models")


if __name__ == "__main__":
	main()
