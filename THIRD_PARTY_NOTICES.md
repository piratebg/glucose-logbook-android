# Third-party notices

The root MIT License applies to original project content, not to third-party tooling or libraries. Preserve applicable notices when redistributing.

| Component | Role | License reference |
| --- | --- | --- |
| Gradle wrapper / Gradle | Build tooling | Apache-2.0 and bundled third-party terms; see licenses/GRADLE-LICENSE.txt and licenses/GRADLE-NOTICE.txt |
| Android Gradle Plugin, AndroidX Activity/Lifecycle/Health Connect | Build/runtime | Apache-2.0; see official project artifacts and AndroidX notices |
| Kotlin and kotlinx.coroutines | Compiler/runtime | Apache-2.0; see JetBrains project distributions |
| JUnit 4 | Tests | EPL-1.0 |
| Hamcrest | Transitive test dependency | BSD-3-Clause |
| Android SDK and JDK | External prerequisites | Their separate distribution terms; not bundled here |

This is a component overview, not a complete transitive license audit or a binary-distribution clearance. Resolve the exact dependency graph with `./gradlew :app:dependencies` (Windows: `gradlew.bat`) and inspect artifact POMs, license files and packaged notices before distributing binaries. Dependencies and licenses may change with upgrades.
