# Build, install and run

## Prerequisites

1. Android Studio with its Android SDK Manager, or equivalent Android command-line tools.
2. JDK 17 or JDK 21 selected as the Gradle JDK. The source targets Java bytecode 17.
3. Android SDK Platform 36, Build Tools 36.0.0, and Platform Tools (adb). Use SDK Manager to accept the applicable SDK licenses yourself.
4. Internet access for the initial Gradle/Maven downloads. The repository includes the Gradle 8.13 wrapper; do not install a different Gradle version globally.
5. An Android 8.0/API 26+ phone for the app, or an emulator for UI tests. Health Connect is a separate capability: its app/provider needs supported Android versions (Android 9+ where the separate provider is supported, built into Android 14+). Check the current [Android guidance](https://developer.android.com/health-and-fitness/health-connect/get-started).

## Android Studio

Clone/download this repository, then choose **Open** and select its `android/` directory. Let Gradle sync. Select the `app` run configuration and a device/emulator, then Run. Android Studio normally creates `local.properties` with your SDK location; that file must remain private and uncommitted.

If the SDK cannot be found, configure it in Android Studio or set `ANDROID_HOME` to your own SDK path. Alternatively create `android/local.properties` containing `sdk.dir=/your/sdk/path`. In Windows properties files, escape the drive colon/backslashes or use Android Studio to generate the file. No personal SDK path is distributed in this repository.

## Command line

Run these commands from `android/`.

| Task | Windows PowerShell | Linux/macOS |
| --- | --- | --- |
| Build preview APK | `.\gradlew.bat :app:assembleDebug` | `./gradlew :app:assembleDebug` |
| Install on connected device | `.\gradlew.bat :app:installDebug` | `./gradlew :app:installDebug` |
| JVM tests and lint | `.\gradlew.bat :app:testDebugUnitTest :app:lintDebug` | `./gradlew :app:testDebugUnitTest :app:lintDebug` |
| Android instrumented tests | `.\gradlew.bat :app:connectedDebugAndroidTest` | `./gradlew :app:connectedDebugAndroidTest` |

On Linux/macOS first run `chmod +x gradlew`. Set `JAVA_HOME` to a real JDK if Gradle cannot find Java. `java -version` should show 17 or 21. Build output: `app/build/outputs/apk/debug/app-debug.apk` relative to `android/`.

`installDebug` may install or replace the package `com.biotrack.app`; choose a dedicated emulator/test device for automated tests. For a USB phone, enable Developer options/USB debugging and authorize your own computer. Use `adb devices` to confirm it is available. Enable installation from the chosen file source only if you manually install an APK you trust.

## First use

1. Open Bio Track -> Meter. Allow Nearby devices on Android 12+, or location permission where older Android BLE discovery requires it. Older Android may also need system Location enabled.
2. Turn on Bluetooth, wake the meter and enable its pairing mode following the manufacturer's instructions. Search, select your own meter and complete Android's pairing prompt. Other named Bluetooth devices may appear in discovery; discovery does not establish compatibility.
3. Keep the meter nearby during history transfer. Avoid competing simultaneous app connections. Bio Track retries a failed connection a bounded number of times; use Reconnect or Recover full meter history when appropriate.
4. Compare imported dates, values and units against the meter. Repeated sync should not duplicate identical records. Do not use the app as a substitute for the meter's display or your care plan.
5. Review meter timestamps before exporting; meter clock values are interpreted in the phone timezone. The app does not silently alter the meter's clock.
6. In Share, grant glucose-write permission to Health Connect, then explicitly share pending readings. Permissions can be declined/revoked. Another health app must independently support reading/displaying glucose; exporting does not guarantee a destination app displays it.

## Reports

Insights -> section selector -> Summary, Patterns, Time of day, Logbook or Reports. Dates & filters selects an inclusive 1-366 calendar-day range, source and explicit meal tag. History and chart dots allow context annotations. Reports -> Save detailed PDF report or Export filtered readings as CSV. The Android document picker controls where the file is saved; a cloud-backed provider may upload it.

## Tests

Use an API 34 emulator for the documented instrumentation baseline. The test suite uses synthetic readings and unique test databases. Report UI/PDF tests save **synthetic** QA files in the emulator's `Download/BioTrackTesting` directory. Do not run instrumentation on a phone with valuable readings: the Gradle test workflow may install/uninstall the target app. An emulator cannot verify meter pairing, radio behavior or background reliability.

The GitHub workflow runs build, JVM tests and lint. Hardware and Android instrumentation results are documented separately; a green source CI check is not device/clinical certification.

## Signing and updates

Debug builds are for development. Android requires the same application ID and signing identity for an in-place update. A fresh GitHub checkout, another computer or a GitHub runner generally generates a different debug key from the original developer's key. Repository source availability does not transfer that key.

- Preserve your own signing key securely for updates. Never commit it, a password or `keystore.properties`.
- If signatures differ, export important readings first. Uninstalling removes the private database; there is currently no general CSV restore/import feature.
- Forks intended for independent distribution should select their own application ID and cleared app name before deployment. An application-ID change creates a separate app and does not migrate the original database.
- For a distributable release, use Android Studio's **Generate Signed Bundle / APK**, create/use a secure release key, review dependency notices and obtain the applicable platform/legal/regulatory review. `assembleRelease` alone does not make an authenticated signed release.
- No public release signing key or stable-signature binary update channel is provided in this source repository.

## Troubleshooting

**Permission denied running gradlew:** run `chmod +x gradlew` or `sh gradlew` on a POSIX system. Windows uses `gradlew.bat`.

**SDK/JDK mismatch:** check Android Studio's Gradle JDK, `JAVA_HOME`, SDK 36 installation and SDK licenses. Do not commit machine-specific fixes in `local.properties`.

**INSTALL_FAILED_UPDATE_INCOMPATIBLE:** the installed package has a different signature. This is not a Bluetooth bug. Follow the signing/data-preservation section rather than immediately uninstalling.

**No readings after connection:** keep the meter awake; check exact model support, pairing, permissions and competing connections; inspect the app status; try full-history recovery. Different model names are not interchangeable.

**Background session ends:** check the phone's battery restrictions, Bluetooth state and notification permission. Reopen and reconnect after process death/force stop. A sleeping/out-of-range meter cannot be guaranteed to transmit.

**Health Connect unavailable or permission denied:** verify Android/provider availability and permissions. Play-distributed versions have additional [Health Connect publication requirements](https://developer.android.com/health-and-fitness/health-connect/publish). A successful local debug build does not grant Play distribution approval.
