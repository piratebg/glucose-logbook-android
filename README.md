# Glucose Logbook for Android

An independent, community-developed glucose logbook, currently named **Bio Track** in the Android app. Import readings from a compatible Bluetooth glucose meter, keep them locally, and choose when to write them to Android Health Connect.

**Experimental preview, version 0.3.0.** One meter model has community-reported testing: Accu-Chek Instant. Wider device compatibility, background reliability and clinical validation have not been established. This project is not affiliated with Roche, mySugr, Google or any meter manufacturer.

## Why this project exists

Bio Track was built for people who want an open-source, local-first alternative with direct Health Connect glucose sharing, visible import status, duplicate-resistant syncing and detailed reports they can export themselves. It separates collecting readings from sharing them, defaults to mmol/L, and lets users inspect and modify the implementation.

Those are this project's design priorities, not a claim that it is clinically superior to mySugr or that other apps are defective. Other products have different features, support and validation. We do not claim that mySugr universally lacks Health Connect support: availability can change by version, platform and region. See the [comparison and evidence](docs/COMPARISON.md).

## Features

- Bluetooth LE discovery, Android bonding, serialized subscriptions, bounded retries and full-history recovery.
- Durable local storage, incremental history requests with overlap and duplicate checks.
- Optional, user-triggered **blood glucose writes to Health Connect**, with stable export identifiers for retries. This is not a direct Google Fit, Google Health cloud or Zepp integration.
- mmol/L by default, optional mg/dL display, configurable targets and editable meal context/notes.
- Calendar-date/source/tag filters; mean, median, quartiles, population SD and descriptive sampled-reading CV.
- Recorded-reading timeline, 24-hour overlay, hourly/weekday/meal summaries, coverage and daily logbooks.
- Offline PDF review reports and full/filtered CSV export. Percentages describe recorded readings, not CGM time in range. No GMI, estimated HbA1c or insulin dosing is calculated.
- No app account, advertising or analytics service; the app's merged manifest does not request Internet access. User-selected export providers and Health Connect have their own data handling.

## Build and run

Install Android Studio, use JDK 17 or 21, and install Android SDK Platform 36 and Build Tools 36.0.0. Open the **`android/`** folder and let Gradle sync.

Windows PowerShell:

```powershell
cd android
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:installDebug
```

Linux/macOS:

```sh
cd android
chmod +x gradlew
./gradlew :app:assembleDebug
./gradlew :app:installDebug
```

`installDebug` needs a running emulator or a USB-debugging-authorized Android device. The APK is generated at `android/app/build/outputs/apk/debug/app-debug.apk`. Android 8.0/API 26 is the app minimum; Health Connect requires a supported newer OS/provider. BLE testing needs real hardware.

Read [BUILDING.md](BUILDING.md) for SDK setup, first pairing, Health Connect permissions, testing, troubleshooting and signing. Every self-build normally has the builder's own debug signature. Do not uninstall an existing app containing readings to bypass a signature mismatch; export data first and see the signing guidance.

## Validation and limitations

The development baseline passed 28 JVM tests and 10 Android emulator tests: protocol parsing, statistics, date/DST boundaries, filters, persistence, schema migration, annotation/export receipt preservation, scrolling and PDF generation/screens. This is engineering validation, not clinical certification or a guarantee of meter accuracy.

Health Connect writing and the Instant meter workflow have been reported working in a community setup. This does not validate all firmware, phones, Android releases or other meter models. See [compatibility](docs/COMPATIBILITY.md), [calculation methods](docs/REPORTING.md) and [safety](SAFETY.md).

## Privacy, license and independence

- [Privacy policy](PRIVACY.md): local storage, permissions, exports and deletion.
- [Safety and intended use](SAFETY.md): informational record keeping; verify against the meter; no treatment decisions or emergency monitoring.
- [Legal and trademark notice](LEGAL.md): no manufacturer endorsement, regulatory-clearance or non-infringement guarantee.
- [MIT License](LICENSE) for original project content; [third-party notices](THIRD_PARTY_NOTICES.md) for dependencies and Gradle tooling.
- [Source provenance](docs/PROVENANCE.md): AI-assisted development and the limited use of an owner-supplied earlier preview for behavior analysis. This is not represented as a formal clean-room project.

No proprietary reference APKs, manufacturer logos/assets, signing keys, patient datasets or device exports are included. Product names are used only to identify interoperability and comparison references; their owners retain their rights. The working app name has not received a comprehensive trademark clearance.

## Contributing

Read [CONTRIBUTING.md](CONTRIBUTING.md). Keep real health data, serial numbers, Bluetooth addresses and credentials out of public issues. See [SECURITY.md](SECURITY.md) for reporting security/privacy concerns.

