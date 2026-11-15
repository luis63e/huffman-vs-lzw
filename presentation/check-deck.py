#!/usr/bin/env python3
"""Structural checks for the slide deck. Run from anywhere: python3 presentation/check-deck.py"""
import json
import re
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
OUTER = (HERE / "index.html").read_text(encoding="utf-8")
INNER = (HERE / "composition" / "index.html").read_text(encoding="utf-8")
SPEAKERS = 5
SLIDES_PER_SPEAKER = 2
errors = []


def check(condition, message):
    if not condition:
        errors.append(message)


def config(html, name):
    blocks = re.findall(r'<script type="application/hyperframes-slideshow\+json">(.*?)</script>', html, re.S)
    check(len(blocks) == 1, f"{name}: expected one slideshow JSON block, found {len(blocks)}")
    return json.loads(blocks[0]) if blocks else {}


outer, inner = config(OUTER, "index.html"), config(INNER, "composition/index.html")
check(outer == inner, "the slideshow JSON in index.html and composition/index.html differ")

slides = inner.get("slides", [])
backup = [s for seq in inner.get("slideSequences", []) for s in seq.get("slides", [])]
check(len(slides) == SPEAKERS * SLIDES_PER_SPEAKER,
      f"expected {SPEAKERS * SLIDES_PER_SPEAKER} main slides, found {len(slides)}")

scenes = {}
for attrs in re.findall(r"<section\b([^>]*)>", INNER):
    get = lambda key: (re.search(rf'{key}="([^"]*)"', attrs) or [None, None])[1]
    scenes[get("data-composition-id")] = {
        "start": float(get("data-start")), "duration": float(get("data-duration")), "part": get("data-part")}

ordered = [s["sceneId"] for s in slides + backup]
check(sorted(ordered) == sorted(scenes), f"slides {ordered} do not match scenes {sorted(scenes)}")
starts = [scenes[i]["start"] for i in ordered if i in scenes]
check(starts == sorted(starts) and len(set(starts)) == len(starts), "scene start times must be unique and in slide order")
for a, b in zip(ordered, ordered[1:]):
    if a in scenes and b in scenes:
        check(scenes[a]["start"] + scenes[a]["duration"] <= scenes[b]["start"], f"scenes {a} and {b} overlap")

end = max((s["start"] + s["duration"] for s in scenes.values()), default=0)
root = re.search(r'id="root"[^>]*data-duration="([\d.]+)"', INNER)
total = re.search(r"var TOTAL = ([\d.]+);", INNER)
check(bool(root) and float(root[1]) == end, f"root data-duration must be {end}")
check(bool(total) and float(total[1]) == end, f"TOTAL must be {end}")

for index, slide in enumerate(slides):
    expected = str(index // SLIDES_PER_SPEAKER + 1)
    scene = scenes.get(slide["sceneId"], {})
    check(scene.get("part") == expected, f"{slide['sceneId']}: data-part must be {expected}")
    check(slide.get("notes", "").startswith(f"Expositor {expected} "), f"{slide['sceneId']}: notes must name Expositor {expected}")
    check("fragments" not in slide, f"{slide['sceneId']}: fragments make short talks need extra key presses")

check("data-frag" not in INNER, "composition must not contain data-frag reveals")
sequence_ids = {seq["id"] for seq in inner.get("slideSequences", [])}
for slide in slides:
    for spot in slide.get("hotspots", []):
        check(spot.get("target") in sequence_ids, f"hotspot {spot.get('id')} targets an unknown sequence")
        region = spot.get("region", {})
        check(all(0 <= region.get(k, -1) <= 100 for k in "xywh"), f"hotspot {spot.get('id')} region is out of range")

for name, html in (("index.html", OUTER), ("composition/index.html", INNER)):
    check(not re.search(r'(src|href)="(https?:)?//', html), f"{name}: loads a remote resource")

if errors:
    print("\n".join(f"FAIL: {e}" for e in errors))
    sys.exit(1)
print(f"OK: {len(slides)} slides for {SPEAKERS} speakers, {len(backup)} backup slide(s), configs in sync")
