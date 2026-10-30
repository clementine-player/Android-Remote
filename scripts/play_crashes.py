#!/usr/bin/env -S uv run
"""Files the crashes Google Play has for the app as GitHub issues, one per crash.

    uv run scripts/play_crashes.py --dry-run  # prints what it would file or update
    uv run scripts/play_crashes.py            # files and updates the issues

Run daily by .github/workflows/play-crashes.yml. It reads Play's Developer Reporting
API (vitals.errors) for the last DAYS days, and signs in with Application Default
Credentials: there, the Workload Identity Federation credentials
google-github-actions/auth writes; locally, gcloud auth application-default login
--impersonate-service-account=<the Play service account>
--scopes=https://www.googleapis.com/auth/playdeveloperreporting, or a token for it in
PLAY_ACCESS_TOKEN. GitHub takes GITHUB_TOKEN (with --dry-run, only to read), and the
repository from GITHUB_REPOSITORY.

Play groups crashes into issues of its own, but splits one crash into several when what
led to it differs further down the stack (a draw, a fling or a drag, say), so the
issues here group Play's by the crash itself: the exception, and the app's top frames,
or the top frames if the app's code isn't on the stack (see signature()). Each GitHub
issue says which of Play's it holds, how often each crashed, on which versions and
devices, and the stack trace of the latest report.

Play puts R8's shortened names back with the mappings dev.yml and release.yml send it.
When it hasn't, the names are put back here with the mapping of the commit the build
was made from: from its release's GitHub release (release.yml attaches them), or for a
development build, from the dev workflow's artifact while GitHub keeps it (retrace,
from the SDK's command-line tools). Without one, the issue says the trace still has
R8's names.

The issues are found again by the signature in their description, so nothing is kept
anywhere else. An issue someone closed is reopened when the crash comes back in a
version newer than any it was seen in before.
"""

import argparse
import contextlib
import dataclasses
import hashlib
import io
import json
import os
import re
import subprocess
import sys
import tempfile
import zipfile
from collections.abc import Callable, Iterable, Iterator
from datetime import UTC, date, datetime, timedelta
from pathlib import Path
from typing import Any, Protocol

import google.auth
import requests
from google.auth.transport.requests import AuthorizedSession
from google.oauth2.credentials import Credentials

PACKAGE = "org.clementine_player.remote"
APP_PACKAGE = "de.qspool.clementineremote."
REPORTING = "https://playdeveloperreporting.googleapis.com/v1beta1"
SCOPE = "https://www.googleapis.com/auth/playdeveloperreporting"
GITHUB = "https://api.github.com"
LABEL = "play-crash"
DAYS = 30

# How many frames make a crash's signature: the app's, or with none of the app's on the
# stack, the top ones.
APP_FRAMES = 3
TOP_FRAMES = 5

MARKER = re.compile(r"<!-- play-crash: (\{.*?\}) -->")
# Play writes a space before a frame's "(", which retrace doesn't take.
FRAME = re.compile(r"^\s*at\s+([\w$.<>-]+)\.([\w$<>-]+)\s?\(([^)]*)\)")
EXCEPTION = re.compile(
    r"^(?:Exception\s+|Caused by:\s+)?"
    r"([\w$.]+(?:Exception|Error|Throwable))\b:?\s*(.*)$"
)

# What Play's and GitHub's APIs send and take.
Json = dict[str, Any]
# A search's query parameters.
Params = dict[str, str | int]
# A stack frame: its class and method.
Frame = tuple[str, str]
# One of Play's issues, and its sampled reports, latest first.
PlayIssue = tuple[Json, list[Json]]


# Signatures.


def normalize_method(name: str) -> str:
    """A method's name without what the compilers add to it.

    That changes from build to build: Kotlin's suffix for inline classes (-0kLqBqw),
    lambda numbering, and R8's outlines.
    """
    name = re.sub(r"-[\w]{7}$", "", name)
    name = re.sub(r"\$lambda\$\d+(\$\d+)*", "$lambda", name)
    name = re.sub(r"\$\d+$", "", name)
    return name


def normalize_class(name: str) -> str:
    """A class's name without numbered anonymous and synthetic classes."""
    name = re.sub(r"\$\$ExternalSynthetic.*$", "", name)
    name = re.sub(r"(\$\d+)+(?=\$|$)", "", name)
    return name


def parse(trace: str) -> tuple[str, str, list[Frame]]:
    """A stack trace's exception class and message, and its frames."""
    exception, message, frames = None, "", []
    for line in trace.splitlines():
        frame = FRAME.match(line)
        if frame:
            frames.append((frame.group(1), frame.group(2)))
            continue
        if exception is None:
            match = EXCEPTION.match(line.strip())
            if match:
                exception, message = match.group(1), match.group(2).strip()
        elif frames and line.strip().startswith("Caused by:"):
            # The cause is part of the report, but the crash is where it was thrown.
            break
    return exception or "unknown", message, frames


def scrub(trace: str) -> str:
    """The trace without what its messages may say about someone's music or phone.

    That's quoted strings (a list's key, say, may be a song's address), URIs and paths.
    The issues are public.
    """
    lines = []
    for line in trace.splitlines():
        if not FRAME.match(line):
            line = re.sub(r'"[^"]*"', '"…"', line)
            line = re.sub(r"\b[a-z][\w+.-]*://\S+", "…", line)
            line = re.sub(r"(?<![\w.])/(?:storage|sdcard|data|mnt)/\S+", "…", line)
        lines.append(line)
    return "\n".join(lines)


def signature_frames(frames: list[Frame]) -> list[Frame]:
    """The frames that tell one crash from another: the app's top ones, or the top."""
    frames = [(normalize_class(c), normalize_method(m)) for c, m in frames]
    app = [f for f in frames if f[0].startswith(APP_PACKAGE)]
    return app[:APP_FRAMES] if app else frames[:TOP_FRAMES]


def signature(trace: str) -> str:
    """A short hash naming the crash in a stack trace, the same for every report."""
    exception, _, frames = parse(trace)
    text = "\n".join([exception, *(f"{c}.{m}" for c, m in signature_frames(frames))])
    return hashlib.sha256(text.encode()).hexdigest()[:12]


def title(trace: str) -> str:
    """The issue's title: the exception, and where the app's code (or the top) threw
    it."""
    exception, _, frames = parse(trace)
    where = signature_frames(frames)
    short = exception.rsplit(".", 1)[-1]
    if not where:
        return f"Crash: {short}"
    cls, method = where[0]
    return f"Crash: {short} in {cls.rsplit('.', 1)[-1]}.{method}"


# Play.


def play_session() -> AuthorizedSession:
    if os.environ.get("PLAY_ACCESS_TOKEN"):
        # A token already made, say by gcloud auth print-access-token for the service
        # account.
        credentials = Credentials(os.environ["PLAY_ACCESS_TOKEN"])
    else:
        credentials, _ = google.auth.default(scopes=[SCOPE])
    return AuthorizedSession(credentials)


def today() -> date:
    return datetime.now(UTC).date()


def interval(days: int, end: date | None = None) -> Params:
    """The search interval Play requires, as query parameters: the last days, to end
    (today)."""
    end = end or today()
    start = end - timedelta(days=days)
    params: Params = {}
    for name, day in (("startTime", start), ("endTime", end)):
        params[f"interval.{name}.year"] = day.year
        params[f"interval.{name}.month"] = day.month
        params[f"interval.{name}.day"] = day.day
    return params


def search(session: AuthorizedSession, what: str, params: Params) -> Iterator[Json]:
    """Every page of a search of errorIssues or errorReports.

    A search of reports has one, of one report per issue: Play samples them.
    """
    params = {**params, "pageSize": 100}
    while True:
        response = session.get(
            f"{REPORTING}/apps/{PACKAGE}/{what}:search", params=params
        )
        response.raise_for_status()
        body = response.json()
        yield from body.get(what, [])
        if not body.get("nextPageToken"):
            return
        params["pageToken"] = body["nextPageToken"]


def windows(days: int, end: date | None = None, size: int = 7) -> list[Params]:
    """The search intervals for reports.

    Play samples one report of an issue per search, so a search each week finds more of
    its devices and versions.
    """
    end = end or today()
    found = []
    while days > 0:
        span = min(size, days)
        found.append(interval(span, end=end))
        end -= timedelta(days=span)
        days -= span
    return found


def crashes(session: AuthorizedSession, days: int) -> Iterator[PlayIssue]:
    """Play's crash issues, each with its sampled reports, latest first."""
    issues = {**interval(days), "filter": "errorIssueType=CRASH"}
    for issue in search(session, "errorIssues", issues):
        issue_id = issue["name"].rsplit("/", 1)[-1]
        reports = []
        for window in windows(days):
            reports += search(
                session,
                "errorReports",
                {**window, "filter": f"errorIssueId={issue_id}"},
            )
        reports.sort(key=lambda r: r.get("eventTime", ""), reverse=True)
        yield issue, reports


# Mappings.

BUNDLE_MAPPING = "BUNDLE-METADATA/com.android.tools.build.obfuscation/proguard.map"


def bundle_mapping(artifact: bytes) -> bytes | None:
    """The Play build's R8 mapping from the dev workflow's artifact.

    It's in the bundle, where the build puts it for Play, or beside it.
    """
    with zipfile.ZipFile(io.BytesIO(artifact)) as archive:
        for name in archive.namelist():
            if name.endswith("mapping/playRelease/mapping.txt"):
                return archive.read(name)
        for name in archive.namelist():
            if name.endswith(".aab"):
                with zipfile.ZipFile(io.BytesIO(archive.read(name))) as bundle:
                    if BUNDLE_MAPPING in bundle.namelist():
                        return bundle.read(BUNDLE_MAPPING)
    return None


def version_code(report: Json) -> int | None:
    """The version code of the build a report came from."""
    code = report.get("appVersion", {}).get("versionCode")
    return int(code) if code else None


def revision(report: Json) -> str | None:
    """The commit the report's build was made from."""
    match = re.search(r'revision: "([0-9a-f]{40})"', report.get("vcsInformation", ""))
    return match.group(1) if match else None


def obfuscated(trace: str) -> bool:
    """Whether a trace still has R8's names.

    Play puts back the names of builds whose mapping it has (dev.yml and release.yml
    send it), so most don't, but a frame with R8's map id for its file shows Play didn't
    have the mapping.
    """
    return "r8-map-id-" in trace


class Mappings:
    """R8's mappings, by the commit the build was made from, fetched once each.

    They come from the GitHub release of a release's tag, or from the dev workflow's
    artifact for a development build.
    """

    def __init__(self, github: "GitHub | None", directory: str) -> None:
        self.github = github
        self.directory = Path(directory)
        self.files: dict[str | None, Path | None] = {}

    def get(self, sha: str | None) -> Path | None:
        if sha not in self.files:
            try:
                self.files[sha] = self.fetch(sha)
            except Exception as e:  # A missing mapping leaves the trace as Play has it.
                print(f"No mapping for {sha}: {e}", file=sys.stderr)
                self.files[sha] = None
        return self.files[sha]

    def fetch(self, sha: str | None) -> Path:
        if not self.github or not sha:
            raise LookupError("can't look for one")
        for tag in self.github.get_list("/tags", per_page=100):
            if tag["commit"]["sha"] == sha and tag["name"].startswith("v"):
                release = self.github.get(f"/releases/tags/{tag['name']}")
                for asset in release["assets"]:
                    if asset["name"].endswith("-play-mapping.txt"):
                        return self.save(sha, self.github.download(asset["url"]))
        runs = self.github.get("/actions/workflows/dev.yml/runs", head_sha=sha)
        for run in runs["workflow_runs"]:
            artifacts = self.github.get(f"/actions/runs/{run['id']}/artifacts")
            for artifact in artifacts["artifacts"]:
                if artifact["name"] == "dev-builds" and not artifact["expired"]:
                    data = self.github.download(
                        artifact["archive_download_url"], accept=None
                    )
                    mapping = bundle_mapping(data)
                    if mapping:
                        return self.save(sha, mapping)
        raise LookupError("no release or artifact has it")

    def save(self, sha: str, data: bytes) -> Path:
        path = self.directory / f"{sha}-mapping.txt"
        path.write_bytes(data)
        return path


def retrace_tool() -> str | None:
    if os.environ.get("RETRACE"):
        return os.environ["RETRACE"]
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if sdk:
        tool = Path(sdk) / "cmdline-tools" / "latest" / "bin" / "retrace"
        if tool.exists():
            return str(tool)
    return None


def retrace(trace: str, mapping: Path | None) -> str | None:
    """The trace with R8's names put back, or None if it can't be."""
    tool = retrace_tool()
    if not mapping or not tool:
        return None
    with tempfile.NamedTemporaryFile("w", suffix=".txt", delete=False) as f:
        f.write(re.sub(r"^(\s*at \S+) \(", r"\1(", trace, flags=re.M))
    try:
        result = subprocess.run(
            [tool, str(mapping), f.name], capture_output=True, text=True, check=False
        )
    finally:
        os.unlink(f.name)
    return result.stdout if result.returncode == 0 and result.stdout.strip() else None


# Grouping.


@dataclasses.dataclass
class Cluster:
    """One crash: the Play issues whose latest reports have its signature."""

    signature: str
    title: str
    # The latest report's trace, and whether it has the app's own names.
    trace: str
    readable: bool
    issues: list[PlayIssue] = dataclasses.field(default_factory=list)
    # When the latest report was.
    time: str = ""


def clusters(
    found: Iterable[PlayIssue], mapping: Callable[[str | None], Path | None]
) -> list[Cluster]:
    """Play's issues grouped by the crash in their latest report's trace.

    mapping gives the R8 mapping of a build, by its commit.
    """
    grouped: dict[str, Cluster] = {}
    for issue, reports in found:
        if not reports:
            continue
        latest = reports[0]
        raw = latest.get("reportText", "")
        readable = retrace(raw, mapping(revision(latest))) if obfuscated(raw) else raw
        trace = scrub(readable or raw)
        sig = signature(trace)
        cluster = grouped.setdefault(
            sig, Cluster(sig, title(trace), trace, readable is not None)
        )
        cluster.issues.append((issue, reports))
        time = latest.get("eventTime", "")
        if time > cluster.time:
            cluster.time, cluster.trace = time, trace
            cluster.readable = readable is not None
    return list(grouped.values())


def versions(cluster: Cluster) -> list[int]:
    codes = set()
    for issue, reports in cluster.issues:
        for key in ("firstAppVersion", "lastAppVersion"):
            code = issue.get(key, {}).get("versionCode")
            if code:
                codes.add(int(code))
        codes.update(c for c in map(version_code, reports) if c)
    return sorted(codes)


def plural(count: int, word: str) -> str:
    return word if count == 1 else word + "s"


def describe(cluster: Cluster, days: int = DAYS) -> str:
    """The issue's description: a summary of each Play issue, and the latest trace."""
    rows = []
    reports_total = users_total = 0
    devices = set()
    for issue, reports in sorted(
        cluster.issues, key=lambda i: -int(i[0].get("errorReportCount", 0))
    ):
        count = int(issue.get("errorReportCount", 0))
        users = int(issue.get("distinctUsers", 0))
        reports_total += count
        users_total += users
        first = issue.get("firstAppVersion", {}).get("versionCode", "?")
        last = issue.get("lastAppVersion", {}).get("versionCode", "?")
        seen = issue.get("lastErrorReportTime", "")[:10]
        issue_id = issue["name"].rsplit("/", 1)[-1]
        link = (
            f"[{issue_id}]({issue['issueUri']})" if issue.get("issueUri") else issue_id
        )
        rows.append(
            f"| {link} | {count} | {users} | {first}\N{EN DASH}{last} | {seen} |"
        )
        for report in reports:
            device = report.get("deviceModel", {})
            model = device.get("marketingName") or device.get("deviceId", {}).get(
                "buildDevice"
            )
            api = report.get("osVersion", {}).get("apiLevel")
            if model:
                devices.add(f"{model} (API {api})" if api else model)
    codes = versions(cluster)
    state = {"signature": cluster.signature, "max_version": max(codes, default=0)}
    note = (
        ""
        if cluster.readable
        else "\n_The trace still has R8's names: no mapping was found for its build._\n"
    )
    reports_seen = f"**{reports_total}** {plural(reports_total, 'report')}"
    users_seen = f"**{users_total}** {plural(users_total, 'user')}"
    codes_seen = f"{plural(len(codes), 'code')} {', '.join(map(str, codes))}"
    return "\n".join(
        [
            f"<!-- play-crash: {json.dumps(state, sort_keys=True)} -->",
            f"Google Play has {reports_seen} of this crash from up to {users_seen} in"
            f" the last {days} days, in version {codes_seen}.",
            "",
            "Play splits it into these issues:",
            "",
            "| Play issue | Reports | Users | Versions | Last report |",
            "|---|---|---|---|---|",
            *rows,
            "",
            f"Devices: {', '.join(sorted(devices)) or 'unknown'}.",
            "",
            f"Latest stack trace, from {cluster.time[:10]}:",
            note,
            "```",
            cluster.trace.strip(),
            "```",
            "",
            "_Filed and updated daily by scripts/play_crashes.py._",
        ]
    )


# GitHub.


class IssueTracker(Protocol):
    """Where the crashes are filed: GitHub, or in tests, a fake."""

    def crash_issues(self) -> dict[str, tuple[Json, Json]]: ...

    def send(self, method: str, path: str, body: Json) -> Json: ...


class GitHub:
    def __init__(self, repository: str, token: str) -> None:
        self.session = requests.Session()
        self.session.headers.update(
            {
                "Authorization": f"Bearer {token}",
                "Accept": "application/vnd.github+json",
                "X-GitHub-Api-Version": "2022-11-28",
            }
        )
        self.base = f"{GITHUB}/repos/{repository}"

    def get(self, path: str, **params: str | int) -> Json:
        response = self.session.get(self.base + path, params=params)
        response.raise_for_status()
        return response.json()

    def get_list(self, path: str, **params: str | int) -> list[Json]:
        response = self.session.get(self.base + path, params=params)
        response.raise_for_status()
        return response.json()

    def download(
        self, url: str, accept: str | None = "application/octet-stream"
    ) -> bytes:
        """A release asset's file, or with accept=None, an artifact's zip.

        GitHub redirects there for an artifact, and refuses the asset's Accept.
        """
        response = self.session.get(url, headers={"Accept": accept} if accept else {})
        response.raise_for_status()
        return response.content

    def send(self, method: str, path: str, body: Json) -> Json:
        response = self.session.request(method, self.base + path, json=body)
        response.raise_for_status()
        return response.json()

    def crash_issues(self) -> dict[str, tuple[Json, Json]]:
        """The issues filed before, open or closed, with their state, by signature."""
        found, page = {}, 1
        while True:
            issues = self.get_list(
                "/issues", labels=LABEL, state="all", per_page=100, page=page
            )
            for issue in issues:
                match = MARKER.search(issue.get("body") or "")
                if match:
                    state = json.loads(match.group(1))
                    found[state["signature"]] = (issue, state)
            if len(issues) < 100:
                return found
            page += 1


def file_issues(
    github: IssueTracker | None,
    found: list[Cluster],
    dry_run: bool,
    days: int = DAYS,
) -> None:
    """Files a new issue for each new crash, and updates the others."""

    def send(method: str, path: str, body: Json) -> None:
        if github is not None and not dry_run:
            github.send(method, path, body)

    filed = github.crash_issues() if github else {}
    label = {
        "name": LABEL,
        "color": "b60205",
        "description": "A crash Google Play reported",
    }
    with contextlib.suppress(requests.HTTPError):  # It's there already.
        send("POST", "/labels", label)
    for cluster in found:
        body = describe(cluster, days)
        before = filed.get(cluster.signature)
        if before is None:
            print(f"New: {cluster.title} ({cluster.signature})")
            send(
                "POST",
                "/issues",
                {"title": cluster.title, "body": body, "labels": [LABEL]},
            )
            continue
        issue, state = before
        newer = [c for c in versions(cluster) if c > state.get("max_version", 0)]
        change = {}
        if issue["state"] == "closed":
            if not newer:
                print(f"Closed, not seen in a newer version: #{issue['number']}")
                continue
            print(f"Reopening #{issue['number']}: seen again in {max(newer)}")
            change["state"] = "open"
            comment = (
                f"Google Play has this crash again, in version code {max(newer)}, "
                "newer than any it was seen in before."
            )
            send("POST", f"/issues/{issue['number']}/comments", {"body": comment})
        if issue.get("body") != body:
            print(f"Updating #{issue['number']}: {cluster.title}")
            change["body"] = body
        if change:
            send("PATCH", f"/issues/{issue['number']}", change)


def main() -> None:
    parser = argparse.ArgumentParser(description=(__doc__ or "").split("\n\n")[0])
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="print what would be filed or updated, and change nothing",
    )
    parser.add_argument("--days", type=int, default=DAYS)
    args = parser.parse_args()

    token = os.environ.get("GITHUB_TOKEN")
    repository = os.environ.get("GITHUB_REPOSITORY", "clementine-player/Android-Remote")
    github = GitHub(repository, token) if token else None
    if github is None and not args.dry_run:
        sys.exit("GITHUB_TOKEN is needed to file issues")

    with tempfile.TemporaryDirectory() as directory:
        mappings = Mappings(github, directory)
        found = clusters(crashes(play_session(), args.days), mappings.get)
        file_issues(github, found, args.dry_run, args.days)
        if args.dry_run:
            for cluster in found:
                print(f"\n=== {cluster.title}\n{describe(cluster, args.days)}")


if __name__ == "__main__":
    main()
