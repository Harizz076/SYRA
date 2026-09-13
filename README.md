# SYRA — Signal-based Yield for Responses to Audio

**An offline-first Android research app for studying affect, music listening, and physiological response in everyday life.**

`Android` · `Kotlin` · `Jetpack Compose` · `Polar BLE` · `Ecological Momentary Assessment`

> SYRA is a research prototype, not a medical device. It is not intended to diagnose, treat, or monitor a health condition.

## About the project

Most studies of music and emotion happen in controlled laboratory settings. SYRA brings data collection into participants’ daily lives: it captures how people feel, what they listen to, and—when a wearable is connected—how their bodies respond in the moment.

The app brings together three streams of information in one exportable session:

1. **Affect** — brief valence–arousal and music-experience questionnaires.
2. **Listening context** — playback state and metadata from supported music apps.
3. **Physiology** — optional heart rate, ECG, accelerometer, and compatible PPG data from Polar BLE devices.

All collection is local-first. Session data is saved on the device as JSONL files and can be exported as a ZIP archive for analysis.

### Core capabilities

- Connect to compatible Polar sensors, including H10, OH1+, and Verity Sense.
- Detect playback from an allowlist of dedicated music applications through Android’s `MediaSessionManager`.
- Run music-triggered, random ESM, or physiology-focused study protocols.
- Present pre-listening, post-listening, and scheduled random questionnaires.
- Keep collection active during supported background scenarios using foreground services and alarm-based prompts.
- Store encrypted local study metadata and export participant sessions in a portable format.

### Built with

- Kotlin and Jetpack Compose
- Android SDK 35 (minimum Android 7.0 / API 24)
- Coroutines, Room, and SQLCipher
- Polar BLE SDK 6.2.0
- Android MediaSession, AlarmManager, and foreground services

## Getting started

### Prerequisites

- Android Studio with an installed Android SDK and a JDK compatible with the project’s Java 11 target.
- An Android 7.0+ device for deployment and testing.
- A compatible Polar BLE sensor for physiology collection.
- A supported music app for music-triggered studies.

### Installation and setup

Clone the repository and open the Android project:

```bash
git clone https://github.com/<your-github-username>/SYRA.git
cd SYRA/app
```

Open `app/` in Android Studio, allow Gradle to sync, then run the `debug` configuration on a connected device. The equivalent command-line workflow is:

```bash
./gradlew assembleDebug
./gradlew installDebug
```

On first launch, complete setup in the app:

1. Enter a participant identifier and select a study mode.
2. Select a local storage location for session exports.
3. Grant Bluetooth and notification permissions when prompted.
4. For music-triggered modes, enable **Notification Access** for SYRA in Android Settings.
5. If prompted, exempt SYRA from battery optimisation to reduce OEM background restrictions.

## Usage

Choose the protocol that matches your study design:

| Mode | What it does | Sensor required |
| --- | --- | --- |
| **Triggered** | Starts a session when a supported music app begins playing. | No |
| **Triggered + Random ESM** | Combines playback-triggered sessions with scheduled prompts. | No |
| **Random ESM** | Sends scheduled affect prompts without monitoring media playback. | No |
| **Physiology** | Records streams from a connected Polar sensor. | Yes |

For a typical music-triggered study, select a mode, start playing music in a supported app, complete the pre-session prompt, and listen as normal. After playback stops, complete the post-session prompt and export the completed session from the app settings.

Exports may contain `ECG.jsonl`, `ACC.jsonl`, `track_info.jsonl`, `pre.jsonl`, `post.jsonl`, and an application log. These files may contain sensitive physiological and self-report data; handle them under your approved research-data procedures.

## Project structure

```text
SYRA/
├── app/                           # Android Studio / Gradle project
│   └── app/src/main/
│       ├── java/                  # App, services, managers, UI, persistence
│       ├── assets/questionnaires/ # JSON-defined questionnaires
│       └── res/                   # Android resources
├── LICENSE
├── NOTICE                          # Required third-party attribution
└── README.md
```

## Quality and privacy

- SYRA is designed for local-first collection and does not declare the Android `INTERNET` permission.
- Use an ethics-approved protocol, obtain informed consent, and comply with your institution’s data-governance requirements before collecting participant data.
- Run local unit tests from `app/` with `./gradlew testDebugUnitTest`.
- Run static analysis from `app/` with `./gradlew detekt`.
- Device logs, pilot-session reports, and other research diagnostics are intentionally excluded from version control.

## Roadmap

- [ ] Add lifecycle and hardware-in-the-loop regression tests for BLE, session recovery, and survey handoff.
- [ ] Strengthen durable event buffering and recovery after interrupted sessions.
- [ ] Add CI for Android builds, tests, and static analysis.
- [ ] Publish de-identified example exports and analysis documentation.
- [ ] Add screenshots and a short demo video.

## License

SYRA is released under the [MIT License](LICENSE). See [NOTICE](NOTICE) for third-party and upstream attribution information that must be retained in redistributions.

## Contact

For questions, collaboration, or bug reports, [open a GitHub issue](../../issues) or email [Shaikh Haris Jamal](mailto:shaikh.jamal@research.iiit.ac.in).

## Acknowledgements

- [Polar Electro](https://github.com/polarofficial/polar-ble-sdk) for the Polar BLE SDK.
