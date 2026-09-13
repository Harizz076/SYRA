<p align="center">
  <img src="app/app/src/main/ic_launcher-playstore.png" width="150" alt="SYRA app icon: an ECG trace, audio waveform, and musical note" />
</p>

<h1 align="center">SYRA</h1>

<p align="center">
  <strong>Signal-based Yield for Responses to Audio</strong><br />
  A research tool for understanding how people respond to audio in everyday life.
</p>

<p align="center">
  <img src="https://img.shields.io/badge/platform-Android-3DDC84?logo=android&logoColor=white" alt="Platform: Android" />
  <img src="https://img.shields.io/badge/language-Kotlin-7F52FF?logo=kotlin&logoColor=white" alt="Language: Kotlin" />
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4" alt="UI: Jetpack Compose" />
  <img src="https://img.shields.io/badge/license-MIT-2ea44f" alt="License: MIT" />
</p>

<p align="center">
  <a href="#getting-started">Get started</a> |
  <a href="#usage">Usage</a> |
  <a href="#license">License</a> |
  <a href="#contact">Contact</a>
</p>

<h2 align="center">See SYRA in action</h2>

<p align="center">
  From setup and study configuration to music-triggered prompts and in-the-moment surveys.
</p>

<table align="center">
  <tr>
    <td align="center">
      <img src="docs/images/1.jpg" alt="SYRA home dashboard" width="150" /><br />
      <strong>Home</strong><br />
      Check sensor status and start a session.
    </td>
    <td align="center">
      <img src="docs/images/2.jpg" alt="SYRA setup and export screen" width="150" /><br />
      <strong>Setup</strong><br />
      Configure recording and export settings.
    </td>
    <td align="center">
      <img src="docs/images/3.jpg" alt="SYRA questionnaire screen" width="150" /><br />
      <strong>Questionnaires</strong><br />
      Complete structured study measures.
    </td>
  </tr>
  <tr>
    <td align="center">
      <img src="docs/images/4.jpg" alt="Music-triggered SYRA notification" width="150" /><br />
      <strong>Music prompt</strong><br />
      Receive a prompt when listening begins.
    </td>
    <td align="center">
      <img src="docs/images/5.jpg" alt="SYRA pre-music affect survey" width="150" /><br />
      <strong>Momentary affect</strong><br />
      Record how you feel before listening.
    </td>
    <td align="center">
      <img src="docs/images/6.jpg" alt="Music playback on an Android device" width="150" /><br />
      <strong>Listening context</strong><br />
      Capture music as it happens in daily life.
    </td>
  </tr>
</table>

## Why SYRA?

Music-and-emotion studies often use short, researcher-selected excerpts in controlled labs. That gives researchers useful control, but it can leave out the everyday moments when people choose music, live with it, and use it to shape how they feel.

SYRA is built for those everyday moments. It keeps data collection on the participant's device and brings momentary self-report, listening context, and optional wearable data together in one exportable session.

```mermaid
flowchart LR
    A["Listening context<br/>Playback state and metadata"] --> D["SYRA session<br/>Local JSONL export"]
    B["Momentary affect<br/>EMA questionnaires"] --> D
    C["Physiology<br/>Optional Polar BLE sensor"] --> D

    classDef listening fill:#DCE8F2,stroke:#4A6585,color:#1E2E40,stroke-width:2px;
    classDef affect fill:#F5E7C6,stroke:#C76B00,color:#3A2A0A,stroke-width:2px;
    classDef physiology fill:#DDEDE4,stroke:#2E6E4E,color:#173D2B,stroke-width:2px;
    classDef session fill:#2F4865,stroke:#1E3048,color:#FFFFFF,stroke-width:3px;

    class A listening;
    class B affect;
    class C physiology;
    class D session;
    linkStyle default stroke:#718397,stroke-width:2px;
```

## Features

| Capability | What SYRA provides |
| --- | --- |
| **Wearable integration** | Records heart rate, ECG, accelerometer, and compatible PPG streams from Polar BLE devices. |
| **Music context** | Watches playback and metadata from supported music apps through Android `MediaSessionManager`. |
| **EMA protocols** | Runs pre-listening, post-listening, and scheduled random questionnaires, including valence-arousal reporting. |
| **Study modes** | Supports music-triggered, random ESM, and physiology-focused protocols. |
| **Local-first storage** | Saves JSONL session data and encrypted study metadata locally, then exports a ZIP when you are ready. |
| **Background collection** | Uses a foreground sensor service and alarm-based prompts where Android permits background operation. |

## Built with

`Kotlin` · `Jetpack Compose` · `Coroutines` · `Room + SQLCipher` · `Polar BLE SDK` · `Android MediaSession` · `AlarmManager`

## Getting started

### Prerequisites

- Android Studio with an installed Android SDK and a JDK compatible with the project’s Java 11 target.
- An Android 7.0+ device for deployment and testing.
- A compatible Polar BLE sensor for physiology collection.
- A supported music app for music-triggered studies.

### Install

```bash
git clone git@github.com:Harizz076/SYRA.git
cd SYRA/app

# Build and install a debug APK on a connected Android device
./gradlew assembleDebug
./gradlew installDebug
```

Alternatively, open the `app/` directory in Android Studio, allow Gradle to sync, and run the `debug` configuration on a connected device.

## Usage

Choose the protocol that matches your study design:

| Mode | Behaviour | Sensor required |
| --- | --- | :---: |
| **Triggered** | Starts a session when a supported music app begins playing. | No |
| **Triggered + Random ESM** | Combines playback-triggered sessions with scheduled prompts. | No |
| **Random ESM** | Sends scheduled affect prompts without media monitoring. | No |
| **Physiology** | Records supported streams from a connected Polar sensor. | Yes |

For a music-triggered session, choose a mode, start listening in a supported app, answer the pre-session prompt, and listen normally. When playback ends, answer the post-session prompt and export the session from the app settings.

### Export format

SYRA exports a ZIP archive containing JSONL files such as:

```text
session-export.zip
├── ECG.jsonl          # Raw ECG samples, when available
├── ACC.jsonl          # Accelerometer samples, when available
├── track_info.jsonl   # Playback-state and track-metadata events
├── pre.jsonl          # Pre-session affect responses
├── post.jsonl         # Post-session affect and music-experience responses
└── LOG.jsonl          # Application diagnostic events
```

## Project structure

```text
SYRA/
├── app/                           # Android Studio / Gradle project
│   └── app/src/main/
│       ├── java/                  # App, services, managers, UI, and persistence
│       ├── assets/questionnaires/ # JSON-defined questionnaires
│       └── res/                   # Android resources
├── docs/images/                   # Screenshots used by this README
├── LICENSE
├── NOTICE                          # Required third-party attribution
└── README.md
```

## License

SYRA is released under the [MIT License](LICENSE). See [NOTICE](NOTICE) for third-party and upstream attribution information that must be retained in redistributions.

## Contact

Have a question, an idea, or a bug to report? Open a [GitHub issue](https://github.com/Harizz076/SYRA/issues) or email [shaikh.jamal@research.iiit.ac.in](mailto:shaikh.jamal@research.iiit.ac.in).

## Acknowledgements

- [Polar Electro](https://github.com/polarofficial/polar-ble-sdk) for the Polar BLE SDK.
