#!/usr/bin/env python3
"""Builds the Google Play listing graphics from the project's own artwork.

    pip install pillow cairosvg
    scripts/store_graphics.py [screenshots dir]

- icon.png (512x512): Clementine's logo, store/clementine-icon.svg, copied from
  clementine-player/Clementine's data/icon.svg.
- featureGraphic.png (1024x500): the logo on the app's own purple-to-orange gradient
  (the connect screen's background, res/drawable/activity_background.xml) with the app's
  name, set in Liberation Sans.
- phoneScreenshots/: the emulator screenshots from the store-screenshots workflow, if a
  directory of them is given, copied as they are.

Everything is drawn from these sources; nothing is generated.
"""
import io
import shutil
import sys
from pathlib import Path

import cairosvg
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
LOGO = ROOT / "store" / "clementine-icon.svg"
OUT = ROOT / "fastlane" / "metadata" / "android" / "en-US" / "images"

# res/values/colors.xml
CLEMENTINE_LEFT = (0xAF, 0x59, 0x7D)
CLEMENTINE_RIGHT = (0xDB, 0x68, 0x35)
WHITE = (255, 255, 255)

FONT_DIR = Path("/usr/share/fonts/truetype/liberation")
FONT_BOLD = FONT_DIR / "LiberationSans-Bold.ttf"
FONT_REGULAR = FONT_DIR / "LiberationSans-Regular.ttf"


def logo(width):
    """The logo rendered from its SVG, trimmed to its visible bounds."""
    png = cairosvg.svg2png(url=str(LOGO), output_width=width)
    image = Image.open(io.BytesIO(png)).convert("RGBA")
    return image.crop(image.getbbox())


def fit(image, box):
    """Scales an image to fit inside a (width, height) box."""
    scale = min(box[0] / image.width, box[1] / image.height)
    return image.resize((round(image.width * scale), round(image.height * scale)),
                        Image.LANCZOS)


def paste_centred(canvas, image, centre):
    canvas.alpha_composite(image, (round(centre[0] - image.width / 2),
                                   round(centre[1] - image.height / 2)))


def gradient(size):
    """A left-to-right gradient between the app's two brand colours."""
    width, height = size
    row = Image.new("RGB", (width, 1))
    for x in range(width):
        t = x / (width - 1)
        row.putpixel((x, 0), tuple(round(a + (b - a) * t)
                                   for a, b in zip(CLEMENTINE_LEFT, CLEMENTINE_RIGHT)))
    return row.resize(size).convert("RGBA")


def icon():
    """512x512, opaque. Play masks it to a rounded square, so the logo keeps clear of the
    corners."""
    canvas = Image.new("RGBA", (512, 512), WHITE + (255,))
    paste_centred(canvas, fit(logo(2048), (380, 380)), (256, 256))
    return canvas.convert("RGB")


def feature_graphic():
    """1024x500, opaque: the logo on a white disc at the left, the name at the right."""
    canvas = gradient((1024, 500))
    draw = ImageDraw.Draw(canvas)

    centre = (250, 250)
    radius = 170
    draw.ellipse((centre[0] - radius, centre[1] - radius,
                  centre[0] + radius, centre[1] + radius), fill=WHITE)
    paste_centred(canvas, fit(logo(2048), (230, 230)), (centre[0] + 4, centre[1]))

    # The name and a line about the app, as large as fits to the right of the disc.
    x, right = 460, 1024 - 56
    lines = [("Clementine", FONT_BOLD, 78), ("Remote", FONT_BOLD, 78),
             ("Control Clementine from your phone", FONT_REGULAR, 32)]
    scale = min(1.0, min((right - x) / draw.textlength(text, ImageFont.truetype(str(f), size))
                         for text, f, size in lines))
    fonts = [ImageFont.truetype(str(f), round(size * scale)) for _, f, size in lines]
    gaps = [0, round(8 * scale), round(28 * scale)]
    heights = [font.getbbox("Clementine")[3] for font in fonts]
    y = 250 - (sum(heights) + sum(gaps)) / 2
    for (text, _, _), font, gap, height in zip(lines, fonts, gaps, heights):
        y += gap + height
        draw.text((x, y), text, font=font, fill=WHITE, anchor="ls")
    return canvas.convert("RGB")


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    icon().save(OUT / "icon.png", optimize=True)
    feature_graphic().save(OUT / "featureGraphic.png", optimize=True)

    if len(sys.argv) > 1:
        shots = OUT / "phoneScreenshots"
        shutil.rmtree(shots, ignore_errors=True)
        shots.mkdir()
        for i, source in enumerate(sorted(Path(sys.argv[1]).glob("[0-9]_*.png")), 1):
            with Image.open(source) as image:
                image.convert("RGB").save(shots / f"{i}.png", optimize=True)

    for path in sorted(OUT.rglob("*.png")):
        with Image.open(path) as image:
            print(f"{path.relative_to(ROOT)}: {image.size[0]}x{image.size[1]} {image.mode}")


if __name__ == "__main__":
    main()
