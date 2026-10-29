"""Generates the PNG textures for the Perfect Enchanting Table mod (no Pillow needed).

Usage: python tools/gen_textures.py <project root>
Positions in gui() must match PerfectEnchantingMenu / PerfectEnchantingScreen.
"""
import os
import random
import struct
import sys
import zlib


def write_png(path, w, h, px):
    """px: list of rows, each row a list of (r, g, b, a)."""
    raw = bytearray()
    for y in range(h):
        raw.append(0)
        for x in range(w):
            raw.extend(px[y][x])

    def chunk(tag, data):
        c = struct.pack(">I", len(data)) + tag + data
        return c + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(bytes(raw), 9))
    png += chunk(b"IEND", b"")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(png)


def canvas(w, h, color=(0, 0, 0, 0)):
    return [[color for _ in range(w)] for _ in range(h)]


def rect(px, x, y, w, h, c):
    for yy in range(y, y + h):
        for xx in range(x, x + w):
            px[yy][xx] = c


# ---------------------------------------------------------------- block textures
OBS = [(16, 10, 27, 255), (24, 15, 38, 255), (33, 21, 52, 255), (45, 30, 70, 255)]
CLOTH = [(18, 36, 104, 255), (24, 48, 128, 255), (31, 60, 152, 255), (40, 76, 176, 255)]
GOLD = [(140, 96, 20, 255), (200, 146, 36, 255), (246, 200, 70, 255), (255, 236, 150, 255)]
GEM = [(30, 70, 170, 255), (70, 130, 235, 255), (150, 200, 255, 255), (225, 245, 255, 255)]


def noise(seed, palette, weights):
    rnd = random.Random(seed)
    px = canvas(16, 16)
    for y in range(16):
        for x in range(16):
            px[y][x] = rnd.choices(palette, weights)[0]
    return px


def gem(px, x, y):
    """2x2 lapis gem with a highlight."""
    px[y][x] = GEM[3]
    px[y][x + 1] = GEM[1]
    px[y + 1][x] = GEM[1]
    px[y + 1][x + 1] = GEM[0]


def block_top():
    px = noise(5, CLOTH, [2, 4, 3, 1])
    # obsidian rim and gold trim, like the red cloth of the vanilla table framed in black
    for i in range(16):
        for (x, y) in ((i, 0), (i, 15), (0, i), (15, i)):
            px[y][x] = OBS[(i * 7) % 3]
    for i in range(1, 15):
        for (x, y) in ((i, 1), (1, i)):
            px[y][x] = GOLD[2]
        for (x, y) in ((i, 14), (14, i)):
            px[y][x] = GOLD[1]
    # gem corners
    for (x, y) in ((1, 1), (13, 1), (1, 13), (13, 13)):
        gem(px, x, y)
    # gold star in the middle
    star = [
        "...#...",
        "..###..",
        "#######",
        ".#####.",
        "..###..",
        ".##.##.",
        ".#...#.",
    ]
    for sy, row in enumerate(star):
        for sx, c in enumerate(row):
            if c == "#":
                px[4 + sy][4 + sx] = GOLD[3] if (sx, sy) in ((3, 1), (3, 2), (3, 3)) else GOLD[2]
    px[7][7] = GEM[2]
    return px


def block_side():
    """Only rows 4-15 are used by the 12 px tall model."""
    px = noise(11, OBS, [3, 4, 3, 1])
    # lapis cloth hanging over the edge, with a scalloped gold hem
    for x in range(16):
        hem = 9 if x % 4 in (1, 2) else 8
        for y in range(4, hem):
            px[y][x] = CLOTH[(x * 3 + y) % 4] if y > 4 else CLOTH[3]
        px[hem][x] = GOLD[1]
        if hem == 9:
            px[8][x] = GOLD[2]
    for x in range(16):
        px[4][x] = GOLD[2]
    # gems on the corners of the cloth and a glowing lapis rune on the obsidian
    gem(px, 0, 5)
    gem(px, 14, 5)
    rune = [(7, 11), (8, 11), (6, 12), (9, 12), (7, 13), (8, 13), (7, 12), (8, 12)]
    for (x, y) in rune:
        px[y][x] = GEM[1]
    px[12][7] = GEM[3]
    px[12][8] = GEM[2]
    for x in range(16):
        px[15][x] = OBS[0]
    return px


def block_bottom():
    return noise(23, OBS, [3, 4, 3, 1])


# ---------------------------------------------------------------- GUI texture
WHITE = (255, 255, 255, 255)
BG = (198, 198, 198, 255)
SHADOW = (85, 85, 85, 255)
BLACK = (0, 0, 0, 255)
SLOT_DARK = (55, 55, 55, 255)
SLOT_BG = (139, 139, 139, 255)
LIST_BG = (24, 28, 52, 255)
FIELD_BG = (18, 18, 24, 255)
CLEAR = (0, 0, 0, 0)


def panel(px, w, h):
    rect(px, 1, 1, w - 2, h - 2, BG)
    for x in range(2, w - 2):
        px[0][x] = BLACK
        px[h - 1][x] = BLACK
    for y in range(2, h - 2):
        px[y][0] = BLACK
        px[y][w - 1] = BLACK
    for x, y in [(1, 1), (w - 2, 1), (1, h - 2), (w - 2, h - 2)]:
        px[y][x] = BLACK
    # bevel
    rect(px, 2, 1, w - 5, 2, WHITE)
    rect(px, 1, 2, 2, h - 5, WHITE)
    rect(px, 3, h - 3, w - 5, 2, SHADOW)
    rect(px, w - 3, 3, 2, h - 5, SHADOW)
    px[3][3] = WHITE
    px[h - 4][w - 4] = SHADOW
    px[1][w - 3] = BG
    px[2][w - 3] = BG
    px[h - 3][1] = BG
    px[h - 3][2] = BG
    px[1][w - 2] = BLACK
    px[h - 2][w - 2] = BLACK
    for x, y in [(0, 0), (1, 0), (0, 1), (w - 1, 0), (w - 2, 0), (w - 1, 1),
                 (0, h - 1), (1, h - 1), (0, h - 2), (w - 1, h - 1), (w - 2, h - 1), (w - 1, h - 2)]:
        px[y][x] = CLEAR
    px[h - 2][1] = BLACK


def inset(px, x, y, w, h, fill=SLOT_BG):
    """Sunken box with outer size w*h at (x, y)."""
    rect(px, x, y, w, h, fill)
    for xx in range(x, x + w - 1):
        px[y][xx] = SLOT_DARK
    for yy in range(y, y + h - 1):
        px[yy][x] = SLOT_DARK
    for xx in range(x + 1, x + w):
        px[y + h - 1][xx] = WHITE
    for yy in range(y + 1, y + h):
        px[yy][x + w - 1] = WHITE


def slot(px, sx, sy):
    inset(px, sx - 1, sy - 1, 18, 18)


def text_field(px, x, y, w, h):
    """Dark field like the anvil's name box: grey frame, black inside."""
    rect(px, x, y, w, h, (160, 160, 160, 255))
    rect(px, x + 1, y + 1, w - 2, h - 2, FIELD_BG)


def magnifier(px, x, y, c):
    ring = [(2, 0), (3, 0), (4, 0), (1, 1), (5, 1), (0, 2), (6, 2), (0, 3), (6, 3), (0, 4), (6, 4),
            (1, 5), (5, 5), (2, 6), (3, 6), (4, 6)]
    for (dx, dy) in ring:
        px[y + dy][x + dx] = c
    for d in range(3):
        px[y + 6 + d][x + 6 + d] = c
        px[y + 5 + d][x + 6 + d] = c


def scroller(px, x, y, enabled):
    light = WHITE if enabled else (230, 230, 230, 255)
    mid = BG if enabled else (160, 160, 160, 255)
    dark = SHADOW if enabled else (110, 110, 110, 255)
    rect(px, x, y, 12, 15, mid)
    for xx in range(x, x + 12):
        px[y][xx] = light
        px[y + 14][xx] = dark
    for yy in range(y, y + 15):
        px[yy][x] = light
        px[yy][x + 11] = dark
    for i in range(3):
        for xx in range(x + 3, x + 9):
            px[y + 5 + i * 2][xx] = dark


def button(px, x, y, state):
    """16x16 button in vanilla style with a quill icon; state: normal, hover, disabled."""
    fill = {"normal": (111, 111, 111, 255), "hover": (126, 136, 196, 255), "disabled": (60, 60, 60, 255)}[state]
    light = {"normal": (170, 170, 170, 255), "hover": (190, 200, 255, 255), "disabled": (80, 80, 80, 255)}[state]
    dark = {"normal": (66, 66, 66, 255), "hover": (74, 82, 140, 255), "disabled": (40, 40, 40, 255)}[state]
    edge = WHITE if state == "hover" else BLACK
    rect(px, x, y, 16, 16, edge)
    rect(px, x + 1, y + 1, 14, 14, fill)
    for i in range(1, 15):
        px[y + 1][x + i] = light
        px[y + i][x + 1] = light
        px[y + 14][x + i] = dark
        px[y + i][x + 14] = dark
    # quill: white feather from the top right, gold nib at the bottom left
    feather = WHITE if state != "disabled" else (120, 120, 120, 255)
    shade = (200, 200, 210, 255) if state != "disabled" else (95, 95, 95, 255)
    nib = GOLD[2] if state != "disabled" else (110, 100, 70, 255)
    for i in range(7):
        fx, fy = x + 11 - i, y + 3 + i
        px[fy][fx] = feather
        px[fy][fx + 1] = shade
        if i < 5:
            px[fy - 1][fx + 1] = feather
    px[y + 3][x + 12] = feather
    px[y + 10][x + 4] = nib
    px[y + 11][x + 3] = nib
    px[y + 12][x + 3] = dark if state == "disabled" else (40, 40, 60, 255)


def lapis_icon(px, x, y):
    """8x8 lapis lazuli chunk for the cost labels."""
    shape = [
        "..aab...",
        ".abbcb..",
        "abcddcb.",
        "abcdcbb.",
        ".abccba.",
        "..abbba.",
        "...aaa..",
        "........",
    ]
    colors = {"a": (22, 46, 120, 255), "b": (38, 84, 190, 255), "c": (70, 130, 235, 255), "d": (170, 215, 255, 255)}
    for dy, row in enumerate(shape):
        for dx, c in enumerate(row):
            if c in colors:
                px[y + dy][x + dx] = colors[c]


def gui():
    W = H = 256
    px = canvas(W, H)
    panel_w, panel_h = 232, 237
    sub = canvas(panel_w, panel_h)
    panel(sub, panel_w, panel_h)
    # item and lapis slots (PerfectEnchantingMenu.ITEM_* / LAPIS_*)
    slot(sub, 8, 17)
    slot(sub, 8, 35)
    # rename field + search field (PerfectEnchantingScreen.RENAME_* / SEARCH_*)
    text_field(sub, 28, 17, 178, 16)
    text_field(sub, 28, 35, 196, 16)
    magnifier(sub, 212, 38, (150, 150, 160, 255))
    # enchantment list and scrollbar track
    inset(sub, 7, 54, 202, 97, LIST_BG)
    inset(sub, 210, 54, 14, 97)
    # player inventory (PerfectEnchantingMenu.INVENTORY_*)
    for row in range(3):
        for col in range(9):
            slot(sub, 36 + col * 18, 155 + row * 18)
    for col in range(9):
        slot(sub, 36 + col * 18, 213)
    for y in range(panel_h):
        for x in range(panel_w):
            px[y][x] = sub[y][x]
    scroller(px, 232, 0, True)
    scroller(px, 244, 0, False)
    button(px, 232, 16, "normal")
    button(px, 232, 32, "hover")
    button(px, 232, 48, "disabled")
    lapis_icon(px, 232, 74)
    return px


def main(root):
    assets = os.path.join(root, "src", "main", "resources", "assets", "perfectenchanting")
    tex = os.path.join(assets, "textures")
    write_png(os.path.join(tex, "block", "perfect_enchanting_table_top.png"), 16, 16, block_top())
    write_png(os.path.join(tex, "block", "perfect_enchanting_table_side.png"), 16, 16, block_side())
    write_png(os.path.join(tex, "block", "perfect_enchanting_table_bottom.png"), 16, 16, block_bottom())
    write_png(os.path.join(tex, "gui", "container", "perfect_enchanting_table.png"), 256, 256, gui())
    # mod icon: upscale of the top texture
    top = block_top()
    icon = [[top[y // 8][x // 8] for x in range(128)] for y in range(128)]
    write_png(os.path.join(assets, "icon.png"), 128, 128, icon)


if __name__ == "__main__":
    main(sys.argv[1])
