#!/usr/bin/env python3
"""Makes the store listing's phone screenshots from a store-screenshots run. Run by
.github/workflows/release.yml, so each release's listing shows the app as released:

    pip install pillow
    scripts/store_screenshots.py <screenshots dir>

They're the numbered screens in the dark theme, which most people use, then the player in
the light theme, to show there's one: Play shows at most eight. They go in
fastlane/metadata/android/en-US/images/phoneScreenshots/, as 1.png, 2.png, ...

A screenshot whose pixels are the same as the one already there is left as it is, so a
release that doesn't change a screen doesn't store it again.
"""
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
SHOTS = ROOT / "fastlane" / "metadata" / "android" / "en-US" / "images" / "phoneScreenshots"
MOST = 8


def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    taken = Path(sys.argv[1])
    dark = sorted(taken.glob("dark_[0-9]_*.png"))
    if not dark:
        sys.exit(f"No dark screenshots (dark_<n>_<screen>.png) in {taken}")
    sources = dark + sorted(taken.glob("1_*.png"))
    if len(sources) > MOST:
        sys.exit(f"{len(sources)} screenshots; Play shows at most {MOST}")

    SHOTS.mkdir(parents=True, exist_ok=True)
    for i, source in enumerate(sources, 1):
        target = SHOTS / f"{i}.png"
        with Image.open(source) as image:
            new = image.convert("RGB")
        if target.exists():
            with Image.open(target) as image:
                old = image.convert("RGB")
            if old.size == new.size and old.tobytes() == new.tobytes():
                print(f"{target.relative_to(ROOT)}: unchanged ({source.name})")
                continue
        new.save(target, optimize=True)
        print(f"{target.relative_to(ROOT)}: {source.name}, {new.size[0]}x{new.size[1]}")
    # Fewer screens than last time: the rest go.
    for extra in SHOTS.glob("*.png"):
        if not extra.stem.isdigit() or int(extra.stem) > len(sources):
            extra.unlink()
            print(f"{extra.relative_to(ROOT)}: removed")


if __name__ == "__main__":
    main()
