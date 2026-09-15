<div align="center">

<img src="images/mockx_logo.png" alt="MockX Logo" width="160"/>

# MockX

**A modern Android location-spoofing app + LSPosed module — with built-in Chinese map sources, GCJ-02 auto-correction, and hardened anti-leak hooks.**

**English** | [简体中文](README.zh-CN.md)

[![GitHub License](https://img.shields.io/github/license/Souitou-iop/XposedFakeLocation?style=for-the-badge&color=red&logo=googledocs&logoColor=red)](https://github.com/Souitou-iop/XposedFakeLocation/blob/master/LICENSE)
![Platform](https://img.shields.io/badge/platform-Android%2010%2B-green.svg?style=for-the-badge&logo=android)
![Xposed API](https://img.shields.io/badge/Xposed%20API-101%2B-8A2BE2.svg?style=for-the-badge&logo=x)
![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-7F52FF.svg?style=for-the-badge&logo=kotlin)

</div>

---

> [!NOTE]
> MockX is a maintained fork of [noobexon1/XposedFakeLocation](https://github.com/noobexon1/XposedFakeLocation), rebranded and substantially upgraded — new application id (`io.github.souitou.mockx`), a full Miuix-style UI, domestic Chinese map sources, GCJ-02 ↔ WGS-84 correction, a 120 Hz-ready interface, and a multi-channel anti-leak hook chain verified against AMap / Baidu / Tencent Maps.

> [!IMPORTANT]
> **This module targets the modern libxposed API (Xposed API 101+).** You need a recent **LSPosed** build that supports the new API — older managers will not load the module. Get the latest LSPosed from the official Telegram channel: **[t.me/LSPosed](https://t.me/LSPosed)**.

## Table of Contents

- [Features](#features)
- [Prerequisites](#prerequisites)
- [Installation](#installation)
- [Usage](#usage)
- [External Control](#external-control)
- [Development](#development)
- [Documentation](#documentation)
- [Contributing](#contributing)
- [License](#license)
- [Disclaimer](#disclaimer)
- [Acknowledgements](#acknowledgements)

---

## Features

- **Per-App Location Spoofing** — pick target apps inside MockX; your selection drives the LSPosed module scope automatically, so you never manage scope by hand.
- **Optional System-Level Hooks** — extend spoofing into `system_server` and `com.android.phone` for deeper coverage (including MIUI/HyperOS blur-location services), via a single toggle.
- **Domestic Map Sources, Zero Key** — switch between AMap vector, AMap satellite, Tianditu vector (custom token), and OpenStreetMap. AMap tiles load instantly in mainland China with no API key.
- **GCJ-02 ↔ WGS-84 Auto-Correction** — tap any point on a GCJ-02 (AMap) basemap and get a precisely back-calculated WGS-84 coordinate (fixed-point iteration, millimeter-level accuracy). Markers and the "my location" dot are re-projected live, so pins align perfectly with roads and buildings.
- **Anti-Leak Hardening** — while spoofing is active, all location leak channels are sealed: Wi-Fi scan results cleared & BSSID/SSID masked, cell tower info emptied and cell event listeners masked, NMEA / GNSS status / raw GNSS measurement registrations suppressed, and `isFromMockProvider` / `isMock` always return `false`. Solves the classic "map app jumps back to the real location after 2 seconds" problem.
- **1 Hz Heartbeat Dispatch** — a background loop pushes fresh fake locations to every registered `LocationListener` every second, preventing map SDKs from timing out and falling back to network positioning.
- **Fine-Tuned Sensor Spoofing** — customize horizontal/vertical accuracy, altitude, mean sea level (and its accuracy), speed (and its accuracy), and GPS noise.
- **Randomization** — scatter your location within a configurable radius to mimic real-world movement (uniform sampling over the circle via the Haversine formula).
- **Reactive Updates** — force-stop a target app only the **first** time it enters the scope. After that, every change in MockX (location, settings, start/stop) reflects in the running target app immediately.
- **Root Relaunch** — force-stop and relaunch a target app straight from the Target Apps screen (requires root).
- **Headless / External Control** — drive the module from another app or `adb shell` via broadcast intents (off by default).
- **120/144 Hz UI** — the app requests the highest refresh rate matching the physical resolution and injects frame-rate hints into the view tree, so Compose list screens stay smooth on HyperOS / ColorOS / OriginOS.
- **Native Miuix UI** — built on the official `compose-miuix-ui` component library: squircle corners, springy switches, glassy micro-islands and system-grade dialogs.
- **Multi-Language** — English, Simplified Chinese, and German, switchable in-app.

## Prerequisites

- **Rooted Android device** (required by LSPosed).
- **Android 10+** (API 29).
- **Modern LSPosed (new API)** — the module is built against the libxposed API (Xposed API 101+). Download the latest from the official Telegram channel: **[t.me/LSPosed](https://t.me/LSPosed)**. Legacy `Xposed` / `EdXposed` and older LSPosed managers are **not** supported.

## Installation

Build from source (or grab an APK from the [releases](https://github.com/Souitou-iop/XposedFakeLocation/releases) page if available):

```shell
git clone https://github.com/Souitou-iop/XposedFakeLocation.git
cd XposedFakeLocation
./gradlew assembleRelease          # arm64-v8a only, ~4.5 MB
# or: ./gradlew assembleDebug
adb install app/build/outputs/apk/release/app-release.apk
```

Then:

1. Open a recent **LSPosed Manager** that supports the new API and enable the **MockX** module; reboot once.
2. Open MockX and select target apps on the **Target Apps** screen — the module's LSPosed scope updates automatically. Do **not** edit the scope manually in LSPosed.
3. **(Optional)** For system-level hooks (`system_server` + `com.android.phone`), enable **Enable system-level hooks** in Settings, then reboot (reboot again after turning it off).

## Usage

1. **Map** — tap anywhere to place the spoof target; use the top-right menu to switch map sources; jump to exact coordinates or save favorites.
2. **Target Apps** — search and select the apps that should receive spoofed locations; use the relaunch button (root) to apply immediately on first add.
3. **Settings** — fine-tune spoofing values, Wi-Fi identity, map source, language, theme, and toggles.
4. **Play/Stop** — the FAB toggles spoofing. Only apps selected in Target Apps see the fake location; everything else keeps real data.
5. First time an app is added: force-stop and reopen it once (relaunch button or manually) so the module gets injected. After that, all changes are live.

## External Control

Optionally let any app or `adb shell` control spoofing headlessly (Settings → External Control, off by default):

```shell
adb shell am broadcast \
  -a io.github.souitou.mockx.action.START \
  -n io.github.souitou.mockx/.manager.control.ControlReceiver \
  --ed latitude 37.7749 --ed longitude -122.4194
```

Actions: `io.github.souitou.mockx.action.START` / `.STOP` / `.SET_LOCATION`. Inputs are hard-validated (lat/lon ranges, accuracy ≤ 100,000 m). See [`docs/EXTERNAL_CONTROL.md`](docs/EXTERNAL_CONTROL.md) for details.

## Development

```shell
./gradlew testDebugUnitTest    # unit tests (coordinate transform, Wi-Fi policy)
./gradlew assembleRelease      # minified release (debug-signed)
./gradlew assembleRelease -PtargetAbi=all   # all ABIs
```

Requirements: JDK 21, Android SDK with compileSdk 36. See the [Code Wiki](docs/wiki/Home.md) for the full architecture walkthrough.

## Documentation

A complete structured Code Wiki lives in [`docs/wiki/`](docs/wiki/Home.md):

| Doc | Content |
| :--- | :--- |
| [Architecture](docs/wiki/01-Architecture.md) | Three process forms, data flow, hook dispatch |
| [Manager App](docs/wiki/02-Manager-App.md) | UI screens, map/GCJ-02 pipeline, high refresh rate |
| [Xposed Module](docs/wiki/03-Xposed-Module.md) | Entry point and every hook class in detail |
| [Data Layer](docs/wiki/04-Data-Layer.md) | Constants, models, remote/local preference stores |
| [Key Classes Reference](docs/wiki/05-Key-Classes-Reference.md) | Lookup table of key classes & functions |
| [Dependencies](docs/wiki/06-Dependencies.md) | Tech stack and dependency rules |
| [Build & Run](docs/wiki/07-Build-And-Run.md) | Environment, commands, CI, troubleshooting |

## Contributing

Contributions are welcome! Please read [`CONTRIBUTING.md`](CONTRIBUTING.md) for the project structure, coding guidelines, and the pull request process.

## License

Distributed under the `MIT License`. See [`LICENSE`](LICENSE) for more information.

## Disclaimer

This application is intended for **development and testing purposes only**. Misuse of location spoofing can violate the terms of service of other applications and services. Use at your own risk. There is no responsibility whatsoever for any damage to the device.

## Acknowledgements

- [noobexon1/XposedFakeLocation](https://github.com/noobexon1/XposedFakeLocation) — the upstream project this fork is based on.
- [GpsSetter](https://github.com/Android1500/GpsSetter) — the original project was highly inspired by this.
- [libxposed API](https://github.com/libxposed/api) — the modern Xposed API this module is built on.
- [LSPosed](https://github.com/LSPosed/LSPosed) ([Telegram](https://t.me/LSPosed)) — the go-to Xposed framework manager app.
- [OSMDroid](https://github.com/osmdroid/osmdroid) — the open-source map engine.
- [compose-miuix-ui](https://github.com/miuix-kotlin-multiplatform/miuix) — the Miuix component library.
- [Jetpack Compose](https://developer.android.com/jetpack/compose) & [Material Design 3](https://m3.material.io/) — modern UI toolkit and design system.
- [Line Awesome Icons](https://icons8.com/line-awesome) — the icon set used in the app.
- [FuckLocation](https://github.com/Mikotwa/FuckLocation) — reference for additional Android location hook handling.
