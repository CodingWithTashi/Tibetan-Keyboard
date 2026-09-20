#!/usr/bin/env python3
"""
Price a subscription base plan from another one, region by region.

Play's bulk price dialog converts one price at market FX, which breaks against a source plan
priced on local purchasing-power tiers — annual set from USD 19.99 came out dearer than 12x
monthly in 165 of 170 regions. This reads the source plan's real regional price and scales it.

    python3 tools/play_pricing.py --ratio 6.67 [--apply]
    python3 tools/play_pricing.py --target-kind onetime --target lifetime_premium_unlock \\
        --target-plan lifetime-unlock --ratio 16.67 --max-months 24 --fix-above-months 20

Auth: gcloud auth application-default login \\
          --scopes=https://www.googleapis.com/auth/androidpublisher
(a plain `gcloud auth login` token is cloud-platform-scoped and 403s).
"""
from __future__ import annotations

import argparse
import json
import subprocess
import sys
import urllib.error
import urllib.request
from decimal import ROUND_HALF_UP, Decimal

API = "https://androidpublisher.googleapis.com/androidpublisher/v3"
PACKAGE = "com.kharagedition.tibetankeyboard"
# Play validates every price against a dated region/currency table and rejects anything older
# than the current one.
REGIONS_VERSION = "2025/03"

# Subscriptions and one-time products hold the same idea (a plan with a price per region) behind
# entirely different names and endpoints, so everything below works through this table.
KINDS = {
    "subscription": {
        "path": "subscriptions",
        "plans": "basePlans",
        "plan_id": "basePlanId",
        "configs": "regionalConfigs",
    },
    "onetime": {
        "path": "oneTimeProducts",
        "plans": "purchaseOptions",
        "plan_id": "purchaseOptionId",
        "configs": "regionalPricingAndAvailabilityConfigs",
        # One-time products have no PATCH — writes go through a batch endpoint that wraps the
        # whole product in a request envelope.
        "batch": True,
    },
}

# Currencies with no minor unit — a price of "100" means 100, not 1.00. Rounding to X.99 would
# be meaningless (and rejected) for these.
ZERO_DECIMAL = {
    "BIF", "CLP", "DJF", "GNF", "JPY", "KMF", "KRW", "MGA", "PYG",
    "RWF", "UGX", "VND", "VUV", "XAF", "XOF", "XPF",
}


# --------------------------------------------------------------------------------------- money


def money_to_decimal(m: dict) -> Decimal:
    """Play's Money {units, nanos} -> Decimal. units is a string int64; nanos is 1e-9 of a unit."""
    return Decimal(m.get("units", "0")) + Decimal(m.get("nanos", 0)) / Decimal(1_000_000_000)


def decimal_to_money(value: Decimal, currency: str) -> dict:
    units = int(value)
    nanos = int((value - units) * 1_000_000_000)
    return {"currencyCode": currency, "units": str(units), "nanos": nanos}


def charm(value: Decimal, currency: str, source: Decimal) -> Decimal:
    """
    Round a computed price to something a store would actually show.

    Zero-decimal currencies round to a whole number; a source price that is itself whole (INR
    60.00) keeps its target whole too, so INR 400 does not become INR 399.99; everything else
    lands on X.99.
    """
    if currency in ZERO_DECIMAL:
        step = Decimal(100) if value >= 1000 else Decimal(10)
        return (value / step).quantize(Decimal(1), rounding=ROUND_HALF_UP) * step

    source_is_whole = source == source.to_integral_value()
    if source_is_whole:
        return value.quantize(Decimal(1), rounding=ROUND_HALF_UP)

    whole = value.quantize(Decimal(1), rounding=ROUND_HALF_UP)
    candidate = whole - Decimal("0.01")
    return candidate if candidate > 0 else Decimal("0.99")


# ----------------------------------------------------------------------------------------- api


def token() -> str:
    for args in (
        ["gcloud", "auth", "application-default", "print-access-token"],
        ["gcloud", "auth", "print-access-token"],
    ):
        try:
            out = subprocess.run(args, capture_output=True, text=True, timeout=60)
            if out.returncode == 0 and out.stdout.strip():
                return out.stdout.strip()
        except (OSError, subprocess.SubprocessError):
            continue
    sys.exit(
        "No access token. Run:\n"
        "  gcloud auth application-default login "
        "--scopes=https://www.googleapis.com/auth/androidpublisher"
    )


QUOTA_PROJECT: str | None = None


def call(method: str, path: str, tok: str, body: dict | None = None) -> dict:
    headers = {
        "Authorization": f"Bearer {tok}",
        "Content-Type": "application/json",
    }
    # User (ADC) credentials have no project of their own, so Google needs one told to it for
    # quota and for the API-enabled check. A header keeps it to this process rather than
    # rewriting the machine's gcloud config.
    if QUOTA_PROJECT:
        headers["x-goog-user-project"] = QUOTA_PROJECT
    req = urllib.request.Request(
        f"{API}{path}",
        method=method,
        data=json.dumps(body).encode() if body is not None else None,
        headers=headers,
    )
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            return json.loads(resp.read() or "{}")
    except urllib.error.HTTPError as e:
        detail = e.read().decode(errors="replace")
        if e.code == 403 and "SCOPE" in detail:
            sys.exit(
                "403: the token lacks the androidpublisher scope. Run:\n"
                "  gcloud auth application-default login "
                "--scopes=https://www.googleapis.com/auth/androidpublisher"
            )
        sys.exit(f"{method} {path} -> HTTP {e.code}\n{detail}")


def find_plan(product: dict, kind: dict, plan_id: str) -> dict:
    for plan in product.get(kind["plans"], []):
        if plan.get(kind["plan_id"]) == plan_id:
            return plan
    sys.exit(f"plan '{plan_id}' not found; have: "
             f"{[p.get(kind['plan_id']) for p in product.get(kind['plans'], [])]}")


# ---------------------------------------------------------------------------------------- main


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--package", default=PACKAGE)
    ap.add_argument("--quota-project", default="tibetan-keyboard",
                    help="GCP project billed for the API call (ADC user creds need one)")
    ap.add_argument("--regions-version", default=REGIONS_VERSION,
                    help="Play regions/currency table to validate against. Must be the latest one "
                         "Play knows, or currencies that have since changed (Bulgaria BGN -> EUR) "
                         "are rejected. Play names the current value in the 400 it returns.")
    ap.add_argument("--source", default="monthly_premium_subscription",
                    help="subscription whose regional prices are the reference")
    ap.add_argument("--source-plan", default="base-renew-plan")
    ap.add_argument("--target", default="annual_premium_subscription")
    ap.add_argument("--target-plan", default="annual-renew-plan")
    ap.add_argument("--target-kind", choices=sorted(KINDS), default="subscription",
                    help="'onetime' for a one-time product such as the lifetime unlock")
    ap.add_argument("--ratio", type=Decimal, required=True,
                    help="target = source x ratio (6.67 = annual at ~44%% off paying monthly)")
    ap.add_argument("--max-months", type=Decimal, default=Decimal(12),
                    help="fail if the target ever costs more than this many months of the source")
    ap.add_argument("--fix-above-months", type=Decimal, default=Decimal(10),
                    help="only reprice regions whose CURRENT target already costs more than this "
                         "many months of the source. Regions that are already a sensible discount "
                         "(the hand-checked US and India prices) are left exactly as they are.")
    ap.add_argument("--all", action="store_true",
                    help="reprice every region, not just the broken ones")
    ap.add_argument("--apply", action="store_true", help="write the prices (default: dry run)")
    args = ap.parse_args()

    global QUOTA_PROJECT
    QUOTA_PROJECT = args.quota_project

    tok = token()

    src_kind = KINDS["subscription"]
    dst_kind = KINDS[args.target_kind]

    src = call("GET", f"/applications/{args.package}/{src_kind['path']}/{args.source}", tok)
    dst = call("GET", f"/applications/{args.package}/{dst_kind['path']}/{args.target}", tok)

    src_plan = find_plan(src, src_kind, args.source_plan)
    dst_plan = find_plan(dst, dst_kind, args.target_plan)

    src_prices = {
        c["regionCode"]: c["price"]
        for c in src_plan.get(src_kind["configs"], [])
        if c.get("price")
    }
    if not src_prices:
        sys.exit(f"no regional prices on {args.source}/{args.source_plan}")

    rows, new_configs, violations = [], [], []
    for cfg in dst_plan.get(dst_kind["configs"], []):
        region = cfg["regionCode"]
        src_price = src_prices.get(region)
        if not src_price:
            # Target sells somewhere the source does not; leave that region untouched.
            new_configs.append(cfg)
            continue

        currency = src_price["currencyCode"]
        monthly = money_to_decimal(src_price)
        current = money_to_decimal(cfg["price"]) if cfg.get("price") else None
        current_months = (current / monthly) if (current is not None and monthly) else None

        # Leave regions alone when their current price is already a real discount — that covers
        # the ones checked by hand, and every untouched region is one less thing to re-verify.
        keep = (
            not args.all
            and current_months is not None
            and current_months <= args.fix_above_months
        )
        if keep:
            new_configs.append(cfg)
            rows.append((region, currency, monthly, current, current_months, current, False))
            continue

        target = charm(monthly * args.ratio, currency, monthly)
        months = (target / monthly) if monthly else Decimal(0)

        if months > args.max_months:
            violations.append((region, currency, monthly, target, months))

        rows.append((region, currency, monthly, target, months, current, True))
        updated = dict(cfg)
        updated["price"] = decimal_to_money(target, currency)
        new_configs.append(updated)

    rows.sort(key=lambda r: r[0])
    changed = [r for r in rows if r[6]]
    print(f"{'REGION':<8}{'CUR':<5}{'MONTHLY':>12}{'PRICE':>14}{'WAS':>14}{'= MONTHS':>10}  ")
    print("-" * 66)
    for region, currency, monthly, target, months, was, is_changed in rows:
        was_s = f"{was:,.2f}" if was is not None else "-"
        mark = "CHANGE" if is_changed else ""
        print(f"{region:<8}{currency:<5}{monthly:>12,.2f}{target:>14,.2f}{was_s:>14}"
              f"{months:>9.1f}x  {mark}")

    print(f"\n{len(rows)} regions inspected · {len(changed)} would change · "
          f"{len(rows) - len(changed)} already a sensible discount and left alone")

    if violations:
        print(f"\nREFUSING: {len(violations)} region(s) would cost more than "
              f"{args.max_months} months of the source plan:")
        for region, currency, monthly, target, months in violations:
            print(f"  {region} {currency} {monthly:,.2f}/mo -> {target:,.2f} ({months:.1f}x)")
        sys.exit(1)

    worst = max(rows, key=lambda r: r[4])
    print(f"worst ratio: {worst[0]} at {worst[4]:.1f} months "
          f"(saving {100 - worst[4] / 12 * 100:.0f}% vs paying monthly)")

    if not args.apply:
        print("\nDry run — nothing written. Re-run with --apply.")
        return

    dst_plan[dst_kind["configs"]] = new_configs
    if dst_kind.get("batch"):
        call(
            "POST",
            f"/applications/{args.package}/{dst_kind['path']}:batchUpdate",
            tok,
            {
                "requests": [{
                    "oneTimeProduct": dst,
                    "updateMask": dst_kind["plans"],
                    "regionsVersion": {"version": args.regions_version},
                }]
            },
        )
    else:
        call(
            "PATCH",
            f"/applications/{args.package}/{dst_kind['path']}/{args.target}"
            f"?updateMask={dst_kind['plans']}&regionsVersion.version={args.regions_version}",
            tok,
            dst,
        )
    print(f"\nApplied to {args.target}/{args.target_plan}.")


if __name__ == "__main__":
    main()
