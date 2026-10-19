# Privacy policy

This policy describes the source release of Bio Track 0.3.0. Forks and modified builds may behave differently.

The app stores glucose readings, timestamps, meter identifiers, import checkpoints, meal tags, notes, preferences and Health Connect export receipts on the Android device. There is no project-operated account, server, advertising or analytics service. The app manifest does not request Internet access. Build tools download dependencies during compilation.

Bluetooth permissions discover and communicate with meters. Older Android versions may require location permission for BLE scanning. A foreground-service notification accompanies meter communication. Health Connect permission is optional and used to write blood glucose records when the user requests sharing. Access can be revoked in Android/Health Connect settings. Reading collection and Health Connect sharing are separate actions.

The local database uses Android app-private storage and platform protection; it is not separately encrypted by an app-managed encryption system. Android backup/device-transfer exclusions are configured. Protect the device with a screen lock. This is not a guarantee against access on compromised devices.

PDF and CSV files contain sensitive health information. Files saved to a selected location or shared through another app are subject to that provider's handling, backups and retention. Health Connect and apps you authorize there also have their own policies. The project does not control those copies.

Clearing app storage or uninstalling removes the app-private database, subject to Android behavior. Separately delete exported files and manage records already written to Health Connect through its controls; uninstalling this app does not promise removal of those copies. CSV restore is not implemented: export does not provide a supported round-trip backup.

Do not post real readings, names, serial numbers, Bluetooth addresses or unredacted screenshots in public GitHub issues. See SECURITY.md for security reporting. Reassess this policy whenever permissions, storage, networking or sharing behavior changes.
