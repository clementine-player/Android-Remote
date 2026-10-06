#!/usr/bin/env python3
"""Helps .github/workflows/translations.yml pull the app's translations from Transifex.

    scripts/android-translations.py count > counts      # translated strings in each language
    scripts/android-translations.py merge REVIEWED_RES  # add strings only REVIEWED_RES has
    scripts/android-translations.py check counts        # fail if a language lost many

For Android files, Transifex's onlytranslated mode leaves reviewed translations out, and
onlyreviewed leaves out those not reviewed yet, so the workflow pulls both: the reviewed
translations into REVIEWED_RES, then the rest into the app's resources, and merges them.
"""

import pathlib
import re
import shutil
import sys

RES = pathlib.Path(__file__).resolve().parent.parent / "app/src/main/res"
# A string, plural or array, with the indentation before it.
ELEMENT = re.compile(
    r'^([ \t]*)<(string|plurals|string-array)\b[^>]*\bname="([^"]+)"[^>]*?(?:/>|>.*?</\2>)',
    re.M | re.S)
# What the workflow has always counted: each string, and each plural or array item.
TRANSLATION = re.compile(r"<string |<item ")


def languages(res):
    return sorted(res.glob("values-*/strings.xml"))


def count():
    for path in languages(RES):
        print(path.parent.name, len(TRANSLATION.findall(path.read_text(encoding="utf-8"))))


def merge(reviewed_res):
    for reviewed in languages(pathlib.Path(reviewed_res)):
        target = RES / reviewed.parent.name / "strings.xml"
        if not target.exists():
            target.parent.mkdir(exist_ok=True)
            shutil.copyfile(reviewed, target)
            continue
        text = target.read_text(encoding="utf-8")
        present = {m.group(3) for m in ELEMENT.finditer(text)}
        added = 0
        previous = None
        # The reviewed file is in the English file's order, so each missing element goes after
        # the one before it that the target has, or at the top if there's none.
        for m in ELEMENT.finditer(reviewed.read_text(encoding="utf-8")):
            name = m.group(3)
            if name in present:
                previous = name
                continue
            if previous is None:
                at = re.search(r"<resources\b[^>]*>", text).end()
            else:
                at = next(e for e in ELEMENT.finditer(text) if e.group(3) == previous).end()
            text = text[:at] + "\n" + m.group(0) + text[at:]
            present.add(name)
            previous = name
            added += 1
        if added:
            target.write_text(text, encoding="utf-8")
            print(f"{reviewed.parent.name}: {added} reviewed translations added")


def check(counts_path):
    before = dict(line.split() for line in open(counts_path) if line.strip())
    before = {language: int(n) for language, n in before.items()}
    after = {path.parent.name: len(TRANSLATION.findall(path.read_text(encoding="utf-8")))
             for path in languages(RES)}
    print(f"Translated strings: {sum(before.values())} before, {sum(after.values())} after.")
    # Losing many translations at once means Transifex didn't give them: a resource .tx/config
    # names wrongly, one never given the translations (see RELEASING.md), or translations left
    # out of a download, as the reviewed ones were from onlytranslated. A language with only a
    # few translations can lose a tenth to one fix, so those are judged by the total alone.
    lost = [f"{language} ({n} before, {after.get(language, 0)} after)"
            for language, n in sorted(before.items())
            if n >= 20 and after.get(language, 0) * 10 < n * 9]
    if sum(after.values()) * 10 < sum(before.values()) * 9:
        lost.append("all languages together")
    if lost:
        print("::error::The pull would remove more than a tenth of the translations of "
              + ", ".join(lost) + ", so it wasn't committed.")
        sys.exit(1)


if __name__ == "__main__":
    command, *args = sys.argv[1:]
    {"count": count, "merge": merge, "check": check}[command](*args)
