#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Build the "RebornCore AE2 Style" built-in resource pack.

The pack restyles the RebornCore / TechReborn GUIs in the 1.21.1 Applied
Energistics 2 look, reusing the exact palette of the ".ae2guiext" pack
(which does the same for GTCEu / LDLib / Titanium):

    outline   #413F54     outer 1px frame
    highlight #F2F2F2     1px inner bevel
    panel     #CBCCD4     raised face
    shadow    #878FA5     lower bevel
    slot      #ADB0C4     recessed face
    slot dark #9A9FB4     recessed top inner shadow
    deep      #696D88     deeper recess

It is registered as an ordinary, user-toggleable built-in resource pack
next to reborncore_darkmode (see RebornCoreClient), so the mod keeps its
stock textures unless the pack is enabled.

Every generated texture keeps the exact file name and pixel size the Java
code expects, so no code changes are needed.

Run:  python3 gen_ae2_gui.py
"""

import json
import os

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.abspath(__file__))
PACK = os.path.join(ROOT, "RebornCore/src/main/resources/resourcepacks/reborncore_ae2style")
PACK_RC = os.path.join(PACK, "assets/reborncore")
PACK_TR = os.path.join(PACK, "assets/techreborn")
OUT_SPRITES = os.path.join(PACK_RC, "textures/gui/sprites")
OUT_ATLAS = os.path.join(PACK_RC, "textures/gui/guielements.png")
OUT_THEME = os.path.join(PACK_RC, "theme.json")
OUT_TR_WIDGETS = os.path.join(PACK_TR, "textures/gui/widgets.png")

SRC_TR_WIDGETS = os.path.join(ROOT, "src/main/resources/assets/techreborn/textures/gui/widgets.png")
SRC_BG_MCMETA = os.path.join(
    ROOT, "RebornCore/src/main/resources/assets/reborncore/textures/gui/sprites/background.png.mcmeta")

# --------------------------------------------------------------------------
# palette
# --------------------------------------------------------------------------
T = (0, 0, 0, 0)
OUT = (65, 63, 84, 255)
HI = (242, 242, 242, 255)
PAN = (203, 204, 212, 255)
SH = (135, 143, 165, 255)
SL = (173, 176, 196, 255)
SLD = (154, 159, 180, 255)
SHD = (105, 109, 136, 255)
DARK = (77, 77, 103, 255)

RED = (181, 0, 0, 255)
RED_HI = (216, 76, 69, 255)
RED_LO = (128, 10, 10, 255)
GOLD = (255, 182, 0, 255)
GOLD_HI = (255, 255, 200, 255)
GOLD_LO = (216, 76, 69, 255)
BLUE = (62, 92, 151, 255)
BLUE_HI = (110, 150, 215, 255)
CYAN = (0, 200, 230, 255)
FLAME_D = (42, 42, 51, 255)
WHITE = (255, 255, 255, 255)
BG = (58, 58, 68, 255)

THEME = {
    "titleColor": "#413F54",
    "subtitleColor": "#B9BDD0",
    "warningTextColor": "#B50000",
    "ioInputColor": "#800000FF",
    "ioOutputColor": "#80FF4500",
    "ioBothColor": "#8034FF1E",
}


def lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3)) + (255,)


def shade(c, f):
    return tuple(max(0, min(255, int(round(c[i] * f)))) for i in range(3)) + (255,)


# --------------------------------------------------------------------------
# tiny raster helpers
# --------------------------------------------------------------------------
def canvas(w, h):
    return Image.new("RGBA", (w, h), T)


def px(im, x, y, c):
    if 0 <= x < im.width and 0 <= y < im.height and c[3] > 0:
        im.putpixel((x, y), c)


def rect(im, x, y, w, h, c):
    for j in range(h):
        for i in range(w):
            px(im, x + i, y + j, c)


def paste(dst, src, x, y):
    """Copy src over dst, skipping fully transparent source pixels."""
    for j in range(src.height):
        for i in range(src.width):
            c = src.getpixel((i, j))
            if c[3] > 0:
                px(dst, x + i, y + j, c)


def nine_slice(src, w, h, border):
    W, H = src.size
    b = border
    out = canvas(w, h)
    paste(out, src.crop((0, 0, b, b)), 0, 0)
    paste(out, src.crop((W - b, 0, W, b)), w - b, 0)
    paste(out, src.crop((0, H - b, b, H)), 0, h - b)
    paste(out, src.crop((W - b, H - b, W, H)), w - b, h - b)
    if w - 2 * b > 0:
        paste(out, src.crop((b, 0, W - b, b)).resize((w - 2 * b, b), Image.NEAREST), b, 0)
        paste(out, src.crop((b, H - b, W - b, H)).resize((w - 2 * b, b), Image.NEAREST), b, h - b)
    if h - 2 * b > 0:
        paste(out, src.crop((0, b, b, H - b)).resize((b, h - 2 * b), Image.NEAREST), 0, b)
        paste(out, src.crop((W - b, b, W, H - b)).resize((b, h - 2 * b), Image.NEAREST), w - b, b)
    if w - 2 * b > 0 and h - 2 * b > 0:
        paste(out, src.crop((b, b, W - b, H - b)).resize((w - 2 * b, h - 2 * b), Image.NEAREST), b, b)
    return out


# ---- AE2 primitives -------------------------------------------------------
def panel(w, h, fill=PAN):
    """AE2 raised panel: dark outline, white bevel, 2px bottom shadow."""
    im = canvas(w, h)
    for y in range(h):
        for x in range(w):
            if x in (0, w - 1) or y in (0, h - 1):
                c = OUT
            elif y == h - 4:
                c = HI
            elif y >= h - 3:
                c = SH
            elif y == 1 or x == 1 or x == w - 2:
                c = HI
            else:
                c = fill
            px(im, x, y, c)
    return im


def inset(w, h, fill=SL, shadow=SLD, sh=1, border=HI):
    """AE2 recessed area: white frame, dark inner shadow on top, slot face."""
    im = canvas(w, h)
    for y in range(h):
        for x in range(w):
            if x in (0, w - 1) or y in (0, h - 1):
                c = border
            elif y <= sh:
                c = shadow
            else:
                c = fill
            px(im, x, y, c)
    return im


def tray(w, h, left_cap=False, right_cap=False):
    """Output-slot tray strip (26px tall)."""
    im = canvas(w, h)
    for y in range(h):
        for x in range(w):
            if y == 0:
                c = OUT
            elif y == h - 1:
                c = HI
            elif left_cap and x == 0:
                c = OUT
            elif left_cap and x == 1:
                c = HI
            elif right_cap and x == w - 1:
                c = OUT
            elif right_cap and x == w - 2:
                c = HI
            else:
                c = SLD
            px(im, x, y, c)
    return im


def mask_from_ascii(rows):
    h = len(rows)
    w = max(len(r) for r in rows)
    return [[x < len(r) and r[x] == "#" for x in range(w)] for r in rows]


def erode(m):
    h = len(m)
    w = len(m[0])
    out = [[False] * w for _ in range(h)]
    for y in range(h):
        for x in range(w):
            if not m[y][x]:
                continue
            ok = True
            for dy in (-1, 0, 1):
                for dx in (-1, 0, 1):
                    ny, nx = y + dy, x + dx
                    if not (0 <= ny < h and 0 <= nx < w) or not m[ny][nx]:
                        ok = False
                        break
                if not ok:
                    break
            out[y][x] = ok
    return out


def paint_mask(m, fill, outline=None, boundary_only=False):
    h = len(m)
    w = len(m[0])
    im = canvas(w, h)
    inner = erode(m) if outline is not None else m
    for y in range(h):
        for x in range(w):
            if not m[y][x]:
                continue
            if boundary_only:
                if not inner[y][x]:
                    px(im, x, y, outline or fill)
            elif outline is not None and not inner[y][x]:
                px(im, x, y, outline)
            else:
                px(im, x, y, fill)
    return im


def polygon_glyph(w, h, points, fill, threshold=110):
    s = 8
    big = Image.new("L", (w * s, h * s), 0)
    ImageDraw.Draw(big).polygon([(x * w * s, y * h * s) for x, y in points], fill=255)
    small = big.resize((w, h), Image.LANCZOS)
    im = canvas(w, h)
    for y in range(h):
        for x in range(w):
            if small.getpixel((x, y)) > threshold:
                px(im, x, y, fill)
    return im


def gradient_fill(w, h, bright, dark, edge_hi=True, edge_lo=True):
    im = canvas(w, h)
    for y in range(h):
        c = lerp(bright, dark, y / max(1, h - 1))
        for x in range(w):
            px(im, x, y, c)
    if edge_hi:
        for y in range(h):
            px(im, 0, y, lerp(bright, HI, 0.45))
    if edge_lo:
        for y in range(h):
            px(im, w - 1, y, shade(dark, 0.7))
    return im


# ---- glyph helpers --------------------------------------------------------
def triangle(w, h, pointing_right, fill):
    im = canvas(w, h)
    cy = (h - 1) / 2.0
    for y in range(h):
        frac = 1.0 - abs(y - cy) / max(0.5, cy)
        n = max(1, int(round(frac * (w - 1))) + 1)
        for i in range(n):
            px(im, i if pointing_right else w - 1 - i, y, fill)
    return im


BOLT_POINTS = [
    (0.58, 0.00), (0.10, 0.56), (0.41, 0.56), (0.30, 1.00),
    (0.90, 0.40), (0.55, 0.40), (0.74, 0.00),
]

HOLOGRAM_H = [
    "##...##",
    "##...##",
    "##...##",
    "#######",
    "#######",
    "##...##",
    "##...##",
    "##...##",
]

PADLOCK_LOCKED = [
    "..####..",
    ".#....#.",
    ".#....#.",
    "########",
    "#......#",
    "#..##..#",
    "#..##..#",
    "#......#",
    "########",
]

PADLOCK_OPEN = [
    "####....",
    "#..#....",
    "#..#....",
    "########",
    "#......#",
    "#..##..#",
    "#..##..#",
    "#......#",
    "########",
]

FLAME = [
    "...#.....#...",
    "...#..#..#...",
    "...##.#.##...",
    "....#####....",
    "...#######...",
    "..#########..",
    "..#########..",
    ".###########.",
    ".###########.",
    ".###########.",
    "..#########..",
    "...#######...",
    "....#####....",
]


def arrow_mask(direction, w, h):
    """Silhouette of a progress arrow that exactly fits a w x h sprite."""
    if direction in ("right", "left"):
        head = max(4, int(round(w * 0.56)))
        shaft_h = max(3, int(round(h * 0.5)))
        y0 = (h - shaft_h) // 2
        m = [[False] * w for _ in range(h)]
        for x in range(0, w - head + 1):
            for y in range(y0, y0 + shaft_h):
                m[y][x] = True
        cy = h // 2
        for i in range(head):
            x = w - head + i
            half = int((head - 1 - i) * (h / 2.0 - 0.5) / max(1, head - 1) + 0.5)
            for y in range(cy - half, cy + half + 1):
                if 0 <= y < h:
                    m[y][x] = True
        if direction == "left":
            m = [row[::-1] for row in m]
        return m
    head = max(4, int(round(h * 0.56)))
    shaft_w = max(3, int(round(w * 0.5)))
    x0 = (w - shaft_w) // 2
    m = [[False] * w for _ in range(h)]
    for y in range(0, h - head + 1):
        for x in range(x0, x0 + shaft_w):
            m[y][x] = True
    cx = w // 2
    for i in range(head):
        y = h - head + i
        half = int((head - 1 - i) * (w / 2.0 - 0.5) / max(1, head - 1) + 0.5)
        for x in range(cx - half, cx + half + 1):
            if 0 <= x < w:
                m[y][x] = True
    if direction == "up":
        m = m[::-1]
    return m


def arrow(direction, w, h, fill, outline=OUT):
    return paint_mask(arrow_mask(direction, w, h), fill, outline)


# ==========================================================================
# RebornCore sprites
# ==========================================================================
def build_sprites():
    out = {}

    # ---- window background (nine slice, mcmeta border 6) ------------------
    out["background"] = panel(20, 20)

    # ---- slots ------------------------------------------------------------
    out["slot"] = inset(18, 18)
    out["fake_slot"] = inset(18, 18, fill=SLD, shadow=SHD)

    output = panel(26, 26)
    paste(output, inset(18, 18), 5, 5)
    out["output_slot"] = output

    out["upgrades"] = _upgrades()

    # ---- slot configuration ----------------------------------------------
    out["button_slot_normal"] = panel(18, 18)
    hover = canvas(18, 18)
    rect(hover, 0, 0, 18, 18, (255, 255, 255, 55))
    for i in range(18):
        for (x, y) in ((i, 0), (i, 17), (0, i), (17, i)):
            px(hover, x, y, (255, 255, 255, 150))
    out["button_hover_overlay_slot_normal"] = hover

    popup = panel(62, 62)
    for (cx, cy) in ((22, 3), (2, 22), (21, 22), (40, 22), (21, 41), (40, 41)):
        paste(popup, inset(18, 18, fill=SLD, shadow=SHD), cx, cy)
    out["slot_config_popup"] = popup

    # ---- tabs -------------------------------------------------------------
    out["slot_tab"] = _tab(24, 24, selected=False)
    out["left_tab"] = _tab(23, 26, selected=False)
    out["left_tab_selected"] = _tab(29, 26, selected=True)

    # ---- output slot tray -------------------------------------------------
    out["slot_bar_right"] = tray(3, 26, left_cap=True)
    out["slot_bar_center"] = tray(20, 26)
    out["slot_bar_left"] = tray(3, 26, right_cap=True)

    # ---- buttons ----------------------------------------------------------
    out["exit_button_normal"] = _exit_button(False)
    out["exit_button_hovered"] = _exit_button(True)
    out["forward"] = _transport("forward")
    out["rewind"] = _transport("rewind")
    out["fast_forward"] = _transport("fast_forward")
    out["fast_rewind"] = _transport("fast_rewind")
    out["button_hologram_enabled"] = _hologram_button(True)
    out["button_hologram_disabled"] = _hologram_button(False)
    out["button_locked"] = _lock_button(True)
    out["button_unlocked"] = _lock_button(False)

    out["dark_check_box_normal"] = inset(13, 13, fill=DARK, shadow=OUT, border=SH)
    out["light_check_box_normal"] = inset(13, 13)
    out["dark_check_box_ticked"] = _checkbox(True, dark=True)
    out["light_check_box_ticked"] = _checkbox(True, dark=False)

    # ---- progress arrows --------------------------------------------------
    for d, w, h in (("right", 16, 10), ("left", 16, 10), ("up", 10, 16), ("down", 10, 16)):
        out["progress_%s_base" % d] = arrow(d, w, h, SH)
        out["progress_%s_overlay" % d] = arrow(d, w, h, HI)

    # ---- energy / fluid bars ---------------------------------------------
    out["energy_bar"] = gradient_fill(12, 40, RED_HI, RED_LO)
    out["energy_bar_background"] = inset(14, 42, fill=SLD, shadow=SHD, sh=2)
    out["power_bar_base"] = inset(14, 50, fill=SLD, shadow=SHD, sh=2)
    out["power_bar_overlay"] = gradient_fill(12, 48, RED_HI, RED_LO)
    out["tank_background"] = inset(22, 56, fill=SLD, shadow=SHD, sh=2)
    out["tank_foreground"] = _tank_graduation()

    top_bg = canvas(169, 3)
    rect(top_bg, 0, 0, 169, 1, OUT)
    rect(top_bg, 0, 1, 169, 1, SLD)
    rect(top_bg, 0, 2, 169, 1, SH)
    out["top_energy_bar_background"] = top_bg
    top = canvas(167, 2)
    rect(top, 0, 0, 167, 1, RED_HI)
    rect(top, 0, 1, 167, 1, RED_LO)
    out["top_energy_bar"] = top

    # ---- icons ------------------------------------------------------------
    out["energy_icon"] = _bolt(9, 13, CYAN)
    out["energy_icon_empty"] = _bolt(9, 13, SLD)
    out["configure_icon"] = _gear(16)
    out["upgrade_icon"] = _upgrade_icon()
    out["charge_slot_icon"] = _charge_icon(True)
    out["discharge_slot_icon"] = _charge_icon(False)

    return out


def _upgrades():
    im = panel(24, 81)
    for i in range(4):
        paste(im, inset(18, 18), 3, 6 + i * 18)
    return im


def _tab(w, h, selected):
    im = panel(w, h)
    if selected:
        # a selected tab visually merges into the window on its right edge
        for y in range(2, h - 4):
            px(im, w - 1, y, PAN)
            px(im, w - 2, y, PAN)
    return im


def _exit_button(hovered):
    im = panel(13, 13, fill=(232, 233, 238, 255) if hovered else PAN)
    color = RED_HI if hovered else OUT
    for i in range(7):
        for (x, y) in ((3 + i, 3 + i), (4 + i, 3 + i), (9 - i, 3 + i), (8 - i, 3 + i)):
            px(im, x, y, color)
    return im


def _transport(kind):
    im = panel(12, 12)
    fast = kind.startswith("fast")
    back = kind.endswith("rewind")
    # keep the glyph clear of the bottom bevel (rows h-4 .. h-1)
    if fast:
        paste(im, triangle(4, 5, not back, SH), 2, 2)
        paste(im, triangle(4, 5, not back, SH), 6, 2)
    else:
        paste(im, triangle(7, 5, not back, SH), 3, 2)
    return im


def _hologram_button(enabled):
    im = panel(20, 12)
    glyph = paint_mask(mask_from_ascii(HOLOGRAM_H), OUT if enabled else SH)
    paste(im, glyph, 7, 2)
    return im


def _lock_button(locked):
    im = panel(20, 12)
    art = PADLOCK_LOCKED if locked else PADLOCK_OPEN
    glyph = paint_mask(mask_from_ascii(art), OUT if locked else SH)
    paste(im, glyph, 6, 1)
    return im


def _checkbox(ticked, dark):
    w = 16 if ticked else 13
    box = inset(13, 13, fill=DARK if dark else SL,
                shadow=OUT if dark else SLD, border=SH if dark else HI)
    im = canvas(w, 13)
    paste(im, box, 0, 0)
    if ticked:
        mark = canvas(w, 13)
        d = ImageDraw.Draw(mark)
        d.line([(3, 7), (6, 10), (13, 1)], fill=HI, width=2)
        for y in range(13):
            for x in range(w):
                if mark.getpixel((x, y))[3] > 128:
                    px(im, x, y, HI)
    return im


def _tank_graduation():
    im = canvas(16, 50)
    for y in range(3, 50, 6):
        rect(im, 0, y, 5, 1, RED)
        rect(im, 11, y, 5, 1, RED)
    return im


def _bolt(w, h, fill):
    return polygon_glyph(w, h, BOLT_POINTS, fill)


def _gear(size):
    im = Image.new("L", (size * 8, size * 8), 0)
    d = ImageDraw.Draw(im)
    c = size * 4.0
    import math
    for i in range(8):
        a = i * math.pi / 4 + math.pi / 8
        for r in range(int(c * 0.55), int(c * 0.95)):
            x = int(c + math.cos(a) * r)
            y = int(c + math.sin(a) * r)
            d.rectangle([x - 9, y - 9, x + 9, y + 9], fill=255)
    d.ellipse([c - 36, c - 36, c + 36, c + 36], fill=255)
    d.ellipse([c - 15, c - 15, c + 15, c + 15], fill=0)
    small = im.resize((size, size), Image.LANCZOS)
    solid = [[small.getpixel((x, y)) > 110 for x in range(size)] for y in range(size)]
    inner = erode(solid)
    out = canvas(size, size)
    for y in range(size):
        for x in range(size):
            if solid[y][x]:
                px(out, x, y, SH if inner[y][x] else OUT)
    return out


def _upgrade_icon():
    im = inset(16, 16)
    for i in range(4, 12):
        for (x, y) in ((i, 7), (i, 8), (7, i), (8, i)):
            px(im, x, y, HI)
    return im


def _charge_icon(up):
    im = canvas(18, 18)
    paste(im, arrow("up" if up else "down", 10, 16, RED_HI), 4, 1)
    return im


# ==========================================================================
# guielements.png atlas  (legacy hard-coded regions - coordinates must match
# GuiBuilder / TRTextures exactly)
# ==========================================================================
def build_atlas():
    atlas = canvas(256, 256)

    # energy output icon
    paste(atlas, _bolt(16, 16, CYAN), 150, 91)

    # generic progress bar (22x15)
    paste(atlas, inset(22, 15, fill=SLD, shadow=SHD, sh=2, border=OUT), 150, 18)
    paste(atlas, gradient_fill(22, 15, GOLD_HI, GOLD_LO, edge_hi=False, edge_lo=False), 150, 34)

    # heat / blue bars
    paste(atlas, inset(114, 18, fill=SLD, shadow=SHD, sh=2, border=OUT), 26, 218)
    paste(atlas, gradient_fill(106, 10, GOLD_HI, GOLD_LO, edge_hi=False, edge_lo=False), 26, 246)
    paste(atlas, gradient_fill(106, 10, BLUE_HI, BLUE, edge_hi=False, edge_lo=False), 0, 236)

    # burn bar (13x13)
    flame = mask_from_ascii(FLAME)
    inner = erode(flame)
    base = canvas(13, 13)
    fill = canvas(13, 13)
    for y in range(13):
        for x in range(13):
            if not flame[y][x]:
                continue
            if not inner[y][x]:
                px(base, x, y, FLAME_D)
                px(fill, x, y, FLAME_D)
            else:
                t = y / 12.0
                if t < 0.35:
                    c = lerp(RED_LO, GOLD, t / 0.35)
                elif t < 0.7:
                    c = lerp(GOLD, (255, 255, 31, 255), (t - 0.35) / 0.35)
                else:
                    c = lerp((255, 255, 31, 255), WHITE, (t - 0.7) / 0.3)
                px(fill, x, y, c)
    paste(atlas, base, 150, 64)
    paste(atlas, fill, 150, 51)

    # progress arrows (base + full are both stored, the code crops the full one)
    paste(atlas, arrow("right", 16, 10, SH), 58, 150)
    paste(atlas, arrow("right", 16, 10, HI), 74, 150)
    paste(atlas, arrow("left", 16, 10, SH), 74, 160)
    paste(atlas, arrow("left", 16, 10, HI), 58, 160)
    paste(atlas, arrow("up", 10, 16, SH), 58, 170)
    paste(atlas, arrow("up", 10, 16, HI), 68, 170)
    paste(atlas, arrow("down", 10, 16, SH), 78, 170)
    paste(atlas, arrow("down", 10, 16, HI), 88, 170)

    # EMI energy bar / fluid tank
    paste(atlas, inset(14, 50, fill=SLD, shadow=SHD, sh=2), 126, 150)
    paste(atlas, gradient_fill(14, 50, RED_HI, RED_LO), 140, 150)
    paste(atlas, inset(22, 56, fill=SLD, shadow=SHD, sh=2), 194, 26)
    paste(atlas, _tank_graduation(), 194, 82)

    return atlas


# ==========================================================================
# TechReborn widgets.png  (only the 3x3 nine patch at 0,0 is used by EMI)
# ==========================================================================
def build_tr_widgets():
    im = Image.open(SRC_TR_WIDGETS).convert("RGBA")
    for (x, y, c) in ((0, 0, HI), (1, 0, SLD), (2, 0, HI),
                      (0, 1, HI), (1, 1, SL), (2, 1, HI),
                      (0, 2, HI), (1, 2, HI), (2, 2, HI)):
        im.putpixel((x, y), c)
    return im


# ==========================================================================
# pack metadata
# ==========================================================================
def build_pack_icon():
    im = canvas(128, 128)
    rect(im, 0, 0, 128, 128, BG)
    window = nine_slice(panel(20, 20), 104, 104, 6)
    paste(im, window, 12, 12)
    for j in range(3):
        for i in range(6):
            paste(im, inset(18, 18), 24 + i * 15, 26 + j * 15)
    paste(im, inset(14, 50, fill=SLD, shadow=SHD, sh=2), 16, 30)
    paste(im, gradient_fill(12, 30, RED_HI, RED_LO), 17, 31)
    paste(im, arrow("right", 16, 10, HI), 60, 88)
    return im


def main():
    for d in (OUT_SPRITES, os.path.dirname(OUT_ATLAS), os.path.dirname(OUT_THEME),
              os.path.dirname(OUT_TR_WIDGETS)):
        os.makedirs(d, exist_ok=True)

    sprites = build_sprites()
    for name, im in sprites.items():
        im.save(os.path.join(OUT_SPRITES, name + ".png"))
    build_atlas().save(OUT_ATLAS)
    build_tr_widgets().save(OUT_TR_WIDGETS)
    build_pack_icon().save(os.path.join(PACK, "pack.png"))

    with open(SRC_BG_MCMETA) as f:
        mcmeta = f.read()
    with open(os.path.join(OUT_SPRITES, "background.png.mcmeta"), "w") as f:
        f.write(mcmeta)

    with open(os.path.join(PACK, "pack.mcmeta"), "w") as f:
        json.dump({"pack": {"pack_format": 34,
                            "description": {"text": "RebornCore AE2 Style"}}}, f, indent=4)

    with open(OUT_THEME, "w") as f:
        json.dump(THEME, f, indent="\t")
        f.write("\n")

    print("rebuild_resourcepack: %d sprites + guielements.png + widgets.png -> %s"
          % (len(sprites), os.path.relpath(PACK, ROOT)))


if __name__ == "__main__":
    main()
