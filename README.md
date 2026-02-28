# Fait System Agent

High-privilege Android System Agent for Android 14 with AI companion overlay.

## Features
- 3D VRM character overlay
- Accessibility service monitoring
- Hardware control (brightness, volume)
- Persona-driven fallback system
- Hacker aesthetic splash screen
- Foreground service for persistent presence

## Quick Start
1. Clone repository: `git clone https://github.com/YOUR_USERNAME/FaitSystemAgent.git`
2. Open in Android Studio
3. Sync Gradle: `./gradlew build`
4. Connect Android 14+ device
5. Run: `./gradlew installDebug`

## Build
```bash
./gradlew build
./gradlew assembleDebug  # Build APK
./gradlew installDebug   # Install on device
```

## API Setup
Get API key from: https://openrouter.io

## Permissions Required
- SYSTEM_ALERT_WINDOW
- BIND_ACCESSIBILITY_SERVICE
- WRITE_SETTINGS
- FOREGROUND_SERVICE_SPECIAL_USE

## Project Structure
```
FaitSystemAgent/
├── app/
│   ├── src/main/
│   │   ├── java/com/faitapp/
│   │   ├── res/
│   │   └── AndroidManifest.xml
│   └── build.gradle
├── gradle/wrapper/
├── .github/workflows/
├── build.gradle
├── settings.gradle
└── gradlew
```

## Android Versions
- Minimum: Android 8.0 (API 26)
- Target: Android 14 (API 34)
