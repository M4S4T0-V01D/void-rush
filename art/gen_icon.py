#!/usr/bin/env python3
"""Void Rush logo: one geometry, written as the SVG master and the Android icon layers, once per theme.

A runner in a flight suit blasting away from a void rift on a jetpack: the VOID family emblem (two galaxy arms
spiralling out of a white-hot spark) is the rift behind, the jet flame trails back into it, and void shards glint
ahead. Themes come from art/themes.json (the VOID desktop themes, shared with VOID Portal).

Run from the project root:  python3 art/gen_icon.py
"""
import json
import math
import os
import re

CX, CY = 40.0, 62.0  # the rift (the emblem), behind and below the runner
# The runner, leaning into the climb: helmet, torso, jetpack, a reaching arm and two trailing legs.
HELMET = (70.0, 34.0, 8.5)
TORSO = "M62,40 L74,43.5 L68.5,58 L57.5,55 Z"
PACK = "M53.5,38.5 Q54.5,36.5 56.5,37 L62,38.5 Q64,39 63.5,41 L58.5,56 Q57.5,58 55.5,57.5 L50.5,56 Q48.5,55.5 49,53.5 Z"
ARM = "M69,45 L80.5,49 Q82.5,50 81.5,52 Q80.5,53.5 78.5,53 L67,49.5 Z"
LEGS = ["M58,54 L64.5,56 L55.5,70 Q54.5,72 52.5,71 Q50.5,70 51.5,68 Z", "M63.5,56 L69,58 L67,72.5 Q66.5,74.5 64.5,74.3 Q62.5,74 62.8,72 Z"]
VISOR = "M71.5,28.5 Q78.5,29.5 78.3,34.5 Q77.5,38 72,37.3 Q73.5,33 71.5,28.5 Z"
GLINT = "M73.5,30.5 Q76.5,31 77,33 Q75.5,32.2 73.8,32.2 Z"
STRIPE = "M60.5,47 L72,50.5 L71.2,52.7 L59.7,49.2 Z"
FLAME = "M50,55.5 L57,57.5 C54,65 47,74 36,82 C41,73 46,64 50,55.5 Z"
FLAME_CORE = "M51.5,56.5 L55,57.5 C52.5,63 48,69 42,74 C45.5,68 49,62 51.5,56.5 Z"
SHARDS = [(86, 42, 3.4), (91, 55, 2.6), (84, 67, 3.0)]  # x, y, half-height
THETA_MAX = 1.7 * math.pi
ARM_K = 0.6  # arm size relative to VOID Portal's
R0, R_GROW = 2.5 * ARM_K, 36.0 * ARM_K
S = 4.6 / 9.5 * ARM_K / 0.4  # spark scale


def shard(x, y, h):
    w = h * 0.62
    return f"M{x:g},{y - h:g} L{x + w:g},{y:g} L{x:g},{y + h:g} L{x - w:g},{y:g} Z"


def suit_paths():
    return [PACK] + LEGS + [TORSO, ARM]


def arm_points(phase, n=64, length=1.0, width_scale=1.0, t0=0.0):
    """Outline of one tapered arm (or the part t0..length of it): thin at the centre, widest early, a fine tail."""
    pts = []
    for i in range(n + 1):
        t = t0 + i / n * (length - t0)
        th = t * THETA_MAX
        r = R0 + R_GROW * t ** 1.25
        pts.append((CX + r * math.cos(th + phase), CY + r * math.sin(th + phase), t))
    left, right = [], []
    for i, (x, y, t) in enumerate(pts):
        x2, y2, _ = pts[min(i + 1, n)] if i < n else pts[i]
        x1, y1, _ = pts[max(i - 1, 0)]
        dx, dy = x2 - x1, y2 - y1
        d = math.hypot(dx, dy) or 1
        nx, ny = -dy / d, dx / d
        w = (0.3 + 2.5 * math.sin(math.pi * min(t, 0.999) ** 0.55)) * width_scale * 0.6 * ARM_K / 0.4 / 2
        left.append((x + nx * w, y + ny * w))
        right.append((x - nx * w, y - ny * w))
    poly = left + right[::-1]
    return "M" + " L".join(f"{x:.2f},{y:.2f}" for x, y in poly) + " Z", pts[-1][:2]


def circle(cx, cy, r):
    return f"M{cx - r:g},{cy:g} a{r:g},{r:g} 0 1,0 {2 * r:g},0 a{r:g},{r:g} 0 1,0 {-2 * r:g},0"


def ellipse(cx, cy, rx, ry):
    return f"M{cx - rx:g},{cy:g} a{rx:g},{ry:g} 0 1,0 {2 * rx:g},0 a{rx:g},{ry:g} 0 1,0 {-2 * rx:g},0"


def capsule(x, y0, y1, r):
    return f"M{x - r:g},{y0:g} a{r:g},{r:g} 0 0,1 {2 * r:g},0 L{x + r:g},{y1:g} a{r:g},{r:g} 0 0,1 {-2 * r:g},0 Z"


def sparkle(cx, cy, r):
    """A four-pointed glint."""
    k = r * 0.28
    return (f"M{cx:.2f},{cy - r:.2f} Q{cx + k:.2f},{cy - k:.2f} {cx + r:.2f},{cy:.2f} Q{cx + k:.2f},{cy + k:.2f} {cx:.2f},{cy + r:.2f} "
            f"Q{cx - k:.2f},{cy + k:.2f} {cx - r:.2f},{cy:.2f} Q{cx - k:.2f},{cy - k:.2f} {cx:.2f},{cy - r:.2f} Z")


SPARK = sparkle(CX, CY, 9.5 * S)
STARS = [(20, 24, 0.9, 0.8), (88, 22, 0.7, 0.6), (96, 80, 1.0, 0.7), (15, 88, 0.7, 0.5), (74, 96, 0.6, 0.6), (50, 14, 0.5, 0.5), (34, 98, 0.5, 0.45)]
PHASES = [-math.pi / 2, math.pi / 2]
SEGMENTS = 14
SUIT_GRAD = (76, 30, 52, 72)  # lit shoulder to shaded legs


def mix(a, b, f):
    pa = [int(a[i:i + 2], 16) for i in (1, 3, 5)]
    pb = [int(b[i:i + 2], 16) for i in (1, 3, 5)]
    return "#" + "".join(f"{round(x + (y - x) * f):02X}" for x, y in zip(pa, pb))


def argb(hex_rgb, a):
    return f"#{int(a * 255):02X}{hex_rgb[1:]}"


def palette(theme):
    """Icon colours from a theme; the same background mapping as VOID Portal's, so the icons sit together."""
    c = theme["colors"]
    light = theme["mode"] == "light"
    a, a2 = c["accent"].upper(), c["accent2"].upper()
    return {
        # A dark flight suit tinted by the accent, lit from the front.
        "suit": [(0, mix("#D8DCF0", a2, 0.3)), (0.45, mix("#3A3F5C", a, 0.35)), (1, mix("#14162A", a, 0.3))],
        "pack": mix("#2A2E48", a, 0.25),
        "rim": mix(a2, "#FFFFFF", 0.5),
        "stripe": a,
        "visor": mix("#0A0820", a2, 0.3),
        "glint": mix(a2, "#FFFFFF", 0.6),
        "arms": [a2, mix(a, "#FFFFFF", 0.25)],
        "hot": mix("#FFFFFF", a2, 0.05),
        "bg": (mix(c["panelAlt"], a, 0.08), c["bg"].upper()) if light else (mix(c["bg"], a, 0.12), mix(c["bg"], "#000000", 0.35)),
        "nebula": (mix(c["bg"], a, 0.35), 0.45) if light else (mix(c["bg"], a, 0.45), 0.85),
        "star": (a, 0.45) if light else ("#FFFFFF", 1.0),
        "core": "#070512",
        "glow": a2,
        "spark_end": mix("#FFFFFF", a2, 0.35),
        "flame": [(0, mix("#FFFFFF", a2, 0.2)), (0.4, a2), (1, mix(a, c["bg"].upper(), 0.2))],
        "shard": mix(c["warning"].upper(), "#FFFFFF", 0.25),
    }


def arm_segments(phase, color, hot):
    """The arm in short overlapping pieces, coloured along its length: white-hot core, arm colour, fading tail."""
    out = []
    for k in range(SEGMENTS):
        t0, t1 = k / SEGMENTS, min(1.0, (k + 1.35) / SEGMENTS)
        tm = (t0 + t1) / 2
        c = mix(hot, color, min(1.0, tm / 0.22))
        alpha = 1.0 if tm < 0.55 else 1.0 - 0.6 * (tm - 0.55) / 0.45
        d, _ = arm_points(phase, n=10, length=t1, t0=t0)
        out.append((d, c, alpha))
    return out


# A layer is (path, paint). Paints: ("fill", colour, alpha), ("stroke", colour, alpha, width),
# ("lin", (x1, y1, x2, y2), [(offset, colour, alpha)]), ("rad", (cx, cy, r), [(offset, colour, alpha)]).
def background_layers(P):
    top, bottom = P["bg"]
    neb, neb_a = P["nebula"]
    star, star_a = P["star"]
    sq = "M0,0h108v108h-108z"
    out = [(sq, ("lin", (54, 0, 54, 108), [(0, top, 1), (1, bottom, 1)])), (sq, ("rad", (54, 54, 50), [(0, neb, neb_a), (1, neb, 0)]))]
    out += [(circle(x, y, r), ("fill", star, a * star_a)) for x, y, r, a in STARS]
    return out


def foreground_layers(P):
    out = [(circle(CX, CY, 21), ("rad", (CX, CY, 21), [(0, P["core"], 0.95), (0.7, P["core"], 0.6), (1, P["core"], 0)]))]
    for phase, color in zip(PHASES, P["arms"]):  # the rift
        for scale, alpha in [(4.0, 0.1), (2.3, 0.28)]:
            out.append((arm_points(phase, width_scale=scale)[0], ("fill", color, alpha)))
        out += [(d, ("fill", c, a)) for d, c, a in arm_segments(phase, color, P["hot"])]
    out += [(circle(CX, CY, 7), ("rad", (CX, CY, 7), [(0, P["glow"], 0.6), (1, P["glow"], 0)])),
            (SPARK, ("rad", (CX, CY, 9.5 * S), [(0, "#FFFFFF", 1), (1, P["spark_end"], 1)]))]
    for x, y, h in SHARDS:  # loot ahead
        out += [(shard(x, y, h * 1.9), ("fill", P["shard"], 0.22)), (shard(x, y, h), ("fill", P["shard"], 1))]
    out += [(FLAME, ("lin", (54, 56, 36, 82), [(o, c, 0.95 - 0.6 * o) for o, c in P["flame"]])), (FLAME_CORE, ("fill", "#FFFFFF", 0.9))]
    body = "".join(suit_paths()) + circle(*HELMET)
    out.append((body, ("stroke", P["glow"], 0.22, 5)))
    out.append((PACK, ("fill", P["pack"], 1)))
    for d in LEGS + [TORSO, ARM, circle(*HELMET)]:
        out.append((d, ("lin", SUIT_GRAD, [(o, c, 1) for o, c in P["suit"]])))
    out += [(STRIPE, ("fill", P["stripe"], 0.95)), (VISOR, ("fill", P["visor"], 0.95)), (GLINT, ("fill", P["glint"], 0.9))]
    out.append((body, ("stroke", P["rim"], 0.55, 1.0)))
    return out


def mono_layers(color):
    body = "".join(suit_paths()) + circle(*HELMET)
    cut = "".join(arm_points(p, width_scale=1.4)[0] for p in PHASES) + SPARK + "".join(shard(x, y, h) for x, y, h in SHARDS)
    return [(body + VISOR, ("evenodd", color, 1)), (cut + FLAME, ("fill", color, 1))]


HEAD = ('<?xml version="1.0" encoding="utf-8"?>\n<vector xmlns:android="http://schemas.android.com/apk/res/android" '
        'xmlns:aapt="http://schemas.android.com/aapt" android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">\n')


def vd(layers):
    out = HEAD
    for d, p in layers:
        kind = p[0]
        if kind in ("fill", "evenodd"):
            extra = ' android:fillType="evenOdd"' if kind == "evenodd" else ""
            out += f'  <path android:pathData="{d}" android:fillColor="{argb(p[1], p[2])}"{extra}/>\n'
        elif kind == "stroke":
            out += f'  <path android:pathData="{d}" android:strokeWidth="{p[3]}" android:strokeLineJoin="round" android:strokeColor="{argb(p[1], p[2])}"/>\n'
        else:
            items = "".join(f'<item android:offset="{o}" android:color="{argb(c, a)}"/>' for o, c, a in p[2])
            if kind == "lin":
                x1, y1, x2, y2 = p[1]
                g = f'<gradient android:type="linear" android:startX="{x1}" android:startY="{y1}" android:endX="{x2}" android:endY="{y2}">{items}</gradient>'
            else:
                cx, cy, r = p[1]
                g = f'<gradient android:type="radial" android:centerX="{cx}" android:centerY="{cy}" android:gradientRadius="{r:.2f}">{items}</gradient>'
            out += f'  <path android:pathData="{d}"><aapt:attr name="android:fillColor">{g}</aapt:attr></path>\n'
    return out + "</vector>\n"


def background_vd(P):
    return vd(background_layers(P))


def foreground_vd(P):
    return vd(foreground_layers(P))


def mono_vd(color="#000000"):
    return vd(mono_layers(color))


def svg(P, size=432):
    defs, body = [], []
    for i, (d, p) in enumerate(background_layers(P) + foreground_layers(P)):
        kind = p[0]
        if kind == "fill":
            body.append(f'<path d="{d}" fill="{p[1]}" fill-opacity="{p[2]:.2f}"/>')
        elif kind == "stroke":
            body.append(f'<path d="{d}" fill="none" stroke="{p[1]}" stroke-opacity="{p[2]}" stroke-width="{p[3]}" stroke-linejoin="round"/>')
        else:
            stops = "".join(f'<stop offset="{o}" stop-color="{c}" stop-opacity="{a:.2f}"/>' for o, c, a in p[2])
            if kind == "lin":
                x1, y1, x2, y2 = p[1]
                defs.append(f'<linearGradient id="g{i}" x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}" gradientUnits="userSpaceOnUse">{stops}</linearGradient>')
            else:
                cx, cy, r = p[1]
                defs.append(f'<radialGradient id="g{i}" cx="{cx}" cy="{cy}" r="{r:.2f}" gradientUnits="userSpaceOnUse">{stops}</radialGradient>')
            body.append(f'<path d="{d}" fill="url(#g{i})"/>')
    head = f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="{size}" height="{size}">'
    return "\n".join([head, "<defs>"] + defs + ["</defs>"] + body + ["</svg>"]) + "\n"


def alias_block(themes):
    """One launcher entry per theme; the app enables the one matching its theme (see Themes.kt)."""
    out = ["        <!-- ICON ALIASES (generated by art/gen_icon.py) -->"]
    for i, t in enumerate(themes):
        key = t["id"].replace("-", "_")
        out += [
            "        <activity-alias",
            f'            android:name=".Icon_{key}"',
            f'            android:enabled="{"true" if i == 0 else "false"}"',
            '            android:exported="true"',
            f'            android:icon="@mipmap/ic_launcher_{key}"',
            f'            android:roundIcon="@mipmap/ic_launcher_{key}"',
            '            android:targetActivity=".MainActivity">',
            "            <intent-filter>",
            '                <action android:name="android.intent.action.MAIN" />',
            '                <category android:name="android.intent.category.LAUNCHER" />',
            "            </intent-filter>",
            "        </activity-alias>",
        ]
    out.append("        <!-- END ICON ALIASES -->")
    return "\n".join(out)


def adaptive(key):
    return ('<?xml version="1.0" encoding="utf-8"?>\n<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
            f'    <background android:drawable="@drawable/ic_launcher_background{key}"/>\n'
            f'    <foreground android:drawable="@drawable/ic_launcher_foreground{key}"/>\n'
            '    <monochrome android:drawable="@drawable/ic_launcher_monochrome"/>\n</adaptive-icon>\n')


if __name__ == "__main__":
    themes = json.load(open("art/themes.json"))
    res = "app/src/main/res/"
    os.makedirs("art/themes", exist_ok=True)
    os.makedirs(res + "drawable", exist_ok=True)
    os.makedirs(res + "mipmap-anydpi-v26", exist_ok=True)
    for i, t in enumerate(themes):
        P = palette(t)
        key = t["id"].replace("-", "_")
        open(f"art/themes/{key}.svg", "w").write(svg(P))
        open(f"{res}drawable/ic_launcher_background_{key}.xml", "w").write(background_vd(P))
        open(f"{res}drawable/ic_launcher_foreground_{key}.xml", "w").write(foreground_vd(P))
        open(f"{res}mipmap-anydpi-v26/ic_launcher_{key}.xml", "w").write(adaptive(f"_{key}"))
        if i == 0:  # the first (default) theme is also the app's own icon
            open("art/rush-icon.svg", "w").write(svg(P))
            open(res + "drawable/ic_launcher_background.xml", "w").write(background_vd(P))
            open(res + "drawable/ic_launcher_foreground.xml", "w").write(foreground_vd(P))
            open(res + "mipmap-anydpi-v26/ic_launcher.xml", "w").write(adaptive(""))
            open(res + "mipmap-anydpi-v26/ic_launcher_round.xml", "w").write(adaptive(""))
    open(res + "drawable/ic_launcher_monochrome.xml", "w").write(mono_vd())
    # Status bar icon: the same shape, white, enlarged to fill the 24dp square.
    note = mono_vd("#FFFFFF").replace('android:width="108dp" android:height="108dp"', 'android:width="24dp" android:height="24dp"')
    note = note.replace("  <path", '  <group android:pivotX="54" android:pivotY="56" android:scaleX="1.5" android:scaleY="1.5">\n  <path', 1)
    open(res + "drawable/ic_notification.xml", "w").write(note.replace("</vector>", "  </group>\n</vector>"))
    manifest = "app/src/main/AndroidManifest.xml"
    if os.path.exists(manifest):
        m = open(manifest).read()
        m = re.sub(r"        <!-- ICON ALIASES.*?<!-- END ICON ALIASES -->", lambda _: alias_block(themes), m, flags=re.S)
        open(manifest, "w").write(m)
    print(f"logo written for {len(themes)} themes")
