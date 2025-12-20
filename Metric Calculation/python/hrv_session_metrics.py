#!/usr/bin/env python3
"""
Compute HRV session metrics from ECG.jsonl + baseline.csv

Outputs:
HR_mean_session, HR_max_session, HR_slope_session,
RMSSD_session, SDNN_session, HRV_window_count,
baseline_available, HR_mean_baseline, RMSSD_baseline,
delta_HR, delta_RMSSD
"""

import json
import numpy as np
import pandas as pd
from scipy.signal import butter, filtfilt, find_peaks
import argparse
from pathlib import Path
import sys


# =========================================================
# READ ECG.jsonl (same as your original method)
# =========================================================
def read_jsonl(path):
    voltages, timestamps = [], []
    with open(path, "r", encoding="utf-8") as f:
        for line in f:
            if not line.strip():
                continue
            obj = json.loads(line)
            for s in obj.get("data", []):
                v = s.get("voltage")
                t = s.get("timeStamp")
                if v is None or t is None:
                    continue
                voltages.append(float(v))
                timestamps.append(int(t))

    if not voltages:
        raise ValueError("No ECG samples found.")

    return np.array(voltages), np.array(timestamps)


# =========================================================
# BANDPASS FILTER
# =========================================================
def bandpass_filter(sig, fs, lowcut=5.0, highcut=40.0, order=3):
    nyq = 0.5 * fs
    low, high = lowcut / nyq, highcut / nyq
    b, a = butter(order, [low, high], btype="band")
    return filtfilt(b, a, sig)


# =========================================================
# R-PEAK DETECTION (Pan–Tompkins style)
# =========================================================
def detect_r_peaks(sig, t, fs):
    diff = np.ediff1d(sig, to_begin=0)
    squared = diff ** 2

    # 120 ms moving average
    win = max(1, int(round(0.12 * fs)))
    ma = np.convolve(squared, np.ones(win)/win, mode="same")

    min_dist = int(round(0.25 * fs))
    peaks, _ = find_peaks(ma, distance=min_dist, prominence=np.mean(ma))

    refined = []
    half = int(round(0.05 * fs))
    for p in peaks:
        lo = max(0, p-half)
        hi = min(len(sig)-1, p+half)
        local = np.argmax(sig[lo:hi+1]) + lo
        refined.append(local)

    refined = np.array(sorted(set(refined)))
    times = t[refined]

    return refined, times


# =========================================================
# HRV METRICS FROM RR
# =========================================================
def compute_hrv(rr_ms):
    if len(rr_ms) < 2:
        return None

    rr_s = rr_ms / 1000.0

    HR = 60000 / rr_ms  
    HR_mean = np.mean(HR)
    HR_max = np.max(HR)

    # slope of HR across session
    t = np.arange(len(HR))
    slope = np.polyfit(t, HR, 1)[0] if len(t) > 1 else 0

    # RMSSD
    diffs = np.diff(rr_ms)
    RMSSD = np.sqrt(np.mean(diffs**2))

    # SDNN
    SDNN = np.std(rr_ms, ddof=1)

    return {
        "HR_mean_session": HR_mean,
        "HR_max_session": HR_max,
        "HR_slope_session": slope,
        "RMSSD_session": RMSSD,
        "SDNN_session": SDNN,
        "HRV_window_count": len(rr_ms)
    }


# =========================================================
# BASELINE HRV METRICS
# =========================================================
def compute_baseline_baselines(baseline_csv):
    df = pd.read_csv(baseline_csv)
    if len(df) == 0:
        return None

    rr_ms = df["duration"].values.astype(float)

    HR = 60000 / rr_ms
    HR_mean = np.mean(HR)

    diffs = np.diff(rr_ms)
    RMSSD = np.sqrt(np.mean(diffs**2)) if len(diffs) > 0 else np.nan

    return {
        "baseline_available": True,
        "HR_mean_baseline": HR_mean,
        "RMSSD_baseline": RMSSD
    }


# =========================================================
# MAIN
# =========================================================
def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--ecg", required=True, help="ECG.jsonl file")
    parser.add_argument("--baseline", required=True, help="baseline.csv (RR intervals)")
    parser.add_argument("--output", default="hrv_session_metrics.csv", help="Output CSV")
    args = parser.parse_args()

    ecg_path = Path(args.ecg)
    base_path = Path(args.baseline)

    if not ecg_path.exists():
        print("ECG file not found.")
        sys.exit(1)
    if not base_path.exists():
        print("Baseline CSV not found.")
        sys.exit(1)

    # ---------------------------
    # LOAD ECG JSONL
    # ---------------------------
    volt, ts = read_jsonl(ecg_path)

    # detect time units
    raw_diffs = np.diff(ts)
    median_dt = np.median(raw_diffs)

    if median_dt > 1e6:
        scale = 1e9
    elif median_dt > 1e3:
        scale = 1e6
    elif median_dt > 1:
        scale = 1e3
    else:
        scale = 1.0

    t_sec = (ts - ts[0]) / scale
    fs = 1.0 / np.median(np.diff(t_sec))

    sig = volt - np.mean(volt)
    filtered = bandpass_filter(sig, fs)

    # detect peaks
    idx, beat_times = detect_r_peaks(filtered, t_sec, fs)

    if len(beat_times) < 2:
        print("Not enough beats found.")
        sys.exit(1)

    rr_s = np.diff(beat_times)
    rr_ms = rr_s * 1000.0
    rr_ms = np.round(rr_ms).astype(float)

    # -------------------------------------
    # SESSION HRV METRICS
    # -------------------------------------
    session_metrics = compute_hrv(rr_ms)

    # -------------------------------------
    # BASELINE METRICS
    # -------------------------------------
    baseline = compute_baseline_baselines(base_path)

    if baseline is None:
        session_metrics.update({
            "baseline_available": False,
            "HR_mean_baseline": np.nan,
            "RMSSD_baseline": np.nan,
            "delta_HR": np.nan,
            "delta_RMSSD": np.nan
        })
    else:
        session_metrics.update(baseline)
        # deltas
        session_metrics["delta_HR"] = session_metrics["HR_mean_session"] - baseline["HR_mean_baseline"]
        session_metrics["delta_RMSSD"] = session_metrics["RMSSD_session"] - baseline["RMSSD_baseline"]

    # -------------------------------------
    # SAVE OUTPUT CSV
    # -------------------------------------
    out_df = pd.DataFrame([session_metrics])
    out_df.to_csv(args.output, index=False)

    print(f"\nSaved → {args.output}")
    print(out_df)


if __name__ == "__main__":
    main()
