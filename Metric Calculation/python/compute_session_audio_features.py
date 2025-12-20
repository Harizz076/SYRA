import json
import pandas as pd
import spotipy
from spotipy.oauth2 import SpotifyClientCredentials
import time
import os  # Import os to access environment variables

# =====================================================
# USER INPUTS
# =====================================================
session_id = input("Enter session_id: ")
participant_id = input("Enter participant_id: ")

track_jsonl_path = "test/track_info.jsonl"

# =====================================================
# SPOTIFY AUTH (SECURE)
# =====================================================

# Load keys from environment variables
# Set them in your terminal BEFORE running the script:
# export SPOTIFY_CLIENT_ID="your_new_id"
# export SPOTIFY_CLIENT_SECRET="your_new_secret"

SPOTIFY_CLIENT_ID = os.environ.get("SPOTIFY_CLIENT_ID")
SPOTIFY_CLIENT_SECRET = os.environ.get("SPOTIFY_CLIENT_SECRET")

if not SPOTIFY_CLIENT_ID or not SPOTIFY_CLIENT_SECRET:
    raise Exception("Spotify credentials not set in environment variables.")

auth_manager = SpotifyClientCredentials(
    client_id=SPOTIFY_CLIENT_ID,
    client_secret=SPOTIFY_CLIENT_SECRET
)
sp = spotipy.Spotify(auth_manager=auth_manager)


# =====================================================
# STEP 1 — Load track_info.jsonl
# =====================================================
track_ids = []

with open(track_jsonl_path, "r") as f:
    for line in f:
        try:
            entry = json.loads(line)

            if entry.get("deviceId") != "spotify":
                continue

            data = entry.get("data", {})
            track_id = data.get("trackId")
            is_paused = data.get("isPaused", True)

            # ===================
            #  THE FIX IS HERE 
            # ===================
            # keep only active, valid spotify playback rows
            if track_id and not is_paused and track_id.startswith("spotify:track:"):
                # track_id example = "spotify:track:0fAPcsD7qrEAfO2EH0ce4V"
                clean_id = track_id.replace("spotify:track:", "")
                track_ids.append(clean_id)
            
        except json.JSONDecodeError:
            print(f"Skipping bad JSON line: {line}")
            continue

track_ids = list(set(track_ids))   # unique
print(f"Found {len(track_ids)} unique tracks.")


# =====================================================
# STEP 2 — Fetch Spotify audio features
# =====================================================
def fetch_audio_features(tids):
    all_features = []
    for i in range(0, len(tids), 50):
        batch = tids[i:i+50]
        try:
            features = sp.audio_features(batch)
            if features: # Check if features is not None
                for f in features:
                    if f is not None:
                        all_features.append(f)
            time.sleep(0.1)
        except spotipy.exceptions.SpotifyException as e:
            print(f"Error fetching batch {i}: {e}")
            continue # Continue to the next batch
    
    if not all_features: # Check if list is empty
        return pd.DataFrame() # Return empty dataframe
        
    return pd.DataFrame(all_features)


if len(track_ids) == 0:
    print("No valid Spotify track IDs found in the file. Exiting.")
    exit()

audio_df = fetch_audio_features(track_ids)

if audio_df.empty:
    print("No audio features returned from Spotify. Exiting.")
    exit()

audio_df = audio_df.rename(columns={"id": "track_id"})


# =====================================================
# STEP 3 — Compute session-level mean features
# =====================================================
feature_names = [
    "energy",
    "valence",
    "tempo",
    "loudness",
    "danceability",
    "acousticness",
    "instrumentalness"
]

session_features = {}

for feat in feature_names:
    if feat in audio_df.columns:
        session_features[f"session_{feat}_mean"] = audio_df[feat].mean()
    else:
        session_features[f"session_{feat}_mean"] = None # or np.nan

session_features["session_track_count"] = len(audio_df)
session_features["session_id"] = session_id
session_features["participant_id"] = participant_id


# =====================================================
# STEP 4 — Save to CSV
# =====================================================
output_df = pd.DataFrame([session_features])

output_df.to_csv("session_audio_features.csv", index=False)

print("\nSaved session_audio_features.csv")
print(output_df)