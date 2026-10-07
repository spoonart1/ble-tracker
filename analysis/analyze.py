#!/usr/bin/env python3
"""Summarise one or more BLE Proximity Lab CSV logs.

Usage: python analyze.py ble_log_*.csv
Reports per (mode, device state): sighting rate, gap percentiles, loss events, RSSI, battery drain.
"""
import csv, sys, statistics as st
from collections import defaultdict


def state(row):
    if row["screen_on"] == "1":
        return "screen_on"
    return "screen_off_locked" if row["locked"] == "1" else "screen_off_unlocked"


def pct(vals, p):
    if not vals:
        return float("nan")
    vals = sorted(vals)
    return vals[min(len(vals) - 1, int(round(p / 100 * (len(vals) - 1))))]


def main(paths):
    for path in paths:
        rows = list(csv.DictReader(open(path)))
        if not rows:
            continue
        print(f"\n=== {path} ===")
        t0, t1 = int(rows[0]["ts_ms"]), int(rows[-1]["ts_ms"])
        print(f"duration: {(t1 - t0) / 60000:.1f} min, rows: {len(rows)}")

        gaps = defaultdict(list)       # (mode,state) -> gaps between consecutive sightings (ms)
        rssi = defaultdict(list)
        count = defaultdict(int)
        losses = defaultdict(int)
        refound = defaultdict(list)    # latency to re-detect after a loss
        for r in rows:
            key = (r["mode"], state(r))
            if r["event"] in ("SIGHTING", "FOUND"):
                count[key] += 1
                if r["rssi"]:
                    rssi[key].append(int(r["rssi"]))
                if r["event"] == "SIGHTING" and r["gap_ms"]:
                    gaps[key].append(int(r["gap_ms"]))
                if r["event"] == "FOUND" and r["note"].startswith("re-found"):
                    refound[key].append(int(r["gap_ms"]))
            elif r["event"] == "LOST":
                losses[key] += 1

        print(f"\n{'mode':<11}{'state':<21}{'n':>6}{'gap p50':>9}{'p95':>9}{'max':>9}{'lost':>6}{'rssi avg':>9}")
        for key in sorted(set(count) | set(losses)):
            g = gaps[key]
            print(f"{key[0]:<11}{key[1]:<21}{count[key]:>6}"
                  f"{pct(g, 50):>9.0f}{pct(g, 95):>9.0f}{(max(g) if g else float('nan')):>9.0f}"
                  f"{losses[key]:>6}{(st.mean(rssi[key]) if rssi[key] else float('nan')):>9.1f}")
        for key, v in refound.items():
            print(f"re-detect after loss {key}: median {st.median(v) / 1000:.1f}s, max {max(v) / 1000:.1f}s")

        hb = [r for r in rows if r["event"] == "HEARTBEAT" and r["charge_uah"] not in ("", "0")]
        if len(hb) >= 2:
            hours = (int(hb[-1]["ts_ms"]) - int(hb[0]["ts_ms"])) / 3.6e6
            d_uah = int(hb[0]["charge_uah"]) - int(hb[-1]["charge_uah"])
            d_pct = int(hb[0]["battery_pct"]) - int(hb[-1]["battery_pct"])
            if hours > 0:
                print(f"\nbattery: -{d_pct}% ({d_uah / 1000:.1f} mAh) over {hours:.2f} h "
                      f"=> {d_pct / hours:.2f} %/h, {d_uah / 1000 / hours:.1f} mAh/h  (run unplugged!)")


if __name__ == "__main__":
    main(sys.argv[1:]) if len(sys.argv) > 1 else print(__doc__)
