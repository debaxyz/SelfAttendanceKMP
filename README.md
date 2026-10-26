# Self Attendance – Kotlin Multiplatform + Compose Multiplatform

**Targets:** Android · iOS · Desktop (JVM) · Web (Wasm + JS)

Cross-platform attendance tracker based on the original Android Jetpack Compose + Firebase app.

---

## Project structure

```
SelfAttendanceKMP/
├── composeApp/
│   ├── src/
│   │   ├── commonMain/          # Shared models, ViewModel, pure logic, basic UI
│   │   ├── androidMain/         # Firebase Auth + Firestore (full)
│   │   ├── iosMain/             # In-memory stub
│   │   ├── desktopMain/         # In-memory stub + window entry
│   │   ├── wasmJsMain/          # Web (Wasm) stub + ComposeViewport entry
│   │   └── jsMain/              # Web (JS) stub + CanvasBasedWindow entry
│   └── build.gradle.kts
├── gradle/
│   └── libs.versions.toml
├── settings.gradle.kts
└── README.md
```

---

## Shared vs Platform

| Layer                         | commonMain | Android          | iOS / Desktop / Web |
|-------------------------------|------------|------------------|---------------------|
| Models (`Rec`, `Cfg`…)        | ✅         |                  |                     |
| Stats & salary logic          | ✅         |                  |                     |
| `AttendanceViewModel`         | ✅         |                  |                     |
| Repository interface          | ✅         |                  |                     |
| Basic Compose UI              | ✅         |                  |                     |
| Firebase Auth + Firestore     |            | ✅ Full          | ❌ Stubs            |
| Google / Email Auth UI        |            | (port later)     | Guest mode          |
| File export (CSV/JSON)        |            | (port later)     |                     |

---

## How to run

### Prerequisites
- **JDK 17+**
- Android Studio Ladybug+ **or** IntelliJ IDEA with Kotlin Multiplatform plugin
- For **iOS**: macOS + Xcode
- Optional: Firebase project + `google-services.json` placed in `composeApp/`

### Android
```bash
./gradlew :composeApp:assembleDebug
# or Run from Android Studio
```

### Desktop
```bash
./gradlew :composeApp:run
```

### Web (recommended – Wasm)
```bash
./gradlew :composeApp:wasmJsBrowserDevelopmentRun
# Opens browser at http://localhost:8080
```

### Web (classic JS)
```bash
./gradlew :composeApp:jsBrowserDevelopmentRun
```

### iOS
After first Gradle sync, open the generated Xcode project or:
```bash
./gradlew :composeApp:iosSimulatorArm64Test
```

---

## Current behaviour

- **Android**: Real Firebase backend (same as original app). Full cloud sync when user is signed in.
- **iOS / Desktop / Web**: In-memory repository. Data lives only while the app is running. Perfect for UI development and demos.
- **Auth**: Shared UI currently offers “Continue as guest”. Full Google Sign-In + Email/Password lives in the original Android code and can be ported into `androidMain`.

---

## Next steps to full parity

1. Port the rich original UI (calendar grid, EditSheet, rates cards, About/Licenses) into `commonMain` or keep Android-specific screens.
2. Move AuthScreen + Credential Manager into `androidMain`.
3. Replace non-Android stubs with:
   - Firebase JS SDK (Web)
   - Firebase iOS SDK
   - SQLDelight + local storage
   - or a shared Ktor backend
4. Add `expect`/`actual` file pickers for CSV / JSON backup & restore.
5. Drop `google-services.json` into `composeApp/` and enable the Google Services plugin for production Android builds.

---

Generated from the original Android Self Attendance project.  
Targets: **Android · iOS · Desktop · Web**
