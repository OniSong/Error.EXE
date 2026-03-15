# Project.EXE Development Standards

## 1. Android & Gradle Configuration
- **Repository Alignment**: Ensure `google()` and `mavenCentral()` are defined in BOTH `settings.gradle` (under `dependencyResolutionManagement`) and the root `build.gradle`.
- **Version Locking**: Flag any PR that attempts to use Android Gradle Plugin (AGP) versions older than 8.1.0 or Kotlin versions older than 1.9.0.
- **Mobile-Friendly Builds**: Since this project is built via GitHub Actions for a mobile developer, ensure all Gradle tasks are optimized for remote execution and don't require local GUI components.

## 2. 3D VRM & Overlay Logic
- **Memory Management**: Flag any high-frequency loops or recursive calls within the 3D rendering service that don't have explicit cleanup/dispose logic.
- **Overlay Permissions**: Ensure any new Activity or Service check for `SYSTEM_ALERT_WINDOW` permissions before attempting to draw on top of other apps.
- **VRM Loading**: Use asynchronous loading for VRM models to prevent freezing the main UI thread.

## 3. Dual-LLM Architecture
- **Cognition vs. Action**: Ensure that the "Action Orchestrator" LLM never blocks the "Cognition" LLM. They must operate on separate threads or via a non-blocking message queue.
- **Token Usage**: Flag functions that pass massive, un-truncated system logs to the LLM to prevent hitting token limits or increasing latency.

## 4. Code Style & Environment
- **Termux Compatibility**: Avoid dependencies that require specialized x86_64 binaries; prefer pure Java/Kotlin or ARM64-compatible C++ libraries.
- **Variable Placeholders**: Always flag hardcoded API keys or Hugging Face tokens. Remind the developer to use `[replace_variable]` or GitHub Secrets.
- 
