#!/usr/bin/env python3
"""
Assert what api-backend/firestore.rules allows, against Firebase's rules simulator.

    python3 tools/firestore_rules_test.py [--deployed]   # local file, or what is live

Needs application-default credentials. The simulator wants new data at `request.resource.data`,
not `request.data` — get that wrong and every rule reading `request.resource` throws, which reads
as a denial, so DENY cases pass for the wrong reason and ALLOW cases fail.
"""
from __future__ import annotations

import argparse
import json
import pathlib
import subprocess
import sys
import urllib.error
import urllib.request

PROJECT = "tibetan-keyboard"
# Resolved from this file, so the script runs from anywhere (npm runs it with cwd=api-backend).
RULES_FILE = str(pathlib.Path(__file__).resolve().parent.parent / "api-backend" / "firestore.rules")

UID = "abc123"
DOC = f"/databases/(default)/documents/users/{UID}"
OTHER = "/databases/(default)/documents/users/someone_else"
ANALYTICS = "/databases/(default)/documents/user_analytics/evt1"
JOURNEY = f"/databases/(default)/documents/journey_stats/{UID}"

PROFILE = {"uid": UID, "displayName": "T", "email": "t@e.com", "isSubscribed": False}
PRO_PROFILE = {**PROFILE, "isPro": True}


def case(name, expectation, method, path, auth=None, new=None, old=None):
    request = {"path": path, "method": method}
    if auth:
        request["auth"] = {"uid": auth}
    if new is not None:
        request["resource"] = {"data": new}
    test = {"expectation": expectation, "request": request, "pathEncoding": "PLAIN"}
    if old is not None:
        test["resource"] = {"data": old}
    return name, test


CASES = [
    # The exposure that was live.
    case("anonymous reads a profile", "DENY", "get", DOC),
    case("signed-in reads another user's profile", "DENY", "get", OTHER, auth=UID),
    case("writing someone else's profile", "DENY", "create", OTHER, auth=UID, new=PROFILE),
    # The entitlement the backend trusts.
    case("create smuggling isPro", "DENY", "create", DOC, auth=UID, new=PRO_PROFILE),
    case("update flipping isPro", "DENY", "update", DOC, auth=UID, new=PRO_PROFILE, old=PROFILE),
    # Admin-SDK-only collections.
    case("reads journey_stats", "DENY", "get", JOURNEY, auth=UID),
    case("deletes own profile", "DENY", "delete", DOC, auth=UID),
    # What the app genuinely does — these failing means sign-in is broken.
    case("signed-in reads own profile", "ALLOW", "get", DOC, auth=UID),
    case("login creates own profile", "ALLOW", "create", DOC, auth=UID, new=PROFILE),
    case("activity ping updates own profile", "ALLOW", "update", DOC, auth=UID,
         new={**PROFILE, "activity": {"activeDaysCount": 3}}, old=PROFILE),
    case("update leaving an existing isPro untouched", "ALLOW", "update", DOC, auth=UID,
         new={**PRO_PROFILE, "displayName": "T2"}, old=PRO_PROFILE),
    case("appends a product-analytics event", "ALLOW", "create", ANALYTICS, auth=UID,
         new={"event": "x"}),
]


def token() -> str:
    out = subprocess.run(
        ["gcloud", "auth", "application-default", "print-access-token"],
        capture_output=True, text=True, timeout=60,
    )
    if out.returncode != 0 or not out.stdout.strip():
        sys.exit("No access token. Run: gcloud auth application-default login")
    return out.stdout.strip()


def api(path: str, tok: str, body: dict | None = None, method: str = "GET") -> dict:
    req = urllib.request.Request(
        f"https://firebaserules.googleapis.com/v1/{path}",
        data=json.dumps(body).encode() if body else None,
        method=method,
        headers={"Authorization": f"Bearer {tok}", "Content-Type": "application/json",
                 "x-goog-user-project": PROJECT},
    )
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            return json.loads(resp.read())
    except urllib.error.HTTPError as e:
        sys.exit(f"{path} -> HTTP {e.code}\n{e.read().decode(errors='replace')}")


def deployed_source(tok: str) -> str:
    releases = api(f"projects/{PROJECT}/releases", tok)["releases"]
    ruleset = next(r for r in releases if r["name"].endswith("cloud.firestore"))["rulesetName"]
    return api(f"{ruleset}", tok)["source"]["files"][0]["content"]


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--deployed", action="store_true", help="test the live ruleset, not the file")
    args = ap.parse_args()

    tok = token()
    source = deployed_source(tok) if args.deployed else open(RULES_FILE).read()
    label = "deployed ruleset" if args.deployed else RULES_FILE

    names = [c[0] for c in CASES]
    tests = [c[1] for c in CASES]
    result = api(
        f"projects/{PROJECT}:test", tok, method="POST",
        body={"source": {"files": [{"name": "firestore.rules", "content": source}]},
              "testSuite": {"testCases": tests}},
    )

    for issue in result.get("issues", []):
        print("ISSUE:", issue.get("description"))

    failures = 0
    print(f"{label}\n")
    for name, test, outcome in zip(names, tests, result.get("testResults", [])):
        passed = outcome.get("state") == "SUCCESS"
        failures += not passed
        print(f"  [{'PASS' if passed else 'FAIL'}] expect {test['expectation']:<5} · {name}")
        if not passed and outcome.get("debugMessages"):
            print("         ", outcome["debugMessages"][0][:160])

    print(f"\n{len(CASES) - failures}/{len(CASES)} assertions pass")
    sys.exit(1 if failures else 0)


if __name__ == "__main__":
    main()
