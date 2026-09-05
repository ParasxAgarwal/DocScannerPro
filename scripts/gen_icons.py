#!/usr/bin/env python3
"""Regenerate raster launcher icons and the store icon from the brand design.

The vector sources of truth live in app/src/main/res/drawable/
(ic_launcher_background.xml, ic_launcher_foreground.xml). This script mirrors
that geometry to produce:
  - legacy launcher rasters for API < 26 (mipmap-*dpi/ic_launcher*.webp)
  - the 512px store listing icon (fastlane/metadata/android/en-US/images/)

Usage: python3 scripts/gen_icons.py
Requires: Pillow
"""

from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "app" / "src" / "main" / "res"
STORE = ROOT / "fastlane" / "metadata" / "android" / "en-US" / "images"

# Viewport of the 108dp adaptive-icon layers; launchers show the central 72dp.
VIEW = 108.0
VISIBLE = 72.0

GRAD_START = (0x3B, 0x82, 0xF6)
GRAD_END = (0x1E, 0x40, 0xAF)
CYAN = (0x22, 0xD3, 0xEE)
BLUE = (0x25, 0x63, 0xEB)
WHITE = (0xFF, 0xFF, 0xFF)

SHEEN_ALPHA = 0x24
STRIP_ALPHA = 0x40


def to_canvas(coord, size):
    """Map a 108-viewport coordinate onto the legacy canvas (central 72dp)."""
    return (coord - (VIEW - VISIBLE) / 2) * (size / VISIBLE)


class IconRenderer:
    def __init__(self, size, ss=4):
        self.size = size
        self.ss = ss
        self.s = size * ss  # supersampled canvas edge
        self.im = Image.new("RGBA", (self.s, self.s), (0, 0, 0, 0))

    def px(self, x):
        return to_canvas(x, self.s)

    def sc(self, length):
        """Scale a length (radius, stroke width) from viewport units."""
        return length * (self.s / VISIBLE)

    def gradient(self):
        grad = Image.new("RGBA", (self.s, self.s))
        small = Image.new("RGBA", (64, 64))
        for y in range(64):
            for x in range(64):
                t = (x + y) / 126.0
                c = tuple(round(a + (b - a) * t) for a, b in zip(GRAD_START, GRAD_END))
                small.putpixel((x, y), c + (255,))
        grad = small.resize((self.s, self.s), Image.BICUBIC)

        # soft radial highlight in the upper-left
        sheen = Image.new("L", (64, 64), 0)
        for y in range(64):
            for x in range(64):
                cx, cy, r = 30 / 108 * 64, 22 / 108 * 64, 62 / 108 * 64
                d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5 / r
                sheen.putpixel((x, y), max(0, round(255 * (1 - d))))
        sheen = sheen.point(lambda v: v * SHEEN_ALPHA // 255).resize(
            (self.s, self.s), Image.BICUBIC
        )
        white = Image.new("RGBA", (self.s, self.s), WHITE + (255,))
        grad = Image.composite(white, grad, sheen)
        self.im.alpha_composite(grad)

    def bar(self, x1, y1, x2, y2, width, color, alpha=255):
        """Horizontal/vertical bar with round caps."""
        d = ImageDraw.Draw(self.im)
        r = self.sc(width) / 2
        box = [self.px(x1), self.px(y1) - r, self.px(x2), self.px(y1) + r]
        if y1 != y2:
            box = [self.px(x1) - r, self.px(y1), self.px(x1) + r, self.px(y2)]
        d.rounded_rectangle(box, radius=r, fill=color + (alpha,))

    def page(self):
        d = ImageDraw.Draw(self.im)
        d.rounded_rectangle(
            [self.px(39), self.px(37), self.px(69), self.px(71)],
            radius=self.sc(3.5),
            fill=WHITE + (255,),
        )
        # scanned strip under the beam (pre-blended 25% cyan over white)
        d.rectangle(
            [self.px(39), self.px(66.5), self.px(69), self.px(71)],
            fill=(0xC8, 0xF4, 0xFB, 255),
        )

    def brackets(self):
        """Rounded-square outline with side middles erased -> viewfinder."""
        layer = Image.new("RGBA", (self.s, self.s), (0, 0, 0, 0))
        d = ImageDraw.Draw(layer)
        w = self.sc(4)
        box = [self.px(31), self.px(31), self.px(77), self.px(77)]
        d.rounded_rectangle(box, radius=self.sc(8), outline=CYAN + (255,), width=round(w))
        mid1, mid2 = self.px(44), self.px(64)
        d.rectangle([mid1, 0, mid2, self.s], fill=(0, 0, 0, 0))  # top+bottom
        d.rectangle([0, mid1, self.s, mid2], fill=(0, 0, 0, 0))  # left+right
        self.im.alpha_composite(layer)

    def render(self):
        self.gradient()
        self.page()
        self.bar(38, 64.5, 70, 64.5, 4, CYAN)  # beam
        self.bar(44.75, 44.5, 59, 44.5, 4.2, BLUE)  # heading
        self.bar(44.75, 51, 63.25, 51, 3.4, BLUE)
        self.bar(44.75, 57.5, 57.5, 57.5, 3.4, BLUE)
        self.brackets()
        return self.im.resize((self.size, self.size), Image.LANCZOS)


def rounded(im, radius_ratio):
    mask = Image.new("L", im.size, 0)
    d = ImageDraw.Draw(mask)
    d.rounded_rectangle([0, 0, im.size[0] - 1, im.size[1] - 1], radius=im.size[0] * radius_ratio, fill=255)
    out = Image.new("RGBA", im.size, (0, 0, 0, 0))
    out.paste(im, (0, 0), mask)
    return out


def circular(im):
    mask = Image.new("L", im.size, 0)
    d = ImageDraw.Draw(mask)
    d.ellipse([0, 0, im.size[0] - 1, im.size[1] - 1], fill=255)
    out = Image.new("RGBA", im.size, (0, 0, 0, 0))
    out.paste(im, (0, 0), mask)
    return out


def main():
    sizes = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
    for dpi, size in sizes.items():
        tile = rounded(IconRenderer(size).render(), 0.22)
        circle = circular(IconRenderer(size).render())
        tile.save(RES / f"mipmap-{dpi}" / "ic_launcher.webp", lossless=True, method=6)
        circle.save(RES / f"mipmap-{dpi}" / "ic_launcher_round.webp", lossless=True, method=6)
        print(f"mipmap-{dpi}: {tile.size}")

    # 512px store icon, full-bleed square (no alpha), Play/F-Droid convention
    store = IconRenderer(512).render().convert("RGB")
    store.save(STORE / "icon.png")
    store.save(STORE / "icon.webp", quality=90, method=6)
    print(f"store icon: {store.size}")

    # preview sheet for quick visual inspection
    sheet = Image.new("RGBA", (1400, 560), (0x11, 0x11, 0x14, 255))
    for i, (label_im, x) in enumerate(
        [(rounded(IconRenderer(360).render(), 0.22), 60), (circular(IconRenderer(360).render()), 500)]
    ):
        sheet.alpha_composite(label_im, (x, 100))
    small_sizes = [96, 72, 48]
    x = 950
    for s in small_sizes:
        sheet.alpha_composite(rounded(IconRenderer(s).render(), 0.22), (x, 100 + (96 - s) // 2))
        x += s + 30
    sheet.convert("RGB").save(ROOT / "scripts" / "icon_preview.png")
    print("preview: scripts/icon_preview.png")


if __name__ == "__main__":
    main()
