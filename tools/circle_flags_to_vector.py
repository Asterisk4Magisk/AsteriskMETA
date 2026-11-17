#!/usr/bin/env python3
# Copyright 2026, AsteriskMETA contributors
# SPDX-License-Identifier: GPL-3.0
"""Convert HatScripts/circle-flags SVGs into Android VectorDrawables.

The circle-flags set is a very small SVG dialect: one clip mask holding a
circle, then flat-filled <path>, <circle> and <rect> shapes. That maps onto a
VectorDrawable directly, so the flags can be bundled as native resources
instead of pulling in an SVG renderer.
"""
import os
import re
import sys
import xml.etree.ElementTree as ET

SVG_NS = "{http://www.w3.org/2000/svg}"
VIEWPORT = 512


def circle_path(cx, cy, r):
    return f"M{cx:g},{cy - r:g}a{r:g},{r:g} 0 1,0 0,{2 * r:g}a{r:g},{r:g} 0 1,0 0,{-2 * r:g}z"


def ellipse_path(cx, cy, rx, ry):
    return (
        f"M{cx - rx:g},{cy:g}"
        f"a{rx:g},{ry:g} 0 1,0 {2 * rx:g},0"
        f"a{rx:g},{ry:g} 0 1,0 {-2 * rx:g},0z"
    )


def rect_path(x, y, w, h, rx, ry):
    if rx <= 0 and ry <= 0:
        return f"M{x:g},{y:g}h{w:g}v{h:g}h{-w:g}z"
    rx = rx if rx > 0 else ry
    ry = ry if ry > 0 else rx
    rx = min(rx, w / 2)
    ry = min(ry, h / 2)
    return (
        f"M{x + rx:g},{y:g}"
        f"H{x + w - rx:g}"
        f"A{rx:g},{ry:g} 0 0 1 {x + w:g},{y + ry:g}"
        f"V{y + h - ry:g}"
        f"A{rx:g},{ry:g} 0 0 1 {x + w - rx:g},{y + h:g}"
        f"H{x + rx:g}"
        f"A{rx:g},{ry:g} 0 0 1 {x:g},{y + h - ry:g}"
        f"V{y + ry:g}"
        f"A{rx:g},{ry:g} 0 0 1 {x + rx:g},{y:g}Z"
    )


def num(value, default=0.0):
    if value is None:
        return default
    return float(re.sub(r"[^0-9.\-]", "", str(value)) or default)


def resolve_alias(path, flags_dir, depth=0):
    """An alias flag file holds nothing but the name of the file it points at."""
    if depth > 8:
        raise RuntimeError("alias chain too deep")
    text = open(path, encoding="utf-8").read().strip()
    if re.fullmatch(r"[\w./\-]+\.svg", text):
        target = os.path.join(flags_dir, text)
        if not os.path.isfile(target):
            raise RuntimeError(f"alias target missing: {text}")
        resolved, _ = resolve_alias(target, flags_dir, depth + 1)
        return resolved, text
    return text, None


def convert(code, svg_text):
    root = ET.fromstring(svg_text.encode("utf-8"))
    if not root.tag.endswith("svg"):
        raise RuntimeError("not an svg document")

    shapes = []
    clip = None

    # The mask is always a single circle with the same radius as the flag.
    masks = root.findall(f"{SVG_NS}mask")
    for mask in masks:
        for circle in mask.findall(f"{SVG_NS}circle"):
            clip = circle_path(
                num(circle.get("cx")),
                num(circle.get("cy")),
                num(circle.get("r")),
            )
            break

    def walk(element, inherited_fill):
        for child in list(element):
            tag = child.tag.replace(SVG_NS, "")
            fill = child.get("fill") or inherited_fill
            if tag == "g":
                walk(child, fill)
            elif tag == "path":
                data = (child.get("d") or "").strip()
                if not data:
                    continue
                if not fill or fill == "none":
                    raise RuntimeError("path without a fill")
                shapes.append((fill, data))
            elif tag == "circle":
                if not fill or fill == "none":
                    raise RuntimeError("circle without a fill")
                shapes.append((
                    fill,
                    circle_path(
                        num(child.get("cx")),
                        num(child.get("cy")),
                        num(child.get("r")),
                    ),
                ))
            elif tag == "ellipse":
                if not fill or fill == "none":
                    raise RuntimeError("ellipse without a fill")
                shapes.append((
                    fill,
                    ellipse_path(
                        num(child.get("cx")),
                        num(child.get("cy")),
                        num(child.get("rx")),
                        num(child.get("ry")),
                    ),
                ))
            elif tag == "rect":
                if not fill or fill == "none":
                    raise RuntimeError("rect without a fill")
                shapes.append((
                    fill,
                    rect_path(
                        num(child.get("x")),
                        num(child.get("y")),
                        num(child.get("width")),
                        num(child.get("height")),
                        num(child.get("rx")),
                        num(child.get("ry")),
                    ),
                ))
            elif tag in ("mask", "defs", "title", "desc"):
                continue
            else:
                raise RuntimeError(f"unsupported element <{tag}>")

    walk(root, None)
    if not shapes:
        raise RuntimeError("no shapes")
    return clip, shapes


def to_vector_drawable(clip, shapes):
    lines = [
        '<?xml version="1.0" encoding="utf-8"?>',
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
        '    android:width="24dp"',
        '    android:height="24dp"',
        f'    android:viewportWidth="{VIEWPORT}"',
        f'    android:viewportHeight="{VIEWPORT}">',
        '    <group>',
    ]
    if clip:
        lines.append(f'        <clip-path android:pathData="{clip}" />')
    for fill, data in shapes:
        lines.append(
            f'        <path android:fillColor="{fill}" android:pathData="{data}" />'
        )
    lines += ['    </group>', '</vector>', '']
    return "\n".join(lines)


def main():
    flags_dir = sys.argv[1]
    out_dir = sys.argv[2]
    os.makedirs(out_dir, exist_ok=True)

    codes = sorted(
        f[:-4] for f in os.listdir(flags_dir) if re.fullmatch(r"[a-z]{2}\.svg", f)
    )
    failures = []
    written = []
    aliased = []
    for code in codes:
        path = os.path.join(flags_dir, f"{code}.svg")
        try:
            text, target = resolve_alias(path, flags_dir)
            if target:
                aliased.append((code, target))
            clip, shapes = convert(code, text)
            xml = to_vector_drawable(clip, shapes)
            with open(os.path.join(out_dir, f"flag_{code}.xml"), "w", encoding="utf-8", newline="\n") as handle:
                handle.write(xml)
            written.append(code)
        except Exception as error:  # noqa: BLE001 - report every failure together
            failures.append((code, str(error)))

    print(f"written: {len(written)}")
    print(f"aliases: {len(aliased)} -> {aliased}")
    if failures:
        print(f"FAILED: {len(failures)}")
        for code, reason in failures:
            print(f"  {code}: {reason}")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
