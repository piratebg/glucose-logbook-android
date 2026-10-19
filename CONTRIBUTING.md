# Contributing

Use synthetic measurements and anonymized diagnostics. Never attach patient datasets, proprietary APKs, serial numbers, Bluetooth addresses, signing keys or credentials to public issues or pull requests.

Describe the exact app version, Android version, meter model/firmware and reproducible steps without identifying a person. A Bluetooth capability claim requires real-device evidence: pairing, service discovery, history/new readings, units/timestamps, repeated-sync deduplication and interrupted reconnect recovery. Brand similarity is not compatibility evidence.

Run JVM tests and lint as described in BUILDING.md. Storage/protocol/reporting changes need relevant tests; run instrumentation on a dedicated emulator for persistence/UI changes. Real-meter testing remains necessary for Bluetooth changes. Keep permissions and network access minimal; update privacy and safety documentation when behavior changes.

Contributions are submitted under the repository's MIT License for original content. Only contribute material you have rights to share. Do not copy proprietary applications' code, graphics or distinctive branding. Report security issues according to SECURITY.md.
