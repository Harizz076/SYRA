# SYRA: Physiological Data Collection in Naturalistic Settings

SYRA is a comprehensive research platform designed to bridge the gap between controlled laboratory experiments and real-world music listening experiences. It enables the synchronized collection of high-fidelity physiological data, music playback metadata, and subjective emotional reports in naturalistic settings.

This project addresses a critical need in music psychology and affective computing: understanding how music influences physiology and emotion in daily life, outside the artificial constraints of a lab.

## Scientific Context & Motivation

Traditional research on music and emotion often relies on participants listening to short excerpts in sterile laboratory environments. While this offers control, it lacks ecological validity. SYRA allows researchers to study:
- **Naturalistic Listening**: How people respond to their own music in their own environments.
- **Physiological Entrainment**: How heart rate and movement synchronize with musical features (tempo, energy) in real-time.
- **Emotional Regulation**: How music is used to regulate mood in daily life, captured via Ecological Momentary Assessment (EMA).

## Key Features

### Android Application (Data Collection)
The Android app serves as the central hub for data recording, ensuring synchronization between multiple data streams.

*   **Multi-Sensor Integration**: Connects to Polar heart rate sensors (H10, OH1+, Verity Sense) via Bluetooth Low Energy (BLE) to capture:
    *   **ECG (Electrocardiogram)**: Raw voltage data (130Hz) for precise heart rate variability (HRV) analysis.
    *   **ACC (Accelerometer)**: 3-axis motion data (200Hz) to track physical activity and filter motion artifacts.
    *   **PPG (Photoplethysmography)**: Optical heart rate data (OH1+/Verity Sense).
*   **Spotify Real-Time Tracking**: Uses the Spotify App Remote SDK to log:
    *   Track Metadata (ID, Name, Artist, Album, URI).
    *   Playback State (Play/Pause events, Seek positions).
*   **Ecological Momentary Assessment (EMA)**:
    *   **Pre-Session Survey**: Captures baseline Affect (Valence/Arousal) before music starts.
    *   **Post-Session Survey**: Captures emotional response, music liking, and familiarity after listening.
    *   **7-Point Likert Scales**: Standardized inputs for psychometric analysis.
*   **Resilience & Usability**:
    *   **Auto-Reconnect**: Automatically attempts to re-establish BLE connections if lost.
    *   **Background Recording**: Continues data collection even when the screen is off or the app is minimized.
    *   **Local Storage**: Saves all data locally in efficient, line-delimited JSON (JSONL) format.

### Analysis Pipeline (Python)
A production-ready Python pipeline transforms raw sensor logs into a unified dataset for statistical modeling (e.g., Linear Mixed Models).

*   **Signal Processing**:
    *   **ECG Filtering**: Bandpass filtering (5-40Hz) to remove baseline wander and high-frequency noise.
    *   **R-Peak Detection**: Robust algorithm to identify R-peaks and calculate RR intervals.
*   **Feature Extraction**:
    *   **HRV Metrics**: Calculates RMSSD (Root Mean Square of Successive Differences) and SDNN (Standard Deviation of NN intervals) to index parasympathetic nervous system activity.
    *   **Audio Features**: Fetches acoustic features (Energy, Valence, Tempo, Danceability, Acousticness) from the Spotify Web API.
    *   **Movement Classification**: Categorizes activity levels (Low/Medium/High) based on accelerometer magnitude.
*   **Data Fusion**: Aligns physiological time-series with music tracks and survey responses into a single "Master Row" per session.

## User Workflow

1.  **Setup**: User wears the Polar device and ensures it is paired with the phone.
2.  **Start Session**: User opens SYRA. The app automatically scans for the known Polar device.
3.  **Pre-Survey**: Upon connection, the user completes a brief "How do you feel?" survey (Valence/Arousal).
4.  **Listening**: The user switches to Spotify and listens to music naturally. SYRA records in the background.
5.  **End Session**: When finished, the user stops the recording in SYRA.
6.  **Post-Survey**: A final survey asks about the music experience and current emotional state.
7.  **Export**: The session data is zipped and can be shared/exported for analysis.

## Data Schema & Output

Data is exported as a ZIP archive containing the following files:

| File | Format | Description |
| :--- | :--- | :--- |
| `ECG.jsonl` | JSONL | Raw ECG voltage samples with timestamps. `{"data": [{"voltage": 123, "timeStamp": 1234567890}]}` |
| `ACC.jsonl` | JSONL | Raw Accelerometer data (x, y, z). `{"data": [{"x": 0.1, "y": 0.2, "z": 9.8, "timeStamp": ...}]}` |
| `track_info.jsonl` | JSONL | Spotify track metadata and playback state changes. |
| `pre.jsonl` | JSONL | Responses from the pre-session survey (Valence, Arousal). |
| `post.jsonl` | JSONL | Responses from the post-session survey (Valence, Arousal, Liking, Familiarity). |
| `baseline.csv` | CSV | (Optional) Baseline physiological data for normalization. |

## Technology Stack

*   **Android**: Kotlin, Jetpack Compose (UI), Coroutines, Room Database, Polar BLE SDK, Spotify App Remote SDK.
*   **Python**: Pandas (Dataframes), NumPy (Math), SciPy (Signal Processing), Spotipy (API Wrapper).

## Setup & Installation

### Android App
1.  **Prerequisites**: Android Device (Android 10+), Polar H10/OH1 sensor, Spotify Premium account (required for SDK).
2.  **Installation**:
    *   Clone the repo: `git clone https://github.com/yourusername/SYRA.git`
    *   Open `app/` in Android Studio.
    *   Add your Spotify Client ID in `SurveyActivity.kt` and `MainActivity.kt`.
    *   Build and deploy to your device.

### Analysis Pipeline
1.  **Environment**:
    ```bash
    cd "Metric Calculation/python"
    pip install pandas numpy scipy spotipy
    ```
2.  **Configuration**:
    *   Edit `master.py` to include your Spotify Developer Credentials (`SPOTIFY_CLIENT_ID`, `SPOTIFY_CLIENT_SECRET`).
3.  **Running the Pipeline**:
    ```bash
    python master.py --session_id S01 --participant_id P01 \
      --track_info /path/to/track_info.jsonl \
      --ecg /path/to/ECG.jsonl \
      --acc /path/to/ACC.jsonl \
      --pre /path/to/pre.jsonl \
      --post /path/to/post.jsonl \
      --session_start "2023-10-27T10:00:00Z" \
      --session_end "2023-10-27T11:00:00Z"
    ```

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Acknowledgments

*   **Polar Electro**: For the open-source [Polar BLE SDK](https://github.com/polarofficial/polar-ble-sdk).
*   **Spotify**: For the [App Remote SDK](https://developer.spotify.com/documentation/android).
*   **Original Project**: Based on [Polar Recorder](https://github.com/boelensman1/polarrecorder).
