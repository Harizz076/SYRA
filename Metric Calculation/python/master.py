#!/usr/bin/env python3
"""
MASTER SYRA PIPELINE — builds master.csv from all raw inputs.

Inputs:
    --session_id S01
    --participant_id P01
    --track_info track_info.jsonl
    --ecg ECG.jsonl
    --baseline baseline.csv
    --acc ACC.jsonl
    --pre pre.jsonl
    --post post.jsonl
    --session_start "2025-11-17T09:45:00Z"
    --session_end   "2025-11-17T10:30:00Z"

Output:
    master.csv (single row)

Columns exactly match the LMM requirements.
"""

import argparse
import json
import time
import numpy as np
import pandas as pd
from pathlib import Path
from scipy.signal import butter, filtfilt, find_peaks
import spotipy
from spotipy.oauth2 import SpotifyClientCredentials


# ============================================================
# --------------  SPOTIFY HELPERS  ----------------------------
# ============================================================
SPOTIFY_CLIENT_ID = "YOUR_SPOTIFY_CLIENT_ID"
SPOTIFY_CLIENT_SECRET = "YOUR_SPOTIFY_CLIENT_SECRET"


def fetch_spotify_features(track_ids):
    """Fetch audio features for a list of track IDs using Spotify API."""
    auth = SpotifyClientCredentials(
        client_id=SPOTIFY_CLIENT_ID,
        client_secret=SPOTIFY_CLIENT_SECRET
    )
    sp = spotipy.Spotify(auth_manager=auth)

    all_features = []
    for i in range(0, len(track_ids), 50):
        batch = track_ids[i:i + 50]
        features = sp.audio_features(batch)
        for f in features:
            if f is not None:
                all_features.append(f)
        time.sleep(0.1)

    df = pd.DataFrame(all_features)
    df = df.rename(columns={"id": "track_id"})
    return df


# ============================================================
# --------------  AUDIO SESSION FEATURES  ----------------------
# ============================================================
def process_track_info(track_info_jsonl):
    """Extract unique track IDs from track_info.jsonl."""
    track_ids = []
    with open(track_info_jsonl, "r") as f:
        for line in f:
            if not line.strip():
                continue
            obj = json.loads(line)
            data = obj.get("data", {})
            if data.get("isPaused") is False:
                tid = data.get("trackId", "")
                tid = tid.replace("spotify:track:", "")
                track_ids.append(tid)

    return list(set(track_ids))


def compute_session_audio_features(track_ids, audio_df):
    """Return simple and weighted means of Spotify features."""
    if len(track_ids) == 0:
        return {}

    df = audio_df[audio_df["track_id"].isin(track_ids)]
    if len(df) == 0:
        return {}

    # Simple means
    out = {
        "session_energy_mean": df["energy"].mean(),
        "session_valence_mean": df["valence"].mean(),
        "session_tempo_mean": df["tempo"].mean(),
        "session_loudness_mean": df["loudness"].mean(),
        "session_danceability_mean": df["danceability"].mean(),
        "session_acousticness_mean": df["acousticness"].mean(),
        "session_instrumentalness_mean": df["instrumentalness"].mean(),
    }

    # Weighted: no durations → same as simple means
    for k in list(out.keys()):
        out[k.replace("_mean", "_weighted")] = out[k]

    out["track_ids"] = "|".join(track_ids)
    return out


# ============================================================
# --------------  ECG + HRV PROCESSING  -----------------------
# ============================================================
def bandpass_filter(sig, fs, lowcut=5.0, highcut=40.0, order=3):
    nyq = fs * 0.5
    b, a = butter(order, [lowcut / nyq, highcut / nyq], btype="band")
    return filtfilt(b, a, sig)


def process_ecg(ecg_jsonl, baseline_csv):
    """Compute all HRV metrics + deltas."""
    volt, ts = [], []
    with open(ecg_jsonl, "r") as f:
        for line in f:
            obj = json.loads(line)
            for d in obj.get("data", []):
                if "voltage" in d and "timeStamp" in d:
                    volt.append(float(d["voltage"]))
                    ts.append(int(d["timeStamp"]))

    volt, ts = np.array(volt), np.array(ts)

    # Infer timestamp unit
    dt = np.median(np.diff(ts))
    if dt > 1e6:
        scale = 1e9
    elif dt > 1e3:
        scale = 1e6
    elif dt > 1:
        scale = 1e3
    else:
        scale = 1.0

    t = (ts - ts[0]) / scale
    fs = 1 / np.median(np.diff(t))

    sig = volt - np.mean(volt)
    filtered = bandpass_filter(sig, fs)

    # Detect peaks
    diff_signal = np.ediff1d(filtered, to_begin=0) ** 2
    ma = np.convolve(diff_signal, np.ones(30) / 30, "same")
    peaks, _ = find_peaks(ma, distance=int(0.25 * fs))

    peak_times = t[peaks]
    rr_ms = np.diff(peak_times) * 1000
    if len(rr_ms) < 2:
        return {}

    HR = 60000 / rr_ms

    # Session HRV
    hr_mean = HR.mean()
    hr_max = HR.max()
    hr_slope = np.polyfit(np.arange(len(HR)), HR, 1)[0]
    rmssd = np.sqrt(np.mean(np.diff(rr_ms)**2))
    sdnn = rr_ms.std(ddof=1)

    # Baseline
    baseline_df = pd.read_csv(baseline_csv)
    base_rr_ms = baseline_df["duration"].values.astype(float)
    base_hr = 60000 / base_rr_ms
    base_rmssd = np.sqrt(np.mean(np.diff(base_rr_ms)**2))

    delta_hr = hr_mean - base_hr.mean()
    delta_rmssd = rmssd - base_rmssd

    return {
        "HR_mean_session": hr_mean,
        "HR_max_session": hr_max,
        "HR_slope_session": hr_slope,
        "RMSSD_session": rmssd,
        "SDNN_session": sdnn,
        "HRV_window_count": len(rr_ms),
        "baseline_available": True,
        "HR_mean_baseline": base_hr.mean(),
        "RMSSD_baseline": base_rmssd,
        "delta_HR": delta_hr,
        "delta_RMSSD": delta_rmssd
    }


# ============================================================
# --------------  ACC PROCESSING  -----------------------------
# ============================================================
def process_acc(acc_jsonl):
    xs, ys, zs = [], [], []

    with open(acc_jsonl, "r") as f:
        for line in f:
            obj = json.loads(line)
            for d in obj.get("data", []):
                if all(k in d for k in ("x", "y", "z")):
                    xs.append(float(d["x"]))
                    ys.append(float(d["y"]))
                    zs.append(float(d["z"]))

    xs, ys, zs = np.array(xs), np.array(ys), np.array(zs)
    mag = np.sqrt(xs**2 + ys**2 + zs**2)

    mean_mag = mag.mean()
    sd_mag = mag.std()

    if mean_mag < 400:
        flag = "low"
    elif mean_mag < 800:
        flag = "medium"
    else:
        flag = "high"

    peaks, _ = find_peaks(mag, prominence=sd_mag * 0.3)
    steps = len(peaks)

    return {
        "accel_mean_session": mean_mag,
        "accel_sd_session": sd_mag,
        "movement_flag": flag,
        "steps_session": steps
    }


# ============================================================
# --------------  ESM PROCESSING  -----------------------------
# ============================================================
def read_single_jsonl(path):
    with open(path, "r") as f:
        for line in f:
            if line.strip():
                return json.loads(line)
    return None


def process_esm(pre_jsonl, post_jsonl, session_id):
    pre = read_single_jsonl(pre_jsonl)
    post = read_single_jsonl(post_jsonl)

    pre_a = pre.get("energy", None)
    pre_v = pre.get("pleasantness", None)
    post_a = post.get("energy", None)
    post_v = post.get("pleasantness", None)

    out = {
        "pre_esm_id": f"{session_id}_PRE",
        "pre_arousal": pre_a,
        "pre_valence": pre_v,
        "post_esm_id": f"{session_id}_POST",
        "post_arousal": post_a,
        "post_valence": post_v,
        "liking_post": post.get("music_liking"),
        "familiarity_post": post.get("music_familiarity"),
        "delta_arousal": post_a - pre_a if pre_a is not None else None,
        "delta_valence": post_v - pre_v if pre_v is not None else None,
    }
    return out


# ============================================================
# --------------  MAIN MASTER PIPELINE  ------------------------
# ============================================================
def main():
    p = argparse.ArgumentParser()
    p.add_argument("--session_id", required=True)
    p.add_argument("--participant_id", required=True)
    p.add_argument("--track_info", required=True)
    p.add_argument("--ecg", required=True)
    p.add_argument("--baseline", required=True)
    p.add_argument("--acc", required=True)
    p.add_argument("--pre", required=True)
    p.add_argument("--post", required=True)
    p.add_argument("--session_start", required=True)
    p.add_argument("--session_end", required=True)
    p.add_argument("--output", default="master.csv")
    args = p.parse_args()

    # -----------------------------
    # TIME METADATA
    # -----------------------------
    start = pd.to_datetime(args.session_start)
    end = pd.to_datetime(args.session_end)
    duration_s = (end - start).total_seconds()

    # -----------------------------
    # AUDIO
    # -----------------------------
    track_ids = process_track_info(args.track_info)
    audio_df = fetch_spotify_features(track_ids)
    audio_metrics = compute_session_audio_features(track_ids, audio_df)

    # -----------------------------
    # ECG HRV
    # -----------------------------
    hrv = process_ecg(args.ecg, args.baseline)

    # -----------------------------
    # ACC
    # -----------------------------
    acc = process_acc(args.acc)

    # -----------------------------
    # ESM
    # -----------------------------
    esm = process_esm(args.pre, args.post, args.session_id)

    # -----------------------------
    # BUILD FINAL ROW
    # -----------------------------
    row = {
        "session_id": args.session_id,
        "participant_id": args.participant_id,
        "session_start_utc": args.session_start,
        "session_end_utc": args.session_end,
        "session_duration_s": duration_s,
        **audio_metrics,
        **hrv,
        **acc,
        **esm,
        "panas_pos": "",
        "panas_neg": "",
        "bmrq_reward": "",
        "bmrq_emotion": "",
        "msi_training": "",
        "msi_engagement": "",
        "age": "",
        "gender": "",
        "rr_missing_pct": "",
        "accel_missing_pct": "",
        "ble_disconnect": "",
        "notes": ""
    }

    # -----------------------------
    # SAVE
    # -----------------------------
    df = pd.DataFrame([row])
    df.to_csv(args.output, index=False)

    print(f"\nSaved → {args.output}")
    print(df)


if __name__ == "__main__":
    main()
