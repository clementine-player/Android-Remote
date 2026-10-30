"""Tests for play_crashes.py: uv run pytest"""

import io
import json
import zipfile
from datetime import date
from pathlib import Path

import pytest
import requests

import play_crashes
from play_crashes import Cluster, Json, PlayIssue

TESTDATA = Path(__file__).parent / "testdata" / "play_crashes"


def trace(name: str) -> str:
    return (TESTDATA / f"{name}.txt").read_text()


# Two of the reports Play split into separate issues, from a draw and from a fling: one
# crash.
DRAW = trace("draw")
FLING = trace("fling")
NPE = trace("npe")
# The same crash in another build: other line numbers, and another lambda's number.
NPE_LATER = NPE.replace(":279)", ":290)").replace("$1.run", "$2.run")
SERVICE = trace("service")
# A development build's, which Play had no mapping for.
OBFUSCATED = trace("obfuscated")
SHA = "8fbe636167f59108ad1140e4778426f7571a6891"


def issue(
    issue_id: str, count: int = 1, users: int = 1, first: int = 1975, last: int = 1975
) -> Json:
    return {
        "name": f"apps/org.clementine_player.remote/{issue_id}",
        "errorReportCount": str(count),
        "distinctUsers": str(users),
        "firstAppVersion": {"versionCode": str(first)},
        "lastAppVersion": {"versionCode": str(last)},
        "lastErrorReportTime": "2026-10-14T20:00:00Z",
        "issueUri": f"https://play.google.com/console/vitals/crashes/{issue_id}/details",
    }


def report(text: str, code: int = 1975, time: str = "2026-10-14T20:00:00Z") -> Json:
    return {
        "reportText": text,
        "eventTime": time,
        "deviceModel": {"marketingName": "Samsung Galaxy S25"},
        "osVersion": {"apiLevel": "36"},
        "appVersion": {"versionCode": str(code)},
        "vcsInformation": f'repositories {{\n  system: GIT\n  revision: "{SHA}"\n}}\n',
    }


def no_mapping(report: Json) -> Path | None:
    return None


def marker(body: str) -> Json:
    match = play_crashes.MARKER.search(body)
    assert match
    return json.loads(match.group(1))


# Signatures.


def test_parses_play_frames() -> None:
    exception, message, frames = play_crashes.parse(NPE)
    assert exception == "java.lang.NullPointerException"
    assert message.startswith("Attempt to invoke")
    assert frames[0] == (
        "de.qspool.clementineremote.backend.ClementinePlayerConnection",
        "closeConnection",
    )
    assert len(frames) == 6


def test_one_crash_split_by_play_is_one() -> None:
    assert play_crashes.signature(DRAW) == play_crashes.signature(FLING)


def test_the_same_crash_in_another_build_is_the_same() -> None:
    assert play_crashes.signature(NPE) == play_crashes.signature(NPE_LATER)


def test_different_crashes_differ() -> None:
    signatures = {play_crashes.signature(t) for t in (DRAW, NPE, SERVICE)}
    assert len(signatures) == 3


def test_goes_by_the_apps_frames() -> None:
    frames = play_crashes.signature_frames(play_crashes.parse(SERVICE)[2])
    activity = "de.qspool.clementineremote.ui.ConnectActivity"
    assert frames[0] == (activity, "connect")
    assert frames[2] == (activity, "onResume$lambda")


def test_titles() -> None:
    assert play_crashes.title(NPE) == (
        "Crash: NullPointerException in ClementinePlayerConnection.closeConnection"
    )
    assert play_crashes.title(DRAW) == (
        "Crash: IllegalArgumentException in "
        "InlineClassHelperKt.throwIllegalArgumentException"
    )


def test_scrubs_what_messages_say_about_someone() -> None:
    trace = (
        "Exception java.lang.IllegalArgumentException: "
        'Key "song/content://media/external/audio/7" '
        "was already used, in /storage/emulated/0/Music/a.ogg\n"
        "  at a.B.c (B.kt:1)\n"
    )
    scrubbed = play_crashes.scrub(trace)
    assert scrubbed == (
        'Exception java.lang.IllegalArgumentException: Key "…" was already used, in …\n'
        "  at a.B.c (B.kt:1)"
    )
    assert play_crashes.signature(trace) == play_crashes.signature(scrubbed)


@pytest.mark.parametrize(
    ("name", "normalized"),
    [
        ("getAndMeasure-0kLqBqw", "getAndMeasure"),
        ("onResume$lambda$1$3", "onResume$lambda"),
    ],
)
def test_normalizes_compiler_method_names(name: str, normalized: str) -> None:
    assert play_crashes.normalize_method(name) == normalized


@pytest.mark.parametrize(
    ("name", "normalized"), [("a.B$1", "a.B"), ("a.B$Scope", "a.B$Scope")]
)
def test_normalizes_compiler_class_names(name: str, normalized: str) -> None:
    assert play_crashes.normalize_class(name) == normalized


# Play.


def test_interval() -> None:
    assert play_crashes.interval(30, end=date(2026, 10, 30)) == {
        "interval.startTime.year": 2026,
        "interval.startTime.month": 9,
        "interval.startTime.day": 30,
        "interval.endTime.year": 2026,
        "interval.endTime.month": 10,
        "interval.endTime.day": 30,
    }


def test_searches_reports_a_week_at_a_time() -> None:
    found = play_crashes.windows(16, end=date(2026, 10, 30))
    assert len(found) == 3
    assert found[0]["interval.startTime.day"] == 23
    assert found[0]["interval.endTime.day"] == 30
    assert found[2]["interval.startTime.day"] == 14
    assert found[2]["interval.endTime.day"] == 16


def zipped(files: dict[str, bytes]) -> bytes:
    data = io.BytesIO()
    with zipfile.ZipFile(data, "w") as archive:
        for name, content in files.items():
            archive.writestr(name, content)
    return data.getvalue()


def test_finds_a_dev_builds_mapping_in_its_bundle() -> None:
    bundle = zipped({play_crashes.BUNDLE_MAPPING: b"# pg_map_id: f47038e\n"})
    artifact = zipped(
        {
            "_temp/ClementineRemote-play-release.aab": bundle,
            "_temp/ClementineRemote-dev.apk": b"apk",
        }
    )
    assert play_crashes.bundle_mapping(artifact) == b"# pg_map_id: f47038e\n"
    assert play_crashes.bundle_mapping(zipped({"a.apk": b"apk"})) is None


def test_revision() -> None:
    assert play_crashes.revision(report(NPE)) == SHA
    assert play_crashes.revision({}) is None


def test_only_obfuscated_traces_are_retraced() -> None:
    assert not play_crashes.obfuscated(NPE)
    assert play_crashes.obfuscated(OBFUSCATED)


# Grouping.


def found() -> list[PlayIssue]:
    return [
        (
            issue("80a42b2b", count=9, users=3, first=1975, last=1977),
            [report(DRAW, 1977, "2026-10-25T10:00:00Z")],
        ),
        (issue("ef46c073", count=2), [report(FLING)]),
        (issue("95832be0"), [report(NPE)]),
        (issue("empty"), []),
    ]


def compose() -> Cluster:
    return next(
        c for c in play_crashes.clusters(found(), no_mapping) if len(c.issues) == 2
    )


def test_groups_play_issues_by_crash() -> None:
    assert len(play_crashes.clusters(found(), no_mapping)) == 2
    cluster = compose()
    assert cluster.time == "2026-10-25T10:00:00Z"
    assert play_crashes.versions(cluster) == [1975, 1977]


def test_describes_a_crash() -> None:
    cluster = compose()
    body = play_crashes.describe(cluster)
    assert marker(body) == {"signature": cluster.signature, "max_version": 1977}
    assert (
        "**11** reports of this crash from up to **4** users in the last 30 days,"
        " in version codes 1975, 1977." in body
    )
    link = "https://play.google.com/console/vitals/crashes/80a42b2b/details"
    assert f"[80a42b2b]({link})" in body
    assert "Samsung Galaxy S25 (API 36)" in body
    assert "R8's names" not in body


def test_counts_one_in_the_singular() -> None:
    (cluster,) = play_crashes.clusters([(issue("95832be0"), [report(NPE)])], no_mapping)
    assert (
        "**1** report of this crash from up to **1** user in the last 30 days,"
        " in version code 1975." in play_crashes.describe(cluster)
    )


def test_says_a_trace_could_not_be_retraced() -> None:
    (cluster,) = play_crashes.clusters(
        [(issue("dev"), [report(OBFUSCATED, 1878)])], no_mapping
    )
    assert "still has R8's names" in play_crashes.describe(cluster)


# GitHub.


class FakeGitHub:
    def __init__(self, issues: list[Json] | None = None) -> None:
        self.issues = issues or []
        self.sent: list[tuple[str, str, Json]] = []

    def crash_issues(self) -> dict[str, tuple[Json, Json]]:
        found = {}
        for issue in self.issues:
            state = marker(issue["body"])
            found[state["signature"]] = (issue, state)
        return found

    def send(self, method: str, path: str, body: Json) -> Json:
        self.sent.append((method, path, body))
        return {}

    def requests(self) -> list[tuple[str, str]]:
        """What was sent, but for making the label."""
        return [(method, path) for method, path, _ in self.sent if path != "/labels"]


def cluster(code: int = 1975) -> Cluster:
    (found,) = play_crashes.clusters(
        [(issue("95832be0", first=code, last=code), [report(NPE, code)])], no_mapping
    )
    return found


def filed(state: str, max_version: int) -> Json:
    state_json = json.dumps(
        {"signature": cluster().signature, "max_version": max_version}
    )
    return {
        "number": 7,
        "state": state,
        "body": f"<!-- play-crash: {state_json} -->\nolder",
    }


def test_files_a_new_crash() -> None:
    github = FakeGitHub()
    play_crashes.file_issues(github, [cluster()], dry_run=False)
    assert github.requests() == [("POST", "/issues")]
    body = next(b for _, path, b in github.sent if path == "/issues")
    assert body["labels"] == ["play-crash"]
    assert body["title"].startswith("Crash: NullPointerException")


def test_updates_an_open_issue() -> None:
    github = FakeGitHub([filed("open", 1975)])
    play_crashes.file_issues(github, [cluster()], dry_run=False)
    assert github.requests() == [("PATCH", "/issues/7")]


def test_leaves_a_closed_issue_closed_for_versions_seen_before() -> None:
    github = FakeGitHub([filed("closed", 1977)])
    play_crashes.file_issues(github, [cluster(1975)], dry_run=False)
    assert github.requests() == []


def test_reopens_a_closed_issue_seen_in_a_newer_version() -> None:
    github = FakeGitHub([filed("closed", 1975)])
    play_crashes.file_issues(github, [cluster(1979)], dry_run=False)
    assert github.requests() == [("POST", "/issues/7/comments"), ("PATCH", "/issues/7")]
    assert github.sent[-1][2]["state"] == "open"


def test_dry_run_changes_nothing() -> None:
    github = FakeGitHub([filed("closed", 1975)])
    play_crashes.file_issues(github, [cluster(1979), cluster()], dry_run=True)
    assert github.sent == []


# Mappings.


class FakeReleases(play_crashes.GitHub):
    """GitHub with releases and tags, and their mappings."""

    def __init__(self, releases: dict[str, bytes], tags: dict[str, str]) -> None:
        super().__init__("clementine-player/Android-Remote", "token")
        self.releases = releases
        self.tags = tags

    def get(self, path: str, **params: str | int) -> Json:
        tag = path.removeprefix("/releases/tags/")
        if tag not in self.releases:
            response = requests.Response()
            response.status_code = 404
            raise requests.HTTPError(response=response)
        asset = {"name": f"ClementineRemote-{tag}-play-mapping.txt", "url": tag}
        return {"assets": [asset]}

    def get_list(self, path: str, **params: str | int) -> list[Json]:
        return [{"name": t, "commit": {"sha": sha}} for t, sha in self.tags.items()]

    def download(self, url: str, accept: str | None = "") -> bytes:
        return self.releases[url]


def test_finds_a_dev_builds_mapping_in_its_pre_release(tmp_path: Path) -> None:
    github = FakeReleases({"dev-2064": b"dev map"}, {})
    mapping = play_crashes.Mappings(github, str(tmp_path)).get(report(OBFUSCATED, 2064))
    assert mapping is not None
    assert mapping.read_bytes() == b"dev map"


def test_finds_a_releases_mapping_by_its_tag(tmp_path: Path) -> None:
    github = FakeReleases({"v13.7": b"release map"}, {"v13.7": SHA})
    mapping = play_crashes.Mappings(github, str(tmp_path)).get(report(OBFUSCATED, 1885))
    assert mapping is not None
    assert mapping.read_bytes() == b"release map"
