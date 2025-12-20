#!/usr/bin/env python3
"""
Compute accelerometer session metrics from ACC.jsonl

Output columns:
accel_mean_session
accel_sd_session
movement_flag       # low / medium / high
steps_session       # estimated from magnitude peaks
"""

import json
import numpy as np
import pandas as pd
from scipy.signal import find_peaks
import argparse


# -------------------------------------------------------
# READ ACC JSONL
# -------------------------------------------------------
def read_acc_jsonl(path):
    xs, ys, zs = [], [], []

    with open(path, "r", encoding="utf-8") as f:
        for line in f:
            if not line.strip():
                continue

            obj = json.loads(line)
            data = obj.get("data", [])

            for d in data:
                # raw integer flux values
                x = d.get("x")
                y = d.get("y")
                z = d.get("z")

                if x is None or y is None or z is None:
                    continue

                xs.append(float(x))
                ys.append(float(y))
                zs.append(float(z))

    if len(xs) == 0:
        raise ValueError("No accelerometer samples found.")

    return np.array(xs), np.array(ys), np.array(zs)


# -------------------------------------------------------
# MOVEMENT FLAG (low / medium / high)
# -------------------------------------------------------
def movement_category(accel_mag):
    mean_val = np.mean(accel_mag)

    # thresholds chosen empirically; adjust if necessary
    if mean_val < 400:
        return "low"
    elif mean_val < 800:
        return "medium"
    else:
        return "high"


# -------------------------------------------------------
# STEP ESTIMATION
# -------------------------------------------------------
def estimate_steps(accel_mag, prominence_factor=0.3):
    if len(accel_mag) < 2:
        return 0

    # Dynamic prominence relative to signal
    prom = np.std(accel_mag) * prominence_factor

    peaks, _ = find_peaks(accel_mag, prominence=prom, distance=10)

    return len(peaks)


# -------------------------------------------------------
# MAIN
# -------------------------------------------------------
def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--acc", required=True, help="ACC.jsonl file")
    parser.add_argument("--output", default="accel_session_metrics.csv", help="Output CSV")
    args = parser.parse_args()

    # Read raw ACC samples
    xs, ys, zs = read_acc_jsonl(args.acc)

    # Compute magnitude
    accel_mag = np.sqrt(xs**2 + ys**2 + zs**2)

    # Compute metrics
    accel_mean = np.mean(accel_mag)
    accel_sd = np.std(accel_mag, ddof=1)
    flag = movement_category(accel_mag)
    steps = estimate_steps(accel_mag)

    # Prepare output row
    out = pd.DataFrame([{
        "accel_mean_session": accel_mean,
        "accel_sd_session": accel_sd,
        "movement_flag": flag,
        "steps_session": steps
    }])

    out.to_csv(args.output, index=False)
    print(f"Saved → {args.output}")
    print(out)


if __name__ == "__main__":
    main()
