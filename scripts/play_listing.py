#!/usr/bin/env python3
"""Sends the store listing in fastlane/ to Google Play: its text, icon, feature graphic and
phone screenshots, the same files F-Droid reads.

    pip install google-api-python-client google-auth pillow
    scripts/play_listing.py --check    # only checks the listing against Play's limits
    scripts/play_listing.py            # updates Play's listing to match

Run by .github/workflows/play-listing.yml. It signs in with Application Default Credentials:
there, the Workload Identity Federation credentials google-github-actions/auth writes; locally,
gcloud auth application-default login --impersonate-service-account=<the Play service account>.

The listing goes to the app's default language on Play, whichever that is (it's set in Play
Console); fastlane/ has the one listing, in en-US.

The phone screenshots are each release's own (release.yml takes them), so they're in the
listing only on release tags: without them, Play's are left as they are.

Only what differs from Play's listing is changed: text as it is, images by their SHA-256, so
merging something else doesn't send the listing for review again. With nothing to change,
the edit is thrown away.
"""
import argparse
import hashlib
import sys
from pathlib import Path

from PIL import Image

PACKAGE = "org.clementine_player.remote"
ROOT = Path(__file__).resolve().parent.parent
LISTING = ROOT / "fastlane" / "metadata" / "android" / "en-US"
IMAGES = LISTING / "images"

# Play's limits, from Play Console's store listing page.
TEXT = {  # file: (listing field, most characters)
    "title.txt": ("title", 30),
    "short_description.txt": ("shortDescription", 80),
    "full_description.txt": ("fullDescription", 4000),
}
MIN_SCREENSHOTS, MAX_SCREENSHOTS = 2, 8


def text():
    """The listing's text fields, as Play takes them."""
    fields = {}
    for name, (field, limit) in TEXT.items():
        value = (LISTING / name).read_text(encoding="utf-8").strip()
        if not value:
            sys.exit(f"{name} is empty")
        if len(value) > limit:
            sys.exit(f"{name} is {len(value)} characters; Play takes at most {limit}")
        fields[field] = value
    return fields


def images():
    """Each image type's files, in the listing's order."""
    files = {
        "icon": [IMAGES / "icon.png"],
        "featureGraphic": [IMAGES / "featureGraphic.png"],
    }
    screenshots = sorted(IMAGES.glob("phoneScreenshots/*.png"), key=lambda p: int(p.stem))
    if screenshots:
        if not MIN_SCREENSHOTS <= len(screenshots) <= MAX_SCREENSHOTS:
            sys.exit(f"{len(screenshots)} phone screenshots; Play takes {MIN_SCREENSHOTS} to "
                     f"{MAX_SCREENSHOTS}")
        files["phoneScreenshots"] = screenshots
    return files


def check_image(kind, path):
    with Image.open(path) as image:
        width, height = image.size
        mode = image.mode
    where = path.relative_to(ROOT)
    if kind == "icon":
        if (width, height) != (512, 512):
            sys.exit(f"{where} is {width}x{height}; the icon must be 512x512")
        if path.stat().st_size > 1024 * 1024:
            sys.exit(f"{where} is over 1 MB")
    elif kind == "featureGraphic":
        if (width, height) != (1024, 500):
            sys.exit(f"{where} is {width}x{height}; the feature graphic must be 1024x500")
    else:
        if not (320 <= min(width, height) and max(width, height) <= 3840):
            sys.exit(f"{where} is {width}x{height}; screenshots' sides must be 320 to 3840")
        if max(width, height) > 2 * min(width, height):
            sys.exit(f"{where} is {width}x{height}; screenshots can be at most twice as long "
                     "as they're wide")
        if path.stat().st_size > 8 * 1024 * 1024:
            sys.exit(f"{where} is over 8 MB")
    # Play takes 24-bit PNGs for these, without transparency.
    if kind != "icon" and mode not in ("RGB", "L"):
        sys.exit(f"{where} is {mode}; Play needs it without transparency (RGB)")


def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def publish(fields, files):
    import google.auth
    from googleapiclient.discovery import build
    from googleapiclient.errors import HttpError
    from googleapiclient.http import MediaFileUpload

    credentials, _ = google.auth.default(scopes=["https://www.googleapis.com/auth/androidpublisher"])
    edits = build("androidpublisher", "v3", credentials=credentials, cache_discovery=False).edits()
    edit = edits.insert(packageName=PACKAGE, body={}).execute()["id"]
    changed = []
    try:
        language = edits.details().get(packageName=PACKAGE, editId=edit).execute()["defaultLanguage"]
        where = dict(packageName=PACKAGE, editId=edit, language=language)
        try:
            current = edits.listings().get(**where).execute()
        except HttpError as error:
            if error.status_code != 404:
                raise
            current = {}
        if any(current.get(field) != value for field, value in fields.items()):
            # Patched, keeping what isn't ours (the video); made, when there's none yet.
            (edits.listings().patch if current else edits.listings().update)(
                **where, body=fields).execute()
            changed.append("text")

        for kind, paths in files.items():
            on_play = edits.images().list(**where, imageType=kind).execute().get("images", [])
            if [image.get("sha256") for image in on_play] == [sha256(p) for p in paths]:
                continue
            edits.images().deleteall(**where, imageType=kind).execute()
            for path in paths:
                edits.images().upload(**where, imageType=kind,
                                      media_body=MediaFileUpload(str(path), mimetype="image/png")
                                      ).execute()
            changed.append(kind)

        if changed:
            edits.commit(packageName=PACKAGE, editId=edit).execute()
            print(f"Updated Play's listing ({language}): {', '.join(changed)}")
        else:
            edits.delete(packageName=PACKAGE, editId=edit).execute()
            print(f"Play's listing ({language}) already matches")
    except BaseException:
        # Not left open, so the next run's edit isn't refused; the error is the one to report.
        try:
            edits.delete(packageName=PACKAGE, editId=edit).execute()
        except Exception:
            pass
        raise


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("--check", action="store_true",
                        help="only check the listing against Play's limits")
    args = parser.parse_args()

    fields = text()
    files = images()
    for kind, paths in files.items():
        for path in paths:
            check_image(kind, path)
    print(f"Listing: {', '.join(f'{f} {len(v)} characters' for f, v in fields.items())}; "
          f"{len(files.get('phoneScreenshots', [])) or 'no'} phone screenshots")

    if not args.check:
        publish(fields, files)


if __name__ == "__main__":
    main()
